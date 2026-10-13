"""The owner console against an in-memory database: `python3 -m unittest test_admin`."""
from __future__ import annotations

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

    async def test_console_page_has_no_inline_styles_or_scripts(self):
        static = Path(admin.HERE) / "static"
        html = (static / "index.html").read_text(encoding="utf-8")
        self.assertNotIn("style=", html)
        self.assertNotIn("<script>", html)
        self.assertNotIn('style="', (static / "app.js").read_text(encoding="utf-8"))

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
