package com.coinepro.core.marketdata

import com.coinepro.core.model.MarketQuote
import retrofit2.Retrofit

data class MarketSnapshot(
    val quotes: List<MarketQuote>,
    val serverTimeEpochMillis: Long?,
)

interface MarketSnapshotGateway {
    /**
     * [symbols] has no default on purpose: a caller that forgets to name a platform's symbols
     * should not silently get a list spanning both of them.
     */
    suspend fun load(symbols: List<String>): MarketSnapshot
}

class NetworkMarketSnapshotGateway private constructor(
    private val api: MarketDataApi,
    private val nowMillis: () -> Long,
) : MarketSnapshotGateway {
    override suspend fun load(symbols: List<String>): MarketSnapshot {
        val response = api.snapshot(symbols.joinToString(","))
        return MarketSnapshot(
            quotes = response.prices.values.mapNotNull { it.toDomain(nowMillis()) },
            serverTimeEpochMillis = response.serverTimeMs,
        )
    }

    companion object {
        fun create(
            retrofit: Retrofit,
            nowMillis: () -> Long = System::currentTimeMillis,
        ): NetworkMarketSnapshotGateway = NetworkMarketSnapshotGateway(
            api = retrofit.create(MarketDataApi::class.java),
            nowMillis = nowMillis,
        )
    }
}
