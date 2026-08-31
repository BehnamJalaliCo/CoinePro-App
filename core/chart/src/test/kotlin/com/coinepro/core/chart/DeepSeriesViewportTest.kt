package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fifty thousand bars resident, and the promise that a frame still costs what is on screen.
 *
 * The owner wants fifty thousand candles loaded and five years of history behind them, for
 * back-testing. That is a memory decision — see `ChartHistory` — and it is only affordable if
 * *nothing per frame* is linear in what is loaded. `ChartViewport` was written that way from the
 * start; these are the tests that say so at the size the ceiling is being raised to, because a walk
 * over the whole series is the kind of thing that gets added later by somebody who only ever ran it
 * on three hundred bars.
 *
 * The technique throughout is a spike: an absurd price is planted far outside the visible window,
 * and anything that walked the whole series would find it.
 */
class DeepSeriesViewportTest {

    private val bars = 50_000

    private val deep = CandleSeries(
        (0 until bars).map { index ->
            val price = 100.0 + (index % 50)
            Candle(
                t = 1_400_000_000L + index * 3600L,
                o = price,
                // One bar near the oldest end, ten thousand times the rest. A price range that has
                // walked further than the window would take its ceiling from here.
                h = if (index == 25) 1_000_000.0 else price + 1,
                l = if (index == 25) 0.001 else price - 1,
                c = price,
                v = 5.0,
            )
        },
    )

    private fun atLiveEdge() = ChartViewport(deep).sized(1_000f, 600f).atRest()

    @Test
    fun `the visible window is the zoom, not the series`() {
        val view = atLiveEdge()
        assertEquals(ChartViewport.DEFAULT_BARS_PER_VIEW - view.blankSlots, view.visibleCount)
        assertTrue(view.firstVisible > bars - 200)
        assertEquals(bars - 1, view.lastVisible)
    }

    @Test
    fun `the price range is taken from the visible slice and nothing else`() {
        // The one per-frame walk on this object. At fifty thousand bars a walk over the series
        // would be fifty thousand comparisons a frame — and it would also draw the wrong picture,
        // which is the half that is visible: the spike at bar twenty-five would flatten every
        // candle on screen into a line at the bottom of the plot.
        val range = atLiveEdge().priceRange

        assertTrue("range reached the spike: $range", range.endInclusive < 1_000.0)
        assertTrue("range reached the spike: $range", range.start > 1.0)
    }

    @Test
    fun `panning to the spike does bring it into the range`() {
        // The opposite assertion, so the one above is measuring the window rather than a clamp.
        val onIt = ChartViewport(deep).sized(1_000f, 600f).atOffset(bars - 60)

        assertTrue(onIt.firstVisible <= 25 && 25 <= onIt.lastVisible)
        assertTrue(onIt.priceRange.endInclusive > 1_000.0)
    }

    @Test
    fun `a bar under a finger is found without walking to it`() {
        val view = atLiveEdge()
        val middle = view.firstVisible + view.visibleCount / 2

        assertEquals(middle, view.indexAt(view.xOf(middle)))
        assertEquals(deep.time[middle], view.timeAt(view.xOf(middle)))
        // And back the other way, which is what every drawing anchored in time goes through.
        assertEquals(view.xOf(middle), view.xOfTime(deep.time[middle]), 0.5f)
    }

    @Test
    fun `adopting a grown series keeps the reader on the same bar`() {
        // What a page-back does at this depth: five thousand bars arrive in front of the oldest one
        // and the reader must not move. The rebase is a binary search, not a scan.
        val view = ChartViewport(deep).sized(1_000f, 600f).atOffset(20_000)
        val anchor = deep.time[view.lastVisible]
        val older = (1 until 5_001).map { back ->
            val price = 90.0
            Candle(deep.time.first() - back * 3600L, price, price + 1, price - 1, price, 1.0)
        }.reversed()
        val grown = CandleSeries(older + deep.bars)

        val moved = view.withSeries(grown)

        assertEquals(anchor, grown.time[moved.lastVisible])
        assertEquals(view.visibleCount, moved.visibleCount)
    }

    @Test
    fun `a series already inside the ceiling is handed back untouched`() {
        // `resident` is called unconditionally on the reload path, so the ordinary chart has to pay
        // one comparison and allocate nothing — `ChartDerived` is keyed on series identity, and a
        // copy here would invalidate every indicator on every load for no reason at all.
        val shallow = CandleSeries(deep.bars.take(300))
        assertSame(shallow, shallow.resident(maxBars = 50_000))
    }
}
