package com.coinepro.core.aisignal

import com.coinepro.core.model.SignalDirection
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AiSignalGateway {
    suspend fun quota(): AiSignalQuota
    suspend fun createJob(request: AiSignalRequest): AiSignalJob
    suspend fun job(jobId: String): AiSignalJob
}

class AiSignalEntitlementRequiredException : Exception("AI Signal entitlement required")
class AiSignalQuotaExhaustedException : Exception("AI Signal quota exhausted")
class AiSignalJobExpiredException : Exception("AI Signal job expired")
class AiSignalRequestRejectedException(message: String) : Exception(message)

/**
 * Paths follow CoinePro-FX, which mounts this router at `/user/ai-signal`. The client previously
 * called `user/signals/ai/…`, which the server has never served.
 */
internal interface AiSignalApi {
    @GET("user/ai-signal/quota")
    suspend fun quota(): AiSignalQuotaResponseDto

    @POST("user/ai-signal/generate")
    suspend fun createJob(@Body body: AiSignalCreateJobDto): AiSignalJobResponseDto

    @GET("user/ai-signal/result/{jobId}")
    suspend fun job(@Path("jobId") jobId: String): AiSignalJobResponseDto
}

internal data class AiSignalCreateJobDto(
    val symbol: String,
    val timeframe: String,
    val risk: String,
    // Null means the user left the control alone. Gson omits nulls by default, so an untouched
    // control never reaches the model as a value it would then act on.
    val tradeStyle: String? = null,
    val riskAppetite: String? = null,
    val directionBias: String? = null,
    val minRr: Double? = null,
    val lot: Double? = null,
    val riskPct: Double? = null,
    val balance: Double? = null,
)

internal data class AiSignalQuotaDto(
    val remaining: Int? = null,
    val limit: Int? = null,
    val resetAt: String? = null,
)

internal data class AiSignalQuotaResponseDto(
    val quota: AiSignalQuotaDto? = null,
)

internal data class AiSignalRequestDto(
    val symbol: String? = null,
    val timeframe: String? = null,
    val risk: String? = null,
)

internal data class AiSignalEntryZoneDto(
    val low: Double? = null,
    val high: Double? = null,
)

internal data class AiSignalTargetDto(
    val level: Int? = null,
    val price: Double? = null,
)

internal data class AiCandleDto(
    val o: Double? = null,
    val h: Double? = null,
    val l: Double? = null,
    val c: Double? = null,
)

internal data class AiGeneratedSignalDto(
    val validated: Boolean = false,
    val signalId: Long? = null,
    val symbol: String? = null,
    val direction: String? = null,
    val timeframe: String? = null,
    val entry: Double? = null,
    val entryZone: AiSignalEntryZoneDto? = null,
    val stopLoss: Double? = null,
    val targets: List<AiSignalTargetDto> = emptyList(),
    val confidence: Int? = null,
    val riskRewardTp1: Double? = null,
    val rationale: String? = null,
    val validatedAt: String? = null,
    val lot: Double? = null,
    val strategy: String? = null,
    val warnings: List<String> = emptyList(),
    val priceNow: Double? = null,
    val ema20: Double? = null,
    val ema50: Double? = null,
    val ema200: Double? = null,
    val rsi14: Double? = null,
    val atr14: Double? = null,
    val macd: Double? = null,
    val bbUpper: Double? = null,
    val bbLower: Double? = null,
    val swingHigh20: Double? = null,
    val swingLow20: Double? = null,
    val changePct20: Double? = null,
    val recentCandles: List<AiCandleDto> = emptyList(),
)

internal data class AiSignalJobDto(
    val id: String? = null,
    val status: String? = null,
    val request: AiSignalRequestDto? = null,
    val result: AiGeneratedSignalDto? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null,
)

internal data class AiSignalJobResponseDto(
    val job: AiSignalJobDto? = null,
    val quota: AiSignalQuotaDto? = null,
)

class NetworkAiSignalGateway private constructor(
    private val api: AiSignalApi,
) : AiSignalGateway {
    override suspend fun quota(): AiSignalQuota = translate {
        requireNotNull(api.quota().quota?.toDomain()) { "Invalid AI Signal quota response" }
    }

    override suspend fun createJob(request: AiSignalRequest): AiSignalJob = translate {
        val safeSymbol = requireNotNull(AiSignalProductScope.normalizeSymbol(request.symbol)) {
            "Unsupported AI Signal symbol"
        }
        val response = api.createJob(
            AiSignalCreateJobDto(
                symbol = safeSymbol,
                timeframe = request.timeframe.wireValue,
                risk = request.risk.wireValue,
                tradeStyle = request.tradeStyle?.wireValue,
                riskAppetite = request.riskAppetite?.wireValue,
                directionBias = request.directionBias?.wireValue,
                minRr = request.minRiskReward?.takeIf { it.isFinite() && it > 0.0 },
                lot = request.lot?.takeIf { it.isFinite() && it > 0.0 },
                riskPct = request.riskPercent?.takeIf { it.isFinite() && it > 0.0 },
                balance = request.balance?.takeIf { it.isFinite() && it >= 0.0 },
            ),
        )
        requireNotNull(response.job?.toDomain(response.quota?.toDomain())) {
            "Invalid AI Signal job response"
        }
    }

    override suspend fun job(jobId: String): AiSignalJob = translate {
        require(jobId.isNotBlank()) { "Missing AI Signal job ID" }
        val response = api.job(jobId)
        requireNotNull(response.job?.toDomain(response.quota?.toDomain())) {
            "Invalid AI Signal job response"
        }
    }

    private suspend fun <T> translate(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        when (error.code()) {
            403 -> throw AiSignalEntitlementRequiredException()
            410 -> throw AiSignalJobExpiredException()
            422 -> throw AiSignalRequestRejectedException("AI Signal request was rejected by server validation")
            429 -> throw AiSignalQuotaExhaustedException()
            else -> throw error
        }
    }

    companion object {
        fun create(retrofit: Retrofit): NetworkAiSignalGateway =
            NetworkAiSignalGateway(retrofit.create(AiSignalApi::class.java))
    }
}

internal fun AiSignalQuotaDto.toDomain(): AiSignalQuota? {
    val safeLimit = limit?.takeIf { it >= 0 } ?: return null
    val safeRemaining = remaining?.takeIf { it >= 0 } ?: return null
    return AiSignalQuota(
        remaining = safeRemaining.coerceAtMost(safeLimit),
        limit = safeLimit,
        resetAt = resetAt,
    )
}

internal fun AiSignalRequestDto.toDomain(): AiSignalRequest? {
    val safeSymbol = AiSignalProductScope.normalizeSymbol(symbol.orEmpty()) ?: return null
    val safeTimeframe = AiSignalTimeframe.entries.firstOrNull {
        it.wireValue.equals(timeframe, ignoreCase = true)
    } ?: return null
    val safeRisk = AiSignalRisk.entries.firstOrNull {
        it.wireValue.equals(risk, ignoreCase = true)
    } ?: return null
    return AiSignalRequest(safeSymbol, safeTimeframe, safeRisk)
}

internal fun AiSignalJobDto.toDomain(quota: AiSignalQuota?): AiSignalJob? {
    val safeId = id?.takeIf { it.isNotBlank() } ?: return null
    val safeStatus = AiSignalJobStatus.entries.firstOrNull { it.wireValue == status?.lowercase() } ?: return null
    val safeRequest = request?.toDomain() ?: return null
    val safeResult = result?.toDomain(safeRequest)

    return AiSignalJob(
        id = safeId,
        status = safeStatus,
        request = safeRequest,
        result = safeResult,
        errorCode = errorCode,
        errorMessage = errorMessage,
        quota = quota,
        createdAt = createdAt,
        expiresAt = expiresAt,
    )
}

internal fun AiGeneratedSignalDto.toDomain(expected: AiSignalRequest): AiGeneratedSignal? {
    if (!validated) return null
    val safeSignalId = signalId?.takeIf { it > 0L } ?: return null
    val safeSymbol = AiSignalProductScope.normalizeSymbol(symbol.orEmpty()) ?: return null
    if (safeSymbol != expected.symbol) return null
    val safeDirection = when (direction?.uppercase()) {
        "BUY" -> SignalDirection.BUY
        "SELL" -> SignalDirection.SELL
        else -> return null
    }
    val safeTimeframe = timeframe?.takeIf {
        it.equals(expected.timeframe.wireValue, ignoreCase = true)
    } ?: return null
    val safeEntry = entry?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val safeStop = stopLoss?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val safeConfidence = confidence?.takeIf { it in 0..100 } ?: return null
    val safeTargets = targets.mapNotNull { target ->
        val level = target.level?.takeIf { it in 1..3 } ?: return@mapNotNull null
        val price = target.price?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
        AiSignalTarget(level, price)
    }
        .distinctBy { it.level }
        .sortedBy { it.level }
    if (safeTargets.isEmpty() || safeTargets.size != targets.size) return null

    val safeZone = entryZone?.let { zone ->
        val low = zone.low?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        val high = zone.high?.takeIf { it.isFinite() && it > 0.0 } ?: return null
        if (low > high) return null
        AiSignalEntryZone(low, high)
    }
    val safeRiskReward = riskRewardTp1?.let {
        if (!it.isFinite() || it <= 0.0) return null
        it
    }

    // Indicators are reported as-is. A value the server could not compute stays null rather than
    // being coerced to zero, which would read on screen as a real reading of zero.
    val snapshot = AiTechnicalSnapshot(
        ema20 = ema20.finiteOrNull(),
        ema50 = ema50.finiteOrNull(),
        ema200 = ema200.finiteOrNull(),
        rsi14 = rsi14.finiteOrNull(),
        atr14 = atr14.finiteOrNull(),
        macd = macd.finiteOrNull(),
        bollingerUpper = bbUpper.finiteOrNull(),
        bollingerLower = bbLower.finiteOrNull(),
        swingHigh20 = swingHigh20.finiteOrNull(),
        swingLow20 = swingLow20.finiteOrNull(),
        changePercent20 = changePct20.finiteOrNull(),
        priceNow = priceNow.finiteOrNull(),
    )
    val candles = recentCandles.mapNotNull { it.toDomain() }

    return AiGeneratedSignal(
        signalId = safeSignalId,
        symbol = safeSymbol,
        direction = safeDirection,
        timeframe = safeTimeframe,
        entry = safeEntry,
        entryZone = safeZone,
        stopLoss = safeStop,
        targets = safeTargets,
        confidence = safeConfidence,
        riskRewardTp1 = safeRiskReward,
        rationale = rationale?.trim()?.takeIf { it.isNotBlank() },
        validatedAt = validatedAt,
        lot = lot?.takeIf { it.isFinite() && it > 0.0 },
        strategy = strategy?.trim()?.takeIf { it.isNotBlank() },
        warnings = warnings.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
        snapshot = snapshot.takeIf { it.hasAny },
        recentCandles = candles,
    )
}

private fun Double?.finiteOrNull(): Double? = this?.takeIf(Double::isFinite)

/** A candle is only usable if every leg is present, positive and ordered. */
internal fun AiCandleDto.toDomain(): AiCandle? {
    val open = o.finiteOrNull()?.takeIf { it > 0.0 } ?: return null
    val high = h.finiteOrNull()?.takeIf { it > 0.0 } ?: return null
    val low = l.finiteOrNull()?.takeIf { it > 0.0 } ?: return null
    val close = c.finiteOrNull()?.takeIf { it > 0.0 } ?: return null
    if (high < low || high < open || high < close || low > open || low > close) return null
    return AiCandle(open = open, high = high, low = low, close = close)
}
