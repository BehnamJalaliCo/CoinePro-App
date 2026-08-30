package com.coinepro.core.marketintel

import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.coinepro.core.network.serverTextOrNull

class MarketIntelController(
    private val gateway: MarketIntelGateway,
    private val scope: CoroutineScope,
    /**
     * Told what each successful fetch actually contained, so it reaches the app's log and export.
     *
     * A callback rather than a log dependency: this module talks to a server and holds state, and
     * giving it `core:diagnostics` to say two sentences would be the wrong trade. The shell holds
     * both and is where they meet.
     *
     * It carries the whole snapshot rather than a summary because the two questions it answers are
     * different — "is the calendar empty because nothing was published or because we could not
     * read it" and "is the news feed genuinely not moving" — and the second is answered by the
     * newest publication date, which no summary would have thought to carry.
     */
    private val onSnapshot: (MarketIntelSnapshot) -> Unit = {},
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
                        calendarSource = snapshot.calendarSource,
                        newsSource = snapshot.newsSource,
                        platform = snapshot.platform,
                    )
                    onSnapshot(snapshot)
                }
                .onFailure { error ->
                    val latest = mutableState.value
                    mutableState.value = latest.copy(
                        loading = false,
                        refreshing = false,
                        error = error.serverTextOrNull(),
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
