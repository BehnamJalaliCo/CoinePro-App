package com.coinepro.core.marketintel

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
     */
    val imageUrl: String? = null,
    /**
     * The story's own text, or null where the feed sent only a summary.
     *
     * Null on both feeds today, and that is a fact about the servers rather than about this app:
     * TradeYar's `news_posts` stores `summary_fa` and no body, and the forex side is a cache of wire
     * headlines. `docs/SERVER_ASK_NEWS_MEDIA.md` is the ask; `articleBody` is what decides whether
     * what arrives under the name is really one.
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
)

data class MarketIntelState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val news: List<MarketNewsItem> = emptyList(),
    val calendar: List<EconomicEvent> = emptyList(),
    val serverTime: Instant? = null,
    val error: String? = null,
    /** See [MarketIntelSnapshot.calendarSource]. Reported, never drawn as data. */
    val calendarSource: CalendarSourceOutcome? = null,
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
