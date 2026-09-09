package com.coinepro.core.chart

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chart's own number formatting, which is not the app's.
 *
 * `MarketNumberFormatter` handles prices inside text, where the app controls the string. The chart
 * draws its own axis labels straight onto a Canvas and cannot reuse it, so the same rule has to be
 * enforced here separately — and it was broken the first time the chart rendered.
 */
class ChartFormattingTest {

    @Test
    fun `axis labels are Latin whatever the device locale is`() {
        // The bug this pins: String.format follows Locale.getDefault(), so on a Persian phone —
        // which is this app's default — the price axis came out as «۲٬۵۹۲٫۶». Persian digits and a
        // Persian decimal separator, on the y axis of a trading chart.
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale("fa", "IR"))
            assertEquals("2592.6", formatPrice(2592.6, 1))
            // 2026-03-04 09:45 UTC. A bar's open time is UTC, so the label is too.
            assertEquals("09:45", formatTime(1_772_617_500L))
            for (locale in listOf(Locale("ar", "EG"), Locale("hi", "IN"), Locale.GERMANY)) {
                Locale.setDefault(locale)
                assertTrue(
                    "digits must stay Latin under $locale",
                    formatPrice(1234.5, 2).all { it.isDigit() && it in '0'..'9' || it == '.' },
                )
            }
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `precision follows the instrument, not a fixed two places`() {
        // Gold and a memecoin cannot share a format. Rounding either to the other's precision makes
        // the number wrong, not merely ugly.
        assertEquals(1, decimalsFor(2_643.18))
        assertEquals(2, decimalsFor(91.2))
        assertEquals(4, decimalsFor(0.4712))
        assertEquals(6, decimalsFor(0.000018))
        // And a negative price — a spread, a P&L axis — is judged on magnitude.
        assertEquals(6, decimalsFor(-0.000018))
    }
}
