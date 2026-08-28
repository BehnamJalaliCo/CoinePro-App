package com.coinepro.app.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.coinepro.app.R
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSheet
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.security.LockCapability

/**
 * Where the reader turns the app lock on, and where the phone gets to explain itself.
 *
 * ### Most of this file is the three states that are not "ready"
 *
 * A switch alone would be right on a phone with a fingerprint enrolled and misleading on the three
 * other kinds of phone this app runs on. So each [LockCapability] says something true and
 * different: a phone with the hardware and nothing enrolled gets a shortcut into the system's own
 * enrolment; a phone with no sensor is told plainly that its passcode is what will be used, which
 * is a real answer rather than an apology; and a phone with no screen lock at all never sees this
 * sheet, because the app cannot add a lock the device does not have.
 *
 * ### It promises exactly what it does
 *
 * «رمز تازه‌ای نمی‌سازیم» is the line that matters. Readers have been trained by a decade of apps
 * to expect a new four-digit code to invent and forget. This one borrows the phone's, which is
 * both less work and — because the phone's is backed by hardware the app cannot reach — better.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLockSheet(
    enabled: Boolean,
    capability: LockCapability,
    onSetEnabled: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptics = rememberCoineProHaptics()

    CoineProSheet(
        title = stringResource(R.string.lock_sheet_title),
        subtitle = stringResource(R.string.lock_sheet_subtitle),
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.lock_sheet_switch),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = enabled,
                    // Off is always allowed, on only when the phone can actually challenge. A
                    // switch that turns on and then never asks for anything is worse than one that
                    // refuses: the reader believes the app is locked and it is not.
                    enabled = capability.usable || enabled,
                    onCheckedChange = { on ->
                        haptics.select()
                        onSetEnabled(on)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CoineProColors.OnAccent,
                        checkedTrackColor = CoineProColors.Gold,
                        uncheckedTrackColor = CoineProColors.Surface,
                        uncheckedBorderColor = CoineProColors.Border,
                    ),
                )
            }

            Text(
                text = stringResource(R.string.lock_sheet_note),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )

            when (capability) {
                LockCapability.NOT_ENROLLED -> {
                    Text(
                        text = stringResource(R.string.lock_sheet_not_enrolled),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.Warning,
                    )
                    CoineProSecondaryButton(
                        text = stringResource(R.string.lock_sheet_enrol),
                        onClick = { context.openBiometricEnrolment() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LockCapability.CREDENTIAL_ONLY -> Text(
                    text = stringResource(R.string.lock_sheet_credential_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
                LockCapability.READY, LockCapability.NONE -> Unit
            }
        }
    }
}
