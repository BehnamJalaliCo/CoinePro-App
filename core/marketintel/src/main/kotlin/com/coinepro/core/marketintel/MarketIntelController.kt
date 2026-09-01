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
    /**
     * Where the reading page gets the whole of a story's text. See [NewsBodySource].
     *
     * On the controller rather than passed to the screen, because the screen is a plain composable
     * taking a plain story — which is what lets the same page open a member's headline and a
     * guest's — and because the answer is per platform, which is a fact this object already holds.
     */
    private val bodies: NewsBodySource = NoNewsBodySource,
) {
    private val mutableState = MutableStateFlow(MarketIntelState())
    val state: StateFlow<MarketIntelState> = mutableState.asStateFlow()

    /**
     * The story's own translated text, or null where the backend publishes none.
     *
     * Suspending and un-cached, which is right for something read once per opened article: the
     * reading page holds what it got for as long as it is open, and a reader who comes back to the
     * same story a minute later would rather have the paragraph the newsroom has since corrected
     * than the one this object remembered.
     *
     * It cannot throw. A body that will not arrive leaves the page exactly as it is — the summary,
     * well set, under a line saying that is what it is — and that page has to be good on its own
     * anyway, because one of the two backends publishes no bodies at all.
     */
    suspend fun articleBody(id: String, summary: String?): String? =
        runCatching { bodies.body(id, summary) }.getOrNull()

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
