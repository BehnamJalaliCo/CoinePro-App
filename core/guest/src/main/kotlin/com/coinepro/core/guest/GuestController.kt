package com.coinepro.core.guest

import com.coinepro.core.common.AppResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GuestPricesState {
    data object Loading : GuestPricesState
    data class Ready(val prices: GuestPrices) : GuestPricesState

    /** [reason] is null where the failure was the network rather than the server. */
    data class Unavailable(val reason: String?) : GuestPricesState
}

sealed interface GuestTrackRecordState {
    data object Loading : GuestTrackRecordState
    data class Ready(val record: GuestTrackRecord) : GuestTrackRecordState

    /** Includes the server saying it has nothing gradeable — see [GuestTrackRecord.available]. */
    data object Unavailable : GuestTrackRecordState
}

sealed interface GuestCommunityState {
    data object Loading : GuestCommunityState
    data class Ready(val community: GuestCommunity) : GuestCommunityState

    /** The request failed, or the server had no channel and no count worth a heading. */
    data object Unavailable : GuestCommunityState
}

sealed interface GuestNewsState {
    data object Loading : GuestNewsState
    data class Ready(val headlines: List<GuestHeadline>) : GuestNewsState
    data class Unavailable(val reason: String?) : GuestNewsState
}

/**
 * The guest screen's data, polled rather than streamed.
 *
 * The signed-in feed is a WebSocket. This one is not, and should not be: the public route is a
 * cached snapshot behind a one-second `Cache-Control`, a socket per uninvited visitor is a cost the
 * server did not agree to, and a reader who has not signed in is looking rather than trading. Ten
 * seconds is the interval that reads as live without pretending to be a tick feed.
 *
 * Polling stops with [stop] and there is exactly one poll job at a time. A screen that starts a
 * second one on every recomposition doubles the request rate for as long as it is open, and the
 * only place that shows up is the server's bill.
 */
class GuestController(
    private val gateway: GuestGateway,
    private val scope: CoroutineScope,
    private val pollMillis: Long = 10_000,
    /**
     * How many instruments a guest is shown at once.
     *
     * The feed carries several hundred. Handing all of them to somebody who has just opened the app
     * is a wall, not a market — and the server's own note beside the route says the same thing. The
     * rest are reachable by search; this is the shelf, not the warehouse.
     */
    private val visibleCount: Int = 20,
) {
    private val pricesMutable = MutableStateFlow<GuestPricesState>(GuestPricesState.Loading)
    private val newsMutable = MutableStateFlow<GuestNewsState>(GuestNewsState.Loading)
    private val recordMutable = MutableStateFlow<GuestTrackRecordState>(GuestTrackRecordState.Loading)
    private val communityMutable = MutableStateFlow<GuestCommunityState>(GuestCommunityState.Loading)

    val prices: StateFlow<GuestPricesState> = pricesMutable.asStateFlow()
    val news: StateFlow<GuestNewsState> = newsMutable.asStateFlow()
    val trackRecord: StateFlow<GuestTrackRecordState> = recordMutable.asStateFlow()
    val community: StateFlow<GuestCommunityState> = communityMutable.asStateFlow()

    private var poll: Job? = null

    /**
     * The symbols polled, chosen once from the first full snapshot.
     *
     * Fixed after that first read rather than recomputed each poll, and that is deliberate: a list
     * that re-sorts itself by volume every ten seconds rearranges under the reader's finger. The
     * market decides what is on the shelf; it does not get to decide where each thing sits while
     * somebody is looking at it.
     */
    private var polled: List<String> = emptyList()

    fun start() {
        if (poll?.isActive == true) return
        poll = scope.launch {
            // The first pass asks for everything, so the shelf is chosen from the real universe
            // rather than from a list compiled into the app months ago.
            refreshPrices(all = polled.isEmpty())
            while (true) {
                delay(pollMillis)
                refreshPrices()
            }
        }
        refreshNews()
        refreshTrackRecord()
        refreshCommunity()
    }

    fun stop() {
        poll?.cancel()
        poll = null
    }

    /**
     * One pass over the price route.
     *
     * A failure does **not** replace a snapshot already on screen. A reader watching a list does
     * not want it to become an error message because one poll in ten timed out; the numbers stay,
     * and the staleness the server reports is what tells them how much to trust them.
     */
    suspend fun refreshPrices(all: Boolean = false) {
        val asked = if (all || polled.isEmpty()) emptyList() else polled
        when (val result = gateway.prices(asked)) {
            is AppResult.Success -> {
                val prices = if (polled.isEmpty()) {
                    // Busiest first, once. Volume is the honest ordering for a shelf nobody has
                    // personalised yet: it is what other people are actually trading, rather than
                    // what moved most in the last hour, which rewards whatever is briefly wild.
                    val chosen = result.value.quotes
                        .sortedByDescending { it.volume24h ?: 0.0 }
                        .take(visibleCount)
                    polled = chosen.map(GuestQuote::symbol)
                    result.value.copy(quotes = chosen, universeSize = result.value.quotes.size)
                } else {
                    // Later polls answer in the feed's order; the shelf keeps the order it was
                    // given, so nothing moves under the reader.
                    val bySymbol = result.value.quotes.associateBy(GuestQuote::symbol)
                    result.value.copy(quotes = polled.mapNotNull(bySymbol::get))
                }
                pricesMutable.value = GuestPricesState.Ready(prices)
            }
            is AppResult.Failure ->
                if (pricesMutable.value !is GuestPricesState.Ready) {
                    pricesMutable.value = GuestPricesState.Unavailable(result.message)
                }
        }
    }

    fun refreshNews() {
        scope.launch {
            newsMutable.value = when (val result = gateway.news()) {
                is AppResult.Success -> GuestNewsState.Ready(result.value)
                is AppResult.Failure -> GuestNewsState.Unavailable(result.message)
            }
        }
    }

    /**
     * The track record, fetched once rather than polled.
     *
     * These are closed trades. Nothing about them changes between one minute and the next, and
     * polling a finished history would be a request that can only ever return the same answer.
     */
    fun refreshTrackRecord() {
        scope.launch {
            recordMutable.value = when (val result = gateway.trackRecord()) {
                is AppResult.Success ->
                    if (result.value.available && result.value.entries.isNotEmpty()) {
                        GuestTrackRecordState.Ready(result.value)
                    } else {
                        // The server said it has nothing gradeable, or the list came back empty.
                        // Drawing "0 trades" here would be a claim about a bot that has traded.
                        GuestTrackRecordState.Unavailable
                    }
                is AppResult.Failure -> GuestTrackRecordState.Unavailable
            }
        }
    }

    /**
     * The community, fetched once per screen rather than polled.
     *
     * The counts move by a handful of people an hour and the server fetches them from Telegram on
     * every request. Polling them would spend somebody else's rate limit to redraw a number that
     * has not visibly changed.
     */
    fun refreshCommunity() {
        scope.launch {
            communityMutable.value = when (val result = gateway.community()) {
                is AppResult.Success ->
                    // Nothing to draw is Unavailable rather than an empty Ready. A section heading
                    // over no channels and no numbers reads as a community nobody joined.
                    if (result.value.isEmpty) {
                        GuestCommunityState.Unavailable
                    } else {
                        GuestCommunityState.Ready(result.value)
                    }
                is AppResult.Failure -> GuestCommunityState.Unavailable
            }
        }
    }
}
