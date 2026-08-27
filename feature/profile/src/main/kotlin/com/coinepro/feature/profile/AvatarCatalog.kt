package com.coinepro.feature.profile

import com.coinepro.core.model.AvatarMark
import com.coinepro.core.symbols.SymbolArtwork

/**
 * The elements the app puts in front of a reader choosing an avatar.
 *
 * Not "every symbol we have artwork for". Five hundred and sixty crypto marks in a grid is a
 * warehouse, and a reader picking a profile picture is not shopping — they want the one that is
 * obviously theirs, in about four seconds. So each list here is short, ordered by what people
 * actually hold, and every entry is checked against [SymbolArtwork] at construction rather than
 * hoped for: an element that renders as a lettered disc would be the one thing on this screen that
 * looks like a bug, on the screen where the reader is deciding whether this app is serious.
 *
 * Anything not on these lists is still reachable — the composer takes any symbol the search screen
 * can find — this is the shelf, not the limit.
 */
object AvatarCatalog {

    /** The coins people actually name themselves after, filtered to what ships. */
    val CRYPTO: List<String> = listOf(
        "BTC", "ETH", "SOL", "BNB", "XRP", "DOGE", "ADA", "TRX", "AVAX", "LINK",
        "TON", "DOT", "MATIC", "SHIB", "LTC", "UNI", "ATOM", "XLM", "NEAR", "APT",
        "FIL", "ARB", "OP", "INJ", "SUI", "PEPE", "RNDR", "AAVE", "SAND", "GRT",
    ).filter { it in SymbolArtwork.BASES }

    /**
     * The major pairs, drawn as two flags.
     *
     * The order is the one every forex desk lists them in — majors first, then the yen crosses —
     * rather than alphabetical, which would open the grid on AUDUSD.
     */
    val FOREX: List<String> = listOf(
        "EURUSD", "GBPUSD", "USDJPY", "USDCHF", "AUDUSD", "USDCAD", "NZDUSD",
        "EURGBP", "EURJPY", "GBPJPY", "AUDJPY", "CHFJPY", "EURAUD", "USDTRY",
    ).filter(SymbolArtwork::covers)

    /** The four metals, which get a disc rather than a flag. */
    val METALS: List<String> = listOf("XAUUSD", "XAGUSD", "XPTUSD", "XPDUSD")
        .filter(SymbolArtwork::covers)

    /** Every mark, in the order they are offered. See [AvatarMark] for what each one is. */
    val MARKS: List<AvatarMark> = AvatarMark.entries.toList()
}
