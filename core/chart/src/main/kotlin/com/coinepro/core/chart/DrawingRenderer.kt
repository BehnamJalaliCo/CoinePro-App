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
import kotlin.math.roundToLong
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
            band(left, right, view.yOf(entry), view.yOf(target), buyColour().copy(alpha = ZONE))
            band(left, right, view.yOf(entry), view.yOf(stop), sellColour().copy(alpha = ZONE))
            // Latin digits on the multiple: it is a market figure sitting on the chart's own
            // canvas beside prices, and «هدف ۲٫۵R» in a column of Latin numbers reads as a
            // different kind of thing from what it is.
            level(measurer, left, right, view.yOf(target), buyColour(), "هدف " + fixed(reward, 1) + "R")
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

        // Everything the second wave of tools added. Split into its own function rather than
        // grown onto the end of this `when`: the branch list above is already at the size where a
        // reader loses their place in it, and the new tools all share a shape — chart-space
        // geometry from [DrawingGeometryA]/[DrawingGeometryB], projected and stroked — that has
        // nothing to do with the hand-rolled arithmetic above.
        else -> drawExtendedDrawing(drawing, view, measurer, chart, p, colour, width)
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
): Boolean {
    val w = view.plotWidth
    val h = view.plotHeight
    val geo = chart.map { GeoPoint(it.time.toDouble(), it.price) }
    val geoB = chart.map { GeoPointB(it.time.toDouble(), it.price) }
    val series = view.series
    val a = screen[0]

    fun runs(list: List<GeoSegment>): Boolean =
        paintRuns(list.map { view.runOf(it) }, measurer, colour, width, w, h)

    fun runsB(list: List<GeoSegmentB>): Boolean =
        paintRuns(list.map { view.runOf(it) }, measurer, colour, width, w, h)

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
        "fibspiral" -> { paintArcs(DrawingGeometryA.fibonacciSpiral(geo), view, measurer, colour, width); true }
        "fibwedge" -> {
            // The two rays the arcs are swept between, faint. Without them the wedge is a stack of
            // arcs with nothing saying where the angle came from.
            screen.getOrNull(1)?.let { drawLine(colour.copy(alpha = GHOST), a, it, width) }
            screen.getOrNull(2)?.let { drawLine(colour.copy(alpha = GHOST), a, it, width) }
            paintArcs(DrawingGeometryA.fibonacciWedge(geo), view, measurer, colour, width)
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
        "path" -> { paintPoly(DrawingGeometryB.path(geoB), view, colour, width); true }
        "polyline" -> {
            paintPoly(DrawingGeometryB.polyline(geoB, closed = isClosedRing(chart)), view, colour, width)
            true
        }
        "arc" -> { paintPoly(DrawingGeometryB.arc(geoB), view, colour, width); true }
        "curve" -> { paintPoly(DrawingGeometryB.curve(geoB), view, colour, width); true }
        "doublecurve" -> { paintPoly(DrawingGeometryB.doubleCurve(geoB), view, colour, width); true }
        "sector" -> { paintPoly(DrawingGeometryB.sector(geoB), view, colour, width); true }

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
            paintBarOffsetRuns(copy, view, anchor, colour, max(width, view.bodyWidth * BAR_COPY_RATIO))
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
            paintBarOffsetPoly(ghost, view, anchor, colour.copy(alpha = GHOST_FEED_ALPHA), width)
            true
        }

        // ── Annotation ──────────────────────────────────────────────────────────────
        "arrowmarks" -> { runsB(DrawingGeometryB.arrowMarks(geoB)); true }
        "pricenote" -> {
            dashed(a, Offset(w, a.y), colour, width)
            val text = listOfNotNull(drawing.text, priceText(chart[0].price)).joinToString("  ")
            boxLabel(measurer, text, a.x + LABEL_INSET.toPx(), a.y, colour, Anchor.ABOVE)
            true
        }
        "pin" -> {
            val head = Offset(a.x, a.y - PIN_HEIGHT.toPx())
            drawLine(colour, head, a, width)
            drawCircle(colour, PIN_RADIUS.toPx(), head)
            drawCircle(BOX_BACKGROUND, PIN_RADIUS.toPx() / 2, head)
            drawing.text?.let { boxLabel(measurer, it, head.x + PIN_RADIUS.toPx() + 4, head.y, colour, Anchor.ABOVE) }
            true
        }
        // `tabledraw`, not `table`: the shipped help catalogue keys `table` to the scripting
        // language's `table.new`, so the drawing tool had to be renamed to keep the two «؟» entries
        // apart. The renderer is where a rename like that goes unnoticed — the tool stays in the
        // rail, arms, places its point and then draws nothing.
        "tabledraw" -> {
            panel(measurer, drawing.text ?: DEFAULT_TABLE, a, colour, rule = true)
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
            panel(measurer, drawing.text ?: DEFAULT_NOTE, Offset(a.x, body.y), colour, rule = false, above = true)
            true
        }
        "signpost" -> {
            val top = Offset(a.x, a.y - POST_HEIGHT.toPx())
            drawLine(colour, a, top, width)
            drawCircle(colour, POST_FOOT.toPx(), a)
            panel(measurer, drawing.text ?: DEFAULT_SIGN, top, colour, rule = false, above = true)
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
            drawPath(diamond, colour.copy(alpha = FILL_SOFT))
            drawPath(diamond, colour, style = Stroke(width))
            drawing.text?.let { label(measurer, it, a.x + reach + 4, a.y - reach, colour) }
            true
        }
        "image" -> {
            // A frame and the picture's own mark, not a placeholder for one. The bitmap itself is
            // not the chart layer's business — nothing here can load a file — so what this tool
            // places is the frame the reader positions, and the caption under it.
            val size = Size(IMAGE_WIDTH.toPx(), IMAGE_HEIGHT.toPx())
            drawRect(colour.copy(alpha = FILL_SOFT), a, size)
            drawRect(colour, a, size, style = Stroke(width))
            val floor = a.y + size.height * IMAGE_HORIZON
            drawLine(colour, Offset(a.x + size.width * 0.12f, floor), Offset(a.x + size.width * 0.42f, a.y + size.height * 0.42f), width)
            drawLine(colour, Offset(a.x + size.width * 0.42f, a.y + size.height * 0.42f), Offset(a.x + size.width * 0.88f, floor), width)
            drawCircle(colour, size.height * 0.09f, Offset(a.x + size.width * 0.74f, a.y + size.height * 0.26f), style = Stroke(width))
            drawing.text?.let { label(measurer, it, a.x, a.y + size.height + LABEL_INSET.toPx(), colour) }
            true
        }

        // ── Volume ──────────────────────────────────────────────────────────────────
        "avwap" -> {
            if (!volumeToolDrawable(drawing.toolId, series)) return true
            drawAnchoredVwap(view, measurer, barIndexOf(series, chart[0].time), colour, width)
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
): Boolean {
    if (runs.isEmpty()) return false
    for (run in runs) {
        strokeRun(run, markColour(run.label, colour), width, w, h)
    }
    val lane = LabelLane(RATIO_LABEL_WIDTH.toPx(), LABEL_MIN_GAP.toPx())
    for (run in runs) {
        val text = run.label?.takeUnless { it == DrawingGeometryB.MARK_UP || it == DrawingGeometryB.MARK_DOWN }
            ?: continue
        val at = labelAnchorOf(run)
        if (at.x < -w || at.x > w * 2) continue
        if (!lane.claim(at.x, at.y)) continue
        label(measurer, text, at.x + LABEL_INSET.toPx(), at.y, colour)
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
private fun DrawScope.strokeRun(run: ScreenRun, colour: Color, width: Float, w: Float, h: Float) {
    if (isDegenerate(run)) {
        if (run.extendA || run.extendB) drawLine(colour, Offset(run.from.x, 0f), Offset(run.from.x, h), width)
        return
    }
    val dx = run.to.x - run.from.x
    val dy = run.to.y - run.from.y
    val length = max(1f, hypot(dx, dy))
    val unit = Offset(dx / length, dy / length)
    val reach = max(w, h) * RAY_REACH
    val start = if (run.extendA) run.from - unit * reach else run.from
    val end = if (run.extendB) run.to + unit * reach else run.to
    drawLine(colour, start, end, width)
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
): Boolean {
    if (arcs.isEmpty()) return false
    val lane = LabelLane(RATIO_LABEL_WIDTH.toPx(), LABEL_MIN_GAP.toPx())
    for (arc in arcs) {
        val centre = view.screenOf(arc.centre)
        val rx = abs(view.xOfTime((arc.centre.t + arc.radiusT).roundToLong()) - centre.x)
        val ry = abs(view.yOf(arc.centre.p + arc.radiusP) - centre.y)
        oval(centre, rx, ry, (-arc.startDeg).toFloat(), (-arc.sweepDeg).toFloat(), colour.copy(alpha = 0.85f), width)
        val text = arc.label ?: continue
        val radians = arc.startDeg * Math.PI / 180.0
        val at = Offset(
            (centre.x + rx * cos(radians)).toFloat(),
            (centre.y - ry * sin(radians)).toFloat(),
        )
        if (!lane.claim(at.x, at.y)) continue
        label(measurer, text, at.x + LABEL_INSET.toPx(), at.y, colour)
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
): Boolean {
    if (poly.points.size < 2) return false
    val points = poly.points.map { view.screenOf(it) }
    if (poly.closed) {
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (index in 1 until points.size) lineTo(points[index].x, points[index].y)
            close()
        }
        drawPath(path, colour.copy(alpha = FILL_SOFT))
        drawPath(path, colour, style = Stroke(width))
        return true
    }
    polyline(points, colour, width)
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
) {
    val originX = view.xOfTime(anchor.t.roundToLong())
    for (run in runs) {
        val ink = markColour(run.label, colour)
        drawLine(
            ink,
            Offset(originX + ((run.a.t - anchor.t) * view.barWidth).toFloat(), view.yOf(run.a.p)),
            Offset(originX + ((run.b.t - anchor.t) * view.barWidth).toFloat(), view.yOf(run.b.p)),
            width,
        )
    }
}

private fun DrawScope.paintBarOffsetPoly(
    poly: GeoPolyB,
    view: ChartViewport,
    anchor: GeoPointB,
    colour: Color,
    width: Float,
) {
    if (poly.points.size < 2) return
    val originX = view.xOfTime(anchor.t.roundToLong())
    polyline(
        poly.points.map { Offset(originX + ((it.t - anchor.t) * view.barWidth).toFloat(), view.yOf(it.p)) },
        colour,
        width,
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
    above: Boolean = false,
): Boolean {
    val measured = measurer.measure(text, boxStyle(Color.White))
    val boxWidth = measured.size.width + BOX_PADDING_X.toPx() * 2
    val boxHeight = measured.size.height + BOX_PADDING_Y.toPx() * 2
    val origin = if (above) Offset(at.x, at.y - boxHeight) else at
    drawRect(BOX_BACKGROUND, origin, Size(boxWidth, boxHeight))
    drawRect(colour, origin, Size(boxWidth, boxHeight), style = Stroke(1f))
    if (rule && measured.lineCount > 1) {
        val y = origin.y + BOX_PADDING_Y.toPx() + measured.getLineBottom(0)
        drawLine(colour.copy(alpha = GHOST), Offset(origin.x, y), Offset(origin.x + boxWidth, y), 1f)
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
): Boolean {
    val series = view.series
    if (series.isEmpty || fromIndex < 0) return false
    val last = series.size - 1
    val values = anchoredVwap(series.high, series.low, series.close, series.volume, fromIndex, last)
    if (values.size < 2) return false
    val points = values.indices.map { step ->
        Offset(view.xOf(fromIndex + step), view.yOf(values[step]))
    }
    polyline(points, colour, max(width, VWAP_WIDTH.toPx()))
    drawCircle(colour, HANDLE_RADIUS.toPx() / 2, points.first())
    labelAbove(measurer, "VWAP  ${priceText(values.last())}", points.last().x - VWAP_LABEL_BACK.toPx(), points.last().y, colour)
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
        band(
            leftX,
            rightX,
            view.yOf(profile.rowLow[profile.valueAreaLow]),
            view.yOf(profile.rowHigh[profile.valueAreaHigh]),
            colour.copy(alpha = ZONE),
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
            drawRect(colour.copy(alpha = PROFILE_FILL), Offset(rightX - full, y), Size(max(0f, full), height))
        }
    }
    val control = (profile.rowLow[profile.pocIndex] + profile.rowHigh[profile.pocIndex]) / 2
    val pocY = view.yOf(control)
    drawLine(colour, Offset(leftX, pocY), Offset(rightX, pocY), max(width, POC_WIDTH.toPx()))
    labelAbove(measurer, "POC  ${priceText(control)}", leftX + LABEL_INSET.toPx(), pocY, colour)
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
