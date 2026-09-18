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

**What the page does not have**, at first: the guest gateway, the KYC flow, the account pages, push notifications. Those stay on the phone until the account API is behind the gateway (§4 below). Copy trading is not on that list because it is not in the product at all any more (run Ψ) — the feature, its screen and its two modules are deleted, and the web has nothing to inherit.

## 3a. Run Ω's four surfaces, and where each of them already is (run Ω5)

The brief's §Ω5 item 2 asks this document to say how the Signal Layer, Confidence, رصد and the Arena
map to the web terminal. The answer is shorter than expected, and deliberately so: **three of the
four are already in `:chart-core`**, which is the module with no Android on it, and the fourth is a
Compose surface over them.

| surface | where it lives | what the web has to add |
| --- | --- | --- |
| `SignalSpec` — what a study is saying | `:chart-core`, `commonMain` | nothing. Four rules over eighty-three built-ins, pure arithmetic, both languages in the file |
| `ConfidenceEngine` — the base rate and its sample size | `:chart-core`, `commonMain` | nothing. A walk over the bars the chart already holds |
| `ChartSignalEngine` / `ChartSignalLayer` — the chart's whole answer | `feature:chart` | **a move.** It is a bag of `:chart-core` types (`SignalRead`, `SetupScore`, `ConfidenceReport`) with no Android in it, and it sits in the feature module only because that is where it was written. Moving it down is a file move and an import change, and the day the web target exists is the day to do it |
| `RasadCoach` — the three sentences | `:chart-core`, `commonMain` | nothing. It was written there for exactly this; it takes a `CandleSeries`, the reads and the score, and returns strings |
| `Arena` — the daily challenge and the score | `:chart-core`, `commonMain` | nothing. The daily pick is a written-out multiplicative hash rather than a platform `Random`, **because of this document**: a challenge that is «the same for everybody» has to be the same on a phone, in a JVM test and in a browser, and only arithmetic spelled out in common code is |
| `ExplainSheetBody`, `RasadSheetBody`, `ArenaResultBody` | `feature:chart`, Compose | the §3 move. All three are *bodies* rather than sheets — written that way so a tablet could dock them — so each is already a plain composable over values, with `stringResource` as its only Android dependency |
| `MarketMood` — the board's lean | `:core:marketdata` | a move, or a copy of forty lines. It reads `MarketTicker` and returns counts; the web's own feed layer will have its own ticker type, and the honest answer is that this one function goes wherever that type does |
| `ShareCard` — the 1080 × 1080 square | `:core:designsystem`, Android `Canvas` | **a second implementation.** This is the one surface in run Ω that does not cross: it draws with `android.graphics` and `StaticLayout` because it has to work from a background thread with a context and no composition. On the web the same card is a `<canvas>` or an offscreen Compose render, and `ShareCardContent` — which is a data class of strings and a bitmap — is the contract both sides fill |

**What this run did *not* do, so the web stays possible:** nothing in `RasadCoach` or `Arena` calls a
clock, a locale, a formatter or a random. Every input is a parameter, including the date. That is
also why both are testable to the degree they are — `ArenaTest`'s «the same day gives the same
challenge» is, read another way, the web-parity test written a year early.

## 3b. Run Σ's surfaces, and what a browser would need for each (run Σ, S8)

Σ1 and Σ3 added a language, a repair table, a library, a document format, a share link and a return
loop. The pattern held: everything that decides is in `commonMain`, and what is left is a screen.

| surface | where it lives | what the web has to add |
| --- | --- | --- |
| `ScriptPaste` — dialect detection and the twenty repairs | `:namascript`, `commonMain` | nothing. Regular expressions over a string, masked for comments and literals, with both languages in the file |
| `PineTranslator` | `:namascript`, `commonMain` | nothing |
| `ScriptTemplates` — twelve, matched on keywords | `:namascript`, `commonMain` | nothing. The digit reader handles Persian and Latin figures in shared code |
| `ScriptPromptKit` — the prompt, versioned | `:namascript`, `commonMain` | nothing. It is generated from `ScriptReference`, so it cannot go stale on either platform |
| `ScriptLibrary` — sixty-one strategies | `:namascript`, `commonMain` | nothing. One table, both languages, identical code token for token |
| `ScriptDocument` / `ScriptFile` / `ScriptLink` | `:namascript`, `commonMain` | nothing for the format. The **link** needs the same thing the phone is waiting for: a service behind `pro-chart.com/s/<id>`. On the web that address is not a deep link at all — it is a page, and it is the natural place for the community surface S7 describes |
| `ReturnLoop` — «since your last visit», the challenge, the streak | `:core:common`, pure Kotlin | nothing. The challenge's walk is written-out arithmetic over the epoch day for the same reason the Arena's is: «the same for everybody» has to mean the same in a browser |
| `ReaderArchive` / `ReaderArchiveFile` | `:core:common`, pure Kotlin | nothing for the format; a file picker for the import. A browser's is `<input type=file>`, and the export is a `Blob` download rather than the clipboard |
| `LastVisitStore` | `:core:datastore`, Android `DataStore` | **a second implementation** — two longs in `localStorage`, or in the account once there is one. This is the only piece of Σ3 that does not cross, and it is nine lines |
| `ScriptScreen`, `ScriptPasteBody`, `ScriptPromptBody`, `MinePanel` | `feature:script`, Compose | the §3 move. All four are bodies over values; the editor's own text field is the one piece with real platform behaviour (selection, an IME, a soft keyboard), and a browser's `<textarea>` behaves differently enough that it is worth saying so here rather than discovering it |
| The two Home cards | `feature:home`, Compose | the §3 move, and nothing else: they render a `SinceLastVisit` and a `DailyChallenge` and call back with a `ChallengeSurface` |

**The rule this run kept:** a decision goes in shared code and a screen draws it. `ReturnLoop`
returns `null` rather than a card; `ScriptPaste` returns a list of fixes rather than a sheet; the
library returns source rather than a chart. That is not web-mindedness for its own sake — it is what
made every one of them testable off a device, which is why Σ has gates at all.

**What the width work changed, and why the web gets it free.** S8 capped the dashboard column at
`CONTENT_MAX_WIDTH` and centred it, because a list of cards across a 1973 dp panel puts a row's name
at one edge and its number at the other. A browser window is the same problem with a mouse in it,
and the app's rule — decide from the space given, never from «is this a phone» — means a maximised
desktop window gets the same treatment without a line of new code.

## 4. What the server side needs

**`SERVER.md` is the buildable version of this section** (run Ψ): the machine to buy, the services
on it, every route the relay carries with its upstream and its cache policy, the three documents it
owns, and the seven-step order to bring it up with a proof for each step. This table stays as the
*why*; that document is the *what*.

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
