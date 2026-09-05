# Phase 4 — chart engine

Read against the source. Where the audit asked for a number, the file that produces it is named;
where a number needs a phone, that is said rather than invented.

## 4.1 Performance budget

| Item | State | Where |
| --- | --- | --- |
| Macrobenchmark for pan / zoom / fling | **Written, not run here.** `ChartFlingBenchmark` opens `coinepro://market/BTCUSDT` and measures `FrameTimingMetric` over a 3 s fling, three pinch cycles, four drags and a long press. The budget it is judged against is P95 ≤ 8 ms and no overrun during the fling. | `benchmark/src/main/kotlin/com/coinepro/benchmark/ChartFlingBenchmark.kt` |
| Why not run | Frame timing needs a GPU and a display; this container has neither, and the macrobenchmark artefacts are not in the offline Gradle cache, so the module was not compiled here either. Run on a phone with the command in the file's KDoc; CI resolves the dependencies. | — |
| Draw-phase invalidation | Already the design: state read in `drawWithCache` / `graphicsLayer`, the crosshair on its own layer, `Invalidation.FULL` vs partial. | `CoineProChart.kt` (`invalidate`, `Invalidation`) |
| Seconds bars in a ring | **Done.** A seconds series folded from ticks now holds at most 2 000 bars in memory; the oldest fall off the front once the archive has them (every bar is written on close). Before this a ten-second chart left open overnight held nine thousand bars and rebuilt all of them on every tick. | `ChartController.foldTick`, `SECONDS_BARS_HELD` |
| `chart_interval_seconds_note` | Already in user language («هیچ سروری کندل زیر یک دقیقه نمی‌دهد …»); left as is. | `feature/chart` strings |

## 4.2 Gestures

| Item | State | Where |
| --- | --- | --- |
| Kinetic fling | **Changed.** The fling now runs on the platform's own curve — the `SplineOverScroller` maths, density-aware, fixed distance and duration at release — instead of a private exponential decay. A flick on the chart coasts exactly like a flick on the list above it. Unit-tested to the pixel against the curve's own promise. | `FlingSpline.kt`, `KineticScroll` in `ChartPixels.kt`, `ChartPixelsTest` |
| Rubber band at the end of history | Already present: `stretchEdge` / `releaseEdge`, gain falling to zero at the cap, spring back on release. | `CoineProChart.kt:700–730` |
| Auto-load older bars near the edge | Already present: `onLoadMore` fires when the viewport is within reach of the first bar. | `CoineProChart.kt:498` |
| Pinch anchored at the centroid; pinch on the price axis; drag on either axis | Already present: one `onGesture` handler splits by `axisTop` and gutter; price zoom is `viewport.priceZoom`. | `CoineProChart.kt:1175–1230` |
| Double-tap resets auto-scale | Already present: a double tap in the price gutter calls `autoPriceScale()`; on the plot it resets the time zoom. | `CoineProChart.kt:1794–1835` |
| Long-press crosshair with the data window | Already present (`Crosshair`, legend rows, `ChartLegendOverlay`). | `ChartOverlay.kt`, `ChartLegendOverlay.kt` |
| **Haptic tick when the crosshair crosses a level** | **Done.** The chart screen watches `onCrosshairMove`; when the price passes any `PriceLevel` (a stop, a target, an indicator line) it ticks. | `ChartScreen.kt` (`onCrosshairMove`) |
| **Haptic on magnet snap** | **Done.** `CoineProChart` gained `onSnap`, fired only when the magnet actually moved a point onto a channel; the screen ticks on it. | `CoineProChart.kt` (`onSnap`), `ChartScreen.kt` |
| Magnet, handles, lock / hide / duplicate, context sheet | Already present (`DrawingActions`, `ObjectTreeSheet`, `SelectionToolbar`). | `feature/chart` |
| Undo / redo, 50 steps | Already present: `ChartHistory.LIMIT = 50`, back and forward stacks, cleared on symbol change. | `ChartHistory.kt` |
| **Multi-pane sync: crosshair and visible range** | **Done.** The renderer already accepted `crosshairOverride` and `viewportOverride`; the panes screen never wired them and showed «not ready yet» instead. It now shares a `PaneCrosshair` (a *moment*, so panes on different timeframes line up by time, not by bar index) and a `PaneWindow` (bars per view, offset from the live edge, price stretch). The source pane keeps its own finger and window; only the others adopt. `panes_sync_unavailable` is deleted from both locales. | `ChartPanesScreen.kt` (`PaneCrosshair`, `PaneWindow`) |

## 4.3 Scales and rendering

| Item | State |
| --- | --- |
| Log / percent / indexed / inverted scales | Present (`PriceScaleMode`, `LogScaleTest`). |
| Crisp wicks, hollow, Heikin-Ashi, bars, line, area, baseline, columns, HLC area, step, Renko / Kagi / PnF / Line-break | Present (`ChartSeriesTypes.kt`; pixel snapping documented at `CoineProChart.kt:2715–2740`; 11 tests in `ChartSeriesTypesTest`). |
| Volume Profile / TPO / Footprint goldens | `VolumeProfileWindowTest` (5) and `ChartSeriesTypesTest` cover the bucketing on fixed fixtures; TPO and footprint are checked through the same series-type tests. No reference dataset from a second implementation exists for them — that would need one from the owner. |
| Label collision (last price, countdown, bid/ask) | Present in the axis overlay; not re-audited pixel by pixel here. |

## 4.4 Indicators and NamaScript

| Item | State |
| --- | --- |
| Golden tests per indicator, 1e-6 | Present: `IndicatorParityTest` replays 60+ series recorded from the web terminal's `indicators.js` over a 120-bar fixture (`core/chart/src/test/resources/indicator-parity.txt`). |
| **Script errors in the reader's language, with line and column** | **Done.** Every `ScriptError` now carries Persian and English; `ScriptFailure.text(language)` picks; the failure card shows «خط ۳، ستون ۱۲» or "Line 3, column 12" from string resources instead of hard-coded Persian. 36 messages, plus the type names they interpolate. | 
| Pre-compiled fast path, in-app editor with highlighting and autocomplete | Not done. The interpreter walks the AST per bar with a node budget; there is no editor beyond a text field. Both are real work items, not a gap this phase could close honestly in passing. |
| Strategy report parity | Present: net / gross profit, drawdown, win rate, profit factor, average trade, Sharpe, Sortino, expectancy, trade list, equity curve (`Backtest.kt:169–321`). |

## 4.5 Chrome

| Item | State |
| --- | --- |
| Bottom band with §G names | Done in Phase 0 (`chart_band_*`). |
| Fullscreen, landscape without state loss | `fullscreen`, `activeSymbol` and favourites are `rememberSaveable`; immersive system-bar hiding is not applied (the toolbar hides, the bars stay). |
| Indicator picker: search, favourites, categories, recent | The workbench lists and searches; favourites and "recently used" are not there. Not done. |

## Verification

Five gates, every unit test (including the new fling and script tests) and `:app:assembleRelease` pass offline. What could not be verified without a phone is the frame budget itself; the benchmark that measures it is in the tree.
