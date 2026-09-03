package com.coinepro.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The mark, drawing itself in — the brand's one piece of motion, reused rather than reinvented.
 *
 * ### Where it comes from
 *
 * TradingView's phone app streams its mark in at the head of the watchlist every time that page is
 * opened and every time the list is flicked back to the top. The owner asked for it by name: «اون
 * بالا لوگو تریدینگ ویو یه استریم زیبا با هر بار اسکرول به بالا و پایین یا مراجعه به اون صفحه
 * دارد». The launch sheet already draws the same wipe, so the *motion* lives here and both call
 * sites read it — one animation the product has, in one place, rather than two that drift.
 *
 * ### What it does
 *
 * A clip front crosses the shape from the leading edge while it eases up from [SCALE_FROM] to full
 * size. A wipe rather than a fade or a glow: the house rules allow neither a blur nor a coloured
 * shadow, and a moving front is what a stroke being drawn actually looks like.
 *
 * ### It replays on a key, and that key is the caller's business
 *
 * [replay] is any value that changes when the mark should draw itself again — a screen's own
 * "arrived" counter, a list's "is at the top" boolean, a tab key. It is deliberately not a
 * scroll *position*: an animation restarted on every pixel of a drag is a flicker, not a
 * signature.
 *
 * ### Reduced motion
 *
 * `continuousMotionAllowed()` reads the platform's animator scale, which is what "Remove
 * animations" and battery saver both drive. Off, the mark is simply there — which is the finished
 * frame, not a degraded one — and screenshot renders take that same path, so a capture is stable.
 */
@Composable
fun ProChartMarkStream(
    replay: Any?,
    modifier: Modifier = Modifier,
    size: Dp = MARK_SIZE,
    tint: Color = CoineProColors.TextPrimary,
    contentDescription: String? = null,
) {
    val moving = continuousMotionAllowed()
    val progress = remember { Animatable(if (moving) 0f else 1f) }
    LaunchedEffect(replay, moving) {
        if (!moving) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(STREAM_MS, easing = LinearEasing))
    }
    val drawn = progress.value
    ProChartMark(
        tint = tint,
        contentDescription = contentDescription,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val scale = SCALE_FROM + (1f - SCALE_FROM) * drawn
                scaleX = scale
                scaleY = scale
            }
            .brandWipe(drawn, fromLeft = true),
    )
}

/**
 * Reveal content from one edge to the other, by [fraction] of its width.
 *
 * A clip rather than an alpha: the ink arrives as a front moving across the shape, which is what a
 * stroke being drawn looks like, and it needs no blur to look continuous. Shared so the launch
 * sheet and the watchlist's mark cannot drift apart.
 */
fun Modifier.brandWipe(fraction: Float, fromLeft: Boolean): Modifier = drawWithContent {
    val shown = size.width * fraction.coerceIn(0f, 1f)
    if (fromLeft) {
        clipRect(right = shown) { this@drawWithContent.drawContent() }
    } else {
        clipRect(left = size.width - shown) { this@drawWithContent.drawContent() }
    }
}

/** Half a second: long enough to read as drawn, short enough that nobody waits for a header. */
private const val STREAM_MS = 520

/** Where the mark starts, as a fraction of its size. */
private const val SCALE_FROM = 0.86f

/** The mark at the head of a page: 28 dp, the height of a title's cap line beside it. */
private val MARK_SIZE = 28.dp
