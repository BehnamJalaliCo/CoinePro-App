package com.coinepro.core.symbols

/**
 * How liquid a market is, as far as the app can know offline.
 *
 * Real 24-hour volume would be the right answer and is not available: the feeds send volume only for
 * the symbols currently on screen, and the MT5 side often sends none at all. Sorting a thousand-row
 * catalogue therefore cannot wait on it. What is available is knowledge of which markets are the
 * large ones, and that is what this encodes — the majors in order, everything else behind them in
 * whatever order the server sent, which for the crypto side is already roughly market cap.
 *
 * Where live volume *is* known, it wins. [byLiquidity] takes it and falls back to this.
 */
object SymbolRanking {

    /**
     * Rank of a symbol among the markets this app considers major. Lower is more liquid;
     * [UNRANKED] for everything else.
     *
     * The two asset classes are **interleaved** rather than concatenated. Ranking all fifty forex
     * majors ahead of Bitcoin — or all hundred coins ahead of EURUSD — is an accident of which list
     * was written first, and it shows up as a mixed search result where one whole class sinks. So
     * position N of the forex list sits beside position N of the crypto list, and the class only
     * breaks the tie between them.
     */
    fun rank(symbol: String): Int = rank(SymbolClassifier.classify(symbol))

    /** The same, for a symbol already classified — which is every caller inside a sort. */
    fun rank(meta: SymbolMeta): Int {
        val position = when (meta.category) {
            SymbolCategory.CRYPTO -> meta.base?.let { CRYPTO_POSITION[SymbolAliases.canonicalBase(it)] }
            else -> FOREX_POSITION[meta.canonical]
        } ?: return UNRANKED
        return position * CLASS_WEIGHTS.size + (CLASS_WEIGHTS[meta.category] ?: CLASS_WEIGHTS.size)
    }

    /** No opinion. Sorts last, and a stable sort then leaves the server's own order intact. */
    const val UNRANKED: Int = Int.MAX_VALUE

    /**
     * Compare two markets by liquidity, preferring live volume when both sides have it.
     *
     * A market with no volume figure sorts *after* every market that has one, rather than being
     * treated as zero. Zero would be a fabricated number, and it would push the entire MT5 side —
     * which reports no volume at all — to the bottom of any mixed list.
     */
    fun byLiquidity(volumeA: Double?, volumeB: Double?, symbolA: String, symbolB: String): Int {
        val a = volumeA?.takeIf { it.isFinite() && it > 0 }
        val b = volumeB?.takeIf { it.isFinite() && it > 0 }
        return when {
            a != null && b != null -> b.compareTo(a)
            a != null -> -1
            b != null -> 1
            else -> rank(symbolA).compareTo(rank(symbolB))
        }
    }

    /**
     * Breaks the tie when a forex market and a coin hold the same position in their own lists.
     *
     * Gold first is not sentiment about gold — it is the instrument this product was built around,
     * and both backends quote it.
     */
    private val CLASS_WEIGHTS: Map<SymbolCategory, Int> = mapOf(
        SymbolCategory.METAL to 0,
        SymbolCategory.FOREX to 1,
        SymbolCategory.CRYPTO to 2,
        SymbolCategory.INDEX to 3,
        SymbolCategory.ENERGY to 4,
        SymbolCategory.OTHER to 5,
    )

    /**
     * The forex, metal, index and energy majors, most traded first.
     *
     * One list rather than four because they compete for the same rows: a user browsing "فارکس"
     * sees all of them, and separate lists would only raise the question of how to merge them.
     */
    private val FOREX_MAJORS = listOf(
        "EURUSD", "GBPUSD", "USDJPY", "XAUUSD", "USDCHF", "AUDUSD", "USDCAD", "NZDUSD",
        "EURJPY", "GBPJPY", "XAGUSD", "EURGBP", "US30", "US500", "US100", "USOIL",
        "EURCHF", "AUDJPY", "EURAUD", "EURCAD", "GBPCHF", "AUDNZD", "NZDJPY", "GBPAUD",
        "GBPCAD", "USDSGD", "USDHKD", "USDTRY", "USDMXN", "USDZAR", "EURNZD", "CADJPY",
        "CHFJPY", "AUDCAD", "AUDCHF", "CADCHF", "NZDCAD", "GBPNZD", "XPTUSD", "XPDUSD",
        "UKOIL", "NATGAS", "GER40", "UK100", "JPN225", "FRA40", "EU50", "HK50", "AUS200",
    )

    /** Coin bases, largest first. */
    private val CRYPTO_MAJORS = listOf(
        "BTC", "ETH", "USDT", "BNB", "SOL", "XRP", "USDC", "ADA", "DOGE", "TRX",
        "AVAX", "SHIB", "DOT", "LINK", "BCH", "NEAR", "POL", "MATIC", "LTC", "UNI",
        "ICP", "DAI", "ETC", "APT", "XLM", "RENDER", "ATOM", "XMR", "OKB", "FIL",
        "HBAR", "ARB", "VET", "MKR", "INJ", "OP", "IMX", "GRT", "AAVE", "STX",
        "TAO", "RUNE", "FTM", "SEI", "THETA", "FLOW", "LDO", "TIA", "SUI", "ALGO",
        "EGLD", "QNT", "BSV", "GALA", "JUP", "FLR", "KCS", "PYTH", "ORDI", "WIF",
        "JASMY", "BONK", "PEPE", "FLOKI", "AXS", "SAND", "MANA", "CHZ", "EOS", "XTZ",
        "NEO", "KAVA", "MINA", "ROSE", "IOTA", "GMX", "ENS", "DYDX", "CFX", "ONDO",
        "WLD", "ENA", "STRK", "ZK", "W", "ETHFI", "BLUR", "1INCH", "COMP", "CRV",
        "SNX", "SUSHI", "YFI", "ZEC", "DASH", "BAT", "ZIL", "ONE", "CELO", "ANKR",
    )

    private val FOREX_POSITION: Map<String, Int> =
        FOREX_MAJORS.withIndex().associate { (index, symbol) -> symbol to index }

    private val CRYPTO_POSITION: Map<String, Int> =
        CRYPTO_MAJORS.withIndex().associate { (index, base) -> base to index }
}
