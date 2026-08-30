package com.coinepro.feature.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextField
import com.coinepro.core.diagnostics.AdminGateState
import com.coinepro.core.diagnostics.HubActions

/**
 * The door, drawn.
 *
 * ### Why the panel has one now
 *
 * Five taps on the version number used to open this panel outright. That was safe as long as the
 * panel was a report; it stopped being safe the moment it became a set of levers — it signs both
 * platforms out, restarts the feed, drops caches, re-registers the push token, and writes a file
 * describing the install. A handset lent to somebody for a minute is all it took.
 *
 * The taps are still how the panel is *found*, which keeps it invisible to an ordinary reader. They
 * are no longer how it is *entered*.
 *
 * ### The three states this screen has to tell apart
 *
 * A wrong password, a gate closed by too many attempts, and a build that was never given a
 * credential at all. They look identical to a reader and have completely different fixes, so each
 * gets its own sentence — the third especially, because an operator hunting for a typo in a build
 * with no key in it is an operator wasting an afternoon.
 *
 * ### What this screen does not keep
 *
 * The typed password lives in a `remember` that is not saved, is cleared the moment it has been
 * submitted, and is never logged. [CoineProTextField]'s `secret` masks it and marks it for the
 * password manager rather than for autofill's general store.
 */
@Composable
internal fun AdminLockScreen(gate: AdminGateState, actions: HubActions, nowMillis: Long) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val lockedMinutes = (gate.remainingLockMillis(nowMillis) / 60_000L + 1).toInt()
    val locked = gate.lockedAt(nowMillis)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .padding(CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.Center,
    ) {
        CoineProCard(modifier = Modifier.fillMaxWidth()) {
            Icon(
                painter = painterResource(CoineProIcons.Locked),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = CoineProColors.TextSecondary,
            )
            Spacer(Modifier.height(CoineProSpacing.One))
            Text(
                text = stringResource(R.string.admin_lock_title),
                style = MaterialTheme.typography.titleMedium,
                color = CoineProColors.TextPrimary,
            )
            Muted(stringResource(R.string.admin_lock_body))
            Spacer(Modifier.height(CoineProSpacing.Two))

            CoineProTextField(
                value = username,
                onValueChange = {
                    username = it
                    actions.onCredentialEdited()
                },
                label = stringResource(R.string.admin_lock_username),
                modifier = Modifier.fillMaxWidth(),
                enabled = gate.provisioned && !locked,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Next,
                ),
                autofill = ContentType.Username,
            )
            Spacer(Modifier.height(CoineProSpacing.One))
            CoineProTextField(
                value = password,
                onValueChange = {
                    password = it
                    actions.onCredentialEdited()
                },
                label = stringResource(R.string.admin_lock_password),
                modifier = Modifier.fillMaxWidth(),
                secret = true,
                enabled = gate.provisioned && !locked,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                isError = gate.refused,
                autofill = ContentType.Password,
            )

            // One message, and which of the three it is decides what an operator does next.
            when {
                locked -> Warning(
                    stringResource(R.string.admin_lock_locked, lockedMinutes.toPersianDigits()),
                )
                !gate.provisioned && gate.refused ->
                    Warning(stringResource(R.string.admin_lock_unprovisioned))
                gate.refused -> Warning(stringResource(R.string.admin_lock_refused))
                else -> Unit
            }

            Spacer(Modifier.height(CoineProSpacing.Two))
            CoineProPrimaryButton(
                text = stringResource(R.string.admin_lock_enter),
                onClick = {
                    // Cleared whichever way it went. A refused password left in the field is a
                    // password sitting legible on a screen somebody will hand back.
                    actions.onUnlock(username, password)
                    password = ""
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !locked && username.isNotBlank() && password.isNotBlank(),
            )
        }
    }
}

@Composable
private fun Warning(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = CoineProSpacing.One),
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.Sell,
        // Right, not End. This app's default locale is Persian and the message is Persian
        // prose; End would follow whatever direction the composition happens to be in.
        textAlign = TextAlign.Right,
    )
}
