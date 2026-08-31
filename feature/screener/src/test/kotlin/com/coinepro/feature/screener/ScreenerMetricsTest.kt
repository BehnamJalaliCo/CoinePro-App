package com.coinepro.feature.screener

import com.coinepro.core.marketdata.MarketTicker
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
 * How a price, the day's table and a day's bars become a row.
 *
 * Two of these are about the same trap from opposite sides: the live price is ahead of both the last
 * daily bar and the venue's five-second-old table, so a screener that took either would print a
 * change percent that disagrees with the price on the same row, and one that took the bar's high
 * would report a market making a new high as sitting at a negative distance from it.
 *
 * The ones at the end are about the second source. Where the venue has answered, the venue is right
 * and the bar does not get a say — and where the venue has not answered a particular field, nothing
 * is allowed to stand in for it: not a zero, not the bar's figure for a window the venue does not
 * measure, and not the neighbouring field that happens to be a number of about the right size.
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
    fun `the catalogue's own day change fills the column when nothing better has arrived`() {
        // The default sort is on «تغییر روزانه». With this thrown away, a screener over a catalogue
        // and no candle source sorted a column of dashes by nothing — on every market the app
        // already knew the day's move for.
        val row = ScreenerMetrics.rowOf(
            bitcoin,
            quote(100.0).copy(changePercent = 25.0),
        )
        assertEquals(25.0, row.changePercent!!, 1e-9)
        // Derived from the same percentage against the same price, so subtracting it from the price
        // lands on the reference the percentage was measured from — never half from a bar.
        assertEquals(20.0, row.changeAbsolute!!, 1e-9)
    }

    @Test
    fun `a venue reading wins over the catalogue's`() {
        val row = ScreenerMetrics.rowOf(
            bitcoin,
            quote(110.0).copy(changePercent = 25.0),
            bars = listOf(bar(90.0, 101.0, 88.0, 100.0), bar(100.0, 112.0, 99.0, 110.0)),
        )
        assertEquals(10.0, row.changePercent!!, 1e-9)
        assertEquals(10.0, row.changeAbsolute!!, 1e-9)
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
    fun `a row carries the readings it was handed and computes none of its own`() {
        // The readings arrive already computed — the controller reduces them on a background
        // dispatcher and caches them per (symbol, indicator, period). A row that computed its own
        // would do several hundred bars of arithmetic per market on whichever thread rebuilt the
        // table, which on this screen is the one drawing it.
        val bars = List(40) { index ->
            val close = 100.0 + index
            bar(close, close + 1.0, close - 1.0, close)
        }
        val none = ScreenerMetrics.rowOf(bitcoin, null, bars)
        assertTrue("nothing handed in, nothing claimed", none.indicators.isEmpty())

        val one = ScreenerMetrics.rowOf(bitcoin, null, bars, indicators = mapOf("rsi:14" to 71.5))
        assertEquals(setOf("rsi:14"), one.indicators.keys)
        assertEquals(71.5, one.indicators.getValue("rsi:14"), 1e-9)
    }

    @Test
    fun `the market a row belongs to comes from its asset class when there is no quote`() {
        assertEquals("CRYPTO", ScreenerMetrics.rowOf(bitcoin, null).market)
        assertEquals("FOREX", ScreenerMetrics.rowOf(SymbolClassifier.classify("EURUSD"), null).market)
    }

    // ── the day's table ─────────────────────────────────────────────────────────────────────

    /** The venue's row for a market at 96, down four percent from a rolling open of 100. */
    private fun ticker(
        change: Double? = -4.0,
        open: Double? = 100.0,
        volume: Double? = null,
        turnover: Double? = null,
    ) = MarketTicker(
        symbol = "BTCUSDT",
        last = 96.0,
        open24h = open,
        high24h = 101.0,
        low24h = 95.0,
        changePercent24h = change,
        volume24h = volume,
        turnover24h = turnover,
    )

    @Test
    fun `the venue's day wins over the one derived from two daily closes`() {
        // The bars here say the market is up ten percent against yesterday's close. The venue says
        // down four over its own rolling twenty-four hours, which is what its site prints and what
        // the reader is holding this column against.
        val row = ScreenerMetrics.rowOf(
            meta = bitcoin,
            quote = null,
            bars = listOf(bar(100.0, 100.0, 100.0, 100.0), bar(100.0, 110.0, 100.0, 110.0)),
            ticker = ticker(),
        )
        assertEquals(-4.0, row.changePercent!!, 1e-9)
        assertEquals(101.0, row.high!!, 1e-9)
        assertEquals(95.0, row.low!!, 1e-9)
        assertTrue(row.resolved)
    }

    @Test
    fun `the change is taken as a pair from one source, never half from each`() {
        // The venue answered the percentage and did not send the open it measured from. The
        // absolute is then unknown rather than borrowed from the bar: a reader who subtracts
        // «تغییر مطلق» from the price expects to land on the number «تغییر روزانه» is measured
        // from, and a rolling percentage beside a midnight-to-now absolute does not agree with
        // itself.
        val row = ScreenerMetrics.rowOf(
            meta = bitcoin,
            quote = null,
            bars = listOf(bar(100.0, 100.0, 100.0, 100.0), bar(100.0, 110.0, 100.0, 110.0)),
            ticker = ticker(open = null),
        )
        assertEquals(-4.0, row.changePercent!!, 1e-9)
        assertNull("half a pair is worse than none", row.changeAbsolute)
    }

    @Test
    fun `volume and turnover are different quantities and never stand in for each other`() {
        // The venue reported a traded quantity and no traded value. «ارزش معاملات» stays unknown:
        // it is money moved, and filling it from a count of coins would rank a cheap token above
        // Bitcoin. The relay this route replaced has exactly that bug in the field it calls
        // `volume24h`, and said so.
        val row = ScreenerMetrics.rowOf(bitcoin, quote = null, ticker = ticker(volume = 500.0))
        assertEquals(500.0, row.volume!!, 1e-9)
        assertNull(row.quoteVolume)

        val both = ScreenerMetrics.rowOf(
            bitcoin,
            quote = null,
            ticker = ticker(volume = 500.0, turnover = 48_000.0),
        )
        assertEquals(48_000.0, both.quoteVolume!!, 1e-9)
    }

    @Test
    fun `a row the table answered for is read, even where the answer was a price and nothing else`() {
        // Two different facts. The progress line above the table counts this market as read, and
        // every cell on it still prints an em dash, because nothing is known about its day.
        val row = ScreenerMetrics.rowOf(
            bitcoin,
            quote = null,
            ticker = MarketTicker(symbol = "BTCUSDT", last = 96.0),
        )
        assertTrue("asked and answered", row.resolved)
        assertEquals(96.0, row.price!!, 1e-9)
        assertNull(row.changePercent)
        assertNull(row.high)
        assertNull(row.volume)
    }

    @Test
    fun `the live price beats the venue's last as well as the bar's close`() {
        // The table is up to five seconds old and the socket is ahead of it. A price on the row that
        // disagreed with the change beside it is the most obvious way for a market table to look
        // broken, and it does not matter which of the two stale sources produced it.
        val row = ScreenerMetrics.rowOf(bitcoin, quote = quote(103.0), ticker = ticker())
        assertEquals(103.0, row.price!!, 1e-9)
        // Making a new high against the venue's own window, so the high moves with it rather than
        // leaving the price sitting above the top of its own range.
        assertEquals(103.0, row.high!!, 1e-9)
        assertEquals(0.0, row.distanceFromHigh!!, 1e-9)
    }
}
