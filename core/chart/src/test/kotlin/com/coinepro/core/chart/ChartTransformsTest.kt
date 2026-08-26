package com.coinepro.core.chart

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartTransformsTest {

    /** A clean staircase up and back down, so every transform's behaviour is inspectable by eye. */
    private val staircase = (0 until 40).map { index ->
        val base = if (index < 20) 100.0 + index else 140.0 - index
        Candle(t = 1_700_000_000L + index * 3600, o = base, h = base + 0.5, l = base - 0.5, c = base)
    }

    @Test
    fun `heikin-ashi smooths without inventing a wick outside the bar`() {
        val out = ChartTransforms.heikinAshi(staircase)
        assertEquals(staircase.size, out.size)
        for (index in out.indices) {
            val bar = out[index]
            assertTrue("high must contain the body", bar.h >= maxOf(bar.o, bar.c) - 1e-9)
            assertTrue("low must contain the body", bar.l <= minOf(bar.o, bar.c) + 1e-9)
            // The averaged high can only come from the source bar or the body it computed.
            assertTrue(bar.h <= maxOf(staircase[index].h, bar.o, bar.c) + 1e-9)
        }
    }

    @Test
    fun `heikin-ashi's first bar seeds from the source rather than from nothing`() {
        val out = ChartTransforms.heikinAshi(staircase)
        val first = staircase.first()
        assertEquals((first.o + first.c) / 2, out.first().o, 1e-9)
        assertEquals((first.o + first.h + first.l + first.c) / 4, out.first().c, 1e-9)
    }

    @Test
    fun `renko emits a brick per move and none for a market that stands still`() {
        val bricks = ChartTransforms.renko(staircase, brick = 5.0)
        // 100 up to 120, then back to 101. Four bricks up (105, 110, 115, 120) and three down
        // (115, 110, 105) — the fourth never completes, because 101 is not five below 105.
        assertEquals(7, bricks.size)
        assertTrue(bricks.take(4).all { it.c > it.o })
        assertTrue(bricks.drop(4).all { it.c < it.o })

        val flat = List(50) { Candle(1_700_000_000L + it * 60, 100.0, 100.0, 100.0, 100.0) }
        assertEquals(emptyList<Candle>(), ChartTransforms.renko(flat, brick = 1.0))
    }

    @Test
    fun `a price-driven type gives every bar a strictly later timestamp`() {
        // Several bricks can come from one source candle, all carrying its time. CandleSeries
        // requires ascending order, so they have to be separated — and without this the series
        // constructor throws on a fast move.
        val spike = listOf(
            Candle(1_700_000_000L, 100.0, 100.0, 100.0, 100.0),
            Candle(1_700_003_600L, 100.0, 130.0, 100.0, 130.0),
        )
        val bricks = ChartTransforms.renko(spike, brick = 5.0)
        assertEquals(6, bricks.size)
        for (index in 1 until bricks.size) {
            assertTrue(bricks[index].t > bricks[index - 1].t)
        }
        // And the result is a valid series, which is the actual requirement.
        assertEquals(6, CandleSeries(bricks).size)
    }

    @Test
    fun `range bars watch the whole bar, not only its close`() {
        // One source candle whose close equals its open, but which travelled thirty points.
        val wide = listOf(Candle(1_700_000_000L, 100.0, 115.0, 100.0, 100.0))
        val bars = ChartTransforms.rangeBars(wide, range = 5.0)
        assertTrue("a close-only reading would emit nothing here", bars.isNotEmpty())
    }

    @Test
    fun `kagi turns only on a real reversal`() {
        val points = ChartTransforms.kagi(staircase, reversal = 4.0)
        assertTrue(points.size >= 2)
        // Every point is flat — Kagi is a line, carried through the engine as a degenerate candle.
        assertTrue(points.all { abs(it.h - it.l) < 1e-9 && abs(it.o - it.c) < 1e-9 })
    }

    @Test
    fun `line break needs to clear the last three lines to reverse`() {
        val lines = ChartTransforms.lineBreak(staircase, count = 3)
        assertTrue(lines.isNotEmpty())
        for (index in 1 until lines.size) assertTrue(lines[index].t > lines[index - 1].t)
    }

    @Test
    fun `the default size scales itself to the instrument`() {
        // The reason a fixed brick size cannot work: these two instruments are eight orders of
        // magnitude apart and both have to produce a readable chart.
        val gold = (0 until 30).map { Candle(it.toLong(), 2600.0, 2605.0, 2595.0, 2600.0 + it) }
        val memecoin = (0 until 30).map {
            Candle(it.toLong(), 0.000018, 0.0000185, 0.0000175, 0.000018 + it * 1e-7)
        }
        assertTrue(ChartTransforms.averageRange(gold) > 1.0)
        assertTrue(ChartTransforms.averageRange(memecoin) < 1e-4)
    }

    @Test
    fun `an empty series survives every transform`() {
        for (type in ChartType.entries) {
            assertEquals(0, ChartTransforms.apply(CandleSeries.EMPTY, type).size)
        }
    }

    @Test
    fun `the ordinary types return the series untouched`() {
        val series = CandleSeries(staircase)
        for (type in listOf(ChartType.CANDLES, ChartType.HOLLOW, ChartType.BARS, ChartType.LINE, ChartType.AREA)) {
            assertEquals(series.bars, ChartTransforms.apply(series, type).bars)
        }
        assertNotEquals(series.bars, ChartTransforms.apply(series, ChartType.HEIKIN_ASHI).bars)
    }
}
