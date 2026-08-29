package com.coinepro.feature.screener

import com.coinepro.core.common.MessageKey
import com.coinepro.core.common.UiMessage
import com.coinepro.core.common.toUiMessage
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.MarketCatalogGateway
import com.coinepro.core.marketdata.MarketSnapshotGateway
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
import kotlinx.coroutines.CoroutineScope
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
    /** How many markets have had their day's bar read. See [ScreenerMetrics] for why this exists. */
    val resolvedCount: Int = 0,
    /** True while bars are still being fetched, so the count on screen can say it is not final. */
    val resolving: Boolean = false,
) {
    /** How many markets passed. The number the screen prints in Persian digits. */
    val matchCount: Int get() = rows.size

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
 * endpoint does not carry. The day's high, low, volume and change all come from a market's own
 * bars, which is one request each. Fetching a thousand before the first row draws is not a
 * screener; it is an outage of your own making.
 *
 * So the work is bounded in three ways, and each is a decision rather than a tuning constant:
 *
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
    /** Saved screens. Null on a surface with no persistence, such as a preview. */
    private val store: ScreenerStore? = null,
) {
    private val _state = MutableStateFlow(ScreenerState())
    val state: StateFlow<ScreenerState> = _state.asStateFlow()

    /** The catalogue, in liquidity order, so "the head of the list" means "the largest markets". */
    private var universe: List<SymbolMeta> = emptyList()

    private val quoteBySymbol = mutableMapOf<String, MarketQuote>()
    private val barsBySymbol = mutableMapOf<String, List<OhlcBar>>()
    private val rowBySymbol = linkedMapOf<String, ScreenerRow>()

    /** Symbols whose bars have been asked for, whether or not an answer came. See the class note. */
    private val asked = mutableSetOf<String>()

    /** The window the list is showing, as symbols. Empty before the first layout pass. */
    private var visible: List<String> = emptyList()

    private var loadJob: Job? = null
    private var pollJob: Job? = null
    private var resolveJob: Job? = null
    private val resolveGate = Semaphore(RESOLUTION_CONCURRENCY)

    /** Loads the catalogue once and starts the quote poll. Called when the screener opens. */
    fun start() {
        val saved = store
        if (saved != null) {
            scope.launch {
                saved.screens.collect { screens -> _state.update { it.copy(saved = screens) } }
            }
        }
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

    /** Stops the quote poll. Called when the screener leaves the composition. */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
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
        rebuild(barsBySymbol.keys.toList())
        recompute()
        scheduleResolution()
    }

    /** Rebuilds the given rows from whatever is currently known about them. */
    private fun rebuild(symbols: Collection<String>) {
        if (symbols.isEmpty()) return
        val keys = requiredIndicatorKeys()
        val known = universe.associateBy(SymbolMeta::symbol)
        symbols.forEach { symbol ->
            val meta = known[symbol] ?: return@forEach
            rowBySymbol[symbol] = ScreenerMetrics.rowOf(
                meta = meta,
                quote = quoteBySymbol[symbol],
                bars = barsBySymbol[symbol].orEmpty(),
                indicatorKeys = keys,
            )
        }
    }

    /** Filters, sorts, publishes, and asks for whatever the next pass needs. */
    private fun recompute() {
        val current = _state.value
        val all = rowBySymbol.values.toList()
        val matched = all.filter { ScreenerFilter.allMatch(current.filters, it) }
        _state.update {
            it.copy(
                rows = it.sort.apply(matched),
                resolvedCount = all.count(ScreenerRow::resolved),
            )
        }
    }

    /**
     * Reads the day's bars for the markets the screen is about to need.
     *
     * The order is deliberate: whatever is on screen first, then the head of the candidate list.
     * "Candidate" means a market that survives the filters that can be answered without bars — a
     * text match, a category, a price threshold — because resolving a market the reader has already
     * excluded by name is a request spent on a row that will never be drawn.
     */
    private fun scheduleResolution() {
        val source = barSource ?: return
        if (resolveJob?.isActive == true) return

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
     * A name and a category are known from the catalogue, and so is the price. Everything else —
     * the day's move, the volume, any indicator — needs the series, and treating one of those as
     * cheap would narrow the candidate list using values that are all still null, which is a
     * screener that resolves nothing and shows nothing.
     */
    private fun answerableWithoutBars(filter: ScreenerFilter): Boolean = when (filter) {
        is ScreenerFilter.TextMatch, is ScreenerFilter.Category -> true
        is ScreenerFilter.Numeric -> filter.field == ScreenerField.LAST_PRICE
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
