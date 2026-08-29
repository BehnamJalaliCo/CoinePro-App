package com.coinepro.core.chart

import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A point handed to, and returned by, [DrawingGeometryA] — a moment and a price, both as doubles.
 *
 * The same chart-space arrangement [ChartPoint] rests on, and deliberately not the same type. A
 * pitchfork's handle is the midpoint of two anchors, and a midpoint of two `Long` times is a
 * rounded time: place the fork, save it, reload it, and the tines have moved by half a bar. Working
 * in doubles all the way through the geometry and rounding once, at the renderer, is what stops
 * that. The conversion is a constructor call at each end and costs nothing.
 */
data class GeoPoint(val t: Double, val p: Double)

/**
 * One straight run of a tool, in chart space.
 *
 * [extendA] and [extendB] say the line does not stop at its endpoint — it carries on past that end
 * to the edge of the plot. They are flags rather than a pre-extended second point on purpose: how
 * far "the edge" is depends on the viewport, which this file cannot see and must not learn about,
 * and a geometry that guessed a reach in chart space would produce a line that stops in mid-air the
 * moment the reader zooms out.
 *
 * [label] carries the ratio the run represents where the tool has one — a Fibonacci level, a Gann
 * slope, a standard-deviation count — already formatted with Latin digits, because these are market
 * figures. It is null for the runs that are structure rather than a level: a channel's sides, a
 * pitchfork's base bar, the edges of a Gann box.
 */
data class GeoSegment(
    val a: GeoPoint,
    val b: GeoPoint,
    val extendA: Boolean = false,
    val extendB: Boolean = false,
    val label: String? = null,
)

/**
 * One arc of a tool, in chart space.
 *
 * Two radii and not one. Chart space has a time axis and a price axis with no common unit, so a
 * circle here is an ellipse there and the reverse; carrying [radiusT] and [radiusP] separately lets
 * the renderer scale each by its own axis and get the shape the reader drew, at every zoom. A
 * single radius would have to pick an axis to be measured in, and the arc would breathe in and out
 * as the price scale changed.
 *
 * [startDeg] and [sweepDeg] are degrees in the usual mathematical sense — zero along increasing
 * time, growing toward increasing price — with a negative [sweepDeg] meaning the arc runs the other
 * way round. [label] carries the arc's ratio where it has one.
 */
data class GeoArc(
    val centre: GeoPoint,
    val radiusT: Double,
    val radiusP: Double,
    val startDeg: Double,
    val sweepDeg: Double,
    val label: String? = null,
)

/**
 * The geometry of eleven of the terminal's harder drawing tools, as pure arithmetic.
 *
 * Nothing here knows about Compose, a canvas or a pixel: every function takes chart-space anchors
 * and returns chart-space runs and arcs, and the renderer is what turns those into a path. That
 * separation is the point of the file. The maths in a pitchfork is where these tools are wrong or
 * right, it is the part a reader will compare against another terminal, and it is exactly the part
 * that cannot be checked at all once it is tangled up in a `DrawScope`. Kept pure, all of it sits
 * under `DrawingGeometryATest` with real coordinates asserted to a delta.
 *
 * Every function guards its own degenerate input by returning an empty list. A tool is placed one
 * tap at a time, so the half-placed state — one anchor of three, two of four — is not an error to
 * be thrown at anybody, it is the normal case for as long as the reader's finger is down. Likewise
 * two anchors on the same spot, or a regression window with nothing in it: those produce no
 * drawable geometry, and an empty list is precisely what "there is nothing to draw yet" means.
 *
 * No TradingView code or asset was used. Every construction here is reimplemented from the
 * published definition of the tool, the same provenance the rest of the drawing layer carries.
 */
object DrawingGeometryA {

    /**
     * The retracement ladder every Fibonacci tool in the app shares.
     *
     * One list rather than a per-tool list, because a reader who reads 0.618 off a wedge and 0.618
     * off a channel is entitled to assume the two agree about what 0.618 is.
     */
    val FIB_RATIOS: List<Double> = listOf(0.236, 0.382, 0.5, 0.618, 0.786, 1.0)

    /**
     * Where a pitchfan puts its rays between the median line and the outer rails.
     *
     * The five published fan ratios, plus 1.0 — which is not a sixth ratio but the outer rails
     * themselves, reached by the same formula. Folding them in rather than drawing them separately
     * is what stops the outer ray and the 0.75 ray being computed two different ways and drifting
     * apart at wide zoom.
     */
    val PITCHFAN_RATIOS: List<Double> = listOf(0.25, 0.382, 0.5, 0.618, 0.75, 1.0)

    /**
     * The seven Gann slopes, as time-by-price multiples of the drawn box and in the order Gann
     * lists them: the 1×1 first because it is the one that matters, then the pairs outward.
     */
    private val GANN_SLOPES: List<Triple<Double, Double, String>> = listOf(
        Triple(1.0, 1.0, "1x1"),
        Triple(1.0, 2.0, "1x2"),
        Triple(2.0, 1.0, "2x1"),
        Triple(1.0, 4.0, "1x4"),
        Triple(4.0, 1.0, "4x1"),
        Triple(1.0, 8.0, "1x8"),
        Triple(8.0, 1.0, "8x1"),
    )

    /** The golden ratio, which is what makes a Fibonacci spiral a Fibonacci spiral. */
    private val PHI: Double = (1.0 + sqrt(5.0)) / 2.0

    // ── [4] regression channel ────────────────────────────────────────────────────────

    /**
     * [4] The least-squares line through a window of closes, with a rail either side.
     *
     * The fit is over the closes themselves and not over the two anchors: a regression channel that
     * merely joined the points the reader tapped would be a trend line with extra steps, and the
     * whole claim of the tool is that the line is where the *data* says it is and the reader only
     * chose the window. The anchors supply the window's chart-space span, so index zero of the
     * window lands on `points[0]`'s time and the last index on `points[1]`'s.
     *
     * The rails sit [deviations] standard deviations of the residuals above and below, measured as
     * a population standard deviation — divided by the count, not by the count less one. That is the
     * convention the web terminal shipped and the one every screenshot of this tool was taken
     * against; switching to the sample estimator would widen every channel in the app by a few
     * tenths of a percent and make two products disagree about a price for no reader-visible reason.
     *
     * Returns the centre line first, then the upper rail, then the lower, each labelled with its own
     * deviation count. Empty if there are fewer than two anchors, if the window falls outside
     * [closes], or if the window holds fewer than two bars — a line through one point is not a fit.
     */
    fun regressionChannel(
        points: List<GeoPoint>,
        closes: DoubleArray,
        fromIndex: Int,
        toIndex: Int,
        deviations: Double = 2.0,
    ): List<GeoSegment> {
        if (points.size < 2) return emptyList()
        if (fromIndex < 0 || toIndex >= closes.size || toIndex <= fromIndex) return emptyList()
        val count = toIndex - fromIndex + 1
        var sumX = 0.0
        var sumY = 0.0
        var sumXX = 0.0
        var sumXY = 0.0
        for (step in 0 until count) {
            val y = closes[fromIndex + step]
            val x = step.toDouble()
            sumX += x
            sumY += y
            sumXX += x * x
            sumXY += x * y
        }
        val denominator = count * sumXX - sumX * sumX
        if (denominator == 0.0) return emptyList()
        val slope = (count * sumXY - sumX * sumY) / denominator
        val intercept = (sumY - slope * sumX) / count
        var squares = 0.0
        for (step in 0 until count) {
            val residual = closes[fromIndex + step] - (intercept + slope * step)
            squares += residual * residual
        }
        val offset = deviations * sqrt(squares / count)
        val startT = points[0].t
        val endT = points[1].t
        val startPrice = intercept
        val endPrice = intercept + slope * (count - 1)
        return listOf(
            GeoSegment(GeoPoint(startT, startPrice), GeoPoint(endT, endPrice), label = ratioLabel(0.0)),
            GeoSegment(
                GeoPoint(startT, startPrice + offset),
                GeoPoint(endT, endPrice + offset),
                label = ratioLabel(deviations),
            ),
            GeoSegment(
                GeoPoint(startT, startPrice - offset),
                GeoPoint(endT, endPrice - offset),
                label = ratioLabel(-deviations),
            ),
        )
    }

    // ── [5] flat top / bottom ─────────────────────────────────────────────────────────

    /**
     * [5] A trend line and a horizontal rail, the shape of an ascending or descending triangle.
     *
     * Three anchors: the first two set the sloping side, the third sets nothing but a price. That is
     * the whole tool and it is why the third anchor's *time* is thrown away here — a reader placing
     * the flat side is choosing a level, and honouring the time they happened to tap at would tilt
     * the rail by a hair and turn a flat top into a very shallow trend line.
     *
     * Both runs extend to the right, because the pattern is read into the bars that have not printed
     * yet: the interest is entirely in where the two sides will meet. Empty if there are fewer than
     * three anchors, or if the first two coincide and there is no slope to draw.
     */
    fun flatTopBottom(points: List<GeoPoint>): List<GeoSegment> {
        if (points.size < 3) return emptyList()
        val first = points[0]
        val second = points[1]
        val level = points[2].p
        if (first == second) return emptyList()
        return listOf(
            GeoSegment(first, second, extendB = true),
            GeoSegment(GeoPoint(first.t, level), GeoPoint(second.t, level), extendB = true),
        )
    }

    // ── [6] disjoint channel ──────────────────────────────────────────────────────────

    /**
     * [6] Two trend lines that are not parallel, closed into a channel by their sides.
     *
     * A parallel channel takes three anchors and derives the second rail; this one takes four and
     * derives nothing, which is the point — a reader who can see that the highs and the lows are
     * converging wants to draw exactly that, and a tool that forced the second rail parallel would
     * be answering a different question.
     *
     * The two sides join anchor to anchor — first-to-third and second-to-fourth — rather than
     * dropping true verticals. Two independently placed lines rarely start or end on the same bar,
     * so a vertical would have to pick one of the two times and clip the other line short of the
     * anchor the reader actually put there. Empty below four anchors, or if either line is a point.
     */
    fun disjointChannel(points: List<GeoPoint>): List<GeoSegment> {
        if (points.size < 4) return emptyList()
        val (first, second, third, fourth) = points
        if (first == second || third == fourth) return emptyList()
        return listOf(
            GeoSegment(first, second),
            GeoSegment(third, fourth),
            GeoSegment(first, third),
            GeoSegment(second, fourth),
        )
    }

    // ── [7]–[10] the pitchfork family ─────────────────────────────────────────────────

    /**
     * [7] An inside pitchfork: the handle starts at the **midpoint of the first and second anchors**.
     *
     * Both coordinates of that midpoint, time and price. Pulling the handle forward into the middle
     * of the first leg shortens the median and steepens it relative to Andrews' original, which is
     * the effect the variant exists for — it tracks a move that began before the reader's first
     * anchor rather than assuming the pivot is the beginning of everything.
     *
     * Returns the base bar joining the second and third anchors, then the median line, then the two
     * tines through the second and third anchors, all three of the latter extended forward. Empty
     * below three anchors, or if the handle lands on the median's own midpoint.
     */
    fun insidePitchfork(points: List<GeoPoint>): List<GeoSegment> =
        fork(points) { first, second, _, _ -> midpoint(first, second) }

    /**
     * [8] A Schiff pitchfork: the handle starts at the midpoint of the first anchor and the median's
     * base **in price only**, keeping the first anchor's time.
     *
     * This is the distinction that is misread most often, so it is worth being blunt about it: the
     * Schiff handle sits directly above or below the reader's first anchor, on the same bar, at half
     * the price distance to the base. It has *not* moved forward in time. Schiff's correction was to
     * the pivot's price — a spike low drags the classic median down with it, and halving the price
     * gap is what pulls the fork back onto the body of the move. Moving the time as well is the
     * *modified* Schiff, [modifiedSchiffPitchfork], and produces a visibly different fork.
     *
     * Same four runs as the rest of the family. Empty below three anchors, or if the handle lands on
     * the median's own midpoint.
     */
    fun schiffPitchfork(points: List<GeoPoint>): List<GeoSegment> =
        fork(points) { first, _, _, median -> GeoPoint(first.t, (first.p + median.p) / 2.0) }

    /**
     * [9] A modified Schiff pitchfork: the handle starts at the midpoint of the first anchor and the
     * median's base **in both time and price**.
     *
     * The one difference from [schiffPitchfork] is the time coordinate, and it is not cosmetic: the
     * handle moves forward to halfway between the pivot and the base bar as well as halfway down,
     * which shortens the median without changing where it ends. The result is a wider fork with the
     * same base, and on a long first leg the two variants disagree about the tines by more than the
     * width of the price scale.
     *
     * Same four runs as the rest of the family. Empty below three anchors, or if the handle lands on
     * the median's own midpoint.
     */
    fun modifiedSchiffPitchfork(points: List<GeoPoint>): List<GeoSegment> =
        fork(points) { first, _, _, median ->
            GeoPoint((first.t + median.t) / 2.0, (first.p + median.p) / 2.0)
        }

    /**
     * [10] A pitchfan: Andrews' classic handle — **the first anchor, untouched** — with the two
     * parallel tines replaced by a fan of rays.
     *
     * The handle is the plain one, the same as a classic Andrews fork, so the fan and the fork read
     * off the same pivot and can be swapped for one another without the median moving. What changes
     * is everything past the base: instead of two rails parallel to the median, every ray leaves the
     * handle and passes through a point on the base bar at one of [PITCHFAN_RATIOS]. Rays diverge
     * where rails stay a fixed distance apart, which is the reason to reach for a fan — it says the
     * channel should widen with time rather than hold its width.
     *
     * Returns the base bar, then the median labelled 0, then a pair of rays per ratio: the one
     * toward the second anchor first, then the one toward the third. The pair at 1.0 is the outer
     * rails, reaching the anchors themselves. Empty below three anchors, or if the handle sits on
     * the base's midpoint and there is no median to fan around.
     */
    fun pitchfan(points: List<GeoPoint>): List<GeoSegment> {
        if (points.size < 3) return emptyList()
        val (handle, second, third) = points
        val median = midpoint(second, third)
        if (handle == median) return emptyList()
        val runs = ArrayList<GeoSegment>(2 + PITCHFAN_RATIOS.size * 2)
        runs += GeoSegment(second, third)
        runs += GeoSegment(handle, median, extendB = true, label = ratioLabel(0.0))
        for (ratio in PITCHFAN_RATIOS) {
            val text = ratioLabel(ratio)
            for (target in listOf(second, third)) {
                val through = GeoPoint(
                    median.t + (target.t - median.t) * ratio,
                    median.p + (target.p - median.p) * ratio,
                )
                runs += GeoSegment(handle, through, extendB = true, label = text)
            }
        }
        return runs
    }

    // ── [11]–[12] the two Fibonacci curves ────────────────────────────────────────────

    /**
     * [11] A Fibonacci spiral: quarter-turn arcs whose radii grow by the golden ratio.
     *
     * The drag from the first anchor to the second is the spiral's first quarter. Its two components
     * become the arc's two radii — the time span is the radius along time, the price span the radius
     * along price — and its signs choose the quadrant the spiral opens into, so a drag up and to the
     * right winds anticlockwise from the time axis and a drag down winds the other way. That is what
     * "oriented by the direction of the first leg" means here, and it is the only thing the second
     * anchor is used for.
     *
     * Each quarter after the first is φ times the one before, and its centre is stepped so the new
     * arc begins exactly where the old one ended: the centre moves along the shared end radius by
     * the difference of the two radii. Computing the centres by that recurrence rather than from a
     * closed form is what keeps the curve continuous — a spiral assembled from independently
     * positioned arcs shows a visible kink at every quarter, and on a chart a kink reads as a level.
     *
     * [turns] full turns means four times that many arcs, each labelled with its radius as a
     * multiple of the first. Empty below two anchors, if [turns] is not positive, or if the drag has
     * no extent along either axis — a spiral needs both a width and a height.
     */
    fun fibonacciSpiral(points: List<GeoPoint>, turns: Int = 4): List<GeoArc> {
        if (points.size < 2 || turns < 1) return emptyList()
        val origin = points[0]
        val deltaT = points[1].t - origin.t
        val deltaP = points[1].p - origin.p
        if (deltaT == 0.0 || deltaP == 0.0) return emptyList()
        val sweep = if (deltaP >= 0.0) QUARTER_TURN else -QUARTER_TURN
        var start = if (deltaT >= 0.0) 0.0 else 180.0
        var radiusT = abs(deltaT)
        var radiusP = abs(deltaP)
        var centre = origin
        var multiple = 1.0
        val arcs = ArrayList<GeoArc>(turns * 4)
        repeat(turns * 4) {
            arcs += GeoArc(centre, radiusT, radiusP, start, sweep, ratioLabel(multiple))
            val endDeg = start + sweep
            val endRad = endDeg * PI / 180.0
            val nextT = radiusT * PHI
            val nextP = radiusP * PHI
            centre = GeoPoint(
                centre.t + (radiusT - nextT) * cos(endRad),
                centre.p + (radiusP - nextP) * sin(endRad),
            )
            radiusT = nextT
            radiusP = nextP
            start = endDeg
            multiple *= PHI
        }
        return arcs
    }

    /**
     * [12] A Fibonacci wedge: arcs at the retracement ratios, swept between two rays out of a shared
     * apex.
     *
     * Three anchors — the apex, then a point on each ray. The first ray sets the scale, so the arc
     * at 1.0 reaches the second anchor's distance from the apex and the rest fall proportionally
     * inside it; the second ray only says how far round to sweep. Every arc therefore shares the
     * apex as its centre, which is what makes the wedge a wedge rather than a stack of unrelated
     * curves.
     *
     * The sweep is normalised into ±180°, so the wedge always fills the narrow angle between the two
     * rays rather than the reflex one on the other side. Without that normalisation a wedge drawn
     * anticlockwise wraps the long way round the apex and paints over most of the chart.
     *
     * Empty below three anchors, or if either ray has no length.
     */
    fun fibonacciWedge(points: List<GeoPoint>): List<GeoArc> {
        if (points.size < 3) return emptyList()
        val (apex, onFirst, onSecond) = points
        if (apex == onFirst || apex == onSecond) return emptyList()
        val startDeg = degreesOf(onFirst.t - apex.t, onFirst.p - apex.p)
        val endDeg = degreesOf(onSecond.t - apex.t, onSecond.p - apex.p)
        val sweep = normaliseDegrees(endDeg - startDeg)
        val spanT = abs(onFirst.t - apex.t)
        val spanP = abs(onFirst.p - apex.p)
        return FIB_RATIOS.map { ratio ->
            GeoArc(apex, spanT * ratio, spanP * ratio, startDeg, sweep, ratioLabel(ratio))
        }
    }

    // ── [13]–[14] the Gann squares ────────────────────────────────────────────────────

    /**
     * [13] A Gann square: the seven angles out of a corner, the box that scales them, and the box's
     * internal Fibonacci grid.
     *
     * The two anchors are opposite corners, and the box between them is the unit — a 1×1 line is
     * exactly the box's diagonal, a 2×1 covers two box widths in one box height, and so on through
     * the seven. Deriving all seven from the drawn box rather than from a fixed price-per-bar is the
     * only way a Gann tool can mean anything on a chart whose axes have no common unit: the reader
     * declares what one unit of time is worth in price by dragging the box, and every angle follows.
     *
     * The box is signed, so a drag down and to the left produces the mirror image rather than the
     * same fan flipped into the wrong quadrant. The angles are rays and extend forward; the edges
     * and the grid are bounded by the box, since a grid line that ran on would stop being a grid.
     *
     * Returns the seven angles labelled `1x1` through `8x1`, then the four edges, then the grid as a
     * time line and a price line per ratio. Empty below two anchors, or if the box is flat on either
     * axis and every angle would collapse onto the same line.
     */
    fun gannSquare(points: List<GeoPoint>): List<GeoSegment> {
        if (points.size < 2) return emptyList()
        val origin = points[0]
        return gannSquareFixed(points, points[1].t - origin.t, points[1].p - origin.p)
    }

    /**
     * [14] A Gann square whose box is stated rather than dragged.
     *
     * The same construction as [gannSquare] and deliberately the same code, because the two must not
     * be able to drift apart: a reader who drags a box and a reader who types its size are drawing
     * the same object, and a bug fixed in one that survived in the other would be a bug nobody could
     * reproduce. Only the first anchor is read — [boxT] and [boxP] supply what the second anchor
     * would have.
     *
     * Both box dimensions are signed, so a negative [boxP] squares downward from the anchor. Empty
     * if there is no anchor at all, or if either dimension is zero.
     */
    fun gannSquareFixed(points: List<GeoPoint>, boxT: Double, boxP: Double): List<GeoSegment> {
        if (points.isEmpty() || boxT == 0.0 || boxP == 0.0) return emptyList()
        val origin = points[0]
        val far = GeoPoint(origin.t + boxT, origin.p + boxP)
        val runs = ArrayList<GeoSegment>(GANN_SLOPES.size + 4 + FIB_RATIOS.size * 2)
        for ((timeUnits, priceUnits, name) in GANN_SLOPES) {
            val reach = GeoPoint(origin.t + boxT * timeUnits, origin.p + boxP * priceUnits)
            runs += GeoSegment(origin, reach, extendB = true, label = name)
        }
        runs += GeoSegment(origin, GeoPoint(far.t, origin.p))
        runs += GeoSegment(GeoPoint(far.t, origin.p), far)
        runs += GeoSegment(far, GeoPoint(origin.t, far.p))
        runs += GeoSegment(GeoPoint(origin.t, far.p), origin)
        for (ratio in FIB_RATIOS) {
            if (ratio >= 1.0) continue
            val text = ratioLabel(ratio)
            val atTime = origin.t + boxT * ratio
            val atPrice = origin.p + boxP * ratio
            runs += GeoSegment(GeoPoint(atTime, origin.p), GeoPoint(atTime, far.p), label = text)
            runs += GeoSegment(GeoPoint(origin.t, atPrice), GeoPoint(far.t, atPrice), label = text)
        }
        return runs
    }

    // ── shared ────────────────────────────────────────────────────────────────────────

    /**
     * The one construction the four pitchfork variants share, parameterised by the only thing that
     * differs between them.
     *
     * Written once on purpose. The four differ in a single expression — where the handle starts —
     * and the temptation to write four similar functions is exactly how a fix to the tine reach
     * lands in three of them and a reader finds the fourth disagreeing with the other three.
     */
    private fun fork(
        points: List<GeoPoint>,
        handleOf: (GeoPoint, GeoPoint, GeoPoint, GeoPoint) -> GeoPoint,
    ): List<GeoSegment> {
        if (points.size < 3) return emptyList()
        val (first, second, third) = points
        val median = midpoint(second, third)
        val handle = handleOf(first, second, third, median)
        val runT = median.t - handle.t
        val runP = median.p - handle.p
        if (runT == 0.0 && runP == 0.0) return emptyList()
        return listOf(
            GeoSegment(second, third),
            GeoSegment(handle, median, extendB = true),
            GeoSegment(second, GeoPoint(second.t + runT, second.p + runP), extendB = true),
            GeoSegment(third, GeoPoint(third.t + runT, third.p + runP), extendB = true),
        )
    }

    /** The point halfway between two, in both axes. */
    private fun midpoint(a: GeoPoint, b: GeoPoint): GeoPoint =
        GeoPoint((a.t + b.t) / 2.0, (a.p + b.p) / 2.0)

    /** The direction of a chart-space run, in degrees from the time axis. */
    private fun degreesOf(deltaT: Double, deltaP: Double): Double =
        atan2(deltaP, deltaT) * 180.0 / PI

    /**
     * The same angle, brought into ±180°.
     *
     * A wedge is the narrow angle between its two rays, never the reflex one, and the difference of
     * two `atan2` results can easily land outside that range.
     */
    private fun normaliseDegrees(degrees: Double): Double {
        var value = degrees
        while (value > 180.0) value -= 360.0
        while (value <= -180.0) value += 360.0
        return value
    }

    /**
     * A ratio, written the way a market figure is written: Latin digits, no trailing noise.
     *
     * [Locale.US] is not optional here. The device locale is Persian, and `%.3f` against it emits
     * Persian digits — which would put «۰٫۶۱۸» on a Fibonacci level, a number that is correct and
     * that no chart in this category has ever shown. That has already caused one bug in this app.
     */
    private fun ratioLabel(value: Double): String {
        val text = String.format(Locale.US, "%.3f", value)
        val trimmed = text.trimEnd('0')
        return if (trimmed.endsWith('.')) trimmed.dropLast(1) else trimmed
    }

    /** A quarter turn, which is the arc a Fibonacci spiral advances by at a time. */
    private const val QUARTER_TURN = 90.0
}
