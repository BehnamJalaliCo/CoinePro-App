package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.LtrDirection
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.symbols.SymbolArtwork

/**
 * One row of the watchlist strip: what it costs and what it has done.
 *
 * Nullable prices throughout, because the strip has to be useful before any feed has answered. A
 * row with a symbol and no price is a row a reader can still tap to switch to; a strip that waited
 * for quotes before drawing anything would be a blank panel during exactly the seconds somebody is
 * deciding where to look next.
 */
data class WatchlistQuote(
    val symbol: String,
    val price: Double? = null,
    /** The move over the feed's own window, as a percentage. Null when the feed has not said. */
    val changePercent: Double? = null,
)

/*
 * The chart-and-watchlist split used to live in this file: `ChartWatchlistLayout`, its drag handle,
 * the `WatchlistStrip` of rows underneath, and the divider position kept in the workspace store.
 *
 * It is gone, and the argument is written where the chart page now says it — see `ChartScreen`. In
 * short: the page inside the split scrolls, and the plot inside *that* takes its height from the
 * screen, so giving the page two thirds of the glass never shrank the chart. It cut the bottom off
 * it, and the reader was left with a chart with no time axis and a list across its middle. What the
 * strip was for — changing instrument without leaving the chart — is `SymbolWheel`'s, in the toolbar
 * band, on the same starred markets with the same prices beside them.
 */

/**
 * The watchlist as a single scrolling row of tickers, for a pane with no room for a list.
 *
 * A ticker row costs 44 points, keeps every symbol one tap away, and does not pretend to be a table.
 * The panes screen draws it under a dense pane and offers it in a sheet from a roomy one, so a
 * symbol filtered out of the row for having no artwork is filtered out of both.
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
                        // **The market on screen is marked in ink, not in gold.**
                        //
                        // Gold in this app is the brand and the one commercial action on a page. A
                        // strip of watched markets is neither, and the raised neutral is what this
                        // app already uses for "one of these is in force".
                        .background(
                            if (selected) CoineProColors.SurfaceElevated else Color.Transparent,
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
                        // Isolated: a Latin ticker dropped bare into a right-to-left row reorders
                        // around whatever punctuation follows it.
                        text = BidiText.isolateLtr(symbol),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) CoineProColors.TextPrimary else CoineProColors.TextSecondary,
                    )
                    quote?.changePercent?.let { move ->
                        LtrDirection {
                            Text(
                                text = MarketNumberFormatter.signedPercent(move),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (move >= 0) CoineProColors.MarketUp else CoineProColors.MarketDown,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The row's own height, and the logo inside it. */
private val TICKER_HEIGHT = 44.dp

private val TICKER_LOGO = 18.dp
