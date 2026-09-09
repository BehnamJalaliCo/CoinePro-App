package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Go to a date: the arithmetic that turns a bar the reader asked for into a viewport offset.
 *
 * The mirror is the whole trap. A resolved date counts *forward* from the oldest bar and
 * [ChartViewport.offset] counts *back* from the live edge, so getting the subtraction the wrong way
 * round lands the reader exactly as far the wrong side of the chart — which looks like a working
 * feature until somebody checks the date on the bar they were sent to.
 */
class ChartFocusTest {

    private val series = CandleSeries(
        (0 until 500).map { index ->
            val base = 2_600.0 + index * 0.1
            Candle(
                t = 1_700_000_000L + index * 3600,
                o = base,
                h = base + 1,
                l = base - 1,
                c = base + 0.5,
            )
        },
    )

    private fun viewport() = ChartViewport(series).sized(width = 360f, height = 240f)

    @Test
    fun `the last bar is the live edge, which is an offset of nothing`() {
        assertEquals(0, focusOffset(series.size, series.size - 1))
    }

    @Test
    fun `a bar in the middle is that many bars back from the live edge`() {
        // Bar 300 of 500 sits 199 bars behind the last one, and the viewport counts in exactly
        // those units.
        assertEquals(199, focusOffset(series.size, 300))

        val focused = viewport().atOffset(focusOffset(series.size, 300))
        assertEquals(300, focused.lastVisible)
        assertTrue(focused.firstVisible < 300)
    }

    @Test
    fun `the oldest bar is reachable and does not run off the front of the series`() {
        val focused = viewport().atOffset(focusOffset(series.size, 0))

        // The far clamp belongs to `atOffset`, which is the only thing that knows how few bars may
        // be left on screen — asking for the first bar must not scroll past it into empty space.
        assertTrue(focused.firstVisible >= 0)
        assertTrue(focused.offset <= series.size)
        assertEquals(0, focused.firstVisible)
    }

    @Test
    fun `a date past the last bar lands on the live edge rather than beyond it`() {
        // What a reader typing next month's date produces. Clamped at zero here, before the
        // viewport ever sees a negative offset.
        assertEquals(0, focusOffset(series.size, series.size + 40))

        val focused = viewport().atOffset(focusOffset(series.size, series.size + 40))
        assertTrue(focused.isAtLiveEdge)
    }

    @Test
    fun `a negative index is clamped by the viewport rather than throwing`() {
        // Nothing upstream should send one, and the layer below must still be total: an index off
        // the front asks for an offset past the oldest bar, and `atOffset` stops it there.
        val focused = viewport().atOffset(focusOffset(series.size, -20))

        assertEquals(0, focused.firstVisible)
    }

    @Test
    fun `an empty series has no bar to go to and asks for no offset`() {
        assertEquals(0, focusOffset(0, 0))
    }
}
