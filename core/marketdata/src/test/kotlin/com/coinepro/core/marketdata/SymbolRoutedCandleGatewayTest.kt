package com.coinepro.core.marketdata

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * The 5.24.2 browser check's three candle faults: forex asked of the crypto server (`422`), a guest
 * with no forex chart token, and the bulk readers' `429`s.
 */
class SymbolRoutedCandleGatewayTest {

    private class Named(override val sourceName: String) : CandleGateway {
        val asked = mutableListOf<String>()
        override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?): CandlePage {
            asked += symbol
            return CandlePage(symbol = symbol, timeframe = timeframe, candles = emptyList())
        }
    }

    @Test
    fun `each symbol goes to the platform that carries it`() = runTest {
        val crypto = Named("TradeYar")
        val forex = Named("CoinePro-FX")
        val routed = SymbolRoutedCandleGateway(crypto = crypto, forex = forex)

        listOf("BTCUSDT", "EURUSD", "XAUUSD", "ETHUSDT").forEach { routed.load(it, Timeframe.H1, 10, null) }

        assertEquals(listOf("BTCUSDT", "ETHUSDT"), crypto.asked)
        assertEquals(listOf("EURUSD", "XAUUSD"), forex.asked)
    }

    @Test
    fun `the venue a caller reads without a symbol is the open tab's`() {
        val crypto = Named("TradeYar")
        val forex = Named("CoinePro-FX")
        assertEquals("CoinePro-FX", SymbolRoutedCandleGateway(crypto, forex, primary = forex).sourceName)
        assertEquals("TradeYar", SymbolRoutedCandleGateway(crypto, forex).sourceName)
    }

    private class Tokens(var fail: Exception? = null, val value: String) : AcademyTokenStore, GuestTokenStore {
        var calls = 0
        override suspend fun token(): String {
            calls++
            fail?.let { throw it }
            return value
        }
        override fun clear() = Unit
    }

    @Test
    fun `a refused forex session falls back to the guest token and waits before asking again`() = runTest {
        var now = 0L
        val session = Tokens(fail = IllegalStateException("401"), value = "session")
        val guest = Tokens(value = "guest")
        val tokens = FallbackAcademyTokens(session, guest, nowMillis = { now })

        assertEquals("guest", tokens.token())
        now += 30_000
        assertEquals("guest", tokens.token())
        assertEquals(1, session.calls)

        session.fail = null
        now += 60_000
        assertEquals("session", tokens.token())
    }

    @Test
    fun `a guest goes straight to the guest token, with no session mint the server must refuse`() = runTest {
        // 5.27.0's browser check: `academy-token` answered 401 on every guest opening.
        val session = Tokens(value = "session")
        val tokens = FallbackAcademyTokens(session, Tokens(value = "guest"), hasSession = { false })
        assertEquals("guest", tokens.token())
        assertEquals(0, session.calls)
    }

    @Test
    fun `a disabled academy is not papered over`() = runTest {
        val tokens = FallbackAcademyTokens(Tokens(fail = AcademyDisabledException(), value = ""), Tokens(value = "guest"))
        try {
            tokens.token()
            fail("expected AcademyDisabledException")
        } catch (_: AcademyDisabledException) {
        }
    }

    @Test
    fun `the pacer spaces starts by its interval`() = runTest {
        val pacer = RequestPacer(intervalMillis = 1_000) { testScheduler.currentTime }
        val starts = mutableListOf<Long>()
        repeat(3) {
            pacer.awaitTurn()
            starts += testScheduler.currentTime
        }
        assertEquals(listOf(0L, 1_000L, 2_000L), starts)
    }
}
