package com.coinepro.core.aisignal

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.SignalDirection
import com.google.gson.annotations.SerializedName
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

interface AiSignalGateway {
    suspend fun quota(): AiSignalQuota
    suspend fun createJob(request: AiSignalRequest): AiSignalJob

    /**
     * [request] is what was asked for, carried by the caller.
     *
     * Neither server echoes it back — the polling response is a status and, when it is ready, a
     * result. The app already knows what it asked for, and the alternative is either dropping the
     * request from the job or reconstructing it from the answer, which would mean the screen
     * describing the question in the model's words instead of the reader's.
     */
    suspend fun job(jobId: String, request: AiSignalRequest): AiSignalJob
}

class AiSignalEntitlementRequiredException : Exception("AI Signal entitlement required")
class AiSignalQuotaExhaustedException : Exception("AI Signal quota exhausted")
class AiSignalJobExpiredException : Exception("AI Signal job expired")
class AiSignalRequestRejectedException(message: String) : Exception(message)

internal interface AiSignalApi {
    @GET
    suspend fun quota(@Url path: String): AiSignalQuotaDto

    @POST
    suspend fun createJob(@Url path: String, @Body body: AiSignalCreateJobDto): AiSignalJobDto

    @GET
    suspend fun job(@Url path: String): AiSignalJobDto
}

/**
 * The same three calls under each backend's own prefix.
 *
 * CoinePro-FX mounts this router at `/user/ai-signal`; TradeYar serves it under `/ai` inside its
 * mobile prefix. The two are otherwise the same endpoint, which is why one gateway serves both.
 */
internal class AiSignalPaths(private val prefix: String) {
    val quota = "$prefix/quota"
    val generate = "$prefix/generate"
    fun result(jobId: String) = "$prefix/result/$jobId"

    companion object {
        fun of(platform: MarketPlatform): AiSignalPaths = when (platform) {
            MarketPlatform.COINEPRO_FX -> AiSignalPaths("user/ai-signal")
            MarketPlatform.TRADEYAR -> AiSignalPaths("api/mobile/v1/ai")
        }
    }
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

/**
 * The quota, flat.
 *
 * Neither server wraps it. CoinePro-FX also sends `used` rather than `remaining` alongside the
 * limit, so the remainder is worked out here when it is missing rather than treated as absent —
 * a quota of "unknown" would grey out a button that works.
 */
internal data class AiSignalQuotaDto(
    val remaining: Int? = null,
    val used: Int? = null,
    val limit: Int? = null,
    val resetAt: String? = null,
    /** CoinePro-FX lists what may be asked for here; TradeYar does not send them. */
    val symbols: List<String> = emptyList(),
    val timeframes: List<String> = emptyList(),
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

/**
 * The generated signal, in the one flat shape both servers write.
 *
 * Neither sends `targets`, `stop_loss` or `risk_reward_tp1`: it is `sl`, `tp1`, `tp2`, `tp3` and
 * `rr`, and the levels become targets here. `entry_zone` and the indicator block exist on neither
 * and stay null rather than being invented.
 */
internal data class AiGeneratedSignalDto(
    val signalId: Long? = null,
    val symbol: String? = null,
    val direction: String? = null,
    val timeframe: String? = null,
    val entry: Double? = null,
    val entryZone: AiSignalEntryZoneDto? = null,
    @SerializedName(value = "sl", alternate = ["stop_loss", "stopLoss"])
    val stopLoss: Double? = null,
    val tp1: Double? = null,
    val tp2: Double? = null,
    val tp3: Double? = null,
    /**
     * Two different scales.
     *
     * CoinePro-FX writes a whole number out of a hundred; TradeYar's prompt asks the model for a
     * fraction between zero and one. Read raw, a TradeYar confidence of 0.82 becomes "1%" on
     * screen — a strong call rendered as a worthless one. [toDomain] rescales rather than
     * demanding either server change, because both are internally consistent.
     */
    val confidence: Double? = null,
    @SerializedName(value = "rr", alternate = ["risk_reward_tp1", "riskRewardTp1"])
    val riskRewardTp1: Double? = null,
    val rationale: String? = null,
    /** CoinePro-FX only, and its ISO form is what dates the advice. */
    @SerializedName(value = "generated_at", alternate = ["validated_at", "validatedAt"])
    val validatedAt: String? = null,
    /** CoinePro-FX only: whether its own arithmetic check on the levels passed. */
    @SerializedName(value = "valid", alternate = ["validated"])
    val validated: Boolean? = null,
    val lot: Double? = null,
    val strategy: String? = null,
    /** A single string on CoinePro-FX and a list on TradeYar; [warningLines] reads either. */
    val warnings: Any? = null,
    @SerializedName(value = "price_now", alternate = ["priceNow"])
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
    @SerializedName(value = "id", alternate = ["job_id", "jobId"])
    val id: String? = null,
    val status: String? = null,
    /** Present in the create response on both servers, absent when polling. */
    val quota: AiSignalQuotaDto? = null,
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
    private val paths: AiSignalPaths,
) : AiSignalGateway {
    override suspend fun quota(): AiSignalQuota = translate {
        requireNotNull(api.quota(paths.quota).toDomain()) { "Invalid AI Signal quota response" }
    }

    override suspend fun createJob(request: AiSignalRequest): AiSignalJob = translate {
        val safeSymbol = requireNotNull(AiSignalProductScope.normalizeSymbol(request.symbol)) {
            "Unsupported AI Signal symbol"
        }
        val response = api.createJob(
            paths.generate,
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
        requireNotNull(response.toDomain(request, fallbackId = null)) {
            "Invalid AI Signal job response"
        }
    }

    override suspend fun job(jobId: String, request: AiSignalRequest): AiSignalJob = translate {
        require(jobId.isNotBlank()) { "Missing AI Signal job ID" }
        // Neither server repeats the job id when polled, for the same reason as chart analysis:
        // the caller just used it to ask. Requiring it back would fail every poll.
        requireNotNull(api.job(paths.result(jobId)).toDomain(request, fallbackId = jobId)) {
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
        fun create(retrofit: Retrofit, platform: MarketPlatform): NetworkAiSignalGateway =
            NetworkAiSignalGateway(
                api = retrofit.create(AiSignalApi::class.java),
                paths = AiSignalPaths.of(platform),
            )
    }
}

internal fun AiSignalQuotaDto.toDomain(): AiSignalQuota? {
    val safeLimit = limit?.takeIf { it >= 0 } ?: return null
    // CoinePro-FX reports what has been spent, TradeYar what is left. Either answers the question.
    val safeRemaining = remaining?.takeIf { it >= 0 }
        ?: used?.takeIf { it >= 0 }?.let { (safeLimit - it).coerceAtLeast(0) }
        ?: return null
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

internal fun AiSignalJobDto.toDomain(
    safeRequest: AiSignalRequest,
    fallbackId: String?,
): AiSignalJob? {
    val safeId = id?.takeIf { it.isNotBlank() } ?: fallbackId?.takeIf { it.isNotBlank() } ?: return null
    val safeStatus = readStatus(status) ?: return null
    val safeResult = result?.toDomain(safeRequest)

    return AiSignalJob(
        id = safeId,
        status = safeStatus,
        request = safeRequest,
        result = safeResult,
        errorCode = errorCode,
        errorMessage = errorMessage,
        quota = quota?.toDomain(),
        createdAt = createdAt,
        expiresAt = expiresAt,
    )
}

internal fun AiGeneratedSignalDto.toDomain(expected: AiSignalRequest): AiGeneratedSignal? {
    // Only an explicit refusal blocks the result. CoinePro-FX checks its own arithmetic on the
    // levels and says so; TradeYar has no such field, and reading its silence as "not validated"
    // would discard every analysis it ever returns.
    if (validated == false) return null
    val safeSignalId = signalId?.takeIf { it > 0L }
    val safeSymbol = AiSignalProductScope.normalizeSymbol(symbol.orEmpty()) ?: return null
    if (safeSymbol != expected.symbol) return null
    val safeDirection = when (direction?.uppercase()) {
        "BUY" -> SignalDirection.BUY
        "SELL" -> SignalDirection.SELL
        else -> return null
    }
    // TradeYar's result carries no timeframe; the one that was asked for is the one it answers
    // about. A mismatch from CoinePro-FX, which does send it, still refuses the result.
    val safeTimeframe = when {
        timeframe.isNullOrBlank() -> expected.timeframe.wireValue
        timeframe.equals(expected.timeframe.wireValue, ignoreCase = true) -> timeframe
        else -> return null
    }
    val safeEntry = entry?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val safeStop = stopLoss?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val safeConfidence = confidence?.toPercent() ?: return null
    val safeTargets = listOfNotNull(
        tp1?.let { 1 to it },
        tp2?.let { 2 to it },
        tp3?.let { 3 to it },
    ).mapNotNull { (level, price) ->
        price.takeIf { it.isFinite() && it > 0.0 }?.let { AiSignalTarget(level, it) }
    }
    // The first target is what the whole call is built around, so a result without one is not a
    // partial answer but an unusable one. Second and third are genuinely optional on both servers.
    if (safeTargets.none { it.level == 1 }) return null

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
        warnings = warningLines(),
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

/**
 * Rescales a confidence into whole percent.
 *
 * A value at or below one is read as a fraction, which is the form TradeYar's prompt asks the model
 * for; anything above is already a percentage, which is what CoinePro-FX writes. The ambiguous case
 * — exactly 1 — is read as one hundred percent rather than one percent, because no model returns a
 * one-in-a-hundred call and labelling a certain one as worthless is the worse mistake by far.
 */
private fun Double.toPercent(): Int? {
    if (!isFinite() || this < 0.0) return null
    val percent = if (this <= 1.0) this * 100.0 else this
    return percent.toInt().takeIf { it in 0..100 }
}

/**
 * Reads the warnings whichever way they arrived.
 *
 * CoinePro-FX writes one string, TradeYar a list. Typed as [Any] because Gson has no way to declare
 * "either", and a mismatch would otherwise throw inside the parser — where the failure is
 * indistinguishable from the network being down.
 */
private fun AiGeneratedSignalDto.warningLines(): List<String> = when (val raw = warnings) {
    is String -> raw.split('\n', '؛', ';')
    is List<*> -> raw.map { it?.toString().orEmpty() }
    else -> emptyList()
}.mapNotNull { it.trim().takeIf(String::isNotEmpty) }

/**
 * Reads the job status under either server's vocabulary.
 *
 * They name the same three states differently: CoinePro-FX calls a job it has not started
 * `pending` and a failed one `error`, TradeYar `queued` and `failed`. Recognising only one set
 * would leave the other's jobs in a state the screen has no case for, which reads as the analysis
 * never finishing.
 */
private fun readStatus(raw: String?): AiSignalJobStatus? = when (raw?.trim()?.lowercase()) {
    "queued", "pending" -> AiSignalJobStatus.QUEUED
    "running" -> AiSignalJobStatus.RUNNING
    "done" -> AiSignalJobStatus.DONE
    "failed", "error" -> AiSignalJobStatus.FAILED
    "expired" -> AiSignalJobStatus.EXPIRED
    else -> null
}
