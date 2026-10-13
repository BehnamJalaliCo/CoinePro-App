package com.coinepro.core.symbols

/**
 * How many decimals an instrument is quoted to, where its kind decides rather than its size
 * (5.32.0).
 *
 * The rest of the app picks a price's decimals from its magnitude — two for anything above one —
 * which is right for a coin and wrong for a currency pair: EURUSD at 1.11876 came out as «1.12» in
 * the watchlist, the legend, the axis and the details pane, all four, while the tab title showed
 * the pip. A trader reads a pair to the pipette, and the venues and TradingView quote it so.
 *
 * Null means «no rule for this kind»: the caller keeps its magnitude rule. Crypto stays there on
 * purpose — a coin at 1.36 and a pair at 1.36 are not quoted alike, and only the kind tells them
 * apart.
 */
object SymbolPrecision {

    fun of(symbol: String): Int? {
        val meta = SymbolClassifier.classify(symbol)
        return when (meta.category) {
            // The pipette: five places, three where the quote currency is the yen.
            SymbolCategory.FOREX -> if (meta.quote == "JPY" || meta.canonical.endsWith("JPY")) 3 else 5
            // Silver at 60.41 is quoted to the tenth of a cent; the magnitude rule gave two places.
            // Gold, the indices and the energy contracts already read right by magnitude.
            SymbolCategory.METAL -> if (meta.canonical.startsWith("XAG")) 3 else null
            else -> null
        }
    }
}
