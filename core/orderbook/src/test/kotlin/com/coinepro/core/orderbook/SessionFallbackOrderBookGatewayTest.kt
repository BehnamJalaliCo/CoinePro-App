package com.coinepro.core.orderbook

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one hole the fallback closes, and the four it deliberately leaves open.
 *
 * The property under test is not "it tries twice". It is that it tries the exchange on exactly one
 * condition — the relay wanting a session this reader has not got — and on no other. Every other
 * refusal the relay gives is already true, and going around the platform to contradict one of them
 * would put a ladder on screen for a market this platform does not carry, or add traffic to an
 * outage, or hide a symbol-mapping bug behind a book that draws.
 */
class SessionFallbackOrderBookGatewayTest {

    private fun book(symbol: String, bid: Double) = OrderBook.of(
        symbol = symbol,
        bids = listOf(DepthLevel(bid, 1.0)),
        asks = listOf(DepthLevel(bid + 1.0, 1.0)),
        at = 0L,
    )

    /** Answers whatever it was built with, and counts what it was asked, so silence is testable. */
    private class Fake(
        private val answer: AppResult<OrderBook>,
        private val streamed: OrderBook? = null,
        override val sourceName: String = "",
    ) : OrderBookGateway {
        var loads = 0
            private set

        override suspend fun load(symbol: String, depth: Int): AppResult<OrderBook> {
            loads += 1
            return answer
        }

        override fun stream(symbol: String): Flow<OrderBook> =
            streamed?.let { flowOf(it) } ?: flowOf()
    }

    @Test
    fun `a relay that serves the book is never gone around`() = runTest {
        val relayBook = book("BTCUSDT", 100.0)
        val exchange = Fake(depthOutage(DepthOutageReason.EXCHANGE_UNREACHABLE))
        val gateway = SessionFallbackOrderBookGateway(
            relay = Fake(AppResult.Success(relayBook), streamed = relayBook, sourceName = "LBank Futures"),
            exchange = exchange,
        )

        val result = gateway.load("BTCUSDT")

        assertEquals(relayBook, (result as AppResult.Success).value)
        assertEquals("the exchange must not be touched while the relay is answering", 0, exchange.loads)
        // And the poll follows the relay, with its cache and its single flight in front of it.
        assertEquals(listOf(relayBook), gateway.stream("BTCUSDT").toList())
    }

    @Test
    fun `a reader with no session on this platform gets the venue's own book`() = runTest {
        // The reported failure, end to end. The relay answers 401, which is a settled refusal and
        // not a transport fault, and the exchange publishes the same book to anybody.
        val publicBook = book("BTCUSDT", 77_766.4)
        val gateway = SessionFallbackOrderBookGateway(
            relay = Fake(depthUnavailable(DepthUnavailableReason.SESSION_REQUIRED)),
            exchange = Fake(AppResult.Success(publicBook), streamed = publicBook),
        )

        val result = gateway.load("BTCUSDT")

        assertTrue("the ladder must draw rather than apologise", result is AppResult.Success)
        assertEquals(publicBook, (result as AppResult.Success).value)
        // The stream follows whoever answered, so the ladder keeps moving from the same source it
        // was first drawn from rather than reverting to the one that refused.
        assertEquals(listOf(publicBook), gateway.stream("BTCUSDT").toList())
    }

    @Test
    fun `when the venue cannot be reached either, the answer is still the missing session`() = runTest {
        // A reader who needs to sign in must not be sent to restart a router. The exchange's own
        // network failure is a symptom of a path this app does not depend on; the settled fact is
        // that the platform does not know who they are.
        val gateway = SessionFallbackOrderBookGateway(
            relay = Fake(depthUnavailable(DepthUnavailableReason.SESSION_REQUIRED)),
            exchange = Fake(AppResult.Failure(ErrorKind.NETWORK)),
        )

        val failure = gateway.load("BTCUSDT") as AppResult.Failure

        assertEquals(DepthUnavailableReason.SESSION_REQUIRED, failure.depthUnavailableReason)
        // A refusal, so the screen offers no retry — and the poll is not started against a source
        // that just failed.
        assertTrue(gateway.stream("BTCUSDT").toList().isEmpty())
    }

    @Test
    fun `every other refusal is passed through untouched`() = runTest {
        // Four conditions, four reasons to leave the platform's answer alone. A symbol outside this
        // platform's scope is the sharpest of them: drawing the exchange's book for it would put a
        // ladder on screen for an instrument the reader cannot trade here.
        val refusals = listOf(
            depthUnavailable(DepthUnavailableReason.SYMBOL_NOT_SERVED),
            depthUnavailable(DepthUnavailableReason.SYMBOL_DELISTED),
            depthUnavailable(DepthUnavailableReason.ENDPOINT_NOT_SERVED),
            depthOutage(DepthOutageReason.RELAY_NOT_CONFIGURED),
        )

        for (refusal in refusals) {
            val exchange = Fake(AppResult.Success(book("BTCUSDT", 1.0)))
            val gateway = SessionFallbackOrderBookGateway(relay = Fake(refusal), exchange = exchange)

            val result = gateway.load("BTCUSDT")

            assertEquals(refusal, result)
            assertEquals("$refusal must not open the fallback", 0, exchange.loads)
        }
    }

    @Test
    fun `a transport failure is not a missing session and does not open the fallback`() = runTest {
        val exchange = Fake(AppResult.Success(book("BTCUSDT", 1.0)))
        val gateway = SessionFallbackOrderBookGateway(
            relay = Fake(AppResult.Failure(ErrorKind.NETWORK)),
            exchange = exchange,
        )

        val result = gateway.load("BTCUSDT")

        assertTrue(result is AppResult.Failure)
        assertEquals(0, exchange.loads)
    }

    @Test
    fun `switching markets does not inherit the previous market's source`() = runTest {
        // The choice is keyed on the symbol, so a market the relay serves cannot end up polled at
        // the exchange because the market before it was not served. Held as a bare flag this would
        // silently outlive the symbol that set it.
        val publicBook = book("BTCUSDT", 77_766.4)
        val relayBook = book("ETHUSDT", 2_416.47)
        var relayRefuses = true
        val relay = object : OrderBookGateway {
            override suspend fun load(symbol: String, depth: Int): AppResult<OrderBook> =
                if (relayRefuses) {
                    depthUnavailable(DepthUnavailableReason.SESSION_REQUIRED)
                } else {
                    AppResult.Success(relayBook)
                }

            override fun stream(symbol: String): Flow<OrderBook> = flowOf(relayBook)
        }
        val gateway = SessionFallbackOrderBookGateway(
            relay = relay,
            exchange = Fake(AppResult.Success(publicBook), streamed = publicBook),
        )

        gateway.load("BTCUSDT")
        assertEquals(listOf(publicBook), gateway.stream("BTCUSDT").toList())

        relayRefuses = false
        gateway.load("ETHUSDT")
        assertEquals(listOf(relayBook), gateway.stream("ETHUSDT").toList())
    }

    @Test
    fun `the provenance line names one venue whichever path answered`() {
        // Both read the same half of the same exchange, so the reader is told the same thing and can
        // check the ladder against the same book. A second spelling would say the app had two ideas
        // about where its numbers come from.
        val gateway = SessionFallbackOrderBookGateway(
            relay = Fake(AppResult.Failure(ErrorKind.NETWORK), sourceName = "LBank Futures"),
            exchange = LBankPublicOrderBookGateway(),
        )

        assertEquals("LBank Futures", gateway.sourceName)
        assertEquals(gateway.sourceName, LBankPublicOrderBookGateway().sourceName)
    }

    @Test
    fun `the platform's own public route is tried before the exchange, and stops it`() = runTest {
        // The order TradeYar's public depth route was built for: our host answers, so an Iranian
        // handset never has to reach LBank's CDN at all.
        val ours = book("BTCUSDT", 79_961.5)
        val platform = Fake(AppResult.Success(ours), streamed = ours)
        val exchange = Fake(AppResult.Success(book("BTCUSDT", 77_766.4)))
        val gateway = SessionFallbackOrderBookGateway(
            relay = Fake(depthUnavailable(DepthUnavailableReason.SESSION_REQUIRED)),
            fallbacks = listOf(platform, exchange),
        )

        val result = gateway.load("BTCUSDT")

        assertEquals(ours, (result as AppResult.Success).value)
        assertEquals("the exchange must not be touched once our own route answered", 0, exchange.loads)
        // And the poll follows the one that answered, not the relay and not the venue.
        assertEquals(listOf(ours), gateway.stream("BTCUSDT").toList())
    }

    @Test
    fun `the exchange still answers when our own public route cannot`() = runTest {
        val venue = book("BTCUSDT", 77_766.4)
        val platform = Fake(AppResult.Failure(ErrorKind.NETWORK))
        val exchange = Fake(AppResult.Success(venue), streamed = venue)
        val gateway = SessionFallbackOrderBookGateway(
            relay = Fake(depthUnavailable(DepthUnavailableReason.SESSION_REQUIRED)),
            fallbacks = listOf(platform, exchange),
        )

        assertEquals(venue, (gateway.load("BTCUSDT") as AppResult.Success).value)
        assertEquals(1, platform.loads)
        assertEquals(listOf(venue), gateway.stream("BTCUSDT").toList())
    }

    @Test
    fun `every fallback failing still reports the relay's refusal`() = runTest {
        val relay = depthUnavailable(DepthUnavailableReason.SESSION_REQUIRED)
        val gateway = SessionFallbackOrderBookGateway(
            relay = Fake(relay),
            fallbacks = listOf(
                Fake(AppResult.Failure(ErrorKind.NETWORK)),
                Fake(depthOutage(DepthOutageReason.EXCHANGE_UNREACHABLE)),
            ),
        )

        val result = gateway.load("BTCUSDT")

        assertTrue(result is AppResult.Failure)
        assertEquals(
            "the reader has to sign in; which other doors were tried is not their situation",
            DepthUnavailableReason.SESSION_REQUIRED,
            (result as AppResult.Failure).depthUnavailableReason,
        )
    }
}
