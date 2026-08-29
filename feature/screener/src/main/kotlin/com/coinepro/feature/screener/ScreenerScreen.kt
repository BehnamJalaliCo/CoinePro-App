package com.coinepro.feature.screener

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.snapshotFlow
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
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.coineProPriceFlash
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.designsystem.resolve
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.feature.screener.model.ScreenerField
import com.coinepro.feature.screener.model.ScreenerFilter
import com.coinepro.feature.screener.model.ScreenerRow
import com.coinepro.feature.screener.model.ScreenerSort
import com.coinepro.feature.screener.model.ScreenerUnit
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The screener — [108] and [109] — as a table a person can actually read on a phone.
 *
 * ### Why this screen exists at all
 *
 * TradingView ships seven screeners on the web and none on a phone; their own help says so in as
 * many words. Their readers ask for it and are told it is not supported. This is the whole feature
 * on a five-inch screen, and it is free: every condition, every indicator, every saved screen, with
 * no cap and no membership check anywhere in this module.
 *
 * ### The design, in one paragraph
 *
 * It is a table, so it is laid out as one and nothing else. Fixed column widths so the digits line
 * up down the column, which is the entire reason a table beats a list of cards. Latin figures with
 * [TextAlign.Right] — never `TextAlign.End`, which would flip them with the layout and undo the
 * alignment on the one screen that needs it most. Persian digits for the result count, because that
 * is prose. One primary action in view at a time: the filter button, and inside the sheet, apply.
 * No card, no shadow, no icon that has a label available.
 *
 * The value columns scroll horizontally as one strip, header and rows together, so a reader who
 * adds a fourth and a fifth column gets a wider table rather than five squeezed numbers.
 */
@Composable
fun ScreenerScreen(
    controller: ScreenerController,
    onOpenSymbol: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // One scroll position shared by the heading strip and every row's value strip. Two states would
    // let the headings drift out of line with the numbers under them, which is worse than no
    // headings at all.
    val valuesScroll = rememberScrollState()

    DisposableEffect(controller) {
        controller.start()
        onDispose(controller::stop)
    }

    // The quote poll's entire subscription. See ScreenerController: the rows below the fold cost
    // nothing until they are scrolled to, and this is the line that makes that true.
    LaunchedEffect(listState, controller) {
        snapshotFlow {
            val info = listState.layoutInfo.visibleItemsInfo
            if (info.isEmpty()) null else info.first().index to info.last().index
        }
            .distinctUntilChanged()
            .collect { window -> window?.let { (first, last) -> controller.setVisible(first, last) } }
    }

    Column(modifier = modifier.fillMaxSize().background(CoineProColors.Stage)) {
        Header(onOpenFilters = { sheetOpen = true })
        CategoryChips(
            selected = selectedCategory(state.filters),
            onSelect = { category -> controller.setFilters(withCategory(state.filters, category)) },
        )
        ResultCount(state)
        ColumnHeadings(
            columns = state.columns,
            indicatorColumns = state.indicatorColumns,
            sort = state.sort,
            scroll = valuesScroll,
            onSort = controller::toggleSort,
            onSortIndicator = controller::toggleIndicatorSort,
        )

        when {
            state.loading && state.rows.isEmpty() -> Centred {
                CircularProgressIndicator(color = CoineProColors.Gold, strokeWidth = 2.dp)
            }

            // A failure is not an empty result, and the two must not share copy. The markets list
            // shipped for a release telling readers on a dead connection that no market matched.
            state.error != null && state.rows.isEmpty() -> Centred {
                CoineProEmptyState(
                    icon = CoineProIcons.Warning,
                    message = state.error?.resolve() ?: stringResource(R.string.screener_failed),
                    action = stringResource(R.string.screener_retry),
                    onAction = controller::refresh,
                )
            }

            state.rows.isEmpty() -> Centred {
                val clear: (() -> Unit)? = if (state.narrowed) ({ controller.clearFilters() }) else null
                CoineProEmptyState(
                    icon = CoineProIcons.Filter,
                    message = if (state.narrowed) {
                        stringResource(R.string.screener_empty)
                    } else {
                        stringResource(R.string.screener_empty_open)
                    },
                    hint = if (state.narrowed) stringResource(R.string.screener_empty_hint) else null,
                    action = if (state.narrowed) stringResource(R.string.screener_clear) else null,
                    onAction = clear,
                )
            }

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxSize(),
                contentPadding = PaddingValues(bottom = CoineProSpacing.Two),
            ) {
                items(state.rows, key = ScreenerRow::symbol) { row ->
                    ScreenerTableRow(
                        row = row,
                        columns = state.columns,
                        indicatorColumns = state.indicatorColumns,
                        scroll = valuesScroll,
                        onClick = { onOpenSymbol(row.symbol) },
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

    if (sheetOpen) {
        ScreenerFilterSheet(
            state = state,
            onDismiss = { sheetOpen = false },
            onSetFilters = controller::setFilters,
            onApplyScreen = controller::apply,
            onSave = controller::save,
            onDelete = controller::delete,
        )
    }
}

/**
 * The title and the one action.
 *
 * A single button, labelled, because the funnel glyph alone is the sort of icon a reader has to
 * learn. The label is two words and the row has space for it.
 */
@Composable
private fun Header(onOpenFilters: () -> Unit) {
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
            text = stringResource(R.string.screener_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        CoineProSecondaryButton(
            text = stringResource(R.string.screener_open_filters),
            onClick = onOpenFilters,
            icon = CoineProIcons.Filter,
        )
    }
}

/**
 * The asset-class chips.
 *
 * A chip row rather than another line in the sheet, because narrowing to «کریپتو» is the one filter
 * readers use on nearly every visit and burying a one-tap action two taps deep is how a screener
 * starts to feel like a form. It writes an ordinary [ScreenerFilter.Category] into the same filter
 * list the sheet edits, so the two controls can never disagree about what is being shown.
 */
@Composable
private fun CategoryChips(selected: SymbolCategory?, onSelect: (SymbolCategory?) -> Unit) {
    val options = remember {
        listOf(
            SymbolCategory.CRYPTO to R.string.screener_category_crypto,
            SymbolCategory.FOREX to R.string.screener_category_forex,
            SymbolCategory.METAL to R.string.screener_category_metal,
            SymbolCategory.INDEX to R.string.screener_category_index,
            SymbolCategory.ENERGY to R.string.screener_category_energy,
        )
    }
    CoineProChipRow(
        options = options.map { (category, label) -> CoineProChip(category.name, stringResource(label)) },
        selectedId = selected?.name,
        onSelect = { id -> onSelect(SymbolCategory.entries.firstOrNull { it.name == id }) },
        allLabel = stringResource(R.string.screener_category_all),
        compact = true,
    )
}

/**
 * How many markets matched, and how much of the catalogue that answer is based on.
 *
 * Persian digits, because this is prose: «۲۳ بازار» is read aloud as words with a number in it,
 * unlike the figures in the table below, which are held up against another terminal and stay Latin.
 * The progress line appears only while bars are still arriving — a count that is still moving has
 * to say so, or a reader will take the first number they see as the answer.
 */
@Composable
private fun ResultCount(state: ScreenerState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.One),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.screener_count, state.matchCount.toPersianDigits()),
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextSecondary,
        )
        if (state.resolving && state.universeSize > 0) {
            Text(
                text = stringResource(
                    R.string.screener_progress,
                    state.resolvedCount.toPersianDigits(),
                    state.universeSize.toPersianDigits(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

/**
 * The sortable column headings.
 *
 * Every heading is a tap target and the sorted one is marked in the accent with an arrow saying
 * which way. Tapping it again flips the direction; tapping another moves the sort and starts
 * descending, which is what somebody who just chose «حجم» means.
 */
@Composable
private fun ColumnHeadings(
    columns: List<ScreenerField>,
    indicatorColumns: List<ScreenerIndicatorColumn>,
    sort: ScreenerSort,
    scroll: ScrollState,
    onSort: (ScreenerField) -> Unit,
    onSortIndicator: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.screener_column_symbol),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextDisabled,
            modifier = Modifier.width(SYMBOL_COLUMN),
        )
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            columns.forEach { column ->
                Heading(
                    label = column.label,
                    // An indicator sort parks itself on a field it is not using, so a field
                    // heading is only the sorted one when no indicator key is set. Without that
                    // check two headings would carry the arrow at once.
                    sorted = sort.indicatorKey == null && column == sort.field,
                    descending = sort.descending,
                    onClick = { onSort(column) },
                )
            }
            indicatorColumns.forEach { column ->
                Heading(
                    label = column.label,
                    sorted = sort.indicatorKey == column.key,
                    descending = sort.descending,
                    onClick = { onSortIndicator(column.key) },
                )
            }
        }
    }
}

/**
 * One column heading: a tap target, marked with an arrow when the table is ordered by it.
 *
 * Shared by the chosen columns and by the indicator columns a condition adds, because they are the
 * same control to a reader and two copies of it would eventually differ in a detail — the arrow's
 * size, the accent, the tap area — that makes one of them look disabled.
 */
@Composable
private fun Heading(label: String, sorted: Boolean, descending: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .width(FIGURE_COLUMN)
            .clip(CoineProShapes.extraSmall)
            .clickable(onClick = onClick)
            .padding(vertical = CoineProSpacing.Half),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sorted) {
            Icon(
                painter = painterResource(
                    if (descending) CoineProIcons.TrendDown else CoineProIcons.TrendUp,
                ),
                contentDescription = null,
                tint = CoineProColors.Accent,
                modifier = Modifier.size(11.dp).padding(end = 2.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (sorted) CoineProColors.Accent else CoineProColors.TextDisabled,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One market.
 *
 * Dense — the same 58dp floor the markets list uses — so a screenful is a screenful of markets
 * rather than of padding. The height is fixed whether or not the figures have arrived, because a
 * table that grows as its numbers land moves the row out from under the reader's thumb.
 */
@Composable
private fun ScreenerTableRow(
    row: ScreenerRow,
    columns: List<ScreenerField>,
    indicatorColumns: List<ScreenerIndicatorColumn>,
    scroll: ScrollState,
    onClick: () -> Unit,
) {
    val haptics = rememberCoineProHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 58.dp)
            // The tint a trader reads before any figure: which rows are moving.
            .coineProPriceFlash(row.price)
            .clickable {
                haptics.select()
                onClick()
            }
            .padding(horizontal = CoineProSpacing.Two, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(SYMBOL_COLUMN)) {
            Text(
                text = row.meta.symbol,
                // Forced left-to-right: a ticker is Latin and a right-to-left paragraph would
                // reorder a symbol that happens to end in a digit.
                style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
                color = CoineProColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.meta.listDescription,
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextDisabled,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            columns.forEach { column ->
                Figure(
                    text = row.textOf(column) ?: ScreenerFormat.cell(row.valueOf(column), column.unit),
                    unit = column.unit,
                    value = row.valueOf(column),
                )
            }
            indicatorColumns.forEach { column ->
                val value = column.valueOf(row)
                Figure(
                    text = ScreenerFormat.cell(value, column.unit),
                    unit = column.unit,
                    value = value,
                )
            }
        }
    }
}

/**
 * One figure in the value strip.
 *
 * A percentage is tinted by its sign and nothing else is, which is the rule the markets list
 * follows: colour on a price column would say something about a number that has no direction.
 */
@Composable
private fun Figure(text: String, unit: ScreenerUnit, value: Double?) {
    val ink = when {
        unit != ScreenerUnit.PERCENT -> CoineProColors.TextPrimary
        (value ?: 0.0) > 0.0 -> CoineProColors.Buy
        (value ?: 0.0) < 0.0 -> CoineProColors.Sell
        else -> CoineProColors.TextMuted
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
        color = ink,
        modifier = Modifier.width(FIGURE_COLUMN),
        // Right, not End. End would mirror with the layout direction and put the decimal points of
        // a Persian screen on the wrong side of the column, which is the one thing a table of
        // figures cannot survive.
        textAlign = TextAlign.Right,
        maxLines = 1,
    )
}

@Composable
private fun ColumnScope.Centred(content: @Composable () -> Unit) {
    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { content() }
}

/**
 * The asset class the chip row is showing, read back out of the filter list.
 *
 * Derived rather than held in its own state, so there is one source of truth about what is being
 * filtered. A chip row with its own copy of the answer is a chip row that shows «کریپتو» after the
 * sheet has removed the condition behind it.
 */
internal fun selectedCategory(filters: List<ScreenerFilter>): SymbolCategory? {
    val filter = filters.filterIsInstance<ScreenerFilter.Category>()
        .firstOrNull { it.field == ScreenerField.ASSET_CLASS } ?: return null
    val only = filter.values.singleOrNull() ?: return null
    return SymbolCategory.entries.firstOrNull { it.name == only }
}

/**
 * [filters] with the asset-class condition set to [category], or removed when it is null.
 *
 * Pure, so the chip row's behaviour is a unit test rather than a screenshot. Removing rather than
 * writing an empty set: an empty [ScreenerFilter.Category] matches everything by design, but
 * leaving one in the list would show the reader a condition row in the sheet that does nothing.
 */
internal fun withCategory(
    filters: List<ScreenerFilter>,
    category: SymbolCategory?,
): List<ScreenerFilter> {
    val without = filters.filterNot {
        it is ScreenerFilter.Category && it.field == ScreenerField.ASSET_CLASS
    }
    return if (category == null) {
        without
    } else {
        without + ScreenerFilter.Category(ScreenerField.ASSET_CLASS, setOf(category.name))
    }
}

/** The ticker column. Wide enough for a Persian name under a ticker without cutting either. */
private val SYMBOL_COLUMN = 96.dp

/** One figure column. Matches the markets list's price column so the two screens align. */
private val FIGURE_COLUMN = 88.dp
