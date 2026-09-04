package com.coinepro.feature.chart

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.backtest.Backtest
import com.coinepro.core.chart.ActiveToolBar
import com.coinepro.core.chart.BarWindow
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ChartDecoration
import com.coinepro.core.chart.ChartLegendChange
import com.coinepro.core.chart.ChartLegendTarget
import com.coinepro.core.chart.ChartMarketStatus
import com.coinepro.core.chart.ChartOrder
import com.coinepro.core.chart.ChartReading
import com.coinepro.core.chart.ChartTransforms
import com.coinepro.core.chart.ChartZoomNudge
import com.coinepro.core.chart.ChartTypePicker
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.chart.DrawingImages
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.EventMark
import com.coinepro.core.chart.IndicatorPicker
import com.coinepro.core.chart.ObjectTree
import com.coinepro.core.chart.Replay
import com.coinepro.core.chart.ScaleSide
import com.coinepro.core.chart.SignalOverlay
import com.coinepro.core.papertrade.PaperPosition
import com.coinepro.core.chart.ToolRail
import com.coinepro.core.chart.axisFontSizeSp
import com.coinepro.core.chart.timeAxisHeight
import com.coinepro.core.chart.TradeFromChart
import com.coinepro.core.chart.TradeSide
import com.coinepro.core.chart.ComparisonBasis
import com.coinepro.core.chart.ComparisonSeries
import com.coinepro.core.chart.MAX_COMPARISONS
import com.coinepro.core.chart.PriceScaleMode
import com.coinepro.core.chart.decimalsFor
import com.coinepro.core.chart.formatPrice
import com.coinepro.core.chartevents.ChartEventController
import com.coinepro.core.chartevents.ChartEventSettings
import com.coinepro.core.chartevents.ChartEventSheet
import com.coinepro.core.chartevents.ChartEventState
import com.coinepro.core.chartevents.SERVED_EVENT_KINDS
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.datastore.ChartColourTemplate
import com.coinepro.core.datastore.ChartLayout
import com.coinepro.core.datastore.ChartEventPrefsStore
import com.coinepro.core.datastore.ChartLayoutStore
import com.coinepro.core.datastore.DrawingTemplate
import com.coinepro.core.datastore.DrawingImageStore
import com.coinepro.core.datastore.DrawingSyncStore
import com.coinepro.core.datastore.TimeZonePrefStore
import com.coinepro.core.datastore.DrawingTemplateStore
import com.coinepro.core.datastore.IntervalFavouritesStore
import com.coinepro.core.datastore.SymbolChartStateStore
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.SharedKeys
import com.coinepro.core.designsystem.sharedElement
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProConfirmDialog
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.CoineProWindowSize
import com.coinepro.core.designsystem.coineProWindowClass
import com.coinepro.core.designsystem.LtrDirection
import com.coinepro.core.designsystem.ProChartSignature
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.onPageAccent
import com.coinepro.core.designsystem.pageAccent
import com.coinepro.core.designsystem.pageAccentInk
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.help.CoineProHelpSheet
import com.coinepro.core.help.HelpCatalog
import com.coinepro.core.marketdata.CHART_TIME_ZONE
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.SECONDS_KEYS
import com.coinepro.core.marketdata.Timeframe
import com.coinepro.core.marketdata.customOf
import com.coinepro.core.symbols.MarketHours
import com.coinepro.core.symbols.SymbolClassifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
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
 * AI screen uses, so the two never disagree about where a stop is. [position] draws the reader's own
 * open trade through the same path, and wins where both exist.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartScreen(
    controller: ChartController,
    signal: SignalOverlay? = null,
    /**
     * The reader's open position on this instrument, drawn from the candle it opened on.
     *
     * The owner's report, and it is the right one: a chart shaded green above the entry and red
     * below it from the first bar on screen says «this whole chart is a position», which was never
     * true. The zone belongs to the candle the trade opened on and to the bars after it, and
     * nothing before. `setupSpan` is where that rule lives; this is what finally gives it a real
     * position to apply it to — until now the only thing that ever reached it was a signal opened
     * from another screen, so a reader who opened a trade *from the chart* watched it appear
     * nowhere on the chart.
     *
     * Takes precedence over [signal] when both are present. A position is a fact about the
     * reader's money and a signal is a suggestion; drawing both would put two entries, two stops
     * and two targets over the same bars, and the reader would have to work out which pair is
     * theirs.
     */
    position: PaperPosition? = null,
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
    /**
     * Take this chart's symbol to the AI, or null on a build with no AI behind it.
     *
     * The AI is contextual now rather than a tab: a reader asks about the market they are looking
     * at, not about "AI". The shell is what turns the symbol into a route — this screen knows the
     * symbol and nothing about the graph.
     */
    onAskAi: ((String) -> Unit)? = null,
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
     * The ring the symbol wheel turns through — item 2 of the owner's list.
     *
     * The wheel in the command band and in the fullscreen strip drew the reader's watchlist, and
     * a reader who had starred nothing saw no wheel at all — which was reported, fairly, as the
     * control not existing. The shell now hands a ring here that is the watchlist when there is
     * one worth turning through and the platform's popular markets when there is not, so the wheel
     * is always on the glass. [watchlist] keeps driving the split pane below the page, which is
     * the reader's own list and should stay empty until they fill it.
     */
    wheelSymbols: List<String> = watchlist,
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
    onPaperTrade: (
        (
            symbol: String,
            buy: Boolean,
            entry: Double,
            size: Double,
            stopLoss: Double,
            takeProfit: Double,
        ) -> Unit
    )? = null,
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
    /**
     * Where the marks on the time axis come from — items 118 and 119.
     *
     * `core:chartevents` was written, cached, tested and reachable from nothing: no call site in
     * the app ever set `ChartDecoration.events`, so a headline this app had already fetched and
     * could already place was never drawn on any chart. Null is the preview, the tests, and a
     * platform this build was not configured for — and the axis is then bare, which is right for a
     * fixture and is exactly what shipped for everybody else.
     */
    events: ChartEventController? = null,
    /**
     * Where the reader's own event switches are kept between launches.
     *
     * `ChartEventPrefsStore` is the third thing in this feature that was written, tested and
     * provided in Hilt and then read by nobody: nothing in the app has ever called
     * `ChartEventController.restoreVisibility`, so a reader who switched the economic calendar on
     * found it off again at the next launch — which is indistinguishable from a switch that does
     * not work. Null keeps the behaviour that shipped: the switches work for this visit and are
     * forgotten, which is right for a preview and a fixture and is what every reader had.
     *
     * The loop is deliberately one-directional — the sheet writes a kind, the store emits, the
     * controller takes the filter — so the stored row is the single answer and a failed write shows
     * up as a switch that comes back rather than as two halves of the app disagreeing.
     */
    chartEventPrefs: ChartEventPrefsStore? = null,
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
     * The image drawing waiting for a picture, or null — item 35.
     *
     * Android's photo picker, so no storage permission is asked for and none is held: the reader
     * grants one image at a time by choosing it. The bytes are read on an IO dispatcher and capped
     * on the way in, so a file the size of a video is a refusal rather than an allocation — the
     * cap is read before the read finishes, which is the only ordering that helps.
     */
    // Already bucketed into bars and already filtered by the reader's switches: the controller
    // places them, so this screen never re-places anything when a switch is flipped.
    val eventMarks by remember(events) { events?.marks ?: MutableStateFlow(emptyList()) }
        .collectAsStateWithLifecycle()
    /**
     * The switches themselves, and why the axis is bare when it is.
     *
     * Collected on the phone page and not only in the studio, because until now that is exactly
     * where they were: `ChartEventSettings` had one call site, inside `ChartStudioScreen`, so a
     * reader who never opened the professional terminal could not turn a kind on, could not turn
     * one off, and — worse — was never told *why* the strip under their candles was empty. Four
     * different answers were being collapsed into one blank axis: no network, this backend does not
     * publish the document, the read failed, and nothing happened this week.
     */
    val eventState by remember(events) { events?.state ?: MutableStateFlow(ChartEventState()) }
        .collectAsStateWithLifecycle()
    // The stored switches, put back and then kept in step. Collected rather than read once: the
    // sheet writes through the store, so this is also the path a tap on a switch takes back to the
    // controller — one direction, one answer, and no chance of the axis and the sheet disagreeing.
    if (events != null && chartEventPrefs != null) {
        LaunchedEffect(events, chartEventPrefs) {
            chartEventPrefs.kinds().collect { stored ->
                events.setVisibility(ChartEventKinds.visibility(stored))
            }
        }
    }
    /** The glyph a reader tapped, or null. Opens everything that landed in that bar. */
    var openedMark by remember { mutableStateOf<EventMark?>(null) }
    /**
     * The bars the events are placed against — what the canvas draws, not what the feed sent.
     *
     * Remembered rather than transformed inside the viewport callback, which fires on every frame
     * of a pan; five of the sixteen chart types re-grid the series, and re-gridding three hundred
     * bars per frame is a dropped frame per pan.
     */
    val eventSeries = remember(state.visibleSeries, state.chartType) {
        ChartTransforms.apply(state.visibleSeries, state.chartType)
    }

    var picturing by remember { mutableStateOf<Long?>(null) }
    val pickerContext = LocalContext.current
    val pickScope = rememberCoroutineScope()
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = picturing
        picturing = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        pickScope.launch(Dispatchers.IO) {
            val bytes = runCatching {
                pickerContext.contentResolver.openInputStream(uri)?.use { input ->
                    input.readNBytes(DrawingImageStore.MAX_SOURCE_BYTES + 1)
                }
            }.getOrNull() ?: return@launch
            if (bytes.size <= DrawingImageStore.MAX_SOURCE_BYTES) controller.attachImage(target, bytes)
        }
    }
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

    // The position wins over the signal, and both are optional — see the `position` parameter.
    //
    // Remembered on the position rather than rebuilt each recomposition: this reads three string
    // resources and allocates an overlay, and the chart recomposes on every tick.
    val positionSetup = position?.let {
        val entry = stringResource(R.string.chart_position_entry)
        val stop = stringResource(R.string.chart_position_stop)
        val target = stringResource(R.string.chart_position_target)
        remember(it, entry, stop, target) { positionOverlay(it, entry, stop, target) }
    }
    // The open rehearsal position, handed up by the replay bar.
    //
    // Held here rather than in the bar because the chart is what draws it, and *only* the drawing
    // is held: the session itself stays inside `ReplayLedgerPanel` and dies with it, which is the
    // rule `ReplayLedger` exists to enforce — a rehearsal must never become a record.
    var replaySetup by remember { mutableStateOf<SignalOverlay?>(null) }
    // The rehearsal wins over both. A reader in replay who has just opened a practice position is
    // looking at that position; a live paper position's levels drawn over a chart rewound to last
    // March would be two prices from a different month.
    val drawnSetup = replaySetup ?: positionSetup ?: signal

    // A zoom step asked for from the Drawings sheet. A serial rather than a boolean, so two taps
    // are two steps — see `ChartZoomNudge`.
    var zoomNudge by remember { mutableStateOf<ChartZoomNudge?>(null) }
    val zoomBy: (Float) -> Unit = { factor ->
        zoomNudge = ChartZoomNudge(serial = (zoomNudge?.serial ?: 0) + 1, factor = factor)
    }

    // The canvas' own width and the plot's, in pixels, so that what is drawn *over* the chart can
    // be placed against the chart's frame rather than against the box. See [gutterWidth].
    //
    // Plain `mutableFloatStateOf` and written from a draw-time callback: both are geometry, neither
    // is worth saving across a process death — the next layout pass sets them again before anything
    // is drawn — and a `rememberSaveable` here would restore a width measured on a different screen.
    var canvasWidthPx by remember { mutableFloatStateOf(0f) }
    var plotWidthPx by remember { mutableFloatStateOf(0f) }

    // Whether a finger is on the toolbar's symbol wheel. While it is, the big picker is drawn over
    // the plot — see `SymbolWheelOverlay`. Held here because the wheel is in the band and the
    // picker is over the chart, and neither is the other's parent.
    var wheelDragging by remember { mutableStateOf(false) }

    // Where a trade from this chart goes: the trade sheet when the chart carries a setup, the
    // terminal where the deployment reports one, and nowhere on a build with neither — in which
    // case the ring under the live bar is not drawn and the hub's card is dimmed. One handler for
    // both, so the two can never disagree about what «trade» means on this page.
    val onTrade: (() -> Unit)? = when {
        state.setup != null -> ({ sheet = ChartSheet.SETUP })
        else -> onOpenTerminal
    }

    // The chart itself, written once and placed in one of two frames.
    //
    // A lambda rather than two copies of the `when`: the loading, failure and drawing branches are
    // the part most likely to drift, and a fullscreen chart that had quietly stopped calling
    // `onLoadMore` would be a bug nobody found for months.
    val canvas: @Composable (Modifier) -> Unit = { canvasModifier ->
        Box(
            modifier = canvasModifier
                // How wide the canvas actually is, which with the plot's own width is the only way
                // anything drawn *over* the chart can know where the price gutter starts. See
                // [gutterWidth].
                .onSizeChanged { canvasWidthPx = it.width.toFloat() },
        ) {
            val gutter = with(LocalDensity.current) {
                gutterWidth(canvasWidthPx, plotWidthPx).toDp()
            }
            // TradingView's quote chip, top-right of the plot: `USDT ⌄`, 26 pt tall in a hairline.
            // There it changes the quote currency; here the quote is the market's own and the chip
            // opens the price-scale sheet, which is the nearest thing this chart has to a choice
            // of unit. Drawn only when there is a chart under it.
            if (!(state.loading && state.series.isEmpty) && !(state.error != null && state.series.isEmpty)) {
                QuoteChip(
                    quote = SymbolClassifier.classify(state.symbol).quote ?: "USD",
                    onClick = { sheet = ChartSheet.SCALE },
                    // Absolute: the price scale is on the right whichever way the page reads, and
                    // the chip belongs at that end of the chart.
                    //
                    // **Inside the plot, not over the gutter.** «اون بالا USDT کارت مستطیلی از کادر
                    // زده بیرون» — and it had. The chip is 74 pt wide and this chart's price gutter
                    // is about 60, so a chip right-aligned to the canvas straddled the hairline
                    // between the plot and the axis, covered the topmost price label, and on the
                    // full-screen chart ran to the very edge of the glass. Pushed in by the
                    // gutter's own width it sits in the plot's top corner, inside the frame, with
                    // the whole axis clear beside it.
                    modifier = Modifier
                        .align(AbsoluteAlignment.TopRight)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .absolutePadding(right = gutter)
                        .zIndex(1f),
                )
                // The watermark, bottom-left of the plot, where TradingView signs its chart.
                // Absolute for the same reason as the chip: the time axis reads left to right on
                // every locale and the mark sits at its origin.
                ChartWatermark(
                    lead = watermarkLead(canvasWidthPx),
                    modifier = Modifier
                        .align(AbsoluteAlignment.BottomLeft)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .zIndex(1f),
                )
            }
            // The picker over the plot while the wheel is turned. Above everything, inert, and
            // centred on the canvas rather than on the plot — the phone app's sits a little left
            // of the pane's middle for the same reason, since the price scale is not part of it.
            SymbolWheelOverlay(
                symbols = wheelSymbols,
                current = state.symbol,
                visible = wheelDragging,
                modifier = Modifier.align(Alignment.Center).zIndex(2f),
            )
            when {
                state.loading && state.series.isEmpty -> Loading()
                state.error != null && state.series.isEmpty -> ChartFailure(state.error!!, controller::retry)
                else -> CoineProChart(
                    series = state.visibleSeries,
                    modifier = Modifier.fillMaxSize(),
                    type = state.chartType,
                    // The legend's first line, as TradingView's phone sets it: the mark and the
                    // instrument's name — «Bitcoin / TetherUS» — not the ticker.
                    seriesLabel = SymbolClassifier.classify(state.symbol).description,
                    legendLogo = state.symbol,
                    decoration = ChartDecoration(
                        overlays = state.overlays,
                        signal = drawnSetup,
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
                        // Items 118 and 119. Already placed against these exact bars, which is the
                        // boundary `ChartDecoration.events` documents: bucketing needs the series
                        // and the reader's filter, and the renderer has neither.
                        events = eventMarks,
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
                        // The plot's width, for the overlays placed against the chart's frame.
                        // Written here rather than measured because only the renderer knows how
                        // wide the price labels made the gutter. See [gutterWidth].
                        plotWidthPx = view.plotWidth
                        controller.setVisibleWindow(BarWindow.visible(view.firstVisible, view.lastVisible))
                        // The same window, to the events reader. It answers a cache hit without
                        // touching a coroutine and drops a window already in flight, so it is safe
                        // to call on every frame of a drag.
                        //
                        // The *displayed* series, not the raw one: a viewport reports indices into
                        // what the canvas draws, and Renko, Kagi, Range, Line-break and
                        // Point-and-figure re-grid the bars before they are drawn. Handing the raw
                        // series over would put a headline on a different day on five of the
                        // sixteen chart types.
                        events?.onVisibleBars(
                            state.symbol,
                            eventSeries,
                            view.firstVisible,
                            view.lastVisible,
                        )
                    },
                    // Offered only where something fetched the events. The canvas draws the glyphs
                    // either way, and a glyph that answers nothing when you tap it is worse than
                    // no glyph.
                    onEventMark = events?.let { { mark -> openedMark = mark } },
                    // The purple ring under the live bar, on the same handler as the hub's trade
                    // card. Absent on a build with nowhere to trade from.
                    onTradeRing = onTrade,
                    zoomNudge = zoomNudge,
                    // Item 108. The window's move, and the market's state, on the legend itself —
                    // which is the only place the two-pane layout has, since it draws no header.
                    change = state.changePercent?.let { percent ->
                        val bars = state.visibleSeries.bars
                        val first = bars.firstOrNull()?.c ?: return@let null
                        val last = bars.lastOrNull()?.c ?: return@let null
                        ChartLegendChange(absolute = last - first, percent = percent)
                    },
                    marketStatus = MarketHours.statusOf(state.symbol).let { status ->
                        when {
                            status.open -> ChartMarketStatus.OPEN
                            status.weekend -> ChartMarketStatus.WEEKEND
                            else -> ChartMarketStatus.CLOSED
                        }
                    },
                    // Item 109. Settings opens the sheet that owns that kind of series; remove
                    // takes it off. A comparison is removed by symbol and an indicator by id, and
                    // `indicatorFor` is what turns a legend row's index back into one — see its
                    // note on why an index is not simply a position in `activeIndicators`.
                    onSeriesSettings = { target ->
                        sheet = if (target is ChartLegendTarget.Comparison) {
                            ChartSheet.COMPARE
                        } else {
                            ChartSheet.INDICATORS
                        }
                    },
                    onRemoveSeries = { target ->
                        when (target) {
                            is ChartLegendTarget.Comparison ->
                                state.comparisons.getOrNull(target.index)
                                    ?.let { controller.removeComparison(it.symbol) }
                            else -> state.indicatorFor(target)?.let(controller::toggleIndicator)
                        }
                    },
                )
            }
            if (state.loadingMore) {
                Box(modifier = Modifier.align(Alignment.TopStart).padding(CoineProSpacing.One)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
            // **The armed tool says so on the plot, not under it.**
            //
            // This bar existed and was correct and was in the wrong place: below the command band,
            // below the chart, off the bottom of a phone. So arming a tool closed the sheet and
            // *nothing visibly happened* — which is exactly how it was reported, three tools in a
            // row with no effect. A drawing tool is a mode, the reader's next tap belongs to it,
            // and a mode whose only indication is off screen is a mode nobody knows they are in.
            //
            // Inside the canvas box, so it follows the chart into fullscreen without being wired
            // twice, and at the top, where it is over the price the reader is about to draw on
            // rather than over the axis they need to read. It carries the point count — «نقطهٔ ۱
            // از ۲» — which is also the instruction: tap the chart.
            ActiveToolBar(
                tool = state.drawing.tool,
                placed = state.drawing.pending.size,
                onCancel = controller::cancelDrawing,
                onUndo = controller::undoDrawing,
                onHelp = onHelp,
                modifier = Modifier.align(Alignment.TopCenter),
            )
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

    /**
     * How tall the plot is on this device.
     *
     * A fraction of the glass rather than the three hundred points it was fixed at. Three hundred
     * is a third of a tall phone and two-fifths of a short one, so the same page looked like two
     * different designs depending on the device — and on the tall one, which is most of them, the
     * chart was the smallest thing on a screen that exists to show a chart. Read once per
     * configuration; a rotation is a configuration change, so it follows the phone round.
     *
     * A large window takes a **larger** fraction and a much higher ceiling, and the reason is that
     * the fraction was never about the chart — it was about the six bands that had to fit under it.
     * On a tablet the tools and the readings are columns beside the plot rather than bands beneath
     * it, so the height that was being reserved for them is height the plot can have. At 0.46 a
     * 1280-point tablet gave the chart 460 points and left five hundred blank; the same page at
     * [TABLET_PLOT_SCREEN_FRACTION] gives it eight hundred and needs no scroll to show the rest.
     */
    val configuration = LocalConfiguration.current
    val window = coineProWindowClass()
    val roomy = window.width != CoineProWindowSize.COMPACT
    val plotHeight = remember(configuration.screenHeightDp, roomy) {
        val fraction = if (roomy) TABLET_PLOT_SCREEN_FRACTION else PLOT_SCREEN_FRACTION
        val floor = if (roomy) TABLET_PLOT_MIN else PLOT_MIN
        val ceiling = if (roomy) TABLET_PLOT_MAX else PLOT_MAX
        (configuration.screenHeightDp * fraction).dp.coerceIn(floor, ceiling)
    }

    /** Whether the price axis is off its defaults — inverted, locked, or a pinned precision. */
    val axisAdjusted = state.inverted || state.priceBarLock || state.decimals != null

    // The three readings, computed from the bars on screen rather than from the whole series, and
    // remembered so a pan does not run Wilder's ADX on every frame.
    val reading = remember(state.visibleSeries) { ChartReading.of(state.visibleSeries) }

    /**
     * Everything on this page that is *read* rather than *touched*.
     *
     * Hoisted into one lambda because it now has two homes: under the plot on a phone, and in its
     * own column on a large window — see [ChartWorkbench]. Two copies of these three blocks is how
     * a tablet ends up drawing the readings panel twice, once in each place, and the duplicate
     * would look like a rendering bug rather than like the two call sites it is.
     */
    val analysisBlocks: @Composable () -> Unit = {
        // The readings, and they are the one block on this page that gains weight rather than
        // losing it. See `ChartReadingsPanel`: they answer the question the reader arrived with,
        // and until now they were three cards styled exactly like the row of sheet-openers above
        // them. Null under sixty bars — `ChartReading.of` refuses to name a market it cannot read,
        // and a panel of three em dashes would be worse than none.
        reading?.let { values ->
            ChartReadingsPanel(
                reading = values,
                interval = state.interval,
                modifier = Modifier.padding(
                    horizontal = CoineProSpacing.Gutter,
                    vertical = CoineProSpacing.OneHalf,
                ),
            )
        }
        state.setup?.let { order -> SetupCard(order, onOpen = { sheet = ChartSheet.SETUP }) }
        onOpenStudio?.let { open ->
            StudioRow(
                summary = studioSummary(state.activeIndicators.size, state.drawing.drawings.size),
                onOpen = open,
                modifier = Modifier.padding(
                    horizontal = CoineProSpacing.Gutter,
                    vertical = CoineProSpacing.Half,
                ),
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
            symbols = wheelSymbols,
            onSelectSymbol = switchSymbol.takeIf { controllerFor != null || onSelectSymbol != null },
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
    // The columns a large window can pay for, and the page in the middle of them. On a phone this
    // is a pass-through: `ChartWorkbench` hands the page the whole modifier and composes neither
    // column, so nothing below this line behaves differently on the device the page was designed
    // for.
    ChartWorkbench(
        modifier = pageModifier,
        tools = { railModifier ->
            ChartToolColumn(
                state = state,
                controller = controller,
                templates = armedTemplates,
                defaultTemplateId = armedDefault?.id,
                onHelp = onHelp,
                modifier = railModifier,
            )
        },
        readings = { columnModifier ->
            ChartReadingsColumn(modifier = columnModifier) { analysisBlocks() }
        },
    ) { workbenchModifier, columns ->
    Column(
        modifier = workbenchModifier
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
                onRedo = controller::redo,
            ),
    ) {
        // No header. TradingView's phone chart starts at the top of the screen: the instrument's
        // name and its price are the legend's first two lines, and the symbol and interval sit on
        // the toolbar under the plot. The header row this screen used to draw — mark, ticker,
        // price, change — said the same things a second time a centimetre above them.
        // TradingView closes its header with a one-point rule (`#2E2E2E` on `#0F0F0F`); this
        // system's strong border is the same step above the page.
        HorizontalDivider(color = CoineProColors.BorderStrong, thickness = 1.dp)

        // The plot, bled to both edges of the phone.
        //
        // It used to sit in a rounded card with a gold hairline, floating on the page, and the
        // argument for that was that it made the chart an *object* the rows below could belong to.
        // On a phone it did the opposite: it spent thirty-two points of width and a radius on
        // framing something that already has a frame — its own axes — and it left the plot under
        // half the glass with six bands of controls stacked beneath. Every terminal in this
        // category gives the plot the full width for the same reason: the price gutter and the time
        // axis *are* the border, and a second one around them is decoration on a measuring
        // instrument.
        //
        // The two hairlines are what is left of the card. They say where the plot stops without
        // costing it any width, and they are what the band below attaches to.
        HorizontalDivider(color = CoineProColors.Border)
        canvas(
            Modifier
                .fillMaxWidth()
                .height(plotHeight)
                .background(CoineProColors.Terminal)
                // The chart alone, recorded into a layer. Sharing the whole screen would hand
                // over the header and the toolbar; sharing this hands over the chart.
                .drawWithContent {
                    chartLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(chartLayer)
                },
        )
        HorizontalDivider(color = CoineProColors.Border)
        // No teaching banner on this screen, and it is the only screen in the app without one.
        //
        // The banner is a good mechanism and it stays everywhere else: one sentence, in place, put
        // away for good once read. But this page's entire product is vertical space. Even under the
        // plot the strip cost about eight per cent of the glass on every visit until the day it was
        // dismissed, on the one screen a reader opens ten times a day. What it said — that the last
        // candle has not closed — is a fact about the *bars*, and it is already in the caption
        // band immediately below, which is where a reader looking at a half-formed candle would
        // look for it. The «؟» in the header still opens the help for anyone who wants it.
        //
        // What the picture above is: the bar length and the span it is drawn at, its high and low,
        // where the prices came from and when the last one arrived. One caption band where there
        // were two — the heading used to sit *above* the plot inside a card and the provenance
        // below it, which put the same kind of quiet fact on both sides of the thing it describes.
        //
        // On a phone only the head is drawn here — the bar length, the span, the high and the low,
        // the four facts that change every time the reader pans. The provenance half — the venue,
        // the bar count, the clock, the repaint claim, the exclusions — is drawn once, inside the
        // disclosure below, because it is answered once and then never looked at again.
        ChartUnderline(
            state = state,
            source = controller.sourceName,
            signalOnChart = drawnSetup != null,
            detail = columns.hasReadings,
        )

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
                // Cleared by the bar itself on dispose, so leaving replay takes the drawing with it.
                onSetupOverlay = { replaySetup = it },
            )
        }

        // One band, where there were four.
        //
        // The bar lengths, the drawing tools, the studies, the chart type, the object list and the
        // way to a full screen — everything a hand reaches for *while reading a chart* — in a
        // single object attached to the plot above it. The span pills, «رفتن به تاریخ», the
        // comparison, the axis, layouts, alerts, the backtest and the screenshot are all one tap
        // behind its «بیشتر», because none of them is touched more than about once a month and each
        // of them used to own a permanent band of this page.
        //
        // The position is unchanged and was already right: below the chart, where a hand holding a
        // phone already is. The complaint about top-placed chart controls is explicit and repeated
        // in reviews of every app in this category — "our thumbs is not that long".
        ChartCommandBand(
            interval = state.interval,
            starred = starredWires,
            onSelectInterval = controller::setInterval,
            onMoreIntervals = { sheet = ChartSheet.INTERVAL },
            // Armed is a state the reader has to be able to see without looking at the chart: it
            // changes what the next tap on the canvas does.
            armedTool = state.drawing.tool != null,
            // Dropped where the palette is already open beside the plot. The button's two jobs are
            // to open the tools and to show that one is armed, and the permanent column does both
            // better — a button that opens a sheet duplicating a column already on screen is the
            // kind of leftover that makes a tablet layout look ported rather than designed.
            showDraw = !columns.hasTools,
            indicators = state.activeIndicators.size,
            drawings = state.drawing.drawings.size,
            onOpen = { sheet = it },
            onFullscreen = { fullscreen = true },
            onMore = { sheet = ChartSheet.MORE },
            // What the sheet would otherwise swallow. A reader who has set the chart to a year, or
            // put a second instrument on it, or inverted the axis, can see from the closed band
            // that something in there is not on its default.
            moreActive = state.range != null ||
                state.comparisons.isNotEmpty() ||
                state.scaleMode != PriceScaleMode.REGULAR ||
                axisAdjusted,
            // The symbol switcher, in the band the owner was pointing at. `switchSymbol` already
            // knows both routes — a controller swap where the app keeps one per symbol, a
            // navigation where it does not — so this tier does not care which build it is in.
            // Offered only where the screen was given a way to switch at all: on a preview or a
            // fixture there is none, and a control that silently does nothing is worse than one
            // that is absent.
            symbols = wheelSymbols,
            symbol = state.symbol,
            onSelectSymbol = switchSymbol.takeIf { controllerFor != null || onSelectSymbol != null },
            // The same quotes the watchlist strip below the page draws from, so the figure beside
            // the ticker in the scroll and the figure in the row it came from are one number.
            quotes = watchlistQuotes,
            onSymbolDrag = { wheelDragging = it },
        )

        // Only where they have nowhere better to be. On a window wide enough for the side column
        // these three are already drawn there, permanently, instead of below a plot the reader has
        // to scroll off the screen to reach them.
        //
        // Closed by default, and that is the whole of what paid for the taller plot. These blocks
        // are *read*, not touched: the trend reading, an open setup, the way into the studio. A
        // reader who wants them taps once and the disclosure remembers for the rest of the session;
        // a reader who came to look at candles never spends a point of glass on them. On a large
        // window the question does not arise — they are a column beside the plot, always open.
        if (!columns.hasReadings) {
            ChartReadingsDisclosure(hasSetup = state.setup != null) {
                ChartUnderline(
                    state = state,
                    source = controller.sourceName,
                    signalOnChart = drawnSetup != null,
                    head = false,
                )
                analysisBlocks()
            }
        }
        Spacer(Modifier.height(CoineProSpacing.Three))
    }
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
        if (placed.toolId == DrawingActions.IMAGE_TOOL) {
            // A frame with no picture is the empty box this tool used to be, so it asks at once —
            // the way a note asks for its words rather than waiting to be tapped a second time.
            picturing = placed.id
            pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else if (placed.text == null && DrawingActions.holdsText(placed.toolId)) {
            labelling = placed.id
        }
    }

    openedMark?.let { mark ->
        ChartEventSheet(mark = mark, onDismiss = { openedMark = null })
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
            title = stringResource(R.string.chart_tools_column_title),
            subtitle = stringResource(
                R.string.chart_tools_column_count,
                DrawingTools.ALL.size.toPersianDigits(),
            ),
            onDismiss = { sheet = null },
        ) {
            // The same palette the tablet keeps open in a column — see `ChartToolPalette`. One call
            // site rather than two, because every one of `ToolRail`'s parameters is a feature that
            // was unreachable until somebody passed it, and a rail that quietly omits a row is not
            // a failure anything would catch.
            ChartToolPalette(
                state = state,
                controller = controller,
                templates = armedTemplates,
                defaultTemplateId = armedDefault?.id,
                onHelp = onHelp,
                // The sheet closes itself the moment a tool is armed: it is covering the chart the
                // tool is about to be drawn on.
                onArmed = { sheet = null },
                // A zoom step closes the sheet too, for the same reason: the reader asked to see
                // the chart differently, and the sheet is what is in the way of seeing it.
                onZoomIn = {
                    zoomBy(ChartZoomNudge.STEP)
                    sheet = null
                },
                onZoomOut = {
                    zoomBy(1f / ChartZoomNudge.STEP)
                    sheet = null
                },
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
                range = state.range,
                onSelectRange = { range ->
                    controller.setRange(range)
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
                        { buy, entry, size, stopLoss, takeProfit ->
                            take(state.symbol, buy, entry, size, stopLoss, takeProfit)
                            sheet = null
                        }
                    },
                )
            }
        }

        // Everything the page used to spend a permanent band on and a reader touches monthly. See
        // `ChartMoreSheetBody` for the split, and for why a row with nothing behind it is drawn
        // disabled here rather than left out.
        ChartSheet.MORE -> CoineProSheet(
            title = stringResource(R.string.chart_more_title),
            subtitle = stringResource(R.string.chart_more_subtitle),
            onDismiss = { sheet = null },
        ) {
            ChartMoreSheetBody(
                range = state.range,
                onSelectRange = { range ->
                    controller.setRange(range)
                    // Closed, because a span redraws the whole picture and the reader has to be
                    // able to see what they asked for. The rows below leave the sheet open only
                    // where they open a second sheet of their own.
                    sheet = null
                },
                // «رفتن به تاریخ» off replay — backlog 105. The replay bar carries its own copy of
                // this field, and two date boxes for one job is the clutter the owner asked to be
                // kept out.
                bars = if (state.replay.isOn) emptyList() else state.visibleSeries.bars,
                onGoToDate = { index ->
                    controller.focusBar(index)
                    sheet = null
                },
                // The sheet stays open on both. Walking a change back is nearly always a sequence —
                // "not that, nor the one before it" — and a sheet that closed on each tap would
                // make a three-step undo six taps and three animations.
                onUndo = if (state.canUndo) ({ controller.undo() }) else null,
                onRedo = if (state.canRedo) ({ controller.redo() }) else null,
                comparisons = state.comparisons.size,
                scaleLabel = state.scaleMode.persianLabel,
                scaleAdjusted = state.scaleMode != PriceScaleMode.REGULAR || axisAdjusted,
                onOpen = { sheet = it },
                // Offered only when there is a price to alert on. A button that opens a composer
                // with an empty number is a button that makes the reader type what the chart
                // already knows.
                onCreateAlert = onCreateAlert?.let { create ->
                    state.lastPrice?.let { price ->
                        {
                            create(state.symbol, price)
                            sheet = null
                        }
                    }
                },
                // Offered only with bars to run over. A report over an empty series is five tabs
                // of dashes.
                onBacktest = { sheet = ChartSheet.BACKTEST }.takeIf { state.series.bars.isNotEmpty() },
                onShare = {
                    sheet = null
                    shareScope.launch {
                        ChartShare.share(context, chartLayer.toImageBitmap(), state.symbol)
                    }
                },
                onAskAi = onAskAi?.let { ask ->
                    {
                        sheet = null
                        ask(state.symbol)
                    }
                },
                onOpenStudio = onOpenStudio?.let { open ->
                    {
                        sheet = null
                        open()
                    }
                },
                // Offered only where something actually fetches events. On a preview, a fixture or
                // a platform this build was not configured for there is nothing behind the row,
                // and a settings page for a feed that does not exist is worse than no row.
                onEvents = { sheet = ChartSheet.EVENTS }.takeIf { events != null },
                // Counted over what a backend serves rather than over all five, so the figure on
                // the closed row is one the reader can actually reach. See `SERVED_EVENT_KINDS`.
                eventKinds = eventState.visibility.kinds.count { it in SERVED_EVENT_KINDS },
                // And why the strip under the candles is empty, when it is: said on the row rather
                // than only behind it, so a reader on a backend that does not publish the document
                // is told so instead of concluding nothing has happened.
                eventNotice = eventState.notice,
                onOpenTerminal = onOpenTerminal?.let { open ->
                    {
                        sheet = null
                        open()
                    }
                },
                // «معامله با کارگزار» opens the venue list rather than jumping straight into this
                // app's own terminal. The in-app route is still the first thing on that sheet where
                // there is one — see `TradePartnersSheetBody` for why it is above the three cards
                // and not below them.
                onTrade = { sheet = ChartSheet.PARTNERS },
                // Offered with bars to rewind through and not already rewinding. `Replay.enter`
                // wants thirty bars and returns null under that; the tile is dimmed on the same
                // condition rather than opening a mode that then does nothing.
                onReplay = {
                    controller.enterReplay()
                    sheet = null
                }.takeIf { !state.replay.isOn && state.series.bars.size >= Replay.MINIMUM_BARS },
                onHelpCenter = {
                    sheet = null
                    onHelp(CHART_HELP_ID)
                },
            )
        }

        ChartSheet.PARTNERS -> CoineProSheet(
            title = stringResource(R.string.chart_partners_title),
            subtitle = stringResource(R.string.chart_partners_subtitle),
            onDismiss = { sheet = null },
        ) {
            TradePartnersSheetBody(
                onTradeHere = onTrade?.let { trade ->
                    {
                        sheet = null
                        trade()
                    }
                },
                tradeHereLabel = stringResource(R.string.chart_partners_here),
            )
        }

        ChartSheet.EVENTS -> CoineProSheet(
            title = stringResource(R.string.chart_events_title),
            subtitle = stringResource(
                R.string.chart_events_subtitle,
                // A prose count, so Persian digits — unlike every figure on the chart above.
                eventState.visibility.kinds.count { it in SERVED_EVENT_KINDS }.toPersianDigits(),
            ),
            onDismiss = { sheet = null },
        ) {
            events?.let { controllerForEvents ->
                // The same section the studio draws, at its one other call site. No dismiss on a
                // switch: turning two kinds on is two taps, and the marks appear on the axis behind
                // the sheet either way — a sheet that closed after each one would make the reader
                // reopen it to see what the first switch did.
                ChartEventSettings(
                    visibility = eventState.visibility,
                    onChange = { next ->
                        // Applied at once so the marks appear under the reader's finger, and
                        // written kind by kind so an id this build does not know about — a newer
                        // build's sixth kind — is left on the row rather than overwritten.
                        controllerForEvents.setVisibility(next)
                        chartEventPrefs?.let { store ->
                            val moved = ChartEventKinds.changes(eventState.visibility, next)
                            storeScope.launch {
                                moved.forEach { (id, on) -> runCatching { store.setKind(id, on) } }
                            }
                        }
                    },
                    // Why the axis is bare, when it is: offline, this backend does not serve the
                    // document, the read failed, or nothing happened in this window. Four
                    // sentences, and «هیچ خبری نبود» is the only one that is not a fault.
                    notice = eventState.notice,
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
                // The image tool keeps its picture id and its caption in the same field, so the
                // sheet is shown only the caption. Editing words must not detach the photo.
                initial = DrawingImages.captionIn(drawing.text) ?: drawing.text.orEmpty(),
                // The icon tool keeps its glyph in the same field a note keeps its words, so the
                // row of marks is offered *above* the keyboard rather than instead of it — a reader
                // who wants a mark the row does not carry can still type one.
                icons = DrawingActions.holdsIcon(drawing.toolId),
                onSave = { text ->
                    val kept = DrawingImages.idIn(drawing.text)
                    val value = if (kept != null) DrawingImages.textFor(kept, text) else text
                    controller.onDrawing(DrawingActions.setText(state.drawing, id, value))
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
 * ### It is a window of its own now, and that is the fix for two reported defects at once
 *
 * This used to be a branch inside the page: `if (fullscreen) FullscreenChart(...) else page`. The
 * page is composed inside the app shell's `NavHost`, and that `NavHost` is one child of a `Column`
 * inside a `Scaffold` — under a `TopAppBar`, the offline bar and the venue-feed bar. So
 * `fillMaxSize()` here filled *the slot the page had*, and «تمام‌صفحه» was a chart with the page's
 * own furniture taken off and the **app's** furniture still standing above it.
 *
 * That is the whole of «می‌ره تو صفحهٔ خانه». The system Back was being intercepted correctly — the
 * [BackHandler] below has been here all along — but the reader was not pressing the system Back.
 * They were pressing the arrow they could *see*, in the top bar that fullscreen never covered, and
 * that arrow is the shell's own `popBackStack()`. The chart route is entered with the chart tab
 * popped inclusive, so the entry behind it is the home tab: press it and you land on «خانه». No
 * amount of work on `BackHandler` could have fixed a button that is not a back handler.
 *
 * A `Dialog` with the platform width and the decor fitting both switched off is a window over the
 * activity, so it covers the top bar, the bars above it and the navigation bar. There is now no
 * stray arrow to press, the system Back and the gesture both land on `onDismissRequest`, and the
 * `BackHandler` stays as the belt to that brace. It is also the only way to reach the whole glass
 * from inside this module: the `Scaffold` belongs to the app shell, and a screen cannot climb out
 * of its own slot.
 *
 * The sheets this mode opens — tools, bar lengths — are composed in [ChartScreen]'s body, outside
 * this window, and they are shown *after* it: a later window is the one on top, so they open over
 * the fullscreen chart exactly as they open over the page.
 *
 * ### What is kept and what goes
 *
 * Kept: the timeframe strip, the tool bar, the symbol and its price, and a way out. Everything
 * else — the statistics, the setup card, the studio card — is a *page* around a chart, and the
 * point of this mode is that there is no page.
 *
 * The controls float over the chart rather than taking rows from it: a fullscreen mode that spent
 * a fifth of its height on chrome would be the card again with fewer features. What has changed is
 * that the top corner is no longer two bare round buttons over the candles — it carries a plate
 * naming the instrument, its price and its move, because with the app's own header gone there was
 * nothing on this screen that said *what* was being drawn. A chart with no name on it is the one
 * thing a full-screen chart must not be.
 *
 * Everything is inset past the system bars. The window fits no decor, so without it the plate
 * would sit under the status bar's clock and the bottom strip under the gesture handle.
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
    /** The reader's list, for the switcher at the top of the bottom strip. See `SymbolWheelBar`. */
    symbols: List<String> = emptyList(),
    /** How a tap on a neighbour is taken, or null where this build cannot switch instrument. */
    onSelectSymbol: ((String) -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(
            // The three that turn a dialog into a window. Without the first it is a card in the
            // middle of the screen; without the third it stops at the system bars and the thing
            // this mode is named after does not happen.
            usePlatformDefaultWidth = false,
            // A tap on the plot is a chart gesture, never a way out. The only ways out are the
            // control that says so and the system's own back.
            dismissOnClickOutside = false,
            dismissOnBackPress = true,
            decorFitsSystemWindows = false,
        ),
    ) {
        // Back leaves fullscreen before it leaves the chart. Without this the reader's first
        // instinct — the gesture that means "out of this" — takes them off the screen entirely,
        // losing the viewport, the armed tool and whatever they were reading.
        //
        // Kept even though the window's own `dismissOnBackPress` would do it: this is the handler
        // that runs while a chart gesture has the pointer, and it is the one that is here in the
        // source for the next person who reads this looking for the back behaviour.
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
                .windowInsetsPadding(WindowInsets.safeDrawing)
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

        // **There is no second identity plate, and that is the fix.**
        //
        // «چارت رو کامل تمام صفحه باز می‌کنیم، کلی پراکندگی و به‌هم‌ریختگی داره.» It did. This mode
        // drew a plate naming the instrument, its price and its move — over a chart whose own
        // legend was already naming the instrument, its price and its move, three points away in
        // the same corner. Two plates, the same three facts, one on top of the other, and beside
        // them the quote chip and the two floating controls: four surfaces competing for one
        // corner of the glass.
        //
        // The legend is the one that stays. It is the chart's, it follows the chart into this mode
        // without being wired twice, it carries the studies as well as the instrument, and it is
        // the thing a reader coming from any other terminal already looks at. The plate this
        // replaces existed because the app's own header is covered here; the legend answers that,
        // and answering it twice is what the owner photographed.

        // The timeframe strip at the bottom, where a thumb already is on a phone held in one
        // hand — and out of the top-left corner where the legend plate draws. The quick ranges go
        // under it, for the same reason and in the same order as on the page.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // **Solid, not translucent.** At 82% the chart's own time axis printed straight
                // through the range pills — dates and hours crossing the words, which is the other
                // half of «به‌هم‌ریختگی». A floating strip over a *chart* is not a floating strip
                // over a list: what is behind it is dense, high-contrast type, and there is no
                // alpha at which that reads as depth rather than as a rendering fault.
                .background(CoineProColors.Stage)
                // Past the gesture handle and the navigation bar. The window fits no decor, so
                // without this the range pills sit under the bar a reader swipes on and the last
                // row of the mode is the one row they cannot press.
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            // A hairline where the strip meets the plot. It is the whole of the elevation this
            // design system gives a floating surface, and without it the strip's translucent
            // ground blends into the volume bars at the foot of the chart.
            HorizontalDivider(thickness = HAIRLINE, color = CoineProColors.Border)
            // The same switcher the page's command band carries, in the same place relative to the
            // lengths. A control that disappears when the chart is made bigger is a control the
            // reader stops trusting is there.
            onSelectSymbol?.let { select ->
                SymbolWheelBar(symbols = symbols, current = state.symbol, onSelect = select)
            }
            IntervalRow(
                selected = state.interval,
                onSelect = controller::setInterval,
                onMore = { onOpenSheet(ChartSheet.INTERVAL) },
                starred = starred,
            )
            // The spans, in the shape they now have everywhere: outlined rectangles under the
            // filled length keys, so the two rows say at a glance that they answer different
            // questions. See `RangeChipRow`.
            RangeChipRow(
                selected = state.range,
                onSelect = controller::setRange,
                modifier = Modifier.padding(bottom = CoineProSpacing.Half),
            )
        }
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

/**
 * The sheets this screen can be showing, and there is now exactly one more than there were.
 *
 * [MORE] is the entry point the redesign turns on: everything a reader touches about once a month
 * used to own a permanent band under the plot, and it is all behind that one word now. Internal
 * rather than private because `ChartChrome.kt` names these in the callbacks it hands back.
 */
internal enum class ChartSheet { TYPE, INDICATORS, TOOLS, DRAWINGS, SETUP, BACKTEST, LAYOUTS, INTERVAL, SCALE, COMPARE, EVENTS, MORE, PARTNERS }

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
 * The interval strip: filled keys, the page's accent on the one in force.
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
 *
 * ### Why the pills became keys
 *
 * They used to be outlined capsules, gold on the selection — and directly under them sat a second
 * row of outlined capsules, identical in every respect, holding spans of history. Two controls
 * that answer completely different questions were drawn the same way, one above the other, and a
 * reader had to read both rows to tell which was which. The lengths are now solid keys and the
 * spans are outlined rectangles a tap away in the «بیشتر» sheet: different shape, different weight,
 * different place. See [RangeChipRow].
 */
@Composable
internal fun IntervalRow(
    selected: ChartInterval,
    onSelect: (ChartInterval) -> Unit,
    onMore: () -> Unit,
    /** The lengths pinned to the strip, in wire spellings. See [TimeframeFavourites]. */
    starred: List<String> = TimeframeFavourites.DEFAULT,
    /**
     * How much of the row this strip gets.
     *
     * It shares its row with the symbol scroll in the command band — see `ChartCommandBand` — and
     * takes the whole width everywhere else. `fillMaxWidth` stays below it because a weight already
     * bounds the width where one is given, and where none is the row still has to reach both edges.
     */
    modifier: Modifier = Modifier,
) {
    // Rebuilt only when the starred list or the selection changes, which are the two things that
    // change the row's contents.
    val shown = remember(selected, starred) { TimeframeFavourites.resolve(starred, selected) }
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            // Top as well as bottom. The row had only the bottom gap, so the first key's edge sat
            // hard against whatever was above it and the two read as one shape. Ten points on both
            // sides is what makes the two tiers of the band read as one object with air in it.
            .padding(top = CoineProSpacing.One, bottom = CoineProSpacing.One),
        // Tighter than the page's gutter, which is what lets the whole row sit on a phone without
        // having to be scrolled to reach the shortest frames.
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        contentPadding = PaddingValues(horizontal = CoineProSpacing.One),
    ) {
        items(shown, key = { it.wire }) { interval ->
            IntervalPill(
                text = interval.wire,
                active = interval == selected,
                onClick = { onSelect(interval) },
            )
        }
        item(key = "more") {
            // Not «بیشتر». The band now has a «بیشتر» of its own one tier below, opening a
            // different sheet, and two identical words in one object each leading somewhere else
            // is the kind of small confusion a reader never quite reports and never stops paying
            // for. This one says what it opens: the other bar lengths.
            IntervalPill(
                text = stringResource(R.string.chart_interval_other),
                active = false,
                latin = false,
                onClick = onMore,
            )
        }
    }
}

/**
 * How wide the price gutter is: the canvas, less the plot the renderer measured inside it.
 *
 * The chart's frame is not its box. `CoineProChart` takes the whole canvas and gives the rightmost
 * strip of it to the price axis, and how wide that strip is depends on the widest price label —
 * which is a run-time fact about the market, not a constant anything on this screen could write
 * down. Anything drawn *over* the chart that must respect the frame has to be told, and this is the
 * arithmetic that turns the two numbers the screen does have into the one it needs.
 *
 * Zero until the first draw has reported a plot width, and zero again whenever the two numbers
 * disagree — a stale plot width from the frame before a rotation, an axis switched off — because a
 * negative inset would push the thing being placed off the other side of the chart. An overlay in
 * the corner is the right answer while the gutter is unknown; one shoved off the canvas is not.
 */
private fun gutterWidth(canvasWidthPx: Float, plotWidthPx: Float): Float =
    (canvasWidthPx - plotWidthPx).coerceIn(0f, canvasWidthPx.coerceAtLeast(0f))

/**
 * TradingView's quote chip: the market's quote currency in a 26 dp hairline chip, 14 sp, with a
 * caret. Measured `74 × 26 pt` on the phone app, four points in from the top-right of the pane.
 */
@Composable
private fun QuoteChip(quote: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .padding(QUOTE_CHIP_INSET)
            .clip(CoineProShapes.small)
            .background(CoineProColors.Stage)
            .border(1.dp, CoineProColors.Border, CoineProShapes.small)
            .clickable(interaction, null, onClick = onClick)
            .height(QUOTE_CHIP_HEIGHT)
            .padding(horizontal = CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        LtrDirection {
            Text(
                text = quote,
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextPrimary,
                maxLines = 1,
            )
        }
        Icon(
            painter = painterResource(DesignR.drawable.icon_caret_down),
            contentDescription = stringResource(R.string.chart_quote_scale),
            tint = CoineProColors.TextPrimary,
            modifier = Modifier.size(12.dp),
        )
    }
}

private val QUOTE_CHIP_HEIGHT = 26.dp
private val QUOTE_CHIP_INSET = 4.dp

/**
 * The brand, bottom-left of the plot, where TradingView signs its chart.
 *
 * Measured off the phone app: the mark 12 pt in from the pane's left edge and 12 pt above the time
 * axis, drawn in the primary ink — `#0F0F0F` on white, at full strength, in every chart shot the
 * owner sent.
 *
 * ### One ink, and it is the owner's instruction
 *
 * «پایین چارت لوگو پرو چارت رو مشکی بکن». The gold mark beside a near-black name read as two
 * objects sitting on the candles rather than as one signature; [ProChartLockup.markTint] makes the
 * whole thing the primary ink, which is black on the light chart and white on the dark one.
 *
 * ### It opens and closes, exactly as TradingView's does
 *
 * TradingView's phone chart signs itself with the mark alone and expands to the full wordmark when
 * you tap it — tap again and it goes back. Both states are in the owner's own screenshots. So this
 * is a control, not a decal: [expanded] is remembered per composition, the mark alone is the
 * resting state, and the change is a size animation rather than a swap, so the name grows out of
 * the mark instead of appearing beside it.
 *
 * The target stays the mark's own size, which keeps a finger drawing across the bottom-left corner
 * of the plot from opening the signature: a watermark that eats drawing taps is worse than one that
 * never opens.
 */
@Composable
private fun ChartWatermark(
    /** How far in from the plot's leading edge the mark sits. See [watermarkLead]. */
    lead: Dp,
    modifier: Modifier = Modifier,
) {
    val axis = with(LocalDensity.current) { timeAxisHeight(axisFontSizeSp(isPriceAxis = false)).sp.toDp() }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberCoineProHaptics()
    Box(
        modifier = modifier
            // **Absolute, like the alignment above it.** `padding(start = …)` is the reading edge,
            // which on this Persian page is the *right* — so the inset was being applied to the
            // side the mark is not on, and the signature sat flush against the left edge of the
            // glass whatever number was written here. That is the other half of «خیلی به گوشهٔ
            // سمت چپ چسبیده»: not only was twelve points too few, they were never spent.
            .absolutePadding(left = lead)
            .padding(bottom = WATERMARK_INSET + axis)
            // **No clip.** There was a `CoineProShapes.small` here, and it was cutting the logo in
            // half: that shape is a ten-point radius, this box closed is exactly the mark's own
            // twenty-three points square, and a ten-point round on an eleven-and-a-half-point
            // half-width is very nearly a circle. So the corners ate the P — its stem runs down the
            // artwork's left edge and its foot sits in the bottom-left corner, which is precisely
            // what a rounded rectangle removes first. What was left read as a ring with an arrow
            // through it, and that is «لوگو پایهٔ P رو نداره». Opening the signature widened the box
            // and gave the letter its edge back, which is why the same logo looked right expanded
            // and wrong closed.
            //
            // Nothing was lost with the clip, because nothing was being clipped *to*: the
            // `clickable` below passes a null indication, so there is no ripple for a shape to
            // contain. It was shaping the artwork and only the artwork.
            .clickable(interaction, null) {
                haptics.select()
                expanded = !expanded
            },
        contentAlignment = AbsoluteAlignment.CenterLeft,
    ) {
        ProChartSignature(
            expanded = expanded,
            markSize = WATERMARK_MARK,
            tint = CoineProColors.TextPrimary,
            contentDescription = stringResource(
                if (expanded) R.string.chart_watermark_collapse else R.string.chart_watermark_expand,
            ),
        )
    }
}

/** Twelve points from the time axis, as the phone app sets it, and the floor for the lead. */
private val WATERMARK_INSET = 12.dp

/**
 * How far in from the plot's leading edge the signature sits.
 *
 * ### Twelve points was a corner, not a position
 *
 * «لوگو روی چارت رو ۱۰ تا ۲۰ درصد به سمت راست بکشید، خیلی به گوشهٔ سمت چپ چسبیده.» A flat twelve
 * points is about three per cent of a phone's width, which puts the mark hard into the angle where
 * the price plot meets the time axis — two rules crossing, with a logo wedged in the corner
 * between them. It reads as something that has slipped rather than something that has been placed.
 *
 * ### A share of the canvas, not a number of points
 *
 * A *proportion*, so the mark holds the same place on a 360-point phone, on a tablet and on the
 * full-screen chart, where a fixed inset would look tucked away on one and adrift on another.
 * [WATERMARK_INSET] is the floor, so a very narrow pane still keeps the mark off the axis rather
 * than on it.
 *
 * ### Six, not twelve
 *
 * Twelve was the middle of the band the owner named and it read as too far in: «لوگو خیلی به سمت
 * راست کشیده شده، یه ۱۰ درصد برگردون به سمت چپ.» The number is the *lead*, and the mark is drawn
 * to the right of it — 23 points wide, another six per cent of a phone — so a twelve per cent lead
 * puts the signature between twelve and eighteen per cent across, which is where the eye actually
 * measures it from. At six the mark occupies six to twelve, the low end of the band as seen rather
 * than as specified, and it is still clearly placed rather than wedged in the corner.
 *
 * It stays well clear of the first candle at any zoom this chart offers, and the tap target is
 * still the mark's own box — see [ChartWatermark].
 */
private const val WATERMARK_LEAD_FRACTION = 0.06f

/** [WATERMARK_LEAD_FRACTION] of a canvas [canvasWidthPx] wide, never less than [WATERMARK_INSET]. */
@Composable
private fun watermarkLead(canvasWidthPx: Float): Dp {
    if (canvasWidthPx <= 0f || !canvasWidthPx.isFinite()) return WATERMARK_INSET
    val lead = with(LocalDensity.current) { (canvasWidthPx * WATERMARK_LEAD_FRACTION).toDp() }
    return maxOf(lead, WATERMARK_INSET)
}

/** The mark on its own, closed; the name grows out of it at the same height. */
private val WATERMARK_MARK = 23.dp

/** The help entry behind the hub's «Help Center». */
private const val CHART_HELP_ID = "chart"

/**
 * One bar length: a solid key, not an outline.
 *
 * A filled block on a raised neutral, with the page's accent under whichever one is in force. The
 * fill is what makes it read as a *key* — something with two states that you press — where the
 * outline it used to have read as a chip and looked exactly like the span pills that sat beneath
 * it. It is also the only treatment on this page that carries a filled accent, which is what makes
 * "this is the length I am on" the loudest thing in the band.
 */
@Composable
private fun IntervalPill(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    /** Wire spellings are market figures and stay Latin; «بیشتر» is prose and must not be. */
    latin: Boolean = true,
) {
    // TradingView's interval chip, measured off the phone app's date-range sheet: a grey plate
    // with 12 pt corners, 44 pt tall, the length in bold 16 pt; the chosen one inverted to the
    // primary ink with the page's ground for its text.
    Box(
        modifier = Modifier
            .clip(CoineProShapes.medium)
            .background(
                if (active) CoineProColors.TextPrimary else CoineProColors.SurfaceElevated,
            )
            .clickable(onClick = onClick)
            .heightIn(min = INTERVAL_KEY_HEIGHT)
            .widthIn(min = INTERVAL_KEY_WIDTH)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
        contentAlignment = Alignment.Center,
    ) {
        val ink = if (active) CoineProColors.Stage else CoineProColors.TextPrimary
        if (latin) {
            LtrDirection {
                Text(
                    text = text,
                    style = MaterialTheme.typography.titleMedium,
                    color = ink,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = ink,
                fontWeight = FontWeight.Bold,
            )
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
    /** The span chips above the intervals — TradingView's «Date range», on the same sheet. */
    range: ChartRange? = null,
    onSelectRange: ((ChartRange) -> Unit)? = null,
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
        onSelectRange?.let { select ->
            RangeChipRow(selected = range, onSelect = select, contentPadding = PaddingValues(0.dp))
            HorizontalDivider(color = CoineProColors.Border)
            Text(
                text = stringResource(R.string.chart_interval_heading),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = CoineProColors.TextPrimary,
            )
        }
        if (starred != null && onStar != null) {
            StarredIntervalSection(starred = starred, hidden = hidden, onStar = onStar, onHide = onHide)
            HorizontalDivider(color = CoineProColors.Border)
        }
        SecondsIntervalSection(selected = selected, onSelect = onSelect)
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
 * The watchlist and nothing else, for the same reason [SymbolWheelBar] shows only the watchlist: a
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
internal fun SheetLabel(text: String) {
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
                checkedTrackColor = CoineProColors.AccentFill,
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

/**
 * The sub-minute lengths, at the head of the sheet, with a sentence about what they are.
 *
 * ### Why they are their own section rather than the first entries under «دقیقه»
 *
 * Because they are a different kind of thing and saying so is the honest design. Every other pill
 * on this sheet asks a server for a series that already exists; these five are built on the phone
 * out of the price feed, so a ten-second chart opened for the first time starts empty and fills as
 * the market trades, and its history is only as deep as the sittings that built it. A reader who
 * taps one and sees three candles has not found a bug, and the line under the row is what tells
 * them so — before they tap, which is the only useful place for it.
 *
 * The order is shortest first, like every other group here.
 */
@Composable
private fun SecondsIntervalSection(selected: ChartInterval, onSelect: (ChartInterval) -> Unit) {
    Text(
        text = stringResource(R.string.chart_interval_seconds),
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
        SECONDS_KEYS.forEach { count ->
            val interval = ChartInterval.Seconds(count)
            IntervalPill(
                text = interval.wire,
                active = interval == selected,
                onClick = { onSelect(interval) },
            )
        }
    }
    Text(
        text = stringResource(R.string.chart_interval_seconds_note),
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.TextMuted,
    )
    HorizontalDivider(color = CoineProColors.Border)
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
internal val PriceScaleMode.persianLabel: String
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
/**
 * The readings, folded away until asked for.
 *
 * ### Why a disclosure rather than a delete
 *
 * The plot needed the glass and these blocks were what it was spending it on — but none of them is
 * wrong, and two of them are the reason somebody stays on the page after they have looked at the
 * candles. So they are still here, one tap down, with the tap costing a single 44-point row.
 *
 * ### Why it opens itself when there is a setup on the chart
 *
 * A setup is the one thing in here that is *news*. A reader with an open position drawn on their
 * chart has a reason to see its numbers without hunting for them, and a fold that hid a live
 * position behind a chevron would be hiding the very thing the page is about. Everything else —
 * the trend reading, the studio row — is reference, and reference waits to be asked for.
 *
 * The state is remembered for the composition rather than persisted. A reader who opens it, scrolls
 * and comes back finds it as they left it; a reader who returns tomorrow gets the plot back at full
 * height, which is the state that is right nine visits out of ten.
 */
@Composable
private fun ChartReadingsDisclosure(hasSetup: Boolean, content: @Composable () -> Unit) {
    // Open, always, on arrival — item 5 of the owner's list. The fold stays so a reader who wants
    // the plot at full height can still have it, but the readings are what this page is for and a
    // panel that has to be discovered behind a chevron was reported as a panel that was not there.
    var open by rememberSaveable(hasSetup) { mutableStateOf(true) }
    val haptics = rememberCoineProHaptics()
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = CoineProColors.Border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.select()
                    open = !open
                }
                .padding(
                    horizontal = CoineProSpacing.Gutter,
                    vertical = CoineProSpacing.OneHalf,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.chart_readings_disclosure),
                style = MaterialTheme.typography.labelLarge,
                color = if (open) CoineProColors.TextPrimary else CoineProColors.TextSecondary,
            )
            Icon(
                painter = painterResource(
                    if (open) CoineProIcons.ChevronUp else CoineProIcons.ChevronDown,
                ),
                contentDescription = null,
                tint = CoineProColors.TextMuted,
                modifier = Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(visible = open) {
            Column { content() }
        }
    }
}

@Composable
private fun ChartUnderline(
    state: ChartUiState,
    source: String,
    signalOnChart: Boolean,
    /**
     * Whether the quiet half is drawn here too.
     *
     * The band is two kinds of fact. The **head** — bar length, span, and the window's high and low
     * — changes every time the reader pans, so it belongs against the plot where their eye already
     * is. The **detail** — the venue, the bar count, the clock on the last bar, the repaint claim
     * and any exclusions — is provenance: read once, trusted after, and worth about a tenth of the
     * glass on every visit thereafter.
     *
     * On a phone the detail moves into the readings disclosure and this is false. On a window with
     * a side column there is nothing to compete for, so it stays true and the band is whole.
     */
    detail: Boolean = true,
    /**
     * Whether the changing half is drawn here.
     *
     * False exactly once: the copy inside the disclosure, which carries the detail alone so the
     * bar length and the extremes are not printed twice on the same page.
     */
    head: Boolean = true,
) {
    val series = state.series
    if (source.isEmpty() && series.isEmpty) return
    val lastBar = series.time.lastOrNull()
    // The window's own extremes, which used to be the chart card's heading above the plot.
    val extent = state.visibleSeries.let { window ->
        if (window.isEmpty) null else window.low.min() to window.high.max()
    }
    val exclusions = remember(state.interval, state.series, state.replay.isOn, state.activeIndicators) {
        chartExclusions(state)
    }
    val mark = remember(state.activeIndicators, signalOnChart) { repaintMark(state, signalOnChart) }
    val subjects = remember(state.activeIndicators, signalOnChart) { repaintSubjects(state, signalOnChart) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = CoineProSpacing.Gutter,
                end = CoineProSpacing.Gutter,
                top = CoineProSpacing.One,
                bottom = CoineProSpacing.Half,
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // What is being drawn: how long one candle is, how much history was asked for, and what
        // the window's high and low are. The span is named beside the length on purpose — the two
        // controls that set them now sit in different places, and this is the line that says what
        // both of them currently are.
        if (head) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.range?.let { span -> state.interval.label + "  ·  " + span.label }
                        ?: state.interval.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextSecondary,
                    fontWeight = FontWeight.Normal,
                )
                extent?.let { (low, high) ->
                    LtrDirection {
                        Text(
                            text = "H " + formatPrice(high, decimalsFor(high)) +
                                "  ·  L " + formatPrice(low, decimalsFor(low)),
                            style = MaterialTheme.typography.labelSmall,
                            color = CoineProColors.TextDisabled,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
            }
        }
        if (!detail) return@Column
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

/** Trend strength reads in the direction's own colour once there is a direction to read. */
@Composable
internal fun ChartReading.strengthColour(): Color = when {
    strengthLabel == "بدون روند" -> CoineProColors.TextMuted
    isUp -> CoineProColors.Buy
    isDown -> CoineProColors.Sell
    else -> CoineProColors.TextPrimary
}

@Composable
internal fun ChartReading.biasColour(): Color = when {
    isUp -> CoineProColors.Buy
    isDown -> CoineProColors.Sell
    else -> CoineProColors.TextMuted
}

/** «هیچ اندیکاتوری روشن نیست» / «۴ اندیکاتور · ۲ ترسیم» — Persian digits, because these are counts. */
internal fun studioSummary(indicators: Int, drawings: Int): String {
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
                // No retry appears under this one — the guard below already excludes it, and that
                // is deliberate: this venue has no feed fine enough to build this bar length, so
                // a «تلاش دوباره» would be a button that cannot ever succeed.
                ChartError.INTERVAL_UNAVAILABLE ->
                    "این پلتفرم کندل این بازه را ندارد. بازهٔ بلندتری انتخاب کنید."
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
 * One bar-length key.
 *
 * Forty by forty-four: past the forty-four-point target on the axis a thumb actually misses on a
 * horizontal strip, and short enough that two tiers of controls still read as one band.
 */
// Forty-four tall and fifty-six wide: TradingView's interval chips, measured off the phone app.
private val INTERVAL_KEY_HEIGHT = 44.dp
private val INTERVAL_KEY_WIDTH = 56.dp

/**
 * How much of the phone the plot gets.
 *
 * Just under half. The page under it is a caption, one control band and one panel — none of which
 * needs a fixed share — so the fraction is set by what the plot itself needs: enough vertical room
 * for a hundred and twenty candles to have shape rather than to be a smear, and enough of the
 * screen left that the readings are on it without a scroll on a phone of ordinary height.
 *
 * A fraction rather than the three hundred points it used to be, because three hundred is a third
 * of a tall phone and two-fifths of a short one — the same layout looking like two different
 * designs. The bounds are what keep it sane at the extremes: a very short phone in landscape does
 * not get a chart with no page under it, and a tablet does not get a plot that runs off the glass.
 *
 * ### Why 0.72 and not 0.46
 *
 * Measured, on the rendered page at 411dp: the plot had about a third of the glass and the six
 * things under it had the rest. A charting app whose chart is a third of the screen reads as a
 * preview of a chart rather than as a terminal, and that one number explained more of "it does not
 * look finished" than every other finding put together.
 *
 * Nothing was deleted to pay for it. The teaching banner left this one screen, and the readings —
 * which are *read* rather than *touched* — went behind a disclosure that opens with one tap and
 * remembers. What is left above the fold is the header, the plot, its caption and the one control
 * band a thumb actually uses.
 */
private const val PLOT_SCREEN_FRACTION = 0.72f
private val PLOT_MIN = 260.dp
private val PLOT_MAX = 780.dp

/**
 * The same three numbers for a window that is not a phone.
 *
 * The old ceiling of 460 was the binding constraint on every tablet: the fraction wanted 590 points
 * on a 1280-tall tablet and 368 on an 800-tall one, and the clamp handed back 460 in the first case
 * and left five hundred points of the screen doing nothing. That ceiling was written for a phone,
 * where a plot taller than 460 pushes the whole control band off the bottom — and on a tablet the
 * control band is a column at the side, so there is nothing left for it to push.
 *
 * [TABLET_PLOT_MIN] is 380 rather than 260 for the opposite reason: a window this wide with a plot
 * a quarter of its height reads as a chart somebody forgot to finish laying out.
 */
private const val TABLET_PLOT_SCREEN_FRACTION = 0.62f
private val TABLET_PLOT_MIN = 380.dp
private val TABLET_PLOT_MAX = 900.dp

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
 * The one-pixel rule this design system calls elevation.
 *
 * A hairline and nothing else: no blur behind a floating surface and no shadow under it, which is
 * the surface rule the whole app is built on and the one the gate in `check-motion-policy.sh`
 * enforces. One device pixel would disappear on a three-times-density phone, so it is a point.
 */
private val HAIRLINE = 1.dp

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
