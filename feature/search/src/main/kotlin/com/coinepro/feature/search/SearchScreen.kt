package com.coinepro.feature.search

import com.coinepro.core.designsystem.CoineProLazyRow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.coinepro.core.designsystem.CONTENT_MAX_WIDTH
import com.coinepro.core.designsystem.pageAccentInk
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.coineProControl
import com.coinepro.core.marketdata.SymbolExpression
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.inEnglish
import com.coinepro.core.designsystem.localName
import com.coinepro.core.designsystem.localRowName
import com.coinepro.core.designsystem.CoineProSkeletonRows
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProMarketRow
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTeachingStrip
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.TeachingSurface
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.designsystem.resolve
import com.coinepro.core.designsystem.rowMotion
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.datastore.RecentSearchStore
import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.marketdata.SparklineStore
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.symbols.MarketHours
import com.coinepro.core.symbols.MatchField
import com.coinepro.core.symbols.SymbolCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Search across everything the active platform quotes.
 *
 * The screen is deliberately one column of rows and nothing else. A market search is a place people
 * arrive with a name in mind and leave a second later, so every element here has to earn its space
 * against that: the field, the category chips, the recent list when the field is empty, and the
 * results. There is no banner, no promotion and no "trending" strip.
 *
 * The reason the row shows both a Persian name and a Latin ticker is that both are how people
 * identify a market, and which one they use is not predictable — the same trader says «طلا» and
 * «XAUUSD» in the same sentence. The matched part is marked so it is obvious *why* a row is here,
 * which matters most for the rows a fuzzy match found.
 *
 * ### It searches the app as well as the market list
 *
 * Everything above was true of a field that could only ever answer with a market, in an app with
 * thirty-odd feature modules — so a reader who had not been shown the academy, the journal, the
 * screener or the backtest had no way to ask for them. [AppSurfaces] is the answer: a static
 * catalogue of sections with keywords in both languages, matched on the same keystroke and drawn
 * **above** the markets in a section of its own.
 *
 * Above, and clearly headed, because the two are different kinds of thing and a reader must never
 * have to work out which one they are looking at. It is safe to put them first because a section
 * only ever appears on a contiguous keyword hit — see [AppSurfaceSearch] — so «BTC» never produces
 * one, and «ژورنال» produces exactly the one it names.
 */
@Composable
fun SearchScreen(
    controller: MarketSearchController,
    /**
     * Opens the chart for a row. Null leaves the list inert.
     *
     * Nullable rather than a no-op default: a row that responds to a tap by doing nothing reads as
     * a broken screen, and the caller is the only thing that knows whether there is a chart to open.
     */
    onOpenSymbol: ((String) -> Unit)? = null,
    /** The reader's own list, so a row can show whether it is on it. */
    watchlist: List<String> = emptyList(),
    onToggleWatch: ((String) -> Unit)? = null,
    /**
     * Which of the app's own sections this reader can reach. Null leaves the field a market search.
     *
     * Nullable rather than defaulted to something plausible, for the reason [onOpenSymbol] is: a
     * default would be a guess about a platform and a session, and guessing wrong here means
     * offering somebody a screen that answers 401.
     */
    access: SurfaceAccess? = null,
    /** Opens one of [AppSurfaces] by its id. Null draws no sections even when [access] is given. */
    onOpenSurface: ((String) -> Unit)? = null,
    /**
     * Where a section that needs an account sends a guest. Null leaves the row inert but still
     * legible, which is the point of drawing it at all — the reader learns the screen exists.
     */
    onSignIn: (() -> Unit)? = null,
    /**
     * The day's shapes, read and never requested.
     *
     * The preview sheet draws whatever line this store already holds for a symbol; it does not ask
     * for one. A long press that opened a request would be the network cost the whole feature is
     * there to avoid — see [previewOf].
     */
    sparklines: SparklineStore? = null,
    /**
     * Arms an alert at the price the preview is showing. Null drops the action rather than
     * disabling it, and so does a market this feed has not quoted — see [MarketsScreen].
     */
    onCreateAlert: ((String, Double) -> Unit)? = null,
    /** The preview's span chips and scrub. Null keeps the short sheet — see [MarketsScreen]. */
    previewCandles: MarketPreviewCandles? = null,
    /**
     * Arms a milestone alert on today's move from the preview sheet (run Ω4).
     *
     * Null drops the chips rather than disabling them, for the reason every other nullable callback
     * on this screen is nullable: only the caller knows whether there is an alert store to write to.
     */
    onMilestoneAlert: ((symbol: String, up: Boolean, percent: Double) -> Unit)? = null,
    /**
     * The markets this reader opened from here before (F3).
     *
     * This screen's own documentation has promised «the recent list when the field is empty» since
     * it was written and there was no such list — an empty field showed the browse ranking, which is
     * the markets tab one tap away. Null keeps that behaviour, which is what the guest shell and
     * every render test get.
     */
    recentSearches: RecentSearchStore? = null,
    /**
     * Drawn as a dialog over the chart rather than as a page (DIALOGS-04): a heading with this
     * title and a close control, the field focused on open, and no teaching strip to overlap the
     * chips. Null is the page the phone opens.
     */
    dialogTitle: String? = null,
    /** Closes the dialog. Read only with [dialogTitle]. */
    onClose: (() -> Unit)? = null,
) {
    LaunchedEffect(controller) { controller.start() }
    val state by controller.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // A flow either way, so the collection is unconditional — the same rule the sparkline store
    // above follows, and for the same reason: a composable call on one branch of a condition is
    // what corrupts a slot table the day a caller passes a store where there was none.
    val recentSource = remember(recentSearches) { recentSearches?.recent() ?: MutableStateFlow(emptyList()) }
    val recent by recentSource.collectAsStateWithLifecycle(emptyList())
    // Opening a market from search is what puts it on the recent list. Wrapped here rather than at
    // every call site, so a row, a chip and the preview sheet's «چارت» all record the same thing.
    val openSymbol: ((String) -> Unit)? = onOpenSymbol?.let { open ->
        { symbol: String ->
            recentSearches?.let { store -> scope.launch { store.record(symbol) } }
            open(symbol)
        }
    }
    // Resolved to a flow first so the collection itself is unconditional. Reading the state inside
    // an `if` would put a composable call on one branch of a condition, which is the shape that
    // corrupts a slot table the day somebody passes a store where there was none.
    val lineSource = remember(sparklines) { sparklines?.lines ?: MutableStateFlow(emptyMap()) }
    val lines by lineSource.collectAsStateWithLifecycle()
    // Survives a rotation with the sheet open, so the reader is not dropped back to the list for
    // turning the phone. The symbol rather than the row: the row is re-derived from the live
    // results, so the price in the sheet ticks with the one in the list behind it.
    var preview by rememberSaveable { mutableStateOf<String?>(null) }

    // Recomputed only when the query or the reader's situation changes. The catalogue is twenty-odd
    // entries and the match is a substring test, so this is nowhere near the cost of ranking a
    // thousand markets — but it runs on every recomposition of a screen that recomposes on every
    // price tick, which is what `remember` is for.
    // Keyed on the two things that change the answer, and deliberately not on `onOpenSurface`: a
    // lambda written at the call site is a new object on every recomposition, so keying on it would
    // rerun the match on every price tick — which is exactly what `remember` was here to stop.
    val offersSurfaces = access != null && onOpenSurface != null
    val surfaces = remember(state.query, access, offersSurfaces) {
        if (access == null || !offersSurfaces) emptyList() else AppSurfaceSearch.search(state.query, access)
    }

    // A spread typed into the field (5.14.0) — see the row under the chips.
    val expression = remember(state.query) {
        state.query.takeIf { SymbolExpression.isExpression(it) }?.filterNot { it == ' ' }?.uppercase()
    }

    val dialog = dialogTitle != null
    val field = remember { FocusRequester() }
    if (dialog) LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
    // Capped and centred on a wide window, so a row's price is not fourteen hundred points from its
    // ticker on a desktop page (DIALOGS-04).
    Box(
        modifier = Modifier.fillMaxSize().background(if (dialog) CoineProColors.Surface else CoineProColors.Stage),
        contentAlignment = Alignment.TopCenter,
    ) {
    Column(
        modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxSize(),
    ) {
        if (dialog) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = CoineProSpacing.Gutter, end = CoineProSpacing.One, top = CoineProSpacing.One),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dialogTitle.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CoineProColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                if (onClose != null) {
                    androidx.compose.material3.IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(CoineProIcons.Close),
                            contentDescription = stringResource(R.string.search_close),
                            tint = CoineProColors.TextSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier.padding(
                horizontal = CoineProSpacing.Gutter,
                vertical = if (dialog) CoineProSpacing.One else CoineProSpacing.Two,
            ),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        ) {
            CoineProTextField(
                value = state.query,
                onValueChange = controller::setQuery,
                label = stringResource(R.string.search_field_label),
                modifier = Modifier.fillMaxWidth().focusRequester(field),
            )
            CategoryChips(selected = state.category, onSelect = controller::setCategory)
            if (!dialog) CoineProTeachingStrip(TeachingSurface.SEARCH, gutter = false)
            // **A spread or a ratio** (5.14.0). The terminal charted `EURUSD/GBPUSD`,
            // `XAUUSD*2` or `(BTCUSDT+ETHUSDT)/2` typed straight into its search, and so does this:
            // when the field holds arithmetic over symbols, the first row charts it. Straight to
            // the chart rather than through the recent list, whose chips carry each market's mark
            // and an expression has none.
            if (expression != null && onOpenSymbol != null) {
                SpreadRow(expression = expression, onOpen = { onOpenSymbol(expression) })
            }
        }

        when {
            state.loading && state.results.isEmpty() -> CoineProSkeletonRows(
                modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                count = 8,
            )

            state.error != null && state.results.isEmpty() -> Centered {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
                ) {
                    Text(
                        text = state.error?.resolve().orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.TextSecondary,
                    )
                    CoineProPrimaryButton(
                        text = stringResource(R.string.search_retry),
                        onClick = controller::refresh,
                    )
                }
            }

            // Not «nothing found» under a spread's own row: the row is what was found.
            state.empty && surfaces.isEmpty() && expression == null -> Centered {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                ) {
                    Text(
                        text = stringResource(R.string.search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.TextSecondary,
                    )
                    Text(
                        // Names the two things that do work, rather than only reporting the miss.
                        text = stringResource(R.string.search_empty_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                    )
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = CoineProSpacing.Four),
            ) {
                // How many markets there are. Worth its row: the answer used to be eight, and a
                // reader has no other way to tell that it is now the whole book.
                if (!state.searching) {
                    if (recent.isNotEmpty()) {
                        item(key = "__recent") {
                            RecentRow(
                                symbols = recent,
                                onOpen = { symbol -> openSymbol?.invoke(symbol) },
                                onClear = {
                                    recentSearches?.let { store -> scope.launch { store.clear() } }
                                },
                            )
                        }
                    }
                    item {
                        SectionHeader(
                            title = stringResource(R.string.search_count, state.catalogSize),
                        )
                    }
                }
                if (surfaces.isNotEmpty()) {
                    item(key = "__surfaces") {
                        SectionHeader(title = stringResource(R.string.search_surfaces_header))
                    }
                    items(surfaces, key = { "surface:" + it.surface.id }) { match ->
                        Column(modifier = rowMotion().fillMaxWidth()) {
                            SurfaceRow(
                                match = match,
                                onOpen = onOpenSurface,
                                onSignIn = onSignIn,
                            )
                        }
                    }
                    // The markets get a heading of their own only once something is above them.
                    // A single «بازارها» over the whole screen would be a label for a screen that
                    // is already called that.
                    item(key = "__markets") {
                        SectionHeader(title = stringResource(R.string.search_markets_header))
                    }
                }
                itemsIndexed(state.results, key = { _, row -> row.meta.symbol }) { index, row ->
                    // A hairline between rows, inset past the logo so it separates the text
                    // columns rather than cutting the artwork. Without it a long list of rows on a
                    // bare stage has nothing for the eye to count by, and sixty-five markets read
                    // as one block of text.
                    if (index > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = CoineProSpacing.Gutter, end = 64.dp)
                                .height(1.dp)
                                .background(CoineProColors.Border),
                        )
                    }
                    MarketRow(
                        row = row,
                        onOpenSymbol = openSymbol,
                        watchlist = watchlist,
                        onToggleWatch = onToggleWatch,
                        // Only where there is a chart to fall back to. A preview whose one
                        // full-size action goes nowhere is a sheet that ends in a dead end.
                        onLongClick = openSymbol?.let { { preview = row.meta.symbol } },
                    )
                }
            }
        }
    }
    }

    // Rebuilt from the live results on every frame the sheet is up, so the figure in it is the
    // same figure as the row behind it rather than a copy taken when the finger went down.
    preview
        ?.let { symbol -> state.results.firstOrNull { it.meta.symbol == symbol } }
        ?.let { row ->
            MarketPreviewSheet(
                state = previewOf(
                    row = row,
                    line = lines[row.meta.symbol.uppercase()].orEmpty(),
                    starred = row.meta.symbol in watchlist,
                    status = MarketHours.statusOf(row.meta),
                    english = inEnglish(),
                ),
                onDismiss = { preview = null },
                onOpenChart = {
                    preview = null
                    onOpenSymbol?.invoke(row.meta.symbol)
                },
                candles = previewCandles,
                onMilestoneAlert = onMilestoneAlert?.let { arm ->
                    { up, percent ->
                        preview = null
                        arm(row.meta.symbol, up, percent)
                    }
                },
                onToggleStar = onToggleWatch?.let { toggle -> { toggle(row.meta.symbol) } },
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

@Composable
private fun CategoryChips(selected: SymbolCategory?, onSelect: (SymbolCategory?) -> Unit) {
    // Null first and always present: "all" is a category to a reader even though it is the absence
    // of one to the filter.
    val options: List<Pair<SymbolCategory?, Int>> = listOf(
        null to R.string.search_category_all,
        SymbolCategory.CRYPTO to R.string.search_category_crypto,
        SymbolCategory.FOREX to R.string.search_category_forex,
        SymbolCategory.METAL to R.string.search_category_metal,
        SymbolCategory.INDEX to R.string.search_category_index,
        SymbolCategory.ENERGY to R.string.search_category_energy,
        SymbolCategory.OTHER to R.string.search_category_other,
    )
    // A count in prose, so Android's own formatting writes it in Persian digits under fa-IR —
    // market figures stay Latin, and this is not one.
    CoineProLazyRow(
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        // The row scrolls, so the last chip has to be able to clear the edge rather than sit
        // against it looking cut off.
        contentPadding = PaddingValues(end = CoineProSpacing.Two),
    ) {
        items(options) { (category, label) ->
            val active = category == selected
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                color = if (active) CoineProColors.OnAccent else CoineProColors.TextSecondary,
                modifier = Modifier
                    .clickable { onSelect(category) }
                    .background(
                        color = if (active) CoineProColors.Accent else Color.Transparent,
                        shape = CoineProPillShape,
                    )
                    .border(1.dp, CoineProColors.Border, CoineProPillShape)
                    .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
            )
        }
    }
}

/**
 * The markets this reader opened from here, newest first (F3).
 *
 * Chips rather than rows, and above the browse list rather than inside it. A recent market is a
 * shortcut, not a result: it has no rank, no price worth a column and no reason to be as tall as
 * the rows underneath it. Four of them fit across a phone, and the strip scrolls.
 */
@Composable
private fun RecentRow(symbols: List<String>, onOpen: (String) -> Unit, onClear: () -> Unit) {
    val haptics = rememberCoineProHaptics()
    Column(modifier = Modifier.padding(top = CoineProSpacing.One)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.search_recent),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.search_recent_clear),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextSecondary,
                modifier = Modifier
                    .clip(CoineProShapes.small)
                    .clickable {
                        haptics.commit()
                        onClear()
                    }
                    .padding(horizontal = CoineProSpacing.Half, vertical = CoineProSpacing.Half),
            )
        }
        CoineProLazyRow(
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            contentPadding = PaddingValues(
                start = CoineProSpacing.Gutter,
                end = CoineProSpacing.Gutter,
                top = CoineProSpacing.Half,
            ),
        ) {
            items(symbols, key = { it }) { symbol ->
                Row(
                    modifier = Modifier
                        .clip(CoineProPillShape)
                        .background(CoineProColors.SurfaceElevated)
                        .clickable {
                            haptics.select()
                            onOpen(symbol)
                        }
                        .padding(
                            start = CoineProSpacing.Half,
                            end = CoineProSpacing.One,
                            top = CoineProSpacing.Half,
                            bottom = CoineProSpacing.Half,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                ) {
                    CoineProAssetLogo(symbol = symbol, size = 20.dp)
                    Text(
                        text = symbol,
                        style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
                        color = CoineProColors.TextPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
    )
}

@Composable
private fun MarketRow(
    row: MarketSearchRow,
    onOpenSymbol: ((String) -> Unit)?,
    watchlist: List<String>,
    onToggleWatch: ((String) -> Unit)?,
    onLongClick: (() -> Unit)?,
) {
    val status = MarketHours.statusOf(row.meta)
    val quote = row.quote
    // A closed market is said to be closed rather than shown a stale percentage, and the weekend is
    // named separately from an unexplained close — one passes by Monday and the other does not.
    val closedNote = stringResource(
        if (status.weekend) R.string.search_weekend else R.string.search_closed,
    ).takeIf { !status.open }

    CoineProMarketRow(
        symbol = row.meta.symbol,
        title = highlighted(
            text = BidiText.isolateLtr(row.meta.pretty),
            range = row.highlight
                .takeIf { row.field !in NameFields }
                ?.intoPretty(row.meta.base?.length),
        ),
        // The long description only when the reader's own words are *in* it — the highlight range
        // is an offset into that exact string, so the short form would put the mark on the wrong
        // letters. Everywhere else the row shows what every other list shows: «بیت‌کوین/تتر» rather
        // than «بیت‌کوین (BTC)», whose parenthesis repeats the ticker on the line above it.
        //
        // A market carries two names since 4.71.0 and the catalogue is searched by both, so the
        // name that matched is not always the name this screen is drawing: a reader with the app in
        // English who types «طلا» hits the Persian name and reads `Gold / US Dollar`. The range
        // belongs to one exact string — [MatchField.DESCRIPTION_EN] versus [MatchField.DESCRIPTION]
        // is which one — so the long name is shown when the hit is in *this* language, and the hit
        // in the other language falls through to the short name with no mark rather than a mark on
        // the wrong letters.
        subtitle = if (row.field == if (inEnglish()) MatchField.DESCRIPTION_EN else MatchField.DESCRIPTION) {
            highlighted(text = row.meta.localName(), range = row.highlight)
        } else {
            highlighted(text = row.meta.localRowName(), range = null)
        },
        price = quote?.let { MarketNumberFormatter.price(it.price, it.decimals()) },
        changePercent = quote?.changePercent?.takeIf { status.open },
        // Closed, and otherwise nothing. A closed market explains a missing number and is worth a
        // word; an open one with no quote already shows a dash where the price goes, and a second
        // dash under the first says the same thing twice.
        trailingNote = closedNote,
        // The list sits on the stage rather than inside a card, so the pill's tint is computed
        // against the stage. Against the wrong ground it is a different colour by a few percent —
        // small, and visible the moment two lists sit next to each other in a review.
        starred = onToggleWatch?.let { row.meta.symbol in watchlist },
        onToggleStar = onToggleWatch?.let { toggle -> { toggle(row.meta.symbol) } },
        background = CoineProColors.Stage,
        horizontalPadding = CoineProSpacing.Gutter,
        onClick = onOpenSymbol?.let { open -> { open(row.meta.symbol) } },
        onLongClick = onLongClick,
    )
}

/**
 * The two fields whose range indexes a *name* rather than the ticker.
 *
 * A hit in either one means the mark belongs on the row's second line, so the ticker on the first
 * line is drawn plain — it was the Persian description alone before the English names landed, and
 * a set is how the two stay one idea in both places that ask.
 */
private val NameFields = setOf(MatchField.DESCRIPTION, MatchField.DESCRIPTION_EN)

/**
 * One of the app's own sections, in the same list as the markets and deliberately not in their
 * shape.
 *
 * A section is drawn as an icon, a name and a sentence — no logo, no figure column, no star. The
 * separation has to be visible without reading, because the one way this feature could make the
 * screen worse is by letting a reader take «ژورنال» for a market they had never heard of.
 *
 * A locked row is drawn and not hidden. The reader learns that the app has an academy and that it
 * wants an account, which is the entire value of putting sections in a search field; what it must
 * not do is open something that answers 401, so it either goes to sign-in or it goes nowhere.
 */
@Composable
private fun SurfaceRow(
    match: AppSurfaceMatch,
    onOpen: ((String) -> Unit)?,
    onSignIn: (() -> Unit)?,
) {
    val haptics = rememberCoineProHaptics()
    val open: (() -> Unit)? = when {
        !match.locked -> onOpen?.let { navigate -> { navigate(match.surface.id) } }
        else -> onSignIn
    }
    val ink = if (match.locked) CoineProColors.TextMuted else CoineProColors.TextPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                open?.let { action ->
                    Modifier.clickable {
                        haptics.select()
                        action()
                    }
                } ?: Modifier,
            )
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CoineProShapes.small)
                .background(CoineProColors.SurfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(match.surface.icon),
                contentDescription = null,
                tint = if (match.locked) CoineProColors.TextDisabled else CoineProColors.TextSecondary,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(match.surface.titleRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Normal,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // The reason it is locked replaces the description rather than sitting under it.
                // Two lines, one of which is an aspiration, is a row that buries its own answer.
                text = if (match.locked) {
                    stringResource(R.string.search_surface_locked)
                } else {
                    stringResource(match.surface.bodyRes)
                },
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(
                if (match.locked) CoineProIcons.Locked else CoineProIcons.ChevronForward,
            ),
            contentDescription = null,
            tint = CoineProColors.TextDisabled,
            // Decorative, so no tap target of its own: the row is the target, and it is already
            // well past the forty-four point floor at this height.
            modifier = Modifier.size(14.dp),
        )
    }
}


/**
 * Move a span measured on the bare ticker onto the slashed form shown in the row.
 *
 * Two shifts, both easy to miss and both visible the moment a match crosses the boundary. The
 * bidi isolate wrapping the text adds one character in front of everything, and the separator adds
 * another to any index past the base — so a hit on the `EUR` of `XAUEUR` would otherwise be drawn
 * over `/EU`.
 */
private fun IntRange.intoPretty(baseLength: Int?): IntRange {
    fun shift(index: Int) = index + 1 + if (baseLength != null && index >= baseLength) 1 else 0
    return shift(first)..shift(last)
}

/** The matched span in the accent colour, so a fuzzy hit explains itself. */
@Composable
private fun highlighted(text: String, range: IntRange?): AnnotatedString {
    if (range == null) return AnnotatedString(text)
    val start = range.first.coerceIn(0, text.length)
    val end = (range.last + 1).coerceIn(start, text.length)
    return buildAnnotatedString {
        append(text.substring(0, start))
        withStyle(SpanStyle(color = CoineProColors.Accent, fontWeight = FontWeight.Bold)) {
            append(text.substring(start, end))
        }
        append(text.substring(end))
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(CoineProSpacing.Four),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/**
 * How many decimals a price deserves.
 *
 * Not a fixed two. A metal trades in cents and a memecoin in millionths, and rounding either to the
 * other's precision makes the number wrong rather than merely ugly.
 */
private fun MarketQuote.decimals(): Int = when {
    price >= 1_000 -> 2
    price >= 1 -> 4
    else -> 6
}

/** The one row a typed expression gets: what will be charted, and how it is built. */
@Composable
private fun SpreadRow(expression: String, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CoineProShapes.medium)
            .background(CoineProColors.SurfaceElevated)
            .coineProControl(onClick = onOpen)
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.OneHalf)
            .semantics { contentDescription = "search-spread" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.tv_chart_line),
            contentDescription = null,
            tint = CoineProColors.pageAccentInk,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.search_spread_open, BidiText.isolateLtr(expression)),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.search_spread_caption),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        }
    }
}
