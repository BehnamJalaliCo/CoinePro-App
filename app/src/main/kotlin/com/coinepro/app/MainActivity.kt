package com.coinepro.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.coinepro.app.alerts.AlertDeepLink
import com.coinepro.app.notifications.PushCoordinator
import com.coinepro.app.sync.BackgroundSyncScheduler
import com.coinepro.core.academy.AcademyController
import com.coinepro.core.community.CommunityController
import com.coinepro.core.chartevents.ChartEventController
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
import com.coinepro.core.datastore.SymbolChartStateStore
import com.coinepro.core.datastore.DrawingTemplateStore
import com.coinepro.core.datastore.IndicatorTemplateStore
import com.coinepro.core.datastore.DrawingImageStore
import com.coinepro.core.datastore.DrawingSyncStore
import com.coinepro.core.datastore.ChartEventPrefsStore
import com.coinepro.core.datastore.TimeZonePrefStore
import com.coinepro.core.datastore.IndicatorFavouritesStore
import com.coinepro.core.datastore.IntervalFavouritesStore
import com.coinepro.core.datastore.TeachingStore
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.feature.chart.ChartWorkspaceStore
import com.coinepro.feature.alerts.AlertsController
import com.coinepro.feature.screener.ScreenerController
import com.coinepro.feature.screener.ScreenerStore
import com.coinepro.app.security.AppIntegrity
import com.coinepro.app.security.IntegrityState
import com.coinepro.app.security.TamperedScreen
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalLogoProvider
import com.coinepro.core.designsystem.LogoProvider
import com.coinepro.app.alerts.LocalAlertScheduler
import com.coinepro.app.alerts.InAppAlertBus
import com.coinepro.core.datastore.LocalAlertStore
import com.coinepro.core.datastore.NotificationSettingsStore
import com.coinepro.core.datastore.ProfileStore
import com.coinepro.core.datastore.UserPreferencesStore
import com.coinepro.app.widget.MarketsWidget
import com.coinepro.app.widget.WidgetRefreshEngine
import com.coinepro.core.marketdata.CandleArchive
import com.coinepro.core.marketdata.CandleCache
import com.coinepro.core.network.NetworkStatus
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.watchlistsync.WatchlistSyncController
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
import com.coinepro.core.orderbook.OrderBookGateway
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.notifications.NotificationController
import com.coinepro.core.portfolio.PortfolioController
import com.coinepro.core.signals.SignalController
import com.coinepro.core.announcements.AnnouncementsController
import com.coinepro.core.marketdata.AcademyTokenStore
import com.coinepro.core.marketdata.MarketTickerStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
/**
 * A `FragmentActivity` rather than a plain `ComponentActivity`, for one reason.
 *
 * `BiometricPrompt` takes a `FragmentActivity` — it hosts its dialog in a fragment so the prompt
 * survives a rotation mid-authentication. `FragmentActivity` *is* a `ComponentActivity`, so
 * nothing else in this file changes and Compose, Hilt and the result launchers all behave exactly
 * as before. See `BiometricGate`.
 */
class MainActivity : FragmentActivity() {
    @Inject lateinit var sessionController: SessionController
    @Inject lateinit var emailAuthController: EmailAuthController
    @Inject lateinit var guestController: GuestController
    @Inject lateinit var profileStore: ProfileStore
    @Inject lateinit var userPreferencesStore: UserPreferencesStore
    @Inject lateinit var networkStatus: NetworkStatus
    @Inject lateinit var widgetRefreshEngine: WidgetRefreshEngine
    @Inject lateinit var candleCache: CandleCache
    @Inject lateinit var candleArchive: CandleArchive
    @Inject lateinit var notificationSettingsStore: NotificationSettingsStore
    @Inject lateinit var localAlertStore: LocalAlertStore
    @Inject lateinit var localAlertScheduler: LocalAlertScheduler
    @Inject lateinit var alertsController: AlertsController
    @Inject lateinit var inAppAlertBus: InAppAlertBus
    @Inject lateinit var guestGateway: GuestGateway
    @Inject lateinit var membershipController: MembershipController
    @Inject lateinit var watchlistStore: WatchlistStore

    @Inject
    lateinit var watchlistSyncController: WatchlistSyncController
    @Inject lateinit var chartLayoutStore: ChartLayoutStore
    @Inject lateinit var chartDrawingStore: ChartDrawingStore
    @Inject lateinit var symbolChartStateStore: SymbolChartStateStore
    @Inject lateinit var drawingTemplateStore: DrawingTemplateStore
    @Inject lateinit var indicatorTemplateStore: IndicatorTemplateStore
    @Inject lateinit var drawingSyncStore: DrawingSyncStore
    @Inject lateinit var drawingImageStore: DrawingImageStore
    @Inject lateinit var timeZonePrefStore: TimeZonePrefStore
    @Inject lateinit var chartEventPrefsStore: ChartEventPrefsStore
    @Inject lateinit var intervalFavouritesStore: IntervalFavouritesStore
    @Inject lateinit var indicatorFavouritesStore: IndicatorFavouritesStore
    @Inject lateinit var teachingStore: TeachingStore
    @Inject lateinit var chartWorkspaceStore: ChartWorkspaceStore
    @Inject lateinit var journalController: JournalController
    @Inject lateinit var paperTradeController: PaperTradeController
    @Inject lateinit var scriptController: ScriptController
    @Inject lateinit var marketDataControllers: Map<MarketPlatform, @JvmSuppressWildcards MarketDataController>
    @Inject lateinit var marketSearchControllers: Map<MarketPlatform, @JvmSuppressWildcards MarketSearchController>

    @Inject
    lateinit var marketTickerStores: Map<MarketPlatform, @JvmSuppressWildcards MarketTickerStore>
    @Inject lateinit var screenerControllers: Map<MarketPlatform, @JvmSuppressWildcards ScreenerController>
    @Inject lateinit var screenerStore: ScreenerStore
    @Inject lateinit var candleGateways: Map<MarketPlatform, @JvmSuppressWildcards CandleGateway>
    @Inject lateinit var orderBookGateways: Map<MarketPlatform, @JvmSuppressWildcards OrderBookGateway>
    @Inject lateinit var portfolioControllers: Map<MarketPlatform, @JvmSuppressWildcards PortfolioController>
    @Inject lateinit var academyController: AcademyController
    @Inject lateinit var communityController: CommunityController
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

    @Inject
    lateinit var announcementsControllers: Map<MarketPlatform, @JvmSuppressWildcards AnnouncementsController>
    @Inject lateinit var chartEventControllers: Map<MarketPlatform, @JvmSuppressWildcards ChartEventController>
    @Inject lateinit var pushCoordinator: PushCoordinator
    @Inject lateinit var backgroundSyncScheduler: BackgroundSyncScheduler

    private var launchSignalId by mutableStateOf<Long?>(null)
    private var launchActivity by mutableStateOf(false)
    private var launchResetToken by mutableStateOf<String?>(null)

    /**
     * A market to open, from a row of the home-screen widget.
     *
     * Held here rather than passed straight down for the same reason the signal id is: the intent
     * can arrive before the composition exists, and it has to survive until something is ready to
     * act on it.
     */
    private var launchSymbol by mutableStateOf<String?>(null)

    /** The bar `?tf=` asked for, alongside [launchSymbol] and consumed with it. */
    private var launchTimeframe by mutableStateOf<String?>(null)
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
        // The manifest opens the window in `Theme.CoinePro.Launch` — white, so the frame the
        // launcher shows matches the launch sheet drawn over the app. The app's own theme takes
        // over here, before the first frame is composed. See `LaunchSplash`.
        setTheme(R.style.Theme_CoinePro)
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
            // The launch sheet over the app, once per process. `rememberSaveable` so a rotation
            // during the first two seconds does not start the launch again, and a plain Box so the
            // app underneath composes — and loads — while the sheet is still up.
            var launched by rememberSaveable { mutableStateOf(false) }
            Box {
            // One collection of the dismissal set for the whole app. Around `CoineProApp` rather
            // than inside it, so no screen has to be handed a store it does not otherwise use.
            CompositionLocalProvider(
                LocalTeachingDismissals provides rememberTeachingDismissals(teachingStore),
                // Logos the drawn set does not cover, from the API host. See `LogoProvider`.
                LocalLogoProvider provides remoteLogos,
            ) {
            CoineProApp(
                sessionController = sessionController,
                emailAuthController = emailAuthController,
                guestController = guestController,
                guestGateway = guestGateway,
                membershipController = membershipController,
                profileStore = profileStore,
                userPreferencesStore = userPreferencesStore,
                networkStatus = networkStatus,
                candleCache = candleCache,
                candleArchive = candleArchive,
                notificationSettingsStore = notificationSettingsStore,
                localAlertStore = localAlertStore,
                localAlertScheduler = localAlertScheduler,
                alertsController = alertsController,
                inAppAlerts = inAppAlertBus,
                watchlistStore = watchlistStore,
                watchlistSyncController = watchlistSyncController,
                chartLayoutStore = chartLayoutStore,
                chartDrawingStore = chartDrawingStore,
                drawingImageStore = drawingImageStore,
                symbolChartStateStore = symbolChartStateStore,
                drawingTemplateStore = drawingTemplateStore,
                indicatorTemplateStore = indicatorTemplateStore,
                drawingSyncStore = drawingSyncStore,
                timeZonePrefStore = timeZonePrefStore,
                chartEventPrefsStore = chartEventPrefsStore,
                intervalFavouritesStore = intervalFavouritesStore,
                indicatorFavouritesStore = indicatorFavouritesStore,
                chartWorkspaceStore = chartWorkspaceStore,
                journalController = journalController,
                paperTradeController = paperTradeController,
                scriptController = scriptController,
                marketDataControllers = marketDataControllers,
                marketSearchControllers = marketSearchControllers,
                marketTickerStores = marketTickerStores,
                screenerControllers = screenerControllers,
                screenerStore = screenerStore,
                candleGateways = candleGateways,
                orderBookGateways = orderBookGateways,
                portfolioControllers = portfolioControllers,
                academyController = academyController,
                communityController = communityController,
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
                announcementsControllers = announcementsControllers,
                chartEventControllers = chartEventControllers,
                academyTokenStore = academyTokenStore,
                pushCoordinator = pushCoordinator,
                backgroundSyncScheduler = backgroundSyncScheduler,
                launchSignalId = launchSignalId,
                launchActivity = launchActivity,
                launchResetToken = launchResetToken,
                launchSymbol = launchSymbol,
                launchTimeframe = launchTimeframe,
                notificationPermissionState = notificationPermissionState,
                onSignalLaunchConsumed = { launchSignalId = null },
                onActivityLaunchConsumed = { launchActivity = false },
                onResetTokenConsumed = { launchResetToken = null },
                onSymbolLaunchConsumed = {
                    launchSymbol = null
                    launchTimeframe = null
                },
                onRequestNotificationPermission = ::requestNotificationPermission,
                onOpenNotificationSettings = ::openNotificationSettings,
                onSendFeedback = ::sendFeedback,
            )
            }
            if (!launched) LaunchSplash(onFinished = { launched = true })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationPermissionState()
        refreshWidgets()
        // The reader's session, not TradeYar's. The unqualified controller is bound to the
        // crypto platform, so a reader whose only account is on CoinePro-FX got no refresh on
        // resume at all — the same class of mistake as the shell's own gate. See
        // `PlatformSessions.sessionForShell`.
        if (platformSessions.signedIn.value.isEmpty()) return
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

    /**
     * Bring the home-screen widget up to date with what this session knows.
     *
     * On resume rather than on every watchlist change: a reader dragging stars around would
     * otherwise fire a fetch per tap. Coming back to the home screen is the moment the widget is
     * about to be *looked at*, which is the only moment its freshness matters — and the app is in
     * the foreground with a network already open, so this is the cheapest fetch of the day.
     *
     * Failures are the engine's to absorb; it keeps the old prices and labels them.
     */
    private fun refreshWidgets() {
        lifecycleScope.launch {
            runCatching { widgetRefreshEngine.refresh() }
            MarketsWidget.refreshAll(this@MainActivity)
        }
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
        val timeframe = runCatching { uri.getQueryParameter(AlertDeepLink.TIMEFRAME_QUERY) }.getOrNull()
        val target = parseCoineProDeepLink(uri.scheme, uri.host, uri.pathSegments, resetToken, timeframe)
        when (target) {
            is CoineProDeepLink.Signal -> launchSignalId = target.signalId
            CoineProDeepLink.Activity -> launchActivity = true
            is CoineProDeepLink.PasswordReset -> launchResetToken = target.token
            is CoineProDeepLink.Market -> {
                // Set before the symbol, and both in the same frame. The chart's launch effect is
                // keyed on the symbol, so a timeframe written after it would arrive one
                // recomposition too late and be read on the *next* deep link instead of this one.
                launchTimeframe = target.timeframe
                launchSymbol = target.symbol
            }
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

/**
 * `/assets/logo/<SYMBOL>.webp` on the API host, for a symbol the vendored artwork does not draw.
 *
 * The host is the build's own; a debug build points at staging and a release at production, and
 * the fetch rides the app's OkHttp client with its pins. Everything the artwork covers never asks.
 */
private val remoteLogos: LogoProvider = LogoProvider { symbol ->
    val base = BuildConfig.API_BASE_URL.trimEnd('/')
    if (base.isBlank()) null else "$base/assets/logo/${symbol.uppercase()}.webp"
}
