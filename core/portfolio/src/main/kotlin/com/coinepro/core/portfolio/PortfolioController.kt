package com.coinepro.core.portfolio

import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How far back the screen is looking. */
enum class PortfolioWindow(val days: Long) {
    WEEK(7),
    MONTH(30),
    /**
     * Everything the server will give.
     *
     * Honest on one platform and a request for the maximum on the other: CoinePro-FX pages a whole
     * broker ledger with no window at all, while TradeYar narrows anything past 31 days and says
     * so. The screen reads the served window back rather than labelling this "all time".
     */
    ALL(0),
}

data class PortfolioUiState(
    val window: PortfolioWindow = PortfolioWindow.MONTH,
    val trades: List<ClosedTrade> = emptyList(),
    val stats: PortfolioStats = PortfolioStats(),
    val bySymbol: List<SymbolPerformance> = emptyList(),
    val byMonth: List<MonthlyPerformance> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: PortfolioError? = null,
    /**
     * The window the server actually served, when it narrowed the one asked for.
     *
     * Null when it served what was requested. Non-null is not an error — it is the screen's cue to
     * say which days these figures cover, because "۳۱ روز اخیر" over a total labelled "۹۰ روز"
     * is worse than either label alone.
     */
    val servedWindow: ClosedRange<Long>? = null,
    /** The server stopped mid-window. Totals below are partial and must be labelled as such. */
    val truncated: Boolean = false,
)

enum class PortfolioError {
    /** The network failed, or the server did. Retrying is the right suggestion. */
    NETWORK,

    /**
     * The reader has no exchange or broker account linked, so there is no ledger to read.
     *
     * Distinct from an empty history: one is "connect an account", the other is "you have not
     * traded yet", and offering the wrong one wastes the reader's time in both directions.
     */
    NOT_CONNECTED,
}

/**
 * The portfolio screen's state machine.
 *
 * Deliberately unchatty. On TradeYar a cold window costs the server about seventeen seconds of
 * paging LBank's order history with the same API key the copy-trade engine signs orders with, so
 * this refreshes when asked and never on a timer.
 */
class PortfolioController(
    private val gateway: PortfolioGateway,
    private val scope: CoroutineScope,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1_000 },
) {

    private val _state = MutableStateFlow(PortfolioUiState())
    val state: StateFlow<PortfolioUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun start() {
        if (_state.value.trades.isEmpty() && job == null && _state.value.error == null) refresh()
    }

    fun setWindow(window: PortfolioWindow) {
        if (window == _state.value.window) return
        _state.update { it.copy(window = window) }
        refresh()
    }

    fun retry() = refresh()

    /**
     * Page further back.
     *
     * Appends and re-derives everything, because the statistics are over the whole held set: a win
     * rate computed per page and averaged would not be a win rate.
     */
    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        scope.launch {
            val (from, to) = boundsFor(current.window)
            runCatching { gateway.history(page = current.page + 1, from = from, to = to) }
                .onSuccess { page -> _state.update { it.append(page) } }
                .onFailure {
                    // The trades already on screen stay. There is nothing useful to say that they
                    // do not already say, and an error banner over real figures reads as though
                    // the figures are wrong.
                    _state.update { it.copy(loadingMore = false) }
                }
        }
    }

    private fun refresh() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = scope.launch {
            val (from, to) = boundsFor(_state.value.window)
            runCatching { gateway.history(page = 1, from = from, to = to) }
                .onSuccess { page ->
                    _state.update {
                        PortfolioUiState(window = it.window).append(page).copy(loading = false)
                    }
                }
                .onFailure { failure ->
                    _state.update {
                        it.copy(loading = false, trades = emptyList(), error = failure.toPortfolioError())
                    }
                }
            job = null
        }
    }

    private fun boundsFor(window: PortfolioWindow): Pair<Long?, Long?> {
        if (window == PortfolioWindow.ALL) return null to null
        val to = nowSeconds()
        return (to - window.days * 86_400L) to to
    }

    private fun PortfolioUiState.append(page: TradeHistoryPage): PortfolioUiState {
        // De-duplicated by id, because a page boundary that shifts between two requests — a trade
        // closing while the reader is scrolling — otherwise counts one trade twice, in the trade
        // count, in the win rate and in the total.
        val merged = (trades + page.trades).distinctBy { it.id }
        return copy(
            trades = merged,
            stats = PortfolioMath.summarise(merged),
            bySymbol = PortfolioMath.bySymbol(merged),
            byMonth = PortfolioMath.byMonth(merged, zone),
            loading = false,
            loadingMore = false,
            hasMore = page.hasMore,
            error = null,
            servedWindow = page.servedWindowOrNull(window, nowSeconds()),
            truncated = truncated || page.truncated,
        )
    }

    private val PortfolioUiState.page: Int
        get() = if (trades.isEmpty()) 0 else 1 + (trades.size - 1) / PortfolioGateway.DEFAULT_PAGE_SIZE
}

/**
 * The served window, but only when it differs from the one asked for.
 *
 * A day of slack rather than an exact comparison: the server's window is anchored on its own clock
 * and this app's on the phone's, so two clocks a few seconds apart would otherwise make every
 * response look narrowed.
 */
internal fun TradeHistoryPage.servedWindowOrNull(window: PortfolioWindow, now: Long): ClosedRange<Long>? {
    val from = windowFrom ?: return null
    val to = windowTo ?: return null
    if (window == PortfolioWindow.ALL) return from..to
    val asked = window.days * 86_400L
    val served = to - from
    return if (asked - served > 86_400L) from..to else null
}

internal fun Throwable.toPortfolioError(): PortfolioError {
    val text = (message ?: "") + (cause?.message ?: "")
    return when {
        text.contains("not_connected") ||
            text.contains("no_api_key") ||
            text.contains("venue_not_connected") -> PortfolioError.NOT_CONNECTED
        else -> PortfolioError.NETWORK
    }
}
