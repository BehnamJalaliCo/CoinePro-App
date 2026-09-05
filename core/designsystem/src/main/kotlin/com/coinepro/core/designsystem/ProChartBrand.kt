package com.coinepro.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Pro Chart mark and wordmark.
 *
 * ### One asset per shape, and the colour comes from the theme
 *
 * The artwork the owner supplied is ink on a black ground — the mark in the brand gold, the name in
 * white. Both are cut by `scripts/design/build-prochart-brand.py` into flat white with the coverage
 * as the alpha channel, which is what makes a single file work everywhere: the shape lives in the
 * alpha, so a tint paints it any colour without a second export, an outline variant or a plate
 * behind it. The launcher is the one place the gold is baked in, because a home-screen icon has no
 * theme to follow and there the brand colour *is* the design.
 *
 * ### The wordmark is written, so it follows the reader's language
 *
 * `drawable-<density>` carries the Persian «پروچارت» and `drawable-en-<density>` the Latin
 * lockup — the same convention `values/` and `values-en/` already use, applied to the one image
 * that is also a piece of writing. A single Latin wordmark on a Persian screen was the old
 * behaviour and it was wrong in a way that is easy to miss from outside the audience: the product's
 * name in this market is «پروچارت», written, and a reader who has never seen the Latin form does
 * not recognise it as the name of the app they just opened.
 *
 * That is the whole difference from the brand this replaces. The old wordmark was a bevelled metal
 * raster whose highlights were near white, so on a pale surface half the name disappeared and it
 * had to be given a dark rectangle to sit on — a plate that read as a sticker on every light
 * screen. This mark is one colour by construction, so light theme paints it near-black and dark
 * theme paints it white, and neither needs a ground.
 *
 * ### The default ink
 *
 * [CoineProColors.TextPrimary], not the accent. The mark is the product's name and behaves like the
 * heading it sits above; painting it gold would make it the loudest thing on a sign-in screen whose
 * one job is a form. A caller that genuinely wants it in another colour passes [tint].
 *
 * ### The aspect ratio is forced, and that is a bug fix rather than a refinement
 *
 * Every call site in the app asks for this at a **width** — `Modifier.width(160.dp)` on the rail,
 * `Modifier.width(220.dp)` on sign-in — and until this comment was written none of them got it.
 * `Image` with a fixed width and an unbounded height does not stretch its painter to the width it
 * was given: `ContentScale.Fit` computes `min(dstWidth / srcWidth, dstHeight / srcHeight)`, the
 * unbounded height resolves to the painter's own intrinsic height, so the second term is 1 and the
 * scale is capped at 1. The layout node is the width the caller asked for; the artwork inside it is
 * drawn at its intrinsic size, centred, with the remainder as transparent margin.
 *
 * Concretely, on the sign-in screen at xxhdpi: the caller asked for 220dp, the xxhdpi cut is
 * 504×141px = 168×47dp, and the name was drawn 168dp wide inside a 220dp box — 24% smaller than
 * asked for, with 26dp of empty box down each side. That is the whole of what the owner circled.
 * The name looked small beside the mark because it *was* small beside the mark, and the gap between
 * them looked like 39dp because the 12dp the lockup asks for had 26dp of empty box added to it.
 *
 * `aspectRatio` fixes both by giving the painter a box with **two** fixed dimensions, at which point
 * `Fit` has a bounded height to scale into and fills it exactly. The ratio is read off the painter
 * rather than typed here, which matters twice: the Persian cut is 3.574:1 and the Latin one is
 * 3.93:1, so a constant would be wrong in one language or the other, and a revised master changes
 * the ratio in the artwork rather than in a number somebody has to remember to update.
 */
@Composable
fun ProChartWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = CoineProColors.TextPrimary,
) {
    val painter = painterResource(R.drawable.prochart_wordmark)
    Image(
        painter = painter,
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        contentScale = ContentScale.Fit,
        modifier = modifier.aspectRatio(painter.artworkAspect()),
    )
}

/**
 * The painter's own width-to-height ratio, or 1 if it has none.
 *
 * A vector or a bitmap always reports one; the fallback is for the painters that do not — a colour
 * or a shape — where a square box is at least a box and not a crash. See [ProChartWordmark] for why
 * this is read rather than written down.
 */
private fun Painter.artworkAspect(): Float {
    val size = intrinsicSize
    return if (size.isSpecified && size.width > 0f && size.height > 0f) {
        size.width / size.height
    } else {
        1f
    }
}

/**
 * The name as live text, for places a raster cannot go — a notification title, a share subject, or
 * any surface that must stay selectable. Prefer [ProChartWordmark] wherever an image will do.
 *
 * One colour and one weight. The capital H is the owner's, settled, and it is deliberate rather
 * than a typo to be helpfully corrected: the artwork reads «Pro Chart» and so does every string in
 * the app. The Persian name is «پرو چارت» and is written out, never transliterated from this one.
 *
 * `textDirection = Ltr` on the style below matters more than it looks: the name is a Latin proper
 * noun on a right-to-left screen, and without the isolation the two words swap around it.
 */
@Composable
fun ProChartWordmarkText(
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = CoineProColors.TextPrimary,
) {
    Text(
        text = PRO_CHART,
        color = color,
        // The name is a Latin proper noun and must not reorder inside a right-to-left screen.
        style = style.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Ltr),
        modifier = modifier,
    )
}

/** The brand name in Latin, in one place so a rename is one edit. */
const val PRO_CHART = "Pro Chart"

/**
 * The brand name in Persian, which is what almost every reader of this app calls it.
 *
 * One word, as the owner's artwork sets it — «پروچارت», not «پرو چارت». It is a proper noun and it
 * is written the way the logo is written; a space here and none in the logo is the kind of
 * inconsistency that makes a product look like two products.
 */
const val PRO_CHART_FA = "پروچارت"

/**
 * The mark on its own, in the brand gold.
 *
 * This was deleted once, correctly, when nothing drew it — a public component with no call site.
 * It is back because [ProChartLockup] needs it: the owner's lockup is a **two-colour** drawing, the
 * mark in gold beside the name in white, and a single tinted image cannot be two colours. Composing
 * the two here instead of shipping a flat raster is also what keeps the light theme working, since
 * the name follows [CoineProColors.TextPrimary] while the mark stays gold on both grounds.
 *
 * Gold by default and not [CoineProColors.TextPrimary], which is the opposite of [ProChartWordmark]
 * and deliberate: the name is type and behaves like the heading it sits beside, the mark is a logo
 * and has a colour of its own.
 */
@Composable
fun ProChartMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = CoineProColors.Gold,
) {
    Image(
        painter = painterResource(R.drawable.prochart_mark),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier,
    )
}

/**
 * The brand at the head of a full-screen surface such as sign-in: the mark beside the name.
 *
 * ### Why a row of two assets rather than one picture of the lockup
 *
 * The owner's lockup is two colours — a gold mark and a white name — and one tinted PNG cannot be
 * two colours. Shipping it flat would work on the dark theme and break on the light one, which is
 * exactly the fault the brand this replaces had: a raster whose highlights were near white, given a
 * dark plate to sit on, reading as a sticker on every pale screen.
 *
 * Composed, the name follows the theme and the mark keeps its own colour, on both grounds.
 *
 * ### The order is the reader's, not the file's
 *
 * A `Row` lays its first child at the **start** edge, which is the right in Persian and the left in
 * English. So the mark leads in both directions without this file naming a side — the same reason
 * nothing in `CoineProListDetail` says left or right either.
 *
 * ### Every number below is measured off `design/brand/prochart-lockup-fa-master.png`
 *
 * The owner drew the lockup once, as a single picture, and that picture is the specification. It
 * was not being followed. Measured on the master — the gold ink's bounding box against the white
 * ink's, both to the pixel:
 *
 * ```
 *   the name : 654 × 191, centre at y = 492
 *   the mark : 264 × 275, centre at y = 464
 *   between  : 85
 * ```
 *
 * Three ratios come out of that, and all three are stated against the **name's height** rather than
 * its width. Height is the shared quantity: the master's setting of the name is 3.42:1 and the cut
 * this app ships is 3.57:1, so a ratio anchored to width would drift by five percent between the
 * artwork and the app while one anchored to height puts the mark at the same size relative to the
 * letterforms in both.
 *
 *  * [MARK_TO_WORDMARK] — 275 / 191. The mark stands **44% taller than the name**, which is what
 *    makes it read as a logo beside a word rather than as a bullet in front of one.
 *  * [GAP_TO_WORDMARK] — 85 / 191.
 *  * [MARK_LIFT] — 28 / 191. This is the one the owner's eye caught.
 *
 * ### The lift, which is the alignment the owner circled
 *
 * `Alignment.CenterVertically` aligns two **boxes**. The mark's box is its ink: the cut fills its
 * canvas from the first row to the last. The name's box is not: below the baseline sit the tails of
 * «ر» and «و» and, below those, the three dots of «پ» and «چ» — measured on the xxxhdpi cut, the
 * baseline is at row 117 of 188, so **38% of the name's box is descender**. Centre the two boxes and
 * the descenders drag the name's letterforms upward relative to the mark, or equivalently the mark
 * hangs below the word. That is precisely what a reader sees and cannot name: «لوگو با نوشتار در یک
 * راستا نیستند».
 *
 * The owner's own drawing does not centre them. It sits the mark 28 units — 15% of the name's height
 * — above the name's box centre, which lands the mark's bottom edge just past the dots and its top
 * edge well clear of the tallest letter. That is optical alignment, done by hand, in the artwork.
 * This composable reproduces it as a number instead of by eye.
 *
 * It is applied as **top padding on the name** rather than as an offset on the mark, and the choice
 * is deliberate: an offset would push the mark's ink outside the row's own bounds, where a parent
 * that clips would cut the top off the logo. Padding keeps every pixel inside the layout. It also
 * costs nothing in height — the padded name is 1.29 × its own height and the mark is 1.44 ×, so the
 * mark still sets the row and the lockup is exactly as tall as it was.
 *
 * ### English draws one asset, not two
 *
 * `drawable-en-*` is not the Latin *name*; it is the whole Latin lockup, mark included, because that
 * is the master the owner supplied for English. Composing a gold mark in front of it drew the mark
 * twice. `prochart_wordmark_is_lockup` says which of the two the current locale has, and where the
 * asset is already a lockup this draws only the asset — one colour in English, which is what the
 * artwork is, rather than two marks in two colours which is what it was.
 */
@Composable
fun ProChartLockup(
    modifier: Modifier = Modifier,
    wordmarkWidth: Dp = 200.dp,
    contentDescription: String? = null,
    /**
     * The mark's colour.
     *
     * Gold by default, which is the brand. The owner asked for the lockup **monochrome** in the two
     * places it sits on a working surface rather than on a brand one — over the chart and above the
     * sign-in form — where a gold mark beside a near-black name reads as two objects rather than
     * one logo. Passing [CoineProColors.TextPrimary] there makes the whole lockup one ink, black on
     * a light theme and white on a dark one, and leaves the brand gold everywhere it is the subject.
     */
    markTint: Color = CoineProColors.Gold,
    /** The name's colour. Follows [markTint] where a caller asks for a monochrome lockup. */
    wordmarkTint: Color = CoineProColors.TextPrimary,
) {
    if (booleanResource(R.bool.prochart_wordmark_is_lockup)) {
        ProChartWordmark(
            modifier = modifier.width(wordmarkWidth),
            contentDescription = contentDescription,
            // The Latin cut is the whole lockup in one colour, so the mark's ink is the asset's.
            tint = markTint,
        )
        return
    }

    // The composed lockup is 1.52 times as wide as the name inside it, and a caller asking for a
    // name wider than about two thirds of the column would push the mark off the edge — silently,
    // because a `Row` overflows rather than complains. So the width a caller asks for is a maximum
    // rather than an instruction: the lockup keeps its proportions and gives up size instead of
    // giving up the mark. `BoxWithConstraints` is the cost of that, once, on a sign-in screen.
    BoxWithConstraints(modifier = modifier) {
        val width = minOf(wordmarkWidth, maxWidth / LOCKUP_TO_WORDMARK)

        // Everything is a multiple of the name's drawn height, which the width and the artwork's
        // own aspect fix between them. `PROCHART_WORDMARK_ASPECT` is the Persian cut; this branch
        // only runs in the locales whose wordmark is the name alone, and that is all but English.
        val wordmarkHeight = width / PROCHART_WORDMARK_ASPECT
        val markHeight = wordmarkHeight * MARK_TO_WORDMARK
        val lift = wordmarkHeight * MARK_LIFT

        Row(
            verticalAlignment = Alignment.CenterVertically,
            // Ink to ink, less the mark's own transparent margin — see [MARK_CANVAS_SIDE_MARGIN].
            horizontalArrangement = Arrangement.spacedBy(
                wordmarkHeight * (GAP_TO_WORDMARK - MARK_CANVAS_SIDE_MARGIN * MARK_TO_WORDMARK),
            ),
        ) {
            ProChartMark(
                modifier = Modifier.size(markHeight),
                contentDescription = contentDescription,
                tint = markTint,
            )
            ProChartWordmark(
                tint = wordmarkTint,
                // Twice the lift, so that centring the padded box leaves the ink one lift *below*
                // the row's centre — which is the same as putting the mark one lift above the ink,
                // without anything leaving the row. See the note above.
                modifier = Modifier.padding(top = lift * 2).width(width),
                // Named once. Two descriptions on one lockup is two announcements of one thing.
                contentDescription = null,
            )
        }
    }
}

/**
 * The Persian wordmark's width-to-height ratio, 3.574:1.
 *
 * Written down here and read off the painter in [ProChartWordmark], which is not a contradiction:
 * the composable needs the ratio of whichever cut the locale resolved, and [ProChartLockup] needs it
 * *before* it lays anything out, to turn the width a caller asked for into the height the rest of
 * the lockup is measured in. Identical at every density — 168×47, 336×94, 504×141, 672×188 — because
 * `build-prochart-brand.py` scales one master.
 */
private const val PROCHART_WORDMARK_ASPECT = 672f / 188f

/** The mark's height as a multiple of the wordmark's, measured off the owner's lockup: 275 / 191. */
private const val MARK_TO_WORDMARK = 1.44f

/** Ink to ink between the two, as a multiple of the wordmark's height: 85 / 191. */
private const val GAP_TO_WORDMARK = 0.445f

/** How far the mark rides above the wordmark's box centre, as a multiple of its height: 28 / 191. */
private const val MARK_LIFT = 0.147f

/**
 * The transparent margin down each side of the mark's own canvas, as a fraction of that canvas.
 *
 * The cut is square and the drawing is not quite — 375 of 384 columns carry ink at xxxhdpi — so
 * `Modifier.size` gives the ink its full height and leaves about one percent of the box empty on
 * either side. Subtracted from the gap so that [GAP_TO_WORDMARK] means what it says: the distance
 * from the last pixel of the name to the first pixel of the mark, not from box edge to box edge.
 */
private const val MARK_CANVAS_SIDE_MARGIN = 0.0117f

/**
 * How wide the whole lockup is, as a multiple of the name inside it.
 *
 * `1 + (gap + mark) / aspect`, with the gap already net of the mark's own canvas margin — which is
 * to say it is the four constants above, multiplied out once, so the clamp cannot drift away from
 * the layout it is clamping. At 1.52 a 168dp name is a 256dp lockup.
 */
private const val LOCKUP_TO_WORDMARK =
    1f + (GAP_TO_WORDMARK - MARK_CANVAS_SIDE_MARGIN * MARK_TO_WORDMARK + MARK_TO_WORDMARK) /
        PROCHART_WORDMARK_ASPECT
