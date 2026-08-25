package com.coinepro.core.aisignal

import com.coinepro.core.model.SignalDirection

enum class AiSignalTimeframe(val wireValue: String, val label: String) {
    M5("M5", "5m"),
    M15("M15", "15m"),
    H1("H1", "1h"),
    H4("H4", "4h"),
    D1("D1", "1d"),
}

enum class AiSignalRisk(val wireValue: String, val label: String) {
    LOW("low", "Low"),
    MEDIUM("medium", "Medium"),
    HIGH("high", "High"),
}

enum class AiSignalJobStatus(val wireValue: String) {
    QUEUED("queued"),
    RUNNING("running"),
    DONE("done"),
    FAILED("failed"),
    EXPIRED("expired"),
}

object AiSignalProductScope {
    val defaultSymbols: List<String> = listOf(
        "XAUUSD",
        "XAGUSD",
        "BTCUSDT",
        "ETHUSDT",
        "BNBUSDT",
        "SOLUSDT",
        "XRPUSDT",
        "ADAUSDT",
        "DOGEUSDT",
        "TRXUSDT",
    )

    fun normalizeSymbol(raw: String): String? {
        val normalized = raw.trim().uppercase().replace("/", "").replace("-", "")
        return when {
            normalized == "XAUUSD" || normalized == "XAGUSD" -> normalized
            normalized.endsWith("USDT") && normalized.length > 4 -> normalized
            else -> null
        }
    }
}

/**
 * Everything the server will take into account when shaping a setup.
 *
 * Only [symbol] and [timeframe] are required. The rest narrow the result and are sent only when
 * the user actually set them, so an untouched control stays absent from the request rather than
 * being sent as a guess the model would then honour.
 */
data class AiSignalRequest(
    val symbol: String,
    val timeframe: AiSignalTimeframe,
    val risk: AiSignalRisk,
    val tradeStyle: AiTradeStyle? = null,
    val riskAppetite: AiRiskAppetite? = null,
    val directionBias: AiDirectionBias? = null,
    val minRiskReward: Double? = null,
    val lot: Double? = null,
    val riskPercent: Double? = null,
    val balance: Double? = null,
)

data class AiSignalQuota(
    val remaining: Int,
    val limit: Int,
    val resetAt: String?,
) {
    val exhausted: Boolean get() = remaining <= 0
}

data class AiSignalEntryZone(
    val low: Double,
    val high: Double,
)

data class AiSignalTarget(
    val level: Int,
    val price: Double,
)

data class AiGeneratedSignal(
    val signalId: Long,
    val symbol: String,
    val direction: SignalDirection,
    val timeframe: String,
    val entry: Double,
    val entryZone: AiSignalEntryZone?,
    val stopLoss: Double,
    val targets: List<AiSignalTarget>,
    val confidence: Int,
    val riskRewardTp1: Double?,
    val rationale: String?,
    val validatedAt: String?,
    /** Suggested position size, when the request supplied a balance or risk percentage. */
    val lot: Double? = null,
    val strategy: String? = null,
    /** Server-raised caveats about the setup. Shown verbatim; never summarised away. */
    val warnings: List<String> = emptyList(),
    /** The indicator readings behind the setup, so the screen can show its reasoning. */
    val snapshot: AiTechnicalSnapshot? = null,
    /** The recent series the model reasoned over, oldest first. */
    val recentCandles: List<AiCandle> = emptyList(),
)

data class AiSignalJob(
    val id: String,
    val status: AiSignalJobStatus,
    val request: AiSignalRequest,
    val result: AiGeneratedSignal?,
    val errorCode: String?,
    val errorMessage: String?,
    val quota: AiSignalQuota?,
    val createdAt: String?,
    val expiresAt: String?,
) {
    val isPending: Boolean get() = status == AiSignalJobStatus.QUEUED || status == AiSignalJobStatus.RUNNING
    val canOpenValidatedSignal: Boolean get() = status == AiSignalJobStatus.DONE && result != null
}

data class AiSignalState(
    val submitting: Boolean = false,
    val refreshingQuota: Boolean = false,
    val job: AiSignalJob? = null,
    val quota: AiSignalQuota? = null,
    val entitlementRequired: Boolean = false,
    val quotaExhausted: Boolean = false,
    val error: String? = null,
)
