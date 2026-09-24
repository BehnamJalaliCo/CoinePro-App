"""The relay against a fake of each backend and a fake publisher: `python3 -m unittest test_relay`."""
from __future__ import annotations

import json
import unittest

from aiohttp import WSMsgType, web
from aiohttp.test_utils import AioHTTPTestCase, TestServer

import relay

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


if __name__ == "__main__":
    unittest.main()
