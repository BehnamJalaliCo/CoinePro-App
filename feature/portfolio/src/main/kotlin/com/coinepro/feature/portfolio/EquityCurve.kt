package com.coinepro.feature.portfolio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.portfolio.EquityPoint
import com.coinepro.core.portfolio.MonthlyPerformance

/**
 * The account over time, as one line.
 *
 * Not a trading chart and not built on `core:chart`: that engine draws bars against a time axis
 * with a viewport a finger can move, and this is a static line whose x is trade order rather than
 * clock time. Trades do not arrive evenly — forty in one hour and none for three days is ordinary
 * — and spacing them by time leaves a curve that is mostly flat gap. Spacing them by sequence puts
 * every trade the same width apart, which is what the shape is actually about.
 *
 * The baseline is drawn only where it means something: on a cumulative-profit curve zero is where
 * the account broke even, and it is the one line worth marking. On a balance curve zero is not on
 * the chart and there is no equivalent, so no baseline is drawn rather than a fake one at the
 * bottom edge.
 */
@Composable
internal fun EquityCurve(
    points: List<EquityPoint>,
    fromZero: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
) {
    if (points.size < 2) return

    val rise = CoineProColors.Buy
    val fall = CoineProColors.Sell
    val baselineColour = CoineProColors.TextMuted
    val up = points.last().equity >= points.first().equity
    val line = if (up) rise else fall

    Canvas(modifier.fillMaxWidth().height(height)) {
        val padding = size.height * 0.08f
        val plot = size.height - padding * 2
        var low = points.minOf { it.equity }
        var high = points.maxOf { it.equity }
        // A cumulative curve is read against zero, so zero has to be on it even when every point
        // is above — otherwise a run of winners draws as a line hugging the bottom of its own
        // range and says nothing about how far above break-even it actually is.
        if (fromZero) {
            low = minOf(low, 0.0)
            high = maxOf(high, 0.0)
        }
        val span = (high - low).takeIf { it > 0.0 } ?: return@Canvas
        fun y(value: Double) = padding + ((high - value) / span).toFloat() * plot
        fun x(index: Int) = index.toFloat() / (points.size - 1) * size.width

        if (fromZero) {
            val zero = y(0.0)
            drawLine(
                color = baselineColour.copy(alpha = 0.5f),
                start = Offset(0f, zero),
                end = Offset(size.width, zero),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            )
        }

        val path = Path()
        val fill = Path()
        points.forEachIndexed { index, point ->
            val px = x(index)
            val py = y(point.equity)
            if (index == 0) {
                path.moveTo(px, py)
                fill.moveTo(px, size.height)
                fill.lineTo(px, py)
            } else {
                path.lineTo(px, py)
                fill.lineTo(px, py)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()

        // A soft wash under the line rather than a solid area: the shape is the message and a
        // filled block competes with it. Vertical only — no colour gradient, which the design
        // rules do not allow outside three allow-listed places.
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(listOf(line.copy(alpha = 0.18f), Color.Transparent)),
        )
        drawPath(path = path, color = line, style = Stroke(width = 2.dp.toPx()))
    }
}

/**
 * One bar per month, above and below a shared zero.
 *
 * Zero sits where it lands rather than in the middle, so a run of profitable months does not draw
 * half a chart of empty space below the axis — and a losing run does not hide its size by being
 * squashed into the bottom half.
 */
@Composable
internal fun MonthlyBars(
    months: List<MonthlyPerformance>,
    /**
     * One label per bar, in the same order.
     *
     * Passed in rather than formatted here: month names are locale work, a `DrawScope` has no
     * access to resources, and three unlabelled bars are three unlabelled bars. Gregorian, because
     * this app deliberately holds no Jalali conversion of its own — `AccountGateway` says why —
     * and both servers timestamp in UTC.
     */
    labels: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
) {
    if (months.isEmpty()) return

    val rise = CoineProColors.Buy
    val fall = CoineProColors.Sell
    val axis = CoineProColors.TextMuted
    val labelStyle = TextStyle(fontSize = 10.sp, color = CoineProColors.TextMuted)
    val measurer = rememberTextMeasurer()

    Canvas(modifier.fillMaxWidth().height(height)) {
        val labelHeight = 14.dp.toPx()
        val padding = size.height * 0.06f
        val plot = size.height - padding * 2 - labelHeight
        val high = maxOf(months.maxOf { it.net }, 0.0)
        val low = minOf(months.minOf { it.net }, 0.0)
        val span = (high - low).takeIf { it > 0.0 } ?: return@Canvas
        fun y(value: Double) = padding + ((high - value) / span).toFloat() * plot

        val zero = y(0.0)
        drawLine(
            color = axis.copy(alpha = 0.4f),
            start = Offset(0f, zero),
            end = Offset(size.width, zero),
            strokeWidth = 1.dp.toPx(),
        )

        val slot = size.width / months.size
        val barWidth = (slot * 0.6f).coerceAtLeast(1f)
        months.forEachIndexed { index, month ->
            // A month with no trades draws nothing at all — not a hairline at zero, which would
            // read as a month that traded and broke even.
            val left = index * slot + (slot - barWidth) / 2f
            if (month.trades > 0) {
                val top = y(month.net)
                drawRect(
                    color = if (month.net >= 0) rise else fall,
                    topLeft = Offset(left, minOf(top, zero)),
                    size = androidx.compose.ui.geometry.Size(barWidth, kotlin.math.abs(top - zero)),
                )
            }
            // The label goes on even for an empty month. That is the whole point of keeping the
            // empty month in the list: the gap has to be legible as a month that traded nothing.
            labels.getOrNull(index)?.let { text ->
                val measured = measurer.measure(text, labelStyle)
                val x = (index * slot + (slot - measured.size.width) / 2f).coerceIn(
                    0f,
                    (size.width - measured.size.width).coerceAtLeast(0f),
                )
                drawText(measured, topLeft = Offset(x, size.height - labelHeight))
            }
        }
    }
}
