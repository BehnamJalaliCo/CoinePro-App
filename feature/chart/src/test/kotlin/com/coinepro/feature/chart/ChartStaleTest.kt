package com.coinepro.feature.chart

import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A timeframe change never blanks the chart (4.74.0, run K item 1b and 1c).
 *
 * ### What this is a test for
 *
 * The owner recorded two hundred and eighty-seven seconds of the app and the single worst thing in
 * it was not a dropped frame — the chart holds a hundred and twenty of those a second. It was
 * tapping M15 and getting a white rectangle with a spinner in it for five seconds, then «چارت
 * بارگیری نشد». Four of the strip's bar lengths did that.
 *
 * Three separate defects sat behind it, and each one has a test here: the switch **emptied** the
 * series before asking for the new one, the request had **no deadline and no second attempt**, and
 * a failure over a chart that had perfectly good candles on it produced a **full-screen error**.
 * None of those needed a device to find and none of them needs one to pin down.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartStaleTest {

    private fun bars(from: Long, count: Int, step: Long = 3_600): List<OhlcBar> =
        (0 until count).map { index ->
            val t = from + index * step
            OhlcBar(t = t, o = 100.0, h = 101.0, l = 99.0, c = 100.5, v = 10.0)
        }

    /** Answers the first call and then holds the second open until somebody gives up on it. */
    private class HangingGateway(private val first: CandlePage, private val hangAfter: Int = 1) : CandleGateway {
        var calls = 0

        override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?): CandlePage {
            calls += 1
            if (calls > hangAfter) awaitCancellation()
            return first
        }
    }

    /**
     * Answers after [delayMillis], and refuses everything once [refusing] is set.
     *
     * Switched rather than counted, unlike [FlakyGateway]: a controller that has started also fills
     * its archive, so «the second call» is not the reader's second request and a test that counted
     * would be asserting about a page request it never meant to name.
     */
    private class SwitchableGateway(
        private val page: CandlePage,
        private val delayMillis: Long = 0L,
    ) : CandleGateway {
        var refusing = false

        override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?): CandlePage {
            if (delayMillis > 0L) kotlinx.coroutines.delay(delayMillis)
            if (refusing) throw RuntimeException("HTTP 502 from the candle feed")
            return page.copy(timeframe = timeframe)
        }
    }

    /** Answers, then refuses, then answers again — the shape of a venue under a burst. */
    private class FlakyGateway(private val page: CandlePage, private val refuseCalls: Set<Int>) : CandleGateway {
        var calls = 0

        override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?): CandlePage {
            calls += 1
            if (calls in refuseCalls) throw RuntimeException("HTTP 502 from the candle feed")
            return page.copy(timeframe = timeframe)
        }
    }

    @Test
    fun `the previous interval's bars stay on the chart while the new ones load`() = runTest {
        val first = CandlePage("BTCUSDT", Timeframe.H1, bars(1_000, 40))
        val gateway = HangingGateway(first)
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()
        assertEquals(40, controller.state.value.series.size)

        controller.setTimeframe(Timeframe.M15)
        // Not `advanceUntilIdle`: the point is the state *during* the load, which is the interval
        // the reader spends looking at the screen and the one that used to be blank.
        advanceTimeBy(1_000)

        val during = controller.state.value
        assertEquals("the chart was emptied", 40, during.series.size)
        assertTrue("and nothing said the bars were on their way out", during.stale)
        assertTrue(during.loading)
        assertNull("a switch in flight is not a failure", during.error)
    }

    @Test
    fun `the dimming comes off in the same frame the new bars arrive`() = runTest {
        val page = CandlePage("BTCUSDT", Timeframe.H1, bars(1_000, 40))
        val gateway = FlakyGateway(page, refuseCalls = emptySet())
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        controller.setTimeframe(Timeframe.M15)
        advanceUntilIdle()

        val after = controller.state.value
        assertFalse("the chart would stay dim for ever", after.stale)
        assertFalse(after.loading)
        assertNull(after.error)
    }

    @Test
    fun `a feed that never answers is given up on and asked once more`() = runTest {
        val first = CandlePage("BTCUSDT", Timeframe.H1, bars(1_000, 40))
        val gateway = HangingGateway(first)
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        controller.setTimeframe(Timeframe.M15)
        // One deadline short of the first timeout: still waiting, still showing the old bars.
        advanceTimeBy(7_000)
        assertTrue("given up on too early", controller.state.value.loading)
        assertEquals(2, gateway.calls)

        advanceUntilIdle()

        val after = controller.state.value
        assertEquals("the second attempt was never made", 3, gateway.calls)
        assertFalse("the spinner never stopped", after.loading)
        assertNotNull("and the reader was never told", after.error)
        assertEquals("the candles were thrown away with the failure", 40, after.series.size)
        assertFalse("a chart nobody is replacing must not stay at forty per cent", after.stale)
    }

    @Test
    fun `a venue that refuses once and answers the second time draws a chart, not a banner`() = runTest {
        // The failure this fixes is overwhelmingly the first request after a switch — an idle
        // connection, a token that had just gone stale — and the reader should never see it.
        val page = CandlePage("BTCUSDT", Timeframe.H1, bars(1_000, 40))
        val gateway = FlakyGateway(page, refuseCalls = setOf(2))
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        controller.setTimeframe(Timeframe.M15)
        advanceUntilIdle()

        assertEquals(3, gateway.calls)
        assertNull("a retried failure is not news", controller.state.value.error)
        assertEquals(40, controller.state.value.series.size)
        assertFalse(controller.state.value.stale)
    }

    @Test
    fun `a retry dims the chart and undims it, like a timeframe change does`(): Unit = runTest {
        // The other way into the dimmed state, and the one run Ω2 asks be pinned: `reload()` off the
        // pull-to-refresh. It was only ever exercised through `setTimeframe` and the two set the flag
        // in different places, so a change to one could leave the other dim for ever — which on a
        // chart is not a visible bug, it is a chart that quietly looks wrong at forty per cent alpha.
        // `retry()` is `reload()`'s one public door — the banner's «تلاش دوباره» and the pull to
        // refresh both come through it.
        val page = CandlePage("BTCUSDT", Timeframe.H1, bars(1_000, 40))
        // Slow enough that the mid-flight state is observable, well inside the controller's deadline.
        val gateway = SwitchableGateway(page, delayMillis = 3_000L)
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()
        assertFalse(controller.state.value.stale)

        controller.retry()
        advanceTimeBy(1_000)
        assertTrue("a refresh over good candles must dim them, not blank them", controller.state.value.stale)
        assertEquals("and must not throw the bars away", 40, controller.state.value.series.size)

        advanceUntilIdle()
        assertFalse("the chart stayed at forty per cent after a refresh", controller.state.value.stale)
    }

    @Test
    fun `a refusal over a chart that already has candles leaves it undimmed and readable`(): Unit = runTest {
        // The state a reader is most likely to be left in: offline, pulling to refresh, and refused.
        // The bars are still true — they are the last ones the venue sent — so the chart must come
        // back to full strength with the news in a banner rather than sit dimmed behind an error.
        val page = CandlePage("BTCUSDT", Timeframe.H1, bars(1_000, 40))
        val gateway = SwitchableGateway(page)
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        gateway.refusing = true
        controller.retry()
        advanceUntilIdle()

        val after = controller.state.value
        assertNotNull("a refusal the reader can act on was swallowed", after.error)
        assertEquals(40, after.series.size)
        assertFalse("a failed refresh left the chart dim", after.stale)
        assertFalse(after.loading)
    }

    @Test
    fun `a cold open with nothing cached is the one case that still shows a skeleton`() = runTest {
        val gateway = HangingGateway(CandlePage("BTCUSDT", Timeframe.H1, emptyList()), hangAfter = 0)
        val controller = ChartController("BTCUSDT", gateway, TestScope(StandardTestDispatcher(testScheduler)))

        controller.start()
        advanceTimeBy(1_000)

        val during = controller.state.value
        assertTrue(during.loading)
        assertTrue(during.series.isEmpty)
        // Nothing is being replaced, so nothing is stale — which is what leaves the skeleton to the
        // first load alone, as item (b) asks.
        assertFalse(during.stale)
    }
}
