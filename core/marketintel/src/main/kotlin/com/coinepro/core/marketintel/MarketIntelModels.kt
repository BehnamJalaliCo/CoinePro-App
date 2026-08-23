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
)

data class MarketIntelState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val news: List<MarketNewsItem> = emptyList(),
    val calendar: List<EconomicEvent> = emptyList(),
    val serverTime: Instant? = null,
    val error: String? = null,
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
