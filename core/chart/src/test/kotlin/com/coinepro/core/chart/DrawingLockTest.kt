package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-object drawing lock.
 *
 * A phone chart is a surface a reader pans, pinches and taps constantly, and every one of those
 * gestures passes through the drawings on it. The complaint is the same in reviews of every app in
 * this category: a trend line that took care to place, nudged out of position by a thumb that was
 * trying to scroll.
 *
 * The property that matters is that the lock is enforced in the *transforms*, not at the buttons —
 * so a call site added later cannot forget it. These tests call the transforms directly, which is
 * the only way to prove that.
 */
class DrawingLockTest {

    private val line = DrawingTools.ALL.first { it.id == "trend" }

    private fun placed(): DrawingState {
        var state = DrawingActions.arm(DrawingState(), line)
        state = DrawingActions.tap(state, ChartPoint(1_700_000_000L, 100.0))
        state = DrawingActions.tap(state, ChartPoint(1_700_003_600L, 110.0))
        return state
    }

    private fun locked(): Pair<DrawingState, Long> {
        val state = placed()
        val id = state.drawings.single().id
        return DrawingActions.setLocked(state, id, true) to id
    }

    @Test
    fun `a new drawing is not locked`() {
        assertFalse(placed().drawings.single().locked)
    }

    @Test
    fun `a locked drawing refuses to be dragged by a handle`() {
        val (state, id) = locked()
        val moved = DrawingActions.movePoint(state, id, index = 0, to = ChartPoint(1_700_000_000L, 999.0))
        assertEquals(100.0, moved.drawings.single().points[0].price, 1e-9)
    }

    @Test
    fun `a locked drawing refuses to be dragged by its body`() {
        val (state, id) = locked()
        val moved = DrawingActions.moveBy(state, id, deltaTime = 3_600, deltaPrice = 50.0)
        assertEquals(state.drawings.single().points, moved.drawings.single().points)
    }

    @Test
    fun `a locked drawing refuses to be deleted`() {
        val (state, id) = locked()
        assertEquals(1, DrawingActions.delete(state, id).drawings.size)
    }

    @Test
    fun `unlocking gives all three back`() {
        val (state, id) = locked()
        val open = DrawingActions.setLocked(state, id, false)
        assertTrue(DrawingActions.movePoint(open, id, 0, ChartPoint(1_700_000_000L, 999.0)).drawings.single().points[0].price == 999.0)
        assertTrue(DrawingActions.delete(open, id).drawings.isEmpty())
    }

    @Test
    fun `locking one leaves its neighbours alone`() {
        var state = placed()
        state = DrawingActions.arm(state, line)
        state = DrawingActions.tap(state, ChartPoint(1_700_007_200L, 120.0))
        state = DrawingActions.tap(state, ChartPoint(1_700_010_800L, 130.0))
        assertEquals(2, state.drawings.size)

        val first = state.drawings.first().id
        val second = state.drawings.last().id
        val half = DrawingActions.setLocked(state, first, true)
        // The locked one survives its own delete...
        assertEquals(listOf(first, second), DrawingActions.delete(half, first).drawings.map { it.id })
        // ...and the unlocked one is still deletable, leaving the locked one behind.
        assertEquals(listOf(first), DrawingActions.delete(half, second).drawings.map { it.id })
    }

    @Test
    fun `a locked drawing is still drawn and still selectable`() {
        // The lock is about interaction, never about rendering. A lock that hid the handles would
        // be a lock nobody could find to undo.
        val (state, id) = locked()
        assertEquals(1, state.visible.size)
        assertEquals(id, DrawingActions.tap(state.copy(tool = null), ChartPoint(0L, 0.0), nearest = id).selectedId)
    }
}
