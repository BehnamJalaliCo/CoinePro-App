@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.coinepro.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.app.di.WebGraph
import com.coinepro.core.common.SiteAssets
import com.coinepro.core.datastore.MarketColorScheme
import com.coinepro.core.datastore.QuoteCurrency
import com.coinepro.core.datastore.ReaderMode
import com.coinepro.core.datastore.ThemeMode
import com.coinepro.core.designsystem.CoineProFold
import com.coinepro.core.designsystem.CoineProTeachingHost
import com.coinepro.core.designsystem.LocalCoineProFold
import com.coinepro.core.designsystem.LocalLogoProvider
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.designsystem.LogoProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/*
 * The page's `MainActivity`: the same composition `MainActivity.setContent` builds — the launch
 * sheet, the app under it, the three first-run screens over it — from the same objects, which here
 * come from `WebGraph` (the phone's Hilt module, generated for the browser) instead of from Hilt.
 *
 * What an activity does that a page does differently is written out below and nothing else:
 * deep links arrive as the page's address instead of an intent; «resume» is the tab becoming
 * visible; the notification permission is the browser's; the language is applied by reloading,
 * which is what `recreate()` is on the phone.
 */

/** The class the phone's notifications name as the one to open. The page is it. */
class MainActivity : androidx.fragment.app.FragmentActivity()

private fun pathJs(): String = js("window.location.pathname")
private fun searchJs(): String = js("window.location.search")
private fun onVisibleJs(callback: () -> Unit): Unit =
    js("document.addEventListener('visibilitychange', function () { if (document.visibilityState === 'visible') callback(); })")

/** The launch state an intent carries on the phone, set from the address or a notification tap. */
object WebLaunch {
    var signalId by mutableStateOf<Long?>(null)
    var activity by mutableStateOf(false)
    var resetToken by mutableStateOf<String?>(null)
    var scriptId by mutableStateOf<String?>(null)
    var symbol by mutableStateOf<String?>(null)
    var timeframe by mutableStateOf<String?>(null)
    var route by mutableStateOf<String?>(null)
    var notificationPermission by mutableStateOf(NotificationPermissionUiState.AVAILABLE_TO_REQUEST)

    /**
     * The page's own address as a deep link. `/terminal/s/<id>` is `https://pro-chart.com/s/<id>`,
     * `/terminal/signal/<id>` is `coinepro://signal/<id>`, and so on — the same parser, the same
     * validation, the same targets as the phone's `consumeDeepLink`.
     */
    fun fromAddress() {
        val segments = pathJs().split('/').filter { it.isNotEmpty() }.let { if (it.firstOrNull() == "terminal") it.drop(1) else it }
        if (segments.isEmpty()) return
        // A top-level screen by name — `/terminal/screener` — rather than a deep link: it carries
        // nothing to validate beyond being one of the named screens.
        if (segments.size == 1 && segments.first() in LAUNCHABLE_ROUTES) {
            route = segments.first()
            return
        }
        val query = searchJs().removePrefix("?").split('&').filter { '=' in it }
            .associate { it.substringBefore('=') to java.net.URLDecoder.decode(it.substringAfter('='), "UTF-8") }
        // `/terminal/BTCUSDT/4h`, the form the page documents (5.18.1).
        webChartLinkOrNull(segments, query[com.coinepro.app.alerts.AlertDeepLink.TIMEFRAME_QUERY])?.let { market ->
            timeframe = market.timeframe
            symbol = market.symbol
            return
        }
        val custom = segments.first() in setOf("signal", "activity", "market")
        val uri = if (custom) {
            Uri.parse("coinepro://" + segments.joinToString("/") + searchJs())
        } else {
            Uri.parse("https://${com.coinepro.core.common.BrandConfig.WEB_HOST}/" + segments.joinToString("/") + searchJs())
        }
        consume(uri, query["token"], query[com.coinepro.app.alerts.AlertDeepLink.TIMEFRAME_QUERY])
    }

    fun consume(intent: Intent) {
        intent.getStringExtra(com.coinepro.app.alerts.AlertAcknowledgeReceiver.EXTRA_ALERT_ID)
            ?.takeIf(String::isNotBlank)
            ?.let { id -> kotlinx.coroutines.MainScope().launch { WebGraph.localAlertStore.acknowledge(id, System.currentTimeMillis()) } }
        val uri = intent.data ?: return
        consume(uri, runCatching { uri.getQueryParameter("token") }.getOrNull(),
            runCatching { uri.getQueryParameter(com.coinepro.app.alerts.AlertDeepLink.TIMEFRAME_QUERY) }.getOrNull())
    }

    private fun consume(uri: Uri, resetToken: String?, timeframe: String?) {
        when (val target = parseCoineProDeepLink(uri.scheme, uri.host, uri.pathSegments, resetToken, timeframe)) {
            is CoineProDeepLink.Signal -> signalId = target.signalId
            CoineProDeepLink.Activity -> activity = true
            is CoineProDeepLink.PasswordReset -> this.resetToken = target.token
            is CoineProDeepLink.Market -> {
                this.timeframe = target.timeframe
                symbol = target.symbol
            }
            is CoineProDeepLink.Script -> scriptId = target.scriptId
            null -> Unit
        }
    }

    fun updateNotificationPermission() {
        notificationPermission = when {
            com.coinepro.web.content.WebPermissions.granted(android.Manifest.permission.POST_NOTIFICATIONS) ->
                NotificationPermissionUiState.GRANTED
            android.content.Context.Page.getSharedPreferences(LAUNCH_PREFERENCES, 0).getBoolean(KEY_REQUESTED, false) ->
                NotificationPermissionUiState.DENIED
            else -> NotificationPermissionUiState.AVAILABLE_TO_REQUEST
        }
    }

    fun requestNotificationPermission() {
        android.content.Context.Page.getSharedPreferences(LAUNCH_PREFERENCES, 0).edit().putBoolean(KEY_REQUESTED, true).apply()
        com.coinepro.web.content.WebPermissions.request(android.Manifest.permission.POST_NOTIFICATIONS) { updateNotificationPermission() }
    }

    /** The phone's «what happens on resume»: the platform on screen is refreshed, and background sync asked for. */
    fun resume() {
        updateNotificationPermission()
        if (WebGraph.platformSessions.signedIn.value.isEmpty()) return
        kotlinx.coroutines.MainScope().launch {
            val platform = WebGraph.activePlatformStore.active.first()
            WebGraph.marketDataControllers.getValue(platform).syncOnResume()
            WebGraph.signalControllers[platform]?.apply {
                refresh()
                refreshHistory()
            }
            WebGraph.executionControllers[platform]?.refreshExecutions()
            WebGraph.notificationControllers[platform]?.refresh()
        }
        WebGraph.backgroundSyncScheduler.requestImmediate()
    }

    fun install() {
        fromAddress()
        updateNotificationPermission()
        com.coinepro.web.content.WebNotifications.onOpen = ::consume
        onVisibleJs(::resume)
    }

    private const val LAUNCH_PREFERENCES = "launch_readiness"
    private const val KEY_REQUESTED = "notification_permission_requested"
}

/** `/assets/logo/<SYMBOL>.webp` on the API host's site, for a symbol the artwork does not draw — as on the phone. */
private val remoteLogos: LogoProvider = LogoProvider { symbol ->
    SiteAssets.url(BuildConfig.API_BASE_URL, "assets/logo/${symbol.uppercase()}.webp")
}

private fun sendFeedback() {
    val body = buildString {
        appendLine("CoinePro web feedback")
        appendLine("Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Environment: ${BuildConfig.BUILD_ENVIRONMENT}")
        appendLine()
        append("Feedback: ")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "CoinePro web feedback")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    runCatching { android.content.Context.Page.startActivity(Intent.createChooser(intent, "Send CoinePro feedback")) }
}

/** `MainActivity.setContent`, for the page. */
@Composable
fun WebApp() {
    val scope = rememberCoroutineScope()
    var launched by remember { mutableStateOf(false) }
    var composeApp by remember { mutableStateOf(false) }
    var appDrawn by remember { mutableStateOf(false) }
    if (composeApp || launched) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            appDrawn = true
        }
    }
    Box {
        if (composeApp || launched) {
            CompositionLocalProvider(
                LocalTeachingDismissals provides rememberTeachingDismissals(WebGraph.teachingStore),
                LocalLogoProvider provides remoteLogos,
                // A browser window has no hinge.
                LocalCoineProFold provides CoineProFold.Flat,
            ) {
                CoineProTeachingHost {
                    CoineProApp(
                        sessionController = WebGraph.sessionController,
                        emailAuthController = WebGraph.emailAuthController,
                        guestController = WebGraph.guestController,
                        guestGateway = WebGraph.guestGateway,
                        membershipController = WebGraph.membershipController,
                        profileStore = WebGraph.profileStore,
                        userPreferencesStore = WebGraph.userPreferencesStore,
                        networkStatus = WebGraph.networkStatus,
                        candleCache = WebGraph.candleCache,
                        candleArchive = WebGraph.candleArchive,
                        tickHistory = WebGraph.tickHistory,
                        liveTradeController = WebGraph.liveTradeController,
                        notificationSettingsStore = WebGraph.notificationSettingsStore,
                        localAlertStore = WebGraph.localAlertStore,
                        localAlertScheduler = WebGraph.localAlertScheduler,
                        morningBriefScheduler = WebGraph.morningBriefScheduler,
                        alertsController = WebGraph.alertsController,
                        inAppAlerts = WebGraph.inAppAlertBus,
                        watchlistStore = WebGraph.watchlistStore,
                        lastVisitStore = WebGraph.lastVisitStore,
                        scriptInstallStore = WebGraph.scriptInstallStore,
                        watchlistSyncController = WebGraph.watchlistSyncController,
                        chartLayoutStore = WebGraph.chartLayoutStore,
                        chartDrawingStore = WebGraph.chartDrawingStore,
                        drawingImageStore = WebGraph.drawingImageStore,
                        symbolChartStateStore = WebGraph.symbolChartStateStore,
                        drawingTemplateStore = WebGraph.drawingTemplateStore,
                        indicatorTemplateStore = WebGraph.indicatorTemplateStore,
                        drawingSyncStore = WebGraph.drawingSyncStore,
                        timeZonePrefStore = WebGraph.timeZonePrefStore,
                        chartEventPrefsStore = WebGraph.chartEventPrefsStore,
                        intervalFavouritesStore = WebGraph.intervalFavouritesStore,
                        indicatorFavouritesStore = WebGraph.indicatorFavouritesStore,
                        recentSearchStore = WebGraph.recentSearchStore,
                        chartWorkspaceStore = WebGraph.chartWorkspaceStore,
                        arenaStore = WebGraph.arenaStore,
                        duelStore = WebGraph.duelStore,
                        chartWatchStore = WebGraph.chartWatchStore,
                        // A browser tab cannot shrink to a floating window of its own; the hub then
                        // offers no tile, as on a phone without the mode.
                        onKeepWatching = null,
                        journalController = WebGraph.journalController,
                        paperTradeController = WebGraph.paperTradeController,
                        scriptController = WebGraph.scriptController,
                        marketDataControllers = WebGraph.marketDataControllers,
                        marketSearchControllers = WebGraph.marketSearchControllers,
                        marketTickerStores = WebGraph.marketTickerStores,
                        screenerControllers = WebGraph.screenerControllers,
                        screenerStore = WebGraph.screenerStore,
                        candleGateways = WebGraph.candleGateways,
                        orderBookGateways = WebGraph.orderBookGateways,
                        portfolioControllers = WebGraph.portfolioControllers,
                        academyController = WebGraph.academyController,
                        communityController = WebGraph.communityController,
                        terminalController = WebGraph.terminalController,
                        accountControllers = WebGraph.accountControllers,
                        adminController = WebGraph.adminController,
                        appLog = WebGraph.appLog,
                        appUpdateGateway = WebGraph.appUpdateGateway,
                        platformSessions = WebGraph.platformSessions,
                        platformCapabilities = WebGraph.platformCapabilities,
                        marketDataCache = WebGraph.marketDataCache,
                        activePlatformStore = WebGraph.activePlatformStore,
                        signalControllers = WebGraph.signalControllers,
                        notificationControllers = WebGraph.notificationControllers,
                        executionControllers = WebGraph.executionControllers,
                        aiSignalControllers = WebGraph.aiSignalControllers,
                        aiVisionControllers = WebGraph.aiVisionControllers,
                        aiAssistantController = WebGraph.aiAssistantController,
                        marketIntelControllers = WebGraph.marketIntelControllers,
                        announcementsControllers = WebGraph.announcementsControllers,
                        chartEventControllers = WebGraph.chartEventControllers,
                        academyTokenStore = WebGraph.academyTokenStore,
                        pushCoordinator = WebGraph.pushCoordinator,
                        backgroundSyncScheduler = WebGraph.backgroundSyncScheduler,
                        launchSignalId = WebLaunch.signalId,
                        launchActivity = WebLaunch.activity,
                        launchResetToken = WebLaunch.resetToken,
                        launchScriptId = WebLaunch.scriptId,
                        launchSymbol = WebLaunch.symbol,
                        launchTimeframe = WebLaunch.timeframe,
                        notificationPermissionState = WebLaunch.notificationPermission,
                        onSignalLaunchConsumed = { WebLaunch.signalId = null },
                        onActivityLaunchConsumed = { WebLaunch.activity = false },
                        launchRoute = WebLaunch.route,
                        onRouteLaunchConsumed = { WebLaunch.route = null },
                        onResetTokenConsumed = { WebLaunch.resetToken = null },
                        onScriptLaunchConsumed = { WebLaunch.scriptId = null },
                        onSymbolLaunchConsumed = {
                            WebLaunch.symbol = null
                            WebLaunch.timeframe = null
                        },
                        onRequestNotificationPermission = WebLaunch::requestNotificationPermission,
                        // A page cannot open the browser's own settings; the phone's screen already
                        // says where the switch is when the permission was refused.
                        onOpenNotificationSettings = {},
                        onSendFeedback = ::sendFeedback,
                    )
                }
            }
        }
        if (!launched) {
            LaunchSplash(
                onFinished = { launched = true },
                onDrawn = { composeApp = true },
                appReady = appDrawn,
            )
        }
        val store = WebGraph.userPreferencesStore
        val readerModeChosen by store.readerModeChosen.collectAsStateWithLifecycle(initialValue = true)
        val welcomeSeen by store.welcomeSeen.collectAsStateWithLifecycle(initialValue = true)
        val startPreferencesSet by store.startPreferencesSet.collectAsStateWithLifecycle(initialValue = true)
        val starterTheme by store.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
        val starterQuote by store.quoteCurrency.collectAsStateWithLifecycle(initialValue = QuoteCurrency.Default)
        val starterColours by store.marketColors.collectAsStateWithLifecycle(initialValue = MarketColorScheme.GREEN_UP)
        if (launched && !welcomeSeen) {
            WelcomeSlides(
                onStart = { scope.launch { store.setWelcomeSeen() } },
                onSignIn = { scope.launch { store.setWelcomeSeen() } },
            )
        } else if (launched && !startPreferencesSet) {
            val starterContext = LocalContext.current
            StarterPreferences(
                theme = starterTheme,
                onTheme = { mode -> scope.launch { store.setThemeMode(mode) } },
                language = AppLanguageStore.current(starterContext),
                onLanguage = { chosen ->
                    AppLanguageStore.set(starterContext, chosen)
                    (starterContext as? android.app.Activity)?.recreate()
                },
                quote = starterQuote,
                onQuote = { chosen -> scope.launch { store.setQuoteCurrency(chosen) } },
                colours = starterColours,
                onColours = { chosen -> scope.launch { store.setMarketColors(chosen) } },
                onDone = { scope.launch { store.setStartPreferencesSet() } },
            )
        } else if (launched && !readerModeChosen) {
            FirstRunQuestion(
                onChoose = { mode -> scope.launch { store.setReaderMode(mode) } },
                onSkip = { scope.launch { store.setReaderMode(ReaderMode.TRADER) } },
            )
        }
    }
}
