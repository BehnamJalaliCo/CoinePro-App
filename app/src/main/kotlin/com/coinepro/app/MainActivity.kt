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
import com.coinepro.core.academy.AcademyController
import com.coinepro.feature.terminal.TerminalController
import com.coinepro.core.account.AccountController
import com.coinepro.core.auth.PlatformCapabilities
import com.coinepro.core.auth.PlatformSessions
import com.coinepro.core.marketdata.MarketDataCache
import com.coinepro.core.diagnostics.AdminController
import com.coinepro.core.diagnostics.AppLog
import com.coinepro.core.aiassistant.AiAssistantController
import com.coinepro.core.aisignal.AiSignalController
import com.coinepro.core.aivision.AiVisionController
import com.coinepro.core.auth.EmailAuthController
import com.coinepro.core.datastore.ChartDrawingStore
import com.coinepro.core.datastore.ChartLayoutStore
import com.coinepro.app.security.AppIntegrity
import com.coinepro.app.security.IntegrityState
import com.coinepro.app.security.TamperedScreen
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.app.alerts.LocalAlertScheduler
import com.coinepro.core.datastore.LocalAlertStore
import com.coinepro.core.datastore.NotificationSettingsStore
import com.coinepro.core.datastore.ProfileStore
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.guest.GuestController
import com.coinepro.core.guest.GuestGateway
import com.coinepro.core.membership.MembershipController
import com.coinepro.core.journal.JournalController
import com.coinepro.core.papertrade.PaperTradeController
import com.coinepro.core.script.ScriptController
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
import com.coinepro.core.marketdata.AcademyTokenStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var sessionController: SessionController
    @Inject lateinit var emailAuthController: EmailAuthController
    @Inject lateinit var guestController: GuestController
    @Inject lateinit var profileStore: ProfileStore
    @Inject lateinit var notificationSettingsStore: NotificationSettingsStore
    @Inject lateinit var localAlertStore: LocalAlertStore
    @Inject lateinit var localAlertScheduler: LocalAlertScheduler
    @Inject lateinit var guestGateway: GuestGateway
    @Inject lateinit var membershipController: MembershipController
    @Inject lateinit var watchlistStore: WatchlistStore
    @Inject lateinit var chartLayoutStore: ChartLayoutStore
    @Inject lateinit var chartDrawingStore: ChartDrawingStore
    @Inject lateinit var journalController: JournalController
    @Inject lateinit var paperTradeController: PaperTradeController
    @Inject lateinit var scriptController: ScriptController
    @Inject lateinit var marketDataControllers: Map<MarketPlatform, @JvmSuppressWildcards MarketDataController>
    @Inject lateinit var marketSearchControllers: Map<MarketPlatform, @JvmSuppressWildcards MarketSearchController>
    @Inject lateinit var candleGateways: Map<MarketPlatform, @JvmSuppressWildcards CandleGateway>
    @Inject lateinit var portfolioControllers: Map<MarketPlatform, @JvmSuppressWildcards PortfolioController>
    @Inject lateinit var academyController: AcademyController
    @Inject lateinit var terminalController: TerminalController
    @Inject lateinit var accountControllers: Map<MarketPlatform, @JvmSuppressWildcards AccountController>
    @Inject lateinit var adminController: AdminController
    @Inject lateinit var appLog: AppLog
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
    @Inject
    lateinit var academyTokenStore: AcademyTokenStore
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
        // Before anything else is drawn, and before any controller is handed a screen.
        //
        // A repackaged copy gets this and nothing else. There is no point checking later: the whole
        // value of the check is that the reader never reaches a field they could type a password
        // into. See [AppIntegrity] for what this does and does not stop.
        val integrity = AppIntegrity.check(this)
        if (integrity is IntegrityState.Repackaged) {
            setContent { CoineProTheme { TamperedScreen(actualFingerprint = integrity.actual) } }
            return
        }
        setContent {
            CoineProApp(
                sessionController = sessionController,
                emailAuthController = emailAuthController,
                guestController = guestController,
                guestGateway = guestGateway,
                membershipController = membershipController,
                profileStore = profileStore,
                notificationSettingsStore = notificationSettingsStore,
                localAlertStore = localAlertStore,
                localAlertScheduler = localAlertScheduler,
                watchlistStore = watchlistStore,
                chartLayoutStore = chartLayoutStore,
                chartDrawingStore = chartDrawingStore,
                journalController = journalController,
                paperTradeController = paperTradeController,
                scriptController = scriptController,
                marketDataControllers = marketDataControllers,
                marketSearchControllers = marketSearchControllers,
                candleGateways = candleGateways,
                portfolioControllers = portfolioControllers,
                academyController = academyController,
                terminalController = terminalController,
                accountControllers = accountControllers,
                adminController = adminController,
                appLog = appLog,
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
                academyTokenStore = academyTokenStore,
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
