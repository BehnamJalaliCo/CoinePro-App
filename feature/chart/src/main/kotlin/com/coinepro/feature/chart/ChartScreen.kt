package com.coinepro.feature.chart

import androidx.compose.foundation.background
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.chart.ChartCatalog
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
import com.coinepro.core.designsystem.persianDigits
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
    onHelp: ((String) -> Unit)? = null,
    signal: SignalOverlay? = null,
    /**
     * Opens the full web terminal on this symbol.
     *
     * Null on a build with no terminal address, which is the default — so the button is absent
     * rather than opening a blank page. It is the only route out of the native chart into a
     * WebView, and an ordinary reader who never presses it never meets one.
     */
    onOpenTerminal: (() -> Unit)? = null,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<ChartSheet?>(null) }

    LaunchedStart(controller)

    Column(modifier = Modifier.fillMaxSize().background(CoineProColors.Stage)) {
        Header(state, onOpenTerminal)
        TimeframeRow(state.timeframe, controller::setTimeframe)

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading && state.series.isEmpty -> Loading()
                state.error != null && state.series.isEmpty -> ChartFailure(state.error!!, controller::retry)
                else -> CoineProChart(
                    series = state.series,
                    modifier = Modifier.fillMaxSize(),
                    type = state.chartType,
                    decoration = ChartDecoration(
                        overlays = state.overlays,
                        signal = signal,
                        levels = state.levels,
                        markers = state.markers,
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
            onOpen = { sheet = it },
        )
    }

    when (sheet) {
        ChartSheet.TYPE -> CoineProSheet(
            title = "نوع چارت",
            subtitle = "${persianDigits(ChartCatalog.CHART_TYPES.size)} نوع",
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
            subtitle = "${persianDigits(ChartCatalog.INDICATORS.size)} اندیکاتور",
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
            subtitle = "${persianDigits(DrawingTools.ALL.size)} ابزار",
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
}

private enum class ChartSheet { TYPE, INDICATORS, TOOLS, DRAWINGS }

@Composable
private fun LaunchedStart(controller: ChartController) {
    androidx.compose.runtime.LaunchedEffect(controller) { controller.start() }
}

@Composable
private fun Header(state: ChartUiState, onOpenTerminal: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        CoineProAssetLogo(symbol = state.symbol, size = 32.dp)
        Column(modifier = Modifier.weight(1f)) {
            LtrDirection {
                Text(
                    text = state.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    color = CoineProColors.TextPrimary,
                )
            }
            Text(
                text = state.timeframe.label,
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        }
        state.lastPrice?.let { price ->
            LtrDirection {
                Text(
                    // Latin digits, as every market figure in this app is: a price is read against
                    // a broker statement and a chart axis, both of which use them.
                    text = formatPrice(price, decimalsFor(price)),
                    style = MaterialTheme.typography.titleMedium,
                    color = CoineProColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        onOpenTerminal?.let {
            // The expand glyph rather than a word: the header already carries the symbol and the
            // price, and a labelled button there would be the widest thing on the row.
            IconButton(onClick = it) {
                Icon(
                    painter = painterResource(DesignR.drawable.tv_maximize2),
                    contentDescription = "ترمینال حرفه‌ای",
                    tint = CoineProColors.TextSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun TimeframeRow(selected: Timeframe, onSelect: (Timeframe) -> Unit) {
    CoineProChipRow(
        // Reversed, so the row reads W1 · D1 · H4 · H1 · … from the side the eye starts on. The
        // enum is ordered shortest-first because that is how a period is naturally listed, but the
        // timeframes people actually reach for are the long ones, and in enum order they were the
        // ones scrolled off the edge.
        options = Timeframe.entries.reversed().map { CoineProChip(id = it.wire, label = it.wire) },
        selectedId = selected.wire,
        // Null cannot happen — no "all" chip is offered — but the row's contract allows it, and a
        // timeframe that silently becomes hourly because something returned null is worse than one
        // that does not change at all.
        onSelect = { id -> Timeframe.of(id)?.let(onSelect) },
    )
}

@Composable
private fun Toolbar(activeIndicators: Int, drawings: Int, onOpen: (ChartSheet) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CoineProColors.Surface)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
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
    }
}

@Composable
private fun ToolbarButton(icon: Int, label: String, count: Int = 0, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(com.coinepro.core.designsystem.CoineProShapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.Half),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (count > 0) CoineProColors.Accent else CoineProColors.TextSecondary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            // The count is what makes this readable at a glance: "اندیکاتور ۴" says four are on,
            // where a highlighted icon only says "some".
            text = if (count > 0) "$label ${persianDigits(count)}" else label,
            style = MaterialTheme.typography.labelSmall,
            color = if (count > 0) CoineProColors.Accent else CoineProColors.TextMuted,
        )
    }
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
