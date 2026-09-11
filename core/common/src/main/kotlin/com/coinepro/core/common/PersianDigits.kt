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
    // A Latin comma rather than U+066C ARABIC THOUSANDS SEPARATOR, which is the typographically
    // Persian character and lays out wrong. U+066C carries the bidi class of an Arabic number, so
    // between two runs of Persian digits it does not merge with them — it stays its own run and
    // ends up on the far side, so «۵۲٬۳۴۰» draws as «۳۴۰٬۵۲». The comma is a common separator, it
    // merges, and IRANYekanX draws the two glyphs identically. See BidiText.isolateNumericRuns,
    // which does the same swap to the numbers inside the server's prose.
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    val signed = if (this < 0) "−" + grouped else grouped
    return signed.map { character ->
        if (character in '0'..'9') '۰' + (character - '0') else character
    }.joinToString("")
}

/**
 * The language the *app* is in, for the code that runs where there is no composition to ask.
 *
 * ### Why this exists
 *
 * A count in prose takes Persian digits — «۴ نماد» — and the same count in English prose takes
 * Latin ones. Until 4.71.0 every prose count in this app was Persian whatever language the screen
 * was in, so the English watchlist read «۴ symbols»: four in Persian, the noun in English, in one
 * phrase. A screen can ask its own configuration what language it is in (`inEnglish()` in the
 * design system, which is what composable code uses and what a render test can override). A widget
 * worker, a notification builder and a timeframe label cannot — they run with no activity and no
 * composition — and this is what they read instead.
 *
 * It is set in `AppLanguageStore`, beside `Locale.setDefault`, on the same two paths: the activity
 * being based on the stored language, and the reader choosing a new one. [AppLanguage.Default]
 * until then, which is the language the app opens in.
 */
object AppLocale {
    @Volatile
    var language: AppLanguage = AppLanguage.Default

    val english: Boolean get() = language == AppLanguage.ENGLISH
}

/**
 * A prose count in the app's own language: «۴» in Persian, `4` in English.
 *
 * This is the one every screen should call. [Int.toPersianDigits] is the unconditional conversion
 * underneath it and stays for the two callers that mean it literally — a Persian date, a Persian
 * numeral written into Persian copy — but a count that sits in a sentence follows the sentence.
 *
 * The prohibition is unchanged and is the reason the market rule is stated separately: **a price, a
 * quantity, a percentage or an axis figure never comes through here in either language.** Those are
 * [MarketNumberFormatter]'s, pinned to `Locale.US`, so a figure on this screen can be compared
 * against MetaTrader or TradingView without being read twice.
 *
 * @param english pass the screen's own answer where there is one — composable code has
 *   `Int.proseDigits()` in the design system, which reads the configuration the screen is drawn
 *   with. The default is [AppLocale], for the code that runs outside a screen.
 */
fun Int.proseDigits(english: Boolean = AppLocale.english): String =
    if (english) toString() else toPersianDigits()

/** A grouped prose count in the app's own language — «۵۲٬۳۴۰ عضو», `52,340 members`. */
fun Long.proseGroupedDigits(english: Boolean = AppLocale.english): String =
    if (english) {
        val digits = toString().removePrefix("-")
        val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
        if (this < 0) "−$grouped" else grouped
    } else {
        toPersianGroupedDigits()
    }
