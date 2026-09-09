package com.coinepro.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hinge mapping, asserted without a hinge.
 *
 * `androidx.window` needs an activity to say anything; [CoineProFold.of] takes the four facts the
 * layout reads and is pinned here at every combination that changes a decision, so the mapping
 * cannot quietly turn a book posture into a table-top one, or a flat device into either.
 */
class CoineProFoldTest {

    @Test
    fun `no hinge is flat`() {
        assertEquals(CoineProFold.Flat, CoineProFold.of(halfOpened = false, horizontalHinge = true, separating = true, hingeTopDp = 400, hingeBottomDp = 420))
        assertFalse(CoineProFold.Flat.halfOpened)
    }

    @Test
    fun `a hinge that does not separate is flat`() {
        assertEquals(CoineProFold.Flat, CoineProFold.of(halfOpened = true, horizontalHinge = true, separating = false, hingeTopDp = 400, hingeBottomDp = 420))
    }

    @Test
    fun `half-open with a horizontal hinge is table-top and carries the crease`() {
        val fold = CoineProFold.of(halfOpened = true, horizontalHinge = true, separating = true, hingeTopDp = 400, hingeBottomDp = 420)
        assertTrue(fold.tableTop)
        assertFalse(fold.book)
        assertTrue(fold.halfOpened)
        assertEquals(400, fold.hingeTopDp)
        assertEquals(420, fold.hingeBottomDp)
    }

    @Test
    fun `half-open with a vertical hinge is book, and no height is capped`() {
        val fold = CoineProFold.of(halfOpened = true, horizontalHinge = false, separating = true, hingeTopDp = 0, hingeBottomDp = 800)
        assertTrue(fold.book)
        assertFalse(fold.tableTop)
        assertEquals(0, fold.hingeTopDp)
    }
}
