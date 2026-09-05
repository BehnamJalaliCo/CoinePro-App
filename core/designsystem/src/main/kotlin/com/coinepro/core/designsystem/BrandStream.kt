package com.coinepro.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * **The tape.** The brand written by a price, which is this product's own piece of motion.
 *
 * ### What it is for
 *
 * «لوگو پرو چارت … باید در صفحه‌ی دیده‌بان باشه و با یه انیمیشن و استریم خاص و اختراعی باشه که مخصوص
 * آپ من باشه، نه اینکه یه چیز الکی.» What stood at the head of the watchlist was
 * `ProChartMarkStream` — a clip front crossing the mark in half a second. It was honest and it was
 * *the reference's*: TradingView streams its mark in exactly that way, and a wipe is what every
 * app in this category does with its logo. Borrowed motion is not a signature.
 *
 * ### What it does instead
 *
 * A single line — the shape of a market, not a decoration — is drawn across the header from the
 * reading edge. It is a real polyline with a head on it, and it is the head that does the work:
 * **the brand is uncovered by the price passing over it**. The mark appears first because the mark
 * is at the reading edge, then each letter of the name as the line reaches it.
 *
 * Behind the head the tape burns off. Every segment fades on a fixed trail, so what the reader
 * sees is a line being *written* rather than a line being drawn and then removed — and by the end
 * there is no line at all, only the brand it wrote. That is the whole idea: this app's logo is
 * something a price leaves behind.
 *
 * Three details are what stop it reading as a gimmick:
 *
 *  * the path is **fixed**, not random. It is the same eleven readings every time, so the header
 *    has a shape a reader comes to recognise rather than a different squiggle on every visit;
 *  * the head **leads the ink** slightly, so a letter is uncovered a hair before the line reaches
 *    it and the two never appear to collide;
 *  * the tape is **gold** and the brand is ink, so the line is unmistakably the brand's own colour
 *    doing the writing, and nothing else on the page competes with it.
 *
 * No blur, no glow and no gradient: a stroke, a clip and an alpha ramp, which is all the house
 * rules allow and all this needs.
 *
 * ### It replays on a key, and reduced motion is the finished frame
 *
 * [replay] is any value that changes when the header should sign itself again — a screen's own
 * "arrived" counter, a list's "is at the top" boolean. It is deliberately a counter and not a
 * scroll *position*: an animation restarted on every pixel of a drag is a flicker, not a signature.
 *
 * With animations off — `continuousMotionAllowed()` reads the platform's animator scale — the brand
 * is simply there and no tape is drawn at all, which is the finished frame rather than a degraded
 * one. Screenshot renders take that same path, so a capture is stable.
 */
@Composable
fun ProChartTapeStream(
    replay: Any?,
    modifier: Modifier = Modifier,
    markSize: Dp = TAPE_MARK,
    tint: Color = CoineProColors.TextPrimary,
    /** The line's own colour. The brand's gold, which is the point of it. */
    tape: Color = CoineProColors.Gold,
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
        progress.animateTo(1f, tween(TAPE_MS, easing = LinearEasing))
    }
    val front = progress.value
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val lockupOnly = booleanResource(R.bool.prochart_wordmark_is_lockup)
    // The Latin cut is drawn left to right whatever the page does, so it is uncovered from its own
    // left; the Persian pair leads with the mark, which a `Row` puts at the reading edge.
    val fromLeft = lockupOnly || !rtl

    val nameHeight = markSize / SIGNATURE_MARK_TO_NAME
    val nameWidth = nameHeight * SIGNATURE_NAME_ASPECT
    val gap = nameHeight * SIGNATURE_GAP
    val full = if (lockupOnly) markSize * LATIN_LOCKUP_ASPECT else markSize + gap + nameWidth

    Box(
        modifier = modifier.width(full).height(markSize),
        contentAlignment = Alignment.Center,
    ) {
        // The ink, uncovered by the head. `INK_LEAD` on the fraction is the "leads the ink" note
        // above: at any moment the wipe is a little ahead of the line, so a letter is clear before
        // the stroke arrives rather than appearing underneath it.
        val uncovered = (front * TAPE_INK_LEAD).coerceIn(0f, 1f)
        if (lockupOnly) {
            ProChartWordmark(
                tint = tint,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxWidth().brandWipe(uncovered, fromLeft = fromLeft),
            )
        } else {
            Row(
                modifier = Modifier.brandWipe(uncovered, fromLeft = fromLeft),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProChartMark(
                    tint = tint,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(markSize),
                )
                Spacer(Modifier.width(gap))
                Image(
                    painter = painterResource(R.drawable.prochart_wordmark),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(tint),
                    contentScale = ContentScale.FillHeight,
                    modifier = Modifier.requiredSize(width = nameWidth, height = nameHeight),
                )
            }
        }
        if (front < 1f) {
            Canvas(modifier = Modifier.matchParentSize()) {
                drawTape(front = front, colour = tape, mirrored = !fromLeft)
            }
        }
    }
}

/**
 * The line itself: eleven readings, a head, and a trail that burns off behind it.
 *
 * Drawn segment by segment rather than as one path, because each segment carries its own alpha —
 * which is what makes this a thing being written rather than a shape being revealed. A single path
 * with one alpha would be the wipe this replaces.
 *
 * [mirrored] flips the x axis for a right-to-left header, so the line travels *away from* the
 * reading edge in both directions rather than towards it in one of them.
 */
private fun DrawScope.drawTape(front: Float, colour: Color, mirrored: Boolean) {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return
    // The whole tape fades in the last fifth, so the header settles on the brand alone. Without it
    // the final segment would be left sitting under the last letter for good.
    val leaving = 1f - ((front - TAPE_FADE_FROM) / (1f - TAPE_FADE_FROM)).coerceIn(0f, 1f)
    if (leaving <= 0f) return
    val stroke = TAPE_STROKE.toPx()
    // Inside the box by half a stroke at each edge, so a reading at 0 or 1 is not sliced by the
    // bounds — and off the very top and bottom, because the line runs behind the letters and a
    // stroke through the middle of the name would read as a strike-through.
    val top = stroke
    val span = (height - stroke * 2f).coerceAtLeast(1f)
    val headX = front * width
    val trail = width * TAPE_TRAIL

    fun pointAt(index: Int): Offset {
        val t = index.toFloat() / (TAPE_READINGS.size - 1)
        val x = t * width
        return Offset(if (mirrored) width - x else x, top + (1f - TAPE_READINGS[index]) * span)
    }

    for (index in 0 until TAPE_READINGS.size - 1) {
        val startT = index.toFloat() / (TAPE_READINGS.size - 1) * width
        val endT = (index + 1).toFloat() / (TAPE_READINGS.size - 1) * width
        if (startT > headX) break
        val start = pointAt(index)
        val end = pointAt(index + 1)
        // The part of this segment the head has reached. A segment the head is halfway through is
        // drawn halfway, which is what keeps the line's tip moving smoothly rather than in eleven
        // jumps.
        val reached = ((headX - startT) / (endT - startT)).coerceIn(0f, 1f)
        val tip = Offset(
            x = start.x + (end.x - start.x) * reached,
            y = start.y + (end.y - start.y) * reached,
        )
        // How far behind the head this segment's own midpoint is, as a share of the trail.
        val behind = (headX - (startT + endT) / 2f).coerceAtLeast(0f)
        val alpha = (1f - behind / trail).coerceIn(0f, 1f) * leaving
        if (alpha <= 0f) continue
        drawLine(
            color = colour.copy(alpha = alpha),
            start = start,
            end = tip,
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }

    // The head: a single dot at the tip, which is the thing the eye actually follows.
    val last = TAPE_READINGS.size - 1
    val segment = (front * last).coerceIn(0f, last.toFloat())
    val index = segment.toInt().coerceAtMost(last - 1)
    val within = segment - index
    val from = pointAt(index)
    val to = pointAt(index + 1)
    drawCircle(
        color = colour.copy(alpha = leaving),
        radius = stroke,
        center = Offset(
            x = from.x + (to.x - from.x) * within,
            y = from.y + (to.y - from.y) * within,
        ),
    )
}

/**
 * The tape's readings, from 0 at the foot of the box to 1 at its head.
 *
 * Eleven, fixed, and shaped like a session rather than like a decoration: an opening drift, a pull
 * back, a run, one sharp rejection, and a close above where it started. It ends high because the
 * head finishes beside the last letter of the name rather than under it.
 */
private val TAPE_READINGS = floatArrayOf(
    0.34f, 0.46f, 0.38f, 0.30f, 0.52f, 0.61f, 0.49f, 0.72f, 0.58f, 0.68f, 0.80f,
)

/** How much of the width is still visible behind the head, as a share of the whole. */
private const val TAPE_TRAIL = 0.42f

/** Where the whole tape starts fading out, as a fraction of the write. */
private const val TAPE_FADE_FROM = 0.8f

/** How far ahead of the line the ink is uncovered, so a letter is never drawn under the stroke. */
private const val TAPE_INK_LEAD = 1.08f

/** Thin enough to be a price and thick enough to be seen at header size. */
private val TAPE_STROKE = 1.5.dp

/** Nine hundred milliseconds: a signature, not a loading screen. */
private const val TAPE_MS = 900

/** The mark's height at the head of a page, and everything else is measured off it. */
private val TAPE_MARK = 24.dp

/**
 * The signature at the foot of a chart: the mark alone, and the name unfurling out of it.
 *
 * ### What was wrong with the swap it replaces
 *
 * «نحوه‌ی استریم نوشته‌ی پروچارت خیلی بده و لوگو پروچارت هم دارای بهم‌ریختگی هست موقع بسته بودن.» Both
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
 * sheet, the chart's signature and the watchlist's tape cannot drift apart.
 */
fun Modifier.brandWipe(fraction: Float, fromLeft: Boolean): Modifier = drawWithContent {
    val shown = size.width * fraction.coerceIn(0f, 1f)
    if (fromLeft) {
        clipRect(right = shown) { this@drawWithContent.drawContent() }
    } else {
        clipRect(left = size.width - shown) { this@drawWithContent.drawContent() }
    }
}

/** The mark at the head of a page: 28 dp, the height of a title's cap line beside it. */
private val MARK_SIZE = 28.dp
