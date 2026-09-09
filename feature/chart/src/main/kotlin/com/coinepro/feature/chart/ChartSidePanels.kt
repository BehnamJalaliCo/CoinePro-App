package com.coinepro.feature.chart

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSpacing

/**
 * A panel the chart can dock beside itself on a wide window.
 *
 * The plan's right-hand panels — watchlist, object tree, depth of market, alerts, the NamaScript
 * editor — are screens other feature modules own, so the chart cannot compose them; it is handed
 * a list of these and draws whichever one the reader opened, in Material's supporting pane,
 * beside the plot. On a phone the same screens are routes and sheets, as they always were, and
 * this list is simply not read: the panel rail needs a window at least
 * [ChartWorkbench]'s tools column + the plot's floor + [CHART_SIDE_PANEL_WIDTH] + the rail wide.
 *
 * [id] is what the reader's choice is saved under; [labelRes] names the panel in the rail's
 * accessibility description and above its content; [icon] is the rail glyph.
 */
@Immutable
data class ChartSidePanel(
    val id: String,
    @StringRes val labelRes: Int,
    @DrawableRes val icon: Int,
    val content: @Composable () -> Unit,
)

/** The docked panel's width: a watchlist's row, a ladder, an editor line — none of them narrower. */
internal val CHART_SIDE_PANEL_WIDTH = 360.dp

/** The rail of panel glyphs at the end edge. */
internal val CHART_SIDE_RAIL_WIDTH = 48.dp

/** Whether a window this wide has room for the tools, the plot, a docked panel and the rail. */
internal fun sidePanelsFit(widthDp: Float): Boolean =
    widthDp >= (CHART_TOOL_COLUMN + CHART_MIN_PLOT_WIDTH + CHART_SIDE_PANEL_WIDTH + CHART_SIDE_RAIL_WIDTH).value

/**
 * The chart's main area with a supporting pane beside it and the rail that opens one.
 *
 * `SupportingPaneScaffold` is told one or two partitions rather than asked: one while nothing is
 * open (the supporting pane is then hidden), two once a panel is chosen. [main] receives the
 * modifier the scaffold measured for it, so the workbench inside re-measures its own columns
 * against the room that is left — with a panel open on a 1280 dp tablet the readings column goes
 * and the tools stay, which is the arithmetic `ChartWorkbench` already does.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun ChartSidePanelHost(
    panels: List<ChartSidePanel>,
    modifier: Modifier = Modifier,
    main: @Composable (Modifier) -> Unit,
) {
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = panels.firstOrNull { it.id == openId }
    Row(modifier = modifier.fillMaxSize()) {
        val partitions = if (open != null) 2 else 1
        val directive = PaneScaffoldDirective(
            maxHorizontalPartitions = partitions,
            horizontalPartitionSpacerSize = 0.dp,
            maxVerticalPartitions = 1,
            verticalPartitionSpacerSize = 0.dp,
            defaultPanePreferredWidth = CHART_SIDE_PANEL_WIDTH,
            excludedBounds = emptyList(),
        )
        val value = calculateThreePaneScaffoldValue(
            maxHorizontalPartitions = partitions,
            adaptStrategies = SupportingPaneScaffoldDefaults.adaptStrategies(),
            currentDestination = ThreePaneScaffoldDestinationItem(SupportingPaneScaffoldRole.Main, null),
        )
        SupportingPaneScaffold(
            directive = directive,
            value = value,
            modifier = Modifier.weight(1f).fillMaxHeight(),
            mainPane = { AnimatedPane { main(Modifier.fillMaxSize()) } },
            supportingPane = {
                AnimatedPane(modifier = Modifier.preferredWidth(CHART_SIDE_PANEL_WIDTH)) {
                    if (open != null) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            VerticalDivider(color = CoineProColors.Border)
                            Column(modifier = Modifier.fillMaxSize().background(CoineProColors.Stage)) {
                                Text(
                                    text = stringResource(open.labelRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CoineProColors.TextPrimary,
                                    modifier = Modifier.padding(
                                        horizontal = CoineProSpacing.Gutter,
                                        vertical = CoineProSpacing.One,
                                    ),
                                )
                                Box(modifier = Modifier.fillMaxWidth().weight(1f)) { open.content() }
                            }
                        }
                    }
                }
            },
        )
        VerticalDivider(color = CoineProColors.Border)
        ChartSideRail(
            panels = panels,
            openId = openId,
            onToggle = { id -> openId = if (openId == id) null else id },
        )
    }
}

/** One glyph per panel, the open one in gold; tapping the open one closes it. */
@Composable
private fun ChartSideRail(
    panels: List<ChartSidePanel>,
    openId: String?,
    onToggle: (String) -> Unit,
) {
    val description = stringResource(R.string.chart_side_rail)
    Column(
        modifier = Modifier
            .width(CHART_SIDE_RAIL_WIDTH)
            .fillMaxHeight()
            .background(CoineProColors.Stage)
            .semantics { contentDescription = description }
            .padding(vertical = CoineProSpacing.Half),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (panel in panels) {
            val selected = panel.id == openId
            IconButton(
                onClick = { onToggle(panel.id) },
                modifier = Modifier.semantics { contentDescription = "side-panel-${panel.id}" },
            ) {
                Icon(
                    painter = painterResource(panel.icon),
                    contentDescription = stringResource(panel.labelRes),
                    tint = if (selected) CoineProColors.Gold else CoineProColors.TextMuted,
                    modifier = Modifier.size(SIDE_RAIL_GLYPH),
                )
            }
        }
    }
}

private val SIDE_RAIL_GLYPH = 22.dp
