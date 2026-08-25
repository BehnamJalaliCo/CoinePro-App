package com.coinepro.core.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Motion that carries information.
 *
 * Every loop in this file is gated on [continuousMotionAllowed] and, more importantly, on something
 * genuinely being in flight. The distinction the product cares about is not "is this pretty" but
 * "does this movement mean something": a shimmering row means a request is outstanding, a flashing
 * price means that price just changed, a revealing sentence means tokens are still arriving. Motion
 * that means nothing is worse than none, because a reader learns to ignore it and then misses the
 * motion that did mean something.
 *
 * With animations turned off, each effect holds a legible static frame rather than vanishing.
 */

private const val SHIMMER_PERIOD_MS = 1_150
private const val FLASH_MS = 620
private const val REVEAL_CHARS_PER_SECOND = 45f

/**
 * A skeleton block for content that has been requested and has not arrived.
 *
 * Deliberately shaped like the thing it stands in for — pass the width and height of the real row —
 * so the layout does not jump when the data lands. A spinner in the middle of an empty screen tells
 * a reader nothing about what is coming.
 */
@Composable
fun CoineProSkeleton(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    shape: Shape = MaterialTheme.shapes.extraSmall,
) {
    Box(
        modifier
            .height(height)
            .background(CoineProColors.SurfaceElevated, shape)
            .clip(shape)
            .coineProShimmer(shape),
    )
}

/**
 * Sweeps a highlight across a placeholder.
 *
 * Held as a static low highlight when motion is off — the block still reads as "waiting" rather
 * than as an empty grey rectangle someone forgot to fill.
 */
@Composable
fun Modifier.coineProShimmer(shape: Shape = MaterialTheme.shapes.extraSmall): Modifier {
    val animate = continuousMotionAllowed()
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(SHIMMER_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "progress",
    )
    val head = if (animate) progress else 0.5f
    val highlight = CoineProColors.TextMuted.copy(alpha = 0.18f)

    return this.drawWithContent {
        drawContent()
        val band = size.width * 0.45f
        val start = head * (size.width + band) - band
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                startX = start,
                endX = start + band,
            ),
        )
    }
}

/**
 * Text that appears as it arrives.
 *
 * [streaming] must reflect whether the server is still sending. When it is false the whole string
 * renders at once, because animating a sentence that arrived complete two seconds ago is a
 * performance of work that already finished — and on a market claim, a reader who watches it type
 * believes it is being reasoned out live.
 */
@Composable
fun CoineProStreamingText(
    text: String,
    streaming: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = CoineProColors.TextPrimary,
) {
    val animate = continuousMotionAllowed()
    var revealed by remember(text) { mutableStateOf(if (streaming && animate) 0 else text.length) }

    LaunchedEffect(text, streaming, animate) {
        if (!streaming || !animate) {
            revealed = text.length
            return@LaunchedEffect
        }
        val frame = (1000f / REVEAL_CHARS_PER_SECOND).toLong().coerceAtLeast(8L)
        while (revealed < text.length) {
            kotlinx.coroutines.delay(frame)
            revealed = (revealed + 1).coerceAtMost(text.length)
        }
    }

    Text(
        // Substring of the real text, never a placeholder: what is on screen is always something
        // the server actually said.
        text = text.take(revealed),
        modifier = modifier,
        style = style,
        color = color,
    )
}

/**
 * A figure that animates to its new value instead of jumping.
 *
 * [format] receives the interpolated value, so the caller keeps control of precision, currency and
 * bidi isolation. The interpolation is display-only — the value it lands on is exactly [target].
 */
@Composable
fun CoineProAnimatedFigure(
    target: Double,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = CoineProTextStyles.RowFigure,
    color: Color = CoineProColors.TextPrimary,
) {
    val animate = continuousMotionAllowed()
    val value by animateFloatAsState(
        targetValue = target.toFloat(),
        animationSpec = tween(durationMillis = if (animate) 520 else 0),
        label = "figure",
    )
    Text(
        text = format(if (animate) value.toDouble() else target),
        modifier = modifier,
        style = style,
        color = color,
    )
}

/**
 * Tints a row for a moment when its price moves, green up and red down.
 *
 * This is the one piece of motion in the product a trader reads rather than tolerates: it says
 * *which* rows are moving in a list where every number looks alike. It fires on a change in
 * [value] and decays; it never loops.
 */
@Composable
fun Modifier.coineProPriceFlash(value: Double?): Modifier {
    val animate = continuousMotionAllowed()
    var previous by remember { mutableStateOf(value) }
    var direction by remember { mutableStateOf(0) }

    LaunchedEffect(value) {
        val old = previous
        previous = value
        if (old == null || value == null || old == value || !animate) return@LaunchedEffect
        direction = if (value > old) 1 else -1
        kotlinx.coroutines.delay(FLASH_MS.toLong())
        direction = 0
    }

    val alpha by animateFloatAsState(
        targetValue = if (direction == 0) 0f else 0.14f,
        animationSpec = tween(if (direction == 0) FLASH_MS else 90),
        label = "flash",
    )
    val tint = if (direction >= 0) CoineProColors.Buy else CoineProColors.Sell
    return this.background(tint.copy(alpha = alpha))
}

/** A determinate bar for work whose progress the server actually reports. */
@Composable
fun CoineProProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    thickness: Dp = 4.dp,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(320),
        label = "progress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(thickness)
            .background(CoineProColors.SurfaceElevated, MaterialTheme.shapes.extraSmall),
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(thickness)
                .background(
                    Brush.horizontalGradient(
                        listOf(CoineProColors.GoldDeep, CoineProColors.GoldBright),
                    ),
                    MaterialTheme.shapes.extraSmall,
                ),
        )
    }
}

/** A step counter that animates between whole numbers — quota used, jobs queued. */
@Composable
fun rememberAnimatedCount(target: Int): Int {
    val animate = continuousMotionAllowed()
    val value by animateIntAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (animate) 420 else 0),
        label = "count",
    )
    return if (animate) value else target
}

