package com.coinepro.feature.heatmap

import com.coinepro.core.symbols.SymbolClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two numbers behind a tile, and what happens when the feed does not supply them.
 *
 * The fallbacks are the reason this is worth testing rather than reading. The MT5 forex side sends
 * no volume at all and no market has a capitalisation anywhere in either backend, so on real data
 * the area fallback is not an edge case, it is the normal case, and it has to produce a map that is
 * coarse rather than wrong.
 *
 * The colour path has no fallback at all, and that asymmetry is the thing most worth pinning: a
 * missing figure must reach the tile as null so it draws as unknown, and must never be borrowed
 * from a neighbouring metric to make the map look complete.
 */
class HeatmapMetricsTest {

    private fun asset(
        symbol: String,
        price: Double = 100.0,
        change: Double? = 1.0,
        period: Double? = null,
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
        periodPercent = period,
        volatilityPercent = volatility,
        typicalVolatilityPercent = typicalVolatility,
        openPrice = open,
        previousClose = previousClose,
        dayHigh = high,
        dayLow = low,
        volume = volume,
        turnover = turnover,
    )

    @Test
    fun `the equal sizing gives every market the same weight whatever the feed sent`() {
        val large = asset("BTCUSDT", volume = 900_000.0)
        val small = asset("DOGEUSDT")
        assertEquals(
            HeatmapMetrics.weightOf(large, HeatmapSize.MONO),
            HeatmapMetrics.weightOf(small, HeatmapSize.MONO),
            0.0,
        )
    }

    @Test
    fun `a reported figure is used as it stands`() {
        val subject = asset("BTCUSDT", volume = 900_000.0, turnover = 4.5e10)
        assertEquals(900_000.0, HeatmapMetrics.weightOf(subject, HeatmapSize.VOLUME), 0.0)
        assertEquals(4.5e10, HeatmapMetrics.weightOf(subject, HeatmapSize.TURNOVER), 0.0)
    }

    @Test
    fun `turnover is derived from volume and price when the feed reports only the two`() {
        val subject = asset("ETHUSDT", price = 3_200.0, volume = 12_500.0)
        assertEquals(3_200.0 * 12_500.0, HeatmapMetrics.weightOf(subject, HeatmapSize.TURNOVER), 0.001)
    }

    @Test
    fun `the liquidity sizing is the ranking, and it needs nothing from the feed`() {
        val major = asset("BTCUSDT")
        val minor = asset("ALGOUSDT")
        val majorWeight = HeatmapMetrics.weightOf(major, HeatmapSize.LIQUIDITY)
        val minorWeight = HeatmapMetrics.weightOf(minor, HeatmapSize.LIQUIDITY)
        assertTrue("a ranked weight must still be positive", minorWeight > 0.0)
        assertTrue("Bitcoin should outweigh a mid-cap on the ranking", majorWeight > minorWeight)
    }

    @Test
    fun `a missing volume falls back to the liquidity ranking rather than to zero`() {
        val subject = asset("ALGOUSDT", volume = null)
        assertEquals(
            HeatmapMetrics.weightOf(subject, HeatmapSize.LIQUIDITY),
            HeatmapMetrics.weightOf(subject, HeatmapSize.VOLUME),
            0.0,
        )
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
    fun `the period colour does not borrow the day's move when it has no period figure`() {
        // This is the regression the whole rework exists to prevent. It used to fall back, so a
        // reader who chose "ninety days" could be shown today's change under a ninety-day label,
        // with nothing anywhere on the screen saying which one they were looking at.
        val dayOnly = asset("BTCUSDT", change = -2.5, period = null)
        assertEquals(-2.5, HeatmapMetrics.valueOf(dayOnly, HeatmapColour.CHANGE)!!, 0.0)
        assertNull(HeatmapMetrics.valueOf(dayOnly, HeatmapColour.PERFORMANCE))
        assertEquals(
            7.0,
            HeatmapMetrics.valueOf(dayOnly.copy(periodPercent = 7.0), HeatmapColour.PERFORMANCE)!!,
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

    @Test
    fun `a period ceiling is wider than a day's, so a quarter is not two colours`() {
        val (_, dayCeiling) = HeatmapMetrics.scaleBoundsOf(HeatmapColour.CHANGE)
        val (_, periodCeiling) = HeatmapMetrics.scaleBoundsOf(HeatmapColour.PERFORMANCE)
        assertTrue(periodCeiling > dayCeiling)
    }

    @Test
    fun `a mode nothing can answer reports itself unavailable, so the sheet can say so`() {
        val blank = listOf(asset("BTCUSDT", change = null), asset("EURUSD", change = null))
        assertFalse(HeatmapMetrics.anyValueFor(blank, HeatmapColour.CHANGE))
        // One market answering is enough. The rest draw as unknown, which is a fact about those
        // markets rather than about the mode.
        val partial = blank + asset("SOLUSDT", change = 2.0)
        assertTrue(HeatmapMetrics.anyValueFor(partial, HeatmapColour.CHANGE))
    }

    @Test
    fun `the two sizings that need nothing are always available and the two that need volume are not`() {
        val volumeless = listOf(asset("EURUSD", volume = null), asset("XAUUSD", volume = null))
        assertTrue(HeatmapMetrics.anyWeightFor(volumeless, HeatmapSize.LIQUIDITY))
        assertTrue(HeatmapMetrics.anyWeightFor(volumeless, HeatmapSize.MONO))
        assertFalse(HeatmapMetrics.anyWeightFor(volumeless, HeatmapSize.VOLUME))
        assertFalse(HeatmapMetrics.anyWeightFor(volumeless, HeatmapSize.TURNOVER))
        assertTrue(HeatmapMetrics.anyWeightFor(volumeless + asset("BTCUSDT", volume = 12.0), HeatmapSize.VOLUME))
    }
}
