package com.coinepro.core.signals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignalStrategyPersianTest {

    @Test
    fun `the names a reader actually meets are Persian`() {
        assertEquals("واکنش به سقف/کف رِنج", SignalStrategyPersian.of("Range rejection"))
        assertEquals("ادامه‌ی روند", SignalStrategyPersian.of("Trend continuation"))
        assertEquals("شکست و پولبک", SignalStrategyPersian.of("Breakout retest"))
    }

    @Test
    fun `the three spellings the backends use are one entry`() {
        // One sends `Range rejection`, the other `range_rejection`, and an analyst typing into a web
        // panel sends `Range Rejection`. Three table entries would have drifted apart.
        val expected = "واکنش به سقف/کف رِنج"
        assertEquals(expected, SignalStrategyPersian.of("range_rejection"))
        assertEquals(expected, SignalStrategyPersian.of("Range Rejection"))
        assertEquals(expected, SignalStrategyPersian.of("  RANGE-REJECTION  "))
    }

    @Test
    fun `a name nobody wrote down comes back as the server said it`() {
        // The honest failure. A reader who sees English knows what the server sent; a reader who
        // sees a wrong Persian phrase has been told something about their money that is not true.
        assertEquals("Ichimoku cloud break", SignalStrategyPersian.of("Ichimoku cloud break"))
    }

    @Test
    fun `no strategy is no line rather than an empty one`() {
        assertNull(SignalStrategyPersian.of(null))
        assertNull(SignalStrategyPersian.of("   "))
    }
}
