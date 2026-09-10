# NamaScript reference

Generated from `ScriptReference` and `ScriptReferenceEn` by `ReferenceDocsTest`; do not edit by hand. The language itself is described in `../SPEC.md`.

## Built-in series

| Call | What it gives | Returns |
| --- | --- | --- |
| `close` | Each bar's closing price. | number series |
| `open` | Each bar's opening price. | number series |
| `high` | Each bar's highest price. | number series |
| `low` | Each bar's lowest price. | number series |
| `volume` | Each bar's volume. Absent on symbols whose feed sends none. | number series |
| `hl2` | The bar's midpoint: (high + low) ÷ 2. | number series |
| `hlc3` | The typical price: (high + low + close) ÷ 3. | number series |
| `ohlc4` | The mean of the bar's four prices. | number series |
| `time` | The bar's time, in Unix seconds. | number series |
| `bar_index` | The bar's number, from zero. | number series |
| `n` | How many bars the chart holds. | number |
| `confirmed` | True on every closed bar and false on the last; end a signal with «and confirmed» so its mark never sits on the bar still forming. | condition series |
| `na` | Absent on every bar; nz(na, 0) fills it and no comparison with it is ever decided. | number series |

## Averages

| Call | What it gives | Returns |
| --- | --- | --- |
| `ta.sma(close, 20)` | Simple moving average over the length. | number series |
| `ta.ema(close, 20)` | Exponential moving average; weights recent bars more. | number series |
| `ta.wma(close, 20)` | Linearly weighted moving average. | number series |
| `ta.hma(close, 21)` | Hull moving average; less lag than EMA, longer warm-up. | number series |
| `ta.smma(close, 14)` | Smoothed moving average (RMA) — the one inside RSI and ATR. | number series |
| `ta.zlema(close, 20)` | Zero-lag exponential moving average. | number series |
| `ta.kama(close, 10, 2, 30)` | Kaufman's adaptive average: fast in a trend, slow in a range. | number series |
| `ta.mcginley(close, 14)` | McGinley Dynamic; an average that adjusts to the market's speed. | number series |
| `ta.linreg(close, 20)` | Linear regression over the window — the value at the line's end on each bar. | number series |

## Oscillators

| Call | What it gives | Returns |
| --- | --- | --- |
| `ta.rsi(close, 14)` | Relative strength index, 0 to 100. | number series |
| `ta.cci(20)` | Commodity channel index. Reads the chart's own high, low and close. | number series |
| `ta.atr(14)` | Average true range — the size of the moves, not their direction. | number series |
| `ta.macd(close, 12, 26, 9)` | The MACD line: the difference of two exponential averages. | number series |
| `ta.macd_signal(close, 12, 26, 9)` | The MACD signal line. | number series |
| `ta.macd_hist(close, 12, 26, 9)` | The MACD histogram: line minus signal. | number series |
| `ta.momentum(close, 10)` | Price minus the price a number of bars ago. | number series |
| `ta.williams_r(14)` | Williams %R, 0 to −100. Reads the chart's high, low and close. | number series |
| `ta.ultimate(7, 14, 28)` | Larry Williams' Ultimate Oscillator over three periods. | number series |
| `ta.trix(close, 18, 9)` | TRIX: the rate of change of a triple exponential average. | number series |
| `ta.trix_signal(close, 18, 9)` | The TRIX signal line. | number series |
| `ta.fisher(9)` | Fisher transform of the bar midpoint; sharpens turns. | number series |
| `ta.fisher_signal(9)` | The Fisher signal line (one bar behind). | number series |
| `ta.crsi(close, 3, 2, 100)` | Connors RSI: the mean of a short RSI, a streak RSI and a percent rank. | number series |
| `ta.smi(close, 20, 5, 5)` | SMI ergodic (TSI form), −100 to 100. | number series |
| `ta.smi_signal(close, 20, 5, 5)` | The SMI signal line. | number series |
| `ta.chop(14)` | Choppiness index; above 61 a range, below 38 a trend. | number series |
| `ta.bop(1)` | Balance of power: (close − open) ÷ (high − low), optionally smoothed. | number series |
| `ta.vortex_plus(14)` | The positive Vortex line. | number series |
| `ta.vortex_minus(14)` | The negative Vortex line. | number series |

## نوسان (تلاطم)

| Call | What it gives | Returns |
| --- | --- | --- |
| `ta.tr()` | Each bar's true range, unaveraged. | number series |
| `ta.hv(close, 10)` | Historical volatility: the standard deviation of log returns, annualised. | number series |
| `ta.chaikin_vol(10, 10)` | Chaikin volatility: the rate of change of the average bar range. | number series |
| `ta.bb_percent(close, 20, 2)` | %B: where price sits between the Bollinger bands, 0 to 1. | number series |
| `ta.bb_width(close, 20, 2)` | Bollinger band width relative to the basis. | number series |
| `ta.keltner_upper(20, 2)` | The upper Keltner channel (EMA ± multiplier × ATR). | number series |
| `ta.keltner_lower(20, 2)` | The lower Keltner channel. | number series |
| `ta.keltner_basis(20, 2)` | The Keltner channel's middle. | number series |
| `ta.env_upper(close, 20, 1)` | The upper envelope: the simple average plus a percentage. | number series |
| `ta.env_lower(close, 20, 1)` | The lower envelope. | number series |
| `ta.env_basis(close, 20, 1)` | The envelope's middle. | number series |

## باندها

| Call | What it gives | Returns |
| --- | --- | --- |
| `ta.bb_basis(close, 20, 2)` | The Bollinger band's middle. | number series |
| `ta.bb_upper(close, 20, 2)` | The upper Bollinger band. | number series |
| `ta.bb_lower(close, 20, 2)` | The lower Bollinger band. | number series |
| `ta.donchian_upper(20)` | The Donchian channel's top. For a breakout read it one bar back: ta.donchian_upper(20)[1]. | number series |
| `ta.donchian_lower(20)` | The Donchian channel's bottom. | number series |

## روند و جهت

| Call | What it gives | Returns |
| --- | --- | --- |
| `ta.supertrend(10, 3)` | The SuperTrend line: an ATR-sized band that changes side with price. | number series |
| `ta.supertrend_trend(10, 3)` | SuperTrend direction: 1 rising, −1 falling. A change of sign is the reversal. | number series |
| `ta.adx(14)` | Trend strength without direction. Below the threshold there is no trend. | number series |
| `ta.di_plus(14)` | The positive directional index. | number series |
| `ta.di_minus(14)` | The negative directional index. | number series |
| `ta.stoch_k(14, 3)` | The stochastic %K line. | number series |
| `ta.stoch_d(14, 3)` | The stochastic %D line; the average of %K. | number series |

## ایچیموکو

| Call | What it gives | Returns |
| --- | --- | --- |
| `ta.ichimoku_conversion(9, 26)` | Tenkan-sen. | number series |
| `ta.ichimoku_base(9, 26)` | Kijun-sen. | number series |
| `ta.ichimoku_span_a(9, 26)` | Senkou span A, unshifted. | number series |
| `ta.ichimoku_span_b(9, 26, 52)` | Senkou span B, unshifted. | number series |

## Volume

| Call | What it gives | Returns |
| --- | --- | --- |
| `ta.vwap()` | Volume-weighted average price from the first bar. | number series |
| `ta.obv()` | On-balance volume. Like vwap, absent without volume. | number series |
| `ta.ad()` | The accumulation/distribution line. | number series |
| `ta.pvt()` | Price-volume trend. | number series |
| `ta.force(13)` | Elder's force index, smoothed with an exponential average. | number series |
| `ta.chaikin_osc(3, 10)` | The Chaikin oscillator: the difference of two averages of the A/D line. | number series |
| `ta.eom(14)` | Ease of movement. | number series |
| `ta.klinger(34, 55, 13)` | The Klinger volume oscillator. | number series |
| `ta.klinger_signal(34, 55, 13)` | The Klinger signal line. | number series |

## آمار

| Call | What it gives | Returns |
| --- | --- | --- |
| `ta.highest(high, 20)` | The highest value in the window. | number series |
| `ta.lowest(low, 20)` | The lowest value in the window. | number series |
| `ta.sum(volume, 20)` | The window's sum. | number series |
| `ta.stdev(close, 20)` | The window's standard deviation. | number series |
| `ta.change(close, 1)` | The difference from a number of bars ago. | number series |
| `ta.roc(close, 12)` | Percent change from a number of bars ago. | number series |
| `ta.cum(volume)` | The cumulative sum from the first bar. | number series |

## منطق کندل‌ها

| Call | What it gives | Returns |
| --- | --- | --- |
| `ta.rising(close, 3)` | True when the value rose on each of the last n bars. | condition series |
| `ta.falling(close, 3)` | True when the value fell on each of the last n bars. | condition series |
| `ta.barssince(cond)` | Bars since the condition last held; absent before its first time. | number series |
| `ta.valuewhen(cond, close, 0)` | The series' value on the last bar the condition held; the third number says how many occurrences back. | number series |
| `ta.pivothigh(5, 5)` | A local top: higher than n bars before and n after. Sits on the bar that confirms it and never moves. | number series |
| `ta.pivotlow(5, 5)` | A local bottom, by the same rule. | number series |

## تقاطع‌ها

| Call | What it gives | Returns |
| --- | --- | --- |
| `ta.crossover(a, b)` | The bar on which a crossed above b. | condition series |
| `ta.crossunder(a, b)` | The bar on which a crossed below b. | condition series |

## Math

| Call | What it gives | Returns |
| --- | --- | --- |
| `math.abs(x)` | Absolute value. | number or series |
| `math.max(a, b)` | The larger of two values. | number or series |
| `math.min(a, b)` | The smaller of two values. | number or series |
| `math.round(x)` | Round to the nearest whole number. | number or series |
| `math.floor(x)` | Round down. | number or series |
| `math.ceil(x)` | Round up. | number or series |
| `math.sqrt(x)` | Square root. | number or series |
| `math.log(x)` | Natural logarithm. | number or series |
| `math.sign(x)` | The sign: −1, 0 or 1. | number or series |
| `math.pow(a, b)` | A power. | number or series |

## شرط و جای‌گزینی

| Call | What it gives | Returns |
| --- | --- | --- |
| `iff(condition, a, b)` | On each bar, a if the condition holds, otherwise b. | number series |
| `nz(series, 0)` | Puts the given number wherever a bar has no value. | number series |

## ورودی کاربر

| Call | What it gives | Returns |
| --- | --- | --- |
| `input(14, title = "طول", min = 2, max = 200)` | A number the reader can change from the panel under the chart; step sets the slider's step. | number |

## خروجی روی نمودار

| Call | What it gives | Returns |
| --- | --- | --- |
| `plot(series, title = "نام", color = color.gold, width = 1.4, dashed = false, pane = "auto")` | Draws a number series as a line. | همان سری |
| `hline(70, title = "اشباع خرید", color = color.grey)` | A horizontal level. | همان عدد |
| `marker(condition, title = "ورود", style = "up", color = color.green)` | A mark on every bar the condition holds. | همان شرط |
| `signal(condition, entry, stop, target = target, buy = true)` | The trade idea from the last bar the condition held, with its risk-to-reward. | true/false |
| `log("متن")` | A line in the studio's log. | درست |

## Averages and trend (4.50)

| Call | What it gives | Returns |
| --- | --- | --- |
| `ta.dema(close, 20)` | Double exponential moving average; less lag than EMA. | number series |
| `ta.tema(close, 20)` | Triple exponential moving average. | number series |
| `ta.t3(close, 10, 0.7)` | Tillson's T3 average with a volume factor. | number series |
| `ta.vwma(20)` | Volume-weighted moving average of the chart's close. | number series |
| `ta.alligator_jaw()` | Williams' Alligator jaw (SMMA 13, shifted 8). | number series |
| `ta.alligator_teeth()` | Alligator teeth (SMMA 8, shifted 5). | number series |
| `ta.alligator_lips()` | Alligator lips (SMMA 5, shifted 3). | number series |
| `ta.psar(0.02, 0.2)` | Wilder's parabolic SAR; the step and the maximum acceleration. | number series |
| `ta.vstop(20, 2)` | Volatility stop: ATR × multiplier from the trend's extreme. | number series |
| `ta.aroon_up(14)` | Aroon up: what share of the period since the last high. | number series |
| `ta.aroon_down(14)` | Aroon down. | number series |
| `ta.mass(25, 9)` | Dorsey's mass index; a bulge above 27 warns of a reversal. | number series |
| `ta.correlation(close, open, 20)` | Pearson correlation of two series over the window. | number series |

## Oscillators (4.50)

| Call | What it gives | Returns |
| --- | --- | --- |
| `ta.ppo(close, 12, 26, 9)` | Percentage price oscillator: MACD divided by the slow average, in percent. | number series |
| `ta.ppo_signal(close, 12, 26, 9)` | The PPO signal line. | number series |
| `ta.pvo(12, 26, 9)` | Percentage volume oscillator. | number series |
| `ta.pvo_signal(12, 26, 9)` | The PVO signal line. | number series |
| `ta.tsi(close, 25, 13, 13)` | True strength index; momentum smoothed twice. | number series |
| `ta.tsi_signal(close, 25, 13, 13)` | The TSI signal line. | number series |
| `ta.stochrsi_k(close, 14, 14, 3, 3)` | Stochastic of RSI, the K line. | number series |
| `ta.stochrsi_d(close, 14, 14, 3, 3)` | Stochastic of RSI, the D line. | number series |
| `ta.ao()` | Awesome Oscillator: SMA 5 minus SMA 34 of the bar midpoint. | number series |
| `ta.ac()` | Accelerator: AO minus its own SMA 5. | number series |
| `ta.cmo(close, 9)` | Chande momentum oscillator. | number series |
| `ta.coppock(close, 14, 11, 10)` | Coppock curve: a WMA of the sum of two ROCs. | number series |
| `ta.rvi(10)` | Relative vigor index over the chart's open, high, low and close. | number series |
| `ta.rvi_signal(10)` | The RVI signal line. | number series |
| `ta.kst(close)` | Know Sure Thing: four smoothed ROCs weighted 1 to 4. | number series |
| `ta.kst_signal(close)` | The KST signal line (SMA 9). | number series |
| `ta.dpo(close, 20)` | Detrended price oscillator: price minus a displaced average. | number series |
| `ta.variance(close, 20)` | The window's variance (the standard deviation squared). | number series |
| `ta.avg(close, 20)` | The window's simple mean; ta.sma by a shorter name. | number series |

## Volume (4.50)

| Call | What it gives | Returns |
| --- | --- | --- |
| `ta.mfi(14)` | Money flow index: a volume-weighted RSI. | number series |
| `ta.cmf(20)` | Chaikin money flow. | number series |
| `ta.netvolume()` | Net volume: volume signed by the bar's direction. | number series |

## Math (4.50)

| Call | What it gives | Returns |
| --- | --- | --- |
| `math.exp(x)` | e to the power x. | number or series |
| `math.log10(x)` | Base-ten logarithm. | number or series |
| `math.sin(x)` | Sine (radians). | number or series |
| `math.cos(x)` | Cosine. | number or series |
| `math.tan(x)` | Tangent. | number or series |
| `math.avg(a, b)` | The mean of two values. | number or series |
| `math.clamp(x, lo, hi)` | Keeps x between two constant bounds. | number or series |

## Input, output, alerts (4.50)

| Call | What it gives | Returns |
| --- | --- | --- |
| `input.int(14, title = "طول", min = 1, max = 100)` | A number input rounded to a whole number. | number |
| `input.float(2.0, title = "ضریب")` | A decimal input; the same as input. | number |
| `input.bool(true, title = "نمایش")` | An on/off input. | true/false |
| `color.new(color.gold, 50)` | The same colour with a transparency percentage (0 to 100). | colour |
| `plotshape(cond, title = "…", style = "triangleup")` | The same as marker, with Pine's shape names: triangleup, triangledown, arrowup, arrowdown. | number |
| `plotchar(cond, title = "…")` | The same as marker. | number |
| `bgcolor(cond, color.new(color.gold, 80))` | A background colour on every bar the condition holds. | number |
| `alertcondition(cond, "نام")` | A named condition the alert centre can follow; true on the last bar if the condition holds there. | true/false |

## Inputs and timeframes (4.56)

| Call | What it gives | Returns |
| --- | --- | --- |
| `input(14, title = "طول", min = 1, max = 100, step = 1)` | A number the reader can change from the panel under the chart; step sets the slider's step. | number |
| `input.string("ema", title = "نوع", options = "ema,sma,wma")` | A choice among the options, shown as chips in the panel. | رشته |
| `input.source("close", title = "منبع")` | A choice of price series: close, open, high, low, hl2, hlc3, ohlc4 or volume. | number series |
| `input.color(color.gold, title = "رنگ خط")` | A colour input, from the named colours. | colour |
| `input.timeframe("240", title = "تایم‌فریم")` | A timeframe choice, for request.security. | رشته |
| `request.security("240", close)` | The expression computed on a coarser timeframe (a multiple of the chart's), each chart bar taking the last completed higher bar's value — no repainting. | number or series |

## Text and absence (4.61)

| Call | What it gives | Returns |
| --- | --- | --- |
| `na(x)` | Absent on every bar; nz(na, 0) fills it and no comparison with it is ever decided. | condition series |
| `str.tostring(x)` | The number as price-style text; a series by its last bar. | رشته |
| `str.length("abc")` | How many characters the text has. | number |
| `str.upper("abc")` | The text in upper case. | رشته |
| `str.lower("ABC")` | The text in lower case. | رشته |
| `str.contains("abc", "b")` | True when the second text occurs inside the first. | true/false |
| `str.startswith("abc", "a")` | True when the text starts with the part given. | true/false |
| `str.endswith("abc", "c")` | True when the text ends with the part given. | true/false |
| `str.replace_all("a-b", "-", "+")` | Every occurrence of the second text replaced by the third. | رشته |
| `str.format("{0} / {1}", close, open)` | Fills {0}, {1}, … with the arguments that follow the pattern. | رشته |

## Drawing on the chart (4.61)

| Call | What it gives | Returns |
| --- | --- | --- |
| `label.new(bar_index, high, "متن", color = color.gold)` | A label at the bar and price given; a series is read at its last bar. | number |
| `line.new(bar_index - 20, low, bar_index, high, color = color.blue, width = 2)` | A line from (x1, y1) to (x2, y2); x is a bar number. | number |
| `box.new(bar_index - 10, high, bar_index, low, color = color.grey)` | A box from the left bar to the right one, between two prices. | number |

## Strategy (4.61)

| Call | What it gives | Returns |
| --- | --- | --- |
| `strategy.long` | The long direction for strategy.entry. | number |
| `strategy.short` | The short direction for strategy.entry. | number |
| `strategy.entry("L", strategy.long, when = cond)` | An entry order on every bar the condition holds, filled at the next bar's open; an entry the other way closes the open trade. | number |
| `strategy.close("L", when = cond)` | Closes the open trade with this id at the next bar's open. | number |
| `strategy.close_all(cond)` | Closes whatever trade is open. | number |
| `strategy.entry("long", cond)` | An entry order on every bar the condition holds, filled at the next bar's open; an entry the other way closes the open trade. | number |

## Colours

`color.blue`, `color.buy`, `color.gold`, `color.green`, `color.grey`, `color.orange`, `color.purple`, `color.red`, `color.sell`, `color.silver`, `color.teal`, `color.white`
