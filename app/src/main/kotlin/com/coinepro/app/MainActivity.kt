package com.coinepro.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.coinepro.app.notifications.PushCoordinator
import com.coinepro.app.sync.BackgroundSyncScheduler
import com.coinepro.core.aiassistant.AiAssistantController
import com.coinepro.core.aisignal.AiSignalController
import com.coinepro.core.aivision.AiVisionController
import com.coinepro.core.auth.SessionController
import com.coinepro.core.auth.SessionState
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.marketdata.MarketDataController
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.notifications.NotificationController
import com.coinepro.core.signals.SignalController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sessionController: SessionController
    @Inject lateinit var marketDataController: MarketDataController
    @Inject lateinit var signalController: SignalController
    @Inject lateinit var notificationController: NotificationController
    @Inject lateinit var executionController: ExecutionController
    @Inject lateinit var aiSignalController: AiSignalController
    @Inject lateinit var aiVisionController: AiVisionController
    @Inject lateinit var aiAssistantController: AiAssistantController
    @Inject lateinit var marketIntelController: MarketIntelController
    @Inject lateinit var pushCoordinator: PushCoordinator
    @Inject lateinit var backgroundSyncScheduler: BackgroundSyncScheduler

    private var launchSignalId by mutableStateOf<Long?>(null)
    private var launchActivity by mutableStateOf(false)
    private var notificationPermissionState by mutableStateOf(NotificationPermissionUiState.NOT_CONFIGURED)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        updateNotificationPermissionState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeDeepLink(intent)
        updateNotificationPermissionState()
        enableEdgeToEdge()
        setContent {
            CoineProApp(
                sessionController = sessionController,
                marketDataController = marketDataController,
                signalController = signalController,
                notificationController = notificationController,
                executionController = executionController,
                aiSignalController = aiSignalController,
                aiVisionController = aiVisionController,
                aiAssistantController = aiAssistantController,
                marketIntelController = marketIntelController,
                pushCoordinator = pushCoordinator,
                backgroundSyncScheduler = backgroundSyncScheduler,
                launchSignalId = launchSignalId,
                launchActivity = launchActivity,
                notificationPermissionState = notificationPermissionState,
                onSignalLaunchConsumed = { launchSignalId = null },
                onActivityLaunchConsumed = { launchActivity = false },
                onRequestNotificationPermission = ::requestNotificationPermission,
                onSendFeedback = ::sendFeedback,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationPermissionState()
        if (sessionController.state.value !is SessionState.SignedIn) return
        marketDataController.syncOnResume()
        signalController.refresh()
        signalController.refreshHistory()
        executionController.refreshExecutions()
        notificationController.refresh()
        marketIntelController.refresh()
        backgroundSyncScheduler.requestImmediate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDeepLink(intent)
    }

    private fun consumeDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        when (uri.host) {
            "signal" -> launchSignalId = uri.pathSegments.firstOrNull()?.toLongOrNull()
            "activity" -> launchActivity = true
        }
    }

    private fun updateNotificationPermissionState() {
        notificationPermissionState = when {
            BuildConfig.FIREBASE_PROJECT_ID.isBlank() -> NotificationPermissionUiState.NOT_CONFIGURED
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> NotificationPermissionUiState.NOT_REQUIRED
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {
                NotificationPermissionUiState.GRANTED
            }
            else -> NotificationPermissionUiState.AVAILABLE_TO_REQUEST
        }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun sendFeedback() {
        val body = buildString {
            appendLine("CoinePro Android feedback")
            appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Environment: ${BuildConfig.BUILD_ENVIRONMENT}")
            appendLine()
            append("Feedback: ")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "CoinePro Android feedback")
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(intent, "Send CoinePro feedback"))
    }
}
