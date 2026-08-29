package com.coinepro.feature.papertrade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.JalaliDate
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.papertrade.PaperRecord
import com.coinepro.core.papertrade.PaperTradeUiState
import com.coinepro.core.portfolio.EquityPoint
import com.coinepro.core.portfolio.MonthlyPerformance
import com.coinepro.core.portfolio.SymbolPerformance
import java.time.ZoneId
import kotlin.math.abs

/**
 * What the account has actually done.
 *
 * Not one statistic here is computed in this module. `PaperRecordMath` translates the paper trades
 * into the shape `PortfolioMath` reads and that arithmetic — the same one the live portfolio screen
 * uses, break-even rule and all — produces every number on this tab. A second implementation would
 * disagree with the portfolio by a rounding convention and nobody could say which «نرخ برد» was the
 * real one.
 *
 * The curve is a **balance** curve rather than cumulative profit, which the live crypto side cannot
 * have: LBank keeps one balance row per user, so there is no history to draw. A paper account knows
 * its balance after every close, so this curve is the account, and the drawdown percentage under it
 * means what it says.
 */
@Composable
fun PaperRecordTab(
    state: PaperTradeUiState,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    if (state.book.closed.isEmpty()) {
        CoineProEmptyState(
            message = stringResource(R.string.paper_record_empty),
            icon = CoineProIcons.TrendUp,
            hint = stringResource(R.string.paper_disclaimer),
            modifier = modifier,
        )
        return
    }
    val record = com.coinepro.core.papertrade.PaperRecordMath.of(state.book.closed, zone)
    val stats = record.stats

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = CoineProSpacing.Gutter,
            end = CoineProSpacing.Gutter,
            bottom = CoineProSpacing.Six,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        item { CurveCard(stats.equity) }
        item { MetricsCard(record) }
        if (record.bySymbol.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.paper_by_symbol)) }
            items(record.bySymbol, key = SymbolPerformance::symbol) { row -> SymbolRow(row) }
        }
        if (record.byMonth.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.paper_by_month)) }
            item { MonthlyCard(record.byMonth) }
        }
    }
}

/**
 * The balance, trade by trade.
 *
 * No axis and no grid: at this height a scale is unreadable and the shape is the message. Scaled to
 * its own extremes, and a curve of fewer than two points draws nothing rather than a flat line — a
 * flat line is a claim that the account did not move.
 */
@Composable
private fun CurveCard(points: List<EquityPoint>) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.paper_curve_title),
            style = MaterialTheme.typography.titleSmall,
            color = CoineProColors.TextPrimary,
        )
        if (points.size < 2) return@CoineProCard
        val rising = points.last().equity >= points.first().equity
        val colour = if (rising) CoineProColors.Buy else CoineProColors.Sell
        // Read out here, not inside the draw scope: a colour token is a composable getter, and a
        // draw lambda is not a composition.
        val baseline = CoineProColors.BorderSubtle
        val density = LocalDensity.current.density
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(top = CoineProSpacing.One),
        ) {
            val low = points.minOf { it.equity }
            val high = points.maxOf { it.equity }
            val span = (high - low).takeIf { it > 0.0 } ?: 1.0
            val step = size.width / (points.size - 1)
            val path = Path()
            points.forEachIndexed { index, point ->
                // Left to right in time, whatever the layout direction. A curve mirrored for a
                // right-to-left page would say the account fell.
                val x = index * step
                val y = ((high - point.equity) / span).toFloat() * size.height
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = colour, style = Stroke(width = 1.6f * density))
            drawLine(
                color = baseline,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = density,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.Half),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = PaperFormat.money(points.first().equity),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
            Text(
                text = PaperFormat.money(points.last().equity),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        }
    }
}

@Composable
private fun MetricsCard(record: PaperRecord) {
    val stats = record.stats
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.paper_record_closed, PaperFormat.count(stats.trades)),
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                )
                PaperBadge()
            }
            Reading(
                stringResource(R.string.paper_stat_net),
                PaperFormat.money(stats.net, signed = true),
                PaperFormat.tone(stats.net),
            )
            Reading(stringResource(R.string.paper_stat_winrate), PaperFormat.ratio(stats.winRate))
            Reading(stringResource(R.string.paper_stat_profit_factor), PaperFormat.ratio(stats.profitFactor))
            Reading(stringResource(R.string.paper_stat_expectancy), PaperFormat.money(stats.expectancy, signed = true))
            Reading(stringResource(R.string.paper_stat_payoff), PaperFormat.ratio(record.payoff))
            Reading(stringResource(R.string.paper_stat_avg_win), PaperFormat.money(record.averageWin))
            Reading(stringResource(R.string.paper_stat_avg_loss), PaperFormat.money(record.averageLoss))
            Reading(stringResource(R.string.paper_stat_best), PaperFormat.money(stats.best, signed = true))
            Reading(stringResource(R.string.paper_stat_worst), PaperFormat.money(stats.worst, signed = true))
            Reading(
                stringResource(R.string.paper_stat_drawdown),
                PaperFormat.money(stats.maxDrawdown) + " · " + PaperFormat.ratio(stats.maxDrawdownPercent),
            )
            Reading(stringResource(R.string.paper_stat_costs), PaperFormat.money(record.costs))
        }
    }
}

@Composable
private fun SymbolRow(row: SymbolPerformance) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = PaperFormat.ticker(row.symbol),
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                )
                Text(
                    text = stringResource(
                        R.string.paper_symbol_line,
                        PaperFormat.count(row.trades),
                        PaperFormat.ratio(row.winRate),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
            }
            Text(
                text = PaperFormat.money(row.net, signed = true),
                style = MaterialTheme.typography.titleSmall,
                color = PaperFormat.tone(row.net),
            )
        }
    }
}

/**
 * Months in the reader's own calendar.
 *
 * Solar Hijri, because a Persian month name over a Gregorian bucket would put three weeks of trades
 * under the wrong heading — `PortfolioMath.byMonth` does the grouping and this only names it. A
 * quiet month is a bar of zero rather than a missing row, so a two-month gap does not read as two
 * consecutive months.
 */
@Composable
private fun MonthlyCard(months: List<MonthlyPerformance>) {
    val widest = months.maxOf { abs(it.net) }.takeIf { it > 0.0 } ?: 1.0
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            months.forEach { month ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                ) {
                    Text(
                        text = JalaliDate(month.year, month.month, 1).monthName,
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextSecondary,
                        modifier = Modifier.weight(0.3f),
                    )
                    Box(modifier = Modifier.weight(0.4f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((abs(month.net) / widest).toFloat().coerceIn(0.02f, 1f))
                                .height(6.dp)
                                .background(PaperFormat.tone(month.net), CoineProShapes.small),
                        )
                    }
                    Text(
                        text = PaperFormat.money(month.net, signed = true),
                        style = MaterialTheme.typography.labelSmall,
                        color = PaperFormat.tone(month.net),
                        modifier = Modifier.weight(0.3f),
                    )
                }
            }
        }
    }
}
