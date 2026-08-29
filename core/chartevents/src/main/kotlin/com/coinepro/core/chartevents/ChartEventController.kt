package com.coinepro.core.chartevents

import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartEvent
import com.coinepro.core.chart.ChartEvents
import com.coinepro.core.chart.EventKind
import com.coinepro.core.chart.EventMark
import com.coinepro.core.chart.EventVisibility
import com.coinepro.core.network.serverTextOrNull
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Why the axis is bare, when it is bare for a reason the reader is entitled to.
 *
 * Every one of these used to arrive as the same thing — an empty strip — and an empty strip is
 * indistinguishable from a quiet week. That is the failure mode this enum exists to end: a reader
 * on a backend that does not publish an events document at all would have concluded the feature was
 * broken, or worse, that nothing had happened.
 *
 * [NOTHING] is the only one of the four that is not a fault, and it is here for the same reason the
 * other three are: it is an answer, and a screen that cannot tell it from a failure will either
 * apologise for a quiet market or stay silent about an outage.
 */
enum class ChartEventNotice {
    /** The phone had no path off itself. Nothing was asked, so nothing can be concluded. */
    OFFLINE,

    /**
     * The backend serving this platform does not carry the events document.
     *
     * A 404 and not a symbol problem, because the route this feed reads takes no symbol and no
     * window — it is one document per platform, so the only thing a 404 can mean is that the
     * platform does not publish one. True of TradeYar today: `docs/BACKEND_ROUTE_MAP.md` records
     * `api/mobile/v1/market-intelligence` as requested and not yet built, and until it is, a crypto
     * chart has no headlines to mark. Saying so is the whole of the honesty here.
     */
    UNSERVED,

    /** The read failed for some other reason. [ChartEventState.error] carries the server's words. */
    FAILED,

    /** The document was read and holds nothing for this instrument in this window. */
    NOTHING,
}

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
    /** Null while a read is in flight and whenever there is something to draw. See [ChartEventNotice]. */
    val notice: ChartEventNotice? = null,
)

/**
 * The marks for one visible range, with the reader's kind filter applied.
 *
 * An extension on the state rather than a method on the controller, and that is a correctness point
 * rather than a style one: a composable that calls this is reading the state it collected, so a
 * change to either the events or the switches recomposes it. A controller method would read the
 * flow's current value behind Compose's back and leave the axis showing the previous answer until
 * something else happened to redraw it.
 *
 * Kept for callers that hold a series of their own and place against it. A screen that simply wants
 * the marks for the chart in front of it reads [ChartEventController.marks] instead and never has
 * to hold the window at all.
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
 *
 * ### The shape a screen actually needs
 *
 * Two members and nothing else: [onVisibleBars] on every viewport change, and [marks] straight into
 * `ChartDecoration.events`. The screen never converts a bar index into a time, never holds the
 * series a second time, and never re-places anything when the reader flips a switch — the flow
 * carries the answer already filtered. The wider surface below it is what the sheet and the
 * settings section use, and what a caller placing against a series of its own can still reach.
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

    /**
     * The glyphs for the bars the chart can see, already filtered by the reader's switches.
     *
     * One line at a call site: `events = marks`. It is a flow rather than a function because the
     * two things it depends on change at different moments and neither is the composition — a
     * fetch lands from a coroutine and a switch is flipped in a sheet — and a function would leave
     * the axis holding whichever answer happened to be current the last time something else
     * redrew it.
     */
    private val mutableMarks = MutableStateFlow<List<EventMark>>(emptyList())
    val marks: StateFlow<List<EventMark>> = mutableMarks.asStateFlow()

    /** The window a request is in flight for, so a drag does not queue one read per frame. */
    private var pending: String? = null

    /** The bars last reported, kept so a fetch that lands later can place itself against them. */
    private var placement: Placement? = null

    private data class Placement(val series: CandleSeries, val first: Int, val last: Int)

    /**
     * Tell the controller which bars the chart can see — the whole of what a chart screen does.
     *
     * The screen hands over indices because indices are what a viewport reports; the times come
     * from the series, here, once. A screen doing that conversion itself is a screen that has to
     * know that a bar's stamp is its *open* and that the last visible bar runs past its own stamp,
     * which is exactly the arithmetic [ChartEvents] exists to keep in one place.
     *
     * An empty series — the frame before the first candles land — clears the marks rather than
     * fetching, because there is no grid to place anything on yet and a mark placed against a
     * series that has since been replaced is a mark on the wrong bar.
     */
    fun onVisibleBars(symbol: String, series: CandleSeries, firstVisible: Int, lastVisible: Int) {
        if (series.isEmpty) {
            placement = null
            mutableMarks.value = emptyList()
            return
        }
        val last = series.size - 1
        val from = minOf(firstVisible, lastVisible).coerceIn(0, last)
        val to = maxOf(firstVisible, lastVisible).coerceIn(0, last)
        placement = Placement(series, from, to)
        replace()
        onVisibleRange(symbol, series.time[from], series.time[to])
    }

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
     * already held. The extra costs nothing — the feed answers with a whole document either way —
     * and it is also what carries the last visible bar: a bar's stamp is its open, so an event
     * late inside that bar sits past the window the indices describe.
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
            notice = null,
        )
        replace()
        scope.launch {
            runCatching { feed.events(symbol, from, to) }
                .onSuccess { fetched ->
                    cache.put(symbol, from, to, fetched, now())
                    if (pending == key) pending = null
                    publish(symbol, fetched)
                }
                .onFailure { failure ->
                    if (pending == key) pending = null
                    val latest = mutableState.value
                    mutableState.value = latest.copy(
                        loading = false,
                        error = failure.serverTextOrNull(),
                        // A failure that still leaves something on the axis is not announced as an
                        // absence: the marks in front of the reader are real, they are simply not
                        // the newest. What was fetched is never thrown away to make room for a
                        // sentence about a fetch — a pan past the cached window that fails would
                        // otherwise wipe marks the reader was reading.
                        notice = if (latest.events.isEmpty()) failure.asNotice() else null,
                    )
                    replace()
                }
        }
    }

    /** Switch one kind on or off. The held events are re-filtered; nothing is refetched. */
    fun setVisible(kind: EventKind, on: Boolean) {
        setVisibility(mutableState.value.visibility.with(kind, on))
    }

    /** Replace the whole filter at once — what the settings section hands back. */
    fun setVisibility(visibility: EventVisibility) {
        mutableState.value = mutableState.value.copy(visibility = visibility)
        replace()
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
        placement = null
        cache.clear()
        mutableState.value = ChartEventState(visibility = mutableState.value.visibility)
        mutableMarks.value = emptyList()
    }

    private fun publish(symbol: String, events: List<ChartEvent>) {
        mutableState.value = mutableState.value.copy(
            symbol = symbol,
            events = events,
            loading = false,
            error = null,
            // A read that came back with nothing is an answer and says so. Left as null when there
            // is something, so a screen shows a notice only when there is genuinely no other
            // explanation for a bare axis.
            notice = if (events.isEmpty()) ChartEventNotice.NOTHING else null,
        )
        replace()
    }

    /**
     * Re-place the held events against the bars last reported.
     *
     * Called from every mutation rather than derived with `combine`, because a derivation would
     * need a collector to be running before it produced anything — and the first thing a chart
     * screen does with this flow is read its value while composing.
     */
    private fun replace() {
        val where = placement
        mutableMarks.value = if (where == null) {
            emptyList()
        } else {
            mutableState.value.marks(where.series, where.first, where.last)
        }
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

/**
 * What a thrown read means for the axis.
 *
 * A 404 is singled out and the rest are not, because on this route a 404 is unambiguous: the
 * document is per platform and takes neither symbol nor window, so there is no such thing as "that
 * one is missing" — only "this backend does not serve it". Every other status is an ordinary
 * failure and is reported as one, with whatever the server actually wrote.
 */
internal fun Throwable.asNotice(): ChartEventNotice = when {
    this is HttpException && code() == 404 -> ChartEventNotice.UNSERVED
    this is IOException -> ChartEventNotice.OFFLINE
    else -> ChartEventNotice.FAILED
}
