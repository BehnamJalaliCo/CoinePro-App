package com.coinepro.feature.search

import com.coinepro.core.common.BidiText
import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.symbols.MarketStatus
import com.coinepro.core.symbols.MatchField
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the long-press preview says, which is the part of it that can be wrong without looking it.
 *
 * A sheet that renders is not a sheet that is right: the two failures worth a test are a stale
 * percentage shown beside the word «بسته», and a figure formatted at somebody else's precision.
 * Neither crashes and neither looks broken.
 */
class MarketPreviewTest {

    private val open = MarketStatus(open = true, weekend = false)

    @Test
    fun `the price keeps the decimals its own magnitude needs`() {
        // Not a fixed two. A memecoin rounded to two decimals reads 0.00, which is not a rounding
        // but a claim that the asset is worthless.
        assertEquals("91,248.30", figure(price(91_248.3)))
        assertEquals("0.5241", figure(price(0.5241)))
        assertEquals("0.00002418", figure(price(0.00002418)))
    }

    @Test
    fun `the figure is isolated so it survives a right-to-left paragraph`() {
        // The sheet's title and subtitle are Persian. Without the isolate the price's grouping
        // commas reorder around it.
        val raw = price(91_248.3).price

        assertTrue(raw != BidiText.strip(raw))
    }

    @Test
    fun `a market the feed has not quoted shows a dash, not a zero`() {
        val state = previewOf(row("SOLUSDT", price = null, percent = null), emptyList(), false, open)

        assertEquals("—", state.price)
        assertNull(state.changePercent)
    }

    @Test
    fun `a closed market drops the move rather than showing yesterday's`() {
        // The row above the sheet does exactly this, and the two must agree: a percentage next to
        // «تعطیل آخر هفته» is a number claiming to be today's.
        val weekend = MarketStatus(open = false, weekend = true)

        val state = previewOf(row("XAUUSD", price = 2_410.55, percent = 1.2), emptyList(), false, weekend)

        assertNull(state.changePercent)
        assertEquals(MarketClosure.WEEKEND, state.closure)
        // The price is still there. It is the last price, which is true; the move is what is stale.
        assertEquals("2,410.55", figure(state))
    }

    @Test
    fun `an unexplained close is named differently from the weekend`() {
        // One of them ends on Sunday evening without anybody doing anything and the other does not.
        val halted = MarketStatus(open = false, weekend = false)

        val state = previewOf(row("XAUUSD", price = 2_410.55, percent = 1.2), emptyList(), false, halted)

        assertEquals(MarketClosure.CLOSED, state.closure)
    }

    @Test
    fun `an open market keeps its move and reports no closure`() {
        val state = previewOf(row("BTCUSDT", price = 91_248.3, percent = -2.4), emptyList(), false, open)

        assertEquals(-2.4, state.changePercent!!, 1e-9)
        assertNull(state.closure)
    }

    @Test
    fun `a single point is no line at all`() {
        // One price has no shape, and the renderer would have to invent what a single value looks
        // like. Empty is the sheet's own signal to draw no picture rather than an empty box.
        val one = previewOf(row("BTCUSDT", price = 1.0, percent = 0.0), listOf(1.0), false, open)
        val two = previewOf(row("BTCUSDT", price = 1.0, percent = 0.0), listOf(1.0, 2.0), false, open)

        assertEquals(emptyList<Double>(), one.line)
        assertEquals(listOf(1.0, 2.0), two.line)
    }

    @Test
    fun `the star reflects the list the reader owns rather than the row's own opinion`() {
        val on = previewOf(row("BTCUSDT", price = 1.0, percent = 0.0), emptyList(), true, open)
        val off = previewOf(row("BTCUSDT", price = 1.0, percent = 0.0), emptyList(), false, open)

        assertTrue(on.starred)
        assertFalse(off.starred)
    }

    @Test
    fun `the title is the slashed form and the subtitle is the Persian name`() {
        val state = previewOf(row("BTCUSDT", price = 1.0, percent = 0.0), emptyList(), false, open)

        assertEquals("BTC/USDT", BidiText.strip(state.pretty))
        assertEquals("BTCUSDT", state.name)
        // Identity, never the prettier name: it is what goes back on the wire for the chart.
        assertEquals("BTCUSDT", state.symbol)
    }

    private fun price(value: Double) =
        previewOf(row("BTCUSDT", price = value, percent = 0.0), emptyList(), false, open)

    /** The price without the bidi isolates, which is what a reader sees. */
    private fun figure(state: MarketPreviewState) = BidiText.strip(state.price)
}

/** A catalogue row with just enough on it to preview. */
private fun row(symbol: String, price: Double?, percent: Double?) = MarketSearchRow(
    meta = SymbolMeta(
        symbol = symbol,
        canonical = symbol,
        category = SymbolCategory.CRYPTO,
        base = symbol.removeSuffix("USDT"),
        quote = "USDT",
        description = symbol,
        popular = false,
    ),
    quote = price?.let {
        MarketQuote(
            instrument = Instrument(symbol, symbol, MarketType.CRYPTO),
            price = it,
            changePercent = percent,
            timestampEpochMillis = 0L,
        )
    },
    field = MatchField.NONE,
    highlight = null,
)
