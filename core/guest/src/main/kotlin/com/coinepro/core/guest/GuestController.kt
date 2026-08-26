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
    private val symbols: List<String> = DEFAULT_SYMBOLS,
    private val pollMillis: Long = 10_000,
) {
    private val pricesMutable = MutableStateFlow<GuestPricesState>(GuestPricesState.Loading)
    private val newsMutable = MutableStateFlow<GuestNewsState>(GuestNewsState.Loading)

    val prices: StateFlow<GuestPricesState> = pricesMutable.asStateFlow()
    val news: StateFlow<GuestNewsState> = newsMutable.asStateFlow()

    private var poll: Job? = null

    fun start() {
        if (poll?.isActive == true) return
        poll = scope.launch {
            while (true) {
                refreshPrices()
                delay(pollMillis)
            }
        }
        refreshNews()
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
    suspend fun refreshPrices() {
        when (val result = gateway.prices(symbols)) {
            is AppResult.Success -> pricesMutable.value = GuestPricesState.Ready(result.value)
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

    companion object {
        /**
         * What a guest sees first.
         *
         * Eight, matching the server's own default hero set, and the reason for a fixed list rather
         * than "the top movers" is that a first screen which reorders itself between two glances is
         * a first screen nobody can learn.
         */
        val DEFAULT_SYMBOLS = listOf(
            "BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT",
            "BNBUSDT", "DOGEUSDT", "ADAUSDT", "TONUSDT",
        )
    }
}
