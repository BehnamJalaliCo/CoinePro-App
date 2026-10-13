"""Pro Chart's owner console: pro-chart.com/admin/, and the three small routes the app talks to.

What it is for, in the order the owner asked:

* **How many.** Downloads of the APK and visits to the web app, read from Caddy's own JSON access
  log (nothing in the request path to slow down or break); installs the app actually opened, from
  `POST /api/app/hello`, which the app sends at most every few hours with an anonymous install id.
  Daily, weekly and monthly actives, new installs, retention, versions, languages, stores, countries
  and the hours readers are on.
* **Talking to readers.** A campaign is a message — an announcement, an offer with a discount code,
  an update nudge, a maintenance notice — aimed by platform, store, language, version range and
  country, scheduled, and delivered as a notification the next time each install checks in. The
  console counts delivered and opened for each.
* **Running the product.** Discount codes, remote configuration (a maintenance banner, the oldest
  version still welcome), the release currently offered and how far it has spread, the health of
  every service the app depends on with history, and an audit log of everything done here.
* **Locked down.** scrypt passwords, optional TOTP second factor, `HttpOnly`/`Secure`/`SameSite=Strict`
  sessions bound to a CSRF token, per-address and per-account login throttling, a strict CSP, and
  every change written to the audit log. No password is ever in git: the owner's account is
  created on the server with `python admin.py set-password <user>`.

Standard library and aiohttp only, one process, state in one SQLite file. `python admin.py` runs it;
`README.md` has the compose service and the Caddy lines.
"""
from __future__ import annotations

import asyncio
import base64
import csv
import hashlib
import hmac
import io
import json
import os
import re
import secrets
import shutil
import sqlite3
import struct
import sys
import time
from collections import defaultdict, deque
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

import aiohttp
from aiohttp import web

HERE = Path(__file__).resolve().parent

# ── Configuration ───────────────────────────────────────────────────────────────────────────────


def _default_probes() -> list[dict]:
    return [
        {"name": "TradeYar — crypto prices", "url": "https://tradeyar.trade-future.ir/api/v1/public/prices?symbols=BTCUSDT"},
        {"name": "CoinePro-FX — forex prices", "url": "https://coineprofx.com/api/public/prices/live"},
        {"name": "Forex mirror (Iran road)", "url": "https://tradeyar.trade-future.ir/fx/api/public/prices/live"},
        {"name": "Web relay", "url": "http://web-relay:8790/relay/health"},
        {"name": "Update channel", "url": "https://pro-chart.com/api/app/latest"},
        {"name": "Web terminal", "url": "https://pro-chart.com/terminal/"},
    ]


@dataclass
class Config:
    db_path: str = os.environ.get("ADMIN_DB", "/data/admin.sqlite3")
    access_logs: list[str] = field(default_factory=lambda: [
        p for p in os.environ.get("ADMIN_ACCESS_LOG", "/logs/access.log").split(",") if p.strip()
    ])
    latest_json: str = os.environ.get("ADMIN_LATEST_JSON", "/site/api/app/latest")
    static_dir: str = os.environ.get("ADMIN_STATIC", str(HERE / "static"))
    font_dir: str = os.environ.get("ADMIN_FONTS", str(HERE / "fonts"))
    cookie_secure: bool = os.environ.get("ADMIN_COOKIE_SECURE", "1") != "0"
    timezone: str = os.environ.get("ADMIN_TZ", "Asia/Tehran")
    session_idle_hours: int = int(os.environ.get("ADMIN_SESSION_IDLE_HOURS", "12"))
    session_max_days: int = int(os.environ.get("ADMIN_SESSION_MAX_DAYS", "7"))
    probes: list[dict] = field(default_factory=lambda: json.loads(os.environ["ADMIN_PROBES"])
                               if os.environ.get("ADMIN_PROBES") else _default_probes())
    probe_every_seconds: int = int(os.environ.get("ADMIN_PROBE_EVERY", "300"))
    ingest_every_seconds: int = int(os.environ.get("ADMIN_INGEST_EVERY", "60"))
    # Tests run without the background loops; production always has them.
    background: bool = os.environ.get("ADMIN_BACKGROUND", "1") != "0"
    login_attempts_per_window: int = 10
    login_window_seconds: int = 600
    lockout_after_failures: int = 8
    lockout_minutes: int = 15
    hello_per_minute: int = int(os.environ.get("ADMIN_HELLO_PER_MINUTE", "60"))
    # The app checks in at most every few hours; an install not seen for this long is gone.
    active_window_days: int = 30


SESSION_COOKIE = "pca_session"
MESSAGE_KINDS = ("announcement", "offer", "update", "maintenance", "alert")
EVENT_KINDS = ("delivered", "opened", "dismissed")
PLATFORMS = ("android", "web")
FLAVOURS = ("direct", "bazaar")
CONFIG_DEFAULTS: dict[str, object] = {
    "maintenance": {"on": False, "message_fa": "", "message_en": ""},
    "min_version_code": 0,
    "update_nudge": {"on": False, "message_fa": "", "message_en": ""},
    "hello_every_hours": 6,
}

# ── Small pieces ────────────────────────────────────────────────────────────────────────────────


def now() -> int:
    return int(time.time())


def clean_text(value, limit: int) -> str:
    text = "" if value is None else str(value)
    text = "".join(ch for ch in text if ch == "\n" or ch >= " ")
    return text.strip()[:limit]


def hash_password(password: str, salt: bytes | None = None) -> str:
    salt = salt or secrets.token_bytes(16)
    n, r, p = 2 ** 15, 8, 1
    digest = hashlib.scrypt(password.encode(), salt=salt, n=n, r=r, p=p, maxmem=96 * 1024 * 1024, dklen=32)
    return f"scrypt${n}${r}${p}${base64.b64encode(salt).decode()}${base64.b64encode(digest).decode()}"


def verify_password(password: str, stored: str) -> bool:
    try:
        scheme, n, r, p, salt, digest = stored.split("$")
        if scheme != "scrypt":
            return False
        check = hashlib.scrypt(password.encode(), salt=base64.b64decode(salt), n=int(n), r=int(r), p=int(p),
                               maxmem=96 * 1024 * 1024, dklen=32)
        return hmac.compare_digest(check, base64.b64decode(digest))
    except (ValueError, TypeError):
        return False


def totp_code(secret_b32: str, step: int) -> str:
    key = base64.b32decode(secret_b32 + "=" * (-len(secret_b32) % 8), casefold=True)
    mac = hmac.new(key, struct.pack(">Q", step), hashlib.sha1).digest()
    offset = mac[-1] & 0x0F
    value = struct.unpack(">I", mac[offset:offset + 4])[0] & 0x7FFFFFFF
    return f"{value % 1_000_000:06d}"


def totp_matches(secret_b32: str, code: str, at: float | None = None) -> int | None:
    """The time step [code] belongs to, within one step either side, or None."""
    code = re.sub(r"\s", "", code or "")
    if not re.fullmatch(r"\d{6}", code):
        return None
    step = int((at if at is not None else time.time()) // 30)
    for candidate in (step - 1, step, step + 1):
        if hmac.compare_digest(totp_code(secret_b32, candidate), code):
            return candidate
    return None


def version_of_file(name: str) -> str:
    found = re.search(r"(\d+\.\d+\.\d+)", name)
    return found.group(1) if found else name


class RateLimit:
    def __init__(self, limit: int, window: int):
        self.limit, self.window = limit, window
        self.hits: dict[str, deque] = defaultdict(deque)

    def allow(self, key: str) -> bool:
        stamp = time.monotonic()
        bucket = self.hits[key]
        while bucket and stamp - bucket[0] > self.window:
            bucket.popleft()
        if len(bucket) >= self.limit:
            return False
        bucket.append(stamp)
        if len(self.hits) > 50_000:
            self.hits.clear()
        return True


# ── Storage ─────────────────────────────────────────────────────────────────────────────────────

SCHEMA = """
CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
CREATE TABLE IF NOT EXISTS users (
    username TEXT PRIMARY KEY, pw_hash TEXT NOT NULL, totp_secret TEXT, totp_enabled INTEGER NOT NULL DEFAULT 0,
    totp_last_step INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL, last_login_at INTEGER,
    failures INTEGER NOT NULL DEFAULT 0, locked_until INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS sessions (
    token_hash TEXT PRIMARY KEY, username TEXT NOT NULL, csrf TEXT NOT NULL, created_at INTEGER NOT NULL,
    last_seen INTEGER NOT NULL, ip TEXT, agent TEXT);
CREATE TABLE IF NOT EXISTS audit (
    id INTEGER PRIMARY KEY AUTOINCREMENT, at INTEGER NOT NULL, username TEXT, ip TEXT, action TEXT NOT NULL,
    detail TEXT);
CREATE TABLE IF NOT EXISTS installs (
    id_hash TEXT PRIMARY KEY, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL, platform TEXT, flavour TEXT,
    version TEXT, version_code INTEGER, lang TEXT, country TEXT, sdk INTEGER, tz TEXT, hellos INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS active_days (day TEXT NOT NULL, id_hash TEXT NOT NULL, PRIMARY KEY (day, id_hash));
CREATE TABLE IF NOT EXISTS active_hours (day TEXT NOT NULL, hour INTEGER NOT NULL, weekday INTEGER NOT NULL,
    count INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (day, hour));
CREATE TABLE IF NOT EXISTS downloads (day TEXT NOT NULL, file TEXT NOT NULL, ip_hash TEXT NOT NULL, country TEXT,
    PRIMARY KEY (day, file, ip_hash));
CREATE TABLE IF NOT EXISTS download_hits (day TEXT NOT NULL, file TEXT NOT NULL, hits INTEGER NOT NULL DEFAULT 0,
    bytes INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (day, file));
CREATE TABLE IF NOT EXISTS visits (day TEXT NOT NULL, ip_hash TEXT NOT NULL, country TEXT, PRIMARY KEY (day, ip_hash));
CREATE TABLE IF NOT EXISTS update_checks (day TEXT NOT NULL, ip_hash TEXT NOT NULL, PRIMARY KEY (day, ip_hash));
CREATE TABLE IF NOT EXISTS log_state (path TEXT PRIMARY KEY, inode INTEGER NOT NULL, offset INTEGER NOT NULL, head TEXT);
CREATE TABLE IF NOT EXISTS messages (
    id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, created_by TEXT, cancelled INTEGER NOT NULL DEFAULT 0,
    kind TEXT NOT NULL, priority TEXT NOT NULL DEFAULT 'normal', title_fa TEXT, body_fa TEXT, title_en TEXT, body_en TEXT,
    cta_fa TEXT, cta_en TEXT, link TEXT, coupon TEXT, audience TEXT NOT NULL DEFAULT '{}', starts_at INTEGER NOT NULL,
    ends_at INTEGER, notify INTEGER NOT NULL DEFAULT 1, draft INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS message_events (message_id INTEGER NOT NULL, id_hash TEXT NOT NULL, kind TEXT NOT NULL,
    at INTEGER NOT NULL, PRIMARY KEY (message_id, id_hash, kind));
CREATE TABLE IF NOT EXISTS coupons (
    code TEXT PRIMARY KEY, percent INTEGER NOT NULL, plan TEXT, max_uses INTEGER, uses INTEGER NOT NULL DEFAULT 0,
    checks INTEGER NOT NULL DEFAULT 0, expires_at INTEGER, note TEXT, active INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL, created_by TEXT);
CREATE TABLE IF NOT EXISTS config (key TEXT PRIMARY KEY, value TEXT NOT NULL, updated_at INTEGER, updated_by TEXT);
CREATE TABLE IF NOT EXISTS health (at INTEGER NOT NULL, target TEXT NOT NULL, ok INTEGER NOT NULL, ms INTEGER, status INTEGER);
CREATE INDEX IF NOT EXISTS health_at ON health (at);
CREATE INDEX IF NOT EXISTS installs_last_seen ON installs (last_seen);
CREATE INDEX IF NOT EXISTS audit_at ON audit (at);
"""


class Store:
    def __init__(self, path: str):
        if path != ":memory:":
            Path(path).parent.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(path, check_same_thread=False, isolation_level=None)
        self.db.row_factory = sqlite3.Row
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.execute("PRAGMA foreign_keys=ON")
        self.db.executescript(SCHEMA)
        self.salt = self.meta("salt") or self._set_meta("salt", secrets.token_hex(32))

    def meta(self, key: str) -> str | None:
        row = self.db.execute("SELECT value FROM meta WHERE key=?", (key,)).fetchone()
        return row["value"] if row else None

    def _set_meta(self, key: str, value: str) -> str:
        self.db.execute("INSERT OR REPLACE INTO meta(key, value) VALUES (?, ?)", (key, value))
        return value

    def anon(self, value: str) -> str:
        return hashlib.sha256((self.salt + "|" + value).encode()).hexdigest()[:32]

    def q(self, sql: str, args=()) -> list[sqlite3.Row]:
        return self.db.execute(sql, args).fetchall()

    def one(self, sql: str, args=()):
        row = self.db.execute(sql, args).fetchone()
        return row[0] if row is not None else None

    def x(self, sql: str, args=()) -> sqlite3.Cursor:
        return self.db.execute(sql, args)

    def audit(self, username: str | None, ip: str | None, action: str, detail: dict | str | None = None) -> None:
        text = detail if isinstance(detail, str) or detail is None else json.dumps(detail, ensure_ascii=False)
        self.x("INSERT INTO audit(at, username, ip, action, detail) VALUES (?,?,?,?,?)",
               (now(), username, ip, action, text))

    def config(self) -> dict:
        values = dict(CONFIG_DEFAULTS)
        for row in self.q("SELECT key, value FROM config"):
            try:
                values[row["key"]] = json.loads(row["value"])
            except ValueError:
                pass
        return values


# ── The app ─────────────────────────────────────────────────────────────────────────────────────


def build_app(config: Config | None = None) -> web.Application:
    config = config or Config()
    app = web.Application(client_max_size=256 * 1024, middlewares=[security_headers])
    app["config"] = config
    app["store"] = Store(config.db_path)
    # What the background loops leave for the requests; the application itself is frozen once it runs.
    app["state"] = {}
    app["zone"] = ZoneInfo(config.timezone)
    app["login_limit"] = RateLimit(config.login_attempts_per_window, config.login_window_seconds)
    app["hello_limit"] = RateLimit(config.hello_per_minute, 60)
    app["public_limit"] = RateLimit(120, 60)

    # The app's routes.
    app.router.add_post("/api/app/hello", hello)
    app.router.add_post("/api/app/event", app_event)
    app.router.add_get("/api/app/coupon", coupon_check)

    # The console.
    app.router.add_get("/admin", index)
    app.router.add_get("/admin/", index)
    app.router.add_get("/admin/health", lambda _: web.json_response({"ok": True}))
    app.router.add_static("/admin/static/", config.static_dir, show_index=False)
    if Path(config.font_dir).is_dir():
        app.router.add_static("/admin/fonts/", config.font_dir, show_index=False)
    api = "/admin/api"
    app.router.add_post(f"{api}/login", login)
    app.router.add_post(f"{api}/logout", logout)
    app.router.add_get(f"{api}/session", session_info)
    app.router.add_get(f"{api}/overview", overview)
    app.router.add_get(f"{api}/downloads", downloads_report)
    app.router.add_get(f"{api}/audience", audience_report)
    app.router.add_get(f"{api}/installs", installs_list)
    app.router.add_get(f"{api}/installs.csv", installs_csv)
    app.router.add_get(f"{api}/messages", messages_list)
    app.router.add_post(f"{api}/messages", message_save)
    app.router.add_post(f"{api}/messages/estimate", message_estimate)
    app.router.add_post(r"/admin/api/messages/{id:\d+}/cancel", message_cancel)
    app.router.add_post(r"/admin/api/messages/{id:\d+}/duplicate", message_duplicate)
    app.router.add_get(f"{api}/coupons", coupons_list)
    app.router.add_post(f"{api}/coupons", coupon_save)
    app.router.add_post(r"/admin/api/coupons/{code}/toggle", coupon_toggle)
    app.router.add_delete(r"/admin/api/coupons/{code}", coupon_delete)
    app.router.add_get(f"{api}/config", config_get)
    app.router.add_put(f"{api}/config", config_put)
    app.router.add_get(f"{api}/release", release_info)
    app.router.add_get(f"{api}/health", health_report)
    app.router.add_post(f"{api}/health/run", health_run)
    app.router.add_get(f"{api}/audit", audit_list)
    app.router.add_get(f"{api}/audit.csv", audit_csv)
    app.router.add_post(f"{api}/account/password", account_password)
    app.router.add_post(f"{api}/account/totp/setup", totp_setup)
    app.router.add_post(f"{api}/account/totp/enable", totp_enable)
    app.router.add_post(f"{api}/account/totp/disable", totp_disable)
    app.router.add_get(f"{api}/account/sessions", sessions_list)
    app.router.add_post(f"{api}/account/sessions/revoke-others", sessions_revoke_others)

    app.on_startup.append(_start)
    app.on_cleanup.append(_stop)
    return app


async def _start(app: web.Application) -> None:
    app["state"]["http"] = aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=12),
                                                 headers={"User-Agent": "ProChart-Admin/1 (health probe)"})
    app["state"]["tasks"] = []
    if app["config"].background:
        app["state"]["tasks"].append(asyncio.create_task(_every(app["config"].ingest_every_seconds, lambda: ingest_logs(app))))
        app["state"]["tasks"].append(asyncio.create_task(_every(app["config"].probe_every_seconds, lambda: run_probes(app))))


async def _stop(app: web.Application) -> None:
    for task in app["state"]["tasks"]:
        task.cancel()
    await app["state"]["http"].close()


async def _every(seconds: int, job) -> None:
    while True:
        try:
            result = job()
            if asyncio.iscoroutine(result):
                await result
        except asyncio.CancelledError:
            raise
        except Exception as error:  # A bad log line or a dead probe must not end the loop.
            print(f"background job failed: {error!r}", file=sys.stderr)
        await asyncio.sleep(seconds)


@web.middleware
async def security_headers(request: web.Request, handler):
    try:
        response = await handler(request)
    except web.HTTPException as error:
        # A 404 for a missing console file went out without these, and Cloudflare kept it (5.31.0).
        _harden(request, error)
        raise
    _harden(request, response)
    return response


def _harden(request: web.Request, response: web.StreamResponse) -> None:
    if request.path.startswith("/api/app/"):
        response.headers.setdefault("Cache-Control", "no-store")
    if request.path.startswith("/admin"):
        response.headers.setdefault(
            "Content-Security-Policy",
            "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; "
            "font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'",
        )
        response.headers.setdefault("X-Frame-Options", "DENY")
        response.headers.setdefault("X-Content-Type-Options", "nosniff")
        response.headers.setdefault("Referrer-Policy", "no-referrer")
        response.headers.setdefault("Cache-Control", "no-store")
        response.headers.setdefault("Permissions-Policy", "camera=(), microphone=(), geolocation=()")


def client_ip(request: web.Request) -> str:
    for header in ("CF-Connecting-IP", "X-Real-IP"):
        value = request.headers.get(header, "").strip()
        if value:
            return value[:64]
    forwarded = request.headers.get("X-Forwarded-For", "")
    if forwarded:
        return forwarded.split(",")[0].strip()[:64]
    return (request.remote or "")[:64]


def country_of(request: web.Request) -> str | None:
    value = request.headers.get("CF-IPCountry", "").strip().upper()
    return value if re.fullmatch(r"[A-Z]{2}", value) and value not in ("XX", "T1") else None


def day_of(app: web.Application, epoch: float) -> str:
    return datetime.fromtimestamp(epoch, app["zone"]).strftime("%Y-%m-%d")


async def read_json(request: web.Request) -> dict:
    try:
        body = await request.json()
    except (ValueError, aiohttp.ContentTypeError):
        raise web.HTTPBadRequest(text=json.dumps({"error": "invalid_json"}), content_type="application/json")
    if not isinstance(body, dict):
        raise web.HTTPBadRequest(text=json.dumps({"error": "invalid_json"}), content_type="application/json")
    return body


def fail(status: int, error: str, **extra) -> web.Response:
    return web.json_response({"error": error, **extra}, status=status)


# ── Sessions ────────────────────────────────────────────────────────────────────────────────────


def _token_hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def current_session(request: web.Request):
    store: Store = request.app["store"]
    config: Config = request.app["config"]
    token = request.cookies.get(SESSION_COOKIE)
    if not token or len(token) > 200:
        return None
    row = store.q("SELECT * FROM sessions WHERE token_hash=?", (_token_hash(token),))
    if not row:
        return None
    session = row[0]
    stamp = now()
    if stamp - session["last_seen"] > config.session_idle_hours * 3600 or \
            stamp - session["created_at"] > config.session_max_days * 86400:
        store.x("DELETE FROM sessions WHERE token_hash=?", (session["token_hash"],))
        return None
    if stamp - session["last_seen"] > 60:
        store.x("UPDATE sessions SET last_seen=? WHERE token_hash=?", (stamp, session["token_hash"]))
    return session


def require(request: web.Request, mutating: bool = False):
    session = current_session(request)
    if session is None:
        raise web.HTTPUnauthorized(text=json.dumps({"error": "signed_out"}), content_type="application/json")
    if mutating:
        sent = request.headers.get("X-CSRF-Token", "")
        if not sent or not hmac.compare_digest(sent, session["csrf"]):
            raise web.HTTPForbidden(text=json.dumps({"error": "csrf"}), content_type="application/json")
        origin = request.headers.get("Origin")
        if origin and request.host and not origin.endswith("//" + request.host):
            raise web.HTTPForbidden(text=json.dumps({"error": "origin"}), content_type="application/json")
    return session


async def index(request: web.Request) -> web.StreamResponse:
    path = Path(request.app["config"].static_dir) / "index.html"
    if not path.is_file():
        return web.Response(status=500, text="console bundle missing")
    return web.FileResponse(path, headers={"Content-Type": "text/html; charset=utf-8"})


async def login(request: web.Request) -> web.Response:
    store: Store = request.app["store"]
    config: Config = request.app["config"]
    ip = client_ip(request)
    if not request.app["login_limit"].allow(ip):
        return fail(429, "too_many_attempts")
    body = await read_json(request)
    username = clean_text(body.get("username"), 64).lower()
    password = str(body.get("password") or "")[:256]
    rows = store.q("SELECT * FROM users WHERE username=?", (username,))
    user = rows[0] if rows else None
    stamp = now()
    if user is not None and user["locked_until"] > stamp:
        store.audit(username, ip, "login.locked")
        return fail(423, "locked", retry_after=user["locked_until"] - stamp)
    # The same work whether or not the user exists, so timing does not tell a guesser which names are real.
    ok = verify_password(password, user["pw_hash"] if user else hash_password("x" * 12))
    if not ok or user is None:
        if user is not None:
            failures = user["failures"] + 1
            locked = stamp + config.lockout_minutes * 60 if failures >= config.lockout_after_failures else 0
            store.x("UPDATE users SET failures=?, locked_until=? WHERE username=?",
                    (0 if locked else failures, locked, username))
        store.audit(username or None, ip, "login.failed")
        return fail(401, "bad_credentials")
    if user["totp_enabled"]:
        code = str(body.get("code") or "")
        if not code:
            return web.json_response({"need_code": True}, status=401)
        step = totp_matches(user["totp_secret"], code)
        if step is None or step <= user["totp_last_step"]:
            store.audit(username, ip, "login.bad_code")
            return fail(401, "bad_code")
        store.x("UPDATE users SET totp_last_step=? WHERE username=?", (step, username))
    token = secrets.token_urlsafe(32)
    store.x("INSERT INTO sessions(token_hash, username, csrf, created_at, last_seen, ip, agent) VALUES (?,?,?,?,?,?,?)",
            (_token_hash(token), username, secrets.token_urlsafe(24), stamp, stamp, ip,
             clean_text(request.headers.get("User-Agent"), 200)))
    store.x("UPDATE users SET failures=0, locked_until=0, last_login_at=? WHERE username=?", (stamp, username))
    store.audit(username, ip, "login.ok")
    response = web.json_response({"ok": True})
    response.set_cookie(SESSION_COOKIE, token, httponly=True, secure=config.cookie_secure, samesite="Strict",
                        path="/admin", max_age=config.session_max_days * 86400)
    return response


async def logout(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    store: Store = request.app["store"]
    store.x("DELETE FROM sessions WHERE token_hash=?", (session["token_hash"],))
    store.audit(session["username"], client_ip(request), "logout")
    response = web.json_response({"ok": True})
    response.del_cookie(SESSION_COOKIE, path="/admin")
    return response


async def session_info(request: web.Request) -> web.Response:
    session = require(request)
    store: Store = request.app["store"]
    user = store.q("SELECT username, totp_enabled, last_login_at FROM users WHERE username=?", (session["username"],))[0]
    return web.json_response({
        "username": user["username"], "csrf": session["csrf"], "totp": bool(user["totp_enabled"]),
        "last_login_at": user["last_login_at"], "timezone": request.app["config"].timezone,
    })


# ── The app's check-in ──────────────────────────────────────────────────────────────────────────


def _install_matches(audience: dict, install: dict) -> bool:
    platforms = audience.get("platforms") or []
    if platforms and install.get("platform") not in platforms:
        return False
    flavours = audience.get("flavours") or []
    if flavours and install.get("platform") == "android" and install.get("flavour") not in flavours:
        return False
    langs = audience.get("langs") or []
    if langs and install.get("lang") not in langs:
        return False
    countries = audience.get("countries") or []
    if countries and (install.get("country") or "") not in countries:
        return False
    code = install.get("version_code") or 0
    if audience.get("min_version_code") and code < int(audience["min_version_code"]):
        return False
    if audience.get("max_version_code") and code > int(audience["max_version_code"]):
        return False
    return True


def live_messages(store: Store, at: int) -> list[sqlite3.Row]:
    return store.q(
        "SELECT * FROM messages WHERE cancelled=0 AND draft=0 AND starts_at<=? AND (ends_at IS NULL OR ends_at>?) "
        "ORDER BY CASE priority WHEN 'high' THEN 0 ELSE 1 END, starts_at DESC LIMIT 20",
        (at, at),
    )


def message_for(row: sqlite3.Row, lang: str) -> dict:
    english = lang == "en"
    title = (row["title_en"] if english and row["title_en"] else row["title_fa"]) or row["title_en"] or ""
    body = (row["body_en"] if english and row["body_en"] else row["body_fa"]) or row["body_en"] or ""
    cta = (row["cta_en"] if english and row["cta_en"] else row["cta_fa"]) or ""
    return {
        "id": row["id"], "kind": row["kind"], "priority": row["priority"], "title": title, "body": body,
        "cta": cta, "link": row["link"] or "", "coupon": row["coupon"] or "", "notify": bool(row["notify"]),
        "ends_at": row["ends_at"],
    }


async def hello(request: web.Request) -> web.Response:
    store: Store = request.app["store"]
    ip = client_ip(request)
    if not request.app["hello_limit"].allow(ip):
        return fail(429, "slow_down")
    body = await read_json(request)
    install_id = clean_text(body.get("install_id"), 128)
    if len(install_id) < 8:
        return fail(400, "install_id")
    platform = clean_text(body.get("platform"), 16).lower()
    platform = platform if platform in PLATFORMS else "android"
    flavour = clean_text(body.get("flavour"), 16).lower()
    flavour = flavour if flavour in FLAVOURS else "direct"
    lang = "en" if clean_text(body.get("lang"), 8).lower().startswith("en") else "fa"
    try:
        version_code = int(body.get("version_code") or 0)
        sdk = int(body.get("sdk") or 0)
    except (TypeError, ValueError):
        return fail(400, "version_code")
    record = {
        "id_hash": store.anon(install_id), "platform": platform, "flavour": flavour,
        "version": clean_text(body.get("version"), 32) or None, "version_code": version_code, "lang": lang,
        "country": country_of(request), "sdk": sdk, "tz": clean_text(body.get("tz"), 64),
    }
    stamp = now()
    store.x(
        "INSERT INTO installs(id_hash, first_seen, last_seen, platform, flavour, version, version_code, lang, country, sdk, tz, hellos) "
        "VALUES (:id_hash, :at, :at, :platform, :flavour, :version, :version_code, :lang, :country, :sdk, :tz, 1) "
        "ON CONFLICT(id_hash) DO UPDATE SET last_seen=:at, platform=:platform, flavour=:flavour, version=:version, "
        "version_code=:version_code, lang=:lang, country=COALESCE(:country, country), sdk=:sdk, tz=:tz, hellos=hellos+1",
        {**record, "at": stamp},
    )
    local = datetime.fromtimestamp(stamp, request.app["zone"])
    day = local.strftime("%Y-%m-%d")
    store.x("INSERT OR IGNORE INTO active_days(day, id_hash) VALUES (?, ?)", (day, record["id_hash"]))
    store.x("INSERT INTO active_hours(day, hour, weekday, count) VALUES (?, ?, ?, 1) "
            "ON CONFLICT(day, hour) DO UPDATE SET count=count+1", (day, local.hour, local.weekday()))
    messages = []
    for row in live_messages(store, stamp):
        try:
            audience = json.loads(row["audience"] or "{}")
        except ValueError:
            audience = {}
        if _install_matches(audience, record):
            messages.append(message_for(row, lang))
    settings = store.config()
    return web.json_response({
        "server_time": stamp,
        "messages": messages,
        "config": {
            "maintenance": settings.get("maintenance"),
            "min_version_code": settings.get("min_version_code"),
            "update_nudge": settings.get("update_nudge"),
            "hello_every_hours": settings.get("hello_every_hours"),
        },
    })


async def app_event(request: web.Request) -> web.Response:
    store: Store = request.app["store"]
    if not request.app["public_limit"].allow(client_ip(request)):
        return fail(429, "slow_down")
    body = await read_json(request)
    install_id = clean_text(body.get("install_id"), 128)
    kind = clean_text(body.get("kind"), 16)
    try:
        message_id = int(body.get("message_id"))
    except (TypeError, ValueError):
        return fail(400, "message_id")
    if len(install_id) < 8 or kind not in EVENT_KINDS:
        return fail(400, "event")
    if store.one("SELECT 1 FROM messages WHERE id=?", (message_id,)) is None:
        return fail(404, "message")
    store.x("INSERT OR IGNORE INTO message_events(message_id, id_hash, kind, at) VALUES (?,?,?,?)",
            (message_id, store.anon(install_id), kind, now()))
    return web.json_response({"ok": True})


async def coupon_check(request: web.Request) -> web.Response:
    store: Store = request.app["store"]
    if not request.app["public_limit"].allow(client_ip(request)):
        return fail(429, "slow_down")
    code = clean_text(request.query.get("code"), 40).upper()
    row = store.q("SELECT * FROM coupons WHERE code=?", (code,))
    if not row:
        return web.json_response({"valid": False})
    coupon = row[0]
    store.x("UPDATE coupons SET checks=checks+1 WHERE code=?", (code,))
    stamp = now()
    valid = bool(coupon["active"]) and (coupon["expires_at"] is None or coupon["expires_at"] > stamp) and \
        (coupon["max_uses"] is None or coupon["uses"] < coupon["max_uses"])
    return web.json_response({"valid": valid, "percent": coupon["percent"] if valid else None,
                              "plan": coupon["plan"] if valid else None, "expires_at": coupon["expires_at"]})


# ── Reading Caddy's access log ──────────────────────────────────────────────────────────────────


def _header(headers: dict, name: str) -> str:
    for key, value in (headers or {}).items():
        if key.lower() == name.lower():
            return value[0] if isinstance(value, list) and value else str(value)
    return ""


def ingest_line(app: web.Application, line: str) -> None:
    """One JSON line of Caddy's access log into the counters. Anything that is not one is skipped."""
    try:
        entry = json.loads(line)
    except ValueError:
        return
    request = entry.get("request") or {}
    uri = str(request.get("uri") or "")
    path = uri.split("?", 1)[0]
    status = int(entry.get("status") or 0)
    if request.get("method") not in ("GET", None) or status not in (200, 206, 304):
        return
    headers = request.get("headers") or {}
    ip = _header(headers, "Cf-Connecting-Ip") or request.get("client_ip") or request.get("remote_ip") or ""
    country = _header(headers, "Cf-Ipcountry").upper() or None
    if country and not re.fullmatch(r"[A-Z]{2}", country):
        country = None
    store: Store = app["store"]
    day = day_of(app, float(entry.get("ts") or time.time()))
    ip_hash = store.anon("ip|" + ip)
    if path.startswith("/download/") and path.endswith(".apk"):
        # A resumed download sends a Range past zero; it is the same download, not a new one.
        ranged = _header(headers, "Range")
        if status == 206 and ranged and not re.match(r"bytes=0-", ranged):
            return
        name = path.rsplit("/", 1)[-1][:120]
        store.x("INSERT OR IGNORE INTO downloads(day, file, ip_hash, country) VALUES (?,?,?,?)", (day, name, ip_hash, country))
        store.x("INSERT INTO download_hits(day, file, hits, bytes) VALUES (?,?,1,?) "
                "ON CONFLICT(day, file) DO UPDATE SET hits=hits+1, bytes=bytes+excluded.bytes",
                (day, name, int(entry.get("size") or 0)))
    elif path in ("/terminal/", "/terminal/index.html", "/", "/index.html") and status in (200, 304):
        store.x("INSERT OR IGNORE INTO visits(day, ip_hash, country) VALUES (?,?,?)", (day, ip_hash, country))
    elif path == "/api/app/latest":
        store.x("INSERT OR IGNORE INTO update_checks(day, ip_hash) VALUES (?,?)", (day, ip_hash))


def ingest_logs(app: web.Application) -> int:
    """New lines of every configured access log since the last pass.

    A rotated log is noticed by its inode, or — since a filesystem may hand the new file the old one's
    inode — by its first line changing; either way it is read again from the start.
    """
    store: Store = app["store"]
    lines = 0
    for path in app["config"].access_logs:
        file = Path(path)
        if not file.is_file():
            continue
        stat = file.stat()
        with file.open("rb") as handle:
            head = hashlib.sha256(handle.readline(4096)).hexdigest()[:16]
        state = store.q("SELECT inode, offset, head FROM log_state WHERE path=?", (path,))
        same = state and state[0]["inode"] == stat.st_ino and state[0]["offset"] <= stat.st_size and state[0]["head"] == head
        offset = state[0]["offset"] if same else 0
        with file.open("rb") as handle:
            handle.seek(offset)
            store.x("BEGIN")
            try:
                for raw in handle:
                    if not raw.endswith(b"\n"):
                        break  # A line still being written: next pass.
                    offset += len(raw)
                    ingest_line(app, raw.decode("utf-8", "replace"))
                    lines += 1
                store.x("INSERT OR REPLACE INTO log_state(path, inode, offset, head) VALUES (?,?,?,?)",
                        (path, stat.st_ino, offset, head))
                store.x("COMMIT")
            except Exception:
                store.x("ROLLBACK")
                raise
    return lines


# ── Health ──────────────────────────────────────────────────────────────────────────────────────


async def probe(app: web.Application, target: dict) -> dict:
    started = time.perf_counter()
    try:
        async with app["state"]["http"].get(target["url"], allow_redirects=True) as response:
            await response.read()
            ok = 200 <= response.status < 400
            return {"name": target["name"], "ok": ok, "status": response.status,
                    "ms": int((time.perf_counter() - started) * 1000)}
    except Exception as error:
        return {"name": target["name"], "ok": False, "status": 0, "ms": int((time.perf_counter() - started) * 1000),
                "error": type(error).__name__}


async def run_probes(app: web.Application) -> list[dict]:
    results = await asyncio.gather(*(probe(app, target) for target in app["config"].probes))
    store: Store = app["store"]
    stamp = now()
    for result in results:
        store.x("INSERT INTO health(at, target, ok, ms, status) VALUES (?,?,?,?,?)",
                (stamp, result["name"], int(result["ok"]), result["ms"], result["status"]))
    store.x("DELETE FROM health WHERE at<?", (stamp - 14 * 86400,))
    app["state"]["last_probe"] = {"at": stamp, "results": results}
    return results


def last_probe(app: web.Application) -> dict | None:
    """The latest round of probes: this process's own, or after a restart the last one stored."""
    if app["state"].get("last_probe"):
        return app["state"]["last_probe"]
    store: Store = app["store"]
    at = store.one("SELECT MAX(at) FROM health")
    if at is None:
        return None
    results = [{"name": row["target"], "ok": bool(row["ok"]), "status": row["status"], "ms": row["ms"]}
               for row in store.q("SELECT target, ok, ms, status FROM health WHERE at=?", (at,))]
    return {"at": at, "results": results}


def server_stats(config: Config) -> dict:
    stats: dict = {}
    try:
        stats["load"] = list(os.getloadavg())
        stats["cpus"] = os.cpu_count()
    except OSError:
        pass
    try:
        info = {}
        for line in Path("/proc/meminfo").read_text().splitlines():
            key, _, value = line.partition(":")
            info[key] = int(value.strip().split()[0]) * 1024
        stats["memory"] = {"total": info.get("MemTotal"), "available": info.get("MemAvailable")}
    except (OSError, ValueError, IndexError):
        pass
    try:
        usage = shutil.disk_usage(Path(config.db_path).parent if config.db_path != ":memory:" else "/")
        stats["disk"] = {"total": usage.total, "free": usage.free}
    except OSError:
        pass
    try:
        stats["uptime"] = float(Path("/proc/uptime").read_text().split()[0])
    except (OSError, ValueError, IndexError):
        pass
    if config.db_path != ":memory:" and Path(config.db_path).exists():
        stats["db_bytes"] = Path(config.db_path).stat().st_size
    return stats


# ── Reports ─────────────────────────────────────────────────────────────────────────────────────


def _days(app: web.Application, count: int) -> list[str]:
    today = datetime.now(app["zone"]).date()
    return [(today - timedelta(days=offset)).isoformat() for offset in range(count - 1, -1, -1)]


def _series(store: Store, sql: str, days: list[str]) -> list[int]:
    found = {row[0]: row[1] for row in store.q(sql, (days[0],))}
    return [int(found.get(day, 0)) for day in days]


def _days_param(request: web.Request, default: int = 30) -> int:
    try:
        return max(7, min(365, int(request.query.get("days", default))))
    except ValueError:
        return default


def _latest(config: Config) -> dict | None:
    try:
        return json.loads(Path(config.latest_json).read_text())
    except (OSError, ValueError):
        return None


def _active_since(store: Store, seconds: int) -> int:
    return store.one("SELECT COUNT(*) FROM installs WHERE last_seen>=?", (now() - seconds,)) or 0


async def overview(request: web.Request) -> web.Response:
    require(request)
    store: Store = request.app["store"]
    config: Config = request.app["config"]
    days = _days(request.app, 30)
    today, yesterday = days[-1], days[-2]
    downloads = _series(store, "SELECT day, COUNT(*) FROM downloads WHERE day>=? GROUP BY day", days)
    dau = _series(store, "SELECT day, COUNT(*) FROM active_days WHERE day>=? GROUP BY day", days)
    visits = _series(store, "SELECT day, COUNT(*) FROM visits WHERE day>=? GROUP BY day", days)
    first_days = {row[0]: row[1] for row in store.q(
        "SELECT day, COUNT(*) FROM (SELECT id_hash, MIN(day) AS day FROM active_days GROUP BY id_hash) WHERE day>=? GROUP BY day",
        (days[0],))}
    new_installs = [int(first_days.get(day, 0)) for day in days]
    latest = _latest(config)
    latest_code = int((latest or {}).get("version_code") or 0)
    active = config.active_window_days * 86400
    on_latest = store.one("SELECT COUNT(*) FROM installs WHERE platform='android' AND version_code>=? AND last_seen>=?",
                          (latest_code, now() - active)) if latest_code else None
    android_active = store.one("SELECT COUNT(*) FROM installs WHERE platform='android' AND last_seen>=?", (now() - active,))
    probes = last_probe(request.app)
    return web.json_response({
        "days": days,
        "kpis": {
            "downloads_today": downloads[-1], "downloads_yesterday": downloads[-2],
            "downloads_7d": sum(downloads[-7:]), "downloads_prev_7d": sum(downloads[-14:-7]),
            "downloads_total": store.one("SELECT COUNT(*) FROM downloads") or 0,
            "downloads_30d": sum(downloads),
            "dau": dau[-1], "dau_yesterday": dau[-2],
            "wau": store.one("SELECT COUNT(DISTINCT id_hash) FROM active_days WHERE day>=?", (days[-7],)) or 0,
            "mau": store.one("SELECT COUNT(DISTINCT id_hash) FROM active_days WHERE day>=?", (days[0],)) or 0,
            "installs_total": store.one("SELECT COUNT(*) FROM installs") or 0,
            "new_today": new_installs[-1], "new_7d": sum(new_installs[-7:]),
            "live_now": _active_since(store, 30 * 60),
            "visits_today": visits[-1], "visits_7d": sum(visits[-7:]),
            "update_checks_today": store.one("SELECT COUNT(*) FROM update_checks WHERE day=?", (today,)) or 0,
            "live_messages": len(live_messages(store, now())),
            "adoption": (on_latest / android_active) if on_latest is not None and android_active else None,
        },
        "series": {"downloads": downloads, "dau": dau, "visits": visits, "new_installs": new_installs},
        "versions": [dict(row) for row in store.q(
            "SELECT COALESCE(NULLIF(version, ''), '?') AS label, COUNT(*) AS value FROM installs WHERE last_seen>=? "
            "GROUP BY version ORDER BY MAX(version_code) DESC LIMIT 8", (now() - active,))],
        "platforms": [dict(row) for row in store.q(
            "SELECT platform || CASE WHEN platform='android' THEN ' · ' || flavour ELSE '' END AS label, COUNT(*) AS value "
            "FROM installs WHERE last_seen>=? GROUP BY label ORDER BY value DESC", (now() - active,))],
        "countries": [dict(row) for row in store.q(
            "SELECT COALESCE(country, '??') AS label, COUNT(*) AS value FROM installs WHERE last_seen>=? "
            "GROUP BY country ORDER BY value DESC LIMIT 10", (now() - active,))],
        "latest": latest,
        "health": (probes or {}).get("results"),
        "audit": [dict(row) for row in store.q("SELECT at, username, action FROM audit ORDER BY id DESC LIMIT 8")],
        "yesterday": yesterday,
    })


async def downloads_report(request: web.Request) -> web.Response:
    require(request)
    store: Store = request.app["store"]
    count = _days_param(request, 90)
    days = _days(request.app, count)
    unique = _series(store, "SELECT day, COUNT(*) FROM downloads WHERE day>=? GROUP BY day", days)
    hits = _series(store, "SELECT day, SUM(hits) FROM download_hits WHERE day>=? GROUP BY day", days)
    by_file = [dict(row) for row in store.q(
        "SELECT file AS label, COUNT(*) AS value FROM downloads WHERE day>=? GROUP BY file ORDER BY value DESC LIMIT 12",
        (days[0],))]
    for row in by_file:
        row["version"] = version_of_file(row["label"])
    return web.json_response({
        "days": days, "unique": unique, "hits": hits,
        "total": store.one("SELECT COUNT(*) FROM downloads") or 0,
        "bytes": store.one("SELECT SUM(bytes) FROM download_hits WHERE day>=?", (days[0],)) or 0,
        "files": by_file,
        "countries": [dict(row) for row in store.q(
            "SELECT COALESCE(country, '??') AS label, COUNT(*) AS value FROM downloads WHERE day>=? "
            "GROUP BY country ORDER BY value DESC LIMIT 12", (days[0],))],
        "visits": _series(store, "SELECT day, COUNT(*) FROM visits WHERE day>=? GROUP BY day", days),
        "update_checks": _series(store, "SELECT day, COUNT(*) FROM update_checks WHERE day>=? GROUP BY day", days),
    })


async def audience_report(request: web.Request) -> web.Response:
    require(request)
    store: Store = request.app["store"]
    count = _days_param(request, 30)
    days = _days(request.app, count)
    dau = _series(store, "SELECT day, COUNT(*) FROM active_days WHERE day>=? GROUP BY day", days)
    # Retention: of the installs first seen N days ago, how many were active again on or after day N.
    retention = {}
    for horizon in (1, 7, 30):
        cohort_day = (datetime.now(request.app["zone"]).date() - timedelta(days=horizon + 6)).isoformat()
        cohort_end = (datetime.now(request.app["zone"]).date() - timedelta(days=horizon)).isoformat()
        cohort = store.q(
            "SELECT id_hash, MIN(day) AS first FROM active_days GROUP BY id_hash HAVING first>=? AND first<=?",
            (cohort_day, cohort_end))
        if not cohort:
            retention[str(horizon)] = None
            continue
        back = 0
        for row in cohort:
            first = datetime.fromisoformat(row["first"]).date()
            target = (first + timedelta(days=horizon)).isoformat()
            if store.one("SELECT 1 FROM active_days WHERE id_hash=? AND day>=? LIMIT 1", (row["id_hash"], target)):
                back += 1
        retention[str(horizon)] = back / len(cohort)
    heat = [[0] * 24 for _ in range(7)]
    for row in store.q("SELECT weekday, hour, SUM(count) AS c FROM active_hours WHERE day>=? GROUP BY weekday, hour", (days[0],)):
        heat[row["weekday"]][row["hour"]] = row["c"]
    active = request.app["config"].active_window_days * 86400

    def split(column: str, limit: int = 10) -> list[dict]:
        return [dict(row) for row in store.q(
            f"SELECT COALESCE(NULLIF(CAST({column} AS TEXT), ''), '?') AS label, COUNT(*) AS value FROM installs WHERE last_seen>=? "
            f"GROUP BY {column} ORDER BY value DESC LIMIT {limit}", (now() - active,))]

    return web.json_response({
        "days": days, "dau": dau,
        "wau": [store.one("SELECT COUNT(DISTINCT id_hash) FROM active_days WHERE day>? AND day<=?",
                          ((datetime.fromisoformat(day) - timedelta(days=7)).date().isoformat(), day)) or 0 for day in days],
        "retention": retention, "heat": heat,
        "versions": split("version", 12), "langs": split("lang"), "flavours": split("flavour"),
        "platforms": split("platform"), "sdks": split("sdk", 12), "countries": split("country", 12),
    })


async def installs_list(request: web.Request) -> web.Response:
    require(request)
    store: Store = request.app["store"]
    clauses, args = [], []
    for key in ("platform", "flavour", "lang", "country", "version"):
        value = clean_text(request.query.get(key), 32)
        if value:
            clauses.append(f"{key}=?")
            args.append(value.upper() if key == "country" else value)
    query = clean_text(request.query.get("q"), 40)
    if query:
        clauses.append("(id_hash LIKE ? OR version LIKE ? OR country LIKE ?)")
        args += [f"{query}%", f"%{query}%", f"%{query.upper()}%"]
    where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
    try:
        limit = max(1, min(200, int(request.query.get("limit", 50))))
        offset = max(0, int(request.query.get("offset", 0)))
    except ValueError:
        limit, offset = 50, 0
    rows = store.q(f"SELECT id_hash, first_seen, last_seen, platform, flavour, version, version_code, lang, country, sdk, tz, hellos "
                   f"FROM installs {where} ORDER BY last_seen DESC LIMIT ? OFFSET ?", (*args, limit, offset))
    total = store.one(f"SELECT COUNT(*) FROM installs {where}", args)
    return web.json_response({"total": total, "rows": [dict(row) | {"id_hash": row["id_hash"][:12]} for row in rows]})


def _csv(rows: list[sqlite3.Row], columns: list[str], name: str) -> web.Response:
    out = io.StringIO()
    writer = csv.writer(out)
    writer.writerow(columns)
    for row in rows:
        writer.writerow([row[column] for column in columns])
    return web.Response(body=("﻿" + out.getvalue()).encode(), content_type="text/csv",
                        headers={"Content-Disposition": f'attachment; filename="{name}"'})


async def installs_csv(request: web.Request) -> web.Response:
    require(request)
    columns = ["id_hash", "first_seen", "last_seen", "platform", "flavour", "version", "version_code", "lang", "country", "sdk", "tz", "hellos"]
    rows = request.app["store"].q(f"SELECT {', '.join(columns)} FROM installs ORDER BY last_seen DESC")
    return _csv(rows, columns, "pro-chart-installs.csv")


# ── Campaigns ───────────────────────────────────────────────────────────────────────────────────

LINK_PATTERN = re.compile(r"^(coinepro://[\w\-/?=&%.:]*|https://[\w\-.]+(/[\w\-./?=&%#:~+]*)?)$")


def _audience(raw) -> dict:
    raw = raw if isinstance(raw, dict) else {}

    def pick(key: str, allowed: tuple | None = None, upper: bool = False) -> list[str]:
        values = raw.get(key) or []
        values = [clean_text(v, 16) for v in values if isinstance(v, str)][:30]
        values = [v.upper() if upper else v.lower() for v in values if v]
        return [v for v in values if allowed is None or v in allowed]

    audience = {
        "platforms": pick("platforms", PLATFORMS), "flavours": pick("flavours", FLAVOURS),
        "langs": pick("langs", ("fa", "en")),
        "countries": [c for c in pick("countries", upper=True) if re.fullmatch(r"[A-Z]{2}", c)],
    }
    for key in ("min_version_code", "max_version_code"):
        try:
            value = int(raw.get(key) or 0)
        except (TypeError, ValueError):
            value = 0
        if value > 0:
            audience[key] = value
    return audience


def _estimate(store: Store, audience: dict, active_days: int) -> int:
    rows = store.q("SELECT platform, flavour, lang, country, version_code FROM installs WHERE last_seen>=?",
                   (now() - active_days * 86400,))
    return sum(1 for row in rows if _install_matches(audience, dict(row)))


def _message_stats(store: Store, message_id: int) -> dict:
    counts = {row["kind"]: row["n"] for row in store.q(
        "SELECT kind, COUNT(*) AS n FROM message_events WHERE message_id=? GROUP BY kind", (message_id,))}
    delivered, opened = counts.get("delivered", 0), counts.get("opened", 0)
    return {"delivered": delivered, "opened": opened, "dismissed": counts.get("dismissed", 0),
            "open_rate": (opened / delivered) if delivered else None}


def _message_state(row: sqlite3.Row, at: int) -> str:
    if row["cancelled"]:
        return "cancelled"
    if row["draft"]:
        return "draft"
    if row["starts_at"] > at:
        return "scheduled"
    if row["ends_at"] is not None and row["ends_at"] <= at:
        return "ended"
    return "live"


async def messages_list(request: web.Request) -> web.Response:
    require(request)
    store: Store = request.app["store"]
    at = now()
    rows = store.q("SELECT * FROM messages ORDER BY id DESC LIMIT 200")
    out = []
    for row in rows:
        item = dict(row)
        item["audience"] = json.loads(row["audience"] or "{}")
        item["state"] = _message_state(row, at)
        item["stats"] = _message_stats(store, row["id"])
        out.append(item)
    return web.json_response({"messages": out})


async def message_estimate(request: web.Request) -> web.Response:
    require(request, mutating=True)
    body = await read_json(request)
    store: Store = request.app["store"]
    audience = _audience(body.get("audience"))
    return web.json_response({"reach": _estimate(store, audience, request.app["config"].active_window_days),
                              "active": _active_since(store, request.app["config"].active_window_days * 86400)})


async def message_save(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    body = await read_json(request)
    store: Store = request.app["store"]
    kind = clean_text(body.get("kind"), 16)
    if kind not in MESSAGE_KINDS:
        return fail(400, "kind")
    title_fa, body_fa = clean_text(body.get("title_fa"), 80), clean_text(body.get("body_fa"), 400)
    title_en, body_en = clean_text(body.get("title_en"), 80), clean_text(body.get("body_en"), 400)
    if not (title_fa and body_fa) and not (title_en and body_en):
        return fail(400, "text")
    link = clean_text(body.get("link"), 300)
    if link and not LINK_PATTERN.match(link):
        return fail(400, "link")
    coupon = clean_text(body.get("coupon"), 40).upper()
    if coupon and store.one("SELECT 1 FROM coupons WHERE code=?", (coupon,)) is None:
        return fail(400, "coupon")
    try:
        starts_at = int(body.get("starts_at") or now())
        ends_at = int(body["ends_at"]) if body.get("ends_at") else None
    except (TypeError, ValueError):
        return fail(400, "schedule")
    if ends_at is not None and ends_at <= starts_at:
        return fail(400, "schedule")
    values = {
        "kind": kind, "priority": "high" if body.get("priority") == "high" else "normal",
        "title_fa": title_fa, "body_fa": body_fa, "title_en": title_en, "body_en": body_en,
        "cta_fa": clean_text(body.get("cta_fa"), 30), "cta_en": clean_text(body.get("cta_en"), 30),
        "link": link, "coupon": coupon or None, "audience": json.dumps(_audience(body.get("audience"))),
        "starts_at": starts_at, "ends_at": ends_at, "notify": 1 if body.get("notify", True) else 0,
        "draft": 1 if body.get("draft") else 0,
    }
    message_id = body.get("id")
    if message_id:
        row = store.q("SELECT * FROM messages WHERE id=?", (int(message_id),))
        if not row:
            return fail(404, "message")
        if _message_state(row[0], now()) not in ("draft", "scheduled"):
            return fail(409, "already_sent")
        store.x(f"UPDATE messages SET {', '.join(f'{k}=:{k}' for k in values)} WHERE id=:id", {**values, "id": int(message_id)})
        store.audit(session["username"], client_ip(request), "message.update", {"id": int(message_id), "kind": kind})
        return web.json_response({"id": int(message_id)})
    cursor = store.x(
        f"INSERT INTO messages(created_at, created_by, {', '.join(values)}) VALUES (:created_at, :created_by, "
        f"{', '.join(':' + k for k in values)})", {**values, "created_at": now(), "created_by": session["username"]})
    store.audit(session["username"], client_ip(request), "message.create",
                {"id": cursor.lastrowid, "kind": kind, "title": title_fa or title_en, "draft": bool(values["draft"])})
    return web.json_response({"id": cursor.lastrowid})


async def message_cancel(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    store: Store = request.app["store"]
    message_id = int(request.match_info["id"])
    if store.x("UPDATE messages SET cancelled=1 WHERE id=?", (message_id,)).rowcount == 0:
        return fail(404, "message")
    store.audit(session["username"], client_ip(request), "message.cancel", {"id": message_id})
    return web.json_response({"ok": True})


async def message_duplicate(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    store: Store = request.app["store"]
    rows = store.q("SELECT * FROM messages WHERE id=?", (int(request.match_info["id"]),))
    if not rows:
        return fail(404, "message")
    row = dict(rows[0])
    columns = ["kind", "priority", "title_fa", "body_fa", "title_en", "body_en", "cta_fa", "cta_en", "link", "coupon",
               "audience", "notify"]
    cursor = store.x(
        f"INSERT INTO messages(created_at, created_by, starts_at, draft, {', '.join(columns)}) "
        f"VALUES (?, ?, ?, 1, {', '.join('?' for _ in columns)})",
        (now(), session["username"], now(), *[row[c] for c in columns]))
    store.audit(session["username"], client_ip(request), "message.duplicate", {"from": row["id"], "id": cursor.lastrowid})
    return web.json_response({"id": cursor.lastrowid})


# ── Coupons ─────────────────────────────────────────────────────────────────────────────────────


async def coupons_list(request: web.Request) -> web.Response:
    require(request)
    rows = request.app["store"].q("SELECT * FROM coupons ORDER BY created_at DESC")
    return web.json_response({"coupons": [dict(row) for row in rows]})


async def coupon_save(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    body = await read_json(request)
    store: Store = request.app["store"]
    code = clean_text(body.get("code"), 40).upper() or ("PC-" + secrets.token_hex(3).upper())
    if not re.fullmatch(r"[A-Z0-9][A-Z0-9\-]{2,39}", code):
        return fail(400, "code")
    try:
        percent = int(body.get("percent"))
        max_uses = int(body["max_uses"]) if body.get("max_uses") else None
        expires_at = int(body["expires_at"]) if body.get("expires_at") else None
    except (TypeError, ValueError, KeyError):
        return fail(400, "numbers")
    if not 1 <= percent <= 90:
        return fail(400, "percent")
    if store.one("SELECT 1 FROM coupons WHERE code=?", (code,)):
        return fail(409, "exists")
    store.x("INSERT INTO coupons(code, percent, plan, max_uses, expires_at, note, created_at, created_by) VALUES (?,?,?,?,?,?,?,?)",
            (code, percent, clean_text(body.get("plan"), 40) or None, max_uses, expires_at,
             clean_text(body.get("note"), 200), now(), session["username"]))
    store.audit(session["username"], client_ip(request), "coupon.create", {"code": code, "percent": percent})
    return web.json_response({"code": code})


async def coupon_toggle(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    store: Store = request.app["store"]
    code = request.match_info["code"].upper()
    if store.x("UPDATE coupons SET active=1-active WHERE code=?", (code,)).rowcount == 0:
        return fail(404, "coupon")
    store.audit(session["username"], client_ip(request), "coupon.toggle", {"code": code})
    return web.json_response({"ok": True})


async def coupon_delete(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    store: Store = request.app["store"]
    code = request.match_info["code"].upper()
    if store.x("DELETE FROM coupons WHERE code=?", (code,)).rowcount == 0:
        return fail(404, "coupon")
    store.audit(session["username"], client_ip(request), "coupon.delete", {"code": code})
    return web.json_response({"ok": True})


# ── Remote configuration, release, health, audit ────────────────────────────────────────────────


async def config_get(request: web.Request) -> web.Response:
    require(request)
    store: Store = request.app["store"]
    rows = {row["key"]: dict(row) for row in store.q("SELECT key, updated_at, updated_by FROM config")}
    return web.json_response({"config": store.config(), "meta": rows})


def _banner(value) -> dict:
    value = value if isinstance(value, dict) else {}
    return {"on": bool(value.get("on")), "message_fa": clean_text(value.get("message_fa"), 200),
            "message_en": clean_text(value.get("message_en"), 200)}


async def config_put(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    body = await read_json(request)
    store: Store = request.app["store"]
    changes: dict[str, object] = {}
    if "maintenance" in body:
        changes["maintenance"] = _banner(body["maintenance"])
    if "update_nudge" in body:
        changes["update_nudge"] = _banner(body["update_nudge"])
    if "min_version_code" in body:
        try:
            changes["min_version_code"] = max(0, int(body["min_version_code"] or 0))
        except (TypeError, ValueError):
            return fail(400, "min_version_code")
    if "hello_every_hours" in body:
        try:
            changes["hello_every_hours"] = max(1, min(24, int(body["hello_every_hours"])))
        except (TypeError, ValueError):
            return fail(400, "hello_every_hours")
    for key, value in changes.items():
        store.x("INSERT OR REPLACE INTO config(key, value, updated_at, updated_by) VALUES (?,?,?,?)",
                (key, json.dumps(value, ensure_ascii=False), now(), session["username"]))
    store.audit(session["username"], client_ip(request), "config.update", changes)
    return web.json_response({"config": store.config()})


async def release_info(request: web.Request) -> web.Response:
    require(request)
    store: Store = request.app["store"]
    latest = _latest(request.app["config"])
    active = now() - request.app["config"].active_window_days * 86400
    versions = [dict(row) for row in store.q(
        "SELECT COALESCE(NULLIF(version, ''), '?') AS label, version_code AS code, COUNT(*) AS value FROM installs "
        "WHERE platform='android' AND last_seen>=? GROUP BY version, version_code ORDER BY version_code DESC LIMIT 15",
        (active,))]
    files = [dict(row) for row in store.q(
        "SELECT file AS label, COUNT(*) AS value FROM downloads GROUP BY file ORDER BY value DESC LIMIT 15")]
    return web.json_response({"latest": latest, "versions": versions, "files": files})


async def health_report(request: web.Request) -> web.Response:
    require(request)
    store: Store = request.app["store"]
    since = now() - 86400
    history: dict[str, list] = defaultdict(list)
    for row in store.q("SELECT at, target, ok, ms FROM health WHERE at>=? ORDER BY at", (since,)):
        history[row["target"]].append([row["at"], row["ok"], row["ms"]])
    uptime = {row["target"]: row["u"] for row in store.q(
        "SELECT target, AVG(ok) AS u FROM health WHERE at>=? GROUP BY target", (now() - 7 * 86400,))}
    return web.json_response({
        "last": last_probe(request.app), "history": history, "uptime_7d": uptime,
        "server": server_stats(request.app["config"]),
        "ingest": [dict(row) for row in store.q("SELECT path, offset FROM log_state")],
    })


async def health_run(request: web.Request) -> web.Response:
    require(request, mutating=True)
    return web.json_response({"results": await run_probes(request.app)})


async def audit_list(request: web.Request) -> web.Response:
    require(request)
    store: Store = request.app["store"]
    action = clean_text(request.query.get("action"), 40)
    rows = store.q("SELECT * FROM audit WHERE (? = '' OR action LIKE ?) ORDER BY id DESC LIMIT 300", (action, action + "%"))
    return web.json_response({"rows": [dict(row) for row in rows]})


async def audit_csv(request: web.Request) -> web.Response:
    require(request)
    rows = request.app["store"].q("SELECT at, username, ip, action, detail FROM audit ORDER BY id DESC")
    return _csv(rows, ["at", "username", "ip", "action", "detail"], "pro-chart-audit.csv")


# ── The owner's account ─────────────────────────────────────────────────────────────────────────


def password_problem(password: str) -> str | None:
    if len(password) < 10:
        return "too_short"
    if len(set(password)) < 5:
        return "too_simple"
    return None


async def account_password(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    body = await read_json(request)
    store: Store = request.app["store"]
    user = store.q("SELECT * FROM users WHERE username=?", (session["username"],))[0]
    if not verify_password(str(body.get("current") or ""), user["pw_hash"]):
        store.audit(session["username"], client_ip(request), "password.bad_current")
        return fail(401, "bad_current")
    new = str(body.get("new") or "")
    problem = password_problem(new)
    if problem:
        return fail(400, problem)
    store.x("UPDATE users SET pw_hash=? WHERE username=?", (hash_password(new), session["username"]))
    # Every other browser signs in again with the new password.
    store.x("DELETE FROM sessions WHERE username=? AND token_hash<>?", (session["username"], session["token_hash"]))
    store.audit(session["username"], client_ip(request), "password.change")
    return web.json_response({"ok": True})


async def totp_setup(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    store: Store = request.app["store"]
    secret = base64.b32encode(secrets.token_bytes(20)).decode().rstrip("=")
    store.x("UPDATE users SET totp_secret=?, totp_enabled=0 WHERE username=?", (secret, session["username"]))
    uri = f"otpauth://totp/Pro%20Chart:{session['username']}?secret={secret}&issuer=Pro%20Chart&digits=6&period=30"
    return web.json_response({"secret": secret, "uri": uri})


async def totp_enable(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    body = await read_json(request)
    store: Store = request.app["store"]
    user = store.q("SELECT * FROM users WHERE username=?", (session["username"],))[0]
    if not user["totp_secret"]:
        return fail(400, "no_secret")
    step = totp_matches(user["totp_secret"], str(body.get("code") or ""))
    if step is None:
        return fail(400, "bad_code")
    store.x("UPDATE users SET totp_enabled=1, totp_last_step=? WHERE username=?", (step, session["username"]))
    store.audit(session["username"], client_ip(request), "totp.enable")
    return web.json_response({"ok": True})


async def totp_disable(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    body = await read_json(request)
    store: Store = request.app["store"]
    user = store.q("SELECT * FROM users WHERE username=?", (session["username"],))[0]
    if user["totp_enabled"] and totp_matches(user["totp_secret"], str(body.get("code") or "")) is None:
        return fail(400, "bad_code")
    store.x("UPDATE users SET totp_enabled=0, totp_secret=NULL WHERE username=?", (session["username"],))
    store.audit(session["username"], client_ip(request), "totp.disable")
    return web.json_response({"ok": True})


async def sessions_list(request: web.Request) -> web.Response:
    session = require(request)
    rows = request.app["store"].q(
        "SELECT token_hash, created_at, last_seen, ip, agent FROM sessions WHERE username=? ORDER BY last_seen DESC",
        (session["username"],))
    return web.json_response({"sessions": [
        {**dict(row), "token_hash": row["token_hash"][:8], "current": row["token_hash"] == session["token_hash"]}
        for row in rows]})


async def sessions_revoke_others(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    store: Store = request.app["store"]
    removed = store.x("DELETE FROM sessions WHERE username=? AND token_hash<>?",
                      (session["username"], session["token_hash"])).rowcount
    store.audit(session["username"], client_ip(request), "sessions.revoke_others", {"removed": removed})
    return web.json_response({"removed": removed})


# ── Command line ────────────────────────────────────────────────────────────────────────────────


def set_password_cli(args: list[str]) -> int:
    """`python admin.py set-password <username> [--stdin]` — creates the account or resets its password."""
    if not args:
        print("usage: python admin.py set-password <username> [--stdin]", file=sys.stderr)
        return 2
    username = args[0].strip().lower()
    if not re.fullmatch(r"[a-z0-9._-]{3,64}", username):
        print("the username may hold a-z, 0-9, dot, dash and underscore", file=sys.stderr)
        return 2
    if "--stdin" in args:
        password = sys.stdin.readline().rstrip("\n")
    else:
        import getpass
        password = getpass.getpass("password: ")
        if getpass.getpass("again: ") != password:
            print("the two do not match", file=sys.stderr)
            return 1
    problem = password_problem(password)
    if problem:
        print(f"password rejected: {problem}", file=sys.stderr)
        return 1
    store = Store(Config().db_path)
    store.x("INSERT INTO users(username, pw_hash, created_at) VALUES (?,?,?) "
            "ON CONFLICT(username) DO UPDATE SET pw_hash=excluded.pw_hash, failures=0, locked_until=0",
            (username, hash_password(password), now()))
    store.x("DELETE FROM sessions WHERE username=?", (username,))
    store.audit(username, "console", "password.set_from_cli")
    print(f"account {username} is ready")
    return 0


def forget_cli(args: list[str]) -> int:
    """Removes test traffic from the figures (5.31.0).

    `python admin.py forget-install <install_id>` — an install's check-ins and campaign events.
    `python admin.py forget-ip <address> [YYYY-MM-DD]` — that address's downloads, visits and update
    checks, on one day or on every day. Both are written to the audit log.
    """
    if len(args) < 2 or args[0] not in ("forget-install", "forget-ip"):
        print("usage: python admin.py forget-install <install_id> | forget-ip <address> [YYYY-MM-DD]", file=sys.stderr)
        return 2
    store = Store(Config().db_path)
    removed = 0
    if args[0] == "forget-install":
        id_hash = store.anon(args[1].strip())
        for table in ("installs", "active_days", "message_events"):
            removed += store.x(f"DELETE FROM {table} WHERE id_hash=?", (id_hash,)).rowcount
        store.audit("console", "console", "data.forget_install", {"rows": removed})
    else:
        ip_hash = store.anon("ip|" + args[1].strip())
        day = args[2] if len(args) > 2 else None
        if day is not None and not re.fullmatch(r"\d{4}-\d{2}-\d{2}", day):
            print("the day is YYYY-MM-DD", file=sys.stderr)
            return 2
        for table in ("downloads", "visits", "update_checks"):
            if day:
                removed += store.x(f"DELETE FROM {table} WHERE ip_hash=? AND day=?", (ip_hash, day)).rowcount
            else:
                removed += store.x(f"DELETE FROM {table} WHERE ip_hash=?", (ip_hash,)).rowcount
        store.audit("console", "console", "data.forget_ip", {"rows": removed, "day": day})
    print(f"{removed} rows removed")
    return 0


def main() -> None:
    if len(sys.argv) > 1 and sys.argv[1] == "set-password":
        raise SystemExit(set_password_cli(sys.argv[2:]))
    if len(sys.argv) > 1 and sys.argv[1] in ("forget-install", "forget-ip"):
        raise SystemExit(forget_cli(sys.argv[1:]))
    if len(sys.argv) > 1 and sys.argv[1] == "ingest":
        app = build_app(Config(background=False))
        print(f"{ingest_logs(app)} lines read")
        return
    web.run_app(build_app(), host=os.environ.get("ADMIN_HOST", "0.0.0.0"), port=int(os.environ.get("ADMIN_PORT", "8791")),
                access_log=None)


if __name__ == "__main__":
    main()
