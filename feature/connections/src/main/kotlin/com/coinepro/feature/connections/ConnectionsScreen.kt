package com.coinepro.feature.connections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProSkeleton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.execution.LbankPermission
import com.coinepro.core.execution.VenueConnection
import com.coinepro.core.model.MarketPlatform

/**
 * Where a reader links the account their signals will execute through.
 *
 * Scoped to one venue, because a platform has one: MetaTrader 5 executes CoinePro-FX, LBank
 * executes TradeYar. Showing both at once invited someone in a crypto session to hand over their
 * broker password to a screen that would never use it.
 *
 * The screen is careful about a distinction the product depends on and readers routinely miss:
 * entering credentials is *setup*, not connection. Only the backend can say a venue verified them,
 * so nothing here turns green on a successful save.
 */
@Composable
fun ConnectionsScreen(
    controller: ExecutionController,
    platform: MarketPlatform = MarketPlatform.TRADEYAR,
) {
    LaunchedEffect(controller) { controller.refreshConnections() }
    val state by controller.connections.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
        contentPadding = PaddingValues(
            horizontal = CoineProSpacing.Gutter,
            vertical = CoineProSpacing.Gutter,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Stack),
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = CoineProSpacing.Half),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.connections_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = CoineProColors.TextPrimary,
                )
                Text(
                    text = stringResource(R.string.connections_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }
        }

        item { Caution(stringResource(R.string.connections_setup_not_proof)) }

        if (state.loading) {
            item {
                CoineProCard(modifier = Modifier.fillMaxWidth()) {
                    CoineProSkeleton(Modifier.fillMaxWidth(0.4f), height = 20.dp)
                    Row(Modifier.padding(top = CoineProSpacing.OneHalf)) {
                        CoineProSkeleton(Modifier.fillMaxWidth(), height = 14.dp)
                    }
                }
            }
        }

        // Server wording, shown verbatim: the client did not diagnose these and must not reword them.
        state.error?.let { item { Caution(it, CoineProColors.Sell) } }
        state.message?.let { item { Caution(it, CoineProColors.Buy) } }

        if (state.unsupported) {
            // Said plainly, not as a failure. This platform never had a venue-connection surface —
            // it links a broker through copy trading, which lives somewhere else — so a card
            // offering a connection that cannot be made would be worse than none.
            item {
                CoineProCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.connections_unsupported_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = CoineProColors.TextPrimary,
                    )
                    Text(
                        text = stringResource(R.string.connections_unsupported_body),
                        modifier = Modifier.padding(top = CoineProSpacing.One),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.TextSecondary,
                    )
                }
            }
        } else {
            item {
                when (platform) {
                    MarketPlatform.COINEPRO_FX -> Mt5Card(
                        connection = state.mt5,
                        onConnect = controller::connectMt5,
                        onDisconnect = controller::disconnectMt5,
                    )

                    MarketPlatform.TRADEYAR -> LbankCard(
                        connection = state.lbank,
                        onConnect = controller::connectLbank,
                        onDisconnect = controller::disconnectLbank,
                    )
                }
            }
        }
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

    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        VenueHeader("MetaTrader 5", connection)
        Text(
            text = stringResource(R.string.connections_mt5_body),
            modifier = Modifier.padding(top = CoineProSpacing.One),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )

        if (connection == null) {
            Column(
                modifier = Modifier.padding(top = CoineProSpacing.OneHalf),
                verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
            ) {
                CoineProTextField(broker, { broker = it }, stringResource(R.string.connections_broker), Modifier.fillMaxWidth())
                CoineProTextField(server, { server = it }, stringResource(R.string.connections_server), Modifier.fillMaxWidth())
                CoineProTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = stringResource(R.string.connections_login),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                CoineProTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = stringResource(R.string.connections_trading_password),
                    modifier = Modifier.fillMaxWidth(),
                    secret = true,
                )
                val ready = broker.isNotBlank() && server.isNotBlank() &&
                    login.isNotBlank() && password.isNotBlank()
                Submit(
                    text = stringResource(R.string.connections_mt5_connect),
                    enabled = ready,
                ) {
                    onConnect(broker.trim(), server.trim(), login.trim(), password)
                    // Cleared the moment it is handed over: nothing keeps a trading password in
                    // composition state a second longer than the request needs it.
                    password = ""
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(top = CoineProSpacing.OneHalf),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                connection.broker?.let { Detail(stringResource(R.string.connections_broker), it) }
                connection.server?.let { Detail(stringResource(R.string.connections_server), it) }
                connection.loginMasked?.let { Detail(stringResource(R.string.connections_login), it) }
                CoineProSecondaryButton(
                    text = stringResource(R.string.connections_disconnect),
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                )
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

    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        VenueHeader("LBank", connection)
        Text(
            text = stringResource(R.string.connections_lbank_body),
            modifier = Modifier.padding(top = CoineProSpacing.One),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
        // Warning-coloured and above the fields, not muted below them. The exchange has no test
        // environment at all — one production host, so every key is a real key and every order
        // spends real money. That is the one thing on this screen that costs something if it is
        // skimmed past, so it is not allowed to look like the rest of the copy.
        Text(
            text = stringResource(R.string.connections_lbank_live_only),
            modifier = Modifier.padding(top = CoineProSpacing.One),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.Warning,
        )

        Column(
            modifier = Modifier.padding(top = CoineProSpacing.OneHalf),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        ) {
            if (connection != null) {
                connection.keyHint?.let {
                    Detail(
                        stringResource(R.string.connections_key_ending),
                        BidiText.isolateLtr("••••$it"),
                    )
                }
                connection.lbankPermission?.let {
                    Detail(stringResource(R.string.connections_permission), it.name)
                }
                if (!connection.connected) {
                    Caution(stringResource(R.string.connections_pending_verification))
                }
                CoineProSecondaryButton(
                    text = stringResource(R.string.connections_lbank_remove),
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            CoineProSegmentedControl(
                options = LbankPermission.entries.map { it to it.label() },
                selected = permission,
                onSelect = { permission = it },
            )
            // Above the fields, not below them: the advice is only worth anything to someone who
            // has not minted the key yet, and a key created with withdrawal rights cannot be
            // narrowed afterwards — it has to be replaced.
            Caution(stringResource(R.string.connections_key_scope_advice), CoineProColors.TextSecondary)
            CoineProTextField(apiKey, { apiKey = it }, stringResource(R.string.connections_api_key), Modifier.fillMaxWidth())
            CoineProTextField(
                value = apiSecret,
                onValueChange = { apiSecret = it },
                label = stringResource(R.string.connections_api_secret),
                modifier = Modifier.fillMaxWidth(),
                secret = true,
            )
            Submit(
                text = stringResource(
                    if (connection == null) {
                        R.string.connections_lbank_save
                    } else {
                        R.string.connections_lbank_replace
                    },
                ),
                enabled = apiKey.isNotBlank() && apiSecret.isNotBlank(),
            ) {
                onConnect(apiKey.trim(), apiSecret, permission)
                apiKey = ""
                apiSecret = ""
            }
        }
    }
}

/* ------------------------------------------------------------------ parts */

@Composable
private fun VenueHeader(name: String, connection: VenueConnection?) {
    val statusRes = when {
        connection == null -> R.string.connections_status_not_configured
        connection.connected -> R.string.connections_status_connected
        else -> R.string.connections_status_configured
    }
    // Server status text wins when it has any, since only the venue knows why it is not connected.
    val raw = connection?.status?.takeIf { it.isNotBlank() && !connection.connected }
    val status = raw?.replace('_', ' ') ?: stringResource(statusRes)
    val colour = when {
        connection?.connected == true -> CoineProColors.Buy
        connection == null -> CoineProColors.TextMuted
        else -> CoineProColors.Warning
    }
    val label = stringResource(R.string.connections_status_of, name, status)

    Row(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = CoineProColors.TextPrimary)
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(6.dp).background(colour, CoineProPillShape))
            Text(status, style = MaterialTheme.typography.labelMedium, color = colour)
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = CoineProColors.TextPrimary)
    }
}

@Composable
private fun Caution(message: String, accent: Color = CoineProColors.Warning) {
    Text(
        text = message,
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.10f), MaterialTheme.shapes.medium)
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.OneHalf),
        style = MaterialTheme.typography.bodySmall,
        color = accent,
    )
}

/**
 * The card's one gold action, dimmed rather than hidden when the form is incomplete — a button that
 * disappears leaves a reader wondering what they did wrong.
 */
@Composable
private fun Submit(text: String, enabled: Boolean, onClick: () -> Unit) {
    CoineProPrimaryButton(
        text = text,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
    )
}

@Composable
private fun LbankPermission.label(): String = when (this) {
    LbankPermission.SPOT -> stringResource(R.string.connections_permission_spot)
    LbankPermission.FUTURES -> stringResource(R.string.connections_permission_futures)
}
