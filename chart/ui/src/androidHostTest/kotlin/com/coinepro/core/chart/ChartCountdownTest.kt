package com.coinepro.core.chart

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bar-close countdown's arithmetic and its digits.
 *
 * Two things worth pinning. The format has to stay Latin on a Persian phone for the same reason
 * the price axis does — it is a market figure sitting in a column of them — and `String.format`
 * follows `Locale.getDefault()`, which on this app's default locale means Persian digits and a
 * Persian decimal separator.
 */
class ChartCountdownTest {

    @Test
    fun `under an hour is minutes and seconds`() {
        assertEquals("0:09", formatCountdown(9))
        assertEquals("1:00", formatCountdown(60))
        assertEquals("14:59", formatCountdown(899))
        assertEquals("59:59", formatCountdown(3_599))
    }

    @Test
    fun `an hour or more carries the hour`() {
        assertEquals("1:00:00", formatCountdown(3_600))
        assertEquals("3:59:59", formatCountdown(14_399))
        assertEquals("23:59:59", formatCountdown(86_399))
    }

    @Test
    fun `a day or more is a day count, because the seconds mean nothing there`() {
        // A weekly bar has days to run. "6d" is the whole of what a reader wants; "6:04:11:37"
        // would be a number nobody reads.
        assertEquals("1d", formatCountdown(86_400))
        assertEquals("2d", formatCountdown(2 * 86_400 + 5))
    }

    @Test
    fun `zero is a close that is due this instant, not an error`() {
        assertEquals("0:00", formatCountdown(0))
    }

    @Test
    fun `the digits are Latin whatever the device locale is`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale("fa", "IR"))
            assertEquals("14:05", formatCountdown(845))
            assertEquals("2:00:00", formatCountdown(7_200))
        } finally {
            Locale.setDefault(original)
        }
    }
}
