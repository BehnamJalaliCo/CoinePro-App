package com.coinepro.core.marketdata

import com.coinepro.core.model.MarketQuote
import com.coinepro.core.symbols.MatchField
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolMatch
import com.coinepro.core.symbols.SymbolMeta
import com.coinepro.core.symbols.SymbolSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
)

data class MarketSearchState(
    val query: String = "",
    val category: SymbolCategory? = null,
    val results: List<MarketSearchRow> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    /** How many markets the catalogue holds — worth showing, since the answer used to be eight. */
    val catalogSize: Int = 0,
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
 */
class MarketSearchController(
    private val gateway: MarketCatalogGateway,
    private val scope: CoroutineScope,
    private val quotesOf: () -> Map<String, MarketQuote> = { emptyMap() },
) {
    private val _state = MutableStateFlow(MarketSearchState())
    val state: StateFlow<MarketSearchState> = _state.asStateFlow()

    private var catalog: List<SymbolMeta> = emptyList()
    private var catalogQuotes: Map<String, MarketQuote> = emptyMap()
    private var loadJob: Job? = null
    private var queryJob: Job? = null

    /** Load the catalogue once. Called when the search surface opens. */
    fun start() {
        if (catalog.isNotEmpty() || loadJob?.isActive == true) return
        refresh()
    }

    fun refresh() {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = scope.launch {
            runCatching { gateway.load() }
                .onSuccess { loaded ->
                    catalog = loaded.markets
                    catalogQuotes = loaded.quotes
                    _state.update { it.copy(loading = false, error = null, catalogSize = catalog.size) }
                    recompute()
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(loading = false, error = failure.message ?: "بازارها در دسترس نیستند")
                    }
                }
        }
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
    )

    /**
     * The live quote if the feed is carrying this symbol, else the one the catalogue arrived with.
     *
     * The live socket only streams what is subscribed, which is a handful; the catalogue carries a
     * price for everything but goes stale from the moment it lands. Preferring the live one means a
     * row a reader is watching ticks, and the rest show a real price rather than a dash.
     */
    private fun quoteFor(meta: SymbolMeta): MarketQuote? =
        quotesOf()[meta.symbol] ?: catalogQuotes[meta.symbol]

    private companion object {
        /**
         * Long enough to skip the intermediate states of a fast typist, short enough that the list
         * still feels attached to the keyboard. Below about 50ms it re-ranks on every key for no
         * benefit; above about 150ms the results visibly lag the text.
         */
        const val DEBOUNCE_MS = 80L
    }
}
