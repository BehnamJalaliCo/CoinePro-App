package com.coinepro.core.marketintel

import com.coinepro.core.model.MarketPlatform
import java.time.Duration
import java.time.Instant

enum class MarketImpact { LOW, MEDIUM, HIGH, UNKNOWN }
enum class NewsSentiment { BULLISH, BEARISH, NEUTRAL, UNKNOWN }
enum class MarketRelevance { GOLD, SILVER, CRYPTO }

data class MarketNewsItem(
    val id: String,
    val title: String,
    val summary: String?,
    val source: String,
    val url: String?,
    val publishedAt: Instant,
    val sentiment: NewsSentiment,
    val impact: MarketImpact,
    val relevance: Set<MarketRelevance>,
    val isStale: Boolean,
    /**
     * The picture that belongs above this story, or null where the feed sent none.
     *
     * Defaulted so the two other places in this repository that build one of these by hand — the
     * chart's event feed and the app's screenshot fixtures — did not have to learn about pictures
     * to keep compiling. That is the only reason for the default; a story from the wire either has
     * an address here or genuinely has no picture, and every surface that draws one is written to
     * be right in the second case.
     *
     * On TradeYar a null here is filled from the platform's own public route before the snapshot
     * leaves the gateway — see [PublicMarketIntel.illustrate], and see [TradeYarPublicNews.media]
     * for why the members' route was the only one not sending it.
     */
    val imageUrl: String? = null,
    /**
     * The story's own text, or null where the feed sent only a summary.
     *
     * **Null in the feed by design, and filled when a reader opens the story.** The claim that used
     * to stand here — that neither server had a body — was wrong: TradeYar's `news_posts` has a
     * `body_fa` column carrying a full Persian translation, and CoinePro-FX's `articles.content` is
     * its own newsroom's article. Both are fetched by [NewsBodySource] at the moment an article is
     * opened, which is where a page of prose belongs; a list route that shipped thirty of them
     * would be sending tens of kilobytes to draw thirty cards, and TradeYar's own public list
     * selects `NULL AS body_fa` for exactly that reason.
     *
     * So a story that arrives here with a body has one because its feed volunteered it, and one
     * without is ordinary rather than deficient. `articleBody` is what decides whether what arrives
     * under the name is really one.
     */
    val body: String? = null,
)

data class EconomicEvent(
    val id: String,
    val title: String,
    val country: String?,
    val currency: String?,
    val scheduledAt: Instant,
    val impact: MarketImpact,
    val actual: String?,
    val forecast: String?,
    val previous: String?,
    val relevance: Set<MarketRelevance>,
    val isStale: Boolean,
)

data class MarketIntelSnapshot(
    val news: List<MarketNewsItem>,
    val calendar: List<EconomicEvent>,
    val serverTime: Instant?,
    /**
     * What the second calendar source did, where one was asked. Null where it was not.
     *
     * Carried rather than logged here because this module has no log — and carried at all because
     * "the calendar is empty" has two causes with opposite fixes: nothing was published, or rows
     * arrived in a shape this app could not read. See [CalendarSourceOutcome].
     */
    val calendarSource: CalendarSourceOutcome? = null,
    /**
     * What the news half of this fetch actually contained, on the wire. See [NewsFeedOutcome].
     *
     * Carried for the same reason and by the same route as [calendarSource]: this module talks to
     * a server and holds state, and the shell is where it meets the log.
     */
    val newsSource: NewsFeedOutcome? = null,
    /**
     * Which backend answered.
     *
     * Carried because one screen has to know, and asking the shell to pass it down would be a
     * second source of truth for a fact the gateway already holds: **TradeYar publishes no economic
     * calendar at all.** Their OpenAPI has no calendar route among its three hundred and forty-one
     * paths, and `docs/NEWS_REQUEST_TRADEYAR.md` asks them in as many words to answer
     * `calendar: []` — «کلید باید باشد، محتوایش نه» — because macro releases belong to the forex
     * side and have no bearing on a token listing.
     *
     * So a crypto reader opening the calendar is looking at a screen that can never fill, and until
     * now it told them «سرور رویدادی نفرستاد», which reads as an outage. See `CalendarMode`.
     */
    val platform: MarketPlatform? = null,
)

/**
 * What one read of a `market-intelligence` route actually contained.
 *
 * ### Why this exists after two rounds of work on the same complaint
 *
 * «اخبار آپدیت نمی‌شود» has been reported four times and answered twice, both times from inside the
 * app, because the authenticated route cannot be called from anywhere else. Each answer was
 * plausible and neither was checked against a body, and the reason is that by the time anything in
 * this app can see the feed it has already been mapped and sorted — which destroys precisely the
 * three facts that tell the four candidate explanations apart:
 *
 * * **[received] against [kept].** A feed whose date format this app cannot read, or whose rows
 *   arrive under keys it does not know, produces `received = 30, kept = 0` — an empty screen behind
 *   a green request in the log, with nothing anywhere to say a single row was ever there. A feed
 *   that is genuinely not publishing produces `received = 0`. Those are opposite problems and the
 *   screen says the same sentence for both.
 * * **[firstPublished] and [lastPublished], untouched, in the order the server sent them.** The
 *   sort added in 4.11.0 makes an out-of-order feed harmless *and invisible*. If the server is
 *   answering oldest-first, `first` is older than `last` and that is the whole diagnosis; if it is
 *   answering newest-first and `first` has not moved between two exports, the server is not
 *   publishing and no client change will ever fix it.
 * * **[sampleKeys] and [envelope].** The first row's own field names, and the top-level ones. A
 *   spelling this app does not read is the difference between an empty screen and a full one, and
 *   it is a fact nobody here can obtain any other way — the route needs a token this side does not
 *   hold.
 *
 * Nothing here is drawn. It is written to the log on every successful fetch, so the next export the
 * owner takes settles the question rather than starting a third theory.
 */
data class NewsFeedOutcome(
    /** The path that was asked, so an export says which of the two backends answered. */
    val route: String,
    /** The HTTP status. Present on every read that reached the server. */
    val status: Int? = null,
    /** How many objects the news array contained, before any were dropped. */
    val received: Int = 0,
    /** How many became stories. Anything less than [received] is a mapping failure, not an outage. */
    val kept: Int = 0,
    /** The **first** row's publication string, exactly as sent and before any sorting. */
    val firstPublished: String? = null,
    /** The **last** row's publication string, exactly as sent and before any sorting. */
    val lastPublished: String? = null,
    /** The first row's field names, comma separated, or null where no row arrived. */
    val sampleKeys: String? = null,
    /** The response object's own top-level keys. Null envelope names drop the array silently. */
    val envelope: String? = null,
    /** Why nothing came back, where that was a failure rather than an empty publication. */
    val failure: String? = null,
) {
    val dropped: Int get() = (received - kept).coerceAtLeast(0)

    /**
     * The line, ready for [com.coinepro.core.marketintel] callers to hand to a log.
     *
     * Built here rather than at the call site so the field names are stable across every place that
     * writes them — an exported log is only searchable if the keys do not drift.
     */
    fun logFields(platform: String): Map<String, String> = mapOf(
        "platform" to platform,
        "route" to route,
        "status" to (status?.toString() ?: "—"),
        "received" to received.toString(),
        "kept" to kept.toString(),
        "dropped" to dropped.toString(),
        "first" to (firstPublished ?: "—"),
        "last" to (lastPublished ?: "—"),
        "keys" to (sampleKeys ?: "—"),
        "envelope" to (envelope ?: "—"),
        "failure" to (failure ?: "—"),
    )
}

data class MarketIntelState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val news: List<MarketNewsItem> = emptyList(),
    val calendar: List<EconomicEvent> = emptyList(),
    val serverTime: Instant? = null,
    val error: String? = null,
    /** See [MarketIntelSnapshot.calendarSource]. Reported, never drawn as data. */
    val calendarSource: CalendarSourceOutcome? = null,
    /** See [MarketIntelSnapshot.newsSource]. Reported, never drawn as data. */
    val newsSource: NewsFeedOutcome? = null,
    /** See [MarketIntelSnapshot.platform]. Null until a fetch has answered. */
    val platform: MarketPlatform? = null,
)

fun List<EconomicEvent>.highImpactWarningsFor(
    symbol: String,
    now: Instant,
    horizon: Duration = Duration.ofHours(6),
): List<EconomicEvent> {
    val relevance = relevanceForSymbol(symbol) ?: return emptyList()
    val recentBoundary = now.minus(Duration.ofHours(1))
    val futureBoundary = now.plus(horizon)
    return asSequence()
        .filter { it.impact == MarketImpact.HIGH }
        .filterNot { it.isStale }
        .filter { relevance in it.relevance }
        .filter { it.scheduledAt >= recentBoundary && it.scheduledAt <= futureBoundary }
        .sortedBy(EconomicEvent::scheduledAt)
        .toList()
}

fun relevanceForSymbol(symbol: String): MarketRelevance? = when (symbol.uppercase()) {
    "XAUUSD" -> MarketRelevance.GOLD
    "XAGUSD" -> MarketRelevance.SILVER
    else -> if (symbol.uppercase().endsWith("USDT")) MarketRelevance.CRYPTO else null
}
