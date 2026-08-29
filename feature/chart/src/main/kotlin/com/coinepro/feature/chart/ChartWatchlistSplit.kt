package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.decimalsFor
import com.coinepro.core.chart.formatPrice
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.LtrDirection
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.symbols.SymbolArtwork
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One row of the watchlist strip: what it costs and what it has done.
 *
 * Nullable prices throughout, because the strip has to be useful before any feed has answered. A
 * row with a symbol and no price is a row a reader can still tap to switch to; a strip that waited
 * for quotes before drawing anything would be a blank panel under the chart during exactly the
 * seconds somebody is deciding where to look next.
 */
data class WatchlistQuote(
    val symbol: String,
    val price: Double? = null,
    /** The move over the feed's own window, as a percentage. Null when the feed has not said. */
    val changePercent: Double? = null,
)

/**
 * The chart page above, the watchlist below, and a handle between them.
 *
 * ### The complaint, in the reader's own words
 *
 * *"in current UI, you can see either chart or watchlist, not simultaneously. huge slowdown. feels
 * completely handicapped."* It is the third-most-common structural complaint about the large mobile
 * terminal, and it is a complaint about *time*: every comparison between two instruments costs a
 * navigation out, a scroll, a tap and a wait, and somebody reading four markets pays it dozens of
 * times an hour.
 *
 * ### Why the whole page goes in the upper pane
 *
 * Not just the chart. The readings, the setup card and the studio entry belong to the chart and
 * scroll with it, so the reader keeps the page they had; what changes is that it now ends at a
 * handle instead of at the bottom of the glass. Splitting the chart out and leaving the rest below
 * the strip would have produced two scrolling regions with the reader's own content divided between
 * them by nothing they could see.
 *
 * ### No watchlist, no split
 *
 * A reader who has starred nothing gets exactly the page they had, with no divider and no empty
 * panel. This is a layout the watchlist earns by existing, not a mode with an off state.
 */
@Composable
internal fun ChartWatchlistLayout(
    symbols: List<String>,
    current: String,
    quotes: Map<String, WatchlistQuote>,
    onSelect: (String) -> Unit,
    /** Where the divider's position is remembered. Null keeps it for this visit only. */
    workspace: ChartWorkspaceStore?,
    modifier: Modifier = Modifier,
    /** The chart page, given the height the split has decided it gets. */
    page: @Composable (Modifier) -> Unit,
) {
    val shown = remember(symbols) { symbols.filter { SymbolArtwork.covers(it) } }
    if (shown.isEmpty()) {
        page(modifier.fillMaxSize())
        return
    }
    val scope = rememberCoroutineScope()
    var ratio by rememberSaveable { mutableFloatStateOf(ChartSplit.DEFAULT) }
    // Read once. A collector would deliver this screen's own writes straight back and fight a
    // finger that is still on the handle.
    var restored by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(workspace) {
        if (restored) return@LaunchedEffect
        restored = true
        workspace?.let { store ->
            runCatching { store.splitRatio.first() }.getOrNull()?.let { ratio = it }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Measured against the room this layout actually has rather than against the window, so a
        // chart in a multi-window split gets the compact treatment for the same reason a small
        // phone does.
        val compact = maxHeight < ChartSplit.COMPACT_HEIGHT
        val totalPx = with(LocalDensity.current) { maxHeight.toPx() }
        Column(modifier = Modifier.fillMaxSize()) {
            if (compact) {
                page(Modifier.fillMaxWidth().weight(1f))
                WatchlistTickerRow(
                    symbols = shown,
                    current = current,
                    quotes = quotes,
                    onSelect = onSelect,
                )
            } else {
                page(Modifier.fillMaxWidth().weight(ratio))
                ChartSplitHandle(
                    onDrag = { amount -> ratio = ChartSplit.after(ratio, amount, totalPx) },
                    // Written when the finger lifts, not per frame. Sixty preferences writes a
                    // second for a value nobody reads until the next launch is a lot of disk for
                    // one decision.
                    onDragEnd = {
                        workspace?.let { store ->
                            scope.launch { runCatching { store.setSplitRatio(ratio) } }
                        }
                    },
                )
                WatchlistStrip(
                    symbols = shown,
                    current = current,
                    quotes = quotes,
                    onSelect = onSelect,
                    modifier = Modifier.fillMaxWidth().weight(1f - ratio),
                )
            }
        }
    }
}

/**
 * The handle between the chart and the watchlist.
 *
 * ### Where it is, and why that is the whole design
 *
 * Low on the screen, which is where a thumb holding a phone already rests. The complaint this
 * feature answers is about *speed* — "huge slowdown" — so a control that has to be reached for
 * with the other hand would give back most of what the split buys. It is also why the split is not
 * a mode: there is nothing to switch on, nothing to discover, and no state in which the reader has
 * one surface and has to remember how to get the other.
 *
 * ### Why it looks like a handle
 *
 * The grip glyph and the extra height are not decoration. A one-pixel divider is draggable in the
 * sense that the code responds to it and undraggable in the sense that nobody tries. Twenty-four
 * points with a visible grip is the smallest thing a reader reaches for without being told to.
 */
@Composable
internal fun ChartSplitHandle(
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberCoineProHaptics()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HANDLE_HEIGHT)
            .background(CoineProColors.Stage)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { haptics.select() },
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                )
            }
            .semantics { contentDescription = "جابه‌جایی مرز نمودار و دیده‌بان" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.tv_more_horizontal),
            contentDescription = null,
            tint = CoineProColors.TextDisabled,
            modifier = Modifier.size(width = GRIP_WIDTH, height = GRIP_HEIGHT),
        )
    }
}

/**
 * The watchlist under the chart, as rows.
 *
 * ### What it is for
 *
 * Switching instrument without leaving the chart, keeping the drawings, the timeframe and the
 * indicators — which the per-symbol state store already restores, so a tap here costs a fetch and
 * nothing else. The alternative, and what this app did until now, is a navigation: back, list,
 * find, open, wait, and then re-set whatever the new chart forgot.
 *
 * ### Symbols with no artwork are not listed
 *
 * `SymbolArtwork.covers` is the filter, here as everywhere else. A row with a blank square or a
 * grey disc with a letter in it is worse than a row that is absent: it reads as a broken image and
 * it is the first thing a reader notices about a list.
 */
@Composable
internal fun WatchlistStrip(
    symbols: List<String>,
    current: String,
    quotes: Map<String, WatchlistQuote>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = remember(symbols) { symbols.filter { SymbolArtwork.covers(it) } }
    val listState = rememberLazyListState()

    // The symbol in front of the reader scrolls itself into view. Without it, opening the chart on
    // the ninth entry of a watchlist shows a strip apparently starting somewhere else, and the
    // reader has to find where they are before they can move.
    LaunchedEffect(shown, current) {
        val index = shown.indexOf(current)
        if (index >= 0) runCatching { listState.animateScrollToItem(index) }
    }

    Column(modifier = modifier.fillMaxSize().background(CoineProColors.Stage)) {
        HorizontalDivider(color = CoineProColors.Border)
        if (shown.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(CoineProSpacing.Two),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "دیده‌بان خالی است. از بازارها نمادی را ستاره کنید تا همین‌جا زیر نمودار بیاید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
            return@Column
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = CoineProSpacing.Half),
        ) {
            items(shown, key = { it }) { symbol ->
                WatchlistRow(
                    symbol = symbol,
                    quote = quotes[symbol.uppercase()],
                    selected = symbol == current,
                    onClick = { onSelect(symbol) },
                )
            }
        }
    }
}

/** One instrument: its mark, its ticker, its price and its move. */
@Composable
private fun WatchlistRow(
    symbol: String,
    quote: WatchlistQuote?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberCoineProHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .background(
                if (selected) CoineProTint.fill(CoineProColors.Gold, CoineProColors.Stage) else Color.Transparent,
            )
            .clickable(enabled = !selected) {
                haptics.select()
                onClick()
            }
            .padding(horizontal = CoineProSpacing.Gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        CoineProAssetLogo(symbol = symbol, size = LOGO)
        Text(
            // Isolated: a Latin ticker dropped bare into a right-to-left row reorders around
            // whatever punctuation follows it.
            text = BidiText.isolateLtr(symbol),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) CoineProColors.Gold else CoineProColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        quote?.price?.let { price ->
            LtrDirection {
                Text(
                    text = formatPrice(price, decimalsFor(price)),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextSecondary,
                    // Right, never End: this is a column of market figures and it stays aligned
                    // the same way in a right-to-left layout as it does in the broker statement
                    // the reader is holding it against.
                    textAlign = TextAlign.Right,
                    modifier = Modifier.width(PRICE_WIDTH),
                )
            }
        }
        quote?.changePercent?.let { move ->
            LtrDirection {
                Text(
                    text = MarketNumberFormatter.signedPercent(move),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (move >= 0) CoineProColors.Buy else CoineProColors.Sell,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.width(MOVE_WIDTH),
                )
            }
        }
    }
}

/**
 * The watchlist as a single scrolling row of tickers, for a screen with no room for a list.
 *
 * ### Why the layout changes rather than shrinking
 *
 * Below `ChartSplit.COMPACT_HEIGHT` a two-thirds chart is already at the floor of what a hundred
 * candles can be read in, and what is left cannot hold a row with a price on it *and* a handle
 * worth grabbing. Shrinking the same list would produce two rows and a divider — a control that is
 * present, is technically the feature, and helps nobody. A ticker row costs 44 points, keeps every
 * symbol one tap away, and does not pretend to be a table.
 *
 * The handle is absent with it, deliberately: there is nothing left to trade between the two panes,
 * and a handle that cannot move is worse than no handle at all.
 */
@Composable
internal fun WatchlistTickerRow(
    symbols: List<String>,
    current: String,
    quotes: Map<String, WatchlistQuote>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = remember(symbols) { symbols.filter { SymbolArtwork.covers(it) } }
    if (shown.isEmpty()) return
    val listState = rememberLazyListState()
    val haptics = rememberCoineProHaptics()

    LaunchedEffect(shown, current) {
        val index = shown.indexOf(current)
        if (index >= 0) runCatching { listState.animateScrollToItem(index) }
    }

    Column(modifier = modifier.fillMaxWidth().background(CoineProColors.Stage)) {
        HorizontalDivider(color = CoineProColors.Border)
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(TICKER_HEIGHT),
            contentPadding = PaddingValues(horizontal = CoineProSpacing.OneHalf),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(shown, key = { it }) { symbol ->
                val quote = quotes[symbol.uppercase()]
                val selected = symbol == current
                Row(
                    modifier = Modifier
                        .clip(CoineProShapes.small)
                        .background(
                            if (selected) {
                                CoineProTint.fill(CoineProColors.Gold, CoineProColors.Stage)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable(enabled = !selected) {
                            haptics.select()
                            onSelect(symbol)
                        }
                        .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                ) {
                    CoineProAssetLogo(symbol = symbol, size = TICKER_LOGO)
                    Text(
                        text = BidiText.isolateLtr(symbol),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) CoineProColors.Gold else CoineProColors.TextSecondary,
                    )
                    quote?.changePercent?.let { move ->
                        LtrDirection {
                            Text(
                                text = MarketNumberFormatter.signedPercent(move),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (move >= 0) CoineProColors.Buy else CoineProColors.Sell,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Big enough to be reached for, small enough that it is not a row of the layout. */
private val HANDLE_HEIGHT = 24.dp

private val GRIP_WIDTH = 26.dp
private val GRIP_HEIGHT = 14.dp

/** A watchlist row: Material's minimum target with a little air, so four fit a short strip. */
private val ROW_HEIGHT = 44.dp

private val LOGO = 22.dp

/**
 * Fixed widths for the two figures, so the prices line up down the strip.
 *
 * A column of right-aligned numbers that each size to their own content is not a column: the
 * decimal points wander and the strip becomes something to read row by row rather than to scan.
 */
private val PRICE_WIDTH = 88.dp

private val MOVE_WIDTH = 58.dp

/** The compact row's own height, and the logo inside it. */
private val TICKER_HEIGHT = 44.dp

private val TICKER_LOGO = 18.dp
