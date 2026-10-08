package com.coinepro.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.toArgb
import com.coinepro.core.symbols.SymbolArtwork
import java.util.Locale

/**
 * What [CoineProAssetLogo] would draw for a symbol, as data rather than as a composable (5.27.0).
 *
 * The home-screen widgets and the notifications are drawn outside Compose — a `RemoteViews` tree, a
 * notification's large icon — and they should carry the same mark a row in the app carries. The
 * rules stay here, beside the composable that uses them, so the two cannot drift.
 */
sealed interface AssetArt {
    /** One disc: a coin, an equity, an index's country. */
    data class Single(@DrawableRes val res: Int) : AssetArt

    /** A forex or metal pair: the base in front, the quote behind, either one possibly lettered. */
    data class Pair(
        @DrawableRes val base: Int?,
        @DrawableRes val quote: Int?,
        val baseLabel: String,
        val quoteLabel: String,
        val baseTint: Int,
        val quoteTint: Int,
    ) : AssetArt

    /** No mark: the ticker's letters on a disc of its own hue. */
    data class Monogram(val label: String, val tint: Int) : AssetArt
}

object AssetArtwork {

    /** A currency's or a metal's own disc — `USD`'s flag, `XAU`'s bar — or null. */
    @DrawableRes
    fun currency(code: String): Int? = artworkFor(code.uppercase(Locale.US))

    fun of(symbol: String): AssetArt {
        val upper = symbol.uppercase(Locale.US)
        pairOf(upper)?.let { (base, quote) ->
            return AssetArt.Pair(
                base = artworkFor(base),
                quote = artworkFor(quote),
                baseLabel = base.take(2),
                quoteLabel = quote.take(2),
                baseTint = CoineProColors.assetBrand(base).toArgb(),
                quoteTint = CoineProColors.assetBrand(quote).toArgb(),
            )
        }
        SymbolArtwork.INDEX_COUNTRY[upper]?.let { country ->
            artworkFor(country)?.let { return AssetArt.Single(it) }
            return AssetArt.Monogram(upper.take(2), CoineProColors.assetBrand(upper).toArgb())
        }
        logoFor(upper)?.let { return AssetArt.Single(it) }
        return AssetArt.Monogram(initialFor(upper), CoineProColors.assetBrand(upper).toArgb())
    }
}
