package com.coinepro.core.execution

import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

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
    suspend fun execution(executionId: String): SignalExecution
    suspend fun requestClose(executionId: String): SignalExecution
}

internal interface ExecutionApi {
    @GET("user/signals/execution/connections")
    suspend fun connections(): ConnectionsResponseDto

    @POST("user/signals/execution/connections/mt5")
    suspend fun connectMt5(@Body body: Mt5ConnectionDto): Map<String, Any?>

    @DELETE("user/signals/execution/connections/mt5")
    suspend fun disconnectMt5(): Map<String, Any?>

    @PUT("user/signals/execution/connections/lbank")
    suspend fun connectLbank(@Body body: LbankConnectionDto): Map<String, Any?>

    @DELETE("user/signals/execution/connections/lbank")
    suspend fun disconnectLbank(): Map<String, Any?>

    @POST("user/signals/execution/signals/{signalId}/execute")
    suspend fun executeSignal(
        @Path("signalId") signalId: Long,
        @Body body: ExecuteSignalDto,
    ): ExecutionResponseDto

    @GET("user/signals/execution/executions/{executionId}")
    suspend fun execution(@Path("executionId") executionId: String): ExecutionResponseDto

    @POST("user/signals/execution/executions/{executionId}/close")
    suspend fun requestClose(@Path("executionId") executionId: String): ExecutionResponseDto
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

class NetworkExecutionGateway private constructor(
    private val api: ExecutionApi,
) : ExecutionGateway {
    override suspend fun connections(): Pair<VenueConnection?, VenueConnection?> {
        val response = api.connections()
        return response.mt5?.toDomain(ExecutionVenue.MT5) to response.lbank?.toDomain(ExecutionVenue.LBANK)
    }

    override suspend fun connectMt5(broker: String, server: String, login: String, password: String) {
        api.connectMt5(Mt5ConnectionDto(broker, server, login, password))
    }

    override suspend fun disconnectMt5() {
        api.disconnectMt5()
    }

    override suspend fun connectLbank(apiKey: String, apiSecret: String, permission: LbankPermission) {
        api.connectLbank(LbankConnectionDto(apiKey, apiSecret, permission.wireValue))
    }

    override suspend fun disconnectLbank() {
        api.disconnectLbank()
    }

    override suspend fun executeSignal(
        signalId: Long,
        venue: ExecutionVenue,
        quantity: Double,
        clientRequestId: String,
    ): SignalExecution = requireNotNull(
        api.executeSignal(
            signalId,
            ExecuteSignalDto(
                venue = venue.wireValue,
                quantity = quantity,
                clientRequestId = clientRequestId,
            ),
        ).execution?.toDomain(),
    ) { "Invalid execution response" }

    override suspend fun execution(executionId: String): SignalExecution = requireNotNull(
        api.execution(executionId).execution?.toDomain(),
    ) { "Invalid execution response" }

    override suspend fun requestClose(executionId: String): SignalExecution = requireNotNull(
        api.requestClose(executionId).execution?.toDomain(),
    ) { "Invalid execution response" }

    companion object {
        fun create(retrofit: Retrofit): NetworkExecutionGateway =
            NetworkExecutionGateway(retrofit.create(ExecutionApi::class.java))
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
    val safeSignalId = signalId ?: return null
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
    val safeId = signalId ?: return null
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
