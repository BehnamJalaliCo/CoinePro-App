"""The web app's relay: the two routes docs/web/SERVER.md §4.12 and §4.13 ask pro-chart.com for.

* `/up/tradeyar/<path>` and `/up/coineprofx/<path>` — the phone's calls to its two backends, from
  the page, on the page's own origin. Same method, path, query and body; a short list of request
  headers; the upstream's status, type and body back unchanged. WebSocket upgrades too.
* **The bearer never reaches the page.** A sign-in or refresh answer has each token replaced by an
  opaque handle, the real one kept here against an `HttpOnly` cookie. A later request's
  `Authorization: Bearer <handle>` (or a refresh body carrying a handle) is swapped back only when
  that browser's cookie holds it. A browser cannot put headers on a WebSocket, so an upgrade gets the
  session's latest bearer for that backend from the cookie alone.
* `/api/img?url=` — a publisher's photo, read for the page, which may show another site's image but
  not read its bytes. `https://` only, image types only, 5 MB, and never an address inside a private
  network, checked on the address actually connected to.

* `/up/panel/<path>` — the web user panel (`/next/panel`). The relay knows who each browser is: on
  sign-in it asks that backend's `me` route with the real token and keeps the account's id, email
  and name with the session, beside when it began, when it was last seen, the browser and device
  family, a coarse country and a salted hash of the address (never the address). Panel calls go on
  to the owner console's internal routes with a shared secret and that verified identity; the page
  never names its own account. The sessions list and revoke, and this browser's sign-out, are
  answered here, since only the relay holds the sessions.
* It also tells the console, server to server and without waiting, what it saw: sign-ins, sign-outs,
  refresh failures, and every trading call it relayed (orders, closes, protection, leverage, signal
  executions, exchange keys) with the symbol, side, quantity and type it carried and the order id
  that came back — never a key, a secret or a token.
* `/relay/internal/…` — the console's own calls, with the same secret: a reader's live sessions,
  and revoking one or all of them. Caddy never routes `/relay/*`.

One process, state in memory: a restart signs browsers out, exactly as clearing the app's data does
on the phone. Standard library and aiohttp only. Run `python3 relay.py`, or the Dockerfile beside
it; `README.md` has the Caddy lines that put it under pro-chart.com.
"""
from __future__ import annotations

import asyncio
import base64
import hashlib
import hmac
import ipaddress
import json
import os
import re
import secrets
import socket
import sys
import time
from dataclasses import asdict, dataclass, field, fields
from urllib.parse import urlsplit

import aiohttp
from aiohttp import web
from yarl import URL

# ── Configuration ───────────────────────────────────────────────────────────────────────────────


@dataclass
class Config:
    backends: dict[str, str] = field(default_factory=lambda: {
        "tradeyar": os.environ.get("RELAY_TRADEYAR_ORIGIN", "https://tradeyar.trade-future.ir/"),
        "coineprofx": os.environ.get("RELAY_COINEPROFX_ORIGIN", "https://coineprofx.com/"),
    })
    cookie_name: str = "pc_session"
    cookie_secure: bool = os.environ.get("RELAY_COOKIE_SECURE", "1") != "0"
    session_days: int = int(os.environ.get("RELAY_SESSION_DAYS", "30"))
    body_limit: int = 8 * 1024 * 1024
    image_limit: int = 5 * 1024 * 1024
    # Tests point the image route at a local server; production never sets this.
    image_allow_private: bool = os.environ.get("RELAY_IMAGE_ALLOW_PRIVATE", "0") == "1"
    image_allow_http: bool = False
    requests_per_minute: int = int(os.environ.get("RELAY_REQUESTS_PER_MINUTE", "240"))
    auth_requests_per_minute: int = int(os.environ.get("RELAY_AUTH_REQUESTS_PER_MINUTE", "12"))
    # The client id is the reader's to choose, so a second ceiling on sign-in routes counts the
    # address alone: rotating X-Client-Id buys a guesser nothing past this.
    auth_address_requests_per_minute: int = int(os.environ.get("RELAY_AUTH_ADDRESS_REQUESTS_PER_MINUTE", "30"))
    # Where to *connect* for a backend, when that is not its public name: an address on the private
    # network. TLS is still verified against the public name (SNI and the certificate), and the Host
    # header still names it, so the backend cannot tell the difference and nothing is turned off.
    connect: dict[str, str] = field(default_factory=lambda: {
        name: value for name, value in (
            ("tradeyar", os.environ.get("RELAY_TRADEYAR_CONNECT", "")),
            ("coineprofx", os.environ.get("RELAY_COINEPROFX_CONNECT", "")),
        ) if value
    })
    # The web user panel. The owner console's internal routes, and the secret both services hold
    # (at least 32 characters; shorter or empty turns the panel's server side off).
    panel_url: str = field(default_factory=lambda: os.environ.get(
        "RELAY_PANEL_URL", "http://admin:8791/internal/panel").rstrip("/"))
    panel_token: str = field(default_factory=lambda: os.environ.get("PANEL_INTERNAL_TOKEN", ""))
    panel_body_limit: int = 2 * 1024 * 1024
    # The country the edge puts on each request (Cloudflare's CF-IPCountry); two letters or nothing.
    country_header: str = field(default_factory=lambda: os.environ.get("RELAY_COUNTRY_HEADER", "CF-IPCountry"))
    # Salts the address hash. Unset, a random one per process: the hashes then only tell sessions of
    # this run apart, which is all they are for.
    hash_salt: str = field(default_factory=lambda: os.environ.get("RELAY_HASH_SALT", "") or secrets.token_hex(16))
    events_batch: int = 50
    # Where the sessions are kept across a restart, encrypted with RELAY_STATE_KEY (any long random
    # string). Either unset: memory only, and a rebuild signs every web reader out.
    state_file: str = field(default_factory=lambda: os.environ.get("RELAY_STATE_FILE", ""))
    state_key: str = field(default_factory=lambda: os.environ.get("RELAY_STATE_KEY", ""))
    state_every: float = 60.0
    # The page signs in on both platforms with one e-mail and password; the one that does not know the
    # reader answers 401 a moment before the other lets them in. A refused sign-in waits this long,
    # and is not reported when the same e-mail from the same address gets into the other platform.
    probe_grace: float = 15.0

    @property
    def persist(self) -> bool:
        return bool(self.state_file) and len(self.state_key) >= 32

    @property
    def panel_enabled(self) -> bool:
        return len(self.panel_token) >= 32 and bool(self.panel_url)


# The request headers the phone sends (AuthInterceptor, the install-id and version interceptors, the
# community key), and nothing else. Never the reader's Cookie: that is this relay's, not theirs.
FORWARDED_REQUEST_HEADERS = (
    "Authorization", "Content-Type", "Accept", "Accept-Language", "X-Install-Id", "X-App-Platform",
    "X-App-Version", "X-Play-Integrity", "X-Play-Integrity-Nonce", "X-Community-Key", "X-Client-Id",
    # Where a sign-in came from, for the owner console's user list (5.35.0).
    "X-Client-Platform", "X-Client-Flavor",
)
RETURNED_RESPONSE_HEADERS = ("Content-Type", "Retry-After", "X-Request-Id")

# Where each backend hands out tokens (`AuthPaths` in core/auth, `EndpointCatalog` in
# core/diagnostics): sign-in, sign-up verification, Google, Telegram, guest, refresh — and the
# forex-to-crypto link (5.25.1), which mints a TradeYar session like a sign-in does and was handing
# the browser the real tokens.
AUTH_ISSUING = re.compile(
    r"^(api/mobile/v1/auth/(login|refresh|register/verify|google|link/coinepro)"
    r"|api/user/auth/(login|refresh|register/verify|google|telegram|guest))/?$"
)
AUTH_LOGOUT = re.compile(r"^(api/mobile/v1/auth/logout|api/user/auth/logout)/?$")
AUTH_ANY = re.compile(r"^api/(mobile/v1|user)/auth/")
# A reader trying to sign in (not a refresh, not a guest token): a refusal here is a failed sign-in.
AUTH_ATTEMPT = re.compile(
    r"^(api/mobile/v1/auth/(login|register/verify|google|link/coinepro)"
    r"|api/user/auth/(login|register/verify|google|telegram))/?$"
)
# Every call that places, changes or closes something on an exchange or a broker account, on either
# platform (`{p}/trade/…`, `{p}/executions…`, `{p}/venues/…` in the wire contract).
TRADE_ROUTE = re.compile(
    r"^api/(mobile/v1|user/mobile)/(trade/(orders(/[^/]+)?|positions/(close|protection)|leverage)"
    r"|executions(/[^/]+(/close)?)?|venues/[^/]+)/?$"
)
MUTATING = ("POST", "PUT", "PATCH", "DELETE")
# What of a trading call's body the audit keeps. A list of what may go, not of what may not: an API
# key or a secret is never on it, whatever the field is called.
TRADE_FIELDS = ("symbol", "side", "type", "quantity", "price", "leverage", "reduce_only", "stop_loss",
                "take_profit", "signal_id", "venue", "client_request_id", "confirmed", "permission",
                "product", "direction", "volume", "tif", "trigger_price")
ME_ROUTE = {"tradeyar": "api/mobile/v1/me", "coineprofx": "api/user/me"}
TOKEN_KEYS = ("access_token", "accessToken", "refresh_token", "refreshToken", "token")
HANDLE_PREFIX = "pch_"

IMAGE_TYPES = ("image/jpeg", "image/png", "image/webp", "image/gif", "image/avif")


# ── Sessions: handle ↔ token, per browser ───────────────────────────────────────────────────────


@dataclass
class Session:
    touched: float
    tokens: dict[str, str] = field(default_factory=dict)          # handle → real token
    latest_bearer: dict[str, str] = field(default_factory=dict)   # backend → real access token
    # Who the browser is, per backend, as that backend's `me` route said: {id, email, name, vip, paid, plan}.
    identity: dict[str, dict] = field(default_factory=dict)
    created: float = field(default_factory=time.time)
    # A public name for the session (a hash of its secret id), for lists and revoking.
    sid: str = ""
    browser: str = ""
    os: str = ""
    device: str = ""
    country: str = ""
    ip_hash: str = ""

    def accounts(self) -> list[str]:
        return [f"{backend}:{who['id']}" for backend, who in sorted(self.identity.items(), key=_backend_order)
                if who.get("id") not in (None, "")]


def _backend_order(item: tuple[str, dict]) -> int:
    # TradeYar first: a CoinePro-FX reader is given a TradeYar account too, so it is the one both share.
    return {"tradeyar": 0, "coineprofx": 1}.get(item[0], 2)


class Sessions:
    def __init__(self, ttl_seconds: float) -> None:
        self.ttl = ttl_seconds
        self.by_id: dict[str, Session] = {}

    def get(self, session_id: str | None) -> Session | None:
        if not session_id:
            return None
        session = self.by_id.get(session_id)
        if session is None:
            return None
        if time.time() - session.touched > self.ttl:
            del self.by_id[session_id]
            return None
        session.touched = time.time()
        return session

    def create(self) -> tuple[str, Session]:
        session_id = secrets.token_urlsafe(32)
        session = Session(touched=time.time(), sid=hashlib.sha256(session_id.encode()).hexdigest()[:16])
        self.by_id[session_id] = session
        return session_id, session

    def sweep(self) -> None:
        now = time.time()
        for key in [k for k, s in self.by_id.items() if now - s.touched > self.ttl]:
            del self.by_id[key]

    def live(self) -> list[Session]:
        now = time.time()
        return [s for s in self.by_id.values() if now - s.touched <= self.ttl]

    def of_account(self, account: str) -> list[tuple[str, Session]]:
        return [(key, s) for key, s in self.by_id.items() if account in s.accounts()]

    def drop(self, key: str) -> None:
        self.by_id.pop(key, None)


# ── Sessions across a restart: one encrypted file ──────────────────────────────────────────────
# A session holds the platforms' real tokens, so the file is Fernet-encrypted (AES-128-CBC + HMAC)
# with a key derived from RELAY_STATE_KEY, written to a temporary name with mode 600 and moved into
# place. A file that does not open with the key (a new key, a damaged file) is ignored: the readers
# sign in again, nothing else breaks.

_SESSION_FIELDS = {f.name for f in fields(Session)}


def _fernet(secret: str):
    from cryptography.fernet import Fernet  # only where persistence is on

    return Fernet(base64.urlsafe_b64encode(hashlib.sha256(secret.encode()).digest()))


def save_sessions(sessions: Sessions, path: str, secret: str) -> int:
    rows = [{"id": key, **asdict(s)} for key, s in sessions.by_id.items()]
    blob = _fernet(secret).encrypt(json.dumps({"v": 1, "saved": time.time(), "sessions": rows}, separators=(",", ":")).encode())
    tmp = f"{path}.tmp"
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "wb") as out:
        out.write(blob)
        out.flush()
        os.fsync(out.fileno())
    os.replace(tmp, path)
    return len(rows)


def load_sessions(sessions: Sessions, path: str, secret: str) -> int:
    try:
        with open(path, "rb") as source:
            blob = source.read()
    except FileNotFoundError:
        return 0
    try:
        data = json.loads(_fernet(secret).decrypt(blob))
    except Exception as error:  # noqa: BLE001 — a key change or a damaged file: start empty
        print(f"relay state not restored: {type(error).__name__}", file=sys.stderr)
        return 0
    now, restored = time.time(), 0
    for row in data.get("sessions", []) if isinstance(data, dict) else []:
        key = row.pop("id", None) if isinstance(row, dict) else None
        if not isinstance(key, str) or not key:
            continue
        try:
            session = Session(**{k: v for k, v in row.items() if k in _SESSION_FIELDS})
        except TypeError:
            continue
        if now - float(session.touched) <= sessions.ttl:
            sessions.by_id[key] = session
            restored += 1
    return restored


def _new_handle() -> str:
    return HANDLE_PREFIX + secrets.token_urlsafe(24)


def swap_out(payload, session: Session, backend: str):
    """Every token in a sign-in answer replaced by a handle, wherever in the JSON it sits."""
    if isinstance(payload, dict):
        result = {}
        for key, value in payload.items():
            if key in TOKEN_KEYS and isinstance(value, str) and value:
                handle = _new_handle()
                session.tokens[handle] = value
                if key in ("access_token", "accessToken", "token"):
                    session.latest_bearer[backend] = value
                result[key] = handle
            else:
                result[key] = swap_out(value, session, backend)
        return result
    if isinstance(payload, list):
        return [swap_out(item, session, backend) for item in payload]
    return payload


def swap_in(payload, session: Session | None):
    """A request body with each handle this browser holds put back as its token."""
    if isinstance(payload, dict):
        return {key: swap_in(value, session) for key, value in payload.items()}
    if isinstance(payload, list):
        return [swap_in(item, session) for item in payload]
    if isinstance(payload, str) and payload.startswith(HANDLE_PREFIX):
        return session.tokens.get(payload, "") if session else ""
    return payload


# ── Rate limits: address and client, as §6 keys them ──────────────────────────────────────────


class Buckets:
    def __init__(self) -> None:
        self.hits: dict[tuple[str, str, bool], list[float]] = {}

    def allow(self, key: tuple[str, str, bool], per_minute: int) -> bool:
        now = time.monotonic()
        window = [t for t in self.hits.get(key, ()) if now - t < 60.0]
        if len(window) >= per_minute:
            self.hits[key] = window
            return False
        window.append(now)
        self.hits[key] = window
        return True


def reader_address(request: web.Request) -> str:
    """The reader's own address, as our nginx appended it: the right-most `X-Forwarded-For` entry.

    Never the left-most, which is whatever the browser chose to send. Handed to the backends as
    `X-Forwarded-For` and `X-Real-IP` (5.25.2): without it every reader of the site reached TradeYar
    and CoinePro-FX from this relay's one address, so their per-address limits were one bucket for
    the whole site and a busy minute answered everybody 429.
    """
    forwarded = request.headers.get("X-Forwarded-For", "")
    last = forwarded.split(",")[-1].strip() if forwarded else ""
    return last or (request.remote or "")


def client_key(request: web.Request) -> tuple[str, str]:
    forwarded = request.headers.get("X-Forwarded-For", "")
    address = forwarded.split(",")[0].strip() if forwarded else (request.remote or "")
    return address, request.headers.get("X-Client-Id", "")[:64]


# ── The public-address guard for /api/img ──────────────────────────────────────────────────────


def is_public(address: str) -> bool:
    ip = ipaddress.ip_address(address)
    if isinstance(ip, ipaddress.IPv6Address) and ip.ipv4_mapped:
        ip = ip.ipv4_mapped
    return ip.is_global and not (ip.is_private or ip.is_loopback or ip.is_link_local
                                 or ip.is_multicast or ip.is_reserved or ip.is_unspecified)


class PublicOnlyResolver(aiohttp.abc.AbstractResolver):
    """Resolves as usual and drops every address that is not public, so the address connected to
    is the address that was checked — no second lookup for a rebinding name to change."""

    def __init__(self, allow_private: bool) -> None:
        self.inner = aiohttp.ThreadedResolver()
        self.allow_private = allow_private

    async def resolve(self, host: str, port: int = 0, family: int = socket.AF_INET):
        answers = await self.inner.resolve(host, port, family)
        kept = [a for a in answers if self.allow_private or is_public(a["host"])]
        if not kept:
            raise OSError(f"{host} resolves to no public address")
        return kept

    async def close(self) -> None:
        await self.inner.close()


# ── The app ─────────────────────────────────────────────────────────────────────────────────────

CONFIG = web.AppKey("config", Config)
SESSIONS = web.AppKey("sessions", Sessions)
BUCKETS = web.AppKey("buckets", Buckets)
UPSTREAM = web.AppKey("upstream", aiohttp.ClientSession)
IMAGES = web.AppKey("images", aiohttp.ClientSession)
SWEEPER = web.AppKey("sweeper", asyncio.Task)
EVENTS = web.AppKey("events", asyncio.Queue)
SENDER = web.AppKey("sender", asyncio.Task)
PENDING = web.AppKey("pending", set)
PROBES = web.AppKey("probes", dict)


def build_app(config: Config | None = None) -> web.Application:
    config = config or Config()
    app = web.Application(client_max_size=config.body_limit)
    app[CONFIG] = config
    app[SESSIONS] = Sessions(config.session_days * 86400)
    app[BUCKETS] = Buckets()
    app[PENDING] = set()
    app[PROBES] = {}
    # The panel's own routes before the backends' catch-all, which would read "panel" as a backend.
    app.router.add_get("/up/panel/sessions", panel_sessions)
    app.router.add_post("/up/panel/sessions/revoke", panel_revoke)
    app.router.add_post("/up/panel/signout", panel_signout)
    app.router.add_route("*", "/up/panel/{path:.*}", panel_forward)
    app.router.add_route("*", "/up/{backend}/{path:.*}", passthrough)
    app.router.add_get("/api/img", image)
    app.router.add_get("/relay/health", health)
    app.router.add_get("/relay/internal/sessions", internal_sessions)
    app.router.add_post("/relay/internal/sessions/revoke", internal_revoke)
    app.on_startup.append(_open_clients)
    app.on_cleanup.append(_close_clients)
    return app


async def health(_: web.Request) -> web.Response:
    return web.json_response({"ok": True})


async def _open_clients(app: web.Application) -> None:
    config: Config = app[CONFIG]
    app[UPSTREAM] = aiohttp.ClientSession(
        timeout=aiohttp.ClientTimeout(total=60, sock_connect=10),
        auto_decompress=True,
        cookie_jar=aiohttp.DummyCookieJar(),
    )
    app[IMAGES] = aiohttp.ClientSession(
        timeout=aiohttp.ClientTimeout(total=20, sock_connect=5),
        connector=aiohttp.TCPConnector(resolver=PublicOnlyResolver(config.image_allow_private)),
        cookie_jar=aiohttp.DummyCookieJar(),
        headers={"User-Agent": "ProChartImageRelay/1.0 (+https://pro-chart.com)"},
    )

    if config.persist:
        try:
            restored = await asyncio.to_thread(load_sessions, app[SESSIONS], config.state_file, config.state_key)
            print(f"relay sessions restored: {restored}", file=sys.stderr)
        except Exception as error:  # noqa: BLE001 — never keep the relay from starting
            print(f"relay state not restored: {type(error).__name__}", file=sys.stderr)
    elif config.state_file:
        print("RELAY_STATE_FILE is set without a RELAY_STATE_KEY of 32+ characters: sessions stay in memory", file=sys.stderr)

    async def sweep() -> None:
        last_sweep = time.monotonic()
        while True:
            await asyncio.sleep(config.state_every if config.persist else 600)
            if time.monotonic() - last_sweep >= 600:
                app[SESSIONS].sweep()
                last_sweep = time.monotonic()
            if config.persist:
                await _save_state(app)

    app[SWEEPER] = asyncio.create_task(sweep())
    app[EVENTS] = asyncio.Queue(maxsize=5000)
    app[SENDER] = asyncio.create_task(_send_events(app))


async def _save_state(app: web.Application) -> None:
    config: Config = app[CONFIG]
    try:
        await asyncio.to_thread(save_sessions, app[SESSIONS], config.state_file, config.state_key)
    except Exception as error:  # noqa: BLE001 — the next round tries again
        print(f"relay state not saved: {type(error).__name__}", file=sys.stderr)


async def _close_clients(app: web.Application) -> None:
    app[SWEEPER].cancel()
    for held in list(app[PROBES].values()):
        for _, task in held:
            task.cancel()
    if app[CONFIG].persist:
        await _save_state(app)
    app[SENDER].cancel()
    for task in list(app[PENDING]):
        task.cancel()
    await app[UPSTREAM].close()
    await app[IMAGES].close()


def _limited(request: web.Request, auth: bool) -> bool:
    config: Config = request.app[CONFIG]
    address, client = client_key(request)
    per_minute = config.auth_requests_per_minute if auth else config.requests_per_minute
    if not request.app[BUCKETS].allow((address, client, auth), per_minute):
        return True
    return auth and not request.app[BUCKETS].allow((address, "*", True), config.auth_address_requests_per_minute)


def upstream_route(config: Config, backend: str, path_and_query: str) -> tuple[str, dict[str, str], dict]:
    """The URL to open for a backend, the Host header it needs, and the TLS name to verify."""
    origin = config.backends[backend]
    connect = config.connect.get(backend)
    if not connect:
        return origin + path_and_query, {}, {}
    public = urlsplit(origin).hostname or ""
    return connect + path_and_query, {"Host": public}, {"server_hostname": public}


async def passthrough(request: web.Request) -> web.StreamResponse:
    config: Config = request.app[CONFIG]
    backend = request.match_info["backend"]
    origin = config.backends.get(backend)
    if origin is None:
        return web.json_response({"detail": "unknown backend"}, status=404)
    path = request.match_info["path"]
    if ".." in path.split("/"):
        return web.json_response({"detail": "bad path"}, status=400)
    auth_route = bool(AUTH_ANY.match(path))
    if _limited(request, auth_route and request.method != "GET"):
        return web.json_response({"detail": "too many requests"}, status=429, headers={"Retry-After": "60"})

    sessions: Sessions = request.app[SESSIONS]
    session = sessions.get(request.cookies.get(config.cookie_name))
    if session is not None:
        note_client(config, session, request)
    target, route_headers, tls = upstream_route(config, backend, path + (("?" + request.query_string) if request.query_string else ""))

    headers = {name: request.headers[name] for name in FORWARDED_REQUEST_HEADERS if name in request.headers}
    headers.update(route_headers)
    reader = reader_address(request)
    if reader:
        headers["X-Forwarded-For"] = reader
        headers["X-Real-IP"] = reader
    bearer = headers.get("Authorization", "")
    if bearer.startswith("Bearer " + HANDLE_PREFIX):
        real = session.tokens.get(bearer[len("Bearer "):]) if session else None
        if real:
            headers["Authorization"] = "Bearer " + real
        else:
            # A handle from another browser, or from before a restart: the backend's own 401, never
            # a request that carries somebody else's credential or a meaningless one.
            headers.pop("Authorization")

    if request.headers.get("Upgrade", "").lower() == "websocket":
        if "Authorization" not in headers and session and backend in session.latest_bearer:
            headers["Authorization"] = "Bearer " + session.latest_bearer[backend]
        return await _socket(request, target, headers, tls)

    body = await request.read() if request.can_read_body else None
    if body and auth_route and "json" in headers.get("Content-Type", ""):
        try:
            body = json.dumps(swap_in(json.loads(body), session)).encode()
        except ValueError:
            pass

    try:
        async with request.app[UPSTREAM].request(request.method, target, headers=headers, data=body,
                                                   allow_redirects=False, **tls) as upstream:
            payload = await upstream.read()
            status = upstream.status
            returned = {name: upstream.headers[name] for name in RETURNED_RESPONSE_HEADERS if name in upstream.headers}
    except (aiohttp.ClientError, asyncio.TimeoutError):
        return web.json_response({"detail": "upstream unreachable"}, status=502)

    response_cookie = None
    issuing = AUTH_ISSUING.match(path)
    if issuing and 200 <= status < 300 and "json" in returned.get("Content-Type", ""):
        try:
            answer = json.loads(payload)
        except ValueError:
            answer = None
        if answer is not None:
            if session is None:
                session_id, session = sessions.create()
                note_client(config, session, request)
                response_cookie = session_id
            hint = read_identity(answer)
            payload = json.dumps(swap_out(answer, session, backend)).encode()
            method = sign_in_method(path)
            if method == "refresh":
                if backend not in session.identity:
                    background(request.app, resolve_identity(request.app, session, backend))
            elif method != "guest":
                drop_probe(request.app, backend, email_hash(body, config.panel_token), session.ip_hash)
                background(request.app, signed_in(request.app, session, backend, method, hint))
    elif AUTH_ATTEMPT.match(path) and status in (401, 403):
        failed = {"kind": "signin_failed", "backend": backend, "method": sign_in_method(path),
                  "email_hash": email_hash(body, config.panel_token), **client_fields(session, request, config)}
        hold_failure(request.app, failed)
    elif issuing and sign_in_method(path) == "refresh" and status in (401, 403) and session is not None:
        # The page signs that platform out now; so does the relay.
        emit(request.app, {"kind": "refresh_failed", "backend": backend, **session_fields(session)})
        session.latest_bearer.pop(backend, None)
        session.identity.pop(backend, None)
    if AUTH_LOGOUT.match(path) and session is not None:
        emit(request.app, {"kind": "signout", "backend": backend, "how": "logout", **session_fields(session)})
        session.latest_bearer.pop(backend, None)
        session.identity.pop(backend, None)
    if request.method in MUTATING and TRADE_ROUTE.match(path):
        background(request.app, trade_event(request.app, session, backend, request.method, path, body,
                                            status, payload))

    response = web.Response(body=payload, status=status, headers=returned)
    response.headers["Cache-Control"] = "no-store"
    if response_cookie:
        response.set_cookie(config.cookie_name, response_cookie, max_age=config.session_days * 86400,
                            path="/up/", httponly=True, secure=config.cookie_secure, samesite="Strict")
    return response


async def _socket(request: web.Request, target: str, headers: dict[str, str], tls: dict) -> web.StreamResponse:
    upstream_url = target.replace("https://", "wss://", 1).replace("http://", "ws://", 1)
    headers.pop("Content-Type", None)
    try:
        upstream = await request.app[UPSTREAM].ws_connect(upstream_url, headers=headers, heartbeat=30, **tls)
    except (aiohttp.ClientError, asyncio.TimeoutError):
        return web.json_response({"detail": "upstream unreachable"}, status=502)
    page = web.WebSocketResponse(heartbeat=30)
    await page.prepare(request)

    async def pump(source, sink) -> None:
        async for message in source:
            if message.type == aiohttp.WSMsgType.TEXT:
                await sink.send_str(message.data)
            elif message.type == aiohttp.WSMsgType.BINARY:
                await sink.send_bytes(message.data)
            else:
                break

    tasks = [asyncio.create_task(pump(upstream, page)), asyncio.create_task(pump(page, upstream))]
    await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
    for task in tasks:
        task.cancel()
    await upstream.close()
    await page.close()
    return page


async def image(request: web.Request) -> web.StreamResponse:
    config: Config = request.app[CONFIG]
    if _limited(request, False):
        return web.Response(status=429, headers={"Retry-After": "60"})
    url = request.query.get("url", "")
    for _ in range(4):
        parts = urlsplit(url)
        allowed = ("https",) + (("http",) if config.image_allow_http else ())
        if parts.scheme not in allowed or not parts.hostname or parts.username or parts.password:
            return web.Response(status=400, text="https only")
        # A literal address never reaches the resolver, so it is checked here.
        try:
            literal = ipaddress.ip_address(parts.hostname)
        except ValueError:
            literal = None
        if literal is not None and not config.image_allow_private and not is_public(str(literal)):
            return web.Response(status=400, text="public addresses only")
        try:
            async with request.app[IMAGES].get(url, allow_redirects=False) as upstream:
                if upstream.status in (301, 302, 303, 307, 308) and "Location" in upstream.headers:
                    url = str(upstream.url.join(URL(upstream.headers["Location"])))
                    continue
                if upstream.status != 200:
                    return web.Response(status=502 if upstream.status >= 500 else upstream.status)
                kind = upstream.headers.get("Content-Type", "").split(";")[0].strip().lower()
                # Some publishers send a photo with no type or a generic one; those are judged by
                # their first bytes below, and anything that is not an image is still refused.
                generic = kind in ("", "application/octet-stream", "binary/octet-stream")
                if kind not in IMAGE_TYPES and not generic:
                    return web.Response(status=415)
                declared = upstream.content_length
                if declared is not None and declared > config.image_limit:
                    return web.Response(status=413)
                chunks, size = [], 0
                async for chunk in upstream.content.iter_chunked(64 * 1024):
                    size += len(chunk)
                    if size > config.image_limit:
                        return web.Response(status=413)
                    chunks.append(chunk)
        except (aiohttp.ClientError, asyncio.TimeoutError, OSError):
            return web.Response(status=502)
        body = b"".join(chunks)
        sniffed = sniff_image(body)
        if sniffed is None:
            return web.Response(status=415)
        kind = sniffed if generic else kind
        return web.Response(body=body, content_type=kind,
                            headers={"Cache-Control": "public, max-age=86400", "X-Content-Type-Options": "nosniff"})
    return web.Response(status=508, text="too many redirects")


def sniff_image(body: bytes) -> str | None:
    """The image type a body's first bytes say it is, or None. Checked on every photo, typed or not."""
    if body.startswith(b"\xff\xd8\xff"):
        return "image/jpeg"
    if body.startswith(b"\x89PNG\r\n\x1a\n"):
        return "image/png"
    if body[:6] in (b"GIF87a", b"GIF89a"):
        return "image/gif"
    if body[:4] == b"RIFF" and body[8:12] == b"WEBP":
        return "image/webp"
    if body[4:8] == b"ftyp" and body[8:12] in (b"avif", b"avis"):
        return "image/avif"
    return None


# ── Who the browser is ──────────────────────────────────────────────────────────────────────────


def agent_family(agent: str) -> tuple[str, str, str]:
    """(browser, operating system, device) from a User-Agent: families only, never the version string."""
    a = agent or ""
    browser = ("Edge" if "Edg/" in a else "Opera" if "OPR/" in a else "Samsung Internet" if "SamsungBrowser/" in a
               else "Firefox" if ("Firefox/" in a or "FxiOS/" in a) else "Chrome" if ("Chrome/" in a or "CriOS/" in a)
               else "Safari" if "Safari/" in a else "Other")
    system = ("Windows" if "Windows" in a else "Android" if "Android" in a
              else "iOS" if re.search(r"iPhone|iPad|iPod", a) else "macOS" if "Mac OS X" in a
              else "ChromeOS" if "CrOS" in a else "Linux" if "Linux" in a else "Other")
    device = ("tablet" if "iPad" in a or ("Android" in a and "Mobile" not in a)
              else "phone" if re.search(r"Mobi|iPhone|iPod|Android", a) else "desktop")
    return browser, system, device


def note_client(config: Config, session: Session, request: web.Request) -> None:
    """The session's browser, device, country and hashed address, as of its latest request."""
    if not session.browser:
        session.browser, session.os, session.device = agent_family(request.headers.get("User-Agent", ""))
    country = request.headers.get(config.country_header, "").strip().upper()
    if re.fullmatch(r"[A-Z]{2}", country) and country not in ("XX", "T1"):
        session.country = country
    address = reader_address(request)
    if address:
        session.ip_hash = hashlib.sha256(f"{config.hash_salt}|ip|{address}".encode()).hexdigest()[:16]


def read_identity(payload) -> dict | None:
    """An account as a sign-in answer or a `me` route describes it: TradeYar wraps it in `user`."""
    if not isinstance(payload, dict):
        return None
    for key in ("user", "profile"):
        if isinstance(payload.get(key), dict):
            payload = payload[key]
            break

    def pick(*keys):
        for key in keys:
            value = payload.get(key)
            if value not in (None, ""):
                return value
        return None

    ident = pick("id", "user_id", "userId", "telegram_id", "telegramId")
    email = pick("email")
    if ident is None and email is None:
        return None
    if isinstance(ident, (dict, list)) or isinstance(email, (dict, list)):
        return None
    name = pick("name", "full_name", "fullName", "display_name", "displayName", "username")
    return {
        "id": str(ident)[:64] if ident is not None else "",
        "email": str(email)[:254].strip().lower() if email else "",
        "name": str(name)[:120] if isinstance(name, (str, int)) else "",
        "vip": bool(pick("is_vip", "isVip", "vip")),
        "paid": bool(pick("is_paid", "isPaid", "paid")),
        "plan": str(pick("plan") or "")[:40],
    }


def sign_in_method(path: str) -> str:
    for word, method in (("refresh", "refresh"), ("guest", "guest"), ("google", "google"), ("telegram", "telegram"),
                         ("link/coinepro", "link"), ("register/verify", "register")):
        if word in path:
            return method
    return "password"


def background(app: web.Application, job) -> None:
    """Work the response does not wait for, kept so it is not collected half-done."""
    task = asyncio.ensure_future(job)
    app[PENDING].add(task)
    task.add_done_callback(app[PENDING].discard)


async def settle(app: web.Application) -> None:
    """Waits for the background work in flight; the tests' way to see what a call set going."""
    while app[PENDING]:
        await asyncio.gather(*list(app[PENDING]), return_exceptions=True)


async def resolve_identity(app: web.Application, session: Session, backend: str) -> dict | None:
    """Asks the backend's `me` route who the session's latest bearer belongs to."""
    bearer = session.latest_bearer.get(backend)
    route = ME_ROUTE.get(backend)
    if not bearer or not route:
        return None
    config: Config = app[CONFIG]
    target, route_headers, tls = upstream_route(config, backend, route)
    headers = {"Authorization": "Bearer " + bearer, "Accept": "application/json", **route_headers}
    try:
        async with app[UPSTREAM].get(target, headers=headers, allow_redirects=False,
                                     timeout=aiohttp.ClientTimeout(total=8), **tls) as upstream:
            if upstream.status != 200:
                return None
            who = read_identity(await upstream.json(content_type=None))
    except (aiohttp.ClientError, asyncio.TimeoutError, ValueError):
        return None
    if who and who.get("id"):
        session.identity[backend] = who
        return who
    return None


async def signed_in(app: web.Application, session: Session, backend: str, method: str, hint: dict | None) -> None:
    who = await resolve_identity(app, session, backend)
    if who is None and hint and hint.get("id"):
        # The `me` route did not answer; the sign-in answer's own account will do.
        session.identity[backend] = who = hint
    if who is not None:
        emit(app, {"kind": "signin", "backend": backend, "method": method, **session_fields(session)})


def session_fields(session: Session | None) -> dict:
    if session is None:
        return {}
    return {
        "identity": [{"backend": backend, **who} for backend, who in sorted(session.identity.items(), key=_backend_order)],
        "sid": session.sid, "browser": session.browser, "os": session.os, "device": session.device,
        "country": session.country, "ip_hash": session.ip_hash,
    }


def client_fields(session: Session | None, request: web.Request, config: Config) -> dict:
    """The same description for a request that has no session yet (a refused sign-in)."""
    if session is not None:
        return session_fields(session)
    browser, system, device = agent_family(request.headers.get("User-Agent", ""))
    country = request.headers.get(config.country_header, "").strip().upper()
    address = reader_address(request)
    return {"identity": [], "sid": "", "browser": browser, "os": system, "device": device,
            "country": country if re.fullmatch(r"[A-Z]{2}", country) else "",
            "ip_hash": hashlib.sha256(f"{config.hash_salt}|ip|{address}".encode()).hexdigest()[:16] if address else ""}


def email_hash(body: bytes | None, key: str) -> str:
    """The address a refused sign-in named, keyed with the panel secret: the console can match it
    against its own accounts' addresses (admin.py `email_key`), and nobody without the secret can
    read it back. Without a secret nothing is sent at all (`emit` is off)."""
    try:
        email = str(json.loads(body or b"{}").get("email") or "").strip().lower()
    except (ValueError, AttributeError):
        return ""
    return hmac.new(key.encode(), email.encode(), hashlib.sha256).hexdigest()[:32] if email and key else ""


def _scalar(value):
    if isinstance(value, bool) or value is None:
        return value
    if isinstance(value, (int, float)):
        return value
    if isinstance(value, str):
        return value[:64]
    return None


async def trade_event(app: web.Application, session: Session | None, backend: str, method: str, path: str,
                      body: bytes | None, status: int, payload: bytes) -> None:
    if session is not None and backend not in session.identity and session.latest_bearer.get(backend):
        await resolve_identity(app, session, backend)
    try:
        sent = json.loads(body) if body else {}
    except ValueError:
        sent = {}
    fields = {key: _scalar(sent.get(key)) for key in TRADE_FIELDS if isinstance(sent, dict) and key in sent}
    fields = {key: value for key, value in fields.items() if value is not None or key in ("reduce_only",)}
    try:
        answer = json.loads(payload) if payload else {}
    except ValueError:
        answer = {}
    order_id, state, detail = "", "", ""
    if isinstance(answer, dict):
        inner = next((answer[k] for k in ("order", "execution", "position") if isinstance(answer.get(k), dict)), answer)
        for key in ("id", "order_id", "orderId", "execution_id"):
            if inner.get(key) not in (None, ""):
                order_id = str(inner[key])[:64]
                break
        state = str(inner.get("status") or "")[:32]
        if status >= 400:
            text = answer.get("detail")
            text = text.get("message") if isinstance(text, dict) else text
            detail = str(text or "")[:200]
    # `trade/orders`, `executions/7/close`, `venues/lbank`: the same words on both platforms.
    tail = path.split("/", 3)[-1]
    emit(app, {"kind": "trade", "backend": backend, "method": method, "path": tail.rstrip("/"), "status": status,
               "fields": fields, "order_id": order_id, "state": state, "detail": detail, **session_fields(session)})


# ── Telling the console ─────────────────────────────────────────────────────────────────────────


def hold_failure(app: web.Application, event: dict) -> None:
    """A refused sign-in is reported after `probe_grace`, unless `drop_probe` takes it back first."""
    if not app[CONFIG].panel_enabled:
        return
    event.setdefault("at", time.time())
    key = (event.get("ip_hash", ""), event.get("email_hash", ""))

    async def later() -> None:
        await asyncio.sleep(app[CONFIG].probe_grace)
        emit(app, event)

    task = asyncio.ensure_future(later())
    held = app[PROBES].setdefault(key, [])
    held.append((event.get("backend"), task))

    def done(_: asyncio.Future) -> None:
        left = [item for item in app[PROBES].get(key, []) if item[1] is not task]
        if left:
            app[PROBES][key] = left
        else:
            app[PROBES].pop(key, None)

    task.add_done_callback(done)


def drop_probe(app: web.Application, backend: str, mail: str, ip: str) -> None:
    """A sign-in into one platform takes back the other platform's refusal of the same e-mail from the
    same address (the page asked both). A refusal on the same platform stays: that was a wrong password."""
    if not mail or not ip:
        return
    for other, task in list(app[PROBES].get((ip, mail), [])):
        if other != backend:
            task.cancel()
            break


def emit(app: web.Application, event: dict) -> None:
    """Queues one event for the console, or drops it: an audit line never holds a reader's call up."""
    if not app[CONFIG].panel_enabled or EVENTS not in app:
        return
    event.setdefault("at", time.time())
    try:
        app[EVENTS].put_nowait(event)
    except asyncio.QueueFull:
        pass


async def _send_events(app: web.Application) -> None:
    config: Config = app[CONFIG]
    queue: asyncio.Queue = app[EVENTS]
    while True:
        batch = [await queue.get()]
        try:
            deadline = time.monotonic() + 0.5
            while len(batch) < config.events_batch:
                left = deadline - time.monotonic()
                if left <= 0:
                    break
                batch.append(await asyncio.wait_for(queue.get(), left))
        except asyncio.TimeoutError:
            pass
        try:
            async with app[UPSTREAM].post(config.panel_url + "/events", json={"events": batch},
                                          headers={"X-Panel-Token": config.panel_token},
                                          timeout=aiohttp.ClientTimeout(total=10)) as response:
                if response.status >= 300:
                    print(f"panel events refused: {response.status}", file=sys.stderr)
        except (aiohttp.ClientError, asyncio.TimeoutError, OSError) as error:
            print(f"panel events not delivered: {type(error).__name__}", file=sys.stderr)


# ── The web user panel ──────────────────────────────────────────────────────────────────────────

PANEL_PATH = re.compile(r"^[A-Za-z0-9_\-./]{1,200}$")
RETURNED_PANEL_HEADERS = ("Content-Type", "Content-Disposition", "Retry-After")


def _panel_session(request: web.Request) -> tuple[str | None, Session | None]:
    config: Config = request.app[CONFIG]
    key = request.cookies.get(config.cookie_name)
    session = request.app[SESSIONS].get(key)
    if session is not None:
        note_client(config, session, request)
    return key, session


def _holds_handle(request: web.Request, session: Session) -> bool:
    """The page still holds one of this session's handles: it has not signed out."""
    bearer = request.headers.get("Authorization", "")
    handle = bearer[len("Bearer "):] if bearer.startswith("Bearer " + HANDLE_PREFIX) else ""
    return bool(handle) and handle in session.tokens


async def _verified(request: web.Request) -> tuple[str | None, Session | None, web.Response | None]:
    if _limited(request, False):
        return None, None, web.json_response({"error": "too_many_requests"}, status=429, headers={"Retry-After": "60"})
    key, session = _panel_session(request)
    if session is None or not _holds_handle(request, session):
        return None, None, web.json_response({"error": "signed_out"}, status=401)
    if not session.identity:
        for backend in list(session.latest_bearer):
            await resolve_identity(request.app, session, backend)
    if not session.accounts():
        return None, None, web.json_response({"error": "no_identity"}, status=401)
    return key, session, None


def describe(session: Session, current: bool = False) -> dict:
    return {"sid": session.sid, "created": int(session.created), "last_seen": int(session.touched),
            "browser": session.browser, "os": session.os, "device": session.device, "country": session.country,
            "accounts": session.accounts(), "current": current}


def _related(sessions: Sessions, session: Session) -> list[tuple[str, Session]]:
    mine = set(session.accounts())
    return [(key, other) for key, other in sessions.by_id.items() if mine & set(other.accounts())]


async def panel_sessions(request: web.Request) -> web.Response:
    key, session, refusal = await _verified(request)
    if refusal is not None:
        return refusal
    rows = [describe(other, other is session) for _, other in _related(request.app[SESSIONS], session)]
    rows.sort(key=lambda row: (not row["current"], -row["last_seen"]))
    return web.json_response({"sessions": rows}, headers={"Cache-Control": "no-store"})


async def panel_revoke(request: web.Request) -> web.Response:
    """{sid} ends one of the reader's sessions; {others: true} every other; {all: true} every one."""
    config: Config = request.app[CONFIG]
    key, session, refusal = await _verified(request)
    if refusal is not None:
        return refusal
    try:
        body = await request.json()
    except (ValueError, aiohttp.ContentTypeError):
        body = {}
    body = body if isinstance(body, dict) else {}
    sessions: Sessions = request.app[SESSIONS]
    related = _related(sessions, session)
    if body.get("all"):
        chosen = related
    elif body.get("others"):
        chosen = [(k, s) for k, s in related if s is not session]
    else:
        sid = str(body.get("sid") or "")
        chosen = [(k, s) for k, s in related if sid and hmac.compare_digest(s.sid, sid)]
        if not chosen:
            return web.json_response({"error": "unknown_session"}, status=404)
    for other_key, other in chosen:
        emit(request.app, {"kind": "session_revoked", "by": "reader", "target": other.sid, **session_fields(other)})
        sessions.drop(other_key)
    response = web.json_response({"revoked": len(chosen)}, headers={"Cache-Control": "no-store"})
    if any(other is session for _, other in chosen):
        response.del_cookie(config.cookie_name, path="/up/")
    return response


async def panel_signout(request: web.Request) -> web.Response:
    """This browser's sign-out: the page forgets its handles, and the relay forgets the session."""
    config: Config = request.app[CONFIG]
    key, session = _panel_session(request)
    if session is not None and key:
        if session.identity:
            emit(request.app, {"kind": "signout", "how": "page", **session_fields(session)})
        request.app[SESSIONS].drop(key)
    response = web.json_response({"ok": True}, headers={"Cache-Control": "no-store"})
    response.del_cookie(config.cookie_name, path="/up/")
    return response


def identity_header(session: Session) -> str:
    fields = session_fields(session)
    return base64.urlsafe_b64encode(json.dumps(fields, separators=(",", ":")).encode()).decode()


async def panel_forward(request: web.Request) -> web.StreamResponse:
    config: Config = request.app[CONFIG]
    path = request.match_info["path"]
    if not PANEL_PATH.match(path) or ".." in path.split("/"):
        return web.json_response({"error": "bad_path"}, status=400)
    if not config.panel_enabled:
        return web.json_response({"error": "panel_off"}, status=503)
    key, session, refusal = await _verified(request)
    if refusal is not None:
        return refusal
    body = await request.read() if request.can_read_body else None
    if body and len(body) > config.panel_body_limit:
        return web.json_response({"error": "too_large"}, status=413)
    headers = {"X-Panel-Token": config.panel_token, "X-Panel-Identity": identity_header(session),
               "Accept": request.headers.get("Accept", "application/json")}
    if "Content-Type" in request.headers:
        headers["Content-Type"] = request.headers["Content-Type"]
    target = config.panel_url + "/" + path + (("?" + request.query_string) if request.query_string else "")
    try:
        async with request.app[UPSTREAM].request(request.method, target, headers=headers, data=body,
                                                   allow_redirects=False,
                                                   timeout=aiohttp.ClientTimeout(total=30)) as upstream:
            payload = await upstream.read()
            returned = {name: upstream.headers[name] for name in RETURNED_PANEL_HEADERS if name in upstream.headers}
            status = upstream.status
    except (aiohttp.ClientError, asyncio.TimeoutError):
        return web.json_response({"error": "panel_unreachable"}, status=502)
    response = web.Response(body=payload, status=status, headers=returned)
    response.headers["Cache-Control"] = "no-store"
    response.headers["X-Content-Type-Options"] = "nosniff"
    return response


# ── The console's calls ─────────────────────────────────────────────────────────────────────────


def _internal(request: web.Request) -> bool:
    config: Config = request.app[CONFIG]
    sent = request.headers.get("X-Panel-Token", "")
    return config.panel_enabled and bool(sent) and hmac.compare_digest(sent.encode(), config.panel_token.encode())


async def internal_sessions(request: web.Request) -> web.Response:
    """Every live session (or one account's): the console's «online now», devices and revoke list."""
    if not _internal(request):
        return web.json_response({"error": "not_found"}, status=404)
    account = request.query.get("account", "")
    sessions: Sessions = request.app[SESSIONS]
    live = sessions.live()
    rows = [describe(s) for s in live if s.accounts() and (not account or account in s.accounts())]
    guests = sum(1 for s in live if not s.accounts())
    return web.json_response({"sessions": rows, "guests": guests, "now": int(time.time())})


async def internal_revoke(request: web.Request) -> web.Response:
    """{account, sid?, operator}: one session of that account, or all of them."""
    if not _internal(request):
        return web.json_response({"error": "not_found"}, status=404)
    try:
        body = await request.json()
    except (ValueError, aiohttp.ContentTypeError):
        return web.json_response({"error": "invalid_json"}, status=400)
    account = str(body.get("account") or "")
    sid = str(body.get("sid") or "")
    if not account:
        return web.json_response({"error": "account"}, status=400)
    sessions: Sessions = request.app[SESSIONS]
    chosen = [(k, s) for k, s in sessions.of_account(account) if not sid or hmac.compare_digest(s.sid, sid)]
    for key, session in chosen:
        emit(request.app, {"kind": "session_revoked", "by": "owner", "operator": str(body.get("operator") or "")[:64],
                           "target": session.sid, **session_fields(session)})
        sessions.drop(key)
    return web.json_response({"revoked": len(chosen)})


def main() -> None:
    web.run_app(build_app(), host=os.environ.get("RELAY_HOST", "127.0.0.1"), port=int(os.environ.get("RELAY_PORT", "8790")))


if __name__ == "__main__":
    main()
