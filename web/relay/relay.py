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

One process, state in memory: a restart signs browsers out, exactly as clearing the app's data does
on the phone. Standard library and aiohttp only. Run `python3 relay.py`, or the Dockerfile beside
it; `README.md` has the Caddy lines that put it under pro-chart.com.
"""
from __future__ import annotations

import asyncio
import ipaddress
import json
import os
import re
import secrets
import socket
import time
from dataclasses import dataclass, field
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


# The request headers the phone sends (AuthInterceptor, the install-id and version interceptors, the
# community key), and nothing else. Never the reader's Cookie: that is this relay's, not theirs.
FORWARDED_REQUEST_HEADERS = (
    "Authorization", "Content-Type", "Accept", "Accept-Language", "X-Install-Id", "X-App-Platform",
    "X-App-Version", "X-Play-Integrity", "X-Play-Integrity-Nonce", "X-Community-Key", "X-Client-Id",
)
RETURNED_RESPONSE_HEADERS = ("Content-Type", "Retry-After", "X-Request-Id")

# Where each backend hands out tokens (`AuthPaths` in core/auth, `EndpointCatalog` in
# core/diagnostics): sign-in, sign-up verification, Google, Telegram, guest, refresh.
AUTH_ISSUING = re.compile(
    r"^(api/mobile/v1/auth/(login|refresh|register/verify|google)"
    r"|api/user/auth/(login|refresh|register/verify|google|telegram|guest))/?$"
)
AUTH_LOGOUT = re.compile(r"^(api/mobile/v1/auth/logout|api/user/auth/logout)/?$")
AUTH_ANY = re.compile(r"^api/(mobile/v1|user)/auth/")
TOKEN_KEYS = ("access_token", "accessToken", "refresh_token", "refreshToken", "token")
HANDLE_PREFIX = "pch_"

IMAGE_TYPES = ("image/jpeg", "image/png", "image/webp", "image/gif", "image/avif")


# ── Sessions: handle ↔ token, per browser ───────────────────────────────────────────────────────


@dataclass
class Session:
    touched: float
    tokens: dict[str, str] = field(default_factory=dict)          # handle → real token
    latest_bearer: dict[str, str] = field(default_factory=dict)   # backend → real access token


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
        session = Session(touched=time.time())
        self.by_id[session_id] = session
        return session_id, session

    def sweep(self) -> None:
        now = time.time()
        for key in [k for k, s in self.by_id.items() if now - s.touched > self.ttl]:
            del self.by_id[key]


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


def build_app(config: Config | None = None) -> web.Application:
    config = config or Config()
    app = web.Application(client_max_size=config.body_limit)
    app[CONFIG] = config
    app[SESSIONS] = Sessions(config.session_days * 86400)
    app[BUCKETS] = Buckets()
    app.router.add_route("*", "/up/{backend}/{path:.*}", passthrough)
    app.router.add_get("/api/img", image)
    app.router.add_get("/relay/health", health)
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

    async def sweep() -> None:
        while True:
            await asyncio.sleep(600)
            app[SESSIONS].sweep()

    app[SWEEPER] = asyncio.create_task(sweep())


async def _close_clients(app: web.Application) -> None:
    app[SWEEPER].cancel()
    await app[UPSTREAM].close()
    await app[IMAGES].close()


def _limited(request: web.Request, auth: bool) -> bool:
    config: Config = request.app[CONFIG]
    address, client = client_key(request)
    per_minute = config.auth_requests_per_minute if auth else config.requests_per_minute
    return not request.app[BUCKETS].allow((address, client, auth), per_minute)


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
    target = origin + path + (("?" + request.query_string) if request.query_string else "")

    headers = {name: request.headers[name] for name in FORWARDED_REQUEST_HEADERS if name in request.headers}
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
        return await _socket(request, target, headers)

    body = await request.read() if request.can_read_body else None
    if body and auth_route and "json" in headers.get("Content-Type", ""):
        try:
            body = json.dumps(swap_in(json.loads(body), session)).encode()
        except ValueError:
            pass

    try:
        async with request.app[UPSTREAM].request(request.method, target, headers=headers, data=body,
                                                   allow_redirects=False) as upstream:
            payload = await upstream.read()
            status = upstream.status
            returned = {name: upstream.headers[name] for name in RETURNED_RESPONSE_HEADERS if name in upstream.headers}
    except (aiohttp.ClientError, asyncio.TimeoutError):
        return web.json_response({"detail": "upstream unreachable"}, status=502)

    response_cookie = None
    if AUTH_ISSUING.match(path) and 200 <= status < 300 and "json" in returned.get("Content-Type", ""):
        try:
            answer = json.loads(payload)
        except ValueError:
            answer = None
        if answer is not None:
            if session is None:
                session_id, session = sessions.create()
                response_cookie = session_id
            payload = json.dumps(swap_out(answer, session, backend)).encode()
    if AUTH_LOGOUT.match(path) and session is not None:
        session.latest_bearer.pop(backend, None)

    response = web.Response(body=payload, status=status, headers=returned)
    response.headers["Cache-Control"] = "no-store"
    if response_cookie:
        response.set_cookie(config.cookie_name, response_cookie, max_age=config.session_days * 86400,
                            path="/up/", httponly=True, secure=config.cookie_secure, samesite="Strict")
    return response


async def _socket(request: web.Request, target: str, headers: dict[str, str]) -> web.StreamResponse:
    upstream_url = target.replace("https://", "wss://", 1).replace("http://", "ws://", 1)
    headers.pop("Content-Type", None)
    try:
        upstream = await request.app[UPSTREAM].ws_connect(upstream_url, headers=headers, heartbeat=30)
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


def main() -> None:
    web.run_app(build_app(), host=os.environ.get("RELAY_HOST", "127.0.0.1"), port=int(os.environ.get("RELAY_PORT", "8790")))


if __name__ == "__main__":
    main()
