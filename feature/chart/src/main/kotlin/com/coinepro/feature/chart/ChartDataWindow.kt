package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.DataWindow
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.NumberStyle
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.coineProControl
import com.coinepro.core.marketdata.CHART_TIME_ZONE
import java.time.Instant

/**
 * The data window over the plot: the crosshair's bar, or the newest one, as a table (5.14.0).
 *
 * The old terminal's floating panel, row for row: time, open, high, low, close, the change from
 * the previous close in price and percent, volume where the feed has it, then every study on the
 * chart with its value at that bar. The legend reads one line; this reads all of them at once,
 * which is what a reader comparing an RSI against a moving average on a particular Tuesday needs.
 *
 * Every figure is a market figure, so Latin digits throughout; the labels take the page's language.
 */
@Composable
fun ChartDataWindow(
    reading: DataWindow.Reading,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val direction = if (reading.up) CoineProColors.MarketUp else CoineProColors.MarketDown
    val changeColour = if (reading.change >= 0) CoineProColors.MarketUp else CoineProColors.MarketDown
    Column(
        modifier = modifier
            .width(212.dp)
            .clip(CoineProShapes.small)
            .background(CoineProColors.SurfaceElevated)
            .border(1.dp, CoineProColors.Border, CoineProShapes.small)
            .semantics { contentDescription = "chart-data-window" },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = CoineProSpacing.One, end = CoineProSpacing.Half, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.chart_data_window),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .clip(CoineProShapes.small)
                    .coineProControl(onClick = onClose)
                    .padding(4.dp),
            ) {
                Icon(
                    painter = painterResource(DesignR.drawable.icon_x),
                    contentDescription = stringResource(R.string.chart_data_window_close),
                    modifier = Modifier.size(14.dp),
                    tint = CoineProColors.TextMuted,
                )
            }
        }
        HorizontalDivider(color = CoineProColors.Border, thickness = 1.dp)
        Column(
            modifier = Modifier.padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            DataRow(stringResource(R.string.chart_data_time), barTime(reading.time), CoineProColors.TextPrimary)
            DataRow(stringResource(R.string.chart_data_open), MarketNumberFormatter.priceAuto(reading.open), direction)
            DataRow(stringResource(R.string.chart_data_high), MarketNumberFormatter.priceAuto(reading.high), direction)
            DataRow(stringResource(R.string.chart_data_low), MarketNumberFormatter.priceAuto(reading.low), direction)
            DataRow(stringResource(R.string.chart_data_close), MarketNumberFormatter.priceAuto(reading.close), direction)
            val sign = if (reading.change > 0) "+" else ""
            val percent = reading.changePercent?.let { " (" + MarketNumberFormatter.signedPercent(it) + ")" } ?: ""
            DataRow(
                stringResource(R.string.chart_data_change),
                BidiText.isolateLtr(sign + MarketNumberFormatter.priceAuto(reading.change) + percent),
                changeColour,
            )
            reading.volume?.let { DataRow(stringResource(R.string.chart_data_volume), compactFigure(it), CoineProColors.TextPrimary) }
            if (reading.rows.isNotEmpty()) {
                HorizontalDivider(
                    color = CoineProColors.BorderSubtle,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            for (row in reading.rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CoineProShapes.small)
                            .background(Color(row.colour.toInt())),
                    )
                    Text(
                        text = BidiText.isolateLtr(row.label),
                        style = CoineProTextStyles.Numeric.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                        color = CoineProColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = BidiText.isolateLtr(
                            row.values.joinToString(" / ") { value -> value?.let(MarketNumberFormatter::priceAuto) ?: "—" },
                        ),
                        style = CoineProTextStyles.Numeric.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                        color = CoineProColors.TextPrimary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun DataRow(label: String, value: String, colour: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = CoineProTextStyles.Numeric.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
            color = colour,
            maxLines = 1,
        )
    }
}

/** The bar's open in the chart's own zone, as the time axis reads it: `2024-08-12 14:00`. */
internal fun barTime(epochSeconds: Long): String {
    val at = Instant.ofEpochSecond(epochSeconds).atZone(CHART_TIME_ZONE)
    fun two(value: Int) = value.toString().padStart(2, '0')
    return BidiText.isolateLtr("${at.year}-${two(at.monthValue)}-${two(at.dayOfMonth)} ${two(at.hour)}:${two(at.minute)}")
}

/** Volume the way the terminal's window wrote it: `1.25M`, `840.50K`. */
internal fun compactFigure(value: Double): String {
    val magnitude = kotlin.math.abs(value)
    val text = when {
        magnitude >= 1e9 -> NumberStyle.fixed(value / 1e9, 2) + "B"
        magnitude >= 1e6 -> NumberStyle.fixed(value / 1e6, 2) + "M"
        magnitude >= 1e3 -> NumberStyle.fixed(value / 1e3, 2) + "K"
        else -> NumberStyle.grouped(value, 0)
    }
    return BidiText.isolateLtr(text)
}
