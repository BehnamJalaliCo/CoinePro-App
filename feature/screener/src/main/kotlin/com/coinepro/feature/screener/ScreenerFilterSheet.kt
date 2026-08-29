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
        options = ScreenerField.NUMERIC.map { CoineProChip(it.name, it.label) },
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

/** The Persian name of an indicator, or its id where a later build wrote one this one lacks. */
private fun labelOf(indicatorId: String): String =
    ScreenerField.entries.firstOrNull { it.indicatorId == indicatorId }?.label ?: indicatorId
