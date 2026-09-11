# Pro Chart — the plan's report

What the «chart, NamaScript, tablet, web» plan asked for, section by section: what is done, with
the proof; what is not, and why. Written as the work went, and finished with the last commit of
the run. Every claim below names the test, gate, file or number that backs it, so a reader can
check it rather than take it.

Versions: §0 shipped as 4.47.0, §1 as 4.48.0, §2 as 4.49.0, §3 as 4.50.0, §4 as 4.51.0. §5 is documentation on 4.51.0.

---

## §0 Copy hygiene — done (4.47.0)

| Asked | Done | Proof |
| --- | --- | --- |
| Rewrite every user-facing «سرور» / "server" from the reader's point of view; exceptions: Safety & version screen, `connections_mt5_server` | 100 Persian + 100 English strings rewritten; leftovers are the Safety screen (`safety_*`), the admin panel, the MetaTrader «سرور» field and the two MT5 body strings that name that field | `grep -rn سرور --include=strings.xml` lists only those; `tools/i18n/lint_strings.py` clean |
| Stop rendering `*_note/_body/_hint` subtitles except those preventing a real mistake; ≤ 60 visible; others → ⓘ; lint failing on new `_note` keys unless whitelisted | 258 keys classified in `tools/i18n/notes.tsv` — **44 visible**, 86 tip (→ ⓘ), 64 state, 28 catalogue, 19 system, 13 admin, 4 label; `NotePolicy` + `CoineProNote` + `CoineProInfoTip` in `core/designsystem`; 80-odd call sites hand the id to the policy | `NotePolicyTest`; lint rules in `lint_strings.py` (unregistered key, budget > 60, registry ≠ policy, a tip key resolved to a string) — the negative case was exercised before commit |
| `BrandConfig.webHost`, legal URLs, App Link hosts → `pro-chart.com`; keep `coineprofx.com` API; document in `docs/release/DOMAINS.md`; remove `user.tradeyar.trade-future.ir` from the store manifest | `BrandConfig.WEB_HOST/WEB_URL/LEGAL_BASE_URL`; legal documents' own links; manifest claims `pro-chart.com/reset`; TradeYar host removed from manifest, `DeepLinkValidation`, `print-assetlinks.sh` | `docs/release/DOMAINS.md` (host not serving as of 2026-09-08; what to put there in order; why the Play listing waits); `DeepLinkValidationTest` |

**Deviation, stated:** the plan says English is the default locale and Persian the translation
and "if inverted, fix first". The repository's standing rule (CLAUDE.md) is the opposite and
predates the plan; the locales were not inverted.

## §1 Module extraction — done (4.48.0)

| Asked | Done | Proof |
| --- | --- | --- |
| `:chart-core` pure Kotlin/KMP (androidTarget) | `chart/core`, `org.jetbrains.kotlin.multiplatform` + `com.android.kotlin.multiplatform.library`, targets `jvm()` and Android; 30 files, 15 962 lines moved as they were; five platform needs as `expect` in `ChartPlatform.kt` | `:chart-core:jvmTest` 577 tests; `ArchitectureTest` |
| `:namascript` pure Kotlin/KMP | `namascript`, same shape; 14 files, 3 573 lines | `:namascript:jvmTest`; `ArchitectureTest` |
| `:chart-ui` Compose | `chart/ui` (`:core:chart` renamed), re-exports the engine; icons cross the seam as `ChartIcon` names | `ChartIconsTest` (every name has a branch and a drawable) |
| Architecture tests failing on `android.*` / `androidx.*` imports | in both multiplatform modules, plus `java.*`, `javax.*` and the app's Android-only modules; the engine's also insists `ChartPlatform.kt` is the only file with an `expect` | `ArchitectureTest` in each |
| `docs/engineering/MODULES.md` | written: the table, the seam, what stayed and why, how to add | — |
| App builds and behaves identically | full `testDebugUnitTest` + `assembleRelease` green; the `actual`s are byte-for-byte the previous code | `TimeScaleTest`, `ChartTimeZoneTest` (axis labels), `DrawingGeometryA/BTest` (ratios) unchanged and passing |

Not moved, on purpose (named in MODULES.md as the next cut): `ChartTextCache`, `ChartFrame`,
and `CoineProChart.kt`'s `formatTime` / `formatTimeTick` / `previousSessionClose`.

## §2 Chart engine — audited; gaps closed where a test could prove them (4.49.0)

The audit first, because the plan's list assumes less than the engine had.

### 2.1 Data & scales

| Asked | State |
| --- | --- |
| Struct-of-arrays bars | **Had.** `CandleSeries` is parallel `DoubleArray`/`LongArray`s. |
| Ring buffer for seconds bars, incremental append | **Had.** `ChartHistory`; seconds bars built on-device (`chart_interval_seconds_note`). |
| Gap handling for sessions | **Had.** `previousSessionClose`, session-aware time axis. |
| Time zone: user TZ, DST-correct | **Had** (`TimeZonePrefStore`, `ChartZone` offsets per moment; `ChartTimeZoneTest` crosses a DST boundary). **Exchange TZ beside the user's — not done.** |
| Scales: linear, log, percent, indexed-to-100, inverted | **Had.** `PriceScaleMode` ×4 + `inverted` (`ChartViewport`, `ChartScreen` axis menu). |
| "Nice" ticks, collision avoidance, manual/auto/lock, per-pane scale, left/right | **Had.** `PriceScaleTest`, `LogScaleTest`, `priceBarLock`, `ScaleSide`. |

### 2.2 Series types

**Had:** candles, hollow, Heikin-Ashi, bars, line, line + markers, step, area, HLC area,
baseline, Renko, Kagi, P&F, line break, range, volume candles, footprint, TPO — 18 of the
plan's 23. **Not had:** columns, high-low, session / visible-range / fixed-range volume
profile as *series types* (volume profile exists as an indicator: `volumeProfile`,
`VolumeProfileWindowTest`), anchored VWAP as a series type (it is a drawing tool).

**Done this section:** `ChartTypeGoldenTest` — all 18 as pixels at 411 dp and 840 dp, 36
goldens in `app/src/test/goldens/chart-type-*.png`. `SeriesTransformReferenceTest` — Renko,
three-line break, Kagi and P&F against their textbook constructions on the 120-bar fixture;
**P&F was wrong** (reversal columns started at the raw close, off the box grid) and is fixed.

### 2.3 Panes & layouts

**Had:** unlimited indicator panes with drag-resize, an eight-pane chart grid on tablets
(`ChartPanesScreen`, `TABLET_MAX_PANES = 8`), synced symbol / interval / crosshair / time range
across panes (`PaneSync`), saved chart templates (`chart_more_layouts`). **Done this section:**
the named layouts — 2 across, 2 down, 3, 4, 6, 8 — as `ChartLayoutPreset`, stored by a stable
id with its count, drawn as the grid they make; the width keeps the last word on columns
(`gridColumns`, `ChartLayoutPresetTest`). **Not done:** export of a whole workspace as JSON
(the store holds symbols, sync, layout; there is no share sheet for it yet).

### 2.4 Drawings

**Had:** 93 tools (`DrawingTools.ALL`; `DrawingToolsTest` checks every one has a help entry),
anchors in (time, price), magnet weak/strong (`MagnetMode`), handles, lock/hide, z-order
(`ObjectTree.bringToFront/sendToBack`), clone, per-tool style templates, alerts on drawings
(`AlertTrigger.DrawingTouch`), measurement, text, position tools with R:R, Fib with editable
levels, Elliott/harmonic with validation (`DrawingWaveTwo/ThreeTest`), Gann, pitchforks,
regression channel, anchored VWAP, ranges, forecast; object tree with search and per-timeframe
visibility. **Not done:** multi-select and group; stickers/emojis as a picker (icons exist).

### 2.5 Interaction

**Had:** the touch physics as specified (`KineticScroll`, rubber-band, focal pinch, axis pinch,
double-tap reset, tick animation); keyboard: digits for timeframes, space, ←/→, Esc, Z/Shift+Z/Y.
**Done this section:** the desk pointer — wheel = time zoom at the cursor, Ctrl+wheel = price
zoom, hover (mouse or stylus) = crosshair, right button = axis menu over the gutter / a held
reading elsewhere; keys `+`/`=`/`-` and Alt+H/V. `ChartDeskPointerTest` injects a real mouse
through the Compose rule. **Not done:** `/` for symbol search (the chart screen has no search
entry to call), palm rejection by `MotionEvent.TOOL_TYPE`, `MotionEventPredictor`,
`Surface.setFrameRate`.

Rendering budget: `ChartFlingBenchmark` (fling, pinch, pan-and-hold; `FrameTimingMetric` at
P50/P90/P95/P99) exists and runs in `android-ci.yml`'s benchmark job on a device. **This
environment has no device; the p95 ≤ 8 ms / ≤ 12 ms thresholds are not measured here.**

### 2.6 Replay, alerts, strategy tester

**Had, all of it:** replay on any timeframe with step / play / speed / seek / jump-to-live and
the discipline ledger (`Replay.kt`, `ReplayTest`); alerts on price, channel (entering / exiting /
inside / outside), move (up / down / %), indicator value, drawing touch and multi-condition, with
once / every bar / every close and expiry (`AlertTrigger`, `AlertFrequency`) and a webhook
payload (`core:webhook`); the report with net profit, gross P/L, drawdown, win rate, profit
factor, average trade, Sharpe, Sortino, expectancy, trade list, equity curve and buy-and-hold
(`BacktestReport`, `BacktestExport`). **Not done:** deep backtesting with a bar magnifier on
lower-timeframe data.

### 2.7 Correctness tests — done

| Asked | Done |
| --- | --- |
| Golden numerical tests for every built-in indicator vs `ta` / TA-Lib, 1e-6 | `IndicatorReferenceTest`: **63 series**, 1e-6 absolute below one and relative above, against `ta` where its definition is the textbook one and TA-Lib's / TradingView's written out where it is not; `scripts/quality/gen_indicator_reference.py` regenerates the fixture and records each series' reference beside it |
| Goldens for Renko / Kagi / P&F / line break | `SeriesTransformReferenceTest` (above) |
| Volume profile, TPO buckets | **Not done** against an outside reference; `VolumeProfileWindowTest` covers the window arithmetic |
| Screenshot goldens for every series type, phone + tablet | `ChartTypeGoldenTest`, 36 goldens |
| Screenshot goldens for every drawing tool | **Not done** (93 tools × 2 = 186 images; the drawing renderer has `DrawingImageTest` and the geometry tests instead) |

**What the reference found, and what changed because of it** (all in 4.49.0, all with the web
terminal's copy reported in `docs/backend/PROMPT_WEB_TERMINAL.md`):

- MACD signal and histogram, force index, TRIX and signal, SMI ergodic and signal, Klinger
  signal: the EMA ran over the undefined head of its source as zeros — a signal eight bars early,
  pulled towards zero for ~40 bars. TRIX and SMI also reported after one of three stages.
- P&F reversal columns off the box grid.
- Conventions pinned and written down: EMA seeds with the first value (pandas / `ta`; TA-Lib and
  TradingView seed with the SMA and converge in ~5n bars); Wilder's RSI / ATR / DMI seed with the
  simple mean; Aroon runs over length + 1 bars; OBV and PVT start at 0; EOM scales by 1e8.

---

## §3 NamaScript — v1.1 completed, specified and tested; v2 stated as v2 (4.50.0)

The language is a **vectorising interpreter**: an expression is a whole series, `ta.*` delegates to
the chart's own indicators. The plan asks for a Pine-v5-class, bar-by-bar language with a typed
compiler and a VM. That is a different execution model and is **not built**; `docs/namascript/SPEC.md`
§10 tables every v2 item against what exists. What was built:

| Asked | Done | Proof |
| --- | --- | --- |
| `docs/namascript/SPEC.md` first, then implement | written first: lexical structure, EBNF grammar, types, `na` and `[]`, built-ins, execution model, diagnostic codes, sandbox, performance, the v2 table | the file; `ScriptDiagnosticsTest` holds its code table to the sources |
| `ta.*` "all TV indicators" | 130 `ta.` bindings — every indicator in `:chart-core` (63 added in 4.50.0) | `ReferenceDocsTest` fails on a bound name with no reference entry; `ConformanceSuiteTest` runs each |
| `math.*`, `color.*`, `input.*` | `math` ×17, `color.new` + 12 names, `input`/`input.int`/`input.float`/`input.bool` | conformance scripts `gen_math_*`, `gen_input_*` |
| plot family | `plot hline marker plotshape plotchar bgcolor alertcondition signal log`; `fill`, `plotcandle`, `barcolor` open | `gen_plot_styles`, `gen_bgcolor`, `gen_alertcondition` |
| `request.security`, `strategy.*`, `label/line/box/table`, collections, UDTs, libraries, `var`/`varip`, loops, functions | **not done** — v2 | SPEC §10 |
| Compiler / bytecode VM / incremental evaluation | **not done** — v2; the measured evaluate time (below) is the argument for it | `ScriptPerformanceTest` |
| Sandbox: CPU budget, memory cap, timeout with a friendly diagnostic, no I/O | node budget (250 000), wall clock (2 s, `E406`), size (20 000 chars), output caps, stack guard; no I/O by construction; **memory cap open** | SPEC §8; `sem_err_too_long`, `sem_plot_count_limit` |
| Diagnostics: line/column, error code, one-line fix hint, both languages; warnings | codes `E101`–`E406` with hints FA + EN, shown in the studio under the message; **warnings open** (no lookahead exists to warn about in v1.1) | `ScriptDiagnosticsTest`; 24 `sem_err_*` scripts |
| Performance: 300 lines / 10 `ta.` / 20k bars, compile < 50 ms, evaluate < 40 ms, realtime < 2 ms (Pixel 6a) | measured on this JVM: **parse 2.6 ms, evaluate 1 378 ms** for 303 lines over 20 000 bars; the device targets are unmeasured here and the evaluate figure is out of the plan's budget by ~30× — a series allocation per line is the cost, a typed-array VM is the fix | `ScriptPerformanceTest` prints the figures |
| Editor: highlighting, autocomplete with signatures, bracket matching, squiggles, format-on-save, snippets, find/replace, undo/redo, line numbers, minimap, split view, shortcuts | **done**: completion strip from the reference (function → with parenthesis), line numbers, bracket auto-close and step-over, the error card with code + hint, presets as snippets (existed), reference tab in both languages. **Open**: highlighting, squiggles at the column, format-on-save, find/replace, minimap, split view (§4's tablet layout is where it belongs) | `CodeFieldTest` |
| Console/log, "Add to chart" live, input UI from `input.*`, publish/library, import/export `.nama`, share link | log (existed), run-on-edit (existed), inputs panel (existed, now with `input.bool`), library (existed); **import/export and share link open** | — |
| Pine paste helper with a diff of unsupported calls | `PineTranslator`: rewrites headers, inputs, `ta.` spellings, `plotshape`/`shape.*`, titles, colours, `var`/`:=`; reports control flow, functions, collections, `request.*`, drawing objects, strategy orders with their line numbers; **not yet surfaced in the studio's UI** | `PineTranslatorTest` (a real EMA-cross indicator translates and runs) |
| Conformance suite ≥ 300 scripts with expected outputs; property tests; parser fuzz; plot goldens | **351 scripts**: 277 generated (one per built-in and form, expectations recorded from the engine and committed), 74 hand-written with worked-out expectations (absence, history, broadcasting, precedence, every error code). **Open**: property-based tests, a parser fuzzer, plot-type screenshot goldens | `ConformanceSuiteTest` |
| Reference docs generated from the stdlib into `docs/namascript/reference/` and the in-app help, FA + EN | `fa.md` and `en.md` generated from `ScriptReference` + `ScriptReferenceEn` (every function has both); the studio's reference tab shows the English line under an English locale; **help-catalogue integration open** | `ReferenceDocsTest` fails when the committed docs are stale |

Numbers: 351 conformance scripts, 130 `ta.` bindings, 26 diagnostic codes, 69 tests in `:namascript:jvmTest`.

## §4 Tablet — adaptive shell in place, parity proven where it can be proven off-device (4.51.0)

The tablet work landed across earlier sprints (the window class, the rail, `CoineProListDetail`,
the chart workbench, eight panes); §4 closes what the plan names and did not have: the activity's
resize contract, the fold, sheets on a wide window, the parity and input matrices, the
architecture rule, and a keyboard/pointer test rig. `material3-adaptive` and `NavigationSuiteScaffold`
are not in the Gradle cache and this environment is offline; the app's own `CoineProWindowClass`
answers the same question from the configuration (and, unlike the library, answers it in a
Robolectric render), so they were not added. Recorded as a deliberate substitution, not a gap.

### 4.1 Foundation

| plan | state | proof |
| --- | --- | --- |
| Compact / Medium / Expanded size classes | `CoineProWindowClass` (600 / 840 dp, Material's numbers), `showsNavigationRail`, `showsTwoPanes`, `prefersLabelledRail`, `maxChartPanes` | `WindowClassTest` at the dp either side of every threshold |
| bottom bar → rail on medium/expanded | `CoineProNavigationRail` from the same `AppDestination.entries` as the bar | **new** `NavigationParityTest`: same keys, same order, same labels, same route on tap |
| `resizeableActivity`, multi-window, DeX, ChromeOS | **new**: `android:resizeableActivity="true"` and the full `configChanges` set on `MainActivity`, so a split-screen resize is a recomposition rather than a restart that loses the viewport | manifest; `ChartPaneCapTest` «a tablet in a narrow multi-window split is capped like the phone it is shaped like» |
| foldables: `FoldingFeature`, table-top posture | **new**: `androidx.window` 1.5.0; `CoineProFold` (table-top / book / hinge dp) provided from `MainActivity` through `LocalCoineProFold`; the chart caps its plot above the hinge in table-top (`plotHeightAboveHinge`) so the price scale is never cut; nothing is placed across a vertical hinge because the two-pane layouts already split there | `CoineProFoldTest`, `ChartFoldTest` (JVM — Robolectric has no hinge) |

### 4.2 Canonical layouts

| plan | state |
| --- | --- |
| Watchlist ⇄ chart list-detail, 320–400 dp list | `CoineProListDetail` on the watchlist/markets, screener, ideas and news pages; detail state is `rememberSaveable` so a rotation keeps the paired symbol. **Open**: a drag-to-resize divider (fixed list width today). |
| Chart: drawing rail, right price scale, timeframe bar, side panels, 1–8 layouts | `ChartWorkbench` columns (tools / readings / both) measured against the content area; `ChartLayoutPreset` 1-8 (4.49.0); the price scale on the right in both directions (`docs/qa/RTL_TABLET.md`). **Open**: a `SupportingPaneScaffold`-style right panel switcher for DOM / object tree / alerts / tester / script editor — each is a page or sheet today, not a docked panel. |
| Sheets → side panels or dialogs ≤ 560 dp on Expanded | **new**: `CoineProSheet` renders as a centred `Dialog` capped at `SHEET_DIALOG_MAX_WIDTH = 560.dp` (90 % height, scrollable body) when the window shows two panes; the phone keeps its bottom sheet. Every sheet in the app goes through this composable. Proof: `SheetShapeTest` (tablet ≤ 560 dp, phone full width). |
| Home 12-column grid; DOM beside the chart; screener sticky-header table; journal/academy two-pane | **Open.** The home page is a single column at every width; DOM is its own page; the screener is a list with a list-detail chart, not a resizable table. Named here rather than half-built. |

### 4.3 Parity guarantee

| plan | state | proof |
| --- | --- | --- |
| one state holder per feature; architecture test that an Expanded-only composable reaches no state Compact lacks | **new** `LayoutParityArchitectureTest`: every `*Workbench.kt` names only the controllers/stores its `*Screen.kt` names; no `Controller`/`Store`/`ViewModel` class is referenced only from files that read the window class | green on the tree as committed |
| `docs/qa/PARITY_MATRIX.md` generated from UI tests | **new** `scripts/quality/gen_parity_matrix.py` reads every `@Config(qualifiers = …)` in the render tests and writes the matrix; `check-cross-phase-consistency.py` fails when it is stale. Cells are counts of real renders, and «—» is printed where none exists: sheets/alerts/screener/terminal have no tablet render yet | the file; the gate |
| screenshot matrix: 12 screens × 4 devices × 2 themes × 2 locales | 9 tablet goldens added (`*-fa-840` portrait for explore, ideas, chart; `*-fa-1280` landscape for watchlist, explore, ideas, menu, chart) on top of the phone set and the 18 chart-type tablet goldens; 13 MB of goldens in the repo. **Not** the plan's full product: no English tablet set, no light tablet set, no Galaxy Tab S9 Ultra (12.4″) qualifier, and a fold is a window class here, not a device | `GoldenScreenshotTest` |
| RTL on tablet: rail right, list-detail mirrored, price scale stays right | documented as a decision in **`docs/qa/RTL_TABLET.md`** | the goldens named there |
| input matrix: touch, S Pen, mouse, keyboard, trackpad | **`docs/qa/INPUT_MATRIX.md`**, one row per chart action; **new** `ChartKeyboardTest` (digits by name, `+`/`-`, Alt+H/V, arrows, Space, Esc, Z/Shift+Z/Y, down-only) beside `ChartDeskPointerTest` (wheel, hover, right-click) | the two tests |
| bug hunts: rotation mid-gesture, split resize with a sheet open, keyboard over the editor, font scale, TalkBack order | font scale 1.3 is a golden (`menu-fa-411-font130`, `watchlist-fa-393-font130`); the rest need a device or an instrumented run | — |

### 4.4 Acceptance, honestly

- Parity matrix: every top-level screen (watchlist, chart, explore, ideas, menu, shell) has a render at phone, tablet-portrait and tablet-landscape. Sheets, alerts, screener and the terminal have phone renders only. The matrix says so; it is not 100 % and is not marked as such.
- Soak test, tablet benchmark thresholds, the 60-second recording: **need a device**. None run here.
- `material3-adaptive`: not added (offline cache; the local class covers the decisions and renders under Robolectric).

Numbers: 6 new test classes (`NavigationParityTest`, `ChartKeyboardTest`, `SheetShapeTest`, `LayoutParityArchitectureTest`, `CoineProFoldTest`, `ChartFoldTest`), 9 new goldens, 2 QA documents plus the generated matrix.


## §5 Web readiness — the engine is portable and proven so; the target is documented, not enabled

| plan | state | proof |
| --- | --- | --- |
| keep `:chart-core` / `:namascript` KMP-clean; `jvm()` target; JVM tests in CI | done in 4.48.0 and held since: both modules are `org.jetbrains.kotlin.multiplatform` with `jvm()` + Android, `commonMain` free of `java.*`/`android.*`/Compose, and `android-ci.yml` runs `:chart-core:jvmTest :namascript:jvmTest` | the two `ArchitectureTest`s; the workflow |
| `wasmJs()` compile check «when the toolchain is stable in the repo» | **not enabled**: no `kotlin-stdlib-wasm-js` in the offline Gradle cache, so the target cannot be resolved here. `docs/web/PLAN.md` §2 lists the exact four steps (target lines, the five `actual`s on `Date`/`CivilDate`, the `JvmInline` import, the CI line) and the two things checked in advance that could have broken the compile (`String.format`, `Math.floorDiv` — neither is in common code) | `docs/web/PLAN.md` |
| `docs/web/PLAN.md` | **new**: what is ready, the Wasm target, the Compose Multiplatform terminal (which of `:chart-ui` moves and how), the server side in build order (static + CDN, API gateway on one origin, auth, WebSocket fan-out, alert engine, layout/watchlist/drawing sync, `assetlinks.json`, support), what stays true on the phone, and three open product decisions | the file |
| prepare `pro-chart.com`: `BrandConfig` host, App Links, legal pages, support | done in 4.47.0: `WEB_HOST`, `WEB_URL`, `LEGAL_BASE_URL`, the `/reset` App Link, the four legal documents pointing at the host; support stays the Telegram channel. The host itself **does not answer** and is the owner's to stand up (`docs/release/DOMAINS.md` gives the order) | `BrandConfig`, the manifest, `DOMAINS.md` |

---

## Run 4.52 → 4.6x — the six locked items

The owner's second prompt: six items, in order, each ending with proof here. Module report first,
as asked: `settings.gradle.kts` includes `:chart-core`, `:chart-ui`, `:namascript` (lines 22, 24,
27) among 85 modules; the no-`android.*`/`java.*` architecture tests pass (`:chart-core:jvmTest`
3/3, `:namascript:jvmTest` 1/1). `:chart-ui` is Android by design (Compose Canvas) and has none.

### Item 1 — locale inversion (4.52.0) — done

| asked | done | proof |
| --- | --- | --- |
| `values/` → English, `values-fa/` → Persian | 47 `strings.xml` + `bools.xml` moved in every module; the wordmark follows (`drawable-*` Latin lockup, `drawable-fa-*` Persian); `app_name` moved to the default set; no key added, removed or edited | `git show --stat 4.52.0` — 48 renames + 48 edits |
| Gradle check failing on Arabic script in `values/` | root task `checkDefaultLocaleIsEnglish`, on `:app:preBuild` | positive run: `BUILD SUCCESSFUL`; negative run with a probe key `probe_persian` in `core/navigation/.../values/strings.xml`: `Arabic script in the default (English) resource set — move the text to values-fa/: core/navigation/src/main/res/values/strings.xml: probe_persian` → `BUILD FAILED`; probe removed |
| `aapt2 dump` proof | below | — |
| product still opens in Persian | `AppLanguage.Default = PERSIAN`, `AppLanguageStore.apply` pins the activity locale; only the *fallback* for a device in a third language changes (Persian → English) | `aapt2 dump resources`: `string/app_name` has one entry `()`, every translatable key has `()` English and `(fa)` Persian |

```
$ aapt2 dump resources app-release-unsigned.apk | grep -A2 "string/nav_watchlist"
    resource 0x7f1106e3 string/nav_watchlist
      () "Watchlist"
      (fa) "دیده‌بان"
$ aapt2 dump resources app-release-unsigned.apk | grep -A2 "string/nav_chart"
    resource 0x7f1106da string/nav_chart
      () "Chart"
      (fa) "چارت"
$ aapt2 dump configurations app-release-unsigned.apk | grep -E "^fa"
fa
fa-hdpi … fa-xxxhdpi
```

No `(en)` configuration remains from the app's own resources (the `en-rAU`… entries in the APK are AndroidX's). Gates and the full unit suite are green on the inverted tree; the goldens did not change because every golden already ran under a `fa-rIR` qualifier.

### Item 2 — tablet layout (4.53.0) — done on Material's scaffolds; device screenshots by qualifier

| asked | done | proof |
| --- | --- | --- |
| `material3-adaptive` + `WindowSizeClass` | `material3-adaptive` 1.1.0 (+ `-layout`, `-navigation`), `material3-window-size-class` 1.4.0, navigation suite 1.4.0, `window-core` 1.5.0 added (Compose BOM 2025.09.01). `CoineProWindowClass` now derives Compact / Medium / Expanded from `androidx.window.core.layout.WindowSizeClass`'s breakpoints and the app feeds it the activity's own window (`currentWindowDpSize()`) | `WindowClassTest` «the breakpoints are the window library's own» |
| `NavigationSuiteScaffold`: bar → rail on Medium/Expanded | the shell's `Scaffold` no longer has a `bottomBar`; `NavigationSuiteScaffoldLayout` places the bar (`NavigationBar`) on Compact and the rail (`NavigationRail`) otherwise, `None` on sub-screens; the bar and the rail stay the app's own composables so the goldens hold | `NavigationParityTest`, tablet goldens |
| Watchlist ⇄ Chart as `ListDetailPaneScaffold`, list 320–400 dp, drag-to-resize, detail keeps state | `CoineProListDetail` is `ListDetailPaneScaffold` with a `PaneScaffoldDirective` built from the measured width (list 360 dp preferred), a **`VerticalDragHandle` on the divider** snapping to the list's width or half the screen, and `AnimatedPane`s; the paired symbol is `rememberSaveable` as before. Used by markets, screener, ideas and news | `SheetShapeTest`, `watchlist-*` goldens, `ChartWindowPublishTest` |
| chart: drawing rail, right price scale, timeframe bar, `SupportingPaneScaffold` panels (Watchlist / Object tree / DOM / Alerts / NamaScript), layouts 1–8 | **new** `ChartSidePanel` + `ChartSidePanelHost` on `SupportingPaneScaffold`: a 48 dp rail at the end edge with five glyphs; the chosen panel docks at 360 dp beside the plot and the workbench re-measures its columns. Needs ≥ 1128 dp (tools 280 + plot 440 + panel 360 + rail 48): the Pixel Tablet and the S9 Ultra in landscape; a tablet upright keeps the sheets. Layouts 1/2H/2V/3/4/6/8 and `panes_layout_label` from 4.49.0 unchanged | `ChartSidePanelTest` (rail on 1280, none on 840, open/close), `chart-fa-1280.png` |
| sheets → dialogs ≤ 560 dp on Expanded | 4.51.0, unchanged | `SheetShapeTest` |
| foldables with `FoldingFeature` | 4.51.0's `CoineProFold` unchanged; `material3-adaptive`'s `Posture` reads the same feature | `CoineProFoldTest`, `ChartFoldTest` |
| screenshots: Pixel Tablet + Tab S9 Ultra + Pixel Fold, dark/light, fa/en | **75 new goldens** in `GoldenScreenshotTest`: five screens × {Pixel Tablet `sw800dp-w1280dp-h800dp-xhdpi`, Galaxy Tab S9 Ultra `sw1232dp-w1973dp-h1232dp-hdpi`, Pixel Fold open `sw775dp-w930dp-h775dp-xhdpi`, cover `w411dp-h797dp-xxhdpi`} × {dark, light} × {fa, en}. Robolectric renders at the panels' dp; not photographs of the devices | `app/src/test/goldens/` (139 files, 22 MB) |
| parity matrix | regenerated with one column per device: every top-level screen has renders on every device; sheets, alerts, screener and terminal still phone-only | `docs/qa/PARITY_MATRIX.md` |

Not done: the home 12-column grid, DOM as a resizable table, journal/academy two-pane, ChromeOS/DeX on hardware, TalkBack order on the rail.

### Item 3 — numerals and fonts (4.54.0) — tabular figures done; the two weights are the owner's files

| asked | done | proof |
| --- | --- | --- |
| `FontFeatureSettings("tnum")` on every numeric text style (prices, axes, DOM, tables, calculators) | on **every** style: the base `coineProTextStyle` carries `tnum`, so all fifteen Material slots and the six `CoineProTextStyles` have it; the chart's axis labels already did (`axisStyle`) and the drawing-box labels now do. For IRANYekanX it is a no-op — its Latin digits are equal-width by design and `check_tabular_digits` measures the font — so the change is for the Latin face and for any fallback | `TypeScaleTest` «every style carries tabular figures» |
| IRANYekanX Medium + SemiBold | **blocked on the owner**: the two TTFs are licensed and not in the repository; Medium and SemiBold resolve to Bold as before. `docs/OWNER_ACTIONS.md` §4 names the files and where they go | — |
| proof: 5-second 60 fps tick recording, no glyph shift | a recording needs a device. What it would show is measured instead: `TabularFiguresTest` lays out the ten strings a price ticks through (`00,000.00` … `99,999.99`) under six styles with the shipped fonts on native Skia and asserts one width per style | `TabularFiguresTest` |

### Item 4 — chart physics (4.55.0) — every mechanism in place; the benchmark needs a device

| asked | state | proof |
| --- | --- | --- |
| float coordinates with snap-at-rest | **new**: the sub-bar remainder of a pan or a fling frame goes into `pixelShift` every frame (`panShift`), and `settlePan()` springs it to the nearest slot on the lift or when the fling ends | `ChartDeskPointerTest` (unchanged behaviour on the desk), goldens identical |
| `exponentialDecay` fling ~1.2 s | `KineticScroll`'s exponential curve, friction 3.8: 2 000 px/s coasts ≈ 1.2 s, 4 000 px/s ≈ 1.4 s | `ChartPixelsTest` «an ordinary flick coasts about one point two seconds» |
| right-edge rubber band + spring return | 4.4x: `stretchEdge` (o / (1 + o / 0.55w), capped at half the plot), `releaseEdge` spring 400 / 0.85 | — |
| focal pinch zoom; price-axis pinch vertical only | 4.4x: the per-axis pinch observer (`axisSpan`), `zoomedBy(factor, focal)`, `priceZoomedBy` | `ChartDeskPointerTest` «a wheel notch towards the reader zooms in at the cursor» |
| double-tap axis reset with spring | **new** `springTo(viewport.atRest())` for the time axis (spring 400, capped at two plot widths of travel); the price axis springs through `rangeLow`/`rangeHigh` as before | — |
| auto-scale spring ~180 ms | 4.4x: `spring(stiffness 700, damping 1.0)` on the drawn range | — |
| 150 ms tick animation | 4.4x: `LIVE_CLOSE_MS = 150`, `TICK_FLASH_MS = 200` | — |
| history load without jump | 4.4x: `withSeries` keeps the anchor bar when older bars are prepended | `ChartPanRestoreTest`, `DeepSeriesViewportTest` |
| draw-phase-only invalidation with three cached layers | **new** `StaticLayerCache`: bottom layer (grid … markers) as a bitmap keyed on `StaticLayerKey`; annotation layer (drawings, panes, axes) live; cursor layer its own `Canvas`. The `Invalidation` levels already gated the tick ladder | `StaticLayerCacheTest` (one miss, one hit, pixels identical; a new key repaints); all 36 chart-type goldens and the chart goldens unchanged through the cached path |
| `MotionEventPredictor` | **new** `ChartStrokePredictor` on `androidx.input:input-motionprediction` 1.0.0, fed by a `pointerInteropFilter` on the freehand tool; the live stroke reaches to the predicted point. Built lazily and only where a display exists | compiles; the predictor answers only on a device |
| `Surface.setFrameRate` | **new**: `View.setRequestedFrameRate(HIGH)` while a gesture or fling is in progress, `NO_PREFERENCE` at rest (Android 15+); the activity already prefers the fastest display mode | — |
| mouse wheel / hover crosshair / keyboard shortcuts | 4.49–4.51 | `ChartDeskPointerTest`, `ChartKeyboardTest` |
| `ChartFlingBenchmark` P95 ≤ 8 ms phone, ≤ 12 ms tablet 4-chart, 0 jank | **needs a device**: `benchmark/…/ChartFlingBenchmark.kt` and `scripts/quality/check-benchmark-thresholds.py` exist; nothing here has a GPU to time | — |

### Item 5 — NamaScript (4.56.0) — done, within the vectorised model

| asked | done | proof |
| --- | --- | --- |
| `docs/namascript/SPEC.md` | written in 4.50.0; §5.3 (inputs), §5.3.1 (`request.security`), §6 (compile + incremental) and §7 (E210) extended | the file; `ScriptDiagnosticsTest` pins the code table to §7 |
| typed AST + compiled evaluation with incremental bars | `TypeChecker` infers a `ScriptType` for every expression and resolves every name and function before a run, refusing with the interpreter's own codes; `CompiledScript` is parse-once/run-many; `IncrementalRunner` evaluates the last *window* bars and splices them — 12 × the largest length the script names, ≥ 120 — when the series is the previous one extended or ticked. A cumulative function or `request.security` makes the script whole-run. **Not a bar-by-bar VM**: `var`/`:=` across bars stays v2 (SPEC §10) | `CompilerTest`: the runner matches a whole run on every bar and every marker across 12 appended bars with ticks in between (≥ 10 tail runs), and a changed input forces one whole run; the 360-script conformance suite passes through the checker unchanged |
| diagnostics with line/column in fa/en | every checker error carries line, column, both languages and the code; the editor underlines the failing token | `NamaSyntaxTest` («the failing token on the failing line is underlined») |
| `input.*` full set with auto-generated settings UI | `input`(+`step`), `input.int`, `input.float`, `input.bool`, `input.string`(options), `input.source`, `input.color`, `input.timeframe`; `ScriptInput.kind/options/step`; the panel draws a slider, a switch, chips or swatches by kind | `CompilerTest` «the full input set records its kind and options»; `sem_input_*` scripts |
| `request.security` MTF | on the chart's own bars bucketed to a coarser timeframe, mapped back confirmed | `CompilerTest` «request security reads the last completed higher bar and never the forming one», `sem_security_*` |
| ≥ 100 conformance scripts | **360** (277 generated, 83 hand-written) | `ConformanceSuiteTest` |
| editor: highlighting + autocomplete + squiggles, phone and tablet, split view on Expanded | `NamaSyntax` colours by token through a `VisualTransformation` (identity offsets); the completion strip from 4.50.0; the squiggle from the check or the run; on an expanded window the studio is code \| chart, the chart full-height | `NamaSyntaxTest`, `CodeFieldTest`; a golden of the split view is not recorded (the studio needs a controller with a database) |

Numbers: 360 conformance scripts, 27 diagnostic codes, 5 new built-ins, 81 tests in `:namascript:jvmTest`.

### Item 6 — network (4.57.0) — done

| asked | done | proof |
| --- | --- | --- |
| `CertificatePinner` for `coineprofx.com` and the crypto API host, primary + backup | `NetworkFactory.okHttpClient` has installed a `CertificatePinner` from `BuildConfig.CERTIFICATE_PINS` since 4.4x; the list was empty. 4.57.0 ships `DEFAULT_CERTIFICATE_PINS`: TradeYar leaf `RO8Xw…` (primary, matched live) + offline backup `Q1JB2…` + ISRG Root X1/X2; CoinePro-FX GTS WE1 `kIdp6…` (primary) + GTS Root R4 `mEflZ…` + GTS Root R1 + ISRG X1/X2 — CA pins, because Cloudflare rotates the leaf's key without notice. Expiry 2027-03-01 | `CertificatePinDefaultsTest` (both hosts ≥ 2 pins, expiry in the future, from the artefact's `BuildConfig`); `CertificatePinsTest` (format, expiry enforcement); the `openssl` measurement in `PINNING.md` |
| route Investing / Cointelegraph / ForexFactory through the backend behind a flag; no direct calls in release | `DIRECT_THIRD_PARTY_FEEDS = false` in the release variant unless `COINEPRO_DIRECT_THIRD_PARTY_FEEDS=true`; `PublicMarketIntel` tries the platform routes (`api/v1/public/news`, `…/calendar/week`) first and returns nothing for the direct sources when the flag is off. **The backend routes do not exist yet** (`docs/backend/FEEDS.md` is the contract), so a release build's news section is what the two platforms' own newsrooms answer | `app/build.gradle.kts` release block; `PublicFeedTest` |

## Run 4.58 → 4.6x — the fix and the four re-listed items

### FIX (4.58.0) — done

| asked | done | proof |
| --- | --- | --- |
| `chart_panel_objects` = «ترسیم‌ها» | the key, and the tool rail's tile that said «اشیا» in a Kotlin literal | `values-fa/strings.xml`, `ToolRail.kt` |
| lint forbids «اشیا» | `FORBIDDEN_VARIANTS` (`شیءها\|اشیا` → «ترسیم‌ها») and `KOTLIN_RETIRED` for `src/main` literals in `tools/i18n/lint_strings.py`; `>اشیا<` in the gate's `FORBIDDEN_UI_WORDS` | `lint_strings: 42 module(s) clean` after the fix; a probe string fails it |
| tablet screenshots | **56 frames** in `docs/qa/screenshots/4.58/` (`<scene>-<device>-<locale>-<theme>.jpg`, half size; the full-size PNGs are in the run's `build/proof/` and the zip sent with the APK): `list-detail`, `panel-objects`, `panel-watchlist`, `panel-depth`, `panel-alerts`, `panel-script`, `panes-4` × Pixel Tablet (1280×800 dp) and Galaxy Tab S9 Ultra (1973×1232 dp) × dark/light × fa/en | `TabletProofTest` |

**Which Material scaffolds are actually in use** (`grep` on `src/main`, call sites):

| scaffold | where | frames that show it |
| --- | --- | --- |
| `ListDetailPaneScaffold` | `core/designsystem/.../CoineProListDetail.kt` — every list ⇄ detail page (markets, screener, ideas, news) with the drag divider | `list-detail-*` |
| `SupportingPaneScaffold` | `feature/chart/.../ChartSidePanels.kt` — the chart's docked panel beside the plot | `panel-*` |
| `NavigationSuiteScaffoldLayout` | `app/.../CoineProApp.kt` — the shell places the bar (compact) or the rail (medium, expanded) | not in these frames (the proofs render screens, not the shell); `ScreenshotRenderTest.tabletShell` |

Seen in the frames and left as is: the readings panel's three words (`متوسط`, `کم`, `خنثی`) are Persian literals under an English locale — copy in code, outside this run's scope, noted for the next copy pass.

### Item 3 — numerals and fonts (4.59.0) — done where the material exists

| asked | done | proof |
| --- | --- | --- |
| `FontFeatureSettings("tnum")` on every numeric style | on **every** style the design system defines — the base `coineProTextStyle` carries it, so prices, changes, PnL, axes, the DOM ladder, tables, calculators and the watchlist all inherit; the chart's canvas labels set it on their `Paint` | `grep -rn 'TABULAR_FIGURES\|"tnum"' core feature chart` → 11 sites: `core/designsystem/CoineProType.kt` (`const val TABULAR_FIGURES = "tnum"`, the base style, `numericTextStyle`, `TextStyle.numeric()`), `chart/ui` × 2 (axis and legend paints), `TypeScaleTest` (fails on any style without it) |
| IRANYekanX Medium + SemiBold (or Variable) | **not in the repository** — `core/designsystem/src/main/res/font/` holds `iranyekanx_regular.ttf` and `iranyekanx_bold.ttf` only; Medium and SemiBold resolve to Bold. The files are licensed and the owner's to supply; the font map is ready for them (`Font(R.font.iranyekanx_medium, FontWeight.Medium)` is a one-line change per weight) | `ls core/designsystem/src/main/res/font` |
| 5-second 60 fps tick recording, no glyph shift | **`TickSequenceTest`**: 300 frames (5 s at 60 fps) of a price ticking through every digit, the thousands rolling, under eight styles; the layout's width is asserted identical on every frame and every glyph's left edge compared frame to frame. Result: `max glyph shift per style: bodyMedium=0.0px, labelSmall=0.0px, titleMedium=0.0px, Numeric=0.0px, NumericLarge=0.0px, RowFigure=0.0px, TileFigure=0.0px, Balance=0.0px` | the test's output line; `TabularFiguresTest` (ten digits, six styles) |

A device recording would show the same frames; what it would add is the display's own rendering, which Robolectric's Skia does not differ from at this level. The sign glyphs `+`/`-` have different advances in every font, so the app keeps the sign in its own column; the test fixes it for that reason.

### Item 4 — chart physics (4.60.0) — every mechanism named, tested where a JVM can test it

Everything from 4.55.0 stands (the table above); this run adds the third layer, the desk's menu and
history keys, and puts a number next to each mechanism. The constants are in `CoineProChart.kt`.

| asked | where | number | proof |
| --- | --- | --- | --- |
| float coordinates, snap-at-rest | `pixelShift` carries the sub-bar remainder; `settlePan()` springs it to the slot on lift | — | `ChartDeskPointerTest`; goldens unchanged |
| `exponentialDecay` fling ≈ 1.2 s | `KineticScroll` (`EXPONENTIAL`) | friction 3.8; 2 000 px/s → 1.2 s | `ChartPixelsTest` «an ordinary flick coasts about one point two seconds» |
| right-edge overscroll ≤ 50 %, rubber band, `spring(400, 0.85)` | `stretchEdge` / `releaseEdge` | `OVERSCROLL_MAX_SHARE = 0.5`, `RUBBER_BAND_KNEE = 0.55`, `OVERSCROLL_STIFFNESS = 400`, `OVERSCROLL_DAMPING = 0.85` | the constants; `ChartPixelsTest` |
| left edge loads history with scroll compensation | `withSeries` keeps the anchor bar and `offset` when older bars are prepended | — | `ChartPanRestoreTest` (prepend keeps the bar under the finger), `DeepSeriesViewportTest` |
| pinch `barSpacing *= 1.0025^Δpx` at the focal x | the two-finger observer, `PINCH_BASE.pow(spanX − lastX)`, `zoomedBy(ratio, focal)` | `PINCH_BASE = 1.0025` | `ChartDeskPointerTest` «zooms in at the cursor» (same focal path) |
| price-axis pinch/drag vertical only; time-axis drag time only | `axisSpan` splits the pinch by the axis it started on (`priceZoomedBy` reads Δy only, `zoomedBy` Δx only); the axis handlers read one coordinate | — | `ChartViewportTest` («zoom keeps the right edge fixed», «a zoom keeps a resting chart resting»); no gesture-level test of the axis strips |
| double-tap axis reset with spring | `springTo(viewport.atRest())`, price axis through `rangeLow`/`rangeHigh` | spring 400, ≤ 2 plot widths | — |
| auto-scale `spring(700, 1.0)` | `AUTO_SCALE_SPRING` | stiffness 700, damping 1.0 (≈ 180 ms) | the constant |
| 150 ms tick animation | `LIVE_CLOSE_MS` | 150 (flash 200) | the constant |
| draw-phase-only invalidation, three cached layers | **new**: series bitmap (`StaticLayerKey`), drawings bitmap (`OverlayLayerKey`: viewport position, marks, selection, palette — not the data), cursor `Canvas` live. `ChartLayerCounters` reads the misses per frame | 60 cursor frames → **0** series misses, **0** drawings misses; a tick → **1** series, **0** drawings; a new bar → **1** and **1** | `ChartLayerInvalidationTest`, `StaticLayerCacheTest`, 36 chart-type goldens unchanged |
| zero allocations in the draw loop | the series pass reuses its `Path`s, `FloatArray`s and paints across frames; a cursor frame allocates the two blits' keys (two small data classes) and nothing per bar | JVM, warmed and interleaved: cursor frame p50 **46.20 ms** / p95 87.41, tick frame p50 **49.79 ms** / p95 85.91 — 1280×800 dp at xhdpi (2560×1600 px) rasterised in software, 400 bars, 3 drawings, 60 frames each. The software blit of two full-size bitmaps is most of both numbers, which is why the miss counts, not the milliseconds, are the proof here | `ChartLayerInvalidationTest`'s printed line; an allocation profile and a GPU frame time are device measurements |
| `MotionEventPredictor` (API 33+) | `ChartStrokePredictor` on `input-motionprediction` 1.0.0, freehand tool | — | compiles; answers only on a device |
| `Surface.setFrameRate` | `setRequestedFrameRate(HIGH)` during a gesture or fling, `NO_PREFERENCE` at rest (Android 15+) | — | — |
| mouse wheel = time zoom at the cursor; Ctrl+wheel = price zoom; hover = crosshair | the desk branch of the pointer handler (`PointerType.Mouse` and `Stylus`) | — | `ChartDeskPointerTest` (4 tests) |
| right-click menu | **new**: `onContextMenu(price, at)` from the chart's secondary press; `ChartScreen` draws a `DropdownMenu` at the pointer — alert here, copy price, scale, search — anchored absolute-left so the offset is the pointer's on either locale | 4 items, 4 strings | `ChartContextMenuTest` (2 tests) |
| keyboard: ←/→, Alt+H/V, Ctrl+Z/Y, Esc, `/`, digits | `ChartShortcuts`: `←/→` step, `Alt+H`/`Alt+V` arm the lines, **new** `Ctrl+Z`/`Ctrl+Shift+Z`/`Ctrl+Y`, `Esc` cancels, **new** `/` opens the search, `1`–`6` timeframes, `+`/`−` zoom, space replay | — | `ChartKeyboardTest` («Ctrl with Z and Y walk the history like the bare keys, and slash opens the search») |
| S Pen hover | a stylus hover is the same desk branch as a mouse hover — crosshair on hover, tracking on the button | — | `ChartDeskPointerTest` covers the branch with a mouse; the pen is a device |
| `ChartFlingBenchmark` p95 ≤ 8 ms phone / ≤ 12 ms tablet 4-chart, 0 jank; 120 fps recording vs TradingView | `benchmark/…/ChartFlingBenchmark.kt`, `scripts/quality/check-benchmark-thresholds.py` | **needs a device** — no GPU here | — |

Numbers: 3 layers, 13 physics constants named above, 4 menu items, 12 keyboard bindings, 4 new tests
(`ChartLayerInvalidationTest` ×2, `ChartContextMenuTest` ×2) and 1 extended (`ChartKeyboardTest`).

### Item 5 — NamaScript (4.61.0) — the surface named in the plan, within the vectorised model

| asked | done | proof |
| --- | --- | --- |
| SPEC: types, series, `na`, `[]`, `var`/`varip`, `ta.*`/`math.*`/`str.*`/`input.*`/`request.security`/`strategy.*`/plot family/`label`/`line`/`box` | `docs/namascript/SPEC.md` §3 (text joins), §3.1 (`na`, `na(x)`), §4 (`na`, `strategy.long/short`), **new §5.5 `str.*`**, **§5.6 objects**, **§5.7 `strategy.*`**, **§5.8 `var`/`varip`**, §6 (what is whole-run), §7 (E407), §8 (memory), §9 (numbers), §10 (the table, updated). Not there and said so: UDTs, `array.*`, `map.*`, `table.*`, functions, loops, per-bar `:=` — a bar-by-bar VM, v2 | the file; `ScriptDiagnosticsTest` pins §7 to the code table |
| typed AST + compiled incremental evaluation | as 4.56.0; the checker now types text joins, `na`, the constants and the 19 new functions, and marks objects and orders whole-run (`WHOLE_RUN`) | `NamaScriptTest` «objects and orders make a script whole-run, text and na do not» |
| sandbox: CPU/memory budget, timeout | CPU: 250 000 nodes (E401); time: 2 000 ms (E406); **memory, new: 8 000 000 retained bar-cells (E407)** — every variable and plot counts, ≈ 72 MB at most | `NamaScriptTest` «the memory budget refuses a script that holds too many series» (450 series × 20 000 bars → E407; the same script over 200 bars runs) |
| diagnostics fa/en | 28 codes, each with a message and a one-line fix in both languages | `ScriptDiagnosticsTest` |
| full `input.*` with generated UI | 4.56.0 | `sem_input_*` (7 scripts) |
| `request.security` with lookahead rules | 4.56.0: confirmed mapping, E210 | `sem_security_*` |
| ≥ 100 conformance scripts | **378** (277 generated, 101 hand-written; 18 new: `sem_na_*`, `sem_var_*`, `sem_varip_*`, `sem_str_*`, `sem_label_*`, `sem_strategy_*`, `sem_text_plus_*`, two refusals) | `ConformanceSuiteTest`, with two new expectation kinds (`drawings`, `trades`) |
| editor: highlighting, autocomplete with signatures, squiggles, snippets, console | colouring and the squiggle from 4.56.0; **signatures**: each completion chip shows the reference's signature after the name (`ta.sma(close, 20)`); **snippets**: a row of four working scripts (EMA cross, RSI with zones, a label on the last bar, a simple strategy), each checked to compile; **console**: the log plus «اجرا در 12 ms · ۲٬۰۰۰ کندل · فقط دنباله» on every run (`ScriptResult.elapsedMillis`); the strategy card | `StudioHelpersTest` (3 tests), `NamaSyntaxTest`, `CodeFieldTest` |
| split view in `chart_panel_script` | 4.56.0 (code \| chart on an expanded window); the panel from 4.58.0 | `TabletProofTest` `panel-script-*` frames |
| `label`/`line`/`box` on the chart | `ScriptOverlay.drawings`: `text`, `trend`, `rect` drawings with ids above 10⁹, drawn by the same renderer as the reader's marks; the studio's preview passes them to `ChartDecoration.drawings` | `NamaScriptTest` «objects are placed at the bar and price given, and become drawings» |
| `strategy.*` | entry / close / close_all, one position, next-open fills, opposite entry reverses, open position reported open; net %, win rate, profit factor, max drawdown; two marks per trade | `NamaScriptTest` «a strategy fills at the next open, reverses on an opposite entry, and reports its figures» (bars 11→21 long at 110.5→120.5, then short, PF > 1, DD = the short's loss) |

**Performance** (`ScriptPerformanceTest`, JVM, 303 lines with ten `ta.` calls over 20 000 bars; the interpreter's arithmetic now on raw arrays):

| figure | isolated run | under the full suite | plan's Pixel 6a target |
| --- | --- | --- | --- |
| compile (lex + parse + type-check) | **1.4 ms** | 3.3 ms | < 50 ms |
| whole evaluation | **168 ms** (was ~1 400 ms in 4.56.0) | 279 ms | < 40 ms |
| realtime, one bar appended (`IncrementalRunner`, 632-bar window) | **6.7 ms** | 12.3 ms | < 2 ms |
| realtime, last bar ticked | **6.2 ms** | 10.8 ms | < 2 ms |

Compile is inside the target on this JVM by a wide margin; the whole evaluation is not, and the realtime figure is three times the target — a 632-bar window through ten indicators and 290 arithmetic lines. The next step for both is the bar-by-bar VM (v2), which would make a tick cost one bar rather than a window. A phone's numbers need a phone.

Numbers: 19 new functions and 2 constants, 1 new statement form, 1 new code, 18 conformance scripts, 8 unit tests, 3 studio tests, 93 tests in `:namascript:jvmTest`.

### Item 6 — network (4.62.0) — verified live, nothing to move

| asked | state | proof |
| --- | --- | --- |
| `CertificatePinner` for `coineprofx.com` and the crypto host, primary + backup | shipped in 4.57.0 (`DEFAULT_CERTIFICATE_PINS`, 4 + 5 digests, expiry 2027-03-01). **Re-measured 2026-09-10** through the build environment's proxy, which tunnels TLS: TradeYar serves leaf `RO8Xw…` (primary, matched) under Let's Encrypt YE2 → ISRG Root X2 (`diGVw…`, backup, matched); CoinePro-FX serves a Cloudflare leaf `BzsaT…` (unpinned by decision) under GTS WE1 (`kIdp6…`, primary, matched) → GTS Root R4 (`mEflZ…`, backup, matched). Both hosts meet two shipped pins on the live chain | `CertificatePinDefaultsTest` (from `BuildConfig`: two hosts, ≥ 2 pins each, expiry ahead); the measurement table in `docs/security/PINNING.md` |
| rotation doc | `docs/security/PINNING.md` «Rotation» (add the new pin a release ahead, ship, rotate, drop the old) and «Before 2027-03-01»; the 2026-09-10 table added | the file |
| Investing / Cointelegraph / ForexFactory via the backend behind a flag; no direct calls in release | `DIRECT_THIRD_PARTY_FEEDS` is `false` in the release variant unless `COINEPRO_DIRECT_THIRD_PARTY_FEEDS=true`; `PublicMarketIntel(directFeeds = false)` returns an empty section rather than a wire. **New test**: with the flag off and our own routes empty, no request leaves for any of the three hosts. The backend routes are still the contract in `docs/backend/FEEDS.md` | `app/build.gradle.kts` release block; `PublicFeedTest` «with direct feeds off, an empty section stays empty and no wire is asked» |

Numbers: 2 hosts, 9 shipped digests, 4 matched live (2 per host), 1 expiry (2027-03-01), 0 changes.

## Run 4.63 → 4.6x — the owner's audit of 4.62.0, and one item per run

The owner's diff of 4.62.0 against 4.57.0 was read off the APK: a grep of the release `classes.dex`
for `exponentialDecay`, `setFrameRate`, `MotionEventPredictor` and `CertificatePinner`, the font
list, the string table. Three of its four «✗» rows are the release build's **R8 obfuscation**, not
missing code: `MotionEventPredictor → j6.a`, `ChartStrokePredictor → com.coinepro.core.chart.b3`
and `CertificatePinner` are all in `app/build/outputs/mapping/release/mapping.txt`, and the pin
digests (`sha256/RO8Xw…`) *are* in the dex as string literals — which is why `tnum` and the four menu
keys, also literals, showed «✓». The fourth row (the wires) is a real finding: the three hosts' URLs
are string literals in the release dex, gated by a flag rather than absent. Each run below ends with
what the APK itself will show.

### RUN A — item 3, the two weights (4.68.0) — done; the package arrived

The 4.63.0 finding stands as history: the Eco archive held Regular and Bold only, the two were
outline-incompatible, and no weight could be made. The owner then supplied **IRANYekanX Pro**
(`IRANYekanXPro.zip`, 148 files: ten static weights in three cuts, a variable font, webfonts,
the licence). What was taken from it and why:

| in the package | taken | why |
| --- | --- | --- |
| `IRANYekanX family/IRANYekanX-Medium.ttf` (500), `IRANYekanX-DemiBold.ttf` (600) | **yes**, as `iranyekanx_medium.ttf` and `iranyekanx_semibold.ttf` | the two weights the typography has asked for since 4.54.0. Version 4.000, 578 glyphs, the same seventeen GSUB features (`ss01–ss04` included, so the numeral machinery holds) as the Regular and Bold already shipped; Latin digits equal-width in each (565 and 569 units) — the gate now reads all four files |
| `IRANYekanX-Regular.ttf` | already in the repository, **byte-identical** (md5 `d9df54c4…`) | — |
| `IRANYekanX-Bold.ttf` | kept the repository's copy | the package's Bold is 84 041 B against the shipped 83 957 B (the archive's copy was re-saved in 2026-09); identical `usWeightClass`, glyph count, digit advances and features. Nothing on screen would change; nothing was swapped |
| `Variable Font/IRANYekanXVF.ttf` (254 KB, `wght` 100–1000, `dots` 0–4) | **no** | different metrics from the statics — Latin digit 547 units against 562–572, Persian digits 10–20 % narrower — so switching to it would reflow every screen; and it offers no weight the four statics do not already give. It would also replace 337 KB of statics with 254 KB, which is not worth a reflow |
| `Farsi numerals/`, `NonEnglish/` cuts | **no** | market figures are Latin by the standing rule and set in Inter; a face with Persian default digits or no Latin would fight both |
| `FontLicense.txt` | copied to `core/designsystem/FONT_LICENSE_IRANYekanX.txt` | the package asks for the six-digit licence code to be written in it beside the fonts. **The code is the owner's** — the placeholder is still `(.....)`; `docs/OWNER_ACTIONS.md` §4 |

**Wiring.** `CoineProFontFamily` maps 400 / 500 / 600 / 700 to four files (`CoineProType.kt`);
no other line changed — the typography has been defined by weight since 4.54.0, exactly so that
this would be a two-line change. `grep -rn "FontSynthesis\|synthetic" --include=*.kt` over the
design system and the features: **none** (the one hit is a KDoc sentence saying there is none).

**Proof.** `FontWeightProofTest` sets one Persian line at the four weights and asserts the four
advances are distinct and strictly increasing — a real heavier face respaces the line, a
synthetic bold of one file thickens strokes and does not — and writes
`docs/qa/screenshots/4.68/fonts-four-weights-fa`. The Persian Home, Watchlist, Chart and sheet
frames re-rendered with the four weights are beside it. 137 goldens — every frame that
carries a title, a label or a large figure — moved past the 0.1 % tolerance (0.3–1.2 % of their
pixels, the weight of the words) and were re-recorded with the four faces; `TickSequenceTest`
and `TabularFiguresTest` pass unchanged (the figures are Inter's and did not move). The release
APK's font list is the acceptance's: four IRANYekanX files (84 240 / 84 836 / 84 836 / 83 957 B)
and Inter Variable (879 708 B).

### One typeface (4.69.0) — Inter and Vazirmatn out; IRANYekanX Eco/Pro is all there is

The owner's instruction after the Pro package landed: *nothing in the project but IRANYekanX
Eco and Pro.* Done, and gated.

| was | is | where |
| --- | --- | --- |
| Inter Variable (879 708 B) set every figure — prices, changes, axes, tickers — through `CoineProLatinFontFamily` | **removed.** `CoineProLatinFontFamily` is now IRANYekanX itself (the name kept so the numeric styles read as what they are); the figures sit in the same face as the words, with the family's equal-width Latin digits (562 / 565 / 569 / 572 units per weight) doing what `tnum` did | `CoineProType.kt`; the release APK carries four `.ttf` files, all IRANYekanX (84 240 / 84 836 / 84 836 / 83 957 B) — the 880 KB of Inter is gone |
| Vazirmatn from Google Fonts stood in for IRANYekanX in the six design mockups (`design/canvas/*.dc.html`), with `preconnect` links to the font service | **the Pro package's own webfonts**: Regular, Medium and Bold `.woff2` (84 KB) under `design/canvas/fonts/` with the package's `FontLicense.txt` beside them, `@font-face` in each mockup, no font service | `design/canvas/`, its README |
| `'Vazirmatn'` in the README banner's SVG | `'IRANYekanX'` | `design/readme/banner.svg`, `scripts/design/build-readme-banner.py` |
| `docs/design/fonts/INTER-OFL.txt` | **removed**; the only font licence in the repository is fontiran's, `core/designsystem/FONT_LICENSE_IRANYekanX.txt` (and the copy beside the webfonts) | — |
| `docs/DESIGN_DIRECTION.md` «Vazirmatn where bundled/approved» | «IRANYekanX Pro — the one typeface in the project» | — |

**The gate.** `check_single_typeface` in `check-cross-phase-consistency.py` walks the repository:
every `.ttf` / `.otf` / `.woff` / `.woff2` must be named `IRANYekanX…`, and no tracked `.html`,
`.css`, `.kt`, `.kts`, `.xml`, `.py`, `.svg` or `.json` may reference `fonts.googleapis.com` or
`fonts.gstatic.com`. It failed on the first run (the mockups' `preconnect` lines) and passes now.

**What moved on screen.** Every figure — the watchlist prices, the balance, the chart's axes and
legends, the DOM ladder, the calculators — changed face, so 115 goldens were
re-recorded; `TickSequenceTest` (300 frames, 0.0 px shift) and `TabularFiguresTest` pass on the
new face without a change, which is the equal-width digits doing their job. IRANYekanX's Latin digits
are narrower than Inter's at the same size (562 against 631 units per em on a digit), so every
column of prices gained a little room; nothing in the 540-test run changed its layout.

**Still the owner's:** the six-digit licence code in `FONT_LICENSE_IRANYekanX.txt` (`(.....)`
is still the placeholder in both copies).

### RUN B — item 4 (4.63.0) — done to the audit; the two device proofs remain

What changed this run, on top of 4.55.0 and 4.60.0:

| asked | done | proof |
| --- | --- | --- |
| `exponentialDecay` fling ≈ 1.2 s | **the chart now flings on Compose's `exponentialDecay`** (`ChartFling`, `chartFlingSpec()`: friction multiplier 3.8 / 4.2, cut-off 20 px/s) read off `withFrameNanos`; the residue-per-bar loop, the wall and the rubber band unchanged | `ChartFlingTest`: 2 000 px/s → 1 212 ms, 4 000 px/s → 1 394 ms, steps never speed up, travel = v/f, stops on its own |
| `Surface.setFrameRate` | **`ChartFrameRate`**: `setRequestedFrameRate(HIGH)` on 15+, `SurfaceControl.Transaction.setFrameRate(child, highest mode, COMPATIBILITY_DEFAULT)` on 12–14 via `rootSurfaceControl.buildReparentTransaction`, released on detach; a vote to the compositor, not a command | compiles; the effect is a device's |
| context menu on right-click **and long-press** | right-click since 4.60.0; **long-press since 4.63.0**: a press that lifts inside the touch slop opens the menu at its reading, a press that moves is tracking | `ChartContextMenuTest` «a long press that lifts where it landed opens the menu on touch», «a long press that drags is the crosshair, not the menu» |
| `MotionEventPredictor` | 4.55.0, `ChartStrokePredictor`; now kept by name | `mapping.txt` |
| everything else in the item | 4.55.0 / 4.60.0 — the table under «Item 4 — chart physics (4.60.0)» | as there |

**The grep the acceptance asks for** — the chart module's sources (`chart/ui/src/main`, `chart/core/src/commonMain`), code lines only:

| symbol | where |
| --- | --- |
| `exponentialDecay` | `ChartFling.kt:7` (import), `ChartFling.kt:24` (`exponentialDecay(frictionMultiplier = …)`) |
| `setFrameRate` | `ChartFrameRate.kt:57` (`SurfaceControl.Transaction().setFrameRate(…)`); `setRequestedFrameRate` at `ChartFrameRate.kt:34` and `CoineProChart.kt:743` |
| `MotionEventPredictor` | `ChartStrokePredictor.kt:6, 28, 31, 34` |

**And the grep of the release `classes.dex` itself** (4.63.0, `app-release-unsigned.apk`, one dex, 9 629 276 B), which is what the owner's audit reads:

| literal | 4.62.0 | 4.63.0 | why it changed |
| --- | --- | --- | --- |
| `exponentialDecay` | 0 | **1** | the fling now calls it, and the function is kept by name (`DecayAnimationSpecKt.exponentialDecay`, `FloatExponentialDecaySpec`) |
| `setFrameRate` | 0 | **1** | `SurfaceControl.Transaction.setFrameRate` in `ChartFrameRate`; platform method names are never obfuscated |
| `setRequestedFrameRate` | 1 | 1 | — |
| `MotionEventPredictor` | 0 | **1** | `-keepnames`; 4.62.0 had it as `j6.a` (`mapping.txt`) |
| `CertificatePinner` | 0 | **1** | `-keepnames`; the pins were always there as literals (`sha256/RO8Xw…` = 1 in both) |
| `ChartStrokePredictor`, `ChartFling`, `ChartFrameRate` | 0 | **1** each | `-keepnames` |

The `-keepnames` rules keep only the names — nothing that R8 would otherwise remove is kept — and are there so the next audit greps the same dex and finds the same words.

| acceptance | state |
| --- | --- |
| `ChartFlingBenchmark` p95 ≤ 8 ms phone, ≤ 12 ms tablet 4-chart, 0 jank | **needs a device** — `benchmark/…/ChartFlingBenchmark.kt` runs with `./gradlew :benchmark:connectedBenchmarkAndroidTest`; nothing here has a GPU |
| 120 fps side-by-side recording vs TradingView | **needs a device** |
| grep report | above |

### RUN C — item 5 (4.64.0) — done to the acceptance list; the phone's numbers remain

The acceptance list, row by row:

| acceptance | state | proof |
| --- | --- | --- |
| `docs/namascript/SPEC.md` | §1–§10, with §5.5–§5.8 from 4.61.0 and the short strategy form from 4.64.0 | the file; `ScriptDiagnosticsTest` |
| typed AST + compiled incremental evaluation | `TypeChecker` → `CompiledScript` → `IncrementalRunner` (4.56.0), the 4.61.0 surface typed and marked whole-run where it must be | `CompilerTest`, `NamaScriptTest` |
| sandbox: CPU / memory budget, timeout | E401 nodes, **E407 memory (4.61.0)**, E406 time | `NamaScriptTest` «the memory budget refuses…» |
| diagnostics with line/column and a fix hint in fa/en | 28 codes, every one with both languages and a hint | `ScriptDiagnosticsTest` |
| full `input.*` with auto-generated settings UI | 4.56.0 | `sem_input_*` (7) |
| `request.security` with lookahead rules | 4.56.0, confirmed mapping, E210 | `sem_security_*` (3) |
| ≥ 100 conformance scripts, one per `ta.*` | **380** scripts; **113 `ta.*` functions, each with a script** — `gen_ta_t3` was the one missing and is recorded now; a test holds the rule | `ConformanceSuiteTest` «every ta function in the reference has a script of its own» |
| editor: highlighting, autocomplete with signatures, squiggles, snippets, console | 4.56.0 + 4.61.0 | `NamaSyntaxTest`, `CodeFieldTest`, `StudioHelpersTest` |
| split view code \| chart inside `chart_panel_script` | 4.56.0 / 4.58.0 | `panel-script-*` frames |
| **`assets/help/content.json` for every new function (fa + en)** | **six new entries** — `na`, `var`, `str`, `labelnew`, `linenew`, `boxnew` — each with title, use case, what, how (steps), tips and an example in both languages; **`strategy` rewritten** to the shipped semantics (both call forms, next-open fills, one trade at a time, the card, what the model leaves out — its old text described `ref()` and a bar-by-bar engine that do not exist). Catalogue: 244 entries | `HelpCatalogTest` (count, both languages on every field, no case collisions) |
| Pine's short strategy form | `strategy.entry("long" \| "short", cond)`, `strategy.close(cond)` accepted beside the full form, so the help's own examples run | `sem_strategy_pine_short_form`, `NamaScriptTest` «the short Pine form…» |
| Pixel 6a: compile < 50 ms, eval < 40 ms, realtime < 2 ms | **needs the phone**; JVM, isolated: compile 1.4 ms, eval 168 ms, realtime 6.7 / 6.2 ms (item 5 table above) | `ScriptPerformanceTest` |

Numbers: 244 help entries (6 new, 1 rewritten), 380 conformance scripts (2 new), 113 `ta.*` functions covered, 94 tests in `:namascript:jvmTest`.

### RUN D — item 6 (4.65.0) — done; the release dex names no third party

| acceptance | done | proof |
| --- | --- | --- |
| OkHttp `CertificatePinner` for `coineprofx.com` and the crypto host, primary + backup, `docs/security/PINNING.md` | 4.57.0, re-measured live 2026-09-10 (4.62.0): each host meets a primary and a backup on the chain it serves; the class is kept by name since 4.63.0 so the dex greps for it | `CertificatePinDefaultsTest`; `PINNING.md` §«Re-measured 2026-09-10»; dex: `CertificatePinner` 1, `sha256/RO8Xw…` 1 |
| Investing / Cointelegraph / ForexFactory through the backend behind a flag, graceful unavailable state, **no direct calls in release** | **the hosts are not in the release build**: `ThirdPartyWires` in `core/marketintel/src/debug` names them, the `release` source set's twin returns no feed and no calendar URL; `PublicNewsFeed.feeds`, `PublicCalendarFeed.URL` and the calendar provenance read it. The 4.57.0 flag still gates a debug build. Empty sections say so: `news_empty`, `guest_news_empty`, `calendar_empty` | dex grep of 4.65.0's release `classes.dex`: `investing.com` 0, `cointelegraph.com` 0, `faireconomy.media` 0, `forexfactory` 0 (4.62.0: 1, 1, 1, 0); `check-release-surface.py` → «Release surface is clean: … no third-party host»; `PublicFeedTest` «with direct feeds off…» (debug, the flag) |
| grep proof: zero `investing.com`, `cointelegraph.com`, `faireconomy.media` literals in the release dex | **0 / 0 / 0** — and `scripts/quality/check-release-surface.py` now fails a release whose dex carries any of the four hosts (`forexfactory.com` included) | the script's line on 4.65.0's APK, below |

### The audit of 4.65.0, answered (4.66.0)

**Run C, «not verifiable from the APK».** The auditor read a 660-byte dex growth from 4.62.0 to
4.65.0 and asked for the editor to be shown rather than described. Three things:

1. *Where the language landed.* The compiler, the checker, the incremental runner, the inputs UI
   and the editor's colouring/squiggles/autocomplete shipped in **4.50.0 → 4.56.0**, and the
   4.61.0 surface (`na`, `var`, `str.*`, objects, `strategy.*`, E407, the fast paths) shipped in
   **4.62.0** — so 4.62.0 already carried all of it, and 4.63.0–4.65.0 added help JSON (an asset,
   not dex), two functions' worth of parsing and the short strategy form: that is the 660 bytes.
   The dex growth the auditor's own table shows for 4.57.0 → 4.62.0 (9.59 → 9.63 MB) is where the
   4.61.0 language is. Source, `git diff --shortstat` over `namascript`, `feature/script`,
   `core/script`: 4.50.0 → 4.57.0 **+1 358 / −141** lines; 4.57.0 → 4.65.0 **+1 012 / −60**. The
   module today: `namascript/src/commonMain` 5 354 lines, the studio 1 441, the controller 280;
   **121** classes under `com.coinepro.core.script` and **90** under `com.coinepro.feature.script`
   in the release mapping; `namascript-jvm.jar` 312 249 B.
2. *The screenshots.* `StudioProofTest` renders the studio at phone size in Persian and writes
   `docs/qa/screenshots/4.66/` (half size; the full frames are in the zip sent with the APK):
   `studio-autocomplete-fa` — the strip under the code field offering `ta.sma(close, 20)`,
   `ta.smi(close, 20, 5, 5)`, … for the word `ta.sm`, and the E301 card for it at «خط ۲، ستون ۸»;
   `studio-diagnostic-fa` — a `)` never closed, reported at «خط ۳، ستون ۱» with the fix hint and
   the code E102, the failing token underlined; `studio-console-strategy-fa` — the strategy card
   (net return, closed trades, win rate, profit factor, drawdown) and the console with the run's
   milliseconds; `studio-inputs-fa` — the generated inputs (slider, chips, switch, swatches). Each
   test asserts the thing it shows is in the semantics tree, so a broken studio cannot pose.
3. *The list of scripts and the test output.* `docs/qa/NAMASCRIPT_CONFORMANCE.md` indexes all
   **380** scripts (278 generated, 102 hand-written) with their expectations; `:namascript:jvmTest`
   on 4.65.0: **92 tests, 0 failures, 10.2 s** (`ConformanceSuiteTest` 3 tests over the 380
   scripts in 0.77 s; `ScriptPerformanceTest` 2 tests, 6.4 s).

**Run D, the optional pin-set.** Done: `network_security_config.xml` now pins both hosts with the
same nine digests and the same expiry (2027-03-01) as `DEFAULT_CERTIFICATE_PINS`, so the
platform's trust manager enforces them for every TLS connection — the terminal's WebView
included. `NetworkSecurityPinsTest` parses the XML and holds it equal to the build's list.

**`tradeyar.trade-future.ir` as the crypto host**: stays until the backend moves; not the app's
to decide.

**Fonts (run A′).** Not done: the Vazirmatn download was declined in this session, and the face is
a standing constraint (`CLAUDE.md`: IRANYekanX) that only the owner changes. The typography is
wired by weight, so either the Pro files or another family is a one-file change when decided.
*(Closed in 4.68.0: the owner supplied IRANYekanX Pro — see RUN A above.)*

**Device proofs.** `docs/qa/DEVICE_PROOFS.md` has the exact commands with this repository's class
names and tasks (the benchmark is `com.coinepro.benchmark.ChartFlingBenchmark` under
`:benchmark:connectedBenchmarkAndroidTest`, not the plan's guess), and
`NamaScriptDevicePerfTest` — an instrumentation test in `app/src/androidTest` — measures compile,
evaluate and realtime on the phone and writes `namascript-perf.json` beside a logcat line.

### RUN E — tools polish and tablet parity (4.67.0) — done where a JVM can do it; four device proofs remain

Five items, one run. What each one is now, what pins it, and what only a device can add.

| item | done | proof |
| --- | --- | --- |
| 1. Pane legend: name + live values in indicator colours, eye/gear/× on tap, reorder, move to a new pane / merge | the legend was already the reference's (rows in the study's colour, live values while tracking, eye · gear · × on a tap; 4.4x). **New:** panes are *arranged* — `movePane` (up/down), `mergePane` (a guest's lines and levels ride in its host under a joint title «ATR 14 · MACD 12/26/9»), `separateOverlay` (an EMA or a band in a pane of its own); the order, the merges and the separations persist per symbol (`SymbolChartState` fields 19–21) and the legend's row resolves to the host. The controls sit on the settings sheet's «نمایش» tab under «جای پنل» rather than as a drag on the legend — a drag on the plot already belongs to the pan and to the drawings, and a reorder by accident is worse than one that costs a tap | `ChartArrangementTest` (3 tests: move / merge / separate; a guest cannot host; write-back and restore), `tools-1-pane-legend-{phone,tablet}-fa` |
| 2. Floating mini-toolbar above the selected drawing + magnifier bubble while dragging a handle + haptic on snap | the toolbar (colour · width · style · lock · duplicate · text · settings · delete, since 4.4x) now **follows the selection**: the draw pass publishes the selected drawing's box (`onSelectionBounds`) and the bar sits just above it, clamped to the canvas. **Magnifier:** `Modifier.magnifier` (2×, 120×72 dp, 10 dp corners) at the finger while a handle is dragged, gone on release. **Haptic on snap:** already wired (`onSnap = { haptics.select() }`), and on every placed point | `tools-2-selection-toolbar-{phone,tablet}-fa`; the magnifier draws only under a live drag, which Robolectric has no finger for — device |
| 3. Rail flyouts per group with the last-used tool promoted + favourites strip on the chart edge | `DrawingState.lastUsed` (per `ToolGroup`, set on every arm, persisted as field 18) lifts the group's last tool to the head of its run with a gold pip — the flyout opens on it; the groups themselves keep their order, so a search result reads as before. **Favourites strip:** the pinned tools down the plot's leading edge (`FavouriteToolStrip`, up to 8), one tap from armed, hidden while a tool is armed so it never sits under the first point | `ToolRailPromotionTest` (3), `ChartArrangementTest` (last-used restored), `tools-3-rail-last-used-*`, `tools-3-favourites-strip-*` |
| 4. Indicator settings: every parameter of every built-in, live preview behind a 20 % scrim | `IndicatorParameter` + `ChartCatalog.PARAMETERS`: **54 knobs on 28 studies** beyond the length (MACD/PPO/PVO fast·slow·signal, Stochastic %D, StochRSI stoch·%K·%D, Bollinger/%B/BBW deviation, Keltner/Supertrend/VolStop multiplier, Envelopes %, Ichimoku 9/26/52, Chaikin Vol ROC, TRIX signal, UO 7/14/28, CRSI 3/2/100, SMI Ergodic 20/5/5, SMI, Chaikin Osc, Klinger, TSI, Mass Index, Coppock, Chande Kroll, KAMA, SAR step/max, HV annualisation) and **53 lengths**, sixteen of which were literals in the dispatch until this run. Defaults are the old literals, so an untouched chart is drawn exactly as before; values are clamped; titles carry them («MACD 8/26/7», «BB 20/2.5»). Stored per symbol (field 17), in layouts (field 12) and templates (field 6) through `ChartParamsCodec`. The sheet's «ورودی‌ها» tab lists them all; the scrim stays at 20 % so the chart redraws visibly under it | `IndicatorParametersTest` (4: order, bounds and clamp, *every* knob changes what is drawn and what the title says, defaults unchanged), `ChartArrangementTest` (clamp, default drops, length routes to the period), `SymbolChartStateStoreTest` / `ChartLayoutStoreTest` round-trips, `tools-4-indicator-inputs-*`, `tools-4-indicator-pane-*` |
| 5. Tablet parity: matrix 100 % from UI tests; screenshot matrix on four devices; 30-minute Monkey soak | `docs/qa/PARITY_MATRIX.md` regenerated: **every named screen has a render in every one of the six windows** — 30 renders added to `ScreenshotRenderTest` for the cells that were `—` (shell bar, indicator sheet, chart panes, alerts, screener, terminal, paper trade on tablet-portrait / Pixel Tablet / Tab S9 Ultra / fold-open / fold-closed). The «other» row is the uncategorised remainder and is not a screen. **Device matrix:** `DeviceProofTest` (instrumentation) takes the six run E scenes × dark/light × fa/en off the real framebuffer on whatever is attached, 24 frames per device, named by model. **Soak:** `scripts/qa/monkey-soak.sh` drives 30 minutes of Monkey and writes `docs/qa/soak/<model>-<stamp>.log` with the crash and ANR counts | the matrix; `DeviceProofTest`; the script — the four devices' frames and the soak log **need the devices** |

**The acceptance, line by line.** Screenshots for 1–4 on phone and tablet: `docs/qa/screenshots/4.67/` (12 frames, `ToolsProofTest`, each asserting what it shows). Parity matrix committed: yes, generated and gated by `check-cross-phase-consistency.py`. Soak log committed: **no** — the script is, the log needs a tablet. The 20-second «add EMA → Fib → recolour → lock → remove» recording beside TradingView: **needs a device** (`docs/qa/DEVICE_PROOFS.md` §3 has the scrcpy line).

**Two choices to know about.** Reorder and merge are sheet buttons, not a legend drag (above). The magnifier uses the platform's `Magnifier` through Compose's modifier rather than a hand-drawn bubble, so it renders the real pixels under the finger on Android 9+ and is simply absent below.

### RUN F — the chart in pixels, and the explaining stops (4.70.0)

The owner's first review of the shipped frames, item by item. Where an item was already true the
row says so and names the proof rather than claiming a change.

| # | asked | done | proof |
| --- | --- | --- | --- |
| 1 | Volume at 18 % height, 50 % alpha, behind price and studies | **already so** — `VOLUME_INLINE = 0.18f`, `VOLUME_ALPHA = 0.5f`, and `drawVolume` runs before the series and before every overlay in the same clip. What made it look taller in the reviewed frame is that 18 % of a *tall* single-pane plot is a tall band | `CoineProChart.kt`; the frames below |
| 2 | Collapsible one-line pane legend, 8 dp top margin, tap to open, remembered | **done**: at rest the study list is one row — the first study, its value and «+N» — and the head keeps the disclosure that opens every row with its eye, gear and ×. A crosshair opens it by itself, because a crosshair is the reader asking what the studies read. `rememberSaveable`, so it survives rotation and navigation. The plate's inset was already 9 dp | `run-f-chart-legend-collapsed` (ten studies, «+9» on one line); `ExplainerRemovedTest` renders prove the plate is three lines |
| 3 | Ticks at round time boundaries, density with zoom, 120 ms fade, date on day change | **done**, and it was two defects wearing one symptom. `TimeScale.roundHoursOnly` thins the hour candidates to a step that divides the day (12/6/4/3/2), exactly as `roundMinutesOnly` already did for minutes — so an H1 chart reads 00:00 · 06:00 · 12:00 · 18:00. And `TimeTick.boundaryTime` floors the stamp to the unit the tick stands for, so a feed whose bars open at 23 minutes past prints `12:00` rather than `11:23` under every day. The fade is a 120 ms linear crossfade over the labels that changed, driven from the draw pass; the first ladder never fades | `RoundTimeAxisTest` (5); `run-f-chart-zoom-30/70/300` |
| 4 | Countdown (mm:ss) under the last price, 200 ms flash on tick | **already so** — `countdownLabel` + `drawLastPrice(secondLine = …)`, and `TICK_FLASH_MIX` lifts the tag towards white for 200 ms. It was absent from the reviewed frame because that fixture's last bar closed in October: a countdown to a bar that has already closed is correctly nothing | `run-f-chart-countdown` — `51:35` under `2,704.8` on a fixture whose last bar is the hour now running |
| 5 | One timeframe name everywhere, in Latin | **done**: `ChartInterval.code` (`H1`, `M15`, `D1`, `30S`) on the toolbar, the H/L row, the reading panel and the studio header. `label` («۱ ساعت») survives only inside sentences, which is where a prose count belongs | `grep -rn "interval.label"` → the two warming sentences and the custom-range menu item |
| 6 | Vector pair icons, no emoji in the symbol header | **already so** — `CoineProPairLogo` draws two overlapped vector circles from `ARTWORK`, and `SymbolArtwork.covers` keeps a symbol without artwork out of every list. There is no emoji anywhere in the chart header: `grep -rn` over the sources finds none | the frames; `CoineProPairLogo.kt` |
| 7 | A price tag on the axis for every level line | **done**: each `PriceLevel` now draws an axis tag in its own colour, between the ladder and the live-price tag so the live price still wins | `run-f-chart-levels` — R1 at `2,625.5` red, P at `2,611.6` gold, S1 at `2,597.6` green |
| 8 | ~70 bars with a 10 % right offset, zoom kept per symbol/TF | **done**: `DEFAULT_BARS_PER_VIEW` 80 → **70**, `RIGHT_MARGIN_SHARE` 6 % → **10 %**, and the zoom is stored per timeframe in `SymbolChartState.zoom` (field 22) rather than only in the composition | `ChartArrangementTest` «the zoom is remembered per timeframe and restored onto the same one» |
| 9 | One `surface0`; one up green, one down red | **done**: `terminal` is now `stage` in both themes, and `buy`/`social` take the market green. The red is the one place the two cannot be identical: TradingView's `#F23645` measures **4.44:1** as ink on the elevated card and a change pill sets 13 sp, so every red **figure** is `#F6465D` (4.89:1) while the *candles* keep the reference's red, which is a fill and carries no text | `SurfaceLadderTest`; the palette's own note |
| 10 | Explainer cards out; first-run coach-mark instead; a UI test | **done**: `CoineProTeachingStrip` now *registers* with `CoineProTeachingHost` at the root of the app, which floats the sentence at the foot of the screen for six seconds on first run and dismisses it for good. No page gives up a row for it, and the «این صفحه چیست؟» line a dismissed strip used to leave behind is gone with it | `ExplainerRemovedTest`: four tests — Home, Watchlist and Chart draw no teaching text after the first run, and on the first run the chart's coach-mark's top edge is in the bottom third of the window |
| 11 | Home: 4×2 icon grid, no borders, signal keeps the accent, sparklines on the rows; watchlist: real 24 h lines ≥ 48 points, bare header icons | **done**: six round glyphs on the raised surface laid out four and two, the signal in gold; `CoineProMarketRow` gained a sparkline column and Home passes the same store the watchlist uses; `SparklineStore` now fetches **48 half-hourly** closes instead of 24 hourly ones — the "Bezier" the review saw was the sampling, since the renderer has never smoothed; the watchlist's search action is a bare 24 dp glyph | `run-f-home-*`, `run-f-watchlist-*` |
| 12 | Lint: a numeric style with Persian digits fails the build | **done**: `check_numeric_styles_are_latin` in the consistency gate reads every `Text` whose style is `.numeric()`/`numericTextStyle`/`Numeric` and fails if `toPersianDigits()` is inside the same call. Verified against a planted violation, which it caught, and the repository is clean | the gate |

**The frames.** `docs/qa/screenshots/4.70/` — Home, Watchlist and Chart in dark and light, Persian
and English (12), plus the collapsed legend with ten studies, the three zoom levels, the level
tags and the countdown. The 4.69.0 set beside it is the "before". Every frame is rendered by
`RunFProofTest`, which asserts what each one shows.

**What a device would still add** (unchanged from run E, plus one): the 10-second recording of the
countdown ticking and the tick flash — both are per-frame animations, and a still frame can only
show the state, not the movement.

### RUN G — the i18n leaks, and the rest of run F (4.71.0)

The owner's second review, from eighteen screenshots. The heaviest finding was not on his numbered
list at all: **the app was bilingual everywhere except where it names a market.** A symbol's name is
keyed by an ISO code rather than by a resource id, so it was the one part of the interface
`strings.xml` could not reach, and an English reader opened the chart on «طلا / دلار آمریکا».

| # | asked | done | proof |
| --- | --- | --- | --- |
| A | Bilingual symbol names everywhere a symbol is named | **done**: `SymbolNames` carries an English table for each of its six (currencies, short currencies, metals, indices, energy, crypto — 129 keys, key-for-key with the Persian ones); `SymbolClassifier` fills a `descriptionEn` in every branch, in that category's own shape; `SymbolMeta.description(english)` / `listDescription(english)` choose. Screens ask through `localName()` / `localRowName()` in the design system, which read **the composition's configuration** rather than `Locale.getDefault()` — the app sets its language per app, so those two legitimately disagree. Off composition (the widget worker) the language comes from `AppLanguageStore`. The catalogue is searched by **both** names, so an English reader typing «طلا» still finds gold; `MatchField.DESCRIPTION_EN` says which name a highlight belongs to, so the mark never lands on the wrong letters | `RunGProofTest`: `theEnglishChartHeaderNamesTheInstrumentInEnglish` (Gold / US Dollar, and no Persian left), `theEnglishWatchlistNamesEveryRowInEnglish` (Bitcoin/Tether, Gold/Dollar), `thePersianChartHeaderIsUnchanged` |
| B | Numbers from the **app** locale; Latin digits in every `numeric*` style | **done**: `Int.proseDigits()` replaces `toPersianDigits()` at every prose count the interface draws — 141 call sites across 59 files — and it comes in two halves: a composable one that reads the screen's own configuration (so a render test's locale override is obeyed), and a plain one defaulting to `AppLocale`, for the code that runs with no screen. `toPersianDigits` stays for the two callers that mean it literally: a Solar Hijri date, and a count inside a hardcoded Persian sentence. The lint from run F item 12 already ships | `theEnglishWatchlistCountsInLatinDigits` — «4 symbols», and **no Persian digit anywhere on the English screen**; `thePersianWatchlistStillCountsInPersianDigits` — «۴ نماد» |
| C | One resource for the composite; the default list name from resources | **done**: `home_change_today` = «%1$s · %2$s today» / «%1$s · %2$s امروز» is one string with both figures and the period word in it, chosen by `toHomePortfolio` from the server's own `change.period`. The base watchlist keeps its **stored** name — it is user data from the first launch, and a name substituted at write time would snap back the moment the reader cleared theirs — and `watchlist_default_name` is drawn in its place until they rename it | `theChangePillIsOneTranslatedSentence` (the proof builds the portfolio through the **real adapter**, not through a literal); `theBaseListIsNamedInTheScreensLanguage` |
| D | Legend `NAME PARAMS · VALUE` with «▸ +N» at the end; 10 % right offset; collision avoidance for every axis tag | **done** in three parts. The row is now name · value with the dot in the muted ink, and the collapsed count is a chip **after** the value rather than welded onto the name («EMA 20 +1 2,699.6» → «EMA 20 · 2,699.6 ▸ +9»). The right offset was already ten per cent and is now *measured* rather than asserted from the constant: from the newest bar's right edge to the axis is 10.0 % of the plot at the default zoom — the reviewed «~۵٪» is what the same margin measures to the bar's **centre**. And the ladder now steps around **every** tag in the gutter, not only the live price: `placeLevelTags` assigns rows before the axis is drawn, the live price is in the list first and so wins every collision, each level takes a row only if the rows already taken leave space for it, and the day's reference takes its row in the same pass and gives way to both — which is the **overlap the owner photographed**: «2,700.0» from the ladder came out under «2,698.3» from the previous close, because that tag was placed *after* the axis was drawn and the axis could not know about it | `theCollapsedLegendPutsItsCountAtTheEnd`; `ChartEdgeMarginTest` «the resting margin is a tenth of the plot, measured»; `run-g-chart-legend-ten` |
| E | Vector pair icons, 20 dp in the header and 32 dp in lists; zero emoji in a symbol header | **done**: the header's pair was already 20 dp of vector artwork (run F proved it); the list mark goes 30 → **32 dp**, which on `CoineProPairLogo`'s proportions is a 24 dp front disc over a 16 dp quote. The dense watchlist table keeps its 28 dp mark and the reason is written where the constant is: that row carries a flag rail, a grip, a star, a ticker, a name and four figure columns on a 393-point phone. The emoji rule is now a **test** rather than a claim | `noSymbolHeaderCarriesAnEmoji` — every string XAUUSD's and EURUSD's headers draw, scanned for U+1F000–U+1FAFF; `run-g-header-xauusd`, `run-g-header-eurusd` |
| F | Sparklines from ≥ 48 real closes; a flat grey line where there is none | **done**: the store has fetched 48 half-hourly closes since run F; what run G fixes is the two ends of it. A row whose line never arrived now draws a **flat rule in the disabled ink** instead of leaving a hole — and never a curve, which is the one thing this component is forbidden to invent. And the screenshot fixture was the sum of two sines, a *smooth* function whose 48 samples draw four clean arcs: it is now a seeded random walk, so a proof frame shows what the renderer does with a day of real noise | `theSparklinesAreADaysWorthOfRealCloses` (≥ 48 points, and two markets never draw the same shape); `run-g-watchlist-sparkline` |
| G | The reading panel closed by default, opened by drag, remembered | **done**: `SymbolChartState.readingsOpen` (field 23) holds it **per symbol**, so a reader who drags the readings open on gold finds the index at full height. The handle takes a vertical drag as well as a tap — a panel is a height, and a height is grabbed — with a threshold of a fifth of its row so a thumb travelling down the page does not open it | `theReadingsPanelIsClosedOnArrival` — the handle is on the page and the trend reading behind it is not; `run-g-chart-readings-closed` |
| H | «study»/«مطالعه» → «indicator»/«اندیکاتور», both words forbidden | **done**: `indicator_settings_arrangement_note` in both languages, and the `chart_band_studies` **key** renamed to `chart_band_indicators` so the word is not in the repository either. Both words are in `FORBIDDEN_UI_WORDS`, with no exception list: the academy's «streak of study» was the one honest use and is now a learning streak | the consistency gate, which failed on the leftovers and passes now |

**The frames.** `docs/qa/screenshots/4.71/` — Home, Watchlist and Chart in dark and light, Persian
and English (12), plus the two headers, the ten-indicator legend, the closed readings panel, the
sparkline row and the six named-and-counted assertions. `docs/qa/screenshots/4.70/` beside it is
the "before". Every frame is rendered by `RunGProofTest`, which asserts what each one shows.

**Two defects found by looking at the frames rather than by the review.** The English watchlist's
*column headings* were Persian — «آخرین · ٪ تغییر · روند» over four English rows — because they are
stored beside the column set (user data, in a module with no resources) rather than in
`strings.xml`; `WatchlistColumn` carries an English label now and the proof asserts both. And the
price-axis overlap survived the first fix: the day's reference was still placed after the ladder, so
it alone could still print over a gridline label. It is in the same pass now, and the crop in
`run-g-chart-legend-ten` is the before-and-after.

**One defect found by the proof rather than by the review.** `WatchlistSyncNotice` formats its
counts against an explicitly passed `AppLanguage`; the mechanical rewrite would have had its Persian
branch return Latin digits. It is back on the literal conversion, which is what that branch means.

### RUN H — the tablet, reviewed as a tablet (4.72.0)

The owner's first review of the tablet screens, scored 6/10. The finding under all ten was one
shape: **the page was a phone's page with columns bolted to it.** A 280-point tool palette, a
360-point side panel and a plot that took its fraction of the *screen* rather than the column left
the chart about 45 % of a Pixel Tablet's glass, with a band of empty page under the toolbar — and
the drawing tools, the market reading and the editor were all in Persian whatever language the app
was in, because none of them is a string resource.

| # | asked | done | proof |
| --- | --- | --- | --- |
| 1 | A 48 dp vertical rail of group icons with flyouts; the full grid only in «all tools»; favourites pinned; the wide panel out of the default layout | **done**: `ChartToolRailColumn` is one 48-point column — twelve group glyphs in the catalogue's own order, the reader's favourites under a rule, and «all tools» at the foot. A group opens a 216-point **flyout beside the rail**, not a popup: a `DropdownMenu` is its own window, which is absent from a screenshot and awkward under a test, and a column is both hit-testable and photographable. Arming a tool closes it. `CHART_TOOL_COLUMN = 280.dp` is gone; the palette it drew is now what the sheet behind «all tools» shows, so every one of the 92 tools is reachable from the rail by construction | `RunHProofTest.theRailAndAFlyout` — the rail is on the page, **measured from its own semantics bounds at 48 dp**, and tapping «Lines» lists «Trend line»; `run-h-rail-flyout-en-dark`, `run-h-rail-flyout-open-en-dark` |
| 2 | The chart fills the column height on every tablet layout; «market reading & tools» into a side-panel tab, never over the candles | **done**: the page's column stops scrolling once there are rails beside it (`fills = columns != NONE`) and the canvas takes `weight(1f)`, so the plot is every point the legend and the toolbar did not take. The readings are no longer a column or a disclosure at all — `ChartWorkbench` turns the `readings` slot into a `ChartSidePanel(READINGS_PANEL_ID)` and prepends it to the side rail, so they are a **tab** like depth and alerts | `run-h-chart-depth-*` (three devices/themes) — the plot runs from the header to the time axis with no blank band; `ChartWorkbenchTest.a tablet with a docked panel reports both, so the page draws no readings of its own` |
| 3 | Expanded budget: rail 48 · plot ≥ 65 % of what is left · panel 360–480 drag-resizable, floor 320 · icon rail 56. List-detail: 320–400 + chart | **done** as arithmetic rather than as constants: `panelWidthFor(windowDp, draggedDp)` clamps the panel to `[320, 480]` **and** to whatever keeps the plot at `CHART_PLOT_SHARE = 0.65` of the space between the two rails. The grip is a 6-point draggable strip with `contentDescription = "side-panel-grip"`; the dragged width is remembered per window | `ChartWorkbenchTest.a docked panel never takes the plot below its share` (1024, 1280, 1480, 1600 dp) and `a drag is clamped to the panel's own range`; `no rail is ever opened at the cost of the plot's floor` walks every width from 300 to 2400 in 4-point steps |
| 4 | NamaScript on Expanded: editor ≥ 400, code\|chart 50/50 toggle, no soft wrap, line numbers, minimap, keep the tabs | **done**: the panel's own floor is the 400 the editor needs. The code field sits in a `horizontalScroll`, which gives it unbounded width and so **stops it wrapping** — `softWrap = false` is not on the `TextFieldValue` overload, and a wrapped line in a language with 90-character `ta.*` calls is an editor you cannot read a stack trace against. Line numbers were already there; the minimap is a 24-point `Canvas` drawing one bar per line at 60 % of its length; the split toggle is two chips and remembers its state per window | `run-h-chart-script-en-dark`, `run-h-chart-script-fa-dark`; `CodeFieldTest` |
| 5 | Tool names, tool groups, market-reading values and every panel label from `values/`+`values-fa/`; a UI test that walks the tablet in `en` and fails on Arabic script | **done**, and the leak was bigger than the review: the tools are a catalogue in `:chart-core`, a module with **no Android resources**, so `strings.xml` could not reach them. `DrawingTool` and `ToolGroup` carry an `englishLabel` beside the Persian one — 92 tools and 12 groups, name for name — and `label(english)` chooses. `ChartReading` does the same for «متوسط · کم · خنثی», and publishes `TRENDING_FLOOR` so the colour rule compares the **number** rather than the word it used to match on. The editor's own 57 literals and the legend's six controls became resources | `TabletEnglishTest`: renders the tablet chart and the tablet watchlist in `en-rUS-w1280dp` and fails on any `[؀-ۿ]` **including content descriptions**, plus `everyToolAndGroupHasAnEnglishName`, which also fails a row copied from the column beside it |
| 6 | The timeframe **code** in multi-chart headers; the ladder bound to the active chart's symbol; one «Depth of market» title; «نمااسکریپت» spelling only | **done**: `ChartPanesScreen` writes `state.interval.code` («H1»), not the prose name. The ladder was already bound to `activeChartSymbol` in the app — the BTCUSDT-beside-XAUUSD frame was a **fixture** defect, and the fixture is seeded per symbol now. `DepthOfMarketScreen`/`DepthOfMarketBody` take `showTitle`, so the docked copy draws the name once and the rail's tab draws the other | `fourChartsEachWithItsOwnMarket` — four symbols, «H1» present, «۱ ساعت» absent; `theDockedLadderNamesItselfOnce` counts the title in **drawn text only**, since the rail glyph carries it as a description |
| 7 | Volume at 18 % of the plot and 50 % opacity on every size class | **already true, now stated**: `VOLUME_INLINE = 0.18f` and `VOLUME_ALPHA = 0.5f` are two constants with no size-class branch anywhere. What made the tablet's band look like a quarter of the plot was item 2 — a short plot with a band scaled to it — and the frames after the fix measure the tallest bar at 18 % of a full-height plot | `run-h-chart-depth-en-dark` and the constants, which a branch would have to be added beside |
| 8 | The phone leftovers: a 10 % right offset, and axis labels never hidden by a tag | **done**: the offset is `RIGHT_MARGIN_SHARE = 0.10f`, measured from the newest bar's **right edge** to the axis rather than asserted from the constant. The collision rule is now a pure function, `placeTagRows`, so it can be tested away from a canvas: the live price is reserved first and wins everything, the ladder's rows are reserved next, and a tag that finds no free row is **dropped rather than nudged** — a tag beside the wrong price is worse than no tag, and its rule is still on the plot with its name at the left end | `ChartPixelsTest`: five cases including «the live price wins every collision» and «no two placed tags ever overlap, whatever comes in»; `ChartViewportTest` for the margin; `run-h-phone-levels-fa-dark` and `run-h-phone-levels-en-light`, six overlays deep |

**The frames.** `docs/qa/screenshots/4.72/` — the rail closed and with a flyout open, the chart with
the ladder docked (Pixel Tablet dark and light, Tab S9 Ultra), the chart with the NamaScript split
in both languages, the list-detail in both, the four-chart layout with four markets, and the two
phone frames for item 8. Every one is rendered by `RunHProofTest`, which asserts what it shows.

**What the rail cost and what it returned.** On a 1280-point Pixel Tablet the 4.71.0 page spent
280 points on the palette and 360 on the panel and left the plot 45 % of the window. The same page
spends 48 and, at the panel's ceiling, keeps the plot at 65 % — 764 points against 476, which is
**288 points of chart** bought with one tap between a group and its tools.

**One thing this run did not do.** The phone's page still leaves a band of empty page below the
collapsed readings row, for the same reason the tablet did: under a `verticalScroll` a plot cannot
take a `weight`. On a tablet that band was the defect item 2 names; on a phone the six bands under
the plot *are* the page, and the fix there is a measured layout rather than a flag, so it is not
smuggled into this run.

## Definition of done — as it stands

- [x] §0 copy hygiene done; lint enforced (`tools/i18n/lint_strings.py` through the consistency gate).
- [x] Modules extracted; architecture tests green; JVM tests for the core modules run in CI.
- [~] §2.2 series types and §2.4 drawing behaviours with goldens; multi-chart layouts; benchmarks. All series types render and 36 chart-type goldens exist; drawing behaviours are pinned by gesture tests, **not** by goldens; layouts 1–8 done; benchmarks need a device.
- [~] NamaScript SPEC written; 378 conformance scripts pass; editor with autocomplete (with signatures), snippets, a console and diagnostics on phone and tablet; `na`, `var`, `str.*`, objects and `strategy.*` within the vectorised model; Pine paste helper works — **as a library**, not yet a button in the studio; a bar-by-bar VM is v2.
- [~] Tablet: adaptive layouts, rail, list-detail, side panels, multi-chart; parity matrix generated and **full for every named screen at every window** (4.67.0); input matrix written and its keyboard/pointer rows tested; the four-device screenshot matrix (`DeviceProofTest`) and the soak (`scripts/qa/monkey-soak.sh`) are wired and **need the devices**.
- [x] `pro-chart.com` in `BrandConfig`, App Links, legal; web plan documented.
- [x] This report, with numbers per section and the open product decisions (`docs/web/PLAN.md` §6, plus the locale decision in §0).

### The owner's plan A–E (4.63.0 → 4.67.0), in one line each

- A. Fonts — done (4.68.0): the Pro package arrived; Medium and DemiBold shipped as their own files, the four weights proven distinct; the licence code is the owner's to write in.
- B. Physics — done (4.63.0): the fling on `exponentialDecay`, `setFrameRate` on Android 12+, the long-press menu, every audited symbol greppable in the release dex; the benchmark and the recording need a device.
- C. NamaScript — done (4.64.0): six help entries fa+en, the strategy entry rewritten, Pine's short strategy form, every `ta.*` under a conformance script; the Pixel 6a timings need the phone.
- D. Network — done (4.65.0): no third-party host in the release dex, a gate that keeps it so; the pins measured live.
- E. Tools and tablet — done (4.67.0): panes arranged, the toolbar over the selection with a magnifier under the finger, the rail led by the last tool with a favourites strip on the plot, 54 knobs on 28 studies, the parity matrix full; the four devices' frames, the soak log and the recording need the devices.
- G. The i18n leaks — done (4.71.0): a symbol has two names and every screen asks for the one it is drawn in, every prose count follows the screen's language, the composite pill and the base list's name come from resources, the legend reads name · value with its count at the end, the readings panel opens on a drag and stays where it is left, and «study» is a word this repository no longer contains.
- H. The tablet — done (4.72.0): the tools are a 48-point rail with flyouts, the plot fills the
  column and keeps 65 % of it, the panel is drag-resizable between 320 and 480, the readings are a
  tab rather than a sheet over the candles, the editor is 400 points wide and does not wrap, and
  the tools, the groups and the market reading have English names for the first time.
- F. Chart pixels and the explainers — done (4.70.0): round time labels on the boundary they stand on, a one-line legend, level price tags, 70 bars with a tenth of air, one ground and one pair of market colours, the teaching sentence floated off the page, and a lint that keeps Persian digits out of a price column.

### The 4.58 run, in one line each

- FIX — done (4.58.0): «ترسیم‌ها», the lint, 56 tablet frames, the three scaffolds named.
- 3. Numerals and fonts — done where the material exists (4.59.0): 300-frame tick proof at 0.0 px; the two weights are the owner's files.
- 4. Chart physics — done (4.60.0): three counted layers, the right-click menu, Ctrl+Z/Y and `/`, every constant named; the fling benchmark and the 120 fps recording need a device.
- 5. NamaScript — done within the model (4.61.0): `na`, `var`, `str.*`, objects, `strategy.*`, E407, an 8× faster evaluator, signatures, snippets, console; a bar-by-bar VM is v2.
- 6. Network — verified (4.62.0): both hosts meet a primary and a backup live; the wires are provably off in release.

### The 4.52 run, in one line each

1. Locale inversion — done (4.52.0): `values/` English, `values-fa/` Persian, Gradle check, `aapt2` proof.
2. Tablet — done (4.53.0): Material's adaptive scaffolds under the app's own bar, rail, list-detail and chart; docked panels; device goldens; generated parity matrix.
3. Numerals and fonts — done where the material exists (4.54.0): `tnum` on every numeric style, glyph-shift proof; IRANYekanX Medium/SemiBold **await the owner's font files**.
4. Chart physics — done (4.55.0, completed 4.60.0): pixel pan with snap-at-rest, decay fling, axis-reset spring, three cached layers with counted misses, right-click menu, Ctrl+Z/Y and `/`, stylus prediction, frame-rate hint; the benchmark still needs a device.
5. NamaScript — done within the vectorised model (4.56.0, extended 4.61.0): typed pass, compiled script, incremental tail runs, `request.security`, full inputs, `na`, `var`, `str.*`, objects, `strategy.*`, the memory budget, a 8× faster evaluator, editor signatures/snippets/console; a bar-by-bar VM stays v2.
6. Network — done (4.57.0, re-measured 4.62.0): pins shipped for both hosts with expiry; release reads no third-party feed.

### The deviation from the first plan, closed

The first run kept Persian in `values/` against the plan; the owner repeated the instruction and item 1 of this run inverted the folders. `CLAUDE.md` now says which set is which.

### What a device would settle, in one list

Tablet soak (30 min Monkey/Espresso), tablet benchmark thresholds, the 60-second recording, the Pixel 6a NamaScript timings (< 50 ms compile / < 40 ms evaluate / < 2 ms realtime; measured here on the JVM at 1.4 ms / 168 ms / 6.7 ms for 300 lines × 20 000 bars after the 4.61.0 fast paths — v2's VM is the next step), S Pen pressure and palm rejection, the hinge's real dp on a Fold, TalkBack order on the rail, Galaxy Tab S9 Ultra goldens.
