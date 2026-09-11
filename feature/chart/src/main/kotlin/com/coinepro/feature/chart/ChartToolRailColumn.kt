package com.coinepro.feature.chart

import androidx.compose.foundation.clickable
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.ChartIcon
import com.coinepro.core.chart.ChartIcons
import com.coinepro.core.chart.DrawingTool
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.ToolGroup
import com.coinepro.core.datastore.DrawingTemplate
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.inEnglish

/**
 * The drawing tools on a tablet: a 48 dp rail of groups, each opening a flyout.
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
 * Modes first — the pointer and the eraser are how a reader gets *out* of a tool, and a rail with
 * no way back is a trap — then the drawing groups in the catalogue's own order, then the
 * favourites, then «all tools» which opens the full grid in a sheet. Each group is one glyph: its
 * first tool's, because a group's first tool is the one it is named after.
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

    Row(modifier = modifier.fillMaxHeight()) {
    Column(
        modifier = Modifier
            .width(CHART_TOOL_RAIL)
            .fillMaxHeight()
            .background(CoineProColors.Surface)
            .verticalScroll(rememberScrollState())
            .semantics { contentDescription = "chart-tool-rail" },
        // No gap between the cells. Each is a 48-point touch target already, and a rail of icons
        // with air between them reads as a list of unrelated buttons rather than one instrument.
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (group in groups) {
            val glyph = DrawingTools.ALL.firstOrNull { it.group == group } ?: continue
            RailGlyph(
                icon = glyph.icon.name,
                description = group.label(english),
                selected = armed?.group == group || openGroup == group.name,
                onClick = { openGroup = if (openGroup == group.name) null else group.name },
            )
        }

        if (favourites.isNotEmpty()) {
            HorizontalDivider(
                color = CoineProColors.Border,
                modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.Half),
            )
            // The reader's own tools, pinned. The rail's whole promise is that the tool somebody
            // draws with forty times a session is one tap away rather than two.
            for (tool in favourites) {
                RailGlyph(
                    icon = tool.icon.name,
                    description = tool.label(english),
                    selected = armed?.id == tool.id,
                    onClick = { controller.arm(tool) },
                )
            }
        }

        HorizontalDivider(
            color = CoineProColors.Border,
            modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.Half),
        )
        RailGlyph(
            icon = "tv_layout_grid",
            description = stringResource(R.string.chart_tools_all),
            selected = false,
            onClick = { allTools = true },
        )
    }

    // **The flyout is layout, not a popup.**
    //
    // A popup would float over the plot, which is where TradingView puts it — and it is also a
    // separate window, which means it is absent from a screenshot and awkward under a test. This is
    // a column beside the rail: it appears when a group is opened, takes [FLYOUT_WIDTH] from the
    // plot while it is open, and goes when a tool is armed or the group is tapped again. What the
    // reader gives up is transient; what they get is a list they can read and hit.
    val open = openGroup?.let { name -> groups.firstOrNull { it.name == name } }
    if (open != null) {
        val tools = DrawingTools.ALL.filter { it.group == open }
        VerticalDivider(color = CoineProColors.Border)
        Column(
            modifier = Modifier
                .width(FLYOUT_WIDTH)
                .fillMaxHeight()
                .background(CoineProColors.Surface)
                .verticalScroll(rememberScrollState())
                .semantics { contentDescription = "chart-tool-flyout" },
        ) {
            Text(
                text = open.label(english),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                modifier = Modifier.padding(
                    horizontal = CoineProSpacing.OneHalf,
                    vertical = CoineProSpacing.One,
                ),
            )
            for (tool in tools) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            openGroup = null
                            controller.arm(tool)
                        }
                        .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                ) {
                    Icon(
                        painter = painterResource(ChartIcons.drawable(tool.icon)),
                        contentDescription = null,
                        tint = if (armed?.id == tool.id) CoineProColors.Gold else CoineProColors.TextSecondary,
                        modifier = Modifier.size(RAIL_GLYPH),
                    )
                    Text(
                        text = tool.label(english),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (armed?.id == tool.id) CoineProColors.Gold else CoineProColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
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

/** One 48 dp cell: a glyph, gold while its group holds the armed tool. */
@Composable
private fun RailGlyph(
    icon: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(CHART_TOOL_RAIL)
            .semantics { contentDescription = description },
    ) {
        Icon(
            painter = painterResource(ChartIcons.drawable(ChartIcon(icon))),
            contentDescription = null,
            tint = if (selected) CoineProColors.Gold else CoineProColors.TextMuted,
            modifier = Modifier.size(RAIL_GLYPH),
        )
    }
}

/** The mark inside a rail cell. The cell is the touch target; this is what is drawn in it. */
private val RAIL_GLYPH = 22.dp

/** The open group's column. Wide enough for «Modified Schiff pitchfork» at the row's own size. */
private val FLYOUT_WIDTH = 216.dp
