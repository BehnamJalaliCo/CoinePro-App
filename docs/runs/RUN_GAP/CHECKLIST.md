# RUN GAP — what the old Pro-Chart had and this app did not

The owner's ask (2026-09-24): read `BehnamJalaliCo/Pro-Chart` thoroughly and cover **every**
shortfall of the new Pro Chart against it. The audit read the old repository's terminal
(`frontend/prochart`, the `bazaarnama/` chart and its panels), its server (`src/api`, the alert
worker, the news and calendar feeds) and its docs, and set each feature against this app.

What is **not** a gap, and why, is at the end: the old repository also holds products this app is not
(the academy, the Instagram tool, the admin panels, the Telegram bot) and server features no screen
ever called.

States: ✅ done · ❌ not done yet · ⏳ owed to the server or the owner.

---

## The chart

| Item | State | Evidence | Frame |
|---|---|---|---|
| A back button on the web chart | ✅ 5.10.3 | `ChartBackMark`: the page draws a real arrow on a disc; the phone's character is unchanged | `docs/runs/RUN_WEB/frames/web-app-chart-fa-phone.png` |
| The 24 indicators the old terminal had: ALMA, MA Ribbon, GMMA, MA Cross, higher-timeframe EMA and RSI, anchored VWAP with bands, standard error bands, moving median, typical price, weighted close, Chandelier Exit, linear regression channel, STC, Elder Ray, Aroon oscillator, ADR, PMO, relative volatility index, Ulcer, PVI, NVI, volume oscillator, pivot highs/lows | ✅ 5.11.0 | `IndicatorsExtD.kt`, a port of `indicators.js` / `indicators_ext_b.js`. `IndicatorParityTest` holds 33 new series to the old JavaScript's own output at 1e-6 (`generate-indicator-parity.mjs`). 107 indicators, each with fa/en help (18 entries written for this app) | `docs/runs/RUN_GAP/frames/gap-fourth-pack-fa.png` (ALMA, Chandelier, STC, Elder Ray); the parity fixture is the numeric evidence |
| Columns and High-Low chart types | ✅ 5.11.0 | `ChartType.COLUMNS`, `ChartType.HIGH_LOW`, drawn by `drawColumns` / `drawHighLow`; 20 types | `app/src/test/goldens/chart-type-columns-fa-411.png`, `chart-type-high-low-fa-411.png` (and 840) |
| Session shading (Sydney, Tokyo, London, New York, London/NY overlap) | ✅ 5.12.0 | The «سشن‌های معاملاتی» study: `Sessions.sessionBands`, drawn behind the candles by `drawTimeBands`. Each city's daylight saving is written out and `SessionsTest` checks it against the JVM's time-zone database every hour for three years | `docs/runs/RUN_GAP/frames/gap-sessions-periods-fa.png` |
| Day / week / month separators | ✅ 5.12.0 | The «جداکننده‌ی دوره‌ها» study: a rule at each new day on intraday charts, month on the daily, year above (`Sessions.periodSeparators`) | `docs/runs/RUN_GAP/frames/gap-sessions-periods-fa.png` (the dashed verticals) |
| Previous day/week/month high, low, close; period opens | ✅ 5.12.0 | The «سقف، کف و بسته شدن دوره‌ی قبل» study: PH, PL, PC and this period's open, stepped at each boundary (`Sessions.previousPeriodLevels`, `SessionsTest`) | `docs/runs/RUN_GAP/frames/gap-sessions-periods-fa.png` |
| Seventeen time zones (this app offers four) | ✅ 5.12.0 | `CHART_ZONES` in `ChartScreen.kt`: the four, then the terminal's thirteen west to east, named in both languages | — **a chip row in a sheet**; the list is the source, and the web build draws any IANA zone through `Intl` |
| Three-month bars (MN3) | ✅ 5.12.0 | `Timeframe.MN3`, folded from daily bars on the calendar quarter (`bucketStart`, `CALENDAR_TIMEFRAMES`) | — **a picker entry**; `TimeframeTest` walks every entry |
| YTD range | ✅ 5.12.0 | `ChartRange.YTD`: four-hour bars in the first two months of the year, daily after, so it never draws fewer than the readable floor (`ChartRangeTest` walks all 366 days) | — **a pill in the range row**; the test is the evidence |
| Layouts 3 stacked, 4 across, 4 stacked | ✅ 5.12.0 | `ChartLayoutPreset.THREE_DOWN`, `FOUR_ACROSS`, `FOUR_DOWN` (`3v`, `4h`, `4v`, the terminal's ids) | — **a tablet layout menu**; `ChartLayoutPresetTest` holds the ids and grids |
| Harmonic pattern detection (Gartley, Bat, Butterfly, Crab) | ❌ | — | — **not built yet** |
| RSI divergence detection (regular and hidden) | ❌ | — | — **not built yet** |
| Price gap detection | ❌ | — | — **not built yet** |
| Technical rating (15 averages, 13 oscillators) | ❌ | — | — **not built yet** |
| Data window | ❌ | — | — **not built yet** |
| Keyboard shortcuts past the fifteen, and the «?» list | ❌ | — | — **not built yet** |
| Screenshot to clipboard and file | ❌ | — | — **not built yet** |
| Spread and ratio symbols (`EURUSD/GBPUSD`) | ❌ | — | — **not built yet** |
| Replay: ten bars at a time, back to the start, «replay from here» | ❌ | — | — **not built yet** |

## Not a gap

| Old feature | Why it is not owed |
|---|---|
| The academy (lessons, quizzes, mentor, bootcamps, certificates) | This app has its own academy; the old one is a separate product in `frontend/academy` |
| Instagram automation, SEO, blog | A separate product (`frontend/ig`, `src/instagram`) |
| Admin panels | Server operations, not the reader's app; this app's admin screen is out of the store build by design |
| Telegram bot | Not running in the old production either |
| Copy trading | Removed from this product by the owner's decision (`ConnectionsScreen.kt`) |
| Server-side ML signals | No inference module existed; the old server never served them |
