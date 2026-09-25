package com.coinepro.feature.chart

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.BuiltInIndicatorTemplate
import com.coinepro.core.chart.BuiltInIndicatorTemplates
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProMenuItem
import com.coinepro.core.designsystem.CoineProSpacing

/**
 * TradingView's «Indicator templates» menu, its six ready-made sets (5.16.0).
 *
 * Each row is the template's name and, under it, what it puts on the chart — so a reader knows
 * before the tap that «Oscillators» is RSI, MACD and the stochastic, and that applying it replaces
 * what is on the chart now, which is what TradingView's does.
 */
@Composable
internal fun IndicatorTemplateSection(
    onApply: (BuiltInIndicatorTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SheetLabel(stringResource(R.string.chart_templates_heading))
        BuiltInIndicatorTemplates.ALL.forEach { template ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clickable { onApply(template) }
                    .semantics { contentDescription = "template-${template.id}" }
                    .padding(vertical = CoineProSpacing.Half),
            ) {
                Text(
                    text = stringResource(templateName(template.id)),
                    style = MaterialTheme.typography.labelLarge,
                    color = CoineProColors.TextPrimary,
                )
                Text(
                    text = templateSummary(template),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
    }
}

/**
 * The same six as a dropdown's rows (DIALOGS-22): a quiet header, a hairline, then each template as
 * its name over a faint summary — not the name and its studies fused into one bold string with a
 * «·» between them. For the toolbar's templates button; call inside a `DropdownMenu`.
 */
@Composable
internal fun ColumnScope.IndicatorTemplateMenuItems(onApply: (BuiltInIndicatorTemplate) -> Unit) {
    Text(
        text = stringResource(R.string.chart_templates_heading),
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        modifier = Modifier.padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
    )
    HorizontalDivider(color = CoineProColors.BorderSubtle)
    BuiltInIndicatorTemplates.ALL.forEach { template ->
        CoineProMenuItem(
            text = stringResource(templateName(template.id)),
            supporting = templateSummary(template),
            onClick = { onApply(template) },
            modifier = Modifier.semantics { contentDescription = "toolbar-template-${template.id}" },
        )
    }
}

@StringRes
internal fun templateName(id: String): Int = when (id) {
    "tv_bw3lines" -> R.string.chart_template_bw3
    "tv_displaced_ema" -> R.string.chart_template_displaced_ema
    "tv_ma_exp_ribbon" -> R.string.chart_template_ribbon
    "tv_oscillators" -> R.string.chart_template_oscillators
    "tv_swing" -> R.string.chart_template_swing
    else -> R.string.chart_template_volume
}

/**
 * What a template puts on the chart, in the abbreviations every terminal prints in its legend —
 * `EMA 20 +5 · RSI` — Latin in both languages, because these are the studies' own names.
 */
internal fun templateSummary(template: BuiltInIndicatorTemplate): String =
    template.indicators.joinToString(" · ") { id ->
        val name = TEMPLATE_ABBREVIATIONS[id] ?: id.uppercase()
        val period = template.periods[id]?.let { " $it" }.orEmpty()
        val shift = template.params[id]?.get("shift")?.toInt()?.takeIf { it > 0 }?.let { " +$it" }.orEmpty()
        name + period + shift
    }

private val TEMPLATE_ABBREVIATIONS = mapOf(
    "alligator" to "Alligator",
    "ema" to "EMA",
    "sma" to "SMA",
    "maribbon" to "MA Ribbon",
    "rsi" to "RSI",
    "macd" to "MACD",
    "stochastic" to "Stoch",
    "vwap" to "VWAP",
    "obv" to "OBV",
    "mfi" to "MFI",
    "cmf" to "CMF",
)
