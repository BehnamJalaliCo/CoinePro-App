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
    /**
     * This platform has no venue-connection surface at all.
     *
     * A state rather than an error, and the distinction is the whole point: nothing went wrong,
     * retrying will not help, and a screen showing a failure would send the reader looking for a
     * problem that does not exist. CoinePro-FX is the case — it links a broker through copy
     * trading instead, which is a different surface entirely.
     */
    val unsupported: Boolean = false,
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
    val isActive: Boolean get() = status in setOf(
        ExecutionStatus.QUEUED,
        ExecutionStatus.SUBMITTED,
        ExecutionStatus.OPEN,
        ExecutionStatus.CLOSE_REQUESTED,
    )

    // LBank close is deliberately not exposed until its provider lifecycle is verified.
    // A queued LBank intent may still be cancelled before it reaches the provider.
    // CLOSE_REQUESTED is already in flight, so no second close action is exposed.
    val canRequestClose: Boolean get() = when {
        status == ExecutionStatus.QUEUED -> true
        venue == ExecutionVenue.MT5 -> status == ExecutionStatus.SUBMITTED || status == ExecutionStatus.OPEN
        else -> false
    }
}

data class ExecutionState(
    val loading: Boolean = false,
    val execution: SignalExecution? = null,
    /**
     * The server refused this attempt for coming too fast, and nothing was retried.
     *
     * Carried as its own flag because it is the one failure a reader must not read as "the order
     * may have gone through". Nothing was sent, waiting fixes it, and the app deliberately does not
     * retry on their behalf — an order is not something to send twice on a guess.
     */
    val rateLimited: Boolean = false,
    /** This platform places no orders at all; see [ConnectionsState.unsupported]. */
    val unsupported: Boolean = false,
    val error: String? = null,
)

data class ExecutionHistoryState(
    val loading: Boolean = false,
    val items: List<SignalExecution> = emptyList(),
    /** This platform places no orders at all; see [ConnectionsState.unsupported]. */
    val unsupported: Boolean = false,
    val error: String? = null,
) {
    val active: List<SignalExecution> get() = items.filter { it.isActive }
}
