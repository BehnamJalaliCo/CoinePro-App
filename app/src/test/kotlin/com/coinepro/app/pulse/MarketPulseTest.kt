package com.coinepro.app.pulse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 5.27.0: the owner's «متعادل» — the numbers the market notifications are allowed to fire on. */
class MarketPulseTest {

    private val minute = 60_000L

    @Test
    fun `each kind of market has its own threshold`() {
        assertEquals(3.0, MarketPulse.thresholdFor("BTCUSDT"), 0.0)
        assertEquals(5.0, MarketPulse.thresholdFor("SOLUSDT"), 0.0)
        assertEquals(1.5, MarketPulse.thresholdFor("XAUUSD"), 0.0)
        assertEquals(0.7, MarketPulse.thresholdFor("EURUSD"), 0.0)
    }

    @Test
    fun `a rise past the threshold inside the window is a move, and the window starts again`() {
        var samples = emptyList<PulseSample>()
        listOf(100.0, 101.0, 102.0).forEachIndexed { index, price ->
            val (kept, move) = MarketPulse.observe("BTCUSDT", samples, index * 15 * minute, price, 3.0)
            assertNull(move)
            samples = kept
        }
        val (kept, move) = MarketPulse.observe("BTCUSDT", samples, 45 * minute, 103.5, 3.0)
        assertNotNull(move)
        assertEquals(3.5, move!!.percent, 1e-9)
        assertEquals(100.0, move.fromPrice, 0.0)
        assertEquals(45 * minute, move.windowMillis)
        assertEquals("the same climb is not announced twice", 1, kept.size)
    }

    @Test
    fun `a fall is measured from the window's high`() {
        val samples = listOf(PulseSample(0, 100.0), PulseSample(15 * minute, 104.0))
        val (_, move) = MarketPulse.observe("BTCUSDT", samples, 30 * minute, 100.5, 3.0)
        assertNotNull(move)
        assertTrue(move!!.percent < -3.0)
        assertEquals(104.0, move.fromPrice, 0.0)
    }

    @Test
    fun `a move slower than four hours is not news`() {
        val samples = listOf(PulseSample(0, 100.0))
        val (kept, move) = MarketPulse.observe("BTCUSDT", samples, MarketPulse.WINDOW_MILLIS + minute, 104.0, 3.0)
        assertNull(move)
        assertEquals(1, kept.size)
    }

    @Test
    fun `the night is quiet from eleven to eight`() {
        assertTrue(MarketPulse.quiet(23 * 60))
        assertTrue(MarketPulse.quiet(3 * 60))
        assertTrue(MarketPulse.quiet(7 * 60 + 59))
        assertFalse(MarketPulse.quiet(8 * 60))
        assertFalse(MarketPulse.quiet(22 * 60 + 59))
    }

    @Test
    fun `the summary goes once a day, from nine`() {
        assertFalse(MarketPulse.digestDue(8 * 60 + 59, today = 10, lastDigestDay = 9))
        assertTrue(MarketPulse.digestDue(9 * 60, today = 10, lastDigestDay = 9))
        assertFalse(MarketPulse.digestDue(13 * 60, today = 10, lastDigestDay = 10))
        assertTrue(MarketPulse.digestDue(13 * 60, today = 10, lastDigestDay = null))
    }
}
