package com.coinepro.core.common

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * The one place that decides how a market figure is written.
 *
 * A price, a quantity, a percentage, an order count, a cache age: every one of them goes through
 * here, whether it is drawn by the chart renderer, laid out by Compose in a ladder, or typed into a
 * calculator's result field. The rule is the same in both locales — **Latin digits, dot decimal,
 * comma grouping** — so a reader can put the app beside MetaTrader, LBank or TradingView and see the
 * same digits. Persian digits are for prose counts only, and those go through [Int.toPersianDigits].
 *
 * The locale is pinned rather than read from the device because `String.format` follows the default
 * locale, and this app's default is Persian: without the pin an axis label comes out as «۲٬۵۹۲٫۶».
 * Before this object existed that sentence was written in six files, once per copy of the format
 * call; now it is written once, and the copies are gone.
 *
 * Tabular figures are not applied here because the font already has them: IRANYekanX's Latin digits
 * share one advance (562 units, measured in `check-cross-phase-consistency.py`'s tabular-digit gate),
 * so a column of these strings lines up without a `tnum` feature. If the font ever changes, that
 * gate fails first.
 *
 * Nothing here isolates its output for bidi. Callers that place a figure inside right-to-left copy
 * wrap it in [BidiText.isolateLtr] — see [MarketNumberFormatter], which does exactly that.
 */
object NumberStyle {
    val locale: Locale = Locale.US

    private val symbols = DecimalFormatSymbols(locale)

    /** `1234.5` at exactly [decimals] places, no grouping — an axis label, a ladder step, a result. */
    fun fixed(value: Double, decimals: Int): String = String.format(locale, "%.${decimals}f", value)

    /** `1,234.50` grouped by thousands at exactly [decimals] places — a list price, a balance. */
    fun grouped(value: Double, decimals: Int): String {
        require(decimals in 0..8) { "decimals must be between 0 and 8" }
        val pattern = if (decimals == 0) "#,##0" else "#,##0.${"0".repeat(decimals)}"
        return DecimalFormat(pattern, symbols).format(value)
    }

    /** `12` — an order count, a bar count, anything whole. */
    fun integer(value: Long): String = String.format(locale, "%d", value)

    /** `62.50%` — a share or a change, the sign left to the caller. */
    fun percent(value: Double, decimals: Int): String = fixed(value, decimals) + "%"
}
