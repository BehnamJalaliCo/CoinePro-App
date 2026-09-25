package com.coinepro.core.marketdata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tick charts (5.17.0): the interval kind, and the fold that makes a bar of every N trades. */
class TickIntervalTest {

    @Test
    fun `every offered tick size round-trips and is never asked of a candle venue`() {
        TICK_KEYS.forEach { count ->
            val interval = ChartInterval.Ticks(count)
            assertEquals("${count}T", interval.wire)
            assertEquals(interval, ChartInterval.of(interval.wire))
            assertNull(sourceTimeframeFor(interval))
        }
        assertEquals(ChartInterval.Seconds(1), ChartInterval.of("1S"))
        assertEquals(ChartInterval.Seconds(5), ChartInterval.of("5S"))
        assertNull(ticksOf("7T"))
        assertNull(ticksOf("T"))
        assertEquals("۱۰۰ تیک", ChartInterval.Ticks(100).label)
    }

    private fun ticks(vararg prices: Double) = prices.mapIndexed { i, p -> Tick(tMs = 1_700_000_000_000L + i * 400L, price = p, volume = 1.0) }

    @Test
    fun `a bar is every N trades, and the last one says how full it is`() {
        val (bars, filled) = TickBars.fold(ticks(10.0, 12.0, 9.0, 11.0, 13.0), 2)
        assertEquals(3, bars.size)
        assertEquals(1, filled)
        assertEquals(10.0, bars[0].o, 0.0)
        assertEquals(12.0, bars[0].h, 0.0)
        assertEquals(12.0, bars[0].c, 0.0)
        assertTrue(bars[0].closed)
        assertEquals(13.0, bars[2].c, 0.0)
        assertTrue(!bars[2].closed)
    }

    @Test
    fun `live trades fill the forming bar before opening the next`() {
        val (bars, filled) = TickBars.fold(ticks(10.0, 12.0, 9.0), 2)
        val (more, fill) = TickBars.append(bars, filled, ticks(8.0, 7.0), 2)
        assertEquals(3, more.size)
        assertEquals(8.0, more[1].l, 0.0)
        assertEquals(8.0, more[1].c, 0.0)
        assertTrue(more[1].closed)
        assertEquals(7.0, more[2].o, 0.0)
        assertEquals(1, fill)
    }
}
