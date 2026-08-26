package com.coinepro.core.marketdata

import com.coinepro.core.model.MarketPlatform
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

internal interface MarketDataApi {
    /**
     * @param symbols null omits the parameter entirely, which on TradeYar means "everything in
     *   scope" — the only way this app can discover the crypto universe rather than shipping a
     *   hand-written list of it. CoinePro-FX has no such mode and rejects a symbol outside its
     *   configured set, so the forex side always names what it wants. See [MarketCatalogGateway].
     */
    @GET
    suspend fun snapshot(@Url path: String, @Query("symbols") symbols: String?): MarketSnapshotDto
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
    /**
     * Symbols the server was asked for and does not quote.
     *
     * New, and the reason it matters is what it replaced: one unknown name used to fail the entire
     * request, so a single stale entry in a watchlist blanked every price on the screen. The server
     * now answers with what it has and names what it could not. Nothing reads this yet — the app
     * asks for the whole universe rather than a list — but it is parsed rather than dropped, so a
     * caller that does name symbols has the answer waiting.
     */
    val unsupported: List<String> = emptyList(),
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
