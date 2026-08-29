package com.coinepro.core.chartevents

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartEvent
import com.coinepro.core.chart.EventKind
import com.coinepro.core.chart.EventMark
import com.coinepro.core.chart.EventVisibility
import com.coinepro.core.chart.Importance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChartEventControllerTest {

    /** Counts what it was asked, so a test can assert a *lack* of a second read. */
    private class CountingFeed(private val events: List<ChartEvent>) : ChartEventFeed {
        var reads = 0
            private set
        val windows = mutableListOf<Pair<Long, Long>>()

        override suspend fun events(symbol: String, fromSeconds: Long, toSeconds: Long): List<ChartEvent> {
            reads++
            windows += fromSeconds to toSeconds
            return events.filter { it.at in fromSeconds..toSeconds }
        }
    }

    private fun event(at: Long, kind: EventKind = EventKind.NEWS) = ChartEvent(
        at = at,
        kind = kind,
        title = "تیتر",
        detail = null,
        importance = Importance.HIGH,
    )

    /** Ten bars, ten thousand seconds apart, starting at 100000 — roughly a daily chart. */
    private val series = CandleSeries(
        (0 until 10).map { index ->
            Candle(t = 100_000L + index * 10_000L, o = 1.0, h = 2.0, l = 0.5, c = 1.5, v = 1.0)
        },
    )

    @Test
    fun `panning inside a window already fetched does not ask the feed a second time`() = runTest {
        val feed = CountingFeed(listOf(event(150_000)))
        val controller = ChartEventController(
            feed = feed,
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            now = { 0L },
        )

        controller.onVisibleRange("BTCUSDT", 100_000, 200_000)
        assertEquals(1, feed.reads)

        // A drag of a tenth of a screen, then a fifth. Both land inside what was fetched, because
        // the fetch deliberately reached half a screen past each edge.
        controller.onVisibleRange("BTCUSDT", 110_000, 210_000)
        controller.onVisibleRange("BTCUSDT", 120_000, 220_000)

        assertEquals(1, feed.reads)
        assertEquals(listOf(150_000L), controller.state.value.events.map(ChartEvent::at))
    }

    @Test
    fun `a pan past what was fetched does ask again, for the new window`() = runTest {
        val feed = CountingFeed(listOf(event(150_000), event(400_000)))
        val controller = ChartEventController(
            feed = feed,
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            now = { 0L },
        )

        controller.onVisibleRange("BTCUSDT", 100_000, 200_000)
        controller.onVisibleRange("BTCUSDT", 350_000, 450_000)

        assertEquals(2, feed.reads)
        assertEquals(listOf(400_000L), controller.state.value.events.map(ChartEvent::at))
    }

    @Test
    fun `the window fetched is wider than the window asked for`() = runTest {
        val feed = CountingFeed(emptyList())
        val controller = ChartEventController(
            feed = feed,
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            now = { 0L },
        )

        controller.onVisibleRange("BTCUSDT", 100_000, 200_000)

        val (from, to) = feed.windows.single()
        assertTrue(from < 100_000)
        assertTrue(to > 200_000)
    }

    @Test
    fun `switching a kind off re-reads what is already held rather than fetching again`() = runTest {
        val feed = CountingFeed(listOf(event(150_000, EventKind.NEWS), event(160_000, EventKind.ECONOMIC)))
        val controller = ChartEventController(
            feed = feed,
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            now = { 0L },
        )
        controller.onVisibleRange("BTCUSDT", 100_000, 200_000)
        controller.setVisibility(EventVisibility.Everything)

        val both = controller.state.value.marks(series, 0, 9)
        assertEquals(listOf(5, 6), both.map(EventMark::barIndex))

        controller.setVisible(EventKind.ECONOMIC, false)

        val newsOnly = controller.state.value.marks(series, 0, 9)
        assertEquals(listOf(5), newsOnly.map(EventMark::barIndex))
        assertEquals(1, feed.reads)
    }

    @Test
    fun `a new instrument does not keep the previous one's events on the axis`() = runTest {
        val feed = CountingFeed(listOf(event(150_000)))
        val controller = ChartEventController(
            feed = feed,
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            now = { 0L },
        )
        controller.onVisibleRange("BTCUSDT", 100_000, 200_000)

        controller.onVisibleRange("XAUUSD", 900_000, 1_000_000)

        assertEquals("XAUUSD", controller.state.value.symbol)
        assertTrue(controller.state.value.events.isEmpty())
    }

    @Test
    fun `a stored filter is put back, and never stored means news alone`() = runTest {
        val controller = ChartEventController(
            feed = CountingFeed(emptyList()),
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            now = { 0L },
        )

        controller.restoreVisibility(EventVisibility.Nothing.encode())
        assertTrue(controller.state.value.visibility.isNothing)

        controller.restoreVisibility(null)
        assertEquals(EventVisibility.Default, controller.state.value.visibility)
    }

    @Test
    fun `a read that failed leaves the reader the server's own wording and no marks`() = runTest {
        val failing = object : ChartEventFeed {
            override suspend fun events(symbol: String, fromSeconds: Long, toSeconds: Long): List<ChartEvent> =
                throw IllegalStateException("boom")
        }
        val controller = ChartEventController(
            feed = failing,
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            now = { 0L },
        )

        controller.onVisibleRange("BTCUSDT", 100_000, 200_000)

        assertTrue(controller.state.value.events.isEmpty())
        assertFalse(controller.state.value.loading)
    }
}
