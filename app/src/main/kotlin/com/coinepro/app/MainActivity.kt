package com.coinepro.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.coinepro.app.notifications.PushCoordinator
import com.coinepro.app.sync.BackgroundSyncScheduler
import com.coinepro.core.account.AccountController
import com.coinepro.core.auth.PlatformCapabilities
import com.coinepro.core.auth.PlatformSessions
import com.coinepro.core.marketdata.MarketDataCache
import com.coinepro.core.diagnostics.AdminController
import com.coinepro.core.aiassistant.AiAssistantController
import com.coinepro.core.aisignal.AiSignalController
import com.coinepro.core.aivision.AiVisionController
import com.coinepro.core.auth.EmailAuthController
import com.coinepro.core.auth.SessionController
import com.coinepro.core.auth.SessionState
import com.coinepro.core.copytrade.CopyTradeController
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.datastore.ActivePlatformStore
import com.coinepro.core.marketdata.MarketDataController
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.notifications.NotificationController
import com.coinepro.core.portfolio.PortfolioController
import com.coinepro.core.signals.SignalController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sessionController: SessionController
    @Inject lateinit var emailAuthController: EmailAuthController
    @Inject lateinit var marketDataControllers: Map<MarketPlatform, @JvmSuppressWildcards MarketDataController>
    @Inject lateinit var marketSearchControllers: Map<MarketPlatform, @JvmSuppressWildcards MarketSearchController>
    @Inject lateinit var candleGateways: Map<MarketPlatform, @JvmSuppressWildcards CandleGateway>
    @Inject lateinit var portfolioControllers: Map<MarketPlatform, @JvmSuppressWildcards PortfolioController>
    @Inject lateinit var accountControllers: Map<MarketPlatform, @JvmSuppressWildcards AccountController>
    @Inject lateinit var adminController: AdminController
    @Inject lateinit var platformSessions: PlatformSessions
    @Inject lateinit var platformCapabilities: PlatformCapabilities
    @Inject lateinit var marketDataCache: MarketDataCache
    @Inject lateinit var activePlatformStore: ActivePlatformStore
    @Inject lateinit var signalControllers: Map<MarketPlatform, @JvmSuppressWildcards SignalController>
    @Inject lateinit var notificationControllers: Map<MarketPlatform, @JvmSuppressWildcards NotificationController>
    @Inject lateinit var executionControllers: Map<MarketPlatform, @JvmSuppressWildcards ExecutionController>
    @Inject lateinit var copyTradeControllers: Map<MarketPlatform, @JvmSuppressWildcards CopyTradeController>
    @Inject lateinit var aiSignalControllers: Map<MarketPlatform, @JvmSuppressWildcards AiSignalController>
    @Inject lateinit var aiVisionControllers: Map<MarketPlatform, @JvmSuppressWildcards AiVisionController>
    @Inject lateinit var aiAssistantController: AiAssistantController
    @Inject lateinit var marketIntelControllers: Map<MarketPlatform, @JvmSuppressWildcards MarketIntelController>
    @Inject lateinit var pushCoordinator: PushCoordinator
    @Inject lateinit var backgroundSyncScheduler: BackgroundSyncScheduler

    private var launchSignalId by mutableStateOf<Long?>(null)
    private var launchActivity by mutableStateOf(false)
    private var launchResetToken by mutableStateOf<String?>(null)
    private var notificationPermissionState by mutableStateOf(NotificationPermissionUiState.NOT_CONFIGURED)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        launchPreferences().edit().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true).apply()
        updateNotificationPermissionState()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageStore.apply(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeDeepLink(intent)
        updateNotificationPermissionState()
        enableEdgeToEdge()
        setContent {
            CoineProApp(
                sessionController = sessionController,
                emailAuthController = emailAuthController,
                marketDataControllers = marketDataControllers,
                marketSearchControllers = marketSearchControllers,
                candleGateways = candleGateways,
                portfolioControllers = portfolioControllers,
                accountControllers = accountControllers,
                adminController = adminController,
                platformSessions = platformSessions,
                platformCapabilities = platformCapabilities,
                marketDataCache = marketDataCache,
                activePlatformStore = activePlatformStore,
                signalControllers = signalControllers,
                notificationControllers = notificationControllers,
                executionControllers = executionControllers,
                copyTradeControllers = copyTradeControllers,
                aiSignalControllers = aiSignalControllers,
                aiVisionControllers = aiVisionControllers,
                aiAssistantController = aiAssistantController,
                marketIntelControllers = marketIntelControllers,
                pushCoordinator = pushCoordinator,
                backgroundSyncScheduler = backgroundSyncScheduler,
                launchSignalId = launchSignalId,
                launchActivity = launchActivity,
                launchResetToken = launchResetToken,
                notificationPermissionState = notificationPermissionState,
                onSignalLaunchConsumed = { launchSignalId = null },
                onActivityLaunchConsumed = { launchActivity = false },
                onResetTokenConsumed = { launchResetToken = null },
                onRequestNotificationPermission = ::requestNotificationPermission,
                onOpenNotificationSettings = ::openNotificationSettings,
                onSendFeedback = ::sendFeedback,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationPermissionState()
        if (sessionController.state.value !is SessionState.SignedIn) return
        // Only the platform on screen is refreshed; the other one is not running, and reading it
        // would spend requests on screens nobody is looking at.
        lifecycleScope.launch {
            val platform = activePlatformStore.active.first()
            marketDataControllers.getValue(platform).syncOnResume()
            signalControllers[platform]?.apply {
                refresh()
                refreshHistory()
            }
            executionControllers[platform]?.refreshExecutions()
            notificationControllers[platform]?.refresh()
        }
        backgroundSyncScheduler.requestImmediate()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDeepLink(intent)
    }

    private fun consumeDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        // A malformed query throws rather than returning null, and a link the app cannot read is
        // not a reason to fail to open.
        val resetToken = runCatching { uri.getQueryParameter("token") }.getOrNull()
        when (val target = parseCoineProDeepLink(uri.scheme, uri.host, uri.pathSegments, resetToken)) {
            is CoineProDeepLink.Signal -> launchSignalId = target.signalId
            CoineProDeepLink.Activity -> launchActivity = true
            is CoineProDeepLink.PasswordReset -> launchResetToken = target.token
            null -> Unit
        }
    }

    private fun updateNotificationPermissionState() {
        val previouslyRequested = launchPreferences().getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
        notificationPermissionState = when {
            BuildConfig.FIREBASE_PROJECT_ID.isBlank() -> NotificationPermissionUiState.NOT_CONFIGURED
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> NotificationPermissionUiState.NOT_REQUIRED
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {
                NotificationPermissionUiState.GRANTED
            }
            previouslyRequested -> NotificationPermissionUiState.DENIED
            else -> NotificationPermissionUiState.AVAILABLE_TO_REQUEST
        }
    }

    private fun requestNotificationPermission() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank() &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            launchPreferences().edit().putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true).apply()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openNotificationSettings() {
        val notificationIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        runCatching { startActivity(notificationIntent) }
            .onFailure {
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null),
                    ),
                )
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
        runCatching { startActivity(Intent.createChooser(intent, "Send CoinePro feedback")) }
    }

    private fun launchPreferences() = getSharedPreferences(LAUNCH_PREFERENCES, MODE_PRIVATE)

    companion object {
        private const val LAUNCH_PREFERENCES = "launch_readiness"
        private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
    }
}
