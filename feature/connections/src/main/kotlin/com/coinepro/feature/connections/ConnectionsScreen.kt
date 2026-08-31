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
import com.coinepro.core.copytrade.CopyTradeController
import com.coinepro.core.copytrade.Mt5Link
import com.coinepro.core.copytrade.Mt5LinkStage
import com.coinepro.core.copytrade.toMt5Link
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
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
 * Where a reader links the account their signals will be traded through.
 *
 * ### Two platforms, two venues, one screen
 *
 * Each platform has exactly one, and they are not the same kind of thing. TradeYar executes through
 * **LBank**, so what it wants is an exchange key pair the reader mints at the exchange. CoinePro-FX
 * does not place orders from the app at all — it links a **MetaTrader 5 broker account** once and a
 * copy service trades it — so what it wants is a broker, a server, an account number and the
 * account's own password.
 *
 * Both are drawn by the same parts, in the same order, with the same rules: a header carrying the
 * server's status word, the details the server knows, the destructive action behind a question, and
 * the form last. A reader moving between platforms should recognise the screen, not relearn it.
 *
 * ### The MetaTrader form that used to be a lie, and the one that is not
 *
 * There was a MetaTrader 5 card here once, over `ExecutionController.connectMt5`, which throws
 * `ExecutionUnsupportedException` unconditionally on both platforms because no backend ever served
 * that route. It asked for a live trading password and could only ever refuse it.
 *
 * The card below is a different thing entirely and is wired to routes that exist: `POST
 * user/account/link`, `DELETE user/account`, and `GET user/copy-status` for the state — the same
 * three the web panel calls and the same three the copy-trading screen already used. Nothing here
 * invents an endpoint, and nothing here is drawn on a platform whose gateway refuses the call.
 *
 * ### Setup is not connection
 *
 * A distinction the product depends on and readers routinely miss: entering credentials is *setup*.
 * Only the backend can say a venue verified them, so nothing here turns green on a successful save.
 * On the forex side that is sharper still — a saved login means nothing until a terminal reports in
 * on that same account, which is why [Mt5LinkStage.PENDING] exists as its own state.
 */
@Composable
fun ConnectionsScreen(
    controller: ExecutionController,
    platform: MarketPlatform = MarketPlatform.TRADEYAR,
    /**
     * CoinePro-FX's account link, which is the copy-trading contract seen from here.
     *
     * Null where the host has not wired one. That is treated as absence rather than as an empty
     * form: a MetaTrader card with no controller behind it is the exact bug this file was once
     * rewritten to remove.
     */
    copyTrade: CopyTradeController? = null,
) {
    LaunchedEffect(controller) { controller.refreshConnections() }
    LaunchedEffect(copyTrade) { copyTrade?.refresh() }

    val state by controller.connections.collectAsStateWithLifecycle()
    val copyState = copyTrade?.state?.collectAsStateWithLifecycle()?.value
    val mt5 = copyState?.toMt5Link()
    val surface = connectionsSurface(platform, state.unsupported, mt5?.stage)
    val forex = surface == ConnectionsSurface.MT5_COPY_LINK

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
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.connections_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = CoineProColors.TextPrimary,
                )
                Text(
                    text = stringResource(
                        if (forex) R.string.connections_subtitle_mt5 else R.string.connections_subtitle,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }
        }

        item {
            Caution(
                stringResource(
                    if (forex) {
                        R.string.connections_setup_not_proof_mt5
                    } else {
                        R.string.connections_setup_not_proof
                    },
                ),
            )
        }

        // A first read, before anything is known. Only ever the read that belongs to the surface on
        // screen: the execution controller's load says nothing about a broker link.
        val reading = if (forex) {
            mt5 != null && mt5.loading && mt5.stage == Mt5LinkStage.NOT_LINKED
        } else {
            state.loading
        }
        if (reading) {
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
        // them. Each surface shows its own server's answer and never the other one's.
        val failure = if (forex) copyState?.error else state.error
        failure?.let { item { Caution(it, CoineProColors.Sell) } }
        if (!forex) {
            state.message?.let { item { Caution(it, CoineProColors.Buy) } }
        }

        when (surface) {
            ConnectionsSurface.MT5_COPY_LINK -> {
                item {
                    Mt5Card(
                        link = mt5 ?: Mt5Link(Mt5LinkStage.NOT_LINKED),
                        onLink = { broker, server, login, password ->
                            copyTrade?.linkAccount(broker, server, login, password)
                        },
                        onUnlink = { copyTrade?.unlinkAccount() },
                    )
                }
                // Under the card, because the question a reader asks after connecting here is what
                // the connection is actually for and where its result is shown.
                item {
                    Caution(
                        stringResource(R.string.connections_mt5_copy_note),
                        CoineProColors.TextSecondary,
                    )
                }
            }

            ConnectionsSurface.EXCHANGE_KEY -> {
                item {
                    LbankCard(
                        connection = state.lbank,
                        onConnect = controller::connectLbank,
                        onDisconnect = controller::disconnectLbank,
                    )
                }
                // Under the card the reader came for, because the question it answers is the one
                // they ask after finding only an exchange here: a forex account is a broker login
                // on the other platform, not a key, and there is no key on this screen that reaches
                // it.
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
 * The three things this screen can be, and there is deliberately no fourth.
 *
 * Every surface named here is one this app can also *complete*: a form is offered only where the
 * route behind it exists on the platform in hand. That rule is what the enum is for — the MetaTrader
 * card that used to sit here was a fourth surface with no server behind it, and it survived a year
 * because nothing named the set.
 */
internal enum class ConnectionsSurface {
    /** LBank, on TradeYar: a key pair the reader mints at the exchange. */
    EXCHANGE_KEY,

    /**
     * MetaTrader 5, on CoinePro-FX: a broker login, over the copy-trading account routes.
     *
     * The word "copy" is in the name because that is what the link is — `user/account/link` is the
     * copy service's, and the account it connects is traded by that service rather than by the
     * reader from this app.
     */
    MT5_COPY_LINK,

    /** No venue here, and no honest form to draw. */
    LINKED_ELSEWHERE,
}

/**
 * Which surface a platform gets, and why absence is checked before anything else.
 *
 * CoinePro-FX has no venue-connection route, so its execution gateway refuses that call rather than
 * answering it emptily — which reaches the controller as `unsupported`. That says nothing about the
 * broker link, so the forex branch is decided on the copy-trade stage instead, and only ever offers
 * the form when a controller is actually behind it: [stage] is null where the host wired none, and
 * [Mt5LinkStage.UNAVAILABLE] where that server has no copy routes either.
 */
internal fun connectionsSurface(
    platform: MarketPlatform,
    unsupported: Boolean,
    stage: Mt5LinkStage?,
): ConnectionsSurface = when {
    platform == MarketPlatform.COINEPRO_FX ->
        if (stage != null && stage != Mt5LinkStage.UNAVAILABLE) {
            ConnectionsSurface.MT5_COPY_LINK
        } else {
            ConnectionsSurface.LINKED_ELSEWHERE
        }

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

/**
 * CoinePro-FX's MetaTrader 5 account, in the shape the exchange card next door already established.
 *
 * The one place it departs from that shape is the subscription case. `user/copy-status` sits behind
 * the same VIP gate the signals do; `user/account/link` does not — it is gated on a verified email
 * and an accepted disclaimer. So a reader without a subscription can genuinely connect an account
 * and genuinely cannot be told what became of it, and the card says both rather than picking the
 * half that renders more tidily. Withholding the form would be inventing a gate the server has not
 * got; claiming a state would be inventing an answer it did not give.
 */
@Composable
private fun Mt5Card(
    link: Mt5Link,
    onLink: (String, String, String, String) -> Unit,
    onUnlink: () -> Unit,
) {
    // Everything but the password survives a rotation, for the reason the exchange card gives: a
    // trading password is not kept in composition state a second longer than the request needs it.
    var broker by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf("") }
    var login by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    // Only ever read where the server cannot answer for itself. Everywhere else the status is the
    // acknowledgement, and a local flag saying "sent" beside a server saying "pending" is noise.
    var sent by rememberSaveable { mutableStateOf(false) }

    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        VenueHeader(
            name = stringResource(R.string.connections_mt5_name),
            status = link.headline(),
            colour = link.tone(),
        )
        Text(
            text = stringResource(R.string.connections_mt5_body),
            modifier = Modifier.padding(top = CoineProSpacing.One),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
        // Warning-coloured and above the fields, not muted below them. What this form asks for is a
        // live trading password, and the Investor password readers reach for instead does not work
        // — saying so afterwards would be saying it too late.
        Text(
            text = stringResource(R.string.connections_mt5_password_warning),
            modifier = Modifier.padding(top = CoineProSpacing.One),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.Warning,
        )

        Column(
            modifier = Modifier.padding(top = CoineProSpacing.OneHalf),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        ) {
            if (link.linked) {
                link.broker?.let { Detail(stringResource(R.string.connections_mt5_broker), it) }
                link.server?.let {
                    Detail(stringResource(R.string.connections_mt5_server), BidiText.isolateLtr(it))
                }
                link.loginMasked?.let {
                    Detail(stringResource(R.string.connections_mt5_login), BidiText.isolateLtr(it))
                }
                // Above the server's own note, because it is the reason the rest of the card cannot
                // be trusted: while the terminal is on another account, nothing here describes the
                // account the reader linked.
                link.liveAccount?.let {
                    Caution(
                        stringResource(R.string.connections_mt5_mismatch, BidiText.isolateLtr(it)),
                        CoineProColors.Sell,
                    )
                }
                // The server's explanation, in Persian, as written. Only the broker knows why it
                // refused, and the app never saw the refusal.
                link.serverNote?.let { Caution(it, CoineProColors.Sell) }
                if (link.stage == Mt5LinkStage.PENDING) {
                    Caution(stringResource(R.string.connections_mt5_pending_note))
                }
            }

            if (link.stage == Mt5LinkStage.LOCKED) {
                Caution(
                    link.serverNote ?: stringResource(R.string.connections_mt5_locked_default),
                    CoineProColors.Accent,
                )
                Caution(
                    stringResource(R.string.connections_mt5_locked_note),
                    CoineProColors.TextSecondary,
                )
                if (sent) {
                    Caution(
                        stringResource(R.string.connections_mt5_sent),
                        CoineProColors.TextSecondary,
                    )
                }
            }

            if (link.canUnlink) {
                DisconnectButton(
                    label = R.string.connections_mt5_unlink,
                    onDisconnect = onUnlink,
                )
            }

            CoineProTextField(
                value = broker,
                onValueChange = { broker = it },
                label = stringResource(R.string.connections_mt5_broker),
                modifier = Modifier.fillMaxWidth(),
                enabled = !link.busy,
            )
            CoineProTextField(
                value = server,
                onValueChange = { server = it },
                label = stringResource(R.string.connections_mt5_server),
                modifier = Modifier.fillMaxWidth(),
                enabled = !link.busy,
            )
            CoineProTextField(
                value = login,
                onValueChange = { login = it },
                label = stringResource(R.string.connections_mt5_login),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !link.busy,
            )
            CoineProTextField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.connections_mt5_password),
                modifier = Modifier.fillMaxWidth(),
                secret = true,
                enabled = !link.busy,
            )
            Submit(
                text = stringResource(
                    when {
                        link.busy -> R.string.connections_mt5_linking
                        link.linked -> R.string.connections_mt5_relink
                        else -> R.string.connections_mt5_link
                    },
                ),
                enabled = !link.busy && broker.isNotBlank() && server.isNotBlank() &&
                    login.isNotBlank() && password.isNotBlank(),
            ) {
                onLink(broker.trim(), server.trim(), login.trim(), password)
                // Cleared the moment it is handed over. The other three are left in place: a
                // rejected login is usually one wrong field, and clearing all four would make the
                // reader retype a broker name to fix a typo in an account number.
                password = ""
                sent = true
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

/**
 * The word beside the dot, for MetaTrader — under the exchange card's rule, applied to a stage.
 *
 * The server's own word wins wherever the link is not proven connected, which is where it carries
 * anything worth reading: `login_failed` says more than "needs attention" ever could — and says it
 * in Persian, through the same table the exchange card uses.
 */
@Composable
private fun Mt5Link.headline(): String {
    val raw = serverStatus?.takeIf { it.isNotBlank() && stage != Mt5LinkStage.CONNECTED }
    return raw?.let(VenueStatusPersian::label) ?: stringResource(
        when (stage) {
            Mt5LinkStage.CONNECTED -> R.string.connections_status_connected
            Mt5LinkStage.PENDING -> R.string.connections_mt5_status_pending
            Mt5LinkStage.ATTENTION -> R.string.connections_mt5_status_attention
            Mt5LinkStage.LOCKED -> R.string.connections_mt5_status_locked
            Mt5LinkStage.NOT_LINKED, Mt5LinkStage.UNAVAILABLE ->
                R.string.connections_status_not_configured
        },
    )
}

@Composable
private fun Mt5Link.tone(): Color = when (stage) {
    Mt5LinkStage.CONNECTED -> CoineProColors.Buy
    Mt5LinkStage.PENDING -> CoineProColors.Warning
    Mt5LinkStage.ATTENTION -> CoineProColors.Sell
    Mt5LinkStage.LOCKED -> CoineProColors.Accent
    Mt5LinkStage.NOT_LINKED, Mt5LinkStage.UNAVAILABLE -> CoineProColors.TextMuted
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
