package com.coinepro.feature.screener

import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.feature.screener.model.ScreenerIndicatorId
import org.junit.Assert.assertEquals
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
        assertNull(ScreenerIndicators.compute("supertrend", 10, rising(40)))
    }

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
    }
}
