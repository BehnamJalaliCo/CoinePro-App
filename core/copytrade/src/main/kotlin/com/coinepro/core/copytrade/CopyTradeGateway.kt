package com.coinepro.core.copytrade

import com.coinepro.core.common.parseWireInstant
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.network.ApiErrors
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Url
import java.time.Instant

interface CopyTradeGateway {
    suspend fun status(): CopyTradeStatus

    /**
     * Turns copying on or off and returns the settings as stored.
     *
     * Only this one field is written. Sending it alone is deliberate and matches the server's
     * contract, where an omitted field means "leave it": a client that echoed the whole settings
     * object back would overwrite risk parameters it had read minutes earlier with whatever it
     * happened to be holding.
     */
    suspend fun setEnabled(enabled: Boolean): CopyPreferences

    suspend fun linkAccount(broker: String, server: String, login: String, password: String)

    suspend fun unlinkAccount()
}

/**
 * The platform has no copy-trading surface.
 *
 * TradeYar is the case: it executes orders per signal, which is a different model with its own
 * screens, so this is absence rather than failure.
 */
class CopyTradeUnsupportedException : Exception("Copy trading is not available on this platform")

/**
 * The server refused for want of a subscription, and said so in a way the app can act on.
 *
 * [serverMessage] is its own wording where it sent any, and is shown instead of anything local —
 * only the server knows what the reader's account is missing and what would fix it.
 */
class CopyTradeMembershipRequiredException(val serverMessage: String? = null) :
    Exception(serverMessage ?: "This account does not hold a subscription")

/**
 * Where copy trading lives, which is only CoinePro-FX.
 *
 * Every path sits under `user/` with the rest of that server's panel surface rather than under
 * `user/mobile/`: these routes predate the mobile app and are the same ones its web panel calls, so
 * there is one implementation and one truth about what the copy is doing.
 */
internal class CopyTradePaths private constructor() {
    val status = "user/copy-status"
    val config = "user/copy-config"
    val link = "user/account/link"
    val account = "user/account"

    companion object {
        fun of(platform: MarketPlatform): CopyTradePaths? = when (platform) {
            MarketPlatform.COINEPRO_FX -> CopyTradePaths()
            MarketPlatform.TRADEYAR -> null
        }
    }
}

internal interface CopyTradeApi {
    @GET
    suspend fun status(@Url path: String): CopyStatusDto

    @POST
    suspend fun setConfig(@Url path: String, @Body body: CopyConfigPatchDto): CopyConfigDto

    @POST
    suspend fun link(@Url path: String, @Body body: LinkAccountDto)

    @HTTP(method = "DELETE")
    suspend fun unlink(@Url path: String)
}

internal data class LinkAccountDto(
    val broker: String,
    val server: String,
    val login: String,
    val password: String,
)

/** Only [enabled] is ever sent; the server reads an absent field as "no change". */
internal data class CopyConfigPatchDto(val enabled: Boolean)

internal data class CopyStatusDto(
    val account: CopyAccountDto? = null,
    val copy: CopyConfigDto? = null,
    val master: CopyBookDto? = null,
    val mirrored: List<CopyPositionDto> = emptyList(),
    val mode: String? = null,
    val accountMismatch: Boolean = false,
    val liveAccount: String? = null,
    val execEvents: List<CopyEventDto> = emptyList(),
    val slotState: CopySlotDto? = null,
)

internal data class CopyAccountDto(
    val broker: String? = null,
    val server: String? = null,
    val loginMasked: String? = null,
    val status: String? = null,
    val lastError: String? = null,
    val alive: Boolean = false,
    val balance: Double? = null,
    val equity: Double? = null,
    val marginLevel: Double? = null,
    val floatingPnl: Double? = null,
    val openCount: Int = 0,
    val currency: String? = null,
    val lastSeen: String? = null,
)

internal data class CopyConfigDto(
    val enabled: Boolean = false,
    val riskMode: String? = null,
    val riskValue: Double? = null,
    val maxLot: Double? = null,
    val maxOpenTrades: Int? = null,
    val copySlTp: Boolean = true,
    val maxDailyLossPct: Double? = null,
    val symbols: List<String> = emptyList(),
)

internal data class CopyBookDto(
    val open: Int = 0,
    val positions: List<CopyPositionDto> = emptyList(),
)

internal data class CopyPositionDto(
    val symbol: String? = null,
    val direction: String? = null,
    val lots: Double = 0.0,
    val profit: Double = 0.0,
    val sl: Double? = null,
    val signalId: Long? = null,
)

internal data class CopyEventDto(
    /** Unix seconds, not an ISO string — this queue is written by the terminal bridge. */
    val ts: Long? = null,
    val signalId: Long? = null,
    val code: String? = null,
    val outcome: String? = null,
    val symbol: String? = null,
    val message: String? = null,
)

internal data class CopySlotDto(
    val state: String? = null,
    val message: String? = null,
    val ts: Long? = null,
)

class NetworkCopyTradeGateway private constructor(
    private val api: CopyTradeApi,
    private val paths: CopyTradePaths?,
) : CopyTradeGateway {

    private fun paths(): CopyTradePaths = paths ?: throw CopyTradeUnsupportedException()

    override suspend fun status(): CopyTradeStatus = translate {
        api.status(paths().status).toDomain()
    }

    override suspend fun setEnabled(enabled: Boolean): CopyPreferences = translate {
        api.setConfig(paths().config, CopyConfigPatchDto(enabled)).toDomain()
    }

    override suspend fun linkAccount(broker: String, server: String, login: String, password: String) = translate {
        require(broker.isNotBlank()) { "Missing broker" }
        require(server.isNotBlank()) { "Missing server" }
        require(login.isNotBlank()) { "Missing login" }
        require(password.isNotBlank()) { "Missing password" }
        api.link(paths().link, LinkAccountDto(broker, server, login, password))
    }

    override suspend fun unlinkAccount() = translate { api.unlink(paths().account) }

    /**
     * Separates the one 403 that has an answer from the ones that do not.
     *
     * A missing subscription and an expired token both arrive as 403, and the session layer has to
     * see the second one to renew the token. So only a refusal the server tagged
     * `membership_required` is claimed here; everything else is rethrown untouched.
     */
    private suspend fun <T> translate(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        if (error.code() == 403) {
            val refusal = ApiErrors.from(error)
            if (refusal.code == MEMBERSHIP_REQUIRED) {
                throw CopyTradeMembershipRequiredException(
                    refusal.message?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
        }
        throw error
    }

    companion object {
        private const val MEMBERSHIP_REQUIRED = "membership_required"

        fun create(retrofit: Retrofit, platform: MarketPlatform): CopyTradeGateway =
            NetworkCopyTradeGateway(
                api = retrofit.create(CopyTradeApi::class.java),
                paths = CopyTradePaths.of(platform),
            )
    }
}

internal fun CopyStatusDto.toDomain() = CopyTradeStatus(
    account = account?.toDomain(),
    preferences = copy?.toDomain() ?: CopyPreferences(),
    master = master?.toDomain() ?: CopyBook(),
    mirrored = mirrored.mapNotNull(CopyPositionDto::toDomain),
    mode = mode?.trim()?.takeIf(String::isNotEmpty),
    accountMismatch = accountMismatch,
    liveAccount = liveAccount?.trim()?.takeIf(String::isNotEmpty),
    events = execEvents.mapNotNull(CopyEventDto::toDomain),
    slotState = slotState?.toDomain(),
)

internal fun CopyAccountDto.toDomain() = CopyAccount(
    broker = broker?.trim()?.takeIf(String::isNotEmpty),
    server = server?.trim()?.takeIf(String::isNotEmpty),
    loginMasked = loginMasked?.trim()?.takeIf(String::isNotEmpty),
    status = status?.trim()?.takeIf(String::isNotEmpty),
    lastError = lastError?.trim()?.takeIf(String::isNotEmpty),
    alive = alive,
    balance = balance,
    equity = equity,
    marginLevel = marginLevel,
    floatingPnl = floatingPnl,
    openCount = openCount.coerceAtLeast(0),
    currency = currency?.trim()?.takeIf(String::isNotEmpty),
    lastSeen = parseWireInstant(lastSeen),
)

internal fun CopyConfigDto.toDomain() = CopyPreferences(
    enabled = enabled,
    riskMode = riskMode?.trim()?.takeIf(String::isNotEmpty),
    riskValue = riskValue,
    maxLot = maxLot,
    maxOpenTrades = maxOpenTrades,
    copyStopAndTargets = copySlTp,
    maxDailyLossPercent = maxDailyLossPct,
    symbols = symbols.mapNotNull { it.trim().takeIf(String::isNotEmpty) },
)

internal fun CopyBookDto.toDomain() = CopyBook(
    open = open.coerceAtLeast(0),
    positions = positions.mapNotNull(CopyPositionDto::toDomain),
)

internal fun CopyPositionDto.toDomain(): CopyPosition? {
    val safeSymbol = symbol?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return CopyPosition(
        symbol = safeSymbol,
        direction = direction?.trim().orEmpty(),
        lots = lots,
        profit = profit,
        // The bridge writes a literal zero for "no stop", which is not the same claim as a stop at
        // zero. Reading it as a price would print a stop loss of 0.00 beside a live position.
        stopLoss = sl?.takeIf { it > 0.0 },
        signalId = signalId?.takeIf { it > 0L },
    )
}

internal fun CopyEventDto.toDomain(): CopyExecutionEvent? {
    val safeCode = code?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val safeMessage = message?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return CopyExecutionEvent(
        at = ts?.let(::instantOfSeconds),
        signalId = signalId?.takeIf { it > 0L },
        code = safeCode,
        outcome = outcome?.trim()?.takeIf(String::isNotEmpty),
        symbol = symbol?.trim()?.takeIf(String::isNotEmpty),
        message = safeMessage,
    )
}

internal fun CopySlotDto.toDomain(): CopySlotState? {
    val safeState = state?.trim()?.takeIf(String::isNotEmpty) ?: return null
    return CopySlotState(
        state = safeState,
        message = message?.trim()?.takeIf(String::isNotEmpty),
        at = ts?.let(::instantOfSeconds),
    )
}

/** Guarded because a bridge that sends milliseconds by mistake would otherwise throw on the year. */
private fun instantOfSeconds(seconds: Long): Instant? =
    runCatching { Instant.ofEpochSecond(seconds) }.getOrNull()?.takeIf { seconds > 0L }
