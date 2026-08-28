package com.coinepro.feature.chart

import com.coinepro.core.chart.ArrowDirection
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ChartLine
import com.coinepro.core.chart.ChartMarker
import com.coinepro.core.chart.ChartOrder
import com.coinepro.core.chart.ChartPane
import com.coinepro.core.chart.ChartPoint
import com.coinepro.core.chart.ChartType
import com.coinepro.core.chart.Drawing
import com.coinepro.core.chart.DrawingActions
import com.coinepro.core.chart.DrawingState
import com.coinepro.core.chart.DrawingTool
import com.coinepro.core.chart.IndicatorPane
import com.coinepro.core.chart.PriceLevel
import com.coinepro.core.chart.Replay
import com.coinepro.core.chart.ReplayState
import com.coinepro.core.chart.TradeSide
import com.coinepro.core.datastore.ChartDrawingStore
import com.coinepro.core.diagnostics.AppLog
import com.coinepro.core.diagnostics.LogTag
import com.coinepro.core.datastore.StoredDrawing
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
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
    /**
     * Whether the price axis is logarithmic.
     *
     * Part of the apparatus rather than of the data, so it survives a timeframe change and a chart
     * type change the same way the indicator set does. See `ChartViewport.logScale`.
     */
    val logScale: Boolean = false,
    /**
     * The lookbacks the reader has changed, by indicator id.
     *
     * Sparse: an indicator absent from this map is drawn at its own default, which is what nearly
     * all of them will be. Storing the defaults too would mean a map that has to be migrated every
     * time one of them changes.
     *
     * The reason this exists: every period in the app was a literal until now, so «EMA 20» was the
     * only exponential average this chart could ever draw. A moving average whose length cannot be
     * changed is not a moving average a trader can use — the length *is* the tool.
     */
    val indicatorPeriods: Map<String, Int> = emptyMap(),
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
            .flatMap { ChartCatalog.overlayFor(it, visibleSeries, indicatorPeriods[it.id]) } +
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
            .mapNotNull { ChartCatalog.paneFor(it, visibleSeries, indicatorPeriods[it.id]) }

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
            // The target the reader dragged, or the two-to-one it was placed at. The fallback is
            // the same expression the renderer falls back to and covers the same case: a setup
            // saved by a build from before the target was a point.
            val target = points.getOrNull(2)?.price?.takeIf { it.isFinite() }
                ?: (entry + 2 * (entry - stop))
            return ChartOrder(side, entry, stop, target)
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
    private val symbol: String,
    private val gateway: CandleGateway,
    private val scope: CoroutineScope,
    timeframe: Timeframe = Timeframe.H1,
    /**
     * Where this symbol's drawings live between sessions.
     *
     * Null leaves the chart exactly as it was — everything in memory and gone with the composition
     * — which is what the tests and the preview use. In the app it is always supplied, because a
     * trend line whose lifetime is a scroll position is not a trend line.
     */
    private val drawings: ChartDrawingStore? = null,
    /**
     * The app's structured log, or null in a test.
     *
     * Here for exactly one measurement: **time to first candle.** In a corpus of reviews of this
     * category of app, load and render speed is the single largest sub-theme of chart complaints
     * — larger than every missing feature put together. A budget that nothing measures is a wish,
     * so every load is timed and anything past [FIRST_CANDLE_BUDGET_MS] is logged as a warning
     * with the symbol, the timeframe and the bar count, which is enough to tell a slow network
     * from a slow route from a response that was simply too big.
     */
    private val log: AppLog? = null,
) {

    /** The venue these bars come from, named. See [CandleGateway.sourceName]. */
    val sourceName: String get() = gateway.sourceName

    private val _state = MutableStateFlow(ChartUiState(symbol = symbol, timeframe = timeframe))
    val state: StateFlow<ChartUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** Read once per controller. See [restoreDrawings]. */
    private var restored = false
    private var replayJob: Job? = null

    fun start() {
        if (_state.value.series.isEmpty && loadJob == null) reload()
        restoreDrawings()
    }

    /**
     * Reads this symbol's drawings back, once.
     *
     * `first()` rather than `collect`: the store is the *record*, not the source of truth while the
     * chart is open. Collecting would mean every save the chart makes comes straight back as an
     * update, and a reader dragging a trend line would fight their own persistence.
     */
    private fun restoreDrawings() {
        val store = drawings ?: return
        if (restored) return
        restored = true
        scope.launch {
            val stored = runCatching { store.drawings(symbol).first() }.getOrNull().orEmpty()
            if (stored.isEmpty()) return@launch
            _state.update { current ->
                // Merged behind whatever is already on the chart rather than replacing it: the
                // restore is asynchronous, and a reader fast enough to draw before it lands must
                // not have that drawing thrown away by it.
                val existing = current.drawing.drawings.map(Drawing::id).toSet()
                current.copy(
                    drawing = current.drawing.copy(
                        drawings = stored.map(StoredDrawing::toDrawing)
                            .filterNot { it.id in existing } + current.drawing.drawings,
                    ),
                )
            }
        }
    }

    /** Writes the current set back. Called after every change that alters what is on the chart. */
    private fun persistDrawings() {
        val store = drawings ?: return
        val snapshot = _state.value.drawing.drawings
        scope.launch { runCatching { store.save(symbol, snapshot.map(Drawing::toStored)) } }
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

    fun toggleLogScale() = _state.update { it.copy(logScale = !it.logScale) }

    fun toggleIndicator(id: String) = _state.update { old ->
        old.copy(
            activeIndicators = if (id in old.activeIndicators) {
                old.activeIndicators - id
            } else {
                old.activeIndicators + id
            },
        )
    }

    /**
     * Set one indicator's lookback, or clear it back to the default.
     *
     * Clamped by the catalogue, so a value from an older build's wider bounds cannot produce a
     * chart of nulls. Clearing removes the key rather than writing the default in: the default is
     * allowed to change, and a stored copy of it would pin the old one forever.
     */
    fun setIndicatorPeriod(id: String, period: Int?) = _state.update { old ->
        val bounds = ChartCatalog.periodOf(id) ?: return@update old
        old.copy(
            indicatorPeriods = if (period == null || period == bounds.default) {
                old.indicatorPeriods - id
            } else {
                old.indicatorPeriods + (id to period.coerceIn(bounds.min, bounds.max))
            },
        )
    }

    fun arm(tool: DrawingTool?) =
        _state.update { it.copy(drawing = DrawingActions.arm(it.drawing, tool)) }

    fun onDrawing(next: DrawingState) {
        _state.update { it.copy(drawing = next) }
        persistDrawings()
    }

    fun undoDrawing() {
        _state.update { it.copy(drawing = DrawingActions.undo(it.drawing)) }
        persistDrawings()
    }

    fun cancelDrawing() = _state.update { it.copy(drawing = DrawingActions.cancel(it.drawing)) }

    fun deleteDrawing(id: Long) {
        _state.update { it.copy(drawing = DrawingActions.delete(it.drawing, id)) }
        persistDrawings()
    }

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
            // Wall clock rather than `AppLog.timed`, because what is being measured is not the
            // gateway call: it is the interval a reader spends looking at an empty chart, which
            // ends when the state carrying the bars is published, not when the response lands.
            val startedAt = System.nanoTime()
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
                    val millis = (System.nanoTime() - startedAt) / 1_000_000
                    val fields = mapOf(
                        "symbol" to current.symbol,
                        "tf" to current.timeframe.name,
                        "bars" to page.candles.size.toString(),
                        "ms" to millis.toString(),
                    )
                    if (millis > FIRST_CANDLE_BUDGET_MS) {
                        log?.warn(LogTag.CHART, "first candle over budget", fields)
                    } else {
                        log?.debug(LogTag.CHART, "first candle", fields)
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(loading = false, error = failure.toChartError()) }
                    log?.warn(
                        LogTag.CHART,
                        "chart load failed",
                        mapOf(
                            "symbol" to current.symbol,
                            "tf" to current.timeframe.name,
                            "ms" to ((System.nanoTime() - startedAt) / 1_000_000).toString(),
                            // The owned error, never the exception's text — that is a platform
                            // string in English and it is not this log's business to carry it.
                            "error" to failure.toChartError().name,
                        ),
                    )
                }
            loadJob = null
        }
    }

    private companion object {
        /**
         * How long a chart may take to show its first candle before it is worth complaining about.
         *
         * Twelve hundred milliseconds. Not a target — a target would be half that — but the point
         * past which a reader on a phone has stopped waiting and started wondering. It is set
         * above `AppLog.SLOW_MILLIS` deliberately: 250ms is the right threshold for an in-process
         * operation and an absurd one for a round trip to a server over a mobile network in Iran.
         */
        const val FIRST_CANDLE_BUDGET_MS = 1_200L
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

/**
 * The two halves of the drawing mapping.
 *
 * They live here rather than in either module because `core:chart` has no business knowing about
 * DataStore and `core:datastore` has no business knowing about the chart engine — and this is the
 * one file where both are already on the classpath.
 */
private fun Drawing.toStored(): StoredDrawing = StoredDrawing(
    id = id,
    toolId = toolId,
    points = points.map { it.time to it.price },
    colour = colour,
    widthDp = widthDp,
    text = text,
    direction = direction.name,
)

private fun StoredDrawing.toDrawing(): Drawing = Drawing(
    id = id,
    toolId = toolId,
    points = points.map { (time, price) -> ChartPoint(time, price) },
    colour = colour,
    widthDp = widthDp,
    // Restored drawings are finished by definition: a half-placed one was never committed, so it
    // was never written.
    complete = true,
    text = text,
    direction = runCatching { ArrowDirection.valueOf(direction) }.getOrDefault(ArrowDirection.UP),
)
