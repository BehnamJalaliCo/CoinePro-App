package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProNote
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.SHEET_PREVIEW_SCRIM_ALPHA
import com.coinepro.core.designsystem.inEnglish
import com.coinepro.core.designsystem.numeric
import com.coinepro.core.script.ScriptFailure
import com.coinepro.core.script.ScriptInput
import com.coinepro.core.script.ScriptInputKind

/**
 * A script indicator's settings — **generated from its own `input(...)`s** (run I item 0.4).
 *
 * ### Why it is a separate sheet from the catalogue's
 *
 * They draw the same three tabs for the same reasons — Inputs change the arithmetic, Style changes
 * the ink, Visibility changes whether it is drawn and where — and everything under Style and
 * Visibility is literally the same behaviour, because a script instance is addressed by an
 * indicator id like any other study. What cannot be shared is the Inputs tab: a built-in's knobs
 * come from `ChartCatalog.parametersOf`, a fixed table with a label and bounds per key, and a
 * script's come from the script, discovered by *running* it. Folding a discovered list into a sheet
 * built around a fixed one would have made both harder to read than two files.
 *
 * ### What is here that a built-in's sheet has not got
 *
 * The **diagnostic**. A script can be wrong in a way a built-in cannot, so its sheet is where the
 * error goes: the line, the column, the code and the one-line fix, in the reader's language. And a
 * script that ran out of budget says so in its own words rather than reading as a mistake — see
 * [ChartScriptPause].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScriptSettingsSheet(
    script: ChartScript,
    inputs: List<ScriptInput>,
    failure: ScriptFailure?,
    paused: ChartScriptPause?,
    colour: Long?,
    widthDp: Float?,
    hidden: Boolean,
    arrangement: IndicatorArrangement?,
    onDismiss: () -> Unit,
    onSetInput: (String, Double?) -> Unit,
    onSetColour: (Long?) -> Unit,
    onSetWidth: (Float?) -> Unit,
    onToggleHidden: () -> Unit,
    onArrange: (IndicatorArrangement.Action) -> Unit,
    onRemove: () -> Unit,
    /** Open this instance's source in the studio. Null where there is no studio to open. */
    onEdit: (() -> Unit)? = null,
    /** The `alertcondition(...)`s this script named, and whether each holds on the last bar. */
    alerts: List<Pair<String, Boolean>> = emptyList(),
    /** Make an alert on one of those conditions. Null where this build has no alerts. */
    onCreateAlert: ((condition: String) -> Unit)? = null,
) {
    CoineProSheet(
        title = script.displayName,
        subtitle = stringResource(R.string.chart_script_kind),
        onDismiss = onDismiss,
        scrimAlpha = SHEET_PREVIEW_SCRIM_ALPHA,
    ) {
        var tab by rememberSaveable(script.instanceId) { mutableStateOf(IndicatorSettingsTab.INPUTS) }
        val accent = colour?.let { Color(it.toULong() shl COLOUR_SHIFT) } ?: CoineProColors.Gold
        val english = inEnglish()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = CoineProSpacing.Gutter)
                .padding(bottom = CoineProSpacing.Two),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        ) {
            // The refusal first, above the tabs, because a script that is not running is the only
            // fact about it that matters and burying it one tab in is how a reader concludes the
            // feature is broken rather than their line 12.
            paused?.let { stop ->
                ScriptNotice(
                    text = stringResource(R.string.chart_script_paused, stop.line.toString()),
                    tone = CoineProColors.Gold,
                    tag = "script-paused",
                )
            }
            failure?.takeIf { paused == null }?.let { error ->
                ScriptNotice(
                    text = "${error.text(english)} — ${error.line}:${error.column} · ${error.code}",
                    tone = CoineProColors.Sell,
                    tag = "script-failure",
                )
                error.hint(english).takeIf { it.isNotBlank() }?.let { hint ->
                    Text(hint, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextSecondary)
                }
            }

            CoineProSegmentedControl(
                options = listOf(
                    IndicatorSettingsTab.INPUTS to stringResource(R.string.indicator_settings_inputs),
                    IndicatorSettingsTab.STYLE to stringResource(R.string.drawing_settings_style),
                    IndicatorSettingsTab.VISIBILITY to stringResource(R.string.drawing_settings_visibility),
                ),
                selected = tab,
                onSelect = { tab = it },
            )
            when (tab) {
                IndicatorSettingsTab.INPUTS -> {
                    if (inputs.isEmpty()) {
                        Text(
                            text = stringResource(R.string.indicator_settings_no_inputs),
                            style = MaterialTheme.typography.bodySmall,
                            color = CoineProColors.TextMuted,
                        )
                    } else {
                        for (input in inputs) {
                            ScriptInputRow(
                                input = input,
                                value = script.overrides[input.name] ?: input.value,
                                accent = accent,
                                onChange = { onSetInput(input.name, it) },
                            )
                        }
                        CoineProNote(R.string.chart_script_inputs_note, style = MaterialTheme.typography.bodySmall)
                    }
                }

                IndicatorSettingsTab.STYLE -> {
                    Text(
                        text = stringResource(R.string.indicator_settings_colour),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                    )
                    // No «the catalogue's own» swatch to lead with, because a script has no
                    // catalogue row: its own first plot's colour is the default and clearing the
                    // override is what returns to it.
                    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                        DRAWING_COLOURS.take(SWATCHES_ACROSS * 2).chunked(SWATCHES_ACROSS).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                                row.forEach { value ->
                                    Box(
                                        modifier = Modifier
                                            .size(SWATCH)
                                            .clip(CircleShape)
                                            .background(Color(value.toULong() shl COLOUR_SHIFT))
                                            .border(
                                                width = if (value == colour) 2.dp else 1.dp,
                                                color = if (value == colour) CoineProColors.Gold else CoineProColors.Border,
                                                shape = CircleShape,
                                            )
                                            .clickable { onSetColour(if (value == colour) null else value) },
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = stringResource(R.string.indicator_settings_width),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                        INDICATOR_WIDTHS.forEach { width ->
                            val active = (widthDp ?: DEFAULT_LINE_WIDTH) == width
                            Box(
                                modifier = Modifier
                                    .clip(CoineProPillShape)
                                    .background(if (active) CoineProTint.fill(accent, CoineProColors.Surface) else Color.Transparent)
                                    .border(1.dp, if (active) CoineProTint.edge(accent) else CoineProColors.Border, CoineProPillShape)
                                    .clickable { onSetWidth(if (width == DEFAULT_LINE_WIDTH) null else width) }
                                    .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
                            ) {
                                Text(
                                    text = if (width == width.toInt().toFloat()) width.toInt().toString() else width.toString(),
                                    style = MaterialTheme.typography.labelSmall.numeric(),
                                    color = if (active) accent else CoineProColors.TextMuted,
                                )
                            }
                        }
                    }
                }

                IndicatorSettingsTab.VISIBILITY -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.indicator_settings_show),
                                style = MaterialTheme.typography.labelMedium,
                                color = CoineProColors.TextPrimary,
                            )
                            CoineProNote(R.string.indicator_settings_show_note, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = !hidden,
                            onCheckedChange = { onToggleHidden() },
                            colors = scriptSwitchColours(),
                        )
                    }
                    arrangement?.let { where ->
                        HorizontalDivider(color = CoineProColors.Border)
                        Text(
                            text = stringResource(R.string.indicator_settings_arrangement),
                            style = MaterialTheme.typography.labelMedium,
                            color = CoineProColors.TextPrimary,
                        )
                        if (where.overlayByDefault) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                            ) {
                                Text(
                                    text = stringResource(R.string.indicator_settings_separate),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = CoineProColors.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = where.separated,
                                    onCheckedChange = { on ->
                                        onArrange(
                                            if (on) IndicatorArrangement.Action.SEPARATE
                                            else IndicatorArrangement.Action.JOIN_PRICE,
                                        )
                                    },
                                    modifier = Modifier.semantics { contentDescription = "script-separate" },
                                    colors = scriptSwitchColours(),
                                )
                            }
                        }
                    }
                    // The script's own `alertcondition`s, on the Visibility tab beside the rest of
                    // what a reader does *to* an instance rather than *with* its numbers. Each row
                    // says whether it is holding right now, which is the one thing that turns «I
                    // wrote a condition» into «I can see it work».
                    if (alerts.isNotEmpty()) {
                        HorizontalDivider(color = CoineProColors.Border)
                        Text(
                            text = stringResource(R.string.chart_script_alerts),
                            style = MaterialTheme.typography.labelMedium,
                            color = CoineProColors.TextPrimary,
                        )
                        for ((condition, firing) in alerts) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "script-alert-$condition" },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = condition,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CoineProColors.TextPrimary,
                                    )
                                    Text(
                                        text = stringResource(
                                            if (firing) R.string.chart_script_alert_firing
                                            else R.string.chart_script_alert_quiet,
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (firing) CoineProColors.Buy else CoineProColors.TextMuted,
                                    )
                                }
                                onCreateAlert?.let { create ->
                                    CoineProSecondaryButton(
                                        text = stringResource(R.string.chart_script_alert_create),
                                        onClick = { create(condition) },
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = CoineProColors.Border)
                    onEdit?.let { edit ->
                        CoineProSecondaryButton(
                            text = stringResource(R.string.chart_script_edit),
                            onClick = edit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "script-edit" },
                        )
                    }
                    CoineProSecondaryButton(
                        text = stringResource(R.string.indicator_settings_remove),
                        onClick = onRemove,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** One `input(...)` as a control: a switch, a slider, or a row of choices. */
@Composable
private fun ScriptInputRow(input: ScriptInput, value: Double, accent: Color, onChange: (Double?) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(input.name, style = MaterialTheme.typography.labelMedium, color = CoineProColors.TextPrimary)
            when (input.kind) {
                ScriptInputKind.BOOL -> Switch(
                    checked = value != 0.0,
                    onCheckedChange = { onChange(if (it) 1.0 else 0.0) },
                    colors = scriptSwitchColours(),
                )
                ScriptInputKind.NUMBER, ScriptInputKind.INTEGER -> Text(
                    text = MarketNumberFormatter.priceAuto(value),
                    style = MaterialTheme.typography.labelMedium.numeric(),
                    color = accent,
                )
                else -> Unit
            }
        }
        when (input.kind) {
            ScriptInputKind.NUMBER, ScriptInputKind.INTEGER -> {
                val low = input.minimum
                val high = input.maximum
                if (low != null && high != null && high > low) {
                    val step = input.step ?: if (input.kind == ScriptInputKind.INTEGER) 1.0 else 0.0
                    val steps = if (step > 0) ((high - low) / step).toInt() - 1 else 0
                    Slider(
                        value = value.toFloat().coerceIn(low.toFloat(), high.toFloat()),
                        onValueChange = { at ->
                            onChange(if (step > 0) low + kotlin.math.round((at - low) / step) * step else at.toDouble())
                        },
                        valueRange = low.toFloat()..high.toFloat(),
                        steps = steps.coerceIn(0, 200),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            ScriptInputKind.TEXT, ScriptInputKind.SOURCE, ScriptInputKind.TIMEFRAME ->
                ScriptChoiceChips(input.options, value.toInt()) { onChange(it.toDouble()) }
            ScriptInputKind.COLOUR, ScriptInputKind.BOOL -> Unit
        }
    }
}

@Composable
private fun ScriptChoiceChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half), modifier = Modifier.fillMaxWidth()) {
        itemsIndexed(options) { index, option ->
            val chosen = index == selected
            Box(
                modifier = Modifier
                    .background(if (chosen) CoineProColors.SurfaceElevated else CoineProColors.Surface, CoineProShapes.small)
                    .border(1.dp, if (chosen) CoineProColors.Gold else CoineProColors.Border, CoineProShapes.small)
                    .clickable { onSelect(index) }
                    .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = if (chosen) CoineProColors.Gold else CoineProColors.TextSecondary,
                )
            }
        }
    }
}

/** A one-line banner in the sheet's own colours — a refusal, or a pause. */
@Composable
private fun ScriptNotice(text: String, tone: Color, tag: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(CoineProTint.fill(tone, CoineProColors.Surface), CoineProShapes.small)
            .border(1.dp, CoineProTint.edge(tone), CoineProShapes.small)
            .padding(CoineProSpacing.One)
            .semantics { contentDescription = tag },
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = tone)
    }
}

@Composable
private fun scriptSwitchColours() = SwitchDefaults.colors(
    checkedThumbColor = CoineProColors.OnAccent,
    checkedTrackColor = CoineProColors.AccentFill,
    uncheckedThumbColor = CoineProColors.TextMuted,
    uncheckedTrackColor = CoineProColors.SurfaceElevated,
)

/** This sheet's own swatch geometry. See `IndicatorSettingsSheet`'s note on why it is not shared. */
private const val SWATCHES_ACROSS = 6
private val SWATCH = 32.dp
private const val COLOUR_SHIFT = 32

/**
 * «My scripts» — the indicator sheet's own section for a reader's NamaScript studies (item 0.2).
 *
 * One row per script the reader could switch on, each a toggle: tapping a row that is off adds the
 * script to the chart, tapping one that is on takes it off, which is exactly what the row above it
 * does for EMA. The section is at the top of the sheet rather than the bottom because these are the
 * studies this particular reader wrote, and a list of eighty-three built-ins is not where you look
 * for your own work.
 *
 * A row is «on» when an instance of that **name** is on the chart — the same key `putScript` uses,
 * so the sheet and the button can never disagree about whether something is already there.
 */
@Composable
internal fun ScriptPickerSection(
    library: List<ChartScriptSource>,
    onChart: List<ChartScript>,
    onAdd: (ChartScriptSource) -> Unit,
    onRemove: (ChartScript) -> Unit,
) {
    if (library.isEmpty() && onChart.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Text(
            text = stringResource(R.string.chart_script_custom),
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextPrimary,
        )
        if (library.isEmpty()) {
            Text(
                text = stringResource(R.string.chart_script_none),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        val live = onChart.associateBy { it.name }
        for (entry in library) {
            val instance = live[entry.name]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (instance != null) onRemove(instance) else onAdd(entry) }
                    .padding(vertical = CoineProSpacing.One)
                    .semantics { contentDescription = "script-row-${entry.name}" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (instance != null) CoineProColors.Gold else CoineProColors.TextPrimary,
                    )
                    if (instance != null) {
                        Text(
                            text = stringResource(R.string.chart_script_on_chart),
                            style = MaterialTheme.typography.labelSmall,
                            color = CoineProColors.TextMuted,
                        )
                    }
                }
                Switch(
                    checked = instance != null,
                    onCheckedChange = { if (instance != null) onRemove(instance) else onAdd(entry) },
                    colors = scriptSwitchColours(),
                )
            }
        }
        HorizontalDivider(color = CoineProColors.Border)
    }
}
