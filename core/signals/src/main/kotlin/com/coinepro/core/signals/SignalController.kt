package com.coinepro.core.signals

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SignalController(
    private val gateway: SignalGateway,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SignalsState())
    private val _detailState = MutableStateFlow(SignalDetailState())

    val state: StateFlow<SignalsState> = _state.asStateFlow()
    val detailState: StateFlow<SignalDetailState> = _detailState.asStateFlow()

    private var listJob: Job? = null
    private var detailJob: Job? = null

    fun start() {
        if (_state.value.items.isEmpty() && !_state.value.loading && !_state.value.membershipRequired) {
            refresh()
        }
    }

    fun selectMarket(market: SignalMarketFilter) {
        if (_state.value.market == market) return
        _state.update { it.copy(market = market, items = emptyList(), error = null, membershipRequired = false) }
        refresh()
    }

    fun selectStatus(status: SignalStatusFilter) {
        if (_state.value.status == status) return
        _state.update { it.copy(status = status, items = emptyList(), error = null, membershipRequired = false) }
        refresh()
    }

    fun refresh() {
        listJob?.cancel()
        val market = _state.value.market
        val status = _state.value.status
        _state.update { it.copy(loading = true, error = null, membershipRequired = false) }
        listJob = scope.launch {
            try {
                val page = gateway.list(market, status)
                _state.update { old ->
                    if (old.market == market && old.status == status) {
                        old.copy(items = page.items, loading = false, error = null)
                    } else {
                        old
                    }
                }
            } catch (_: SignalMembershipRequiredException) {
                _state.update { it.copy(items = emptyList(), loading = false, membershipRequired = true, error = null) }
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = error.message ?: "Signals are unavailable") }
            }
        }
    }

    fun loadDetail(signalId: Long) {
        detailJob?.cancel()
        _detailState.value = SignalDetailState(signalId = signalId, loading = true)
        detailJob = scope.launch {
            try {
                _detailState.value = SignalDetailState(
                    signalId = signalId,
                    signal = gateway.detail(signalId),
                )
            } catch (_: SignalMembershipRequiredException) {
                _detailState.value = SignalDetailState(signalId = signalId, membershipRequired = true)
            } catch (error: Exception) {
                _detailState.value = SignalDetailState(
                    signalId = signalId,
                    error = error.message ?: "Signal details are unavailable",
                )
            }
        }
    }

    fun clearDetail() {
        detailJob?.cancel()
        detailJob = null
        _detailState.value = SignalDetailState()
    }

    fun clear() {
        listJob?.cancel()
        detailJob?.cancel()
        listJob = null
        detailJob = null
        _state.value = SignalsState()
        _detailState.value = SignalDetailState()
    }
}
