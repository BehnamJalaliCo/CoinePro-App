package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The kind filter, the stored form of it, and the hit test.
 *
 * Placement itself is covered by [ChartEventsTest]; what is here is everything that decides which
 * events reach placement at all, and what happens to a finger afterwards.
 */
class ChartEventVisibilityTest {

    /** Four bars a thousand seconds apart, opening at 1000. */
    private val series = CandleSeries(
        (0 until 4).map { index ->
            Candle(t = 1_000L + index * 1_000L, o = 1.0, h = 2.0, l = 0.5, c = 1.5, v = 10.0)
        },
    )

    private fun event(
        at: Long,
        kind: EventKind = EventKind.NEWS,
        importance: Importance = Importance.LOW,
    ) = ChartEvent(at = at, kind = kind, title = "رویداد", detail = null, importance = importance)

    @Test
    fun `an event of a kind the reader switched off never becomes a mark`() {
        val events = listOf(event(1_200, EventKind.ECONOMIC, Importance.HIGH))

        val marks = ChartEvents.place(events, series, 0, 3, EventVisibility(setOf(EventKind.NEWS)))

        assertTrue(marks.isEmpty())
    }

    @Test
    fun `a bar holding a hidden event and a shown one draws as the shown one alone`() {
        // The hidden release is the louder of the two. If the filter ran after bucketing, the mark
        // would draw red for an event the settings say is not on the chart.
        val events = listOf(
            event(1_100, EventKind.ECONOMIC, Importance.HIGH),
            event(1_400, EventKind.NEWS, Importance.LOW),
        )

        val mark = ChartEvents.place(events, series, 0, 3, EventVisibility(setOf(EventKind.NEWS))).single()

        assertEquals(1, mark.events.size)
        assertEquals(EventKind.NEWS, mark.kind)
        assertEquals(Importance.LOW, mark.importance)
        assertFalse(mark.isCluster)
    }

    @Test
    fun `the default filter draws both kinds a backend actually serves`() {
        // It used to be news alone, which left the economic calendar — the one source that is
        // genuinely there and dated to the second — switched off for every reader who never opened
        // the professional studio, which on a phone was every reader.
        val events = listOf(event(1_200, EventKind.NEWS), event(2_200, EventKind.ECONOMIC))

        val shown = ChartEvents.place(events, series, 0, 3, EventVisibility.Default)

        assertEquals(listOf(0, 1), shown.map(EventMark::barIndex))
    }

    @Test
    fun `the default leaves off the three kinds no feed here publishes`() {
        // Switching on a kind nothing serves would change nothing on the axis and teach a reader
        // that the switches do not work.
        val events = listOf(
            event(1_200, EventKind.EARNINGS),
            event(2_200, EventKind.DIVIDEND),
            event(3_200, EventKind.SPLIT),
        )

        assertTrue(ChartEvents.place(events, series, 0, 3, EventVisibility.Default).isEmpty())
    }

    @Test
    fun `a kind switched off is not drawn until it is switched back on`() {
        val events = listOf(event(1_200, EventKind.NEWS), event(2_200, EventKind.ECONOMIC))
        val quiet = EventVisibility.Default.with(EventKind.ECONOMIC, false)

        val hidden = ChartEvents.place(events, series, 0, 3, quiet)
        assertEquals(listOf(0), hidden.map(EventMark::barIndex))

        val shown = ChartEvents.place(events, series, 0, 3, quiet.with(EventKind.ECONOMIC, true))
        assertEquals(listOf(0, 1), shown.map(EventMark::barIndex))
    }

    @Test
    fun `a filter with every kind off places nothing at all`() {
        val events = listOf(event(1_200), event(2_200, EventKind.ECONOMIC))

        assertTrue(ChartEvents.place(events, series, 0, 3, EventVisibility.Nothing).isEmpty())
    }

    @Test
    fun `an empty filter survives a restart rather than turning news back on`() {
        val stored = EventVisibility.Nothing.encode()

        assertEquals("", stored)
        assertEquals(EventVisibility.Nothing, EventVisibility.decode(stored))
        // Never stored is a different answer from stored-as-empty, and only the first is the default.
        assertEquals(EventVisibility.Default, EventVisibility.decode(null))
    }

    @Test
    fun `a stored filter comes back with exactly the kinds it went in with`() {
        val chosen = EventVisibility(setOf(EventKind.NEWS, EventKind.SPLIT))

        assertEquals(chosen, EventVisibility.decode(chosen.encode()))
    }

    @Test
    fun `a kind name a later build no longer knows is dropped rather than refusing the whole filter`() {
        val decoded = EventVisibility.decode("NEWS,BUYBACK, ECONOMIC ")

        assertEquals(setOf(EventKind.NEWS, EventKind.ECONOMIC), decoded.kinds)
    }

    @Test
    fun `switching a kind off leaves the others alone`() {
        val both = EventVisibility(setOf(EventKind.NEWS, EventKind.ECONOMIC))

        val off = both.with(EventKind.ECONOMIC, false)

        assertTrue(off.isOn(EventKind.NEWS))
        assertFalse(off.isOn(EventKind.ECONOMIC))
        assertFalse(off.isNothing)
    }

    @Test
    fun `a touch between two marks opens the nearer one`() {
        val marks = ChartEvents.place(listOf(event(1_200), event(2_200)), series, 0, 3)
        // Bar n sits at 100n pixels, so the two marks are at 0 and 100.
        val xOf: (Int) -> Float = { index -> index * 100f }

        val hit = ChartEvents.markAt(marks, xPixels = 60f, radiusPixels = 70f, xOf = xOf)

        assertSame(marks[1], hit)
    }

    @Test
    fun `a touch further than the radius from every mark opens nothing`() {
        val marks = ChartEvents.place(listOf(event(1_200)), series, 0, 3)
        val xOf: (Int) -> Float = { index -> index * 100f }

        assertNull(ChartEvents.markAt(marks, xPixels = 300f, radiusPixels = 24f, xOf = xOf))
        assertNull(ChartEvents.markAt(emptyList(), xPixels = 0f, radiusPixels = 24f, xOf = xOf))
    }
}
