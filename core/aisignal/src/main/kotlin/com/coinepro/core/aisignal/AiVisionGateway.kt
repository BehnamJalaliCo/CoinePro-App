package com.coinepro.core.aisignal

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import okhttp3.MultipartBody
import okhttp3.RequestBody

interface AiVisionGateway {
    suspend fun quota(): AiSignalQuota
    suspend fun createJob(image: AiVisionImage, request: AiVisionRequest): AiVisionJob
    suspend fun job(jobId: String): AiVisionJob
}

internal interface AiVisionApi {
    @GET("user/signals/ai/vision/quota")
    suspend fun quota(): AiSignalQuotaResponseDto

    @Multipart
    @POST("user/signals/ai/vision/jobs")
    suspend fun createJob(
        @Part image: MultipartBody.Part,
        @Part("symbol") symbol: RequestBody,
        @Part("timeframe") timeframe: RequestBody,
    ): AiVisionJobResponseDto

    @GET("user/signals/ai/vision/jobs/{jobId}")
    suspend fun job(@Path("jobId") jobId: String): AiVisionJobResponseDto
}

internal data class AiVisionRequestDto(val symbol: String? = null, val timeframe: String? = null)
internal data class AiVisionAnalysisDto(
    val validated: Boolean = false,
    val signalId: Long? = null,
    val symbol: String? = null,
    val timeframe: String? = null,
    val trend: String? = null,
    val entry: Double? = null,
    val stopLoss: Double? = null,
    val targets: List<AiSignalTargetDto> = emptyList(),
    val confidence: Int? = null,
    val explanation: String? = null,
)
internal data class AiVisionJobDto(
    val id: String? = null,
    val status: String? = null,
    val request: AiVisionRequestDto? = null,
    val result: AiVisionAnalysisDto? = null,
    val errorMessage: String? = null,
)
internal data class AiVisionJobResponseDto(val job: AiVisionJobDto? = null, val quota: AiSignalQuotaDto? = null)

class NetworkAiVisionGateway private constructor(private val api: AiVisionApi) : AiVisionGateway {
    override suspend fun quota(): AiSignalQuota = translate {
        requireNotNull(api.quota().quota?.toDomain()) { "Invalid AI Vision quota response" }
    }

    override suspend fun createJob(image: AiVisionImage, request: AiVisionRequest): AiVisionJob = translate {
        val symbol = requireNotNull(AiSignalProductScope.normalizeSymbol(request.symbol)) { "Unsupported AI Vision symbol" }
        val body = image.bytes.toRequestBody(image.mimeType.toMediaType())
        val part = MultipartBody.Part.createFormData("image", "chart.${extension(image.mimeType)}", body)
        val textType = "text/plain".toMediaType()
        val response = api.createJob(
            part,
            symbol.toRequestBody(textType),
            request.timeframe.wireValue.toRequestBody(textType),
        )
        requireNotNull(response.job?.toDomain(response.quota?.toDomain())) { "Invalid AI Vision job response" }
    }

    override suspend fun job(jobId: String): AiVisionJob = translate {
        require(jobId.isNotBlank()) { "Missing AI Vision job ID" }
        val response = api.job(jobId)
        requireNotNull(response.job?.toDomain(response.quota?.toDomain())) { "Invalid AI Vision job response" }
    }

    private suspend fun <T> translate(block: suspend () -> T): T = try { block() } catch (error: HttpException) {
        when (error.code()) {
            403 -> throw AiSignalEntitlementRequiredException()
            410 -> throw AiSignalJobExpiredException()
            413 -> throw AiSignalRequestRejectedException("AI Vision image is too large")
            415 -> throw AiSignalRequestRejectedException("AI Vision image type is unsupported")
            422 -> throw AiSignalRequestRejectedException("AI Vision request was rejected by server validation")
            429 -> throw AiSignalQuotaExhaustedException()
            else -> throw error
        }
    }

    companion object { fun create(retrofit: Retrofit) = NetworkAiVisionGateway(retrofit.create(AiVisionApi::class.java)) }
}

private fun extension(mimeType: String) = when (mimeType) {
    "image/png" -> "png"
    "image/webp" -> "webp"
    else -> "jpg"
}

internal fun AiVisionRequestDto.toDomain(): AiVisionRequest? {
    val symbol = AiSignalProductScope.normalizeSymbol(symbol.orEmpty()) ?: return null
    val timeframe = AiSignalTimeframe.entries.firstOrNull { it.wireValue.equals(timeframe, true) } ?: return null
    return AiVisionRequest(symbol, timeframe)
}

internal fun AiVisionJobDto.toDomain(quota: AiSignalQuota?): AiVisionJob? {
    val id = id?.takeIf { it.isNotBlank() } ?: return null
    val status = AiSignalJobStatus.entries.firstOrNull { it.wireValue == this.status?.lowercase() } ?: return null
    val request = request?.toDomain() ?: return null
    val result = result?.toDomain(request)
    if (status == AiSignalJobStatus.DONE && result == null) return null
    return AiVisionJob(id, status, request, result, errorMessage, quota)
}

internal fun AiVisionAnalysisDto.toDomain(expected: AiVisionRequest): AiVisionAnalysis? {
    if (!validated) return null
    val signalId = signalId?.takeIf { it > 0 } ?: return null
    val symbol = AiSignalProductScope.normalizeSymbol(symbol.orEmpty()) ?: return null
    if (symbol != expected.symbol) return null
    val timeframe = timeframe?.takeIf { it.equals(expected.timeframe.wireValue, true) } ?: return null
    val trend = trend?.uppercase()?.takeIf { it in setOf("BULLISH", "BEARISH", "NEUTRAL") } ?: return null
    val entry = entry?.takeIf { it.isFinite() && it > 0 } ?: return null
    val stop = stopLoss?.takeIf { it.isFinite() && it > 0 } ?: return null
    val confidence = confidence?.takeIf { it in 0..100 } ?: return null
    val explanation = explanation?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val targets = targets.mapNotNull {
        val level = it.level?.takeIf { value -> value in 1..3 } ?: return@mapNotNull null
        val price = it.price?.takeIf { value -> value.isFinite() && value > 0 } ?: return@mapNotNull null
        AiSignalTarget(level, price)
    }.distinctBy { it.level }.sortedBy { it.level }
    if (targets.isEmpty() || targets.size != this.targets.size) return null
    return AiVisionAnalysis(signalId, symbol, timeframe, trend, entry, stop, targets, confidence, explanation)
}
