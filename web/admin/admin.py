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
    # The two servers that hold the readers' accounts (5.35.0). Each answers its admin bridge only
    # with the shared secret its own `PROCHART_ADMIN_BRIDGE_TOKEN` holds; the token stays on this
    # server and never reaches the browser. An empty token hides that server from «کاربران».
    bridges: dict = field(default_factory=lambda: {
        "tradeyar": {
            "label": "TradeYar — کریپتو",
            "url": os.environ.get("ADMIN_TRADEYAR_BRIDGE_URL",
                                  "https://tradeyar.trade-future.ir/api/mobile/v1/admin-bridge").rstrip("/"),
            "token": os.environ.get("ADMIN_TRADEYAR_BRIDGE_TOKEN", ""),
        },
        "coineprofx": {
            "label": "CoinePro-FX — فارکس",
            "url": os.environ.get("ADMIN_COINEPROFX_BRIDGE_URL",
                                  "https://coineprofx.com/api/user/admin-bridge").rstrip("/"),
            "token": os.environ.get("ADMIN_COINEPROFX_BRIDGE_TOKEN", ""),
        },
    })
    # The web user panel (`/next/panel`, «کاربران وب»). The relay is its only caller and proves it with
    # this secret, the same value as the relay's (at least 32 characters; shorter turns it off).
    panel_token: str = field(default_factory=lambda: os.environ.get("PANEL_INTERNAL_TOKEN", ""))
    relay_url: str = field(default_factory=lambda: os.environ.get("ADMIN_RELAY_URL", "http://web-relay:8790").rstrip("/"))
    panel_history: int = 10

    @property
    def panel_enabled(self) -> bool:
        return len(self.panel_token) >= 32


SESSION_COOKIE = "pca_session"
PROBE_AGENT_PREFIX = "ProChart-Admin/"
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
CREATE TABLE IF NOT EXISTS install_hours (id_hash TEXT NOT NULL, day TEXT NOT NULL, hour INTEGER NOT NULL,
    count INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (id_hash, day, hour));
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

PANEL_MIGRATIONS = [
    # 1 — the web user panel (`/next/panel`) and «کاربران وب».
    """
    CREATE TABLE IF NOT EXISTS web_accounts (
        account TEXT PRIMARY KEY, backend TEXT NOT NULL, backend_id TEXT NOT NULL, email TEXT, email_key TEXT, name TEXT,
        first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL, sign_ins INTEGER NOT NULL DEFAULT 0,
        vip INTEGER NOT NULL DEFAULT 0, paid INTEGER NOT NULL DEFAULT 0, plan TEXT, premium_until TEXT,
        membership TEXT, bridge_id INTEGER, bridge_checked_at INTEGER,
        profile TEXT NOT NULL DEFAULT '{}', prefs TEXT NOT NULL DEFAULT '{}', note TEXT,
        last_device TEXT, last_browser TEXT, last_os TEXT, last_country TEXT);
    CREATE TABLE IF NOT EXISTS web_aliases (
        alias TEXT PRIMARY KEY, account TEXT NOT NULL, backend TEXT NOT NULL, email TEXT, email_key TEXT, name TEXT,
        vip INTEGER NOT NULL DEFAULT 0, paid INTEGER NOT NULL DEFAULT 0, plan TEXT, last_seen INTEGER);
    CREATE INDEX IF NOT EXISTS web_aliases_account ON web_aliases (account);
    CREATE INDEX IF NOT EXISTS web_aliases_email ON web_aliases (email_key);
    CREATE TABLE IF NOT EXISTS web_events (
        id INTEGER PRIMARY KEY AUTOINCREMENT, account TEXT, at INTEGER NOT NULL, kind TEXT NOT NULL, name TEXT,
        detail TEXT, source TEXT NOT NULL DEFAULT 'relay', backend TEXT, sid TEXT, device TEXT, browser TEXT, os TEXT,
        country TEXT, ip_hash TEXT);
    CREATE INDEX IF NOT EXISTS web_events_account ON web_events (account, at);
    CREATE INDEX IF NOT EXISTS web_events_kind ON web_events (kind, at);
    CREATE TABLE IF NOT EXISTS web_trades (
        id INTEGER PRIMARY KEY AUTOINCREMENT, account TEXT, at INTEGER NOT NULL, backend TEXT, method TEXT, path TEXT,
        action TEXT, symbol TEXT, side TEXT, quantity REAL, type TEXT, price REAL, venue TEXT, status INTEGER,
        ok INTEGER NOT NULL DEFAULT 0, order_id TEXT, state TEXT, detail TEXT, fields TEXT, sid TEXT, device TEXT);
    CREATE INDEX IF NOT EXISTS web_trades_account ON web_trades (account, at);
    CREATE INDEX IF NOT EXISTS web_trades_at ON web_trades (at);
    CREATE TABLE IF NOT EXISTS web_workspace (
        account TEXT NOT NULL, kind TEXT NOT NULL, version INTEGER NOT NULL, data TEXT NOT NULL, size INTEGER NOT NULL,
        updated_at INTEGER NOT NULL, device TEXT, PRIMARY KEY (account, kind));
    CREATE TABLE IF NOT EXISTS web_workspace_history (
        account TEXT NOT NULL, kind TEXT NOT NULL, version INTEGER NOT NULL, data TEXT NOT NULL, size INTEGER NOT NULL,
        saved_at INTEGER NOT NULL, device TEXT, restored_from INTEGER, PRIMARY KEY (account, kind, version));
    CREATE TABLE IF NOT EXISTS web_inbox (
        id INTEGER PRIMARY KEY AUTOINCREMENT, account TEXT, created_at INTEGER NOT NULL, created_by TEXT,
        kind TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, link TEXT, ticket_id INTEGER);
    CREATE INDEX IF NOT EXISTS web_inbox_account ON web_inbox (account, created_at);
    CREATE TABLE IF NOT EXISTS web_inbox_state (
        message_id INTEGER NOT NULL, account TEXT NOT NULL, read_at INTEGER, dismissed INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY (message_id, account));
    CREATE TABLE IF NOT EXISTS web_tickets (
        id INTEGER PRIMARY KEY AUTOINCREMENT, account TEXT NOT NULL, subject TEXT NOT NULL, category TEXT NOT NULL,
        status TEXT NOT NULL DEFAULT 'open', created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
        unread_user INTEGER NOT NULL DEFAULT 0, unread_owner INTEGER NOT NULL DEFAULT 1);
    CREATE INDEX IF NOT EXISTS web_tickets_account ON web_tickets (account, updated_at);
    CREATE TABLE IF NOT EXISTS web_ticket_messages (
        id INTEGER PRIMARY KEY AUTOINCREMENT, ticket_id INTEGER NOT NULL, at INTEGER NOT NULL, author TEXT NOT NULL,
        operator TEXT, body TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS web_attachments (
        id INTEGER PRIMARY KEY AUTOINCREMENT, ticket_id INTEGER NOT NULL, message_id INTEGER NOT NULL, account TEXT NOT NULL,
        name TEXT, type TEXT NOT NULL, size INTEGER NOT NULL, data BLOB NOT NULL, created_at INTEGER NOT NULL);
    """,
]


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
        # The web user panel's tables, one numbered step at a time: a step runs once, in order, and
        # a later change is a new step at the end of the list, never an edit of an old one.
        done = int(self.meta("panel_schema") or 0)
        for number, step in enumerate(PANEL_MIGRATIONS[done:], start=done + 1):
            self.db.executescript(step)
            self._set_meta("panel_schema", str(number))

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
    # The readers' accounts, on the two servers that hold them (5.35.0).
    app.router.add_get(f"{api}/users/backends", users_backends)
    app.router.add_get(r"/admin/api/users/{backend:[a-z]+}/stats", users_stats)
    app.router.add_get(r"/admin/api/users/{backend:[a-z]+}/list", users_list)
    app.router.add_get(r"/admin/api/users/{backend:[a-z]+}/list.csv", users_csv)
    app.router.add_get(r"/admin/api/users/{backend:[a-z]+}/{id:\d+}", users_detail)
    app.router.add_post(r"/admin/api/users/{backend:[a-z]+}/{id:\d+}/{action:[a-z-]+}", users_action)
    app.router.add_put(r"/admin/api/users/{backend:[a-z]+}/{id:\d+}/note", users_note)
    app.router.add_delete(r"/admin/api/users/{backend:[a-z]+}/{id:\d+}", users_delete)
    # The web user panel: the relay's internal routes, and «کاربران وب» (web accounts).
    install_panel(app)

    app.on_startup.append(_start)
    app.on_cleanup.append(_stop)
    return app


async def _start(app: web.Application) -> None:
    app["state"]["http"] = aiohttp.ClientSession(timeout=aiohttp.ClientTimeout(total=12),
                                                 headers={"User-Agent": PROBE_AGENT_PREFIX + "1 (health probe)"})
    app["state"]["tasks"] = []
    if app["config"].background:
        app["state"]["tasks"].append(asyncio.create_task(_every(app["config"].ingest_every_seconds, lambda: ingest_logs(app))))
        app["state"]["tasks"].append(asyncio.create_task(_every(app["config"].probe_every_seconds, lambda: run_probes(app))))
        app["state"]["tasks"].append(asyncio.create_task(_every(6 * 3600, lambda: prune_panel(app["store"]))))


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
    if request.path.startswith("/api/app/") or request.path.startswith("/internal/"):
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
    # Which install the hour's count came from, so `forget-install` can take its share back out.
    store.x("INSERT INTO install_hours(id_hash, day, hour, count) VALUES (?, ?, ?, 1) "
            "ON CONFLICT(id_hash, day, hour) DO UPDATE SET count=count+1", (record["id_hash"], day, local.hour))
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
    # The console's own health probes (5.32.0): every five minutes it fetches the terminal and the
    # update channel, and those are not a reader visiting or an install checking for an update.
    if _header(headers, "User-Agent").startswith(PROBE_AGENT_PREFIX):
        return
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


# ── Readers' accounts (5.35.0) ─────────────────────────────────────────────────────────────────
#
# The accounts themselves live on TradeYar and CoinePro-FX. Each exposes the same admin bridge
# (`/admin-bridge/…`), opened by a shared secret; this console is its only caller. The browser
# talks to this server, never to the bridges: the secret stays here, every action is checked
# against the console's own session and CSRF token, and every action is written to the audit log.

USER_ACTIONS = ("ban", "unban", "sign-out", "verify-email", "password-reset", "push", "grant", "revoke-pro")
USER_LIST_PARAMS = ("q", "status", "platform", "scope", "sort", "page", "size")


def _bridge(request: web.Request) -> tuple[str, dict]:
    name = request.match_info["backend"]
    bridge = request.app["config"].bridges.get(name)
    if not bridge:
        raise web.HTTPNotFound(text=json.dumps({"error": "unknown_backend"}), content_type="application/json")
    if not bridge.get("token") or not bridge.get("url"):
        raise web.HTTPServiceUnavailable(text=json.dumps({"error": "bridge_not_configured"}),
                                         content_type="application/json")
    return name, bridge


async def bridge_call(request: web.Request, bridge: dict, method: str, path: str, *,
                      params: dict | None = None, body: dict | None = None, operator: str = "console"):
    """One call to a bridge. (status, decoded body); 502 when the server cannot be reached."""
    http: aiohttp.ClientSession = request.app["state"]["http"]
    headers = {"X-ProChart-Admin": bridge["token"], "X-ProChart-Operator": operator[:64],
               "Accept": "application/json"}
    try:
        async with http.request(method, bridge["url"] + path, params=params, json=body, headers=headers,
                                timeout=aiohttp.ClientTimeout(total=25), allow_redirects=False) as response:
            text = await response.text()
            try:
                data = json.loads(text) if text else {}
            except ValueError:
                data = {"error": "bad_upstream_body"}
            return response.status, data
    except (aiohttp.ClientError, asyncio.TimeoutError) as error:
        return 502, {"error": "backend_unreachable", "detail": type(error).__name__}


def _relay(status: int, data) -> web.Response:
    if status == 404 and isinstance(data, dict) and data.get("detail") in (None, "", "Not Found"):
        # A bridge with no token answers 404 for everything: say so rather than «not found».
        return fail(503, "bridge_disabled_on_server")
    if status == 401:
        return fail(502, "bridge_token_mismatch")
    return web.json_response(data, status=status if status < 500 or status == 502 else 502)


async def users_backends(request: web.Request) -> web.Response:
    require(request)
    return web.json_response({"backends": [
        {"key": key, "label": value["label"], "configured": bool(value.get("token") and value.get("url"))}
        for key, value in request.app["config"].bridges.items()
    ]})


async def users_stats(request: web.Request) -> web.Response:
    require(request)
    _, bridge = _bridge(request)
    status, data = await bridge_call(request, bridge, "GET", "/stats", params={"tz": request.app["config"].timezone})
    return _relay(status, data)


def _list_params(request: web.Request) -> dict:
    params = {}
    for name in USER_LIST_PARAMS:
        value = clean_text(request.query.get(name), 120)
        if value:
            params[name] = value
    return params


async def users_list(request: web.Request) -> web.Response:
    require(request)
    _, bridge = _bridge(request)
    status, data = await bridge_call(request, bridge, "GET", "/users", params=_list_params(request))
    return _relay(status, data)


USER_CSV_COLUMNS = ["id", "email", "full_name", "phone", "email_verified", "has_password", "google", "telegram",
                    "created_at", "signup_platform", "signup_flavor", "signup_method", "signup_app_version",
                    "last_seen_at", "last_platform", "last_app_version", "sign_ins", "banned", "ban_reason", "note"]


async def users_csv(request: web.Request) -> web.Response:
    session = require(request)
    name, bridge = _bridge(request)
    params = {**_list_params(request), "size": "500"}
    rows: list[dict] = []
    for page in range(1, 41):  # 20,000 accounts at most in one file
        status, data = await bridge_call(request, bridge, "GET", "/users", params={**params, "page": str(page)})
        if status != 200:
            return _relay(status, data)
        batch = data.get("users") or []
        rows.extend(batch)
        if len(batch) < 500:
            break
    request.app["store"].audit(session["username"], client_ip(request), "users.export",
                               {"backend": name, "rows": len(rows), "filters": params})
    out = io.StringIO()
    writer = csv.writer(out)
    writer.writerow(USER_CSV_COLUMNS)
    for row in rows:
        writer.writerow(["" if row.get(column) is None else row.get(column) for column in USER_CSV_COLUMNS])
    return web.Response(body=("﻿" + out.getvalue()).encode(), content_type="text/csv", charset="utf-8",
                        headers={"Content-Disposition": f'attachment; filename="pro-chart-users-{name}.csv"'})


async def users_detail(request: web.Request) -> web.Response:
    require(request)
    _, bridge = _bridge(request)
    status, data = await bridge_call(request, bridge, "GET", f"/users/{int(request.match_info['id'])}")
    return _relay(status, data)


async def users_action(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    name, bridge = _bridge(request)
    action = request.match_info["action"]
    if action not in USER_ACTIONS:
        return fail(404, "unknown_action")
    user_id = int(request.match_info["id"])
    body: dict | None = None
    if action == "ban":
        raw = await read_json(request)
        body = {"reason": clean_text(raw.get("reason"), 500)}
    elif action == "push":
        raw = await read_json(request)
        title, text = clean_text(raw.get("title"), 120), clean_text(raw.get("body"), 600)
        if not title or not text:
            return fail(400, "title_and_body_required")
        body = {"title": title, "body": text}
    elif action == "grant":
        raw = await read_json(request)
        plan = raw.get("plan") if raw.get("plan") in ("monthly", "quarterly", "yearly") else "monthly"
        try:
            days = max(1, min(3660, int(raw.get("days") or 30)))
        except (TypeError, ValueError):
            return fail(400, "days")
        body = {"plan": plan, "days": days}
    status, data = await bridge_call(request, bridge, "POST", f"/users/{user_id}/{action}", body=body,
                                     operator=session["username"])
    if status < 300:
        detail = {"backend": name, "user": user_id}
        if body and action != "push":
            detail.update(body)
        elif action == "push":
            detail["title"] = body["title"]
        request.app["store"].audit(session["username"], client_ip(request), f"users.{action}", detail)
    return _relay(status, data)


async def users_note(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    name, bridge = _bridge(request)
    raw = await read_json(request)
    user_id = int(request.match_info["id"])
    note = clean_text(raw.get("note"), 2000)
    status, data = await bridge_call(request, bridge, "PUT", f"/users/{user_id}/note", body={"note": note},
                                     operator=session["username"])
    if status < 300:
        request.app["store"].audit(session["username"], client_ip(request), "users.note",
                                   {"backend": name, "user": user_id})
    return _relay(status, data)


async def users_delete(request: web.Request) -> web.Response:
    """Deleting an account cannot be undone: the owner types its email address to confirm it."""
    session = require(request, mutating=True)
    name, bridge = _bridge(request)
    raw = await read_json(request)
    user_id = int(request.match_info["id"])
    status, account = await bridge_call(request, bridge, "GET", f"/users/{user_id}")
    if status != 200:
        return _relay(status, account)
    typed = clean_text(raw.get("confirm"), 255).lower()
    expected = (account.get("email") or str(user_id)).strip().lower()
    if not typed or not hmac.compare_digest(typed, expected):
        return fail(400, "confirm_mismatch")
    status, data = await bridge_call(request, bridge, "DELETE", f"/users/{user_id}", operator=session["username"])
    if status < 300:
        request.app["store"].audit(session["username"], client_ip(request), "users.delete",
                                   {"backend": name, "user": user_id})
    return _relay(status, data)


# ── The web user panel ──────────────────────────────────────────────────────────────────────────
#
# `/next/panel` is the reader's own panel in the web app, and «کاربران وب» is the owner's view of
# it. The browser never talks to this service for it: the relay (web/relay/relay.py) holds the
# reader's session, asks the reader's backend who they are, and calls `/internal/panel/…` here with
# the shared secret and that verified identity in `X-Panel-Identity`. The relay also posts what it
# saw — sign-ins, sign-outs, trading calls — to `/internal/panel/events`. Caddy never routes
# `/internal/*`; the secret is the second lock.
#
# A web account is `<backend>:<id>` (tradeyar:7). A reader signed in to both platforms in one
# browser has two; the first one seen (TradeYar first) keeps the panel's data, the other is an alias
# of it, so the workspace does not split when the forex reader is given a crypto account too.

PANEL_BACKENDS = ("tradeyar", "coineprofx")
PANEL_ALIAS = re.compile(r"^(tradeyar|coineprofx):[A-Za-z0-9_.-]{1,64}$")
PANEL_KINDS = ("layouts", "settings", "drawings", "templates", "watchlist")
PANEL_KIND_LIMIT = {"drawings": 1024 * 1024}
PANEL_KIND_DEFAULT_LIMIT = 256 * 1024
PANEL_BODY_LIMIT = 1600 * 1024
PANEL_TICKET_CATEGORIES = ("account", "billing", "trading", "bug", "idea", "other")
PANEL_TICKET_STATES = ("open", "answered", "closed")
PANEL_ATTACHMENT_LIMIT = 300 * 1024
PANEL_ATTACHMENTS_PER_MESSAGE = 3
PANEL_INBOX_KINDS = ("info", "offer", "warning", "update", "ticket")
PANEL_CLIENT_KINDS = ("error", "feature", "page")
PANEL_SECURITY_KINDS = ("signin", "signout", "signin_failed", "refresh_failed", "session_revoked", "erase")
# Per account, as (requests, seconds). Every write the panel accepts is counted against one of these.
PANEL_LIMITS = {
    "write": (60, 60), "events": (30, 60), "ticket": (5, 3600), "reply": (20, 600), "export": (6, 3600),
    "relay": (600, 60),
}
# How long each kind of line is kept, in days.
PANEL_RETENTION = {"page": 90, "feature": 90, "error": 60}
PANEL_DEFAULT_RETENTION = 365
PANEL_TRADE_RETENTION = 730
PANEL_PREFS = {"autosync": bool, "sync_kinds": list, "digest": bool, "lang": str, "theme": str, "sounds": bool}
TRADE_ACTIONS = (
    (re.compile(r"^trade/orders$"), {"POST": "order"}),
    (re.compile(r"^trade/orders/[^/]+$"), {"PATCH": "amend", "PUT": "amend", "DELETE": "cancel"}),
    (re.compile(r"^trade/positions/close$"), {"POST": "close"}),
    (re.compile(r"^trade/positions/protection$"), {"POST": "protection", "PUT": "protection", "PATCH": "protection"}),
    (re.compile(r"^trade/leverage$"), {"POST": "leverage", "PUT": "leverage", "PATCH": "leverage"}),
    (re.compile(r"^executions$"), {"POST": "execution"}),
    (re.compile(r"^executions/[^/]+/close$"), {"POST": "execution_close"}),
    (re.compile(r"^venues/[^/]+$"), {"POST": "venue_connect", "PUT": "venue_connect", "DELETE": "venue_disconnect"}),
)


def email_key(secret: str, email: str) -> str:
    """The keyed hash the relay puts on a refused sign-in's address (relay.py `email_hash`)."""
    email = (email or "").strip().lower()
    return hmac.new(secret.encode(), email.encode(), hashlib.sha256).hexdigest()[:32] if email and secret else ""


def _jsonish(value, fallback):
    try:
        out = json.loads(value) if isinstance(value, str) and value else fallback
    except ValueError:
        return fallback
    return out if isinstance(out, type(fallback)) else fallback


def install_panel(app: web.Application) -> None:
    app["panel_limits"] = {name: RateLimit(n, window) for name, (n, window) in PANEL_LIMITS.items()}
    r = app.router
    base = "/internal/panel"
    r.add_post(f"{base}/events", panel_events_in)
    r.add_get(f"{base}/me", panel_me)
    r.add_put(f"{base}/profile", panel_profile)
    r.add_put(f"{base}/prefs", panel_prefs)
    r.add_get(f"{base}/activity", panel_activity)
    r.add_get(f"{base}/security", panel_security)
    r.add_post(f"{base}/event", panel_client_events)
    r.add_get(f"{base}/trades", panel_trades)
    r.add_get(f"{base}/workspace", panel_workspace_list)
    r.add_get(base + r"/workspace/{kind:[a-z]+}", panel_workspace_get)
    r.add_put(base + r"/workspace/{kind:[a-z]+}", panel_workspace_put)
    r.add_get(base + r"/workspace/{kind:[a-z]+}/versions", panel_workspace_versions)
    r.add_get(base + r"/workspace/{kind:[a-z]+}/versions/{version:\d+}", panel_workspace_version)
    r.add_post(base + r"/workspace/{kind:[a-z]+}/restore", panel_workspace_restore)
    r.add_get(f"{base}/inbox", panel_inbox)
    r.add_post(f"{base}/inbox/read-all", panel_inbox_read_all)
    r.add_post(base + r"/inbox/{id:\d+}/read", panel_inbox_read)
    r.add_delete(base + r"/inbox/{id:\d+}", panel_inbox_dismiss)
    r.add_get(f"{base}/tickets", panel_tickets)
    r.add_post(f"{base}/tickets", panel_ticket_new)
    r.add_get(base + r"/tickets/{id:\d+}", panel_ticket)
    r.add_post(base + r"/tickets/{id:\d+}/messages", panel_ticket_reply)
    r.add_post(base + r"/tickets/{id:\d+}/close", panel_ticket_close)
    r.add_get(base + r"/tickets/{id:\d+}/attachments/{aid:\d+}", panel_attachment)
    r.add_get(f"{base}/export", panel_export)
    r.add_delete(f"{base}/data", panel_erase)

    api = "/admin/api/web"
    account = r"{account:(tradeyar|coineprofx):[A-Za-z0-9_.-]{1,64}}"
    r.add_get(f"{api}/overview", web_overview)
    r.add_get(f"{api}/accounts", web_accounts)
    r.add_get(f"{api}/accounts.csv", web_accounts_csv)
    r.add_get(f"{api}/accounts/{account}", web_account)
    r.add_get(f"{api}/accounts/{account}/timeline", web_timeline)
    r.add_post(f"{api}/accounts/{account}/refresh", web_refresh)
    r.add_post(f"{api}/accounts/{account}/revoke", web_revoke)
    r.add_put(f"{api}/accounts/{account}/note", web_note)
    r.add_get(f"{api}/accounts/{account}/workspace/" + r"{kind:[a-z]+}/{version:\d+}", web_workspace_version)
    r.add_post(f"{api}/accounts/{account}/workspace/" + r"{kind:[a-z]+}/restore", web_workspace_restore)
    r.add_get(f"{api}/trades", web_trades)
    r.add_get(f"{api}/trades.csv", web_trades_csv)
    r.add_get(f"{api}/tickets", web_tickets)
    r.add_get(api + r"/tickets/{id:\d+}", web_ticket)
    r.add_post(api + r"/tickets/{id:\d+}/reply", web_ticket_reply)
    r.add_post(api + r"/tickets/{id:\d+}/status", web_ticket_status)
    r.add_get(api + r"/attachments/{aid:\d+}", web_attachment)
    r.add_get(f"{api}/messages", web_messages)
    r.add_post(f"{api}/messages", web_message_send)
    r.add_get(f"{api}/errors", web_errors)


# ── Reading the relay's calls ─────────────────────────────────────────────────────────────────────


def _panel_refuse(status: int, error: str, **extra) -> web.HTTPException:
    kind = {400: web.HTTPBadRequest, 401: web.HTTPUnauthorized, 403: web.HTTPForbidden, 404: web.HTTPNotFound,
            409: web.HTTPConflict, 413: web.HTTPRequestEntityTooLarge, 429: web.HTTPTooManyRequests,
            503: web.HTTPServiceUnavailable}[status]
    if status == 413:
        return web.HTTPRequestEntityTooLarge(max_size=PANEL_BODY_LIMIT, actual_size=0,
                                             text=json.dumps({"error": error, **extra}), content_type="application/json")
    return kind(text=json.dumps({"error": error, **extra}), content_type="application/json")


def _relay_only(request: web.Request) -> None:
    """The secret, compared in constant time. A wrong or missing one is told nothing but «no»."""
    config: Config = request.app["config"]
    if not config.panel_enabled:
        raise _panel_refuse(503, "panel_off")
    sent = request.headers.get("X-Panel-Token", "")
    if not sent or not hmac.compare_digest(sent.encode(), config.panel_token.encode()):
        raise _panel_refuse(404, "not_found")


async def panel_body(request: web.Request, limit: int = PANEL_BODY_LIMIT) -> dict:
    """The JSON body, read up to [limit] bytes — past it the call is refused, not truncated."""
    if request.content_length is not None and request.content_length > limit:
        raise _panel_refuse(413, "too_large")
    chunks, size = [], 0
    async for chunk in request.content.iter_chunked(64 * 1024):
        size += len(chunk)
        if size > limit:
            raise _panel_refuse(413, "too_large")
        chunks.append(chunk)
    try:
        body = json.loads(b"".join(chunks) or b"{}")
    except ValueError:
        raise _panel_refuse(400, "invalid_json")
    if not isinstance(body, dict):
        raise _panel_refuse(400, "invalid_json")
    return body


def _clean_identity(raw) -> list[dict]:
    out = []
    for item in raw if isinstance(raw, list) else []:
        if not isinstance(item, dict) or item.get("backend") not in PANEL_BACKENDS:
            continue
        ident = clean_text(item.get("id"), 64)
        if not ident or not PANEL_ALIAS.match(f"{item['backend']}:{ident}"):
            continue
        out.append({"backend": item["backend"], "id": ident, "email": clean_text(item.get("email"), 254).lower(),
                    "name": clean_text(item.get("name"), 120), "vip": bool(item.get("vip")),
                    "paid": bool(item.get("paid")), "plan": clean_text(item.get("plan"), 40)})
    return out


def _client(raw: dict) -> dict:
    return {
        "sid": clean_text(raw.get("sid"), 32), "browser": clean_text(raw.get("browser"), 32),
        "os": clean_text(raw.get("os"), 32), "device": clean_text(raw.get("device"), 16),
        "country": (clean_text(raw.get("country"), 2).upper() or None), "ip_hash": clean_text(raw.get("ip_hash"), 32),
    }


def panel_account(app: web.Application, identity: list[dict], client: dict, at: int, touch: bool = True) -> str | None:
    """The canonical web account for the aliases a session carries, created on first sight."""
    store: Store = app["store"]
    secret = app["config"].panel_token
    aliases = [f"{who['backend']}:{who['id']}" for who in identity]
    if not aliases:
        return None
    known = [store.one("SELECT account FROM web_aliases WHERE alias=?", (alias,)) for alias in aliases]
    account = next((a for a in known if a), None) or aliases[0]
    lead = next((who for who in identity if f"{who['backend']}:{who['id']}" == account), identity[0])
    for who, alias, existing in zip(identity, aliases, known):
        store.x(
            "INSERT INTO web_aliases(alias, account, backend, email, email_key, name, vip, paid, plan, last_seen) "
            "VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT(alias) DO UPDATE SET email=COALESCE(NULLIF(excluded.email, ''), email), "
            "email_key=COALESCE(NULLIF(excluded.email_key, ''), email_key), name=COALESCE(NULLIF(excluded.name, ''), name), "
            "vip=excluded.vip, paid=excluded.paid, plan=excluded.plan, last_seen=excluded.last_seen",
            (alias, existing or account, who["backend"], who["email"], email_key(secret, who["email"]), who["name"],
             int(who["vip"]), int(who["paid"]), who["plan"], at))
    backend, backend_id = account.split(":", 1)
    vip = any(who["vip"] for who in identity)
    paid = any(who["paid"] for who in identity)
    plan = next((who["plan"] for who in identity if who["plan"]), "")
    store.x(
        "INSERT INTO web_accounts(account, backend, backend_id, email, email_key, name, first_seen, last_seen, vip, paid, plan, "
        "last_device, last_browser, last_os, last_country) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
        "ON CONFLICT(account) DO UPDATE SET email=COALESCE(NULLIF(excluded.email, ''), email), "
        "email_key=COALESCE(NULLIF(excluded.email_key, ''), email_key), name=COALESCE(NULLIF(excluded.name, ''), name), "
        "last_seen=MAX(last_seen, excluded.last_seen), vip=excluded.vip, paid=excluded.paid, "
        "plan=COALESCE(NULLIF(excluded.plan, ''), plan), last_device=COALESCE(NULLIF(excluded.last_device, ''), last_device), "
        "last_browser=COALESCE(NULLIF(excluded.last_browser, ''), last_browser), last_os=COALESCE(NULLIF(excluded.last_os, ''), last_os), "
        "last_country=COALESCE(excluded.last_country, last_country)" if touch else
        "INSERT OR IGNORE INTO web_accounts(account, backend, backend_id, email, email_key, name, first_seen, last_seen, vip, paid, "
        "plan, last_device, last_browser, last_os, last_country) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        (account, backend, backend_id, lead["email"], email_key(secret, lead["email"]), lead["name"], at, at, int(vip),
         int(paid), plan, client.get("device") or "", client.get("browser") or "", client.get("os") or "", client.get("country")))
    return account


def panel_reader(request: web.Request) -> dict:
    """Who is calling: the relay's secret, then the identity it verified with the backend."""
    _relay_only(request)
    raw = request.headers.get("X-Panel-Identity", "")
    try:
        decoded = json.loads(base64.urlsafe_b64decode(raw + "=" * (-len(raw) % 4)))
    except (ValueError, TypeError):
        raise _panel_refuse(401, "no_identity")
    if not isinstance(decoded, dict):
        raise _panel_refuse(401, "no_identity")
    identity = _clean_identity(decoded.get("identity"))
    client = _client(decoded)
    account = panel_account(request.app, identity, client, now())
    if account is None:
        raise _panel_refuse(401, "no_identity")
    store: Store = request.app["store"]
    aliases = [row["alias"] for row in store.q("SELECT alias FROM web_aliases WHERE account=?", (account,))] or [account]
    return {"account": account, "aliases": aliases, "identity": identity, **client}


def panel_limit(request: web.Request, reader: dict, bucket: str) -> None:
    if not request.app["panel_limits"][bucket].allow(reader["account"]):
        raise _panel_refuse(429, "slow_down")


def panel_log(store: Store, account: str | None, kind: str, name: str = "", detail=None, source: str = "relay",
              client: dict | None = None, backend: str | None = None, at: int | None = None) -> None:
    client = client or {}
    text = None if detail is None else json.dumps(detail, ensure_ascii=False)[:4000]
    store.x("INSERT INTO web_events(account, at, kind, name, detail, source, backend, sid, device, browser, os, country, ip_hash) "
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (account, at or now(), kind, name[:80], text, source, backend, client.get("sid"), client.get("device"),
             client.get("browser"), client.get("os"), client.get("country"), client.get("ip_hash")))


def trade_action(method: str, path: str) -> str:
    for pattern, by_method in TRADE_ACTIONS:
        if pattern.match(path):
            return by_method.get(method, method.lower())
    return method.lower()


def _number(value) -> float | None:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if number == number and abs(number) < 1e15 else None


async def panel_events_in(request: web.Request) -> web.Response:
    """What the relay saw. Each line is the relay's word; the browser has no way to write here."""
    _relay_only(request)
    body = await panel_body(request, 512 * 1024)
    events = body.get("events")
    if not isinstance(events, list):
        raise _panel_refuse(400, "events")
    store: Store = request.app["store"]
    stored = 0
    store.x("BEGIN")
    try:
        for event in events[:500]:
            if not isinstance(event, dict):
                continue
            kind = clean_text(event.get("kind"), 32)
            at = int(_number(event.get("at")) or now())
            identity = _clean_identity(event.get("identity"))
            client = _client(event)
            backend = event.get("backend") if event.get("backend") in PANEL_BACKENDS else None
            account = panel_account(request.app, identity, client, at, touch=kind != "session_revoked") if identity else None
            if kind == "signin" and account:
                # One sign-in on the page reaches both platforms (CoinePro-FX, then TradeYar through the
                # account link): the relay reports each, the console counts one per browser and minute.
                if not (client.get("sid") and store.one(
                        "SELECT 1 FROM web_events WHERE account=? AND kind='signin' AND sid=? AND at BETWEEN ? AND ?",
                        (account, client["sid"], at - 60, at + 60))):
                    store.x("UPDATE web_accounts SET sign_ins=sign_ins+1 WHERE account=?", (account,))
                    panel_log(store, account, "signin", clean_text(event.get("method"), 16), None, "relay", client, backend, at)
            elif kind == "signin_failed":
                key = clean_text(event.get("email_hash"), 64)
                if key and backend:
                    account = store.one("SELECT account FROM web_aliases WHERE email_key=? AND backend=?", (key, backend))
                panel_log(store, account, "signin_failed", clean_text(event.get("method"), 16), None, "relay", client, backend, at)
            elif kind in ("signout", "refresh_failed"):
                panel_log(store, account, kind, clean_text(event.get("how"), 16), None, "relay", client, backend, at)
            elif kind == "session_revoked":
                panel_log(store, account, kind, clean_text(event.get("by"), 16),
                          {"operator": clean_text(event.get("operator"), 64), "target": clean_text(event.get("target"), 32)},
                          "relay", client, backend, at)
            elif kind == "trade":
                fields = event.get("fields") if isinstance(event.get("fields"), dict) else {}
                fields = {clean_text(k, 32): (v if isinstance(v, (int, float, bool)) or v is None else clean_text(v, 64))
                          for k, v in list(fields.items())[:24]}
                method = clean_text(event.get("method"), 8).upper()
                path = clean_text(event.get("path"), 120)
                status = int(_number(event.get("status")) or 0)
                action = trade_action(method, path)
                venue = fields.get("venue") or (path.split("/")[1] if path.startswith("venues/") else None)
                store.x(
                    "INSERT INTO web_trades(account, at, backend, method, path, action, symbol, side, quantity, type, price, venue, "
                    "status, ok, order_id, state, detail, fields, sid, device) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    (account, at, backend, method, path, action, clean_text(fields.get("symbol"), 24).upper() or None,
                     clean_text(fields.get("side") or fields.get("direction"), 8).lower() or None,
                     _number(fields.get("quantity") if fields.get("quantity") is not None else fields.get("volume")),
                     clean_text(fields.get("type"), 16) or None, _number(fields.get("price")), venue,
                     status, int(200 <= status < 300), clean_text(event.get("order_id"), 64) or None,
                     clean_text(event.get("state"), 32) or None, clean_text(event.get("detail"), 200) or None,
                     json.dumps(fields, ensure_ascii=False), client["sid"], client["device"]))
                panel_log(store, account, "trade", action,
                          {"symbol": fields.get("symbol"), "side": fields.get("side"), "status": status}, "relay", client, backend, at)
            else:
                continue
            stored += 1
        store.x("COMMIT")
    except Exception:
        store.x("ROLLBACK")
        raise
    return web.json_response({"stored": stored})


# ── The reader's panel ───────────────────────────────────────────────────────────────────────────


def _account_row(store: Store, account: str):
    rows = store.q("SELECT * FROM web_accounts WHERE account=?", (account,))
    return rows[0] if rows else None


def _inbox_rows(store: Store, account: str, limit: int = 100) -> list[dict]:
    row = _account_row(store, account)
    since = (row["first_seen"] if row else now()) - 30 * 86400
    rows = store.q(
        "SELECT m.*, s.read_at, s.dismissed FROM web_inbox m LEFT JOIN web_inbox_state s ON s.message_id=m.id AND s.account=? "
        "WHERE (m.account=? OR (m.account IS NULL AND m.created_at>=?)) AND COALESCE(s.dismissed, 0)=0 "
        "ORDER BY m.created_at DESC, m.id DESC LIMIT ?", (account, account, since, limit))
    return [{"id": r["id"], "kind": r["kind"], "title": r["title"], "body": r["body"], "link": r["link"] or "",
             "ticket_id": r["ticket_id"], "created_at": r["created_at"], "read": r["read_at"] is not None,
             "broadcast": r["account"] is None} for r in rows]


def _workspace_meta(store: Store, account: str) -> list[dict]:
    rows = {r["kind"]: r for r in store.q("SELECT kind, version, size, updated_at, device FROM web_workspace WHERE account=?", (account,))}
    counts = {r["kind"]: r["n"] for r in store.q(
        "SELECT kind, COUNT(*) AS n FROM web_workspace_history WHERE account=? GROUP BY kind", (account,))}
    return [{"kind": kind, "version": rows[kind]["version"] if kind in rows else 0, "size": rows[kind]["size"] if kind in rows else 0,
             "updated_at": rows[kind]["updated_at"] if kind in rows else None, "device": rows[kind]["device"] if kind in rows else None,
             "versions": counts.get(kind, 0), "limit": PANEL_KIND_LIMIT.get(kind, PANEL_KIND_DEFAULT_LIMIT)}
            for kind in PANEL_KINDS]


async def panel_me(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    store: Store = request.app["store"]
    row = _account_row(store, reader["account"])
    aliases = [dict(r) for r in store.q(
        "SELECT alias, backend, email, name, vip, paid, plan, last_seen FROM web_aliases WHERE account=? ORDER BY alias",
        (reader["account"],))]
    inbox = _inbox_rows(store, reader["account"])
    last = store.q("SELECT at, name, device, browser, os, country FROM web_events WHERE account=? AND kind='signin' "
                   "ORDER BY at DESC LIMIT 2", (reader["account"],))
    return web.json_response({
        "account": reader["account"], "aliases": aliases,
        "first_seen": row["first_seen"], "last_seen": row["last_seen"], "sign_ins": row["sign_ins"],
        "vip": bool(row["vip"]), "paid": bool(row["paid"]), "plan": row["plan"], "premium_until": row["premium_until"],
        "profile": _jsonish(row["profile"], {}), "prefs": _jsonish(row["prefs"], {}),
        "unread": sum(1 for m in inbox if not m["read"]),
        "open_tickets": store.one("SELECT COUNT(*) FROM web_tickets WHERE account=? AND status!='closed'", (reader["account"],)) or 0,
        "answered_tickets": store.one("SELECT COUNT(*) FROM web_tickets WHERE account=? AND unread_user>0", (reader["account"],)) or 0,
        "trades_30d": store.one("SELECT COUNT(*) FROM web_trades WHERE account=? AND at>=?", (reader["account"], now() - 30 * 86400)) or 0,
        "workspace": _workspace_meta(store, reader["account"]),
        "previous_sign_in": dict(last[1]) if len(last) > 1 else None,
        "session": {"sid": reader["sid"], "device": reader["device"], "browser": reader["browser"], "os": reader["os"],
                    "country": reader["country"]},
    })


AVATAR_PATTERN = re.compile(r"^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=]+$")


async def panel_profile(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    panel_limit(request, reader, "write")
    body = await panel_body(request, 96 * 1024)
    avatar = str(body.get("avatar") or "")
    if avatar and (len(avatar) > 64 * 1024 or not (AVATAR_PATTERN.match(avatar) or len(avatar) <= 8)):
        raise _panel_refuse(400, "avatar")
    profile = {"display_name": clean_text(body.get("display_name"), 40), "tagline": clean_text(body.get("tagline"), 80),
               "avatar": avatar}
    store: Store = request.app["store"]
    store.x("UPDATE web_accounts SET profile=? WHERE account=?", (json.dumps(profile, ensure_ascii=False), reader["account"]))
    panel_log(store, reader["account"], "feature", "profile.save", None, "panel", reader)
    return web.json_response({"profile": profile})


async def panel_prefs(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    panel_limit(request, reader, "write")
    body = await panel_body(request, 8 * 1024)
    store: Store = request.app["store"]
    prefs = _jsonish(store.one("SELECT prefs FROM web_accounts WHERE account=?", (reader["account"],)), {})
    for key, kind in PANEL_PREFS.items():
        if key not in body:
            continue
        value = body[key]
        if kind is bool:
            prefs[key] = bool(value)
        elif kind is list:
            prefs[key] = [k for k in (value if isinstance(value, list) else []) if k in PANEL_KINDS]
        else:
            prefs[key] = clean_text(value, 16)
    store.x("UPDATE web_accounts SET prefs=? WHERE account=?", (json.dumps(prefs), reader["account"]))
    return web.json_response({"prefs": prefs})


def _page(request: web.Request, default: int = 50, top: int = 200) -> tuple[int, int]:
    try:
        limit = max(1, min(top, int(request.query.get("limit", default))))
        before = int(request.query.get("before", 0))
    except ValueError:
        limit, before = default, 0
    return limit, before


def _event_row(row: sqlite3.Row, owner: bool = False) -> dict:
    out = {"id": row["id"], "at": row["at"], "kind": row["kind"], "name": row["name"] or "", "source": row["source"],
           "backend": row["backend"], "device": row["device"], "browser": row["browser"], "os": row["os"],
           "country": row["country"], "detail": _jsonish(row["detail"], {}) if row["detail"] else None}
    if owner:
        out.update({"sid": row["sid"], "ip_hash": (row["ip_hash"] or "")[:8], "account": row["account"]})
    return out


async def panel_activity(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    limit, before = _page(request)
    kind = clean_text(request.query.get("kind"), 20)
    store: Store = request.app["store"]
    rows = store.q("SELECT * FROM web_events WHERE account=? AND (?='' OR kind=?) AND (?=0 OR id<?) AND kind!='error' "
                   "ORDER BY id DESC LIMIT ?", (reader["account"], kind, kind, before, before, limit))
    return web.json_response({"items": [_event_row(r) for r in rows]})


async def panel_security(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    store: Store = request.app["store"]
    marks = ",".join("?" for _ in PANEL_SECURITY_KINDS)
    rows = store.q(f"SELECT * FROM web_events WHERE account=? AND kind IN ({marks}) ORDER BY id DESC LIMIT 100",
                   (reader["account"], *PANEL_SECURITY_KINDS))
    devices = [dict(r) for r in store.q(
        "SELECT browser, os, device, MAX(at) AS last_seen, COUNT(*) AS sign_ins, MAX(country) AS country FROM web_events "
        "WHERE account=? AND kind='signin' GROUP BY browser, os, device ORDER BY last_seen DESC LIMIT 20", (reader["account"],))]
    failed = store.one("SELECT COUNT(*) FROM web_events WHERE account=? AND kind='signin_failed' AND at>=?",
                       (reader["account"], now() - 30 * 86400)) or 0
    return web.json_response({"items": [_event_row(r) for r in rows], "devices": devices, "failed_30d": failed})


async def panel_client_events(request: web.Request) -> web.Response:
    """The page's own lines: its errors and which features it used. Small, batched, signed-in only."""
    reader = panel_reader(request)
    panel_limit(request, reader, "events")
    body = await panel_body(request, 64 * 1024)
    events = body.get("events")
    if not isinstance(events, list) or len(events) > 50:
        raise _panel_refuse(400, "events")
    store: Store = request.app["store"]
    stored = 0
    stamp = now()
    for event in events:
        if not isinstance(event, dict) or event.get("kind") not in PANEL_CLIENT_KINDS:
            continue
        name = clean_text(event.get("name"), 80)
        if not name:
            continue
        detail = event.get("detail") if isinstance(event.get("detail"), dict) else None
        if detail is not None:
            detail = {clean_text(k, 24): (clean_text(v, 1500) if isinstance(v, str) else v if isinstance(v, (int, float, bool)) else None)
                      for k, v in list(detail.items())[:12]}
            if len(json.dumps(detail)) > 4000:
                detail = {"truncated": True}
        at = int(_number(event.get("at")) or stamp)
        # A clock that is off by more than a day is the browser's, not the event's.
        at = at if abs(at - stamp) < 86400 else stamp
        panel_log(store, reader["account"], event["kind"], name, detail, "page", reader, None, at)
        stored += 1
    return web.json_response({"stored": stored})


def _trade_row(row: sqlite3.Row) -> dict:
    out = dict(row)
    out["fields"] = _jsonish(row["fields"], {})
    out["ok"] = bool(row["ok"])
    return out


def _trade_filters(request: web.Request, account: str | None) -> tuple[str, list]:
    clauses, args = [], []
    if account:
        clauses.append("account=?")
        args.append(account)
    for key in ("symbol", "side", "venue", "backend", "action"):
        value = clean_text(request.query.get(key), 24)
        if value:
            clauses.append(f"{key}=?")
            args.append(value.upper() if key == "symbol" else value.lower())
    status = request.query.get("status")
    if status in ("ok", "failed"):
        clauses.append("ok=?")
        args.append(1 if status == "ok" else 0)
    for key, op in (("from", ">="), ("to", "<=")):
        try:
            value = int(request.query.get(key) or 0)
        except ValueError:
            value = 0
        if value:
            clauses.append(f"at{op}?")
            args.append(value)
    if not account:
        q = clean_text(request.query.get("account"), 80)
        if q:
            clauses.append("account=?")
            args.append(q)
    return ("WHERE " + " AND ".join(clauses)) if clauses else "", args


async def panel_trades(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    where, args = _trade_filters(request, reader["account"])
    limit, before = _page(request, 100, 500)
    store: Store = request.app["store"]
    rows = store.q(f"SELECT * FROM web_trades {where} {'AND' if where else 'WHERE'} (?=0 OR id<?) ORDER BY id DESC LIMIT ?",
                   (*args, before, before, limit))
    return web.json_response({"items": [_trade_row(r) for r in rows],
                              "total": store.one(f"SELECT COUNT(*) FROM web_trades {where}", args) or 0})


def _kind(request: web.Request) -> str:
    kind = request.match_info["kind"]
    if kind not in PANEL_KINDS:
        raise _panel_refuse(404, "kind")
    return kind


async def panel_workspace_list(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    return web.json_response({"kinds": _workspace_meta(request.app["store"], reader["account"])})


async def panel_workspace_get(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    kind = _kind(request)
    rows = request.app["store"].q("SELECT * FROM web_workspace WHERE account=? AND kind=?", (reader["account"], kind))
    if not rows:
        return web.json_response({"kind": kind, "version": 0, "data": None})
    row = rows[0]
    return web.json_response({"kind": kind, "version": row["version"], "data": json.loads(row["data"]), "size": row["size"],
                              "updated_at": row["updated_at"], "device": row["device"]})


def save_workspace(app: web.Application, account: str, kind: str, data_text: str, device: str | None,
                   restored_from: int | None = None) -> int:
    """A new version of one part of the workspace; the history keeps the last few, older ones go."""
    store: Store = app["store"]
    current = store.one("SELECT version FROM web_workspace WHERE account=? AND kind=?", (account, kind)) or 0
    version = current + 1
    stamp = now()
    size = len(data_text.encode())
    store.x("INSERT INTO web_workspace(account, kind, version, data, size, updated_at, device) VALUES (?,?,?,?,?,?,?) "
            "ON CONFLICT(account, kind) DO UPDATE SET version=excluded.version, data=excluded.data, size=excluded.size, "
            "updated_at=excluded.updated_at, device=excluded.device", (account, kind, version, data_text, size, stamp, device))
    store.x("INSERT OR REPLACE INTO web_workspace_history(account, kind, version, data, size, saved_at, device, restored_from) "
            "VALUES (?,?,?,?,?,?,?,?)", (account, kind, version, data_text, size, stamp, device, restored_from))
    store.x("DELETE FROM web_workspace_history WHERE account=? AND kind=? AND version<=?",
            (account, kind, version - app["config"].panel_history))
    return version


async def panel_workspace_put(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    kind = _kind(request)
    panel_limit(request, reader, "write")
    limit = PANEL_KIND_LIMIT.get(kind, PANEL_KIND_DEFAULT_LIMIT)
    body = await panel_body(request, limit + 16 * 1024)
    data = body.get("data")
    if not isinstance(data, dict):
        raise _panel_refuse(400, "data")
    text = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    if len(text.encode()) > limit:
        raise _panel_refuse(413, "too_large", limit=limit)
    store: Store = request.app["store"]
    current = store.one("SELECT version FROM web_workspace WHERE account=? AND kind=?", (reader["account"], kind)) or 0
    try:
        base = int(body.get("base_version") or 0)
    except (TypeError, ValueError):
        raise _panel_refuse(400, "base_version")
    if base != current and not body.get("force"):
        raise _panel_refuse(409, "newer_version", version=current)
    device = clean_text(body.get("device"), 60) or f"{reader['browser']} · {reader['os']}"
    version = save_workspace(request.app, reader["account"], kind, text, device)
    panel_log(store, reader["account"], "feature", f"workspace.save.{kind}", {"version": version, "size": len(text.encode())},
              "panel", reader)
    return web.json_response({"kind": kind, "version": version, "size": len(text.encode())})


async def panel_workspace_versions(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    kind = _kind(request)
    rows = request.app["store"].q("SELECT version, size, saved_at, device, restored_from FROM web_workspace_history "
                                  "WHERE account=? AND kind=? ORDER BY version DESC", (reader["account"], kind))
    return web.json_response({"kind": kind, "versions": [dict(r) for r in rows]})


async def panel_workspace_version(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    kind = _kind(request)
    rows = request.app["store"].q("SELECT * FROM web_workspace_history WHERE account=? AND kind=? AND version=?",
                                  (reader["account"], kind, int(request.match_info["version"])))
    if not rows:
        raise _panel_refuse(404, "version")
    return web.json_response({"kind": kind, "version": rows[0]["version"], "data": json.loads(rows[0]["data"]),
                              "saved_at": rows[0]["saved_at"]})


def restore_workspace(app: web.Application, account: str, kind: str, version: int, device: str) -> int:
    rows = app["store"].q("SELECT data FROM web_workspace_history WHERE account=? AND kind=? AND version=?",
                          (account, kind, version))
    if not rows:
        raise _panel_refuse(404, "version")
    return save_workspace(app, account, kind, rows[0]["data"], device, restored_from=version)


async def panel_workspace_restore(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    kind = _kind(request)
    panel_limit(request, reader, "write")
    body = await panel_body(request, 1024)
    try:
        version = int(body.get("version"))
    except (TypeError, ValueError):
        raise _panel_refuse(400, "version")
    new = restore_workspace(request.app, reader["account"], kind, version, f"{reader['browser']} · {reader['os']}")
    panel_log(request.app["store"], reader["account"], "feature", f"workspace.restore.{kind}", {"from": version, "version": new},
              "panel", reader)
    return web.json_response({"kind": kind, "version": new})


async def panel_inbox(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    items = _inbox_rows(request.app["store"], reader["account"])
    return web.json_response({"items": items, "unread": sum(1 for m in items if not m["read"])})


def _own_message(store: Store, account: str, message_id: int) -> None:
    row = store.q("SELECT account FROM web_inbox WHERE id=?", (message_id,))
    if not row or (row[0]["account"] is not None and row[0]["account"] != account):
        raise _panel_refuse(404, "message")


async def panel_inbox_read(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    panel_limit(request, reader, "write")
    store: Store = request.app["store"]
    message_id = int(request.match_info["id"])
    _own_message(store, reader["account"], message_id)
    store.x("INSERT INTO web_inbox_state(message_id, account, read_at) VALUES (?,?,?) "
            "ON CONFLICT(message_id, account) DO UPDATE SET read_at=COALESCE(read_at, excluded.read_at)",
            (message_id, reader["account"], now()))
    return web.json_response({"ok": True})


async def panel_inbox_read_all(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    panel_limit(request, reader, "write")
    store: Store = request.app["store"]
    marked = 0
    for item in _inbox_rows(store, reader["account"], 500):
        if not item["read"]:
            store.x("INSERT INTO web_inbox_state(message_id, account, read_at) VALUES (?,?,?) "
                    "ON CONFLICT(message_id, account) DO UPDATE SET read_at=excluded.read_at", (item["id"], reader["account"], now()))
            marked += 1
    return web.json_response({"marked": marked})


async def panel_inbox_dismiss(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    panel_limit(request, reader, "write")
    store: Store = request.app["store"]
    message_id = int(request.match_info["id"])
    _own_message(store, reader["account"], message_id)
    store.x("INSERT INTO web_inbox_state(message_id, account, read_at, dismissed) VALUES (?,?,?,1) "
            "ON CONFLICT(message_id, account) DO UPDATE SET dismissed=1", (message_id, reader["account"], now()))
    return web.json_response({"ok": True})


def sniff_image(data: bytes) -> str | None:
    if data.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if data.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if data[:6] in (b"GIF87a", b"GIF89a"):
        return "image/gif"
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "image/webp"
    return None


def _attachments(raw) -> list[tuple[str, str, bytes]]:
    """Small images only, judged by their bytes: never SVG, never anything a browser would run."""
    if raw in (None, []):
        return []
    if not isinstance(raw, list) or len(raw) > PANEL_ATTACHMENTS_PER_MESSAGE:
        raise _panel_refuse(400, "attachments")
    out = []
    for item in raw:
        if not isinstance(item, dict):
            raise _panel_refuse(400, "attachments")
        text = str(item.get("data") or "")
        if "," in text and text.startswith("data:"):
            text = text.split(",", 1)[1]
        if len(text) > PANEL_ATTACHMENT_LIMIT * 4 // 3 + 8:
            raise _panel_refuse(413, "attachment_too_large", limit=PANEL_ATTACHMENT_LIMIT)
        try:
            data = base64.b64decode(text, validate=True)
        except (ValueError, TypeError):
            raise _panel_refuse(400, "attachments")
        if len(data) > PANEL_ATTACHMENT_LIMIT:
            raise _panel_refuse(413, "attachment_too_large", limit=PANEL_ATTACHMENT_LIMIT)
        kind = sniff_image(data)
        if kind is None:
            raise _panel_refuse(400, "attachment_type")
        out.append((clean_text(item.get("name"), 80) or "image", kind, data))
    return out


def _store_message(store: Store, ticket_id: int, account: str, author: str, operator: str | None, text: str,
                   files: list[tuple[str, str, bytes]]) -> int:
    stamp = now()
    message_id = store.x("INSERT INTO web_ticket_messages(ticket_id, at, author, operator, body) VALUES (?,?,?,?,?)",
                         (ticket_id, stamp, author, operator, text)).lastrowid
    for name, kind, data in files:
        store.x("INSERT INTO web_attachments(ticket_id, message_id, account, name, type, size, data, created_at) "
                "VALUES (?,?,?,?,?,?,?,?)", (ticket_id, message_id, account, name, kind, len(data), data, stamp))
    return message_id


def _ticket_row(row: sqlite3.Row) -> dict:
    return {"id": row["id"], "account": row["account"], "subject": row["subject"], "category": row["category"],
            "status": row["status"], "created_at": row["created_at"], "updated_at": row["updated_at"],
            "unread_user": bool(row["unread_user"]), "unread_owner": bool(row["unread_owner"])}


def _thread(store: Store, ticket_id: int, owner: bool = False) -> list[dict]:
    files: dict[int, list] = defaultdict(list)
    for a in store.q("SELECT id, message_id, name, type, size FROM web_attachments WHERE ticket_id=? ORDER BY id", (ticket_id,)):
        files[a["message_id"]].append({"id": a["id"], "name": a["name"], "type": a["type"], "size": a["size"]})
    return [{"id": m["id"], "at": m["at"], "author": m["author"], "body": m["body"], "attachments": files.get(m["id"], []),
             **({"operator": m["operator"]} if owner else {})}
            for m in store.q("SELECT * FROM web_ticket_messages WHERE ticket_id=? ORDER BY id", (ticket_id,))]


async def panel_tickets(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    rows = request.app["store"].q("SELECT * FROM web_tickets WHERE account=? ORDER BY updated_at DESC LIMIT 100",
                                  (reader["account"],))
    return web.json_response({"items": [_ticket_row(r) for r in rows]})


async def panel_ticket_new(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    body = await panel_body(request)
    subject, text = clean_text(body.get("subject"), 120), clean_text(body.get("body"), 4000)
    category = body.get("category") if body.get("category") in PANEL_TICKET_CATEGORIES else "other"
    if len(subject) < 3 or len(text) < 5:
        raise _panel_refuse(400, "subject_and_body")
    files = _attachments(body.get("attachments"))
    store: Store = request.app["store"]
    if (store.one("SELECT COUNT(*) FROM web_tickets WHERE account=? AND status!='closed'", (reader["account"],)) or 0) >= 10:
        raise _panel_refuse(429, "too_many_open")
    panel_limit(request, reader, "ticket")
    stamp = now()
    ticket_id = store.x("INSERT INTO web_tickets(account, subject, category, status, created_at, updated_at, unread_owner) "
                        "VALUES (?,?,?,?,?,?,1)", (reader["account"], subject, category, "open", stamp, stamp)).lastrowid
    _store_message(store, ticket_id, reader["account"], "user", None, text, files)
    panel_log(store, reader["account"], "feature", "ticket.open", {"ticket": ticket_id, "category": category}, "panel", reader)
    return web.json_response({"id": ticket_id}, status=201)


def _own_ticket(store: Store, account: str, ticket_id: int) -> sqlite3.Row:
    rows = store.q("SELECT * FROM web_tickets WHERE id=? AND account=?", (ticket_id, account))
    if not rows:
        raise _panel_refuse(404, "ticket")
    return rows[0]


async def panel_ticket(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    store: Store = request.app["store"]
    ticket = _own_ticket(store, reader["account"], int(request.match_info["id"]))
    store.x("UPDATE web_tickets SET unread_user=0 WHERE id=?", (ticket["id"],))
    return web.json_response({"ticket": _ticket_row(ticket), "messages": _thread(store, ticket["id"])})


async def panel_ticket_reply(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    body = await panel_body(request)
    store: Store = request.app["store"]
    ticket = _own_ticket(store, reader["account"], int(request.match_info["id"]))
    text = clean_text(body.get("body"), 4000)
    if len(text) < 1:
        raise _panel_refuse(400, "body")
    files = _attachments(body.get("attachments"))
    panel_limit(request, reader, "reply")
    _store_message(store, ticket["id"], reader["account"], "user", None, text, files)
    store.x("UPDATE web_tickets SET status='open', updated_at=?, unread_owner=1 WHERE id=?", (now(), ticket["id"]))
    return web.json_response({"ok": True}, status=201)


async def panel_ticket_close(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    panel_limit(request, reader, "write")
    store: Store = request.app["store"]
    ticket = _own_ticket(store, reader["account"], int(request.match_info["id"]))
    store.x("UPDATE web_tickets SET status='closed', updated_at=? WHERE id=?", (now(), ticket["id"]))
    return web.json_response({"ok": True})


def _image(row: sqlite3.Row) -> web.Response:
    return web.Response(body=row["data"], content_type=row["type"], headers={
        "Cache-Control": "private, max-age=3600", "X-Content-Type-Options": "nosniff",
        "Content-Disposition": "inline", "Content-Security-Policy": "default-src 'none'; sandbox"})


async def panel_attachment(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    store: Store = request.app["store"]
    ticket = _own_ticket(store, reader["account"], int(request.match_info["id"]))
    rows = store.q("SELECT * FROM web_attachments WHERE id=? AND ticket_id=?", (int(request.match_info["aid"]), ticket["id"]))
    if not rows:
        raise _panel_refuse(404, "attachment")
    return _image(rows[0])


def account_export(store: Store, account: str) -> dict:
    """Everything this service holds for one web account, in one document."""
    row = _account_row(store, account)
    tickets = []
    for t in store.q("SELECT * FROM web_tickets WHERE account=? ORDER BY id", (account,)):
        thread = _thread(store, t["id"])
        for message in thread:
            for a in message["attachments"]:
                data = store.one("SELECT data FROM web_attachments WHERE id=?", (a["id"],))
                a["data"] = base64.b64encode(data).decode() if data else ""
        tickets.append({**_ticket_row(t), "messages": thread})
    return {
        "exported_at": now(), "account": account,
        "profile": {k: row[k] for k in ("backend", "backend_id", "email", "name", "first_seen", "last_seen", "sign_ins",
                                        "vip", "paid", "plan", "premium_until", "last_device", "last_browser", "last_os",
                                        "last_country")} if row else None,
        "panel_profile": _jsonish(row["profile"], {}) if row else {}, "prefs": _jsonish(row["prefs"], {}) if row else {},
        "aliases": [dict(r) for r in store.q("SELECT alias, backend, email, name, vip, paid, plan, last_seen FROM web_aliases "
                                             "WHERE account=?", (account,))],
        "workspace": [{"kind": r["kind"], "version": r["version"], "updated_at": r["updated_at"], "data": json.loads(r["data"])}
                      for r in store.q("SELECT * FROM web_workspace WHERE account=?", (account,))],
        "workspace_history": [{k: r[k] for k in ("kind", "version", "size", "saved_at", "device", "restored_from")}
                              for r in store.q("SELECT * FROM web_workspace_history WHERE account=? ORDER BY kind, version", (account,))],
        "inbox": _inbox_rows(store, account, 1000),
        "tickets": tickets,
        "activity": [_event_row(r) for r in store.q("SELECT * FROM web_events WHERE account=? ORDER BY id", (account,))],
        "trades": [_trade_row(r) for r in store.q("SELECT * FROM web_trades WHERE account=? ORDER BY id", (account,))],
    }


async def panel_export(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    panel_limit(request, reader, "export")
    store: Store = request.app["store"]
    data = account_export(store, reader["account"])
    panel_log(store, reader["account"], "feature", "data.export", None, "panel", reader)
    return web.Response(body=json.dumps(data, ensure_ascii=False, indent=1).encode(), content_type="application/json",
                        charset="utf-8", headers={"Content-Disposition": 'attachment; filename="pro-chart-panel-data.json"'})


def erase_account(store: Store, account: str) -> dict:
    """Everything of the account goes, except the trading audit, which stays without a name on it."""
    counts = {}
    for table in ("web_workspace", "web_workspace_history", "web_events", "web_attachments", "web_inbox_state"):
        counts[table] = store.x(f"DELETE FROM {table} WHERE account=?", (account,)).rowcount
    tickets = [r["id"] for r in store.q("SELECT id FROM web_tickets WHERE account=?", (account,))]
    for ticket_id in tickets:
        store.x("DELETE FROM web_ticket_messages WHERE ticket_id=?", (ticket_id,))
    counts["web_tickets"] = store.x("DELETE FROM web_tickets WHERE account=?", (account,)).rowcount
    counts["web_inbox"] = store.x("DELETE FROM web_inbox WHERE account=?", (account,)).rowcount
    counts["web_trades_anonymised"] = store.x(
        "UPDATE web_trades SET account=NULL, sid=NULL, device=NULL WHERE account=?", (account,)).rowcount
    counts["web_aliases"] = store.x("DELETE FROM web_aliases WHERE account=?", (account,)).rowcount
    counts["web_accounts"] = store.x("DELETE FROM web_accounts WHERE account=?", (account,)).rowcount
    return counts


async def panel_erase(request: web.Request) -> web.Response:
    reader = panel_reader(request)
    panel_limit(request, reader, "write")
    body = await panel_body(request, 1024)
    if body.get("confirm") is not True:
        raise _panel_refuse(400, "confirm")
    store: Store = request.app["store"]
    counts = erase_account(store, reader["account"])
    store.audit("reader", None, "web.erase", {"account": reader["account"], "rows": sum(counts.values())})
    return web.json_response({"erased": counts})


def prune_panel(store: Store) -> int:
    """Old lines out: page views and feature use after 90 days, errors after 60, the rest after a year,
    the trading audit after two."""
    stamp, removed = now(), 0
    for kind, days in PANEL_RETENTION.items():
        removed += store.x("DELETE FROM web_events WHERE kind=? AND at<?", (kind, stamp - days * 86400)).rowcount
    marks = ",".join("?" for _ in PANEL_RETENTION)
    removed += store.x(f"DELETE FROM web_events WHERE kind NOT IN ({marks}) AND at<?",
                       (*PANEL_RETENTION, stamp - PANEL_DEFAULT_RETENTION * 86400)).rowcount
    removed += store.x("DELETE FROM web_trades WHERE at<?", (stamp - PANEL_TRADE_RETENTION * 86400,)).rowcount
    return removed


# ── «کاربران وب»: the owner's view ──────────────────────────────────────────────────────────────


async def relay_call(app: web.Application, method: str, path: str, *, params: dict | None = None,
                     body: dict | None = None) -> tuple[int, dict]:
    config: Config = app["config"]
    if not config.panel_enabled:
        return 503, {"error": "panel_off"}
    try:
        async with app["state"]["http"].request(method, config.relay_url + path, params=params, json=body,
                                                 headers={"X-Panel-Token": config.panel_token},
                                                 timeout=aiohttp.ClientTimeout(total=8), allow_redirects=False) as response:
            try:
                data = await response.json(content_type=None)
            except ValueError:
                data = {}
            return response.status, data if isinstance(data, dict) else {}
    except (aiohttp.ClientError, asyncio.TimeoutError, OSError) as error:
        return 502, {"error": "relay_unreachable", "detail": type(error).__name__}


def _web_account_param(request: web.Request) -> str:
    account = request.match_info["account"]
    if _account_row(request.app["store"], account) is None:
        raise web.HTTPNotFound(text=json.dumps({"error": "web_account"}), content_type="application/json")
    return account


def _split(items, key: str, limit: int = 10) -> list[dict]:
    counts: dict[str, int] = defaultdict(int)
    for item in items:
        counts[item.get(key) or "?"] += 1
    return [{"label": k, "value": v} for k, v in sorted(counts.items(), key=lambda kv: -kv[1])[:limit]]


async def web_overview(request: web.Request) -> web.Response:
    require(request)
    store: Store = request.app["store"]
    status, live = await relay_call(request.app, "GET", "/relay/internal/sessions")
    sessions = live.get("sessions", []) if status == 200 else []
    stamp = now()
    online = [s for s in sessions if stamp - int(s.get("last_seen") or 0) <= 300]
    days = _days(request.app, 30)

    def by_day(sql: str) -> list[int]:
        found: dict[str, int] = defaultdict(int)
        for row in store.q(sql, (stamp - 31 * 86400,)):
            found[day_of(request.app, row[0])] += 1
        return [found.get(day, 0) for day in days]

    signins = by_day("SELECT at FROM web_events WHERE kind='signin' AND at>=?")
    trades = by_day("SELECT at FROM web_trades WHERE at>=?")
    errors = by_day("SELECT at FROM web_events WHERE kind='error' AND at>=?")
    recent = [dict(r) for r in store.q("SELECT device, browser, os, country FROM web_events WHERE kind='signin' AND at>=?",
                                       (stamp - 30 * 86400,))]
    source = sessions or recent
    return web.json_response({
        "relay": {"ok": status == 200, "status": status, "error": live.get("error") if status != 200 else None},
        "online_now": len({tuple(s.get("accounts") or [s.get("sid")]) for s in online}),
        "online_sessions": len(online), "live_sessions": len(sessions), "guests": live.get("guests", 0),
        "online": [{**s, "accounts": s.get("accounts"), **_who(store, s.get("accounts"))}
                   for s in sorted(online, key=lambda s: -int(s.get("last_seen") or 0))[:50]],
        "kpis": {
            "accounts": store.one("SELECT COUNT(*) FROM web_accounts") or 0,
            "active_1d": store.one("SELECT COUNT(*) FROM web_accounts WHERE last_seen>=?", (stamp - 86400,)) or 0,
            "active_7d": store.one("SELECT COUNT(*) FROM web_accounts WHERE last_seen>=?", (stamp - 7 * 86400,)) or 0,
            "new_7d": store.one("SELECT COUNT(*) FROM web_accounts WHERE first_seen>=?", (stamp - 7 * 86400,)) or 0,
            "signins_today": signins[-1], "signins_7d": sum(signins[-7:]),
            "failed_24h": store.one("SELECT COUNT(*) FROM web_events WHERE kind='signin_failed' AND at>=?", (stamp - 86400,)) or 0,
            "trades_24h": store.one("SELECT COUNT(*) FROM web_trades WHERE at>=?", (stamp - 86400,)) or 0,
            "trades_failed_24h": store.one("SELECT COUNT(*) FROM web_trades WHERE at>=? AND ok=0", (stamp - 86400,)) or 0,
            "errors_24h": store.one("SELECT COUNT(*) FROM web_events WHERE kind='error' AND at>=?", (stamp - 86400,)) or 0,
            "open_tickets": store.one("SELECT COUNT(*) FROM web_tickets WHERE status!='closed'") or 0,
            "waiting_tickets": store.one("SELECT COUNT(*) FROM web_tickets WHERE unread_owner=1 AND status!='closed'") or 0,
            "vip": store.one("SELECT COUNT(*) FROM web_accounts WHERE vip=1") or 0,
            "synced": store.one("SELECT COUNT(DISTINCT account) FROM web_workspace") or 0,
        },
        "days": days, "series": {"signins": signins, "trades": trades, "errors": errors},
        "devices": _split(source, "device"), "browsers": _split(source, "browser"), "systems": _split(source, "os"),
        "countries": _split(source, "country"),
        "features": [dict(r) for r in store.q(
            "SELECT name AS label, COUNT(*) AS value FROM web_events WHERE kind IN ('feature', 'page') AND at>=? "
            "GROUP BY name ORDER BY value DESC LIMIT 12", (stamp - 7 * 86400,))],
    })


def _who(store: Store, aliases) -> dict:
    """The console's own account for a live session's aliases: its id, e-mail and name."""
    for alias in aliases or []:
        rows = store.q("SELECT a.account, a.email, a.name FROM web_aliases x JOIN web_accounts a ON a.account = x.account "
                       "WHERE x.alias=?", (clean_text(alias, 80),))
        if rows:
            return {"account": rows[0]["account"], "email": rows[0]["email"], "name": rows[0]["name"]}
    return {"account": None, "email": None, "name": None}


WEB_ACCOUNT_SORTS = {
    "seen": "a.last_seen DESC", "new": "a.first_seen DESC", "signins": "a.sign_ins DESC",
    "trades": "trades_30d DESC, a.last_seen DESC", "email": "a.email ASC",
}


def _web_accounts_query(request: web.Request, online: set[str]) -> tuple[str, list]:
    clauses, args = [], []
    q = clean_text(request.query.get("q"), 80).lower()
    if q:
        clauses.append("(a.account LIKE ? OR LOWER(a.email) LIKE ? OR LOWER(a.name) LIKE ? OR a.account IN "
                       "(SELECT account FROM web_aliases WHERE alias LIKE ? OR LOWER(email) LIKE ?))")
        args += [f"%{q}%"] * 5
    backend = request.query.get("backend")
    if backend in PANEL_BACKENDS:
        clauses.append("a.account IN (SELECT account FROM web_aliases WHERE backend=?)")
        args.append(backend)
    show = request.query.get("filter")
    stamp = now()
    if show == "online":
        marks = ",".join("?" for _ in online) or "''"
        clauses.append(f"a.account IN (SELECT account FROM web_aliases WHERE alias IN ({marks}))")
        args += list(online)
    elif show == "vip":
        clauses.append("a.vip=1")
    elif show == "premium":
        clauses.append("(a.paid=1 OR (a.premium_until IS NOT NULL AND a.premium_until>?))")
        args.append(datetime.now(timezone.utc).isoformat())
    elif show == "traders":
        clauses.append("a.account IN (SELECT account FROM web_trades WHERE at>=?)")
        args.append(stamp - 30 * 86400)
    elif show == "tickets":
        clauses.append("a.account IN (SELECT account FROM web_tickets WHERE status!='closed')")
    elif show == "errors":
        clauses.append("a.account IN (SELECT account FROM web_events WHERE kind='error' AND at>=?)")
        args.append(stamp - 7 * 86400)
    elif show == "active7":
        clauses.append("a.last_seen>=?")
        args.append(stamp - 7 * 86400)
    return ("WHERE " + " AND ".join(clauses)) if clauses else "", args


def _web_account_rows(store: Store, where: str, args: list, order: str, limit: int, offset: int, online: set[str]) -> list[dict]:
    stamp = now()
    rows = store.q(
        "SELECT a.*, (SELECT COUNT(*) FROM web_trades t WHERE t.account=a.account AND t.at>=?) AS trades_30d, "
        "(SELECT COUNT(*) FROM web_trades t WHERE t.account=a.account) AS trades_total, "
        "(SELECT COUNT(*) FROM web_tickets k WHERE k.account=a.account AND k.status!='closed') AS open_tickets, "
        "(SELECT COUNT(*) FROM web_events e WHERE e.account=a.account AND e.kind='error' AND e.at>=?) AS errors_7d "
        f"FROM web_accounts a {where} ORDER BY {order} LIMIT ? OFFSET ?",
        (stamp - 30 * 86400, stamp - 7 * 86400, *args, limit, offset))
    out = []
    for row in rows:
        aliases = [r["alias"] for r in store.q("SELECT alias FROM web_aliases WHERE account=? ORDER BY alias", (row["account"],))]
        item = {k: row[k] for k in ("account", "backend", "backend_id", "email", "name", "first_seen", "last_seen", "sign_ins",
                                    "plan", "premium_until", "last_device", "last_browser", "last_os", "last_country",
                                    "trades_30d", "trades_total", "open_tickets", "errors_7d", "bridge_checked_at")}
        item.update({"vip": bool(row["vip"]), "paid": bool(row["paid"]), "aliases": aliases,
                     "membership": _jsonish(row["membership"], {}) if row["membership"] else None,
                     "online": any(a in online for a in aliases)})
        out.append(item)
    return out


async def _online_aliases(app: web.Application) -> tuple[set[str], list[dict], int]:
    status, live = await relay_call(app, "GET", "/relay/internal/sessions")
    sessions = live.get("sessions", []) if status == 200 else []
    stamp = now()
    online = {a for s in sessions if stamp - int(s.get("last_seen") or 0) <= 300 for a in s.get("accounts") or []}
    return online, sessions, status


async def web_accounts(request: web.Request) -> web.Response:
    require(request)
    store: Store = request.app["store"]
    online, _, status = await _online_aliases(request.app)
    where, args = _web_accounts_query(request, online)
    order = WEB_ACCOUNT_SORTS.get(request.query.get("sort") or "seen", WEB_ACCOUNT_SORTS["seen"])
    try:
        page = max(1, int(request.query.get("page", 1)))
        size = max(1, min(200, int(request.query.get("size", 50))))
    except ValueError:
        page, size = 1, 50
    total = store.one(f"SELECT COUNT(*) FROM web_accounts a {where}", args) or 0
    return web.json_response({"total": total, "page": page, "size": size, "relay_ok": status == 200,
                              "accounts": _web_account_rows(store, where, args, order, size, (page - 1) * size, online)})


WEB_ACCOUNT_CSV = ["account", "aliases", "email", "name", "first_seen", "last_seen", "sign_ins", "vip", "paid", "plan",
                   "premium_until", "trades_30d", "trades_total", "open_tickets", "errors_7d", "last_device", "last_browser",
                   "last_os", "last_country", "online"]


async def web_accounts_csv(request: web.Request) -> web.Response:
    session = require(request)
    store: Store = request.app["store"]
    online, _, _ = await _online_aliases(request.app)
    where, args = _web_accounts_query(request, online)
    rows = _web_account_rows(store, where, args, WEB_ACCOUNT_SORTS["seen"], 20000, 0, online)
    store.audit(session["username"], client_ip(request), "web.export_accounts", {"rows": len(rows)})
    out = io.StringIO()
    writer = csv.writer(out)
    writer.writerow(WEB_ACCOUNT_CSV)
    for row in rows:
        writer.writerow([" ".join(row[c]) if c == "aliases" else ("" if row.get(c) is None else row.get(c)) for c in WEB_ACCOUNT_CSV])
    return web.Response(body=("﻿" + out.getvalue()).encode(), content_type="text/csv", charset="utf-8",
                        headers={"Content-Disposition": 'attachment; filename="pro-chart-web-accounts.csv"'})


async def bridge_state(request: web.Request, account: str) -> list[dict]:
    """Each alias's account on its own server, through the existing admin bridges: VIP, Premium,
    ban. Matched by email, since a bridge's own id is not always the id `me` gives the reader."""
    store: Store = request.app["store"]
    out = []
    for alias in store.q("SELECT alias, backend, email FROM web_aliases WHERE account=?", (account,)):
        bridge = request.app["config"].bridges.get(alias["backend"])
        entry = {"alias": alias["alias"], "backend": alias["backend"], "configured": bool(bridge and bridge.get("token"))}
        if not entry["configured"] or not alias["email"]:
            out.append(entry)
            continue
        status, listing = await bridge_call(request, bridge, "GET", "/users",
                                            params={"q": alias["email"], "scope": "all", "size": "5"})
        match = next((u for u in (listing.get("users") or []) if str(u.get("email") or "").lower() == alias["email"]), None) \
            if status == 200 else None
        if match is None:
            entry["error"] = "not_found" if status == 200 else listing.get("error") or f"http_{status}"
            out.append(entry)
            continue
        status, detail = await bridge_call(request, bridge, "GET", f"/users/{int(match['id'])}")
        if status != 200:
            entry["error"] = detail.get("error") or f"http_{status}"
            out.append(entry)
            continue
        subscriptions = detail.get("subscriptions") or []
        live = [s for s in subscriptions if s.get("live")]
        entry.update({
            "bridge_id": int(match["id"]), "banned": bool(detail.get("banned")), "membership": detail.get("membership"),
            "plan": detail.get("plan"), "kyc": detail.get("kyc"),
            "premium_until": max((s.get("ends_at") or "" for s in live), default=None),
            "subscriptions": subscriptions[:10], "sessions_live": sum(1 for s in detail.get("sessions") or [] if s.get("live")),
            "email_verified": detail.get("email_verified"), "created_at": detail.get("created_at"),
        })
        out.append(entry)
    vip = any((e.get("membership") or {}).get("vip") for e in out)
    paid = any((e.get("membership") or {}).get("paid") for e in out)
    premium = max((e.get("premium_until") or "" for e in out), default="") or None
    bridge_id = next((e.get("bridge_id") for e in out if e["alias"] == account and e.get("bridge_id")), None)
    if any("bridge_id" in e for e in out):
        store.x("UPDATE web_accounts SET vip=?, paid=?, premium_until=?, membership=?, bridge_id=COALESCE(?, bridge_id), "
                "bridge_checked_at=? WHERE account=?",
                (int(vip), int(paid), premium, json.dumps([{k: e.get(k) for k in ("alias", "membership", "premium_until", "banned")}
                                                           for e in out], ensure_ascii=False), bridge_id, now(), account))
    return out


async def web_account(request: web.Request) -> web.Response:
    require(request)
    account = _web_account_param(request)
    store: Store = request.app["store"]
    row = _account_row(store, account)
    aliases = [dict(r) for r in store.q("SELECT alias, backend, email, name, vip, paid, plan, last_seen FROM web_aliases "
                                        "WHERE account=? ORDER BY alias", (account,))]
    status, live = await relay_call(request.app, "GET", "/relay/internal/sessions")
    names = {a["alias"] for a in aliases}
    sessions = [s for s in live.get("sessions", []) if names & set(s.get("accounts") or [])] if status == 200 else []
    bridges = []
    if not row["bridge_checked_at"] or now() - row["bridge_checked_at"] > 3600:
        bridges = await bridge_state(request, account)
        row = _account_row(store, account)
    stamp = now()
    return web.json_response({
        "account": {k: row[k] for k in row.keys() if k not in ("profile", "prefs", "membership", "email_key")},
        "membership": _jsonish(row["membership"], []) if row["membership"] else None,
        "profile": _jsonish(row["profile"], {}), "prefs": _jsonish(row["prefs"], {}),
        "aliases": aliases, "sessions": sessions, "relay_ok": status == 200, "bridges": bridges,
        "workspace": _workspace_meta(store, account),
        "workspace_versions": [dict(r) for r in store.q(
            "SELECT kind, version, size, saved_at, device, restored_from FROM web_workspace_history WHERE account=? "
            "ORDER BY kind, version DESC", (account,))],
        "tickets": [_ticket_row(r) for r in store.q("SELECT * FROM web_tickets WHERE account=? ORDER BY updated_at DESC", (account,))],
        "counts": {
            "trades_30d": store.one("SELECT COUNT(*) FROM web_trades WHERE account=? AND at>=?", (account, stamp - 30 * 86400)) or 0,
            "trades_failed_30d": store.one("SELECT COUNT(*) FROM web_trades WHERE account=? AND at>=? AND ok=0",
                                           (account, stamp - 30 * 86400)) or 0,
            "errors_7d": store.one("SELECT COUNT(*) FROM web_events WHERE account=? AND kind='error' AND at>=?",
                                   (account, stamp - 7 * 86400)) or 0,
            "failed_signins_30d": store.one("SELECT COUNT(*) FROM web_events WHERE account=? AND kind='signin_failed' AND at>=?",
                                            (account, stamp - 30 * 86400)) or 0,
            "unread_inbox": sum(1 for m in _inbox_rows(store, account) if not m["read"]),
        },
        "features": [dict(r) for r in store.q(
            "SELECT name AS label, COUNT(*) AS value FROM web_events WHERE account=? AND kind IN ('feature', 'page') AND at>=? "
            "GROUP BY name ORDER BY value DESC LIMIT 10", (account, stamp - 30 * 86400))],
    })


async def web_timeline(request: web.Request) -> web.Response:
    require(request)
    account = _web_account_param(request)
    limit, before = _page(request, 100, 500)
    kind = clean_text(request.query.get("kind"), 20)
    rows = request.app["store"].q(
        "SELECT * FROM web_events WHERE account=? AND (?='' OR kind=?) AND (?=0 OR id<?) ORDER BY id DESC LIMIT ?",
        (account, kind, kind, before, before, limit))
    return web.json_response({"items": [_event_row(r, owner=True) for r in rows]})


async def web_refresh(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    account = _web_account_param(request)
    bridges = await bridge_state(request, account)
    request.app["store"].audit(session["username"], client_ip(request), "web.refresh", {"account": account})
    return web.json_response({"bridges": bridges})


async def web_revoke(request: web.Request) -> web.Response:
    """One live web session of the account ({sid}), or all of them ({})."""
    session = require(request, mutating=True)
    account = _web_account_param(request)
    body = await read_json(request)
    sid = clean_text(body.get("sid"), 32)
    store: Store = request.app["store"]
    revoked = 0
    for alias in [r["alias"] for r in store.q("SELECT alias FROM web_aliases WHERE account=?", (account,))] or [account]:
        status, data = await relay_call(request.app, "POST", "/relay/internal/sessions/revoke",
                                        body={"account": alias, "sid": sid, "operator": session["username"]})
        if status != 200:
            return fail(502, data.get("error") or "relay_unreachable")
        revoked += int(data.get("revoked") or 0)
    store.audit(session["username"], client_ip(request), "web.revoke", {"account": account, "sid": sid or "all", "revoked": revoked})
    return web.json_response({"revoked": revoked})


async def web_note(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    account = _web_account_param(request)
    body = await read_json(request)
    note = clean_text(body.get("note"), 2000)
    request.app["store"].x("UPDATE web_accounts SET note=? WHERE account=?", (note, account))
    request.app["store"].audit(session["username"], client_ip(request), "web.note", {"account": account})
    return web.json_response({"ok": True})


async def web_workspace_version(request: web.Request) -> web.Response:
    session = require(request)
    account = _web_account_param(request)
    kind = request.match_info["kind"]
    store: Store = request.app["store"]
    rows = store.q("SELECT * FROM web_workspace_history WHERE account=? AND kind=? AND version=?",
                   (account, kind, int(request.match_info["version"])))
    if not rows:
        return fail(404, "version")
    store.audit(session["username"], client_ip(request), "web.workspace_view",
                {"account": account, "kind": kind, "version": rows[0]["version"]})
    return web.Response(body=rows[0]["data"].encode(), content_type="application/json", charset="utf-8",
                        headers={"Content-Disposition": f'attachment; filename="{kind}-v{rows[0]["version"]}.json"'})


async def web_workspace_restore(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    account = _web_account_param(request)
    kind = request.match_info["kind"]
    if kind not in PANEL_KINDS:
        return fail(404, "kind")
    body = await read_json(request)
    try:
        version = int(body.get("version"))
    except (TypeError, ValueError):
        return fail(400, "version")
    new = restore_workspace(request.app, account, kind, version, "owner console")
    store: Store = request.app["store"]
    panel_log(store, account, "feature", f"workspace.restore.{kind}", {"from": version, "version": new, "by": "owner"}, "owner")
    store.audit(session["username"], client_ip(request), "web.workspace_restore",
                {"account": account, "kind": kind, "from": version, "version": new})
    return web.json_response({"version": new})


WEB_TRADE_CSV = ["id", "at", "account", "backend", "action", "method", "path", "symbol", "side", "quantity", "type", "price",
                 "venue", "status", "ok", "order_id", "state", "detail", "device"]


async def web_trades(request: web.Request) -> web.Response:
    require(request)
    store: Store = request.app["store"]
    where, args = _trade_filters(request, None)
    try:
        page = max(1, int(request.query.get("page", 1)))
        size = max(1, min(500, int(request.query.get("size", 100))))
    except ValueError:
        page, size = 1, 100
    rows = store.q(f"SELECT * FROM web_trades {where} ORDER BY id DESC LIMIT ? OFFSET ?", (*args, size, (page - 1) * size))
    totals = store.q(f"SELECT COUNT(*) AS n, SUM(ok) AS ok, COUNT(DISTINCT account) AS accounts FROM web_trades {where}", args)[0]
    return web.json_response({"items": [_trade_row(r) for r in rows], "total": totals["n"] or 0, "ok": totals["ok"] or 0,
                              "accounts": totals["accounts"] or 0, "page": page, "size": size,
                              "symbols": [dict(r) for r in store.q(
                                  f"SELECT symbol AS label, COUNT(*) AS value FROM web_trades {where} "
                                  f"{'AND' if where else 'WHERE'} symbol IS NOT NULL GROUP BY symbol ORDER BY value DESC LIMIT 10", args)]})


async def web_trades_csv(request: web.Request) -> web.Response:
    session = require(request)
    store: Store = request.app["store"]
    where, args = _trade_filters(request, None)
    rows = store.q(f"SELECT {', '.join(WEB_TRADE_CSV)} FROM web_trades {where} ORDER BY id DESC LIMIT 100000", args)
    store.audit(session["username"], client_ip(request), "web.export_trades", {"rows": len(rows), "filters": dict(request.query)})
    return _csv(rows, WEB_TRADE_CSV, "pro-chart-web-trades.csv")


async def web_tickets(request: web.Request) -> web.Response:
    require(request)
    store: Store = request.app["store"]
    status = request.query.get("status") or ""
    q = clean_text(request.query.get("q"), 80).lower()
    clauses, args = [], []
    if status in PANEL_TICKET_STATES:
        clauses.append("t.status=?")
        args.append(status)
    elif status == "waiting":
        clauses.append("t.unread_owner=1 AND t.status!='closed'")
    if q:
        clauses.append("(LOWER(t.subject) LIKE ? OR t.account LIKE ? OR LOWER(a.email) LIKE ?)")
        args += [f"%{q}%"] * 3
    where = ("WHERE " + " AND ".join(clauses)) if clauses else ""
    rows = store.q(f"SELECT t.*, a.email, a.name FROM web_tickets t LEFT JOIN web_accounts a ON a.account=t.account {where} "
                   "ORDER BY t.unread_owner DESC, t.updated_at DESC LIMIT 300", args)
    return web.json_response({"items": [{**_ticket_row(r), "email": r["email"], "name": r["name"],
                                         "messages": store.one("SELECT COUNT(*) FROM web_ticket_messages WHERE ticket_id=?", (r["id"],))}
                                        for r in rows],
                              "counts": {s: store.one("SELECT COUNT(*) FROM web_tickets WHERE status=?", (s,)) or 0
                                         for s in PANEL_TICKET_STATES}})


def _ticket_or_404(store: Store, ticket_id: int) -> sqlite3.Row:
    rows = store.q("SELECT * FROM web_tickets WHERE id=?", (ticket_id,))
    if not rows:
        raise web.HTTPNotFound(text=json.dumps({"error": "ticket"}), content_type="application/json")
    return rows[0]


async def web_ticket(request: web.Request) -> web.Response:
    require(request)
    store: Store = request.app["store"]
    ticket = _ticket_or_404(store, int(request.match_info["id"]))
    store.x("UPDATE web_tickets SET unread_owner=0 WHERE id=?", (ticket["id"],))
    account = _account_row(store, ticket["account"])
    return web.json_response({"ticket": _ticket_row(ticket), "messages": _thread(store, ticket["id"], owner=True),
                              "account": {"account": ticket["account"], "email": account["email"] if account else None,
                                          "name": account["name"] if account else None}})


def _inbox_message(store: Store, account: str | None, kind: str, title: str, body: str, link: str, operator: str,
                   ticket_id: int | None = None) -> int:
    return store.x("INSERT INTO web_inbox(account, created_at, created_by, kind, title, body, link, ticket_id) VALUES (?,?,?,?,?,?,?,?)",
                   (account, now(), operator, kind, title, body, link or None, ticket_id)).lastrowid


async def web_ticket_reply(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    store: Store = request.app["store"]
    ticket = _ticket_or_404(store, int(request.match_info["id"]))
    body = await read_json(request)
    text = clean_text(body.get("body"), 4000)
    if not text:
        return fail(400, "body")
    _store_message(store, ticket["id"], ticket["account"], "owner", session["username"], text, [])
    status = "closed" if body.get("close") else "answered"
    store.x("UPDATE web_tickets SET status=?, updated_at=?, unread_user=1, unread_owner=0 WHERE id=?", (status, now(), ticket["id"]))
    _inbox_message(store, ticket["account"], "ticket", f"پاسخ پشتیبانی: {ticket['subject']}"[:120], text[:400], "",
                   session["username"], ticket["id"])
    store.audit(session["username"], client_ip(request), "web.ticket_reply", {"ticket": ticket["id"], "status": status})
    return web.json_response({"ok": True, "status": status})


async def web_ticket_status(request: web.Request) -> web.Response:
    session = require(request, mutating=True)
    store: Store = request.app["store"]
    ticket = _ticket_or_404(store, int(request.match_info["id"]))
    body = await read_json(request)
    status = body.get("status")
    if status not in PANEL_TICKET_STATES:
        return fail(400, "status")
    store.x("UPDATE web_tickets SET status=?, updated_at=? WHERE id=?", (status, now(), ticket["id"]))
    store.audit(session["username"], client_ip(request), "web.ticket_status", {"ticket": ticket["id"], "status": status})
    return web.json_response({"ok": True})


async def web_attachment(request: web.Request) -> web.Response:
    require(request)
    rows = request.app["store"].q("SELECT * FROM web_attachments WHERE id=?", (int(request.match_info["aid"]),))
    if not rows:
        return fail(404, "attachment")
    return _image(rows[0])


async def web_messages(request: web.Request) -> web.Response:
    require(request)
    store: Store = request.app["store"]
    rows = store.q("SELECT m.*, (SELECT COUNT(*) FROM web_inbox_state s WHERE s.message_id=m.id AND s.read_at IS NOT NULL) AS reads "
                   "FROM web_inbox m WHERE m.kind!='ticket' ORDER BY m.id DESC LIMIT 200")
    return web.json_response({"items": [dict(r) for r in rows],
                              "accounts": store.one("SELECT COUNT(*) FROM web_accounts") or 0})


async def web_message_send(request: web.Request) -> web.Response:
    """A message in the panel's inbox: to one web account, or to every one ({account: null})."""
    session = require(request, mutating=True)
    body = await read_json(request)
    store: Store = request.app["store"]
    title, text = clean_text(body.get("title"), 120), clean_text(body.get("body"), 2000)
    if not title or not text:
        return fail(400, "title_and_body_required")
    kind = body.get("kind") if body.get("kind") in PANEL_INBOX_KINDS and body.get("kind") != "ticket" else "info"
    link = clean_text(body.get("link"), 300)
    if link and not (LINK_PATTERN.match(link) or re.fullmatch(r"/next/[\w\-/]*", link)):
        return fail(400, "link")
    account = body.get("account")
    if account is not None:
        account = str(account)
        if not PANEL_ALIAS.match(account) or _account_row(store, account) is None:
            return fail(404, "web_account")
    message_id = _inbox_message(store, account, kind, title, text, link, session["username"])
    store.audit(session["username"], client_ip(request), "web.message",
                {"id": message_id, "account": account or "all", "kind": kind, "title": title})
    return web.json_response({"id": message_id})


async def web_errors(request: web.Request) -> web.Response:
    """The pages' own errors, newest first and grouped by message, across every web account."""
    require(request)
    store: Store = request.app["store"]
    try:
        days = max(1, min(60, int(request.query.get("days", 7))))
    except ValueError:
        days = 7
    q = clean_text(request.query.get("q"), 80).lower()
    rows = store.q("SELECT * FROM web_events WHERE kind='error' AND at>=? AND (?='' OR LOWER(name) LIKE ? OR LOWER(detail) LIKE ?) "
                   "ORDER BY id DESC LIMIT 500", (now() - days * 86400, q, f"%{q}%", f"%{q}%"))
    groups: dict[str, dict] = {}
    for row in rows:
        detail = _jsonish(row["detail"], {}) if row["detail"] else {}
        key = row["name"] or "?"
        group = groups.setdefault(key, {"name": key, "count": 0, "accounts": set(), "last_at": row["at"], "first_at": row["at"],
                                        "sample": detail, "browsers": defaultdict(int), "versions": defaultdict(int)})
        group["count"] += 1
        group["first_at"] = row["at"]
        if row["account"]:
            group["accounts"].add(row["account"])
        group["browsers"][f"{row['browser'] or '?'} · {row['os'] or '?'}"] += 1
        group["versions"][str(detail.get("version") or "?")] += 1
    out = sorted(groups.values(), key=lambda g: (-g["count"], -g["last_at"]))
    for group in out:
        group["accounts"] = sorted(group["accounts"])[:20]
        group["browsers"] = dict(group["browsers"])
        group["versions"] = dict(group["versions"])
    return web.json_response({"groups": out[:100], "recent": [_event_row(r, owner=True) for r in rows[:100]], "days": days})


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
    if len(args) < 2 or args[0] not in ("forget-install", "forget-ip", "forget-hour"):
        print("usage: python admin.py forget-install <install_id> | forget-ip <address> [YYYY-MM-DD] | "
              "forget-hour <YYYY-MM-DD> <hour>", file=sys.stderr)
        return 2
    store = Store(Config().db_path)
    removed = 0
    if args[0] == "forget-hour":
        # One check-in's unit in the hours heat map, for traffic from before install_hours existed.
        try:
            day, hour = args[1], int(args[2])
        except (IndexError, ValueError):
            print("usage: python admin.py forget-hour <YYYY-MM-DD> <hour>", file=sys.stderr)
            return 2
        removed = store.x("UPDATE active_hours SET count=count-1 WHERE day=? AND hour=? AND count>0", (day, hour)).rowcount
        store.x("DELETE FROM active_hours WHERE count<=0")
        store.audit("console", "console", "data.forget_hour", {"day": day, "hour": hour, "rows": removed})
    elif args[0] == "forget-install":
        id_hash = store.anon(args[1].strip())
        for row in store.q("SELECT day, hour, count FROM install_hours WHERE id_hash=?", (id_hash,)):
            store.x("UPDATE active_hours SET count=MAX(0, count-?) WHERE day=? AND hour=?", (row["count"], row["day"], row["hour"]))
        store.x("DELETE FROM active_hours WHERE count<=0")
        for table in ("installs", "active_days", "message_events", "install_hours"):
            removed += store.x(f"DELETE FROM {table} WHERE id_hash=?", (id_hash,)).rowcount
        store.audit("console", "console", "data.forget_install", {"rows": removed})
    else:
        ip_hash = store.anon("ip|" + args[1].strip())
        day = args[2] if len(args) > 2 else None
        if day is not None and not re.fullmatch(r"\d{4}-\d{2}-\d{2}", day):
            print("the day is YYYY-MM-DD", file=sys.stderr)
            return 2
        # The raw request counts are kept per file and day, not per address: take one request back
        # for each download of this address being forgotten, and drop a day's row it leaves empty.
        downloads = store.q("SELECT day, file FROM downloads WHERE ip_hash=?" + (" AND day=?" if day else ""),
                            (ip_hash, day) if day else (ip_hash,))
        for row in downloads:
            store.x("UPDATE download_hits SET hits=MAX(0, hits-1) WHERE day=? AND file=?", (row["day"], row["file"]))
        store.x("DELETE FROM download_hits WHERE hits<=0")
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
    if len(sys.argv) > 1 and sys.argv[1] in ("forget-install", "forget-ip", "forget-hour"):
        raise SystemExit(forget_cli(sys.argv[1:]))
    if len(sys.argv) > 1 and sys.argv[1] == "ingest":
        app = build_app(Config(background=False))
        print(f"{ingest_logs(app)} lines read")
        return
    web.run_app(build_app(), host=os.environ.get("ADMIN_HOST", "0.0.0.0"), port=int(os.environ.get("ADMIN_PORT", "8791")),
                access_log=None)


if __name__ == "__main__":
    main()
