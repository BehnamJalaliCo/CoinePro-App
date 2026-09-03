package com.coinepro.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.painterResource
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
 * The signature at the foot of a chart: the mark alone, and the name unfurling out of it.
 *
 * ### What was wrong with the swap it replaces
 *
 * «نحوهٔ استریم نوشتهٔ پروچارت خیلی بده و لوگو پروچارت هم دارای بهم‌ریختگی هست موقع بسته بودن.» Both
 * halves of that were one fault. The two states were **two different composables** — a bare
 * [ProChartMark] closed, a whole [ProChartLockup] open — and Compose swapped one subtree for the
 * other in a single frame. So the name did not arrive, it appeared; and the mark was re-created on
 * every tap, at a size the lockup derived from the *name's* width rather than the size it had been
 * drawn at a frame earlier. Two marks a fraction of a point apart, alternating, is exactly what
 * «به‌هم‌ریختگی» describes: the logo twitched every time it was touched.
 *
 * ### One mark, and a name that grows out of it
 *
 * The mark here is drawn **once**, at [markSize], and never rebuilt. It is the fixed point. What
 * animates is everything to the reading side of it:
 *
 *  * the name's **box** opens from nothing to its full width on a spring, which is what makes the
 *    mark stay put while the name beside it grows — a tween would arrive and stop dead, and a
 *    signature that stops dead reads as a layout jump rather than as a thing unfolding;
 *  * the name is drawn at its **own proportions inside that box and clipped**, so the letters are
 *    uncovered by an edge travelling away from the logo rather than squeezed into a narrow frame;
 *  * it **rises** a third of its own height into place as it comes out, so the name arrives from
 *    behind the mark instead of sliding along beside it;
 *  * the mark itself **settles** — a few percent of scale, spent over the same spring — so the
 *    whole signature reads as one object reacting to the tap rather than as a logo with a drawer.
 *
 * Closing is the same spring run backwards, which is why it is one animation value and not two.
 *
 * ### English is one asset, and it unfurls even more simply
 *
 * `prochart_wordmark_is_lockup` says the Latin cut is the whole lockup — mark included — so there
 * is nothing to compose there and nothing to draw twice. That branch clips **the lockup itself**,
 * from the mark's width out to the whole thing: closed it is the mark, because the mark is the
 * first thing in the artwork, and open it is the lockup. One picture, one clip, and not a frame in
 * which two marks exist.
 *
 * ### Reduced motion
 *
 * `continuousMotionAllowed()` reads the platform's animator scale. Off, the target is snapped to
 * and both states are simply drawn — which is the finished frame in each direction, not a degraded
 * one. Screenshot renders take that path too, so a capture is stable.
 */
@Composable
fun ProChartSignature(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    markSize: Dp = MARK_SIZE,
    tint: Color = CoineProColors.TextPrimary,
    contentDescription: String? = null,
) {
    val moving = continuousMotionAllowed()
    val target = if (expanded) 1f else 0f
    val open = remember { Animatable(target) }
    LaunchedEffect(target, moving) {
        if (moving) {
            open.animateTo(target, spring(dampingRatio = UNFURL_DAMPING, stiffness = UNFURL_STIFFNESS))
        } else {
            open.snapTo(target)
        }
    }
    val unfurled = open.value.coerceIn(0f, 1f)

    if (booleanResource(R.bool.prochart_wordmark_is_lockup)) {
        LatinSignature(unfurled, markSize, tint, contentDescription, modifier)
        return
    }

    // Everything is measured off the mark, so a caller sets one number. The name's height is the
    // mark's, less the lockup's own 1.44:1 — see `ProChartLockup` for where that comes from — and
    // its width follows from the Persian cut's aspect. Ink to ink, the gap is the lockup's too.
    val nameHeight = markSize / SIGNATURE_MARK_TO_NAME
    val nameWidth = nameHeight * SIGNATURE_NAME_ASPECT
    val gap = nameHeight * SIGNATURE_GAP

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProChartMark(
            tint = tint,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(markSize)
                .graphicsLayer {
                    val settle = 1f + MARK_SETTLE * unfurled
                    scaleX = settle
                    scaleY = settle
                },
        )
        // Zero-width and zero-gap when closed, so a collapsed signature is exactly the mark's own
        // box and nothing beside it — the layout does not reserve room for a name that is not
        // there, and the tap target does not quietly extend across the corner of the plot.
        Spacer(Modifier.width(gap * unfurled))
        Box(
            modifier = Modifier
                .height(nameHeight)
                .width(nameWidth * unfurled)
                .clipToBounds(),
            // The start edge, which is the side the mark is on in both directions: a `Row` lays its
            // first child at the start, so pinning the name there uncovers it *away from the logo*
            // — leftwards in English, rightwards in Persian — without this file naming a side.
            contentAlignment = Alignment.CenterStart,
        ) {
            Image(
                painter = painterResource(R.drawable.prochart_wordmark),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                // `FillHeight` and a required size: the name is drawn at its own proportions and
                // the *box* is what narrows, so the letters are uncovered rather than squeezed. A
                // `Fit` inside a shrinking box would scale the name down as it opened, which is two
                // motions at once and reads as a rendering fault.
                contentScale = ContentScale.FillHeight,
                modifier = Modifier
                    .requiredSize(width = nameWidth, height = nameHeight)
                    .graphicsLayer {
                        // A hair of rise, spent by the time the box is a third open. The name comes
                        // *out* of the mark rather than sliding along beside it.
                        translationY = size.height * NAME_RISE * (1f - unfurled)
                        alpha = (unfurled * INK_LEAD).coerceAtMost(1f)
                    },
            )
        }
    }
}

/**
 * The English branch: the Latin lockup, clipped from its mark out to the whole name.
 *
 * The artwork already reads mark-then-name left to right, so the width of the box is the whole of
 * the animation — at [markSize] the visible part *is* the mark, and at the lockup's full width it
 * is the lockup. The rise and the settle are the Persian branch's, applied to the one asset.
 *
 * `AbsoluteAlignment.CenterLeft` rather than `CenterStart`, and deliberately: the Latin cut is
 * drawn left to right whatever direction the page reads, so the mark is at its **left** edge even
 * on a Persian page that has been switched to English. Pinning to the start edge would uncover it
 * from the wrong end and show the reader the tail of the word first.
 */
@Composable
private fun LatinSignature(
    unfurled: Float,
    markSize: Dp,
    tint: Color,
    contentDescription: String?,
    modifier: Modifier,
) {
    val height = markSize
    val full = height * LATIN_LOCKUP_ASPECT
    Box(
        modifier = modifier
            .height(height)
            .width(markSize + (full - markSize) * unfurled)
            .clipToBounds(),
        contentAlignment = AbsoluteAlignment.CenterLeft,
    ) {
        Image(
            painter = painterResource(R.drawable.prochart_wordmark),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint),
            contentScale = ContentScale.FillHeight,
            modifier = Modifier
                .requiredSize(width = full, height = height)
                .graphicsLayer {
                    val settle = 1f + MARK_SETTLE * unfurled
                    scaleX = settle
                    scaleY = settle
                },
        )
    }
}

/** The Latin lockup's width-to-height ratio, 3.93:1 — the cut in `drawable-en-*`. */
private const val LATIN_LOCKUP_ASPECT = 3.93f

/** Where the name's box lands relative to the mark: the lockup's own 275 / 191. */
private const val SIGNATURE_MARK_TO_NAME = 1.44f

/** The Persian wordmark's width-to-height ratio, the same 672 / 188 the lockup measures. */
private const val SIGNATURE_NAME_ASPECT = 672f / 188f

/** Ink to ink between mark and name, as a multiple of the name's height: the lockup's 85 / 191. */
private const val SIGNATURE_GAP = 0.445f

/** How much the mark grows as the name comes out. Four percent: felt, not seen. */
private const val MARK_SETTLE = 0.04f

/** How far the name starts below its resting line, as a fraction of its own height. */
private const val NAME_RISE = 0.35f

/** How far ahead of the box the ink comes up, so the name is solid before the box has finished. */
private const val INK_LEAD = 2.2f

/** Enough bounce to read as sprung, not enough to overshoot into the price gutter. */
private const val UNFURL_DAMPING = 0.72f
private const val UNFURL_STIFFNESS = 380f

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
