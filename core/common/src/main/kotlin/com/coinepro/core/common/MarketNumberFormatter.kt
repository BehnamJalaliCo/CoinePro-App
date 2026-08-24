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
}
