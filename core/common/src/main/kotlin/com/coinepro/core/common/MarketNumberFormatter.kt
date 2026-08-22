package com.coinepro.core.common

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object MarketNumberFormatter {
    fun price(value: Double, decimals: Int = 2): String {
        require(decimals in 0..8) { "decimals must be between 0 and 8" }
        val pattern = if (decimals == 0) "#,##0" else "#,##0.${"0".repeat(decimals)}"
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(value)
    }

    fun signedPercent(value: Double): String {
        val sign = if (value > 0) "+" else ""
        return "$sign${DecimalFormat("0.00", DecimalFormatSymbols(Locale.US)).format(value)}%"
    }
}
