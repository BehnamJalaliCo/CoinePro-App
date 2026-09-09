package com.coinepro.feature.chart

import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProFold
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The plot above the crease.
 *
 * On a foldable half-open on a table the hinge runs across the window at a height the layout
 * cannot choose; the plot either stops above it or the price scale is cut in two. The arithmetic
 * is a pure function so it is pinned here: flat devices are untouched, a table-top cap is the
 * hinge's top less the chrome above the plot, and a hinge too high to leave a chart is not obeyed
 * past the point where the chart stops being one.
 */
class ChartFoldTest {

    @Test
    fun `a flat device keeps the height it asked for`() {
        assertEquals(620.dp, plotHeightAboveHinge(620.dp, CoineProFold.Flat))
    }

    @Test
    fun `a book posture does not cap the height`() {
        assertEquals(620.dp, plotHeightAboveHinge(620.dp, CoineProFold(book = true)))
    }

    @Test
    fun `table-top caps the plot at the hinge less the chrome above it`() {
        val fold = CoineProFold(tableTop = true, hingeTopDp = 420, hingeBottomDp = 440)
        assertEquals((420 - 96).dp, plotHeightAboveHinge(620.dp, fold))
    }

    @Test
    fun `a plot already above the hinge is not stretched to it`() {
        val fold = CoineProFold(tableTop = true, hingeTopDp = 700, hingeBottomDp = 720)
        assertEquals(300.dp, plotHeightAboveHinge(300.dp, fold))
    }

    @Test
    fun `a hinge too high for a chart leaves the minimum plot`() {
        val fold = CoineProFold(tableTop = true, hingeTopDp = 200, hingeBottomDp = 220)
        assertEquals(180.dp, plotHeightAboveHinge(620.dp, fold))
    }
}
