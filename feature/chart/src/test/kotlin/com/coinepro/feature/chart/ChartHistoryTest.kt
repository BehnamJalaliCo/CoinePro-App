package com.coinepro.feature.chart

import com.coinepro.core.chart.ChartType
import com.coinepro.core.chart.DrawingState
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartHistoryTest {

    private var clock = 0L

    private fun history(limit: Int = 50) = ChartHistory(limit = limit) { clock }

    private fun step(
        interval: Timeframe = Timeframe.H1,
        type: ChartType = ChartType.CANDLES,
        indicators: Set<String> = emptySet(),
    ) = ChartStep(
        interval = ChartInterval.Preset(interval),
        range = null,
        chartType = type,
        indicators = indicators,
        indicatorPeriods = emptyMap(),
        drawing = DrawingState(),
    )

    @Test
    fun `an empty history offers nothing in either direction`() {
        val history = history()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertNull(history.undo(step()))
        assertNull(history.redo(step()))
    }

    @Test
    fun `undo and redo walk the same path in both directions`() {
        val history = history()
        val first = step(indicators = emptySet())
        val second = step(indicators = setOf("ema"))
        val third = step(indicators = setOf("ema", "rsi"))

        history.record(first)
        history.record(second)
        assertTrue(history.canUndo)

        // Standing at the third state, one step back is the second.
        assertEquals(second, history.undo(third))
        assertEquals(first, history.undo(second))
        assertFalse(history.canUndo)

        // And forward again, in the order they were taken.
        assertTrue(history.canRedo)
        assertEquals(second, history.redo(first))
        assertEquals(third, history.redo(second))
        assertFalse(history.canRedo)
    }

    @Test
    fun `going a different way loses the way not taken`() {
        val history = history()
        history.record(step(indicators = emptySet()))
        history.undo(step(indicators = setOf("ema")))
        assertTrue("undo must fill the redo stack", history.canRedo)

        // The reader has now made a *different* change. Redo would put back a state that was
        // computed against a chart that no longer exists.
        history.record(step(type = ChartType.HEIKIN_ASHI))
        assertFalse(history.canRedo)
    }

    @Test
    fun `a change that changes nothing is not a step`() {
        val history = history()
        val same = step()
        history.record(same)
        history.record(same)
        assertEquals(same, history.undo(step(indicators = setOf("ema"))))
        assertFalse("the duplicate was kept", history.canUndo)
    }

    @Test
    fun `a drag is one step and not thirty`() {
        val history = history()
        // The first frame of the gesture always records — there is nothing on the stack to
        // coalesce against.
        history.record(step(indicators = setOf("a")), coalescable = true)
        // Thirty frames at sixteen milliseconds is under half a second, so every one of them falls
        // inside the window and the whole half-gesture is one step. A drag that runs longer than
        // the window does record again — that is the next test, and it is the behaviour a reader
        // wants from a slow, deliberate adjustment.
        repeat(30) {
            clock += 16
            history.record(step(indicators = setOf("frame-$it")), coalescable = true)
        }
        assertEquals(setOf("a"), history.undo(step()).let { it!!.indicators })
        assertFalse("a single drag left more than one step", history.canUndo)
    }

    @Test
    fun `a long drag keeps recording once the window has passed`() {
        val history = history()
        history.record(step(indicators = setOf("a")), coalescable = true)
        clock += 700
        history.record(step(indicators = setOf("b")), coalescable = true)
        assertEquals(setOf("b"), history.undo(step())!!.indicators)
        assertEquals(setOf("a"), history.undo(step())!!.indicators)
    }

    @Test
    fun `a discrete change is never coalesced away`() {
        val history = history()
        history.record(step(indicators = setOf("a")))
        history.record(step(indicators = setOf("b")))
        assertEquals(setOf("b"), history.undo(step())!!.indicators)
        assertEquals(setOf("a"), history.undo(step())!!.indicators)
    }

    @Test
    fun `the oldest steps fall off the end rather than the newest`() {
        val history = history(limit = 3)
        repeat(5) { history.record(step(indicators = setOf("step-$it"))) }
        // The three most recent survive; the two oldest are gone. A cap that dropped the *newest*
        // would make undo stop working exactly when the reader had been busiest.
        assertEquals(setOf("step-4"), history.undo(step())!!.indicators)
        assertEquals(setOf("step-3"), history.undo(step())!!.indicators)
        assertEquals(setOf("step-2"), history.undo(step())!!.indicators)
        assertFalse(history.canUndo)
    }

    @Test
    fun `clearing forgets both directions`() {
        val history = history()
        history.record(step(indicators = setOf("a")))
        history.undo(step())
        history.clear()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }
}
