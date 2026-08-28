package com.coinepro.core.aivision

import com.coinepro.core.common.UiMessage
import com.coinepro.core.model.SignalDirection

const val AI_VISION_MAX_UPLOAD_BYTES: Int = 6_000_000

enum class AiVisionJobStatus(val wireValue: String) {
    QUEUED("queued"),
    RUNNING("running"),
    DONE("done"),
    FAILED("failed"),
    EXPIRED("expired"),
}

enum class AiVisionAssessment(val wireValue: String) {
    ACTIONABLE("actionable"),
    LOW_CONFIDENCE("low_confidence"),
    UNKNOWN("unknown"),
    UNSUPPORTED("unsupported"),
}

object AiVisionTimeframes {
    val supported: Set<String> = setOf("M1", "M5", "M15", "H1", "H4", "D1")

    fun normalize(raw: String?): String? = raw
        ?.trim()
        ?.uppercase()
        ?.takeIf { it in supported }
}

data class AiVisionImageUpload(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    val isSupported: Boolean
        get() = fileName.isNotBlank() &&
            mimeType in setOf("image/jpeg", "image/png", "image/webp") &&
            bytes.isNotEmpty() &&
            bytes.size <= AI_VISION_MAX_UPLOAD_BYTES
}

data class AiVisionEntryZone(
    val low: Double,
    val high: Double,
)

data class AiVisionTarget(
    val level: Int,
    val price: Double,
)

data class AiVisionResult(
    val assessment: AiVisionAssessment,
    val symbol: String?,
    val timeframe: String?,
    val confidence: Int?,
    val trendBias: String?,
    val marketStructure: String?,
    val setup: String?,
    val direction: SignalDirection?,
    val entryZone: AiVisionEntryZone?,
    val stopLoss: Double?,
    val targets: List<AiVisionTarget>,
    val risk: String?,
    val reasoning: String?,
    val signalId: Long?,
    val validatedAt: String?,
) {
    val canOpenValidatedSignal: Boolean
        get() = assessment == AiVisionAssessment.ACTIONABLE && signalId != null && signalId > 0L
}

data class AiVisionJob(
    val id: String,
    val status: AiVisionJobStatus,
    val result: AiVisionResult?,
    val errorCode: String?,
    val errorMessage: String?,
    val createdAt: String?,
    val expiresAt: String?,
) {
    val isPending: Boolean
        get() = status == AiVisionJobStatus.QUEUED || status == AiVisionJobStatus.RUNNING
}

data class AiVisionState(
    val uploading: Boolean = false,
    val job: AiVisionJob? = null,
    /**
     * Owned copy, in the reader's language.
     *
     * This was a `String?` that the controller wrote authored **English** sentences into — "Write a
     * message before sending.", "The prepared image is too large for AI Vision." — and the screen
     * rendered verbatim, to an audience whose default language is Persian. Not exception text
     * leaking through: sentences somebody wrote, for the reader, in the wrong language.
     */
    val error: UiMessage? = null,
)
