package com.coinepro.core.chart

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A point on one of the second batch of drawing tools, in **chart space** — a moment and a price.
 *
 * The same decision [ChartPoint] rests on, with the moment widened to a `Double`. These tools
 * subdivide the interval between two taps — a Bézier at sixty-four samples, a cycle repeating at a
 * fractional bar — and a `Long` of milliseconds would round every one of those samples onto a
 * millisecond boundary, which on a monthly chart is invisible and on a one-second chart is a curve
 * with visible steps in it. Nothing here is in pixels, so a curve placed at 2,614 stays at 2,614
 * through every pan, zoom and rotation.
 *
 * It is deliberately *not* the same type the first batch of geometry uses. Two agents built these
 * two batches at once, and a shared point type would have been a file neither of them owned.
 */
data class GeoPointB(val t: Double, val p: Double)

/**
 * One straight piece of a drawing, with the two flags that say it does not stop where it is drawn.
 *
 * [extendA] and [extendB] are what separate a trend line from a ray from an extended line without
 * three separate shapes, and [timeCycles] leans on them harder than that: a full-height vertical is
 * expressed here as a zero-length segment with both flags set, because a drawing layer that knows
 * nothing about the viewport cannot know how tall "full height" is. The renderer does.
 *
 * [label] is the text the renderer draws against the segment — a measured Fibonacci ratio, an
 * Elliott wave name, a cycle number. It is null on a segment that is only a line.
 */
data class GeoSegmentB(
    val a: GeoPointB,
    val b: GeoPointB,
    val extendA: Boolean = false,
    val extendB: Boolean = false,
    val label: String? = null,
)

/**
 * A chain of points drawn as one stroke, optionally closed back to the start.
 *
 * Every curved tool in this file ends here. Sampling an arc, a Bézier or a pie sector into a
 * polyline rather than handing the renderer a centre and two angles is what keeps the curve in
 * chart space: each sample is a real (time, price) pair, so the curve deforms correctly when the
 * price axis is switched to logarithmic, and it stays anchored when the chart is panned. A shape
 * described by an angle would have to be re-derived every frame and would be wrong on a log axis.
 */
data class GeoPolyB(val points: List<GeoPointB>, val closed: Boolean = false, val label: String? = null)

/**
 * The geometry of the second batch of drawing tools: the harmonic and Elliott corrections, the
 * curves, the free-form paths, and the three tools that replay one part of the chart somewhere else.
 *
 * Backlog items 15 to 25 and 31 to 33. Every function takes chart-space anchors and returns
 * chart-space shapes; nothing here imports Compose, touches a viewport or knows what a pixel is,
 * which is what makes the whole file unit-testable against real coordinates rather than against a
 * screenshot.
 *
 * Two conventions run through all of it. Degenerate input never throws — too few anchors, a
 * zero-length interval, an empty source window and a radius of zero all return an empty list or an
 * empty poly, because these functions are called on every frame while the reader is still tapping
 * the tool out and a half-placed drawing is the normal case, not the error case. And measured
 * numbers are formatted with `Locale.US`: the device locale is Persian and `String.format` would
 * otherwise emit Persian digits into a Fibonacci ratio, which is a market figure and takes Latin
 * digits.
 */
object DrawingGeometryB {

    /** Two pi, once, so no sampling loop below has to write it out and get it slightly wrong. */
    private const val TWO_PI = 2.0 * Math.PI

    /**
     * The marker a shape carries when its last leg ends in an arrowhead.
     *
     * Carried in [GeoPolyB.label] rather than in a boolean of its own, because the shape types are
     * shared with the first batch of geometry and could not grow a field for one tool. The renderer
     * compares against this constant; nothing else in the file writes it.
     */
    const val ARROW_HEAD: String = "arrowhead"

    /** A mark that reads upward: an arrow pointing up, or a bar that closed above its open. */
    const val MARK_UP: String = "up"

    /** A mark that reads downward: an arrow pointing down, or a bar that closed below its open. */
    const val MARK_DOWN: String = "down"

    /** Five anchors, because three drives is a five-pivot pattern. Later taps are ignored. */
    private const val THREE_DRIVES_ANCHORS = 5

    /** The five pivots of an Elliott triangle, in the order they are placed. */
    private val TRIANGLE_PIVOTS = listOf("A", "B", "C", "D", "E")

    /** The waves of a double combination: a correction, the connector, a second correction. */
    private val DOUBLE_COMBO_WAVES = listOf("W", "X", "Y")

    /** The waves of a triple combination. The second X is a connector like the first, not a typo. */
    private val TRIPLE_COMBO_WAVES = listOf("W", "X", "Y", "X", "Z")

    /** How tall an arrow mark stands, as a fraction of the average price step between anchors. */
    private const val ARROW_HEIGHT = 0.6

    /** How wide the two barbs of an arrow head spread, as a fraction of the average time step. */
    private const val ARROW_BARB_SPREAD = 0.2

    /** How far down the shaft the barbs meet it, as a fraction of the arrow's own height. */
    private const val ARROW_BARB_DROP = 0.35

    /** The height an arrow falls back to when every anchor sits at the same price. */
    private const val ARROW_FLAT_HEIGHT = 0.01

    // ══════════════════════════════════════════════════════════ harmonic and Elliott

    /**
     * [15] Three drives: the five-pivot zig-zag, with the ratio each leg actually measured.
     *
     * The textbook pattern retraces 0.618 and extends 1.272 twice over, and the temptation is to
     * print those two numbers on the legs. This prints what the reader drew instead. A three drives
     * that is trading is one whose measured ratios sit *near* the textbook ones, and a drawing that
     * labels every leg 0.618 no matter where it was dropped has thrown away the only information
     * the tool exists to give.
     *
     * The first leg is the measuring stick and is labelled 1.000 so it is obvious which one it is;
     * every later leg is labelled its price travel divided by the leg before it. A leg following a
     * flat one cannot be measured at all and is drawn without a label rather than with a zero.
     */
    fun threeDrives(points: List<GeoPointB>): List<GeoSegmentB> {
        if (points.size < 2) return emptyList()
        val anchors = points.take(THREE_DRIVES_ANCHORS)
        val legs = ArrayList<GeoSegmentB>(anchors.size - 1)
        var previousTravel = 0.0
        for (index in 0 until anchors.size - 1) {
            val from = anchors[index]
            val to = anchors[index + 1]
            val travel = abs(to.p - from.p)
            val label = when {
                index == 0 -> ratio(1.0)
                previousTravel == 0.0 -> null
                else -> ratio(travel / previousTravel)
            }
            legs += GeoSegmentB(from, to, label = label)
            previousTravel = travel
        }
        return legs
    }

    /**
     * [16] An Elliott triangle: five pivots A to E, four legs between them.
     *
     * Each leg is named by the pair of pivots it spans — "A-B", "B-C" and so on — rather than by a
     * single letter, and that is forced rather than decorative. There are five pivots and only four
     * legs, so a one-letter-per-leg scheme has to drop either A or E, and a reader who cannot point
     * at E cannot say where the triangle ended, which is the one thing a triangle is read for.
     */
    fun elliottTriangle(points: List<GeoPointB>): List<GeoSegmentB> {
        if (points.size < 2) return emptyList()
        val anchors = points.take(TRIANGLE_PIVOTS.size)
        return (0 until anchors.size - 1).map { index ->
            GeoSegmentB(
                a = anchors[index],
                b = anchors[index + 1],
                label = "${TRIANGLE_PIVOTS[index]}-${TRIANGLE_PIVOTS[index + 1]}",
            )
        }
    }

    /**
     * [17] A double combination, W-X-Y: two corrections joined by a connector.
     *
     * Five anchors and three wave names, so the leading leg carries no label. That is the leg *into*
     * the correction — the move being corrected — and naming it W would claim the correction started
     * one pivot earlier than the reader said it did.
     */
    fun elliottDoubleCombo(points: List<GeoPointB>): List<GeoSegmentB> =
        combination(points, DOUBLE_COMBO_WAVES)

    /**
     * [18] A triple combination, W-X-Y-X-Z: three corrections and the two connectors between them.
     *
     * Seven anchors for five waves, on the same arrangement as [elliottDoubleCombo]: the first leg
     * is the approach and is left unlabelled.
     */
    fun elliottTripleCombo(points: List<GeoPointB>): List<GeoSegmentB> =
        combination(points, TRIPLE_COMBO_WAVES)

    /**
     * The shared body of the two combination tools.
     *
     * Wave *n* is the leg arriving at anchor *n*, which is why the labels are read one behind the
     * leg index and the leg at index zero gets `null` from `getOrNull(-1)` without a special case.
     */
    private fun combination(points: List<GeoPointB>, waves: List<String>): List<GeoSegmentB> {
        if (points.size < 2) return emptyList()
        val anchors = points.take(waves.size + 2)
        return (0 until anchors.size - 1).map { index ->
            GeoSegmentB(anchors[index], anchors[index + 1], label = waves.getOrNull(index - 1))
        }
    }

    // ══════════════════════════════════════════════════════════ cycles, paths and curves

    /**
     * [19] Time cycles: the interval between the two anchors, repeated forward as vertical lines.
     *
     * Each line is a zero-length segment with both extend flags set and both ends at the same price.
     * That looks like a bug and is the opposite of one: this file has no viewport and therefore no
     * idea how tall the plot is, so "full height" can only be expressed as a position plus an
     * instruction to extend, and the renderer supplies the height. Giving the segment two different
     * prices here would be inventing a top and a bottom that the next zoom would falsify.
     *
     * The step is signed, so a reader who drags the second anchor to the left gets the cycle
     * repeating leftwards, which is how a cycle count back from a known high is read. A zero
     * interval would repeat the same line [count] times on top of itself, and returns nothing.
     */
    fun timeCycles(points: List<GeoPointB>, count: Int = 12): List<GeoSegmentB> {
        if (points.size < 2 || count <= 0) return emptyList()
        val step = points[1].t - points[0].t
        if (step == 0.0 || !step.isFinite()) return emptyList()
        val origin = points[0]
        return (0 until count).map { cycle ->
            val at = GeoPointB(origin.t + step * cycle, origin.p)
            GeoSegmentB(a = at, b = at, extendA = true, extendB = true, label = cycle.toString())
        }
    }

    /**
     * [20] A free path: every anchor joined in order, with an arrowhead on the last leg.
     *
     * The arrowhead is what separates this from [polyline]. A path is drawn to say "from here to
     * there", and the direction is the message; a polyline is drawn to outline a shape and pointing
     * it somewhere would be a claim the reader did not make.
     */
    fun path(points: List<GeoPointB>): GeoPolyB {
        if (points.size < 2) return GeoPolyB(emptyList())
        return GeoPolyB(points.toList(), closed = false, label = ARROW_HEAD)
    }

    /**
     * [21] A polyline through every anchor, closed back to the first if asked.
     *
     * Closing is the caller's decision rather than something inferred from the last anchor landing
     * near the first: on a zoomed-out chart two anchors a week apart are a few pixels apart, and a
     * tool that silently closed itself would be a tool that occasionally drew a shape nobody asked
     * for.
     */
    fun polyline(points: List<GeoPointB>, closed: Boolean): GeoPolyB {
        if (points.size < 2) return GeoPolyB(emptyList())
        return GeoPolyB(points.toList(), closed = closed)
    }

    /**
     * [22] A circular arc through three anchors, sampled into a polyline.
     *
     * The circle is the circumcircle of the three anchors, found from the determinant rather than by
     * intersecting two perpendicular bisectors, because the bisector form divides by a slope that is
     * infinite whenever two anchors share a time — and two anchors sharing a time is what happens
     * every time somebody drags straight up.
     *
     * Three anchors on one line have no circumcircle at all: the determinant is zero and the centre
     * runs off to infinity. Rather than divide by it, the three anchors are returned as they are,
     * which *is* the arc in that case — a straight line through all three. The test for collinearity
     * is relative to the size of the coordinates, because a chart time is around 1.7e9 and an
     * absolute epsilon that is sane for a price is meaningless next to it.
     *
     * The circle is round in (time, price) space and therefore an ellipse on screen, wider or
     * narrower as the reader zooms. That is inherent to storing the arc in chart space and is the
     * price of the arc staying anchored to its bars at all.
     */
    fun arc(points: List<GeoPointB>, samples: Int = 48): GeoPolyB {
        if (points.size < 3) return GeoPolyB(emptyList())
        val first = points[0]
        val middle = points[1]
        val last = points[2]
        val determinant = 2.0 * (
            first.t * (middle.p - last.p) +
                middle.t * (last.p - first.p) +
                last.t * (first.p - middle.p)
            )
        val magnitude = maxOf(
            abs(first.t), abs(middle.t), abs(last.t),
            abs(first.p), abs(middle.p), abs(last.p),
            1.0,
        )
        if (!determinant.isFinite() || abs(determinant) <= 1e-12 * magnitude * magnitude) {
            return GeoPolyB(listOf(first, middle, last))
        }
        val firstSquared = first.t * first.t + first.p * first.p
        val middleSquared = middle.t * middle.t + middle.p * middle.p
        val lastSquared = last.t * last.t + last.p * last.p
        val centreT = (
            firstSquared * (middle.p - last.p) +
                middleSquared * (last.p - first.p) +
                lastSquared * (first.p - middle.p)
            ) / determinant
        val centreP = (
            firstSquared * (last.t - middle.t) +
                middleSquared * (first.t - last.t) +
                lastSquared * (middle.t - first.t)
            ) / determinant
        val radius = hypot(first.t - centreT, first.p - centreP)
        val start = atan2(first.p - centreP, first.t - centreT)
        val through = turn(atan2(middle.p - centreP, middle.t - centreT) - start)
        val toEnd = turn(atan2(last.p - centreP, last.t - centreT) - start)
        // Anticlockwise from the first anchor unless the middle anchor is not reached that way, in
        // which case the arc runs the other way round. Without this the arc would take the short way
        // between the outer two anchors and miss the one the reader placed to shape it.
        val sweep = if (through <= toEnd) toEnd else toEnd - TWO_PI
        val steps = samples.coerceAtLeast(1)
        return GeoPolyB(
            (0..steps).map { step ->
                val angle = start + sweep * step / steps
                GeoPointB(centreT + radius * cos(angle), centreP + radius * sin(angle))
            },
        )
    }

    /**
     * [23] A quadratic Bézier from three anchors, sampled into a polyline.
     *
     * The middle anchor pulls the curve rather than sitting on it, which is the difference between
     * this and [arc] and is worth knowing before choosing between them: a reader who wants the line
     * to pass through a particular high wants the arc, and a reader who wants to bend a line away
     * from a cluster of bars wants this.
     */
    fun curve(points: List<GeoPointB>, samples: Int = 48): GeoPolyB {
        if (points.size < 3) return GeoPolyB(emptyList())
        val start = points[0]
        val control = points[1]
        val end = points[2]
        val steps = samples.coerceAtLeast(1)
        return GeoPolyB(
            (0..steps).map { step ->
                val u = step.toDouble() / steps
                val v = 1.0 - u
                GeoPointB(
                    t = v * v * start.t + 2.0 * v * u * control.t + u * u * end.t,
                    p = v * v * start.p + 2.0 * v * u * control.p + u * u * end.p,
                )
            },
        )
    }

    /**
     * [24] A cubic Bézier from four anchors, sampled into a polyline.
     *
     * Two control points instead of one, which buys the single thing a quadratic cannot draw: a
     * curve that changes which way it bends. That is the shape of a rounded top rolling into a
     * decline, and drawing it as two quadratics joined at the inflection leaves a visible corner at
     * the join.
     *
     * Sampled more finely than [curve] by default, because a curve with an inflection in it shows
     * its facets at a sample count a single bend hides.
     */
    fun doubleCurve(points: List<GeoPointB>, samples: Int = 64): GeoPolyB {
        if (points.size < 4) return GeoPolyB(emptyList())
        val start = points[0]
        val firstControl = points[1]
        val secondControl = points[2]
        val end = points[3]
        val steps = samples.coerceAtLeast(1)
        return GeoPolyB(
            (0..steps).map { step ->
                val u = step.toDouble() / steps
                val v = 1.0 - u
                val startWeight = v * v * v
                val firstWeight = 3.0 * v * v * u
                val secondWeight = 3.0 * v * u * u
                val endWeight = u * u * u
                GeoPointB(
                    t = startWeight * start.t + firstWeight * firstControl.t +
                        secondWeight * secondControl.t + endWeight * end.t,
                    p = startWeight * start.p + firstWeight * firstControl.p +
                        secondWeight * secondControl.p + endWeight * end.p,
                )
            },
        )
    }

    /**
     * [25] A small arrow at each anchor, pointing the way that anchor moved.
     *
     * Three segments per anchor — a shaft and two barbs, all of them meeting at the anchor itself,
     * so the arrow's tip marks the price rather than hovering above it. The shaft carries [MARK_UP]
     * or [MARK_DOWN] so the renderer can colour it without recomputing the comparison.
     *
     * "Fixed proportion" here means proportional to the drawing, not to the screen: the height is a
     * fraction of the average price step between anchors and the barb spread a fraction of the
     * average time step. An arrow sized in dollars and seconds would be a thumbnail at one zoom and
     * cover the plot at another. When every anchor sits at the same price there is no step to scale
     * by, and the height falls back to one percent of the price.
     *
     * The first anchor has nothing before it to compare against and points up. Guessing its
     * direction from the anchor *after* it would be labelling a move with the direction of a
     * different move.
     */
    fun arrowMarks(points: List<GeoPointB>): List<GeoSegmentB> {
        if (points.size < 2) return emptyList()
        var timeSteps = 0.0
        var priceSteps = 0.0
        for (index in 1 until points.size) {
            timeSteps += abs(points[index].t - points[index - 1].t)
            priceSteps += abs(points[index].p - points[index - 1].p)
        }
        val gaps = (points.size - 1).toDouble()
        val spread = ARROW_BARB_SPREAD * (timeSteps / gaps)
        val averageStep = priceSteps / gaps
        val height = if (averageStep > 0.0) {
            ARROW_HEIGHT * averageStep
        } else {
            ARROW_FLAT_HEIGHT * abs(points[0].p)
        }
        if (height == 0.0 || !height.isFinite() || !spread.isFinite()) return emptyList()
        val marks = ArrayList<GeoSegmentB>(points.size * 3)
        for (index in points.indices) {
            val at = points[index]
            val rising = index == 0 || at.p >= points[index - 1].p
            val direction = if (rising) 1.0 else -1.0
            val tail = at.p - direction * height
            val barb = at.p - direction * ARROW_BARB_DROP * height
            marks += GeoSegmentB(
                a = GeoPointB(at.t, tail),
                b = at,
                label = if (rising) MARK_UP else MARK_DOWN,
            )
            marks += GeoSegmentB(GeoPointB(at.t - spread, barb), at)
            marks += GeoSegmentB(GeoPointB(at.t + spread, barb), at)
        }
        return marks
    }

    // ══════════════════════════════════════════════════════════ replaying the chart

    /**
     * [31] Bars pattern: the shape of one window of the chart, re-plotted from an anchor.
     *
     * The whole value of the tool is in what it does *not* preserve. The window's shape — the size
     * of each bar relative to the next, and the distance between them — is copied exactly; the price
     * level is thrown away and replaced by wherever the reader dropped the anchor. That is what lets
     * a reader ask whether the run-up of three months ago is repeating here, at a price four hundred
     * dollars lower, which is a question a copy that kept its own level cannot even be asked.
     *
     * The normalisation is anchored on the *close* of the first copied bar, not on its high, low or
     * midpoint: a reader dropping this on a bar is pointing at a level the market settled at, and
     * that level is the close. So the first bar's close lands exactly on [anchor], and every other
     * price in the window keeps its distance from that close, multiplied by [scale].
     *
     * Each bar comes back as a high-low stick, labelled [MARK_UP] or [MARK_DOWN] by whether it
     * closed above its open, so the renderer can colour the copy the way it colours the real bars.
     * The sticks are laid one time unit apart from the anchor, because this function is given no bar
     * spacing and cannot invent one; the caller multiplies by whatever its timeframe is worth.
     *
     * A window that is empty, inverted or entirely off the end of the arrays returns nothing rather
     * than clamping to a single bar, because a one-bar pattern is not a pattern and would be a
     * silent answer to a question that had no answer.
     */
    fun barsPattern(
        source: DoubleArray,
        sourceOpen: DoubleArray,
        sourceHigh: DoubleArray,
        sourceLow: DoubleArray,
        fromIndex: Int,
        toIndex: Int,
        anchor: GeoPointB,
        scale: Double = 1.0,
    ): List<GeoSegmentB> {
        if (!scale.isFinite()) return emptyList()
        val lastUsable = minOf(source.size, sourceOpen.size, sourceHigh.size, sourceLow.size) - 1
        if (lastUsable < 0) return emptyList()
        if (fromIndex > toIndex || toIndex < 0 || fromIndex > lastUsable) return emptyList()
        val from = fromIndex.coerceIn(0, lastUsable)
        val to = toIndex.coerceIn(0, lastUsable)
        val base = source[from]
        if (!base.isFinite()) return emptyList()
        return (from..to).map { index ->
            val at = anchor.t + (index - from)
            val high = anchor.p + (sourceHigh[index] - base) * scale
            val low = anchor.p + (sourceLow[index] - base) * scale
            GeoSegmentB(
                a = GeoPointB(at, low),
                b = GeoPointB(at, high),
                label = if (source[index] >= sourceOpen[index]) MARK_UP else MARK_DOWN,
            )
        }
    }

    /**
     * [32] Ghost feed: the chart continued forward by replaying the returns of an earlier window.
     *
     * Returns rather than differences, and that is the whole design. Replaying "the price rose four
     * dollars a bar" from a window recorded at forty dollars onto a chart now at four thousand draws
     * a flat line; replaying "the price rose one percent a bar" draws the same *move*, which is what
     * the reader meant by "what if this happens again". It is also why the source window and the
     * anchor need share no level at all.
     *
     * If [bars] outruns the window the returns cycle from the beginning, so the projection keeps its
     * character instead of stopping mid-move. A window containing a zero or a non-finite close
     * cannot yield a return and that pair is skipped rather than producing an infinity that would
     * take the rest of the projection with it.
     */
    fun ghostFeed(
        closes: DoubleArray,
        fromIndex: Int,
        toIndex: Int,
        anchor: GeoPointB,
        bars: Int,
    ): GeoPolyB {
        if (bars <= 0) return GeoPolyB(emptyList())
        val lastUsable = closes.size - 1
        if (lastUsable < 1 || fromIndex >= toIndex || toIndex < 1 || fromIndex > lastUsable) {
            return GeoPolyB(emptyList())
        }
        val from = fromIndex.coerceIn(0, lastUsable)
        val to = toIndex.coerceIn(0, lastUsable)
        if (to - from < 1) return GeoPolyB(emptyList())
        val returns = ArrayList<Double>(to - from)
        for (index in from + 1..to) {
            val previous = closes[index - 1]
            val current = closes[index]
            if (previous == 0.0 || !previous.isFinite() || !current.isFinite()) continue
            returns += current / previous
        }
        if (returns.isEmpty()) return GeoPolyB(emptyList())
        val projection = ArrayList<GeoPointB>(bars + 1)
        var price = anchor.p
        projection += GeoPointB(anchor.t, price)
        for (step in 0 until bars) {
            price *= returns[step % returns.size]
            projection += GeoPointB(anchor.t + (step + 1), price)
        }
        return GeoPolyB(projection)
    }

    /**
     * [33] A pie sector: centre at the first anchor, radius and start angle from the second, swept
     * to the third.
     *
     * The sweep is the *short* way round, never more than half a turn. The alternative — always
     * anticlockwise — makes a reader who drags a few degrees the wrong way get a wedge of nearly a
     * full turn, and no gesture distinguishes the two intents, so the sector that can actually be
     * drawn deliberately is the one worth having.
     *
     * The returned poly starts at the centre and is closed, so the renderer fills it as a wedge
     * rather than stroking it as an arc with two loose ends. Two anchors in the same place give a
     * radius of zero and nothing is returned; a third anchor on top of the centre has no angle at
     * all and is treated the same way.
     */
    fun sector(points: List<GeoPointB>, samples: Int = 32): GeoPolyB {
        if (points.size < 3) return GeoPolyB(emptyList())
        val centre = points[0]
        val edge = points[1]
        val target = points[2]
        val radius = hypot(edge.t - centre.t, edge.p - centre.p)
        if (radius == 0.0 || !radius.isFinite()) return GeoPolyB(emptyList())
        if (target.t == centre.t && target.p == centre.p) return GeoPolyB(emptyList())
        val start = atan2(edge.p - centre.p, edge.t - centre.t)
        val anticlockwise = turn(atan2(target.p - centre.p, target.t - centre.t) - start)
        val sweep = if (anticlockwise > Math.PI) anticlockwise - TWO_PI else anticlockwise
        val steps = samples.coerceAtLeast(1)
        val wedge = ArrayList<GeoPointB>(steps + 2)
        wedge += centre
        for (step in 0..steps) {
            val angle = start + sweep * step / steps
            wedge += GeoPointB(centre.t + radius * cos(angle), centre.p + radius * sin(angle))
        }
        return GeoPolyB(wedge, closed = true)
    }

    // ══════════════════════════════════════════════════════════ helpers

    /**
     * A measured ratio, as three decimals in Latin digits.
     *
     * `Locale.US` is not decoration: the device locale is Persian, and `String.format` without it
     * renders a Fibonacci ratio in Persian digits. A ratio is a market figure and takes Latin ones.
     */
    private fun ratio(value: Double): String = String.format(Locale.US, "%.3f", value)

    /** An angle brought into a single anticlockwise turn, `[0, 2π)`, whatever it started as. */
    private fun turn(angle: Double): Double = ((angle % TWO_PI) + TWO_PI) % TWO_PI
}
