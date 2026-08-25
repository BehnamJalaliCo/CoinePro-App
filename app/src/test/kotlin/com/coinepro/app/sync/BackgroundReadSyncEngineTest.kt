package com.coinepro.app.sync

import com.coinepro.core.auth.SessionMemory
import com.coinepro.core.auth.SessionTokenStorage
import com.coinepro.core.marketdata.MarketSnapshot
import com.coinepro.core.marketdata.MarketSnapshotGateway
import com.coinepro.core.marketdata.NoOpMarketDataCache
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.signals.CachedSignalHistory
import com.coinepro.core.signals.SignalGateway
import com.coinepro.core.signals.SignalHistoryCache
import com.coinepro.core.signals.SignalMarketFilter
import com.coinepro.core.signals.SignalMembershipRequiredException
import com.coinepro.core.signals.SignalPage
import com.coinepro.core.signals.SignalStatusFilter
import com.coinepro.core.signals.TradingSignal
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundReadSyncEngineTest {
    @Test
    fun `stored token is hydrated only for sync and then removed from memory`() = runBlocking {
        val memory = SessionMemory()
        val storage = FakeStorage("stored-token")
        var marketSawToken = false
        val marketGateway = object : MarketSnapshotGateway {
            override suspend fun load(symbols: List<String>): MarketSnapshot {
                marketSawToken = memory.token() == "stored-token"
                return MarketSnapshot(emptyList(), null)
            }
        }
        val signalCache = FakeSignalCache()
        val engine = BackgroundReadSyncEngine(
            storage = storage,
            memory = memory,
            marketGateway = marketGateway,
            marketCache = NoOpMarketDataCache,
            signalGateway = EmptySignalGateway(),
            signalCache = signalCache,
            activePlatform = { MarketPlatform.TRADEYAR },
        )

        val outcome = engine.sync()

        assertEquals(BackgroundSyncOutcome.SUCCESS, outcome)
        assertTrue(marketSawToken)
        assertNull(memory.token())
        assertFalse(storage.cleared)
        assertEquals(1, signalCache.replaceCount)
    }

    @Test
    fun `no stored or memory session skips all network reads`() = runBlocking {
        val memory = SessionMemory()
        val storage = FakeStorage(null)
        var marketCalls = 0
        val signalGateway = CountingSignalGateway()
        val engine = BackgroundReadSyncEngine(
            storage = storage,
            memory = memory,
            marketGateway = object : MarketSnapshotGateway {
                override suspend fun load(symbols: List<String>): MarketSnapshot {
                    marketCalls++
                    return MarketSnapshot(emptyList(), null)
                }
            },
            marketCache = NoOpMarketDataCache,
            signalGateway = signalGateway,
            signalCache = FakeSignalCache(),
            activePlatform = { MarketPlatform.TRADEYAR },
        )

        val outcome = engine.sync()

        assertEquals(BackgroundSyncOutcome.NO_SESSION, outcome)
        assertEquals(0, marketCalls)
        assertEquals(0, signalGateway.listCalls)
    }

    @Test
    fun `transient read failure requests retry without inventing success`() = runBlocking {
        val memory = SessionMemory().apply { setToken("memory-token") }
        val signalCache = FakeSignalCache()
        val engine = BackgroundReadSyncEngine(
            storage = FakeStorage(null),
            memory = memory,
            marketGateway = object : MarketSnapshotGateway {
                override suspend fun load(symbols: List<String>): MarketSnapshot =
                    throw IOException("network unavailable")
            },
            marketCache = NoOpMarketDataCache,
            signalGateway = EmptySignalGateway(),
            signalCache = signalCache,
            activePlatform = { MarketPlatform.TRADEYAR },
        )

        val outcome = engine.sync()

        assertEquals(BackgroundSyncOutcome.RETRYABLE_FAILURE, outcome)
        assertEquals("memory-token", memory.token())
        assertEquals(1, signalCache.replaceCount)
    }

    @Test
    fun `membership loss clears only signal history cache and does not become retry loop`() = runBlocking {
        val memory = SessionMemory().apply { setToken("memory-token") }
        val signalCache = FakeSignalCache()
        val engine = BackgroundReadSyncEngine(
            storage = FakeStorage(null),
            memory = memory,
            marketGateway = object : MarketSnapshotGateway {
                override suspend fun load(symbols: List<String>) = MarketSnapshot(emptyList(), null)
            },
            marketCache = NoOpMarketDataCache,
            signalGateway = object : SignalGateway {
                override suspend fun list(
                    market: SignalMarketFilter,
                    status: SignalStatusFilter,
                    limit: Int,
                    offset: Int,
                ): SignalPage = throw SignalMembershipRequiredException()

                override suspend fun detail(signalId: Long): TradingSignal = error("unused")
            },
            signalCache = signalCache,
            activePlatform = { MarketPlatform.TRADEYAR },
        )

        val outcome = engine.sync()

        assertEquals(BackgroundSyncOutcome.SUCCESS, outcome)
        assertTrue(signalCache.cleared)
        assertEquals(0, signalCache.replaceCount)
    }

    private class FakeStorage(
        private var token: String?,
    ) : SessionTokenStorage {
        var cleared = false

        override suspend fun readToken(): String? = token

        override suspend fun writeToken(token: String) {
            this.token = token
        }

        override suspend fun clear() {
            token = null
            cleared = true
        }
    }

    private class FakeSignalCache : SignalHistoryCache {
        var replaceCount = 0
        var cleared = false

        override suspend fun read(): CachedSignalHistory? = null

        override suspend fun replace(snapshot: CachedSignalHistory) {
            replaceCount++
        }

        override suspend fun clear() {
            cleared = true
        }
    }

    private class EmptySignalGateway : SignalGateway {
        override suspend fun list(
            market: SignalMarketFilter,
            status: SignalStatusFilter,
            limit: Int,
            offset: Int,
        ): SignalPage = SignalPage(emptyList(), 0, null)

        override suspend fun detail(signalId: Long): TradingSignal = error("unused")
    }

    private class CountingSignalGateway : SignalGateway {
        var listCalls = 0

        override suspend fun list(
            market: SignalMarketFilter,
            status: SignalStatusFilter,
            limit: Int,
            offset: Int,
        ): SignalPage {
            listCalls++
            return SignalPage(emptyList(), 0, null)
        }

        override suspend fun detail(signalId: Long): TradingSignal = error("unused")
    }
}
