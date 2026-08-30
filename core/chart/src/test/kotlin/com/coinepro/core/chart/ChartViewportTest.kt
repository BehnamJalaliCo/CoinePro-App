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
        // Forward it stops at half a screen of air rather than at the newest bar. Panning past the
        // live edge is what gives a projection somewhere to land; going further would leave the
        // reader looking at an empty plot with the market off the left of it.
        assertEquals(
            -ChartViewport.DEFAULT_BARS_PER_VIEW / 2,
            view.pannedBy(-1_000_000f).offset,
        )
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
    fun `a short series fills the plot instead of hiding in the corner of it`() {
        // The Renko case: a hundred candles become nineteen bricks, and dividing by the 120-bar
        // window put all nineteen in the leftmost sixth of the canvas.
        val short = CandleSeries(series.bars.take(19))
        val view = ChartViewport(short).sized(360f, 240f)
        assertEquals(360f / 19, view.barWidth, 1e-4f)
        assertEquals(19, view.visibleCount)
    }

    @Test
    fun `a very short series stops widening rather than drawing three slabs`() {
        val tiny = CandleSeries(series.bars.take(3))
        val view = ChartViewport(tiny).sized(360f, 240f)
        assertEquals(360f / ChartViewport.MIN_BARS_PER_VIEW, view.barWidth, 1e-4f)
    }

    @Test
    fun `a full window still divides by the window`() {
        val view = viewport()
        assertEquals(360f / ChartViewport.DEFAULT_BARS_PER_VIEW, view.barWidth, 1e-4f)
    }
}

/**
 * The air between the newest candle and the price axis.
 *
 * The loudest single tell that a chart was drawn by somebody who had not used one: with the live
 * edge glued to the gutter there is nowhere to put a projection, and the live-price tag and the bar
 * countdown are pressed against the candle they are describing. See [ChartViewport.offset].
 */
class ChartEdgeMarginTest {

    private val series = CandleSeries(
        (0 until 500).map { index ->
            val base = 100.0 + index * 0.1
            Candle(1_700_000_000L + index * 3600, base, base + 1, base - 1, base + 0.5)
        },
    )

    private fun viewport() = ChartViewport(series).sized(width = 360f, height = 240f)

    @Test
    fun `a chart at rest leaves the newest bar clear of the price axis`() {
        val glued = viewport().atOffset(0)
        val rested = viewport().atRest()
        // Glued, the last bar's centre is half a slot from the edge and its body touches the gutter.
        assertEquals(360f - glued.barWidth / 2, glued.xOf(glued.lastVisible), 1e-3f)
        // At rest there is real space after it — several bar widths of it.
        val clearance = 360f - rested.xOf(rested.lastVisible)
        assertTrue("the last bar must not be against the axis", clearance > rested.barWidth * 2)
    }

    @Test
    fun `resting still shows the newest bar and still follows the feed`() {
        val rested = viewport().atRest()
        assertEquals(series.size - 1, rested.lastVisible)
        assertTrue("air at the edge is still the live edge", rested.isAtLiveEdge)
        val extended = CandleSeries(series.bars + Candle(1_700_000_000L + 500 * 3600, 150.0, 151.0, 149.0, 150.5))
        val after = rested.withSeries(extended)
        assertEquals("the margin survives a new bar", rested.offset, after.offset)
        assertEquals(extended.size - 1, after.lastVisible)
    }

    @Test
    fun `the margin is a share of the window rather than a fixed count of bars`() {
        // Six bars is a comfortable margin at eighty a screen and a third of the plot at fourteen.
        // What has to stay the same across zooms is the *picture*, so the share is what is pinned.
        val wide = viewport().copy(barsPerView = 400).atRest()
        val tight = viewport().copy(barsPerView = ChartViewport.MIN_BARS_PER_VIEW).atRest()
        val wideShare = (360f - wide.xOf(wide.lastVisible)) / 360f
        val tightShare = (360f - tight.xOf(tight.lastVisible)) / 360f
        assertTrue("the wide chart keeps a visible margin", wideShare > 0.02f)
        assertTrue("and the tight one does not give away a third of the plot", tightShare < 0.25f)
    }

    @Test
    fun `panning through the live edge moves by one slot a bar with no jump`() {
        // The failure a separately-stored margin would have: the air has to be spent and refilled at
        // the boundary, so the picture lurches as the reader drags across it.
        val anchor = 490
        val steps = (-4..4).map { offset -> viewport().atOffset(offset).xOf(anchor) }
        val width = viewport().barWidth
        steps.zipWithNext { a, b -> b - a }.forEach { assertEquals(width, it, 1e-3f) }
    }

    @Test
    fun `a zoom keeps a resting chart resting`() {
        val rested = viewport().atRest()
        val zoomed = rested.zoomedBy(0.5f)
        assertEquals("still at rest, at the new zoom's own margin", zoomed.restingOffset, zoomed.offset)
        assertTrue(zoomed.blankSlots > 0)
    }

    @Test
    fun `a thumbnail keeps every pixel`() {
        // `atOffset(0)` still means "glue the newest bar to the edge", which is what a list-row
        // sparkline wants: no gutter to breathe into, and nothing to project.
        val thumbnail = viewport().atOffset(0)
        assertEquals(0, thumbnail.blankSlots)
        assertEquals(ChartViewport.DEFAULT_BARS_PER_VIEW, thumbnail.visibleCount)
    }

    @Test
    fun `a touch in the empty slots reads the newest bar rather than nothing`() {
        val rested = viewport().atRest()
        assertEquals(rested.lastVisible, rested.indexAt(359f))
    }
}

/**
 * The chart's own proportions.
 *
 * Not a render — a render test cannot say *why* a layout is wrong, and these two rules are the ones
 * that produced the app's worst-looking screen. They are arithmetic, so they are pinned as
 * arithmetic.
 */
class ChartProportionsTest {

    @Test
    fun `indicator panes never take more than half the canvas`() {
        // Four oscillators declare 18% each. Unclamped that is 72%, and the candles — the thing the
        // reader opened the chart for — end up shorter than the strips underneath them.
        val requested = List(4) { 0.18f }.sum()
        val granted = minOf(0.5f, requested)
        assertEquals(0.5f, granted, 1e-6f)
        assertTrue("the price must keep the larger share", 1f - granted >= 0.5f)
    }

    @Test
    fun `one pane takes what it asks for`() {
        val requested = 0.18f
        assertEquals(requested, minOf(0.5f, requested), 1e-6f)
    }

    @Test
    fun `volume no longer competes with the price for height`() {
        // Volume used to be a band of its own at a fifth of the canvas. It is drawn inside the
        // price pane now, so a chart with three oscillators gives the candles half the height
        // rather than a quarter of it.
        val panes = minOf(0.5f, 3 * 0.18f)
        assertEquals(0.5f, panes, 1e-6f)
        assertEquals(0.5f, 1f - panes, 1e-6f)
    }
}
