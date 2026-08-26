package com.coinepro.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The defaults on a quote, which are the safe ones on purpose.
 *
 * A market quote is the one object in this app a reader acts on money with. Its defaults say
 * "unknown" and "stale" rather than "zero" and "live", and that choice is worth pinning: a quote
 * that defaults to fresh would let a parser that dropped a timestamp render a two-hour-old price
 * as a current one, which is the failure that costs money.
 */
class MarketQuoteTest {

    private val instrument = Instrument("XAUUSD", "طلا", MarketType.FOREX)

    @Test
    fun `a quote with only the required fields is stale and unattributed`() {
        val quote = MarketQuote(instrument = instrument, price = 2_408.5, timestampEpochMillis = 0)

        assertTrue("a quote must not default to live", quote.isStale)
        assertEquals(QuoteSource.UNKNOWN, quote.source)
        assertNull("no bid means no bid, not zero", quote.bid)
        assertNull(quote.ask)
        assertNull("a missing change is missing, not flat", quote.changePercent)
    }

    @Test
    fun `bid and ask are optional because one of the two feeds does not send them`() {
        // TradeYar's relay publishes its ticker topic with both null. Their team confirmed the
        // exchange does have a book and the nulls are the relay's, which is why the fields stay
        // rather than being deleted — they are unwired, not dead.
        val quote = MarketQuote(
            instrument = instrument,
            price = 91_248.30,
            bid = null,
            ask = null,
            timestampEpochMillis = 1_787_751_459_000,
            source = QuoteSource.LBANK,
            isStale = false,
        )
        assertNull(quote.bid)
        assertNull(quote.ask)
        assertEquals(QuoteSource.LBANK, quote.source)
    }

    @Test
    fun `an instrument knows what it is, separately from who serves it`() {
        // MarketType is what the instrument *is*; MarketPlatform is *who serves it*. They line up
        // today and are still different questions — collapsing them is what makes a second forex
        // venue impossible to add later.
        assertEquals(MarketType.FOREX, instrument.marketType)
        assertEquals(MarketPlatform.COINEPRO_FX, MarketPlatform.forMarket(instrument.marketType))
    }
}
