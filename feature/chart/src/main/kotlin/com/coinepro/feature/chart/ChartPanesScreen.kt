package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.chart.ChartDecoration
import com.coinepro.core.chart.ChartLegendChange
import com.coinepro.core.chart.ChartMarketStatus
import com.coinepro.core.chart.ChartViewport
import com.coinepro.core.chart.Crosshair
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.chart.decimalsFor
import com.coinepro.core.chart.formatPrice
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.datastore.ChartLayoutStore
import com.coinepro.core.datastore.SymbolChartStateStore
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProGoldRule
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProWindowClass
import com.coinepro.core.designsystem.LtrDirection
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.coineProWindowClass
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.symbols.MarketHours
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Several charts on one screen.
 *
 * ### How many, and why the answer is different on a phone and on a tablet
 *
 * **Two on a phone.** Side by side at 411dp gives each pane about two hundred points of width. A
 * hundred candles in that is a smear, the price axis eats a fifth of it, and the legend covers what
 * is left — so a reader gets two charts they cannot read instead of one they can. Stacked, each
 * pane keeps the full width and gives up height, which is the axis a chart can actually spare: the
 * price range on screen shrinks, the shape of the market does not. A third pane on a phone is under
 * two hundred points tall, which is smaller than the chart card the owner already judged too small
 * and gave a fullscreen mode to escape.
 *
 * **Eight on a tablet**, and the same sentence explains both numbers rather than contradicting
 * itself. The cap was never about how many charts a reader wants; it was about how much glass one
 * pane needs to still be a chart — [PANE_MIN_WIDTH] across and [PANE_MIN_HEIGHT] down. A 1280×800
 * tablet holds three columns of those with room to spare, so eight panes are three rows of real
 * charts rather than eight smears, and the web terminal's own sixteen stops being a different
 * product and starts being a different screen size. Eight rather than sixteen because sixteen on a
 * ten-inch tablet is back under the floor, and because eight live panes are eight subscriptions and
 * eight draw passes a frame.
 *
 * [maxPanesFor] is where the two numbers are written down, and it reads them off the window rather
 * than off a build flag, so a tablet in a half-screen split gets the phone's answer — which is
 * correct, because in that split it *is* a phone-shaped window.
 *
 * ### The grid, and why the panes are not simply stacked any more
 *
 * [ChartPaneGrid] measures the room it was given and fills it in as many columns as
 * [PANE_MIN_WIDTH] allows, up to [PANE_MAX_COLUMNS]. On a phone that arithmetic returns one column
 * and the screen is exactly what it always was. Nothing here is switched on by a device check.
 *
 * ### Each pane is a whole chart
 *
 * Its own symbol, its own interval, its own indicators and its own drawings, because each pane is
 * a real [ChartController] out of the app's own holder — the same object the single-chart screen
 * uses. That is what makes the per-symbol restore work here too: opening gold in a pane brings back
 * the timeframe and the indicators that symbol was last read on, not a fresh default.
 *
 * ### What the panes share is switched on one thing at a time
 *
 * See [PaneSync]. Everything defaults off, because panes that immediately overwrite each other's
 * symbol are one chart drawn several times. A tie runs from the first pane outward; with eight
 * panes "the other one" is not a thing that exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartPanesScreen(
    /** The instrument the first pane opens on — the chart the reader split from. */
    firstSymbol: String,
    /**
     * The app's controller holder, keyed by symbol.
     *
     * A function rather than a fixed list of controllers, because a pane's symbol changes while the
     * screen is open and each symbol has its own controller with its own drawings. Handing in fixed
     * ones would make the symbol switch a navigation, which is what this screen exists to avoid.
     */
    controllerFor: (String) -> ChartController,
    modifier: Modifier = Modifier,
    /** The reader's own list, for choosing what each pane shows. */
    watchlist: List<String> = emptyList(),
    /** Live prices for the picker strips, where the shell has them. */
    quotes: Map<String, WatchlistQuote> = emptyMap(),
    /** Where the sync switches, the pane count and the panes' symbols are kept between visits. */
    workspace: ChartWorkspaceStore? = null,
    symbolChartStates: SymbolChartStateStore? = null,
    chartLayoutStore: ChartLayoutStore? = null,
    onBack: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val maxPanes = maxPanesFor(coineProWindowClass())

    /**
     * What each pane is showing, as one comma-joined string.
     *
     * One string so `rememberSaveable` can put it in a `Bundle` without a custom saver — the same
     * reason `ChartScreen` keeps its own starred timeframes that way. A wire symbol is letters and
     * digits, so a comma can never appear inside one.
     */
    var encoded by rememberSaveable(firstSymbol) {
        mutableStateOf(List(CoineProWindowClass.PHONE_MAX_PANES) { firstSymbol }.joinToString(","))
    }
    val symbols = remember(encoded) { encoded.split(',').filter(String::isNotBlank) }
    var sync by remember { mutableStateOf(PaneSync.OFF) }

    // What the panes share when a tie is on. Each carries the pane it came from, so the source
    // pane draws its own finger and its own window and only the *other* panes adopt them —
    // otherwise the source would be handed back its own report and the two would chase each
    // other round a loop. See [PaneCrosshair] and [PaneWindow].
    var sharedCrosshair by remember { mutableStateOf<PaneCrosshair?>(null) }
    var sharedWindow by remember { mutableStateOf<PaneWindow?>(null) }

    // Read once rather than collected. The switches and the symbols are edited here and written
    // back, and a collector would deliver each of this screen's own writes straight back as an
    // update — which is harmless for a boolean and is exactly how a stored symbol ends up fighting
    // the reader.
    LaunchedEffect(workspace, maxPanes) {
        val store = workspace ?: return@LaunchedEffect
        sync = runCatching { store.paneSync.first() }.getOrDefault(PaneSync.OFF)
        val saved = runCatching { store.extraPaneSymbols.first() }.getOrDefault(emptyList())
        // Clamped against *this* window and not against what was stored. A reader who arranged six
        // panes on a tablet and then opened the app on a phone gets two, and their six come back
        // untouched on the tablet, because nothing here writes the clamped value down.
        val count = runCatching { store.paneCount.first() }
            .getOrDefault(CoineProWindowClass.PHONE_MAX_PANES)
            .coerceIn(CoineProWindowClass.PHONE_MAX_PANES, maxPanes)
        encoded = (listOf(firstSymbol) + saved).let { restored ->
            List(count) { index -> restored.getOrNull(index) ?: firstSymbol }
        }.joinToString(",")
    }

    /** Every pane's live controller, one per symbol currently on screen. */
    val controllers = symbols.map { symbol ->
        // Keyed on the symbol so two panes showing one instrument share the controller — and
        // therefore share its drawings, which is what a reader comparing two timeframes of the
        // same market expects.
        remember(symbol, controllerFor) { controllerFor(symbol) }
    }

    val writeSymbols: (List<String>) -> Unit = { next ->
        encoded = next.joinToString(",")
        workspace?.let { store ->
            scope.launch { runCatching { store.setExtraPaneSymbols(next.drop(1)) } }
        }
    }

    val setSymbol: (Int, String) -> Unit = { index, symbol ->
        // A symbol tie replaces every pane at once rather than only the pane below, because with
        // more than two panes "the other one" has no meaning. See [PaneSync].
        val next = if (sync.symbol) symbols.map { symbol } else {
            symbols.mapIndexed { position, current -> if (position == index) symbol else current }
        }
        writeSymbols(next)
    }

    val setInterval: (Int, ChartInterval) -> Unit = { index, interval ->
        controllers.getOrNull(index)?.setInterval(interval)
        if (sync.interval) {
            controllers.forEachIndexed { position, controller ->
                if (position != index) controller.setInterval(interval)
            }
        }
    }

    val setCount: (Int) -> Unit = { requested ->
        val count = requested.coerceIn(CoineProWindowClass.PHONE_MAX_PANES, maxPanes)
        // A new pane opens on the first pane's symbol rather than on a default, because the first
        // pane is the chart the reader split from and a fourth chart of something they were not
        // looking at is a fourth subscription for nothing.
        val next = List(count) { index -> symbols.getOrNull(index) ?: symbols.firstOrNull() ?: firstSymbol }
        writeSymbols(next)
        workspace?.let { store -> scope.launch { runCatching { store.setPaneCount(count) } } }
    }

    val setSync: (PaneSyncField, Boolean) -> Unit = { field, on ->
        val next = sync.with(field, on)
        sync = next
        workspace?.let { store -> scope.launch { runCatching { store.setPaneSync(next) } } }
        // Switching a tie on applies it once, immediately, from the first pane. Waiting for the
        // next change would leave the reader looking at panes that say they are tied and are not,
        // and the only way to find out would be to change something.
        if (on) {
            when (field) {
                PaneSyncField.SYMBOL -> symbols.firstOrNull()?.let { head ->
                    writeSymbols(symbols.map { head })
                }
                PaneSyncField.INTERVAL -> controllers.firstOrNull()?.let { head ->
                    val interval = head.state.value.interval
                    controllers.drop(1).forEach { it.setInterval(interval) }
                }
                // Nothing to apply until a finger or a pan reports; switching off drops the
                // shared copy so the other panes go back to their own.
                PaneSyncField.CROSSHAIR -> sharedCrosshair = null
                PaneSyncField.TIME_RANGE -> sharedWindow = null
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(CoineProColors.Stage)) {
        PanesHeader(
            symbols = symbols,
            tied = sync.anyOn,
            count = symbols.size,
            maxPanes = maxPanes,
            onSetCount = setCount,
            onBack = onBack,
        )
        ChartPaneGrid(
            count = symbols.size,
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) { index, paneModifier ->
            ChartPane(
                controller = controllers[index],
                watchlist = watchlist,
                quotes = quotes,
                symbolChartStates = symbolChartStates,
                chartLayoutStore = chartLayoutStore,
                onSelectSymbol = { symbol -> setSymbol(index, symbol) },
                onSelectInterval = { interval -> setInterval(index, interval) },
                // Past two panes the per-pane strips are more chrome than chart: eight ticker rows
                // and eight timeframe strips is half the tablet spent saying what each pane is
                // rather than showing it. Dense panes move both behind the pane's own header.
                dense = symbols.size > CoineProWindowClass.PHONE_MAX_PANES,
                index = index,
                sync = sync,
                sharedCrosshair = sharedCrosshair,
                sharedWindow = sharedWindow,
                onCrosshair = { crosshair -> sharedCrosshair = crosshair },
                onWindow = { window -> sharedWindow = window },
                modifier = paneModifier,
            )
        }
        HorizontalDivider(color = CoineProColors.Border)
        PaneSyncRow(
            sync = sync,
            onChange = setSync,
        )
    }
}

/**
 * The panes, laid into as many columns as the room honestly allows.
 *
 * The arithmetic is the whole design and it is deliberately not a device check: a column exists
 * when there is [PANE_MIN_WIDTH] for it, and a row is at least [PANE_MIN_HEIGHT] tall whatever that
 * costs. On a 411dp phone that returns one column, so this composable draws exactly the stacked
 * layout the screen has always had; on a 1280dp tablet it returns three, and eight panes become
 * three rows of readable charts.
 *
 * The scroll only ever engages when the rows cannot fit — eight panes on a short landscape window,
 * say. That is the right failure: a reader who asks for eight panes on a window with room for six
 * gets six and a scroll, rather than eight bands too thin to read, which is the outcome the pane
 * cap exists to prevent in the first place.
 */
@Composable
private fun ChartPaneGrid(
    count: Int,
    modifier: Modifier = Modifier,
    pane: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val columns = paneColumns(maxWidth, count)
        val rows = (count + columns - 1) / columns
        // At least the floor, and otherwise an equal share of the room. `maxOf` rather than
        // `coerceAtLeast` on a division, because the division is what decides whether this layout
        // scrolls at all and the floor is what stops it lying about how tall a chart is.
        val rowHeight = maxOf(maxHeight / rows, PANE_MIN_HEIGHT)
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            for (row in 0 until rows) {
                if (row > 0) HorizontalDivider(color = CoineProColors.Border)
                Row(modifier = Modifier.fillMaxWidth().height(rowHeight)) {
                    for (column in 0 until columns) {
                        if (column > 0) VerticalDivider(color = CoineProColors.Border)
                        val index = row * columns + column
                        if (index < count) {
                            pane(index, Modifier.weight(1f).fillMaxHeight())
                        } else {
                            // The gap in a part-filled last row. Left empty rather than letting the
                            // last pane stretch across it: five panes in three columns should read
                            // as five charts on a grid, not as two charts and one twice as wide.
                            Spacer(Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}

/**
 * How many columns [width] can hold without a pane dropping under [PANE_MIN_WIDTH].
 *
 * Never more than there are panes — two panes on a wide tablet are two columns, not two thirds of
 * three — and never more than [PANE_MAX_COLUMNS].
 */
internal fun paneColumns(width: Dp, count: Int): Int {
    if (count <= 1) return 1
    val affordable = (width / PANE_MIN_WIDTH).toInt().coerceAtLeast(1)
    return minOf(affordable, count, PANE_MAX_COLUMNS)
}

/**
 * How many panes this window will carry: [CoineProWindowClass.PHONE_MAX_PANES] or
 * [CoineProWindowClass.TABLET_MAX_PANES].
 *
 * A function of the window rather than a constant, which is the change the tablet layer made
 * necessary. See the note on [ChartPanesScreen] for why the two numbers are different and why that
 * is one argument rather than two.
 */
internal fun maxPanesFor(window: CoineProWindowClass): Int = window.maxChartPanes

/**
 * One pane: what it is showing, how to change it, and the chart itself.
 *
 * Deliberately less than the chart screen. No readings, no setup card, no tool bar and no studio
 * entry — a pane is a fraction of the glass, and every row of chrome is taken from the candles.
 * What is kept is the two controls that make a pane a pane: which instrument, and which bar length.
 *
 * [dense] is what happens to those two past the second pane. Eight panes each carrying a timeframe
 * strip and a scrolling ticker row would spend more of a tablet on chrome than the whole phone
 * layout does; so in a dense pane both move behind the header — the symbol opens the picker, the
 * bar length opens the interval sheet — and the header itself is the only permanent row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartPane(
    controller: ChartController,
    watchlist: List<String>,
    quotes: Map<String, WatchlistQuote>,
    symbolChartStates: SymbolChartStateStore?,
    chartLayoutStore: ChartLayoutStore?,
    onSelectSymbol: (String) -> Unit,
    onSelectInterval: (ChartInterval) -> Unit,
    index: Int,
    sync: PaneSync,
    sharedCrosshair: PaneCrosshair?,
    sharedWindow: PaneWindow?,
    onCrosshair: (PaneCrosshair?) -> Unit,
    onWindow: (PaneWindow?) -> Unit,
    modifier: Modifier = Modifier,
    dense: Boolean = false,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var intervalSheet by remember { mutableStateOf(false) }
    var symbolSheet by remember { mutableStateOf(false) }

    // Bound before started, in that order and for the reason given on `ChartController.start`: the
    // saved settings have to be applied before the first fetch goes out, or the pane loads once on
    // the app default and then visibly jumps.
    LaunchedEffect(controller) {
        controller.bindStores(symbolChartStates, chartLayoutStore)
        controller.start()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Half),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            Row(
                // In a dense pane this pair is the symbol picker, because the ticker row that used
                // to be one is gone. In an ordinary pane it stays inert, so nothing about the phone
                // layout gains a tap target it did not have.
                modifier = if (dense) {
                    Modifier
                        .clip(CoineProShapes.small)
                        .clickable { symbolSheet = true }
                        .padding(horizontal = CoineProSpacing.Half, vertical = 4.dp)
                } else {
                    Modifier
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            ) {
                CoineProAssetLogo(symbol = state.symbol, size = PANE_LOGO)
                LtrDirection {
                    Text(
                        text = BidiText.isolateLtr(state.symbol),
                        style = MaterialTheme.typography.labelMedium,
                        color = CoineProColors.TextPrimary,
                    )
                }
            }
            Text(
                text = state.interval.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (dense) CoineProColors.TextSecondary else CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .then(
                        if (dense) {
                            Modifier
                                .clip(CoineProShapes.small)
                                .clickable { intervalSheet = true }
                                .padding(horizontal = CoineProSpacing.Half, vertical = 4.dp)
                        } else {
                            Modifier
                        },
                    )
                    .weight(1f),
            )
            state.lastPrice?.let { price ->
                LtrDirection {
                    Text(
                        text = formatPrice(price, decimalsFor(price)),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextSecondary,
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.loading && state.series.isEmpty -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }

                state.error != null && state.series.isEmpty -> Box(
                    modifier = Modifier.fillMaxSize().padding(CoineProSpacing.Two),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.panes_no_chart),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextMuted,
                    )
                }

                else -> CoineProChart(
                    series = state.visibleSeries,
                    modifier = Modifier.fillMaxSize(),
                    type = state.chartType,
                    decoration = ChartDecoration(
                        overlays = state.overlays,
                        levels = state.levels,
                        markers = state.markers,
                        panes = state.panes,
                        colours = state.chartColours,
                        showCountdown = !state.replay.isOn,
                    ),
                    focusIndex = state.focusIndex,
                    // The two halves of pane sync. Reported only while the tie is on, adopted only
                    // from another pane: the source keeps its own finger and its own window.
                    onCrosshairMove = if (sync.crosshair) {
                        { crosshair -> onCrosshair(PaneCrosshair.of(index, state.visibleSeries, crosshair)) }
                    } else {
                        null
                    },
                    crosshairOverride = sharedCrosshair
                        ?.takeIf { sync.crosshair && it.source != index }
                        ?.at(state.visibleSeries),
                    onViewportChange = if (sync.timeRange) {
                        { view -> onWindow(PaneWindow(index, view.barsPerView, view.offset, view.priceZoom)) }
                    } else {
                        null
                    },
                    viewportOverride = sharedWindow
                        ?.takeIf { sync.timeRange && it.source != index }
                        ?.at(state.visibleSeries),
                    // Item 108, and this layout is the one that gains most: it draws no page
                    // header at all, so before this the move and the market's state were nowhere
                    // on screen.
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
                    // The same threshold the single chart uses, and it matters more here: every
                    // pane is a full draw pass a frame, and a tablet may be running eight. See
                    // `CONFLATE_FROM_BARS`.
                    conflate = state.visibleSeries.bars.size > CONFLATE_FROM_BARS,
                    drawing = state.canvasDrawing,
                    onDrawing = controller::onDrawing,
                    eraser = state.drawing.eraser,
                    onLoadMore = controller::loadMore,
                    logScale = state.logScale,
                    scaleMode = state.scaleMode,
                    inverted = state.inverted,
                    priceBarLock = state.priceBarLock,
                    decimals = state.decimals,
                )
            }
        }
        if (!dense) {
            IntervalRow(
                selected = state.interval,
                onSelect = onSelectInterval,
                onMore = { intervalSheet = true },
            )
            WatchlistTickerRow(
                symbols = watchlist,
                current = state.symbol,
                quotes = quotes,
                onSelect = onSelectSymbol,
            )
        }
    }

    if (intervalSheet) {
        CoineProSheet(
            title = stringResource(R.string.panes_interval_title),
            subtitle = BidiText.isolateLtr(state.symbol),
            onDismiss = { intervalSheet = false },
        ) {
            IntervalSheetBody(
                selected = state.interval,
                onSelect = { interval ->
                    onSelectInterval(interval)
                    intervalSheet = false
                },
            )
        }
    }

    if (symbolSheet) {
        CoineProSheet(
            title = stringResource(R.string.panes_symbol_title),
            subtitle = BidiText.isolateLtr(state.symbol),
            onDismiss = { symbolSheet = false },
        ) {
            // The same strip a roomy pane carries permanently, in a sheet. One list rather than two
            // implementations of "choose an instrument", so a symbol that is filtered out of the
            // strip for having no artwork is filtered out of here too.
            WatchlistTickerRow(
                symbols = watchlist,
                current = state.symbol,
                quotes = quotes,
                onSelect = { symbol ->
                    onSelectSymbol(symbol)
                    symbolSheet = false
                },
            )
        }
    }
}

/**
 * The four ties, as four switches, behind one line.
 *
 * ### Why it collapses
 *
 * Four labelled switches with a sentence under each is about two hundred and twenty points — most
 * of a pane on a phone. On a screen whose entire argument is that every chart has to stay readable,
 * permanent chrome of that size would take back what the extra panes were for. Closed, it is one
 * row that says what is tied; open, it is the four switches with their notes and nothing hidden.
 *
 * Independent on purpose — see [PaneSync]. The two the canvas cannot yet honour are drawn disabled
 * with the reason under them rather than as live switches that store a preference and do nothing.
 */
@Composable
private fun PaneSyncRow(
    sync: PaneSync,
    onChange: (PaneSyncField, Boolean) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CoineProColors.Surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            Text(
                text = stringResource(R.string.panes_sync_title),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = syncSummary(sync),
                style = MaterialTheme.typography.labelSmall,
                color = if (sync.anyOn) CoineProColors.Gold else CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            Icon(
                // The list's own caret, turned: ninety degrees from "go there" is "open this". The
                // drawable is auto-mirrored in a right-to-left layout, so clockwise is down.
                painter = painterResource(DesignR.drawable.icon_caret_left),
                contentDescription = stringResource(
                    if (open) R.string.panes_sync_close else R.string.panes_sync_open,
                ),
                tint = CoineProColors.TextMuted,
                modifier = Modifier.size(14.dp).rotate(if (open) -90f else 90f),
            )
        }
        if (open) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = SYNC_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = CoineProSpacing.Gutter,
                        end = CoineProSpacing.Gutter,
                        bottom = CoineProSpacing.One,
                    ),
                verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                PaneSyncField.entries.forEach { field ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(field.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = CoineProColors.TextPrimary,
                            )
                            Text(
                                text = stringResource(field.noteRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = CoineProColors.TextMuted,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Switch(
                            checked = sync.isOn(field),
                            onCheckedChange = { on -> onChange(field, on) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CoineProColors.OnAccent,
                                checkedTrackColor = CoineProColors.AccentFill,
                                uncheckedThumbColor = CoineProColors.TextMuted,
                                uncheckedTrackColor = CoineProColors.SurfaceElevated,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * What is tied, in a phrase, for the closed row.
 *
 * Names the fields rather than counting them. «۲ مورد» would make the reader open the row to find
 * out which two, which is the one thing a summary must not do.
 */
@Composable
private fun syncSummary(sync: PaneSync): String {
    val on = PaneSyncField.entries.filter(sync::isOn)
    if (on.isEmpty()) return stringResource(R.string.panes_sync_none)
    // Resolved through `map` and joined afterwards rather than inside `joinToString`: that one
    // takes its transform as a nullable function type, so it is not an inline lambda a composable
    // call may appear in.
    val labels = on.map { stringResource(it.labelRes) }
    return labels.joinToString(" · ")
}

/** Which instruments are up, how many panes there are, and a way back to the single chart. */
@Composable
private fun PanesHeader(
    symbols: List<String>,
    tied: Boolean,
    count: Int,
    maxPanes: Int,
    onSetCount: (Int) -> Unit,
    onBack: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // Persian digits: this is a count in a sentence, not a market figure.
                    text = stringResource(R.string.panes_title, count.toPersianDigits()),
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                )
                LtrDirection {
                    Text(
                        // Distinct symbols only. Four panes of gold on four timeframes is a
                        // subtitle that says «XAUUSD» four times, which tells the reader nothing
                        // they cannot already see and pushes anything that would out of the row.
                        text = symbols.distinct().joinToString("  ·  ", limit = HEADER_SYMBOL_LIMIT) {
                            BidiText.isolateLtr(it)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (tied) CoineProColors.Gold else CoineProColors.TextMuted,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
            onBack?.let { back ->
                Row(
                    modifier = Modifier
                        .clip(CoineProShapes.small)
                        .background(CoineProColors.SurfaceElevated)
                        .clickable(onClick = back)
                        .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.Half),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                ) {
                    Icon(
                        painter = painterResource(DesignR.drawable.tv_chart_candles),
                        contentDescription = null,
                        tint = CoineProColors.Gold,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(R.string.panes_single),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.Gold,
                    )
                }
            }
        }
        // Absent on a phone, where the cap and the floor are both two and the control would be a
        // row with one choice in it — which reads as a broken segmented control rather than as a
        // setting that does not apply here.
        if (maxPanes > CoineProWindowClass.PHONE_MAX_PANES) {
            PaneCountRow(
                count = count,
                maxPanes = maxPanes,
                onSetCount = onSetCount,
                modifier = Modifier.padding(top = CoineProSpacing.Half),
            )
        }
        CoineProGoldRule(modifier = Modifier.padding(top = CoineProSpacing.Half))
    }
}

/**
 * How many panes, as one key per count.
 *
 * A row of keys rather than a stepper, because the reader almost always knows the number they want
 * — two to compare, four to watch a session — and a stepper makes six taps of what should be one.
 * Persian digits: a pane count is prose.
 */
@Composable
private fun PaneCountRow(
    count: Int,
    maxPanes: Int,
    onSetCount: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Text(
            text = stringResource(R.string.panes_count_label),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        for (value in CoineProWindowClass.PHONE_MAX_PANES..maxPanes) {
            val selected = value == count
            Box(
                modifier = Modifier
                    .clip(CoineProShapes.small)
                    .background(
                        if (selected) CoineProColors.SurfaceElevated else CoineProColors.Surface,
                    )
                    .clickable { onSetCount(value) }
                    .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value.toPersianDigits(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) CoineProColors.TextPrimary else CoineProColors.TextMuted,
                )
            }
        }
    }
}

/**
 * The narrowest a pane may be before it stops being a chart.
 *
 * Four hundred points is about three hundred and thirty of plot once the price gutter has taken its
 * share, which is a hundred candles at a spacing a reader can still see the wicks in. It is the
 * number the whole column arithmetic is built on: it is what makes a phone one column and a
 * landscape tablet three, without either being written down as a device.
 */
private val PANE_MIN_WIDTH = 400.dp

/**
 * The shortest a pane may be.
 *
 * Two hundred points is the floor the phone argument already established — below it a pane is
 * shorter than the chart card the owner judged too small. It applies on a tablet too, and it is
 * what makes the grid scroll rather than shrink when a reader asks for more panes than the window
 * can show at once.
 */
private val PANE_MIN_HEIGHT = 200.dp

/**
 * The most columns the grid will ever use.
 *
 * Three. A fourth column needs 1600dp before a pane clears [PANE_MIN_WIDTH], which is a desktop
 * window rather than a tablet; and past three the panes stop being charts the reader is comparing
 * and become a contact sheet they are scanning. Eight panes in three columns is three rows, which
 * is a shape a reader can hold in their head.
 */
private const val PANE_MAX_COLUMNS = 3

/** How many instruments the header names before it elides. Beyond this the line wraps or clips. */
private const val HEADER_SYMBOL_LIMIT = 4

/** Small: a pane header is chrome and every point of it is taken from the candles. */
private val PANE_LOGO = 18.dp

/**
 * How tall the open sync panel may grow.
 *
 * Four rows and their notes, capped so that opening it never pushes a pane off the screen — the
 * panel scrolls inside itself instead. A control that hides what it controls is not a control.
 */
private val SYNC_MAX_HEIGHT = 220.dp

/**
 * A crosshair one pane reported, as a moment in time rather than a bar index.
 *
 * Time and not index, because the panes may be on different timeframes or have loaded different
 * depths of history, and bar 412 of one is nothing in particular on another. The moment is what
 * a reader tied the panes to see: «where was everything else when this happened».
 */
data class PaneCrosshair(val source: Int, val time: Long) {
    /** This moment on [series] — the last bar that had opened by then — or null off its history. */
    fun at(series: CandleSeries): Crosshair? {
        if (series.isEmpty) return null
        val times = series.time
        if (time < times.first()) return null
        var index = times.binarySearch(time)
        if (index < 0) index = -index - 2
        index = index.coerceIn(0, series.size - 1)
        return Crosshair(index, series.close[index])
    }

    companion object {
        fun of(source: Int, series: CandleSeries, crosshair: Crosshair?): PaneCrosshair? {
            val index = crosshair?.index ?: return null
            if (index !in 0 until series.size) return null
            return PaneCrosshair(source, series.time[index])
        }
    }
}

/**
 * A window one pane reported: the three numbers `CoineProChart.viewportOverride` adopts.
 *
 * Bars from the live edge, bars per screen and the price stretch — not the whole viewport, which
 * belongs to one series' prices. The offset is meaningful across panes on the same timeframe and
 * approximate across different ones, which is what the tie says it is.
 */
data class PaneWindow(val source: Int, val barsPerView: Int, val offset: Int, val priceZoom: Float) {
    fun at(series: CandleSeries): ChartViewport =
        ChartViewport(series = series, barsPerView = barsPerView, offset = offset, priceZoom = priceZoom)
}
