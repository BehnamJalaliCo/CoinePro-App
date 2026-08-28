package com.coinepro.feature.signals

import com.coinepro.core.model.MarketType
import com.coinepro.core.model.SignalDirection
import com.coinepro.core.signals.SignalLiveQuote
import com.coinepro.core.signals.TradingSignal
import com.coinepro.core.model.QuoteSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The sign is the whole test.
 *
 * A sell whose market has fallen is winning, and the arithmetic that says so — negating the move —
 * is exactly the arithmetic somebody writes the wrong way round at four in the afternoon. Getting
 * it backwards would paint every profitable short red and every losing one green, on the screen a
 * reader opens to find out which of their calls is working.
 */
class LivePnlTest {

    private fun signal(
        direction: SignalDirection,
        entry: Double?,
        price: Double?,
        server: Double? = null,
    ) = TradingSignal(
        id = 1,
        market = MarketType.CRYPTO,
        symbol = "BTCUSDT",
        direction = direction,
        status = "active",
        timeframe = "H1",
        strategy = null,
        confidence = null,
        entry = entry,
        entryZone = null,
        stopLoss = null,
        targets = emptyList(),
        riskRewardTp1 = null,
        currentQuote = price?.let {
            SignalLiveQuote(
                price = it,
                bid = null,
                ask = null,
                timestampEpochMillis = null,
                source = QuoteSource.UNKNOWN,
                isStale = false,
            )
        },
        livePnlPercent = server,
        hitTarget = null,
        createdAt = null,
        closedAt = null,
    )

    @Test
    fun `a buy that rose is in profit`() {
        val pnl = livePnl(signal(SignalDirection.BUY, entry = 100.0, price = 110.0))
        assertEquals(10.0, pnl!!, 1e-9)
    }

    @Test
    fun `a sell that fell is in profit, not in loss`() {
        val pnl = livePnl(signal(SignalDirection.SELL, entry = 100.0, price = 90.0))
        assertEquals(10.0, pnl!!, 1e-9)
    }

    @Test
    fun `a sell that rose is in loss`() {
        val pnl = livePnl(signal(SignalDirection.SELL, entry = 100.0, price = 110.0))
        assertEquals(-10.0, pnl!!, 1e-9)
    }

    @Test
    fun `the server's own figure wins over the computed one`() {
        // The platform reports this number elsewhere; two slightly different answers to the same
        // question is worse than one, whichever is more precise.
        val pnl = livePnl(signal(SignalDirection.BUY, entry = 100.0, price = 110.0, server = 7.5))
        assertEquals(7.5, pnl!!, 1e-9)
    }

    @Test
    fun `nothing is claimed without an entry, a quote, or a side`() {
        assertNull(livePnl(signal(SignalDirection.BUY, entry = null, price = 110.0)))
        assertNull(livePnl(signal(SignalDirection.BUY, entry = 100.0, price = null)))
        assertNull(livePnl(signal(SignalDirection.NEUTRAL, entry = 100.0, price = 110.0)))
        // An entry of zero is not a trade and dividing by it would report infinity as a percentage.
        assertNull(livePnl(signal(SignalDirection.BUY, entry = 0.0, price = 110.0)))
    }
}
