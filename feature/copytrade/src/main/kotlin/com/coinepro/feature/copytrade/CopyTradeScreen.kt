package com.coinepro.feature.copytrade

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.copytrade.CopyAccount
import com.coinepro.core.copytrade.CopyExecutionEvent
import com.coinepro.core.copytrade.CopyPosition
import com.coinepro.core.copytrade.CopyPreferences
import com.coinepro.core.copytrade.CopyTradeController
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSkeleton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What CoinePro-FX has where the other platform has order execution.
 *
 * The screen answers four questions in the order a reader asks them, and each one is a card:
 * is copying on, which account is it running on, what is open right now, and — the one nothing else
 * in the app could answer — why did the last signal not open on my account.
 *
 * That last card is the reason this screen exists rather than a switch tucked into settings. A copy
 * account that quietly takes none of the trades is indistinguishable from a quiet market from the
 * outside, and readers waited days on one before anybody could tell them the terminal had been
 * refusing every order for a volume the broker would not accept. The server writes that reason in
 * full, in Persian, including the broker's own return code; it is shown here exactly as sent.
 */
@Composable
fun CopyTradeScreen(controller: CopyTradeController) {
    LaunchedEffect(controller) { controller.refresh() }
    val state by controller.state.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
        contentPadding = PaddingValues(CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Stack),
    ) {
        item {
            Column(
                modifier = Modifier.padding(horizontal = CoineProSpacing.Half),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.copy_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = CoineProColors.TextPrimary,
                )
                Text(
                    text = stringResource(R.string.copy_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }
        }

        when {
            state.unsupported -> item { Absent() }

            // Not a failure, and not drawn as one. There is a subscription behind this and the
            // server's own wording says what is missing better than anything local could.
            state.membershipRequired -> item {
                Notice(
                    state.membershipMessage ?: stringResource(R.string.copy_membership_default),
                    CoineProColors.Accent,
                )
            }

            state.status == null && state.loading -> item { LoadingCard() }

            state.status == null -> item {
                Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf)) {
                    state.error?.let { Notice(it, CoineProColors.Sell) }
                    CoineProPrimaryButton(
                        text = stringResource(R.string.copy_retry),
                        onClick = controller::refresh,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            else -> {
                val status = state.status!!
                // Above everything: while this is true, nothing else on the screen describes the
                // reader's account, and the switch below would be reporting a copy that cannot run.
                status.slotState?.let { slot ->
                    item { Notice(slot.message ?: slot.state, CoineProColors.Warning) }
                }
                if (status.accountMismatch) {
                    item { MismatchCard(status.liveAccount) }
                }
                state.error?.let { item { Notice(it, CoineProColors.Sell) } }

                item {
                    SwitchCard(
                        preferences = status.preferences,
                        hasAccount = status.account != null,
                        saving = state.saving,
                        onChange = controller::setEnabled,
                    )
                }
                item {
                    AccountCard(
                        account = status.account,
                        saving = state.saving,
                        onLink = controller::linkAccount,
                        onUnlink = controller::unlinkAccount,
                    )
                }
                if (status.account != null) {
                    item { TermsCard(status.preferences) }
                }
                item {
                    PositionsCard(
                        title = stringResource(R.string.copy_mirrored_title),
                        empty = stringResource(R.string.copy_mirrored_empty),
                        // Withheld rather than shown as empty: the server hides them here because
                        // they belong to whatever account the terminal is really on.
                        positions = if (status.accountMismatch) emptyList() else status.mirrored,
                    )
                }
                item {
                    PositionsCard(
                        title = stringResource(R.string.copy_master_title),
                        empty = stringResource(R.string.copy_master_empty),
                        positions = status.master.positions,
                    )
                }
                item { EventsCard(status.events) }
            }
        }
    }
}

/* ----------------------------------------------------------------------- cards */

@Composable
private fun SwitchCard(
    preferences: CopyPreferences,
    hasAccount: Boolean,
    saving: Boolean,
    onChange: (Boolean) -> Unit,
) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.copy_switch_title),
                style = MaterialTheme.typography.titleMedium,
                color = CoineProColors.TextPrimary,
            )
            Switch(
                checked = preferences.enabled,
                onCheckedChange = onChange,
                // Held while a write is in flight rather than allowed to bounce back and forth: an
                // off→on transition resets the server's signal baseline, so a switch that flickered
                // twice would not be a cosmetic problem.
                enabled = hasAccount && !saving,
            )
        }
        Text(
            text = stringResource(
                if (preferences.enabled) R.string.copy_switch_on else R.string.copy_switch_off,
            ),
            modifier = Modifier.padding(top = CoineProSpacing.One),
            style = MaterialTheme.typography.bodyMedium,
            color = if (preferences.enabled) CoineProColors.Buy else CoineProColors.TextSecondary,
        )
        Text(
            text = stringResource(
                if (hasAccount) R.string.copy_switch_note else R.string.copy_switch_needs_account,
            ),
            modifier = Modifier.padding(top = CoineProSpacing.One),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

@Composable
private fun AccountCard(
    account: CopyAccount?,
    saving: Boolean,
    onLink: (String, String, String, String) -> Unit,
    onUnlink: () -> Unit,
) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        if (account == null) {
            LinkForm(saving = saving, onLink = onLink)
        } else {
            LinkedAccount(account = account, onUnlink = onUnlink)
        }
    }
}

@Composable
private fun LinkForm(saving: Boolean, onLink: (String, String, String, String) -> Unit) {
    var broker by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Text(
        text = stringResource(R.string.copy_account_none_title),
        style = MaterialTheme.typography.titleMedium,
        color = CoineProColors.TextPrimary,
    )
    Text(
        text = stringResource(R.string.copy_account_none_body),
        modifier = Modifier.padding(top = CoineProSpacing.One),
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.TextMuted,
    )
    // Warning-coloured and above the fields, not muted below them. What this form asks for is a
    // live trading password, and the Investor password readers reach for instead does not work —
    // saying so afterwards would be saying it too late.
    Text(
        text = stringResource(R.string.copy_password_warning),
        modifier = Modifier.padding(top = CoineProSpacing.One),
        style = MaterialTheme.typography.bodyMedium,
        color = CoineProColors.Warning,
    )

    Column(
        modifier = Modifier.padding(top = CoineProSpacing.OneHalf),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        CoineProTextField(broker, { broker = it }, stringResource(R.string.copy_broker), Modifier.fillMaxWidth())
        CoineProTextField(server, { server = it }, stringResource(R.string.copy_server), Modifier.fillMaxWidth())
        CoineProTextField(
            value = login,
            onValueChange = { login = it },
            label = stringResource(R.string.copy_login),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        CoineProTextField(
            value = password,
            onValueChange = { password = it },
            label = stringResource(R.string.copy_password),
            modifier = Modifier.fillMaxWidth(),
            secret = true,
        )
        CoineProPrimaryButton(
            text = stringResource(if (saving) R.string.copy_linking else R.string.copy_link),
            onClick = {
                onLink(broker.trim(), server.trim(), login.trim(), password)
                // Cleared the moment it is handed over: nothing keeps a trading password in
                // composition state a second longer than the request needs it.
                password = ""
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !saving && broker.isNotBlank() && server.isNotBlank() &&
                login.isNotBlank() && password.isNotBlank(),
        )
    }
}

@Composable
private fun LinkedAccount(account: CopyAccount, onUnlink: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.copy_account_title),
            style = MaterialTheme.typography.titleMedium,
            color = CoineProColors.TextPrimary,
        )
        // Live rather than the stored status column: a terminal that is checking in proves more
        // than a status word written when the account was last touched.
        Health(alive = account.alive, status = account.status)
    }

    Column(
        modifier = Modifier.padding(top = CoineProSpacing.OneHalf),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        account.broker?.let { Detail(stringResource(R.string.copy_broker), it) }
        account.server?.let { Detail(stringResource(R.string.copy_server), BidiText.isolateLtr(it)) }
        account.loginMasked?.let {
            Detail(stringResource(R.string.copy_login), BidiText.isolateLtr(it))
        }

        val currency = account.currency ?: ""
        account.balance?.let {
            Detail(stringResource(R.string.copy_balance), amount(it, currency))
        }
        account.equity?.let {
            Detail(stringResource(R.string.copy_equity), amount(it, currency))
        }
        account.floatingPnl?.let {
            Detail(
                label = stringResource(R.string.copy_floating),
                value = amount(it, currency, signed = true),
                accent = if (it >= 0) CoineProColors.Buy else CoineProColors.Sell,
            )
        }
        account.marginLevel?.let {
            Detail(
                stringResource(R.string.copy_margin_level),
                BidiText.isolateLtr("${MarketNumberFormatter.price(it, decimals = 0)}%"),
            )
        }
        Detail(
            stringResource(R.string.copy_open_count),
            MarketNumberFormatter.price(account.openCount.toDouble(), decimals = 0),
        )
        account.lastSeen?.let {
            Detail(stringResource(R.string.copy_last_seen), BidiText.isolateLtr(stamp(it)))
        }
    }

    // The server's own explanation of an unhealthy link, shown as written — only the broker knows
    // why it refused, and the app never saw the refusal.
    account.lastError?.let {
        Notice(it, CoineProColors.Sell, Modifier.padding(top = CoineProSpacing.OneHalf))
    }

    // Said only when it changes what the figures above mean. A terminal that is reporting needs no
    // announcement; one that has gone quiet turns every number on this card into history.
    if (!account.alive) {
        Notice(
            message = stringResource(
                if (account.lastSeen == null) R.string.copy_terminal_never else R.string.copy_terminal_quiet,
            ),
            accent = CoineProColors.Warning,
            modifier = Modifier.padding(top = CoineProSpacing.OneHalf),
        )
    }

    CoineProSecondaryButton(
        text = stringResource(R.string.copy_unlink),
        onClick = onUnlink,
        modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.OneHalf),
    )
}

/**
 * The settings the copy is running under, read-only.
 *
 * Read-only on purpose. These are risk parameters whose bounds and interactions are explained
 * beside them in the web panel; a lot-size stepper on a phone with none of that context is a dial
 * without a gauge. Stating the terms is worth doing anyway — a reader who does not know their own
 * risk setting cannot judge the positions above.
 */
@Composable
private fun TermsCard(preferences: CopyPreferences) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.copy_terms_title),
            style = MaterialTheme.typography.titleSmall,
            color = CoineProColors.TextPrimary,
        )
        Column(
            modifier = Modifier.padding(top = CoineProSpacing.OneHalf),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            preferences.riskMode?.let { Detail(stringResource(R.string.copy_risk_mode), it.riskModeLabel()) }
            preferences.riskValue?.let {
                Detail(stringResource(R.string.copy_risk_value), MarketNumberFormatter.price(it))
            }
            preferences.maxLot?.let {
                Detail(stringResource(R.string.copy_max_lot), MarketNumberFormatter.price(it))
            }
            preferences.maxOpenTrades?.let {
                Detail(
                    stringResource(R.string.copy_max_open),
                    MarketNumberFormatter.price(it.toDouble(), decimals = 0),
                )
            }
            preferences.maxDailyLossPercent?.let {
                Detail(
                    stringResource(R.string.copy_daily_loss),
                    BidiText.isolateLtr("${MarketNumberFormatter.price(it, decimals = 0)}%"),
                )
            }
            Detail(
                stringResource(R.string.copy_stop_targets),
                stringResource(
                    if (preferences.copyStopAndTargets) {
                        R.string.copy_stop_targets_on
                    } else {
                        R.string.copy_stop_targets_off
                    },
                ),
            )
            Detail(
                label = stringResource(R.string.copy_symbols),
                value = preferences.symbols.takeIf { it.isNotEmpty() }
                    ?.joinToString("، ") { BidiText.isolateLtr(it) }
                    ?: stringResource(R.string.copy_symbols_all),
            )
        }
        Text(
            text = stringResource(R.string.copy_terms_note),
            modifier = Modifier.padding(top = CoineProSpacing.One),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

@Composable
private fun PositionsCard(title: String, empty: String, positions: List<CopyPosition>) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = CoineProColors.TextPrimary)
        if (positions.isEmpty()) {
            Text(
                text = empty,
                modifier = Modifier.padding(top = CoineProSpacing.One),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )
            return@CoineProCard
        }
        Column(
            modifier = Modifier.padding(top = CoineProSpacing.OneHalf),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            positions.forEachIndexed { index, position ->
                if (index > 0) HorizontalDivider(color = CoineProColors.Border)
                PositionRow(position)
            }
        }
    }
}

@Composable
private fun PositionRow(position: CopyPosition) {
    val up = position.profit >= 0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = BidiText.isolateLtr(position.symbol),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = MarketNumberFormatter.money(position.profit, signed = true),
                style = MaterialTheme.typography.titleSmall,
                color = if (up) CoineProColors.Buy else CoineProColors.Sell,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // The broker's own word for the side, not translated: only it knows what it opened.
            Text(
                text = BidiText.isolateLtr(position.direction.uppercase()),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.TextSecondary,
            )
            Text(
                text = "${stringResource(R.string.copy_lots)} ${MarketNumberFormatter.price(position.lots)}",
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        position.stopLoss?.let {
            Text(
                text = "${stringResource(R.string.copy_stop)} ${MarketNumberFormatter.price(it)}",
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        position.signalId?.let {
            Text(
                text = stringResource(
                    R.string.copy_from_signal,
                    MarketNumberFormatter.price(it.toDouble(), decimals = 0),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
    }
}

@Composable
private fun EventsCard(events: List<CopyExecutionEvent>) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.copy_events_title),
            style = MaterialTheme.typography.titleSmall,
            color = CoineProColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.copy_events_note),
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
        if (events.isEmpty()) {
            Text(
                text = stringResource(R.string.copy_events_empty),
                modifier = Modifier.padding(top = CoineProSpacing.One),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )
            return@CoineProCard
        }
        Column(
            modifier = Modifier.padding(top = CoineProSpacing.OneHalf),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            events.forEachIndexed { index, event ->
                if (index > 0) HorizontalDivider(color = CoineProColors.Border)
                EventRow(event)
            }
        }
    }
}

@Composable
private fun EventRow(event: CopyExecutionEvent) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = event.symbol?.let(BidiText::isolateLtr).orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = CoineProColors.TextSecondary,
            )
            event.at?.let {
                Text(
                    text = BidiText.isolateLtr(stamp(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
        // Server text, verbatim. It already carries the technical cause and the broker's return
        // code, assembled from a refusal the app never saw; rewording it would lose exactly the
        // part support needs.
        Text(
            text = event.message,
            style = MaterialTheme.typography.bodyMedium,
            color = when (event.outcome) {
                "ok", "filled" -> CoineProColors.TextPrimary
                else -> CoineProColors.Warning
            },
        )
    }
}

@Composable
private fun MismatchCard(liveAccount: String?) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.copy_mismatch_title),
            style = MaterialTheme.typography.titleSmall,
            color = CoineProColors.Sell,
        )
        Text(
            text = liveAccount
                ?.let { stringResource(R.string.copy_mismatch_body, BidiText.isolateLtr(it)) }
                ?: stringResource(R.string.copy_mismatch_body_unknown),
            modifier = Modifier.padding(top = CoineProSpacing.One),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
    }
}

@Composable
private fun Absent() {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.copy_unavailable_title),
            style = MaterialTheme.typography.titleSmall,
            color = CoineProColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.copy_unavailable_body),
            modifier = Modifier.padding(top = CoineProSpacing.One),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
    }
}

/* ----------------------------------------------------------------------- parts */

@Composable
private fun Health(alive: Boolean, status: String?) {
    val colour = if (alive) CoineProColors.Buy else CoineProColors.Warning
    Row(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(colour, CoineProPillShape))
        Text(
            // The server's own status word, with its underscores opened out. Not translated: it is
            // the term support will ask the reader to read back.
            text = status?.replace('_', ' ').orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = colour,
        )
    }
}

@Composable
private fun Detail(label: String, value: String, accent: Color = CoineProColors.TextPrimary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = accent)
    }
}

@Composable
private fun Notice(message: String, accent: Color, modifier: Modifier = Modifier) {
    Text(
        text = message,
        modifier = modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.10f), MaterialTheme.shapes.medium)
            .padding(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.OneHalf),
        style = MaterialTheme.typography.bodySmall,
        color = accent,
    )
}

@Composable
private fun LoadingCard() {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CoineProSkeleton(Modifier.fillMaxWidth(0.4f), height = 20.dp)
        Row(Modifier.padding(top = CoineProSpacing.OneHalf)) {
            CoineProSkeleton(Modifier.fillMaxWidth(), height = 14.dp)
        }
    }
}

@Composable
private fun String.riskModeLabel(): String = when (this) {
    "proportional" -> stringResource(R.string.copy_risk_mode_proportional)
    "fixed_lot" -> stringResource(R.string.copy_risk_mode_fixed_lot)
    "risk_percent" -> stringResource(R.string.copy_risk_mode_risk_percent)
    // An unknown mode is shown as the server named it rather than hidden: a setting nobody can see
    // is a setting nobody can question.
    else -> BidiText.isolateLtr(this)
}

private fun amount(value: Double, currency: String, signed: Boolean = false): String =
    MarketNumberFormatter.money(
        value = value,
        currencySymbol = if (currency.isBlank()) "$" else "$currency ",
        signed = signed,
    )

private val stampFormat = DateTimeFormatter.ofPattern("MM-dd · HH:mm")

/**
 * Absolute, not relative.
 *
 * "Two hours ago" is friendlier and, on this screen, worse: a reader comparing this against their
 * terminal's own log needs a time they can find in it.
 */
private fun stamp(at: Instant): String =
    stampFormat.format(at.atZone(ZoneId.systemDefault()))
