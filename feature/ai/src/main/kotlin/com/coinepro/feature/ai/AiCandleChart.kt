package com.coinepro.feature.ai

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coinepro.core.aisignal.AiCandle
import com.coinepro.core.designsystem.CoineProColors

/**
 * The recent series the model reasoned over, with the setup's levels drawn across it.
 *
 * Deliberately not a trading chart: no pan, no zoom, no indicators of its own. It exists so the
 * entry, stop and targets can be seen against the price action the model actually saw, which is
 * the difference between a number on a card and a claim you can check.
 *
 * The vertical scale spans the candles *and* every level, so a stop below the series or a target
 * above it stays visible instead of being clipped off the edge.
 */
@Composable
internal fun AiCandleChart(
    candles: List<AiCandle>,
    entry: Double?,
    stopLoss: Double?,
    targets: List<Double>,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
) {
    if (candles.isEmpty()) return

    val levels = listOfNotNull(entry, stopLoss) + targets
    val low = (candles.minOf { it.low }.let { c -> levels.minOrNull()?.coerceAtMost(c) ?: c })
    val high = (candles.maxOf { it.high }.let { c -> levels.maxOrNull()?.coerceAtLeast(c) ?: c })
    val span = (high - low).takeIf { it > 0.0 } ?: return

    // Resolved before the Canvas: the draw lambda is a DrawScope, not a composable, so theme
    // colours have to be read out here.
    val riseColour = CoineProColors.Buy
    val fallColour = CoineProColors.Sell
    val entryColour = CoineProColors.GoldBright

    Canvas(modifier.fillMaxWidth().height(height)) {
        // A little headroom so wicks and the outermost level never touch the frame.
        val pad = size.height * 0.06f
        val plotHeight = size.height - pad * 2
        fun y(value: Double): Float = pad + ((high - value) / span).toFloat() * plotHeight

        entry?.let { drawLevel(y(it), entryColour) }
        stopLoss?.let { drawLevel(y(it), fallColour) }
        targets.forEach { drawLevel(y(it), riseColour) }

        val slot = size.width / candles.size
        val bodyWidth = (slot * 0.62f).coerceAtLeast(1f)
        candles.forEachIndexed { index, candle ->
            val centre = slot * (index + 0.5f)
            val rising = candle.close >= candle.open
            val colour = if (rising) riseColour else fallColour

            drawLine(
                color = colour.copy(alpha = 0.7f),
                start = Offset(centre, y(candle.high)),
                end = Offset(centre, y(candle.low)),
                strokeWidth = 1.5f,
            )
            val top = y(maxOf(candle.open, candle.close))
            val bottom = y(minOf(candle.open, candle.close))
            drawRect(
                color = colour,
                topLeft = Offset(centre - bodyWidth / 2, top),
                // A doji has no body; give it a hairline so the candle does not disappear.
                size = androidx.compose.ui.geometry.Size(bodyWidth, (bottom - top).coerceAtLeast(1.5f)),
            )
        }
    }
}

private fun DrawScope.drawLevel(y: Float, colour: androidx.compose.ui.graphics.Color) {
    drawLine(
        color = colour.copy(alpha = 0.55f),
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 1f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
    )
}

private fun DrawScope.drawLine(
    color: androidx.compose.ui.graphics.Color,
    start: Offset,
    end: Offset,
    strokeWidth: Float,
    pathEffect: PathEffect? = null,
) {
    drawLine(
        color = color,
        start = start,
        end = end,
        strokeWidth = strokeWidth,
        cap = Stroke.DefaultCap,
        pathEffect = pathEffect,
    )
}
