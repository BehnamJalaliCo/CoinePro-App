# RUN WEB — checklist

The web terminal, from `docs/web/TERMINAL_BUILD_PROMPT.md`. Every row carries a state, its evidence
and a frame — or says why a still frame cannot carry it and names what can.

States: ✅ done · ❌ not done, or narrowed (the row says exactly how) · ⏳ owed to the owner or the server.

---

## W1b — the browser target on `:chart-ui`

| Item | State | Evidence | Frame |
|---|---|---|---|
| **1** Compose Multiplatform and `wasmJs` on `:chart-ui` | ✅ | `chart/ui/build.gradle.kts` adds `wasmJs { browser() }` and the JetBrains Compose coordinates in `commonMain`; `:core:tokens` is the new module holding the design tokens in the same package, so no import in the app changed. Compose Multiplatform **1.9.0**, Material 3 **1.8.2**: `:app:dependencies releaseRuntimeClasspath` was resolved on `HEAD` and on this tree and the 152 `androidx.*` modules resolve to identical versions (Material 3 1.9.0 had moved Compose to 1.9.3 and was refused for that reason — `REPORT.md`) | — **a dependency graph, not a picture.** The diff script is in `REPORT.md` §1 |
| **2** The two Android files behind a seam | ✅ | `ChartUiPlatform.kt` declares the seam; `ChartFrameRate` and `ChartStrokePredictor` sit behind `ChartHost`, an interface the composable holds instead of a `View`. The browser's host votes for nothing and predicts nothing, and says so in its KDoc | — **an interface.** `:chart-ui:compileKotlinWasmJs` compiles it; the Android host tests run the phone's side |
| **3** Files moved `androidMain` → `commonMain` | ✅ | Twelve files, `CoineProChart.kt` last. Still Android-only, deliberately: `ChartFrameRate`, `ChartStrokePredictor`, `ChartIcons`, `ToolRail`, `ChartPickers` — none of them is reached from `CoineProChart` | — **a source-set move.** `git show --stat` lists the renames |
| **4** The resource calls | ✅ **narrowed: a seam, not `org.jetbrains.compose.resources`** | `painterResource`/`stringResource` became `chartGlyph`/`chartText`. On the phone they are the same `R` calls; in the browser the two glyphs are built from the XML's own path data (checked on every commit by `check_web_glyphs`) and the seven legend words are a two-language table. Compose resources would have put a second copy of every drawable and string on the phone's classpath for a browser that needs nine | `web-btcusdt-4h-fa.png` — the legend drawn in the browser |
| **5** `:core:designsystem` follows | ❌ **narrowed: only the tokens moved** | Palette, colours, page accents and the tabular-figures constant are in `:core:tokens`. The rest of the design system — share card, Coil, window size — is not needed by the chart and stays where it is until W2 needs a piece of it | — **nothing to show; the row is a scope statement** |
| **6** `:chart-ui:compileKotlinWasmJs` in CI | ✅ | `android-ci.yml` compiles `:core:tokens`, `:chart-ui` and builds `:web:terminalBundle`, and keeps the bundle as the `pro-chart-terminal` artefact | — **a workflow step.** Its first run is on the push |
| **Golden frames unchanged** | ✅ | `./gradlew testDebugUnitTest` green on this tree, which includes every golden screenshot test in `:app` and `:chart-ui`; `./gradlew :app:assembleRelease` green | — **the goldens are the frames**, and they did not move |
| **Milestone: `CoineProChart` in a browser on real candles** | ✅ | The bundle served locally with `/api/*` relayed to `pro-chart.com`, driven by Chromium: BTCUSDT 4h and XAUUSD 1h in Persian with a Solar Hijri axis, ETHUSDT 15m in English, SOLUSDT 1d at 390 px wide. The live price moved between two captures (85,692.5 → 85,650.1) | `web-btcusdt-4h-fa.png`, `web-xauusd-1h-fa.png`, `web-ethusdt-15m-en.png`, `web-solusdt-1d-fa-phone.png` |

## W2 — the terminal shell (the part this run ships)

| Item | State | Evidence | Frame |
|---|---|---|---|
| **Mount at `/terminal/`, deep links render** | ✅ | `<base href="/terminal/">`; `Route.parse` reads `/terminal/{SYMBOL}/{tf}` in any case and falls back to BTCUSDT 1h; the address bar follows every change (`replaceState`). The ETH frame was opened as `/terminal/ethusdt/15m` and the page rewrote it to `/terminal/ETHUSDT/15m` | `web-ethusdt-15m-en.png` |
| **Poll every 2 s, stop when hidden, `X-Client-Id`** | ✅ | Crypto: `/api/crypto/prices` every 2 s moves the forming bar; the loop asks nothing while `document.visibilityState` is `hidden`; one random id per browser in `localStorage`, sent on every request | — **a cadence, not a still.** `Terminal.kt`'s loop and `Browser.kt`'s `ClientId` |
| **Crypto does not tick — say so** | ✅ **narrowed** | The page does not open the socket at all in this release, so there is no welcome frame to show. What it says instead is true of what it does: «قیمت هر ۲ ثانیه به‌روز می‌شود» for crypto, «نمودار هر ۳۰ ثانیه به‌روز می‌شود» for forex, and after 90 s with nothing new it says the connection is gone rather than letting a frozen chart pass as live | every frame's bottom line |
| **Forex price never spliced into forex candles** | ✅ | The live forex price and the forex candles come from different upstreams (`SERVER.md` §4.10), so forex refetches its own bars every 30 s instead | — **an absence.** `Terminal.kt`, the `Venue.FOREX` branch |
| **One typeface** | ✅ | IRANYekanX's four weights are copied into the bundle at build time from `core/designsystem/src/main/res/font/`; no other face exists in the page. The marks IRANYekanX lacks — `·`, `Δ`, `◉`, `⋮`, `✕`, `—`, `∅`… — have a browser-only substitute from the typeface's own character map (`ChartMarks`); the phone keeps its characters | `web-xauusd-1h-fa.png` — no empty boxes |
| Workbench, rails, layout grid, object tree, watchlist, screener | ❌ **not in this release** | W2 items 1–6 beyond the chart itself. The page is a chart, an instrument, a timeframe and a language | — **not built** |

## W1½ and W3

| Item | State | Evidence | Frame |
|---|---|---|---|
| W1½ the 1,150 drawables | ❌ **not needed yet** | The page draws two glyphs, both built from their XML's path data | — **not built** |
| W3 the script studio | ❌ **not in this release** | `:namascript` compiles to Wasm already; the screen is not ported | — **not built** |

## Publishing

| Item | State | Evidence | Frame |
|---|---|---|---|
| The bundle | ✅ | `./gradlew :web:terminalBundle` → `web/build/terminal/`, eleven files, 12 MB before compression (Skia's own `skiko.wasm` is 8.4 MB of it). No webpack — `REPORT.md` §3 says why | — **a directory listing**, in `REPORT.md` |
| On `pro-chart.com/terminal/` | ⏳ **owed to the server agent** | `/terminal/` answers `404` today. What finishes it: put `web/build/terminal/` (or CI's `pro-chart-terminal` artefact) at `site/terminal` and run `bin/precompress.sh site/terminal` | — **a deployment this repository cannot do** |
