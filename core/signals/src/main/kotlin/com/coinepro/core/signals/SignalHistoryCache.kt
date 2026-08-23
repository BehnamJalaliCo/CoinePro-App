package com.coinepro.core.signals

data class CachedSignalHistory(
    val items: List<TradingSignal>,
    val expectedTotal: Int,
    val coverageComplete: Boolean,
    val cachedAtEpochMillis: Long,
)

interface SignalHistoryCache {
    suspend fun read(): CachedSignalHistory?
    suspend fun replace(snapshot: CachedSignalHistory)
    suspend fun clear()
}

object NoOpSignalHistoryCache : SignalHistoryCache {
    override suspend fun read(): CachedSignalHistory? = null
    override suspend fun replace(snapshot: CachedSignalHistory) = Unit
    override suspend fun clear() = Unit
}
