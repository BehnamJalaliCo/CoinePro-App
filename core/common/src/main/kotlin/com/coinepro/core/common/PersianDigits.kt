package com.coinepro.core.common

/**
 * Rewrites Persian and Arabic-Indic digits as Latin ones, leaving everything else alone.
 *
 * A Persian keyboard produces ۰-۹ by default, so this is what a reader types into any numeric field
 * unless they deliberately switch layouts. Sending those characters to a server that expects Latin
 * ones fails in the worst possible way: the field looks correct on screen, the refusal says the
 * value is wrong, and the reader has no way to see the difference.
 *
 * `Char.isDigit()` is not a defence — Persian digits are Unicode category Nd, so a filter built on
 * it keeps them and passes them through unchanged. Fold first, then filter.
 */
fun String.foldDigitsToLatin(): String = map { character ->
    when (character) {
        in '۰'..'۹' -> '0' + (character - '۰') // Persian (Extended Arabic-Indic)
        in '٠'..'٩' -> '0' + (character - '٠') // Arabic-Indic
        else -> character
    }
}.joinToString("")
