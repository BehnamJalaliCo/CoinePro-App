package com.coinepro.core.marketintel

import java.net.URI
import java.time.Instant
import com.coinepro.core.common.parseWireInstant
import com.coinepro.core.model.MarketPlatform
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
) : MarketIntelGateway {
    override suspend fun snapshot(): MarketIntelSnapshot = when (platform) {
        // The prefix is not decoration: TradeYar serves every mobile route under `api/mobile/v1`
        // and CoinePro-FX under `user`. A path built for one reaches nothing on the other, and
        // arrives as an ordinary HTTP error rather than as anything resembling a wiring mistake.
        MarketPlatform.COINEPRO_FX -> api.forexSnapshot()
        MarketPlatform.TRADEYAR -> api.cryptoSnapshot()
    }.toDomain()

    companion object {
        fun create(retrofit: Retrofit, platform: MarketPlatform): MarketIntelGateway =
            NetworkMarketIntelGateway(retrofit.create(MarketIntelApi::class.java), platform)
    }
}

private interface MarketIntelApi {
    @GET("user/market-intelligence")
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

internal fun MarketIntelSnapshotDto.toDomain(): MarketIntelSnapshot = MarketIntelSnapshot(
    news = news.mapNotNull(MarketNewsDto::toDomain),
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

internal fun parseImpact(value: String?): MarketImpact = when (value?.trim()?.lowercase()) {
    "low" -> MarketImpact.LOW
    "medium" -> MarketImpact.MEDIUM
    "high" -> MarketImpact.HIGH
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
