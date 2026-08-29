package com.coinepro.feature.heatmap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSegmentTabs
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing

/**
 * The four choices that decide what the map means, and the scale it is drawn on.
 *
 * In a sheet rather than on the screen because they are settings, not filters: a reader picks a
 * sizing and a palette once and then spends every later visit looking at the map. Four rows of
 * controls permanently above the canvas would cost a third of the map's height to save a tap
 * nobody takes twice.
 *
 * The scale caption is here unconditionally. The legend on the screen is dropped on a short phone,
 * so this is the one place the reader can always find out what a fully saturated tile means.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapSettingsSheet(
    options: HeatmapOptions,
    scale: Double,
    onOptions: (HeatmapOptions) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CoineProSheet(
        title = stringResource(R.string.heatmap_settings),
        subtitle = stringResource(R.string.heatmap_scale, MarketNumberFormatter.price(scale, decimals = 1)),
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = CoineProSpacing.Three),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        ) {
            Section(stringResource(R.string.heatmap_size)) {
                CoineProSegmentTabs(
                    options = listOf(
                        HeatmapSize.MARKET_CAP to stringResource(R.string.heatmap_size_market_cap),
                        HeatmapSize.VOLUME to stringResource(R.string.heatmap_size_volume),
                        HeatmapSize.TURNOVER to stringResource(R.string.heatmap_size_turnover),
                        HeatmapSize.MONO to stringResource(R.string.heatmap_size_mono),
                    ),
                    selected = options.size,
                    onSelect = { onOptions(options.copy(size = it)) },
                )
            }
            Section(stringResource(R.string.heatmap_colour)) {
                CoineProSegmentTabs(
                    options = listOf(
                        HeatmapColour.CHANGE to stringResource(R.string.heatmap_colour_change),
                        HeatmapColour.PERFORMANCE to stringResource(R.string.heatmap_colour_performance),
                        HeatmapColour.VOLATILITY to stringResource(R.string.heatmap_colour_volatility),
                        HeatmapColour.RANGE to stringResource(R.string.heatmap_colour_range),
                        HeatmapColour.GAP to stringResource(R.string.heatmap_colour_gap),
                    ),
                    selected = options.colour,
                    onSelect = { onOptions(options.copy(colour = it)) },
                )
            }
            Section(stringResource(R.string.heatmap_palette)) {
                CoineProSegmentTabs(
                    options = listOf(
                        HeatmapPalette.CLASSIC to stringResource(R.string.heatmap_palette_classic),
                        HeatmapPalette.COLOUR_BLIND to stringResource(R.string.heatmap_palette_colour_blind),
                        HeatmapPalette.MONOCHROME to stringResource(R.string.heatmap_palette_monochrome),
                    ),
                    selected = options.palette,
                    onSelect = { onOptions(options.copy(palette = it)) },
                )
                // Said in words as well as shown, because a reader who needs this palette is
                // exactly the reader who cannot confirm from the swatches that it helps.
                if (options.palette == HeatmapPalette.COLOUR_BLIND) {
                    Text(
                        text = stringResource(R.string.heatmap_palette_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                        modifier = Modifier.padding(horizontal = CoineProSpacing.Two, vertical = 4.dp),
                    )
                }
            }
            Section(stringResource(R.string.heatmap_grouping)) {
                CoineProSegmentTabs(
                    options = listOf(
                        HeatmapGrouping.NONE to stringResource(R.string.heatmap_grouping_none),
                        HeatmapGrouping.BY_CLASS to stringResource(R.string.heatmap_grouping_class),
                    ),
                    selected = options.grouping,
                    onSelect = { onOptions(options.copy(grouping = it)) },
                )
            }
        }
    }
}

/** A label over one strip of choices. The label carries the question; the strip carries the answer. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            modifier = Modifier.padding(horizontal = CoineProSpacing.Two),
        )
        content()
    }
}
