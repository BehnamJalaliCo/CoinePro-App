package com.coinepro.core.signals

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.SignalDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SignalHistoryTruthTest {
    @Test
    fun `missing target hit status stays missing and is excluded from tp rate`() {
        val mapped = SignalDto(
            id = 7,
            market = "forex",
            symbol = "XAUUSD",
            direction = "BUY",
            status = "closed",
            targets = listOf(SignalTargetDto(level = 1, price = 2500.0)),
        ).toDomain(nowMs = 0L, MarketPlatform.COINEPRO_FX)!!

        assertNull(mapped.targets.single().hit)

        val summary = summarizeSignalPerformance(listOf(mapped))
        assertEquals(0, summary.tpHitRate.denominator)
        assertNull(summary.tpHitRate.percent)
    }

    @Test
    fun `history pagination advances by server page size when mapped rows are fewer`() = runBlocking {
        val gateway = PagingGateway()
        val controller = SignalController(gateway, CoroutineScope(coroutineContext))

        controller.refreshHistory()
        while (controller.historyState.value.loading) yield()

        val state = controller.historyState.value
        assertEquals(listOf(0, 50), gateway.forexOffsets)
        assertEquals(listOf(0), gateway.cryptoOffsets)
        assertEquals(59, state.items.size)
        assertEquals(60, state.expectedTotal)
        assertFalse(state.coverageComplete)
    }

    private class PagingGateway : SignalGateway {
        val forexOffsets = mutableListOf<Int>()
        val cryptoOffsets = mutableListOf<Int>()

        override suspend fun list(
            market: SignalMarketFilter,
            status: SignalStatusFilter,
            limit: Int,
            offset: Int,
        ): SignalPage {
            assertEquals(SignalStatusFilter.CLOSED, status)
            assertEquals(50, limit)
            return when (market) {
                SignalMarketFilter.FOREX -> {
                    forexOffsets += offset
                    when (offset) {
                        0 -> SignalPage((1L..49L).map(::historySignal), total = 60, serverTimeEpochMillis = null)
                        50 -> SignalPage((51L..60L).map(::historySignal), total = 60, serverTimeEpochMillis = null)
                        else -> SignalPage(emptyList(), total = 60, serverTimeEpochMillis = null)
                    }
                }
                SignalMarketFilter.CRYPTO -> {
                    cryptoOffsets += offset
                    SignalPage(emptyList(), total = 0, serverTimeEpochMillis = null)
                }
            }
        }

        override suspend fun detail(signalId: Long): TradingSignal = historySignal(signalId)
    }
}

private fun historySignal(id: Long) = TradingSignal(
    id = id,
    market = MarketType.FOREX,
    symbol = "XAUUSD",
    direction = SignalDirection.BUY,
    status = "closed",
    timeframe = "H1",
    strategy = null,
    confidence = null,
    entry = 100.0,
    entryZone = null,
    stopLoss = 99.0,
    targets = emptyList(),
    riskRewardTp1 = null,
    currentQuote = null,
    livePnlPercent = null,
    hitTarget = null,
    createdAt = "2026-08-20T10:00:00Z",
    closedAt = "2026-08-20T11:00:00Z",
)
