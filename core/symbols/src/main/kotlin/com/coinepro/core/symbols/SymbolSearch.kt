package com.coinepro.core.symbols

/** Which text a query matched, so the row can underline the part that hit. */
enum class MatchField {
    /** The ticker, e.g. `BTCUSDT`. */
    SYMBOL,

    /** The Persian description, e.g. «بیت‌کوین (BTC)». */
    DESCRIPTION,

    /** The base alone, e.g. `BTC` — which is what most people type. */
    BASE,

    /** No query; every market matches equally. */
    NONE,
}

/**
 * One result: the market, how well it matched, and where.
 *
 * [range] is null for a scattered match, where the query's letters appear in order but not together.
 * There is no single span to underline in that case, and underlining each letter separately turns
 * the row into confetti.
 */
data class SymbolMatch(
    val meta: SymbolMeta,
    val score: Int,
    val field: MatchField,
    val range: IntRange?,
)

/**
 * Ranked symbol search.
 *
 * The app had a `contains` filter, which is the wrong tool for a catalogue of this size in two ways
 * a user notices immediately. It cannot rank — typing `BTC` put `WBTCUSDT` and `BTCUSDT` on equal
 * footing, in whatever order the list happened to be in. And it searched the ticker only, so a
 * Persian speaker typing «بیت‌کوین» got nothing at all, in an app that is Persian by default.
 *
 * The four match kinds and their spacing live in [TextRanking], which is shared with the app-section
 * catalogue so both halves of one search field rank the same way. What this object adds on top is
 * everything that knows about markets: which three texts a market can be found by, and the two
 * tie-breaks below.
 *
 * A popular market gets a small boost — enough to lift `EURUSD` over `EURNZD`, nowhere near enough
 * to lift a substring over a prefix.
 *
 * The final tie-break is liquidity, not the alphabet. Alphabetical order is what made the old list
 * feel random: `AAVEUSDT` first, always, whatever you were looking for.
 */
object SymbolSearch {

    /**
     * @param query what the user typed. Empty means "browse", which is a ranked list rather than no
     *   list — popular markets, then the majors in order, then the server's own order behind them.
     * @param category null for every category.
     */
    fun search(
        markets: List<SymbolMeta>,
        query: String,
        category: SymbolCategory? = null,
    ): List<SymbolMatch> {
        val pool = if (category == null) markets else markets.filter { it.category == category }
        val needle = query.trim()
        if (needle.isEmpty()) {
            return pool
                .sortedWith(
                    compareByDescending<SymbolMeta> { it.popular }
                        .thenBy { SymbolRanking.rank(it) },
                )
                .map { SymbolMatch(it, score = 0, field = MatchField.NONE, range = null) }
        }
        return pool
            .mapNotNull { match(it, needle) }
            .sortedWith(
                compareByDescending<SymbolMatch> { it.score }
                    .thenBy { SymbolRanking.rank(it.meta) },
            )
    }

    /** The best of the three texts a market can be found by, or null when none of them match. */
    fun match(meta: SymbolMeta, query: String): SymbolMatch? {
        val needle = query.trim()
        if (needle.isEmpty()) return SymbolMatch(meta, 0, MatchField.NONE, null)

        val candidates = listOfNotNull(
            TextRanking.score(meta.symbol, needle)?.let { MatchField.SYMBOL to it },
            meta.base?.let { base -> TextRanking.score(base, needle)?.let { MatchField.BASE to it } },
            TextRanking.score(meta.description, needle)?.let { MatchField.DESCRIPTION to it },
        )
        val (field, hit) = candidates.maxByOrNull { it.second.score } ?: return null
        return SymbolMatch(
            meta = meta,
            score = hit.score + if (meta.popular) POPULAR_BOOST else 0,
            field = field,
            range = hit.range,
        )
    }

    /**
     * Enough to order two equally good matches, not enough to reorder two different match kinds.
     *
     * The gap between kinds in [TextRanking] is two thousand; this is 250. That ratio is the whole
     * design: popularity is a tie-break, and a boost large enough to lift a scattered match over a
     * prefix match would make the search feel like it was ignoring what was typed.
     */
    private const val POPULAR_BOOST = 250
}
