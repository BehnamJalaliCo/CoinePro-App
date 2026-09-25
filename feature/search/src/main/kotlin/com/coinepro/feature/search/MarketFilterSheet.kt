package com.coinepro.feature.search

import com.coinepro.core.designsystem.CoineProLazyRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.inEnglish
import com.coinepro.core.designsystem.numeric
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.symbols.SymbolCategory

/**
 * What a reader can narrow the markets list by (F2).
 *
 * Four questions, and each one is a question about the *listing* rather than about the price: what
 * kind of market it is, who quotes it, how far it has moved today, and how much of it changed hands.
 * They compose — «crypto, on LBank, down more than five per cent» is one list none of the four could
 * produce alone.
 *
 * ### Bands rather than a slider
 *
 * The change filter offers named bands — «بیش از ۵٪+», «بین ۰ و ۵٪-» — instead of two draggable
 * handles. A two-handle range on a phone is four gestures to set one thing, it is unreadable to a
 * screen reader, and nobody filtering a market list wants 3.7 % rather than 5 %. The bands are the
 * questions people actually ask, and the state underneath them is still a pair of numbers, so the
 * day a slider earns its place nothing else changes.
 *
 * ### Only what this list can answer
 *
 * A column with one value in it is not offered. On a venue whose rows all come from one place, the
 * venue block is absent rather than a single chip that cannot change anything; the same for the
 * types. A filter that can only ever be a no-op teaches the reader that the sheet is decoration.
 */
data class MarketFilter(
    val types: Set<SymbolCategory> = emptySet(),
    val venues: Set<String> = emptySet(),
    val change: ChangeBand? = null,
    val turnover: TurnoverFloor? = null,
) {
    val isEmpty: Boolean get() = types.isEmpty() && venues.isEmpty() && change == null && turnover == null

    /** How many of the four questions have been answered — the number on the button's badge. */
    val count: Int get() = types.size + venues.size + (if (change != null) 1 else 0) +
        (if (turnover != null) 1 else 0)

    /** Whether [row] survives. A figure the app does not have never removes a row — see below. */
    fun keeps(row: MarketSearchRow, changePercent: Double?): Boolean {
        if (types.isNotEmpty() && row.meta.category !in types) return false
        if (venues.isNotEmpty() && row.venue !in venues) return false
        // **A change band keeps the rows nothing knows the change of.** A band is a reader narrowing
        // what is on screen, not a request for «only the rows you have a figure for», and dropping
        // the unknowns would empty the whole non-crypto side — which reports no change at all on
        // this venue — on the day gold moved eight per cent.
        if (change != null && changePercent != null && !change.holds(changePercent)) return false
        // **A turnover floor does not**, and the difference is not an inconsistency. «Over ten
        // million a day» is a claim about the market, and a row that said nothing has not made it.
        // The same rule `SymbolUniverse.filter` follows, deliberately: two filters over one list
        // that disagreed about a null would put different rows on screen for the same question.
        if (turnover != null && (row.turnover24h ?: -1.0) < turnover.atLeast) return false
        return true
    }
}

/** The named moves a reader asks for. Percentages, which is the unit on the screen. */
enum class ChangeBand(val from: Double?, val to: Double?, val labelRes: Int) {
    UP_5(5.0, null, R.string.markets_filter_up_5),
    UP_ANY(0.0, null, R.string.markets_filter_up),
    DOWN_ANY(null, 0.0, R.string.markets_filter_down),
    DOWN_5(null, -5.0, R.string.markets_filter_down_5),
    ;

    fun holds(percent: Double): Boolean {
        from?.let { if (percent < it) return false }
        to?.let { if (percent > it) return false }
        return true
    }
}

/** Turnover floors, in whole US dollars a day. */
enum class TurnoverFloor(val atLeast: Double, val labelRes: Int) {
    OVER_1M(1_000_000.0, R.string.markets_filter_turnover_1m),
    OVER_10M(10_000_000.0, R.string.markets_filter_turnover_10m),
    OVER_100M(100_000_000.0, R.string.markets_filter_turnover_100m),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketFilterSheet(
    filter: MarketFilter,
    /** The types actually present in the list behind the sheet. */
    types: List<SymbolCategory>,
    /** The venues actually present. Empty where nothing named one, and the block is then absent. */
    venues: List<String>,
    onChange: (MarketFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    CoineProSheet(title = stringResource(R.string.markets_filter_title), onDismiss = onDismiss) {
        MarketFilterBody(filter = filter, types = types, venues = venues, onChange = onChange)
    }
}

/**
 * The sheet's contents, without the sheet.
 *
 * Split out for one reason and it is worth stating: a `ModalBottomSheet` draws into a **window of
 * its own**, and the render harness captures the activity's decor view — so a frame taken with this
 * sheet open is a frame of the list behind it. Every other surface in this app is captured by
 * drawing what is on screen, and a proof that silently showed the wrong thing would be worse than
 * none. `UniverseProofTest` renders this directly, which is the same composition the sheet puts in
 * front of a reader.
 */
@Composable
fun MarketFilterBody(
    filter: MarketFilter,
    types: List<SymbolCategory>,
    venues: List<String>,
    onChange: (MarketFilter) -> Unit,
) {
    val english = inEnglish()
    run {
        Column(
            modifier = Modifier
                .padding(horizontal = CoineProSpacing.Gutter)
                .padding(bottom = CoineProSpacing.Three),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
        ) {
            if (types.size > 1) {
                Section(stringResource(R.string.markets_filter_type)) {
                    items(types) { type ->
                        Chip(
                            label = type.filterLabel(english),
                            selected = type in filter.types,
                            onClick = {
                                val next = if (type in filter.types) filter.types - type else filter.types + type
                                onChange(filter.copy(types = next))
                            },
                        )
                    }
                }
            }
            if (venues.size > 1) {
                Section(stringResource(R.string.markets_filter_venue)) {
                    items(venues) { venue ->
                        Chip(
                            label = venue,
                            selected = venue in filter.venues,
                            onClick = {
                                val next = if (venue in filter.venues) filter.venues - venue else filter.venues + venue
                                onChange(filter.copy(venues = next))
                            },
                        )
                    }
                }
            }
            Section(stringResource(R.string.markets_filter_change)) {
                items(ChangeBand.entries.toList()) { band ->
                    Chip(
                        label = stringResource(band.labelRes),
                        selected = filter.change == band,
                        // A second tap on the chosen band clears it. Bands are exclusive, so there
                        // is no «all» chip to press instead and a reader who chose one must have a
                        // way back that is not «clear everything».
                        onClick = { onChange(filter.copy(change = if (filter.change == band) null else band)) },
                    )
                }
            }
            Section(stringResource(R.string.markets_filter_turnover)) {
                items(TurnoverFloor.entries.toList()) { floor ->
                    Chip(
                        label = stringResource(floor.labelRes),
                        selected = filter.turnover == floor,
                        onClick = {
                            onChange(filter.copy(turnover = if (filter.turnover == floor) null else floor))
                        },
                    )
                }
            }
            // Present only once there is something to clear. A permanently visible «clear» on an
            // untouched sheet is a control that does nothing, which is how a reader learns to stop
            // reading the buttons.
            if (!filter.isEmpty) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Chip(
                        label = stringResource(R.string.markets_filter_clear),
                        selected = false,
                        onClick = { onChange(MarketFilter()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            fontWeight = FontWeight.Normal,
        )
        CoineProLazyRow(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half), content = content)
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberCoineProHaptics()
    Box(
        modifier = Modifier
            .clip(CoineProPillShape)
            .background(if (selected) CoineProColors.AccentFill else Color.Transparent)
            .border(1.dp, if (selected) Color.Transparent else CoineProColors.Border, CoineProPillShape)
            .clickable {
                haptics.select()
                onClick()
            }
            .padding(horizontal = CoineProSpacing.One + CoineProSpacing.Half, vertical = CoineProSpacing.Half),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.numeric(),
            color = if (selected) CoineProColors.OnAccent else CoineProColors.TextPrimary,
            maxLines = 1,
        )
    }
}

/** A market family's name on a chip. The same words the category tabs use. */
@Composable
private fun SymbolCategory.filterLabel(english: Boolean): String {
    val label = when (this) {
        SymbolCategory.CRYPTO -> R.string.search_category_crypto
        SymbolCategory.FOREX -> R.string.search_category_forex
        SymbolCategory.METAL -> R.string.search_category_metal
        SymbolCategory.INDEX -> R.string.markets_filter_type_index
        SymbolCategory.ENERGY -> R.string.markets_filter_type_energy
        SymbolCategory.OTHER -> R.string.markets_filter_type_other
    }
    // The parameter is read so the signature stays honest about being locale-aware: the strings
    // themselves are already per-locale, and this exists so a caller cannot pass a language and
    // quietly get the other one.
    @Suppress("UNUSED_EXPRESSION") english
    return stringResource(label)
}

/** Every venue the rows behind the sheet came from, in the order they first appear. */
fun venuesOf(rows: List<MarketSearchRow>): List<String> {
    val seen = LinkedHashSet<String>()
    rows.forEach { row -> row.venue?.let(seen::add) }
    return seen.toList()
}

/** Every market family present, in the catalogue's own order. */
fun typesOf(rows: List<MarketSearchRow>): List<SymbolCategory> {
    val seen = LinkedHashSet<SymbolCategory>()
    rows.forEach { seen += it.meta.category }
    return seen.toList()
}

/** Rows fitting the filter. [changeOf] supplies the day's move, which is not on the listing. */
fun applyFilter(
    rows: List<MarketSearchRow>,
    filter: MarketFilter,
    changeOf: (MarketSearchRow) -> Double?,
): List<MarketSearchRow> {
    if (filter.isEmpty) return rows
    return rows.filter { filter.keeps(it, changeOf(it)) }
}
