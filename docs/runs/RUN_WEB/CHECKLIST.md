# RUN WEB — checklist

The web version at `/terminal/`. It started as the phone's chart alone (W1b, 5.8.x). **Since 5.9.0
it is the whole phone app**: the same Kotlin sources for every screen, compiled for the browser.
Every row carries a state, its evidence and a frame, or says why a still frame cannot show it and
names what can.

States: ✅ done · ❌ not done, or narrowed (the row says exactly how) · ⏳ owed to the server or the owner.

---

## The approach — one app, not a second one

| Item | State | Evidence | Frame |
|---|---|---|---|
| **The phone's sources, not a port** | ✅ | `web/build.gradle.kts` `sharedSources` lists `app`, all 37 `core/*` modules, every `feature/*` module and `chart/ui`. `web/tools/share_sources.py` copies them into `web/build/generated/shared` and makes only mechanical changes: a serializer for every wire class, a `<Name>Web` class for every Retrofit interface, and the dependency graph Hilt builds (`WebGraph.kt`) generated from `AppModule` and the `@Inject` constructors. **606 files, 141 wire classes, 31 services** on this tree | — **a build step.** Its log line reads `shared 606 files, 141 wire classes, 31 services` |
| **What stands in for Android** | ✅ | `web/src/shims/kotlin`: the Android, AndroidX, OkHttp, Retrofit, Room, DataStore, WorkManager, Gson and JVM APIs the sources call, under the same names. Each one is backed by the browser: `fetch` for HTTP, `WebSocket`, `localStorage` for preferences, files and the database, WebAuthn for biometrics, `getUserMedia` for the camera, the Notification API, the share sheet and the clipboard | — **source.** One file per package |
| **Only a few files replaced** | ✅ | `replacedFiles`: `MainActivity`, `CoineProApplication`, the two widget classes and their configuration activities, the widget snapshot bridge, `GoogleSignIn`, the chart's three platform files and the two share-image files. Each browser version is in `web/src/wasmJsMain` and says what the page does in their place | — **a list**, in `web/build.gradle.kts` |
| **The phone did not change** | ✅ | No file under `app/`, `core/`, `feature/` or `chart/` changed in this run. `./gradlew testDebugUnitTest` (every golden) and `./gradlew :app:assembleRelease` green | — **the goldens are the frames** |

## Every screen, in the browser

Served locally at `/terminal/` with `/api/*` relayed to `pro-chart.com` and `/up/*` passed to the
two backends (the passthrough `SERVER.md` §4.12 asks for), driven by Chromium at 390 × 844 unless
the frame says otherwise.

| Screen | State | Frame |
|---|---|---|
| First run: launch, welcome, start preferences, reader question | ✅ | shown on first visit; kept out of the set to spare the reader four near-empty frames |
| Watchlist — live prices, sparklines, logos, toolbar | ✅ | `web-app-watchlist-fa-phone.png` |
| Chart tab — the chart owns the screen, as on the phone in portrait | ✅ | `web-app-chart-fa-phone.png` |
| Rasad | ✅ | `web-app-rasad-fa-phone.png` |
| Ideas: signals, track record | ✅ | `web-app-ideas-fa-phone.png` |
| Community | ✅ | `web-app-community-fa-phone.png`. The forum is empty because the server's feed is `{"posts":[]}` today, and the phone shows the same |
| Menu, every row | ✅ | `web-app-menu-fa-phone.png` |
| Search, screener, heatmap, markets, explore | ✅ | `web-app-{search,screener,heatmap,markets,explore}-fa-phone.png` |
| News with photos, calendar | ✅ | `web-app-news-fa-phone.png`, `web-app-calendar-fa-phone.png`. The photos come through `/api/img` (`SERVER.md` §4.13, `web/relay`); the frame was taken behind the relay. Without it each card says «تصویر نیامد», the phone's own words for a photo that did not load |
| Paper trading, journal | ✅ | `web-app-paper-fa-phone.png`, `web-app-journal-fa-phone.png` |
| Chart studio, NamaScript, alerts, trader's toolbox | ✅ | `web-app-{studio,backtest,alerts,toolbox}-fa-phone.png` |
| Security and release notes, profile, notification settings | ✅ | `web-app-{security,profile,notif}-fa-phone.png` |
| Portfolio, connections, setup builder, signals, subscription, identity check, activity | ✅ **for a guest: the sign-in screen**, the phone's own gate | `web-app-portfolio-fa-phone.png` |
| English, light theme | ✅ | `web-app-menu-en-light-phone.png`, `web-app-rasad-en-light-phone.png` |
| Wide window: navigation rail, list-detail | ✅ | `web-app-watchlist-en-light-desktop.png` |
| Tablet grid, four charts | ✅ | `web-app-tablet-grid-fa.png` |
| Installable, with the phone's launcher icon | ✅ | `manifest.webmanifest`, icons from `scripts/design/build-web-icons.py`, `sw.js`. Chromium's installability check reports nothing but «in-incognito», which is the test browser's own mode — no **frame**: a check result, printed by the run in `REPORT.md` §7 |
| Opens with no network | ✅ | `sw.js` keeps the last good copy of each bundle file. `web-app-offline-start-fa-phone.png`: reloaded offline, the app opens with the phone's own offline banner. A guest's prices are blank there on the phone too — the guest feed is not cached on either |

## Faults found and fixed in the browser

| Fault | Fixed by |
|---|---|
| Three watchlist toolbar icons and a caret stayed blank | `Drawables.ensure`: the fetch belongs to the page now, not to the composable that first asked. A row composed for one frame and then dropped used to cancel the fetch halfway, and the name stayed «loading» for the whole visit |
| Characters IRANYekanX does not have: `○` in the watchlist, and any `\uXXXX` escape | `web/tools/glyphs.json` maps them, `share_sources.py` also maps them when written as escapes, and `check_web_glyph_map` now reads every Kotlin string literal as well as the string tables |
| Publishers' photos: a page may not read another site's bytes | `WebRoutes.mapImage` → `/api/img?url=` (`SERVER.md` §4.13) |

## What a browser cannot do — not a gap in the port

| Phone feature | On the web |
|---|---|
| Home-screen widgets (markets, single symbol) | ❌ **no such thing on the web.** `Widgets.web.kt` keeps the calls and draws nothing |
| Picture-in-picture watch mode | ❌ a tab cannot shrink into its own floating window; `onKeepWatching = null`, so the hub offers no tile, as on a phone without the mode |
| Push while the app is closed (FCM) | ⏳ needs a Web Push key pair and a server sender; while the tab is open, the Notification API shows the same notifications |
| The launcher icon | ✅ the installed web app carries it (above) |
| Play Integrity | ❌ Android-only; the header is not sent, which the backends already accept from a phone without Play services |
| Background sync when closed | ❌ WorkManager's jobs run on timers while the tab is open |
| Opening the system notification settings | ❌ a page cannot; the phone's screen already says where the switch is |

## Publishing

| Item | State | Evidence |
|---|---|---|
| The bundle | ✅ | `./gradlew :web:terminalBundle` → `web/build/terminal/`. CI builds it and keeps it as the `pro-chart-terminal` artefact |
| On `pro-chart.com/terminal/` | ⏳ **owed to the server agent** | `BLOCKED.md` B1 |
| The relay for signed-in screens and news photos | ✅ **written and tested here**: `web/relay/` — the `/up/` passthrough with the token swap (§4.12) and `/api/img` (§4.13). Sixteen tests against fake backends, run in CI; driven locally behind the page, where all six news photos came through it | — **a service.** `python3 -m unittest test_relay` |
| …running on pro-chart.com | ✅ **since 2026-09-24**, per the server's own review: `/up/tradeyar/api/mobile/v1/auth/methods` 200, `/api/img` of a public photo 200, of `127.0.0.1` 400, the image's own 13 tests green inside the container, 26 MiB | — **a server check**, quoted in `REPORT.md` §8 |
| The bundle downloadable without a GitHub login | ✅ the `web-latest` release, replaced on every push to main | — **a download**, `web/relay/README.md` |
| Google sign-in from the page | ⏳ **owed to the owner** | `BLOCKED.md` B4 |
