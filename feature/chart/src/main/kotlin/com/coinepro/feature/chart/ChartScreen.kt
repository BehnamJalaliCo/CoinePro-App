package com.coinepro.feature.chart

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.backtest.Backtest
import com.coinepro.core.chart.ActiveToolBar
import com.coinepro.core.chart.BarWindow
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ChartDecoration
import com.coinepro.core.chart.ChartOrder
import com.coinepro.core.chart.ChartReading
import com.coinepro.core.chart.ChartTypePicker
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.chart.DrawingState
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.IndicatorPicker
import com.coinepro.core.chart.ObjectTree
import com.coinepro.core.chart.Replay
import com.coinepro.core.chart.ScaleSide
import com.coinepro.core.chart.SignalOverlay
import com.coinepro.core.chart.ToolRail
import com.coinepro.core.chart.TradeFromChart
import com.coinepro.core.chart.TradeSide
import com.coinepro.core.chart.ComparisonBasis
import com.coinepro.core.chart.ComparisonSeries
import com.coinepro.core.chart.MAX_COMPARISONS
import com.coinepro.core.chart.PriceScaleMode
import com.coinepro.core.chart.decimalsFor
import com.coinepro.core.chart.formatPrice
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.datastore.ChartColourTemplate
import com.coinepro.core.datastore.ChartLayout
import com.coinepro.core.datastore.ChartLayoutStore
import com.coinepro.core.datastore.DrawingTemplate
import com.coinepro.core.datastore.DrawingSyncStore
import com.coinepro.core.datastore.TimeZonePrefStore
import com.coinepro.core.datastore.DrawingTemplateStore
import com.coinepro.core.datastore.IntervalFavouritesStore
import com.coinepro.core.datastore.SymbolChartStateStore
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProConfirmDialog
import com.coinepro.core.designsystem.CoineProGoldRule
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPress
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.LtrDirection
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.pressScale
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.help.CoineProHelpSheet
import com.coinepro.core.help.HelpCatalog
import com.coinepro.core.marketdata.CHART_TIME_ZONE
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.Timeframe
import com.coinepro.core.marketdata.customOf
import com.coinepro.core.symbols.SymbolClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.coinepro.core.chart.DrawingActions
import com.coinepro.core.chart.DrawingIconPicker
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.common.BidiText

/**
 * The chart screen.
 *
 * This is the screen the whole `core:chart` module existed for and did not have: eighty-three
 * indicators, ninety-one drawing tools, eighteen chart types and fifteen timeframes — plus any
 * minute count a reader types — were all built, tested and rendered into screenshots without a
 * single reader being able to reach any of them.
 *
 * The layout is one decision repeated: the chart gets the room and everything else is a sheet. A
 * phone has one screenful, and a toolbar that permanently occupies a fifth of it to hold controls
 * that are used once a session is a toolbar that costs more than it saves.
 *
 * [signal] draws a setup over the bars when the screen was opened from one — the same overlay the
 * AI screen uses, so the two never disagree about where a stop is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    controller: ChartController,
    signal: SignalOverlay? = null,
    /**
     * Opens the full web terminal on this symbol.
     *
     * Null on a build with no terminal address, which is the default — so the button is absent
     * rather than opening a blank page. It is the only route out of the native chart into a
     * WebView, and an ordinary reader who never presses it never meets one.
     */
    onOpenTerminal: (() -> Unit)? = null,
    /**
     * Opens the chart studio on this symbol — indicators, drawing tools, replay, backtest, script.
     *
     * A whole destination rather than a toolbar, and that is the owner's call. The chart page is
     * for *reading* a market; the studio is for working on it. Cramming both into one screen is
     * what produced a toolbar wider than the phone.
     */
    onOpenStudio: (() -> Unit)? = null,
    /** The reader's watchlist, for the switcher strip. Fewer than two symbols hides it. */
    watchlist: List<String> = emptyList(),
    onSelectSymbol: ((String) -> Unit)? = null,
    /**
     * The app's controller holder, keyed by symbol — what makes the split watchlist a *switch*
     * rather than a navigation.
     *
     * With it, tapping a row in the strip below the chart swaps the controller in place: the
     * screen never leaves, nothing is popped, and the new symbol arrives with its own drawings,
     * its own timeframe and its own indicators because it has its own controller and the
     * per-symbol store restores the rest. Without it the screen falls back to [onSelectSymbol],
     * which is a navigation and loses the scroll position, the armed tool and the sheet state
     * every time.
     *
     * Null in the preview and the tests, where there is one controller and no holder.
     */
    controllerFor: ((String) -> ChartController)? = null,
    /**
     * Told which instrument the reader switched to, so the shell can keep up.
     *
     * Not a navigation and must not become one — the whole point of [controllerFor] is that the
     * screen stays. It exists because the shell holds routes that name a symbol, the studio's above
     * all, and after an in-place switch «استودیو» has to open on the chart in front of the reader
     * rather than on the one the route was built with. A shell that does not care may leave it null.
     */
    onSymbolChanged: ((String) -> Unit)? = null,
    /** Live prices for the watchlist strip. Empty draws tickers with no figures beside them. */
    watchlistQuotes: Map<String, WatchlistQuote> = emptyMap(),
    /**
     * Where the divider between the chart and the watchlist is remembered.
     *
     * Null keeps the split at its default and forgets a drag when the screen leaves, which is what
     * a preview wants and would be a small daily annoyance in the app. See [ChartWorkspaceStore].
     */
    workspace: ChartWorkspaceStore? = null,
    /**
     * The reader's saved drawing styles, and the per-tool defaults over them.
     *
     * Passed as the store rather than as a list and six lambdas, the way [chartLayoutStore] is:
     * this screen reads templates for the armed tool and for the drawing being edited, which are
     * two different queries that change as the reader works, and hoisting both would put the
     * screen's own state in the shell.
     */
    drawingTemplates: DrawingTemplateStore? = null,
    /** Takes the drawn setup as a paper trade. See [SetupSheetBody]. */
    onPaperTrade: ((symbol: String, buy: Boolean, entry: Double, size: Double) -> Unit)? = null,
    /**
     * Create a price alert at a price taken from the chart.
     *
     * Hoisted rather than opened here, because the composer lives in `feature:notifications` and a
     * feature module reaching into another one is how two screens end up owning the same sheet.
     * The chart's job is to say *which price*; the app's is to ask the rest.
     *
     * "Alerts can't be set from the chart" is 6.2% of chart complaints across this category and is
     * the top one for the broker-app audience. The chart is where a reader decides a level
     * matters; making them leave it, find the alerts screen and type the number back in is asking
     * them to do the app's arithmetic.
     */
    onCreateAlert: ((symbol: String, price: Double) -> Unit)? = null,
    /** Saved layouts. Null leaves the button off — a build with no store has nothing to offer. */
    layouts: List<ChartLayout>? = null,
    onSaveLayout: ((ChartLayout) -> Unit)? = null,
    /** Removes one layout **by id**. A name is not an identity: two layouts may share one. */
    onDeleteLayout: ((String) -> Unit)? = null,
    /**
     * Where this symbol's own chart settings live between sessions.
     *
     * Handed to the controller here rather than injected into it, because the app builds its chart
     * controllers in a session-lived holder that knows nothing about persistence. Null is the
     * preview and the tests: the chart then opens on the app defaults every time, which is correct
     * for a fixture and would be a bug in the app. See [ChartController.start].
     */
    symbolChartStates: SymbolChartStateStore? = null,
    /**
     * The layout store, for the two things the list of layouts cannot answer: which one was last
     * applied, and recording the next one. Saving and deleting stay hoisted, because those are the
     * two the shell says something about.
     */
    chartLayoutStore: ChartLayoutStore? = null,
    /**
     * Which bar lengths the reader has pinned to the strip, and which they have struck out of the
     * picker.
     *
     * Passed as the store rather than as a list and two lambdas, the way [chartLayoutStore] is: the
     * strip reads the starred list and the sheet reads the hidden set, which are two queries that
     * change as the reader works, and hoisting both would put this screen's own state in the shell.
     *
     * Null is the preview and the tests, and the screen then keeps the set for its own lifetime —
     * starring works and the strip changes, and a cold start comes back on
     * [TimeframeFavourites.DEFAULT]. That is correct for a fixture and would be a small daily
     * annoyance in the app, which is why the app passes one.
     */
    intervalFavourites: IntervalFavouritesStore? = null,
    /**
     * How far a newly placed drawing travels between layouts — items 51 and 188.
     *
     * Bound to the controller here as well as in the studio, because either screen may be the first
     * one a deep link opens and a default that depended on which was is not a default.
     */
    drawingSync: DrawingSyncStore? = null,
    /**
     * The zone the time axis is read in — item 107.
     *
     * `TimeZonePrefStore` was written, provided in Hilt and injected nowhere, so `CoineProChart`'s
     * `zone` parameter took its default on every call and the axis was hard-wired to Tehran. Null
     * here is the preview and the tests, which keep the default and are right to.
     *
     * The `ZoneId` is resolved once beside the collector rather than per label, because the canvas
     * formats a date for every gridline on every frame and `ZoneId.of` is a lookup and an
     * allocation — the store's own KDoc asks callers for exactly this.
     */
    timeZones: TimeZonePrefStore? = null,
) {
    /**
     * The instrument the reader switched to from the strip, or null while they are on the one this
     * screen was opened with.
     *
     * Saved rather than remembered, so a rotation does not send them back to the symbol they
     * arrived on — which would be the switch silently undoing itself at the worst moment.
     */
    var activeSymbol by rememberSaveable { mutableStateOf<String?>(null) }

    // The controller the whole screen works against.
    //
    // Shadowing the parameter deliberately: everything below this line means "the chart in front
    // of the reader", and after a tap in the watchlist strip that is a *different* controller —
    // the one the app's holder keeps for that symbol, with its own drawings and its own restored
    // timeframe. Two names for the same idea would be one name too many, and the mistake it
    // invites is the interesting one: half the screen driving the old symbol's controller.
    val resolvedController = remember(controller, controllerFor, activeSymbol) {
        activeSymbol?.let { symbol -> controllerFor?.invoke(symbol) } ?: controller
    }
    @Suppress("NAME_SHADOWING")
    val controller = resolvedController
    val state by controller.state.collectAsStateWithLifecycle()

    /**
     * The reader's chart zone, resolved once per change rather than once per label.
     *
     * The stored id is validated on the way in — `TimeZonePrefStore.setZone` refuses one this
     * device cannot resolve — but the `runCatching` stays, because a row can also be read on a
     * device that has since lost the zone through a tzdata update, and an axis that throws mid-draw
     * takes the whole screen with it.
     */
    val storedZone by (timeZones?.zone() ?: flowOf(TimeZonePrefStore.DEFAULT_ZONE_ID))
        .collectAsStateWithLifecycle(TimeZonePrefStore.DEFAULT_ZONE_ID)
    val chartZone = remember(storedZone) {
        runCatching { ZoneId.of(storedZone) }.getOrDefault(CHART_TIME_ZONE)
    }
    val zoneScope = rememberCoroutineScope()

    /**
     * Switching instrument without leaving the screen.
     *
     * The controller swap is the whole feature: the per-symbol store restores that symbol's
     * timeframe, chart type, indicators and periods before its first fetch, and its own drawings
     * come back from `ChartDrawingStore` — so a tap costs a fetch and nothing else. On a build
     * with no holder this falls back to the navigation that was here before, which is correct but
     * loses the scroll position and the armed tool.
     */
    val switchSymbol: (String) -> Unit = { symbol ->
        if (controllerFor != null) {
            activeSymbol = symbol
            onSymbolChanged?.invoke(symbol)
        } else {
            onSelectSymbol?.invoke(symbol)
        }
    }
    var sheet by remember { mutableStateOf<ChartSheet?>(null) }
    /** Which drawing's own settings are open, or null. Opened from the object tree's row. */
    var styling by remember { mutableStateOf<Long?>(null) }
    /**
     * The annotation whose text the reader is typing, or null.
     *
     * Opened automatically the moment a text tool finishes placing — see the effect below. The
     * alternative, a reader placing a note and then having to find a "set text" command, is how a
     * note tool ends up used once.
     */
    var labelling by remember { mutableStateOf<Long?>(null) }
    /**
     * Whether the reader is being asked before every drawing comes off the chart.
     *
     * A confirmation, and it is one of the cases `CoineProConfirmDialog` names by hand: an hour of
     * marking up cannot be rebuilt, and the store is written the moment the transform runs.
     */
    var confirmClear by remember { mutableStateOf(false) }
    /**
     * Whether the chart has the whole screen.
     *
     * The chart lives in a card two hundred and eighty density-independent pixels tall, under a
     * header, a symbol wheel, a timeframe strip and a tool bar, with statistics under it. That is
     * a good page and a small chart: on a 411dp-wide phone it is about a third of the glass, and
     * a reader trying to read structure across a hundred and twenty bars in that band is doing it
     * through a letterbox.
     *
     * Saved, so the fullscreen chart survives a rotation — which is exactly when somebody wants
     * it — rather than snapping back to the card the moment the phone turns.
     */
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    /**
     * The starred bar lengths, when there is no store to keep them.
     *
     * One comma-joined string so `rememberSaveable` can put it in a `Bundle` without a custom
     * saver. Unused the moment a store is supplied — the two are never merged, because merging them
     * is how the store's deliberately-empty sentinel would get quietly refilled from a stale local
     * copy.
     */
    var ownFavourites by rememberSaveable { mutableStateOf(TimeframeFavourites.DEFAULT.joinToString(",")) }
    val storedFavourites by remember(intervalFavourites) {
        intervalFavourites?.favourites() ?: flowOf(emptyList<String>())
    }.collectAsStateWithLifecycle(TimeframeFavourites.DEFAULT)
    val hiddenIntervals by remember(intervalFavourites) {
        intervalFavourites?.hidden() ?: flowOf(emptySet<String>())
    }.collectAsStateWithLifecycle(emptySet())
    val starredWires = if (intervalFavourites != null) {
        storedFavourites
    } else {
        ownFavourites.split(',').filter { it.isNotBlank() }
    }
    val favouriteScope = rememberCoroutineScope()
    /**
     * Star or unstar one length.
     *
     * The cap is enforced here rather than in the store, which is a storage layer with no idea how
     * wide a phone is. Both refusals are silent: the star simply does not move, which says "not
     * that one" at the moment and in the place the reader looked.
     */
    val onStarWire: (String) -> Unit = { wire ->
        val pinned = wire in starredWires
        val allowed = if (pinned) {
            TimeframeFavourites.canUnstar(starredWires)
        } else {
            TimeframeFavourites.canStar(starredWires)
        }
        if (allowed) {
            if (intervalFavourites != null) {
                favouriteScope.launch {
                    runCatching {
                        if (pinned) intervalFavourites.unstar(wire) else intervalFavourites.star(wire)
                    }
                }
            } else {
                val next = if (pinned) starredWires - wire else starredWires + wire
                ownFavourites = next.joinToString(",")
            }
        }
    }
    /** Strike one length out of the picker, or put it back. Null where there is nothing to store in. */
    val onHideWire: ((String) -> Unit)? = intervalFavourites?.let { store ->
        { wire ->
            favouriteScope.launch {
                runCatching { if (wire in hiddenIntervals) store.unhide(wire) else store.hide(wire) }
            }
        }
    }

    // The «؟» dots on every picker raise an id; this is what answers them. Hosted here rather than
    // handed in by the app, because this screen is the only place in the product that *has* help
    // ids — the catalogue is 186 entries and 179 of them are chart tools and indicators. Passing
    // the host down from the app meant every caller had to remember to supply one, and none did:
    // the entire help feature was dead code, which R8 noticed before anybody else.
    var helpId by remember { mutableStateOf<String?>(null) }
    val help = rememberHelpCatalog(helpId != null)
    val helpEntry = helpId?.let { help?.get(it) }
    val onHelp: (String) -> Unit = { helpId = it }

    LaunchedStart(controller, symbolChartStates, chartLayoutStore, drawingSync)

    // The demonstration marks' reaper — item 41.
    //
    // A ticker rather than an animation: `DrawingActions.expire` returns the same state when nothing
    // has expired, so a tick that removed nothing costs a comparison and writes nothing to disk. It
    // runs only while the reader is in demonstration mode, so an ordinary chart has no timer at all
    // — which is the difference between a feature and a wakelock.
    if (state.drawing.demonstrating) {
        LaunchedEffect(controller) {
            while (true) {
                delay(DEMONSTRATION_TICK_MS)
                controller.expireDemonstrationMarks()
            }
        }
    }

    // The tree, rebuilt only when the drawings or what is hidden actually change. `treeOf` does a
    // catalogue lookup and formats a label per drawing, and on a chart with forty objects that is
    // not work to repeat on every frame of a pan.
    val objectTree = remember(state.drawing.drawings, state.hiddenDrawingIds) {
        ObjectTree.treeOf(state.drawing.drawings, state.hiddenDrawingIds)
    }

    val storeScope = rememberCoroutineScope()

    // Every colour template, the two built-ins first. Collected here rather than hoisted, because
    // the picker that shows them and the chart that paints with them are both on this screen.
    val colourTemplates by remember(chartLayoutStore) {
        chartLayoutStore?.templates() ?: flowOf(emptyList<ChartColourTemplate>())
    }.collectAsStateWithLifecycle(emptyList())

    val armedToolId = state.drawing.tool?.id
    val armedTemplates by remember(armedToolId, drawingTemplates) {
        if (armedToolId == null || drawingTemplates == null) {
            flowOf(emptyList<DrawingTemplate>())
        } else {
            drawingTemplates.templates(armedToolId)
        }
    }.collectAsStateWithLifecycle(emptyList())
    // The saved styles for whatever is *selected*, which is a different question from the one
    // above: that one asks about the tool being armed to draw with, this one about a drawing that
    // already exists. Both change as the reader works and neither can be hoisted without putting
    // this screen's own state in the shell.
    val selectedToolId = state.drawing.drawings.firstOrNull { it.id == state.drawing.selectedId }?.toolId
    val selectedTemplates by remember(selectedToolId, drawingTemplates) {
        if (selectedToolId == null || drawingTemplates == null) {
            flowOf(emptyList<DrawingTemplate>())
        } else {
            drawingTemplates.templates(selectedToolId)
        }
    }.collectAsStateWithLifecycle(emptyList())
    val armedDefault by remember(armedToolId, drawingTemplates) {
        if (armedToolId == null || drawingTemplates == null) {
            flowOf<DrawingTemplate?>(null)
        } else {
            drawingTemplates.defaultFor(armedToolId)
        }
    }.collectAsStateWithLifecycle(null)

    // A tool that has a default template arrives already wearing it.
    //
    // Applied here rather than at each rail — the chart's sheet, the studio's section, a keyboard
    // shortcut — because a default that only some of the ways of arming a tool honour is a default
    // the reader cannot rely on, which is worse than none.
    LaunchedEffect(armedToolId, armedDefault?.id) {
        val template = armedDefault ?: return@LaunchedEffect
        controller.setDrawingStyle(template.colour, template.widthDp)
    }

    val chartLayer = rememberGraphicsLayer()
    val shareScope = rememberCoroutineScope()
    val context = LocalContext.current

    val focusRequester = remember { FocusRequester() }
    // Requested once, so a keyboard works without the reader first tapping the chart. It is
    // harmless where there is no keyboard: focus on a container changes nothing a finger sees.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // The chart itself, written once and placed in one of two frames.
    //
    // A lambda rather than two copies of the `when`: the loading, failure and drawing branches are
    // the part most likely to drift, and a fullscreen chart that had quietly stopped calling
    // `onLoadMore` would be a bug nobody found for months.
    val canvas: @Composable (Modifier) -> Unit = { canvasModifier ->
        Box(modifier = canvasModifier) {
            when {
                state.loading && state.series.isEmpty -> Loading()
                state.error != null && state.series.isEmpty -> ChartFailure(state.error!!, controller::retry)
                else -> CoineProChart(
                    series = state.visibleSeries,
                    modifier = Modifier.fillMaxSize(),
                    type = state.chartType,
                    decoration = ChartDecoration(
                        overlays = state.overlays,
                        signal = signal,
                        levels = state.levels,
                        markers = state.markers,
                        panes = state.panes,
                        // Only here. Every other chart in the app is a picture of history — a
                        // signal's evidence, a script's backtest, a row's sparkline — and counting
                        // down to a close on one of those would be counting down to something that
                        // already happened. The replay is history too, so it turns the countdown
                        // off with it.
                        showCountdown = !state.replay.isOn,
                        // The overlays and how they are expressed. Handed over raw rather than
                        // rebased here, because the anchor a percentage comparison is measured
                        // from is the leftmost *visible* bar — and this screen does not know
                        // where the reader has panned to. Only the renderer holds the viewport.
                        comparisons = state.comparisons,
                        comparisonBasis = state.comparisonBasis,
                        // The reader's own palette, mapped from the stored row at this boundary
                        // rather than by either module: `core:chart` may not depend on
                        // `core:datastore` and `core:datastore` may not depend on Compose, so this
                        // screen is the one place both types are already on the classpath. Null
                        // leaves the canvas on the theme's colours, which is not the same as
                        // choosing the dark built-in — see `ColourTemplateSection`.
                        colours = state.chartColours,
                    ),
                    // The bar «رفتن به تاریخ» resolved, or null. The canvas pans to it and the
                    // controller clears it, so a reader who then pans away is not dragged back on
                    // the next recomposition. See `ChartController.focusBar`.
                    focusIndex = state.focusIndex,
                    conflate = state.visibleSeries.bars.size > CONFLATE_FROM_BARS,
                    zone = chartZone,
                    // The hidden drawings are filtered out here and merged back by the controller
                    // on the way in. See `ChartUiState.canvasDrawing`: passing the raw list would
                    // make the object tree's eye do nothing, and passing a permanently filtered
                    // one would persist a hide as a delete.
                    drawing = state.canvasDrawing,
                    onDrawing = controller::onDrawing,
                    // Read off the drawing state itself. It used to travel as a second boolean
                    // on the ui state, which is two sources for one fact and the way a rail ends up
                    // showing a trend line armed while the canvas erases. See `DrawingState.mode`.
                    eraser = state.drawing.eraser,
                    // The controller guards against a second call while one is in flight and
                    // against asking for history the server has already said does not exist, so
                    // the chart can ask freely.
                    onLoadMore = controller::loadMore,
                    logScale = state.logScale,
                    // The axis, carried through rather than stopping at the sheet that sets it.
                    //
                    // Every one of these was saved per symbol, restored on reopen, and reached the
                    // canvas nowhere: a reader could choose «درصدی», watch it persist across a
                    // restart, and never see the axis relabel. `logScale` stays for the callers
                    // holding a saved boolean and [scaleMode] wins where the two disagree.
                    scaleMode = state.scaleMode,
                    inverted = state.inverted,
                    priceBarLock = state.priceBarLock,
                    decimals = state.decimals,
                    scaleSide = state.scaleSide,
                    onScalePanes = controller::scalePanes,
                    // A long press on a drawn level offers an alert at exactly that price.
                    //
                    // The shortest route there is between "I can see the level" and "tell me when
                    // it is hit", which is the loop the product is built around. Offered only
                    // where the app can actually take an alert; on a build without the composer
                    // the gesture is absent rather than silently doing nothing.
                    onRequestAlertAt = onCreateAlert?.let { create ->
                        { price -> create(state.symbol, price) }
                    },
                    // Long press on the price gutter opens the axis' own settings, which is where
                    // every terminal puts them and the one gesture on this chart a reader is
                    // likely to try by accident and be pleased to find.
                    onPriceAxisMenu = { sheet = ChartSheet.SCALE },
                    // The bars on screen, back to the controller. One study reads it — the
                    // visible-range volume profile — and until this existed it was computed once
                    // against the whole series and never followed a pan, which is a "visible range"
                    // study that ignores the visible range.
                    onViewportChange = { view ->
                        controller.setVisibleWindow(BarWindow.visible(view.firstVisible, view.lastVisible))
                    },
                )
            }
            if (state.loadingMore) {
                Box(modifier = Modifier.align(Alignment.TopStart).padding(CoineProSpacing.One)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
            // The floating toolbar, inside the canvas box so that it follows the chart into
            // fullscreen without being wired twice. Top-centre rather than bottom: the price and
            // time axes are along the bottom and right, the fullscreen mode's own strip is along
            // the bottom too, and the legend plate is top-*start* — so the top middle is the one
            // band of this canvas nothing else claims.
            //
            // It draws only when something is selected; `DrawingSelectionToolbar` returns without
            // composing anything otherwise, so an ordinary chart pays nothing for it.
            DrawingSelectionToolbar(
                state = state.drawing,
                multiSelect = state.multiSelect,
                templates = selectedTemplates,
                onSetMultiSelect = controller::setMultiSelect,
                onRecolour = controller::recolourSelection,
                onSetWidth = { width ->
                    controller.restyleSelection(state.drawing.colour, width)
                },
                onSetTextColour = controller::setSelectionTextColour,
                onSetFillColour = controller::setSelectionFillColour,
                onSetLineStyle = controller::setSelectionLineStyle,
                onApplyTemplate = { template ->
                    controller.restyleSelection(template.colour, template.widthDp)
                },
                onEditText = { id -> labelling = id },
                onDuplicate = controller::cloneDrawing,
                onSetLocked = controller::setDrawingLocked,
                onDelete = {
                    // Every selected drawing, not only the primary. `DrawingActions.delete` refuses
                    // a locked one, so a mixed selection loses the loose drawings and keeps the
                    // protected ones — which is what the lock is for.
                    state.drawing.selection.forEach(controller::deleteDrawing)
                },
                onOpenSettings = { id -> styling = id },
                onDismiss = controller::clearSelection,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(CoineProSpacing.Half),
            )
        }
    }

    if (fullscreen) {
        FullscreenChart(
            state = state,
            controller = controller,
            canvas = canvas,
            starred = starredWires,
            onOpenSheet = { sheet = it },
            onExit = { fullscreen = false },
        )
    } else {
    // The page above, the reader's own watchlist below, and a handle they can reach with the thumb
    // already holding the phone. See `ChartWatchlistLayout`: with no watchlist this is exactly the
    // page it always was, so nothing is a mode and nothing has to be discovered.
    ChartWatchlistLayout(
        symbols = watchlist,
        current = state.symbol,
        quotes = watchlistQuotes,
        onSelect = switchSymbol,
        workspace = workspace,
    ) { pageModifier ->
    Column(
        modifier = pageModifier
            .background(CoineProColors.Stage)
            .verticalScroll(rememberScrollState())
            .focusRequester(focusRequester)
            .focusable()
            .chartShortcuts(
                onTimeframe = controller::setTimeframe,
                onReplayToggle = controller::replayToggle,
                onStep = controller::replayStep,
                onStepBack = controller::replayStepBack,
                onCancelDrawing = controller::cancelDrawing,
                onUndoDrawing = controller::undoDrawing,
            ),
    ) {
        Header(state, onOpenTerminal)
        // The wheel is the strip's older, worse sibling and only appears where the split cannot:
        // on a build with no controller holder, where switching is a navigation anyway. Drawing
        // both would be the same list twice on one screen.
        if (controllerFor == null) {
            onSelectSymbol?.let { select ->
                SymbolWheel(symbols = watchlist, current = state.symbol, onSelect = select)
            }
        }

        // The chart in a card with a gold hairline, rather than bled to the screen's edges. The
        // owner chose this and the reason it works is that it makes the chart an *object* on the
        // page — the stats and the setup below read as belonging to it instead of as unrelated
        // rows that happen to follow.
        Column(
            modifier = Modifier
                .padding(horizontal = CoineProSpacing.Gutter)
                .fillMaxWidth()
                .clip(CoineProShapes.medium)
                .background(CoineProColors.Terminal)
                .border(1.dp, CoineProTint.edge(CoineProColors.Gold), CoineProShapes.medium)
                .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.OneHalf),
        ) {
            ChartCardHeading(state)
            canvas(
                Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT)
                    // The chart alone, recorded into a layer. Sharing the whole screen would hand
                    // over the header and the toolbar; sharing this hands over the chart.
                    .drawWithContent {
                        chartLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(chartLayer)
                    },
            )
            ProvenanceLine(source = controller.sourceName, state = state, signalOnChart = signal != null)
        }

        // Only when something is being compared, so a chart with one instrument on it pays
        // nothing for the feature at all.
        if (state.comparisons.isNotEmpty()) {
            ComparisonBar(
                comparisons = state.comparisons,
                basis = state.comparisonBasis,
                onSetBasis = controller::setComparisonBasis,
                onRemove = controller::removeComparison,
            )
        }

        if (state.replay.isOn) {
            ReplayBar(
                state = state.replay,
                onToggle = controller::replayToggle,
                onStep = controller::replayStep,
                onStepBack = controller::replayStepBack,
                onSeek = controller::replaySeek,
                // The ladder overload, not the `Double` one: the picker hands over a step and the
                // type is what makes an unknown speed impossible rather than merely ignored.
                onSpeed = { step -> controller.replaySetSpeed(step) },
                onJumpToLive = controller::replayJumpToLive,
                onGoTo = controller::replayGoTo,
                onExit = controller::exitReplay,
            )
        }

        // Timeframe first, then the tools — both under the chart, in the order a reader reaches
        // for them. Switching timeframe is the most frequent thing anybody does on a chart, so it
        // gets the position closest to the thumb.
        IntervalRow(
            selected = state.interval,
            onSelect = controller::setInterval,
            onMore = { sheet = ChartSheet.INTERVAL },
            starred = starredWires,
        )
        RangeRow(selected = state.range, onSelect = controller::setRange)
        // «رفتن به تاریخ» off replay — backlog 105. The canvas now takes a focus index, so the
        // field moves the chart instead of resolving a date and doing nothing; it is drawn only
        // when the replay bar is not, because that bar carries its own copy of this field and two
        // date boxes one above the other is the clutter the owner asked to be kept out.
        if (!state.replay.isOn && state.visibleSeries.bars.isNotEmpty()) {
            GoToDateField(bars = state.visibleSeries.bars, onGoTo = controller::focusBar)
        }

        // The bar that makes the chart a chart, and it sits **below** the chart rather than above
        // it.
        //
        // Two separate findings put it here. The sheets themselves were dead code — every one was
        // written, tested and rendered, and nothing in the app ever assigned `sheet` to any of
        // them except SETUP, which is why the drawing tools appeared not to work. And the position
        // is the reader's: in reviews of every app in this category the complaint about top-placed
        // chart controls is explicit and repeated — "our thumbs is not that long" — while the
        // praise goes to apps that put the controls where a hand holding a phone already is.
        ChartToolBar(
            drawing = state.drawing,
            indicators = state.activeIndicators.size,
            drawings = state.drawing.drawings.size,
            onOpen = { sheet = it },
            onOpenStudio = onOpenStudio,
            onFullscreen = { fullscreen = true },
            scaleMode = state.scaleMode,
            axisAdjusted = state.inverted || state.priceBarLock || state.decimals != null,
            comparisons = state.comparisons.size,
            onOpenScale = { sheet = ChartSheet.SCALE },
            // Offered only when there is a price to alert on. A button that opens a composer with
            // an empty number is a button that makes the reader type what the chart already knows.
            onCreateAlert = onCreateAlert?.let { create ->
                state.lastPrice?.let { price -> { create(state.symbol, price) } }
            },
        )

        ActiveToolBar(
            tool = state.drawing.tool,
            placed = state.drawing.pending.size,
            onCancel = controller::cancelDrawing,
            onUndo = controller::undoDrawing,
            onHelp = onHelp,
        )

        ReadingRow(state)
        state.setup?.let { order -> SetupCard(order, onOpen = { sheet = ChartSheet.SETUP }) }
        StudioCard(
            indicators = state.activeIndicators.size,
            drawings = state.drawing.drawings.size,
            onOpen = onOpenStudio,
            // The backtest report, reachable from the chart at last. `ChartSheet.BACKTEST` has been
            // declared and rendered since this screen was written and **nothing ever assigned it**:
            // the report was reachable only from the studio, and the twenty-five-metric engine
            // behind it was reachable from nowhere at all. It goes here rather than on the toolbar,
            // which is full and must not grow — this card is an existing affordance with room.
            //
            // Offered only with bars to run over. A report over an empty series is five tabs of
            // dashes.
            onBacktest = { sheet = ChartSheet.BACKTEST }.takeIf { state.series.bars.isNotEmpty() },
            onShare = {
                shareScope.launch {
                    ChartShare.share(context, chartLayer.toImageBitmap(), state.symbol)
                }
            },
        )
        Spacer(Modifier.height(CoineProSpacing.Three))
    }
    }
    }

    // A text tool that has just been placed asks for its text at once.
    //
    // Keyed on the newest drawing's id, so it fires exactly once per placement and never reopens
    // when the reader taps an existing note to select it — selecting is not editing, and a
    // keyboard that appears every time you touch a label is a label you stop touching.
    val newest = state.drawing.drawings.lastOrNull()
    LaunchedEffect(newest?.id) {
        val placed = newest ?: return@LaunchedEffect
        if (placed.text == null && DrawingActions.holdsText(placed.toolId)) labelling = placed.id
    }

    when (sheet) {
        ChartSheet.TYPE -> CoineProSheet(
            title = "نوع چارت",
            // Counted after the volume gate, not before. A subtitle that promises eighteen over a
            // list of sixteen is a small lie the reader catches immediately.
            subtitle = "${ChartCatalog.chartTypeCount(state.series.hasVolume).toPersianDigits()} نوع",
            onDismiss = { sheet = null },
        ) {
            ChartTypePicker(
                selected = state.chartType,
                onSelect = {
                    controller.setChartType(it)
                    sheet = null
                },
                onHelp = onHelp,
                hasVolume = state.series.hasVolume,
            )
        }

        ChartSheet.INDICATORS -> CoineProSheet(
            title = "اندیکاتورها",
            // Counted after the volume gate, like the chart-type subtitle above: fourteen studies
            // are arithmetic on a volume column, and a subtitle promising eighty-three over a list
            // of sixty-nine is a small lie the reader catches immediately.
            subtitle = "${ChartCatalog.indicatorCount(state.series.hasVolume).toPersianDigits()} اندیکاتور",
            onDismiss = { sheet = null },
        ) {
            // No dismiss on select: switching four indicators on is four taps, and a sheet that
            // closes after each one turns that into twelve.
            IndicatorPicker(
                active = state.activeIndicators,
                onToggle = { controller.toggleIndicator(it.id) },
                onHelp = onHelp,
                hasVolume = state.series.hasVolume,
                periods = state.indicatorPeriods,
                onSetPeriod = controller::setIndicatorPeriod,
            )
        }

        ChartSheet.TOOLS -> CoineProSheet(
            title = "ابزارهای ترسیم",
            subtitle = "${(DrawingTools.ALL.size).toPersianDigits()} ابزار",
            onDismiss = { sheet = null },
        ) {
            // The armed tool's saved styles, above the rail rather than on the toolbar. Choosing
            // one arms the tool *and* sets the style in a single tap, which is the whole point:
            // "draw a trend line the way I always draw trend lines" is one decision, not two.
            ToolTemplateRow(
                tool = state.drawing.tool,
                templates = armedTemplates,
                defaultTemplateId = armedDefault?.id,
                onApply = { template ->
                    controller.armWithStyle(state.drawing.tool, template.colour, template.widthDp)
                    sheet = null
                },
            )
            ToolRail(
                selected = state.drawing.tool?.id,
                onSelect = {
                    // Plain arm. This tool's own default template, where the reader has set one,
                    // is applied by the effect that watches the armed tool — one place rather than
                    // two, so the studio's rail gets the same behaviour without repeating it.
                    controller.arm(it)
                    sheet = null
                },
                onHelp = onHelp,
                // Every parameter the rail takes, and each one was previously a feature that
                // existed and could not be reached. The rail's own action row — magnet, keep
                // drawing, lock all, hide layers — renders only when a caller offers at least one
                // of these callbacks, and neither call site offered any: the whole row had never
                // been on a screen. The magnet in particular was `OFF` for the life of the app,
                // which also made the OHLC channel bindings dead code, since one is written only
                // by a tap the magnet moved.
                //
                // `hasVolume` is the one that was actively wrong rather than merely absent. On the
                // MT5 forex feed a reader could arm a volume-profile tool and watch it draw
                // nothing, because the rail was offering three tools the renderer has no column
                // for.
                hasVolume = state.series.hasVolume,
                favourites = state.drawing.favourites,
                onToggleFavourite = { controller.toggleToolFavourite(it.id) },
                magnet = state.drawing.magnetMode,
                onCycleMagnet = controller::cycleMagnet,
                keepDrawing = state.drawing.keepDrawing,
                onKeepDrawing = controller::setKeepDrawing,
                lockedAll = state.drawing.lockedAll,
                onLockAll = controller::setLockAllDrawings,
                hidden = state.drawing.hidden,
                onHide = controller::setLayerHidden,
                onHideAll = controller::setAllLayersHidden,
            )
        }

        ChartSheet.LAYOUTS -> CoineProSheet(
            title = "چیدمان‌ها",
            onDismiss = { sheet = null },
        ) {
            LayoutSheetBody(
                layouts = layouts.orEmpty(),
                current = state,
                onApply = { layout ->
                    controller.applyLayout(layout)
                    sheet = null
                },
                onSave = { name -> onSaveLayout?.invoke(newLayout(state, name)) },
                onDelete = { id -> onDeleteLayout?.invoke(id) },
            )
            // The palette, inside the sheet that already means «the apparatus I look through» —
            // and not on the toolbar, which is full and must not grow. `ChartLayout` has always
            // had a field for it; until now nothing could set one.
            chartLayoutStore?.let { store ->
                HorizontalDivider(color = CoineProColors.Border)
                ColourTemplateSection(
                    templates = colourTemplates,
                    selected = state.colourTemplate,
                    onSelect = controller::setColourTemplate,
                    onSave = { template -> storeScope.launch { runCatching { store.saveTemplate(template) } } },
                    onDelete = { id ->
                        if (state.colourTemplate?.id == id) controller.setColourTemplate(null)
                        storeScope.launch { runCatching { store.deleteTemplate(id) } }
                    },
                )
            }
        }

        ChartSheet.INTERVAL -> CoineProSheet(
            title = "بازهٔ زمانی",
            subtitle = "${Timeframe.entries.size.toPersianDigits()} بازهٔ آماده",
            onDismiss = { sheet = null },
        ) {
            IntervalSheetBody(
                selected = state.interval,
                onSelect = {
                    controller.setInterval(it)
                    sheet = null
                },
                starred = starredWires,
                hidden = hiddenIntervals,
                // No dismiss on star: pinning four lengths is four taps, and a sheet that closed
                // after each one would turn that into eight.
                onStar = onStarWire,
                onHide = onHideWire,
            )
        }

        ChartSheet.SCALE -> CoineProSheet(
            title = "مقیاس قیمت",
            subtitle = state.scaleMode.persianLabel,
            onDismiss = { sheet = null },
        ) {
            PriceScaleSheetBody(
                state = state,
                controller = controller,
                zoneId = timeZones?.let { storedZone },
                onSelectZone = { id -> timeZones?.let { store -> zoneScope.launch { store.setZone(id) } } },
            )
        }

        ChartSheet.COMPARE -> CoineProSheet(
            title = "مقایسه با نماد دیگر",
            subtitle = "${MAX_COMPARISONS.toPersianDigits()} نماد هم‌زمان",
            onDismiss = { sheet = null },
        ) {
            ComparisonSheetBody(
                base = state.symbol,
                watchlist = watchlist,
                comparisons = state.comparisons,
                basis = state.comparisonBasis,
                onSetBasis = controller::setComparisonBasis,
                onAdd = controller::addComparison,
                onRemove = controller::removeComparison,
            )
        }

        ChartSheet.BACKTEST -> CoineProSheet(
            title = "بک‌تست",
            subtitle = state.symbol,
            onDismiss = { sheet = null },
        ) {
            // The scroll is the *host's* decision and this host is a sheet, so it scrolls. The
            // studio renders the same body inside a `LazyColumn` item and must pass nothing —
            // a vertically scrollable child under an infinite height constraint throws.
            BacktestSheetBody(
                bars = state.series.bars,
                symbol = state.symbol,
                modifier = Modifier.verticalScroll(rememberScrollState()),
                hasMoreHistory = state.hasMore,
                loadingHistory = state.loadingMore,
                onLoadMoreHistory = controller::loadMore,
            )
        }

        ChartSheet.SETUP -> state.setup?.let { order ->
            CoineProSheet(
                title = "معاملهٔ روی نمودار",
                subtitle = state.symbol,
                onDismiss = { sheet = null },
            ) {
                SetupSheetBody(
                    order = order,
                    symbol = state.symbol,
                    livePrice = state.lastPrice,
                    onPaperTrade = onPaperTrade?.let { take ->
                        { buy, entry, size ->
                            take(state.symbol, buy, entry, size)
                            sheet = null
                        }
                    },
                )
            }
        }

        ChartSheet.DRAWINGS -> CoineProSheet(
            title = "درخت ترسیم‌ها",
            subtitle = state.drawing.drawings.size.toPersianDigits() + " ترسیم",
            onDismiss = { sheet = null },
        ) {
            // The clipboard and the way to an empty chart, above the tree. See
            // `DrawingClipboardRow` for why the words live here rather than on the floating strip.
            DrawingClipboardRow(
                state = state.drawing,
                onCopy = controller::copySelection,
                onPaste = {
                    controller.pasteClipboard()
                    // Closed, because the copies land on the chart and the reader has to be able
                    // to see where. Every other action in this sheet leaves it open.
                    sheet = null
                },
                onClear = { confirmClear = true },
            )
            ObjectTreeSheetBody(
                groups = objectTree,
                drawings = state.drawing.drawings,
                selectedId = state.drawing.selectedId,
                onSelect = { id ->
                    controller.selectDrawing(id)
                    // Closed on select, because a reader who has just found their line wants to be
                    // looking at it. Every other action on the row leaves the sheet open.
                    sheet = null
                },
                onToggleHidden = controller::toggleDrawingHidden,
                onToggleLocked = { node -> controller.setDrawingLocked(node.id, !node.locked) },
                onDelete = controller::deleteDrawing,
                onReorder = controller::reorderDrawing,
                onOpenStyle = { id -> styling = id },
            )
        }

        null -> Unit
    }

    // One drawing's own settings, opened from its row in the object tree.
    //
    // Keyed on the drawing rather than on the sheet, so a drawing deleted from underneath the sheet
    // — by a swipe on the row behind it, or by the eraser — closes it instead of leaving a panel
    // editing something that is gone.
    styling?.let { id ->
        val drawing = state.drawing.drawings.firstOrNull { it.id == id }
        if (drawing == null) {
            styling = null
        } else {
            DrawingStyleSheet(
                drawing = drawing,
                store = drawingTemplates,
                onDismiss = { styling = null },
                onSetColour = { colour ->
                    controller.applyTemplateToDrawing(drawing.id, colour, drawing.widthDp)
                    // The next drawing follows the last decision, which is what every terminal
                    // does and what a reader marking six levels in red expects after the first.
                    controller.setDrawingStyle(colour, drawing.widthDp)
                },
                onSetWidth = { width ->
                    controller.applyTemplateToDrawing(drawing.id, drawing.colour, width)
                    controller.setDrawingStyle(drawing.colour, width)
                },
                onSetDeviations = { value -> controller.setDrawingDeviations(drawing.id, value) },
                onApplyTemplate = { template ->
                    controller.applyTemplateToDrawing(drawing.id, template.colour, template.widthDp)
                    controller.setDrawingStyle(template.colour, template.widthDp)
                },
                onBringToFront = { controller.bringDrawingToFront(drawing.id) },
                onSendToBack = { controller.sendDrawingToBack(drawing.id) },
                onDelete = {
                    controller.deleteDrawing(drawing.id)
                    styling = null
                },
            )
        }
    }

    labelling?.let { id ->
        val drawing = state.drawing.drawings.firstOrNull { it.id == id }
        if (drawing == null) {
            labelling = null
        } else {
            DrawingTextSheet(
                initial = drawing.text.orEmpty(),
                // The icon tool keeps its glyph in the same field a note keeps its words, so the
                // row of marks is offered *above* the keyboard rather than instead of it — a reader
                // who wants a mark the row does not carry can still type one.
                icons = DrawingActions.holdsIcon(drawing.toolId),
                onSave = { text ->
                    controller.onDrawing(DrawingActions.setText(state.drawing, id, text))
                    labelling = null
                },
                onDismiss = { labelling = null },
            )
        }
    }

    if (confirmClear) {
        CoineProConfirmDialog(
            title = "پاک کردن همهٔ ترسیم‌ها",
            message = "هر ${state.drawing.drawings.size.toPersianDigits()} ترسیم این نماد برداشته می‌شود و برنمی‌گردد. اندیکاتورها و تنظیمات نمودار دست‌نخورده می‌مانند.",
            confirmLabel = "پاک کن",
            dismissLabel = "بماند",
            destructive = true,
            onConfirm = {
                controller.clearDrawings()
                confirmClear = false
                sheet = null
            },
            onDismiss = { confirmClear = false },
        )
    }

    helpEntry?.let { entry ->
        CoineProHelpSheet(entry = entry, onDismiss = { helpId = null })
    }
}

/**
 * Where a note, a callout, a text mark or a price label gets its words.
 *
 * ### Why a sheet and not typing on the canvas
 *
 * An in-place editor on a chart means a caret positioned in chart space, a keyboard that covers
 * the thing being labelled, and a text box that has to survive pan and zoom while it is open.
 * Every terminal that offers in-place editing on a phone does it badly. A sheet is one field, the
 * keyboard where the reader expects it, and the drawing still visible above it.
 *
 * ### Saving an empty box is a valid answer
 *
 * It clears the text and the drawing falls back to its placeholder, which is how a reader undoes a
 * label without deleting the drawing under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawingTextSheet(
    initial: String,
    /**
     * Whether this drawing is the icon tool, whose «text» is one glyph.
     *
     * The tool stores its mark in `Drawing.text` — one field and one codec rather than a parallel
     * pair — so the picker is a row of ten answers above the same box. A free keyboard alone is the
     * wrong shape for it: a sentence typed into an icon tool is drawn at label size inside a diamond
     * built for a single glyph, and the reader has no way to find that out before they type it.
     */
    icons: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    CoineProSheet(
        title = stringResource(R.string.chart_label_title),
        subtitle = stringResource(R.string.chart_label_subtitle),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            if (icons) {
                DrawingIconPicker(
                    selected = text.takeIf { it.isNotEmpty() },
                    // Picked and saved in one gesture. An icon is one mark, so a picker that only
                    // filled the box and left the reader to press «ثبت» would be two taps for a
                    // decision that has exactly one.
                    onPick = { glyph ->
                        text = glyph
                        onSave(glyph)
                    },
                )
            }
            CoineProTextField(
                value = text,
                onValueChange = { text = it.take(DrawingActions.MAX_TEXT_LENGTH) },
                label = stringResource(R.string.chart_label_field),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            CoineProPrimaryButton(
                text = stringResource(R.string.chart_label_save),
                onClick = { onSave(text) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The chart with the whole screen, and the four controls that still have to be reachable.
 *
 * ### What is kept and what goes
 *
 * Kept: the timeframe strip, the tool bar, the symbol and its price, and a way out. Everything
 * else — the statistics, the setup card, the studio card, the symbol wheel — is a *page* around a
 * chart, and the point of this mode is that there is no page.
 *
 * The controls float over the chart rather than taking rows from it: a fullscreen mode that spent
 * a fifth of its height on chrome would be the card again with fewer features.
 *
 * ### It does not force landscape
 *
 * Rotating is the reader's decision and the phone already has a control for it. An app that
 * flipped the screen on a reader holding it in bed would be doing something they did not ask for,
 * and one that *locked* landscape would be worse. The chart fills whatever shape it is given —
 * the viewport is measured, not assumed — so a phone turned sideways gets a genuinely wide chart
 * and one held upright gets a tall one, both without this screen knowing which happened.
 */
@Composable
private fun FullscreenChart(
    state: ChartUiState,
    controller: ChartController,
    canvas: @Composable (Modifier) -> Unit,
    /** The reader's pinned bar lengths, so the strip is the same one they see on the page. */
    starred: List<String>,
    onOpenSheet: (ChartSheet) -> Unit,
    onExit: () -> Unit,
) {
    // Back leaves fullscreen before it leaves the chart. Without this the reader's first instinct
    // — the gesture that means "out of this" — takes them off the screen entirely, losing the
    // viewport, the armed tool and whatever they were reading.
    BackHandler(onBack = onExit)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Terminal),
    ) {
        canvas(Modifier.fillMaxSize())

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(CoineProSpacing.One),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FloatingChartButton(
                icon = CoineProIcons.Tools,
                label = stringResource(R.string.chart_fullscreen_tools),
                onClick = { onOpenSheet(ChartSheet.TOOLS) },
            )
            FloatingChartButton(
                icon = CoineProIcons.Close,
                label = stringResource(R.string.chart_fullscreen_exit),
                onClick = onExit,
            )
        }

        // The timeframe strip at the bottom, where a thumb already is on a phone held in one
        // hand — and out of the top-left corner where the legend plate draws. The quick ranges go
        // under it, for the same reason and in the same order as on the page.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(CoineProColors.Stage.copy(alpha = STRIP_ALPHA)),
        ) {
            IntervalRow(
                selected = state.interval,
                onSelect = controller::setInterval,
                onMore = { onOpenSheet(ChartSheet.INTERVAL) },
                starred = starred,
            )
            RangeRow(selected = state.range, onSelect = controller::setRange)
        }
    }
}

/** A round control that floats over the chart rather than taking a row from it. */
@Composable
private fun FloatingChartButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    val haptics = rememberCoineProHaptics()
    Box(
        modifier = Modifier
            .size(FLOATING_BUTTON)
            .clip(CircleShape)
            .background(CoineProColors.Stage.copy(alpha = FLOATING_ALPHA))
            .clickable {
                haptics.select()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = CoineProColors.TextSecondary,
            modifier = Modifier.size(FLOATING_GLYPH),
        )
    }
}

/**
 * The help catalogue, parsed the first time somebody asks for it and never again.
 *
 * [wanted] is what makes it lazy: the file is 186 entries of Persian and English prose and parsing
 * it costs a frame, which is a frame nobody should pay for opening a chart. It is read off the
 * main thread for the same reason.
 *
 * A failure returns null and the «؟» simply does nothing, which is what it did before this existed.
 * A missing help file is not a reason to fail opening a chart.
 */
@Composable
internal fun rememberHelpCatalog(wanted: Boolean): HelpCatalog? {
    val context = LocalContext.current
    var catalog by remember { mutableStateOf<HelpCatalog?>(null) }
    LaunchedEffect(wanted) {
        if (wanted && catalog == null) {
            catalog = withContext(Dispatchers.IO) {
                runCatching { HelpCatalog.load(context.assets) }.getOrNull()
            }
        }
    }
    return catalog
}

private enum class ChartSheet { TYPE, INDICATORS, TOOLS, DRAWINGS, SETUP, BACKTEST, LAYOUTS, INTERVAL, SCALE, COMPARE }

/**
 * Binds the stores and starts the controller, in that order and in one effect.
 *
 * The order is the whole of it. `start` reads this symbol's saved settings and applies them before
 * it fetches anything, so a store bound after it would arrive too late to stop the chart opening on
 * the app default — and the reader would watch it load once and then jump.
 */
@Composable
private fun LaunchedStart(
    controller: ChartController,
    symbolChartStates: SymbolChartStateStore?,
    chartLayoutStore: ChartLayoutStore?,
    drawingSync: DrawingSyncStore?,
) {
    LaunchedEffect(controller) {
        controller.bindStores(symbolChartStates, chartLayoutStore, drawingSync)
        controller.start()
    }
}

/**
 * The page's heading: what this market is, and what it is doing.
 *
 * The price is set at forty points because it is the answer to the only question every visit
 * begins with, and the gold rule under it is what ties the whole page to that heading. The change
 * is measured across the loaded window rather than from a session open — neither feed sends one,
 * and naming it after the window is the honest version: it describes the picture on screen.
 */
@Composable
private fun Header(state: ChartUiState, onOpenTerminal: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.OneHalf),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        ) {
            CoineProAssetLogo(symbol = state.symbol, size = 34.dp)
            Column(modifier = Modifier.weight(1f)) {
                LtrDirection {
                    Text(
                        text = state.symbol,
                        style = MaterialTheme.typography.titleSmall,
                        color = CoineProColors.TextPrimary,
                    )
                }
                Text(
                    text = SymbolClassifier.classify(state.symbol).description,
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            onOpenTerminal?.let {
                IconButton(onClick = it, modifier = Modifier.size(30.dp)) {
                    Icon(
                        painter = painterResource(DesignR.drawable.tv_maximize2),
                        contentDescription = "ترمینال حرفه‌ای",
                        tint = CoineProColors.TextMuted,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = CoineProSpacing.OneHalf),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            LtrDirection {
                Text(
                    // Latin digits, as every market figure in this app is: a price is read against
                    // a broker statement and a chart axis, both of which use them.
                    text = state.lastPrice?.let { formatPrice(it, decimalsFor(it)) } ?: "—",
                    style = CoineProTextStyles.Balance,
                    color = CoineProColors.TextPrimary,
                )
            }
            state.changePercent?.let { move ->
                LtrDirection {
                    Text(
                        text = MarketNumberFormatter.signedPercent(move),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (move >= 0) CoineProColors.Buy else CoineProColors.Sell,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        }
        CoineProGoldRule(modifier = Modifier.padding(top = CoineProSpacing.One))
    }
}

/**
 * The interval strip: outlined pills, gold on the one in force.
 *
 * ### Why not fifteen
 *
 * It used to draw every preset, reversed, and that was right when there were eight. There are now
 * fifteen plus whatever minute count a reader types, and fifteen pills is not a strip — it is a
 * scrolling wall directly under the chart, in which the frame somebody actually wants is somewhere
 * off the edge. The row would have grown into the thing this screen is built to avoid.
 *
 * ### Why the set is now the reader's
 *
 * It was a constant of six — the set the keyboard binds and the set chart vision reads — and those
 * are a defensible six that are still somebody else's. A reader who works the two-hour and the
 * weekly had M1 and M5 permanently under their thumb and their own two lengths behind «بیشتر»
 * forever, with nothing suggesting that could change. [starred] is that set, they fill it
 * themselves in the sheet behind «بیشتر», and [TimeframeFavourites.DEFAULT] is what it starts as —
 * so nobody who never opens the sheet notices any difference.
 *
 * Whatever is in force is always drawn, even when it is not starred. A strip that showed no
 * selection because the reader had chosen H2 would leave them unable to see what they were looking
 * at from the control that sets it.
 */
@Composable
internal fun IntervalRow(
    selected: ChartInterval,
    onSelect: (ChartInterval) -> Unit,
    onMore: () -> Unit,
    /** The lengths pinned to the strip, in wire spellings. See [TimeframeFavourites]. */
    starred: List<String> = TimeframeFavourites.DEFAULT,
) {
    // Rebuilt only when the starred list or the selection changes, which are the two things that
    // change the row's contents.
    val shown = remember(selected, starred) { TimeframeFavourites.resolve(starred, selected) }
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = CoineProSpacing.OneHalf),
        // Tighter than the page's gutter, which is what lets the whole row sit on a phone without
        // having to be scrolled to reach the shortest frames.
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        contentPadding = PaddingValues(horizontal = CoineProSpacing.Two),
    ) {
        items(shown, key = { it.wire }) { interval ->
            IntervalPill(
                text = interval.wire,
                active = interval == selected,
                onClick = { onSelect(interval) },
            )
        }
        item(key = "more") {
            IntervalPill(text = "بیشتر", active = false, latin = false, onClick = onMore)
        }
    }
}

/**
 * The quick ranges: «همه · ۵ سال · ۱ سال · ۶ ماه · ۳ ماه · ۱ ماه · ۵ روز · ۱ روز».
 *
 * ### Where it is, and why not on the toolbar
 *
 * Directly under the interval strip, which is under the chart — where a thumb holding the phone
 * already is. The toolbar is full and must not grow, and a range control belongs beside the other
 * control that answers "how much time am I looking at" rather than in a row of sheet-openers.
 *
 * ### Why it is a second row rather than more pills in the first
 *
 * They answer different questions. «H4» is how long one candle is; «۱ سال» is how much history is
 * in front of you. Mixing them into one strip would make «۱ ماه» and «M15» look like alternatives
 * of the same kind, which is exactly the confusion that makes a reader tap one and be surprised.
 * The range row is always drawn rather than revealed by a gesture — a control nobody can see is a
 * control nobody finds — and it carries no «بیشتر», because eight ranges is the whole set.
 *
 * See [ChartRange] for why tapping one changes the bar length.
 */
@Composable
private fun RangeRow(selected: ChartRange?, onSelect: (ChartRange) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = CoineProSpacing.OneHalf),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        contentPadding = PaddingValues(horizontal = CoineProSpacing.Two),
    ) {
        items(ChartRange.OFFERED, key = { it.name }) { range ->
            IntervalPill(
                text = range.label,
                active = range == selected,
                // Persian prose durations, not wire spellings, so they must not be forced Latin.
                latin = false,
                onClick = { onSelect(range) },
            )
        }
    }
}

/** One pill in the interval strip, and in the groups inside the sheet. */
@Composable
private fun IntervalPill(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    /** Wire spellings are market figures and stay Latin; «بیشتر» is prose and must not be. */
    latin: Boolean = true,
) {
    Box(
        modifier = Modifier
            .clip(CoineProPillShape)
            .background(
                if (active) {
                    CoineProTint.fill(CoineProColors.Gold, CoineProColors.Stage)
                } else {
                    Color.Transparent
                },
            )
            .border(
                width = 1.dp,
                color = if (active) CoineProTint.edge(CoineProColors.Gold) else CoineProColors.Border,
                shape = CoineProPillShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
    ) {
        val ink = if (active) CoineProColors.Gold else CoineProColors.TextMuted
        if (latin) {
            LtrDirection {
                Text(text = text, style = MaterialTheme.typography.labelSmall, color = ink)
            }
        } else {
            Text(text = text, style = MaterialTheme.typography.labelSmall, color = ink)
        }
    }
}

/**
 * Every interval, grouped, with a field for one the reader makes up.
 *
 * Three groups rather than one reversed list, because fifteen pills in a single wrap have no shape
 * and a reader looking for the three-hour has to read all fifteen. Minutes, hours, then days and
 * up is how a trader already names them, and within a group the order is shortest first, which is
 * how a period is naturally listed.
 *
 * The custom field is the point of the sheet as much as the presets are. It takes Persian or Latin
 * digits — an Iranian keyboard produces the first by default, and a field that silently refuses
 * «۲۰۵» while accepting `205` looks broken — and it stays disabled until what is typed is actually
 * an interval, so the reader is never sent to a server with a number it will refuse.
 */
@Composable
internal fun IntervalSheetBody(
    selected: ChartInterval,
    onSelect: (ChartInterval) -> Unit,
    /** The lengths pinned to the strip under the chart, or null where the caller offers no starring. */
    starred: List<String>? = null,
    /** The presets the reader has struck out of this sheet. See [TimeframeFavourites.offered]. */
    hidden: Set<String> = emptySet(),
    onStar: ((String) -> Unit)? = null,
    /** Strike one preset out of this sheet, or put it back. Null hides the second gesture entirely. */
    onHide: ((String) -> Unit)? = null,
) {
    var typed by rememberSaveable { mutableStateOf("") }
    val custom = customOf(typed)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        if (starred != null && onStar != null) {
            StarredIntervalSection(starred = starred, hidden = hidden, onStar = onStar, onHide = onHide)
            HorizontalDivider(color = CoineProColors.Border)
        }
        INTERVAL_GROUPS.forEach { (title, frames) ->
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            ) {
                // Struck-out presets are absent rather than dimmed. A row that cannot be tapped is
                // a row that costs a tap to discover, and the reader is the one who struck it out.
                TimeframeFavourites.offered(
                    frames.map { ChartInterval.Preset(it) },
                    hidden,
                    selected,
                ).forEach { interval ->
                    IntervalPill(
                        text = interval.wire,
                        active = interval == selected,
                        onClick = { onSelect(interval) },
                    )
                }
            }
        }

        HorizontalDivider(color = CoineProColors.Border)

        Text(
            text = "بازهٔ دلخواه",
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            fontWeight = FontWeight.Normal,
        )
        CoineProTextField(
            value = typed,
            onValueChange = { typed = it },
            label = "دقیقه، از ۱ تا ۱۴۴۰",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        CoineProPrimaryButton(
            text = custom?.let { "نمایش ${it.label}" } ?: "نمایش بازهٔ دلخواه",
            onClick = {
                custom?.let {
                    onSelect(ChartInterval.Custom(it))
                    typed = ""
                }
            },
            enabled = custom != null,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "کندل بازهٔ دلخواه روی همین دستگاه از کندل‌های کوتاه‌تر ساخته می‌شود و از نیمه‌شب تهران شمرده می‌شود.",
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

/**
 * Which lengths the reader wants under their thumb.
 *
 * ### Why it is a separate section and not a long press on the pills above
 *
 * Because a long press on a pill is invisible. The pills in the groups below *select* a length —
 * that is the obvious meaning of tapping one and it must stay the only meaning — so the second job
 * gets its own row, its own heading and its own glyph. A reader who never wants to change the
 * strip reads one line and scrolls past; a reader who does can see, in one place, exactly which of
 * the fifteen are pinned.
 *
 * The star is filled and gold when pinned, hollow and muted when not, which is the one convention
 * this app already uses for a personal shortlist — the tool rail's favourites row does the same.
 *
 * ### The two refusals, and why they are silent
 *
 * [TimeframeFavourites.toggle] will not unpin the last length and will not pin past
 * [TimeframeFavourites.MAX]. Neither raises a message: the star simply does not move, which says
 * "not that one" at the moment and in the place the reader looked, where a snackbar over the sheet
 * would say it somewhere else a second later. The count under the heading is what makes the second
 * refusal legible before it happens.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StarredIntervalSection(
    starred: List<String>,
    hidden: Set<String>,
    onStar: (String) -> Unit,
    onHide: ((String) -> Unit)?,
) {
    SheetLabel("کدام بازه‌ها روی نوار زیر نمودار باشند")
    Text(
        // A prose count of a shortlist, so Persian digits — unlike the wire spellings on the pills.
        text = starred.size.toPersianDigits() + " از " + TimeframeFavourites.MAX.toPersianDigits() +
            " بازه سنجاق شده" +
            if (onHide != null) ". نگه‌داشتن یک بازه، آن را از همین صفحه هم برمی‌دارد." else ".",
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.TextMuted,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Timeframe.entries.forEach { frame ->
            val wire = frame.wire
            val pinned = wire in starred
            val struck = wire in hidden
            Row(
                modifier = Modifier
                    .clip(CoineProPillShape)
                    .background(
                        if (pinned) {
                            CoineProTint.fill(CoineProColors.Gold, CoineProColors.Surface)
                        } else {
                            Color.Transparent
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (pinned) CoineProTint.edge(CoineProColors.Gold) else CoineProColors.Border,
                        shape = CoineProPillShape,
                    )
                    .combinedClickable(
                        onClick = { onStar(wire) },
                        // The second gesture, and it is a long press because it is the rarer of the
                        // two and because a second target on a pill this size is a target nobody
                        // hits. The sentence above says the gesture exists, which is what a long
                        // press needs to be a feature rather than a secret.
                        onLongClick = onHide?.let { hide -> { hide(wire) } },
                    )
                    .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    painter = painterResource(
                        when {
                            struck -> DesignR.drawable.icon_eye_slash
                            pinned -> DesignR.drawable.icon_filled_star
                            else -> DesignR.drawable.icon_star
                        },
                    ),
                    contentDescription = when {
                        struck -> "برگرداندن به فهرست"
                        pinned -> "برداشتن از نوار"
                        else -> "سنجاق روی نوار"
                    },
                    tint = when {
                        struck -> CoineProColors.TextDisabled
                        pinned -> CoineProColors.Gold
                        else -> CoineProColors.TextMuted
                    },
                    modifier = Modifier.size(13.dp),
                )
                LtrDirection {
                    Text(
                        text = wire,
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            struck -> CoineProColors.TextDisabled
                            pinned -> CoineProColors.Gold
                            else -> CoineProColors.TextMuted
                        },
                    )
                }
            }
        }
    }
}

/**
 * The price axis' own settings.
 *
 * Everything here is one tap deep from the toolbar and none of it is on the toolbar, which is the
 * trade this screen keeps making: an axis has six things a reader might want to change and a chart
 * has one screenful. The four modes are a chip row because they are exclusive; the rest are
 * switches and a precision row, in the order somebody would reach for them.
 */
@Composable
private fun PriceScaleSheetBody(
    state: ChartUiState,
    controller: ChartController,
    /** The stored zone id, and the way to change it. Null in a preview leaves the chips absent. */
    zoneId: String?,
    onSelectZone: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        SheetLabel("چه چیزی اندازه گرفته می‌شود")
        CoineProChipRow(
            options = PriceScaleMode.entries.map { CoineProChip(id = it.name, label = it.persianLabel) },
            selectedId = state.scaleMode.name,
            onSelect = { id ->
                PriceScaleMode.entries.firstOrNull { it.name == id }?.let(controller::setScaleMode)
            },
            compact = true,
        )
        Text(
            text = state.scaleMode.persianNote,
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )

        HorizontalDivider(color = CoineProColors.Border)

        SettingSwitch(
            label = "معکوس",
            note = "کف قیمت بالا و سقف پایین. برای خواندن جفت‌ارز وارونه.",
            checked = state.inverted,
            onChange = { controller.toggleInverted() },
        )
        SettingSwitch(
            label = "قفل نسبت قیمت به کندل",
            note = "بزرگ‌نمایی افقی و عمودی با هم حرکت می‌کنند، پس شیب خط روند ثابت می‌ماند.",
            checked = state.priceBarLock,
            onChange = controller::setPriceBarLock,
        )

        HorizontalDivider(color = CoineProColors.Border)

        // «جای محور» — items 95 and 96, and the note that used to stand here was out of date.
        //
        // It said the canvas drew the right-hand gutter and only that one, so a control would be
        // three silent options out of four. That stopped being true when `plotFrame` learned all
        // four cases: `ChartFrame.kt` places the gutter left, right or both and takes the width off
        // the plot before the viewport is sized, and the legend insets at `CoineProChart.kt:1817`
        // already read the side. Everything measures against the plot rectangle rather than the
        // canvas edge, so the gestures came along for free. What was missing was only this — a way
        // to say it. `ChartController.setScaleSide` had no caller at all.
        //
        // «یکی» is `MERGED` and is worth its own word rather than a fourth position: it is not
        // about where the gutter sits but about whether two overlaid instruments share an axis,
        // which is the difference between an honest comparison and one that flatters whichever
        // series was drawn second.
        SheetLabel("جای محور قیمت")
        CoineProChipRow(
            options = SCALE_SIDES.map { (side, label) -> CoineProChip(id = side.name, label = label) },
            selectedId = state.scaleSide.name,
            onSelect = { id ->
                ScaleSide.entries.firstOrNull { it.name == id }?.let(controller::setScaleSide)
            },
            compact = true,
        )

        HorizontalDivider(color = CoineProColors.Border)

        // «منطقهٔ زمانی» — item 107. A short list and not `ZoneId.getAvailableZoneIds()`: six
        // hundred rows in a bottom sheet is a search problem, and the four that matter to somebody
        // reading these two markets are the local one, the two sessions that move them, and UTC —
        // which is what every exchange timestamp is quoted in and the only one that never shifts.
        //
        // The label carries the current offset because a zone name alone does not answer the
        // question anybody is asking, which is «this candle closed at what o'clock for me».
        if (zoneId != null) {
            SheetLabel("منطقهٔ زمانی نمودار")
            CoineProChipRow(
                options = CHART_ZONES.map { (id, label) -> CoineProChip(id = id, label = label) },
                selectedId = zoneId,
                onSelect = { id -> id?.let(onSelectZone) },
                compact = true,
            )

            HorizontalDivider(color = CoineProColors.Border)
        }

        SheetLabel("رقم اعشار")
        CoineProChipRow(
            // Null is «خودکار», which is not the same as zero: the axis derives a precision from
            // the range, and a chart of a coin priced at 0.00004 needs eight where gold needs two.
            options = DECIMAL_CHOICES.map { count ->
                CoineProChip(
                    id = count?.toString() ?: AUTOMATIC_DECIMALS,
                    label = count?.toPersianDigits() ?: "خودکار",
                )
            },
            selectedId = state.decimals?.toString() ?: AUTOMATIC_DECIMALS,
            onSelect = { id -> controller.setDecimals(id?.takeIf { it != AUTOMATIC_DECIMALS }?.toIntOrNull()) },
            compact = true,
        )
    }
}

/**
 * Choosing a second instrument, from the strip the reader already keeps.
 *
 * The watchlist and nothing else, for the same reason [SymbolWheel] shows only the watchlist: a
 * full market search inside this sheet would be a second search screen, and the set somebody
 * compares against is by definition the set they already follow. Anything already on the chart,
 * and the chart's own symbol, are absent rather than shown greyed — a row that cannot be tapped is
 * a row that costs a tap to discover.
 */
@Composable
private fun ComparisonSheetBody(
    base: String,
    watchlist: List<String>,
    comparisons: List<ComparisonSeries>,
    basis: ComparisonBasis,
    onSetBasis: (ComparisonBasis) -> Unit,
    onAdd: (String) -> ComparisonRefusal?,
    onRemove: (String) -> Unit,
) {
    var refusal by remember { mutableStateOf<ComparisonRefusal?>(null) }
    val drawn = comparisons.map { it.symbol.uppercase() }.toSet()
    val offered = watchlist.filter { it.uppercase() != base.uppercase() && it.uppercase() !in drawn }
    val full = comparisons.size >= MAX_COMPARISONS

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        SheetLabel("چطور اندازه گرفته شود")
        CoineProChipRow(
            options = COMPARISON_BASES.map { CoineProChip(id = it.name, label = it.persianLabel) },
            selectedId = basis.name,
            onSelect = { id ->
                COMPARISON_BASES.firstOrNull { it.name == id }?.let(onSetBasis)
            },
            compact = true,
        )

        comparisons.forEachIndexed { index, series ->
            ComparisonRow(
                symbol = series.symbol,
                colour = Color(series.colour.toULong() shl COLOUR_SHIFT),
                index = index,
                onRemove = { onRemove(series.symbol) },
            )
        }

        HorizontalDivider(color = CoineProColors.Border)

        if (full) {
            Text(
                text = ComparisonRefusal.LIMIT_REACHED.persianMessage,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        } else if (offered.isEmpty()) {
            Text(
                text = "برای مقایسه، نمادی به دیده‌بان اضافه کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        } else {
            SheetLabel("از دیده‌بان")
            offered.forEach { symbol ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CoineProShapes.small)
                        .clickable { refusal = onAdd(symbol) }
                        .padding(vertical = CoineProSpacing.Half),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                ) {
                    CoineProAssetLogo(symbol = symbol, size = 20.dp)
                    Text(
                        text = BidiText.isolateLtr(symbol),
                        style = MaterialTheme.typography.labelMedium,
                        color = CoineProColors.TextSecondary,
                    )
                }
            }
        }

        refusal?.let { reason ->
            Text(
                text = reason.persianMessage,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.Sell,
            )
        }
    }
}

/** One compared instrument inside the sheet, with the way off it. */
@Composable
private fun ComparisonRow(symbol: String, colour: Color, index: Int, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Box(modifier = Modifier.size(COMPARISON_DOT).clip(CircleShape).background(colour))
        Text(
            text = BidiText.isolateLtr(symbol),
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            // A prose count of where this line sits in the four slots, so the legend and the chip
            // row can be matched up without relying on colour alone.
            text = "خط ${(index + 1).toPersianDigits()}",
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        Text(
            text = "حذف",
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.Sell,
            modifier = Modifier
                .clip(CoineProShapes.small)
                .clickable(onClick = onRemove)
                .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
        )
    }
}

/**
 * The compared instruments, under the chart, each in the colour of its own line.
 *
 * Present only while something is compared, so the ordinary chart is unchanged. The basis sits in
 * the same strip rather than in the sheet because it is the control a reader reaches for *while*
 * looking at the two lines — "which rose more" and "is gold gaining on the dollar" are two
 * different readings of the same picture, and making the second one cost a sheet is making it a
 * thing nobody discovers.
 */
@Composable
private fun ComparisonBar(
    comparisons: List<ComparisonSeries>,
    basis: ComparisonBasis,
    onSetBasis: (ComparisonBasis) -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = CoineProSpacing.Half),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        CoineProChipRow(
            options = COMPARISON_BASES.map { CoineProChip(id = it.name, label = it.persianLabel) },
            selectedId = basis.name,
            onSelect = { id -> COMPARISON_BASES.firstOrNull { it.name == id }?.let(onSetBasis) },
            compact = true,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = CoineProSpacing.Gutter),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            comparisons.forEach { series ->
                val colour = Color(series.colour.toULong() shl COLOUR_SHIFT)
                Row(
                    modifier = Modifier
                        .clip(CoineProPillShape)
                        .background(CoineProTint.fill(colour, CoineProColors.Stage))
                        .border(1.dp, CoineProTint.edge(colour), CoineProPillShape)
                        .clickable { onRemove(series.symbol) }
                        .padding(horizontal = CoineProSpacing.One, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    LtrDirection {
                        Text(
                            text = series.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = colour,
                        )
                    }
                    Icon(
                        painter = painterResource(CoineProIcons.Close),
                        contentDescription = "حذف " + series.label,
                        tint = colour,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}

/** A quiet heading inside a sheet, for a control that needs one word of context. */
@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        fontWeight = FontWeight.Normal,
    )
}

/** A labelled switch with the sentence that says what it does to the picture. */
@Composable
private fun SettingSwitch(
    label: String,
    note: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = CoineProColors.TextPrimary)
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.widthIn(min = SWITCH_MIN),
            colors = SwitchDefaults.colors(
                checkedThumbColor = CoineProColors.OnAccent,
                checkedTrackColor = CoineProColors.Accent,
                uncheckedThumbColor = CoineProColors.TextMuted,
                uncheckedTrackColor = CoineProColors.SurfaceElevated,
            ),
        )
    }
}

/**
 * This chart's apparatus, ready to be saved under [name].
 *
 * The id is generated here rather than taken from the name, because the store keys on it and two
 * layouts are allowed to share a name — a reader who saves «روزانه» twice has two layouts, not one
 * overwritten. The clock is read once so that a new layout's created and updated dates agree.
 */
private fun newLayout(state: ChartUiState, name: String): ChartLayout {
    val now = System.currentTimeMillis()
    return state.toLayout(id = "layout_" + now.toString(RADIX_36), name = name, createdAt = now, updatedAt = now)
}

/** The fifteen presets as a reader groups them, for the sheet behind «بیشتر». */
private val INTERVAL_GROUPS: List<Pair<String, List<Timeframe>>> = listOf(
    "دقیقه" to listOf(
        Timeframe.M1,
        Timeframe.M2,
        Timeframe.M3,
        Timeframe.M5,
        Timeframe.M10,
        Timeframe.M15,
        Timeframe.M30,
        Timeframe.M45,
    ),
    "ساعت" to listOf(Timeframe.H1, Timeframe.H2, Timeframe.H3, Timeframe.H4),
    "روز و بالاتر" to listOf(Timeframe.D1, Timeframe.W1, Timeframe.MN1),
)

/**
 * The three bases the chart may offer.
 *
 * `ABSOLUTE` is deliberately absent. It needs a second price axis to be drawn against — see
 * `ComparisonBasis.ABSOLUTE`, which says so — and offering it without one would put an instrument
 * priced at 0.42 on an axis running to 2,300, where its line leaves the plot entirely and the
 * reader concludes the comparison is broken.
 */
private val COMPARISON_BASES = listOf(
    ComparisonBasis.PERCENT,
    ComparisonBasis.INDEXED_100,
    ComparisonBasis.RATIO,
)

/** The precisions the axis offers, plus null for the derived one. See `ChartViewport.decimals`. */
/**
 * The four gutter placements, in the order a reader meets them.
 *
 * Right first because it is the default and where every terminal in this market puts it; merged
 * last because it is the one that is not about placement at all. Labels rather than glyphs: there
 * is no icon for "one shared axis" that anybody reads correctly the first time.
 */
/**
 * Above how many loaded bars the renderer starts merging half-pixel columns — item 176.
 *
 * `ColumnConflator` and its four draw paths were written, tested and correct, and `conflate`
 * defaulted to `false` with not one of the five call sites passing it, so the merge never ran on
 * anybody's phone.
 *
 * Decided from the series rather than from the viewport, because the viewport is private to the
 * canvas and this is the honest upper bound: a chart can never show more bars than are loaded, so
 * below this number no two points can share a column and every comparison conflation makes is one
 * that fails. Eight hundred is wider than any phone plot in pixels, which is the number that
 * matters — past it a reader panned out is drawing several vertices into one column, and that is
 * the case the merge exists for.
 */
internal const val CONFLATE_FROM_BARS = 800

/**
 * The zones offered for the time axis — item 107.
 *
 * Tehran first because Persian is this app's default locale and the reader is almost always in it.
 * New York and London because those two sessions are what move gold and the majors, and a reader
 * timing an entry around a session open needs the axis to say so without arithmetic. UTC last
 * because it is the one every exchange actually stamps and the only one with no daylight saving to
 * shift a candle boundary twice a year.
 *
 * Four and not `ZoneId.getAvailableZoneIds()`. Six hundred rows in a chip row is not a list, it is
 * a search screen, and none of the other five hundred and ninety-six answers a question anybody
 * reading these two markets has.
 */
private val CHART_ZONES: List<Pair<String, String>> = listOf(
    "Asia/Tehran" to "تهران",
    "America/New_York" to "نیویورک",
    "Europe/London" to "لندن",
    "UTC" to "UTC",
)

private val SCALE_SIDES: List<Pair<ScaleSide, String>> = listOf(
    ScaleSide.RIGHT to "راست",
    ScaleSide.LEFT to "چپ",
    ScaleSide.BOTH to "هر دو",
    ScaleSide.MERGED to "یکی",
)

private val DECIMAL_CHOICES: List<Int?> = listOf(null, 0, 2, 4, 8)

/** The chip id standing for "no pinned precision". Not a number, so it cannot collide with one. */
private const val AUTOMATIC_DECIMALS = "auto"

/**
 * What turns a stored ARGB `Long` into a Compose colour.
 *
 * Thirty-two, and it is not obvious: `Color(ULong)` takes the value in the *high* half of a 64-bit
 * word, so a plain `Color(0xFF4C9AFFL.toULong())` is transparent black. `core:datastore` and
 * `core:chart` both hand colours over as packed longs because neither may depend on Compose, so
 * this shift is the whole of the boundary and it is written down once.
 */
private const val COLOUR_SHIFT = 32

/** Base thirty-six, so a millisecond clock becomes a short id rather than thirteen digits. */
private const val RADIX_36 = 36

/** How large the dot standing for a comparison line is. */
private val COMPARISON_DOT = 10.dp

/** Keeps a switch from being squeezed to nothing beside a long Persian label. */
private val SWITCH_MIN = 48.dp

/** What the axis is measuring, in a word. The store keeps ids; the screen keeps the words. */
private val PriceScaleMode.persianLabel: String
    get() = when (this) {
        PriceScaleMode.REGULAR -> "عادی"
        PriceScaleMode.LOGARITHMIC -> "لگاریتمی"
        PriceScaleMode.PERCENT -> "درصدی"
        PriceScaleMode.INDEXED_100 -> "شاخص ۱۰۰"
    }

/** One sentence on what each mode is for, because the four names do not say it on their own. */
private val PriceScaleMode.persianNote: String
    get() = when (this) {
        PriceScaleMode.REGULAR -> "فاصله‌های برابر روی محور، مقدارهای برابر پول."
        PriceScaleMode.LOGARITHMIC -> "فاصله‌های برابر، درصدهای برابر. برای بازه‌های بلند که قیمت چند برابر شده."
        PriceScaleMode.PERCENT -> "صفر روی اولین کندل دیدهٔ شما، و بقیه درصد نسبت به آن."
        PriceScaleMode.INDEXED_100 -> "همان درصد، با مبدأ ۱۰۰ — آن‌طور که شاخص‌ها خوانده می‌شوند."
    }

/** How a compared instrument is expressed against this one. */
private val ComparisonBasis.persianLabel: String
    get() = when (this) {
        ComparisonBasis.PERCENT -> "درصد"
        ComparisonBasis.INDEXED_100 -> "شاخص ۱۰۰"
        ComparisonBasis.RATIO -> "نسبت"
        ComparisonBasis.ABSOLUTE -> "قیمت خام"
    }

/**
 * Why a comparison was refused, said to the reader.
 *
 * Each one is a different sentence on purpose. A single «نشد» would leave somebody at the cap
 * tapping the same row again, and somebody who mis-tapped the chart's own symbol looking for a
 * fault that is not there.
 */
private val ComparisonRefusal.persianMessage: String
    get() = when (this) {
        ComparisonRefusal.BLANK -> "نمادی انتخاب نشد."
        ComparisonRefusal.SAME_SYMBOL -> "همین نماد روی نمودار است."
        ComparisonRefusal.ALREADY_COMPARED -> "این نماد همین حالا روی نمودار است."
        ComparisonRefusal.LIMIT_REACHED ->
            "بیشتر از ${MAX_COMPARISONS.toPersianDigits()} نماد هم‌زمان خوانده نمی‌شود. یکی را حذف کنید."
    }

/** What the card above the chart says: the window, and its high and low. */
/**
 * Where these prices came from, and when the last one arrived.
 *
 * ### The accusation this answers
 *
 * «کندل‌سازی» — candle-manufacturing — is the loudest single accusation in Persian-language
 * reviews of this whole category of app: that the broker draws its own prices. It is usually
 * wrong, and an app with no provenance anywhere in it cannot say so credibly. A chart that simply
 * asserts a number gives a suspicious reader nothing to check and an honest operator no way to be
 * believed.
 *
 * One line settles it: the venue is named, so a reader can hold this chart against that venue's
 * own, and the last bar's time is printed, so "the price is stuck" and "the market is closed" stop
 * looking identical.
 *
 * ### Why the time is the *bar's* and not the request's
 *
 * A successful request that returns the same bars is not new data. Printing when the app last
 * asked would be reassuring and false — which is exactly the kind of thing the accusation is
 * about.
 *
 * ### And what the chart is *not* showing
 *
 * The venue and the clock answer "where did this come from". They do not answer the question that
 * actually produces the accusation, which is why the picture differs from the venue's own when
 * both are correct. TradingView's users left over exactly that — their volume did not match the
 * exchange's, nothing on the chart said why, and the conclusion drawn was that the data was
 * invented. A folded interval, a truncated page and a feed with no volume column are three honest
 * reasons for a difference and all three used to be silent. See [chartExclusions].
 *
 * The repaint mark sits in the same strip because it is the same kind of claim: this is the row
 * where the chart says what it knows and what it does not.
 */
@Composable
private fun ProvenanceLine(source: String, state: ChartUiState, signalOnChart: Boolean) {
    val series = state.series
    if (source.isEmpty() && series.isEmpty) return
    val lastBar = series.time.lastOrNull()
    val exclusions = remember(state.interval, state.series, state.replay.isOn, state.activeIndicators) {
        chartExclusions(state)
    }
    val mark = remember(state.activeIndicators, signalOnChart) { repaintMark(state, signalOnChart) }
    val subjects = remember(state.activeIndicators, signalOnChart) { repaintSubjects(state, signalOnChart) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CoineProSpacing.Half, end = CoineProSpacing.Half, top = CoineProSpacing.Half),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (source.isNotEmpty()) {
                // The venue name is a proper noun in Latin script inside a right-to-left line, so
                // it is isolated: without it a name ending in a digit reorders the whole row.
                Text(
                    text = stringResource(R.string.chart_source, BidiText.isolateLtr(source)),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextDisabled,
                    fontWeight = FontWeight.Normal,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            ) {
                if (!series.isEmpty) {
                    Text(
                        text = barCountLine(series.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextDisabled,
                        fontWeight = FontWeight.Normal,
                    )
                }
                lastBar?.let { time ->
                    LtrDirection {
                        Text(
                            text = barClock(time),
                            style = MaterialTheme.typography.labelSmall,
                            color = CoineProColors.TextDisabled,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
            }
        }
        // The mark, and only where something on this chart has earned it. `repaintMark` returns
        // null the moment a study that rewrites its own past is switched on, so the reader can
        // never read this line and apply it to the zigzag beside it.
        mark?.let { claim ->
            Text(
                text = claim.label + " — " + subjects.joinToString("، "),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.Buy,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = CoineProSpacing.Half),
            )
            Text(
                text = claim.note,
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextDisabled,
                fontWeight = FontWeight.Normal,
            )
        }
        if (exclusions.isNotEmpty()) {
            Text(
                text = exclusionsLine(exclusions),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = CoineProSpacing.Half),
            )
        }
    }
}

@Composable
private fun ChartCardHeading(state: ChartUiState) {
    val extent = state.visibleSeries.let { series ->
        if (series.isEmpty) null else series.low.min() to series.high.max()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CoineProSpacing.Half, end = CoineProSpacing.Half, bottom = CoineProSpacing.One),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = state.interval.label,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            fontWeight = FontWeight.Normal,
        )
        extent?.let { (low, high) ->
            LtrDirection {
                Text(
                    text = "H " + formatPrice(high, decimalsFor(high)) + "  ·  L " + formatPrice(low, decimalsFor(low)),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextDisabled,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Three readings of the market, computed from the bars on screen.
 *
 * Not a decoration and not a prediction. Trend strength is Wilder's ADX, which is the standard
 * answer to "is there a trend here at all"; volatility is today's ATR against its own recent
 * range, so "متوسط" means average *for this instrument* rather than against some absolute; the
 * bias is the two moving averages the chart already draws. Every one of them is arithmetic the app
 * already ships, said in a word — which is the whole point of putting them here rather than making
 * a reader switch three indicators on to learn the same thing.
 */
@Composable
private fun ReadingRow(state: ChartUiState) {
    val reading = remember(state.visibleSeries) { ChartReading.of(state.visibleSeries) } ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.OneHalf),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        ReadingCard("قدرت روند", reading.strengthLabel, reading.strengthColour(), Modifier.weight(1f))
        ReadingCard("نوسان", reading.volatilityLabel, CoineProColors.TextPrimary, Modifier.weight(1f))
        ReadingCard("سوگیری", reading.biasLabel, reading.biasColour(), Modifier.weight(1f))
    }
}

@Composable
private fun ReadingCard(label: String, value: String, tone: Color, modifier: Modifier = Modifier) {
    CoineProCard(
        modifier = modifier,
        shape = CoineProShapes.small,
        contentPadding = PaddingValues(CoineProSpacing.OneHalf),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            fontWeight = FontWeight.Normal,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = tone,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * The setup the reader has drawn, as three numbers and a ratio.
 *
 * Present only once a `longshort` drawing exists: this card is the drawing's consequence, and one
 * that appeared before there was a setup would be three em dashes taking up a card's worth of page.
 */
@Composable
private fun SetupCard(order: ChartOrder, onOpen: () -> Unit) {
    val buy = order.side == TradeSide.BUY
    val tone = if (buy) CoineProColors.Buy else CoineProColors.Sell
    CoineProCard(
        modifier = Modifier
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Half)
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = CoineProShapes.small,
        accent = CoineProColors.Gold,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ستاپ ترسیم‌شده", style = MaterialTheme.typography.labelMedium, color = CoineProColors.TextPrimary)
            LtrDirection {
                Text(
                    text = "R : R = 1 : " + MarketNumberFormatter.price(TradeFromChart.riskReward(order) ?: 0.0, 1),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.Gold,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = CoineProSpacing.OneHalf),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
        ) {
            SetupFigure("ورود", order.entry, CoineProColors.TextPrimary, Modifier.weight(1f))
            SetupFigure("حد ضرر", order.stopLoss, CoineProColors.Sell, Modifier.weight(1f))
            SetupFigure("هدف", order.takeProfit, tone, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SetupFigure(label: String, price: Double, tone: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            fontWeight = FontWeight.Normal,
        )
        LtrDirection {
            Text(
                text = formatPrice(price, decimalsFor(price)),
                style = MaterialTheme.typography.labelMedium,
                color = tone,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * The one way from the reading page into the working one.
 *
 * A card rather than a toolbar, and the counts on it are the reason it can be: "۴ اندیکاتور · ۲
 * ترسیم" tells a returning reader what state their chart is in, which a row of icons never did.
 */
@Composable
private fun StudioCard(
    indicators: Int,
    drawings: Int,
    onOpen: (() -> Unit)?,
    /** Opens the backtest report over the bars on screen, or null with nothing to run over. */
    onBacktest: (() -> Unit)?,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Half)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onOpen?.let { open ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(CoineProShapes.small)
                    .background(CoineProColors.SurfaceElevated)
                    .clickable(onClick = open)
                    .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.OneHalf),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                Icon(
                    painter = painterResource(DesignR.drawable.icon_sliders_horizontal),
                    contentDescription = null,
                    tint = CoineProColors.Gold,
                    modifier = Modifier.size(18.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "استودیوی چارت",
                        style = MaterialTheme.typography.labelMedium,
                        color = CoineProColors.TextPrimary,
                    )
                    Text(
                        text = studioSummary(indicators, drawings),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                        fontWeight = FontWeight.Normal,
                    )
                }
                Icon(
                    // Forward in the reading direction. See the same fix in the markets strip:
                    // the left caret is auto-mirrored and pointed back out of the row it opens.
                    painter = painterResource(CoineProIcons.ChevronForward),
                    contentDescription = null,
                    tint = CoineProColors.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        onBacktest?.let { open ->
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CoineProShapes.small)
                    .background(CoineProColors.SurfaceElevated)
                    .clickable(onClick = open)
                    .semantics { contentDescription = "بک‌تست" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(DesignR.drawable.tv_play),
                    contentDescription = null,
                    tint = CoineProColors.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CoineProShapes.small)
                .background(CoineProColors.SurfaceElevated)
                .clickable(onClick = onShare)
                .semantics { contentDescription = "اشتراک تصویر" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(DesignR.drawable.icon_camera),
                contentDescription = null,
                tint = CoineProColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Trend strength reads in the direction's own colour once there is a direction to read. */
@Composable
private fun ChartReading.strengthColour(): Color = when {
    strengthLabel == "بدون روند" -> CoineProColors.TextMuted
    isUp -> CoineProColors.Buy
    isDown -> CoineProColors.Sell
    else -> CoineProColors.TextPrimary
}

@Composable
private fun ChartReading.biasColour(): Color = when {
    isUp -> CoineProColors.Buy
    isDown -> CoineProColors.Sell
    else -> CoineProColors.TextMuted
}

/** «هیچ اندیکاتوری روشن نیست» / «۴ اندیکاتور · ۲ ترسیم» — Persian digits, because these are counts. */
private fun studioSummary(indicators: Int, drawings: Int): String {
    val parts = buildList {
        if (indicators > 0) add(indicators.toPersianDigits() + " اندیکاتور")
        if (drawings > 0) add(drawings.toPersianDigits() + " ترسیم")
    }
    return if (parts.isEmpty()) "اندیکاتور، ابزار، بازپخش، بک‌تست و نما اسکریپت" else parts.joinToString(" · ")
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

/**
 * Why the chart is empty, and what to do about it.
 *
 * Each case gets its own sentence, and only one of them offers a retry. Offering "try again" for a
 * symbol the platform does not carry sends the reader round a loop that cannot end.
 */
@Composable
private fun ChartFailure(error: ChartError, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(CoineProSpacing.Four),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = when (error) {
                ChartError.NETWORK -> "چارت بارگیری نشد."
                ChartError.UNSUPPORTED_SYMBOL -> "این نماد روی این پلتفرم چارت ندارد."
                ChartError.CHART_DISABLED -> "چارت این پلتفرم موقتاً در دسترس نیست."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
        if (error == ChartError.NETWORK) {
            Text(
                text = "تلاش دوباره",
                style = MaterialTheme.typography.labelLarge,
                color = CoineProColors.Accent,
                modifier = Modifier
                    .clip(com.coinepro.core.designsystem.CoineProPillShape)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.One),
            )
        }
    }
}

/**
 * How tall the chart card is.
 *
 * A little under half a phone. Tall enough that a hundred candles have shape, short enough that the
 * readings and the setup are on screen with it — which is the whole argument for a card rather than
 * a full-bleed chart.
 */
private val CHART_HEIGHT = 300.dp

/**
 * The chart's own control bar.
 *
 * ### Why it exists
 *
 * The tool rail, the indicator picker, the chart-type picker and the drawing list all lived in
 * sheets on this screen, and the only way to reach any of them was to leave for the studio — a
 * separate destination which, until this release, also held a separate controller, so whatever you
 * chose there was thrown away on the way back. The tools were two navigations and a lost state
 * away from the chart they act on. Now they are one tap, on the chart, with nothing disposed.
 *
 * ### Why a row of icons and not a menu
 *
 * A menu hides how many indicators are on. These carry their own counts — «۴» beside the
 * indicators glyph, «۷» beside the drawings — because the questions a reader actually has at this
 * bar are *what have I got on this chart* and *how do I get it off*, and a menu answers neither
 * until it is open.
 *
 * The studio stays, and stays last. It is the place for the long jobs — layouts, backtests,
 * scripts — that deserve a screen rather than a sheet.
 */
@Composable
private fun ChartToolBar(
    drawing: DrawingState,
    indicators: Int,
    drawings: Int,
    onOpen: (ChartSheet) -> Unit,
    onOpenStudio: (() -> Unit)?,
    onFullscreen: () -> Unit,
    /** What the price axis is measuring, so the control can show that it is not the default. */
    scaleMode: PriceScaleMode,
    /** Whether anything else on the axis has been changed — inverted, locked, or pinned. */
    axisAdjusted: Boolean,
    /** How many instruments are drawn over this one. Badged, like the indicator count. */
    comparisons: Int,
    onOpenScale: () -> Unit,
    onCreateAlert: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Half),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolBarButton(
            icon = DesignR.drawable.tv_tool_trend,
            label = "ابزار ترسیم",
            // Armed is a state the reader has to be able to see without looking at the chart —
            // it changes what the next tap on the canvas does.
            active = drawing.tool != null,
            onClick = { onOpen(ChartSheet.TOOLS) },
        )
        ToolBarButton(
            icon = DesignR.drawable.icon_sliders_horizontal,
            label = "اندیکاتورها",
            count = indicators,
            onClick = { onOpen(ChartSheet.INDICATORS) },
        )
        ToolBarButton(
            icon = DesignR.drawable.tv_chart_candles,
            label = "نوع چارت",
            onClick = { onOpen(ChartSheet.TYPE) },
        )
        ToolBarButton(
            icon = DesignR.drawable.tv_tool_cursor,
            label = "ترسیم‌ها",
            count = drawings,
            onClick = { onOpen(ChartSheet.DRAWINGS) },
        )
        ToolBarButton(
            icon = DesignR.drawable.icon_bookmark_simple,
            label = "چیدمان‌ها",
            onClick = { onOpen(ChartSheet.LAYOUTS) },
        )
        // The chart, with the whole screen. Placed here rather than as a corner control on the
        // canvas, because a tap target floating over a chart is a tap target that steals a
        // gesture from the drawing tools underneath it.
        onCreateAlert?.let { create ->
            ToolBarButton(
                icon = DesignR.drawable.tv_bell,
                label = "هشدار قیمت",
                onClick = create,
            )
        }
        // A second instrument over this one. It is the one thing on a chart that answers a
        // question about the world rather than about one symbol, and without it the reader opens
        // two charts and holds one of them in their head.
        ToolBarButton(
            icon = DesignR.drawable.tv_chart_line,
            label = "مقایسه",
            count = comparisons,
            onClick = { onOpen(ChartSheet.COMPARE) },
        )
        // The axis, and it opens a sheet rather than toggling.
        //
        // It was a one-tap logarithmic switch, which is two of the four questions an axis can be
        // asked; percent and indexed-100 had nowhere to live, and neither did inverting it or
        // pinning its precision. Six controls do not fit on a toolbar and would have made it the
        // row of buttons this screen is deliberately without — so the button stays one button and
        // the choices moved into the sheet behind it. Lit when the axis is not on its defaults,
        // which is what the old toggle's highlight was actually telling the reader.
        ToolBarButton(
            icon = DesignR.drawable.tv_chart_percent,
            label = "مقیاس قیمت",
            active = scaleMode != PriceScaleMode.REGULAR || axisAdjusted,
            onClick = onOpenScale,
        )
        ToolBarButton(
            icon = DesignR.drawable.tv_maximize2,
            label = "تمام‌صفحه",
            onClick = onFullscreen,
        )
        Spacer(Modifier.weight(1f))
        onOpenStudio?.let {
            ToolBarButton(
                icon = DesignR.drawable.tv_layout_grid,
                label = "استودیو",
                onClick = it,
            )
        }
    }
}

/** One control on the chart's bar, with the count of what it holds where there is one. */
@Composable
private fun ToolBarButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    count: Int = 0,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberCoineProHaptics()
    val ink = if (active) CoineProColors.Accent else CoineProColors.TextSecondary
    Row(
        modifier = Modifier
            .pressScale(interaction, CoineProPress.CHIP)
            .clip(CoineProShapes.small)
            .background(
                if (active) {
                    CoineProTint.fill(CoineProColors.Accent, CoineProColors.Stage)
                } else {
                    CoineProColors.Surface
                },
            )
            .clickable(interaction, null) {
                haptics.select()
                onClick()
            }
            .padding(horizontal = CoineProSpacing.One, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(17.dp),
            tint = ink,
        )
        // Zero is drawn as nothing rather than as «۰». A count is there to say how much is on the
        // chart, and a nought says the same thing as no badge while costing a glyph.
        if (count > 0) {
            Text(
                text = count.toPersianDigits(),
                style = MaterialTheme.typography.labelSmall,
                color = ink,
            )
        }
    }
}

/**
 * How often the demonstration marks are swept, in milliseconds.
 *
 * A second. The marks live eight seconds of which the last three are a fade — see
 * `DrawingActions.DEMONSTRATION_FADE_MS` — so a second is fine enough that a mark leaves the object
 * tree at about the moment it leaves the canvas, and coarse enough to be free.
 */
private const val DEMONSTRATION_TICK_MS = 1_000L

/** The fullscreen chart's floating controls. */
private val FLOATING_BUTTON = 36.dp
private val FLOATING_GLYPH = 18.dp

/** How opaque a floating control's plate is over the chart. Enough to read, little enough to see through. */
private const val FLOATING_ALPHA = 0.72f

/** The same, for the timeframe strip along the bottom. */
private const val STRIP_ALPHA = 0.82f

/**
 * The last bar's clock time, in the reader's own zone.
 *
 * Hours and minutes and nothing else: the question this answers is "is this feed live", and a
 * reader glancing at a chart at ten past three knows immediately whether 15:10 or 11:40 is the
 * right answer. A date would be the answer to a different question and would double the width of
 * the line.
 *
 * Latin digits and `Locale.US` on the pattern, deliberately. This sits in the same row as the
 * price extremes above it — it is a market figure, not prose — and `ofPattern` follows the default
 * locale, which on this app's Persian default would print «۱۵:۱۰» in a column of Latin numbers.
 */
private fun barClock(epochSeconds: Long): String = runCatching {
    java.time.Instant.ofEpochSecond(epochSeconds)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.US))
}.getOrDefault("")
