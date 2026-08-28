package com.coinepro.core.chart

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Which bars are on screen, and where each price and time lands in pixels.
 *
 * This is the only place that converts between chart space and screen space, and everything else —
 * the renderer, the crosshair, the future drawing tools, the signal overlay — is written against
 * these five functions and nothing else. That is the arrangement the web app's fifty-two drawing
 * tools were built on, and it is why a tool stores its points as `(time, price)` and gets pan and
 * zoom for free: nothing in a tool knows what a pixel is.
 *
 * Immutable. Pan and zoom return a new viewport rather than mutating this one, so a frame renders
 * from a single consistent state and a gesture cannot change the geometry halfway down a draw.
 */
data class ChartViewport(
    /** The series being plotted — already transformed if the chart type rewrites bars. */
    val series: CandleSeries,
    /** How many bars fill the plot. This is the zoom level. */
    val barsPerView: Int = DEFAULT_BARS_PER_VIEW,
    /**
     * How many bars back from the newest the right edge sits. This is the pan position.
     *
     * Zero means pinned to the live edge, which is where a chart opens and where it should stay
     * while new bars arrive — a chart that drifts left as the market prints is a chart the reader
     * has to keep dragging back.
     */
    val offset: Int = 0,
    /** The plot rectangle in pixels: the canvas minus the price axis and the time axis. */
    val plotWidth: Float = 0f,
    val plotHeight: Float = 0f,
    /**
     * Prices that must stay on screen even though no bar reaches them.
     *
     * A trade setup is the case: its target sits above everything that has happened yet — that is
     * what makes it a target — so a range taken from the bars alone puts it off the top of the
     * canvas. The reader then sees a green band running off the edge and no line, which is the
     * least useful possible drawing of "here is where you take profit".
     */
    val includedPrices: List<Double> = emptyList(),
    /**
     * Whether the price axis is logarithmic.
     *
     * ### Why a trading chart needs this and a general-purpose chart does not
     *
     * On a linear axis, equal *distances* mean equal amounts of money. On a log axis they mean
     * equal *percentages*, which is what a trader actually reads: a move from 100 to 110 and one
     * from 1,000 to 1,100 are the same trade and the linear axis draws the second one ten times
     * larger.
     *
     * It matters most exactly where this app is weakest without it. Bitcoin over two years spans
     * more than one order of magnitude; on a linear axis the whole of the first year is a flat
     * line pressed against the bottom of the plot, and every structure in it — every level, every
     * trend line a reader might want to draw — is invisible. That is not a preference, it is a
     * chart that cannot answer the question it was opened for.
     *
     * ### Off by default
     *
     * Because on a single day of a single instrument the two are indistinguishable, and the axis a
     * reader has not asked about should be the one they expect. It earns its place on the long
     * views, and it is one tap away.
     *
     * ### What it does not touch
     *
     * Time. A log *time* axis is meaningless. And nothing in chart space: a drawing is still
     * `(time, price)`, so a trend line drawn on the linear axis is the same two prices on the log
     * one — it simply stops being straight, which is correct and is what every terminal does.
     */
    val logScale: Boolean = false,
    /**
     * How much of the price axis is shown, relative to what the visible bars need.
     *
     * One is the default and means "fit the bars, with headroom" — the behaviour the chart has
     * always had. Above one the range widens and the candles flatten; below one they stretch and
     * the extremes run off the top and bottom of the plot.
     *
     * ### Why a chart needs this
     *
     * The auto-fit range is right for reading price and wrong for reading *shape*. A market that
     * has moved half a percent all week fills the plot with a mountain range of noise; one that
     * gapped ten percent on Monday spends the rest of the week as a flat line in the bottom third.
     * Every terminal answers both with the same gesture — drag the price gutter — and this is the
     * number that gesture moves.
     *
     * ### The bounds
     *
     * A quarter to eight. Below a quarter the visible bars are four times the height of the plot
     * and the reader is looking at a vertical line; above eight the candles are a horizontal one.
     * Both are states somebody can reach by accident and neither is a chart.
     */
    val priceZoom: Float = 1f,
) {
    /** Index of the first visible bar, inclusive. */
    val firstVisible: Int
        get() = max(0, lastVisible + 1 - visibleCount)

    /** Index of the last visible bar, inclusive. −1 when there is nothing to show. */
    val lastVisible: Int
        get() = if (series.isEmpty) -1 else (series.size - 1 - offset).coerceIn(0, series.size - 1)

    /** How many bars are actually drawn, which is fewer than [barsPerView] on a short series. */
    val visibleCount: Int
        get() = if (series.isEmpty) 0 else min(effectiveBarsPerView, lastVisible + 1)

    private val effectiveBarsPerView: Int
        get() = barsPerView.coerceIn(MIN_BARS_PER_VIEW, MAX_BARS_PER_VIEW)

    /** Whether the right edge is at the newest bar, and so should follow new data. */
    val isAtLiveEdge: Boolean get() = offset == 0

    /**
     * Pixels per bar.
     *
     * A series shorter than the window fills the plot rather than sitting in the left of it. That
     * matters most for the price-driven types, which are *inherently* short — a hundred candles
     * become nineteen Renko bricks, and dividing by the window put those nineteen in the leftmost
     * sixth of the canvas with five colliding axis labels underneath.
     *
     * The floor stops the opposite problem: three bars filling a phone screen are three coloured
     * slabs, not a chart. Below [MIN_BARS_PER_VIEW] the width stops growing and the bars simply do
     * not reach the right edge.
     */
    val barWidth: Float
        get() {
            val slots = min(effectiveBarsPerView, max(visibleCount, MIN_BARS_PER_VIEW))
            return if (slots == 0) 0f else plotWidth / slots
        }

    /** The candle body width — the rest of the slot is the gap between bars. */
    val bodyWidth: Float get() = max(1f, barWidth * BODY_RATIO)

    /**
     * The price range on screen, with headroom.
     *
     * The padding is eight percent of the visible range, which keeps the highest wick off the top
     * edge. Two fallbacks matter: a perfectly flat series has no range to take a percentage of, and
     * an empty one has no prices at all. Both would otherwise collapse the axis to a single value
     * and divide by zero.
     */
    val priceRange: ClosedFloatingPointRange<Double> by lazy {
        if (series.isEmpty || visibleCount == 0) return@lazy 0.0..1.0
        var low = Double.MAX_VALUE
        var high = -Double.MAX_VALUE
        for (index in firstVisible..lastVisible) {
            if (series.low[index] < low) low = series.low[index]
            if (series.high[index] > high) high = series.high[index]
        }
        for (price in includedPrices) {
            if (price < low) low = price
            if (price > high) high = price
        }
        // The headroom has to be taken in the same space the axis is drawn in.
        //
        // Additive padding on a log axis is wrong twice over. On a range like 100–10,000 eight
        // percent of the span is 792, so the bottom of the axis becomes −692 — a price with no
        // logarithm, which sent the whole axis back to the linear fallback and made the log toggle
        // do nothing at all. And even where it stayed positive it would be invisible at the top
        // and enormous at the bottom, because a fixed amount of money is a different percentage at
        // each end. Multiplicative padding is the same eight percent of the *visible height* at
        // both ends, which is what the linear branch means by it too.
        val zoom = priceZoom.coerceIn(MIN_PRICE_ZOOM, MAX_PRICE_ZOOM).toDouble()
        if (logScale && low > 0.0 && high > low) {
            // Widened about the geometric middle rather than the arithmetic one: on a log axis
            // that is the point that stays still, and expanding about the wrong centre would slide
            // the whole chart up the plot as the reader dragged.
            val middle = exp((ln(low) + ln(high)) / 2)
            val half = ln(high / low) / 2 * (1 + PRICE_PADDING * 2) * zoom
            return@lazy (middle / exp(half))..(middle * exp(half))
        }
        val padding = when {
            high > low -> (high - low) * PRICE_PADDING
            high != 0.0 -> abs(high) * 0.02
            else -> 1.0
        }
        val middle = (low + high) / 2
        val half = (high - low) / 2 + padding
        (middle - half * zoom)..(middle + half * zoom)
    }

    // ---------------------------------------------------------------- chart space to screen

    /** Screen x of the bar at [index], at the centre of its slot. */
    fun xOf(index: Int): Float = (index - firstVisible) * barWidth + barWidth / 2

    /**
     * Screen y of a price.
     *
     * On a log axis the position is taken in log space rather than the value — which is the whole
     * of what "logarithmic" means here. The guard is not a formality: a price at or below zero has
     * no logarithm, and while a *price* cannot be negative, [includedPrices] can carry a target a
     * reader dragged below the axis and an indicator pane reuses this function for values that
     * routinely are (MACD, a rate of change). Anything the log axis cannot place falls back to the
     * linear placement rather than to `NaN`, which would silently stop drawing that line.
     */
    fun yOf(price: Double): Float {
        val low = priceRange.start
        val high = priceRange.endInclusive
        if (logScale && low > 0.0 && high > low && price > 0.0) {
            val logLow = ln(low)
            val logSpan = ln(high) - logLow
            if (logSpan > 0.0) {
                return (plotHeight - (ln(price) - logLow) / logSpan * plotHeight).toFloat()
            }
        }
        val span = high - low
        if (span <= 0.0) return plotHeight / 2
        return (plotHeight - (price - low) / span * plotHeight).toFloat()
    }

    /**
     * Screen x of a moment.
     *
     * Interpolates between bars rather than snapping to one, so a drawing anchored partway through
     * a bar stays where it was put. A time before or after the loaded range extrapolates at the
     * current bar spacing — which is what lets a trend line drawn last week still reach the right
     * edge today.
     */
    fun xOfTime(time: Long): Float {
        if (series.isEmpty) return 0f
        val index = indexOfTime(time)
        return xOf(index.toInt()) + ((index - index.toInt()) * barWidth).toFloat()
    }

    // ---------------------------------------------------------------- screen space to chart

    /** The bar under a screen x, clamped to the series. */
    fun indexAt(x: Float): Int {
        if (series.isEmpty || barWidth <= 0f) return 0
        return (firstVisible + (x / barWidth).toInt()).coerceIn(firstVisible, lastVisible)
    }

    /**
     * The price at a screen y. Not clamped: dragging above the plot means a higher price.
     *
     * The exact inverse of [yOf], including its log branch and its fallback — they have to agree
     * or a drawing placed by a finger lands somewhere else. That is the failure this pairing
     * exists to prevent, and `ChartViewportTest` holds them against each other.
     */
    fun priceAt(y: Float): Double {
        val low = priceRange.start
        val high = priceRange.endInclusive
        if (plotHeight <= 0f) return low
        if (logScale && low > 0.0 && high > low) {
            val logLow = ln(low)
            val logSpan = ln(high) - logLow
            if (logSpan > 0.0) {
                return exp(logLow + (plotHeight - y) / plotHeight * logSpan)
            }
        }
        return low + (plotHeight - y) / plotHeight * (high - low)
    }

    /** The moment at a screen x, interpolated between bars the same way [xOfTime] is. */
    fun timeAt(x: Float): Long {
        if (series.isEmpty) return 0
        val index = indexAt(x)
        return series.time[index]
    }

    // ---------------------------------------------------------------- gestures

    /**
     * Drag by [pixels], positive meaning the content moves right — which shows older bars.
     *
     * Panning is quantised to whole bars. Sub-bar panning would be smoother and is wrong here: the
     * bars would shimmer against a grid that cannot move with them, and every drawing tool anchored
     * to a bar index would sit fractionally off.
     */
    fun pannedBy(pixels: Float): ChartViewport {
        if (barWidth <= 0f) return this
        val bars = (pixels / barWidth).roundToInt()
        return atOffset(offset + bars)
    }

    fun atOffset(newOffset: Int): ChartViewport {
        val maximum = max(0, series.size - MIN_BARS_PER_VIEW)
        return copy(offset = newOffset.coerceIn(0, maximum))
    }

    /**
     * Zoom by a scale factor, keeping the right edge fixed.
     *
     * The right edge rather than the pinch centre, because on a price chart the right edge is the
     * present and it is what a reader is looking at. Zooming around the finger is correct on a map
     * and disorienting here — the live price would slide off screen while you were trying to look
     * at it more closely.
     */
    fun zoomedBy(scale: Float): ChartViewport {
        if (scale <= 0f) return this
        val bars = (effectiveBarsPerView / scale).roundToInt()
        return copy(barsPerView = bars.coerceIn(MIN_BARS_PER_VIEW, MAX_BARS_PER_VIEW))
    }

    /**
     * Stretch or compress the price axis, as a factor on the current setting.
     *
     * Multiplicative rather than additive, because the gesture that drives it is a drag on the
     * price gutter and a drag has to feel the same at every scale: adding a fixed amount would be
     * imperceptible when zoomed out and violent when zoomed in.
     */
    fun priceZoomedBy(factor: Float): ChartViewport {
        if (factor <= 0f || !factor.isFinite()) return this
        return copy(priceZoom = (priceZoom * factor).coerceIn(MIN_PRICE_ZOOM, MAX_PRICE_ZOOM))
    }

    /** Back to fitting the visible bars. What a double-tap on the price gutter does. */
    fun autoPriceScale(): ChartViewport = copy(priceZoom = 1f)

    /** Re-measure after a layout pass. */
    fun sized(width: Float, height: Float): ChartViewport =
        copy(plotWidth = max(0f, width), plotHeight = max(0f, height))

    /**
     * Adopt a new series, keeping the reader where they were.
     *
     * Two cases, and neither survives simply carrying the offset across.
     *
     * At the live edge the offset stays zero and the view follows the new bar — that is what a
     * chart on the right edge should do.
     *
     * Away from it, the reader is looking at a *moment*, not at a count of bars from the end. The
     * offset is measured from the right, so appending one bar silently shifts the whole view a bar
     * older — which on a live feed drags the chart out from under someone reading history. Prepending
     * history when paging back has the opposite problem. So the current right-hand bar's timestamp
     * is looked up in the new series and the offset is recomputed from it, which is correct for
     * bars added at either end.
     */
    fun withSeries(newSeries: CandleSeries): ChartViewport {
        val maximum = max(0, newSeries.size - MIN_BARS_PER_VIEW)
        if (isAtLiveEdge || series.isEmpty || newSeries.isEmpty) {
            return copy(series = newSeries, offset = if (isAtLiveEdge) 0 else offset.coerceIn(0, maximum))
        }
        val anchor = series.time[lastVisible]
        val index = lastIndexAtOrBefore(newSeries.time, anchor)
        val rebased = if (index < 0) offset else newSeries.size - 1 - index
        return copy(series = newSeries, offset = rebased.coerceIn(0, maximum))
    }

    /** The last bar at or before [time], by binary search. −1 when every bar is later. */
    private fun lastIndexAtOrBefore(times: LongArray, time: Long): Int {
        if (times.isEmpty() || times[0] > time) return -1
        var low = 0
        var high = times.size - 1
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (times[middle] <= time) low = middle else high = middle - 1
        }
        return low
    }

    /** Fractional bar index of a moment, extrapolating beyond the loaded range. */
    private fun indexOfTime(time: Long): Double {
        val times = series.time
        if (time <= times.first()) {
            val spacing = barSpacingSeconds()
            return if (spacing <= 0) 0.0 else (time - times.first()).toDouble() / spacing
        }
        if (time >= times.last()) {
            val spacing = barSpacingSeconds()
            val last = (times.size - 1).toDouble()
            return if (spacing <= 0) last else last + (time - times.last()).toDouble() / spacing
        }
        // Binary search for the bar containing this moment, then interpolate inside it.
        var low = 0
        var high = times.size - 1
        while (low + 1 < high) {
            val middle = (low + high) / 2
            if (times[middle] <= time) low = middle else high = middle
        }
        val span = (times[high] - times[low]).toDouble()
        return if (span <= 0) low.toDouble() else low + (time - times[low]) / span
    }

    /** The series' bar interval, taken from the last gap rather than assumed. */
    private fun barSpacingSeconds(): Long {
        val times = series.time
        if (times.size < 2) return 0
        return times[times.size - 1] - times[times.size - 2]
    }

    private fun abs(value: Double) = if (value < 0) -value else value

    companion object {
        /**
         * What a phone chart opens on.
         *
         * A hundred and twenty bars is about four hours of five-minute candles or four months of
         * daily ones — enough context to see the trend the last few bars belong to, and few enough
         * that a body is still wide enough to read on a 400dp-wide screen.
         */
        const val DEFAULT_BARS_PER_VIEW = 120

        /** Below this the chart stops being a chart and becomes a few coloured rectangles. */
        const val MIN_BARS_PER_VIEW = 14

        /** Past this every bar is under a pixel wide and the wicks alias into a grey band. */
        const val MAX_BARS_PER_VIEW = 600

        /** The share of a bar's slot the body occupies; the rest is the gap. */
        const val BODY_RATIO = 0.72f

        /** Headroom above the highest wick and below the lowest, as a share of the visible range. */
        const val PRICE_PADDING = 0.08

        /** Below this the visible bars are several times the plot's height. See [priceZoom]. */
        const val MIN_PRICE_ZOOM = 0.25f

        /** Above this the candles are a horizontal line. */
        const val MAX_PRICE_ZOOM = 8f
    }
}
