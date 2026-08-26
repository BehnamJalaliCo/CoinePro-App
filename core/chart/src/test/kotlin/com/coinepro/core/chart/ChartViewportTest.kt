package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartViewportTest {

    private val series = CandleSeries(
        (0 until 500).map { index ->
            val base = 100.0 + index * 0.1
            Candle(
                t = 1_700_000_000L + index * 3600,
                o = base,
                h = base + 1,
                l = base - 1,
                c = base + 0.5,
            )
        },
    )

    private fun viewport() = ChartViewport(series).sized(width = 360f, height = 240f)

    @Test
    fun `a chart opens pinned to the live edge`() {
        val view = viewport()
        assertTrue(view.isAtLiveEdge)
        assertEquals(series.size - 1, view.lastVisible)
        assertEquals(ChartViewport.DEFAULT_BARS_PER_VIEW, view.visibleCount)
    }

    @Test
    fun `a new bar does not move the chart when the reader is watching the edge`() {
        val extended = CandleSeries(series.bars + Candle(1_700_000_000L + 500 * 3600, 150.0, 151.0, 149.0, 150.5))
        val following = viewport().withSeries(extended)
        assertTrue(following.isAtLiveEdge)
        assertEquals(extended.size - 1, following.lastVisible)
    }

    @Test
    fun `a new bar does not drag the chart when the reader has panned away`() {
        // The failure this prevents: reading history while the market prints, and having the view
        // shift by a bar every few seconds.
        val scrolled = viewport().atOffset(200)
        val anchorTime = series.time[scrolled.lastVisible]
        val extended = CandleSeries(series.bars + Candle(1_700_000_000L + 500 * 3600, 150.0, 151.0, 149.0, 150.5))
        val after = scrolled.withSeries(extended)
        assertFalse(after.isAtLiveEdge)
        assertEquals(anchorTime, extended.time[after.lastVisible])
    }

    @Test
    fun `panning is quantised to whole bars and cannot leave the data`() {
        val view = viewport()
        val panned = view.pannedBy(view.barWidth * 10)
        assertEquals(10, panned.offset)

        // Past the oldest bar there is nothing to show, so it stops.
        assertEquals(series.size - ChartViewport.MIN_BARS_PER_VIEW, view.pannedBy(1_000_000f).offset)
        // And it cannot pan into the future either.
        assertEquals(0, view.pannedBy(-1_000_000f).offset)
    }

    @Test
    fun `zoom is clamped at both ends`() {
        val view = viewport()
        assertEquals(ChartViewport.MIN_BARS_PER_VIEW, view.zoomedBy(1000f).barsPerView)
        assertEquals(ChartViewport.MAX_BARS_PER_VIEW, view.zoomedBy(0.001f).barsPerView)
    }

    @Test
    fun `zoom keeps the right edge fixed`() {
        // The live price must stay put while zooming — it is what the reader is looking at.
        val view = viewport()
        val zoomed = view.zoomedBy(2f)
        assertEquals(view.lastVisible, zoomed.lastVisible)
        assertTrue(zoomed.visibleCount < view.visibleCount)
    }

    @Test
    fun `screen and chart space round-trip`() {
        val view = viewport()
        for (index in view.firstVisible..view.lastVisible step 17) {
            assertEquals(index, view.indexAt(view.xOf(index)))
        }
        // The tolerance is Float's, not the arithmetic's: yOf returns a screen coordinate, and a
        // Float carries about seven significant digits — so a price near 120 round-trips to within
        // roughly a hundred-thousandth. That is far finer than a pixel, which is all this has to be.
        for (price in listOf(view.priceRange.start, view.priceRange.endInclusive, 120.0)) {
            assertEquals(price, view.priceAt(view.yOf(price)), 1e-3)
        }
    }

    @Test
    fun `price rises up the screen`() {
        val view = viewport()
        assertTrue("a higher price must sit higher", view.yOf(140.0) < view.yOf(110.0))
        assertEquals(0f, view.yOf(view.priceRange.endInclusive), 0.01f)
        assertEquals(view.plotHeight, view.yOf(view.priceRange.start), 0.01f)
    }

    @Test
    fun `the price range leaves headroom above the highest wick`() {
        val view = viewport()
        var highest = -Double.MAX_VALUE
        for (index in view.firstVisible..view.lastVisible) highest = maxOf(highest, series.high[index])
        assertTrue("the top wick must not touch the edge", view.priceRange.endInclusive > highest)
    }

    @Test
    fun `a time outside the loaded range extrapolates instead of clamping`() {
        // What lets a trend line drawn last week still reach today's right edge.
        val view = viewport()
        val beyond = series.time.last() + 20 * 3600
        assertTrue(view.xOfTime(beyond) > view.xOf(view.lastVisible))

        val before = series.time.first() - 20 * 3600
        assertTrue(view.xOfTime(before) < view.xOf(view.firstVisible))
    }

    @Test
    fun `an empty series produces a viewport that draws nothing rather than crashing`() {
        val empty = ChartViewport(CandleSeries.EMPTY).sized(360f, 240f)
        assertEquals(0, empty.visibleCount)
        assertEquals(-1, empty.lastVisible)
        assertEquals(0.0..1.0, empty.priceRange)
        // Every accessor still answers.
        empty.yOf(1.0)
        empty.xOfTime(0)
        empty.indexAt(10f)
    }

    @Test
    fun `a flat series still has a range to divide by`() {
        val flat = CandleSeries((0 until 50).map { Candle(it.toLong(), 100.0, 100.0, 100.0, 100.0) })
        val view = ChartViewport(flat).sized(360f, 240f)
        assertTrue(view.priceRange.endInclusive > view.priceRange.start)
        assertTrue(view.yOf(100.0).isFinite())
    }

    @Test
    fun `a series shorter than the window keeps its bar width`() {
        // Otherwise nine bars stretch across the whole canvas and then shrink when history loads.
        val short = CandleSeries(series.bars.take(9))
        val view = ChartViewport(short).sized(360f, 240f)
        assertEquals(360f / ChartViewport.DEFAULT_BARS_PER_VIEW, view.barWidth, 1e-4f)
        assertEquals(9, view.visibleCount)
    }
}
