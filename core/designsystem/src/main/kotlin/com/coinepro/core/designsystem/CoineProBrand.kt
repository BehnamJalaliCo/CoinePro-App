package com.coinepro.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
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
 * The CoinePro name, set in the brand's two-tone treatment.
 *
 * Typeset rather than shipped as a raster. The supplied wordmark is a JPEG on a white ground whose
 * "Coine" glyphs are themselves near-white, so no cut-out can separate them cleanly — every
 * attempt leaves the product's most prominent asset visibly ragged. Live text renders crisp at any
 * density, mirrors correctly, and stays selectable by accessibility services. Replace this with the
 * real vector wordmark if one is ever supplied.
 */
@Composable
fun CoineProWordmark(
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

/** Mark above the name, the standard lockup for full-screen surfaces such as sign-in. */
@Composable
fun CoineProLockup(
    modifier: Modifier = Modifier,
    markSize: Dp = 96.dp,
    style: TextStyle = LocalTextStyle.current,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        CoineProMark(size = markSize)
        CoineProWordmark(style = style)
    }
}
