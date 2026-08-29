package com.coinepro.core.symbols

import java.util.Locale

/**
 * How well a query matched one text, and which characters it landed on.
 *
 * [range] is null for a scattered match, where the query's letters appear in order but not
 * together. There is no single span to underline in that case, so it doubles as the test for
 * "was this a contiguous hit" — which is what a caller that cannot draw a highlight needs to
 * know before it shows a result at all.
 */
data class TextHit(val score: Int, val range: IntRange?)

/**
 * Scoring one string against a query, with nothing in it that knows about markets.
 *
 * This was private inside [SymbolSearch] until a second surface needed the same ordering. The
 * app-section catalogue in `feature:search` is searched by the same reader, in the same field, on
 * the same keystroke — and a second scorer would have meant two answers to "does a prefix beat a
 * substring", drifting apart the first time either was tuned. There is one, and both call it.
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
 * Within a kind, shorter and earlier wins: `EURUSD` beats `EURNZD` on nothing, but `EUR` beats
 * both, and a substring near the front beats one near the back.
 *
 * Nothing outside this object should depend on the absolute values. The gaps between the kinds are
 * the contract — a caller adding its own bonus has to keep it small enough not to cross one.
 */
object TextRanking {

    /**
     * Score one text against the query, or null when it does not match at all.
     *
     * Case-folded with [Locale.ROOT] rather than the device locale, because the Turkish locale maps
     * `I` to a dotless `ı` and would stop `BTC` from matching `btc` for a user whose phone is set to
     * Turkish. Persian text is unaffected by case folding either way.
     */
    fun score(text: String, query: String): TextHit? {
        val haystack = text.lowercase(Locale.ROOT)
        val needle = query.lowercase(Locale.ROOT)
        val slack = haystack.length - needle.length
        if (slack < 0) return null

        if (haystack == needle) return TextHit(EXACT, haystack.indices)
        if (haystack.startsWith(needle)) {
            return TextHit(PREFIX - slack * SLACK_PENALTY, 0 until needle.length)
        }
        val at = haystack.indexOf(needle)
        if (at >= 0) {
            return TextHit(SUBSTRING - at * POSITION_PENALTY - slack * 2, at until at + needle.length)
        }
        return subsequence(haystack, needle)
    }

    /** Every letter of the query, in order, but not adjacent. Fewer gaps scores higher. */
    private fun subsequence(haystack: String, needle: String): TextHit? {
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
        return TextHit(SUBSEQUENCE - gaps * SLACK_PENALTY, null)
    }

    // Scores are scaled by ten so the penalties can be whole numbers and the ordering stays exactly
    // reproducible in tests.
    const val EXACT = 10_000
    const val PREFIX = 8_000
    const val SUBSTRING = 6_000
    const val SUBSEQUENCE = 3_000

    private const val SLACK_PENALTY = 10
    private const val POSITION_PENALTY = 10
}
