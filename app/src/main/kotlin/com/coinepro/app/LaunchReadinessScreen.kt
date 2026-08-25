package com.coinepro.app

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSpacing

/** Whether the runtime notification permission can be asked for, and whether it already was. */
enum class NotificationPermissionUiState {
    NOT_CONFIGURED,
    NOT_REQUIRED,
    AVAILABLE_TO_REQUEST,
    DENIED,
    GRANTED,
}

/**
 * What a reader should know before using anything with consequences.
 *
 * The risk and provider-truth cards are accented rather than left as ordinary copy. This is the
 * screen a reader opens once and skims, so the two claims that actually cost money if missed — that
 * outcomes are not guaranteed, and that a configured connection is not a verified one — have to
 * survive being skimmed.
 */
@Composable
fun LaunchReadinessScreen(
    notificationPermissionState: NotificationPermissionUiState,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSendFeedback: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .verticalScroll(rememberScrollState())
            .padding(CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Stack),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = CoineProSpacing.Half),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.safety_title),
                style = MaterialTheme.typography.headlineSmall,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.safety_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )
        }

        SafetyCard(R.string.safety_how_title, R.string.safety_how_body)

        CoineProCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(R.string.safety_permissions_title)
            Body(R.string.safety_camera_body)
            Spacer(Modifier.height(CoineProSpacing.One))

            val permissionCopy = stringResource(notificationPermissionState.copyRes())
            Text(
                text = permissionCopy,
                modifier = Modifier.semantics {
                    contentDescription = permissionCopy
                },
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )
            when (notificationPermissionState) {
                NotificationPermissionUiState.AVAILABLE_TO_REQUEST -> {
                    Spacer(Modifier.height(CoineProSpacing.OneHalf))
                    CoineProPrimaryButton(
                        text = stringResource(R.string.safety_enable_notifications),
                        onClick = onRequestNotificationPermission,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                NotificationPermissionUiState.DENIED -> {
                    Spacer(Modifier.height(CoineProSpacing.OneHalf))
                    CoineProPrimaryButton(
                        text = stringResource(R.string.safety_open_settings),
                        onClick = onOpenNotificationSettings,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                else -> Unit
            }
        }

        SafetyCard(R.string.safety_risk_title, R.string.safety_risk_body, accent = CoineProColors.Warning)
        SafetyCard(R.string.safety_provider_title, R.string.safety_provider_body, accent = CoineProColors.Warning)
        SafetyCard(R.string.safety_privacy_title, R.string.safety_privacy_body)

        CoineProCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(R.string.safety_support_title)
            Body(R.string.safety_support_body)
            Spacer(Modifier.height(CoineProSpacing.OneHalf))
            CoineProPrimaryButton(
                text = stringResource(R.string.safety_send_feedback),
                onClick = onSendFeedback,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Text(
            text = stringResource(R.string.safety_footer),
            modifier = Modifier.padding(horizontal = CoineProSpacing.Half),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

@Composable
private fun SafetyCard(
    @StringRes title: Int,
    @StringRes body: Int,
    accent: Color? = null,
) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleSmall,
            color = accent ?: CoineProColors.TextPrimary,
        )
        Spacer(Modifier.height(CoineProSpacing.One))
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
    }
}

@Composable
private fun CardTitle(@StringRes title: Int) {
    Text(
        text = stringResource(title),
        style = MaterialTheme.typography.titleSmall,
        color = CoineProColors.TextPrimary,
    )
    Spacer(Modifier.height(CoineProSpacing.One))
}

@Composable
private fun Body(@StringRes body: Int) {
    Text(
        text = stringResource(body),
        style = MaterialTheme.typography.bodyMedium,
        color = CoineProColors.TextSecondary,
    )
}

@StringRes
private fun NotificationPermissionUiState.copyRes(): Int = when (this) {
    NotificationPermissionUiState.NOT_CONFIGURED -> R.string.safety_push_not_configured
    NotificationPermissionUiState.NOT_REQUIRED -> R.string.safety_push_not_required
    NotificationPermissionUiState.AVAILABLE_TO_REQUEST -> R.string.safety_push_available
    NotificationPermissionUiState.DENIED -> R.string.safety_push_denied
    NotificationPermissionUiState.GRANTED -> R.string.safety_push_granted
}
