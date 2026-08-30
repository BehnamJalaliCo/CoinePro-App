package com.coinepro.core.marketintel

import java.net.URI
import java.time.Instant
import com.coinepro.core.common.parseWireInstant
import com.coinepro.core.model.MarketPlatform
import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.http.GET

interface MarketIntelGateway {
    suspend fun snapshot(): MarketIntelSnapshot
}

/**
 * One reader per backend.
 *
 * The two platforms have nothing in common here beyond the shape of the answer: CoinePro-FX reports
 * on gold, silver and the macro calendar that moves them; TradeYar reports on the coins it lists.
 * Asking one for the other's news is not a degraded result but a wrong one — a rate decision has no
 * bearing on a listing, and a token unlock has none on bullion. So the platform picks the path, and
 * nothing merges the two.
 */
class NetworkMarketIntelGateway private constructor(
    private val api: MarketIntelApi,
    private val platform: MarketPlatform,
    /**
     * Where the calendar comes from when the primary route sends none.
     *
     * CoinePro-FX only, and see [AcademyCalendarSource] for why it exists: `market-intelligence`
     * has been answering `calendar: []` since it was built, and the data has been on the same host
     * the whole time under a route the web product already reads.
     */
    private val academyCalendar: AcademyCalendarSource = NoAcademyCalendarSource,
) : MarketIntelGateway {

    override suspend fun snapshot(): MarketIntelSnapshot {
        val primary = when (platform) {
            // The prefix is not decoration: TradeYar serves every mobile route under
            // `api/mobile/v1` and CoinePro-FX under `user`. A path built for one reaches nothing on
            // the other, and arrives as an ordinary HTTP error rather than as anything resembling a
            // wiring mistake.
            MarketPlatform.COINEPRO_FX -> api.forexSnapshot()
            MarketPlatform.TRADEYAR -> api.cryptoSnapshot()
        }.toDomain()
        // Only when the primary sends nothing. A route that starts filling its own calendar wins
        // immediately and without a code change, which is the whole point of asking second rather
        // than instead — the fallback must not outlive the gap it was built for.
        if (primary.calendar.isNotEmpty()) return primary
        val fallback = academyCalendar.events()
        return primary.copy(calendar = fallback.events, calendarSource = fallback)
    }

    companion object {
        fun create(
            retrofit: Retrofit,
            platform: MarketPlatform,
            academyCalendar: AcademyCalendarSource = NoAcademyCalendarSource,
        ): MarketIntelGateway = NetworkMarketIntelGateway(
            api = retrofit.create(MarketIntelApi::class.java),
            platform = platform,
            academyCalendar = academyCalendar,
        )
    }
}

private interface MarketIntelApi {
    // Under `user/mobile` with the rest of the app's surface, not `user/` — the app asked for the
    // shorter one and the server put it where its siblings live, which is the better address.
    @GET("user/mobile/market-intelligence")
    suspend fun forexSnapshot(): MarketIntelSnapshotDto

    @GET("api/mobile/v1/market-intelligence")
    suspend fun cryptoSnapshot(): MarketIntelSnapshotDto
}

internal data class MarketIntelSnapshotDto(
    val serverTime: String?,
    val news: List<MarketNewsDto> = emptyList(),
    val calendar: List<EconomicEventDto> = emptyList(),
)

internal data class MarketNewsDto(
    val id: String?,
    val title: String?,
    val summary: String?,
    val source: String?,
    val url: String?,
    val publishedAt: String?,
    val sentiment: String?,
    val impact: String?,
    val relevance: List<String> = emptyList(),
    val stale: Boolean?,
    /**
     * The address of the picture that belongs above this story.
     *
     * Neither backend sends it today — that ask is `docs/SERVER_ASK_NEWS_MEDIA.md` — and the field
     * is read now rather than on the day it lands, because the alternative is a server that starts
     * sending pictures to an app that silently drops them and two people each certain the other has
     * the bug.
     *
     * **The alternates are not hedging.** These two feeds have already disagreed with each other
     * about spelling once, in this exact subject area: the public headline route serves `titleFa`,
     * `summaryFa` and `sourceUrl` in camel case while the market-intelligence adapter beside it is
     * snake case throughout, so `core:guest` carries a `@SerializedName` for every field it reads.
     * Naming the spellings a wire feed plausibly arrives under costs one annotation; getting it
     * wrong costs a round trip through two backend teams to discover that the picture was there all
     * along under `thumbnail`.
     */
    @SerializedName(
        value = "image_url",
        alternate = ["imageUrl", "image", "thumbnail", "thumbnail_url", "cover_image_url"],
    )
    val imageUrl: String? = null,
    /**
     * The story's own text, where the server has one.
     *
     * Read on the same terms and for the same reason as [imageUrl]. `news_posts` stores `summary_fa`
     * and no body, and the forex side is a cache of wire headlines, so this is null on both feeds
     * today; what [articleBody] does with it is where the honesty lives, not here.
     */
    @SerializedName(
        value = "body",
        alternate = ["content", "body_fa", "content_fa", "full_text", "article_body"],
    )
    val body: String? = null,
)

internal data class EconomicEventDto(
    val id: String?,
    val title: String?,
    val country: String?,
    val currency: String?,
    val scheduledAt: String?,
    val impact: String?,
    val actual: String?,
    val forecast: String?,
    val previous: String?,
    val relevance: List<String> = emptyList(),
    val stale: Boolean?,
)

/**
 * The snapshot, ordered by this app rather than taken on trust.
 *
 * ### Why the news is sorted here, when the contract already says it arrives sorted
 *
 * Because «اخبار اصلاً آپدیت نمی‌شود، همان چیزی است که از ورژن ۱ بوده» is what an unsorted feed
 * looks like from the reader's chair, and it is indistinguishable from a server that has stopped
 * publishing. `docs/NEWS_REQUEST_TRADEYAR.md` asks for «مرتب بر اساس `published_at` نزولی», and an
 * ask is not a guarantee: an adapter that answers `ORDER BY id`, or ascending, or in whatever order
 * the rows came back, puts the *oldest* stories at the top of the list and appends every new one
 * below the fold. The feed then updates on every single fetch and looks frozen, because the only
 * part of it anybody reads never changes.
 *
 * That failure survives every check made from inside the app — the request goes out, the response
 * is fresh, the count is right, the newest `published_at` moves — which is precisely why it was
 * worth three reports without being found. One `sortedByDescending` makes it impossible from this
 * side, and costs nothing on a feed of twenty to fifty rows.
 *
 * The calendar has been sorted here since it was written, and `relatedTo` in `feature:news` sorts
 * the suggestions under a story newest-first. The feed itself was the one list in this app that
 * took somebody else's word for its order.
 */
internal fun MarketIntelSnapshotDto.toDomain(): MarketIntelSnapshot = MarketIntelSnapshot(
    news = news.mapNotNull(MarketNewsDto::toDomain).sortedByDescending(MarketNewsItem::publishedAt),
    calendar = calendar.mapNotNull(EconomicEventDto::toDomain).sortedBy(EconomicEvent::scheduledAt),
    serverTime = parseInstant(serverTime),
)

internal fun MarketNewsDto.toDomain(): MarketNewsItem? {
    val safeId = id?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val safeTitle = title?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val safeSource = source?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val published = parseInstant(publishedAt) ?: return null
    return MarketNewsItem(
        id = safeId,
        title = safeTitle,
        summary = summary?.trim()?.takeIf(String::isNotEmpty),
        source = safeSource,
        url = safeHttpsUrl(url),
        publishedAt = published,
        sentiment = parseSentiment(sentiment),
        impact = parseImpact(impact),
        relevance = parseRelevance(relevance),
        isStale = stale != false,
        imageUrl = safeHttpsUrl(imageUrl),
        body = articleBody(body, summary),
    )
}

internal fun EconomicEventDto.toDomain(): EconomicEvent? {
    val safeId = id?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val safeTitle = title?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val scheduled = parseInstant(scheduledAt) ?: return null
    return EconomicEvent(
        id = safeId,
        title = safeTitle,
        country = country?.trim()?.takeIf(String::isNotEmpty),
        currency = currency?.trim()?.takeIf(String::isNotEmpty),
        scheduledAt = scheduled,
        impact = parseImpact(impact),
        actual = actual?.trim()?.takeIf(String::isNotEmpty),
        forecast = forecast?.trim()?.takeIf(String::isNotEmpty),
        previous = previous?.trim()?.takeIf(String::isNotEmpty),
        relevance = parseRelevance(relevance),
        isStale = stale != false,
    )
}

/**
 * How much a release is expected to move a market.
 *
 * The three English words are the agreed contract and the rest are what a second source turns out
 * to speak. `academy/bn/calendar` was written for the web product years before this contract
 * existed, so it may well grade an event `1`/`2`/`3` or in Persian; reading those costs six lines
 * and the alternative is a calendar where every row says «نامشخص» — which looks like a broken app
 * rather than like a feed with its own vocabulary.
 *
 * Anything genuinely unrecognised is still [MarketImpact.UNKNOWN] and is printed as such. Guessing
 * an impact is not this function's business: the high-impact warning on the chart is built on it.
 */
internal fun parseImpact(value: String?): MarketImpact = when (value?.trim()?.lowercase()) {
    "low", "1", "کم", "پایین" -> MarketImpact.LOW
    "medium", "moderate", "2", "متوسط", "میانه" -> MarketImpact.MEDIUM
    "high", "3", "زیاد", "بالا", "پرتأثیر", "پرتاثیر" -> MarketImpact.HIGH
    else -> MarketImpact.UNKNOWN
}

internal fun parseSentiment(value: String?): NewsSentiment = when (value?.trim()?.lowercase()) {
    "bullish", "positive" -> NewsSentiment.BULLISH
    "bearish", "negative" -> NewsSentiment.BEARISH
    "neutral" -> NewsSentiment.NEUTRAL
    else -> NewsSentiment.UNKNOWN
}

internal fun parseRelevance(values: List<String>): Set<MarketRelevance> = values.mapNotNull { value ->
    when (value.trim().lowercase()) {
        "gold", "xau", "xauusd" -> MarketRelevance.GOLD
        "silver", "xag", "xagusd" -> MarketRelevance.SILVER
        "crypto", "digital_assets" -> MarketRelevance.CRYPTO
        else -> null
    }
}.toSet()

internal fun parseInstant(value: String?): Instant? = parseWireInstant(value)

internal fun safeHttpsUrl(value: String?): String? = runCatching {
    value?.trim()?.takeIf(String::isNotEmpty)?.let { raw ->
        val uri = URI(raw)
        if (uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) raw else null
    }
}.getOrNull()

/**
 * The story's own text, or null where what arrived is not one.
 *
 * The reading page prints this under a heading that promises the full story, so what it accepts is
 * the whole of that promise. Three things are refused, and each is a way a `body` field can be
 * present and still not be a body:
 *
 * * **Blank**, which needs no argument.
 * * **A copy of the summary.** This is the likeliest first version of the route rather than an
 *   exotic case: an adapter mapping `summary_fa` into both fields is one line and looks correct
 *   from the server side. Taken at face value it would print the same paragraph twice on one page,
 *   the second time under a heading claiming it was more.
 * * **Markup.** The page sets plain text, because a Compose `Text` renders `<p>` as the four
 *   characters it is. A story with its own tags printed through it reads as a broken app rather
 *   than as a story, and the honest fallback — the summary, well set, and the source named — is
 *   better than that. `docs/SERVER_ASK_NEWS_MEDIA.md` asks for plain text for exactly this reason.
 *
 * What it does accept, it normalises: CRLF becomes LF so a Windows-authored body does not arrive
 * with a stray carriage return at the end of every paragraph, and a run of blank lines collapses to
 * one, because a blank line is the paragraph break the reader splits on and four of them in a row
 * is a hole in the page rather than four breaks.
 */
internal fun articleBody(raw: String?, summary: String?): String? {
    val trimmed = raw?.replace("\r\n", "\n")?.replace('\r', '\n')?.trim()?.takeIf(String::isNotEmpty)
        ?: return null
    if (trimmed.equals(summary?.trim(), ignoreCase = true)) return null
    if (MARKUP.containsMatchIn(trimmed)) return null
    return trimmed.replace(BLANK_LINES, "\n\n")
}

/** An opening or closing tag with a name — not any `<`, which Persian prose may legitimately hold. */
private val MARKUP = Regex("</?[A-Za-z][A-Za-z0-9]*[^<>]*>")

/** Two or more line breaks with nothing but whitespace between them. */
private val BLANK_LINES = Regex("\n[ \\t]*(\n[ \\t]*)+")
