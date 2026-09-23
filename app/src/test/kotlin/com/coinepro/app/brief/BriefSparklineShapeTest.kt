package com.coinepro.app.brief

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line on the brief, as geometry.
 *
 * No canvas, no Robolectric, no bitmap. Everything asserted here is a decision the picture could
 * get wrong; the painting that follows it has nothing left to decide. See [BriefSparklineShape] for
 * why the split exists.
 */
class BriefSparklineShapeTest {

    private fun series(vararg closes: Double): CandleSeries = CandleSeries(
        closes.mapIndexed { index, close ->
            Candle(
                t = 1_700_000_000L + index * 3_600L,
                o = close,
                h = close + 1.0,
                l = close - 1.0,
                c = close,
                v = 1.0,
            )
        },
    )

    @Test
    fun `no series is no line`() {
        assertNull(BriefSparklineShape.of(null))
    }

    @Test
    fun `one close is no line`() {
        // A dot in a notification reads as a broken image, and a single bar is a dot.
        assertNull(BriefSparklineShape.of(series(100.0)))
    }

    @Test
    fun `two closes are enough`() {
        assertNotNull(BriefSparklineShape.of(series(100.0, 101.0)))
    }

    @Test
    fun `the box is filled end to end`() {
        val shape = BriefSparklineShape.of(series(100.0, 105.0, 110.0))!!
        assertEquals(0f, shape.points.first().x)
        assertEquals(1f, shape.points.last().x)
    }

    @Test
    fun `price runs up the box, not down it`() {
        // The one place the canvas's downward y and a chart's upward one are reconciled. Drawn the
        // other way a reader would take the picture for a different night.
        val shape = BriefSparklineShape.of(series(100.0, 110.0))!!
        assertEquals(1f, shape.points.first().y)
        assertEquals(0f, shape.points.last().y)
    }

    @Test
    fun `a perfectly flat night draws, centred`() {
        // Flat is a fact about the market. Refusing to render it would look like a failure to fetch.
        val shape = BriefSparklineShape.of(series(100.0, 100.0, 100.0))!!
        assertTrue(shape.rising)
        assertTrue(shape.points.all { it.y == 0.5f })
    }

    @Test
    fun `the direction is first against last, not the final bar`() {
        // A night that fell all the way and bounced on the last bar is a night that fell, and the
        // sentence above the picture says so.
        assertFalse(BriefSparklineShape.of(series(110.0, 104.0, 100.0, 101.0))!!.rising)
        assertTrue(BriefSparklineShape.of(series(100.0, 106.0, 110.0, 109.0))!!.rising)
    }

    @Test
    fun `only the last bars are carried`() {
        val shape = BriefSparklineShape.of(series(*DoubleArray(100) { 100.0 + it }), bars = 24)!!
        assertEquals(24, shape.points.size)
    }

    @Test
    fun `a non-finite close is dropped rather than plotted at zero`() {
        val shape = BriefSparklineShape.of(series(100.0, Double.NaN, 110.0))!!
        assertEquals(2, shape.points.size)
    }

    @Test
    fun `a series of nothing but non-finite closes is no line`() {
        assertNull(BriefSparklineShape.of(series(Double.NaN, Double.NaN)))
    }
}
