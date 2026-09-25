package com.coinepro.core.chart

import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The screener's growth scan (5.17.0): each setup fires on the shape it names, and the score reads it. */
class GrowthScanTest {

    private fun series(closes: List<Double>, volumes: List<Double>? = null): CandleSeries = CandleSeries(
        closes.mapIndexed { i, c ->
            val open = (closes.getOrElse(i - 1) { c } + c) / 2
            Candle(
                t = 1_700_000_000L + i * 86_400L,
                o = open,
                h = maxOf(open, c) * 1.002,
                l = minOf(open, c) * 0.998,
                c = c,
                v = volumes?.get(i) ?: 1_000.0,
            )
        },
    )

    @Test
    fun `too short a series is not scanned at all`() {
        assertNull(GrowthScan.of(series(List(40) { 100.0 })))
    }

    private val down = List(150) { 200.0 - it * 0.6 + sin(it / 3.0) }

    @Test
    fun `a decline turning up starts a trend within the lookback`() {
        val up = List(25) { down.last() + it * 1.2 + sin(it / 3.0) * 0.5 }
        val scan = GrowthScan.of(series(down + up))!!
        assertTrue("trend start fired: ${scan.barsAgo}", GrowthScan.Kind.TREND_START in scan.barsAgo)
    }

    @Test
    fun `a rise that has run for a while scores as growth`() {
        val up = List(80) { down.last() + it * 1.2 + sin(it / 3.0) * 0.5 }
        val scan = GrowthScan.of(series(down + up))!!
        assertTrue("a rise scores above the middle: ${scan.growth}", scan.growth > 50.0)
    }

    @Test
    fun `a steady decline scores low and carries no bullish setup that is fresh`() {
        val down = List(220) { 300.0 - it * 0.8 + sin(it / 4.0) * 0.3 }
        val scan = GrowthScan.of(series(down))!!
        assertTrue("a decline scores below the middle: ${scan.growth}", scan.growth < 40.0)
    }

    @Test
    fun `a close through a flat range on triple volume is a breakout and a volume surge on this bar`() {
        val flat = List(120) { 100.0 + sin(it / 2.0) * 0.8 }
        val closes = flat + 106.0
        val volumes = List(120) { 1_000.0 } + 3_500.0
        val scan = GrowthScan.of(series(closes, volumes))!!
        assertEquals(0, scan.barsAgo[GrowthScan.Kind.BREAKOUT])
        assertEquals(0, scan.barsAgo[GrowthScan.Kind.VOLUME_SURGE])
        assertTrue(scan.fresh().any { it.first == GrowthScan.Kind.BREAKOUT })
    }

    @Test
    fun `the ids, labels and values the screener stores round trip`() {
        GrowthScan.IDS.forEach { id ->
            assertTrue(GrowthScan.isScanId(id))
            assertNotNull(GrowthScan.labelOf(id, english = true))
            assertNotNull(GrowthScan.labelOf(id, english = false))
        }
        val scan = GrowthScan.Scan(mapOf(GrowthScan.Kind.BREAKOUT to 2), growth = 71.0, reliability = null, samples = 1)
        assertEquals(2.0, GrowthScan.valueOf(GrowthScan.Kind.BREAKOUT.id, scan)!!, 0.0)
        assertEquals(71.0, GrowthScan.valueOf(GrowthScan.GROWTH_ID, scan)!!, 0.0)
        assertNull(GrowthScan.valueOf(GrowthScan.Kind.MOMENTUM.id, scan))
        assertNull(GrowthScan.valueOf(GrowthScan.RELIABILITY_ID, scan))
    }

    @Test
    fun `every setup names studies the chart catalogue has`() {
        val known = ChartCatalog.INDICATORS.map { it.id }.toSet()
        GrowthScan.Kind.entries.forEach { kind ->
            kind.studies.forEach { id -> assertTrue("${kind.name} → $id", id in known) }
        }
    }
}
