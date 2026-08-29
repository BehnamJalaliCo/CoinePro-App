package com.coinepro.core.marketdata

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared table's behaviour under the conditions this audience actually has.
 *
 * The interesting cases are all about a connection that comes and goes, because that is the normal
 * one here rather than the exceptional one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarketTickerStoreTest {

    private fun table(vararg rows: Pair<String, Double?>, ttl: Long? = 5_000L) = MarketTickerTable(
        tickers = rows.associate { (symbol, change) ->
            symbol to MarketTicker(symbol = symbol, last = 1.0, changePercent24h = change)
        },
        serverTimeEpochMillis = 1L,
        cacheTtlMillis = ttl,
        fetchedAtEpochMillis = 1L,
        source = "lbank",
    )

    private class FakeGateway(
        override val supported: Boolean = true,
        var answer: () -> MarketTickerTable = { MarketTickerTable.Empty },
    ) : MarketTickerGateway {
        var calls = 0
        override suspend fun load(symbols: List<String>?): MarketTickerTable {
            calls += 1
            return answer()
        }
    }

    @Test
    fun `a platform with no route is never polled and never fails`() {
        // CoinePro-FX. The distinction that matters downstream is "this platform does not serve
        // these figures" against "the request failed", because they call for different screens.
        val scope = TestScope()
        val gateway = FakeGateway(supported = false)
        val store = MarketTickerStore(gateway, scope)

        store.start()
        scope.advanceTimeBy(60_000)
        scope.runCurrent()

        assertEquals(0, gateway.calls)
        assertFalse(store.supported)
        assertFalse(store.state.value.failed)
        assertTrue(store.state.value.table.tickers.isEmpty())
    }

    @Test
    fun `a failed poll keeps the figures it already had`() {
        // The case this app is in most often. Blanking every percentage on screen because one
        // request was dropped would be a worse answer than figures a minute old — and the reader
        // can see which, because the table carries when it was fetched.
        val scope = TestScope()
        val gateway = FakeGateway()
        gateway.answer = { table("BTCUSDT" to 1.5) }
        val store = MarketTickerStore(gateway, scope)

        store.start()
        scope.runCurrent()
        assertEquals(1.5, store.state.value["BTCUSDT"]!!.changePercent24h!!, 1e-9)

        gateway.answer = { error("the socket dropped") }
        scope.advanceTimeBy(5_001)
        scope.runCurrent()

        assertTrue("the failure was swallowed rather than reported", store.state.value.failed)
        assertEquals(
            "a dropped request emptied the table",
            1.5,
            store.state.value["BTCUSDT"]!!.changePercent24h!!,
            1e-9,
        )
    }

    @Test
    fun `a recovered poll clears the failure`() {
        val scope = TestScope()
        val gateway = FakeGateway()
        gateway.answer = { error("down") }
        val store = MarketTickerStore(gateway, scope)

        store.start()
        scope.runCurrent()
        assertTrue(store.state.value.failed)

        gateway.answer = { table("BTCUSDT" to 2.0) }
        scope.advanceTimeBy(5_001)
        scope.runCurrent()

        assertFalse(store.state.value.failed)
    }

    @Test
    fun `the poll runs at the server's own cache lifetime`() {
        // Polling faster than the TTL only re-reads the server's cache: the same bytes and the
        // same figures, on somebody's mobile data.
        val scope = TestScope()
        val gateway = FakeGateway()
        gateway.answer = { table("BTCUSDT" to 1.0, ttl = 30_000L) }
        val store = MarketTickerStore(gateway, scope)

        store.start()
        scope.runCurrent()
        assertEquals(1, gateway.calls)

        scope.advanceTimeBy(29_000)
        scope.runCurrent()
        assertEquals("polled before the server's cache had turned over", 1, gateway.calls)

        scope.advanceTimeBy(1_500)
        scope.runCurrent()
        assertEquals(2, gateway.calls)
    }

    @Test
    fun `a missing or zero cache lifetime cannot become a request loop`() {
        val scope = TestScope()
        val gateway = FakeGateway()
        gateway.answer = { table("BTCUSDT" to 1.0, ttl = 0L) }
        val store = MarketTickerStore(gateway, scope)

        store.start()
        scope.runCurrent()
        scope.advanceTimeBy(4_000)
        scope.runCurrent()

        assertEquals("a zero TTL was taken literally against an 801-row route", 1, gateway.calls)
    }

    @Test
    fun `the last screen to close stops the feed, not the first`() {
        // A market list under a heat map under a sheet: three readers, and the first one to be
        // dismissed must not take the figures away from the two still on screen.
        val scope = TestScope()
        val gateway = FakeGateway()
        gateway.answer = { table("BTCUSDT" to 1.0) }
        val store = MarketTickerStore(gateway, scope)

        store.start()
        store.start()
        scope.runCurrent()
        val afterFirst = gateway.calls

        store.stop()
        scope.advanceTimeBy(5_001)
        scope.runCurrent()
        assertTrue("one screen closing stopped the feed for the others", gateway.calls > afterFirst)

        store.stop()
        val afterStop = gateway.calls
        scope.advanceTimeBy(20_000)
        scope.runCurrent()
        assertEquals("the feed kept polling with nobody reading it", afterStop, gateway.calls)
    }

    @Test
    fun `a symbol is looked up however the caller spelled it`() {
        val scope = TestScope()
        val gateway = FakeGateway()
        gateway.answer = { table("BTCUSDT" to 1.0) }
        val store = MarketTickerStore(gateway, scope)

        store.start()
        scope.runCurrent()

        assertEquals(1.0, store.state.value["btcusdt"]!!.changePercent24h!!, 1e-9)
        assertNull(store.state.value["ETHUSDT"])
    }
}
