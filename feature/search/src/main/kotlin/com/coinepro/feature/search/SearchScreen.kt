package com.coinepro.feature.search

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProMarketRow
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.symbols.MarketHours
import com.coinepro.core.symbols.MatchField
import com.coinepro.core.symbols.SymbolCategory

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
) {
    LaunchedEffect(controller) { controller.start() }
    val state by controller.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = CoineProSpacing.Gutter,
                vertical = CoineProSpacing.Two,
            ),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        ) {
            CoineProTextField(
                value = state.query,
                onValueChange = controller::setQuery,
                label = stringResource(R.string.search_field_label),
                modifier = Modifier.fillMaxWidth(),
            )
            CategoryChips(selected = state.category, onSelect = controller::setCategory)
        }

        when {
            state.loading && state.results.isEmpty() -> Centered {
                CircularProgressIndicator(color = CoineProColors.Gold)
            }

            state.error != null && state.results.isEmpty() -> Centered {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
                ) {
                    Text(
                        text = state.error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.TextSecondary,
                    )
                    CoineProPrimaryButton(
                        text = stringResource(R.string.search_retry),
                        onClick = controller::refresh,
                    )
                }
            }

            state.empty -> Centered {
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
                    item {
                        SectionHeader(
                            title = stringResource(R.string.search_count, state.catalogSize),
                        )
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
                                .padding(start = CoineProSpacing.Gutter, end = 66.dp)
                                .height(1.dp)
                                .background(CoineProColors.Border),
                        )
                    }
                    MarketRow(row, onOpenSymbol)
                }
            }
        }
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
    LazyRow(
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
private fun MarketRow(row: MarketSearchRow, onOpenSymbol: ((String) -> Unit)?) {
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
                .takeIf { row.field != MatchField.DESCRIPTION }
                ?.intoPretty(row.meta.base?.length),
        ),
        subtitle = highlighted(
            text = row.meta.description,
            range = row.highlight.takeIf { row.field == MatchField.DESCRIPTION },
        ),
        price = quote?.let { MarketNumberFormatter.price(it.price, it.decimals()) },
        changePercent = quote?.changePercent?.takeIf { status.open },
        // Closed, and otherwise nothing. A closed market explains a missing number and is worth a
        // word; an open one with no quote already shows a dash where the price goes, and a second
        // dash under the first says the same thing twice.
        trailingNote = closedNote,
        // The list sits on the stage rather than inside a card, so the pill's tint is computed
        // against the stage. Against the wrong ground it is a different colour by a few percent —
        // small, and visible the moment two lists sit next to each other in a review.
        background = CoineProColors.Stage,
        horizontalPadding = CoineProSpacing.Gutter,
        onClick = onOpenSymbol?.let { open -> { open(row.meta.symbol) } },
    )
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
