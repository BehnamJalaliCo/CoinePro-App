package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing

/**
 * Switching symbol without leaving the chart.
 *
 * The web terminal has a wheel for this; on a phone the same idea is a strip. The thing it replaces
 * is four taps — back, search, type, open — repeated every time somebody compares two instruments,
 * which is most of what looking at charts *is*.
 *
 * It shows the reader's own watchlist and nothing else. A strip of "popular" symbols would be a
 * second market list on a screen that is not a market list; the watchlist is by definition the set
 * this reader flips between, and if it is empty the strip is absent rather than filled with
 * suggestions.
 *
 * The current symbol scrolls itself into view. Without that, opening the chart on the ninth symbol
 * of a watchlist shows a strip apparently starting somewhere else, and the reader has to hunt for
 * where they are before they can move.
 */
@Composable
internal fun SymbolWheel(
    symbols: List<String>,
    current: String,
    onSelect: (String) -> Unit,
) {
    if (symbols.size < 2) return
    val listState = rememberLazyListState()

    LaunchedEffect(symbols, current) {
        val index = symbols.indexOf(current)
        if (index >= 0) listState.animateScrollToItem(index)
    }

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(CoineProColors.Surface),
        state = listState,
        contentPadding = PaddingValues(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.Half),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        items(symbols, key = { it }) { symbol ->
            val selected = symbol == current
            Row(
                modifier = Modifier
                    .clip(CoineProShapes.small)
                    .background(if (selected) CoineProColors.Accent else Color.Transparent)
                    .clickable(enabled = !selected) { onSelect(symbol) }
                    .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoineProAssetLogo(symbol = symbol, size = 18.dp)
                Text(
                    // Isolated: a Latin ticker in a right-to-left row reorders without it.
                    text = BidiText.isolateLtr(symbol),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) CoineProColors.OnAccent else CoineProColors.TextSecondary,
                )
            }
        }
    }
}
