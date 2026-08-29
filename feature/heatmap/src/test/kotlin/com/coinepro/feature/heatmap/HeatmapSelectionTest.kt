package com.coinepro.feature.heatmap

import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which markets a phone-sized map is allowed to leave out, and how the reader gets them back.
 *
 * The cap is the answer to the complaint this rework started from — a map of every market in the
 * catalogue is a wall of tiles too small to label, which is what «only writes some symbols' names»
 * described. What is asserted here is that leaving markets out is *ordered* rather than arbitrary,
 * that the order follows what the reader is currently sizing by, and that focusing a block reaches
 * past the cap rather than reshuffling under it.
 */
class HeatmapSelectionTest {

    private fun asset(symbol: String, volume: Double? = null) = HeatmapAsset(
        meta = SymbolClassifier.classify(symbol),
        price = 100.0,
        volume = volume,
    )

    private val market = listOf(
        asset("BTCUSDT", volume = 10.0),
        asset("ETHUSDT", volume = 90.0),
        asset("SOLUSDT", volume = 50.0),
        asset("XAUUSD"),
        asset("EURUSD"),
    )

    @Test
    fun `a small catalogue is drawn whole, because the cap is a ceiling and not a quota`() {
        val focused = HeatmapSelection.select(
            market,
            HeatmapOptions(size = HeatmapSize.VOLUME, density = HeatmapDensity.FOCUSED),
        )
        assertEquals(market.size, focused.size)
        assertEquals(listOf("ETHUSDT", "SOLUSDT", "BTCUSDT"), focused.take(3).map(HeatmapAsset::symbol))
    }

    @Test
    fun `a catalogue past the cap keeps the heaviest markets and drops the rest`() {
        // A real catalogue, in the sense that matters here: more markets than any density will
        // draw. What is asserted is not how many survive — that is a product decision the density
        // owns — but that nothing dropped outweighs anything kept, which is the property that makes
        // leaving markets out defensible at all.
        val many = (0 until 400).map { index ->
            asset(symbol = "BTCUSDT", volume = (400 - index).toDouble())
        }
        val drawn = HeatmapSelection.select(
            many,
            HeatmapOptions(size = HeatmapSize.VOLUME, density = HeatmapDensity.FOCUSED),
        )
        assertTrue("the cap did nothing", drawn.size < many.size)
        val dropped = many - drawn.toSet()
        val lightestKept = drawn.minOf { HeatmapMetrics.weightOf(it, HeatmapSize.VOLUME) }
        val heaviestDropped = dropped.maxOf { HeatmapMetrics.weightOf(it, HeatmapSize.VOLUME) }
        assertTrue("a dropped market outweighed a kept one", lightestKept >= heaviestDropped)
        // And heaviest first, so the caller can take a prefix of this list and still have the
        // largest markets — which is what the drill-down and the density control both do.
        val weights = drawn.map { HeatmapMetrics.weightOf(it, HeatmapSize.VOLUME) }
        assertEquals(weights.sortedDescending(), weights)
    }

    @Test
    fun `the order follows what the reader is sizing by, not a fixed ranking`() {
        // A map that answered "where is the money today" with the offline liquidity table would be
        // answering a different question from the one the control asks, and it would answer it
        // identically whichever sizing the reader picked — which is what the previous version of
        // this screen did for all four of them.
        val byRank = HeatmapSelection.select(market, HeatmapOptions(size = HeatmapSize.LIQUIDITY))
            .map(HeatmapAsset::symbol)
        val byVolume = HeatmapSelection.select(market, HeatmapOptions(size = HeatmapSize.VOLUME))
            .map(HeatmapAsset::symbol)
        assertEquals("ETHUSDT", byVolume.first())
        assertNotEquals("the two sizings produced the same map", byRank, byVolume)
        // The markets with no volume at all fall to the ranking, which puts them behind every
        // market that reported one rather than dropping them off the map.
        assertEquals(listOf("XAUUSD", "EURUSD").toSet(), byVolume.takeLast(2).toSet())
    }

    @Test
    fun `no cap at all is a supported answer`() {
        val everything = HeatmapSelection.select(
            market,
            HeatmapOptions(density = HeatmapDensity.EVERYTHING),
        )
        assertEquals(market.size, everything.size)
    }

    @Test
    fun `focusing a block is how the reader reaches past the cap`() {
        val crypto = HeatmapSelection.select(
            market,
            HeatmapOptions(grouping = HeatmapGrouping.BY_CLASS),
            focus = HeatmapBucket.Class(SymbolCategory.CRYPTO),
        )
        assertTrue(crypto.all { it.meta.category == SymbolCategory.CRYPTO })
        assertEquals(market.count { it.meta.category == SymbolCategory.CRYPTO }, crypto.size)
    }

    @Test
    fun `the quote cut puts a metal and a currency pair in one block`() {
        assertEquals(
            HeatmapBucket.Quote("USD"),
            HeatmapSelection.bucketOf(asset("XAUUSD"), HeatmapGrouping.BY_QUOTE),
        )
        assertEquals(
            HeatmapBucket.Quote("USD"),
            HeatmapSelection.bucketOf(asset("EURUSD"), HeatmapGrouping.BY_QUOTE),
        )
        assertEquals(
            HeatmapBucket.Quote("USDT"),
            HeatmapSelection.bucketOf(asset("BTCUSDT"), HeatmapGrouping.BY_QUOTE),
        )
    }

    @Test
    fun `an ungrouped map has one bucket, so a focus on it is the whole map`() {
        market.forEach {
            assertEquals(HeatmapBucket.None, HeatmapSelection.bucketOf(it, HeatmapGrouping.NONE))
        }
    }
}
