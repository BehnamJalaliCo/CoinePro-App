"""The owner console against an in-memory database: `python3 -m unittest test_admin`."""
from __future__ import annotations

import base64
import io
import json
import os
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest import mock

from aiohttp.test_utils import AioHTTPTestCase

# The deployed container sets ADMIN_* for itself; the tests describe the console, not the host.
for _name in [name for name in os.environ if name.startswith("ADMIN_")]:
    del os.environ[_name]

import admin  # noqa: E402

USER = "owner"
PASSWORD = "correct-horse-battery-9"


class ConsoleTest(AioHTTPTestCase):
    async def get_application(self):
        self.tmp = tempfile.TemporaryDirectory()
        latest = Path(self.tmp.name) / "latest.json"
        latest.write_text(json.dumps({"version_code": 53000000, "version_name": "5.30.0",
                                      "url": "https://pro-chart.com/download/pro-chart-5.30.0.apk",
                                      "sha256": "a" * 64, "notes_fa": "یادداشت", "notes_en": "notes", "mandatory": False}))
        self.log = Path(self.tmp.name) / "access.log"
        self.config = admin.Config(db_path=":memory:", access_logs=[str(self.log)], latest_json=str(latest),
                                   cookie_secure=False, background=False, probes=[])
        app = admin.build_app(self.config)
        store = app["store"]
        store.x("INSERT INTO users(username, pw_hash, created_at) VALUES (?,?,?)",
                (USER, admin.hash_password(PASSWORD), admin.now()))
        return app

    async def tearDownAsync(self):
        await super().tearDownAsync()
        self.tmp.cleanup()

    @property
    def store(self) -> admin.Store:
        return self.app["store"]

    async def sign_in(self) -> str:
        response = await self.client.post("/admin/api/login", json={"username": USER, "password": PASSWORD})
        self.assertEqual(response.status, 200)
        session = await (await self.client.get("/admin/api/session")).json()
        return session["csrf"]

    async def hello(self, install: str, **extra) -> dict:
        body = {"install_id": install, "platform": "android", "flavour": "direct", "lang": "fa", "version": "5.30.0",
                "version_code": 53000000, "sdk": 34, "tz": "Asia/Tehran", **extra}
        response = await self.client.post("/api/app/hello", json=body, headers={"CF-IPCountry": "IR"})
        self.assertEqual(response.status, 200)
        return await response.json()

    # ── Signing in ──────────────────────────────────────────────────────────────────────────────

    async def test_console_is_closed_until_signed_in(self):
        for path in ("overview", "downloads", "audience", "installs", "messages", "coupons", "config", "release",
                     "health", "audit", "installs.csv", "audit.csv", "account/sessions"):
            response = await self.client.get(f"/admin/api/{path}")
            self.assertEqual(response.status, 401, path)

    async def test_wrong_password_and_unknown_user_answer_alike(self):
        wrong = await self.client.post("/admin/api/login", json={"username": USER, "password": "nope-nope-nope"})
        nobody = await self.client.post("/admin/api/login", json={"username": "ghost", "password": "nope-nope-nope"})
        self.assertEqual((wrong.status, await wrong.json()), (nobody.status, await nobody.json()))
        self.assertEqual(wrong.status, 401)

    async def test_session_cookie_is_locked_down(self):
        response = await self.client.post("/admin/api/login", json={"username": USER, "password": PASSWORD})
        cookie = response.headers["Set-Cookie"]
        self.assertIn("HttpOnly", cookie)
        self.assertIn("SameSite=Strict", cookie)
        self.assertIn("Path=/admin", cookie)

    async def test_secure_cookie_by_default(self):
        self.assertTrue(admin.Config().cookie_secure)

    async def test_changes_need_the_csrf_token(self):
        await self.sign_in()
        response = await self.client.post("/admin/api/coupons", json={"percent": 10})
        self.assertEqual(response.status, 403)

    async def test_changes_from_another_origin_are_refused(self):
        csrf = await self.sign_in()
        response = await self.client.post("/admin/api/coupons", json={"percent": 10},
                                          headers={"X-CSRF-Token": csrf, "Origin": "https://evil.example"})
        self.assertEqual(response.status, 403)

    async def test_account_locks_after_repeated_failures(self):
        for _ in range(self.config.lockout_after_failures):
            await self.client.post("/admin/api/login", json={"username": USER, "password": "wrong-wrong-wrong"})
        response = await self.client.post("/admin/api/login", json={"username": USER, "password": PASSWORD})
        self.assertEqual(response.status, 423)

    async def test_logout_ends_the_session(self):
        csrf = await self.sign_in()
        await self.client.post("/admin/api/logout", headers={"X-CSRF-Token": csrf})
        self.assertEqual((await self.client.get("/admin/api/overview")).status, 401)

    async def test_totp_second_factor(self):
        csrf = await self.sign_in()
        setup = await (await self.client.post("/admin/api/account/totp/setup", headers={"X-CSRF-Token": csrf})).json()
        self.assertTrue(setup["uri"].startswith("otpauth://totp/"))
        code = admin.totp_code(setup["secret"], int(time.time() // 30))
        enabled = await self.client.post("/admin/api/account/totp/enable", json={"code": code}, headers={"X-CSRF-Token": csrf})
        self.assertEqual(enabled.status, 200)
        await self.client.post("/admin/api/logout", headers={"X-CSRF-Token": csrf})
        first = await self.client.post("/admin/api/login", json={"username": USER, "password": PASSWORD})
        self.assertEqual(first.status, 401)
        self.assertTrue((await first.json())["need_code"])
        # The code that enabled it is spent; the next one in time is not.
        replay = await self.client.post("/admin/api/login", json={"username": USER, "password": PASSWORD, "code": code})
        self.assertEqual(replay.status, 401)
        fresh = admin.totp_code(setup["secret"], int(time.time() // 30) + 1)
        ok = await self.client.post("/admin/api/login", json={"username": USER, "password": PASSWORD, "code": fresh})
        self.assertEqual(ok.status, 200)

    async def test_password_change_rules(self):
        csrf = await self.sign_in()
        bad = await self.client.post("/admin/api/account/password", json={"current": "nope", "new": "x" * 12},
                                     headers={"X-CSRF-Token": csrf})
        self.assertEqual(bad.status, 401)
        weak = await self.client.post("/admin/api/account/password", json={"current": PASSWORD, "new": "aaaaaaaaaaaa"},
                                      headers={"X-CSRF-Token": csrf})
        self.assertEqual((await weak.json())["error"], "too_simple")
        good = await self.client.post("/admin/api/account/password", json={"current": PASSWORD, "new": "N3w-Passphrase!"},
                                      headers={"X-CSRF-Token": csrf})
        self.assertEqual(good.status, 200)

    async def test_console_page_has_a_strict_policy(self):
        response = await self.client.get("/admin/")
        self.assertEqual(response.status, 200)
        self.assertIn("script-src 'self'", response.headers["Content-Security-Policy"])
        self.assertEqual(response.headers["X-Frame-Options"], "DENY")
        self.assertIn("app.js", await response.text())

    async def test_missing_console_file_is_not_cached(self):
        response = await self.client.get("/admin/static/nope.js")
        self.assertEqual(response.status, 404)
        self.assertEqual(response.headers.get("Cache-Control"), "no-store")
        hello = await self.client.post("/api/app/hello", json={"install_id": "install-0009"})
        self.assertEqual(hello.headers.get("Cache-Control"), "no-store")

    async def test_an_install_without_a_version_is_labelled(self):
        await self.client.post("/api/app/hello", json={"install_id": "install-0009", "version_code": 1})
        await self.sign_in()
        overview = await (await self.client.get("/admin/api/overview")).json()
        self.assertEqual(overview["versions"], [{"label": "?", "value": 1}])

    async def test_test_traffic_can_be_forgotten(self):
        await self.hello("test-install-0001")
        self.log.write_text(self.access("/download/pro-chart-5.30.0.apk", ip="9.9.9.9") + self.access("/terminal/", ip="9.9.9.9"))
        admin.ingest_logs(self.app)
        with mock.patch.object(admin, "Store", lambda _path: self.store):
            self.assertEqual(admin.forget_cli(["forget-install", "test-install-0001"]), 0)
            self.assertEqual(admin.forget_cli(["forget-ip", "9.9.9.9"]), 0)
        self.assertEqual(self.store.one("SELECT COUNT(*) FROM installs"), 0)
        self.assertEqual(self.store.one("SELECT COUNT(*) FROM downloads"), 0)
        self.assertEqual(self.store.one("SELECT COUNT(*) FROM visits"), 0)
        self.assertEqual(self.store.one("SELECT COUNT(*) FROM download_hits"), 0)
        self.assertEqual(self.store.one("SELECT COUNT(*) FROM active_hours"), 0)

    async def test_the_consoles_own_probes_are_not_traffic(self):
        line = json.loads(self.access("/terminal/"))
        line["request"]["headers"]["User-Agent"] = [admin.PROBE_AGENT_PREFIX + "1 (health probe)"]
        update = json.loads(self.access("/api/app/latest"))
        update["request"]["headers"]["User-Agent"] = [admin.PROBE_AGENT_PREFIX + "1 (health probe)"]
        self.log.write_text(json.dumps(line) + "\n" + json.dumps(update) + "\n")
        admin.ingest_logs(self.app)
        self.assertEqual(self.store.one("SELECT COUNT(*) FROM visits"), 0)
        self.assertEqual(self.store.one("SELECT COUNT(*) FROM update_checks"), 0)

    def test_forget_hour(self):
        self.store.x("INSERT INTO active_hours(day, hour, weekday, count) VALUES ('2026-10-13', 14, 0, 1)")
        with mock.patch.object(admin, "Store", lambda _path: self.store):
            self.assertEqual(admin.forget_cli(["forget-hour", "2026-10-13", "14"]), 0)
        self.assertEqual(self.store.one("SELECT COUNT(*) FROM active_hours"), 0)

    async def test_console_page_has_no_inline_styles_or_scripts(self):
        static = Path(admin.HERE) / "static"
        html = (static / "index.html").read_text(encoding="utf-8")
        self.assertNotIn("style=", html)
        self.assertNotIn("<script>", html)
        self.assertNotIn('style="', (static / "app.js").read_text(encoding="utf-8"))
        self.assertNotIn('style="', (static / "webusers.js").read_text(encoding="utf-8"))
        self.assertIn("webusers.js", html)

    async def test_web_users_console_is_served_with_the_strict_policy(self):
        response = await self.client.get("/admin/static/webusers.js")
        self.assertEqual(response.status, 200)
        self.assertIn("VIEWS.web", await response.text())

    # ── The app's check-in, campaigns and codes ─────────────────────────────────────────────────

    async def test_hello_registers_an_install_once(self):
        await self.hello("install-0001")
        await self.hello("install-0001")
        await self.hello("install-0002", platform="web")
        rows = self.store.q("SELECT * FROM installs")
        self.assertEqual(len(rows), 2)
        self.assertEqual(sorted(r["hellos"] for r in rows), [1, 2])
        self.assertTrue(all(r["country"] == "IR" for r in rows))
        # Only a salted hash of the id is kept.
        self.assertNotIn("install-0001", json.dumps([dict(r) for r in rows]))

    async def test_hello_refuses_a_missing_id(self):
        response = await self.client.post("/api/app/hello", json={"install_id": "x"})
        self.assertEqual(response.status, 400)

    async def test_messages_reach_only_their_audience(self):
        csrf = await self.sign_in()
        headers = {"X-CSRF-Token": csrf}
        everyone = await (await self.client.post("/admin/api/messages", json={
            "kind": "announcement", "title_fa": "سلام", "body_fa": "متن", "title_en": "Hi", "body_en": "Body"}, headers=headers)).json()
        bazaar = await (await self.client.post("/admin/api/messages", json={
            "kind": "offer", "title_fa": "بازار", "body_fa": "فقط بازار", "audience": {"flavours": ["bazaar"]}}, headers=headers)).json()
        old = await (await self.client.post("/admin/api/messages", json={
            "kind": "update", "title_fa": "به‌روز کنید", "body_fa": "نسخهٔ تازه", "audience": {"max_version_code": 52999999}},
            headers=headers)).json()
        later = await (await self.client.post("/admin/api/messages", json={
            "kind": "announcement", "title_fa": "بعداً", "body_fa": "بعداً", "starts_at": admin.now() + 3600}, headers=headers)).json()
        draft = await (await self.client.post("/admin/api/messages", json={
            "kind": "announcement", "title_fa": "پیش", "body_fa": "نویس", "draft": True}, headers=headers)).json()
        direct = await self.hello("install-0001", lang="en")
        self.assertEqual([m["id"] for m in direct["messages"]], [everyone["id"]])
        self.assertEqual(direct["messages"][0]["title"], "Hi")
        store = await self.hello("install-0002", flavour="bazaar", version_code=52900000)
        self.assertEqual(sorted(m["id"] for m in store["messages"]), sorted([everyone["id"], bazaar["id"], old["id"]]))
        for hidden in (later["id"], draft["id"]):
            self.assertNotIn(hidden, [m["id"] for m in store["messages"]])
        estimate = await (await self.client.post("/admin/api/messages/estimate", json={"audience": {"flavours": ["bazaar"]}},
                                                 headers=headers)).json()
        self.assertEqual(estimate, {"reach": 1, "active": 2})

    async def test_delivered_and_opened_are_counted_once(self):
        csrf = await self.sign_in()
        created = await (await self.client.post("/admin/api/messages", json={
            "kind": "announcement", "title_fa": "سلام", "body_fa": "متن"}, headers={"X-CSRF-Token": csrf})).json()
        for kind in ("delivered", "delivered", "opened"):
            response = await self.client.post("/api/app/event", json={"install_id": "install-0001", "message_id": created["id"], "kind": kind})
            self.assertEqual(response.status, 200)
        listing = await (await self.client.get("/admin/api/messages")).json()
        stats = listing["messages"][0]["stats"]
        self.assertEqual((stats["delivered"], stats["opened"], stats["open_rate"]), (1, 1, 1.0))
        unknown = await self.client.post("/api/app/event", json={"install_id": "install-0001", "message_id": 999, "kind": "opened"})
        self.assertEqual(unknown.status, 404)

    async def test_message_validation(self):
        csrf = await self.sign_in()
        headers = {"X-CSRF-Token": csrf}
        cases = [
            ({"kind": "spam", "title_fa": "a", "body_fa": "b"}, "kind"),
            ({"kind": "offer", "title_fa": "", "body_fa": ""}, "text"),
            ({"kind": "offer", "title_fa": "a", "body_fa": "b", "link": "javascript:alert(1)"}, "link"),
            ({"kind": "offer", "title_fa": "a", "body_fa": "b", "link": "http://pro-chart.com"}, "link"),
            ({"kind": "offer", "title_fa": "a", "body_fa": "b", "coupon": "NOPE"}, "coupon"),
            ({"kind": "offer", "title_fa": "a", "body_fa": "b", "starts_at": 200, "ends_at": 100}, "schedule"),
        ]
        for body, error in cases:
            response = await self.client.post("/admin/api/messages", json=body, headers=headers)
            self.assertEqual((response.status, (await response.json())["error"]), (400, error), body)

    async def test_a_sent_message_is_cancelled_not_edited(self):
        csrf = await self.sign_in()
        headers = {"X-CSRF-Token": csrf}
        created = await (await self.client.post("/admin/api/messages", json={"kind": "alert", "title_fa": "a", "body_fa": "b"},
                                                headers=headers)).json()
        edit = await self.client.post("/admin/api/messages", json={"id": created["id"], "kind": "alert", "title_fa": "c", "body_fa": "d"},
                                      headers=headers)
        self.assertEqual(edit.status, 409)
        await self.client.post(f"/admin/api/messages/{created['id']}/cancel", headers=headers)
        self.assertEqual((await self.hello("install-0001"))["messages"], [])
        copy = await (await self.client.post(f"/admin/api/messages/{created['id']}/duplicate", headers=headers)).json()
        listing = await (await self.client.get("/admin/api/messages")).json()
        states = {m["id"]: m["state"] for m in listing["messages"]}
        self.assertEqual(states, {created["id"]: "cancelled", copy["id"]: "draft"})

    async def test_coupons(self):
        csrf = await self.sign_in()
        headers = {"X-CSRF-Token": csrf}
        made = await (await self.client.post("/admin/api/coupons", json={"code": "yalda-20", "percent": 20, "max_uses": 5},
                                             headers=headers)).json()
        self.assertEqual(made["code"], "YALDA-20")
        auto = await (await self.client.post("/admin/api/coupons", json={"percent": 15}, headers=headers)).json()
        self.assertRegex(auto["code"], r"^PC-[0-9A-F]{6}$")
        self.assertEqual((await self.client.post("/admin/api/coupons", json={"code": "YALDA-20", "percent": 5}, headers=headers)).status, 409)
        self.assertEqual((await self.client.post("/admin/api/coupons", json={"percent": 95}, headers=headers)).status, 400)
        check = await (await self.client.get("/api/app/coupon?code=yalda-20")).json()
        self.assertEqual((check["valid"], check["percent"]), (True, 20))
        await self.client.post("/admin/api/coupons/YALDA-20/toggle", headers=headers)
        self.assertFalse((await (await self.client.get("/api/app/coupon?code=YALDA-20")).json())["valid"])
        self.assertFalse((await (await self.client.get("/api/app/coupon?code=NOPE")).json())["valid"])
        message = await self.client.post("/admin/api/messages", json={"kind": "offer", "title_fa": "a", "body_fa": "b", "coupon": "yalda-20"},
                                         headers=headers)
        self.assertEqual(message.status, 200)
        deleted = await self.client.delete("/admin/api/coupons/YALDA-20", headers=headers)
        self.assertEqual(deleted.status, 200)

    async def test_remote_config_reaches_the_app(self):
        csrf = await self.sign_in()
        response = await self.client.put("/admin/api/config", json={
            "maintenance": {"on": True, "message_fa": "تعمیرات", "message_en": "Maintenance"},
            "min_version_code": 53000000, "hello_every_hours": 99}, headers={"X-CSRF-Token": csrf})
        self.assertEqual(response.status, 200)
        config = (await self.hello("install-0001"))["config"]
        self.assertTrue(config["maintenance"]["on"])
        self.assertEqual(config["min_version_code"], 53000000)
        self.assertEqual(config["hello_every_hours"], 24)
        self.assertFalse(config["update_nudge"]["on"])

    # ── Reports ─────────────────────────────────────────────────────────────────────────────────

    def access(self, uri: str, status: int = 200, ip: str = "1.2.3.4", range_header: str | None = None, size: int = 1000) -> str:
        headers = {"Cf-Connecting-Ip": [ip], "Cf-Ipcountry": ["IR"]}
        if range_header:
            headers["Range"] = [range_header]
        return json.dumps({"ts": time.time(), "status": status, "size": size,
                           "request": {"method": "GET", "uri": uri, "headers": headers, "client_ip": "10.0.0.1"}}) + "\n"

    async def test_downloads_are_read_from_the_access_log(self):
        lines = [
            self.access("/download/pro-chart-5.30.0.apk"),
            self.access("/download/pro-chart-5.30.0.apk"),                                         # the same reader again
            self.access("/download/pro-chart-5.30.0.apk", status=206, range_header="bytes=5000-"),  # a resume
            self.access("/download/pro-chart-5.30.0.apk", ip="5.6.7.8"),
            self.access("/download/pro-chart-5.29.0.apk", ip="5.6.7.8"),
            self.access("/download/pro-chart-5.30.0.apk", status=404, ip="9.9.9.9"),
            self.access("/terminal/"), self.access("/terminal/", ip="5.6.7.8"),
            self.access("/api/app/latest"),
            "not json\n",
        ]
        self.log.write_text("".join(lines) + '{"partial":')
        self.assertEqual(admin.ingest_logs(self.app), 10)
        # A second pass reads nothing new; the half-written last line waits for its newline.
        self.assertEqual(admin.ingest_logs(self.app), 0)
        await self.sign_in()
        report = await (await self.client.get("/admin/api/downloads?days=7")).json()
        self.assertEqual(report["unique"][-1], 3)
        self.assertEqual(report["hits"][-1], 4)
        self.assertEqual(report["visits"][-1], 2)
        self.assertEqual(report["update_checks"][-1], 1)
        self.assertEqual(report["files"][0], {"label": "pro-chart-5.30.0.apk", "value": 2, "version": "5.30.0"})
        overview = await (await self.client.get("/admin/api/overview")).json()
        self.assertEqual(overview["kpis"]["downloads_today"], 3)

    async def test_rotated_log_is_read_from_the_start(self):
        self.log.write_text(self.access("/download/pro-chart-5.30.0.apk"))
        admin.ingest_logs(self.app)
        self.log.unlink()
        self.log.write_text(self.access("/download/pro-chart-5.30.0.apk", ip="7.7.7.7"))
        self.assertEqual(admin.ingest_logs(self.app), 1)

    async def test_reports_answer_with_data(self):
        await self.hello("install-0001")
        await self.hello("install-0002", flavour="bazaar", lang="en", version="5.29.0", version_code=52900000)
        await self.sign_in()
        overview = await (await self.client.get("/admin/api/overview")).json()
        self.assertEqual(overview["kpis"]["dau"], 2)
        self.assertEqual(overview["kpis"]["adoption"], 0.5)
        self.assertEqual(len(overview["days"]), 30)
        audience = await (await self.client.get("/admin/api/audience?days=30")).json()
        self.assertEqual(sum(map(sum, audience["heat"])), 2)
        installs = await (await self.client.get("/admin/api/installs?flavour=bazaar")).json()
        self.assertEqual(installs["total"], 1)
        csv_text = await (await self.client.get("/admin/api/installs.csv")).text()
        self.assertIn("id_hash", csv_text)
        release = await (await self.client.get("/admin/api/release")).json()
        self.assertEqual(release["latest"]["version_name"], "5.30.0")
        self.assertEqual([v["code"] for v in release["versions"]], [53000000, 52900000])
        health = await (await self.client.get("/admin/api/health")).json()
        self.assertIn("server", health)
        audit = await (await self.client.get("/admin/api/audit")).json()
        self.assertEqual(audit["rows"][0]["action"], "login.ok")

    async def test_probes_are_recorded(self):
        self.config.probes = [{"name": "console", "url": str(self.client.make_url("/admin/health"))}]
        csrf = await self.sign_in()
        results = await (await self.client.post("/admin/api/health/run", headers={"X-CSRF-Token": csrf})).json()
        self.assertTrue(results["results"][0]["ok"])
        health = await (await self.client.get("/admin/api/health")).json()
        self.assertEqual(health["uptime_7d"], {"console": 1.0})


class UsersConsoleTest(AioHTTPTestCase):
    """«کاربران»: the console relays to a bridge and keeps the bridge's secret to itself."""

    BRIDGE_TOKEN = "b" * 40

    async def get_application(self):
        from aiohttp import web
        from aiohttp.test_utils import TestServer

        self.calls: list[tuple[str, str, dict, dict | None, dict]] = []
        accounts = {7: {"id": 7, "email": "sara@example.com", "full_name": "سارا", "banned": False,
                        "signup_platform": "android", "signup_flavor": "bazaar", "sessions": []}}

        async def handler(request: web.Request) -> web.Response:
            body = await request.json() if request.can_read_body else None
            self.calls.append((request.method, request.path, dict(request.query), body, dict(request.headers)))
            if request.headers.get("X-ProChart-Admin") != self.BRIDGE_TOKEN:
                return web.json_response({"detail": "unauthorised"}, status=401)
            tail = request.path.split("/admin-bridge", 1)[1]
            if tail == "/stats":
                return web.json_response({"totals": {"total": 1}, "platforms": [], "flavors": [], "methods": [],
                                          "versions": [], "daily_signups": []})
            if tail == "/users":
                return web.json_response({"total": 1, "page": 1, "size": 50, "users": [accounts[7]]})
            parts = tail.strip("/").split("/")
            user = accounts.get(int(parts[1])) if len(parts) > 1 and parts[1].isdigit() else None
            if user is None:
                return web.json_response({"detail": "no such account"}, status=404)
            if len(parts) == 2 and request.method == "GET":
                return web.json_response(user)
            return web.json_response({"ok": True, "sessions_ended": 2})

        bridge = web.Application()
        bridge.router.add_route("*", "/{tail:.*}", handler)
        self.bridge_server = TestServer(bridge)
        await self.bridge_server.start_server()
        base = str(self.bridge_server.make_url("/api/mobile/v1/admin-bridge"))

        self.tmp = tempfile.TemporaryDirectory()
        config = admin.Config(db_path=":memory:", access_logs=[], latest_json=str(Path(self.tmp.name) / "none.json"),
                              cookie_secure=False, background=False, probes=[])
        config.bridges = {
            "tradeyar": {"label": "TradeYar", "url": base, "token": self.BRIDGE_TOKEN},
            "coineprofx": {"label": "CoinePro-FX", "url": base, "token": ""},
        }
        app = admin.build_app(config)
        app["store"].x("INSERT INTO users(username, pw_hash, created_at) VALUES (?,?,?)",
                       (USER, admin.hash_password(PASSWORD), admin.now()))
        return app

    async def tearDownAsync(self):
        await self.bridge_server.close()
        await super().tearDownAsync()
        self.tmp.cleanup()

    async def sign_in(self) -> str:
        await self.client.post("/admin/api/login", json={"username": USER, "password": PASSWORD})
        return (await (await self.client.get("/admin/api/session")).json())["csrf"]

    async def test_closed_until_signed_in(self):
        for path in ("users/backends", "users/tradeyar/stats", "users/tradeyar/list", "users/tradeyar/7",
                     "users/tradeyar/list.csv"):
            self.assertEqual((await self.client.get(f"/admin/api/{path}")).status, 401, path)
        self.assertEqual(self.calls, [])

    async def test_backends_say_which_are_configured_and_never_the_token(self):
        await self.sign_in()
        body = await (await self.client.get("/admin/api/users/backends")).text()
        self.assertNotIn(self.BRIDGE_TOKEN, body)
        backends = {b["key"]: b["configured"] for b in json.loads(body)["backends"]}
        self.assertEqual(backends, {"tradeyar": True, "coineprofx": False})
        unconfigured = await self.client.get("/admin/api/users/coineprofx/stats")
        self.assertEqual(unconfigured.status, 503)

    async def test_list_relays_only_known_filters_with_the_secret(self):
        await self.sign_in()
        response = await self.client.get("/admin/api/users/tradeyar/list?q=sara&platform=android&evil=1&page=2")
        self.assertEqual(response.status, 200)
        self.assertEqual((await response.json())["users"][0]["email"], "sara@example.com")
        method, path, query, _, headers = self.calls[-1]
        self.assertEqual((method, path), ("GET", "/api/mobile/v1/admin-bridge/users"))
        self.assertEqual(query, {"q": "sara", "platform": "android", "page": "2"})
        self.assertEqual(headers["X-ProChart-Admin"], self.BRIDGE_TOKEN)

    async def test_actions_need_csrf_and_are_audited_with_the_operator(self):
        csrf = await self.sign_in()
        refused = await self.client.post("/admin/api/users/tradeyar/7/ban", json={"reason": "spam"})
        self.assertEqual(refused.status, 403)
        done = await self.client.post("/admin/api/users/tradeyar/7/ban", json={"reason": "spam"},
                                      headers={"X-CSRF-Token": csrf})
        self.assertEqual((await done.json())["sessions_ended"], 2)
        method, path, _, body, headers = self.calls[-1]
        self.assertEqual((method, path, body), ("POST", "/api/mobile/v1/admin-bridge/users/7/ban", {"reason": "spam"}))
        self.assertEqual(headers["X-ProChart-Operator"], USER)
        audit = self.app["store"].q("SELECT action, detail FROM audit WHERE action LIKE 'users.%'")
        self.assertEqual(audit[-1]["action"], "users.ban")
        self.assertIn('"backend": "tradeyar"', audit[-1]["detail"])
        unknown = await self.client.post("/admin/api/users/tradeyar/7/drop-tables", headers={"X-CSRF-Token": csrf})
        self.assertEqual(unknown.status, 404)

    async def test_push_needs_a_title_and_a_body(self):
        csrf = await self.sign_in()
        response = await self.client.post("/admin/api/users/tradeyar/7/push", json={"title": "سلام"},
                                          headers={"X-CSRF-Token": csrf})
        self.assertEqual(response.status, 400)

    async def test_delete_needs_the_typed_email(self):
        csrf = await self.sign_in()
        wrong = await self.client.delete("/admin/api/users/tradeyar/7", json={"confirm": "someone@else.com"},
                                         headers={"X-CSRF-Token": csrf})
        self.assertEqual((wrong.status, (await wrong.json())["error"]), (400, "confirm_mismatch"))
        self.assertFalse(any(c[0] == "DELETE" for c in self.calls))
        right = await self.client.delete("/admin/api/users/tradeyar/7", json={"confirm": "Sara@Example.com"},
                                         headers={"X-CSRF-Token": csrf})
        self.assertEqual(right.status, 200)
        self.assertEqual(self.calls[-1][0], "DELETE")

    async def test_a_missing_account_and_a_wrong_secret_are_told_apart(self):
        await self.sign_in()
        self.assertEqual((await self.client.get("/admin/api/users/tradeyar/99")).status, 404)
        self.app["config"].bridges["tradeyar"]["token"] = "x" * 40
        mismatch = await self.client.get("/admin/api/users/tradeyar/stats")
        self.assertEqual((mismatch.status, (await mismatch.json())["error"]), (502, "bridge_token_mismatch"))

    async def test_csv_export(self):
        await self.sign_in()
        response = await self.client.get("/admin/api/users/tradeyar/list.csv")
        self.assertEqual(response.status, 200)
        text = (await response.read()).decode("utf-8-sig")
        self.assertIn("sara@example.com", text)
        self.assertTrue(text.startswith("id,email"))


PANEL_TOKEN = "p" * 40
PNG = base64.b64encode(b"\x89PNG\r\n\x1a\n" + b"\x00" * 64).decode()


def identity(*accounts: tuple[str, str, str], sid: str = "s1", **extra) -> str:
    """The relay's X-Panel-Identity for a session holding these (backend, id, email) accounts."""
    fields = {"identity": [{"backend": b, "id": i, "email": e, "name": "سارا", "vip": False, "paid": False, "plan": ""}
                           for b, i, e in accounts],
              "sid": sid, "browser": "Chrome", "os": "Android", "device": "phone", "country": "IR", "ip_hash": "abcd1234",
              **extra}
    return base64.urlsafe_b64encode(json.dumps(fields).encode()).decode().rstrip("=")


SARA = ("tradeyar", "7", "sara@example.com")
SARA_FX = ("coineprofx", "45", "sara@example.com")
ALI = ("tradeyar", "8", "ali@example.com")


class WebPanelTest(AioHTTPTestCase):
    """The web user panel's store (the relay's internal routes) and «کاربران وب» (the owner's view)."""

    async def get_application(self):
        from aiohttp import web
        from aiohttp.test_utils import TestServer

        self.relay_sessions: list[dict] = []
        self.relay_calls: list[tuple[str, dict, dict]] = []

        async def sessions(request: web.Request) -> web.Response:
            if request.headers.get("X-Panel-Token") != PANEL_TOKEN:
                return web.json_response({"error": "not_found"}, status=404)
            account = request.query.get("account")
            rows = [s for s in self.relay_sessions if not account or account in s["accounts"]]
            return web.json_response({"sessions": rows, "guests": 3})

        async def revoke(request: web.Request) -> web.Response:
            body = await request.json()
            self.relay_calls.append(("revoke", body, dict(request.headers)))
            return web.json_response({"revoked": 1 if body["account"] == "tradeyar:7" else 0})

        async def bridge(request: web.Request) -> web.Response:
            if request.headers.get("X-ProChart-Admin") != "b" * 40:
                return web.json_response({"detail": "unauthorised"}, status=401)
            tail = request.path.split("/admin-bridge", 1)[1]
            if tail == "/users":
                q = request.query.get("q")
                users = [{"id": 501, "email": "sara@example.com"}] if q == "sara@example.com" else []
                return web.json_response({"total": len(users), "users": users})
            if tail == "/users/501":
                return web.json_response({"id": 501, "email": "sara@example.com", "banned": False, "sessions": [{"live": True}],
                                          "membership": {"vip": True, "paid": True, "plan": "pro", "expires_at": None},
                                          "subscriptions": [{"plan": "monthly", "source": "usdt", "live": True,
                                                             "ends_at": "2030-01-01T00:00:00+00:00"}]})
            return web.json_response({"detail": "no such account"}, status=404)

        fake = web.Application()
        fake.router.add_get("/relay/internal/sessions", sessions)
        fake.router.add_post("/relay/internal/sessions/revoke", revoke)
        fake.router.add_route("*", "/api/mobile/v1/admin-bridge/{tail:.*}", bridge)
        self.fake = TestServer(fake)
        await self.fake.start_server()

        self.tmp = tempfile.TemporaryDirectory()
        config = admin.Config(db_path=":memory:", access_logs=[], latest_json=str(Path(self.tmp.name) / "none.json"),
                              cookie_secure=False, background=False, probes=[], panel_token=PANEL_TOKEN,
                              relay_url=str(self.fake.make_url("")).rstrip("/"), panel_history=3)
        config.bridges = {
            "tradeyar": {"label": "TradeYar", "url": str(self.fake.make_url("/api/mobile/v1/admin-bridge")), "token": "b" * 40},
            "coineprofx": {"label": "CoinePro-FX", "url": "http://127.0.0.1:9/none", "token": ""},
        }
        app = admin.build_app(config)
        app["store"].x("INSERT INTO users(username, pw_hash, created_at) VALUES (?,?,?)",
                       (USER, admin.hash_password(PASSWORD), admin.now()))
        return app

    async def tearDownAsync(self):
        await self.fake.close()
        await super().tearDownAsync()
        self.tmp.cleanup()

    @property
    def store(self) -> admin.Store:
        return self.app["store"]

    async def panel(self, method: str, path: str, body=None, who: str | None = None, token: str = PANEL_TOKEN, raw: bytes | None = None):
        headers = {"X-Panel-Token": token, "X-Panel-Identity": who if who is not None else identity(SARA)}
        if raw is not None:
            headers["Content-Type"] = "application/json"
            return await self.client.request(method, "/internal/panel/" + path, data=raw, headers=headers)
        return await self.client.request(method, "/internal/panel/" + path, json=body, headers=headers)

    async def sign_in(self) -> str:
        await self.client.post("/admin/api/login", json={"username": USER, "password": PASSWORD})
        return (await (await self.client.get("/admin/api/session")).json())["csrf"]

    async def relay_events(self, *events: dict):
        response = await self.client.post("/internal/panel/events", json={"events": list(events)},
                                          headers={"X-Panel-Token": PANEL_TOKEN})
        self.assertEqual(response.status, 200)
        return await response.json()

    def relay_identity(self, *accounts) -> dict:
        return {"identity": [{"backend": b, "id": i, "email": e, "name": "", "vip": False, "paid": False, "plan": ""}
                             for b, i, e in accounts], "sid": "s1", "browser": "Firefox", "os": "Windows",
                "device": "desktop", "country": "DE", "ip_hash": "ffff0000"}

    # ── The relay's door ────────────────────────────────────────────────────────────────────────

    async def test_only_the_relay_with_the_secret_gets_in(self):
        self.assertEqual((await self.client.get("/internal/panel/me")).status, 404)
        self.assertEqual((await self.panel("GET", "me", token="x" * 40)).status, 404)
        self.assertEqual((await self.client.post("/internal/panel/events", json={"events": []})).status, 404)
        self.assertEqual((await self.panel("GET", "me", who="")).status, 401)
        self.assertEqual((await self.panel("GET", "me", who="not-base64-json")).status, 401)
        bad = identity(("tradeyar", "7; DROP TABLE", "x@y.z"))
        self.assertEqual((await self.panel("GET", "me", who=bad)).status, 401)
        self.app["config"].panel_token = "short"
        self.assertEqual((await self.panel("GET", "me", token="short")).status, 503)
        self.assertEqual(self.store.one("SELECT COUNT(*) FROM web_accounts"), 0)

    async def test_internal_answers_are_never_cached(self):
        response = await self.panel("GET", "me")
        self.assertEqual(response.headers.get("Cache-Control"), "no-store")

    async def test_both_platforms_in_one_browser_are_one_web_account(self):
        me = await (await self.panel("GET", "me", who=identity(SARA, SARA_FX))).json()
        self.assertEqual(me["account"], "tradeyar:7")
        self.assertEqual([a["alias"] for a in me["aliases"]], ["coineprofx:45", "tradeyar:7"])
        alone = await (await self.panel("GET", "me", who=identity(SARA_FX))).json()
        self.assertEqual(alone["account"], "tradeyar:7")
        other = await (await self.panel("GET", "me", who=identity(ALI))).json()
        self.assertEqual(other["account"], "tradeyar:8")
        self.assertEqual(me["session"]["browser"], "Chrome")

    # ── What the relay saw ──────────────────────────────────────────────────────────────────────

    async def test_relay_events_become_activity_trades_and_security(self):
        who = self.relay_identity(SARA)
        await self.relay_events(
            {"kind": "signin", "backend": "tradeyar", "method": "google", "at": admin.now(), **who},
            {"kind": "trade", "backend": "tradeyar", "method": "POST", "path": "trade/orders", "status": 200,
             "fields": {"symbol": "btcusdt", "side": "BUY", "type": "limit", "quantity": 0.01, "price": 80000},
             "order_id": "991", "state": "submitted", **who},
            {"kind": "trade", "backend": "tradeyar", "method": "POST", "path": "trade/positions/close", "status": 422,
             "fields": {"symbol": "ETHUSDT", "side": "sell", "quantity": 1}, "detail": "margin too low", **who},
            {"kind": "trade", "backend": "tradeyar", "method": "DELETE", "path": "venues/lbank", "status": 200, "fields": {}, **who},
            {"kind": "signin_failed", "backend": "tradeyar", "email_hash": admin.email_key(PANEL_TOKEN, "Sara@Example.com"),
             **self.relay_identity()},
            {"kind": "bogus", **who},
        )
        account = self.store.q("SELECT * FROM web_accounts WHERE account='tradeyar:7'")[0]
        self.assertEqual((account["sign_ins"], account["last_browser"], account["last_country"]), (1, "Firefox", "DE"))
        trades = self.store.q("SELECT * FROM web_trades ORDER BY id")
        self.assertEqual([(t["action"], t["symbol"], t["side"], t["ok"]) for t in trades],
                         [("order", "BTCUSDT", "buy", 1), ("close", "ETHUSDT", "sell", 0), ("venue_disconnect", None, None, 1)])
        self.assertEqual(trades[0]["order_id"], "991")
        self.assertEqual(trades[2]["venue"], "lbank")
        security = await (await self.panel("GET", "security")).json()
        kinds = [item["kind"] for item in security["items"]]
        self.assertIn("signin", kinds)
        self.assertIn("signin_failed", kinds)
        self.assertEqual(security["failed_30d"], 1)
        self.assertEqual(security["devices"][0]["browser"], "Firefox")
        mine = await (await self.panel("GET", "trades?status=failed")).json()
        self.assertEqual([t["symbol"] for t in mine["items"]], ["ETHUSDT"])
        theirs = await (await self.panel("GET", "trades", who=identity(ALI))).json()
        self.assertEqual(theirs["items"], [])
        activity = await (await self.panel("GET", "activity")).json()
        self.assertNotIn("ip_hash", activity["items"][0])

    async def test_one_sign_in_on_both_platforms_is_one_sign_in(self):
        both = self.relay_identity(SARA, SARA_FX)
        at = admin.now()
        await self.relay_events({"kind": "signin", "backend": "coineprofx", "method": "password", "at": at, **both},
                                {"kind": "signin", "backend": "tradeyar", "method": "password", "at": at + 1, **both})
        self.assertEqual(self.store.one("SELECT sign_ins FROM web_accounts WHERE account='tradeyar:7'"), 1)
        self.assertEqual(self.store.one("SELECT COUNT(*) FROM web_events WHERE kind='signin'"), 1)
        elsewhere = {**both, "sid": "s9"}
        await self.relay_events({"kind": "signin", "backend": "tradeyar", "method": "password", "at": at + 2, **elsewhere},
                                {"kind": "signin", "backend": "tradeyar", "method": "password", "at": at + 3600, **both})
        self.assertEqual(self.store.one("SELECT sign_ins FROM web_accounts WHERE account='tradeyar:7'"), 3)

    async def test_client_events_are_small_batched_and_limited(self):
        events = [{"kind": "error", "name": "TypeError: x is undefined", "detail": {"stack": "at a.js:1", "version": "6.0", "big": "y" * 5000}},
                  {"kind": "feature", "name": "indicator.add.rsi"}, {"kind": "page", "name": "panel/security"},
                  {"kind": "signin", "name": "forged"}, {"kind": "feature", "name": ""}]
        stored = await (await self.panel("POST", "event", {"events": events})).json()
        self.assertEqual(stored["stored"], 3)
        self.assertEqual(self.store.one("SELECT COUNT(*) FROM web_events WHERE kind='signin'"), 0)
        detail = json.loads(self.store.one("SELECT detail FROM web_events WHERE kind='error'"))
        self.assertLessEqual(len(detail["big"]), 1500)
        self.assertEqual((await self.panel("POST", "event", {"events": [{"kind": "page", "name": "x"}] * 51})).status, 400)
        self.assertEqual((await self.panel("POST", "event", raw=b'{"events":[' + b'1,' * 40000 + b'1]}')).status, 413)
        statuses = [(await self.panel("POST", "event", {"events": [{"kind": "page", "name": "x"}]})).status for _ in range(31)]
        self.assertEqual(statuses[-1], 429)

    # ── The cloud workspace ─────────────────────────────────────────────────────────────────────

    async def test_workspace_versions_conflicts_history_and_restore(self):
        first = await (await self.panel("PUT", "workspace/layouts", {"data": {"keys": {"pc.layouts.v1": "1"}}, "base_version": 0})).json()
        self.assertEqual(first["version"], 1)
        stale = await self.panel("PUT", "workspace/layouts", {"data": {"keys": {}}, "base_version": 0})
        self.assertEqual((stale.status, (await stale.json())["version"]), (409, 1))
        for n in range(2, 6):
            await self.panel("PUT", "workspace/layouts", {"data": {"keys": {"pc.layouts.v1": str(n)}}, "base_version": n - 1})
        versions = await (await self.panel("GET", "workspace/layouts/versions")).json()
        self.assertEqual([v["version"] for v in versions["versions"]], [5, 4, 3])  # panel_history=3
        old = await (await self.panel("GET", "workspace/layouts/versions/3")).json()
        self.assertEqual(old["data"]["keys"]["pc.layouts.v1"], "3")
        restored = await (await self.panel("POST", "workspace/layouts/restore", {"version": 3})).json()
        self.assertEqual(restored["version"], 6)
        current = await (await self.panel("GET", "workspace/layouts")).json()
        self.assertEqual((current["version"], current["data"]["keys"]["pc.layouts.v1"]), (6, "3"))
        self.assertEqual((await self.panel("POST", "workspace/layouts/restore", {"version": 1})).status, 404)
        forced = await self.panel("PUT", "workspace/layouts", {"data": {"keys": {}}, "base_version": 1, "force": True})
        self.assertEqual(forced.status, 200)
        self.assertEqual((await self.panel("GET", "workspace/secrets")).status, 404)
        self.assertEqual((await self.panel("PUT", "workspace/settings", {"data": "text"})).status, 400)
        big = {"data": {"keys": {"pc.prefs.v1": "x" * (256 * 1024)}}, "base_version": 0}
        self.assertEqual((await self.panel("PUT", "workspace/settings", big)).status, 413)
        drawings = {"data": {"keys": {"pc.drawings.v1.BTCUSDT": "x" * (512 * 1024)}}, "base_version": 0}
        self.assertEqual((await self.panel("PUT", "workspace/drawings", drawings)).status, 200)
        # Another reader sees none of it.
        theirs = await (await self.panel("GET", "workspace/layouts", who=identity(ALI))).json()
        self.assertIsNone(theirs["data"])
        listing = await (await self.panel("GET", "workspace")).json()
        self.assertEqual({k["kind"]: k["version"] for k in listing["kinds"]}["layouts"], 7)

    async def test_profile_and_prefs_keep_only_what_they_know(self):
        saved = await (await self.panel("PUT", "profile", {"display_name": "S" * 99, "tagline": "سلام", "avatar": "data:image/svg+xml;base64,PHN2Zz4="})).json()
        self.assertEqual(saved.get("error"), "avatar")
        saved = await (await self.panel("PUT", "profile", {"display_name": "S" * 99, "avatar": "🦁", "admin": True})).json()
        self.assertEqual(len(saved["profile"]["display_name"]), 40)
        self.assertNotIn("admin", saved["profile"])
        prefs = await (await self.panel("PUT", "prefs", {"autosync": 1, "sync_kinds": ["layouts", "evil"], "role": "owner"})).json()
        self.assertEqual(prefs["prefs"], {"autosync": True, "sync_kinds": ["layouts"]})

    # ── Inbox and tickets ───────────────────────────────────────────────────────────────────────

    async def test_messages_tickets_and_attachments(self):
        await self.panel("GET", "me")
        await self.panel("GET", "me", who=identity(ALI))
        csrf = await self.sign_in()
        headers = {"X-CSRF-Token": csrf}
        self.assertEqual((await self.client.post("/admin/api/web/messages", json={"title": "t", "body": "b"})).status, 403)
        await self.client.post("/admin/api/web/messages", json={"account": None, "title": "به همه", "body": "سلام"}, headers=headers)
        one = await (await self.client.post("/admin/api/web/messages", json={"account": "tradeyar:7", "title": "فقط سارا",
                                                                             "body": "متن", "kind": "offer"}, headers=headers)).json()
        bad = await self.client.post("/admin/api/web/messages", json={"account": "tradeyar:7", "title": "x", "body": "y",
                                                                      "link": "javascript:alert(1)"}, headers=headers)
        self.assertEqual(bad.status, 400)
        inbox = await (await self.panel("GET", "inbox")).json()
        self.assertEqual((len(inbox["items"]), inbox["unread"]), (2, 2))
        ali = await (await self.panel("GET", "inbox", who=identity(ALI))).json()
        self.assertEqual([m["title"] for m in ali["items"]], ["به همه"])
        self.assertEqual((await self.panel("POST", f"inbox/{one['id']}/read", who=identity(ALI))).status, 404)
        await self.panel("POST", f"inbox/{one['id']}/read")
        self.assertEqual((await (await self.panel("GET", "inbox")).json())["unread"], 1)
        await self.panel("POST", "inbox/read-all")
        self.assertEqual((await (await self.panel("GET", "inbox")).json())["unread"], 0)
        await self.panel("DELETE", f"inbox/{one['id']}")
        self.assertEqual(len((await (await self.panel("GET", "inbox")).json())["items"]), 1)

        refused = await self.panel("POST", "tickets", {"subject": "چارت", "body": "تصویر", "attachments": [
            {"name": "x.svg", "data": base64.b64encode(b"<svg onload=alert(1)>").decode()}]})
        self.assertEqual((refused.status, (await refused.json())["error"]), (400, "attachment_type"))
        huge = base64.b64encode(b"\x89PNG\r\n\x1a\n" + b"0" * (301 * 1024)).decode()
        self.assertEqual((await self.panel("POST", "tickets", {"subject": "big", "body": "too big", "attachments": [{"data": huge}]})).status, 413)
        made = await self.panel("POST", "tickets", {"subject": "مشکل چارت", "category": "bug", "body": "چارت باز نمی‌شود",
                                                    "attachments": [{"name": "shot.png", "data": "data:image/png;base64," + PNG}]})
        self.assertEqual(made.status, 201)
        ticket_id = (await made.json())["id"]
        thread = await (await self.panel("GET", f"tickets/{ticket_id}")).json()
        attachment = thread["messages"][0]["attachments"][0]
        self.assertEqual(attachment["type"], "image/png")
        image = await self.panel("GET", f"tickets/{ticket_id}/attachments/{attachment['id']}")
        self.assertEqual((image.status, image.headers["X-Content-Type-Options"]), (200, "nosniff"))
        self.assertIn("sandbox", image.headers["Content-Security-Policy"])
        self.assertEqual((await self.panel("GET", f"tickets/{ticket_id}", who=identity(ALI))).status, 404)
        self.assertEqual((await self.panel("GET", f"tickets/{ticket_id}/attachments/{attachment['id']}", who=identity(ALI))).status, 404)

        waiting = await (await self.client.get("/admin/api/web/tickets?status=waiting")).json()
        self.assertEqual([t["id"] for t in waiting["items"]], [ticket_id])
        owner_view = await (await self.client.get(f"/admin/api/web/tickets/{ticket_id}")).json()
        self.assertEqual(owner_view["account"]["email"], "sara@example.com")
        self.assertEqual((await self.client.get(f"/admin/api/web/attachments/{attachment['id']}")).status, 200)
        replied = await self.client.post(f"/admin/api/web/tickets/{ticket_id}/reply", json={"body": "درست شد"}, headers=headers)
        self.assertEqual((await replied.json())["status"], "answered")
        mine = await (await self.panel("GET", "tickets")).json()
        self.assertTrue(mine["items"][0]["unread_user"])
        self.assertEqual((await (await self.panel("GET", "inbox")).json())["items"][0]["kind"], "ticket")
        await self.panel("POST", f"tickets/{ticket_id}/messages", {"body": "ممنون"})
        self.assertEqual(self.store.one("SELECT status FROM web_tickets WHERE id=?", (ticket_id,)), "open")
        await self.panel("POST", f"tickets/{ticket_id}/close")
        self.assertEqual(self.store.one("SELECT status FROM web_tickets WHERE id=?", (ticket_id,)), "closed")
        # Five new tickets an hour: the one above and four more.
        statuses = [(await self.panel("POST", "tickets", {"subject": f"subject {n}", "body": "body text"})).status for n in range(5)]
        self.assertEqual(statuses, [201, 201, 201, 201, 429])
        actions = [r["action"] for r in self.store.q("SELECT action FROM audit")]
        self.assertIn("web.message", actions)
        self.assertIn("web.ticket_reply", actions)

    # ── Export and erase ────────────────────────────────────────────────────────────────────────

    async def test_export_holds_everything_and_erase_removes_it(self):
        await self.relay_events({"kind": "trade", "backend": "tradeyar", "method": "POST", "path": "trade/orders", "status": 200,
                                 "fields": {"symbol": "BTCUSDT"}, **self.relay_identity(SARA)})
        await self.panel("PUT", "workspace/settings", {"data": {"keys": {"pc.prefs.v1": "{}"}}, "base_version": 0})
        await self.panel("POST", "tickets", {"subject": "سوال", "body": "یک سوال دارم",
                                             "attachments": [{"name": "a.png", "data": PNG}]})
        export = await self.panel("GET", "export")
        self.assertIn("attachment", export.headers["Content-Disposition"])
        data = await export.json()
        self.assertEqual(data["account"], "tradeyar:7")
        self.assertEqual(data["workspace"][0]["kind"], "settings")
        self.assertEqual(data["tickets"][0]["messages"][0]["attachments"][0]["data"], PNG)
        self.assertEqual(data["trades"][0]["symbol"], "BTCUSDT")
        self.assertEqual((await self.panel("DELETE", "data", {})).status, 400)
        erased = await (await self.panel("DELETE", "data", {"confirm": True})).json()
        self.assertEqual(erased["erased"]["web_trades_anonymised"], 1)
        for table in ("web_workspace", "web_tickets", "web_attachments", "web_ticket_messages", "web_events"):
            self.assertEqual(self.store.one(f"SELECT COUNT(*) FROM {table}"), 0, table)
        self.assertIsNone(self.store.one("SELECT account FROM web_trades"))
        self.assertEqual(self.store.one("SELECT action FROM audit ORDER BY id DESC LIMIT 1"), "web.erase")

    # ── «کاربران وب» ────────────────────────────────────────────────────────────────────────────

    async def test_owner_views_are_closed_until_signed_in(self):
        for path in ("web/overview", "web/accounts", "web/accounts.csv", "web/accounts/tradeyar:7", "web/trades",
                     "web/trades.csv", "web/tickets", "web/tickets/1", "web/messages", "web/errors", "web/attachments/1",
                     "web/accounts/tradeyar:7/timeline"):
            self.assertEqual((await self.client.get(f"/admin/api/{path}")).status, 401, path)

    async def test_owner_overview_accounts_and_detail(self):
        await self.relay_events({"kind": "signin", "backend": "tradeyar", "method": "password", **self.relay_identity(SARA, SARA_FX)},
                                {"kind": "trade", "backend": "tradeyar", "method": "POST", "path": "trade/orders", "status": 200,
                                 "fields": {"symbol": "BTCUSDT", "side": "buy"}, **self.relay_identity(SARA)},
                                {"kind": "signin", "backend": "tradeyar", "method": "password", **self.relay_identity(ALI)})
        await self.panel("POST", "event", {"events": [{"kind": "error", "name": "RangeError: bad", "detail": {"version": "6.0"}}]})
        self.relay_sessions[:] = [{"sid": "s1", "accounts": ["tradeyar:7", "coineprofx:45"], "last_seen": admin.now(),
                                   "created": admin.now() - 60, "browser": "Chrome", "os": "Android", "device": "phone", "country": "IR"},
                                  {"sid": "s2", "accounts": ["tradeyar:8"], "last_seen": admin.now() - 3600,
                                   "created": admin.now() - 7200, "browser": "Safari", "os": "iOS", "device": "phone", "country": "IR"}]
        csrf = await self.sign_in()
        overview = await (await self.client.get("/admin/api/web/overview")).json()
        self.assertTrue(overview["relay"]["ok"])
        self.assertEqual((overview["online_now"], overview["live_sessions"], overview["guests"]), (1, 2, 3))
        # «Online now» names the console's account and its e-mail, not only the platform aliases.
        self.assertEqual((overview["online"][0]["account"], overview["online"][0]["email"]), ("tradeyar:7", "sara@example.com"))
        self.assertEqual(overview["kpis"]["accounts"], 2)
        self.assertEqual(overview["series"]["signins"][-1], 2)
        self.assertEqual(overview["kpis"]["errors_24h"], 1)
        self.assertEqual(overview["browsers"][0]["label"], "Chrome")
        listing = await (await self.client.get("/admin/api/web/accounts?filter=online")).json()
        self.assertEqual([a["account"] for a in listing["accounts"]], ["tradeyar:7"])
        self.assertTrue(listing["accounts"][0]["online"])
        self.assertEqual(listing["accounts"][0]["trades_30d"], 1)
        traders = await (await self.client.get("/admin/api/web/accounts?filter=traders")).json()
        self.assertEqual(traders["total"], 1)
        found = await (await self.client.get("/admin/api/web/accounts?q=coineprofx:45")).json()
        self.assertEqual(found["total"], 1)
        csv_text = (await (await self.client.get("/admin/api/web/accounts.csv")).read()).decode("utf-8-sig")
        self.assertTrue(csv_text.startswith("account,aliases,email"))
        detail = await (await self.client.get("/admin/api/web/accounts/tradeyar:7")).json()
        self.assertEqual([s["sid"] for s in detail["sessions"]], ["s1"])
        bridge = next(b for b in detail["bridges"] if b["alias"] == "tradeyar:7")
        self.assertEqual((bridge["bridge_id"], bridge["membership"]["vip"]), (501, True))
        self.assertEqual(bridge["premium_until"], "2030-01-01T00:00:00+00:00")
        self.assertTrue(detail["account"]["vip"])
        vip = await (await self.client.get("/admin/api/web/accounts?filter=premium")).json()
        self.assertEqual([a["account"] for a in vip["accounts"]], ["tradeyar:7"])
        self.assertEqual((await self.client.get("/admin/api/web/accounts/tradeyar:999")).status, 404)
        timeline = await (await self.client.get("/admin/api/web/accounts/tradeyar:7/timeline")).json()
        self.assertEqual({i["kind"] for i in timeline["items"]}, {"signin", "trade", "error"})
        self.assertEqual((await self.client.post("/admin/api/web/accounts/tradeyar:7/revoke", json={})).status, 403)
        revoked = await (await self.client.post("/admin/api/web/accounts/tradeyar:7/revoke", json={"sid": "s1"},
                                                headers={"X-CSRF-Token": csrf})).json()
        self.assertEqual(revoked["revoked"], 1)
        self.assertEqual(self.relay_calls[0][1]["operator"], USER)
        self.assertEqual(self.relay_calls[0][2]["X-Panel-Token"], PANEL_TOKEN)
        await self.client.put("/admin/api/web/accounts/tradeyar:7/note", json={"note": "VIP از تلگرام"}, headers={"X-CSRF-Token": csrf})
        self.assertEqual(self.store.one("SELECT note FROM web_accounts WHERE account='tradeyar:7'"), "VIP از تلگرام")
        errors = await (await self.client.get("/admin/api/web/errors")).json()
        self.assertEqual((errors["groups"][0]["name"], errors["groups"][0]["count"]), ("RangeError: bad", 1))
        actions = {r["action"] for r in self.store.q("SELECT action FROM audit")}
        self.assertTrue({"web.revoke", "web.note", "web.export_accounts"} <= actions)

    async def test_owner_trade_audit_filters_and_csv(self):
        who = self.relay_identity(SARA)
        await self.relay_events(*[{"kind": "trade", "backend": "tradeyar", "method": "POST", "path": "trade/orders",
                                   "status": status, "fields": {"symbol": symbol, "side": side, "venue": "lbank"}, **who}
                                  for symbol, side, status in (("BTCUSDT", "buy", 200), ("ETHUSDT", "sell", 400), ("BTCUSDT", "sell", 200))])
        await self.sign_in()
        everything = await (await self.client.get("/admin/api/web/trades")).json()
        self.assertEqual((everything["total"], everything["ok"], everything["accounts"]), (3, 2, 1))
        btc = await (await self.client.get("/admin/api/web/trades?symbol=btcusdt&side=sell")).json()
        self.assertEqual(btc["total"], 1)
        failed = await (await self.client.get("/admin/api/web/trades?status=failed&venue=lbank")).json()
        self.assertEqual([t["symbol"] for t in failed["items"]], ["ETHUSDT"])
        dated = await (await self.client.get(f"/admin/api/web/trades?from={admin.now() + 60}")).json()
        self.assertEqual(dated["total"], 0)
        csv_text = (await (await self.client.get("/admin/api/web/trades.csv?symbol=ETHUSDT")).read()).decode("utf-8-sig")
        self.assertEqual(len(csv_text.strip().splitlines()), 2)
        self.assertIn("web.export_trades", {r["action"] for r in self.store.q("SELECT action FROM audit")})

    async def test_owner_can_view_and_restore_a_workspace_version(self):
        for n in (1, 2):
            await self.panel("PUT", "workspace/drawings", {"data": {"keys": {"pc.drawings.v1.BTCUSDT": str(n)}}, "base_version": n - 1})
        csrf = await self.sign_in()
        detail = await (await self.client.get("/admin/api/web/accounts/tradeyar:7")).json()
        self.assertEqual({w["kind"]: w["version"] for w in detail["workspace"]}["drawings"], 2)
        view = await self.client.get("/admin/api/web/accounts/tradeyar:7/workspace/drawings/1")
        self.assertEqual((await view.json())["keys"]["pc.drawings.v1.BTCUSDT"], "1")
        restored = await (await self.client.post("/admin/api/web/accounts/tradeyar:7/workspace/drawings/restore", json={"version": 1},
                                                 headers={"X-CSRF-Token": csrf})).json()
        self.assertEqual(restored["version"], 3)
        current = await (await self.panel("GET", "workspace/drawings")).json()
        self.assertEqual(current["data"]["keys"]["pc.drawings.v1.BTCUSDT"], "1")
        actions = {r["action"] for r in self.store.q("SELECT action FROM audit")}
        self.assertTrue({"web.workspace_view", "web.workspace_restore"} <= actions)

    async def test_old_lines_are_pruned(self):
        old = admin.now() - 400 * 86400
        self.store.x("INSERT INTO web_events(account, at, kind, name) VALUES ('tradeyar:7', ?, 'page', 'x')", (old,))
        self.store.x("INSERT INTO web_events(account, at, kind, name) VALUES ('tradeyar:7', ?, 'signin', 'x')", (old,))
        self.store.x("INSERT INTO web_trades(account, at, action) VALUES ('tradeyar:7', ?, 'order')", (old,))
        self.assertEqual(admin.prune_panel(self.store), 2)
        self.assertEqual(self.store.one("SELECT COUNT(*) FROM web_trades"), 1)

    def test_migrations_run_once_and_in_order(self):
        with tempfile.TemporaryDirectory() as folder:
            db = str(Path(folder) / "a.sqlite3")
            admin.Store(db)
            store = admin.Store(db)
            self.assertEqual(store.meta("panel_schema"), str(len(admin.PANEL_MIGRATIONS)))


class CommandLineTest(unittest.TestCase):
    def test_set_password_creates_the_account_from_stdin(self):
        with tempfile.TemporaryDirectory() as folder:
            db = str(Path(folder) / "admin.sqlite3")
            with mock.patch.dict(os.environ, {"ADMIN_DB": db}), mock.patch.object(sys, "stdin", io.StringIO("a-long-Passphrase-1\n")), \
                    mock.patch.object(admin.Config, "__init__", _config_with(db)):
                self.assertEqual(admin.set_password_cli(["Owner", "--stdin"]), 0)
            store = admin.Store(db)
            row = store.q("SELECT pw_hash FROM users WHERE username='owner'")[0]
            self.assertTrue(admin.verify_password("a-long-Passphrase-1", row["pw_hash"]))
            self.assertNotIn("a-long-Passphrase-1", Path(db).read_bytes().decode("latin-1"))

    def test_set_password_refuses_a_weak_one(self):
        with mock.patch.object(sys, "stdin", io.StringIO("short\n")):
            self.assertEqual(admin.set_password_cli(["owner", "--stdin"]), 1)


def _config_with(db: str):
    original = admin.Config.__init__

    def init(self, *args, **kwargs):
        original(self, *args, **kwargs)
        self.db_path = db
    return init


if __name__ == "__main__":
    unittest.main()
