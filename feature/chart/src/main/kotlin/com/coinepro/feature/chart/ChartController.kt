package com.coinepro.feature.chart

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ChartLine
import com.coinepro.core.chart.ChartMarker
import com.coinepro.core.chart.ChartType
import com.coinepro.core.chart.DrawingActions
import com.coinepro.core.chart.DrawingState
import com.coinepro.core.chart.DrawingTool
import com.coinepro.core.chart.IndicatorPane
import com.coinepro.core.chart.PriceLevel
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Everything the chart screen is showing.
 *
 * One object rather than a dozen `remember`s in the composable, because most of it has to survive a
 * rotation and a fair amount of it has to be *saved*: a reader who set up four indicators and drew
 * three trend lines has done work, and losing it to a configuration change is losing their work.
 */
data class ChartUiState(
    val symbol: String,
    val timeframe: Timeframe = Timeframe.H1,
    val chartType: ChartType = ChartType.CANDLES,
    val series: CandleSeries = CandleSeries.EMPTY,
    val loading: Boolean = false,
    /** Distinct from [loading]: paging back leaves the chart on screen and usable. */
    val loadingMore: Boolean = false,
    val error: ChartError? = null,
    val activeIndicators: Set<String> = emptySet(),
    val drawing: DrawingState = DrawingState(),
    val hasMore: Boolean = false,
) {
    /** The price-scale overlays for whatever is switched on. Recomputed when the series changes. */
    val overlays: List<ChartLine>
        get() = ChartCatalog.INDICATORS
            .filter { it.id in activeIndicators && it.pane == IndicatorPane.PRICE }
            .flatMap { ChartCatalog.overlayFor(it, series) } +
            ChartCatalog.INDICATORS
                .filter { it.id in activeIndicators && it.pane == IndicatorPane.STRUCTURE }
                .flatMap { ChartCatalog.structureFor(it, series).lines }

    val levels: List<PriceLevel>
        get() = ChartCatalog.INDICATORS
            .filter { it.id in activeIndicators && it.pane == IndicatorPane.STRUCTURE }
            .flatMap { ChartCatalog.structureFor(it, series).levels }

    val markers: List<ChartMarker>
        get() = ChartCatalog.INDICATORS
            .filter { it.id in activeIndicators && it.pane == IndicatorPane.STRUCTURE }
            .flatMap { ChartCatalog.structureFor(it, series).markers }

    /** The last close, which is what the header shows beside the symbol. */
    val lastPrice: Double? get() = series.bars.lastOrNull()?.c
}

/**
 * Why the chart is empty.
 *
 * Separated from a message string so the screen can say something useful for each: a symbol this
 * platform does not carry is a different situation from a network that dropped, and offering
 * "retry" for the first one wastes the reader's time.
 */
enum class ChartError {
    /** The network failed. Retrying is the right suggestion. */
    NETWORK,

    /** The server does not carry this symbol on this platform. Retrying will not help. */
    UNSUPPORTED_SYMBOL,

    /** CoinePro-FX's academy token feature is switched off server-side. */
    CHART_DISABLED,
}

/**
 * The chart screen's state machine.
 *
 * Plain class, plain coroutine scope, no Android and no Compose — the same shape as every other
 * controller in this app, and for the same reason: the interesting behaviour here is paging and
 * timeframe switching, and both are much easier to be sure about as a unit test than as a screen.
 */
class ChartController(
    symbol: String,
    private val gateway: CandleGateway,
    private val scope: CoroutineScope,
    timeframe: Timeframe = Timeframe.H1,
) {

    private val _state = MutableStateFlow(ChartUiState(symbol = symbol, timeframe = timeframe))
    val state: StateFlow<ChartUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun start() {
        if (_state.value.series.isEmpty && loadJob == null) reload()
    }

    /**
     * Switch timeframe, which is a reload rather than a transform.
     *
     * The bars themselves are different — an H4 bar is not four H1 bars unless the feed says so —
     * so anything derived from them goes with it. The drawings do not: they are anchored in
     * (time, price) and mean the same thing on every timeframe, which is the whole reason they are
     * stored that way.
     */
    fun setTimeframe(timeframe: Timeframe) {
        if (timeframe == _state.value.timeframe) return
        _state.update { it.copy(timeframe = timeframe, series = CandleSeries.EMPTY, hasMore = false) }
        reload()
    }

    fun setChartType(type: ChartType) = _state.update { it.copy(chartType = type) }

    fun toggleIndicator(id: String) = _state.update { old ->
        old.copy(
            activeIndicators = if (id in old.activeIndicators) {
                old.activeIndicators - id
            } else {
                old.activeIndicators + id
            },
        )
    }

    fun arm(tool: DrawingTool?) =
        _state.update { it.copy(drawing = DrawingActions.arm(it.drawing, tool)) }

    fun onDrawing(next: DrawingState) = _state.update { it.copy(drawing = next) }

    fun undoDrawing() = _state.update { it.copy(drawing = DrawingActions.undo(it.drawing)) }

    fun cancelDrawing() = _state.update { it.copy(drawing = DrawingActions.cancel(it.drawing)) }

    fun deleteDrawing(id: Long) =
        _state.update { it.copy(drawing = DrawingActions.delete(it.drawing, id)) }

    fun retry() = reload()

    /**
     * Page backwards from the oldest bar held.
     *
     * Guarded against overlapping calls, because the natural trigger is "the reader panned near the
     * left edge" and that fires on every frame of a drag. Two pages in flight would arrive in
     * whichever order the network chose and interleave into the series.
     */
    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || current.loading || !current.hasMore || current.series.isEmpty) return
        val oldest = current.series.time.first()
        _state.update { it.copy(loadingMore = true) }
        scope.launch {
            val result = runCatching {
                gateway.load(current.symbol, current.timeframe, before = oldest)
            }
            result.onSuccess { page ->
                _state.update { old ->
                    // Prepended, and only bars strictly older than what is held. The server
                    // promises no overlap; trusting that promise and being wrong would double a
                    // bar, and a doubled bar is a spike on the chart that never happened.
                    val older = page.candles.filter { it.t < oldest }
                    old.copy(
                        series = CandleSeries(older.map(OhlcBar::toCandle) + old.series.bars),
                        loadingMore = false,
                        hasMore = page.hasMore && older.isNotEmpty(),
                    )
                }
            }.onFailure {
                // A failed page-back leaves the chart alone. There is nothing to say that would be
                // more useful than the bars already on screen.
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    private fun reload() {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        loadJob = scope.launch {
            val current = _state.value
            runCatching { gateway.load(current.symbol, current.timeframe) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            series = CandleSeries(page.candles.map(OhlcBar::toCandle)),
                            loading = false,
                            error = null,
                            hasMore = page.hasMore,
                        )
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(loading = false, error = failure.toChartError()) }
                }
            loadJob = null
        }
    }
}

/**
 * A wire bar as a chart bar.
 *
 * The one place the two models meet, and it is deliberately here rather than in either module:
 * `core:chart` is a Compose module and `core:marketdata` must not depend on it, so neither can own
 * the mapping without dragging the other in.
 */
internal fun OhlcBar.toCandle(): Candle = Candle(t = t, o = o, h = h, l = l, c = c, v = v)

/**
 * What went wrong, from what the gateway threw.
 *
 * Matched on the server's own error code rather than the HTTP status, which is what TradeYar's team
 * asked for: they answer 422 where 400 might be expected and said plainly to branch on `code`.
 */
internal fun Throwable.toChartError(): ChartError {
    val text = (message ?: "") + (cause?.message ?: "")
    return when {
        text.contains("academy_disabled") -> ChartError.CHART_DISABLED
        text.contains("TYR-021") || text.contains("unsupported_symbol") -> ChartError.UNSUPPORTED_SYMBOL
        else -> ChartError.NETWORK
    }
}
