"""The relay against a fake of each backend and a fake publisher: `python3 -m unittest test_relay`."""
from __future__ import annotations

import asyncio
import base64
import hashlib
import hmac
import json
import os
import unittest

from aiohttp import WSMsgType, web
from aiohttp.test_utils import AioHTTPTestCase, TestServer
from yarl import URL

# The deployed container sets RELAY_*_CONNECT to the backends' private addresses, and `Config` reads
# its defaults from the environment — some when `relay` is imported. Run there, the tests reached
# the real backends instead of the fakes and failed with 502. They describe the relay, not the host.
for _name in [name for name in os.environ if name.startswith("RELAY_")]:
    del os.environ[_name]

import relay  # noqa: E402

REAL_ACCESS = "real-access-token"
REAL_REFRESH = "real-refresh-token"
PNG = b"\x89PNG\r\n\x1a\n" + b"0" * 64


def fake_backend() -> web.Application:
    async def login(request: web.Request) -> web.Response:
        return web.json_response({"access_token": REAL_ACCESS, "refresh_token": REAL_REFRESH, "user": {"id": 7}})

    async def refresh(request: web.Request) -> web.Response:
        body = await request.json()
        if body.get("refresh_token") != REAL_REFRESH:
            return web.json_response({"detail": "bad refresh"}, status=401)
        return web.json_response({"access_token": REAL_ACCESS + "-2", "refresh_token": REAL_REFRESH})

    async def echo(request: web.Request) -> web.Response:
        return web.json_response({
            "method": request.method,
            "query": request.query_string,
            "headers": {k: v for k, v in request.headers.items()},
            "body": (await request.read()).decode(),
        }, headers={"X-Request-Id": "r1", "Set-Cookie": "backend=1"})

    async def socket(request: web.Request) -> web.WebSocketResponse:
        ws = web.WebSocketResponse()
        await ws.prepare(request)
        await ws.send_str(json.dumps({"auth": request.headers.get("Authorization")}))
        async for message in ws:
            if message.type == WSMsgType.TEXT:
                await ws.send_str("echo:" + message.data)
        return ws

    async def publisher(request: web.Request) -> web.Response:
        kind = request.match_info["kind"]
        if kind == "photo.png":
            return web.Response(body=PNG, content_type="image/png")
        if kind == "untyped":
            return web.Response(body=b"RIFF\x10\x00\x00\x00WEBPVP8 " + b"0" * 32, headers={"Content-Type": ""})
        if kind == "html-as-octet":
            return web.Response(body=b"<html><script>", content_type="application/octet-stream")
        if kind == "page.html":
            return web.Response(text="<html>", content_type="text/html")
        if kind == "huge.png":
            return web.Response(body=b"0" * (6 * 1024 * 1024), content_type="image/png")
        if kind == "moved":
            raise web.HTTPFound("/img/photo.png")
        return web.Response(status=404)

    app = web.Application()
    app.router.add_post("/api/mobile/v1/auth/login", login)
    app.router.add_post("/api/mobile/v1/auth/refresh", refresh)

    async def link(request: web.Request) -> web.Response:
        body = await request.json()
        return web.json_response({"access_token": REAL_ACCESS, "refresh_token": REAL_REFRESH, "seen": body.get("coinepro_token")})

    app.router.add_post("/api/mobile/v1/auth/link/coinepro", link)
    app.router.add_route("*", "/api/echo", echo)
    app.router.add_get("/api/ws/prices", socket)
    app.router.add_get("/img/{kind}", publisher)
    return app


class RelayTest(AioHTTPTestCase):
    async def get_application(self) -> web.Application:
        self.upstream = TestServer(fake_backend())
        await self.upstream.start_server()
        base = str(self.upstream.make_url("/"))
        config = relay.Config(backends={"tradeyar": base, "coineprofx": base}, cookie_secure=False,
                              image_allow_private=True, image_allow_http=True)
        return relay.build_app(config)

    async def asyncTearDown(self) -> None:
        await super().asyncTearDown()
        await self.upstream.close()

    async def sign_in(self) -> dict:
        response = await self.client.post("/up/tradeyar/api/mobile/v1/auth/login", json={"email": "a@b.c", "password": "x"})
        self.assertEqual(response.status, 200)
        return await response.json()

    async def test_sign_in_answer_carries_handles_not_tokens(self) -> None:
        answer = await self.sign_in()
        self.assertTrue(answer["access_token"].startswith(relay.HANDLE_PREFIX))
        self.assertTrue(answer["refresh_token"].startswith(relay.HANDLE_PREFIX))
        self.assertNotIn(REAL_ACCESS, json.dumps(answer))
        self.assertEqual(answer["user"], {"id": 7})
        self.assertIn("pc_session", {c.key for c in self.client.session.cookie_jar})

    async def test_handle_becomes_the_bearer_upstream(self) -> None:
        answer = await self.sign_in()
        response = await self.client.get("/up/tradeyar/api/echo?a=1&b=2",
                                          headers={"Authorization": "Bearer " + answer["access_token"], "X-Install-Id": "i1",
                                                   "X-Evil": "no"})
        seen = await response.json()
        self.assertEqual(seen["headers"]["Authorization"], "Bearer " + REAL_ACCESS)
        self.assertEqual(seen["headers"]["X-Install-Id"], "i1")
        self.assertNotIn("X-Evil", seen["headers"])
        self.assertNotIn("Cookie", seen["headers"])
        self.assertEqual(seen["query"], "a=1&b=2")
        self.assertEqual(response.headers["X-Request-Id"], "r1")
        self.assertNotIn("Set-Cookie", response.headers)

    async def test_handle_from_another_browser_is_dropped(self) -> None:
        answer = await self.sign_in()
        self.client.session.cookie_jar.clear()
        response = await self.client.get("/up/tradeyar/api/echo", headers={"Authorization": "Bearer " + answer["access_token"]})
        seen = await response.json()
        self.assertNotIn("Authorization", seen["headers"])

    async def test_refresh_body_handle_is_swapped_back(self) -> None:
        answer = await self.sign_in()
        response = await self.client.post("/up/tradeyar/api/mobile/v1/auth/refresh", json={"refresh_token": answer["refresh_token"]})
        self.assertEqual(response.status, 200)
        fresh = await response.json()
        self.assertTrue(fresh["access_token"].startswith(relay.HANDLE_PREFIX))
        seen = await (await self.client.get("/up/tradeyar/api/echo", headers={"Authorization": "Bearer " + fresh["access_token"]})).json()
        self.assertEqual(seen["headers"]["Authorization"], "Bearer " + REAL_ACCESS + "-2")

    async def test_the_account_link_takes_a_handle_and_gives_handles_back(self) -> None:
        # The forex handle goes in as the real token; the TradeYar tokens come back as handles.
        signed_in = await self.sign_in()
        answer = await (await self.client.post(
            "/up/tradeyar/api/mobile/v1/auth/link/coinepro", json={"coinepro_token": signed_in["access_token"]},
        )).json()
        self.assertEqual(answer["seen"], REAL_ACCESS)
        self.assertTrue(answer["access_token"].startswith(relay.HANDLE_PREFIX))
        self.assertTrue(answer["refresh_token"].startswith(relay.HANDLE_PREFIX))

    async def test_the_reader_address_reaches_the_backend(self) -> None:
        # Our nginx appends the reader on the right; the left-most entry is the browser's own claim.
        seen = await (await self.client.get(
            "/up/tradeyar/api/echo", headers={"X-Forwarded-For": "6.6.6.6, 203.0.113.9"},
        )).json()
        self.assertEqual(seen["headers"]["X-Forwarded-For"], "203.0.113.9")
        self.assertEqual(seen["headers"]["X-Real-IP"], "203.0.113.9")

    async def test_post_body_and_method_pass_unchanged(self) -> None:
        seen = await (await self.client.patch("/up/coineprofx/api/echo", data=b'{"x":1}', headers={"Content-Type": "application/json"})).json()
        self.assertEqual(seen["method"], "PATCH")
        self.assertEqual(seen["body"], '{"x":1}')

    async def test_unknown_backend_and_dot_dot(self) -> None:
        self.assertEqual((await self.client.get("/up/elsewhere/api/echo")).status, 404)
        self.assertEqual((await self.client.get("/up/tradeyar/api/../secret")).status in (400, 404), True)

    async def test_socket_gets_the_bearer_from_the_cookie(self) -> None:
        await self.sign_in()
        async with self.client.ws_connect("/up/tradeyar/api/ws/prices?symbols=BTCUSDT") as ws:
            first = json.loads((await ws.receive()).data)
            self.assertEqual(first["auth"], "Bearer " + REAL_ACCESS)
            await ws.send_str("hi")
            self.assertEqual((await ws.receive()).data, "echo:hi")

    async def test_image_is_relayed_with_cache_header(self) -> None:
        url = str(self.upstream.make_url("/img/photo.png"))
        response = await self.client.get("/api/img", params={"url": url})
        self.assertEqual(response.status, 200)
        self.assertEqual(await response.read(), PNG)
        self.assertIn("max-age", response.headers["Cache-Control"])

    async def test_image_follows_a_redirect(self) -> None:
        response = await self.client.get("/api/img", params={"url": str(self.upstream.make_url("/img/moved"))})
        self.assertEqual(response.status, 200)

    async def test_untyped_photo_is_judged_by_its_bytes(self) -> None:
        response = await self.client.get("/api/img", params={"url": str(self.upstream.make_url("/img/untyped"))})
        self.assertEqual(response.status, 200)
        self.assertEqual(response.headers["Content-Type"], "image/webp")
        response = await self.client.get("/api/img", params={"url": str(self.upstream.make_url("/img/html-as-octet"))})
        self.assertEqual(response.status, 415)

    async def test_image_refuses_non_images_and_oversize(self) -> None:
        self.assertEqual((await self.client.get("/api/img", params={"url": str(self.upstream.make_url("/img/page.html"))})).status, 415)
        self.assertEqual((await self.client.get("/api/img", params={"url": str(self.upstream.make_url("/img/huge.png"))})).status, 413)


class PrivateNetworkTest(AioHTTPTestCase):
    """RELAY_*_CONNECT: open the private address, keep the public name in Host (and in TLS)."""

    async def get_application(self) -> web.Application:
        self.upstream = TestServer(fake_backend())
        await self.upstream.start_server()
        config = relay.Config(backends={"tradeyar": "https://tradeyar.example/", "coineprofx": "https://fx.example/"},
                              connect={"tradeyar": str(self.upstream.make_url("/"))}, cookie_secure=False)
        return relay.build_app(config)

    async def asyncTearDown(self) -> None:
        await super().asyncTearDown()
        await self.upstream.close()

    async def test_connects_privately_and_names_the_public_host(self) -> None:
        seen = await (await self.client.get("/up/tradeyar/api/echo")).json()
        self.assertEqual(seen["headers"]["Host"], "tradeyar.example")

    def test_route_keeps_the_public_name_for_tls(self) -> None:
        config = relay.Config(backends={"tradeyar": "https://tradeyar.example/"}, connect={"tradeyar": "https://10.0.0.3/"})
        url, headers, tls = relay.upstream_route(config, "tradeyar", "api/x?a=1")
        self.assertEqual(url, "https://10.0.0.3/api/x?a=1")
        self.assertEqual(headers, {"Host": "tradeyar.example"})
        self.assertEqual(tls, {"server_hostname": "tradeyar.example"})


class LimitTest(AioHTTPTestCase):
    async def get_application(self) -> web.Application:
        return relay.build_app(relay.Config(backends={"tradeyar": "http://127.0.0.1:9/"}, auth_requests_per_minute=100,
                                            auth_address_requests_per_minute=3))

    async def test_rotating_client_ids_does_not_escape_the_address_ceiling(self) -> None:
        statuses = []
        for n in range(5):
            response = await self.client.post("/up/tradeyar/api/mobile/v1/auth/login", json={}, headers={"X-Client-Id": f"c{n}"})
            statuses.append(response.status)
        self.assertEqual(statuses[-1], 429)
        self.assertNotIn(429, statuses[:3])


PANEL_TOKEN = "p" * 40
CHROME_PHONE = ("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/126.0 Mobile Safari/537.36")
FIREFOX_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"


def fake_accounts() -> web.Application:
    """Both backends' account routes: sign-in, `me`, refresh, guest, and the trading calls."""

    async def login(request: web.Request) -> web.Response:
        body = await request.json()
        if body.get("password") == "wrong":
            return web.json_response({"detail": "ایمیل یا رمز عبور درست نیست."}, status=401)
        return web.json_response({"access_token": REAL_ACCESS, "refresh_token": REAL_REFRESH, "user": {"id": 7}})

    async def refresh(request: web.Request) -> web.Response:
        body = await request.json()
        if body.get("refresh_token") != REAL_REFRESH:
            return web.json_response({"detail": "bad refresh"}, status=401)
        return web.json_response({"access_token": REAL_ACCESS + "-2", "refresh_token": REAL_REFRESH})

    async def me_tradeyar(request: web.Request) -> web.Response:
        if not request.headers.get("Authorization", "").startswith("Bearer " + REAL_ACCESS):
            return web.json_response({"detail": "unauthorised"}, status=401)
        return web.json_response({"user": {"user_id": 7, "email": "Sara@Example.com", "full_name": "سارا",
                                           "is_vip": True, "plan": "pro"}})

    async def me_forex(request: web.Request) -> web.Response:
        if request.headers.get("Authorization") != "Bearer fx-access":
            return web.json_response({"detail": "unauthorised"}, status=401)
        return web.json_response({"id": 45, "email": "sara@example.com", "name": "Sara FX"})

    async def fx_login(request: web.Request) -> web.Response:
        return web.json_response({"access_token": "fx-access", "refresh_token": "fx-refresh"})

    async def guest(request: web.Request) -> web.Response:
        return web.json_response({"token": "guest-token", "scope": "guest"})

    async def order(request: web.Request) -> web.Response:
        await request.read()
        return web.json_response({"order": {"id": 991, "status": "submitted"}})

    async def refused_order(request: web.Request) -> web.Response:
        return web.json_response({"detail": {"code": "margin", "message": "margin too low"}}, status=422)

    async def venue(request: web.Request) -> web.Response:
        return web.json_response({"configured": True, "key_hint": "abcd…wxyz"})

    async def echo(request: web.Request) -> web.Response:
        return web.json_response({"headers": dict(request.headers)})

    app = web.Application()
    app.router.add_post("/api/mobile/v1/auth/login", login)
    app.router.add_post("/api/mobile/v1/auth/refresh", refresh)
    app.router.add_get("/api/mobile/v1/me", me_tradeyar)
    app.router.add_post("/api/user/auth/login", fx_login)
    app.router.add_post("/api/user/auth/guest", guest)
    app.router.add_get("/api/user/me", me_forex)
    app.router.add_post("/api/mobile/v1/trade/orders", order)
    app.router.add_post("/api/user/mobile/trade/positions/close", refused_order)
    app.router.add_get("/api/mobile/v1/trade/orders", order)
    app.router.add_post("/api/mobile/v1/venues/lbank", venue)
    app.router.add_get("/api/echo", echo)
    return app


def fake_console(seen: dict) -> web.Application:
    """The owner console's internal panel routes: records what reached them."""

    async def events(request: web.Request) -> web.Response:
        if request.headers.get("X-Panel-Token") != PANEL_TOKEN:
            return web.json_response({"error": "token"}, status=401)
        seen["events"].extend((await request.json())["events"])
        return web.json_response({"ok": True})

    async def anything(request: web.Request) -> web.Response:
        body = await request.read()
        seen["calls"].append({"method": request.method, "path": request.path, "query": request.query_string,
                              "headers": dict(request.headers), "body": body.decode()})
        if request.path.endswith("/export"):
            return web.Response(body=b'{"all":1}', content_type="application/json",
                                headers={"Content-Disposition": 'attachment; filename="x.json"', "Set-Cookie": "c=1"})
        return web.json_response({"ok": True, "path": request.path}, status=201 if request.method == "POST" else 200)

    app = web.Application(client_max_size=8 * 1024 * 1024)
    app.router.add_post("/internal/panel/events", events)
    app.router.add_route("*", "/internal/panel/{tail:.*}", anything)
    return app


class PanelTest(AioHTTPTestCase):
    """The web user panel: who the browser is, what reaches the console, and nothing more."""

    async def get_application(self) -> web.Application:
        self.upstream = TestServer(fake_accounts())
        await self.upstream.start_server()
        self.seen = {"events": [], "calls": []}
        self.console = TestServer(fake_console(self.seen))
        await self.console.start_server()
        base = str(self.upstream.make_url("/"))
        self.config = relay.Config(backends={"tradeyar": base, "coineprofx": base}, cookie_secure=False,
                                   panel_url=str(self.console.make_url("/internal/panel")), panel_token=PANEL_TOKEN,
                                   hash_salt="salt", probe_grace=0.3)
        return relay.build_app(self.config)

    async def asyncTearDown(self) -> None:
        await super().asyncTearDown()
        await self.upstream.close()
        await self.console.close()

    async def sign_in(self, agent: str = CHROME_PHONE, password: str = "x") -> dict:
        response = await self.client.post("/up/tradeyar/api/mobile/v1/auth/login",
                                          json={"email": "sara@example.com", "password": password},
                                          headers={"User-Agent": agent, "CF-IPCountry": "ir",
                                                   "X-Forwarded-For": "6.6.6.6, 203.0.113.9"})
        answer = await response.json()
        await relay.settle(self.app)
        return answer

    async def events(self, kind: str, count: int = 1) -> list[dict]:
        await relay.settle(self.app)
        for _ in range(60):
            found = [e for e in self.seen["events"] if e["kind"] == kind]
            if len(found) >= count:
                return found
            await asyncio.sleep(0.05)
        self.fail(f"no {kind} event: {[e['kind'] for e in self.seen['events']]}")

    def panel_headers(self, handle: str) -> dict:
        return {"Authorization": "Bearer " + handle}

    def session(self) -> relay.Session:
        return next(iter(self.app[relay.SESSIONS].by_id.values()))

    async def test_sign_in_learns_who_the_browser_is(self) -> None:
        await self.sign_in()
        session = self.session()
        self.assertEqual(session.identity["tradeyar"]["id"], "7")
        self.assertEqual(session.identity["tradeyar"]["email"], "sara@example.com")
        self.assertTrue(session.identity["tradeyar"]["vip"])
        self.assertEqual((session.browser, session.os, session.device, session.country), ("Chrome", "Android", "phone", "IR"))
        # The address is hashed, never kept.
        self.assertNotIn("203.0.113.9", repr(session))
        self.assertEqual(len(session.ip_hash), 16)
        event = (await self.events("signin"))[0]
        self.assertEqual((event["backend"], event["method"], event["identity"][0]["id"]), ("tradeyar", "password", "7"))
        self.assertNotIn(REAL_ACCESS, json.dumps(self.seen["events"]))

    async def test_a_guest_token_is_not_a_sign_in(self) -> None:
        await self.client.post("/up/coineprofx/api/user/auth/guest", json={})
        await relay.settle(self.app)
        self.assertEqual(self.session().identity, {})
        await asyncio.sleep(0.6)
        self.assertEqual([e for e in self.seen["events"] if e["kind"] == "signin"], [])

    async def test_panel_calls_carry_the_verified_identity_and_the_secret(self) -> None:
        answer = await self.sign_in()
        response = await self.client.post("/up/panel/tickets?x=1", json={"subject": "hi"},
                                          headers={**self.panel_headers(answer["access_token"]), "X-Panel-Identity": "forged"})
        self.assertEqual(response.status, 201)
        call = self.seen["calls"][-1]
        self.assertEqual((call["method"], call["path"], call["query"]), ("POST", "/internal/panel/tickets", "x=1"))
        self.assertEqual(call["headers"]["X-Panel-Token"], PANEL_TOKEN)
        identity = json.loads(base64.urlsafe_b64decode(call["headers"]["X-Panel-Identity"]))
        self.assertEqual(identity["identity"][0]["id"], "7")
        self.assertEqual(identity["sid"], self.session().sid)
        self.assertNotIn("Authorization", call["headers"])
        self.assertNotIn("Cookie", call["headers"])
        self.assertNotIn(REAL_ACCESS, json.dumps(call))
        self.assertEqual(json.loads(call["body"]), {"subject": "hi"})
        exported = await self.client.get("/up/panel/export", headers=self.panel_headers(answer["access_token"]))
        self.assertIn("attachment", exported.headers["Content-Disposition"])
        self.assertNotIn("Set-Cookie", exported.headers)

    async def test_panel_needs_the_cookie_and_a_handle_of_that_browser(self) -> None:
        answer = await self.sign_in()
        self.assertEqual((await self.client.get("/up/panel/me")).status, 401)
        self.assertEqual((await self.client.get("/up/panel/me", headers=self.panel_headers("pch_someone-else"))).status, 401)
        self.client.session.cookie_jar.clear()
        self.assertEqual((await self.client.get("/up/panel/me", headers=self.panel_headers(answer["access_token"]))).status, 401)
        self.assertEqual(self.seen["calls"], [])

    async def test_panel_refuses_bad_paths_and_oversized_bodies(self) -> None:
        answer = await self.sign_in()
        headers = self.panel_headers(answer["access_token"])
        dotted = await self.client.get(URL("/up/panel/a/%2e%2e/b", encoded=True), headers=headers)
        self.assertEqual(dotted.status, 400)
        self.assertEqual((await self.client.get("/up/panel/a%20b", headers=headers)).status, 400)
        big = await self.client.put("/up/panel/workspace/drawings", data=b"x" * (2 * 1024 * 1024 + 1),
                                    headers={**headers, "Content-Type": "application/json"})
        self.assertEqual(big.status, 413)

    async def test_sessions_list_and_revoke(self) -> None:
        first = await self.sign_in(FIREFOX_DESKTOP)
        first_cookie = {c.key: c.value for c in self.client.session.cookie_jar}
        self.client.session.cookie_jar.clear()
        second = await self.sign_in(CHROME_PHONE)
        listing = await (await self.client.get("/up/panel/sessions", headers=self.panel_headers(second["access_token"]))).json()
        rows = listing["sessions"]
        self.assertEqual(len(rows), 2)
        self.assertTrue(rows[0]["current"])
        self.assertEqual(rows[1]["browser"], "Firefox")
        self.assertEqual(rows[1]["accounts"], ["tradeyar:7"])
        unknown = await self.client.post("/up/panel/sessions/revoke", json={"sid": "nope"}, headers=self.panel_headers(second["access_token"]))
        self.assertEqual(unknown.status, 404)
        done = await self.client.post("/up/panel/sessions/revoke", json={"sid": rows[1]["sid"]}, headers=self.panel_headers(second["access_token"]))
        self.assertEqual((await done.json())["revoked"], 1)
        self.assertEqual(len(self.app[relay.SESSIONS].by_id), 1)
        # The revoked browser's handle no longer reaches anything.
        self.client.session.cookie_jar.update_cookies(first_cookie)
        seen = await (await self.client.get("/up/tradeyar/api/echo", headers=self.panel_headers(first["access_token"]))).json()
        self.assertNotIn("Authorization", seen["headers"])
        event = (await self.events("session_revoked"))[0]
        self.assertEqual((event["by"], event["target"]), ("reader", rows[1]["sid"]))

    async def test_sign_out_everywhere_ends_this_browser_too(self) -> None:
        await self.sign_in(FIREFOX_DESKTOP)
        self.client.session.cookie_jar.clear()
        answer = await self.sign_in()
        response = await self.client.post("/up/panel/sessions/revoke", json={"all": True}, headers=self.panel_headers(answer["access_token"]))
        self.assertEqual((await response.json())["revoked"], 2)
        self.assertIn("pc_session=;", response.headers.get("Set-Cookie", "").replace('""', ""))
        self.assertEqual(self.app[relay.SESSIONS].by_id, {})

    async def test_page_sign_out_drops_the_session(self) -> None:
        answer = await self.sign_in()
        response = await self.client.post("/up/panel/signout")
        self.assertEqual(response.status, 200)
        self.assertEqual(self.app[relay.SESSIONS].by_id, {})
        event = (await self.events("signout"))[0]
        self.assertEqual(event["how"], "page")
        seen = await (await self.client.get("/up/tradeyar/api/echo", headers=self.panel_headers(answer["access_token"]))).json()
        self.assertNotIn("Authorization", seen["headers"])

    async def test_trading_calls_are_audited_without_secrets(self) -> None:
        answer = await self.sign_in()
        headers = self.panel_headers(answer["access_token"])
        await self.client.post("/up/tradeyar/api/mobile/v1/trade/orders", headers=headers, json={
            "client_request_id": "r1", "symbol": "BTCUSDT", "side": "buy", "type": "limit", "quantity": 0.01,
            "price": 80000, "api_key": "AKIA-NEVER", "token": "tok-NEVER"})
        await self.client.get("/up/tradeyar/api/mobile/v1/trade/orders", headers=headers)
        await self.client.post("/up/tradeyar/api/mobile/v1/venues/lbank", headers=headers,
                               json={"api_key": "KEY-NEVER", "api_secret": "SECRET-NEVER", "permission": "futures"})
        trades = await self.events("trade", 2)
        self.assertEqual(len(trades), 2)  # the GET listing is not a trading call
        order = next(e for e in trades if e["path"] == "trade/orders")
        self.assertEqual(order["fields"], {"client_request_id": "r1", "symbol": "BTCUSDT", "side": "buy", "type": "limit",
                                           "quantity": 0.01, "price": 80000})
        self.assertEqual((order["order_id"], order["status"], order["state"], order["method"]), ("991", 200, "submitted", "POST"))
        self.assertEqual(order["identity"][0]["id"], "7")
        venue = next(e for e in trades if e["path"] == "venues/lbank")
        self.assertEqual(venue["fields"], {"permission": "futures"})
        everything = json.dumps(self.seen["events"])
        for secret in ("NEVER", REAL_ACCESS, "203.0.113.9"):
            self.assertNotIn(secret, everything)

    async def test_a_refused_trade_keeps_the_reason(self) -> None:
        await self.client.post("/up/coineprofx/api/user/auth/login", json={"email": "sara@example.com", "password": "x"})
        await relay.settle(self.app)
        handle = next(h for h, t in self.session().tokens.items() if t == "fx-access")
        await self.client.post("/up/coineprofx/api/user/mobile/trade/positions/close", headers=self.panel_headers(handle),
                               json={"symbol": "XAUUSD", "side": "buy", "quantity": 0.1})
        event = (await self.events("trade"))[0]
        self.assertEqual((event["backend"], event["status"], event["detail"]), ("coineprofx", 422, "margin too low"))
        self.assertEqual(event["identity"][0], {"backend": "coineprofx", "id": "45", "email": "sara@example.com",
                                                "name": "Sara FX", "vip": False, "paid": False, "plan": ""})

    async def test_failed_sign_in_and_refresh_are_reported(self) -> None:
        await self.sign_in(password="wrong")
        failed = (await self.events("signin_failed"))[0]
        expected = hmac.new(PANEL_TOKEN.encode(), b"sara@example.com", hashlib.sha256).hexdigest()[:32]
        self.assertEqual(failed["email_hash"], expected)
        self.assertNotIn("sara@example.com", json.dumps(failed))
        await self.sign_in()
        response = await self.client.post("/up/tradeyar/api/mobile/v1/auth/refresh", json={"refresh_token": "pch_stale"})
        self.assertEqual(response.status, 401)
        await self.events("refresh_failed")
        self.assertNotIn("tradeyar", self.session().identity)

    async def test_the_other_platforms_refusal_of_a_good_sign_in_is_not_a_failed_attempt(self) -> None:
        # The page tries TradeYar, which does not know this password, then gets in on CoinePro-FX.
        await self.sign_in(password="wrong")
        response = await self.client.post("/up/coineprofx/api/user/auth/login",
                                          json={"email": "sara@example.com", "password": "x"},
                                          headers={"User-Agent": CHROME_PHONE, "X-Forwarded-For": "6.6.6.6, 203.0.113.9"})
        self.assertEqual(response.status, 200)
        await self.events("signin")
        await asyncio.sleep(0.6)
        await relay.settle(self.app)
        self.assertEqual([e for e in self.seen["events"] if e["kind"] == "signin_failed"], [])
        self.assertEqual(self.app[relay.PROBES], {})

    async def test_a_refusal_from_another_address_or_the_same_platform_still_counts(self) -> None:
        await self.sign_in(password="wrong")
        await self.client.post("/up/coineprofx/api/user/auth/login", json={"email": "sara@example.com", "password": "x"},
                               headers={"User-Agent": CHROME_PHONE, "X-Forwarded-For": "6.6.6.6, 198.51.100.4"})
        await self.sign_in()  # the same platform, the same address: the refusal was a wrong password
        self.assertEqual(len(await self.events("signin_failed")), 1)

    async def test_the_consoles_calls_need_the_secret(self) -> None:
        await self.sign_in()
        await self.client.post("/up/coineprofx/api/user/auth/guest", json={})
        self.assertEqual((await self.client.get("/relay/internal/sessions")).status, 404)
        self.assertEqual((await self.client.get("/relay/internal/sessions", headers={"X-Panel-Token": "x" * 40})).status, 404)
        token = {"X-Panel-Token": PANEL_TOKEN}
        listing = await (await self.client.get("/relay/internal/sessions", headers=token)).json()
        self.assertEqual(len(listing["sessions"]), 1)
        self.assertEqual(listing["sessions"][0]["accounts"], ["tradeyar:7"])
        self.assertEqual(listing["sessions"][0]["country"], "IR")
        one = await (await self.client.get("/relay/internal/sessions?account=tradeyar:8", headers=token)).json()
        self.assertEqual(one["sessions"], [])
        refused = await self.client.post("/relay/internal/sessions/revoke", json={"account": "tradeyar:7"})
        self.assertEqual(refused.status, 404)
        revoked = await (await self.client.post("/relay/internal/sessions/revoke", headers=token,
                                                json={"account": "tradeyar:7", "operator": "owner"})).json()
        self.assertEqual(revoked["revoked"], 1)
        event = (await self.events("session_revoked"))[0]
        self.assertEqual((event["by"], event["operator"]), ("owner", "owner"))

    async def test_without_a_secret_the_panel_is_off(self) -> None:
        answer = await self.sign_in()
        self.app[relay.CONFIG].panel_token = "short"
        response = await self.client.get("/up/panel/me", headers=self.panel_headers(answer["access_token"]))
        self.assertEqual(response.status, 503)
        self.assertEqual((await self.client.get("/relay/internal/sessions", headers={"X-Panel-Token": "short"})).status, 404)

    def test_agent_families(self) -> None:
        self.assertEqual(relay.agent_family(CHROME_PHONE), ("Chrome", "Android", "phone"))
        self.assertEqual(relay.agent_family(FIREFOX_DESKTOP), ("Firefox", "Windows", "desktop"))
        ipad = "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        self.assertEqual(relay.agent_family(ipad), ("Safari", "iOS", "tablet"))
        edge = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36 Edg/126.0"
        self.assertEqual(relay.agent_family(edge), ("Edge", "macOS", "desktop"))
        self.assertEqual(relay.agent_family(""), ("Other", "Other", "desktop"))

    def test_identity_reading(self) -> None:
        self.assertEqual(relay.read_identity({"user": {"userId": 3, "email": "A@B.c"}})["id"], "3")
        self.assertEqual(relay.read_identity({"telegram_id": 9, "name": "x"})["name"], "x")
        self.assertIsNone(relay.read_identity({"access_token": "t"}))
        self.assertIsNone(relay.read_identity({"id": {"nested": 1}}))


class GuardTest(AioHTTPTestCase):
    async def get_application(self) -> web.Application:
        return relay.build_app(relay.Config())

    async def test_private_and_non_https_addresses_are_refused(self) -> None:
        self.assertEqual((await self.client.get("/api/img", params={"url": "http://example.com/a.png"})).status, 400)
        self.assertEqual((await self.client.get("/api/img", params={"url": "https://127.0.0.1/a.png"})).status, 400)
        self.assertEqual((await self.client.get("/api/img", params={"url": "https://[::1]/a.png"})).status, 400)
        self.assertEqual((await self.client.get("/api/img", params={"url": "https://169.254.169.254/latest"})).status, 400)
        self.assertEqual((await self.client.get("/api/img", params={"url": "https://localhost/a.png"})).status, 502)
        self.assertEqual((await self.client.get("/api/img", params={"url": "https://user:pw@example.com/a.png"})).status, 400)

    def test_is_public(self) -> None:
        for address in ("127.0.0.1", "10.0.0.1", "192.168.1.1", "172.16.0.1", "169.254.169.254", "::1", "fd00::1", "::ffff:10.0.0.1", "0.0.0.0"):
            self.assertFalse(relay.is_public(address), address)
        for address in ("1.1.1.1", "8.8.8.8", "2606:4700::1111"):
            self.assertTrue(relay.is_public(address), address)



class StateTest(unittest.IsolatedAsyncioTestCase):
    """The sessions survive a restart in one encrypted file, and nothing else changes."""

    KEY = "k" * 40

    def setUp(self) -> None:
        import tempfile

        self.dir = tempfile.TemporaryDirectory()
        self.path = os.path.join(self.dir.name, "sessions.bin")

    def tearDown(self) -> None:
        self.dir.cleanup()

    def filled(self) -> tuple[str, relay.Sessions]:
        sessions = relay.Sessions(30 * 86400)
        key, session = sessions.create()
        session.tokens["pch_a"] = REAL_ACCESS
        session.latest_bearer["tradeyar"] = REAL_ACCESS
        session.identity["tradeyar"] = {"id": 7, "email": "a@b.c"}
        session.browser = "Chrome"
        return key, sessions

    def test_round_trip_keeps_every_field_and_the_file_is_sealed(self) -> None:
        key, sessions = self.filled()
        self.assertEqual(relay.save_sessions(sessions, self.path, self.KEY), 1)
        with open(self.path, "rb") as f:
            blob = f.read()
        self.assertNotIn(REAL_ACCESS.encode(), blob)
        self.assertNotIn(b"a@b.c", blob)
        self.assertEqual(os.stat(self.path).st_mode & 0o777, 0o600)
        again = relay.Sessions(30 * 86400)
        self.assertEqual(relay.load_sessions(again, self.path, self.KEY), 1)
        self.assertEqual(again.by_id[key], sessions.by_id[key])

    def test_another_key_or_a_damaged_file_restores_nothing(self) -> None:
        _, sessions = self.filled()
        relay.save_sessions(sessions, self.path, self.KEY)
        self.assertEqual(relay.load_sessions(relay.Sessions(86400), self.path, "z" * 40), 0)
        with open(self.path, "wb") as f:
            f.write(b"not a sealed file")
        self.assertEqual(relay.load_sessions(relay.Sessions(86400), self.path, self.KEY), 0)
        self.assertEqual(relay.load_sessions(relay.Sessions(86400), self.path + ".missing", self.KEY), 0)

    def test_expired_sessions_stay_behind(self) -> None:
        key, sessions = self.filled()
        sessions.by_id[key].touched -= 31 * 86400
        relay.save_sessions(sessions, self.path, self.KEY)
        self.assertEqual(relay.load_sessions(relay.Sessions(30 * 86400), self.path, self.KEY), 0)

    def test_persistence_needs_both_the_file_and_a_long_key(self) -> None:
        self.assertFalse(relay.Config().persist)
        self.assertFalse(relay.Config(state_file=self.path, state_key="short").persist)
        self.assertTrue(relay.Config(state_file=self.path, state_key=self.KEY).persist)

    async def test_a_restarted_relay_knows_the_browser(self) -> None:
        config = relay.Config(backends={"tradeyar": "http://127.0.0.1:9/", "coineprofx": "http://127.0.0.1:9/"},
                              cookie_secure=False, state_file=self.path, state_key=self.KEY)
        first = TestServer(relay.build_app(config))
        await first.start_server()
        key, session = first.app[relay.SESSIONS].create()
        session.tokens["pch_b"] = REAL_REFRESH
        await first.close()  # the cleanup writes the file
        second = TestServer(relay.build_app(config))
        await second.start_server()
        try:
            self.assertEqual(second.app[relay.SESSIONS].get(key).tokens, {"pch_b": REAL_REFRESH})
        finally:
            await second.close()


if __name__ == "__main__":
    unittest.main()
