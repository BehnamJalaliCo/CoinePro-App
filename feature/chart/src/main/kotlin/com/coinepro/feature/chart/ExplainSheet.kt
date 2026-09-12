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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ConfidenceEngine
import com.coinepro.core.chart.ConfidenceReport
import com.coinepro.core.chart.MarketState
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.inEnglish
import com.coinepro.core.designsystem.onPageAccent
import com.coinepro.core.designsystem.pageAccentInk
import com.coinepro.core.designsystem.numeric

/**
 * **What this study is, what it is saying, and how often it has been right** (run Ω1).
 *
 * ### Why a whole sheet
 *
 * Because the Now strip is a headline and a headline that cannot be opened is a claim. A reader who
 * sees «RSI · 61 % of 18» and cannot find out what that 61 means has been given a number to trust
 * on the app's word, which is exactly what this product is supposed to replace.
 *
 * So the sheet answers, in order, the four questions a person actually has: what is this thing; what
 * is it saying right now; how has it done here before; and what can I do about it. Nothing else. It
 * is not a settings sheet — that already exists and the gear still opens it — and it is not a
 * tutorial.
 *
 * ### The honesty rules, all three visible at once
 *
 * * The **sample size** sits beside every percentage, and under [ConfidenceReport.THIN] there is no
 *   percentage at all.
 * * The **horizon** is named — «ten bars later» — because a win rate without one is a win rate about
 *   nothing.
 * * The **last five outcomes** are drawn as dots, so a run of four losses under a 60 % is visible
 *   rather than averaged away.
 *
 * Public rather than internal, and for one reason: on a tablet this is a **side panel** beside the
 * plot rather than a sheet over it (E10), and the panel is composed by the shell — which is outside
 * this module. One body, two frames, no second copy to keep in step.
 */
@Composable
fun ExplainSheetBody(
    /** The study this is about, or null for the chart as a whole. */
    id: String?,
    layer: ChartSignalLayer,
    onSetHorizon: (Int) -> Unit,
    onAddAlert: (() -> Unit)?,
    onPractise: (() -> Unit)?,
    onSelect: (String) -> Unit,
    /** Ask for this study on the other bar lengths. Null where nothing can fetch them. */
    onShowTimeframes: ((String) -> Unit)? = null,
) {
    val english = inEnglish()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CoineProSpacing.Gutter)
            .padding(bottom = CoineProSpacing.Two)
            .semantics { contentDescription = "explain-sheet" },
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        if (id == null) {
            ChartExplanation(layer = layer, english = english, onSelect = onSelect)
            return@Column
        }
        val read = layer.readOf(id)
        if (read == null) {
            Text(
                text = stringResource(R.string.chart_explain_missing),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
            return@Column
        }
        val report = layer.confidenceOf(id)

        // 1. What it says now — the sentence, at the top, in the largest type on the sheet. It is
        // the reason the reader opened this.
        Row(
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(STATE_DOT)
                    .clip(CircleShape)
                    .background(stateColour(read.state)),
            )
            Text(
                text = read.state.label(english),
                style = MaterialTheme.typography.titleSmall,
                color = stateColour(read.state),
            )
        }
        layer.sentence(id, english)?.let { sentence ->
            Text(
                text = sentence,
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextPrimary,
            )
        }

        // 2. What it is. One line, from the catalogue the indicator sheet already prints.
        ChartCatalog.INDICATORS.firstOrNull { it.id == id }?.let { option ->
            Text(
                text = option.label,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
            )
        }

        HorizontalDivider(color = CoineProColors.BorderSubtle)

        // 3. How it has done here.
        ConfidenceCard(report = report, english = english, onSetHorizon = onSetHorizon)

        // 4. What to do about it. Both are offered only where the screen wired them; a row that
        // opens nothing is worse than an absent one.
        read.stop?.let { stop ->
            Text(
                text = stringResource(R.string.chart_explain_stop, MarketNumberFormatter.priceAuto(stop)),
                style = MaterialTheme.typography.bodySmall.numeric(),
                color = CoineProColors.TextSecondary,
            )
        }
        onAddAlert?.let {
            CoineProSecondaryButton(
                text = stringResource(R.string.chart_explain_alert),
                onClick = it,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        onPractise?.let {
            CoineProSecondaryButton(
                text = stringResource(R.string.chart_explain_practise),
                onClick = it,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // **The same study, on the bigger picture.**
        //
        // Asked for rather than shown: each row is a request for bars this chart does not hold, and
        // a sheet that fetched three timeframes every time it opened would be three requests for a
        // question most readers are not asking. See `ChartController.readAcrossTimeframes`.
        onShowTimeframes?.let { ask ->
            val rows = layer.across[id]
            if (rows == null) {
                CoineProSecondaryButton(
                    text = stringResource(R.string.chart_explain_timeframes),
                    onClick = { ask(id) },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (rows.isEmpty()) {
                Text(
                    text = stringResource(R.string.chart_explain_timeframes_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            } else {
                for (row in rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(OUTCOME_DOT)
                                .clip(CircleShape)
                                .background(stateColour(row.state)),
                        )
                        Text(
                            text = row.timeframe,
                            style = MaterialTheme.typography.labelMedium.numeric(),
                            color = CoineProColors.TextPrimary,
                        )
                        Text(
                            text = row.sentence,
                            style = MaterialTheme.typography.bodySmall,
                            color = CoineProColors.TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // 5. The one line that is not negotiable, said once, at the foot.
        Text(
            text = stringResource(R.string.chart_explain_not_advice),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
    }
}

/**
 * The chart as a whole: the score, and every study that voted for it.
 *
 * Tapping a row moves the sheet onto that study, which is the reason the score is worth tapping at
 * all — «72» is a summary and a summary the reader cannot take apart is a black box.
 */
@Composable
private fun ChartExplanation(layer: ChartSignalLayer, english: Boolean, onSelect: (String) -> Unit) {
    val tone = lerp(CoineProColors.TextMuted, CoineProColors.Gold, layer.setup.tone)
    Row(
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = layer.setup.score.toString(),
            style = MaterialTheme.typography.headlineSmall.numeric(),
            color = tone,
        )
        Column {
            Text(
                text = layer.setup.side.label(english),
                style = MaterialTheme.typography.labelLarge,
                color = stateColour(layer.setup.side),
            )
            Text(
                text = stringResource(
                    R.string.chart_setup_from,
                    layer.setup.studies.toString(),
                    layer.setup.signals.toString(),
                ),
                style = MaterialTheme.typography.labelSmall.numeric(),
                color = CoineProColors.TextMuted,
            )
        }
    }
    HorizontalDivider(color = CoineProColors.BorderSubtle)
    for (read in layer.reads) {
        val report = layer.confidenceOf(read.id)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(read.id) }
                .padding(vertical = CoineProSpacing.Half),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(STATE_DOT)
                    .clip(CircleShape)
                    .background(stateColour(read.state)),
            )
            Text(
                text = layer.sentence(read.id, english) ?: read.id,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (report != null && report.trustworthy) {
                Text(
                    text = stringResource(R.string.chart_confidence_short, report.percent.toString(), report.samples.toString()),
                    style = MaterialTheme.typography.labelSmall.numeric(),
                    color = lerp(CoineProColors.TextMuted, CoineProColors.Gold, report.winRate.toFloat()),
                )
            }
        }
    }
}

/**
 * The base rate, with everything a reader needs to discount it.
 *
 * Win rate, average R, sample size, the horizon it was measured over, and the last five outcomes as
 * dots. The dots are the part that stops a number being believed too easily: a 60 % whose last four
 * were losses looks different from a 60 % that alternates, and no single figure can show that.
 */
@Composable
private fun ConfidenceCard(report: ConfidenceReport?, english: Boolean, onSetHorizon: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
        Text(
            text = stringResource(R.string.chart_confidence_title),
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextPrimary,
        )
        if (report == null || report.samples == 0) {
            Text(
                text = stringResource(R.string.chart_confidence_none),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        } else {
            Text(
                text = if (report.trustworthy) {
                    stringResource(
                        R.string.chart_confidence_long,
                        report.percent.toString(),
                        report.samples.toString(),
                        report.horizon.toString(),
                    )
                } else {
                    stringResource(R.string.chart_confidence_thin_long, report.samples.toString())
                },
                style = MaterialTheme.typography.bodySmall.numeric(),
                color = CoineProColors.TextSecondary,
            )
            Text(
                // Two decimals, Latin, with its sign: «+0.42 R» is a market figure like any other.
                text = stringResource(R.string.chart_confidence_r, formatR(report.averageR)),
                style = MaterialTheme.typography.bodySmall.numeric(),
                color = CoineProColors.TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                for (win in report.recent) {
                    Box(
                        modifier = Modifier
                            .size(OUTCOME_DOT)
                            .clip(CircleShape)
                            .background(if (win) CoineProColors.Buy else CoineProColors.Sell),
                    )
                }
            }
        }
        // The horizon, as three chips. It is a question about the reader rather than about the
        // market — «did it work» is five bars away for one person and twenty for another.
        Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            for (bars in ConfidenceEngine.HORIZONS) {
                val selected = report?.horizon == bars
                Text(
                    text = stringResource(R.string.chart_confidence_bars, bars.toString()),
                    style = MaterialTheme.typography.labelSmall.numeric(),
                    color = if (selected) CoineProColors.onPageAccent else CoineProColors.TextSecondary,
                    modifier = Modifier
                        .clip(CoineProPillShape)
                        .background(if (selected) CoineProColors.pageAccentInk else CoineProColors.SurfaceElevated)
                        .clickable { onSetHorizon(bars) }
                        .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half)
                        .semantics { contentDescription = "horizon-$bars" },
                )
            }
        }
    }
}

/**
 * Average R, as a signed figure.
 *
 * Written here rather than taken from [MarketNumberFormatter] because R is not a price and not a
 * percentage: it is a ratio, two decimals, and its sign is the whole of what a reader takes from it.
 */
private fun formatR(value: Double): String {
    val rounded = kotlin.math.round(value * 100) / 100
    val sign = if (rounded > 0) "+" else ""
    return sign + MarketNumberFormatter.price(rounded, 2)
}

private val STATE_DOT = 8.dp
private val OUTCOME_DOT = 6.dp
