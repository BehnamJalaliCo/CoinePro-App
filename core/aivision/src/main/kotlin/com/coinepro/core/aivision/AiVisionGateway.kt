package com.coinepro.core.aivision

import com.coinepro.core.aisignal.AiSignalProductScope
import com.coinepro.core.model.SignalDirection
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface AiVisionGateway {
    suspend fun createJob(upload: AiVisionImageUpload): AiVisionJob
    suspend fun job(jobId: String): AiVisionJob
}

class AiVisionEntitlementRequiredException : Exception("AI Vision entitlement required")
class AiVisionJobExpiredException : Exception("AI Vision job expired")
class AiVisionImageTooLargeException : Exception("AI Vision image is too large")
class AiVisionUnsupportedMediaException : Exception("AI Vision image type is unsupported")
class AiVisionRequestRejectedException(message: String) : Exception(message)
class AiVisionRateLimitedException : Exception("AI Vision rate limit reached")

internal interface AiVisionApi {
    @Multipart
    @POST("user/ai/vision/jobs")
    suspend fun createJob(@Part image: MultipartBody.Part): AiVisionJobResponseDto

    @GET("user/ai/vision/jobs/{jobId}")
    suspend fun job(@Path("jobId") jobId: String): AiVisionJobResponseDto
}

internal data class AiVisionEntryZoneDto(
    val low: Double? = null,
    val high: Double? = null,
)

internal data class AiVisionTargetDto(
    val level: Int? = null,
    val price: Double? = null,
)

internal data class AiVisionResultDto(
    val validated: Boolean = false,
    val assessment: String? = null,
    val symbol: String? = null,
    val timeframe: String? = null,
    val confidence: Int? = null,
    val trendBias: String? = null,
    val marketStructure: String? = null,
    val setup: String? = null,
    val direction: String? = null,
    val entryZone: AiVisionEntryZoneDto? = null,
    val stopLoss: Double? = null,
    val targets: List<AiVisionTargetDto> = emptyList(),
    val risk: String? = null,
    val reasoning: String? = null,
    val signalId: Long? = null,
    val validatedAt: String? = null,
)

internal data class AiVisionJobDto(
    val id: String? = null,
    val status: String? = null,
    val result: AiVisionResultDto? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val createdAt: String? = null,
    val expiresAt: String? = null,
)

internal data class AiVisionJobResponseDto(
    val job: AiVisionJobDto? = null,
)

class NetworkAiVisionGateway private constructor(
    private val api: AiVisionApi,
) : AiVisionGateway {
    override suspend fun createJob(upload: AiVisionImageUpload): AiVisionJob = translate {
        require(upload.isSupported) { "Invalid AI Vision image upload" }
        val body = upload.bytes.toRequestBody(upload.mimeType.toMediaType())
        val part = MultipartBody.Part.createFormData("image", upload.fileName, body)
        requireNotNull(api.createJob(part).job?.toDomain()) { "Invalid AI Vision job response" }
    }

    override suspend fun job(jobId: String): AiVisionJob = translate {
        require(jobId.isNotBlank()) { "Missing AI Vision job ID" }
        requireNotNull(api.job(jobId).job?.toDomain()) { "Invalid AI Vision job response" }
    }

    private suspend fun <T> translate(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        when (error.code()) {
            403 -> throw AiVisionEntitlementRequiredException()
            410 -> throw AiVisionJobExpiredException()
            413 -> throw AiVisionImageTooLargeException()
            415 -> throw AiVisionUnsupportedMediaException()
            422 -> throw AiVisionRequestRejectedException("AI Vision image was rejected by server validation")
            429 -> throw AiVisionRateLimitedException()
            else -> throw error
        }
    }

    companion object {
        fun create(retrofit: Retrofit): NetworkAiVisionGateway =
            NetworkAiVisionGateway(retrofit.create(AiVisionApi::class.java))
    }
}

internal fun AiVisionJobDto.toDomain(): AiVisionJob? {
    val safeId = id?.takeIf { it.isNotBlank() } ?: return null
    val safeStatus = AiVisionJobStatus.entries.firstOrNull {
        it.wireValue == status?.lowercase()
    } ?: return null
    return AiVisionJob(
        id = safeId,
        status = safeStatus,
        result = result?.toDomain(),
        errorCode = errorCode,
        errorMessage = errorMessage?.trim()?.takeIf { it.isNotBlank() },
        createdAt = createdAt,
        expiresAt = expiresAt,
    )
}

internal fun AiVisionResultDto.toDomain(): AiVisionResult? {
    if (!validated) return null
    val safeAssessment = AiVisionAssessment.entries.firstOrNull {
        it.wireValue == assessment?.lowercase()
    } ?: return null
    val safeConfidence = confidence?.takeIf { it in 0..100 }
        ?: if (safeAssessment == AiVisionAssessment.ACTIONABLE) return null else null
    val safeSymbol = symbol?.let { AiSignalProductScope.normalizeSymbol(it) ?: return null }
    val safeTimeframe = timeframe?.let { AiVisionTimeframes.normalize(it) ?: return null }
    val cleanTrend = trendBias.clean()
    val cleanStructure = marketStructure.clean()
    val cleanSetup = setup.clean()
    val cleanReasoning = reasoning.clean()
    val cleanRisk = risk?.trim()?.lowercase()?.takeIf { it in setOf("low", "medium", "high") }

    if (safeAssessment != AiVisionAssessment.ACTIONABLE) {
        if (
            signalId != null ||
            direction != null ||
            entryZone != null ||
            stopLoss != null ||
            targets.isNotEmpty()
        ) return null
        return AiVisionResult(
            assessment = safeAssessment,
            symbol = safeSymbol,
            timeframe = safeTimeframe,
            confidence = safeConfidence,
            trendBias = cleanTrend,
            marketStructure = cleanStructure,
            setup = cleanSetup,
            direction = null,
            entryZone = null,
            stopLoss = null,
            targets = emptyList(),
            risk = cleanRisk,
            reasoning = cleanReasoning,
            signalId = null,
            validatedAt = validatedAt,
        )
    }

    val safeSignalId = signalId?.takeIf { it > 0L } ?: return null
    val actionableSymbol = safeSymbol ?: return null
    val actionableTimeframe = safeTimeframe ?: return null
    val safeDirection = when (direction?.uppercase()) {
        "BUY" -> SignalDirection.BUY
        "SELL" -> SignalDirection.SELL
        else -> return null
    }
    val zone = entryZone ?: return null
    val low = zone.low?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val high = zone.high?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    if (low > high) return null
    val safeStop = stopLoss?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val safeTargets = targets.mapNotNull { target ->
        val level = target.level?.takeIf { it in 1..3 } ?: return@mapNotNull null
        val price = target.price?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
        AiVisionTarget(level, price)
    }
        .distinctBy { it.level }
        .sortedBy { it.level }
    if (safeTargets.isEmpty() || safeTargets.size != targets.size) return null

    when (safeDirection) {
        SignalDirection.BUY -> if (safeStop >= low || safeTargets.any { it.price <= high }) return null
        SignalDirection.SELL -> if (safeStop <= high || safeTargets.any { it.price >= low }) return null
        SignalDirection.NEUTRAL -> return null
    }

    if (
        cleanTrend == null ||
        cleanStructure == null ||
        cleanSetup == null ||
        cleanRisk == null ||
        cleanReasoning == null
    ) return null

    return AiVisionResult(
        assessment = safeAssessment,
        symbol = actionableSymbol,
        timeframe = actionableTimeframe,
        confidence = safeConfidence,
        trendBias = cleanTrend,
        marketStructure = cleanStructure,
        setup = cleanSetup,
        direction = safeDirection,
        entryZone = AiVisionEntryZone(low, high),
        stopLoss = safeStop,
        targets = safeTargets,
        risk = cleanRisk,
        reasoning = cleanReasoning,
        signalId = safeSignalId,
        validatedAt = validatedAt,
    )
}

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotBlank() }
