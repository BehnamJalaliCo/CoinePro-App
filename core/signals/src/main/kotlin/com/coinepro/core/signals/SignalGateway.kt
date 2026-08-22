package com.coinepro.core.signals

import com.coinepro.core.model.MarketType
import com.coinepro.core.model.QuoteSource
import com.coinepro.core.model.SignalDirection
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface SignalGateway {
    suspend fun list(
        market: SignalMarketFilter,
        status: SignalStatusFilter,
        limit: Int = 50,
        offset: Int = 0,
    ): SignalPage

    suspend fun detail(signalId: Long): TradingSignal
}

internal interface SignalApi {
    @GET("user/signals")
    suspend fun listSignals(
        @Query("market") market: String,
        @Query("status") status: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): SignalListResponseDto

    @GET("user/signals/{signalId}")
    suspend fun signalDetail(@Path("signalId") signalId: Long): SignalDetailResponseDto
}

internal data class SignalListResponseDto(
    val items: List<SignalDto> = emptyList(),
    val total: Int = 0,
    val serverTimeMs: Long? = null,
)

internal data class SignalDetailResponseDto(
    val signal: SignalDto? = null,
    val serverTimeMs: Long? = null,
)

internal data class SignalDto(
    val id: Long? = null,
    val market: String? = null,
    val symbol: String? = null,
    val direction: String? = null,
    val status: String? = null,
    val timeframe: String? = null,
    val strategy: String? = null,
    val confidence: Int? = null,
    val entry: Double? = null,
    val entryZone: EntryZoneDto? = null,
    val stopLoss: Double? = null,
    val targets: List<SignalTargetDto> = emptyList(),
    val riskRewardTp1: Double? = null,
    val currentQuote: SignalQuoteDto? = null,
    val livePnlPercent: Double? = null,
    val hitTarget: String? = null,
    val rationale: String? = null,
    val scoreBreakdown: ScoreBreakdownDto? = null,
    val closeReason: String? = null,
    val result: SignalResultDto? = null,
    val createdAt: String? = null,
    val closedAt: String? = null,
)

internal data class EntryZoneDto(val low: Double? = null, val high: Double? = null)
internal data class SignalTargetDto(val level: Int = 0, val price: Double? = null, val hit: Boolean = false)
internal data class SignalQuoteDto(
    val price: Double? = null,
    val bid: Double? = null,
    val ask: Double? = null,
    val timestampMs: Long? = null,
    val source: String? = null,
)
internal data class ScoreBreakdownDto(
    val technical: Double? = null,
    val pattern: Double? = null,
    val ml: Double? = null,
)
internal data class SignalResultDto(val pnlUsd: Double? = null, val source: String? = null)

class NetworkSignalGateway private constructor(
    private val api: SignalApi,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : SignalGateway {
    override suspend fun list(
        market: SignalMarketFilter,
        status: SignalStatusFilter,
        limit: Int,
        offset: Int,
    ): SignalPage = translateAccess {
        val response = api.listSignals(market.wireValue, status.wireValue, limit, offset)
        SignalPage(
            items = response.items.mapNotNull { it.toDomain(nowMillis()) },
            total = response.total,
            serverTimeEpochMillis = response.serverTimeMs,
        )
    }

    override suspend fun detail(signalId: Long): TradingSignal = translateAccess {
        val response = api.signalDetail(signalId)
        requireNotNull(response.signal?.toDomain(nowMillis())) { "Invalid signal payload" }
    }

    private suspend fun <T> translateAccess(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        if (error.code() == 403) throw SignalMembershipRequiredException()
        throw error
    }

    companion object {
        fun create(retrofit: Retrofit): NetworkSignalGateway =
            NetworkSignalGateway(retrofit.create(SignalApi::class.java))
    }
}

internal fun SignalDto.toDomain(nowMs: Long): TradingSignal? {
    val safeId = id ?: return null
    val safeSymbol = symbol?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
    val safeMarket = when (market?.lowercase()) {
        "forex" -> MarketType.FOREX
        "crypto" -> MarketType.CRYPTO
        else -> return null
    }
    val safeDirection = when (direction?.uppercase()) {
        "BUY" -> SignalDirection.BUY
        "SELL" -> SignalDirection.SELL
        else -> return null
    }
    val quote = currentQuote?.toDomain(safeMarket, nowMs)
    return TradingSignal(
        id = safeId,
        market = safeMarket,
        symbol = safeSymbol,
        direction = safeDirection,
        status = status.orEmpty(),
        timeframe = timeframe,
        strategy = strategy,
        confidence = confidence?.coerceIn(0, 100),
        entry = entry,
        entryZone = entryZone?.let { SignalEntryZone(it.low, it.high) },
        stopLoss = stopLoss,
        targets = targets.map { SignalTarget(it.level, it.price, it.hit) },
        riskRewardTp1 = riskRewardTp1,
        currentQuote = quote,
        livePnlPercent = livePnlPercent,
        hitTarget = hitTarget,
        rationale = rationale,
        scoreBreakdown = scoreBreakdown?.let { SignalScoreBreakdown(it.technical, it.pattern, it.ml) },
        closeReason = closeReason,
        result = result?.let { SignalResult(it.pnlUsd, it.source) },
        createdAt = createdAt,
        closedAt = closedAt,
    )
}

internal fun SignalQuoteDto.toDomain(market: MarketType, nowMs: Long): SignalLiveQuote? {
    val safePrice = price?.takeIf { it > 0 } ?: return null
    val sourceKind = when {
        source.orEmpty().contains("lbank", ignoreCase = true) -> QuoteSource.LBANK
        source.orEmpty().contains("finnhub", ignoreCase = true) -> QuoteSource.FINNHUB
        else -> QuoteSource.UNKNOWN
    }
    val timestamp = timestampMs
    val threshold = when (market) {
        MarketType.CRYPTO -> 15_000L
        MarketType.FOREX -> 90_000L
    }
    val stale = timestamp == null || timestamp <= 0L || nowMs - timestamp > threshold || nowMs - timestamp < -10_000L
    return SignalLiveQuote(
        price = safePrice,
        bid = bid,
        ask = ask,
        timestampEpochMillis = timestamp,
        source = sourceKind,
        isStale = stale,
    )
}
