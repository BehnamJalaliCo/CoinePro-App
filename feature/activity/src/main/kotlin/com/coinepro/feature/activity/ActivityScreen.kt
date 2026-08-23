package com.coinepro.feature.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.notifications.NotificationController
import com.coinepro.core.notifications.PriceAlert
import com.coinepro.core.notifications.PriceAlertCondition
import com.coinepro.core.notifications.PriceAlertTrigger
import com.coinepro.core.notifications.PushPreferences

@Composable
fun ActivityScreen(
    controller: NotificationController,
    onOpenSignal: (Long) -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var symbol by remember { mutableStateOf("XAUUSD") }
    var value by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf(PriceAlertCondition.CROSS) }

    LaunchedEffect(controller) {
        controller.refresh()
        controller.markRead()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        item {
            Text(
                text = "Activity & Alerts",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Server-truth signal events and price alerts. No fake local triggers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            PreferenceCard(
                value = state.preferences,
                onChange = controller::updatePreferences,
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(CoineProSpacing.Two),
                    verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                ) {
                    Text("New price alert", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = symbol,
                        onValueChange = { symbol = it.uppercase() },
                        label = { Text("Symbol") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Price") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                        listOf(
                            PriceAlertCondition.ABOVE,
                            PriceAlertCondition.BELOW,
                            PriceAlertCondition.CROSS,
                        ).forEach { item ->
                            FilterChip(
                                selected = condition == item,
                                onClick = { condition = item },
                                label = { Text(item.name.replace('_', ' ')) },
                            )
                        }
                    }
                    Button(
                        onClick = {
                            val target = value.toDoubleOrNull()
                            if (target != null && target > 0) {
                                controller.createAlert(
                                    symbol = symbol,
                                    condition = condition,
                                    value = target,
                                    trigger = PriceAlertTrigger.ONCE,
                                )
                                value = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Create alert")
                    }
                }
            }
        }

        if (state.alerts.isNotEmpty()) {
            item { Text("Price alerts", fontWeight = FontWeight.SemiBold) }
            items(state.alerts, key = { it.id }) { alert ->
                AlertRow(
                    alert = alert,
                    onToggle = { controller.setAlertActive(alert, it) },
                    onDelete = { controller.deleteAlert(alert.id) },
                )
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Notifications", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = controller::refresh) { Text("Refresh") }
            }
        }

        if (state.notifications.isEmpty() && !state.loading) {
            item {
                Text(
                    text = "No notifications yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.notifications) { notification ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = notification.signalId != null) {
                            notification.signalId?.let(onOpenSignal)
                        },
                ) {
                    Column(modifier = Modifier.padding(CoineProSpacing.Two)) {
                        Text(notification.title, fontWeight = FontWeight.SemiBold)
                        if (notification.body.isNotBlank()) {
                            Spacer(Modifier.height(CoineProSpacing.Half))
                            Text(
                                notification.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        state.lastError?.let { error ->
            item {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        item { Spacer(Modifier.height(CoineProSpacing.Three)) }
    }
}

@Composable
private fun PreferenceCard(
    value: PushPreferences,
    onChange: (PushPreferences) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CoineProSpacing.Two)) {
            Text("Push preferences", fontWeight = FontWeight.SemiBold)
            PreferenceRow("New signals", value.newSignals) {
                onChange(value.copy(newSignals = it))
            }
            HorizontalDivider()
            PreferenceRow("Entry / TP / SL", value.signalUpdates) {
                onChange(value.copy(signalUpdates = it))
            }
            HorizontalDivider()
            PreferenceRow("Price alerts", value.priceAlerts) {
                onChange(value.copy(priceAlerts = it))
            }
        }
    }
}

@Composable
private fun PreferenceRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.padding(vertical = CoineProSpacing.OneHalf))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun AlertRow(
    alert: PriceAlert,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(CoineProSpacing.Two)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(alert.symbol, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${alert.condition.name.replace('_', ' ')} ${MarketNumberFormatter.price(alert.value, 2)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = alert.active, onCheckedChange = onToggle)
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}
