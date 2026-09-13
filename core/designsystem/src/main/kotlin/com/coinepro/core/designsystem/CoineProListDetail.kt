package com.coinepro.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.calculateThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.defaultDragHandleSemantics
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The two widths that decide whether a list and a detail can share a screen.
 *
 * Both are measurements of real content rather than round numbers, which is why they are here with
 * their reasons rather than inline at four call sites.
 */
object CoineProPaneDefaults {

    /**
     * How wide the list pane is when there are two.
     *
     * A market row is an instrument logo, a symbol, a price, a signed change and a sparkline, and
     * at the shipping type scale that is about 340dp before it starts eliding the symbol. 360dp is
     * that plus the gutter, and it is fixed rather than a fraction on purpose: a list pane that
     * grows with the window ends up 500dp wide on a large tablet, holding the same five columns
     * with white space poured between them, while the chart beside it — the thing the extra width
     * would actually have been worth something to — gets nothing.
     */
    val LIST_WIDTH: Dp = 360.dp

    /**
     * The least the detail pane may be given before the split is refused.
     *
     * 480dp is what a chart needs to be a chart: about 400dp of plot after the price gutter, which
     * is a hundred and twenty candles at a legible spacing. Below it the reader has a list and a
     * smear, which is worse than the list and a full-width chart one tap away — so the split is
     * declined rather than delivered badly.
     *
     * [LIST_WIDTH] plus this is 840dp, which is [CoineProWindowClass.EXPANDED_WIDTH_DP] exactly.
     * That is not a coincidence and it is not circular either: the two were measured from content
     * and they land on the published breakpoint, which is the check that the breakpoint means
     * something here rather than being borrowed.
     */
    val MIN_DETAIL_WIDTH: Dp = 480.dp

    /** The draggable divider between the panes: wide enough to grab, narrow enough to read as a line. */
    val DIVIDER_WIDTH: Dp = 12.dp
}

/**
 * A list beside what one of its rows opens — where there is room, and only there.
 *
 * ### The problem it solves
 *
 * Markets → chart, news → article, signals → detail, screener → chart: on a phone each of those is
 * a navigation, and it has to be, because a phone has one screenful. On a tablet the same
 * navigation throws away a list that was occupying a third of the glass in order to show a detail
 * that does not need the other two thirds — so a reader comparing four instruments pays a push, a
 * read, and a pop, four times, on a device that could have shown them all along.
 *
 * ### Why it measures instead of asking the window
 *
 * `BoxWithConstraints`, on the space **this layout was actually given**. The window is the wrong
 * ruler: the navigation rail has already taken [CoineProRailWidth.ICON] or
 * [CoineProRailWidth.LABELLED] off the front of it, and on the smallest expanded window that is the
 * difference between a 480dp detail pane and a 400dp one. A layout that asked
 * [LocalCoineProWindowClass] would split at exactly the width where the split no longer fits.
 *
 * ### What the caller has to do differently
 *
 * [list] is handed `twoPane`, and that is the whole contract: **false means a row tap is still a
 * navigation** — nothing about the phone changes — and **true means a row tap sets selection**, and
 * the caller passes the resulting detail back in as [detail]. Nothing here navigates, holds
 * selection, or knows what a row is; a scaffold that did any of those would have to know about the
 * back stack, and then every screen would inherit whichever back behaviour it happened to pick.
 *
 * ### Right-to-left
 *
 * The list is drawn first in the `Row`, so it takes the **start** edge — the right in Persian,
 * which is where a reader's eye begins and therefore where the thing being chosen from belongs.
 * Nothing in this file names left or right; the divider is a sibling between the two panes and
 * lands correctly in both directions for the same reason.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun CoineProListDetail(
    modifier: Modifier = Modifier,
    /** See [CoineProPaneDefaults.LIST_WIDTH]. Widened only by a caller whose rows are genuinely wider. */
    listWidth: Dp = CoineProPaneDefaults.LIST_WIDTH,
    /** See [CoineProPaneDefaults.MIN_DETAIL_WIDTH]. Below this the split is declined. */
    minDetailWidth: Dp = CoineProPaneDefaults.MIN_DETAIL_WIDTH,
    /**
     * What fills the detail pane before anything is chosen.
     *
     * A real sentence by default rather than an empty rectangle. Half of a tablet screen left blank
     * reads as a screen that failed to load, and a reader who thinks that taps around looking for
     * the part that is missing.
     */
    empty: @Composable () -> Unit = { CoineProDetailPlaceholder() },
    /** The chosen row's screen, or null while nothing is chosen. Drawn only when there are two panes. */
    detail: (@Composable () -> Unit)? = null,
    /**
     * The list. Told whether it is sharing the screen, because that changes what a row tap means —
     * see the note above.
     */
    list: @Composable (twoPane: Boolean) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val twoPane = maxWidth >= listWidth + minDetailWidth
        if (!twoPane) {
            list(false)
            return@BoxWithConstraints
        }
        // Material's list-detail scaffold, told the answer rather than asked for it: the
        // directive is built from the width *this* composable measured, which is the window less
        // the rail — see the class KDoc for why the window's own class would put two panes where
        // one fits. Two partitions, and the reader can drag the divider between them.
        val directive = PaneScaffoldDirective(
            maxHorizontalPartitions = 2,
            horizontalPartitionSpacerSize = CoineProPaneDefaults.DIVIDER_WIDTH,
            maxVerticalPartitions = 1,
            verticalPartitionSpacerSize = 0.dp,
            defaultPanePreferredWidth = listWidth,
            excludedBounds = emptyList(),
        )
        val value = calculateThreePaneScaffoldValue(
            maxHorizontalPartitions = 2,
            adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
            currentDestination = ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List, null),
        )
        val expansion = rememberPaneExpansionState(
            keyProvider = value,
            anchors = listOf(
                PaneExpansionAnchor.Offset.fromStart(listWidth),
                PaneExpansionAnchor.Proportion(0.5f),
            ),
        )
        ListDetailPaneScaffold(
            directive = directive,
            value = value,
            listPane = {
                AnimatedPane(modifier = Modifier.preferredWidth(listWidth)) { list(true) }
            },
            detailPane = {
                AnimatedPane {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(CoineProColors.Stage),
                    ) {
                        if (detail != null) detail() else empty()
                    }
                }
            },
            paneExpansionState = expansion,
            paneExpansionDragHandle = { state -> CoineProPaneDragHandle(state) },
        )
    }
}

/**
 * The divider between the two panes, and the handle that moves it.
 *
 * A hairline in the design system's border colour, as the old fixed divider was, with Material's
 * pill in the middle so the affordance is visible; the whole spacer width is draggable. Snaps to
 * the list's default width or to half the screen — the two widths anybody actually wants.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ThreePaneScaffoldScope.CoineProPaneDragHandle(state: PaneExpansionState) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(CoineProPaneDefaults.DIVIDER_WIDTH)
            .paneExpansionDraggable(
                state = state,
                minTouchTargetSize = LocalMinimumInteractiveComponentSize.current,
                interactionSource = interaction,
                semanticsProperties = state.defaultDragHandleSemantics(),
            ),
        contentAlignment = Alignment.Center,
    ) {
        VerticalDivider(color = CoineProColors.Border)
        VerticalDragHandle(interactionSource = interaction)
    }
}

/**
 * The detail pane with nothing chosen.
 *
 * Deliberately a sentence and no glyph. The empty states elsewhere in the app carry the screen's
 * own mark because they are the whole screen; this one is half of a screen whose other half is a
 * populated list, and a large mark beside a list of live prices reads as an error next to working
 * content.
 */
@Composable
fun CoineProDetailPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(CoineProSpacing.Three),
        contentAlignment = Alignment.Center,
    ) {
        // The pane's mark is the chart's, because the detail pane of every list-detail layout in
        // this app holds a chart. A pane with a sentence and nothing else reads, on a tablet, as
        // half a screen that failed to draw.
        CoineProEmptyState(
            icon = CoineProIcons.Chart,
            message = stringResource(R.string.pane_detail_empty),
        )
    }
}
