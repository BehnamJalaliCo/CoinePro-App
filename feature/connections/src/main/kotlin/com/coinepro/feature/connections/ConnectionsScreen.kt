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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.annotation.StringRes
import com.coinepro.core.designsystem.CoineProConfirmDialog

/**
 * Where a reader links the exchange account their signals will execute through.
 *
 * ### One venue, and the one that used to be a lie
 *
 * A platform has one venue: LBank executes TradeYar. CoinePro-FX has none — it does not place
 * orders from the app at all, it links a broker account once and a service trades it, which is copy
 * trading and lives on its own screen against `user/account/link`.
 *
 * This screen used to draw a MetaTrader 5 card beside the exchange one: four fields ending in a
 * live **trading password**, and a gold button wired to `ExecutionController.connectMt5` — which
 * throws `ExecutionUnsupportedException` unconditionally, on both platforms, because no backend
 * ever had that route. It could not have worked on the day it was written. The reader who found it
 * was asked to type the most dangerous credential the product touches into a form that could only
 * ever refuse it, on the strength of a heading that promised MetaTrader.
 *
 * So the card is gone and the screen says instead where a forex account is actually linked. That
 * sentence is the whole answer to «اتصال حساب اگر اینجا باشه» — it is, for crypto; it is not, for
 * forex — and a reader who came looking for MetaTrader leaves knowing where to go rather than
 * having filled in a form that went nowhere.
 *
 * ### Setup is not connection
 *
 * A distinction the product depends on and readers routinely miss: entering credentials is *setup*.
 * Only the backend can say a venue verified them, so nothing here turns green on a successful save.
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

        if (connectionsSurface(platform, state.unsupported) == ConnectionsSurface.LINKED_ELSEWHERE) {
            item { ElsewhereCard() }
        } else {
            item {
                LbankCard(
                    connection = state.lbank,
                    onConnect = controller::connectLbank,
                    onDisconnect = controller::disconnectLbank,
                )
            }
            // Under the card the reader came for, because the question it answers is the one they
            // ask after finding only an exchange here: the forex account is linked by copy trading
            // and not by a key, so there is nothing on this screen that would ever connect it.
            item { Caution(stringResource(R.string.connections_forex_elsewhere), CoineProColors.TextSecondary) }
        }
    }
}

/**
 * The two things this screen can be, and there is deliberately no third.
 *
 * The third used to exist: a MetaTrader 5 form, over `ExecutionController.connectMt5`, which throws
 * on every platform because no backend ever served that route. Naming the surfaces as a closed set
 * is what stops it coming back — a venue this app can offer is one it can also complete, and the
 * only one of those is the exchange key.
 */
internal enum class ConnectionsSurface {
    /** LBank, on TradeYar: a key pair the reader mints at the exchange. */
    EXCHANGE_KEY,

    /** No venue here. CoinePro-FX links its broker account through copy trading instead. */
    LINKED_ELSEWHERE,
}

/**
 * Absent is not failed, and the two arrive as one answer.
 *
 * CoinePro-FX has no venue-connection route at all, so its gateway refuses the call rather than
 * answering it emptily — which reaches the controller as its `unsupported` state. The platform is
 * checked as well because the screen has to be honest before the first read returns, and because a
 * form drawn for a platform that cannot use it is the bug this whole file was rewritten for.
 */
internal fun connectionsSurface(
    platform: MarketPlatform,
    unsupported: Boolean,
): ConnectionsSurface = when {
    unsupported -> ConnectionsSurface.LINKED_ELSEWHERE
    platform == MarketPlatform.COINEPRO_FX -> ConnectionsSurface.LINKED_ELSEWHERE
    else -> ConnectionsSurface.EXCHANGE_KEY
}

/**
 * What a platform with no venue connection says for itself.
 *
 * Neither a failure nor an empty list: nothing went wrong, retrying will not help, and the account
 * this reader wants to link is linked somewhere real. Naming that somewhere is the difference
 * between an honest absence and a dead end.
 */
@Composable
private fun ElsewhereCard() {
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

@Composable
private fun LbankCard(
    connection: VenueConnection?,
    onConnect: (String, String, LbankPermission) -> Unit,
    onDisconnect: () -> Unit,
) {
    // The key survives a rotation; the secret does not, for the same reason the password does not.
    var apiKey by rememberSaveable { mutableStateOf("") }
    var apiSecret by remember { mutableStateOf("") }
    var permission by rememberSaveable { mutableStateOf(LbankPermission.SPOT) }

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
                DisconnectButton(label = R.string.connections_lbank_remove, onDisconnect = onDisconnect)
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

/**
 * The button that ends a venue connection, with the question in front of it.
 *
 * Disconnecting throws away credentials the reader typed by hand — an MT5 login and password, or
 * an exchange key pair — and this app never keeps them locally, so the only way back is to find
 * them again. It also stops every copy running from that account, which is the part that costs
 * money rather than time.
 *
 * The dialog lives here rather than in either card because both ask the same question, and the
 * wrong outcome is identical on either.
 */
@Composable
private fun DisconnectButton(@StringRes label: Int, onDisconnect: () -> Unit) {
    var asked by rememberSaveable { mutableStateOf(false) }
    CoineProSecondaryButton(
        text = stringResource(label),
        onClick = { asked = true },
        modifier = Modifier.fillMaxWidth(),
    )
    if (asked) {
        CoineProConfirmDialog(
            title = stringResource(R.string.connections_disconnect_title),
            message = stringResource(R.string.connections_disconnect_body),
            confirmLabel = stringResource(label),
            dismissLabel = stringResource(R.string.connections_keep),
            destructive = true,
            onConfirm = {
                asked = false
                onDisconnect()
            },
            onDismiss = { asked = false },
        )
    }
}
