package com.coinepro.core.script

/**
 * A ready-made study that marks entries on the candles.
 *
 * These are ports of the script library the previous Pro-Chart shipped — the ones readers actually
 * put on a chart to get buy and sell marks. They are not preserved verbatim. Every one of them had
 * at least one of the faults this file's [ScriptStrategies] doc lists, and a fault in a study that
 * paints arrows is not cosmetic: a reader scrolls back, sees arrows on the good turns, and takes
 * that as evidence. So each is ported with the fault repaired and the repair named in the script's
 * own comments, where the reader who opens it will read them.
 *
 * [id] is repository-owned identity and must stay stable: a reader who opened a strategy and edited
 * it has a saved copy that remembers where it came from, and renaming the id orphans it.
 *
 * There is deliberately no title or description here. Both are shown to a reader, so both live in
 * `feature:script`'s twinned `strings.xml` pair and are looked up by [id] — a Persian literal in
 * this module could never have an English twin, and a study named only in Persian is a study half
 * the audience cannot choose between.
 *
 * [warmUpBars] is the number of leading bars the script itself refuses to signal on, stated here so
 * a test can check the claim rather than trusting the comment. It is a *declared* floor, not a
 * measured one: the script may well stay quiet for longer.
 */
data class ScriptStrategy(
    val id: String,
    val warmUpBars: Int,
    val source: String,
)

/**
 * The ported strategy library.
 *
 * ## What was wrong with the originals
 *
 * The old library was written against an engine that evaluated every series over the whole loaded
 * array, last bar included, and nothing in any of the twenty-odd shipped scripts distinguished a
 * closed bar from the one still forming. The engine even defined `barstate.isconfirmed`; not one
 * script used it. So every arrow those scripts drew on the right-hand edge was provisional, and a
 * reader watching a chart saw marks appear and vanish. That is the fault repeated in every port,
 * and every port here ends its entry condition with `and confirmed`.
 *
 * The rest were specific, and each is named in the strategy that carried it:
 *
 * * a Donchian breakout tested against a channel that **included the breaking bar**, which is a
 *   condition that can never be true — `close > highest(high, 20)` where the window ends at this
 *   bar asks whether the close exceeds a maximum it is part of;
 * * a confluence score plotted against a line at 3 whose arrow used `crossover(score, 3)`, so the
 *   arrow only ever appeared at 4;
 * * a VWAP term that is silently false on every bar of any feed that reports no volume, quietly
 *   capping that same score at 3 and making its own threshold unreachable;
 * * an Ichimoku study that compared the close against an **undisplaced** senkou B, so it was not
 *   reading the cloud a chart draws at all;
 * * setups whose entry condition was a *state* rather than an event, marking every bar of a trend
 *   instead of the bar it began;
 * * a risk box anchored to the live close on every bar rather than to the bar the setup fired on;
 * * indicators read inside their own warm-up, where a zero-filled inner window still bends them;
 * * fixed thresholds and fixed stop distances that mean one thing on gold and another on Bitcoin.
 *
 * ## What none of them can see
 *
 * Every one of these reads one symbol on one timeframe and nothing else. No order book, no funding,
 * no news, no higher timeframe, no session. A mark is a statement that a pattern in past prices
 * completed on a bar that has closed. It is not advice, and nothing here knows whether the trade
 * is a good idea.
 */
object ScriptStrategies {

    /**
     * Two Hull moving averages crossing.
     *
     * **Signals** a buy on the bar the fast Hull average closed above the slow one, and a sell on
     * the bar it closed below. **Evidence** is nothing but those two averages of the close.
     *
     * **What it cannot see**: whether the cross is happening inside a range, where two averages
     * cross back and forth for no reason. It has no trend filter at all — that is what
     * [TREND_PULLBACK] and [TRIPLE_CONFLUENCE] are for.
     *
     * Ported from «کراسِ HMA (خرید/فروش)». Two faults repaired. The original marked the forming
     * bar. And the Hull average is a weighted average of a difference that has its own warm-up,
     * which the library passes through as zero before masking the result back out — the mask hides
     * the undefined head but not the √period bars after it, which were smoothed against those
     * zeros and are pulled towards them. The original signalled inside that region; this waits it
     * out.
     *
     * A mark is not advice.
     */
    val HULL_CROSS = ScriptStrategy(
        id = "hull-cross",
        warmUpBars = 26,
        source = """
            // دو میانگین هال؛ تقاطعشان را نشانه می‌گذارد.
            fastLength = input(9, title = "دوره‌ی تند", min = 4, max = 120)
            slowLength = input(21, title = "دوره‌ی کند", min = 6, max = 300)

            fast = ta.hma(close, fastLength)
            slow = ta.hma(close, slowLength)

            // میانگین هال از یک میانگین وزنیِ تفاضل ساخته می‌شود و آن تفاضل، خودش دوره‌ی گرم شدن
            // دارد. کتابخانه آن دوره را با صفر پر می‌کند و بعد ماسک می‌زند؛ ماسک سرِ ناتعریف را
            // پنهان می‌کند اما ریشه‌ی دوره کندل بعد از آن هنوز به سمت صفر خم شده‌اند. نسخه‌ی قدیمی
            // همان‌جا سیگنال می‌داد.
            ready = bar_index >= slowLength + math.ceil(math.sqrt(slowLength))

            // «confirmed» یعنی این کندل بسته شده. بدون آن، نشانه روی کندلِ در حال شکل‌گیری
            // می‌نشیند و با هر تیک جابه‌جا می‌شود یا محو می‌شود.
            buy = ta.crossover(fast, slow) and ready and confirmed
            sell = ta.crossunder(fast, slow) and ready and confirmed

            plot(fast, title = "هال تند", color = color.gold)
            plot(slow, title = "هال کند", color = color.blue)

            marker(buy, title = "خرید", style = "up")
            marker(sell, title = "فروش", style = "down")
        """.trimIndent(),
    )

    /**
     * A SuperTrend flip, with the stop the indicator itself defines.
     *
     * **Signals** the bar on which SuperTrend changed side. **Evidence** is the ATR-scaled band
     * around the bar mid: the flip is price closing through the band that had been holding.
     *
     * **What it cannot see**: whether the flip is the start of a trend or the middle of a range.
     * SuperTrend flips on every swing, and in a sideways market it flips constantly.
     *
     * Ported from «استراتژیِ سوپرترند». Three faults repaired. The original marked the forming
     * bar. It read the flip as `crossover(close, supertrend)` — which happens to coincide with the
     * flip only because the line jumps across the price at that moment, so it was right by accident
     * and would stop being right the day the band rule changed; this reads the direction series,
     * which is what a flip actually is. And SuperTrend's direction is *seeded* to "up" at the first
     * bar its ATR exists, so a series that opens in a downtrend flips on its first valid bar and
     * the original drew a sell there that means nothing; this waits for the seed to be washed out.
     *
     * A mark is not advice.
     */
    val SUPERTREND_FLIP = ScriptStrategy(
        id = "supertrend-flip",
        warmUpBars = 30,
        source = """
            // سوپرترند: باندی به اندازه‌ی ATR دور میانه‌ی کندل، که سمتش را با قیمت عوض می‌کند.
            atrLength = input(10, title = "دوره‌ی ATR", min = 3, max = 100)
            multiplier = input(3, title = "ضریب باند", min = 0.5, max = 8)

            band = ta.supertrend(atrLength, multiplier)
            direction = ta.supertrend_trend(atrLength, multiplier)
            range = ta.atr(atrLength)

            // جهتِ سوپرترند در اولین کندلی که ATR دارد با «صعودی» بذرگذاری می‌شود. اگر نمودار در
            // یک روند نزولی شروع شده باشد، همان کندل جهت را برمی‌گرداند و آن برگشت، معنایی ندارد.
            ready = bar_index >= atrLength * 3

            // تغییر سمت، نه تقاطع قیمت با خط: خط در لحظه‌ی تغییر از یک طرف قیمت به طرف دیگر می‌پرد.
            flipUp = direction > 0 and direction[1] < 0 and ready and confirmed
            flipDown = direction < 0 and direction[1] > 0 and ready and confirmed

            plot(band, title = "سوپرترند", color = color.teal)

            marker(flipUp, title = "برگشت به صعودی", style = "up")
            marker(flipDown, title = "برگشت به نزولی", style = "down")

            // حد ضرر همان خط سوپرترند است — همان چیزی که اگر بشکند، فرض ورود باطل شده. شرط
            // «range > 0» جلوی ستاپی را می‌گیرد که حد ضررش روی خودِ ورود بنشیند.
            risk = close - band
            signal(flipUp and range > 0 and risk > 0, close, band, target = close + risk * 2, buy = true)
        """.trimIndent(),
    )

    /**
     * A twenty-bar channel breakout in the direction of a longer trend.
     *
     * **Signals** the bar whose close broke above the highest high of the bars *before* it, while
     * that close was also above a long moving average. **Evidence** is the channel, the trend
     * average, and nothing else.
     *
     * **What it cannot see**: whether the level being broken is one anybody is watching. Twenty
     * bars is a window, not a structure.
     *
     * Ported from «بریک‌اوتِ دانچیان + فیلترِ روند», and this is the one that was not merely
     * imprecise but **dead**. The original wrote `crossover(close, highest(high, 20))` with a
     * window ending at the current bar. The highest high of a window that contains this bar is at
     * least this bar's high, and a close is never above its own bar's high — so the condition was
     * false on every bar of every chart, and that strategy had never once drawn a buy. The short
     * side was dead for the mirror reason. The channel here is shifted back one bar, which is the
     * channel a breakout is measured against.
     *
     * A mark is not advice.
     */
    val CHANNEL_BREAKOUT = ScriptStrategy(
        id = "channel-breakout",
        warmUpBars = 70,
        source = """
            // شکست کانال بیست کندلی، هم‌جهت با میانگین بلندمدت.
            length = input(20, title = "پنجره‌ی کانال", min = 5, max = 300)
            trendLength = input(50, title = "دوره‌ی روند", min = 10, max = 400)

            // کانالی که کندلِ شکننده در آن نباشد. اگر پنجره تا همین کندل بیاید، بیشینه‌اش دست‌کم
            // به اندازه‌ی سقفِ همین کندل است و قیمت بسته‌شدن هیچ‌وقت از سقف کندل خودش بالاتر نیست:
            // شرط روی هر نموداری همیشه نادرست می‌ماند. نسخه‌ی قدیمی دقیقاً همین را داشت.
            roof = ta.donchian_upper(length)[1]
            floorLine = ta.donchian_lower(length)[1]

            trend = ta.ema(close, trendLength)
            range = ta.atr(14)
            ready = bar_index >= trendLength + length

            plot(roof, title = "سقف کانال", color = color.grey, dashed = true)
            plot(floorLine, title = "کف کانال", color = color.grey, dashed = true)
            plot(trend, title = "روند", color = color.gold)

            buy = ta.crossover(close, roof) and close > trend and ready and confirmed
            sell = ta.crossunder(close, floorLine) and close < trend and ready and confirmed

            marker(buy, title = "شکست صعودی", style = "up")
            marker(sell, title = "شکست نزولی", style = "down")

            // حد ضرر از نوسان خودِ نماد می‌آید، نه از یک درصد ثابت: دو درصد روی طلا و روی
            // بیت‌کوین دو چیز کاملاً متفاوت است.
            risk = range * 2
            signal(buy and range > 0, close, close - risk, target = close + risk * 2, buy = true)
        """.trimIndent(),
    )

    /**
     * Price returning through the outer Bollinger band with the RSI still stretched.
     *
     * **Signals** a buy on the bar the close came back up through the lower band while the RSI was
     * below its oversold input, and the mirror for a sell. **Evidence** is the band, the RSI, and a
     * check that the band is wide enough to mean anything.
     *
     * **What it cannot see**: the difference between a range it can fade and a trend it should not.
     * Mean reversion in a strong trend loses on every signal.
     *
     * Ported from «بازگشت از باند بولینگر + RSI». Two faults repaired. The original marked the
     * forming bar. And it had no width check: a flat or gap-frozen stretch has a standard deviation
     * of zero, which collapses both bands onto the basis, and then every wobble through the basis
     * reads as a return through the band. The width floor is expressed as a percentage of the basis
     * so it means the same thing at any price.
     *
     * A mark is not advice.
     */
    val BAND_REVERSION = ScriptStrategy(
        id = "band-reversion",
        warmUpBars = 40,
        source = """
            // برگشت از لبه‌ی باند، با تأیید RSI.
            length = input(20, title = "دوره‌ی باند", min = 5, max = 200)
            width = input(2, title = "ضریب انحراف", min = 0.5, max = 5)
            rsiLength = input(14, title = "دوره‌ی RSI", min = 2, max = 100)
            oversold = input(35, title = "آستانه‌ی اشباع فروش", min = 5, max = 50)
            overbought = input(65, title = "آستانه‌ی اشباع خرید", min = 50, max = 95)
            minWidth = input(0.6, title = "کمینه‌ی پهنای باند (درصد)", min = 0.05, max = 10)

            upper = ta.bb_upper(close, length, width)
            lower = ta.bb_lower(close, length, width)
            basis = ta.bb_basis(close, length, width)
            rsi = ta.rsi(close, rsiLength)

            plot(upper, title = "لبه‌ی بالا", color = color.grey)
            plot(basis, title = "میانه", color = color.gold, dashed = true)
            plot(lower, title = "لبه‌ی پایین", color = color.grey)

            // روی یک بازه‌ی صاف یا قفل‌شده، انحراف معیار صفر می‌شود و هر دو لبه روی میانه می‌نشینند.
            // آن‌وقت هر تکان کوچکی «برگشت از باند» خوانده می‌شود. این کف پهنا، آن حالت را حذف
            // می‌کند — و چون نسبت به میانه است، روی هر قیمتی یک معنا دارد.
            wideEnough = (upper - lower) / basis * 100 > minWidth
            ready = bar_index >= length * 2

            buy = ta.crossover(close, lower) and rsi < oversold and wideEnough and ready and confirmed
            sell = ta.crossunder(close, upper) and rsi > overbought and wideEnough and ready and confirmed

            marker(buy, title = "برگشت خرید", style = "up")
            marker(sell, title = "برگشت فروش", style = "down")
        """.trimIndent(),
    )

    /**
     * A pullback entry inside a long trend, timed by the stochastic.
     *
     * **Signals** a buy when price is above a long moving average and %K crossed up through %D from
     * inside the oversold zone. **Evidence** is the long average for direction and the stochastic
     * for timing.
     *
     * **What it cannot see**: whether the pullback is a pause or the start of the reversal that
     * ends the trend. It also cannot see the trend on any other timeframe.
     *
     * Ported from «پولبکِ EMA200+استوکاستیک». Three faults repaired. The original marked the
     * forming bar. It signalled from the first bar %K existed, but %D is a moving average of a raw
     * %K whose warm-up head is zero-filled, so the first few %D values are dragged towards zero and
     * the crossings there are artefacts — this waits past them. And the original's stop was
     * `low - atr`, which on the signal bar is a stop below a low that had not finished forming;
     * this uses the previous bar's low, which has.
     *
     * A mark is not advice.
     */
    val TREND_PULLBACK = ScriptStrategy(
        id = "trend-pullback",
        warmUpBars = 217,
        source = """
            // در جهت روند بلندمدت، ورود روی پولبک.
            trendLength = input(200, title = "دوره‌ی روند", min = 20, max = 500)
            stochLength = input(14, title = "دوره‌ی استوکاستیک", min = 3, max = 100)
            smoothing = input(3, title = "هموارسازی", min = 1, max = 20)
            lowZone = input(30, title = "ناحیه‌ی پایین", min = 5, max = 45)
            highZone = input(70, title = "ناحیه‌ی بالا", min = 55, max = 95)

            trend = ta.ema(close, trendLength)
            k = ta.stoch_k(stochLength, smoothing)
            d = ta.stoch_d(stochLength, smoothing)
            range = ta.atr(14)

            plot(trend, title = "روند", color = color.gold, width = 2)
            plot(k, title = "‎%K", color = color.blue, pane = "separate")
            plot(d, title = "‎%D", color = color.orange, pane = "separate")
            hline(highZone, title = "ناحیه‌ی بالا", color = color.grey, pane = "separate")
            hline(lowZone, title = "ناحیه‌ی پایین", color = color.grey, pane = "separate")

            // ‎%D میانگینِ یک ‎%K است که سرِ گرم‌شدنش با صفر پر شده، پس اولین مقادیرش به سمت صفر
            // کشیده شده‌اند و تقاطع‌های آنجا ساختگی‌اند.
            ready = bar_index >= trendLength + stochLength + smoothing

            up = close > trend
            down = close < trend
            buy = up and ta.crossover(k, d) and k < lowZone and ready and confirmed
            sell = down and ta.crossunder(k, d) and k > highZone and ready and confirmed

            marker(buy, title = "خرید پولبک", style = "up")
            marker(sell, title = "فروش پولبک", style = "down")

            // کف کندل قبل، نه کف همین کندل: کندل سیگنال تازه بسته شده و کفِ کندلِ پیش از آن
            // ساختاری است که شکستنش فرض ورود را باطل می‌کند.
            stop = low[1] - range
            signal(buy and range > 0 and close > stop, close, stop, target = close + (close - stop) * 2, buy = true)
        """.trimIndent(),
    )

    /**
     * Three independent confirmations that must agree on the same closed bar.
     *
     * **Signals** a buy when the trend pair is bullish, momentum crossed up through its midline,
     * and volatility is above its own recent average. **Evidence** is two moving averages, an RSI,
     * and an ATR compared against a mean of itself.
     *
     * **What it cannot see**: that its three confirmations are not independent. Two moving averages
     * and an RSI are all functions of the same closes, and agreeing with each other is much of what
     * they do — three lights on one wire.
     *
     * Ported from «ستاپِ همگراییِ سه‌گانه (روند+مومنتوم+نوسان)». Four faults repaired. It marked
     * the forming bar and read every indicator inside its own warm-up. Its thresholds were written
     * into the source, so a reader who wanted a stricter midline had to edit code; they are inputs
     * now. And it demanded the RSI's *crossing* of the midline land on the same bar as two
     * conditions that are states — a coincidence that on real series almost never happens, so the
     * study it shipped was very nearly silent, which is presumably why nobody ever noticed its
     * other faults. This fires on the bar all three first agree, whichever of them agreed last.
     *
     * A mark is not advice.
     */
    val TRIPLE_CONFLUENCE = ScriptStrategy(
        id = "triple-confluence",
        warmUpBars = 105,
        source = """
            // سه تأیید که باید روی یک کندلِ بسته با هم موافق باشند.
            fastLength = input(21, title = "میانگین تند", min = 3, max = 200)
            slowLength = input(55, title = "میانگین کند", min = 5, max = 400)
            rsiLength = input(14, title = "دوره‌ی RSI", min = 2, max = 100)
            midline = input(50, title = "خط میانی RSI", min = 30, max = 70)
            volLength = input(50, title = "دوره‌ی میانگین نوسان", min = 5, max = 200)

            fast = ta.ema(close, fastLength)
            slow = ta.ema(close, slowLength)
            rsi = ta.rsi(close, rsiLength)
            range = ta.atr(14)

            plot(fast, title = "میانگین تند", color = color.gold)
            plot(slow, title = "میانگین کند", color = color.blue)

            // نوسان را با میانگین خودش می‌سنجیم، نه با عددی ثابت: «نوسان بالا» روی هر نمادی
            // عدد دیگری است.
            lively = range > ta.sma(range, volLength)
            ready = bar_index >= slowLength + volLength

            // کندلی که هر سه تأیید برای اولین بار با هم موافق شدند — نه کندلی که همه‌ی آن‌ها در
            // آن اتفاق بیفتند. نسخه‌ی قدیمی می‌خواست تقاطع RSI دقیقاً روی همان کندلی بیفتد که دو
            // شرط دیگر هم برقرارند؛ آن هم‌زمانی روی داده‌های واقعی تقریباً هرگز رخ نمی‌دهد و آن
            // ستاپ عملاً ساکت بود.
            longState = fast > slow and rsi > midline and lively
            shortState = fast < slow and rsi < midline and lively
            buy = longState and not longState[1] and ready and confirmed
            sell = shortState and not shortState[1] and ready and confirmed

            marker(buy, title = "خرید همگرا", style = "up")
            marker(sell, title = "فروش همگرا", style = "down")
        """.trimIndent(),
    )

    /**
     * Four yes-or-no readings added into a score, and the bar the score first reached its
     * threshold.
     *
     * **Signals** the bar on which the count of agreeing conditions rose to the threshold and had
     * not been there on the bar before. **Evidence** is a trend pair, an RSI midline, position
     * against the Bollinger basis, and the sign of the MACD histogram.
     *
     * **What it cannot see**: that the four are correlated, so a score of four is not four times
     * the evidence of a score of one.
     *
     * Ported from «امتیازدهیِ همگرایی (۴ سیگنال)», which carried two faults that cancelled each
     * other's symptoms and so were invisible. It drew its arrow on `crossover(score, 3)`, and a
     * cross demands the series be strictly above the level — an integer score stepping from 2 to 3
     * is not above 3, so the arrow only ever appeared at 4, while the legend and the guide line both
     * said three. And its fourth term was `close > vwap()`, which on any feed that reports no
     * volume compares the close against a VWAP that has fallen back to the close — permanently
     * false, capping the score at 3 and making even the honest threshold unreachable. The cross is
     * replaced by "reached the threshold on this bar and had not on the last", and the volume-bound
     * term by one every feed can answer.
     *
     * A mark is not advice.
     */
    val CONFLUENCE_SCORE = ScriptStrategy(
        id = "confluence-score",
        warmUpBars = 60,
        source = """
            // چهار خواندنِ بله/خیر که جمع می‌شوند. امتیاز صفر تا چهار.
            fastLength = input(20, title = "میانگین تند", min = 3, max = 200)
            slowLength = input(50, title = "میانگین کند", min = 5, max = 400)
            threshold = input(3, title = "آستانه‌ی امتیاز", min = 1, max = 4)

            trendUp = ta.ema(close, fastLength) > ta.ema(close, slowLength)
            momentumUp = ta.rsi(close, 14) > 50
            abovePivot = close > ta.bb_basis(close, 20, 2)
            accelerating = ta.macd_hist(close, 12, 26, 9) > 0

            // شرط‌ها در جمع به یک و صفر تبدیل می‌شوند.
            score = trendUp + momentumUp + abovePivot + accelerating

            plot(score, title = "امتیاز صعودی", color = color.gold, pane = "separate")
            hline(threshold, title = "آستانه", color = color.buy, pane = "separate")

            // «رسیدن به آستانه»، نه «تقاطع با آستانه». تقاطع می‌خواهد سری اکیداً از خط بالاتر برود
            // و امتیازی که از ۲ به ۳ می‌رود از ۳ بالاتر نیست — نسخه‌ی قدیمی به همین دلیل فقط روی ۴
            // فلش می‌گذاشت، در حالی که خط راهنما و متنش هر دو ۳ می‌گفتند.
            strong = score >= threshold
            reached = strong and not strong[1]
            ready = bar_index >= slowLength + 10

            marker(reached and ready and confirmed, title = "رسیدن به آستانه", style = "up")
        """.trimIndent(),
    )

    /**
     * Trend, SuperTrend and momentum agreeing, marked on the bar the agreement began.
     *
     * **Signals** the first closed bar on which price is above a medium moving average, SuperTrend
     * is on the up side, and the RSI is above its threshold. **Evidence** is those three, plus an
     * ATR that sets the stop distance.
     *
     * **What it cannot see**: how long the agreement will last. It marks the beginning and says
     * nothing about the end.
     *
     * Ported from «همگراییِ سه‌گانه: روند+سوپرترند+RSI (حد ضرر/هدف)». The original marked the
     * forming bar. Worse, its condition was a *state* — all three comparisons are true for as long
     * as a trend runs — so it drew an arrow on every bar of that trend and a hundred-bar move
     * became a solid wall of green. A setup is an event; this fires on the bar the state turned on
     * and stays quiet after. The original also drew its risk box from `close` on every bar, so the
     * box moved with the live price and had no relationship to the bar the setup came from; the
     * setup here is anchored to the firing bar.
     *
     * A mark is not advice.
     */
    val SUPERTREND_MOMENTUM = ScriptStrategy(
        id = "supertrend-momentum",
        warmUpBars = 80,
        source = """
            // روند، سوپرترند و مومنتوم — و کندلی که هر سه با هم موافق شدند.
            trendLength = input(50, title = "دوره‌ی روند", min = 10, max = 400)
            atrLength = input(10, title = "دوره‌ی سوپرترند", min = 3, max = 100)
            multiplier = input(3, title = "ضریب سوپرترند", min = 0.5, max = 8)
            buyLevel = input(52, title = "آستانه‌ی RSI خرید", min = 50, max = 80)
            sellLevel = input(48, title = "آستانه‌ی RSI فروش", min = 20, max = 50)
            stopMultiple = input(1.5, title = "ضریب حد ضرر (ATR)", min = 0.3, max = 6)
            rewardMultiple = input(2, title = "هدف، چند برابر ریسک", min = 1, max = 6)

            trend = ta.ema(close, trendLength)
            direction = ta.supertrend_trend(atrLength, multiplier)
            rsi = ta.rsi(close, 14)
            range = ta.atr(14)

            plot(trend, title = "روند", color = color.gold)
            plot(ta.supertrend(atrLength, multiplier), title = "سوپرترند", color = color.teal)

            ready = bar_index >= trendLength + atrLength * 3

            // شرط‌ها یک «حالت»‌اند و تا وقتی روند ادامه دارد درست می‌مانند. نسخه‌ی قدیمی روی هر
            // کندلِ آن حالت فلش می‌گذاشت. ستاپ یک رویداد است، نه یک حالت: کندلی که حالت در آن
            // روشن شد.
            longState = close > trend and direction > 0 and rsi > buyLevel
            shortState = close < trend and direction < 0 and rsi < sellLevel
            buy = longState and not longState[1] and ready and confirmed
            sell = shortState and not shortState[1] and ready and confirmed

            marker(buy, title = "خرید", style = "up")
            marker(sell, title = "فروش", style = "down")

            // ستاپ به کندلِ سیگنال گره می‌خورد، نه به قیمت زنده‌ی لحظه. جعبه‌ی نسخه‌ی قدیمی روی هر
            // کندل از close همان کندل ساخته می‌شد و با هر تیک جابه‌جا می‌شد.
            risk = range * stopMultiple
            signal(buy and range > 0, close, close - risk, target = close + risk * rewardMultiple, buy = true)
        """.trimIndent(),
    )

    /**
     * A channel break confirmed by CCI and by rate of change.
     *
     * **Signals** the bar whose close broke the channel formed by the bars before it, while the CCI
     * was beyond its threshold and price had risen over the momentum window. **Evidence** is the
     * shifted channel, the CCI and the rate of change.
     *
     * **What it cannot see**: volume. The original called this a volume-momentum setup; the thing
     * it actually measured was rate of change of price, and no term in it ever read volume at all.
     *
     * Ported from «همگراییِ سه‌گانه: شکستِ کانال+CCI+حجمِ مومنتوم». It carried the same dead
     * channel as [CHANNEL_BREAKOUT] — a window that included the breaking bar, so neither side
     * could ever fire — and marked the forming bar. Its ±100 CCI thresholds and its ATR multiples
     * were fixed in the source; they are inputs here, because a level tuned on one instrument on
     * one timeframe is not a level anywhere else.
     *
     * A mark is not advice.
     */
    val CHANNEL_MOMENTUM = ScriptStrategy(
        id = "channel-momentum",
        warmUpBars = 50,
        source = """
            // شکست کانال، با تأیید CCI و شتاب قیمت.
            length = input(20, title = "پنجره‌ی کانال", min = 5, max = 300)
            cciLength = input(20, title = "دوره‌ی CCI", min = 3, max = 200)
            cciLevel = input(100, title = "آستانه‌ی CCI", min = 20, max = 300)
            momentumLength = input(10, title = "پنجره‌ی شتاب", min = 2, max = 100)
            stopMultiple = input(2, title = "ضریب حد ضرر (ATR)", min = 0.3, max = 6)

            // پنجره یک کندل عقب کشیده شده. با پنجره‌ای که کندلِ شکننده را در خود دارد، شرط هرگز
            // برقرار نمی‌شود — همان اشکالی که نسخه‌ی قدیمی داشت.
            roof = ta.donchian_upper(length)[1]
            floorLine = ta.donchian_lower(length)[1]

            cci = ta.cci(cciLength)
            momentum = ta.roc(close, momentumLength)
            range = ta.atr(14)
            ready = bar_index >= length + cciLength + momentumLength

            plot(roof, title = "سقف کانال", color = color.grey, dashed = true)
            plot(floorLine, title = "کف کانال", color = color.grey, dashed = true)

            buy = ta.crossover(close, roof) and cci > cciLevel and momentum > 0 and ready and confirmed
            sell = ta.crossunder(close, floorLine) and cci < -cciLevel and momentum < 0 and ready and confirmed

            marker(buy, title = "شکست خرید", style = "up")
            marker(sell, title = "شکست فروش", style = "down")

            risk = range * stopMultiple
            signal(buy and range > 0, close, close - risk, target = close + risk * 2, buy = true)
        """.trimIndent(),
    )

    /**
     * The Ichimoku cloud, read at the bar a chart actually draws it.
     *
     * **Signals** a Tenkan/Kijun cross that happened on the correct side of the cloud. **Evidence**
     * is four midpoints of rolling high-low ranges.
     *
     * **What it cannot see**: the Chikou span, which is the close plotted twenty-six bars into the
     * past. It is deliberately absent. Read forward it is the one part of Ichimoku that looks like
     * a lookahead, and a script that compared today's price with a Chikou value would be comparing
     * today with a bar that has not happened.
     *
     * Ported from «ابرِ ایچیموکو». The original marked the forming bar, and it tested
     * `close > spanB` against an **undisplaced** senkou B. The cloud a chart draws at a bar was
     * computed twenty-six bars earlier; comparing today's close with a span computed from today's
     * fifty-two-bar window is not reading the cloud, it is reading a different indicator that
     * happens to share a name. Here both spans are shifted back by the base period, which is the
     * cloud that is actually on screen at that bar.
     *
     * A mark is not advice.
     */
    val ICHIMOKU_CLOUD = ScriptStrategy(
        id = "ichimoku-cloud",
        warmUpBars = 78,
        source = """
            // ابر ایچیموکو، خوانده‌شده در همان کندلی که نمودار آن را می‌کشد.
            conversionLength = input(9, title = "تنکان", min = 2, max = 60)
            baseLength = input(26, title = "کیجون", min = 3, max = 120)
            spanLength = input(52, title = "اسپن B", min = 5, max = 240)

            conversion = ta.ichimoku_conversion(conversionLength, baseLength)
            base = ta.ichimoku_base(conversionLength, baseLength)

            // ابری که نمودار روی این کندل نشان می‌دهد، «کیجون» کندل قبل‌تر محاسبه شده است. نسخه‌ی
            // قدیمی اسپن جابه‌جانشده را با قیمتِ امروز مقایسه می‌کرد؛ آن دیگر ابر نبود.
            spanA = ta.ichimoku_span_a(conversionLength, baseLength)[baseLength]
            spanB = ta.ichimoku_span_b(conversionLength, baseLength, spanLength)[baseLength]
            cloudTop = math.max(spanA, spanB)
            cloudBottom = math.min(spanA, spanB)

            plot(conversion, title = "تنکان", color = color.blue)
            plot(base, title = "کیجون", color = color.sell)
            plot(cloudTop, title = "سقف ابر", color = color.grey)
            plot(cloudBottom, title = "کف ابر", color = color.grey)

            ready = bar_index >= spanLength + baseLength

            buy = ta.crossover(conversion, base) and close > cloudTop and ready and confirmed
            sell = ta.crossunder(conversion, base) and close < cloudBottom and ready and confirmed

            marker(buy, title = "خرید بالای ابر", style = "up")
            marker(sell, title = "فروش زیر ابر", style = "down")
        """.trimIndent(),
    )

    /**
     * Directional movement: which side is in control, and whether anybody is.
     *
     * **Signals** a +DI/−DI cross taken only while the ADX says a trend is present. **Evidence** is
     * Wilder's directional movement over the high, the low and the close.
     *
     * **What it cannot see**: the ADX is a smoothing of a smoothing and lags badly. By the time it
     * is above its threshold, a good part of the move has usually happened.
     *
     * Ported from «قدرتِ روند (ADX + DI)», which was a study rather than a signal: it coloured the
     * background and left the reader to spot the crossings. It is a signal here, with two faults of
     * the original's repaired along the way — the forming bar, and a fixed threshold of 25 that is
     * a different thing on a five-minute chart than on a daily one.
     *
     * A mark is not advice.
     */
    val DIRECTIONAL_STRENGTH = ScriptStrategy(
        id = "directional-strength",
        warmUpBars = 56,
        source = """
            // جهت و قدرت روند: تقاطع ‎+DI و ‎−DI، فقط وقتی ADX می‌گوید روندی هست.
            length = input(14, title = "دوره‌ی DMI", min = 3, max = 100)
            trendLevel = input(25, title = "آستانه‌ی ADX", min = 10, max = 60)

            adx = ta.adx(length)
            plus = ta.di_plus(length)
            minus = ta.di_minus(length)

            plot(adx, title = "ADX", color = color.purple, width = 2, pane = "separate")
            plot(plus, title = "‎+DI", color = color.buy, pane = "separate")
            plot(minus, title = "‎−DI", color = color.sell, pane = "separate")
            hline(trendLevel, title = "آستانه‌ی روند", color = color.grey, pane = "separate")

            // ADX هموارسازیِ یک هموارسازی است: از حرکت جهت‌دار، بعد از شاخص جهت‌دار، و بعد یک بار
            // دیگر. سه دوره‌ی گرم شدن پشت سر هم دارد.
            ready = bar_index >= length * 4
            trending = adx > trendLevel

            buy = ta.crossover(plus, minus) and trending and ready and confirmed
            sell = ta.crossunder(plus, minus) and trending and ready and confirmed

            marker(buy, title = "کنترل با خریداران", style = "up")
            marker(sell, title = "کنترل با فروشندگان", style = "down")
        """.trimIndent(),
    )

    /**
     * Every strategy, in the order a reader meets them: the plain ones first, the ones that stack
     * several confirmations after, and the two that need the most explaining last.
     */
    val ALL: List<ScriptStrategy> = listOf(
        HULL_CROSS,
        SUPERTREND_FLIP,
        CHANNEL_BREAKOUT,
        BAND_REVERSION,
        TREND_PULLBACK,
        TRIPLE_CONFLUENCE,
        CONFLUENCE_SCORE,
        SUPERTREND_MOMENTUM,
        CHANNEL_MOMENTUM,
        ICHIMOKU_CLOUD,
        DIRECTIONAL_STRENGTH,
    )

    fun byId(id: String): ScriptStrategy? = ALL.firstOrNull { it.id == id }
}
