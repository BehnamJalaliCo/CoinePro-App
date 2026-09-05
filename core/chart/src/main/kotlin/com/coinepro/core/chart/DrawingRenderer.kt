package com.coinepro.core.chart

import android.graphics.BitmapFactory
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * The fifty drawing tools, drawn.
 *
 * This is the port of the web terminal's `drawings.js` and `drawtools_ext.js` — the geometry, not
 * the plumbing. Every formula here is the one that shipped in Pro Chart, which matters for a reason
 * beyond saving work: a reader who has drawn a Gann fan in the web terminal and draws one here has
 * to get the same nine rays at the same nine angles, or the two products disagree about the market.
 *
 * Nothing in this file computes a coordinate. Every screen position comes from [ChartViewport],
 * which is what lets a drawing survive a pan, a zoom, a chart-type switch and a rotation without a
 * single tool knowing that any of those happened. Points are stored as (time, price) and converted
 * here, once, per frame.
 *
 * The web original's provenance note travels with it: no TradingView code or asset was used, and
 * every formula was reimplemented from the published definition of the tool.
 */

/**
 * Draw one placed drawing.
 *
 * Returns whether the tool was recognised. [CoineProChart] ignores the answer — there is nothing
 * useful it could do with it mid-frame — but the screenshot test does not: it draws all fifty and
 * fails if any returns false, which is what stops a tool reaching the rail with no way to render it.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod")
fun DrawScope.drawDrawing(
    drawing: Drawing,
    view: ChartViewport,
    measurer: TextMeasurer,
    selected: Boolean = false,
    /**
     * The moment this frame is being drawn at, for the marks that expire.
     *
     * Defaulted rather than required, because only demonstration marks read it and every other
     * caller would be passing a clock to be ignored. The trap it hides is worth naming: a fade
     * computed at draw time only advances when something repaints, so a chart sitting perfectly
     * still shows a mark at the opacity it had on the last frame. On a live feed that is a tick
     * away; on a frozen one the mark is still removed by `DrawingActions.expire`, which is the
     * model's own answer and does not wait for a frame.
     */
    nowMillis: Long = System.currentTimeMillis(),
    /**
     * Where the image tool's pictures come from.
     *
     * Defaulted to the process-wide [DrawingImages] for the same reason [nowMillis] is defaulted to
     * the clock: every caller but a test would be passing the one answer there is. The seam is here
     * so a test can hand a picture in without a file, a decoder or a device — and so that the day
     * the app holds two independent picture stores, this does not have to become a global again.
     */
    images: DrawingImageSource = DrawingImages,
    /**
     * What a drawing's own labels are plated with.
     *
     * The stage colour, from the caller, because this renderer has no palette and threading one
     * through every shape for a plate would be a wide change for a narrow need. Unspecified draws
     * no plate at all, which is what a caller with no theme to hand gets — a test, or a preview.
     *
     * The plate is why it exists: a Fibonacci retracement puts «2562.7 50.0%» straight over the
     * bars, and a price in the tool's own blue on top of a red candle is not a figure, it is a
     * smudge. The setup levels solved this the same way; see `drawLevelLabel`.
     */
    plate: Color = Color.Unspecified,
    /**
     * Which handle of this drawing a finger is holding, or −1 for none.
     *
     * The handle a reader is dragging is, by construction, the one thing on the chart they cannot
     * see: it is underneath the fingertip. A five-point dot with a fingertip on it gives no
     * feedback at all about *where* it landed, which is why placing a level exactly on a wick is
     * so much harder on a phone than with a mouse — the reader is aiming at something they last
     * saw a frame before they covered it.
     *
     * Growing the held handle puts its ring back out past the edge of the finger, so what the
     * reader sees is a ring around their own fingertip with the price line running through it.
     * That ring is the readout: the line's position relative to the ring's centre says whether the
     * anchor is above or below where they meant it, and it is visible without lifting off.
     *
     * Only ever one, and only on the selected drawing. Every handle growing would be an animation
     * that says a state changed rather than one that says *this* is what you are holding.
     */
    grabbed: Int = -1,
    /**
     * How far into the grab animation the held handle is, from 0 to 1.
     *
     * Separate from [grabbed] so the ring can shrink back after the finger lifts rather than
     * vanishing: the caller keeps the index for the length of the return and drives this to zero.
     */
    grabProgress: Float = 0f,
): Boolean {
    val chart = drawing.points
    if (chart.isEmpty()) return false
    val fade = DrawingActions.fadeAlpha(drawing, nowMillis)
    // Gone rather than drawn at zero: a fully faded mark still has handles, a timeframe tag and a
    // label, and every one of them would paint over the price at full strength.
    if (fade <= 0f) return true
    val p = chart.map { Offset(view.xOfTime(it.time), view.yOf(it.price)) }
    val colour = faded(drawing.colour, fade)
    val width = max(1f, drawing.widthDp.dp.toPx())
    val w = view.plotWidth
    val h = view.plotHeight
    // A tool half-placed draws what it has so far and nothing it does not: an XABCD with three of
    // its five points is a three-point polyline, not a guess at where D will land.
    val a = p[0]
    val b = p.getOrNull(1)

    /**
     * The drawing's three colours and its dash, resolved once.
     *
     * Faded here rather than at each use, so a mark's text and its wash go out with its line.
     */
    val paint = DrawingPaint(
        line = colour,
        textOverride = drawing.textColour?.let { faded(it, fade) },
        fillOverride = drawing.fillColour?.let { faded(it, fade) },
        dash = dashEffect(drawing.lineStyle, width),
    )

    // The ten primitives below shadow the file-level ones of the same names, so that every one of
    // the hundred-odd calls in the `when` picks up the reader's dash, text colour and fill without
    // being rewritten — and so that a tool added later gets them for free instead of being the one
    // that quietly ignores them. Kotlin resolves a local function ahead of a member and ahead of a
    // top-level extension, which is exactly why the file-level ones are named `stroke*`/`paint*`:
    // two callables with the same name and the same signature would be a silent recursion.
    fun drawLine(colour: Color, from: Offset, to: Offset, width: Float) =
        strokeSegment(colour, from, to, width, paint.dash)

    fun dashed(from: Offset, to: Offset, colour: Color, width: Float) =
        strokeDashed(from, to, colour, width, paint.dash)

    fun ray(from: Offset, to: Offset, colour: Color, width: Float, w: Float, h: Float, both: Boolean) =
        strokeRay(from, to, colour, width, w, h, both, paint.dash)

    fun polyline(points: List<Offset>, colour: Color, width: Float): Boolean =
        strokeChain(points, colour, width, paint.dash)

    fun oval(centre: Offset, rx: Float, ry: Float, startAngle: Float, sweep: Float, colour: Color, width: Float) =
        strokeOval(centre, rx, ry, startAngle, sweep, colour, width, paint.dash)

    fun fillQuad(a: Offset, b: Offset, c: Offset, d: Offset, colour: Color) =
        fillPolygon(a, b, c, d, paint.wash(colour))

    fun band(left: Float, right: Float, y0: Float, y1: Float, colour: Color) =
        fillBand(left, right, y0, y1, paint.wash(colour))

    fun label(measurer: TextMeasurer, text: String, x: Float, y: Float, colour: Color) =
        paintLabel(measurer, text, x, y, paint.words(colour), plate)

    fun labelAbove(measurer: TextMeasurer, text: String, x: Float, y: Float, colour: Color) =
        paintLabelAbove(measurer, text, x, y, paint.words(colour), plate)

    fun boxLabel(
        measurer: TextMeasurer,
        text: String,
        x: Float,
        y: Float,
        colour: Color,
        anchor: Anchor = Anchor.START,
    ): Boolean = paintBoxLabel(measurer, text, x, y, colour, paint.words(Color.White), anchor)

    val handled = when (drawing.toolId) {

        // ── Lines ───────────────────────────────────────────────────────────────────
        "trend" -> b?.let { drawLine(colour, a, it, width) } != null
        "ray" -> b?.let { ray(a, it, colour, width, w, h, both = false) } != null
        "extline" -> b?.let { ray(a, it, colour, width, w, h, both = true) } != null
        "hline" -> {
            drawLine(colour, Offset(0f, a.y), Offset(w, a.y), width)
            labelAbove(measurer, priceText(chart[0].price), a.x + LABEL_INSET.toPx(), a.y, colour)
            true
        }
        "hray" -> {
            drawLine(colour, a, Offset(w, a.y), width)
            labelAbove(measurer, priceText(chart[0].price), a.x + LABEL_INSET.toPx(), a.y, colour)
            true
        }
        "vline" -> {
            drawLine(colour, Offset(a.x, 0f), Offset(a.x, h), width)
            true
        }
        "crossline" -> {
            drawLine(colour, Offset(0f, a.y), Offset(w, a.y), width)
            drawLine(colour, Offset(a.x, 0f), Offset(a.x, h), width)
            labelAbove(measurer, priceText(chart[0].price), a.x + LABEL_INSET.toPx(), a.y, colour)
            true
        }
        "angle" -> b?.let { end ->
            ray(a, end, colour, width, w, h, both = false)
            // The horizontal the angle is measured *from*, faint, and the arc between them. Without
            // the baseline the number is a claim the drawing does not show.
            drawLine(colour.copy(alpha = GHOST), a, Offset(a.x + ANGLE_BASE.toPx(), a.y), width)
            val degrees = degreesOf(a, end)
            drawArc(
                color = colour,
                startAngle = if (degrees >= 0) -degrees else 0f,
                sweepAngle = abs(degrees),
                useCenter = false,
                topLeft = Offset(a.x - ANGLE_RADIUS.toPx(), a.y - ANGLE_RADIUS.toPx()),
                size = Size(ANGLE_RADIUS.toPx() * 2, ANGLE_RADIUS.toPx() * 2),
                style = Stroke(width),
            )
            boxLabel(measurer, "${fixed(degrees, 1)}°", a.x + ANGLE_RADIUS.toPx() + 4, a.y - 8, colour)
        } != null
        "infoline" -> b?.let { end ->
            drawLine(colour, a, end, width)
            val delta = chart[1].price - chart[0].price
            val percent = if (chart[0].price != 0.0) delta / chart[0].price * 100 else 0.0
            val bars = ((end.x - a.x) / max(1f, view.barWidth)).roundToInt()
            val text = "Δ ${priceText(delta)}\n${fixed(percent, 2)}% | $bars بار\n${fixed(degreesOf(a, end), 1)}°"
            boxLabel(measurer, text, (a.x + end.x) / 2, (a.y + end.y) / 2, colour, Anchor.CENTER)
        } != null

        // ── Channels ────────────────────────────────────────────────────────────────
        "channel" -> {
            if (b == null) return false
            drawLine(colour, a, b, width)
            p.getOrNull(2)?.let { third ->
                val offset = third - a
                val top = a + offset
                val bottom = b + offset
                drawLine(colour, top, bottom, width)
                fillQuad(a, b, bottom, top, colour.copy(alpha = FILL_FAINT))
            }
            true
        }
        "pitchfork" -> {
            if (b == null) return false
            val third = p.getOrNull(2)
            if (third == null) {
                drawLine(colour, a, b, width)
            } else {
                // The median runs from the handle through the midpoint of the other two; the two
                // tines run parallel to it from each of them. Extended three times their own length
                // because a pitchfork is read forward, into bars that have not printed yet.
                val middle = Offset((b.x + third.x) / 2, (b.y + third.y) / 2)
                val reach = (middle - a) * PITCHFORK_REACH
                drawLine(colour, a, a + reach, width)
                for (prong in listOf(b, third)) {
                    drawLine(colour.copy(alpha = 0.9f), prong, prong + reach, width)
                }
            }
            true
        }

        // ── Fibonacci ───────────────────────────────────────────────────────────────
        "fib" -> b?.let {
            val top = max(chart[0].price, chart[1].price)
            val bottom = min(chart[0].price, chart[1].price)
            val span = top - bottom
            fibRows(
                measurer = measurer,
                levels = FIB_RETRACEMENT.map { level -> level to (top - span * level) },
                fromX = min(a.x, it.x),
                toX = max(a.x, it.x),
                view = view,
                colour = colour,
                width = width,
                paint = paint,
                plate = plate,
            )
        } != null
        "fibext" -> b?.let {
            val base = chart[0].price
            val diff = chart[1].price - base
            fibRows(
                measurer = measurer,
                levels = FIB_EXTENSION.map { level -> level to (base + diff * level) },
                fromX = min(a.x, it.x),
                toX = max(a.x, it.x) + FIB_OVERHANG.toPx(),
                view = view,
                colour = colour,
                width = width,
                paint = paint,
                plate = plate,
            )
        } != null
        "fib3" -> p.getOrNull(2)?.let { third ->
            // A→B measures the impulse; the ratios are projected from C, which is why this needs a
            // third tap that plain retracement does not.
            val direction = if (chart[1].price - chart[0].price >= 0) 1.0 else -1.0
            val magnitude = abs(chart[1].price - chart[0].price)
            drawLine(colour.copy(alpha = GHOST), a, b!!, width)
            drawLine(colour.copy(alpha = GHOST), b, third, width)
            fibRows(
                measurer = measurer,
                levels = FIB_FULL.map { level -> level to (chart[2].price + direction * magnitude * level) },
                fromX = b.x,
                toX = third.x + FIB_OVERHANG.toPx() * 2,
                view = view,
                colour = colour,
                width = width,
                paint = paint,
                plate = plate,
            )
        } != null
        "fibfan" -> b?.let { end ->
            drawRect(
                color = colour.copy(alpha = 0.25f),
                topLeft = Offset(min(a.x, end.x), min(a.y, end.y)),
                size = Size(abs(end.x - a.x), abs(end.y - a.y)),
                // The fan's bounding box is a construction line rather than a fill, so it takes the
                // line colour and the reader's dash — not the wash.
                style = Stroke(width, pathEffect = paint.dash),
            )
            for (level in FIB_FAN) {
                val y = a.y + (end.y - a.y) * level.toFloat()
                ray(a, Offset(end.x, y), colour.copy(alpha = 0.85f), width, w, h, both = false)
                label(measurer, "${fixed(level * 100, 1)}%", end.x + 3, y, colour)
            }
            true
        } != null
        "fibtime" -> b?.let {
            val unit = chart[1].time - chart[0].time
            if (unit == 0L) return false
            for (step in FIB_SEQUENCE) {
                val x = view.xOfTime(chart[0].time + step * unit)
                if (x > w + 4) break
                drawLine(colour, Offset(x, 0f), Offset(x, h), width)
                label(measurer, step.toString(), x + 3, 2f, colour)
            }
            true
        } != null
        "fibtimeext" -> p.getOrNull(2)?.let {
            val unit = chart[1].time - chart[0].time
            if (unit == 0L) return false
            // Fourteen ratios, and at any zoom where the unit is short they bunch. The lines all
            // stay — they are the tool — but a label is only drawn where the last one has cleared,
            // which is the rule the time axis already follows.
            var occupiedUntil = Float.NEGATIVE_INFINITY
            for (level in FIB_FULL) {
                val x = view.xOfTime(chart[2].time + (level * unit).toLong())
                if (x > w + 4) break
                drawLine(colour.copy(alpha = 0.85f), Offset(x, 0f), Offset(x, h), width)
                if (x < occupiedUntil) continue
                label(measurer, fixed(level, 3), x + 3, 2f, colour)
                occupiedUntil = x + RATIO_LABEL_WIDTH.toPx()
            }
            true
        } != null
        "fibchannel" -> p.getOrNull(2)?.let { third ->
            val offset = third - a
            var lastLabel: Offset? = null
            for (level in FIB_CHANNEL) {
                val shift = offset * level.toFloat()
                val edge = level == 0.0 || level == 1.0
                drawLine(colour.copy(alpha = if (edge) 1f else 0.7f), a + shift, b!! + shift, width)
                val at = b + shift
                val clear = lastLabel?.let { hypot(at.x - it.x, at.y - it.y) >= RATIO_LABEL_WIDTH.toPx() } ?: true
                if (!clear) continue
                label(measurer, "${fixed(level * 100, 1)}%", at.x + 3, at.y, colour)
                lastLabel = at
            }
            true
        } != null
        "fibcircles" -> b?.let { end ->
            // Ellipses rather than circles, and deliberately: the two axes are priced in different
            // units, so a true circle would be a different shape at every zoom level.
            val rx = abs(end.x - a.x)
            val ry = abs(end.y - a.y)
            for (level in FIB_CIRCLES) {
                oval(a, rx * level.toFloat(), ry * level.toFloat(), 0f, 360f, colour.copy(alpha = 0.7f), width)
            }
            true
        } != null
        "fibarcs" -> b?.let { end ->
            val rx = abs(end.x - a.x)
            val ry = abs(end.y - a.y)
            // The half that faces the way the reader dragged. An arc drawn on the other side is
            // measuring a move that did not happen.
            val start = if (end.y >= a.y) 0f else 180f
            for (level in FIB_ARCS) {
                oval(a, rx * level.toFloat(), ry * level.toFloat(), start, 180f, colour.copy(alpha = 0.8f), width)
            }
            true
        } != null

        // ── Gann ────────────────────────────────────────────────────────────────────
        "gannbox" -> b?.let { end ->
            val x = min(a.x, end.x)
            val y = min(a.y, end.y)
            val boxWidth = abs(end.x - a.x)
            val boxHeight = abs(end.y - a.y)
            drawRect(colour, Offset(x, y), Size(boxWidth, boxHeight), style = Stroke(width, pathEffect = paint.dash))
            for (ratio in GANN_RATIOS) {
                if (ratio == 0.0 || ratio == 1.0) continue
                val faint = colour.copy(alpha = 0.45f)
                drawLine(faint, Offset(x, y + boxHeight * ratio.toFloat()), Offset(x + boxWidth, y + boxHeight * ratio.toFloat()), width)
                drawLine(faint, Offset(x + boxWidth * ratio.toFloat(), y), Offset(x + boxWidth * ratio.toFloat(), y + boxHeight), width)
            }
            val diagonal = colour.copy(alpha = 0.8f)
            drawLine(diagonal, Offset(x, y + boxHeight), Offset(x + boxWidth, y), width)
            drawLine(diagonal, Offset(x, y), Offset(x + boxWidth, y + boxHeight), width)
            true
        } != null
        "gannfan" -> b?.let { end ->
            val unit = end - a
            for (slope in GANN_FAN) {
                val emphasis = if (slope == 1.0) 1f else 0.6f
                ray(a, Offset(a.x + unit.x, a.y + unit.y * slope.toFloat()), colour.copy(alpha = emphasis), width, w, h, both = false)
            }
            true
        } != null

        // ── Patterns and Elliott ────────────────────────────────────────────────────
        "xabcd" -> pattern(p, chart, XABCD_LABELS, ratios = true, measurer, colour, width, paint)
        "abcd" -> pattern(p, chart, ABCD_LABELS, ratios = true, measurer, colour, width, paint)
        "cypher" -> pattern(p, chart, XABCD_LABELS, ratios = true, measurer, colour, width, paint)
        "hns" -> pattern(p, chart, HNS_LABELS, ratios = false, measurer, colour, width, paint)
        "ell_impulse" -> pattern(p, chart, IMPULSE_LABELS, ratios = false, measurer, colour, width, paint)
        "ell_abc" -> pattern(p, chart, ABC_LABELS, ratios = false, measurer, colour, width, paint)
        "tripattern", "triangle" -> p.getOrNull(2)?.let { third ->
            fillQuad(a, b!!, third, third, colour.copy(alpha = FILL_SOFT))
            polyline(listOf(a, b, third, a), colour, width)
        } != null

        // ── Shapes ──────────────────────────────────────────────────────────────────
        "rect" -> b?.let { end ->
            val topLeft = Offset(min(a.x, end.x), min(a.y, end.y))
            val size = Size(abs(end.x - a.x), abs(end.y - a.y))
            drawRect(paint.wash(colour.copy(alpha = FILL_SOFT)), topLeft, size, style = Fill)
            drawRect(colour, topLeft, size, style = Stroke(width, pathEffect = paint.dash))
            true
        } != null
        "rotrect" -> p.getOrNull(2)?.let { third ->
            // The third tap sets the width, measured along the normal of the first two. That is what
            // makes this a rectangle at an angle rather than a rectangle with a corner dragged.
            val along = b!! - a
            val length = max(1f, hypot(along.x, along.y))
            val normal = Offset(-along.y / length, along.x / length)
            val reach = (third - a).x * normal.x + (third - a).y * normal.y
            val corners = listOf(a, b, b + normal * reach, a + normal * reach)
            fillQuad(corners[0], corners[1], corners[2], corners[3], colour.copy(alpha = FILL_SOFT))
            polyline(corners + corners[0], colour, width)
        } != null
        "circle" -> b?.let { end ->
            val radius = hypot(end.x - a.x, end.y - a.y)
            drawCircle(paint.wash(colour.copy(alpha = FILL_SOFT)), radius, a)
            drawCircle(colour, radius, a, style = Stroke(width, pathEffect = paint.dash))
            true
        } != null
        "ellipse" -> b?.let { end ->
            val centre = Offset((a.x + end.x) / 2, (a.y + end.y) / 2)
            val rx = abs(end.x - a.x) / 2
            val ry = abs(end.y - a.y) / 2
            drawOval(paint.wash(colour.copy(alpha = FILL_SOFT)), Offset(centre.x - rx, centre.y - ry), Size(rx * 2, ry * 2))
            oval(centre, rx, ry, 0f, 360f, colour, width)
            true
        } != null
        "sine" -> b?.let {
            val wavelength = chart[1].time - chart[0].time
            if (wavelength == 0L) return false
            val mid = (chart[0].price + chart[1].price) / 2
            val amplitude = abs(chart[1].price - chart[0].price) / 2
            val samples = (0..SINE_SAMPLES).map { step ->
                val time = chart[0].time + (wavelength * (step.toDouble() / SINE_SAMPLES) * SINE_CYCLES).toLong()
                val phase = 2 * Math.PI * (time - chart[0].time) / wavelength
                Offset(view.xOfTime(time), view.yOf(mid + amplitude * sin(phase)))
            }
            polyline(samples, colour, width)
        } != null
        "brush" -> polyline(p, colour, width)
        "highlighter" -> polyline(p, colour.copy(alpha = HIGHLIGHT_ALPHA), max(width, HIGHLIGHT_WIDTH.toPx()))

        // ── Position ────────────────────────────────────────────────────────────────
        "longshort" -> b?.let { end ->
            // Entry and stop are tapped; the target is a third point placed on commit at two-to-one
            // — see `DrawingActions.withTarget`. It is a *point*, so it has a handle and the reader
            // can drag it to whatever reward they actually want, and the label follows what they
            // dragged rather than continuing to claim 2R.
            //
            // The fallback covers a drawing saved by a build before the target existed: those have
            // two points, and reading `chart[2]` would drop them off the chart entirely.
            val entry = chart[0].price
            val stop = chart[1].price
            val target = chart.getOrNull(2)?.price ?: (entry + 2 * (entry - stop))
            val risk = abs(entry - stop)
            val reward = if (risk > 0) abs(target - entry) / risk else 0.0
            val left = min(a.x, end.x)
            val right = max(a.x, end.x) + POSITION_OVERHANG.toPx()
            // Which colour goes above the entry and which below is [setupBands]' decision, shared
            // with the signal overlay in `drawSignal`. Two files agreeing by coincidence about
            // where the red goes is the one disagreement a trading chart may not have — and a
            // short's bands land the other way up here for free, exactly as they do there.
            setupBands(entry, stop, target).forEach { zone ->
                val fill = if (zone.role == SetupBandRole.RISK) sellColour() else buyColour()
                band(left, right, view.yOf(zone.from), view.yOf(zone.to), fill.copy(alpha = ZONE))
            }
            // Latin digits on the multiple: it is a market figure sitting on the chart's own
            // canvas beside prices, and «هدف ۲٫۵R» in a column of Latin numbers reads as a
            // different kind of thing from what it is.
            level(measurer, left, right, view.yOf(target), buyColour(), "هدف " + fixed(reward, 1) + "R", paint, plate)
            level(measurer, left, right, view.yOf(entry), colour, "ورود", paint, plate)
            level(measurer, left, right, view.yOf(stop), sellColour(), "حد ضرر", paint, plate)
            true
        } != null

        // ── Measure ─────────────────────────────────────────────────────────────────
        "pricerange" -> b?.let { end ->
            val centre = (a.x + end.x) / 2
            drawLine(colour, Offset(centre, a.y), Offset(centre, end.y), width)
            drawLine(colour, Offset(centre - BRACKET.toPx(), a.y), Offset(centre + BRACKET.toPx(), a.y), width)
            drawLine(colour, Offset(centre - BRACKET.toPx(), end.y), Offset(centre + BRACKET.toPx(), end.y), width)
            val delta = chart[1].price - chart[0].price
            val percent = if (chart[0].price != 0.0) delta / chart[0].price * 100 else 0.0
            band(min(a.x, end.x), max(a.x, end.x), a.y, end.y, gainColour(delta).copy(alpha = ZONE))
            boxLabel(measurer, "${priceText(delta)}\n${fixed(percent, 2)}%", centre, (a.y + end.y) / 2, colour, Anchor.CENTER)
        } != null
        "daterange" -> b?.let { end ->
            val centre = (a.y + end.y) / 2
            drawLine(colour, Offset(a.x, centre), Offset(end.x, centre), width)
            drawLine(colour, Offset(a.x, centre - BRACKET.toPx()), Offset(a.x, centre + BRACKET.toPx()), width)
            drawLine(colour, Offset(end.x, centre - BRACKET.toPx()), Offset(end.x, centre + BRACKET.toPx()), width)
            val bars = abs(((end.x - a.x) / max(1f, view.barWidth)).roundToInt())
            boxLabel(measurer, "$bars بار\n${spanText(abs(chart[1].time - chart[0].time))}", (a.x + end.x) / 2, centre, colour, Anchor.CENTER)
        } != null
        "dprange" -> b?.let { end ->
            val topLeft = Offset(min(a.x, end.x), min(a.y, end.y))
            val size = Size(abs(end.x - a.x), abs(end.y - a.y))
            drawRect(paint.wash(colour.copy(alpha = FILL_SOFT)), topLeft, size, style = Fill)
            drawRect(colour, topLeft, size, style = Stroke(width, pathEffect = paint.dash))
            val delta = chart[1].price - chart[0].price
            val percent = if (chart[0].price != 0.0) delta / chart[0].price * 100 else 0.0
            val bars = abs((size.width / max(1f, view.barWidth)).roundToInt())
            val text = "${priceText(delta)} (${fixed(percent, 2)}%)\n$bars بار | ${spanText(abs(chart[1].time - chart[0].time))}"
            boxLabel(measurer, text, topLeft.x + size.width / 2, topLeft.y + size.height / 2, colour, Anchor.CENTER)
        } != null
        "forecast" -> b?.let { end ->
            // A cone rather than a line, because a forecast that draws as a line claims a precision
            // nobody has. The mouth widens with the size of the move being projected.
            val spread = abs(end.y - a.y) * FORECAST_SPREAD + FORECAST_FLOOR.toPx()
            val cone = Path().apply {
                moveTo(a.x, a.y)
                lineTo(end.x, end.y - spread)
                lineTo(end.x, end.y + spread)
                close()
            }
            drawPath(cone, gainColour(chart[1].price - chart[0].price).copy(alpha = ZONE))
            dashed(a, end, colour, width)
            val percent = if (chart[0].price != 0.0) (chart[1].price - chart[0].price) / chart[0].price * 100 else 0.0
            boxLabel(measurer, "${fixed(percent, 2)}%", end.x + 4, end.y, colour)
        } != null
        "ruler" -> b?.let { end ->
            dashed(a, end, colour, width)
            val delta = chart[1].price - chart[0].price
            val percent = if (chart[0].price != 0.0) delta / chart[0].price * 100 else 0.0
            val bars = abs(((end.x - a.x) / max(1f, view.barWidth)).roundToInt())
            band(min(a.x, end.x), max(a.x, end.x), a.y, end.y, gainColour(delta).copy(alpha = ZONE))
            val arrow = if (delta >= 0) "▲" else "▼"
            boxLabel(
                measurer = measurer,
                text = "$arrow ${priceText(abs(delta))} (${fixed(percent, 2)}%)\n$bars بار",
                x = end.x,
                y = end.y,
                colour = gainColour(delta),
                anchor = Anchor.ABOVE,
            )
        } != null
        "cyclic" -> b?.let {
            val period = chart[1].time - chart[0].time
            if (period == 0L) return false
            for (step in 0 until CYCLE_LIMIT) {
                val x = view.xOfTime(chart[0].time + step * period)
                if (x > w + 4) break
                drawLine(colour, Offset(x, 0f), Offset(x, h), width)
            }
            true
        } != null

        // ── Annotation ──────────────────────────────────────────────────────────────
        "arrow" -> b?.let { end ->
            drawLine(colour, a, end, width)
            arrowHead(a, end, colour, ARROW_HEAD.toPx())
            true
        } != null
        "arrowdir" -> {
            val tail = when (drawing.direction) {
                ArrowDirection.UP -> Offset(a.x, a.y + ARROW_REACH.toPx())
                ArrowDirection.DOWN -> Offset(a.x, a.y - ARROW_REACH.toPx())
                ArrowDirection.LEFT -> Offset(a.x + ARROW_REACH.toPx(), a.y)
                ArrowDirection.RIGHT -> Offset(a.x - ARROW_REACH.toPx(), a.y)
            }
            drawLine(colour, tail, a, width)
            arrowHead(tail, a, colour, ARROW_HEAD.toPx())
            true
        }
        "text" -> {
            label(measurer, drawing.text ?: DEFAULT_NOTE, a.x, a.y, colour)
            true
        }
        "callout" -> b?.let { end ->
            drawLine(colour, a, end, width)
            drawCircle(colour, CALLOUT_DOT.toPx(), end)
            boxLabel(measurer, drawing.text ?: DEFAULT_NOTE, a.x, a.y, colour)
        } != null
        "pricelabel" -> {
            dashed(Offset(0f, a.y), Offset(w, a.y), colour, width)
            val text = listOfNotNull(drawing.text, priceText(chart[0].price)).joinToString("  ")
            val measured = measurer.measure(text, boxStyle(Color.White))
            val tagWidth = measured.size.width + TAG_PADDING.toPx() * 2
            drawRect(colour, Offset(w - tagWidth, a.y - measured.size.height / 2f - 2), Size(tagWidth, measured.size.height + 4f))
            drawText(measured, topLeft = Offset(w - tagWidth + TAG_PADDING.toPx(), a.y - measured.size.height / 2f))
            true
        }
        "note" -> {
            drawCircle(colour, NOTE_RADIUS.toPx(), a)
            drawing.text?.let { boxLabel(measurer, it, a.x + NOTE_RADIUS.toPx() + 5, a.y - 8, colour) }
            true
        }

        // Everything the second wave of tools added. Split into its own function rather than
        // grown onto the end of this `when`: the branch list above is already at the size where a
        // reader loses their place in it, and the new tools all share a shape — chart-space
        // geometry from [DrawingGeometryA]/[DrawingGeometryB], projected and stroked — that has
        // nothing to do with the hand-rolled arithmetic above.
        else -> drawExtendedDrawing(drawing, view, measurer, chart, p, colour, width, paint, images)
    }

    // The interval the mark was drawn on, as a small tag beside its first anchor. Only on a
    // finished drawing that carries one: a half-placed pattern already has an anchor count on the
    // toolbar, and a second label following the finger is noise on top of it.
    if (handled && drawing.complete && drawing.timeframe != null) {
        timeframeTag(measurer, drawing.timeframe, a, colour)
    }

    // The handles come last so they sit over whatever the tool drew, and only on the selected one:
    // eight white dots on every drawing would bury the chart under its own annotations.
    if (handled && selected) {
        val base = HANDLE_RADIUS.toPx()
        val grow = grabProgress.coerceIn(0f, 1f)
        p.forEachIndexed { index, point ->
            val held = index == grabbed && grow > 0f
            val radius = if (held) base * (1f + (HANDLE_GRAB_SCALE - 1f) * grow) else base
            // A halo under the held one, and it is the part that does the work: at a radius wider
            // than a fingertip it is the only thing about the anchor the reader can still see.
            // Faint, because it is drawn over the bars the anchor is being aimed at, and a solid
            // disc there would hide the very wick the handle is being placed on.
            if (held) {
                drawCircle(colour.copy(alpha = HANDLE_HALO_ALPHA * grow), radius * HANDLE_HALO_SCALE, point)
            }
            drawCircle(Color.White, radius, point)
            drawCircle(colour, radius, point, style = Stroke(HANDLE_RING.toPx()))
            // The readout: what the finger is over, said beside the ring rather than under the
            // finger. The bar's open, high, low and close and its moment, so a handle being
            // dragged onto a wick can be put exactly on it — the same figures a magnifier would
            // show, without the magnifier's lens over the very bars being aimed at.
            if (held && grow >= 1f) {
                handleReadout(measurer, view, chart[index], point, colour, plate)
            }
        }
    }
    return handled
}

/**
 * The plate beside a held handle: the anchor's price, the bar's OHLC under it and the bar's time.
 *
 * Placed above and to the reading side of the ring so the fingertip covers none of it, and
 * flipped to the other side at the plot's edge. The plate is the stage colour at the tag's
 * opacity, so the candles behind it are dimmed rather than hidden.
 */
private fun DrawScope.handleReadout(
    measurer: TextMeasurer,
    view: ChartViewport,
    anchor: ChartPoint,
    at: Offset,
    colour: Color,
    plate: Color,
) {
    val series = view.series
    if (series.isEmpty) return
    val bar = view.indexAt(at.x).coerceIn(0, series.size - 1)
    val lines = listOf(
        view.formatPrice(anchor.price),
        "O " + view.formatPrice(series.open[bar]) + "  H " + view.formatPrice(series.high[bar]),
        "L " + view.formatPrice(series.low[bar]) + "  C " + view.formatPrice(series.close[bar]),
        formatTime(series.time[bar], spanSeconds = 0L, zone = CHART_ZONE),
    )
    val measured = lines.map { measurer.measure(it, boxStyle(Color.White)) }
    val pad = TAG_PADDING.toPx()
    val width = measured.maxOf { it.size.width } + pad * 2
    val height = measured.sumOf { it.size.height } + pad * 2
    val lift = READOUT_LIFT.toPx()
    var left = at.x + lift
    if (left + width > size.width) left = at.x - lift - width
    var top = at.y - lift - height
    if (top < 0f) top = at.y + lift
    val origin = Offset(left, top)
    val ground = if (plate.isSpecified) plate.copy(alpha = READOUT_PLATE_ALPHA) else BOX_BACKGROUND
    drawRoundRect(ground, origin, Size(width, height), cornerRadius = CornerRadius(READOUT_RADIUS.toPx()))
    drawRoundRect(colour, origin, Size(width, height), cornerRadius = CornerRadius(READOUT_RADIUS.toPx()), style = Stroke(1f))
    var y = origin.y + pad
    measured.forEachIndexed { line, text ->
        drawText(text, color = if (line == 0) colour else Color.White, topLeft = Offset(origin.x + pad, y))
        y += text.size.height
    }
}

/** Which way a standalone arrow marker points. */
enum class ArrowDirection { UP, DOWN, LEFT, RIGHT }

// ---------------------------------------------------------------------------- the image tool's pictures

/**
 * What the image tool has to draw: a picture, or the reason there is not one.
 *
 * Three states and not a nullable bitmap, because "not here yet" and "not here any more" are
 * different facts and the reader is owed the difference. A chart that has just opened has not
 * finished reading its pictures off disk, and a frame that announced a missing file during that
 * second would be crying wolf on every launch; a picture whose file a reinstall took is gone for
 * good, and a frame that stayed hopeful about it would leave the reader waiting for a load that is
 * never coming.
 */
sealed interface DrawingImage {

    /** The picture, decoded and ready. */
    data class Shown(val bitmap: ImageBitmap) : DrawingImage

    /** Nothing yet — either nobody has asked for it, or the read is still running. */
    data object Waiting : DrawingImage

    /** Asked for, and the bytes are not there. See `DrawingImageStore`'s note on missing files. */
    data object Gone : DrawingImage
}

/** Where [drawDrawing] gets an image drawing's picture from. One function, so a test can be one. */
fun interface DrawingImageSource {
    fun imageFor(imageId: String): DrawingImage
}

/**
 * The decoded pictures, held for the process.
 *
 * ### Why the cache is here and not beside the files
 *
 * `DrawingImageStore` owns the bytes and knows nothing about Compose; this owns the decoded
 * [ImageBitmap] and knows nothing about files. That split is not tidiness — `core:chart` does not
 * depend on `core:datastore` and must not start, because the chart engine is the one part of this
 * app that is pure enough to render in a unit test. The feature layer joins the two: it reads bytes
 * from the store and puts them here, and that is the only place the two halves meet.
 *
 * ### Why it is a cache and not a map
 *
 * A picture at `DrawingImageStore.MAX_EDGE` is about four megabytes decoded. Holding every picture
 * a reader has ever placed, across every symbol, is how a chart dies on a cheap phone — so this
 * keeps [CAPACITY] of them in least-recently-drawn order and lets the rest go. An evicted picture
 * is [DrawingImage.Waiting] again, not [DrawingImage.Gone]: the bytes are still on disk and the
 * next read brings it back.
 *
 * ### Why a revision counter
 *
 * A picture arrives on a background thread, after the frame that wanted it was drawn. Compose
 * repaints when state a draw read has changed, so [imageFor] reads [revision] and every [put]
 * bumps it — without that, a picture loaded off disk would sit in this map invisible until the
 * next tick of the feed moved something else, which on a closed market is never.
 */
object DrawingImages : DrawingImageSource {

    /**
     * How many decoded pictures are held at once.
     *
     * Six, which is about twenty-four megabytes at the store's cap and more images than any chart
     * this app has been used on carries. The number matters less than the fact that there is one:
     * an unbounded cache of photographs on a phone is a crash with a delay on it.
     */
    private const val CAPACITY = 6

    /** Access-ordered, so [CAPACITY] evicts the picture least recently *drawn* rather than oldest. */
    private val loaded = object : LinkedHashMap<String, ImageBitmap>(CAPACITY, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>): Boolean =
            size > CAPACITY
    }

    /** The ids somebody looked for and did not find. Never evicted; there is nothing to evict. */
    private val gone = HashSet<String>()

    private val revision = mutableIntStateOf(0)

    /**
     * The id inside a drawing's text, or null when there is not one.
     *
     * The image tool keeps its picture reference in `Drawing.text`, the field a note keeps its
     * words in, because [Drawing] has no field of its own for it — adding one would change a codec
     * that three other things write, and `core:chart` cannot be the module that decides that. The
     * shape is `img_<sixteen hex>` optionally followed by a space and the reader's caption, so a
     * picture and a caption can live in one field and an ordinary caption on an ordinary drawing is
     * still just a caption.
     *
     * The pattern is duplicated from `DrawingImageStore.isImageId` rather than depended on, the way
     * `StoredDrawing` duplicates the drawing defaults, and both are pinned by tests.
     */
    fun idIn(text: String?): String? =
        text?.trim()?.substringBefore(' ')?.takeIf(ID::matches)

    /** The reader's own words out of the same field, or null when the text is a bare id. */
    fun captionIn(text: String?): String? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        val head = trimmed.substringBefore(' ')
        val rest = if (ID.matches(head)) trimmed.substringAfter(' ', "") else trimmed
        return rest.trim().takeIf(String::isNotEmpty)
    }

    /** The two halves back into one field, which is what the picker writes to `Drawing.text`. */
    fun textFor(imageId: String, caption: String? = null): String =
        listOfNotNull(imageId, caption?.trim()?.takeIf(String::isNotEmpty)).joinToString(" ")

    override fun imageFor(imageId: String): DrawingImage {
        // Read inside the draw phase on purpose — see the class note on the revision counter.
        revision.intValue
        synchronized(loaded) {
            loaded[imageId]?.let { return DrawingImage.Shown(it) }
            return if (imageId in gone) DrawingImage.Gone else DrawingImage.Waiting
        }
    }

    /** Hold a picture somebody else decoded. */
    fun put(imageId: String, bitmap: ImageBitmap) {
        synchronized(loaded) {
            loaded[imageId] = bitmap
            gone.remove(imageId)
        }
        revision.intValue++
    }

    /**
     * Decode stored bytes and hold the result. False means the bytes are not a picture.
     *
     * Here rather than at the call site so that the one place in the app that turns
     * `DrawingImageStore`'s bytes into a bitmap is the one that caches it. A false answer is the
     * caller's cue to call [markGone]: bytes that no decoder recognises are, to a reader, exactly
     * as absent as a file that is not there.
     *
     * Decoding is not free — call it off the main thread.
     */
    fun put(imageId: String, encoded: ByteArray): Boolean {
        val bitmap = runCatching { BitmapFactory.decodeByteArray(encoded, 0, encoded.size) }.getOrNull()
            ?: return false
        put(imageId, bitmap.asImageBitmap())
        return true
    }

    /** Record that a picture's bytes are not there, so the frame can say so instead of waiting. */
    fun markGone(imageId: String) {
        synchronized(loaded) {
            loaded.remove(imageId)
            gone.add(imageId)
        }
        revision.intValue++
    }

    /** Drop one picture from memory — the reader deleted the drawing, or replaced its picture. */
    fun forget(imageId: String) {
        synchronized(loaded) {
            loaded.remove(imageId)
            gone.remove(imageId)
        }
        revision.intValue++
    }

    /** Drop everything. For a sign-out, and for a test that must not see the last one's pictures. */
    fun clear() {
        synchronized(loaded) {
            loaded.clear()
            gone.clear()
        }
        revision.intValue++
    }

    private val ID = Regex("^img_[0-9a-f]{16}$")
}

/**
 * The rectangle an image drawing occupies, in screen pixels.
 *
 * Two anchors are the reader's own corners, in either order — a box dragged up and to the left is
 * the same box as one dragged down and to the right, which is what every other two-point tool in
 * this file assumes.
 *
 * One anchor has to have a size invented for it, and [span] is that size measured in bars by the
 * caller. Invented in chart space rather than in device pixels because the alternative is a picture
 * that stays the same size on the glass while the bars slide underneath it — which is a sticker,
 * not an annotation. The height follows the picture's own aspect, so a one-tap image is never
 * distorted and never needs a second tap to be right.
 */
internal fun imageFrame(a: Offset, b: Offset?, span: Float, imageWidth: Int, imageHeight: Int): Rect {
    if (b != null) return Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
    val aspect = if (imageWidth > 0 && imageHeight > 0) {
        imageWidth.toFloat() / imageHeight
    } else {
        IMAGE_WIDTH.value / IMAGE_HEIGHT.value
    }
    return Rect(a.x, a.y, a.x + span, a.y + span / aspect)
}

/**
 * The picture's own rectangle inside [frame], centred and never stretched.
 *
 * Letterboxed rather than filled. A reader dragging a box around a picture is positioning it, not
 * cropping it, and a chart annotation that silently distorts the screenshot pasted into it is worse
 * than one that leaves a margin. A degenerate frame — a two-point drawing still being placed, both
 * anchors on the same bar — is handed straight back, so the caller draws nothing rather than
 * dividing by it.
 */
internal fun fitImage(frame: Rect, imageWidth: Int, imageHeight: Int): Rect {
    if (frame.width <= 0f || frame.height <= 0f || imageWidth <= 0 || imageHeight <= 0) return frame
    val scale = min(frame.width / imageWidth, frame.height / imageHeight)
    val width = imageWidth * scale
    val height = imageHeight * scale
    val left = frame.left + (frame.width - width) / 2f
    val top = frame.top + (frame.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

/**
 * A stored ARGB colour as a Compose colour, already carrying the mark's fade.
 *
 * One function rather than the same two lines at four sites, because the fade has to reach all
 * three of a drawing's colours the same way: a demonstration mark whose line fades while its text
 * stays solid does not read as fading, it reads as broken.
 */
private fun faded(argb: Long, fade: Float): Color {
    val colour = Color(argb.toInt())
    return if (fade < 1f) colour.copy(alpha = colour.alpha * fade) else colour
}

/**
 * The four brushes one drawing is painted with, resolved once per frame.
 *
 * [Drawing] carries three colours and a line style, and a renderer that reads only the first of
 * them is the defect this whole layer keeps producing: the field exists, the toolbar sets it, and
 * the picture never changes. Resolving them into one object here means every painter below takes
 * *one* extra argument instead of four, and there is a single place where "null means follow the
 * line" is decided rather than one per tool.
 *
 * The fade is already folded into [line], [words] and the wash, so nothing downstream asks the
 * clock a second time and no two painters can disagree about how far gone a demonstration mark is.
 */
private class DrawingPaint(
    /** The stroke colour, faded. What every tool draws its own geometry in. */
    val line: Color,
    private val textOverride: Color?,
    private val fillOverride: Color?,
    /** The reader's dash, or null for [LineStyleKind.SOLID] — which means "the tool's own look". */
    val dash: PathEffect?,
) {
    /**
     * The colour a piece of text is written in.
     *
     * The override is applied to the drawing's *own* voice only: text already in the drawing's
     * colour, and the white a boxed label defaults to. A position tool's green target and red stop
     * are the market speaking rather than the reader, and they keep their colours — the same
     * decision [markColour] makes for the arrow marks, and for the same reason.
     *
     * The override's alpha is multiplied by the fallback's rather than replacing it, so a fading
     * demonstration mark takes its text with it.
     */
    fun words(fallback: Color): Color {
        val over = textOverride ?: return fallback
        if (!isOwn(fallback)) return fallback
        return over.copy(alpha = over.alpha * fallback.alpha)
    }

    /**
     * The colour a fill is washed in, keeping the alpha the tool asked for.
     *
     * The alpha and not the hue is the tool's, and that is the whole rule: a pattern fills at six
     * percent and a risk band at twelve, and those numbers are what stop a filled drawing burying
     * the candles under it. A reader's fill colour arriving at full opacity would do exactly that.
     */
    fun wash(tint: Color): Color {
        val over = fillOverride ?: return tint
        if (!isOwn(tint)) return tint
        // A fill colour stored at full alpha means «the hue is mine, the opacity is the tool's».
        // Below full it is the reader's own opacity, set on the fill slider, and it replaces the
        // tool's rather than multiplying it — the slider says what the wash *is*, not a fraction
        // of a number the reader never saw.
        return if (over.alpha >= 1f) over.copy(alpha = tint.alpha) else over.copy(alpha = over.alpha)
    }

    /**
     * Whether a colour is the drawing's own, at whatever alpha the tool chose.
     *
     * Compared on the three channels and not on the whole value, because every tool derives its
     * ghosts and washes with `copy(alpha = …)`, which leaves the hue untouched. White is counted as
     * the drawing's own because that is what a boxed label's text defaults to, and boxed text is
     * exactly the text a reader means when they set a text colour.
     */
    private fun isOwn(colour: Color): Boolean =
        (colour.red == line.red && colour.green == line.green && colour.blue == line.blue) ||
            (colour.red == 1f && colour.green == 1f && colour.blue == 1f)
}

// ---------------------------------------------------------------------------- primitives

/**
 * One straight stroke, with the drawing's dash on it.
 *
 * Named away from `drawLine` deliberately. `DrawScope` has a member of that name, and a member wins
 * overload resolution against a top-level extension — so the private `drawLine` this file used to
 * carry was never once called, and every "dashed" line went out solid. The local shadows in
 * [drawDrawing] rely on that same rule from the other side: a *local* function does beat a member.
 */
private fun DrawScope.strokeSegment(colour: Color, from: Offset, to: Offset, width: Float, dash: PathEffect?) {
    val (start, end) = registered(from, to, width)
    drawLine(color = colour, start = start, end = end, strokeWidth = width, pathEffect = dash)
}

/**
 * The two ends of a stroke, moved onto device pixels **only where doing so cannot rotate it**.
 *
 * ### Why the rule is "axis-aligned only"
 *
 * A horizontal line asked for at y = 812.0, one pixel wide, straddles the boundary between rows 811
 * and 812 and is painted as two rows at half intensity — a soft grey smear where a crisp rule was
 * intended. That is what a horizontal-line tool has looked like on this chart, and the horizontal
 * line is the most-drawn tool there is: a reader marks a level, and the level they marked comes out
 * blurrier than the gridline behind it.
 *
 * The same treatment applied to a *diagonal* would be a defect rather than a fix. Moving either end
 * of a trend line by half a pixel changes its angle, by an amount that grows as the line gets
 * shorter — and a trend line's angle is the entire content of the drawing. So a segment is
 * registered only when it is already flat or already upright, where snapping moves the whole line
 * together and cannot change what it says.
 *
 * The comparison is exact rather than tolerant, and deliberately: an `hline` and a `vline` are
 * built from one anchor's coordinate used twice, so their two ends are the *same float*. Anything
 * that is merely nearly-flat is a trend line the reader drew nearly flat, and it keeps its angle.
 */
private fun DrawScope.registered(from: Offset, to: Offset, width: Float): Pair<Offset, Offset> = when {
    from.y == to.y -> {
        val row = strokeCentre(from.y, width)
        Offset(from.x, row) to Offset(to.x, row)
    }
    from.x == to.x -> {
        val column = strokeCentre(from.x, width)
        Offset(column, from.y) to Offset(column, to.y)
    }
    else -> from to to
}

/**
 * The dashed stroke a handful of tools use whatever the reader chose.
 *
 * [dash] wins when it is set, so a reader who picks dotted gets dotted here too; null keeps the
 * tool's own six-on-four, which is what a price label and a forecast have always looked like.
 */
private fun DrawScope.strokeDashed(from: Offset, to: Offset, colour: Color, width: Float, dash: PathEffect?) {
    val (start, end) = registered(from, to, width)
    drawLine(
        color = colour,
        start = start,
        end = end,
        strokeWidth = width,
        pathEffect = dash ?: PathEffect.dashPathEffect(floatArrayOf(DASH_ON.toPx(), DASH_OFF.toPx())),
    )
}

/**
 * A ray from [from] through [to], continued to well past the canvas.
 *
 * Four times the longer side rather than a fixed constant: a fixed one either stops short on a
 * tablet or wastes most of its length on a phone, and a ray that stops short reads as a trend line
 * that someone drew badly.
 */
private fun DrawScope.strokeRay(
    from: Offset,
    to: Offset,
    colour: Color,
    width: Float,
    w: Float,
    h: Float,
    both: Boolean,
    dash: PathEffect?,
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = max(1f, hypot(dx, dy))
    val reach = max(w, h) * RAY_REACH
    val unit = Offset(dx / length, dy / length)
    val start = if (both) from - unit * reach else from
    strokeSegment(colour, start, from + unit * reach, width, dash)
}

private fun DrawScope.strokeChain(
    points: List<Offset>,
    colour: Color,
    width: Float,
    dash: PathEffect?,
): Boolean {
    if (points.size < 2) return false
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (index in 1 until points.size) lineTo(points[index].x, points[index].y)
    }
    drawPath(path, colour, style = Stroke(width, pathEffect = dash))
    return true
}

private fun DrawScope.fillPolygon(a: Offset, b: Offset, c: Offset, d: Offset, colour: Color) {
    val path = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        lineTo(d.x, d.y)
        close()
    }
    drawPath(path, colour)
}

private fun DrawScope.strokeOval(
    centre: Offset,
    rx: Float,
    ry: Float,
    startAngle: Float,
    sweep: Float,
    colour: Color,
    width: Float,
    dash: PathEffect?,
) {
    if (rx <= 0f || ry <= 0f) return
    drawArc(
        color = colour,
        startAngle = startAngle,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(centre.x - rx, centre.y - ry),
        size = Size(rx * 2, ry * 2),
        style = Stroke(width, pathEffect = dash),
    )
}

private fun DrawScope.fillBand(left: Float, right: Float, y0: Float, y1: Float, colour: Color) {
    drawRect(colour, Offset(left, min(y0, y1)), Size(max(0f, right - left), abs(y1 - y0)))
}

private fun DrawScope.arrowHead(from: Offset, to: Offset, colour: Color, size: Float) {
    val angle = atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
    val spread = Math.PI / 7
    val path = Path().apply {
        moveTo(to.x, to.y)
        lineTo((to.x - size * cos(angle - spread)).toFloat(), (to.y - size * sin(angle - spread)).toFloat())
        lineTo((to.x - size * cos(angle + spread)).toFloat(), (to.y - size * sin(angle + spread)).toFloat())
        close()
    }
    drawPath(path, colour)
}

// ---------------------------------------------------------------------------- text

private enum class Anchor { START, CENTER, ABOVE }

private fun DrawScope.paintLabel(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    colour: Color,
    plate: Color = Color.Unspecified,
) {
    val measured = measurer.measure(text, boxStyle(colour))
    drawLabelPlate(plate, x, y, measured.size.width.toFloat(), measured.size.height.toFloat())
    drawText(measured, topLeft = Offset(x, y))
}

/**
 * The soft ground under a label that sits over the bars.
 *
 * Four-fifths rather than solid, so the candle underneath still shows through and the plate reads
 * as a highlight over the chart rather than a hole cut in it — the same figure the setup levels
 * use. Nothing at all when the caller had no colour to give.
 */
private fun DrawScope.drawLabelPlate(plate: Color, x: Float, y: Float, width: Float, height: Float) {
    if (plate == Color.Unspecified) return
    val padding = LABEL_PLATE_PADDING.toPx()
    drawRoundRect(
        color = plate.copy(alpha = LABEL_PLATE_ALPHA),
        topLeft = Offset(x - padding, y - padding / 2f),
        size = Size(width + padding * 2, height + padding),
        cornerRadius = CornerRadius(LABEL_PLATE_RADIUS.toPx(), LABEL_PLATE_RADIUS.toPx()),
    )
}

/** Matches the setup levels' plate, so two labels on one chart are the same object. */
private val LABEL_PLATE_PADDING = 4.dp
private const val LABEL_PLATE_ALPHA = 0.8f
private val LABEL_PLATE_RADIUS = 3.dp

/**
 * A label that clears the line it belongs to.
 *
 * [y] is the line, not the text: the label is lifted by its own measured height so the line runs
 * under it rather than through it. Passing the line's y straight to [label] put a Fibonacci price
 * with a blue rule struck through the middle of it, which the screenshot caught and no amount of
 * reading the code would have.
 */
private fun DrawScope.paintLabelAbove(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    colour: Color,
    plate: Color = Color.Unspecified,
) {
    val measured = measurer.measure(text, boxStyle(colour))
    val top = y - measured.size.height - LABEL_LIFT.toPx()
    drawLabelPlate(plate, x, top, measured.size.width.toFloat(), measured.size.height.toFloat())
    drawText(measured, topLeft = Offset(x, top))
}

/**
 * A boxed label.
 *
 * Boxed rather than laid straight on the chart, because these carry the numbers a measurement tool
 * exists to report and the background behind them is candles. Unboxed text over a wick is text
 * nobody can read at exactly the moment they need it.
 */
private fun DrawScope.paintBoxLabel(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    colour: Color,
    /**
     * What the words inside the box are written in.
     *
     * Separate from [colour], which is the border: the box is the drawing's frame and the text is
     * the drawing's voice, and a reader who picks a text colour means the second. White is the
     * default because the box is filled with [BOX_BACKGROUND] and nothing else reads on it.
     */
    ink: Color = Color.White,
    anchor: Anchor = Anchor.START,
): Boolean {
    val measured = measurer.measure(text, boxStyle(ink))
    val boxWidth = measured.size.width + BOX_PADDING_X.toPx() * 2
    val boxHeight = measured.size.height + BOX_PADDING_Y.toPx() * 2
    val origin = when (anchor) {
        Anchor.START -> Offset(x, y)
        Anchor.CENTER -> Offset(x - boxWidth / 2, y - boxHeight / 2)
        Anchor.ABOVE -> Offset(x - boxWidth / 2, y - boxHeight - BOX_LIFT.toPx())
    }
    drawRect(BOX_BACKGROUND, origin, Size(boxWidth, boxHeight))
    drawRect(colour, origin, Size(boxWidth, boxHeight), style = Stroke(1f))
    drawText(measured, topLeft = Offset(origin.x + BOX_PADDING_X.toPx(), origin.y + BOX_PADDING_Y.toPx()))
    return true
}

/**
 * The interval a mark was drawn on, as a small chip above and left of its first anchor — item 52.
 *
 * Latin digits and the raw interval code, because "H1" and "M15" are market notation rather than
 * prose: they are the same characters on the timeframe picker, on the header and here, and
 * translating the digits in one of the three would make the reader match them by eye.
 *
 * Muted rather than in the drawing's own colour. This is provenance, not part of the mark, and a
 * tag as loud as the trend line it labels turns twenty marks into forty things to read.
 */
private fun DrawScope.timeframeTag(
    measurer: TextMeasurer,
    timeframe: String,
    at: Offset,
    colour: Color,
) {
    val faded = colour.copy(alpha = colour.alpha * TAG_ALPHA)
    val measured = measurer.measure(timeframe, boxStyle(faded))
    val width = measured.size.width + TAG_PADDING.toPx()
    val height = measured.size.height + 2f
    val origin = Offset(at.x - width - TAG_GAP.toPx(), at.y - height - TAG_GAP.toPx())
    drawRect(BOX_BACKGROUND, origin, Size(width, height))
    drawRect(faded, origin, Size(width, height), style = Stroke(1f))
    drawText(measured, topLeft = Offset(origin.x + TAG_PADDING.toPx() / 2, origin.y + 1f))
}

private fun DrawScope.level(
    measurer: TextMeasurer,
    left: Float,
    right: Float,
    y: Float,
    colour: Color,
    text: String,
    paint: DrawingPaint,
    plate: Color,
) {
    strokeSegment(colour, Offset(left, y), Offset(right, y), 1.5f, paint.dash)
    paintLabelAbove(measurer, text, left + LABEL_INSET.toPx(), y, paint.words(colour), plate)
}

/**
 * The rows of a Fibonacci tool: a line at each ratio, labelled with the ratio and the price it sits
 * at.
 *
 * The price is on the label rather than left to the axis on purpose. A reader placing a limit order
 * at 0.618 needs the number, and reading it off the right-hand axis by eye is how people end up two
 * ticks out.
 */
private fun DrawScope.fibRows(
    measurer: TextMeasurer,
    levels: List<Pair<Double, Double>>,
    fromX: Float,
    toX: Float,
    view: ChartViewport,
    colour: Color,
    width: Float,
    paint: DrawingPaint,
    plate: Color,
): Boolean {
    for ((ratio, price) in levels) {
        val y = view.yOf(price)
        strokeSegment(colour.copy(alpha = 0.85f), Offset(fromX, y), Offset(toX, y), width, paint.dash)
        paintLabelAbove(
            measurer,
            "${fixed(ratio * 100, 1)}%  ${priceText(price)}",
            fromX + LABEL_INSET.toPx(),
            y,
            paint.words(colour),
            plate,
        )
    }
    return true
}

/**
 * A labelled polyline with the leg ratios written on it.
 *
 * The ratios are the whole point of a harmonic pattern — an XABCD is only an XABCD if BC retraces AB
 * by something between 0.382 and 0.886 — so the drawing computes and prints them rather than leaving
 * the reader to measure its own legs.
 */
private fun DrawScope.pattern(
    screen: List<Offset>,
    chart: List<ChartPoint>,
    labels: List<String>,
    ratios: Boolean,
    measurer: TextMeasurer,
    colour: Color,
    width: Float,
    paint: DrawingPaint,
): Boolean {
    if (screen.size < 2) return false
    strokeChain(screen, colour, width, paint.dash)
    for (index in 0 until screen.size - 2) {
        fillPolygon(
            screen[index],
            screen[index + 1],
            screen[index + 2],
            screen[index],
            paint.wash(colour.copy(alpha = PATTERN_FILL)),
        )
    }
    for ((index, point) in screen.withIndex()) {
        val text = labels.getOrNull(index)?.takeIf { it.isNotEmpty() } ?: continue
        drawCircle(Color.White, VERTEX_RADIUS.toPx(), point)
        drawCircle(colour, VERTEX_RADIUS.toPx(), point, style = Stroke(1.5f))
        val measured = measurer.measure(text, boxStyle(paint.words(colour)))
        drawText(measured, topLeft = Offset(point.x - measured.size.width / 2f, point.y - measured.size.height / 2f))
    }
    if (ratios) {
        // Leg midpoints on a tight zigzag sit within a few pixels of each other, and two ratios
        // printed on top of one another read as a longer number that is neither of them. Same rule
        // as the Fibonacci ladders: the drawing is complete without a label, so the label yields.
        var lastLabel: Offset? = null
        for (index in 1 until chart.size - 1) {
            val first = abs(chart[index].price - chart[index - 1].price)
            val second = abs(chart[index + 1].price - chart[index].price)
            val ratio = if (first != 0.0) second / first else 0.0
            val midpoint = Offset(
                (screen[index].x + screen[index + 1].x) / 2,
                (screen[index].y + screen[index + 1].y) / 2,
            )
            val clear = lastLabel?.let {
                hypot(midpoint.x - it.x, midpoint.y - it.y) >= RATIO_LABEL_WIDTH.toPx()
            } ?: true
            if (!clear) continue
            paintLabel(measurer, fixed(ratio, 3), midpoint.x + 4, midpoint.y, paint.words(colour))
            lastLabel = midpoint
        }
    }
    return true
}

// ---------------------------------------------------------------------------- formatting

/** Latin digits and a magnitude-appropriate precision — a chart figure, not prose. */
private fun priceText(value: Double): String = formatPrice(value, decimalsFor(value))

private fun fixed(value: Double, decimals: Int): String = formatPrice(value, decimals)

private fun fixed(value: Float, decimals: Int): String = formatPrice(value.toDouble(), decimals)

/** A duration in the largest unit that is not fractional: days if there are any, otherwise hours. */
private fun spanText(seconds: Long): String {
    val days = seconds / 86_400
    return if (days > 0) "$days روز" else "${seconds / 3600} ساعت"
}

private fun degreesOf(from: Offset, to: Offset): Float =
    (atan2(-(to.y - from.y).toDouble(), (to.x - from.x).toDouble()) * 180 / Math.PI).toFloat()

private fun boxStyle(colour: Color) = TextStyle(color = colour, fontSize = LABEL_SIZE)

// The two market colours, hard-coded rather than read from the theme: a stop-loss band is red in
// both themes and in every terminal, and a drawing that changed sides with the palette would be a
// trap. These are the same values `CoineProColors.Buy`/`Sell` resolve to on the dark stage.
private fun buyColour() = Color(0xFF00B15C)

private fun sellColour() = Color(0xFFF6465D)

private fun gainColour(delta: Double) = if (delta >= 0) buyColour() else sellColour()

// ---------------------------------------------------------------------------- ratios

/** The retracement ladder. The web terminal's, so the two products agree on what 0.618 means. */
private val FIB_RETRACEMENT = listOf(0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0)

/** Projections past the move, for the extension tool. */
private val FIB_EXTENSION = listOf(0.0, 0.618, 1.0, 1.272, 1.618, 2.618)

/** Everything, for the three-point and time tools that project a long way out. */
private val FIB_FULL = listOf(0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0, 1.272, 1.414, 1.618, 2.0, 2.618, 3.618, 4.236)

private val FIB_FAN = listOf(0.236, 0.382, 0.5, 0.618, 0.786, 1.0)

private val FIB_CHANNEL = listOf(0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0, 1.618)

private val FIB_CIRCLES = listOf(0.236, 0.382, 0.5, 0.618, 1.0, 1.618, 2.618)

private val FIB_ARCS = listOf(0.382, 0.5, 0.618, 1.0)

/** The counting sequence itself, for the time zones. */
private val FIB_SEQUENCE = listOf(0, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 144, 233)

private val GANN_RATIOS = listOf(0.0, 0.25, 0.382, 0.5, 0.618, 0.75, 1.0)

/** The fan's nine slopes as multiples of 1×1: 8×1 down to 1×8. */
private val GANN_FAN = listOf(8.0, 4.0, 3.0, 2.0, 1.0, 0.5, 1.0 / 3, 0.25, 0.125)

private val XABCD_LABELS = listOf("X", "A", "B", "C", "D")
private val ABCD_LABELS = listOf("A", "B", "C", "D")
private val HNS_LABELS = listOf("LS", "H", "RS", "", "")
private val IMPULSE_LABELS = listOf("0", "1", "2", "3", "4", "5")
private val ABC_LABELS = listOf("0", "A", "B", "C")

// ---------------------------------------------------------------------------- geometry constants

/** How far past the canvas a ray runs, as a multiple of the longer side. */
private const val RAY_REACH = 4f

/** A pitchfork's tines run three median-lengths forward. */
private const val PITCHFORK_REACH = 3f

private const val SINE_SAMPLES = 120
private const val SINE_CYCLES = 4.0

/** A cyclic tool stops at sixty repeats, which is far more than fits any screen. */
private const val CYCLE_LIMIT = 60

/** How far a Fibonacci ladder runs past the second point, so the labels are not on the last bar. */
private val FIB_OVERHANG = 30.dp

/** A position box extends past its second point, into the space the trade would play out in. */
private val POSITION_OVERHANG = 40.dp

private val ANGLE_RADIUS = 26.dp
private val ANGLE_BASE = 40.dp

private const val FORECAST_SPREAD = 0.4f
private val FORECAST_FLOOR = 10.dp

private val BRACKET = 8.dp
private val ARROW_HEAD = 12.dp
private val ARROW_REACH = 14.dp
private val CALLOUT_DOT = 3.dp
private val NOTE_RADIUS = 7.dp

/** Breathing room inside the price-axis tag, which is a filled chip rather than plain text. */
private val TAG_PADDING = 6.dp

/** How far the timeframe chip sits off the anchor it labels, on both axes. */
private val TAG_GAP = 3.dp

/** How much of the drawing's own colour the timeframe chip keeps. Provenance, not the mark. */
private const val TAG_ALPHA = 0.7f

private val VERTEX_RADIUS = 8.dp

/** Four: an eight-point handle, the design brief's, with a two-point ring. */
private val HANDLE_RADIUS = 4.dp

/**
 * How much larger the handle under a finger gets.
 *
 * Two and a bit, which takes a five-point dot to eleven — past the edge of an average fingertip's
 * contact patch, which is what it has to clear to be seen at all while it is being held.
 */
private const val HANDLE_GRAB_SCALE = 2.2f

/** The halo around the held handle, as a multiple of its grown radius, and how faint it is. */
private const val HANDLE_HALO_SCALE = 1.9f
private const val HANDLE_HALO_ALPHA = 0.18f
private val HANDLE_RING = 2.dp

/** The readout plate's distance from the held ring, its corner, and how much stage it keeps. */
private val READOUT_LIFT = 14.dp
private val READOUT_RADIUS = 4.dp
private const val READOUT_PLATE_ALPHA = 0.92f

private val HIGHLIGHT_WIDTH = 12.dp
private const val HIGHLIGHT_ALPHA = 0.3f

private val DASH_ON = 6.dp
private val DASH_OFF = 4.dp

/**
 * Roughly how wide a ratio label is at [LABEL_SIZE], used to decide whether the next one fits.
 *
 * Measured rather than guessed would be exact, but that means laying out a label to find out
 * whether to lay it out; a constant that errs generous drops one label too many at worst, which is
 * the harmless direction.
 */
private val RATIO_LABEL_WIDTH = 34.dp

private val LABEL_INSET = 4.dp
private val LABEL_LIFT = 2.dp
private val LABEL_SIZE = 9.sp

private val BOX_PADDING_X = 6.dp
private val BOX_PADDING_Y = 4.dp
private val BOX_LIFT = 6.dp
private val BOX_BACKGROUND = Color(0xEB141821)

/** A tool's construction lines, shown at a fraction of its own colour. */
private const val GHOST = 0.5f

/** The fill inside a shape, and the fainter one inside a channel. */
private const val FILL_SOFT = 0.10f
private const val FILL_FAINT = 0.08f
private const val PATTERN_FILL = 0.06f

/** A risk or reward band. Matches the signal overlay, which is the same idea. */
private const val ZONE = 0.12f

/** What a text or callout with nothing typed in it yet says. */
private const val DEFAULT_NOTE = "یادداشت"

// ============================================================================ the second wave

/**
 * The thirty-five drawing tools that arrived with the two pure geometry libraries.
 *
 * One painter per *shape type* rather than one per tool, which is the whole reason this stayed a
 * readable function while the tool count nearly doubled. [DrawingGeometryA] and [DrawingGeometryB]
 * return three shapes between them — a straight run, an arc, a chain of points — so this routes a
 * tool id to the right geometry call and hands whatever comes back to [paintRuns], [paintArcs] or
 * [paintPoly]. A new tool built out of those shapes is one line here.
 *
 * Returns whether the tool was recognised, on the same contract [drawDrawing] has: a recognised
 * tool that had nothing to draw yet — three anchors of a five-anchor pattern, a volume tool on a
 * feed with no volume — still answers true, because the alternative would report a tool as missing
 * from the renderer when it is merely waiting for the reader's next tap.
 */
@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
private fun DrawScope.drawExtendedDrawing(
    drawing: Drawing,
    view: ChartViewport,
    measurer: TextMeasurer,
    chart: List<ChartPoint>,
    screen: List<Offset>,
    colour: Color,
    width: Float,
    paint: DrawingPaint,
    images: DrawingImageSource,
): Boolean {
    val w = view.plotWidth
    val h = view.plotHeight
    val geo = chart.map { GeoPoint(it.time.toDouble(), it.price) }
    val geoB = chart.map { GeoPointB(it.time.toDouble(), it.price) }
    val series = view.series
    val a = screen[0]

    fun runs(list: List<GeoSegment>): Boolean =
        paintRuns(list.map { view.runOf(it) }, measurer, colour, width, w, h, paint)

    fun runsB(list: List<GeoSegmentB>): Boolean =
        paintRuns(list.map { view.runOf(it) }, measurer, colour, width, w, h, paint)

    return when (drawing.toolId) {

        // ── Channels ────────────────────────────────────────────────────────────────
        "regression" -> {
            val from = barIndexOf(series, chart[0].time)
            val to = chart.getOrNull(1)?.let { barIndexOf(series, it.time) } ?: -1
            runs(
                DrawingGeometryA.regressionChannel(
                    points = geo,
                    closes = series.close,
                    fromIndex = min(from, to),
                    toIndex = max(from, to),
                    // The drawing's own value, not the geometry's default. This call omitted the
                    // argument for the life of the tool, so every regression channel in the app sat
                    // at exactly two standard deviations and nothing could say otherwise — the
                    // parameter existed, was documented, and had no way to be reached.
                    deviations = drawing.deviations,
                ),
            )
            true
        }
        "flattop" -> { runs(DrawingGeometryA.flatTopBottom(geo)); true }
        "disjoint" -> { runs(DrawingGeometryA.disjointChannel(geo)); true }
        "pitchfork_inside" -> { runs(DrawingGeometryA.insidePitchfork(geo)); true }
        "pitchfork_schiff" -> { runs(DrawingGeometryA.schiffPitchfork(geo)); true }
        "pitchfork_schiffmod" -> { runs(DrawingGeometryA.modifiedSchiffPitchfork(geo)); true }
        "pitchfan" -> { runs(DrawingGeometryA.pitchfan(geo)); true }

        // ── Fibonacci curves ────────────────────────────────────────────────────────
        "fibspiral" -> { paintArcs(DrawingGeometryA.fibonacciSpiral(geo), view, measurer, colour, width, paint); true }
        "fibwedge" -> {
            // The two rays the arcs are swept between, faint. Without them the wedge is a stack of
            // arcs with nothing saying where the angle came from.
            screen.getOrNull(1)?.let { strokeSegment(colour.copy(alpha = GHOST), a, it, width, paint.dash) }
            screen.getOrNull(2)?.let { strokeSegment(colour.copy(alpha = GHOST), a, it, width, paint.dash) }
            paintArcs(DrawingGeometryA.fibonacciWedge(geo), view, measurer, colour, width, paint)
            true
        }

        // ── Gann squares ────────────────────────────────────────────────────────────
        "gannsquare" -> { runs(DrawingGeometryA.gannSquare(geo)); true }
        "gannsquarefixed" -> {
            // One tap, so the box has to come from somewhere: it is sized so that the 1×1 leaves
            // the anchor at forty-five degrees *on screen*, which is the only reading of "one unit
            // of time equals one unit of price" that survives a chart whose axes share no unit.
            val boxT = GANN_FIXED_BARS * max(1.0, barSpacingOf(series))
            // `priceAt` takes a screen y in Float; the constant is a Double so the time side of the
            // box keeps its precision. Convert at the boundary rather than making the constant a
            // Float, which would quietly coarsen `boxT` above.
            val boxP = view.priceAt(a.y - (GANN_FIXED_BARS * view.barWidth).toFloat()) - chart[0].price
            runs(DrawingGeometryA.gannSquareFixed(geo, boxT, boxP))
            true
        }

        // ── Patterns and Elliott ────────────────────────────────────────────────────
        "threedrives" -> { runsB(DrawingGeometryB.threeDrives(geoB)); true }
        "ell_triangle" -> { runsB(DrawingGeometryB.elliottTriangle(geoB)); true }
        "ell_double" -> { runsB(DrawingGeometryB.elliottDoubleCombo(geoB)); true }
        "ell_triple" -> { runsB(DrawingGeometryB.elliottTripleCombo(geoB)); true }

        // ── Free-form shapes ────────────────────────────────────────────────────────
        "path" -> { paintPoly(DrawingGeometryB.path(geoB), view, colour, width, paint); true }
        "polyline" -> {
            paintPoly(DrawingGeometryB.polyline(geoB, closed = isClosedRing(chart)), view, colour, width, paint)
            true
        }
        "arc" -> { paintPoly(DrawingGeometryB.arc(geoB), view, colour, width, paint); true }
        "curve" -> { paintPoly(DrawingGeometryB.curve(geoB), view, colour, width, paint); true }
        "doublecurve" -> { paintPoly(DrawingGeometryB.doubleCurve(geoB), view, colour, width, paint); true }
        "sector" -> { paintPoly(DrawingGeometryB.sector(geoB), view, colour, width, paint); true }

        // ── Measure ─────────────────────────────────────────────────────────────────
        "timecycles" -> { runsB(DrawingGeometryB.timeCycles(geoB)); true }
        "barspattern" -> {
            val anchor = geoB.getOrNull(1) ?: return true
            val from = barIndexOf(series, chart[0].time)
            val to = barIndexOf(series, chart[1].time)
            val copy = DrawingGeometryB.barsPattern(
                source = series.close,
                sourceOpen = series.open,
                sourceHigh = series.high,
                sourceLow = series.low,
                fromIndex = min(from, to),
                toIndex = max(from, to),
                anchor = anchor,
            )
            paintBarOffsetRuns(copy, view, anchor, colour, max(width, view.bodyWidth * BAR_COPY_RATIO), paint)
            true
        }
        "ghostfeed" -> {
            val anchor = geoB.getOrNull(1) ?: return true
            val from = barIndexOf(series, chart[0].time)
            val to = barIndexOf(series, chart[1].time)
            val window = abs(to - from)
            val ghost = DrawingGeometryB.ghostFeed(
                closes = series.close,
                fromIndex = min(from, to),
                toIndex = max(from, to),
                anchor = anchor,
                bars = window,
            )
            paintBarOffsetPoly(ghost, view, anchor, colour.copy(alpha = GHOST_FEED_ALPHA), width, paint)
            true
        }

        // ── Annotation ──────────────────────────────────────────────────────────────
        "arrowmarks" -> { runsB(DrawingGeometryB.arrowMarks(geoB)); true }
        "pricenote" -> {
            strokeDashed(a, Offset(w, a.y), colour, width, paint.dash)
            val text = listOfNotNull(drawing.text, priceText(chart[0].price)).joinToString("  ")
            paintBoxLabel(measurer, text, a.x + LABEL_INSET.toPx(), a.y, colour, paint.words(Color.White), Anchor.ABOVE)
            true
        }
        "pin" -> {
            val head = Offset(a.x, a.y - PIN_HEIGHT.toPx())
            strokeSegment(colour, head, a, width, paint.dash)
            drawCircle(colour, PIN_RADIUS.toPx(), head)
            drawCircle(BOX_BACKGROUND, PIN_RADIUS.toPx() / 2, head)
            drawing.text?.let {
                paintBoxLabel(measurer, it, head.x + PIN_RADIUS.toPx() + 4, head.y, colour, paint.words(Color.White), Anchor.ABOVE)
            }
            true
        }
        // `tabledraw`, not `table`: the shipped help catalogue keys `table` to the scripting
        // language's `table.new`, so the drawing tool had to be renamed to keep the two «؟» entries
        // apart. The renderer is where a rename like that goes unnoticed — the tool stays in the
        // rail, arms, places its point and then draws nothing.
        "tabledraw" -> {
            panel(measurer, drawing.text ?: DEFAULT_TABLE, a, colour, rule = true, paint = paint)
            true
        }
        "comment" -> {
            // A bubble with a tail, so the note points at the bar it is about. A boxed label on its
            // own says "somewhere near here", which on a crowded chart is not an answer.
            val body = Offset(a.x, a.y - COMMENT_LIFT.toPx())
            val tail = Path().apply {
                moveTo(a.x, a.y)
                lineTo(a.x + COMMENT_TAIL.toPx(), body.y)
                lineTo(a.x - COMMENT_TAIL.toPx() / 2, body.y)
                close()
            }
            drawPath(tail, BOX_BACKGROUND)
            drawPath(tail, colour, style = Stroke(1f))
            panel(measurer, drawing.text ?: DEFAULT_NOTE, Offset(a.x, body.y), colour, rule = false, paint = paint, above = true)
            true
        }
        "signpost" -> {
            val top = Offset(a.x, a.y - POST_HEIGHT.toPx())
            strokeSegment(colour, a, top, width, paint.dash)
            drawCircle(colour, POST_FOOT.toPx(), a)
            panel(measurer, drawing.text ?: DEFAULT_SIGN, top, colour, rule = false, paint = paint, above = true)
            true
        }
        "icon" -> {
            val reach = ICON_SIZE.toPx()
            val diamond = Path().apply {
                moveTo(a.x, a.y - reach)
                lineTo(a.x + reach, a.y)
                lineTo(a.x, a.y + reach)
                lineTo(a.x - reach, a.y)
                close()
            }
            drawPath(diamond, paint.wash(colour.copy(alpha = FILL_SOFT)))
            drawPath(diamond, colour, style = Stroke(width, pathEffect = paint.dash))
            // The glyph goes *inside* the diamond, because that is what an icon is. It used to be
            // set beside it as a caption, which was the only shape available while nothing could
            // set `Drawing.text` at all — the tool drew an empty diamond for every reader.
            val glyph = drawing.text?.take(1) ?: DrawingActions.DEFAULT_ICON_GLYPH
            val measured = measurer.measure(glyph, boxStyle(paint.words(colour)))
            drawText(
                measured,
                topLeft = Offset(a.x - measured.size.width / 2f, a.y - measured.size.height / 2f),
            )
            true
        }
        "image" -> {
            // The reader's own picture, anchored in chart space so it pans and zooms with the bars
            // like every other mark. What this tool drew for the life of the app was a frame, a
            // picture glyph and a caption — an empty box, which reads as a bitmap that failed to
            // load — because nothing in this layer could open a file. It still cannot: the bytes
            // are read by `DrawingImageStore` and decoded into [DrawingImages] above, and all this
            // knows is an id off `Drawing.text` and a bitmap to put in a rectangle.
            val imageId = DrawingImages.idIn(drawing.text)
            val caption = DrawingImages.captionIn(drawing.text)
            val picture = imageId?.let(images::imageFor) ?: DrawingImage.Waiting
            val bitmap = (picture as? DrawingImage.Shown)?.bitmap
            if (bitmap != null) {
                // One anchor or two. The tool is a one-tap tool in the shipped catalogue, so a box
                // has to be derived for it, and it is derived in *bars* rather than in pixels —
                // that is the whole difference between a picture pinned to the chart and a sticker
                // pinned to the glass. Two anchors, once the catalogue offers them, are the
                // reader's own corners and are used exactly as given.
                val span = max(MIN_IMAGE_SPAN.toPx(), view.barWidth * IMAGE_BARS)
                val frame = imageFrame(a, screen.getOrNull(1), span, bitmap.width, bitmap.height)
                val fitted = fitImage(frame, bitmap.width, bitmap.height)
                // Scaled through a transform rather than drawn into an integer destination
                // rectangle: `drawImage`'s sized overload takes `IntOffset`/`IntSize`, and rounding
                // the destination every frame makes a picture shudder against the bars underneath
                // it while the chart is panned.
                withTransform({
                    translate(fitted.left, fitted.top)
                    scale(fitted.width / bitmap.width, fitted.height / bitmap.height, Offset.Zero)
                }) {
                    // The drawing's own opacity, which already carries the demonstration fade — see
                    // the note on [DrawingPaint]. A picture that stayed solid while its frame faded
                    // out would be the one mark on the chart that ignores the clock.
                    drawImage(bitmap, alpha = colour.alpha)
                }
                drawRect(
                    colour,
                    Offset(frame.left, frame.top),
                    Size(frame.width, frame.height),
                    style = Stroke(width, pathEffect = paint.dash),
                )
                caption?.let {
                    paintLabel(measurer, it, frame.left, frame.bottom + LABEL_INSET.toPx(), paint.words(colour))
                }
            } else {
                // No picture yet, or none any more. Both draw the frame this tool has always drawn
                // — the difference is what it says underneath. A drawing whose file is gone must
                // not vanish and must not lie: the reader placed it, so it stays where they put it
                // and tells them the picture is missing, which is something they can act on.
                val size = Size(IMAGE_WIDTH.toPx(), IMAGE_HEIGHT.toPx())
                drawRect(paint.wash(colour.copy(alpha = FILL_SOFT)), a, size)
                drawRect(colour, a, size, style = Stroke(width, pathEffect = paint.dash))
                val floor = a.y + size.height * IMAGE_HORIZON
                strokeSegment(
                    colour,
                    Offset(a.x + size.width * 0.12f, floor),
                    Offset(a.x + size.width * 0.42f, a.y + size.height * 0.42f),
                    width,
                    paint.dash,
                )
                strokeSegment(
                    colour,
                    Offset(a.x + size.width * 0.42f, a.y + size.height * 0.42f),
                    Offset(a.x + size.width * 0.88f, floor),
                    width,
                    paint.dash,
                )
                drawCircle(colour, size.height * 0.09f, Offset(a.x + size.width * 0.74f, a.y + size.height * 0.26f), style = Stroke(width))
                val words = if (picture is DrawingImage.Gone) {
                    listOfNotNull(MISSING_IMAGE_CAPTION, caption).joinToString(" — ")
                } else {
                    caption ?: DEFAULT_IMAGE_CAPTION
                }
                paintLabel(measurer, words, a.x, a.y + size.height + LABEL_INSET.toPx(), paint.words(colour))
            }
            true
        }

        // ── Volume ──────────────────────────────────────────────────────────────────
        "avwap" -> {
            if (!volumeToolDrawable(drawing.toolId, series)) return true
            drawAnchoredVwap(view, measurer, barIndexOf(series, chart[0].time), colour, width, paint)
            true
        }
        "volumeprofile" -> {
            if (!volumeToolDrawable(drawing.toolId, series)) return true
            val end = screen.getOrNull(1) ?: return true
            val from = barIndexOf(series, chart[0].time)
            val to = barIndexOf(series, chart[1].time)
            drawVolumeProfile(
                view = view,
                measurer = measurer,
                fromIndex = min(from, to),
                toIndex = max(from, to),
                leftX = min(a.x, end.x),
                rightX = max(a.x, end.x),
                colour = colour,
                width = width,
                paint = paint,
            )
            true
        }
        "avolumeprofile" -> {
            if (!volumeToolDrawable(drawing.toolId, series)) return true
            val from = barIndexOf(series, chart[0].time)
            if (from < 0) return true
            drawVolumeProfile(
                view = view,
                measurer = measurer,
                fromIndex = from,
                toIndex = series.size - 1,
                leftX = a.x,
                rightX = min(w, view.xOf(series.size - 1)),
                colour = colour,
                width = width,
                paint = paint,
            )
            true
        }

        else -> false
    }
}

// ---------------------------------------------------------------------------- shape painters

/**
 * One straight run of a tool, already in pixels.
 *
 * The two geometry libraries return two segment types that carry the same four facts, and both are
 * flattened to this before anything is stroked. One painter that both feed rather than two that
 * drift: the extend flags and the label placement are exactly the sort of thing that gets fixed in
 * one copy and not the other.
 */
private data class ScreenRun(
    val from: Offset,
    val to: Offset,
    val extendA: Boolean,
    val extendB: Boolean,
    val label: String?,
)

private fun ChartViewport.runOf(segment: GeoSegment) = ScreenRun(
    from = screenOf(segment.a),
    to = screenOf(segment.b),
    extendA = segment.extendA,
    extendB = segment.extendB,
    label = segment.label,
)

private fun ChartViewport.runOf(segment: GeoSegmentB) = ScreenRun(
    from = screenOf(segment.a),
    to = screenOf(segment.b),
    extendA = segment.extendA,
    extendB = segment.extendB,
    label = segment.label,
)

/** A chart-space point in pixels. The one place the geometry's `Double` time is rounded. */
private fun ChartViewport.screenOf(point: GeoPoint): Offset =
    Offset(xOfTime(point.t.roundToLong()), yOf(point.p))

private fun ChartViewport.screenOf(point: GeoPointB): Offset =
    Offset(xOfTime(point.t.roundToLong()), yOf(point.p))

/**
 * Stroke a set of runs, then label them.
 *
 * Two passes and not one, because a Gann square's seventeen lines cross each other: labels drawn
 * inline are struck through by whatever is stroked after them, and the reader is left with ratios
 * that have a rule through the middle.
 *
 * [DrawingGeometryB.MARK_UP] and [DrawingGeometryB.MARK_DOWN] are colour instructions rather than text — they come off `arrowMarks`
 * and `barsPattern` to say which way that piece read — so they choose the market colour and are
 * never drawn as a label.
 */
private fun DrawScope.paintRuns(
    runs: List<ScreenRun>,
    measurer: TextMeasurer,
    colour: Color,
    width: Float,
    w: Float,
    h: Float,
    paint: DrawingPaint,
): Boolean {
    if (runs.isEmpty()) return false
    for (run in runs) {
        strokeRun(run, markColour(run.label, colour), width, w, h, paint.dash)
    }
    val lane = LabelLane(RATIO_LABEL_WIDTH.toPx(), LABEL_MIN_GAP.toPx())
    for (run in runs) {
        val text = run.label?.takeUnless { it == DrawingGeometryB.MARK_UP || it == DrawingGeometryB.MARK_DOWN }
            ?: continue
        val at = labelAnchorOf(run)
        if (at.x < -w || at.x > w * 2) continue
        if (!lane.claim(at.x, at.y)) continue
        paintLabel(measurer, text, at.x + LABEL_INSET.toPx(), at.y, paint.words(colour))
    }
    return true
}

/**
 * Where a run's label goes: its right-hand end, which is the end a reader's eye arrives at.
 *
 * A degenerate run is the exception and is not a degenerate case. `timeCycles` expresses a
 * full-height vertical as a zero-length segment with both extend flags set, so its label belongs at
 * the top of the plot beside the line rather than at a point in the middle of the candles.
 */
private fun labelAnchorOf(run: ScreenRun): Offset {
    if (isDegenerate(run)) return Offset(run.from.x, 0f)
    return if (run.to.x >= run.from.x) run.to else run.from
}

private fun isDegenerate(run: ScreenRun): Boolean =
    hypot(run.to.x - run.from.x, run.to.y - run.from.y) < DEGENERATE_PX

/**
 * Stroke one run, honouring its extend flags with the reach a ray already uses.
 *
 * The zero-length case is the trap the geometry authors flagged and it is handled first: a segment
 * whose two ends coincide *and* which asks to be extended is a full-height vertical, because a
 * library with no viewport cannot express "as tall as the plot" any other way. Skipping it as an
 * empty segment would silently drop every line of the time-cycles tool.
 */
private fun DrawScope.strokeRun(
    run: ScreenRun,
    colour: Color,
    width: Float,
    w: Float,
    h: Float,
    dash: PathEffect?,
) {
    if (isDegenerate(run)) {
        if (run.extendA || run.extendB) {
            strokeSegment(colour, Offset(run.from.x, 0f), Offset(run.from.x, h), width, dash)
        }
        return
    }
    val dx = run.to.x - run.from.x
    val dy = run.to.y - run.from.y
    val length = max(1f, hypot(dx, dy))
    val unit = Offset(dx / length, dy / length)
    val reach = max(w, h) * RAY_REACH
    val start = if (run.extendA) run.from - unit * reach else run.from
    val end = if (run.extendB) run.to + unit * reach else run.to
    strokeSegment(colour, start, end, width, dash)
}

/**
 * Stroke a set of arcs.
 *
 * The two radii are converted through their own axis — the time radius through [ChartViewport.xOfTime],
 * the price radius through [ChartViewport.yOf] — which is what makes a Fibonacci spiral keep its
 * shape at every zoom instead of breathing as the price scale changes.
 *
 * The angle is negated on the way to the canvas. Chart space measures degrees anticlockwise from
 * increasing time, and a canvas measures them clockwise from three o'clock because its y axis points
 * down; a spiral drawn without the negation winds the wrong way and its quarters no longer join.
 */
private fun DrawScope.paintArcs(
    arcs: List<GeoArc>,
    view: ChartViewport,
    measurer: TextMeasurer,
    colour: Color,
    width: Float,
    paint: DrawingPaint,
): Boolean {
    if (arcs.isEmpty()) return false
    val lane = LabelLane(RATIO_LABEL_WIDTH.toPx(), LABEL_MIN_GAP.toPx())
    for (arc in arcs) {
        val centre = view.screenOf(arc.centre)
        val rx = abs(view.xOfTime((arc.centre.t + arc.radiusT).roundToLong()) - centre.x)
        val ry = abs(view.yOf(arc.centre.p + arc.radiusP) - centre.y)
        strokeOval(
            centre,
            rx,
            ry,
            (-arc.startDeg).toFloat(),
            (-arc.sweepDeg).toFloat(),
            colour.copy(alpha = 0.85f),
            width,
            paint.dash,
        )
        val text = arc.label ?: continue
        val radians = arc.startDeg * Math.PI / 180.0
        val at = Offset(
            (centre.x + rx * cos(radians)).toFloat(),
            (centre.y - ry * sin(radians)).toFloat(),
        )
        if (!lane.claim(at.x, at.y)) continue
        paintLabel(measurer, text, at.x + LABEL_INSET.toPx(), at.y, paint.words(colour))
    }
    return true
}

/**
 * Stroke or fill a sampled chain of points.
 *
 * Closed chains are filled at the tool's own colour and low alpha and then outlined with a hairline,
 * which is the treatment every other filled shape in this file gets; open ones are stroked. A chain
 * labelled [DrawingGeometryB.ARROW_HEAD] gets its last leg capped, which is the single thing that
 * separates a path from a polyline.
 */
private fun DrawScope.paintPoly(
    poly: GeoPolyB,
    view: ChartViewport,
    colour: Color,
    width: Float,
    paint: DrawingPaint,
): Boolean {
    if (poly.points.size < 2) return false
    val points = poly.points.map { view.screenOf(it) }
    if (poly.closed) {
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (index in 1 until points.size) lineTo(points[index].x, points[index].y)
            close()
        }
        drawPath(path, paint.wash(colour.copy(alpha = FILL_SOFT)))
        drawPath(path, colour, style = Stroke(width, pathEffect = paint.dash))
        return true
    }
    strokeChain(points, colour, width, paint.dash)
    if (poly.label == DrawingGeometryB.ARROW_HEAD) {
        arrowHead(points[points.size - 2], points.last(), colour, ARROW_HEAD.toPx())
    }
    return true
}

/**
 * The two tools that lay their output one *bar* apart from the anchor rather than one second.
 *
 * `barsPattern` and `ghostFeed` are given no bar spacing and cannot invent one, so they return
 * times as `anchor.t + n` for a whole number of bars. Rounding those to a `Long` and asking the
 * viewport for them would place a thirty-bar copy inside thirty seconds — a vertical smear one
 * pixel wide. The offset is multiplied by the bar width here instead, which is the one place that
 * knows what a bar is worth in pixels.
 */
private fun DrawScope.paintBarOffsetRuns(
    runs: List<GeoSegmentB>,
    view: ChartViewport,
    anchor: GeoPointB,
    colour: Color,
    width: Float,
    paint: DrawingPaint,
) {
    val originX = view.xOfTime(anchor.t.roundToLong())
    for (run in runs) {
        strokeSegment(
            markColour(run.label, colour),
            Offset(originX + ((run.a.t - anchor.t) * view.barWidth).toFloat(), view.yOf(run.a.p)),
            Offset(originX + ((run.b.t - anchor.t) * view.barWidth).toFloat(), view.yOf(run.b.p)),
            width,
            paint.dash,
        )
    }
}

private fun DrawScope.paintBarOffsetPoly(
    poly: GeoPolyB,
    view: ChartViewport,
    anchor: GeoPointB,
    colour: Color,
    width: Float,
    paint: DrawingPaint,
) {
    if (poly.points.size < 2) return
    val originX = view.xOfTime(anchor.t.roundToLong())
    strokeChain(
        poly.points.map { Offset(originX + ((it.t - anchor.t) * view.barWidth).toFloat(), view.yOf(it.p)) },
        colour,
        width,
        paint.dash,
    )
}

/** A `MARK_UP`/`MARK_DOWN` hint resolved to a market colour; anything else keeps the tool's own. */
private fun markColour(label: String?, colour: Color): Color = when (label) {
    DrawingGeometryB.MARK_UP -> buyColour()
    DrawingGeometryB.MARK_DOWN -> sellColour()
    else -> colour
}

/**
 * A bordered panel with the reader's text in it, optionally with a rule under the first line.
 *
 * Shared by the four annotation tools that are a box with words in it. They differ in what points
 * at the box — a post, a tail, nothing — and not in the box, and four copies of the measuring and
 * padding arithmetic is four chances for one of them to sit a pixel off the others.
 */
private fun DrawScope.panel(
    measurer: TextMeasurer,
    text: String,
    at: Offset,
    colour: Color,
    rule: Boolean,
    paint: DrawingPaint,
    above: Boolean = false,
): Boolean {
    val measured = measurer.measure(text, boxStyle(paint.words(Color.White)))
    val boxWidth = measured.size.width + BOX_PADDING_X.toPx() * 2
    val boxHeight = measured.size.height + BOX_PADDING_Y.toPx() * 2
    val origin = if (above) Offset(at.x, at.y - boxHeight) else at
    drawRect(BOX_BACKGROUND, origin, Size(boxWidth, boxHeight))
    drawRect(colour, origin, Size(boxWidth, boxHeight), style = Stroke(1f))
    if (rule && measured.lineCount > 1) {
        val y = origin.y + BOX_PADDING_Y.toPx() + measured.getLineBottom(0)
        strokeSegment(colour.copy(alpha = GHOST), Offset(origin.x, y), Offset(origin.x + boxWidth, y), 1f, null)
    }
    drawText(measured, topLeft = Offset(origin.x + BOX_PADDING_X.toPx(), origin.y + BOX_PADDING_Y.toPx()))
    return true
}

// ---------------------------------------------------------------------------- label collisions

/**
 * Which labels fit, given the ones already placed.
 *
 * A Gann square emits seventeen runs and a pitchfan fourteen, and every one of them carries a ratio.
 * Drawn unmanaged at anything but the widest zoom they overlap into a grey smear that is neither
 * readable nor obviously a set of numbers, which is worse than showing fewer of them: the lines are
 * the tool, the labels are a convenience, and a convenience yields.
 *
 * Collision is judged in **both** axes rather than only vertically, which is not fussiness. A Gann
 * grid's horizontal rules share an x and differ in y, so a vertical-only rule would be right for
 * them — but the time-cycles tool puts a dozen labels along the top of the plot at the *same* y and
 * different x, and a vertical-only rule would suppress all but the first of them.
 *
 * Not a `Set` of rounded positions: two labels a hair either side of a bucket boundary land in
 * different buckets and both draw, which is exactly the case this exists to catch.
 */
internal class LabelLane(private val gapX: Float, private val gapY: Float) {

    private val takenX = ArrayList<Float>()
    private val takenY = ArrayList<Float>()

    /**
     * Claim a position, and say whether the label may be drawn there.
     *
     * A refused claim is *not* recorded. The label was never drawn, so it occupies nothing, and
     * recording it would let one suppressed label cast a shadow that suppresses a second.
     */
    fun claim(x: Float, y: Float): Boolean {
        for (index in takenX.indices) {
            if (abs(x - takenX[index]) < gapX && abs(y - takenY[index]) < gapY) return false
        }
        takenX += x
        takenY += y
        return true
    }

    /** How many labels have actually been placed. The count a test reads to see the rule working. */
    val placed: Int get() = takenX.size
}

// ---------------------------------------------------------------------------- volume tools

/**
 * The three tools that read volume rather than price.
 *
 * Named here rather than inferred from the group, because the renderer must not depend on the
 * rail's grouping to decide whether it is about to draw a lie.
 */
private val VOLUME_TOOLS = setOf("avwap", "volumeprofile", "avolumeprofile")

/**
 * Whether a volume tool has any volume to read.
 *
 * **The MT5 forex feed reports no volume at all**, and [CandleSeries.volume] fills the absent
 * entries with zeros so the panes that must draw something per bar have an array to walk. A volume
 * profile computed over those zeros does not fail — it returns a profile with no point of control
 * and empty rows — and a caller that drew it anyway would put a confident, empty histogram on the
 * chart, which reads as "nothing traded anywhere" rather than as "this feed does not say".
 *
 * So the three volume tools draw nothing on such a feed. They still report themselves as
 * *recognised*, because the tool is implemented; it is the data that is missing, and the rail hides
 * the whole group in that case rather than offering a tool that would draw nothing.
 */
internal fun volumeToolDrawable(toolId: String, series: CandleSeries): Boolean =
    toolId !in VOLUME_TOOLS || series.hasVolume

/**
 * The running volume-weighted mean price from an anchor bar forward.
 *
 * Anchored, so there is no window and no warm-up: the reader's tap *is* the start of the average,
 * which is the whole point of the tool — a VWAP from the day's open, from the gap, from the news.
 * Each bar contributes its typical price weighted by its volume, and the result at bar *n* is every
 * bar from the anchor to *n* and nothing before it.
 *
 * A bar with no volume contributes nothing and does not reset the average, and a window whose
 * volume is still zero falls back to the typical price rather than dividing by it.
 */
internal fun anchoredVwap(
    high: DoubleArray,
    low: DoubleArray,
    close: DoubleArray,
    volume: DoubleArray,
    fromIndex: Int,
    toIndex: Int,
): DoubleArray {
    val size = minOf(high.size, low.size, close.size, volume.size)
    val from = fromIndex.coerceIn(0, max(0, size - 1))
    val to = toIndex.coerceIn(from, max(0, size - 1))
    if (size == 0) return DoubleArray(0)
    val out = DoubleArray(to - from + 1)
    var weighted = 0.0
    var traded = 0.0
    for (index in from..to) {
        val typical = (high[index] + low[index] + close[index]) / 3.0
        val amount = if (volume[index] > 0.0 && volume[index].isFinite()) volume[index] else 0.0
        weighted += typical * amount
        traded += amount
        out[index - from] = if (traded > 0.0) weighted / traded else typical
    }
    return out
}

/** The anchored VWAP as a line from the anchor bar to the last bar, with its value on the end. */
private fun DrawScope.drawAnchoredVwap(
    view: ChartViewport,
    measurer: TextMeasurer,
    fromIndex: Int,
    colour: Color,
    width: Float,
    paint: DrawingPaint,
): Boolean {
    val series = view.series
    if (series.isEmpty || fromIndex < 0) return false
    val last = series.size - 1
    val values = anchoredVwap(series.high, series.low, series.close, series.volume, fromIndex, last)
    if (values.size < 2) return false
    val points = values.indices.map { step ->
        Offset(view.xOf(fromIndex + step), view.yOf(values[step]))
    }
    strokeChain(points, colour, max(width, VWAP_WIDTH.toPx()), paint.dash)
    drawCircle(colour, HANDLE_RADIUS.toPx() / 2, points.first())
    paintLabelAbove(
        measurer,
        "VWAP  ${priceText(values.last())}",
        points.last().x - VWAP_LABEL_BACK.toPx(),
        points.last().y,
        paint.words(colour),
    )
    return true
}

/**
 * The volume profile of one window, drawn against the right-hand end of the drawing's own range.
 *
 * Against the drawing's range and not the pane's edge, which is the difference between a tool that
 * answers "where did this move trade" and one that answers "where did the screen trade": a reader
 * who drags a profile over last Tuesday and finds the histogram pinned to the right of the chart
 * cannot tell which bars it covered.
 *
 * The buy/sell split is drawn only when the range is wide enough for two bars in a row to be told
 * apart. Below that the two pieces are a few pixels each and read as one bar in an arbitrary
 * colour, so the total is drawn in the drawing's own colour instead — fewer facts, none of them
 * misleading.
 *
 * Draws nothing at all when the window carries no volume: [IndicatorsExtC.volumeProfile] reports a
 * point of control of −1 in that case, and flat rows drawn from it would claim the market traded
 * evenly at every price.
 */
private fun DrawScope.drawVolumeProfile(
    view: ChartViewport,
    measurer: TextMeasurer,
    fromIndex: Int,
    toIndex: Int,
    leftX: Float,
    rightX: Float,
    colour: Color,
    width: Float,
    paint: DrawingPaint,
): Boolean {
    val series = view.series
    if (series.isEmpty || fromIndex < 0 || toIndex < fromIndex) return false
    val profile = IndicatorsExtC.volumeProfile(
        high = series.high,
        low = series.low,
        close = series.close,
        open = series.open,
        volume = series.volume,
        fromIndex = fromIndex,
        toIndex = toIndex,
        rows = PROFILE_ROWS,
    )
    if (profile.pocIndex < 0) return false
    var peak = 0.0
    for (value in profile.volume) if (value > peak) peak = value
    if (peak <= 0.0) return false

    val span = max(MIN_PROFILE_SPAN.toPx(), rightX - leftX)
    val split = span >= SPLIT_PROFILE_SPAN.toPx()
    if (profile.valueAreaLow in profile.rowLow.indices && profile.valueAreaHigh in profile.rowHigh.indices) {
        fillBand(
            leftX,
            rightX,
            view.yOf(profile.rowLow[profile.valueAreaLow]),
            view.yOf(profile.rowHigh[profile.valueAreaHigh]),
            paint.wash(colour.copy(alpha = ZONE)),
        )
    }
    for (row in profile.volume.indices) {
        if (profile.volume[row] <= 0.0) continue
        val top = view.yOf(profile.rowHigh[row])
        val bottom = view.yOf(profile.rowLow[row])
        val slot = abs(bottom - top)
        val height = max(1f, slot - ROW_GAP.toPx())
        val y = min(top, bottom) + (slot - height) / 2
        if (split) {
            val buyWidth = (profile.buy[row] / peak).toFloat() * span
            val sellWidth = (profile.sell[row] / peak).toFloat() * span
            drawRect(buyColour().copy(alpha = PROFILE_FILL), Offset(rightX - buyWidth, y), Size(max(0f, buyWidth), height))
            drawRect(
                sellColour().copy(alpha = PROFILE_FILL),
                Offset(rightX - buyWidth - sellWidth, y),
                Size(max(0f, sellWidth), height),
            )
        } else {
            val full = (profile.volume[row] / peak).toFloat() * span
            drawRect(paint.wash(colour.copy(alpha = PROFILE_FILL)), Offset(rightX - full, y), Size(max(0f, full), height))
        }
    }
    val control = (profile.rowLow[profile.pocIndex] + profile.rowHigh[profile.pocIndex]) / 2
    val pocY = view.yOf(control)
    strokeSegment(colour, Offset(leftX, pocY), Offset(rightX, pocY), max(width, POC_WIDTH.toPx()), paint.dash)
    paintLabelAbove(measurer, "POC  ${priceText(control)}", leftX + LABEL_INSET.toPx(), pocY, paint.words(colour))
    return true
}

// ---------------------------------------------------------------------------- series lookups

/**
 * The bar nearest a moment, or −1 on an empty series.
 *
 * Nearest rather than containing: a drawing's anchor is a time the reader's finger landed on, which
 * is somewhere inside a bar and, once the anchor has been dragged past the live edge, may be after
 * every bar there is. Clamping to the ends is the honest answer in both directions.
 */
private fun barIndexOf(series: CandleSeries, time: Long): Int {
    if (series.isEmpty) return -1
    val times = series.time
    if (time <= times[0]) return 0
    if (time >= times[times.size - 1]) return times.size - 1
    var low = 0
    var high = times.size - 1
    while (low < high) {
        val middle = (low + high) / 2
        if (times[middle] < time) low = middle + 1 else high = middle
    }
    val before = max(0, low - 1)
    return if (abs(times[low] - time) <= abs(times[before] - time)) low else before
}

/** The series' median-ish bar spacing in seconds, or one when there is nothing to measure. */
private fun barSpacingOf(series: CandleSeries): Double {
    if (series.size < 2) return 1.0
    val span = series.time[series.size - 1] - series.time[0]
    if (span <= 0) return 1.0
    return span.toDouble() / (series.size - 1)
}

/**
 * Whether a free-form chain was closed by a tap on its own first point.
 *
 * Closure is stored by repeating the first anchor at the end rather than by a flag, which is what
 * lets [Drawing] stay the shape it has always been. Three anchors minimum, because two points that
 * happen to coincide are a reader who tapped twice, not a shape.
 */
private fun isClosedRing(points: List<ChartPoint>): Boolean =
    points.size > 2 && points.first() == points.last()

// ---------------------------------------------------------------------------- second-wave constants

/** How wide the box of a one-tap Gann square is, in bars. */
private const val GANN_FIXED_BARS = 20.0

/** A run shorter than this in pixels is the geometry's way of writing "a full-height vertical". */
private const val DEGENERATE_PX = 0.5f

/** How near two labels may come vertically before the second one yields. */
private val LABEL_MIN_GAP = 11.dp

/** How thick a copied bar is drawn, as a fraction of a real candle body. */
private const val BAR_COPY_RATIO = 0.6f

/** A projection is a hypothesis, and is drawn as one. */
private const val GHOST_FEED_ALPHA = 0.7f

private val PIN_HEIGHT = 22.dp
private val PIN_RADIUS = 5.dp
private val POST_HEIGHT = 30.dp
private val POST_FOOT = 3.dp
private val COMMENT_LIFT = 16.dp
private val COMMENT_TAIL = 7.dp
private val ICON_SIZE = 7.dp
private val IMAGE_WIDTH = 76.dp
private val IMAGE_HEIGHT = 52.dp
private const val IMAGE_HORIZON = 0.74f

/**
 * How many bars wide a one-tap picture is drawn.
 *
 * The number that turns a single anchor into a box. Twenty-four bars is about a fifth of a phone
 * chart's visible range at the default zoom — big enough to read a screenshot pasted into it,
 * small enough that placing one does not hide the price it was placed next to. In *bars*, so the
 * picture grows and shrinks with the chart the way its anchor does.
 */
private const val IMAGE_BARS = 24f

/**
 * The narrowest a picture is allowed to get, whatever the zoom.
 *
 * A floor and not a scale: zoomed far enough out, twenty-four bars is a dozen pixels, and a picture
 * that small is not a small picture — it is a speck the reader cannot see, cannot recognise and
 * cannot grab a handle on to delete. Below this the drawing stops shrinking, which breaks the
 * chart-space rule deliberately and only where honouring it would make the mark unusable.
 */
private val MIN_IMAGE_SPAN = 48.dp

/** How many price bands a drawn profile is cut into. Matches the volume-profile indicator. */
private const val PROFILE_ROWS = 24

/** The histogram's fill. Solid enough to read against candles, faint enough not to bury them. */
private const val PROFILE_FILL = 0.45f

/** A profile narrower than this has nowhere to put a bar; it is widened to this instead. */
private val MIN_PROFILE_SPAN = 24.dp

/** Below this the buy and sell pieces are a few pixels each, and the total is drawn instead. */
private val SPLIT_PROFILE_SPAN = 64.dp

/** The gap between two rows of the histogram, so the bars read as bars rather than as a block. */
private val ROW_GAP = 1.dp

/** The point of control is a hairline, and a distinctly heavier one than the rows around it. */
private val POC_WIDTH = 2.dp

private val VWAP_WIDTH = 2.dp

/** How far back from the last bar the VWAP's own value is written, so it is not off the edge. */
private val VWAP_LABEL_BACK = 60.dp

/** What a table with nothing typed into it yet shows. */
private const val DEFAULT_TABLE = "عنوان\nمقدار"

/** What a signpost with nothing typed into it yet shows. */
private const val DEFAULT_SIGN = "نشانه"

/**
 * What an image frame with no picture in it yet says, and it says what the tool is.
 *
 * An unlabelled empty rectangle reads as a bitmap that failed to load; a labelled one reads as a
 * frame waiting for a picture, which is what it is between the tap that places it and the moment
 * the reader picks one.
 */
private const val DEFAULT_IMAGE_CAPTION = "قاب تصویر"

/**
 * What a frame whose picture is not on disk any more says.
 *
 * The honest degrade, and the reason [DrawingImage] has three states rather than two. A reinstall,
 * a cleared storage or a restored backup takes the files and leaves the drawings, and a reader who
 * finds a blank frame where their screenshot was is owed the reason — otherwise the app looks like
 * it lost the picture at random, which is the one reading that stops them trusting the tool.
 */
private const val MISSING_IMAGE_CAPTION = "تصویر یافت نشد"
