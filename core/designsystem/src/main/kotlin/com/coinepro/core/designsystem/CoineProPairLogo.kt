package com.coinepro.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coinepro.core.symbols.SymbolArtwork
import java.util.Locale

/**
 * What a forex pair or a metal looks like in a list beside the coins.
 *
 * A crypto market is one asset and gets one disc. A forex market is a *ratio* of two, and a single
 * disc cannot say which two — EURUSD and GBPUSD would be the same picture. So a pair is drawn the
 * way every terminal draws it: the base currency's flag in front, the quote's behind and smaller,
 * with a notch between them so the back disc reads as a separate object rather than as a shadow.
 *
 * The artwork is the country and metal sets under `design/asset-logos/tv-logos`, converted like
 * every other logo here. They were already in the repository and are proper flags, rather than the
 * four-shape approximations that would fit inside a Kotlin file.
 */
@Composable
fun CoineProPairLogo(
    /** The wire symbol, e.g. `EURUSD` or `XAUUSD`. */
    symbol: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    val pair = pairOf(symbol)
    if (pair == null) {
        CoineProAssetLogo(symbol = symbol, modifier = modifier, size = size)
        return
    }
    val (base, quote) = pair
    // The proportions every terminal converged on: the quote is half the base, and the two overlap
    // by about a third. Bigger and the pair reads as two markets; smaller and the quote is a dot.
    val frontSize = size * 0.74f
    val backSize = size * 0.50f

    // Absolute rather than start/end, so the composition is identical in Persian and English. It
    // encodes "base in front of quote", not a reading order, and a mirrored version of it would
    // silently swap which currency a reader takes to be the base.
    Box(modifier = modifier.size(size)) {
        Box(
            modifier = Modifier
                .align(AbsoluteAlignment.BottomRight)
                .size(backSize)
                .clip(CircleShape)
                .border(1.dp, CoineProColors.assetRing, CircleShape),
        ) {
            CurrencyDisc(currency = quote, size = backSize)
        }
        Box(
            modifier = Modifier
                .align(AbsoluteAlignment.TopLeft)
                .size(frontSize)
                .clip(CircleShape)
                // Two rings, doing different jobs. The outer is the notch that separates the front
                // disc from the one behind it, in the colour of the card they sit on so it reads as
                // a gap rather than as a dark scar. The inner is the same hairline every coin gets,
                // so a pair and a coin look like members of one set.
                .border(2.dp, CoineProColors.Surface, CircleShape)
                .border(1.dp, CoineProColors.assetRing, CircleShape),
        ) {
            CurrencyDisc(currency = base, size = frontSize)
        }
    }
}

/** One currency or metal as a filled disc, with the lettered token for anything unrecognised. */
@Composable
private fun CurrencyDisc(currency: String, size: Dp) {
    val art = artworkFor(currency)
    if (art == null) {
        CoineProAssetToken(
            label = currency.take(2),
            tint = CoineProColors.assetTint(currency),
            size = size,
        )
        return
    }
    Image(
        painter = painterResource(art),
        contentDescription = null,
        modifier = Modifier.size(size).clip(CircleShape),
        // Cropped for the same reason the coins are: a flag is drawn on a rectangle, and fitting
        // one inside a circle would leave it floating in a disc of empty space.
        contentScale = ContentScale.Crop,
    )
}

/**
 * Splits a forex or metal symbol into base and quote, or null when it is neither.
 *
 * Length is most of the test: a forex symbol is exactly two three-letter codes and a crypto one is
 * not, so `XAUUSD` passes while `BTCUSDT` is seven characters and is left to the coin table. Length
 * alone is not enough, though — plenty of six-letter coin tickers exist — so at least one half must
 * also be a currency this app has artwork for.
 */
internal fun pairOf(symbol: String): Pair<String, String>? {
    val clean = symbol.uppercase(Locale.US).filter { it.isLetter() }
    if (clean.length != 6) return null
    val base = clean.substring(0, 3)
    val quote = clean.substring(3, 6)
    if (artworkFor(base) == null && artworkFor(quote) == null) return null
    return base to quote
}

/** Whether a symbol names a forex or metal market rather than a coin. */
internal fun isPairSymbol(symbol: String): Boolean = pairOf(symbol) != null

/**
 * One index as its country's flag.
 *
 * A single disc rather than the pair composition, because an index is one thing. Null where the
 * app has no flag for it, which is how `HK50` stays out of a list rather than appearing as a
 * grey square — see `SymbolArtwork.INDEX_COUNTRY`.
 */
@Composable
internal fun CoineProIndexLogo(symbol: String, modifier: Modifier = Modifier, size: Dp = 42.dp) {
    val country = SymbolArtwork.INDEX_COUNTRY[symbol.uppercase(Locale.US)]
    val art = country?.let { artworkFor(it) }
    if (art == null) {
        // The token directly, *not* back through `CoineProAssetLogo`. That call is what routed
        // here in the first place, so returning to it is an infinite recursion — which is exactly
        // what shipped when the country codes were missing from `ARTWORK`: every index render
        // died with a StackOverflowError instead of drawing a fallback.
        CoineProAssetToken(
            label = symbol.take(2).uppercase(Locale.US),
            tint = CoineProColors.assetTint(symbol),
            modifier = modifier,
            size = size,
        )
        return
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, CoineProColors.assetRing, CircleShape),
    ) {
        Image(
            painter = painterResource(art),
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

@DrawableRes
/** Internal rather than private so the test can hold the two tables against each other. */
internal fun artworkFor(currency: String): Int? = ARTWORK[currency]

/**
 * The twenty currencies the MT5 feed quotes, and the four precious metals.
 *
 * An explicit table rather than a currency-to-country rule, because the exceptions are the ones
 * that matter: the euro is not a country, and the renminbi trades as CNY onshore and CNH offshore
 * while being one flag.
 */
private val ARTWORK: Map<String, Int> = mapOf(
    "USD" to R.drawable.asset_flag_us,
    "EUR" to R.drawable.asset_flag_eu,
    "GBP" to R.drawable.asset_flag_gb,
    "JPY" to R.drawable.asset_flag_jp,
    "CHF" to R.drawable.asset_flag_ch,
    "CAD" to R.drawable.asset_flag_ca,
    "AUD" to R.drawable.asset_flag_au,
    "NZD" to R.drawable.asset_flag_nz,
    "TRY" to R.drawable.asset_flag_tr,
    "SEK" to R.drawable.asset_flag_se,
    "NOK" to R.drawable.asset_flag_no,
    "DKK" to R.drawable.asset_flag_dk,
    "ZAR" to R.drawable.asset_flag_za,
    "MXN" to R.drawable.asset_flag_mx,
    "SGD" to R.drawable.asset_flag_sg,
    "PLN" to R.drawable.asset_flag_pl,
    "CZK" to R.drawable.asset_flag_cz,
    "HUF" to R.drawable.asset_flag_hu,
    "CNH" to R.drawable.asset_flag_cn,
    "CNY" to R.drawable.asset_flag_cn,
    // The country codes, for the indices. This table is keyed by *currency* everywhere above —
    // USD, GBP, JPY — and an index has no currency, it has a country: DAX is «DE», not «EUR».
    // Without these seven rows `artworkFor("US")` is null, which is how the index logo first
    // shipped as an infinite recursion between itself and the coin logo rather than as a flag.
    //
    // Germany and France are authored rather than vendored, because TradingView's set has neither;
    // see design/asset-logos/authored.
    "US" to R.drawable.asset_flag_us,
    "GB" to R.drawable.asset_flag_gb,
    "JP" to R.drawable.asset_flag_jp,
    "AU" to R.drawable.asset_flag_au,
    "EU" to R.drawable.asset_flag_eu,
    "DE" to R.drawable.asset_flag_de,
    "FR" to R.drawable.asset_flag_fr,
    // The exotics an MT5 broker quotes beyond the majors. Added with their flags rather than ahead
    // of them: a currency in this map with no drawable is a crash, and one with a drawable and no
    // entry here is a lettered token where a flag was available.
    "RUB" to R.drawable.asset_flag_ru,
    "INR" to R.drawable.asset_flag_in,
    "BRL" to R.drawable.asset_flag_br,
    "KRW" to R.drawable.asset_flag_kr,
    "THB" to R.drawable.asset_flag_th,
    "ILS" to R.drawable.asset_flag_il,
    "SAR" to R.drawable.asset_flag_sa,
    "AED" to R.drawable.asset_flag_ae,
    // HKD and TWD are absent on purpose. TradingView publishes a plain grey square for both rather
    // than a flag, and a grey disc on a symbol row reads as a broken image — the lettered token is
    // the honest answer. HKD had been showing that square since the first twenty flags shipped.

    "XAU" to R.drawable.asset_metal_gold,
    "XAG" to R.drawable.asset_metal_silver,
    "XPT" to R.drawable.asset_metal_platinum,
    "XPD" to R.drawable.asset_metal_palladium,
)
