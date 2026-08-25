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
