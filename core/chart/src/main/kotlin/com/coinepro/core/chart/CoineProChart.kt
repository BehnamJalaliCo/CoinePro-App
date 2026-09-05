package com.coinepro.core.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.unit.sp
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.designsystem.TABULAR_FIGURES
import com.coinepro.core.designsystem.CoineProLatinFontFamily
import com.coinepro.core.common.JalaliDate
import com.coinepro.core.common.NumberStyle
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.LocalCoineProPalette
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The price chart.
 *
 * One Canvas and no view hierarchy, because the thing being drawn is a few hundred rectangles that
 * change on every frame of a drag. A composable per candle would allocate and recompose a hundred
 * nodes per frame; this allocates nothing inside the draw loop.
 *
 * The layout is the one every terminal converged on and is not a preference: price axis down the
 * right, time along the bottom, volume in a band under the price. Right rather than start-relative
 * even in Persian — a price axis is not text, it is the y axis of a graph, and mirroring it would
 * put the newest bar on the left, which no trader on earth reads.
 *
 * Everything about *where things are* comes from [ChartViewport] and nothing here computes a
 * coordinate of its own. That is what will let the drawing tools land later without touching this
 * file.
 */
@Composable
fun CoineProChart(
    series: CandleSeries,
    modifier: Modifier = Modifier,
    type: ChartType = ChartType.CANDLES,
    typeConfig: ChartTypeConfig = ChartTypeConfig(),
    decoration: ChartDecoration = ChartDecoration(),
    /** Interaction off makes this a static picture — for a list row or a card. */
    interactive: Boolean = true,
    /**
     * The drawing layer's state, or null on a chart that is not a drawing surface.
     *
     * Hoisted rather than owned here, because a screen has to save it, restore it and show it in a
     * list beside the chart. What is passed in decides what a tap does: with a tool armed a tap
     * places a point, and without one it selects whatever the tap landed on.
     */
    drawing: DrawingState? = null,
    onDrawing: ((DrawingState) -> Unit)? = null,
    /**
     * Whether the eraser is the armed mode.
     *
     * A parameter rather than something read off [DrawingState.tool], because it cannot be read off
     * there: the eraser lives in [ToolGroup.MODES], and [DrawingActions.arm] deliberately refuses to
     * arm a mode as a tool — a mode draws nothing, so putting one in `tool` would leave the chart
     * waiting for taps that never place anything. The screen that owns the rail knows which mode the
     * reader picked, and this is that one bit.
     *
     * With it on, a tap takes out the leg of a stroke under the finger and a long press takes out
     * the whole object. Partial erase is the entire reason the eraser is worth shipping: a reader
     * who overshot the end of a brush stroke wants the overshoot gone, not the stroke, and every
     * "eraser" that only deletes whole objects is a delete button with a different icon.
     */
    eraser: Boolean = false,
    /**
     * Called when the reader pans within [LOAD_MORE_MARGIN] bars of the oldest one loaded.
     *
     * The chart is the only thing that knows where the reader is looking, so it is the only thing
     * that can ask for more history. Before this the fetch existed and had no caller at all: a
     * reader could pan back to the first bar of whatever the first request returned and simply
     * stop, with nothing on screen to say the market had a past.
     *
     * Fired at most once per position — the guard is [LaunchedEffect] keyed on the bar index —
     * and the caller is expected to ignore it while a load is already running.
     */
    onLoadMore: (() -> Unit)? = null,
    /**
     * Grow or shrink the indicator panes, as a factor. Null where the caller does not offer it.
     *
     * Driven by a drag on the boundary between the candles and the first pane — the line a reader
     * reaches for without being told, and the same gesture every desktop terminal uses.
     */
    onScalePanes: ((Float) -> Unit)? = null,
    /**
     * Whether the price axis is logarithmic. See [ChartViewport.logScale] for why it exists.
     *
     * Passed in rather than owned here, because it belongs with the timeframe and the chart type —
     * it is part of what a saved layout means, and a reader who set it should find it set when
     * they come back.
     */
    logScale: Boolean = false,
    /**
     * Offer to set an alert at a price the reader long-pressed on.
     *
     * A callback rather than a menu built here, because an alert is not a drawing: it outlives the
     * screen, it has to be persisted and it has to be named in Persian, and `core:chart` has no
     * strings, no storage and no idea what an alert is. What the chart is uniquely able to say is
     * *which price the finger meant*, which is exactly what crosses this boundary.
     *
     * Fired on a long press that lands on one of [ChartDecoration.levels] — a pivot, a support, a
     * target — rather than anywhere on the plot, because "alert me at 2,643.17" is almost never
     * what a reader means and "alert me at this level" almost always is.
     */
    onRequestAlertAt: ((Double) -> Unit)? = null,
    /**
     * Open the price axis' own menu — log scale, percent, decimals, which side it sits on.
     *
     * Long press on the gutter, which is where every terminal puts it and is the one gesture on
     * this chart a reader is likely to try by accident and be pleased to find. Null on a chart with
     * no menu to open, and then a long press in the gutter does nothing rather than summoning a
     * crosshair the reader was not reaching for.
     */
    onPriceAxisMenu: (() -> Unit)? = null,
    /**
     * What the price axis measures. See [PriceScaleMode].
     *
     * The four modes were on [ChartViewport] and reachable from the axis sheet, were saved per
     * symbol and restored on reopen, and **nothing crossed into the canvas**: a reader could pick
     * «درصد», watch the sheet record it, come back to it a day later still recorded — and the chart
     * never once relabelled. A setting that survives a restart and changes nothing is worse than an
     * absent one, because the reader believes it.
     *
     * [logScale] stays for the callers that hold a saved boolean, and this wins when the two
     * disagree in the only direction they can: a mode that is not [PriceScaleMode.REGULAR] is an
     * explicit choice, and a stale `logScale = true` beside it must not drag the axis back to
     * logarithmic.
     */
    scaleMode: PriceScaleMode = PriceScaleMode.REGULAR,
    /** Draw the axis upside down. See [ChartViewport.inverted]. */
    inverted: Boolean = false,
    /** Tie the price scale to the bar scale, so a pinch moves both. See [ChartViewport.priceBarLock]. */
    priceBarLock: Boolean = false,
    /** Pin every label's precision, or null to derive it. See [ChartViewport.decimals]. */
    decimals: Int? = null,
    /**
     * Which gutter the price axis is drawn in. See [ScaleSide].
     *
     * Carried onto the viewport so that it is one value rather than two that can disagree, and so
     * anything reading the viewport — a drawing, an alert composer, a screenshot — sees the reader's
     * choice. It is honoured for real: [plotFrame] decides the plot rectangle from it, the ladder is
     * painted into whichever gutters it asks for, and every gesture that used to measure against
     * `size.width` now measures against that rectangle. [ScaleSide.MERGED] draws the right-hand
     * gutter, because this canvas puts every series it draws on the one price axis already.
     */
    scaleSide: ScaleSide = ScaleSide.RIGHT,
    /**
     * Merge points that would land in the same half-pixel column into one vertical stroke.
     *
     * Off by default, which is what TradingView ships and is the right default here for the same
     * reason: at ordinary zooms no two bars share a column, so every comparison it makes is one that
     * fails, and the cost is a comparison per bar for a picture that is identical. Panned out past a
     * screenful of bars it is the difference between six vertices a pixel and one. See
     * [ColumnConflator] for why it keeps each column's extremes rather than one point.
     */
    conflate: Boolean = false,
    /**
     * The zone the dates along the bottom are read in, and the one the bold month boundary uses.
     *
     * A parameter because it is a reader's setting everywhere else in this category of app — a
     * trader in Tehran reading a New York session wants the session's own clock — and because the
     * two things that print a date here have to be told the *same* zone or they disagree with each
     * other. They did: the labels and the boundary both took the device's zone while the bars
     * themselves are bucketed in [CHART_ZONE], so on a phone outside Tehran the bold label landed on
     * a different bar than the month it was naming.
     *
     * Resolved by the caller and passed in already built. [ZoneId.of] is a lookup and a parse, and
     * the one place it must never happen is inside the draw pass — a zone per label, per frame.
     */
    zone: ZoneId = CHART_ZONE,
    /** The instrument's name for the legend's first row, or null to leave it as the four prices. */
    seriesLabel: String? = null,
    /** The instrument whose mark opens the legend. See [ChartLegendOverlay]. */
    legendLogo: String? = null,
    /**
     * Which legend rows are switched off, as the caller last stored them.
     *
     * The chart honours this itself — a hidden overlay is not drawn, a hidden pane takes no height —
     * rather than only reporting the tap, because a visibility toggle that draws the same chart is
     * the defect this whole wave is about. A tap toggles the row here *and* reports it through
     * [onToggleSeriesVisibility]; a caller that stores the answer and passes it back finds the chart
     * already in that state, and a caller that only listens still gets a working control.
     */
    hiddenSeries: Set<ChartLegendTarget> = emptySet(),
    /** A legend row's eye was tapped. See [hiddenSeries]. */
    onToggleSeriesVisibility: ((ChartLegendTarget) -> Unit)? = null,
    /** A legend row's settings were asked for. Null hides the affordance rather than disabling it. */
    onSeriesSettings: ((ChartLegendTarget) -> Unit)? = null,
    /** A legend row's remove was tapped. Null hides the affordance rather than disabling it. */
    onRemoveSeries: ((ChartLegendTarget) -> Unit)? = null,
    /**
     * The session's move for the legend's change row, or null to let the bar answer for itself.
     *
     * Given, it wins on the last bar and is ignored on every earlier one: once the crosshair takes
     * the legend into history, a figure about *today* printed under a bar from March is a number
     * about a different day. See `ChartLegendChange`.
     */
    change: ChartLegendChange? = null,
    /** Whether this instrument is trading. Named on the legend's head row only when it is not. */
    marketStatus: ChartMarketStatus? = null,
    /**
     * Where the crosshair went, every time it moved or was dismissed.
     *
     * Hoisted so two panes can share one. It was private `remember` state, which is exactly why the
     * panes screen shipped with its crosshair-sync switch disabled: there was no way for one chart
     * to tell the other what the reader was pointing at. Appended with a default, so no existing
     * call site changes.
     */
    onCrosshairMove: ((Crosshair?) -> Unit)? = null,
    /**
     * The magnet pulled a placed point onto a bar's open, high, low or close.
     *
     * Fired once per placement, only when the point actually moved onto a channel — a tap with
     * the magnet off, or one that landed outside the weak magnet's reach, is not a snap. The
     * caller's haptic goes here; the chart itself makes no sound and no vibration.
     */
    onSnap: (() -> Unit)? = null,
    /**
     * A crosshair placed by something other than this chart's own finger.
     *
     * Drawn instead of the local one when it is non-null, and it does not disturb the local one —
     * the second pane keeps whatever its own reader last did, so releasing the sync puts both back
     * where they were rather than leaving one of them holding the other's bar.
     */
    crosshairOverride: Crosshair? = null,
    /** Where the reader is looking, every time it changes. The other half of pane sync. */
    onViewportChange: ((ChartViewport) -> Unit)? = null,
    /**
     * A window donated by another pane: how far zoomed in, how far panned back, how stretched.
     *
     * Those three numbers and not the whole viewport, because the other pane is a different
     * instrument with a different price range and a different series — adopting its geometry whole
     * would put this chart's bars on that chart's prices. Three numbers are what "the same window"
     * actually means.
     */
    viewportOverride: ChartViewport? = null,
    /**
     * A glyph on the time axis was tapped, with everything that landed in that bar.
     *
     * Null draws the marks and does not offer them, which is the honest state for a screen with
     * nowhere to open one — a glyph that answers nothing is worse than no glyph. The tap is taken
     * inside the axis strip only, so nothing on the plot changes meaning.
     */
    onEventMark: ((EventMark) -> Unit)? = null,
    /**
     * The purple ring under the live bar — TradingView's trade button.
     *
     * The phone app hangs a 20 pt ring with a lightning bolt at the bottom of the plot, under the
     * newest bar, and a tap on it opens the broker's order panel. Null draws nothing, which is the
     * honest state for a screen with nowhere to trade from. The ring follows the bar as the reader
     * pans and disappears with it when the live edge is off screen: a trade button over history
     * would be a button for a price that is not the market's.
     */
    onTradeRing: (() -> Unit)? = null,
    /**
     * Put a particular bar on screen — what «برو به تاریخ» actually does.
     *
     * The index into the *displayed* series, counted from the oldest bar, or null to leave the view
     * where the reader put it. `ChartController.focusBar` has held this number and
     * `ChartUiState.focusIndex` has carried it since go-to-date was built; the canvas had no
     * parameter to hand it to, so a reader could type a date, watch the field accept it, and see
     * the chart stay exactly where it was. A resolved date that moves nothing is worse than no
     * field at all, because it teaches the reader that their chart has no history there.
     *
     * The pan is a one-shot keyed on the value, not a lock: the reader may pan straight off it
     * again, and asking for the same bar twice does nothing — which is why the caller clears it
     * back to null after a jump. It writes through the same [ChartViewport.atOffset] every gesture
     * uses, so the saved zoom and the saved offset are updated by the effect below exactly as they
     * are after a drag, and an index past either end of the series lands on that end rather than
     * throwing.
     */
    focusIndex: Int? = null,
    /**
     * A zoom step asked for from outside the canvas — the Drawings sheet's «Zoom in» and «Zoom
     * out», which TradingView's phone app keeps beside the drawing modes.
     *
     * One-shot, keyed on the request's serial like [focusIndex] is keyed on the bar: the same
     * factor asked for twice is two steps, and a request already applied is not applied again on
     * recomposition. It goes through [ChartViewport.zoomedBy], the same arithmetic a pinch uses,
     * so the saved zoom and the live-edge margin behave exactly as they do after a pinch.
     */
    zoomNudge: ChartZoomNudge? = null,
) {
    val display = remember(series, type, typeConfig) { ChartTransforms.apply(series, type, typeConfig) }
    // Unkeyed, and that is the whole point. Keyed on `display`, this reset to the default 120 bars
    // at the live edge every time the series identity changed — which is every timeframe switch,
    // every chart-type switch, and, because `ReplayState.visible` allocates a new series on each
    // read, *every frame of a replay*. A reader could not zoom in during a replay at all, and the
    // line below — the anchor-preserving `withSeries`, written precisely to stop the view jumping
    // — ran against an already-reset value and had nothing to preserve.
    var viewport by remember { mutableStateOf(ChartViewport(display)) }
    var crosshair by remember { mutableStateOf<Crosshair?>(null) }

    /**
     * Where the reader was looking, in the two numbers that survive anything.
     *
     * A [ChartViewport] cannot be saved — it holds a whole [CandleSeries], which is megabytes and
     * is refetched anyway. Its *state* is two integers: how far zoomed in, and how far panned back.
     * Saved, those two rebuild the view over whatever series comes back.
     *
     * This is the second-most-complained-about thing about charts in this category of app, after
     * load speed: a reader zooms into an hour of price action, the phone rotates or Android
     * reclaims the process, and the chart comes back at the default hundred and twenty bars on the
     * live edge with the work thrown away.
     */
    var savedZoom by rememberSaveable { mutableIntStateOf(ChartViewport.DEFAULT_BARS_PER_VIEW) }

    /**
     * And how far panned back, or [UNSET_OFFSET] on a chart nobody has panned yet.
     *
     * A sentinel rather than zero, because zero is now a position a reader can deliberately be in —
     * the newest bar against the axis — and is *not* where a chart should open. Where it opens is
     * [ChartViewport.atRest], which keeps a few empty slots at the live edge, and that is a number
     * the viewport works out from the zoom rather than one this can hard-code. Without the sentinel
     * there is no way to tell "never panned" from "panned to the very edge", and the restore would
     * glue every chart in the app to its price axis on the first frame.
     */
    var savedOffset by rememberSaveable { mutableIntStateOf(UNSET_OFFSET) }

    /**
     * And how far the price axis was stretched.
     *
     * The third number that makes up "where the reader was looking". Saved with the other two for
     * the same reason: a reader who compressed a noisy chart to see its shape, then rotated the
     * phone, should not have to do it again.
     */
    var savedPriceZoom by rememberSaveable { mutableFloatStateOf(1f) }

    /**
     * Whether the saved trio has already been spent on this composition.
     *
     * A plain holder rather than a `mutableStateOf`, and deliberately not `rememberSaveable`: it is
     * a fact about *this composition*, so a fresh one — process death, rotation, navigating back
     * from the studio — starts false and restores, and writing it must not invalidate anything. The
     * file uses the same array idiom for `dirty` and `paneTop`.
     */
    val seeded = remember { booleanArrayOf(false) }

    // Follow the live edge as bars arrive, but never drag the view out from under a reader who has
    // panned back. ChartViewport.withSeries decides which of those applies.
    //
    // The saved trio is applied on the same pass rather than in a separate effect: an effect runs
    // after the first frame, so the chart would draw once at the default and then jump. And it is
    // applied on the *first* pass only — see [seedViewport] for the pan bug that fixed.
    remember(display) {
        // **Read without subscribing, and that is not a micro-optimisation.**
        //
        // The three saved numbers are written from the effect below, which runs after every pan
        // step. Read plainly here they would be read *during composition*, so this composable's
        // recompose scope would subscribe to all three — and the effect's write would then
        // invalidate the whole chart a second time for every bar the reader drags. One drag step
        // cost two complete recompositions of a composable with nine gesture handlers, four
        // measured labels and a thousand-line draw lambda in it, and the second one existed only to
        // record that the first had happened.
        //
        // `withoutReadObservation` is exactly the right instrument rather than a trick: these are
        // *seed* values, spent once on the first pass of a fresh composition — see [seedViewport] —
        // and a seed that re-fires when its own consequence is written back is a loop, not a
        // subscription worth having. A restore still works, because a fresh composition reads them
        // on its first pass, which is the only pass that spends them.
        viewport = Snapshot.withoutReadObservation {
            seedViewport(
                current = viewport,
                series = display,
                seeded = seeded[0],
                savedZoom = savedZoom,
                savedOffset = savedOffset,
                savedPriceZoom = savedPriceZoom,
                // A chart with an axis rests with air at the live edge; one without is a thumbnail
                // or a list-row sparkline, which has no gutter to breathe into and every pixel
                // spent on the shape. `showAxes` is the one bit that already separates the two
                // everywhere else in this file.
                restAtEdge = decoration.showAxes,
            )
        }
        seeded[0] = true
    }

    // Applied separately from the series, because it changes on its own — a reader toggling the
    // axis has not changed a single bar, and folding it into the block above would make the
    // toggle a no-op until the next series arrived.
    remember(logScale, scaleMode, inverted, priceBarLock, decimals, scaleSide) {
        // The mode wins over the boolean when they disagree. See the `scaleMode` parameter: a
        // caller that has both is a caller migrating from one to the other, and the explicit choice
        // is the newer of the two.
        val mode = if (scaleMode == PriceScaleMode.REGULAR && logScale) {
            PriceScaleMode.LOGARITHMIC
        } else {
            scaleMode
        }
        viewport = viewport
            .withScaleMode(mode)
            .copy(inverted = inverted, priceBarLock = priceBarLock)
            .withDecimals(decimals)
            .withScaleSide(scaleSide)
    }

    // The saved-state write-back for the viewport used to be here, as a `LaunchedEffect` keyed on
    // the three numbers. It now shares one snapshot flow with the outward report — see the block
    // beside `emitViewport` below — because two effects keyed on fields of a value that changes on
    // every frame of a drag are two effect restarts per frame, and a restart is a coroutine
    // cancelled and relaunched through the recomposer, in the frame. Neither needed to be in
    // composition at all.

    // Pan to the bar the caller asked for. After the viewport restoration above rather than inside
    // it, so a jump is one more move of the same viewport — the restoration still runs first on a
    // fresh composition, and what it restores is then overridden only when a date was actually
    // asked for.
    LaunchedEffect(focusIndex) {
        if (focusIndex != null) viewport = viewport.atOffset(focusOffset(display.size, focusIndex))
    }
    LaunchedEffect(zoomNudge?.serial) {
        if (zoomNudge != null) viewport = viewport.zoomedBy(zoomNudge.factor)
    }

    // Ask for history when the reader gets near the edge of it. Keyed on the first visible bar, so
    // the request goes out once per position rather than on every frame of a drag — and, because
    // the index changes as bars are prepended, again once the reader keeps going past the new
    // oldest bar.
    //
    // Keyed on the *oldest bar's timestamp* rather than on the series, and that is the half of «چارت
    // خیلی کنده» that lives in this file. `display` is a new object on every prepend — and on every
    // frame of a replay — so keying on it restarted the effect and asked again immediately, which
    // once the candle archive started answering `loadMore` from disk meant a page request per frame
    // for as long as the reader stayed near the left edge. The oldest timestamp changes exactly once
    // per page that actually arrives, which is the rate this should run at.
    if (onLoadMore != null) {
        val nearStart = viewport.firstVisible <= LOAD_MORE_MARGIN
        val oldestBar = if (display.isEmpty) 0L else display.time.first()
        LaunchedEffect(nearStart, oldestBar) {
            if (nearStart && !display.isEmpty) onLoadMore()
        }
    }

    /**
     * The wall clock, once a second, and only when something on screen reads it.
     *
     * Gated three ways so an idle or historical chart ticks nothing at all: the caller has to ask
     * for a countdown, the reader has to be at the live edge, and the series has to be long enough
     * to have a bar interval. A thumbnail in a list row passes none of those and never starts a
     * coroutine.
     *
     * Not continuous motion in the sense the reduced-motion gate is about — it is a clock, and a
     * clock that stops when animations are off is a broken clock.
     */
    val countdownLive = decoration.showCountdown && viewport.isAtLiveEdge && display.size >= 2
    var nowSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(countdownLive) {
        if (!countdownLive) return@LaunchedEffect
        while (true) {
            nowSeconds = System.currentTimeMillis() / 1_000
            // Aligned to the next whole second rather than a flat one-second sleep, so the digits
            // change on the second instead of drifting a few milliseconds later each minute.
            delay(1_000 - System.currentTimeMillis() % 1_000)
        }
    }

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val priceFontSp = axisFontSizeSp(isPriceAxis = true)
    val timeFontSp = axisFontSizeSp(isPriceAxis = false)

    /**
     * The gutter's width, measured rather than declared.
     *
     * It used to be a flat 58dp with a comment saying "enough for six digits at 10sp", which was
     * true of neither the font it ended up using nor of an instrument quoted to six decimals. Now
     * it is [priceAxisWidth] over the widest label the axis is actually going to print, which is
     * the last close at its own precision — and that number comes back *even*, so the hairline
     * between the plot and the axis lands on a device pixel instead of being smeared across two.
     *
     * Keyed on the series rather than recomputed per frame: the magnitude of a price does not
     * change between two bars of the same instrument, and re-measuring text inside a draw pass is
     * the one thing in this file that would actually cost a frame.
     */
    // **Grouped, because the axis prints it grouped.** This used to be a bare `formatPrice`, and
    // the labels beside it go through `axisText`, which puts the thousands separators in — so the
    // gutter was measured against `81192.5` and then asked to hold `81,192.5`. One comma is about
    // four pixels, the tag is drawn a fixed inset from the gutter's edge, and the result is the
    // live price tag running off the right-hand edge of the glass: «قیمت از کادر زده بیرون». A
    // price over a million loses two commas' worth and clips two characters.
    val sampleLabel = remember(display) {
        val price = display.bars.lastOrNull()?.c ?: 0.0
        groupThousands(formatPrice(price, decimalsFor(price)))
    }
    val axisWidth = remember(sampleLabel, priceFontSp, density, measurer) {
        priceAxisWidth(
            maxLabelWidth = measurer
                .measure(sampleLabel, axisStyle(Color.Black, priceFontSp))
                .size.width.toFloat(),
            fontSize = with(density) { priceFontSp.sp.toPx() },
        )
    }
    val timeHeight = remember(timeFontSp, density) {
        timeAxisHeight(with(density) { timeFontSp.sp.toPx() })
    }

    /**
     * How much room one date needs on the time axis, measured rather than guessed.
     *
     * `timeAxisTicks` turns this into a minimum gap in *bars*, which is what decides how many
     * labels the ladder is allowed to place. Measured off a real label at the real font — `30 Sep`
     * is the widest shape the axis prints in the common case — plus the clear space two labels need
     * between them. A guessed number here is a chart whose dates either collide at one zoom or
     * thin out to two at another, and both look like a bug rather than a layout.
     */
    val timeLabelGapPx = remember(timeFontSp, density, measurer) {
        measurer
            .measure(TIME_LABEL_SAMPLE, axisStyle(Color.Black, timeFontSp))
            .size.width.toFloat() + LABEL_GAP
    }
    val tolerancePx = with(density) { DrawingHitTest.TOLERANCE_DP.dp.toPx() }

    /**
     * The last viewport the draw pass computed, for the gestures to read.
     *
     * A plain array rather than state: the gesture handlers need the plot size, which is only known
     * inside the draw lambda, and writing state from a draw pass invites a recomposition loop. The
     * gestures do not need to recompose when it changes — they need to read the current value at
     * the moment a finger lands.
     */
    val lastView = remember { arrayOfNulls<ChartViewport>(1) }

    /**
     * Whether the drag in progress started in the price gutter.
     *
     * Decided once, at the down event, and held for the gesture. Testing the current position on
     * every move would let a finger that started on the plot wander into the gutter and start
     * rescaling mid-drag, which is the sort of thing that feels like the chart having a seizure.
     */
    var gutterDrag by remember { mutableStateOf(false) }

    /**
     * Where the candles end and the first indicator pane begins, in pixels.
     *
     * A plain array rather than state, for the same reason [lastView] is one: it is written by the
     * draw pass, which is the only thing that knows where the boundary landed, and the gesture
     * needs to *read* it at the moment a finger lands rather than recompose when it changes. Zero
     * means there is no pane and so no divider to drag.
     */
    val paneTop = remember { floatArrayOf(0f) }

    /**
     * Where the time axis begins, in pixels, or zero when there is none.
     *
     * Published by the draw pass for the same reason [paneTop] is: the strip's top edge depends on
     * how much height the panes took, which nothing outside the draw lambda knows. The gesture that
     * scales time reads it at the moment a finger lands, and the pan gesture reads it to refuse a
     * drag that started down there — two controls in the same pixels is a chart that does both
     * things at once and neither of them properly.
     */
    val timeAxisTop = remember { floatArrayOf(0f) }

    /**
     * The trade ring's centre and radius in canvas pixels, or a zero radius while none is drawn.
     *
     * Written by the draw pass and read by the tap handler, for the reason [timeAxisTop] is: only
     * the draw pass knows where the newest bar landed.
     */
    val tradeRing = remember { floatArrayOf(0f, 0f, 0f) }

    /**
     * Each indicator pane's scale as the draw pass resolved it, for the crosshair layer to read.
     *
     * A plain array for the same reason [paneTop] is one — written by the draw pass, read by a
     * second draw pass, and making it state would recompose the tree to record that a frame had
     * been drawn. See [PaneBand] for what the crosshair does with it and what it was doing before.
     */
    val paneBands = remember { arrayOf(emptyList<PaneBand>()) }
    val armed = drawing?.tool

    /**
     * Tracking mode: the crosshair is down and staying down.
     *
     * Entered by a long press and left by a tap. It is a *mode* rather than a gesture because
     * reading a value off a chart is a two-handed job on a phone — a finger held on a candle covers
     * the candle, and the reader has to lift it to see what the legend said. So the crosshair stays
     * where it was put, panning is suspended so a stray drag cannot slide the bar out from under
     * it, and the corner readout fills out with every indicator on the chart rather than the
     * overlays alone.
     */
    var tracking by remember { mutableStateOf(false) }

    /**
     * Line-movement mode: a vertical drag moves the selected drawing and nothing else.
     *
     * Entered by a double tap on a chart with something selected. Placing a level at exactly the
     * right price with a finger is the hardest thing to do on a drawing layer, because the same
     * drag that adjusts the price also drags the line sideways through time — and for a horizontal
     * level, sideways is meaningless. This locks the time component to zero, so the whole gesture
     * spends itself on the one number the reader is trying to land.
     */
    var lineMove by remember { mutableStateOf(false) }

    /**
     * Which handle of the selected drawing a finger is on, and how far its grab has animated.
     *
     * Two indices rather than one, because the shrink has to outlive the gesture: the moment the
     * finger lifts, `grabbedHandle` goes to −1 while `paintedHandle` keeps the index for the two
     * hundred milliseconds the ring takes to come back down. One index would make the handle snap
     * to its resting size the instant the reader let go, which reads as the drawing having been
     * dropped rather than placed.
     */
    var grabbedHandle by remember { mutableIntStateOf(-1) }
    var paintedHandle by remember { mutableIntStateOf(-1) }
    val grabGrow = remember { Animatable(0f) }
    LaunchedEffect(grabbedHandle) {
        if (grabbedHandle >= 0) {
            paintedHandle = grabbedHandle
            grabGrow.animateTo(1f, tween(HANDLE_GRAB_MS))
        } else {
            grabGrow.animateTo(0f, tween(HANDLE_GRAB_MS))
            paintedHandle = -1
        }
    }

    /** Momentum after a flick. See [KineticScroll] for why it is touch-only. */
    val kinetic = remember(density) { KineticScroll(density.density) }
    var flinging by remember { mutableStateOf(false) }

    /**
     * How far past the end of the history the picture is currently stretched, in pixels.
     *
     * See [ChartViewport.pixelShift] for what it does to the drawing and why it is not the pan.
     * This is the value itself and the spring that returns it: an [Animatable] rather than a plain
     * float so that the release is one call and cannot be left half-sprung by a recomposition.
     */
    val edgePull = remember { Animatable(0f) }
    val pullScope = rememberCoroutineScope()
    val maxPull = with(density) { EDGE_PULL_MAX_DP.toPx() }

    /**
     * Stretch the band by [pixels] of refused travel, with the resistance a rubber band has.
     *
     * The gain falls off as the band stretches — at the cap it is zero — so the reader feels the
     * end coming rather than arriving at it. A constant gain would make the band a second, slower
     * chart that still slides forever, which says the opposite of what it is here to say.
     */
    fun stretchEdge(pixels: Float) {
        if (maxPull <= 0f || pixels == 0f) return
        val slack = 1f - abs(edgePull.value) / maxPull
        if (slack <= 0f) return
        val next = (edgePull.value + pixels * EDGE_PULL_GAIN * slack).coerceIn(-maxPull, maxPull)
        pullScope.launch { edgePull.snapTo(next) }
    }

    /** Let the band go. A no-op when it was never stretched, which is nearly every gesture. */
    fun releaseEdge() {
        if (edgePull.value == 0f) return
        pullScope.launch {
            edgePull.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    // Bouncy enough to be seen at forty pixels, and not so bouncy that the bars
                    // oscillate: one overshoot, small, and settled.
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
    }

    /**
     * How much of the chart the last change actually invalidated, merged since the last draw.
     *
     * A plain array rather than state, for the same reason [lastView] is one: it is written by a
     * gesture and read by the draw pass, and making it state would recompose the whole subtree to
     * record that a crosshair moved. The write is always paired with a real state change — the
     * crosshair, the viewport — so the redraw is already coming; this only says how much of it has
     * to be recomputed.
     */
    val dirty = remember { arrayOf(Invalidation.FULL) }

    /** The last scale and its ticks, reused when only the cursor has moved. See [Invalidation]. */
    val scaleCache = remember { ScaleCache() }

    // A `pointerInput` block is started once and then runs across recompositions, so anything it
    // reads directly is whatever was in scope the frame it started — a callback the caller has
    // since replaced, or last frame's levels. Keying the block on them instead would restart the
    // gesture detector mid-drag every time the caller passed a new lambda, which on a chart that
    // recomposes with the price is every second. `rememberUpdatedState` is the way out of both.
    val currentEvents = rememberUpdatedState(decoration.events)
    val currentEventMark = rememberUpdatedState(onEventMark)
    val currentLevels = rememberUpdatedState(decoration.levels)
    val currentAlert = rememberUpdatedState(onRequestAlertAt)
    val currentAxisMenu = rememberUpdatedState(onPriceAxisMenu)
    val currentTradeRing = rememberUpdatedState(onTradeRing)

    fun invalidate(level: Invalidation) {
        dirty[0] = dirty[0].merge(level)
    }

    // A comparison arriving, leaving, or changing basis repaints the plot without moving the price
    // scale — the axis stays the base instrument's, which is the whole design of the feature. LIGHT
    // rather than CURSOR, because CURSOR is the level that lets the previous viewport instance and
    // its tick ladder be reused, and reusing them here would be reusing a frame that had no
    // comparison on it.
    remember(decoration.comparisons, decoration.comparisonBasis) {
        invalidate(Invalidation.LIGHT)
    }

    /**
     * The viewport as it was actually drawn, for a gesture that needs to measure against pixels.
     *
     * The viewport this composable *holds* is never sized: sizing happens on a copy inside the draw
     * pass, because writing the layout back into state from a draw would recompose every frame. So
     * its `plotWidth` and `plotHeight` are both zero, and every gesture that divided by one of them
     * was quietly doing nothing at all — a pan whose bar width was zero moved no bars, a gutter
     * drag whose plot height was zero returned before it rescaled anything, and a crosshair placed
     * against it reported the first bar at the bottom of the range wherever the finger landed.
     *
     * `lastView` is the one the draw pass published, and it is the only one with a size on it.
     */
    fun drawn(): ChartViewport = lastView[0] ?: viewport

    /** The drawing layer as of this frame, for the effect below to read without restarting on it. */
    val currentDrawing = rememberUpdatedState(drawing)
    val currentOnDrawing = rememberUpdatedState(onDrawing)

    // Pull every magnet-bound anchor back onto its channel whenever the bars are replaced.
    //
    // This is what the binding is *for*, and without it the whole apparatus — the channel chosen at
    // the tap, carried through the state, written into the store — is bookkeeping nobody spends. A
    // feed that revises a bar, refetches a session or corrects a bad tick moves the low a trend line
    // was drawn against; a line bound to "the low of that bar" moves with it, while a line frozen at
    // the number that low used to be is left hanging a few ticks above the very thing it touched.
    //
    // Keyed on the series alone, and deliberately not on the drawing state: keying on the drawings
    // would restart this on every frame of a drag, and the drag is not a data revision.
    //
    // Guarded on the bindings being non-empty, so a chart whose drawings were all placed with the
    // magnet off does no work at all. And the result is compared before it is emitted, so a new
    // series that moved nothing does not push a state change through persistence to say so.
    LaunchedEffect(display) {
        val state = currentDrawing.value ?: return@LaunchedEffect
        val emit = currentOnDrawing.value ?: return@LaunchedEffect
        if (state.bindings.isEmpty() || display.isEmpty) return@LaunchedEffect
        val resnapped = DrawingActions.resnap(state, display)
        if (resnapped != state) emit(resnapped)
    }

    // A series that has emptied — a failed load, a symbol with no history — must not leave a
    // crosshair behind pointing at a bar index that no longer exists, and must not leave the
    // reader in a tracking mode with nothing to track.
    LaunchedEffect(display.isEmpty) {
        if (display.isEmpty) {
            crosshair = null
            tracking = false
            lineMove = false
            kinetic.stop()
        }
    }

    /**
     * The fling, driven off the frame clock rather than off a timer.
     *
     * [withFrameMillis] rather than a `delay` loop, because the animation has to advance exactly
     * once per frame that is actually going to be drawn: a timer that fires between frames does the
     * arithmetic twice and moves the chart twice as far, and one that fires late leaves a gap.
     *
     * The residue is carried across frames for the same reason the drag's is — panning is quantised
     * to whole bars, and throwing away the remainder every frame turns a smooth deceleration into a
     * stutter that stops early. Reaching the end of the loaded history stops the fling outright
     * rather than letting it spend the rest of its momentum against a wall.
     */
    LaunchedEffect(flinging) {
        if (!flinging) return@LaunchedEffect
        var residue = 0f
        while (kinetic.isRunning) {
            val step = withFrameMillis { now -> kinetic.tick(now) }
            residue += step
            val width = drawn().barWidth
            if (width <= 0f) break
            val bars = (residue / width).toInt()
            if (bars != 0) {
                val before = viewport.offset
                viewport = viewport.atOffset(before + bars)
                residue -= bars * width
                if (viewport.offset == before) {
                    // The wall. The momentum is spent into the band rather than deleted: a fling
                    // that stops in a single frame with nothing on screen to explain it is
                    // indistinguishable from the app having stopped listening, and the reader's
                    // next move is to flick again, harder, at a chart that has no more history.
                    kinetic.stop()
                    stretchEdge(step)
                }
                invalidate(Invalidation.FULL)
            }
        }
        // Whether it ran out of momentum or hit the end, the band goes back either way — and
        // `releaseEdge` costs nothing on the ordinary fling that never reached an edge.
        releaseEdge()
        flinging = false
    }

    /**
     * The colours the whole draw pass paints with, resolved once.
     *
     * Once, and at the top, rather than a `?:` at each of the forty places a colour is read. Every
     * one of those would be a place a later change could forget, and a colour template that recolours
     * the candles and leaves the volume bars on the theme's is the sort of half-applied setting that
     * reads as a rendering bug rather than as a missing feature.
     *
     * A stored template beat the theme, and a null one keeps the theme exactly — which is what every
     * chart in the app that has never heard of templates passes, so their pixels do not move.
     */
    // TradingView's own chart palette, measured off its chart on 2026-09-02 (see
    // docs/design/TRADINGVIEW_PARITY.md) rather than the app's market colours. The owner's brief
    // is that the chart be point-for-point TradingView's, and the colour of a candle is the first
    // point anybody compares. Only the chart takes these; the rest of the app keeps its own greens.
    val themePalette = if (LocalCoineProPalette.current.isDark) {
        ChartPalette(
            up = Color(TradingViewPalette.UP),
            down = Color(TradingViewPalette.DOWN),
            grid = Color(TradingViewPalette.DARK_GRID),
            text = Color(TradingViewPalette.DARK_TEXT),
            crosshair = Color(TradingViewPalette.DARK_CROSSHAIR),
            stage = CoineProColors.Stage,
        )
    } else {
        ChartPalette(
            up = Color(TradingViewPalette.UP),
            down = Color(TradingViewPalette.DOWN),
            grid = Color(TradingViewPalette.LIGHT_GRID),
            text = Color(TradingViewPalette.LIGHT_TEXT),
            crosshair = Color(TradingViewPalette.LIGHT_CROSSHAIR),
            stage = CoineProColors.Stage,
        )
    }

    /**
     * Whether the dates along the bottom are written in Solar Hijri. See [formatTimeTick].
     *
     * Read from the configuration rather than from `Locale.getDefault()`, and that is not a
     * preference: this app sets its language per-app, so the process default and the configuration
     * can legitimately disagree — and the configuration is the one every other date in the app is
     * already drawn from, through `stringResource`. Resolved once here rather than per label,
     * because it is read inside a draw pass that runs sixty times a second on a pan.
     */
    val jalaliDates =
        LocalConfiguration.current.locales[0]?.language == AppLanguage.PERSIAN.tag

    /**
     * Which legend rows the reader has switched off.
     *
     * Seeded from the caller and toggled here, which is the one place a controlled/uncontrolled
     * mix is the right answer: a caller that stores the set gets it back through
     * [onToggleSeriesVisibility] and passes it in, and the `remember` key adopts it; a caller that
     * only listens still gets a control that works. The alternative — reporting the tap and
     * drawing the same chart — is the exact defect this wave is about.
     */
    var hidden by remember(hiddenSeries) { mutableStateOf(hiddenSeries) }

    /**
     * The decoration with the hidden rows taken out, which is what actually gets drawn.
     *
     * Taken out rather than skipped at each draw call, because a hidden pane must not go on
     * claiming its share of the canvas' height — a reader who hides an oscillator expects the
     * candles to grow back into the space.
     *
     * The legend is built from the *unfiltered* decoration, so a hidden row keeps its place, its
     * index and its eye. A legend that drops the row it just hid gives the reader no way back.
     */
    val shown = if (hidden.isEmpty()) {
        decoration
    } else {
        decoration.copy(
            overlays = decoration.overlays.filterIndexed { index, _ ->
                ChartLegendTarget.Overlay(index) !in hidden
            },
            panes = decoration.panes.filterIndexed { index, _ ->
                ChartLegendTarget.Pane(index) !in hidden
            },
            comparisons = decoration.comparisons.filterIndexed { index, _ ->
                ChartLegendTarget.Comparison(index) !in hidden
            },
        )
    }

    val palette = decoration.colours?.let { chosen ->
        ChartPalette(
            up = chartColour(chosen.up),
            down = chartColour(chosen.down),
            grid = chartColour(chosen.grid),
            text = chartColour(chosen.text),
            // The base colour only. Every line that draws with it applies its own alpha and its own
            // dash — the crosshair is deliberately fainter than the levels it crosses — and taking
            // the alpha from the template as well would let one stored value silently override a
            // decision the renderer makes for legibility.
            crosshair = chartColour(chosen.crosshair),
            // `stage` is this file's name for the ground: the axis gutters, the legend plate, the
            // panel behind a tag, and the whole canvas when a template is in force.
            stage = chartColour(chosen.background),
        )
    } ?: themePalette

    // ------------------------------------------------------------------ what each type needs
    //
    // Worked out here and not in the draw pass. Every one of these walks the series, and a draw
    // pass runs on every frame of a drag: keyed on the window instead, they run when the window
    // moves, which is once per pan step rather than sixty times a second.

    /**
     * The level a baseline chart splits its fill at.
     *
     * The reader's own when they set one, and otherwise the *window's* opening close — which is what
     * makes the two fills read as "up on the period shown" and "down on it", and what keeps the base
     * on screen as the reader pans. A series-wide default would leave the base off the top of the
     * plot on any panned view and paint the whole chart one colour.
     */
    // Keyed on the type as well, and that key is doing real work. This block builds a
    // `CandleSeries` — which validates its bars' ordering on construction and grows six lazy
    // columns on first read — and it is re-keyed on `firstVisible`, so it ran on *every bar of
    // every drag*, on every chart in the app, to produce a number only the baseline type reads.
    // Fifteen of the sixteen chart types were paying for a split level they never draw.
    val baseLevel = remember(display, type, typeConfig.baseLevel, viewport.firstVisible, viewport.lastVisible) {
        typeConfig.baseLevel ?: if (display.isEmpty || type != ChartType.BASELINE) {
            0.0
        } else {
            ChartTransforms.defaultBaseLevel(
                CandleSeries(
                    display.bars.subList(
                        viewport.firstVisible.coerceIn(0, display.size - 1),
                        (viewport.lastVisible + 1).coerceIn(1, display.size),
                    ),
                ),
            )
        }
    }
    val baselineHalves = remember(display, baseLevel, type) {
        if (type == ChartType.BASELINE) {
            ChartTransforms.baselineSplit(display, baseLevel)
        } else {
            EMPTY_SPLIT
        }
    }
    val stepHeld = remember(display, type) {
        if (type == ChartType.STEP_LINE) ChartTransforms.stepLine(display) else DoubleArray(0)
    }
    val barWidths = remember(display, type) {
        if (type == ChartType.VOLUME_CANDLES) ChartTransforms.volumeWidths(display) else DoubleArray(0)
    }

    // Rebased once and spent three ways: by the comparison lines, by the legend rows that name them
    // and by the reading under the crosshair. Doing it in each would run the same pass over the same
    // arrays and give them a way to disagree about what a comparison is currently reading.
    val rebased = remember(decoration.comparisons, decoration.comparisonBasis, viewport.firstVisible, display) {
        decoration.comparisons.map {
            rebase(it, decoration.comparisonBasis, viewport.firstVisible, display.close)
        }
    }
    val shownRebased = if (hidden.isEmpty()) {
        rebased
    } else {
        rebased.filterIndexed { index, _ -> ChartLegendTarget.Comparison(index) !in hidden }
    }

    /**
     * The three importances as colours, resolved once in composition.
     *
     * [CoineProColors] are composition locals and a draw pass is not composition, so they cannot be
     * read per glyph. Three reads here, held for the frame.
     */
    val eventColours = EventMarkColours(
        high = importanceColour(Importance.HIGH),
        medium = importanceColour(Importance.MEDIUM),
        low = importanceColour(Importance.LOW),
    )

    /** Half a pixel, or nothing at all. See the `conflate` parameter. */
    val conflateGap = if (conflate) CONFLATION_GAP_PX else 0f

    /**
     * Measured axis labels, kept between frames. See [TextWidthCache].
     *
     * Rebuilt whenever the measurer or the density changes, because a layout measured at one
     * density is the wrong size at another and nothing in the key would say so.
     */
    val textCache = remember(measurer, density) { TextWidthCache<TextLayoutResult>() }

    /** The plot rectangle the last draw published, for the overlays stacked on top of it. */
    val frames = remember { arrayOfNulls<PlotFrame>(1) }

    /**
     * The setup's own levels, which the price range has to open far enough to include.
     *
     * Hoisted out of the draw lambda, where it was `decoration.signal?.levels().orEmpty()` — a list
     * built afresh on every frame, and then compared against the cached viewport by value to decide
     * whether the frame's geometry had moved. So the allocation happened sixty times a second and
     * its only consumer was an equality test it could never fail. Keyed on the signal, it is built
     * when the signal changes and compares by identity the rest of the time.
     */
    val signalLevels = remember(decoration.signal) { decoration.signal?.levels().orEmpty() }

    // Kept fresh for the gesture handlers, which start once and then run across recompositions: a
    // block that captured `scaleSide` would go on measuring against the gutter the reader moved
    // away from until something else restarted it.
    val currentSide = rememberUpdatedState(scaleSide)
    val currentAxes = rememberUpdatedState(decoration.showAxes)
    val currentAxisWidth = rememberUpdatedState(axisWidth)
    fun frameOf(canvasWidth: Float): PlotFrame =
        plotFrame(canvasWidth, currentAxisWidth.value, currentSide.value, currentAxes.value)

    /**
     * Where a pointer is resting in the price gutter, and how long the `+` stays after it leaves.
     *
     * Null means the chip is not offered. See [PriceAxisAlertAffordance] for why it lingers rather
     * than vanishing with the finger.
     */
    val alertPointer = remember { mutableStateOf<Float?>(null) }
    val alertHeld = remember { mutableStateOf(false) }
    // Through `snapshotFlow` rather than as effect keys, and that is not a style choice: an effect
    // keyed on the value would read it *in composition*, so a pointer sliding down the gutter would
    // recompose the whole chart to restart a timer.
    LaunchedEffect(Unit) {
        snapshotFlow { alertPointer.value to alertHeld.value }.collectLatest { (y, held) ->
            if (y == null || held) return@collectLatest
            delay(ALERT_LINGER_MILLIS)
            alertPointer.value = null
        }
    }

    // The crosshair, reported outward. One effect rather than a call at each of the five places it
    // is written, so a new gesture cannot forget to tell the other pane — and read through a
    // snapshot flow for the same reason the alert is: reading it in composition would undo the
    // second layer's whole point.
    val emitCrosshair = rememberUpdatedState(onCrosshairMove)
    LaunchedEffect(Unit) {
        snapshotFlow { crosshair }.collect { emitCrosshair.value?.invoke(it) }
    }

    // And the window: saved for a restart and reported outward, on the same three numbers.
    //
    // One flow rather than two keyed effects, and read through a snapshot flow rather than in
    // composition, for the reason on the crosshair above and one more: a drag moves the viewport
    // once per frame, and a `LaunchedEffect` keyed on it is cancelled and relaunched once per
    // frame — on the main thread, inside the frame that is also drawing the chart. The writes are
    // two ints into a bundle and one callback; what cost the frames was the restart, not the
    // work. `distinctUntilChanged` on the three fields keeps a crosshair move or a scale change,
    // which are also viewport copies, from writing or reporting anything.
    val emitViewport = rememberUpdatedState(onViewportChange)
    LaunchedEffect(Unit) {
        snapshotFlow { viewport }
            .distinctUntilChanged { a, b ->
                a.barsPerView == b.barsPerView && a.offset == b.offset && a.priceZoom == b.priceZoom
            }
            .collect { window ->
                savedZoom = window.barsPerView
                savedOffset = window.offset
                savedPriceZoom = window.priceZoom
                emitViewport.value?.invoke(window)
            }
    }

    // A window donated by another pane. Three numbers, not the whole viewport — see the parameter.
    remember(viewportOverride?.barsPerView, viewportOverride?.offset, viewportOverride?.priceZoom) {
        viewportOverride?.let { other ->
            viewport = viewport
                .copy(barsPerView = other.barsPerView, priceZoom = other.priceZoom)
                .atOffset(other.offset)
        }
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!interactive) {
                        Modifier
                    } else {
                        Modifier
                            .pointerInput(display, armed) {
                                // Panning is off while a tool is armed. A one-finger drag on a
                                // chart in drawing mode is a placement, and a chart that scrolls
                                // under the finger at the same time places the point somewhere the
                                // reader was not pointing.
                                if (armed != null) return@pointerInput
                                // Both gestures accumulate what they could not spend.
                                //
                                // Pan and zoom are both quantised — pan to whole bars, zoom to a
                                // whole bar count — and both used to throw the remainder away on
                                // every frame. That is fine at three pixels a bar and completely
                                // broken zoomed in: at thirty pixels a bar a fourteen-pixel drag
                                // rounds to zero bars, so a slow drag moved the chart *not at all*
                                // while a fast one jumped. Same for zoom near the floor, where
                                // `14 / 1.02` rounds back to 14 and a pinch did nothing.
                                //
                                // Keeping the residue across frames makes a slow drag and a fast
                                // one cover the same ground, which is the whole of what "the pan
                                // feels wrong" means.
                                var panResidue = 0f
                                // The fling is not here, and that is still deliberate.
                                //
                                // `detectTransformGestures` offers no release callback and no
                                // velocity — it never returns and reports only per-frame deltas —
                                // and getting one out of it would mean reimplementing multi-touch
                                // transform detection on `awaitEachGesture`, which is a rewrite of
                                // a gesture that works. So the momentum is measured by the
                                // observer below instead: a second handler that consumes nothing,
                                // watches the same pointers on the final pass, and hands the speed
                                // to `KineticScroll` when the last one lifts.
                                detectTransformGestures(
                                    onGesture = { centroid, pan, zoom, _ ->
                                        // Tracking mode suspends panning entirely. The reader has
                                        // asked for a reading of one bar; a chart that slides
                                        // under the crosshair while they look at the legend is
                                        // answering a question about a different bar.
                                        if (tracking) return@detectTransformGestures
                                        // A drag that started on the time axis belongs to the
                                        // gesture that scales time, and letting it pan as well
                                        // would move the chart sideways while compressing it.
                                        val axisTop = timeAxisTop[0]
                                        val onTimeAxis = axisTop > 0f && centroid.y >= axisTop
                                        // The zoom is not here. `detectTransformGestures` reports
                                        // one scalar for the pinch — the ratio of the centroid's
                                        // *radius* — and a single number cannot say whether the
                                        // fingers separated sideways or up and down. That
                                        // distinction is the whole of stage two, so the pinch is
                                        // measured per axis by the observer below and this handler
                                        // is left with the pan.
                                        if (!onTimeAxis && abs(pan.x) > 0f) {
                                            panResidue += pan.x
                                            val width = drawn().barWidth
                                            if (width > 0f) {
                                                val bars = (panResidue / width).toInt()
                                                if (bars != 0) {
                                                    val before = viewport.offset
                                                    viewport = viewport.atOffset(before + bars)
                                                    panResidue -= bars * width
                                                    // Refused travel goes into the band, so a
                                                    // finger dragging into the end of the history
                                                    // meets resistance instead of a dead surface.
                                                    if (viewport.offset == before) {
                                                        stretchEdge(bars * width)
                                                    }
                                                    invalidate(Invalidation.FULL)
                                                }
                                            }
                                        }
                                    },
                                )
                            }
                            .pointerInput(display) {
                                // Stage two of the zoom: a pinch that means one axis, or both,
                                // depending on which way the fingers went.
                                //
                                // ### Why this is a separate handler and not a parameter
                                //
                                // `detectTransformGestures` hands its callback a single `zoom`,
                                // computed from the ratio of the *centroid size* — the mean
                                // distance of the pointers from their centre. That number is a
                                // radius, and a radius has thrown away the direction. Fingers
                                // separating sideways and fingers separating vertically produce
                                // the same scalar, so a gesture built on it can only ever mean
                                // "closer", and the chart magnifies uniformly like a photograph.
                                //
                                // On a desk that is survivable, because the price gutter and the
                                // date strip are both a mouse-drag away. On a phone it is the
                                // difference between a chart and a picture of a chart: the reader
                                // holding the glass in one hand has two fingers and no third
                                // gesture, and "let me see this swing taller without changing how
                                // many bars I can see" is the most common thing they want.
                                //
                                // So the spans are measured per axis here — the mean horizontal
                                // distance from the centroid, and the mean vertical one — and each
                                // ratio drives its own scale. A pinch that widens horizontally and
                                // not vertically changes the bar count and leaves the candles
                                // exactly as tall. A pinch that grows in both, which is what a
                                // reader who simply wants "closer" does, still changes both, so
                                // nothing anybody had learned has been taken away.
                                //
                                // Watched on the Final pass and consuming nothing, for the same
                                // reason the momentum observer below does: the transform gesture
                                // above is a working multi-touch implementation and there is no
                                // reason to rewrite it in order to read two more numbers off the
                                // same pointers.
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var lastX = 0f
                                    var lastY = 0f
                                    var timeResidue = 1f
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Final)
                                        val down = event.changes.count { it.pressed }
                                        if (down == 0) break
                                        // One finger is a pan, and tracking mode is a reading of a
                                        // single bar that a rescale would answer for a different
                                        // one. Either way the spans are forgotten rather than
                                        // carried, so putting a second finger down starts a fresh
                                        // measurement instead of jumping by whatever changed while
                                        // the gesture was not a pinch.
                                        if (down < 2 || tracking) {
                                            lastX = 0f
                                            lastY = 0f
                                            timeResidue = 1f
                                            continue
                                        }
                                        val spanX = event.axisSpan(vertical = false)
                                        val spanY = event.axisSpan(vertical = true)
                                        val floor = PINCH_AXIS_FLOOR_DP.toPx()
                                        // An axis the fingers barely straddle carries no signal —
                                        // two fingers side by side have a vertical span of almost
                                        // nothing, and a ratio of two nearly-zero numbers is noise
                                        // that would make the price scale jitter for the whole
                                        // gesture. Below the floor that axis simply does not move,
                                        // which is also exactly the behaviour a reader pinching
                                        // horizontally is asking for.
                                        if (lastX >= floor && spanX >= floor) {
                                            val ratio = spanX / lastX
                                            if (abs(ratio - 1f) > ZOOM_DEADZONE) {
                                                // Accumulated, because the bar count is a whole
                                                // number: a slow pinch whose every frame rounds
                                                // back to the count it started on would move
                                                // nothing at all while a fast one jumped.
                                                timeResidue = (timeResidue * ratio)
                                                    .coerceIn(MIN_SCALE_RESIDUE, MAX_SCALE_RESIDUE)
                                                val before = viewport.barsPerView
                                                val zoomed = viewport.zoomedBy(timeResidue)
                                                if (zoomed.barsPerView != before) {
                                                    viewport = zoomed
                                                    timeResidue = 1f
                                                    invalidate(Invalidation.FULL)
                                                }
                                            }
                                        }
                                        if (lastY >= floor && spanY >= floor) {
                                            val ratio = spanY / lastY
                                            if (abs(ratio - 1f) > ZOOM_DEADZONE) {
                                                // Inverted, because `priceZoom` above one *widens*
                                                // the range and so flattens the candles: fingers
                                                // moving apart vertically have to shrink it.
                                                // Bounded by the viewport at a quarter and eight,
                                                // and a double tap on the gutter puts it back.
                                                viewport = viewport.priceZoomedBy(1f / ratio)
                                                invalidate(Invalidation.FULL)
                                            }
                                        }
                                        lastX = spanX
                                        lastY = spanY
                                    }
                                }
                            }
                            .pointerInput(display, armed) {
                                // Momentum, and the one thing that had to be built for it: a
                                // release with a speed attached.
                                //
                                // This handler consumes nothing and decides nothing. It watches the
                                // same pointers the transform gesture above is already handling, on
                                // the *final* pass — after every other handler has had the event and
                                // taken what it wanted — feeds their positions to a velocity
                                // tracker, and when the last one lifts, hands the horizontal speed
                                // to `KineticScroll`. Doing it this way rather than rewriting the
                                // transform gesture on `awaitEachGesture` keeps a working
                                // multi-touch implementation working.
                                //
                                // Two refusals, and both matter. A gesture that ever had a second
                                // pointer down was a pinch, and a pinch does not fling — the
                                // fingers separating produces a large centroid velocity that means
                                // nothing about where the reader wanted the chart to end up. And a
                                // gesture from anything but a touch pointer does not fling either;
                                // see `KineticScroll` for why a trackpad decelerates itself.
                                if (armed != null) return@pointerInput
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    // A finger on the glass always beats momentum already running.
                                    kinetic.stop()
                                    flinging = false
                                    val tracker = VelocityTracker()
                                    tracker.addPosition(down.uptimeMillis, down.position)
                                    var flingable = down.type == PointerType.Touch
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Final)
                                        if (event.changes.count { it.pressed } > 1) flingable = false
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change != null && change.pressed) {
                                            if (change.type != PointerType.Touch) flingable = false
                                            tracker.addPosition(change.uptimeMillis, change.position)
                                        }
                                        if (event.changes.none { it.pressed }) break
                                    }
                                    // Never off the plot: a flick on the time axis is a zoom and a
                                    // flick in the gutter is a price stretch, and neither of them
                                    // should coast.
                                    val axisTop = timeAxisTop[0]
                                    val onPlot = (axisTop <= 0f || down.position.y < axisTop) &&
                                        frameOf(size.width.toFloat()).onPlot(down.position.x)
                                    if (flingable && onPlot && !tracking) {
                                        kinetic.start(tracker.calculateVelocity().x)
                                        flinging = kinetic.isRunning
                                    }
                                    // The band goes back on the lift, whether or not a fling
                                    // followed. A gesture that dragged into the end of the history
                                    // and simply stopped there must not leave the picture held off
                                    // its edge until the reader touches the glass again; and where
                                    // a fling *did* start, its own loop releases the band again at
                                    // the end, which is harmless because the release is a no-op on
                                    // a band already at rest.
                                    if (!flinging) releaseEdge()
                                }
                            }
                            .pointerInput(Unit) {
                                // A second finger holds the magnet on — item 38.
                                //
                                // The latch and its rule live in `DrawingActions.holdMagnet` and
                                // `commit` drops it, so a hold cannot outlive the drawing it was
                                // held for. What was missing was the gesture, and without it the
                                // whole momentary magnet was unreachable: `effectiveMagnetMode`
                                // read a flag nothing outside a test ever set.
                                //
                                // A second finger and not a long press, because a long press is
                                // already the eraser's "take the whole drawing" and a tool armed
                                // mid-placement has no spare press left. Resting the other thumb on
                                // the glass is what a reader does on a terminal to say «snap this
                                // one», and it composes with the tap that follows instead of
                                // replacing it.
                                //
                                // An observer in the same shape as the two below: the final pass,
                                // consuming nothing, deciding nothing. Every other handler has
                                // already had the event, so the pinch that a second finger usually
                                // means goes on zooming exactly as it did — this only reads how
                                // many pointers are down and latches a flag. Keyed on `Unit` and
                                // reading the state through `rememberUpdatedState`, because a
                                // handler keyed on the state it emits restarts itself on its own
                                // output and loses the gesture halfway through.
                                awaitPointerEventScope {
                                    var held = false
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Final)
                                        val state = currentDrawing.value
                                        val emit = currentOnDrawing.value
                                        // Only while something is actually being placed. A second
                                        // finger on a chart with no tool armed is a pinch and
                                        // nothing else, and latching there would leave the magnet
                                        // held for the next drawing the reader starts.
                                        val placing = state?.tool != null
                                        val second = event.changes.count { it.pressed } > 1
                                        val wanted = placing && second
                                        if (wanted == held) continue
                                        held = wanted
                                        if (state == null || emit == null) continue
                                        emit(
                                            if (wanted) {
                                                DrawingActions.holdMagnet(state)
                                            } else {
                                                DrawingActions.releaseMagnet(state)
                                            },
                                        )
                                    }
                                }
                            }
                            .pointerInput(onRequestAlertAt != null) {
                                // The `+` on the price axis: a watcher, not a gesture.
                                //
                                // The same shape as the fling observer above and for the same
                                // reason. It consumes nothing and decides nothing, and runs on the
                                // final pass after every other handler has had the event — so the
                                // drag that stretches the gutter and the long press that opens its
                                // menu both go on working exactly as they did. All it adds is
                                // *where a pointer is resting in the gutter*, which is the one
                                // thing no existing handler reports and the thing an alert needs.
                                //
                                // The strip is the gutter proper, with none of the twelve pixels of
                                // reach the drag gets: a pan that ends near the right edge must not
                                // summon a button over the reader's chart.
                                if (onRequestAlertAt == null) return@pointerInput
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Final)
                                        val frame = frameOf(size.width.toFloat())
                                        val position = event.changes.firstOrNull()?.position
                                        val inside = position != null &&
                                            event.type != PointerEventType.Exit &&
                                            frame.inGutter(position.x, 0f) &&
                                            position.y in 0f..size.height.toFloat()
                                        if (inside) {
                                            alertPointer.value = position.y
                                            alertHeld.value = true
                                        } else {
                                            alertHeld.value = false
                                        }
                                    }
                                }
                            }
                            .pointerInput(display, armed, drawing, onDrawing) {
                                if (onDrawing == null || drawing == null) return@pointerInput
                                // Freehand is the one tool family that needs a drag: the stroke is
                                // the gesture. Everything else is N taps, which is the only thing
                                // that works for a five-point pattern on a phone — a five-point
                                // drag would have to be one continuous gesture with four pauses.
                                if (armed?.points != 0) return@pointerInput
                                val samples = mutableListOf<ChartPoint>()
                                detectDragGestures(
                                    onDragStart = { position ->
                                        samples.clear()
                                        val plot = frameOf(size.width.toFloat()).toPlot(position)
                                        lastView[0]?.let { samples += it.chartPointAt(plot, display, drawing.magnetMode) }
                                    },
                                    onDrag = { change, _ ->
                                        val plot = frameOf(size.width.toFloat()).toPlot(change.position)
                                        lastView[0]?.let { samples += it.chartPointAt(plot, display, drawing.magnetMode) }
                                    },
                                    onDragEnd = { onDrawing(DrawingActions.stroke(drawing, samples.toList())) },
                                    onDragCancel = { samples.clear() },
                                )
                            }
                            .pointerInput(display, drawing, onDrawing, tolerancePx) {
                                // Editing a drawing after it has been placed.
                                //
                                // `DrawingActions.movePoint` and `moveBy` have existed since the
                                // drawing engine was written and had **zero callers** — the white
                                // handles were painted on every selected drawing and were pure
                                // decoration. A trend line could be placed and then never adjusted,
                                // which on a chart is most of what drawing a trend line is.
                                //
                                // Only runs with nothing armed and something selected, so it
                                // cannot steal the drag that places a freehand stroke or the one
                                // that pans.
                                var handle = -1
                                var origin: ChartPoint? = null
                                detectDragGestures(
                                    onDragStart = { position ->
                                        val state = drawing ?: return@detectDragGestures
                                        if (state.tool != null) return@detectDragGestures
                                        val id = state.selectedId ?: return@detectDragGestures
                                        val view = lastView[0] ?: return@detectDragGestures
                                        val target = state.drawings.firstOrNull { it.id == id }
                                            ?: return@detectDragGestures
                                        // In line-movement mode the whole object moves and the
                                        // handles are ignored: the gesture is "put this line at
                                        // that price", and grabbing an endpoint would rotate the
                                        // line instead of translating it.
                                        val plot = frameOf(size.width.toFloat()).toPlot(position)
                                        handle = if (lineMove) -1 else handleIndexAt(target, view, plot, tolerancePx)
                                        // Published so the draw pass can grow the one being held.
                                        // Only a handle: dragging the whole object moves everything
                                        // under the finger and there is no single anchor to mark.
                                        grabbedHandle = handle
                                        origin = view.chartPointAt(plot, display, state.magnetMode)
                                    },
                                    onDrag = { change, _ ->
                                        val state = drawing ?: return@detectDragGestures
                                        val emit = onDrawing ?: return@detectDragGestures
                                        val id = state.selectedId ?: return@detectDragGestures
                                        val view = lastView[0] ?: return@detectDragGestures
                                        val from = origin ?: return@detectDragGestures
                                        val to = view.chartPointAt(
                                            frameOf(size.width.toFloat()).toPlot(change.position),
                                            display,
                                            state.magnetMode,
                                        )
                                        change.consume()
                                        if (handle >= 0) {
                                            // The other end of a two-point object is what the
                                            // moving one is held straight against — item 48. Only
                                            // for a pair: a five-point pattern has no "other end",
                                            // and `constrain` refuses those anyway.
                                            val opposite = state.drawings
                                                .firstOrNull { it.id == id }
                                                ?.takeIf { it.points.size == 2 && state.constrainAngle }
                                                ?.points?.get(1 - handle)
                                            val end = if (opposite == null) {
                                                to
                                            } else {
                                                DrawingActions.constrain(
                                                    toolId = state.drawings.first { it.id == id }.toolId,
                                                    from = opposite,
                                                    to = to,
                                                    view = view,
                                                )
                                            }
                                            emit(DrawingActions.movePoint(state, id, handle, end))
                                        } else {
                                            // A delta in *chart* space, not pixels, so a shape
                                            // dragged while zoomed lands where the finger did.
                                            emit(
                                                DrawingActions.moveBy(
                                                    state = state,
                                                    id = id,
                                                    // Line-movement mode spends the whole gesture
                                                    // on price. See `lineMove`.
                                                    deltaTime = if (lineMove) 0L else to.time - from.time,
                                                    deltaPrice = to.price - from.price,
                                                ),
                                            )
                                        }
                                        origin = to
                                    },
                                    onDragEnd = {
                                        // Held for one adjustment, like the magnet and like the
                                        // placing tap above. See the long-press branch.
                                        if (handle >= 0) {
                                            drawing?.takeIf { it.constrainAngle }?.let { held ->
                                                onDrawing?.invoke(DrawingActions.setConstrainAngle(held, false))
                                            }
                                        }
                                        handle = -1
                                        grabbedHandle = -1
                                        origin = null
                                    },
                                    onDragCancel = {
                                        handle = -1
                                        grabbedHandle = -1
                                        origin = null
                                    },
                                )
                            }
                            .pointerInput(display, onScalePanes) {
                                // The divider between the candles and the first indicator pane:
                                // drag it to give the pane more or less of the canvas.
                                //
                                // Confined to a band around the boundary so it cannot steal the
                                // pan, and only when there is a pane to resize — on a chart with
                                // no oscillator on it there is no divider and the band does not
                                // exist. `paneTop` is written by the draw pass, which is the only
                                // thing that knows where the boundary actually landed.
                                if (onScalePanes == null) return@pointerInput
                                var onDivider = false
                                detectVerticalDragGestures(
                                    onDragStart = { position ->
                                        val boundary = paneTop[0]
                                        // The band is `separatorHitRect`'s and not a number
                                        // invented here: nine density-independent pixels straddling
                                        // a one-pixel line. It is the difference between a divider
                                        // that "just works" and one a reader concludes is not
                                        // draggable, and it never appears in a screenshot.
                                        onDivider = boundary > 0f &&
                                            position.y in separatorHitRect(boundary, density.density)
                                    },
                                    onDragEnd = { onDivider = false },
                                    onDragCancel = { onDivider = false },
                                ) { change, dragAmount ->
                                    if (!onDivider) return@detectVerticalDragGestures
                                    change.consume()
                                    val boundary = paneTop[0]
                                    if (boundary <= 0f) return@detectVerticalDragGestures
                                    // Dragging *up* grows the panes, because the divider moves up
                                    // and the space below it is theirs.
                                    onScalePanes(1f - dragAmount / boundary * DIVIDER_SENSITIVITY)
                                }
                            }
                            .pointerInput(display, axisWidth) {
                                // The price gutter: drag it to stretch or compress the scale.
                                //
                                // The gesture every terminal uses, and the reason it is worth
                                // having is that the auto-fit range is right for reading a price
                                // and wrong for reading a *shape*. A market that has moved half a
                                // percent all week fills the plot with noise; one that gapped ten
                                // percent on Monday spends the rest of it as a flat line.
                                //
                                // Confined to the gutter — the strip the axis labels are drawn in,
                                // widened to a thumb — so it cannot steal a pan from the plot. In
                                // a right-to-left layout the gutter is still on the right: the
                                // chart is drawn left-to-right regardless of the reading
                                // direction, because no trader on earth reads the newest bar on
                                // the left.
                                detectVerticalDragGestures(
                                    onDragStart = { position ->
                                        gutterDrag = frameOf(size.width.toFloat())
                                            .inGutter(position.x, GUTTER_REACH_DP.toPx())
                                    },
                                    onDragEnd = { gutterDrag = false },
                                    onDragCancel = { gutterDrag = false },
                                ) { change, dragAmount ->
                                    if (!gutterDrag) return@detectVerticalDragGestures
                                    change.consume()
                                    val height = drawn().plotHeight
                                    if (height <= 0f) return@detectVerticalDragGestures
                                    // Dragging *down* compresses, which is the convention
                                    // everywhere: the finger pushes the extremes toward the middle.
                                    viewport = viewport.priceZoomedBy(1f + dragAmount / height * GUTTER_SENSITIVITY)
                                    invalidate(Invalidation.FULL)
                                }
                            }
                            .pointerInput(display) {
                                // Stage three: a drag along the dates scales time and leaves the
                                // price alone.
                                //
                                // The counterpart of the gutter drag above, and the reason the pair
                                // exists is that a pinch answers "closer" and neither axis
                                // separately. A reader comparing the shape of two swings wants more
                                // bars without the candles changing height; a reader reading a
                                // consolidation wants the opposite. Dragging right pulls the bars
                                // apart and dragging left packs them in, which is the direction
                                // every terminal uses and is what the finger is literally doing to
                                // the axis under it.
                                //
                                // Confined to the strip the dates are drawn in, and the pan gesture
                                // above refuses any drag that started there, so the two cannot both
                                // claim the same finger.
                                var onTimeAxis = false
                                var scaleResidue = 1f
                                detectHorizontalDragGestures(
                                    onDragStart = { position ->
                                        val axisTop = timeAxisTop[0]
                                        onTimeAxis = axisTop > 0f && position.y >= axisTop
                                        scaleResidue = 1f
                                    },
                                    onDragEnd = { onTimeAxis = false },
                                    onDragCancel = { onTimeAxis = false },
                                ) { change, dragAmount ->
                                    if (!onTimeAxis) return@detectHorizontalDragGestures
                                    change.consume()
                                    val width = drawn().plotWidth
                                    if (width <= 0f) return@detectHorizontalDragGestures
                                    // Accumulated across frames for the same reason the pinch is:
                                    // the bar count is a whole number, and a slow drag whose every
                                    // frame rounds back to the count it started on moves nothing at
                                    // all while a fast one jumps.
                                    scaleResidue = (scaleResidue * (1f + dragAmount / width * TIME_AXIS_SENSITIVITY))
                                        .coerceIn(MIN_SCALE_RESIDUE, MAX_SCALE_RESIDUE)
                                    val before = viewport.barsPerView
                                    val zoomed = viewport.zoomedBy(scaleResidue)
                                    if (zoomed.barsPerView != before) {
                                        viewport = zoomed
                                        scaleResidue = 1f
                                        invalidate(Invalidation.FULL)
                                    }
                                }
                            }
                            .pointerInput(display, drawing, onDrawing, eraser, tolerancePx) {
                                // A long press means one of five things, decided by where it lands
                                // and by what is armed.
                                //
                                // With the eraser armed it takes out whatever is under the finger,
                                // whole — the deliberate, destructive half of the eraser, kept
                                // behind a press so that a tap can safely mean "just this leg".
                                // Otherwise: mid-placement, or on a handle of the selected drawing,
                                // it latches the angle constraint — see `constrainableAt` and the
                                // branch below; in the gutter it opens the axis menu; on a level it
                                // offers an alert at that level's price; anywhere else it enters
                                // tracking mode — the crosshair appears, follows the finger, and
                                // **stays** when the finger lifts, until a tap dismisses it. A
                                // crosshair that dies with the gesture is a crosshair a reader has
                                // to keep their thumb over the answer to read, which on a phone is
                                // most of the answer.
                                //
                                // The position is resolved against `lastView`, not against the
                                // viewport this composable holds: only the draw pass knows the plot
                                // size, and an unsized viewport reports every touch as the first
                                // bar at the bottom of the range.
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { position ->
                                        val view = lastView[0]
                                        val frame = frameOf(size.width.toFloat())
                                        val plot = frame.toPlot(position)
                                        val inGutter = frame.inGutter(position.x, GUTTER_REACH_DP.toPx())
                                        val alert = currentAlert.value
                                        val axisMenu = currentAxisMenu.value
                                        val erasing = eraser && drawing != null &&
                                            onDrawing != null && view != null && !inGutter
                                        // Where a long press means «hold it straight» instead —
                                        // item 48. See `constrainableAt` for the two windows, and
                                        // the note on the branch below for why the long press is
                                        // the gesture at all.
                                        val constraining = !erasing && !inGutter && view != null &&
                                            drawing != null && onDrawing != null &&
                                            constrainableAt(drawing, view, plot, tolerancePx)
                                        val level = view?.let { placed ->
                                            currentLevels.value.firstOrNull {
                                                abs(placed.yOf(it.price) - plot.y) <= tolerancePx
                                            }
                                        }
                                        when {
                                            erasing -> eraseAt(
                                                state = drawing!!,
                                                x = plot.x,
                                                y = plot.y,
                                                view = view!!,
                                                tolerancePx = tolerancePx,
                                                whole = true,
                                            )?.let { next -> onDrawing?.invoke(next) }
                                            // The angle constraint, latched — item 48.
                                            //
                                            // A long press and not a second finger, because the
                                            // second finger is already the momentary magnet's
                                            // (item 38) and a chart on which one extra thumb means
                                            // two different things is a chart on which it means
                                            // neither. The long press is free in exactly the two
                                            // windows `constrainableAt` names: mid-placement, where
                                            // dropping a crosshair the next tap has to dismiss was
                                            // never what a reader wanted anyway, and on a handle of
                                            // the selected drawing, which is a target nobody presses
                                            // to read a price off. Everywhere else — and that is
                                            // almost everywhere — the long press still enters
                                            // tracking mode exactly as it did.
                                            //
                                            // A latch rather than a hold, because placement is taps
                                            // and not a drag: a constraint that died when the
                                            // finger came up would be released before the tap it
                                            // was held for. The tap that spends it clears it, and
                                            // pressing again clears it early.
                                            constraining -> onDrawing?.invoke(
                                                DrawingActions.setConstrainAngle(
                                                    drawing!!,
                                                    !drawing.constrainAngle,
                                                ),
                                            )
                                            inGutter && axisMenu != null -> axisMenu()
                                            !inGutter && level != null && alert != null ->
                                                alert(level.price)
                                            view != null -> {
                                                tracking = true
                                                crosshair = view.crosshairAt(plot)
                                                invalidate(Invalidation.CURSOR)
                                            }
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        if (!tracking) return@detectDragGesturesAfterLongPress
                                        val plot = frameOf(size.width.toFloat()).toPlot(change.position)
                                        lastView[0]?.let { crosshair = it.crosshairAt(plot) }
                                        invalidate(Invalidation.CURSOR)
                                    },
                                    // Nothing on release: the crosshair is the reading, and the
                                    // reading is what the reader long-pressed to get.
                                    onDragEnd = {},
                                    onDragCancel = {},
                                )
                            }
                            .pointerInput(display, armed, drawing, onDrawing, eraser, tolerancePx) {
                                detectTapGestures(
                                    onDoubleTap = { position ->
                                        // A double tap means the first of these that applies.
                                        //
                                        // With a path or a polyline part-drawn it ends the shape,
                                        // and it has to come first: those two tools are the only
                                        // ones with no tap count to run out of, so without a way to
                                        // say "that is the last corner" they can be started and
                                        // never finished. That is the gesture every terminal uses
                                        // and the reader will try it whether or not it is wired.
                                        //
                                        // With a drawing selected and no tool armed it arms
                                        // line-movement mode rather than resetting anything — the
                                        // reader is working on that object, and "put the view back"
                                        // is not what they meant by tapping on it twice.
                                        //
                                        // Otherwise: in the gutter it resets the price scale; on
                                        // the plot it returns to the live edge. Two double-taps,
                                        // each one the "put this back" for the axis it lands on.
                                        val finishing = armed != null &&
                                            DrawingActions.isVariablePoint(armed.id) &&
                                            drawing?.pending?.isNotEmpty() == true
                                        val editing = drawing != null &&
                                            drawing.selectedId != null &&
                                            drawing.tool == null
                                        when {
                                            finishing -> drawing?.let { pendingShape ->
                                                onDrawing?.invoke(DrawingActions.finish(pendingShape))
                                            }
                                            editing -> lineMove = !lineMove
                                            else -> {
                                                viewport = if (
                                                    frameOf(size.width.toFloat())
                                                        .inGutter(position.x, GUTTER_REACH_DP.toPx())
                                                ) {
                                                    viewport.autoPriceScale()
                                                } else {
                                                    // `atRest`, not `atOffset(0)`: "put the view
                                                    // back" has to land on the position the chart
                                                    // opens in, air at the live edge included.
                                                    // Returning to a glued edge would make the
                                                    // reset produce a picture the reader has never
                                                    // seen the chart in.
                                                    viewport.atRest()
                                                }
                                                invalidate(Invalidation.FULL)
                                            }
                                        }
                                    },
                                    onTap = { position ->
                                        // A single tap is the way out of both modes, and it takes
                                        // priority over everything else a tap can mean. A reader in
                                        // tracking mode who taps is asking for the crosshair to go
                                        // away, not to select the drawing that happened to be under
                                        // their finger — and certainly not to place a point they
                                        // cannot see under the crosshair they are reading.
                                        if (tracking || lineMove) {
                                            tracking = false
                                            lineMove = false
                                            crosshair = null
                                            invalidate(Invalidation.CURSOR)
                                            return@detectTapGestures
                                        }
                                        // The trade ring, before anything on the plot: it is a
                                        // button drawn over the bars, and a tap on it must not also
                                        // place a point or select the drawing under it.
                                        val ring = tradeRing
                                        val onTrade = currentTradeRing.value
                                        if (ring[2] > 0f && onTrade != null) {
                                            val reach = ring[2] + TRADE_RING_REACH_DP.toPx()
                                            val dx = position.x - ring[0]
                                            val dy = position.y - ring[1]
                                            if (dx * dx + dy * dy <= reach * reach) {
                                                onTrade()
                                                return@detectTapGestures
                                            }
                                        }
                                        // An event glyph, and only in the strip the glyphs are
                                        // drawn in. Tested before anything else a tap can mean and
                                        // confined to those few points of height, so a tap on the
                                        // plot goes on meaning exactly what it meant.
                                        val axisTop = timeAxisTop[0]
                                        val onEvents = currentEventMark.value
                                        if (axisTop > 0f && position.y >= axisTop && onEvents != null) {
                                            val placed = lastView[0]
                                            val hit = placed?.let { seen ->
                                                ChartEvents.markAt(
                                                    marks = currentEvents.value,
                                                    xPixels = frameOf(size.width.toFloat()).toPlot(position).x,
                                                    radiusPixels = EventGlyphs.TOUCH_RADIUS_DP.dp.toPx(),
                                                    xOf = seen::xOf,
                                                )
                                            }
                                            if (hit != null) {
                                                onEvents(hit)
                                                return@detectTapGestures
                                            }
                                        }
                                        val state = drawing ?: return@detectTapGestures
                                        val emit = onDrawing ?: return@detectTapGestures
                                        val view = lastView[0] ?: return@detectTapGestures
                                        val plot = frameOf(size.width.toFloat()).toPlot(position)
                                        // The eraser takes the tap whole. It is not a tool that
                                        // places anything, so nothing below this would be right.
                                        if (eraser) {
                                            eraseAt(
                                                state = state,
                                                x = plot.x,
                                                y = plot.y,
                                                view = view,
                                                tolerancePx = tolerancePx,
                                                whole = false,
                                            )?.let(emit)
                                            return@detectTapGestures
                                        }
                                        // Tapping the first anchor again closes a path or a
                                        // polyline. Measured in pixels, because "on the anchor" is
                                        // a finger's width and a finger is not a number of bars.
                                        if (closesPendingShape(state, plot.x, plot.y, view, tolerancePx)) {
                                            emit(DrawingActions.closeShape(state))
                                            return@detectTapGestures
                                        }
                                        // With nothing armed the tap is a selection, so the hit
                                        // test runs; with a tool armed it is a placement, and
                                        // running the hit test would let a tap that happens to
                                        // land on an old drawing silently select instead of place.
                                        val hit = if (state.tool == null) {
                                            DrawingHitTest.at(
                                                drawings = state.drawings,
                                                x = plot.x,
                                                y = plot.y,
                                                view = view,
                                                tolerancePx = tolerancePx,
                                            )?.id
                                        } else {
                                            null
                                        }
                                        // `tapSnapped` rather than `tap`, and the point handed over
                                        // unsnapped: the magnet has to run *inside* the action, or
                                        // the channel it chose is thrown away on the way in and the
                                        // binding this whole arrangement exists for is never
                                        // written. See `chartPointAt`.
                                        //
                                        // The held constraint bites here, on the point on its way
                                        // in — item 48. Placement on a phone is N taps and not a
                                        // drag, so "the moving end" of a two-point tool is whatever
                                        // the second tap lands on, and this is the only place it
                                        // passes through. Constrained *before* the magnet rather
                                        // than after, because the magnet's job is to choose a
                                        // channel for the point it is given: snapping first and
                                        // then rotating the result would record a binding to a low
                                        // the point no longer sits on.
                                        val constrained = if (state.constrainAngle) {
                                            state.tool?.let { armedTool ->
                                                state.pending.lastOrNull()?.let { from ->
                                                    DrawingActions.constrain(
                                                        toolId = armedTool.id,
                                                        from = from,
                                                        to = view.rawChartPointAt(plot),
                                                        view = view,
                                                    )
                                                }
                                            }
                                        } else {
                                            null
                                        }
                                        // The snap and the tap, in the open rather than through
                                        // `tapSnapped`, because the snap's answer is wanted twice:
                                        // once as the point to place, once to say whether the
                                        // magnet did anything, which is what the haptic is for.
                                        val snapped = DrawingActions.snap(
                                            point = constrained ?: view.rawChartPointAt(plot),
                                            series = display,
                                            mode = state.effectiveMagnetMode,
                                        )
                                        if (snapped.channel != null) onSnap?.invoke()
                                        val placed = DrawingActions.tap(
                                            state,
                                            snapped.point,
                                            hit,
                                            snapped.channel,
                                        )
                                        // Held for one point, exactly as the magnet is: a latch
                                        // nothing drops is a latch the reader turned on by accident
                                        // and cannot find the switch for. `commit` releases the
                                        // magnet and does not know about this one, so it is
                                        // released here — on the tap that spent it, whether or not
                                        // that tap finished the shape.
                                        emit(
                                            if (constrained != null) {
                                                DrawingActions.setConstrainAngle(placed, false)
                                            } else {
                                                placed
                                            },
                                        )
                                    },
                                )
                            }
                    },
                ),
        ) {
            // The ground, and only when a template asked for one. A Compose `Canvas` is transparent
            // and every chart in this app has always drawn onto whatever composable sits behind it;
            // filling unconditionally would change the look of every card and list row that embeds a
            // chart. With a template in force the fill is not optional — it is what makes the axis
            // gutters and the empty state agree with the plot instead of leaving the theme's colour
            // in two strips down the edges.
            if (decoration.colours != null) {
                drawRect(color = palette.stage, size = size)
            }
            // Where the plot sits once the gutters have been taken off it. One rectangle, computed
            // here and published for the gestures and the overlays, so that nothing anywhere else
            // measures the canvas edge for itself — which is how `ScaleSide` came to be stored and
            // never read.
            val frame = plotFrame(size.width, axisWidth, scaleSide, decoration.showAxes)
            frames[0] = frame
            val plotWidth = frame.width
            val timeAxis = if (decoration.showAxes && decoration.showTimeAxis) timeHeight else 0f
            // Panes eat into the price's height, never into each other or the axis. Clamped in
            // total, because four oscillators at 18% each would leave the candles a sliver — and
            // the candles are what the reader came for.
            // The panes' own request, scaled by whatever the reader dragged, and still capped at
            // `PANE_BUDGET`. The cap is what stops the candles — which are the subject — being
            // squeezed out by three oscillators; the scale is what lets somebody reading
            // divergence give the RSI real height without switching the others off.
            //
            // Counted over the *shown* panes, so hiding one from the legend gives its height back
            // to the candles rather than leaving a gap where it used to be.
            val paneRatio = min(
                PANE_BUDGET,
                shown.panes.sumOf { it.heightRatio.toDouble() }.toFloat() *
                    decoration.paneScale.coerceIn(MIN_PANE_SCALE, MAX_PANE_SCALE),
            )
            val available = max(0f, size.height - timeAxis)
            val paneHeight = if (shown.panes.isEmpty()) 0f else available * paneRatio
            val plotHeight = max(0f, available - paneHeight)
            // Published for the divider gesture. See `paneTop`.
            paneTop[0] = if (paneHeight > 0f) plotHeight else 0f
            // And for the gesture that scales time. See `timeAxisTop`.
            timeAxisTop[0] = if (timeAxis > 0f) plotHeight + paneHeight else 0f

            // The dirty level, consumed. Anything that arrives after this point has to raise it
            // again for the next frame, which is what makes a stale level impossible.
            val dirtyLevel = dirty[0]
            dirty[0] = Invalidation.NONE

            val candidate = viewport
                .sized(plotWidth, plotHeight)
                .copy(includedPrices = signalLevels)
            // The whole of what [Invalidation] buys. On a cursor-level change the previous
            // viewport *instance* is kept rather than an equal new one, so its lazily-computed
            // price range — a walk over every visible bar — and the tick ladder derived from it are
            // not recomputed. Equality is the guard rather than the level: a level that says
            // "cursor" while the geometry has actually moved would draw the last frame's scale
            // under this frame's bars.
            val cached = scaleCache.view
            val settled =
                if (dirtyLevel <= Invalidation.CURSOR && cached == candidate) cached else candidate
            val ticks = if (settled === cached) {
                scaleCache.ticks ?: priceTicks(settled, density.density)
            } else {
                priceTicks(settled, density.density)
            }
            // The time ladder is cached beside the price one and for the same reason: it walks every
            // visible bar asking what calendar boundary it opens, and a crosshair moving must not
            // pay for that. Both are keyed on the viewport *instance*, so the pair can only ever be
            // reused for the frame they were computed on.
            val timeTicks = if (settled === cached) {
                scaleCache.timeTicks ?: timeAxisTicks(settled, type, zone, timeLabelGapPx)
            } else {
                timeAxisTicks(settled, type, zone, timeLabelGapPx)
            }
            scaleCache.view = settled
            scaleCache.ticks = ticks
            scaleCache.timeTicks = timeTicks

            // The rubber band, and the one place it enters the picture.
            //
            // The cache above is keyed on the settled viewport, without the band, so the frames a
            // reader spends *not* at the edge — which is nearly all of them — reuse their price
            // range and tick ladder exactly as before. Neither ladder depends on the shift anyway:
            // the price ticks are prices and the time ticks are bar indices, and both are placed
            // through `xOf`, which is where the shift is applied.
            val pull = edgePull.value
            val view = if (pull == 0f) settled else settled.copy(pixelShift = pull)
            lastView[0] = view

            // Everything below is in *plot* space: x zero is the plot's left edge, which is where
            // `ChartViewport.xOf` has always put the first bar. The gutters are painted at negative
            // x or past `plotWidth`, and the gestures come back the other way through
            // `PlotFrame.toPlot`.
            translate(left = frame.left) {
                if (view.visibleCount == 0) {
                    // Nothing to draw, and the axis is *cleared* rather than left holding the last
                    // series' numbers. A price scale that keeps its labels after a failed load is
                    // the most dangerous thing this file could render: it says the market is at a
                    // price nobody has quoted.
                    if (decoration.showAxes) {
                        if (frame.rightGutter > 0f) drawEmptyAxis(plotWidth, frame.rightGutter, palette, measurer)
                        if (frame.leftGutter > 0f) drawEmptyAxis(-frame.leftGutter, frame.leftGutter, palette, measurer)
                    }
                    return@Canvas
                }

                // Candle geometry, resolved once for the frame. Everything that draws a bar-shaped
                // thing — candles, OHLC ticks, volume, a histogram — takes its width from here, so
                // the four cannot drift apart by a pixel and make the chart look mis-registered.
                val ratio = density.density
                val spacing = if (ratio > 0f) view.barWidth / ratio else view.barWidth
                val body = optimalBarWidth(spacing, ratio)
                val metrics = CandleMetrics(
                    body = body,
                    wick = wickWidth(body, spacing, ratio),
                    border = borderWidth(body, ratio),
                    outlined = drawBorder(body, borderWidth(body, ratio)),
                )

                // Which type is going to be *drawn*, which is not always the one that was picked.
                // A footprint and a profile are tables of numbers inside a bar: without volume they
                // would be a grid of zeros, and below `FOOTPRINT_MIN_SLOT_DP` the numbers cannot be
                // printed at all and what is left is grey mush a reader cannot tell from a fault.
                // Both fall back to candles, which is the honest picture of the same bars.
                val drawnType = when {
                    type == ChartType.FOOTPRINT &&
                        (!view.series.hasVolume || view.barWidth < FOOTPRINT_MIN_SLOT_DP.toPx()) ->
                        ChartType.CANDLES
                    type == ChartType.TPO && !view.series.hasVolume -> ChartType.CANDLES
                    else -> type
                }

                if (decoration.showAxes) drawGrid(view, plotWidth, palette, ticks, timeTicks)
                // The setup goes *under* the price. It is context for the bars, and drawn over them
                // it tints every candle it covers — which on a full-height risk band is most of them.
                decoration.signal?.let { drawSignal(view, it, palette, measurer) }
                // Volume sits in the foot of the price pane rather than in a band of its own — the
                // way every terminal draws it. A separate band cost a fifth of the canvas to say
                // something the reader glances at, and on a chart with three oscillators the volume
                // bars ended up taller than the candles above them.
                //
                // Never under a footprint or a profile: those two already draw volume per price row
                // inside the bar, and a second reading of the same number behind them is noise.
                // And nothing at all when the reader has switched the price off from the legend.
                // That row's eye has to mean what the others' mean — a toggle that dims its own
                // label and leaves the candles standing is the defect, not the feature. The axes,
                // the grid and the studies stay, which is the point of hiding it: reading two
                // oscillators against each other without the bars in the way.
                val priceShown = ChartLegendTarget.Series !in hidden
                val volumeBand = priceShown && decoration.showVolume && series.hasVolume &&
                    drawnType != ChartType.FOOTPRINT && drawnType != ChartType.TPO
                if (volumeBand) {
                    clipRect(0f, 0f, plotWidth, plotHeight) {
                        drawVolume(view, plotHeight, palette, metrics.body)
                    }
                }
                if (priceShown) clipRect(0f, 0f, plotWidth, plotHeight) {
                    when {
                        drawnType == ChartType.BASELINE -> drawBaseline(
                            view = view,
                            palette = palette,
                            base = baseLevel,
                            above = baselineHalves.first,
                            below = baselineHalves.second,
                            conflateGap = conflateGap,
                        )
                        drawnType == ChartType.HLC_AREA -> drawHlcArea(view, palette, conflateGap)
                        drawnType == ChartType.STEP_LINE -> drawStepLine(view, palette, stepHeld, conflateGap)
                        drawnType == ChartType.LINE_MARKERS -> drawLineMarkers(view, palette, conflateGap)
                        drawnType == ChartType.VOLUME_CANDLES ->
                            drawVolumeCandles(view, palette, metrics, barWidths)
                        drawnType == ChartType.FOOTPRINT -> drawFootprint(
                            view = view,
                            palette = palette,
                            rows = footprintRows(view, typeConfig),
                            measurer = measurer,
                            cache = textCache,
                        )
                        drawnType == ChartType.TPO -> drawTpo(
                            view = view,
                            palette = palette,
                            rows = legibleRows(
                                requested = typeConfig.tpoRows
                                    ?: ChartTransforms.defaultRows(view.series, view.firstVisible, view.lastVisible),
                                plotHeight = view.plotHeight,
                                minRowPx = MIN_CELL_HEIGHT_DP.toPx(),
                            ),
                            bracketBars = tpoBracketBars(
                                visibleCount = view.visibleCount,
                                barSeconds = barIntervalSeconds(view.series),
                                bracketMinutes = typeConfig.tpoBracketMinutes,
                            ),
                        )
                        drawnType.isLine ->
                            drawLineSeries(view, palette, filled = drawnType == ChartType.AREA, conflateGap = conflateGap)
                        drawnType == ChartType.BARS -> drawOhlcBars(view, palette, metrics)
                        else -> drawCandles(
                            view = view,
                            palette = palette,
                            hollow = drawnType == ChartType.HOLLOW,
                            metrics = metrics,
                        )
                    }
                }
                // Clipped, like the drawings below and for the same reason. An overlay is a value
                // per bar and most of them stay near the price — but a pivot ladder, a SuperTrend
                // after a flip, or a Bollinger band on a spike all resolve to a y outside the plot,
                // and an unclipped Canvas paints them over the header and the axis. The first render
                // of the chart screen had a pivot line drawn across the symbol name.
                clipRect(0f, 0f, plotWidth, plotHeight) {
                    // The volume profile's rows go in first, so its own three lines and every other
                    // study on the price sit over them rather than under. See
                    // [drawVolumeProfileRows] for why they are anchored where they are.
                    shown.overlays.forEach { overlay ->
                        overlay.profile?.let { rows ->
                            drawVolumeProfileRows(view, rows, Color(overlay.colour), plotWidth)
                        }
                    }
                    shown.overlays.forEach { drawOverlay(view, it, density.density, conflateGap) }
                    // Over the overlays and under the levels: a comparison is a second instrument's
                    // price and belongs in the same layer as the first's moving averages, while a
                    // level is a line the reader has to be able to see across everything.
                    drawComparisons(
                        view = view,
                        comparisons = shown.comparisons,
                        rebased = shownRebased,
                        basis = decoration.comparisonBasis,
                        palette = palette,
                        measurer = measurer,
                    )
                    decoration.levels.forEach { drawLevel(view, it, plotWidth, measurer) }
                    decoration.markers.forEach { drawMarker(view, it, density.density) }
                }
                // The reader's own drawings go *over* the price — the opposite of the signal band.
                // They are annotations on the bars, and an annotation the bars cover is not one.
                //
                // Clipped to the plot, because a Compose Canvas does not clip itself and half these
                // tools are unbounded by definition: a ray runs four screen-diagonals past its
                // second point, and an unclipped one paints straight over the price axis, the volume
                // pane and whatever composable sits below the chart.
                //
                // A live drawing layer wins over the decoration's static list, and carries the
                // half-placed drawing with it — that is what lets a five-point pattern take shape as
                // it is tapped out rather than appearing whole on the fifth tap.
                val marks = drawing?.visible ?: decoration.drawings
                val highlighted = drawing?.selectedId ?: decoration.selectedDrawingId
                if (marks.isNotEmpty()) {
                    clipRect(0f, 0f, plotWidth, plotHeight) {
                        marks.forEach { mark ->
                            drawDrawing(
                                drawing = mark,
                                view = view,
                                measurer = measurer,
                                selected = mark.id == highlighted,
                                // So a Fibonacci price over a red candle is a figure rather than a
                                // smudge. The renderer has no palette of its own; this is the one
                                // colour it needs from ours.
                                plate = palette.stage,
                                grabbed = paintedHandle,
                                // Read here, inside the draw, so the grab animation repaints the
                                // canvas without recomposing a composable with nine gesture
                                // handlers hanging off it.
                                grabProgress = grabGrow.value,
                            )
                        }
                    }
                }
                // Each pane's own scale, resolved once and *published* — the crosshair layer above
                // reads it so that a pointer inside a strip reports what the strip measures rather
                // than a price the price axis would have had to invent. See [PaneBand].
                if (paneHeight > 0f) {
                    var top = plotHeight
                    val share = paneHeight / shown.panes.sumOf { it.heightRatio.toDouble() }.toFloat()
                    val bands = ArrayList<PaneBand>(shown.panes.size)
                    for (pane in shown.panes) {
                        val height = pane.heightRatio * share
                        val band = paneBandOf(pane, view.firstVisible, view.lastVisible, top, height)
                        if (band != null) {
                            bands += band
                            drawPane(view, pane, band, plotWidth, palette, measurer, density.density, metrics.body)
                        }
                        top += height
                    }
                    paneBands[0] = bands
                } else {
                    paneBands[0] = emptyList()
                }
                // The live edge, and its price against the axis. This is the one number on the chart
                // a reader looks for without being asked, and before this it was only in the header —
                // where it says nothing about *where* on the scale the market currently is.
                val lastPriceY =
                    if (decoration.showLastPrice && priceShown) {
                        lastPriceTagY(
                            view = view,
                            measurer = measurer,
                            cache = textCache,
                            twoLines = countdownLive && nowSeconds > 0L,
                        )
                    } else {
                        null
                    }
                if (decoration.showAxes) {
                    // The ladder goes in every gutter the reader asked for; the tags go in one of
                    // them. Two live-price tags saying the same number is not two readings, and the
                    // one that is worth having is the one nearest the live edge.
                    if (frame.rightGutter > 0f) {
                        drawPriceAxis(
                            view = view,
                            gutterX = plotWidth,
                            gutterWidth = frame.rightGutter,
                            palette = palette,
                            measurer = measurer,
                            suppressNear = if (frame.tagsOnRight) lastPriceY else null,
                            ticks = ticks,
                            cache = textCache,
                        )
                    }
                    if (frame.leftGutter > 0f) {
                        drawPriceAxis(
                            view = view,
                            gutterX = -frame.leftGutter,
                            gutterWidth = frame.leftGutter,
                            palette = palette,
                            measurer = measurer,
                            suppressNear = if (frame.tagsOnRight) null else lastPriceY,
                            ticks = ticks,
                            cache = textCache,
                        )
                    }
                    if (decoration.showTimeAxis) {
                        drawTimeAxis(
                            view = view,
                            top = plotHeight + paneHeight,
                            plotWidth = plotWidth,
                            type = type,
                            palette = palette,
                            measurer = measurer,
                            zone = zone,
                            ticks = timeTicks,
                            jalali = jalaliDates,
                            cache = textCache,
                        )
                        drawEventMarks(view, decoration.events, plotHeight + paneHeight, eventColours)
                    }
                    // The frame — item 6 of the owner's list, «چارت چهارچوب ندارد».
                    //
                    // Two hairlines: one where the plot meets the price scale, one where it meets
                    // the time scale. Every terminal draws them and this chart did not, so the
                    // scales floated beside the bars as loose columns of figures and the plot had
                    // no edge to be read against. Drawn in the grid's colour a step above the
                    // grid's alpha, which is exactly the relationship the reference keeps: the
                    // frame is the strongest neutral line on the glass and still a neutral.
                    drawFrameRules(
                        plotWidth = plotWidth,
                        plotBottom = plotHeight + paneHeight,
                        rightGutter = frame.rightGutter,
                        leftGutter = frame.leftGutter,
                        timeAxis = decoration.showTimeAxis,
                        palette = palette,
                    )
                }
                // The day's reference goes under the live price, so the tag that wins a collision
                // is the one that is moving. See [previousSessionClose] for why it is intraday-only.
                if (decoration.showPreviousClose && decoration.showLastPrice && priceShown) {
                    previousSessionClose(view.series, view.lastVisible, zone)?.let { reference ->
                        drawPreviousClose(
                            view = view,
                            price = reference,
                            plotWidth = plotWidth,
                            frame = frame,
                            palette = palette,
                            measurer = measurer,
                            withAxis = decoration.showAxes,
                            suppressNear = lastPriceY,
                        )
                    }
                }
                if (decoration.showLastPrice && priceShown) {
                    drawLastPrice(
                        view = view,
                        plotWidth = plotWidth,
                        frame = frame,
                        palette = palette,
                        measurer = measurer,
                        withAxis = decoration.showAxes,
                        countdown = if (countdownLive && decoration.showAxes && nowSeconds > 0L) {
                            countdownLabel(view, nowSeconds)
                        } else {
                            null
                        },
                    )
                }
                // The trade ring, last, so it sits over the live-price rule and the bars. The
                // centre is published in *canvas* pixels — the tap handler reads it before any
                // gutter arithmetic — so the plot's left offset is added back here.
                if (priceShown && currentTradeRing.value != null) {
                    drawTradeRing(view, plotHeight, palette, frame.left, tradeRing)
                } else {
                    tradeRing[2] = 0f
                }
            }
        }

        // ------------------------------------------------------------------ the second layer
        //
        // The crosshair on its own canvas, stacked over the bars.
        //
        // `Invalidation` was honest that a Compose `Canvas` repaints everything it draws, and that
        // has not changed — but *what* it draws can be split. The bars, the axes and the panes are
        // one draw lambda that reads none of the crosshair's state; the crosshair is a second one
        // that reads it. A pointer moving therefore re-runs this lambda alone: the price range is
        // not re-walked, the ticks are not rebuilt, and the several hundred rectangles underneath
        // are not re-issued. That is the two-layer repaint every terminal has, in the one form
        // Compose offers.
        //
        // The geometry comes from what the layer below published rather than from a second
        // calculation, so the two can never disagree about where a bar is.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val frame = frames[0] ?: return@Canvas
            val view = lastView[0] ?: return@Canvas
            if (view.visibleCount == 0) return@Canvas
            val mark = crosshairOverride ?: crosshair
            val timeAxis = if (decoration.showAxes && decoration.showTimeAxis) timeHeight else 0f
            translate(left = frame.left) {
                // The eight directions the held constraint will accept, drawn from the anchor the
                // next tap is measured against. See [drawConstraintSpokes]: without them the
                // constraint is a mode with no tell, and a reader who armed it by accident has no
                // way to discover they did.
                drawing?.let { live ->
                    if (live.constrainAngle) {
                        constraintAnchors(live).forEach { drawConstraintSpokes(view, it, palette) }
                    }
                }
                if (mark != null) {
                    drawCrosshair(
                        view = view,
                        crosshair = mark,
                        plotWidth = frame.width,
                        frame = frame,
                        fullHeight = max(0f, size.height - timeAxis),
                        palette = palette,
                        measurer = measurer,
                        decoration = decoration,
                        zone = zone,
                        // Which of the three pointers the rail is in. Nothing read this before, so
                        // «نشانگر پیکانی» and «نشانگر نقطه‌ای» were two rail entries that changed a
                        // field and nothing else — see [DrawingMode].
                        mode = drawing?.mode ?: DrawingMode.CURSOR,
                        paneBands = paneBands[0],
                    )
                }
            }
        }

        if (decoration.showLegend) {
            ChartLegendOverlay(
                decoration = decoration,
                series = display,
                viewport = viewport,
                rebased = rebased,
                crosshair = { crosshairOverride ?: crosshair },
                seriesLabel = seriesLabel,
                logoSymbol = legendLogo,
                palette = palette,
                hidden = hidden,
                measurer = measurer,
                tracking = tracking,
                onToggleVisibility = { target ->
                    hidden = if (target in hidden) hidden - target else hidden + target
                    onToggleSeriesVisibility?.invoke(target)
                },
                onOpenSettings = onSeriesSettings,
                onRemove = onRemoveSeries,
                change = change,
                marketStatus = marketStatus,
                // Inset past the gutter it sits beside, so a left-hand axis does not have the
                // legend printed over its numbers.
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (decoration.showAxes && scaleSide != ScaleSide.RIGHT &&
                            scaleSide != ScaleSide.MERGED
                        ) {
                            with(density) { axisWidth.toDp() }
                        } else {
                            0.dp
                        },
                        end = if (decoration.showAxes && scaleSide != ScaleSide.LEFT) {
                            with(density) { axisWidth.toDp() }
                        } else {
                            0.dp
                        },
                    ),
            )
        }

        onRequestAlertAt?.let { request ->
            AlertGutterAffordance(
                pointer = alertPointer,
                frames = frames,
                lastView = lastView,
                palette = palette,
                onRequestAlertAt = request,
            )
        }
    }
}

/**
 * The `+` in the gutter, with its state read here rather than in the chart's own body.
 *
 * A composable of its own for one reason, and it is the same reason the crosshair got its own
 * canvas: reading [pointer] in [CoineProChart] would recompose the entire chart — every gesture
 * handler, the whole draw lambda — because a pointer moved a few pixels down the price axis.
 */
@Composable
private fun AlertGutterAffordance(
    pointer: MutableState<Float?>,
    frames: Array<PlotFrame?>,
    lastView: Array<ChartViewport?>,
    palette: ChartPalette,
    onRequestAlertAt: (Double) -> Unit,
) {
    val y = pointer.value ?: return
    val view = lastView[0] ?: return
    val price = view.priceAt(y)
    if (!price.isFinite()) return
    PriceAxisAlertAffordance(
        frame = frames[0],
        pointerY = y,
        price = price,
        label = view.axisText(price),
        palette = palette,
        onRequestAlertAt = {
            onRequestAlertAt(it)
            pointer.value = null
        },
    )
}


/**
 * A stored ARGB long as a Compose colour.
 *
 * `Color(Long)` is the same conversion every other colour in this file goes through — an overlay's,
 * a level's, a comparison's — and it is used here rather than the packed-`ULong` form so a template
 * colour and an overlay colour cannot end up converted two different ways.
 *
 * What is a decision is the zero-alpha guard. A colour template comes out of a preferences string,
 * and a value written without its alpha byte — `0x2962FF` rather than `0xFF2962FF` — is fully
 * transparent, so the chart would paint nothing at all and report no error. Reading a missing alpha
 * as opaque turns that mistake into a visible colour rather than an invisible chart.
 */
private fun chartColour(argb: Long): Color = Color(opaqueArgb(argb))

/**
 * The same value with an opaque alpha byte when it had none.
 *
 * Split out from [chartColour] so the rule can be asserted without a Compose type in a unit test —
 * the arithmetic is the part that can be wrong, and `Color` is a value class over a `ULong` that
 * adds nothing to assert.
 */
internal fun opaqueArgb(argb: Long): Long =
    if ((argb ushr ALPHA_SHIFT) == 0L) argb or ALPHA_MASK else argb

/**
 * One glyph per event, in the strip the dates live in.
 *
 * ### Where they are, and where they are not
 *
 * In the time-axis band and never on the price. An event is a fact about a *moment*, not about a
 * price, and a marker floating over the candles at an invented height is a marker a reader tries to
 * read a level off. The strip is also the only place on the chart with nothing else competing for
 * the pixels, which is why every terminal puts them there.
 *
 * ### A mark outside the window is dropped, never pinned
 *
 * [ChartEvents.place] already drops what is out of range, and this refuses again rather than
 * clamping. A glyph held against the edge of the axis claims something happened at a time it did
 * not, and a reader who scrolls to it finds a bar with nothing to do with it — which is worse than
 * an event they cannot see, because it is an event they can see and is wrong.
 *
 * A cluster is drawn as a stack: the same square with a second, offset outline behind it, so a
 * reader can tell before tapping that it opens a list rather than a card.
 */
private fun DrawScope.drawEventMarks(
    view: ChartViewport,
    marks: List<EventMark>,
    axisTop: Float,
    colours: EventMarkColours,
) {
    if (marks.isEmpty()) return
    val side = EventGlyphs.SIZE_DP.dp.toPx()
    val centreY = axisTop + EventGlyphs.AXIS_GAP_DP.dp.toPx() + side / 2
    val radius = CornerRadius(EVENT_RADIUS_DP.toPx(), EVENT_RADIUS_DP.toPx())
    for (mark in marks) {
        if (mark.barIndex < view.firstVisible || mark.barIndex > view.lastVisible) continue
        val x = view.xOf(mark.barIndex)
        if (x < 0f || x > view.plotWidth) continue
        val colour = colours.of(mark.importance)
        if (mark.isCluster) {
            drawRoundRect(
                color = colour,
                topLeft = Offset(x - side / 2 + EVENT_STACK_PX, centreY - side / 2 - EVENT_STACK_PX),
                size = Size(side, side),
                cornerRadius = radius,
                style = Stroke(width = HAIRLINE_DP.toPx()),
            )
        }
        drawRoundRect(
            color = colour,
            topLeft = Offset(x - side / 2, centreY - side / 2),
            size = Size(side, side),
            cornerRadius = radius,
        )
    }
}

/** How far the cluster's outline sits behind the glyph in front of it. */
private const val EVENT_STACK_PX = 2f

/** An event glyph's corner. Eased rather than round, like every other chip on this canvas. */
private val EVENT_RADIUS_DP = 2.dp

/**
 * The importance colours, read out of the theme once and carried into the draw pass.
 *
 * A holder rather than three parameters because they travel together and always will: an importance
 * that gained a fourth level would otherwise be a change at every call site.
 */
internal data class EventMarkColours(val high: Color, val medium: Color, val low: Color) {
    /** The colour for one importance. */
    fun of(importance: Importance): Color = when (importance) {
        Importance.HIGH -> high
        Importance.MEDIUM -> medium
        Importance.LOW -> low
    }
}

/**
 * What an importance looks like, in one place.
 *
 * Red for a release that moves the market, amber for one that might, green for one that is on the
 * calendar and rarely does — the same three colours the rest of the app already uses for risk, so a
 * reader does not have to learn a second key. It lives in `core:chart` rather than beside the event
 * sheet because the sheet's module depends on this one and not the other way round, and two tables
 * of three colours is one table that will be changed alone.
 */
@Composable
@ReadOnlyComposable
fun importanceColour(importance: Importance): Color = when (importance) {
    Importance.HIGH -> CoineProColors.Sell
    Importance.MEDIUM -> CoineProColors.Warning
    Importance.LOW -> CoineProColors.Buy
}

/**
 * The zone this chart reads times in unless the caller says otherwise.
 *
 * The same zone `core:marketdata` cuts its buckets in — its `CHART_TIME_ZONE` — written out again
 * here because `core:chart` does not depend on that module and a chart is not going to gain a
 * dependency on the feed layer to learn what a day is. If one of the two ever moves, the other has
 * to move with it: the whole bug this fixes was two halves of the same picture using two zones.
 *
 * A top-level `val`, so the lookup and the parse happen once for the process rather than once per
 * label per frame.
 */
val CHART_ZONE: ZoneId = ZoneId.of("Asia/Tehran")

/** The two halves of a baseline split when there is no baseline to split. Allocated once. */
private val EMPTY_SPLIT: Pair<DoubleArray, DoubleArray> = DoubleArray(0) to DoubleArray(0)

/**
 * How many price rows one footprint bar is cut into.
 *
 * Two constraints meet here and neither one alone is enough. [ChartTransforms.defaultRows] answers
 * in price — a row is an eighth of an average bar's range — and knows nothing about pixels; the plot
 * knows pixels and nothing about what a row means. So the row count is derived from the data and
 * then capped by how tall an *average bar* actually is on screen, because that is the box the rows
 * have to fit inside. Capping against the whole plot instead would allow sixty-four rows in a bar a
 * centimetre high.
 */
private fun DrawScope.footprintRows(view: ChartViewport, config: ChartTypeConfig): Int {
    val requested = config.footprintRows
        ?: ChartTransforms.defaultRows(view.series, view.firstVisible, view.lastVisible)
    val span = view.priceRange.endInclusive - view.priceRange.start
    val average = ChartTransforms.averageRange(view.series.bars)
    val barPixels = if (span > 0.0 && average > 0.0) {
        (average / span * view.plotHeight).toFloat()
    } else {
        view.plotHeight
    }
    return legibleRows(requested, barPixels, MIN_CELL_HEIGHT_DP.toPx())
}

/** The alpha byte of a fully opaque ARGB value. See [chartColour]. */
private const val ALPHA_MASK = 0xFF000000L

/** Where the alpha byte sits in an ARGB value. See [chartColour]. */
private const val ALPHA_SHIFT = 24

/** The colours the chart draws with, resolved once per composition rather than per bar. */
internal data class ChartPalette(
    val up: Color,
    val down: Color,
    val grid: Color,
    val text: Color,
    val crosshair: Color,
    val stage: Color,
)

// ---------------------------------------------------------------------------- series

/**
 * The candles, at the widths [optimalBarWidth] and its neighbours decided.
 *
 * The body is no longer a fixed share of the slot. `optimalBarWidth` gives the whole slot to the
 * body when the bars are hair-thin and opens a gap that settles at a fifth once there is room for
 * one, which is what stops a zoomed-out chart being a grey haze and a zoomed-in one being a picket
 * fence. The outline follows the same rule in reverse: below the width at which two borders would
 * meet, [CandleMetrics.outlined] is false and the body is filled solid, because two overlapping
 * strokes is what turns a screen full of candles to mush.
 */
private fun DrawScope.drawCandles(
    view: ChartViewport,
    palette: ChartPalette,
    hollow: Boolean,
    metrics: CandleMetrics,
) {
    val body = metrics.body
    val wick = crispStroke(metrics.wick)
    for (index in view.firstVisible..view.lastVisible) {
        val bar = view.series[index]
        // Registered, and then everything else about this bar is measured from the registered edge
        // rather than from the geometric centre — see the note on `barLeft`. A body snapped onto a
        // pixel boundary with a wick still drawn at the unsnapped centre is a candle whose mast
        // leans, which is more visible than either error alone.
        val left = barLeft(view.xOf(index), body)
        val x = strokeCentre(left + body / 2f, wick)
        // A hollow chart colours by the *previous close*, not by the bar's own open — that is what
        // makes a run of gaps up read as one colour even when individual bars closed down.
        val rising = if (hollow && index > 0) bar.c >= view.series.close[index - 1] else bar.up
        val colour = if (rising) palette.up else palette.down

        drawLine(
            color = colour,
            start = Offset(x, view.yOf(bar.h)),
            end = Offset(x, view.yOf(bar.l)),
            strokeWidth = wick,
        )
        val top = min(view.yOf(bar.o), view.yOf(bar.c))
        // A doji has no body height at all, and a zero-height rectangle draws nothing — so it is
        // given a hairline. Without it a flat bar vanishes and the chart appears to have a gap.
        // Snapped for the same reason the width is: an unsnapped top and bottom put a fifth of a
        // pixel of the body's own colour into the row above it, which on a screen full of small
        // bodies reads as a chart drawn slightly out of focus.
        val bodyTop = round(top)
        val height = max(1f, round(abs(view.yOf(bar.c) - view.yOf(bar.o))))
        if (hollow && rising && metrics.outlined) {
            drawRect(
                color = colour,
                topLeft = Offset(left, bodyTop),
                size = Size(body, height),
                style = Stroke(width = metrics.border),
            )
        } else {
            drawRect(color = colour, topLeft = Offset(left, bodyTop), size = Size(body, height))
        }
    }
}

/**
 * The four widths every bar-shaped mark on the chart is drawn at, resolved once per frame.
 *
 * Together rather than separately because they constrain each other: the wick is capped by the
 * body, the border is derived from it, and whether the border is drawn at all depends on both. Four
 * call sites each doing their own arithmetic is four chances for the volume bars to be a pixel
 * wider than the candles above them, which reads as a chart that has not been assembled properly.
 */
internal data class CandleMetrics(
    /** The candle body, and the width of a volume bar and a histogram column. */
    val body: Float,
    /** The high-to-low line, never wider than [body]. */
    val wick: Float,
    /** The outline's thickness, meaningful only when [outlined] is true. */
    val border: Float,
    /** Whether there is room to outline the body rather than fill it solid. */
    val outlined: Boolean,
)

/**
 * A second instrument drawn over the first, in the units that make the two comparable.
 *
 * ### Why it does not get its own price axis
 *
 * Because the base chart's geometry is the thing the reader is reading. Rescaling the plot to hold
 * both instruments' prices would move every candle, every level and every trend line the moment a
 * comparison was switched on, and switch them all back when it was switched off — so the act of
 * asking "did gold beat the index" would change the chart the question was about.
 *
 * Instead the compared series is rebased to a percentage and then *mapped back onto the price axis*:
 * a percentage `p` is drawn at the price the base would be at if it had moved `p` from the anchor,
 * `anchor × (1 + p/100)`. The candles keep their own scale and their own labels, and equal
 * percentage moves become equal vertical distances — which is the entire content of the feature. The
 * anchor is the base's close at the first *visible* bar, so the comparison re-answers itself as the
 * reader pans; see [ComparisonBasis.PERCENT] for why that is the honest reading rather than a
 * convenience.
 *
 * ### The gap is never bridged
 *
 * A `NaN` closes the path and the next finite value opens a new one. This is not defensive coding:
 * two instruments do not trade the same hours, and a compared equity against a base that trades all
 * weekend has real holes in it. A path that joined across one would draw a confident straight line
 * through a weekend nobody traded, sloping to wherever Monday opened — and a reader would take that
 * slope for price action, because that is what a line on a chart means.
 *
 * ### The one basis that cannot share the axis
 *
 * [ComparisonBasis.RATIO] is not a percentage of anything the price axis measures — a gold/dollar
 * ratio might live at `0.0004` — so it is drawn against its own scale, taken from
 * [combinedRange] and labelled in a narrow strip at the left edge. The range is padded so the line
 * uses the middle of the plot, which also keeps its top label clear of the legend.
 * [ComparisonBasis.ABSOLUTE] needs a second *price* axis, which this chart does not build and which
 * no screen currently asks for, so it draws nothing rather than drawing a line off the plot.
 */
private fun DrawScope.drawComparisons(
    view: ChartViewport,
    comparisons: List<ComparisonSeries>,
    rebased: List<DoubleArray>,
    basis: ComparisonBasis,
    palette: ChartPalette,
    measurer: TextMeasurer,
) {
    if (comparisons.isEmpty() || rebased.isEmpty()) return
    if (basis == ComparisonBasis.ABSOLUTE) return
    if (view.visibleCount == 0 || view.plotHeight <= 0f) return

    // The ratio scale, when there is one, is taken across every comparison at once so two ratio
    // lines are on the same axis and can be read against each other.
    val ratioRange = if (basis == ComparisonBasis.RATIO) combinedRange(rebased) else null
    val anchor = view.series.close.getOrNull(view.firstVisible) ?: return
    if (basis != ComparisonBasis.RATIO && (!anchor.isFinite() || anchor == 0.0)) return

    val low = ratioRange?.start ?: 0.0
    val high = ratioRange?.endInclusive ?: 0.0
    val span = high - low
    // A flat ratio — one bar visible, or a pair pegged to each other — has no range to divide by,
    // and is drawn down the middle rather than not at all.
    val ratioSpan = if (span > 0.0 && span.isFinite()) span else 0.0

    fun yOfRatio(value: Double): Float {
        if (ratioSpan == 0.0) return view.plotHeight / 2f
        val fraction = RATIO_AXIS_INSET + (value - low) / ratioSpan * (1.0 - 2 * RATIO_AXIS_INSET)
        return if (view.inverted) {
            (fraction * view.plotHeight).toFloat()
        } else {
            (view.plotHeight - fraction * view.plotHeight).toFloat()
        }
    }

    val width = COMPARISON_WIDTH_DP.toPx()
    comparisons.forEachIndexed { position, comparison ->
        val values = rebased.getOrNull(position) ?: return@forEachIndexed
        val path = Path()
        var running = false
        for (index in view.firstVisible..view.lastVisible) {
            val value = values.getOrNull(index) ?: Double.NaN
            if (!value.isFinite()) {
                // The break. See the KDoc: the next finite bar starts a new sub-path rather than
                // continuing this one.
                running = false
                continue
            }
            val y = when (basis) {
                ComparisonBasis.RATIO -> yOfRatio(value)
                ComparisonBasis.INDEXED_100 -> view.yOf(anchor * (1 + (value - 100.0) / 100.0))
                else -> view.yOf(anchor * (1 + value / 100.0))
            }
            if (!y.isFinite()) {
                running = false
                continue
            }
            val x = view.xOf(index)
            if (running) path.lineTo(x, y) else path.moveTo(x, y)
            running = true
        }
        drawPath(path, color = Color(comparison.colour), style = Stroke(width = width))
    }

    if (ratioRange == null || ratioSpan == 0.0) return
    // The ratio's own scale, at the left edge. Two numbers rather than a ladder: this is a shape to
    // read against itself, and a second full gridline set would compete with the price's.
    val places = decimalsFor(high)
    listOf(high to yOfRatio(high), low to yOfRatio(low)).forEach { (value, y) ->
        val label = measurer.measure(formatPrice(value, places), axisStyle(palette.text))
        drawText(
            textLayoutResult = label,
            topLeft = Offset(
                AXIS_PADDING_DP.toPx(),
                (y - label.size.height / 2f).coerceIn(0f, max(0f, view.plotHeight - label.size.height)),
            ),
        )
    }
}

/**
 * One comparison's current reading, in the units its basis is expressed in.
 *
 * The sign is printed for a percentage and not for an index, because `+18%` and `118` say the same
 * thing and only one of them is a *change*. `Locale.US` for the same reason every other market
 * figure on this canvas carries it: the device locale is Persian and would otherwise render this one
 * number in a Latin column in Persian digits.
 */
internal fun comparisonReading(value: Double, basis: ComparisonBasis): String = when {
    !value.isFinite() -> NO_VALUE
    basis == ComparisonBasis.PERCENT ->
        (if (value > 0.0) "+" else "") + formatPrice(value, SCALE_VALUE_DECIMALS) + "%"
    basis == ComparisonBasis.INDEXED_100 -> formatPrice(value, SCALE_VALUE_DECIMALS)
    else -> formatPrice(value, decimalsFor(value))
}

/**
 * How much of the plot a ratio line is kept away from the edges.
 *
 * A tenth at each end. A line that touches the top pixel of the plot reads as clipped rather than as
 * at its maximum, and the inset is also what keeps the upper of the two ratio labels clear of the
 * legend plate in the same corner.
 */
private const val RATIO_AXIS_INSET = 0.1

/** How thick a comparison line is drawn. One step above an overlay, because it is a second subject. */
private val COMPARISON_WIDTH_DP = 1.6.dp

private fun DrawScope.drawOhlcBars(view: ChartViewport, palette: ChartPalette, metrics: CandleMetrics) {
    val tick = metrics.body / 2
    val stroke = crispStroke(metrics.wick)
    for (index in view.firstVisible..view.lastVisible) {
        val bar = view.series[index]
        // Registered on both axes, and for the same reason as the candles: an OHLC bar is nothing
        // but three strokes, so a bar chart is the type where a half-pixel offset is *all* the
        // reader sees. The two ticks take the mast's own registered x so they meet it exactly.
        val x = strokeCentre(view.xOf(index), stroke)
        val colour = if (bar.up) palette.up else palette.down
        val high = view.yOf(bar.h)
        val low = view.yOf(bar.l)
        val open = strokeCentre(view.yOf(bar.o), stroke)
        val close = strokeCentre(view.yOf(bar.c), stroke)
        drawLine(colour, Offset(x, high), Offset(x, low), stroke)
        drawLine(colour, Offset(round(x - tick), open), Offset(x, open), stroke)
        drawLine(colour, Offset(x, close), Offset(round(x + tick), close), stroke)
    }
}

/**
 * The closes as a line, and optionally the area under it.
 *
 * The fill is a shallow vertical ramp rather than a flat wash — see [areaBrush] for the numbers and
 * for why the gate allows this one gradient. Flat at `0.16` it read as a printed block; ramped from
 * the line down it reads as the edge of something lit, which is what every terminal's area chart
 * looks like and what a reader's eye takes as "under the price".
 */
private fun DrawScope.drawLineSeries(
    view: ChartViewport,
    palette: ChartPalette,
    filled: Boolean,
    conflateGap: Float,
) {
    val path = closePath(view, conflateGap)
    // Colour by the direction of the visible window, so a line chart still says up or down at a
    // glance the way a candle chart does.
    val colour = directionColour(view, palette)

    if (filled) {
        val fill = Path().apply {
            addPath(path)
            lineTo(view.xOf(view.lastVisible), view.plotHeight)
            lineTo(view.xOf(view.firstVisible), view.plotHeight)
            close()
        }
        drawPath(fill, brush = areaBrush(colour, 0f, view.plotHeight))
    }
    drawPath(path, color = colour, style = Stroke(width = LINE_WIDTH_DP.toPx()))
}

/**
 * The volume profile itself: one horizontal bar per price row — item 54.
 *
 * The study used to resolve to three flat lines. The rows behind them were bucketed, the point of
 * control and the value area were found, and then the histogram — the entire subject of an
 * indicator named «پروفایل حجم» — was discarded at the call site because the type it had to travel
 * in was one value per time bar. It travels on [ChartLine.profile] now, and this is what draws it.
 *
 * ### Where the bars sit, and why
 *
 * Anchored to the plot's left edge and given [PROFILE_SPAN] of its width at the widest row. Against
 * an edge because a profile drawn across the middle is a profile drawn over the candles, and the
 * candles are what the reader came for; the *left* edge because the price gutter and the last-price
 * tag live on the right, and the newest bars — the ones anybody is actually reading — are there
 * too. The oldest bars on screen are the cheapest pixels on the chart.
 *
 * ### Why it is flat fill and nothing else
 *
 * No gradient and no glow: this app's surface rules forbid both, and a histogram that has to be
 * seen *through* is exactly the case where a designer reaches for one. Three flat alphas do the
 * same job — the point-of-control row reads heaviest, the rest of the value area a step below it,
 * and the tails faint enough that a candle behind them stays legible. That ladder is also the
 * reading: the shape of the value area is visible without tracing the two dashed edges.
 *
 * A row with no volume is skipped rather than drawn as a zero-width rectangle, and the widest row
 * is taken from the profile's own peak, so a window that traded almost everything at one price
 * still fills the span instead of drawing twenty-four slivers.
 */
private fun DrawScope.drawVolumeProfileRows(
    view: ChartViewport,
    profile: VolumeProfile,
    colour: Color,
    plotWidth: Float,
) {
    var peak = 0.0
    for (value in profile.volume) if (value > peak) peak = value
    // A profile of zeros would be drawn as equal bars at every price, which is a claim that the
    // market traded evenly across its whole range. `volumeProfileFor` already refuses the case; this
    // is the second guard, because the divide below is the one that would produce it.
    if (peak <= 0.0) return
    val span = plotWidth * PROFILE_SPAN
    if (span <= 0f) return
    val valueArea = profile.valueAreaLow..profile.valueAreaHigh
    for (row in profile.volume.indices) {
        if (profile.volume[row] <= 0.0) continue
        val top = view.yOf(profile.rowHigh[row])
        val bottom = view.yOf(profile.rowLow[row])
        val slot = abs(bottom - top)
        // A hairline of gap between rows, so twenty-four bars read as twenty-four bars rather than
        // as one block with a ragged right edge — and never thinner than a pixel, because a row
        // that rounds to nothing is a price that traded and cannot be seen to have.
        val height = max(1f, slot - PROFILE_ROW_GAP_DP.toPx())
        val y = min(top, bottom) + (slot - height) / 2
        val width = max(1f, (profile.volume[row] / peak).toFloat() * span)
        val alpha = when {
            row == profile.pocIndex -> PROFILE_POC_ALPHA
            row in valueArea -> PROFILE_AREA_ALPHA
            else -> PROFILE_TAIL_ALPHA
        }
        drawRect(colour.copy(alpha = alpha), Offset(0f, y), Size(width, height))
    }
}

private fun DrawScope.drawOverlay(
    view: ChartViewport,
    overlay: ChartLine,
    density: Float,
    conflateGap: Float,
) {
    val path = Path()
    var started = false
    // The conflator holds a column open, so the pen has to be lifted through it as well: a gap that
    // bypassed it would leave the previous column unflushed and join the two sides of the gap.
    val conflator = ColumnConflator(conflateGap) { x, y ->
        if (started) path.lineTo(x, y) else { path.moveTo(x, y); started = true }
    }
    for (index in view.firstVisible..view.lastVisible) {
        val value = overlay.values[index]
        if (value == null) {
            // A gap in the middle of a line — a SuperTrend flip, a missing bar — lifts the pen
            // rather than drawing a straight line across it, which would read as a real move.
            // Unless the study is one whose gaps are the point: a zigzag names only its turns and
            // the join between them is the whole shape.
            if (!overlay.connectNulls) {
                conflator.flush()
                started = false
            }
            continue
        }
        conflator.add(view.xOf(index), view.yOf(value))
    }
    conflator.flush()
    drawPath(
        path = path,
        color = Color(overlay.colour),
        style = Stroke(
            width = overlay.widthDp * density,
            pathEffect = if (overlay.dashed) {
                dashEffect(LineStyleKind.DASHED, overlay.widthDp * density)
            } else {
                null
            },
        ),
    )
}

// ---------------------------------------------------------------------------- panes

/**
 * Volume in the foot of the price pane.
 *
 * Anchored to the bottom of the plot and scaled so the tallest visible bar reaches [VOLUME_INLINE]
 * of the plot's height. Drawn faint and *under* the candles, so it reads as ground rather than as a
 * second series competing with the price.
 *
 * The peak is taken from what is visible, not from the whole series. One record day three months
 * back would otherwise flatten every bar on screen to a millimetre — which is the same failure the
 * price axis avoids by scaling to the window.
 */
private fun DrawScope.drawVolume(
    view: ChartViewport,
    plotHeight: Float,
    palette: ChartPalette,
    body: Float,
) {
    var peak = 0.0
    for (index in view.firstVisible..view.lastVisible) {
        if (view.series.volume[index] > peak) peak = view.series.volume[index]
    }
    if (peak <= 0.0) return
    val band = plotHeight * VOLUME_INLINE
    val floorY = round(plotHeight)
    for (index in view.firstVisible..view.lastVisible) {
        val bar = view.series[index]
        // Rounded up, not down: a bar whose volume is a hundredth of the session's peak is still a
        // bar that traded, and flooring it to zero height draws nothing where the reader expects a
        // stub. The floor of the band is a single snapped y for every column so the row of bars
        // stands on one line rather than on a ragged one.
        val height = max(1f, round((view.series.volume[index] / peak * band).toFloat()))
        drawRect(
            color = (if (bar.up) palette.up else palette.down).copy(alpha = VOLUME_ALPHA),
            topLeft = Offset(barLeft(view.xOf(index), body), floorY - height),
            size = Size(body, height),
        )
    }
}

/**
 * One indicator pane: a hairline lid, a title, and the lines on their own scale.
 *
 * The scale is taken from what is *visible*, not from the whole series. An RSI that spent last
 * March at 12 must not flatten today's range, and a reader who has zoomed in is asking about the
 * bars in front of them.
 *
 * Levels declared by the pane are folded into the extremes before the scale is fixed, so RSI's 30
 * and 70 are always on screen even on a stretch where the line never reached them — a pane whose
 * reference lines are outside its own scale is a pane that lies about where the line is sitting.
 */
private fun DrawScope.drawPane(
    view: ChartViewport,
    pane: ChartPane,
    band: PaneBand,
    plotWidth: Float,
    palette: ChartPalette,
    measurer: TextMeasurer,
    density: Float,
    body: Float,
) {
    val top = band.top
    val height = band.height
    if (height <= 0f || plotWidth <= 0f) return
    drawRule(palette.grid.copy(alpha = GRID_ALPHA), top, 0f, plotWidth, HAIRLINE_DP.toPx())

    val bottom = band.bottom
    val ceiling = band.ceiling
    val high = band.high
    val low = band.low
    fun yOf(value: Double): Float = band.yOf(value)

    clipRect(0f, top, plotWidth, top + height) {
        pane.levels.forEach { level ->
            val y = yOf(level.price)
            drawRule(
                colour = Color(level.colour.toInt()),
                y = y,
                fromX = 0f,
                toX = plotWidth,
                stroke = HAIRLINE_DP.toPx(),
                pathEffect = dashEffect(LineStyleKind.LARGE_DASHED, HAIRLINE_DP.toPx()),
            )
        }
        pane.histogram?.let { histogram ->
            val zero = yOf(0.0)
            for (index in view.firstVisible..view.lastVisible) {
                val value = histogram.values[index] ?: continue
                val y = yOf(value)
                drawRect(
                    color = if (value >= 0) palette.up else palette.down,
                    topLeft = Offset(barLeft(view.xOf(index), body), round(min(y, zero))),
                    size = Size(body, max(1f, round(abs(y - zero)))),
                )
            }
        }
        pane.lines.forEach { line ->
            val path = Path()
            var started = false
            for (index in view.firstVisible..view.lastVisible) {
                val value = line.values[index]
                if (value == null) {
                    if (!line.connectNulls) started = false
                    continue
                }
                val x = view.xOf(index)
                val y = yOf(value)
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
            drawPath(
                path = path,
                color = Color(line.colour),
                style = Stroke(
                    width = line.widthDp * density,
                    pathEffect = if (line.dashed) {
                        dashEffect(LineStyleKind.DASHED, line.widthDp * density)
                    } else {
                        null
                    },
                ),
            )
        }
    }

    // The title on its own ground. A pane's own reference lines run the full width and one of them
    // is usually near the top — RSI's seventy, MACD's zero on a positive stretch — so without this
    // the dashes ran straight through the letters.
    val title = measurer.measure(pane.title, axisStyle(palette.text))
    drawRect(
        color = palette.stage,
        topLeft = Offset(0f, top + 1f),
        size = Size(title.size.width + LEGEND_INSET_DP.toPx() * 2, title.size.height + AXIS_PADDING_DP.toPx() * 2),
    )
    drawText(title, topLeft = Offset(LEGEND_INSET_DP.toPx(), top + AXIS_PADDING_DP.toPx()))
    // The two extremes at the right edge rather than a full axis: a pane is a shape to read, not a
    // scale to measure off, and five gridline labels in a 90px strip is unreadable noise.
    val decimals = paneDecimals(high - low)
    val topLabel = measurer.measure(formatPrice(ceiling, decimals), axisStyle(palette.text))
    drawText(topLabel, topLeft = Offset(plotWidth + AXIS_PADDING_DP.toPx(), top + AXIS_PADDING_DP.toPx()))
    val bottomLabel = measurer.measure(formatPrice(bottom, decimals), axisStyle(palette.text))
    drawText(
        bottomLabel,
        topLeft = Offset(plotWidth + AXIS_PADDING_DP.toPx(), top + height - bottomLabel.size.height - AXIS_PADDING_DP.toPx()),
    )
}

/**
 * One indicator pane's vertical scale for one frame — where the strip is, and what its pixels mean.
 *
 * ### Why this is a type rather than four locals inside the draw
 *
 * Because the crosshair needs it and could not get it, and the result was the chart stating a price
 * that nobody had quoted.
 *
 * The pointer reports `(index, price)` and the price comes from [ChartViewport.priceAt], which
 * describes the *price* pane and nothing else. A finger inside an RSI strip is below the price
 * plot, so the fraction it produces is negative and the price is somewhere under the low of the
 * visible range — and the axis tag then printed that number, clamped to the bottom of the gutter.
 * The reader touched an oscillator reading 62 and the chart answered with a price. On a screen
 * somebody places orders from, that is the same class of defect as an axis left holding a dead
 * series' labels, which this file already refuses to do.
 *
 * So the scale each pane resolves to is published rather than being a local, the crosshair asks
 * which pane it is standing in, and a pointer inside one reads the pane's own value on the pane's
 * own scale.
 *
 * [low] and [high] are the extremes actually found; [bottom] and [ceiling] are those with the
 * pane's breathing room added and are what the pixels are stretched over.
 */
internal data class PaneBand(
    val top: Float,
    val height: Float,
    val low: Double,
    val high: Double,
    val bottom: Double,
    val ceiling: Double,
) {
    /** Whether a canvas y falls inside this strip. */
    fun contains(y: Float): Boolean = height > 0f && y >= top && y <= top + height

    /** A value in the pane's own units, at a pixel. */
    fun yOf(value: Double): Float =
        top + height * (1.0 - (value - bottom) / (ceiling - bottom)).toFloat()

    /** And back again — what the pane reads at a pixel, which is what the crosshair wants. */
    fun valueAt(y: Float): Double =
        if (height <= 0f) bottom else ceiling - (y - top) / height * (ceiling - bottom)

    /** How precisely to print it. From the pane's own span; see [paneDecimals]. */
    val decimals: Int get() = paneDecimals(high - low)
}

/**
 * The scale a pane resolves to over the bars [first]..[last], or null when it has nothing to draw.
 *
 * Pure and separated from the drawing for the reason the legend rows were: this is the arithmetic
 * two different layers now depend on agreeing about, and a second copy of it inside the crosshair
 * would be a copy that drifts.
 */
internal fun paneBandOf(
    pane: ChartPane,
    first: Int,
    last: Int,
    top: Float,
    height: Float,
): PaneBand? {
    var low = Double.MAX_VALUE
    var high = -Double.MAX_VALUE
    for (line in pane.lines + listOfNotNull(pane.histogram)) {
        for (index in first..last) {
            val value = line.values[index] ?: continue
            if (value < low) low = value
            if (value > high) high = value
        }
    }
    for (level in pane.levels) {
        if (level.price < low) low = level.price
        if (level.price > high) high = level.price
    }
    // A histogram is read against zero, so zero has to be inside the scale even when every bar in
    // view is on one side of it. Without this a run of positive MACD draws as bars hanging off the
    // bottom edge with no baseline to hang from.
    if (pane.histogram != null) {
        if (low > 0.0) low = 0.0
        if (high < 0.0) high = 0.0
    }
    if (high < low) return null
    // A perfectly flat pane still has to draw, and dividing by a zero span would put it at NaN.
    val span = (high - low).takeIf { it > 0.0 } ?: 1.0
    val padded = span * PANE_PADDING
    return PaneBand(
        top = top,
        height = height,
        low = low,
        high = high,
        bottom = low - padded,
        ceiling = high + padded,
    )
}

/**
 * How many decimals a pane's extremes need.
 *
 * Driven by the pane's own span, not by the price's: an RSI spanning 40 points needs none and a
 * MACD spanning 0.004 needs four. Reusing the price rule would print "0.00" for both edges of a
 * MACD pane, which says nothing at all.
 */
private fun paneDecimals(span: Double): Int = when {
    span >= 100 -> 0
    span >= 10 -> 1
    span >= 1 -> 2
    span >= 0.01 -> 4
    else -> 6
}

// ---------------------------------------------------------------------------- axes

/**
 * The prices a grid line and an axis label are allowed to land on.
 *
 * ### Why this is the single most visible thing on the chart
 *
 * Before this, the axis divided the visible range into five and printed whatever fell out:
 * `2571.34`, `2578.85`, `2586.36`. Every label was an arbitrary number, and the range being divided
 * was the *padded* one, so not even the extremes were real. Nothing about that is wrong, and
 * everything about it reads as a debug render — a chart is trusted partly because its axis lands on
 * numbers a person would say out loud.
 *
 * So the step is chosen from the 1-2-5 ladder: the first of `1×10ⁿ`, `2×10ⁿ`, `2.5×10ⁿ`, `5×10ⁿ`
 * that is at least the raw span divided by the target count. `2.5` is in the ladder because prices
 * are not counts — a quarter step is a natural reading on a currency, where a third never is.
 *
 * Ticks are generated across the *padded* range so the top and bottom of the plot are still
 * covered, but they are anchored to the ladder, so panning slides the labels rather than
 * renumbering them.
 */
private fun priceTicks(view: ChartViewport, density: Float = 0f): PriceTicks {
    val low = view.priceRange.start
    val high = view.priceRange.endInclusive
    val span = high - low
    if (span <= 0.0 || !span.isFinite()) return PriceTicks(emptyList(), 0.0)
    if (view.logScale && low > 0.0) return logPriceTicks(low, high)

    val rough = span / gridRows(view.plotHeight, density)
    val magnitude = 10.0.pow(floor(log10(rough)))
    val step = when {
        rough <= magnitude -> magnitude
        rough <= magnitude * 2 -> magnitude * 2
        rough <= magnitude * 2.5 -> magnitude * 2.5
        rough <= magnitude * 5 -> magnitude * 5
        else -> magnitude * 10
    }
    val first = ceil(low / step) * step
    val prices = buildList {
        var price = first
        // Bounded rather than `while (price <= high)`: a step that underflows to zero on a
        // degenerate range would spin here for ever, on the main thread, inside a draw.
        while (price <= high && size <= MAX_TICKS) {
            add(price)
            price += step
        }
    }
    return PriceTicks(prices, step)
}

/** The tick list and the step it was built on, which is what decides the label's precision. */
private class PriceTicks(
    private val prices: List<Double>,
    val step: Double,
) : List<Double> by prices

/**
 * How many decimals a label needs to distinguish it from the label above it.
 *
 * Driven by the gap between two ticks rather than by the price's magnitude: on a step of 0.05 a
 * reader needs two decimals whether the instrument trades at 3 or at 30,000.
 */
/**
 * Gridlines for a logarithmic axis.
 *
 * ### Why the linear ladder is wrong here
 *
 * Evenly spaced *values* are not evenly spaced on a log axis. Feeding a 1-2-5 ladder to a log
 * placement over 1,000 to 100,000 puts eight of its ten lines inside the top fifth of the plot and
 * leaves the bottom four fifths — which is where most of the price action is on exactly the charts
 * a reader turns log scale on for — with nothing at all.
 *
 * ### The ladder that is right
 *
 * One, two and five per decade: 1, 2, 5, 10, 20, 50, 100 … Those are the numbers a reader already
 * reads a log axis by, they are evenly spaced *in log space*, and they are round in every decade
 * rather than round only near the top.
 *
 * When the visible range covers less than a decade — the common case once a reader has zoomed in
 * with log scale still on — the ladder alone gives one or two lines, so the linear ticks are used
 * instead. On that range the two axes are visually identical anyway, so nothing is lost.
 *
 * The returned `step` is the gap between the two lowest lines, which is what
 * [PriceTicks.step] is for: it decides the decimal places of the labels, and on a log axis the
 * smallest gap is the one that needs the most of them.
 */
private fun logPriceTicks(low: Double, high: Double): PriceTicks {
    val decade = floor(log10(low)).toInt()
    val prices = buildList {
        var exponent = decade
        while (exponent <= ceil(log10(high)).toInt() && size <= MAX_TICKS) {
            val base = 10.0.pow(exponent)
            for (multiple in LOG_MULTIPLES) {
                val price = base * multiple
                if (price in low..high) add(price)
            }
            exponent++
        }
    }
    // Too few lines to be a grid. On a sub-decade range the linear ladder is both denser and, at
    // that zoom, indistinguishable from a correct log one.
    if (prices.size < MIN_LOG_TICKS) return linearPriceTicks(low, high)
    val step = if (prices.size >= 2) prices[1] - prices[0] else high - low
    return PriceTicks(prices, step)
}

/** The 1-2-5 ladder, factored out so the log branch can fall back to it. */
private fun linearPriceTicks(low: Double, high: Double): PriceTicks {
    val span = high - low
    if (span <= 0.0 || !span.isFinite()) return PriceTicks(emptyList(), 0.0)
    val rough = span / GRID_ROWS
    val magnitude = 10.0.pow(floor(log10(rough)))
    val step = when {
        rough <= magnitude -> magnitude
        rough <= magnitude * 2 -> magnitude * 2
        rough <= magnitude * 2.5 -> magnitude * 2.5
        rough <= magnitude * 5 -> magnitude * 5
        else -> magnitude * 10
    }
    val first = ceil(low / step) * step
    val prices = buildList {
        var price = first
        while (price <= high && size <= MAX_TICKS) {
            add(price)
            price += step
        }
    }
    return PriceTicks(prices, step)
}

private fun decimalsForStep(step: Double): Int = when {
    step <= 0.0 || !step.isFinite() -> 2
    step >= 100 -> 0
    step >= 1 -> 1
    else -> min(MAX_DECIMALS, ceil(-log10(step)).toInt() + 1)
}

private fun DrawScope.drawGrid(
    view: ChartViewport,
    plotWidth: Float,
    palette: ChartPalette,
    ticks: PriceTicks,
    timeTicks: List<TimeTick>,
) {
    val grid = palette.grid.copy(alpha = GRID_ALPHA)
    // Dotted, the way TradingView draws its grid: one point of ink and three of air, measured off
    // its chart at 2× — two device pixels on, six off. A solid hairline at the same colour reads
    // as a ruled page; the dots read as a scale the candles sit in front of.
    val dots = PathEffect.dashPathEffect(floatArrayOf(GRID_DOT_DP.toPx(), GRID_GAP_DP.toPx()), 0f)
    // One device pixel, registered. A grid is the most repeated mark on the chart and therefore the
    // one whose softness the eye reads as the whole picture's: at 0.8dp on a 3× screen the renderer
    // was asked for 2.4 pixels of ink at a fractional y, and painted three rows — one solid and two
    // partial — for every horizontal rule on the screen. `crispStroke` takes it to a whole pixel and
    // `strokeCentre` puts that pixel on a pixel. Nothing about the layout moves; the smear goes.
    val hairline = crispStroke(HAIRLINE_DP.toPx())
    // On the round prices, not at even fractions of the plot. The two agree because they are the
    // same list — see `priceTicks` — and the list is now computed once per frame and handed to both
    // the grid and the axis rather than being built twice.
    for (price in ticks) {
        val y = view.yOf(price)
        if (y < 0f || y > view.plotHeight) continue
        val row = strokeCentre(y, hairline)
        drawLine(grid, Offset(0f, row), Offset(plotWidth, row), hairline, pathEffect = dots)
    }
    // Verticals stand where the time labels stand, not at even fractions of the width — and now
    // that the labels stand on calendar boundaries, so do the columns. A gridline at midnight or at
    // the start of a month is the only thing a vertical rule on a price chart is for; one at 38% of
    // the visible bar count is a ruled line through the middle of a session.
    for (tick in timeTicks) {
        val x = view.xOf(tick.index)
        if (x < 0f || x > plotWidth) continue
        val column = strokeCentre(x, hairline)
        drawLine(grid, Offset(column, 0f), Offset(column, view.plotHeight), hairline, pathEffect = dots)
    }
}

/** TradingView's grid dot: one point on, three off. See [drawGrid]. */
private val GRID_DOT_DP = 1.dp
private val GRID_GAP_DP = 3.dp

/**
 * A horizontal rule across the plot, on a device-pixel row.
 *
 * Every level this chart draws is horizontal — the pane lids, the reference lines inside a pane, the
 * last price, the previous close, a setup's entry and target — and every one of them was being
 * asked for at a fractional y, which the rasteriser answers by painting two rows at partial
 * coverage. One dashed rule drawn that way looks like a slightly worn dashed rule; **seven** of
 * them, at seven different fractions, look like a chart whose lines were drawn by different hands.
 * The dash pattern makes it worse rather than better, because a soft edge on a short segment is a
 * larger share of the segment.
 *
 * The width is the argument rather than a constant so a caller that has already thickened its line
 * — a selected level, a wider style — keeps its weight and only gains the registration.
 */
private fun DrawScope.drawFrameRules(
    plotWidth: Float,
    plotBottom: Float,
    rightGutter: Float,
    leftGutter: Float,
    timeAxis: Boolean,
    palette: ChartPalette,
) {
    val colour = palette.grid.copy(alpha = FRAME_ALPHA)
    val hairline = crispStroke(HAIRLINE_DP.toPx())
    if (rightGutter > 0f) {
        val column = strokeCentre(plotWidth, hairline)
        drawLine(colour, Offset(column, 0f), Offset(column, plotBottom), hairline)
    }
    if (leftGutter > 0f) {
        val column = strokeCentre(0f, hairline)
        drawLine(colour, Offset(column, 0f), Offset(column, plotBottom), hairline)
    }
    if (timeAxis) {
        val row = strokeCentre(plotBottom, hairline)
        drawLine(colour, Offset(0f, row), Offset(plotWidth, row), hairline)
    }
}

private fun DrawScope.drawRule(
    colour: Color,
    y: Float,
    fromX: Float,
    toX: Float,
    stroke: Float,
    pathEffect: PathEffect? = null,
) {
    val width = crispStroke(stroke)
    val row = strokeCentre(y, width)
    drawLine(colour, Offset(fromX, row), Offset(toX, row), width, pathEffect = pathEffect)
}

/**
 * The labels the time axis will print this frame.
 *
 * Computed once and handed to both the axis and the grid, so the two cannot disagree — the same
 * discipline `priceTicks` follows, and for the same reason.
 *
 * [minGapPx] is turned into a gap in *bars* here rather than inside [TimeScale], which knows nothing
 * about pixels and must not learn: the collision rule is "two labels must not touch", and how many
 * bars that is depends entirely on the zoom.
 */
private fun timeAxisTicks(
    view: ChartViewport,
    type: ChartType,
    zone: ZoneId,
    minGapPx: Float,
): List<TimeTick> {
    if (view.visibleCount <= 0) return emptyList()
    val perBar = view.barWidth
    val gapBars = if (perBar > 0f) ceil(minGapPx / perBar).toInt() else 1
    return TimeScale.ticks(
        times = view.series.time,
        first = view.firstVisible,
        last = view.lastVisible,
        zone = zone,
        minGapBars = gapBars,
        maxTicks = MAX_TIME_LABELS,
        dated = type.isTimeBased,
    )
}

/**
 * The numbers down the right-hand side.
 *
 * ### Three rules, and each one is a defect that used to be visible
 *
 * **Nothing is dropped for overflowing.** A tick whose label runs past the top or the bottom of the
 * axis is *cropped* by the clip below, not skipped. A skipped label is a gridline with no number
 * against it and a reader counting rows to work out what it was; half a number still says which
 * thousand they are in.
 *
 * **Nothing is left overlapping.** Two labels a few pixels apart do not read as two numbers, they
 * read as one damaged one — and it happens for real at the top of a compressed axis, where two
 * ticks land within a line height of each other. [separateLabels] nudges them apart, keeping their
 * order, rather than dropping the later one.
 *
 * **The live-price tag wins.** A gridline label under it is a number half-covered by another
 * number, and the covered one is the one a reader can infer from its neighbours.
 */
private fun DrawScope.drawPriceAxis(
    view: ChartViewport,
    /** The gutter's left edge in plot space — past the plot on the right, negative on the left. */
    gutterX: Float,
    gutterWidth: Float,
    palette: ChartPalette,
    measurer: TextMeasurer,
    suppressNear: Float?,
    ticks: PriceTicks,
    cache: TextWidthCache<TextLayoutResult>,
) {
    if (ticks.isEmpty()) return
    // From the *span*, not from the top of the range. `decimalsFor(2643.18)` gives one decimal
    // because the number is over a thousand — so zoomed into a thirty-cent window on gold, all
    // five labels rounded to the same string. What decides the precision is how far apart the
    // labels are, which is exactly the tick step.
    // Measured on the *scaled* gap rather than on the tick step, because the two are the same number
    // only in the two modes that print prices. In percent the ticks are still prices — that is what
    // places them — while the labels are percentages, and a step of `2.5` dollars on gold would ask
    // for a precision that says nothing about how far apart `+0.09%` and `+0.19%` are.
    val values = ticks.map(view::scaleValue)
    val gap = values.zipWithNext { above, below -> abs(below - above) }
        .filter { it > 0.0 }
        .minOrNull()
        ?: abs(ticks.step)
    val decimals = decimalsForStep(gap)
    // Through the cache, and this is where it earns its keep: the ladder is anchored to round
    // prices, so panning slides the same five to twenty-four strings across the gutter rather than
    // renumbering them, and every one of them was being laid out again on every frame of the drag.
    val style = axisStyle(palette.text)
    // TradingView sets every fifth rung bold — 80,000 among 79,000 and 81,000 — so a reader finds
    // the round levels without reading the column. The rule is the price being a whole multiple
    // of five steps, which is what "round" means on a ladder whose step is already round.
    val heavy = axisStyle(palette.text, bold = true)
    val labels = ticks.map { price ->
        val text = view.axisText(price, decimals)
        val chosen = if (isMajorTick(price, ticks.step)) heavy else style
        cache.measure(text to chosen) { measurer.measure(text, chosen) }
    }
    val lineHeight = labels.maxOf { it.size.height }.toFloat()
    val placed = separateLabels(
        centres = FloatArray(ticks.size) { view.yOf(ticks[it]) },
        height = lineHeight + LABEL_SEPARATION_DP.toPx(),
        top = 0f,
        bottom = view.plotHeight,
    )
    // Cropped rather than dropped: the clip is the whole gutter, so a label pushed against either
    // end loses the part that falls outside and keeps the rest.
    clipRect(gutterX, 0f, gutterX + gutterWidth, view.plotHeight) {
        labels.forEachIndexed { index, label ->
            val top = placed[index] - label.size.height / 2
            if (top + label.size.height < 0f || top > view.plotHeight) return@forEachIndexed
            if (suppressNear != null && abs(top - suppressNear) < label.size.height * 1.4f) {
                return@forEachIndexed
            }
            drawText(
                textLayoutResult = label,
                topLeft = Offset(gutterX + AXIS_PADDING_DP.toPx(), top),
            )
        }
    }
}

/**
 * The price axis with nothing on it.
 *
 * Drawn when the series is empty — a symbol with no history, a load that failed — and it is a
 * deliberate *clearing* rather than simply not drawing. The gutter is repainted in the stage
 * colour, the hairline that separates it from the plot is kept so the chart still has an edge, and
 * a single [NO_VALUE] sits where the numbers would be.
 *
 * The alternative, leaving the last series' labels standing, is the most dangerous thing this file
 * could render: an axis that says the market is at a price nobody has quoted, on a screen a reader
 * is about to place an order from.
 */
private fun DrawScope.drawEmptyAxis(
    gutterX: Float,
    gutterWidth: Float,
    palette: ChartPalette,
    measurer: TextMeasurer,
) {
    val width = max(0f, gutterWidth)
    drawRect(color = palette.stage, topLeft = Offset(gutterX, 0f), size = Size(width, size.height))
    // The hairline goes on the edge the plot is on, so the chart still has a boundary rather than a
    // strip of stage colour floating against it.
    val edge = if (gutterX < 0f) gutterX + width else gutterX
    val axisRule = crispStroke(HAIRLINE_DP.toPx())
    val axisEdge = strokeCentre(edge, axisRule)
    drawLine(
        color = palette.grid.copy(alpha = GRID_ALPHA),
        start = Offset(axisEdge, 0f),
        end = Offset(axisEdge, size.height),
        strokeWidth = axisRule,
    )
    val mark = measurer.measure(NO_VALUE, axisStyle(palette.text))
    drawText(
        textLayoutResult = mark,
        topLeft = Offset(
            gutterX + max(0f, (width - mark.size.width) / 2),
            max(0f, (size.height - mark.size.height) / 2),
        ),
    )
}

/**
 * Where the live-price tag will sit, so the axis can step around it.
 *
 * Computed separately rather than returned from [drawLastPrice], because the axis is drawn first —
 * the tag has to land on top of the gridline labels, not under them.
 */
private fun DrawScope.lastPriceTagY(
    view: ChartViewport,
    measurer: TextMeasurer,
    cache: TextWidthCache<TextLayoutResult>,
    /** Whether the tag carries the countdown under the price, which doubles its height. */
    twoLines: Boolean,
): Float? {
    val bar = view.series.bars.getOrNull(view.lastVisible) ?: return null
    val y = view.yOf(bar.c)
    if (y < 0f || y > view.plotHeight) return null
    // A single glyph, in a fixed style, laid out to find one number: the line height of the axis
    // font. It never changes and it was measured afresh on every frame — twice on a chart with a
    // countdown. Through the cache it is measured once for the life of the screen.
    val probe = axisStyle(Color.White)
    val line = cache.measure(TAG_HEIGHT_PROBE to probe) { measurer.measure(TAG_HEIGHT_PROBE, probe) }
        .size.height
    val secondProbe = tagSecondLineStyle(Color.White)
    val second = if (twoLines) {
        cache.measure(TAG_HEIGHT_PROBE to secondProbe) { measurer.measure(TAG_HEIGHT_PROBE, secondProbe) }
            .size.height
    } else {
        0
    }
    val height = line + second + TAG_PADDING_DP.toPx() * 2
    val anchor = if (twoLines) y - line / 2 - TAG_PADDING_DP.toPx() else y - height / 2
    return anchor.coerceIn(0f, max(0f, view.plotHeight - height))
}

/**
 * The one character the tag height is taken from.
 *
 * A digit rather than a letter because every string this tag ever holds is digits, and a font's
 * line height for `0` is the height the tag has to clear. Named so the two places that probe it
 * agree on the key they cache it under.
 */
private const val TAG_HEIGHT_PROBE = "0"

/**
 * The price at the live edge, tagged against the axis.
 *
 * A dashed rule the width of the plot and a filled tag on the axis in the last bar's own direction.
 * The rule is what places the price on the visible scale; the tag is what makes the number legible
 * against the gridline labels it sits between.
 *
 * Skipped when the last close is outside the visible price range, which happens whenever the reader
 * has panned back — a tag pinned to the top or bottom edge would claim a price the chart is not
 * showing.
 */
private fun DrawScope.drawLastPrice(
    view: ChartViewport,
    plotWidth: Float,
    frame: PlotFrame,
    palette: ChartPalette,
    measurer: TextMeasurer,
    withAxis: Boolean,
    /** The bar's time to run, already formatted, or null where there is no countdown to show. */
    countdown: String? = null,
) {
    val bar = view.series.bars.getOrNull(view.lastVisible) ?: return
    val y = view.yOf(bar.c)
    if (y < 0f || y > view.plotHeight) return
    val colour = if (bar.up) palette.up else palette.down
    drawRule(
        colour = colour.copy(alpha = LAST_PRICE_ALPHA),
        y = y,
        fromX = 0f,
        toX = plotWidth,
        stroke = HAIRLINE_DP.toPx(),
        // Dotted rather than dashed: TradingView's last-price line is a one-point dot every three.
        pathEffect = dashEffect(LineStyleKind.SPARSE_DOTTED, HAIRLINE_DP.toPx()),
    )
    if (!withAxis || frame.tagGutterWidth <= 0f) return
    // One tag, two lines: the price over the countdown, in the same fill — TradingView's own
    // arrangement, measured at 30 css px tall for two 12 px lines. It used to be a coloured tag
    // with a second, stage-coloured chip under it, which read as two labels rather than one fact.
    drawAxisTag(
        text = view.axisText(bar.c),
        y = y,
        frame = frame,
        fill = colour,
        textColour = TAG_INK,
        measurer = measurer,
        plotHeight = view.plotHeight,
        secondLine = countdown,
    )
}

/**
 * White on the fill, in both themes — what TradingView prints. The tag's fill is the candle
 * colour, and TradingView's greens and reds are dark enough that white clears them.
 */
private val TAG_INK = Color.White

/**
 * TradingView's trade button: a purple ring with a lightning bolt, hanging at the bottom of the
 * plot under the newest bar.
 *
 * Measured off the phone app: a 20 pt ring, 1.5 pt stroke, its bottom 3 pt above the time axis,
 * centred on the live bar, filled with the pane colour so the grid does not run through it. It is
 * drawn only while the newest bar is on screen and only when the ring has room — a ring clipped by
 * the plot's edge is half a button.
 *
 * [out] receives the centre and radius in canvas pixels for the tap handler; a zero radius means
 * nothing was drawn this frame.
 */
private fun DrawScope.drawTradeRing(
    view: ChartViewport,
    plotHeight: Float,
    palette: ChartPalette,
    plotLeft: Float,
    out: FloatArray,
) {
    val last = view.series.bars.lastIndex
    if (last < 0 || last < view.firstVisible || last > view.lastVisible) {
        out[2] = 0f
        return
    }
    val radius = TRADE_RING_DP.toPx() / 2f
    val x = view.xOf(last)
    val y = plotHeight - TRADE_RING_LIFT_DP.toPx() - radius
    if (x - radius < 0f || y - radius < 0f) {
        out[2] = 0f
        return
    }
    val centre = Offset(x, y)
    val ink = Color(TradingViewPalette.TRADE)
    drawCircle(color = palette.stage, radius = radius, center = centre)
    drawCircle(color = ink, radius = radius, center = centre, style = Stroke(TRADE_RING_STROKE_DP.toPx()))
    // The bolt: a six-point polygon, drawn at 55 % of the ring's height and 60 % as wide as tall,
    // which is the proportion of the phone app's glyph.
    val h = radius * TRADE_BOLT_HEIGHT
    val w = h * TRADE_BOLT_WIDTH
    val bolt = Path().apply {
        moveTo(x + w * 0.20f, y - h / 2f)
        lineTo(x - w / 2f, y + h * 0.10f)
        lineTo(x - w * 0.02f, y + h * 0.10f)
        lineTo(x - w * 0.20f, y + h / 2f)
        lineTo(x + w / 2f, y - h * 0.10f)
        lineTo(x + w * 0.02f, y - h * 0.10f)
        close()
    }
    drawPath(bolt, ink)
    out[0] = x + plotLeft
    out[1] = y
    out[2] = radius
}

/** The ring's diameter, stroke and lift off the time axis. Phone app, measured: 20 / 1.5 / 3 pt. */
private val TRADE_RING_DP = 20.dp
private val TRADE_RING_STROKE_DP = 1.5.dp
private val TRADE_RING_LIFT_DP = 3.dp

/** Extra reach around the ring for a finger, on top of its own radius. */
private val TRADE_RING_REACH_DP = 12.dp

/** The bolt's height as a fraction of the ring's radius, and its width as a fraction of its height. */
private const val TRADE_BOLT_HEIGHT = 1.1f
private const val TRADE_BOLT_WIDTH = 0.6f

/**
 * The close of the session before the one bar [index] belongs to, or null where there is not one.
 *
 * A *session* rather than a bar, decided by the calendar day in the reader's own zone. That is what
 * makes the line mean "where the day started" on a five-minute chart and on a four-hour one alike,
 * without either of them being told what timeframe it is.
 *
 * Null on three honest cases and never a fabricated number: a series too short to have a bar
 * interval, a chart whose bars are a day or longer — where the previous close is the candle next
 * door and the line would say nothing — and a window that does not reach back into a previous day.
 *
 * Pure, so the rule is testable off the canvas. The walk is backwards from [index] and stops at the
 * first bar in a different day, which on a normal intraday chart is at most a session's worth of
 * bars and is done once per frame.
 */
internal fun previousSessionClose(series: CandleSeries, index: Int, zone: ZoneId): Double? {
    if (series.size < 2 || index !in 0 until series.size) return null
    val times = series.time
    val interval = times[times.size - 1] - times[times.size - 2]
    if (interval <= 0L || interval >= SECONDS_PER_DAY) return null
    val day = localDay(times[index], zone)
    // **A binary search, and the reason is the depth the archive now reaches.**
    //
    // This used to walk back one bar at a time, and the walk is bounded by a session — which is
    // fine at three hundred bars and is not fine at fifty thousand. On a one-minute chart a session
    // is fourteen hundred bars, and each step of the walk is an `Instant.atZone().toLocalDate()`:
    // a calendar conversion, not a comparison. Fourteen hundred of those, on every frame of a
    // drag, to find one number that hardly ever changes.
    //
    // The search is legitimate because `localDay` is monotone in the timestamps and the timestamps
    // are required to ascend — `CandleSeries` checks that on construction. So "the last bar before
    // [index] that belongs to an earlier day" can be found by halving, in seventeen conversions
    // instead of fourteen hundred, and the answer is the same bar the walk returned.
    var low = 0
    var high = index - 1
    var found = -1
    while (low <= high) {
        val middle = (low + high) / 2
        if (localDay(times[middle], zone) < day) {
            found = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    return if (found < 0) null else series.close[found]
}

/** The calendar day a moment falls in, in [zone]. Whole days, so a comparison is one subtraction. */
private fun localDay(epochSeconds: Long, zone: ZoneId): Long =
    Instant.ofEpochSecond(epochSeconds).atZone(zone).toLocalDate().toEpochDay()

/** A day, for the rule that keeps the previous close off charts that have no session. */
private const val SECONDS_PER_DAY = 86_400L

/**
 * The previous session's close, ruled across the plot.
 *
 * Fainter than the live-price rule and dotted rather than dashed, because the two are different
 * kinds of claim and must not read as a pair of equals: one is where the market *is* and belongs to
 * this second, the other is a fixed reference the whole day is measured against. A reader glancing
 * at the chart has to be able to tell which is which without reading either number.
 *
 * The tag is drawn hollow — the stage colour behind the axis' own text — for the same reason. A
 * filled chip in the direction colour is the live price's, and a second one beside it would be two
 * chips competing to be the number the reader looks at.
 *
 * Skipped when it would land on the live-price tag, which happens whenever the day is flat: two
 * labels a few pixels apart do not read as two numbers, they read as one damaged one, and of the
 * pair the reference is the one a reader can infer from the fact that the market has not moved.
 */
private fun DrawScope.drawPreviousClose(
    view: ChartViewport,
    price: Double,
    plotWidth: Float,
    frame: PlotFrame,
    palette: ChartPalette,
    measurer: TextMeasurer,
    withAxis: Boolean,
    suppressNear: Float?,
) {
    val y = view.yOf(price)
    if (y < 0f || y > view.plotHeight) return
    drawRule(
        colour = palette.text.copy(alpha = PREVIOUS_CLOSE_ALPHA),
        y = y,
        fromX = 0f,
        toX = plotWidth,
        stroke = HAIRLINE_DP.toPx(),
        pathEffect = dashEffect(LineStyleKind.DOTTED, HAIRLINE_DP.toPx()),
    )
    if (!withAxis || frame.tagGutterWidth <= 0f) return
    val label = measurer.measure(view.axisText(price), axisStyle(palette.text))
    val height = label.size.height + TAG_PADDING_DP.toPx() * 2
    val top = (y - height / 2).coerceIn(0f, max(0f, view.plotHeight - height))
    if (suppressNear != null && abs(top - suppressNear) < height) return
    drawAxisChip(frame, top, height, palette.stage)
    drawText(
        textLayoutResult = label,
        topLeft = Offset(frame.tagGutterX + AXIS_PADDING_DP.toPx(), top + TAG_PADDING_DP.toPx()),
    )
}

/**
 * What the live bar's countdown reads right now, or null where there is nothing to count.
 *
 * Null for a series too short to have an interval, and for one whose last bar is not on screen —
 * the countdown lives inside the live-price tag now (see [drawLastPrice]) and a tag that is not
 * drawn has no second line to fill.
 */
private fun countdownLabel(view: ChartViewport, nowSeconds: Long): String? {
    val times = view.series.time
    if (times.size < 2) return null
    val interval = times[times.size - 1] - times[times.size - 2]
    if (interval <= 0L) return null
    val bar = view.series.bars.getOrNull(view.lastVisible) ?: return null
    val priceY = view.yOf(bar.c)
    if (priceY < 0f || priceY > view.plotHeight) return null
    val remaining = times[times.size - 1] + interval - nowSeconds
    // Null rather than a placeholder when there is nothing to count: TradingView's tag simply has
    // one line then, and a `∅` under the price read as a reading rather than as an absence.
    return if (remaining in 0..MAX_COUNTDOWN_SECONDS) formatCountdown(remaining) else null
}

/**
 * The plate behind a number in the price gutter, rounded on one side only.
 *
 * A chip with four rounded corners floats: it reads as a badge that happens to be near the axis,
 * and the reader's eye goes looking for what it is pointing at. Square on the edge it is flush
 * against and rounded on the side that faces the plot, it reads as something that has *grown out
 * of* the axis — which is exactly the claim it is making about the price it carries.
 *
 * The gutter is on the right on every chart in this app, so [labelChipRadii] is asked for the
 * right-aligned form. The two-pixel radius is small enough never to compete with the digits inside
 * it and large enough to be visible as a decision rather than as an artefact.
 */
private fun DrawScope.drawAxisChip(
    frame: PlotFrame,
    top: Float,
    height: Float,
    fill: Color,
) {
    val radius = TAG_RADIUS_DP.toPx()
    // Square against the canvas edge and rounded towards the plot, whichever side the gutter is on.
    // Hard-coding the right-aligned form was one of the several ways the axis could only ever be on
    // the right: on a left-hand gutter it rounded the corners that are flush with the screen.
    val radii = labelChipRadii(rightAligned = frame.tagsOnRight, radius = radius)
    val gutterX = frame.tagGutterX
    val chip = Path().apply {
        addRoundRect(
            RoundRect(
                left = gutterX + 1f,
                top = top,
                right = gutterX + max(1f, frame.tagGutterWidth - 1f),
                bottom = top + height,
                topLeftCornerRadius = CornerRadius(radii[0], radii[1]),
                topRightCornerRadius = CornerRadius(radii[2], radii[3]),
                bottomRightCornerRadius = CornerRadius(radii[4], radii[5]),
                bottomLeftCornerRadius = CornerRadius(radii[6], radii[7]),
            ),
        )
    }
    drawPath(chip, color = fill)
}

/**
 * Seconds as `m:ss`, `h:mm:ss` or `Nd`, whichever the size calls for.
 *
 * Latin digits and a colon, deliberately, in a Persian-first app: this is a market figure — the
 * same class of thing as a price — and the axis it sits on is already Latin. Persian digits here
 * would make one label in a column of numbers read differently from the rest.
 */
internal fun formatCountdown(seconds: Long): String {
    val days = seconds / 86_400
    if (days >= 1) return "${days}d"
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val rest = seconds % 60
    // `Locale.US`, not the default. `String.format` follows the device locale, and this app's
    // default locale is Persian — so without it the countdown reads «۱۴:۰۵» in a column of Latin
    // prices. The price formatter above was fixed for the same reason and this is the same bug.
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, rest)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, rest)
    }
}

/**
 * A filled label in the price gutter.
 *
 * Shared by the last-price tag and the crosshair's, so the two are the same object in two colours
 * rather than two things that drifted apart. Clamped inside the plot so a tag near an edge stays
 * whole instead of being sliced by the canvas boundary.
 */
private fun DrawScope.drawAxisTag(
    text: String,
    y: Float,
    frame: PlotFrame,
    fill: Color,
    textColour: Color,
    measurer: TextMeasurer,
    /** The bottom of the strip the tag is clamped inside. */
    plotHeight: Float,
    /** And its top, which is zero for the price plot and the pane's own edge inside a pane. */
    top: Float = 0f,
    /** A second line under the first, in the same tag — the live bar's countdown. */
    secondLine: String? = null,
    /** Above and below the text. The live tag's is 3 dp; the crosshair's 4, for a 24 px tag. */
    padding: Dp = TAG_PADDING_DP,
    /** From the gutter's edge to the text. The axis' own 10; the crosshair's 8. */
    inset: Dp = AXIS_PADDING_DP,
) {
    val label = measurer.measure(text, axisStyle(textColour))
    // The countdown, a size down and a shade lighter than the price above it — the phone app's
    // tag prints `687.46` at 14 pt in white and `00:14` at 12 pt in white at about 70 %.
    val second = secondLine?.let { measurer.measure(it, tagSecondLineStyle(textColour)) }
    val pad = padding.toPx()
    val height = label.size.height + (second?.size?.height ?: 0) + pad * 2
    val lowest = max(top, plotHeight - height)
    // Centred on the price when there is one line; with two, the *price* line stays on the price
    // and the countdown hangs under it, which is where TradingView puts it.
    val anchor = if (second == null) y - height / 2 else y - label.size.height / 2 - pad
    val tagTop = anchor.coerceIn(top, lowest)
    drawAxisChip(frame, tagTop, height, fill)
    // **The inset gives way before the digits do.**
    //
    // The gutter is measured against the widest label the axis expects to print, and that estimate
    // can be beaten: a viewport with pinned decimals, a price that gained a digit since the width
    // was cached, a system font scaled up between two frames. When it is, the tag used to keep its
    // ten-point inset and push the number off the edge of the canvas — a price with its first
    // characters missing, which is worse than a price set a little tighter against the axis. So
    // the inset is what shrinks, down to a single pixel, and the figure stays whole.
    val widest = max(label.size.width, second?.size?.width ?: 0)
    val room = frame.tagGutterWidth - widest - 1f
    val x = frame.tagGutterX + inset.toPx().coerceIn(1f, max(1f, room))
    drawText(textLayoutResult = label, topLeft = Offset(x, tagTop + pad))
    second?.let {
        drawText(textLayoutResult = it, topLeft = Offset(x, tagTop + pad + label.size.height))
    }
}


private fun DrawScope.drawTimeAxis(
    view: ChartViewport,
    top: Float,
    plotWidth: Float,
    type: ChartType,
    palette: ChartPalette,
    measurer: TextMeasurer,
    zone: ZoneId,
    ticks: List<TimeTick>,
    /** Solar Hijri dates. See [formatTimeTick]; resolved once per frame from the composition. */
    jalali: Boolean,
    /**
     * The measured-label cache the price axis already uses.
     *
     * The dates were the last uncached text on this canvas, and they were the worst place for it
     * to be left: laying a string out is font work rather than arithmetic, this loop does it up to
     * [MAX_TIME_LABELS] times, and it ran on **every frame of a drag** for labels that do not
     * change. A reader panning across an afternoon re-measures the same six dates sixty times a
     * second — the ladder slides, it does not renumber — which is precisely the shape the cache in
     * [TextWidthCache] was written for and the argument its own KDoc makes about the price gutter.
     */
    cache: TextWidthCache<TextLayoutResult>,
) {
    // How much time the plot is showing, which is what decides how much of a date a month label
    // needs. Taken from the visible window rather than the timeframe: the same daily chart zoomed
    // into a fortnight and zoomed out to a decade wants different labels.
    val span = (view.series.time.getOrNull(view.lastVisible) ?: 0L) -
        (view.series.time.getOrNull(view.firstVisible) ?: 0L)
    // Where the previous label ended, so two that survived the tick spacing but whose *glyphs* are
    // wider than it assumed do not print on top of each other. Skipping a label is better than
    // overlapping one: two collided labels say nothing, one says when.
    var occupiedUntil = -Float.MAX_VALUE
    // The bars the labels sit on are the reader's right edge inward, so the whole row is laid out
    // against the bar area rather than the canvas: a label clamped to `plotWidth` would be dragged
    // into the empty slots at the live edge and would then be naming a bar it is not over.
    val barArea = view.xOf(view.lastVisible) + view.barWidth / 2
    // The two styles this axis prints in, built once for the frame rather than once per label. They
    // are also half the cache key — see [TextWidthCache] on why the key cannot be the text alone: a
    // layout carries the colour it was measured with, so a palette change has to miss.
    val plain = axisStyle(palette.text, axisFontSizeSp(isPriceAxis = false), bold = false)
    val heavy = axisStyle(palette.text, axisFontSizeSp(isPriceAxis = false), bold = true)
    for (tick in ticks) {
        // A price-driven type has no clock, so its axis is numbered by bar. Printing a date there
        // would be a fabricated one — Renko bars carry synthetic timestamps.
        val text =
            if (type.isTimeBased) formatTimeTick(tick, span, zone, jalali) else "#${tick.index + 1}"
        val style = if (tick.isBoundary()) heavy else plain
        val label = cache.measure(text to style) { measurer.measure(text, style) }
        val limit = max(0f, min(plotWidth, barArea) - label.size.width)
        val x = (view.xOf(tick.index) - label.size.width / 2).coerceIn(0f, limit)
        if (x < occupiedUntil) continue
        drawText(label, topLeft = Offset(x, top + AXIS_PADDING_DP.toPx()))
        occupiedUntil = x + label.size.width + LABEL_GAP
    }
}

// ---------------------------------------------------------------------------- overlays

/**
 * One horizontal level, with its label at the left end.
 *
 * The label goes left because the right of the plot is where price is, and a row of labels stacked
 * against the live edge is a row of labels over the bars a reader is actually watching.
 */
private fun DrawScope.drawLevel(
    view: ChartViewport,
    level: PriceLevel,
    plotWidth: Float,
    measurer: TextMeasurer,
) {
    val y = view.yOf(level.price)
    if (y < 0f || y > view.plotHeight) return
    val colour = Color(level.colour.toInt())
    val right = if (level.extendRight) plotWidth else view.xOf(view.lastVisible)
    drawRule(
        colour = colour,
        y = y,
        fromX = 0f,
        toX = right,
        stroke = HAIRLINE_DP.toPx(),
        pathEffect = dashEffect(LineStyleKind.LARGE_DASHED, HAIRLINE_DP.toPx()),
    )
    val text = level.label ?: return
    val measured = measurer.measure(text, axisStyle(colour))
    drawText(measured, topLeft = Offset(AXIS_PADDING_DP.toPx(), y - measured.size.height - 1f))
}

/**
 * One marker, clear of the bar it belongs to.
 *
 * Offset above the high or below the low rather than drawn at the price, because a marker on the
 * high hides the high — and on a swing study the high is the thing being pointed at.
 */
private fun DrawScope.drawMarker(view: ChartViewport, marker: ChartMarker, density: Float) {
    val x = view.xOfTime(marker.time)
    if (x < 0f || x > view.plotWidth) return
    val clearance = MARKER_CLEARANCE * density
    val size = MARKER_SIZE * density
    val anchor = view.yOf(marker.price) + if (marker.above) -clearance else clearance
    val colour = Color(marker.colour.toInt())
    when (marker.glyph) {
        MarkerGlyph.CIRCLE -> drawCircle(colour, size / 2, Offset(x, anchor))
        MarkerGlyph.ARROW_UP, MarkerGlyph.ARROW_DOWN -> {
            val pointsDown = marker.glyph == MarkerGlyph.ARROW_DOWN
            val tip = if (pointsDown) anchor + size / 2 else anchor - size / 2
            val base = if (pointsDown) anchor - size / 2 else anchor + size / 2
            val triangle = Path().apply {
                moveTo(x, tip)
                lineTo(x - size / 2, base)
                lineTo(x + size / 2, base)
                close()
            }
            drawPath(triangle, colour)
        }
    }
}

/**
 * The setup, anchored to the bar the position opened on.
 *
 * ### What changed and why
 *
 * These bands used to be drawn from x zero to the plot's right edge whatever the setup was — a
 * full-width green wash above the entry and a red one below it, on every chart in the app that
 * carries a [SignalOverlay]. On screen that says "this whole chart is a long position", which is
 * false: before the entry bar there was no stop and no target to be inside, and after a close there
 * is no position left to be in. That is the report — «حد سود و ضرر از کندلی که پوزیشن باز شده ستاپ
 * چیده بشه … نه کل چارت رو».
 *
 * So [setupSpan] decides the horizontal reach and this only fills it, and [setupBands] decides which
 * side of the entry each colour goes on — which is what makes a short invert without a branch here.
 * Both are pure and both are asserted in `SetupZoneTest`; this function is a draw pass and cannot be.
 *
 * The hairline and its label travel with the band rather than spanning the plot, for the same reason
 * the fill does: a dashed stop drawn back across forty bars of history is the same claim as the
 * shading, in less ink. A setup with no [SignalOverlay.issuedAt] is the one case that still reaches
 * both edges, and it reaches them with no fill at all — see [SetupSpan.anchored].
 */
private fun DrawScope.drawSignal(
    view: ChartViewport,
    signal: SignalOverlay,
    palette: ChartPalette,
    measurer: TextMeasurer,
) {
    val span = setupSpan(view, signal)
    // A setup whose whole life is off one side of the plot. Nothing at all rather than a sliver
    // pinned to the edge, which would put a position at a time it was not open.
    if (span.isEmpty) return
    val entryY = view.yOf(signal.entry)
    val labelX = span.left + AXIS_PADDING_DP.toPx()

    // The band from entry to stop is the money at risk, and the band from entry to target is the
    // money on offer. Drawn as areas because their relative size is the whole judgement — but only
    // over the bars the position was actually open across.
    if (span.anchored) {
        setupBands(signal).forEach { band ->
            val fromY = view.yOf(band.from)
            val toY = view.yOf(band.to)
            drawRect(
                color = colourOf(band.role, palette).copy(alpha = ZONE_ALPHA),
                topLeft = Offset(span.left, min(fromY, toY)),
                size = Size(span.width, abs(toY - fromY)),
            )
        }
    }

    signal.stopLoss?.let { stop ->
        val stopY = view.yOf(stop)
        drawDashedLevel(stopY, span.left, span.right, palette.down)
        drawLevelLabel(signal.stopLabel, stopY, labelX, palette.down, measurer, palette)
    }
    signal.takeProfits.forEachIndexed { index, price ->
        val y = view.yOf(price)
        drawDashedLevel(y, span.left, span.right, palette.up)
        drawLevelLabel(signal.targetLabels.getOrNull(index), y, labelX, palette.up, measurer, palette)
    }
    drawLine(
        color = palette.crosshair,
        start = Offset(span.left, entryY),
        end = Offset(span.right, entryY),
        strokeWidth = LINE_WIDTH_DP.toPx(),
    )
    drawLevelLabel(signal.entryLabel, entryY, labelX, palette.crosshair, measurer, palette)
    drawEntryMark(view, signal, span, entryY, palette)
}

/** The sell colour for the risk, the buy colour for the reward. The whole of the mapping. */
private fun colourOf(role: SetupBandRole, palette: ChartPalette): Color =
    if (role == SetupBandRole.RISK) palette.down else palette.up

/**
 * The tick that says the position opened *here*.
 *
 * Small on purpose: a rule from the top of the plot to the bottom would be a second crosshair, and
 * the reader already has one. What is needed is only the left end of the band identified with the
 * candle underneath it — so the mark spans the setup's own prices and stops, and a dot sits on the
 * entry itself.
 *
 * Nothing is drawn when the entry bar has scrolled off the left. The band still reaches the edge,
 * because the position really was open across every bar on screen; a mark pinned to the edge would
 * instead say it opened at a bar it did not.
 */
private fun DrawScope.drawEntryMark(
    view: ChartViewport,
    signal: SignalOverlay,
    span: SetupSpan,
    entryY: Float,
    palette: ChartPalette,
) {
    val x = span.entryX?.takeIf { span.anchored } ?: return
    val ys = signal.levels().filter { it.isFinite() }.map { view.yOf(it) }
    drawLine(
        color = palette.crosshair,
        start = Offset(x, ys.minOrNull() ?: entryY),
        end = Offset(x, ys.maxOrNull() ?: entryY),
        strokeWidth = HAIRLINE_DP.toPx(),
    )
    drawCircle(palette.crosshair, ENTRY_MARK_DP.toPx(), Offset(x, entryY))
}

/**
 * A word above its line, at the left edge.
 *
 * Above rather than on: a label drawn across a price line is a label with a rule through it, which
 * is the mistake the drawing tools had to be fixed for. Skipped entirely when the line is off the
 * plot, so a target far above the visible range does not leave its name floating at the top.
 */
private fun DrawScope.drawLevelLabel(
    text: String?,
    y: Float,
    x: Float,
    colour: Color,
    measurer: TextMeasurer,
    palette: ChartPalette,
) {
    if (text.isNullOrBlank()) return
    val measured = measurer.measure(text, axisStyle(colour))
    // Above the line by default, and below it when above would be underneath the legend.
    //
    // The legend is a Compose overlay sitting on the top-left of the plot, so the canvas cannot see
    // it — and a setup whose target is near the top of the visible range put «حد سود» straight
    // under the OHLC row, where the plate and the legend's own plate stacked into a smudge. A level
    // reads the same either side of its line; the legend does not move.
    val reserved = LEGEND_LINES * measured.size.height + LEGEND_INSET_DP.toPx() * 2f
    val above = y - measured.size.height - 1f
    val top = if (above < reserved) y + 1f else above
    if (top < 0f || top + measured.size.height > size.height) return
    // A plate under the word, because these labels sit *inside* the plot rather than out on the
    // axis: a setup anchored to its entry candle puts «ورود» straight over the bars, and three
    // characters in the sell red on top of a red candle is not a word, it is a smudge. The axis
    // labels need no plate — nothing is drawn behind them.
    //
    // The stage colour at four-fifths rather than solid, so the candle underneath still shows
    // through and the plate reads as a highlight over the chart instead of a hole cut in it.
    val padding = LEGEND_INSET_DP.toPx()
    drawRoundRect(
        color = palette.stage.copy(alpha = LEVEL_PLATE_ALPHA),
        topLeft = Offset(x - padding, top - padding / 2f),
        size = Size(measured.size.width + padding * 2, measured.size.height + padding),
        cornerRadius = CornerRadius(EVENT_RADIUS_DP.toPx(), EVENT_RADIUS_DP.toPx()),
    )
    drawText(measured, topLeft = Offset(x, top))
}

/**
 * A dashed rule between two x's rather than across the plot.
 *
 * It takes both ends because a setup's lines are only true between them — see [drawSignal]. A caller
 * that wants the whole width passes zero and the plot width, which is what a timeless level is.
 */
private fun DrawScope.drawDashedLevel(y: Float, startX: Float, endX: Float, colour: Color) {
    drawRule(
        colour = colour,
        y = y,
        fromX = startX,
        toX = endX,
        stroke = HAIRLINE_DP.toPx(),
        pathEffect = dashEffect(LineStyleKind.LARGE_DASHED, HAIRLINE_DP.toPx()),
    )
}

/**
 * The crosshair, and the two labels that make it readable.
 *
 * Snapped to a bar in x and free in y: the reader is asking "what happened at this bar", and a
 * crosshair between two bars answers a question about a bar that does not exist.
 *
 * The price is tagged in the axis gutter and the time under the plot, rather than being printed in
 * a corner. A crosshair whose value is written somewhere else makes the reader look away from the
 * place they are pointing at — and the OHLC readout, which used to live here, is now the legend's
 * job and follows the same bar.
 *
 * The vertical rule runs the full height, panes included, so a turn in the price and the reading in
 * the oscillator below it are measured against the same bar.
 *
 * ### The three pointers — item 40
 *
 * [DrawingMode] carries which of the rail's [ToolGroup.MODES] entries is on, and for two of them
 * this is the only thing in the app that acts on it. «نشانگر پیکانی» and «نشانگر نقطه‌ای» were
 * rail entries that set a field nothing read: tapping either changed the state, the button lit,
 * and the chart went on drawing the same full crosshair. What the reader is choosing between is
 * how much of the plot the pointer is allowed to cover, and that is a decision about *these two
 * lines*, so it is decided here:
 *
 * * [DrawingMode.CURSOR] and every mode that is not a pointer — the full crosshair, both rules.
 * * [DrawingMode.ARROW_CURSOR] — no rules at all. For a reader who finds the full-width lines busy
 *   over a dense chart; the tags in the two gutters still say the price and the time, so nothing is
 *   lost except the ink across the candles.
 * * [DrawingMode.DOT] — a small ring at the touch point instead. The pointer a reader wants while
 *   placing anchors on a crowded chart: it says *here* without painting over the two bars either
 *   side of here.
 *
 * The readouts are the same in all three, because the readouts are the answer and only the pointer
 * is a preference.
 */
private fun DrawScope.drawCrosshair(
    view: ChartViewport,
    crosshair: Crosshair,
    plotWidth: Float,
    frame: PlotFrame,
    fullHeight: Float,
    palette: ChartPalette,
    measurer: TextMeasurer,
    decoration: ChartDecoration,
    zone: ZoneId,
    mode: DrawingMode = DrawingMode.CURSOR,
    /**
     * The indicator panes' scales, so a pointer standing in one is read on that pane's scale.
     * Empty on a chart with no panes, which is the common case and costs one `firstOrNull`.
     */
    paneBands: List<PaneBand> = emptyList(),
) {
    val index = crosshair.index.coerceIn(view.firstVisible, view.lastVisible)
    val bar = view.series[index]
    val x = view.xOf(index)
    val y = view.yOf(crosshair.price)
    // Which strip the pointer is standing in, or null for the price plot. See [PaneBand]: below the
    // price plot the price is an extrapolation and printing it is the chart naming a price nobody
    // quoted.
    val band = paneBands.firstOrNull { it.contains(y) }
    // A tighter pattern than the level rules use. The crosshair is transient and has to be
    // distinguishable at a glance from the dashed levels it crosses; a shorter dash is the cheapest
    // way to say "this one is yours and it is not part of the chart".
    val hairline = crispStroke(HAIRLINE_DP.toPx())
    val dash = dashEffect(LineStyleKind.DASHED, hairline)
    when (mode) {
        DrawingMode.ARROW_CURSOR -> Unit
        DrawingMode.DOT -> {
            // A ring rather than a filled disc, so the pointer does not hide the pixel it is
            // pointing at — which on a dot pointer is the entire point of choosing it.
            drawCircle(palette.crosshair, DOT_CURSOR_DP.toPx(), Offset(x, y), style = Stroke(hairline))
            drawCircle(palette.crosshair, hairline, Offset(x, y))
        }
        else -> {
            // Registered like the grid. The crosshair moves under a finger, so an unregistered one
            // does not merely look soft — it *pulses*, alternating between one crisp pixel column
            // and two grey ones as the touch point crosses each boundary. That flicker is the most
            // conspicuous rendering artefact in the whole chart, because it is the one mark the
            // reader is deliberately watching.
            val column = strokeCentre(x, hairline)
            val row = strokeCentre(y, hairline)
            drawLine(palette.crosshair, Offset(column, 0f), Offset(column, fullHeight), hairline, pathEffect = dash)
            drawLine(palette.crosshair, Offset(0f, row), Offset(plotWidth, row), hairline, pathEffect = dash)
        }
    }

    if (!decoration.showAxes || frame.tagGutterWidth <= 0f) return
    // In a pane, the pane's own reading; on the price, the price. The tag is clamped to whichever
    // strip it belongs to, so a reading taken at the top of an RSI does not slide up into the price
    // gutter and sit among numbers measured in dollars.
    if (band != null) {
        drawAxisTag(
            text = formatPrice(band.valueAt(y), band.decimals),
            y = y,
            frame = frame,
            fill = palette.crosshair,
            textColour = TAG_INK,
            measurer = measurer,
            plotHeight = band.top + band.height,
            top = band.top,
            padding = CROSSHAIR_TAG_PADDING_DP,
            inset = CROSSHAIR_TAG_INSET_DP,
        )
    } else {
        drawAxisTag(
            text = view.axisText(crosshair.price),
            y = y,
            frame = frame,
            fill = palette.crosshair,
            textColour = TAG_INK,
            measurer = measurer,
            plotHeight = view.plotHeight,
            padding = CROSSHAIR_TAG_PADDING_DP,
            inset = CROSSHAIR_TAG_INSET_DP,
        )
    }
    if (!decoration.showTimeAxis) return
    // The time under the finger, centred on the rule and held inside the canvas. Without the clamp
    // the label at either end of a scrolled chart hangs half off the edge.
    // The crosshair reads one bar, so it always wants the clock — a reader holding a finger on a
    // candle is asking which candle, and «12 Mar» does not answer that on an hourly chart.
    val stamp = measurer.measure(
        formatTime(bar.t, zone = zone),
        axisStyle(TAG_INK, axisFontSizeSp(isPriceAxis = false)),
    )
    // TradingView's crosshair tags: 24 px tall with 8 px at either side of the text.
    val pad = CROSSHAIR_TAG_PADDING_DP.toPx()
    val side = CROSSHAIR_TAG_INSET_DP.toPx()
    val width = stamp.size.width + side * 2
    val left = (x - width / 2).coerceIn(0f, max(0f, plotWidth - width))
    drawRoundRect(
        color = palette.crosshair,
        topLeft = Offset(left, fullHeight + 1f),
        size = Size(width, stamp.size.height + pad * 2),
        cornerRadius = CornerRadius(TAG_RADIUS_DP.toPx(), TAG_RADIUS_DP.toPx()),
    )
    drawText(stamp, topLeft = Offset(left + side, fullHeight + 1f + pad))
}

/**
 * The eight directions a held angle constraint will accept, drawn from the anchor — item 48.
 *
 * The constraint itself is arithmetic in `DrawingActions.constrain` and was correct long before
 * anything called it. What it had no way to be was *visible*: a latch that only shows itself on the
 * tap after the one that set it is a mode a reader cannot tell they are in, and the first thing
 * they would do is put a point somewhere they did not mean to and conclude the chart is broken.
 *
 * So while the latch is held and there is an anchor to measure from, the eight rays are painted
 * faintly from it — the four axes and the four diagonals, which is exactly what `constrain` rounds
 * to. They are short, hairline and well under the crosshair's own weight: they are scaffolding for
 * the next tap, not a study, and they disappear with it.
 *
 * Which anchors those are is [constraintAnchors]' answer, and it is called per anchor rather than
 * given a list, because the two cases want different counts of it.
 */
private fun DrawScope.drawConstraintSpokes(
    view: ChartViewport,
    anchor: ChartPoint,
    palette: ChartPalette,
) {
    val x = view.xOfTime(anchor.time)
    val y = view.yOf(anchor.price)
    val reach = SPOKE_REACH_DP.toPx()
    val colour = palette.crosshair.copy(alpha = SPOKE_ALPHA)
    val width = HAIRLINE_DP.toPx()
    val dash = dashEffect(LineStyleKind.DASHED, width)
    // An eighth of a turn at a time, which is the step `constrain` rounds to. Written as the same
    // rotation rather than as eight literal offsets, so the picture cannot drift from the maths.
    for (step in 0 until CONSTRAINT_STEPS) {
        val angle = step * (2.0 * PI / CONSTRAINT_STEPS)
        drawLine(
            color = colour,
            start = Offset(x, y),
            end = Offset(
                x + (reach * cos(angle)).toFloat(),
                y + (reach * sin(angle)).toFloat(),
            ),
            strokeWidth = width,
            pathEffect = dash,
        )
    }
}

// ---------------------------------------------------------------------------- helpers

/** Where a touch lands in chart space. */
private fun ChartViewport.crosshairAt(position: Offset): Crosshair =
    Crosshair(index = indexAt(position.x), price = priceAt(position.y))

/**
 * A price as the axis currently prints it — which in two of the four modes is not a price at all.
 *
 * Every label on this canvas that names a level goes through here: the gridline labels, the
 * live-price tag and the crosshair's tag. That is the point of it being one function. The axis used
 * to format prices directly, so switching to [PriceScaleMode.PERCENT] relaid the *spacing* — the
 * ticks already branch on the mode — and then printed dollars against it, which reads as a chart
 * whose scale has come loose from its labels.
 *
 * [places] is the precision the caller worked out from what it is drawing; [ChartViewport.decimals]
 * overrides it, because a reader who pinned five decimals for a venue's tick size means five
 * everywhere and not five where the algorithm agreed.
 */
private fun ChartViewport.axisText(price: Double, places: Int): String {
    val text = formatPrice(scaleValue(price), decimals?.coerceIn(0, AXIS_MAX_DECIMALS) ?: places)
    // Thousands grouped the way TradingView prints them — `77,310.00`, not `77310.00` — on a price
    // scale only. A percentage scale has nothing to group and a comma in `+1.25%` is noise.
    return when (scaleMode) {
        PriceScaleMode.REGULAR, PriceScaleMode.LOGARITHMIC -> groupThousands(text)
        PriceScaleMode.PERCENT, PriceScaleMode.INDEXED_100 -> text
    }
}

/**
 * `77310.00` → `77,310.00`. Latin digits and a Latin comma, because this is a market figure.
 *
 * The sign and the fraction are left alone; only the whole part is grouped, and only when it has
 * more than three digits — `1000` becomes `1,000`, `999.5` stays as it is. Non-numeric text (the
 * `—` of an empty axis) passes through untouched.
 */
internal fun groupThousands(text: String): String {
    val sign = if (text.startsWith("-") || text.startsWith("−") || text.startsWith("+")) text.take(1) else ""
    val body = text.drop(sign.length)
    val dot = body.indexOf('.')
    val whole = if (dot >= 0) body.substring(0, dot) else body
    val fraction = if (dot >= 0) body.substring(dot) else ""
    if (whole.length <= 3 || !whole.all(Char::isDigit)) return text
    val grouped = whole.reversed().chunked(3).joinToString(",").reversed()
    return sign + grouped + fraction
}

/** Whether [price] sits on a round rung — a whole multiple of five ticks. See [drawPriceAxis]. */
internal fun isMajorTick(price: Double, step: Double): Boolean {
    if (step <= 0.0 || !step.isFinite()) return false
    val major = step * MAJOR_TICK_EVERY
    val ratio = price / major
    return abs(ratio - round(ratio)) < MAJOR_TICK_EPSILON
}

private const val MAJOR_TICK_EVERY = 5
private const val MAJOR_TICK_EPSILON = 1e-6

/**
 * The same, for the tags that have no tick step to take a precision from.
 *
 * A percentage wants two places whatever the instrument costs — `+1.25%` is the reading, and
 * `+1.2500%` is four characters of noise in a gutter that is already tight — while a price wants
 * the precision its own magnitude implies.
 */
private fun ChartViewport.axisText(price: Double): String = axisText(
    price = price,
    places = when (scaleMode) {
        PriceScaleMode.PERCENT, PriceScaleMode.INDEXED_100 -> SCALE_VALUE_DECIMALS
        PriceScaleMode.REGULAR, PriceScaleMode.LOGARITHMIC -> decimalsFor(price)
    },
)

/** The ceiling [ChartViewport.decimals] is clamped to, matching the viewport's own. */
private const val AXIS_MAX_DECIMALS = 8

/** How many places a percent or index label carries. See [ChartViewport.axisText]. */
private const val SCALE_VALUE_DECIMALS = 2

/**
 * Whether this tap lands back on the first anchor of a part-drawn path or polyline.
 *
 * The gesture that closes a shape, and the reason it is measured in pixels rather than in chart
 * space is the reason every hit test on this canvas is: "on the anchor" is a finger's width, and a
 * finger is not a number of bars — the same drag that is a comfortable target zoomed out is half
 * the screen zoomed in.
 *
 * Three anchors is the floor, because closing two of them produces a triangle with no third corner:
 * a line drawn out and back along itself. [DrawingActions.closeShape] enforces the same rule, and
 * this refuses earlier so a second tap on the first anchor of a two-point pending shape still reads
 * as an ordinary third tap.
 */
internal fun closesPendingShape(
    state: DrawingState,
    x: Float,
    y: Float,
    view: ChartViewport,
    tolerancePx: Float,
): Boolean {
    val tool = state.tool ?: return false
    if (!DrawingActions.isVariablePoint(tool.id)) return false
    val first = state.pending.firstOrNull() ?: return false
    if (state.pending.size < 3) return false
    val dx = view.xOfTime(first.time) - x
    val dy = view.yOf(first.price) - y
    return dx * dx + dy * dy <= tolerancePx * tolerancePx
}

/**
 * Which of a drawing's own anchors a touch has landed on, or −1.
 *
 * Pixels rather than chart space, for the reason every hit test here is: an anchor is a finger's
 * width, and a finger is not a number of bars. Extracted because the drag that moves a handle and
 * the long press that constrains it have to agree exactly about what "on a handle" means — two
 * copies of this test would be a gesture that constrains one handle and then drags a different one.
 */
internal fun handleIndexAt(
    target: Drawing,
    view: ChartViewport,
    plot: Offset,
    tolerancePx: Float,
): Int = target.points.indexOfFirst { point ->
    val dx = view.xOfTime(point.time) - plot.x
    val dy = view.yOf(point.price) - plot.y
    dx * dx + dy * dy <= tolerancePx * tolerancePx
}

/**
 * The points a held angle constraint will be measured from, for the guide rays — item 48.
 *
 * One while a placement is running: the anchor already down, which is the `from` the next tap is
 * constrained against. Two while a two-point drawing is selected, because the constraint there
 * applies to whichever end the reader grabs and the other one is the pivot — and the canvas cannot
 * know which that will be until the drag starts. Showing both is the honest answer to "what will
 * this do", and it is also the picture: two fans that meet along the line the object already is.
 *
 * Empty in every other state, which is what stops eight rays appearing over a chart with a latch
 * left on and nothing to spend it on.
 */
internal fun constraintAnchors(state: DrawingState): List<ChartPoint> {
    state.pending.lastOrNull()?.let { return listOf(it) }
    val id = state.selectedId ?: return emptyList()
    val target = state.drawings.firstOrNull { it.id == id } ?: return emptyList()
    return if (target.points.size == 2) target.points else emptyList()
}

/**
 * Whether a long press here means «hold it straight» rather than «put a crosshair down» — item 48.
 *
 * Two windows and no others, and the narrowness is the design. The angle constraint needs a
 * modifier a phone does not have, so it has to borrow a gesture; borrowing one costs whatever that
 * gesture did before, and the long press on this canvas is worth a great deal — it is tracking mode,
 * the eraser's whole-object rub-out, the axis menu and the alert offer.
 *
 * * **Mid-placement**, with an anchor already down. The long press here used to enter tracking
 *   mode, which is a crosshair the very next tap has to dismiss instead of placing the point the
 *   reader was in the middle of placing. It was never worth anything in this window.
 * * **On a handle** of the selected drawing, with nothing armed. A twelve-pixel target on a shape
 *   the reader has already selected is not somewhere anybody presses to read a price, and it is
 *   exactly where they are about to drag.
 *
 * Everywhere else on the plot the long press still means what it always meant.
 */
internal fun constrainableAt(
    state: DrawingState,
    view: ChartViewport,
    plot: Offset,
    tolerancePx: Float,
): Boolean {
    if (state.tool != null) return state.pending.isNotEmpty()
    val id = state.selectedId ?: return false
    val target = state.drawings.firstOrNull { it.id == id } ?: return false
    if (target.points.size != 2) return false
    return handleIndexAt(target, view, plot, tolerancePx) >= 0
}

/**
 * What the eraser does to whatever is under the finger, or null when it is over nothing.
 *
 * Null rather than the unchanged state, so the caller emits nothing at all on a miss: a tap on empty
 * chart with the eraser armed should not push a state through the whole persistence path to say
 * that nothing happened.
 *
 * [whole] is the difference between the two gestures, and it is the difference the eraser exists
 * for. A tap takes out the one leg under the finger — a brush stroke that overshot loses the
 * overshoot and keeps the rest, which is what a reader means by rubbing something out. A long press
 * takes the object, which is what they mean when the whole thing was a mistake. A drawing that is
 * not a chain has no legs to take out, and [DrawingActions.erasePartial] already answers that with
 * the whole object. A tap that found a chain but not one of its legs — near the shape, between two
 * of its corners — changes nothing, which is why the null answer is worth having: erasing the whole
 * stroke would be a far larger answer than the question.
 */
internal fun eraseAt(
    state: DrawingState,
    x: Float,
    y: Float,
    view: ChartViewport,
    tolerancePx: Float,
    whole: Boolean,
): DrawingState? {
    val target = DrawingHitTest.at(
        drawings = state.drawings,
        x = x,
        y = y,
        view = view,
        tolerancePx = tolerancePx,
    ) ?: return null
    if (whole) return DrawingActions.erase(state, target.id).takeIf { it != state }
    // −1 is passed straight through rather than short-circuited, because it is not always a miss:
    // `erasePartial` deletes a non-chain drawing whole before it ever looks at the index, so a tap
    // on a horizontal level with the eraser armed removes the level — which is the only thing it
    // could sensibly mean. On a chain it refuses, and the null keeps the miss silent.
    val segment = DrawingActions.segmentAt(target, x, y, view, tolerancePx)
    return DrawingActions.erasePartial(state, target.id, segment).takeIf { it != state }
}

/**
 * Where a touch lands in chart space, with nothing done to it.
 *
 * The input to both the magnet and to [DrawingActions.tapSnapped], which does its own snapping and
 * must therefore be handed the unsnapped point: snapping twice is harmless arithmetic, but snapping
 * *before* handing it over is what threw the channel away.
 */
private fun ChartViewport.rawChartPointAt(position: Offset): ChartPoint =
    ChartPoint(timeAt(position.x), priceAt(position.y))

/**
 * Where a touch lands as a drawing point, snapped to a bar's OHLC by the magnet.
 *
 * The snap is against the series being displayed, not the raw one. On a price-driven chart type
 * that means it snaps to a Renko brick or a Kagi turn, which is the right answer there and is what
 * every terminal does — but it is worth knowing that such a point is anchored to a synthetic
 * timestamp, and will not survive a switch back to candles unchanged.
 *
 * This takes the [MagnetMode] rather than a boolean, and that is the whole of the difference
 * between a magnet and a magnet worth having. It used to take `magnet: Boolean` and call the
 * strong snap whenever it was true, which meant two things went wrong at once: the reader's choice
 * of a *weak* magnet was ignored, so a text label placed anywhere near a bar was dragged onto a
 * wick; and the channel the snap chose — open, high, low or close — was discarded on the way out,
 * so nothing downstream could ever record which of the four prices the reader had actually aimed
 * at.
 *
 * The channel matters because it is what survives a revision. TradingView persists *which OHLC
 * channel a point bound to*, not the price it happened to land on, and the reason is that a feed
 * revises bars: a session resent, a bad tick corrected, a daily bar closed properly at the end of
 * the day. A trend line anchored to "the low of Tuesday" moves with Tuesday's low when it is
 * corrected and keeps touching it; one anchored to the number that low happened to be at the moment
 * of the tap is left hanging a few ticks off the very thing it was drawn against. That difference —
 * and not the convenience of landing on a round number — is why the magnet is worth having at all,
 * and it is [DrawingActions.resnap] that spends the binding.
 *
 * A drag path still comes through here rather than through [DrawingActions.tapSnapped]: a freehand
 * stroke and a dragged handle are hundreds of points a frame apart, and binding every one of them
 * would fill the binding map with entries for a stroke nobody will ever re-snap. Taps are what bind.
 */
private fun ChartViewport.chartPointAt(
    position: Offset,
    series: CandleSeries,
    magnet: MagnetMode,
): ChartPoint = DrawingActions.snap(rawChartPointAt(position), series, magnet).point

/**
 * The style every label on this canvas is measured in.
 *
 * Left-to-right explicitly. The measurer takes its direction from the composition, so on a Persian
 * device an axis label was laid out as a right-to-left paragraph — and a leading minus sign is a
 * neutral character, so a MACD pane's lower bound printed as `4.92-`. The digits are Latin market
 * figures and they read in one direction only.
 *
 * The size defaults to the price axis'. That is deliberate: this style is the chart's ordinary
 * label style — the legend, a pane's title, a level's name — and all of those are readings rather
 * than context. Only the dates along the bottom take the smaller one, by asking for it. See
 * [axisFontSizeSp].
 *
 * [bold] is for the one thing on this canvas that earns a second weight: a time-axis tick that
 * opens a new month or year.
 */
internal fun axisStyle(
    colour: Color,
    sizeSp: Float = axisFontSizeSp(isPriceAxis = true),
    bold: Boolean = false,
) = TextStyle(
    color = colour,
    fontSize = sizeSp.sp,
    fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
    // Inter with tabular figures: the axis is a column of numbers, and a column of numbers has
    // to line up — and a price label that changes from 2,574.9 to 2,575.0 must not move.
    fontFamily = CoineProLatinFontFamily,
    fontFeatureSettings = TABULAR_FIGURES,
    textDirection = TextDirection.Ltr,
)

/**
 * A dash pattern, or none at all.
 *
 * The wrapper exists for one reason: [dashIntervals] returns an empty array for a solid line, and
 * `PathEffect.dashPathEffect` with an empty array throws. Every caller would otherwise need the
 * same guard, and the one that forgot it would crash inside a draw pass.
 */
/**
 * How far back the viewport sits when a given bar is the one being looked at.
 *
 * [ChartViewport.offset] counts *back from the live edge*, and a go-to-date result counts forward
 * from the oldest bar, so the two are mirror images of each other and getting the mirror wrong
 * lands the reader the same distance the wrong side of the chart. Its own function because that is
 * the whole of the arithmetic and it is worth a test that does not need a composition.
 *
 * Clamped at zero here, and at the far end by [ChartViewport.atOffset], which knows how many bars
 * there are to be short of: a date past the last bar is the live edge, and one before the first is
 * as far back as the series goes.
 */
internal fun focusOffset(size: Int, index: Int): Int = (size - 1 - index).coerceAtLeast(0)

/**
 * The viewport a new series should produce, given where the reader is and what was saved.
 *
 * ### The bug this shape exists to stop
 *
 * «چارت اصلا روش اسکرول میکنم به عقب برمیگرده دیگه به جلو برنمیگرده» — panning back is sticky and
 * panning forward makes no progress at all.
 *
 * The saved trio is written from a `LaunchedEffect`, which runs *after* composition, and was read
 * from a `remember(display)` block, which runs *during* it. So whenever a series replacement landed
 * in the same frame as a pan, the restore threw the reader's movement away and put them back where
 * they had been a frame earlier. Panning back, the stale offset is behind them, so they still crept
 * forward and it merely felt sticky; panning *forward*, the stale offset is also behind them — which
 * is precisely where they are trying to leave — so every frame undid the drag and the chart could
 * not be moved right at all. It only became visible when the candle archive started answering
 * `loadMore` from disk, which replaces the series on almost every frame near the left edge instead
 * of once per network round trip.
 *
 * ### The rule
 *
 * The saved trio restores a *fresh composition* — process death, a rotation, coming back from the
 * studio — and nothing else. After that first pass [ChartViewport.withSeries] alone decides what a
 * new series does to a reader who has panned, which is the job it was written for and which it does
 * correctly at both ends: it follows the live edge when the reader is on it, and re-anchors on the
 * right-hand bar's own timestamp when they are not, so a prepend of a thousand bars moves nothing.
 *
 * A symbol switch is unaffected: it arrives as a new series through the same `withSeries`, which
 * keeps the reader on the same *moment* rather than the same bar count, and that is what switching
 * instrument on a chart should do.
 */
internal fun seedViewport(
    current: ChartViewport,
    series: CandleSeries,
    seeded: Boolean,
    savedZoom: Int,
    savedOffset: Int,
    savedPriceZoom: Float,
    restAtEdge: Boolean,
): ChartViewport {
    val followed = current.withSeries(series)
    if (seeded) return followed
    val restored = followed.copy(barsPerView = savedZoom, priceZoom = savedPriceZoom)
    return when {
        savedOffset != UNSET_OFFSET -> restored.atOffset(savedOffset)
        restAtEdge -> restored.atRest()
        else -> restored.atOffset(0)
    }
}

internal fun dashEffect(style: LineStyleKind, lineWidth: Float): PathEffect? {
    val intervals = dashIntervals(style, lineWidth)
    return if (intervals.isEmpty()) null else PathEffect.dashPathEffect(intervals)
}

/**
 * How many decimals a price on this chart deserves.
 *
 * Taken from the magnitude rather than fixed, for the same reason the search rows do it: gold at
 * 2,643.18 and a memecoin at 0.000018 cannot share a format, and rounding either to the other's
 * precision makes the number wrong rather than merely ugly.
 */
fun decimalsFor(price: Double): Int {
    val magnitude = abs(price)
    return when {
        magnitude >= 1_000 -> 1
        magnitude >= 1 -> 2
        magnitude >= 0.01 -> 4
        else -> 6
    }
}

/**
 * Latin digits, always.
 *
 * Through [NumberStyle], the one place the digit policy lives. Without it `String.format` follows
 * the device locale, and on a Persian phone — which is this app's default — a price comes out as
 * «۲٬۵۹۲٫۶»: Persian digits and a Persian decimal separator, on the axis of a chart. Market figures
 * stay Latin and comparable down a column; only prose counts are written in Persian digits.
 */
fun formatPrice(value: Double, decimals: Int): String = NumberStyle.fixed(value, decimals)

/**
 * When a bar opened, in the reader's own zone, at the precision the axis can use.
 *
 * ### Two things were wrong with `HH:mm`
 *
 * It was **UTC**. A bar's open time arrives as an epoch and the axis printed the hour of that
 * epoch, so a reader in Tehran was shown London's clock under their own candles — three and a half
 * hours out, silently, on the one row of the chart whose job is to say *when*.
 *
 * And on a daily or weekly chart every bar opens at midnight, so every label on the axis read
 * `00:00`. Five identical labels is not a time axis; it is five copies of the same non-answer.
 *
 * So the format follows the spacing: minutes and hours while the labels are within a day of each
 * other, day and month once they are further apart than that, and the year as well once they span
 * more than one. [spanSeconds] is what the axis is actually showing, not the timeframe — the same
 * daily chart zoomed into a week and zoomed out to five years wants different labels.
 *
 * Latin digits, for the same reason [formatPrice] uses them: this is a market figure.
 *
 * [zone] defaults to the device's, which is the only honest answer for a caller that has no chart to
 * take a zone from. The canvas always passes its own — see the composable's `zone` parameter — so
 * the label and the bold month boundary above it can never be read in two different zones again.
 */
internal fun formatTime(
    epochSeconds: Long,
    spanSeconds: Long = 0L,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val zoned = Instant.ofEpochSecond(epochSeconds).atZone(zone)
    val pattern = when {
        spanSeconds >= SPAN_MULTI_YEAR -> "MMM yy"
        spanSeconds >= SPAN_MULTI_DAY -> "d MMM"
        else -> "HH:mm"
    }
    // The Latin locale formats the month name as well, so a Persian device gets "12 Mar" rather
    // than a Gregorian month rendered in Persian script — which would read as a Jalali date and
    // be wrong by eleven days.
    return zoned.format(DateTimeFormatter.ofPattern(pattern, Locale.US))
}

/**
 * A time-axis label, in the shape the boundary it stands on calls for.
 *
 * This is the second half of what makes the axis read as a calendar. The ladder decides *where* the
 * labels go; this decides what each one says, and the rule is that a label names the boundary it
 * opens and nothing more:
 *
 *  * A year boundary is the year. `2026`, not `1 Jan`.
 *  * A month boundary is the month, with the year attached only once the window spans more than
 *    one — on a two-year chart `Mar` alone is ambiguous and on a two-month chart the year is noise.
 *  * A week or a day is the date. `12 Mar`.
 *  * An hour or a minute is the clock. `14:00`.
 *
 * The old axis picked one pattern for every label from the visible span, so a chart spanning a
 * week printed `d MMM` on all five — including the one that opened January, which then read
 * `1 Jan` where every terminal on earth reads `2026`. Falls back to [formatTime] for a tick with no
 * boundary, which is the even-spread case.
 */
internal fun formatTimeTick(
    tick: TimeTick,
    spanSeconds: Long,
    zone: ZoneId,
    /**
     * Write the dates in Solar Hijri, which is what a Persian reader's calendar actually is.
     *
     * ### Why this parameter exists, when the note below says the opposite
     *
     * That note was right about the danger and wrong about the remedy. Rendering a *Gregorian*
     * month in Persian script — «مارس» over a bar that opened on 12 March — is indeed a date wrong
     * by eleven days, and refusing to do it was correct. But the conclusion drawn from it, that the
     * axis must therefore print `12 Mar`, made this the **only** surface in the app on a Gregorian
     * calendar. Every other date a reader sees — the economic calendar's rows, a headline's
     * timestamp, the activity log, a signal's age — is Solar Hijri through [PersianDateTime]. So a
     * reader comparing a CPI release at «۶ اسفند» against the candle it moved was asked to convert
     * a calendar in their head, on the one screen where the whole point is lining two things up.
     *
     * The answer is neither of the two the note considered: convert the date properly and then name
     * the month it lands in. «۱۲ اسفند» is the same instant as `2 March`, not a transliteration of
     * it, and [JalaliDate] is the same conversion the rest of the app has always used.
     *
     * The clock is untouched in both calendars. See [PersianDateTime]: `14:30` is a market figure
     * read against MetaTrader and LBank, and it stays Latin wherever it appears.
     */
    jalali: Boolean = false,
): String {
    if (jalali) return persianTimeTick(tick, spanSeconds, zone)
    val pattern = when (tick.unit) {
        TimeTickUnit.YEAR -> "yyyy"
        TimeTickUnit.MONTH -> if (spanSeconds >= SPAN_MULTI_YEAR) "MMM yy" else "MMM"
        TimeTickUnit.WEEK, TimeTickUnit.DAY -> "d MMM"
        TimeTickUnit.HOUR, TimeTickUnit.MINUTE -> "HH:mm"
        null -> return formatTime(tick.time, spanSeconds, zone)
    }
    // `Locale.US` for the same reason [formatTime] uses it: a Gregorian month name rendered in
    // Persian script reads as a Jalali date and is wrong by eleven days.
    return Instant.ofEpochSecond(tick.time)
        .atZone(zone)
        .format(DateTimeFormatter.ofPattern(pattern, Locale.US))
}

/**
 * The same label, in Solar Hijri, shaped for the boundary it stands on.
 *
 * The four cases mirror the Gregorian ones exactly, so the axis has the same rhythm in both
 * calendars: a year is a year, a month is a month with the year attached only across a long window,
 * a day is a day and a month, and anything finer is the clock.
 *
 * A date outside [JalaliDate]'s break table returns [PersianDateTime.UNREPRESENTABLE] rather than
 * throwing, which matters here more than anywhere: this runs inside a draw pass, where an exception
 * is a dead frame and then a dead app, and a synthetic timestamp on a Renko bar is exactly the kind
 * of value that reaches it. The axis prints an em dash for that one label and carries on.
 */
private fun persianTimeTick(tick: TimeTick, spanSeconds: Long, zone: ZoneId): String {
    val moment = Instant.ofEpochSecond(tick.time)
    val date = moment.atZone(zone).toLocalDate()
    val day = JalaliDate.fromGregorianOrNull(date) ?: return PersianDateTime.UNREPRESENTABLE
    return when (tick.unit) {
        TimeTickUnit.YEAR -> day.year.toPersianDigits()
        TimeTickUnit.MONTH ->
            if (spanSeconds >= SPAN_MULTI_YEAR) day.monthName + " " + day.year.toPersianDigits()
            else day.monthName
        TimeTickUnit.WEEK, TimeTickUnit.DAY -> day.formatShort()
        TimeTickUnit.HOUR, TimeTickUnit.MINUTE -> PersianDateTime.clock(moment, zone)
        // The even-spread case, which is the one place the *span* rather than the boundary decides.
        null -> when {
            spanSeconds >= SPAN_MULTI_YEAR -> day.monthName + " " + day.year.toPersianDigits()
            spanSeconds >= SPAN_MULTI_DAY -> day.formatShort()
            else -> PersianDateTime.clock(moment, zone)
        }
    }
}

/** Beyond this much visible time, an axis label is a date rather than a clock. */
private const val SPAN_MULTI_DAY = 60L * 60 * 24 * 3

/** And beyond this, a month and a year rather than a day. */
private const val SPAN_MULTI_YEAR = 60L * 60 * 24 * 400

/** The volume band, as a share of the canvas. */
/**
 * How much of the price pane the tallest visible volume bar reaches.
 *
 * A fifth. Enough to compare one bar against its neighbours, short enough that the candles above it
 * are never in doubt about which series the chart is about.
 */
private const val VOLUME_INLINE = 0.20f

/**
 * How much of the plot's width the widest row of a volume profile reaches. See
 * [drawVolumeProfileRows].
 *
 * A fifth, deliberately the same share the inline volume band takes of the height. The two are the
 * same claim about the same number seen from two directions, and a chart on which one of them is
 * twice as loud as the other reads as though it meant something by it.
 */
private const val PROFILE_SPAN = 0.20f

/** The gap between two rows of the profile, so the bars read as bars rather than as a block. */
private val PROFILE_ROW_GAP_DP = 1.dp

/**
 * The radius of the dot pointer's ring. See [drawCrosshair].
 *
 * Small enough that the ring is a mark rather than a target — a reader who picked the dot over the
 * full crosshair picked it to stop the pointer covering the chart, and a fat circle would be the
 * same complaint in a rounder shape.
 */
private val DOT_CURSOR_DP = 5.dp

/** How far the constraint's guide rays reach from the anchor. See [drawConstraintSpokes]. */
private val SPOKE_REACH_DP = 44.dp

/** Faint: the rays are scaffolding for the next tap and must not read as a drawn object. */
private const val SPOKE_ALPHA = 0.45f

/** Eighths of a turn — the four axes and the four diagonals `DrawingActions.constrain` rounds to. */
private const val CONSTRAINT_STEPS = 8

/**
 * The three weights of a profile row: the point of control, the rest of the value area, the tails.
 *
 * All well under half, because the whole bar has to be seen through — a row that hides the candle
 * behind it has traded the subject of the chart for a study of it. The ladder is what carries the
 * shape of the value area without the reader tracing its two dashed edges.
 */
private const val PROFILE_POC_ALPHA = 0.34f
private const val PROFILE_AREA_ALPHA = 0.22f
private const val PROFILE_TAIL_ALPHA = 0.12f

/**
 * The most of the canvas indicator panes may take between them.
 *
 * Half. Past that the candles stop being the subject of the picture, and a reader with four
 * oscillators switched on has usually forgotten they switched on the fourth.
 */
private const val PANE_BUDGET = 0.5f


/**
 * How far a label chip's rounded corners are cut.
 *
 * Two, and only on the side facing the plot — see [drawAxisChip]. Three read as a pill and a pill
 * floats; two reads as a corner that has been eased, which is what a chip attached to an axis
 * should look like.
 */
private val TAG_RADIUS_DP = 2.dp

/**
 * The saved pan position of a chart nobody has panned. See `savedOffset`.
 *
 * [Int.MIN_VALUE] rather than a negative number that could be a real position: every value from
 * `-barsPerView / 2` upward is somewhere a reader can legitimately be.
 */
internal const val UNSET_OFFSET = Int.MIN_VALUE

/**
 * How close to the oldest loaded bar a reader has to get before more history is fetched.
 *
 * Ten bars, not zero. At zero the reader hits the wall, waits for a round trip and watches the
 * chart jump; ten bars ahead is roughly a flick of the thumb, which is enough for the request to
 * land before they arrive. Larger would fetch history nobody asked for on a chart that opened and
 * was never panned — the default view already reaches back a hundred and twenty bars.
 */
private const val LOAD_MORE_MARGIN = 10


/**
 * How far left of the price axis a finger still counts as being on it.
 *
 * The axis is 56dp of labels; a tap target of that width alone is under the 48dp minimum once the
 * padding is taken off, and readers aim at the numbers rather than at the edge. Twelve more is
 * enough to be reachable without eating a meaningful strip of the plot.
 */
private val GUTTER_REACH_DP = 12.dp

/**
 * How much of the plot's height a full drag is worth.
 *
 * Two: dragging from the top of the chart to the bottom roughly triples the scale, which is about
 * one comfortable thumb travel for one useful change. Higher and a small correction overshoots.
 */
private const val GUTTER_SENSITIVITY = 2f

/**
 * Above this the countdown stops being a countdown.
 *
 * Two days: a weekly or monthly bar has days to run and "3d" tells a reader nothing they did not
 * know. It is also the guard against a stale feed — a bar whose "close" is a week away means the
 * series is old, not that the market is slow.
 */
private const val MAX_COUNTDOWN_SECONDS = 2L * 86_400

/**
 * What this chart prints where a number is not available: the empty set.
 *
 * Not «N/A», which is English on a Persian screen and an abbreviation of a phrase the reader has
 * never seen spelled out. Not a dash either, which is the harder mistake to see: a dash in a column
 * of signed market figures is a minus sign, and a countdown reading `—` has been read as a negative
 * one more than once. `∅` is neither a number nor a word in any of this app's languages, it is the
 * same glyph in Persian and Latin runs, and it means exactly what it looks like.
 */
internal const val NO_VALUE = "∅"


/**
 * The multiples a log axis puts a line at, once per decade.
 *
 * One, two and five. Three lines a decade is the density every terminal uses and it is not
 * arbitrary: they are the round numbers a reader reads a logarithmic scale by, and they sit at
 * roughly even intervals in log space (0, 0.30 and 0.70 of a decade).
 */
private val LOG_MULTIPLES = listOf(1.0, 2.0, 5.0)

/** Below this many lines a log axis is not a grid, and the linear ladder is used instead. */
private const val MIN_LOG_TICKS = 3

/**
 * How far the reader may shrink or grow the indicator panes.
 *
 * A third to three times. Below a third an oscillator is a coloured smear with no readable scale —
 * worse than switching it off, because it still costs the candles height. Above three times the
 * `PANE_BUDGET` cap is doing all the work anyway, so a larger number would be a control that
 * stopped responding.
 */
/**
 * How much of the plot's height a full drag is worth.
 *
 * One and a half: dragging the divider to the top of the candles roughly doubles the panes. Higher
 * would make a small correction overshoot straight into the `PANE_BUDGET` cap, where the control
 * appears to stop working.
 */
private const val DIVIDER_SENSITIVITY = 1.5f

/**
 * How much of the plot's width a full drag along the time axis is worth.
 *
 * Two: dragging from one edge of the chart to the other roughly triples the bar count in one
 * direction and thirds it in the other, which is one comfortable thumb travel for one useful change
 * of zoom. It is the same number as [GUTTER_SENSITIVITY] on purpose — the two gestures are the same
 * gesture on different axes, and a reader who has learned the feel of one should find the other
 * already familiar.
 */
private const val TIME_AXIS_SENSITIVITY = 2f

/**
 * Bounds on the zoom factor a drag may accumulate before it is spent on the bar count.
 *
 * The residue is a *product*, so a drag that never crosses a whole-bar boundary would otherwise
 * compound without limit and hand the viewport an absurd factor the moment it finally did. A
 * hundredth to a hundred covers every drag a finger can make in one gesture and cannot overflow.
 */
private const val MIN_SCALE_RESIDUE = 0.01f
private const val MAX_SCALE_RESIDUE = 100f

private const val MIN_PANE_SCALE = 0.33f
private const val MAX_PANE_SCALE = 3f

/** How solid the last-price rule is. Present, and never competing with the candles. */
private const val LAST_PRICE_ALPHA = 0.75f

/**
 * And how faint the previous session's close is.
 *
 * Well under the live price's, because the two are different kinds of claim: one is where the
 * market is right now, the other is a fixed reference the whole day is measured against. Drawn at
 * the same weight the pair would read as two live prices, which is the one reading this line must
 * not produce.
 */
private const val PREVIOUS_CLOSE_ALPHA = 0.42f

/**
 * Rows the corner legend will print before it starts counting instead.
 *
 * Four, and it was two, and the number that changed underneath it is the row height rather than
 * this. The argument for two was that four rows of legend — the OHLC line, the change, and two
 * studies, *each with its own visibility, settings and remove glyph* — covered the top third of the
 * plot with chrome on the one screen whose product is the plot. That was true, and the glyphs were
 * the reason: they lifted every study row from the height of its own text to a 24dp button.
 *
 * They are now behind a disclosure on the head row — see `ChartLegendOverlay` — so a study row is
 * as tall as the words in it and four of them cost less ink than two did. Which is the shape the
 * complaint had all along: the reader was not being shown too much of their own chart, they were
 * being shown too much furniture around too little of it.
 *
 * A reader who wants every study's value at a bar still puts the crosshair down, and that is what
 * [TRACKING_LEGEND_LINES] is for: there the legend has stopped being a legend and become the
 * reading, and ten rows is the right answer to a question actually asked.
 *
 * The count that replaces the hidden rows is not a loss — «+۹» is one glyph saying there are nine
 * more, which is what the reader needs to know before deciding to ask.
 */
internal const val LEGEND_LINES = 4

/**
 * And how many it will print in tracking mode.
 *
 * Ten rather than four, because in tracking mode the legend has stopped being a legend and become
 * the reading: the reader has deliberately asked what every study says about one bar, and answering
 * with the first four of them and a «+5» is answering a different question. Ten is where a phone
 * runs out of height anyway, and the budget below stops it before that on a short chart.
 */
internal const val TRACKING_LEGEND_LINES = 10

/** The share of the plot the reading may occupy while the crosshair is down. See [LEGEND_BUDGET]. */
internal const val TRACKING_LEGEND_BUDGET = 0.5f

/**
 * Clear space between two price-axis labels before they count as colliding.
 *
 * Two density-independent pixels on top of the line height. Zero would let two labels sit with
 * their bounding boxes exactly touching, which is legible in a font sample and not on a chart —
 * the descender of one number and the digits of the next need daylight between them or the pair
 * reads as a single smeared row.
 */
private val LABEL_SEPARATION_DP = 2.dp

/** Between two rows of the corner legend. */
internal val LEGEND_GAP_DP = 2.dp

/**
 * How far the corner legend sits from the canvas edge.
 *
 * Wider than the axis padding: the legend's first glyph is a letter and the axis's is a digit, and
 * four pixels that read as tight beside a number read as clipped beside an «O».
 */
// Nine, measured: TradingView's phone legend starts nine points in from the pane's left edge.
internal val LEGEND_INSET_DP = 9.dp

/** The share of the plot's height the corner legend may occupy before it stops adding rows. */
internal const val LEGEND_BUDGET = 0.25f

/** Breathing room between the legend's text and the edge of the plate behind it. */
internal val LEGEND_PLATE_PADDING_DP = 5.dp

/** How much of the chart shows through the legend's plate. */
// Nothing. TradingView draws its legend straight onto the pane with no plate behind it, and the
// 82% stage-coloured panel this app drew over the top-left of the plot was the single most visible
// difference between the two charts at a glance.
internal const val LEGEND_PLATE_ALPHA = 0f

/** The plate's corner. Softer than a tag's, because it is a panel rather than a marker. */
internal val LEGEND_PLATE_RADIUS_DP = 6.dp

/** Padding inside the last-price tag. */
private val TAG_PADDING_DP = 3.dp

/**
 * The crosshair's tags, measured on TradingView: 24 px tall — a 16 px line of 12 px type with 4 px
 * above and below — and 8 px from the tag's edge to the text on either side.
 */
private val CROSSHAIR_TAG_PADDING_DP = 4.dp
private val CROSSHAIR_TAG_INSET_DP = 8.dp

/**
 * The live tag's second line — the countdown — as TradingView's phone sets it: a size down from the
 * price and lighter. 12 pt under a 14 pt price there; the same ratio against this axis' 12 sp here.
 */
private fun tagSecondLineStyle(ink: Color) =
    axisStyle(ink.copy(alpha = TAG_SECOND_LINE_ALPHA), TAG_SECOND_LINE_SP)

private const val TAG_SECOND_LINE_SP = 10.5f
private const val TAG_SECOND_LINE_ALPHA = 0.7f

/** Breathing room above and below a pane's extremes, so the line never touches the lid. */
private const val PANE_PADDING = 0.06

/**
 * How many horizontal divisions the axis aims for, given how tall the plot actually is.
 *
 * ### The number that was wrong, and why it looked like nothing was
 *
 * This was a flat five. Five is a reasonable count for a chart in a card — the signal detail's
 * inline plot, the setup preview — and it is the wrong count for the chart *screen*, which since
 * the plot was taken to seventy per cent of the display is around six hundred device-independent
 * pixels tall. Five divisions of that is a gridline every hundred and twenty points and four
 * numbers down the whole axis, which is not a scale a reader can read a level off: to place a price
 * between `2560.0` and `2570.0` they have to measure by eye across four centimetres of empty black.
 *
 * A count that does not follow the height is also the reason the same constant could not be right
 * for both: the inline chart got a sensible five and the full screen got the same five stretched
 * over four times the height.
 *
 * ### The rule
 *
 * One division per [GRID_PITCH_DP], clamped. The pitch is the distance at which a label and the
 * line under it read as a scale rather than as decoration — close enough that the eye interpolates
 * between two of them instead of measuring, far enough that the numbers are not a ladder of noise
 * and the price axis' own labels never collide. Every terminal lands in the same band; this is at
 * the roomier end of it.
 *
 * The clamps matter more than the pitch. [MIN_GRID_ROWS] keeps a short inline chart from resolving
 * to one line or none, which is a plot with no scale at all; [MAX_GRID_ROWS] keeps a tall plot in a
 * large-text accessibility setting from asking for more labels than the axis can print without
 * [separateLabels] pushing them apart.
 *
 * A zero density is the caller that has none to give — the tests, which assert the ladder rather
 * than the count — and falls back to the old flat five so nothing about their expectations moves.
 */
private fun gridRows(plotHeight: Float, density: Float): Int {
    if (density <= 0f || plotHeight <= 0f) return GRID_ROWS
    val dp = plotHeight / density
    return (dp / GRID_PITCH_DP).roundToInt().coerceIn(MIN_GRID_ROWS, MAX_GRID_ROWS)
}

/** The fallback count, and the one a chart with no density to measure by still gets. */
private const val GRID_ROWS = 5

/** See [gridRows]. One horizontal division per this many device-independent pixels. */
private const val GRID_PITCH_DP = 76f

private const val MIN_GRID_ROWS = 3
private const val MAX_GRID_ROWS = 12

/** A ceiling on the tick loop, so a degenerate range cannot spin inside a draw pass. */
private const val MAX_TICKS = 24

/** As many decimals as any instrument this app quotes deserves. */
private const val MAX_DECIMALS = 8
private const val GRID_COLUMNS = 5

/**
 * The most labels the time axis will print, however many boundaries the window offers.
 *
 * Five was a fixed count and is now a ceiling, which is the difference the ladder makes: a window
 * holding two month boundaries and nothing else gets two labels rather than five, three of which
 * would have been arbitrary moments padding out a row. `TimeScale` fills up to this from the
 * coarsest boundary down and stops when it runs out of room or of boundaries.
 */
private const val MAX_TIME_LABELS = 6

/** Minimum clear space between two time labels before the later one is dropped. */
private const val LABEL_GAP = 12f

/**
 * The label the time axis is measured against when it decides how many will fit.
 *
 * A real date rather than a count of characters: the font is IRANYekanX and its Latin digits are
 * not the same width as its letters, so `"30 Sep"` measured is the only honest answer to how wide
 * a date is. Never drawn — this string exists to be measured.
 */
private const val TIME_LABEL_SAMPLE = "30 Sep"

/**
 * Every stroke on this chart, in **dp**, and that is the fix rather than a tidy-up.
 *
 * These were raw pixels. On a 3× phone `WICK_WIDTH = 1.4f` is 0.47dp — thinner than a hairline,
 * thinner than the platform can draw honestly — so a candle's wick rendered as a grey suggestion
 * while the same file's marker sizes, which *were* density-scaled, came out right. One renderer,
 * two unit systems, and the half that was wrong is the half a trader looks at.
 *
 * The hairline is 0.8dp rather than 1: a full point is heavier than a gridline should be against
 * candles, and Compose resolves the fraction to a real subpixel rather than rounding it away.
 */
internal val HAIRLINE_DP = 0.8.dp
internal val LINE_WIDTH_DP = 1.6.dp
// Ten, measured: TradingView sets its price labels 10 css px in from the axis edge (tick 5 + inner
// padding 5, in Lightweight Charts' own terms), and the live-price tag's text at the same x.
internal val AXIS_PADDING_DP = 10.dp
// Opaque: the template colour *is* the grid colour, measured as #282828 on TradingView's #0F0F0F.
// It used to be a 12% white at 35% alpha — four percent of ink, which is a grid nobody can see.
private const val GRID_ALPHA = 1f

/** The frame around the plot: the grid's colour, firm enough to be an edge and still a neutral. */
private const val FRAME_ALPHA = 0.9f
// TradingView's volume histogram: the candle colour at half strength, measured #1A5A54 on #0F0F0F.
private const val VOLUME_ALPHA = 0.5f
private const val ZONE_ALPHA = 0.12f

/** How opaque the plate behind an in-plot level label is. See [drawLevelLabel]. */
private const val LEVEL_PLATE_ALPHA = 0.8f

/**
 * The dot on the entry bar of a setup.
 *
 * Two points, which is a shade under a wick at the default zoom: enough to find, small enough that
 * it does not hide the candle it is identifying.
 */
private val ENTRY_MARK_DP = 2.dp

/** How far a marker sits from the bar's high or low, so it points rather than covers. */
private const val MARKER_CLEARANCE = 8f
private const val MARKER_SIZE = 7f

/** Below this a pinch is a drag with slightly uneven fingers, not an intent to zoom. */
private const val ZOOM_DEADZONE = 0.01f

/**
 * How far the fingers must straddle an axis before a pinch is allowed to scale it.
 *
 * Two fingers held side by side have a vertical span of a few pixels, and the ratio of two
 * near-zero numbers is noise. Below this the axis is left alone — which is also the right answer
 * for the gesture, because a reader whose fingers are level is asking about time.
 */
private val PINCH_AXIS_FLOOR_DP = 12.dp

/** How long the handle takes to grow under a finger, and to come back down after it lifts. */
private const val HANDLE_GRAB_MS = 200

/**
 * The mean distance of the pressed pointers from their centroid along one axis.
 *
 * The per-axis counterpart of Compose's `calculateCentroidSize`, which measures the same thing as a
 * radius and so cannot tell a horizontal pinch from a vertical one. See the pinch observer in
 * [CoineProChart] for why that distinction is the whole feature.
 */
private fun PointerEvent.axisSpan(vertical: Boolean): Float {
    val centroid = calculateCentroid(useCurrent = true)
    if (centroid == Offset.Unspecified) return 0f
    var total = 0f
    var counted = 0
    changes.forEach { change ->
        if (change.pressed) {
            val position = change.position
            total += abs(if (vertical) position.y - centroid.y else position.x - centroid.x)
            counted++
        }
    }
    return if (counted == 0) 0f else total / counted
}

/**
 * How far the picture may be pulled past the end of the history.
 *
 * Forty points is the distance every scrolling surface on the phone uses, and the number matters
 * less than the fact that it is small: this is a signal that there is nothing more, not a second
 * way to pan.
 */
private val EDGE_PULL_MAX_DP = 40.dp

/** The share of refused travel the band takes at rest. Falls to nothing as the band stretches. */
private const val EDGE_PULL_GAIN = 0.45f

/**
 * How much of the chart a change has actually invalidated.
 *
 * ### What this is not
 *
 * On a canvas-per-layer renderer the answer to "the crosshair moved" is to repaint one transparent
 * canvas and leave the bars alone, and that is where this vocabulary comes from. Compose has no
 * second *surface* to keep the bars on: a `Canvas` is one draw lambda, and the platform rasterises
 * whatever is stacked over it either way.
 *
 * What it does have is a second *draw lambda*, and that turns out to be the half that mattered. The
 * bars, the axes and the panes are one lambda that reads none of the crosshair's state; the
 * crosshair and its tags are a second one that reads it, and the legend is a composable overlay
 * that reads it too. A pointer moving therefore re-runs the small lambda alone — the several
 * hundred rectangles underneath are not re-issued, and the composable that owns them is not even
 * recomposed, because every read of the crosshair outside the draw phase is deferred behind a
 * lambda or a `snapshotFlow`. The pixels are still rasterised; the work in front of them is not
 * done twice.
 *
 * ### What it is
 *
 * The *computation* in front of the painting is skippable, and it is where the cost is. A
 * crosshair moving under a finger recomputes, sixty times a second, a price range that walks every
 * visible bar and a tick ladder derived from it — for a scale that has not moved by a pixel.
 * [CURSOR] says so, and the draw pass answers by keeping the previous viewport *instance*, whose
 * price range is a `lazy` that has already been forced, and the tick list cached beside it.
 *
 * ### Merged max-wins
 *
 * Several things can change between two frames and the strongest of them decides. Merging the other
 * way — last write wins — would let a crosshair move arriving after a zoom quietly downgrade the
 * frame to a cursor repaint and draw the new bars against the old scale.
 */
enum class Invalidation {
    /** Nothing has changed. The default between frames. */
    NONE,

    /** The crosshair moved. The scale, the ticks and the indicator reads all still hold. */
    CURSOR,

    /** Something on top of the bars changed, but not the geometry underneath them. */
    LIGHT,

    /** Pan, zoom, a new series, a resize. Everything is recomputed. */
    FULL,

    ;

    /** The stronger of two levels. See the class KDoc for why it is not the later of two. */
    fun merge(other: Invalidation): Invalidation = if (ordinal >= other.ordinal) this else other
}

/**
 * The last frame's scale and the ticks that came out of it.
 *
 * A mutable holder rather than state, for the reason [Invalidation] explains: it is written by the
 * draw pass and read by the next one, and making it state would recompose the tree to record that a
 * frame had been drawn. Holding the viewport by *identity* is the point — an equal copy would force
 * its lazy price range all over again, which is the work being avoided.
 */
private class ScaleCache {
    var view: ChartViewport? = null
    var ticks: PriceTicks? = null
    var timeTicks: List<TimeTick>? = null
}

/**
 * One zoom step asked for from outside the canvas. See `CoineProChart.zoomNudge`.
 *
 * [serial] is what makes two identical requests two steps: a `LaunchedEffect` keyed on the value
 * alone would run once for «zoom in» and never again for the second tap.
 */
data class ChartZoomNudge(val serial: Int, val factor: Float) {
    companion object {
        /** One step, in or out. A quarter, which is about one notch of a pinch. */
        const val STEP = 1.25f
    }
}
