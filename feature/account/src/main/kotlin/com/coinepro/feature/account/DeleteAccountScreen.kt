package com.coinepro.feature.account

import com.coinepro.core.common.BrandConfig
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
    /**
     * Opens the privacy policy in the app.
     *
     * The two deletion buttons below are deliberately **not** given the same treatment: Google Play
     * requires a deletion route reachable without the app, and somebody choosing to finish an
     * irreversible act at a desk has asked for the web. The policy is reading, not an act.
     */
    onOpenPolicy: (() -> Unit)? = null,
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
            onClick = { if (onOpenPolicy != null) onOpenPolicy() else context.open(POLICY_URL) },
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
 *
 * **The address itself is never on screen, and the second button is why it does not need to be.**
 * Two readers genuinely need the address rather than the page: somebody who would rather finish an
 * irreversible thing at a desk than on a phone, and a handset with no browser at all, where a
 * button that opens nothing is the whole route failing in silence. Neither of them needs it printed
 * in the body text, where it is read by everybody and used by nobody. It is handed over on request
 * — the copy button — and on the one failure that leaves no other way through.
 */
@Composable
private fun OutOfAppRoute() = ProvidePageAccent(PageAccent.BRAND) {
    val context = LocalContext.current
    // The clip's own name, which some clipboard managers show in their history. The card's title
    // is already the sentence that names what the address is for.
    val clipLabel = stringResource(R.string.delete_account_web_title)
    // Saved rather than remembered: this line is the only confirmation a copy happened — Android
    // stopped showing its own in 13 — and a reader who rotates the phone to read it would come
    // back to a card that says nothing and press the button again.
    var copied by rememberSaveable { mutableStateOf(false) }
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
                // The copy runs only where the page could not be opened. A device that has a
                // browser has no use for the address on its clipboard, and overwriting whatever the
                // reader had copied — an exchange UID, a wallet address — is not a favour.
                onClick = {
                    if (!context.open(DELETION_URL)) copied = context.copyLink(clipLabel, DELETION_URL)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            CoineProSecondaryButton(
                text = stringResource(R.string.delete_account_web_copy),
                onClick = { copied = context.copyLink(clipLabel, DELETION_URL) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (copied) {
                Text(
                    text = stringResource(R.string.delete_account_web_copied),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
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
 * Opens a link. False where the device has no browser.
 *
 * Rather than crashing: a device with no activity for `ACTION_VIEW` is unusual but real (a locked
 * kiosk build). It reports the failure rather than swallowing it because the caller has an answer
 * for it — the address goes to the clipboard, which is the only route left on a handset that
 * cannot open a page. The previous version of this comment said the page was still reachable
 * "by typing the address, which is printed above"; the address is not printed anywhere on this
 * screen, so that sentence described a way out that did not exist.
 */
private fun Context.open(url: String): Boolean = try {
    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/**
 * Puts the address on the clipboard, and says whether it got there.
 *
 * The answer is not ceremony. The card prints "copied" only on a true, because a line that says so
 * whether or not the system took the clip sends somebody to a computer with an empty paste buffer
 * and nothing to type. `setPrimaryClip` throws on OEM builds that police the clipboard, and the
 * service is absent altogether in a context that has none.
 */
private fun Context.copyLink(label: String, url: String): Boolean = runCatching {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, url))
    clipboard != null
}.getOrDefault(false)

// Compiled in, and never rendered. Both pages are reached by the named buttons above; the strings
// themselves reach the reader only through the browser they open, or through the clipboard when
// they ask for the address.
private const val SITE = BrandConfig.LEGAL_BASE_URL
private const val DELETION_URL = "$SITE/delete-account/"
private const val POLICY_URL = "$SITE/privacy/"
