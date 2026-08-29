package com.coinepro.feature.heatmap

import com.coinepro.core.common.UiMessage
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.symbols.SymbolRanking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Everything the heatmap screen draws, as one immutable value.
 *
 * [assets] is the whole catalogue, priced, with whatever bars have arrived folded in. It is not yet
 * capped or sorted — [HeatmapSelection] does that against the reader's current options, which the
 * controller deliberately does not know about, so changing a sizing does not re-enter the network
 * layer.
 */
data class HeatmapState(
    val assets: List<HeatmapAsset> = emptyList(),
    /** True until the catalogue itself has landed. The map has nothing at all before that. */
    val loading: Boolean = false,
    /** True while daily bars are still being fetched. The coverage line says "still filling in". */
    val resolving: Boolean = false,
    /**
     * Whether this map has any way to obtain a figure at all.
     *
     * False when no bar source was wired in, and it is the difference between "the numbers have not
     * arrived yet" and "there will be no numbers" — two states that look identical on the canvas
     * and must not read identically in the copy above it.
     */
    val canResolve: Boolean = false,
    /**
     * Owned copy, not the exception's own message — the rule `MarketSearchController` follows. A
     * reader on a bad connection must not be shown `Unable to resolve host` as product copy.
     */
    val error: UiMessage? = null,
) {
    /** How many markets have had their bars read, whatever those bars turned out to contain. */
    val resolved: Int get() = assets.count(HeatmapAsset::resolved)
}

/**
 * Loads the catalogue, then the daily bars that give the map its second variable.
 *
 * ### The one hard problem, and how it is answered
 *
 * A heatmap has to colour several hundred markets by figures the snapshot endpoint does not carry.
 * The day's move, the day's range and the day's volume all come from a market's own bars, which is
 * one request each. Fetching a thousand before the first tile draws is not a map; it is an outage
 * of your own making — and `SparklineStore` and `ScreenerController` both learned that here first.
 *
 * So the work is bounded in three ways, and each is a decision rather than a tuning constant:
 *
 * * **Only what the map can draw.** [RESOLUTION_BUDGET] markets are resolved, taken in liquidity
 *   order. That is comfortably more than [HeatmapDensity.STANDARD] draws, so every tile on a
 *   default map is covered and a reader who raises the density or drills into a block still finds
 *   figures waiting rather than a fresh wait.
 * * **[CONCURRENCY] at a time.** The budget queues rather than opening two hundred sockets, and
 *   the largest markets — the ones with the largest tiles — are served first, so the map fills
 *   from the middle outward the way a reader reads it.
 * * **Once per symbol, ever, per run.** A market whose bars failed is not retried. A symbol one
 *   backend does not carry answers 4xx every time, and retrying it per frame is the worst thing
 *   this class could do to our own server.
 *
 * ### Why this is a controller and not a `remember` in the screen
 *
 * State in a `StateFlow`, work on an injected [CoroutineScope], no dependency on a lifecycle owner
 * — the shape `MarketSearchController` established in this app. It is what lets the resolution
 * order be asserted without Robolectric, and it is what stops a rotation from restarting two
 * hundred candle requests.
 */
class HeatmapController(
    /** The catalogue and the live prices, shared with the search screen rather than fetched twice. */
    private val search: MarketSearchController,
    private val scope: CoroutineScope,
    /**
     * Daily bars, for every figure the snapshot does not carry.
     *
     * Null is a supported state and not a broken one: the map still draws, sized by the offline
     * liquidity ranking, with every tile hatched as unknown and a line above it saying why. That is
     * a worse map than one with bars and a far better one than a field of grey squares implying the
     * whole market is flat.
     */
    private val bars: HeatmapBarSource? = null,
    /**
     * The venue's own twenty-four-hour statistics for the whole catalogue, in one call.
     *
     * Null today on both platforms, because no route serves it yet — the ask is recorded in this
     * module's `## SERVER ASKS`. It is a constructor parameter now rather than later so that the
     * day the route ships, the wiring is one argument and this class does not change: it already
     * asks for the batch first and already lets [HeatmapFacts] prefer it over the bars.
     */
    private val tickers: HeatmapTickerSource? = null,
) {
    private val _state = MutableStateFlow(HeatmapState(canResolve = bars != null || tickers != null))
    val state: StateFlow<HeatmapState> = _state.asStateFlow()

    private val barsBySymbol = mutableMapOf<String, List<OhlcBar>>()
    private val tickerBySymbol = mutableMapOf<String, HeatmapTicker>()

    /** Symbols whose bars have been asked for, whether or not an answer came. See the class note. */
    private val asked = mutableSetOf<String>()

    private var period: HeatmapPeriod = HeatmapPeriod.MONTH
    private var collectJob: Job? = null
    private var tickerJob: Job? = null
    private var resolveJob: Job? = null
    private val gate = Semaphore(CONCURRENCY)

    /** Loads the catalogue once and starts resolving bars. Called when the map opens. */
    fun start() {
        search.start()
        loadTickers()
        if (collectJob?.isActive == true) return
        collectJob = scope.launch {
            search.state.collect { catalogue ->
                _state.update {
                    it.copy(
                        assets = assetsOf(catalogue.results),
                        loading = catalogue.loading && catalogue.results.isEmpty(),
                        error = catalogue.error,
                    )
                }
                resolve()
            }
        }
    }

    /**
     * Re-reads the catalogue, and forgets which markets have been asked for.
     *
     * The bars themselves are kept until their replacements arrive, so a refresh does not blank a
     * map the reader is looking at — but [asked] is cleared, which is what makes refresh the retry
     * for a market whose candles failed the first time. Without that this class would have no way
     * back from a bad minute of network, by design, and the reader would have no way to ask.
     */
    fun refresh() {
        asked.clear()
        loadTickers()
        search.refresh()
    }

    /**
     * The whole catalogue's twenty-four-hour statistics, in one request.
     *
     * Ahead of the per-market candles and independent of them: the moment this lands, every tile on
     * the map has a change, a range and a volume, and the candle queue behind it is only still
     * running for the two figures a rolling ticker cannot carry — the period return and the median
     * daily range. Today it does nothing at all, because [tickers] is null on both platforms.
     */
    private fun loadTickers() {
        val source = tickers ?: return
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            val loaded = source.tickers()
            if (loaded.isEmpty()) return@launch
            tickerBySymbol.putAll(loaded)
            _state.update { it.copy(assets = assetsOf(search.state.value.results)) }
        }
    }

    /**
     * The window [HeatmapColour.PERFORMANCE] measures over.
     *
     * Set from the screen rather than read from it, because the figure is derived from bars this
     * class holds and recomputing it costs no network at all — the ninety-bar page is already here.
     * A period change that refetched would be a spinner for a number the app already knows.
     */
    fun setPeriod(period: HeatmapPeriod) {
        if (this.period == period) return
        this.period = period
        _state.update { it.copy(assets = assetsOf(search.state.value.results)) }
    }

    private fun assetsOf(rows: List<MarketSearchRow>): List<HeatmapAsset> {
        val bars = synchronized(barsBySymbol) { barsBySymbol.toMap() }
        return heatmapAssetsFrom(rows, bars, tickerBySymbol, period, asked + tickerBySymbol.keys)
    }

    /**
     * Asks for the bars of the markets a map could plausibly show, largest first.
     *
     * The order is the liquidity ranking rather than the reader's current sizing, and that is
     * deliberate: the sizing can change with a tap and the ranking cannot, so resolving against it
     * means switching from volume to turnover does not restart the queue.
     */
    private fun resolve() {
        val source = bars ?: return
        if (resolveJob?.isActive == true) return
        val pending = _state.value.assets
            .sortedBy { SymbolRanking.rank(it.meta) }
            .take(RESOLUTION_BUDGET)
            .map(HeatmapAsset::symbol)
            .filter { it !in asked }
        if (pending.isEmpty()) return
        asked.addAll(pending)
        _state.update { it.copy(resolving = true) }
        resolveJob = scope.launch {
            coroutineScope {
                pending.forEach { symbol ->
                    launch {
                        gate.withPermit {
                            val loaded = source.bars(symbol)
                            // Guarded because these are [CONCURRENCY] coroutines writing one map,
                            // and the scope they run on is the caller's rather than one this class
                            // chose. On the app's main dispatcher the guard costs nothing and is
                            // unnecessary; on any other it is the difference between a map and a
                            // ConcurrentModificationException on a background thread.
                            synchronized(barsBySymbol) { barsBySymbol[symbol] = loaded }
                        }
                        // Republished per market rather than once at the end. A map that appears
                        // all at once after twenty seconds is indistinguishable from a map that is
                        // broken, and the reader has no way to tell which one they are looking at
                        // until it finishes.
                        _state.update { it.copy(assets = assetsOf(search.state.value.results)) }
                    }
                }
            }
            _state.update { it.copy(resolving = false) }
            // Cleared before the recursive call, or the guard at the top of `resolve` would decline
            // it — this coroutine *is* the job it would be testing. A catalogue that grew while the
            // pass was running holds markets nobody asked for, and the collector that would have
            // noticed them was declined by that same guard; asking again here is what stops them
            // from staying unknown until the next refresh.
            resolveJob = null
            resolve()
        }
    }

    private companion object {
        /**
         * How many markets have their bars read.
         *
         * Above [HeatmapDensity.STANDARD]'s tile count with room to spare, so drilling into a block
         * or raising the density lands on figures that are already here. Below the size of a real
         * catalogue, because the markets past this point are ones no density setting will give a
         * tile large enough to read.
         */
        const val RESOLUTION_BUDGET = 220

        /** How many candle requests may be in flight at once. `SparklineStore`'s own number. */
        const val CONCURRENCY = 4
    }
}
