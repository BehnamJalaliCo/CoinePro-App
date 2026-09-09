# Pro Chart — the plan's report

What the «chart, NamaScript, tablet, web» plan asked for, section by section: what is done, with
the proof; what is not, and why. Written as the work went, and finished with the last commit of
the run. Every claim below names the test, gate, file or number that backs it, so a reader can
check it rather than take it.

Versions: §0 shipped as 4.47.0, §1 as 4.48.0, §2 as 4.49.0. Later sections name theirs.

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

## §4 Tablet — see below

## §5 Web readiness — see below
