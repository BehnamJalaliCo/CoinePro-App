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
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(listWidth).fillMaxHeight()) { list(true) }
            VerticalDivider(color = CoineProColors.Border)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    // The stage, so the detail pane is the page and the list beside it is the
                    // chrome — rather than two panels of equal weight with a rule between them,
                    // which is what makes a desktop mail client look like a filing cabinet.
                    .background(CoineProColors.Stage),
            ) {
                if (detail != null) detail() else empty()
            }
        }
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
        CoineProEmptyState(message = stringResource(R.string.pane_detail_empty))
    }
}
