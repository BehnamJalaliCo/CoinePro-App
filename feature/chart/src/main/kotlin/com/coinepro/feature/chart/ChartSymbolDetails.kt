package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.Candle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.inEnglish
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier
import java.time.ZoneId
import kotlin.math.abs

/**
 * TradingView's symbol details, under the watchlist on the web's desktop page (5.31.0).
 *
 * The mark and the ticker, the instrument's name, the venue and the kind of market, the price in
 * large type with the day's move beside it, when it was last updated, the bid and the ask where the
 * feed gives them, and the day's range and volume. Everything is read from the chart the reader is
 * looking at — the same bars, the same «day» as the legend and the tab title — so the three can
 * never disagree about what the market did.
 */
@Composable
fun ChartSymbolDetails(
    state: ChartUiState,
    zone: ZoneId,
    venue: String?,
    modifier: Modifier = Modifier,
    bid: Double? = null,
    ask: Double? = null,
    /** When the live price last moved, from the feed; the last bar's open is the fallback. */
    updatedAtMillis: Long? = null,
) {
    val english = inEnglish()
    val meta = remember(state.symbol) { SymbolClassifier.classify(state.symbol) }
    // The chart's own precision: the reader's choice, else the instrument's — a pair's five places.
    val places = state.decimals ?: remember(state.symbol) { com.coinepro.core.symbols.SymbolPrecision.of(state.symbol) }
    fun figure(value: Double): String = places?.let { MarketNumberFormatter.price(value, it) } ?: MarketNumberFormatter.priceAuto(value)
    val bars = state.visibleSeries.bars
    val last = bars.lastOrNull()
    val day = remember(bars) { detailsDay(bars) }
    val price = state.lastPrice
    val percent = state.changePercent
    val absolute = state.changeAbsolute
    val ink = when {
        percent == null || percent == 0.0 -> CoineProColors.TextSecondary
        percent > 0 -> CoineProColors.MarketUp
        else -> CoineProColors.MarketDown
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CoineProColors.Stage)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.OneHalf),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoineProAssetLogo(symbol = state.symbol, size = LOGO)
            Spacer(Modifier.width(CoineProSpacing.One))
            Text(
                text = BidiText.isolateLtr(state.symbol),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = CoineProColors.TextPrimary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Text(
                text = listOfNotNull(meta.description(english).takeIf(String::isNotBlank), venue?.let(BidiText::isolateLtr))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(meta.category.labelRes()),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        }
        if (price != null) {
            // A figure: Latin digits, left to right, whatever the page's language.
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = BidiText.isolateLtr(figure(price)),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = CoineProColors.TextPrimary,
                )
                meta.quote?.let { quote ->
                    Spacer(Modifier.width(CoineProSpacing.Half))
                    Text(
                        text = quote,
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                        modifier = Modifier.padding(bottom = CoineProSpacing.Half),
                    )
                }
                if (percent != null) {
                    Spacer(Modifier.width(CoineProSpacing.One))
                    Text(
                        text = BidiText.isolateLtr(
                            listOfNotNull(absolute?.let { (if (it < 0) "−" else "+") + figure(kotlin.math.abs(it)) }, MarketNumberFormatter.signedPercent(percent)).joinToString("  "),
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ink,
                        modifier = Modifier.padding(bottom = CoineProSpacing.Half),
                    )
                }
            }
        }
        val updated = updatedAtMillis?.takeIf { it > 0 } ?: last?.t?.times(1000)
        if (updated != null) {
            Text(
                text = stringResource(R.string.chart_details_updated, BidiText.isolateLtr(desktopClock(updated, zone))),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        }
        if (bid != null || ask != null || day != null) {
            HorizontalDivider(color = CoineProColors.Border)
            Text(
                text = stringResource(R.string.chart_details_key_stats),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            bid?.let { DetailRow(stringResource(R.string.chart_details_bid), figure(it)) }
            ask?.let { DetailRow(stringResource(R.string.chart_details_ask), figure(it)) }
            if (bid != null && ask != null && ask >= bid) {
                DetailRow(stringResource(R.string.chart_details_spread), figure(ask - bid))
            }
            day?.let { window ->
                DetailRow(
                    stringResource(R.string.chart_details_day_range),
                    figure(window.low) + " — " + figure(window.high),
                )
                DetailRow(stringResource(R.string.chart_details_day_open), figure(window.open))
                window.volume?.let { DetailRow(stringResource(R.string.chart_details_volume), detailsVolume(it)) }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = BidiText.isolateLtr(value),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = CoineProColors.TextPrimary,
        )
    }
}

/** The last day of bars: the range the day's move is measured over, and what traded in it. */
internal data class DetailsDay(val open: Double, val high: Double, val low: Double, val volume: Double?)

internal fun detailsDay(bars: List<Candle>): DetailsDay? {
    val last = bars.lastOrNull() ?: return null
    val from = last.t - DAY_SECONDS
    val window = bars.filter { it.t > from }.ifEmpty { listOf(last) }
    val volumes = window.mapNotNull { it.v }
    return DetailsDay(
        open = window.first().o,
        high = window.maxOf { it.h },
        low = window.minOf { it.l },
        volume = volumes.takeIf { it.isNotEmpty() }?.sum(),
    )
}

/** `1.25M`, `840.3K`, `12.4B` — a volume is a figure, so Latin, as the axis writes it. */
internal fun detailsVolume(value: Double): String {
    val magnitude = abs(value)
    return when {
        magnitude >= 1e9 -> MarketNumberFormatter.price(value / 1e9, 2) + "B"
        magnitude >= 1e6 -> MarketNumberFormatter.price(value / 1e6, 2) + "M"
        magnitude >= 1e3 -> MarketNumberFormatter.price(value / 1e3, 1) + "K"
        else -> MarketNumberFormatter.price(value, if (magnitude >= 10) 0 else 2)
    }
}

private fun SymbolCategory.labelRes(): Int = when (this) {
    SymbolCategory.CRYPTO -> R.string.chart_details_crypto
    SymbolCategory.FOREX -> R.string.chart_details_forex
    SymbolCategory.METAL -> R.string.chart_details_metal
    SymbolCategory.INDEX -> R.string.chart_details_index
    SymbolCategory.ENERGY -> R.string.chart_details_energy
    SymbolCategory.OTHER -> R.string.chart_details_other
}

private const val DAY_SECONDS = 86_400L
private val LOGO = 28.dp
