package com.coinepro.core.symbols

/**
 * What kind of thing a market symbol names.
 *
 * [OTHER] is not a failure state. Both feeds quote instruments this app has never heard of — a new
 * listing, a broker-specific contract, an index nobody outside one venue trades — and the honest
 * answer for those is to show them with their ticker rather than to hide them or to guess a
 * category. Every screen that groups by category needs a bucket for them, so it is a member of the
 * enum rather than a null.
 */
enum class SymbolCategory {
    FOREX,
    CRYPTO,
    METAL,
    INDEX,
    ENERGY,
    OTHER,
}

/**
 * Everything the app knows about one market symbol without asking a server.
 *
 * Neither backend sends a category, a Persian name or a base/quote split — CoinePro-FX returns MT5
 * symbol strings and TradeYar returns LBank pair strings, and that is all. This is derived
 * client-side so the whole universe is usable the moment the symbol list arrives, rather than only
 * the handful anybody hand-listed.
 *
 * The three name fields are deliberately separate:
 *
 * * [symbol] is **identity**. It is the feed's own spelling, cleaned of punctuation and upper-cased,
 *   and it is what goes back on the wire. Nothing may substitute a prettier name for it.
 * * [canonical] is the same instrument under this app's preferred name — `GOLD` and `XAUUSD` are one
 *   market, and ranking, aliasing and artwork all key off this. It is never sent to a server.
 * * [description] is what a Persian reader sees.
 */
data class SymbolMeta(
    val symbol: String,
    val canonical: String,
    val category: SymbolCategory,
    val base: String?,
    val quote: String?,
    val description: String,
    val popular: Boolean,
) {
    /**
     * The symbol written the way a terminal writes it — `EUR/USD`, `BTC/USDT`, `XAU/USD`.
     *
     * An index or an energy contract has no meaningful split, so it is shown whole rather than cut
     * at an arbitrary three characters.
     */
    val pretty: String get() = if (base != null && quote != null) "$base/$quote" else symbol

    /**
     * The short label for a tile or a chip, where there is room for one word.
     *
     * A coin is its base alone: `BTCUSDT` and `BTCUSD` are the same asset quoted twice, and a grid
     * showing both spelled out reads as two different markets.
     */
    val short: String get() = when {
        category == SymbolCategory.CRYPTO && base != null -> base
        base != null && quote != null -> "$base/$quote"
        else -> symbol
    }

    /**
     * [description], cut to something that fits under a ticker in a list.
     *
     * ### Both legs, in short names
     *
     * A pair's full description is two spelled-out currencies — «دلار آمریکا / فرانک سوئیس», twenty-
     * four characters — and no column narrow enough to leave room for a price will hold it. What
     * shipped first was every forex row ending in an ellipsis.
     *
     * The fix for *that* was to print the **base** alone, and it was wrong in a way the ellipsis at
     * least was not: USDJPY, USDCHF and USDCAD all came out «دلار آمریکا». Three instruments, one
     * subtitle, in the column whose entire job is telling them apart — and a reader seeing the same
     * words on three consecutive rows reads it as a bug, correctly.
     *
     * A pair is two things and the row has to say both, so both are said in the short names
     * [SymbolNames.shortDisplayOf] keeps: «دلار/ین» is seven characters and fits under any ticker.
     *
     * ### A coin is a pair too
     *
     * The classifier's description for a coin is «بیت‌کوین (BTC)», which is right for search — a
     * reader who types either half must find it — and wrong for a row, because the ticker `BTC/USDT`
     * is already on the line above it. The parenthesis said the same thing twice and said nothing
     * about the quote leg, so `BTC/USDT` and `BTC/USDC` came out identical underneath two different
     * tickers. Drawn as «بیت‌کوین/تتر» it is the same shape as the forex rows above it and the two
     * legs are the two legs.
     *
     * An index keeps its description, which is already its name.
     *
     * Search still matches on [description] — a reader typing «فرانک سوئیس» must still find USDCHF,
     * and would not if the long name had been dropped from the thing being searched rather than
     * from the thing being drawn.
     */
    val listDescription: String get() = when (category) {
        SymbolCategory.FOREX, SymbolCategory.METAL -> {
            val first = base?.let(SymbolNames::shortDisplayOf)
            val second = quote?.let(SymbolNames::shortDisplayOf)
            when {
                first != null && second != null && first != second -> "$first/$second"
                first != null -> first
                else -> description
            }
        }
        SymbolCategory.CRYPTO -> {
            val first = base?.let { SymbolNames.CRYPTO[it] }
            val second = quote?.let { SymbolNames.CRYPTO[it] ?: SymbolNames.CURRENCY_SHORT[it] }
            when {
                first != null && second != null && first != second -> "$first/$second"
                // No Persian name for the coin — a listing this app has never heard of — so the
                // classifier's own answer stands rather than half a pair.
                else -> description
            }
        }
        else -> description
    }
}

/** Whether a market is currently tradable, and whether the weekend is the reason it is not. */
data class MarketStatus(
    val open: Boolean,
    /** True only when the close is the ordinary weekend, which is worth saying differently. */
    val weekend: Boolean,
)
