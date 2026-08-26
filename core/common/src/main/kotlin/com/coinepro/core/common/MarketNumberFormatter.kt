package com.coinepro.core.common

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formats market values for display.
 *
 * [Locale.US] is fixed on purpose rather than following the device: prices, quantities and
 * percentages must stay Latin-digit and dot-decimal so a trader can compare them against MetaTrader,
 * Binance or TradingView without mentally converting. Output is isolated with [BidiText] so those
 * Latin runs survive inside right-to-left copy.
 */
object MarketNumberFormatter {
    fun price(value: Double, decimals: Int = 2): String {
        require(decimals in 0..8) { "decimals must be between 0 and 8" }
        val pattern = if (decimals == 0) "#,##0" else "#,##0.${"0".repeat(decimals)}"
        return BidiText.isolateLtr(DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(value))
    }

    /**
     * A price with the number of decimals its own magnitude needs.
     *
     * Two decimals is right for anything priced in whole units and wrong for everything else. A
     * crypto list holds both at once: BTC at 64,182.40 and XRP at 0.5241 and SHIB at 0.00002418.
     * Formatted at a fixed two, the second reads 0.52 — a rounding that hides a fifth of a percent
     * — and the third reads 0.00, which is not a rounding but a claim that the asset is worthless.
     *
     * The steps below are the ones the exchanges themselves use, so a reader comparing this screen
     * against LBank or Binance sees the same digits rather than a number that looks disagreed with.
     *
     * Magnitude is taken from the absolute value: a price is never negative, but a difference
     * passed here would be, and picking decimals from a signed number would give −0.5241 two.
     */
    fun priceAuto(value: Double): String = price(value, decimalsFor(kotlin.math.abs(value)))

    private fun decimalsFor(magnitude: Double): Int = when {
        // Exactly zero is not a very small number, it is an absent one, and 0.00000000 reads as a
        // precision claim about nothing. Two decimals says "no price" the way the rest of the app
        // does.
        magnitude == 0.0 -> 2
        magnitude >= 1.0 -> 2
        magnitude >= 0.01 -> 4
        magnitude >= 0.0001 -> 6
        // Eight is the floor rather than a step: it is the most `price` accepts, and below it the
        // exchanges stop quoting a unit price and start quoting per thousand.
        else -> 8
    }

    fun signedPercent(value: Double): String {
        val sign = if (value > 0) "+" else ""
        val formatted = DecimalFormat("0.00", DecimalFormatSymbols(Locale.US)).format(value)
        return BidiText.isolateLtr("$sign$formatted%")
    }

    /**
     * An amount with its currency symbol, isolated as one run.
     *
     * The symbol has to be inside the isolate, not concatenated onto an already-isolated number:
     * `"$" + price(v)` puts the sign outside the left-to-right run, so a right-to-left paragraph
     * reorders it to the far end and `$12,480.35` renders as `12,480.35$`.
     */
    fun money(
        value: Double,
        currencySymbol: String = "$",
        decimals: Int = 2,
        signed: Boolean = false,
    ): String {
        require(decimals in 0..8) { "decimals must be between 0 and 8" }
        val pattern = if (decimals == 0) "#,##0" else "#,##0.${"0".repeat(decimals)}"
        val magnitude = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))
            .format(kotlin.math.abs(value))
        val sign = when {
            !signed -> if (value < 0) "-" else ""
            value < 0 -> "-"
            else -> "+"
        }
        return BidiText.isolateLtr("$sign$currencySymbol$magnitude")
    }

    /**
     * An amount held with its unit — `0.1482 BTC`.
     *
     * Isolated as one run for the same reason as [money]: with the unit outside the isolate, a
     * right-to-left paragraph moves it in front of the number.
     */
    fun quantity(value: Double, unit: String, decimals: Int = 4): String {
        require(decimals in 0..8) { "decimals must be between 0 and 8" }
        val pattern = if (decimals == 0) "#,##0" else "#,##0.${"0".repeat(decimals)}"
        val formatted = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(value)
        return BidiText.isolateLtr("$formatted $unit")
    }
}
