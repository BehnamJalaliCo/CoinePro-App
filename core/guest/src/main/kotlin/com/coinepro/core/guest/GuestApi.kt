package com.coinepro.core.guest

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * TradeYar's public surface — the part of the product a reader can see before they have an account.
 *
 * These are not new routes. `app/api/routers/public/` has served them all along for the web site,
 * and they take no `Authorization` header at all; the app simply never called them, because until
 * now it had nothing to show anybody who was not signed in.
 *
 * Two things about the wire shape are worth knowing before reading the DTOs.
 *
 * The price feed uses **one-letter keys** — `s`, `p`, `t`, `c` — because it is a browser wire form
 * carrying several hundred symbols, and long names would be most of the payload.
 *
 * And the naming is **mixed**: `age_ms` is snake_case, `titleFa` is camelCase, in responses from
 * the same server. The app's Gson is configured for snake_case, so every camelCase key needs its
 * own `@SerializedName`. Left off, the field parses as null and the row renders blank rather than
 * failing — which is exactly the sort of quiet wrongness that survives a code review.
 */
internal interface GuestApi {
    /**
     * The last-value snapshot for every symbol, or the ones named.
     *
     * Empty [symbols] returns all of them — several hundred rows. The caller asks for what is on
     * screen instead: the server documents a per-client cap, and a list nobody is looking at is
     * bandwidth spent on nothing.
     */
    @GET("api/v1/public/prices")
    suspend fun prices(@Query("symbols") symbols: String): PriceSnapshotDto

    /**
     * Closed signals with their recorded outcome — a track record, not a demonstration.
     *
     * The route's own name says "demo" and the word is misleading: every row is a real published
     * signal that has already closed, with the P&L the ladder actually banked. That is the only
     * thing worth showing somebody who has not signed up, because anything invented would be a
     * claim about performance.
     */
    @GET("api/demo/signals")
    suspend fun trackRecord(@Query("limit") limit: Int = 12): TrackRecordDto

    @GET("api/v1/news/list")
    suspend fun news(
        @Query("type") type: String = "news",
        @Query("limit") limit: Int = 20,
    ): NewsListDto
}

internal data class PriceSnapshotDto(
    val ts: Long? = null,
    val source: String? = null,
    @SerializedName("age_ms") val ageMs: Long? = null,
    /**
     * The server's own judgement that these numbers are too old to present as current.
     *
     * Read rather than recomputed from [ageMs]: the threshold is the server's to set, and a client
     * that decides for itself will disagree with the web site about the same feed.
     */
    val stale: Boolean? = null,
    val data: List<PriceRowDto>? = null,
)

internal data class PriceRowDto(
    @SerializedName("s") val symbol: String? = null,
    @SerializedName("p") val price: Double? = null,
    @SerializedName("t") val timestamp: Long? = null,
    /** Optional keys are omitted when unknown rather than sent as zero, so these stay nullable. */
    @SerializedName("c") val changePercent24h: Double? = null,
    @SerializedName("h") val high24h: Double? = null,
    @SerializedName("l") val low24h: Double? = null,
    @SerializedName("v") val volume24h: Double? = null,
)

internal data class TrackRecordDto(
    val signals: List<TrackRecordSignalDto>? = null,
    /**
     * The server's own statement that it has something to say.
     *
     * Read rather than inferred from an empty list, because the route documents the distinction
     * and it matters: an empty list can mean the query failed, and drawing it as "no trades yet"
     * would be a false claim about a bot that has traded.
     */
    @SerializedName("data_available") val dataAvailable: Boolean? = null,
    @SerializedName("empty_reason") val emptyReason: String? = null,
)

internal data class TrackRecordSignalDto(
    val symbol: String? = null,
    val timeframe: String? = null,
    val direction: String? = null,
    @SerializedName("entry_price") val entryPrice: Double? = null,
    @SerializedName("is_win") val isWin: Boolean? = null,
    @SerializedName("pct_gain") val pctGain: Double? = null,
    @SerializedName("risk_reward") val riskReward: Double? = null,
    @SerializedName("closed_at") val closedAt: String? = null,
)

internal data class NewsListDto(val data: List<NewsItemDto>? = null)

internal data class NewsItemDto(
    val slug: String? = null,
    val source: String? = null,
    @SerializedName("sourceUrl") val sourceUrl: String? = null,
    @SerializedName("titleFa") val titleFa: String? = null,
    @SerializedName("summaryFa") val summaryFa: String? = null,
    @SerializedName("publishedAt") val publishedAt: String? = null,
    val importance: Int? = null,
)
