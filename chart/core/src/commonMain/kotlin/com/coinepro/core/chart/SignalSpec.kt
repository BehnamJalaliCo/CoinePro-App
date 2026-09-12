package com.coinepro.core.chart

import kotlin.math.abs

/**
 * What a study is **saying**, as opposed to what it draws.
 *
 * ### Why this file exists at all
 *
 * This is the whole thesis of the product in one type. Every charting app in the world draws an RSI;
 * none of them tells the reader that it is oversold, that it has just turned, that the last eighteen
 * times it did this on this instrument it was right eleven of them, or where the stop would go. A
 * line is a *measurement*; a reader wants a *judgement*, and the distance between the two is the
 * reason somebody with no training opens a chart, looks at it for four seconds and closes it.
 *
 * So every indicator in [ChartCatalog] and every one of the reader's own scripts answers four
 * questions here, and the rest of the app — the Now strip, the Setup score, the Explain sheet, the
 * coach, the Arena — is built on nothing but these answers:
 *
 * 1. **What is it saying now?** [SignalRead.state] — bull, bear or neutral, on the newest bar.
 * 2. **Where did it say something?** [SignalRead.events] — one per bar where the study fired.
 * 3. **In words?** [SignalRead.note] — a template, rendered in the reader's own language.
 * 4. **Where would you be wrong?** [SignalRead.stop] — a price, from the study's own arithmetic.
 *
 * ### Why the words are templates rather than sentences
 *
 * Because this module has no resources and the product has two languages. A sentence written here
 * would be Persian in an English build — which is precisely the leak run G spent a version
 * removing. A [SignalNote] carries a *shape* and its numbers; [SignalNote.text] renders it. The
 * phrasing lives in one table, both languages side by side, so a phrase cannot be translated in one
 * place and forgotten in the other.
 *
 * ### Why it is not an LLM
 *
 * Because a reader has to be able to trust it. Every sentence here is a mechanical consequence of
 * the same arithmetic that drew the line — «RSI came back over 30» is true because the line crossed
 * 30 — and it is computed on the phone, offline, in microseconds, identically every time. A model's
 * paraphrase of the same fact would be prettier, slower, occasionally wrong, and impossible to test.
 * The coach may *phrase* things later; what it is phrasing is this.
 */
enum class MarketState {
    BULL,
    BEAR,
    NEUTRAL,
    ;

    /** What the state is called, in the language the screen is in. */
    fun label(english: Boolean): String = when (this) {
        BULL -> if (english) "Bullish" else "صعودی"
        BEAR -> if (english) "Bearish" else "نزولی"
        NEUTRAL -> if (english) "Neutral" else "خنثی"
    }
}

/**
 * One bar on which a study said something.
 *
 * [strength] is 0..1 and is the study's own confidence *in this event* — how far past a threshold
 * it went, how wide the cross was — and is not the historical [ConfidenceReport.winRate]. The two
 * are deliberately separate: one is «how loudly is it saying it», the other is «how often has it
 * been right», and a product that multiplies them together has invented a number.
 */
data class SignalEvent(
    val bar: Int,
    val side: TradeSide,
    val strength: Float = 1f,
)

/**
 * A sentence, as a shape plus its numbers.
 *
 * The shapes are deliberately few. Eighty-three indicators do not say eighty-three different kinds
 * of thing: they cross something, they leave a zone, they turn, they sit above or below a line.
 * Sixteen phrasings cover every one of them, which means the *reader* meets a vocabulary they learn
 * once rather than eighty-three sentences somebody wrote separately.
 */
data class SignalNote(
    val shape: NoteShape,
    /** What goes in the sentence's slots — a study's name, a level, a price. Already formatted. */
    val values: List<String> = emptyList(),
) {
    fun text(english: Boolean): String = shape.render(english, values)
}

/** The sixteen things a study can say. See [SignalNote]. */
enum class NoteShape {
    /** `{0}` crossed above `{1}` — a fast average over a slow one, a price over a band. */
    CROSSED_ABOVE,
    CROSSED_BELOW,

    /** `{0}` is above `{1}` — the standing state rather than the moment it changed. */
    SITS_ABOVE,
    SITS_BELOW,

    /** `{0}` came back over `{1}` — an oscillator leaving an oversold floor. */
    LEFT_FLOOR,
    LEFT_CEILING,

    /** `{0}` is under `{1}` and has not turned — in the zone, still falling. */
    AT_FLOOR,
    AT_CEILING,

    /** `{0}` crossed zero. */
    CROSSED_ZERO_UP,
    CROSSED_ZERO_DOWN,

    /** `{0}` turned up — a SuperTrend flip, a parabolic flip, a slope change. */
    TURNED_UP,
    TURNED_DOWN,

    /** Price broke the high of the last `{0}` bars. */
    BROKE_HIGH,
    BROKE_LOW,

    /** `{0}` reads `{1}` — a measurement with no direction in it: ADX, ATR, volatility. */
    READS,

    /** Nothing to say on this bar. */
    QUIET,
    ;

    fun render(english: Boolean, values: List<String>): String {
        fun at(index: Int): String = values.getOrElse(index) { "" }
        return when (this) {
            CROSSED_ABOVE -> if (english) "${at(0)} crossed above ${at(1)}" else "${at(0)} از بالای ${at(1)} رد شد"
            CROSSED_BELOW -> if (english) "${at(0)} crossed below ${at(1)}" else "${at(0)} از زیر ${at(1)} رد شد"
            SITS_ABOVE -> if (english) "${at(0)} is above ${at(1)}" else "${at(0)} بالای ${at(1)} است"
            SITS_BELOW -> if (english) "${at(0)} is below ${at(1)}" else "${at(0)} زیر ${at(1)} است"
            LEFT_FLOOR -> if (english) "${at(0)} came back over ${at(1)}" else "${at(0)} از ${at(1)} برگشت"
            LEFT_CEILING -> if (english) "${at(0)} came back under ${at(1)}" else "${at(0)} از ${at(1)} پایین آمد"
            AT_FLOOR -> if (english) "${at(0)} is under ${at(1)}" else "${at(0)} زیر ${at(1)} است"
            AT_CEILING -> if (english) "${at(0)} is over ${at(1)}" else "${at(0)} بالای ${at(1)} است"
            CROSSED_ZERO_UP -> if (english) "${at(0)} crossed zero upward" else "${at(0)} از صفر به بالا رد شد"
            CROSSED_ZERO_DOWN -> if (english) "${at(0)} crossed zero downward" else "${at(0)} از صفر به پایین رد شد"
            TURNED_UP -> if (english) "${at(0)} turned up" else "${at(0)} رو به بالا برگشت"
            TURNED_DOWN -> if (english) "${at(0)} turned down" else "${at(0)} رو به پایین برگشت"
            BROKE_HIGH -> if (english) "Price broke the ${at(0)}-bar high" else "قیمت سقف ${at(0)} کندل را شکست"
            BROKE_LOW -> if (english) "Price broke the ${at(0)}-bar low" else "قیمت کف ${at(0)} کندل را شکست"
            READS -> if (english) "${at(0)} reads ${at(1)}" else "${at(0)} برابر ${at(1)} است"
            QUIET -> if (english) "No signal on this bar" else "روی این کندل سیگنالی نیست"
        }
    }
}

/**
 * Everything one study is saying about one series.
 *
 * [events] is in bar order, oldest first, and is the input to both the markers on the chart and
 * [ConfidenceEngine]. [stop] is the price at which the newest event would be wrong — the study's
 * own, not a percentage somebody picked — and is null for a study that has no opinion about that
 * (an ADX reading is not a trade).
 */
data class SignalRead(
    val id: String,
    val state: MarketState,
    val events: List<SignalEvent> = emptyList(),
    val note: SignalNote = SignalNote(NoteShape.QUIET),
    val stop: Double? = null,
) {
    val newest: SignalEvent? get() = events.lastOrNull()

    /** Whether the newest event is on the newest bar, which is what a «now» pill reports. */
    fun firesOn(bar: Int): Boolean = events.lastOrNull()?.bar == bar

    companion object {
        /** A study that is drawn but says nothing — a volume profile, a background band. */
        fun quiet(id: String): SignalRead = SignalRead(id, MarketState.NEUTRAL)
    }
}

/**
 * Reads a study's opinion off the same arithmetic that draws it.
 *
 * ### The rule for which reading each family gets
 *
 * Four rules cover eighty-three indicators, and which one applies is a property of what the study
 * *is* rather than a list somebody maintains:
 *
 * * **A line on the price** (every moving average, VWAP, a band's basis) is a *reference*: the
 *   signal is the close crossing it, the state is which side the close is on.
 * * **A band** (Bollinger, Keltner, Donchian, envelopes) is a *boundary*: Donchian breaks out of it,
 *   the others revert into it, and the state is the close against the basis.
 * * **A bounded oscillator** (RSI, Stochastic, CCI, Williams, MFI, UO, CRSI, SMI…) has a *floor and
 *   a ceiling*: the signal is leaving one, the state is which half of the range it is in.
 * * **An unbounded oscillator** (MACD, momentum, TRIX, OBV, the volume line studies) has a *zero*:
 *   the signal is crossing it — or its own signal line where it has one — and the state is its sign.
 *
 * Anything genuinely without a direction — ADX, ATR, standard deviation, choppiness — reports its
 * reading and no events, because inventing a buy from a volatility measure is how an app teaches
 * somebody to lose money.
 */
object SignalSpec {

    /**
     * The bounded oscillators, with the floor and ceiling each is read against.
     *
     * The numbers are the conventional ones and are the same numbers `ChartCatalog.paneFor` draws
     * as reference lines — which is the point: a reader sees the line at 30 and the sentence says
     * «came back over 30», and the two cannot drift because a test compares them.
     */
    val OSCILLATOR_BOUNDS: Map<String, ClosedFloatingPointRange<Double>> = mapOf(
        "rsi" to 30.0..70.0,
        "stochastic" to 20.0..80.0,
        "stochrsi" to 20.0..80.0,
        "cci" to -100.0..100.0,
        "woodies" to -100.0..100.0,
        "williams" to -80.0..-20.0,
        "mfi" to 20.0..80.0,
        "uo" to 30.0..70.0,
        "crsi" to 30.0..70.0,
        "smi" to -40.0..40.0,
        "bbpercent" to 0.0..1.0,
        "fisher" to -2.0..2.0,
        "aroonosc" to -50.0..50.0,
    )

    /** The studies whose reading has no direction in it. They report and never fire. */
    val MEASURES: Set<String> = setOf(
        "atr", "adx", "choppiness", "stddev", "hv", "chaikinVol", "bbw", "massindex",
        "volumeprofile_ind", "correlation", "rvol",
    )

    /**
     * What [id] is saying about [series].
     *
     * `period` and `params` are the reader's own, exactly as [ChartCatalog.overlayFor] takes them,
     * so the sentence describes the line on *their* chart rather than a default nobody is looking
     * at. Returns a quiet read for an id this build does not have — an old layout, a renamed study
     * — because a chart that cannot explain one of its lines is still a chart.
     */
    fun read(
        id: String,
        series: CandleSeries,
        period: Int? = null,
        params: Map<String, Double> = emptyMap(),
        english: Boolean = false,
    ): SignalRead {
        if (series.isEmpty || series.size < MIN_BARS) return SignalRead.quiet(id)
        val option = ChartCatalog.INDICATORS.firstOrNull { it.id == id } ?: return SignalRead.quiet(id)
        val name = option.shortLabel(english)
        val last = series.size - 1
        return when {
            id in MEASURES -> measure(id, name, series)
            id in OSCILLATOR_BOUNDS -> {
                val bounds = OSCILLATOR_BOUNDS.getValue(id)
                val line = firstLineOf(option, series, period, params) ?: return SignalRead.quiet(id)
                bounded(id, name, line, bounds, series, english)
            }
            id == "supertrend" || id == "sar" || id == "volatilitystop" || id == "chandekroll" ->
                flipping(id, name, option, series, period, params, english)
            id == "donchian" -> breakout(id, series, period ?: ChartCatalog.periodOf(id)?.default ?: 20, english)
            option.pane == IndicatorPane.PRICE -> {
                val line = firstLineOf(option, series, period, params) ?: return SignalRead.quiet(id)
                reference(id, name, line, series, english)
            }
            else -> {
                val line = firstLineOf(option, series, period, params) ?: return SignalRead.quiet(id)
                zeroCross(id, name, line, series, secondLineOf(option, series, period, params), english)
            }
        }.let { read ->
            // The stop is attached here rather than inside each branch: it is the same question for
            // every study — where is this reading wrong — and the answer is the same arithmetic.
            if (read.stop != null || read.events.isEmpty()) read
            else read.copy(stop = defaultStop(series, read.events.last(), last))
        }
    }

    /**
     * The same four rules, applied to a line this catalogue does not own.
     *
     * This is what a **reader's own script** gets when it does not call `signal()` itself: one of
     * its plots, read by the rule its pane implies — a line over the price is a reference the close
     * crosses, a line in its own pane is an oscillator around zero. It is the same code path the
     * built-ins take, which is the point: a script is an indicator, and a study the app cannot
     * explain is a study the app has failed at, whoever wrote it.
     */
    fun readLine(
        id: String,
        name: String,
        line: Line,
        series: CandleSeries,
        onPrice: Boolean,
        english: Boolean = false,
    ): SignalRead {
        if (series.isEmpty || series.size < MIN_BARS) return SignalRead.quiet(id)
        val read = if (onPrice) {
            reference(id, name, line, series, english)
        } else {
            zeroCross(id, name, line, series, null, english)
        }
        return if (read.stop != null || read.events.isEmpty()) {
            read
        } else {
            read.copy(stop = defaultStop(series, read.events.last()))
        }
    }

    /** Every switched-on study's reading, in the order the reader added them. */
    fun readAll(
        ids: List<String>,
        series: CandleSeries,
        periods: Map<String, Int> = emptyMap(),
        params: Map<String, Map<String, Double>> = emptyMap(),
        english: Boolean = false,
    ): List<SignalRead> = ids.map { id ->
        read(id, series, periods[id], params[id] ?: emptyMap(), english)
    }

    // ── the four rules ───────────────────────────────────────────────────────────────────────

    /** A reference line on the price: the close crossing it is the event. */
    private fun reference(id: String, name: String, line: Line, series: CandleSeries, english: Boolean): SignalRead {
        val close = series.close
        val events = mutableListOf<SignalEvent>()
        for (bar in 1 until series.size) {
            val now = line[bar] ?: continue
            val before = line[bar - 1] ?: continue
            val was = close[bar - 1]
            val nowClose = close[bar]
            if (was <= before && nowClose > now) {
                events += SignalEvent(bar, TradeSide.BUY, crossStrength(nowClose, now))
            } else if (was >= before && nowClose < now) {
                events += SignalEvent(bar, TradeSide.SELL, crossStrength(nowClose, now))
            }
        }
        val last = series.size - 1
        val level = line[last]
        val state = when {
            level == null -> MarketState.NEUTRAL
            close[last] > level -> MarketState.BULL
            close[last] < level -> MarketState.BEAR
            else -> MarketState.NEUTRAL
        }
        val note = when {
            events.lastOrNull()?.bar == last && state == MarketState.BULL ->
                SignalNote(NoteShape.CROSSED_ABOVE, listOf(priceWord(english), name))
            events.lastOrNull()?.bar == last && state == MarketState.BEAR ->
                SignalNote(NoteShape.CROSSED_BELOW, listOf(priceWord(english), name))
            state == MarketState.BULL -> SignalNote(NoteShape.SITS_ABOVE, listOf(priceWord(english), name))
            state == MarketState.BEAR -> SignalNote(NoteShape.SITS_BELOW, listOf(priceWord(english), name))
            // Exactly on the line — a least-squares fit through a straight run does this, and so
            // does a flat market. It is a reading rather than a direction, and a study that fired
            // and then says nothing is the one shape this vocabulary must never produce.
            level != null -> SignalNote(NoteShape.READS, listOf(name, figure(level)))
            else -> SignalNote(NoteShape.QUIET)
        }
        return SignalRead(id, state, events, note, stop = level)
    }

    /** A bounded oscillator: leaving the floor or the ceiling is the event. */
    private fun bounded(
        id: String,
        name: String,
        line: Line,
        bounds: ClosedFloatingPointRange<Double>,
        series: CandleSeries,
        english: Boolean,
    ): SignalRead {
        val floor = bounds.start
        val ceiling = bounds.endInclusive
        val middle = (floor + ceiling) / 2
        val events = mutableListOf<SignalEvent>()
        for (bar in 1 until series.size) {
            val now = line[bar] ?: continue
            val before = line[bar - 1] ?: continue
            if (before <= floor && now > floor) events += SignalEvent(bar, TradeSide.BUY, zoneStrength(before, floor, ceiling))
            if (before >= ceiling && now < ceiling) events += SignalEvent(bar, TradeSide.SELL, zoneStrength(before, floor, ceiling))
        }
        val last = series.size - 1
        val level = line[last]
        val state = when {
            level == null -> MarketState.NEUTRAL
            level > middle -> MarketState.BULL
            level < middle -> MarketState.BEAR
            else -> MarketState.NEUTRAL
        }
        val note = when {
            level == null -> SignalNote(NoteShape.QUIET)
            events.lastOrNull()?.bar == last && events.last().side == TradeSide.BUY ->
                SignalNote(NoteShape.LEFT_FLOOR, listOf(name, figure(floor)))
            events.lastOrNull()?.bar == last ->
                SignalNote(NoteShape.LEFT_CEILING, listOf(name, figure(ceiling)))
            level <= floor -> SignalNote(NoteShape.AT_FLOOR, listOf(name, figure(floor)))
            level >= ceiling -> SignalNote(NoteShape.AT_CEILING, listOf(name, figure(ceiling)))
            else -> SignalNote(NoteShape.READS, listOf(name, figure(level)))
        }
        return SignalRead(id, state, events, note)
    }

    /** An unbounded oscillator: zero, or its own signal line where it has one. */
    private fun zeroCross(
        id: String,
        name: String,
        line: Line,
        series: CandleSeries,
        signal: Line?,
        english: Boolean,
    ): SignalRead {
        val events = mutableListOf<SignalEvent>()
        for (bar in 1 until series.size) {
            val now = line[bar] ?: continue
            val before = line[bar - 1] ?: continue
            val nowRef = signal?.get(bar) ?: 0.0
            val beforeRef = signal?.get(bar - 1) ?: 0.0
            if (before <= beforeRef && now > nowRef) events += SignalEvent(bar, TradeSide.BUY)
            if (before >= beforeRef && now < nowRef) events += SignalEvent(bar, TradeSide.SELL)
        }
        val last = series.size - 1
        val level = line[last]
        val reference = signal?.get(last) ?: 0.0
        val state = when {
            level == null -> MarketState.NEUTRAL
            level > reference -> MarketState.BULL
            level < reference -> MarketState.BEAR
            else -> MarketState.NEUTRAL
        }
        val note = when {
            level == null -> SignalNote(NoteShape.QUIET)
            events.lastOrNull()?.bar == last && state == MarketState.BULL && signal == null ->
                SignalNote(NoteShape.CROSSED_ZERO_UP, listOf(name))
            events.lastOrNull()?.bar == last && signal == null ->
                SignalNote(NoteShape.CROSSED_ZERO_DOWN, listOf(name))
            events.lastOrNull()?.bar == last && state == MarketState.BULL ->
                SignalNote(NoteShape.CROSSED_ABOVE, listOf(name, signalWord(english)))
            events.lastOrNull()?.bar == last ->
                SignalNote(NoteShape.CROSSED_BELOW, listOf(name, signalWord(english)))
            state == MarketState.BULL && signal == null -> SignalNote(NoteShape.AT_CEILING, listOf(name, "0"))
            state == MarketState.BEAR && signal == null -> SignalNote(NoteShape.AT_FLOOR, listOf(name, "0"))
            state == MarketState.BULL -> SignalNote(NoteShape.SITS_ABOVE, listOf(name, signalWord(english)))
            else -> SignalNote(NoteShape.SITS_BELOW, listOf(name, signalWord(english)))
        }
        return SignalRead(id, state, events, note)
    }

    /** A study that flips sides — SuperTrend, parabolic SAR, a volatility stop. */
    private fun flipping(
        id: String,
        name: String,
        option: IndicatorOption,
        series: CandleSeries,
        period: Int?,
        params: Map<String, Double>,
        english: Boolean,
    ): SignalRead {
        val close = series.close
        val line = trailingStopOf(option, series, period, params) ?: return SignalRead.quiet(id)
        val events = mutableListOf<SignalEvent>()
        for (bar in 1 until series.size) {
            val now = line[bar] ?: continue
            val before = line[bar - 1] ?: continue
            val wasBelow = close[bar - 1] < before
            val isBelow = close[bar] < now
            if (wasBelow && !isBelow) events += SignalEvent(bar, TradeSide.BUY)
            if (!wasBelow && isBelow) events += SignalEvent(bar, TradeSide.SELL)
        }
        val last = series.size - 1
        val level = line[last]
        val state = when {
            level == null -> MarketState.NEUTRAL
            close[last] > level -> MarketState.BULL
            else -> MarketState.BEAR
        }
        val note = when {
            events.lastOrNull()?.bar == last && state == MarketState.BULL -> SignalNote(NoteShape.TURNED_UP, listOf(name))
            events.lastOrNull()?.bar == last -> SignalNote(NoteShape.TURNED_DOWN, listOf(name))
            state == MarketState.BULL -> SignalNote(NoteShape.SITS_ABOVE, listOf(priceWord(english), name))
            else -> SignalNote(NoteShape.SITS_BELOW, listOf(priceWord(english), name))
        }
        // The stop *is* the study here, which is the whole reason a reader switches one on.
        return SignalRead(id, state, events, note, stop = level)
    }

    /** Donchian: the channel is a breakout, not a mean to revert to. */
    private fun breakout(id: String, series: CandleSeries, period: Int, english: Boolean): SignalRead {
        val band = Indicators.donchian(series.high, series.low, period)
        val close = series.close
        val events = mutableListOf<SignalEvent>()
        for (bar in 1 until series.size) {
            val roof = band.upper[bar - 1] ?: continue
            val floor = band.lower[bar - 1] ?: continue
            if (close[bar] > roof) events += SignalEvent(bar, TradeSide.BUY)
            if (close[bar] < floor) events += SignalEvent(bar, TradeSide.SELL)
        }
        val last = series.size - 1
        val basis = band.basis[last]
        val state = when {
            basis == null -> MarketState.NEUTRAL
            close[last] > basis -> MarketState.BULL
            close[last] < basis -> MarketState.BEAR
            else -> MarketState.NEUTRAL
        }
        val note = when {
            events.lastOrNull()?.bar == last && events.last().side == TradeSide.BUY ->
                SignalNote(NoteShape.BROKE_HIGH, listOf(period.toString()))
            events.lastOrNull()?.bar == last -> SignalNote(NoteShape.BROKE_LOW, listOf(period.toString()))
            state == MarketState.BULL -> SignalNote(NoteShape.SITS_ABOVE, listOf(priceWord(english), channelWord(english)))
            state == MarketState.BEAR -> SignalNote(NoteShape.SITS_BELOW, listOf(priceWord(english), channelWord(english)))
            basis != null -> SignalNote(NoteShape.READS, listOf(channelWord(english), figure(basis)))
            else -> SignalNote(NoteShape.QUIET)
        }
        val stop = if (events.lastOrNull()?.side == TradeSide.BUY) band.lower[last] else band.upper[last]
        return SignalRead(id, state, events, note, stop = stop)
    }

    /** A measurement: it reports, it never fires. See [MEASURES]. */
    private fun measure(id: String, name: String, series: CandleSeries): SignalRead {
        val line = when (id) {
            "atr" -> Indicators.atr(series.high, series.low, series.close)
            "adx" -> Indicators.adx(series.high, series.low, series.close).adx
            "choppiness" -> Indicators.choppiness(series.high, series.low, series.close)
            else -> null
        }
        val level = line?.get(series.size - 1)
        val note = if (level == null) SignalNote(NoteShape.QUIET) else SignalNote(NoteShape.READS, listOf(name, figure(level)))
        return SignalRead(id, MarketState.NEUTRAL, emptyList(), note)
    }

    // ── the pieces the rules share ───────────────────────────────────────────────────────────

    /**
     * Where this reading is wrong, in price.
     *
     * The swing the signal came out of, with a little air under it: the lowest low of the last
     * [STOP_LOOKBACK] bars for a buy, the highest high for a sell, pushed out by a fraction of the
     * average range so a stop is not sitting exactly on the wick that every other reader can see.
     *
     * A fixed percentage was the alternative and it is wrong for the reason the ATR preset gives:
     * one per cent is a rounding error on a small cap and a fortune on EURUSD. This is the
     * instrument's own recent behaviour and nothing else.
     */
    fun defaultStop(series: CandleSeries, event: SignalEvent, at: Int = series.size - 1): Double? {
        if (series.isEmpty) return null
        val from = maxOf(0, event.bar - STOP_LOOKBACK)
        val until = minOf(series.size, event.bar + 1)
        if (from >= until) return null
        var low = Double.MAX_VALUE
        var high = -Double.MAX_VALUE
        for (bar in from until until) {
            low = minOf(low, series.low[bar])
            high = maxOf(high, series.high[bar])
        }
        val range = Indicators.atr(series.high, series.low, series.close)[minOf(at, series.size - 1)] ?: 0.0
        val air = range * STOP_AIR
        return if (event.side == TradeSide.BUY) low - air else high + air
    }

    /** The study's first drawn line, whichever pane it is in. */
    private fun firstLineOf(
        option: IndicatorOption,
        series: CandleSeries,
        period: Int?,
        params: Map<String, Double>,
    ): Line? = if (option.pane == IndicatorPane.PRICE) {
        ChartCatalog.overlayFor(option, series, period, params = params).firstOrNull()?.values
    } else {
        ChartCatalog.paneFor(option, series, period, params = params)?.lines?.firstOrNull()?.values
    }

    /** Its second line where it has one — MACD's signal, Stochastic's %D. */
    private fun secondLineOf(
        option: IndicatorOption,
        series: CandleSeries,
        period: Int?,
        params: Map<String, Double>,
    ): Line? = if (option.pane == IndicatorPane.PRICE) {
        null
    } else {
        ChartCatalog.paneFor(option, series, period, params = params)?.lines?.getOrNull(1)?.values
    }

    /**
     * The single trailing level of a flipping study.
     *
     * SuperTrend draws two half-lines — one for each side — so that the flip is a visible break
     * rather than a diagonal; reading it needs them back as one series, which is what this does.
     */
    private fun trailingStopOf(
        option: IndicatorOption,
        series: CandleSeries,
        period: Int?,
        params: Map<String, Double>,
    ): Line? {
        val lines = ChartCatalog.overlayFor(option, series, period, params = params)
        if (lines.isEmpty()) return null
        if (lines.size == 1) return lines.first().values
        return Line.of(series.size) { bar ->
            lines.firstNotNullOfOrNull { it.values[bar] }
        }
    }

    /** How far past the line the cross went, as a fraction of the price, clamped to 0..1. */
    private fun crossStrength(close: Double, level: Double): Float {
        if (level == 0.0) return 1f
        val distance = abs(close - level) / abs(level)
        return (distance / CROSS_FULL).coerceIn(0.0, 1.0).toFloat()
    }

    /** How deep into the zone it had gone before it left, as a fraction of the zone's own width. */
    private fun zoneStrength(before: Double, floor: Double, ceiling: Double): Float {
        val width = ceiling - floor
        if (width <= 0.0) return 1f
        val depth = if (before <= floor) (floor - before) else (before - ceiling)
        return (depth / width).coerceIn(0.0, 1.0).toFloat()
    }

    private fun figure(value: Double): String =
        if (abs(value) >= 1000) formatPrice(value, 0) else formatFixed(value, if (abs(value) >= 10) 1 else 2)

    /**
     * The three words these sentences use that are not a study's name.
     *
     * They take the language because the sentence does. «قیمت از بالای EMA رد شد» and "price crossed
     * above EMA" are the same sentence with one word translated, and that word belongs beside the
     * shape it is substituted into rather than in a resource file this module cannot see.
     */
    internal fun priceWord(english: Boolean): String = if (english) "Price" else "قیمت"
    internal fun signalWord(english: Boolean): String = if (english) "its signal line" else "خط سیگنالش"
    internal fun channelWord(english: Boolean): String = if (english) "the channel" else "کانال"

    /** Below this there is not enough history for any of it to mean anything. */
    const val MIN_BARS = 20

    /** How far back a stop looks for the swing it sits behind. */
    const val STOP_LOOKBACK = 10

    /** And how much of an average range it stands clear of that swing by. */
    const val STOP_AIR = 0.25

    /** A cross this far past the line, as a fraction of price, is a strength of one. */
    private const val CROSS_FULL = 0.01
}

/**
 * The study's short name, for a sentence.
 *
 * The catalogue's own label is Persian prose — «شاخص قدرت نسبی» — which is right on a row in a list
 * and wrong in the middle of a sentence that already says what happened. `RSI`, `MACD`, `BB` is
 * what a reader of a chart calls these in *both* languages; nobody says «شاخص قدرت نسبی از ۳۰
 * برگشت». It is the help id where there is one and the wire id otherwise, both upper-cased, which
 * is the same string the legend already prints beside the line.
 *
 * `english` is taken and deliberately unused: the name is the same in both languages, and a caller
 * that has to remember which strings are translated and which are not is a caller that will get one
 * of them wrong.
 */
@Suppress("UNUSED_PARAMETER")
internal fun IndicatorOption.shortLabel(english: Boolean): String = (helpId ?: id).uppercase()
