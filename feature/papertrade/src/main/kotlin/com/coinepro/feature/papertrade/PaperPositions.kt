package com.coinepro.feature.papertrade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.rowMotion
import com.coinepro.core.papertrade.PaperPosition
import com.coinepro.core.papertrade.PaperTradeController
import com.coinepro.core.papertrade.PaperTradeUiState

/**
 * What is open, marked against the live feed.
 *
 * Every row carries the whole decision: what it is, what it cost, what it is worth now, and what
 * can be done to it without leaving the list. The four actions are the four a trader actually
 * takes — take it all off, take half off, turn it round, move the stop — and they are buttons
 * rather than a menu because a position going against you is not the moment to hunt for a submenu.
 */
@Composable
fun PaperPositions(
    state: PaperTradeUiState,
    controller: PaperTradeController,
    onOpenSymbol: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var protecting by rememberSaveable { mutableStateOf<Long?>(null) }

    if (state.book.positions.isEmpty()) {
        CoineProEmptyState(
            message = stringResource(R.string.paper_positions_empty),
            icon = CoineProIcons.Wallet,
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
        items(state.book.positions, key = PaperPosition::id) { position ->
            Column(modifier = rowMotion().fillMaxWidth()) {
                PositionCard(
                    position = position,
                    mark = state.marks[position.symbol],
                    onClose = { controller.closePosition(position.id, 1.0) },
                    onCloseHalf = { controller.closePosition(position.id, 0.5) },
                    onReverse = { controller.reverse(position.id) },
                    onProtect = { protecting = position.id },
                    onOpenSymbol = onOpenSymbol,
                )
            }
        }
        item {
            CoineProSecondaryButton(
                text = stringResource(R.string.paper_close_all),
                onClick = { controller.closeAll() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val target = protecting?.let { id -> state.book.positions.firstOrNull { it.id == id } }
    if (target != null) {
        ProtectionSheet(
            position = target,
            onDismiss = { protecting = null },
            onSave = { stopLoss, takeProfit ->
                controller.setProtection(target.id, stopLoss, takeProfit)
                protecting = null
            },
        )
    }
}

@Composable
private fun PositionCard(
    position: PaperPosition,
    mark: Double?,
    onClose: () -> Unit,
    onCloseHalf: () -> Unit,
    onReverse: () -> Unit,
    onProtect: () -> Unit,
    onOpenSymbol: ((String) -> Unit)?,
) {
    val profit = position.unrealised(mark)
    CoineProCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpenSymbol?.let { open -> { open(position.symbol) } },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = PaperFormat.ticker(position.symbol),
                        style = MaterialTheme.typography.titleSmall,
                        color = CoineProColors.TextPrimary,
                    )
                    Text(
                        text = PaperFormat.sideLabel(position.side) + " · " + PaperFormat.size(position.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = PaperFormat.sideTone(position.side),
                    )
                }
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    Text(
                        // A dash, not a zero. An open position with no mark has an unknown result,
                        // and a zero would read as a trade that went nowhere.
                        text = PaperFormat.money(profit, signed = true),
                        style = MaterialTheme.typography.titleSmall,
                        color = PaperFormat.tone(profit),
                    )
                    Text(
                        text = PaperFormat.percent(position.unrealisedPercent(mark)),
                        style = MaterialTheme.typography.labelSmall,
                        color = PaperFormat.tone(profit),
                    )
                }
            }
            Reading(stringResource(R.string.paper_entry), PaperFormat.price(position.entry))
            Reading(stringResource(R.string.paper_mark), PaperFormat.price(mark))
            if (position.stopLoss != null || position.takeProfit != null) {
                Reading(
                    stringResource(R.string.paper_stop_loss) + " · " + stringResource(R.string.paper_take_profit),
                    PaperFormat.price(position.stopLoss) + " · " + PaperFormat.price(position.takeProfit),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                PaperBadge(modifier = Modifier.padding(top = CoineProSpacing.Half))
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.Half),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            ) {
                CoineProSecondaryButton(
                    text = stringResource(R.string.paper_close),
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                )
                CoineProSecondaryButton(
                    text = stringResource(R.string.paper_close_half),
                    onClick = onCloseHalf,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            ) {
                CoineProSecondaryButton(
                    text = stringResource(R.string.paper_reverse),
                    onClick = onReverse,
                    modifier = Modifier.weight(1f),
                )
                CoineProSecondaryButton(
                    text = stringResource(R.string.paper_protect),
                    onClick = onProtect,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Where a stop and a target are attached to a position that is already open.
 *
 * Both fields empty means no protection at all, and clearing one is how it is removed — there is no
 * separate delete, because a reader who wants the stop gone deletes the number, which is what they
 * would do anyway.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtectionSheet(
    position: PaperPosition,
    onDismiss: () -> Unit,
    onSave: (Double?, Double?) -> Unit,
) {
    var stopLoss by rememberSaveable(position.id) {
        mutableStateOf(position.stopLoss?.let { PaperFormat.size(it) }.orEmpty())
    }
    var takeProfit by rememberSaveable(position.id) {
        mutableStateOf(position.takeProfit?.let { PaperFormat.size(it) }.orEmpty())
    }

    CoineProSheet(
        title = stringResource(R.string.paper_protect_title),
        subtitle = PaperFormat.ticker(position.symbol) + " · " + PaperFormat.sideLabel(position.side),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Text(
                text = stringResource(R.string.paper_rule_stop),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
            )
            CoineProTextField(
                value = stopLoss,
                onValueChange = { stopLoss = it },
                label = stringResource(R.string.paper_stop_loss),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            CoineProTextField(
                value = takeProfit,
                onValueChange = { takeProfit = it },
                label = stringResource(R.string.paper_take_profit),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            CoineProPrimaryButton(
                text = stringResource(R.string.paper_save),
                onClick = { onSave(stopLoss.asNumber(), takeProfit.asNumber()) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.paper_banner),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}
