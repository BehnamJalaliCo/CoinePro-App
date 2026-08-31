package com.coinepro.core.marketintel

import java.net.URI
import java.time.Instant
import com.coinepro.core.common.parseWireInstant
import com.coinepro.core.model.MarketPlatform
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import retrofit2.HttpException
import retrofit2.Response
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
 *
 * ### Why this reader stopped going through Gson's field-naming policy
 *
 * Because two failure modes were measured in it, and both are invisible from the reader's chair.
 *
 * **A missing array threw.** The DTOs declared `relevance: List<String> = emptyList()` and Kotlin
 * defaults do not survive Gson: a data class whose primary constructor has any parameter without a
 * default gets no no-arg constructor, so Gson allocates it through `Unsafe` and never runs the
 * initialiser. A single news row without a `relevance` key therefore left a **null** in a field
 * declared non-null, and the first thing to touch it — `parseRelevance` — threw
 * `NullPointerException: Parameter specified as non-null is null`. That exception is caught by
 * `MarketIntelController`, which keeps whatever list was already on screen and sets `error`. One
 * optional field the server never promised, and the whole snapshot — news *and* calendar — is lost.
 *
 * **A camel-case timestamp dropped every row, silently.** `published_at` and `scheduled_at` were
 * resolved only by `FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES`. TradeYar's *own* public news
 * route, on the same host, serves `publishedAt`, `titleFa` and `sourceUrl` — camel case — so this
 * is not a hypothetical: an adapter written next to that serializer spells it the other way, every
 * row parses with a null date, `toDomain` drops all of them, and the screen is empty behind a
 * perfectly successful HTTP 200 with no error anywhere to explain it.
 *
 * Reading the body as JSON with an explicit list of spellings per field removes both by
 * construction. It is the shape `AcademyCalendarSource` in this same module already uses, for the
 * same reason, and it is what makes [NewsFeedOutcome] possible at all — a probe cannot report the
 * server's own row order or the first row's field names from a body that has already been mapped.
 */
class NetworkMarketIntelGateway private constructor(
    private val api: MarketIntelApi,
    private val platform: MarketPlatform,
    /**
     * Where the calendar comes from when the primary route sends none.
     *
     * CoinePro-FX only, and see [AcademyCalendarSource] for why it exists: `market-intelligence`
     * has been answering `calendar: []` since it was built.
     */
    private val academyCalendar: AcademyCalendarSource = NoAcademyCalendarSource,
    /**
     * News and the calendar from public sources, for whichever section the backend left empty.
     *
     * Null in the tests and in any build that would rather show nothing than show a third party's
     * feed. See [PublicMarketIntel] for the measurement that made it necessary and for why it is
     * consulted per section rather than instead.
     */
    private val publicSources: PublicMarketIntel? = null,
) : MarketIntelGateway {

    override suspend fun snapshot(): MarketIntelSnapshot {
        val route = when (platform) {
            // The prefix is not decoration: TradeYar serves every mobile route under
            // `api/mobile/v1` and CoinePro-FX under `user`. A path built for one reaches nothing on
            // the other, and arrives as an ordinary HTTP error rather than as anything resembling a
            // wiring mistake.
            MarketPlatform.COINEPRO_FX -> FOREX_ROUTE
            MarketPlatform.TRADEYAR -> CRYPTO_ROUTE
        }
        val response = when (platform) {
            MarketPlatform.COINEPRO_FX -> api.forexSnapshot()
            MarketPlatform.TRADEYAR -> api.cryptoSnapshot()
        }
        // A refusal is not the end of the screen any more, and this is the change that matters most
        // to a reader who is not signed in.
        //
        // Both `market-intelligence` routes are behind auth and answer 401 to a guest. This used to
        // throw here, which meant the public sources below were never reached — so the one reader
        // who has not yet decided whether to trust the product was shown an empty news screen and an
        // empty calendar, worded as an outage, for content that is public everywhere else. The same
        // held for a member whose token had merely expired.
        //
        // So a failure becomes an *empty* snapshot that remembers what happened, the fallbacks get
        // their turn, and the error is only rethrown if they had nothing either. The reader still
        // ends up with the server's own words when there is genuinely nothing to show — see
        // [orRethrow] — and gets a full screen the rest of the time.
        val failure = if (response.isSuccessful) null else HttpException(response)
        val primary = if (failure != null) {
            MarketIntelSnapshot(
                news = emptyList(),
                calendar = emptyList(),
                serverTime = null,
                newsSource = NewsFeedOutcome(
                    route = route,
                    status = response.code(),
                    failure = failure.message(),
                ),
            )
        } else {
            readSnapshot(response.body(), route, response.code(), platform)
        }
        return primary
            .withCalendar()
            .withNews()
            .orRethrow(failure)
    }

    private fun MarketIntelSnapshot.orRethrow(failure: HttpException?): MarketIntelSnapshot =
        if (failure != null && shouldRethrow()) throw failure else this

    /**
     * The calendar, from the first source that has one.
     *
     * Three in order, and the order is the order of authority: this backend's own route, then
     * CoinePro-FX's public academy route, then the published week. Each is asked only because the
     * one before it sent nothing — a route that starts filling its own calendar wins immediately and
     * without a code change, which is the whole point of asking second rather than instead. A
     * fallback must not outlive the gap it was built for.
     */
    private suspend fun MarketIntelSnapshot.withCalendar(): MarketIntelSnapshot {
        if (calendar.isNotEmpty()) return this
        val academy = academyCalendar.events()
        if (academy.events.isNotEmpty()) return copy(calendar = academy.events, calendarSource = academy)
        return withPublicCalendar(publicSources?.calendar().orEmpty(), academy)
    }

    /**
     * The news, from the wires, when the backend sent no stories.
     *
     * Same rule and same reason as the calendar above. [NewsFeedOutcome.route] records the public
     * source so a reader of the diagnostics screen — or of a bug report — can tell at a glance
     * whether what they are looking at came from this product's own servers or from a wire.
     */
    private suspend fun MarketIntelSnapshot.withNews(): MarketIntelSnapshot {
        if (news.isNotEmpty()) return this
        return withPublicNews(publicSources?.news().orEmpty())
    }

    companion object {
        internal const val FOREX_ROUTE = "user/mobile/market-intelligence"
        internal const val CRYPTO_ROUTE = "api/mobile/v1/market-intelligence"

        /** Not a URL, because there are two of them per platform. See [PublicNewsFeed.feeds]. */
        internal const val PUBLIC_NEWS_ROUTE = "public-rss"

        fun create(
            retrofit: Retrofit,
            platform: MarketPlatform,
            academyCalendar: AcademyCalendarSource = NoAcademyCalendarSource,
            publicSources: PublicMarketIntel? = null,
        ): MarketIntelGateway = NetworkMarketIntelGateway(
            api = retrofit.create(MarketIntelApi::class.java),
            platform = platform,
            academyCalendar = academyCalendar,
            publicSources = publicSources,
        )
    }
}

/**
 * The two snapshot routes, read as JSON.
 *
 * [Response] rather than a bare body so the probe can carry the status the app actually got, and
 * [JsonElement] rather than a typed body for the reason on [NetworkMarketIntelGateway]. Gson
 * deserialises `JsonElement` without consulting the field-naming policy at all, so nothing about
 * this depends on how the converter happens to be configured.
 */
internal interface MarketIntelApi {
    // Under `user/mobile` with the rest of the app's surface, not `user/` — the app asked for the
    // shorter one and the server put it where its siblings live, which is the better address.
    @GET("user/mobile/market-intelligence")
    suspend fun forexSnapshot(): Response<JsonElement>

    @GET("api/mobile/v1/market-intelligence")
    suspend fun cryptoSnapshot(): Response<JsonElement>
}

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
 * below the fold.
 *
 * The sort was necessary and it was not sufficient, which is why [NewsFeedOutcome] now records the
 * first and last publication string **in the order the server sent them**, beside the count that
 * arrived and the count that survived. Sorting hides a server's order; the probe is what preserves
 * the evidence of it.
 */
internal fun readSnapshot(
    body: JsonElement?,
    route: String,
    status: Int,
    platform: MarketPlatform? = null,
): MarketIntelSnapshot {
    val root = body?.takeIf(JsonElement::isJsonObject)?.asJsonObject
        ?: return MarketIntelSnapshot(
            news = emptyList(),
            calendar = emptyList(),
            serverTime = null,
            newsSource = NewsFeedOutcome(route = route, status = status, failure = "body is not an object"),
            platform = platform,
        )

    val newsRows = root.rows(NEWS_KEYS)
    val news = newsRows.mapNotNull(::readNews)
    val calendar = root.rows(CALENDAR_KEYS).mapNotNull(::readEvent)

    return MarketIntelSnapshot(
        news = news.sortedByDescending(MarketNewsItem::publishedAt),
        calendar = calendar.sortedBy(EconomicEvent::scheduledAt),
        serverTime = root.moment(SERVER_TIME_KEYS),
        newsSource = NewsFeedOutcome(
            route = route,
            status = status,
            received = newsRows.size,
            kept = news.size,
            // Untouched strings, from the first and last object of the array as it arrived. This is
            // the one fact the sorted list can no longer show, and it is the whole question: a
            // server answering oldest-first has a `first` older than its `last`.
            firstPublished = newsRows.firstOrNull()?.text(PUBLISHED_KEYS),
            lastPublished = newsRows.lastOrNull()?.text(PUBLISHED_KEYS),
            sampleKeys = newsRows.firstOrNull()?.keySet()?.joinToString(","),
            envelope = root.keySet().joinToString(","),
        ),
        platform = platform,
    )
}

internal fun readNews(row: JsonObject): MarketNewsItem? {
    val id = row.text(NEWS_ID_KEYS) ?: return null
    val title = row.text(TITLE_KEYS) ?: return null
    val source = row.text(SOURCE_KEYS) ?: return null
    val published = row.moment(PUBLISHED_KEYS) ?: return null
    val summary = row.text(SUMMARY_KEYS)
    return MarketNewsItem(
        id = id,
        title = title,
        summary = summary,
        source = source,
        url = safeHttpsUrl(row.text(URL_KEYS)),
        publishedAt = published,
        sentiment = parseSentiment(row.text(SENTIMENT_KEYS)),
        impact = parseImpact(row.text(IMPACT_KEYS)),
        relevance = parseRelevance(row.strings(RELEVANCE_KEYS)),
        // Absent reads as stale. A feed that did not say is one the app must not vouch for.
        isStale = row.flag(STALE_KEYS) != false,
        imageUrl = safeHttpsUrl(row.text(IMAGE_KEYS)),
        body = articleBody(row.text(BODY_KEYS), summary),
    )
}

internal fun readEvent(row: JsonObject): EconomicEvent? {
    val id = row.text(EVENT_ID_KEYS) ?: return null
    val title = row.text(TITLE_KEYS) ?: return null
    val scheduled = row.moment(SCHEDULED_KEYS) ?: return null
    return EconomicEvent(
        id = id,
        // Persian, wherever the server did not already write it in Persian — see [CalendarPersian],
        // which returns a Persian title untouched. Both routes are documented to translate and
        // neither does, so without this the one Persian screen in the app that is entirely about
        // macro data printed «US Core CPI (MoM)» on every row.
        title = CalendarPersian.title(title),
        country = CalendarPersian.country(row.text(COUNTRY_KEYS)),
        currency = row.text(CURRENCY_KEYS),
        scheduledAt = scheduled,
        impact = parseImpact(row.text(IMPACT_KEYS)),
        actual = row.text(ACTUAL_KEYS),
        forecast = row.text(FORECAST_KEYS),
        previous = row.text(PREVIOUS_KEYS),
        relevance = parseRelevance(row.strings(RELEVANCE_KEYS)),
        isStale = row.flag(STALE_KEYS) != false,
    )
}

/**
 * Every spelling each field plausibly arrives under, in order of preference.
 *
 * **These are not hedging.** The two feeds have already disagreed with each other about spelling in
 * this exact subject area: TradeYar's public headline route serves `titleFa`, `summaryFa`,
 * `sourceUrl` and `publishedAt` in camel case while the market-intelligence adapter beside it was
 * specified snake case throughout. Naming the spellings a wire feed plausibly arrives under costs a
 * line each; getting it wrong costs a screen that is empty behind an HTTP 200 and two rounds of
 * work to discover the rows were there all along under a different key.
 *
 * The one thing deliberately **not** aliased is `importance` onto `impact`. TradeYar's public feed
 * grades a story `importance: 7`, which is a ten-point scale, and [parseImpact] reads `3` as
 * `HIGH` — so reading one as the other would print a mild story as market-moving. An unmapped
 * grade stays [MarketImpact.UNKNOWN], which is what the contract asks the server to map.
 */
private val NEWS_KEYS = listOf("news", "items", "articles", "data", "results", "rows")
private val CALENDAR_KEYS = listOf("calendar", "events", "economic_calendar", "economicCalendar")
private val SERVER_TIME_KEYS = listOf("server_time", "serverTime", "generated_at", "generatedAt", "now")
private val NEWS_ID_KEYS = listOf("id", "slug", "news_id", "newsId", "uuid", "guid")
private val EVENT_ID_KEYS = listOf("id", "event_id", "eventId", "slug", "uuid")
private val TITLE_KEYS = listOf("title", "title_fa", "titleFa", "headline", "event", "name")
private val SUMMARY_KEYS = listOf("summary", "summary_fa", "summaryFa", "description", "excerpt")
private val SOURCE_KEYS = listOf("source", "source_name", "sourceName", "provider", "publisher")
private val URL_KEYS = listOf("url", "source_url", "sourceUrl", "link", "permalink")
private val PUBLISHED_KEYS =
    listOf("published_at", "publishedAt", "published", "publish_date", "date", "created_at", "createdAt")
private val SCHEDULED_KEYS =
    listOf("scheduled_at", "scheduledAt", "date", "datetime", "event_time", "eventTime", "release_time", "time", "timestamp")
private val SENTIMENT_KEYS = listOf("sentiment", "tone")
private val IMPACT_KEYS = listOf("impact", "impact_level", "impactLevel", "severity")
private val RELEVANCE_KEYS = listOf("relevance", "markets", "symbols", "tags")
private val STALE_KEYS = listOf("stale", "is_stale", "isStale")
private val IMAGE_KEYS = listOf(
    "image_url", "imageUrl", "image", "thumbnail", "thumbnail_url", "thumbnailUrl",
    "cover_image_url", "source_image_url", "sourceImageUrl",
)
private val BODY_KEYS = listOf("body", "content", "body_fa", "content_fa", "full_text", "fullText", "article_body")
private val COUNTRY_KEYS = listOf("country", "country_code", "countryCode", "region")
private val CURRENCY_KEYS = listOf("currency", "ccy", "currency_code", "currencyCode")
private val ACTUAL_KEYS = listOf("actual", "actual_value", "actualValue")
private val FORECAST_KEYS = listOf("forecast", "consensus", "estimate", "forecast_value")
private val PREVIOUS_KEYS = listOf("previous", "prior", "previous_value")

/** The first of [names] that holds an array, as its objects. Empty where the key is absent. */
private fun JsonObject.rows(names: List<String>): List<JsonObject> = names.asSequence()
    .mapNotNull { get(it) }
    .filter(JsonElement::isJsonArray)
    .map { array -> array.asJsonArray.filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject) }
    .firstOrNull { it.isNotEmpty() }
    .orEmpty()

/** The first of [names] present as a non-blank string or number. */
private fun JsonObject.text(names: List<String>): String? = names.asSequence()
    .mapNotNull { get(it) }
    .filter(JsonElement::isJsonPrimitive)
    .map { it.asString.trim() }
    .firstOrNull(String::isNotEmpty)

/** The first of [names] present as an array of primitives, or empty. */
private fun JsonObject.strings(names: List<String>): List<String> = names.asSequence()
    .mapNotNull { get(it) }
    .filter(JsonElement::isJsonArray)
    .map { array -> array.asJsonArray.filter(JsonElement::isJsonPrimitive).map { it.asString } }
    .firstOrNull { it.isNotEmpty() }
    .orEmpty()

/** The first of [names] present as a boolean. Null where none is, which is not the same as false. */
private fun JsonObject.flag(names: List<String>): Boolean? = names.asSequence()
    .mapNotNull { get(it) }
    .filter(JsonElement::isJsonPrimitive)
    .mapNotNull { runCatching { it.asBoolean }.getOrNull() }
    .firstOrNull()

/**
 * A moment, from a wire string **or** an epoch.
 *
 * The contract asks for ISO and says so in both request documents, and an ISO string is what is
 * read first. An epoch is accepted after it because the alternative is dropping every row of a feed
 * that sends one — which is the exact shape of «اخبار خالی است» with a green request in the log —
 * and because the ambiguity a bare integer carries is resolvable: anything past the year 3000 read
 * as seconds must be milliseconds. Ten digits is seconds until 2286, so the test does not become
 * wrong within the life of this app.
 */
private fun JsonObject.moment(names: List<String>): Instant? {
    for (name in names) {
        val value = get(name)?.takeIf(JsonElement::isJsonPrimitive) ?: continue
        val raw = value.asString.trim()
        if (raw.isEmpty()) continue
        parseWireInstant(raw)?.let { return it }
        val number = raw.toLongOrNull() ?: continue
        if (number <= 0) continue
        return if (number > EPOCH_SECONDS_CEILING) Instant.ofEpochMilli(number) else Instant.ofEpochSecond(number)
    }
    return null
}

/** The first of January 3000, in seconds. Above it, a number has to be milliseconds. */
private const val EPOCH_SECONDS_CEILING = 32_503_680_000L

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

// ── what to do with a section the backend left empty ──────────────────────────────────────────
//
// Three decisions, lifted out of the suspend orchestration above so they can be asserted. Each is
// one `if`, and each is one of the five rounds this screen has been reported broken in: the app was
// right about the wire and wrong about what to do when the wire had nothing on it.

/**
 * The calendar the reader ends up with.
 *
 * [academy] is kept as the recorded outcome even when it was empty and [published] filled the
 * screen, because it carries which route answered and what it said — the evidence the diagnostics
 * screen exists to show. An empty calendar with no explanation is exactly what this module was
 * written to stop producing.
 */
internal fun MarketIntelSnapshot.withPublicCalendar(
    published: List<EconomicEvent>,
    academy: CalendarSourceOutcome = CalendarSourceOutcome.None,
): MarketIntelSnapshot = when {
    calendar.isNotEmpty() -> this
    published.isEmpty() -> copy(calendarSource = academy)
    else -> copy(
        calendar = published,
        calendarSource = CalendarSourceOutcome(
            events = published,
            received = published.size,
            route = PublicCalendarFeed.URL,
        ),
    )
}

/**
 * The stories the reader ends up with.
 *
 * `kept == received` deliberately, and it is not a formality: `feedUnreadable` in `feature:news`
 * shows «هیچ خبری خوانده نشد» exactly when `received > 0 && kept == 0`, and `feedShortfall` warns
 * when they differ. Nothing was dropped on the way in here, so neither line should appear.
 */
internal fun MarketIntelSnapshot.withPublicNews(wires: List<MarketNewsItem>): MarketIntelSnapshot =
    if (news.isNotEmpty() || wires.isEmpty()) {
        this
    } else {
        copy(
            news = wires,
            newsSource = NewsFeedOutcome(
                route = NetworkMarketIntelGateway.PUBLIC_NEWS_ROUTE,
                received = wires.size,
                kept = wires.size,
                firstPublished = wires.firstOrNull()?.publishedAt?.toString(),
                lastPublished = wires.lastOrNull()?.publishedAt?.toString(),
                envelope = "rss",
            ),
        )
    }

/**
 * Whether the server's refusal is still the reader's business once the fallbacks have run.
 *
 * The distinction: a 401 with a wire full of stories behind it is not something to put in front of a
 * reader — they have a screen of news, and the fact that they are signed out is for the sign-in
 * button to say. A 401 with nothing behind it is exactly what they need told, in the server's own
 * wording, and the request table needs the code.
 */
internal fun MarketIntelSnapshot.shouldRethrow(): Boolean = news.isEmpty() && calendar.isEmpty()
