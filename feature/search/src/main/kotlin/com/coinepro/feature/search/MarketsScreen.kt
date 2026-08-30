package com.coinepro.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.watchlistsync.WatchlistSyncController
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPercentPill
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPullToRefresh
import com.coinepro.core.designsystem.CoineProSegmentTabs
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProSparkline
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.designsystem.resolve
import com.coinepro.core.designsystem.CoineProTeachingStrip
import com.coinepro.core.designsystem.TeachingSurface
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.marketdata.MarketTicker
import com.coinepro.core.marketdata.MarketTickerStore
import com.coinepro.core.marketdata.SparklineStore
import com.coinepro.core.symbols.MarketHours
import com.coinepro.core.symbols.SymbolCategory
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The markets tab, in the dense «ترمینال» language.
 *
 * The owner picked this direction for every screen that is a *list*, and the reasoning holds: a
 * market list is read by scanning down a column, so the job of the layout is to put the same three
 * things in the same three places on every row and get out of the way. Nothing here is decorated.
 *
 * The row carries a **shape as well as a number**, and that is the one addition over what the app
 * had. A price says where the market is; the line beside it says how it got there, which is the
 * question a reader is actually asking when they scan a list. Without it the screen is a
 * spreadsheet and there is no reason to linger on it.
 *
 * Search is a *separate destination* rather than a field at the top. A field here would occupy the
 * first row of every visit for something people do on a minority of them — and the search screen
 * that already exists ranks, highlights and remembers in a way an inline filter cannot.
 *
 * The «دیده‌بان» segment is [WatchlistPanel] rather than a fourth filter over the same list, and
 * that is the one structural change here. A watchlist is not a category: it has its own order, its
 * own colour flags, its own columns and several of itself. It shares this screen's row so the two
 * cannot look like two apps — see [MarketListRow].
 *
 * **The tabs are two axes, not one.** The category strip says what kind of thing a row is; the
 * lens strip under it — «داغ», «بیشترین رشد», «بیشترین افت» — says where something is happening,
 * which is the question a reader actually opens this screen with and the one four hundred markets
 * in catalogue order cannot answer. They compose, so «کریپتو» plus «بیشترین افت» is a list neither
 * strip could produce alone. See [MarketLens] and [arrangeMarkets].
 *
 * That second strip is **absent, not empty, where it cannot be filled.** It is drawn from
 * [MarketTickerStore], and CoinePro-FX has no such route at all — so on that platform the lens
 * strip and the sortable headings simply are not there. A tab that can never fill is worse than no
 * tab: it teaches the reader that the app is broken rather than that the data is elsewhere.
 *
 * **Holding a row opens [MarketPreviewSheet]** rather than the chart. The chart is a route, a
 * candle request and a layout; the question a reader scanning this list is actually asking is what
 * one row is doing, and the answer — price, move, the day's shape — is already in memory on this
 * screen. On the connection this product is built for that is the difference between an answer and
 * a four-second wait somebody abandons.
 */
@Composable
fun MarketsScreen(
    controller: MarketSearchController,
    sparklines: SparklineStore,
    onOpenSymbol: (String) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    watchlist: List<String> = emptyList(),
    /**
     * Starring, from the screen that owns the watchlist tab.
     *
     * It had a «دیده‌بان» tab and no way to put anything in it: the star existed on the search
     * screen and on Home, and Home lists only what the platform quotes — two instruments on the
     * forex side. So a reader on CoinePro-FX could star two markets, or go three levels deep into
     * search, to fill a tab that is one tap away.
     */
    onToggleWatch: ((String) -> Unit)? = null,
    /**
     * The lists themselves.
     *
     * Optional, and the tab degrades to a plain filter over [watchlist] without it, because the
     * guest build reaches this screen with no preferences file of its own to write into. Where it
     * is supplied the segment becomes the full panel: several lists, flags, columns, import and
     * export.
     */
    watchlistStore: WatchlistStore? = null,
    /**
     * Sync, where the platform serves it.
     *
     * Optional and null on the guest build, which has no account to sync against. Null draws
     * nothing at all rather than a disabled control — a control the reader cannot use is an
     * advertisement for something they do not have.
     */
    watchlistSync: WatchlistSyncController? = null,
    /**
     * The day's open, high, low, change and turnover for the whole catalogue.
     *
     * Optional, and null is a first-class answer rather than a gap: the guest shell has no such
     * store, and CoinePro-FX has no route behind one. Where it is absent — or present but
     * [MarketTickerStore.supported] is false — this screen is exactly the screen it was before,
     * with the category tabs and no second axis.
     *
     * It is the store rather than a table because the store polls at the interval the server asks
     * for and is reference counted, so the heat map or a sheet reading the same figures does not
     * open a second request for them.
     */
    tickers: MarketTickerStore? = null,
    /** The open signals strip at the foot. Null on a build with nothing to link to. */
    openSignals: MarketsSignalStrip? = null,
    /**
     * Arms an alert on a symbol at the price the preview is showing.
     *
     * The **price comes from here** rather than being looked up again by the caller. This screen's
     * quote is the catalogue's where the live socket is not carrying the symbol, and the shell's
     * live map is not — so a caller reading its own feed would find nothing for most of the list
     * and the button would silently do nothing.
     *
     * Null drops the action rather than disabling it, for the reason every other nullable callback
     * on this screen is nullable: a button that answers a press with nothing is worse than no
     * button, and only the caller knows whether there is a composer to open.
     */
    onCreateAlert: ((String, Double) -> Unit)? = null,
) {
    LaunchedEffect(controller) { controller.start() }
    // Reference counted in the store, so leaving this screen does not stop the poll for whatever
    // else is reading the same table — and coming back does not start a second one.
    DisposableEffect(tickers) {
        tickers?.start()
        onDispose { tickers?.stop() }
    }
    val state by controller.state.collectAsStateWithLifecycle()
    val lines by sparklines.lines.collectAsStateWithLifecycle()
    // A flow either way, so the collection below is unconditional. A `tickers?.state?.collect…`
    // would add and remove a subscription as the store appears, which is a composition that
    // changes shape for a reason that has nothing to do with what is on screen.
    val tickerFlow = remember(tickers) {
        tickers?.state ?: MutableStateFlow(MarketTickerStore.MarketTickerState())
    }
    val tickerState by tickerFlow.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(MarketsTab.ALL) }
    // Held as the two primitives rather than as a `MarketSort?`, because `rememberSaveable` can put
    // an enum and a boolean into a Bundle on its own and cannot put a data class there without a
    // Saver written for it. Enums survive process death; a hand-written Saver is a second place for
    // this to go wrong on a rotation.
    var lensChoice by rememberSaveable { mutableStateOf(MarketLens.NONE) }
    var sortKey by rememberSaveable { mutableStateOf<MarketSortKey?>(null) }
    var sortDescending by rememberSaveable { mutableStateOf(true) }
    // The whole second axis, gated on the platform in one place.
    //
    // Not `tickers != null`: a store built over `UnsupportedMarketTickerGateway` exists, answers,
    // and answers with nothing forever. Reading `supported` here is what keeps the lens strip off
    // CoinePro-FX rather than putting four tabs there that can only ever be empty. It also
    // *neutralises a restored choice*, which is the case a rotation cannot produce but a platform
    // switch can: a reader who left «داغ» selected on TradeYar comes back to the plain list rather
    // than to a saved state the new platform cannot honour.
    val arranged = tickers?.supported == true
    val lens = if (arranged) lensChoice else MarketLens.NONE
    val sort = if (arranged) sortKey?.let { MarketSort(it, sortDescending) } else null
    // The symbol, not the row: the row is looked up again from the live results on every frame, so
    // the price inside the sheet ticks with the one in the list behind it instead of freezing at
    // whatever it was when the finger went down. Saveable, so a rotation does not close it.
    var preview by rememberSaveable { mutableStateOf<String?>(null) }

    // The category chip and the watchlist tab are different filters over one list, so they are
    // applied here rather than pushed into the controller: the controller's category is what the
    // *search* screen uses, and a tab that quietly rewrote it would change the other screen too.
    // Hoisted out of the filter block: the rows need it too, to draw each star's state.
    val watched = remember(watchlist) { watchlist.map { it.uppercase() }.toSet() }
    val rows = remember(state.results, tab, watched, tickerState, lens, sort) {
        // The category first, then the day's figures. The order matters for one reason that is not
        // about arithmetic: `state.results` is the catalogue, which `MarketCatalogGateway` has
        // already filtered through `SymbolArtwork.covers`, so arranging *these* rows can never
        // introduce a symbol with no artwork. Building the gainers list out of the ticker table
        // instead — eight hundred rows, filtered by nothing — would put lettered discs in a list
        // this app does not allow them in.
        val visible = state.results.filter { row ->
            when (tab) {
                MarketsTab.ALL -> true
                MarketsTab.CRYPTO -> row.meta.category == SymbolCategory.CRYPTO
                MarketsTab.FOREX -> row.meta.category == SymbolCategory.FOREX
                MarketsTab.METAL -> row.meta.category == SymbolCategory.METAL
                MarketsTab.WATCHLIST -> row.meta.symbol.uppercase() in watched
            }
        }
        arrangeMarkets(rows = visible, tickers = tickerState, lens = lens, sort = sort)
    }
    val panel = tab == MarketsTab.WATCHLIST && watchlistStore != null

    Column(modifier = modifier.fillMaxSize().background(CoineProColors.Stage)) {
        Header(onOpenSearch = onOpenSearch)
        CoineProTeachingStrip(TeachingSurface.MARKETS)
        // The shared strip. This screen had grown a byte-for-byte copy of it — same tray, same
        // raised block, same weights — which is one more place for the next change to be applied
        // once and forgotten once. It is also how this row ended up without the tick every other
        // control in the app answers a tap with.
        CoineProSegmentTabs(
            options = MarketsTab.entries.map { it to stringResource(it.labelRes) },
            selected = tab,
            onSelect = { tab = it },
        )
        // The second axis, and only where there is a route to fill it. It is deliberately below the
        // categories rather than merged into them: five Persian labels already fill that tray edge
        // to edge, and a lens is a different question from a category — one narrows what a row *is*
        // and the other narrows what it *did today*.
        if (arranged && !panel) {
            LensChips(
                selected = lensChoice,
                onSelect = { chosen ->
                    lensChoice = chosen
                    // The sort goes with it. A sort is a refinement of whatever list is on screen,
                    // and carrying «ارزش معاملات ↓» from the losers into «همهٔ بازارها» would answer
                    // a tap on a lens with a list ordered by something the reader chose for a
                    // different one.
                    sortKey = null
                },
            )
        }
        // The panel draws its own headings, over whichever columns the reader chose. Two heading
        // strips, one of them describing a layout that is not on screen, would be worse than none.
        if (!panel) {
            ColumnHeadings(
                starRail = onToggleWatch != null,
                sort = sort,
                // Null where there is nothing to sort by, which leaves the headings exactly the
                // three inert words they have always been on a platform without the route.
                onSort = if (!arranged) {
                    null
                } else {
                    { key ->
                        val next = nextMarketSort(sort, key)
                        sortKey = next?.key
                        sortDescending = next?.descending ?: true
                    }
                },
            )
        }

        when {
            panel -> WatchlistPanel(
                store = requireNotNull(watchlistStore),
                catalogue = state.results,
                lines = lines,
                onRequestLine = sparklines::request,
                onOpenSymbol = onOpenSymbol,
                watchlistSync = watchlistSync,
                modifier = Modifier.weight(1f),
            )
            state.loading && state.results.isEmpty() -> Centred {
                CircularProgressIndicator(color = CoineProColors.Gold, strokeWidth = 2.dp)
            }
            // A failure is not an empty search. The controller has set `state.error` on every
            // catalogue failure since it was written and this screen never read it, so a reader
            // whose request failed was told «موردی یافت نشد» — the empty-search copy — with no
            // error, no retry, and no pull target, because the pull lives in the branch below and
            // is unreachable while the list is empty. On one of five bottom-bar destinations.
            state.error != null && rows.isEmpty() -> Centred {
                CoineProEmptyState(
                    icon = CoineProIcons.Warning,
                    message = state.error?.resolve() ?: stringResource(R.string.search_failed),
                    action = stringResource(R.string.search_retry),
                    onAction = controller::refresh,
                )
            }
            // A lens with no table is not an empty market list. It is a screen waiting for — or
            // missing — the one request that defines it, and «موردی یافت نشد» there blames the
            // catalogue for the absence of the day's figures. The two states are told apart because
            // the store keeps its last table on a failure and reports the failure separately.
            (lens != MarketLens.NONE || sort != null) && tickerState.table.tickers.isEmpty() -> Centred {
                if (tickerState.failed) {
                    CoineProEmptyState(
                        icon = CoineProIcons.Warning,
                        message = stringResource(R.string.markets_figures_failed),
                        action = stringResource(R.string.search_retry),
                        onAction = { tickers?.refresh() },
                    )
                } else {
                    CircularProgressIndicator(color = CoineProColors.Gold, strokeWidth = 2.dp)
                }
            }
            rows.isEmpty() -> Centred {
                Text(
                    text = when {
                        tab == MarketsTab.WATCHLIST -> stringResource(R.string.markets_watchlist_empty)
                        // The table arrived and nothing in it qualifies — a real answer, and a
                        // different one from "no market matches that name".
                        lens != MarketLens.NONE -> stringResource(R.string.markets_lens_empty)
                        else -> stringResource(R.string.search_empty)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextMuted,
                )
            }
            else -> CoineProPullToRefresh(
                refreshing = state.loading,
                onRefresh = controller::refresh,
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = CoineProSpacing.One),
                ) {
                    items(rows, key = { it.meta.symbol }) { row ->
                        // Asked for as the row appears, not for the whole catalogue up front — a
                        // thousand markets would be a thousand requests nobody looked at.
                        LaunchedEffect(row.meta.symbol) { sparklines.request(row.meta.symbol) }
                        MarketListRow(
                            row = row,
                            onClick = { onOpenSymbol(row.meta.symbol) },
                            starred = onToggleWatch?.let { row.meta.symbol.uppercase() in watched },
                            onToggleStar = onToggleWatch?.let { toggle ->
                                { toggle(row.meta.symbol) }
                            },
                            onLongClick = { preview = row.meta.symbol },
                            trailing = {
                                MarketFigures(
                                    row = row,
                                    ticker = tickerState.tickerFor(row),
                                    line = lines[row.meta.symbol.uppercase()].orEmpty(),
                                    turnoverColumn = sort?.key == MarketSortKey.TURNOVER,
                                )
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = CoineProSpacing.Two),
                            thickness = 1.dp,
                            color = CoineProColors.BorderSubtle,
                        )
                    }
                }
            }
        }

        openSignals?.let { SignalStrip(it) }
    }

    // Read from `rows` rather than from `state.results`, so a preview cannot outlive the tab it was
    // opened from: switching to a filter that excludes the symbol closes the sheet instead of
    // leaving a market on screen that the list behind it no longer holds.
    preview
        ?.let { symbol -> rows.firstOrNull { it.meta.symbol == symbol } }
        ?.let { row ->
            MarketPreviewSheet(
                state = previewOf(
                    row = row,
                    // Whatever the store already has. Nothing here asks for a line — the rows
                    // above did that as they scrolled past, which is the only reason this sheet
                    // costs no network at all.
                    line = lines[row.meta.symbol.uppercase()].orEmpty(),
                    starred = row.meta.symbol.uppercase() in watched,
                    status = MarketHours.statusOf(row.meta),
                    // The same source the row behind the sheet drew its pill from.
                    changePercent = tickerState.tickerFor(row)?.changePercent24h
                        ?: row.quote?.changePercent,
                ),
                onDismiss = { preview = null },
                onOpenChart = {
                    preview = null
                    onOpenSymbol(row.meta.symbol)
                },
                onToggleStar = onToggleWatch?.let { toggle -> { toggle(row.meta.symbol) } },
                // Two conditions, and the second is the row's own: an alert needs a level to fire
                // at, and a market this feed has not quoted has none. The action is dropped rather
                // than opened onto an empty field.
                onCreateAlert = onCreateAlert?.let { arm ->
                    row.quote?.price?.let { price ->
                        {
                            preview = null
                            arm(row.meta.symbol, price)
                        }
                    }
                },
            )
        }
}

/** The open-signal line at the foot of the list. */
data class MarketsSignalStrip(val count: Int, val summary: String, val onClick: () -> Unit)

private enum class MarketsTab(val labelRes: Int) {
    ALL(R.string.search_category_all),
    CRYPTO(R.string.search_category_crypto),
    FOREX(R.string.search_category_forex),
    METAL(R.string.search_category_metal),
    WATCHLIST(R.string.markets_watchlist),
}

@Composable
private fun Header(onOpenSearch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CoineProSpacing.Two, end = CoineProSpacing.Two, top = CoineProSpacing.OneHalf, bottom = CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.markets_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
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

/**
 * The lens strip: where something is happening today.
 *
 * Pills rather than a second [CoineProSegmentTabs] tray, and that is a legibility decision rather
 * than a decorative one. Two filled trays stacked read as two equal filters over one list, and
 * these are not equal — the categories are the primary axis and always have been, and the lens
 * refines whichever of them is open. A row of pills under a tray says that; two trays do not.
 *
 * It scrolls because «همهٔ بازارها» and «بیشترین رشد» together are wider than a 360dp phone, and a
 * row of four pills that clipped its last one would hide the losers.
 */
@Composable
private fun LensChips(selected: MarketLens, onSelect: (MarketLens) -> Unit) {
    val haptics = rememberCoineProHaptics()
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        // Two of gutter at each end, so the first pill sits under the first tab above it and the
        // last one can clear the edge instead of looking cut off.
        contentPadding = PaddingValues(
            start = CoineProSpacing.Two,
            end = CoineProSpacing.Two,
            top = CoineProSpacing.One,
        ),
    ) {
        items(MarketLens.entries) { lens ->
            val active = lens == selected
            Text(
                text = stringResource(lens.labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = if (active) CoineProColors.OnAccent else CoineProColors.TextSecondary,
                maxLines = 1,
                modifier = Modifier
                    .clickable {
                        // Only a change is worth a tick, which is the rule the tab strip above
                        // states: a buzz for pressing the pill you are already on teaches the
                        // reader to distrust the ones that mean something.
                        if (!active) haptics.select()
                        onSelect(lens)
                    }
                    .background(
                        color = if (active) CoineProColors.Accent else Color.Transparent,
                        shape = CoineProPillShape,
                    )
                    .border(1.dp, CoineProColors.Border, CoineProPillShape)
                    .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.Half),
            )
        }
    }
}

/**
 * What each column is, and — where the day's table exists — the control that orders by it.
 *
 * Three words, once, above a list whose rows never change shape. Without it the line in the middle
 * of the row is a decoration; with it, it is a twenty-four-hour trend and the reader knows to read
 * it as one.
 *
 * Two of the three headings are also the sort. The middle one is the awkward case and is worth
 * saying out loud: the column it labels draws a *sparkline*, and turnover is a figure, so tapping
 * it swaps the column to the figure and the heading to «ارزش معاملات» in the same gesture. The rule
 * that keeps this honest is that **the heading always names what is actually drawn below it** — a
 * heading reading «روند ۲۴ ساعت» over a column of numbers would be the one failure this strip
 * exists to prevent. The alternative, a fourth heading for turnover, does not fit: the row is a
 * logo, a ninety-six point ticker column, a weighted middle and a ninety-two point price column,
 * and on a 360dp phone there is nothing left to give.
 *
 * A sortable heading is [CoineProColors.TextSecondary] rather than muted, so the two that respond
 * to a tap are distinguishable from «نماد», which does not.
 */
@Composable
private fun ColumnHeadings(
    starRail: Boolean,
    sort: MarketSort? = null,
    /** Null where this platform serves no day's table, which leaves all three headings inert. */
    onSort: ((MarketSortKey) -> Unit)? = null,
) {
    // Laid out as the row it labels, not as three words spread edge to edge.
    //
    // Under `SpaceBetween` «نماد» sat against the reading edge — a whole logo, and sometimes a
    // star, to the right of the ticker it names — «روند» landed wherever its two neighbours left
    // it rather than over the sparkline, and only «قیمت» happened to be correct. A heading that is
    // not above its column tells the reader the list is arranged in a way it is not.
    //
    // Every measurement here is `MarketListRow`'s: the same 16 of horizontal padding, the same 12
    // between elements, the same 30dp logo and the same [SymbolColumn]. The star is optional in
    // the row, so it is optional here too, and it reserves 48 because that is what
    // `minimumInteractiveComponentSize` gives it.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Two)
            .padding(top = CoineProSpacing.OneHalf, bottom = CoineProSpacing.Half),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val style = MaterialTheme.typography.labelSmall
        if (starRail) Spacer(modifier = Modifier.width(48.dp))
        Spacer(modifier = Modifier.width(30.dp))
        Text(
            text = stringResource(R.string.markets_column_symbol),
            style = style,
            color = CoineProColors.TextMuted,
            maxLines = 1,
            modifier = Modifier.width(SymbolColumn),
        )
        val turnoverColumn = sort?.key == MarketSortKey.TURNOVER
        Text(
            text = stringResource(
                if (turnoverColumn) R.string.markets_column_turnover else R.string.markets_column_trend,
            ) + sortMark(sort, MarketSortKey.TURNOVER),
            style = style,
            color = headingInk(sort, MarketSortKey.TURNOVER, onSort),
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .then(onSort?.let { Modifier.clickable { it(MarketSortKey.TURNOVER) } } ?: Modifier),
        )
        Text(
            text = stringResource(R.string.markets_column_price) + sortMark(sort, MarketSortKey.CHANGE),
            style = style,
            color = headingInk(sort, MarketSortKey.CHANGE, onSort),
            maxLines = 1,
            modifier = onSort?.let { Modifier.clickable { it(MarketSortKey.CHANGE) } } ?: Modifier,
        )
    }
}

/**
 * The arrow after a heading, or nothing.
 *
 * The same two glyphs `WatchlistColumnHeading` uses, deliberately: they are the only arrows in this
 * app that have been seen rendered in IRANYekanX, and a heading whose sort marker came out as a
 * missing-glyph box would be worse than a heading with no marker at all.
 */
private fun sortMark(sort: MarketSort?, key: MarketSortKey): String = when {
    sort?.key != key -> ""
    sort.descending -> " ↓"
    else -> " ↑"
}

/** Muted where a tap does nothing, secondary where it sorts, primary where it already has. */
@Composable
private fun headingInk(
    sort: MarketSort?,
    key: MarketSortKey,
    onSort: ((MarketSortKey) -> Unit)?,
) = when {
    onSort == null -> CoineProColors.TextMuted
    sort?.key == key -> CoineProColors.TextPrimary
    else -> CoineProColors.TextSecondary
}

/**
 * The trailing block of a markets row: the day's shape, then the price and the move.
 *
 * Split out of the row itself because it is the one part the watchlist replaces — that list puts
 * the reader's chosen columns here instead. Everything before it is fixed in [MarketListRow], so
 * the two can never drift apart in the ways three copies of a market row already have once.
 *
 * The move comes from the day's table first and from the quote only as a fallback, which is the
 * right way round rather than the defensive one: `MarketQuote.changePercent` has been null on every
 * quote either backend has ever returned — the snapshot carries a symbol, a price, a bid, an ask
 * and a time — so this pill has never once been drawn from it. The fallback stays because a
 * platform with no ticker route still has whatever its own quote said, and because the day the
 * snapshot carries a move, nothing here needs changing.
 */
@Composable
private fun RowScope.MarketFigures(
    row: MarketSearchRow,
    ticker: MarketTicker?,
    line: List<Double>,
    /**
     * Draw the turnover in the middle column instead of the trend.
     *
     * True only while the reader is sorting by it. A list ordered by a figure it does not show is a
     * list in an order nobody can check — and the alternative, a permanent turnover column, would
     * cost the sparkline, which is the one thing on this screen that says *how* a market got where
     * it is rather than where it ended up.
     */
    turnoverColumn: Boolean,
) {
    val change = ticker?.changePercent24h ?: row.quote?.changePercent
    val tone = when {
        change == null -> CoineProColors.TextMuted
        change >= 0.0 -> CoineProColors.Buy
        else -> CoineProColors.Sell
    }
    if (turnoverColumn) {
        Text(
            // The compact form the watchlist's volume columns use — `918.44M` rather than
            // `918,442,310.05`, which no column in this row is wide enough to hold. A dash, never a
            // zero: the table omits a turnover it does not know, and `0` would be a claim that
            // nothing traded.
            text = ticker?.turnover24h?.let { compactAmount(it) }
                ?: stringResource(R.string.search_no_price),
            style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
            color = if (ticker?.turnover24h == null) CoineProColors.TextDisabled else CoineProColors.TextSecondary,
            // Right, never End. The locale is Persian and the figure is Latin; an End alignment
            // would throw this column to the far side of its own box, away from its heading.
            textAlign = TextAlign.Right,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    } else {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            CoineProSparkline(
                values = line,
                modifier = Modifier.width(58.dp).height(24.dp),
                colour = tone,
            )
        }
    }
    Column(
        // Fixed and end-aligned, so the decimal points line up down the column. Free-width,
        // `1.08` and `91,248.30` both started at the same x and ended 36dp apart — a column of
        // prices nothing could be compared across, which is most of what a market list is for.
        modifier = Modifier.width(FIGURE_COLUMN),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = row.quote?.let { MarketNumberFormatter.priceAuto(it.price) }
                ?: stringResource(R.string.search_no_price),
            style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
            color = CoineProColors.TextPrimary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Right,
            maxLines = 1,
        )
        // The shared pill, not a local one. This row had grown its own — a flat alpha over the
        // move's colour rather than the tint formula the rest of the app computes against the
        // surface behind it — so the same percentage was a slightly different green here than
        // on Home, on a screen a reader reaches from Home.
        change?.let {
            CoineProPercentPill(
                percent = it,
                modifier = Modifier.padding(top = 2.dp),
                background = CoineProColors.Stage,
            )
        }
    }
}

@Composable
private fun SignalStrip(strip: MarketsSignalStrip) {
    Row(
        modifier = Modifier
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.One)
            .fillMaxWidth()
            .clip(CoineProShapes.small)
            .background(CoineProColors.Surface)
            .clickable(onClick = strip.onClick)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.nav_signals_fill),
            contentDescription = null,
            tint = CoineProColors.Gold,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.markets_open_signals, strip.count),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = strip.summary,
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            // Forward in the reading direction, which is what this row offers. The left caret was
            // here, and it is auto-mirrored — so in Persian it turned round and pointed *back*,
            // away from the screen it opens, on the one glyph whose whole job is to say which way.
            painter = painterResource(CoineProIcons.ChevronForward),
            contentDescription = null,
            tint = CoineProColors.TextMuted,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun ColumnScope.Centred(content: @Composable () -> Unit) {
    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
}

/** The price column's width, matching `CoineProMarketRow` so the two lists align the same way. */
private val FIGURE_COLUMN = 92.dp
