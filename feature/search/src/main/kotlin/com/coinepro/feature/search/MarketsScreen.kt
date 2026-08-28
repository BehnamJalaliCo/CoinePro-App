package com.coinepro.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPercentPill
import com.coinepro.core.designsystem.CoineProPullToRefresh
import com.coinepro.core.designsystem.CoineProSegmentTabs
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProSparkline
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.coineProPriceFlash
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.marketdata.SparklineStore
import com.coinepro.core.symbols.SymbolCategory

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
 */
@Composable
fun MarketsScreen(
    controller: MarketSearchController,
    sparklines: SparklineStore,
    onOpenSymbol: (String) -> Unit,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    watchlist: List<String> = emptyList(),
    /** The open signals strip at the foot. Null on a build with nothing to link to. */
    openSignals: MarketsSignalStrip? = null,
) {
    LaunchedEffect(controller) { controller.start() }
    val state by controller.state.collectAsStateWithLifecycle()
    val lines by sparklines.lines.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(MarketsTab.ALL) }

    // The category chip and the watchlist tab are different filters over one list, so they are
    // applied here rather than pushed into the controller: the controller's category is what the
    // *search* screen uses, and a tab that quietly rewrote it would change the other screen too.
    val rows = remember(state.results, tab, watchlist) {
        val watched = watchlist.map { it.uppercase() }.toSet()
        state.results.filter { row ->
            when (tab) {
                MarketsTab.ALL -> true
                MarketsTab.CRYPTO -> row.meta.category == SymbolCategory.CRYPTO
                MarketsTab.FOREX -> row.meta.category == SymbolCategory.FOREX
                MarketsTab.METAL -> row.meta.category == SymbolCategory.METAL
                MarketsTab.WATCHLIST -> row.meta.symbol.uppercase() in watched
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().background(CoineProColors.Stage)) {
        Header(onOpenSearch = onOpenSearch)
        // The shared strip. This screen had grown a byte-for-byte copy of it — same tray, same
        // raised block, same weights — which is one more place for the next change to be applied
        // once and forgotten once. It is also how this row ended up without the tick every other
        // control in the app answers a tap with.
        CoineProSegmentTabs(
            options = MarketsTab.entries.map { it to stringResource(it.labelRes) },
            selected = tab,
            onSelect = { tab = it },
        )
        ColumnHeadings()

        when {
            state.loading && state.results.isEmpty() -> Centred {
                CircularProgressIndicator(color = CoineProColors.Gold, strokeWidth = 2.dp)
            }
            rows.isEmpty() -> Centred {
                Text(
                    text = if (tab == MarketsTab.WATCHLIST) {
                        stringResource(R.string.markets_watchlist_empty)
                    } else {
                        stringResource(R.string.search_empty)
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
                        MarketRow(
                            row = row,
                            line = lines[row.meta.symbol.uppercase()].orEmpty(),
                            onClick = { onOpenSymbol(row.meta.symbol) },
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
 * What each column is.
 *
 * Three words, once, above a list whose rows never change shape. Without it the line in the middle
 * of the row is a decoration; with it, it is a twenty-four-hour trend and the reader knows to read
 * it as one.
 */
@Composable
private fun ColumnHeadings() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = CoineProSpacing.Two, end = CoineProSpacing.Two, top = CoineProSpacing.OneHalf, bottom = CoineProSpacing.Half),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val style = MaterialTheme.typography.labelSmall
        Text(stringResource(R.string.markets_column_symbol), style = style, color = CoineProColors.TextDisabled)
        Text(stringResource(R.string.markets_column_trend), style = style, color = CoineProColors.TextDisabled)
        Text(stringResource(R.string.markets_column_price), style = style, color = CoineProColors.TextDisabled)
    }
}

@Composable
private fun MarketRow(row: MarketSearchRow, line: List<Double>, onClick: () -> Unit) {
    val change = row.quote?.changePercent
    val up = (change ?: 0.0) >= 0.0
    val tone = when {
        change == null -> CoineProColors.TextMuted
        up -> CoineProColors.Buy
        else -> CoineProColors.Sell
    }
    val haptics = rememberCoineProHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Every row the same height whether or not the feed has quoted it yet. Without this a
            // list of forty markets where six are still waiting breathes as the prices land, and
            // the reader's thumb lands on the row below the one they aimed at.
            .defaultMinSize(minHeight = 58.dp)
            // The tint a trader reads: which rows are moving, found before any figure is read.
            .coineProPriceFlash(row.quote?.price)
            .clickable {
                haptics.select()
                onClick()
            }
            .padding(horizontal = CoineProSpacing.Two, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        CoineProAssetLogo(symbol = row.meta.symbol, size = 30.dp)
        // Wider than it was. Eighty-four points fitted the ticker and cut every Persian name
        // under it; the sparkline beside it was floating in a weighted box with room to spare.
        Column(modifier = Modifier.width(96.dp)) {
            Text(
                text = row.meta.symbol,
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
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            CoineProSparkline(
                values = line,
                modifier = Modifier.width(58.dp).height(24.dp),
                colour = tone,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = row.quote?.let { MarketNumberFormatter.priceAuto(it.price) }
                    ?: stringResource(R.string.search_no_price),
                style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
                color = CoineProColors.TextPrimary,
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
