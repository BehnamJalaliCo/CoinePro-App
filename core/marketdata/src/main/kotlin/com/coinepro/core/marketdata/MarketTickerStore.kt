package com.coinepro.core.marketdata

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The day's figures for the whole catalogue, held once and read by every screen that needs them.
 *
 * ### One store rather than a fetch per screen
 *
 * Six screens want this table — the market list's sorting and its gainers and losers, the screener,
 * the heat map, a symbol's statistics, and the funding-rate reading. The route answers for the
 * entire catalogue in a single request, so a per-screen fetch would mean six copies of the same
 * eight hundred rows, six timers, and six chances for two screens to disagree about what today's
 * move was. A shared store is how the reader gets one answer.
 *
 * ### The refresh interval is the server's, not a number picked here
 *
 * The response carries `cache_ttl_ms`, and polling faster than it only re-reads the server's own
 * cache: the same bytes, the same figures, on somebody's mobile data. [refreshIntervalMillis]
 * takes the server's TTL when it sends one and never drops below [MIN_INTERVAL_MILLIS], so a TTL
 * of zero or a malformed one cannot turn this into a request loop.
 *
 * ### It stops when nobody is looking
 *
 * [start] and [stop] are reference counted because several screens can be alive at once — a market
 * list under a heat map that is under a sheet — and the first one to close must not stop the feed
 * for the two still reading it.
 */
class MarketTickerStore(
    private val gateway: MarketTickerGateway,
    private val scope: CoroutineScope,
) {

    private val mutable = MutableStateFlow(MarketTickerState())
    val state: StateFlow<MarketTickerState> = mutable.asStateFlow()

    private var readers = 0
    private var poll: Job? = null

    /** Whether this platform serves the figures at all. False on CoinePro-FX, which has no route. */
    val supported: Boolean get() = gateway.supported

    fun start() {
        readers += 1
        if (poll != null || !gateway.supported) return
        poll = scope.launch {
            while (isActive) {
                loadOnce()
                delay(mutable.value.refreshIntervalMillis)
            }
        }
    }

    fun stop() {
        readers = (readers - 1).coerceAtLeast(0)
        if (readers > 0) return
        poll?.cancel()
        poll = null
    }

    /** A reader's own pull-to-refresh. Deliberately does not reset the poll's clock. */
    fun refresh() {
        if (!gateway.supported) return
        scope.launch { loadOnce() }
    }

    private suspend fun loadOnce() {
        mutable.value = mutable.value.copy(loading = true)
        val next = runCatching { gateway.load() }
        mutable.value = next.fold(
            onSuccess = { table ->
                mutable.value.copy(table = table, loading = false, failed = false)
            },
            onFailure = {
                // The previous table is kept rather than cleared. A dropped request on a connection
                // that comes and goes is the normal case for this audience, and blanking every
                // percentage on screen because one poll failed would be a worse answer than showing
                // figures that are a minute old — which the reader can see, because the table
                // carries when it was fetched.
                mutable.value.copy(loading = false, failed = true)
            },
        )
    }

    private companion object {
        /**
         * The floor under the server's own TTL.
         *
         * Five seconds is what the route currently reports, and this exists for the case where it
         * reports nothing or reports zero — either of which would otherwise make this a tight loop
         * against a route that answers 801 rows.
         */
        const val MIN_INTERVAL_MILLIS = 5_000L
    }

    /** What every reader of this store sees. */
    data class MarketTickerState(
        val table: MarketTickerTable = MarketTickerTable.Empty,
        val loading: Boolean = false,
        /**
         * The last attempt failed, and the [table] beside this may still be worth reading.
         *
         * Two flags rather than one state, because "stale but usable" is the state this app is in
         * most of the time and it is not an error to be reported as one.
         */
        val failed: Boolean = false,
    ) {
        val refreshIntervalMillis: Long
            get() = (table.cacheTtlMillis ?: 0L).coerceAtLeast(MIN_INTERVAL_MILLIS)

        /** The figures for one market, or null where the table does not carry it. */
        operator fun get(symbol: String): MarketTicker? = table.tickers[symbol.uppercase()]
    }
}
