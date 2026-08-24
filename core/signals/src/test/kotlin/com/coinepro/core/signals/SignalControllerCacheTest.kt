package com.coinepro.core.signals

import com.coinepro.core.common.MessageKey
import com.coinepro.core.common.UiMessage
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.SignalDirection
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalControllerCacheTest {
    @Test
    fun `network failure keeps cached history explicit and reports refresh error`() = runBlocking {
        val cached = CachedSignalHistory(
            items = listOf(cacheSignal(7)),
            expectedTotal = 4,
            coverageComplete = false,
            cachedAtEpochMillis = 1234L,
        )
        val cache = FakeHistoryCache(cached)
        val gateway = object : SignalGateway {
            override suspend fun list(
                market: SignalMarketFilter,
                status: SignalStatusFilter,
                limit: Int,
                offset: Int,
            ): SignalPage = throw IOException("offline")

            override suspend fun detail(signalId: Long): TradingSignal = error("unused")
        }
        val controller = SignalController(gateway, CoroutineScope(coroutineContext), cache)

        controller.refreshHistory()
        while (controller.historyState.value.loading) yield()

        val state = controller.historyState.value
        assertEquals(listOf(7L), state.items.map(TradingSignal::id))
        assertEquals(4, state.expectedTotal)
        assertFalse(state.coverageComplete)
        assertTrue(state.fromCache)
        assertEquals(1234L, state.cacheStoredAtEpochMillis)
        // The refresh failure is reported as owned copy behind the cached-provenance lead-in.
        // The IOException's own text is deliberately not surfaced: it is diagnostic output, not
        // something a reader can act on, and it would be untranslatable.
        assertEquals(
            UiMessage.Prefixed(
                MessageKey.CACHED_HISTORY_SHOWN,
                UiMessage.Local(MessageKey.SIGNAL_HISTORY_UNAVAILABLE),
            ),
            state.error,
        )
    }

    @Test
    fun `successful refresh replaces cache and removes cached provenance`() = runBlocking {
        val cache = FakeHistoryCache(
            CachedSignalHistory(
                items = listOf(cacheSignal(1)),
                expectedTotal = 1,
                coverageComplete = true,
                cachedAtEpochMillis = 10L,
            ),
        )
        val gateway = PagingGateway(listOf(cacheSignal(20), cacheSignal(21)))
        val controller = SignalController(
            gateway = gateway,
            scope = CoroutineScope(coroutineContext),
            historyCache = cache,
            nowMillis = { 9000L },
        )

        controller.refreshHistory()
        while (controller.historyState.value.loading) yield()

        val state = controller.historyState.value
        assertEquals(listOf(21L, 20L), state.items.map(TradingSignal::id))
        assertFalse(state.fromCache)
        assertEquals(null, state.cacheStoredAtEpochMillis)
        assertEquals(null, state.error)
        assertNotNull(cache.replaced)
        assertEquals(9000L, cache.replaced?.cachedAtEpochMillis)
    }

    @Test
    fun `membership loss clears account history cache`() = runBlocking {
        val cache = FakeHistoryCache(
            CachedSignalHistory(listOf(cacheSignal(1)), 1, true, 10L),
        )
        val gateway = object : SignalGateway {
            override suspend fun list(
                market: SignalMarketFilter,
                status: SignalStatusFilter,
                limit: Int,
                offset: Int,
            ): SignalPage = throw SignalMembershipRequiredException()

            override suspend fun detail(signalId: Long): TradingSignal = error("unused")
        }
        val controller = SignalController(gateway, CoroutineScope(coroutineContext), cache)

        controller.refreshHistory()
        while (controller.historyState.value.loading) yield()

        assertTrue(controller.historyState.value.membershipRequired)
        assertTrue(controller.historyState.value.items.isEmpty())
        assertTrue(cache.cleared)
    }

    private class FakeHistoryCache(
        private var snapshot: CachedSignalHistory?,
    ) : SignalHistoryCache {
        var replaced: CachedSignalHistory? = null
        var cleared = false

        override suspend fun read(): CachedSignalHistory? = snapshot

        override suspend fun replace(snapshot: CachedSignalHistory) {
            this.snapshot = snapshot
            replaced = snapshot
        }

        override suspend fun clear() {
            snapshot = null
            cleared = true
        }
    }

    private class PagingGateway(
        private val forexItems: List<TradingSignal>,
    ) : SignalGateway {
        override suspend fun list(
            market: SignalMarketFilter,
            status: SignalStatusFilter,
            limit: Int,
            offset: Int,
        ): SignalPage {
            assertEquals(SignalStatusFilter.CLOSED, status)
            return when (market) {
                SignalMarketFilter.FOREX -> if (offset == 0) {
                    SignalPage(forexItems, forexItems.size, null)
                } else {
                    SignalPage(emptyList(), forexItems.size, null)
                }
                SignalMarketFilter.CRYPTO -> SignalPage(emptyList(), 0, null)
            }
        }

        override suspend fun detail(signalId: Long): TradingSignal = error("unused")
    }
}

private fun cacheSignal(id: Long) = TradingSignal(
    id = id,
    market = MarketType.FOREX,
    symbol = "XAUUSD",
    direction = SignalDirection.BUY,
    status = "closed",
    timeframe = "H1",
    strategy = null,
    confidence = null,
    entry = 2500.0,
    entryZone = null,
    stopLoss = 2490.0,
    targets = emptyList(),
    riskRewardTp1 = null,
    currentQuote = null,
    livePnlPercent = null,
    hitTarget = null,
    createdAt = "2026-08-20T10:00:00Z",
    closedAt = "2026-08-20T11:00:00Z",
)
