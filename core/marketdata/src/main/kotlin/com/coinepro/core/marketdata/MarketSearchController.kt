package com.coinepro.core.marketdata

import com.coinepro.core.common.MessageKey
import com.coinepro.core.common.UiMessage
import com.coinepro.core.common.toUiMessage
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.symbols.MatchField
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolMatch
import com.coinepro.core.symbols.SymbolMeta
import com.coinepro.core.symbols.SymbolSearch
import com.coinepro.core.symbols.SymbolUniverse
import com.coinepro.core.symbols.UniverseSymbol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row of results: the market, its price if we have one, and where the query hit. */
data class MarketSearchRow(
    val meta: SymbolMeta,
    val quote: MarketQuote?,
    val field: MatchField,
    /** The span of [SymbolMeta.symbol] or [SymbolMeta.description] that matched, when contiguous. */
    val highlight: IntRange?,
    /**
     * The listing this row came from — venue, turnover, tick size, status.
     *
     * Null for a row the snapshot produced and the universe never named, which on a venue with no
     * `v1/symbols` is most of them. A screen reads it for the filter sheet's venue and turnover
     * columns and must treat null as «not said», never as zero: see `SymbolUniverseGateway`.
     */
    val listing: UniverseSymbol? = null,
) {
    /** What the row is worth in a day, where anything said so. */
    val turnover24h: Double? get() = listing?.turnover24h

    /** Which venue quotes it, or null where nothing named one. */
    val venue: String? get() = listing?.venue
}

data class MarketSearchState(
    val query: String = "",
    val category: SymbolCategory? = null,
    val results: List<MarketSearchRow> = emptyList(),
    val loading: Boolean = false,
    /**
     * Owned copy, not the exception's own message.
     *
     * This used to be `failure.message`, so a reader on a bad connection was shown
     * `Unable to resolve host "api…"` or `HTTP 404 Not Found` as product copy — an English
     * platform string, in a Persian app, that nobody can act on either way.
     */
    val error: UiMessage? = null,
    /** How many markets the catalogue holds — worth showing, since the answer used to be eight. */
    val catalogSize: Int = 0,
    /**
     * Every price the catalogue arrived with, keyed by ticker in upper case.
     *
     * Published because [results] is not a catalogue — it is whatever the current query and
     * category leave standing — and a caller that needs "the price of this one symbol" cannot ask
     * a filtered list for it. The chart's watchlist strip is the caller that needed it: the live
     * socket carries eight markets and a reader can star any of nine hundred, so a strip fed from
     * the socket alone drew a logo, a ticker and two empty columns for almost everything on it.
     *
     * It changes when the catalogue is reloaded and not when a key is pressed, which is what makes
     * it safe to read from the shell.
     */
    val catalogueQuotes: Map<String, MarketQuote> = emptyMap(),
) {
    val searching: Boolean get() = query.isNotBlank()
    val empty: Boolean get() = searching && results.isEmpty() && !loading && error == null
}

/**
 * Search over everything a platform quotes.
 *
 * Three things here are deliberate and each replaces something the app did worse.
 *
 * **The catalogue is loaded, not declared.** It comes from [MarketCatalogGateway], so a market the
 * backend added is searchable without an app release.
 *
 * **Typing is debounced, filtering is not.** A keystroke waits [DEBOUNCE_MS] before re-ranking a
 * thousand rows; switching a category chip re-ranks immediately, because that is a deliberate act
 * with no more input coming. Debouncing a chip would make it feel broken.
 *
 * **Ranking lives in `core:symbols`.** This class holds state and does no scoring of its own, so
 * the ordering can be tested without a coroutine, a gateway or a screen.
 *
 * **The prices on the rows are the socket's, as they arrive.** See [liveQuotes].
 */
class MarketSearchController(
    private val gateway: MarketCatalogGateway,
    private val scope: CoroutineScope,
    /**
     * The live feed, if this build has one.
     *
     * ### Why it is a flow and not a lambda
     *
     * It used to be `() -> Map<String, MarketQuote>`, read **once per re-rank** — on a keystroke, a
     * category chip, a catalogue reload. Nothing re-ranks while a reader is looking at a list, so
     * every row on the watchlist and on the markets list held whatever the price had been at the
     * moment the list was built and did not move again: «قیمت‌ها باید لحظه‌ای باشند». The socket was
     * delivering ticks the whole time and this class was the place they stopped.
     *
     * A flow is collected for the life of the controller, so a tick becomes a new row the frame it
     * lands. There is no interval here to tune and deliberately so — the feed is a push socket, and
     * the fastest this can be is *whatever the server sends*, with nothing in between sampling it.
     *
     * Null is a real answer: the guest build has no authenticated socket, and its catalogue prices
     * simply do not tick.
     */
    private val liveQuotes: Flow<Map<String, MarketQuote>>? = null,
    /**
     * The whole universe, where this build can ask for one (F1).
     *
     * The catalogue answers «what is quoted right now», which on CoinePro-FX is nineteen symbols.
     * The universe answers «what markets are there», and the difference is the difference between a
     * markets tab and a watchlist. Both are loaded; the universe supplies the rows the catalogue
     * never mentioned and the catalogue supplies their prices.
     *
     * Null is a real answer — the guest shell has no gateway to hand — and this class then behaves
     * exactly as it did before the universe existed.
     */
    private val universe: SymbolUniverseGateway? = null,
) {
    /**
     * What the reader said they think in, from the starter screen (run ΤΦΥ, U2).
     *
     * Applied to the **universe** and nowhere else: it decides which of two listings of one asset
     * on one venue reaches the list, and it never converts a figure. Null — and the default on
     * every platform either backend serves today — leaves every listing where it is. See
     * `SymbolUniverse.preferring`.
     *
     * A settable property rather than a constructor argument because this object is a singleton
     * that outlives every screen and the answer is a stored preference the reader can change at
     * MENU → ظاهر. The shell writes it and calls [refresh]; a flow collected here would give this
     * object a lifecycle it does not have.
     */
    var preferredQuote: String? = null

    private val _state = MutableStateFlow(MarketSearchState())
    val state: StateFlow<MarketSearchState> = _state.asStateFlow()

    private var catalog: List<SymbolMeta> = emptyList()
    private var catalogQuotes: Map<String, MarketQuote> = emptyMap()

    /** The universe by ticker, so a row can be given its listing without a scan. */
    private var listings: Map<String, UniverseSymbol> = emptyMap()
    private var loadJob: Job? = null
    private var queryJob: Job? = null
    private var liveJob: Job? = null

    /** The last thing the socket said, for the rows a re-rank builds from scratch. */
    private var live: Map<String, MarketQuote> = emptyMap()

    /** Load the catalogue once, and start listening to the feed. Called when a surface opens. */
    fun start() {
        listen()
        if (catalog.isNotEmpty() || loadJob?.isActive == true) return
        refresh()
    }

    /**
     * Collect the feed, patching the price on the rows already built.
     *
     * Only the quote is replaced — never the ranking, the query or the order. A tick is new
     * information about one market, not a reason to rearrange a list under a reader's thumb.
     */
    private fun listen() {
        val source = liveQuotes ?: return
        if (liveJob?.isActive == true) return
        liveJob = scope.launch { source.collect(::applyLive) }
    }

    private fun applyLive(quotes: Map<String, MarketQuote>) {
        live = quotes
        if (quotes.isEmpty()) return
        _state.update { current ->
            var moved = false
            val rows = current.results.map { row ->
                val fresh = quotes[row.meta.symbol] ?: return@map row
                if (fresh == row.quote) {
                    row
                } else {
                    moved = true
                    row.copy(quote = fresh)
                }
            }
            // Identity, not a copy, when nothing changed: a `StateFlow` set to an equal value is
            // cheap, and one set to a *new list of equal rows* is a recomposition of every visible
            // row on the screen for no new information.
            if (moved) current.copy(results = rows) else current
        }
    }

    fun refresh() {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = scope.launch {
            // The universe first, and its failure is not this load's failure: a markets tab with
            // the bundled three hundred in it and no prices is a screen a reader can use, and a
            // markets tab that refuses to draw because one path 404s is not.
            val listed = SymbolUniverse.preferring(
                universe?.let { source -> runCatching { source.load() }.getOrNull() }.orEmpty(),
                preferredQuote,
            )
            listings = listed.associateBy { it.id.uppercase() }
            runCatching { gateway.load() }
                .onSuccess { loaded ->
                    catalog = withUniverse(loaded.markets, listed)
                    catalogQuotes = loaded.quotes
                    val published = loaded.quotes.mapKeys { (ticker, _) -> ticker.uppercase() }
                    _state.update {
                        it.copy(
                            loading = false,
                            error = null,
                            catalogSize = catalog.size,
                            catalogueQuotes = published,
                        )
                    }
                    recompute()
                }
                .onFailure { failure ->
                    // A universe with no prices is still a markets list. The snapshot failing used
                    // to empty the screen; now it empties the *price column*, which is the honest
                    // reading of what actually went wrong.
                    if (listed.isNotEmpty()) {
                        catalog = withUniverse(emptyList(), listed)
                        _state.update {
                            it.copy(loading = false, error = null, catalogSize = catalog.size)
                        }
                        recompute()
                        return@launch
                    }
                    _state.update {
                        it.copy(loading = false, error = failure.toUiMessage(MessageKey.MARKETS_UNAVAILABLE))
                    }
                }
        }
    }

    /**
     * The snapshot's markets, plus every market the universe named that it did not.
     *
     * The snapshot's own row wins where both have one: it is what this venue is quoting today and
     * its classification has already been through the app's own aliases. What the universe adds is
     * the long tail — on a venue answering with nineteen symbols, that is the entire markets tab.
     */
    private fun withUniverse(
        quoted: List<SymbolMeta>,
        listed: List<UniverseSymbol>,
    ): List<SymbolMeta> {
        if (listed.isEmpty()) return quoted
        val known = quoted.mapTo(HashSet(quoted.size)) { it.symbol.uppercase() }
        return quoted + listed.filterNot { it.id.uppercase() in known }.map { it.meta }
    }

    fun setQuery(query: String) {
        if (_state.value.query == query) return
        _state.update { it.copy(query = query) }
        queryJob?.cancel()
        // An empty box is not a query — it is the browse list, and it should come back instantly
        // rather than a tenth of a second after the last character is deleted.
        if (query.isBlank()) {
            recompute()
            return
        }
        queryJob = scope.launch {
            delay(DEBOUNCE_MS)
            recompute()
        }
    }

    fun setCategory(category: SymbolCategory?) {
        if (_state.value.category == category) return
        _state.update { it.copy(category = category) }
        queryJob?.cancel()
        recompute()
    }

    private fun recompute() {
        val current = _state.value
        val matches = SymbolSearch.search(catalog, current.query, current.category)
        _state.update { it.copy(results = matches.map(::row)) }
    }

    private fun row(match: SymbolMatch) = MarketSearchRow(
        meta = match.meta,
        quote = quoteFor(match.meta),
        field = match.field,
        highlight = match.range,
        listing = listings[match.meta.symbol.uppercase()],
    )

    /**
     * The live quote if the feed is carrying this symbol, else the one the catalogue arrived with.
     *
     * The live socket only streams what is subscribed, which is a handful; the catalogue carries a
     * price for everything but goes stale from the moment it lands. Preferring the live one means a
     * row a reader is watching ticks, and the rest show a real price rather than a dash.
     */
    private fun quoteFor(meta: SymbolMeta): MarketQuote? =
        live[meta.symbol] ?: catalogQuotes[meta.symbol]

    private companion object {
        /**
         * The brief's number (F3), and the catalogue it now ranks is why it went up from eighty.
         *
         * Eighty was measured against a few hundred rows. Since F1 this searches the whole universe
         * — the venue's list *plus* the bundled three hundred, thousands of rows on LBank — and a
         * re-rank of that on every keystroke of a fast typist is work thrown away before anybody
         * sees it. Two hundred milliseconds is one word of typing, not one letter.
         */
        const val DEBOUNCE_MS = 200L
    }
}
