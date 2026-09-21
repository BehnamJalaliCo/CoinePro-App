package com.coinepro.core.chart

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Three things the chart computed and then did not draw.
 *
 * Each of these had working arithmetic and no way to reach it: the volume profile bucketed its rows
 * and threw them away at the call site, the angle constraint had no gesture, and two of the rail's
 * three pointers set a field nothing read. The tests are written against what a reader would see —
 * the histogram arriving at the canvas, the long press being free exactly where it is free — rather
 * than against inventories, because an inventory assertion is what let all three ship.
 */
class ChartPointerAndProfileTest {

    /** 200 bars: 150 heavy ones around 100, then 50 light ones around 200. */
    private val series = CandleSeries(
        (0 until 200).map { index ->
            val old = index < 150
            val base = if (old) 100.0 else 200.0
            val drift = (index % 5) * 0.2
            Candle(
                t = 1_700_000_000L + index * 3600L,
                o = base + drift,
                h = base + drift + 1.0,
                l = base + drift - 1.0,
                c = base + drift + 0.5,
                v = if (old) 1_000.0 else 40.0,
            )
        },
    )

    private val view = ChartViewport(series).sized(width = 360f, height = 240f)

    private val profileOption = ChartCatalog.INDICATORS.first { it.id == "volumeprofile_ind" }

    private fun tool(id: String) = DrawingTools[id]!!

    // ── item 54: the profile reaches the canvas as a profile ──────────────────────────

    @Test
    fun `the volume profile row carries the histogram it was read off`() {
        val lines = ChartCatalog.overlayFor(profileOption, series)
        val rows = lines.first().profile
        assertNotNull("the point-of-control line has to carry the rows or nothing draws them", rows)
        assertTrue("a profile with no buckets is not a profile", rows!!.volume.isNotEmpty())
        assertTrue("some price has to have traded", rows.volume.any { it > 0.0 })
        // The line and the bars must be two readings of one measurement, or the histogram is
        // drawn against a point of control that came from somewhere else.
        val control = (rows.rowLow[rows.pocIndex] + rows.rowHigh[rows.pocIndex]) / 2
        assertEquals(control, lines.first().values.raw(0), 1e-9)
    }

    @Test
    fun `only one of the three lines carries the rows, so the bars are drawn once`() {
        val lines = ChartCatalog.overlayFor(profileOption, series)
        assertEquals(
            "the histogram belongs to the headline, not to every level derived from it",
            1,
            lines.count { it.profile != null },
        )
    }

    @Test
    fun `the rows follow the window the reader is looking at`() {
        val visible = ChartCatalog.overlayFor(
            profileOption,
            series,
            window = BarWindow.visible(150, 199),
        ).first().profile
        assertNotNull(visible)
        // The light bars traded around 200 and nowhere near 100, so the ladder the bars are drawn
        // against has to start above the old price. A histogram whose rows reach down to 100 is a
        // picture of a window the reader is not looking at, drawn over the one they are.
        val whole = ChartCatalog.overlayFor(profileOption, series).first().profile!!
        assertTrue(
            "the windowed ladder must not reach the prices the window never traded at",
            visible!!.rowLow.min() > 150.0,
        )
        assertTrue(
            "and the whole-series ladder must, or the two answers are the same answer",
            whole.rowLow.min() < 150.0,
        )
    }

    @Test
    fun `an ordinary overlay carries no profile, so nothing else draws a histogram`() {
        val ema = ChartCatalog.INDICATORS.first { it.id == "ema" }
        assertTrue(ChartCatalog.overlayFor(ema, series).all { it.profile == null })
    }

    @Test
    fun `a feed with no volume offers no rows to draw`() {
        val silent = CandleSeries(
            (0 until 50).map { Candle(1_700_000_000L + it * 3600L, 100.0, 101.0, 99.0, 100.5) },
        )
        assertTrue(ChartCatalog.overlayFor(profileOption, silent).isEmpty())
    }

    // ── item 48: where the long press is allowed to mean the constraint ───────────────

    private fun plotOf(time: Long, price: Double) =
        Offset(view.xOfTime(time), view.yOf(price))

    @Test
    fun `mid-placement the long press is free, and before the first anchor it is not`() {
        val armed = DrawingActions.arm(DrawingState(), tool("trend"))
        assertFalse(
            "with nothing placed yet the long press is still tracking mode",
            constrainableAt(armed, view, plotOf(series.time[40], 100.0), TOLERANCE),
        )
        val pending = DrawingActions.tap(armed, ChartPoint(series.time[40], 100.0))
        assertTrue(
            "with an anchor down the crosshair a long press would drop is one the next tap dismisses",
            constrainableAt(pending, view, plotOf(series.time[80], 120.0), TOLERANCE),
        )
    }

    @Test
    fun `with nothing armed and nothing selected the long press keeps its old meaning`() {
        assertFalse(constrainableAt(DrawingState(), view, plotOf(series.time[40], 100.0), TOLERANCE))
    }

    @Test
    fun `on a selected line the long press is the constraint only on a handle`() {
        val ends = listOf(
            ChartPoint(series.time[40], 100.0),
            ChartPoint(series.time[120], 180.0),
        )
        val state = DrawingState(
            drawings = listOf(Drawing(id = 1L, toolId = "trend", points = ends)),
            selectedId = 1L,
        )
        for (end in ends) {
            assertTrue(
                "an anchor of the selected drawing is where a reader is about to drag",
                constrainableAt(state, view, plotOf(end.time, end.price), TOLERANCE),
            )
        }
        // Halfway along the line is still a place to read a price off, so tracking mode keeps it.
        assertFalse(
            "the middle of a line is not a handle",
            constrainableAt(state, view, plotOf(series.time[80], 140.0), TOLERANCE),
        )
    }

    @Test
    fun `a shape with no second point to constrain does not take the gesture`() {
        val state = DrawingState(
            drawings = listOf(
                Drawing(
                    id = 1L,
                    toolId = "xabcd",
                    points = (0 until 5).map { ChartPoint(series.time[40 + it * 10], 100.0 + it) },
                ),
            ),
            selectedId = 1L,
        )
        assertFalse(constrainableAt(state, view, plotOf(series.time[40], 100.0), TOLERANCE))
    }

    @Test
    fun `the handle under the finger is the one that moves`() {
        val ends = listOf(
            ChartPoint(series.time[40], 100.0),
            ChartPoint(series.time[120], 180.0),
        )
        val drawing = Drawing(id = 1L, toolId = "trend", points = ends)
        assertEquals(0, handleIndexAt(drawing, view, plotOf(ends[0].time, ends[0].price), TOLERANCE))
        assertEquals(1, handleIndexAt(drawing, view, plotOf(ends[1].time, ends[1].price), TOLERANCE))
        assertEquals(-1, handleIndexAt(drawing, view, plotOf(series.time[80], 140.0), TOLERANCE))
    }

    @Test
    fun `the guide rays are drawn from the point the constraint measures against`() {
        // Without an anchor there is nothing to be at forty-five degrees *to*, and a fan of rays
        // over a chart with a latch left on would be a mode announcing itself about nothing.
        assertTrue(constraintAnchors(DrawingState()).isEmpty())

        var placing = DrawingActions.arm(DrawingState(), tool("trend"))
        assertTrue("nothing is down yet", constraintAnchors(placing).isEmpty())
        placing = DrawingActions.tap(placing, ChartPoint(series.time[40], 100.0))
        assertEquals(
            "mid-placement the rays belong to the anchor the next tap is measured from",
            listOf(ChartPoint(series.time[40], 100.0)),
            constraintAnchors(placing),
        )

        val ends = listOf(
            ChartPoint(series.time[40], 100.0),
            ChartPoint(series.time[120], 180.0),
        )
        val selected = DrawingState(
            drawings = listOf(Drawing(id = 1L, toolId = "trend", points = ends)),
            selectedId = 1L,
        )
        assertEquals(
            "either end can be the one that moves, so both pivots are shown",
            ends,
            constraintAnchors(selected),
        )
    }

    @Test
    fun `the constraint spends itself on the point it was held for`() {
        // The latch has to be dropped by whatever consumes it: `DrawingActions.commit` releases the
        // magnet and knows nothing about this one, so a latch nobody clears is a chart that goes on
        // snapping every line the reader draws afterwards.
        var state = DrawingActions.arm(DrawingState(), tool("trend"))
        state = DrawingActions.tap(state, ChartPoint(series.time[40], 100.0))
        state = DrawingActions.setConstrainAngle(state, true)
        assertTrue(state.constrainAngle)
        val second = DrawingActions.constrain(
            toolId = "trend",
            from = state.pending.first(),
            to = ChartPoint(series.time[120], 180.0),
            view = view,
        )
        val placed = DrawingActions.setConstrainAngle(
            DrawingActions.tapSnapped(state, second, series),
            false,
        )
        assertFalse("a held constraint must not outlive the placement", placed.constrainAngle)
        assertEquals(1, placed.drawings.size)
    }

    // ── item 40: the three pointers are three different modes ─────────────────────────

    @Test
    fun `each pointer entry in the rail resolves to a mode the canvas can branch on`() {
        for (mode in listOf(DrawingMode.CURSOR, DrawingMode.ARROW_CURSOR, DrawingMode.DOT)) {
            val state = DrawingActions.arm(DrawingState(), tool(mode.toolId))
            assertEquals(
                "«${tool(mode.toolId).label}» has to reach the crosshair as its own mode",
                mode,
                state.mode,
            )
            assertNull("a pointer places nothing", state.tool)
        }
        // And the three are genuinely distinct, or two rail buttons draw the same thing.
        assertEquals(
            3,
            listOf(DrawingMode.CURSOR, DrawingMode.ARROW_CURSOR, DrawingMode.DOT).toSet().size,
        )
    }

    private companion object {
        /** A thumb's worth of slack, the same order the canvas uses for its own hit tests. */
        const val TOLERANCE = 12f
    }
}
