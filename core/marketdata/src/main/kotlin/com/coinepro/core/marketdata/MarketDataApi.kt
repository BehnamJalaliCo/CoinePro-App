package com.coinepro.core.marketdata

import retrofit2.http.GET
import retrofit2.http.Query

internal interface MarketDataApi {
    @GET("ws/snapshot")
    suspend fun snapshot(@Query("symbols") symbols: String): MarketSnapshotDto
}

internal data class MarketSnapshotDto(
    val prices: Map<String, WireQuoteDto> = emptyMap(),
    val serverTimeMs: Long? = null,
)

internal data class WireQuoteDto(
    val symbol: String? = null,
    val price: Double? = null,
    val bid: Double? = null,
    val ask: Double? = null,
    val ts: Long? = null,
    val receivedAtMs: Long? = null,
    val source: String? = null,
    val venue: String? = null,
    val market: String? = null,
)
