package com.coinepro.core.chart

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * How a chart is drawn, when that changes the bars rather than only their shape.
 *
 * Most of these are ordinary — candles and bars plot the feed untouched, and line and area plot its
 * closes. Five of them **rewrite the data**: a Renko brick appears when price moves far enough,
 * not when a minute passes, so a hundred candles can become nine bricks or four hundred. That is
 * the reason this is a transform rather than a drawing option.
 *
 * The rewritten types are also *time-free*. A Renko chart has no meaningful x axis, so the bars are
 * given synthetic ascending timestamps and the time axis stops being a clock. Every terminal does
 * this and none of them explain it; the alternative — collapsing hours into one x position and
 * leaving gaps elsewhere — is worse.
 *
 * A third group keeps the feed's bars exactly as they are and derives *extra geometry* at drawing
 * time: how wide each candle should be, which price rows a bar's volume fills, which letters a
 * session's brackets print. Those come back from [ChartTransforms] as their own arrays and lists
 * rather than as a rewritten series, because a series cannot carry them — [Candle] has four prices
 * and a volume, and a footprint is a table per bar. [ChartTransforms.apply] returns them unchanged
 * for exactly that reason, which is not the same as them being ordinary.
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

    /** Closes against a level the reader sets, so the fill says which side of it price is on. */
    BASELINE,

    /** The high-low band as a filled area, with the close drawn through it. */
    HLC_AREA,

    /** The close held flat until the next bar, so a level reads as a level and not as a slope. */
    STEP_LINE,

    /** The close line with a dot on every bar, so a sparse series still shows where its bars are. */
    LINE_MARKERS,

    /** Candles as wide as their volume, so the bar that carried the move is the bar that is thick. */
    VOLUME_CANDLES,

    /** Each bar's volume split into price rows, so it shows *where inside the bar* it traded. */
    FOOTPRINT,

    /** A letter per price row per time bracket, so the session's shape is the shape of the profile. */
    TPO,
    ;

    /**
     * Whether this type draws bars at all, or a single line through the data.
     *
     * [HLC_AREA] is in here even though it also fills a band: the band is drawn from the same walk
     * of the same points, and what the renderer branches on is "is there a body per bar", which for
     * this type there is not.
     */
    val isLine: Boolean
        get() = when (this) {
            LINE, AREA, KAGI, POINT_AND_FIGURE, BASELINE, HLC_AREA, STEP_LINE, LINE_MARKERS -> true
            else -> false
        }

    /**
     * Whether the x axis is still a clock. False for everything price-driven.
     *
     * Written as the short list of exceptions rather than as a list of the fifteen that are ordinary,
     * because the five that are not are a closed set — they are the transforms that emit synthetic
     * timestamps — and a type added later is far more likely to belong to the majority. Anything new
     * that *does* rewrite time has to be added here, and its transform in [ChartTransforms.apply] is
     * the reminder.
     */
    val isTimeBased: Boolean
        get() = when (this) {
            RENKO, RANGE, LINE_BREAK, KAGI, POINT_AND_FIGURE -> false
            else -> true
        }
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
    /**
     * The level a [ChartType.BASELINE] chart splits its fill at, in price.
     *
     * Null means the window's opening close — [ChartTransforms.defaultBaseLevel] — which makes the
     * two fills read as "up on the period" and "down on the period". A number here pins it instead,
     * which is what a reader wants when the level is their entry rather than the window's start.
     */
    val baseLevel: Double? = null,
    /**
     * How many price rows one [ChartType.FOOTPRINT] bar is cut into.
     *
     * Null means [ChartTransforms.defaultRows] over the visible window. A fixed number here is what
     * a reader who is comparing two instruments wants; derived is what a reader scrolling one of
     * them wants, because a row has to stay tall enough to hold a number.
     */
    val footprintRows: Int? = null,
    /**
     * How many price rows a [ChartType.TPO] session is cut into.
     *
     * Separate from [footprintRows] and deliberately so: a footprint's rows divide one bar, a TPO's
     * divide a whole session, and the useful counts are an order of magnitude apart. Null derives
     * both from the same [ChartTransforms.defaultRows], over their own price span.
     */
    val tpoRows: Int? = null,
    /**
     * How long one TPO letter covers, in minutes.
     *
     * The classic profile uses thirty — the letter is the half hour. Null means "derive it", which
     * is the renderer dividing the session into a readable number of brackets from the timeframe it
     * is showing; [ChartTransforms.tpo] takes the answer in *bars*, since only the caller knows how
     * many minutes a bar is.
     */
    val tpoBracketMinutes: Int? = null,
)

/**
 * One price row of one footprint bar: the volume that traded inside [low]..[high], split by side.
 *
 * [buy] and [sell] are an attribution, not a measurement, and the KDoc on [ChartTransforms.footprint]
 * says exactly how far that attribution can be trusted. Both are kept even though one of them is
 * always zero, because the renderer draws two columns per row and a row that knows only its total
 * would have to ask the bar which column to put it in.
 */
data class FootprintRow(
    val low: Double,
    val high: Double,
    val buy: Double,
    val sell: Double,
) {
    /** What the row prints when the two columns are collapsed into one. */
    val total: Double get() = buy + sell
}

/**
 * One cell of a TPO profile: price row [rowIndex] was visited during time bracket [bracket].
 *
 * Only visits are emitted, so the count of entries at a row *is* the width of the profile there and
 * the renderer does not have to test anything — it draws one letter per entry. Both fields are
 * indices rather than a price and a time because that is what a profile is: the x axis of a TPO is
 * a count of brackets, not a clock, however the bars underneath it are spaced.
 */
data class TpoBracket(
    val rowIndex: Int,
    val bracket: Int,
)

object ChartTransforms {

    /**
     * Apply a chart type to a series. Types that do not rewrite the data return it unchanged.
     *
     * The volume-scaled, footprint and TPO types come back unchanged too, and that is not an
     * oversight: their extra geometry is not a series, so the renderer asks for it separately from
     * [volumeWidths], [footprint] and [tpo] once it knows the window it is drawing.
     */
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

    /**
     * The level a Baseline chart splits at when the reader has not set one: the window's first
     * close.
     *
     * That choice makes the green half mean "up on the period shown" and the red half "down on it",
     * so panning the chart moves the base and the fill keeps answering the same question. A fixed
     * default — zero, or the midpoint — answers a different question every time the window moves.
     */
    fun defaultBaseLevel(series: CandleSeries): Double =
        if (series.isEmpty) 0.0 else series.bars.first().c

    /**
     * How many price rows to cut a span into when the config leaves the count null.
     *
     * A row has to be tall enough to print a number inside and short enough to say something, and
     * neither is a fixed count: one bar of a quiet market and a whole session of a violent one are
     * the same picture at different scales. The unit here is the average bar's range — a row is an
     * eighth of it — so an ordinary bar comes out at eight rows and a session at whatever its own
     * travel earns, bounded so a flat market still gets rows and a crash does not get a thousand.
     */
    fun defaultRows(
        series: CandleSeries,
        fromIndex: Int = 0,
        toIndex: Int = series.size - 1,
    ): Int {
        if (series.isEmpty) return MIN_ROWS
        val from = fromIndex.coerceIn(0, series.size - 1)
        val to = toIndex.coerceIn(from, series.size - 1)
        var low = series.low[from]
        var high = series.high[from]
        for (index in from..to) {
            low = min(low, series.low[index])
            high = max(high, series.high[index])
        }
        val span = high - low
        val row = averageRange(series.bars) / ROWS_PER_AVERAGE_BAR
        if (span <= 0 || row <= 0) return MIN_ROWS
        return (span / row).roundToInt().coerceIn(MIN_ROWS, MAX_ROWS)
    }

    /**
     * Each bar's width as a fraction of the slot it is drawn in, from its share of the window's
     * heaviest volume.
     *
     * The floor at [MIN_BAR_WIDTH] is not cosmetic. A bar with a twentieth of the peak volume drawn
     * at a twentieth of the width is a hairline, and a hairline is indistinguishable from a gap — the
     * reader would conclude the market was closed rather than quiet. Clamping means the bottom fifth
     * of the range stops carrying information, which is the right trade: the top of the range is
     * where the reader is looking anyway.
     *
     * A feed that reports no volume gets all ones, so the chart is exactly the ordinary candle chart
     * rather than a chart of fabricated widths. That is the same guard the volume pane uses.
     */
    fun volumeWidths(series: CandleSeries): DoubleArray {
        val widths = DoubleArray(series.size) { 1.0 }
        if (!series.hasVolume) return widths
        val volumes = series.volume
        var peak = 0.0
        for (volume in volumes) peak = max(peak, volume)
        if (peak <= 0) return widths
        for (index in widths.indices) {
            widths[index] = (volumes[index] / peak).coerceIn(MIN_BAR_WIDTH, 1.0)
        }
        return widths
    }

    /**
     * One bar's volume, cut into [rows] equal price rows and attributed to a side.
     *
     * Be clear about what this can and cannot know. A real footprint comes from the tape — every
     * trade, with the side it hit — and neither feed sends one; what arrives is `{t,o,h,l,c,v}`. So
     * the bar's whole volume is attributed to the direction it closed, and spread evenly across its
     * range, because OHLC genuinely does not say where inside the bar it traded. The picture is
     * therefore a picture of the bar's *shape and weight*, not of order flow, and the axis label says
     * so.
     *
     * What it will not do is invent participants. A feed with no volume returns an empty list rather
     * than a grid of zeros — the same rule as the volume pane, which hides itself rather than drawing
     * a market where nobody traded.
     */
    fun footprint(series: CandleSeries, index: Int, rows: Int): List<FootprintRow> {
        if (index < 0 || index >= series.size) return emptyList()
        if (!series.hasVolume) return emptyList()
        val bar = series[index]
        val volume = bar.v ?: return emptyList()
        if (volume <= 0) return emptyList()
        val count = rows.coerceAtLeast(1)
        val buy = if (bar.up) volume else 0.0
        val sell = if (bar.up) 0.0 else volume
        if (bar.range <= 0) return listOf(FootprintRow(bar.l, bar.h, buy, sell))
        val height = bar.range / count
        val share = 1.0 / count
        return List(count) { row ->
            val low = bar.l + row * height
            FootprintRow(
                low = low,
                // The top row takes the bar's own high, so rounding cannot leave a hairline gap
                // between the last row and the wick it is supposed to reach.
                high = if (row == count - 1) bar.h else low + height,
                buy = buy * share,
                sell = sell * share,
            )
        }
    }

    /**
     * Which price rows the market visited in which time bracket, over `fromIndex..toIndex`.
     *
     * One entry per visited cell and none for the cells it skipped, so the number of entries on a
     * row *is* the width of the profile at that price and the renderer draws one letter per entry
     * without testing anything. The point of the chart is that this width, not the price, is what
     * says where the session agreed on value.
     *
     * [bracketBars] is in bars rather than in minutes on purpose: only the caller knows how long a
     * bar is, and a bracket expressed in minutes would have to be converted here by a file that has
     * no timeframe. `ChartTypeConfig.tpoBracketMinutes` holds the reader's answer; the renderer does
     * the division.
     */
    fun tpo(
        series: CandleSeries,
        fromIndex: Int,
        toIndex: Int,
        rows: Int,
        bracketBars: Int,
    ): List<TpoBracket> {
        if (series.isEmpty) return emptyList()
        val from = fromIndex.coerceIn(0, series.size - 1)
        val to = toIndex.coerceIn(from, series.size - 1)
        val count = rows.coerceAtLeast(1)
        val width = bracketBars.coerceAtLeast(1)
        var low = series.low[from]
        var high = series.high[from]
        for (index in from..to) {
            low = min(low, series.low[index])
            high = max(high, series.high[index])
        }
        val span = high - low
        val height = if (span > 0) span / count else 0.0
        val cells = LinkedHashSet<TpoBracket>()
        for (index in from..to) {
            val bracket = (index - from) / width
            val first = rowOf(series.low[index], low, height, count)
            val last = rowOf(series.high[index], low, height, count)
            for (row in first..last) cells += TpoBracket(row, bracket)
        }
        return cells.sortedWith(compareBy({ it.rowIndex }, { it.bracket }))
    }

    /**
     * The level the line is still sitting at when it arrives at each bar.
     *
     * A step line draws horizontally at `stepLine[index]` across bar `index`, then vertically to
     * `close[index]`. Keeping the held level in its own array rather than doubling the points means
     * the renderer's path walk, its crosshair and its hit-testing all still index by bar, which they
     * would not if the series had two entries per bar.
     *
     * The first bar holds its own close: there is no previous one, and starting from zero would draw
     * a vertical the height of the instrument's price.
     */
    fun stepLine(series: CandleSeries): DoubleArray {
        if (series.isEmpty) return DoubleArray(0)
        val close = series.close
        return DoubleArray(close.size) { index -> if (index == 0) close[0] else close[index - 1] }
    }

    /**
     * The closes split into the half above [base] and the half below it, each with `NaN` where the
     * other half owns the point.
     *
     * Two arrays of the full length, rather than two shorter ones, so both are still indexed by bar
     * — the renderer fills each with a single path and skips the `NaN`s, which is one walk per fill
     * instead of a segment list per crossing. No index ever holds a value in both: a point belongs
     * to exactly one side, and a bar sitting exactly on the base counts as above, the way a doji
     * counts as up.
     *
     * The two fills meet at the base line because the renderer clips them to it, not because this
     * function emits the crossing. Interpolating one here would put a price in the series that no
     * bar closed at, which is the thing every other transform in this file refuses to do.
     */
    fun baselineSplit(series: CandleSeries, base: Double): Pair<DoubleArray, DoubleArray> {
        val close = series.close
        val above = DoubleArray(close.size)
        val below = DoubleArray(close.size)
        for (index in close.indices) {
            val value = close[index]
            val isAbove = value >= base
            above[index] = if (isAbove) value else Double.NaN
            below[index] = if (isAbove) Double.NaN else value
        }
        return above to below
    }

    /** Which row a price falls in, with the very top of the span belonging to the top row. */
    private fun rowOf(price: Double, low: Double, height: Double, rows: Int): Int {
        if (height <= 0) return 0
        return floor((price - low) / height).toInt().coerceIn(0, rows - 1)
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

    /** The narrowest a volume-scaled candle may be drawn, as a fraction of its slot. */
    private const val MIN_BAR_WIDTH = 0.2

    /** How many rows an average bar's range is worth, when a row count has to be derived. */
    private const val ROWS_PER_AVERAGE_BAR = 8

    /** Fewest rows worth drawing: below this the profile stops being a profile. */
    private const val MIN_ROWS = 4

    /** Most rows worth drawing: past this a row is thinner than the letter that goes in it. */
    private const val MAX_ROWS = 64
}
