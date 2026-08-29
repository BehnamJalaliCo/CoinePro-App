package com.coinepro.feature.account

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.account.AccountController
import com.coinepro.core.account.AccountDeletion
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.designsystem.PageAccent
import com.coinepro.core.designsystem.ProvidePageAccent
import com.coinepro.core.designsystem.CoineProThinkingDots

/**
 * Deleting the account.
 *
 * Three things this screen does that a confirmation dialog would not, and each is the reason it is
 * a screen:
 *
 * It **says what goes and what stays** before asking. "Are you sure?" is not informed consent; a
 * reader deleting an account is entitled to know that their trading records survive anonymised for
 * as long as the law says, and that this does not close their exchange account — which is the one
 * thing people assume it does.
 *
 * It **asks for a typed word**, not a second tap. The action is irreversible and the button sits
 * one tap from a menu; a mis-tap should not be able to reach it. Typing is the cheapest barrier
 * that cannot be crossed by accident, and it is the barrier every product that gets this right
 * uses.
 *
 * And it **has an answer for a server that cannot do it yet**. Neither deployment serves the route
 * at the time of writing, so where the capability flag is off the screen shows the published
 * out-of-app route instead of a button that would fail. That is not a placeholder — it is the route
 * Google Play requires be published in any case, and it works today.
 */
@Composable
fun DeleteAccountScreen(
    controller: AccountController,
    /** From `auth/methods`. False shows the out-of-app route rather than a button. */
    supported: Boolean,
    onDeleted: () -> Unit,
) {
    val state by controller.deletion.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var typed by rememberSaveable { mutableStateOf("") }

    // The outcome on screen belongs to this visit. Coming back to last week's refusal would read as
    // a fresh one, and coming back to a stale "done" would sign the reader out of a live account.
    LaunchedEffect(controller) { controller.clearDeletion() }

    LaunchedEffect(state) {
        if (state is AccountDeletion.Done) onDeleted()
    }

    val confirmWord = stringResource(R.string.delete_account_confirm_word)
    val deleting = state is AccountDeletion.Deleting
    // Folded and trimmed, so a trailing space from a keyboard's autocomplete does not read as a
    // failure to follow the instruction.
    val armed = typed.trim() == confirmWord && !deleting

    // The one screen in the app that declares a consequence rather than a domain. Everything
    // inside — the delete button above all — takes the refusal red, so the action never looks like
    // the gold "continue" it sits one tap away from.
    ProvidePageAccent(PageAccent.DESTRUCTIVE) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .verticalScroll(rememberScrollState())
            .padding(CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
    ) {
        Text(
            text = stringResource(R.string.delete_account_title),
            style = MaterialTheme.typography.titleLarge,
            color = CoineProColors.TextPrimary,
        )
        Text(
            text = stringResource(R.string.delete_account_irreversible),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.Sell,
        )

        Bullets(
            title = stringResource(R.string.delete_account_removed_title),
            lines = listOf(
                R.string.delete_account_removed_identity,
                    // Named explicitly because it is the one a reader would not predict. Both
                    // servers delete the academy identity along with the account, and somebody who
                    // asked to be forgotten and could still recover an academy password with the
                    // same e-mail has not been forgotten.
                    R.string.delete_account_removed_academy,
                R.string.delete_account_removed_exchange,
                R.string.delete_account_removed_notifications,
                R.string.delete_account_removed_history,
            ),
        )
        Bullets(
            title = stringResource(R.string.delete_account_kept_title),
            lines = listOf(
                R.string.delete_account_kept_trades,
                R.string.delete_account_kept_security,
            ),
            note = stringResource(R.string.delete_account_kept_note),
        )
        Bullets(
            title = stringResource(R.string.delete_account_not_removed_title),
            lines = listOf(R.string.delete_account_not_removed_exchange),
        )
        // The fourth card, and the one this screen was missing.
        //
        // Deleting the account deletes what is on a server. A reader's journal, their watchlists,
        // their chart layouts, their templates and their on-device price alerts have never been on
        // one — neither backend has a route for any of them — so a DELETE cannot touch them, and
        // they are still sitting on the phone afterwards. Both readings of that are wrong to leave
        // to guesswork: somebody asking to be forgotten deserves to know a year of their trading
        // diary is still on the handset, and somebody who has kept that diary deserves to know it
        // is not about to be thrown away. Saying which is which is the difference between a
        // consent screen and a confirmation.
        Bullets(
            title = stringResource(R.string.delete_account_local_title),
            lines = listOf(
                R.string.delete_account_local_journal,
                R.string.delete_account_local_workspace,
                R.string.delete_account_local_alerts,
            ),
            note = stringResource(R.string.delete_account_local_note),
        )

        if (supported) {
            CoineProCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                    Text(
                        text = stringResource(R.string.delete_account_type_to_confirm, confirmWord),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.TextSecondary,
                    )
                    CoineProTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = confirmWord,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !deleting,
                    )
                    if (deleting) {
                        CoineProThinkingDots()
                    } else {
                        CoineProPrimaryButton(
                            text = stringResource(R.string.delete_account_action),
                            onClick = controller::deleteAccount,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = armed,
                        )
                    }
                }
            }
        } else {
            OutOfAppRoute()
        }

        when (val current = state) {
            // Not an error message. The route does not exist on this deployment, which is a fact
            // about the server and not a refusal of the reader, so it hands over the other way.
            AccountDeletion.Unsupported -> {
                Text(
                    text = stringResource(R.string.delete_account_unsupported),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
                OutOfAppRoute()
            }
            is AccountDeletion.Refused -> Text(
                // The server's own wording where it gave one. A generic message would hide the one
                // case a reader can act on — an open position, an unsettled balance.
                text = current.reason ?: stringResource(R.string.delete_account_refused),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.Sell,
            )
            else -> Unit
        }

        Text(
            text = stringResource(R.string.delete_account_policy_link),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
        CoineProSecondaryButton(
            text = stringResource(R.string.delete_account_open_policy),
            onClick = { context.open(POLICY_URL) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    }
}

/**
 * The published deletion page.
 *
 * On [PageAccent.BRAND] rather than the screen's red: opening a web page is not the destructive
 * act, it is the way to reach it. Painting it red would put two identical-looking buttons on one
 * screen, one of which deletes an account and one of which opens a browser.
 */
@Composable
private fun OutOfAppRoute() = ProvidePageAccent(PageAccent.BRAND) {
    val context = LocalContext.current
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Text(
                text = stringResource(R.string.delete_account_web_title),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.delete_account_web_body),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )
            CoineProPrimaryButton(
                text = stringResource(R.string.delete_account_web_action),
                onClick = { context.open(DELETION_URL) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Bullets(title: String, lines: List<Int>, note: String? = null) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            lines.forEach { line ->
                // The bullet is written into the string rather than drawn beside it: a Compose
                // bullet paints at a fixed left offset, which is the wrong side of an RTL line.
                Text(
                    text = "• " + stringResource(line),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }
            note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
    }
}

/**
 * Opens a link, and does nothing when the device has no browser.
 *
 * Rather than crashing: a device with no activity for `ACTION_VIEW` is unusual but real (a locked
 * kiosk build), and the whole page is reachable by typing the address, which is printed above.
 */
private fun android.content.Context.open(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Unit
    }
}

private const val SITE = "https://behnamjalalico.github.io/CoinePro-App"
private const val DELETION_URL = "$SITE/delete-account/"
private const val POLICY_URL = "$SITE/privacy/"
