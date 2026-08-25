package com.coinepro.core.marketdata

import com.coinepro.core.model.MarketPlatform
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

internal interface MarketDataApi {
    @GET
    suspend fun snapshot(@Url path: String, @Query("symbols") symbols: String): MarketSnapshotDto
}

/**
 * The snapshot lives at a different address on each backend.
 *
 * CoinePro-FX serves it from the root, TradeYar from under its mobile prefix — its nginx has a
 * `/ws` location that the mobile prefix deliberately sits inside. One hard-coded path means the
 * feed on one platform silently never fills.
 */
internal fun MarketPlatform.snapshotPath(): String = when (this) {
    MarketPlatform.COINEPRO_FX -> "ws/snapshot"
    MarketPlatform.TRADEYAR -> "api/mobile/v1/ws/snapshot"
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
