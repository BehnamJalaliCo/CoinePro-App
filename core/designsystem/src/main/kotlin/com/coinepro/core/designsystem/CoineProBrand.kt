package com.coinepro.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * rather than styled text. It is cut from a black-ground master by clearing every genuinely black
 * region — including the enclosed counters of o, e and P, which a border-only fill cannot reach.
 */
@Composable
fun CoineProWordmark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(R.drawable.coinepro_wordmark),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}

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
