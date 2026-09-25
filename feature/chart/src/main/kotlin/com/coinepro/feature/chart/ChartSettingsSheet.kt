package com.coinepro.feature.chart

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.ChartAppearance
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.pageAccentInk

/**
 * TradingView's «Chart settings», tab for tab (5.16.0).
 *
 * The dialog has six tabs and this has the same six, in the same order and under the same names,
 * because a reader who knows where TradingView keeps «colour bars on previous close» looks under
 * Symbol for it and nowhere else. What each tab holds:
 *
 * * **Symbol** — how the bars are drawn: bodies, wicks, colouring against the previous close, the
 *   volume columns.
 * * **Status line** — which parts of the legend show.
 * * **Scales** — the price-line switches, then the scale settings this chart already had (mode,
 *   inversion, lock, side, zone, precision), unchanged.
 * * **Canvas** — the grid's two directions, the watermark, the margins, and the colour templates.
 * * **Trading** — the chrome that trades from the chart.
 * * **Events** — the kinds of event drawn on the time axis, the section the events sheet has.
 *
 * The existing sections are handed in rather than rebuilt, so the scale sheet and this tab can never
 * disagree about what a switch does.
 */
enum class ChartSettingsTab(@StringRes val labelRes: Int) {
    SYMBOL(R.string.chart_settings_tab_symbol),
    STATUS_LINE(R.string.chart_settings_tab_status),
    SCALES(R.string.chart_settings_tab_scales),
    CANVAS(R.string.chart_settings_tab_canvas),
    TRADING(R.string.chart_settings_tab_trading),
    EVENTS(R.string.chart_settings_tab_events),
}

@Composable
fun ChartSettingsBody(
    tab: ChartSettingsTab,
    onTab: (ChartSettingsTab) -> Unit,
    appearance: ChartAppearance,
    onChange: (ChartAppearance) -> Unit,
    /** The scale settings this chart already had. */
    scales: @Composable () -> Unit,
    /** The colour templates, where there is a store to keep them in. */
    colours: (@Composable () -> Unit)? = null,
    /** The event kinds, where the build draws events. */
    events: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .testTag(CHART_SETTINGS_TAG),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        CoineProChipRow(
            options = ChartSettingsTab.entries.map { CoineProChip(id = it.name, label = stringResource(it.labelRes)) },
            selectedId = tab.name,
            onSelect = { id -> ChartSettingsTab.entries.firstOrNull { it.name == id }?.let(onTab) },
            compact = true,
        )
        HorizontalDivider(color = CoineProColors.Border)
        when (tab) {
            ChartSettingsTab.SYMBOL -> {
                Toggle(R.string.chart_settings_prev_close, appearance.colourOnPreviousClose) {
                    onChange(appearance.copy(colourOnPreviousClose = it))
                }
                Toggle(R.string.chart_settings_bodies, appearance.bodies) { onChange(appearance.copy(bodies = it)) }
                Toggle(R.string.chart_settings_wicks, appearance.wicks) { onChange(appearance.copy(wicks = it)) }
                Toggle(R.string.chart_settings_volume, appearance.volume) { onChange(appearance.copy(volume = it)) }
            }
            ChartSettingsTab.STATUS_LINE -> {
                Toggle(R.string.chart_settings_logo, appearance.legendLogo) { onChange(appearance.copy(legendLogo = it)) }
                Toggle(R.string.chart_settings_ohlc, appearance.legendOhlc) { onChange(appearance.copy(legendOhlc = it)) }
                Toggle(R.string.chart_settings_change, appearance.legendChange) { onChange(appearance.copy(legendChange = it)) }
                Toggle(R.string.chart_settings_indicators, appearance.legendIndicators) {
                    onChange(appearance.copy(legendIndicators = it))
                }
            }
            ChartSettingsTab.SCALES -> {
                Toggle(R.string.chart_settings_last_price, appearance.lastPriceLine) {
                    onChange(appearance.copy(lastPriceLine = it))
                }
                Toggle(R.string.chart_settings_countdown, appearance.countdown) { onChange(appearance.copy(countdown = it)) }
                Toggle(R.string.chart_settings_prev_line, appearance.previousCloseLine) {
                    onChange(appearance.copy(previousCloseLine = it))
                }
                Toggle(R.string.chart_settings_scale_unit, appearance.scaleUnit) {
                    onChange(appearance.copy(scaleUnit = it))
                }
                HorizontalDivider(color = CoineProColors.Border)
                scales()
            }
            ChartSettingsTab.CANVAS -> {
                Toggle(R.string.chart_settings_grid_vertical, appearance.gridVertical) {
                    onChange(appearance.copy(gridVertical = it))
                }
                Toggle(R.string.chart_settings_grid_horizontal, appearance.gridHorizontal) {
                    onChange(appearance.copy(gridHorizontal = it))
                }
                Toggle(R.string.chart_settings_watermark, appearance.watermark) { onChange(appearance.copy(watermark = it)) }
                Toggle(R.string.chart_settings_crosshair_magnet, appearance.crosshairMagnet) {
                    onChange(appearance.copy(crosshairMagnet = it))
                }
                HorizontalDivider(color = CoineProColors.Border)
                MarginRow(R.string.chart_settings_margin_top, appearance.topMarginPercent, TOP_MARGINS) {
                    onChange(appearance.copy(topMarginPercent = it))
                }
                MarginRow(R.string.chart_settings_margin_bottom, appearance.bottomMarginPercent, BOTTOM_MARGINS) {
                    onChange(appearance.copy(bottomMarginPercent = it))
                }
                colours?.let {
                    HorizontalDivider(color = CoineProColors.Border)
                    it()
                }
            }
            ChartSettingsTab.TRADING -> {
                Toggle(R.string.chart_settings_trade_ring, appearance.tradeRing) { onChange(appearance.copy(tradeRing = it)) }
                Toggle(R.string.chart_settings_quote_chip, appearance.quoteChip) { onChange(appearance.copy(quoteChip = it)) }
            }
            ChartSettingsTab.EVENTS -> events?.invoke() ?: Text(
                text = stringResource(R.string.chart_settings_no_events),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        HorizontalDivider(color = CoineProColors.Border)
        // TradingView's footer «Reset settings», for this dialog's own switches. The scale and
        // colour sections keep their own state and are not touched by it.
        Text(
            text = stringResource(R.string.chart_settings_reset),
            style = MaterialTheme.typography.labelLarge,
            color = if (appearance == ChartAppearance()) CoineProColors.TextDisabled else CoineProColors.pageAccentInk,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clickable(enabled = appearance != ChartAppearance()) { onChange(ChartAppearance()) }
                .padding(vertical = CoineProSpacing.One),
        )
    }
}

/** A labelled switch, the dialog's one control. */
@Composable
private fun Toggle(@StringRes labelRes: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            modifier = Modifier.widthIn(min = 52.dp),
            colors = SwitchDefaults.colors(
                checkedThumbColor = CoineProColors.OnAccent,
                checkedTrackColor = CoineProColors.AccentFill,
                uncheckedThumbColor = CoineProColors.TextMuted,
                uncheckedTrackColor = CoineProColors.SurfaceElevated,
            ),
        )
    }
}

/**
 * A margin as TradingView's Canvas tab sets it, in percent of the plot. Latin figures: a percentage
 * on a chart control is a figure, not a prose count.
 */
@Composable
private fun MarginRow(@StringRes labelRes: Int, value: Int, choices: List<Int>, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SheetLabel(stringResource(labelRes))
        CoineProChipRow(
            options = (choices + value).distinct().sorted().map { CoineProChip(id = it.toString(), label = "$it%") },
            selectedId = value.toString(),
            onSelect = { id -> id?.toIntOrNull()?.let(onSelect) },
            compact = true,
        )
    }
}

private val TOP_MARGINS = listOf(0, 5, 10, 15, 20, 30)
private val BOTTOM_MARGINS = listOf(0, 4, 8, 12, 16, 20)

internal const val CHART_SETTINGS_TAG = "chart-settings"
