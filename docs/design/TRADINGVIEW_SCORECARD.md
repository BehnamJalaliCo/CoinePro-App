# TradingView scorecard — phone, tablet and web, ahead and behind

Measured against the owner's «کالبدشکافی تریدینگ‌ویو» artifact (§04 chart, §05 alerts, §06
screeners, §07 social, §08 trading, §09 pixel specs, §12 scorecard), row for row, on the tree as of
5.16.1. Where the artifact gives a pixel value, `TRADINGVIEW_PARITY.md` holds the measurement; this
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
| Per-interval drawing visibility | Visibility tab | ✅ | ✅ | ✅ | 5.16.1, stored with the drawing |
| Drawing timeframe tag | an unmet feature request | ✅ | ✅ | ✅ | The interval a drawing was made on shows on it |
| Per-symbol memory | one layout per chart | ✅ | ✅ | ✅ | Interval and indicators remembered per symbol |
| Persian, RTL, Jalali calendar | none | ✅ | ✅ | ✅ | The product's default language |
| Confidence engine, repaint claim, Rasad, Arena, Duel | none | ✅ | ✅ | ✅ | Features TradingView has no equivalent of |
| NamaScript | Pine Script | ✅ | ✅ | ✅ | Pine-shaped, runs on the device, conformance suite of 100+ scripts |

## Where we are level (5.16.0 and 5.16.1)

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

## Where we are behind

| Area | TradingView | Us | What closing it takes |
|---|---|---|---|
| Server-side alerts | every condition runs on their servers | simple price alerts on the server; the rest are evaluated on the device, every 15 min in the background and **every minute while the app is open** (5.16.1) | An evaluator in the backend repository — not this app |
| Alert delivery by email, SMS, Telegram | yes | push, sound, webhook | Backend delivery channels; the Telegram bot already exists in CoineProFx |
| Drawing alerts on channels and rectangles | enter / exit the shape | treated as lines | `AlertTrigger.Drawing` shape-aware evaluation |
| Screeners | seven (stock, ETF, bond, crypto coin, CEX, DEX, forex) | one | Per-asset field sets |
| Screener CSV export | yes | no | `core:export` has a CSV writer; the screener has no action for it |
| News and ideas | filters by provider, community ideas and follows | economic calendar and headlines | A social layer is out of scope for a single-author product |
| Backtest | Strategy Tester, bar magnifier, slippage, commission | three strategies, commission | Slippage and a lower-interval magnifier in `core:backtest` |
| Trading from the chart | drag an order line to modify it; DOM trades | paper positions; DOM reads only | A broker bridge |
| Legend «⋯» menu | pin to scale, move to pane, visual order | hide, settings, remove | The three items as legend actions |
| Session volume profile as a chart type | yes | as an indicator | A chart type backed by `volumeprofile` |
| Tick and seconds data from the server | yes | seconds built from the live feed | Neither backend serves ticks |
| Currency and unit label on the price scale | yes | no | A scale label from the symbol's quote currency |
| Empty-value glyph on the web | `∅` | `…` | IRANYekanX has no `∅`, and the typeface rule stands |

## Not copied, on purpose

| Item | Why |
|---|---|
| Pulsing dot at the line's end | The motion policy: nothing moves that the reader did not move |
| Baseline measured from the middle of the visible range | The first close of the visible range is a level a reader can name |
| TradingView's font stack | One typeface, IRANYekanX |
