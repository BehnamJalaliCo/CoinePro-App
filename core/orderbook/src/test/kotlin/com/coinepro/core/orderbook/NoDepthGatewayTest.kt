package com.coinepro.core.orderbook

import com.coinepro.core.common.AppResult
import com.coinepro.core.common.ErrorKind
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The refusal, which is the answer this app gives on MT5 and — until TradeYar relays LBank's book —
 * on crypto too.
 *
 * The property under test is not "it fails". It is that it fails *in a way a screen can read*: a
 * named reason rather than an empty book, and a stream that finishes rather than one that waits.
 * Both of the wrong answers here draw a screen that looks like it is working.
 */
class NoDepthGatewayTest {

    @Test
    fun `a feed without depth refuses with a reason rather than answering an empty book`() = runTest {
        val gateway = NoDepthGateway(DepthUnavailableReason.FEED_PUBLISHES_NO_DEPTH)

        val result = gateway.load("XAUUSD")

        assertTrue("a refusal must not arrive as a book", result is AppResult.Failure)
        val failure = result as AppResult.Failure
        assertEquals(DepthUnavailableReason.FEED_PUBLISHES_NO_DEPTH, failure.depthUnavailableReason)
    }

    @Test
    fun `the refusal is not a retryable transport failure`() {
        // VALIDATION rather than NETWORK or SERVER, because the request will never become valid for
        // this feed. Anything that offers a retry on the strength of the kind must not offer one
        // here.
        val failure = depthUnavailable(DepthUnavailableReason.FEED_PUBLISHES_NO_DEPTH)
        assertEquals(ErrorKind.VALIDATION, failure.kind)
    }

    @Test
    fun `every refusal stays distinguishable, because they have different futures`() {
        // Four sentences on screen, four reasons here, and the screen picks by value. Collapsed to
        // one they would all read as the most pessimistic of them.
        DepthUnavailableReason.entries.forEach { reason ->
            assertEquals(reason, depthUnavailable(reason).depthUnavailableReason)
        }
    }

    @Test
    fun `an outage is a failure with a retry, not a refusal, and never reads as one`() {
        // The split the screen turns on. `SYMBOL_DELISTED` and `EXCHANGE_UNREACHABLE` both arrive
        // as a 502 from the same route; one is permanent and one ends on its own, and reading the
        // second as the first would tell a reader a live market had been removed from the exchange.
        val outage = depthOutage(DepthOutageReason.EXCHANGE_UNREACHABLE)
        assertEquals(DepthOutageReason.EXCHANGE_UNREACHABLE, outage.depthOutageReason)
        assertNull("an outage must never be read as a refusal", outage.depthUnavailableReason)
        assertEquals(ErrorKind.SERVER, outage.kind)

        val refusal = depthUnavailable(DepthUnavailableReason.SYMBOL_DELISTED)
        assertNull("a refusal must never be read as an outage", refusal.depthOutageReason)
    }

    @Test
    fun `both outage reasons survive the round trip through the failure`() {
        DepthOutageReason.entries.forEach { reason ->
            assertEquals(reason, depthOutage(reason).depthOutageReason)
        }
    }

    @Test
    fun `an ordinary failure carries no depth reason, so the two are never confused`() {
        val network = AppResult.Failure(ErrorKind.NETWORK)
        assertNull(network.depthUnavailableReason)
        assertNull(network.depthOutageReason)
        // And a validation failure from somewhere else is not mistaken for a refusal either.
        assertNull(AppResult.Failure(ErrorKind.VALIDATION, message = "bad symbol").depthUnavailableReason)
        // Nor a plain server failure for a named outage: an ordinary 500 has no upstream story and
        // must keep the generic sentence rather than borrowing the exchange's.
        assertNull(AppResult.Failure(ErrorKind.SERVER).depthOutageReason)
    }

    @Test
    fun `the stream finishes instead of waiting forever`() = runTest {
        // The whole point. A flow that never emits and never completes is what produces a spinner
        // that turns for the life of the screen; this one completes, so the collector can stop.
        val emitted = NoDepthGateway(DepthUnavailableReason.ENDPOINT_NOT_SERVED).stream("XAUUSD").toList()
        assertTrue("a refusing stream must emit nothing at all", emitted.isEmpty())
    }

    @Test
    fun `the controller turns the refusal into a settled screen with no retry`() = runTest {
        val controller = OrderBookController(
            gateway = NoDepthGateway(DepthUnavailableReason.FEED_PUBLISHES_NO_DEPTH, sourceName = "MetaTrader 5"),
            scope = this,
        )

        controller.start("XAUUSD")
        testScheduler.runCurrent()

        val state = controller.state.value
        assertNotNull(state.unavailable)
        assertEquals(DepthUnavailableReason.FEED_PUBLISHES_NO_DEPTH, state.unavailable)
        assertTrue("a settled refusal must not still be loading", !state.loading)
        assertTrue("a refusal is not a failure and must not offer a retry", !state.failed)
        assertNull(state.book)
        assertEquals("MetaTrader 5", state.sourceName)

        // And refreshing changes nothing, because there is nothing to change.
        controller.refresh()
        testScheduler.runCurrent()
        assertTrue(!controller.state.value.loading)
    }
}
