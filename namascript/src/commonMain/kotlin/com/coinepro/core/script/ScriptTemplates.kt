package com.coinepro.core.script

/**
 * The twelve shapes a strategy comes in, for a reader who pasted a sentence (run Σ, S3 A).
 *
 * ### Why a template picker and not a language model
 *
 * «buy when EMA 20 crosses EMA 50» is not a program and there is no honest way to compile it. What
 * it *is* is one of about a dozen shapes, and the reader will know which one the moment they see it
 * named. So the paste box matches keywords, offers the closest few with their numbers already
 * filled in from whatever the sentence mentioned, and the reader picks. That is a worse answer than
 * a model would give on a good day and a much better one than a model gives on a bad day, when it
 * returns a plausible script for a strategy nobody asked for.
 *
 * Every template compiles, draws, and calls `signal(...)`, which is what makes it an *indicator*
 * in this app rather than a picture: it gets a state, a base rate and an Explain sheet the moment
 * it lands on the chart.
 *
 * Both languages, in one table, for the reason every other table in this module is: the keywords
 * that select a template and the source it produces belong together.
 */
data class ScriptTemplate(
    val id: String,
    val title: String,
    val titleEn: String,
    /** One line, under the title in the picker. */
    val summary: String,
    val summaryEn: String,
    /** What the template is filled with when the sentence named no numbers. */
    val defaults: List<Int>,
    /** The source, with `%1`, `%2`… where [defaults] go. */
    private val shape: String,
) {
    /**
     * The script, with [numbers] substituted for the placeholders.
     *
     * A sentence that named fewer numbers than the template has holes keeps the defaults for the
     * rest, in order, which is the only rule that cannot produce a script with a hole left in it.
     */
    fun source(numbers: List<Int> = emptyList()): String {
        var text = shape.trimIndent()
        defaults.forEachIndexed { index, fallback ->
            text = text.replace("%${index + 1}", (numbers.getOrNull(index) ?: fallback).toString())
        }
        return text.trim() + "\n"
    }

    /** How well this template answers [text], 0 when it does not. Higher is a better match. */
    internal fun score(text: String): Int {
        val lower = text.lowercase()
        return keywords.count { it in lower }
    }

    internal var keywords: List<String> = emptyList()
        private set

    internal fun keyedBy(vararg words: String): ScriptTemplate = apply { keywords = words.toList() }
}

/** The twelve, and the matcher that picks between them. */
object ScriptTemplates {

    /**
     * The templates that answer [text], best first, at most [limit].
     *
     * A sentence that matches nothing gets the first three anyway rather than an empty sheet: a
     * reader who wrote something the matcher did not understand is better served by «here are the
     * three commonest shapes» than by a blank panel telling them to try again.
     */
    fun matching(text: String, limit: Int = 3): List<ScriptTemplate> {
        val ranked = ALL.map { it to it.score(text) }.filter { it.second > 0 }
        if (ranked.isEmpty()) return ALL.take(limit)
        return ranked.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    /**
     * Every whole number in [text], in order, as the template's holes.
     *
     * Persian digits count: a reader writing «وقتی میانگین ۲۰ از ۵۰ رد شد» has named two periods
     * and the picker should arrive with them already in place.
     */
    fun numbersIn(text: String): List<Int> =
        NUMBER.findAll(text.map { PERSIAN_DIGITS.indexOf(it).takeIf { i -> i >= 0 }?.digitToChar() ?: it }.joinToString(""))
            .mapNotNull { it.value.toIntOrNull() }
            .toList()

    val ALL: List<ScriptTemplate> = listOf(
        ScriptTemplate(
            id = "ma-cross",
            title = "تقاطع دو میانگین",
            titleEn = "Two moving averages cross",
            summary = "میانگین تند از کند رد می‌شود.",
            summaryEn = "A fast average crosses a slow one.",
            defaults = listOf(20, 50),
            shape = """
                fast = ta.ema(close, input(%1, title = "دوره‌ی تند", min = 2, max = 400))
                slow = ta.ema(close, input(%2, title = "دوره‌ی کند", min = 2, max = 400))
                plot(fast, title = "تند", color = color.gold)
                plot(slow, title = "کند", color = color.blue)
                signal(ta.crossover(fast, slow), text = "میانگین تند از کند رد شد")
                signal(ta.crossunder(fast, slow), text = "میانگین تند زیر کند رفت")
            """,
        ).keyedBy("cross", "تقاطع", "میانگین", "ema", "sma", "moving average", "رد شد"),
        ScriptTemplate(
            id = "rsi-zones",
            title = "بازگشت از اشباع",
            titleEn = "Coming back from an extreme",
            summary = "RSI از کف برمی‌گردد یا از سقف پایین می‌آید.",
            summaryEn = "RSI comes back over its floor or under its ceiling.",
            defaults = listOf(14, 30, 70),
            shape = """
                r = ta.rsi(close, input(%1, title = "دوره", min = 2, max = 200))
                floorLevel = input(%2, title = "کف", min = 1, max = 49)
                ceilingLevel = input(%3, title = "سقف", min = 51, max = 99)
                plot(r, title = "RSI", pane = "own")
                hline(floorLevel, pane = "own")
                hline(ceilingLevel, pane = "own")
                signal(ta.crossover(r, floorLevel), text = "RSI از کف برگشت")
                signal(ta.crossunder(r, ceilingLevel), text = "RSI از سقف پایین آمد")
            """,
        ).keyedBy("rsi", "اشباع", "oversold", "overbought", "اشباع فروش", "اشباع خرید"),
        ScriptTemplate(
            id = "breakout",
            title = "شکست سقف و کف",
            titleEn = "Breaking a high or a low",
            summary = "قیمت از بالاترین یا پایین‌ترین n کندل رد می‌شود.",
            summaryEn = "Price passes the highest or lowest of the last n bars.",
            defaults = listOf(20),
            shape = """
                span = input(%1, title = "تعداد کندل", min = 2, max = 400)
                top = ta.highest(high, span)[1]
                bottom = ta.lowest(low, span)[1]
                plot(top, title = "سقف", color = color.green)
                plot(bottom, title = "کف", color = color.red)
                signal(close > top, text = "سقف شکسته شد")
                signal(close < bottom, text = "کف شکسته شد")
            """,
        ).keyedBy("breakout", "شکست", "سقف", "کف", "high", "low", "range"),
        ScriptTemplate(
            id = "band-reversion",
            title = "برگشت به میانگین",
            titleEn = "Back to the middle",
            summary = "قیمت از باند برمی‌گردد.",
            summaryEn = "Price comes back from a band.",
            defaults = listOf(20, 2),
            shape = """
                span = input(%1, title = "دوره", min = 2, max = 200)
                width = input(%2, title = "انحراف", min = 1, max = 5)
                mid = ta.sma(close, span)
                dev = ta.stdev(close, span) * width
                plot(mid, title = "میانه")
                plot(mid + dev, title = "بالا", color = color.red)
                plot(mid - dev, title = "پایین", color = color.green)
                signal(ta.crossover(close, mid - dev), text = "از باند پایین برگشت")
                signal(ta.crossunder(close, mid + dev), text = "از باند بالا برگشت")
            """,
        ).keyedBy("bollinger", "باند", "برگشت", "reversion", "mean", "میانگین‌گرایی"),
        ScriptTemplate(
            id = "macd-signal",
            title = "تقاطع مکدی",
            titleEn = "MACD crossing its signal",
            summary = "مکدی از خط سیگنالش رد می‌شود.",
            summaryEn = "MACD crosses its signal line.",
            defaults = listOf(12, 26, 9),
            shape = """
                fastLen = input(%1, title = "تند", min = 2, max = 200)
                slowLen = input(%2, title = "کند", min = 2, max = 400)
                signalLen = input(%3, title = "سیگنال", min = 1, max = 100)
                macdLine = ta.ema(close, fastLen) - ta.ema(close, slowLen)
                sig = ta.ema(macdLine, signalLen)
                plot(macdLine, title = "مکدی", pane = "own")
                plot(sig, title = "سیگنال", ownPane = true, color = color.gold)
                signal(ta.crossover(macdLine, sig), text = "مکدی از سیگنالش رد شد")
                signal(ta.crossunder(macdLine, sig), text = "مکدی زیر سیگنالش رفت")
            """,
        ).keyedBy("macd", "مکدی", "همگرایی"),
        ScriptTemplate(
            id = "trend-pullback",
            title = "پولبک در روند",
            titleEn = "A pullback inside a trend",
            summary = "بالای میانگین بلند، برگشت از میانگین کوتاه.",
            summaryEn = "Above the slow average, coming back off the fast one.",
            defaults = listOf(200, 20),
            shape = """
                trendLen = input(%1, title = "دوره‌ی روند", min = 10, max = 400)
                pullLen = input(%2, title = "دوره‌ی پولبک", min = 2, max = 200)
                trend = ta.ema(close, trendLen)
                pull = ta.ema(close, pullLen)
                plot(trend, title = "روند", color = color.blue)
                plot(pull, title = "پولبک", color = color.gold)
                rising = close > trend
                signal(rising and ta.crossover(close, pull), text = "پولبک در روند صعودی تمام شد")
                signal(not rising and ta.crossunder(close, pull), text = "پولبک در روند نزولی تمام شد")
            """,
        ).keyedBy("pullback", "پولبک", "اصلاح", "trend", "روند"),
        ScriptTemplate(
            id = "volume-spike",
            title = "جهش حجم",
            titleEn = "A jump in volume",
            summary = "حجم چند برابر میانگینش می‌شود.",
            summaryEn = "Volume goes to a multiple of its own average.",
            defaults = listOf(20, 2),
            shape = """
                span = input(%1, title = "دوره", min = 2, max = 200)
                times = input(%2, title = "چند برابر", min = 1, max = 10)
                average = ta.sma(volume, span)
                plot(volume, title = "حجم", pane = "own")
                plot(average * times, title = "آستانه", ownPane = true, color = color.gold)
                signal(volume > average * times and close > open, text = "جهش حجم با کندل صعودی")
                signal(volume > average * times and close < open, text = "جهش حجم با کندل نزولی")
            """,
        ).keyedBy("volume", "حجم", "spike", "جهش"),
        ScriptTemplate(
            id = "atr-stop",
            title = "حد ضرر متحرک",
            titleEn = "A trailing stop",
            summary = "خطی که به اندازه‌ی چند ATR زیر قیمت می‌آید.",
            summaryEn = "A line that follows the price a few ATRs below it.",
            defaults = listOf(14, 3),
            shape = """
                span = input(%1, title = "دوره‌ی ATR", min = 2, max = 200)
                times = input(%2, title = "ضریب", min = 1, max = 10)
                band = ta.atr(span) * times
                stopLine = ta.highest(close, span) - band
                plot(stopLine, title = "حد ضرر", color = color.red)
                signal(ta.crossunder(close, stopLine), text = "قیمت زیر حد ضرر متحرک رفت")
            """,
        ).keyedBy("atr", "حد ضرر", "stop", "trailing", "متحرک"),
        ScriptTemplate(
            id = "inside-bar",
            title = "کندل داخلی",
            titleEn = "An inside bar",
            summary = "کندلی که کاملاً داخل کندل قبلش است.",
            summaryEn = "A bar wholly inside the one before it.",
            defaults = emptyList(),
            shape = """
                inside = high < high[1] and low > low[1]
                marker(inside, style = "circle", title = "کندل داخلی")
                signal(inside and close > open, text = "کندل داخلی صعودی")
                signal(inside and close < open, text = "کندل داخلی نزولی")
            """,
        ).keyedBy("inside", "کندل داخلی", "الگو", "pattern", "engulf"),
        ScriptTemplate(
            id = "gap",
            title = "شکاف قیمتی",
            titleEn = "A price gap",
            summary = "کندل با فاصله از کندل قبل باز می‌شود.",
            summaryEn = "A bar opens away from the one before it.",
            defaults = listOf(1),
            shape = """
                size = input(%1, title = "درصد", min = 1, max = 20)
                gapUp = open > close[1] * (1 + size / 100)
                gapDown = open < close[1] * (1 - size / 100)
                marker(gapUp, style = "arrowup", title = "شکاف صعودی")
                marker(gapDown, style = "arrowdown", title = "شکاف نزولی")
                signal(gapUp, text = "شکاف صعودی")
                signal(gapDown, text = "شکاف نزولی")
            """,
        ).keyedBy("gap", "شکاف", "پرش"),
        ScriptTemplate(
            id = "adx-trend",
            title = "روند قوی",
            titleEn = "A trend worth trading",
            summary = "ADX از آستانه رد می‌شود.",
            summaryEn = "ADX passes a threshold.",
            defaults = listOf(14, 25),
            shape = """
                span = input(%1, title = "دوره", min = 2, max = 200)
                gate = input(%2, title = "آستانه", min = 5, max = 60)
                strength = ta.adx(span)
                plot(strength, title = "ADX", pane = "own")
                hline(gate, pane = "own")
                signal(ta.crossover(strength, gate) and close > ta.ema(close, span), text = "روند صعودی جان گرفت")
                signal(ta.crossover(strength, gate) and close < ta.ema(close, span), text = "روند نزولی جان گرفت")
            """,
        ).keyedBy("adx", "روند قوی", "strength", "قدرت روند"),
        ScriptTemplate(
            id = "session-open",
            title = "شکست اولین کندل",
            titleEn = "Breaking the first bar",
            summary = "قیمت از سقف یا کف اولین کندل روز رد می‌شود.",
            summaryEn = "Price passes the first bar of the run's high or low.",
            defaults = listOf(1),
            shape = """
                back = input(%1, title = "کندل مرجع", min = 1, max = 20)
                top = high[back]
                bottom = low[back]
                plot(top, title = "سقف مرجع", color = color.green)
                plot(bottom, title = "کف مرجع", color = color.red)
                signal(ta.crossover(close, top), text = "سقف کندل مرجع شکست")
                signal(ta.crossunder(close, bottom), text = "کف کندل مرجع شکست")
            """,
        ).keyedBy("session", "باز شدن", "اولین کندل", "open", "روز"),
    )

    private val NUMBER = Regex("""\d+""")
    private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
}
