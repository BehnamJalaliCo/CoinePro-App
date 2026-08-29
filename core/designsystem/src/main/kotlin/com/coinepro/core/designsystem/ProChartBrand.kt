package com.coinepro.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
 * The Pro-Chart mark and wordmark.
 *
 * ### One asset per shape, and the colour comes from the theme
 *
 * The artwork the owner supplied is white ink on a black ground. Both are cut by
 * `scripts/design/build-prochart-brand.py` into flat white with the luminance as the alpha channel,
 * which is what makes a single file work everywhere: the shape lives in the alpha, so a tint paints
 * it any colour without a second export, an outline variant or a plate behind it.
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
 * One colour and one weight, because the name is one word in the artwork too. The hyphen is part of
 * it: the product is «Pro-Chart», and "Pro Chart" is two words that a line break may separate.
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
const val PRO_CHART = "Pro-Chart"

/*
 * There is deliberately no `ProChartMark` composable.
 *
 * The mark on its own had one, and after the lockup stopped stacking it above a wordmark that
 * already contains it, nothing in the app drew it — a public component with no call site, which is
 * the shape of thing this codebase has spent a whole wave deleting. `prochart_mark.png` stays in
 * `res/`, because the launcher's three layers, the Play icon and the README banner are all cut from
 * it by `scripts/design/build-prochart-brand.py`; if a screen ever wants the mark alone, this is
 * four lines coming back.
 */

/**
 * The brand at the head of a full-screen surface such as sign-in.
 *
 * The wordmark **and nothing above it**. The supplied artwork already carries the mark to the left
 * of the name, so the mark-over-wordmark stack this used to draw put the same drawing on screen
 * twice — which is how a logo stops reading as a logo and starts reading as a mistake. One asset,
 * one lockup, at a size that fills the space a stack used to.
 *
 * [markSize] is gone with it rather than kept as a parameter nothing reads.
 */
@Composable
fun ProChartLockup(
    modifier: Modifier = Modifier,
    wordmarkWidth: Dp = 240.dp,
    contentDescription: String? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
    ) {
        ProChartWordmark(
            modifier = Modifier.width(wordmarkWidth),
            contentDescription = contentDescription,
        )
    }
}
