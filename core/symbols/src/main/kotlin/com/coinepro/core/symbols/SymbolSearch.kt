package com.coinepro.core.symbols

import java.util.Locale

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
 * Four match kinds, scored well apart so a weaker kind can never overtake a stronger one on
 * tie-breaks alone:
 *
 * | Kind | Score | Example for `eur` |
 * | --- | --- | --- |
 * | exact | 10000 | `EUR` |
 * | prefix | ~8000 | `EURUSD` |
 * | contiguous substring | ~6000 | `XAUEUR` |
 * | scattered subsequence | ~3000 | `E`…`U`…`R` |
 *
 * Within a kind, shorter and earlier wins: `EURUSD` beats `EURNZD` on nothing, but `EUR` beats both,
 * and a substring near the front beats one near the back. A popular market gets a small boost —
 * enough to lift `EURUSD` over `EURNZD`, nowhere near enough to lift a substring over a prefix.
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
            score(meta.symbol, needle)?.let { MatchField.SYMBOL to it },
            meta.base?.let { base -> score(base, needle)?.let { MatchField.BASE to it } },
            score(meta.description, needle)?.let { MatchField.DESCRIPTION to it },
        )
        val (field, hit) = candidates.maxByOrNull { it.second.score } ?: return null
        return SymbolMatch(
            meta = meta,
            score = hit.score + if (meta.popular) POPULAR_BOOST else 0,
            field = field,
            range = hit.range,
        )
    }

    private data class Hit(val score: Int, val range: IntRange?)

    /**
     * Score one text against the query.
     *
     * Case-folded with [Locale.ROOT] rather than the device locale, because the Turkish locale maps
     * `I` to a dotless `ı` and would stop `BTC` from matching `btc` for a user whose phone is set to
     * Turkish. Persian text is unaffected by case folding either way.
     */
    private fun score(text: String, query: String): Hit? {
        val haystack = text.lowercase(Locale.ROOT)
        val needle = query.lowercase(Locale.ROOT)
        val slack = haystack.length - needle.length
        if (slack < 0) return null

        if (haystack == needle) return Hit(EXACT, haystack.indices)
        if (haystack.startsWith(needle)) {
            return Hit(PREFIX - slack * SLACK_PENALTY, 0 until needle.length)
        }
        val at = haystack.indexOf(needle)
        if (at >= 0) {
            return Hit(SUBSTRING - at * POSITION_PENALTY - slack * 2, at until at + needle.length)
        }
        return subsequence(haystack, needle)
    }

    /** Every letter of the query, in order, but not adjacent. Fewer gaps scores higher. */
    private fun subsequence(haystack: String, needle: String): Hit? {
        var matched = 0
        var gaps = 0
        var previous = -1
        for (index in haystack.indices) {
            if (matched == needle.length) break
            if (haystack[index] != needle[matched]) continue
            if (previous >= 0) gaps += index - previous - 1
            previous = index
            matched++
        }
        if (matched != needle.length) return null
        return Hit(SUBSEQUENCE - gaps * SLACK_PENALTY, null)
    }

    // Scores are scaled by ten so the penalties can be whole numbers and the ordering stays exactly
    // reproducible in tests. Nothing outside this file should depend on the absolute values.
    private const val EXACT = 10_000
    private const val PREFIX = 8_000
    private const val SUBSTRING = 6_000
    private const val SUBSEQUENCE = 3_000

    private const val SLACK_PENALTY = 10
    private const val POSITION_PENALTY = 10

    /**
     * Enough to order two equally good matches, not enough to reorder two different match kinds.
     *
     * The gap between kinds is two thousand; this is 250. That ratio is the whole design: popularity
     * is a tie-break, and a boost large enough to lift a scattered match over a prefix match would
     * make the search feel like it was ignoring what was typed.
     */
    private const val POPULAR_BOOST = 250
}
