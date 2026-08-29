package com.coinepro.feature.screener

import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.symbols.SymbolClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a price and a day's bars become a row.
 *
 * Two of these are about the same trap from opposite sides: the live price is ahead of the last
 * daily bar, so a screener that took the bar's close would print a change percent that disagrees
 * with the price on the same row, and one that took the bar's high would report a market making a
 * new high as sitting at a negative distance from it.
 */
class ScreenerMetricsTest {

    private val bitcoin = SymbolClassifier.classify("BTCUSDT")

    private fun quote(price: Double) = MarketQuote(
        instrument = Instrument("BTCUSDT", "BTCUSDT", MarketType.CRYPTO),
        price = price,
        timestampEpochMillis = 0L,
    )

    private fun bar(open: Double, high: Double, low: Double, close: Double, volume: Double = 0.0) =
        OhlcBar(t = 0, o = open, h = high, l = low, c = close, v = volume)

    @Test
    fun `a market with no bars carries its price and nothing else`() {
        val row = ScreenerMetrics.rowOf(bitcoin, quote(100.0))
        assertEquals(100.0, row.price!!, 1e-9)
        assertFalse("nothing is claimed about a day nobody has read", row.resolved)
        assertNull(row.changePercent)
        assertNull(row.volume)
        assertNull(row.high)
    }

    @Test
    fun `the day's move is measured against the previous close, not the current bar's open`() {
        val row = ScreenerMetrics.rowOf(
            meta = bitcoin,
            quote = null,
            bars = listOf(bar(90.0, 100.0, 90.0, 100.0), bar(100.0, 112.0, 99.0, 110.0)),
        )
        assertEquals(10.0, row.changeAbsolute!!, 1e-9)
        assertEquals(10.0, row.changePercent!!, 1e-9)
    }

    @Test
    fun `the live price beats the bar's close, so the move agrees with the price beside it`() {
        val row = ScreenerMetrics.rowOf(
            meta = bitcoin,
            quote = quote(120.0),
            bars = listOf(bar(90.0, 100.0, 90.0, 100.0), bar(100.0, 112.0, 99.0, 110.0)),
        )
        assertEquals(120.0, row.price!!, 1e-9)
        assertEquals(20.0, row.changePercent!!, 1e-9)
    }

    @Test
    fun `a market making a new high sits at zero distance from it, never a negative one`() {
        val row = ScreenerMetrics.rowOf(
            meta = bitcoin,
            quote = quote(130.0),
            bars = listOf(bar(90.0, 100.0, 90.0, 100.0), bar(100.0, 112.0, 99.0, 110.0)),
        )
        assertEquals(130.0, row.high!!, 1e-9)
        assertEquals(0.0, row.distanceFromHigh!!, 1e-9)
    }

    @Test
    fun `a market making a new low widens the bar downwards too`() {
        val row = ScreenerMetrics.rowOf(
            meta = bitcoin,
            quote = quote(80.0),
            bars = listOf(bar(90.0, 100.0, 90.0, 100.0), bar(100.0, 112.0, 99.0, 110.0)),
        )
        assertEquals(80.0, row.low!!, 1e-9)
        assertEquals(0.0, row.distanceFromLow!!, 1e-9)
    }

    @Test
    fun `a volume of zero is unknown rather than a claim that nothing traded`() {
        // The MT5 side reports none at all. Treated as zero it would present the entire forex
        // catalogue as the quietest markets of the day.
        val row = ScreenerMetrics.rowOf(
            meta = bitcoin,
            quote = null,
            bars = listOf(bar(90.0, 100.0, 90.0, 100.0), bar(100.0, 112.0, 99.0, 110.0, volume = 0.0)),
        )
        assertNull(row.volume)
        assertNull(row.quoteVolume)
        assertTrue("but the bar was still read", row.resolved)
    }

    @Test
    fun `turnover is the volume at the bar's typical price`() {
        val row = ScreenerMetrics.rowOf(
            meta = bitcoin,
            quote = null,
            bars = listOf(bar(90.0, 100.0, 90.0, 100.0), bar(100.0, 120.0, 90.0, 105.0, volume = 10.0)),
        )
        assertEquals(10.0, row.volume!!, 1e-9)
        assertEquals(10.0 * (120.0 + 90.0 + 105.0) / 3.0, row.quoteVolume!!, 1e-6)
    }

    @Test
    fun `the day's range is measured against its own low`() {
        val row = ScreenerMetrics.rowOf(
            meta = bitcoin,
            quote = null,
            bars = listOf(bar(90.0, 100.0, 90.0, 100.0), bar(100.0, 110.0, 100.0, 105.0)),
        )
        assertEquals(10.0, row.rangePercent!!, 1e-9)
    }

    @Test
    fun `a market with a single bar of history measures against that bar's open`() {
        val row = ScreenerMetrics.rowOf(bitcoin, null, listOf(bar(100.0, 110.0, 99.0, 105.0)))
        assertEquals(5.0, row.changePercent!!, 1e-9)
    }

    @Test
    fun `only the indicator readings a screen asked for are computed`() {
        val bars = List(40) { index ->
            val close = 100.0 + index
            bar(close, close + 1.0, close - 1.0, close)
        }
        val none = ScreenerMetrics.rowOf(bitcoin, null, bars)
        assertTrue("nothing asked for, nothing computed", none.indicators.isEmpty())

        val one = ScreenerMetrics.rowOf(bitcoin, null, bars, indicatorKeys = setOf("rsi:14"))
        assertEquals(setOf("rsi:14"), one.indicators.keys)
    }

    @Test
    fun `the market a row belongs to comes from its asset class when there is no quote`() {
        assertEquals("CRYPTO", ScreenerMetrics.rowOf(bitcoin, null).market)
        assertEquals("FOREX", ScreenerMetrics.rowOf(SymbolClassifier.classify("EURUSD"), null).market)
    }
}
