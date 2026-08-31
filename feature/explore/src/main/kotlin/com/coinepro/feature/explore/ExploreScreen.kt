package com.coinepro.feature.explore

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProErrorState
import com.coinepro.core.designsystem.CoineProHeaderAction
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProListHeader
import com.coinepro.core.designsystem.CoineProPercentPill
import com.coinepro.core.designsystem.CoineProProse
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProSparkline
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.marketdata.MarketTickerStore
import com.coinepro.core.marketdata.SparklineStore
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.marketintel.MarketNewsItem
import com.coinepro.core.symbols.SymbolCategory
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Where a reader who does not yet know what they are looking for starts.
 *
 * ### What this screen is for, and what it is not
 *
 * Every other market surface in the app answers a question the reader already has. The markets tab
 * answers "what is BTC doing"; search answers "where is gold"; the screener answers "which markets
 * are oversold". None of them answers "what is going on today", and that is the question somebody
 * opens a trading app with before they have a position. This screen is that answer, and it is
 * deliberately shallow: three doors, one row of markets, five headlines. Everything on it is a way
 * *into* a screen that goes deeper, and nothing on it is a screen a reader should stay on.
 *
 * ### Every figure here comes from somewhere the app already reads
 *
 * There is no fixture and no second source anywhere on this surface:
 *
 * * **The cards** are [MarketSearchController]'s catalogue — the same one the markets tab lists and
 *   search searches, from `MarketCatalogGateway`, already filtered through `SymbolArtwork.covers`.
 * * **The prices and the day's move** are [MarketTickerStore]'s rollup where the platform serves
 *   one, and the catalogue's own quote where it does not. CoinePro-FX has no ticker route; this
 *   screen works there and says so in the only way that matters, by showing the same numbers the
 *   rest of the app shows.
 * * **The lines** are [SparklineStore] — twenty-four hourly closes from the same candle gateway the
 *   chart uses, requested per card as it appears and never twice.
 * * **The headlines** are [MarketIntelController], the controller `feature:news` is built on, so a
 *   story here and the same story there cannot be two different readings of one feed.
 *
 * ### The three tiles
 *
 * News and the calendar because those are the two screens a reader looking for context actually
 * wants, and the third is the heat map — the whole market in one picture, which is the same
 * question this screen asks, answered at a different scale. [onOpenHeatmap] is nullable and the
 * tile is simply absent without it, in the same way every optional entry in this app is absent
 * rather than disabled: a tile that answers a press with nothing is worse than a row of two.
 *
 * ### Deliberately not here
 *
 * A "brokers" tile. The reference has one; we have nothing behind it — connections and the terminal
 * are account-scoped screens about one reader's own broker, not a directory — and a tile that opens
 * a sign-in wall is an advertisement rather than a door.
 */
@Composable
fun ExploreScreen(
    controller: MarketSearchController,
    intel: MarketIntelController,
    sparklines: SparklineStore,
    onOpenSymbol: (String) -> Unit,
    onOpenNews: () -> Unit,
    onOpenCalendar: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * The day's open, high, low and change for the whole catalogue.
     *
     * Optional, and null is a first-class answer: CoinePro-FX has no such route, and there the card
     * takes its figures from the catalogue quote instead. Passed as the store rather than a table so
     * this screen starts and stops the poll with its own lifetime — it is reference counted, so the
     * markets tab reading the same table keeps it running when this screen leaves.
     */
    tickers: MarketTickerStore? = null,
    /** The third tile. Null drops it rather than drawing a door with nothing behind it. */
    onOpenHeatmap: (() -> Unit)? = null,
    /**
     * The full market list.
     *
     * This screen shows a strip of cards, which is a *taste* of the catalogue and not the catalogue.
     * When Explore took the markets tab's place in the bar, the list it replaced had to keep a door
     * — a reader who came looking for "all of them" must not have to find the menu to discover that
     * the app still has the screen they were using yesterday. Null drops the row entirely.
     */
    onOpenMarkets: (() -> Unit)? = null,
    /** The search affordance in the header. Null on a host with no search route. */
    onOpenSearch: (() -> Unit)? = null,
    /** Opens one story where it was published. Null leaves the list readable but inert. */
    onOpenStory: ((MarketNewsItem) -> Unit)? = null,
) {
    LaunchedEffect(controller) { controller.start() }
    LaunchedEffect(intel) { intel.refresh() }
    // Reference counted in the store, so leaving does not stop the poll for whatever else is
    // reading the same table — and coming back does not start a second one.
    DisposableEffect(tickers) {
        tickers?.start()
        onDispose { tickers?.stop() }
    }

    val state by controller.state.collectAsStateWithLifecycle()
    val news by intel.state.collectAsStateWithLifecycle()
    val lines by sparklines.lines.collectAsStateWithLifecycle()
    // A flow either way, so the collection below is unconditional. A `tickers?.state?.collect…`
    // would add and remove a subscription as the store appears, which is a composition changing
    // shape for a reason that has nothing to do with what is on screen.
    val tickerFlow = remember(tickers) {
        tickers?.state ?: MutableStateFlow(MarketTickerStore.MarketTickerState())
    }
    val tickerState by tickerFlow.collectAsStateWithLifecycle()

    // Saveable, so a rotation does not send the reader back to «همه».
    var category by rememberSaveable { mutableStateOf<SymbolCategory?>(null) }

    val lenses = remember(state.results) { ExploreBoard.lenses(state.results) }
    val cards = remember(state.results, tickerState, category) {
        ExploreBoard.cards(
            rows = state.results,
            tickers = tickerState.table.tickers,
            category = category,
        )
    }
    // Asked for as the row is composed rather than as each card scrolls into view: there are eight
    // of them and the store's own rules — once per symbol per run, four at a time — already stop
    // this from becoming a burst. A per-card request would refire on every horizontal scroll frame.
    LaunchedEffect(cards) { cards.forEach { sparklines.request(it.symbol) } }

    // A category with nothing behind it can only happen through a platform switch — the chips are
    // built from the catalogue, so nothing on screen offers one — and a reader who left «فارکس»
    // selected on CoinePro-FX must come back to a list rather than to an empty row.
    LaunchedEffect(lenses) {
        if (category != null && lenses.none { it.category == category }) category = null
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(CoineProColors.Stage),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        contentPadding = PaddingValues(bottom = CoineProSpacing.Three),
    ) {
        item("header") {
            CoineProListHeader(
                title = stringResource(R.string.explore_title),
                subtitle = stringResource(R.string.explore_subtitle),
                actions = onOpenSearch?.let { open ->
                    {
                        CoineProHeaderAction(
                            icon = CoineProIcons.Search,
                            label = stringResource(R.string.explore_search),
                            onClick = open,
                        )
                    }
                },
            )
        }

        item("tiles") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CoineProSpacing.Gutter),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                DoorTile(
                    icon = CoineProIcons.News,
                    label = stringResource(R.string.explore_door_news),
                    onClick = onOpenNews,
                    modifier = Modifier.weight(1f),
                )
                DoorTile(
                    icon = CoineProIcons.Calendar,
                    label = stringResource(R.string.explore_door_calendar),
                    onClick = onOpenCalendar,
                    modifier = Modifier.weight(1f),
                )
                onOpenHeatmap?.let { open ->
                    DoorTile(
                        // The Phosphor four-cell grid rather than the TradingView layout glyph the
                        // menu row uses. They mean the same thing in a list, where the word beside
                        // them carries it; on a tile the glyph is what a reader recognises before
                        // reading, and `tv_layout_grid` is a plain rounded rectangle that reads as
                        // an icon that failed to load.
                        icon = DesignR.drawable.brand_grid,
                        label = stringResource(R.string.explore_door_heatmap),
                        onClick = open,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        onOpenMarkets?.let { open ->
            item("all-markets") {
                CoineProSecondaryButton(
                    text = stringResource(R.string.explore_all_markets),
                    onClick = open,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = CoineProSpacing.Gutter),
                )
            }
        }

        if (lenses.isNotEmpty()) {
            item("chips") {
                CategoryChips(
                    lenses = lenses,
                    selected = category,
                    onSelect = { category = it },
                )
            }
        }

        item("cards") {
            when {
                // The catalogue is one request and it is the whole screen. A spinner is the honest
                // picture until it lands; a row of empty cards would not be.
                state.loading && cards.isEmpty() -> LoadingStrip()
                // The server's own words where there are any — see `MarketSearchState.error`, which
                // is a UiMessage precisely so an English protocol string never reaches a Persian
                // reader.
                state.error != null && cards.isEmpty() -> CoineProErrorState(
                    message = stringResource(R.string.explore_markets_unavailable),
                    action = stringResource(R.string.explore_retry),
                    onAction = controller::refresh,
                    modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                )
                cards.isEmpty() -> CoineProEmptyState(
                    icon = CoineProIcons.Markets,
                    message = stringResource(R.string.explore_markets_empty),
                    modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                )
                else -> MarketStrip(
                    cards = cards,
                    lines = lines,
                    onOpenSymbol = onOpenSymbol,
                )
            }
        }

        item("stories-header") {
            CoineProListHeader(
                title = stringResource(R.string.explore_stories),
                actions = {
                    CoineProHeaderAction(
                        icon = CoineProIcons.ChevronForward,
                        label = stringResource(R.string.explore_stories_all),
                        onClick = onOpenNews,
                    )
                },
            )
        }

        val stories = news.news.take(ExploreBoard.STORY_LIMIT)
        if (stories.isEmpty()) {
            item("stories-empty") {
                if (news.loading) {
                    LoadingStrip()
                } else {
                    CoineProEmptyState(
                        icon = CoineProIcons.News,
                        message = stringResource(R.string.explore_stories_empty),
                        action = stringResource(R.string.explore_retry),
                        onAction = intel::refresh,
                        modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                    )
                }
            }
        } else {
            items(stories, key = { it.id }) { story ->
                StoryRow(story = story, onClick = onOpenStory?.let { open -> { open(story) } })
            }
        }
    }
}

/**
 * One of the three doors.
 *
 * A block with a glyph over a word, and nothing else — no count, no preview, no second line. The
 * tile's whole job is to be recognisable at a glance and pressable without reading, which a
 * subtitle undoes: a reader who has to read a tile has already stopped scanning.
 */
@Composable
private fun DoorTile(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CoineProCard(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = CoineProSpacing.OneHalf, horizontal = CoineProSpacing.One),
        // The card's own click rather than a `clickable` on its modifier: it moves the fill as well
        // as the scale, which is what a finger on a block this size actually reads as contact.
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = CoineProColors.TextSecondary,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.TextPrimary,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The category strip.
 *
 * Pills in a scrolling row rather than a filled tray, and for the reason the markets tab states
 * about its own second strip: a tray reads as the primary filter of the screen it sits on, and this
 * one narrows a single row of eight cards. It scrolls because five Persian labels are wider than a
 * 360dp phone and a strip that clipped its last chip would hide the indices entirely.
 */
@Composable
private fun CategoryChips(
    lenses: List<ExploreLens>,
    selected: SymbolCategory?,
    onSelect: (SymbolCategory?) -> Unit,
) {
    val haptics = rememberCoineProHaptics()
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        contentPadding = PaddingValues(horizontal = CoineProSpacing.Gutter),
    ) {
        items(lenses, key = { it.category?.name ?: "all" }) { lens ->
            val active = lens.category == selected
            Text(
                text = stringResource(lens.labelRes()),
                style = MaterialTheme.typography.labelSmall,
                color = if (active) CoineProColors.OnAccent else CoineProColors.TextSecondary,
                maxLines = 1,
                modifier = Modifier
                    .clickable {
                        // Only a change is worth a tick. A buzz for pressing the chip you are
                        // already on teaches the reader to distrust the ones that mean something.
                        if (!active) haptics.select()
                        onSelect(lens.category)
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

/** The chip's label. A category with no name of its own cannot reach this list — see [ExploreBoard]. */
private fun ExploreLens.labelRes(): Int = when (category) {
    null -> R.string.explore_category_all
    SymbolCategory.CRYPTO -> R.string.explore_category_crypto
    SymbolCategory.METAL -> R.string.explore_category_metal
    SymbolCategory.FOREX -> R.string.explore_category_forex
    SymbolCategory.INDEX -> R.string.explore_category_index
    SymbolCategory.ENERGY -> R.string.explore_category_energy
    SymbolCategory.OTHER -> R.string.explore_category_other
}

/** The horizontally scrolling row of markets. */
@Composable
private fun MarketStrip(
    cards: List<ExploreCard>,
    lines: Map<String, List<Double>>,
    onOpenSymbol: (String) -> Unit,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        contentPadding = PaddingValues(horizontal = CoineProSpacing.Gutter),
    ) {
        items(cards, key = { it.symbol }) { card ->
            MarketCard(
                card = card,
                line = lines[card.symbol.uppercase()].orEmpty(),
                onClick = { onOpenSymbol(card.symbol) },
            )
        }
    }
}

/**
 * One market: its mark, its short name, its price, its move and its day.
 *
 * The width is fixed rather than measured from the content, because the point of a row of these is
 * that they line up — a strip whose cards were each as wide as their own price would put the
 * percentage pills at five different heights across the screen and turn a comparison into a puzzle.
 *
 * The line is drawn only where the store has one. `CoineProSparkline` already refuses a series of
 * fewer than two points and draws nothing, which is the right answer: a flat line is a claim that
 * the price did not move, and «not loaded yet» is not that claim.
 */
@Composable
private fun MarketCard(
    card: ExploreCard,
    line: List<Double>,
    onClick: () -> Unit,
) {
    CoineProCard(
        modifier = Modifier.width(CARD_WIDTH),
        shape = CoineProShapes.medium,
        contentPadding = PaddingValues(CoineProSpacing.OneHalf),
        onClick = onClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                // Every symbol that reaches here has artwork: the catalogue was filtered through
                // `SymbolArtwork.covers` before it left the gateway. There is no blank square and
                // no lettered disc to fall back to, by construction rather than by care.
                CoineProAssetLogo(symbol = card.symbol, size = 26.dp)
                Text(
                    // The ticker is Latin inside a right-to-left row, so it is isolated. Without
                    // this `BTC/USDT` renders with its legs swapped next to a Persian label.
                    text = BidiText.isolateLtr(card.meta.short),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = CoineProColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                // A market figure, so Latin digits with the decimals its own magnitude needs.
                // `'—'` where the feed carried no price at all, which this card should not be
                // showing — `ExploreBoard.cards` drops those — and says so honestly if it ever does.
                text = card.price?.let(MarketNumberFormatter::priceAuto) ?: NO_VALUE,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = CoineProColors.TextPrimary,
                maxLines = 1,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                card.changePercent?.let { percent ->
                    CoineProPercentPill(percent = percent, background = CoineProColors.Surface)
                } ?: Text(
                    text = NO_VALUE,
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
                CoineProSparkline(
                    values = line,
                    modifier = Modifier.weight(1f).height(22.dp),
                    // The line takes the move's own colour so the two read as one object rather
                    // than as a figure with a decoration beside it.
                    colour = when {
                        (card.changePercent ?: 0.0) > 0 -> CoineProColors.Buy
                        (card.changePercent ?: 0.0) < 0 -> CoineProColors.Sell
                        else -> CoineProColors.TextMuted
                    },
                )
            }
        }
    }
}

/**
 * One headline.
 *
 * The byline sits above the title rather than below it, which is the reference's arrangement and is
 * also the right one for a list: the eye lands on the heavy line, and a date that came after it
 * would be read as part of the next story.
 */
@Composable
private fun StoryRow(story: MarketNewsItem, onClick: (() -> Unit)?) {
    CoineProCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter),
        onClick = onClick,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Text(
                // The publisher and the moment, in that order, joined by the app's own separator.
                // The source is a Latin wire-service name inside a Persian line, so it is isolated.
                text = BidiText.isolateLtr(story.source) + " · " + PersianDateTime.moment(story.publishedAt),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                maxLines = 1,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                // A wire headline is a whole sentence, not a run, so it sets its own paragraph
                // direction — an English one laid out right-to-left comes back with its full stop
                // at the start of the line. The news list answers this the same way; the rule is
                // shared so the two screens cannot drift apart on the same story.
                text = CoineProProse.paragraph(story.title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = CoineProColors.TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                textAlign = CoineProProse.alignment(story.title),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The shape of the row before it has anything in it.
 *
 * A block of the surface colour at the height the strip will be, so the screen does not jump when
 * the catalogue lands. Not a shimmer: a moving highlight is decoration reporting nothing, and this
 * app's motion policy has no room for one.
 */
@Composable
private fun LoadingStrip() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter)
            .height(CARD_HEIGHT)
            .background(CoineProColors.Surface, CoineProShapes.medium)
            .border(1.dp, CoineProColors.BorderSubtle, CoineProShapes.medium),
    )
}

/** Wide enough for `64,182.40` at the title size, narrow enough that two and a half cards show. */
private val CARD_WIDTH = 148.dp

/** The strip's own height, so the placeholder above does not resize the page when it fills. */
private val CARD_HEIGHT = 116.dp

/** The app's null. An em dash, never a zero — a zero is a price, and «no price» is not one. */
private const val NO_VALUE = "—"
