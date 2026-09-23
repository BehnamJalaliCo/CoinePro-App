package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stamp-sized window's rules.
 *
 * Two assertions this file exists for. The aspect is **clamped**, because Android refuses a ratio
 * outside its own bounds by throwing — a very tall window would otherwise crash the app on the way
 * into the mode rather than degrade. And the countdown is **null past the close** rather than zero
 * or negative, which is the bug the live tag once had: a countdown is `close − now`, and a bar
 * minutes old has a negative one.
 */
class ChartWatchTest {

    private fun snapshot(price: Double = 100.0, change: Double? = 1.0) = WatchSnapshot(
        symbol = "BTCUSDT",
        intervalWire = "1h",
        price = price,
        changePercent = change,
    )

    @Test
    fun `a sane window keeps its own ratio`() {
        assertEquals(16.0 / 9.0, ChartWatch.aspectOf(1600, 900), 0.0001)
    }

    @Test
    fun `a window too tall for the system is clamped rather than refused`() {
        // The crash this guards: `setAspectRatio` throws outside 1:2.39 … 2.39:1.
        val aspect = ChartWatch.aspectOf(100, 1_000)
        assertEquals(ChartWatch.MIN_ASPECT, aspect, 0.0001)
        assertTrue(aspect >= ChartWatch.MIN_ASPECT)
    }

    @Test
    fun `a window too wide is clamped the other way`() {
        assertEquals(ChartWatch.MAX_ASPECT, ChartWatch.aspectOf(10_000, 100), 0.0001)
    }

    @Test
    fun `a window with no size yet falls back rather than dividing by zero`() {
        // The mode can be entered before anything is laid out, and a zero here would become a
        // crash at exactly the moment the reader asked for the feature.
        assertEquals(ChartWatch.DEFAULT_ASPECT, ChartWatch.aspectOf(0, 0), 0.0001)
        assertEquals(ChartWatch.DEFAULT_ASPECT, ChartWatch.aspectOf(-4, 9), 0.0001)
    }

    @Test
    fun `the first snapshot is always published`() {
        // A window that waited a second before drawing anything would open empty.
        assertTrue(ChartWatch.shouldPublish(null, snapshot(), null, 0L))
        assertTrue(ChartWatch.shouldPublish(null, snapshot(), 5_000L, 5_000L))
    }

    @Test
    fun `an unchanged snapshot is not republished`() {
        val same = snapshot()
        assertFalse(ChartWatch.shouldPublish(same, same, 0L, 100_000L))
    }

    @Test
    fun `a change inside the window is held`() {
        assertFalse(ChartWatch.shouldPublish(snapshot(), snapshot(price = 101.0), 1_000L, 1_500L))
    }

    @Test
    fun `a change after the window goes through`() {
        assertTrue(ChartWatch.shouldPublish(snapshot(), snapshot(price = 101.0), 1_000L, 2_000L))
    }

    @Test
    fun `exactly at the window goes through`() {
        assertTrue(
            ChartWatch.shouldPublish(
                snapshot(),
                snapshot(price = 101.0),
                1_000L,
                1_000L + ChartWatch.MIN_PUBLISH_MILLIS,
            ),
        )
    }

    @Test
    fun `no bar close is no countdown`() {
        assertNull(ChartWatch.countdownSeconds(null, 1_700_000_000L))
    }

    @Test
    fun `a bar with time left counts down`() {
        assertEquals(90, ChartWatch.countdownSeconds(1_700_000_090L, 1_700_000_000L))
    }

    @Test
    fun `a bar whose close has passed has no countdown at all`() {
        // Not zero and not a negative number. The next snapshot carries the next bar; until then
        // the window draws no line rather than a stuck «0:00» or a minus sign.
        assertNull(ChartWatch.countdownSeconds(1_700_000_000L, 1_700_000_000L))
        assertNull(ChartWatch.countdownSeconds(1_699_999_000L, 1_700_000_000L))
    }

    @Test
    fun `a close a month out is a date rather than a countdown`() {
        assertNull(ChartWatch.countdownSeconds(1_700_000_000L + 30L * 86_400L, 1_700_000_000L))
    }
}
