package com.coinepro.feature.chart

import com.coinepro.core.common.toPersianDigits
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import com.coinepro.core.help.CoineProHelpSheet
import com.coinepro.core.help.HelpCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.backtest.Backtest
import kotlinx.coroutines.launch
import com.coinepro.core.datastore.ChartLayout
import com.coinepro.core.chart.Replay
import com.coinepro.core.chart.ChartDecoration
import com.coinepro.core.chart.ChartTypePicker
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.chart.DrawingList
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.IndicatorPicker
import com.coinepro.core.chart.SignalOverlay
import com.coinepro.core.chart.ToolRail
import com.coinepro.core.chart.ActiveToolBar
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.LtrDirection
import com.coinepro.core.chart.decimalsFor
import com.coinepro.core.chart.formatPrice
import com.coinepro.core.marketdata.Timeframe
import com.coinepro.core.designsystem.R as DesignR

/**
 * The chart screen.
 *
 * This is the screen the whole `core:chart` module existed for and did not have: fifty-six
 * indicators, fifty-two drawing tools, eleven chart types and eight timeframes were all built,
 * tested and rendered into screenshots without a single reader being able to reach any of them.
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
     * Opens the NamaScript studio on this symbol.
     *
     * On the chart's own toolbar because that is where a reader is when they think "I want a line
     * this app does not have". A script written anywhere else would be written against a symbol
     * chosen twice.
     */
    onOpenScript: (() -> Unit)? = null,
    /** The reader's watchlist, for the switcher strip. Fewer than two symbols hides it. */
    watchlist: List<String> = emptyList(),
    onSelectSymbol: ((String) -> Unit)? = null,
    /** Takes the drawn setup as a paper trade. See [SetupSheetBody]. */
    onPaperTrade: ((symbol: String, buy: Boolean, entry: Double, size: Double) -> Unit)? = null,
    /** Saved layouts. Null leaves the button off — a build with no store has nothing to offer. */
    layouts: List<ChartLayout>? = null,
    onSaveLayout: ((ChartLayout) -> Unit)? = null,
    onDeleteLayout: ((String) -> Unit)? = null,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<ChartSheet?>(null) }

    // The «؟» dots on every picker raise an id; this is what answers them. Hosted here rather than
    // handed in by the app, because this screen is the only place in the product that *has* help
    // ids — the catalogue is 186 entries and 179 of them are chart tools and indicators. Passing
    // the host down from the app meant every caller had to remember to supply one, and none did:
    // the entire help feature was dead code, which R8 noticed before anybody else.
    var helpId by remember { mutableStateOf<String?>(null) }
    val help = rememberHelpCatalog(helpId != null)
    val helpEntry = helpId?.let { help?.get(it) }
    val onHelp: (String) -> Unit = { helpId = it }

    LaunchedStart(controller)

    val chartLayer = rememberGraphicsLayer()
    val shareScope = rememberCoroutineScope()
    val context = LocalContext.current

    val focusRequester = remember { FocusRequester() }
    // Requested once, so a keyboard works without the reader first tapping the chart. It is
    // harmless where there is no keyboard: focus on a container changes nothing a finger sees.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
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
        onSelectSymbol?.let { select ->
            SymbolWheel(symbols = watchlist, current = state.symbol, onSelect = select)
        }
        TimeframeRow(state.timeframe, controller::setTimeframe)

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // The chart alone, recorded into a layer. Sharing the whole screen would hand over
                // the status bar and the toolbar; sharing this hands over the chart.
                .drawWithContent {
                    chartLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(chartLayer)
                },
        ) {
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
                    ),
                    drawing = state.drawing,
                    onDrawing = controller::onDrawing,
                )
            }
            if (state.loadingMore) {
                // Over the chart rather than pushing it: paging back must not move the bars the
                // reader is looking at.
                Box(modifier = Modifier.align(Alignment.TopStart).padding(CoineProSpacing.One)) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        }

        if (state.replay.isOn) {
            ReplayBar(
                state = state.replay,
                onToggle = controller::replayToggle,
                onStep = controller::replayStep,
                onStepBack = controller::replayStepBack,
                onSeek = controller::replaySeek,
                onSpeed = controller::replaySetSpeed,
                onExit = controller::exitReplay,
            )
        }

        ActiveToolBar(
            tool = state.drawing.tool,
            placed = state.drawing.pending.size,
            onCancel = controller::cancelDrawing,
            onUndo = controller::undoDrawing,
            onHelp = onHelp,
        )
        Toolbar(
            activeIndicators = state.activeIndicators.size,
            drawings = state.drawing.drawings.size,
            hasSetup = state.setup != null,
            canBacktest = state.series.bars.size >= Backtest.MINIMUM_BARS,
            hasLayouts = layouts != null,
            onShare = {
                shareScope.launch {
                    ChartShare.share(context, chartLayer.toImageBitmap(), state.symbol)
                }
            },
            // Offered only when there is something to replay. A button that answers "not enough
            // bars" is a button that should not have been there.
            onReplay = controller::enterReplay
                .takeIf { !state.replay.isOn && state.series.bars.size >= Replay.MINIMUM_BARS },
            onOpenScript = onOpenScript,
            onOpen = { sheet = it },
        )
    }

    when (sheet) {
        ChartSheet.TYPE -> CoineProSheet(
            title = "نوع چارت",
            subtitle = "${(ChartCatalog.CHART_TYPES.size).toPersianDigits()} نوع",
            onDismiss = { sheet = null },
        ) {
            ChartTypePicker(
                selected = state.chartType,
                onSelect = {
                    controller.setChartType(it)
                    sheet = null
                },
                onHelp = onHelp,
            )
        }

        ChartSheet.INDICATORS -> CoineProSheet(
            title = "اندیکاتورها",
            subtitle = "${(ChartCatalog.INDICATORS.size).toPersianDigits()} اندیکاتور",
            onDismiss = { sheet = null },
        ) {
            // No dismiss on select: switching four indicators on is four taps, and a sheet that
            // closes after each one turns that into twelve.
            IndicatorPicker(
                active = state.activeIndicators,
                onToggle = { controller.toggleIndicator(it.id) },
                onHelp = onHelp,
            )
        }

        ChartSheet.TOOLS -> CoineProSheet(
            title = "ابزارهای ترسیم",
            subtitle = "${(DrawingTools.ALL.size).toPersianDigits()} ابزار",
            onDismiss = { sheet = null },
        ) {
            ToolRail(
                selected = state.drawing.tool?.id,
                onSelect = {
                    controller.arm(it)
                    sheet = null
                },
                onHelp = onHelp,
            )
        }

        ChartSheet.LAYOUTS -> CoineProSheet(
            title = "چیدمان‌ها",
            onDismiss = { sheet = null },
        ) {
            LayoutSheetBody(
                layouts = layouts.orEmpty(),
                current = ChartLayout(
                    name = "",
                    chartTypeId = state.chartType.name,
                    timeframeId = state.timeframe.name,
                    indicatorIds = state.activeIndicators.toList(),
                ),
                onApply = { layout ->
                    controller.applyLayout(layout.chartTypeId, layout.timeframeId, layout.indicatorIds)
                    sheet = null
                },
                onSave = { name ->
                    onSaveLayout?.invoke(
                        ChartLayout(
                            name = name,
                            chartTypeId = state.chartType.name,
                            timeframeId = state.timeframe.name,
                            indicatorIds = state.activeIndicators.toList(),
                        ),
                    )
                },
                onDelete = { name -> onDeleteLayout?.invoke(name) },
            )
        }

        ChartSheet.BACKTEST -> CoineProSheet(
            title = "بک‌تست",
            subtitle = state.symbol,
            onDismiss = { sheet = null },
        ) {
            BacktestSheetBody(bars = state.series.bars, symbol = state.symbol)
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
            title = "ترسیم‌های روی چارت",
            onDismiss = { sheet = null },
        ) {
            DrawingList(
                drawings = state.drawing.drawings,
                onSelect = { },
                onDelete = { controller.deleteDrawing(it.id) },
            )
        }

        null -> Unit
    }

    helpEntry?.let { entry ->
        CoineProHelpSheet(entry = entry, onDismiss = { helpId = null })
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
private fun rememberHelpCatalog(wanted: Boolean): HelpCatalog? {
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

private enum class ChartSheet { TYPE, INDICATORS, TOOLS, DRAWINGS, SETUP, BACKTEST, LAYOUTS }

@Composable
private fun LaunchedStart(controller: ChartController) {
    androidx.compose.runtime.LaunchedEffect(controller) { controller.start() }
}

/**
 * Symbol, timeframe, price and the session's move — the four things a reader checks before they
 * look at a single candle.
 *
 * The change is computed against the *first visible bar*, not against yesterday's close: this line
 * describes the picture on screen, and a percentage that disagreed with the bars under it would be
 * the more confusing of the two numbers.
 */
@Composable
private fun Header(state: ChartUiState, onOpenTerminal: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        CoineProAssetLogo(symbol = state.symbol, size = 28.dp)
        Column(modifier = Modifier.weight(1f)) {
            LtrDirection {
                Text(
                    text = state.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    color = CoineProColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = state.timeframe.label,
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        }
        state.lastPrice?.let { price ->
            Column(horizontalAlignment = Alignment.End) {
                LtrDirection {
                    Text(
                        // Latin digits, as every market figure in this app is: a price is read
                        // against a broker statement and a chart axis, both of which use them.
                        text = formatPrice(price, decimalsFor(price)),
                        style = MaterialTheme.typography.titleMedium,
                        color = CoineProColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                state.changePercent?.let { move ->
                    LtrDirection {
                        Text(
                            text = MarketNumberFormatter.signedPercent(move),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (move >= 0) CoineProColors.Buy else CoineProColors.Sell,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        onOpenTerminal?.let {
            // The expand glyph rather than a word: the header already carries the symbol and the
            // price, and a labelled button there would be the widest thing on the row.
            IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                Icon(
                    painter = painterResource(DesignR.drawable.tv_maximize2),
                    contentDescription = "ترمینال حرفه‌ای",
                    tint = CoineProColors.TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * The timeframe strip.
 *
 * Given its own vertical room and closed with a hairline. Before that the chips sat flush against
 * the chart's top gridline, and the selected one read as a label stuck to the plot rather than as
 * a control above it.
 */
@Composable
private fun TimeframeRow(selected: Timeframe, onSelect: (Timeframe) -> Unit) {
    Column {
        CoineProChipRow(
            // Reversed, so the row reads W1 · D1 · H4 · H1 · … from the side the eye starts on. The
            // enum is ordered shortest-first because that is how a period is naturally listed, but
            // the timeframes people actually reach for are the long ones, and in enum order they
            // were the ones scrolled off the edge.
            options = Timeframe.entries.reversed().map { CoineProChip(id = it.wire, label = it.wire) },
            selectedId = selected.wire,
            // Null cannot happen — no "all" chip is offered — but the row's contract allows it, and
            // a timeframe that silently becomes hourly because something returned null is worse
            // than one that does not change at all.
            onSelect = { id -> Timeframe.of(id)?.let(onSelect) },
            modifier = Modifier.padding(bottom = CoineProSpacing.One),
            compact = true,
        )
        HorizontalDivider(thickness = 1.dp, color = CoineProColors.BorderSubtle)
    }
}

/**
 * The chart's tool strip: icons only, and scrollable.
 *
 * Labelled buttons were the single worst piece of layout in this app. Eight of them in a fixed Row
 * overflowed the width, so Compose measured the last few against zero and their Persian labels
 * wrapped one character per line — a hundred and thirty density-independent pixels of empty black
 * between the chart and the toolbar, on every phone, in every screenshot.
 *
 * Icons carry the meaning here the way they do in every terminal, the label survives as the
 * accessibility name, and the row scrolls rather than being squeezed. A count rides as a small
 * filled badge on its icon instead of as a word beside it.
 */
@Composable
private fun Toolbar(
    activeIndicators: Int,
    drawings: Int,
    hasSetup: Boolean,
    canBacktest: Boolean,
    hasLayouts: Boolean,
    onShare: () -> Unit,
    onReplay: (() -> Unit)?,
    onOpenScript: (() -> Unit)?,
    onOpen: (ChartSheet) -> Unit,
) {
    Column {
        HorizontalDivider(thickness = 1.dp, color = CoineProColors.BorderSubtle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CoineProColors.Surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarButton(DesignR.drawable.tv_chart_candles, "نوع چارت") { onOpen(ChartSheet.TYPE) }
            ToolbarButton(DesignR.drawable.tv_tool_sine, "اندیکاتور", activeIndicators) {
                onOpen(ChartSheet.INDICATORS)
            }
            ToolbarButton(DesignR.drawable.tv_tool_trend, "ابزار") { onOpen(ChartSheet.TOOLS) }
            if (drawings > 0) {
                ToolbarButton(DesignR.drawable.tv_tool_select, "ترسیم‌ها", drawings) {
                    onOpen(ChartSheet.DRAWINGS)
                }
            }
            onReplay?.let { ToolbarButton(DesignR.drawable.tv_play, "بازپخش", onClick = it) }
            // Only once a setup exists to talk about. The button is the drawing's consequence, not
            // a second way to start one.
            if (hasSetup) {
                ToolbarButton(DesignR.drawable.tv_tool_longshort, "معامله") { onOpen(ChartSheet.SETUP) }
            }
            onOpenScript?.let {
                ToolbarButton(DesignR.drawable.tv_code2, "نما اسکریپت", onClick = it)
            }
            // On the chart because the bars are already here. A backtest screen elsewhere would
            // need a symbol picker, a timeframe picker and a second fetch to answer the same
            // question.
            if (canBacktest) {
                ToolbarButton(DesignR.drawable.icon_chart_line_up, "بک‌تست") { onOpen(ChartSheet.BACKTEST) }
            }
            if (hasLayouts) {
                ToolbarButton(DesignR.drawable.icon_sliders_horizontal, "چیدمان") { onOpen(ChartSheet.LAYOUTS) }
            }
            ToolbarButton(DesignR.drawable.icon_camera, "اشتراک تصویر", onClick = onShare)
        }
    }
}

/**
 * One tool: a 44dp target with a 20dp glyph, and a badge when something is on.
 *
 * Forty-four because that is the smallest target a finger hits reliably, and the row is now the
 * only chrome between the chart and the bottom of the screen — a miss here costs the reader the
 * gesture *and* whatever the mis-tap opened.
 */
@Composable
private fun ToolbarButton(icon: Int, label: String, count: Int = 0, onClick: () -> Unit) {
    val active = count > 0
    Box(
        modifier = Modifier
            .size(TOOL_TARGET)
            .clip(CoineProShapes.small)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (active) CoineProColors.Accent else CoineProColors.TextSecondary,
        )
        if (active) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .size(BADGE_SIZE)
                    .background(CoineProColors.Accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    // A prose count, so Persian digits — unlike a price, which stays Latin.
                    text = count.toPersianDigits(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = BADGE_TEXT),
                    color = CoineProColors.OnAccent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private val TOOL_TARGET = 44.dp
private val BADGE_SIZE = 14.dp
private val BADGE_TEXT = 8.sp

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
