package com.coinepro.feature.heatmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing

/**
 * One market's figures, without leaving the map.
 *
 * ### Why a long press and not a second screen
 *
 * A heatmap is read by comparison. The reader has just found the one tile that disagrees with its
 * neighbours, and the question is always "what exactly is this one doing" — asked about the tile,
 * in the context of the forty around it. Navigating away answers it and destroys the context at the
 * same time, and coming back re-lays the map out and loses the tile.
 *
 * So the tap goes to the chart, which is the reader committing, and the press-and-hold opens this,
 * which is the reader checking. Every figure the map could have coloured by is here at once — that
 * is the second thing this sheet is for: a reader who cannot see why a tile is dark can read the
 * gap, the range position and the volatility excess in one place instead of switching the whole map
 * through five colour modes to interrogate one square.
 *
 * Every absent figure prints an em dash rather than a zero, and a market whose candles have not been
 * read says so in a sentence at the top. A sheet full of dashes with no explanation reads as a
 * broken sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapDetailSheet(
    asset: HeatmapAsset,
    period: HeatmapPeriod,
    onOpenChart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val periodLabel = when (period) {
        HeatmapPeriod.WEEK -> stringResource(R.string.heatmap_period_week)
        HeatmapPeriod.MONTH -> stringResource(R.string.heatmap_period_month)
        HeatmapPeriod.QUARTER -> stringResource(R.string.heatmap_period_quarter)
    }
    CoineProSheet(
        // The pair as a terminal writes it, not the feed's run-together spelling: this is the one
        // place on the screen with room for `BTC/USDT`, and the tile above it only had room for
        // `BTC`.
        title = asset.meta.pretty,
        subtitle = asset.meta.description,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = CoineProSpacing.Gutter,
                    end = CoineProSpacing.Gutter,
                    bottom = CoineProSpacing.Three,
                ),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            if (!asset.resolved) {
                Text(
                    text = stringResource(R.string.heatmap_detail_unresolved),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                    modifier = Modifier.padding(bottom = CoineProSpacing.Half),
                )
            }

            Figure(stringResource(R.string.heatmap_detail_price), HeatmapFormat.price(asset.price))
            Figure(
                label = stringResource(R.string.heatmap_detail_change),
                value = HeatmapFormat.percent(asset.changePercent),
                ink = directionInk(asset.changePercent),
            )
            Figure(
                label = stringResource(R.string.heatmap_detail_period, periodLabel),
                value = HeatmapFormat.percent(asset.periodPercent),
                ink = directionInk(asset.periodPercent),
            )
            Figure(
                label = stringResource(R.string.heatmap_detail_gap),
                value = HeatmapFormat.percent(HeatmapMetrics.valueOf(asset, HeatmapColour.GAP)),
                ink = directionInk(HeatmapMetrics.valueOf(asset, HeatmapColour.GAP)),
            )
            Figure(stringResource(R.string.heatmap_detail_open), HeatmapFormat.price(asset.openPrice))
            Figure(
                label = stringResource(R.string.heatmap_detail_previous_close),
                value = HeatmapFormat.price(asset.previousClose),
            )
            Figure(stringResource(R.string.heatmap_detail_high), HeatmapFormat.price(asset.dayHigh))
            Figure(stringResource(R.string.heatmap_detail_low), HeatmapFormat.price(asset.dayLow))
            Figure(
                label = stringResource(R.string.heatmap_detail_range),
                value = HeatmapFormat.tileFigure(
                    HeatmapMetrics.valueOf(asset, HeatmapColour.RANGE),
                    HeatmapColour.RANGE,
                ),
            )
            Figure(
                label = stringResource(R.string.heatmap_detail_volatility),
                value = HeatmapFormat.percent(asset.volatilityPercent),
            )
            Figure(
                label = stringResource(R.string.heatmap_detail_typical),
                value = HeatmapFormat.percent(asset.typicalVolatilityPercent),
            )
            Figure(stringResource(R.string.heatmap_detail_volume), HeatmapFormat.amount(asset.volume))
            Figure(
                label = stringResource(R.string.heatmap_detail_turnover),
                value = HeatmapFormat.amount(asset.turnover),
            )
            // Only where the market has one. A funding row reading «—» on every currency pair and
            // every spot coin is a row that teaches the reader to stop reading the sheet.
            asset.fundingRatePercent?.let { rate ->
                Figure(
                    label = stringResource(R.string.heatmap_detail_funding),
                    value = HeatmapFormat.percent(rate),
                    ink = directionInk(rate),
                )
            }

            CoineProPrimaryButton(
                text = stringResource(R.string.heatmap_detail_open_chart),
                onClick = onOpenChart,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = CoineProSpacing.OneHalf),
            )
        }
    }
}

/**
 * One label and one figure, on a line.
 *
 * Every figure arrives from [HeatmapFormat] already isolated as a left-to-right run, which is what
 * keeps a leading minus attached to its number: without the isolate a right-to-left paragraph
 * reorders `−1.24%` to `1.24%−`, and a reader scanning a column of them reads a loss as a gain.
 * `TextAlign.Right` rather than `End`, so the numbers line up on the same edge in both locales and
 * a column of them can be compared down its length.
 */
@Composable
private fun Figure(label: String, value: String, ink: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = ink ?: CoineProColors.TextPrimary,
            textAlign = TextAlign.Right,
        )
    }
}

/**
 * The market's own up and down colour, for the three figures that carry a direction.
 *
 * Only those three. Colouring a price or a volume by its sign would be colour with no meaning, and
 * a sheet where everything is green teaches the reader to stop seeing the green that matters. An
 * absent figure takes the ordinary ink: a dash is not a loss.
 */
@Composable
private fun directionInk(value: Double?): Color? = when {
    value == null || !value.isFinite() || value == 0.0 -> null
    value > 0.0 -> CoineProColors.Buy
    else -> CoineProColors.Sell
}
