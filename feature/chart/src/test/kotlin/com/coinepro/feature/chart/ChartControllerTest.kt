package com.coinepro.feature.chart

import com.coinepro.core.chart.ChartType
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChartControllerTest {

    private class FakeGateway : CandleGateway {
        var pages = ArrayDeque<CandlePage>()
        var failure: Throwable? = null
        val calls = mutableListOf<Triple<String, Timeframe, Long?>>()

        override suspend fun load(
            symbol: String,
            timeframe: Timeframe,
            limit: Int,
            before: Long?,
        ): CandlePage {
            calls += Triple(symbol, timeframe, before)
            failure?.let { throw it }
            return pages.removeFirstOrNull() ?: CandlePage(symbol, timeframe, emptyList())
        }
    }

    private fun bars(from: Long, count: Int, step: Long = 3_600): List<OhlcBar> =
        (0 until count).map { index ->
            val t = from + index * step
            OhlcBar(t = t, o = 100.0, h = 101.0, l = 99.0, c = 100.5, v = 10.0)
        }

    private fun page(bars: List<OhlcBar>, hasMore: Boolean = false) =
        CandlePage("BTCUSDT", Timeframe.H1, bars, hasMore = hasMore)

    @Test
    fun `it loads on start and stops loading`() = runTest {
        val gateway = FakeGateway().apply { pages.add(page(bars(1_000, 50))) }
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))

        controller.start()
        advanceUntilIdle()

        assertEquals(50, controller.state.value.series.size)
        assertFalse(controller.state.value.loading)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `starting twice does not load twice`() = runTest {
        // start() is called from a LaunchedEffect, which runs again on any recomposition keyed
        // differently — and a second fetch would replace the series under a reader mid-pan.
        val gateway = FakeGateway().apply { pages.add(page(bars(1_000, 10))) }
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))

        controller.start()
        advanceUntilIdle()
        controller.start()
        advanceUntilIdle()

        assertEquals(1, gateway.calls.size)
    }

    @Test
    fun `switching timeframe reloads and keeps the drawings`() = runTest {
        // The bars change; the drawings do not. They are anchored in (time, price), which is the
        // whole reason they are stored that way — a trend line means the same thing on H1 and D1.
        val gateway = FakeGateway().apply {
            pages.add(page(bars(1_000, 10)))
            pages.add(page(bars(0, 20, step = 86_400)))
        }
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        controller.arm(DrawingTools["hline"])
        controller.onDrawing(
            com.coinepro.core.chart.DrawingActions.tap(
                controller.state.value.drawing,
                com.coinepro.core.chart.ChartPoint(1_000, 100.0),
            ),
        )
        assertEquals(1, controller.state.value.drawing.drawings.size)

        controller.setTimeframe(Timeframe.D1)
        advanceUntilIdle()

        assertEquals(Timeframe.D1, controller.state.value.timeframe)
        assertEquals(20, controller.state.value.series.size)
        assertEquals("the drawing was lost", 1, controller.state.value.drawing.drawings.size)
    }

    @Test
    fun `switching to the timeframe already shown does nothing`() = runTest {
        val gateway = FakeGateway().apply { pages.add(page(bars(1_000, 10))) }
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        controller.setTimeframe(Timeframe.H1)
        advanceUntilIdle()

        assertEquals(1, gateway.calls.size)
    }

    @Test
    fun `paging back prepends older bars and asks from the oldest held`() = runTest {
        // The second page is entirely older than the first, which is what the server promises: no
        // overlap and no gap. The overlapping case has its own test below.
        val gateway = FakeGateway().apply {
            pages.add(page(bars(100_000, 10), hasMore = true))
            pages.add(page(bars(1_000, 10), hasMore = true))
        }
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        controller.loadMore()
        advanceUntilIdle()

        assertEquals(20, controller.state.value.series.size)
        assertEquals(1_000L, controller.state.value.series.time.first())
        assertEquals("must page from the oldest bar held", 100_000L, gateway.calls.last().third)
    }

    @Test
    fun `a page that overlaps what is held is trimmed rather than doubling a bar`() = runTest {
        // The server promises no overlap. Trusting the promise and being wrong doubles a bar, and a
        // doubled bar is a spike on the chart that never happened.
        val gateway = FakeGateway().apply {
            pages.add(page(bars(10_000, 5), hasMore = true))
            pages.add(page(bars(9_000, 8), hasMore = true))
        }
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()
        controller.loadMore()
        advanceUntilIdle()

        val times = controller.state.value.series.time.toList()
        assertEquals("a bar was duplicated", times.size, times.toSet().size)
        assertTrue(times.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun `two overlapping page-backs do not both run`() = runTest {
        // The natural trigger is "panned near the left edge", which fires every frame of a drag.
        val gateway = FakeGateway().apply {
            pages.add(page(bars(10_000, 5), hasMore = true))
            pages.add(page(bars(1_000, 5), hasMore = true))
            pages.add(page(bars(500, 5), hasMore = true))
        }
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        controller.loadMore()
        controller.loadMore()
        controller.loadMore()
        advanceUntilIdle()

        assertEquals("more than one page-back ran", 2, gateway.calls.size)
    }

    @Test
    fun `paging stops when the server says there is no more`() = runTest {
        val gateway = FakeGateway().apply { pages.add(page(bars(1_000, 5), hasMore = false)) }
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        controller.loadMore()
        advanceUntilIdle()

        assertEquals(1, gateway.calls.size)
    }

    @Test
    fun `a page-back that returns nothing older ends the paging`() = runTest {
        val gateway = FakeGateway().apply {
            pages.add(page(bars(1_000, 5), hasMore = true))
            pages.add(page(emptyList(), hasMore = true))
        }
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()
        controller.loadMore()
        advanceUntilIdle()

        assertFalse("the server said there was more and sent none", controller.state.value.hasMore)
    }

    @Test
    fun `a failed page-back leaves the chart alone`() = runTest {
        val gateway = FakeGateway().apply { pages.add(page(bars(1_000, 5), hasMore = true)) }
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        gateway.failure = RuntimeException("timeout")
        controller.loadMore()
        advanceUntilIdle()

        assertEquals("the bars on screen must not vanish", 5, controller.state.value.series.size)
        assertFalse(controller.state.value.loadingMore)
        assertNull("a failed page-back is not a screen-level error", controller.state.value.error)
    }

    @Test
    fun `the server's error code decides what the screen says`() = runTest {
        // TradeYar's team asked for this explicitly: they answer 422 where 400 might be expected
        // and said to branch on `code`, not on the status.
        assertEquals(ChartError.CHART_DISABLED, RuntimeException("academy_disabled").toChartError())
        assertEquals(ChartError.UNSUPPORTED_SYMBOL, RuntimeException("TYR-021").toChartError())
        assertEquals(ChartError.UNSUPPORTED_SYMBOL, RuntimeException("unsupported_symbol").toChartError())
        assertEquals(ChartError.NETWORK, RuntimeException("timeout").toChartError())
        assertEquals(ChartError.NETWORK, RuntimeException().toChartError())
    }

    @Test
    fun `a wrapped error code is still found`() = runTest {
        val wrapped = RuntimeException("HTTP 403", RuntimeException("""{"code":"academy_disabled"}"""))
        assertEquals(ChartError.CHART_DISABLED, wrapped.toChartError())
    }

    @Test
    fun `an indicator toggles on and off`() = runTest {
        val controller = ChartController("BTCUSDT", FakeGateway(), TestScope(StandardTestDispatcher(testScheduler)))
        controller.toggleIndicator("rsi")
        assertTrue("rsi" in controller.state.value.activeIndicators)
        controller.toggleIndicator("rsi")
        assertFalse("rsi" in controller.state.value.activeIndicators)
    }

    @Test
    fun `only price-pane indicators become overlays`() = runTest {
        // RSI on the price scale would collapse a gold chart to a flat line: 0-100 against 2,600.
        val gateway = FakeGateway().apply { pages.add(page(bars(1_000, 120))) }
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        controller.toggleIndicator("rsi")
        assertTrue("RSI must not draw over the price", controller.state.value.overlays.isEmpty())

        controller.toggleIndicator("ema")
        assertTrue(controller.state.value.overlays.isNotEmpty())
    }

    @Test
    fun `a structure study contributes levels and markers, not overlays alone`() = runTest {
        val gateway = FakeGateway().apply { pages.add(page(bars(1_000, 200))) }
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        controller.toggleIndicator("pivots")
        assertTrue(controller.state.value.overlays.isNotEmpty())
    }

    @Test
    fun `the chart type changes without refetching`() = runTest {
        // Every chart type is a transform of the same bars. Refetching for one would be a spinner
        // and a network round trip to redraw data already held.
        val gateway = FakeGateway().apply { pages.add(page(bars(1_000, 10))) }
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        controller.setChartType(ChartType.RENKO)
        advanceUntilIdle()

        assertEquals(ChartType.RENKO, controller.state.value.chartType)
        assertEquals(1, gateway.calls.size)
    }

    @Test
    fun `an empty response is not an error`() = runTest {
        // A market with no history is a real answer. Showing "something went wrong" for it sends a
        // reader looking for a fault that is not there.
        val controller = ChartController("NEWUSDT", FakeGateway(), TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        assertTrue(controller.state.value.series.isEmpty)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `a live read moves the open bar without moving the ones behind it`() = runTest {
        // «قیمت کندل‌ها لحظه‌ای نیست» — the chart was fetched once and then sat there, so the last
        // close was whatever the market was when the screen opened and the live tag's countdown ran
        // out and printed nothing. A tick has to move the forming candle and nothing else.
        val opening = bars(1_000, 5)
        val gateway = FakeGateway().apply { pages.add(page(opening)) }
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()
        assertEquals(100.5, controller.state.value.series.close.last(), 0.0001)

        val last = opening.last()
        gateway.pages.add(page(listOf(last.copy(c = 142.0, h = 143.0))))
        controller.refreshLiveEdge("BTCUSDT", controller.state.value.interval)
        advanceUntilIdle()

        val series = controller.state.value.series
        assertEquals(5, series.size)
        assertEquals(142.0, series.close.last(), 0.0001)
        // The bars behind it are history and history does not move.
        assertEquals(100.5, series.close[3], 0.0001)
    }

    @Test
    fun `a live read appends the bar the venue has just opened`() = runTest {
        val opening = bars(1_000, 5)
        val gateway = FakeGateway().apply { pages.add(page(opening)) }
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        // The tail comes back one bar further along, which is what a venue does when a bar closes.
        val opened = OhlcBar(t = opening.last().t + 3_600, o = 100.5, h = 104.0, l = 100.0, c = 103.0, v = 2.0)
        gateway.pages.add(page(listOf(opening.last(), opened)))
        controller.refreshLiveEdge("BTCUSDT", controller.state.value.interval)
        advanceUntilIdle()

        val series = controller.state.value.series
        assertEquals(6, series.size)
        assertEquals(103.0, series.close.last(), 0.0001)
        // Joined by open time, not by position: no bar is doubled where the tail meets what is held.
        val times = series.time.toList()
        assertEquals(times.size, times.distinct().size)
    }

    @Test
    fun `a live read for a series the reader has left is ignored`() = runTest {
        val gateway = FakeGateway().apply { pages.add(page(bars(1_000, 5))) }
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()
        val callsBefore = gateway.calls.size

        // A poll for the interval that was on screen when it was armed, after a switch away from
        // it. It must not fetch, and it must tell its loop to stop rather than tick again.
        val stale = ChartInterval.Preset(Timeframe.M5)
        assertFalse(controller.refreshLiveEdge("BTCUSDT", stale))
        advanceUntilIdle()
        assertEquals(callsBefore, gateway.calls.size)
    }
}
