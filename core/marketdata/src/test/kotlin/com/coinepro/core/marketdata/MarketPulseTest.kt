package com.coinepro.core.marketdata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the four figures at the top of the markets screen are allowed to claim (run ΤΦΥ, U3).
 *
 * Every test here is about a **refusal**. The row is the first thing on a screen full of numbers,
 * which makes an invented figure the most expensive mistake it could make: a reader who has seen
 * the published fear-and-greed index would compare it with ours and find them disagreeing, and a
 * capitalisation worked out from turnover would carry a familiar name and mean something else.
 *
 * So three of the four are pinned null, on purpose and with the reason in [MarketPulse]'s own note,
 * and the fourth is pinned to what the day's table actually says.
 */
class MarketPulseTest {

    private fun table(vararg tickers: MarketTicker) = MarketTickerTable(
        tickers = tickers.associateBy { it.symbol.uppercase() },
        serverTimeEpochMillis = null,
        cacheTtlMillis = null,
        fetchedAtEpochMillis = null,
        source = null,
    )

    private fun ticker(
        symbol: String,
        change: Double? = null,
        turnover: Double? = null,
    ) = MarketTicker(
        symbol = symbol,
        last = 100.0,
        changePercent24h = change,
        turnover24h = turnover,
    )

    @Test
    fun `an empty table is four dashes rather than four zeroes`() {
        val pulse = MarketPulse.of(MarketTickerTable.Empty)
        assertTrue("a board nobody has quoted has nothing to draw", pulse.isEmpty)
        assertEquals(0, pulse.markets)
        assertNull(pulse.turnover24h)
        assertNull(pulse.breadth)
    }

    @Test
    fun `turnover is the sum of the markets that reported one`() {
        val pulse = MarketPulse.of(
            table(
                ticker("BTCUSDT", change = 2.0, turnover = 1_000.0),
                ticker("ETHUSDT", change = -1.0, turnover = 500.0),
                // No turnover on the wire. Left out of the sum rather than counted as zero, which
                // would be a claim that nothing traded.
                ticker("SOLUSDT", change = 3.0, turnover = null),
            ),
        )
        assertEquals(1_500.0, pulse.turnover24h!!, 0.0001)
        assertEquals(3, pulse.markets)
    }

    @Test
    fun `turnover is named as one venue's book and never as the whole market`() {
        val pulse = MarketPulse.of(table(ticker("BTCUSDT", turnover = 10.0)))
        assertTrue(
            "the row must be able to say this figure is the venue's, not the world's",
            pulse.turnoverIsVenueOnly,
        )
        val none = MarketPulse.of(MarketTickerTable.Empty)
        assertFalse("a figure that does not exist cannot be qualified", none.turnoverIsVenueOnly)
    }

    @Test
    fun `breadth counts only the markets whose change the feed actually sent`() {
        val pulse = MarketPulse.of(
            table(
                ticker("BTCUSDT", change = 1.0),
                ticker("ETHUSDT", change = -1.0),
                // Unquoted: it is neither up nor down, and counting it as down would make a quiet
                // feed look like a falling market.
                ticker("SOLUSDT", change = null),
            ),
        )
        assertEquals(50, pulse.breadth)
    }

    @Test
    fun `cap, dominance and the mood index stay null on any table at all`() {
        // The whole of U3's honesty, in one assertion. None of the three can be computed from a
        // ticker table — the first two need a circulating supply neither backend serves, and the
        // third is somebody else's published number — so no table, however full, may produce one.
        val pulse = MarketPulse.of(
            table(
                ticker("BTCUSDT", change = 9.0, turnover = 9_000_000_000.0),
                ticker("ETHUSDT", change = 4.0, turnover = 4_000_000_000.0),
            ),
        )
        assertNull("market cap is price times supply, and no feed sends a supply", pulse.marketCap)
        assertNull("dominance needs the same supply figures", pulse.bitcoinDominance)
        assertNull("the published index is not ours to compute", pulse.fearGreed)
        // And the row is still worth drawing: one real figure and three explained dashes.
        assertFalse(pulse.isEmpty)
    }

    @Test
    fun `a turnover of zero is not counted as a turnover`() {
        // Zero on the wire is the same absence a null is — the market did not trade, or the feed
        // did not say — and summing it changes nothing while making `turnover24h` non-null on a
        // table that reported nothing.
        val pulse = MarketPulse.of(table(ticker("BTCUSDT", turnover = 0.0)))
        assertNull(pulse.turnover24h)
    }
}
