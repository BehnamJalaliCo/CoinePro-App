package com.coinepro.core.chart

import com.coinepro.core.common.NumberStyle
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What the price axis is measuring.
 *
 * A price chart is asked four different questions and only one of them is "what is it worth". The
 * mode is which question the axis answers, and it is a mode rather than four separate flags because
 * they are mutually exclusive by construction: an axis cannot be counting money and counting
 * percent at the same time, and the boolean this replaced made that representable.
 */
enum class PriceScaleMode {
    /**
     * What is it worth. Equal distances are equal amounts of money.
     *
     * The right answer inside a single session of a single instrument, where the range is small
     * enough that the other three modes would all draw the same picture with worse labels.
     */
    REGULAR,

    /**
     * What did it move, as a percentage. Equal distances are equal percentages.
     *
     * The axis still prints prices — the *spacing* is what changes. It earns its place on the long
     * views: Bitcoin over two years spans more than an order of magnitude, and on a regular axis
     * the whole of the first year is a flat line pressed against the bottom of the plot with every
     * level and every trend line in it invisible.
     */
    LOGARITHMIC,

    /**
     * How far is it from where the screen starts. The axis prints `0` at the first visible bar and
     * a percentage everywhere else.
     *
     * This is the mode that makes two instruments comparable. A reader who wants to know whether
     * gold beat the index this quarter cannot read that off two axes denominated in different
     * units, and percent is the only labelling under which "the higher line won" is true.
     */
    PERCENT,

    /**
     * The same question as [PERCENT], answered on the scale a performance chart is conventionally
     * quoted on: the first visible bar is `100` and everything else is relative to it.
     *
     * Arithmetically it is [PERCENT] plus a hundred and nothing else, and it exists because
     * "it is at 118" and "it is up 18%" are the same fact read by different people. Fund and index
     * work is quoted the first way; nothing is gained by making that reader do the addition.
     */
    INDEXED_100,
}

/**
 * Which edge of the plot carries the price axis.
 *
 * State only — this viewport places prices in the plot and knows nothing about the gutters around
 * it. The renderer reads this to decide where to put the labels and how much width to take off the
 * plot before handing it back through [ChartViewport.sized].
 */
enum class ScaleSide {
    /** The default, and where every terminal in this market puts it: nearest the live edge. */
    RIGHT,

    /** For a reader who came from a platform that put it there, and for a left-handed grip. */
    LEFT,

    /** Both gutters labelled. Costs width, and buys a reading at either end of a wide tablet. */
    BOTH,

    /**
     * One axis shared by every series in the pane, rather than one per series.
     *
     * The mode that matters once a second instrument is overlaid: separate axes let two series be
     * drawn on top of each other at different scales, which flatters whichever one is being sold.
     * Merged, they are on the same axis and the comparison is honest.
     */
    MERGED,
}

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
     * Zero means the newest bar is *against* the right edge, which is where a chart opens only if
     * nobody asked for the margin below. Negative means the reader — or [atRest] — has pushed the
     * live edge away from the axis and the slots between the two carry no bar.
     *
     * ### Why it is allowed to be negative
     *
     * Because a chart whose newest candle is glued to the price axis is the single loudest tell
     * that a chart was drawn by somebody who had not used one. Three things are wrong with it at
     * once, and all three are visible in the first second:
     *
     *  * The live-price tag and the bar countdown are drawn in the gutter *beside* the last candle,
     *    so at any ordinary zoom they sit directly against the bars they are describing.
     *  * There is nowhere to put a projection. A trend line, a channel or a Fibonacci extension is
     *    drawn to say where price is going, and on a glued chart it has no room to say it — the
     *    line stops at the axis on the same bar it was anchored to.
     *  * A trader watching a bar form watches the *right* of the screen. With the edge at the axis
     *    the newest bar is half-clipped by the gutter's own hairline.
     *
     * Every terminal answers all three the same way: keep a few empty bar slots after the last bar.
     * [restingOffset] is how many, [blankSlots] is what the geometry does with it, and the default
     * here stays zero so that a thumbnail or a list-row sparkline — which has no axis and no room
     * to give away — is laid out exactly as it was before.
     */
    val offset: Int = 0,
    /** The plot rectangle in pixels: the canvas minus the price axis and the time axis. */
    val plotWidth: Float = 0f,
    val plotHeight: Float = 0f,
    /**
     * A horizontal offset in pixels applied to everything drawn in bar space, and to nothing else.
     *
     * This is the rubber band at the end of the history. [offset] is a whole number of bars and it
     * is clamped, so when a fling reaches the oldest bar the chart simply stops — dead, mid-flight,
     * with the momentum unspent and nothing on screen to say why. Every scrolling surface on the
     * phone answers that the same way: the content follows a little further than it is allowed to,
     * and springs back. That is the difference between "there is no more history" and "the app
     * stopped responding to me".
     *
     * It is deliberately *not* part of the pan. It moves the bars, the grid's time lines, the
     * drawings and the overlays together, because they are one picture; it leaves the price axis,
     * the horizontal grid and the last-price tag exactly where they were, because those measure
     * price and price has not moved. A translation of the whole canvas would have slid the axis
     * too, which reads as a rendering fault rather than as elasticity.
     *
     * Transient, like [plotWidth] and [plotHeight]: it is set by the draw pass for the frames the
     * band is stretched and is never persisted, never restored, and never part of what a viewport
     * means when it is compared for equality across a save.
     */
    val pixelShift: Float = 0f,
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
     * The range the axis is drawn at this frame, when it is not the fitted one.
     *
     * The auto-scale is animated: when the fitted range moves — new bars, a pan, a zoom — the
     * canvas springs the drawn range towards it over about 180 ms rather than jumping. This is
     * where the in-between value goes. Transient like [pixelShift]: set by the draw pass, never
     * persisted, and null on every viewport that is compared or saved.
     */
    val priceRangeOverride: ClosedFloatingPointRange<Double>? = null,
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
    /**
     * Which question the price axis answers. See [PriceScaleMode].
     *
     * ### Why the geometry only changes for one of the four
     *
     * Percent and indexed are *affine* rewrites of price — multiply by a constant, add a constant —
     * and a linear axis laid out over an affine rewrite of a range is the same axis. So the bars
     * land in exactly the same pixels in those two modes and only the labels change, which is what
     * every terminal does and is the property that lets a drawing survive a mode switch untouched.
     * Only [PriceScaleMode.LOGARITHMIC] genuinely moves anything, because a logarithm is not affine.
     *
     * That is worth knowing before someone "fixes" percent mode by rebuilding [priceRange] in
     * percent space: it would place every bar in the pixel it is already in, and would divide by a
     * base of zero on an instrument that has printed one.
     *
     * ### What no mode touches
     *
     * Time — a logarithmic *time* axis is meaningless — and nothing in chart space. A drawing is
     * still `(time, price)`, so a trend line drawn on the regular axis is the same two prices on
     * the logarithmic one. It simply stops being straight, which is correct.
     */
    val scaleMode: PriceScaleMode = PriceScaleMode.REGULAR,
    /**
     * Whether the axis runs the other way, with the low at the top.
     *
     * Not a novelty. It is how an inverse pair is read without refetching it: `USD/IRR` flipped is
     * the shape of `IRR/USD`, and a reader holding one side of a pair wants their own side the
     * right way up. It is also the fastest way to see whether two instruments are genuinely
     * inversely correlated — flip one and the two lines should lie on top of each other.
     *
     * Applied at the very last step, on the fraction of the plot a price has reached, so it
     * composes with all four modes for free rather than needing a branch inside each of them.
     */
    val inverted: Boolean = false,
    /**
     * Whether the two axes zoom together.
     *
     * ### What it protects
     *
     * An angle. A trend line's slope is pixels of price over pixels of time, so zooming one axis
     * without the other rewrites the geometry of every drawing on the chart: the line still passes
     * through its two anchors — those are stored as `(time, price)` and cannot move — but it is no
     * longer the same line to look at, and a reader who drew a channel by eye at one zoom finds it
     * does not describe the same market at another. Anything measured off a slope — a fan, a pitchfork,
     * an angle — is only meaningful with this on.
     *
     * ### Off by default
     *
     * Because the far more common gesture is "show me more bars", and a reader who has not drawn
     * anything does not want the candles flattening as they pinch out.
     */
    val priceBarLock: Boolean = false,
    /**
     * An explicit number of decimals for every price label, or null to derive one.
     *
     * Derived is right almost always — an instrument at 30,000 needs two decimals and one at
     * 0.00004 needs eight, and nothing but the price itself says which. It is wrong for the case
     * the derivation cannot see: a venue's own tick size. An instrument quoted to five decimals
     * that happens to be trading near one gets four from the rule below, and the label then rounds
     * two adjacent ticks to the same string — an axis where two different prices read identically.
     * That is what this overrides.
     */
    val decimals: Int? = null,
    /**
     * Which side of the plot the price gutter is drawn on. Pure state; see [ScaleSide].
     *
     * It lives here rather than in the renderer because it is part of what a saved layout means —
     * a reader who moved the axis should find it moved when they come back — and because the
     * renderer already reads its geometry from this one object.
     */
    val scaleSide: ScaleSide = ScaleSide.RIGHT,
) {
    /**
     * The old boolean shape of [scaleMode], for the call sites written before there were four modes.
     *
     * Derived rather than stored, so there is exactly one thing to set and no way to reach the
     * state where a viewport claims to be logarithmic and regular at once.
     */
    constructor(
        series: CandleSeries,
        barsPerView: Int = DEFAULT_BARS_PER_VIEW,
        offset: Int = 0,
        plotWidth: Float = 0f,
        plotHeight: Float = 0f,
        includedPrices: List<Double> = emptyList(),
        logScale: Boolean,
        priceZoom: Float = 1f,
    ) : this(
        series = series,
        barsPerView = barsPerView,
        offset = offset,
        plotWidth = plotWidth,
        plotHeight = plotHeight,
        includedPrices = includedPrices,
        priceZoom = priceZoom,
        scaleMode = if (logScale) PriceScaleMode.LOGARITHMIC else PriceScaleMode.REGULAR,
    )

    /** Whether the price axis is logarithmic — the one bit of [scaleMode] most callers want. */
    val logScale: Boolean get() = scaleMode == PriceScaleMode.LOGARITHMIC

    /** Index of the first visible bar, inclusive. */
    val firstVisible: Int
        get() = max(0, lastVisible + 1 - visibleCount)

    /** Index of the last visible bar, inclusive. −1 when there is nothing to show. */
    val lastVisible: Int
        get() = if (series.isEmpty) -1 else (series.size - 1 - offset).coerceIn(0, series.size - 1)

    /** How many bars are actually drawn, which is fewer than [barsPerView] on a short series. */
    val visibleCount: Int
        get() = if (series.isEmpty) 0 else min(effectiveBarsPerView - blankSlots, lastVisible + 1)

    private val effectiveBarsPerView: Int
        get() = barsPerView.coerceIn(MIN_BARS_PER_VIEW, MAX_BARS_PER_VIEW)

    /**
     * How many slots at the right edge carry no bar — the air between the newest candle and the
     * price axis. See [offset].
     *
     * Derived from the pan position rather than stored beside it, so the two cannot disagree and so
     * panning through the live edge is one continuous movement: every bar of pan slides the picture
     * by exactly one slot whichever side of zero the offset is on. A separate "margin" field would
     * have had to be spent and refilled at the boundary, which is a jump.
     */
    val blankSlots: Int
        get() = if (series.isEmpty) 0 else max(0, -offset).coerceAtMost(maxBlankSlots)

    /**
     * The furthest the reader may push the newest bar from the axis: half a screen.
     *
     * Half rather than a fixed count, because "how much air is too much" is a question about the
     * picture and not about the instrument. Past half the screen the chart is mostly empty and the
     * reader has lost the thing they were panning towards; short of it, every projection a drawing
     * tool can make has somewhere to land.
     */
    private val maxBlankSlots: Int get() = effectiveBarsPerView / 2

    /**
     * Where the chart sits when it is following the market: the newest bar, with air after it.
     *
     * A share of the window rather than a fixed number of bars, and that is the part worth stating.
     * TradingView stores its own right offset in bars, which is right on a desktop where the window
     * is wide and rarely re-zoomed, and wrong on a phone: six bars is a comfortable margin at eighty
     * bars a screen, more than a third of the plot at [MIN_BARS_PER_VIEW], and an invisible sliver
     * at six hundred. A share is the same *picture* at every zoom, which is what the reader is
     * actually judging.
     *
     * Clamped at both ends so the share cannot round to nothing when zoomed right in, and cannot
     * eat a screenful when zoomed right out.
     */
    val restingOffset: Int
        get() = -(effectiveBarsPerView * RIGHT_MARGIN_SHARE)
            .roundToInt()
            .coerceIn(MIN_RIGHT_SLOTS, MAX_RIGHT_SLOTS)
            .coerceAtMost(maxBlankSlots)

    /** Whether the right edge is at the newest bar, and so should follow new data. */
    val isAtLiveEdge: Boolean get() = offset <= 0

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
            // The blank slots at the live edge are counted, because they are part of the picture:
            // leaving them out would make the bars wider to fill a plot that is deliberately not
            // full, and the margin would collapse to nothing on exactly the short series where it
            // is most visible.
            val slots = min(effectiveBarsPerView, max(visibleCount + blankSlots, MIN_BARS_PER_VIEW))
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
     *
     * Always in price, in every mode. Percent and indexed are affine and so lay out identically;
     * see [scaleMode] for why rebuilding this in their units would be work that changes nothing.
     */
    val priceRange: ClosedFloatingPointRange<Double> by lazy {
        priceRangeOverride?.let { return@lazy it }
        fittedPriceRange
    }

    /** The range the visible bars ask for, before any animation. See [priceRangeOverride]. */
    val fittedPriceRange: ClosedFloatingPointRange<Double> by lazy {
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
        if (scaleMode == PriceScaleMode.LOGARITHMIC && low > 0.0 && high > low) {
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

    // ---------------------------------------------------------------- what the axis prints

    /**
     * The price the percentage modes are measured from: the close of the first bar on screen.
     *
     * The close rather than the open, because that is the value the series' own line is drawn
     * through and the one a reader sees at the left edge — measuring from an open the chart never
     * plotted would put the zero line a gap away from where the line starts.
     *
     * Recomputed as the reader pans, which is the point: percent mode answers "since the left of
     * *this* screen", not "since some fixed epoch". Zero on an empty series, where it is unused.
     */
    val scaleBase: Double
        get() = if (series.isEmpty || visibleCount == 0) 0.0 else series.close[firstVisible]

    /**
     * The number the axis prints for a price, which is not the price in two of the four modes.
     *
     * Everything that draws a label — the gutter, the crosshair, the last-price tag — goes through
     * here rather than formatting the price directly, so that turning on percent mode relabels all
     * of them at once instead of relabelling the gutter and leaving the crosshair quoting dollars.
     */
    fun scaleValue(price: Double): Double = when (scaleMode) {
        PriceScaleMode.REGULAR, PriceScaleMode.LOGARITHMIC -> price
        PriceScaleMode.PERCENT -> percentOf(price, scaleBase)
        PriceScaleMode.INDEXED_100 -> indexedOf(price, scaleBase)
    }

    /**
     * Back from a printed number to the price it stands for.
     *
     * The exact inverse of [scaleValue], and it exists for the same reason [priceAt] does: a reader
     * can type a level into an alert or a drawing while the axis is in percent, and the value that
     * comes back has to be a price or the alert fires at eighteen units instead of at eighteen
     * percent up.
     */
    fun priceOfScaleValue(value: Double): Double = when (scaleMode) {
        PriceScaleMode.REGULAR, PriceScaleMode.LOGARITHMIC -> value
        PriceScaleMode.PERCENT -> priceOfPercent(value, scaleBase)
        PriceScaleMode.INDEXED_100 -> priceOfPercent(value - 100.0, scaleBase)
    }

    /**
     * How many decimals a label carries: [decimals] when the caller has set one, or a figure taken
     * from the magnitude of the range when they have not.
     *
     * Four bands rather than a count of significant digits, because the axis is read as a column
     * and a column of labels has to agree on its decimal point. Deriving per label would print
     * `9.9995` above `10.000` and the reader would have to compare them digit by digit.
     */
    val effectiveDecimals: Int
        get() {
            decimals?.let { return it.coerceIn(0, MAX_DECIMALS) }
            val magnitude = max(abs(priceRange.start), abs(priceRange.endInclusive))
            return when {
                magnitude >= 1_000.0 -> 2
                magnitude >= 1.0 -> 4
                else -> 8
            }
        }

    /**
     * A market figure as a string, at the axis' precision.
     *
     * Through [NumberStyle], which is what keeps the digits Latin: the app's default locale is
     * Persian, and a price rendered through the default locale comes out in Persian digits — right
     * for a count in prose and wrong on an axis, where the reader is comparing it against an order
     * book and a wallet balance that are both in Latin digits.
     */
    fun formatPrice(value: Double): String =
        NumberStyle.fixed(value, effectiveDecimals)

    // ---------------------------------------------------------------- chart space to screen

    /** Screen x of the bar at [index], at the centre of its slot, plus any [pixelShift]. */
    fun xOf(index: Int): Float = (index - firstVisible) * barWidth + barWidth / 2 + pixelShift

    /**
     * Screen y of a price.
     *
     * On a log axis the position is taken in log space rather than the value — which is the whole
     * of what "logarithmic" means here. The guard is not a formality: a price at or below zero has
     * no logarithm, and while a *price* cannot be negative, [includedPrices] can carry a target a
     * reader dragged below the axis and an indicator pane reuses this function for values that
     * routinely are (MACD, a rate of change). Anything the log axis cannot place falls back to the
     * linear placement rather than to `NaN`, which would silently stop drawing that line.
     *
     * [inverted] is applied once, at the end, to the fraction of the plot the price reached. That
     * is why flipping the axis needs no branch of its own in any mode and why flipping twice is
     * exactly the identity rather than nearly it.
     */
    fun yOf(price: Double): Float = yOfFraction(fractionOf(price))

    /**
     * How far up the plot a price sits, from zero at the bottom of the range to one at the top.
     *
     * Before inversion, and in whichever space the mode lays the axis out in. Everything about the
     * vertical placement is here, so the two public conversions cannot drift apart.
     */
    private fun fractionOf(price: Double): Double {
        val low = priceRange.start
        val high = priceRange.endInclusive
        if (scaleMode == PriceScaleMode.LOGARITHMIC && low > 0.0 && high > low && price > 0.0) {
            val logLow = ln(low)
            val logSpan = ln(high) - logLow
            if (logSpan > 0.0) return (ln(price) - logLow) / logSpan
        }
        val span = high - low
        if (span <= 0.0) return 0.5
        return (price - low) / span
    }

    /** The fraction turned into a pixel, and the only place [inverted] is honoured. */
    private fun yOfFraction(fraction: Double): Float =
        if (inverted) (fraction * plotHeight).toFloat() else (plotHeight - fraction * plotHeight).toFloat()

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
        // The shift is taken back out first, so the bar under a finger is the bar the finger is
        // actually over while the band is stretched — the exact inverse of [xOf], which is the
        // property `ChartViewportTest` holds the pair to.
        return (firstVisible + ((x - pixelShift) / barWidth).toInt()).coerceIn(firstVisible, lastVisible)
    }

    /**
     * The price at a screen y. Not clamped: dragging above the plot means a higher price.
     *
     * The exact inverse of [yOf], including its log branch, its inversion and its fallback — they
     * have to agree or a drawing placed by a finger lands somewhere else. That is the failure this
     * pairing exists to prevent, and `ChartViewportTest` holds them against each other.
     */
    fun priceAt(y: Float): Double {
        val low = priceRange.start
        val high = priceRange.endInclusive
        if (plotHeight <= 0f) return low
        val fraction = if (inverted) y / plotHeight else (plotHeight - y) / plotHeight
        if (scaleMode == PriceScaleMode.LOGARITHMIC && low > 0.0 && high > low) {
            val logLow = ln(low)
            val logSpan = ln(high) - logLow
            if (logSpan > 0.0) return exp(logLow + fraction * logSpan)
        }
        return low + fraction * (high - low)
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
        // The near clamp is negative now: past the newest bar there is air rather than a wall. The
        // far clamp is unchanged — past the oldest bar there is genuinely nothing, and coasting
        // into it would leave the reader looking at an empty plot with no way to tell whether the
        // history ran out or the feed did.
        return copy(offset = newOffset.coerceIn(-maxBlankSlots, max(-maxBlankSlots, maximum)))
    }

    /**
     * The resting position at the live edge: the newest bar, with [restingOffset] slots of air.
     *
     * What a chart opens on and what a double tap on the plot puts it back to. Separate from
     * `atOffset(0)`, which still means "glue the newest bar to the axis" and is what a thumbnail
     * wants — a sparkline in a list row has no gutter to breathe into and no projection to make.
     */
    fun atRest(): ChartViewport = atOffset(restingOffset)

    /**
     * Zoom by a scale factor, keeping the right edge fixed.
     *
     * The right edge rather than the pinch centre, because on a price chart the right edge is the
     * present and it is what a reader is looking at. Zooming around the finger is correct on a map
     * and disorienting here — the live price would slide off screen while you were trying to look
     * at it more closely.
     *
     * With [priceBarLock] on, the price axis moves by whatever factor the bar count *actually*
     * moved by — after rounding to whole bars and after the bounds — rather than by the factor that
     * was asked for. Using the requested one would let the two axes drift apart at the ends of the
     * zoom range, which is exactly where a reader zooming hard would notice their drawings shearing.
     */
    fun zoomedBy(
        scale: Float,
        /**
         * Where the pinch is, as a share of the plot's width from the left, or null to hold the
         * right edge. With a focal point the bar under the fingers stays under the fingers, which
         * is the reference's pinch; without one the present stays put, which is what a zoom asked
         * for from a button means.
         */
        focal: Float? = null,
    ): ChartViewport {
        if (scale <= 0f || !scale.isFinite()) return this
        val before = effectiveBarsPerView
        val bars = (before / scale).roundToInt().coerceIn(MIN_BARS_PER_VIEW, MAX_BARS_PER_VIEW)
        if (focal != null && !series.isEmpty && bars != before) {
            // The bar under the focal point, then the offset that keeps it there once the window
            // is `bars` wide: the slots to its right are the same share of the new window.
            val share = focal.coerceIn(0f, 1f)
            val underFinger = firstVisible + (share * (visibleCount + blankSlots)).toInt()
            val rightOfFinger = ((1f - share) * bars).roundToInt()
            val newLast = underFinger + rightOfFinger - 1
            return copy(barsPerView = bars).let { zoomed ->
                zoomed.atOffset(series.size - 1 - newLast)
            }
        }
        // Whether the margin at the live edge was the resting one, asked *before* the bar count
        // moves. The margin is a share of the window, so a chart that was resting has to go on
        // resting at the new zoom — otherwise pinching out shrinks the air to a sliver and pinching
        // in blows it up to a third of the plot, and the reader has to re-find the edge every time
        // they change zoom.
        val resting = offset == restingOffset
        val zoomed = if (!priceBarLock) {
            copy(barsPerView = bars)
        } else {
            val realised = bars.toFloat() / before
            copy(
                barsPerView = bars,
                priceZoom = (priceZoom * realised).coerceIn(MIN_PRICE_ZOOM, MAX_PRICE_ZOOM),
            )
        }
        // Re-clamped either way: the near bound is half the window, so a hard pinch inward can
        // leave a stored offset outside the range the new zoom allows.
        return if (resting) zoomed.atRest() else zoomed.atOffset(zoomed.offset)
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

    // ---------------------------------------------------------------- axis settings

    /** Switch what the axis measures. See [PriceScaleMode]. */
    fun withScaleMode(mode: PriceScaleMode): ChartViewport = copy(scaleMode = mode)

    /**
     * The one-tap version of [withScaleMode], between regular and logarithmic.
     *
     * Kept because that is the toggle the toolbar actually offers and the one the chart shipped
     * with; the other two modes are reached from the axis menu, where there is room to name them.
     */
    fun toggleLogScale(): ChartViewport = withScaleMode(
        if (scaleMode == PriceScaleMode.LOGARITHMIC) PriceScaleMode.REGULAR else PriceScaleMode.LOGARITHMIC,
    )

    /** Flip the axis so the low is at the top, or back. See [inverted]. */
    fun toggleInverted(): ChartViewport = copy(inverted = !inverted)

    /** Tie the price axis to the bar axis, or let them move independently. See [priceBarLock]. */
    fun withPriceBarLock(locked: Boolean): ChartViewport = copy(priceBarLock = locked)

    /** Pin the label precision, or pass null to go back to deriving it. See [decimals]. */
    fun withDecimals(n: Int?): ChartViewport = copy(decimals = n?.coerceIn(0, MAX_DECIMALS))

    /** Move the price gutter. See [ScaleSide]. */
    fun withScaleSide(side: ScaleSide): ChartViewport = copy(scaleSide = side)

    /**
     * The boolean form of [withScaleMode], for callers holding a saved `logScale` flag.
     *
     * Turning it off returns to [PriceScaleMode.REGULAR] only when the axis was actually
     * logarithmic. A caller that is merely restoring a stale `false` must not silently drag a
     * reader out of percent mode, which a plain `copy(scaleMode = REGULAR)` would do on every
     * recomposition.
     */
    fun copy(logScale: Boolean): ChartViewport = when {
        logScale -> withScaleMode(PriceScaleMode.LOGARITHMIC)
        scaleMode == PriceScaleMode.LOGARITHMIC -> withScaleMode(PriceScaleMode.REGULAR)
        else -> this
    }

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
            // `min(0, offset)` rather than `0`: a reader resting with air at the live edge is still
            // at the live edge, and resetting them to zero would snap the newest bar against the
            // axis every time a bar arrived — which on a live feed is the chart twitching once a
            // minute.
            return copy(
                series = newSeries,
                offset = if (isAtLiveEdge) min(0, offset) else offset.coerceIn(0, maximum),
            )
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
         * **Eighty**, and the number comes from the screen rather than from taste. The dominant
         * viewport in this app's market is 393dp wide (Iran, ~19% of devices, with 384 and 412 next
         * — Samsung and Xiaomi between them are 77% of the market). Subtract the price gutter and
         * about 330dp of that is plot. At eighty bars each slot is ~4dp, of which the candle body
         * is 72% — just under 3dp, which is the narrowest a body can be and still show its colour
         * and its direction at arm's length.
         *
         * A hundred and twenty, which is what this used to be, put each body under 2dp on the same
         * screen: a grey haze with wicks in it. That is a chart a reader zooms *in* on before they
         * can use it, every time they open one, which is the wrong default to hand somebody.
         *
         * The reader can still see more — [MAX_BARS_PER_VIEW] is 600 — and their zoom is saved.
         * This is only where a chart *starts*.
         */
        const val DEFAULT_BARS_PER_VIEW = 80

        /** Below this the chart stops being a chart and becomes a few coloured rectangles. */
        const val MIN_BARS_PER_VIEW = 14

        /** Past this every bar is under a pixel wide and the wicks alias into a grey band. */
        const val MAX_BARS_PER_VIEW = 2400

        /** The share of a bar's slot the body occupies; the rest is the gap. */
        const val BODY_RATIO = 0.72f

        /**
         * How much of the window is air between the newest bar and the price axis, at rest.
         *
         * Six percent. On the 393dp phone this app is built for that is about twenty points of
         * plot: wide enough that the live-price tag and the countdown are not pressed against the
         * candle they describe, wide enough for a projection to say something, and narrow enough
         * that it never reads as the feed having stopped. Ten percent was tried and reads as a
         * gap; three is not distinguishable from none.
         */
        const val RIGHT_MARGIN_SHARE = 0.06f

        /** Below two slots the margin rounds away entirely at the tightest zoom. */
        const val MIN_RIGHT_SLOTS = 2

        /** And above this it stops growing, so a zoomed-out chart is bars rather than air. */
        const val MAX_RIGHT_SLOTS = 24

        /** Headroom above the highest wick and below the lowest, as a share of the visible range. */
        const val PRICE_PADDING = 0.08

        /** Below this the visible bars are several times the plot's height. See [priceZoom]. */
        const val MIN_PRICE_ZOOM = 0.25f

        /** Above this the candles are a horizontal line. */
        const val MAX_PRICE_ZOOM = 8f

        /**
         * The most decimals any label may carry.
         *
         * Eight, because that is a satoshi and nothing quoted on either feed is finer. It is a
         * clamp on [decimals] rather than a suggestion: `"%.400f"` is a legal format string and
         * would put four hundred characters into a text measure inside a draw.
         */
        const val MAX_DECIMALS = 8

        /**
         * A value as a percentage of a base — the arithmetic behind [PriceScaleMode.PERCENT].
         *
         * The negation when the base is below zero is the part worth explaining. Dividing by a
         * negative base already flips the sign, so a series that went *up* from −40 to −20 would
         * report −50%: technically what the formula says, and read by every human being as a loss.
         * Flipping it back keeps "up on the chart" and "positive on the axis" the same thing.
         * Prices are never negative, but the indicator panes share this axis and oscillators are.
         *
         * A base of zero has no percentage of it at all — every value is infinitely far from
         * nothing — so it reports zero rather than an infinity that would take the whole axis
         * with it.
         */
        fun percentOf(value: Double, base: Double): Double {
            if (base == 0.0 || !base.isFinite() || !value.isFinite()) return 0.0
            val raw = 100.0 * (value - base) / base
            return if (base < 0.0) -raw else raw
        }

        /**
         * The same figure rebased so the first visible bar reads 100, which is
         * [PriceScaleMode.INDEXED_100].
         *
         * Written as [percentOf] plus a hundred rather than as its own expression, so the two
         * cannot disagree about what a base of zero or a negative base means.
         */
        fun indexedOf(value: Double, base: Double): Double = percentOf(value, base) + 100.0

        /** [percentOf] run backwards: the value that is this many percent from the base. */
        fun priceOfPercent(percent: Double, base: Double): Double {
            if (base == 0.0 || !base.isFinite() || !percent.isFinite()) return base
            val signed = if (base < 0.0) -percent else percent
            return base + signed * base / 100.0
        }
    }
}
