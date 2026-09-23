package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sheet behind the dot.
 *
 * The assertion this file exists for is the first one: the figure the sheet prints and the rule
 * that placed the dot have to be the same arithmetic, because a mark on a bar whose sheet says
 * nothing unusual happened is worse than no mark at all.
 */
class NotableBarReadingTest {

    /** A quiet series with one bar that is [spike] times as tall, at [at]. */
    private fun withSpike(size: Int, at: Int, spike: Double): CandleSeries = CandleSeries(
        (0 until size).map { index ->
            val base = 100.0
            val half = if (index == at) spike / 2 else 0.5
            Candle(
                t = 1_700_000_000L + index * 3_600L,
                o = base,
                h = base + half,
                l = base - half,
                c = if (index == at) base + half else base,
                v = 1.0,
            )
        },
    )

    private fun event(atSeconds: Long, title: String = "Rate decision") = ChartEvent(
        at = atSeconds,
        kind = EventKind.ECONOMIC,
        title = title,
        detail = null,
        importance = Importance.HIGH,
    )

    @Test
    fun `the dot and the sheet read the same arithmetic`() {
        val series = withSpike(size = 60, at = 40, spike = 6.0)
        val marked = NotableBars.of(series)
        assertTrue(marked.contains(40))
        // Six against a baseline of one, so six times — the same window, the same exclusion of the
        // bar from its own baseline, computed by the same object.
        assertEquals(6.0, NotableBars.ratioAt(series, 40)!!, 0.0001)
        assertEquals(6.0, NotableBarReadings.of(series, 40)!!.ratio!!, 0.0001)
    }

    @Test
    fun `a bar inside the first window has no ratio and the rule marks none either`() {
        val series = withSpike(size = 60, at = 5, spike = 9.0)
        assertFalse(NotableBars.of(series).contains(5))
        assertNull(NotableBars.ratioAt(series, 5))
        assertNull(NotableBarReadings.of(series, 5)!!.ratio)
    }

    @Test
    fun `an index the series does not hold is null rather than an exception`() {
        val series = withSpike(size = 30, at = 25, spike = 4.0)
        assertNull(NotableBarReadings.of(series, -1))
        assertNull(NotableBarReadings.of(series, 30))
    }

    @Test
    fun `the window is the bar's own, half open at the next bar's open`() {
        val series = withSpike(size = 60, at = 40, spike = 6.0)
        val reading = NotableBarReadings.of(series, 40)!!
        assertEquals(1_700_000_000L + 40 * 3_600L, reading.fromSeconds)
        assertEquals(1_700_000_000L + 41 * 3_600L, reading.toSeconds)
    }

    @Test
    fun `the last bar's window is one interval wide rather than empty`() {
        val series = withSpike(size = 60, at = 59, spike = 6.0)
        val reading = NotableBarReadings.of(series, 59)!!
        assertEquals(3_600L, reading.toSeconds - reading.fromSeconds)
    }

    @Test
    fun `only the events inside the window survive, in order`() {
        val series = withSpike(size = 60, at = 40, spike = 6.0)
        val open = 1_700_000_000L + 40 * 3_600L
        val reading = NotableBarReadings.of(
            series,
            40,
            events = listOf(
                event(open + 1_800, "inside, later"),
                event(open - 1, "the bar before"),
                event(open, "inside, on the open"),
                // Exactly the next bar's open: that bar's, not this one's. Half-open, the same rule
                // `ChartEvents.barOf` places every other event with.
                event(open + 3_600, "the bar after"),
            ),
        )!!
        assertEquals(listOf("inside, on the open", "inside, later"), reading.events.map(ChartEvent::title))
        assertTrue(reading.explained)
    }

    @Test
    fun `a bar nothing explains says so rather than reaching for the nearest headline`() {
        val series = withSpike(size = 60, at = 40, spike = 6.0)
        val open = 1_700_000_000L + 40 * 3_600L
        val reading = NotableBarReadings.of(series, 40, events = listOf(event(open - 7_200)))!!
        assertFalse(reading.explained)
        assertTrue(reading.events.isEmpty())
    }

    @Test
    fun `the move is open to close, and its sign is the bar's own`() {
        val series = CandleSeries(
            listOf(
                Candle(t = 1_700_000_000L, o = 100.0, h = 110.0, l = 90.0, c = 105.0, v = 1.0),
                Candle(t = 1_700_003_600L, o = 100.0, h = 110.0, l = 90.0, c = 95.0, v = 1.0),
            ),
        )
        val rose = NotableBarReadings.of(series, 0)!!
        assertEquals(5.0, rose.movePercent!!, 0.0001)
        assertTrue(rose.up)

        val fell = NotableBarReadings.of(series, 1)!!
        assertEquals(-5.0, fell.movePercent!!, 0.0001)
        assertFalse(fell.up)
    }

    @Test
    fun `a zero open has no percentage rather than an infinite one`() {
        val series = CandleSeries(
            listOf(Candle(t = 1_700_000_000L, o = 0.0, h = 1.0, l = 0.0, c = 1.0, v = 1.0)),
        )
        assertNull(NotableBarReadings.of(series, 0)!!.movePercent)
    }

    @Test
    fun `a doji reads as up, the way every terminal colours one`() {
        val series = CandleSeries(
            listOf(Candle(t = 1_700_000_000L, o = 100.0, h = 101.0, l = 99.0, c = 100.0, v = 1.0)),
        )
        assertTrue(NotableBarReadings.of(series, 0)!!.up)
    }
}
