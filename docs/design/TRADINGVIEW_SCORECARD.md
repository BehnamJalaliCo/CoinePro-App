# TradingView scorecard — phone, tablet and web, ahead and behind

Measured against the owner's «کالبدشکافی تریدینگ‌ویو» artifact (§04 chart, §05 alerts, §06
screeners, §07 social, §08 trading, §09 pixel specs, §12 scorecard), row for row, on the tree as of
5.17.0 (first written at 5.16.1). Where the artifact gives a pixel value, `TRADINGVIEW_PARITY.md` holds the measurement; this
file is the verdict.

Columns: **Phone** is Android portrait, **Tablet** is Android at ≥600 dp, **Web** is the browser
build (`:web`, the same Kotlin sources). ✅ at or past TradingView · ◐ partly · ✗ missing.

## Where we are ahead

| Area | TradingView | Phone | Tablet | Web | Why it is ahead |
|---|---|---|---|---|---|
| Drawing tools | 110+, every one free | ✅ | ✅ | ✅ | Same set, no plan gate; drawings keep their OHLC channel (StickedPoint) |
| Indicators per chart | 2 / 5 / 10 / 25 by plan | ✅ | ✅ | ✅ | No cap |
| Charts per layout | 1 / 2 / 4 / 8 / 16 by plan | ✅ 1 | ✅ 8 | ✅ 16 | Every preset free |
| Alerts per device | 5 / 20 / 100 / 400 by plan | ✅ 200 | ✅ 200 | ✅ 200 | Free tier is forty times TradingView's |
| Alert audit trail | log of fired alerts only | ✅ | ✅ | ✅ | created · fired · delivered · snoozed · expired, with price and interval |
| Webhook | plain POST, paid plans | ✅ | ✅ | ✅ | Signed (HMAC), free |
| Replay | not on every chart type, paid for intraday | ✅ | ✅ | ✅ | Every chart type, every interval |
| Mobile screener | none on the phone | ✅ | ✅ | ✅ | Indicator filters on the phone, and «add all to the watchlist» (5.16.1) |
| Growth scan: markets about to trend | none — filters show what *is*, not what is starting | ✅ | ✅ | ✅ | Ten bullish setups (trend start, breakout on volume, momentum turn, golden cross, squeeze release, volume surge, bullish divergence, pullback end, structure break, bullish harmonic) and a trend-end warning, with how many bars ago each fired, a 0–100 growth score, the setup's own hit rate on that market, and a background watch that notifies on new entrants (5.17.0) |
| Screener to chart | opens the symbol | ✅ | ✅ | ✅ | Opens the symbol with the studies that draw the setup, on the interval it was scanned on (5.17.0) |
| Screener on the web | a page of its own | ✅ | ✅ | ✅ | A rail item beside the chart and `/terminal/screener` (5.17.0) |
| Per-interval drawing visibility | Visibility tab | ✅ | ✅ | ✅ | 5.16.1, stored with the drawing |
| Drawing timeframe tag | an unmet feature request | ✅ | ✅ | ✅ | The interval a drawing was made on shows on it |
| Per-symbol memory | one layout per chart | ✅ | ✅ | ✅ | Interval and indicators remembered per symbol |
| Persian, RTL, Jalali calendar | none | ✅ | ✅ | ✅ | The product's default language |
| Confidence engine, repaint claim, Rasad, Arena, Duel | none | ✅ | ✅ | ✅ | Features TradingView has no equivalent of |
| NamaScript | Pine Script | ✅ | ✅ | ✅ | Pine-shaped, runs on the device, conformance suite of 100+ scripts |

## Where we are level (5.16.0 – 5.17.0)

| Area | Phone | Tablet | Web | Evidence |
|---|---|---|---|---|
| §09 canvas specs: crosshair `[6,6]`, line 2 px, OHLC tick 30 %, margins 10 % / 8 % | ✅ | ✅ | ✅ | `TRADINGVIEW_PARITY.md`, third round |
| Baseline fill: strongest at the base line, fading away from it on both sides | ✅ | ✅ | ✅ | `drawBaseline`, `chart-type-baseline-*` goldens (5.16.1) |
| Chart settings: six tabs, reset | ✅ | ✅ | ✅ | `ChartSettingsBody` |
| Crosshair «magnet» mode (snaps to the close) | ✅ | ✅ | ✅ | `ChartAppearance.crosshairMagnet` (5.16.1) |
| Time-axis menu (zone, latest bar, go to date, sessions) | — | ✅ | ✅ | Right click on the time axis (5.16.1); a phone has no secondary button |
| Typed intervals `90m`, `2.5h`, `۵ ساعت` | ✅ | ✅ | ✅ | `customTypedOf` (5.16.1) |
| 6-month and 12-month intervals | ✅ | ✅ | ✅ | `Timeframe.MN6 / MN12`, folded from D1 (5.16.1) |
| Alert «moving N % within K bars» | ✅ | ✅ | ✅ | `AlertConditionDraft.moveBars` (5.16.1) |
| Alert expiry | ✅ | ✅ | ✅ | `AlertDraft.expiresAt`, never / 1 / 7 / 30 / 60 days (5.16.1) |
| Desktop toolbar, bottom bar, tab title, legend hover | — | ✅ sideways | ✅ | `ChartDesktopToolbar`, gated at ≥600 dp tall |
| Indicator templates (six built-ins) | ✅ | ✅ | ✅ | `BuiltInIndicatorTemplates` |
| Server-side alerts for channel, move % in N bars and RSI, besides the five price conditions | ✅ | ✅ | ✅ | CoinePro-FX `alert_specs.py` + `PriceAlertCondition.SPEC`; TradeYar now evaluates its price alerts every minute (`mobile_alert_tasks.py`) (5.17.0) |
| Alert delivery by Telegram and email | ✅ | ✅ | ✅ | `AlertDraft.serverChannels` → `channels` on CoinePro-FX's route (5.17.0) |
| Drawing alerts that enter / exit rectangles and channels | ✅ | ✅ | ✅ | `AlertDrawingLevel.boundsAt` (`AlertShapeBoundsTest`) (5.17.0) |
| Screener CSV export | ✅ | ✅ | ✅ | `ScreenerController.csv` through `core:export` (5.17.0) |
| News filters: impact, sentiment, publisher, symbol or word | ✅ | ✅ | ✅ | `NewsFilter` (`NewsFilterTest`) (5.17.0) |
| Community «Following» | ✅ | ✅ | ✅ | Follow an author from a post's «⋯»; the list is kept on the device (`CommunityControllerTest`) (5.17.0) |
| Backtest slippage, stop / target and bar magnifier; seven strategies | ✅ | ✅ | ✅ | `Backtest.run(slippagePercent, stopPercent, targetPercent, magnifier)`, `firstTouch` (`BacktestPropertiesTest`) (5.17.0) |
| Drag an order line to modify it | ✅ | ✅ | ✅ | `ChartOrderLine` on the price axis → `setProtection` / `amend` on paper (`ChartTradeLinesTest`) (5.17.0) |
| Trading from the DOM | ✅ | ✅ | ✅ | Armed ladder places a paper limit at the tapped price (5.17.0) |
| Legend «⋯»: own scale, bring to front, send to back | ✅ | ✅ | ✅ | `ChartController.setOwnScale / bringToFront / sendToBack` (5.17.0) |
| Session volume profile as a chart type | ✅ | ✅ | ✅ | `ChartType.SESSION_VOLUME_PROFILE`, `chart-type-svp-*` goldens (5.17.0) |
| Tick and seconds bars with history | ✅ | ✅ | ✅ | `ChartInterval.Ticks(1/10/100/1000)`, seconds 1–50 from CoinePro-FX's `public/market/ticks` and `seconds` (`TickChartTest`) (5.17.0) |
| Currency label on the price scale | ✅ | ✅ | ✅ | `ChartAppearance.scaleUnit` (5.17.0) |

## Where we are still behind

| Area | TradingView | Us | What closing it takes |
|---|---|---|---|
| Server evaluation of moving-average, volume and drawing alerts | on their servers | on the device, every minute while open and every 15 min in the background | Those conditions in `alert_specs.py`; a drawing alert needs the drawing on the server |
| Alert delivery by SMS | yes | push, Telegram, email, webhook | An SMS provider on the backend |
| Advanced server alerts on crypto | yes | the five price conditions on TradeYar; channel, move and RSI on CoinePro-FX only | The spec evaluator ported to TradeYar |
| Screeners per asset (stock, ETF, bond, DEX …) | seven | one, split by platform and asset class | Per-asset field sets; the markets this product carries are crypto, forex, metals, indices and energy |
| Tick history on crypto | yes | seconds and ticks built from the live feed on TradeYar | A tick store in TradeYar like CoinePro-FX's `tick_store.py` |
| Trading on a live broker from the chart | yes | paper only | A broker bridge |
| Published ideas with a chart | yes | text and picture posts | A chart snapshot attached to a post |

## Not copied, on purpose

| Item | Why |
|---|---|
| Pulsing dot at the line's end | The motion policy: nothing moves that the reader did not move |
| Baseline measured from the middle of the visible range | The first close of the visible range is a level a reader can name |
| TradingView's font stack | One typeface, IRANYekanX |
| `∅` for an empty value on the web | IRANYekanX has no `∅` and the one-typeface rule stands; the web prints `…` |
