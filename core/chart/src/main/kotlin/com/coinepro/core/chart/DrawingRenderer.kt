package com.coinepro.core.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.sin

/**
 * The fifty drawing tools, drawn.
 *
 * This is the port of the web terminal's `drawings.js` and `drawtools_ext.js` — the geometry, not
 * the plumbing. Every formula here is the one that shipped in Pro-Chart, which matters for a reason
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
): Boolean {
    val chart = drawing.points
    if (chart.isEmpty()) return false
    val p = chart.map { Offset(view.xOfTime(it.time), view.yOf(it.price)) }
    val colour = Color(drawing.colour.toInt())
    val width = max(1f, drawing.widthDp.dp.toPx())
    val w = view.plotWidth
    val h = view.plotHeight
    // A tool half-placed draws what it has so far and nothing it does not: an XABCD with three of
    // its five points is a three-point polyline, not a guess at where D will land.
    val a = p[0]
    val b = p.getOrNull(1)
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
            val text = "Δ ${priceText(delta)}\n${fixed(percent, 2)}٪ | $bars بار\n${fixed(degreesOf(a, end), 1)}°"
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
            )
        } != null
        "fibfan" -> b?.let { end ->
            drawRect(
                color = colour.copy(alpha = 0.25f),
                topLeft = Offset(min(a.x, end.x), min(a.y, end.y)),
                size = Size(abs(end.x - a.x), abs(end.y - a.y)),
                style = Stroke(width),
            )
            for (level in FIB_FAN) {
                val y = a.y + (end.y - a.y) * level.toFloat()
                ray(a, Offset(end.x, y), colour.copy(alpha = 0.85f), width, w, h, both = false)
                label(measurer, "${fixed(level * 100, 1)}٪", end.x + 3, y, colour)
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
                label(measurer, "${fixed(level * 100, 1)}٪", at.x + 3, at.y, colour)
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
            drawRect(colour, Offset(x, y), Size(boxWidth, boxHeight), style = Stroke(width))
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
        "xabcd" -> pattern(p, chart, XABCD_LABELS, ratios = true, measurer, colour, width)
        "abcd" -> pattern(p, chart, ABCD_LABELS, ratios = true, measurer, colour, width)
        "cypher" -> pattern(p, chart, XABCD_LABELS, ratios = true, measurer, colour, width)
        "hns" -> pattern(p, chart, HNS_LABELS, ratios = false, measurer, colour, width)
        "ell_impulse" -> pattern(p, chart, IMPULSE_LABELS, ratios = false, measurer, colour, width)
        "ell_abc" -> pattern(p, chart, ABC_LABELS, ratios = false, measurer, colour, width)
        "tripattern", "triangle" -> p.getOrNull(2)?.let { third ->
            fillQuad(a, b!!, third, third, colour.copy(alpha = FILL_SOFT))
            polyline(listOf(a, b, third, a), colour, width)
        } != null

        // ── Shapes ──────────────────────────────────────────────────────────────────
        "rect" -> b?.let { end ->
            val topLeft = Offset(min(a.x, end.x), min(a.y, end.y))
            val size = Size(abs(end.x - a.x), abs(end.y - a.y))
            drawRect(colour.copy(alpha = FILL_SOFT), topLeft, size, style = Fill)
            drawRect(colour, topLeft, size, style = Stroke(width))
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
            drawCircle(colour.copy(alpha = FILL_SOFT), radius, a)
            drawCircle(colour, radius, a, style = Stroke(width))
            true
        } != null
        "ellipse" -> b?.let { end ->
            val centre = Offset((a.x + end.x) / 2, (a.y + end.y) / 2)
            val rx = abs(end.x - a.x) / 2
            val ry = abs(end.y - a.y) / 2
            drawOval(colour.copy(alpha = FILL_SOFT), Offset(centre.x - rx, centre.y - ry), Size(rx * 2, ry * 2))
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
            // Entry and stop are tapped; the target is the two-to-one that makes the drawing worth
            // having. A reader who wants a different multiple drags the target line afterwards.
            val entry = chart[0].price
            val stop = chart[1].price
            val target = entry + 2 * (entry - stop)
            val left = min(a.x, end.x)
            val right = max(a.x, end.x) + POSITION_OVERHANG.toPx()
            band(left, right, view.yOf(entry), view.yOf(target), buyColour().copy(alpha = ZONE))
            band(left, right, view.yOf(entry), view.yOf(stop), sellColour().copy(alpha = ZONE))
            level(measurer, left, right, view.yOf(target), buyColour(), "هدف ۲R")
            level(measurer, left, right, view.yOf(entry), colour, "ورود")
            level(measurer, left, right, view.yOf(stop), sellColour(), "حد ضرر")
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
            boxLabel(measurer, "${priceText(delta)}\n${fixed(percent, 2)}٪", centre, (a.y + end.y) / 2, colour, Anchor.CENTER)
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
            drawRect(colour.copy(alpha = FILL_SOFT), topLeft, size, style = Fill)
            drawRect(colour, topLeft, size, style = Stroke(width))
            val delta = chart[1].price - chart[0].price
            val percent = if (chart[0].price != 0.0) delta / chart[0].price * 100 else 0.0
            val bars = abs((size.width / max(1f, view.barWidth)).roundToInt())
            val text = "${priceText(delta)} (${fixed(percent, 2)}٪)\n$bars بار | ${spanText(abs(chart[1].time - chart[0].time))}"
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
            boxLabel(measurer, "${fixed(percent, 2)}٪", end.x + 4, end.y, colour)
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
                text = "$arrow ${priceText(abs(delta))} (${fixed(percent, 2)}٪)\n$bars بار",
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

        else -> false
    }

    // The handles come last so they sit over whatever the tool drew, and only on the selected one:
    // eight white dots on every drawing would bury the chart under its own annotations.
    if (handled && selected) {
        for (point in p) {
            drawCircle(Color.White, HANDLE_RADIUS.toPx(), point)
            drawCircle(colour, HANDLE_RADIUS.toPx(), point, style = Stroke(HANDLE_RING.toPx()))
        }
    }
    return handled
}

/** Which way a standalone arrow marker points. */
enum class ArrowDirection { UP, DOWN, LEFT, RIGHT }

// ---------------------------------------------------------------------------- primitives

private fun DrawScope.drawLine(colour: Color, from: Offset, to: Offset, width: Float) {
    drawLine(color = colour, start = from, end = to, strokeWidth = width)
}

private fun DrawScope.dashed(from: Offset, to: Offset, colour: Color, width: Float) {
    drawLine(
        color = colour,
        start = from,
        end = to,
        strokeWidth = width,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON.toPx(), DASH_OFF.toPx())),
    )
}

/**
 * A ray from [from] through [to], continued to well past the canvas.
 *
 * Four times the longer side rather than a fixed constant: a fixed one either stops short on a
 * tablet or wastes most of its length on a phone, and a ray that stops short reads as a trend line
 * that someone drew badly.
 */
private fun DrawScope.ray(
    from: Offset,
    to: Offset,
    colour: Color,
    width: Float,
    w: Float,
    h: Float,
    both: Boolean,
) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val length = max(1f, hypot(dx, dy))
    val reach = max(w, h) * RAY_REACH
    val unit = Offset(dx / length, dy / length)
    val start = if (both) from - unit * reach else from
    drawLine(colour, start, from + unit * reach, width)
}

private fun DrawScope.polyline(points: List<Offset>, colour: Color, width: Float): Boolean {
    if (points.size < 2) return false
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (index in 1 until points.size) lineTo(points[index].x, points[index].y)
    }
    drawPath(path, colour, style = Stroke(width))
    return true
}

private fun DrawScope.fillQuad(a: Offset, b: Offset, c: Offset, d: Offset, colour: Color) {
    val path = Path().apply {
        moveTo(a.x, a.y)
        lineTo(b.x, b.y)
        lineTo(c.x, c.y)
        lineTo(d.x, d.y)
        close()
    }
    drawPath(path, colour)
}

private fun DrawScope.oval(
    centre: Offset,
    rx: Float,
    ry: Float,
    startAngle: Float,
    sweep: Float,
    colour: Color,
    width: Float,
) {
    if (rx <= 0f || ry <= 0f) return
    drawArc(
        color = colour,
        startAngle = startAngle,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(centre.x - rx, centre.y - ry),
        size = Size(rx * 2, ry * 2),
        style = Stroke(width),
    )
}

private fun DrawScope.band(left: Float, right: Float, y0: Float, y1: Float, colour: Color) {
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

private fun DrawScope.label(measurer: TextMeasurer, text: String, x: Float, y: Float, colour: Color) {
    val measured = measurer.measure(text, boxStyle(colour))
    drawText(measured, topLeft = Offset(x, y))
}

/**
 * A label that clears the line it belongs to.
 *
 * [y] is the line, not the text: the label is lifted by its own measured height so the line runs
 * under it rather than through it. Passing the line's y straight to [label] put a Fibonacci price
 * with a blue rule struck through the middle of it, which the screenshot caught and no amount of
 * reading the code would have.
 */
private fun DrawScope.labelAbove(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    colour: Color,
) {
    val measured = measurer.measure(text, boxStyle(colour))
    drawText(measured, topLeft = Offset(x, y - measured.size.height - LABEL_LIFT.toPx()))
}

/**
 * A boxed label.
 *
 * Boxed rather than laid straight on the chart, because these carry the numbers a measurement tool
 * exists to report and the background behind them is candles. Unboxed text over a wick is text
 * nobody can read at exactly the moment they need it.
 */
private fun DrawScope.boxLabel(
    measurer: TextMeasurer,
    text: String,
    x: Float,
    y: Float,
    colour: Color,
    anchor: Anchor = Anchor.START,
): Boolean {
    val measured = measurer.measure(text, boxStyle(Color.White))
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

private fun DrawScope.level(
    measurer: TextMeasurer,
    left: Float,
    right: Float,
    y: Float,
    colour: Color,
    text: String,
) {
    drawLine(colour, Offset(left, y), Offset(right, y), 1.5f)
    labelAbove(measurer, text, left + LABEL_INSET.toPx(), y, colour)
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
): Boolean {
    for ((ratio, price) in levels) {
        val y = view.yOf(price)
        drawLine(colour.copy(alpha = 0.85f), Offset(fromX, y), Offset(toX, y), width)
        labelAbove(measurer, "${fixed(ratio * 100, 1)}٪  ${priceText(price)}", fromX + LABEL_INSET.toPx(), y, colour)
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
): Boolean {
    if (screen.size < 2) return false
    polyline(screen, colour, width)
    for (index in 0 until screen.size - 2) {
        fillQuad(screen[index], screen[index + 1], screen[index + 2], screen[index], colour.copy(alpha = PATTERN_FILL))
    }
    for ((index, point) in screen.withIndex()) {
        val text = labels.getOrNull(index)?.takeIf { it.isNotEmpty() } ?: continue
        drawCircle(Color.White, VERTEX_RADIUS.toPx(), point)
        drawCircle(colour, VERTEX_RADIUS.toPx(), point, style = Stroke(1.5f))
        val measured = measurer.measure(text, boxStyle(colour))
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
            label(measurer, fixed(ratio, 3), midpoint.x + 4, midpoint.y, colour)
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

private val VERTEX_RADIUS = 8.dp

private val HANDLE_RADIUS = 5.dp
private val HANDLE_RING = 2.dp

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
