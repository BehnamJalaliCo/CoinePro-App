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

## Definition of done — as it stands

- [x] §0 copy hygiene done; lint enforced (`tools/i18n/lint_strings.py` through the consistency gate).
- [x] Modules extracted; architecture tests green; JVM tests for the core modules run in CI.
- [~] §2.2 series types and §2.4 drawing behaviours with goldens; multi-chart layouts; benchmarks. All series types render and 36 chart-type goldens exist; drawing behaviours are pinned by gesture tests, **not** by goldens; layouts 1–8 done; benchmarks need a device.
- [~] NamaScript SPEC written; 351 conformance scripts pass; editor with autocomplete and diagnostics on phone and tablet; Pine paste helper works — **as a library**, not yet a button in the studio.
- [~] Tablet: adaptive layouts, rail, list-detail, side panels, multi-chart; parity matrix generated and honest (**not 100 %**: sheets, alerts, screener, terminal have phone renders only); input matrix written and its keyboard/pointer rows tested; soak test and device screenshots **need a device**.
- [x] `pro-chart.com` in `BrandConfig`, App Links, legal; web plan documented.
- [x] This report, with numbers per section and the open product decisions (`docs/web/PLAN.md` §6, plus the locale decision in §0).

### The 4.52 run, in one line each

1. Locale inversion — done (4.52.0): `values/` English, `values-fa/` Persian, Gradle check, `aapt2` proof.
2. Tablet — done (4.53.0): Material's adaptive scaffolds under the app's own bar, rail, list-detail and chart; docked panels; device goldens; generated parity matrix.
3. Numerals and fonts — done where the material exists (4.54.0): `tnum` on every numeric style, glyph-shift proof; IRANYekanX Medium/SemiBold **await the owner's font files**.
4. Chart physics — done (4.55.0, completed 4.60.0): pixel pan with snap-at-rest, decay fling, axis-reset spring, three cached layers with counted misses, right-click menu, Ctrl+Z/Y and `/`, stylus prediction, frame-rate hint; the benchmark still needs a device.
5. NamaScript — done within the vectorised model (4.56.0, extended 4.61.0): typed pass, compiled script, incremental tail runs, `request.security`, full inputs, `na`, `var`, `str.*`, objects, `strategy.*`, the memory budget, a 8× faster evaluator, editor signatures/snippets/console; a bar-by-bar VM stays v2.
6. Network — done (4.57.0): pins shipped for both hosts with expiry; release reads no third-party feed.

### The deviation from the first plan, closed

The first run kept Persian in `values/` against the plan; the owner repeated the instruction and item 1 of this run inverted the folders. `CLAUDE.md` now says which set is which.

### What a device would settle, in one list

Tablet soak (30 min Monkey/Espresso), tablet benchmark thresholds, the 60-second recording, the Pixel 6a NamaScript timings (< 50 ms compile / < 40 ms evaluate / < 2 ms realtime; measured here on the JVM at 1.4 ms / 168 ms / 6.7 ms for 300 lines × 20 000 bars after the 4.61.0 fast paths — v2's VM is the next step), S Pen pressure and palm rejection, the hinge's real dp on a Fold, TalkBack order on the rail, Galaxy Tab S9 Ultra goldens.
