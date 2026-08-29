package com.coinepro.feature.screener

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSheetSearch
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.feature.screener.model.NumericOp
import com.coinepro.feature.screener.model.ScreenerField
import com.coinepro.feature.screener.model.ScreenerFilter
import com.coinepro.feature.screener.model.ScreenerIndicatorId
import com.coinepro.feature.screener.model.ScreenerPresets
import com.coinepro.feature.screener.model.ScreenerScreen as SavedScreen

/**
 * Where a screen is built: the conditions, the presets, and the reader's saved screens.
 *
 * ### One sheet rather than three
 *
 * Conditions, presets and saved screens are the same act — deciding what the table shows — and
 * splitting them across three surfaces would make "start from a preset and change one number" a
 * journey. They are stacked in the order somebody actually uses them: what is on now, then how to
 * add to it, then what to start from, then what to keep.
 *
 * ### One primary action
 *
 * «اعمال» closes the sheet, and it is the only filled button in view. Everything else — adding a
 * condition, saving a screen, applying a preset — is a neutral control, because filtering happens
 * live as each condition is added and none of those is the act the reader came here to finish.
 *
 * ### Nothing here is gated
 *
 * There is no membership check in this file, no counter on saved screens, and no indicator the
 * sheet declines to offer. The free note under the title says so in the product's own voice, so a
 * reader who has met the competitor's paywall knows before they invest any effort.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreenerFilterSheet(
    state: ScreenerState,
    onDismiss: () -> Unit,
    onSetFilters: (List<ScreenerFilter>) -> Unit,
    onApplyScreen: (SavedScreen) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    CoineProSheet(
        title = stringResource(R.string.screener_filter_sheet_title),
        subtitle = stringResource(R.string.screener_filter_sheet_subtitle),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CoineProSpacing.Gutter),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        ) {
            SearchCondition(
                query = textQuery(state.filters),
                onQuery = { query -> onSetFilters(withTextMatch(state.filters, query)) },
            )

            ActiveConditions(
                filters = state.filters,
                onRemove = { index -> onSetFilters(state.filters.filterIndexed { at, _ -> at != index }) },
            )

            ConditionBuilder(onAdd = { filter -> onSetFilters(state.filters + filter) })

            IndicatorBuilder(
                hasVolume = state.feedHasVolume,
                onAdd = { filter -> onSetFilters(state.filters + filter) },
            )

            SectionLabel(stringResource(R.string.screener_presets))
            CoineProChipRow(
                options = ScreenerPresets.all.map { CoineProChip(it.id, it.name) },
                selectedId = state.activeScreenId,
                onSelect = { id -> ScreenerPresets.all.firstOrNull { it.id == id }?.let(onApplyScreen) },
                compact = true,
            )

            SavedScreens(
                saved = state.saved,
                activeId = state.activeScreenId,
                onApply = onApplyScreen,
                onDelete = onDelete,
                onSave = onSave,
            )

            Text(
                text = stringResource(R.string.screener_free_note),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )

            CoineProPrimaryButton(
                text = stringResource(R.string.screener_apply),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().padding(bottom = CoineProSpacing.Two),
            )
        }
    }
}

/**
 * The free-text condition.
 *
 * A search box rather than a row in the condition list, because that is the shape everybody already
 * knows and because the condition it writes is a single [ScreenerFilter.TextMatch] that replaces
 * itself. Behind it is `core:symbols`' ranked matcher, so «طلا» finds XAUUSD exactly as it does on
 * the search screen.
 */
@Composable
private fun SearchCondition(query: String, onQuery: (String) -> Unit) {
    CoineProSheetSearch(
        value = query,
        onValueChange = onQuery,
        placeholder = stringResource(R.string.screener_search_placeholder),
        modifier = Modifier.padding(top = CoineProSpacing.One),
    )
}

/** The conditions currently narrowing the table, each with the one control that removes it. */
@Composable
private fun ActiveConditions(filters: List<ScreenerFilter>, onRemove: (Int) -> Unit) {
    val rows = filters.withIndex().filterNot { it.value is ScreenerFilter.TextMatch }
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
        rows.forEach { (index, filter) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CoineProShapes.small)
                    .background(CoineProColors.SurfaceElevated)
                    .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                Text(
                    text = describe(filter),
                    style = MaterialTheme.typography.labelMedium,
                    color = CoineProColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(CoineProIcons.Close),
                    contentDescription = stringResource(R.string.screener_delete),
                    tint = CoineProColors.TextMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CoineProShapes.extraSmall)
                        .clickable { onRemove(index) },
                )
            }
        }
    }
}

/**
 * The one place a condition is written.
 *
 * A field, an operator and a number, in that order, because that is the order the sentence reads in
 * Persian. The period box appears only for a field that is computed from a series — which is also
 * the switch that decides whether this builds a [ScreenerFilter.Numeric] or the indicator filter
 * [109] is about: an RSI condition with its own lookback is a different question from the RSI
 * column's, and both are free.
 */
@Composable
private fun ConditionBuilder(onAdd: (ScreenerFilter) -> Unit) {
    var field by remember { mutableStateOf(ScreenerField.CHANGE_PERCENT) }
    var op by remember { mutableStateOf(NumericOp.GT) }
    var value by remember { mutableStateOf("") }
    var bound by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("") }

    SectionLabel(stringResource(R.string.screener_field))
    CoineProChipRow(
        // The day's own figures only. The indicator-derived fields moved to [IndicatorBuilder],
        // which offers all eighty-three rather than the eight that happen to have a column —
        // see [115]. They are still columns, still sortable and still saved; they are simply no
        // longer two routes to writing the same condition.
        options = ScreenerField.NUMERIC.filterNot(ScreenerField::isDerived)
            .map { CoineProChip(it.name, it.label) },
        selectedId = field.name,
        onSelect = { id -> ScreenerField.entries.firstOrNull { it.name == id }?.let { field = it } },
        compact = true,
    )

    SectionLabel(stringResource(R.string.screener_operator))
    CoineProChipRow(
        options = NumericOp.entries.map { CoineProChip(it.name, it.label) },
        selectedId = op.name,
        onSelect = { id -> NumericOp.entries.firstOrNull { it.name == id }?.let { op = it } },
        compact = true,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        CoineProTextField(
            value = value,
            // Folded on the way in, not on the way out. A Persian keyboard produces ۰-۹ by default,
            // and `toDoubleOrNull` refuses those — so the field would look correct and the condition
            // would silently never be added.
            onValueChange = { value = it.foldDigitsToLatin() },
            label = stringResource(R.string.screener_value),
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (op.takesSecondValue) {
            CoineProTextField(
                value = bound,
                onValueChange = { bound = it.foldDigitsToLatin() },
                label = stringResource(R.string.screener_second_value),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        if (field.isDerived) {
            CoineProTextField(
                value = period,
                onValueChange = { period = it.foldDigitsToLatin() },
                label = stringResource(R.string.screener_period),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supporting = field.defaultPeriod?.toString(),
            )
        }
    }

    CoineProSecondaryButton(
        text = stringResource(R.string.screener_add_condition),
        onClick = {
            buildFilter(field, op, value, bound, period)?.let { filter ->
                onAdd(filter)
                value = ""
                bound = ""
                period = ""
            }
        },
        icon = CoineProIcons.Add,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * The indicator condition builder — [115], and the whole of what the competition sells.
 *
 * ### Why this is a search box and not another chip row
 *
 * Because there are eighty-three of them. A chip row is the right control for six asset classes and
 * the wrong one for a catalogue: the reader who wants «استوکاستیک RSI» would have to scroll a strip
 * past sixty chips they do not want, twice, once to find it and once to check they had not passed
 * it. The chart's own indicator picker grew a search field for the same reason and at the same
 * size, and the two now behave the same way — a plain substring over the Persian name and the Latin
 * id, so «rsi» and «قدرت» find the same row.
 *
 * ### What is not offered says why
 *
 * Under the picker is the list of indicators this screener will not filter on, grouped by reason —
 * structure studies that draw levels rather than a value, the correlation coefficient that needs a
 * second symbol, and on a feed with no volume column, the volume studies. Printing them is the
 * point: a reader who looks for «پیووت» and does not find it should learn that it has no single
 * number per market, not conclude that the app forgot it. And an indicator that *is* listed always
 * produces a real reading — nothing here is offered that would silently score every market as zero,
 * which is the failure `ChartCatalog.VOLUME_ONLY_INDICATORS` exists to prevent.
 *
 * ### The period is the indicator's own
 *
 * Each indicator carries its own default and its own bounds, so the box under «EMA» suggests 20 and
 * the one under «همبستگی» would start at 5. An indicator whose shape is a fixed set of periods —
 * MACD's 12/26/9, the Awesome Oscillator's 5/34 — is shown no period box at all rather than one
 * that changes nothing, which is the rule `ChartCatalog.PERIODS` states for the chart.
 */
@Composable
private fun IndicatorBuilder(hasVolume: Boolean, onAdd: (ScreenerFilter) -> Unit) {
    var query by remember { mutableStateOf("") }
    var indicatorId by remember { mutableStateOf(ScreenerIndicatorId.RSI) }
    var op by remember { mutableStateOf(NumericOp.LT) }
    var value by remember { mutableStateOf("") }
    var bound by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("") }

    val offered = ScreenerIndicatorCatalog.matching(query, hasVolume)
    val selected = offered.firstOrNull { it.id == indicatorId }
        ?: ScreenerIndicatorCatalog.optionOf(indicatorId)

    SectionLabel(stringResource(R.string.screener_indicator))
    CoineProSheetSearch(
        value = query,
        onValueChange = { query = it },
        placeholder = stringResource(R.string.screener_indicator_search),
    )
    if (offered.isEmpty()) {
        Text(
            text = stringResource(R.string.screener_indicator_none),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
    } else {
        CoineProChipRow(
            options = offered.map { CoineProChip(it.id, it.label) },
            selectedId = selected?.id,
            onSelect = { id -> id?.let { indicatorId = it } },
            compact = true,
        )
    }

    SectionLabel(stringResource(R.string.screener_operator))
    CoineProChipRow(
        options = NumericOp.entries.map { CoineProChip(it.name, it.label) },
        selectedId = op.name,
        onSelect = { id -> NumericOp.entries.firstOrNull { it.name == id }?.let { op = it } },
        compact = true,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        CoineProTextField(
            value = value,
            // Folded on the way in, as everywhere else on this sheet: a Persian keyboard produces
            // ۰-۹ and `toDoubleOrNull` refuses them, so the box would look filled and the condition
            // would never be added.
            onValueChange = { value = it.foldDigitsToLatin() },
            label = stringResource(R.string.screener_value),
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (op.takesSecondValue) {
            CoineProTextField(
                value = bound,
                onValueChange = { bound = it.foldDigitsToLatin() },
                label = stringResource(R.string.screener_second_value),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        if (selected?.takesPeriod == true) {
            CoineProTextField(
                value = period,
                onValueChange = { period = it.foldDigitsToLatin() },
                label = stringResource(R.string.screener_period),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supporting = selected.defaultPeriod?.toString(),
            )
        }
    }

    CoineProSecondaryButton(
        text = stringResource(R.string.screener_add_indicator),
        onClick = {
            val option = selected ?: return@CoineProSecondaryButton
            buildIndicatorFilter(option, op, value, bound, period)?.let { filter ->
                onAdd(filter)
                value = ""
                bound = ""
                period = ""
            }
        },
        icon = CoineProIcons.Add,
        modifier = Modifier.fillMaxWidth(),
    )

    WithheldIndicators(hasVolume)
}

/**
 * The indicators the screener does not offer, each under the reason it does not.
 *
 * Grouped by reason rather than listed flat, so the three sentences are said once each instead of
 * twenty-three times. Prose, so no digits appear in it at all — the names are the content and a
 * count would only invite the reader to check it.
 */
@Composable
private fun WithheldIndicators(hasVolume: Boolean) {
    val withheld = ScreenerIndicatorCatalog.withheld(hasVolume)
    if (withheld.isEmpty()) return
    SectionLabel(stringResource(R.string.screener_indicator_absent))
    withheld.groupBy { it.why }.forEach { (why, rows) ->
        Text(
            text = stringResource(
                R.string.screener_indicator_absent_line,
                why.reason,
                rows.joinToString("، ") { it.label },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
    }
}

/** The reader's own screens, with the row that saves the current one beside them. */
@Composable
private fun SavedScreens(
    saved: List<SavedScreen>,
    activeId: String?,
    onApply: (SavedScreen) -> Unit,
    onDelete: (String) -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    SectionLabel(stringResource(R.string.screener_saved))
    saved.forEach { screen ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CoineProShapes.small)
                .background(CoineProColors.SurfaceElevated)
                .clickable { onApply(screen) }
                .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Text(
                text = screen.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (screen.id == activeId) CoineProColors.Accent else CoineProColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(CoineProIcons.Delete),
                contentDescription = stringResource(R.string.screener_delete),
                tint = CoineProColors.TextMuted,
                modifier = Modifier
                    .size(16.dp)
                    .clip(CoineProShapes.extraSmall)
                    .clickable { onDelete(screen.id) },
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoineProTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.screener_save_name),
            modifier = Modifier.weight(1f),
        )
        CoineProSecondaryButton(
            text = stringResource(R.string.screener_save),
            onClick = {
                onSave(name)
                name = ""
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        modifier = Modifier.padding(top = CoineProSpacing.Half),
    )
}

/**
 * Turns what the sheet is holding into a condition, or null when it is not yet a condition.
 *
 * Pure and internal, so the one piece of judgement in this file — that a derived field builds the
 * indicator filter rather than a plain threshold — is a unit test rather than something only a
 * person tapping the screen can check.
 *
 * An unparseable number answers null and the button does nothing, which is deliberately quieter
 * than an error: the field is right there, empty or half-typed, and a red sentence under a box the
 * reader has not finished filling in is a reprimand.
 */
internal fun buildFilter(
    field: ScreenerField,
    op: NumericOp,
    value: String,
    bound: String,
    period: String,
): ScreenerFilter? {
    val number = value.trim().toDoubleOrNull() ?: return null
    val second = bound.trim().toDoubleOrNull()
    if (op.takesSecondValue && second == null) return null
    val indicatorId = field.indicatorId
    return if (indicatorId != null) {
        ScreenerFilter.IndicatorFilter(
            indicatorId = indicatorId,
            period = period.trim().toIntOrNull() ?: field.defaultPeriod,
            op = op,
            value = number,
            bound = second,
        )
    } else {
        ScreenerFilter.Numeric(field = field, op = op, value = number, bound = second)
    }
}

/**
 * One indicator condition, or null when the sheet is not holding one yet.
 *
 * Pure and internal for the same reason [buildFilter] is: the judgement in it — that a blank period
 * box means the indicator's own default rather than no lookback at all — is a unit test rather than
 * something only a person tapping the screen can check. The period is clamped into the indicator's
 * own bounds, which are not the same for every one of them, so a reader who types 1 into an EMA
 * gets the two-bar minimum the engine will actually compute rather than a condition that answers
 * null for every market.
 */
internal fun buildIndicatorFilter(
    option: ScreenerIndicatorCatalog.Option,
    op: NumericOp,
    value: String,
    bound: String,
    period: String,
): ScreenerFilter? {
    val number = value.trim().toDoubleOrNull() ?: return null
    val second = bound.trim().toDoubleOrNull()
    if (op.takesSecondValue && second == null) return null
    val chosen = period.trim().toIntOrNull()?.coerceIn(option.minPeriod, option.maxPeriod)
    return ScreenerFilter.IndicatorFilter(
        indicatorId = option.id,
        period = if (option.takesPeriod) chosen ?: option.defaultPeriod else null,
        op = op,
        value = number,
        bound = second,
    )
}

/** The text condition currently in [filters], or an empty string when there is none. */
internal fun textQuery(filters: List<ScreenerFilter>): String =
    filters.filterIsInstance<ScreenerFilter.TextMatch>().firstOrNull()?.query.orEmpty()

/**
 * [filters] with its text condition replaced by [query], or removed when [query] is blank.
 *
 * Removed rather than kept as an empty match: a blank [ScreenerFilter.TextMatch] matches everything
 * by design, but leaving one in the list would show a condition row that does nothing and would
 * make [ScreenerState.narrowed] claim the table is filtered when it is not.
 */
internal fun withTextMatch(filters: List<ScreenerFilter>, query: String): List<ScreenerFilter> {
    val without = filters.filterNot { it is ScreenerFilter.TextMatch }
    return if (query.isBlank()) without else without + ScreenerFilter.TextMatch(query)
}

/**
 * One condition as a sentence a reader can check.
 *
 * Persian words, Latin numbers — the app's rule, and this is the row where the two meet. The
 * period is spelled into the field name for an indicator condition, because «شاخص قدرت نسبی» with
 * no number beside it does not say which of the reader's two RSI conditions this is.
 */
internal fun describe(filter: ScreenerFilter): String = when (filter) {
    is ScreenerFilter.Numeric -> buildString {
        append(filter.field.label)
        append(' ')
        append(filter.op.label)
        append(' ')
        append(ScreenerFormat.threshold(filter.value))
        if (filter.op.takesSecondValue && filter.bound != null) {
            append(" — ")
            append(ScreenerFormat.threshold(filter.bound))
        }
    }

    is ScreenerFilter.IndicatorFilter -> buildString {
        append(labelOf(filter.indicatorId))
        filter.period?.let {
            append(' ')
            append(ScreenerFormat.threshold(it.toDouble()))
        }
        append(' ')
        append(filter.op.label)
        append(' ')
        append(ScreenerFormat.threshold(filter.value))
        if (filter.op.takesSecondValue && filter.bound != null) {
            append(" — ")
            append(ScreenerFormat.threshold(filter.bound))
        }
    }

    is ScreenerFilter.Category -> filter.field.label + ": " + filter.values.sorted().joinToString("، ")

    is ScreenerFilter.TextMatch -> filter.query
}

/**
 * The Persian name of an indicator, wherever it comes from.
 *
 * The chart's catalogue first, the eight legacy fields second, the raw id last. Before [115] this
 * knew only the eight, so a condition on any of the other seventy-five would have printed a bare
 * id in the middle of a Persian sentence.
 */
private fun labelOf(indicatorId: String): String = ScreenerIndicatorCatalog.labelOf(indicatorId)
