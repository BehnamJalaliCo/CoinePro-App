package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.ConfidenceReport
import com.coinepro.core.chart.MarketState
import com.coinepro.core.chart.SetupScore
import com.coinepro.core.chart.SignalRead
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.inEnglish
import com.coinepro.core.designsystem.numeric

/**
 * The **Now strip**: what every study on this chart is saying, in one row (run Ω1).
 *
 * ### Why this row exists and where it is
 *
 * It is the product. A chart with an RSI on it draws a line; this says «RSI · oversold · 61 % of 18»
 * and it is the only row in the app a beginner can act on without knowing what an RSI is. The
 * prompt puts it «under the legend», and the legend is drawn *inside* the plot by the chart engine —
 * so the honest placement is immediately under the plot, at the head of the reading, where it is one
 * glance from the candles and costs the plot nothing but its own twenty-six points.
 *
 * ### The colour rule, which is the whole of the design
 *
 * **Direction is green or red; confidence is grey to gold.** A percentage drawn in green would be
 * read as «up» on a chart where green means up, and a reader would take a high win rate on a bearish
 * study as a buy. So the dot is the market's colour and the figure is the brand's, and the two never
 * swap. See `docs/design/REPORT.md`, run Ω.
 */
@Composable
internal fun ChartNowStrip(
    layer: ChartSignalLayer,
    onOpenExplain: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (layer.isEmpty) return
    val english = inEnglish()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Half)
            .semantics { contentDescription = "now-strip" },
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The chart's own number first, because it is the answer to the question the reader is
        // actually asking — «is anything happening» — and the pills behind it are the working.
        SetupChip(score = layer.setup, english = english, onClick = { onOpenExplain(null) })
        for (read in layer.reads) {
            StudyPill(
                read = read,
                report = layer.confidenceOf(read.id),
                layerName = layer.nameOf(read.id),
                english = english,
                onClick = { onOpenExplain(read.id) },
            )
        }
    }
}

/**
 * The Setup score: 0–100, on a grey→gold scale, with the studies it came from.
 *
 * Never green and never red, and that is not a style preference: this is a measure of *agreement*,
 * and the two colours on this screen that already mean something mean «up» and «down». A gold 80
 * says «this chart is saying one thing loudly»; the direction is the word beside it.
 */
@Composable
private fun SetupChip(score: SetupScore, english: Boolean, onClick: () -> Unit) {
    val tone = lerp(CoineProColors.TextMuted, CoineProColors.Gold, score.tone)
    Row(
        modifier = Modifier
            .clip(CoineProPillShape)
            .background(CoineProColors.SurfaceRaised)
            .border(1.dp, tone.copy(alpha = CHIP_EDGE_ALPHA), CoineProPillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half)
            .semantics { contentDescription = "setup-score" },
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.chart_setup_score),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        Text(
            // Latin, like every figure on this screen. A score is a market figure, not a count in
            // a sentence — see the app's numeral rule.
            text = score.score.toString(),
            style = MaterialTheme.typography.labelLarge.numeric(),
            color = tone,
        )
        if (!score.isEmpty) {
            Text(
                text = score.side.label(english),
                style = MaterialTheme.typography.labelSmall,
                color = stateColour(score.side),
            )
        }
    }
}

/**
 * One study: its state as a dot, its name, and how often it has been right here.
 *
 * The sample size is printed beside the percentage and is not optional. «61 %» on its own is the
 * single most misleading thing this app could draw, and «61 % of 18» is a fact a reader can weigh.
 * Under [ConfidenceReport.THIN] samples there is no percentage at all — the pill says «low data»,
 * which is the truth and is more useful than a number built out of five outcomes.
 */
@Composable
private fun StudyPill(
    read: SignalRead,
    report: ConfidenceReport?,
    layerName: String,
    english: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CoineProPillShape)
            .background(CoineProColors.SurfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half)
            .semantics { contentDescription = "signal-pill-${read.id}" },
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(DOT)
                .clip(CircleShape)
                .background(stateColour(read.state)),
        )
        Text(
            text = layerName,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextPrimary,
        )
        when {
            report == null || report.samples == 0 -> Unit
            !report.trustworthy -> Text(
                text = stringResource(R.string.chart_confidence_thin),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
            else -> Text(
                text = stringResource(R.string.chart_confidence_short, report.percent.toString(), report.samples.toString()),
                style = MaterialTheme.typography.labelSmall.numeric(),
                color = lerp(CoineProColors.TextMuted, CoineProColors.Gold, report.winRate.toFloat()),
            )
        }
    }
}

/**
 * A state's colour: the market's own, and only here.
 *
 * Neutral is muted rather than a third hue. A chart that painted «no opinion» in its own colour
 * would have three colours competing on a surface where two of them already mean something.
 */
@Composable
internal fun stateColour(state: MarketState): Color = when (state) {
    MarketState.BULL -> CoineProColors.Buy
    MarketState.BEAR -> CoineProColors.Sell
    MarketState.NEUTRAL -> CoineProColors.TextMuted
}

/** The state dot: six points, the same size as a signal marker on the plot. */
private val DOT = 6.dp

/** How much of the score's own tone its edge keeps: enough to read as a rim, not as a border. */
private const val CHIP_EDGE_ALPHA = 0.6f
