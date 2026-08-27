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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProGoldRule
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.symbols.SymbolClassifier
import com.coinepro.core.chart.TradeFromChart
import com.coinepro.core.chart.TradeSide
import com.coinepro.core.chart.ChartReading
import com.coinepro.core.chart.ChartOrder
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
        onSelectSymbol?.let { select ->
            SymbolWheel(symbols = watchlist, current = state.symbol, onSelect = select)
        }
        TimeframeRow(state.timeframe, controller::setTimeframe)

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT)
                    // The chart alone, recorded into a layer. Sharing the whole screen would hand
                    // over the header and the toolbar; sharing this hands over the chart.
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
                    Box(modifier = Modifier.align(Alignment.TopStart).padding(CoineProSpacing.One)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    }
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

        ReadingRow(state)
        state.setup?.let { order -> SetupCard(order, onOpen = { sheet = ChartSheet.SETUP }) }
        StudioCard(
            indicators = state.activeIndicators.size,
            drawings = state.drawing.drawings.size,
            onOpen = onOpenStudio,
            onShare = {
                shareScope.launch {
                    ChartShare.share(context, chartLayer.toImageBitmap(), state.symbol)
                }
            },
        )
        Spacer(Modifier.height(CoineProSpacing.Three))
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

private enum class ChartSheet { TYPE, INDICATORS, TOOLS, DRAWINGS, SETUP, BACKTEST, LAYOUTS }

@Composable
private fun LaunchedStart(controller: ChartController) {
    androidx.compose.runtime.LaunchedEffect(controller) { controller.start() }
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
 * The timeframe strip: outlined pills, gold on the one in force.
 *
 * All eight, at the owner's call, in the mockup's shape rather than the mockup's five. Eight fit
 * because the pill is sized to its label and the row scrolls if a wider locale ever needs it —
 * dropping M1, M5 and M30 would have taken the three shortest frames away from scalpers, who are
 * exactly the readers who open a chart most often.
 */
@Composable
private fun TimeframeRow(selected: Timeframe, onSelect: (Timeframe) -> Unit) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = CoineProSpacing.OneHalf),
        // Tighter than the page's gutter, which is what lets all eight sit on a phone without the
        // row having to be scrolled to reach the shortest frames.
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        contentPadding = PaddingValues(horizontal = CoineProSpacing.Two),
    ) {
        // Reversed, so the row reads W1 · D1 · H4 · H1 · … from the side the eye starts on. The
        // enum is ordered shortest-first because that is how a period is naturally listed, but the
        // timeframes people reach for are the long ones, and in enum order they were the ones
        // scrolled off the edge.
        items(Timeframe.entries.reversed(), key = { it.wire }) { frame ->
            val active = frame == selected
            Box(
                modifier = Modifier
                    .clip(CoineProPillShape)
                    .background(if (active) CoineProTint.fill(CoineProColors.Gold, CoineProColors.Stage) else Color.Transparent)
                    .border(
                        width = 1.dp,
                        color = if (active) CoineProTint.edge(CoineProColors.Gold) else CoineProColors.Border,
                        shape = CoineProPillShape,
                    )
                    .clickable { onSelect(frame) }
                    .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
            ) {
                LtrDirection {
                    Text(
                        text = frame.wire,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) CoineProColors.Gold else CoineProColors.TextMuted,
                    )
                }
            }
        }
    }
}

/** What the card above the chart says: the window, and its high and low. */
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
            text = state.timeframe.label,
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
                    painter = painterResource(DesignR.drawable.icon_caret_left),
                    contentDescription = null,
                    tint = CoineProColors.TextMuted,
                    modifier = Modifier.size(14.dp),
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
