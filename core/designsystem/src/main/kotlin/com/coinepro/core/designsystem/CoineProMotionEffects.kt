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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
/**
 * How long a price tint stays up.
 *
 * Six hundred and twenty was long enough to still be lit when the next tick arrived, so a fast
 * market read as a row that was permanently green rather than a row that had just moved — the
 * signal that says «this one changed» stops saying anything once it is always on. The reference
 * holds its own for a little over a third of a second, which is long enough to be caught by
 * peripheral vision and short enough to have cleared before the next print.
 */
private const val FLASH_MS = 380
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
 * A list that has been asked for and has not arrived.
 *
 * The third dead surface, and the one this module had no answer for at all: a screen waiting on a
 * feed showed a spinner, or nothing, and both say the same unhelpful thing — *something is
 * happening somewhere*. A reader looking at a blank market list cannot tell it from a market list
 * with no markets in it.
 *
 * Rows shaped like the rows that are coming say three things instead: the wait is for a list, the
 * list will be about this long, and the layout will not jump when it lands. The stagger is the
 * point of the shimmer being per-row rather than one band across the whole block — a list assembles
 * itself down the page, which is the direction the reader is already looking.
 *
 * @param count how many placeholders. Match roughly what the screen usually holds; more rows than
 *   the data will fill is a layout that shrinks when it succeeds.
 * @param leading whether each row starts with a round token — true for anything with an instrument
 *   logo, false for a list of plain text rows.
 */
@Composable
fun CoineProSkeletonRows(
    modifier: Modifier = Modifier,
    count: Int = 6,
    leading: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        repeat(count) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .coineProEnter(delayMillis = index * ENTER_STAGGER_MS)
                    .padding(vertical = CoineProSpacing.Half),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading) {
                    CoineProSkeleton(
                        modifier = Modifier.size(30.dp),
                        height = 30.dp,
                        shape = CircleShape,
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Two unequal bars, not one. A title and its subtitle are never the same
                    // length, and a placeholder built from identical blocks reads as a loading
                    // graphic rather than as the row it is standing in for.
                    CoineProSkeleton(modifier = Modifier.fillMaxWidth(0.42f), height = 12.dp)
                    CoineProSkeleton(modifier = Modifier.fillMaxWidth(0.26f), height = 10.dp)
                }
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CoineProSkeleton(modifier = Modifier.width(64.dp), height = 13.dp)
                    CoineProSkeleton(modifier = Modifier.width(44.dp), height = 10.dp)
                }
            }
        }
    }
}

/** How far apart two rows deal themselves out. Short enough that the list still lands at once. */
private const val ENTER_STAGGER_MS = 40

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
        // Nine per cent, down from fourteen. A tint this sits *behind* a whole row of type, and at
        // fourteen the row's own figures lost contrast against it every time the price moved — the
        // flash was competing with the number it was drawing attention to.
        targetValue = if (direction == 0) 0f else FLASH_ALPHA,
        animationSpec = tween(if (direction == 0) FLASH_MS else 90),
        label = "flash",
    )
    // Movement, not execution. A price ticking up is not a buy order — see
    // [CoineProColors.MarketUp].
    val tint = if (direction >= 0) CoineProColors.MarketUp else CoineProColors.MarketDown
    return this.background(tint.copy(alpha = alpha))
}

/** The tint's peak, as a fraction. Felt at the edge of vision; never read as a fill. */
private const val FLASH_ALPHA = 0.09f

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

/**
 * Arrive, rather than appear.
 *
 * A fade from nothing plus a short rise, once, when the composable is first laid out. It is the
 * cheapest thing that separates an interface which was *built* from one which was printed: a screen
 * whose content is simply present at frame one has no beginning, and a reader reads it as a page.
 * Eight points and 240ms is short enough that nobody times it and long enough that the eye follows
 * the content up into place.
 *
 * It is finite and runs exactly once per composition, so it is outside what the reduced-motion gate
 * governs — but it still asks [continuousMotionAllowed], because a person who turned animations off
 * asked not to watch things move into place either. With motion off the content is simply there,
 * which is the correct behaviour and not a degraded one.
 *
 * [delayMillis] staggers a list: pass the index times forty or so, and the rows deal themselves out
 * instead of snapping in as a block. Keep the stagger short — a fifth item that waits half a second
 * is not elegance, it is latency the reader can see.
 */
@Composable
fun Modifier.coineProEnter(delayMillis: Int = 0): Modifier {
    val animate = continuousMotionAllowed()
    var arrived by remember { mutableStateOf(!animate) }
    LaunchedEffect(animate) {
        if (!animate) {
            arrived = true
            return@LaunchedEffect
        }
        if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis.toLong())
        arrived = true
    }
    val progress by animateFloatAsState(
        targetValue = if (arrived) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (animate) CoineProMotionSpecs.SLOW_MS else 0,
            easing = CoineProMotionSpecs.Enter,
        ),
        label = "enter",
    )
    val rise = with(LocalDensity.current) { ENTER_RISE.toPx() }
    return this
        .graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * rise
        }
}

/** How far content travels on the way in. Small, because the fade is doing most of the work. */
private val ENTER_RISE = 8.dp

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

