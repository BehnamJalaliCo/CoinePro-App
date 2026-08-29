package com.coinepro.core.chartevents

import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartEvent
import com.coinepro.core.chart.ChartEvents
import com.coinepro.core.chart.EventKind
import com.coinepro.core.chart.EventMark
import com.coinepro.core.chart.EventVisibility
import com.coinepro.core.network.serverTextOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the chart knows about events right now.
 *
 * [events] is everything fetched for [symbol] over the window last asked for — unfiltered, because
 * [visibility] can change without a refetch and re-filtering a held list is free where a second
 * round trip is not.
 */
data class ChartEventState(
    val symbol: String = "",
    val events: List<ChartEvent> = emptyList(),
    val visibility: EventVisibility = EventVisibility.Default,
    val loading: Boolean = false,
    /** The server's own wording when a read failed, never a sentence this app invented. */
    val error: String? = null,
)

/**
 * The marks for one visible range, with the reader's kind filter applied.
 *
 * An extension on the state rather than a method on the controller, and that is a correctness point
 * rather than a style one: a composable that calls this is reading the state it collected, so a
 * change to either the events or the switches recomposes it. A controller method would read the
 * flow's current value behind Compose's back and leave the axis showing the previous answer until
 * something else happened to redraw it.
 */
fun ChartEventState.marks(series: CandleSeries, firstVisible: Int, lastVisible: Int): List<EventMark> =
    ChartEvents.place(events, series, firstVisible, lastVisible, visibility)

/**
 * Events for whatever the chart is looking at, fetched once and kept.
 *
 * The controller exists because the chart cannot own this itself. A pan is a gesture that fires
 * many times a second; the feed is a network call; and the two are joined by a cache and a window
 * wider than the screen, which is state that has to outlive a composition and be shared by the
 * axis, the sheet and the settings switches.
 */
class ChartEventController(
    private val feed: ChartEventFeed,
    private val scope: CoroutineScope,
    private val cache: ChartEventCache = ChartEventCache(),
    /** Unix seconds. Injected so a test can hold time still rather than sleep through a TTL. */
    private val now: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    private val mutableState = MutableStateFlow(ChartEventState())
    val state: StateFlow<ChartEventState> = mutableState.asStateFlow()

    /** The window a request is in flight for, so a drag does not queue one read per frame. */
    private var pending: String? = null

    /**
     * Tell the controller what the chart can see, in unix seconds.
     *
     * Called on every change of visible range, including every frame of a pan — so the fast path
     * has to be the common one. It is: a cache hit publishes and returns without touching a
     * coroutine, and a window already being fetched is dropped rather than fetched again.
     *
     * The fetch is deliberately **wider** than the window asked for. Requesting exactly what is on
     * screen would miss the cache on the very next frame of the same drag, which would make the
     * cache a decoration; half a screen either side means an ordinary pan stays inside what is
     * already held. The extra costs nothing — the feed answers with a whole document either way.
     */
    fun onVisibleRange(symbol: String, fromSeconds: Long, toSeconds: Long) {
        if (symbol.isBlank() || toSeconds <= fromSeconds) return
        val moment = now()
        val padding = maxOf((toSeconds - fromSeconds) / 2, MIN_PADDING_SECONDS)
        val from = fromSeconds - padding
        val to = toSeconds + padding
        val cached = cache.hit(symbol, fromSeconds, toSeconds, moment)
        if (cached != null) {
            publish(symbol, cached)
            return
        }
        val key = "${symbol.uppercase()}:$from:$to"
        if (pending == key) return
        pending = key
        // A different instrument's events must not stay on the axis while the new ones load: they
        // would sit under candles they have nothing to do with, which is worse than an empty strip.
        val current = mutableState.value
        mutableState.value = current.copy(
            symbol = symbol,
            events = if (current.symbol.equals(symbol, ignoreCase = true)) current.events else emptyList(),
            loading = true,
            error = null,
        )
        scope.launch {
            runCatching { feed.events(symbol, from, to) }
                .onSuccess { fetched ->
                    cache.put(symbol, from, to, fetched, now())
                    if (pending == key) pending = null
                    publish(symbol, fetched)
                }
                .onFailure { failure ->
                    if (pending == key) pending = null
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        error = failure.serverTextOrNull(),
                    )
                }
        }
    }

    /** Switch one kind on or off. The held events are re-filtered; nothing is refetched. */
    fun setVisible(kind: EventKind, on: Boolean) {
        mutableState.value = mutableState.value.let { it.copy(visibility = it.visibility.with(kind, on)) }
    }

    /** Replace the whole filter at once — what the settings section hands back. */
    fun setVisibility(visibility: EventVisibility) {
        mutableState.value = mutableState.value.copy(visibility = visibility)
    }

    /**
     * Put back what was stored for this reader, as [EventVisibility.encode] wrote it.
     *
     * Null is "never stored", which is the default filter — see [EventVisibility.decode] for why
     * that is not the same as the empty string.
     */
    fun restoreVisibility(stored: String?) {
        setVisibility(EventVisibility.decode(stored))
    }

    /** Drop everything: a sign-out, or a platform change. The next reader starts from nothing. */
    fun clear() {
        pending = null
        cache.clear()
        mutableState.value = ChartEventState(visibility = mutableState.value.visibility)
    }

    private fun publish(symbol: String, events: List<ChartEvent>) {
        mutableState.value = mutableState.value.copy(
            symbol = symbol,
            events = events,
            loading = false,
            error = null,
        )
    }

    private companion object {
        /**
         * The smallest amount fetched either side of the screen, in seconds.
         *
         * An hour. On a one-minute chart half a screen is a few minutes, and padding by that would
         * put the cache back to missing on almost every drag; an hour is the point below which
         * widening the request stops buying anything.
         */
        const val MIN_PADDING_SECONDS = 3_600L
    }
}
