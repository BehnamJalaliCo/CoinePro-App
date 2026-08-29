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
 * The six choices that decide what the map means, and the scale it is drawn on.
 *
 * In a sheet rather than on the screen because they are settings, not filters: a reader picks a
 * sizing and a palette once and then spends every later visit looking at the map. Six rows of
 * controls permanently above the canvas would cost half the map's height to save a tap nobody
 * takes twice.
 *
 * ### Why every strip here can say "no data"
 *
 * The version this replaced offered nine choices of which eight changed nothing: the app had no
 * capitalisation, no volume, no change and no range for any market, so eight of the nine radio
 * buttons drew the identical map. A control that does nothing is worse than a missing one, because
 * the reader concludes the *map* is broken rather than the option.
 *
 * So each strip is passed the assets it would act on and asks whether anything can answer it. A
 * mode nothing can answer keeps its place — it will work the moment the candles land — but says so
 * underneath, in a sentence naming the reason rather than a disabled grey. The one mode that could
 * never work on either backend, capitalisation, is not here at all: see [HeatmapSize].
 *
 * The scale caption is in the header unconditionally. The legend on the screen is dropped on a
 * short phone, so this is the one place the reader can always find out what a fully saturated tile
 * means.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeatmapSettingsSheet(
    options: HeatmapOptions,
    scale: Double,
    assets: List<HeatmapAsset>,
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
                if (!HeatmapMetrics.anyValueFor(assets, options.colour)) {
                    Note(stringResource(R.string.heatmap_colour_unavailable))
                }
            }
            // Only under the mode it belongs to. A window control sitting permanently under a map
            // coloured by the day's move is a control whose effect a reader cannot find.
            if (options.colour == HeatmapColour.PERFORMANCE) {
                Section(stringResource(R.string.heatmap_period)) {
                    CoineProSegmentTabs(
                        options = listOf(
                            HeatmapPeriod.WEEK to stringResource(R.string.heatmap_period_week),
                            HeatmapPeriod.MONTH to stringResource(R.string.heatmap_period_month),
                            HeatmapPeriod.QUARTER to stringResource(R.string.heatmap_period_quarter),
                        ),
                        selected = options.period,
                        onSelect = { onOptions(options.copy(period = it)) },
                    )
                }
            }
            Section(stringResource(R.string.heatmap_size)) {
                CoineProSegmentTabs(
                    options = listOf(
                        HeatmapSize.LIQUIDITY to stringResource(R.string.heatmap_size_liquidity),
                        HeatmapSize.VOLUME to stringResource(R.string.heatmap_size_volume),
                        HeatmapSize.TURNOVER to stringResource(R.string.heatmap_size_turnover),
                        HeatmapSize.MONO to stringResource(R.string.heatmap_size_mono),
                    ),
                    selected = options.size,
                    onSelect = { onOptions(options.copy(size = it)) },
                )
                when {
                    !HeatmapMetrics.anyWeightFor(assets, options.size) ->
                        Note(stringResource(R.string.heatmap_size_volume_note))

                    options.size == HeatmapSize.LIQUIDITY ->
                        Note(stringResource(R.string.heatmap_size_note))
                }
            }
            Section(stringResource(R.string.heatmap_density)) {
                CoineProSegmentTabs(
                    options = listOf(
                        HeatmapDensity.FOCUSED to stringResource(R.string.heatmap_density_focused),
                        HeatmapDensity.STANDARD to stringResource(R.string.heatmap_density_standard),
                        HeatmapDensity.EVERYTHING to stringResource(R.string.heatmap_density_everything),
                    ),
                    selected = options.density,
                    onSelect = { onOptions(options.copy(density = it)) },
                )
                Note(stringResource(R.string.heatmap_density_note))
            }
            Section(stringResource(R.string.heatmap_grouping)) {
                CoineProSegmentTabs(
                    options = listOf(
                        HeatmapGrouping.NONE to stringResource(R.string.heatmap_grouping_none),
                        HeatmapGrouping.BY_CLASS to stringResource(R.string.heatmap_grouping_class),
                        HeatmapGrouping.BY_QUOTE to stringResource(R.string.heatmap_grouping_quote),
                    ),
                    selected = options.grouping,
                    onSelect = { onOptions(options.copy(grouping = it)) },
                )
                if (options.grouping != HeatmapGrouping.NONE) {
                    Note(stringResource(R.string.heatmap_grouping_note))
                }
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
                    Note(stringResource(R.string.heatmap_palette_note))
                }
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

/** A sentence under a strip, saying what the strip cannot say on its own. */
@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        modifier = Modifier.padding(horizontal = CoineProSpacing.Two, vertical = 4.dp),
    )
}
