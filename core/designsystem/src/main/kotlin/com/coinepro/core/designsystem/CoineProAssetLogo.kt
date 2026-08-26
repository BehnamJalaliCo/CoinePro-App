package com.coinepro.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * The table is generated: `scripts/design/build-symbol-logos.py` merges the archives under
 * `design/asset-logos` and writes both the drawables and [AssetLogoTable]. To add a market, drop
 * artwork in the archive and re-run the script — never hand-edit either output.
 *
 * Every mark is clipped to a disc and given a hairline ring, and neither is decoration.
 *
 * The clip is what makes a row of them look like one set. The archives disagree about shape — most
 * marks are drawn as a filled circle, but the newer listings arrive on a square canvas, and a
 * square among circles reads as a mistake rather than as a different coin.
 *
 * The ring solves the opposite problem. A good number of these coins use a
 * near-black disc — XRP, XLM, ATOM, Solana's wordmark — and against this app's near-black stage
 * they lose their edge entirely and read as a floating glyph rather than a coin. The ring costs
 * nothing on the bright marks and rescues the dark ones, which is why it is unconditional rather
 * than applied by a luminance test that would have to be recomputed per theme.
 */
@Composable
fun CoineProAssetLogo(
    symbol: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    // A forex or metal market is a pair and is drawn as one; only a coin is a single disc.
    if (isPairSymbol(symbol)) {
        CoineProPairLogo(symbol = symbol, modifier = modifier, size = size)
        return
    }
    val logo = logoFor(symbol)
    if (logo != null) {
        Image(
            painter = painterResource(logo),
            // Decorative: the row already names the instrument in text, so announcing the logo
            // separately would read the same thing twice.
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, CoineProColors.assetRing, CircleShape),
            // Crop rather than fit, so a mark drawn on a square canvas fills the disc instead of
            // sitting inside it at four fifths the size of its circular neighbours.
            contentScale = ContentScale.Crop,
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

/**
 * The instrument behind a market symbol — `BTCUSDT` and `BTCUSD` both give `BTC`.
 *
 * Two rules earn their place here, and both were bugs before the table grew past eight entries.
 *
 * A suffix is only stripped when at least two characters survive it. Without that, `WBTC` loses its
 * quote-looking tail and becomes `W`, and `XBTUSD` becomes `XB` — a symbol nobody has artwork for,
 * silently falling back to the letter W.
 *
 * `BUSD` and `TUSD` are deliberately absent from the suffix list for the same reason: they are
 * quote currencies, but stripping them would eat the base of any symbol ending in those letters.
 * The cost of leaving them out is that `ETHBUSD` finds no logo; the cost of putting them in is that
 * several symbols find the wrong one.
 */
internal fun baseOf(symbol: String): String {
    val clean = symbol.uppercase(Locale.US).filter { it.isLetterOrDigit() }
    val base = QUOTES.firstOrNull { clean.length >= it.length + 2 && clean.endsWith(it) }
        ?.let { clean.dropLast(it.length) }
        ?: clean
    return ALIASES[base] ?: base
}

/** Longest first, so `USDT` is tried before the `USD` inside it. */
private val QUOTES = listOf("USDT", "USDC", "USD", "BTC", "ETH")

/**
 * Symbols whose artwork lives under a different name.
 *
 * Wrapped and staked tokens are the bulk of it — they are the same asset with a different contract,
 * and no archive draws a separate mark for them. The rest are renames the archives predate.
 */
private val ALIASES = mapOf(
    "WBTC" to "BTC",
    "XBT" to "BTC",
    "WETH" to "ETH",
    "BETH" to "ETH",
    "STETH" to "ETH",
    "WSOL" to "SOL",
    "WBNB" to "BNB",
    "BCC" to "BCH",
    "IOTA" to "MIOTA",
    "XNO" to "NANO",
    "POL" to "MATIC",
    "RENDER" to "RNDR",
)

@DrawableRes
private fun logoFor(symbol: String): Int? = AssetLogoTable.forBase(baseOf(symbol))
