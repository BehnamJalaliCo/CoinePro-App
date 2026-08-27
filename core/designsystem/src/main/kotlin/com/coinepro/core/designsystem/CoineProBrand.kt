package com.coinepro.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The CoinePro wordmark, as supplied.
 *
 * The artwork's bevelled metal cannot be reproduced by typesetting, so this is the real asset
 * rather than styled text. It is cropped straight out of the owner's transparent master — see
 * `core/designsystem/brand/` — which replaced an earlier cut made by clearing black out of a
 * black-ground JPEG. That cut worked, but every edge it produced was an approximation of an alpha
 * channel that now simply exists.
 *
 * The silver half of the name is **not** the master's own. In the supplied artwork "Coine" sits at
 * luminance 232 against a "Pro" that is genuinely metallic, which reads as white text beside gold
 * rather than as two halves of one name in the same material. `scripts/design/build-brand-lockup.py`
 * remaps it onto the mark's own silver distribution — a histogram match against the C, so "make it
 * the silver of the C" is performed by measurement rather than picked by eye. The gold and every
 * edge in the file are untouched.
 *
 * It is still given a dark plate on a light background. The match deepened the shadows but left the
 * top faces near white, because that is what the mark's own chrome does; on a pale surface those
 * faces still disappear and the name still reads as half of itself. The plate keeps the artwork
 * exactly as drawn and changes only what is behind it.
 */
@Composable
fun CoineProWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val onLightSurface = !LocalCoineProPalette.current.isDark
    Image(
        painter = painterResource(R.drawable.coinepro_wordmark),
        contentDescription = contentDescription,
        modifier = modifier
            .then(
                if (onLightSurface) {
                    Modifier
                        .background(WORDMARK_PLATE, RoundedCornerShape(CoineProSpacing.One))
                        // Enough that the plate reads as a deliberate ground rather than as a
                        // rectangle the artwork failed to fill.
                        .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One)
                } else {
                    Modifier
                },
            ),
    )
}

/**
 * The plate behind the wordmark in light theme.
 *
 * Deliberately not [CoineProColors.Stage] inverted or any other palette entry: this is the ground
 * the artwork was cut against, and matching it is what keeps the bevel reading as metal rather than
 * as a sticker.
 */
private val WORDMARK_PLATE = Color(0xFF0B0D12)

/**
 * The name as live text, for places a raster cannot go — a notification title, a share subject, or
 * any surface that must stay selectable. Prefer [CoineProWordmark] wherever an image will do.
 */
@Composable
fun CoineProWordmarkText(
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = CoineProColors.Silver)) { append("Coine") }
            withStyle(SpanStyle(color = CoineProColors.Gold)) { append("Pro") }
        },
        // The name is a Latin proper noun and must not reorder inside a right-to-left screen.
        style = style.copy(fontWeight = FontWeight.Bold, textDirection = TextDirection.Ltr),
        modifier = modifier,
    )
}

/** The interlocking C/P mark. */
@Composable
fun CoineProMark(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    Image(
        painter = painterResource(R.drawable.coinepro_mark),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

/** Mark above the wordmark, the standard lockup for full-screen surfaces such as sign-in. */
@Composable
fun CoineProLockup(
    modifier: Modifier = Modifier,
    markSize: Dp = 96.dp,
    wordmarkWidth: Dp = 168.dp,
    contentDescription: String? = null,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
    ) {
        CoineProMark(size = markSize)
        // The lockup as a whole names the product, so the description sits on the wordmark and the
        // mark above it stays decorative rather than announcing the name twice.
        CoineProWordmark(
            modifier = Modifier.width(wordmarkWidth),
            contentDescription = contentDescription,
        )
    }
}
