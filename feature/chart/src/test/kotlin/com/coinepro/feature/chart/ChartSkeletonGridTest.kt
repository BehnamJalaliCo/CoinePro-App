package com.coinepro.feature.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The loading chart is the same density on every window** (run Ψ).
 *
 * The owner's question for this run was whether the work that shipped for a phone also works on the
 * larger glass. The skeleton was one of the places it did not: five rules and six columns is a
 * phone's chart, and on a landscape tablet the same five are 160 dp apart — a sparse table where a
 * chart is about to be.
 *
 * The windows below are the ones `docs/qa/PARITY_MATRIX.md` tracks, so this reads as the matrix
 * does. What is asserted is not an exact count but the property the count exists for: the spacing
 * stays inside a band a reader would call «a grid», at every width and height the product is drawn
 * at.
 */
class ChartSkeletonGridTest {

    @Test
    fun `every window in the parity matrix gets a grid at a readable spacing`() {
        WINDOWS.forEach { (name, extent) ->
            val lines = ChartSkeletonGrid.lines(extent)
            val spacing = extent / (lines + 1)
            println("$name: ${extent.toInt()} dp → $lines rules, ${spacing.toInt()} dp apart")
            assertTrue(
                "$name draws $lines rules ${spacing.toInt()} dp apart — that is not a grid",
                spacing in MIN_READABLE_SPACING..MAX_READABLE_SPACING,
            )
        }
    }

    @Test
    fun `a phone still gets what it always got`() {
        // The regression this rule must not cause: the phone's picture was right and stays right.
        // 914 dp of height is the owner's own device.
        assertEquals(9, ChartSkeletonGrid.lines(914f))
        assertEquals(4, ChartSkeletonGrid.lines(411f))
    }

    @Test
    fun `the count is bounded at both ends`() {
        // A docked panel can be very short and a desktop window very tall; neither may produce a
        // single rule or a field of hatching.
        assertEquals(ChartSkeletonGrid.MIN_LINES, ChartSkeletonGrid.lines(0f))
        assertEquals(ChartSkeletonGrid.MIN_LINES, ChartSkeletonGrid.lines(120f))
        assertEquals(ChartSkeletonGrid.MAX_LINES, ChartSkeletonGrid.lines(4_000f))
    }

    private companion object {
        /**
         * Every span the skeleton is asked to fill, named as the parity matrix names its windows.
         *
         * Both dimensions of each, because the same rule draws the horizontals and the verticals
         * and a window is wrong in whichever direction nobody checked.
         */
        val WINDOWS = listOf(
            "phone width" to 411f,
            "phone height" to 914f,
            "tablet-portrait width" to 840f,
            "pixel-tablet width" to 1_280f,
            "pixel-tablet height" to 800f,
            "tab-s9-ultra width" to 1_973f,
            "tab-s9-ultra height" to 1_232f,
            "fold-open width" to 930f,
            "fold-closed width" to 411f,
            // The chart docked in a side panel, which is the narrowest it is ever drawn.
            "docked panel width" to 320f,
        )

        /** Wider than this and it is not a scale; tighter and it is hatching. */
        const val MIN_READABLE_SPACING = 60f
        const val MAX_READABLE_SPACING = 130f
    }
}
