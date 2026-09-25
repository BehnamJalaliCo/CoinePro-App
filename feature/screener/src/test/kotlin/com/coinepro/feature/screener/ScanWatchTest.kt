package com.coinepro.feature.screener

import com.coinepro.core.chart.GrowthScan
import com.coinepro.feature.screener.model.ScanWatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The growth scan's background alerts (5.17.0): the rule, the newcomers, and the stored shape. */
class ScanWatchTest {

    private val watch = ScanWatch(
        id = "w1",
        scanIds = setOf(GrowthScan.Kind.BREAKOUT.id),
        withinBars = 3,
        minGrowth = 60.0,
        timeframe = "H4",
        symbols = listOf("BTCUSDT", "ETHUSDT"),
        known = setOf("BTCUSDT"),
    )

    private fun scan(growth: Double, breakoutAgo: Int?) = GrowthScan.Scan(
        barsAgo = breakoutAgo?.let { mapOf(GrowthScan.Kind.BREAKOUT to it) } ?: emptyMap(),
        growth = growth,
        reliability = null,
        samples = 0,
    )

    @Test
    fun `a watch matches the same rule the screen applies`() {
        assertTrue(watch.matches(scan(70.0, 2)))
        assertFalse("too old", watch.matches(scan(70.0, 5)))
        assertFalse("too weak", watch.matches(scan(50.0, 1)))
        assertFalse("no setup", watch.matches(scan(90.0, null)))
    }

    @Test
    fun `only markets that were not in the scan last time are announced`() {
        assertEquals(listOf("ETHUSDT"), watch.entrants(setOf("BTCUSDT", "ETHUSDT")))
        assertTrue(watch.entrants(setOf("BTCUSDT")).isEmpty())
    }

    @Test
    fun `watches survive the codec`() {
        val other = watch.copy(id = "w2", minGrowth = null, scanIds = emptySet(), known = emptySet())
        assertEquals(listOf(watch, other), ScanWatchCodec.decodeAll(ScanWatchCodec.encodeAll(listOf(watch, other))))
    }
}
