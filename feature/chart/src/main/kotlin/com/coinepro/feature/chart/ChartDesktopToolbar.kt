package com.coinepro.feature.chart

import com.coinepro.core.designsystem.coineProHorizontalScroll
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.BuiltInIndicatorTemplate
import com.coinepro.core.chart.BuiltInIndicatorTemplates
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ChartType
import com.coinepro.core.chart.drawableRes
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProMenuItem
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
 * layouts, settings, fullscreen, the camera and the trade button.
 *
 * Measured against TradingView's own (CHART-08, -09, -14): a 38 dp bar; every control a 34 dp plate
 * with 4 dp corners that lights on hover and darkens while pressed; one glyph family, the vendored
 * `tv_*` set, each at its own 28 dp box, so every glyph's ink is the same weight; words 14 sp regular.
 * Every icon-only control names itself in a tooltip (CHART-11).
 *
 * The row follows the reader's direction, as TradingView's Arabic chart does: in Persian the symbol
 * sits at the right-hand end and the layout cluster at the left. The drawing rail and the price axis
 * do not move — see [ChartWorkbench] — so the bar mirrors and the chart under it does not (CHART-03).
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
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // Below a desktop's width the worded buttons keep their glyph and lose the word — TradingView
        // does the same — so nothing is cut mid-word at the scroll edge (MOBILE-18).
        val worded = maxWidth >= DESKTOP_WORDED_MIN
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(DESKTOP_TOOLBAR_HEIGHT)
                .background(CoineProColors.Stage)
                .padding(horizontal = 4.dp)
                .testTag(DESKTOP_TOOLBAR_TAG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val scroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .scrollEdgeFade(scroll)
                    .coineProHorizontalScroll(scroll),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                // Symbol search: the mark and the ticker, the way TradingView's first button reads.
                ToolbarChip(
                    onClick = onSymbolSearch,
                    description = "toolbar-symbol",
                    tooltip = stringResource(R.string.chart_menu_search),
                ) { ink ->
                    CoineProAssetLogo(symbol = symbol, size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = symbol,
                        style = ChromeTextStyle(),
                        fontWeight = FontWeight.SemiBold,
                        color = ink,
                        maxLines = 1,
                    )
                }
                GlyphButton(DesignR.drawable.tv_plus, stringResource(R.string.chart_toolbar_compare), vendored = true) {
                    onOpen(ChartSheet.COMPARE)
                }
                ToolbarSeparator()
                // The interval: the favourites inline, the one in force lit, the caret for the rest.
                // TradingView's spellings on the keys — `1m 1h 4h 1D` — the stored wire is unchanged.
                val shown = (starred + interval.wire).distinct()
                shown.forEach { wire ->
                    val active = wire == interval.wire
                    ToolbarChip(
                        onClick = { ChartInterval.of(wire)?.let(onSelectInterval) },
                        description = "toolbar-interval-$wire",
                        active = active,
                    ) { ink ->
                        Text(
                            text = tvIntervalCode(wire),
                            style = ChromeTextStyle(),
                            fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                            color = if (active) CoineProColors.pageAccentInk else ink,
                        )
                    }
                }
                GlyphButton(DesignR.drawable.tv_caret_down, stringResource(R.string.chart_sheet_interval), vendored = true) {
                    onOpen(ChartSheet.INTERVAL)
                }
                ToolbarSeparator()
                val typeIcon = ChartCatalog.CHART_TYPES.firstOrNull { it.type == chartType }?.icon?.drawableRes()
                    ?: DesignR.drawable.tv_chart_candles
                GlyphButton(typeIcon, stringResource(R.string.chart_toolbar_type), vendored = true) { onOpen(ChartSheet.TYPE) }
                ToolbarSeparator()
                LabelledButton(
                    icon = DesignR.drawable.tv_indicators,
                    vendored = true,
                    label = stringResource(R.string.chart_band_indicators),
                    active = indicators > 0,
                    worded = worded,
                    description = "toolbar-indicators",
                ) { onOpen(ChartSheet.INDICATORS) }
                TemplatesButton(onTemplate)
                ToolbarSeparator()
                onAlert?.let {
                    LabelledButton(
                        icon = DesignR.drawable.tv_bell,
                        vendored = true,
                        label = stringResource(R.string.chart_toolbar_alert),
                        active = false,
                        worded = worded,
                        description = "toolbar-alert",
                        onClick = it,
                    )
                }
                LabelledButton(
                    icon = DesignR.drawable.tv_replay,
                    vendored = true,
                    label = stringResource(R.string.chart_toolbar_replay),
                    active = replayOn,
                    worded = worded,
                    description = "toolbar-replay",
                    onClick = onReplay,
                )
                ToolbarSeparator()
                GlyphButton(
                    DesignR.drawable.tv_undo,
                    stringResource(R.string.chart_more_undo),
                    vendored = true,
                    mirrored = true,
                    onClick = onUndo,
                )
                GlyphButton(
                    DesignR.drawable.tv_redo,
                    stringResource(R.string.chart_more_redo),
                    vendored = true,
                    mirrored = true,
                    onClick = onRedo,
                )
            }
            ToolbarSeparator()
            GlyphButton(DesignR.drawable.tv_layout_grid, stringResource(R.string.chart_sheet_layouts), vendored = true) {
                onOpen(ChartSheet.LAYOUTS)
            }
            GlyphButton(DesignR.drawable.tv_settings2, stringResource(R.string.chart_sheet_settings), vendored = true) {
                onOpen(ChartSheet.SETTINGS)
            }
            GlyphButton(DesignR.drawable.tv_maximize2, stringResource(R.string.chart_band_fullscreen), vendored = true, onClick = onFullscreen)
            GlyphButton(DesignR.drawable.tv_camera, stringResource(R.string.chart_toolbar_snapshot), vendored = true, onClick = onSnapshot)
            GlyphButton(DesignR.drawable.tv_more_horizontal, stringResource(R.string.chart_band_more), vendored = true) {
                onOpen(ChartSheet.MORE)
            }
            onTrade?.let { trade ->
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .height(DESKTOP_CONTROL)
                        .clip(CoineProShapes.extraSmall)
                        .background(CoineProColors.pageAccentInk)
                        .clickable(onClick = trade)
                        .semantics { contentDescription = "toolbar-trade" }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.chart_toolbar_trade),
                        style = ChromeTextStyle(),
                        fontWeight = FontWeight.SemiBold,
                        color = CoineProColors.onPageAccent,
                    )
                }
            }
        }
    }
    // One boundary under the bar, in the strong rule the separators use; the page draws none of its
    // own above the plot (CHART-15).
    HorizontalDivider(color = CoineProColors.Border, thickness = 1.dp)
}

/**
 * TradingView's «Indicator templates» button: the six ready-made sets in a menu, each a name over
 * its faint summary rather than one fused string (DIALOGS-22).
 */
@Composable
private fun TemplatesButton(onTemplate: (BuiltInIndicatorTemplate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        GlyphButton(DesignR.drawable.tv_tool_template, stringResource(R.string.chart_templates_heading), vendored = true) {
            open = true
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            BuiltInIndicatorTemplates.ALL.forEach { template ->
                CoineProMenuItem(
                    text = stringResource(templateName(template.id)),
                    supporting = templateSummary(template),
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

/** The chrome's words: TradingView's 14 px regular (CHART-14). */
@Composable
private fun ChromeTextStyle(): TextStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal)

/**
 * TradingView's control plate: nothing at rest, a raised plate under the pointer, a deeper one while
 * pressed or while the thing behind it is on — 4 dp corners, the same on every bar (CHART-09).
 */
@Composable
internal fun Modifier.chromePlate(
    interaction: MutableInteractionSource,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier {
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val ground = when {
        !enabled -> Color.Transparent
        pressed || active -> CoineProColors.SurfacePressed
        hovered -> CoineProColors.SurfaceHover
        else -> Color.Transparent
    }
    return this
        .clip(CoineProShapes.extraSmall)
        .background(ground)
        .hoverable(interaction, enabled = enabled)
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/** The ink of a chrome glyph at rest and under the pointer: TradingView's `#DBDBDB`, then white. */
@Composable
internal fun chromeInk(hovered: Boolean, enabled: Boolean = true): Color = when {
    !enabled -> CoineProColors.TextDisabled
    hovered -> CoineProColors.TextPrimary
    else -> CoineProColors.TextPrimary.copy(alpha = CHROME_INK_ALPHA)
}

/**
 * A control's name, after a pause under the pointer (CHART-11): TradingView's plain tooltip, a
 * pressed-plate ground with the primary ink. A long press shows it on a touch screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChromeTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
        tooltip = {
            PlainTooltip(
                containerColor = CoineProColors.SurfaceRaised,
                contentColor = CoineProColors.TextPrimary,
                shape = CoineProShapes.extraSmall,
            ) {
                Text(text = label, style = MaterialTheme.typography.bodySmall)
            }
        },
        state = rememberTooltipState(),
    ) { content() }
}

@Composable
private fun ToolbarChip(
    onClick: (() -> Unit)?,
    description: String,
    active: Boolean = false,
    tooltip: String? = null,
    content: @Composable (Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val chip: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .height(DESKTOP_CONTROL)
                .widthIn(min = DESKTOP_CONTROL)
                .chromePlate(interaction, active = active, enabled = onClick != null) { onClick?.invoke() }
                .semantics { contentDescription = description }
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) { content(chromeInk(hovered, onClick != null)) }
    }
    if (tooltip != null) ChromeTooltip(tooltip, chip) else chip()
}

/**
 * One glyph button. [vendored] says the glyph is one of the `tv_*` set, drawn inside its own 28-unit
 * box, and so takes the full 28 dp (CHART-08). [mirrored] turns an arrow for a right-to-left bar.
 */
@Composable
private fun GlyphButton(
    @DrawableRes icon: Int,
    label: String,
    active: Boolean = false,
    vendored: Boolean = false,
    mirrored: Boolean = false,
    onClick: (() -> Unit)?,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val flip = mirrored && LocalLayoutDirection.current == LayoutDirection.Rtl
    ChromeTooltip(label) {
        Box(
            modifier = Modifier
                .size(DESKTOP_CONTROL)
                .chromePlate(interaction, active = active, enabled = onClick != null) { onClick?.invoke() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                tint = if (active) CoineProColors.pageAccentInk else chromeInk(hovered, onClick != null),
                modifier = Modifier
                    .size(chromeGlyphSize(icon, vendored))
                    .then(if (flip) Modifier.mirrorX() else Modifier),
            )
        }
    }
}

@Composable
private fun LabelledButton(
    @DrawableRes icon: Int,
    label: String,
    active: Boolean,
    worded: Boolean,
    description: String,
    vendored: Boolean = false,
    onClick: () -> Unit,
) {
    ToolbarChip(
        onClick = onClick,
        description = description,
        tooltip = label.takeUnless { worded },
    ) { rest ->
        val ink = if (active) CoineProColors.pageAccentInk else rest
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(chromeGlyphSize(icon, vendored)),
        )
        if (worded) {
            Spacer(Modifier.width(4.dp))
            Text(text = label, style = ChromeTextStyle(), color = ink, maxLines = 1)
        }
    }
}

/** TradingView's separator: 1 × 22, the strong rule, a hair off each neighbour (CHART-15). */
@Composable
private fun ToolbarSeparator() {
    VerticalDivider(
        modifier = Modifier.height(22.dp).padding(horizontal = 4.dp),
        color = CoineProColors.BorderStrong,
    )
}

/**
 * A fade at whichever end of a sideways row still has more in it, so a control that runs past the
 * edge reads as «more this way» rather than as cut (MOBILE-18). A mask on the row's own pixels in
 * eight steps rather than a gradient brush, the same way the sheet's chip rows fade: the design
 * system keeps gradients to the brand mark and the chart.
 */
private fun Modifier.scrollEdgeFade(scroll: ScrollState): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = EDGE_FADE.toPx().coerceAtMost(size.width / 2f)
        val rtl = layoutDirection == LayoutDirection.Rtl
        // «Forward» is the reading end: the left edge in Persian.
        val fadeLeft = if (rtl) scroll.canScrollForward else scroll.canScrollBackward
        val fadeRight = if (rtl) scroll.canScrollBackward else scroll.canScrollForward
        val band = fade / EDGE_FADE_STEPS
        for (step in 0 until EDGE_FADE_STEPS) {
            // Band 0 is at the very edge and keeps the least of the row.
            val keep = Color.Black.copy(alpha = (step + 0.5f) / EDGE_FADE_STEPS)
            if (fadeLeft) {
                drawRect(keep, topLeft = Offset(step * band, 0f), size = Size(band, size.height), blendMode = BlendMode.DstIn)
            }
            if (fadeRight) {
                drawRect(
                    keep,
                    topLeft = Offset(size.width - (step + 1) * band, 0f),
                    size = Size(band, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
    }

/** Flips a glyph left to right. */
private fun Modifier.mirrorX(): Modifier = graphicsLayer { scaleX = -1f }

/** TradingView's desktop toolbar: 38 px, 34 px plates, 28 px glyph boxes. */
private val DESKTOP_TOOLBAR_HEIGHT = 38.dp
private val DESKTOP_CONTROL = 34.dp

/**
 * The box a chrome glyph is drawn in so that every glyph's *ink* is TradingView's (CHART-08). The
 * `tv_*` set draws inside a 28-unit box with a margin, so it takes 28; a Phosphor glyph a caller
 * still hands in reaches its box's edges, so it takes 22.
 */
internal fun chromeGlyphSize(@DrawableRes icon: Int, vendored: Boolean = false): Dp =
    if (vendored || icon in VENDORED_ON_28) VENDORED_GLYPH else PHOSPHOR_GLYPH

private val VENDORED_GLYPH = 28.dp
private val PHOSPHOR_GLYPH = 22.dp

/** The vendored glyphs a caller may hand the chrome without saying so — the side rail's panels. */
private val VENDORED_ON_28 = setOf(
    DesignR.drawable.tv_chart_columns,
    DesignR.drawable.tv_bell,
    DesignR.drawable.tv_bell_ring,
    DesignR.drawable.tv_star,
    DesignR.drawable.tv_info,
    DesignR.drawable.tv_list,
    DesignR.drawable.tv_sparkle,
    DesignR.drawable.tv_help_circle,
    DesignR.drawable.tv_calendar_days,
    DesignR.drawable.tv_layout_grid,
    DesignR.drawable.tv_code2,
    DesignR.drawable.tv_settings2,
    DesignR.drawable.tv_camera,
    DesignR.drawable.tv_search,
    DesignR.drawable.tv_pencil,
    DesignR.drawable.tv_magnet,
    DesignR.drawable.tv_trash2,
    DesignR.drawable.tv_more_horizontal,
    DesignR.drawable.tv_maximize2,
    DesignR.drawable.tv_minimize2,
    DesignR.drawable.tv_play,
)

/** `#DBDBDB` on TradingView's dark is its white at about 86 %; the same step off our primary ink. */
private const val CHROME_INK_ALPHA = 0.86f

/** Below this the worded buttons drop their words. */
private val DESKTOP_WORDED_MIN: Dp = 1200.dp

/** The fade at a scrolling row's open end. */
private val EDGE_FADE = 24.dp
private const val EDGE_FADE_STEPS = 8

internal const val DESKTOP_TOOLBAR_TAG = "chart-desktop-toolbar"

/**
 * TradingView's desktop bar *under* the chart (5.16.0): the date ranges and «Go to» at the reading
 * start, and at the other end the clock in the chart's zone, then `%`, `log` and `auto`. The phone
 * keeps its ranges in the sheet; a desktop has them one click away, where TradingView does.
 * 38 dp and 14 sp regular like the bar over the plot (CHART-16).
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
    /** TradingView's calendar button after the ranges; null where there are no bars to go to. */
    onGoToDate: (() -> Unit)? = null,
    /** Whether the price scale fits the bars on screen by itself, and the way back to that. */
    auto: Boolean = true,
    onAuto: (() -> Unit)? = null,
) {
    var now by remember { mutableStateOf(ChartClock.now()) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1_000)
            now = ChartClock.now()
        }
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(DESKTOP_BOTTOM_HEIGHT)
            .background(CoineProColors.Stage)
            .padding(horizontal = 4.dp)
            .testTag(DESKTOP_BOTTOM_TAG),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val scroll = rememberScrollState()
        Row(
            modifier = Modifier
                .weight(1f)
                .scrollEdgeFade(scroll)
                .coineProHorizontalScroll(scroll),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // TradingView reads shortest first from the reading start.
            ChartRange.OFFERED.reversed().forEach { option ->
                val active = option == range
                ToolbarChip(onClick = { onRange(option) }, description = "range-${option.name}", active = active) { ink ->
                    Text(
                        text = rangeCode(option),
                        style = ChromeTextStyle(),
                        color = if (active) CoineProColors.pageAccentInk else ink,
                    )
                }
            }
            onGoToDate?.let { go ->
                ToolbarSeparator()
                GlyphButton(DesignR.drawable.tv_calendar_days, stringResource(R.string.chart_more_goto), vendored = true, onClick = go)
            }
        }
        ToolbarChip(onClick = onZone, description = "toolbar-clock", tooltip = stringResource(R.string.chart_time_menu_zone)) { ink ->
            Text(
                text = desktopClock(now, zone),
                style = ChromeTextStyle(),
                color = ink,
                maxLines = 1,
            )
        }
        ToolbarSeparator()
        ToggleText("%", percent, "toolbar-percent", stringResource(R.string.chart_scale_mode_percent), onPercent)
        ToggleText("log", logarithmic, "toolbar-log", stringResource(R.string.chart_scale_mode_log), onLog)
        onAuto?.let { ToggleText("auto", auto, "toolbar-auto", stringResource(R.string.chart_toolbar_auto), it) }
    }
}

@Composable
private fun ToggleText(text: String, on: Boolean, description: String, tooltip: String, onClick: () -> Unit) {
    ToolbarChip(onClick = onClick, description = description, tooltip = tooltip) { ink ->
        Text(
            text = text,
            style = ChromeTextStyle(),
            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
            color = if (on) CoineProColors.pageAccentInk else ink,
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

private val DESKTOP_BOTTOM_HEIGHT = 38.dp

internal const val DESKTOP_BOTTOM_TAG = "chart-desktop-bottom"

/**
 * TradingView's own spellings under its chart — `1D 5D 1M 3M 6M YTD 1Y 5Y All` — Latin in both
 * languages, as a chart's controls are here. «All» joined the set in 5.18.2: a Persian word at the
 * end of a row of Latin codes read as two controls glued together (MOBILE-33).
 */
internal fun rangeCode(range: ChartRange): String = when (range) {
    ChartRange.D1 -> "1D"
    ChartRange.D5 -> "5D"
    ChartRange.M1 -> "1M"
    ChartRange.M3 -> "3M"
    ChartRange.M6 -> "6M"
    ChartRange.YTD -> "YTD"
    ChartRange.Y1 -> "1Y"
    ChartRange.Y5 -> "5Y"
    ChartRange.ALL -> "All"
}

/**
 * Where the desktop bar's clock reads the time. The wall clock everywhere but a golden frame, which
 * fixes it so a pixel comparison is not a comparison of two seconds.
 */
object ChartClock {
    var now: () -> Long = { System.currentTimeMillis() }
}
