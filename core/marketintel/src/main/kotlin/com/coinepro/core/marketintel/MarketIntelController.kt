package com.coinepro.core.marketintel

import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MarketIntelController(
    private val gateway: MarketIntelGateway,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(MarketIntelState())
    val state: StateFlow<MarketIntelState> = mutableState.asStateFlow()

    fun refresh() {
        val current = mutableState.value
        if (current.loading || current.refreshing) return
        mutableState.value = current.copy(
            loading = current.news.isEmpty() && current.calendar.isEmpty(),
            refreshing = current.news.isNotEmpty() || current.calendar.isNotEmpty(),
            error = null,
        )
        scope.launch {
            runCatching { gateway.snapshot() }
                .onSuccess { snapshot ->
                    mutableState.value = MarketIntelState(
                        news = snapshot.news,
                        calendar = snapshot.calendar,
                        serverTime = snapshot.serverTime,
                    )
                }
                .onFailure { error ->
                    val latest = mutableState.value
                    mutableState.value = latest.copy(
                        loading = false,
                        refreshing = false,
                        error = error.message ?: "Market intelligence is unavailable.",
                    )
                }
        }
    }

    fun highImpactWarnings(symbol: String, now: Instant = Instant.now()): List<EconomicEvent> =
        mutableState.value.calendar.highImpactWarningsFor(symbol, now)

    fun clear() {
        mutableState.value = MarketIntelState()
    }
}
