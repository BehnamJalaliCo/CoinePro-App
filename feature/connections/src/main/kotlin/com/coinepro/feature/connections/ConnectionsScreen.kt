package com.coinepro.feature.connections

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.execution.LbankPermission
import com.coinepro.core.execution.VenueConnection

@Composable
fun ConnectionsScreen(controller: ExecutionController) {
    LaunchedEffect(controller) { controller.refreshConnections() }
    val state by controller.connections.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Connections", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Connect accounts only to execute CoinePro signals. Credentials are sent to the backend over HTTPS and are never stored by the Android app.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Values you enter are setup inputs, not proof of a live provider connection. Connected/verified state comes only from the backend/provider. If verification is missing or fails, execution stays unavailable or pending instead of being guessed locally.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.loading) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Mt5Card(
            connection = state.mt5,
            onConnect = controller::connectMt5,
            onDisconnect = controller::disconnectMt5,
        )
        LbankCard(
            connection = state.lbank,
            onConnect = controller::connectLbank,
            onDisconnect = controller::disconnectLbank,
        )
    }
}

@Composable
private fun Mt5Card(
    connection: VenueConnection?,
    onConnect: (String, String, String, String) -> Unit,
    onDisconnect: () -> Unit,
) {
    var broker by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("MetaTrader 5", style = MaterialTheme.typography.titleLarge)
            ConnectionStatus(connection, "MT5")
            Text(
                "Broker, server, login and trading password are account inputs you provide. CoinePro does not treat saving them as a successful MT5 connection; provider verification must be returned by the backend.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (connection == null) {
                OutlinedTextField(broker, { broker = it }, label = { Text("Broker") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(server, { server = it }, label = { Text("MT5 server") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    login,
                    { login = it },
                    label = { Text("Login") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text("Trading password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        onConnect(broker.trim(), server.trim(), login.trim(), password)
                        password = ""
                    },
                    enabled = broker.isNotBlank() && server.isNotBlank() && login.isNotBlank() && password.isNotBlank(),
                ) { Text("Connect MT5") }
            } else {
                connection.broker?.let { Text("Broker: $it") }
                connection.server?.let { Text("Server: $it") }
                connection.loginMasked?.let { Text("Login: $it") }
                TextButton(onClick = onDisconnect) { Text("Disconnect") }
            }
        }
    }
}

@Composable
private fun LbankCard(
    connection: VenueConnection?,
    onConnect: (String, String, LbankPermission) -> Unit,
    onDisconnect: () -> Unit,
) {
    var apiKey by remember { mutableStateOf("") }
    var apiSecret by remember { mutableStateOf("") }
    var permission by remember { mutableStateOf(LbankPermission.SPOT) }

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("LBank", style = MaterialTheme.typography.titleLarge)
            ConnectionStatus(connection, "LBank")
            Text(
                "Use an LBank API key with only the permission you need: Spot or Futures. Withdrawal permission is not required. Saving credentials does not mean the exchange has verified them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (connection != null) {
                connection.keyHint?.let { Text("Key ending: ••••$it") }
                connection.lbankPermission?.let { Text("Permission: ${it.name}") }
                if (!connection.connected) {
                    Text(
                        "Saved securely; provider verification is still pending. Execution remains QUEUED until LBank confirms it.",
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                TextButton(onClick = onDisconnect) { Text("Remove LBank connection") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LbankPermission.entries.forEach { option ->
                    FilterChip(
                        selected = permission == option,
                        onClick = { permission = option },
                        label = { Text(option.name) },
                    )
                }
            }
            OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                apiSecret,
                { apiSecret = it },
                label = { Text("API Secret") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    onConnect(apiKey.trim(), apiSecret, permission)
                    apiKey = ""
                    apiSecret = ""
                },
                enabled = apiKey.isNotBlank() && apiSecret.isNotBlank(),
            ) { Text(if (connection == null) "Save LBank connection" else "Replace credentials") }
        }
    }
}

@Composable
private fun ConnectionStatus(connection: VenueConnection?, venueName: String) {
    val text = when {
        connection == null -> "Not configured"
        connection.connected -> "Connected"
        connection.status.isNotBlank() -> connection.status.replace('_', ' ')
        else -> "Configured"
    }
    val color = when {
        connection?.connected == true -> MaterialTheme.colorScheme.primary
        connection == null -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.tertiary
    }
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.semantics { contentDescription = "$venueName connection status: $text" },
    )
}
