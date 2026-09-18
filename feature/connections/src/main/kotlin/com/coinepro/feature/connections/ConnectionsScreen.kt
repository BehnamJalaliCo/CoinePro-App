package com.coinepro.feature.connections

import androidx.annotation.StringRes
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.coinepro.core.common.VenueStatusPersian
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProInfoTip
import com.coinepro.core.designsystem.noteVisible
import com.coinepro.core.designsystem.CoineProConfirmDialog
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProSkeleton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.CoineProTeachingStrip
import com.coinepro.core.designsystem.TeachingSurface
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.execution.LbankPermission
import com.coinepro.core.execution.VenueConnection
import com.coinepro.core.model.MarketPlatform

/**
 * Where a reader links the account their orders are placed through.
 *
 * ### One venue, because the product has one
 *
 * TradeYar executes through **LBank**, so what this screen wants is an exchange key pair the reader
 * mints at the exchange: a header carrying the server's status word, the details the server knows,
 * the destructive action behind a question, and the form last.
 *
 * ### The MetaTrader card, and why it is gone (run Ψ)
 *
 * There were two of them, one after the other. The first sat over `ExecutionController.connectMt5`,
 * which throws `ExecutionUnsupportedException` unconditionally because no backend ever served that
 * route — a form that could only ever refuse what it asked for. The second was real and was wired
 * to `user/account/link`, `DELETE user/account` and `GET user/copy-status`: the **copy-trading**
 * account link, seen from here.
 *
 * The owner's decision is that the product has no copy trading. On the forex side this app is a
 * gold signal and a chart, and nothing mirrors anybody's orders onto a MetaTrader account — so
 * there is no broker account to link, the card is gone with the feature, and the twenty
 * `connections_mt5_*` strings went with it. CoinePro-FX now takes the honest surface this file
 * already had for a platform with nothing to connect: [ConnectionsSurface.LINKED_ELSEWHERE].
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
    val surface = connectionsSurface(platform, state.unsupported)

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
        contentPadding = PaddingValues(
            horizontal = CoineProSpacing.Gutter,
            vertical = CoineProSpacing.Gutter,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Stack),
    ) {
        item { CoineProTeachingStrip(TeachingSurface.CONNECTIONS, gutter = false) }
        item {
            Column(
                modifier = Modifier.padding(horizontal = CoineProSpacing.Half),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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

        // A first read, before anything is known.
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

        // Server wording, shown verbatim: the client did not diagnose these and must not reword
        // them.
        state.error?.let { item { Caution(it, CoineProColors.Sell) } }
        state.message?.let { item { Caution(it, CoineProColors.Buy) } }

        when (surface) {
            ConnectionsSurface.EXCHANGE_KEY -> {
                item {
                    LbankCard(
                        connection = state.lbank,
                        onConnect = controller::connectLbank,
                        onDisconnect = controller::disconnectLbank,
                    )
                }
                // Under the card the reader came for, because the question it answers is the one
                // they ask after finding only an exchange here: the forex side of this app is a
                // gold signal and a chart, and there is no account on it for a key to reach.
                item {
                    Caution(
                        stringResource(R.string.connections_forex_elsewhere),
                        CoineProColors.TextSecondary,
                    )
                }
            }

            ConnectionsSurface.LINKED_ELSEWHERE -> item { ElsewhereCard() }
        }
    }
}

/**
 * The two things this screen can be, and there is deliberately no third.
 *
 * Every surface named here is one this app can also *complete*: a form is offered only where the
 * route behind it exists on the platform in hand. That rule is what the enum is for — the MetaTrader
 * card that used to sit here was a surface with no server behind it, and it survived a year because
 * nothing named the set. The second MetaTrader surface, `MT5_COPY_LINK`, had a real server and went
 * for a different reason: the feature it linked an account for no longer exists (run Ψ).
 */
internal enum class ConnectionsSurface {
    /** LBank, on TradeYar: a key pair the reader mints at the exchange. */
    EXCHANGE_KEY,

    /** No venue here, and no honest form to draw. */
    LINKED_ELSEWHERE,
}

/**
 * Which surface a platform gets, and why absence is checked before anything else.
 *
 * CoinePro-FX has no venue-connection route, so its execution gateway refuses that call rather than
 * answering it emptily — which reaches the controller as `unsupported`. It now has nothing else to
 * offer either: with copy trading gone there is no broker account to link from this app, so the
 * forex side is [ConnectionsSurface.LINKED_ELSEWHERE] by the platform rather than by a stage.
 */
internal fun connectionsSurface(
    platform: MarketPlatform,
    unsupported: Boolean,
): ConnectionsSurface = when {
    platform == MarketPlatform.COINEPRO_FX -> ConnectionsSurface.LINKED_ELSEWHERE
    unsupported -> ConnectionsSurface.LINKED_ELSEWHERE
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
        VenueHeader(
            name = "LBank",
            status = connection.headline(),
            colour = connection.tone(),
        )
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
                    Detail(stringResource(R.string.connections_permission), it.label())
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

/* ------------------------------------------------------------------ status */

/**
 * The word beside the dot, for the exchange.
 *
 * Server status text wins when it has any, since only the venue knows why it is not connected —
 * and it is put into Persian on the way through. Leaving it alone was the first rule and it put
 * «awaiting provider confirmation», in English, at the top of a Persian reader's own connection
 * card, beside the coloured dot, in the one place that says whether their account works. See
 * [VenueStatusPersian]: a word the table knows is translated, a word it does not know still
 * reaches the reader as the venue wrote it.
 */
@Composable
private fun VenueConnection?.headline(): String {
    val raw = this?.status?.takeIf { it.isNotBlank() && !connected }
    return raw?.let(VenueStatusPersian::label) ?: stringResource(
        when {
            this == null -> R.string.connections_status_not_configured
            connected -> R.string.connections_status_connected
            else -> R.string.connections_status_configured
        },
    )
}

@Composable
private fun VenueConnection?.tone(): Color = when {
    this?.connected == true -> CoineProColors.Buy
    this == null -> CoineProColors.TextMuted
    else -> CoineProColors.Warning
}


/* ------------------------------------------------------------------ parts */

/**
 * A venue's name and its state, on one line.
 *
 * Takes the words already resolved rather than a connection object, because the two venues on this
 * screen are different types with the same header — and a header that switched on which one it was
 * given is how the two cards would drift apart.
 */
@Composable
private fun VenueHeader(name: String, status: String, colour: Color) {
    val label = stringResource(R.string.connections_status_of, name, status)
    Row(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.titleMedium, color = CoineProColors.TextPrimary)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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

/** A caution by id: the note policy decides whether it is the plate or an ⓘ beside the card. */
@Composable
private fun Caution(@StringRes id: Int, accent: Color = CoineProColors.Warning) {
    if (noteVisible(id)) {
        Caution(stringResource(id), accent)
    } else {
        CoineProInfoTip(
            stringResource(id),
            modifier = Modifier.padding(horizontal = CoineProSpacing.Two),
            tint = accent,
        )
    }
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
