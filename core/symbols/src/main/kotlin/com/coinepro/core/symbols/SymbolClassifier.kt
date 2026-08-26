package com.coinepro.core.symbols

import java.util.Locale

/**
 * Turns a feed's symbol string into something the app can display, group, rank and search.
 *
 * Neither backend sends any of that. CoinePro-FX returns the master MT5 account's symbol names and
 * TradeYar returns LBank's pair names — bare strings, in two different spelling conventions, with no
 * category, no Persian name and no base/quote split. Deriving them here is what lets the whole
 * universe be usable the moment the list arrives, instead of only the eight symbols somebody typed
 * into a constant.
 *
 * **Order matters and is not alphabetical.** Metal is tested before forex because `XAUUSD` is six
 * letters and would otherwise have to be a currency pair; energy and index are tested before both
 * because `USOIL` and `US500` start with a currency code. Crypto is last because its test is the
 * loosest — strip a known quote suffix and take what remains — and running it earlier would claim
 * symbols the specific tests were about to identify properly.
 */
object SymbolClassifier {

    fun classify(symbol: String): SymbolMeta {
        val clean = clean(symbol)
        val canonical = SymbolAliases.canonical(clean)
        val popular = canonical in POPULAR

        metal(clean, canonical, popular)?.let { return it }
        energy(clean, canonical, popular)?.let { return it }
        index(clean, canonical, popular)?.let { return it }
        forex(clean, canonical, popular)?.let { return it }
        crypto(clean, canonical, popular)?.let { return it }

        return SymbolMeta(
            symbol = clean,
            canonical = canonical,
            category = SymbolCategory.OTHER,
            base = null,
            quote = null,
            description = clean,
            popular = popular,
        )
    }

    /** Classify a whole feed list, preserving its order — which is the server's own ranking. */
    fun classifyAll(symbols: List<String>): List<SymbolMeta> = symbols.map(::classify)

    /**
     * Symbols that are feed noise rather than instruments.
     *
     * Both catalogues carry fragments — `1`, `4`, `2Z`, `0G` — and leveraged-token shards like
     * `1COIN` that name nothing tradable here. The test is deliberately narrow: too short to be a
     * ticker, or unclassifiable *and* containing a digit. A real instrument this app has not heard
     * of stays, because dropping it would be worse than showing it plainly.
     */
    fun isNoise(symbol: String): Boolean {
        val clean = clean(symbol)
        if (clean.length < 3) return true
        if (classify(clean).category != SymbolCategory.OTHER) return false
        return clean.any(Char::isDigit)
    }

    private fun clean(symbol: String): String =
        symbol.uppercase(Locale.US).filter { it.isLetterOrDigit() }

    // ------------------------------------------------------------------ per-category tests

    private fun metal(clean: String, canonical: String, popular: Boolean): SymbolMeta? {
        val base = SymbolNames.METAL.keys.firstOrNull { canonical.startsWith(it) } ?: return null
        val quote = canonical.drop(base.length).take(3).ifEmpty { "USD" }
        val quoteName = SymbolNames.CURRENCY[quote]
        return SymbolMeta(
            symbol = clean,
            canonical = canonical,
            category = SymbolCategory.METAL,
            base = base,
            quote = quote,
            description = SymbolNames.METAL.getValue(base) + if (quoteName != null) " / $quoteName" else "",
            popular = popular,
        )
    }

    private fun energy(clean: String, canonical: String, popular: Boolean): SymbolMeta? {
        val name = SymbolNames.ENERGY[canonical] ?: return null
        return SymbolMeta(
            symbol = clean,
            canonical = canonical,
            category = SymbolCategory.ENERGY,
            base = null,
            quote = null,
            description = name,
            popular = popular,
        )
    }

    private fun index(clean: String, canonical: String, popular: Boolean): SymbolMeta? {
        val name = SymbolNames.INDEX[canonical] ?: return null
        return SymbolMeta(
            symbol = clean,
            canonical = canonical,
            category = SymbolCategory.INDEX,
            base = null,
            quote = null,
            description = name,
            popular = popular,
        )
    }

    private fun forex(clean: String, canonical: String, popular: Boolean): SymbolMeta? {
        if (canonical.length != 6) return null
        val base = canonical.take(3)
        val quote = canonical.drop(3)
        val baseName = SymbolNames.CURRENCY[base] ?: return null
        val quoteName = SymbolNames.CURRENCY[quote] ?: return null
        return SymbolMeta(
            symbol = clean,
            canonical = canonical,
            category = SymbolCategory.FOREX,
            base = base,
            quote = quote,
            description = "$baseName / $quoteName",
            popular = popular,
        )
    }

    /**
     * A coin, identified by stripping a known quote currency off the end.
     *
     * At least two characters must survive the strip. Without that rule `WBTC` loses its
     * quote-looking tail and becomes the coin `W`, and `XBTUSD` becomes `XB` — both wrong, and both
     * wrong *silently*, since a two-letter ticker is perfectly plausible.
     *
     * `BUSD` and `TUSD` are absent from the list on purpose even though they are quote currencies.
     * Stripping them would eat the tail of every symbol ending in those letters; the cost of leaving
     * them out is that `ETHBUSD` is not recognised, which is the cheaper mistake.
     */
    private fun crypto(clean: String, canonical: String, popular: Boolean): SymbolMeta? {
        val quote = QUOTES.firstOrNull { canonical.length >= it.length + 2 && canonical.endsWith(it) }
            ?: return null
        val base = canonical.dropLast(quote.length)
        // The wrapped form keeps its own ticker on screen — `WBTCUSDT` is not `BTCUSDT` and a list
        // showing both must say so — but ranks and reads as the asset it wraps.
        val asset = SymbolAliases.canonicalBase(base)
        val name = SymbolNames.CRYPTO[asset]
        return SymbolMeta(
            symbol = clean,
            canonical = asset + quote,
            category = SymbolCategory.CRYPTO,
            base = base,
            quote = quote,
            description = if (name != null) "$name ($base)" else base,
            popular = popular,
        )
    }

    /** Longest first, so `USDT` is tried before the `USD` inside it. */
    private val QUOTES = listOf("USDT", "USDC", "USD", "BTC", "ETH")

    /**
     * The symbols that get a rank boost in search, in their canonical spelling.
     *
     * Deliberately short. This is not "the good markets" — it is the dozen-odd anyone typing two
     * letters almost certainly means, and every name added to it dilutes the ones already there.
     */
    private val POPULAR = setOf(
        "EURUSD", "GBPUSD", "USDJPY", "AUDUSD", "USDCAD", "USDCHF", "GBPJPY",
        "XAUUSD", "XAGUSD",
        "BTCUSDT", "ETHUSDT", "BTCUSD", "ETHUSD",
        "US30", "US500", "US100",
    )
}
