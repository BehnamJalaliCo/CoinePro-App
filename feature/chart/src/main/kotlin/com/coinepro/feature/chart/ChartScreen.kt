package com.coinepro.feature.chart

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import com.coinepro.core.designsystem.CoineProFold
import com.coinepro.core.designsystem.inEnglish
import com.coinepro.core.designsystem.CoineProSkeleton
import com.coinepro.core.designsystem.coineProFold
import com.coinepro.core.designsystem.CoineProTeachingStrip
import com.coinepro.core.designsystem.TeachingSurface
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Rect
import com.coinepro.core.chart.IndicatorPane
import kotlin.math.roundToInt
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.backtest.Backtest
import com.coinepro.core.chart.ActiveToolBar
import com.coinepro.core.chart.BarWindow
import com.coinepro.core.chart.ChartAlertLine
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
import com.coinepro.core.chart.DrawingTool
import com.coinepro.core.chart.drawableRes
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
import androidx.compose.ui.graphics.asAndroidBitmap
import com.coinepro.core.chart.ArenaScore
import com.coinepro.core.chart.RasadCoach
import com.coinepro.core.chart.TradeFacts
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
import com.coinepro.core.designsystem.proseDigits
import com.coinepro.core.datastore.ChartColourTemplate
import com.coinepro.core.datastore.ChartLayout
import com.coinepro.core.datastore.ChartEventPrefsStore
import com.coinepro.core.datastore.ChartLayoutStore
import com.coinepro.core.datastore.DrawingTemplate
import com.coinepro.core.datastore.DrawingImageStore
import com.coinepro.core.datastore.DrawingSyncStore
import com.coinepro.core.datastore.TimeZonePrefStore
import com.coinepro.core.datastore.DrawingTemplateStore
import com.coinepro.core.datastore.IndicatorFavouritesStore
import com.coinepro.core.datastore.IntervalFavouritesStore
import com.coinepro.core.datastore.SymbolChartStateStore
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.SharedKeys
import com.coinepro.core.designsystem.sharedElement
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.ShareCard
import com.coinepro.core.designsystem.ShareCardContent
import com.coinepro.core.designsystem.ShareCardTone
import com.coinepro.core.designsystem.LocalCoineProPalette
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProNote
import com.coinepro.core.designsystem.CoineProMotionSpecs
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.coinepro.core.chart.DrawingActions
import com.coinepro.core.chart.DrawingIconPicker
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.common.BidiText
import com.coinepro.core.designsystem.LocalToaster
import com.coinepro.core.designsystem.CoineProToast
import com.coinepro.core.designsystem.ToastTone
import com.coinepro.core.designsystem.CoineProCelebration
import com.coinepro.core.designsystem.CoineProConfetti
import androidx.annotation.StringRes
import com.coinepro.core.datastore.ReaderMode

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
     * Opens the depth ladder on this symbol, or null where this venue publishes no Level II.
     *
     * ### Why it arrives as a parameter now
     *
     * It was a worded button in the app bar's action slot — the only permanent control on the chart
     * route that was not part of the chart — and it cost forty-eight points of height on every
     * phone, on every symbol, for a screen a reader opens occasionally. The owner's recording is
     * what settled it: the plot had about forty-five per cent of the glass and this row was one of
     * the five things holding it there.
     *
     * So it moves where the rest of the once-a-month controls already live — the «…» hub — and on a
     * window wide enough it stays what it already was, a docked panel beside the plot. Null keeps
     * the tile out altogether, which is right for the forex venue: its broker publishes no order
     * book and a tile whose only destination is a refusal is worse than an absent one.
     */
    onOpenDepth: (() -> Unit)? = null,
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
     * The reader's saved drawing styles, and the per-tool defaults over them.
     *
     * Passed as the store rather than as a list and six lambdas, the way [chartLayoutStore] is:
     * this screen reads templates for the armed tool and for the drawing being edited, which are
     * two different queries that change as the reader works, and hoisting both would put the
     * screen's own state in the shell.
     */
    drawingTemplates: DrawingTemplateStore? = null,
    /** Panels the shell can dock beside the plot on a wide window — see [ChartSidePanel]. */
    sidePanels: List<ChartSidePanel> = emptyList(),
    /** The side panel open when the screen first composes — `"objects"` or a shell panel's id. */
    initialSidePanel: String? = null,
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
    /**
     * How much of this chart's chrome to draw (run Ω3). See [ReaderMode].
     *
     * ### Why it is a filter over the callbacks rather than a second screen
     *
     * Every advanced entry on this page is already optional — `onOpenDepth`, `onOpenStudio`,
     * `onOpenScript` and the workbench's `tools` slot are all nullable, and the chrome draws nothing
     * for a null. That is not a coincidence: they are nullable because a build without a depth feed
     * or without an editor route has to draw a page that makes sense, and «this reader asked for a
     * simpler page» is the same question with a different answer.
     *
     * So Simple mode is *not* a variant of this screen. It is this screen with four handlers unset,
     * which means there is no second layout to keep in step, nothing is unreachable — the controls
     * are still built and still tested — and switching back is one recomposition.
     */
    readerMode: ReaderMode = ReaderMode.TRADER,
    /**
     * Flip between the simple page and the full one, in one tap from the «…» hub (run Ω3).
     *
     * The round trip is lossless — the store remembers which full mode the reader came from — which
     * is what makes the tap safe to try. Null where nothing can store the answer, and the tile is
     * then not drawn rather than drawn doing nothing.
     */
    onToggleSimple: (() -> Unit)? = null,
    /**
     * The reader's last finished rehearsal trade, for رصد to review (run Ω4).
     *
     * A flat [TradeFacts] rather than the app's own paper-trade row, and hoisted rather than read
     * here, for the reason [alerts] is: `feature:chart` has no business knowing what a paper account
     * is. What a review is about is a price, a stop and an outcome.
     */
    lastPaperTrade: TradeFacts? = null,
    /**
     * **میدان** — today's challenge, played on this chart (run Ω4). See [ArenaSession].
     *
     * Null is the ordinary chart, which is the state this screen is in almost always. Non-null adds
     * one band above the command band and one sheet at the end, and changes nothing else: a reader
     * in the Arena is trading the chart they trade on, which is the whole point of a rehearsal.
     */
    arena: ArenaSession? = null,
    /**
     * Scores the session — the app's, because the score is over the *paper account's* trades and
     * this module does not know what one is. Called when the clock runs out or the reader stops.
     */
    onScoreArena: (() -> ArenaScore)? = null,
    /** Records the finished session and shares it. Null where there is nothing to record into. */
    onArenaFinished: ((ArenaScore) -> Unit)? = null,
    onShareArena: ((ArenaScore) -> Unit)? = null,
    /** Starts today's challenge. Null where the shell cannot set one on this chart. */
    onStartArena: (() -> Unit)? = null,
    /** How many days in a row, and the reader's best, for the result sheet. */
    arenaStreak: Int = 0,
    arenaBest: Int? = null,
    onCreateAlert: ((symbol: String, price: Double) -> Unit)? = null,
    /**
     * The alerts already set on this symbol, drawn on the plot (run Ω2).
     *
     * Passed in rather than read here, for the reason [onCreateAlert] is hoisted: the store lives in
     * `core:notifications` and this module has no business knowing what an alert's condition or its
     * repeat rule are. What it needs is a price and an id, which is what [ChartAlertLine] is.
     */
    alerts: List<ChartAlertLine> = emptyList(),
    /**
     * Move one of them to a new price — a drag on its gutter tag (run Ω2).
     *
     * Null draws the lines and refuses to move them, which is the honest state for a build with a
     * read-only route to the store.
     */
    onMoveAlert: ((id: String, price: Double) -> Unit)? = null,
    /** The symbol search, for the `/` key and the desk's menu; null on a screen without one. */
    onOpenSymbolSearch: (() -> Unit)? = null,
    /**
     * Open a script instance's source in the NamaScript editor (4.73.0).
     *
     * From the settings sheet's «Open in the editor», so a script the reader is looking at on the
     * chart is one tap from the text that makes it. Null where this build has no editor route.
     */
    onOpenScript: ((source: String, name: String) -> Unit)? = null,
    /**
     * The scripts this reader could switch on, for the indicator sheet's «My scripts» section.
     *
     * Filled by the app from the saved-script table and the shipped presets. Empty in a test and a
     * preview, where the section simply is not drawn.
     */
    scriptLibrary: List<ChartScriptSource> = emptyList(),
    /**
     * Make an alert on one of a script's `alertcondition(...)`s (run I item 0.6).
     *
     * The script's source travels with the request because that is what the alert has to carry:
     * see `AlertTrigger.ScriptCondition`. Null where this build has no alerts.
     */
    onCreateScriptAlert: ((symbol: String, name: String, source: String, condition: String) -> Unit)? = null,
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
    /** The starred and recent indicators. Null — a preview — hides the star and the two chips. */
    indicatorFavourites: IndicatorFavouritesStore? = null,
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

    // One haptics handle for the canvas: the magnet's snap and the crosshair crossing a level both
    // tick through it. Neither is a sound the chart makes itself — see `CoineProChart.onSnap`.
    val haptics = rememberCoineProHaptics()
    var lastCrosshairPrice by remember { mutableStateOf<Double?>(null) }
    // A removed drawing offers itself back (run Ω2).
    //
    // Three places delete one — the selection toolbar, the object tree's row and the style sheet's
    // button — and each of them is one tap with no question asked, which is the right trade for a
    // line somebody drew in two seconds. What was missing is the other half of that trade: without a
    // way back, «one tap, no question» is «one tap, gone». The undo is the chart's own history, so it
    // restores the drawing with its colour, its width, its lock and its place in the stack, and a
    // reader who mis-taps twice can press it twice.
    val toaster = LocalToaster.current
    val drawingRemovedMessage = stringResource(R.string.chart_drawing_removed)
    val undoLabel = stringResource(R.string.chart_more_undo)

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

    /** Deletes a drawing and says so, with the way back. See the note on [toaster] above. */
    val deleteDrawingAnnounced: (Long) -> Unit = { id ->
        controller.deleteDrawing(id)
        toaster.show(
            CoineProToast(
                message = drawingRemovedMessage,
                tone = ToastTone.NEUTRAL,
                actionLabel = undoLabel,
                onAction = controller::undo,
            ),
        )
    }

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
    /**
     * Which study the Explain sheet is about, or null for the chart as a whole (run Ω1).
     *
     * Held beside [sheet] rather than inside it because the sheet is one destination with two
     * subjects — a study, and the score over all of them — and the score's own row moves the sheet
     * onto a study without closing it.
     */
    var explaining by remember { mutableStateOf<String?>(null) }
    /**
     * A setup built from one price in the gutter, rather than drawn (run Ω2).
     *
     * The gutter chip's second action. It wins over [ChartUiState.setup] for as long as it is open,
     * because a reader who has just asked for an order *at this price* is not asking about the three
     * lines they dragged an hour ago — and it is cleared on dismiss, so the drawn setup is back
     * untouched. Held here rather than pushed into the controller for the same reason: nothing about
     * it is worth surviving a rotation, and a stored one would reopen this sheet on every restore.
     */
    var pendingOrder by remember { mutableStateOf<ChartOrder?>(null) }
    // The desk's right-click menu: the price it landed on and where, or null while closed.
    var contextMenu by remember { mutableStateOf<ChartContextMenu?>(null) }
    /** Which drawing's own settings are open, or null. Opened from the object tree's row. */
    var styling by remember { mutableStateOf<Long?>(null) }
    /** Which indicator's settings sheet is open, by id, or null. Opened from the legend's gear. */
    var indicatorSettings by remember { mutableStateOf<String?>(null) }
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
     * The step before [fullscreen]: the toolbar slides out of the page for [FULLSCREEN_SLIDE_MS],
     * and only then does the window open over it. Without the beat the slide happens under a
     * window that has already covered it, which is the same as no slide at all.
     */
    var fullscreenRequested by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(fullscreenRequested) {
        if (fullscreenRequested && !fullscreen) {
            delay(FULLSCREEN_SLIDE_MS.toLong())
            fullscreen = true
        }
    }

    /**
     * The chart's ink on a symbol switch: zero the frame the instrument changes, one 250 ms later.
     *
     * The design brief's cross-fade on the wheel's release. A true cross-fade would hold the old
     * series on a second canvas for a quarter second; this is the same picture — the new chart
     * dissolving in over the stage — at the cost of one layer rather than two.
     */
    val symbolFade = remember { Animatable(1f) }
    var fadedSymbol by remember { mutableStateOf(state.symbol) }
    var fadedInterval by remember { mutableStateOf(state.interval) }
    LaunchedEffect(state.symbol, state.interval) {
        when {
            state.symbol != fadedSymbol -> {
                fadedSymbol = state.symbol
                fadedInterval = state.interval
                symbolFade.snapTo(0f)
                symbolFade.animateTo(1f, tween(SYMBOL_CROSSFADE_MS))
            }
            // A timeframe change is a shorter dissolve: the same market, redrawn.
            state.interval != fadedInterval -> {
                fadedInterval = state.interval
                symbolFade.snapTo(0f)
                symbolFade.animateTo(1f, tween(INTERVAL_CROSSFADE_MS))
            }
        }
    }

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

    // **The Signal Layer's language** (run Ω1).
    //
    // The controller has no composition and so cannot read `inEnglish()`, and the sentences it
    // builds — «RSI came back over 30» — are prose. Told here, once, and again whenever the reader
    // changes language: the studies are re-read rather than the strings translated, which is the
    // same work and one fewer thing that can end up half in each language.
    val readingInEnglish = inEnglish()
    LaunchedEffect(controller, readingInEnglish) { controller.setSignalLanguage(readingInEnglish) }

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

    // **What a shared card says** (run Ω4). Hoisted here rather than built inside the share lambda
    // because every one of these is a `stringResource` or a composition read, and the lambda runs on
    // a coroutine after the sheet has closed — reading a composition local there is reading a
    // composition that is no longer there.
    val shareOnDark = LocalCoineProPalette.current.isDark
    val shareRtl = !inEnglish()
    val shareTitle = BidiText.isolateLtr(state.symbol)
    // The trader's own name for the bar length — `H1`, never «۱ ساعت»: this is a caption on a
    // control-like line, and the app reserves Persian numerals for prose.
    val shareSubtitle = state.interval.code
    val shareLast = state.lastPrice
    // The move across the loaded window, which is what the picture on the card shows — see
    // `ChartState.changePercent` for why it is named after the window rather than the session.
    val shareChange = state.changePercent
    val shareHeadline = when {
        shareChange != null -> BidiText.isolateLtr(MarketNumberFormatter.signedPercent(shareChange))
        shareLast != null -> BidiText.isolateLtr(MarketNumberFormatter.priceAuto(shareLast))
        else -> ""
    }
    val shareTone = when {
        shareChange == null -> ShareCardTone.NEUTRAL
        shareChange >= 0.0 -> ShareCardTone.UP
        else -> ShareCardTone.DOWN
    }
    // رصد's own sentences, so the card says the same thing the page does. A card with a picture and
    // no words is a picture of some candles.
    val shareLines = RasadCoach.readChart(
        series = state.visibleSeries,
        reads = state.signals.reads,
        setup = state.signals.setup,
        english = inEnglish(),
    )

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
    var canvasHeightPx by remember { mutableStateOf(0) }
    /** The selected drawing's box on the canvas, in pixels, or null while nothing is selected. */
    var selectionBounds by remember { mutableStateOf<Rect?>(null) }
    val density = LocalDensity.current
    var plotWidthPx by remember { mutableFloatStateOf(0f) }

    // Whether a finger is on the toolbar's symbol wheel. While it is, the big picker is drawn over
    // the plot — see `SymbolWheelOverlay`. Held here because the wheel is in the band and the
    // picker is over the chart, and neither is the other's parent.
    var wheelDragging by remember { mutableStateOf(false) }
    // And how far it has turned since its last step, so the picker slides with the cell rather than
    // waiting for the symbol to change. See `SymbolScrollWheel`.
    val wheelTravel = remember { mutableFloatStateOf(0f) }

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
                .onSizeChanged {
                    canvasWidthPx = it.width.toFloat()
                    canvasHeightPx = it.height
                },
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
                travelPx = wheelTravel::floatValue,
                modifier = Modifier.align(Alignment.Center).zIndex(2f),
            )
            when {
                state.loading && state.series.isEmpty -> Loading()
                state.error != null && state.series.isEmpty -> ChartFailure(state.error!!, controller::retry)
                // **A seconds chart with nothing on it yet is not a broken chart.**
                //
                // No venue serves a bar shorter than a minute, so these are built here out of the
                // price feed — see `ChartInterval.Seconds`. Before the first tick lands there is
                // genuinely nothing to draw, and what was drawn instead was an empty grid with an
                // empty axis, which reads exactly like a chart that failed. «تایم‌فریم ۱۰ ثانیه کار
                // نمی‌کند» is what that looks like from the other side of the screen.
                state.series.isEmpty && state.interval is ChartInterval.Seconds ->
                    SecondsWarmingUp(state.interval)
                else -> {
                CoineProChart(
                    series = state.visibleSeries,
                    // Read in the layer, so the fade repaints without recomposing the chart. The
                    // stale factor multiplies into the same alpha rather than adding a second
                    // layer: bars on their way out are drawn at [STALE_ALPHA] and a symbol switch
                    // fades over the top of that. See [ChartUiState.stale].
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = symbolFade.value * if (state.stale) STALE_ALPHA else 1f }
                        .testTag("chart-plot"),
                    type = state.chartType,
                    // The legend's first line, as TradingView's phone sets it: the mark and the
                    // instrument's name — «Bitcoin / TetherUS» — not the ticker.
                    seriesLabel = SymbolClassifier.classify(state.symbol).description(inEnglish()),
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
                    // And the other half of that gesture: a paper order at the price under the
                    // finger (run Ω2). The side is read off the market rather than asked — a price
                    // below the last trade is where somebody wants to get long and one above is
                    // where they want to get short, which is true of every limit order anybody has
                    // ever placed — and the stop and target arrive as a draggable default, not as a
                    // recommendation. Offered only where the build can take a paper trade.
                    onRequestOrderAt = onPaperTrade?.let {
                        { price ->
                            val live = state.lastPrice ?: state.series.bars.lastOrNull()?.c
                            val side = if (live != null && price > live) TradeSide.SELL else TradeSide.BUY
                            TradeFromChart.defaultOrder(side, price)?.let { order ->
                                pendingOrder = order
                                sheet = ChartSheet.SETUP
                            }
                        }
                    },
                    alerts = alerts,
                    onMoveAlert = onMoveAlert,
                    // The gutter's `L`. It writes the same field the scale sheet writes, so the two
                    // cannot disagree and a layout saved after a tap here carries the log axis.
                    onToggleLogScale = {
                        controller.setScaleMode(
                            if (state.scaleMode == PriceScaleMode.LOGARITHMIC) {
                                PriceScaleMode.REGULAR
                            } else {
                                PriceScaleMode.LOGARITHMIC
                            },
                        )
                    },
                    // Long press on the price gutter opens the axis' own settings, which is where
                    // every terminal puts them and the one gesture on this chart a reader is
                    // likely to try by accident and be pleased to find.
                    onPriceAxisMenu = { sheet = ChartSheet.SCALE },
                    onContextMenu = { price, at ->
                        // The platform's context tick as the menu lands — a different answer to the
                        // finger from the long-press buzz that fired while it was still holding. See
                        // `CoineProHaptics.contextClick`.
                        haptics.contextClick()
                        contextMenu = ChartContextMenu(price, at)
                    },
                    // Where the selected drawing lies on the canvas, so the floating toolbar can
                    // sit just above it rather than at the top of the plot (run E).
                    onSelectionBounds = { bounds -> selectionBounds = bounds },
                    // A tick when the magnet takes a point, and a tick when the crosshair crosses a
                    // level — a stop, a target, an indicator line. The reader feels the line under
                    // the finger without looking away from the price they are dragging towards.
                    onSnap = { haptics.select() },
                    // And a tick on every placed point, snapped or not — the reference's soft
                    // haptic under the finger as a shape is tapped out.
                    onPlace = { haptics.select() },
                    onCrosshairMove = { crosshair ->
                        val price = crosshair?.price
                        val previous = lastCrosshairPrice
                        // The crosshair taking hold under a long press: the platform's long-press
                        // buzz, once, as it lands.
                        if (crosshair != null && previous == null) haptics.longPress()
                        if (price != null && previous != null && price != previous) {
                            val low = minOf(previous, price)
                            val high = maxOf(previous, price)
                            if (state.levels.any { level -> level.price in low..high }) haptics.select()
                        }
                        lastCrosshairPrice = price
                    },
                    // The bars on screen, back to the controller. One study reads it — the
                    // visible-range volume profile — and until this existed it was computed once
                    // against the whole series and never followed a pan, which is a "visible range"
                    // study that ignores the visible range.
                    // The reader's own zoom on this symbol and this timeframe, restored and kept.
                    savedBarsPerView = state.barsPerView,
                    onViewportChange = { view ->
                        controller.setZoom(view.barsPerView)
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
                        val id = state.indicatorFor(target)
                        when {
                            target is ChartLegendTarget.Comparison -> sheet = ChartSheet.COMPARE
                            // The study's own sheet — inputs, style, visibility — rather than
                            // the whole catalogue with the study somewhere in it.
                            id != null -> indicatorSettings = id
                            else -> sheet = ChartSheet.INDICATORS
                        }
                    },
                    // The name, tapped: «what is this study and what is it saying». The gear beside
                    // it still opens the settings — see `onExplainSeries` for why the two are
                    // different questions. A comparison has no reading of its own, so its name
                    // stays inert rather than opening a sheet with nothing in it.
                    onExplainSeries = { target ->
                        state.indicatorFor(target)?.let { id ->
                            explaining = id
                            sheet = ChartSheet.EXPLAIN
                        }
                    },
                    // One hidden set for the legend's eye and the settings sheet's switch: the
                    // chart reports the row, the controller keeps the id.
                    hiddenSeries = state.hiddenTargets,
                    // A script whose last compile failed keeps its previous drawing and grows a
                    // dot: the study is on the chart, and it is not what the text now says.
                    warningSeries = state.scriptWarnings,
                    onToggleSeriesVisibility = { target ->
                        state.indicatorFor(target)?.let(controller::toggleIndicatorHidden)
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
                // The right-click (or S Pen button) menu at the pointer. Anchored at the plot's
                // absolute top-left and offset in left-to-right pixels, because the position came
                // from the pointer and the time axis reads left to right on every locale; the
                // items themselves take the screen's direction back.
                contextMenu?.let { menu ->
                    val direction = LocalLayoutDirection.current
                    val density = LocalDensity.current
                    val clipboard = LocalClipboardManager.current
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Box(Modifier.align(AbsoluteAlignment.TopLeft)) {
                            DropdownMenu(
                                expanded = true,
                                onDismissRequest = { contextMenu = null },
                                offset = with(density) { DpOffset(menu.at.x.toDp(), menu.at.y.toDp()) },
                            ) {
                                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                                    if (onCreateAlert != null) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.chart_menu_alert_here, MarketNumberFormatter.priceAuto(menu.price))) },
                                            onClick = { onCreateAlert(state.symbol, menu.price); contextMenu = null },
                                            modifier = Modifier.semantics { contentDescription = "chart-menu-alert" },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chart_menu_copy_price)) },
                                        onClick = { clipboard.setText(AnnotatedString(MarketNumberFormatter.priceAuto(menu.price))); contextMenu = null },
                                        modifier = Modifier.semantics { contentDescription = "chart-menu-copy" },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.chart_menu_scale)) },
                                        onClick = { sheet = ChartSheet.SCALE; contextMenu = null },
                                        modifier = Modifier.semantics { contentDescription = "chart-menu-scale" },
                                    )
                                    if (onOpenSymbolSearch != null) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.chart_menu_search)) },
                                            onClick = { onOpenSymbolSearch(); contextMenu = null },
                                            modifier = Modifier.semantics { contentDescription = "chart-menu-search" },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
            // **A load that failed over a chart that still has candles on it is a line, not a page.**
            //
            // The reader can see prices. They are real and they are merely old, and replacing a
            // working chart with a centred «چارت بارگیری نشد» throws away the only useful thing on
            // the glass to deliver news that fits in a sentence. So the full-screen failure is kept
            // for the one case that earns it — nothing to look at at all, the branch above — and
            // everything else gets this: one compact row at the foot of the plot, with the retry in
            // it, over a chart the reader can go on reading and drawing on.
            if (state.error != null && !state.series.isEmpty) {
                ChartInlineFailure(
                    error = state.error!!,
                    onRetry = controller::retry,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        // Clear of the time axis, which is the one row at this end of the plot that
                        // has to stay readable: a banner over it hides the dates the reader would
                        // use to judge how old these candles are.
                        .padding(bottom = TIME_AXIS_CLEARANCE)
                        .zIndex(1f),
                )
            }
            // Older bars arriving: a shimmer down the left edge, where they will land, rather
            // than a spinner in the corner. The viewport is anchored at the newest bar, so the
            // bars on screen do not move when the history grows — see `ChartViewport.offset`.
            if (state.loadingMore) {
                CoineProSkeleton(
                    modifier = Modifier
                        .align(AbsoluteAlignment.TopLeft)
                        .width(HISTORY_SKELETON_WIDTH)
                        .fillMaxHeight()
                        .zIndex(1f),
                    height = 1.dp,
                    shape = RectangleShape,
                )
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
            // rather than over the axis they need to read. It carries the point count — «نقطه‌ی ۱
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
                    //
                    // One message for the lot, and one undo: the chart's history records the whole
                    // deletion as one step, so offering a message per drawing would be three toasts
                    // for one action, each claiming to undo the same thing.
                    state.drawing.selection.forEach(controller::deleteDrawing)
                    toaster.show(
                        CoineProToast(
                            message = drawingRemovedMessage,
                            tone = ToastTone.NEUTRAL,
                            actionLabel = undoLabel,
                            onAction = controller::undo,
                        ),
                    )
                },
                onOpenSettings = { id -> styling = id },
                onDismiss = controller::clearSelection,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(CoineProSpacing.Half)
                    // Above the selected drawing when its top leaves room for the bar, otherwise
                    // at the top of the plot as before; the horizontal stays centred so the bar
                    // never runs off a phone's edge. Clamped to the canvas, so a drawing scrolled
                    // half off the bottom still has its toolbar on the glass.
                    .offset {
                        val top = selectionBounds?.top ?: 0f
                        val bar = with(density) { SELECTION_TOOLBAR_HEIGHT.roundToPx() }
                        val y = (top - bar).roundToInt().coerceIn(0, (canvasHeightPx - bar).coerceAtLeast(0))
                        IntOffset(0, y)
                    },
            )
            // The reader's pinned tools along the plot's leading edge, one tap from armed (run E).
            // Only when there are some: a strip with nothing on it is a strip in the way.
            if (state.drawing.favourites.isNotEmpty() && state.drawing.tool == null) {
                FavouriteToolStrip(
                    favourites = state.drawing.favourites,
                    onArm = controller::arm,
                    modifier = Modifier
                        .align(AbsoluteAlignment.CenterLeft)
                        .padding(start = CoineProSpacing.Half),
                )
            }
            // The first time a study this reader *wrote* draws on the chart (run Ω2).
            //
            // Over the plot rather than over the page, because the plot is where the thing being
            // celebrated just appeared — and it is the one moment in this app where somebody has
            // crossed from reading a chart to building one. Once per install; see `CoineProConfetti`.
            CoineProConfetti(
                celebrate = state.scripts.isNotEmpty(),
                key = CoineProCelebration.FIRST_SCRIPT,
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
    val fold = coineProFold()
    val plotHeight = remember(configuration.screenHeightDp, roomy, fold) {
        val fraction = if (roomy) TABLET_PLOT_SCREEN_FRACTION else PLOT_SCREEN_FRACTION
        val floor = if (roomy) TABLET_PLOT_MIN else PLOT_MIN
        val ceiling = if (roomy) TABLET_PLOT_MAX else PLOT_MAX
        val wanted = (configuration.screenHeightDp * fraction).dp.coerceIn(floor, ceiling)
        // Half-open on a table, hinge across the window: the plot stays above the crease so the
        // price scale is never cut in two, and the tools and readings take the lower half.
        plotHeightAboveHinge(wanted, fold)
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
        // The same gate as the hub's tile, and it has to be the same one: a mode that hides the
        // workbench from the «…» sheet and leaves a row for it under the chart reads as a bug.
        onOpenStudio?.takeIf { readerMode.showsWorkbench }?.let { open ->
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
            onExit = {
                fullscreen = false
                fullscreenRequested = false
            },
            symbols = wheelSymbols,
            onSelectSymbol = switchSymbol.takeIf { controllerFor != null || onSelectSymbol != null },
        )
    } else {
    // **The chart page has the whole screen, and the watchlist is not part of it.**
    //
    // There used to be a split here: the page above, the starred markets as rows below, a handle
    // between them. It was reported twice — «وقتی دیده‌بان نماد اضافه می‌کنی چارت رو ببین چه شکلی
    // می‌شه», then «نمادها میان روی قسمت پایینی چارت … این یه باگه» — and the second report came
    // with the picture that explains it. The page inside the split is a *scrolling* page and the
    // plot inside it keeps a height measured against the screen, so giving the page a fraction of
    // the screen did not shrink the chart: it cut it off. The reader was left with a chart with no
    // time axis, sliced across the middle by a list they had not asked to be there.
    //
    // Switching instrument without leaving the chart is what the strip was for, and that is now
    // where it belongs — in the toolbar band under the plot, on the same starred markets and with
    // the same prices beside them. See `SymbolWheel`: one control, in the chrome, costing the plot
    // nothing.
    //
    // The columns a large window can pay for, and the page in the middle of them. On a phone this
    // is a pass-through: `ChartWorkbench` hands the page the whole modifier and composes neither
    // column, so nothing below this line behaves differently on the device the page was designed
    // for.
    // The object tree is the chart's own panel; the rest come from the shell. First in the rail
    // because it is the one a reader drawing on a tablet reaches for most.
    val objectTreePanel = ChartSidePanel(
        id = "objects",
        labelRes = R.string.chart_panel_objects,
        icon = DesignR.drawable.icon_list_bullets,
    ) {
        ObjectTreeSheetBody(
            groups = objectTree,
            drawings = state.drawing.drawings,
            selectedId = state.drawing.selectedId,
            onSelect = controller::selectDrawing,
            onToggleHidden = controller::toggleDrawingHidden,
            onToggleLocked = { node -> controller.setDrawingLocked(node.id, !node.locked) },
            onDelete = deleteDrawingAnnounced,
            onReorder = controller::reorderDrawing,
            onOpenStyle = { id -> styling = id },
        )
    }
    // **Explain and رصد, docked rather than covering the chart** (run Ω5).
    //
    // Both are already *bodies* rather than sheets — `ExplainSheetBody` and `RasadSheetBody` — and
    // that was deliberate from the day each was written, for exactly this: on a phone a bottom sheet
    // is the right shape because there is one column, and on a tablet a sheet that covers the chart
    // it is explaining is the wrong shape for the same content. Same composable, two homes.
    //
    // `ChartWorkbench` draws neither column on a phone, so this list costs the device the page was
    // designed for nothing at all.
    val explainPanel = ChartSidePanel(
        id = "explain",
        labelRes = R.string.chart_explain_title,
        icon = DesignR.drawable.tv_help_circle,
    ) {
        ExplainSheetBody(
            // The pill the reader last opened, or the chart's own score when they have opened none.
            // Null is the Setup score's own card, which is the right thing for a panel that is
            // simply *there* rather than opened by a tap on a study.
            id = explaining,
            layer = state.signals,
            onSetHorizon = controller::setConfidenceHorizon,
            // The same rule the sheet's own copy follows: offered only where there is a composer
            // and a price to alert on.
            onAddAlert = onCreateAlert?.let { create ->
                state.lastPrice?.let { price -> { create(state.symbol, price) } }
            },
            onPractise = { controller.enterReplay() },
            onSelect = { id -> explaining = id },
        )
    }
    val rasadPanel = ChartSidePanel(
        id = "rasad",
        labelRes = R.string.rasad_name,
        icon = DesignR.drawable.icon_sparkle,
    ) {
        RasadSheetBody(
            series = state.visibleSeries,
            reads = state.signals.reads,
            setup = state.signals.setup,
            lastTrade = lastPaperTrade,
            onCreateAlert = onCreateAlert?.let { arm ->
                { suggestion -> arm(state.symbol, suggestion.price) }
            },
        )
    }
    // **The Arena's result, beside the chart it scored** (run Ω5).
    //
    // On a phone the result is a sheet over the plot, which is right there: the five minutes are
    // over and there is nothing behind it to look at. On a tablet there is — the chart the reader
    // was just scored on, with their own trades still marked on it — so the result docks and the two
    // are read together.
    val arenaPanel = arena?.score?.let { result ->
        ChartSidePanel(
            id = "arena",
            labelRes = R.string.arena_name,
            icon = DesignR.drawable.icon_crown_simple,
        ) {
            ArenaResultBody(
                score = result,
                symbol = state.symbol,
                streak = arenaStreak,
                best = arenaBest,
                onShare = onShareArena?.let { share -> { share(result) } },
            )
        }
    }
    ChartWorkbench(
        modifier = Modifier.fillMaxSize(),
        sidePanels = listOfNotNull(objectTreePanel, explainPanel, rasadPanel, arenaPanel) + sidePanels,
        initialSidePanel = initialSidePanel,
        // The rail is the drawing tools as a column beside the plot on a roomy window. In Simple
        // mode there is no column, for the same reason the band loses its pencil: fifty drawing
        // tools is the answer to a question a first chart has not asked.
        tools = if (readerMode.showsAdvancedChrome) {
            { railModifier ->
                ChartToolRailColumn(
                    state = state,
                    controller = controller,
                    templates = armedTemplates,
                    defaultTemplateId = armedDefault?.id,
                    onHelp = onHelp,
                    modifier = railModifier,
                )
            }
        } else {
            null
        },
        readings = { ChartReadingsColumn { analysisBlocks() } },
    ) { workbenchModifier, columns ->
    // **The plot fills the column on a tablet** (run H item 2).
    //
    // The page is a scrolling column on a phone, where the six bands under the plot are the page.
    // On a window with the rails beside it there is nothing left to scroll to — the tools and the
    // readings are panels now — and a scrolling column there meant the chart took its fraction of
    // the *screen* and left the rest of the column blank: «چارت ارتفاع صفحه را پر نمی‌کند — پایین
    // نوار ابزار ۲۵–۳۵٪ فضای خالی». So on a roomy window the column does not scroll and the plot
    // takes every point the bands above and below it did not.
    val fills = columns != ChartWorkbenchColumns.NONE
    Column(
        modifier = workbenchModifier
            .background(CoineProColors.Stage)
            .then(if (fills) Modifier else Modifier.verticalScroll(rememberScrollState()))
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
                onZoom = { zoomIn -> zoomBy(if (zoomIn) ChartZoomNudge.STEP else 1f / ChartZoomNudge.STEP) },
                onArmTool = { id -> DrawingTools.ALL.firstOrNull { it.id == id }?.let(controller::arm) },
                onSearch = onOpenSymbolSearch,
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
                .then(if (fills) Modifier.weight(1f) else Modifier.height(plotHeight))
                .background(CoineProColors.Terminal)
                // The chart alone, recorded into a layer. Sharing the whole screen would hand
                // over the header and the toolbar; sharing this hands over the chart.
                .drawWithContent {
                    chartLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(chartLayer)
                },
        )
        HorizontalDivider(color = CoineProColors.Border)

        // **What the chart is saying** (4.75.0, run Ω1): the Setup score and one pill per study.
        //
        // Under the plot rather than under the legend, and the difference is the renderer: the
        // legend is drawn *inside* the canvas by the chart engine, so a row «under» it would have to
        // be positioned against a plate whose height depends on how many studies are on. Here it is
        // the first thing below the candles, which is where a reader's eye goes next, and it costs
        // the plot twenty-six points only on a chart that has something to say.
        //
        // It is the one row this page has gained since the bands were collapsed, and it is the
        // product: a line is a measurement, this is the judgement. See `ChartNowStrip`.
        ChartNowStrip(
            layer = state.signals,
            onOpenExplain = { id ->
                explaining = id
                sheet = ChartSheet.EXPLAIN
            },
        )

        // **رصد, one line under the strip** (run Ω4).
        //
        // The strip says what each study is saying; this says what the *chart* is saying, which is a
        // different question and the one a reader actually arrives with. One line — the trend
        // sentence — and the other two are behind it: three permanent lines of prose under the plot
        // is exactly the explanatory text run Ω2 spent a version removing.
        RasadLine(series = state.visibleSeries, onOpen = { sheet = ChartSheet.RASAD })

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
        // The band slides down and out for the 200 ms before the fullscreen window opens, and
        // back in when it closes — the reference's, and the one thing that makes the mode read as
        // the page growing rather than a second page appearing.
        // **The Arena's clock** (run Ω4), above the band and nowhere else. See `ArenaBar`.
        arena?.let { session ->
            ArenaBar(
                session = session,
                onFinish = {
                    if (!session.running) {
                        session.start()
                    } else {
                        val result = onScoreArena?.invoke() ?: ArenaScore.UNPLAYED
                        session.finish(result)
                        onArenaFinished?.invoke(result)
                        sheet = ChartSheet.ARENA
                    }
                },
            )
            // One second at a time on the composition's own clock. Keyed on `running` so pressing
            // «تمام» stops it immediately rather than letting one more tick land after the score.
            LaunchedEffect(session, session.running) {
                while (session.running) {
                    delay(1_000)
                    if (session.tick()) {
                        val result = onScoreArena?.invoke() ?: ArenaScore.UNPLAYED
                        session.finish(result)
                        onArenaFinished?.invoke(result)
                        sheet = ChartSheet.ARENA
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !fullscreenRequested,
            enter = slideInVertically(CoineProMotionSpecs.defaultSpatialFor()) { it } + fadeIn(tween(FULLSCREEN_SLIDE_MS)),
            exit = slideOutVertically(CoineProMotionSpecs.defaultSpatialFor()) { it } + fadeOut(tween(FULLSCREEN_SLIDE_MS)),
        ) {
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
            showDraw = !columns.hasTools && readerMode.showsAdvancedChrome,
            indicators = state.activeIndicators.size,
            drawings = state.drawing.drawings.size,
            onOpen = { sheet = it },
            onFullscreen = { fullscreenRequested = true },
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
            onSymbolTravel = { wheelTravel.floatValue = it },
        )
        }

        // Once, under the band, for the reader who has not found the band yet. «خیلی موارد رو
        // کاربر بخواد پیدا کنه»: the wheel turns, the pencil is the tools, the sliders are the
        // studies, and «…» holds depth, compare and the rest. Said in one line the first time and
        // dismissed for good; the way back is the small «این صفحه چیست؟» the strip leaves behind.
        if (!fullscreenRequested) CoineProTeachingStrip(TeachingSurface.CHART)

        // **The readings are not a row on this page any more** (4.76.0, run Ω2).
        //
        // They were a closed disclosure — one line, «خوانش بازار و ابزارها», with the trend reading,
        // an open setup and the way into the studio behind it. Closed it still cost a row, a rule
        // and a chevron at the foot of the one screen whose entire product is vertical space, and
        // what it holds is read occasionally rather than while reading: they are now a tile in the
        // «…» hub, which is where everything else touched about once a month already lives.
        //
        // A wide window is unchanged: there they are a column beside the plot, always open, because
        // there is room for them and scrolling to a reading is what a tablet exists to avoid.
        if (columns.hasReadings) {
            // Nothing here. `ChartWorkbench` draws them in its own column — see [analysisBlocks].
            Unit
        }
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
            title = stringResource(R.string.chart_sheet_type),
            // Counted after the volume gate, not before. A subtitle that promises eighteen over a
            // list of sixteen is a small lie the reader catches immediately.
            subtitle = stringResource(
                R.string.chart_sheet_type_subtitle,
                ChartCatalog.chartTypeCount(state.series.hasVolume).toPersianDigits(),
            ),
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
            title = stringResource(R.string.chart_sheet_indicators),
            // Counted after the volume gate, like the chart-type subtitle above: fourteen studies
            // are arithmetic on a volume column, and a subtitle promising eighty-three over a list
            // of sixty-nine is a small lie the reader catches immediately.
            subtitle = stringResource(
                R.string.chart_sheet_indicators_subtitle,
                ChartCatalog.indicatorCount(state.series.hasVolume).toPersianDigits(),
            ),
            onDismiss = { sheet = null },
        ) {
            // No dismiss on select: switching four indicators on is four taps, and a sheet that
            // closes after each one turns that into twelve.
            val starred by remember(indicatorFavourites) {
                indicatorFavourites?.favourites() ?: flowOf(emptyList<String>())
            }.collectAsStateWithLifecycle(emptyList())
            val recent by remember(indicatorFavourites) {
                indicatorFavourites?.recent() ?: flowOf(emptyList<String>())
            }.collectAsStateWithLifecycle(emptyList())
            IndicatorPicker(
                active = state.activeIndicators,
                onToggle = { option ->
                    val turningOn = option.id !in state.activeIndicators
                    controller.toggleIndicator(option.id)
                    if (turningOn) {
                        indicatorFavourites?.let { store -> favouriteScope.launch { store.recordRecent(option.id) } }
                    }
                },
                onHelp = onHelp,
                hasVolume = state.series.hasVolume,
                periods = state.indicatorPeriods,
                onSetPeriod = controller::setIndicatorPeriod,
                favourites = starred,
                onToggleFavourite = indicatorFavourites?.let { store ->
                    { id -> favouriteScope.launch { store.toggleFavourite(id) } }
                },
                recent = recent,
                // **No keyboard on open** (run K item 3). The reference raises one; on the owner's
                // phone that covered the bottom half of the sheet and the catalogue with it.
                autoFocusSearch = false,
                // The reader's own studies, at the foot of the same scroll — see
                // `ScriptPickerSection` for why they moved from the head of the sheet to here.
                trailing = {
                    ScriptPickerSection(
                        library = scriptLibrary,
                        onChart = state.scripts,
                        onAdd = { entry -> controller.putScript(entry.name, entry.source) },
                        onRemove = { instance -> controller.removeScript(instance.instanceId) },
                        collapsible = true,
                    )
                },
                modifier = Modifier.fillMaxHeight(INDICATOR_SHEET_HEIGHT),
            )
        }

        ChartSheet.TOOLS -> CoineProSheet(
            title = stringResource(R.string.chart_tools_column_title),
            subtitle = stringResource(
                R.string.chart_tools_column_count,
                DrawingTools.ALL.size.proseDigits(),
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
            title = stringResource(R.string.chart_sheet_layouts),
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
            title = stringResource(R.string.chart_sheet_interval),
            subtitle = stringResource(
                R.string.chart_sheet_interval_subtitle,
                Timeframe.entries.size.toPersianDigits(),
            ),
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
            title = stringResource(R.string.chart_sheet_scale),
            subtitle = stringResource(state.scaleMode.labelRes),
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
            title = stringResource(R.string.chart_sheet_compare),
            subtitle = stringResource(
                R.string.chart_sheet_compare_subtitle,
                MAX_COMPARISONS.toPersianDigits(),
            ),
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
            title = stringResource(R.string.chart_sheet_backtest),
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

        // The gutter's order wins over the drawn one while it is open — see [pendingOrder].
        ChartSheet.SETUP -> (pendingOrder ?: state.setup)?.let { order ->
            CoineProSheet(
                title = stringResource(R.string.chart_setup_title),
                subtitle = state.symbol,
                onDismiss = { sheet = null; pendingOrder = null },
            ) {
                SetupSheetBody(
                    order = order,
                    symbol = state.symbol,
                    livePrice = state.lastPrice,
                    onPaperTrade = onPaperTrade?.let { take ->
                        { buy, entry, size, stopLoss, takeProfit ->
                            take(state.symbol, buy, entry, size, stopLoss, takeProfit)
                            sheet = null
                            pendingOrder = null
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
                scaleLabel = stringResource(state.scaleMode.labelRes),
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
                        // **A card, not a screenshot** (run Ω4). The plot goes into the top of a
                        // 1080 × 1080 square with the instrument, the bar length, رصد's own first
                        // sentence and the mark — because what a reader posts is cropped to a square
                        // by every surface that shows it, and a chart with no words on it is a
                        // picture of some candles.
                        val card = ShareCard.render(
                            context = context,
                            dark = shareOnDark,
                            rtl = shareRtl,
                            content = ShareCardContent(
                                title = shareTitle,
                                subtitle = shareSubtitle,
                                headline = shareHeadline,
                                tone = shareTone,
                                lines = shareLines,
                                image = chartLayer.toImageBitmap().asAndroidBitmap(),
                            ),
                        )
                        ChartShare.share(context, card, state.symbol)
                    }
                },
                onAskAi = onAskAi?.let { ask ->
                    {
                        sheet = null
                        ask(state.symbol)
                    }
                },
                onOpenStudio = onOpenStudio?.takeIf { readerMode.showsWorkbench }?.let { open ->
                    {
                        sheet = null
                        open()
                    }
                },
                // Offered only where something actually fetches events. On a preview, a fixture or
                // a platform this build was not configured for there is nothing behind the row,
                // and a settings page for a feed that does not exist is worse than no row.
                onEvents = { sheet = ChartSheet.EVENTS }.takeIf { events != null },
                // The trend reading, the open setup and the way into the studio — the row that used
                // to sit at the foot of the chart page. Offered only where there is a reading to
                // show: under sixty bars `ChartReading.of` refuses to name a market it cannot read.
                onRasad = { sheet = ChartSheet.RASAD }
                    .takeIf { state.visibleSeries.size >= RasadCoach.MINIMUM_BARS },
                onReadings = { sheet = ChartSheet.READINGS }.takeIf { reading != null || state.setup != null },
                simplified = !readerMode.showsAdvancedChrome,
                // The sheet closes behind it: the tap's whole result is the page underneath
                // changing, and a sheet left open over it hides the one thing it did.
                onToggleSimple = onToggleSimple?.let { toggle ->
                    {
                        sheet = null
                        toggle()
                    }
                },
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
                onOpenDepth = onOpenDepth?.takeIf { readerMode.showsAdvancedChrome }?.let { open ->
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
                }.takeIf {
                    readerMode.showsAdvancedChrome &&
                        !state.replay.isOn &&
                        state.series.bars.size >= Replay.MINIMUM_BARS
                },
                // Offered where the shell can set one and the reader is not already in one. The
                // challenge needs history behind it and history in front — see `Arena.challengeFor`,
                // which answers null rather than a challenge that cannot be played.
                onArena = onStartArena?.takeIf { arena == null },
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

        // **The Explain sheet** (run Ω1): what a study is, what it is saying, and its base rate.
        ChartSheet.EXPLAIN -> CoineProSheet(
            title = stringResource(R.string.chart_explain_title),
            onDismiss = { sheet = null },
        ) {
            ExplainSheetBody(
                id = explaining,
                layer = state.signals,
                onSetHorizon = controller::setConfidenceHorizon,
                // Offered only where the screen was given an alert composer and a price to alert
                // on — the same rule the hub's alert tile follows.
                onAddAlert = onCreateAlert?.let { create ->
                    state.lastPrice?.let { price ->
                        {
                            create(state.symbol, price)
                            sheet = null
                        }
                    }
                },
                // «Practise this» is replay, which is the app's own answer to «what would I have
                // done»: it needs bars to rewind through and not to be rewinding already.
                onPractise = {
                    controller.enterReplay()
                    sheet = null
                }.takeIf { !state.replay.isOn && state.series.bars.size >= Replay.MINIMUM_BARS },
                onSelect = { explaining = it },
                onShowTimeframes = controller::readAcrossTimeframes,
            )
        }

        // The readings, off the page and into the hub (run Ω2). Same three blocks, same lambda.
        ChartSheet.READINGS -> CoineProSheet(
            title = stringResource(R.string.chart_reading_title),
            onDismiss = { sheet = null },
        ) {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                ChartUnderline(
                    state = state,
                    source = controller.sourceName,
                    signalOnChart = drawnSetup != null,
                    head = false,
                )
                analysisBlocks()
                Spacer(Modifier.height(CoineProSpacing.Two))
            }
        }

        // **رصد** (run Ω4): the chart read out loud, plus the two things the coach can do about it.
        ChartSheet.RASAD -> CoineProSheet(
            title = stringResource(R.string.rasad_title),
            onDismiss = { sheet = null },
        ) {
            RasadSheetBody(
                series = state.visibleSeries,
                reads = state.signals.reads,
                setup = state.signals.setup,
                // Offered only where the shell has handed this screen a finished rehearsal trade.
                // A review of a trade nobody made is a paragraph about nothing.
                lastTrade = lastPaperTrade,
                // The same hoisted composer the price gutter uses — the chart says which price, the
                // app asks the rest. See `onCreateAlert`.
                onCreateAlert = onCreateAlert?.let { arm ->
                    { suggestion ->
                        sheet = null
                        arm(state.symbol, suggestion.price)
                    }
                },
            )
        }

        // **میدان's result** (run Ω4): the two halves of the score, what they were made of, and
        // the streak. Opened by the clock running out and by «تمام», which are the same thing.
        ChartSheet.ARENA -> CoineProSheet(
            title = stringResource(R.string.arena_result_title),
            onDismiss = { sheet = null },
        ) {
            val result = arena?.score
            if (result == null) {
                Text(
                    text = stringResource(R.string.arena_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextMuted,
                    modifier = Modifier.padding(CoineProSpacing.Gutter),
                )
            } else {
                ArenaResultBody(
                    score = result,
                    symbol = state.symbol,
                    streak = arenaStreak,
                    best = arenaBest,
                    onShare = onShareArena?.let { share -> { share(result) } },
                )
            }
        }

        ChartSheet.EVENTS -> CoineProSheet(
            title = stringResource(R.string.chart_events_title),
            subtitle = stringResource(
                R.string.chart_events_subtitle,
                // A prose count, so Persian digits — unlike every figure on the chart above.
                eventState.visibility.kinds.count { it in SERVED_EVENT_KINDS }.proseDigits(),
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
            title = stringResource(R.string.chart_sheet_objects),
            subtitle = stringResource(
                R.string.chart_sheet_objects_subtitle,
                state.drawing.drawings.size.toPersianDigits(),
            ),
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
                onDelete = deleteDrawingAnnounced,
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
                    deleteDrawingAnnounced(drawing.id)
                    styling = null
                },
                onSetTextColour = { colour -> controller.setDrawingTextColour(drawing.id, colour) },
                onSetFillColour = { colour -> controller.setDrawingFillColour(drawing.id, colour) },
                onSetLineStyle = { style -> controller.setDrawingLineStyle(drawing.id, style) },
                onMovePoint = { index, to -> controller.moveDrawingPoint(drawing.id, index, to) },
                onSetLocked = { locked -> controller.setDrawingLocked(drawing.id, locked) },
                onSaveAsDefault = { controller.setDrawingStyle(drawing.colour, drawing.widthDp) },
            )
        }
    }

    indicatorSettings?.let { id ->
        // A script's gear opens the script's own sheet — its `input(...)`s, its diagnostic — and
        // everything else about it (the colour, the eye, the pane) is the same behaviour the
        // catalogue's sheet drives, because a script instance is addressed by an indicator id.
        val script = state.scripts.firstOrNull { it.ownerId == id }
        if (script != null) {
            val owners = state.paneOwnersShown
            val at = owners.indexOf(id)
            val hostAbove = if (at > 0) owners[at - 1] else null
            ScriptSettingsSheet(
                script = script,
                inputs = state.scriptDraw.inputs[id].orEmpty(),
                failure = state.scriptDraw.failures[id],
                paused = state.scriptDraw.paused[id],
                colour = state.indicatorColours[id],
                widthDp = state.indicatorWidths[id],
                hidden = id in state.hiddenIndicators,
                arrangement = IndicatorArrangement(
                    // A script decides its own pane through `pane =` and by measuring its values
                    // against the price, so «is this an overlay» is a fact about what it drew
                    // rather than about a catalogue row: it is an overlay when it put lines over
                    // the candles, and `separate` is then a choice the reader can make.
                    overlayByDefault = state.scriptDraw.overlayOwners.contains(id),
                    separated = id in state.separated,
                    merged = state.paneMerges[id] != null,
                    canMoveUp = at > 0,
                    canMoveDown = at in 0 until owners.lastIndex,
                    canMergeUp = hostAbove != null && state.paneMerges[hostAbove] == null,
                ),
                onDismiss = { indicatorSettings = null },
                onSetInput = { name, value -> controller.setScriptInput(script.instanceId, name, value) },
                onSetColour = { colour -> controller.setIndicatorColour(id, colour) },
                onSetWidth = { width -> controller.setIndicatorWidth(id, width) },
                onToggleHidden = { controller.toggleIndicatorHidden(id) },
                onArrange = { action ->
                    when (action) {
                        IndicatorArrangement.Action.SEPARATE -> controller.separateOverlay(id, true)
                        IndicatorArrangement.Action.JOIN_PRICE -> controller.separateOverlay(id, false)
                        IndicatorArrangement.Action.MOVE_UP -> controller.movePane(id, up = true)
                        IndicatorArrangement.Action.MOVE_DOWN -> controller.movePane(id, up = false)
                        IndicatorArrangement.Action.MERGE_UP -> controller.mergePane(id, hostAbove)
                        IndicatorArrangement.Action.UNMERGE -> controller.mergePane(id, null)
                    }
                },
                onRemove = {
                    controller.removeScript(script.instanceId)
                    indicatorSettings = null
                },
                onEdit = onOpenScript?.let { open ->
                    {
                        indicatorSettings = null
                        open(script.source, script.name)
                    }
                },
                alerts = state.scriptDraw.alerts[id].orEmpty().map {
                    it.title to it.firing(state.visibleSeries.size)
                },
                onCreateAlert = onCreateScriptAlert?.let { create ->
                    { condition ->
                        indicatorSettings = null
                        create(state.symbol, script.displayName, script.source, condition)
                    }
                },
            )
            return@let
        }
        val option = ChartCatalog.INDICATORS.firstOrNull { it.id == id }
        if (option == null || id !in state.activeIndicators) {
            indicatorSettings = null
        } else {
            val owners = state.paneOwnersShown
            val at = owners.indexOf(id)
            val overlay = option.pane == IndicatorPane.PRICE
            val hostAbove = if (at > 0) owners[at - 1] else null
            val arrangement = IndicatorArrangement(
                overlayByDefault = overlay,
                separated = id in state.separated,
                merged = state.paneMerges[id] != null,
                canMoveUp = at > 0,
                canMoveDown = at in 0 until owners.lastIndex,
                canMergeUp = hostAbove != null && state.paneMerges[hostAbove] == null,
            )
            IndicatorSettingsSheet(
                arrangement = arrangement,
                onArrange = { action ->
                    when (action) {
                        IndicatorArrangement.Action.SEPARATE -> controller.separateOverlay(id, true)
                        IndicatorArrangement.Action.JOIN_PRICE -> controller.separateOverlay(id, false)
                        IndicatorArrangement.Action.MOVE_UP -> controller.movePane(id, up = true)
                        IndicatorArrangement.Action.MOVE_DOWN -> controller.movePane(id, up = false)
                        IndicatorArrangement.Action.MERGE_UP -> controller.mergePane(id, hostAbove)
                        IndicatorArrangement.Action.UNMERGE -> controller.mergePane(id, null)
                    }
                },
                option = option,
                period = state.indicatorPeriods[id],
                colour = state.indicatorColours[id],
                widthDp = state.indicatorWidths[id],
                hidden = id in state.hiddenIndicators,
                onDismiss = { indicatorSettings = null },
                onSetPeriod = { period -> controller.setIndicatorPeriod(id, period) },
                params = state.indicatorParams[id].orEmpty(),
                onSetParam = { key, value -> controller.setIndicatorParam(id, key, value) },
                onSetColour = { colour -> controller.setIndicatorColour(id, colour) },
                onSetWidth = { width -> controller.setIndicatorWidth(id, width) },
                onToggleHidden = { controller.toggleIndicatorHidden(id) },
                onRemove = {
                    controller.toggleIndicator(id)
                    indicatorSettings = null
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
            title = stringResource(R.string.chart_clear_title),
            message = stringResource(
                R.string.chart_clear_message,
                state.drawing.drawings.size.toPersianDigits(),
            ),
            confirmLabel = stringResource(R.string.chart_clear_confirm),
            dismissLabel = stringResource(R.string.chart_clear_dismiss),
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
 * That is the whole of «می‌ره تو صفحه‌ی خانه». The system Back was being intercepted correctly — the
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
        PredictiveBackHandler { progress ->
            try {
                progress.collect { }
            } catch (cancelled: CancellationException) {
                return@PredictiveBackHandler
            }
            onExit()
        }

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
            // The same glyph as the toolbar's, flipped inward: the button that opened the mode
            // is the button that closes it.
            FloatingChartButton(
                icon = DesignR.drawable.icon_arrows_in,
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
internal enum class ChartSheet { TYPE, INDICATORS, TOOLS, DRAWINGS, SETUP, BACKTEST, LAYOUTS, INTERVAL, SCALE, COMPARE, EVENTS, MORE, PARTNERS, EXPLAIN, READINGS, RASAD, ARENA }

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
/**
 * The reader's pinned tools down the plot's leading edge (run E): the rail's favourites row,
 * without opening the rail. One glyph per tool, a tap arms it, and the strip steps aside the
 * moment a tool is armed so it never sits under the first point of a drawing.
 */
@Composable
private fun FavouriteToolStrip(favourites: Set<String>, onArm: (DrawingTool) -> Unit, modifier: Modifier = Modifier) {
    val tools = remember(favourites) { DrawingTools.ALL.filter { it.id in favourites }.take(FAVOURITE_STRIP_MAX) }
    if (tools.isEmpty()) return
    Column(
        modifier = modifier
            .clip(CoineProShapes.large)
            .background(CoineProColors.SurfaceElevated)
            .border(1.dp, CoineProColors.Border, CoineProShapes.large)
            .padding(CoineProSpacing.Half)
            .semantics { contentDescription = "favourite-tool-strip" },
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        tools.forEach { tool ->
            IconButton(
                onClick = { onArm(tool) },
                modifier = Modifier.size(FAVOURITE_STRIP_CELL).semantics { contentDescription = "favourite-tool-${tool.id}" },
            ) {
                Icon(
                    painter = painterResource(tool.icon.drawableRes()),
                    contentDescription = tool.label(inEnglish()),
                    tint = CoineProColors.Gold,
                    modifier = Modifier.size(FAVOURITE_STRIP_GLYPH),
                )
            }
        }
    }
}

/** The floating toolbar's plate height, as `SelectionToolbar` lays it out: one row of 40 dp cells and its padding. */
private val SELECTION_TOOLBAR_HEIGHT: Dp = 56.dp
private const val FAVOURITE_STRIP_MAX = 8
private val FAVOURITE_STRIP_CELL: Dp = 36.dp
private val FAVOURITE_STRIP_GLYPH: Dp = 20.dp

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
            // glass whatever number was written here. That is the other half of «خیلی به گوشه‌ی
            // سمت چپ چسبیده»: not only was twelve points too few, they were never spent.
            .absolutePadding(left = lead)
            .padding(bottom = WATERMARK_INSET + axis)
            // **No clip.** There was a `CoineProShapes.small` here, and it was cutting the logo in
            // half: that shape is a ten-point radius, this box closed is exactly the mark's own
            // twenty-three points square, and a ten-point round on an eleven-and-a-half-point
            // half-width is very nearly a circle. So the corners ate the P — its stem runs down the
            // artwork's left edge and its foot sits in the bottom-left corner, which is precisely
            // what a rounded rectangle removes first. What was left read as a ring with an arrow
            // through it, and that is «لوگو پایه‌ی P رو نداره». Opening the signature widened the box
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
            // Six per cent at rest — the reference's watermark is a ghost on the bars, not a
            // badge — and full ink once tapped open, where it is a name being read.
            tint = CoineProColors.TextPrimary.copy(alpha = if (expanded) 1f else WATERMARK_ALPHA),
            contentDescription = stringResource(
                if (expanded) R.string.chart_watermark_collapse else R.string.chart_watermark_expand,
            ),
        )
    }
}

/** Twelve points from the time axis, as the phone app sets it, and the floor for the lead. */
private val WATERMARK_INSET = 12.dp

/** The mark's ink at rest: six per cent, the design brief's measure of the reference. */
private const val WATERMARK_ALPHA = 0.06f

/**
 * How far in from the plot's leading edge the signature sits.
 *
 * ### Twelve points was a corner, not a position
 *
 * «لوگو روی چارت رو ۱۰ تا ۲۰ درصد به سمت راست بکشید، خیلی به گوشه‌ی سمت چپ چسبیده.» A flat twelve
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
    val haptics = rememberCoineProHaptics()
    Box(
        modifier = Modifier
            .clip(CoineProShapes.medium)
            .background(
                if (active) CoineProColors.TextPrimary else CoineProColors.SurfaceElevated,
            )
            .clickable {
                // A tick on the change, not on a re-press of the key already down.
                if (!active) haptics.select()
                onClick()
            }
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
        INTERVAL_GROUPS.forEach { (titleRes, frames) ->
            // The reference sets its group names — TICKS, SECONDS, MINUTES — in 12 sp capitals.
            // Persian has no capitals; the size and the weight are what carry over.
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.labelMedium,
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
            text = stringResource(R.string.chart_custom_interval),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            fontWeight = FontWeight.Normal,
        )
        CoineProTextField(
            value = typed,
            onValueChange = { typed = it },
            label = stringResource(R.string.chart_custom_interval_field),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
        CoineProPrimaryButton(
            text = custom?.let { stringResource(R.string.chart_custom_interval_show, it.label) }
                ?: stringResource(R.string.chart_custom_interval_show_generic),
            onClick = {
                custom?.let {
                    onSelect(ChartInterval.Custom(it))
                    typed = ""
                }
            },
            enabled = custom != null,
            // Full width and 56 tall: the reference's «+ Add interval».
            modifier = Modifier.fillMaxWidth().height(ADD_INTERVAL_HEIGHT),
        )
        CoineProNote(R.string.chart_custom_interval_note, style = MaterialTheme.typography.bodySmall)
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
    SheetLabel(stringResource(R.string.chart_favourites_label))
    Text(
        // A prose count of a shortlist, so Persian digits — unlike the wire spellings on the pills.
        text = stringResource(
            R.string.chart_favourites_count,
            starred.size.toPersianDigits(),
            TimeframeFavourites.MAX.toPersianDigits(),
        ) + stringResource(R.string.chart_favourites_full_stop),
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.TextMuted,
    )
    // And the one sentence that prevents a real mistake, through the component that decides whether
    // a tip is drawn inline or folded into an ⓘ: holding a timeframe here removes it from the page
    // as well as from the strip, which is not recoverable by tapping the same place again.
    if (onHide != null) CoineProNote(R.string.chart_favourites_hide_note)
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
                        struck -> stringResource(R.string.chart_favourite_restore)
                        pinned -> stringResource(R.string.chart_favourite_unpin)
                        else -> stringResource(R.string.chart_favourite_pin)
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
        SheetLabel(stringResource(R.string.chart_scale_measures))
        CoineProChipRow(
            options = PriceScaleMode.entries.map {
                CoineProChip(id = it.name, label = stringResource(it.labelRes))
            },
            selectedId = state.scaleMode.name,
            onSelect = { id ->
                PriceScaleMode.entries.firstOrNull { it.name == id }?.let(controller::setScaleMode)
            },
            compact = true,
        )
        Text(
            text = stringResource(state.scaleMode.noteRes),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )

        HorizontalDivider(color = CoineProColors.Border)

        SettingSwitch(
            label = stringResource(R.string.chart_scale_inverted),
            noteRes = R.string.chart_scale_inverted_note,
            checked = state.inverted,
            onChange = { controller.toggleInverted() },
        )
        SettingSwitch(
            label = stringResource(R.string.chart_scale_lock),
            noteRes = R.string.chart_scale_lock_note,
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
        SheetLabel(stringResource(R.string.chart_scale_side))
        CoineProChipRow(
            options = SCALE_SIDES.map { (side, labelRes) ->
                CoineProChip(id = side.name, label = stringResource(labelRes))
            },
            selectedId = state.scaleSide.name,
            onSelect = { id ->
                ScaleSide.entries.firstOrNull { it.name == id }?.let(controller::setScaleSide)
            },
            compact = true,
        )

        HorizontalDivider(color = CoineProColors.Border)

        // «منطقه‌ی زمانی» — item 107. A short list and not `ZoneId.getAvailableZoneIds()`: six
        // hundred rows in a bottom sheet is a search problem, and the four that matter to somebody
        // reading these two markets are the local one, the two sessions that move them, and UTC —
        // which is what every exchange timestamp is quoted in and the only one that never shifts.
        //
        // The label carries the current offset because a zone name alone does not answer the
        // question anybody is asking, which is «this candle closed at what o'clock for me».
        if (zoneId != null) {
            SheetLabel(stringResource(R.string.chart_scale_zone))
            CoineProChipRow(
                options = CHART_ZONES.map { (id, labelRes) ->
                    CoineProChip(id = id, label = stringResource(labelRes))
                },
                selectedId = zoneId,
                onSelect = { id -> id?.let(onSelectZone) },
                compact = true,
            )

            HorizontalDivider(color = CoineProColors.Border)
        }

        SheetLabel(stringResource(R.string.chart_scale_decimals))
        CoineProChipRow(
            // Null is «خودکار», which is not the same as zero: the axis derives a precision from
            // the range, and a chart of a coin priced at 0.00004 needs eight where gold needs two.
            options = DECIMAL_CHOICES.map { count ->
                CoineProChip(
                    id = count?.toString() ?: AUTOMATIC_DECIMALS,
                    label = count?.toPersianDigits() ?: stringResource(R.string.scale_decimals_auto),
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
    val haptics = rememberCoineProHaptics()
    val drawn = comparisons.map { it.symbol.uppercase() }.toSet()
    val offered = watchlist.filter { it.uppercase() != base.uppercase() && it.uppercase() !in drawn }
    val full = comparisons.size >= MAX_COMPARISONS

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        SheetLabel(stringResource(R.string.chart_compare_measure))
        CoineProChipRow(
            options = COMPARISON_BASES.map {
                CoineProChip(id = it.name, label = stringResource(it.labelRes))
            },
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
                text = ComparisonRefusal.LIMIT_REACHED.message(),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        } else if (offered.isEmpty()) {
            Text(
                text = stringResource(R.string.chart_compare_empty),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        } else {
            SheetLabel(stringResource(R.string.chart_compare_from_watchlist))
            offered.forEach { symbol ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CoineProShapes.small)
                        .clickable {
                            val answer = onAdd(symbol)
                            refusal = answer
                            // «It worked» and «it did not» must never feel the same. A refusal here
                            // is a sentence a reader has to look up to read, and the buzz is what
                            // makes them look. See `CoineProHaptics.reject`.
                            if (answer == null) haptics.select() else haptics.reject()
                        }
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
                text = reason.message(),
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
            text = stringResource(R.string.chart_compare_line, (index + 1).toPersianDigits()),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        Text(
            text = stringResource(R.string.chart_compare_remove),
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
            options = COMPARISON_BASES.map {
                CoineProChip(id = it.name, label = stringResource(it.labelRes))
            },
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
                        .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
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
                        contentDescription = stringResource(R.string.chart_compare_remove_named, series.label),
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
    @StringRes noteRes: Int,
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
            CoineProNote(
                noteRes,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
                modifier = Modifier.padding(top = 4.dp),
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
    CoineProNote(R.string.chart_interval_seconds_note, style = MaterialTheme.typography.bodySmall)
    HorizontalDivider(color = CoineProColors.Border)
}

/** The fifteen presets as a reader groups them, for the sheet behind «بیشتر». */
private val INTERVAL_GROUPS: List<Pair<Int, List<Timeframe>>> = listOf(
    R.string.chart_interval_group_minutes to listOf(
        Timeframe.M1,
        Timeframe.M2,
        Timeframe.M3,
        Timeframe.M5,
        Timeframe.M10,
        Timeframe.M15,
        Timeframe.M30,
        Timeframe.M45,
    ),
    R.string.chart_interval_group_hours to listOf(Timeframe.H1, Timeframe.H2, Timeframe.H3, Timeframe.H4),
    R.string.chart_interval_group_days to listOf(Timeframe.D1, Timeframe.W1, Timeframe.MN1),
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
private val CHART_ZONES: List<Pair<String, Int>> = listOf(
    "Asia/Tehran" to R.string.chart_zone_tehran,
    "America/New_York" to R.string.chart_zone_new_york,
    "Europe/London" to R.string.chart_zone_london,
    "UTC" to R.string.chart_zone_utc,
)

private val SCALE_SIDES: List<Pair<ScaleSide, Int>> = listOf(
    ScaleSide.RIGHT to R.string.chart_scale_side_right,
    ScaleSide.LEFT to R.string.chart_scale_side_left,
    ScaleSide.BOTH to R.string.chart_scale_side_both,
    ScaleSide.MERGED to R.string.chart_scale_side_merged,
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
internal val PriceScaleMode.labelRes: Int
    get() = when (this) {
        PriceScaleMode.REGULAR -> R.string.chart_scale_mode_regular
        PriceScaleMode.LOGARITHMIC -> R.string.chart_scale_mode_log
        PriceScaleMode.PERCENT -> R.string.chart_scale_mode_percent
        PriceScaleMode.INDEXED_100 -> R.string.chart_scale_mode_indexed
    }

/** One sentence on what each mode is for, because the four names do not say it on their own. */
private val PriceScaleMode.noteRes: Int
    get() = when (this) {
        PriceScaleMode.REGULAR -> R.string.chart_scale_mode_regular_note
        PriceScaleMode.LOGARITHMIC -> R.string.chart_scale_mode_log_note
        PriceScaleMode.PERCENT -> R.string.chart_scale_mode_percent_note
        PriceScaleMode.INDEXED_100 -> R.string.chart_scale_mode_indexed_note
    }

/** How a compared instrument is expressed against this one. */
private val ComparisonBasis.labelRes: Int
    get() = when (this) {
        ComparisonBasis.PERCENT -> R.string.chart_basis_percent
        ComparisonBasis.INDEXED_100 -> R.string.chart_basis_indexed
        ComparisonBasis.RATIO -> R.string.chart_basis_ratio
        ComparisonBasis.ABSOLUTE -> R.string.chart_basis_absolute
    }

/**
 * Why a comparison was refused, said to the reader.
 *
 * Each one is a different sentence on purpose. A single «نشد» would leave somebody at the cap
 * tapping the same row again, and somebody who mis-tapped the chart's own symbol looking for a
 * fault that is not there.
 */
@Composable
private fun ComparisonRefusal.message(): String = when (this) {
    ComparisonRefusal.BLANK -> stringResource(R.string.chart_refusal_blank)
    ComparisonRefusal.SAME_SYMBOL -> stringResource(R.string.chart_refusal_same)
    ComparisonRefusal.ALREADY_COMPARED -> stringResource(R.string.chart_refusal_already)
    ComparisonRefusal.LIMIT_REACHED ->
        stringResource(R.string.chart_refusal_limit, MAX_COMPARISONS.toPersianDigits())
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
 * candles. So they are still here, one drag down, with the handle costing a single 44-point row.
 *
 * ### Closed on arrival, and remembered per symbol (run G)
 *
 * It opened itself on every visit and took about a quarter of the chart screen: «پنل خوانش بازار و
 * ابزارها به‌صورت پیش‌فرض باز و ~۲۵٪ صفحه‌ی چارت را می‌گیرد». The page is the plot, so the plot gets
 * the glass until somebody asks otherwise — and when they do ask, the answer is kept. [open] is the
 * controller's, stored against the symbol, so a reader who drags the readings open on gold finds
 * them open on gold tomorrow and finds the index at full height.
 *
 * ### Why a drag and not only a tap
 *
 * Because the panel is a *height*, and a height is grabbed rather than pressed — the same gesture
 * that opens a sheet anywhere else in the system. The tap is kept beside it: the row is a 44-point
 * target either way, and a reader who taps a chevron has done nothing wrong. The threshold is a
 * fifth of a row's height so a deliberate pull opens it and a thumb sliding down the page does not.
 */
@Composable
private fun ChartReadingsDisclosure(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val haptics = rememberCoineProHaptics()
    // The drag's running total, reset at the end of every gesture. A vertical drag that passes the
    // threshold flips the panel once and then ignores the rest of the gesture, so a long pull opens
    // the panel rather than opening and closing it a dozen times on the way down.
    var dragged by remember { mutableFloatStateOf(0f) }
    var settled by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = CoineProColors.Border)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        if (settled) return@rememberDraggableState
                        dragged += delta
                        // Down opens, up closes — the direction the panel itself moves.
                        val wanted = when {
                            dragged >= READINGS_DRAG_THRESHOLD -> true
                            dragged <= -READINGS_DRAG_THRESHOLD -> false
                            else -> return@rememberDraggableState
                        }
                        settled = true
                        if (wanted != open) {
                            haptics.select()
                            onOpenChange(wanted)
                        }
                    },
                    onDragStopped = {
                        dragged = 0f
                        settled = false
                    },
                )
                .clickable {
                    haptics.select()
                    onOpenChange(!open)
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

/**
 * How far the readings handle has to be pulled before the panel flips, in pixels.
 *
 * A fifth of the 44-point row it sits in, at the three-times density this is read at: far enough
 * that a thumb travelling down the page past the handle does not open it, short enough that a
 * deliberate pull opens it before the finger has left the row.
 */
private const val READINGS_DRAG_THRESHOLD = 26f

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
    // The locale's own comma, resolved once. Every list in this strip is joined with it — the
    // studies a mark is about, and the studies inside one exclusion — so a Latin build reads
    // «pivots, swings» rather than «pivots، swings».
    val separator = stringResource(R.string.chart_list_separator)
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
        verticalArrangement = Arrangement.spacedBy(4.dp),
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
                    text = state.range?.let { span ->
                        state.interval.code + "  ·  " + stringResource(span.labelRes)
                    }
                        ?: state.interval.code,
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
                text = stringResource(
                    R.string.chart_provenance_line,
                    stringResource(claim.labelRes),
                    // Resolved through `map` and then joined: `joinToString`'s transform is a
                    // nullable parameter and therefore not an inline lambda, so a composable call
                    // cannot happen inside it. `map` is inline and can.
                    subjects.map { resolveArgument(it, separator) }.joinToString(separator),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.Buy,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.padding(top = CoineProSpacing.Half),
            )
            Text(
                text = stringResource(claim.noteRes),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextDisabled,
                fontWeight = FontWeight.Normal,
            )
        }
        if (exclusions.isNotEmpty()) {
            Text(
                text = exclusionsLine(
                    exclusions.map { reason ->
                        stringResource(
                            reason.res,
                            *reason.args.map { resolveArgument(it, separator) }.toTypedArray(),
                        )
                    },
                    heading = stringResource(R.string.provenance_exclusions_line),
                ),
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
            Text(
            text = stringResource(R.string.chart_setup_card_title),
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextPrimary,
        )
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
            SetupFigure(
                stringResource(R.string.setup_entry),
                order.entry,
                CoineProColors.TextPrimary,
                Modifier.weight(1f),
            )
            SetupFigure(
                stringResource(R.string.setup_stop),
                order.stopLoss,
                CoineProColors.Sell,
                Modifier.weight(1f),
            )
            SetupFigure(stringResource(R.string.setup_target), order.takeProfit, tone, Modifier.weight(1f))
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
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** Trend strength reads in the direction's own colour once there is a direction to read. */
@Composable
internal fun ChartReading.strengthColour(): Color = when {
    // The *threshold*, not the word: the label is bilingual since 4.72.0 and a colour rule that
    // compares it against one language's string would leave the English screen with no grey.
    strength < ChartReading.TRENDING_FLOOR -> CoineProColors.TextMuted
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
@Composable
internal fun studioSummary(indicators: Int, drawings: Int): String {
    // Both resolved before the branches, for the reason `selectionSummary` gives: a `stringResource`
    // inside a conditional is a composable call that happens on some compositions and not others.
    val indicatorLabel = stringResource(R.string.chart_hub_indicator_count, indicators.toPersianDigits())
    val drawingLabel = stringResource(R.string.chart_hub_drawing_count, drawings.toPersianDigits())
    val empty = stringResource(R.string.chart_hub_empty)
    val parts = buildList {
        if (indicators > 0) add(indicatorLabel)
        if (drawings > 0) add(drawingLabel)
    }
    return if (parts.isEmpty()) empty else parts.joinToString(" · ")
}

@Composable
private fun Loading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

/**
 * The wait before a sub-minute chart has a first bar, said in words.
 *
 * It is a different wait from every other one on this screen and it deserves different copy: no
 * request is in flight and none is going to be. The bars are folded from the price feed on this
 * phone, so the first one appears when the market next ticks — and on a quiet market that can be
 * several seconds. A spinner would promise something arriving from somewhere; this says what is
 * actually happening, and it names the length so a reader who wanted a minute chart can see they
 * are not on one.
 */
@Composable
private fun SecondsWarmingUp(interval: ChartInterval) {
    Column(
        modifier = Modifier.fillMaxSize().padding(CoineProSpacing.Four),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.chart_seconds_warming, interval.label),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.chart_seconds_warming_hint),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * One sentence per way a load can fail, shared by the full-screen failure and the inline row.
 *
 * Split out so the two cannot drift: the banner is the same news in a smaller place, and a reader
 * who meets both in one sitting must not be told two different things.
 */
private fun chartErrorText(error: ChartError): Int = when (error) {
    ChartError.NETWORK -> R.string.chart_load_failed
    ChartError.UNSUPPORTED_SYMBOL -> R.string.chart_load_unsupported
    ChartError.CHART_DISABLED -> R.string.chart_load_disabled
    // No retry appears under this one — the callers already exclude it, and that is deliberate:
    // this venue has no feed fine enough to build this bar length, so a «تلاش دوباره» would be a
    // button that cannot ever succeed.
    ChartError.INTERVAL_UNAVAILABLE -> R.string.chart_load_interval
}

/**
 * The failure as one row at the foot of a chart that still has candles on it.
 *
 * ### Why this exists at all
 *
 * Because the full-screen version was being shown for a *transient* failure over a working chart.
 * The owner's recording has it five times: a timeframe the venue did not answer for, and the whole
 * chart — the candles, the drawings, the indicators, the axis — replaced by a centred sentence. The
 * reader had lost nothing except the app's willingness to draw what it already had.
 *
 * So the rule is the one TradingView follows: the page is for «there is nothing to show you», and
 * anything else is a line. One row, a sentence, and the retry in it, over a chart that stays
 * readable and usable while it is up.
 */
@Composable
private fun ChartInlineFailure(error: ChartError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(horizontal = CoineProSpacing.Two)
            .clip(com.coinepro.core.designsystem.CoineProPillShape)
            // Opaque enough to read over candles, which is the whole difficulty of a banner on a
            // chart: a scrim that lets the plot through makes both illegible.
            .background(CoineProColors.SurfaceElevated.copy(alpha = 0.94f))
            .border(1.dp, CoineProColors.BorderSubtle, com.coinepro.core.designsystem.CoineProPillShape)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One)
            .semantics { contentDescription = "chart-inline-failure" },
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(chartErrorText(error)),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
            maxLines = 2,
        )
        if (error == ChartError.NETWORK) {
            Text(
                text = stringResource(R.string.chart_retry),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.Accent,
                modifier = Modifier
                    .clip(com.coinepro.core.designsystem.CoineProPillShape)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
            )
        }
    }
}

/**
 * Why the chart is empty, and what to do about it.
 *
 * Each case gets its own sentence, and only one of them offers a retry. Offering "try again" for a
 * symbol the platform does not carry sends the reader round a loop that cannot end.
 *
 * Shown only when there is genuinely nothing on the plot. A failure over a chart that still has
 * candles on it is [ChartInlineFailure] instead.
 */
@Composable
private fun ChartFailure(error: ChartError, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(CoineProSpacing.Four),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            // Resources, not literals. These four sentences were the last Persian strings left in
            // this file's own code, which meant an English reader met «چارت بارگیری نشد» on the one
            // screen in the app where nothing else was going to explain itself.
            text = stringResource(chartErrorText(error)),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
        if (error == ChartError.NETWORK) {
            Text(
                text = stringResource(R.string.chart_retry),
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
/**
 * The indicator sheet is full-height, the reference's: a list of eighty is a page, not a drawer.
 *
 * **One, as of 4.74.0** (run K item 3). It was 0.92, which left eight per cent of the window as a
 * strip of chart above the sheet and, with the keyboard up, put the catalogue's last rows below the
 * fold. There is nothing on that strip a reader is reading while they choose a study, and the whole
 * of the request is that the list be visible. The sheet's own handle and heading are still inside
 * it, so «full» is not edge to edge — it is as much as this sheet is allowed to take.
 */
private const val INDICATOR_SHEET_HEIGHT = 1f

/** The toolbar's slide out before the fullscreen window, and the chart's fade on a symbol switch. */
private const val FULLSCREEN_SLIDE_MS = 200
private const val SYMBOL_CROSSFADE_MS = 250
private const val INTERVAL_CROSSFADE_MS = 150

/** The shimmer down the left edge while older bars load. */
private val HISTORY_SKELETON_WIDTH = 24.dp

/**
 * How solid bars that are on their way out are drawn: forty per cent.
 *
 * The figure the owner asked for and the one TradingView uses, and it is a judgement about *two*
 * legibilities at once. Much above this and a reader cannot tell the old candles from the new ones
 * when they land — the switch stops reading as a switch. Much below it and the chart may as well
 * have been blanked, which is the thing being fixed. See [ChartUiState.stale].
 */
internal const val STALE_ALPHA = 0.4f

/**
 * How far above the plot's foot the inline failure sits: enough to clear the time axis.
 *
 * The dates are the one row at this end that has to stay readable while a banner is up — they are
 * how a reader judges how old the candles under it are.
 */
private val TIME_AXIS_CLEARANCE = 28.dp

private val INTERVAL_KEY_HEIGHT = 44.dp
private val INTERVAL_KEY_WIDTH = 56.dp

/** The «+ Add interval» button at the foot of the timeframe sheet, as the reference sizes it. */
private val ADD_INTERVAL_HEIGHT = 56.dp

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

/**
 * The plot's height once the hinge has had its say: [wanted] on a flat device, and on a table-top
 * posture the top half less the toolbar's row, never below [FOLD_PLOT_MIN]. Pure, so the arithmetic
 * is asserted on the JVM in `ChartFoldTest`.
 */
internal fun plotHeightAboveHinge(wanted: Dp, fold: CoineProFold): Dp {
    if (!fold.tableTop || fold.hingeTopDp <= 0) return wanted
    val aboveHinge = (fold.hingeTopDp - FOLD_CHROME_ABOVE_PLOT_DP).dp
    return wanted.coerceAtMost(aboveHinge).coerceAtLeast(FOLD_PLOT_MIN)
}

/** The header and the interval row that sit above the plot; what the hinge's top has to pay for. */
private const val FOLD_CHROME_ABOVE_PLOT_DP = 96

/** Below this the plot is a sparkline; a hinge that high is a device the layout does not know. */
private val FOLD_PLOT_MIN = 180.dp
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

/** What a secondary press on the plot asked about: the price under it, and where on screen. */
internal data class ChartContextMenu(val price: Double, val at: Offset)

/**
 * One argument of a [ChartExclusion] or a repaint subject, as a string.
 *
 * Three shapes reach here and each is a different kind of thing rather than a formatting variant: a
 * plain `String` is a name out of the catalogue and is printed as it is, an `Int` is a resource id —
 * the signal's own subject, a claim's own sentence — and a `List<String>` is several study names
 * that share one placeholder. Anything else is printed by `toString`, which is not a fallback worth
 * hiding: it would be a programming mistake and it would be visible.
 */
@Composable
private fun resolveArgument(value: Any, separator: String): String = when (value) {
    is Int -> stringResource(value)
    is List<*> -> value.joinToString(separator) { it.toString() }
    else -> value.toString()
}
