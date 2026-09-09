package com.coinepro.core.chart

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind the seven chart types that used to draw as something else.
 *
 * The drawing itself cannot be asserted without a graphics context, so everything that *decides*
 * what is drawn is a pure function and lives here: what conflation keeps, how a profile's brackets
 * are sized, how many rows fit, and how a volume is written into a cell four characters wide.
 */
class ChartSeriesTypesTest {

    private fun emitted(minGap: Float, points: List<Pair<Float, Float>>): List<Pair<Float, Float>> {
        val out = mutableListOf<Pair<Float, Float>>()
        val conflator = ColumnConflator(minGap) { x, y -> out += x to y }
        points.forEach { (x, y) -> conflator.add(x, y) }
        conflator.flush()
        return out
    }

    @Test
    fun `with conflation off every point is drawn`() {
        val points = List(20) { it * 0.1f to it.toFloat() }
        assertEquals(points, emitted(minGap = 0f, points = points))
    }

    @Test
    fun `points inside the same sliver of a column are merged`() {
        // Twenty bars landing in two pixels, which is what a chart panned out to a thousand bars
        // hands the path builder.
        val points = List(20) { it * 0.1f to (it % 3).toFloat() }
        val out = emitted(minGap = CONFLATION_GAP_PX, points = points)
        assertTrue("merging must remove work", out.size < points.size)
    }

    @Test
    fun `a spike inside a merged column survives it`() {
        // The whole reason a column keeps its extremes rather than its last point: the spike is
        // what a reader zoomed out is looking for.
        val points = listOf(0f to 50f, 0.1f to 50f, 0.2f to 4f, 0.3f to 96f, 0.4f to 50f)
        val out = emitted(minGap = CONFLATION_GAP_PX, points = points)
        val ys = out.map { it.second }
        assertTrue("the low must survive", ys.contains(4f))
        assertTrue("the high must survive", ys.contains(96f))
        assertEquals("the run still starts where it started", 50f, ys.first(), 0f)
        assertEquals("and ends where it ended", 50f, ys.last(), 0f)
    }

    @Test
    fun `a column is drawn in the order the price reached its extremes`() {
        val down = emitted(0.5f, listOf(0f to 10f, 0.1f to 2f, 0.2f to 90f)).map { it.second }
        val up = emitted(0.5f, listOf(0f to 10f, 0.1f to 90f, 0.2f to 2f)).map { it.second }
        assertTrue("low first when it fell first", down.indexOf(2f) < down.indexOf(90f))
        assertTrue("high first when it rose first", up.indexOf(90f) < up.indexOf(2f))
    }

    @Test
    fun `a new column starts once the points are far enough apart`() {
        val out = emitted(0.5f, listOf(0f to 1f, 10f to 2f, 20f to 3f))
        assertEquals(listOf(0f to 1f, 10f to 2f, 20f to 3f), out)
    }

    @Test
    fun `a volume is written in Latin digits whatever the device locale is`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale("fa", "IR"))
            assertEquals("1.2K", compactVolume(1_234.0))
            assertEquals("3.5M", compactVolume(3_500_000.0))
            assertEquals("2.0B", compactVolume(2_000_000_000.0))
            assertEquals("48", compactVolume(48.0))
            assertEquals("0.40", compactVolume(0.4))
            assertTrue(
                "no Persian digits may reach a footprint cell",
                compactVolume(1_234.0).none { it in '۰'..'۹' },
            )
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `a bracket is the reader's minutes converted by the bar's own length`() {
        // Half-hour brackets on a five-minute chart are six bars each.
        assertEquals(6, tpoBracketBars(visibleCount = 120, barSeconds = 300, bracketMinutes = 30))
        // And on an hourly chart half an hour cannot be less than one bar.
        assertEquals(1, tpoBracketBars(visibleCount = 120, barSeconds = 3_600, bracketMinutes = 30))
    }

    @Test
    fun `without an answer the window is divided into a readable number of brackets`() {
        val bars = tpoBracketBars(visibleCount = 260, barSeconds = 0, bracketMinutes = null)
        assertTrue("a bracket is always at least a bar", bars >= 1)
        assertTrue("and the profile stays roughly a session wide", 260 / bars <= TPO_TARGET_BRACKETS + 1)
        // A window shorter than the target gets a bracket per bar rather than a fraction of one.
        assertEquals(1, tpoBracketBars(visibleCount = 5, barSeconds = 0, bracketMinutes = null))
    }

    @Test
    fun `rows are capped by the height they have to fit into`() {
        assertEquals("sixty-four rows do not fit in ninety pixels", 9, legibleRows(64, 90f, 10f))
        assertEquals("and a generous plot keeps what was asked for", 12, legibleRows(12, 900f, 10f))
        assertTrue("a row count is never zero", legibleRows(64, 2f, 10f) >= 1)
    }

    @Test
    fun `the bar interval is read off the newest pair of bars`() {
        val bars = List(5) { Candle(t = 1_700_000_000L + it * 900L, o = 1.0, h = 2.0, l = 0.5, c = 1.5) }
        assertEquals(900L, barIntervalSeconds(CandleSeries(bars)))
        assertEquals("a series too short to have one says so", 0L, barIntervalSeconds(CandleSeries(bars.take(1))))
    }

    @Test
    fun `the area fill is a ramp rather than one flat value`() {
        // The defect was a single `AREA_ALPHA = 0.16f` at both ends, which reads as printed rather
        // than lit. The ramp is TradingView's own and the gate allows it for this file alone.
        assertNotEquals(AREA_ALPHA_TOP, AREA_ALPHA_BOTTOM)
        assertTrue("strongest at the line", AREA_ALPHA_TOP > AREA_ALPHA_BOTTOM)
        assertTrue("and never opaque enough to hide a candle", AREA_ALPHA_TOP < 0.5f)
    }
}
