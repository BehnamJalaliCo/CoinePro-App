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
}
