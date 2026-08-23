package com.coinepro.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class NotificationPermissionUiState {
    NOT_CONFIGURED,
    NOT_REQUIRED,
    AVAILABLE_TO_REQUEST,
    GRANTED,
}

@Composable
fun LaunchReadinessScreen(
    notificationPermissionState: NotificationPermissionUiState,
    onRequestNotificationPermission: () -> Unit,
    onSendFeedback: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Launch & safety", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Before using high-consequence features, review how CoinePro handles signals, permissions, external providers and support.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SafetyCard(
            title = "How CoinePro works",
            body = "Signal → Analysis → Entry / SL / TP → Execute → Monitor → Close / TP / SL → Result / History. Execution always requires an explicit confirmation path; Android does not invent broker or provider outcomes.",
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Permissions", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Camera access is requested only when you choose Camera in AI Vision. Gallery / file selection remains available without camera permission.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    notificationPermissionCopy(notificationPermissionState),
                    modifier = Modifier.semantics {
                        contentDescription = "Notification permission status: ${notificationPermissionCopy(notificationPermissionState)}"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (notificationPermissionState == NotificationPermissionUiState.AVAILABLE_TO_REQUEST) {
                    Button(onClick = onRequestNotificationPermission) {
                        Text("Enable notifications")
                    }
                }
            }
        }

        SafetyCard(
            title = "Trading and AI risk",
            body = "Trading and investment involve risk of loss. Signals, analysis and AI output are not guaranteed outcomes. Execution depends on external providers, account permissions, market conditions and server/provider confirmation. Historical or displayed results do not guarantee future performance. Review every order before confirming it.",
        )

        SafetyCard(
            title = "Provider truth",
            body = "A locally configured connection is not the same as a verified live provider connection. CoinePro enables provider-dependent actions only from explicit server/provider evidence; missing, stale or failed states stay visible.",
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Privacy", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Launch-readiness analytics are disabled. No analytics event SDK or new tracking event is introduced until purpose, fields, consent/retention and ownership are explicitly reviewed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Support & feedback", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Use the system share sheet to send feedback through an app you choose. The prepared message contains only app version/environment metadata and never includes session tokens, broker credentials, AI Vision images or execution secrets.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onSendFeedback) {
                    Text("Send feedback")
                }
            }
        }

        Text(
            "Production connectivity, provider whitelisting and live execution readiness are separate operational gates and are not implied by this screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun notificationPermissionCopy(state: NotificationPermissionUiState): String = when (state) {
    NotificationPermissionUiState.NOT_CONFIGURED ->
        "Push notifications are not configured for this build, so Android will not request notification permission."

    NotificationPermissionUiState.NOT_REQUIRED ->
        "This Android version does not require the runtime notification permission."

    NotificationPermissionUiState.AVAILABLE_TO_REQUEST ->
        "Notifications can alert you to server-provided signal and activity updates. Permission is optional and is requested only after you choose Enable notifications here."

    NotificationPermissionUiState.GRANTED ->
        "Notification permission is granted. Delivery still depends on configured push services and server state."
}

@Composable
private fun SafetyCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
