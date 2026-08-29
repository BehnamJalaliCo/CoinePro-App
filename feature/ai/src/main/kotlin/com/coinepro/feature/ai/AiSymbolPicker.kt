package com.coinepro.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coinepro.core.aisignal.AiSymbolOrigin
import com.coinepro.core.aisignal.AiSymbolUniverse
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSheetEmpty
import com.coinepro.core.designsystem.CoineProSheetSearch
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.symbols.SymbolCategory

/**
 * The whole market universe, one tap behind the request panel.
 *
 * ### What this replaces
 *
 * A row of nine chips — eight coins on TradeYar, two metals on CoinePro-FX — hard-coded in
 * `AiSignalProductScope`, on an app that knows 441 crypto markets and the whole MT5 forex universe
 * and already ships a ranked fuzzy matcher over them in `core:symbols`. Every other market surface
 * in this product could find `فرانک`; the AI screen could not find `EURUSD`.
 *
 * ### Why the origin line is not chrome
 *
 * The list under the search box is one of three different things — a scope the server stated, the
 * platform's whole catalogue, or a hand-written fallback standing in while the catalogue loads —
 * and they mean different things to somebody who cannot find what they are looking for. «۹ نماد»
 * with a reason is an explanation; a short list with no reason is a bug report.
 *
 * Counts here are prose, so Persian digits; the tickers are market identity and stay Latin,
 * isolated so they do not reorder inside a right-to-left line.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSymbolPickerSheet(
    universe: AiSymbolUniverse,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<SymbolCategory?>(null) }

    CoineProSheet(
        title = stringResource(R.string.ai_symbol_picker_title),
        subtitle = universe.originLine(),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            CoineProSheetSearch(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.ai_symbol_search),
            )
            // Only the categories actually present. A «شاخص» chip on a crypto platform is a filter
            // whose only possible answer is an empty list.
            val categories = universe.categories
            if (categories.size > 1) {
                AiChoiceRow(
                    label = stringResource(R.string.ai_symbol_category),
                    options = categories.map { it to categoryLabel(it) },
                    selected = category,
                    onSelect = { category = it },
                )
            }
        }

        val results = remember(universe, query, category) { universe.search(query, category) }
        if (results.isEmpty()) {
            CoineProSheetEmpty(stringResource(R.string.ai_symbol_none))
            return@CoineProSheet
        }
        LazyColumn(
            // Bounded so the sheet does not grow past the screen on a four-hundred-row catalogue,
            // and so the search box stays visible while the list under it scrolls.
            modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = CoineProSpacing.Gutter,
                vertical = CoineProSpacing.One,
            ),
        ) {
            items(results, key = { it.meta.symbol }) { match ->
                AiSymbolRow(
                    symbol = match.meta.symbol,
                    title = match.meta.pretty,
                    description = match.meta.listDescription,
                    selected = match.meta.symbol == selected,
                    onClick = { onSelect(match.meta.symbol) },
                )
            }
        }
    }
}

@Composable
private fun AiSymbolRow(
    symbol: String,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (selected) CoineProColors.Gold.copy(alpha = 0.10f) else CoineProColors.Surface,
            )
            .padding(horizontal = CoineProSpacing.One, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoineProAssetLogo(symbol = symbol, size = 30.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = BidiText.isolateLtr(title),
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) CoineProColors.GoldBright else CoineProColors.TextPrimary,
                textAlign = TextAlign.Right,
                maxLines = 1,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
                textAlign = TextAlign.Right,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** What the reader is choosing from, and where it came from. */
@Composable
internal fun AiSymbolUniverse.originLine(): String {
    if (loading && markets.isEmpty()) return stringResource(R.string.ai_symbol_loading)
    val count = size.toPersianDigits()
    return when (origin) {
        AiSymbolOrigin.SERVER -> stringResource(R.string.ai_symbol_source_server, count)
        AiSymbolOrigin.CATALOGUE -> stringResource(R.string.ai_symbol_source_catalogue, count)
        AiSymbolOrigin.FALLBACK -> stringResource(R.string.ai_symbol_source_fallback, count)
    }
}

@Composable
internal fun categoryLabel(category: SymbolCategory): String = stringResource(
    when (category) {
        SymbolCategory.FOREX -> R.string.ai_category_forex
        SymbolCategory.CRYPTO -> R.string.ai_category_crypto
        SymbolCategory.METAL -> R.string.ai_category_metal
        SymbolCategory.INDEX -> R.string.ai_category_index
        SymbolCategory.ENERGY -> R.string.ai_category_energy
        SymbolCategory.OTHER -> R.string.ai_category_other
    },
)
