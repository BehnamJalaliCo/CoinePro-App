package com.coinepro.feature.admin

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.diagnostics.ABSENT
import com.coinepro.core.diagnostics.AdminUiState
import com.coinepro.core.diagnostics.DeviceReport
import com.coinepro.core.diagnostics.HubActions
import com.coinepro.core.diagnostics.LogLevel
import com.coinepro.core.diagnostics.PushPermission
import com.coinepro.core.diagnostics.PushPreferenceKey
import com.coinepro.core.diagnostics.PushStatus

/**
 * The install itself: what was built, what it is running on, and the panel's own controls.
 *
 * Everything here answers a question a developer sends back on the first reply to a bug report —
 * which build, which phone, how much room is left, is push actually deliverable — which is why all
 * of it is in the exported file as well as on screen.
 *
 * [push] is nullable rather than defaulted because "the app could not observe this" and "push is
 * off" are different facts. Where the app observed nothing, the card is absent rather than drawn
 * with confident-looking zeroes.
 */
internal fun LazyListScope.systemSection(
    state: AdminUiState,
    actions: HubActions,
    push: PushStatus?,
    verbosity: LogLevel,
) {
    item { BuildCard(state) }
    item { DeviceCard(state.device) }
    push?.let { item { PushCard(it, actions) } }
    item { LogSettingsCard(state, actions, verbosity) }
    item {
        CoineProSecondaryButton(
            text = stringResource(R.string.admin_lock_now),
            onClick = actions.onLock,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BuildCard(state: AdminUiState) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Info, R.string.admin_build_title)
        Field(R.string.admin_version, figure("${state.build.versionName} (${state.build.versionCode})"))
        Field(R.string.admin_environment, figure(state.build.environment))
        Field(R.string.admin_application_id, figure(state.build.applicationId))
        Field(
            R.string.admin_debuggable,
            stringResource(if (state.build.debuggable) R.string.admin_yes else R.string.admin_no),
            // A debuggable build in a reader's hands is a finding, not a detail.
            if (state.build.debuggable) CoineProColors.Sell else CoineProColors.TextPrimary,
        )
        Field(
            R.string.admin_firebase,
            stringResource(
                if (state.build.firebaseConfigured) R.string.admin_configured else R.string.admin_not_configured,
            ),
        )
    }
}

/**
 * The handset.
 *
 * Almost every report this project ever received was "it does not work on my phone", and almost
 * every one of them turned on something in this card: an Android version whose behaviour changed, a
 * locale that reorders a line, a device with no room left to write a cache. None of it identifies
 * anybody — model, version and language describe a class of device, not its owner.
 */
@Composable
private fun DeviceCard(device: DeviceReport) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.IdentityCard, R.string.admin_device_title)
        Field(R.string.admin_device_model, figure("${device.manufacturer} ${device.model}".trim()))
        Field(R.string.admin_device_android, figure("${device.androidRelease} (API ${device.sdkInt})"))
        Field(R.string.admin_device_abi, figure(device.abi))
        Field(R.string.admin_device_locale, figure(device.locale))
        Field(
            R.string.admin_device_direction,
            stringResource(if (device.layoutDirectionRtl) R.string.admin_rtl else R.string.admin_ltr),
        )
        Field(
            R.string.admin_device_heap,
            figure("${device.usedHeapMegabytes} / ${device.maxHeapMegabytes} MB"),
        )
        Field(
            R.string.admin_device_storage,
            figure("${device.freeStorageMegabytes} MB"),
            // Under a hundred megabytes free is where caches start failing silently, which then
            // arrives as "the app shows nothing" rather than as a storage problem.
            if (device.freeStorageMegabytes in 1..99) CoineProColors.Warning else CoineProColors.TextPrimary,
        )
        Muted(stringResource(R.string.admin_device_note))
    }
}

@Composable
private fun PushCard(push: PushStatus, actions: HubActions) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Bell, R.string.admin_push_title)

        // Both halves, separately. A granted permission on a server that cannot send is not "push
        // is on", and the fix for each is in a different place.
        Field(
            R.string.admin_push_permission,
            stringResource(push.permission.labelRes()),
            if (push.permission == PushPermission.GRANTED) CoineProColors.Buy else CoineProColors.Warning,
        )
        Field(
            R.string.admin_push_server,
            stringResource(
                when (push.serverEnabled) {
                    true -> R.string.admin_on
                    false -> R.string.admin_off
                    null -> R.string.admin_unknown
                },
            ),
            when (push.serverEnabled) {
                true -> CoineProColors.Buy
                false -> CoineProColors.Warning
                null -> CoineProColors.TextMuted
            },
        )
        Field(R.string.admin_push_token, figure(push.tokenHint))

        when (push.permission) {
            PushPermission.AVAILABLE -> CoineProSecondaryButton(
                text = stringResource(R.string.admin_push_request),
                onClick = actions.onRequestPushPermission,
                modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
            )
            PushPermission.DENIED -> CoineProSecondaryButton(
                text = stringResource(R.string.admin_push_settings),
                onClick = actions.onOpenPushSettings,
                modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
            )
            else -> Unit
        }

        Spacer(Modifier.height(CoineProSpacing.One))
        Divider()
        Toggle(R.string.admin_push_new_signals, push.newSignals) {
            actions.onSetPushPreference(PushPreferenceKey.NEW_SIGNALS, it)
        }
        Toggle(R.string.admin_push_signal_updates, push.signalUpdates) {
            actions.onSetPushPreference(PushPreferenceKey.SIGNAL_UPDATES, it)
        }
        Toggle(R.string.admin_push_price_alerts, push.priceAlerts) {
            actions.onSetPushPreference(PushPreferenceKey.PRICE_ALERTS, it)
        }

        CoineProSecondaryButton(
            text = stringResource(R.string.admin_push_reregister),
            onClick = actions.onReRegisterPushToken,
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
        )
    }
}

/**
 * What the log costs and how loudly it is written.
 *
 * The verbosity control is the one an operator reaches for when they are about to reproduce
 * something: TRACE for the two minutes it takes, then back. Leaving it on TRACE would mean the ring
 * holds two seconds of history the next time it matters, which is what the note under it says.
 *
 * "Wipe" clears the ring and the file together — see [com.coinepro.core.diagnostics.AppLog.clear].
 * It is the app's retention obligation made a button rather than a promise, and it is deliberately
 * on this section rather than beside the export, where a mis-tap would destroy the very thing an
 * operator came here to send.
 */
@Composable
private fun LogSettingsCard(state: AdminUiState, actions: HubActions, verbosity: LogLevel) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardHead(CoineProIcons.Settings, R.string.admin_log_verbosity)
        Spacer(Modifier.height(CoineProSpacing.One))
        CoineProSegmentedControl(
            // Latin, because these are the letters the exported file uses and an operator should
            // not have to translate between two alphabets to compare the screen against it.
            options = LogLevel.entries.map { it to it.name },
            selected = verbosity,
            onSelect = actions.onSetVerbosity,
        )
        Muted(stringResource(R.string.admin_log_verbosity_note))
        Field(
            R.string.admin_log_persisted,
            if (state.logBytes > 0) figure("${state.logBytes / 1024} KB") else ABSENT,
        )
        Muted(stringResource(R.string.admin_log_persisted_note))
        CoineProSecondaryButton(
            text = stringResource(R.string.admin_log_wipe),
            onClick = actions.onClearLog,
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.One),
        )
    }
}
