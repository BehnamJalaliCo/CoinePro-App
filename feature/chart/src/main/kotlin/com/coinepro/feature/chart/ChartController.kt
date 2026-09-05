package com.coinepro.feature.chart

import com.coinepro.core.chart.ArrowDirection
import com.coinepro.core.chart.BarField
import com.coinepro.core.chart.BarWindow
import com.coinepro.core.chart.CandlePatterns
import com.coinepro.core.chart.ChainOutcome
import com.coinepro.core.chart.ChainPlot
import com.coinepro.core.chart.ChainedIndicator
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ChartColours
import com.coinepro.core.chart.ChartLegendTarget
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
import com.coinepro.core.chart.DrawingImages
import com.coinepro.core.chart.DrawingLayer
import com.coinepro.core.chart.DrawingState
import com.coinepro.core.chart.DrawingSync
import com.coinepro.core.chart.DrawingTool
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.IndicatorChain
import com.coinepro.core.chart.IndicatorPane
import com.coinepro.core.chart.IndicatorSource
import com.coinepro.core.chart.LineStyleKind
import com.coinepro.core.chart.MagnetMode
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
import com.coinepro.core.chart.ChartHistory
import com.coinepro.core.chart.align
import com.coinepro.core.chart.comparisonColour
import com.coinepro.core.chart.resident
import com.coinepro.core.datastore.ChartColourTemplate
import com.coinepro.core.datastore.ChartDrawingStore
import com.coinepro.core.datastore.ChartLayout
import com.coinepro.core.datastore.ChartLayoutStore
import com.coinepro.core.datastore.DrawingSyncMode
import com.coinepro.core.datastore.DrawingImageStore
import com.coinepro.core.datastore.DrawingSyncStore
import com.coinepro.core.datastore.IndicatorTemplate
import com.coinepro.core.datastore.StoredDrawing
import com.coinepro.core.datastore.SymbolChartState
import com.coinepro.core.datastore.SymbolChartStateStore
import com.coinepro.core.diagnostics.AppLog
import com.coinepro.core.diagnostics.LogTag
import com.coinepro.core.marketdata.CandleArchive
import com.coinepro.core.marketdata.CandleCache
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.ChartTickSource
import com.coinepro.core.marketdata.HISTORY_PAGE_BARS
import com.coinepro.core.marketdata.HISTORY_PAGE_BUDGET
import com.coinepro.core.marketdata.NoChartTicks
import com.coinepro.core.marketdata.NoOpCandleArchive
import com.coinepro.core.marketdata.NoOpCandleCache
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.PriceTick
import com.coinepro.core.marketdata.fillHistory
import com.coinepro.core.marketdata.resolveCandleRequest
import com.coinepro.core.marketdata.Timeframe
import com.coinepro.core.marketdata.of
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
     * Whether there is a step to take back, and one to put back.
     *
     * Published on the state rather than read off the controller so the buttons that offer them
     * can be disabled the frame the stack empties. See [ChartHistory] for what a step is.
     */
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
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
    /**
     * The colour and the stroke the reader gave an indicator, by id — the settings sheet's Style
     * tab. Sparse, like the periods: absent means the catalogue's own. Applied where the lines are
     * read, so a study's every line that was drawn in the catalogue colour takes the new one and
     * its secondary shades — a signal line, a band's fill — keep their relation to it.
     */
    val indicatorColours: Map<String, Long> = emptyMap(),
    val indicatorWidths: Map<String, Float> = emptyMap(),
    /**
     * The indicators switched on but not drawn — the legend's eye, and the settings sheet's
     * Visibility tab. Hidden, not off: the period and the style are kept for the way back.
     */
    val hiddenIndicators: Set<String> = emptySet(),
    val drawing: DrawingState = DrawingState(),
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
     * How many bars this app holds on disk for this series, across every session.
     *
     * Not the same number as `series.size` and the difference is the whole point of the archive:
     * the chart holds a window, the archive holds everything that has ever been fetched. It is a
     * **fact** and is worded to the reader as one — see `CandleArchive.MAX_BARS_PER_SERIES`, which
     * is a ceiling on capacity and must never be read out as a promise about history that exists.
     * Zero until the first load lands, or wherever no archive is wired.
     */
    val archivedBars: Int = 0,
    /**
     * Whether the venue has genuinely run out of history for this series.
     *
     * Separate from `!hasMore`, which only says the last page came back short. This is the answer
     * a backward fill established by walking to the end, and it is the honest basis for a chart
     * that stops offering «بیشتر» rather than one that keeps asking a server that has nothing.
     */
    val venueExhausted: Boolean = false,
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
     * The quick range the reader last tapped, or null when they chose a bar length directly.
     *
     * Held so the range row can light the pill that is in force. It cannot be derived from
     * [interval], because three ranges resolve to the daily bar — «۳ ماه», «۶ ماه» and «۱ سال» are
     * all drawn on D1 — and a row that lit all three, or guessed one of them, would be telling the
     * reader something they did not choose. Cleared by [ChartController.setInterval], because a
     * reader who has just picked H1 by hand is no longer looking at a range.
     */
    val range: ChartRange? = null,
    /**
     * Whether the next tap on the canvas *adds* to the selection instead of replacing it.
     *
     * A latch rather than a modifier key, because a phone has no modifier key and the alternative
     * — long-press-then-tap — collides with the long press that already offers an alert on a level
     * and the one that erases a whole object. It is armed from the floating toolbar that appears
     * on the first selection, so it is only ever reachable once there is something to add to, and
     * it turns itself off with the selection.
     *
     * Held here rather than in `DrawingState` because `DrawingActions.select` already takes an
     * `additive` flag and the canvas cannot pass one — the canvas calls `tapSnapped`, which
     * selects with `additive = false` by design. This is the bit that lets [ChartController.onDrawing]
     * re-apply the reader's intent on the way back in.
     */
    val multiSelect: Boolean = false,
    /**
     * The bars on screen, as the renderer last reported them.
     *
     * One study reads it — the visible-range volume profile — and reads nothing else, which is why
     * it is a whole field rather than a flag: its answer is a function of where the reader has
     * panned to, and until the viewport crossed back over this boundary the profile was computed
     * once against the whole series and never followed a pan.
     *
     * It is in [ChartDerived]'s key, but it is checked *apart* from the other five and repaired
     * rather than invalidated — see [ChartDerived.rewindowed]. And it is only published while
     * something reads it, so on an ordinary chart this field does not move at all as the reader
     * drags; see [ChartController.setVisibleWindow] for both, and for the drag they were costing.
     */
    val window: BarWindow = BarWindow.WHOLE_SERIES,
    /**
     * What each indicator is computed on, where the reader has said something other than the close.
     *
     * Sparse on purpose. An entry here is an *override*; every switched-on indicator with no entry
     * reads the candles, and [chainNodes] materialises the full list on demand. Storing the whole
     * list instead would mean keeping it in step with [activeIndicators] at four call sites, and the
     * one that was forgotten would leave a node for an indicator the reader had switched off — which
     * `IndicatorChain.evaluate` refuses as a missing source, taking the whole chain down with it.
     */
    val chainSources: Map<String, IndicatorSource> = emptyMap(),
    /**
     * The candlestick patterns the reader wants marked.
     *
     * Ids from `CandlePatterns.OPTIONS`, deliberately outside [activeIndicators]: a pattern has no
     * value per bar, no lookback and no pane, and putting one in the indicator set would hand every
     * consumer of that set — the period stepper, the layout store, the chain — a row that answers
     * none of their questions.
     */
    val patterns: Set<String> = emptySet(),
    /**
     * How far a newly placed drawing travels between layouts.
     *
     * The reader's stored default, read back from `DrawingSyncStore` and stamped onto
     * `DrawingState.sync`. Held here as well so a screen can show which of the three is in force
     * without collecting the store a second time.
     */
    val syncMode: DrawingSyncMode = DrawingSyncMode.NONE,
    /**
     * The bar the chart has been asked to scroll to, or null.
     *
     * Set by «رفتن به تاریخ» — backlog 105 — and consumed by the canvas. It is an index rather than
     * a moment because the viewport counts in bars, and the field that produces it has already
     * resolved a Jalali date against the loaded series.
     */
    val focusIndex: Int? = null,
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
        get() {
            // The layout filter first: a mark set to `DrawingSync.GLOBAL` is a fact about the
            // instrument and shows under every layout, and the other two show under the layout they
            // were made on. `syncedInto` is the one place that rule lives, so the canvas and the
            // object tree cannot disagree about which marks exist.
            val onThisLayout = DrawingActions.syncedInto(drawing.drawings, drawing.layoutId)
            val shown = if (hiddenDrawingIds.isEmpty()) {
                onThisLayout
            } else {
                onThisLayout.filterNot { it.id in hiddenDrawingIds }
            }
            return if (shown.size == drawing.drawings.size) drawing else drawing.copy(drawings = shown)
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
        get() = if (aiVisionWire != null) null else "تحلیل تصویری روی این بازه‌ی زمانی کار نمی‌کند. یکی از بازه‌های ۱ دقیقه، ۵ دقیقه، ۱۵ دقیقه، ۱ ساعت، ۴ ساعت یا ۱ روز را انتخاب کنید."

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
        // **The window is checked last and separately, and that is the fix for «چارت وحشتناک کنده».**
        //
        // It used to be one comparison: a carried value matched only if all six inputs agreed, the
        // window among them. But the window changes on *every bar of a drag*, and exactly one study
        // in the whole catalogue reads it — the visible-range volume profile. So one indicator's
        // input was invalidating all of them, and a reader dragging a chart with five studies on it
        // was paying for a complete recomputation of every one of them, over every resident bar,
        // per bar of movement, on the main thread inside composition. Measured on a desktop JVM at
        // twelve thousand resident bars and five ordinary indicators that is fifteen milliseconds a
        // step; on the phone this app is built for it is several frames, and it is why the drag
        // "went backwards and forwards horribly slowly" rather than merely dropping the odd frame.
        //
        // Now the five inputs that describe *what is computed* are checked together, and the one
        // that describes *where the reader is looking* is applied afterwards by [rewindowed], which
        // recomputes the window-scoped study alone and keeps every other line by reference. A chart
        // with no window-scoped study on it — which is every chart in this app until somebody
        // switches the profile on — reuses the carried value whole.
        val reusable = carried?.takeIf {
            it.matchesApartFromWindow(visibleSeries, activeIndicators, indicatorPeriods, partner, chained)
        }
        reusable?.rewindowed(visibleSeries, activeIndicators, indicatorPeriods, chained, window)
            ?: ChartDerived.of(visibleSeries, activeIndicators, indicatorPeriods, partner, window, chained)
    }

    /**
     * Every switched-on indicator as a chain node, with the reader's own sources folded in.
     *
     * Materialised from [activeIndicators] rather than stored, so it cannot drift out of step with
     * what is switched on — see [chainSources]. The node id is the indicator id, which is what makes
     * "point the RSI at the EMA" expressible without the reader ever meeting an id: an indicator can
     * appear once on a chart, so one node per indicator is the whole graph.
     */
    val chainNodes: List<ChainedIndicator>
        get() = ChartCatalog.INDICATORS
            .filter { it.id in activeIndicators }
            .map { option ->
                ChainedIndicator(
                    nodeId = option.id,
                    indicatorId = option.id,
                    period = indicatorPeriods[option.id],
                    source = chainSources[option.id] ?: IndicatorSource.CANDLES,
                )
            }

    /**
     * The chain, evaluated — or null when the reader has not pointed anything at anything.
     *
     * The guard is what keeps an ordinary chart free: with every node reading the candles there is
     * no chain to walk, [ChartDerived] draws all of it exactly as it always has, and this whole
     * pipeline costs one `any`. It also means a chart with no chain can never be taken down by a
     * refusal, which is the failure mode a always-on evaluator would have.
     */
    internal val chain: ChainOutcome?
        get() = if (chainSources.values.any { it is IndicatorSource.Output }) {
            IndicatorChain.evaluate(visibleSeries, chainNodes)
        } else {
            null
        }

    /**
     * What the chain drew, or nothing.
     *
     * `by lazy` rather than a getter, because both [overlays] and [panes] read it and evaluating a
     * ten-link chain twice per frame is the cost this is worth avoiding. It is safe to cache on the
     * state because a state is immutable: a new chain is a new state.
     */
    private val chainPlot: ChainPlot by lazy(LazyThreadSafetyMode.NONE) {
        when (val outcome = chain) {
            is ChainOutcome.Ready -> IndicatorChain.plot(outcome, chainNodes)
            else -> ChainPlot()
        }
    }

    /**
     * The indicators the chain is drawing, so [ChartDerived] does not draw them a second time.
     *
     * The one thing that has to be right about this arrangement. A chained EMA drawn by both paths
     * is two lines a pixel apart, in the same colour, on a chart the reader cannot debug — and it
     * would look like a rendering fault rather than like double work.
     */
    internal val chained: Set<String>
        get() = when (val outcome = chain) {
            is ChainOutcome.Ready -> outcome.order.toSet()
            else -> emptySet()
        }

    /**
     * Why the chain will not draw, in a sentence the reader can act on, or null.
     *
     * Surfaced beside the indicator list rather than swallowed. A chain with a loop in it has no
     * answer at all, and a picker that silently drew the unchained half would be telling the reader
     * their chain works.
     */
    val chainRefusal: String?
        get() = (chain as? ChainOutcome.Refused)?.message

    /**
     * The price-scale overlays for whatever is switched on.
     *
     * Derived from [visibleSeries], not [series]. During replay an indicator computed over every
     * bar would place a moving average using prices the reader is not allowed to have seen yet —
     * the future leaking back in through the one door nobody watches.
     */
    val overlays: List<ChartLine>
        // The empty case returns the memoised list *itself* rather than a copy of it. `a + b`
        // allocates a new list even when `b` is empty, so this getter used to hand back a fresh
        // list on every read — which is exactly what `ChartDerived` exists to stop, and which the
        // memoisation test caught by identity. Chained indicators are the rare case; an ordinary
        // chart reads this on every frame of a drag.
        get() = if (indicatorsHidden) emptyList()
        else chainPlot.priceLines.ifEmpty { return styledOverlays }.let { styledOverlays + it }

    /** [ChartDerived.overlays] with the reader's colours and widths on them; the list itself when there are none. */
    private val styledOverlays: List<ChartLine>
        get() {
            if (indicatorColours.isEmpty() && indicatorWidths.isEmpty()) return derived.overlays
            return derived.overlays.mapIndexed { index, line ->
                restyled(line, derived.overlayOwners.getOrNull(index))
            }
        }

    private val styledPanes: List<ChartPane>
        get() {
            if (indicatorColours.isEmpty() && indicatorWidths.isEmpty()) return derived.panes
            return derived.panes.mapIndexed { index, pane ->
                val owner = derived.paneOwners.getOrNull(index)
                if (owner == null || (owner !in indicatorColours && owner !in indicatorWidths)) {
                    pane
                } else {
                    pane.copy(
                        lines = pane.lines.map { restyled(it, owner) },
                        histogram = pane.histogram?.let { restyled(it, owner) },
                    )
                }
            }
        }

    private fun restyled(line: ChartLine, owner: String?): ChartLine {
        if (owner == null) return line
        val colour = indicatorColours[owner]
        val width = indicatorWidths[owner]
        if (colour == null && width == null) return line
        val own = ChartCatalog.INDICATORS.firstOrNull { it.id == owner }?.colour
        return line.copy(
            // Only the lines in the catalogue's own colour take the new one; a signal line in a
            // second shade stays distinguishable from the line it signals.
            colour = if (colour != null && line.colour == own) colour else line.colour,
            widthDp = width ?: line.widthDp,
        )
    }

    /**
     * The hidden indicators as the legend addresses them: by their position in the overlay and
     * pane lists. The chart's legend takes this and gives back a target; [indicatorFor] turns it
     * into an id again, which is what keeps one hidden set for the eye and the sheet.
     */
    val hiddenTargets: Set<ChartLegendTarget>
        get() {
            if (hiddenIndicators.isEmpty()) return emptySet()
            val targets = LinkedHashSet<ChartLegendTarget>()
            derived.overlayOwners.forEachIndexed { index, owner ->
                if (owner in hiddenIndicators) targets += ChartLegendTarget.Overlay(index)
            }
            derived.paneOwners.forEachIndexed { index, owner ->
                if (owner in hiddenIndicators) targets += ChartLegendTarget.Pane(index)
            }
            return targets
        }

    val levels: List<PriceLevel> get() = if (indicatorsHidden) emptyList() else derived.levels

    /**
     * Which indicator a legend row belongs to, or null — item 109.
     *
     * The legend hands back an index into the list it was given, and `ChartDerived` keeps the owner
     * of each entry beside it. `getOrNull` rather than an assumption of alignment because a chained
     * overlay is appended *after* the derived ones and carries no owner: a no-op is the honest
     * answer there until the chain records one, and it is a great deal better than removing
     * whichever study happens to sit at that index.
     */
    fun indicatorFor(target: ChartLegendTarget): String? = when (target) {
        is ChartLegendTarget.Overlay -> derived.overlayOwners.getOrNull(target.index)
        is ChartLegendTarget.Pane -> derived.paneOwners.getOrNull(target.index)
        else -> null
    }

    /**
     * Whether the rail's «اندیکاتورها» switch is off — item 44.
     *
     * Read here rather than inside `DrawingState.isShown`, because that method filters *drawings*
     * and an indicator is not one: it has no id in `hiddenIds`, no tool, and nothing on the drawing
     * state to hide. So the layer switch wrote `DrawingLayer.INDICATORS` into `hidden` and the four
     * getters below went on drawing every line — the switch moved, the chart did not, which is
     * exactly the kind of control this wave exists to stop shipping.
     *
     * Hidden, not switched off. `activeIndicators` is left alone, so flicking the switch back
     * brings the same set of studies with the same periods rather than an empty chart the reader
     * has to rebuild.
     */
    private val indicatorsHidden: Boolean get() = DrawingLayer.INDICATORS in drawing.hidden

    /**
     * The arrows over and under the bars: the structure studies', plus the candlestick patterns.
     *
     * Patterns are appended rather than merged into [ChartDerived], because they are not indicators
     * — see [patterns] — and `CandlePatterns.markersFor` already answers with nothing when none is
     * switched on, so a chart without them allocates one empty list.
     */
    val markers: List<ChartMarker>
        get() = if (indicatorsHidden) emptyList()
        else CandlePatterns.markersFor(visibleSeries, patterns)
            .ifEmpty { return derived.markers }
            .let { derived.markers + it }

    /**
     * The strips below the price — one per switched-on oscillator.
     *
     * In catalogue order rather than in the order the reader switched them on, so the same three
     * indicators always stack the same way. A pane order that depended on tap history would move
     * under a reader who turned one off and back on.
     */
    val panes: List<ChartPane>
        /** The same identity-preserving empty case as [overlays], for the same reason. */
        get() = if (indicatorsHidden) emptyList()
        else chainPlot.panes.ifEmpty { return styledPanes }.let { styledPanes + it }

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

    /**
     * The same move in the instrument's own units.
     *
     * Beside [changePercent] rather than derived from it at the call site, and measured over the
     * same window from the same two bars, because the two figures are printed next to each other
     * and a reader who could reconstruct one from the other and get a different answer would be
     * right to distrust both. A ratio on its own is the one figure that cannot be checked — see
     * `ChartHeadline` for why the heading now leads with this one.
     *
     * Null on an empty window, and never on a zero first bar: unlike a percentage, a difference is
     * perfectly well defined when the price started at nothing.
     */
    val changeAbsolute: Double?
        get() {
            val bars = visibleSeries.bars
            val first = bars.firstOrNull()?.c ?: return null
            val last = bars.lastOrNull()?.c ?: return null
            return last - first
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
    // The marks that travel with the apparatus, chosen against the layout on screen *now* — that
    // is the set the reader can see — and re-stamped with this layout's id so applying it later
    // brings them back. Without the re-stamp a layout saved off the working chart would store
    // marks whose `layoutId` is null and show none of them, which is the shape of bug that reads
    // as "the drawings were lost".
    drawings = DrawingActions.savedWithLayout(drawing.drawings, drawing.layoutId)
        .map { it.copy(layoutId = id).toStored(drawing) },
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
    /** This venue has no feed fine enough to build this bar length. Retrying cannot help. */
    INTERVAL_UNAVAILABLE,

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
     * Where the image tool's pictures live, or null in a test and the preview.
     *
     * Separate from [drawings] and deliberately so: that store packs every drawing on a symbol into
     * one preferences string, and a photo has no business in one. A drawing carries an id; this
     * owns the bytes behind it. Null leaves the image tool drawing its frame and nothing in it,
     * which is exactly what it did before there was a store at all.
     */
    private val images: DrawingImageStore? = null,
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
    /**
     * Every bar this app has ever been given for this series, kept between sessions.
     *
     * Distinct from [cache], which holds one screenful so that a chart opens with something true
     * on it and is trimmed on every write. This is not trimmed to a screenful, it is read *before*
     * the network on a page-back, and it is deepened in the background after every load — so the
     * second walk back through a week costs nothing and the window a reader has accumulated is
     * theirs from then on. [NoOpCandleArchive] is the default and is a working answer: every read
     * is empty and the chart pages from the network exactly as it did before this existed.
     */
    private val archive: CandleArchive = NoOpCandleArchive,
    /** See [bindStores]. Supplied here where a caller builds the controller itself. */
    symbolStates: SymbolChartStateStore? = null,
    /** See [bindStores]. Supplied here where a caller builds the controller itself. */
    layoutStore: ChartLayoutStore? = null,
    /**
     * Where a series is built and its columns filled in, or null to do it on [scope]'s own thread.
     *
     * The app passes `Dispatchers.Default`, because [scope] there is the main dispatcher and a
     * twenty-thousand-bar series built on it is a dropped frame at the moment the reader tapped.
     * The tests leave it null: their scope is a test dispatcher whose clock they drive by hand,
     * and a hop to a real worker thread would put the series build outside that clock — the
     * assertion would run before the bars landed, on some machines and not others.
     */
    private val workers: CoroutineDispatcher? = null,
    /**
     * Whether to poll the venue for the live edge. Off by default; the app turns it on.
     *
     * [startLive] is an endless loop of `delay` and a fetch, which is the right shape against a
     * real clock and the wrong one against a virtual clock: a `runTest` scope advances time until
     * nothing is left to run, and a loop that always has another delay outstanding means that
     * moment never comes — the test hangs rather than fails, which is the worst way for it to go.
     *
     * So the same idiom as [workers]: the app supplies it, the tests and the previews leave it,
     * and a test that wants to prove the merge is right calls [mergeLive]'s effect through a
     * gateway it drives itself instead of waiting on a poll.
     */
    private val live: Boolean = false,
    /**
     * Where the live price comes from. [NoChartTicks] by default, which is a working answer.
     *
     * The app passes the socket the watchlist is already running — see `MarketDataController
     * .chartTicks` — and that is what makes the last candle move between one poll and the next, and
     * what a sub-minute bar is built out of. Without it the chart still reconciles its tail against
     * the candles endpoint on a cadence, so a build with no feed is the chart as it was rather than
     * a chart with a hole in it; but [ChartInterval.Seconds] has nothing else to draw and stays
     * empty, which is why the seconds keys are only offered where a feed exists.
     */
    private val ticks: ChartTickSource = NoChartTicks,
) {

    /** The venue these bars come from, named. See [CandleGateway.sourceName]. */
    val sourceName: String get() = gateway.sourceName

    private val _state = MutableStateFlow(
        ChartUiState(symbol = symbol, interval = ChartInterval.Preset(timeframe)),
    )
    val state: StateFlow<ChartUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /**
     * Everything on this chart a reader can take back, and put back.
     *
     * One per controller, and a controller is one symbol, so a stack can never offer to undo a
     * change made to a different market.
     */
    private val history = ChartHistory()

    /**
     * True while [restore] is applying a step, so the setters it calls do not record it.
     *
     * Without this an undo would push the state it was undoing onto the stack as a new change, the
     * redo stack would be cleared by the very action that exists to fill it, and a second undo
     * would put the reader back where they started. Undo would work exactly once and then flip.
     */
    private var restoring = false

    /** The background deepening of the archive. One at a time; a switch cancels the last. */
    private var fillJob: Job? = null

    /** The live-edge poll. One at a time; a switch or a replay cancels the last. See [startLive]. */
    private var liveJob: Job? = null


    /** Read once per controller. See [restoreDrawings]. */
    private var restored = false
    private var replayJob: Job? = null

    private var symbolStates: SymbolChartStateStore? = symbolStates
    private var layouts: ChartLayoutStore? = layoutStore

    /** The drawing-sync default. Bound once; see [bindStores] for why a second bind is ignored. */
    private var syncStore: DrawingSyncStore? = null

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

    /** The interval a deep link asked for, until the restore is done with it. See [openAt]. */
    private var requestedInterval: ChartInterval? = null

    /**
     * Hands the controller the two stores it persists through, where the caller could not.
     *
     * The app builds its controllers in a session-lived holder that predates both stores and does
     * not know about either; the chart routes do. Calling this before [start] is what makes the
     * restore happen; calling it after is harmless and does nothing, because the restore has run.
     * A null argument leaves whatever was already bound, so the studio re-entering the same
     * controller cannot unbind the chart's stores.
     */
    fun bindStores(
        symbolStates: SymbolChartStateStore?,
        layouts: ChartLayoutStore?,
        /**
         * Where the drawing-sync default lives.
         *
         * A third store rather than a field on the per-symbol row, because how far a mark travels
         * is a property of how the reader works and not of the instrument: somebody who keeps
         * permanent levels keeps them on everything.
         */
        drawingSync: DrawingSyncStore? = null,
    ) {
        symbolStates?.let { this.symbolStates = it }
        layouts?.let { this.layouts = it }
        drawingSync?.let { store ->
            if (syncStore == null) {
                syncStore = store
                // Collected rather than read once: the setting is global and the studio may change
                // it while the chart screen behind it is alive, and a chart that kept the old
                // default until it was reopened would be a setting that appears not to work.
                scope.launch {
                    runCatching {
                        store.mode().collect { mode ->
                            _state.update {
                                it.copy(
                                    syncMode = mode,
                                    drawing = DrawingActions.setSyncDefault(it.drawing, mode.toDrawingSync()),
                                )
                            }
                        }
                    }
                }
            }
        }
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
            // After the restore, not before: the stored row carries an interval too, and a
            // request that landed first would be silently overwritten by it. This is the one
            // ordering that makes «open this alert's chart on the bar it fired on» true.
            consumeRequestedInterval()
            loadJob = null
            reload()
        }
    }

    /**
     * The bar a deep link asked for — `coinepro://market/XAUUSD?tf=H1`.
     *
     * Recorded rather than applied, because [start] restores this symbol's stored interval
     * asynchronously and whichever of the two ran last would win. An alert fired on the four-hour
     * close and the reader left that symbol on the daily are both true, and the link is the more
     * specific of the two: it names the bar the alert was actually decided on.
     *
     * An unreadable or absent value is not an error and not a reason to change anything — the
     * stored interval is already the right answer for a link that says nothing.
     */
    fun openAt(wire: String?) {
        requestedInterval = ChartInterval.of(wire) ?: return
        if (symbolStateRestored && loadJob == null) consumeRequestedInterval()
    }

    private fun consumeRequestedInterval() {
        val wanted = requestedInterval ?: return
        requestedInterval = null
        if (wanted == _state.value.interval) return
        setInterval(wanted)
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
            // The pictures, off disk, after the drawings that name them are on the chart. Without
            // this a restart shows every image drawing as an empty frame until something else
            // happens to repaint it — and «چیزی دیگر» on a chart nobody is touching is never.
            //
            // A read that fails marks the id gone rather than leaving it waiting forever: the two
            // states look different on the canvas, and «تصویر یافت نشد» is the true one for a file
            // a reinstall took away.
            val pictures = images ?: return@launch
            for (imageId in stored.mapNotNull { DrawingImages.idIn(it.text) }.distinct()) {
                val bytes = pictures.read(imageId)
                if (bytes == null || !DrawingImages.put(imageId, bytes)) DrawingImages.markGone(imageId)
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
                indicatorColours = saved.indicatorColours.filterKeys { id -> ChartCatalog.INDICATORS.any { it.id == id } },
                indicatorWidths = saved.indicatorWidths.filterKeys { id -> ChartCatalog.INDICATORS.any { it.id == id } },
                scaleMode = mode ?: current.scaleMode,
                // Sparse on the way out and sparse on the way back: an indicator whose source no
                // longer decodes reads the candles, which is what every indicator does until
                // somebody redirects it. Dropping the entry is therefore the correct repair and
                // not a loss.
                chainSources = saved.chainSources
                    .mapNotNull { (id, encoded) -> decodeChainSource(encoded)?.let { id to it } }
                    .toMap(),
                patterns = saved.patterns.filter { id -> CandlePatterns.OPTIONS.any { it.id == id } }.toSet(),
                drawing = current.drawing.copy(
                    // A row written before the magnet was stored has a null here, and null is not
                    // `OFF`: it means nothing was said, so the chart keeps whatever it has. An
                    // unrecognised name is the same situation from a newer build.
                    magnetMode = saved.magnetMode
                        ?.let { name -> MagnetMode.entries.firstOrNull { it.name == name } }
                        ?: current.drawing.magnetMode,
                    keepDrawing = saved.keepDrawing,
                    favourites = saved.toolFavourites
                        .filter { id -> DrawingTools.ALL.any { it.id == id } }
                        .toSet(),
                ),
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
        // Trailing-edge debounce. This is called from `onDrawing`, which arrives at the rate of a
        // drag — sixty states a second while a handle is held — and each call used to launch a
        // DataStore write of the whole layer. Sixty serialised writes a second to one preferences
        // file is where the seconds of lag under a drawing tool came from: the file lock queued
        // behind itself and the main thread waited for `edit`. Only the last snapshot inside the
        // window is written; a snapshot already on its way to disk is never cancelled, because a
        // cancelled `edit` is a lost edit.
        pendingDrawings?.cancel()
        pendingDrawings = scope.launch {
            delay(DRAWING_PERSIST_DEBOUNCE_MS)
            withContext(NonCancellable) {
                runCatching { store.save(symbol, snapshot.drawings.map { it.toStored(snapshot) }) }
            }
        }
    }

    /** The write [persistDrawings] is holding back, so the next call can supersede it. */
    private var pendingDrawings: Job? = null

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
            indicatorColours = current.indicatorColours,
            indicatorWidths = current.indicatorWidths,
            scaleMode = current.scaleMode.name,
            logScale = current.logScale,
            updatedAt = System.currentTimeMillis(),
            magnetMode = current.drawing.magnetMode.name,
            keepDrawing = current.drawing.keepDrawing,
            toolFavourites = current.drawing.favourites.toList(),
            patterns = current.patterns.toList(),
            chainSources = current.chainSources.mapValues { (_, source) -> encodeChainSource(source) },
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
        record()
        // Cleared before the guard, not inside the update below. A reader on the daily bar who
        // taps «D1» in the strip has chosen a length rather than a range, and the range row must
        // stop claiming «۶ ماه» even though nothing about the chart is going to reload.
        // [setRange] records its own range after calling this, so it is unaffected.
        _state.update { it.copy(range = null) }
        if (interval == _state.value.interval) return
        _state.update {
            it.copy(
                interval = interval,
                series = CandleSeries.EMPTY,
                hasMore = false,
                // The tag the next drawing records. Stamped on the state rather than passed to each
                // of the six calls that can commit a drawing, so the one that was forgotten cannot
                // write an unlabelled mark. See `Drawing.timeframe`.
                drawing = DrawingActions.setTimeframe(it.drawing, interval.wire),
                // A jump the reader asked for on the old series means nothing on the new one.
                focusIndex = null,
            )
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
        record()
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
        record()
        _state.update { old ->
            val next = if (id in old.activeIndicators) {
                old.activeIndicators - id
            } else {
                old.activeIndicators + id
            }
            old.copy(
                activeIndicators = next,
                // The window, spent here and only here. [setVisibleWindow] stops publishing it
                // while nothing reads it, so a reader who pans across a week and *then* switches
                // the visible-range profile on would otherwise get a profile of the bars they were
                // looking at before the pan — a "visible range" study measuring an invisible range,
                // which is the exact defect the window was added to fix. Taking the remembered
                // window at the moment the study comes on closes that, and costs a chart with no
                // window-scoped study nothing: the field is already equal.
                window = if (ChartDerived.readsWindow(next, old.chained)) lastWindow else old.window,
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
    /** One indicator's line colour, or null for the catalogue's. The settings sheet's Style tab. */
    fun setIndicatorColour(id: String, colour: Long?) {
        record()
        _state.update { old ->
            old.copy(indicatorColours = if (colour == null) old.indicatorColours - id else old.indicatorColours + (id to colour))
        }
        persistSymbolState()
    }

    /** One indicator's stroke, in dp, or null for the catalogue's. */
    fun setIndicatorWidth(id: String, widthDp: Float?) {
        record()
        _state.update { old ->
            old.copy(
                indicatorWidths = if (widthDp == null || widthDp <= 0f) {
                    old.indicatorWidths - id
                } else {
                    old.indicatorWidths + (id to widthDp)
                },
            )
        }
        persistSymbolState()
    }

    /** Draw or stop drawing one switched-on indicator. The legend's eye and the Visibility tab. */
    fun toggleIndicatorHidden(id: String) = _state.update { old ->
        old.copy(hiddenIndicators = if (id in old.hiddenIndicators) old.hiddenIndicators - id else old.hiddenIndicators + id)
    }

    fun setIndicatorPeriod(id: String, period: Int?) {
        record()
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
     * Arm a tool, or one of the rail's modes.
     *
     * `DrawingActions.arm` now owns both halves: a `ToolGroup.MODES` entry sets `DrawingState.mode`
     * and a magnet entry advances the magnet, so there is nothing left for this controller to
     * record on the side. There used to be — an `eraser` boolean lived on [ChartUiState] and was
     * handed to the canvas by hand, which meant two sources for one fact and a rail that could show
     * a trend line armed while the canvas was erasing. `DrawingState.eraser` is the single read now.
     */
    fun arm(tool: DrawingTool?) = _state.update {
        it.copy(drawing = DrawingActions.arm(it.drawing, tool))
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
            drawingWidthDp = usableWidth(widthDp),
        )
    }

    fun onDrawing(next: DrawingState) {
        // A step, but not sixty of them. This is the one call in the controller that arrives at
        // the rate of a drag, and what makes a step worth keeping is whether the *shape* of the
        // layer changed — a drawing added or deleted, a tap placed or taken back — rather than
        // whether a point moved. A move is a frame of a gesture, and [ChartHistory] collapses a
        // run of those into one step per six hundred milliseconds. A selection change is not a
        // step at all: nothing about the chart is different, and an undo that only deselected
        // something would be an undo that appeared to do nothing.
        val before = _state.value.drawing
        val shapeChanged = next.drawings.size != before.drawings.size ||
            next.pending.size != before.pending.size
        val moved = !shapeChanged && next.drawings != before.drawings
        if (shapeChanged || moved) record(coalescable = moved)
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
            // The multi-select latch, re-applied. The canvas selects with `additive = false`
            // because it has no way to know the reader is collecting things; this is where that
            // intention is put back. See `widenSelection` for the four states it refuses to touch.
            val widened = widenSelection(current.drawing, restored, current.multiSelect)
            current.copy(
                drawing = widened,
                // The latch drops with the selection. A reader who tapped empty space has finished
                // collecting, and a mode still armed after its subject is gone is a mode that
                // surprises them on the next tap.
                multiSelect = current.multiSelect && widened.selection.isNotEmpty(),
                carried = current.derived,
            )
        }
        persistDrawings()
    }

    /**
     * Arm or disarm the multi-select latch.
     *
     * Offered only from the toolbar that appears once something is selected, so it can never be on
     * with nothing to add to. See [ChartUiState.multiSelect] for why a latch rather than a gesture.
     */
    fun setMultiSelect(on: Boolean) = _state.update { it.copy(multiSelect = on) }

    /** Drop the selection without touching anything in it, and leave the latch behind. */
    fun clearSelection() = _state.update {
        it.copy(drawing = DrawingActions.clearSelection(it.drawing), multiSelect = false)
    }

    /**
     * Recolour everything selected in one go.
     *
     * The reason multi-select is worth having: eight levels placed in the default gold become eight
     * in the reader's own colour in one gesture rather than eight round trips through a sheet. A
     * locked drawing is skipped by `DrawingActions.recolourSelection` — colour is an edit — and the
     * chosen colour is adopted for the next drawing, because a reader who has just recoloured their
     * whole selection has said what they want their drawings to look like.
     */
    fun recolourSelection(colour: Long) {
        _state.update { it.copy(drawing = DrawingActions.recolourSelection(it.drawing, colour)) }
        persistDrawings()
    }

    /**
     * Store the picture the reader picked and point one drawing at it — item 35.
     *
     * Three steps and every one can fail, so none of them is assumed: the store downscales and
     * writes, the decoder turns the *written* bytes back into a bitmap — the written ones, not the
     * picked ones, so what is cached is what a restart will read — and only then does the drawing
     * get the id. A failure at any step leaves the drawing exactly as it was, a frame with no
     * picture, rather than one pointing at a file that is not there.
     *
     * The previous picture is forgotten last. Deleting it first would mean a reader who replaces a
     * photo on a chart, and whose replacement fails, loses the one they had.
     */
    fun attachImage(id: Long, bytes: ByteArray) {
        val store = images ?: return
        scope.launch {
            val before = _state.value.drawing.drawings.firstOrNull { it.id == id } ?: return@launch
            val previous = DrawingImages.idIn(before.text)
            val imageId = store.put(bytes) ?: return@launch
            val stored = store.read(imageId)
            if (stored == null || !DrawingImages.put(imageId, stored)) {
                store.forget(imageId)
                return@launch
            }
            val caption = DrawingImages.captionIn(before.text)
            onDrawing(
                DrawingActions.setText(
                    _state.value.drawing,
                    id,
                    DrawingImages.textFor(imageId, caption),
                ),
            )
            previous?.let {
                store.forget(it)
                DrawingImages.forget(it)
            }
        }
    }

    /**
     * The words' colour, the wash's colour and the dash, each across the whole selection.
     *
     * Folded over `DrawingState.selection` rather than applied to the primary, because that is what
     * every other control on the floating toolbar does and a panel where two swatch rows act on one
     * drawing and a third acts on eight would be the worse kind of surprise. The per-id setters in
     * `DrawingActions` each skip a locked drawing, so a mixed selection restyles the loose ones and
     * leaves the protected ones — the same reading [recolourSelection] takes.
     *
     * Null on either colour is a real argument: it puts that part back to following the line.
     */
    fun setSelectionTextColour(colour: Long?) = restyleEachSelected { state, id ->
        DrawingActions.setTextColour(state, id, colour)
    }

    fun setSelectionFillColour(colour: Long?) = restyleEachSelected { state, id ->
        DrawingActions.setFillColour(state, id, colour)
    }

    fun setSelectionLineStyle(style: LineStyleKind) = restyleEachSelected { state, id ->
        DrawingActions.setLineStyle(state, id, style)
    }

    /**
     * The same three, on one named drawing — what its settings sheet calls, where the subject is
     * the drawing the sheet was opened from rather than whatever is selected.
     */
    fun setDrawingTextColour(id: Long, colour: Long?) = restyleOne { DrawingActions.setTextColour(it, id, colour) }

    fun setDrawingFillColour(id: Long, colour: Long?) = restyleOne { DrawingActions.setFillColour(it, id, colour) }

    fun setDrawingLineStyle(id: Long, style: LineStyleKind) = restyleOne { DrawingActions.setLineStyle(it, id, style) }

    /** One anchor of a drawing put at a typed price or moment — the settings sheet's coordinates tab. */
    fun moveDrawingPoint(id: Long, index: Int, to: ChartPoint) = onDrawing(
        DrawingActions.movePoint(_state.value.drawing, id, index, to),
    )

    private fun restyleOne(transform: (DrawingState) -> DrawingState) {
        _state.update { current -> current.copy(drawing = transform(current.drawing)) }
        persistDrawings()
    }

    private fun restyleEachSelected(transform: (DrawingState, Long) -> DrawingState) {
        _state.update { current ->
            current.copy(drawing = current.drawing.selection.fold(current.drawing, transform))
        }
        persistDrawings()
    }

    /** The same for colour and width together — a template dropped onto the whole selection. */
    fun restyleSelection(colour: Long, widthDp: Float) {
        val width = usableWidth(widthDp)
        _state.update {
            it.copy(
                drawing = DrawingActions.restyleSelection(it.drawing, colour, width),
                drawingWidthDp = width,
            )
        }
        persistDrawings()
    }

    /**
     * Put the selection on the clipboard.
     *
     * Not persisted, and that is the right lifetime: a clipboard is a thing you are in the middle
     * of using. One that survived a restart would offer to paste a trend line the reader copied
     * last Tuesday onto a chart they have since redrawn.
     */
    fun copySelection() = _state.update { it.copy(drawing = DrawingActions.copySelection(it.drawing)) }

    /**
     * Paste the clipboard, offset so the copies are not hidden underneath their originals.
     *
     * The offset is computed here rather than passed in because only the controller holds both
     * halves of it — the bar length and the visible price span. See [pasteOffset].
     */
    fun pasteClipboard() {
        _state.update { current ->
            val (time, price) = pasteOffset(current.visibleSeries, current.interval.seconds)
            current.copy(drawing = DrawingActions.paste(current.drawing, time, price))
        }
        persistDrawings()
    }

    /** Copy one drawing and paste it in one step, leaving the clipboard as it was. */
    fun cloneDrawing(id: Long) {
        _state.update { current ->
            val (time, price) = pasteOffset(current.visibleSeries, current.interval.seconds)
            current.copy(drawing = DrawingActions.clone(current.drawing, id, time, price))
        }
        persistDrawings()
    }

    /**
     * Take everything off the chart.
     *
     * Persisted immediately, like every other change to the drawing set. The confirmation belongs
     * to the screen — this is the transform, and a transform that asked a question would be a
     * transform that cannot be called from a test.
     */
    fun clearDrawings() {
        _state.update { it.copy(drawing = DrawingActions.clear(it.drawing), hiddenDrawingIds = emptySet()) }
        persistDrawings()
    }

    // ── the rail's modes ─────────────────────────────────────────────────────────────

    /**
     * Advance the magnet one step: off, weak, strong, off.
     *
     * The whole magnet — the mode enum, the snap arithmetic, the channel bindings, the resnap after
     * a data revision — existed and was unreachable, because no call site passed the rail a way to
     * cycle it. It was `OFF` for the life of the app, which also made the OHLC channel bindings
     * inert: a binding is written only by a tap the magnet moved.
     */
    fun cycleMagnet() = _state.update { it.copy(drawing = DrawingActions.cycleMagnet(it.drawing)) }

    /** The magnet set to one mode outright, from the Drawings sheet's menu. */
    fun setMagnet(mode: MagnetMode) = _state.update { it.copy(drawing = DrawingActions.setMagnet(it.drawing, mode)) }

    /** Keep the armed tool after a drawing completes, or let it fall back to the cursor. */
    fun setKeepDrawing(keep: Boolean) = _state.update {
        it.copy(drawing = DrawingActions.setKeepDrawing(it.drawing, keep))
    }

    /**
     * Lock or unlock every drawing at once, and remember which way the switch is.
     *
     * Persisted, because the lock is written onto each drawing and a chart that came back unlocked
     * would be a chart where the reader's protection quietly expired overnight.
     */
    fun setLockAllDrawings(locked: Boolean) {
        _state.update { it.copy(drawing = DrawingActions.setLockAll(it.drawing, locked)) }
        persistDrawings()
    }

    /** Hide or show one layer of the chart. See `DrawingLayer`. */
    fun setLayerHidden(layer: DrawingLayer, hidden: Boolean) = _state.update {
        it.copy(drawing = DrawingActions.setHidden(it.drawing, layer, hidden))
    }

    /** Hide or show every layer at once — the «همه» entry beside the three. */
    fun setAllLayersHidden(hidden: Boolean) = _state.update {
        it.copy(drawing = DrawingActions.setAllHidden(it.drawing, hidden))
    }

    /**
     * Pin a tool to the rail's favourites row, or take it back out.
     *
     * Not persisted yet — see the note on `ChartScreen`'s tool sheet. The set lives as long as the
     * controller, which is as long as the reader stays in this symbol's chart, and comes back empty
     * on a cold open. That is the one half of this feature that is not finished, and it is recorded
     * rather than hidden.
     */
    fun toggleToolFavourite(toolId: String) = _state.update {
        it.copy(drawing = DrawingActions.toggleFavourite(it.drawing, toolId))
    }

    /**
     * Put the chart on a quick range — «۱ ماه», «۵ سال», «همه».
     *
     * A range is a bar length plus the memory of why it was chosen. The length change goes through
     * [setInterval] so nothing about a reload has to be repeated here, and the range is recorded
     * *after* it, because `setInterval` clears it — a reader who picks a length by hand is no
     * longer on a range, and this is the one caller for which that is not true.
     */
    fun setRange(range: ChartRange) {
        // Recorded here as well as inside [setInterval], and the duplicate costs nothing: the
        // history drops a step identical to the one on top of it. What it buys is a range change
        // that lands on the same bar length still being one step, so undoing «۶ ماه» gives back
        // the span rather than silently doing nothing.
        record()
        setInterval(range.interval)
        _state.update { it.copy(range = range) }
    }

    // ── what the reader is looking at, and what is computed on what ──────────────────

    /**
     * The bars on screen, reported by the renderer.
     *
     * The one input to [ChartDerived] that no other part of this controller can know, and the one
     * that arrives at the rate of a drag: the canvas calls this once per bar the reader moves, so
     * on a fast pan it is called tens of times a second.
     *
     * ### Why most calls now publish nothing at all
     *
     * Because on most charts nothing reads the answer. Exactly one study is scoped to the window —
     * see [ChartDerived.WINDOW_SCOPED] — and until a reader switches it on, every one of these
     * calls was allocating a [ChartUiState], pushing it through the flow, recomposing the whole
     * chart page and re-answering `overlays`, `panes`, `levels` and `markers` in order to record a
     * number with no consumer. That is the second half of «چارت وحشتناک کنده»: not the arithmetic,
     * the state churn behind it.
     *
     * The window is still *remembered* on every call — [lastWindow] — so that the moment somebody
     * does switch the profile on it is computed against where they are actually looking rather
     * than against wherever the chart happened to be the last time the window was published. See
     * [toggleIndicator], which spends it.
     *
     * ### And why the published state carries the previous answer with it
     *
     * `carried` is what lets the next state reuse this one's indicators, and until now it was
     * written in exactly one place — the drawing drag. A pan therefore arrived at a state whose
     * carry was null or stale, so the reuse this whole apparatus exists for could not happen on the
     * one gesture that needs it most. Forcing `current.derived` here is not extra work: it is the
     * computation the very next frame was going to do anyway, done once and handed forward.
     */
    fun setVisibleWindow(window: BarWindow) {
        lastWindow = window
        _state.update { current ->
            when {
                current.window == window -> current
                !ChartDerived.readsWindow(current.activeIndicators, current.chained) -> current
                else -> current.copy(window = window, carried = current.derived)
            }
        }
    }

    /**
     * The last window the renderer reported, whether or not it was worth publishing.
     *
     * A plain field rather than state, deliberately: writing it must not recompose anything, and
     * nothing reads it except the moment a window-scoped study is switched on. See
     * [setVisibleWindow].
     */
    private var lastWindow: BarWindow = BarWindow.WHOLE_SERIES

    /**
     * What one indicator is computed on: the candles, or another indicator's output.
     *
     * A null source clears the override and puts the indicator back on the close, which is what
     * every published formula assumes and what a reader who has changed their mind wants. The map
     * is sparse — see [ChartUiState.chainSources] — so clearing removes the entry rather than
     * storing a default that would then have to be kept in step with the catalogue.
     */
    fun setChainSource(indicatorId: String, source: IndicatorSource?) {
        _state.update { current ->
            val next = if (source == null || source == IndicatorSource.CANDLES) {
                current.chainSources - indicatorId
            } else {
                current.chainSources + (indicatorId to source)
            }
            if (next == current.chainSources) current else current.copy(chainSources = next, carried = null)
        }
    }

    /**
     * Mark or stop marking one candlestick pattern.
     *
     * Switching an indicator off leaves its chain source behind on purpose; switching a pattern off
     * simply removes it, because a pattern has no configuration to preserve.
     */
    fun togglePattern(id: String) = _state.update { current ->
        val next = if (id in current.patterns) current.patterns - id else current.patterns + id
        current.copy(patterns = next)
    }

    /**
     * Apply a saved indicator set — and **only** the indicators, their periods and their sources.
     *
     * That restraint is the entire difference between this and a saved layout, and it is the reason
     * both exist. A layout is the apparatus: the timeframe, the chart type, the price scale and the
     * palette. A template is the *measurements*, and a reader who keeps one called «واگرایی» wants
     * those four studies on whatever they are currently looking at — not to be moved to the four-hour
     * chart of whatever instrument they were on when they saved it. Reading a timeframe out of this
     * would make the two objects the same object with two names.
     *
     * Ids this build no longer has are skipped rather than failing the apply, the same rule
     * [putLayoutOn] follows: losing one study is better than losing the template.
     */
    fun applyIndicatorTemplate(template: IndicatorTemplate) {
        _state.update { current ->
            val known = template.indicators.filter { id -> ChartCatalog.INDICATORS.any { it.id == id } }
            current.copy(
                activeIndicators = known.toSet(),
                indicatorPeriods = template.periods.filterKeys { ChartCatalog.periodOf(it) != null },
                chainSources = template.sources
                    .filterKeys { it in known }
                    .mapNotNull { (id, encoded) -> decodeChainSource(encoded)?.let { id to it } }
                    .toMap(),
                carried = null,
            )
        }
        persistSymbolState()
    }

    /** This chart's studies, ready to be saved under [name]. See [applyIndicatorTemplate]. */
    fun indicatorTemplateOf(id: String, name: String, now: Long): IndicatorTemplate {
        val current = _state.value
        return IndicatorTemplate(
            id = id,
            name = name.trim(),
            // Catalogue order rather than set order, so a template applied tomorrow stacks its
            // panes the way the chart stacks them today.
            indicators = ChartCatalog.INDICATORS.map { it.id }.filter { it in current.activeIndicators },
            periods = current.indicatorPeriods,
            sources = current.chainSources.mapValues { (_, source) -> encodeChainSource(source) },
            createdAt = now,
        )
    }

    /**
     * How far a newly placed drawing travels between layouts.
     *
     * Written through to `DrawingState.sync`, which is what actually gets stamped onto a mark, and
     * kept on the ui state so a screen can show which of the three is in force. Existing drawings
     * are left alone: a reader changing the default has said what they want *next*, and silently
     * widening a year of marks is the one move here that cannot be taken back.
     */
    fun setSyncMode(mode: DrawingSyncMode) {
        _state.update {
            it.copy(syncMode = mode, drawing = DrawingActions.setSyncDefault(it.drawing, mode.toDrawingSync()))
        }
        syncStore?.let { store -> scope.launch { runCatching { store.setMode(mode) } } }
    }

    /**
     * Scroll the chart to one bar — backlog 105, «رفتن به تاریخ».
     *
     * An index rather than a moment, because the viewport counts in bars and the field that produces
     * this has already resolved a Jalali date against the loaded series. Null clears it, which is
     * what the canvas needs after it has honoured one: a focus that stayed set would drag the view
     * back every time anything else recomposed.
     */
    fun focusBar(index: Int?) = _state.update {
        if (it.focusIndex == index) it else it.copy(focusIndex = index)
    }

    /**
     * Drop the demonstration marks whose time is up — item 41.
     *
     * Called on a timer by the screen while `DrawingState.demonstrating` is on. `DrawingActions.expire`
     * returns the same state when nothing expired, and the guard here is what stops a tick that
     * removed nothing writing the whole drawing set back to disk once a second.
     */
    fun expireDemonstrationMarks(nowMillis: Long = System.currentTimeMillis()) {
        val before = _state.value.drawing
        val after = DrawingActions.expire(before, nowMillis)
        if (after === before) return
        _state.update { it.copy(drawing = after) }
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

    // ── taking it back, and putting it back ─────────────────────────────────────────

    /** The chart's apparatus as it stands, for the stack. See [ChartStep]. */
    private fun step(): ChartStep = _state.value.let { current ->
        ChartStep(
            interval = current.interval,
            range = current.range,
            chartType = current.chartType,
            indicators = current.activeIndicators,
            indicatorPeriods = current.indicatorPeriods,
            drawing = current.drawing,
        )
    }

    /**
     * Record where the chart is *before* a change.
     *
     * Called at the top of every action a reader can reverse, before the change is applied and
     * before any guard that might decide the change is a no-op. That order is deliberate: a check
     * that has to be right in a dozen places will eventually be wrong in one, and [ChartHistory]
     * drops a step identical to the top of its stack anyway.
     */
    private fun record(coalescable: Boolean = false) {
        if (restoring) return
        history.record(step(), coalescable)
        publishHistory()
    }

    private fun publishHistory() {
        val undo = history.canUndo
        val redo = history.canRedo
        _state.update {
            if (it.canUndo == undo && it.canRedo == redo) it else it.copy(canUndo = undo, canRedo = redo)
        }
    }

    /**
     * Put one step back on the chart.
     *
     * Routed through the ordinary setters rather than written straight into the state, because
     * several of them do more than copy a field: a bar length has to refetch, an indicator set has
     * to spend the visible window, and both have to be persisted so the chart the reader comes back
     * to tomorrow is the one they undid to rather than the one they undid.
     *
     * The order is fixed and matters. The interval goes first because [setInterval] clears the
     * range — restoring a range and then an interval would throw the range away — and the drawing
     * layer goes last because a drawing carries the timeframe it was placed on, and
     * [DrawingActions.setTimeframe] inside [setInterval] would otherwise restamp the layer we are
     * in the middle of restoring.
     */
    private fun restore(from: ChartStep?) {
        val target = from ?: return
        restoring = true
        try {
            val current = _state.value
            if (target.interval != current.interval) setInterval(target.interval)
            if (target.range != _state.value.range) {
                _state.update { it.copy(range = target.range) }
            }
            if (target.chartType != current.chartType) setChartType(target.chartType)
            if (target.indicators != current.activeIndicators ||
                target.indicatorPeriods != current.indicatorPeriods
            ) {
                _state.update {
                    it.copy(
                        activeIndicators = target.indicators,
                        indicatorPeriods = target.indicatorPeriods,
                        // The same spend [toggleIndicator] makes: a window-scoped study coming back
                        // on has to measure the bars the reader is looking at now, not the ones
                        // that were on screen when the window was last published.
                        window = if (ChartDerived.readsWindow(target.indicators, it.chained)) {
                            lastWindow
                        } else {
                            it.window
                        },
                    )
                }
                persistSymbolState()
            }
            if (target.drawing != _state.value.drawing) {
                _state.update { it.copy(drawing = target.drawing) }
                persistDrawings()
            }
        } finally {
            restoring = false
        }
        publishHistory()
    }

    /** Take back the last change to the apparatus — a bar length, a study, a drawing, a span. */
    fun undo() = restore(history.undo(step()))

    /** Put back the last thing [undo] took. Cleared the moment the reader goes a different way. */
    fun redo() = restore(history.redo(step()))

    /**
     * The drawing rail's «واگرد», which is now the chart's undo.
     *
     * It used to call `DrawingActions.undo`, a second mechanism with its own rules and no redo. The
     * two produce the same outcome on the drawing layer — a step is recorded per tap, so the first
     * undo after three taps of an XABCD takes back the tap and not somebody's trend line from ten
     * minutes ago — and having one stack means the rail's button can also take back the indicator
     * the reader switched on by accident thirty seconds earlier, which is what they were reaching
     * for anyway.
     */
    fun undoDrawing() = undo()

    fun cancelDrawing() = _state.update { it.copy(drawing = DrawingActions.cancel(it.drawing)) }

    /** Lock or unlock one drawing, and remember it. See [com.coinepro.core.chart.Drawing.locked]. */
    fun setDrawingLocked(id: Long, locked: Boolean) {
        _state.update { it.copy(drawing = DrawingActions.setLocked(it.drawing, id, locked)) }
        persistDrawings()
    }

    /**
     * How wide one regression channel's rails sit — item 8.
     *
     * Persisted like every other edit to a placed drawing. The transform clamps to
     * `DrawingActions.MIN_DEVIATIONS`..`MAX_DEVIATIONS` and refuses a locked drawing, so this is a
     * plain forward and the sheet's own dimming is the visible half of the same rule.
     */
    fun setDrawingDeviations(id: Long, deviations: Double) {
        _state.update { it.copy(drawing = DrawingActions.setDeviations(it.drawing, id, deviations)) }
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
                // The layout the next drawing belongs to, and the one `syncedInto` filters against.
                // Without it every mark carries a null layout and «فقط این چیدمان» means nothing.
                drawing = withLayoutDrawings(
                    DrawingActions.setLayout(
                        DrawingActions.setTimeframe(current.drawing, (interval ?: current.interval).wire),
                        layout.id,
                    ),
                    layout.drawings,
                ),
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
    /**
     * Put a layout's saved marks back on the chart.
     *
     * Merged by id rather than appended, because applying the same layout twice must not double
     * every line on it — and matched by id rather than by geometry, because two marks at the same
     * price drawn at different times are two marks. Anything already on the chart that the layout
     * does not carry is left alone: a `GLOBAL` level is a fact about the symbol and does not
     * belong to whatever apparatus happens to be on.
     */
    private fun withLayoutDrawings(state: DrawingState, stored: List<StoredDrawing>): DrawingState {
        if (stored.isEmpty()) return state
        val restored = stored.map { it.toDrawing() }
        val ids = restored.map { it.id }.toSet()
        return state.copy(drawings = state.drawings.filterNot { it.id in ids } + restored)
    }

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
        // **A ceiling on what one chart may hold in memory, and it is not decoration.**
        //
        // Before the archive, this method's brake was the network: a page-back cost a round trip,
        // so a reader dragging at the left edge asked a few times a second at most. Reading from
        // disk removed that brake, and the first build with the archive in it grew the series as
        // fast as frames rendered — tens of thousands of bars in a couple of seconds, every one of
        // them rebuilding six columns and recomputing every switched-on indicator. The report was
        // "the chart is very slow", and it was right.
        //
        // The renderer's own trigger has been fixed to ask once per page rather than once per
        // frame; this is the belt to that brace, because a trigger that misbehaves again must cost
        // a reader a stalled request rather than a stalled phone. [ChartHistory.MAX_RESIDENT_BARS]
        // is about forty screenfuls at ordinary zoom, so nobody reaches it by dragging on purpose.
        //
        // `hasMore` is deliberately **not** cleared: there really is more history, it is in the
        // archive, and saying otherwise would be a lie the «بیشتر» affordance is built on. What
        // stops is this chart growing; a reader who wants to see further back changes the bar
        // length, which is the right instrument for it anyway.
        if (current.series.size >= ChartHistory.MAX_RESIDENT_BARS) {
            log?.debug(
                LogTag.CHART,
                "page-back held at the resident ceiling",
                mapOf(
                    "symbol" to current.symbol,
                    "tf" to current.interval.wire,
                    "bars" to current.series.size.toString(),
                ),
            )
            return
        }
        val oldest = current.series.time.first()
        _state.update { it.copy(loadingMore = true) }
        scope.launch {
            // **Disk before the network.** The archive holds every bar this app has ever been
            // given for this series, so a reader walking back over a week they have already walked
            // is served from storage in a frame rather than from a server in a second — and it
            // works with no connection at all, which on the networks this app is used on is not a
            // small thing. Only when the archive comes back empty is the venue asked.
            // **A disk page is not a network page, and it stopped being sized like one.**
            //
            // [HISTORY_PAGE_BARS] is five hundred because that is the smallest cap of the three
            // venues this app talks to — it is a fact about a server, and it has no business
            // deciding how much of our own archive we read in one go. Reading five hundred at a
            // time from a local table meant a reader walking back through a year of hourly candles
            // paid ninety round trips through this whole method, ninety series rebuilds and ninety
            // recompositions, for bars that were already on the device. [ARCHIVE_PAGE_BARS] is what
            // a page-back costs when nothing is waiting on a network.
            //
            // Clamped to the room left under the ceiling, so the last page is a partial one and the
            // series lands exactly on [ChartHistory.MAX_RESIDENT_BARS] rather than a page past it.
            // A chart that overshoots its own bound by five thousand bars is a bound that does not
            // mean anything.
            val room = (ChartHistory.MAX_RESIDENT_BARS - current.series.size)
                .coerceIn(1, ARCHIVE_PAGE_BARS)
            val stored = runCatching {
                archive.read(current.symbol, current.interval, room, before = oldest)
            }.getOrDefault(emptyList())
            if (stored.isNotEmpty()) {
                prependOlder(current, stored, hasMore = true)
                realignComparisons()
                return@launch
            }
            // A seconds chart's history is the archive and only the archive. Asking the venue for
            // bars "before" a ten-second one gets minutes back, and prepending those would put a
            // different series in front of this one. The archive came back empty, so there is
            // genuinely no more.
            if (current.interval is ChartInterval.Seconds) {
                _state.update { it.copy(loadingMore = false, hasMore = false) }
                return@launch
            }
            val result = runCatching {
                gateway.load(current.symbol, current.interval, before = oldest)
            }
            result.onSuccess { page ->
                // Only bars strictly older than what is held. The server promises no overlap;
                // trusting that promise and being wrong would double a bar, and a doubled bar is a
                // spike on the chart that never happened.
                val older = page.candles.filter { it.t < oldest }
                prependOlder(current, older, hasMore = page.hasMore && older.isNotEmpty())
                // The base grew at its left edge, so every overlay is now a page short of it.
                realignComparisons()
                // History is cached too. Paging back is the second place a reader waits, and a
                // reader who pans back over the same week twice should only pay for it once.
                runCatching { cache.write(current.symbol, current.interval, page.candles) }
                // And kept. The cache is trimmed to a screenful on every write, so without this
                // the walk above is paid for again tomorrow — which is exactly what used to happen.
                runCatching { archive.write(current.symbol, current.interval, page.candles) }
                publishDepth(current.symbol, current.interval)
            }.onFailure {
                // A failed page-back leaves the chart alone. There is nothing to say that would be
                // more useful than the bars already on screen.
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    /**
     * Put a page of older bars in front of what is held.
     *
     * ### Why this does not slice the series back down
     *
     * `CandleSeries.resident` exists to bound a chart at [ChartHistory.MAX_RESIDENT_BARS], and this
     * is the one path that can walk past it — twenty-four page-backs at [HISTORY_PAGE_BARS] each.
     * It is deliberately not applied here. Slicing after a prepend can only drop bars from the
     * newest end, since the oldest are the ones the reader just asked for, and there is no way to
     * put those back later without replacing the series under a reader whose viewport is an index
     * into it. That is a chart that jumps while somebody is reading it, to save a few megabytes on
     * the single series they are actively dragging through. The bound that matters is the one on
     * the reload path, which is where eight controllers each paint a fresh series; this one costs
     * memory only for as long as one reader keeps pulling, and `CandleArchive.MAX_BARS_PER_SERIES`
     * is its ceiling.
     */
    private suspend fun prependOlder(request: ChartUiState, older: List<OhlcBar>, hasMore: Boolean) {
        // The reader may have switched symbol or interval while the page was in flight, and
        // prepending this page onto that series would draw one instrument's history under
        // another's label.
        val base = _state.value
        if (base.symbol != request.symbol || base.interval != request.interval) {
            _state.update { it.copy(loadingMore = false) }
            return
        }
        // Off the main thread, like the reload path: this concatenates and re-checks every bar
        // held, which at the resident ceiling is fifty thousand of them, in the frame after the
        // reader dragged to the left edge. The series it joins onto is read once here; if a live
        // tick or a switch replaces it while the join runs, the join is thrown away rather than
        // written over the newer series.
        val joined = buildSeries { CandleSeries(older.map(OhlcBar::toCandle) + base.series.bars) }
        _state.update { old ->
            when {
                old.symbol != request.symbol || old.interval != request.interval -> old.copy(loadingMore = false)
                old.series !== base.series -> old.copy(loadingMore = false)
                else -> old.copy(series = joined, loadingMore = false, hasMore = hasMore)
            }
        }
    }

    /**
     * Build a series on a worker thread with its columns already filled in.
     *
     * The controller's scope is the main dispatcher — it has to be, the state is read by
     * composition — so any series built inline is built in a frame. This is the one place series
     * are built for the chart, and everything that reaches the state flow comes through it warm.
     */
    private suspend fun buildSeries(build: () -> CandleSeries): CandleSeries {
        val worker = workers ?: return build().warm()
        return withContext(worker) { build().warm() }
    }

    /**
     * Read how deep the archive now is for this series and publish it.
     *
     * A fact, printed as one. See [ChartUiState.archivedBars].
     */
    private suspend fun publishDepth(symbol: String, interval: ChartInterval) {
        val span = runCatching { archive.span(symbol, interval) }.getOrNull() ?: return
        _state.update { old ->
            if (old.symbol != symbol || old.interval != interval) old else old.copy(archivedBars = span.count)
        }
    }

    private fun reload() {
        loadJob?.cancel()
        // **The fill goes too, and this is most of «تایم‌فریم یک دقیقه گیر کرد».**
        //
        // Only the load used to be cancelled here. The previous interval's archive fill — up to
        // forty page requests and their Room writes — kept running, on the same connection pool
        // and the same server as the load for the interval the reader had just tapped. On the
        // minute chart, where every page is five hundred bars of a few hours, the new load queued
        // behind the old fill and the spinner stayed. `deepenArchive` for the new interval will
        // start its own fill once the new bars are on screen; nothing of the old one is wanted.
        fillJob?.cancel()
        fillJob = null
        _state.update { it.copy(loading = true, error = null) }
        loadJob = scope.launch {
            val current = _state.value
            // **A seconds chart has no server to ask.**
            //
            // Neither venue serves a bar shorter than a minute, so there is no request to send and
            // a page of minutes would be the wrong series under the right label. What it has is the
            // archive — every seconds bar this app has built before — and the price feed, which
            // starts adding to it on the next tick. So the load *is* the archive read, and the
            // chart is not left saying «در حال بارگیری» over a series that will never arrive.
            if (current.interval is ChartInterval.Seconds) {
                paintFromArchive(current.symbol, current.interval)
                _state.update {
                    if (it.symbol != current.symbol || it.interval != current.interval) {
                        it
                    } else {
                        it.copy(loading = false, error = null, hasMore = !it.series.isEmpty, venueExhausted = true)
                    }
                }
                publishDepth(current.symbol, current.interval)
                startLive(current.symbol, current.interval)
                if (loadJob === coroutineContext[Job]) loadJob = null
                return@launch
            }
            paintFromCache(current.symbol, current.interval)
            // Wall clock rather than `AppLog.timed`, because what is being measured is not the
            // gateway call: it is the interval a reader spends looking at an empty chart, which
            // ends when the state carrying the bars is published, not when the response lands.
            val startedAt = System.nanoTime()
            // `runCatching` catches `CancellationException` too, and every interval switch cancels
            // the load in flight. Without this the cancelled job writes its own failure over a
            // series the switch has just emptied, so the chart says «چارت بارگیری نشد» for the
            // timeframe the reader has just picked — while that timeframe is still loading. On a
            // slow link that is a failure on every tap and none on the one that loaded at open,
            // which is exactly how it was reported.
            val outcome = runCatching { gateway.load(current.symbol, current.interval) }
            if (outcome.exceptionOrNull() is CancellationException) return@launch
            outcome
                .onSuccess { page ->
                    // Built and warmed off the main thread. This scope is the main dispatcher,
                    // and a series is a map over every bar, an ordering check over every bar and,
                    // on first use, six columns over every bar — a few thousand on the hourly
                    // chart, twenty thousand on the minute one. Done here it was the frame the
                    // reader's tap landed in.
                    val series = buildSeries { CandleSeries(page.candles.map(OhlcBar::toCandle)).resident() }
                    _state.update {
                        it.copy(
                            // Bounded here and only here. This is the path eight live controllers
                            // take when a reader flips between symbols, and it is the one that can
                            // be handed a deep series without anybody asking for one. Below the
                            // ceiling `resident` returns the same object, so an ordinary chart
                            // pays one comparison and allocates nothing.
                            series = series,
                            loading = false,
                            error = null,
                            hasMore = page.hasMore,
                            venueExhausted = false,
                        )
                    }
                    realignComparisons()
                    // Written after the state, not before: the reader's chart is the thing that
                    // matters and a slow disk must never sit between them and their candles.
                    runCatching { cache.write(current.symbol, current.interval, page.candles) }
                    runCatching { archive.write(current.symbol, current.interval, page.candles) }
                    deepenArchive(current.symbol, current.interval)
                    // The archive may already hold a season of this series from an earlier
                    // sitting; the chart is not made to wait for a drag to show it.
                    deepenResident(current.symbol, current.interval)
                    // And the newest bar is kept newest. See [startLive].
                    startLive(current.symbol, current.interval)
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
            // Only if this job is still the current one. A cancelled job that nulls the handle
            // leaves the job that replaced it untracked, so the next switch cancels nothing and
            // two loads race — and the older one can land last, drawing the previous interval's
            // bars under the new interval's label.
            if (loadJob === coroutineContext[Job]) loadJob = null
        }
    }

    /**
     * Deepen the archive for this series in the background, and say how deep it got.
     *
     * ### Why this runs at all, and why it is not a download
     *
     * The owner asked for twenty to fifty thousand candles. Fifty thousand is the archive's
     * ceiling and it is **capacity**: at five hundred bars a page it is a hundred round trips, and
     * no venue this app talks to holds that much for most series anyway — the crypto route's whole
     * retention is on the order of two thousand bars. So this does not fetch fifty thousand
     * candles; it walks a session's worth backwards after each load and keeps what it finds, and
     * the depth accumulates across openings of the app. `HistoryDepth.bars` is what the reader is
     * told, because it is a fact; the constant never is.
     *
     * It is fire-and-forget on purpose. Nothing on screen waits for it, a failure is not shown,
     * and a switch of symbol or interval cancels it — the reader's next chart is worth more than
     * finishing a fill for the last one.
     */
    private fun deepenArchive(symbol: String, interval: ChartInterval) {
        if (archive === NoOpCandleArchive) return
        // Nothing to walk back through: no venue serves a bar this short, so the only bars this
        // series will ever have are the ones the price feed builds. See [ChartInterval.Seconds].
        if (interval is ChartInterval.Seconds) return
        fillJob?.cancel()
        fillJob = scope.launch {
            // **Let the reader have the chart first.**
            //
            // The fill used to start the instant the first page landed, so the reader's first
            // drag, first zoom and first indicator competed with a queue of page requests on the
            // same pool and the same radio — and on the minute chart lost to it. A few seconds of
            // quiet is what a chart needs to feel settled, and a fill that starts after them costs
            // the reader nothing they would notice. A reader who taps another interval inside
            // that window cancels this before it has sent a byte.
            delay(FILL_SETTLE_MS)
            // Paced, and shallower where the bars are cheapest. The budget is sized for hourly
            // candles, where forty pages is five years and worth having tonight; on M1 and M5 it
            // is a few days that the reader will page through by hand if they want them, and
            // twenty thousand Room rows a session that nobody asked for.
            val fine = interval.seconds < Timeframe.M15.seconds
            val depth = runCatching {
                gateway.fillHistory(
                    symbol,
                    interval,
                    archive,
                    maxPages = if (fine) FINE_FILL_PAGES else HISTORY_PAGE_BUDGET,
                    pageDelayMillis = FILL_PAGE_PAUSE_MS,
                )
            }.getOrNull() ?: return@launch
            _state.update { old ->
                if (old.symbol != symbol || old.interval != interval) {
                    old
                } else {
                    // A fill that walked to the end of the venue has established something a
                    // short page never can — but "the venue has no more" is not "there is no
                    // more". The fill's own writes usually sit *behind* what the chart is
                    // drawing, and those bars are pages the reader can still walk back through.
                    // So «بیشتر» is withdrawn only when the archive has nothing older either,
                    // which is the one state where another page-back genuinely cannot produce a
                    // bar. Getting this backwards is what would make a chart with thirty
                    // thousand bars on disk refuse to show the reader the second one.
                    val archivedOldest = depth.oldest
                    val archiveHasOlder = archivedOldest != null &&
                        !old.series.isEmpty &&
                        archivedOldest < old.series.time.first()
                    old.copy(
                        archivedBars = depth.bars,
                        venueExhausted = depth.venueExhausted,
                        // **Older on disk always means more, whatever ended the fill.**
                        //
                        // This used to read `if (depth.venueExhausted) archiveHasOlder else
                        // old.hasMore`, which handled one of the four ways a fill ends and let the
                        // venue's own `hasMore` decide the other three. `HistoryStop.CEILING` is
                        // the one that bites: a fill stops there when the archive is *full*, which
                        // is the state with the most history behind it and precisely when
                        // `venueExhausted` is false. So a chart whose venue serves one page, over
                        // an archive holding fifty thousand bars, kept the venue's `hasMore = false`
                        // and refused to walk back through any of them.
                        //
                        // Invisible until the ceiling was raised, because before that the archive
                        // never reached it. The paragraph above this already names the failure —
                        // "a chart with thirty thousand bars on disk refuse to show the reader the
                        // second one" — and guarded only half of it.
                        //
                        // The honest rule needs no case analysis: there is more to show if there
                        // are older bars anywhere, so the archive's own answer wins whenever it has
                        // one and the venue's is kept only when it does not.
                        hasMore = archiveHasOlder || old.hasMore,
                    )
                }
            }
            // What the fill just wrote belongs on the chart, not only on the disk.
            deepenResident(symbol, interval)
            log?.debug(
                LogTag.CHART,
                "archive filled",
                mapOf(
                    "symbol" to symbol,
                    "tf" to interval.wire,
                    "bars" to depth.bars.toString(),
                    "added" to depth.added.toString(),
                    "pages" to depth.pages.toString(),
                    "stop" to depth.stop.name,
                ),
            )
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
    /**
     * Keep the newest bar the newest, and keep its close the market's — item four of the owner's
     * list: «قیمت کندل‌ها لحظه‌ای نیست و شمارش معکوس ندارد».
     *
     * ### What was wrong
     *
     * Nothing on this controller ever asked the venue a second question. A chart was fetched once,
     * on open or on a timeframe tap, and then sat there: the last candle's close was however the
     * market stood when the request went out, the legend's price was that same close, and — the
     * part that made it obvious — the live tag's countdown ran out and then printed nothing at all,
     * because a countdown is `open + length − now` and a bar minutes old has a negative one. On the
     * minute chart, which is where the owner noticed it, the tag lost its second line inside sixty
     * seconds of opening the screen.
     *
     * ### What this does
     *
     * Re-reads the last few bars on a cadence and merges them over the tail: the open bar's close,
     * high, low and volume are replaced in place, and the bar that follows it is appended when the
     * venue opens one. Everything downstream is already derived from the series — `lastPrice`, the
     * legend, the change, the studies, the live tag and its countdown — so a merged tail is a live
     * chart with no second source of truth to disagree with it.
     *
     * ### The cadence, and why it is not one number
     *
     * A twelfth of the bar's own length, between three and thirty seconds: five seconds on M1,
     * thirty on anything from H1 up. A flat five-second poll would ask the venue seven hundred
     * times an hour for a daily candle that changes once a day; a flat minute would leave the
     * minute chart a minute stale, which is the whole complaint.
     *
     * ### What it never does
     *
     * Run during a replay, over an empty chart, or while a full load is in flight — a rehearsal is
     * a picture of the past and a live bar merged into it would be a bar from the future. And it
     * never prepends: history comes from [loadMore] and the archive, and this touches the tail
     * only, so a reader panned back keeps exactly the window they panned to.
     */
    private fun startLive(symbol: String, interval: ChartInterval) {
        liveJob?.cancel()
        if (!live) return
        liveJob = scope.launch {
            // The price feed, folded bar by bar. This is the one that makes a candle *move*: it
            // reports as often as the market trades, so between two reconciliations the forming bar
            // grows continuously instead of standing still. It is also the whole of a seconds
            // chart, which has no server behind it at all.
            launch {
                ticks.ticks(symbol)
                    // `takeWhile` rather than a cancel from inside the collector: [foldTick]
                    // answers false the moment this series stops being the one on screen, and that
                    // answer ends the collection cleanly instead of by throwing.
                    .takeWhile { tick -> foldTick(symbol, interval, tick) }
                    .collect { }
            }
            // And the venue's own answer, on a slower clock, because a tick carries a price and a
            // candle carries a volume, a high and a low the phone may have missed while it was
            // asleep or off the feed. The socket is the smoothness; this is the truth.
            //
            // Skipped entirely on a seconds chart — there is no request that fetches a ten-second
            // bar, and asking for one would come back as minutes and overwrite everything the
            // ticks have built. See [ChartInterval.Seconds].
            if (interval !is ChartInterval.Seconds) {
                val period = livePollMillis(interval)
                while (true) {
                    delay(period)
                    if (!refreshLiveEdge(symbol, interval)) return@launch
                }
            }
        }
    }

    /**
     * Put one price into the bar it belongs to. Returns false once this series has left the screen.
     *
     * ### Three cases, and the third is the one that is easy to get wrong
     *
     *  * The tick belongs to the **bar already at the live edge**: its close moves, and its high and
     *    low widen if the price went past them. The open never moves — it is history the moment the
     *    bar opens.
     *  * The tick belongs to a **later bar**: the previous one is finished and a new one opens at
     *    this price, `o = h = l = c`, with no volume, because a tick reports no size. A null volume
     *    rather than a zero: `Candle.v` is nullable exactly so that "not reported" and "nothing
     *    traded" are different things, and a zero here would draw an empty column in the volume pane
     *    on every bar of a seconds chart.
     *  * The tick belongs to an **earlier** bar — a late quote, a snapshot refresh after a
     *    reconnect, a clock a second behind the server's — and is dropped. Writing it would rewind
     *    the live edge, which on screen is a candle that jumps backwards.
     *
     * ### It writes to the archive on a new bar and not on every tick
     *
     * A seconds chart's history is whatever it has built, so it has to be kept or the next visit
     * opens on nothing. But a write per tick is a disk write several times a second for a row that
     * is about to change again. The bar is persisted when it is **finished** — which is the moment
     * the next one opens — so each bar is written exactly once, with its final values.
     */
    internal suspend fun foldTick(symbol: String, interval: ChartInterval, tick: PriceTick): Boolean {
        val base = _state.value
        if (base.symbol != symbol || base.interval != interval) return false
        // A rehearsal is a picture of the past; a live price merged into it is a bar from the
        // future. A load in flight is about to replace the series wholesale.
        if (base.replay.isOn || base.loading) return true
        if (!tick.price.isFinite() || tick.price <= 0.0) return true

        val length = interval.seconds
        if (length <= 0L) return true
        val held = base.series.bars
        val last = held.lastOrNull()
        // An empty seconds chart starts here: the first tick is the first bar. Every other kind of
        // chart has been loaded before a tick can reach it, so this is not a general "seed from a
        // price" path — it is the one series that legitimately begins with nothing.
        if (last == null) {
            if (interval !is ChartInterval.Seconds) return true
            val first = interval.bucketStart(tick.epochSeconds)
            return publishTail(base, held + Candle(first, tick.price, tick.price, tick.price, tick.price))
        }
        if (tick.epochSeconds < last.t) return true

        // **The grid is the venue's, not the epoch's.**
        //
        // `bucketStart` lays a bar length on the epoch, which is what both servers do and what the
        // axis is drawn from — but it is not what this arithmetic may assume. A venue that ships an
        // hourly series opening at :10, a fold from a finer bar, a custom interval counted from the
        // reader's midnight: any of those puts the held bars on a grid the epoch's does not line up
        // with, and a tick bucketed on the epoch would land *before* the bar it belongs to and be
        // dropped as stale. Every price would be, forever, silently.
        //
        // Counting from the last held bar cannot be wrong about that: zero steps means the tick is
        // inside the bar that is open, and n steps means n bars have opened since. It is also one
        // expression for both cases rather than two that have to agree.
        val opensAt = last.t + ((tick.epochSeconds - last.t) / length) * length

        if (opensAt == last.t) {
            val moved = last.copy(
                h = maxOf(last.h, tick.price),
                l = minOf(last.l, tick.price),
                c = tick.price,
            )
            // Nothing to publish when the price has not left the bar's body or its extremes — a
            // market printing the same price twice must not cost a series rebuild.
            if (moved == last) return true
            return publishTail(base, held.dropLast(1) + moved)
        }

        // A new bar. The one it replaces is final now, so that is when it is kept.
        runCatching { archive.write(symbol, interval, listOf(last.toBar())) }
        // A ring, not a list that grows for as long as the screen is open. A ten-second chart left
        // on overnight would otherwise hold nine thousand bars by morning and rebuild every one
        // of them on each tick; the oldest fall off the front once the archive has them, and a
        // page back reads them from there. See [SECONDS_BARS_HELD].
        val kept = if (interval is ChartInterval.Seconds && held.size >= SECONDS_BARS_HELD) {
            held.subList(held.size - SECONDS_BARS_HELD + 1, held.size)
        } else {
            held
        }
        return publishTail(base, kept + Candle(opensAt, tick.price, tick.price, tick.price, tick.price))
    }

    /**
     * Publish a series whose tail has moved, unless something replaced it while this was built.
     *
     * The build is on the worker dispatcher like every other series build in this file — six
     * columns over every bar held — and the identity check afterwards is what makes that safe: a
     * switch of symbol, a page-back or a reload landing during the build wins, and this join is
     * dropped rather than written over the newer series.
     */
    private suspend fun publishTail(base: ChartUiState, bars: List<Candle>): Boolean {
        val series = buildSeries { CandleSeries(bars) }
        _state.update { old ->
            when {
                old.symbol != base.symbol || old.interval != base.interval -> old
                old.series !== base.series -> old
                else -> old.copy(series = series, loading = false)
            }
        }
        return true
    }

    /**
     * One live read, merged. Returns false when this series is no longer the one on screen.
     *
     * Split out of [startLive]'s loop so the behaviour is reachable without the loop: a test can
     * ask for a single poll and assert what it did, where driving the loop itself would mean a
     * virtual clock that never runs out of work. The loop is then only a clock, and this is the
     * whole of what a tick means.
     */
    internal suspend fun refreshLiveEdge(symbol: String, interval: ChartInterval): Boolean {
        val current = _state.value
        if (current.symbol != symbol || current.interval != interval) return false
        if (current.replay.isOn || current.loading || current.series.isEmpty) return true
        val fresh = runCatching {
            gateway.load(symbol, interval, limit = LIVE_TAIL_BARS)
        }.getOrNull()?.candles.orEmpty()
        if (fresh.isEmpty()) return true
        mergeLive(symbol, interval, fresh)
        return true
    }

    /**
     * Join a freshly read tail onto the series, replacing the bars it covers.
     *
     * The join is by **open time**, never by position: a venue that has opened a new bar since the
     * last poll returns a tail one bar further along, and matching on position would overwrite the
     * wrong candle. Held bars from the tail's first open time onward are dropped and the tail put
     * in their place, which is the same operation whether nothing changed, the open bar moved, or
     * three bars opened while the phone was asleep.
     *
     * Silently does nothing when the tail is older than everything held — a stale cached response,
     * or a venue that answered a page-back — because writing it would rewind the chart.
     */
    private suspend fun mergeLive(symbol: String, interval: ChartInterval, fresh: List<OhlcBar>) {
        val base = _state.value
        if (base.symbol != symbol || base.interval != interval || base.series.isEmpty) return
        val from = fresh.first().t
        val held = base.series.bars
        val keep = held.takeWhile { it.t < from }
        // A tail that begins before the whole held window is a page-back, not a live read.
        if (keep.isEmpty() && held.first().t < from) return
        val merged = keep + fresh.map(OhlcBar::toCandle)
        // Nothing moved: the same count and the same newest bar. Compared before the build, because
        // an unchanged poll is the common case and rebuilding five thousand bars is not free.
        if (merged.size == held.size && merged.lastOrNull() == held.lastOrNull()) return
        val series = buildSeries { CandleSeries(merged) }
        _state.update { old ->
            when {
                old.symbol != symbol || old.interval != interval -> old
                // The series was replaced while the join ran — a switch, a page-back, a replay.
                old.series !== base.series -> old
                else -> old.copy(series = series)
            }
        }
        runCatching { cache.write(symbol, interval, fresh) }
        runCatching { archive.write(symbol, interval, fresh) }
    }

    /**
     * Put the history the device already holds **on the chart**, without waiting for a drag.
     *
     * ### The complaint
     *
     * «داخل آپ زده ۳۰۰ کندل ولی در واقع باید ۵ الی ۵۰ هزار کندل باشه». It was true, and it was not
     * a limit: [ChartHistory.MAX_RESIDENT_BARS] has been fifty thousand and the archive holds as
     * many, but the *resident* series was whatever one network page returned — three hundred bars —
     * and every one of the other forty-nine thousand seven hundred waited for the reader to drag to
     * the left edge and ask for them by hand, one page at a time.
     *
     * ### What this does
     *
     * Reads the archive backwards in [ARCHIVE_PAGE_BARS] pages until the chart holds
     * [RESIDENT_TARGET_BARS] or the archive runs out, prepending each page through the same
     * [prependOlder] a page-back uses. It runs after the first paint — where an earlier sitting
     * left history — and again after each archive fill, so a symbol opened for the first time
     * deepens as the fill lands rather than staying at one page for the session.
     *
     * Five thousand and not fifty: fifty thousand is the *ceiling* a reader may drag to, and making
     * it the opening state would put every switched-on indicator over fifty thousand bars on a
     * screen showing a hundred and twenty. Five thousand is about seven months of hourly candles —
     * enough that «برو به تاریخ», a backtest and a season of dragging all land inside what is
     * already loaded — and it is the bottom of the range the owner asked for.
     */
    private suspend fun deepenResident(symbol: String, interval: ChartInterval) {
        if (archive === NoOpCandleArchive) return
        repeat(RESIDENT_FILL_PAGES) {
            val current = _state.value
            if (current.symbol != symbol || current.interval != interval) return
            if (current.series.isEmpty || current.series.size >= RESIDENT_TARGET_BARS) return
            val room = (RESIDENT_TARGET_BARS - current.series.size).coerceIn(1, ARCHIVE_PAGE_BARS)
            val stored = runCatching {
                archive.read(symbol, interval, room, before = current.series.time.first())
            }.getOrDefault(emptyList())
            if (stored.isEmpty()) return
            prependOlder(current, stored, hasMore = true)
        }
    }

    /**
     * Draw whatever the archive holds for this series, and nothing else.
     *
     * The seconds chart's whole load path. [paintFromCache] is the wrong shape for it twice over:
     * it reads the screenful cache first, which for a series no gateway ever wrote is always empty,
     * and it refuses to draw over a chart that already has bars, which is exactly the state a
     * seconds chart is in a second after it opened. This paints unconditionally, because on this
     * path there is no network answer coming behind it that could disagree.
     */
    private suspend fun paintFromArchive(symbol: String, interval: ChartInterval) {
        val stored = runCatching {
            archive.read(symbol, interval, RESIDENT_TARGET_BARS)
        }.getOrDefault(emptyList())
        if (stored.isEmpty()) return
        val series = buildSeries { CandleSeries(stored.map(OhlcBar::toCandle)).resident() }
        _state.update { latest ->
            if (latest.symbol != symbol || latest.interval != interval) latest else latest.copy(series = series)
        }
    }

    private suspend fun paintFromCache(symbol: String, interval: ChartInterval) {
        val cached = runCatching { cache.read(symbol, interval) }.getOrDefault(emptyList())
            // The archive is a superset of the cache and outlives it: the cache is trimmed to a
            // screenful and is cleared with the app's storage, while the archive is what this
            // series has accumulated. Falling back to it means a chart opened after a clear-cache,
            // or one whose cache write lost a race, still comes up on real candles.
            .ifEmpty { runCatching { archive.read(symbol, interval) }.getOrDefault(emptyList()) }
        if (cached.isEmpty()) return
        // Only if the chart is still empty — checked before the build, so a cache that lost the
        // race to the network does not cost a worker thread a build that is then thrown away.
        val current = _state.value
        if (current.symbol != symbol || current.interval != interval || !current.series.isEmpty) return
        val series = buildSeries { CandleSeries(cached.map(OhlcBar::toCandle)) }
        _state.update { latest ->
            if (latest.symbol != symbol || latest.interval != interval || !latest.series.isEmpty) {
                latest
            } else {
                // `loading` stays true: the fetch is still out, the spinner still belongs, and the
                // reader now has something to look at while it runs. Those are not in conflict.
                latest.copy(series = series)
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

        /**
         * How many bars one page-back takes off the **disk**: five thousand.
         *
         * Deliberately an order of magnitude above [HISTORY_PAGE_BARS], and the difference is the
         * whole point — that constant is a venue's page cap, this one is a judgement about a local
         * read. The archive holds up to `CandleArchive.MAX_BARS_PER_SERIES` bars and the owner
         * wants a chart he can back-test on, which means walking to the far end of it: at five
         * hundred a page that is a hundred passes through `loadMore`, each one rebuilding the
         * series and every switched-on indicator over it. At five thousand it is ten.
         *
         * Not larger, because a page-back is still a thing that happens while a finger is on the
         * glass. Five thousand hourly bars is about seven months and roughly sixty screenfuls at
         * ordinary zoom, so it is far more than one drag can consume — the reader reaches the left
         * edge, one page arrives, and they can go on dragging without meeting another wait.
         */
        const val ARCHIVE_PAGE_BARS = 5_000

        /**
         * How long a freshly loaded chart is left alone before its archive fill starts.
         *
         * Four seconds: long enough for the first drag and the first zoom to happen on a quiet
         * connection, short enough that a reader who stays on the chart still gets the fill in
         * the same sitting. See [deepenArchive].
         */
        const val FILL_SETTLE_MS = 4_000L

        /** The breath between two fill pages. See `fillHistory`'s `pageDelayMillis`. */
        const val FILL_PAGE_PAUSE_MS = 400L

        /**
         * Pages one fill may fetch on an interval finer than M15: eight, or four thousand bars.
         *
         * On M1 that is under three days and on M5 two weeks — a reader who wants more pages
         * back by hand and pays for exactly what they look at. The full [HISTORY_PAGE_BUDGET]
         * on M1 was twenty thousand rows into Room and forty requests in a row, every time the
         * timeframe was tapped, for history nobody back-tests on.
         */
        const val FINE_FILL_PAGES = 8

        /**
         * How many bars the chart holds without being dragged: five thousand.
         *
         * See [deepenResident] for why this is not `ChartHistory.MAX_RESIDENT_BARS`, which is the
         * ceiling a reader may drag to rather than the window a chart opens in.
         */
        const val RESIDENT_TARGET_BARS = 5_000

        /** At [ARCHIVE_PAGE_BARS] a page one is enough for the target; two is the belt to it. */
        const val RESIDENT_FILL_PAGES = 2

        /** Bars one live poll re-reads: the open one, and two behind it in case a bar closed. */
        const val LIVE_TAIL_BARS = 3

        /**
         * The most seconds bars a live series holds in memory before the oldest fall off the front.
         *
         * Two thousand ten-second bars is five and a half hours on screen, which is more than any
         * phone shows at once and less than a night's worth of ticks. Nothing is lost: each bar is
         * written to the archive the moment it closes, and paging back reads it from there.
         */
        const val SECONDS_BARS_HELD = 2_000

        /**
         * How often the live edge is re-read, from the bar's own length.
         *
         * A twelfth of the bar, between three and thirty seconds — five on M1, thirty from H1 up.
         * See [startLive] for why a single number is wrong in both directions.
         */
        fun livePollMillis(interval: ChartInterval): Long =
            (interval.seconds * 1_000L / 12L).coerceIn(3_000L, 30_000L)
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
    /**
     * Which indicator each entry of [overlays] came from, aligned index for index — item 109.
     *
     * A parallel list and not a field on `ChartLine`, because `ChartLine` belongs to `core:chart`
     * and an indicator id is this module's idea; the legend hands back an index into the list it
     * was given, so an index is exactly what has to be resolvable.
     *
     * Built in the same pass as [overlays] rather than reconstructed afterwards, which is the only
     * way the two stay aligned: one indicator can produce three lines — a Bollinger band is three
     * — so a second pass that assumed one line each would remove the wrong study.
     */
    val overlayOwners: List<String> = emptyList(),
    /** The same for [panes], where the mapping happens to be one to one. Built the same way anyway. */
    val paneOwners: List<String> = emptyList(),
) {
    /** What [ChartDerived] was computed from. See [key]. */
    internal data class Key(
        val series: CandleSeries,
        val active: Set<String>,
        val periods: Map<String, Int>,
        /** The correlation partner, which changes what one pane draws. See [ChartUiState.derived]. */
        val comparison: ComparisonSeries? = null,
        /**
         * The bars on screen.
         *
         * In the key because one study reads it — the visible-range volume profile — and a key
         * without it is a profile computed on the first frame and then carried across every pan
         * the reader makes. That is exactly the failure the key exists to prevent, arriving through
         * the one input nobody had thought of as an input.
         */
        val window: BarWindow = BarWindow.WHOLE_SERIES,
        /** The indicators a chain is drawing instead, which changes what this must skip. */
        val chained: Set<String> = emptySet(),
    )

    /** Whether this value is still the right answer for these inputs, the window included. */
    internal fun matches(
        series: CandleSeries,
        active: Set<String>,
        periods: Map<String, Int>,
        comparison: ComparisonSeries? = null,
        window: BarWindow = BarWindow.WHOLE_SERIES,
        chained: Set<String> = emptySet(),
    ): Boolean = matchesApartFromWindow(series, active, periods, comparison, chained) &&
        key?.window == window

    /**
     * The same check with the window left out — everything that decides *what* is computed.
     *
     * Separate from [matches] because the window is not that kind of input. The other five say
     * which lines exist and what arithmetic makes them; the window says which bars one study
     * measures over, and it moves on every bar of a drag. Folding it in made a drag invalidate the
     * whole answer sixty times a second, so it is checked on its own and repaired by [rewindowed]
     * rather than thrown away with everything else.
     */
    internal fun matchesApartFromWindow(
        series: CandleSeries,
        active: Set<String>,
        periods: Map<String, Int>,
        comparison: ComparisonSeries? = null,
        chained: Set<String> = emptySet(),
    ): Boolean = key != null &&
        key.series === series &&
        key.active == active &&
        key.periods == periods &&
        key.comparison == comparison &&
        key.chained == chained

    /**
     * This value re-answered for a new window, recomputing only the studies that read one.
     *
     * ### Why this can be surgery rather than a recomputation
     *
     * Because a window-scoped study is a study whose *values* depend on where the reader has panned
     * to, and there is exactly one — see [WINDOW_SCOPED]. Every other line on the chart is a
     * function of the bars, the switches and the lookbacks alone, all of which [matchesApartFromWindow]
     * has already established are unchanged. So the correct new answer is the old one with one
     * study's lines replaced, and the rest carried by reference.
     *
     * ### Why it rebuilds the list rather than patching it in place
     *
     * Because a window-scoped study may produce a different *number* of lines at a different
     * window: `volumeProfileFor` answers null in a window where nothing traded, and the row
     * disappears. Patching by index would leave `overlays` and [overlayOwners] one entry out of
     * step — and those two being aligned is what makes the legend's remove button take off the
     * study the reader pressed it on rather than the one after it. So the price lines are rebuilt
     * in the order [of] builds them: catalogue order, price panes first, structure lines after,
     * with each option's group taken from the carried value unless it is window-scoped.
     *
     * The panes, the levels and the markers are untouched, because nothing that produces them
     * takes a window at all.
     */
    internal fun rewindowed(
        series: CandleSeries,
        active: Set<String>,
        periods: Map<String, Int>,
        chained: Set<String>,
        window: BarWindow,
    ): ChartDerived {
        val previous = key ?: return this
        if (previous.window == window) return this
        // Nothing on this chart reads a window, so the new one changes nothing but the key. The
        // key still has to move, or the *next* pan would compare against a stale window and take
        // this branch again for ever — cheap, but a lie in the one field that exists to be checked.
        if (!readsWindow(active, chained)) return copy(key = previous.copy(window = window))

        val held = LinkedHashMap<String, MutableList<ChartLine>>()
        overlayOwners.forEachIndexed { at, owner ->
            overlays.getOrNull(at)?.let { held.getOrPut(owner) { ArrayList() } += it }
        }
        val chosen = ChartCatalog.INDICATORS.filter { it.id in active && it.id !in chained }
        val lines = ArrayList<ChartLine>(overlays.size)
        val owners = ArrayList<String>(overlayOwners.size)
        fun emit(id: String, produced: List<ChartLine>) {
            for (line in produced) {
                lines += line
                owners += id
            }
        }
        for (option in chosen) {
            if (option.pane != IndicatorPane.PRICE) continue
            emit(
                option.id,
                if (option.id in WINDOW_SCOPED) {
                    ChartCatalog.overlayFor(option, series, periods[option.id], window)
                } else {
                    held[option.id].orEmpty()
                },
            )
        }
        for (option in chosen) {
            if (option.pane != IndicatorPane.STRUCTURE) continue
            emit(option.id, held[option.id].orEmpty())
        }
        return copy(
            key = previous.copy(window = window),
            overlays = lines,
            overlayOwners = owners,
        )
    }

    companion object {
        internal val EMPTY = ChartDerived()

        /**
         * The indicators whose answer depends on where the reader has panned to.
         *
         * One entry, and the whole performance argument above rests on that staying true, so it is
         * held here as a set rather than written as an `if` — and `ChartDerivedTest` derives the
         * same set from the catalogue by asking every indicator for its lines at two different
         * windows and seeing which ones answer differently. A study added later that reads a window
         * and is not named here would draw a profile of the bars the reader was looking at several
         * pans ago; the test is what says so before a reader does.
         *
         * `volumeprofile_ind` is the visible-range volume profile. Its whole subject is the window
         * — see `ChartCatalog.volumeProfileFor` — so it is the one line on the chart that genuinely
         * has to be recomputed as the reader drags, and [rewindowed] recomputes it alone.
         */
        internal val WINDOW_SCOPED: Set<String> = setOf("volumeprofile_ind")

        /**
         * Whether anything actually switched on reads the visible window.
         *
         * Asked before the window is published at all — see [ChartController.setVisibleWindow].
         * A chart with no window-scoped study is the ordinary chart, and for it the window is a
         * number nothing reads: publishing it on every bar of a drag would allocate a state,
         * recompose the whole page and re-answer six getters to record a fact with no consumer.
         *
         * A chained indicator is excluded for the same reason [of] excludes it: the chain is
         * drawing that study, and this path is not.
         */
        internal fun readsWindow(active: Set<String>, chained: Set<String>): Boolean =
            WINDOW_SCOPED.any { it in active && it !in chained }

        fun of(
            series: CandleSeries,
            active: Set<String>,
            periods: Map<String, Int>,
            /** The second instrument, for the one study that measures two series against each other. */
            comparison: ComparisonSeries? = null,
            /** The bars on screen, for the one study whose answer depends on them. */
            window: BarWindow = BarWindow.WHOLE_SERIES,
            /**
             * Indicators a chain is already drawing, and which this must therefore not draw.
             *
             * Skipped rather than deduplicated afterwards: the chain draws a chained EMA in the pane
             * of the thing it is measuring, and this path would draw the same EMA over the candles.
             * Two lines in one colour a pixel apart is a rendering fault as far as a reader is
             * concerned, and there is nothing on screen that would let them tell it from one.
             */
            chained: Set<String> = emptySet(),
        ): ChartDerived {
            val key = Key(series, active, periods, comparison, window, chained)
            if (active.isEmpty() || series.isEmpty) return ChartDerived(key = key)
            val chosen = ChartCatalog.INDICATORS.filter { it.id in active && it.id !in chained }
            // Each structure study computed **once** and its three products taken from the one
            // answer. Three separate calls is what this file used to do, and a zigzag over three
            // hundred bars is not a cheap thing to compute twice for nothing.
            val structures = chosen
                .filter { it.pane == IndicatorPane.STRUCTURE }
                .map { ChartCatalog.structureFor(it, series) }
            // Paired before they are flattened, so the owner travels with the line rather than
            // being inferred from a position afterwards. A Bollinger band is three lines from one
            // indicator; a mapping rebuilt on the assumption of one apiece takes the wrong study
            // off the chart when a reader presses remove on the third row.
            val priceLines = chosen
                .filter { it.pane == IndicatorPane.PRICE }
                .flatMap { option ->
                    ChartCatalog.overlayFor(option, series, periods[option.id], window)
                        .map { option.id to it }
                }
            val structureLines = chosen
                .filter { it.pane == IndicatorPane.STRUCTURE }
                .zip(structures)
                .flatMap { (option, structure) -> structure.lines.map { option.id to it } }
            val separatePanes = chosen
                .filter { it.pane == IndicatorPane.SEPARATE }
                .mapNotNull { option ->
                    ChartCatalog.paneFor(option, series, periods[option.id], comparison)
                        ?.let { option.id to it }
                }
            return ChartDerived(
                key = key,
                overlays = (priceLines + structureLines).map { it.second },
                overlayOwners = (priceLines + structureLines).map { it.first },
                levels = structures.flatMap { it.levels },
                markers = structures.flatMap { it.markers },
                paneOwners = separatePanes
                    .filter { it.second.lines.isNotEmpty() || it.second.histogram != null }
                    .map { it.first },
                panes = separatePanes
                    .map { it.second }
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
 * A drawn bar back on the wire's shape, so a bar the phone built can be kept.
 *
 * The one caller is the seconds chart, whose bars exist nowhere else: nothing fetched them and
 * nothing will, so if the archive is not told about them they are gone when the screen is. `closed`
 * is true because this is only ever called on a bar the next one has already replaced. A volume the
 * feed never reported stays absent rather than becoming zero — see [Candle.v].
 */
internal fun Candle.toBar(): OhlcBar =
    OhlcBar(t = t, o = o, h = h, l = l, c = c, v = v ?: 0.0, closed = true)

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
        // Refused before a request went out, because this venue has no feed fine enough to fold
        // this bar length from. Distinguished from the network because retrying cannot help, and
        // a «تلاش دوباره» under it sends the reader round a loop that has no end.
        text.contains("interval_unavailable") -> ChartError.INTERVAL_UNAVAILABLE
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
    timeframe = timeframe,
    sync = sync.name,
    layoutId = layoutId,
    deviations = deviations,
    textColour = textColour,
    fillColour = fillColour,
    lineStyle = lineStyle.name,
    // Carried, not dropped, precisely so the codec can refuse it: a mark made to fade while
    // talking must not be written, and the one place that decision belongs is the codec both
    // stores pass through. Dropping it here would hide the refusal from the layout blob.
    fadesAtMillis = fadesAtMillis,
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
    timeframe = timeframe,
    // `valueOf` and not a `when`, with the ordinary case as the fallback: a value this build does
    // not know belongs to a newer app on the same account, and reading it as `LAYOUT` shows the
    // mark on the chart it was drawn on rather than throwing away somebody's work.
    sync = runCatching { DrawingSync.valueOf(sync) }.getOrDefault(DrawingSync.LAYOUT),
    layoutId = layoutId,
    deviations = deviations,
    textColour = textColour,
    fillColour = fillColour,
    lineStyle = runCatching { LineStyleKind.valueOf(lineStyle) }.getOrDefault(LineStyleKind.SOLID),
)

/**
 * `DrawingSyncMode` as the drawing layer's own reach.
 *
 * Two enums for one idea, and they are in modules that may not see each other: `core:datastore`
 * must not depend on `core:chart`, so the stored setting and the field on a `Drawing` are declared
 * on opposite sides of a boundary and this feature module is the only place both are on the
 * classpath — the same arrangement `ChartUiState.chartColours` uses for the palette.
 *
 * The three cases line up one for one, which is why this is a `when` and not a lookup: if a fourth
 * mode is ever added to either side, this stops compiling rather than silently mapping it to
 * something plausible.
 */
private fun DrawingSyncMode.toDrawingSync(): DrawingSync = when (this) {
    DrawingSyncMode.NONE -> DrawingSync.NONE
    DrawingSyncMode.LAYOUT -> DrawingSync.LAYOUT
    DrawingSyncMode.GLOBAL -> DrawingSync.GLOBAL
}

/**
 * An indicator's source as the template store keeps it: an opaque string.
 *
 * `IndicatorTemplateStore` deliberately does not understand chaining — it stores what it is given
 * and hands it back untouched — so the spelling is this module's, and it has to survive a round
 * trip through a build that has never heard of it. Two forms and nothing else:
 *
 * * `bars:CLOSE` — a column of the candles, named by [BarField].
 * * `out:rsi` / `out:macd:signal` — another node's output, with the output named only when it is
 *   not that indicator's main line.
 *
 * A node id is an indicator id here — one chart draws an indicator once — and an indicator id is
 * letters, digits and underscores, so the colon can never appear inside one and the split is
 * unambiguous.
 */
internal fun encodeChainSource(source: IndicatorSource): String = when (source) {
    is IndicatorSource.Bars -> "bars:" + source.field.name
    is IndicatorSource.Output ->
        if (source.output == null) "out:" + source.nodeId else "out:" + source.nodeId + ":" + source.output
}

/**
 * The reverse, answering null for anything this build cannot read.
 *
 * Null rather than a default, and the caller drops the entry: a source it cannot parse is a source
 * it must not guess at. Silently substituting the close would draw an indicator that looks computed
 * and is not the one the reader saved — the exact failure `IndicatorChain`'s own KDoc refuses for
 * the multi-column indicators.
 */
internal fun decodeChainSource(encoded: String): IndicatorSource? {
    val parts = encoded.split(':')
    return when {
        parts.size == 2 && parts[0] == "bars" ->
            BarField.entries.firstOrNull { it.name == parts[1] }?.let(IndicatorSource::Bars)
        parts.size == 2 && parts[0] == "out" && parts[1].isNotBlank() -> IndicatorSource.Output(parts[1])
        parts.size == 3 && parts[0] == "out" && parts[1].isNotBlank() ->
            IndicatorSource.Output(parts[1], parts[2].takeIf { it.isNotBlank() })
        else -> null
    }
}

/** How long a run of drawing edits is held before the layer is written. See `persistDrawings`. */
private const val DRAWING_PERSIST_DEBOUNCE_MS = 300L
