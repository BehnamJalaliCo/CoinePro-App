package com.coinepro.feature.chart

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import com.coinepro.core.chart.BuiltInIndicatorTemplate
import com.coinepro.core.chart.BuiltInIndicatorTemplates
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ChartType
import com.coinepro.core.chart.drawableRes
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.pageAccentInk
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.of
import com.coinepro.core.designsystem.onPageAccent

/**
 * TradingView's desktop chart toolbar, across the top of the chart on a wide window (5.16.0).
 *
 * The phone keeps TradingView's *phone* toolbar — the band under the plot. A browser window or a
 * tablet held sideways is TradingView's desktop, and there the toolbar is a row along the top in a
 * fixed order a trader's hand already knows: the symbol, compare, the interval with its favourites
 * inline, the chart type, indicators, templates, alert, replay, undo and redo; then at the far end
 * layouts, settings, fullscreen, the camera and the trade button. This is that row, in that order,
 * 40 dp tall with 32 dp controls and 20 dp glyphs, a hairline under it.
 *
 * Left to right in every language, as TradingView's is and as the chart under it is: the toolbar
 * belongs to the chart, and a chart reads left to right.
 */
@Composable
internal fun ChartDesktopToolbar(
    symbol: String,
    interval: ChartInterval,
    /** The reader's starred lengths, in wire spellings — inline, as TradingView lists favourites. */
    starred: List<String>,
    chartType: ChartType,
    indicators: Int,
    replayOn: Boolean,
    onSymbolSearch: (() -> Unit)?,
    onSelectInterval: (ChartInterval) -> Unit,
    onOpen: (ChartSheet) -> Unit,
    onTemplate: (BuiltInIndicatorTemplate) -> Unit,
    onAlert: (() -> Unit)?,
    onReplay: () -> Unit,
    onUndo: (() -> Unit)?,
    onRedo: (() -> Unit)?,
    onFullscreen: () -> Unit,
    onSnapshot: () -> Unit,
    onTrade: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(DESKTOP_TOOLBAR_HEIGHT)
                .background(CoineProColors.Stage)
                .padding(horizontal = 4.dp)
                .testTag(DESKTOP_TOOLBAR_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Symbol search: the mark and the ticker, bold, the way TradingView's first button reads.
                ToolbarChip(onClick = onSymbolSearch, description = "toolbar-symbol") {
                    CoineProAssetLogo(symbol = symbol, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = symbol,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = CoineProColors.TextPrimary,
                        maxLines = 1,
                    )
                }
                GlyphButton(DesignR.drawable.icon_plus, stringResource(R.string.chart_toolbar_compare)) {
                    onOpen(ChartSheet.COMPARE)
                }
                ToolbarSeparator()
                // The interval: the favourites inline, the one in force lit, the caret for the rest.
                val shown = (starred + interval.wire).distinct()
                shown.forEach { wire ->
                    val active = wire == interval.wire
                    ToolbarChip(
                        onClick = {
                            ChartInterval.of(wire)?.let(onSelectInterval)
                        },
                        description = "toolbar-interval-$wire",
                    ) {
                        Text(
                            text = wire,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) CoineProColors.pageAccentInk else CoineProColors.TextPrimary,
                        )
                    }
                }
                GlyphButton(DesignR.drawable.icon_caret_down, stringResource(R.string.chart_sheet_interval)) {
                    onOpen(ChartSheet.INTERVAL)
                }
                ToolbarSeparator()
                val typeIcon = ChartCatalog.CHART_TYPES.firstOrNull { it.type == chartType }?.icon?.drawableRes()
                    ?: DesignR.drawable.tv_chart_candles
                GlyphButton(typeIcon, stringResource(R.string.chart_toolbar_type)) { onOpen(ChartSheet.TYPE) }
                ToolbarSeparator()
                LabelledButton(
                    icon = DesignR.drawable.icon_sliders_horizontal,
                    label = stringResource(R.string.chart_band_indicators),
                    active = indicators > 0,
                    description = "toolbar-indicators",
                ) { onOpen(ChartSheet.INDICATORS) }
                TemplatesButton(onTemplate)
                ToolbarSeparator()
                onAlert?.let {
                    LabelledButton(
                        icon = DesignR.drawable.tv_bell,
                        label = stringResource(R.string.chart_toolbar_alert),
                        active = false,
                        description = "toolbar-alert",
                        onClick = it,
                    )
                }
                LabelledButton(
                    icon = DesignR.drawable.icon_rewind,
                    label = stringResource(R.string.chart_toolbar_replay),
                    active = replayOn,
                    description = "toolbar-replay",
                    onClick = onReplay,
                )
                ToolbarSeparator()
                GlyphButton(DesignR.drawable.icon_arrow_counter_clockwise, stringResource(R.string.chart_more_undo), onClick = onUndo)
                GlyphButton(DesignR.drawable.icon_arrow_clockwise, stringResource(R.string.chart_more_redo), onClick = onRedo)
            }
            ToolbarSeparator()
            GlyphButton(DesignR.drawable.tv_layout_grid, stringResource(R.string.chart_sheet_layouts)) { onOpen(ChartSheet.LAYOUTS) }
            GlyphButton(DesignR.drawable.tv_settings2, stringResource(R.string.chart_sheet_settings)) { onOpen(ChartSheet.SETTINGS) }
            GlyphButton(DesignR.drawable.tv_maximize2, stringResource(R.string.chart_band_fullscreen), onClick = onFullscreen)
            GlyphButton(DesignR.drawable.tv_camera, stringResource(R.string.chart_toolbar_snapshot), onClick = onSnapshot)
            GlyphButton(DesignR.drawable.tv_more_horizontal, stringResource(R.string.chart_band_more)) { onOpen(ChartSheet.MORE) }
            onTrade?.let { trade ->
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .height(DESKTOP_CONTROL)
                        .clip(CoineProShapes.small)
                        .background(CoineProColors.pageAccentInk)
                        .clickable(onClick = trade)
                        .semantics { contentDescription = "toolbar-trade" }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.chart_toolbar_trade),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = CoineProColors.onPageAccent,
                    )
                }
            }
        }
    }
    HorizontalDivider(color = CoineProColors.BorderSubtle, thickness = 1.dp)
}

/** TradingView's «Indicator templates» button: the six ready-made sets in a menu. */
@Composable
private fun TemplatesButton(onTemplate: (BuiltInIndicatorTemplate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        GlyphButton(DesignR.drawable.tv_tool_template, stringResource(R.string.chart_templates_heading)) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BuiltInIndicatorTemplates.ALL.forEach { template ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(templateName(template.id)) + "  ·  " + templateSummary(template),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    onClick = {
                        open = false
                        onTemplate(template)
                    },
                    modifier = Modifier.semantics { contentDescription = "toolbar-template-${template.id}" },
                )
            }
        }
    }
}

@Composable
private fun ToolbarChip(
    onClick: (() -> Unit)?,
    description: String,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .height(DESKTOP_CONTROL)
            .widthIn(min = DESKTOP_CONTROL)
            .clip(CoineProShapes.small)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .semantics { contentDescription = description }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun GlyphButton(
    @DrawableRes icon: Int,
    label: String,
    active: Boolean = false,
    onClick: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .size(DESKTOP_CONTROL)
            .clip(CoineProShapes.small)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = when {
                onClick == null -> CoineProColors.TextDisabled
                active -> CoineProColors.pageAccentInk
                else -> CoineProColors.TextPrimary
            },
            modifier = Modifier.size(DESKTOP_GLYPH),
        )
    }
}

@Composable
private fun LabelledButton(
    @DrawableRes icon: Int,
    label: String,
    active: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    val ink = if (active) CoineProColors.pageAccentInk else CoineProColors.TextPrimary
    ToolbarChip(onClick = onClick, description = description) {
        Icon(painter = painterResource(icon), contentDescription = null, tint = ink, modifier = Modifier.size(DESKTOP_GLYPH))
        Spacer(Modifier.width(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp), color = ink, maxLines = 1)
    }
}

@Composable
private fun ToolbarSeparator() {
    VerticalDivider(
        modifier = Modifier.height(20.dp).padding(horizontal = 4.dp),
        color = CoineProColors.Border,
    )
}

/** TradingView's desktop toolbar: 38–40 px, 32 px controls, 20 px glyphs. */
private val DESKTOP_TOOLBAR_HEIGHT = 40.dp
private val DESKTOP_CONTROL = 32.dp
private val DESKTOP_GLYPH = 20.dp

internal const val DESKTOP_TOOLBAR_TAG = "chart-desktop-toolbar"

/**
 * TradingView's desktop bar *under* the chart (5.16.0): the date ranges on the left — `1D 5D 1M 3M
 * 6M YTD 1Y 5Y All` — and on the right the clock in the chart's zone, then `%` and `log`. The
 * phone keeps its ranges in the sheet; a desktop has them one click away, where TradingView does.
 */
@Composable
internal fun ChartDesktopBottomBar(
    range: ChartRange?,
    zone: java.time.ZoneId,
    percent: Boolean,
    logarithmic: Boolean,
    onRange: (ChartRange) -> Unit,
    onZone: () -> Unit,
    onPercent: () -> Unit,
    onLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var now by remember { mutableStateOf(ChartClock.now()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            now = ChartClock.now()
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .height(DESKTOP_BOTTOM_HEIGHT)
                .background(CoineProColors.Stage)
                .padding(horizontal = 4.dp)
                .testTag(DESKTOP_BOTTOM_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // TradingView reads shortest first, left to right.
                ChartRange.OFFERED.reversed().forEach { option ->
                    val active = option == range
                    ToolbarChip(onClick = { onRange(option) }, description = "range-${option.name}") {
                        Text(
                            text = rangeCode(option) ?: stringResource(option.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) CoineProColors.pageAccentInk else CoineProColors.TextMuted,
                        )
                    }
                }
            }
            ToolbarChip(onClick = onZone, description = "toolbar-clock") {
                Text(
                    text = desktopClock(now, zone),
                    style = MaterialTheme.typography.labelMedium,
                    color = CoineProColors.TextMuted,
                    maxLines = 1,
                )
            }
            ToolbarSeparator()
            ToggleText("%", percent, "toolbar-percent", onPercent)
            ToggleText("log", logarithmic, "toolbar-log", onLog)
        }
    }
}

@Composable
private fun ToggleText(text: String, on: Boolean, description: String, onClick: () -> Unit) {
    ToolbarChip(onClick = onClick, description = description) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
            color = if (on) CoineProColors.pageAccentInk else CoineProColors.TextMuted,
        )
    }
}

/**
 * `14:05:09 (UTC+3:30)` — TradingView's clock, Latin in both languages because it is a figure on
 * the chart's own chrome, with the zone's offset rather than its name so it reads at a glance.
 */
internal fun desktopClock(epochMillis: Long, zone: java.time.ZoneId): String = runCatching {
    val at = java.time.Instant.ofEpochMilli(epochMillis).atZone(zone)
    val time = at.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss", java.util.Locale.US))
    val seconds = at.offset.totalSeconds
    val sign = if (seconds < 0) "-" else "+"
    val hours = kotlin.math.abs(seconds) / 3600
    val minutes = kotlin.math.abs(seconds) % 3600 / 60
    val offset = if (minutes == 0) "UTC$sign$hours" else "UTC$sign$hours:" + minutes.toString().padStart(2, '0')
    "$time ($offset)"
}.getOrDefault("")

private val DESKTOP_BOTTOM_HEIGHT = 32.dp

internal const val DESKTOP_BOTTOM_TAG = "chart-desktop-bottom"

/**
 * TradingView's own spellings under its chart — `1D 5D 1M 3M 6M YTD 1Y 5Y` — Latin in both languages,
 * as a chart's controls are here. «All» is a word, so it keeps its translation.
 */
internal fun rangeCode(range: ChartRange): String? = when (range) {
    ChartRange.D1 -> "1D"
    ChartRange.D5 -> "5D"
    ChartRange.M1 -> "1M"
    ChartRange.M3 -> "3M"
    ChartRange.M6 -> "6M"
    ChartRange.YTD -> "YTD"
    ChartRange.Y1 -> "1Y"
    ChartRange.Y5 -> "5Y"
    ChartRange.ALL -> null
}

/**
 * Where the desktop bar's clock reads the time. The wall clock everywhere but a golden frame, which
 * fixes it so a pixel comparison is not a comparison of two seconds.
 */
object ChartClock {
    var now: () -> Long = { System.currentTimeMillis() }
}
