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

/**
 * Rewrites Latin digits as Persian ones — the opposite direction, and a much narrower licence.
 *
 * This is for **prose counts only**: "۷ درس", "بند ۶", "۳ نتیجه". It must never touch a market
 * figure. A price, a quantity, a percentage or a date on a chart axis stays Latin so a reader can
 * compare it against MetaTrader, LBank or TradingView without converting in their head — that rule
 * is the whole reason [MarketNumberFormatter] pins `Locale.US`, and passing a price through here
 * would undo it silently.
 *
 * There were three copies of this before it moved here — one in the design system, one in the help
 * catalogue, and one about to be written by hand as `"${'$'}{index + 1}."`. The hand-written one is
 * how the rule actually gets broken: nobody writing a numbered list thinks of themselves as
 * formatting a number.
 */
fun Int.toPersianDigits(): String = toString().map { character ->
    if (character in '0'..'9') '۰' + (character - '0') else character
}.joinToString("")

/**
 * A large prose count, grouped — «۵۲٬۳۴۰ عضو».
 *
 * Same licence as [Int.toPersianDigits] and the same prohibition: prose only, never a market
 * figure. The separator is U+066C, the Arabic thousands separator, and not a Latin comma, because
 * a comma between Persian digits is the one punctuation mark that reads as a decimal point to
 * roughly half the world.
 *
 * Grouping is the reason this exists separately rather than as an overload of [Int.toPersianDigits].
 * The counts it is for run to five and six figures, where ungrouped digits stop being readable; the
 * counts that one is for are list positions and lesson numbers, where a separator would be noise.
 */
fun Long.toPersianGroupedDigits(): String {
    val digits = toString().removePrefix("-")
    val grouped = digits.reversed().chunked(3).joinToString("٬").reversed()
    val signed = if (this < 0) "−" + grouped else grouped
    return signed.map { character ->
        if (character in '0'..'9') '۰' + (character - '0') else character
    }.joinToString("")
}
