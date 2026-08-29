package com.coinepro.core.orderbook

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A gateway whose answers the test dictates. Finite streams, so a test never has to time out. */
private class FakeOrderBookGateway(
    var first: AppResult<OrderBook>,
    var live: List<OrderBook> = emptyList(),
    override val sourceName: String = "LBank",
) : OrderBookGateway {
    var loads = 0
        private set

    override suspend fun load(symbol: String, depth: Int): AppResult<OrderBook> {
        loads += 1
        return first
    }

    override fun stream(symbol: String): Flow<OrderBook> = flowOf(*live.toTypedArray())
}

private fun book(symbol: String = "BTCUSDT", bid: Double, ask: Double, at: Long = 1L) = OrderBook.of(
    symbol = symbol,
    bids = listOf(DepthLevel(bid, 1.0)),
    asks = listOf(DepthLevel(ask, 1.0)),
    at = at,
)

/**
 * The three answers the ladder can get, and the requirement that it always settles on one of them.
 *
 * The state this class exists to prevent is the fourth: `loading` still true after everything has
 * been asked and answered, which is what a reader sees as a spinner that never resolves.
 */
class OrderBookControllerTest {

    @Test
    fun `a snapshot settles the screen and the stream then replaces it`() = runTest {
        val gateway = FakeOrderBookGateway(
            first = AppResult.Success(book(bid = 100.0, ask = 101.0, at = 1L)),
            live = listOf(book(bid = 100.5, ask = 101.5, at = 2L)),
        )
        val controller = OrderBookController(gateway, this)

        controller.start("btcusdt")
        testScheduler.runCurrent()

        val state = controller.state.value
        assertEquals("BTCUSDT", state.symbol)
        assertFalse(state.loading)
        assertNull(state.unavailable)
        assertFalse(state.failed)
        assertTrue(state.hasDepth)
        // The stream's book won, so the ladder is showing the live one rather than the snapshot.
        assertEquals(2L, state.book!!.at)
        assertEquals("LBank", state.sourceName)
    }

    @Test
    fun `a transport failure settles as a failure that a retry can address`() = runTest {
        val gateway = FakeOrderBookGateway(first = AppResult.Failure(ErrorKind.NETWORK))
        val controller = OrderBookController(gateway, this)

        controller.start("BTCUSDT")
        testScheduler.runCurrent()

        assertFalse("a settled failure must stop loading", controller.state.value.loading)
        assertTrue(controller.state.value.failed)
        assertNull(controller.state.value.unavailable)

        // And a retry actually asks again, unlike a retry over a refusal.
        gateway.first = AppResult.Success(book(bid = 100.0, ask = 101.0))
        controller.refresh()
        testScheduler.runCurrent()
        assertEquals(2, gateway.loads)
        assertTrue(controller.state.value.hasDepth)
        assertFalse(controller.state.value.failed)
    }

    @Test
    fun `a book that arrives with no levels is its own state, not depth of zero`() = runTest {
        val gateway = FakeOrderBookGateway(first = AppResult.Success(OrderBook.empty("XAUUSD", at = 7L)))
        val controller = OrderBookController(gateway, this)

        controller.start("XAUUSD")
        testScheduler.runCurrent()

        val state = controller.state.value
        assertTrue(state.emptyBook)
        assertFalse(state.hasDepth)
        assertFalse(state.loading)
        // Not a failure and not a refusal: the feed answered, and the answer was a closed market.
        assertFalse(state.failed)
        assertNull(state.unavailable)
    }

    @Test
    fun `switching symbols clears the previous book instead of leaving it under a new heading`() = runTest {
        val gateway = FakeOrderBookGateway(first = AppResult.Success(book("BTCUSDT", bid = 100.0, ask = 101.0)))
        val controller = OrderBookController(gateway, this)
        controller.start("BTCUSDT")
        testScheduler.runCurrent()

        gateway.first = AppResult.Failure(ErrorKind.NETWORK)
        controller.start("ETHUSDT")
        testScheduler.runCurrent()

        val state = controller.state.value
        assertEquals("ETHUSDT", state.symbol)
        // Bitcoin's book must not still be on screen with Ethereum's name above it.
        assertNull(state.book)
        assertTrue(state.failed)
    }

    @Test
    fun `asking for the same symbol twice does not reopen the stream`() = runTest {
        val gateway = FakeOrderBookGateway(first = AppResult.Success(book(bid = 100.0, ask = 101.0)))
        val controller = OrderBookController(gateway, this)

        controller.start("BTCUSDT")
        testScheduler.runCurrent()
        controller.start("BTCUSDT")
        testScheduler.runCurrent()

        assertEquals(1, gateway.loads)
    }
}
