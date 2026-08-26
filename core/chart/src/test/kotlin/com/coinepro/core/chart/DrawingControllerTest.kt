package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingControllerTest {

    private val trend = DrawingTools["trend"]!!
    private val xabcd = DrawingTools["xabcd"]!!
    private val hline = DrawingTools["hline"]!!
    private val brush = DrawingTools["brush"]!!

    private fun point(time: Long, price: Double) = ChartPoint(time, price)

    private val series = CandleSeries(
        (0 until 10).map { index ->
            Candle(
                t = 1_700_000_000L + index * 3600,
                o = 100.0 + index,
                h = 105.0 + index,
                l = 95.0 + index,
                c = 101.0 + index,
            )
        },
    )

    @Test
    fun `a one-tap tool commits on the first tap`() {
        val state = DrawingActions.tap(DrawingActions.arm(DrawingState(), hline), point(1, 100.0))
        assertEquals(1, state.drawings.size)
        assertEquals("hline", state.drawings[0].toolId)
        assertTrue("pending should be empty after a commit", state.pending.isEmpty())
    }

    @Test
    fun `a five-tap tool commits on the fifth, not the sixth`() {
        var state = DrawingActions.arm(DrawingState(), xabcd)
        repeat(4) { step -> state = DrawingActions.tap(state, point(step.toLong(), 100.0 + step)) }
        assertTrue("committed early", state.drawings.isEmpty())
        assertEquals(4, state.pending.size)
        assertEquals(1, state.remaining)

        state = DrawingActions.tap(state, point(5, 105.0))
        assertEquals(1, state.drawings.size)
        assertEquals(5, state.drawings[0].points.size)
    }

    @Test
    fun `the half-placed drawing is visible while it is being placed`() {
        // Otherwise four taps of an XABCD are four taps into nothing.
        var state = DrawingActions.arm(DrawingState(), xabcd)
        state = DrawingActions.tap(state, point(1, 100.0))
        state = DrawingActions.tap(state, point(2, 110.0))
        assertEquals(1, state.visible.size)
        assertEquals(2, state.visible[0].points.size)
        assertFalse(state.visible[0].complete)
        assertEquals(DrawingState.PREVIEW_ID, state.visible[0].id)
    }

    @Test
    fun `placing disarms the tool`() {
        // A rail that stays armed draws a second line the moment the reader taps to look at
        // something. This is the behaviour that decision is recorded as.
        val state = DrawingActions.tap(DrawingActions.arm(DrawingState(), hline), point(1, 100.0))
        assertNull(state.tool)
    }

    @Test
    fun `undo takes back a tap before it takes back a drawing`() {
        var state = DrawingActions.tap(DrawingActions.arm(DrawingState(), hline), point(1, 100.0))
        state = DrawingActions.arm(state, xabcd)
        state = DrawingActions.tap(state, point(2, 101.0))
        state = DrawingActions.tap(state, point(3, 102.0))

        state = DrawingActions.undo(state)
        assertEquals("undo ate a finished drawing", 1, state.drawings.size)
        assertEquals(1, state.pending.size)

        state = DrawingActions.undo(state)
        assertEquals(1, state.drawings.size)
        assertTrue(state.pending.isEmpty())

        state = DrawingActions.undo(state)
        assertTrue(state.drawings.isEmpty())
        assertFalse(state.canUndo)
    }

    @Test
    fun `arming a tool clears the selection`() {
        var state = DrawingActions.tap(DrawingActions.arm(DrawingState(), hline), point(1, 100.0))
        assertEquals(1L, state.selectedId)
        state = DrawingActions.arm(state, trend)
        assertNull(state.selectedId)
    }

    @Test
    fun `a mode entry arms nothing`() {
        val cursor = DrawingTools["cursor"]!!
        assertNull(DrawingActions.arm(DrawingState(), cursor).tool)
    }

    @Test
    fun `a tap with no tool selects what the hit test found`() {
        val state = DrawingActions.tap(DrawingState(), point(1, 100.0), nearest = 7L)
        assertEquals(7L, state.selectedId)
        assertTrue(state.drawings.isEmpty())
    }

    @Test
    fun `a drag places a two-point tool in one gesture`() {
        val state = DrawingActions.drag(
            DrawingActions.arm(DrawingState(), trend),
            point(1, 100.0),
            point(5, 120.0),
        )
        assertEquals(1, state.drawings.size)
        assertEquals(2, state.drawings[0].points.size)
    }

    @Test
    fun `a drag does not place a tool that needs three points`() {
        // Its third point is not a corner of anything the first two describe, so there is nothing
        // sensible to invent for it.
        val channel = DrawingTools["channel"]!!
        val state = DrawingActions.drag(
            DrawingActions.arm(DrawingState(), channel),
            point(1, 100.0),
            point(5, 120.0),
        )
        assertTrue(state.drawings.isEmpty())
    }

    @Test
    fun `a freehand stroke needs at least two samples`() {
        val armed = DrawingActions.arm(DrawingState(), brush)
        assertTrue(DrawingActions.stroke(armed, listOf(point(1, 100.0))).drawings.isEmpty())
        assertEquals(1, DrawingActions.stroke(armed, listOf(point(1, 100.0), point(2, 101.0))).drawings.size)
    }

    @Test
    fun `ids never repeat, even after a delete`() {
        // A repeated id would make selection and deletion pick the wrong drawing, which is the kind
        // of bug that shows up as "it deleted the other one".
        var state = DrawingState()
        repeat(3) { state = DrawingActions.tap(DrawingActions.arm(state, hline), point(1, 100.0)) }
        state = DrawingActions.delete(state, 2L)
        state = DrawingActions.tap(DrawingActions.arm(state, hline), point(1, 100.0))
        assertEquals(listOf(1L, 3L, 4L), state.drawings.map { it.id })
    }

    @Test
    fun `bringing to front makes it last, which is what a tap on an overlap finds`() {
        var state = DrawingState()
        repeat(3) { state = DrawingActions.tap(DrawingActions.arm(state, hline), point(1, 100.0)) }
        state = DrawingActions.bringToFront(state, 1L)
        assertEquals(listOf(2L, 3L, 1L), state.drawings.map { it.id })
    }

    @Test
    fun `moving a point moves only that point`() {
        var state = DrawingActions.drag(
            DrawingActions.arm(DrawingState(), trend),
            point(1, 100.0),
            point(5, 120.0),
        )
        state = DrawingActions.movePoint(state, 1L, 1, point(9, 130.0))
        assertEquals(point(1, 100.0), state.drawings[0].points[0])
        assertEquals(point(9, 130.0), state.drawings[0].points[1])
    }

    @Test
    fun `moving a drawing moves every point by the same delta`() {
        var state = DrawingActions.drag(
            DrawingActions.arm(DrawingState(), trend),
            point(1_000, 100.0),
            point(5_000, 120.0),
        )
        state = DrawingActions.moveBy(state, 1L, deltaTime = 600, deltaPrice = -5.0)
        assertEquals(listOf(1_600L, 5_600L), state.drawings[0].points.map { it.time })
        assertEquals(listOf(95.0, 115.0), state.drawings[0].points.map { it.price })
    }

    @Test
    fun `the magnet snaps to the nearest bar and to its nearest of open high low close`() {
        // Bar 3 runs 98..108 with an open of 103. A tap at 107.4 is nearest that bar's high.
        val snapped = DrawingActions.snap(ChartPoint(1_700_000_000L + 3 * 3600 + 200, 107.4), series)
        assertEquals(1_700_000_000L + 3 * 3600, snapped.time)
        assertEquals(108.0, snapped.price, 1e-9)
    }

    @Test
    fun `the magnet on an empty series is a no-op rather than a crash`() {
        val point = ChartPoint(1, 100.0)
        assertEquals(point, DrawingActions.snap(point, CandleSeries(emptyList())))
    }

    @Test
    fun `cancel disarms without placing anything`() {
        var state = DrawingActions.arm(DrawingState(), xabcd)
        state = DrawingActions.tap(state, point(1, 100.0))
        state = DrawingActions.cancel(state)
        assertNull(state.tool)
        assertTrue(state.pending.isEmpty())
        assertTrue(state.drawings.isEmpty())
    }
}
