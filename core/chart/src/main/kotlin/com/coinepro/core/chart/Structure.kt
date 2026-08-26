package com.coinepro.core.chart

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The seven structure studies: pivots, swing points, fractals, zigzag, auto-Fibonacci, support and
 * resistance, and supply/demand zones.
 *
 * They are separated from [Indicators] and [IndicatorsExt] because they do not produce a value per
 * bar. A moving average answers "what is the average here"; these answer "where are the levels" and
 * "which bars matter", and the difference is not cosmetic — it is why the chart needed two new
 * drawing shapes ([PriceLevel] and [ChartMarker]) before any of this could be shown.
 *
 * Ported from `indicators_ext_b.js`, formula for formula, for the same reason as everything else in
 * this module: a reader who has a support level at 2,614 in the web terminal must get 2,614 here.
 */
object Structure {

    // ══════════════════════════════════════════════════════════ pivots

    /**
     * The five pivot conventions, which genuinely disagree about where support is.
     *
     * Offering one would be simpler and would be taking a side in an argument traders have with
     * each other. A Camarilla R1 and a Classic R1 are different prices from the same bar, and a
     * reader who trades one and is shown the other is being told something false.
     */
    enum class PivotType(val label: String) {
        CLASSIC("کلاسیک"),
        FIBONACCI("فیبوناچی"),
        CAMARILLA("کاماریلا"),
        WOODIE("وودی"),
        DEMARK("دی‌مارک"),
    }

    /** One bar's pivot set. Nulls are real: DeMark defines only a pivot and one level each side. */
    data class PivotLevels(
        val pivot: Double,
        val r1: Double?,
        val r2: Double?,
        val r3: Double?,
        val s1: Double?,
        val s2: Double?,
        val s3: Double?,
    )

    /**
     * Pivot levels for one bar, from the *previous* bar's range.
     *
     * From the previous bar and not this one, which is the whole point of a pivot: it is a level
     * known before the session opens, so it can be traded. Computing it from the bar it is drawn on
     * would make it a description of what already happened.
     */
    fun pivotLevels(
        high: Double,
        low: Double,
        close: Double,
        previousClose: Double?,
        type: PivotType,
    ): PivotLevels {
        val range = high - low
        return when (type) {
            PivotType.FIBONACCI -> {
                val pivot = (high + low + close) / 3
                PivotLevels(
                    pivot = pivot,
                    r1 = pivot + 0.382 * range,
                    r2 = pivot + 0.618 * range,
                    r3 = pivot + range,
                    s1 = pivot - 0.382 * range,
                    s2 = pivot - 0.618 * range,
                    s3 = pivot - range,
                )
            }
            PivotType.CAMARILLA -> PivotLevels(
                pivot = (high + low + close) / 3,
                r1 = close + 1.1 * range / 12,
                r2 = close + 1.1 * range / 6,
                r3 = close + 1.1 * range / 4,
                s1 = close - 1.1 * range / 12,
                s2 = close - 1.1 * range / 6,
                s3 = close - 1.1 * range / 4,
            )
            PivotType.WOODIE -> {
                val pivot = (high + low + 2 * close) / 4
                PivotLevels(
                    pivot = pivot,
                    r1 = 2 * pivot - low,
                    r2 = pivot + range,
                    r3 = high + 2 * (pivot - low),
                    s1 = 2 * pivot - high,
                    s2 = pivot - range,
                    s3 = low - 2 * (high - pivot),
                )
            }
            PivotType.DEMARK -> {
                // DeMark branches on where the bar closed. The web terminal approximates the open
                // with the previous close, and that approximation is carried across deliberately:
                // matching it matters more than being independently more correct, because the two
                // products would otherwise disagree about a level a reader is trading.
                val reference = previousClose ?: close
                val x = when {
                    close < reference -> high + 2 * low + close
                    close > reference -> 2 * high + low + close
                    else -> high + low + 2 * close
                }
                PivotLevels(
                    pivot = x / 4,
                    r1 = x / 2 - low,
                    r2 = null,
                    r3 = null,
                    s1 = x / 2 - high,
                    s2 = null,
                    s3 = null,
                )
            }
            PivotType.CLASSIC -> {
                val pivot = (high + low + close) / 3
                PivotLevels(
                    pivot = pivot,
                    r1 = 2 * pivot - low,
                    r2 = pivot + range,
                    r3 = high + 2 * (pivot - low),
                    s1 = 2 * pivot - high,
                    s2 = pivot - range,
                    s3 = low - 2 * (high - pivot),
                )
            }
        }
    }


    /**
     * Which bucket a pivot is computed from.
     *
     * This is not a preference and getting it wrong is not subtle. A pivot is a level derived from
     * the **previous session**, which is what makes it a level: everybody computes the same number
     * from the same closed day, so it is where orders sit. Recomputing it from the previous *bar*
     * on an hourly chart produces a new pivot every hour — a jagged line that spans the whole
     * range, describes nothing, and is not what any other terminal draws. The web original does
     * exactly that, and a screenshot of the port is what made it visible.
     *
     * [BAR] keeps the original behaviour, and exists so the parity fixture still checks the
     * formula against the JavaScript. It is not offered to readers.
     */
    enum class PivotSession(val seconds: Long, val label: String) {
        DAILY(86_400, "روزانه"),
        WEEKLY(604_800, "هفتگی"),
        BAR(0, "هر میله"),
    }

    /**
     * Epoch was a Thursday, so a naive `time / 604800` starts its weeks on one.
     *
     * Four days of offset puts the boundary on Monday, which is where a trading week starts.
     */
    private const val MONDAY_OFFSET = 4 * 86_400L

    /** The seven pivot lines, flat across each session and stepping at its boundary. */
    fun pivots(
        series: CandleSeries,
        type: PivotType = PivotType.CLASSIC,
        session: PivotSession = PivotSession.DAILY,
    ): List<ChartLine> {
        if (series.size < 2) return emptyList()
        val levels = sessionLevels(series, type, session)
        fun line(pick: (PivotLevels) -> Double?): Line =
            Line.of(series.size) { index -> levels[index]?.let(pick) }
        return listOf(
            ChartLine(line { it.r3 }, 0xFFDC2626, label = "R3", dashed = true),
            ChartLine(line { it.r2 }, 0xFFF6465D, label = "R2", dashed = true),
            ChartLine(line { it.r1 }, 0xFFF87171, label = "R1", dashed = true),
            ChartLine(line { it.pivot }, 0xFF94A3B8, label = "P"),
            ChartLine(line { it.s1 }, 0xFF4ADE80, label = "S1", dashed = true),
            ChartLine(line { it.s2 }, 0xFF00B15C, label = "S2", dashed = true),
            ChartLine(line { it.s3 }, 0xFF00B15C, label = "S3", dashed = true),
        )
    }

    /**
     * Each bar's pivot set, from the last *completed* bucket before it.
     *
     * Null for every bar in the first bucket: there is no completed session before it, and a pivot
     * invented for the opening day would be a level nobody could have traded.
     */
    private fun sessionLevels(
        series: CandleSeries,
        type: PivotType,
        session: PivotSession,
    ): Array<PivotLevels?> {
        val out = arrayOfNulls<PivotLevels>(series.size)
        if (session == PivotSession.BAR) {
            for (index in 1 until series.size) {
                out[index] = pivotLevels(
                    high = series.high[index - 1],
                    low = series.low[index - 1],
                    close = series.close[index - 1],
                    previousClose = if (index > 1) series.close[index - 2] else null,
                    type = type,
                )
            }
            return out
        }
        val bucketOf = { time: Long ->
            if (session == PivotSession.WEEKLY) {
                (time + MONDAY_OFFSET) / session.seconds
            } else {
                time / session.seconds
            }
        }
        // One pass: close each bucket as the next one opens, and hand its levels to every bar of
        // that next bucket. Nothing here looks forward, which is the property that matters.
        var currentBucket = bucketOf(series.time[0])
        var high = series.high[0]
        var low = series.low[0]
        var close = series.close[0]
        var previous: PivotLevels? = null
        var previousClose: Double? = null
        for (index in 0 until series.size) {
            val bucket = bucketOf(series.time[index])
            if (bucket != currentBucket) {
                val closed = pivotLevels(high, low, close, previousClose, type)
                previousClose = close
                previous = closed
                currentBucket = bucket
                high = series.high[index]
                low = series.low[index]
            } else if (index > 0) {
                high = max(high, series.high[index])
                low = min(low, series.low[index])
            }
            close = series.close[index]
            out[index] = previous
        }
        return out
    }

    // ══════════════════════════════════════════════════════════ swings

    /**
     * Swing highs and lows: a bar whose high beats [left] bars behind and [right] ahead.
     *
     * The [right] bars are why these appear late. A swing high is not knowable until the bars after
     * it have printed, and a study that marked it on the bar itself would be drawing the future.
     */
    fun swings(series: CandleSeries, left: Int = 5, right: Int = 5): List<ChartMarker> {
        val markers = mutableListOf<ChartMarker>()
        for (index in left until series.size - right) {
            var isHigh = true
            var isLow = true
            for (step in 1..left) {
                if (series.high[index - step] >= series.high[index]) isHigh = false
                if (series.low[index - step] <= series.low[index]) isLow = false
            }
            for (step in 1..right) {
                if (series.high[index + step] >= series.high[index]) isHigh = false
                if (series.low[index + step] <= series.low[index]) isLow = false
            }
            if (isHigh) {
                markers += ChartMarker(series.time[index], series.high[index], true, SELL, MarkerGlyph.ARROW_DOWN, "H")
            }
            if (isLow) {
                markers += ChartMarker(series.time[index], series.low[index], false, BUY, MarkerGlyph.ARROW_UP, "L")
            }
        }
        return markers
    }

    /**
     * Williams fractals — the same test at a fixed two bars each side.
     *
     * Offered separately from [swings] rather than as a preset, because a fractal is a named thing
     * in the literature a reader may be following, and finding it under "swing points, left 2,
     * right 2" is finding it only if you already knew.
     */
    fun fractals(series: CandleSeries, span: Int = 2): List<ChartMarker> {
        val markers = mutableListOf<ChartMarker>()
        for (index in span until series.size - span) {
            var up = true
            var down = true
            for (step in 1..span) {
                if (series.high[index - step] >= series.high[index] ||
                    series.high[index + step] >= series.high[index]
                ) {
                    up = false
                }
                if (series.low[index - step] <= series.low[index] ||
                    series.low[index + step] <= series.low[index]
                ) {
                    down = false
                }
            }
            if (up) {
                markers += ChartMarker(series.time[index], series.high[index], true, 0xFFF59E0B, MarkerGlyph.ARROW_DOWN, "▲")
            }
            if (down) {
                markers += ChartMarker(series.time[index], series.low[index], false, 0xFFF0B90B, MarkerGlyph.ARROW_UP, "▼")
            }
        }
        return markers
    }

    // ══════════════════════════════════════════════════════════ zigzag

    /** One confirmed turn: which bar, at what price, and whether it is a peak. */
    data class Swing(val index: Int, val price: Double, val isPeak: Boolean)

    /**
     * The turning points, filtered by a percentage retracement.
     *
     * [deviationPercent] is the whole study: at 1 % it draws every wobble and at 20 % it draws the
     * two moves of the year. Neither is wrong and there is no good default, which is why it is a
     * parameter rather than a constant.
     *
     * The last swing is included although it is unconfirmed — price may still extend it. The web
     * terminal does the same, and it is the right call: a zigzag that stops several days back looks
     * broken, and the reader can see that the final leg is still forming.
     */
    fun zigzagSwings(series: CandleSeries, deviationPercent: Double): List<Swing> {
        if (series.size < 2) return emptyList()
        val swings = mutableListOf<Swing>()
        val threshold = deviationPercent / 100
        var lastIndex = 0
        var lastHigh = series.high[0]
        var lastLow = series.low[0]
        var direction = 0
        for (index in 1 until series.size) {
            // Both branches can run on the same bar while direction is still zero — the very first
            // move has not picked a side yet. Reproduced rather than tidied: it is what decides
            // which bar becomes the first swing, and the two products must agree on that.
            if (direction >= 0) {
                if (series.high[index] > lastHigh) {
                    lastHigh = series.high[index]
                    lastIndex = index
                }
                if (series.low[index] < lastHigh * (1 - threshold)) {
                    swings += Swing(lastIndex, lastHigh, isPeak = true)
                    direction = -1
                    lastLow = series.low[index]
                    lastIndex = index
                }
            }
            if (direction <= 0) {
                if (series.low[index] < lastLow) {
                    lastLow = series.low[index]
                    lastIndex = index
                }
                if (series.high[index] > lastLow * (1 + threshold)) {
                    swings += Swing(lastIndex, lastLow, isPeak = false)
                    direction = 1
                    lastHigh = series.high[index]
                    lastIndex = index
                }
            }
        }
        swings += Swing(lastIndex, if (direction >= 0) lastHigh else lastLow, isPeak = direction >= 0)
        return swings
    }

    /** The zigzag as a line that skips every bar between turns, plus a dot at each turn. */
    fun zigzag(
        series: CandleSeries,
        deviationPercent: Double = 5.0,
        colour: Long = 0xFFF59E0B,
    ): Pair<ChartLine, List<ChartMarker>> {
        val swings = zigzagSwings(series, deviationPercent)
        val byIndex = swings.associateBy { it.index }
        val line = ChartLine(
            values = Line.of(series.size) { byIndex[it]?.price },
            colour = colour,
            label = "ZigZag",
            // Without this the line is a row of disconnected dots: every bar between two turns is
            // deliberately absent, and the whole shape is the join across them.
            connectNulls = true,
        )
        val markers = swings.map { swing ->
            ChartMarker(
                time = series.time[swing.index],
                price = swing.price,
                above = swing.isPeak,
                colour = if (swing.isPeak) SELL else BUY,
                glyph = MarkerGlyph.CIRCLE,
                text = null,
            )
        }
        return line to markers
    }

    // ══════════════════════════════════════════════════════════ levels

    /**
     * Fibonacci levels across the most recent zigzag leg, placed without anybody dragging anything.
     *
     * The same ratios the drawing tool offers, on the leg the market just made. It is not a
     * substitute for placing one by hand — a trader who has picked a different leg has picked it
     * for a reason — but it is the answer to "what would the obvious retracement be here".
     */
    fun autoFibonacci(series: CandleSeries, deviationPercent: Double = 5.0): List<PriceLevel> {
        val swings = zigzagSwings(series, deviationPercent)
        if (swings.size < 2) return emptyList()
        val last = swings[swings.size - 1]
        val previous = swings[swings.size - 2]
        val low = min(previous.price, last.price)
        val high = max(previous.price, last.price)
        val span = high - low
        val rising = last.price >= previous.price
        return AUTO_FIB_RATIOS.mapIndexed { position, ratio ->
            PriceLevel(
                price = if (rising) high - span * ratio else low + span * ratio,
                colour = AUTO_FIB_COLOURS[position],
                label = "${formatPrice(ratio * 100, 1)}٪",
            )
        }
    }

    /**
     * Support and resistance, by clustering swing prices that fall within a tolerance of each other.
     *
     * The count is the point. A level price touched four times is a different claim from one touched
     * twice, and the label says which — `S/R ×4` — rather than drawing both the same and leaving the
     * reader to guess why one is there.
     *
     * Clusters of one are dropped. A single swing high is not a level; it is a bar.
     */
    fun supportResistance(
        series: CandleSeries,
        lookback: Int = 15,
        tolerancePercent: Double = 0.1,
    ): List<PriceLevel> {
        val tolerance = tolerancePercent / 100
        val prices = mutableListOf<Double>()
        for (index in lookback until series.size - lookback) {
            var isHigh = true
            var isLow = true
            for (step in 1..lookback) {
                if (series.high[index - step] >= series.high[index] ||
                    series.high[index + step] >= series.high[index]
                ) {
                    isHigh = false
                }
                if (series.low[index - step] <= series.low[index] ||
                    series.low[index + step] <= series.low[index]
                ) {
                    isLow = false
                }
            }
            if (isHigh) prices += series.high[index]
            if (isLow) prices += series.low[index]
        }
        prices.sort()
        val clusters = mutableListOf<Cluster>()
        for (price in prices) {
            val last = clusters.lastOrNull()
            if (last != null && abs(price - last.mean) <= last.mean * tolerance) {
                last.total += price
                last.count++
            } else {
                clusters += Cluster(price, 1)
            }
        }
        return clusters.filter { it.count >= 2 }.map { cluster ->
            PriceLevel(
                price = cluster.mean,
                colour = when {
                    cluster.count >= 4 -> 0xFFF59E0B
                    cluster.count >= 3 -> 0xFFFBBF24
                    else -> 0xFF94A3B8
                },
                label = "S/R ×${cluster.count}",
            )
        }
    }

    private class Cluster(var total: Double, var count: Int) {
        val mean: Double get() = total / count
    }

    /**
     * Supply and demand zones: the bar a violent move left from.
     *
     * A move of [impulse] average true ranges in one bar is taken as an impulse, and the bar
     * *before* it is the zone — that is where the orders that caused the move were sitting. The
     * zone is the base bar's high and low, so it is a band rather than a line.
     *
     * Only the most recent [maxZones] survive. An old zone that price has driven through twice is
     * not a level any more, and a chart carrying every zone since 2024 is a chart of stripes.
     */
    fun supplyDemand(
        series: CandleSeries,
        impulse: Double = 2.0,
        atrLength: Int = 14,
        maxZones: Int = 5,
    ): List<PriceLevel> {
        if (series.size < 3) return emptyList()
        val atr = Indicators.atr(series.high, series.low, series.close, atrLength)
        val zones = mutableListOf<PriceLevel>()
        for (index in 1 until series.size - 1) {
            val average = atr[index] ?: continue
            val move = abs(series.close[index + 1] - series.open[index + 1])
            if (move < impulse * average) continue
            val rising = series.close[index + 1] >= series.open[index + 1]
            val colour = if (rising) BUY else SELL
            val label = if (rising) "تقاضا" else "عرضه"
            zones += PriceLevel(series.high[index], colour, label)
            // The band's other edge. Unlabelled on purpose: two labels on one zone is one too many.
            zones += PriceLevel(series.low[index], colour, null)
        }
        return zones.takeLast(max(2, maxZones * 2))
    }

    private const val BUY = 0xFF00B15C
    private const val SELL = 0xFFF6465D

    private val AUTO_FIB_RATIOS = listOf(0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0)
    private val AUTO_FIB_COLOURS = listOf(
        0xFF94A3B8, 0xFFF87171, 0xFFFB923C, 0xFFFACC15, 0xFF4ADE80, 0xFF22D3EE, 0xFF94A3B8,
    )
}
