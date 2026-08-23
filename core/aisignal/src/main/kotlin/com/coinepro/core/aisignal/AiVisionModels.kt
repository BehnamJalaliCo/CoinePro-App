package com.coinepro.core.aisignal

enum class AiVisionImageSource { CAMERA, GALLERY }

data class AiVisionImage(
    val bytes: ByteArray,
    val mimeType: String,
    val source: AiVisionImageSource,
) {
    init {
        require(bytes.isNotEmpty()) { "Vision image is empty" }
        require(bytes.size <= MAX_IMAGE_BYTES) { "Vision image is too large" }
        require(mimeType in SUPPORTED_MIME_TYPES) { "Unsupported vision image type" }
    }

    companion object {
        const val MAX_IMAGE_BYTES: Int = 10 * 1024 * 1024
        val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}

data class AiVisionRequest(
    val symbol: String,
    val timeframe: AiSignalTimeframe,
)

data class AiVisionAnalysis(
    val signalId: Long,
    val symbol: String,
    val timeframe: String,
    val trend: String,
    val entry: Double,
    val stopLoss: Double,
    val targets: List<AiSignalTarget>,
    val confidence: Int,
    val explanation: String,
)

data class AiVisionJob(
    val id: String,
    val status: AiSignalJobStatus,
    val request: AiVisionRequest,
    val result: AiVisionAnalysis?,
    val errorMessage: String?,
    val quota: AiSignalQuota?,
) {
    val isPending: Boolean get() = status == AiSignalJobStatus.QUEUED || status == AiSignalJobStatus.RUNNING
    val canOpenValidatedSignal: Boolean get() = status == AiSignalJobStatus.DONE && result != null
}

data class AiVisionState(
    val selectedImage: AiVisionImage? = null,
    val submitting: Boolean = false,
    val job: AiVisionJob? = null,
    val quota: AiSignalQuota? = null,
    val entitlementRequired: Boolean = false,
    val quotaExhausted: Boolean = false,
    val error: String? = null,
)
