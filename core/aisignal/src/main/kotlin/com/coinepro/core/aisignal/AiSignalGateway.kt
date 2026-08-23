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

internal interface AiSignalApi {
    @GET("user/signals/ai/quota")
    suspend fun quota(): AiSignalQuotaResponseDto

    @POST("user/signals/ai/jobs")
    suspend fun createJob(@Body body: AiSignalCreateJobDto): AiSignalJobResponseDto

    @GET("user/signals/ai/jobs/{jobId}")
    suspend fun job(@Path("jobId") jobId: String): AiSignalJobResponseDto
}

internal data class AiSignalCreateJobDto(
    val symbol: String,
    val timeframe: String,
    val risk: String,
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
    )
}
