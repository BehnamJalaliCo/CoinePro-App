package com.coinepro.core.signals

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.QuoteSource
import com.coinepro.core.model.SignalDirection
import com.google.gson.annotations.SerializedName
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
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
    @GET
    suspend fun listSignals(
        @Url path: String,
        @Query("status") status: String?,
        @Query("limit") limit: Int,
    ): SignalListResponseDto

    @GET
    suspend fun signalDetail(@Url path: String): SignalDetailResponseDto
}

/**
 * Where each backend publishes its signals, which is nothing like the other.
 *
 * TradeYar serves one list under its mobile prefix and filters it with a `status` query.
 * CoinePro-FX has no mobile signal route at all: its list lives under `public`, split into two
 * addresses by status, and its single-signal route is the admin API. So the status becomes a path
 * there and a query here, and [detail] is null where no such endpoint exists.
 */
internal class SignalPaths(private val platform: MarketPlatform) {
    fun list(status: SignalStatusFilter): String = when (platform) {
        MarketPlatform.TRADEYAR -> "api/mobile/v1/signals"
        // `recent` carries both open and closed calls, which is the closest thing CoinePro-FX has
        // to a closed-only list. Filtering it down happens after the read rather than being
        // requested, because asking for a status this address does not understand returns
        // everything — silently, and looking exactly like a filter that worked.
        MarketPlatform.COINEPRO_FX -> when (status) {
            SignalStatusFilter.ACTIVE -> "public/signals/active"
            SignalStatusFilter.RECENT, SignalStatusFilter.CLOSED -> "public/signals/recent"
        }
    }

    /** The `status` query, where the server reads one. */
    fun statusQuery(status: SignalStatusFilter): String? = when (platform) {
        MarketPlatform.TRADEYAR -> status.wireValue
        MarketPlatform.COINEPRO_FX -> null
    }

    fun detail(signalId: Long): String? = when (platform) {
        MarketPlatform.TRADEYAR -> "api/mobile/v1/signals/$signalId"
        MarketPlatform.COINEPRO_FX -> null
    }
}

internal data class SignalListResponseDto(
    val items: List<SignalDto> = emptyList(),
    /** TradeYar reports no total at all; CoinePro-FX calls it `count`. */
    @SerializedName(value = "total", alternate = ["count"])
    val total: Int = 0,
    val serverTimeMs: Long? = null,
    /**
     * TradeYar's honest answer to a reader without a subscription: an empty list and this flag,
     * rather than a 403. Without it an empty list would read as "no signals right now".
     */
    val membershipRequired: Boolean = false,
    /**
     * How this deployment wants the subscription explained, in its own words.
     *
     * Worth carrying rather than replacing with copy of the app's own: how someone gets a
     * subscription differs per platform and changes without the app being rebuilt. TradeYar's says
     * to subscribe through Telegram or the web and sign in with the same account — which is a fact
     * about their account model, not something a client could know.
     */
    val membershipMessage: String? = null,
)

internal data class SignalDetailResponseDto(
    val signal: SignalDto? = null,
    val serverTimeMs: Long? = null,
)

/**
 * One signal, in whichever of the two shapes arrived.
 *
 * TradeYar builds this object to exactly the names below. CoinePro-FX publishes a flatter, older
 * one — `entry_price`, `sl`, `tp1`/`tp2`/`tp3`, `signal_score` — and omits several fields
 * entirely. Naming both spellings here is what lets one reader serve both; the fields CoinePro-FX
 * has no answer for stay null rather than being filled in with something plausible.
 */
internal data class SignalDto(
    val id: Long? = null,
    /** Absent on CoinePro-FX, which serves one market and never says so. */
    val market: String? = null,
    val symbol: String? = null,
    val direction: String? = null,
    val status: String? = null,
    val timeframe: String? = null,
    val strategy: String? = null,
    @SerializedName(value = "confidence", alternate = ["signal_score"])
    val confidence: Int? = null,
    @SerializedName(value = "entry", alternate = ["entry_price"])
    val entry: Double? = null,
    val entryZone: EntryZoneDto? = null,
    @SerializedName(value = "stop_loss", alternate = ["sl"])
    val stopLoss: Double? = null,
    val targets: List<SignalTargetDto> = emptyList(),
    /** CoinePro-FX's three levels, which become [targets] when it sends no list. */
    val tp1: Double? = null,
    val tp2: Double? = null,
    val tp3: Double? = null,
    @SerializedName(value = "risk_reward_tp1", alternate = ["rr"])
    val riskRewardTp1: Double? = null,
    val currentQuote: SignalQuoteDto? = null,
    /** CoinePro-FX's live price, flat and without a timestamp to judge its age by. */
    val currentPrice: Double? = null,
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
internal data class SignalTargetDto(val level: Int = 0, val price: Double? = null, val hit: Boolean? = null)
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
    private val platform: MarketPlatform,
    private val paths: SignalPaths,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : SignalGateway {
    override suspend fun list(
        market: SignalMarketFilter,
        status: SignalStatusFilter,
        limit: Int,
        offset: Int,
    ): SignalPage = translateAccess {
        val response = api.listSignals(
            paths.list(status),
            paths.statusQuery(status),
            limit.coerceIn(1, 100),
        )
        // A subscription the reader does not have is not an empty market. Raised as the same
        // refusal a 403 produces, so one case on screen covers both servers' ways of saying it.
        if (response.membershipRequired) {
            throw SignalMembershipRequiredException(response.membershipMessage?.trim()?.takeIf { it.isNotEmpty() })
        }
        val now = nowMillis()
        val items = response.items
            .mapNotNull { it.toDomain(now, platform) }
            // CoinePro-FX cannot be asked for closed calls; its `recent` list is open and closed
            // together. Narrowing it here is the honest way to answer the question that was asked.
            .filter { status != SignalStatusFilter.CLOSED || !it.status.equals("active", ignoreCase = true) }
        SignalPage(
            items = items,
            total = response.total.takeIf { it > 0 } ?: items.size,
            serverTimeEpochMillis = response.serverTimeMs,
        )
    }

    override suspend fun detail(signalId: Long): TradingSignal = translateAccess {
        require(signalId > 0L) { "Signal ID must be positive" }
        val path = paths.detail(signalId)
            // CoinePro-FX publishes no single-signal route for the app — the one it has is the
            // admin API. Reading it out of the list is the only honest answer available, and it is
            // the recent list rather than the active one so a closed call can still be opened from
            // a notification.
            ?: return@translateAccess requireNotNull(
                list(
                    market = if (platform == MarketPlatform.TRADEYAR) {
                        SignalMarketFilter.CRYPTO
                    } else {
                        SignalMarketFilter.FOREX
                    },
                    status = SignalStatusFilter.RECENT,
                    limit = 100,
                    offset = 0,
                ).items.firstOrNull { it.id == signalId },
            ) { "Invalid signal payload" }
        requireNotNull(api.signalDetail(path).signal?.toDomain(nowMillis(), platform)) {
            "Invalid signal payload"
        }
    }

    private suspend fun <T> translateAccess(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        if (error.code() == 403) throw SignalMembershipRequiredException()
        throw error
    }

    companion object {
        fun create(retrofit: Retrofit, platform: MarketPlatform): NetworkSignalGateway =
            NetworkSignalGateway(
                api = retrofit.create(SignalApi::class.java),
                platform = platform,
                paths = SignalPaths(platform),
            )
    }
}

internal fun SignalDto.toDomain(nowMs: Long, platform: MarketPlatform): TradingSignal? {
    val safeId = id?.takeIf { it > 0L } ?: return null
    val safeSymbol = symbol?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
    // CoinePro-FX never names the market, because it serves exactly one. The platform the request
    // went to is the answer, and it is not a guess: a signal cannot arrive from a server that does
    // not trade it. A market the server does name still has to be one this platform holds.
    val safeMarket = when (market?.lowercase()) {
        "forex" -> MarketType.FOREX
        "crypto" -> MarketType.CRYPTO
        null, "" -> platform.marketType
        else -> return null
    }
    if (safeMarket != platform.marketType) return null
    if (!isProductSignalSymbol(safeMarket, safeSymbol)) return null
    val safeDirection = when (direction?.uppercase()) {
        "BUY" -> SignalDirection.BUY
        "SELL" -> SignalDirection.SELL
        else -> return null
    }
    // CoinePro-FX sends a bare price with no timestamp to judge its age by, so it is carried as a
    // quote of unknown freshness rather than being dressed up as a live one.
    val quote = currentQuote?.toDomain(safeMarket, nowMs)
        ?: currentPrice?.takeIf { it.isFinite() && it > 0.0 }?.let {
            SignalQuoteDto(price = it).toDomain(safeMarket, nowMs)
        }
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
        // Whichever way the levels arrived. CoinePro-FX sends three fields and no list.
        targets = targets.takeIf { it.isNotEmpty() }?.map { SignalTarget(it.level, it.price, it.hit) }
            ?: listOfNotNull(
                tp1?.let { SignalTarget(1, it, null) },
                tp2?.let { SignalTarget(2, it, null) },
                tp3?.let { SignalTarget(3, it, null) },
            ),
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

internal fun isProductSignalSymbol(market: MarketType, symbol: String): Boolean {
    val normalized = symbol.trim().uppercase()
    return when (market) {
        MarketType.FOREX -> normalized == "XAUUSD" || normalized == "XAGUSD"
        MarketType.CRYPTO -> normalized.endsWith("USDT") && normalized.length > 4
    }
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
