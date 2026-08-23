package com.coinepro.core.marketdata

import com.coinepro.core.model.MarketQuote

data class CachedMarketSnapshot(
    val quotes: List<MarketQuote>,
    val cachedAtEpochMillis: Long,
)

interface MarketDataCache {
    suspend fun read(): CachedMarketSnapshot?
    suspend fun replace(quotes: List<MarketQuote>, cachedAtEpochMillis: Long)
    suspend fun clear()
}

object NoOpMarketDataCache : MarketDataCache {
    override suspend fun read(): CachedMarketSnapshot? = null
    override suspend fun replace(quotes: List<MarketQuote>, cachedAtEpochMillis: Long) = Unit
    override suspend fun clear() = Unit
}
