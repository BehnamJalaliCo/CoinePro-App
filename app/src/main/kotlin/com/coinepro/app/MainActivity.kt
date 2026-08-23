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
import com.coinepro.core.auth.SessionController
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.marketdata.MarketDataController
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
    @Inject lateinit var pushCoordinator: PushCoordinator

    private var launchSignalId by mutableStateOf<Long?>(null)
    private var launchActivity by mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeDeepLink(intent)
        requestNotificationPermissionIfConfigured()
        enableEdgeToEdge()
        setContent {
            CoineProApp(
                sessionController = sessionController,
                marketDataController = marketDataController,
                signalController = signalController,
                notificationController = notificationController,
                executionController = executionController,
                pushCoordinator = pushCoordinator,
                launchSignalId = launchSignalId,
                launchActivity = launchActivity,
                onSignalLaunchConsumed = { launchSignalId = null },
                onActivityLaunchConsumed = { launchActivity = false },
            )
        }
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

    private fun requestNotificationPermissionIfConfigured() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
