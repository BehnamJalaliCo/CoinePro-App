package com.coinepro.feature.heatmap

import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.symbols.SymbolClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turning bars into the figures a tile is drawn from.
 *
 * This is the arithmetic the whole feature was missing. Everything asserted here used to be absent
 * rather than wrong — the map had a `changePercent` field and nothing that could fill it — so what
 * these tests pin is not a regression in a calculation but the existence of one, plus the three
 * places where the honest answer is null and the tempting answer is a number.
 */
class HeatmapFactsTest {

    private val meta = SymbolClassifier.classify("BTCUSDT")

    private fun quote(price: Double) = MarketQuote(
        instrument = Instrument("BTCUSDT", "BTC", MarketType.CRYPTO),
        price = price,
        timestampEpochMillis = 0L,
    )

    private fun bar(open: Double, high: Double, low: Double, close: Double, volume: Double = 0.0) =
        OhlcBar(t = 0L, o = open, h = high, l = low, c = close, v = volume)

    /** A flat series of [count] bars, each one percent wide, so a median range is predictable. */
    private fun flatSeries(count: Int, close: Double = 100.0) = List(count) {
        bar(open = close, high = close * 1.005, low = close * 0.995, close = close)
    }

    @Test
    fun `the day's move is the live price against the previous close, not against the bar's own`() {
        // The last daily bar is still open and its close lags the socket. Taking the bar's close
        // would colour the tile from a figure that disagrees with the price the detail sheet shows
        // for the same market one press later.
        val bars = listOf(bar(90.0, 95.0, 88.0, 90.0), bar(90.0, 99.0, 90.0, 96.0))
        val asset = HeatmapFacts.assetOf(meta, quote(99.0), bars)!!
        assertEquals(10.0, asset.changePercent!!, 0.001)
        assertEquals(90.0, asset.previousClose!!, 0.0)
    }

    @Test
    fun `a market with one bar of history measures its move from that bar's open`() {
        val asset = HeatmapFacts.assetOf(meta, quote(110.0), listOf(bar(100.0, 112.0, 99.0, 108.0)))!!
        assertEquals(10.0, asset.changePercent!!, 0.001)
        assertNull("there is no yesterday to report", asset.previousClose)
    }

    @Test
    fun `the day's high and low are widened by the live price`() {
        // A market trading above its recorded high is making a new high, not sitting at a hundred
        // and ten percent of its range.
        val asset = HeatmapFacts.assetOf(meta, quote(120.0), listOf(bar(100.0, 112.0, 99.0, 108.0)))!!
        assertEquals(120.0, asset.dayHigh!!, 0.0)
        assertEquals(99.0, asset.dayLow!!, 0.0)
        assertEquals(100.0, HeatmapMetrics.valueOf(asset, HeatmapColour.RANGE)!!, 0.001)
    }

    @Test
    fun `a zero volume is treated as unreported rather than as a market that did not trade`() {
        // The MT5 forex side sends zero for every symbol. Believing it would present the entire
        // forex catalogue as the quietest markets of the day.
        val asset = HeatmapFacts.assetOf(meta, quote(100.0), listOf(bar(100.0, 101.0, 99.0, 100.0)))!!
        assertNull(asset.volume)
        assertNull(asset.turnover)
    }

    @Test
    fun `turnover is the traded quantity at the bar's typical price`() {
        val asset = HeatmapFacts.assetOf(
            meta,
            quote(100.0),
            listOf(bar(100.0, 110.0, 90.0, 100.0, volume = 50.0)),
        )!!
        assertEquals(50.0, asset.volume!!, 0.0)
        assertEquals(50.0 * 100.0, asset.turnover!!, 0.001)
    }

    @Test
    fun `a period figure needs the whole window, and answers null rather than a shorter one`() {
        val short = HeatmapFacts.assetOf(meta, quote(100.0), flatSeries(20), period = HeatmapPeriod.MONTH)!!
        assertNull("twenty bars cannot answer a thirty-bar question", short.periodPercent)

        val long = HeatmapFacts.assetOf(meta, quote(110.0), flatSeries(40), period = HeatmapPeriod.MONTH)!!
        assertEquals(10.0, long.periodPercent!!, 0.001)
    }

    @Test
    fun `the typical range is a median, so one violent day does not become the normal`() {
        val calm = flatSeries(30).toMutableList()
        // One day ten times as wide as the rest. A mean would be dragged by it and the market would
        // then read as calm for a month; a median ignores it, which is the point.
        calm[5] = bar(open = 100.0, high = 105.0, low = 95.0, close = 100.0)
        val asset = HeatmapFacts.assetOf(meta, quote(100.0), calm + bar(100.0, 103.0, 100.0, 100.0))!!
        assertEquals(1.0, asset.typicalVolatilityPercent!!, 0.01)
        assertTrue("today was wider than normal", HeatmapMetrics.valueOf(asset, HeatmapColour.VOLATILITY)!! > 0.0)
    }

    @Test
    fun `a short history has no normal at all, so volatility says nothing rather than guessing`() {
        val asset = HeatmapFacts.assetOf(meta, quote(100.0), flatSeries(6))!!
        assertNull(asset.typicalVolatilityPercent)
        assertNull(HeatmapMetrics.valueOf(asset, HeatmapColour.VOLATILITY))
    }

    @Test
    fun `a market with no bars keeps its price, reports nothing else, and says it is unresolved`() {
        val asset = HeatmapFacts.assetOf(meta, quote(100.0), emptyList())!!
        assertEquals(100.0, asset.price, 0.0)
        assertFalse(asset.resolved)
        HeatmapColour.entries.forEach { colour ->
            assertNull("$colour must not invent an answer", HeatmapMetrics.valueOf(asset, colour))
        }
    }

    @Test
    fun `a market with neither a quote nor a bar has no tile`() {
        assertNull(HeatmapFacts.assetOf(meta, quote = null, bars = emptyList()))
        // A bar is enough on its own: the catalogue price can be missing while the history is not.
        assertNotNull(HeatmapFacts.assetOf(meta, quote = null, bars = listOf(bar(1.0, 2.0, 1.0, 2.0))))
    }
}
