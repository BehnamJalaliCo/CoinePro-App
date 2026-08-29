package com.coinepro.feature.screener

import com.coinepro.core.common.MessageKey
import com.coinepro.core.common.UiMessage
import com.coinepro.core.common.toUiMessage
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.MarketCatalogGateway
import com.coinepro.core.marketdata.MarketSnapshotGateway
import com.coinepro.core.marketdata.MarketTicker
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.symbols.SymbolMeta
import com.coinepro.core.symbols.SymbolRanking
import com.coinepro.feature.screener.model.ScreenerField
import com.coinepro.feature.screener.model.ScreenerFilter
import com.coinepro.feature.screener.model.ScreenerRow
import com.coinepro.feature.screener.model.ScreenerScreen
import com.coinepro.feature.screener.model.ScreenerSort
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * Where a market's recent bars come from.
 *
 * An interface rather than a `CandleGateway` in the controller's constructor, because the screener
 * asks a different question from the chart — one series per market, for hundreds of markets, cached
 * for the life of a screen — and because a test that wants to prove a filter reaches the right rows
 * should not have to fake a paging protocol. [CandleScreenerBarSource] is the real one.
 */
fun interface ScreenerBarSource {
    /**
     * The bars for [symbol], oldest first, or an empty list when they cannot be had.
     *
     * Empty rather than an exception: a symbol one backend does not carry is an ordinary outcome on
     * a mixed catalogue, and a screener that stopped resolving because one market answered 404
     * would show a table that fills in halfway and then never finishes.
     */
    suspend fun bars(symbol: String): List<OhlcBar>
}

/**
 * [ScreenerBarSource] over the app's own candle gateway.
 *
 * Daily bars, because every figure the screener derives is a *day's* figure — the day's high, the
 * day's move, the day's volume — and because a daily series long enough for a two-hundred-period
 * average is still one small response. [LIMIT] is chosen from the longest lookback
 * [ScreenerIndicators] will accept plus room for the average to warm up, not from a round number.
 */
class CandleScreenerBarSource(
    private val gateway: CandleGateway,
    private val timeframe: Timeframe = Timeframe.D1,
    private val limit: Int = LIMIT,
) : ScreenerBarSource {

    override suspend fun bars(symbol: String): List<OhlcBar> =
        runCatching { gateway.load(symbol, timeframe, limit = limit).candles }.getOrDefault(emptyList())

    private companion object {
        /**
         * Enough history for a two-hundred-period average to have a value on the last bar, with a
         * margin. Below this a `SMA_DISTANCE` filter at its longest setting answers null for every
         * market and the screen reads as broken rather than as unwarmed.
         */
        const val LIMIT = 260
    }
}

/**
 * Everything the screener screen draws, as one immutable value.
 *
 * [rows] is already filtered and already sorted. The screen renders it and does no further work of
 * its own — a screen that re-filtered would be a second copy of the rules, and the two would
 * disagree the first time one of them changed.
 */
data class ScreenerState(
    val loading: Boolean = false,
    /**
     * Owned copy, not the exception's own message — the same rule [com.coinepro.core.marketdata.MarketSearchController]
     * follows. A reader on a bad connection must not be shown `Unable to resolve host` as product
     * copy in a Persian app.
     */
    val error: UiMessage? = null,
    /** How many markets the catalogue holds. The denominator of the progress line. */
    val universeSize: Int = 0,
    val filters: List<ScreenerFilter> = emptyList(),
    val sort: ScreenerSort = ScreenerSort.DEFAULT,
    val columns: List<ScreenerField> = ScreenerField.DEFAULT_COLUMNS,
    val rows: List<ScreenerRow> = emptyList(),
    /** The reader's saved screens. Unlimited, free, and never gated. */
    val saved: List<ScreenerScreen> = emptyList(),
    /** Which saved screen the current filter set came from, or null once it has been edited. */
    val activeScreenId: String? = null,
    /** How many markets have had their day read. See [ScreenerMetrics] for where that comes from. */
    val resolvedCount: Int = 0,
    /** True while figures are still being fetched, so the count on screen can say it is not final. */
    val resolving: Boolean = false,
    /**
     * How many markets were left out because a condition could not be decided about them.
     *
     * The table's answer to the heat map's hatched tile. A market with no volume figure is not a
     * market that traded less than the reader asked for, and dropping it into the same silence as
     * one that genuinely failed the threshold is a screener quietly editing somebody's list. The
     * screen prints this under the match count whenever it is not zero, so the unknown is visible
     * as an unknown rather than as an absence.
     */
    val unknownCount: Int = 0,
    /**
     * Whether anything resolved so far reports a volume column.
     *
     * Decides which indicators the filter sheet may offer — see [ScreenerIndicatorCatalog]. It is
     * a fact about the *feed* rather than about one market, and it starts false: before any bar has
     * been read the honest answer is that we have not seen volume, and offering a money-flow filter
     * that every row would then decline is worse than offering it a second later. The MT5 forex
     * side reports none at all, and there the fourteen volume studies stay withheld with a reason
     * printed under the picker rather than silently scoring every market as zero.
     */
    val feedHasVolume: Boolean = false,
) {
    /** How many markets passed. The number the screen prints in Persian digits. */
    val matchCount: Int get() = rows.size

    /**
     * The extra columns this screen's indicator conditions put on the table — [115].
     *
     * Derived rather than stored, so it cannot drift from the conditions it describes. See
     * [ScreenerIndicatorColumn] for why a condition earns a column at all: a filter on an indicator
     * the table does not show is a filter a reader cannot check, correct, or sort by.
     */
    val indicatorColumns: List<ScreenerIndicatorColumn>
        get() = ScreenerIndicatorColumn.of(filters, columns)

    /** True where the screen has finished, has not failed, and still has nothing to show. */
    val empty: Boolean get() = rows.isEmpty() && !loading && error == null

    /** True where the reader has narrowed the list at all, which decides the empty copy. */
    val narrowed: Boolean get() = filters.isNotEmpty()

    /** The current working set as a screen, ready to be saved under [name]. */
    fun asScreen(id: String, name: String): ScreenerScreen =
        ScreenerScreen(id = id, name = name, filters = filters, sort = sort, columns = columns)
}

/**
 * The screener — [108], and with [ScreenerFilter.IndicatorFilter] behind it, [109].
 *
 * A plain controller rather than an Android `ViewModel`, which is what every other controller in
 * this app is: state in a `StateFlow`, work on an injected [CoroutineScope], and no dependency on a
 * lifecycle owner. That is what lets the filtering be tested with no Robolectric and no main-thread
 * dispatcher, and it is the shape `MarketSearchController` established.
 *
 * ### The one hard problem, and how it is answered
 *
 * A phone screener has to filter a catalogue of a thousand markets on figures that the snapshot
 * endpoint does not carry. Where the platform serves the day's table, all of them — the high, the
 * low, the volume, the turnover and the change — arrive for the whole catalogue in one request, and
 * this problem is simply gone: opening the screen costs the catalogue, that table, and the prices of
 * the rows in view. Where it does not, those figures still come from each market's own bars at one
 * request apiece, and fetching a thousand before the first row draws is not a screener but an outage
 * of your own making.
 *
 * So the bar path is bounded in four ways, and each is a decision rather than a tuning constant:
 *
 * * **Only what the table cannot answer.** A market the day's table covers is not asked for its
 *   candles at all unless the screen carries an indicator condition, which is the one thing a
 *   twenty-four-hour rollup cannot answer. That is the difference between opening the screener for
 *   one request and opening it for a hundred and twenty.
 * * **Only what will be read.** Indicator readings are computed for the keys this screen's filters
 *   and columns actually name. A screen with no RSI column and no RSI filter never computes one.
 * * **Only as far as the reader can get.** [RESOLUTION_BUDGET] markets are resolved per pass, taken
 *   in liquidity order after the filters that need no bars have already narrowed the list. A reader
 *   scrolling past the budget extends it by scrolling — the visible window is always resolved
 *   first.
 * * **Once per symbol, ever, per run.** A market whose bars failed is not retried on the next
 *   recomposition. `SparklineStore` learned this the same way: a failing endpoint would otherwise
 *   turn a scroll into a denial of service against our own server.
 *
 * ### Quotes are polled for the visible rows and nothing else
 *
 * [setVisible] is the whole subscription. A table of forty visible rows costs one snapshot request
 * carrying forty symbols; the eight hundred rows below the fold cost nothing at all until they are
 * scrolled to. This is the single most important thing in the class and it is asserted by a test,
 * because it is also the easiest thing to lose — passing `rows` instead of `visible` to one call
 * would work perfectly on a fixture of ten markets and melt a phone on a real catalogue.
 *
 * ### Nothing here blocks
 *
 * Every network call is inside a `launch` on the injected scope. The public methods all return
 * immediately, so a filter chip is never a frame the reader waits through.
 */
class ScreenerController(
    private val gateway: MarketCatalogGateway,
    private val scope: CoroutineScope,
    /**
     * Live prices for the visible rows. Null on a build with no snapshot endpoint, in which case
     * the catalogue's own prices are used and simply do not tick.
     */
    private val quotes: MarketSnapshotGateway? = null,
    /** Bars, for every figure the snapshot does not carry. Null limits the screener to price. */
    private val barSource: ScreenerBarSource? = null,
    /**
     * The day's figures for the whole catalogue, followed rather than fetched.
     *
     * Wired on TradeYar, which serves the route, and on CoinePro-FX, which does not: the source
     * there answers with an empty map and everything below carries on reading candles exactly as it
     * did before. Null is the third case — a preview, or a caller with no market data at all — and
     * behaves like the second.
     */
    private val tickers: ScreenerTickerSource? = null,
    /** Saved screens. Null on a surface with no persistence, such as a preview. */
    private val store: ScreenerStore? = null,
    /**
     * Where indicator arithmetic runs.
     *
     * Not the caller's thread, and this is the whole of the cost decision the class note promises.
     * One reading is a pass over up to two hundred and sixty bars; a scan is four hundred markets
     * times the conditions on the screen, and it is redone whenever the reader adds a filter. On
     * the main dispatcher that is a table that stops scrolling while it filters. Injected rather
     * than hard-coded so a test can pass an unconfined dispatcher and stay deterministic.
     */
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val _state = MutableStateFlow(ScreenerState())
    val state: StateFlow<ScreenerState> = _state.asStateFlow()

    /** The catalogue, in liquidity order, so "the head of the list" means "the largest markets". */
    private var universe: List<SymbolMeta> = emptyList()

    private val quoteBySymbol = mutableMapOf<String, MarketQuote>()
    private val barsBySymbol = mutableMapOf<String, List<OhlcBar>>()
    private val tickerBySymbol = mutableMapOf<String, MarketTicker>()
    private val rowBySymbol = linkedMapOf<String, ScreenerRow>()

    /** Symbols whose bars have been asked for, whether or not an answer came. See the class note. */
    private val asked = mutableSetOf<String>()

    /**
     * Indicator readings, by symbol and then by normalised key — so, per (symbol, indicator, period).
     *
     * The cache the class note's third bound is made of. A market's daily bars do not change during
     * a scan, so neither does its fourteen-bar RSI, and recomputing it on every rebuild would mean
     * four hundred markets re-reduced each time a chip is tapped or a quote ticks. Cleared only by
     * [refresh], which is also when the bars behind it are thrown away — a cache outliving its
     * inputs is the one way this could report a stale number, and the two are dropped together.
     */
    private val readings = mutableMapOf<String, MutableMap<String, Double?>>()

    /** The window the list is showing, as symbols. Empty before the first layout pass. */
    private var visible: List<String> = emptyList()

    private var loadJob: Job? = null
    private var pollJob: Job? = null
    private var tickerJob: Job? = null
    private var resolveJob: Job? = null
    private val resolveGate = Semaphore(RESOLUTION_CONCURRENCY)

    /**
     * True from the moment the day's table is asked for until the platform has answered once.
     *
     * The candle pass waits on this rather than racing it. Both requests go out together, and on a
     * platform that serves the table the answer makes a hundred and twenty candle requests
     * unnecessary — so starting them a few hundred milliseconds early would spend them on figures
     * that were already on their way for free. The wait is bounded by the source's own contract:
     * every implementation emits once on every platform, including one where the route does not
     * exist and one where the request failed. See [ScreenerTickerSource].
     */
    private var awaitingTickers = false

    /** Loads the catalogue once, follows the day's table, and starts the quote poll. */
    fun start() {
        val saved = store
        if (saved != null) {
            scope.launch {
                saved.screens.collect { screens -> _state.update { it.copy(saved = screens) } }
            }
        }
        followTickers()
        if (pollJob == null && quotes != null) {
            pollJob = scope.launch {
                while (isActive) {
                    delay(POLL_MS)
                    pollVisibleQuotes()
                }
            }
        }
        if (universe.isNotEmpty() || loadJob?.isActive == true) return
        refresh()
    }

    /** Re-reads the catalogue. The pull-to-refresh target, and the retry on the error state. */
    fun refresh() {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = scope.launch {
            runCatching { gateway.load() }
                .onSuccess { catalog ->
                    // Sorted once, here, rather than on every recompute: this order is what
                    // "resolve the most important markets first" means, and it does not change.
                    universe = catalog.markets.sortedBy { SymbolRanking.rank(it) }
                    quoteBySymbol.putAll(catalog.quotes)
                    // The readings are answers about bars, and a fresh catalogue is a fresh scan.
                    // Dropped together so a cached number can never outlive the series it came from.
                    readings.clear()
                    rebuild(universe.map(SymbolMeta::symbol))
                    _state.update {
                        it.copy(loading = false, error = null, universeSize = universe.size)
                    }
                    recompute()
                    scheduleResolution()
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = failure.toUiMessage(MessageKey.MARKETS_UNAVAILABLE),
                        )
                    }
                }
        }
    }

    /**
     * Stops the quote poll and lets go of the day's table.
     *
     * Cancelling [tickerJob] is what lowers the shared store's reference count — the source attaches
     * that to its own collection — so a screener the reader has left does not keep a five-second
     * request against the whole catalogue running behind them.
     */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
        tickerJob?.cancel()
        tickerJob = null
        awaitingTickers = false
    }

    /**
     * Follows the day's table for as long as the screen is open.
     *
     * One request answers every market, and then the store behind it re-reads at the server's own
     * cache interval — so this is a subscription rather than a fetch, and «تغییر روزانه» keeps
     * moving instead of freezing at whatever it was when the screen opened while the price column
     * beside it went on ticking.
     *
     * An empty answer is folded in as nothing rather than as a wipe. It means one of two things —
     * this platform has no such route, or the request failed — and neither is a reason to take
     * figures off a table the reader is reading. What it does do is release the candle pass, which
     * has been holding for exactly this answer.
     */
    private fun followTickers() {
        val source = tickers ?: return
        if (tickerJob?.isActive == true) return
        awaitingTickers = true
        tickerJob = scope.launch {
            source.tickers().collect { table ->
                awaitingTickers = false
                if (table.isNotEmpty()) {
                    // Replaced rather than merged: the table is the whole of what the platform
                    // knows, so a market it stopped carrying has to stop carrying figures too.
                    tickerBySymbol.clear()
                    tickerBySymbol.putAll(table)
                    rebuild(rowBySymbol.keys.toList())
                    recompute()
                }
                scheduleResolution()
            }
        }
    }

    // ── the filter set ──────────────────────────────────────────────────────────────────────

    /** Replaces the whole condition list, which is what the filter sheet's one primary action does. */
    fun setFilters(filters: List<ScreenerFilter>) {
        if (_state.value.filters == filters) return
        _state.update { it.copy(filters = filters, activeScreenId = null) }
        onRequirementsChanged()
    }

    fun addFilter(filter: ScreenerFilter) = setFilters(_state.value.filters + filter)

    /** Removes the condition at [index]. Out of range is a no-op rather than a crash. */
    fun removeFilter(index: Int) {
        val current = _state.value.filters
        if (index !in current.indices) return
        setFilters(current.filterIndexed { at, _ -> at != index })
    }

    fun clearFilters() = setFilters(emptyList())

    /** Moves the sort, or flips it when the reader taps the column it is already on. */
    fun toggleSort(field: ScreenerField) {
        _state.update { it.copy(sort = it.sort.toggled(field)) }
        recompute()
    }

    /**
     * The same, for one of the indicator columns a condition put on the table.
     *
     * Sorting reads what is already in each row and asks for nothing new, so this is as cheap as
     * the field version — the readings were computed when the condition was added.
     */
    fun toggleIndicatorSort(key: String) {
        _state.update { it.copy(sort = it.sort.toggledIndicator(key)) }
        recompute()
    }

    fun setSort(sort: ScreenerSort) {
        if (_state.value.sort == sort) return
        _state.update { it.copy(sort = sort) }
        recompute()
    }

    /** Chooses the columns. A column that needs an indicator makes the screener resolve it. */
    fun setColumns(columns: List<ScreenerField>) {
        if (_state.value.columns == columns) return
        _state.update { it.copy(columns = columns) }
        onRequirementsChanged()
    }

    // ── saved screens ───────────────────────────────────────────────────────────────────────

    /** Adopts a saved screen or a preset — its filters, its sort and its columns, all at once. */
    fun apply(screen: ScreenerScreen) {
        _state.update {
            it.copy(
                filters = screen.filters,
                sort = screen.sort,
                columns = screen.columns,
                activeScreenId = screen.id,
            )
        }
        onRequirementsChanged()
    }

    /**
     * Saves the current working set under [name].
     *
     * No cap, no membership check, no "you have used your free screen". The id is derived from the
     * clock so a rename cannot orphan the record; a blank name is refused because a row in a list
     * with no label is not something a reader can choose again.
     */
    fun save(name: String) {
        val store = store ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val screen = _state.value.asScreen(
            id = _state.value.activeScreenId ?: "screen_" + System.currentTimeMillis().toString(),
            name = trimmed,
        )
        _state.update { it.copy(activeScreenId = screen.id) }
        scope.launch { store.save(screen) }
    }

    /** Deletes a saved screen. Nothing is protected; the presets are copies, not records. */
    fun delete(id: String) {
        val store = store ?: return
        scope.launch { store.delete(id) }
        if (_state.value.activeScreenId == id) {
            _state.update { it.copy(activeScreenId = null) }
        }
    }

    // ── the visible window ──────────────────────────────────────────────────────────────────

    /**
     * Tells the controller which rows the reader can actually see.
     *
     * Called from the list as it scrolls, with the first and last index the layout reports. The
     * indices are clamped and a window that has not changed is dropped, so a scroll that moves
     * within one row does not re-poll.
     *
     * This is what makes the quote poll affordable — see the class note — and it is also what gives
     * the bar resolver its priority order, because the rows a reader is looking at are the ones
     * whose figures they are waiting for.
     */
    fun setVisible(first: Int, last: Int) {
        val rows = _state.value.rows
        if (rows.isEmpty()) return
        val from = first.coerceIn(0, rows.lastIndex)
        val to = last.coerceIn(from, rows.lastIndex)
        val window = rows.subList(from, to + 1).map(ScreenerRow::symbol)
        if (window == visible) return
        visible = window
        scheduleResolution()
    }

    /**
     * One poll of the visible rows' prices.
     *
     * Internal rather than private so a test can drive it without a virtual clock, which is what
     * proves the request carries the visible symbols and nothing else.
     */
    internal suspend fun pollVisibleQuotes() {
        val gateway = quotes ?: return
        val window = visible
        if (window.isEmpty()) return
        val snapshot = runCatching { gateway.load(window) }.getOrNull() ?: return
        if (snapshot.quotes.isEmpty()) return
        snapshot.quotes.forEach { quote -> quoteBySymbol[quote.instrument.symbol] = quote }
        rebuild(snapshot.quotes.map { it.instrument.symbol })
        recompute()
    }

    // ── the machinery ───────────────────────────────────────────────────────────────────────

    /**
     * What has to change when the filters or the columns do.
     *
     * The indicator keys a screen needs are derived from both, so a new RSI column and a new RSI
     * filter both invalidate every row that already holds indicator readings. Rebuilding only the
     * rows that have bars keeps that bounded to what has actually been resolved.
     */
    private fun onRequirementsChanged() {
        val symbols = barsBySymbol.keys.toList()
        // The rows are rebuilt from what is already cached first, so the table reacts to the chip
        // in the same frame it was tapped. Anything the new condition needs and nobody has computed
        // yet lands a moment later, off the main thread. The alternative — waiting for the whole
        // reduction before showing anything — is a filter control that feels broken on a catalogue
        // of four hundred markets.
        rebuild(symbols)
        recompute()
        scope.launch {
            if (warm(symbols)) {
                rebuild(symbols)
                recompute()
            }
            scheduleResolution()
        }
    }

    /**
     * Rebuilds the given rows from whatever is currently known about them.
     *
     * Cheap on purpose, and safe to call from the main thread: it reads the [readings] cache and
     * never computes an indicator. [warm] is the half that costs something, and it runs elsewhere.
     */
    private fun rebuild(symbols: Collection<String>) {
        if (symbols.isEmpty()) return
        val keys = requiredIndicatorKeys()
        val known = universe.associateBy(SymbolMeta::symbol)
        symbols.forEach { symbol ->
            val meta = known[symbol] ?: return@forEach
            val cached = readings[symbol]
            rowBySymbol[symbol] = ScreenerMetrics.rowOf(
                meta = meta,
                quote = quoteBySymbol[symbol],
                bars = barsBySymbol[symbol].orEmpty(),
                ticker = tickerBySymbol[symbol],
                indicators = if (cached == null || keys.isEmpty()) {
                    emptyMap()
                } else {
                    // Only the keys this screen names. A cache entry left over from a condition the
                    // reader has since removed must not reach the row, or a column removed from the
                    // screen would go on being sortable by a value nothing shows.
                    keys.mapNotNull { key -> cached[key]?.let { key to it } }.toMap()
                },
            )
        }
    }

    /**
     * Computes whatever [symbols] are missing for the screen's current conditions, off the caller's
     * thread, and files it in [readings].
     *
     * @return true when anything new was computed, so the caller can skip a rebuild that would
     *   produce exactly the rows it already published.
     *
     * The work is bounded twice over before it starts: by the key set, which is only what the
     * filters and columns actually name, and by the cache, which means a market is reduced once per
     * (symbol, indicator, period) for the life of a scan however many times the table is rebuilt.
     * What is left is genuinely expensive — four hundred markets over two hundred and sixty bars —
     * and that is why it is inside [withContext] rather than beside the call that publishes state.
     */
    private suspend fun warm(symbols: Collection<String>): Boolean {
        val keys = requiredIndicatorKeys()
        if (keys.isEmpty() || symbols.isEmpty()) return false
        val outstanding = symbols.mapNotNull { symbol ->
            val bars = barsBySymbol[symbol] ?: return@mapNotNull null
            val cached = readings[symbol]
            val missing = if (cached == null) keys else keys.filterNot(cached::containsKey).toSet()
            if (missing.isEmpty()) null else Triple(symbol, bars, missing)
        }
        if (outstanding.isEmpty()) return false
        val computed = withContext(computeDispatcher) {
            outstanding.map { (symbol, bars, missing) ->
                symbol to ScreenerIndicators.computeAll(missing, bars)
            }
        }
        val requested = outstanding.associate { (symbol, _, missing) -> symbol to missing }
        computed.forEach { (symbol, values) ->
            val into = readings.getOrPut(symbol) { mutableMapOf() }
            // Every key that was asked for is written, including the ones that came back with no
            // answer. See the note on [readings] for why recording the absence matters.
            requested[symbol].orEmpty().forEach { key -> into[key] = values[key] }
        }
        return true
    }

    /** Filters, sorts, publishes, and asks for whatever the next pass needs. */
    private fun recompute() {
        val current = _state.value
        val all = rowBySymbol.values.toList()
        // Counted in the same pass that filters, because the two answers are about the same rows
        // and a second walk over a catalogue of a thousand on every quote tick is a walk too many.
        var unknown = 0
        val matched = all.filter { row ->
            val kept = ScreenerFilter.allMatch(current.filters, row)
            if (!kept && ScreenerFilter.anyUndecided(current.filters, row)) unknown += 1
            kept
        }
        _state.update {
            it.copy(
                rows = it.sort.apply(matched),
                resolvedCount = all.count(ScreenerRow::resolved),
                unknownCount = unknown,
                // One market with a volume figure proves the feed carries the column. `any` and
                // not `all`: the catalogue is mixed, and one crypto pair reporting volume is enough
                // to make a money-flow filter worth offering.
                feedHasVolume = it.feedHasVolume || all.any { row -> row.volume != null },
            )
        }
    }

    /**
     * Reads the day's bars for the markets the screen is about to need, and for no others.
     *
     * The order is deliberate: whatever is on screen first, then the head of the candidate list.
     * "Candidate" means a market that survives the filters that can be answered without bars — a
     * text match, a category, a price threshold — because resolving a market the reader has already
     * excluded by name is a request spent on a row that will never be drawn.
     *
     * A market the day's table already answered for is skipped outright unless the screen carries
     * an indicator condition. That is the whole of the saving the table brought: the default screen
     * — price, the day's move, volume — now costs the catalogue and one table, where it used to cost
     * a hundred and twenty candle series to derive figures the venue had already computed. An
     * indicator is the one thing a twenty-four-hour rollup structurally cannot answer, so the moment
     * a reader adds an RSI condition the candles are fetched exactly as they always were.
     */
    private fun scheduleResolution() {
        val source = barSource ?: return
        if (resolveJob?.isActive == true) return
        // The day's table is on its way and may make this pass unnecessary. See [awaitingTickers].
        if (awaitingTickers) return

        // Hoisted rather than asked per symbol: it is the same answer for every market in the pass,
        // and it builds a set each time it is called.
        val needsSeries = requiredIndicatorKeys().isNotEmpty()
        val cheap = _state.value.filters.filter(::answerableWithoutBars)
        val candidates = buildList {
            addAll(visible)
            rowBySymbol.values.forEach { row ->
                if (ScreenerFilter.allMatch(cheap, row)) add(row.symbol)
            }
        }
        val wanted = candidates.asSequence()
            .distinct()
            .filterNot(asked::contains)
            .filter { symbol -> needsSeries || symbol !in tickerBySymbol }
            .take(RESOLUTION_BUDGET)
            .toList()
        if (wanted.isEmpty()) {
            if (_state.value.resolving) _state.update { it.copy(resolving = false) }
            return
        }

        asked.addAll(wanted)
        _state.update { it.copy(resolving = true) }
        resolveJob = scope.launch {
            coroutineScope {
                wanted.forEach { symbol ->
                    launch {
                        resolveGate.withPermit {
                            val bars = source.bars(symbol)
                            if (bars.isNotEmpty()) barsBySymbol[symbol] = bars
                        }
                    }
                }
            }
            warm(wanted)
            rebuild(wanted)
            _state.update { it.copy(resolving = false) }
            recompute()
            // Only for a window that scrolled in while this pass was running. Deliberately not an
            // unconditional re-schedule: that would walk the whole catalogue a budget at a time and
            // undo the bound the budget exists to impose.
            if (visible.any { it !in asked }) scheduleResolution()
        }
    }

    /**
     * Whether a filter can decide a market without its bars.
     *
     * A name and a category are known from the catalogue, and so is the price. The day's move, the
     * volume and the rest are known too wherever the day's table answered for them — and there
     * counting them as cheap is what stops a candle request being spent on a market the reader's
     * own threshold has already excluded.
     *
     * Where there is no table they are not known, and treating one of them as cheap would narrow the
     * candidate list using values that are all still null: a screener that resolves nothing and
     * therefore shows nothing. An indicator is never cheap on either platform, because nothing but
     * the series can answer it.
     */
    private fun answerableWithoutBars(filter: ScreenerFilter): Boolean = when (filter) {
        is ScreenerFilter.TextMatch, is ScreenerFilter.Category -> true
        is ScreenerFilter.Numeric ->
            filter.field == ScreenerField.LAST_PRICE ||
                (tickerBySymbol.isNotEmpty() && !filter.field.isDerived)
        is ScreenerFilter.IndicatorFilter -> false
    }

    /** Every indicator reading this screen's filters and columns name, as normalised keys. */
    private fun requiredIndicatorKeys(): Set<String> {
        val current = _state.value
        return ScreenerFilter.indicatorKeys(current.filters) +
            current.columns.mapNotNull(ScreenerField::indicatorKey)
    }

    private companion object {
        /**
         * How often the visible rows' prices are re-read.
         *
         * Four seconds, which is slower than the chart's socket and deliberately so: a screener is
         * read by scanning a column, not by watching one number, and a table that reflowed its sort
         * order every second would be unreadable while it did.
         */
        const val POLL_MS = 4_000L

        /**
         * How many markets have their bars read per pass.
         *
         * A hundred and twenty is roughly three screenfuls past wherever the reader has scrolled to,
         * which is enough that the table is always full ahead of the thumb and small enough that the
         * first pass finishes in a few seconds on a phone connection. Scrolling further asks for
         * more; nothing here is a limit on what can eventually be filtered.
         */
        const val RESOLUTION_BUDGET = 120

        /** How many bar requests may be in flight. The number `SparklineStore` settled on. */
        const val RESOLUTION_CONCURRENCY = 4
    }
}
