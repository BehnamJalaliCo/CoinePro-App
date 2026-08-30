package com.coinepro.feature.chart

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.coinepro.core.designsystem.CoineProRailWidth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which permanent columns the chart page opens, at the widths where the answer changes.
 *
 * The property that actually matters is the last test here: **the plot never gets smaller than it
 * was on a phone.** Everything else on this screen can be argued about; a tablet layout that hands
 * the reader a narrower chart than the phone they came from is not a layout to argue about, and it
 * is the outcome a column added without a floor produces every time.
 */
class ChartWorkbenchTest {

    /** The content width left over after the navigation rail has taken the start edge. */
    private fun content(window: Dp, labelledRail: Boolean = false): Dp =
        window - if (labelledRail) CoineProRailWidth.LABELLED else CoineProRailWidth.ICON

    @Test
    fun `a phone opens neither column, so the page is exactly what it was`() {
        assertEquals(
            ChartWorkbenchColumns.NONE,
            columnsFor(width = 411.dp, hasTools = true, hasReadings = true),
        )
    }

    @Test
    fun `a large phone in landscape still opens neither, because the plot would pay for it`() {
        // 640dp of content less the 280 the palette costs is 360 of plot — narrower than the same
        // chart on the phone held upright.
        assertEquals(
            ChartWorkbenchColumns.NONE,
            columnsFor(width = 640.dp, hasTools = true, hasReadings = true),
        )
    }

    @Test
    fun `a tablet held upright opens the tool column and stops there`() {
        // 840dp window, 80dp rail: 760 of content, 480 of plot once the palette has its 280.
        val width = content(840.dp)
        assertEquals(
            ChartWorkbenchColumns.TOOLS,
            columnsFor(width = width, hasTools = true, hasReadings = true),
        )
    }

    @Test
    fun `a tablet held sideways opens both`() {
        val width = content(1280.dp)
        assertEquals(
            ChartWorkbenchColumns.TOOLS_AND_READINGS,
            columnsFor(width = width, hasTools = true, hasReadings = true),
        )
    }

    @Test
    fun `there is a band where the tools are open and the readings are not`() {
        // The order is the decision, and this is what makes it observable: a reader arms a tool
        // many times a session and reads the panel once, so a portrait tablet spends its one
        // affordable column on the palette. A rule that switched both on together would have no
        // such band, and the tablet in the middle would get neither.
        val onlyTools = CHART_TOOL_COLUMN + CHART_MIN_PLOT_WIDTH
        val both = CHART_TOOL_COLUMN + CHART_READINGS_COLUMN + CHART_MIN_PLOT_WIDTH
        assertTrue(onlyTools < both)
        assertEquals(
            ChartWorkbenchColumns.TOOLS,
            columnsFor(width = onlyTools, hasTools = true, hasReadings = true),
        )
        assertEquals(
            ChartWorkbenchColumns.TOOLS,
            columnsFor(width = both - 1.dp, hasTools = true, hasReadings = true),
        )
        assertEquals(
            ChartWorkbenchColumns.TOOLS_AND_READINGS,
            columnsFor(width = both, hasTools = true, hasReadings = true),
        )
    }

    @Test
    fun `a caller with no palette can still earn the readings column`() {
        // The studio offers readings and no drawing rail. It pays the same price for its column as
        // the palette would have.
        val threshold = CHART_READINGS_COLUMN + CHART_MIN_PLOT_WIDTH
        assertEquals(
            ChartWorkbenchColumns.NONE,
            columnsFor(width = threshold - 1.dp, hasTools = false, hasReadings = true),
        )
        val columns = columnsFor(width = threshold, hasTools = false, hasReadings = true)
        assertEquals(ChartWorkbenchColumns.READINGS, columns)
        assertTrue("the readings column is what was earned", columns.hasReadings)
        // And it must not claim a palette nobody handed it: the page would drop the band's drawing
        // button and put nothing on screen in its place.
        assertTrue("no palette was offered", !columns.hasTools)
    }

    @Test
    fun `no column is ever opened at the cost of the plot's floor`() {
        var width = 300.dp
        while (width <= 2400.dp) {
            val columns = columnsFor(width = width, hasTools = true, hasReadings = true)
            val taken = when (columns) {
                ChartWorkbenchColumns.NONE -> 0.dp
                ChartWorkbenchColumns.TOOLS -> CHART_TOOL_COLUMN
                ChartWorkbenchColumns.READINGS -> CHART_READINGS_COLUMN
                ChartWorkbenchColumns.TOOLS_AND_READINGS ->
                    CHART_TOOL_COLUMN + CHART_READINGS_COLUMN
            }
            assertTrue(
                "at ${width} the columns left only ${width - taken} of plot",
                columns == ChartWorkbenchColumns.NONE || width - taken >= CHART_MIN_PLOT_WIDTH,
            )
            width += 10.dp
        }
    }

    @Test
    fun `the plot's floor is at least as wide as the phone the page was designed for`() {
        // A phone is 411dp across and the plot is bled to both of its edges. The floor being above
        // that is the whole promise: no reader ever moves from a phone to a tablet and finds less
        // chart than they had.
        assertTrue(CHART_MIN_PLOT_WIDTH >= 411.dp)
    }
}
