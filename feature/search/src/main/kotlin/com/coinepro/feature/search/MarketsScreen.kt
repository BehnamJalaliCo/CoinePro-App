package com.coinepro.feature.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.inEnglish
import com.coinepro.core.designsystem.CoineProSkeletonRows
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPercentText
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPullToRefresh
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProSparkline
import com.coinepro.core.designsystem.CoineProTeachingStrip
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.TeachingSurface
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.designsystem.resolve
import com.coinepro.core.designsystem.numeric
import com.coinepro.core.designsystem.rowMotion
import com.coinepro.core.marketdata.MarketPulse
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.marketdata.MarketTicker
import com.coinepro.core.marketdata.MarketTickerStore
import com.coinepro.core.marketdata.SparklineStore
import com.coinepro.core.symbols.MarketHours
import com.coinepro.core.symbols.SymbolUniverse
import com.coinepro.core.watchlistsync.WatchlistSyncController
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
 * **One strip of tabs, not two** (run ΤΦΥ, U5). This screen carried a category tray — «همه»,
 * «کریپتو», «فارکس», «فلزات», «دیده‌بان» — and a lens row under it, and a reader had to work out
 * that the two composed before either was useful. What ships is seven underlined tabs: «برتر»,
 * «پرطرفدار», «دیده‌بان», «برنده/بازنده», «حجم», «فارکس», «فلزات». Some of them are a family and
 * some are an ordering, and that is an implementation detail nobody has to learn. See [MarketsPage]
 * and [arrangeMarkets].
 *
 * A tab is **absent, not empty, where it cannot be filled.** The ordering tabs are drawn from
 * [MarketTickerStore], and CoinePro-FX has no such route at all — so on that platform they, and the
 * sortable headings, simply are not there; the family tabs are offered only where the catalogue
 * holds that family. A tab that can never fill is worse than no tab: it teaches the reader that the
 * app is broken rather than that the data is elsewhere. See [offeredPages].
 *
 * Above the tabs sit the day's four figures ([MarketPulseRow], U3), the headlines
 * ([MarketNewsTicker], U4) and what is left of Explore ([ExploreDoors], U7) — the three rooms that
 * lost their bottom-bar destination when the bar became دیده‌بان · چارت · رَصد · انجمن · منو.
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
     * The preview's own chart — six spans and a scrub, without leaving this list (run Ω3).
     *
     * Optional, and null is the sheet this screen had before: a price, a pill and the day the rows
     * already hold. Hoisted rather than built here because it caches, and a cache built inside a
     * composable is a cache thrown away on the first recomposition that changes a key.
     */
    previewCandles: MarketPreviewCandles? = null,
    /**
     * Whether a tap on a row opens the preview or the chart (run Ω3). See `ReaderMode`.
     *
     * A long press opens the preview either way, so nothing is lost to a reader who has this off —
     * and nothing is hidden from one who has it on, because the sheet's own «چارت» is the tap they
     * would have made.
     */
    previewOnTap: Boolean = false,
    /**
     * Arms a milestone alert on today's move from the preview sheet (run Ω4).
     *
     * Null drops the chips rather than disabling them, for the reason every other nullable callback
     * on this screen is nullable: only the caller knows whether there is an alert store to write to.
     */
    onMilestoneAlert: ((symbol: String, up: Boolean, percent: Double) -> Unit)? = null,
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
    /**
     * The day's headlines, for the ticker under the pulse (run ΤΦΥ, U4).
     *
     * Plain values rather than the news module's own model, and [onOpenHeadline] rather than a
     * route: this screen has no business depending on `feature:news`, and a ticker that did would
     * drag a reading page, an image loader and a body parser into a row that wants four fields.
     * Empty draws nothing at all.
     */
    headlines: List<MarketHeadline> = emptyList(),
    onOpenHeadline: ((String) -> Unit)? = null,
    /**
     * «تحلیل» on the watchlist tab: the reader's list, side by side (run ΤΦΥ, U6).
     *
     * Passed straight through to [WatchlistPanel], which supplies the symbols in the reader's own
     * order. Null drops the control rather than disabling it.
     */
    onCompare: ((List<String>) -> Unit)? = null,
    /** Explore's remaining doors, moved here off the bottom bar (run ΤΦΥ, U7). Null drops each one. */
    onOpenNews: (() -> Unit)? = null,
    onOpenCalendar: (() -> Unit)? = null,
    onOpenHeatmap: (() -> Unit)? = null,
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
    // One axis now, not two (U5). The tab carries both the family it narrows to and the order it
    // puts what is left in — see [MarketsPage].
    var page by rememberSaveable { mutableStateOf(MarketsPage.TOP) }
    // Held as the two primitives rather than as a `MarketSort?`, because `rememberSaveable` can put
    // an enum and a boolean into a Bundle on its own and cannot put a data class there without a
    // Saver written for it. Enums survive process death; a hand-written Saver is a second place for
    // this to go wrong on a rotation.
    var sortKey by rememberSaveable { mutableStateOf<MarketSortKey?>(null) }
    var sortDescending by rememberSaveable { mutableStateOf(true) }
    // The whole ordering axis, gated on the platform in one place.
    //
    // Not `tickers != null`: a store built over `UnsupportedMarketTickerGateway` exists, answers,
    // and answers with nothing forever. Reading `supported` here is what keeps «پرطرفدار»,
    // «برنده/بازنده» and «حجم» off CoinePro-FX rather than putting three tabs there that can only
    // ever be empty. It also *neutralises a restored choice*, which is the case a rotation cannot
    // produce but a platform switch can: a reader who left «حجم» selected on TradeYar comes back to
    // «برتر» rather than to a saved state the new platform cannot honour.
    val arranged = tickers?.supported == true
    val lens = if (arranged) page.lens else MarketLens.NONE
    val sort = if (arranged) sortKey?.let { MarketSort(it, sortDescending) } else null
    // The symbol, not the row: the row is looked up again from the live results on every frame, so
    // the price inside the sheet ticks with the one in the list behind it instead of freezing at
    // whatever it was when the finger went down. Saveable, so a rotation does not close it.
    var preview by rememberSaveable { mutableStateOf<String?>(null) }
    // F2's filter sheet, and F2's page. Neither is `rememberSaveable`: a filter is a narrowing the
    // reader can see the effect of and would have to undo after a rotation to get their list back,
    // and a page count restored without the rows under it is a claim about a list that has not
    // loaded. Both come back at their defaults, which is the list itself.
    var filter by remember { mutableStateOf(MarketFilter()) }
    var filtersOpen by remember { mutableStateOf(false) }
    var loaded by remember { mutableIntStateOf(SymbolUniverse.PAGE) }
    // Which pulse cell is being explained, or null. Owned here rather than hoisted to the shell,
    // because the explanation is a fact about *this app's inputs* — what each figure would need and
    // why three of them read «—» — and nothing outside this file knows it. See [MarketPulseSheet].
    var pulseCell by remember { mutableStateOf<MarketPulseCell?>(null) }

    // The category chip and the watchlist tab are different filters over one list, so they are
    // applied here rather than pushed into the controller: the controller's category is what the
    // *search* screen uses, and a tab that quietly rewrote it would change the other screen too.
    // Hoisted out of the filter block: the rows need it too, to draw each star's state.
    val watched = remember(watchlist) { watchlist.map { it.uppercase() }.toSet() }
    val rows = remember(state.results, page, watched, tickerState, lens, sort, filter) {
        // The category first, then the day's figures. The order matters for one reason that is not
        // about arithmetic: `state.results` is the catalogue, which `MarketCatalogGateway` has
        // already filtered through `SymbolArtwork.covers`, so arranging *these* rows can never
        // introduce a symbol with no artwork. Building the gainers list out of the ticker table
        // instead — eight hundred rows, filtered by nothing — would put lettered discs in a list
        // this app does not allow them in.
        val visible = state.results.filter { row ->
            when {
                page.panel -> row.meta.symbol.uppercase() in watched
                else -> page.category == null || row.meta.category == page.category
            }
        }
        val arranged = arrangeMarkets(
            rows = visible,
            tickers = tickerState,
            lens = lens,
            sort = sort,
            watched = watched,
        )
        // The filter last, over the arranged list, so the rank a row is numbered with is its place
        // in *this* list rather than its place in one the reader cannot see.
        applyFilter(arranged, filter) { row ->
            tickerState.tickerFor(row)?.changePercent24h ?: row.quote?.changePercent
        }
    }
    val panel = page.panel && watchlistStore != null

    // **Only the categories this platform actually carries.**
    //
    // The strip was five fixed tabs, so on TradeYar «فارکس» and «فلزات» were two doors onto an
    // empty screen — and, because nothing had been searched, onto the *search* screen's copy:
    // «بازاری با این نام پیدا نشد». A reader who presses a tab the app drew and is told their
    // search found nothing concludes the list is broken, which is exactly what was reported.
    //
    // Computed from the catalogue rather than from the platform, so it needs no table of which
    // backend carries what and it is right the day either of them adds a family. «همه» and the
    // watchlist are always drawn: the first is the list itself and the second is the reader's own,
    // which is allowed to be empty and has copy that says so.
    val offered = remember(state.results, arranged) {
        offeredPages(
            families = state.results.mapTo(HashSet()) { it.meta.category },
            hasFigures = arranged,
        )
    }
    // A tab that has just gone away — the platform switched under the reader — must not leave the
    // list filtered by it, which would be an invisible filter with no chip to unset.
    LaunchedEffect(offered) { if (page !in offered) page = MarketsPage.TOP }
    // A new list is a new first page. Without this, changing a tab on a list the reader had scrolled
    // a thousand rows into would compose a thousand rows of the *new* list before drawing a frame.
    LaunchedEffect(page, filter, sort) { loaded = SymbolUniverse.PAGE }

    Column(modifier = modifier.fillMaxSize().background(CoineProColors.Stage)) {
        Header(
            onOpenSearch = onOpenSearch,
            filters = filter.count,
            // Absent on the watchlist, which is the reader's own list in the reader's own order:
            // a turnover floor over it would be the app hiding something they put there by hand.
            onOpenFilters = if (panel) null else ({ filtersOpen = true }),
        )
        CoineProTeachingStrip(TeachingSurface.MARKETS)
        // **The pulse, the headlines, then the tabs** (U3, U4, U5), in that order and above
        // everything else on the screen. A reader opens this surface asking «how is the market»
        // before they ask about any one row, and the four figures answer it without a scroll.
        //
        // The pulse is computed from the table this screen is already polling, so it costs no
        // request of its own — and three of its four cells read «—», which is the honest state
        // rather than a loading one. See `MarketPulse`.
        // **Absent where not one of the four could ever be filled**, which is the same rule the tabs
        // are drawn by. Three of the cells are «—» on every platform and that is a fact worth
        // saying; four of them, on a platform with no ticker route at all, is a row of nothing —
        // and a row of nothing teaches the reader that the app is broken rather than that the data
        // is elsewhere. On CoinePro-FX the pulse is simply not there.
        if (arranged) {
            val pulse = remember(tickerState.table) { MarketPulse.of(tickerState.table) }
            MarketPulseRow(pulse = pulse, onOpen = { pulseCell = it })
        }
        if (onOpenHeadline != null) {
            MarketNewsTicker(headlines = headlines, onOpen = onOpenHeadline)
        }
        ExploreDoors(
            onOpenNews = onOpenNews,
            onOpenCalendar = onOpenCalendar,
            onOpenHeatmap = onOpenHeatmap,
        )
        // One strip where there were two. The tray plus the lens row said one thing in two
        // registers and made the reader learn that they composed; seven underlined tabs say it
        // once. See [MarketsPage].
        MarketsTabRow(
            pages = offered,
            selected = page,
            onSelect = { chosen ->
                page = chosen
                // The sort goes with it. A sort is a refinement of whatever list is on screen, and
                // carrying «ارزش معاملات ↓» from «برنده/بازنده» into «برتر» would answer a tap on a
                // tab with a list ordered by something the reader chose for a different one.
                sortKey = null
            },
        )
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
                onCompare = onCompare,
                modifier = Modifier.weight(1f),
            )
            // Rows-to-be rather than a spinner: the reader sees the shape of the list that is
            // coming, and nothing jumps when it arrives.
            state.loading && state.results.isEmpty() -> CoineProSkeletonRows(
                modifier = Modifier.weight(1f).padding(horizontal = CoineProSpacing.Gutter),
                count = 8,
            )
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
                    CoineProSkeletonRows(count = 8, leading = false)
                }
            }
            rows.isEmpty() -> Centred {
                Text(
                    text = when {
                        page.panel -> stringResource(R.string.markets_watchlist_empty)
                        // The table arrived and nothing in it qualifies — a real answer, and a
                        // different one from "no market matches that name".
                        lens != MarketLens.NONE -> stringResource(R.string.markets_lens_empty)
                        // Nothing was typed here — this screen has no field — so the search
                        // screen's «بازاری با این نام پیدا نشد» was answering a question the
                        // reader never asked. The tab is the only filter left that can empty the
                        // list, and it is named.
                        // A filter the reader set is the likeliest reason a list is empty, and it
                        // is the one they can undo. It is named before the tab for that reason.
                        !filter.isEmpty -> stringResource(R.string.markets_filter_empty)
                        page.category != null -> stringResource(
                            R.string.markets_category_empty,
                            stringResource(page.labelRes),
                        )
                        else -> stringResource(R.string.markets_none)
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
                    // **A page at a time** (F2). `rows` is the whole universe — thousands on LBank
                    // once F1 stopped hiding the markets with no artwork — and a `LazyColumn` given
                    // all of them still allocates an item for every one of them and keys them all.
                    // What is handed over is the first [SymbolUniverse.PAGE] and one more page each
                    // time the reader reaches the end.
                    val page = rows.take(loaded)
                    itemsIndexed(page, key = { _, row -> row.meta.symbol }) { index, row ->
                        // Asked for as the row appears, not for the whole catalogue up front — a
                        // thousand markets would be a thousand requests nobody looked at.
                        LaunchedEffect(row.meta.symbol) { sparklines.request(row.meta.symbol) }
                        MarketListRow(
                            modifier = rowMotion(fades = false),
                            row = row,
                            // The row's place in this list. Numbered on the markets tab, where the
                            // order is a ranking, and never on a search result or the watchlist,
                            // where it is not.
                            rank = index + 1,
                            onClick = {
                                if (previewOnTap && previewCandles != null) {
                                    preview = row.meta.symbol
                                } else {
                                    onOpenSymbol(row.meta.symbol)
                                }
                            },
                            starred = onToggleWatch?.let { row.meta.symbol.uppercase() in watched },
                            onToggleStar = onToggleWatch?.let { toggle ->
                                { toggle(row.meta.symbol) }
                            },
                            onLongClick = { preview = row.meta.symbol },
                            // The markets list is the one surface this is safe on: it scrolls
                            // vertically and has no horizontal gesture of its own. The watchlist
                            // panel deliberately does not take it — it has a reorder drag.
                            swipeToStar = true,
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
                    if (loaded < rows.size) {
                        // The next page is asked for by the *last row appearing*, not by a button
                        // and not by a scroll-position listener. A listener recomputes on every
                        // frame of the fling; this composes once, when the reader actually reaches
                        // the end, and costs nothing until then.
                        item(key = MORE_KEY) {
                            LaunchedEffect(loaded) { loaded += SymbolUniverse.PAGE }
                            CoineProSkeletonRows(count = 3)
                        }
                    }
                }
            }
        }

        openSignals?.let { SignalStrip(it) }
    }

    pulseCell?.let { cell ->
        MarketPulseSheet(
            cell = cell,
            pulse = remember(tickerState.table) { MarketPulse.of(tickerState.table) },
            onDismiss = { pulseCell = null },
        )
    }

    if (filtersOpen) {
        MarketFilterSheet(
            filter = filter,
            // Offered from the *unfiltered* list, so choosing «crypto» does not make «forex»
            // disappear from the sheet that put it there.
            types = remember(state.results) { typesOf(state.results) },
            venues = remember(state.results) { venuesOf(state.results) },
            onChange = { filter = it },
            onDismiss = { filtersOpen = false },
        )
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
                    english = inEnglish(),
                ),
                onDismiss = { preview = null },
                onOpenChart = {
                    preview = null
                    onOpenSymbol(row.meta.symbol)
                },
                candles = previewCandles,
                onMilestoneAlert = onMilestoneAlert?.let { arm ->
                    { up, percent ->
                        preview = null
                        arm(row.meta.symbol, up, percent)
                    }
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

/** The key of the row that asks for the next page. Stable, so it is not re-created per page. */
private const val MORE_KEY = "markets-next-page"

/**
 * **What is left of Explore** (run ΤΦΥ, U7).
 *
 * The bottom bar is five destinations — دیده‌بان · چارت · رَصد · انجمن · منو — and Explore is not
 * one of them any more. Its three rooms are not gone: news, the economic calendar and the heat map
 * are all reached from here, one tap from the surface whose subject they are.
 *
 * Three pills rather than a menu, because three is few enough to show and a menu that has to be
 * opened to see what is in it is a door the reader never finds. Each one is dropped rather than
 * disabled where the caller has nothing behind it, and the row is absent when all three are.
 */
@Composable
private fun ExploreDoors(
    onOpenNews: (() -> Unit)?,
    onOpenCalendar: (() -> Unit)?,
    onOpenHeatmap: (() -> Unit)?,
) {
    if (onOpenNews == null && onOpenCalendar == null && onOpenHeatmap == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.Half),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Door(R.string.markets_door_news, onOpenNews)
        Door(R.string.markets_door_calendar, onOpenCalendar)
        Door(R.string.markets_door_heatmap, onOpenHeatmap)
    }
}

@Composable
private fun RowScope.Door(labelRes: Int, onClick: (() -> Unit)?) {
    if (onClick == null) return
    val haptics = rememberCoineProHaptics()
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(CoineProPillShape)
            .background(CoineProColors.SurfaceElevated)
            .clickable {
                haptics.select()
                onClick()
            }
            .padding(vertical = CoineProSpacing.Half),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun Header(
    onOpenSearch: () -> Unit,
    /**
     * How many filter questions are answered, or null on a tab that has no filter (F2).
     *
     * A number rather than a boolean, because «filtered» and «filtered three ways» are different
     * facts to a reader looking at a short list and wondering where the rest went.
     */
    filters: Int? = null,
    onOpenFilters: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CoineProSpacing.Two, end = CoineProSpacing.Two, top = CoineProSpacing.OneHalf, bottom = CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Text(
            text = stringResource(R.string.markets_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (onOpenFilters != null) {
            val active = (filters ?: 0) > 0
            Row(
                modifier = Modifier
                    .height(34.dp)
                    .clip(CoineProShapes.small)
                    .background(if (active) CoineProColors.AccentFill else CoineProColors.SurfaceElevated)
                    .clickable(onClick = onOpenFilters)
                    .padding(horizontal = CoineProSpacing.One),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            ) {
                Icon(
                    painter = painterResource(DesignR.drawable.icon_sliders_horizontal),
                    contentDescription = stringResource(R.string.markets_filter),
                    tint = if (active) CoineProColors.OnAccent else CoineProColors.TextSecondary,
                    modifier = Modifier.size(17.dp),
                )
                if (active) {
                    Text(
                        text = filters.toString(),
                        style = MaterialTheme.typography.labelSmall.numeric(),
                        color = CoineProColors.OnAccent,
                    )
                }
            }
        }
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
    // Every measurement here is `MarketListRow`'s: the same 16 of horizontal padding, the same
    // [RowGap] between elements, the same [LogoSize] disc and the same [SymbolColumn]. They are
    // read from the row rather than written again, because the two fell out of step once already —
    // see the row's own note on the gap. The star is optional in the row, so it is optional here
    // too, and it reserves 48 because that is what `minimumInteractiveComponentSize` gives it.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Two)
            .padding(top = CoineProSpacing.OneHalf, bottom = CoineProSpacing.Half),
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        val style = MaterialTheme.typography.labelSmall
        if (starRail) Spacer(modifier = Modifier.width(48.dp))
        Spacer(modifier = Modifier.width(LogoSize))
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
    // Movement, not execution: a market that rose is not an order to buy. See
    // `CoineProColors.MarketUp`.
    val tone = CoineProColors.marketMove(change)
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
        // Plain, for the reason `CoineProPercentText` gives: a pill on every row of a list stops
        // marking anything out. The card surfaces keep theirs.
        change?.let {
            CoineProPercentText(
                percent = it,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
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
