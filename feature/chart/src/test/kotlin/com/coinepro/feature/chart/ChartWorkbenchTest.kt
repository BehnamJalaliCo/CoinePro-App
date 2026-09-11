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
 *
 * ### What changed in 4.72.0
 *
 * The tools went from a 280 dp palette to a 48 dp rail and the readings stopped being a column at
 * all — they are a panel on the side rail. So the question this function answers got smaller: it is
 * now «is there room for the rail beside the plot», and «are the readings docked somewhere else».
 */
class ChartWorkbenchTest {

    /** The content width left over after the navigation rail has taken the start edge. */
    private fun content(window: Dp, labelledRail: Boolean = false): Dp =
        window - if (labelledRail) CoineProRailWidth.LABELLED else CoineProRailWidth.ICON

    @Test
    fun `a phone opens no rail, so the page is exactly what it was`() {
        assertEquals(
            ChartWorkbenchColumns.NONE,
            columnsFor(width = 411.dp, hasTools = true, docked = false),
        )
    }

    @Test
    fun `a large phone in landscape now affords the rail, because the rail costs 48`() {
        // 640dp of content less the 48 the rail costs is 592 of plot — wider than the same chart on
        // the phone held upright, which is the whole point of the rail replacing the palette.
        assertEquals(
            ChartWorkbenchColumns.TOOLS,
            columnsFor(width = 640.dp, hasTools = true, docked = false),
        )
    }

    @Test
    fun `a tablet held upright opens the rail`() {
        assertEquals(
            ChartWorkbenchColumns.TOOLS,
            columnsFor(width = content(840.dp), hasTools = true, docked = false),
        )
    }

    @Test
    fun `a tablet with a docked panel reports both, so the page draws no readings of its own`() {
        val columns = columnsFor(width = content(1280.dp), hasTools = true, docked = true)
        assertEquals(ChartWorkbenchColumns.TOOLS_AND_READINGS, columns)
        assertTrue("the readings are elsewhere", columns.hasReadings)
        assertTrue("and the rail is beside the plot", columns.hasTools)
    }

    @Test
    fun `the rail is refused below the plot's floor`() {
        val floor = CHART_TOOL_RAIL + CHART_MIN_PLOT_WIDTH
        assertEquals(
            ChartWorkbenchColumns.NONE,
            columnsFor(width = floor - 1.dp, hasTools = true, docked = false),
        )
        assertEquals(
            ChartWorkbenchColumns.TOOLS,
            columnsFor(width = floor, hasTools = true, docked = false),
        )
    }

    @Test
    fun `a caller with no palette still reports the docked readings`() {
        // The studio offers readings and no drawing rail. What it needs from this function is the
        // one bit that stops it drawing the readings twice.
        val columns = columnsFor(width = 900.dp, hasTools = false, docked = true)
        assertEquals(ChartWorkbenchColumns.READINGS, columns)
        assertTrue("the readings are docked", columns.hasReadings)
        assertTrue("no palette was offered", !columns.hasTools)
    }

    @Test
    fun `no rail is ever opened at the cost of the plot's floor`() {
        var width = 300.dp
        while (width <= 2400.dp) {
            val columns = columnsFor(width = width, hasTools = true, docked = false)
            val taken = if (columns.hasTools) CHART_TOOL_RAIL else 0.dp
            assertTrue(
                "at $width the rail left only ${width - taken} of plot",
                columns == ChartWorkbenchColumns.NONE || width - taken >= CHART_MIN_PLOT_WIDTH,
            )
            width += 4.dp
        }
    }

    /**
     * The owner's budget for a large tablet, as arithmetic: **the plot keeps 65 %**.
     *
     * A Pixel Tablet is 1280 points wide. The two rails take 104 of them and the panel is clamped
     * so that what is left of the remaining 1176 is at least 65 % — which is what the 4.71.0 layout
     * broke, with a 280 dp palette and a 360 dp panel leaving the chart 45 %.
     */
    @Test
    fun `a docked panel never takes the plot below its share`() {
        for (window in listOf(1024f, 1280f, 1480f, 1600f)) {
            val panel = panelWidthFor(windowDp = window, draggedDp = CHART_SIDE_PANEL_MAX.value)
            val betweenRails = window - (CHART_TOOL_RAIL + CHART_SIDE_RAIL_WIDTH).value
            val plot = betweenRails - panel.value
            assertTrue(
                "at ${window}dp the plot kept ${plot / betweenRails}",
                plot / betweenRails >= CHART_PLOT_SHARE - 0.001f,
            )
        }
    }

    @Test
    fun `a drag is clamped to the panel's own range`() {
        // Dragged to nothing, the panel stops at its floor: below 320 a ladder and an editor line
        // stop being readable, at which point the panel is not narrow, it is broken.
        assertEquals(CHART_SIDE_PANEL_MIN, panelWidthFor(windowDp = 1600f, draggedDp = 40f))
        // And dragged to the edge of the screen it stops at its ceiling.
        assertEquals(CHART_SIDE_PANEL_MAX, panelWidthFor(windowDp = 1600f, draggedDp = 4_000f))
    }
}
