package com.coinepro.feature.screener

import com.coinepro.core.designsystem.coineProHorizontalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.coinepro.core.designsystem.CoineProToggleChip
import com.coinepro.core.designsystem.CoineProWindowSize
import com.coinepro.core.designsystem.LocalToaster
import com.coinepro.core.designsystem.ToastTone
import com.coinepro.core.designsystem.coineProWindowClass
import com.coinepro.core.symbols.SymbolMeta
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
@OptIn(ExperimentalFoundationApi::class)
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
    val toaster = LocalToaster.current
    val savedMessage = stringResource(R.string.screener_export_saved)
    val failedMessage = stringResource(R.string.screener_export_failed)
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.Default) { controller.csv(english).toByteArray(Charsets.UTF_8) }
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream")
                }.isSuccess
            }
            // A toast that names the file and goes away (LISTS-28), not a permanent «Saved» line
            // that pushed the whole table down fifteen points and never left.
            toaster.show(
                if (saved) savedMessage else failedMessage,
                if (saved) ToastTone.SUCCESS else ToastTone.FAILURE,
            )
        }
    }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // One scroll position shared by the heading strip and every row's value strip. Two states would
    // let the headings drift out of line with the numbers under them, which is worse than no
    // headings at all.
    val valuesScroll = rememberScrollState()
    val dense = coineProWindowClass().width == CoineProWindowSize.EXPANDED

    DisposableEffect(controller) {
        controller.start()
        onDispose(controller::stop)
    }

    // The quote poll's entire subscription. See ScreenerController: the rows below the fold cost
    // nothing until they are scrolled to, and this is the line that makes that true. The chrome now
    // scrolls with the rows (LISTS-09), so only the items that *are* rows are counted, by their
    // content type, and their index is taken back to the row list's own.
    val rowIndex = rememberUpdatedState(
        remember(state.rows) { state.rows.withIndex().associate { (index, row) -> row.symbol to index } },
    )
    LaunchedEffect(listState, controller) {
        snapshotFlow {
            val rows = listState.layoutInfo.visibleItemsInfo.filter { it.contentType == ROW_TYPE }
            val first = rows.firstOrNull()?.let { rowIndex.value[it.key] }
            val last = rows.lastOrNull()?.let { rowIndex.value[it.key] }
            if (first == null || last == null) null else first to last
        }
            .distinctUntilChanged()
            .collect { window -> window?.let { (first, last) -> controller.setVisible(first, last) } }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(CoineProColors.Stage)) {
        // **A real table where there is room** (LISTS-02): past this width every quote column the
        // day's figures answer is shown, at a figure width that needs no sideways strip, and the
        // ticker column takes what is left — the reference's screener, not a phone table in a pane.
        val wide = maxWidth >= WIDE_TABLE
        val columns = remember(state.columns, wide) { displayColumns(state.columns, wide) }
        val figure = if (wide) WIDE_FIGURE_COLUMN else FIGURE_COLUMN
        val logo = if (dense) DENSE_LOGO else LOGO
        val figureCount = columns.size + state.indicatorColumns.size
        val figuresWidth = figure * figureCount + CoineProSpacing.One * (figureCount - 1).coerceAtLeast(0)
        val symbolWidth = if (wide) {
            (maxWidth - CoineProSpacing.Two * 2 - logo - CoineProSpacing.One * 2 - figuresWidth)
                .coerceIn(SYMBOL_COLUMN, WIDE_SYMBOL_MAX)
        } else {
            SYMBOL_COLUMN
        }
        val layout = TableLayout(
            logo = logo,
            symbol = symbolWidth,
            figure = figure,
            dense = dense,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = CoineProSpacing.Two),
        ) {
            // Everything above the headings scrolls away with the page (LISTS-09). Pinned, it was
            // four hundred points of a phone's eight hundred before the first market.
            item(key = "h:header") {
                Header(
                    onOpenFilters = { sheetOpen = true },
                    filtersEnabled = state.mode == ScreenerMode.TABLE,
                    onExport = { exporter.launch(EXPORT_NAME) }.takeIf { state.rows.isNotEmpty() },
                )
            }
            item(key = "h:teach") { CoineProTeachingStrip(TeachingSurface.SCREENER) }
            item(key = "h:modes") { ModeChips(selected = state.mode, onSelect = controller::setMode, compact = !dense) }
            item(key = "h:frames") {
                TimeframeChips(selected = state.timeframe, onSelect = controller::setTimeframe, compact = !dense)
            }
            if (state.mode == ScreenerMode.SIGNALS) {
                item(key = "h:scan") {
                    ScanControls(
                        state = state,
                        english = english,
                        compact = !dense,
                        onSetIds = controller::setScanIds,
                        onSetWithin = controller::setScanWithin,
                        onSetMinGrowth = controller::setMinGrowth,
                        onToggleWatch = controller::toggleWatch,
                    )
                }
            }
            item(key = "h:categories") {
                CategoryChips(
                    selected = selectedCategory(state.filters),
                    onSelect = { category -> controller.setFilters(withCategory(state.filters, category)) },
                    compact = !dense,
                )
            }
            item(key = "h:count") { ResultCount(state) }
            if (onAddToWatchlist != null && state.rows.isNotEmpty()) {
                item(key = "h:add") {
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
                            .padding(bottom = CoineProSpacing.One)
                            .semantics { contentDescription = "screener-add-to-watchlist" },
                    )
                }
            }
            // The one piece of chrome that stays: the column headings, on the stage colour with a
            // rule under them, as the reference's table keeps its own.
            stickyHeader(key = "h:columns") {
                ColumnHeadings(
                    columns = columns,
                    indicatorColumns = state.indicatorColumns,
                    sort = state.sort,
                    scroll = valuesScroll,
                    english = english,
                    layout = layout,
                    scrolls = !wide,
                    onSort = controller::toggleSort,
                    onSortIndicator = controller::toggleIndicatorSort,
                )
            }

            when {
                state.loading && state.rows.isEmpty() -> item(key = "h:skeleton") {
                    CoineProSkeletonRows(
                        count = 8,
                        modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
                    )
                }

                // A failure is not an empty result, and the two must not share copy. The markets list
                // shipped for a release telling readers on a dead connection that no market matched.
                state.error != null && state.rows.isEmpty() -> item(key = "h:error") {
                    Centred {
                        CoineProEmptyState(
                            icon = CoineProIcons.Warning,
                            message = state.error?.resolve() ?: stringResource(R.string.screener_failed),
                            action = stringResource(R.string.screener_retry),
                            onAction = controller::refresh,
                        )
                    }
                }

                state.rows.isEmpty() -> item(key = "h:empty") {
                    Centred {
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
                }

                else -> items(state.rows, key = ScreenerRow::symbol, contentType = { ROW_TYPE }) { row ->
                    Column(modifier = rowMotion().fillMaxWidth()) {
                        val tags = if (state.mode == ScreenerMode.SIGNALS) {
                            ScreenerScanTags.of(row, state.scanWithin)
                        } else {
                            emptyList()
                        }
                        ScreenerTableRow(
                            row = row,
                            columns = columns,
                            indicatorColumns = state.indicatorColumns,
                            scroll = valuesScroll,
                            layout = layout,
                            scrolls = !wide,
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
                        // Full-bleed, the same box the row's hover fills (MOBILE-30).
                        HorizontalDivider(thickness = 1.dp, color = CoineProColors.BorderSubtle)
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

/** The measurements one table is drawn with, shared by the headings and every row. */
@Immutable
internal data class TableLayout(
    val logo: Dp,
    val symbol: Dp,
    val figure: Dp,
    /** The single-line desktop row (LISTS-05). */
    val dense: Boolean,
)

/**
 * The columns a table of this width shows (LISTS-02).
 *
 * The reader's own choice on a phone. On a wide table, every quote column the day's figures answer
 * is added after it, in the enum's order — price, move, change, volume, turnover, high, low, range,
 * distance from the high and the low — because the reference fills a desktop with columns, and
 * three figures across a thousand points is a strip with a hole beside it. Nothing derived is
 * added: an RSI column costs a candle series per market, and that stays the reader's to ask for.
 */
internal fun displayColumns(chosen: List<ScreenerField>, wide: Boolean): List<ScreenerField> {
    if (!wide) return chosen
    val quote = ScreenerField.entries.filter { it.isNumeric && !it.isDerived }
    return chosen + quote.filterNot { it in chosen }
}

/**
 * The title and the one action.
 *
 * A single button, labelled, because the funnel glyph alone is the sort of icon a reader has to
 * learn. The label is two words and the row has space for it. Both controls are thirty-two points
 * tall, the height of the chip rows under them, so the header reads as one band of controls rather
 * than two large pills over a strip of small ones (LISTS-19).
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
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Text(
            text = stringResource(R.string.screener_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        if (onExport != null) {
            PillButton(
                text = stringResource(R.string.screener_export_csv),
                onClick = onExport,
                modifier = Modifier.semantics { contentDescription = "screener-export" },
            )
        }
        if (filtersEnabled) {
            PillButton(
                text = stringResource(R.string.screener_open_filters),
                onClick = onOpenFilters,
                icon = CoineProIcons.Filter,
            )
        }
    }
}

/** A thirty-two point outlined pill: the screener header's two actions. See [Header]. */
@Composable
private fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: Int? = null) {
    val haptics = rememberCoineProHaptics()
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    Row(
        modifier = modifier
            .height(PILL_HEIGHT)
            .clip(CoineProShapes.small)
            .background(if (hovered) CoineProColors.SurfaceHover else CoineProColors.SurfaceElevated)
            .border(1.dp, CoineProColors.BorderSubtle, CoineProShapes.small)
            .clickable(interactionSource = hover, indication = null) {
                haptics.select()
                onClick()
            }
            .padding(horizontal = CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = CoineProColors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
            color = CoineProColors.TextPrimary,
            maxLines = 1,
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
private fun CategoryChips(selected: SymbolCategory?, onSelect: (SymbolCategory?) -> Unit, compact: Boolean) {
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
        compact = compact,
    )
}

/**
 * How many markets matched, how much of the catalogue is still being read, and how many markets
 * could not be judged at all.
 *
 * Persian digits, because this is prose: «۲۳ بازار» is read aloud as words with a number in it,
 * unlike the figures in the table below, which are held up against another terminal and stay Latin.
 *
 * **The progress line has a slot of its own** (LISTS-10), held whether or not it is drawn, so the
 * table does not jump fifteen points up when a scan finishes. It says what is happening — «در حال
 * بررسی ۱۲۰ از ۸۶۲» — rather than «۰ از ۸۶۲ بررسی شد» over a table already full of prices.
 *
 * The unknown line is this table's answer to the heat map's hatched tile, and it waits for the scan
 * to settle: while markets are still being read, «no figure» is not yet true of any of them.
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
        Box(modifier = Modifier.height(PROGRESS_SLOT)) {
            if (state.resolving && state.universeSize > 0) {
                Text(
                    text = stringResource(
                        R.string.screener_progress,
                        state.readCount.coerceAtMost(state.universeSize).proseDigits(),
                        state.universeSize.proseDigits(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
        if (state.unknownCount > 0 && !state.resolving) {
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
 * Every heading is a tap target, the whole cell tall, and the sorted one carries an arrow in front
 * of its word in the label's own ink — `↓ Last price` — the same sort vocabulary the watchlist and
 * the markets list use (LISTS-15). Tapping it again flips the direction; tapping another moves the
 * sort and starts descending, which is what somebody who just chose «حجم» means.
 */
@Composable
private fun ColumnHeadings(
    columns: List<ScreenerField>,
    indicatorColumns: List<ScreenerIndicatorColumn>,
    sort: ScreenerSort,
    scroll: ScrollState,
    english: Boolean,
    layout: TableLayout,
    scrolls: Boolean,
    onSort: (ScreenerField) -> Unit,
    onSortIndicator: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(CoineProColors.Stage)) {
        HorizontalDivider(thickness = 1.dp, color = CoineProColors.BorderSubtle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = HEADING_HEIGHT)
                .padding(horizontal = CoineProSpacing.Two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The heading spans the logo as well as the ticker, so «نماد» sits over the whole first
            // column rather than three points to the left of where the tickers start — and the gap
            // after it, the same gap the rows keep between the ticker and the first figure (LISTS-12).
            Text(
                text = stringResource(R.string.screener_column_symbol),
                style = HeadingStyle(),
                color = CoineProColors.TextMuted,
                maxLines = 1,
                modifier = Modifier.width(layout.logo + CoineProSpacing.One + layout.symbol + CoineProSpacing.One),
            )
            FigureStrip(scroll = scroll, scrolls = scrolls) {
                columns.forEach { column ->
                    Heading(
                        label = column.labelIn(english),
                        width = layout.figure,
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
                        width = layout.figure,
                        sorted = sort.indicatorKey == column.key,
                        descending = sort.descending,
                        onClick = { onSortIndicator(column.key) },
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = CoineProColors.BorderSubtle)
    }
}

/** The heading ink and size: the reference's thirteen-pixel grey, a step under the figures. */
@Composable
private fun HeadingStyle() = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal)

/**
 * One column heading: a tap target the height of the header row, marked with an arrow when the
 * table is ordered by it.
 *
 * **Right, on the absolute axis** (LISTS-04). The figures under it are `TextAlign.Right` in both
 * directions; a heading laid out with `Arrangement.End` hugged the *left* of its cell in Persian,
 * twenty-eight points off the numbers it names.
 */
@Composable
private fun Heading(label: String, width: Dp, sorted: Boolean, descending: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(width)
            .heightIn(min = HEADING_HEIGHT)
            .clip(CoineProShapes.extraSmall)
            .clickable(onClick = onClick),
        contentAlignment = AbsoluteAlignment.CenterRight,
    ) {
        Text(
            text = when {
                !sorted -> label
                descending -> "↓ $label"
                else -> "↑ $label"
            },
            style = HeadingStyle(),
            color = if (sorted) CoineProColors.TextPrimary else CoineProColors.TextMuted,
            textAlign = TextAlign.Right,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The figure block, shared by the headings and the rows.
 *
 * On a narrow table it scrolls sideways, all rows and the headings as one strip, with a fade on the
 * side there is more to see (LISTS-03) — a strip with nothing to say it scrolls read as a column of
 * «–» with no heading. On a wide one it is a plain row: every column fits.
 */
@Composable
private fun RowScope.FigureStrip(scroll: ScrollState, scrolls: Boolean, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .weight(1f)
            .then(if (scrolls) Modifier.edgeFade(scroll).coineProHorizontalScroll(scroll) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * A sixteen-point fade on the end of a scrolled strip while there is more beyond it (LISTS-03).
 *
 * The end in the reading direction: in Persian the strip starts at the right and what is hidden
 * lies to the left. Four flat steps of the stage colour rather than a gradient brush — the motion
 * policy keeps gradients off controls, and at sixteen points the steps read as one fade.
 */
@Composable
private fun Modifier.edgeFade(scroll: ScrollState): Modifier {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val stage = CoineProColors.Stage
    return drawWithContent {
        drawContent()
        if (scroll.canScrollForward) {
            val step = EDGE_FADE.toPx() / FADE_STEPS
            for (index in 0 until FADE_STEPS) {
                // The outermost step is the most opaque.
                val alpha = (FADE_STEPS - index).toFloat() / (FADE_STEPS + 1)
                val x = if (rtl) index * step else size.width - (index + 1) * step
                drawRect(
                    color = stage.copy(alpha = alpha),
                    topLeft = androidx.compose.ui.geometry.Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(step, size.height),
                )
            }
        }
    }
}

private const val FADE_STEPS = 4

/**
 * One market.
 *
 * Fifty-eight points on a phone, forty on a desktop where the name sits beside the ticker on one
 * line (LISTS-05) — so a screenful is a screenful of markets rather than of padding. The height is
 * fixed whether or not the figures have arrived, because a table that grows as its numbers land
 * moves the row out from under the reader's thumb.
 */
@Composable
private fun ScreenerTableRow(
    row: ScreenerRow,
    columns: List<ScreenerField>,
    indicatorColumns: List<ScreenerIndicatorColumn>,
    scroll: ScrollState,
    layout: TableLayout,
    scrolls: Boolean,
    tags: List<Pair<GrowthScan.Kind, Int>> = emptyList(),
    english: Boolean = false,
    onClick: () -> Unit,
) {
    val haptics = rememberCoineProHaptics()
    val hover = remember { MutableInteractionSource() }
    val hovered by hover.collectIsHoveredAsState()
    val pressed by hover.collectIsPressedAsState()
    val name = screenerRowName(row.meta, row.meta.localRowName())
    val tag = tags.firstOrNull()?.let { (kind, ago) ->
        scanTag(kind, ago, english) + if (tags.size > 1) " +" + (tags.size - 1).proseDigits() else ""
    }
    val ticker = if (layout.dense) DenseTicker() else MaterialTheme.typography.labelMedium
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = if (layout.dense && tag == null) DENSE_ROW else ROW_HEIGHT)
            // The palette's hover plate, not Material's eight per cent (LISTS-17).
            .background(
                when {
                    pressed -> CoineProColors.SurfacePressed
                    hovered -> CoineProColors.SurfaceHover
                    else -> Color.Transparent
                },
            )
            // The tint a trader reads before any figure: which rows are moving.
            .coineProPriceFlash(row.price)
            .clickable(interactionSource = hover, indication = null) {
                haptics.select()
                onClick()
            }
            .padding(horizontal = CoineProSpacing.Two, vertical = if (layout.dense) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // **The mark, which every other list in this app has and this one did not.**
        //
        // A screener is the surface a reader scans fastest — sixty rows, looking for one — and it
        // was the one list where the only thing to recognise was a Latin ticker in the same weight
        // as the fifty-nine above it. A logo is read before a word is: it is what turns "read every
        // row" into "find the orange disc".
        CoineProAssetLogo(symbol = row.meta.symbol, size = layout.logo)
        Spacer(modifier = Modifier.width(CoineProSpacing.One))
        Column(modifier = Modifier.width(layout.symbol)) {
            val tickerText: @Composable () -> Unit = {
                Text(
                    text = row.meta.symbol,
                    // Forced left-to-right: a ticker is Latin and a right-to-left paragraph would
                    // reorder a symbol that happens to end in a digit.
                    style = ticker.copy(textDirection = TextDirection.Ltr),
                    color = CoineProColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (layout.dense) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tickerText()
                    if (name != null) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                            color = CoineProColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                tickerText()
                // Only where it says something the ticker does not (LISTS-24): «BREW» under
                // «BREWUSDT» was the ticker again in a quieter ink.
                if (name != null) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            tag?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.MarketUp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // The gap the watchlist keeps between the ticker column and the figures (LISTS-12): without
        // it «0.003980» touched «MEMESTOCKU…».
        Spacer(modifier = Modifier.width(CoineProSpacing.One))
        FigureStrip(scroll = scroll, scrolls = scrolls) {
            columns.forEach { column ->
                Figure(
                    text = row.textOf(column) ?: ScreenerFormat.cell(row.valueOf(column), column.unit),
                    unit = column.unit,
                    value = row.valueOf(column),
                    width = layout.figure,
                    dense = layout.dense,
                )
            }
            indicatorColumns.forEach { column ->
                val value = column.valueOf(row)
                if (column.key == GrowthScan.GROWTH_ID) {
                    GrowthFigure(value, width = layout.figure)
                } else {
                    Figure(
                        text = ScreenerFormat.cell(value, column.unit),
                        unit = column.unit,
                        value = value,
                        width = layout.figure,
                        dense = layout.dense,
                    )
                }
            }
        }
    }
}

/** The desktop ticker and figure: thirteen points, a step up from the phone's twelve (LISTS-18). */
@Composable
private fun DenseTicker() = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp)

/**
 * The name beside a screener row's ticker, or null where it would only repeat it (LISTS-24).
 */
internal fun screenerRowName(meta: SymbolMeta, name: String): String? {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return null
    val same = trimmed.equals(meta.base, ignoreCase = true) ||
        trimmed.equals(meta.symbol, ignoreCase = true) ||
        trimmed.equals(meta.pretty, ignoreCase = true)
    return if (same) null else trimmed
}

/**
 * One figure in the value strip.
 *
 * A percentage is tinted by its sign and nothing else is, which is the rule the markets list
 * follows: colour on a price column would say something about a number that has no direction.
 */
@Composable
private fun Figure(text: String, unit: ScreenerUnit, value: Double?, width: Dp, dense: Boolean) {
    val ink = when {
        value == null -> CoineProColors.TextDisabled
        unit != ScreenerUnit.PERCENT -> CoineProColors.TextPrimary
        // Movement, not execution. See `CoineProColors.MarketUp`.
        value > 0.0 -> CoineProColors.MarketUp
        value < 0.0 -> CoineProColors.MarketDown
        else -> CoineProColors.TextMuted
    }
    Text(
        text = text,
        style = (if (dense) DenseTicker() else MaterialTheme.typography.labelMedium)
            .copy(textDirection = TextDirection.Ltr),
        color = if (unit == ScreenerUnit.TEXT) CoineProColors.TextSecondary else ink,
        modifier = Modifier.width(width),
        // Right, not End. End would mirror with the layout direction and put the decimal points of
        // a Persian screen on the wrong side of the column, which is the one thing a table of
        // figures cannot survive.
        textAlign = TextAlign.Right,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
    )
}

/** An empty or failed table, in a box the height of a few rows: a lazy list has no weight to give. */
@Composable
private fun Centred(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().heightIn(min = EMPTY_HEIGHT).padding(CoineProSpacing.Two),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The two faces of the screener. */
@Composable
private fun ModeChips(selected: ScreenerMode, onSelect: (ScreenerMode) -> Unit, compact: Boolean) {
    CoineProChipRow(
        options = listOf(
            CoineProChip(ScreenerMode.TABLE.name, stringResource(R.string.screener_mode_table)),
            CoineProChip(ScreenerMode.SIGNALS.name, stringResource(R.string.screener_mode_signals)),
        ),
        selectedId = selected.name,
        onSelect = { id -> ScreenerMode.entries.firstOrNull { it.name == id }?.let(onSelect) },
        compact = compact,
    )
}

/**
 * The interval every indicator and setup is read on. The day's figures — the move, the range, the
 * volume — stay the day's whichever is chosen.
 */
@Composable
private fun TimeframeChips(selected: Timeframe, onSelect: (Timeframe) -> Unit, compact: Boolean) {
    CoineProChipRow(
        options = SCAN_TIMEFRAMES.map { CoineProChip(it.name, timeframeCode(it)) },
        selectedId = selected.name,
        onSelect = { id -> SCAN_TIMEFRAMES.firstOrNull { it.name == id }?.let(onSelect) },
        compact = compact,
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
    compact: Boolean,
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
                // The app's own chip, not Material's `FilterChip` (LISTS-19): one chip system on the
                // screen, not a second one with its own height, radius and outline.
                CoineProToggleChip(
                    label = if (english) kind.labelEn else kind.label,
                    selected = on,
                    onClick = { onSetIds(if (on) state.scanIds - kind.id else state.scanIds + kind.id) },
                    compact = compact,
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
            compact = compact,
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
            compact = compact,
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
private fun GrowthFigure(value: Double?, width: Dp) {
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
        modifier = Modifier.width(width),
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

/** The instrument's mark, at the size every other list in this app draws it. */
private val LOGO = 26.dp

/** The mark on a single-line desktop row. */
private val DENSE_LOGO = 20.dp

/** The ticker column. Wide enough for a Persian name under a ticker without cutting either. */
private val SYMBOL_COLUMN = 96.dp

/** How wide the ticker column may grow on a wide table before the slack goes to the right edge. */
private val WIDE_SYMBOL_MAX = 360.dp

/**
 * One figure column on a phone.
 *
 * Seventy-four, so the default three fit a 412-point phone beside the ticker column with no strip
 * to scroll (LISTS-03): 16 + 26 + 8 + 96 + 8 in front, 3 × 74 + 2 × 8 after, and 16 of gutter is
 * 408. `104,532.45` at twelve points is about sixty-two.
 */
private val FIGURE_COLUMN = 74.dp

/** One figure column on a wide table: the reference's fixed numeric cell. */
private val WIDE_FIGURE_COLUMN = 96.dp

/** Where the table stops being a phone's and shows every quote column (LISTS-02). */
private val WIDE_TABLE = 900.dp

private val ROW_HEIGHT = 58.dp
private val DENSE_ROW = 40.dp
private val HEADING_HEIGHT = 32.dp
private val PILL_HEIGHT = 32.dp
private val PROGRESS_SLOT = 16.dp
private val EDGE_FADE = 16.dp
private val EMPTY_HEIGHT = 320.dp

/** The content type of a market row, which is how the visible window is told apart from chrome. */
private const val ROW_TYPE = "row"

private val SCAN_TIMEFRAMES = listOf(Timeframe.M15, Timeframe.H1, Timeframe.H4, Timeframe.D1, Timeframe.W1)
private val WITHIN_CHOICES = listOf(1, 3, 5, 10, 20)
private val GROWTH_CHOICES = listOf(50.0, 65.0, 80.0)
private const val STRONG_GROWTH = 65.0
private const val WEAK_GROWTH = 35.0
private const val CSV_MIME = "text/csv"
private const val EXPORT_NAME = "screener.csv"
