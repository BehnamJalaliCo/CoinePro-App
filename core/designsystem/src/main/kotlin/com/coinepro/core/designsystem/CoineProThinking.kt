package com.coinepro.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Motion that reports work genuinely still in flight.
 *
 * Everything here loops only while [continuousMotionAllowed] is true. With animations turned off
 * each one holds a legible static frame rather than freezing mid-cycle or vanishing — the state
 * still has to be readable, it just stops moving.
 */
private const val PULSE_PERIOD_MS = 1_400
private const val SWEEP_PERIOD_MS = 1_800

/** Three dots that rise in sequence while a request is outstanding. */
@Composable
fun CoineProThinkingDots(
    modifier: Modifier = Modifier,
    dotSize: Dp = 6.dp,
) {
    val animate = continuousMotionAllowed()
    val transition = rememberInfiniteTransition(label = "thinking")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(PULSE_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSize / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            // Held at mid-brightness when motion is off, so the row still reads as "working".
            val alpha = if (!animate) {
                0.55f
            } else {
                val offset = index * (1f / 3f)
                0.35f + 0.65f * ((sin((phase - offset) * 2f * Math.PI.toFloat()) + 1f) / 2f)
            }
            // The screen's accent rather than a fixed gold: the dots appear on the AI screen,
            // whose accent is the analysis blue, so a gold indicator there was a second accent on
            // a page that had already spent its one.
            val ink = CoineProColors.pageAccentInk
            Canvas(Modifier.size(dotSize)) {
                drawCircle(color = ink.copy(alpha = alpha))
            }
        }
    }
}

/**
 * A hairline that sweeps while the model works, as a progress bar of unknown duration.
 *
 * Deliberately not a determinate bar: the server reports queued or running and never a percentage,
 * so showing one would be inventing progress that nobody measured.
 */
@Composable
fun CoineProStreamingBar(
    modifier: Modifier = Modifier,
    thickness: Dp = 2.dp,
) {
    val animate = continuousMotionAllowed()
    val transition = rememberInfiniteTransition(label = "stream")
    val head by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SWEEP_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "head",
    )

    // Read before the Canvas: the draw lambda is a DrawScope, not a composable, so a theme colour
    // has to be resolved out here. The band follows the page accent for the same reason the dots
    // do — a bar that reports work on an analysis screen is that screen's work, not the brand's.
    val trackColour = CoineProColors.Border
    val band = CoineProColors.pageAccentInk

    Canvas(modifier.height(thickness)) {
        val width = size.width
        if (width <= 0f) return@Canvas
        drawRect(color = trackColour)
        val bandWidth = width * 0.36f
        // A static three-quarter band when motion is off: still clearly a busy state, just still.
        val start = if (animate) head * (width + bandWidth) - bandWidth else width * 0.32f
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(band.copy(alpha = 0f), band, band.copy(alpha = 0f)),
                startX = start,
                endX = start + bandWidth,
            ),
            topLeft = Offset(start.coerceAtLeast(0f), 0f),
            size = size.copy(
                width = (start + bandWidth).coerceAtMost(width) - start.coerceAtLeast(0f),
            ),
        )
    }
}
