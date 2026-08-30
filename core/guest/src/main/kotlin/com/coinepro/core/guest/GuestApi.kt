package com.coinepro.core.guest

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Path
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

    /**
     * The public channels and how many people are in them.
     *
     * The route's own docstring is unusually firm about one thing, and it is the whole reason the
     * DTO below is shaped the way it is: a channel whose count could not be fetched must render as
     * unavailable. Not as a zero, and not as the number that was fetched an hour ago. Every count
     * therefore arrives beside its own `available` flag, and the app reads the flag rather than
     * testing the number.
     */
    @GET("api/v1/public/community")
    suspend fun community(): CommunityDto

    /**
     * How to become a member, from the server rather than from the build.
     *
     * The referral links in particular must never be compiled in. An account opened without the
     * right link is not recorded against CoinePro in the exchange's own system and cannot be
     * verified afterwards — so a link that is one release out of date does not degrade, it silently
     * costs the reader their membership and takes a support conversation to discover.
     *
     * This one route is **camelCase** while the rest of the mobile surface is snake_case, because
     * that is the shape it was asked for and the shape it now serves. `@SerializedName` carries
     * both spellings so neither side has to remember which is which.
     */
    @GET("api/v1/public/membership")
    suspend fun membership(): MembershipDto

    /**
     * Candles a reader can see before they have an account.
     *
     * Same code path as the signed-in route rather than a copy — the server's own note says so, and
     * it is the reason the numbers agree. Two implementations of one market eventually disagree,
     * and a reader who spots it has learned that the app lied to them before they signed up.
     */
    @GET("api/v1/public/candles/{symbol}")
    suspend fun candles(
        @Path("symbol") symbol: String,
        @Query("tf") timeframe: String,
        @Query("limit") limit: Int,
    ): PublicCandlesDto
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
    /**
     * The publisher's illustration. Camel case on the wire like its neighbours, so it needs the
     * annotation; the snake-case alternate is there because the members' route beside it spells
     * every one of these the other way round and either could be the one that changes.
     */
    @SerializedName(value = "sourceImageUrl", alternate = ["source_image_url", "image_url"])
    val sourceImageUrl: String? = null,
    @SerializedName("titleFa") val titleFa: String? = null,
    @SerializedName("summaryFa") val summaryFa: String? = null,
    @SerializedName("publishedAt") val publishedAt: String? = null,
    val importance: Int? = null,
)

internal data class CommunityDto(
    val channels: List<CommunityChannelDto>? = null,
    @SerializedName("telegram_members_total") val telegramMembersTotal: Long? = null,
    @SerializedName("telegram_members_total_available") val telegramMembersTotalAvailable: Boolean? = null,
    @SerializedName("bot_users") val botUsers: CommunityCountDto? = null,
    val note: String? = null,
)

internal data class CommunityChannelDto(
    val key: String? = null,
    val username: String? = null,
    val url: String? = null,
    val label: String? = null,
    /** The server's verdict on its own number. Read instead of testing [members] against zero. */
    val available: Boolean? = null,
    val members: Long? = null,
    val source: String? = null,
)

internal data class CommunityCountDto(
    val available: Boolean? = null,
    val value: Long? = null,
    val label: String? = null,
    val source: String? = null,
)

internal data class MembershipDto(
    @SerializedName(value = "lbank_referral_url", alternate = ["lbankReferralUrl"])
    val lbankReferralUrl: String? = null,
    @SerializedName(value = "ourbit_referral_url", alternate = ["ourbitReferralUrl"])
    val ourbitReferralUrl: String? = null,
    @SerializedName(value = "min_deposit_usdt", alternate = ["minDepositUsdt"])
    val minDepositUsdt: Double? = null,
    /** Which exchanges this platform can actually place orders on. */
    @SerializedName(value = "copy_trade_exchanges", alternate = ["copyTradeExchanges"])
    val copyTradeExchanges: List<String>? = null,
    /**
     * Which exchanges accept a UID for membership — a superset of [copyTradeExchanges].
     *
     * The distinction is real and the app must not flatten it: an Ourbit UID earns membership but
     * is never traded on, so a screen that offered copy trading to an Ourbit member would be
     * promising something the platform cannot do.
     */
    @SerializedName(value = "uid_exchanges", alternate = ["uidExchanges"])
    val uidExchanges: List<String>? = null,
    /** The sentence to print above the links, in the reader's language, from the server. */
    @SerializedName(value = "notice_fa", alternate = ["noticeFa"])
    val noticeFa: String? = null,
)

internal data class PublicCandlesDto(
    val symbol: String? = null,
    /** Normalised by the server: `1h` goes out, `H1` comes back. Never key a cache on the request. */
    val tf: String? = null,
    val candles: List<PublicCandleDto>? = null,
    @SerializedName("has_more") val hasMore: Boolean? = null,
    val source: String? = null,
)

internal data class PublicCandleDto(
    /** Unix **seconds**, and the bar's *open* time. */
    val t: Long? = null,
    val o: Double? = null,
    val h: Double? = null,
    val l: Double? = null,
    val c: Double? = null,
    val v: Double? = null,
    /** False on the bar still forming. Drawn, but never counted as a closed bar by an indicator. */
    val closed: Boolean? = null,
)
