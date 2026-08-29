package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The eleven tools in [DrawingGeometryA], checked as coordinates.
 *
 * Every assertion here is a real number worked out by hand from the published definition of the
 * tool, not a shape the code happened to produce. That is the only kind of test worth writing for
 * this file: a pitchfork whose tines are parallel to *something* passes any structural check and
 * still puts the fork in the wrong place, and the reader who notices is the one comparing it against
 * another terminal.
 *
 * One fixture serves nearly all of them — four anchors chosen so that no two coordinates coincide
 * and no midpoint lands on an anchor, which is what makes the four pitchfork variants tell
 * themselves apart below.
 */
class DrawingGeometryATest {

    private val anchor = GeoPoint(0.0, 100.0)
    private val second = GeoPoint(10.0, 120.0)
    private val third = GeoPoint(20.0, 110.0)
    private val fourth = GeoPoint(30.0, 140.0)

    private val threeAnchors = listOf(anchor, second, third)

    private fun assertPoint(expectedT: Double, expectedP: Double, actual: GeoPoint) {
        assertEquals(expectedT, actual.t, DELTA)
        assertEquals(expectedP, actual.p, DELTA)
    }

    // ── [4] regression channel ────────────────────────────────────────────────────────

    @Test
    fun `a regression channel fits the closes and not the anchors the reader tapped`() {
        // Six closes whose least-squares fit is y = 9.761904… + 1.428571…x, with a population
        // residual deviation of 1.126872…. The anchors contribute only the time span: the fit
        // starts at t=0 and ends at t=10 because that is where the reader put the window, while
        // every price comes from the data.
        val closes = doubleArrayOf(10.0, 12.0, 11.0, 15.0, 14.0, 18.0)
        val runs = DrawingGeometryA.regressionChannel(listOf(anchor, second), closes, 0, 5)
        assertEquals(3, runs.size)
        assertPoint(0.0, 9.761904761904761, runs[0].a)
        assertPoint(10.0, 16.904761904761905, runs[0].b)
        assertPoint(0.0, 12.015649441180804, runs[1].a)
        assertPoint(10.0, 19.158506584037948, runs[1].b)
        assertPoint(0.0, 7.508160082628718, runs[2].a)
        assertPoint(10.0, 14.651017225485862, runs[2].b)
        assertEquals(listOf("0", "2", "-2"), runs.map { it.label })
        // The rails are exactly parallel to the centre, which is the whole claim of a channel.
        assertEquals(runs[0].b.p - runs[0].a.p, runs[1].b.p - runs[1].a.p, DELTA)
        assertEquals(runs[0].b.p - runs[0].a.p, runs[2].b.p - runs[2].a.p, DELTA)
    }

    @Test
    fun `a regression window of one bar is not a fit and draws nothing`() {
        val closes = doubleArrayOf(10.0, 12.0, 11.0)
        assertTrue(DrawingGeometryA.regressionChannel(listOf(anchor, second), closes, 1, 1).isEmpty())
        assertTrue(DrawingGeometryA.regressionChannel(listOf(anchor, second), closes, 0, 9).isEmpty())
        assertTrue(DrawingGeometryA.regressionChannel(listOf(anchor), closes, 0, 2).isEmpty())
    }

    // ── [5] flat top and bottom ───────────────────────────────────────────────────────

    @Test
    fun `a flat top takes only the price from its third anchor and never the time`() {
        val runs = DrawingGeometryA.flatTopBottom(threeAnchors)
        assertEquals(2, runs.size)
        assertPoint(0.0, 100.0, runs[0].a)
        assertPoint(10.0, 120.0, runs[0].b)
        // The third anchor sits at t=20; the rail is still flat across the first two anchors' span.
        assertPoint(0.0, 110.0, runs[1].a)
        assertPoint(10.0, 110.0, runs[1].b)
        assertEquals(runs[1].a.p, runs[1].b.p, DELTA)
        assertTrue(runs.all { it.extendB && !it.extendA })
    }

    @Test
    fun `a flat top with no slope and a flat top with two anchors both draw nothing`() {
        assertTrue(DrawingGeometryA.flatTopBottom(listOf(anchor, second)).isEmpty())
        assertTrue(DrawingGeometryA.flatTopBottom(listOf(anchor, anchor, third)).isEmpty())
    }

    // ── [6] disjoint channel ──────────────────────────────────────────────────────────

    @Test
    fun `a disjoint channel keeps its two lines independent and joins them anchor to anchor`() {
        val runs = DrawingGeometryA.disjointChannel(listOf(anchor, second, third, fourth))
        assertEquals(4, runs.size)
        assertPoint(0.0, 100.0, runs[0].a)
        assertPoint(10.0, 120.0, runs[0].b)
        assertPoint(20.0, 110.0, runs[1].a)
        assertPoint(30.0, 140.0, runs[1].b)
        // The two rails have different slopes — 2.0 and 3.0 per unit of time — which is exactly what
        // separates this tool from a parallel channel.
        assertEquals(2.0, (runs[0].b.p - runs[0].a.p) / (runs[0].b.t - runs[0].a.t), DELTA)
        assertEquals(3.0, (runs[1].b.p - runs[1].a.p) / (runs[1].b.t - runs[1].a.t), DELTA)
        assertPoint(0.0, 100.0, runs[2].a)
        assertPoint(20.0, 110.0, runs[2].b)
        assertPoint(10.0, 120.0, runs[3].a)
        assertPoint(30.0, 140.0, runs[3].b)
        assertTrue(runs.none { it.extendA || it.extendB })
    }

    @Test
    fun `a disjoint channel with three anchors draws nothing`() {
        assertTrue(DrawingGeometryA.disjointChannel(threeAnchors).isEmpty())
    }

    // ── [7]–[9] the pitchfork variants ────────────────────────────────────────────────

    @Test
    fun `an inside pitchfork hangs its handle on the midpoint of the first leg`() {
        val runs = DrawingGeometryA.insidePitchfork(threeAnchors)
        assertEquals(4, runs.size)
        assertPoint(10.0, 120.0, runs[0].a)
        assertPoint(20.0, 110.0, runs[0].b)
        assertPoint(5.0, 110.0, runs[1].a) // midpoint of (0,100) and (10,120)
        assertPoint(15.0, 115.0, runs[1].b) // the base's midpoint, unchanged by the variant
        assertPoint(10.0, 120.0, runs[2].a)
        assertPoint(20.0, 125.0, runs[2].b) // second anchor plus the median's run of (10, 5)
        assertPoint(20.0, 110.0, runs[3].a)
        assertPoint(30.0, 115.0, runs[3].b)
    }

    @Test
    fun `a Schiff pitchfork halves the price to the base and leaves the time alone`() {
        val runs = DrawingGeometryA.schiffPitchfork(threeAnchors)
        assertEquals(4, runs.size)
        // The handle is directly above the first anchor, on the same bar: t is still 0.
        assertPoint(0.0, 107.5, runs[1].a)
        assertPoint(15.0, 115.0, runs[1].b)
        assertPoint(10.0, 120.0, runs[2].a)
        assertPoint(25.0, 127.5, runs[2].b) // the median's run here is (15, 7.5)
        assertPoint(20.0, 110.0, runs[3].a)
        assertPoint(35.0, 117.5, runs[3].b)
    }

    @Test
    fun `a modified Schiff pitchfork halves the time as well and so widens the fork`() {
        val runs = DrawingGeometryA.modifiedSchiffPitchfork(threeAnchors)
        assertEquals(4, runs.size)
        assertPoint(7.5, 107.5, runs[1].a) // halfway to the base in both axes
        assertPoint(15.0, 115.0, runs[1].b)
        assertPoint(10.0, 120.0, runs[2].a)
        assertPoint(17.5, 127.5, runs[2].b) // the median's run here is (7.5, 7.5)
        assertPoint(20.0, 110.0, runs[3].a)
        assertPoint(27.5, 117.5, runs[3].b)
    }

    @Test
    fun `classic inside Schiff and modified Schiff put the handle in four different places`() {
        // The point of the whole family. Same three anchors, four handles, and no two alike — if two
        // of these ever collided the app would be shipping the same fork under two names. The
        // classic handle is read off the pitchfan, which is the variant that keeps it untouched.
        val classic = DrawingGeometryA.pitchfan(threeAnchors)[1].a
        val inside = DrawingGeometryA.insidePitchfork(threeAnchors)[1].a
        val schiff = DrawingGeometryA.schiffPitchfork(threeAnchors)[1].a
        val modified = DrawingGeometryA.modifiedSchiffPitchfork(threeAnchors)[1].a

        assertPoint(0.0, 100.0, classic)
        assertPoint(5.0, 110.0, inside)
        assertPoint(0.0, 107.5, schiff)
        assertPoint(7.5, 107.5, modified)

        val handles = listOf(classic, inside, schiff, modified)
        assertEquals(4, handles.toSet().size)
        // Schiff and modified Schiff share a price and differ only in time; that is the distinction
        // that gets misread, so it is asserted as such rather than left to the set above.
        assertEquals(schiff.p, modified.p, DELTA)
        assertNotEquals(schiff.t, modified.t, DELTA)
        // Classic and Schiff share a time and differ only in price.
        assertEquals(classic.t, schiff.t, DELTA)
        assertNotEquals(classic.p, schiff.p, DELTA)
    }

    @Test
    fun `every pitchfork variant refuses two anchors`() {
        val two = listOf(anchor, second)
        assertTrue(DrawingGeometryA.insidePitchfork(two).isEmpty())
        assertTrue(DrawingGeometryA.schiffPitchfork(two).isEmpty())
        assertTrue(DrawingGeometryA.modifiedSchiffPitchfork(two).isEmpty())
        assertTrue(DrawingGeometryA.pitchfan(two).isEmpty())
    }

    // ── [10] pitchfan ─────────────────────────────────────────────────────────────────

    @Test
    fun `a pitchfan rays out of the classic handle through the base at the fan ratios`() {
        val runs = DrawingGeometryA.pitchfan(threeAnchors)
        assertEquals(14, runs.size) // base, median, and a pair per ratio
        assertPoint(10.0, 120.0, runs[0].a)
        assertPoint(20.0, 110.0, runs[0].b)
        assertPoint(0.0, 100.0, runs[1].a)
        assertPoint(15.0, 115.0, runs[1].b)
        assertEquals("0", runs[1].label)
        // The 0.25 pair sits a quarter of the way from the base's midpoint toward each anchor.
        assertPoint(0.0, 100.0, runs[2].a)
        assertPoint(13.75, 116.25, runs[2].b)
        assertEquals("0.25", runs[2].label)
        assertPoint(16.25, 113.75, runs[3].b)
        assertEquals("0.25", runs[3].label)
        // The pair at 1.0 is the outer rails and lands exactly on the two anchors.
        assertPoint(10.0, 120.0, runs[12].b)
        assertPoint(20.0, 110.0, runs[13].b)
        assertEquals("1", runs[12].label)
        assertTrue(runs.drop(1).all { it.a == anchor && it.extendB })
    }

    // ── [11] Fibonacci spiral ─────────────────────────────────────────────────────────

    @Test
    fun `a Fibonacci spiral grows each quarter by phi and starts the next where the last ended`() {
        val arcs = DrawingGeometryA.fibonacciSpiral(listOf(anchor, second), turns = 1)
        assertEquals(4, arcs.size)
        assertPoint(0.0, 100.0, arcs[0].centre)
        assertEquals(10.0, arcs[0].radiusT, DELTA)
        assertEquals(20.0, arcs[0].radiusP, DELTA)
        assertEquals(0.0, arcs[0].startDeg, DELTA) // the drag runs forward in time
        assertEquals(90.0, arcs[0].sweepDeg, DELTA) // and upward in price
        assertEquals("1", arcs[0].label)

        assertPoint(0.0, 87.63932022500211, arcs[1].centre)
        assertEquals(16.18033988749895, arcs[1].radiusT, DELTA)
        assertEquals(32.3606797749979, arcs[1].radiusP, DELTA)
        assertEquals(90.0, arcs[1].startDeg, DELTA)
        assertEquals("1.618", arcs[1].label)

        assertPoint(10.0, 87.63932022500211, arcs[2].centre)
        assertEquals(180.0, arcs[2].startDeg, DELTA)
        assertEquals("2.618", arcs[2].label)

        assertPoint(10.0, 120.0, arcs[3].centre)
        assertEquals(270.0, arcs[3].startDeg, DELTA)
        assertEquals("4.236", arcs[3].label)

        // Four turns is sixteen quarters, and the radii are a clean geometric run throughout.
        val long = DrawingGeometryA.fibonacciSpiral(listOf(anchor, second))
        assertEquals(16, long.size)
        for (index in 1 until long.size) {
            assertEquals(1.618033988749895, long[index].radiusT / long[index - 1].radiusT, DELTA)
            assertEquals(1.618033988749895, long[index].radiusP / long[index - 1].radiusP, DELTA)
        }
    }

    @Test
    fun `a spiral dragged downward winds the other way`() {
        val down = DrawingGeometryA.fibonacciSpiral(listOf(anchor, GeoPoint(-10.0, 80.0)), turns = 1)
        assertEquals(180.0, down[0].startDeg, DELTA)
        assertEquals(-90.0, down[0].sweepDeg, DELTA)
        assertEquals(10.0, down[0].radiusT, DELTA)
        assertEquals(20.0, down[0].radiusP, DELTA)
    }

    @Test
    fun `a spiral with no height and a spiral of no turns both draw nothing`() {
        assertTrue(DrawingGeometryA.fibonacciSpiral(listOf(anchor, GeoPoint(10.0, 100.0))).isEmpty())
        assertTrue(DrawingGeometryA.fibonacciSpiral(listOf(anchor, second), turns = 0).isEmpty())
        assertTrue(DrawingGeometryA.fibonacciSpiral(listOf(anchor)).isEmpty())
    }

    // ── [12] Fibonacci wedge ──────────────────────────────────────────────────────────

    @Test
    fun `a Fibonacci wedge shares one apex and sweeps the narrow angle between its rays`() {
        val arcs = DrawingGeometryA.fibonacciWedge(threeAnchors)
        assertEquals(6, arcs.size)
        assertTrue(arcs.all { it.centre == anchor })
        assertEquals(listOf("0.236", "0.382", "0.5", "0.618", "0.786", "1"), arcs.map { it.label })
        // The first ray sets the scale: at 1.0 the arc reaches the second anchor's own spans.
        assertEquals(10.0, arcs[5].radiusT, DELTA)
        assertEquals(20.0, arcs[5].radiusP, DELTA)
        assertEquals(2.36, arcs[0].radiusT, DELTA)
        assertEquals(4.72, arcs[0].radiusP, DELTA)
        assertEquals(5.0, arcs[2].radiusT, DELTA)
        assertEquals(10.0, arcs[2].radiusP, DELTA)
        // Every arc starts along the first ray and sweeps back to the second, the short way round.
        assertTrue(arcs.all { it.startDeg == arcs[0].startDeg && it.sweepDeg == arcs[0].sweepDeg })
        assertEquals(63.43494882292201, arcs[0].startDeg, DELTA)
        assertEquals(-36.86989764584402, arcs[0].sweepDeg, DELTA)
        assertTrue(arcs[0].sweepDeg > -180.0 && arcs[0].sweepDeg <= 180.0)
    }

    @Test
    fun `a wedge with a ray of no length draws nothing`() {
        assertTrue(DrawingGeometryA.fibonacciWedge(listOf(anchor, anchor, third)).isEmpty())
        assertTrue(DrawingGeometryA.fibonacciWedge(listOf(anchor, second)).isEmpty())
    }

    // ── [13]–[14] the Gann squares ────────────────────────────────────────────────────

    @Test
    fun `a Gann square scales all seven angles by the box the reader dragged`() {
        val runs = DrawingGeometryA.gannSquare(listOf(anchor, second))
        assertEquals(21, runs.size) // seven angles, four edges, ten grid lines

        assertEquals(listOf("1x1", "1x2", "2x1", "1x4", "4x1", "1x8", "8x1"), runs.take(7).map { it.label })
        assertTrue(runs.take(7).all { it.a == anchor && it.extendB })
        assertPoint(10.0, 120.0, runs[0].b) // 1×1 is the box's own diagonal
        assertPoint(10.0, 140.0, runs[1].b) // 1×2: one box of time, two of price
        assertPoint(20.0, 120.0, runs[2].b)
        assertPoint(10.0, 180.0, runs[3].b)
        assertPoint(40.0, 120.0, runs[4].b)
        assertPoint(10.0, 260.0, runs[5].b)
        assertPoint(80.0, 120.0, runs[6].b)

        // The four edges close the box and are bounded, unlike the angles.
        assertPoint(0.0, 100.0, runs[7].a)
        assertPoint(10.0, 100.0, runs[7].b)
        assertPoint(10.0, 120.0, runs[8].b)
        assertPoint(0.0, 120.0, runs[9].b)
        assertPoint(0.0, 100.0, runs[10].b)
        assertTrue(runs.drop(7).none { it.extendA || it.extendB })

        // The grid: a time line then a price line at each ratio below the far edge.
        assertEquals("0.236", runs[11].label)
        assertPoint(2.36, 100.0, runs[11].a)
        assertPoint(2.36, 120.0, runs[11].b)
        assertPoint(0.0, 104.72, runs[12].a)
        assertPoint(10.0, 104.72, runs[12].b)
        assertEquals("0.5", runs[15].label)
        assertPoint(5.0, 100.0, runs[15].a)
        assertPoint(0.0, 110.0, runs[16].a)
        assertEquals("0.786", runs[19].label)
        assertPoint(7.86, 100.0, runs[19].a)
        assertPoint(0.0, 115.72, runs[20].a)
    }

    @Test
    fun `a Gann square with a stated box is the same square as one dragged to that size`() {
        val dragged = DrawingGeometryA.gannSquare(listOf(anchor, second))
        val stated = DrawingGeometryA.gannSquareFixed(listOf(anchor), 10.0, 20.0)
        assertEquals(dragged, stated)
    }

    @Test
    fun `a Gann square squares downward when the box is negative`() {
        val runs = DrawingGeometryA.gannSquareFixed(listOf(anchor), -10.0, -20.0)
        assertPoint(-10.0, 80.0, runs[0].b) // the 1×1 follows the drag into the lower left
        assertPoint(-80.0, 80.0, runs[6].b)
        assertPoint(-5.0, 100.0, runs[15].a)
    }

    @Test
    fun `a flat Gann box and a Gann square with no anchor both draw nothing`() {
        assertTrue(DrawingGeometryA.gannSquare(listOf(anchor, GeoPoint(10.0, 100.0))).isEmpty())
        assertTrue(DrawingGeometryA.gannSquare(listOf(anchor)).isEmpty())
        assertTrue(DrawingGeometryA.gannSquareFixed(emptyList(), 10.0, 20.0).isEmpty())
        assertTrue(DrawingGeometryA.gannSquareFixed(listOf(anchor), 10.0, 0.0).isEmpty())
    }

    private companion object {
        /** Doubles compared as prices need a delta, and this one is far tighter than any pixel. */
        const val DELTA = 1e-9
    }
}
