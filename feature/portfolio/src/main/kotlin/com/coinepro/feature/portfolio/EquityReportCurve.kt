package com.coinepro.feature.portfolio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.portfolio.EquityPoint

/**
 * The running high of a series, point by point.
 *
 * Pulled out of the drawing code because it is the only part of the curve that is arithmetic rather
 * than geometry, and because the shaded region depends on it being right: the drawdown is the gap
 * between this line and the equity line, so an off-by-one here draws a picture of a loss that did
 * not happen. Pure, and tested.
 */
internal fun runningPeaks(values: List<Double>): List<Double> {
    if (values.isEmpty()) return emptyList()
    var peak = values.first()
    return values.map { value ->
        if (value > peak) peak = value
        peak
    }
}

/**
 * Account equity over time, with the drawdown shaded under the running peak.
 *
 * ### Why the shading exists at all
 *
 * A line that ends higher than it started tells a reader they made money and hides how. The eleven
 * trades in the middle where the account gave back a third of itself are on the chart — they are
 * the dip — but a dip read against nothing looks small, because the eye compares it to the height
 * of the whole picture rather than to the high it fell from. Shading the region between the running
 * peak and the equity turns "a dip" into "this much was given back", which is the same information
 * the eye was already being shown and could not see.
 *
 * The deepest fall is then marked **explicitly**, with its own band and a vertical rule at the
 * trough, because even the shading does not answer the question a trader actually has: not "did it
 * fall" but "how far, and over which stretch". A curve alone never answers that, and the number
 * quoted from a report without the span is the number that gets misread as a single bad trade. It
 * is not one — see `PortfolioMetrics.deepestDrawdown`.
 *
 * ### Drawing
 *
 * Canvas, in the idiom of the app's own chart: flat fills, hairlines at one point, no gradient, no
 * blur, no coloured shadow. Points are spaced by sequence rather than by clock time, for the same
 * reason the summary curve is — forty trades in an hour and none for three days is ordinary, and a
 * time axis would leave a chart that is mostly empty gap.
 */
@Composable
internal fun EquityReportCurve(
    points: List<EquityPoint>,
    /**
     * The fall to mark. Null draws the shading without the marker, which is the right picture for
     * a curve that never fell — a marker over nothing would invent one.
     */
    drawdown: DrawdownSpan?,
    /**
     * True when [points] is real account balance rather than cumulative profit from zero.
     *
     * It decides whether zero belongs on the chart. On a profit-from-zero curve it is break-even
     * and the one line worth drawing; on a balance curve it is far below everything and drawing it
     * would flatten the whole series into the top pixel.
     */
    isBalance: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 200.dp,
) {
    if (points.size < 2) return

    val equity = remember(points) { points.map { it.equity } }
    val peaks = remember(equity) { runningPeaks(equity) }
    val rise = CoineProColors.Buy
    val fall = CoineProColors.Sell
    val muted = CoineProColors.TextMuted
    val line = if (equity.last() >= equity.first()) rise else fall

    Canvas(modifier.fillMaxWidth().height(height)) {
        val hairline = 1.dp.toPx()
        val padding = size.height * 0.08f
        val plot = size.height - padding * 2
        var low = equity.min()
        var high = peaks.max()
        if (!isBalance) {
            low = minOf(low, 0.0)
            high = maxOf(high, 0.0)
        }
        val range = (high - low).takeIf { it > 0.0 } ?: return@Canvas
        fun y(value: Double) = padding + ((high - value) / range).toFloat() * plot
        fun x(index: Int) = index.toFloat() / (equity.size - 1) * size.width

        if (!isBalance) {
            drawLine(
                color = muted.copy(alpha = 0.5f),
                start = Offset(0f, y(0.0)),
                end = Offset(size.width, y(0.0)),
                strokeWidth = hairline,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }

        // The band behind the deepest fall goes down first, so the curve and the peak line stay on
        // top of it. A marker painted over the data would be a marker obscuring the thing it marks.
        if (drawdown != null && drawdown.troughIndex > drawdown.peakIndex) {
            val left = x(drawdown.peakIndex)
            val right = x(drawdown.troughIndex)
            drawRect(
                color = fall.copy(alpha = 0.10f),
                topLeft = Offset(left, 0f),
                size = Size(right - left, size.height),
            )
        }

        // The given-back region: out along the running peak, back along the equity. A flat fill at
        // low alpha rather than a wash, because the design rules keep gradients to the brand mark
        // and the chart engine's own area series, and because a flat region has one edge the eye
        // can follow instead of a soft one it has to guess at.
        val region = Path()
        region.moveTo(x(0), y(peaks.first()))
        for (index in 1 until peaks.size) region.lineTo(x(index), y(peaks[index]))
        for (index in equity.indices.reversed()) region.lineTo(x(index), y(equity[index]))
        region.close()
        drawPath(path = region, color = fall.copy(alpha = 0.16f))

        // The running peak, as a hairline. Dashed so it never reads as a second series.
        val peakPath = Path()
        peaks.forEachIndexed { index, value ->
            if (index == 0) peakPath.moveTo(x(index), y(value)) else peakPath.lineTo(x(index), y(value))
        }
        drawPath(
            path = peakPath,
            color = muted.copy(alpha = 0.7f),
            style = Stroke(width = hairline, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))),
        )

        val curve = Path()
        equity.forEachIndexed { index, value ->
            if (index == 0) curve.moveTo(x(index), y(value)) else curve.lineTo(x(index), y(value))
        }
        drawPath(path = curve, color = line, style = Stroke(width = 1.6.dp.toPx()))

        if (drawdown != null && drawdown.troughIndex > drawdown.peakIndex) {
            val troughX = x(drawdown.troughIndex)
            val top = y(drawdown.peakEquity)
            val bottom = y(drawdown.troughEquity)
            // One solid vertical rule from the high to the low, with a tick at each end: the exact
            // distance the account fell, drawn as a distance rather than implied by two dots.
            drawLine(fall, Offset(troughX, top), Offset(troughX, bottom), hairline * 1.5f)
            val tick = 5.dp.toPx()
            drawLine(fall, Offset(troughX - tick, top), Offset(troughX + tick, top), hairline)
            drawLine(fall, Offset(troughX - tick, bottom), Offset(troughX + tick, bottom), hairline)
            drawLine(
                color = fall.copy(alpha = 0.6f),
                start = Offset(x(drawdown.peakIndex), top),
                end = Offset(troughX, top),
                strokeWidth = hairline,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 4f)),
            )
        }
    }
}
