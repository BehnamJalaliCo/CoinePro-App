package com.coinepro.core.chart

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * The chart's pixel arithmetic, with nothing Compose in it.
 *
 * Every number in this file is a real constant out of a shipping terminal rather than a value that
 * looked right in a preview, and each one is written down with the reason it is that number. They
 * are here — plain `Float` in, plain `Float` out, no `Dp`, no `DrawScope` — for two reasons. They
 * can be unit-tested, which the draw pass in `CoineProChart.kt` cannot; and a renderer that reads
 * its geometry out of named functions is a renderer somebody can change one rule of without
 * discovering the other four by breaking them.
 *
 * The vocabulary follows the source these rules come from: **bar spacing** is the pitch from one
 * bar's centre to the next in *layout* pixels, **pixel ratio** is the device's density (the same
 * number Compose calls `Density.density`), and everything returned is in *device* pixels and
 * already floored onto a device-pixel boundary. Mixing the two units is the mistake this file
 * exists to make hard: the same file once had wick widths in raw pixels and marker sizes in dp,
 * and the half that was wrong was the half a trader looks at.
 */

/**
 * How wide a candle body is, given the space one bar has.
 *
 * ### Why not a fixed percentage of the slot
 *
 * A fixed ratio reads wrong at both extremes. At three pixels a bar, 72% of the slot rounds to two
 * pixels and the chart is a grey haze; at forty pixels a bar it leaves an eleven-pixel canyon
 * between candles that makes a trend look like a picket fence. What a reader actually wants is for
 * the *gap* to stay legible and to grow slowly as they zoom in, which is what this curve does: the
 * body-to-slot ratio starts at effectively the whole slot when bars are tiny and decays toward
 * **80/20** as the bars get wide.
 *
 * ### The three numbers
 *
 * **2.5 to 4** is a special case, pinned to `floor(3 × pixelRatio)`. That band is where the general
 * curve would hand back two pixels and then three again a fraction of a bar-spacing later, so a
 * slow zoom through it made every candle on screen flicker between widths. Pinning it costs a
 * fraction of a pixel of accuracy and removes the shimmer.
 *
 * **0.2** is how much of the slot the gap is allowed to grow into, and **`atan`** is what makes it
 * arrive there gently: `atan(x) / (π / 2)` runs from 0 to 1 over the whole positive line, so the
 * coefficient falls from 1.0 to 0.8 and never past it. A linear falloff would either reach 80/20
 * immediately or overshoot into candles that are mostly gap.
 *
 * The result is floored onto a device pixel, capped by the slot itself, and never allowed below one
 * device pixel — a candle that rounds to nothing is a hole in the chart.
 */
fun optimalBarWidth(barSpacing: Float, pixelRatio: Float): Float {
    if (barSpacing >= SPECIAL_CASE_FROM && barSpacing <= SPECIAL_CASE_TO) {
        return floor(SPECIAL_CASE_WIDTH * pixelRatio)
    }
    val beyond = max(SPECIAL_CASE_TO, barSpacing) - SPECIAL_CASE_TO
    val coefficient = 1f - GAP_SHARE * atan(beyond) / (PI.toFloat() * 0.5f)
    val curved = floor(barSpacing * coefficient * pixelRatio)
    val slot = floor(barSpacing * pixelRatio)
    return max(floor(pixelRatio), min(curved, slot))
}

/**
 * How wide the line from a bar's high to its low is drawn.
 *
 * One device pixel, which is the answer at every zoom a reader spends time at: a wick is a
 * *position*, not a quantity, and thickening it with the body only makes a wide candle look like it
 * has a mast. The two clamps are what stop that one pixel being wrong.
 *
 * The upper clamp is the body. A wick wider than the candle it rises out of reads as a bar with
 * shoulders, and it happens for real on a 3× screen zoomed all the way out, where the body has
 * floored to two device pixels and the naive answer is three.
 *
 * The lower clamp is one device pixel flat, and it is deliberately **not** `floor(pixelRatio)` even
 * though that is the shape the rule is usually written in. Taking the maximum against
 * `floor(pixelRatio)` last would swallow the body cap entirely on any screen denser than 1× —
 * `max(3, min(3, 2))` is 3 — so the cap would be dead code on precisely the devices this app runs
 * on. One pixel is the floor because a stroke thinner than a pixel is a stroke the platform draws
 * as a grey suggestion, which is what the wicks in this file looked like before they were moved off
 * raw pixels.
 */
fun wickWidth(barWidth: Float, barSpacing: Float, pixelRatio: Float): Float {
    val hairline = min(floor(pixelRatio), floor(barSpacing * pixelRatio))
    return max(MIN_STROKE, min(hairline, barWidth))
}

/**
 * How thick a candle's outline is, when it has one.
 *
 * Half the width of a candle drawn at one unit of bar spacing — which is to say, half of the
 * narrowest candle the renderer will ever produce — floored to a device pixel. That sounds
 * arbitrary and is not: it makes the border scale with the screen rather than with the zoom, so a
 * candle keeps the same visual weight of outline whether the reader is looking at thirty bars or
 * six hundred.
 *
 * The second branch is the one that matters. When the body has shrunk to the point where two
 * borders would meet in the middle, the border is recomputed from the body itself — `(barWidth − 1)
 * / 2`, the largest outline that still leaves a pixel of fill between the two sides. Below that
 * there is nothing left to outline and [drawBorder] says so.
 */
fun borderWidth(barWidth: Float, pixelRatio: Float): Float {
    var border = floor(optimalBarWidth(1f, pixelRatio) * 0.5f)
    if (barWidth <= 2 * border) border = floor((barWidth - 1f) * 0.5f)
    return max(floor(pixelRatio), border)
}

/**
 * Whether a candle gets an outline at all, or is filled solid.
 *
 * The threshold is `barWidth > borderWidth × 2`: the body has to be wide enough for two borders and
 * something in between them. Below it the outline would consume the whole candle and the two
 * strokes would overlap, which is exactly the zoom level at which a chart full of outlined candles
 * turns to mush — a band of colour with no readable direction in it. So the answer there is a solid
 * fill, which still says up or down at a glance, and the outline comes back on its own as the
 * reader zooms in.
 */
fun drawBorder(barWidth: Float, borderWidth: Float): Boolean = barWidth > borderWidth * 2

// ---------------------------------------------------------------------------- pixel registration
//
// The three functions below are the difference between a chart that was drawn and a chart that was
// rendered, and none of them changes a single number the reader is being told — they change only
// *where the ink lands*.
//
// ### The defect they close
//
// `optimalBarWidth` has always floored the body onto a whole device pixel, which is correct and was
// not enough, because nothing ever floored the position it was drawn at. A bar's centre is
// `(index − first) × barWidth + barWidth / 2`, and `barWidth` is the plot divided by however many
// bars are in view — an irrational number for all practical purposes. So a body eleven device
// pixels wide was asked to start at x = 431.6, the rasteriser spread it over twelve columns with
// the two outer ones at partial coverage, and the *next* body started at 448.2 and got a different
// pair of partial columns. Nothing is wrong in the arithmetic and every candle on screen is a
// slightly different weight, with gaps that alternate between too tight and too open. That is the
// single loudest reason a hand-written chart reads as a picture of a chart: the eye reads the
// irregularity long before it reads the prices.
//
// The same thing happens to every hairline. A one-pixel rule asked for at y = 812.0 straddles the
// boundary between rows 811 and 812 and is painted as two rows at half intensity — a 2px grey
// smear where a 1px line was intended. A grid drawn that way looks soft and dirty at any density,
// and looks *worse* the denser the screen, which is the opposite of what a reader expects from a
// better phone.
//
// ### Why it is here rather than at the call sites
//
// Because there are six call sites — candles, OHLC bars, volume candles, the volume band, a pane's
// histogram, and every rule and axis line — and a chart in which the volume bars are registered
// half a pixel differently from the candles above them is mis-registered in exactly the way this
// whole file exists to prevent. One named function each, tested, used everywhere.

/**
 * The left edge of a bar-shaped mark whose centre wants to be at [centreX], on a device pixel.
 *
 * Rounded rather than floored: rounding keeps the body within half a pixel of the centre the
 * geometry asked for, so a candle never visibly leans away from its own wick, and it distributes
 * the accumulated error evenly instead of biasing every bar in the chart to the left.
 *
 * The width is not adjusted. [optimalBarWidth] has already made it a whole number of device pixels,
 * so a snapped left edge means both edges land on boundaries and every candle on screen is drawn
 * with exactly the same weight — which is the whole of the effect.
 */
fun barLeft(centreX: Float, body: Float): Float = round(centreX - body / 2f)

/**
 * The centre a vertical or horizontal stroke of [stroke] pixels must be given to cover whole pixels.
 *
 * An odd-width stroke is centred on a pixel's middle — `floor(x) + 0.5` — because an odd number of
 * pixels has a middle one; an even-width stroke is centred on the boundary between two. Getting
 * this the wrong way round is worse than not doing it at all: it moves every line half a pixel and
 * leaves them just as soft.
 *
 * [stroke] is rounded to a whole pixel first and never allowed below one, since a fractional stroke
 * cannot be registered on anything — asking for 2.4 pixels of ink is asking the rasteriser to
 * decide, and it decides differently at every position.
 */
fun strokeCentre(x: Float, stroke: Float): Float {
    val width = max(MIN_STROKE, round(stroke))
    return if (width.toInt() % 2 == 1) floor(x) + 0.5f else round(x)
}

/**
 * A stroke width, rounded onto a whole device pixel and never thinner than one.
 *
 * The companion to [strokeCentre]: registering a line's position is pointless while its width is
 * still fractional, because the rasteriser resolves the leftover fraction into a partial column at
 * one end. Every rule, grid line and axis edge in the renderer goes through this.
 */
fun crispStroke(stroke: Float): Float = max(MIN_STROKE, round(stroke))

/**
 * How wide the price gutter has to be for its labels.
 *
 * The terms, left to right, are the border between plot and axis (1), the tick's inner margin (5),
 * two paddings that scale with the type size so a reader who has enlarged their system font gets a
 * proportionally roomier axis rather than a cramped one, the outer margin (5), and the widest label
 * that will be printed in it.
 *
 * ### Why it is rounded up to an even number
 *
 * Because the 1px border between the plot and the axis has to land on a device-pixel boundary. At
 * 2× density an odd width puts that border on a half-pixel, the compositor resolves it by painting
 * two rows at half intensity, and the result is a fuzzy grey line down the side of the chart
 * instead of a crisp one. It is invisible in a description and it is the whole difference between
 * an axis that looks drawn and an axis that looks photographed. Nobody will ever name it in a
 * review; they will say the chart looks cheap.
 */
fun priceAxisWidth(maxLabelWidth: Float, fontSize: Float): Float {
    val raw = ceil(
        AXIS_BORDER + AXIS_INNER_MARGIN +
            fontSize / BASE_FONT_SIZE * AXIS_TICK_PADDING +
            fontSize / BASE_FONT_SIZE * AXIS_TICK_PADDING +
            AXIS_OUTER_MARGIN + maxLabelWidth,
    )
    return if (raw.toInt() % 2 == 0) raw else raw + 1f
}

/**
 * How tall the strip of dates under the plot has to be.
 *
 * The border (1), the gap between the plot and the first glyph (5), one line of type, and three
 * paddings that scale with it. At the 12-unit reference size this comes out at exactly **28**,
 * which is the number every terminal's time axis is, and the reason it is worth reproducing rather
 * than picking is that it is the smallest height at which a date and the tick above it read as
 * belonging to each other rather than as two separate rows.
 *
 * It scales with the font because the font scales with the reader's accessibility setting. A fixed
 * height plus a growing font is a clipped date, which is the failure mode this replaces.
 */
fun timeAxisHeight(fontSize: Float): Float = ceil(
    AXIS_BORDER + AXIS_INNER_MARGIN + fontSize +
        TIME_TICK_PADDING * fontSize / BASE_FONT_SIZE +
        TIME_TICK_PADDING * fontSize / BASE_FONT_SIZE +
        TIME_LABEL_PADDING * fontSize / BASE_FONT_SIZE,
)

/**
 * Where a finger or a cursor still counts as being on a separator line.
 *
 * The line itself is one pixel. The band is **nine density-independent pixels**, offset four above
 * it, so it straddles the line rather than sitting under it — nine times the size of the thing it
 * is a target for.
 *
 * That ratio is the entire trick. A one-pixel divider that can only be grabbed on its one pixel is
 * a control that appears broken; the same divider with a band around it is a control that "just
 * works", and nobody can point at what makes the difference because the band never appears in a
 * screenshot. It is the cheapest thing in this file and it is most of what separates an interface
 * that feels expensive from one that does not.
 */
fun separatorHitRect(separatorY: Float, density: Float): ClosedFloatingPointRange<Float> {
    val start = separatorY + SEPARATOR_OFFSET * density
    return start..(start + SEPARATOR_BAND * density)
}

/**
 * The five ways a line on this chart can be drawn.
 *
 * Five and not two, because a dash pattern is how a chart says what *kind* of line something is
 * without spending a colour on it: a pivot is solid and the levels derived from it are dashed, a
 * target is one weight of dash and a stop another, and a reader's own drawing has to be
 * distinguishable from the study underneath it.
 */
enum class LineStyleKind {
    /** No pattern at all. */
    SOLID,

    /** On for its own width, off for its own width. Reads as a texture rather than as segments. */
    DOTTED,

    /** The default broken line: segments twice the stroke's width, gaps to match. */
    DASHED,

    /** Six times the width, for a line that has to stay legible across a whole plot. */
    LARGE_DASHED,

    /** A dot every five widths. The quietest line that is still visibly deliberate. */
    SPARSE_DOTTED,
}

/**
 * The on/off pattern for a line style, in device pixels.
 *
 * Every interval is a multiple of the line's own width rather than a fixed number of pixels, and
 * that is the point: a reader who thickens a drawing to 4px gets dashes four times as long, so the
 * line keeps the proportions it was designed with instead of turning into a solid rule with
 * pinholes in it. A fixed pattern is a pattern that only looks right at one stroke width.
 *
 * [LineStyleKind.SOLID] returns an empty array. Callers must treat that as "no dash effect" —
 * handing an empty interval list to a platform path effect is undefined and on Android it throws.
 */
fun dashIntervals(style: LineStyleKind, lineWidth: Float): FloatArray = when (style) {
    LineStyleKind.SOLID -> FloatArray(0)
    LineStyleKind.DOTTED -> floatArrayOf(lineWidth, lineWidth)
    LineStyleKind.DASHED -> floatArrayOf(2 * lineWidth, 2 * lineWidth)
    LineStyleKind.LARGE_DASHED -> floatArrayOf(6 * lineWidth, 6 * lineWidth)
    LineStyleKind.SPARSE_DOTTED -> floatArrayOf(lineWidth, 4 * lineWidth)
}

/**
 * How large the type on an axis is, in scalable pixels.
 *
 * **Twelve on the price axis, eleven on the time axis**, and the difference is not a rounding
 * error. The vertical axis carries the number the reader came for — the one they read off a
 * gridline and repeat out loud — and the horizontal one carries context they glance at to place a
 * candle in the week. Setting both to the same size makes the chart louder without making anything
 * more legible; setting the price axis a step larger is the cheapest way to say which of the two a
 * reader is meant to trust.
 *
 * Both are `sp` rather than `dp`, so both follow the system font setting. Eleven is the floor of
 * this app's own type scale, which is what stops the time axis being sized by taste.
 */
fun axisFontSizeSp(isPriceAxis: Boolean): Float =
    if (isPriceAxis) PRICE_AXIS_FONT_SP else TIME_AXIS_FONT_SP

/**
 * The type size the legend prints at.
 *
 * A point under the time axis and two under the price axis, and it is the smallest type this app
 * sets anywhere — which is right, because the legend is the only text drawn *over* the picture
 * rather than beside it. Everything in the gutters has a clear ground and can afford to be read at
 * a glance; the legend is read deliberately, by somebody who has already decided to look at it, and
 * every point it gives back is a point of chart it stops covering.
 *
 * Not a hard-coded 10: it is still a `sp`, so a reader who has enlarged their system type gets a
 * larger legend and the plate's own row arithmetic — which measures rather than assumes — makes
 * room for it.
 */
fun legendFontSizeSp(): Float = LEGEND_FONT_SP

/**
 * The eight corner radii of a label chip, in the order a rounded rectangle takes them: top-left,
 * top-right, bottom-right, bottom-left, each as an x and a y.
 *
 * A chip in the price gutter is **rounded on one side only**. The side it is flush against stays
 * square, so the chip reads as something that has grown out of the axis edge; round it on all four
 * and it detaches and floats, which makes the reader look for what it is pointing at. It is the
 * same reason a tab is round on top and square where it meets the page.
 *
 * [rightAligned] means the chip is flush against the right-hand edge of the canvas — the ordinary
 * case, since the price axis is on the right on every chart in this app — so its right corners are
 * square and the radius goes on the left. Pass false for a chip anchored to a left-hand axis.
 *
 * The radius is 2dp: enough to read as intentional at arm's length, small enough that it never
 * competes with the number inside it.
 */
fun labelChipRadii(rightAligned: Boolean, radius: Float): FloatArray = if (rightAligned) {
    floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
} else {
    floatArrayOf(0f, 0f, radius, radius, radius, radius, 0f, 0f)
}

/**
 * Nudge a column of labels apart so none of them overlaps its neighbour.
 *
 * ### Why labels are moved rather than dropped
 *
 * Two axis labels a few pixels apart do not read as two numbers; they read as one damaged one. The
 * obvious fix is to drop the later of the pair, which is what the time axis does — but on the price
 * axis a dropped label is a gridline with no value against it, and the reader is left counting
 * lines from the one above. Moving the label a few pixels costs a small inaccuracy in *where* it
 * sits and keeps the number, which is the trade every terminal makes.
 *
 * ### How
 *
 * One pass down the column pushing anything too close to its predecessor away, then one pass back
 * up pulling anything that has been pushed past [bottom] into the space above it. Two passes are
 * needed rather than one: a single downward pass piles the overflow up against the bottom edge, and
 * a single upward pass does the same at the top.
 *
 * Order is preserved by construction — the passes walk the input in sorted order and each label is
 * placed at least [height] beyond the last — so a label never crosses the one it was below. When
 * the column is too short to hold them all at full separation they compress against the top rather
 * than disappearing, which is a legible failure and, unlike dropping them, a recoverable one: the
 * reader zooms and they come apart.
 *
 * [centres] are the labels' ideal centre lines; the returned array is their placed centre lines, at
 * the same indices.
 */
fun separateLabels(
    centres: FloatArray,
    height: Float,
    top: Float,
    bottom: Float,
): FloatArray {
    if (centres.isEmpty()) return FloatArray(0)
    val order = centres.indices.sortedBy { centres[it] }
    val placed = FloatArray(centres.size)
    val half = height / 2

    var previous = Float.NEGATIVE_INFINITY
    for (index in order) {
        var y = max(centres[index], top + half)
        if (previous.isFinite() && y - previous < height) y = previous + height
        placed[index] = y
        previous = y
    }

    var next = Float.POSITIVE_INFINITY
    for (position in order.indices.reversed()) {
        val index = order[position]
        var y = min(placed[index], bottom - half)
        if (next.isFinite() && next - y < height) y = next - height
        placed[index] = y
        next = y
    }
    return placed
}

/**
 * Momentum after the finger lifts.
 *
 * ### Why it is touch only
 *
 * A flick is a statement of intent that a finger cannot make any other way: the pointer has left
 * the glass, and the only thing that can carry the reader's meaning past that moment is the speed
 * they let go at. A mouse wheel and a trackpad have no such moment — the operating system already
 * turns a two-finger swipe into its own decelerating stream of scroll events — so adding momentum
 * on top of one applies the deceleration twice and the chart carries on moving for a second after
 * the reader has stopped. Every desktop terminal that has shipped this has had to take it back out
 * again. So the caller is required to establish that the gesture came from a touch pointer before
 * it calls [start]; this class deliberately has no idea what a pointer is.
 *
 * ### The decay
 *
 * Exponential, at [DECAY_PER_MILLISECOND] per millisecond of wall clock rather than per frame. Per
 * frame would make the fling travel further on a 120Hz phone than on a 60Hz one, which is a chart
 * that behaves differently on different hardware for no reason a reader could ever discover. At
 * 0.997 the speed halves roughly every 230ms and is down to a twentieth of its starting value after
 * a second, which is about as long as a flick should keep going before it feels like the chart has
 * got away from the reader.
 *
 * ### The cut-off
 *
 * [MIN_VELOCITY] is **twenty pixels a second**. At 60Hz that is a third of a pixel per frame: below
 * it the chart is not moving, it is shimmering, and every frame spent there is a frame spent
 * recomputing a price scale that produced the same picture. Stopping at a threshold rather than at
 * zero is also what makes the fling *end* rather than asymptotically approach stillness, which
 * matters because the end of the fling is when the chart is allowed to go idle.
 */
class KineticScroll {

    private var velocity = 0f
    private var lastTick = 0L
    private var started = false
    private var running = false

    /** Whether a fling is still in flight. False before [start] and after the decay cuts off. */
    val isRunning: Boolean get() = running

    /**
     * Begin a fling at [velocity] pixels per second, positive meaning the content moves right.
     *
     * A velocity already below the cut-off starts nothing at all, so a slow drag that ends with the
     * finger almost still does not produce a one-frame twitch after the release.
     */
    fun start(velocity: Float) {
        if (!velocity.isFinite() || abs(velocity) < MIN_VELOCITY) {
            stop()
            return
        }
        this.velocity = velocity
        lastTick = 0L
        started = false
        running = true
    }

    /**
     * How far the content should move since the last tick, in pixels.
     *
     * The first tick after [start] establishes the clock and returns zero — there is no elapsed
     * time to integrate over yet, and guessing a frame's worth would make the fling's first step
     * depend on when the caller happened to ask.
     *
     * Returns zero forever once the fling has stopped, so a caller that keeps ticking a finished
     * animation is harmless rather than wrong.
     */
    fun tick(nowMillis: Long): Float {
        if (!running) return 0f
        // A flag rather than a zero sentinel on the clock, because zero is a perfectly ordinary
        // frame time — `withFrameMillis` on a fresh process hands one out — and treating it as
        // "not started yet" would make the first fling of a session never advance at all.
        if (!started) {
            started = true
            lastTick = nowMillis
            return 0f
        }
        val elapsed = nowMillis - lastTick
        if (elapsed <= 0L) return 0f
        lastTick = nowMillis
        // Integrated over the interval rather than sampled at its start: at 60Hz the two differ by
        // under a percent, but a dropped frame makes the sampled version overshoot by however long
        // the stall was, which is exactly when a reader notices the chart jump.
        val decayed = velocity * DECAY_PER_MILLISECOND.pow(elapsed.toFloat())
        val travelled = (velocity - decayed) / DECAY_RATE
        velocity = decayed
        if (abs(velocity) < MIN_VELOCITY) stop()
        return travelled / MILLIS_PER_SECOND
    }

    /** Cancel the fling. Called on the next touch down, so a finger always beats the momentum. */
    fun stop() {
        velocity = 0f
        lastTick = 0L
        started = false
        running = false
    }

    private companion object {
        /**
         * The share of its speed a fling keeps per millisecond.
         *
         * Not per frame — see the class KDoc. 0.997 is the number every touch scroller in this
         * class of app converges on; below 0.99 the fling stops under the finger and above 0.999 it
         * coasts long enough to feel like the chart is ignoring the reader.
         */
        const val DECAY_PER_MILLISECOND = 0.997f

        /** `1 − decay`, which is what the closed form of the integral divides by. */
        const val DECAY_RATE = 1f - DECAY_PER_MILLISECOND

        /** Below this many pixels a second the fling is over. See the class KDoc. */
        const val MIN_VELOCITY = 20f

        const val MILLIS_PER_SECOND = 1_000f
    }
}

// ---------------------------------------------------------------------------- constants

/** The bar spacing band where the body width is pinned instead of curved. See [optimalBarWidth]. */
private const val SPECIAL_CASE_FROM = 2.5f
private const val SPECIAL_CASE_TO = 4f

/** What the body is pinned to inside that band, in layout pixels. */
private const val SPECIAL_CASE_WIDTH = 3f

/** How much of a bar's slot the gap is allowed to grow into, at the limit. See [optimalBarWidth]. */
private const val GAP_SHARE = 0.2f

/** A stroke cannot be thinner than a device pixel and still be drawn honestly. */
private const val MIN_STROKE = 1f

/** The hairline between the plot and an axis. */
private const val AXIS_BORDER = 1f

/** Between that border and the first glyph of a label. */
private const val AXIS_INNER_MARGIN = 5f

/** Between the last glyph and the edge of the canvas. */
private const val AXIS_OUTER_MARGIN = 5f

/** The type size the padding terms were measured at, and so what they are expressed relative to. */
private const val BASE_FONT_SIZE = 12f

/** Padding either side of a price-axis tick, at [BASE_FONT_SIZE]. */
private const val AXIS_TICK_PADDING = 5f

/** Padding above and below a time-axis tick mark, at [BASE_FONT_SIZE]. */
private const val TIME_TICK_PADDING = 3f

/** Padding under a time-axis label, at [BASE_FONT_SIZE]. */
private const val TIME_LABEL_PADDING = 4f

/** How far above the line the separator's grab band starts. See [separatorHitRect]. */
private const val SEPARATOR_OFFSET = -4f

/** And how tall it is: nine times the line it is a target for. */
private const val SEPARATOR_BAND = 9f

/** See [axisFontSizeSp]. */
private const val PRICE_AXIS_FONT_SP = 12f
// Twelve on both axes, which is what TradingView sets: its time labels are the same 12 px as its
// price labels, and a smaller row under a larger column read as an afterthought.
private const val TIME_AXIS_FONT_SP = 12f

/** See [legendFontSizeSp]. */
// Twelve, up from ten. TradingView's legend values are 13 px on a 411 px phone and its title 16;
// at ten this app's legend was the smallest text on the screen and the one a trader reads most.
// Fourteen: TradingView's phone legend sets its values at 14 pt and its title at 17, measured.
private const val LEGEND_FONT_SP = 14f
