package com.coinepro.feature.alerts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors

/**
 * The line an alert watches, drawn small beside it.
 *
 * ### What this is, precisely, so nobody mistakes it for a chart
 *
 * A polyline through the drawing's **stored anchors**, normalised into this box. Not a render: this
 * module does not depend on `core:chart` and must not start — [AlertDrawings] has the argument, and
 * the short version is that a second geometry engine would one day tell a reader about a touch that
 * is not on the chart in front of them. What the thumbnail answers is «which of my three trend
 * lines is this», which the anchors answer completely.
 *
 * Time runs along x and price up y, both scaled to whatever the anchors span, so a nearly flat line
 * still reads as flat and a steep one as steep **relative to itself**. A span of zero on either
 * axis is centred rather than divided by, which is what a horizontal line is.
 *
 * ### One anchor is a level, and that is not a degenerate case
 *
 * `hline`, `pricelabel` and the rest of the price marks store a single point. They draw as a
 * horizontal line across the middle of the box, because that is exactly what they are on a chart —
 * not as a dot, which would read as a fault.
 */
@Composable
internal fun AlertDrawingSketch(
    points: List<Pair<Long, Double>>,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    tint: Color = CoineProColors.Gold,
) {
    Canvas(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.small)
            .background(CoineProColors.SurfaceElevated)
            .border(1.dp, CoineProColors.BorderSubtle, MaterialTheme.shapes.small)
            .padding(PLATE_INSET),
    ) {
        val finite = points.filter { it.second.isFinite() }
        val width = this.size.width
        val height = this.size.height
        val inset = STROKE.toPx()
        if (finite.isEmpty()) return@Canvas
        if (finite.size == 1) {
            drawLine(
                color = tint,
                start = Offset(inset, height / 2f),
                end = Offset(width - inset, height / 2f),
                strokeWidth = inset,
                cap = StrokeCap.Round,
            )
            return@Canvas
        }
        val times = finite.map { it.first }
        val prices = finite.map { it.second }
        val tSpan = (times.max() - times.min()).toDouble()
        val pSpan = prices.max() - prices.min()
        val usableWidth = width - inset * 2f
        val usableHeight = height - inset * 2f
        val at = { point: Pair<Long, Double> ->
            val x = if (tSpan == 0.0) 0.5f else ((point.first - times.min()) / tSpan).toFloat()
            // Price up: the canvas's y grows downwards and a chart's does not, so this is the one
            // place the two conventions are reconciled. A sketch drawn upside down would be worse
            // than no sketch — a reader would take it for a different line.
            val y = if (pSpan == 0.0) 0.5f else (1.0 - (point.second - prices.min()) / pSpan).toFloat()
            Offset(inset + x * usableWidth, inset + y * usableHeight)
        }
        finite.zipWithNext { from, to ->
            drawLine(
                color = tint,
                start = at(from),
                end = at(to),
                strokeWidth = inset,
                cap = StrokeCap.Round,
            )
        }
    }
}

private val PLATE_INSET = 6.dp
private val STROKE = 2.dp
