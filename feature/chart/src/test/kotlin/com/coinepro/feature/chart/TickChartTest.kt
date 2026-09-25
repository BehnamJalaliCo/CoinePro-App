package com.coinepro.feature.chart

import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Tick
import com.coinepro.core.marketdata.TickHistory
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Seconds and tick charts drawn from the server's history (5.17.0), not from nothing. */
@OptIn(ExperimentalCoroutinesApi::class)
class TickChartTest {

    private class Hourly : CandleGateway {
        override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?): CandlePage =
            CandlePage(
                symbol,
                timeframe,
                if (before == null) (0 until 50).map { OhlcBar(1_700_000_000L + it * 3_600L, 1.0, 2.0, 0.5, 1.5, 1.0) } else emptyList(),
            )
    }

    private class Server : TickHistory {
        override suspend fun ticks(symbol: String, limit: Int, beforeMs: Long?): List<Tick> =
            if (beforeMs != null) emptyList() else (0 until 55).map { Tick(1_700_000_000_000L + it * 250L, 100.0 + it, 1.0) }

        override suspend fun seconds(symbol: String, seconds: Int, limit: Int, before: Long?): List<OhlcBar> =
            if (before != null) emptyList() else (0 until 20).map { OhlcBar(1_700_000_000L + it * seconds, 1.0, 2.0, 0.5, 1.5, 1.0) }
    }

    @Test
    fun `a tick chart opens on the venue's trades, ten to a bar`() = runTest {
        val chart = ChartController("BTCUSDT", Hourly(), TestScope(StandardTestDispatcher(testScheduler)), tickHistory = Server())
        chart.start()
        advanceUntilIdle()
        chart.setInterval(ChartInterval.Ticks(10))
        advanceUntilIdle()
        assertEquals(6, chart.state.value.series.size)
        assertEquals(100.0, chart.state.value.series.bars.first().o, 0.0)
        assertEquals(154.0, chart.state.value.series.bars.last().c, 0.0)
    }

    @Test
    fun `a seconds chart opens on the venue's seconds bars`() = runTest {
        val chart = ChartController("BTCUSDT", Hourly(), TestScope(StandardTestDispatcher(testScheduler)), tickHistory = Server())
        chart.start()
        advanceUntilIdle()
        chart.setInterval(ChartInterval.Seconds(5))
        advanceUntilIdle()
        assertEquals(20, chart.state.value.series.size)
    }
}
