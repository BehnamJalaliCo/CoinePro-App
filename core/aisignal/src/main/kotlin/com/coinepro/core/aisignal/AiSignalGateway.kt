package com.coinepro.core.aisignal

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.SignalDirection
import com.coinepro.core.network.ApiErrors
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

/**
 * The four refusals this endpoint family has a status code for.
 *
 * Each carries the server's own reader-facing sentence and machine code where the body had them,
 * rather than an authored English message. The message these used to carry — "AI Signal request was
 * rejected by server validation" — was going straight onto a Persian reader's screen, because the
 * controller could not tell an authored string from a server one and rendered both verbatim.
 *
 * [serverMessage] is null unless the server wrote something a reader can act on; `ApiErrors` has
 * already separated that from FastAPI's English defaults. Null is the honest answer, and the screen
 * has its own Persian sentence for it.
 */
sealed class AiSignalException(
    val serverMessage: String?,
    val serverCode: String?,
) : Exception(serverCode ?: "ai_signal_error")

class AiSignalEntitlementRequiredException(
    serverMessage: String? = null,
    serverCode: String? = null,
) : AiSignalException(serverMessage, serverCode)

class AiSignalQuotaExhaustedException(
    serverMessage: String? = null,
    serverCode: String? = null,
    /** From the body's `retry_after`, in seconds, where the server sent one. */
    val retryAfterSeconds: Int? = null,
) : AiSignalException(serverMessage, serverCode)

class AiSignalJobExpiredException(
    serverMessage: String? = null,
    serverCode: String? = null,
) : AiSignalException(serverMessage, serverCode)

class AiSignalRequestRejectedException(
    serverMessage: String? = null,
    serverCode: String? = null,
    /** The request field the server blamed, when it named one. */
    val field: String? = null,
) : AiSignalException(serverMessage, serverCode)

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

/**
 * The generate request, spelled the way both contracts spell it.
 *
 * ### The bug this class was
 *
 * Pressing «ساخت ستاپ» answered `422` every single time, and the screen showed the reader the
 * English exception text. Two causes, both here:
 *
 * * **`risk` was always sent, and neither contract has the field.** TradeYar's part 11 lists
 *   `symbol, timeframe, trade_style?, risk_appetite?, direction_bias?, min_rr?, risk_percent?,
 *   balance?`; CoinePro-FX's takes the same set plus `lot`. An always-present field a strict
 *   pydantic model was never told about is exactly an unprocessable-entity, and `risk` is a
 *   vestige of the Phase 7 contract that `risk_appetite` replaced.
 * * **`riskPct` serialised as `risk_pct`.** Both contracts say `risk_percent`.
 *
 * ### Why the names are written out rather than left to the naming policy
 *
 * `NetworkFactory` configures Gson with `LOWER_CASE_WITH_UNDERSCORES`, so `tradeStyle` did already
 * reach the wire as `trade_style`. That is convenient and it is also invisible: the field that was
 * wrong, `riskPct`, was wrong in a way no reader of this file could see, because the file never
 * says what any of these are called. Naming them here makes the contract legible at the point it is
 * declared and makes `AiSignalWireTest` able to pin it — the alternative is a JSON key that changes
 * when somebody renames a Kotlin property.
 *
 * Gson's `alternate` is read-only: it is consulted when parsing and ignored when writing, so it
 * cannot make one request satisfy two spellings. Where the two backends genuinely disagreed there
 * would be nothing for it but a per-platform body; they do not disagree, and this is the set both
 * documented.
 */
internal data class AiSignalCreateJobDto(
    @SerializedName("symbol")
    val symbol: String,
    @SerializedName("timeframe")
    val timeframe: String,
    // Null means the user left the control alone. Gson omits nulls by default, so an untouched
    // control never reaches the model as a value it would then act on.
    @SerializedName("trade_style")
    val tradeStyle: String? = null,
    @SerializedName("risk_appetite")
    val riskAppetite: String? = null,
    @SerializedName("direction_bias")
    val directionBias: String? = null,
    @SerializedName("min_rr")
    val minRr: Double? = null,
    /** CoinePro-FX only. TradeYar's contract has no lot, and ignores one rather than refusing it. */
    @SerializedName("lot")
    val lot: Double? = null,
    @SerializedName("risk_percent")
    val riskPercent: Double? = null,
    @SerializedName("balance")
    val balance: Double? = null,
)

/**
 * The quota, flat.
 *
 * Neither server wraps it. CoinePro-FX also sends `used` rather than `remaining` alongside the
 * limit, so the remainder is worked out here when it is missing rather than treated as absent —
 * a quota of "unknown" would grey out a button that works.
 *
 * Only ever parsed, never written, so the alternates cost nothing and each buys a real spelling one
 * of the two backends has been seen to use: CoinePro-FX's assistant endpoint calls the ceiling
 * `quota` rather than `limit`, and its job envelope writes the refill as `reset_at` while the panel
 * routes it grew out of write `resets_at`.
 */
internal data class AiSignalQuotaDto(
    val remaining: Int? = null,
    val used: Int? = null,
    @SerializedName(value = "limit", alternate = ["daily_limit", "quota"])
    val limit: Int? = null,
    @SerializedName(value = "reset_at", alternate = ["resets_at", "reset"])
    val resetAt: String? = null,
    /**
     * What the server says it will accept.
     *
     * CoinePro-FX lists both here; TradeYar sends neither. **Where a server states its own list,
     * that list wins** over anything the client believes — a picker offering something the server
     * will refuse is the 422 this whole change exists to stop.
     */
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
    /** Bar open time. TradeYar sends milliseconds; CoinePro-FX omits it entirely. */
    val t: Long? = null,
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
    @SerializedName(value = "price_now", alternate = ["priceNow", "current_price", "price"])
    val priceNow: Double? = null,
    /**
     * TradeYar nests the indicator block; CoinePro-FX writes the same readings flat beside the
     * levels. Both are read, and [mergedSnapshot] prefers the flat one where a server sent both.
     */
    val snapshot: AiSnapshotDto? = null,
    @SerializedName(value = "ema20", alternate = ["ema_20"])
    val ema20: Double? = null,
    @SerializedName(value = "ema50", alternate = ["ema_50"])
    val ema50: Double? = null,
    @SerializedName(value = "ema200", alternate = ["ema_200"])
    val ema200: Double? = null,
    @SerializedName(value = "rsi14", alternate = ["rsi_14", "rsi"])
    val rsi14: Double? = null,
    @SerializedName(value = "atr14", alternate = ["atr_14", "atr"])
    val atr14: Double? = null,
    val macd: Double? = null,
    @SerializedName(value = "bb_upper", alternate = ["bollinger_upper", "bbUpper"])
    val bbUpper: Double? = null,
    @SerializedName(value = "bb_lower", alternate = ["bollinger_lower", "bbLower"])
    val bbLower: Double? = null,
    @SerializedName(value = "swing_high_20", alternate = ["swing_high20", "swing_high", "swingHigh20"])
    val swingHigh20: Double? = null,
    @SerializedName(value = "swing_low_20", alternate = ["swing_low20", "swing_low", "swingLow20"])
    val swingLow20: Double? = null,
    @SerializedName(value = "change_pct_20", alternate = ["change_pct20", "change_pct", "change_percent_20"])
    val changePct20: Double? = null,
    /**
     * TradeYar's part 11 calls this `candles`; CoinePro-FX's evidence block is `recent_candles`.
     * Read under both names, because this is the series the levels are drawn across and a chart
     * that silently never appears is indistinguishable from a server that sent no evidence.
     */
    @SerializedName(value = "recent_candles", alternate = ["candles"])
    val recentCandles: List<AiCandleDto> = emptyList(),
) {
    /** The flat reading where there is one, else the nested one. Neither invents a zero. */
    val mergedSnapshot: AiTechnicalSnapshot
        get() = AiTechnicalSnapshot(
            ema20 = ema20.finiteOrNull() ?: snapshot?.ema20.finiteOrNull(),
            ema50 = ema50.finiteOrNull() ?: snapshot?.ema50.finiteOrNull(),
            ema200 = ema200.finiteOrNull() ?: snapshot?.ema200.finiteOrNull(),
            rsi14 = rsi14.finiteOrNull() ?: snapshot?.rsi14.finiteOrNull(),
            atr14 = atr14.finiteOrNull() ?: snapshot?.atr14.finiteOrNull(),
            macd = macd.finiteOrNull() ?: snapshot?.macd.finiteOrNull(),
            bollingerUpper = bbUpper.finiteOrNull() ?: snapshot?.bbUpper.finiteOrNull(),
            bollingerLower = bbLower.finiteOrNull() ?: snapshot?.bbLower.finiteOrNull(),
            swingHigh20 = swingHigh20.finiteOrNull() ?: snapshot?.swingHigh20.finiteOrNull(),
            swingLow20 = swingLow20.finiteOrNull() ?: snapshot?.swingLow20.finiteOrNull(),
            changePercent20 = changePct20.finiteOrNull() ?: snapshot?.changePct20.finiteOrNull(),
            priceNow = priceNow.finiteOrNull() ?: snapshot?.priceNow.finiteOrNull(),
        )
}

/**
 * The nested indicator block, as TradeYar's part 11 sends it.
 *
 * Its documented six are `rsi_14`, `atr_14`, `macd`, `ema_20`, `ema_50`, `ema_200`. The other five
 * are here because CoinePro-FX computes them and a server that later nests what it currently sends
 * flat should not silently lose them — a snapshot is the whole reason the verdict is checkable.
 */
internal data class AiSnapshotDto(
    @SerializedName(value = "ema_20", alternate = ["ema20"])
    val ema20: Double? = null,
    @SerializedName(value = "ema_50", alternate = ["ema50"])
    val ema50: Double? = null,
    @SerializedName(value = "ema_200", alternate = ["ema200"])
    val ema200: Double? = null,
    @SerializedName(value = "rsi_14", alternate = ["rsi14", "rsi"])
    val rsi14: Double? = null,
    @SerializedName(value = "atr_14", alternate = ["atr14", "atr"])
    val atr14: Double? = null,
    val macd: Double? = null,
    @SerializedName(value = "bb_upper", alternate = ["bollinger_upper"])
    val bbUpper: Double? = null,
    @SerializedName(value = "bb_lower", alternate = ["bollinger_lower"])
    val bbLower: Double? = null,
    @SerializedName(value = "swing_high_20", alternate = ["swing_high20", "swing_high"])
    val swingHigh20: Double? = null,
    @SerializedName(value = "swing_low_20", alternate = ["swing_low20", "swing_low"])
    val swingLow20: Double? = null,
    @SerializedName(value = "change_pct_20", alternate = ["change_pct20", "change_pct"])
    val changePct20: Double? = null,
    @SerializedName(value = "price_now", alternate = ["current_price", "price"])
    val priceNow: Double? = null,
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
        val response = api.createJob(paths.generate, request.toWire(safeSymbol))
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

    /**
     * Turns a status code into a refusal the screen can explain, carrying the server's own words.
     *
     * The body is read once, here, rather than left on the exception for a controller to dig out.
     * `ApiErrors` knows all four envelope shapes the two backends use and, crucially, tells a
     * Persian sentence written for a reader apart from pydantic's English `"Field required"` — so
     * what reaches [AiSignalException.serverMessage] is either something worth showing verbatim or
     * nothing at all.
     */
    private suspend fun <T> translate(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        val body = ApiErrors.from(error)
        when (error.code()) {
            403 -> throw AiSignalEntitlementRequiredException(body.message, body.code)
            410 -> throw AiSignalJobExpiredException(body.message, body.code)
            422, 400 -> throw AiSignalRequestRejectedException(body.message, body.code, body.field)
            429 -> throw AiSignalQuotaExhaustedException(body.message, body.code, body.retryAfterSeconds)
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

/**
 * The request as it goes on the wire.
 *
 * Separate from [NetworkAiSignalGateway.createJob] so that `AiSignalWireTest` can serialise exactly
 * what the app would send, through exactly the Gson `NetworkFactory` configures, and assert the key
 * names. The bug this replaces was invisible to every test in the module because no test ever
 * looked at the JSON — they all went from domain object to domain object.
 *
 * Zero and negative are dropped rather than sent: a lot of zero is not a smaller position, it is a
 * field the reader left alone, and a balance of zero would have the model size every trade at
 * nothing. A balance is allowed to be any positive figure; the rest must be positive to mean
 * anything at all.
 */
internal fun AiSignalRequest.toWire(safeSymbol: String): AiSignalCreateJobDto = AiSignalCreateJobDto(
    symbol = safeSymbol,
    timeframe = timeframe.wireValue,
    tradeStyle = tradeStyle?.wireValue,
    riskAppetite = riskAppetite?.wireValue,
    directionBias = directionBias?.wireValue,
    minRr = minRiskReward?.takeIf { it.isFinite() && it > 0.0 },
    lot = lot?.takeIf { it.isFinite() && it > 0.0 },
    riskPercent = riskPercent?.takeIf { it.isFinite() && it > 0.0 },
    balance = balance?.takeIf { it.isFinite() && it > 0.0 },
)

internal fun AiSignalQuotaDto.toDomain(): AiSignalQuota? {
    val safeLimit = limit?.takeIf { it >= 0 } ?: return null
    // CoinePro-FX reports what has been spent, TradeYar what is left. Either answers the question.
    val safeRemaining = remaining?.takeIf { it >= 0 }
        ?: used?.takeIf { it >= 0 }?.let { (safeLimit - it).coerceAtLeast(0) }
        ?: return null
    // A bar length this build cannot resolve is kept as text rather than dropped, so the screen can
    // say that the server offers one it does not — silence there reads as the app being complete.
    val known = timeframes.mapNotNull(AiSignalTimeframe::ofWire).distinct()
    val unknown = timeframes
        .filter { AiSignalTimeframe.ofWire(it) == null }
        .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .distinct()
    return AiSignalQuota(
        remaining = safeRemaining.coerceAtMost(safeLimit),
        limit = safeLimit,
        resetAt = resetAt?.trim()?.takeIf(String::isNotEmpty),
        symbols = symbols.mapNotNull(AiSignalProductScope::normalizeSymbol).distinct(),
        timeframes = known,
        unknownTimeframes = unknown,
    )
}

/**
 * A request echoed back by a server, where one ever is.
 *
 * [AiSignalRequestDto.risk] is read but no longer required: it is not part of either live contract,
 * and refusing an echo that omits it would discard a perfectly good job description over a field the
 * app has stopped sending.
 */
internal fun AiSignalRequestDto.toDomain(): AiSignalRequest? {
    val safeSymbol = AiSignalProductScope.normalizeSymbol(symbol.orEmpty()) ?: return null
    val safeTimeframe = AiSignalTimeframe.ofWire(timeframe) ?: return null
    val safeRisk = AiSignalRisk.entries.firstOrNull {
        it.wireValue.equals(risk, ignoreCase = true)
    } ?: AiSignalRisk.MEDIUM
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
    val snapshot = mergedSnapshot
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
    return AiCandle(open = open, high = high, low = low, close = close, time = t.toEpochSeconds())
}

/**
 * Milliseconds or seconds, both to seconds. Absent stays absent.
 *
 * The two servers disagree and neither says which it is sending, so the magnitude decides: anything
 * past the year 33658 in seconds is milliseconds, and no other reading of a market timestamp is
 * plausible. A wrong guess here does not draw a slightly wrong axis — it puts every bar in the year
 * 55000 and the chart draws one pixel wide.
 */
private fun Long?.toEpochSeconds(): Long? {
    val value = this?.takeIf { it > 0L } ?: return null
    return if (value > 1_000_000_000_000L) value / 1_000L else value
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
