package com.coinepro.feature.alerts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.foldDigitsToLatin
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.resolve
import com.coinepro.core.notifications.NotificationController
import com.coinepro.core.notifications.PriceAlert
import com.coinepro.core.notifications.PriceAlertCondition
import com.coinepro.core.notifications.PriceAlertTrigger

/**
 * Price alerts: making them, and turning them off.
 *
 * The gateway for this has existed since the notification work and had no screen, which meant the
 * app could *receive* a price alert it gave nobody any way to create. Every alert in the list came
 * from somewhere else.
 *
 * Two decisions worth stating.
 *
 * **Five conditions, not two.** "Above" and "below" fire whenever the price is already there, which
 * is what somebody usually does *not* want on an instrument that has been above their level all
 * week. The three crossing conditions fire on the transition, and telling them apart is the whole
 * difference between an alert that arrives once when something happens and one that arrives the
 * moment it is set.
 *
 * **Once by default.** A recurring alert on a price that oscillates around the level is a phone
 * that will not stop, and the reader who set it is asleep. Recurring is offered, deliberately, as
 * the second choice.
 */
@Composable
fun AlertsScreen(controller: NotificationController, initialSymbol: String? = null) {
    val state by controller.state.collectAsStateWithLifecycle()

    LaunchedEffect(controller) { controller.refresh() }

    var symbol by rememberSaveable { mutableStateOf(initialSymbol.orEmpty()) }
    var value by rememberSaveable { mutableStateOf("") }
    var condition by rememberSaveable { mutableStateOf(PriceAlertCondition.CROSS_UP) }
    var recurring by rememberSaveable { mutableStateOf(false) }

    val price = value.foldDigitsToLatin().trim().toDoubleOrNull()
    val armed = symbol.isNotBlank() && price != null && price > 0

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
    ) {
        item {
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf)) {
                    Text(
                        text = stringResource(R.string.alerts_new_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = CoineProColors.TextPrimary,
                    )
                    CoineProTextField(
                        value = symbol,
                        onValueChange = { symbol = it.uppercase() },
                        label = stringResource(R.string.alerts_symbol),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CoineProTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = stringResource(R.string.alerts_price),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                        PriceAlertCondition.entries.forEach { option ->
                            Chip(
                                label = stringResource(option.labelRes()),
                                selected = option == condition,
                                onClick = { condition = option },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.alerts_recurring),
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoineProColors.TextPrimary,
                            )
                            Text(
                                text = stringResource(R.string.alerts_recurring_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = CoineProColors.TextMuted,
                            )
                        }
                        Switch(checked = recurring, onCheckedChange = { recurring = it })
                    }

                    CoineProPrimaryButton(
                        text = stringResource(R.string.alerts_create),
                        onClick = {
                            controller.createAlert(
                                symbol = symbol,
                                condition = condition,
                                value = price ?: return@CoineProPrimaryButton,
                                trigger = if (recurring) PriceAlertTrigger.RECURRING else PriceAlertTrigger.ONCE,
                            )
                            value = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = armed,
                    )
                    // The server's own wording, where it refused. The app cannot know why a
                    // particular symbol was rejected on a particular deployment.
                    state.lastMessage?.let { message ->
                        Text(
                            text = message.resolve(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = CoineProColors.Sell,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.alerts_existing_title),
                style = MaterialTheme.typography.titleMedium,
                color = CoineProColors.TextPrimary,
            )
        }

        if (state.alerts.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.alerts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextMuted,
                )
            }
        }

        items(state.alerts, key = PriceAlert::id) { alert ->
            AlertRow(
                alert = alert,
                onToggle = { controller.setAlertActive(alert, !alert.active) },
                onDelete = { controller.deleteAlert(alert.id) },
            )
        }
    }
}

@Composable
private fun AlertRow(alert: PriceAlert, onToggle: () -> Unit, onDelete: () -> Unit) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = BidiText.isolateLtr(alert.symbol),
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                )
                Text(
                    text = stringResource(
                        alert.condition.labelRes(),
                    ) + " " + BidiText.isolateLtr(MarketNumberFormatter.priceAuto(alert.value)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
                if (alert.trigger == PriceAlertTrigger.RECURRING) {
                    Text(
                        text = stringResource(R.string.alerts_recurring),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextMuted,
                    )
                }
            }
            Switch(checked = alert.active, onCheckedChange = { onToggle() })
            Text(
                text = stringResource(R.string.alerts_delete),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.Sell,
                modifier = Modifier
                    .clip(CoineProShapes.small)
                    .clickable(onClick = onDelete)
                    .padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.Half),
            )
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) CoineProColors.OnAccent else CoineProColors.TextSecondary,
        modifier = Modifier
            .clip(CoineProShapes.small)
            .background(if (selected) CoineProColors.Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
    )
}

/**
 * Persian for each condition.
 *
 * On the enum rather than in a `when` at each call site, because there are two call sites — the
 * chip row and the list — and a condition added to the enum without a label would silently show
 * the wrong one at whichever site was updated last.
 */
private fun PriceAlertCondition.labelRes(): Int = when (this) {
    PriceAlertCondition.ABOVE -> R.string.alerts_condition_above
    PriceAlertCondition.BELOW -> R.string.alerts_condition_below
    PriceAlertCondition.CROSS_UP -> R.string.alerts_condition_cross_up
    PriceAlertCondition.CROSS_DOWN -> R.string.alerts_condition_cross_down
    PriceAlertCondition.CROSS -> R.string.alerts_condition_cross
}
