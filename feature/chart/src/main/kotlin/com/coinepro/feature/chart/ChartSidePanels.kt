package com.coinepro.feature.chart

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
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

/**
 * The docked panel's width: a watchlist's row, a ladder, an editor line — none of them narrower.
 *
 * Since 4.72.0 it is the **starting** width of a panel the reader can drag, not a fixed one. The
 * owner's budget for a large tablet is «side panel 360–480 dp (drag-resizable, min 320)», and the
 * numbers below are that budget. Four hundred is where a NamaScript line stops wrapping, which is
 * why it is the default rather than the floor.
 */
internal val CHART_SIDE_PANEL_WIDTH = 400.dp

/** The narrowest a reader may drag a docked panel before it stops giving way. */
internal val CHART_SIDE_PANEL_MIN = 320.dp

/** And the widest, so a panel can never take the plot below [CHART_PLOT_SHARE]. */
internal val CHART_SIDE_PANEL_MAX = 480.dp

/**
 * The share of the workbench the plot keeps whatever else is open — **65 %**.
 *
 * The owner's number, and the one the 4.71.0 tablet frames broke: with a 280 dp palette and a
 * 360 dp panel a Pixel Tablet's chart was under half the window. It is enforced here rather than
 * trusted: the panel's width is clamped against it on every measure, so a drag stops at the point
 * where the chart would start paying.
 */
internal const val CHART_PLOT_SHARE = 0.65f

/**
 * The rail of panel glyphs at the end edge — **56 dp** since 4.72.0.
 *
 * Eight points wider than the tool rail, and deliberately: this one carries the *screens* (the
 * watchlist, the ladder, the editor), it is pressed less often, and a rail that is the same width
 * as the tool rail at the other edge reads as a mirror of it rather than as a different thing.
 */
internal val CHART_SIDE_RAIL_WIDTH = 56.dp

/** Whether a window this wide has room for the tools, the plot, a docked panel and the rail. */
internal fun sidePanelsFit(widthDp: Float): Boolean =
    widthDp >= (CHART_TOOL_RAIL + CHART_MIN_PLOT_WIDTH + CHART_SIDE_PANEL_MIN + CHART_SIDE_RAIL_WIDTH).value

/**
 * How wide a docked panel may actually be, given the window and the owner's plot share.
 *
 * Three numbers argue and the smallest wins: what the reader dragged it to, the panel's own
 * maximum, and whatever is left after the plot has taken [CHART_PLOT_SHARE] of the room the two
 * rails do not use. The floor is [CHART_SIDE_PANEL_MIN] — below it a ladder and an editor line stop
 * being readable, at which point the panel is not narrow, it is broken.
 */
internal fun panelWidthFor(windowDp: Float, draggedDp: Float): Dp {
    val betweenRails = windowDp - (CHART_TOOL_RAIL + CHART_SIDE_RAIL_WIDTH).value
    val spare = betweenRails * (1f - CHART_PLOT_SHARE)
    val allowed = minOf(draggedDp, CHART_SIDE_PANEL_MAX.value, spare)
    return maxOf(allowed, CHART_SIDE_PANEL_MIN.value).dp
}

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
    /** The panel open when the host first composes — a render's, or a restored workspace's. */
    initialOpenId: String? = null,
    main: @Composable (Modifier) -> Unit,
) {
    var openId by rememberSaveable { mutableStateOf(initialOpenId) }
    val open = panels.firstOrNull { it.id == openId }
    // What the reader dragged the panel to, in points, before the budget has its say. Remembered
    // across a rotation because a width is a preference, not a transient.
    var dragged by rememberSaveable { mutableFloatStateOf(CHART_SIDE_PANEL_WIDTH.value) }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val panelWidth = panelWidthFor(windowDp = maxWidth.value, draggedDp = dragged)
    val density = LocalDensity.current
    // Which way widens the panel. In Persian the whole workbench is mirrored: the panel sits at the
    // *left* of the glass with its grip on the right, so a drag to the right makes it wider — the
    // opposite of what the same gesture means in English. The edge moves with the finger either
    // way, which is the only rule a reader has for a grip.
    val widen = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
    Row(modifier = Modifier.fillMaxSize()) {
        val partitions = if (open != null) 2 else 1
        val directive = PaneScaffoldDirective(
            maxHorizontalPartitions = partitions,
            horizontalPartitionSpacerSize = 0.dp,
            maxVerticalPartitions = 1,
            verticalPartitionSpacerSize = 0.dp,
            defaultPanePreferredWidth = panelWidth,
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
                AnimatedPane(modifier = Modifier.preferredWidth(panelWidth)) {
                    if (open != null) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            // The divider is the grip. Dragging it towards the plot widens the
                            // panel and away from it narrows it — the direction the edge moves —
                            // and `panelWidthFor` decides what the drag is allowed to mean.
                            VerticalDivider(
                                color = CoineProColors.Border,
                                modifier = Modifier
                                    .width(PANEL_GRIP)
                                    .draggable(
                                        orientation = Orientation.Horizontal,
                                        state = rememberDraggableState { delta ->
                                            dragged = (dragged + widen * delta / density.density)
                                                .coerceIn(CHART_SIDE_PANEL_MIN.value, CHART_SIDE_PANEL_MAX.value)
                                        },
                                    )
                                    .semantics { contentDescription = "side-panel-grip" },
                            )
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
}

/** The grip's own width. Wider than a hairline so a thumb can find it; still reads as a divider. */
private val PANEL_GRIP = 6.dp

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
