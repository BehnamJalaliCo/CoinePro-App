package com.coinepro.core.execution

import com.coinepro.core.model.MarketPlatform
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.POST
import retrofit2.http.Url
import retrofit2.http.Query

interface ExecutionGateway {
    suspend fun connections(): Pair<VenueConnection?, VenueConnection?>
    suspend fun connectMt5(broker: String, server: String, login: String, password: String)
    suspend fun disconnectMt5()
    suspend fun connectLbank(apiKey: String, apiSecret: String, permission: LbankPermission)
    suspend fun disconnectLbank()
    suspend fun executeSignal(
        signalId: Long,
        venue: ExecutionVenue,
        quantity: Double,
        clientRequestId: String,
    ): SignalExecution
    suspend fun executions(limit: Int = 50): List<SignalExecution>
    suspend fun execution(executionId: String): SignalExecution
    suspend fun requestClose(executionId: String): SignalExecution
}

/**
 * This deployment has no order-execution surface at all.
 *
 * Distinct from a failure: nothing went wrong and retrying will not help. The screen shows the
 * feature as absent rather than as broken, which is the difference between "not here" and "we lost
 * your order".
 */
class ExecutionUnsupportedException : Exception("Order execution is not available on this platform")

class ExecutionRateLimitedException : Exception(
    "Execution request was rate limited. No automatic retry was sent.",
)

class ExecutionRequestRejectedException : Exception(
    "Execution request was rejected by server validation.",
)

internal interface ExecutionApi {
    @GET
    suspend fun venue(@Url path: String): ConnectionDto

    @POST
    suspend fun connectLbank(@Url path: String, @Body body: LbankConnectionDto): ConnectionDto

    @HTTP(method = "DELETE")
    suspend fun disconnectLbank(@Url path: String): ConnectionDto

    @POST
    suspend fun executeSignal(@Url path: String, @Body body: ExecuteSignalDto): ExecutionResponseDto

    @GET
    suspend fun executions(@Url path: String, @Query("limit") limit: Int): ExecutionListResponseDto

    @GET
    suspend fun execution(@Url path: String): ExecutionResponseDto

    @POST
    suspend fun requestClose(@Url path: String): ExecutionResponseDto
}

/**
 * Where order execution lives, which is only one of the two platforms.
 *
 * TradeYar places orders on LBank on the reader's behalf and serves the whole surface below.
 * CoinePro-FX has no equivalent: its routes for this were never built, and its own model is copy
 * trading — the reader links a broker account and a service trades it, rather than sending an
 * order per signal. So this returns null there, and the gateway says the feature is absent instead
 * of posting to addresses that answer 404 in wording that reads like an outage.
 */
internal class ExecutionPaths(private val prefix: String) {
    val venue = "$prefix/venues/lbank"
    val executions = "$prefix/executions"
    fun execution(executionId: String) = "$prefix/executions/$executionId"
    fun close(executionId: String) = "$prefix/executions/$executionId/close"

    companion object {
        fun of(platform: MarketPlatform): ExecutionPaths? = when (platform) {
            MarketPlatform.TRADEYAR -> ExecutionPaths("api/mobile/v1")
            MarketPlatform.COINEPRO_FX -> null
        }
    }
}

internal data class Mt5ConnectionDto(
    val broker: String,
    val server: String,
    val login: String,
    val password: String,
)

internal data class LbankConnectionDto(
    val apiKey: String,
    val apiSecret: String,
    val permission: String,
)

internal data class ExecuteSignalDto(
    val signalId: Long,
    val venue: String,
    val quantity: Double,
    val clientRequestId: String,
    val confirmed: Boolean = true,
)

internal data class ConnectionDto(
    val configured: Boolean = false,
    val connected: Boolean = false,
    val status: String? = null,
    val broker: String? = null,
    val server: String? = null,
    val loginMasked: String? = null,
    val permission: String? = null,
    val keyHint: String? = null,
)

internal data class ConnectionsResponseDto(
    val mt5: ConnectionDto? = null,
    val lbank: ConnectionDto? = null,
)

internal data class ExecutionSignalDto(
    val signalId: Long? = null,
    val symbol: String? = null,
    val direction: String? = null,
    val timeframe: String? = null,
    val entry: Double? = null,
    val stopLoss: Double? = null,
    val tp1: Double? = null,
    val tp2: Double? = null,
    val tp3: Double? = null,
)

internal data class ExecutionDto(
    val id: String? = null,
    val signalId: Long? = null,
    val venue: String? = null,
    val product: String? = null,
    val status: String? = null,
    val side: String? = null,
    val quantity: String? = null,
    val providerOrderId: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val signal: ExecutionSignalDto? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val closedAt: String? = null,
)

internal data class ExecutionResponseDto(val execution: ExecutionDto? = null)
internal data class ExecutionListResponseDto(val items: List<ExecutionDto> = emptyList())

class NetworkExecutionGateway private constructor(
    private val api: ExecutionApi,
    private val paths: ExecutionPaths?,
) : ExecutionGateway {
    private fun paths(): ExecutionPaths = paths ?: throw ExecutionUnsupportedException()

    override suspend fun connections(): Pair<VenueConnection?, VenueConnection?> = translate {
        // The venue read is a single object, not a pair. Only one venue exists on the platform
        // that has this surface at all, so MT5 is reported absent rather than guessed at.
        null to api.venue(paths().venue).toDomain(ExecutionVenue.LBANK)
    }

    /**
     * MetaTrader is not reachable from here on either platform.
     *
     * CoinePro-FX links a broker account through its copy-trading configuration, which is a
     * different surface and a different model; TradeYar trades crypto and has no MT5 at all. A
     * method that quietly did nothing would leave the reader believing an account was linked.
     */
    override suspend fun connectMt5(broker: String, server: String, login: String, password: String) {
        throw ExecutionUnsupportedException()
    }

    override suspend fun disconnectMt5() {
        throw ExecutionUnsupportedException()
    }

    override suspend fun connectLbank(apiKey: String, apiSecret: String, permission: LbankPermission) = translate {
        api.connectLbank(paths().venue, LbankConnectionDto(apiKey, apiSecret, permission.wireValue))
        Unit
    }

    override suspend fun disconnectLbank() = translate {
        api.disconnectLbank(paths().venue)
        Unit
    }

    override suspend fun executeSignal(
        signalId: Long,
        venue: ExecutionVenue,
        quantity: Double,
        clientRequestId: String,
    ): SignalExecution = translate {
        require(signalId > 0L) { "Signal ID must be positive" }
        require(quantity.isFinite() && quantity > 0.0) { "Execution quantity must be positive and finite" }
        require(clientRequestId.isNotBlank()) { "Missing idempotency request ID" }
        requireNotNull(
            api.executeSignal(
                paths().executions,
                // The signal id travels in the body. It used to be a path segment, which is the
                // kind of difference that answers 404 rather than failing in a way anyone reads.
                ExecuteSignalDto(
                    signalId = signalId,
                    venue = venue.wireValue,
                    quantity = quantity,
                    clientRequestId = clientRequestId,
                ),
            ).execution?.toDomain(),
        ) { "Invalid execution response" }
    }

    override suspend fun executions(limit: Int): List<SignalExecution> = translate {
        api.executions(paths().executions, limit.coerceIn(1, 100)).items.mapNotNull { it.toDomain() }
    }

    override suspend fun execution(executionId: String): SignalExecution = translate {
        require(executionId.isNotBlank()) { "Missing execution ID" }
        requireNotNull(api.execution(paths().execution(executionId)).execution?.toDomain()) {
            "Invalid execution response"
        }
    }

    override suspend fun requestClose(executionId: String): SignalExecution = translate {
        require(executionId.isNotBlank()) { "Missing execution ID" }
        requireNotNull(api.requestClose(paths().close(executionId)).execution?.toDomain()) {
            "Invalid execution response"
        }
    }

    private suspend fun <T> translate(block: suspend () -> T): T = try {
        block()
    } catch (error: HttpException) {
        when (error.code()) {
            422 -> throw ExecutionRequestRejectedException()
            429 -> throw ExecutionRateLimitedException()
            else -> throw error
        }
    }

    companion object {
        fun create(retrofit: Retrofit, platform: MarketPlatform): NetworkExecutionGateway =
            NetworkExecutionGateway(
                api = retrofit.create(ExecutionApi::class.java),
                paths = ExecutionPaths.of(platform),
            )
    }
}

internal fun ConnectionDto.toDomain(venue: ExecutionVenue) = VenueConnection(
    venue = venue,
    configured = configured,
    connected = connected,
    status = status.orEmpty(),
    broker = broker,
    server = server,
    loginMasked = loginMasked,
    lbankPermission = LbankPermission.entries.firstOrNull { it.wireValue == permission },
    keyHint = keyHint,
)

internal fun ExecutionDto.toDomain(): SignalExecution? {
    val safeId = id?.takeIf { it.isNotBlank() } ?: return null
    val safeSignalId = signalId?.takeIf { it > 0L } ?: return null
    val safeVenue = ExecutionVenue.entries.firstOrNull { it.wireValue == venue } ?: return null
    val safeStatus = ExecutionStatus.entries.firstOrNull { it.wireValue == status } ?: ExecutionStatus.UNKNOWN
    return SignalExecution(
        id = safeId,
        signalId = safeSignalId,
        venue = safeVenue,
        product = product.orEmpty(),
        status = safeStatus,
        side = side.orEmpty(),
        quantity = quantity.orEmpty(),
        providerOrderId = providerOrderId,
        errorCode = errorCode,
        errorMessage = errorMessage,
        signal = signal?.toDomain(),
        createdAt = createdAt,
        updatedAt = updatedAt,
        closedAt = closedAt,
    )
}

private fun ExecutionSignalDto.toDomain(): ExecutionSignalSnapshot? {
    val safeId = signalId?.takeIf { it > 0L } ?: return null
    val safeSymbol = symbol?.takeIf { it.isNotBlank() } ?: return null
    return ExecutionSignalSnapshot(
        signalId = safeId,
        symbol = safeSymbol,
        direction = direction.orEmpty(),
        timeframe = timeframe,
        entry = entry,
        stopLoss = stopLoss,
        tp1 = tp1,
        tp2 = tp2,
        tp3 = tp3,
    )
}
