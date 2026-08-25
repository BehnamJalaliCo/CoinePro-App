package com.coinepro.core.copytrade

import com.coinepro.core.network.serverTextOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds what the copy-trading screen shows and the three things it can change.
 *
 * The switch is the delicate part. Turning copying on is not a preference — the server resets the
 * signal baseline on the off→on transition, so the reader's account starts taking trades from that
 * moment. That makes it a write worth showing as in flight and worth re-reading afterwards rather
 * than assuming: [setEnabled] holds [CopyTradeState.saving] until the server has answered, and then
 * refreshes from the server instead of trusting the local guess.
 */
class CopyTradeController(
    private val gateway: CopyTradeGateway,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CopyTradeState())
    val state: StateFlow<CopyTradeState> = _state.asStateFlow()

    fun clear() {
        _state.value = CopyTradeState()
    }

    fun refresh() {
        scope.launch {
            _state.update { it.copy(loading = true, error = null, message = null) }
            runCatching { gateway.status() }
                .onSuccess { status -> _state.value = CopyTradeState(status = status) }
                .onFailure { error -> _state.value = error.toState() }
        }
    }

    fun setEnabled(enabled: Boolean) {
        val current = _state.value
        if (current.saving) return
        // Nothing to say to the server, and on the off→on path it would reset the baseline for no
        // reason. A switch already in the requested position is not a request.
        if (current.status?.preferences?.enabled == enabled) return
        scope.launch {
            _state.update { it.copy(saving = true, error = null, message = null) }
            runCatching { gateway.setEnabled(enabled) }
                .onSuccess {
                    // Deliberately not folded into local state. Whether copying is really running
                    // depends on the terminal as well as the setting, and only a fresh read of the
                    // status says so.
                    _state.update { it.copy(saving = false) }
                    refresh()
                }
                .onFailure { error ->
                    _state.update { it.copy(saving = false).mergeFailure(error) }
                }
        }
    }

    fun linkAccount(broker: String, server: String, login: String, password: String) {
        if (broker.isBlank() || server.isBlank() || login.isBlank() || password.isBlank()) return
        scope.launch {
            _state.update { it.copy(saving = true, error = null, message = null) }
            runCatching { gateway.linkAccount(broker, server, login, password) }
                .onSuccess {
                    _state.update { it.copy(saving = false) }
                    refresh()
                }
                .onFailure { error ->
                    _state.update { it.copy(saving = false).mergeFailure(error) }
                }
        }
    }

    fun unlinkAccount() {
        scope.launch {
            _state.update { it.copy(saving = true, error = null, message = null) }
            runCatching { gateway.unlinkAccount() }
                .onSuccess {
                    _state.update { it.copy(saving = false) }
                    refresh()
                }
                .onFailure { error ->
                    _state.update { it.copy(saving = false).mergeFailure(error) }
                }
        }
    }

    /** A load that failed has nothing left to show, so the state is replaced rather than annotated. */
    private fun Throwable.toState(): CopyTradeState = when (this) {
        is CopyTradeUnsupportedException -> CopyTradeState(unsupported = true)
        is CopyTradeMembershipRequiredException -> CopyTradeState(
            membershipRequired = true,
            membershipMessage = serverMessage,
        )
        else -> CopyTradeState(error = serverTextOrNull())
    }

    /** A write that failed leaves the status on screen intact — it is still true. */
    private fun CopyTradeState.mergeFailure(error: Throwable): CopyTradeState = when (error) {
        is CopyTradeUnsupportedException -> copy(unsupported = true)
        is CopyTradeMembershipRequiredException -> copy(
            membershipRequired = true,
            membershipMessage = error.serverMessage,
        )
        else -> copy(error = error.serverTextOrNull())
    }
}
