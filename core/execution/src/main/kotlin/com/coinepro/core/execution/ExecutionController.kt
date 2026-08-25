package com.coinepro.core.execution

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.coinepro.core.network.serverTextOrNull

class ExecutionController(
    private val gateway: ExecutionGateway,
    private val scope: CoroutineScope,
) {
    private val _connections = MutableStateFlow(ConnectionsState())
    val connections: StateFlow<ConnectionsState> = _connections.asStateFlow()

    private val _execution = MutableStateFlow(ExecutionState())
    val execution: StateFlow<ExecutionState> = _execution.asStateFlow()

    private val _history = MutableStateFlow(ExecutionHistoryState())
    val history: StateFlow<ExecutionHistoryState> = _history.asStateFlow()

    fun clear() {
        _connections.value = ConnectionsState()
        _execution.value = ExecutionState()
        _history.value = ExecutionHistoryState()
    }

    fun refreshConnections() {
        scope.launch {
            _connections.update { it.copy(loading = true, error = null, message = null) }
            runCatching { gateway.connections() }
                .onSuccess { (mt5, lbank) ->
                    _connections.value = ConnectionsState(mt5 = mt5, lbank = lbank)
                }
                .onFailure { error ->
                    // Absent is not broken. A platform that never had this surface must not be
                    // reported as one whose surface failed, or the reader goes looking for a
                    // problem to fix.
                    if (error is ExecutionUnsupportedException) {
                        _connections.value = ConnectionsState(unsupported = true)
                        return@onFailure
                    }
                    _connections.update {
                        it.copy(loading = false, error = error.serverTextOrNull())
                    }
                }
        }
    }

    fun refreshExecutions() {
        scope.launch {
            _history.update { it.copy(loading = true, error = null) }
            runCatching { gateway.executions() }
                .onSuccess { items -> _history.value = ExecutionHistoryState(items = items) }
                .onFailure { error ->
                    if (error is ExecutionUnsupportedException) {
                        _history.value = ExecutionHistoryState(unsupported = true)
                        return@onFailure
                    }
                    _history.update {
                        it.copy(loading = false, error = error.serverTextOrNull())
                    }
                }
        }
    }

    fun connectMt5(broker: String, server: String, login: String, password: String) {
        if (broker.isBlank() || server.isBlank() || login.isBlank() || password.isBlank()) return
        scope.launch {
            _connections.update { it.copy(loading = true, error = null, message = null) }
            runCatching { gateway.connectMt5(broker, server, login, password) }
                .onSuccess {
                    _connections.update { it.copy(message = "MT5 connection saved.") }
                    refreshConnections()
                }
                .onFailure { error ->
                    _connections.update { it.copy(loading = false, error = error.serverTextOrNull()) }
                }
        }
    }

    fun disconnectMt5() {
        scope.launch {
            runCatching { gateway.disconnectMt5() }
                .onSuccess { refreshConnections() }
                .onFailure { error -> _connections.update { it.copy(error = error.message) } }
        }
    }

    fun connectLbank(apiKey: String, apiSecret: String, permission: LbankPermission) {
        if (apiKey.isBlank() || apiSecret.isBlank()) return
        scope.launch {
            _connections.update { it.copy(loading = true, error = null, message = null) }
            runCatching { gateway.connectLbank(apiKey, apiSecret, permission) }
                .onSuccess {
                    _connections.update { it.copy(message = "LBank credentials saved securely.") }
                    refreshConnections()
                }
                .onFailure { error ->
                    _connections.update { it.copy(loading = false, error = error.serverTextOrNull()) }
                }
        }
    }

    fun disconnectLbank() {
        scope.launch {
            runCatching { gateway.disconnectLbank() }
                .onSuccess { refreshConnections() }
                .onFailure { error -> _connections.update { it.copy(error = error.message) } }
        }
    }

    fun executeSignal(
        signalId: Long,
        venue: ExecutionVenue,
        quantity: Double,
        clientRequestId: String,
    ) {
        val validationError = quantityValidationError(venue, quantity)
        if (validationError != null) {
            _execution.value = ExecutionState(error = validationError)
            return
        }
        if (clientRequestId.isBlank()) {
            _execution.value = ExecutionState(error = "Missing idempotency request ID")
            return
        }
        scope.launch {
            _execution.value = ExecutionState(loading = true)
            runCatching { gateway.executeSignal(signalId, venue, quantity, clientRequestId) }
                .onSuccess { value ->
                    _execution.value = ExecutionState(execution = value)
                    refreshExecutions()
                }
                .onFailure { error ->
                    _execution.value = when (error) {
                        is ExecutionRateLimitedException -> ExecutionState(rateLimited = true)
                        is ExecutionUnsupportedException -> ExecutionState(unsupported = true)
                        else -> ExecutionState(error = error.serverTextOrNull())
                    }
                }
        }
    }

    fun refreshExecution(executionId: String) {
        if (executionId.isBlank()) return
        scope.launch {
            runCatching { gateway.execution(executionId) }
                .onSuccess { value ->
                    _execution.value = ExecutionState(execution = value)
                    refreshExecutions()
                }
                .onFailure { error -> _execution.update { it.copy(error = error.message) } }
        }
    }

    fun requestClose() {
        val current = _execution.value.execution ?: return
        if (!current.canRequestClose) return
        scope.launch {
            _execution.update { it.copy(loading = true, error = null) }
            runCatching { gateway.requestClose(current.id) }
                .onSuccess { value ->
                    _execution.value = ExecutionState(execution = value)
                    refreshExecutions()
                }
                .onFailure { error ->
                    _execution.update { it.copy(loading = false, error = error.serverTextOrNull()) }
                }
        }
    }

    fun clearExecution() {
        _execution.value = ExecutionState()
    }

    companion object {
        fun quantityValidationError(venue: ExecutionVenue, quantity: Double): String? {
            if (!quantity.isFinite()) return "Quantity must be a finite number"
            return when (venue) {
                ExecutionVenue.MT5 -> when {
                    quantity < 0.01 -> "MT5 quantity must be at least 0.01 lot"
                    quantity > 100.0 -> "MT5 quantity cannot exceed 100 lots"
                    else -> null
                }
                ExecutionVenue.LBANK -> when {
                    quantity <= 0.0 -> "LBank amount must be greater than zero"
                    quantity > 1_000_000_000.0 -> "LBank amount is outside the supported request range"
                    else -> null
                }
            }
        }
    }
}
