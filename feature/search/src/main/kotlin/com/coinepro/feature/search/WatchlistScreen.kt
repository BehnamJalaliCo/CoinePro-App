package com.coinepro.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.ProChartMarkStream
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
) {
    LaunchedEffect(controller) { controller.start() }
    val state by controller.state.collectAsStateWithLifecycle()
    val lines by sparklines.lines.collectAsStateWithLifecycle()

    // What makes the mark at the head of this page draw itself again.
    //
    // Bumped when the screen is arrived at and when the catalogue lands under it, which are the two
    // moments TradingView's own mark streams in: opening the tab, and the list appearing. It is a
    // *counter* rather than a scroll offset on purpose — see `ProChartMarkStream`: an animation
    // restarted on every pixel of a drag is a flicker, not a signature.
    var streamKey by rememberSaveable { mutableIntStateOf(0) }
    val settled = !state.loading && state.results.isNotEmpty()
    LaunchedEffect(settled) { if (settled) streamKey += 1 }

    Column(modifier = modifier.fillMaxSize().background(CoineProColors.Stage)) {
        WatchlistHeader(onOpenSearch = onOpenSearch, streamKey = streamKey)
        when {
            // The panel draws every row from the catalogue, so before it arrives there is nothing
            // to draw — not even an empty list, which would say «این فهرست خالی است» about a list
            // that may be full.
            state.loading && state.results.isEmpty() -> WatchlistStateBlock {
                CircularProgressIndicator(color = CoineProColors.Gold, strokeWidth = 2.dp)
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
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The heading: the title on the reading edge, the brand in the middle, the search in the corner.
 *
 * The magnifier is where the markets tab keeps its own, because this screen and that one are the
 * same list seen twice and a reader who has learned where it is should find it in the same place.
 *
 * The mark in the middle is TradingView's arrangement and the owner's instruction — their watchlist
 * carries the brand at the top of the page and streams it in on arrival. `ProChartMarkStream` is
 * the motion; [streamKey] is what says when to run it.
 */
@Composable
private fun WatchlistHeader(onOpenSearch: (() -> Unit)?, streamKey: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = CoineProSpacing.Two,
                end = CoineProSpacing.Two,
                top = CoineProSpacing.OneHalf,
                bottom = CoineProSpacing.One,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.markets_watchlist),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        ProChartMarkStream(replay = streamKey, contentDescription = null)
        Spacer(Modifier.weight(1f))
        if (onOpenSearch != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CoineProShapes.small)
                    .background(CoineProColors.SurfaceElevated)
                    .clickable(onClick = onOpenSearch),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(DesignR.drawable.icon_magnifying_glass),
                    contentDescription = stringResource(R.string.search_title),
                    tint = CoineProColors.TextSecondary,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

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
