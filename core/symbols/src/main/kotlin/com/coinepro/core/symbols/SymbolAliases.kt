package com.coinepro.core.symbols

/**
 * The same instrument under the several names the feeds spell it.
 *
 * MT5 brokers do not agree on symbol names. The Nasdaq 100 arrives as `NAS100`, `US100`, `NDX` or
 * `USTEC` depending on whose server is behind the account; gold as `XAUUSD` or `GOLD`; WTI as
 * `USOIL`, `WTI` or `XTIUSD`. None of that is an error, and a rename on the broker's side must not
 * turn a ranked major into an unrecognised string that sorts to the bottom of every list.
 *
 * So classification, ranking and artwork all key off the canonical name, while the feed's own
 * spelling stays the identity that goes back on the wire. See [SymbolMeta] for why those are two
 * different fields rather than one.
 *
 * Aliases only ever *narrow* to a name this app already knows. Adding one that points at a symbol
 * with no entry anywhere else buys nothing.
 */
object SymbolAliases {

    fun canonical(clean: String): String = SYMBOLS[clean] ?: clean

    /**
     * The same coin under another name, applied to a **base** rather than to a whole symbol.
     *
     * These have to be separate from [SYMBOLS] because a coin alias has to survive the quote
     * currency: the feed says `WBTCUSDT`, not `WBTC`, so a whole-symbol table would never fire. It
     * is applied after the quote suffix is stripped.
     */
    fun canonicalBase(base: String): String = BASES[base] ?: base

    private val SYMBOLS: Map<String, String> = mapOf(
        // Indices — the biggest source of disagreement between brokers.
        "JP225" to "JPN225", "NI225" to "JPN225", "NKY" to "JPN225", "NIK225" to "JPN225",
        "DE40" to "GER40", "DE30" to "GER40", "GER30" to "GER40", "DAX" to "GER40", "DAX40" to "GER40",
        "NAS100" to "US100", "NAS" to "US100", "NDX" to "US100", "USTEC" to "US100",
        "SPX" to "US500", "SPX500" to "US500", "SP500" to "US500", "US500CASH" to "US500",
        "DJI" to "US30", "DJ30" to "US30", "WS30" to "US30", "DOW" to "US30",
        "FTSE" to "UK100", "FTSE100" to "UK100",
        "CAC40" to "FRA40", "FR40" to "FRA40",
        "STOXX50" to "EU50", "EUSTX50" to "EU50", "SX5E" to "EU50",
        "HSI" to "HK50",
        "AU200" to "AUS200", "ASX200" to "AUS200",

        // Metals.
        "GOLD" to "XAUUSD", "SILVER" to "XAGUSD",

        // Energy.
        "WTI" to "USOIL", "WTIUSD" to "USOIL", "XTIUSD" to "USOIL", "OILUSD" to "USOIL",
        "BRENT" to "UKOIL", "BRENTUSD" to "UKOIL", "XBRUSD" to "UKOIL",
        "NGAS" to "NATGAS", "XNGUSD" to "NATGAS", "NGASUSD" to "NATGAS",
    )

    /**
     * Coins that were renamed, and the wrapped or staked forms of one asset.
     *
     * No archive draws a separate mark for a wrapped token and no trader thinks of it as a separate
     * coin, so `WBTCUSDT` should rank, read and illustrate as Bitcoin.
     *
     * `POL` and `MATIC` are deliberately *not* collapsed into each other: both spellings are live on
     * the feeds during the migration, and a table that renamed one to the other would make the pair
     * look like a duplicate listing in a list that shows both.
     */
    private val BASES: Map<String, String> = mapOf(
        "WBTC" to "BTC", "XBT" to "BTC",
        "WETH" to "ETH", "BETH" to "ETH", "STETH" to "ETH",
        "WSOL" to "SOL", "WBNB" to "BNB",
        "RNDR" to "RENDER",
        "BCC" to "BCH",
        "XNO" to "NANO",
    )
}
