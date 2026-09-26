package com.coinepro.feature.chart

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.coinepro.core.chart.ChartIcon
import com.coinepro.core.chart.ChartIcons
import com.coinepro.core.chart.DrawingLayer
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.MagnetMode
import com.coinepro.core.chart.ToolGroup
import com.coinepro.core.datastore.DrawingTemplate
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.inEnglish
import com.coinepro.core.designsystem.pageAccentInk

/**
 * The drawing tools on a tablet or a desktop: a 48 dp rail of groups, each opening a flyout.
 *
 * ### What this replaces, and why
 *
 * A 280 dp palette, permanently open, in every tablet layout. It was the right shape for the
 * question run E asked — «the tools should not be a sheet on a screen this size» — and the wrong
 * answer to the one the owner asked next: on a Pixel Tablet it left the plot about **45 % of the
 * window**, and a chart screen whose chart is half the glass is a furniture screen.
 *
 * TradingView's desktop keeps the same tools in a column of *group* icons about a finger wide, with
 * the group's contents one tap away in a flyout. That is this. Ninety-one tools stay reachable, the
 * plot gets back 232 points, and the tool a reader is actually using is on the rail as a favourite.
 *
 * ### The order down the rail
 *
 * TradingView's: the drawing groups in the catalogue's own order, the favourites, then — after a
 * rule — the rail's own switches: magnet, stay in drawing mode, lock all, hide all; a rule; remove
 * all (CHART-18). «All tools» at the foot opens the full grid in a sheet. Each group is one glyph:
 * its first tool's, because a group's first tool is the one it is named after.
 *
 * ### The cells
 *
 * TradingView's measure (CHART-09): a 38 dp pitch, a 34 dp plate with 4 dp corners that lights under
 * the pointer and darkens for the armed group, the glyph at its own 28 dp box in the chrome's ink.
 * Every cell names itself in a tooltip (CHART-11).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun ChartToolRailColumn(
    state: ChartUiState,
    controller: ChartController,
    templates: List<DrawingTemplate>,
    defaultTemplateId: String?,
    onHelp: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val english = inEnglish()
    val hasVolume = state.series.hasVolume
    val groups = remember(hasVolume) {
        DrawingTools.GROUPS.filter { it != ToolGroup.VOLUME || hasVolume }
    }
    val favourites = remember(state.drawing.favourites) {
        DrawingTools.ALL.filter { it.id in state.drawing.favourites }
    }
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    var allTools by rememberSaveable { mutableStateOf(false) }
    val armed = state.drawing.tool
    val drawing = state.drawing

    Column(
        modifier = modifier
            .width(CHART_TOOL_RAIL)
            .fillMaxHeight()
            // The chart's own ground, not a raised one: a rail on white beside a plot on off-white
            // looked lifted over the chart it serves (CHART-20).
            .background(CoineProColors.Stage)
            .verticalScroll(rememberScrollState())
            .padding(vertical = CoineProSpacing.Half)
            .semantics { contentDescription = "chart-tool-rail" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (group in groups) {
            val glyph = DrawingTools.ALL.firstOrNull { it.group == group } ?: continue
            val open = openGroup == group.name
            Box {
                RailCell(
                    icon = ChartIcons.drawable(ChartIcon(glyph.icon.name)),
                    description = group.label(english),
                    active = armed?.group == group || open,
                    onClick = { openGroup = if (open) null else group.name },
                )
                if (open) {
                    ToolFlyout(
                        group = group,
                        armedId = armed?.id,
                        english = english,
                        onArm = { tool ->
                            openGroup = null
                            controller.arm(tool)
                        },
                        onDismiss = { openGroup = null },
                    )
                }
            }
        }

        if (favourites.isNotEmpty()) {
            RailSeparator()
            // The reader's own tools, pinned. The rail's whole promise is that the tool somebody
            // draws with forty times a session is one tap away rather than two.
            for (tool in favourites) {
                RailCell(
                    icon = ChartIcons.drawable(ChartIcon(tool.icon.name)),
                    description = tool.label(english),
                    active = armed?.id == tool.id,
                    onClick = { controller.arm(tool) },
                )
            }
        }

        // TradingView's rail switches, where the hand already is rather than inside a sheet.
        RailSeparator()
        RailCell(
            icon = DesignR.drawable.tv_magnet,
            description = stringResource(R.string.keys_magnet),
            active = drawing.magnetMode != MagnetMode.OFF,
            lit = drawing.magnetMode != MagnetMode.OFF,
            onClick = controller::cycleMagnet,
        )
        RailCell(
            icon = DesignR.drawable.tv_tool_keepdrawing,
            description = stringResource(R.string.keys_keep_drawing),
            active = drawing.keepDrawing,
            lit = drawing.keepDrawing,
            onClick = { controller.setKeepDrawing(!drawing.keepDrawing) },
        )
        RailCell(
            icon = if (drawing.lockedAll) DesignR.drawable.tv_lock else DesignR.drawable.tv_unlock,
            description = stringResource(R.string.chart_rail_lock_all),
            active = drawing.lockedAll,
            lit = drawing.lockedAll,
            onClick = { controller.setLockAllDrawings(!drawing.lockedAll) },
        )
        val allHidden = drawing.hidden.size == DrawingLayer.entries.size
        RailCell(
            icon = if (allHidden) DesignR.drawable.tv_eye_off else DesignR.drawable.tv_eye,
            description = stringResource(R.string.keys_hide_all),
            active = allHidden,
            lit = allHidden,
            onClick = { controller.setAllLayersHidden(!allHidden) },
        )
        RailSeparator()
        RailCell(
            icon = DesignR.drawable.tv_trash2,
            description = stringResource(R.string.keys_remove_all),
            active = false,
            onClick = controller::clearDrawings.takeIf { drawing.drawings.isNotEmpty() },
        )
        RailSeparator()
        RailCell(
            icon = DesignR.drawable.tv_layout_grid,
            description = stringResource(R.string.chart_tools_all),
            active = false,
            onClick = { allTools = true },
        )
    }

    // The full three-column grid, in a sheet rather than in the layout. Same palette the phone
    // opens, so a tool that can be reached on one is reachable on the other by construction.
    if (allTools) {
        CoineProSheet(
            title = stringResource(R.string.chart_tools_column_title),
            onDismiss = { allTools = false },
        ) {
            ChartToolPalette(
                state = state,
                controller = controller,
                templates = templates,
                defaultTemplateId = defaultTemplateId,
                onHelp = onHelp,
                onArmed = { allTools = false },
            )
        }
    }
}

/**
 * The open group's tools, **floating beside the rail** (CHART-06, DIALOGS-27).
 *
 * It was a column in the layout, and opening a group pushed the toolbar, the legend and every candle
 * 217 points sideways — the chart re-laid itself because a menu opened. TradingView's is a popup
 * anchored to the button: 272 wide, 6 dp corners, a shadow, 40 dp rows, the tool's shortcut at the
 * far end. The share picture never held it — `chartLayer.record` scopes the picture to the canvas —
 * so the reason the note here gave for a layout column no longer stands.
 */
@Composable
private fun ToolFlyout(
    group: ToolGroup,
    armedId: String?,
    english: Boolean,
    onArm: (com.coinepro.core.chart.DrawingTool) -> Unit,
    onDismiss: () -> Unit,
) {
    val tools = remember(group) { DrawingTools.ALL.filter { it.group == group } }
    val reader = LocalLayoutDirection.current
    val gap = with(LocalDensity.current) { (RAIL_CELL + FLYOUT_GAP).roundToPx() }
    // The rail is laid out left to right in every language (see `ChartWorkbench`), so the flyout
    // opens to its right; its rows take the reader's direction back.
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(gap, 0),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides reader) {
            Surface(
                modifier = Modifier
                    .width(FLYOUT_WIDTH)
                    .heightIn(max = FLYOUT_MAX_HEIGHT)
                    .semantics { contentDescription = "chart-tool-flyout" },
                shape = RoundedCornerShape(FLYOUT_RADIUS),
                color = CoineProColors.SurfaceOverlay,
                shadowElevation = FLYOUT_SHADOW,
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = CoineProSpacing.One)) {
                    Text(
                        text = group.label(english).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                        modifier = Modifier.padding(
                            start = CoineProSpacing.OneHalf,
                            end = CoineProSpacing.OneHalf,
                            top = CoineProSpacing.One,
                            bottom = CoineProSpacing.Half,
                        ),
                    )
                    for (tool in tools) {
                        val on = armedId == tool.id
                        val interaction = remember { MutableInteractionSource() }
                        val hovered by interaction.collectIsHoveredAsState()
                        val shortcut = remember(tool.id) { ChartKeyAction.entries.firstOrNull { it.tool == tool.id }?.combo }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(FLYOUT_ROW)
                                .chromePlate(interaction, active = on) { onArm(tool) }
                                .padding(horizontal = CoineProSpacing.One),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                        ) {
                            Icon(
                                painter = painterResource(ChartIcons.drawable(tool.icon)),
                                contentDescription = null,
                                tint = if (on) CoineProColors.pageAccentInk else chromeInk(hovered),
                                modifier = Modifier.size(RAIL_GLYPH),
                            )
                            Text(
                                text = tool.label(english),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (on) CoineProColors.pageAccentInk else CoineProColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            shortcut?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CoineProColors.TextMuted,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One rail cell: a [RAIL_CELL] pitch, a 34 dp plate, the glyph at 28 — every rail glyph is one of
 * the vendored `tv_*` set (CHART-08). [lit] draws the glyph in the accent
 * — a switch that is on — where [active] only deepens the plate.
 */
@Composable
private fun RailCell(
    @DrawableRes icon: Int,
    description: String,
    active: Boolean,
    onClick: (() -> Unit)?,
    lit: Boolean = false,
    vendored: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    ChromeTooltip(description) {
        Box(
            modifier = Modifier
                .size(width = CHART_TOOL_RAIL, height = RAIL_CELL)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(RAIL_PLATE)
                    .chromePlate(interaction, active = active, enabled = onClick != null) { onClick?.invoke() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = if (lit) CoineProColors.pageAccentInk else chromeInk(hovered, onClick != null),
                    modifier = Modifier.size(chromeGlyphSize(icon, vendored)),
                )
            }
        }
    }
}

/** TradingView's rail rule: 36 wide, the strong border, a little air either side. */
@Composable
private fun RailSeparator() {
    HorizontalDivider(
        color = CoineProColors.BorderStrong,
        modifier = Modifier.width(RAIL_RULE).padding(vertical = CoineProSpacing.One),
    )
}

/** The pitch down the rail, the plate in each cell, and the glyph box: TradingView's 38, 34 and 28. */
private val RAIL_CELL = 38.dp
private val RAIL_PLATE = 34.dp
private val RAIL_GLYPH = 28.dp
private val RAIL_RULE = 36.dp

/** The flyout: TradingView's 272-wide popup with 40 dp rows, 6 dp corners and a soft shadow. */
private val FLYOUT_WIDTH = 272.dp
private val FLYOUT_ROW = 40.dp
private val FLYOUT_RADIUS = 6.dp
private val FLYOUT_SHADOW = 8.dp
private val FLYOUT_GAP = 4.dp
private val FLYOUT_MAX_HEIGHT = 560.dp
