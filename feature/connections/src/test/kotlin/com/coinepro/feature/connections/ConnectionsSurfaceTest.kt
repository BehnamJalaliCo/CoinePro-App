package com.coinepro.feature.connections

import com.coinepro.core.model.MarketPlatform
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The screen must never again offer a connection it cannot make.
 *
 * It offered one for a year: a MetaTrader 5 card with a broker, a server, an account number and a
 * live trading password, over `ExecutionController.connectMt5`, which throws
 * `ExecutionUnsupportedException` on both platforms because neither backend has that route. The
 * owner found the screen and asked whether an account is connected here — the answer is yes for the
 * exchange and no for forex, and the form said otherwise.
 *
 * What is pinned here is the closed set. A third surface added to [ConnectionsSurface] fails the
 * last test until somebody says which server completes it.
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
    fun `forex is told where its account is linked rather than shown a form`() {
        assertEquals(
            ConnectionsSurface.LINKED_ELSEWHERE,
            connectionsSurface(MarketPlatform.COINEPRO_FX, unsupported = false),
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
    fun `there is no MetaTrader surface on this screen at all`() {
        assertEquals(
            listOf(ConnectionsSurface.EXCHANGE_KEY, ConnectionsSurface.LINKED_ELSEWHERE),
            ConnectionsSurface.entries,
        )
    }
}
