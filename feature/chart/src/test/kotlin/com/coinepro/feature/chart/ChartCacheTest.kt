package com.coinepro.feature.chart

import com.coinepro.core.marketdata.CandleCache
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cached chart: something true to draw before the network answers.
 *
 * "The chart won't come up" is the loudest complaint about every app in this category, and 19.3% of
 * negative chart mentions in Persian-language reviews — larger than the next category by two and a
 * half times. The reviews put the threshold sharply: instant is the stated baseline, three seconds
 * is noticed, five seconds reliably produces a one-star review.
 *
 * None of that is fixed by a faster request, which is why these tests are about what is on screen
 * *while* the request is out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartCacheTest {

    private fun bars(count: Int, base: Double) = (0 until count).map { index ->
        val price = base + index
        OhlcBar(t = 1_700_000_000L + index * 3600, o = price, h = price + 1, l = price - 1, c = price, v = 1.0)
    }

    private class FakeCache(private val stored: List<OhlcBar>) : CandleCache {
        var written: List<OhlcBar> = emptyList()
        override suspend fun read(symbol: String, timeframe: Timeframe, limit: Int) = stored
        override suspend fun write(symbol: String, timeframe: Timeframe, bars: List<OhlcBar>) {
            written = bars
        }
        override suspend fun clear() = Unit
    }

    /** A gateway that never answers, so the only thing on screen can be the cache. */
    private class NeverAnswers : CandleGateway {
        override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?): CandlePage {
            kotlinx.coroutines.awaitCancellation()
        }
    }

    private class Answers(private val bars: List<OhlcBar>) : CandleGateway {
        override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?) =
            CandlePage(symbol = symbol, timeframe = timeframe, candles = bars)
    }

    @Test
    fun `the chart draws the cache while the fetch is still out`() = runTest {
        val cache = FakeCache(bars(50, base = 100.0))
        val controller = ChartController("BTCUSDT", NeverAnswers(), this, cache = cache)
        controller.start()
        advanceUntilIdle()

        assertEquals(50, controller.state.value.series.size)
        // Still loading, and that is not a contradiction: the fetch is out, the spinner belongs,
        // and the reader has something real to look at while it runs.
        assertTrue(controller.state.value.loading)
        // The gateway never answers, so the load job would keep this test's scope alive.
        coroutineContext.cancelChildren()
    }

    @Test
    fun `the network answer replaces the cached bars`() = runTest {
        val cache = FakeCache(bars(50, base = 100.0))
        val controller = ChartController("BTCUSDT", Answers(bars(200, base = 900.0)), this, cache = cache)
        controller.start()
        advanceUntilIdle()

        assertEquals(200, controller.state.value.series.size)
        assertEquals(900.0, controller.state.value.series.bars.first().o, 1e-9)
        assertTrue(!controller.state.value.loading)
    }

    @Test
    fun `a fetched page is written back`() = runTest {
        val cache = FakeCache(emptyList())
        val controller = ChartController("BTCUSDT", Answers(bars(120, base = 10.0)), this, cache = cache)
        controller.start()
        advanceUntilIdle()
        assertEquals(120, cache.written.size)
    }

    @Test
    fun `an empty cache changes nothing`() = runTest {
        val cache = FakeCache(emptyList())
        val controller = ChartController("BTCUSDT", Answers(bars(30, base = 5.0)), this, cache = cache)
        controller.start()
        advanceUntilIdle()
        assertEquals(30, controller.state.value.series.size)
    }

    @Test
    fun `a cache that throws does not break the chart`() = runTest {
        // The contract that matters most: a cache which can fail a chart open turns a slow path
        // into a broken one, which is strictly worse than having no cache at all.
        val angry = object : CandleCache {
            override suspend fun read(symbol: String, timeframe: Timeframe, limit: Int): List<OhlcBar> =
                error("disk is on fire")
            override suspend fun write(symbol: String, timeframe: Timeframe, bars: List<OhlcBar>) =
                error("disk is still on fire")
            override suspend fun clear() = Unit
        }
        val controller = ChartController("BTCUSDT", Answers(bars(40, base = 7.0)), this, cache = angry)
        controller.start()
        advanceUntilIdle()
        assertEquals(40, controller.state.value.series.size)
        assertEquals(null, controller.state.value.error)
    }
}
