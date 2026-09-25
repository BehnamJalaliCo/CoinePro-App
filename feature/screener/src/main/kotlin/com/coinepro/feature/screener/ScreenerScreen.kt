package com.coinepro.feature.screener

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import com.coinepro.core.chart.GrowthScan
import com.coinepro.core.designsystem.inEnglish
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import com.coinepro.core.designsystem.proseDigits
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.localRowName
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTeachingStrip
import com.coinepro.core.designsystem.TeachingSurface
import com.coinepro.core.designsystem.coineProPriceFlash
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.designsystem.resolve
import com.coinepro.core.designsystem.rowMotion
import com.coinepro.core.designsystem.CoineProSkeletonRows
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
    /**
     * Every market the screen found, into the watchlist — TradingView's first-class «results to a
     * watchlist» flow (5.16.1). Null where the build has no watchlist to add to.
     */
    onAddToWatchlist: ((List<String>) -> Unit)? = null,
    /**
     * Opens a market with the studies that draw the setup it was listed for, on the interval it was
     * scanned on (5.17.0) — the chart the reader lands on shows why the market is on the list. Null
     * falls back to [onOpenSymbol].
     */
    onOpenSetup: ((symbol: String, studies: List<String>, timeframe: Timeframe) -> Unit)? = null,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val english = inEnglish()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val savedMessage = stringResource(R.string.screener_export_saved)
    val failedMessage = stringResource(R.string.screener_export_failed)
    var exportOutcome by remember { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.Default) { controller.csv(english).toByteArray(Charsets.UTF_8) }
            exportOutcome = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream")
                }.fold(onSuccess = { savedMessage }, onFailure = { failedMessage })
            }
        }
    }
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
        Header(
            onOpenFilters = { sheetOpen = true },
            filtersEnabled = state.mode == ScreenerMode.TABLE,
            onExport = { exporter.launch(EXPORT_NAME) }.takeIf { state.rows.isNotEmpty() },
        )
        exportOutcome?.let { outcome ->
            Text(
                text = outcome,
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                modifier = Modifier.padding(horizontal = CoineProSpacing.Two),
            )
        }
        CoineProTeachingStrip(TeachingSurface.SCREENER)
        ModeChips(selected = state.mode, onSelect = controller::setMode)
        TimeframeChips(selected = state.timeframe, onSelect = controller::setTimeframe)
        if (state.mode == ScreenerMode.SIGNALS) {
            ScanControls(
                state = state,
                english = english,
                onSetIds = controller::setScanIds,
                onSetWithin = controller::setScanWithin,
                onSetMinGrowth = controller::setMinGrowth,
                onToggleWatch = controller::toggleWatch,
            )
        }
        CategoryChips(
            selected = selectedCategory(state.filters),
            onSelect = { category -> controller.setFilters(withCategory(state.filters, category)) },
        )
        ResultCount(state)
        if (onAddToWatchlist != null && state.rows.isNotEmpty()) {
            var added by rememberSaveable(state.rows.size, state.filters) { mutableStateOf(false) }
            CoineProSecondaryButton(
                text = if (added) {
                    stringResource(R.string.screener_added_to_watchlist)
                } else {
                    stringResource(R.string.screener_add_to_watchlist, state.rows.size.proseDigits())
                },
                onClick = {
                    if (!added) onAddToWatchlist(state.rows.map(ScreenerRow::symbol))
                    added = true
                },
                modifier = Modifier
                    .padding(horizontal = CoineProSpacing.Two)
                    .semantics { contentDescription = "screener-add-to-watchlist" },
            )
        }
        ColumnHeadings(
            columns = state.columns,
            indicatorColumns = state.indicatorColumns,
            sort = state.sort,
            scroll = valuesScroll,
            english = english,
            onSort = controller::toggleSort,
            onSortIndicator = controller::toggleIndicatorSort,
        )

        when {
            state.loading && state.rows.isEmpty() -> CoineProSkeletonRows(
                count = 8,
                modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
            )

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
                    Column(modifier = rowMotion().fillMaxWidth()) {
                        val tags = if (state.mode == ScreenerMode.SIGNALS) {
                            ScreenerScanTags.of(row, state.scanWithin)
                        } else {
                            emptyList()
                        }
                        ScreenerTableRow(
                            row = row,
                            columns = state.columns,
                            indicatorColumns = state.indicatorColumns,
                            scroll = valuesScroll,
                            tags = tags,
                            english = english,
                            onClick = {
                                val open = onOpenSetup
                                if (state.mode == ScreenerMode.SIGNALS && open != null) {
                                    open(row.symbol, setupStudiesOf(tags), state.timeframe)
                                } else {
                                    onOpenSymbol(row.symbol)
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
private fun Header(onOpenFilters: () -> Unit, filtersEnabled: Boolean, onExport: (() -> Unit)?) {
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
        if (onExport != null) {
            CoineProSecondaryButton(
                text = stringResource(R.string.screener_export_csv),
                onClick = onExport,
                modifier = Modifier.semantics { contentDescription = "screener-export" },
            )
            Spacer(modifier = Modifier.width(CoineProSpacing.One))
        }
        if (filtersEnabled) {
            CoineProSecondaryButton(
                text = stringResource(R.string.screener_open_filters),
                onClick = onOpenFilters,
                icon = CoineProIcons.Filter,
            )
        }
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
 * How many markets matched, how much of the catalogue that answer is based on, and how many markets
 * could not be judged at all.
 *
 * Persian digits, because this is prose: «۲۳ بازار» is read aloud as words with a number in it,
 * unlike the figures in the table below, which are held up against another terminal and stay Latin.
 * The progress line appears only while figures are still arriving — a count that is still moving has
 * to say so, or a reader will take the first number they see as the answer.
 *
 * The third line is this table's answer to the heat map's hatched tile. A market with no figure for
 * one of the conditions is not a market that failed them; it is a market nothing is known about, and
 * dropping it into the same silence as a market that was measured and fell short would be the
 * screener editing somebody's list without saying so. It appears only when there is something to
 * report, so an ordinary screen with every figure in hand carries no extra line at all.
 */
@Composable
private fun ResultCount(state: ScreenerState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.One),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.screener_count, state.matchCount.proseDigits()),
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextSecondary,
        )
        if (state.resolving && state.universeSize > 0) {
            Text(
                text = stringResource(
                    R.string.screener_progress,
                    state.resolvedCount.proseDigits(),
                    state.universeSize.proseDigits(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
            )
        }
        if (state.unknownCount > 0) {
            Text(
                text = stringResource(R.string.screener_unknown, state.unknownCount.proseDigits()),
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
    english: Boolean,
    onSort: (ScreenerField) -> Unit,
    onSortIndicator: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The heading spans the logo as well as the ticker, so «نماد» sits over the whole first
        // column rather than three points to the left of where the tickers start.
        Text(
            text = stringResource(R.string.screener_column_symbol),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            modifier = Modifier.width(LOGO + CoineProSpacing.One + SYMBOL_COLUMN),
        )
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            columns.forEach { column ->
                Heading(
                    label = column.labelIn(english),
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
                    label = column.labelIn(english),
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
                modifier = Modifier.size(11.dp).padding(end = 4.dp),
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
    tags: List<Pair<GrowthScan.Kind, Int>> = emptyList(),
    english: Boolean = false,
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
            .padding(horizontal = CoineProSpacing.Two, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // **The mark, which every other list in this app has and this one did not.**
        //
        // A screener is the surface a reader scans fastest — sixty rows, looking for one — and it
        // was the one list where the only thing to recognise was a Latin ticker in the same weight
        // as the fifty-nine above it. A logo is read before a word is: it is what turns "read every
        // row" into "find the orange disc". The markets list, the watchlist and the chart's own
        // strip all carry it; a table without it does not look denser, it looks unfinished.
        CoineProAssetLogo(symbol = row.meta.symbol, size = LOGO)
        Spacer(modifier = Modifier.width(CoineProSpacing.One))
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
                text = row.meta.localRowName(),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            tags.firstOrNull()?.let { (kind, ago) ->
                Text(
                    text = scanTag(kind, ago, english) + if (tags.size > 1) " +" + (tags.size - 1).proseDigits() else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.MarketUp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
                if (column.key == GrowthScan.GROWTH_ID) {
                    GrowthFigure(value)
                } else {
                    Figure(
                        text = ScreenerFormat.cell(value, column.unit),
                        unit = column.unit,
                        value = value,
                    )
                }
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
        // Movement, not execution. See `CoineProColors.MarketUp`.
        (value ?: 0.0) > 0.0 -> CoineProColors.MarketUp
        (value ?: 0.0) < 0.0 -> CoineProColors.MarketDown
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

/** The two faces of the screener. */
@Composable
private fun ModeChips(selected: ScreenerMode, onSelect: (ScreenerMode) -> Unit) {
    CoineProChipRow(
        options = listOf(
            CoineProChip(ScreenerMode.TABLE.name, stringResource(R.string.screener_mode_table)),
            CoineProChip(ScreenerMode.SIGNALS.name, stringResource(R.string.screener_mode_signals)),
        ),
        selectedId = selected.name,
        onSelect = { id -> ScreenerMode.entries.firstOrNull { it.name == id }?.let(onSelect) },
        compact = true,
    )
}

/**
 * The interval every indicator and setup is read on. The day's figures — the move, the range, the
 * volume — stay the day's whichever is chosen.
 */
@Composable
private fun TimeframeChips(selected: Timeframe, onSelect: (Timeframe) -> Unit) {
    CoineProChipRow(
        options = SCAN_TIMEFRAMES.map { CoineProChip(it.name, timeframeCode(it)) },
        selectedId = selected.name,
        onSelect = { id -> SCAN_TIMEFRAMES.firstOrNull { it.name == id }?.let(onSelect) },
        compact = true,
    )
}

/**
 * The growth scan's controls: which setups, how recent, and how high a score (5.17.0).
 *
 * Setups are a multi-choice row — «any of these» — because the question is «what is starting to
 * move», and a reader who ticks breakout and trend start wants both kinds of answer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScanControls(
    state: ScreenerState,
    english: Boolean,
    onSetIds: (Set<String>) -> Unit,
    onSetWithin: (Int) -> Unit,
    onSetMinGrowth: (Double?) -> Unit,
    onToggleWatch: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = stringResource(R.string.screener_scan_setups),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            GrowthScan.Kind.BULLISH.forEach { kind ->
                val on = kind.id in state.scanIds
                FilterChip(
                    selected = on,
                    onClick = { onSetIds(if (on) state.scanIds - kind.id else state.scanIds + kind.id) },
                    label = { Text(if (english) kind.labelEn else kind.label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.semantics { contentDescription = "scan-" + kind.id },
                )
            }
        }
        Text(
            text = stringResource(R.string.screener_scan_within),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        CoineProChipRow(
            options = WITHIN_CHOICES.map {
                CoineProChip(it.toString(), stringResource(R.string.screener_scan_within_bars, it.proseDigits()))
            },
            selectedId = state.scanWithin.toString(),
            onSelect = { id -> id?.toIntOrNull()?.let(onSetWithin) },
            compact = true,
        )
        Text(
            text = stringResource(R.string.screener_scan_min_growth),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        CoineProChipRow(
            options = GROWTH_CHOICES.map { CoineProChip(it.toInt().toString(), "≥ " + it.toInt()) },
            selectedId = state.minGrowth?.toInt()?.toString(),
            onSelect = { id -> onSetMinGrowth(id?.toDoubleOrNull()) },
            allLabel = stringResource(R.string.screener_scan_min_any),
            compact = true,
        )
        // TradingView's screener alerts: told when a market enters this scan, in the background.
        CoineProSecondaryButton(
            text = stringResource(
                if (state.currentWatch != null) R.string.screener_scan_watching else R.string.screener_scan_watch,
            ),
            onClick = onToggleWatch,
            icon = CoineProIcons.Bell,
            modifier = Modifier.semantics { contentDescription = "screener-scan-watch" },
        )
    }
}

/** The growth score, tinted by where it sits: the colour is the reading, the number its detail. */
@Composable
private fun GrowthFigure(value: Double?) {
    val ink = when {
        value == null -> CoineProColors.TextMuted
        value >= STRONG_GROWTH -> CoineProColors.MarketUp
        value <= WEAK_GROWTH -> CoineProColors.MarketDown
        else -> CoineProColors.TextPrimary
    }
    Text(
        text = value?.let { it.toInt().toString() } ?: ScreenerFormat.cell(null, ScreenerUnit.PLAIN),
        style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
        color = ink,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.width(FIGURE_COLUMN),
        textAlign = TextAlign.Right,
        maxLines = 1,
    )
}

/** «Breakout · 2 bars ago», or «· this bar» for the newest. Persian digits: a count in prose. */
@Composable
private fun scanTag(kind: GrowthScan.Kind, ago: Int, english: Boolean): String {
    val name = if (english) kind.labelEn else kind.label
    return if (ago == 0) {
        stringResource(R.string.screener_scan_tag_now, name)
    } else {
        stringResource(R.string.screener_scan_tag_ago, name, ago.proseDigits())
    }
}

/** The studies that draw a row's setups, or the trend's where it has none fresh. */
internal fun setupStudiesOf(tags: List<Pair<GrowthScan.Kind, Int>>): List<String> =
    tags.flatMap { it.first.studies }.distinct().ifEmpty { GrowthScan.Kind.TREND_START.studies }

/** The interval's own short code, the one the chart's strip prints. Latin: a code, not prose. */
private fun timeframeCode(timeframe: Timeframe): String = when (timeframe) {
    Timeframe.M15 -> "15m"
    Timeframe.H1 -> "1H"
    Timeframe.H4 -> "4H"
    Timeframe.D1 -> "1D"
    Timeframe.W1 -> "1W"
    else -> timeframe.wire
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
/** The instrument's mark, at the size every other list in this app draws it. */
private val LOGO = 26.dp

private val SYMBOL_COLUMN = 96.dp

/** One figure column. Matches the markets list's price column so the two screens align. */
private val FIGURE_COLUMN = 88.dp

private val SCAN_TIMEFRAMES = listOf(Timeframe.M15, Timeframe.H1, Timeframe.H4, Timeframe.D1, Timeframe.W1)
private val WITHIN_CHOICES = listOf(1, 3, 5, 10, 20)
private val GROWTH_CHOICES = listOf(50.0, 65.0, 80.0)
private const val STRONG_GROWTH = 65.0
private const val WEAK_GROWTH = 35.0
private const val CSV_MIME = "text/csv"
private const val EXPORT_NAME = "screener.csv"
