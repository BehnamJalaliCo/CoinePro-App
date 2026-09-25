package com.coinepro.feature.chart

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.IndicatorOption
import com.coinepro.core.designsystem.CoineProChipDefaults
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProNotedLabel
import com.coinepro.core.designsystem.CoineProSwitch
import com.coinepro.core.designsystem.CoineProToggleChip
import com.coinepro.core.designsystem.inEnglish
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.SHEET_PREVIEW_SCRIM_ALPHA
import com.coinepro.core.designsystem.coineProControl

/** The three pages of an indicator's settings, as the reference names them. */
enum class IndicatorSettingsTab { INPUTS, STYLE, VISIBILITY }

/**
 * One switched-on indicator's settings, opened from its legend row's gear.
 *
 * ### Inputs · Style · Visibility
 *
 * The reference's three, and the split is by what changes: the *inputs* change the arithmetic
 * (the lookback), the *style* changes the ink (colour, stroke), *visibility* changes whether the
 * study is drawn at all without forgetting either. The sheet scrims the chart at twenty per cent
 * rather than forty so a reader nudging an average from 20 to 50 sees it move.
 *
 * ### What is not here
 *
 * An indicator's chain source — «RSI روی EMA» — stays in the studio's own section, because it
 * is a relation between two studies and a sheet about one of them would show half of it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IndicatorSettingsSheet(
    option: IndicatorOption,
    period: Int?,
    colour: Long?,
    widthDp: Float?,
    hidden: Boolean,
    onDismiss: () -> Unit,
    onSetPeriod: (Int?) -> Unit,
    onSetColour: (Long?) -> Unit,
    /** The reader's other knobs for this indicator, by key — see `ChartCatalog.parametersOf`. */
    params: Map<String, Double> = emptyMap(),
    onSetParam: (String, Double?) -> Unit = { _, _ -> },
    /** Where the study sits among the panes and what may be done about it; null hides the section. */
    arrangement: IndicatorArrangement? = null,
    onArrange: (IndicatorArrangement.Action) -> Unit = {},
    onSetWidth: (Float?) -> Unit,
    onToggleHidden: () -> Unit,
    onRemove: () -> Unit,
) {
    val english = inEnglish()
    // What the study looked like when the sheet opened, so Cancel can put it back: every change
    // previews live on the chart behind the scrim, and «Cancel» has to mean «as it was».
    val opened = remember(option.id) { IndicatorSnapshot(period, colour, widthDp, params, hidden) }
    val defaults = {
        onSetPeriod(null)
        ChartCatalog.parametersOf(option.id).filter { it.key != ChartCatalog.LENGTH }.forEach { onSetParam(it.key, null) }
        onSetColour(null)
        onSetWidth(null)
    }
    CoineProSheet(
        title = option.label(english),
        subtitle = ChartCatalog.categoryOf(option.id).label(english),
        onDismiss = onDismiss,
        scrimAlpha = SHEET_PREVIEW_SCRIM_ALPHA,
    ) {
        IndicatorSettingsBody(
            option = option,
            period = period,
            colour = colour,
            widthDp = widthDp,
            hidden = hidden,
            onSetPeriod = onSetPeriod,
            onSetColour = onSetColour,
            params = params,
            onSetParam = onSetParam,
            arrangement = arrangement,
            onArrange = onArrange,
            onSetWidth = onSetWidth,
            onToggleHidden = onToggleHidden,
            onRemove = onRemove,
            onDefaults = defaults,
            onCancel = {
                onSetPeriod(opened.period)
                ChartCatalog.parametersOf(option.id).filter { it.key != ChartCatalog.LENGTH }
                    .forEach { onSetParam(it.key, opened.params[it.key]) }
                onSetColour(opened.colour)
                onSetWidth(opened.widthDp)
                if (hidden != opened.hidden) onToggleHidden()
                onDismiss()
            },
            onOk = onDismiss,
        )
    }
}

/** A study's settings as the sheet found them. */
private data class IndicatorSnapshot(
    val period: Int?,
    val colour: Long?,
    val widthDp: Float?,
    val params: Map<String, Double>,
    val hidden: Boolean,
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun IndicatorSettingsBody(
    option: IndicatorOption,
    period: Int?,
    colour: Long?,
    widthDp: Float?,
    hidden: Boolean,
    onSetPeriod: (Int?) -> Unit,
    onSetColour: (Long?) -> Unit,
    onSetWidth: (Float?) -> Unit,
    onToggleHidden: () -> Unit,
    onRemove: () -> Unit,
    initialTab: IndicatorSettingsTab = IndicatorSettingsTab.INPUTS,
    params: Map<String, Double> = emptyMap(),
    onSetParam: (String, Double?) -> Unit = { _, _ -> },
    arrangement: IndicatorArrangement? = null,
    onArrange: (IndicatorArrangement.Action) -> Unit = {},
    /** The footer's three; with no [onOk] there is no footer (a preview, an inline panel). */
    onDefaults: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onOk: (() -> Unit)? = null,
) {
    var tab by rememberSaveable(option.id) { mutableStateOf(initialTab) }
    val accent = Color(option.colour.toULong() shl COLOUR_SHIFT)
    val english = inEnglish()
    Column(modifier = Modifier.fillMaxWidth()) {
        ChartDialogTabs(
            options = listOf(
                IndicatorSettingsTab.INPUTS to stringResource(R.string.indicator_settings_inputs),
                IndicatorSettingsTab.STYLE to stringResource(R.string.drawing_settings_style),
                IndicatorSettingsTab.VISIBILITY to stringResource(R.string.drawing_settings_visibility),
            ),
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
        )
        ChartPinnedFooter(
            footer = onOk?.let { ok ->
                { ChartDialogFooter(onCancel = onCancel ?: ok, onOk = ok, onDefaults = onDefaults) }
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CoineProSpacing.Gutter)
                    .padding(top = CoineProSpacing.OneHalf, bottom = CoineProSpacing.Two),
                verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
            ) {
                when (tab) {
                    IndicatorSettingsTab.INPUTS -> {
                        // Every knob the catalogue names for this study — the length first, then MACD's
                        // fast/slow/signal, a band's deviation, Ichimoku's three spans (run E). The chart
                        // behind the twenty-per-cent scrim redraws on each change.
                        val bounds = ChartCatalog.periodOf(option.id)
                        val parameters = ChartCatalog.parametersOf(option.id)
                        if (parameters.isEmpty()) {
                            Text(
                                text = stringResource(R.string.indicator_settings_no_inputs),
                                style = MaterialTheme.typography.bodySmall,
                                color = CoineProColors.TextMuted,
                            )
                        } else {
                            for (spec in parameters) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                                ) {
                                    if (spec.key == ChartCatalog.LENGTH) {
                                        // The note explains the length, so its ⓘ sits on the length's own
                                        // line rather than alone under the last field (MOBILE-14).
                                        CoineProNotedLabel(
                                            label = stringResource(R.string.indicator_settings_length),
                                            note = R.string.indicator_settings_length_note,
                                            modifier = Modifier.weight(1f),
                                        )
                                    } else {
                                        Text(
                                            text = if (english) spec.labelEn else spec.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = CoineProColors.TextPrimary,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (spec.key == ChartCatalog.LENGTH && bounds != null) {
                                        ChartNumberField(
                                            value = (period ?: bounds.default).toDouble(),
                                            min = bounds.min.toDouble(),
                                            max = bounds.max.toDouble(),
                                            step = 1.0,
                                            decimals = 0,
                                            onChange = { next -> onSetPeriod(next.toInt()) },
                                            tag = "indicator-param-${spec.key}",
                                        )
                                    } else {
                                        ChartNumberField(
                                            value = params[spec.key] ?: spec.default,
                                            min = spec.min,
                                            max = spec.max,
                                            step = spec.step,
                                            decimals = if (spec.integer) 0 else decimalsOf(spec.step),
                                            onChange = { next -> onSetParam(spec.key, next) },
                                            tag = "indicator-param-${spec.key}",
                                        )
                                    }
                                }
                            }
                        }
                    }
                    IndicatorSettingsTab.STYLE -> {
                        Text(
                            text = stringResource(R.string.indicator_settings_colour),
                            style = MaterialTheme.typography.labelSmall,
                            color = CoineProColors.TextMuted,
                        )
                        val chosen = colour ?: option.colour
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                        ) {
                            (listOf(option.colour) + DRAWING_COLOURS.filter { it != option.colour }.take(SWATCHES_ACROSS * 2 - 1))
                                .forEach { value ->
                                    ChartColourSwatch(
                                        colour = Color(value.toULong() shl COLOUR_SHIFT),
                                        selected = value == chosen,
                                        onClick = { onSetColour(if (value == option.colour) null else value) },
                                    )
                                }
                        }
                        Text(
                            text = stringResource(R.string.indicator_settings_width),
                            style = MaterialTheme.typography.labelSmall,
                            color = CoineProColors.TextMuted,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                        ) {
                            INDICATOR_WIDTHS.forEach { width ->
                                CoineProToggleChip(
                                    // A stroke in dp is a figure on a control, so Latin.
                                    label = if (width == width.toInt().toFloat()) width.toInt().toString() else width.toString(),
                                    selected = (widthDp ?: DEFAULT_LINE_WIDTH) == width,
                                    onClick = { onSetWidth(if (width == DEFAULT_LINE_WIDTH) null else width) },
                                    compact = true,
                                    neutral = true,
                                )
                            }
                        }
                    }
                    IndicatorSettingsTab.VISIBILITY -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                        ) {
                            CoineProNotedLabel(
                                label = stringResource(R.string.indicator_settings_show),
                                note = R.string.indicator_settings_show_note,
                                modifier = Modifier.weight(1f),
                            )
                            CoineProSwitch(checked = !hidden, onCheckedChange = { onToggleHidden() })
                        }
                        arrangement?.let { where ->
                            HorizontalDivider(color = CoineProColors.Border)
                            // One line for the heading and its ⓘ, not three (DIALOGS-23).
                            CoineProNotedLabel(
                                label = stringResource(R.string.indicator_settings_arrangement),
                                note = R.string.indicator_settings_arrangement_note,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            if (where.overlayByDefault) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                                ) {
                                    Text(
                                        text = stringResource(R.string.indicator_settings_separate),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = CoineProColors.TextPrimary,
                                        modifier = Modifier.weight(1f),
                                    )
                                    CoineProSwitch(
                                        checked = where.separated,
                                        onCheckedChange = { on ->
                                            onArrange(if (on) IndicatorArrangement.Action.SEPARATE else IndicatorArrangement.Action.JOIN_PRICE)
                                        },
                                        modifier = Modifier.semantics { contentDescription = "indicator-separate" },
                                    )
                                }
                            }
                            if (where.hasPane) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                                    verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                                ) {
                                    ArrangementPill(
                                        text = stringResource(R.string.indicator_settings_move_up),
                                        enabled = where.canMoveUp,
                                        tag = "indicator-move-up",
                                        accent = accent,
                                    ) { onArrange(IndicatorArrangement.Action.MOVE_UP) }
                                    ArrangementPill(
                                        text = stringResource(R.string.indicator_settings_move_down),
                                        enabled = where.canMoveDown,
                                        tag = "indicator-move-down",
                                        accent = accent,
                                    ) { onArrange(IndicatorArrangement.Action.MOVE_DOWN) }
                                    if (where.merged) {
                                        ArrangementPill(
                                            text = stringResource(R.string.indicator_settings_unmerge),
                                            enabled = true,
                                            tag = "indicator-unmerge",
                                            accent = accent,
                                        ) { onArrange(IndicatorArrangement.Action.UNMERGE) }
                                    } else {
                                        ArrangementPill(
                                            text = stringResource(R.string.indicator_settings_merge_up),
                                            enabled = where.canMergeUp,
                                            tag = "indicator-merge-up",
                                            accent = accent,
                                        ) { onArrange(IndicatorArrangement.Action.MERGE_UP) }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = CoineProColors.Border)
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
}

/** How many decimals a step needs: 0.1 → one, 0.01 → two, 0.5 → one. */
private fun decimalsOf(step: Double): Int {
    var decimals = 0
    var scaled = step
    while (decimals < 4 && kotlin.math.abs(scaled - kotlin.math.round(scaled)) > 1e-9) {
        scaled *= 10.0
        decimals++
    }
    return decimals
}

/**
 * Where one study sits among the panes, as the settings sheet's «Pane» section reads it (run E).
 *
 * A pane is moved, merged into the one above it, or set apart with the sheet's own buttons rather
 * than by dragging its legend, because a drag on the plot already belongs to the pan and to the
 * drawings: a third claimant on the same finger would win some of the time, and a reorder that
 * happens by accident is worse than one that takes a tap more.
 */
data class IndicatorArrangement(
    /** The catalogue draws this study over the candles, so «a pane of its own» is a choice. */
    val overlayByDefault: Boolean,
    /** Set apart from the price pane by the reader. Meaningful only for an overlay. */
    val separated: Boolean,
    /** Drawn inside another study's pane. */
    val merged: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val canMergeUp: Boolean,
) {
    /** Whether the study occupies a pane at all — a pane study, or an overlay set apart. */
    val hasPane: Boolean get() = !overlayByDefault || separated

    enum class Action { SEPARATE, JOIN_PRICE, MOVE_UP, MOVE_DOWN, MERGE_UP, UNMERGE }
}

@Composable
private fun ArrangementPill(text: String, enabled: Boolean, tag: String, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(CoineProPillShape)
            .border(1.dp, if (enabled) CoineProTint.edge(accent) else CoineProColors.Border, CoineProPillShape)
            .coineProControl(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = tag }
            .heightIn(min = CoineProChipDefaults.CompactHeight)
            .padding(horizontal = CoineProSpacing.OneHalf),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) accent else CoineProColors.TextMuted,
        )
    }
}

/**
 * The strokes offered, in dp; the catalogue's own is one point two.
 *
 * [INDICATOR_WIDTHS] and [DEFAULT_LINE_WIDTH] are internal since 4.73.0 so the script indicator's
 * settings sheet offers the same four strokes rather than a second opinion about them. The swatch
 * geometry beside them stays private: three other files in this module already declare a `SWATCH`
 * of their own at their own size, and widening these would only make every one of them ambiguous.
 */
internal val INDICATOR_WIDTHS: List<Float> = listOf(1f, DEFAULT_LINE_WIDTH, 2f, 3f)
internal const val DEFAULT_LINE_WIDTH = 1.2f
private const val SWATCHES_ACROSS = 6
private const val COLOUR_SHIFT = 32
