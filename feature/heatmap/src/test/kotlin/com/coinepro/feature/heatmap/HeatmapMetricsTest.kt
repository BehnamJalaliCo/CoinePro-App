package com.coinepro.feature.heatmap

import com.coinepro.core.symbols.SymbolClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two numbers behind a tile, and what happens when the feed does not supply them.
 *
 * The fallbacks are the reason this is worth testing rather than reading. Neither backend sends a
 * capitalisation and the MT5 side sends no volume at all, so on the app's own data every sizing
 * except the equal one goes down the fallback path — which means the fallback is not an edge case
 * here, it is the normal case, and it has to produce a map that is coarse rather than wrong.
 */
class HeatmapMetricsTest {

    private fun asset(
        symbol: String,
        price: Double = 100.0,
        change: Double? = 1.0,
        marketCap: Double? = null,
        volume: Double? = null,
        turnover: Double? = null,
        high: Double? = null,
        low: Double? = null,
        open: Double? = null,
        previousClose: Double? = null,
        volatility: Double? = null,
        typicalVolatility: Double? = null,
    ) = HeatmapAsset(
        meta = SymbolClassifier.classify(symbol),
        price = price,
        changePercent = change,
        volatilityPercent = volatility,
        typicalVolatilityPercent = typicalVolatility,
        openPrice = open,
        previousClose = previousClose,
        dayHigh = high,
        dayLow = low,
        marketCap = marketCap,
        volume = volume,
        turnover = turnover,
    )

    @Test
    fun `the equal sizing gives every market the same weight whatever the feed sent`() {
        val large = asset("BTCUSDT", marketCap = 1.2e12, volume = 900_000.0)
        val small = asset("DOGEUSDT")
        assertEquals(
            HeatmapMetrics.weightOf(large, HeatmapSize.MONO),
            HeatmapMetrics.weightOf(small, HeatmapSize.MONO),
            0.0,
        )
    }

    @Test
    fun `a reported figure is used as it stands`() {
        val subject = asset("BTCUSDT", marketCap = 1.2e12, volume = 900_000.0, turnover = 4.5e10)
        assertEquals(1.2e12, HeatmapMetrics.weightOf(subject, HeatmapSize.MARKET_CAP), 0.0)
        assertEquals(900_000.0, HeatmapMetrics.weightOf(subject, HeatmapSize.VOLUME), 0.0)
        assertEquals(4.5e10, HeatmapMetrics.weightOf(subject, HeatmapSize.TURNOVER), 0.0)
    }

    @Test
    fun `turnover is derived from volume and price when the feed reports only the two`() {
        val subject = asset("ETHUSDT", price = 3_200.0, volume = 12_500.0)
        assertEquals(3_200.0 * 12_500.0, HeatmapMetrics.weightOf(subject, HeatmapSize.TURNOVER), 0.001)
    }

    @Test
    fun `a missing figure falls back to the liquidity ranking rather than to zero`() {
        val major = asset("BTCUSDT")
        val minor = asset("ALGOUSDT")
        val majorWeight = HeatmapMetrics.weightOf(major, HeatmapSize.MARKET_CAP)
        val minorWeight = HeatmapMetrics.weightOf(minor, HeatmapSize.MARKET_CAP)
        assertTrue("a fallback weight must still be positive", minorWeight > 0.0)
        assertTrue("Bitcoin should outweigh a mid-cap on the ranking", majorWeight > minorWeight)
    }

    @Test
    fun `a figure that is zero, negative or not a number is not a figure`() {
        // A feed that answers zero volume for a market it is quoting has told us nothing, and
        // taking it literally would remove the market from the map. The ranking answers instead.
        val zeroed = asset("BTCUSDT", volume = 0.0)
        val nonsense = asset("BTCUSDT", volume = Double.NaN)
        val expected = HeatmapMetrics.weightOf(asset("BTCUSDT"), HeatmapSize.VOLUME)
        assertEquals(expected, HeatmapMetrics.weightOf(zeroed, HeatmapSize.VOLUME), 0.0)
        assertEquals(expected, HeatmapMetrics.weightOf(nonsense, HeatmapSize.VOLUME), 0.0)
    }

    @Test
    fun `the change colour plots the session change and the performance colour falls back to it`() {
        val subject = asset("BTCUSDT", change = -2.5)
        assertEquals(-2.5, HeatmapMetrics.valueOf(subject, HeatmapColour.CHANGE)!!, 0.0)
        assertEquals(-2.5, HeatmapMetrics.valueOf(subject, HeatmapColour.PERFORMANCE)!!, 0.0)
        assertEquals(
            7.0,
            HeatmapMetrics.valueOf(subject.copy(periodPercent = 7.0), HeatmapColour.PERFORMANCE)!!,
            0.0,
        )
    }

    @Test
    fun `the range colour reads plus one hundred at the high and minus one hundred at the low`() {
        val high = asset("BTCUSDT", price = 120.0, high = 120.0, low = 100.0)
        val low = high.copy(price = 100.0)
        val middle = high.copy(price = 110.0)
        assertEquals(100.0, HeatmapMetrics.valueOf(high, HeatmapColour.RANGE)!!, 0.001)
        assertEquals(-100.0, HeatmapMetrics.valueOf(low, HeatmapColour.RANGE)!!, 0.001)
        assertEquals(0.0, HeatmapMetrics.valueOf(middle, HeatmapColour.RANGE)!!, 0.001)
    }

    @Test
    fun `a session with no range has no position inside it`() {
        // A closed market over a weekend reports a high equal to its low. The midpoint would be a
        // claim about where it is trading, and it is not trading.
        val flat = asset("EURUSD", price = 1.08, high = 1.08, low = 1.08)
        assertNull(HeatmapMetrics.valueOf(flat, HeatmapColour.RANGE))
    }

    @Test
    fun `the gap colour is the open against the previous close`() {
        val gapped = asset("XAUUSD", open = 2_040.0, previousClose = 2_000.0)
        assertEquals(2.0, HeatmapMetrics.valueOf(gapped, HeatmapColour.GAP)!!, 0.001)
        assertNull(HeatmapMetrics.valueOf(asset("XAUUSD"), HeatmapColour.GAP))
    }

    @Test
    fun `the volatility colour is the excess over this instrument's own normal, so it has a sign`() {
        val agitated = asset("BTCUSDT", volatility = 6.0, typicalVolatility = 3.5)
        val calm = asset("BTCUSDT", volatility = 1.5, typicalVolatility = 3.5)
        assertEquals(2.5, HeatmapMetrics.valueOf(agitated, HeatmapColour.VOLATILITY)!!, 0.001)
        assertEquals(-2.0, HeatmapMetrics.valueOf(calm, HeatmapColour.VOLATILITY)!!, 0.001)
        // Without a normal to compare against there is no excess, and a raw range on a diverging
        // ramp would paint every market as a gain.
        assertNull(HeatmapMetrics.valueOf(asset("BTCUSDT", volatility = 6.0), HeatmapColour.VOLATILITY))
    }

    @Test
    fun `the place-in-range scale is fixed, because it is already a percentage of the range`() {
        assertEquals(100.0 to 100.0, HeatmapMetrics.scaleBoundsOf(HeatmapColour.RANGE))
        assertEquals(100.0, HeatmapMetrics.scaleFor(listOf(4.0, -9.0), HeatmapColour.RANGE), 0.0)
        // A session change, by contrast, is normalised against the day the reader is looking at.
        assertEquals(9.0, HeatmapMetrics.scaleFor((1..10).map { it.toDouble() }, HeatmapColour.CHANGE), 0.0)
    }
}
