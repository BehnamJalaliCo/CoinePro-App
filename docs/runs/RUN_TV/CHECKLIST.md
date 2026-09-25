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
| TradingView's font stack | ✅ not a gap | IRANYekanX by the owner's standing rule; every size is matched in sp | — **the rule in CLAUDE.md** |

## 5.16.1 — the scorecard's behind rows (`docs/design/TRADINGVIEW_SCORECARD.md`)

| Item | State | Evidence | Frame |
|---|---|---|---|
| Baseline fill strongest at the base line on both sides, as TradingView draws it | ✅ 5.16.1 | `fillHalf` reverses the lower half's alphas (`ChartTypeGoldenTest`, re-recorded) | `app/src/test/goldens/chart-type-baseline-fa-411.png` |
| Crosshair magnet mode: the horizontal hair sits on the close | ✅ 5.16.1 | `ChartAppearance.crosshairMagnet`, key `crossmagnet` (`TradingViewSettingsTest`) | — **a crosshair exists only under a finger or pointer**, not in a still frame; the switch is on the Appearance tab |
| Time-axis context menu (zone, latest bar, go to date, sessions) | ✅ 5.16.1 | `CoineProChart(onTimeAxisMenu)` on a secondary press in the bottom 28 dp; `ChartScreen.timeAxisMenu` | — **a right click is a mouse event**, and the menu closes before a frame is taken |
| Typed intervals in minutes and hours, Persian digits and words accepted | ✅ 5.16.1 | `customTypedOf` (`TradingViewIntervalsTest`) | — **typed text resolves to an interval**; the test pins the parse |
| 6-month and 12-month intervals | ✅ 5.16.1 | `Timeframe.MN6 / MN12`, folded from D1 with January/July and January buckets (`TradingViewIntervalsTest`) | — **two more chips in the interval sheet**; the bucket arithmetic is what can be wrong and is tested |
| Drawing visibility per interval family (seconds … months) | ✅ 5.16.1 | `Drawing.hiddenOn`, `IntervalFamily`, stored as field 16 (`IntervalFamilyTest`, `ChartDrawingHiddenOnTest`) | — **a sheet tab**; sheets are outside the decor view the proof rig captures |
| Alert «moves N % within K bars» | ✅ 5.16.1 | `AlertConditionDraft.moveBars`, capped at 500 (`AlertDraftTest`) | — **a chip row in the alert sheet**, outside the captured decor view |
| Alert expiry: never, 1, 7, 30, 60 days | ✅ 5.16.1 | `AlertDraft.expiresAt` written and read back (`AlertDraftTest`) | — **a chip row in the alert sheet**, outside the captured decor view |
| Alerts checked every minute while the app is open | ✅ 5.16.1 | `LocalAlertScheduler.checkNow` on a lifecycle loop, one pass at a time behind `PASS_LOCK` | — **a timer**, not a picture; the fifteen-minute background period is unchanged |
| Screener: add every result to the watchlist | ✅ 5.16.1 | `ScreenerScreen(onAddToWatchlist)` → `watchlistStore.add` | — **one button under the result count**; the watchlist is where its effect shows |

## 5.17.0 — the growth scan, and the rest of the behind rows

| Item | State | Evidence | Frame |
|---|---|---|---|
| Growth scan on the phone: eleven setups, bars since, growth score, ranked | ✅ 5.17.0 | `GrowthScan` (`GrowthScanTest`), `ScreenerMode.SIGNALS` (`ScreenerControllerTest`, `ScreenerGrowthProofTest`) | `docs/runs/RUN_TV/frames/tv-screener-growth-fa-dark.png` |
| Growth scan on the web, narrowed to two setups, headings in English | ✅ 5.17.0 | `ScreenerGrowthProofTest.theGrowthScanOnTheWeb`, `ScreenerField.labelIn` | `docs/runs/RUN_TV/frames/tv-screener-growth-en-desktop.png` |
| Screener on the web's rail, after the chart, and at `/terminal/screener` | ✅ 5.17.0 | `coineProRailItems` (`NavigationParityTest`), `WebApp.fromAddress` | — **the rail's order is what can be wrong**, and `NavigationParityTest` pins it |
| Scan watch: a notification when a new market enters a saved scan | ✅ 5.17.0 | `ScanWatch.entrants` (`ScanWatchTest`), `ScanWatchWorker` | — **a notification is outside the app's window** |
| Screener CSV export | ✅ 5.17.0 | `ScreenerController.csv` (`ScreenerControllerTest`) | `docs/runs/RUN_TV/frames/tv-screener-growth-en-desktop.png` (the CSV button) |
| Tick bars (1, 10, 100, 1000) and seconds with server history | ✅ 5.17.0 | `ChartInterval.Ticks`, `TickBars.fold` (`TickIntervalTest`, `TickChartTest`); CoinePro-FX `market_ticks.py` (`test_market_ticks.py`) | — **a live feed**, not a fixture; the fold is tested |
| Session volume profile chart type | ✅ 5.17.0 | `drawSessionVolumeProfiles` (`ChartTypeGoldenTest`) | `app/src/test/goldens/chart-type-svp-fa-411.png` |
| Legend «⋯» menu: own scale, front, back | ✅ 5.17.0 | `ChartController.setOwnScale / bringToFront / sendToBack` | — **a menu closes before a frame is taken** |
| Currency label on the price scale | ✅ 5.17.0 | `ChartAppearance.scaleUnit`, key `unit` | — **drawn from the quote currency on a live chart**; the goldens render without a symbol |
| Drawing alerts on rectangles and channels | ✅ 5.17.0 | `AlertDrawingLevel.boundsAt` (`AlertShapeBoundsTest`) | — **an alert fires in the background**, not on screen |
| Server alerts: channel, move, RSI on CoinePro-FX; Telegram and email | ✅ 5.17.0 | `ServerAlertSpec` (`ServerAlertRowsTest`); CoinePro-FX `test_alert_specs.py`; TradeYar `test_mobile_alert_eval.py` | — **a server-side evaluation**; the three test suites pin it |
| Backtest slippage, stop / target, bar magnifier | ✅ 5.17.0 | `Backtest.run`, `firstTouch` (`BacktestPropertiesTest`) | — **a sheet**, outside the captured decor view |
| Drag an order line; trade from the DOM | ✅ 5.17.0 | `ChartTradeLines` (`ChartTradeLinesTest`), `DomTradeControls` | — **a drag and a tap**, not a still |
| News filters | ✅ 5.17.0 | `NewsFilter` (`NewsFilterTest`) | — **the fixture feed is empty in the proof rig**; the filter is tested |
| Community «Following» | ✅ 5.17.0 | `CommunityController.toggleFollow / visiblePosts` (`CommunityControllerTest`) | — **the board is served live**; the narrowing is tested |

## 5.18.0 — real crypto orders on LBank

| Item | State | Evidence | Frame |
|---|---|---|---|
| Live ticket: market or limit, stop, target, reduce-only, reviewed as a sentence before anything is sent | ✅ 5.18.0 | `LiveTradeSheetBody`, `LiveOrderCheck` (`LiveTradeTest`); `LiveTradeProofTest` asserts the review sends nothing and the confirm sends one | `docs/runs/RUN_TV/frames/live-ticket-review-fa-dark.png` |
| The order on the book after confirm; the position with SL/TP, close half and close; cancel on a resting order | ✅ 5.18.0 | `LiveTradeController`, TradeYar `trade.py` (`test_positions_close_and_protection`, `test_a_resting_limit_can_be_moved_and_cancelled`) | `docs/runs/RUN_TV/frames/live-ticket-sent-fa-dark.png` |
| Live orders and the entry drawn on the chart; a drag asks, then cancels and re-places at the new price | ✅ 5.18.0 | `LiveTradeLines` (`LiveTradeLinesTest`), the amend route's cancel-then-place order (`test_a_resting_limit_can_be_moved_and_cancelled`) | — **a drag and a dialog**, not a still; the tests pin what the drag names and what is sent |
| «Order here» on a crypto chart opens the live ticket at that price | ✅ 5.18.0 | `ChartScreen(onRequestOrderAt)` → `LiveTicketSeed` | — **a long-press gesture**; the seeded ticket is the frame above |
| Depth ladder: an armed tap on a crypto ladder asks, then sends a real limit | ✅ 5.18.0 | `DepthOfMarketBody(liveVenue)`, `placeLadderOrder` | — **a tap and a dialog**, not a still |
| Idempotent, never optimistic, the exchange's own words, a kill switch | ✅ 5.18.0 | `mobile_orders` migration 083, `test_the_same_request_twice_places_one_order`, `test_an_unconfirmed_submit_is_unknown_never_filled`, `test_the_exchange_s_refusal_is_recorded_in_its_own_words`, `test_a_spot_key_and_the_kill_switch_each_stop_a_write` | — **server behaviour**, pinned by the TradeYar suite |
| Proven against LBank itself | ✅ 5.18.0 | TradeYar `scripts/lbank_live_trade_smoke.py`: read-only checks, and `--round-trip` places one limit 20 % under the market and cancels it | — **LBank answers only the server's whitelisted IP**; the script is run there |

