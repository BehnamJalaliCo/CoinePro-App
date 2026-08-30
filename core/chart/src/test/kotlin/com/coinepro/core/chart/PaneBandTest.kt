package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What an indicator pane's pixels mean.
 *
 * The defect these pin: the crosshair reported `(index, price)` and the price came from
 * [ChartViewport.priceAt], which describes the price plot and nothing else. A finger inside an RSI
 * strip is *below* the price plot, so the fraction came out negative, the price came out under the
 * low of the visible range, and the axis printed it — clamped to the bottom of the gutter. The
 * reader touched an oscillator reading 62 and the chart answered with a price nobody had quoted.
 */
class PaneBandTest {

    private val rsi = ChartPane(
        title = "RSI 14",
        lines = listOf(
            ChartLine(
                values = Line.of(10) { 30.0 + it * 5.0 },
                colour = 0xFF00FF00,
                label = "RSI",
            ),
        ),
        levels = listOf(PriceLevel(30.0, 0xFF888888), PriceLevel(70.0, 0xFF888888)),
    )

    private fun band(top: Float = 200f, height: Float = 80f) =
        paneBandOf(rsi, first = 0, last = 9, top = top, height = height)

    @Test
    fun `a pane resolves to its own extremes and not the price's`() {
        val band = band()
        assertNotNull(band)
        requireNotNull(band)
        assertEquals(30.0, band.low, 1e-9)
        assertEquals(75.0, band.high, 1e-9)
        // The breathing room the pane draws with, so a line never touches the lid.
        assertTrue(band.bottom < band.low)
        assertTrue(band.ceiling > band.high)
    }

    @Test
    fun `a pixel inside the strip reads the pane's own value`() {
        val band = requireNotNull(band())
        // The top of the strip is the ceiling, the bottom is the floor, and the middle is halfway
        // between the two — which is the reading the crosshair prints.
        assertEquals(band.ceiling, band.valueAt(200f), 1e-6)
        assertEquals(band.bottom, band.valueAt(280f), 1e-6)
        assertEquals((band.ceiling + band.bottom) / 2, band.valueAt(240f), 1e-6)
    }

    @Test
    fun `the two conversions are exact inverses`() {
        // The same pairing `ChartViewport` holds between yOf and priceAt, for the same reason: a
        // reading taken by a finger has to land back on the pixel the finger was on.
        val band = requireNotNull(band())
        // A pixel is a Float, so the round trip is exact to the precision a pixel can carry — which
        // is the guarantee that matters: the reading is the one the reader is pointing at.
        listOf(32.0, 50.0, 68.0, 74.0).forEach { value ->
            assertEquals(value, band.valueAt(band.yOf(value)), 1e-3)
        }
    }

    @Test
    fun `a strip only claims the pixels it occupies`() {
        val band = requireNotNull(band(top = 200f, height = 80f))
        assertFalse("the price plot above is not the pane's", band.contains(199f))
        assertTrue(band.contains(200f))
        assertTrue(band.contains(280f))
        assertFalse("nor is the time axis below it", band.contains(281f))
    }

    @Test
    fun `a histogram keeps zero on its scale`() {
        // A run of positive MACD would otherwise draw as bars hanging off the bottom edge with no
        // baseline to hang from.
        val macd = ChartPane(
            title = "MACD",
            histogram = ChartLine(Line.of(5) { 0.4 + it * 0.1 }, colour = 0xFF00FF00),
        )
        val band = requireNotNull(paneBandOf(macd, 0, 4, top = 0f, height = 100f))
        assertTrue("zero is inside the scale", band.bottom <= 0.0 && band.ceiling >= 0.0)
    }

    @Test
    fun `a pane with nothing in it resolves to nothing rather than to a flat scale`() {
        // The failure it prevents is the one the empty price axis prevents: a strip drawn against a
        // scale invented from no data says something about a study that has not produced a value.
        val empty = ChartPane(title = "EMPTY", lines = listOf(ChartLine(Line.empty(0), 0xFF00FF00)))
        assertNull(paneBandOf(empty, 0, -1, top = 0f, height = 60f))
    }

    @Test
    fun `a flat pane still has a span to divide by`() {
        val flat = ChartPane(
            title = "FLAT",
            lines = listOf(ChartLine(Line.of(6) { 50.0 }, 0xFF00FF00)),
        )
        val band = requireNotNull(paneBandOf(flat, 0, 5, top = 0f, height = 60f))
        assertTrue(band.ceiling > band.bottom)
        assertTrue(band.valueAt(30f).isFinite())
    }

    @Test
    fun `the reading is printed at the pane's own precision`() {
        // An RSI spanning forty points needs none of a price's decimals, and a MACD spanning four
        // thousandths needs more. Reusing the price rule prints "0.00" for both edges of a MACD.
        val macd = ChartPane(
            title = "MACD",
            lines = listOf(ChartLine(Line.from(listOf(-0.002, 0.0, 0.002)), 0xFF00FF00)),
        )
        // An RSI spanning forty-five points reads to one decimal; a MACD spanning four thousandths
        // needs four, and a shared rule would print "0.00" for both edges of it.
        assertEquals(1, requireNotNull(band()).decimals)
        assertTrue(requireNotNull(paneBandOf(macd, 0, 2, 0f, 60f)).decimals >= 4)
    }
}
