package com.coinepro.core.chart

import kotlin.math.abs

/**
 * How many decimals a price on this chart deserves.
 *
 * Taken from the magnitude rather than fixed, for the same reason the search rows do it: gold at
 * 2,643.18 and a memecoin at 0.000018 cannot share a format, and rounding either to the other's
 * precision makes the number wrong rather than merely ugly.
 */
fun decimalsFor(price: Double): Int {
    val magnitude = abs(price)
    return when {
        magnitude >= 1_000 -> 1
        magnitude >= 1 -> 2
        magnitude >= 0.01 -> 4
        else -> 6
    }
}

/**
 * Latin digits, always.
 *
 * Through [formatFixed], the platform seam the digit policy lives behind. Without it `String.format` follows
 * the device locale, and on a Persian phone — which is this app's default — a price comes out as
 * «۲٬۵۹۲٫۶»: Persian digits and a Persian decimal separator, on the axis of a chart. Market figures
 * stay Latin and comparable down a column; only prose counts are written in Persian digits.
 */
fun formatPrice(value: Double, decimals: Int): String = formatFixed(value, decimals)
