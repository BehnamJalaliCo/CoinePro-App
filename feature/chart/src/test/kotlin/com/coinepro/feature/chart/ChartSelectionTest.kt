package com.coinepro.feature.chart

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartPoint
import com.coinepro.core.chart.Drawing
import com.coinepro.core.chart.DrawingState
import com.coinepro.core.chart.DrawingTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Multi-select, and where a pasted copy lands.
 *
 * The canvas cannot express either. It selects with `additive = false` because it has no modifier
 * key, and `DrawingActions.paste` takes an offset it has no viewport to compute — so both live on
 * this side and both are the kind of thing that goes wrong once and then goes wrong forever.
 */
class ChartSelectionTest {

    private fun line(id: Long) = Drawing(
        id = id,
        toolId = "trend",
        points = listOf(ChartPoint(1_700_000_000L, 100.0), ChartPoint(1_700_003_600L, 110.0)),
    )

    private val three = DrawingState(drawings = listOf(line(1), line(2), line(3)))

    private fun selecting(state: DrawingState, id: Long) =
        state.copy(selectedId = id, selection = setOf(id))

    @Test
    fun `with the latch off a tap replaces the selection`() {
        val previous = selecting(three, 1)
        val next = selecting(three, 2)
        assertSame(next, widenSelection(previous, next, multiSelect = false))
        assertEquals(setOf(2L), widenSelection(previous, next, multiSelect = false).selection)
    }

    @Test
    fun `with the latch on a tap adds to the selection and moves the handles`() {
        val previous = selecting(three, 1)
        val next = selecting(three, 2)
        val widened = widenSelection(previous, next, multiSelect = true)
        assertEquals(setOf(1L, 2L), widened.selection)
        assertEquals(2L, widened.selectedId)
    }

    @Test
    fun `tapping something already selected takes it back out`() {
        val previous = three.copy(selectedId = 2, selection = setOf(1L, 2L))
        val next = selecting(three, 2)
        val widened = widenSelection(previous, next, multiSelect = true)
        assertEquals(setOf(1L), widened.selection)
        // The handles follow to what is left, never to the drawing just deselected.
        assertEquals(1L, widened.selectedId)
    }

    @Test
    fun `deselecting the last thing leaves the handles on nothing`() {
        val previous = selecting(three, 1)
        val next = selecting(three, 1)
        val widened = widenSelection(previous, next, multiSelect = true)
        assertTrue(widened.selection.isEmpty())
        assertNull(widened.selectedId)
    }

    @Test
    fun `a tap on empty space still clears with the latch on`() {
        val previous = three.copy(selectedId = 1, selection = setOf(1L, 2L))
        val next = three.copy(selectedId = null, selection = emptySet())
        val widened = widenSelection(previous, next, multiSelect = true)
        assertTrue(widened.selection.isEmpty())
        assertNull(widened.selectedId)
    }

    @Test
    fun `placing a drawing is never widened into the previous selection`() {
        // A commit selects the drawing it just placed. Adding the reader's earlier selection to it
        // would mean the next recolour repainted a line they had finished with.
        val previous = selecting(three, 1)
        val placed = three.copy(
            drawings = three.drawings + line(4),
            selectedId = 4,
            selection = setOf(4L),
        )
        assertEquals(setOf(4L), widenSelection(previous, placed, multiSelect = true).selection)
    }

    @Test
    fun `a tap while a tool is armed is left alone`() {
        val previous = selecting(three, 1)
        val armed = selecting(three, 2).copy(tool = DrawingTools["trend"])
        assertSame(armed, widenSelection(previous, armed, multiSelect = true))
    }

    @Test
    fun `a paste is offset in both axes so a horizontal line is not hidden under itself`() {
        val bars = CandleSeries(
            (0 until 50).map { index ->
                val price = 100.0 + index
                Candle(1_700_000_000L + index * 3600, price, price + 2, price - 2, price + 1, 1.0)
            },
        )
        val (time, price) = pasteOffset(bars, intervalSeconds = 3600)
        assertEquals(3 * 3600L, time)
        assertTrue("a paste with no price offset hides a horizontal line", price > 0.0)
        // And it stays a small fraction of what is on screen rather than a jump off the plot.
        val span = bars.high.max() - bars.low.min()
        assertTrue(price < span / 10)
    }

    @Test
    fun `a paste onto an empty chart moves in time only`() {
        val (time, price) = pasteOffset(CandleSeries.EMPTY, intervalSeconds = 300)
        assertEquals(3 * 300L, time)
        assertEquals(0.0, price, 0.0)
    }

    @Test
    fun `a nonsense bar length still produces a usable offset`() {
        // `ChartInterval.seconds` is never zero today. It is clamped anyway, because a zero offset
        // is the exact failure this function exists to prevent and a silent one.
        val (time, _) = pasteOffset(CandleSeries.EMPTY, intervalSeconds = 0)
        assertTrue(time > 0L)
    }
}
