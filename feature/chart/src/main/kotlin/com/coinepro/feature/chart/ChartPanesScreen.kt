package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.chart.ChartDecoration
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.chart.decimalsFor
import com.coinepro.core.chart.formatPrice
import com.coinepro.core.common.BidiText
import com.coinepro.core.datastore.ChartLayoutStore
import com.coinepro.core.datastore.SymbolChartStateStore
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProGoldRule
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.LtrDirection
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.marketdata.ChartInterval
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Two charts on one screen, stacked.
 *
 * ### Vertical only, and that is a decision rather than an omission
 *
 * Side by side at 411dp gives each pane about two hundred points of width. A hundred candles in
 * that is a smear, the price axis eats a fifth of it, and the legend covers what is left — so a
 * reader gets two charts they cannot read instead of one they can. Shipping that in order to claim
 * the feature is precisely the thing this app is trying not to do. Stacked, each pane keeps the
 * full width and gives up height, which is the axis a chart can actually spare: the price range on
 * screen shrinks, the shape of the market does not.
 *
 * A phone turned sideways gets two wide, short panes from the same code, because the panes are
 * measured rather than assumed. That is the honest way to get a side-by-side reading, and it is the
 * reader's decision rather than the app's.
 *
 * ### Two, and not sixteen
 *
 * The web terminal allows sixteen. It is also a thirteen-inch surface with a mouse, and the two
 * facts are the same fact. Every pane past the second on a phone is under two hundred points tall
 * — shorter than the chart card on the ordinary chart screen, which the owner already judged too
 * small and gave a fullscreen mode to escape. [MAX_PANES] is where the cap is written down.
 *
 * ### Each pane is a whole chart
 *
 * Its own symbol, its own interval, its own indicators and its own drawings, because each pane is
 * a real [ChartController] out of the app's own holder — the same object the single-chart screen
 * uses. That is what makes the per-symbol restore work here too: opening gold in the lower pane
 * brings back the timeframe and the indicators that symbol was last read on, not a fresh default.
 *
 * ### What the panes share is switched on one thing at a time
 *
 * See [PaneSync]. Everything defaults off, because two panes that immediately overwrite each
 * other's symbol are one chart drawn twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartPanesScreen(
    /** The instrument the upper pane opens on — the chart the reader split from. */
    firstSymbol: String,
    /**
     * The app's controller holder, keyed by symbol.
     *
     * A function rather than two controllers, because a pane's symbol changes while the screen is
     * open and each symbol has its own controller with its own drawings. Handing in two fixed ones
     * would make the symbol switch a navigation, which is what this screen exists to avoid.
     */
    controllerFor: (String) -> ChartController,
    modifier: Modifier = Modifier,
    /** The reader's own list, for choosing what each pane shows. */
    watchlist: List<String> = emptyList(),
    /** Live prices for the picker strips, where the shell has them. */
    quotes: Map<String, WatchlistQuote> = emptyMap(),
    /** Where the sync switches and the second pane's symbol are kept between visits. */
    workspace: ChartWorkspaceStore? = null,
    symbolChartStates: SymbolChartStateStore? = null,
    chartLayoutStore: ChartLayoutStore? = null,
    onBack: (() -> Unit)? = null,
    /**
     * Whether the canvas can report and accept a crosshair position yet.
     *
     * False today. `CoineProChart` owns its crosshair internally and hoists neither a report nor an
     * override, so the switch is offered **disabled** with the reason on it rather than as a switch
     * that stores a preference and changes nothing — which is the failure this codebase calls out
     * by name: a setting that survives a restart and does nothing is worse than an absent one,
     * because the reader believes it. The day the canvas hoists it, this becomes true and the
     * switch works with no other change here.
     */
    crosshairSyncAvailable: Boolean = false,
    /** The same, for the visible window of bars. See [crosshairSyncAvailable]. */
    timeRangeSyncAvailable: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var topSymbol by rememberSaveable(firstSymbol) { mutableStateOf(firstSymbol) }
    var bottomSymbol by rememberSaveable(firstSymbol) { mutableStateOf(firstSymbol) }
    var sync by remember { mutableStateOf(PaneSync.OFF) }

    // Read once rather than collected. The switches are edited here and written back, and a
    // collector would deliver each of this screen's own writes straight back as an update — which
    // is harmless for a boolean and is exactly how a stored symbol ends up fighting the reader.
    LaunchedEffect(workspace) {
        val store = workspace ?: return@LaunchedEffect
        sync = runCatching { store.paneSync.first() }.getOrDefault(PaneSync.OFF)
        val saved = runCatching { store.secondPaneSymbol.first() }.getOrNull()
        if (saved != null && saved != topSymbol) bottomSymbol = saved
    }

    val topController = remember(topSymbol, controllerFor) { controllerFor(topSymbol) }
    val bottomController = remember(bottomSymbol, controllerFor) { controllerFor(bottomSymbol) }

    val setSync: (PaneSyncField, Boolean) -> Unit = { field, on ->
        val next = sync.with(field, on)
        sync = next
        workspace?.let { store -> scope.launch { runCatching { store.setPaneSync(next) } } }
        // Switching a tie on applies it once, immediately, from the upper pane. Waiting for the
        // next change would leave the reader looking at two panes that say they are tied and are
        // not, and the only way to find out would be to change something.
        if (on) {
            when (field) {
                PaneSyncField.SYMBOL -> bottomSymbol = topSymbol
                PaneSyncField.INTERVAL ->
                    bottomController.setInterval(topController.state.value.interval)
                PaneSyncField.CROSSHAIR, PaneSyncField.TIME_RANGE -> Unit
            }
        }
    }

    val setTopSymbol: (String) -> Unit = { symbol ->
        topSymbol = symbol
        if (sync.symbol) {
            bottomSymbol = symbol
            workspace?.let { store -> scope.launch { runCatching { store.setSecondPaneSymbol(symbol) } } }
        }
    }
    val setBottomSymbol: (String) -> Unit = { symbol ->
        bottomSymbol = symbol
        workspace?.let { store -> scope.launch { runCatching { store.setSecondPaneSymbol(symbol) } } }
        if (sync.symbol) topSymbol = symbol
    }

    Column(modifier = modifier.fillMaxSize().background(CoineProColors.Stage)) {
        PanesHeader(
            top = topSymbol,
            bottom = bottomSymbol,
            tied = sync.anyOn,
            onBack = onBack,
        )
        ChartPane(
            controller = topController,
            watchlist = watchlist,
            quotes = quotes,
            symbolChartStates = symbolChartStates,
            chartLayoutStore = chartLayoutStore,
            onSelectSymbol = setTopSymbol,
            onSelectInterval = { interval ->
                topController.setInterval(interval)
                if (sync.interval) bottomController.setInterval(interval)
            },
            modifier = Modifier.weight(1f),
        )
        HorizontalDivider(color = CoineProColors.Border)
        ChartPane(
            controller = bottomController,
            watchlist = watchlist,
            quotes = quotes,
            symbolChartStates = symbolChartStates,
            chartLayoutStore = chartLayoutStore,
            onSelectSymbol = setBottomSymbol,
            onSelectInterval = { interval ->
                bottomController.setInterval(interval)
                if (sync.interval) topController.setInterval(interval)
            },
            modifier = Modifier.weight(1f),
        )
        HorizontalDivider(color = CoineProColors.Border)
        PaneSyncRow(
            sync = sync,
            onChange = setSync,
            crosshairAvailable = crosshairSyncAvailable,
            timeRangeAvailable = timeRangeSyncAvailable,
        )
    }
}

/**
 * One pane: what it is showing, how to change it, and the chart itself.
 *
 * Deliberately less than the chart screen. No readings, no setup card, no tool bar and no studio
 * entry — a pane is half a phone, and every row of chrome is taken from the candles. What is kept
 * is the two controls that make a pane a pane: which instrument, and which bar length.
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
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var intervalSheet by remember { mutableStateOf(false) }

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
            CoineProAssetLogo(symbol = state.symbol, size = PANE_LOGO)
            LtrDirection {
                Text(
                    text = BidiText.isolateLtr(state.symbol),
                    style = MaterialTheme.typography.labelMedium,
                    color = CoineProColors.TextPrimary,
                )
            }
            Text(
                text = state.interval.label,
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.weight(1f),
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
                        text = "این نماد در این پنجره چارت ندارد.",
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
                    drawing = state.canvasDrawing,
                    onDrawing = controller::onDrawing,
                    eraser = state.eraser,
                    onLoadMore = controller::loadMore,
                    logScale = state.logScale,
                    scaleMode = state.scaleMode,
                    inverted = state.inverted,
                    priceBarLock = state.priceBarLock,
                    decimals = state.decimals,
                )
            }
        }
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

    if (intervalSheet) {
        CoineProSheet(
            title = "بازهٔ زمانی",
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
}

/**
 * The four ties, as four switches, behind one line.
 *
 * ### Why it collapses
 *
 * Four labelled switches with a sentence under each is about two hundred and twenty points — most
 * of a pane. On a screen whose entire argument is that two charts have to stay readable, permanent
 * chrome of that size would take back what the second pane was for. Closed, it is one row that says
 * what is tied; open, it is the four switches with their notes and nothing hidden.
 *
 * Independent on purpose — see [PaneSync]. The two the canvas cannot yet honour are drawn disabled
 * with the reason under them rather than as live switches that store a preference and do nothing.
 */
@Composable
private fun PaneSyncRow(
    sync: PaneSync,
    onChange: (PaneSyncField, Boolean) -> Unit,
    crosshairAvailable: Boolean,
    timeRangeAvailable: Boolean,
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
                text = "هم‌گام‌سازی",
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
                contentDescription = if (open) "بستن" else "باز کردن",
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
                    val available = when (field) {
                        PaneSyncField.CROSSHAIR -> crosshairAvailable
                        PaneSyncField.TIME_RANGE -> timeRangeAvailable
                        else -> true
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = field.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (available) {
                                    CoineProColors.TextPrimary
                                } else {
                                    CoineProColors.TextDisabled
                                },
                            )
                            Text(
                                text = if (available) field.note else UNAVAILABLE_NOTE,
                                style = MaterialTheme.typography.bodySmall,
                                color = CoineProColors.TextMuted,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Switch(
                            checked = available && sync.isOn(field),
                            enabled = available,
                            onCheckedChange = { on -> onChange(field, on) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CoineProColors.OnAccent,
                                checkedTrackColor = CoineProColors.Accent,
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
private fun syncSummary(sync: PaneSync): String {
    val on = PaneSyncField.entries.filter(sync::isOn)
    return if (on.isEmpty()) "هیچ‌کدام" else on.joinToString(" · ") { it.label }
}

/** Which two instruments are up, and a way back to the single chart. */
@Composable
private fun PanesHeader(top: String, bottom: String, tied: Boolean, onBack: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "دو نمودار",
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                )
                LtrDirection {
                    Text(
                        text = BidiText.isolateLtr(top) + "  ·  " + BidiText.isolateLtr(bottom),
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
                        text = "یک نمودار",
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.Gold,
                    )
                }
            }
        }
        CoineProGoldRule(modifier = Modifier.padding(top = CoineProSpacing.Half))
    }
}

/**
 * How many panes this screen will ever show.
 *
 * Two. See the note on [ChartPanesScreen] for the argument: a third pane on a phone is under two
 * hundred points tall, which is smaller than the chart card the owner already judged too small.
 * The constant exists so that the number is written down once, with its reason, rather than living
 * as the fact that the composable happens to call [ChartPane] twice.
 */
const val MAX_PANES = 2

/** Why the crosshair and time-range switches are not live yet. See [ChartPanesScreen]. */
private const val UNAVAILABLE_NOTE =
    "بوم نمودار هنوز موقعیت نشانگر و پنجرهٔ دید را بیرون نمی‌دهد، پس این هم‌گام‌سازی فعلاً در دسترس نیست."

/** Small: a pane header is chrome and every point of it is taken from the candles. */
private val PANE_LOGO = 18.dp

/**
 * How tall the open sync panel may grow.
 *
 * Four rows and their notes, capped so that opening it never pushes a pane off the screen — the
 * panel scrolls inside itself instead. A control that hides what it controls is not a control.
 */
private val SYNC_MAX_HEIGHT = 220.dp
