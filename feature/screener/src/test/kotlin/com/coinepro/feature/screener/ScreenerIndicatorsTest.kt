package com.coinepro.feature.screener

import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.symbols.SymbolClassifier
import com.coinepro.feature.screener.model.NumericOp
import com.coinepro.feature.screener.model.ScreenerFilter
import com.coinepro.feature.screener.model.ScreenerIndicatorId
import com.coinepro.feature.screener.model.ScreenerRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reduction from a chart's series to a screener's single number — [109].
 *
 * The arithmetic itself belongs to `core:chart` and is tested there. What is tested here is
 * everything around it: that the *last* reading is taken and not the first present one, that a
 * series too short to warm an indicator up answers null rather than a number, that the readings
 * which would otherwise be in the instrument's own units are normalised so one threshold can be
 * typed across a mixed catalogue, and that a key round-trips back into an id and a period.
 */
class ScreenerIndicatorsTest {

    private fun rising(count: Int) = List(count) { index ->
        val close = 100.0 + index
        OhlcBar(t = index * 86_400L, o = close, h = close + 1.0, l = close - 1.0, c = close, v = 1.0)
    }

    private fun flat(count: Int, price: Double = 100.0) = List(count) { index ->
        OhlcBar(t = index * 86_400L, o = price, h = price + 1.0, l = price - 1.0, c = price, v = 1.0)
    }

    private fun falling(count: Int) = List(count) { index ->
        val close = 180.0 - index
        OhlcBar(t = index * 86_400L, o = close, h = close + 1.0, l = close - 1.0, c = close, v = 1.0)
    }

    @Test
    fun `a series that has only risen reads at the top of the RSI scale`() {
        val value = ScreenerIndicators.compute(ScreenerIndicatorId.RSI, 14, rising(40))
        assertEquals(100.0, value!!, 0.01)
    }

    @Test
    fun `a flat series reads at the bottom of the RSI scale`() {
        val value = ScreenerIndicators.compute(ScreenerIndicatorId.RSI, 14, flat(40))
        assertEquals(0.0, value!!, 0.01)
    }

    @Test
    fun `a series too short to compute anything answers null rather than a number`() {
        // Below thirty bars every reading here is either warm-up or noise, and a number a filter
        // would then rank a market by is worse than no number.
        assertNull(ScreenerIndicators.compute(ScreenerIndicatorId.RSI, 14, rising(10)))
    }

    @Test
    fun `an indicator this build does not know answers null`() {
        assertNull(ScreenerIndicators.compute("no_such_indicator", 10, rising(40)))
    }

    @Test
    fun `an indicator that only the chart used to know is now a filter too`() {
        // The whole of [115]: SuperTrend, the Stochastic RSI and the Aroon are in the chart's
        // catalogue and were unreachable from the screener, which offered eight ids written by
        // hand. Each of them reduces to a real number on a fixture that can be reasoned about.
        val bars = rising(120)
        val trend = ScreenerIndicators.compute("supertrend", null, bars)
        assertNotNull("a price-scale indicator reduces to a distance", trend)
        // A series that has only risen sits above a trailing stop that follows it up.
        assertTrue(trend!! > 0.0)

        val stochRsi = ScreenerIndicators.compute("stochrsi", 14, bars)
        assertNotNull(stochRsi)
        assertTrue("a stochastic reading is bounded", stochRsi!! in 0.0..100.0)

        val aroon = ScreenerIndicators.compute("aroon", 14, bars)
        assertEquals("a market making new highs every bar reads Aroon Up at a hundred", 100.0, aroon!!, 0.01)
    }

    @Test
    fun `an indicator filter scores a known fixture and bites at the threshold`() {
        // The end-to-end shape of [109]: a fixture, a reading, and a condition that admits one
        // market and refuses the other. Nothing here is mocked — this is the arithmetic the screen
        // runs, through the key the filter addresses it by.
        val climbing = ScreenerRow(meta = bitcoin, price = 139.0, indicators = readingsFor(rising(120)))
        val stalled = ScreenerRow(meta = bitcoin, price = 100.0, indicators = readingsFor(flat(120)))
        val sliding = ScreenerRow(meta = bitcoin, price = 61.0, indicators = readingsFor(falling(120)))
        val overbought = ScreenerFilter.IndicatorFilter(
            indicatorId = ScreenerIndicatorId.RSI,
            period = 14,
            op = NumericOp.GT,
            value = 70.0,
        )
        assertTrue(overbought.matches(climbing))
        assertFalse(overbought.matches(stalled))
        // And a catalogue-only indicator, which is what [115] added. Aroon Up counts bars since
        // the window's high: a market making new highs reads a hundred, one that has been falling
        // for a fortnight reads zero, and the condition separates them.
        val trending = ScreenerFilter.IndicatorFilter("aroon", period = 14, op = NumericOp.GTE, value = 90.0)
        assertTrue(trending.matches(climbing))
        assertFalse(trending.matches(sliding))
    }

    @Test
    fun `a volume study is withheld on a feed that reports no volume`() {
        // The `hasVolume` convention `ChartCatalog` states: zero is not the same claim as absent. A
        // money-flow index over a column nobody sent is not «no money flowing», and on a screener
        // it would rank four hundred markets by a number that was never reported.
        val withVolume = rising(120)
        val withoutVolume = withVolume.map { it.copy(v = 0.0) }

        assertNotNull(ScreenerIndicators.compute("mfi", 14, withVolume))
        assertNull(ScreenerIndicators.compute("mfi", 14, withoutVolume))
        assertNull(ScreenerIndicators.compute("obv", null, withoutVolume))
        // And the reading that needs no volume is unaffected on the same bars.
        assertNotNull(ScreenerIndicators.compute(ScreenerIndicatorId.RSI, 14, withoutVolume))
    }

    /** Classified rather than hand-built, so the fixture is a market the app would really produce. */
    private val bitcoin = SymbolClassifier.classify("BTCUSDT")

    /** Every reading a test asks about, computed the way the controller computes them. */
    private fun readingsFor(bars: List<OhlcBar>): Map<String, Double> =
        ScreenerIndicators.computeAll(setOf("rsi:14", "aroon:14"), bars)

    @Test
    fun `the average distance is a signed percentage of the average, not of the price`() {
        // A rising series sits above its own average. Fifty bars of +1 a day puts the last close
        // some way over the fifty-period mean, and the answer has to be a percentage so that one
        // threshold works on gold and on a satoshi-priced coin alike.
        val value = ScreenerIndicators.compute(ScreenerIndicatorId.SMA_DISTANCE, 50, rising(120))
        assertTrue("a rising market is above its own average", value!! > 0.0)
        assertTrue("and the answer is a percentage, not a price difference", value < 100.0)
    }

    @Test
    fun `volatility is published as a share of price rather than in the instrument's units`() {
        // A two-point daily range on a hundred-point instrument is two percent, whatever the
        // instrument is. In raw units the same filter would be meaningless across a mixed list.
        val value = ScreenerIndicators.compute(ScreenerIndicatorId.ATR_PERCENT, 14, flat(60))
        assertEquals(2.0, value!!, 0.2)
    }

    @Test
    fun `a period is clamped rather than refused, so a saved screen is never dropped`() {
        // A later build may allow a wider range. Answering with the nearest supported period beats
        // silently losing a condition the reader wrote, which is what refusing it would amount to.
        val bars = rising(500)
        val clamped = ScreenerIndicators.compute(ScreenerIndicatorId.RSI, 100_000, bars)
        val atMaximum = ScreenerIndicators.compute(ScreenerIndicatorId.RSI, ScreenerIndicators.MAX_PERIOD, bars)
        assertEquals(atMaximum!!, clamped!!, 1e-9)
    }

    @Test
    fun `a period below the floor is clamped up rather than dividing by nothing`() {
        val bars = rising(40)
        val clamped = ScreenerIndicators.compute(ScreenerIndicatorId.RSI, 0, bars)
        val atFloor = ScreenerIndicators.compute(ScreenerIndicatorId.RSI, ScreenerIndicators.MIN_PERIOD, bars)
        assertEquals(atFloor!!, clamped!!, 1e-9)
    }

    @Test
    fun `computeAll takes keys apart and skips the ones it cannot answer`() {
        val values = ScreenerIndicators.computeAll(setOf("rsi:2", "rsi:14", "nonsense:9"), rising(40))
        assertEquals(setOf("rsi:2", "rsi:14"), values.keys)
        assertEquals(100.0, values.getValue("rsi:2"), 0.01)
    }

    @Test
    fun `a key with no period falls back to the indicator's own default`() {
        val byKey = ScreenerIndicators.computeAll(setOf("rsi"), rising(40))
        val spelledOut = ScreenerIndicators.compute(ScreenerIndicatorId.RSI, 14, rising(40))
        assertEquals(spelledOut!!, byKey.getValue("rsi"), 1e-9)
    }

    @Test
    fun `every advertised indicator can actually be computed`() {
        // The catalogue and the calculator are two lists that could drift apart, and the symptom
        // would be a filter the sheet offers and nothing ever matches.
        val bars = rising(120)
        ScreenerIndicatorId.ALL.forEach { id ->
            assertTrue(id, ScreenerIndicators.compute(id, null, bars) != null)
        }
        // And the same promise for the catalogue the sheet now offers: everything it advertises on
        // a feed with volume must produce a reading on a series long enough to warm it up. A row
        // offered and never answered is the failure this whole item is about.
        ScreenerIndicatorCatalog.offered(hasVolume = true).forEach { option ->
            assertNotNull(option.id, ScreenerIndicators.compute(option.id, option.defaultPeriod, bars))
        }
    }
}
