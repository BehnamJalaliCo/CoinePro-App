package com.coinepro.core.designsystem

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent

/**
 * Where a logo the vendored artwork does not cover may come from.
 *
 * The app draws its own marks for every listed symbol (`SymbolArtwork`) and a symbol without one
 * does not reach a list — that is the owner's rule and this does not loosen it. What a provider
 * adds is a second source for the places a *searched* or *deep-linked* symbol can appear before
 * anyone has drawn it: the symbol header, a search result, the wheel. Null means "no picture for
 * this one"; the caller then draws the monogram it always drew.
 */
fun interface LogoProvider {
    /** The image for [symbol], or null when this provider has nothing to offer. */
    fun url(symbol: String): String?
}

/** No remote source. The default, and what every screenshot renders with. */
object NoLogos : LogoProvider {
    override fun url(symbol: String): String? = null
}

val LocalLogoProvider = staticCompositionLocalOf<LogoProvider> { NoLogos }

/**
 * A logo from the network, with the monogram under it the whole time.
 *
 * The monogram is not a placeholder that gets swapped for a spinner: it *is* the logo until the
 * picture arrives, and stays the logo if it never does. A broken-image glyph or a blank disc is
 * the one thing a list of markets must never show.
 */
@Composable
internal fun RemoteLogo(
    url: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp,
) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null,
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentScale = ContentScale.Crop,
        loading = { CoineProAssetToken(label = label, tint = tint, size = size) },
        error = { CoineProAssetToken(label = label, tint = tint, size = size) },
        success = {
            SubcomposeAsyncImageContent(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(1.dp, CoineProColors.assetRing, CircleShape),
            )
        },
    )
}
