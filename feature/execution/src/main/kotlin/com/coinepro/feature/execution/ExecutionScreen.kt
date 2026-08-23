package com.coinepro.feature.execution

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.execution.ExecutionStatus
import com.coinepro.core.execution.ExecutionVenue
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.SignalDirection
import com.coinepro.core.signals.SignalController
import java.util.UUID

@Composable
fun ExecutionScreen(
    signalId: Long,
    signalController: SignalController,
    executionController: ExecutionController,
    onOpenConnections: () -> Unit,
) {
    LaunchedEffect(signalId) {
        signalController.loadDetail(signalId)
        executionController.refreshConnections()
    }
    DisposableEffect(signalId) {
        onDispose {
            executionController.clearExecution()
            signalController.clearDetail()
        }
    }

    val signalState by signalController.detailState.collectAsStateWithLifecycle()
    val connectionState by executionController.connections.collectAsStateWithLifecycle()
    val executionState by executionController.execution.collectAsStateWithLifecycle()
    val signal = signalState.signal
    val venue = when (signal?.market) {
        MarketType.FOREX -> ExecutionVenue.MT5
        MarketType.CRYPTO -> ExecutionVenue.LBANK
        null -> null
    }
    val connection = when (venue) {
        ExecutionVenue.MT5 -> connectionState.mt5
        ExecutionVenue.LBANK -> connectionState.lbank
        null -> null
    }
    val connectionUsable = when (venue) {
        ExecutionVenue.MT5 -> connection?.connected == true
        ExecutionVenue.LBANK -> connection?.configured == true
        null -> false
    }

    var quantityText by remember(signalId) { mutableStateOf("") }
    var confirmed by remember(signalId) { mutableStateOf(false) }
    val clientRequestId = remember(signalId) { UUID.randomUUID().toString() }
    val quantity = quantityText.toDoubleOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Execute CoinePro signal", style = MaterialTheme.typography.headlineSmall)
        Text(
            "This screen can execute only the selected CoinePro signal. Symbol, direction, Entry, SL and TP are server-owned and cannot be replaced here.",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (signalState.loading || connectionState.loading) CircularProgressIndicator()
        signalState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        connectionState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (signal != null) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${signal.symbol} · ${signal.direction.name}", style = MaterialTheme.typography.titleLarge)
                    signal.timeframe?.let { Text("Timeframe: $it") }
                    Text("Entry: ${signal.entry?.let(::priceText) ?: "—"}")
                    Text("Stop: ${signal.stopLoss?.let(::priceText) ?: "—"}")
                    signal.targets.sortedBy { it.level }.forEach { target ->
                        Text("TP${target.level}: ${target.price?.let(::priceText) ?: "—"}")
                    }
                }
            }
        }

        if (venue != null) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Venue: ${if (venue == ExecutionVenue.MT5) "MetaTrader 5" else "LBank"}")
                    Text(
                        when {
                            connection == null -> "No connection configured."
                            connection.connected -> "Connection confirmed by backend."
                            venue == ExecutionVenue.LBANK -> "LBank credentials are saved; provider verification is pending. A request can be queued, but it is not an open trade until provider acknowledgement."
                            else -> "Connection status: ${connection.status.ifBlank { "pending" }}"
                        },
                        color = if (connectionUsable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                    )
                    if (!connectionUsable) {
                        Button(onClick = onOpenConnections) { Text("Open Connections") }
                    }
                }
            }
        }

        if (executionState.execution == null && signal != null && venue != null) {
            OutlinedTextField(
                value = quantityText,
                onValueChange = { quantityText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text(if (venue == ExecutionVenue.MT5) "Lot / execution quantity" else "Base-asset quantity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                Text(
                    "I confirm this exact signal execution request.",
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Button(
                onClick = {
                    executionController.executeSignal(
                        signalId = signal.id,
                        venue = venue,
                        quantity = requireNotNull(quantity),
                        clientRequestId = clientRequestId,
                    )
                },
                enabled =
                    connectionUsable &&
                    signal.status == "active" &&
                    signal.direction in setOf(SignalDirection.BUY, SignalDirection.SELL) &&
                    quantity != null && quantity > 0 && confirmed && !executionState.loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Confirm execution") }
        }

        executionState.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        executionState.execution?.let { execution ->
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Execution status", style = MaterialTheme.typography.titleMedium)
                    Text(execution.status.name.replace('_', ' '), style = MaterialTheme.typography.titleLarge)
                    Text("Venue: ${execution.venue.name} · Product: ${execution.product.ifBlank { "—" }}")
                    Text("Quantity: ${execution.quantity}")
                    when (execution.status) {
                        ExecutionStatus.QUEUED -> Text(
                            "Queued only. The broker/exchange has not confirmed an open trade.",
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        ExecutionStatus.SUBMITTED -> Text("Submitted to provider; open state is not confirmed yet.")
                        ExecutionStatus.OPEN -> Text("Provider has confirmed this execution as open.", color = MaterialTheme.colorScheme.primary)
                        ExecutionStatus.CLOSE_REQUESTED -> Text("Close requested; waiting for provider confirmation.")
                        ExecutionStatus.CLOSED -> Text("Provider has confirmed this execution as closed.")
                        ExecutionStatus.FAILED -> Text(execution.errorMessage ?: "Provider reported execution failure.", color = MaterialTheme.colorScheme.error)
                        ExecutionStatus.CANCELLED -> Text("Queued request was cancelled before provider confirmation.")
                        ExecutionStatus.UNKNOWN -> Text("Unknown provider state; no open/closed assumption is made.", color = MaterialTheme.colorScheme.error)
                    }
                    execution.providerOrderId?.let { Text("Provider order: $it") }
                    if (execution.canRequestClose) {
                        TextButton(onClick = executionController::requestClose) {
                            Text(if (execution.status == ExecutionStatus.QUEUED) "Cancel queued request" else "Request close")
                        }
                    }
                }
            }
        }
    }
}

private fun priceText(value: Double): String = MarketNumberFormatter.price(value, 6).trimEnd('0').trimEnd('.')
