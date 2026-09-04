package com.coinepro.feature.search

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistColumn
import com.coinepro.core.datastore.WatchlistFlag
import com.coinepro.core.datastore.WatchlistSettings
import com.coinepro.core.datastore.WatchlistSort
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTeachingStrip
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.TeachingSurface
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.designsystem.rowMotion
import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.watchlistsync.R as SyncR
import com.coinepro.core.watchlistsync.WatchlistSyncController
import com.coinepro.core.watchlistsync.WatchlistSyncState
import com.coinepro.core.watchlistsync.messageRes
import com.coinepro.core.watchlistsync.noticeArguments
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * The reader's own lists, on the markets tab's watchlist segment.
 *
 * ### Why this is a panel inside the markets screen rather than a screen of its own
 *
 * The watchlist is one of five tabs on a screen the reader is already on, and the instruments in
 * it are the instruments in the list above it. Making it a separate destination would put a
 * navigation step between "these are the markets" and "these are my markets", and would make the
 * two lists two screens that have to be kept looking alike by hand. They share
 * [MarketListRow] instead, so they cannot drift.
 *
 * ### Four things the competition charges for
 *
 * Several named lists, colour flags, a chosen column set with a sort, and plain-text import and
 * export. The first is the one that matters: the obvious competitor's free tier allows exactly one
 * watchlist. `WatchlistStore` puts no limit on the number here, and this panel's list switcher is
 * the whole of that feature's surface — a chip per list and a plus.
 *
 * ### Order, drag and sort
 *
 * A watchlist's order is the reader's, so the default is the order they built and the drag handle
 * is how they change it. A column sort is a *view* over that order and never rewrites it, which is
 * why the third tap on a heading returns to the reader's own order rather than leaving them stuck
 * in somebody else's. The handle disappears while a sort is on, because dragging a row inside a
 * list ordered by volume would be a gesture with nothing to mean.
 *
 * ### What is shown, and what is only stored
 *
 * A symbol the catalogue does not carry is kept in storage and not drawn. That is the app's
 * standing rule — no symbol without artwork reaches a list — and the watchlist is where it would
 * otherwise be broken first, since a reader can import any ticker they like from another app.
 */
@Composable
fun WatchlistPanel(
    store: WatchlistStore,
    /** Everything the platform quotes, already filtered to symbols this app can draw. */
    catalogue: List<MarketSearchRow>,
    /** Symbol to its last day of closes, for the day-high and day-low columns. */
    lines: Map<String, List<Double>>,
    /** Asks for one symbol's line. Called as a row appears, never for the whole list. */
    onRequestLine: (String) -> Unit,
    onOpenSymbol: (String) -> Unit,
    /** Sync, where the platform serves it. Null draws nothing — see [MarketsScreen]'s own note. */
    watchlistSync: WatchlistSyncController? = null,
    /** Starts a price alert from a row's menu. Null where this build has no alert composer. */
    onCreateAlert: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    // Remembered against the store rather than rebuilt every recomposition: a fresh Flow instance
    // per frame would make `collectAsStateWithLifecycle` restart its collection on every frame.
    val listsFlow = remember(store) { store.lists() }
    val activeFlow = remember(store) { store.activeListId() }
    val lists by listsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeId by activeFlow.collectAsStateWithLifecycle(initialValue = Watchlist.DEFAULT_LIST_ID)
    val settingsFlow = remember(store, activeId) { store.settings(activeId) }
    val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = WatchlistSettings())

    var sheet by remember { mutableStateOf<WatchlistSheet?>(null) }
    var flagFilter by remember(activeId) { mutableStateOf<WatchlistFlag?>(null) }

    val stored = lists.firstOrNull { it.id == activeId }?.symbols.orEmpty()
    // The optimistic order a drag is working in. See `ReorderHandle`: the store is written once,
    // when the finger lifts, and this holds the live picture until that write comes back.
    var draft by remember(activeId) { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(stored, draft) { if (draft != null && stored == draft) draft = null }
    val order = draft ?: stored

    val bySymbol = remember(catalogue) { catalogue.associateBy { it.meta.symbol.uppercase() } }
    val visible = remember(order, bySymbol, flagFilter, settings.flags) {
        order.filter { flagFilter == null || settings.flags[it] == flagFilter }
            .mapNotNull { bySymbol[it] }
    }
    val rows = remember(visible, settings.sort, settings.flags, lines) {
        sortRows(visible, settings.sort, settings.flags, lines)
    }

    // One scroll state for every row's figure block and for the headings above them, so a reader
    // who pushes the columns sideways moves the whole table rather than one row out of alignment
    // with its neighbours.
    val figureScroll = rememberScrollState()
    val columns = remember(settings.columns) {
        // Enum order, not set order: the reader ticks boxes in whatever order they think of them,
        // and a column set that rearranged itself according to that would be unreadable.
        WatchlistColumn.entries.filter { it in settings.columns && it != WatchlistColumn.FLAG }
    }
    val flagged = remember(settings.flags) { settings.flags.values.toSet() }
    // The flag column *is* the rail. Unticking it in the column sheet takes the rail off every
    // row, which is what makes that tick mean something — the rail is not a figure and has no
    // cell of its own in the strip.
    val rail = WatchlistColumn.FLAG in settings.columns
    val flagSortAction: (() -> Unit)? = if (!rail) {
        null
    } else {
        { scope.launch { store.setSort(activeId, nextSort(settings.sort, WatchlistColumn.FLAG)) } }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ListSwitcher(
            lists = lists,
            activeId = activeId,
            onSelect = { id -> scope.launch { store.setActiveList(id) } },
            onManage = { sheet = WatchlistSheet.Lists },
        )
        Toolbar(
            count = order.size,
            flags = flagged,
            flagFilter = flagFilter,
            onFlagFilter = { flagFilter = it },
            // The flag has no heading in the strip below — its column is the three-point rail at
            // the row's leading edge, which has no room for a word — so its sort control lives
            // here, beside the colours it orders by.
            flagSort = settings.sort.takeIf { it.column == WatchlistColumn.FLAG },
            onFlagSort = flagSortAction,
            onColumns = { sheet = WatchlistSheet.Columns },
            onTransfer = { sheet = WatchlistSheet.Transfer },
        )
        CoineProTeachingStrip(TeachingSurface.WATCHLIST)
        if (columns.isNotEmpty()) {
            Headings(
                columns = columns,
                sort = settings.sort,
                scroll = figureScroll,
                // The grip only exists while the list is in the reader's own order, and it takes
                // forty-six points with its spacing. A heading strip that ignored that would sit
                // that far off its own column the moment a sort was turned on.
                lead = headingLead(withRail = rail, withHandle = settings.sort.isManual),
                onSort = { column ->
                    scope.launch { store.setSort(activeId, nextSort(settings.sort, column)) }
                },
            )
        }

        if (rows.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CoineProEmptyState(
                    icon = DesignR.drawable.brand_watchlist,
                    message = stringResource(
                        if (flagFilter != null) R.string.watchlist_empty_flag else R.string.watchlist_empty,
                    ),
                    hint = stringResource(R.string.watchlist_empty_hint),
                    action = stringResource(R.string.watchlist_transfer),
                    onAction = { sheet = WatchlistSheet.Transfer },
                    // An invitation, not a retry. An empty watchlist has nothing else on it, and
                    // the one way to fill it should not be the quietest thing on the page.
                    actionIsPrimary = true,
                )
            }
        } else {
            val orderState = rememberUpdatedState(order)
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(rows, key = { it.meta.symbol }) { row ->
                    val symbol = row.meta.symbol.uppercase()
                    // Asked for as the row appears, not for the whole list up front — and the
                    // day-high and day-low columns read the same series, so a row that has one
                    // has both.
                    LaunchedEffect(symbol) { onRequestLine(symbol) }
                    MarketListRow(
                        modifier = rowMotion(),
                        row = row,
                        onClick = { onOpenSymbol(row.meta.symbol) },
                        onLongClick = { sheet = WatchlistSheet.RowMenu(symbol) },
                        flag = settings.flags[symbol],
                        flagRail = rail,
                        handle = if (settings.sort.isManual) {
                            {
                                ReorderHandle(
                                    symbol = symbol,
                                    order = orderState,
                                    onPreview = { draft = it },
                                    onCommit = { from, to ->
                                        scope.launch { store.move(activeId, from, to) }
                                    },
                                )
                            }
                        } else {
                            null
                        },
                        trailing = {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(figureScroll),
                                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val figures = figuresFor(row, lines[symbol].orEmpty())
                                columns.forEach { column ->
                                    WatchlistFigureCell(column = column, figures = figures)
                                }
                            }
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

    // Sync, at the foot of the panel and never above the lists themselves.
    //
    // Drawn only where the platform serves the route: `available` is false on CoinePro-FX, which
    // has none, and a row saying so would be a row about a feature that platform's reader has no
    // way to want. It is a button rather than a background job on purpose — the watchlist is the
    // only thing a reader builds inside this app, and a sync they did not ask for, on a connection
    // that comes and goes, is the wrong moment to touch it.
    if (watchlistSync != null) {
        val syncState by watchlistSync.state.collectAsStateWithLifecycle()
        if (syncState.available) {
            // Read from the configuration rather than a store: the app already re-bases its
            // context on the reader's chosen language, so this is the same answer, and it is one a
            // feature module can reach without depending on `:app`.
            val language = AppLanguage.fromTag(LocalConfiguration.current.locales[0].language)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.One),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        syncState.messageRes(),
                        *syncState.noticeArguments(language).toTypedArray(),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.weight(1f),
                )
                CoineProSecondaryButton(
                    text = stringResource(SyncR.string.watchlist_sync_action),
                    onClick = watchlistSync::sync,
                )
            }
        }
    }

    WatchlistSheets(
        sheet = sheet,
        onOpenSymbol = onOpenSymbol,
        onCreateAlert = onCreateAlert,
        store = store,
        lists = lists,
        activeId = activeId,
        settings = settings,
        onDismiss = { sheet = null },
    )
}

/**
 * Which panel is open over the list.
 *
 * A sealed hierarchy rather than four booleans, because two of them open at once is not a state
 * this screen has and should not be a state it can represent.
 */
internal sealed interface WatchlistSheet {
    /** Make, rename and delete lists. */
    data object Lists : WatchlistSheet

    /** Choose the columns for the list on screen. */
    data object Columns : WatchlistSheet

    /** Paste a list in, or copy this one out. */
    data object Transfer : WatchlistSheet

    /**
     * One row's own menu: its flag, and removing it.
     *
     * Named `RowMenu` rather than `Row`, because every file that handles it also lays out
     * `androidx.compose.foundation.layout.Row`, and two things called Row a few lines apart is a
     * reading cost for no gain.
     */
    data class RowMenu(val symbol: String) : WatchlistSheet
}

/**
 * The list switcher: one chip per list, and a way to manage them.
 *
 * A chip row rather than a dropdown. A reader with four lists switches between them constantly and
 * a dropdown costs two taps every time; the chips cost one and keep every list's name in sight,
 * which is also how somebody discovers that more than one list is even possible here.
 */
@Composable
private fun ListSwitcher(
    lists: List<Watchlist>,
    activeId: String,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoineProChipRow(
            options = lists.map { CoineProChip(id = it.id, label = it.name, count = it.symbols.size) },
            selectedId = activeId,
            onSelect = { id -> id?.let(onSelect) },
            modifier = Modifier.weight(1f),
            compact = true,
        )
        IconAction(
            icon = CoineProIcons.Add,
            label = stringResource(R.string.watchlist_manage),
            onClick = onManage,
            modifier = Modifier.padding(end = CoineProSpacing.Two),
        )
    }
}

/**
 * The line between the switcher and the table.
 *
 * It carries the count, the flag filter, and the two controls that change what the table shows.
 * The flag filter appears only once something is flagged: seven grey dots above an unflagged list
 * are seven controls that do nothing, and the reader has no way to know why.
 */
@Composable
private fun Toolbar(
    count: Int,
    flags: Set<WatchlistFlag>,
    flagFilter: WatchlistFlag?,
    onFlagFilter: (WatchlistFlag?) -> Unit,
    /** The sort if it is currently on the flag column, and null otherwise. */
    flagSort: WatchlistSort?,
    /** Cycles the flag sort. Null where the reader has taken the flag column off the rows. */
    onFlagSort: (() -> Unit)?,
    onColumns: () -> Unit,
    onTransfer: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Text(
            // A prose count, so Persian digits — unlike every figure in the table below it.
            text = stringResource(R.string.watchlist_symbol_count, count.toPersianDigits()),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        if (onFlagSort != null) {
            // **A key, and the word on it is «برچسب».**
            //
            // Two faults, one circle drawn round them. The word was «پرچم» — the literal
            // translation of the reference's *flag*, and in Persian the thing a country has, so a
            // reader met it beside «۳ نماد» and looked for a country. And it was bare text in a
            // line of counts, which is why it read as a mistake rather than as a control: nothing
            // about it said it could be pressed, so the only thing left to notice was the word.
            //
            // The plate is the same one the flag filter's neighbours wear, so it now reads as what
            // it is — the sort key for the colour column, next to the colours it orders by.
            Text(
                text = WatchlistColumn.FLAG.persianLabel + when {
                    flagSort == null -> ""
                    flagSort.descending -> " ↓"
                    else -> " ↑"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (flagSort != null) CoineProColors.TextSecondary else CoineProColors.TextMuted,
                maxLines = 1,
                modifier = Modifier
                    .clip(CoineProShapes.small)
                    .background(CoineProColors.SurfaceElevated)
                    .clickable(onClick = onFlagSort)
                    .padding(horizontal = CoineProSpacing.One, vertical = 3.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (flags.isNotEmpty()) {
            WatchlistFlag.entries.filter { it in flags }.forEach { flag ->
                FlagDot(
                    flag = flag,
                    selected = flagFilter == flag,
                    onClick = { onFlagFilter(if (flagFilter == flag) null else flag) },
                )
            }
        }
        IconAction(
            icon = DesignR.drawable.icon_sliders_horizontal,
            label = stringResource(R.string.watchlist_columns),
            onClick = onColumns,
        )
        IconAction(
            icon = DesignR.drawable.icon_copy,
            label = stringResource(R.string.watchlist_transfer),
            onClick = onTransfer,
        )
    }
}

/** One colour, as a filter. Selected, it gains a ring rather than changing its own colour. */
@Composable
private fun FlagDot(flag: WatchlistFlag, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberCoineProHaptics()
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CoineProPillShape)
            .clickable {
                haptics.select()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (selected) 16.dp else 12.dp)
                .clip(CoineProPillShape)
                .background(Color(flag.argb.toULong() shl 32)),
        )
    }
}

/**
 * The column headings, scrolled in step with the rows below them.
 *
 * The leading spacer is the width of everything before the figure block — the flag rail, the drag
 * handle, the logo and the ticker column — worked out from the same constants the row uses, so the
 * heading sits over its column instead of near it.
 */
@Composable
private fun Headings(
    columns: List<WatchlistColumn>,
    sort: WatchlistSort,
    scroll: ScrollState,
    lead: Dp,
    onSort: (WatchlistColumn) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
        // The last of the row's own steps — [headingLead] carries the ones before it. The two have
        // to add up to the same number or every heading sits beside its column instead of over it.
        horizontalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        Spacer(modifier = Modifier.width(lead))
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            columns.forEach { column ->
                WatchlistColumnHeading(
                    column = column,
                    sorted = sort.column == column,
                    descending = sort.descending,
                    onClick = { onSort(column) },
                )
            }
        }
    }
}

/**
 * The grip that reorders a row.
 *
 * ### One write, at the end
 *
 * The obvious implementation calls `move` on every step and lets the store's flow redraw the list.
 * It does not work: the write is asynchronous, so the next step computes its indices against an
 * order the store has not caught up to yet, and a fast drag ends up shuffling rows the reader
 * never touched. So the drag maintains an optimistic order locally through [onPreview] — which is
 * what the list actually draws while a finger is down — and [onCommit] is called once, with the
 * row's original and final positions, when the finger lifts.
 *
 * ### Why it counts pixels rather than asking the layout
 *
 * Every row in this module is exactly [MarketRowHeight] tall, by construction rather than by luck,
 * so a finger that has travelled one row's height has travelled one position. Measuring each item
 * instead would mean holding a map of item bounds that is stale for one frame after every reorder,
 * which is the frame the next step is computed in.
 *
 * The gesture is on the handle, not on the row: a drag anywhere on the row would fight the list's
 * own scrolling, and a long press already belongs to the row menu.
 */
@Composable
private fun ReorderHandle(
    symbol: String,
    order: State<List<String>>,
    onPreview: (List<String>?) -> Unit,
    onCommit: (Int, Int) -> Unit,
) {
    val haptics = rememberCoineProHaptics()
    val rowHeightPx = with(LocalDensity.current) { MarketRowHeight.toPx() }
    Box(
        modifier = Modifier
            // Thirty-two wide — see [HandleWidth] — and forty
            // tall, which with the row's nine points of padding at each end is exactly
            // [MarketRowHeight]. Deliberately not `minimumInteractiveComponentSize`: its
            // forty-eight points are the guidance for a *tap*, they would push the default column
            // set past the width of a 411dp phone, and — the part that would actually break — they
            // would make this the tallest thing in the row and stretch it to 66dp, which the drag
            // arithmetic below divides by 58.
            .width(HandleWidth)
            .height(40.dp)
            .clip(CoineProShapes.small)
            .pointerInput(symbol) {
                var working = emptyList<String>()
                var origin = -1
                var travelled = 0f
                detectDragGestures(
                    onDragStart = {
                        working = order.value
                        origin = working.indexOf(symbol)
                        travelled = 0f
                        haptics.commit()
                    },
                    onDragEnd = {
                        val landing = working.indexOf(symbol)
                        if (origin >= 0 && landing >= 0 && landing != origin) onCommit(origin, landing)
                        origin = -1
                    },
                    onDragCancel = {
                        onPreview(null)
                        origin = -1
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        if (origin < 0) return@detectDragGestures
                        travelled += amount.y
                        while (abs(travelled) >= rowHeightPx) {
                            val step = if (travelled > 0f) 1 else -1
                            val from = working.indexOf(symbol)
                            val to = from + step
                            if (from < 0 || to !in working.indices) {
                                travelled = 0f
                                break
                            }
                            val moved = working.toMutableList()
                            moved.add(to, moved.removeAt(from))
                            working = moved
                            onPreview(moved)
                            travelled -= step * rowHeightPx
                            haptics.select()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Three rules rather than a glyph: there is no drag-handle drawable in the icon set, and
        // three bars is what a grip looks like in every list that has one.
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(2.dp)
                        .clip(CoineProPillShape)
                        .background(CoineProColors.TextDisabled),
                )
            }
        }
    }
}

/** A square icon button in the app's flat style: a fill, a hairline, and no shadow. */
@Composable
internal fun IconAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberCoineProHaptics()
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CoineProShapes.small)
            .background(CoineProColors.SurfaceElevated)
            .border(1.dp, CoineProColors.BorderSubtle, CoineProShapes.small)
            .clickable {
                haptics.select()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = CoineProColors.TextSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * What the next tap on a heading means.
 *
 * Largest first, then smallest first, then back to the reader's own order. The third state is the
 * one most tables leave out and it is the one this list cannot do without — the manual order is
 * the feature, and a sort with no way off it would take it away.
 */
internal fun nextSort(current: WatchlistSort, column: WatchlistColumn): WatchlistSort = when {
    current.column != column -> WatchlistSort(column, descending = true)
    current.descending -> WatchlistSort(column, descending = false)
    else -> WatchlistSort.Manual
}

/**
 * The rows in the order the reader asked for.
 *
 * A row with no figure for the sorted column always sinks to the bottom, in both directions. The
 * alternative — treating a missing price as zero — floats every unquoted instrument to the top of
 * an ascending sort, which is a list sorted by "what the feed has not sent yet".
 */
internal fun sortRows(
    rows: List<MarketSearchRow>,
    sort: WatchlistSort,
    flags: Map<String, WatchlistFlag>,
    lines: Map<String, List<Double>>,
): List<MarketSearchRow> {
    val column = sort.column ?: return rows
    val keyed = rows.map { row ->
        val symbol = row.meta.symbol.uppercase()
        row to if (column == WatchlistColumn.FLAG) {
            flags[symbol]?.ordinal?.toDouble()
        } else {
            val figures = figuresFor(row, lines[symbol].orEmpty())
            when (column) {
                WatchlistColumn.LAST_PRICE -> figures.price
                WatchlistColumn.CHANGE -> figures.change
                WatchlistColumn.CHANGE_PERCENT -> figures.changePercent
                WatchlistColumn.DAY_HIGH -> figures.dayHigh
                WatchlistColumn.DAY_LOW -> figures.dayLow
                WatchlistColumn.VOLUME -> figures.volume
                WatchlistColumn.QUOTE_VOLUME -> figures.quoteVolume
                WatchlistColumn.FLAG -> null
            }
        }
    }
    val present = keyed.filter { it.second != null }.sortedBy { it.second }
    val absent = keyed.filter { it.second == null }.map { it.first }
    val ordered = if (sort.descending) present.asReversed() else present
    return ordered.map { it.first } + absent
}

/**
 * How far the figure block starts from the row's leading edge.
 *
 * The flag rail, the drag handle's touch target where there is one, the logo and the ticker
 * column, plus the twelve points of spacing the row puts between each of them. Written out from
 * the row's own constants rather than measured, because the headings live in a different `Row`
 * from the cells they label and the two have to agree to the point — a heading strip that is
 * nearly right is worse than none, since it labels the wrong column rather than no column.
 */
private fun headingLead(withRail: Boolean, withHandle: Boolean) =
    (if (withRail) 3.dp + RowGap else 0.dp) +
        (if (withHandle) HandleWidth + RowGap else 0.dp) +
        LogoSize + RowGap + SymbolColumn

/**
 * How wide the reorder grip is.
 *
 * Thirty-two points, and the two it lost are two of the twenty-four the row was overflowing a
 * 393-point phone by — see [MarketListRow] and [WatchlistColumn.DEFAULT] for that arithmetic. It is
 * still the largest thing the row can spare and still a comfortable target at forty points tall.
 */
internal val HandleWidth = 32.dp
