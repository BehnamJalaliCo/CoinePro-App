package com.coinepro.core.execution

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ExecutionController(
    private val gateway: ExecutionGateway,
    private val scope: CoroutineScope,
) {
    private val _connections = MutableStateFlow(ConnectionsState())
    val connections: StateFlow<ConnectionsState> = _connections.asStateFlow()

    private val _execution = MutableStateFlow(ExecutionState())
    val execution: StateFlow<ExecutionState> = _execution.asStateFlow()

    fun clear() {
        _connections.value = ConnectionsState()
        _execution.value = ExecutionState()
    }

    fun refreshConnections() {
        scope.launch {
            _connections.update { it.copy(loading = true, error = null, message = null) }
            runCatching { gateway.connections() }
                .onSuccess { (mt5, lbank) ->
                    _connections.value = ConnectionsState(mt5 = mt5, lbank = lbank)
                }
                .onFailure { error ->
                    _connections.update {
                        it.copy(loading = false, error = error.message ?: "Connections unavailable")
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
                    _connections.update { it.copy(loading = false, error = error.message ?: "MT5 connection failed") }
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
                    _connections.update { it.copy(loading = false, error = error.message ?: "LBank connection failed") }
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
        if (quantity <= 0 || clientRequestId.isBlank()) return
        scope.launch {
            _execution.value = ExecutionState(loading = true)
            runCatching { gateway.executeSignal(signalId, venue, quantity, clientRequestId) }
                .onSuccess { value -> _execution.value = ExecutionState(execution = value) }
                .onFailure { error ->
                    _execution.value = ExecutionState(error = error.message ?: "Execution request failed")
                }
        }
    }

    fun refreshExecution(executionId: String) {
        if (executionId.isBlank()) return
        scope.launch {
            runCatching { gateway.execution(executionId) }
                .onSuccess { value -> _execution.value = ExecutionState(execution = value) }
                .onFailure { error -> _execution.update { it.copy(error = error.message) } }
        }
    }

    fun requestClose() {
        val current = _execution.value.execution ?: return
        if (!current.canRequestClose) return
        scope.launch {
            _execution.update { it.copy(loading = true, error = null) }
            runCatching { gateway.requestClose(current.id) }
                .onSuccess { value -> _execution.value = ExecutionState(execution = value) }
                .onFailure { error ->
                    _execution.update { it.copy(loading = false, error = error.message ?: "Close request failed") }
                }
        }
    }

    fun clearExecution() {
        _execution.value = ExecutionState()
    }
}
