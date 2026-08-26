package com.coinepro.core.portfolio

import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortfolioControllerTest {

    private val now = 1_787_751_459L

    private fun trade(id: String, net: Double, closedAt: Long, symbol: String = "BTCUSDT") = ClosedTrade(
        id = id,
        symbol = symbol,
        direction = TradeDirection.BUY,
        volume = 0.01,
        entry = 90_000.0,
        exit = 91_000.0,
        openedAt = closedAt - 600,
        closedAt = closedAt,
        netProfit = net,
    )

    private class FakeGateway(
        private val answer: (Int, Long?, Long?) -> TradeHistoryPage,
    ) : PortfolioGateway {
        val requests = mutableListOf<Triple<Int, Long?, Long?>>()

        override suspend fun history(page: Int, perPage: Int, from: Long?, to: Long?): TradeHistoryPage {
            requests += Triple(page, from, to)
            return answer(page, from, to)
        }
    }

    private fun controller(gateway: PortfolioGateway, scheduler: kotlinx.coroutines.test.TestCoroutineScheduler) =
        PortfolioController(
            gateway = gateway,
            scope = TestScope(UnconfinedTestDispatcher(scheduler)),
            zone = ZoneOffset.UTC,
            nowSeconds = { now },
        )

    @Test
    fun `the first load derives every figure from the trades`() = runTest {
        val gateway = FakeGateway { _, _, _ ->
            TradeHistoryPage(
                trades = listOf(
                    trade("1", 100.0, now - 86_400),
                    trade("2", -40.0, now - 3_600),
                ),
                page = 1,
                total = 2,
                hasMore = false,
            )
        }
        val controller = controller(gateway, testScheduler)
        controller.start()

        val state = controller.state.value
        assertEquals(2, state.trades.size)
        assertEquals(60.0, state.stats.net, 1e-9)
        assertEquals(50.0, state.stats.winRate!!, 1e-9)
        assertEquals(1, state.bySymbol.size)
        assertFalse(state.loading)
        assertNull(state.error)
    }

    @Test
    fun `the default window is thirty days, not the server's maximum`() = runTest {
        // TradeYar caps at 31. Opening on 30 means the first paint is never the one that comes
        // back saying "we shortened your window" — a warning about a window the reader never chose.
        val gateway = FakeGateway { _, _, _ -> TradeHistoryPage(emptyList(), 1, 0, false) }
        controller(gateway, testScheduler).start()

        val (_, from, to) = gateway.requests.single()
        assertEquals(now, to)
        assertEquals(now - 30 * 86_400L, from)
    }

    @Test
    fun `the all-time window sends no bounds at all`() = runTest {
        val gateway = FakeGateway { _, _, _ -> TradeHistoryPage(emptyList(), 1, 0, false) }
        val controller = controller(gateway, testScheduler)
        controller.start()
        controller.setWindow(PortfolioWindow.ALL)

        val (_, from, to) = gateway.requests.last()
        assertNull(from)
        assertNull(to)
    }

    @Test
    fun `changing the window reloads and drops the old trades`() = runTest {
        var page = 0
        val gateway = FakeGateway { _, _, _ ->
            page++
            TradeHistoryPage(listOf(trade("t$page", 10.0, now - page * 3_600L)), 1, 1, false)
        }
        val controller = controller(gateway, testScheduler)
        controller.start()
        assertEquals(listOf("t1"), controller.state.value.trades.map { it.id })

        controller.setWindow(PortfolioWindow.WEEK)
        assertEquals("the window's trades, not both windows'", listOf("t2"), controller.state.value.trades.map { it.id })
    }

    @Test
    fun `selecting the window already shown does not refetch`() = runTest {
        val gateway = FakeGateway { _, _, _ -> TradeHistoryPage(emptyList(), 1, 0, false) }
        val controller = controller(gateway, testScheduler)
        controller.start()
        controller.setWindow(PortfolioWindow.MONTH)

        assertEquals("one cold LBank walk is enough", 1, gateway.requests.size)
    }

    @Test
    fun `paging appends and recomputes over everything held`() = runTest {
        val gateway = FakeGateway { page, _, _ ->
            when (page) {
                1 -> TradeHistoryPage(listOf(trade("1", 100.0, now - 7_200)), 1, 2, hasMore = true)
                else -> TradeHistoryPage(listOf(trade("2", -50.0, now - 10_800)), 2, 2, hasMore = false)
            }
        }
        val controller = controller(gateway, testScheduler)
        controller.start()
        controller.loadMore()

        val state = controller.state.value
        assertEquals(2, state.trades.size)
        // The point of recomputing: a win rate averaged per page would be 100 then 0, not 50.
        assertEquals(50.0, state.stats.winRate!!, 1e-9)
        assertEquals(50.0, state.stats.net, 1e-9)
        assertFalse(state.hasMore)
    }

    @Test
    fun `a trade that appears in two pages is counted once`() = runTest {
        // The natural cause is a trade closing while the reader scrolls, which shifts the page
        // boundary under them. Counted twice it would inflate the trade count, the win rate and
        // the total all at once.
        val gateway = FakeGateway { page, _, _ ->
            when (page) {
                1 -> TradeHistoryPage(listOf(trade("1", 100.0, now - 60)), 1, 2, hasMore = true)
                else -> TradeHistoryPage(
                    listOf(trade("1", 100.0, now - 60), trade("2", 20.0, now - 120)),
                    2,
                    2,
                    hasMore = false,
                )
            }
        }
        val controller = controller(gateway, testScheduler)
        controller.start()
        controller.loadMore()

        assertEquals(2, controller.state.value.trades.size)
        assertEquals(120.0, controller.state.value.stats.net, 1e-9)
    }

    @Test
    fun `a failed page-back leaves the trades on screen`() = runTest {
        var call = 0
        val gateway = FakeGateway { _, _, _ ->
            call++
            if (call == 1) {
                TradeHistoryPage(listOf(trade("1", 100.0, now - 60)), 1, 2, hasMore = true)
            } else {
                throw IllegalStateException("network")
            }
        }
        val controller = controller(gateway, testScheduler)
        controller.start()
        controller.loadMore()

        assertEquals(1, controller.state.value.trades.size)
        assertNull("no banner over figures that are still correct", controller.state.value.error)
        assertFalse(controller.state.value.loadingMore)
    }

    @Test
    fun `a missing exchange key reads as not connected, not as a network failure`() = runTest {
        // Two different screens follow from these: "link an account" and "try again". Offering the
        // wrong one wastes the reader's time in both directions.
        val gateway = FakeGateway { _, _, _ -> throw IllegalStateException("venue_not_connected") }
        val controller = controller(gateway, testScheduler)
        controller.start()

        assertEquals(PortfolioError.NOT_CONNECTED, controller.state.value.error)
    }

    @Test
    fun `anything else is a network failure`() = runTest {
        val gateway = FakeGateway { _, _, _ -> throw IllegalStateException("502 Bad Gateway") }
        val controller = controller(gateway, testScheduler)
        controller.start()

        assertEquals(PortfolioError.NETWORK, controller.state.value.error)
        assertTrue(controller.state.value.trades.isEmpty())
    }

    @Test
    fun `a narrowed window is reported, and a matching one is not`() = runTest {
        val asked = 31 * 86_400L
        val narrowed = FakeGateway { _, _, _ ->
            TradeHistoryPage(
                trades = emptyList(),
                page = 1,
                total = 0,
                hasMore = false,
                windowFrom = now - asked,
                windowTo = now,
            )
        }
        val controller = controller(narrowed, testScheduler)
        controller.start()
        // Asked for 30 days and got 31 — wider, not narrower, so there is nothing to warn about.
        assertNull(controller.state.value.servedWindow)

        val cut = FakeGateway { _, _, _ ->
            TradeHistoryPage(
                trades = emptyList(),
                page = 1,
                total = 0,
                hasMore = false,
                windowFrom = now - 7 * 86_400L,
                windowTo = now,
            )
        }
        val second = controller(cut, testScheduler)
        second.start()
        assertEquals(now - 7 * 86_400L..now, second.state.value.servedWindow)
    }

    @Test
    fun `a couple of seconds of clock skew is not a narrowed window`() = runTest {
        // The server anchors its window on its own clock and this app on the phone's. An exact
        // comparison would flag every single response.
        val gateway = FakeGateway { _, _, _ ->
            TradeHistoryPage(
                trades = emptyList(),
                page = 1,
                total = 0,
                hasMore = false,
                windowFrom = now - 30 * 86_400L + 45,
                windowTo = now,
            )
        }
        val controller = controller(gateway, testScheduler)
        controller.start()
        assertNull(controller.state.value.servedWindow)
    }

    @Test
    fun `truncation is carried into the state so the totals can be labelled partial`() = runTest {
        val gateway = FakeGateway { _, _, _ ->
            TradeHistoryPage(
                trades = listOf(trade("1", 5.0, now - 60)),
                page = 1,
                total = 1,
                hasMore = false,
                truncated = true,
            )
        }
        val controller = controller(gateway, testScheduler)
        controller.start()
        assertTrue(controller.state.value.truncated)
    }

    @Test
    fun `retry after a failure loads`() = runTest {
        var fail = true
        val gateway = FakeGateway { _, _, _ ->
            if (fail) throw IllegalStateException("network")
            TradeHistoryPage(listOf(trade("1", 10.0, now - 60)), 1, 1, false)
        }
        val controller = controller(gateway, testScheduler)
        controller.start()
        assertEquals(PortfolioError.NETWORK, controller.state.value.error)

        fail = false
        controller.retry()
        assertNull(controller.state.value.error)
        assertEquals(1, controller.state.value.trades.size)
    }

    @Test
    fun `start does not retry a failure by itself`() = runTest {
        // Otherwise every recomposition that calls start would fire another request at a server
        // that is already failing.
        val gateway = FakeGateway { _, _, _ -> throw IllegalStateException("network") }
        val controller = controller(gateway, testScheduler)
        controller.start()
        controller.start()
        controller.start()

        assertEquals(1, gateway.requests.size)
    }
}
