package com.coinepro.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.ProChartTapeStream
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.resolve
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.marketdata.SparklineStore
import com.coinepro.core.watchlistsync.WatchlistSyncController

/**
 * The reader's own lists, with an address of their own.
 *
 * ### Why this exists next to [WatchlistPanel]'s note, which says the opposite
 *
 * That note is right about what the panel is — a segment of the markets tab, sharing
 * [MarketListRow] with the list above it so the two can never drift — and it was wrong about one
 * thing only: that being a segment is enough. A trader's own list is the most-visited surface in
 * every app of this category, and ours had no address at all. Nothing could navigate to it, no
 * deep link could land on it, the app's own search returned «دیده‌بان» and sent the reader to the
 * markets tab hoping they would find the right segment, and a shell that wanted to put it in front
 * of somebody had nowhere to point.
 *
 * So this is a **wrapper and nothing else**. It owns no list, no column set, no sort and no
 * storage: it starts the catalogue, draws a heading, and hands the whole surface to the same panel
 * the markets tab draws. Two screens that looked alike would have to be kept alike by hand, which
 * is exactly what that note warned about — and is why this file is ninety lines rather than seven
 * hundred.
 *
 * ### It works for a guest
 *
 * There is nothing here that needs an account. `WatchlistStore` is the device's own preferences
 * file and the guest shell already carries one; [watchlistSync] is the only account-shaped thing on
 * the screen and it is nullable, so a guest gets the lists, the flags, the columns and the
 * import — everything except a sync there is no account to sync against.
 */
@Composable
fun WatchlistScreen(
    /** The platform's catalogue. Started here, so the screen can be opened cold. */
    controller: MarketSearchController,
    /** The reader's own lists, flags, columns and sort. The device's, not the server's. */
    store: WatchlistStore,
    /** The day's line for a row, asked for as that row appears. */
    sparklines: SparklineStore,
    onOpenSymbol: (String) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The way to put something new on the list.
     *
     * Null drops the control rather than disabling it: only the caller knows whether this build has
     * a search screen to open, and a button that answers a press with nothing is worse than none.
     */
    onOpenSearch: (() -> Unit)? = null,
    /** Sync, where the platform serves it. Null draws nothing — see [WatchlistPanel]. */
    watchlistSync: WatchlistSyncController? = null,
    /**
     * Starts a price alert on a symbol, from its row's menu.
     *
     * Null drops the row action rather than disabling it, for the same reason [onOpenSearch] does:
     * only the caller knows whether this build has an alert composer behind it.
     */
    onCreateAlert: ((String) -> Unit)? = null,
) {
    LaunchedEffect(controller) { controller.start() }
    val state by controller.state.collectAsStateWithLifecycle()
    val lines by sparklines.lines.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().background(CoineProColors.Stage)) {
        WatchlistHeader(onOpenSearch = onOpenSearch)
        when {
            // The panel draws every row from the catalogue, so before it arrives there is nothing
            // to draw — not even an empty list, which would say «این فهرست خالی است» about a list
            // that may be full.
            state.loading && state.results.isEmpty() -> WatchlistStateBlock {
                // **The loading mark, which is where the brand belongs on this page.**
                //
                // «لوگو پرو چارت بارگیری … باید در صفحه‌ی دیده‌بان باشه» — and it is, at the one
                // moment it costs nothing: while there is no list to be above. A spinner says work
                // is happening and nothing else; the tape says the same thing in the product's own
                // hand, and it is gone by the time the first row arrives. See `ProChartTapeStream`.
                ProChartTapeStream(replay = state.loading, contentDescription = null)
            }
            // A failure is not an empty watchlist, and this is the screen where confusing the two
            // costs the most: a reader whose catalogue request failed would otherwise be told that
            // the list they built themselves is empty.
            state.error != null && state.results.isEmpty() -> WatchlistStateBlock {
                CoineProEmptyState(
                    icon = CoineProIcons.Warning,
                    message = state.error?.resolve() ?: stringResource(R.string.search_failed),
                    action = stringResource(R.string.search_retry),
                    onAction = controller::refresh,
                )
            }
            else -> WatchlistPanel(
                store = store,
                catalogue = state.results,
                lines = lines,
                onRequestLine = sparklines::request,
                onOpenSymbol = onOpenSymbol,
                watchlistSync = watchlistSync,
                onCreateAlert = onCreateAlert,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The heading: the title on the reading edge, the search in the corner, and nothing between them.
 *
 * The magnifier is where the markets tab keeps its own, because this screen and that one are the
 * same list seen twice and a reader who has learned where it is should find it in the same place.
 *
 * ### The brand is not here any more
 *
 * It was: a mark and the name, written in by a price line, in the middle of this row. It is a good
 * piece of motion and it was in the wrong place. This page's fold is the product — a watchlist is
 * scanned, and every point above the first row is a row the reader cannot see — and the brand was
 * spending about a hundred points of width and, with the row's padding, a quarter of the chrome
 * above the table to tell somebody holding the app which app they are holding. The reference's
 * watchlist has a title, a control, and rows.
 *
 * The tape is not gone from this screen: it writes itself over the **loading** state, which is the
 * one moment on a watchlist when a signature costs nothing — there is no list for it to be above.
 */
@Composable
private fun WatchlistHeader(onOpenSearch: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = CoineProSpacing.Two,
                end = CoineProSpacing.Two,
                top = CoineProSpacing.One,
                bottom = CoineProSpacing.Half,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.markets_watchlist),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        // A bare glyph, not a plate (run F). A header action on a terminal screen is a 24dp icon
        // with a touch target around it — TradingView's, Binance's, and now this one's: the grey
        // square this used to sit on read as a button on a screen whose every other control is an
        // icon, and the plate is what the owner picked out of the shipped frame. The target is
        // still 44: what went away is the fill, not the reach.
        if (onOpenSearch != null) {
            Box(
                modifier = Modifier
                    // The reach is 44 and the *room* is 24: a header action that occupied its own
                    // target would push the first row down by ten points, and on this screen the
                    // fold is the product — `FoldMetricsTest` holds the chrome to a budget for
                    // exactly that reason. The same trick the chart legend's buttons use.
                    .headerTarget(footprint = HEADER_GLYPH, target = HEADER_TOUCH)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenSearch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(DesignR.drawable.icon_magnifying_glass),
                    contentDescription = stringResource(R.string.search_title),
                    tint = CoineProColors.TextSecondary,
                    modifier = Modifier.size(HEADER_GLYPH),
                )
            }
        }
    }
}

/**
 * A control that reaches further than the room it takes.
 *
 * The child is measured at [target] and the parent is told [footprint], so a thumb lands on 44
 * points of glass while the row is laid out around 24. Without it the only way to a legal target
 * is to spend the height, and on a list screen height above the fold is rows.
 */
private fun Modifier.headerTarget(footprint: Dp, target: Dp): Modifier = layout { measurable, _ ->
    val reach = target.roundToPx()
    val box = footprint.roundToPx()
    val placeable = measurable.measure(Constraints.fixed(reach, reach))
    layout(box, box) { placeable.place((box - reach) / 2, (box - reach) / 2) }
}

/** A header action's reach and its glyph — see [WatchlistHeader]. */
private val HEADER_TOUCH = 44.dp
private val HEADER_GLYPH = 24.dp

/**
 * What is left of the column, for the two states that are one sentence.
 *
 * Its own rather than the markets tab's `Centred`, which is private to that file. Copying six lines
 * is the cheaper of the two mistakes available here: the alternative is making a layout helper part
 * of this module's surface so two files can share a `Box`.
 */
@Composable
private fun ColumnScope.WatchlistStateBlock(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(CoineProSpacing.Two),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
