package com.coinepro.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * An instrument's own logo, with the lettered token as the fallback.
 *
 * The fallback is not a placeholder to be removed later — it is the permanent answer for every
 * instrument whose logo we do not have the right to ship, and there will always be some. A list
 * that renders a logo for the assets we have and a coloured initial for the rest stays coherent;
 * one that renders a generic grey circle for the rest does not.
 *
 * Logos are matched on the **base** symbol, so `BTCUSDT` and a future `BTCUSD` resolve to the same
 * artwork rather than needing an entry each.
 *
 * To add one: drop a square vector or WebP at `core/designsystem/src/main/res/drawable/asset_<base>`
 * and add the single line to [logos]. Prefer a vector — it is one file for every density — and
 * prefer artwork that reads on both a near-black and a white ground, since both themes ship.
 */
@Composable
fun CoineProAssetLogo(
    symbol: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    val logo = logoFor(symbol)
    if (logo != null) {
        Image(
            painter = painterResource(logo),
            // Decorative: the row already names the instrument in text, so announcing the logo
            // separately would read the same thing twice.
            contentDescription = null,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    } else {
        CoineProAssetToken(
            label = initialFor(symbol),
            tint = CoineProColors.assetTint(symbol),
            modifier = modifier,
            size = size,
        )
    }
}

/**
 * The letter shown when no logo exists, taken from the wire symbol rather than any display name.
 *
 * A translated name yields an Arabic-script letter in Persian that no exchange shows and that
 * renders as a bare stroke at token size. Both metals start with X in their wire symbols, so a
 * first letter would label gold and silver identically; their element symbols are what every
 * terminal shows anyway.
 */
fun initialFor(symbol: String): String = when (val base = baseOf(symbol)) {
    "XAU" -> "Au"
    "XAG" -> "Ag"
    else -> base.take(1).uppercase(Locale.US)
}

@DrawableRes
private fun logoFor(symbol: String): Int? = logos[baseOf(symbol)]

private fun baseOf(symbol: String): String = symbol
    .uppercase(Locale.US)
    .removeSuffix("USDT")
    .removeSuffix("USDC")
    .removeSuffix("USD")

/**
 * Symbol base to artwork.
 *
 * Only the markets the app quotes are here. The full archive lives in `design/asset-logos`, and
 * `scripts/design/svg-to-vector.py` converts one on demand — so adding a market is a command
 * rather than a design task, and six hundred unused vectors stay out of the APK.
 *
 * ADA is deliberately absent. Its artwork is built from a referenced shape that Android's vector
 * format cannot express, and the converter refuses it rather than emitting an icon that is nearly
 * right; the lettered token is the correct rendering for it.
 */
private val logos: Map<String, Int> = mapOf(
    "BTC" to R.drawable.asset_btc,
    "ETH" to R.drawable.asset_eth,
    "SOL" to R.drawable.asset_sol,
    "BNB" to R.drawable.asset_bnb,
    "XRP" to R.drawable.asset_xrp,
    "DOGE" to R.drawable.asset_doge,
    "TRX" to R.drawable.asset_trx,
)
