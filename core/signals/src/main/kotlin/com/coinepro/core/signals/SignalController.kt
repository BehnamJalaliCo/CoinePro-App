package com.coinepro.core.signals

import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val HISTORY_PAGE_SIZE = 50
private const val MAX_HISTORY_RECORDS = 1000

class SignalController(
    private val gateway: SignalGateway,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(SignalsState())
    private val _detailState = MutableStateFlow(SignalDetailState())
    private val _historyState = MutableStateFlow(SignalHistoryState())

    val state: StateFlow<SignalsState> = _state.asStateFlow()
    val detailState: StateFlow<SignalDetailState> = _detailState.asStateFlow()
    val historyState: StateFlow<SignalHistoryState> = _historyState.asStateFlow()

    private var listJob: Job? = null
    private var detailJob: Job? = null
    private var historyJob: Job? = null

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

    fun refreshHistory() {
        historyJob?.cancel()
        _historyState.update { it.copy(loading = true, error = null, membershipRequired = false) }
        historyJob = scope.launch {
            try {
                val forex = loadClosedHistory(SignalMarketFilter.FOREX)
                val crypto = loadClosedHistory(SignalMarketFilter.CRYPTO)
                val combined = (forex.items + crypto.items)
                    .distinctBy(TradingSignal::id)
                    .sortedWith(compareByDescending<TradingSignal> { historyInstant(it) }.thenByDescending(TradingSignal::id))
                val expected = forex.expectedTotal + crypto.expectedTotal
                _historyState.value = SignalHistoryState(
                    items = combined,
                    expectedTotal = expected,
                    coverageComplete = forex.complete && crypto.complete && combined.size >= expected,
                )
            } catch (_: SignalMembershipRequiredException) {
                _historyState.value = SignalHistoryState(membershipRequired = true)
            } catch (error: Exception) {
                _historyState.update {
                    it.copy(loading = false, error = error.message ?: "Signal history is unavailable")
                }
            }
        }
    }

    private suspend fun loadClosedHistory(market: SignalMarketFilter): HistoryLoad {
        val items = mutableListOf<TradingSignal>()
        var offset = 0
        var expectedTotal = 0
        var complete = true

        do {
            val page = gateway.list(
                market = market,
                status = SignalStatusFilter.CLOSED,
                limit = HISTORY_PAGE_SIZE,
                offset = offset,
            )
            expectedTotal = page.total.coerceAtLeast(0)
            items += page.items

            if (page.items.isEmpty()) {
                complete = offset >= expectedTotal
                break
            }

            // Offset belongs to the server row-set, not to the subset that survived Android mapping.
            // Advancing by mapped item count could repeat pages when an invalid row is rejected locally.
            offset += HISTORY_PAGE_SIZE
            if (items.size >= MAX_HISTORY_RECORDS && offset < expectedTotal) {
                complete = false
                break
            }
        } while (offset < expectedTotal)

        return HistoryLoad(
            items = items.take(MAX_HISTORY_RECORDS),
            expectedTotal = expectedTotal,
            complete = complete && offset >= expectedTotal,
        )
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
        historyJob?.cancel()
        listJob = null
        detailJob = null
        historyJob = null
        _state.value = SignalsState()
        _detailState.value = SignalDetailState()
        _historyState.value = SignalHistoryState()
    }

    private data class HistoryLoad(
        val items: List<TradingSignal>,
        val expectedTotal: Int,
        val complete: Boolean,
    )
}

private fun historyInstant(signal: TradingSignal): Instant? {
    val raw = signal.closedAt ?: signal.createdAt ?: return null
    return runCatching { Instant.parse(raw) }.getOrNull()
}
