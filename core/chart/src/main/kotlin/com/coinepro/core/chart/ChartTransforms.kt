package com.coinepro.core.chart

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * How a chart is drawn, when that changes the bars rather than only their shape.
 *
 * Half of these are ordinary — candles and bars plot the feed untouched, and line and area plot its
 * closes. The other half **rewrite the data**: a Renko brick appears when price moves far enough,
 * not when a minute passes, so a hundred candles can become nine bricks or four hundred. That is
 * the reason this is a transform rather than a drawing option.
 *
 * The rewritten types are also *time-free*. A Renko chart has no meaningful x axis, so the bars are
 * given synthetic ascending timestamps and the time axis stops being a clock. Every terminal does
 * this and none of them explain it; the alternative — collapsing hours into one x position and
 * leaving gaps elsewhere — is worse.
 */
enum class ChartType {
    /** The feed, untouched. */
    CANDLES,

    /** The feed, with a hollow body when the close is above the previous close. */
    HOLLOW,

    /** Averaged bars: smoother trend, and a close that is not a price anybody traded at. */
    HEIKIN_ASHI,

    /** Open-high-low-close ticks. The feed, untouched. */
    BARS,

    /** Closes only. */
    LINE,

    /** Closes, filled to the floor. */
    AREA,

    /** A brick per fixed price move. Time-free. */
    RENKO,

    /** A bar per fixed price range. Time-free. */
    RANGE,

    /** A line per break of the last N. Time-free. */
    LINE_BREAK,

    /** A continuous line that turns on a reversal. Time-free, and plotted as a line. */
    KAGI,

    /** Columns of boxes. Time-free, and plotted as a line. */
    POINT_AND_FIGURE,
    ;

    /** Whether this type draws bars at all, or a single line through the data. */
    val isLine: Boolean get() = this == LINE || this == AREA || this == KAGI || this == POINT_AND_FIGURE

    /** Whether the x axis is still a clock. False for everything price-driven. */
    val isTimeBased: Boolean
        get() = this != RENKO && this != RANGE && this != LINE_BREAK && this != KAGI &&
            this != POINT_AND_FIGURE
}

/** Tuning for the price-driven types. Null means "derive it from the data" — see [ChartTransforms]. */
data class ChartTypeConfig(
    /** Renko brick height, in price. */
    val brick: Double? = null,
    /** Range-bar height, in price. */
    val range: Double? = null,
    /** How many prior lines a Line Break must clear to reverse. Three is the convention. */
    val lineBreakCount: Int = 3,
    /** Kagi reversal, in price. */
    val reversal: Double? = null,
    /** Point-and-figure box size, in price. */
    val box: Double? = null,
    /** How many boxes reverse a P&F column. Three is the convention. */
    val boxReversal: Int = 3,
)

object ChartTransforms {

    /** Apply a chart type to a series. Types that do not rewrite the data return it unchanged. */
    fun apply(
        series: CandleSeries,
        type: ChartType,
        config: ChartTypeConfig = ChartTypeConfig(),
    ): CandleSeries = when (type) {
        ChartType.HEIKIN_ASHI -> CandleSeries(heikinAshi(series.bars))
        ChartType.RENKO -> CandleSeries(renko(series.bars, config.brick))
        ChartType.RANGE -> CandleSeries(rangeBars(series.bars, config.range))
        ChartType.LINE_BREAK -> CandleSeries(lineBreak(series.bars, config.lineBreakCount))
        ChartType.KAGI -> CandleSeries(kagi(series.bars, config.reversal))
        ChartType.POINT_AND_FIGURE ->
            CandleSeries(pointAndFigure(series.bars, config.box, config.boxReversal))
        else -> series
    }

    /**
     * The average true range of the opening bars, which is the default size for every
     * price-driven type.
     *
     * A brick size has to come from somewhere, and a fixed number cannot serve both gold at 2,600
     * and a memecoin at 0.000018. The average move is the only scale-free answer.
     */
    fun averageRange(bars: List<Candle>, period: Int = 14): Double {
        if (bars.isEmpty()) return 1.0
        var sum = 0.0
        var count = 0
        for (index in 1 until bars.size) {
            val bar = bars[index]
            val previousClose = bars[index - 1].c
            sum += maxOf(
                bar.h - bar.l,
                abs(bar.h - previousClose),
                abs(bar.l - previousClose),
            )
            count++
            if (count >= period) break
        }
        return if (count > 0) sum / count else bars[0].range.takeIf { it > 0 } ?: 1.0
    }

    /**
     * Averaged bars.
     *
     * The close is the bar's own average and the open is the previous *Heikin* bar's midpoint, so
     * the series smooths itself and a run of one colour reads as a trend. Worth being explicit about
     * the cost: **none of these four numbers is a price that traded.** An order placed at a Heikin
     * close is an order at a number the market never printed, which is why the price axis and the
     * crosshair keep showing the real series underneath.
     */
    fun heikinAshi(bars: List<Candle>): List<Candle> {
        if (bars.isEmpty()) return emptyList()
        val out = ArrayList<Candle>(bars.size)
        var previousOpen = (bars[0].o + bars[0].c) / 2
        var previousClose = (bars[0].o + bars[0].h + bars[0].l + bars[0].c) / 4
        for (index in bars.indices) {
            val source = bars[index]
            val close = (source.o + source.h + source.l + source.c) / 4
            val open = if (index == 0) (source.o + source.c) / 2 else (previousOpen + previousClose) / 2
            out += Candle(
                t = source.t,
                o = open,
                h = maxOf(source.h, open, close),
                l = minOf(source.l, open, close),
                c = close,
                v = source.v,
            )
            previousOpen = open
            previousClose = close
        }
        return out
    }

    /** A brick every time price closes a full [brick] away from the last one. */
    fun renko(bars: List<Candle>, brick: Double? = null): List<Candle> {
        if (bars.isEmpty()) return emptyList()
        val size = brick?.takeIf { it > 0 } ?: averageRange(bars)
        val out = ArrayList<Candle>()
        val clock = AscendingClock()
        var base = bars[0].c
        for (bar in bars) {
            while (bar.c >= base + size) {
                out += Candle(clock.next(bar.t), base, base + size, base, base + size)
                base += size
            }
            while (bar.c <= base - size) {
                out += Candle(clock.next(bar.t), base, base, base - size, base - size)
                base -= size
            }
        }
        return out
    }

    /**
     * A bar every time price covers [range], measured across the whole OHLC rather than closes.
     *
     * Walking all four prices of each source bar matters: a bar whose high and low span three ranges
     * has to emit three bars, and one that only looks at closes emits none of them.
     */
    fun rangeBars(bars: List<Candle>, range: Double? = null): List<Candle> {
        if (bars.isEmpty()) return emptyList()
        val size = range?.takeIf { it > 0 } ?: averageRange(bars)
        val out = ArrayList<Candle>()
        val clock = AscendingClock()
        var open = bars[0].o
        var high = bars[0].o
        var low = bars[0].o
        for (bar in bars) {
            for (price in doubleArrayOf(bar.o, bar.h, bar.l, bar.c)) {
                high = max(high, price)
                low = min(low, price)
                if (high - low >= size) {
                    out += Candle(clock.next(bar.t), open, high, low, price)
                    open = price
                    high = price
                    low = price
                }
            }
        }
        return out
    }

    /** A line per new high or low; a reversal needs to clear the last [count] lines. */
    fun lineBreak(bars: List<Candle>, count: Int = 3): List<Candle> {
        if (bars.isEmpty()) return emptyList()
        val out = ArrayList<Candle>()
        val clock = AscendingClock()
        val lines = ArrayList<Pair<Double, Double>>() // open to close
        val firstOpen = bars[0].o
        for (bar in bars) {
            val price = bar.c
            if (lines.isEmpty()) {
                if (abs(price - firstOpen) > EPSILON) {
                    lines += firstOpen to price
                    out += Candle(
                        clock.next(bar.t), firstOpen,
                        max(firstOpen, price), min(firstOpen, price), price,
                    )
                }
                continue
            }
            val (lastOpen, lastClose) = lines.last()
            val reference = lines.takeLast(count)
            val ceiling = reference.maxOf { max(it.first, it.second) }
            val floorPrice = reference.minOf { min(it.first, it.second) }
            val rising = lastClose >= lastOpen
            when {
                rising && price > lastClose -> {
                    lines += lastClose to price
                    out += Candle(clock.next(bar.t), lastClose, price, lastClose, price)
                }
                !rising && price < lastClose -> {
                    lines += lastClose to price
                    out += Candle(clock.next(bar.t), lastClose, lastClose, price, price)
                }
                rising && price < floorPrice -> {
                    lines += floorPrice to price
                    out += Candle(clock.next(bar.t), floorPrice, floorPrice, price, price)
                }
                !rising && price > ceiling -> {
                    lines += ceiling to price
                    out += Candle(clock.next(bar.t), ceiling, price, ceiling, price)
                }
            }
        }
        return out
    }

    /**
     * A continuous line that extends with the trend and turns on a [reversal].
     *
     * Kagi is a line, not bars, so each point is stored as a flat candle — all four prices equal.
     * That keeps one series type through the whole engine instead of a second one that exists for
     * two chart types.
     */
    fun kagi(bars: List<Candle>, reversal: Double? = null): List<Candle> {
        if (bars.isEmpty()) return emptyList()
        val size = reversal?.takeIf { it > 0 } ?: averageRange(bars)
        val clock = AscendingClock()
        val points = ArrayList<Candle>()
        points += flat(clock.next(bars[0].t), bars[0].c)
        var direction = 0
        var extreme = bars[0].c
        for (bar in bars) {
            val price = bar.c
            when {
                direction >= 0 && price > extreme -> {
                    extreme = price
                    points[points.lastIndex] = flat(clock.next(bar.t), price)
                    direction = 1
                }
                direction <= 0 && price < extreme -> {
                    extreme = price
                    points[points.lastIndex] = flat(clock.next(bar.t), price)
                    direction = -1
                }
                direction == 1 && price <= extreme - size -> {
                    points += flat(clock.next(bar.t), price)
                    extreme = price
                    direction = -1
                }
                direction == -1 && price >= extreme + size -> {
                    points += flat(clock.next(bar.t), price)
                    extreme = price
                    direction = 1
                }
            }
        }
        return points
    }

    /** Columns of boxes, plotted as a line through each column's extreme. */
    fun pointAndFigure(
        bars: List<Candle>,
        box: Double? = null,
        reversal: Int = 3,
    ): List<Candle> {
        if (bars.isEmpty()) return emptyList()
        val size = box?.takeIf { it > 0 } ?: averageRange(bars)
        val clock = AscendingClock()
        val points = ArrayList<Candle>()
        var direction = 0
        var extreme = bars[0].c
        for (bar in bars) {
            val price = bar.c
            when {
                direction >= 0 && price >= extreme + size -> {
                    extreme += floor((price - extreme) / size) * size
                    points += flat(clock.next(bar.t), extreme)
                    direction = 1
                }
                direction <= 0 && price <= extreme - size -> {
                    extreme -= floor((extreme - price) / size) * size
                    points += flat(clock.next(bar.t), extreme)
                    direction = -1
                }
                direction == 1 && price <= extreme - size * reversal -> {
                    extreme = price
                    points += flat(clock.next(bar.t), extreme)
                    direction = -1
                }
                direction == -1 && price >= extreme + size * reversal -> {
                    extreme = price
                    points += flat(clock.next(bar.t), extreme)
                    direction = 1
                }
            }
        }
        return points
    }

    private fun flat(time: Long, price: Double) = Candle(time, price, price, price, price)

    /**
     * Strictly ascending synthetic timestamps.
     *
     * A price-driven type can emit several bars from one source candle, all carrying that candle's
     * timestamp. [CandleSeries] requires ascending order and the renderer indexes by position, so
     * each one is nudged a second past the last. The numbers are not clock time and are not shown
     * as such — on these types the time axis is labelled by bar, not by date.
     */
    private class AscendingClock {
        private var last = 0L
        fun next(time: Long): Long {
            val value = max(time, last + 1)
            last = value
            return value
        }
    }

    /** Prices this close together are the same price, whatever floating point says. */
    private const val EPSILON = 1e-9
}
