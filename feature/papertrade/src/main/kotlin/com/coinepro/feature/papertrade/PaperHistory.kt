package com.coinepro.feature.papertrade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.papertrade.PaperClosedTrade
import com.coinepro.core.papertrade.PaperFill
import com.coinepro.core.papertrade.PaperFillBasis
import com.coinepro.core.papertrade.PaperTradeUiState
import java.time.ZoneId

/**
 * Every round trip that is over, and under it the audit of how each fill got its price.
 *
 * The fill log is the part that makes this feature honest rather than merely detailed. It prints,
 * for every fill, the price it got, the last price at that moment, what the spread and the slippage
 * cost, and two facts about the fill's provenance: whether anything was watching when the level was
 * crossed, and whether the spread crossed was quoted or assumed. A reader who suspects the
 * simulator is being generous can check it, which is the only reason to believe it when it is not.
 */
@Composable
fun PaperHistory(
    state: PaperTradeUiState,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val closed = state.book.closed.sortedByDescending { it.closedAtEpochMillis }
    val fills = state.book.fills.sortedByDescending { it.atEpochMillis }

    if (closed.isEmpty() && fills.isEmpty()) {
        CoineProEmptyState(
            message = stringResource(R.string.paper_history_empty),
            icon = CoineProIcons.Activity,
            hint = stringResource(R.string.paper_disclaimer),
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            start = CoineProSpacing.Gutter,
            end = CoineProSpacing.Gutter,
            bottom = CoineProSpacing.Six,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        if (closed.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.paper_closed_title)) }
            items(closed, key = PaperClosedTrade::id) { trade -> ClosedCard(trade, zone) }
        }
        if (fills.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.paper_fills_title)) }
            items(fills, key = PaperFill::id) { fill -> FillCard(fill, zone) }
        }
    }
}

@Composable
private fun ClosedCard(trade: PaperClosedTrade, zone: ZoneId) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = PaperFormat.ticker(trade.symbol),
                        style = MaterialTheme.typography.titleSmall,
                        color = CoineProColors.TextPrimary,
                    )
                    Text(
                        text = PaperFormat.sideLabel(trade.side) + " · " + PaperFormat.size(trade.size) +
                            " · " + PaperFormat.reasonLabel(trade.reason),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = PaperFormat.money(trade.net, signed = true),
                        style = MaterialTheme.typography.titleSmall,
                        color = PaperFormat.tone(trade.net),
                    )
                    Text(
                        text = PaperFormat.percent(trade.netPercent),
                        style = MaterialTheme.typography.labelSmall,
                        color = PaperFormat.tone(trade.net),
                    )
                }
            }
            Reading(
                stringResource(R.string.paper_entry) + " · " + stringResource(R.string.paper_close),
                PaperFormat.price(trade.entry) + " · " + PaperFormat.price(trade.exit),
            )
            Reading(stringResource(R.string.paper_stat_costs), PaperFormat.money(trade.fees))
            Reading(stringResource(R.string.paper_balance), PaperFormat.money(trade.balanceAfter))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = PaperFormat.moment(trade.closedAtEpochMillis, zone),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
                PaperBadge()
            }
        }
    }
}

@Composable
private fun FillCard(fill: PaperFill, zone: ZoneId) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = PaperFormat.ticker(fill.symbol) + " · " + PaperFormat.sideLabel(fill.side) +
                        " · " + PaperFormat.size(fill.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
                Text(
                    text = PaperFormat.price(fill.price),
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                )
            }
            Text(
                text = stringResource(R.string.paper_fill_reference, PaperFormat.price(fill.reference)),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
            Reading(stringResource(R.string.paper_preview_spread), PaperFormat.money(fill.spreadCost))
            Reading(stringResource(R.string.paper_preview_slippage), PaperFormat.money(fill.slippage))
            Reading(stringResource(R.string.paper_preview_fee), PaperFormat.money(fill.fee))
            Text(
                text = stringResource(
                    if (fill.basis == PaperFillBasis.TAKEN) R.string.paper_fill_taken else R.string.paper_fill_rested,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextSecondary,
            )
            if (!fill.watched) {
                Text(
                    text = stringResource(R.string.paper_fill_unwatched),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.Warning,
                )
            }
            if (fill.assumedSpread) {
                Text(
                    text = stringResource(R.string.paper_fill_assumed),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.Warning,
                )
            }
            Text(
                text = PaperFormat.moment(fill.atEpochMillis, zone),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        }
    }
}
