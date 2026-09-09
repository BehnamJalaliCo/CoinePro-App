package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartEventsTest {

    /** Four hourly bars, opening at 1000, 2000, 3000 and 4000 — a thousand seconds apart. */
    private val series = CandleSeries(
        (0 until 4).map { index ->
            val time = 1_000L + index * 1_000L
            Candle(t = time, o = 1.0, h = 2.0, l = 0.5, c = 1.5, v = 10.0)
        },
    )

    private fun event(
        at: Long,
        importance: Importance = Importance.LOW,
        kind: EventKind = EventKind.NEWS,
        title: String = "رویداد",
    ) = ChartEvent(at = at, kind = kind, title = title, detail = null, importance = importance)

    @Test
    fun `three events inside one bar collapse into a single mark carrying all of them`() {
        val events = listOf(
            event(2_100, Importance.LOW),
            event(2_900, Importance.MEDIUM, EventKind.EARNINGS),
            event(2_500, Importance.HIGH, EventKind.ECONOMIC),
        )

        val marks = ChartEvents.place(events, series, fromIndex = 0, toIndex = 3)

        assertEquals(1, marks.size)
        val mark = marks.single()
        assertEquals(1, mark.barIndex)
        assertEquals(3, mark.events.size)
        assertTrue(mark.isCluster)
        // The strongest thing in the bar is what the glyph draws as; the rest are inside it.
        assertEquals(Importance.HIGH, mark.importance)
        assertEquals(EventKind.ECONOMIC, mark.kind)
        // Earliest first, so the sheet reads in the order the morning happened.
        assertEquals(listOf(2_100L, 2_500L, 2_900L), mark.events.map(ChartEvent::at))
    }

    @Test
    fun `events in different bars stay separate marks, ordered by bar`() {
        val events = listOf(event(3_500), event(1_200), event(2_000))

        val marks = ChartEvents.place(events, series, fromIndex = 0, toIndex = 3)

        assertEquals(listOf(0, 1, 2), marks.map(EventMark::barIndex))
        assertTrue(marks.none(EventMark::isCluster))
    }

    @Test
    fun `an event past the visible range is dropped rather than clamped to the edge`() {
        val events = listOf(event(3_500, Importance.HIGH))

        // Bar 3 is off-screen. A mark on bar 1 would claim the news broke at a time it did not.
        assertTrue(ChartEvents.place(events, series, fromIndex = 0, toIndex = 1).isEmpty())
        assertEquals(2, ChartEvents.place(events, series, 0, 3).single().barIndex)
    }

    @Test
    fun `an event before the first bar of the whole series is dropped`() {
        assertTrue(ChartEvents.place(listOf(event(500)), series, 0, 3).isEmpty())
        assertNull(ChartEvents.barOf(series, 999))
    }

    @Test
    fun `an event past the end of the last bar's own interval is dropped`() {
        // The last bar is given the width of the gap before it, so it covers 4000 up to 5000.
        assertEquals(3, ChartEvents.barOf(series, 4_999))
        assertNull(ChartEvents.barOf(series, 5_000))
        assertTrue(ChartEvents.place(listOf(event(6_000)), series, 0, 3).isEmpty())
    }

    @Test
    fun `a bar's interval is half open, so an event on a bar's open belongs to that bar`() {
        assertEquals(0, ChartEvents.barOf(series, 1_000))
        assertEquals(0, ChartEvents.barOf(series, 1_999))
        assertEquals(1, ChartEvents.barOf(series, 2_000))
    }

    @Test
    fun `a reversed window is read as the same window rather than as nothing`() {
        val marks = ChartEvents.place(listOf(event(2_400)), series, fromIndex = 3, toIndex = 0)

        assertEquals(1, marks.single().barIndex)
    }

    @Test
    fun `a window past the end of the series is clamped without clamping any event`() {
        val events = listOf(event(1_500), event(3_500))

        val marks = ChartEvents.place(events, series, fromIndex = 0, toIndex = 99)

        assertEquals(listOf(0, 2), marks.map(EventMark::barIndex))
    }

    @Test
    fun `an empty series and an empty event list both place nothing`() {
        assertTrue(ChartEvents.place(listOf(event(2_100)), CandleSeries.EMPTY, 0, 0).isEmpty())
        assertTrue(ChartEvents.place(emptyList(), series, 0, 3).isEmpty())
        assertNull(ChartEvents.barOf(CandleSeries.EMPTY, 2_100))
    }

    @Test
    fun `a single-bar series still holds an event that lands on its open`() {
        val one = CandleSeries(listOf(Candle(t = 1_000, o = 1.0, h = 2.0, l = 0.5, c = 1.5)))

        assertEquals(0, ChartEvents.barOf(one, 1_000))
        assertNull(ChartEvents.barOf(one, 1_001))
    }
}
