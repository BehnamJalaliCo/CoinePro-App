package com.coinepro.feature.chart

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ChartOrder
import com.coinepro.core.chart.ChartLine
import com.coinepro.core.chart.ChartMarker
import com.coinepro.core.chart.ChartPane
import com.coinepro.core.chart.ChartType
import com.coinepro.core.chart.DrawingActions
import com.coinepro.core.chart.DrawingState
import com.coinepro.core.chart.DrawingTool
import com.coinepro.core.chart.IndicatorPane
import com.coinepro.core.chart.PriceLevel
import com.coinepro.core.chart.Replay
import com.coinepro.core.chart.ReplayState
import com.coinepro.core.chart.TradeSide
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    /**
     * Bar replay: the chart rewound and walked forward.
     *
     * Off is [ReplayState] with no bars, not a null, so every read below is a plain property
     * access rather than a null check that somebody will forget in the one place that matters —
     * the place that would then draw the future.
     */
    val replay: ReplayState = ReplayState(),
) {
    /**
     * The price-scale overlays for whatever is switched on. Recomputed when the series changes.
     *
     * Derived from [visibleSeries], not [series]. During replay an indicator computed over every
     * bar would place a moving average using prices the reader is not allowed to have seen yet —
     * the future leaking back in through the one door nobody watches.
     */
    val overlays: List<ChartLine>
        get() = ChartCatalog.INDICATORS
            .filter { it.id in activeIndicators && it.pane == IndicatorPane.PRICE }
            .flatMap { ChartCatalog.overlayFor(it, visibleSeries) } +
            ChartCatalog.INDICATORS
                .filter { it.id in activeIndicators && it.pane == IndicatorPane.STRUCTURE }
                .flatMap { ChartCatalog.structureFor(it, visibleSeries).lines }

    val levels: List<PriceLevel>
        get() = ChartCatalog.INDICATORS
            .filter { it.id in activeIndicators && it.pane == IndicatorPane.STRUCTURE }
            .flatMap { ChartCatalog.structureFor(it, visibleSeries).levels }

    val markers: List<ChartMarker>
        get() = ChartCatalog.INDICATORS
            .filter { it.id in activeIndicators && it.pane == IndicatorPane.STRUCTURE }
            .flatMap { ChartCatalog.structureFor(it, visibleSeries).markers }

    /**
     * The strips below the price — one per switched-on oscillator.
     *
     * In catalogue order rather than in the order the reader switched them on, so the same three
     * indicators always stack the same way. A pane order that depended on tap history would move
     * under a reader who turned one off and back on.
     */
    val panes: List<ChartPane>
        get() = ChartCatalog.INDICATORS
            .filter { it.id in activeIndicators && it.pane == IndicatorPane.SEPARATE }
            .mapNotNull { ChartCatalog.paneFor(it, visibleSeries) }

    /** The last close, which is what the header shows beside the symbol. */
    /**
     * What the chart may draw.
     *
     * Every consumer reads this rather than [series], and that is the whole safety property of
     * replay: the future is not hidden by the renderer, it is absent from what the renderer is
     * given. A screen that filtered while drawing would leak it through the crosshair, the price
     * axis, the last-price line and the indicator panes, one at a time.
     */
    val visibleSeries: CandleSeries get() = if (replay.isOn) replay.visible else series

    /**
     * The setup the reader has drawn, as numbers.
     *
     * Read off the newest complete `longshort` drawing rather than held separately, because the
     * drawing *is* the setup — a second copy would be the one that disagrees with the lines on
     * screen, and the numbers disagreeing with the picture is the whole failure mode here.
     *
     * Side comes from the geometry: a stop below the entry is a buy. Asking the reader to also
     * pick a direction would let them pick the one their drawing does not show.
     */
    val setup: ChartOrder?
        get() {
            val drawing = drawing.drawings.lastOrNull { it.toolId == "longshort" && it.complete }
                ?: return null
            val points = drawing.points.takeIf { it.size >= 2 } ?: return null
            val entry = points[0].price
            val stop = points[1].price
            if (entry == stop || !entry.isFinite() || !stop.isFinite()) return null
            val side = if (stop < entry) TradeSide.BUY else TradeSide.SELL
            // The same 2R the renderer draws. One arithmetic, two consumers: if the target line
            // and the target number came from different expressions they would drift.
            return ChartOrder(side, entry, stop, entry + 2 * (entry - stop))
        }

    val lastPrice: Double? get() = visibleSeries.bars.lastOrNull()?.c

    /**
     * The move across the loaded window, as a percentage.
     *
     * Measured from the first loaded bar rather than from a session open, because a session open is
     * something only the server knows and neither feed sends one. Naming it after the window is the
     * honest version: it describes the picture, and the picture is what the reader is looking at.
     */
    val changePercent: Double?
        get() {
            val bars = visibleSeries.bars
            val first = bars.firstOrNull()?.c ?: return null
            val last = bars.lastOrNull()?.c ?: return null
            return if (first == 0.0) null else (last - first) / first * 100.0
        }
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
    private var replayJob: Job? = null

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

    /* ------------------------------------------------------------------ replay */

    /**
     * Enter replay at the loaded bars.
     *
     * Refused below [Replay.MINIMUM_BARS], where the cursor would start at the chart's own right
     * edge and the exercise would be pointless rather than merely short. Returning silently is
     * right here: the button that calls this is only shown when there are enough bars, so a
     * refusal means the series shrank under it, and a dialog about that helps nobody.
     */
    fun enterReplay() {
        val bars = _state.value.series.bars
        val entered = Replay.enter(bars) ?: return
        _state.update { it.copy(replay = entered) }
    }

    fun exitReplay() {
        replayJob?.cancel()
        replayJob = null
        _state.update { it.copy(replay = Replay.exit()) }
    }

    fun replayStep() = withReplay(Replay::step)

    fun replayStepBack() = withReplay(Replay::stepBack)

    fun replaySeek(fraction: Float) = withReplay { current ->
        Replay.seek(current, index = ((current.bars.size - 1) * fraction).toInt())
    }

    fun replaySetSpeed(speed: Double) = withReplay { Replay.setSpeed(it, speed) }

    /**
     * Play or pause.
     *
     * The clock lives here rather than in the composable. A `LaunchedEffect` driving this would
     * stop when the screen left composition — which is correct for an animation and wrong for a
     * replay, because a reader who opens the indicator sheet mid-replay has not asked it to stop.
     */
    fun replayToggle() {
        val next = Replay.toggle(_state.value.replay)
        _state.update { it.copy(replay = next) }
        replayJob?.cancel()
        replayJob = if (!next.playing) {
            null
        } else {
            scope.launch {
                while (isActive) {
                    delay(Replay.delayMillis(_state.value.replay.speed))
                    val stepped = Replay.step(_state.value.replay)
                    _state.update { it.copy(replay = stepped) }
                    if (!stepped.playing) break
                }
            }
        }
    }

    private fun withReplay(transform: (ReplayState) -> ReplayState) {
        val current = _state.value.replay
        if (!current.isOn) return
        // Any manual move pauses. A reader stepping back while it plays is trying to look at
        // something, and a chart that keeps advancing under them is fighting the finger.
        replayJob?.cancel()
        replayJob = null
        _state.update { it.copy(replay = transform(current).copy(playing = false)) }
    }

    /**
     * Applies a layout: type, timeframe and the whole indicator set.
     *
     * The set is *replaced*, not merged. A layout that added its indicators to whatever was already
     * on would drift towards every indicator being on at once, which is the state a layout exists
     * to escape.
     */
    fun applyLayout(chartTypeId: String, timeframeId: String, indicatorIds: List<String>) {
        val type = ChartType.entries.firstOrNull { it.name == chartTypeId }
        val timeframe = Timeframe.entries.firstOrNull { it.name == timeframeId }
        // Unknown ids are skipped rather than failing the whole apply: a layout saved by an older
        // build may name an indicator this one has renamed, and losing one line is better than
        // losing the layout.
        _state.update { current ->
            current.copy(
                chartType = type ?: current.chartType,
                activeIndicators = indicatorIds.filter { id -> ChartCatalog.INDICATORS.any { it.id == id } }.toSet(),
            )
        }
        if (timeframe != null) setTimeframe(timeframe)
    }

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
