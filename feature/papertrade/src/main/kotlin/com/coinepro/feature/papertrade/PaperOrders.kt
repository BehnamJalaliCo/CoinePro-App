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
import androidx.compose.ui.Alignment
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
import com.coinepro.core.papertrade.PaperOrder
import com.coinepro.core.papertrade.PaperOrderState
import com.coinepro.core.papertrade.PaperTradeController
import com.coinepro.core.papertrade.PaperTradeUiState
import java.time.ZoneId
import kotlin.math.abs

/**
 * Orders that have not become positions yet, and the ones that never will.
 *
 * The settled half of this list is not clutter. A rejected order is the only place a reader ever
 * finds out that their account could not margin what they asked for, or that the symbol they typed
 * has no price — and an app that dropped those rows silently would leave them believing they had
 * placed something. Every settled row carries its reason in a sentence.
 */
@Composable
fun PaperOrders(
    state: PaperTradeUiState,
    controller: PaperTradeController,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    var amending by rememberSaveable { mutableStateOf<Long?>(null) }
    val working = state.book.working
    val settled = state.book.orders.filterNot { it.working }.sortedByDescending { it.placedAtEpochMillis }

    if (working.isEmpty() && settled.isEmpty()) {
        CoineProEmptyState(
            message = stringResource(R.string.paper_orders_empty),
            icon = CoineProIcons.Pending,
            hint = stringResource(R.string.paper_rule_resting),
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
        if (working.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.paper_orders_working)) }
            items(working, key = PaperOrder::id) { order ->
                Column(modifier = rowMotion().fillMaxWidth()) {
                    WorkingCard(
                        order = order,
                        mark = state.marks[order.symbol],
                        onCancel = { controller.cancel(order.id) },
                        onAmend = { amending = order.id },
                    )
                }
            }
        }
        if (settled.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.paper_orders_settled)) }
            items(settled, key = PaperOrder::id) { order ->
                Column(modifier = rowMotion().fillMaxWidth()) { SettledRow(order, zone) }
            }
        }
    }

    val target = amending?.let { id -> working.firstOrNull { it.id == id } }
    if (target != null) {
        AmendSheet(
            order = target,
            onDismiss = { amending = null },
            onSave = { limit, stop, size ->
                controller.amend(target.id, limit, stop, size)
                amending = null
            },
        )
    }
}

@Composable
private fun WorkingCard(
    order: PaperOrder,
    mark: Double?,
    onCancel: () -> Unit,
    onAmend: () -> Unit,
) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = PaperFormat.ticker(order.symbol),
                        style = MaterialTheme.typography.titleSmall,
                        color = CoineProColors.TextPrimary,
                    )
                    Text(
                        text = PaperFormat.sideLabel(order.side) + " · " + PaperFormat.typeLabel(order.type) +
                            " · " + PaperFormat.size(order.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = PaperFormat.sideTone(order.side),
                    )
                }
                PaperBadge()
            }
            Reading(
                stringResource(if (order.triggered) R.string.paper_limit_price else R.string.paper_stop_price),
                PaperFormat.price(order.restingPrice),
            )
            val distance = order.restingPrice?.let { level -> mark?.let { abs(level - it) } }
            if (distance != null) {
                Text(
                    text = stringResource(R.string.paper_distance, PaperFormat.price(distance)),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
            }
            if (order.triggered) {
                Text(
                    text = stringResource(R.string.paper_triggered),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.Warning,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.Half),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            ) {
                CoineProSecondaryButton(
                    text = stringResource(R.string.paper_amend),
                    onClick = onAmend,
                    modifier = Modifier.weight(1f),
                )
                CoineProSecondaryButton(
                    text = stringResource(R.string.paper_cancel),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SettledRow(order: PaperOrder, zone: ZoneId) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = PaperFormat.ticker(order.symbol) + " · " + PaperFormat.typeLabel(order.type),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
                Text(
                    text = order.rejectedBecause?.let { PaperFormat.rejectLabel(it) }
                        ?: PaperFormat.moment(order.settledAtEpochMillis ?: order.placedAtEpochMillis, zone),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (order.rejectedBecause != null) CoineProColors.Sell else CoineProColors.TextMuted,
                )
            }
            Text(
                text = stringResource(
                    when (order.state) {
                        PaperOrderState.FILLED -> R.string.paper_state_filled
                        PaperOrderState.CANCELLED -> R.string.paper_state_cancelled
                        PaperOrderState.REJECTED -> R.string.paper_state_rejected
                        PaperOrderState.WORKING -> R.string.paper_orders_working
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
                color = when (order.state) {
                    PaperOrderState.FILLED -> CoineProColors.Buy
                    PaperOrderState.REJECTED -> CoineProColors.Sell
                    else -> CoineProColors.TextMuted
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmendSheet(
    order: PaperOrder,
    onDismiss: () -> Unit,
    onSave: (Double?, Double?, Double?) -> Unit,
) {
    var limit by rememberSaveable(order.id) { mutableStateOf(order.limitPrice?.let { PaperFormat.size(it) }.orEmpty()) }
    var stop by rememberSaveable(order.id) { mutableStateOf(order.stopPrice?.let { PaperFormat.size(it) }.orEmpty()) }
    var size by rememberSaveable(order.id) { mutableStateOf(PaperFormat.size(order.size)) }

    CoineProSheet(
        title = stringResource(R.string.paper_amend_title),
        subtitle = PaperFormat.ticker(order.symbol) + " · " + PaperFormat.typeLabel(order.type),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            if (order.type.needsLimit) {
                CoineProTextField(
                    value = limit,
                    onValueChange = { limit = it },
                    label = stringResource(R.string.paper_limit_price),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            if (order.type.needsStop) {
                CoineProTextField(
                    value = stop,
                    onValueChange = { stop = it },
                    label = stringResource(R.string.paper_stop_price),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            CoineProTextField(
                value = size,
                onValueChange = { size = it },
                label = stringResource(R.string.paper_size),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            // Moving a level forgets the crossing the old one had watched — see `PaperEngine.amend`.
            Text(
                text = stringResource(R.string.paper_rule_unwatched),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
            CoineProPrimaryButton(
                text = stringResource(R.string.paper_save),
                onClick = { onSave(limit.asNumber(), stop.asNumber(), size.asNumber()) },
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

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = CoineProColors.TextPrimary,
        modifier = Modifier.padding(top = CoineProSpacing.One),
    )
}
