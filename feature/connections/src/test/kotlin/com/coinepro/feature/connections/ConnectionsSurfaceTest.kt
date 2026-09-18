package com.coinepro.feature.connections

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
 * somewhere else, while `user/account/link` sat there working the whole time. So a second
 * MetaTrader card went in, this one over routes that existed.
 *
 * **Both are gone now, and for a third reason** (run Ψ): the account they linked was a
 * *copy-trading* account, and the product no longer has copy trading. On the forex side this app is
 * a gold signal and a chart — nothing mirrors orders onto a broker account, so there is no account
 * to link and no form that could be completed. CoinePro-FX takes `LINKED_ELSEWHERE`, which is the
 * surface this file already had for exactly that case.
 *
 * What is pinned here is the closed set and the rule behind it: a surface is offered exactly where
 * the routes that complete it exist on the platform in hand.
 */
class ConnectionsSurfaceTest {

    @Test
    fun `the exchange key is offered only where a venue route exists`() {
        assertEquals(
            ConnectionsSurface.EXCHANGE_KEY,
            connectionsSurface(MarketPlatform.TRADEYAR, unsupported = false),
        )
    }

    @Test
    fun `forex has nothing to connect, whatever its execution gateway says`() {
        // Both ways round, because the forex answer is now a property of the platform rather than
        // of a gateway's reply: there is no account on that side of the product at all.
        assertEquals(
            ConnectionsSurface.LINKED_ELSEWHERE,
            connectionsSurface(MarketPlatform.COINEPRO_FX, unsupported = false),
        )
        assertEquals(
            ConnectionsSurface.LINKED_ELSEWHERE,
            connectionsSurface(MarketPlatform.COINEPRO_FX, unsupported = true),
        )
    }

    @Test
    fun `a server that refuses the venue route is absence, not an empty form`() {
        // The gateway throws rather than answering, which is how a platform with no such surface
        // reports itself. A form drawn over that would collect credentials nothing would read.
        assertEquals(
            ConnectionsSurface.LINKED_ELSEWHERE,
            connectionsSurface(MarketPlatform.TRADEYAR, unsupported = true),
        )
    }

    @Test
    fun `the set of surfaces is closed at two`() {
        // The count is the claim. A third entry would mean somebody added a surface, and every
        // surface on this screen has to be one the app can also complete.
        assertEquals(
            listOf(
                ConnectionsSurface.EXCHANGE_KEY,
                ConnectionsSurface.LINKED_ELSEWHERE,
            ),
            ConnectionsSurface.entries,
        )
    }
}
