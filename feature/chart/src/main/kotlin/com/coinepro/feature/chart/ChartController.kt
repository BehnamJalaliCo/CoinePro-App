package com.coinepro.feature.chart

import com.coinepro.core.chart.ArrowDirection
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ChartColours
import com.coinepro.core.chart.ChartLine
import com.coinepro.core.chart.ChartMarker
import com.coinepro.core.chart.ChartOrder
import com.coinepro.core.chart.ChartPane
import com.coinepro.core.chart.ChartPoint
import com.coinepro.core.chart.ChartType
import com.coinepro.core.chart.ComparisonBasis
import com.coinepro.core.chart.ComparisonSeries
import com.coinepro.core.chart.Drawing
import com.coinepro.core.chart.DrawingActions
import com.coinepro.core.chart.DrawingState
import com.coinepro.core.chart.DrawingTool
import com.coinepro.core.chart.IndicatorPane
import com.coinepro.core.chart.MAX_COMPARISONS
import com.coinepro.core.chart.ObjectTree
import com.coinepro.core.chart.PriceChannel
import com.coinepro.core.chart.PriceLevel
import com.coinepro.core.chart.PriceScaleMode
import com.coinepro.core.chart.Replay
import com.coinepro.core.chart.ReplaySpeed
import com.coinepro.core.chart.ReplayState
import com.coinepro.core.chart.ScaleSide
import com.coinepro.core.chart.TradeSide
import com.coinepro.core.chart.align
import com.coinepro.core.chart.comparisonColour
import com.coinepro.core.datastore.ChartColourTemplate
import com.coinepro.core.datastore.ChartDrawingStore
import com.coinepro.core.datastore.ChartLayout
import com.coinepro.core.datastore.ChartLayoutStore
import com.coinepro.core.datastore.StoredDrawing
import com.coinepro.core.datastore.SymbolChartState
import com.coinepro.core.datastore.SymbolChartStateStore
import com.coinepro.core.diagnostics.AppLog
import com.coinepro.core.diagnostics.LogTag
import com.coinepro.core.marketdata.CandleCache
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.NoOpCandleCache
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.resolveCandleRequest
import com.coinepro.core.marketdata.Timeframe
import com.coinepro.core.marketdata.of
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
    /**
     * The bar length on screen: one of the fifteen presets, or a minute count the reader typed.
     *
     * A [ChartInterval] rather than a [Timeframe] because those are the two things a reader can
     * choose and everything downstream — the request, the cache key, the axis, the saved layout —
     * needs only a length, a caption and a wire spelling from either. Holding the enum here meant
     * every custom interval had to be smuggled past it, and the branch that gets forgotten is the
     * one that quietly draws hourly bars for somebody who asked for two hundred and five minutes.
     */
    val interval: ChartInterval = ChartInterval.Preset(Timeframe.H1),
    val chartType: ChartType = ChartType.CANDLES,
    val series: CandleSeries = CandleSeries.EMPTY,
    val loading: Boolean = false,
    /** Distinct from [loading]: paging back leaves the chart on screen and usable. */
    val loadingMore: Boolean = false,
    val error: ChartError? = null,
    val activeIndicators: Set<String> = emptySet(),
    /**
     * What the price axis is measuring.
     *
     * Part of the apparatus rather than of the data, so it survives a timeframe change and a chart
     * type change the same way the indicator set does. It replaced a `logScale` boolean, which
     * could represent only two of the four questions an axis is asked — see `PriceScaleMode`, and
     * [logScale] for the boolean that is still derived from it so nothing downstream had to change
     * on the same day.
     */
    val scaleMode: PriceScaleMode = PriceScaleMode.REGULAR,
    /** Whether the axis is flipped, low at the top. Apparatus, like [scaleMode]. */
    val inverted: Boolean = false,
    /** Whether the price axis is tied to the bar axis, so zooming one zooms the other. */
    val priceBarLock: Boolean = false,
    /** A pinned label precision, or null to let the axis derive one from the range. */
    val decimals: Int? = null,
    /** Which gutter carries the price labels. See `ScaleSide`, and `MERGED` when comparing. */
    val scaleSide: ScaleSide = ScaleSide.RIGHT,
    /**
     * How much of the canvas the indicator panes take, as a factor on what they ask for.
     *
     * Part of the apparatus like [scaleMode], so it survives a timeframe change. See
     * `ChartDecoration.paneScale`.
     */
    val paneScale: Float = 1f,
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
    /**
     * Whether the eraser is the mode the reader picked.
     *
     * Held here rather than read off [DrawingState.tool], because it cannot be read off there:
     * `DrawingActions.arm` refuses to arm anything in `ToolGroup.MODES` — a mode places no points,
     * so putting one in `tool` would leave the chart waiting for taps that never commit. The rail
     * knows which mode was chosen and this is that one bit, carried to the canvas.
     *
     * Until this existed the eraser was a rail entry that did nothing at all: it was selected, the
     * arm refused it, and the reader was left with a tool they could pick and not use.
     */
    val eraser: Boolean = false,
    /**
     * The drawings the reader has switched off in the object tree.
     *
     * Ids rather than a flag on [Drawing], and that is deliberate: hiding is a property of *this
     * reader's view right now*, not of the object, and a flag on the drawing would be written to
     * disk and come back a week later as a trend line that is on the chart and cannot be seen. It
     * is also why hidden drawings are filtered on the way to the canvas and merged back on the way
     * out — see [canvasDrawing] and [ChartController.onDrawing] — rather than removed from the
     * list, which would persist as a deletion.
     */
    val hiddenDrawingIds: Set<Long> = emptySet(),
    /**
     * The stroke width the next drawing is placed at.
     *
     * Beside `DrawingState.colour`, which is the same idea for the other half of a style, and here
     * rather than there because `core:chart` owns that state and this is the feature module's
     * business: it is set by applying a saved template, which is a storage concept. A drawing
     * already on the chart keeps whatever width it was placed at.
     */
    val drawingWidthDp: Float = DEFAULT_DRAWING_WIDTH_DP,
    /**
     * The colour template the chart paints with, or null for the theme's own palette.
     *
     * Held as the stored row rather than as an id, so the screen can hand the renderer six colours
     * without a second lookup, and so a template deleted while it is on the chart keeps painting
     * until something replaces it — which is better than a chart that reverts under the reader the
     * moment they tidy their template list.
     */
    val colourTemplate: ChartColourTemplate? = null,
    val hasMore: Boolean = false,
    /**
     * Bar replay: the chart rewound and walked forward.
     *
     * Off is [ReplayState] with no bars, not a null, so every read below is a plain property
     * access rather than a null check that somebody will forget in the one place that matters —
     * the place that would then draw the future.
     */
    val replay: ReplayState = ReplayState(),
    /**
     * The instruments drawn over this one, already lined up with its bars.
     *
     * Aligned rather than raw, because two feeds do not share a calendar and the arithmetic that
     * reconciles them is exactly the thing that is easy to get invisibly wrong — see
     * `com.coinepro.core.chart.align`. At most `MAX_COMPARISONS`; see [ComparisonRefusal] for the
     * three ways a request to add one is turned down.
     */
    val comparisons: List<ComparisonSeries> = emptyList(),
    /** How a comparison is expressed against this chart. See `ComparisonBasis`. */
    val comparisonBasis: ComparisonBasis = ComparisonBasis.PERCENT,
    /**
     * An already-computed [ChartDerived] the controller is carrying forward, or null to compute.
     *
     * Not part of what this state *means* — it is the same answer, arrived at without the work —
     * so it is excluded from equality and from `toString` by being the only property the
     * controller ever sets, and by nothing outside this file reading it. See [derived].
     */
    val carried: ChartDerived? = null,
) {
    /**
     * Whether the price axis is logarithmic.
     *
     * Derived rather than stored, so it can never disagree with [scaleMode]. It stays because the
     * chart composable, the saved layout and the per-symbol store all still speak in a boolean,
     * and widening every one of them on the same day as the axis itself would have been four
     * chances to get one wrong.
     */
    val logScale: Boolean get() = scaleMode == PriceScaleMode.LOGARITHMIC

    /**
     * What the canvas is given: everything except what the reader has hidden.
     *
     * Filtered here rather than in the renderer, and never by removing anything from [drawing].
     * The chart hands its state straight back through `onDrawing`, so a hidden drawing dropped on
     * the way in would be a hidden drawing *deleted* on the way out, and the delete would be
     * persisted before anybody noticed. [ChartController.onDrawing] merges them back in at their
     * own places, which is the other half of this.
     *
     * The common case — nothing hidden — returns the same object, so a chart with no hidden
     * drawings allocates nothing and compares equal frame to frame.
     */
    val canvasDrawing: DrawingState
        get() = if (hiddenDrawingIds.isEmpty()) {
            drawing
        } else {
            drawing.copy(drawings = drawing.drawings.filterNot { it.id in hiddenDrawingIds })
        }

    /**
     * The six colours the canvas should paint with, or null to leave it on the theme's.
     *
     * The mapping lives here rather than in `core:datastore` because `core:chart` must not depend
     * on that module and it must not depend on this one either — the two types are the same six
     * fields declared on opposite sides of a boundary neither may cross, and this is the one place
     * where both are already on the classpath.
     */
    val chartColours: ChartColours?
        get() = colourTemplate?.let { template ->
            ChartColours(
                up = template.up,
                down = template.down,
                grid = template.grid,
                background = template.background,
                text = template.text,
                crosshair = template.crosshair,
            )
        }

    /**
     * Whether one page of this interval is fewer bars than a page normally is.
     *
     * True only for the intervals the feed does not serve, where each drawn bar is folded from
     * many source bars and the server's own page cap therefore bites: two hundred and five minutes
     * off a five-minute feed is forty-one source bars each, so a thousand-bar page is twenty-four
     * drawn ones. That is a real property of asking a minute feed for a very long bar and it
     * cannot be fixed by asking differently — only by paging back.
     *
     * It is surfaced because a chart that is simply short, with nothing saying why, reads as a
     * defect. `resolveCandleRequest` is the same arithmetic the gateway will do, asked here
     * without making the request.
     */
    val historyTruncated: Boolean get() = resolveCandleRequest(interval).truncated

    /**
     * The preset in force, or null when the reader typed their own interval.
     *
     * Null is not a failure and callers must treat it as "this is not one of the fifteen" rather
     * than as "there is no interval" — [interval] always has one. Anything that only needs a
     * length or a caption should read [interval] instead and never meet this at all.
     */
    val timeframe: Timeframe? get() = (interval as? ChartInterval.Preset)?.timeframe

    /**
     * The spelling the chart-vision endpoint would accept for this interval, or null.
     *
     * The vision model was trained and validated on six bar lengths and the gateway refuses
     * anything else outright — so an entry point that forwarded [interval] blindly would turn a
     * reader's two-hundred-and-five-minute chart into a failed request with a server-worded error.
     * Asking first, and disabling the entry with [aiVisionRefusal] instead, is the difference
     * between a feature that is unavailable and a feature that is broken.
     */
    val aiVisionWire: String? get() = interval.wire.takeIf { it in AI_VISION_INTERVALS }

    /** Why chart vision cannot read this chart, in a sentence, or null when it can. */
    val aiVisionRefusal: String?
        get() = if (aiVisionWire != null) null else "تحلیل تصویری روی این بازهٔ زمانی کار نمی‌کند. یکی از بازه‌های ۱ دقیقه، ۵ دقیقه، ۱۵ دقیقه، ۱ ساعت، ۴ ساعت یا ۱ روز را انتخاب کنید."

    /**
     * Everything the indicators produce, computed once.
     *
     * ### Why this is one object and not four getters
     *
     * It used to be four, and they were plain `get()`s — so reading them recomputed. Two things
     * followed, and both are the kind of cost that only shows up on a cheap phone:
     *
     *  * **Every structure study ran three times per read.** `overlays`, `levels` and `markers`
     *    each called `structureFor` for the same study and threw away two thirds of each answer.
     *    A zigzag over three hundred bars, computed three times, to draw it once.
     *  * **A drawing drag recomputed the lot, every frame.** Dragging a trend line emits a new
     *    state per frame — that is what makes the line follow the finger — and each new state
     *    recomputed every switched-on indicator, none of which had changed, because not one of
     *    their inputs had moved.
     *
     * Now it is one value with `lazy`, so a state that is never read costs nothing and a state
     * read four times costs once; and [ChartController] carries it across state copies whose
     * indicator inputs are unchanged, which is what makes a drag free.
     */
    internal val derived: ChartDerived by lazy(LazyThreadSafetyMode.NONE) {
        // The first comparison, not all four. `correlation` measures this chart against *one*
        // other series, and the first slot is the one the reader added first — which is the one
        // they meant when they switched a correlation on. The rest are drawn as overlays and take
        // no part in it.
        val partner = comparisons.firstOrNull()
        carried?.takeIf { it.matches(visibleSeries, activeIndicators, indicatorPeriods, partner) }
            ?: ChartDerived.of(visibleSeries, activeIndicators, indicatorPeriods, partner)
    }

    /**
     * The price-scale overlays for whatever is switched on.
     *
     * Derived from [visibleSeries], not [series]. During replay an indicator computed over every
     * bar would place a moving average using prices the reader is not allowed to have seen yet —
     * the future leaking back in through the one door nobody watches.
     */
    val overlays: List<ChartLine> get() = derived.overlays

    val levels: List<PriceLevel> get() = derived.levels

    val markers: List<ChartMarker> get() = derived.markers

    /**
     * The strips below the price — one per switched-on oscillator.
     *
     * In catalogue order rather than in the order the reader switched them on, so the same three
     * indicators always stack the same way. A pane order that depended on tap history would move
     * under a reader who turned one off and back on.
     */
    val panes: List<ChartPane> get() = derived.panes

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
 * The six bar lengths chart vision reads, by wire spelling.
 *
 * A copy of `AiVisionTimeframes.supported`, and copied deliberately rather than imported: this
 * module has no dependency on `core:aivision` and acquiring one so that a screen can ask a
 * six-element question would put the whole vision stack on the chart's classpath. `MARKET_COLOURS`
 * in `core:chart` makes the same trade for the same reason. The test beside this file asserts the
 * exact contents, so if that set ever changes the copy fails loudly rather than drifting.
 */
internal val AI_VISION_INTERVALS: Set<String> = setOf("M1", "M5", "M15", "H1", "H4", "D1")

/**
 * Why a request to compare a second instrument was turned down.
 *
 * An owned answer rather than a silent no, because all three of these are things a reader did on
 * purpose and each wants a different sentence back: "that is already on the chart" is reassurance,
 * "four is the limit" is an instruction to remove one first, and "that is this chart" is a mis-tap.
 * A refusal that only failed to happen would look like the button was broken.
 */
enum class ComparisonRefusal {
    /** The symbol was blank — nothing was actually chosen. */
    BLANK,

    /** It is the chart's own instrument. Comparing a thing to itself draws a flat line at zero. */
    SAME_SYMBOL,

    /** Already on the chart. Adding it twice would draw one line under another. */
    ALREADY_COMPARED,

    /** `MAX_COMPARISONS` are already drawn. See that constant for why the cap is four. */
    LIMIT_REACHED,
}

/**
 * Whether [symbol] may be added, and why not when it may not.
 *
 * A pure function rather than four guards inside the coroutine that fetches, because the *rules*
 * are the part worth pinning in a test and the fetch is not. The order is deliberate and is the
 * order a reader would reason in: a mis-tap on the chart's own symbol is answered before the cap
 * is mentioned, so somebody with four comparisons who taps the base symbol is told the true reason
 * rather than being sent away to delete one.
 *
 * Comparison is by uppercase, because `btcusdt` and `BTCUSDT` are one instrument and a chart that
 * drew both would be drawing one line twice.
 */
internal fun refuseComparison(
    base: String,
    existing: List<String>,
    symbol: String,
): ComparisonRefusal? {
    val wanted = symbol.trim().uppercase()
    if (wanted.isEmpty()) return ComparisonRefusal.BLANK
    if (wanted == base.trim().uppercase()) return ComparisonRefusal.SAME_SYMBOL
    if (existing.any { it.uppercase() == wanted }) return ComparisonRefusal.ALREADY_COMPARED
    if (existing.size >= MAX_COMPARISONS) return ComparisonRefusal.LIMIT_REACHED
    return null
}

/**
 * This chart's apparatus as a saveable layout.
 *
 * A function rather than a constructor call at each of the three call sites — the chart's sheet,
 * the studio's section, and the round trip a test asserts on — because a layout assembled by hand
 * in three places is three chances to forget the periods, which is precisely the field that was
 * dropped when layouts were four strings.
 *
 * [createdAt] is carried separately from [updatedAt] so that saving over an existing layout keeps
 * the date it was made; a caller writing a new one passes the same value for both.
 */
internal fun ChartUiState.toLayout(
    id: String,
    name: String,
    createdAt: Long,
    updatedAt: Long,
): ChartLayout = ChartLayout(
    id = id,
    name = name,
    symbol = symbol,
    timeframe = interval.wire,
    chartType = chartType.name,
    indicators = activeIndicators.toList(),
    indicatorPeriods = indicatorPeriods,
    scaleMode = scaleMode.name,
    // Null where the chart is on the theme's own palette, which is not the same as being on the
    // dark built-in: a reader who never opened the colour picker should get whatever the theme
    // does today, including after they switch the app to light.
    colourTemplate = colourTemplate?.id,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

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
    /**
     * The bars this app already has for this series.
     *
     * The chart draws these *before* the fetch goes out, so the reader sees candles rather than an
     * empty rectangle. "The chart won't come up" is the loudest complaint about every app in this
     * category and 19.3% of negative chart mentions in Persian reviews — and none of it is fixed
     * by a faster request, only by having something true to draw while one is in flight.
     */
    private val cache: CandleCache = NoOpCandleCache,
    /** See [bindStores]. Supplied here where a caller builds the controller itself. */
    symbolStates: SymbolChartStateStore? = null,
    /** See [bindStores]. Supplied here where a caller builds the controller itself. */
    layoutStore: ChartLayoutStore? = null,
) {

    /** The venue these bars come from, named. See [CandleGateway.sourceName]. */
    val sourceName: String get() = gateway.sourceName

    private val _state = MutableStateFlow(
        ChartUiState(symbol = symbol, interval = ChartInterval.Preset(timeframe)),
    )
    val state: StateFlow<ChartUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** Read once per controller. See [restoreDrawings]. */
    private var restored = false
    private var replayJob: Job? = null

    private var symbolStates: SymbolChartStateStore? = symbolStates
    private var layouts: ChartLayoutStore? = layoutStore

    /**
     * The raw bars of each compared instrument, by uppercase symbol, in the order they were added.
     *
     * Held beside the aligned series rather than derived from it, because alignment is lossy: a
     * [ComparisonSeries] is one value per *base* bar, and re-aligning it against a longer base
     * after a page-back would carry the wrong prices forward. Re-alignment therefore always starts
     * from what the feed actually sent.
     */
    private val comparisonSources = LinkedHashMap<String, CandleSeries>()

    /**
     * Whether the saved per-symbol state has been read back yet.
     *
     * Load-bearing, not bookkeeping. Every setter writes the chart's state back to disk, and a
     * setter that ran before the restore landed would overwrite a reader's saved timeframe with
     * the app default — which is the exact bug this whole feature exists to remove, arriving by
     * the back door.
     */
    private var symbolStateRestored = false

    /**
     * Hands the controller the two stores it persists through, where the caller could not.
     *
     * The app builds its controllers in a session-lived holder that predates both stores and does
     * not know about either; the chart routes do. Calling this before [start] is what makes the
     * restore happen; calling it after is harmless and does nothing, because the restore has run.
     * A null argument leaves whatever was already bound, so the studio re-entering the same
     * controller cannot unbind the chart's stores.
     */
    fun bindStores(symbolStates: SymbolChartStateStore?, layouts: ChartLayoutStore?) {
        symbolStates?.let { this.symbolStates = it }
        layouts?.let { this.layouts = it }
    }

    /**
     * Reads back what this reader last did here, then loads.
     *
     * The order is the point and it is the whole of item [141]/[143]. The saved timeframe, chart
     * type, indicators, periods and scale mode are applied **before** the first fetch goes out, so
     * the chart never requests, never paints and never counts down a bar length the reader did not
     * ask for. Restoring afterwards would work in the sense of ending in the right place, and
     * would still be wrong: the reader would watch an hourly chart resolve and then jump to their
     * five-minute one, and a chart that jumps under a finger is a chart that cannot be trusted to
     * be showing what its header says.
     *
     * ### Why the state is per symbol at all
     *
     * Because the alternative is what the large mobile terminals do, and it is their single
     * loudest small complaint: chart settings are global, so changing the timeframe or the
     * indicators while reading one asset silently changes every other asset. Somebody who watches
     * gold on the four-hour and a small cap on the five-minute re-sets the chart on every switch,
     * forever, and never gets told why. MetaTrader keys it per instrument and nobody there has
     * ever had to think about it. This is cheap — one row per instrument actually opened — and it
     * removes an irritation that is paid on every single symbol switch.
     */
    fun start() {
        restoreDrawings()
        // Either store is reason enough to read before loading: one carries this symbol's own
        // settings and the other the layout the reader last used, and a caller that bound only the
        // second still expects it to be on the chart when it opens.
        if (symbolStateRestored || (symbolStates == null && layouts == null)) {
            if (_state.value.series.isEmpty && loadJob == null) reload()
            return
        }
        if (loadJob != null) return
        // Claimed before the read so a second `start()` from a recomposition cannot start a second
        // restore and race the first one's write-back.
        symbolStateRestored = true
        _state.update { it.copy(loading = true, error = null) }
        loadJob = scope.launch {
            val saved = runCatching { symbolStates?.state(symbol)?.first() }.getOrNull()
            if (saved != null) {
                applySymbolState(saved)
            } else {
                // Nothing for this symbol, so the reader's last layout is the best guess at the
                // apparatus they want — it is what they were working in. A saved per-symbol row
                // always wins over it, which is why this is the else branch and not a preamble.
                restoreLastOpenedLayout()
            }
            loadJob = null
            reload()
        }
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
                val arriving = stored.filterNot { it.id in existing }
                val merged = current.drawing.copy(
                    drawings = arriving.map(StoredDrawing::toDrawing) + current.drawing.drawings,
                )
                // The magnet bindings, folded back on top of the drawings themselves.
                //
                // Without this half the binding is written on every save and read by nothing,
                // which is the same as not storing it: the point a reader snapped to the low of a
                // bar is restored as a bare price, so a later revision of that bar leaves the
                // anchor a few ticks off the low it was drawn against and nothing on the chart
                // says why. `PriceChannel.decode` answers null for a missing or unrecognised name,
                // which is exactly what a row written before channels existed decodes to.
                current.copy(
                    drawing = arriving.fold(merged) { state, row ->
                        DrawingActions.withChannels(
                            state,
                            row.id,
                            row.channels.map(PriceChannel::decode),
                        )
                    },
                )
            }
        }
    }

    /**
     * Puts a stored row onto the chart, ignoring anything this build no longer understands.
     *
     * Every id is resolved rather than trusted. A row written by a later build can name an
     * interval, a chart type, a scale mode or an indicator that has since been renamed or removed,
     * and the honest answer to each of those is to keep the app's current value for that one field
     * — not to discard the row, which would throw away four good settings because of a fifth.
     */
    private fun applySymbolState(saved: SymbolChartState) {
        val interval = ChartInterval.of(saved.timeframe)
        val type = ChartType.entries.firstOrNull { it.name == saved.chartType }
        val mode = saved.scaleMode
            ?.let { name -> PriceScaleMode.entries.firstOrNull { it.name == name } }
            // A row from before the axis had four modes carries only the boolean.
            ?: PriceScaleMode.LOGARITHMIC.takeIf { saved.logScale }
        _state.update { current ->
            current.copy(
                interval = interval ?: current.interval,
                chartType = type ?: current.chartType,
                activeIndicators = saved.indicators
                    .filter { id -> ChartCatalog.INDICATORS.any { it.id == id } }
                    .toSet(),
                indicatorPeriods = saved.indicatorPeriods.filterKeys { ChartCatalog.periodOf(it) != null },
                scaleMode = mode ?: current.scaleMode,
            )
        }
    }

    /** The layout the reader last applied anywhere, put back on a cold open. See [start]. */
    private suspend fun restoreLastOpenedLayout() {
        val store = layouts ?: return
        val id = runCatching { store.lastOpened().first() }.getOrNull() ?: return
        val layout = runCatching { store.get(id) }.getOrNull() ?: return
        putLayoutOn(layout)
    }

    /**
     * Writes the current set back. Called after every change that alters what is on the chart.
     *
     * The whole drawing state goes in, not just the list, because a point placed with the magnet
     * on is stored with the OHLC channel it bound to and those bindings live on the state rather
     * than on the drawing — see `DrawingActions.channelsOf`. Saving the list alone wrote the
     * anchor's price and forgot what the reader actually chose, which is «the low of that bar».
     */
    private fun persistDrawings() {
        val store = drawings ?: return
        val snapshot = _state.value.drawing
        scope.launch {
            runCatching { store.save(symbol, snapshot.drawings.map { it.toStored(snapshot) }) }
        }
    }

    /**
     * Writes this symbol's apparatus back, after every change to any part of it.
     *
     * Written on every change rather than on leaving the screen, because there is no reliable
     * "leaving": the process can be killed from the recents list between one bar and the next, and
     * a setting that is only saved on a graceful exit is a setting that is lost exactly when the
     * reader is most annoyed. The store packs every symbol into one preferences string, so this is
     * one small write and not one per field.
     */
    private fun persistSymbolState() {
        val store = symbolStates ?: return
        if (!symbolStateRestored) return
        val current = _state.value
        val row = SymbolChartState(
            symbol = symbol,
            timeframe = current.interval.wire,
            chartType = current.chartType.name,
            indicators = current.activeIndicators.toList(),
            indicatorPeriods = current.indicatorPeriods,
            scaleMode = current.scaleMode.name,
            logScale = current.logScale,
            updatedAt = System.currentTimeMillis(),
        )
        scope.launch { runCatching { store.put(row) } }
    }

    /**
     * Switch interval, which is a reload rather than a transform.
     *
     * The bars themselves are different — an H4 bar is not four H1 bars unless the feed says so —
     * so anything derived from them goes with it, and every comparison is fetched again on the new
     * grid. The drawings do not go: they are anchored in (time, price) and mean the same thing on
     * every interval, which is the whole reason they are stored that way.
     */
    fun setInterval(interval: ChartInterval) {
        if (interval == _state.value.interval) return
        _state.update {
            it.copy(interval = interval, series = CandleSeries.EMPTY, hasMore = false)
        }
        persistSymbolState()
        reload()
        refetchComparisons()
    }

    /**
     * The preset form of [setInterval], for the keyboard shortcuts and the strip.
     *
     * Kept as its own name rather than making every caller wrap a [Timeframe] by hand: those two
     * call sites choose from the fifteen and have no custom interval to express, and a wrapper
     * repeated at each of them is a wrapper somebody eventually writes differently.
     */
    fun setTimeframe(timeframe: Timeframe) = setInterval(ChartInterval.Preset(timeframe))

    fun setChartType(type: ChartType) {
        _state.update { it.copy(chartType = type) }
        persistSymbolState()
    }

    /** Switch what the price axis measures. See `PriceScaleMode`. */
    fun setScaleMode(mode: PriceScaleMode) {
        if (mode == _state.value.scaleMode) return
        _state.update { it.copy(scaleMode = mode) }
        persistSymbolState()
    }

    /** Flip the axis so the low is at the top, or back. Apparatus; not persisted per symbol. */
    fun toggleInverted() = _state.update { it.copy(inverted = !it.inverted) }

    /** Tie the price axis to the bar axis, or let them move independently. */
    fun setPriceBarLock(locked: Boolean) = _state.update { it.copy(priceBarLock = locked) }

    /** Pin the label precision, or pass null to go back to deriving it from the range. */
    fun setDecimals(decimals: Int?) = _state.update { it.copy(decimals = decimals) }

    /** Move the price gutter, or merge every series onto one axis. See `ScaleSide`. */
    fun setScaleSide(side: ScaleSide) = _state.update { it.copy(scaleSide = side) }

    /** Grow or shrink the indicator panes, as a factor on the current setting. */
    fun scalePanes(factor: Float) = _state.update {
        if (factor <= 0f || !factor.isFinite()) it else it.copy(paneScale = it.paneScale * factor)
    }

    fun toggleIndicator(id: String) {
        _state.update { old ->
            old.copy(
                activeIndicators = if (id in old.activeIndicators) {
                    old.activeIndicators - id
                } else {
                    old.activeIndicators + id
                },
            )
        }
        persistSymbolState()
    }

    /**
     * Set one indicator's lookback, or clear it back to the default.
     *
     * Clamped by the catalogue, so a value from an older build's wider bounds cannot produce a
     * chart of nulls. Clearing removes the key rather than writing the default in: the default is
     * allowed to change, and a stored copy of it would pin the old one forever.
     */
    fun setIndicatorPeriod(id: String, period: Int?) {
        _state.update { old ->
            val bounds = ChartCatalog.periodOf(id) ?: return@update old
            old.copy(
                indicatorPeriods = if (period == null || period == bounds.default) {
                    old.indicatorPeriods - id
                } else {
                    old.indicatorPeriods + (id to period.coerceIn(bounds.min, bounds.max))
                },
            )
        }
        persistSymbolState()
    }

    /**
     * Arm a tool, or a mode.
     *
     * The eraser is the one rail entry that arms nothing and still has to change what a tap does.
     * `DrawingActions.arm` refuses every `ToolGroup.MODES` entry by design — a mode places no
     * points — so it is recorded separately in [ChartUiState.eraser] and handed to the canvas as
     * its own flag. Without that the eraser was a button a reader could press and then find had
     * done nothing, which is the worst of the three possible behaviours.
     */
    fun arm(tool: DrawingTool?) = _state.update {
        it.copy(
            drawing = DrawingActions.arm(it.drawing, tool),
            eraser = tool?.id == ERASER_TOOL,
        )
    }

    /**
     * Arm a tool with a saved style already on it.
     *
     * One call rather than an arm followed by two setters, because the three have to land in the
     * same state update: a reader who picks «خط روند ۲ نقطه‌ای قرمز» from the template row and
     * starts drawing in the next frame must not place the first line in the previous colour.
     */
    fun armWithStyle(tool: DrawingTool?, colour: Long, widthDp: Float) = _state.update {
        it.copy(
            drawing = DrawingActions.arm(it.drawing, tool).copy(colour = colour),
            eraser = tool?.id == ERASER_TOOL,
            drawingWidthDp = usableWidth(widthDp),
        )
    }

    fun onDrawing(next: DrawingState) {
        // The one hot path. A drag emits a state per frame — that is what makes the line follow
        // the finger — and none of those frames touches the bars, the indicator set or a lookback.
        // Carrying the computed value forward is what stops each of them recomputing every
        // switched-on indicator; `ChartDerived.matches` re-checks it, so a carry that has gone
        // stale is discarded rather than drawn.
        _state.update { current ->
            // What comes back is the *filtered* list the canvas was given, so the hidden drawings
            // have to be put back before anything else looks at it — including `persistDrawings`,
            // which would otherwise write a set with the hidden ones missing and lose them for
            // good on the next restore.
            val stamped = stampWidth(next.drawings, current.drawing.drawings, current.drawingWidthDp)
            val restored = next.copy(
                drawings = mergeHidden(stamped, current.drawing.drawings, current.hiddenDrawingIds),
            )
            current.copy(drawing = restored, carried = current.derived)
        }
        persistDrawings()
    }

    /**
     * Show or hide one drawing, without touching the drawing itself.
     *
     * Not persisted, deliberately. Hiding is how a reader gets one object out of the way while
     * they read something under it; a hidden state that survived a restart would be a drawing they
     * cannot see, cannot remember hiding, and would report as lost.
     */
    fun setDrawingHidden(id: Long, hidden: Boolean) = _state.update { current ->
        val ids = if (hidden) current.hiddenDrawingIds + id else current.hiddenDrawingIds - id
        if (ids == current.hiddenDrawingIds) current else current.copy(hiddenDrawingIds = ids)
    }

    /** Flip one drawing's visibility, for a row whose eye has no value of its own. */
    fun toggleDrawingHidden(id: Long) =
        setDrawingHidden(id, id !in _state.value.hiddenDrawingIds)

    /**
     * Select one drawing, which is what shows its handles on the canvas.
     *
     * The object tree's whole purpose: forty drawings cannot be found by tapping, and this is how
     * a row in a list becomes the object on the chart. Passing null clears the selection.
     */
    fun selectDrawing(id: Long?) =
        _state.update { it.copy(drawing = DrawingActions.select(it.drawing, id)) }

    /**
     * Move one drawing in the z-order and remember it.
     *
     * [toIndex] is an index into the state's own list, where 0 is the back of the chart — see
     * `ObjectTree.reorder`, which does the arithmetic and clamps an overshooting drag rather than
     * ignoring it. Persisted, because z-order is part of what the reader arranged: the note they
     * pushed behind their trend lines has to still be behind them tomorrow.
     */
    fun reorderDrawing(id: Long, toIndex: Int) {
        _state.update {
            it.copy(drawing = it.drawing.copy(drawings = ObjectTree.reorder(it.drawing.drawings, id, toIndex)))
        }
        persistDrawings()
    }

    /** Put one drawing on top, which also makes it the one a tap on an overlap finds. */
    fun bringDrawingToFront(id: Long) {
        _state.update {
            it.copy(drawing = it.drawing.copy(drawings = ObjectTree.bringToFront(it.drawing.drawings, id)))
        }
        persistDrawings()
    }

    /** Put one drawing behind everything else. The way out of "my note covers the candles". */
    fun sendDrawingToBack(id: Long) {
        _state.update {
            it.copy(drawing = it.drawing.copy(drawings = ObjectTree.sendToBack(it.drawing.drawings, id)))
        }
        persistDrawings()
    }

    /**
     * Put a saved style onto one drawing that is already on the chart.
     *
     * A locked drawing is left alone, the same rule `DrawingActions.recolour` follows: colour and
     * width are edits, and the lock exists precisely so that a stray gesture cannot edit. The row
     * that offers this is dimmed for a locked drawing, so the refusal is visible before it happens
     * rather than being discovered by tapping.
     */
    fun applyTemplateToDrawing(id: Long, colour: Long, widthDp: Float) {
        _state.update {
            it.copy(drawing = it.drawing.copy(drawings = applyStyle(it.drawing.drawings, id, colour, widthDp)))
        }
        persistDrawings()
    }

    /**
     * Set the style the next drawing is placed in, without arming anything.
     *
     * What the template row does when the reader is not choosing a tool at the same time — for
     * instance from a drawing's own settings, where they have just saved the current style and
     * want the next line to match.
     */
    fun setDrawingStyle(colour: Long, widthDp: Float) = _state.update {
        it.copy(
            drawing = it.drawing.copy(colour = colour),
            drawingWidthDp = usableWidth(widthDp),
        )
    }

    /**
     * Paint the chart with one of the saved colour templates, or with the theme's own.
     *
     * Not persisted per symbol: a palette is a property of the reader's eyes and their room, not
     * of the instrument, and a chart that switched colours when they switched symbol would be the
     * "settings are global" complaint inverted into something stranger. It travels with a saved
     * layout instead — see `ChartLayout.colourTemplate` — which is the object that already means
     * "the apparatus I look through".
     */
    fun setColourTemplate(template: ChartColourTemplate?) =
        _state.update { it.copy(colourTemplate = template) }

    fun undoDrawing() {
        _state.update { it.copy(drawing = DrawingActions.undo(it.drawing)) }
        persistDrawings()
    }

    fun cancelDrawing() = _state.update { it.copy(drawing = DrawingActions.cancel(it.drawing)) }

    /** Lock or unlock one drawing, and remember it. See [com.coinepro.core.chart.Drawing.locked]. */
    fun setDrawingLocked(id: Long, locked: Boolean) {
        _state.update { it.copy(drawing = DrawingActions.setLocked(it.drawing, id, locked)) }
        persistDrawings()
    }

    fun deleteDrawing(id: Long) {
        _state.update { it.copy(drawing = DrawingActions.delete(it.drawing, id)) }
        persistDrawings()
    }

    /* -------------------------------------------------------------- comparison */

    /**
     * Draw a second instrument over this one.
     *
     * Answers immediately with the reason it will not, or null once the fetch has been started —
     * so the caller can say «همین حالا روی نمودار است» rather than watching a button do nothing.
     * A `null` answer is not a promise that the line will appear: the load can still fail, and
     * when it does the comparison is dropped without touching the chart. That asymmetry is
     * deliberate. A comparison is an addition to a picture that is already correct, and failing
     * the whole chart because a second feed was unreachable would take away the thing the reader
     * actually came for.
     */
    fun addComparison(symbol: String): ComparisonRefusal? {
        val current = _state.value
        val refusal = refuseComparison(current.symbol, current.comparisons.map { it.symbol }, symbol)
        if (refusal != null) return refusal
        val wanted = symbol.trim().uppercase()
        scope.launch {
            val page = runCatching { gateway.load(wanted, current.interval) }.getOrNull() ?: return@launch
            val series = CandleSeries(page.candles.map(OhlcBar::toCandle))
            if (series.isEmpty) return@launch
            comparisonSources[wanted] = series
            realignComparisons()
        }
        return null
    }

    /** Take one off. Its source bars go too, so a re-add fetches rather than redrawing stale ones. */
    fun removeComparison(symbol: String) {
        comparisonSources.remove(symbol.trim().uppercase())
        realignComparisons()
    }

    /** Switch how the comparisons are expressed. See `ComparisonBasis`. */
    fun setComparisonBasis(basis: ComparisonBasis) =
        _state.update { it.copy(comparisonBasis = basis) }

    /**
     * Rebuild every comparison against the base bars as they are now.
     *
     * Called after any change to the base series — a reload, a page-back, a fresh comparison — and
     * never skipped as an optimisation. A [ComparisonSeries] is one value per base bar by
     * construction; leaving a stale one in place after the base grew by a page would draw an
     * overlay a hundred bars out of register with the candles under it, and nothing about the
     * picture would say so.
     *
     * The colour is the slot's, not the symbol's, so removing the first of three re-colours the
     * other two. That is the right way round: the palette's job is to keep the lines apart from
     * each other and from the candles, and it can only do that if it is assigned by position.
     */
    private fun realignComparisons() {
        val base = _state.value.series
        val aligned = comparisonSources.entries.mapIndexed { index, (symbol, series) ->
            align(
                base = base,
                other = series,
                symbol = symbol,
                label = symbol,
                colour = comparisonColour(index),
            )
        }
        _state.update { it.copy(comparisons = aligned) }
    }

    /**
     * Fetch every comparison again on the new interval.
     *
     * Not re-aligned from what is held: the bars themselves are a different length now, and
     * stretching a set of hourly closes across daily bars would draw a line that is wrong
     * everywhere and looks plausible. One that fails to come back is dropped, for the reason given
     * in [addComparison].
     */
    private fun refetchComparisons() {
        val symbols = comparisonSources.keys.toList()
        if (symbols.isEmpty()) return
        val interval = _state.value.interval
        scope.launch {
            for (compared in symbols) {
                val page = runCatching { gateway.load(compared, interval) }.getOrNull()
                val series = page?.candles?.map(OhlcBar::toCandle)?.let(::CandleSeries)
                if (series == null || series.isEmpty) {
                    comparisonSources.remove(compared)
                } else {
                    comparisonSources[compared] = series
                }
            }
            realignComparisons()
        }
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

    /**
     * Jump to a bar the reader named — a date typed into the transport, not a drag of the scrub.
     *
     * Separate from [replaySeek] because the intent is different and the state machine treats it
     * so: a jump stops playback, since landing on the bar you went to look at and immediately
     * being carried past it costs you the thing you came for. An index off either end is clamped
     * rather than refused, because the caller computes it from a date and an off-by-one there must
     * not end a practice session.
     */
    fun replayGoTo(index: Int) = withReplay { Replay.goTo(it, index) }

    /**
     * Walk the cursor to the newest bar of the snapshot, still inside replay.
     *
     * Not [exitReplay]. Leaving throws the snapshot away and hands the chart back to the live feed;
     * this reveals the rest of it so the run can be reviewed end to end first. A reader who means
     * "get me out" has the exit button next to this one.
     */
    fun replayJumpToLive() = withReplay(Replay::jumpToLive)

    /**
     * Change speed by ladder step, which is what the picker calls.
     *
     * The `Double` overload stays for a caller holding a persisted multiplier, where an unknown
     * value has to be ignorable. This one cannot fail: the type is the validation.
     */
    fun replaySetSpeed(speed: ReplaySpeed) = withReplay { Replay.setSpeed(it, speed) }

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
                    delay(Replay.delayMillis(_state.value.replay.speedStep))
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
     * Applies a layout: type, interval, the whole indicator set, its periods, and the axis.
     *
     * The set is *replaced*, not merged. A layout that added its indicators to whatever was already
     * on would drift towards every indicator being on at once, which is the state a layout exists
     * to escape.
     *
     * The interval is resolved with `ChartInterval.of` rather than matched against the enum's
     * names, which is what this did and which quietly lost two whole classes of layout: a custom
     * interval saved as `205`, and every preset saved before the wire spelling and the enum name
     * were guaranteed to agree. Both used to fall through as "unknown" and leave the reader on
     * whatever they happened to be looking at, with no sign that half the layout had not applied.
     */
    fun applyLayout(layout: ChartLayout) {
        val before = _state.value.interval
        putLayoutOn(layout)
        persistSymbolState()
        // The reload is here rather than inside [putLayoutOn], because the cold-open restore uses
        // that method too and `start` issues the one load itself. Two loads for one open is not
        // merely wasteful: the second one lands over the first and the chart visibly redraws.
        if (_state.value.interval != before) {
            _state.update { it.copy(series = CandleSeries.EMPTY, hasMore = false) }
            reload()
            refetchComparisons()
        }
        layouts?.let { store -> scope.launch { runCatching { store.setLastOpened(layout.id) } } }
    }

    /**
     * The half of [applyLayout] that only touches this controller's state.
     *
     * Split out because the cold-open restore needs exactly this and must not do the other three
     * things: writing the per-symbol row back before the reader has changed anything would stamp a
     * layout's settings onto a symbol they never applied it to, re-recording the last-opened id
     * would be recording what it just read, and a reload here would be the second one of the open.
     */
    private fun putLayoutOn(layout: ChartLayout) {
        val type = ChartType.entries.firstOrNull { it.name == layout.chartType }
        val interval = ChartInterval.of(layout.timeframe)
        val mode = PriceScaleMode.entries.firstOrNull { it.name == layout.scaleMode }
        // Unknown ids are skipped rather than failing the whole apply: a layout saved by an older
        // build may name an indicator this one has renamed, and losing one line is better than
        // losing the layout.
        _state.update { current ->
            current.copy(
                interval = interval ?: current.interval,
                chartType = type ?: current.chartType,
                activeIndicators = layout.indicators
                    .filter { id -> ChartCatalog.INDICATORS.any { it.id == id } }
                    .toSet(),
                indicatorPeriods = layout.indicatorPeriods
                    .filterKeys { ChartCatalog.periodOf(it) != null },
                scaleMode = mode ?: current.scaleMode,
            )
        }
        applyColourTemplate(layout.colourTemplate)
    }

    /**
     * Put a layout's palette on the chart, resolving the stored id against what is saved now.
     *
     * Asynchronous because the templates are on disk and the two built-ins are prepended at read
     * time rather than stored, so there is nothing to resolve against without asking the store. A
     * null id is an explicit "the theme's own colours" and clears whatever is on the chart; an id
     * that no longer resolves leaves the palette alone rather than clearing it, because a layout
     * that lost its template should not also take away the colours the reader is looking at.
     */
    private fun applyColourTemplate(id: String?) {
        if (id == null) {
            _state.update { it.copy(colourTemplate = null) }
            return
        }
        val store = layouts ?: return
        scope.launch {
            val template = runCatching { store.templates().first().firstOrNull { it.id == id } }
                .getOrNull() ?: return@launch
            _state.update { it.copy(colourTemplate = template) }
        }
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
                gateway.load(current.symbol, current.interval, before = oldest)
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
                // The base grew at its left edge, so every overlay is now a page short of it.
                realignComparisons()
                // History is cached too. Paging back is the second place a reader waits, and a
                // reader who pans back over the same week twice should only pay for it once.
                runCatching { cache.write(current.symbol, current.interval, page.candles) }
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
            paintFromCache(current.symbol, current.interval)
            // Wall clock rather than `AppLog.timed`, because what is being measured is not the
            // gateway call: it is the interval a reader spends looking at an empty chart, which
            // ends when the state carrying the bars is published, not when the response lands.
            val startedAt = System.nanoTime()
            runCatching { gateway.load(current.symbol, current.interval) }
                .onSuccess { page ->
                    _state.update {
                        it.copy(
                            series = CandleSeries(page.candles.map(OhlcBar::toCandle)),
                            loading = false,
                            error = null,
                            hasMore = page.hasMore,
                        )
                    }
                    realignComparisons()
                    // Written after the state, not before: the reader's chart is the thing that
                    // matters and a slow disk must never sit between them and their candles.
                    runCatching { cache.write(current.symbol, current.interval, page.candles) }
                    val millis = (System.nanoTime() - startedAt) / 1_000_000
                    val fields = mapOf(
                        "symbol" to current.symbol,
                        "tf" to current.interval.wire,
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
                    _state.update {
                        // A failure over a chart that already has cached bars on it is not an
                        // error *screen*. The reader can see prices, they are real, and they are
                        // merely old — so the failure is reported without throwing away the only
                        // useful thing on the surface. `ChartFailure` is shown only when there is
                        // genuinely nothing to look at.
                        it.copy(loading = false, error = failure.toChartError())
                    }
                    log?.warn(
                        LogTag.CHART,
                        "chart load failed",
                        mapOf(
                            "symbol" to current.symbol,
                            "tf" to current.interval.wire,
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

    /**
     * Draw whatever is cached for this series, if the chart is empty and still waiting.
     *
     * Three guards, and each one prevents a specific wrong picture:
     *
     *  * **Only when the chart is empty.** A reload over a chart that already has bars — a retry,
     *    a refresh — must not flash older cached ones in between.
     *  * **Only while this load is still the current one.** A reader who switches interval twice
     *    quickly has two loads in flight, and the first one's cache landing after the second's
     *    network answer would put the wrong series on screen.
     *  * **Silently on failure.** A cache that can fail a chart open turns a slow path into a
     *    broken one.
     */
    private suspend fun paintFromCache(symbol: String, interval: ChartInterval) {
        val cached = runCatching { cache.read(symbol, interval) }.getOrDefault(emptyList())
        if (cached.isEmpty()) return
        _state.update { current ->
            if (current.symbol != symbol || current.interval != interval || !current.series.isEmpty) {
                current
            } else {
                // `loading` stays true: the fetch is still out, the spinner still belongs, and the
                // reader now has something to look at while it runs. Those are not in conflict.
                current.copy(series = CandleSeries(cached.map(OhlcBar::toCandle)))
            }
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
 * What the switched-on indicators draw, computed once for a given set of inputs.
 *
 * Three inputs and nothing else: the bars, which indicators are on, and their lookbacks. Anything
 * else the reader does — panning, zooming, drawing, selecting, replaying a frame — leaves all
 * three untouched, which is what makes carrying this value forward correct rather than merely
 * fast.
 */
data class ChartDerived internal constructor(
    /**
     * The three inputs this was computed from, so a carried value can be *checked* rather than
     * trusted.
     *
     * This is what makes carrying it forward safe by construction: a state that changed its bars,
     * its indicators or a lookback and still holds the old value simply recomputes, because the
     * key no longer matches. Without it, one forgotten call site would draw last timeframe's
     * moving average over this timeframe's candles and nothing would ever say so.
     *
     * The series is compared by identity, which is the point — it is a large object that is
     * replaced wholesale when it changes, never mutated.
     */
    internal val key: Key? = null,
    val overlays: List<ChartLine> = emptyList(),
    val levels: List<PriceLevel> = emptyList(),
    val markers: List<ChartMarker> = emptyList(),
    val panes: List<ChartPane> = emptyList(),
) {
    /** What [ChartDerived] was computed from. See [key]. */
    internal data class Key(
        val series: CandleSeries,
        val active: Set<String>,
        val periods: Map<String, Int>,
        /** The correlation partner, which changes what one pane draws. See [ChartUiState.derived]. */
        val comparison: ComparisonSeries? = null,
    )

    /** Whether this value is still the right answer for these inputs. */
    internal fun matches(
        series: CandleSeries,
        active: Set<String>,
        periods: Map<String, Int>,
        comparison: ComparisonSeries? = null,
    ): Boolean = key != null &&
        key.series === series &&
        key.active == active &&
        key.periods == periods &&
        key.comparison == comparison

    companion object {
        internal val EMPTY = ChartDerived()

        fun of(
            series: CandleSeries,
            active: Set<String>,
            periods: Map<String, Int>,
            /** The second instrument, for the one study that measures two series against each other. */
            comparison: ComparisonSeries? = null,
        ): ChartDerived {
            val key = Key(series, active, periods, comparison)
            if (active.isEmpty() || series.isEmpty) return ChartDerived(key = key)
            val chosen = ChartCatalog.INDICATORS.filter { it.id in active }
            // Each structure study computed **once** and its three products taken from the one
            // answer. Three separate calls is what this file used to do, and a zigzag over three
            // hundred bars is not a cheap thing to compute twice for nothing.
            val structures = chosen
                .filter { it.pane == IndicatorPane.STRUCTURE }
                .map { ChartCatalog.structureFor(it, series) }
            return ChartDerived(
                key = key,
                overlays = chosen
                    .filter { it.pane == IndicatorPane.PRICE }
                    .flatMap { ChartCatalog.overlayFor(it, series, periods[it.id]) } +
                    structures.flatMap { it.lines },
                levels = structures.flatMap { it.levels },
                markers = structures.flatMap { it.markers },
                panes = chosen
                    .filter { it.pane == IndicatorPane.SEPARATE }
                    .mapNotNull { ChartCatalog.paneFor(it, series, periods[it.id], comparison) }
                    // A pane with neither a line nor a histogram is a titled empty box, and
                    // `correlation` returns exactly that when there is no second instrument
                    // loaded to correlate against. Dropped here rather than drawn: a strip of
                    // blank canvas under the candles reads as a rendering fault, and the reader
                    // has no way to tell it from one.
                    .filter { it.lines.isNotEmpty() || it.histogram != null },
            )
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

/**
 * The two halves of the drawing mapping.
 *
 * They live here rather than in either module because `core:chart` has no business knowing about
 * DataStore and `core:datastore` has no business knowing about the chart engine — and this is the
 * one file where both are already on the classpath.
 */
private fun Drawing.toStored(state: DrawingState): StoredDrawing = StoredDrawing(
    id = id,
    toolId = toolId,
    points = points.map { it.time to it.price },
    colour = colour,
    widthDp = widthDp,
    text = text,
    direction = direction.name,
    locked = locked,
    // One entry per point, in the same order, and null wherever the magnet was off. What the
    // reader chose at a snapped point is «the low of that bar», not a number — see
    // `DrawingActions.channelsOf` and the note on [ChartController.persistDrawings].
    channels = DrawingActions.channelsOf(state, this).map { it?.name },
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
    locked = locked,
)
