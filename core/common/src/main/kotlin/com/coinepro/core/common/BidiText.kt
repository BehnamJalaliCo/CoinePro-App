package com.coinepro.core.common

/**
 * Keeps Latin-scripted values readable inside right-to-left copy.
 *
 * Prices, symbols and identifiers stay Latin so they remain comparable with broker and exchange
 * terminals, which means every one of them is a left-to-right run embedded in Persian text. Without
 * an isolate the surrounding paragraph direction reorders neighbouring punctuation and adjacent
 * runs merge into each other, so `2,412.85` next to `-0.31%` can render with the signs on the wrong
 * values.
 *
 * The isolate characters are formatting-only: they carry no width and never affect string equality
 * checks on the numeric text itself, but they do change [String.length]. Apply this at the moment
 * of display, never before parsing or comparing.
 */
object BidiText {
    /** U+2066 LEFT-TO-RIGHT ISOLATE */
    private const val LRI = '⁦'

    /** U+2069 POP DIRECTIONAL ISOLATE */
    private const val PDI = '⁩'

    /** Wraps [value] so it renders left-to-right regardless of the surrounding paragraph. */
    fun isolateLtr(value: String): String = if (value.isEmpty()) value else "$LRI$value$PDI"

    /** Removes any isolates previously added by [isolateLtr]. */
    fun strip(value: String): String = value.replace(LRI.toString(), "").replace(PDI.toString(), "")

    /**
     * Whether a whole sentence is Latin, and therefore a paragraph rather than a run.
     *
     * ### Why this is a different question from [isolateLtr]
     *
     * An isolate is for a *run* — a price, a ticker, a percentage — sitting inside Persian copy.
     * It keeps the run's own characters in order and the surrounding paragraph stays right-to-left,
     * which is correct, because the paragraph really is Persian.
     *
     * A whole English sentence is not a run. Laid out in a right-to-left paragraph it comes back
     * with its **final full stop at the beginning**: «.lifting precious metals». That is what a news
     * card looked like the moment its stories started arriving from an English wire — the words in
     * order, the sentence-ending punctuation on the wrong end of the line, on every row.
     *
     * So a caller asks this and sets the paragraph's own direction, rather than isolating a
     * sentence that has nothing around it to be isolated from.
     *
     * ### Where the line is drawn
     *
     * On the letters only, ignoring digits, spaces and punctuation — those are direction-neutral
     * and counting them would call «۱۲ BTC» Latin. A string with no letters at all is not Latin: a
     * bare figure is a run and belongs to whatever paragraph it sits in.
     */
    fun isLatinSentence(value: String): Boolean {
        var latin = 0
        var other = 0
        for (character in value) {
            if (!character.isLetter()) continue
            if (character.code < 0x0250) latin++ else other++
        }
        return latin > 0 && latin > other
    }
}
