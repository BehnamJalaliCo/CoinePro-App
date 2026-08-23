package com.coinepro.core.execution

enum class ExecutionVenue(val wireValue: String) {
    MT5("mt5"),
    LBANK("lbank"),
}

enum class LbankPermission(val wireValue: String) {
    SPOT("spot"),
    FUTURES("futures"),
}

enum class ExecutionStatus(val wireValue: String) {
    QUEUED("queued"),
    SUBMITTED("submitted"),
    OPEN("open"),
    CLOSE_REQUESTED("close_requested"),
    CLOSED("closed"),
    FAILED("failed"),
    CANCELLED("cancelled"),
    UNKNOWN("unknown"),
}

data class VenueConnection(
    val venue: ExecutionVenue,
    val configured: Boolean,
    val connected: Boolean,
    val status: String,
    val broker: String? = null,
    val server: String? = null,
    val loginMasked: String? = null,
    val lbankPermission: LbankPermission? = null,
    val keyHint: String? = null,
)

data class ConnectionsState(
    val loading: Boolean = false,
    val mt5: VenueConnection? = null,
    val lbank: VenueConnection? = null,
    val error: String? = null,
    val message: String? = null,
)

data class ExecutionSignalSnapshot(
    val signalId: Long,
    val symbol: String,
    val direction: String,
    val timeframe: String?,
    val entry: Double?,
    val stopLoss: Double?,
    val tp1: Double?,
    val tp2: Double?,
    val tp3: Double?,
)

data class SignalExecution(
    val id: String,
    val signalId: Long,
    val venue: ExecutionVenue,
    val product: String,
    val status: ExecutionStatus,
    val side: String,
    val quantity: String,
    val providerOrderId: String?,
    val errorCode: String?,
    val errorMessage: String?,
    val signal: ExecutionSignalSnapshot?,
    val createdAt: String?,
    val updatedAt: String?,
    val closedAt: String?,
) {
    val isBrokerConfirmedOpen: Boolean get() = status == ExecutionStatus.OPEN
    val canRequestClose: Boolean get() = status in setOf(
        ExecutionStatus.QUEUED,
        ExecutionStatus.SUBMITTED,
        ExecutionStatus.OPEN,
        ExecutionStatus.CLOSE_REQUESTED,
    )
}

data class ExecutionState(
    val loading: Boolean = false,
    val execution: SignalExecution? = null,
    val error: String? = null,
)
