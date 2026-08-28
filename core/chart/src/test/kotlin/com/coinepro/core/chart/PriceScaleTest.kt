package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stretching and compressing the price axis — the drag on the price gutter.
 *
 * The auto-fit range is right for reading a price and wrong for reading a shape. A market that has
 * moved half a percent all week fills the plot with noise; one that gapped ten percent on Monday
 * spends the rest of it as a flat line in the bottom third. This is the number that answers both,
 * and the properties worth pinning are that it is *centred* — the chart must not slide up the plot
 * as the reader drags — and that it is bounded at both ends.
 */
class PriceScaleTest {

    private val series = CandleSeries(
        (0 until 120).map { index ->
            val base = 1_000.0 + 40 * kotlin.math.sin(index * 2 * Math.PI / 30)
            Candle(1_700_000_000L + index * 3600, base, base + 5, base - 5, base + 1, 1.0)
        },
    )

    private fun viewport(log: Boolean = false) = ChartViewport(
        series = series,
        barsPerView = 120,
        plotWidth = 900f,
        plotHeight = 600f,
        logScale = log,
    )

    private fun midpoint(view: ChartViewport) =
        (view.priceRange.start + view.priceRange.endInclusive) / 2

    @Test
    fun `zooming out widens the range and zooming in narrows it`() {
        val base = viewport()
        val span = { v: ChartViewport -> v.priceRange.endInclusive - v.priceRange.start }
        assertTrue(span(base.priceZoomedBy(2f)) > span(base))
        assertTrue(span(base.priceZoomedBy(0.5f)) < span(base))
        // Exactly proportional, so a drag of the same length does the same thing wherever it
        // starts from.
        assertEquals(span(base) * 2, span(base.priceZoomedBy(2f)), 1e-6)
    }

    @Test
    fun `the middle of the range does not move`() {
        // The whole feel of the gesture. Widening about the wrong point slides the chart up or
        // down the plot as the reader drags, which reads as the chart running away from them.
        val base = viewport()
        listOf(0.5f, 1.5f, 3f).forEach { factor ->
            assertEquals(midpoint(base), midpoint(base.priceZoomedBy(factor)), 1e-6)
        }
    }

    @Test
    fun `on a log axis the geometric middle does not move`() {
        // Log space has its own centre, and widening about the arithmetic one would slide the
        // chart for exactly the same reason.
        val base = viewport(log = true)
        val geometric = { v: ChartViewport ->
            kotlin.math.sqrt(v.priceRange.start * v.priceRange.endInclusive)
        }
        listOf(0.5f, 2f).forEach { factor ->
            assertEquals(geometric(base), geometric(base.priceZoomedBy(factor)), 1e-6)
        }
    }

    @Test
    fun `it is bounded at both ends`() {
        val base = viewport()
        // A hundred drags in one direction cannot leave the chart a vertical or a horizontal line.
        var out = base
        repeat(100) { out = out.priceZoomedBy(2f) }
        assertEquals(ChartViewport.MAX_PRICE_ZOOM, out.priceZoom, 1e-6f)

        var into = base
        repeat(100) { into = into.priceZoomedBy(0.5f) }
        assertEquals(ChartViewport.MIN_PRICE_ZOOM, into.priceZoom, 1e-6f)
    }

    @Test
    fun `a nonsense factor is ignored rather than propagated`() {
        // A drag on a plot of zero height would produce these. `NaN` reaching `priceRange` makes
        // every price on the chart un-drawable, silently.
        val base = viewport()
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { factor ->
            assertEquals(base.priceZoom, base.priceZoomedBy(factor).priceZoom, 0f)
        }
    }

    @Test
    fun `auto puts it back and touches nothing else`() {
        val stretched = viewport().priceZoomedBy(3f).atOffset(20).copy(barsPerView = 60)
        val reset = stretched.autoPriceScale()
        assertEquals(1f, reset.priceZoom, 0f)
        // The horizontal position and zoom are a different axis and a different gesture.
        assertEquals(20, reset.offset)
        assertEquals(60, reset.barsPerView)
    }

    @Test
    fun `the default is exactly what the chart drew before this existed`() {
        // The guard against the feature having changed every chart in the app by arriving. At
        // zoom 1 the range has to be the old fit-plus-eight-percent, to the bit.
        val view = viewport()
        val low = series.low.min()
        val high = series.high.max()
        val padding = (high - low) * ChartViewport.PRICE_PADDING
        assertEquals(low - padding, view.priceRange.start, 1e-9)
        assertEquals(high + padding, view.priceRange.endInclusive, 1e-9)
    }
}
