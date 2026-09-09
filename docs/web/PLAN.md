# The web terminal at pro-chart.com — plan

§5 of the Pro Chart plan. There is no web build and none is started here; what this document does is fix the shape of the one that will exist, so the Android work between now and then keeps it possible rather than closing it off. The single guarantee the plan asks for is «mobile, tablet and web are one» — one chart engine, one language, one account, one set of layouts and watchlists — and the mechanism is the module cut made in 4.48.0 (`docs/engineering/MODULES.md`): the engine and the language have no platform in them, and this document says what the web adds on each side of that line.

## 1. What is ready today

| piece | state | proof |
| --- | --- | --- |
| `:chart-core` compiles without Android | KMP, `jvm()` + Android; `commonMain` has no `java.*`, no Compose | `chart/core/.../ArchitectureTest`, `:chart-core:jvmTest` in CI (`android-ci.yml`) |
| `:namascript` compiles without Android | same | `namascript/.../ArchitectureTest`, `:namascript:jvmTest` in CI |
| the platform seam is five functions | `ChartPlatform.kt`: `systemChartZone`, `currentTimeMillis`, `formatFixed`, `formatLocalMoment`, `ChartIcon` | `MODULES.md` §The platform seam |
| the engine matches an outside reference | 63 indicator series against a pandas/`ta` fixture; the web terminal's own `indicators.js` defects reported | `IndicatorReferenceTest`, `docs/backend/PROMPT_WEB_TERMINAL.md` |
| the language is specified and suited | `docs/namascript/SPEC.md`, 351 conformance scripts, generated FA/EN reference | `ConformanceSuiteTest`, `ReferenceDocsTest` |
| the brand host is in the app | `BrandConfig.WEB_HOST = pro-chart.com`, `WEB_URL`, `LEGAL_BASE_URL`, App Link `/reset` | `docs/release/DOMAINS.md`, `APP_LINKS.md` |
| the calendar does not need `java.time` | `CivilDate.ofEpochDay` in common code | `TimeScaleTest` |

## 2. The `wasmJs` target — checked, not enabled

The plan asks for a `wasmJs()` compile check «when the Kotlin/Wasm toolchain is stable in the repo». It is not in the repo: the Gradle cache holds no `kotlin-stdlib-wasm-js` and this environment builds offline, so a target cannot be added and compiled here. What adding it takes, so the day it is done is a morning and not a week:

1. `wasmJs { browser() }` in `chart/core/build.gradle.kts` and `namascript/build.gradle.kts`, beside `jvm()`.
2. A `wasmJsMain` source set with the five `actual`s. Four are one line each on top of the browser's `Date` and `Intl`; `formatLocalMoment` takes the `CivilDate` already in common code and a twenty-line pattern printer for the two patterns the object tree uses (`d MMM`, `HH:mm`), so `Intl` is not even needed for it.
3. `@JvmInline` on `ChartIcon` becomes `kotlin.jvm.JvmInline`, which is already what the import resolves to; nothing else in `commonMain` names a JVM type (the architecture tests are the proof, and they will be the proof for Wasm: add `kotlin.js.` to their forbidden list for the JVM source sets).
4. `:chart-core:compileKotlinWasmJs :namascript:compileKotlinWasmJs` in CI beside the two `jvmTest` jobs. No Node, no browser: a compile check, as the plan says.

The two things that could break the compile and were looked at: `String.format` — not used in common code (it is behind `formatFixed`); `Math.floorDiv` — replaced by Kotlin's `floorDiv` in 4.48.0.

## 3. The terminal

**Compose Multiplatform, Wasm target, one page.** `:chart-ui` today is an Android library because it draws on `androidx.compose.ui.graphics.Canvas` and reads Android resources for the icons. The move is: `:chart-ui` becomes KMP with `androidTarget` + `wasmJs`, `CoineProChart` stays as it is (the Canvas API is Compose's own, and identical on both), the `ChartIcon → drawable` map gets a second `actual` that returns a Compose resource. The tool rail, the pickers, the legend overlay follow the same rule: Compose-only code moves, Android-only code (`R.drawable`, `Context`, `Toast`) gets an `expect`.

**What the page has**, in the order the plan lists for the tablet, because a browser window is an Expanded window: the labelled rail, the chart with the tools column and the readings panel (`ChartWorkbench` as it is), the 1–8 layout grid, the object tree, the NamaScript studio split beside the chart, the watchlist as the list of a list-detail. The tablet layouts are the web layouts; that is why §4 was done before §5.

**What the page does not have**, at first: the guest gateway, the KYC flow, copy trading, the account pages, push notifications. Those stay on the phone until the account API is behind the gateway (§4 below).

## 4. What the server side needs

None of this exists on `pro-chart.com` today (the host does not answer — `DOMAINS.md`). In the order it has to be built:

| need | why | notes |
| --- | --- | --- |
| **DNS, TLS, static hosting + CDN** | the legal pages, `assetlinks.json`, and the terminal's bundle (a Wasm build is 5–15 MB; it has to be cached at the edge and served with `Content-Encoding: br`) | Let's Encrypt or Cloudflare; if Cloudflare, no certificate pinning on this host (`docs/security/PINNING.md`) |
| **API gateway** on `pro-chart.com/api/` | the browser cannot call `coineprofx.com` and `tradeyar.trade-future.ir` directly without CORS on both, and one origin means one cookie, one session, one rate limit | a reverse proxy that routes by prefix to the two backends; the phone keeps its direct base URLs until the gateway is proven, then moves (`COINEPRO_API_BASE_URL*` is per build) |
| **auth via the same API** | one account across phone, tablet, web | the token flow the app uses (`docs/AUTH_CONTRACT.md`) works from a browser as bearer headers; the gateway adds `HttpOnly` cookie issuance for the web so the token is not in `localStorage` |
| **WebSocket fan-out** | the phone holds one socket per venue; a thousand browser tabs cannot | one upstream socket per venue (`MARKET_DATA_CONTRACT.md`: LBank futures, Finnhub via CoinePro-FX), fanned out to subscribers by symbol; the app's snapshot-then-stream contract stays |
| **alert engine** | the phone evaluates local alerts on-device; a browser tab that is closed evaluates nothing | server-side evaluation of price/indicator alerts (the app already distinguishes venue alerts from local ones — `alerts_venue_server_note`), delivery by push (phone) and by the WebSocket (web) |
| **layouts, watchlists, drawings synced** | one workspace | the watchlist sync already exists (`WatchlistSyncController`, GET/PUT); the drawing sync store exists (`DrawingSyncStore`); the chart layout (`ChartLayoutStore`, `ChartWorkspaceStore`) needs the same GET/PUT pair — a JSON document per account, last-writer-wins with a version number, which is what the watchlist does |
| **`/.well-known/assetlinks.json`** | App Links for `pro-chart.com/reset` | `scripts/release/print-assetlinks.sh` prints it |
| **support** | the plan names a support e-mail; the product's support is a Telegram channel (`BrandConfig.SUPPORT_URL`) | unchanged; a `support@pro-chart.com` alias can forward there when the mail domain exists |

## 5. What stays true on the phone until then

- The API base URLs stay on the backends' own hosts (`DOMAINS.md` §What does not move).
- Every legal link points at `pro-chart.com/legal/…`; the documents are bundled so the reader is never stranded.
- No Android-only type enters `chart/core/src/commonMain` or `namascript/src/commonMain`; the two `ArchitectureTest`s fail the build if one does.
- The chart's rendering decisions (`ChartWorkbenchColumns`, `ChartLayoutPreset`, the window class) are measured against the space given, not against «is this a phone», so a browser window of any size gets the layout its width earns.

## 6. Open product decisions for the owner

1. **One origin or two.** The gateway is the clean answer; the alternative is CORS on both backends, which is a change on two servers the app does not own.
2. **Which backend owns the account** on the web (`docs/SERVER_ASK_ONE_ACCOUNT_TWO_BACKENDS.md` — the same question, not yet answered).
3. **Whether the web terminal is free, member-only, or the phone's guest tier.** The guest gateway is an Android construct; the browser's equivalent is a read-only page with no account.
