# RUN TV — the web version as TradingView's desktop, measured against the owner's dissection

Source: the owner's «کالبدشکافی تریدینگ‌ویو» artifact (§04 chart, §05 alerts, §09 pixel specs, §12
scorecard) and a second pixel-level pass over the old Pro-Chart terminal
(`frontend/prochart/src/pages/BazaarNama.jsx` and `bazaarnama/`). Every row below is 5.16.0.

## The dissection's §12 «five next things», re-counted from today's tree

| Item | State | Evidence | Frame |
|---|---|---|---|
| Drawing anchors keep their OHLC channel (StickedPoint) | ✅ before this run | `DrawingState.bindings: Map<PointRef, PriceChannel>`, persisted and decoded by `ChartController` | — **a stored field, not a picture**; `DrawingWaveThreeTest` covers the binding |
| Alert audit log | ✅ before this run | `AlertAudit` records created / fired / delivered / snoozed / expired with price and timeframe; `AlertAuditSheet` | — **existing sheet**, unchanged by this run |
| Per-symbol memory of timeframe and studies | ✅ before this run | `SymbolChartStateStore` keyed by symbol | — **a store**, `SymbolChartStateStoreTest` is the evidence |
| Percent and Indexed-to-100 scales | ✅ before this run | `PriceScaleMode` REGULAR / LOGARITHMIC / PERCENT / INDEXED_100 | `docs/runs/RUN_TV/frames/tv-desktop-chart-en-dark.png` (the `%` and `log` switches in the bottom bar) |
| Volume profile (fixed, anchored, visible) and anchored VWAP | ✅ before this run | `volumeprofile`, `avolumeprofile`, `volumeprofile_ind`, `avwap` | — **existing tools**, `ChartCatalogTest` |

## §09 pixel specs on the canvas

| Item | State | Evidence | Frame |
|---|---|---|---|
| Crosshair `LargeDashed` [6, 6] at width 1 | ✅ 5.16.0 | `drawCrosshair` uses `LineStyleKind.LARGE_DASHED` | — **a crosshair only exists under a pointer**; the dash is one constant in `drawCrosshair` |
| Series line width 2 | ✅ 5.16.0 | `LINE_WIDTH_DP = 2.dp` (was 1.6) | `docs/runs/RUN_TV/frames/tv-desktop-template-en-dark.png` |
| OHLC bar ticks at 30 % of the slot | ✅ 5.16.0 | `drawOhlcBars`: `floor(barWidth · 0.3)`, TradingView's `optimalBarWidth` for bars | — **the frames are candles**; the rule is `OHLC_TICK_SHARE` |
| Price margins 10 % over, 8 % under (TradingView's Canvas defaults) | ✅ 5.16.0 | `ChartViewport.topMargin / bottomMargin`, linear and log (`TradingViewSettingsTest`, `PriceScaleTest`) | `docs/runs/RUN_TV/frames/tv-desktop-chart-en-dark.png` |
| News as the purple lightning on the time axis | ✅ 5.16.0 | `drawLightning`, `#9C27B0`, for `EventKind.NEWS` marks | — **the fixture feed has no news**; the glyph is `drawLightning` in `CoineProChart` |
| «»» scroll to the most recent bar | ✅ 5.16.0 | `ScrollToRealtimeButton`, shown only off the live edge | — **it appears only after a pan**, which a still frame of a fresh chart does not have |

## The desktop chrome

| Item | State | Evidence | Frame |
|---|---|---|---|
| Top toolbar in TradingView's order (symbol, compare, intervals, type, indicators, templates, alert, replay, undo/redo … layouts, settings, fullscreen, snapshot, trade) | ✅ 5.16.0 | `ChartDesktopToolbar`, on a window with the tool column (`TradingViewDesktopProofTest`) | `docs/runs/RUN_TV/frames/tv-desktop-chart-en-dark.png` |
| Bottom bar: 1D 5D 1M 3M 6M YTD 1Y 5Y All, clock with UTC offset, `%`, `log` | ✅ 5.16.0 | `ChartDesktopBottomBar` | `docs/runs/RUN_TV/frames/tv-desktop-chart-fa-light.png` |
| Chart settings dialog: Symbol, Status line, Scales, Appearance (TradingView's Canvas), Trading, Events | ✅ 5.16.0 | `ChartSettingsBody` + `ChartAppearance`, stored once for every chart (`ChartLayoutStore.appearance`) | `docs/runs/RUN_TV/frames/tv-settings-canvas-en-dark.png` |
| Status line switches (logo, OHLC, change, indicators) | ✅ 5.16.0 | `ChartDecoration.legendOhlc / legendChange / legendStudies`, `legendLogo` | `docs/runs/RUN_TV/frames/tv-settings-status-en-dark.png` |
| Symbol tab: bodies, wicks, colour on previous close, volume | ✅ 5.16.0 | `drawCandles(onPreviousClose, bodies, wicks)`, `drawOhlcBars(onPreviousClose)` | `docs/runs/RUN_TV/frames/tv-settings-symbol-en-dark.png` |
| Six indicator templates (Bill Williams' 3 Lines, Displaced EMA, MA Exp Ribbon, Oscillators, Swing Trading, Volume Based) | ✅ 5.16.0 | `BuiltInIndicatorTemplates`, EMA gained TradingView's Offset input (`TradingViewSettingsTest`) | `docs/runs/RUN_TV/frames/tv-desktop-template-en-dark.png` |
| Legend controls on hover | ✅ 5.16.0 | The plate's pointer Enter/Exit shows eye, gear and cross; a phone still taps «⋯» | — **hover is a mouse state**, not a still; `ChartLegendOverlay` `hover` |
| Ctrl / ⌘ held = momentary magnet | ✅ 5.16.0 | `chartShortcuts(onModifierHeld)` → `ChartController.holdMagnet` | — **a held key**, not a still frame |
| Browser tab title `XAUUSD 2,551.7 ▼ −1.74%` | ✅ 5.16.0 | `WindowTitle` (a no-op on the phone, `document.title` on the web), `tabTitle` (`DesktopChromeTest`) | — **a browser's tab strip is outside the page**; `DesktopChromeTest` pins the string |
| Up to 16 charts per layout on a desktop window | ✅ 5.16.0 | `DESKTOP_MAX_PANES = 16` past 1400 dp; presets 9, 12, 16 (`WindowClassTest`, `ChartLayoutPresetTest`) | — **the pane screen's frames are in the tablet set**; the new presets are rows in the same menu |

## Not copied, on purpose

| Item | State | Evidence | Frame |
|---|---|---|---|
| Seconds and tick intervals from the server | ✅ not a gap | Neither backend serves them; the seconds intervals this chart has are built from the live feed (`ChartInterval.Seconds`) | — **a data limit**, stated in the dissection's own §12 |
| TradingView's font stack | ✅ not a gap | IRANYekanX by the owner's standing rule; every size is matched in sp | — **the rule in CLAUDE.md** |
