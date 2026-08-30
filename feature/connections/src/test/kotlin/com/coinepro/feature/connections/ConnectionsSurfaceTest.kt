package com.coinepro.feature.connections

import com.coinepro.core.copytrade.Mt5LinkStage
import com.coinepro.core.model.MarketPlatform
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The screen must never offer a connection it cannot make, and must offer every one it can.
 *
 * It failed both ways in turn. First it drew a MetaTrader 5 card over
 * `ExecutionController.connectMt5`, which throws on both platforms because neither backend has that
 * route — a form for the most dangerous credential the product touches that could only ever refuse
 * it. Then, having removed it, the screen told a CoinePro-FX reader their broker account was linked
 * somewhere else, while `user/account/link` sat there working the whole time.
 *
 * What is pinned here is the closed set and the rule behind it: a surface is offered exactly where
 * the routes that complete it exist on the platform in hand.
 */
class ConnectionsSurfaceTest {

    @Test
    fun `the exchange key is offered only where a venue route exists`() {
        assertEquals(
            ConnectionsSurface.EXCHANGE_KEY,
            connectionsSurface(MarketPlatform.TRADEYAR, unsupported = false, stage = null),
        )
    }

    @Test
    fun `forex is offered the MetaTrader link it actually has`() {
        assertEquals(
            ConnectionsSurface.MT5_COPY_LINK,
            connectionsSurface(
                MarketPlatform.COINEPRO_FX,
                unsupported = false,
                stage = Mt5LinkStage.NOT_LINKED,
            ),
        )
    }

    @Test
    fun `the venue route being absent on forex does not withdraw the broker link`() {
        // CoinePro-FX always reports the execution surface unsupported — it has no venue route at
        // all. That says nothing about `user/account/link`, and reading it as a veto is what left
        // the screen with no forex connection on it.
        assertEquals(
            ConnectionsSurface.MT5_COPY_LINK,
            connectionsSurface(
                MarketPlatform.COINEPRO_FX,
                unsupported = true,
                stage = Mt5LinkStage.CONNECTED,
            ),
        )
    }

    @Test
    fun `a subscription gate is not a missing surface`() {
        // The status read is refused, the link is not. The card has to be drawn to say so.
        assertEquals(
            ConnectionsSurface.MT5_COPY_LINK,
            connectionsSurface(
                MarketPlatform.COINEPRO_FX,
                unsupported = true,
                stage = Mt5LinkStage.LOCKED,
            ),
        )
    }

    @Test
    fun `a forex screen with no copy-trade controller behind it draws no form`() {
        // The exact bug this file was rewritten for: a MetaTrader card with nothing wired to it.
        assertEquals(
            ConnectionsSurface.LINKED_ELSEWHERE,
            connectionsSurface(MarketPlatform.COINEPRO_FX, unsupported = true, stage = null),
        )
    }

    @Test
    fun `a server with no copy routes is absence, not an empty form`() {
        assertEquals(
            ConnectionsSurface.LINKED_ELSEWHERE,
            connectionsSurface(
                MarketPlatform.COINEPRO_FX,
                unsupported = true,
                stage = Mt5LinkStage.UNAVAILABLE,
            ),
        )
    }

    @Test
    fun `a server that refuses the venue route is absence, not an empty form`() {
        // The gateway throws rather than answering, which is how a platform with no such surface
        // reports itself. A form drawn over that would collect credentials nothing would read.
        assertEquals(
            ConnectionsSurface.LINKED_ELSEWHERE,
            connectionsSurface(MarketPlatform.TRADEYAR, unsupported = true, stage = null),
        )
    }

    @Test
    fun `crypto is never shown the broker form, whatever the copy state says`() {
        // TradeYar has no copy-trading routes, so its stage can only ever be UNAVAILABLE — but the
        // exchange key is chosen on the platform, not on the absence, so a stray stage cannot
        // put a MetaTrader form in front of a crypto reader.
        assertEquals(
            ConnectionsSurface.EXCHANGE_KEY,
            connectionsSurface(
                MarketPlatform.TRADEYAR,
                unsupported = false,
                stage = Mt5LinkStage.UNAVAILABLE,
            ),
        )
    }

    @Test
    fun `the set of surfaces is closed at three`() {
        assertEquals(
            listOf(
                ConnectionsSurface.EXCHANGE_KEY,
                ConnectionsSurface.MT5_COPY_LINK,
                ConnectionsSurface.LINKED_ELSEWHERE,
            ),
            ConnectionsSurface.entries,
        )
    }
}
