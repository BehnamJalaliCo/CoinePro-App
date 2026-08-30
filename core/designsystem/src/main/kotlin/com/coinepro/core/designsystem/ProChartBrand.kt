package com.coinepro.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Pro CHart mark and wordmark.
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
 */
@Composable
fun ProChartWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = CoineProColors.TextPrimary,
) {
    Image(
        painter = painterResource(R.drawable.prochart_wordmark),
        contentDescription = contentDescription,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier,
    )
}

/**
 * The name as live text, for places a raster cannot go — a notification title, a share subject, or
 * any surface that must stay selectable. Prefer [ProChartWordmark] wherever an image will do.
 *
 * One colour and one weight. The capital H is the owner's, settled, and it is deliberate rather
 * than a typo to be helpfully corrected: the artwork reads «Pro CHart» and so does every string in
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
const val PRO_CHART = "Pro CHart"

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
 */
@Composable
fun ProChartLockup(
    modifier: Modifier = Modifier,
    wordmarkWidth: Dp = 200.dp,
    contentDescription: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        // Sized off the wordmark rather than fixed, so the two stay in proportion at whatever width
        // a caller asks for. 0.34 is the ratio in the owner's own artwork, measured.
        ProChartMark(
            modifier = Modifier.size(wordmarkWidth * MARK_TO_WORDMARK),
            contentDescription = contentDescription,
        )
        ProChartWordmark(
            modifier = Modifier.width(wordmarkWidth),
            // Named once. Two content descriptions on one lockup is two announcements of one thing.
            contentDescription = null,
        )
    }
}

/** The mark's height as a fraction of the wordmark's width, measured off the owner's lockup. */
private const val MARK_TO_WORDMARK = 0.34f
