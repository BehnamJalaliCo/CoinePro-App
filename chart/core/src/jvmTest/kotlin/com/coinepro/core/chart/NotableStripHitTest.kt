package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Turning a touch in the axis strip into a notable bar.
 *
 * The same shape as `markAt`'s own test and for the same reasons: nearest rather than first, so two
 * dots on adjacent bars do not both resolve to the left-hand one for ever.
 */
class NotableStripHitTest {

    /** Twelve points apart, which is about what a dense chart gives. */
    private val xOf: (Int) -> Float = { index -> index * 12f }

    @Test
    fun `nothing within the radius is nothing`() {
        assertNull(ChartEvents.notableAt(listOf(0, 40), xPixels = 240f, radiusPixels = 24f, xOf = xOf))
    }

    @Test
    fun `an empty list answers nothing`() {
        assertNull(ChartEvents.notableAt(emptyList(), xPixels = 0f, radiusPixels = 24f, xOf = xOf))
    }

    @Test
    fun `the nearest wins, not the first`() {
        // Both are inside a 24pt radius of 22f; the first in the list is the further one.
        assertEquals(2, ChartEvents.notableAt(listOf(1, 2), xPixels = 22f, radiusPixels = 24f, xOf = xOf))
    }

    @Test
    fun `a bar an event glyph already holds is not offered`() {
        // The glyph is the more specific answer and takes the touch; without the exclusion a tap
        // that missed the glyph by a few points would open the dot's sheet instead.
        assertNull(
            ChartEvents.notableAt(
                notable = listOf(5),
                xPixels = 60f,
                radiusPixels = 24f,
                exclude = setOf(5),
                xOf = xOf,
            ),
        )
        assertEquals(
            6,
            ChartEvents.notableAt(
                notable = listOf(5, 6),
                xPixels = 66f,
                radiusPixels = 24f,
                exclude = setOf(5),
                xOf = xOf,
            ),
        )
    }

    @Test
    fun `the radius is inclusive at its edge`() {
        assertEquals(0, ChartEvents.notableAt(listOf(0), xPixels = 24f, radiusPixels = 24f, xOf = xOf))
        assertNull(ChartEvents.notableAt(listOf(0), xPixels = 24.5f, radiusPixels = 24f, xOf = xOf))
    }
}
