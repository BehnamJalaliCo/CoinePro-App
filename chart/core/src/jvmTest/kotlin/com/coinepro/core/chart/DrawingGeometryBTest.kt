package com.coinepro.core.chart

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The second batch of drawing geometry, checked at real coordinates.
 *
 * Every assertion here is a number that was worked out by hand from the definition of the tool
 * rather than read back off a first run, which is the only version of this test that can fail
 * usefully: a fixture captured from the implementation agrees with the implementation forever,
 * including on the day somebody inverts a sign in it.
 *
 * The two tests that matter most are the last two. One pins the property [DrawingGeometryB.barsPattern]
 * exists for — that the shape survives and the level does not — and the other pins the degenerate
 * case [DrawingGeometryB.arc] would otherwise divide by zero on.
 */
class DrawingGeometryBTest {

    private val delta = 1e-9

    private fun assertPoint(expectedT: Double, expectedP: Double, actual: GeoPointB) {
        assertEquals(expectedT, actual.t, delta)
        assertEquals(expectedP, actual.p, delta)
    }

    // ── harmonic and Elliott ──────────────────────────────────────────────────────────

    @Test
    fun `three drives labels every leg with the ratio it actually measured`() {
        // Drawn to the textbook: a base leg of 10, retraced 0.618, extended 1.272, retraced 0.618.
        val legs = DrawingGeometryB.threeDrives(
            listOf(
                GeoPointB(0.0, 100.0),
                GeoPointB(1.0, 110.0),
                GeoPointB(2.0, 103.82),
                GeoPointB(3.0, 111.68096),
                GeoPointB(4.0, 106.82288672),
            ),
        )
        assertEquals(4, legs.size)
        assertEquals(listOf("1.000", "0.618", "1.272", "0.618"), legs.map { it.label })
        assertPoint(0.0, 100.0, legs[0].a)
        assertPoint(1.0, 110.0, legs[0].b)
        assertPoint(4.0, 106.82288672, legs[3].b)
        assertTrue(DrawingGeometryB.threeDrives(listOf(GeoPointB(0.0, 1.0))).isEmpty())
    }

    @Test
    fun `an Elliott triangle names each leg by the two pivots it spans`() {
        val legs = DrawingGeometryB.elliottTriangle(
            listOf(
                GeoPointB(0.0, 100.0),
                GeoPointB(1.0, 90.0),
                GeoPointB(2.0, 98.0),
                GeoPointB(3.0, 92.0),
                GeoPointB(4.0, 96.0),
            ),
        )
        assertEquals(4, legs.size)
        assertEquals(listOf("A-B", "B-C", "C-D", "D-E"), legs.map { it.label })
        assertPoint(1.0, 90.0, legs[0].b)
        assertPoint(4.0, 96.0, legs[3].b)
        assertTrue(DrawingGeometryB.elliottTriangle(emptyList()).isEmpty())
    }

    @Test
    fun `a double combination labels W X Y and leaves the approach leg unnamed`() {
        val legs = DrawingGeometryB.elliottDoubleCombo(
            listOf(
                GeoPointB(0.0, 50.0),
                GeoPointB(1.0, 60.0),
                GeoPointB(2.0, 55.0),
                GeoPointB(3.0, 58.0),
                GeoPointB(4.0, 52.0),
            ),
        )
        assertEquals(4, legs.size)
        assertEquals(listOf(null, "W", "X", "Y"), legs.map { it.label })
        assertPoint(0.0, 50.0, legs[0].a)
        assertPoint(4.0, 52.0, legs[3].b)
    }

    @Test
    fun `a triple combination repeats X as the second connector`() {
        val legs = DrawingGeometryB.elliottTripleCombo(
            (0..6).map { GeoPointB(it.toDouble(), 100.0 + it) },
        )
        assertEquals(6, legs.size)
        assertEquals(listOf(null, "W", "X", "Y", "X", "Z"), legs.map { it.label })
        assertPoint(6.0, 106.0, legs[5].b)
    }

    // ── cycles, paths and curves ──────────────────────────────────────────────────────

    @Test
    fun `time cycles repeat the placed interval as full-height verticals`() {
        val lines = DrawingGeometryB.timeCycles(
            listOf(GeoPointB(100.0, 50.0), GeoPointB(130.0, 60.0)),
            count = 4,
        )
        assertEquals(4, lines.size)
        assertEquals(listOf(100.0, 130.0, 160.0, 190.0), lines.map { it.a.t })
        lines.forEach { line ->
            // A vertical is a position plus an instruction to extend: same price at both ends.
            assertEquals(line.a.t, line.b.t, delta)
            assertEquals(50.0, line.a.p, delta)
            assertEquals(50.0, line.b.p, delta)
            assertTrue(line.extendA && line.extendB)
        }
        assertEquals(listOf("0", "1", "2", "3"), lines.map { it.label })
        // A zero interval would stack every cycle on the first one.
        assertTrue(
            DrawingGeometryB.timeCycles(
                listOf(GeoPointB(100.0, 50.0), GeoPointB(100.0, 60.0)),
            ).isEmpty(),
        )
    }

    @Test
    fun `a path keeps every anchor and flags its last leg with an arrowhead`() {
        val anchors = listOf(GeoPointB(0.0, 1.0), GeoPointB(5.0, 3.0), GeoPointB(9.0, 2.0))
        val poly = DrawingGeometryB.path(anchors)
        assertEquals(anchors, poly.points)
        assertFalse(poly.closed)
        assertEquals(DrawingGeometryB.ARROW_HEAD, poly.label)
        assertTrue(DrawingGeometryB.path(listOf(GeoPointB(0.0, 1.0))).points.isEmpty())
    }

    @Test
    fun `a polyline closes only when it is asked to`() {
        val anchors = listOf(GeoPointB(0.0, 1.0), GeoPointB(5.0, 3.0), GeoPointB(9.0, 2.0))
        assertFalse(DrawingGeometryB.polyline(anchors, closed = false).closed)
        val closed = DrawingGeometryB.polyline(anchors, closed = true)
        assertTrue(closed.closed)
        assertEquals(anchors, closed.points)
        assertEquals(null, closed.label)
    }

    @Test
    fun `an arc through three anchors samples the circle they define`() {
        // The unit circle: left, top, right. Centre (0,0), radius 1, swept clockwise through the top.
        val poly = DrawingGeometryB.arc(
            listOf(GeoPointB(-1.0, 0.0), GeoPointB(0.0, 1.0), GeoPointB(1.0, 0.0)),
            samples = 4,
        )
        assertEquals(5, poly.points.size)
        val root = sqrt(2.0) / 2.0
        assertPoint(-1.0, 0.0, poly.points[0])
        assertPoint(-root, root, poly.points[1])
        assertPoint(0.0, 1.0, poly.points[2])
        assertPoint(root, root, poly.points[3])
        assertPoint(1.0, 0.0, poly.points[4])
        assertFalse(poly.closed)
    }

    @Test
    fun `a quadratic curve is pulled by its middle anchor rather than passing through it`() {
        val poly = DrawingGeometryB.curve(
            listOf(GeoPointB(0.0, 0.0), GeoPointB(1.0, 2.0), GeoPointB(2.0, 0.0)),
            samples = 2,
        )
        assertEquals(3, poly.points.size)
        assertPoint(0.0, 0.0, poly.points[0])
        // Halfway is a quarter, a half, a quarter — so the peak reaches 1.0, not the control's 2.0.
        assertPoint(1.0, 1.0, poly.points[1])
        assertPoint(2.0, 0.0, poly.points[2])
        assertTrue(DrawingGeometryB.curve(listOf(GeoPointB(0.0, 0.0))).points.isEmpty())
    }

    @Test
    fun `a double curve is the cubic through its four anchors`() {
        val poly = DrawingGeometryB.doubleCurve(
            listOf(
                GeoPointB(0.0, 0.0),
                GeoPointB(0.0, 3.0),
                GeoPointB(3.0, 3.0),
                GeoPointB(3.0, 0.0),
            ),
            samples = 2,
        )
        assertEquals(3, poly.points.size)
        assertPoint(0.0, 0.0, poly.points[0])
        // (P0 + 3P1 + 3P2 + P3) / 8.
        assertPoint(1.5, 2.25, poly.points[1])
        assertPoint(3.0, 0.0, poly.points[2])
        assertTrue(
            DrawingGeometryB.doubleCurve(
                listOf(GeoPointB(0.0, 0.0), GeoPointB(1.0, 1.0), GeoPointB(2.0, 2.0)),
            ).points.isEmpty(),
        )
    }

    @Test
    fun `an arrow mark points the way its anchor moved and meets at the anchor`() {
        // Steps of 10 and 10 in time, 10 and 5 in price: height 0.6 x 7.5 = 4.5, spread 0.2 x 10 = 2.
        val marks = DrawingGeometryB.arrowMarks(
            listOf(GeoPointB(0.0, 100.0), GeoPointB(10.0, 110.0), GeoPointB(20.0, 105.0)),
        )
        assertEquals(9, marks.size)
        assertEquals(
            listOf(
                DrawingGeometryB.MARK_UP,
                DrawingGeometryB.MARK_UP,
                DrawingGeometryB.MARK_DOWN,
            ),
            listOf(marks[0].label, marks[3].label, marks[6].label),
        )
        // The first anchor has nothing before it and points up.
        assertPoint(0.0, 95.5, marks[0].a)
        assertPoint(0.0, 100.0, marks[0].b)
        assertPoint(-2.0, 98.425, marks[1].a)
        assertPoint(2.0, 98.425, marks[2].a)
        // The third fell, so the shaft hangs above the anchor and the barbs with it.
        assertPoint(20.0, 109.5, marks[6].a)
        assertPoint(20.0, 105.0, marks[6].b)
        assertPoint(18.0, 106.575, marks[7].a)
        assertTrue(DrawingGeometryB.arrowMarks(listOf(GeoPointB(0.0, 100.0))).isEmpty())
    }

    // ── replaying the chart ───────────────────────────────────────────────────────────

    @Test
    fun `a bars pattern replots the window as high-low sticks from the anchor`() {
        val sticks = DrawingGeometryB.barsPattern(
            source = doubleArrayOf(10.0, 12.0, 11.0),
            sourceOpen = doubleArrayOf(9.0, 13.0, 10.0),
            sourceHigh = doubleArrayOf(11.0, 14.0, 12.0),
            sourceLow = doubleArrayOf(9.0, 11.0, 9.5),
            fromIndex = 0,
            toIndex = 2,
            anchor = GeoPointB(100.0, 500.0),
            scale = 2.0,
        )
        assertEquals(3, sticks.size)
        assertEquals(listOf(100.0, 101.0, 102.0), sticks.map { it.a.t })
        assertPoint(100.0, 498.0, sticks[0].a)
        assertPoint(100.0, 502.0, sticks[0].b)
        assertPoint(101.0, 502.0, sticks[1].a)
        assertPoint(101.0, 508.0, sticks[1].b)
        assertPoint(102.0, 499.0, sticks[2].a)
        assertPoint(102.0, 504.0, sticks[2].b)
        assertEquals(
            listOf(DrawingGeometryB.MARK_UP, DrawingGeometryB.MARK_DOWN, DrawingGeometryB.MARK_UP),
            sticks.map { it.label },
        )
        // An inverted or empty window is not an error and is not a one-bar pattern either.
        assertTrue(
            DrawingGeometryB.barsPattern(
                source = doubleArrayOf(10.0, 12.0),
                sourceOpen = doubleArrayOf(9.0, 13.0),
                sourceHigh = doubleArrayOf(11.0, 14.0),
                sourceLow = doubleArrayOf(9.0, 11.0),
                fromIndex = 1,
                toIndex = 0,
                anchor = GeoPointB(0.0, 1.0),
            ).isEmpty(),
        )
        assertTrue(
            DrawingGeometryB.barsPattern(
                source = DoubleArray(0),
                sourceOpen = DoubleArray(0),
                sourceHigh = DoubleArray(0),
                sourceLow = DoubleArray(0),
                fromIndex = 0,
                toIndex = 0,
                anchor = GeoPointB(0.0, 1.0),
            ).isEmpty(),
        )
    }

    @Test
    fun `a bars pattern keeps the shape of the window and throws its level away`() {
        // This is the whole tool. The window sits around 10; the copy sits around 500, doubled.
        val high = doubleArrayOf(11.0, 14.0, 12.0)
        val low = doubleArrayOf(9.0, 11.0, 9.5)
        val closes = doubleArrayOf(10.0, 12.0, 11.0)
        val sticks = DrawingGeometryB.barsPattern(
            source = closes,
            sourceOpen = doubleArrayOf(9.0, 13.0, 10.0),
            sourceHigh = high,
            sourceLow = low,
            fromIndex = 0,
            toIndex = 2,
            anchor = GeoPointB(100.0, 500.0),
            scale = 2.0,
        )
        val sourceRanges = (0..2).map { high[it] - low[it] }
        val copiedRanges = sticks.map { it.b.p - it.a.p }
        // Shape preserved: every bar stands in the same proportion to its neighbour as it did.
        assertEquals(sourceRanges[0] / sourceRanges[1], copiedRanges[0] / copiedRanges[1], delta)
        assertEquals(sourceRanges[1] / sourceRanges[2], copiedRanges[1] / copiedRanges[2], delta)
        // The close of the first copied bar sits exactly on the anchor: the same fraction of the
        // way up the first stick as the real close sat up the real bar.
        assertEquals(
            (closes[0] - low[0]) / (high[0] - low[0]),
            (500.0 - sticks[0].a.p) / (sticks[0].b.p - sticks[0].a.p),
            delta,
        )
        // Level discarded: nothing in the copy is anywhere near where the window was drawn.
        assertTrue(sticks.all { it.a.p > 400.0 })
    }

    @Test
    fun `a ghost feed replays the window's returns forward from the anchor`() {
        // Up ten percent, then down ten percent, cycling once more when the projection outruns them.
        val poly = DrawingGeometryB.ghostFeed(
            closes = doubleArrayOf(100.0, 110.0, 99.0),
            fromIndex = 0,
            toIndex = 2,
            anchor = GeoPointB(5.0, 200.0),
            bars = 3,
        )
        assertEquals(4, poly.points.size)
        assertPoint(5.0, 200.0, poly.points[0])
        assertPoint(6.0, 220.0, poly.points[1])
        assertPoint(7.0, 198.0, poly.points[2])
        assertPoint(8.0, 217.8, poly.points[3])
        assertTrue(
            DrawingGeometryB.ghostFeed(
                doubleArrayOf(100.0, 110.0),
                fromIndex = 0,
                toIndex = 1,
                anchor = GeoPointB(0.0, 1.0),
                bars = 0,
            ).points.isEmpty(),
        )
        // A single-bar window has no return in it at all.
        assertTrue(
            DrawingGeometryB.ghostFeed(
                doubleArrayOf(100.0, 110.0),
                fromIndex = 1,
                toIndex = 1,
                anchor = GeoPointB(0.0, 1.0),
                bars = 4,
            ).points.isEmpty(),
        )
    }

    @Test
    fun `a sector sweeps from the radius anchor to the third anchor and closes on the centre`() {
        val poly = DrawingGeometryB.sector(
            listOf(GeoPointB(0.0, 0.0), GeoPointB(2.0, 0.0), GeoPointB(0.0, 5.0)),
            samples = 2,
        )
        // Centre, then three points along a quarter turn of a circle of radius 2.
        assertEquals(4, poly.points.size)
        assertTrue(poly.closed)
        assertPoint(0.0, 0.0, poly.points[0])
        assertPoint(2.0, 0.0, poly.points[1])
        assertPoint(sqrt(2.0), sqrt(2.0), poly.points[2])
        assertPoint(0.0, 2.0, poly.points[3])
        // A radius of zero has no wedge in it.
        assertTrue(
            DrawingGeometryB.sector(
                listOf(GeoPointB(1.0, 1.0), GeoPointB(1.0, 1.0), GeoPointB(2.0, 2.0)),
            ).points.isEmpty(),
        )
    }

    @Test
    fun `an arc through three collinear anchors degrades to the straight line through them`() {
        // The circumcircle of three points on a line has an infinite radius, and the determinant it
        // is found from is zero. The anchors come back as they went in rather than as a division.
        val anchors = listOf(GeoPointB(0.0, 0.0), GeoPointB(1.0, 1.0), GeoPointB(2.0, 2.0))
        val poly = DrawingGeometryB.arc(anchors, samples = 48)
        assertEquals(anchors, poly.points)
        assertFalse(poly.closed)
        // The same holds at chart-sized times, where an absolute epsilon would have missed it.
        val far = listOf(
            GeoPointB(1.7e9, 2600.0),
            GeoPointB(1.7e9 + 3600.0, 2650.0),
            GeoPointB(1.7e9 + 7200.0, 2700.0),
        )
        assertEquals(3, DrawingGeometryB.arc(far).points.size)
        assertEquals(far, DrawingGeometryB.arc(far).points)
    }
}
