package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two decisions the canvas makes on behalf of a tap, taken out of the gesture handler.
 *
 * Both are pure and both used to be unreachable: `closeShape`, `finish`, `segmentAt` and
 * `erasePartial` were written, tested in isolation and had no caller, so a reader could arm a
 * polyline and never end it and could arm the eraser and rub out nothing. These are the functions
 * that carry the tap from a pixel to one of those actions, and they are here rather than inside the
 * `detectTapGestures` block precisely so that they can be asserted without a device.
 */
class ChartCanvasGesturesTest {

    private val series = CandleSeries(
        (0 until 120).map { index ->
            val base = 100.0 + index * 0.5
            Candle(START + index * HOUR, base, base + 2, base - 2, base + 1)
        },
    )

    private val view = ChartViewport(series).sized(width = 360f, height = 240f)

    private val polyline = DrawingTools["polyline"]!!

    private fun at(point: ChartPoint) = view.xOfTime(point.time) to view.yOf(point.price)

    private fun pointAt(index: Int, price: Double) = ChartPoint(series.time[index], price)

    // ── closing a variable-point shape ────────────────────────────────────────────────

    @Test
    fun `tapping the first anchor of a three-point polyline closes the shape`() {
        val pending = listOf(pointAt(10, 105.0), pointAt(20, 112.0), pointAt(30, 118.0))
        val state = DrawingState(tool = polyline, pending = pending)
        val (x, y) = at(pending.first())

        assertTrue(closesPendingShape(state, x, y, view, tolerancePx = 12f))
    }

    @Test
    fun `a tap a finger's width away from the first anchor is an ordinary tap`() {
        val pending = listOf(pointAt(10, 105.0), pointAt(20, 112.0), pointAt(30, 118.0))
        val state = DrawingState(tool = polyline, pending = pending)
        val (x, y) = at(pending.first())

        // Forty pixels is well past a fingertip. Getting this wrong in the other direction is the
        // worse failure: a polyline that closes itself whenever the reader taps near where they
        // started cannot be drawn past three corners.
        assertFalse(closesPendingShape(state, x, y + 40f, view, tolerancePx = 12f))
    }

    @Test
    fun `two anchors are not enough to close, because the closure would be a line drawn back on itself`() {
        val pending = listOf(pointAt(10, 105.0), pointAt(20, 112.0))
        val state = DrawingState(tool = polyline, pending = pending)
        val (x, y) = at(pending.first())

        assertFalse(closesPendingShape(state, x, y, view, tolerancePx = 12f))
    }

    @Test
    fun `a fixed-point tool never closes, however near the first anchor the tap lands`() {
        val trend = DrawingTools["trend"]!!
        val pending = listOf(pointAt(10, 105.0), pointAt(20, 112.0), pointAt(30, 118.0))
        val state = DrawingState(tool = trend, pending = pending)
        val (x, y) = at(pending.first())

        // A trend line commits on its second tap and has no third state to be closed out of.
        assertFalse(closesPendingShape(state, x, y, view, tolerancePx = 12f))
    }

    @Test
    fun `nothing armed means nothing to close`() {
        val state = DrawingState(pending = listOf(pointAt(10, 105.0)))
        val (x, y) = at(state.pending.first())

        assertFalse(closesPendingShape(state, x, y, view, tolerancePx = 12f))
    }

    // ── the eraser ────────────────────────────────────────────────────────────────────

    private fun path(id: Long = 1L, locked: Boolean = false) = Drawing(
        id = id,
        toolId = "path",
        points = listOf(
            pointAt(10, 105.0),
            pointAt(30, 115.0),
            pointAt(50, 125.0),
            pointAt(70, 135.0),
        ),
        locked = locked,
    )

    /** The screen point halfway along one leg of a chain. */
    private fun midOfLeg(drawing: Drawing, leg: Int): Pair<Float, Float> {
        val (ax, ay) = at(drawing.points[leg])
        val (bx, by) = at(drawing.points[leg + 1])
        return (ax + bx) / 2f to (ay + by) / 2f
    }

    @Test
    fun `a tap on the middle leg of a path splits it in two and keeps both halves`() {
        val drawing = path()
        val state = DrawingState(drawings = listOf(drawing))
        val (x, y) = midOfLeg(drawing, leg = 1)

        val erased = eraseAt(state, x, y, view, tolerancePx = 12f, whole = false)
        assertNotNull(erased)
        // Four anchors, middle leg gone: two anchors on each side, so two drawings survive.
        assertEquals(2, erased!!.drawings.size)
        assertEquals(2, erased.drawings[0].points.size)
        assertEquals(2, erased.drawings[1].points.size)
        assertEquals(drawing.points.first(), erased.drawings[0].points.first())
        assertEquals(drawing.points.last(), erased.drawings[1].points.last())
    }

    @Test
    fun `a long press erases the whole object rather than one of its legs`() {
        val drawing = path()
        val state = DrawingState(drawings = listOf(drawing))
        val (x, y) = midOfLeg(drawing, leg = 1)

        val erased = eraseAt(state, x, y, view, tolerancePx = 12f, whole = true)
        assertNotNull(erased)
        assertTrue(erased!!.drawings.isEmpty())
    }

    @Test
    fun `a tap on empty chart answers null so nothing is emitted`() {
        val state = DrawingState(drawings = listOf(path()))

        // Null rather than the unchanged state: a miss must not push a state change through
        // persistence to report that nothing happened.
        assertNull(eraseAt(state, 5f, 5f, view, tolerancePx = 12f, whole = false))
        assertNull(eraseAt(state, 5f, 5f, view, tolerancePx = 12f, whole = true))
    }

    @Test
    fun `a locked drawing is refused by both halves of the eraser`() {
        val drawing = path(locked = true)
        val state = DrawingState(drawings = listOf(drawing))
        val (x, y) = midOfLeg(drawing, leg = 1)

        assertNull(eraseAt(state, x, y, view, tolerancePx = 12f, whole = false))
        assertNull(eraseAt(state, x, y, view, tolerancePx = 12f, whole = true))
    }

    @Test
    fun `a tap on a horizontal level takes the level, because a level has no legs to split`() {
        val level = Drawing(id = 4L, toolId = "hline", points = listOf(pointAt(20, 118.0)))
        val state = DrawingState(drawings = listOf(level))

        // The tap lands on the level far from the anchor: a one-point level is grabbable all the
        // way across the plot, and the eraser has to agree with the hit test about that.
        val erased = eraseAt(state, 300f, view.yOf(118.0), view, tolerancePx = 12f, whole = false)
        assertNotNull(erased)
        assertTrue(erased!!.drawings.isEmpty())
    }

    @Test
    fun `the topmost drawing wins when two overlap`() {
        val under = path(id = 1L)
        val over = path(id = 2L)
        val state = DrawingState(drawings = listOf(under, over))
        val (x, y) = midOfLeg(over, leg = 0)

        val erased = eraseAt(state, x, y, view, tolerancePx = 12f, whole = true)
        assertNotNull(erased)
        // The newer one is the one the reader means, and it is the only one that goes.
        assertEquals(listOf(1L), erased!!.drawings.map(Drawing::id))
    }

    // ── the magnet's binding, end to end ──────────────────────────────────────────────

    @Test
    fun `a tap placed by the magnet records the channel and follows a revised bar`() {
        val trend = DrawingTools["trend"]!!
        var state = DrawingState(tool = trend, magnetMode = MagnetMode.STRONG, keepDrawing = true)

        // Two taps aimed just under two lows. In strong magnet both land on the low itself.
        state = DrawingActions.tapSnapped(state, ChartPoint(series.time[10], series.low[10] - 0.4), series)
        state = DrawingActions.tapSnapped(state, ChartPoint(series.time[40], series.low[40] - 0.4), series)

        val placed = state.drawings.single()
        assertEquals(series.low[10], placed.points[0].price, 1e-9)
        assertEquals(series.low[40], placed.points[1].price, 1e-9)
        assertEquals(
            listOf(PriceChannel.LOW, PriceChannel.LOW),
            DrawingActions.channelsOf(state, placed),
        )

        // The feed corrects bar 10: the low is two dollars deeper than first reported. This is the
        // whole reason the channel is stored rather than the price — a line bound to "the low of
        // that bar" moves down with it and still touches it.
        val corrected = CandleSeries(
            series.bars.mapIndexed { index, bar ->
                if (index == 10) bar.copy(l = bar.l - 2.0) else bar
            },
        )
        val moved = DrawingActions.resnap(state, corrected)

        assertEquals(corrected.low[10], moved.drawings.single().points[0].price, 1e-9)
        assertEquals(corrected.low[40], moved.drawings.single().points[1].price, 1e-9)
    }

    @Test
    fun `a tap placed with the magnet off keeps its exact price through a revision`() {
        val trend = DrawingTools["trend"]!!
        var state = DrawingState(tool = trend, magnetMode = MagnetMode.OFF, keepDrawing = true)
        val loose = ChartPoint(series.time[10] + 900L, 111.37)

        state = DrawingActions.tapSnapped(state, loose, series)
        state = DrawingActions.tapSnapped(state, ChartPoint(series.time[40], 130.0), series)

        assertTrue(state.bindings.isEmpty())
        val corrected = CandleSeries(
            series.bars.mapIndexed { index, bar ->
                if (index == 10) bar.copy(l = bar.l - 2.0) else bar
            },
        )
        // Unbound points are the reader's own placement and a data revision is not licence to move
        // them.
        assertEquals(loose, DrawingActions.resnap(state, corrected).drawings.single().points[0])
    }

    // ── a colour template's alpha ─────────────────────────────────────────────────────

    @Test
    fun `a template colour written without its alpha byte is read as opaque`() {
        // The failure this guards is silent: a preferences string holding `0x2962FF` is a fully
        // transparent blue, so the candles would simply not be painted and nothing would report it.
        assertEquals(0xFF2962FF, opaqueArgb(0x2962FF))
        assertEquals(0xFF000000, opaqueArgb(0x000000))
    }

    @Test
    fun `a template colour that carries an alpha keeps it exactly`() {
        assertEquals(0xFFD8A848, opaqueArgb(0xFFD8A848))
        // Including a deliberately faint one: a template is entitled to ask for a half-strength
        // grid, and forcing every value opaque would take that away.
        assertEquals(0x40FFFFFF, opaqueArgb(0x40FFFFFF))
    }

    private companion object {
        const val START = 1_700_000_000L
        const val HOUR = 3_600L
    }
}
