package com.coinepro.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.coinepro.core.common.BidiText
import com.coinepro.core.symbols.SymbolMeta
import com.coinepro.core.symbols.SymbolSearch

/**
 * A symbol field that knows the catalogue.
 *
 * ### The bug this replaces
 *
 * Both the journal and the paper-trading ticket took a symbol as free text. A reader who typed
 * «bit» — or «بیت‌کوین», or `btc` — got a field holding the word they typed and a screen that did
 * nothing with it: the journal's search found no entry called *bit*, and the ticket had no quote
 * for a market called *BIT*, so the market order was refused for want of a price. Neither screen
 * ever said that the thing to type was `BTCUSDT`, because neither knew the catalogue existed.
 *
 * This field does. Below the text, while it is focused and holds something that is not yet a market
 * the catalogue recognises, it lists the markets that match — by ticker, by base, by Persian name —
 * ranked the way the app's own search ranks them ([SymbolSearch]), each with its logo and its
 * description, and a tap fills the field with the wire symbol. A reader who already knows the ticker
 * types it and the list gets out of the way as soon as it matches exactly.
 *
 * The suggestions are drawn *in the column*, not in a popup. A popup over a `LazyColumn` is
 * clipped by the row it opened from on half the phones this app runs on, and a list that pushes the
 * form down is the one the reader can always see whole.
 *
 * @param markets the catalogue the host has — the platform's live universe, already classified and
 *   already filtered to symbols the app has artwork for. Empty leaves this a plain text field.
 */
@Composable
fun CoineProSymbolField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    markets: List<SymbolMeta>,
    modifier: Modifier = Modifier,
    /** Called with the wire symbol when a suggestion is tapped, after [onValueChange]. */
    onPick: ((SymbolMeta) -> Unit)? = null,
    limit: Int = SUGGESTION_LIMIT,
) {
    var focused by remember { mutableStateOf(false) }
    val query = value.trim()
    val exact = remember(query, markets) {
        query.isNotEmpty() && markets.any { it.symbol.equals(query, ignoreCase = true) }
    }
    val suggestions = remember(focused, query, markets, exact, limit) {
        if (!focused || query.isEmpty() || exact || markets.isEmpty()) {
            emptyList()
        } else {
            SymbolSearch.search(markets, query).take(limit).map { it.meta }
        }
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
        CoineProTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
        )
        if (focused && query.isNotEmpty() && !exact && markets.isNotEmpty()) {
            if (suggestions.isEmpty()) {
                Text(
                    text = stringResource(R.string.symbol_field_no_match),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    modifier = Modifier.padding(horizontal = CoineProSpacing.Half),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CoineProShapes.medium)
                        .background(CoineProColors.SurfaceElevated)
                        .border(1.dp, CoineProColors.Border, CoineProShapes.medium),
                ) {
                    suggestions.forEach { meta ->
                        SuggestionRow(meta) {
                            onValueChange(meta.symbol)
                            onPick?.invoke(meta)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(meta: SymbolMeta, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoineProAssetLogo(symbol = meta.symbol, size = 26.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = BidiText.isolateLtr(meta.pretty),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextPrimary,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = meta.listDescription,
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                textAlign = TextAlign.Right,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Six rows: enough to hold every spelling a reader could mean, few enough not to bury the form. */
private const val SUGGESTION_LIMIT = 6
