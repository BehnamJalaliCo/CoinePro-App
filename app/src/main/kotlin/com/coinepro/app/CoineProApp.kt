package com.coinepro.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.coinepro.app.alerts.LocalAlertScheduler
import com.coinepro.app.auth.GoogleSignInClient
import com.coinepro.app.auth.GoogleSignInOutcome
import com.coinepro.app.chart.rememberChartControllers
import com.coinepro.app.notifications.PushCoordinator
import com.coinepro.app.notifications.channelDescriptionRes
import com.coinepro.app.notifications.channelNameRes
import com.coinepro.app.security.AppIntegrity
import com.coinepro.app.sync.BackgroundSyncScheduler
import com.coinepro.core.academy.AcademyController
import com.coinepro.core.account.AccountController
import com.coinepro.core.aiassistant.AiAssistantController
import com.coinepro.core.aisignal.AiSignalController
import com.coinepro.core.aivision.AiVisionController
import com.coinepro.core.auth.EmailAuthController
import com.coinepro.core.auth.EmailAuthStep
import com.coinepro.core.auth.PlatformCapabilities
import com.coinepro.core.auth.PlatformSessions
import com.coinepro.core.auth.SessionController
import com.coinepro.core.auth.SessionState
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.copytrade.CopyTradeController
import com.coinepro.core.datastore.ActivePlatformStore
import com.coinepro.core.datastore.ChartLayout
import com.coinepro.core.datastore.ChartLayoutStore
import com.coinepro.core.datastore.LocalAlertStore
import com.coinepro.core.datastore.NotificationSettingsStore
import com.coinepro.core.datastore.ProfileStore
import com.coinepro.core.datastore.StoredProfile
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.CoineProAvatar
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProReading
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.PageAccent
import com.coinepro.core.designsystem.ProvidePageAccent
import com.coinepro.core.diagnostics.AdminController
import com.coinepro.core.diagnostics.AppLog
import com.coinepro.core.diagnostics.LogTag
import com.coinepro.core.diagnostics.Appearance
import com.coinepro.core.diagnostics.ControlHub
import com.coinepro.core.diagnostics.CrashReport
import com.coinepro.core.diagnostics.FeedStatus
import com.coinepro.core.diagnostics.HubActions
import com.coinepro.core.diagnostics.HubTone
import com.coinepro.core.diagnostics.PushPermission
import com.coinepro.core.diagnostics.PushPreferenceKey
import com.coinepro.core.diagnostics.PushStatus
import com.coinepro.core.diagnostics.ServerCapabilities
import com.coinepro.core.diagnostics.SessionRow
import com.coinepro.core.diagnostics.VenueStatus
import com.coinepro.core.execution.ConnectionsState
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.guest.GuestCandleGateway
import com.coinepro.core.guest.GuestController
import com.coinepro.core.guest.GuestGateway
import com.coinepro.core.guest.GuestMarketCatalogGateway
import com.coinepro.core.guest.GuestMembershipState
import com.coinepro.core.journal.JournalController
import com.coinepro.core.marketdata.AcademyTokenStore
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.MarketConnectionState
import com.coinepro.core.marketdata.MarketDataCache
import com.coinepro.core.marketdata.MarketDataController
import com.coinepro.core.marketdata.MarketDataState
import com.coinepro.core.marketdata.MarketDataSymbols
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.marketdata.SparklineStore
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.membership.MembershipController
import com.coinepro.core.model.AvatarSpec
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.SignalDirection
import com.coinepro.core.navigation.AppDestination
import com.coinepro.core.notifications.LocalPriceAlert
import com.coinepro.core.notifications.NotificationCategory
import com.coinepro.core.notifications.NotificationController
import com.coinepro.core.notifications.NotificationSettings
import com.coinepro.core.papertrade.PaperTradeController
import com.coinepro.core.portfolio.PortfolioController
import com.coinepro.core.script.ScriptController
import com.coinepro.core.signals.SignalController
import com.coinepro.feature.academy.AcademyScreen
import com.coinepro.feature.academy.LessonScreen
import com.coinepro.feature.account.DeleteAccountScreen
import com.coinepro.feature.activity.ActivityScreen
import com.coinepro.feature.admin.AdminScreen
import com.coinepro.feature.ai.AiStudioScreen
import com.coinepro.feature.aiassistant.AiAssistantScreen
import com.coinepro.feature.aivision.AiVisionScreen
import com.coinepro.feature.alerts.AlertsScreen
import com.coinepro.feature.auth.AuthScreen
import com.coinepro.feature.auth.EmailAuthScreen
import com.coinepro.feature.calendar.EconomicCalendarScreen
import com.coinepro.feature.chart.ChartController
import com.coinepro.feature.chart.ChartScreen
import com.coinepro.feature.chart.ChartStudioScreen
import com.coinepro.feature.connections.ConnectionsScreen
import com.coinepro.feature.copytrade.CopyTradeScreen
import com.coinepro.feature.execution.ExecutionScreen
import com.coinepro.feature.guest.GuestGate
import com.coinepro.feature.guest.GuestGateScreen
import com.coinepro.feature.guest.GuestNewsScreen
import com.coinepro.feature.guest.GuestScreen
import com.coinepro.feature.home.HomeBriefing
import com.coinepro.feature.home.HomePortfolio
import com.coinepro.feature.home.HomeScreen
import com.coinepro.feature.home.HomeSubscription
import com.coinepro.feature.home.toHomeBriefing
import com.coinepro.feature.home.toHomePortfolio
import com.coinepro.feature.home.toHomeSubscription
import com.coinepro.feature.journal.JournalScreen
import com.coinepro.feature.kyc.KycScreen
import com.coinepro.feature.membership.MembershipScreen
import com.coinepro.feature.news.NewsScreen
import com.coinepro.feature.notifications.AlertComposerSheet
import com.coinepro.feature.notifications.NotificationSection
import com.coinepro.feature.notifications.NotificationSettingsScreen
import com.coinepro.feature.papertrade.PaperTradeScreen
import com.coinepro.feature.portfolio.PortfolioScreen
import com.coinepro.feature.profile.AvatarComposerSheet
import com.coinepro.feature.profile.ProfileAction
import com.coinepro.feature.profile.ProfileScreen
import com.coinepro.feature.script.ScriptScreen
import com.coinepro.feature.search.MarketsScreen
import com.coinepro.feature.search.MarketsSignalStrip
import com.coinepro.feature.search.SearchScreen
import com.coinepro.feature.signaldetail.SignalChartController
import com.coinepro.feature.signaldetail.SignalDetailScreen
import com.coinepro.feature.signals.SignalsScreen
import com.coinepro.feature.terminal.TerminalController
import com.coinepro.feature.terminal.TerminalScreen
import com.coinepro.feature.tools.ToolsScreen
import kotlinx.coroutines.launch

private const val SIGNAL_DETAIL_PATTERN = "signal/{signalId}"
private const val EXECUTION_PATTERN = "execution/{signalId}"
private const val CONNECTIONS_ROUTE = "connections"
private const val AI_VISION_ROUTE = "ai/vision"
private const val AI_ASSISTANT_ROUTE = "ai/assistant"
private const val MARKET_SEARCH_ROUTE = "market/search"
private const val CHART_PATTERN = "chart/{symbol}"
private const val PORTFOLIO_ROUTE = "portfolio"
private const val ACADEMY_ROUTE = "academy"
private const val TERMINAL_ROUTE = "terminal"
private const val LESSON_PATTERN = "academy/lesson/{slug}"
private const val NEWS_ROUTE = "market/news"
private const val CALENDAR_ROUTE = "market/calendar"
private const val LAUNCH_READINESS_ROUTE = "launch-readiness"
private const val ADMIN_ROUTE = "diagnostics"
private const val PROFILE_ROUTE = "profile"
private const val NOTIFICATIONS_ROUTE = "notifications"
private const val MEMBERSHIP_ROUTE = "membership"
private const val KYC_ROUTE = "account/verify"
private const val DELETE_ACCOUNT_ROUTE = "account/delete"
private const val ALERTS_ROUTE = "alerts"
private const val JOURNAL_ROUTE = "journal"
private const val PAPER_TRADE_ROUTE = "paper-trade"

/**
 * Two destinations that used to be tabs.
 *
 * The routes keep their old spelling so a saved back stack and every deep link that named them
 * still land — what changed is that they are reached from Home rather than from the bar.
 */
private const val TOOLS_ROUTE = "tools"
private const val ACTIVITY_ROUTE = "activity"
private const val SCRIPT_PATTERN = "script/{symbol}"
private const val STUDIO_PATTERN = "chart/{symbol}/studio"
private fun signalDetailRoute(signalId: Long) = "signal/$signalId"
private fun executionRoute(signalId: Long) = "execution/$signalId"

/**
 * A ticker is safe in a path segment — both feeds spell them in ASCII letters and digits — but
 * encoding it costs nothing and a symbol that ever grows a slash would otherwise route nowhere.
 */
private fun chartRoute(symbol: String) = "chart/" + Uri.encode(symbol)

/**
 * The script studio, on a symbol.
 *
 * A symbol in the path rather than a screen that picks one, because every way into this screen
 * already knows which instrument the reader is looking at — the chart's toolbar, and the toolkit
 * card, which passes the first symbol on the watchlist.
 */
private fun scriptRoute(symbol: String) = "script/" + Uri.encode(symbol)

/** The chart's working surface, on a symbol. */
private fun studioRoute(symbol: String) = "chart/" + Uri.encode(symbol) + "/studio"

/**
 * Which symbol the studio opens on when it was not reached from a chart.
 *
 * The reader's own first watchlist entry, and the platform's first quoted instrument otherwise.
 * Both are real symbols on the active backend, which matters: opening the studio on a ticker this
 * platform does not carry would greet a first-time reader with an empty chart and an error.
 */
private fun defaultScriptSymbol(platform: MarketPlatform, watchlist: List<String>): String =
    watchlist.firstOrNull() ?: MarketDataSymbols.forPlatform(platform).first()

/**
 * Which domain a route belongs to.
 *
 * Analysis is anything whose whole job is reading the market — the market list, a chart, a search,
 * news, the calendar, the AI screens. Social is copy trading. Premium is nothing yet: the
 * subscription screen is not built, and listing a route here that does not exist would be a rule
 * with no case. Everything else is brand gold, which is the app acting on the reader's account.
 *
 * Home is deliberately **not** analysis, even though it carries a market list. Its hero is the
 * balance and its primary action is "generate a signal" — the screen is about the account, and the
 * one gold object on it should be the thing that acts. The market list is a passenger there; the
 * dedicated markets surface is the search route, and that one is blue.
 */
/**
 * Routes whose screen carries its own heading.
 *
 * Both visual voices define themselves by a heading — the gold one by a title over a fading rule
 * and a large figure, the terminal one by a title over its column names — so on these the app bar
 * is reduced to the back arrow.
 */
private val SELF_TITLED: Set<String> = setOf(
    CHART_PATTERN,
    STUDIO_PATTERN,
    SCRIPT_PATTERN,
    SIGNAL_DETAIL_PATTERN,
    PORTFOLIO_ROUTE,
    NEWS_ROUTE,
    CALENDAR_ROUTE,
    ACTIVITY_ROUTE,
    MARKET_SEARCH_ROUTE,
    PROFILE_ROUTE,
    NOTIFICATIONS_ROUTE,
    MEMBERSHIP_ROUTE,
)

private fun accentFor(route: String?): PageAccent = when (route) {
    MARKET_SEARCH_ROUTE,
    CHART_PATTERN,
    NEWS_ROUTE,
    CALENDAR_ROUTE,
    AI_VISION_ROUTE,
    AI_ASSISTANT_ROUTE,
    SCRIPT_PATTERN,
    STUDIO_PATTERN,
    AppDestination.MARKETS.route,
    AppDestination.CHART.route,
    AppDestination.AI.route,
    -> PageAccent.ANALYSIS

    CONNECTIONS_ROUTE -> PageAccent.SOCIAL

    else -> PageAccent.BRAND
}
private fun lessonRoute(slug: String) = "academy/lesson/" + Uri.encode(slug)

@Composable
fun CoineProApp(
    sessionController: SessionController,
    emailAuthController: EmailAuthController,
    guestController: GuestController,
    /** The public feed, which is what makes the guest experience the app rather than a teaser. */
    guestGateway: GuestGateway,
    membershipController: MembershipController,
    profileStore: ProfileStore,
    notificationSettingsStore: NotificationSettingsStore,
    localAlertStore: LocalAlertStore,
    localAlertScheduler: LocalAlertScheduler,
    watchlistStore: WatchlistStore,
    chartLayoutStore: ChartLayoutStore,
    journalController: JournalController,
    paperTradeController: PaperTradeController,
    scriptController: ScriptController,
    marketDataControllers: Map<MarketPlatform, MarketDataController>,
    marketSearchControllers: Map<MarketPlatform, MarketSearchController>,
    candleGateways: Map<MarketPlatform, CandleGateway>,
    portfolioControllers: Map<MarketPlatform, PortfolioController>,
    academyController: AcademyController,
    terminalController: TerminalController,
    accountControllers: Map<MarketPlatform, AccountController>,
    adminController: AdminController,
    /** The app's narrative log, for the crash report and the diagnostics screen. */
    appLog: AppLog,
    platformSessions: PlatformSessions,
    platformCapabilities: PlatformCapabilities,
    marketDataCache: MarketDataCache,
    activePlatformStore: ActivePlatformStore,
    signalControllers: Map<MarketPlatform, SignalController>,
    notificationControllers: Map<MarketPlatform, NotificationController>,
    executionControllers: Map<MarketPlatform, ExecutionController>,
    copyTradeControllers: Map<MarketPlatform, CopyTradeController>,
    aiSignalControllers: Map<MarketPlatform, AiSignalController>,
    aiVisionControllers: Map<MarketPlatform, AiVisionController>,
    aiAssistantController: AiAssistantController,
    marketIntelControllers: Map<MarketPlatform, MarketIntelController>,
    /** Cleared on sign-out — a derived credential that would otherwise outlive the session. */
    academyTokenStore: AcademyTokenStore,
    pushCoordinator: PushCoordinator,
    backgroundSyncScheduler: BackgroundSyncScheduler,
    launchSignalId: Long?,
    launchActivity: Boolean,
    /** Set when the recovery App Link opened the app; null on every other launch. */
    launchResetToken: String?,
    notificationPermissionState: NotificationPermissionUiState,
    onSignalLaunchConsumed: () -> Unit,
    onActivityLaunchConsumed: () -> Unit,
    onResetTokenConsumed: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSendFeedback: () -> Unit,
) {
    // Every configured platform restores, not just the one the shell happens to open on.
    //
    // Sign-in can now land on either backend — a new account is TradeYar's, an account made before
    // 1.27.0 is CoinePro-FX's — so restoring only one of them on launch would leave a returning
    // reader signed out of the app while a perfectly good session sat in storage.
    LaunchedEffect(platformSessions) { platformSessions.start() }
    val sessionStates by platformSessions.states.collectAsStateWithLifecycle(initialValue = emptyMap())
    val emailAuthState by emailAuthController.state.collectAsStateWithLifecycle()
    val loginConfigState by sessionController.loginConfigState.collectAsStateWithLifecycle()
    // Exactly one feed runs at a time. Switching platform stops the old controller before the new
    // one starts, so two sockets are never open and the screen can never blend their quotes.
    val activePlatform by activePlatformStore.active
        .collectAsStateWithLifecycle(initialValue = activePlatformStore.available.first())
    // What gates the shell: the session belonging to the platform on screen.
    //
    // It used to be the unqualified controller's, which is TradeYar's — and that is wrong the
    // moment sign-in can succeed on the other backend. A reader whose account is on CoinePro-FX
    // would have completed a sign-in, had a valid session written, and still been looking at the
    // sign-in screen, because the app was asking a server they do not have an account with.
    //
    // The fallback is for the first frame only, before the map has been collected.
    val session = sessionStates[activePlatform] ?: SessionState.Loading
    val marketDataController = marketDataControllers.getValue(activePlatform)
    val marketSearchController = marketSearchControllers.getValue(activePlatform)
    val marketState by marketDataController.state.collectAsStateWithLifecycle()
    // The account reads follow the same rule as the feed: one platform at a time, and the balance
    // on screen always belongs to the backend named above it.
    val accountController = accountControllers.getValue(activePlatform)
    // News and the calendar follow the platform for the same reason, and a stronger one: a rate
    // decision has no bearing on a listing and a token unlock has none on bullion, so the wrong
    // market's headlines are not a degraded answer but a misleading one.
    val marketIntelController = marketIntelControllers.getValue(activePlatform)
    // Everything else that reads from a backend follows the same rule: the screen belongs to the
    // platform named above it, and no controller is ever handed the other one's data.
    val signalController = signalControllers.getValue(activePlatform)
    val notificationController = notificationControllers.getValue(activePlatform)
    val executionController = executionControllers.getValue(activePlatform)
    val copyTradeController = copyTradeControllers.getValue(activePlatform)
    val aiSignalController = aiSignalControllers.getValue(activePlatform)
    val aiVisionController = aiVisionControllers.getValue(activePlatform)
    val briefingState by accountController.briefing.collectAsStateWithLifecycle()
    val portfolioState by accountController.portfolio.collectAsStateWithLifecycle()
    // Read once per briefing rather than on every recomposition, so the age is fixed at the moment
    // the briefing arrived. It is deliberately not a ticking clock: the label is coarse enough that
    // a second-by-second update would buy nothing and would be continuous motion for its own sake.
    val briefingReadAt = remember(briefingState) { System.currentTimeMillis() / 1_000 }
    val scope = rememberCoroutineScope()
    val signedIn = session is SessionState.SignedIn
    val watchlist by watchlistStore.symbols.collectAsStateWithLifecycle(initialValue = emptyList())
    // The periodic check exists only while there is something to check. A worker that wakes every
    // quarter of an hour to read an empty list is a battery cost with no possible benefit — and it
    // is exactly the kind of thing that never shows up in testing and does show up in a review.
    val storedAlerts by localAlertStore.alerts.collectAsStateWithLifecycle(initialValue = emptyList())
    LaunchedEffect(storedAlerts) {
        localAlertScheduler.sync(hasActiveAlerts = storedAlerts.any { it.active })
    }
    // Read here rather than inside the shell, because both branches need it: a guest has a profile
    // in this app and it is the same profile they keep when they sign in.
    val profile by profileStore.profile.collectAsStateWithLifecycle(initialValue = StoredProfile())

    // Sign-in, sign-out and the platform the session belongs to. No email, no token, no name: the
    // question a log answers here is *whether* there is a session and on which backend, and the
    // rest is the thing that must never be in a file attached to a crash report.
    LaunchedEffect(session) {
        appLog.info(
            tag = LogTag.AUTH,
            message = "session " + session::class.java.simpleName,
            fields = mapOf("platform" to activePlatform.name),
        )
    }
    val notificationSettings by notificationSettingsStore.settings
        .collectAsStateWithLifecycle(initialValue = NotificationSettings())
    val chartLayouts by chartLayoutStore.layouts.collectAsStateWithLifecycle(initialValue = emptyList())

    val capabilities by platformCapabilities.state.collectAsStateWithLifecycle()
    // What each deployment offers. Read once on sign-in: it is server configuration, not live
    // state, so re-reading it per screen would spend a request to be told the same thing.
    LaunchedEffect(signedIn) {
        if (signedIn) platformCapabilities.refresh() else platformCapabilities.clear()
    }
    val methods = capabilities[activePlatform]
    // Only the two flags a single server reports are assumed present when unheard. Everything else
    // stays hidden until a server has confirmed it — a button certain to fail is worse than one
    // that appears a moment late.
    val chartVisionAvailable = methods?.chartVision == true
    val pushAvailable = methods?.push == true
    val assistantAvailable = methods?.assistant ?: true
    val aiSignalsAvailable = methods?.aiSignals ?: true
    // Off unless the server said yes. A delete button that does nothing is the worst button in the
    // app; where this is false the screen shows the published out-of-app route, which works today.
    val accountDeletionAvailable = methods?.accountDeletion == true
    // Asking spends the one prompt Android grants, and it is spent for good: a reader who declines
    // is not asked again. A deployment that cannot deliver a push would spend it on nothing, and
    // one who granted it and then never heard anything has been told something untrue by the
    // request itself. Unconfigured is already the case for a build without Firebase and reads the
    // same way here, so it is reused rather than given a second name.
    val deliverablePermissionState = if (pushAvailable) {
        notificationPermissionState
    } else {
        NotificationPermissionUiState.NOT_CONFIGURED
    }

    LaunchedEffect(signedIn, activePlatform) {
        marketDataControllers.forEach { (platform, controller) ->
            if (platform != activePlatform) controller.stop()
        }
        if (signedIn) {
            marketDataController.start()
            accountController.refresh()
            pushCoordinator.registerCurrentToken()
            backgroundSyncScheduler.enableForAuthenticatedSession()
        } else {
            backgroundSyncScheduler.disable()
            marketDataController.stop()
            signalControllers.values.forEach(SignalController::clear)
            notificationControllers.values.forEach(NotificationController::clear)
            executionControllers.values.forEach(ExecutionController::clear)
            copyTradeControllers.values.forEach(CopyTradeController::clear)
            aiSignalControllers.values.forEach(AiSignalController::clear)
            aiVisionControllers.values.forEach(AiVisionController::clear)
            aiAssistantController.clear()
            marketIntelControllers.values.forEach(MarketIntelController::clear)
            // The academy token is a second credential, derived from the mobile one and held only
            // in memory. Without this it outlives the sign-out by up to twelve hours — and after a
            // *deletion* it is a live bearer for an account that no longer exists.
            academyTokenStore.clear()
        }
    }

    // The three flags the server understands, kept in step with the fifteen switches on the phone.
    //
    // It matters that this is derived rather than mirrored. Two of the categories map onto a server
    // flag and the rest are the app's own, so what is sent is the *consequence* of the reader's
    // choices — see `NotificationSettings.serverPreferences`, and in particular why one wanted
    // update keeps a flag on that two unwanted ones would otherwise turn off at the source.
    //
    // Keyed on the derived value, not the settings: flipping a switch the server has never heard of
    // must not spend a request telling it something it already knows.
    val serverPushPreferences = remember(notificationSettings) { notificationSettings.serverPreferences() }
    LaunchedEffect(signedIn, notificationController, serverPushPreferences) {
        if (signedIn) notificationController.updatePreferences(serverPushPreferences)
    }

    // Refreshed here rather than in onResume, so a platform switch reads that platform's news
    // instead of leaving the previous market's headlines under the new market's heading.
    LaunchedEffect(signedIn, marketIntelController) {
        if (signedIn) marketIntelController.refresh()
    }

    val notificationState by notificationController.state.collectAsStateWithLifecycle()
    val venueState by executionController.connections.collectAsStateWithLifecycle()
    // The shell follows the session, not a remembered preference.
    //
    // This is the second half of the sign-in fix, and it is the half that shows up as "I sign in
    // and it throws me straight back to the guest screen".
    //
    // The two backends are separate accounts with separate tokens, and the shell reads *one* of
    // them: every controller on screen is `controllers.getValue(activePlatform)`. Sign-in now
    // creates a **TradeYar** session — that is where a CoinePro account belongs, and it is what
    // fixes the mail arriving as "CoinePro Fx" — but `activePlatform` is a stored preference, and
    // on a phone that ran an earlier build it still says CoinePro-FX. The shell then opens on a
    // platform this reader has no token for, the first request comes back 401, the 401 handler
    // ends the session, and the app lands back on the guest screen. From the outside that is
    // indistinguishable from a crash, which is exactly how it was reported.
    //
    // So: if the platform on screen has no session and exactly one platform does, follow it. Only
    // when it is unambiguous — somebody signed in to both has made a real choice and this must not
    // overrule it.
    LaunchedEffect(sessionStates, activePlatform) {
        if (sessionStates[activePlatform] is SessionState.SignedIn) return@LaunchedEffect
        activePlatformStore.available
            .filter { sessionStates[it] is SessionState.SignedIn }
            .singleOrNull()
            ?.let { activePlatformStore.setActive(it) }
    }
    val context = LocalContext.current
    val googleSignIn = remember(context) { GoogleSignInClient(context) }

    // Assembled here rather than inside the diagnostics module: every controller the hub reaches is
    // already in this scope, and giving core:diagnostics a dependency on all of them would make the
    // module that observes the app depend on nearly the whole app.
    val hub = ControlHub(
        sessions = activePlatformStore.available.map { platform ->
            SessionRow(
                platform = platform,
                signedIn = sessionStates[platform] is SessionState.SignedIn,
                detail = (sessionStates[platform] as? SessionState.RevalidationRequired)?.message,
            )
        },
        feed = FeedStatus(
            tone = marketState.connection.tone(),
            label = stringResource(marketState.connection.labelRes()),
            subscribedSymbols = marketState.quotes.size,
            cacheAgeLabel = marketState.cacheStoredAtEpochMillis?.let { BidiText.isolateLtr(it.toString()) },
        ),
        push = PushStatus(
            permission = notificationPermissionState.toHubPermission(),
            // Null rather than false: the app has not asked the server yet, and reporting an
            // unasked capability as off would put words in the server's mouth.
            serverEnabled = null,
            newSignals = notificationState.preferences.newSignals,
            signalUpdates = notificationState.preferences.signalUpdates,
            priceAlerts = notificationState.preferences.priceAlerts,
        ),
        venue = venueState.forPlatform(activePlatform),
        // What each server said about itself, per platform. Null inside a row means that server
        // has not answered yet, which the panel draws differently from a capability reported off —
        // the whole point of asking is to tell those two apart.
        capabilities = capabilities.mapValues { (_, methods) ->
            ServerCapabilities(
                emailPassword = methods.emailPassword,
                google = methods.google,
                telegram = methods.telegram,
                push = methods.push,
                chartVision = methods.chartVision,
            )
        },
        appearance = Appearance(AppLanguageStore.current(context).tag),
    )

    val hubActions = HubActions(
        onSelectPlatform = { platform ->
            adminController.select(platform)
            scope.launch { activePlatformStore.setActive(platform) }
        },
        onSignOut = { platform -> scope.launch { platformSessions.logout(platform) } },
        onSignOutEverywhere = { scope.launch { platformSessions.logoutAll() } },
        onRestartFeed = marketDataController::retry,
        onSyncNow = backgroundSyncScheduler::requestImmediate,
        onClearMarketCache = { scope.launch { marketDataCache.clear() } },
        onRequestPushPermission = onRequestNotificationPermission,
        onOpenPushSettings = onOpenNotificationSettings,
        onReRegisterPushToken = { scope.launch { pushCoordinator.registerCurrentToken() } },
        onSetPushPreference = { key, value ->
            val current = notificationState.preferences
            notificationController.updatePreferences(
                when (key) {
                    PushPreferenceKey.NEW_SIGNALS -> current.copy(newSignals = value)
                    PushPreferenceKey.SIGNAL_UPDATES -> current.copy(signalUpdates = value)
                    PushPreferenceKey.PRICE_ALERTS -> current.copy(priceAlerts = value)
                },
            )
        },
        onSetLanguage = { tag ->
            AppLanguageStore.set(context, AppLanguage.fromTag(tag))
            // The locale is read in attachBaseContext, so the change lands on the next creation.
            (context as? Activity)?.recreate()
        },
        onProbe = adminController::probe,
        onToggleFailuresOnly = adminController::toggleFailuresOnly,
        onClearRequests = adminController::clearRequests,
        onCopyLog = {
            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                ClipData.newPlainText("CoinePro log", adminController.logText()),
            )
        },
    )

    CoineProTheme {
        when (val current = session) {
            is SessionState.SignedIn -> MainShell(
                guest = false,
                profile = profile,
                notificationSettingsStore = notificationSettingsStore,
                localAlertStore = localAlertStore,
                localAlertScheduler = localAlertScheduler,
                accountName = current.profile.name,
                accountEmail = current.profile.email,
                onSetDisplayName = { name -> scope.launch { profileStore.setDisplayName(name) } },
                onSetTagline = { line -> scope.launch { profileStore.setTagline(line) } },
                onSetAvatar = { spec -> scope.launch { profileStore.setAvatar(spec) } },
                onToggleBalanceHidden = {
                    scope.launch { profileStore.setBalanceHidden(!profile.balanceHidden) }
                },
                onSignIn = null,
                guestController = guestController,
                membershipController = membershipController,
                marketState = marketState,
                marketSearchController = marketSearchController,
                candleGateway = candleGateways.getValue(activePlatform),
                portfolioController = portfolioControllers.getValue(activePlatform),
                academyController = academyController,
                terminalController = terminalController,
                hasAcademy = activePlatform == MarketPlatform.COINEPRO_FX,
                adminController = adminController,
                appLog = appLog,
                hub = hub,
                hubActions = hubActions,
                briefing = briefingState.toHomeBriefing(briefingReadAt),
                portfolio = portfolioState.toHomePortfolio(),
                subscription = current.entitlement.toHomeSubscription(),
                watchlist = watchlist,
                onToggleWatch = { symbol -> scope.launch { watchlistStore.toggle(symbol) } },
                onRefreshAccount = accountController::refresh,
                signalController = signalController,
                notificationController = notificationController,
                executionController = executionController,
                copyTradeController = copyTradeController,
                aiSignalController = aiSignalController,
                aiVisionController = aiVisionController,
                aiAssistantController = aiAssistantController,
                marketIntelController = marketIntelController,
                accountController = accountController,
                launchSignalId = launchSignalId,
                launchActivity = launchActivity,
                notificationPermissionState = deliverablePermissionState,
                chartVisionAvailable = chartVisionAvailable,
                pushAvailable = pushAvailable,
                assistantAvailable = assistantAvailable,
                aiSignalsAvailable = aiSignalsAvailable,
                accountDeletionAvailable = accountDeletionAvailable,
                journalController = journalController,
                paperTradeController = paperTradeController,
                scriptController = scriptController,
                chartLayouts = chartLayouts,
                onSaveLayout = { layout -> scope.launch { chartLayoutStore.save(layout) } },
                onDeleteLayout = { name -> scope.launch { chartLayoutStore.delete(name) } },
                onSignalLaunchConsumed = onSignalLaunchConsumed,
                onActivityLaunchConsumed = onActivityLaunchConsumed,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onSendFeedback = onSendFeedback,
                onMarketRetry = marketDataController::retry,
                onSubscribeSymbols = marketDataController::subscribe,
                platforms = activePlatformStore.available,
                activePlatform = activePlatform,
                onSelectPlatform = { platform ->
                    scope.launch { activePlatformStore.setActive(platform) }
                },
                onLogout = {
                    scope.launch {
                        pushCoordinator.unregisterCurrentToken()
                        // The name and the face go with the session. The next person to open this
                        // app on this phone is not necessarily the same person, and a stranger's
                        // photograph over a stranger's name is the loudest possible way to tell
                        // them the sign-out did not work.
                        profileStore.clear()
                        // Every platform, not the one on screen. «خروج» means leaving, and a reader
                        // who holds a session on the other backend would otherwise be signed out
                        // and then silently moved onto it by the effect that follows a session.
                        platformSessions.logoutAll()
                    }
                },
            )
            // Signing in is the email flow's job now. The other two states are not sign-in at all —
            // one is a session being restored, the other a session that exists but could not be
            // revalidated — and putting credential fields in front of either would ask the reader
            // to solve a problem that is not theirs.
            SessionState.SignedOut -> {
                LaunchedEffect(emailAuthController) {
                    emailAuthController.loadMethods()
                    // Picks up a registration that was started and not finished — the reader left
                    // for their inbox and the process was killed while they were gone.
                    emailAuthController.resume()
                }
                // Arriving on a recovery link means the reader is mid-recovery, so the screen opens
                // where they left off rather than on a sign-in form they cannot yet complete.
                LaunchedEffect(launchResetToken) {
                    if (launchResetToken != null) {
                        emailAuthController.goTo(EmailAuthStep.RESET_PASSWORD)
                    }
                }

                // Signed out is not the same as unwelcome, and it is no longer a smaller app.
                //
                // This was a sign-in form, then a single scrolling page of prices in front of one.
                // Both were the same mistake at different sizes: they made somebody who had not
                // signed in look at a *preview* of the product rather than the product. What runs
                // here now is the app — the same shell, the same bottom bar, the same markets list
                // over the same several hundred instruments, the same chart on the same candles,
                // the same toolkit, the same profile with the reader's own avatar in it.
                //
                // What makes that honest rather than a trick is that TradeYar publishes the routes
                // for it. `api/v1/public/prices` is the whole universe, `api/v1/public/candles`
                // runs the *same server code* as the signed-in route — their note, and it is why
                // the numbers agree — and the news and the closed-signal record are published too.
                // So the guest is not being shown a mock: they are being shown the product, as
                // much of it as can honestly be given away.
                //
                // Two tabs need an account and say so once, without a wall and without anything
                // blurred behind them. Nobody is pushed: «به زور کسی رو ما ثبت نام نمی‌کنیم».
                //
                // Saveable, so a rotation mid-password does not throw the reader back to the
                // market with a half-typed form gone.
                var signingIn by rememberSaveable { mutableStateOf(false) }
                val showForm = signingIn || launchResetToken != null

                if (!showForm) {
                    // Built here rather than in Hilt because they are the guest's alone and their
                    // lifetime is this branch: signing in disposes them along with the shell.
                    val guestCatalog = remember(guestGateway) { GuestMarketCatalogGateway(guestGateway) }
                    val guestCandles = remember(guestGateway) { GuestCandleGateway(guestGateway) }
                    val guestSearch = remember(guestCatalog, scope) {
                        MarketSearchController(gateway = guestCatalog, scope = scope)
                    }
                    MainShell(
                        guest = true,
                        profile = profile,
                        notificationSettingsStore = notificationSettingsStore,
                        localAlertStore = localAlertStore,
                        localAlertScheduler = localAlertScheduler,
                        accountName = null,
                        accountEmail = null,
                        onSetDisplayName = { name -> scope.launch { profileStore.setDisplayName(name) } },
                        onSetTagline = { line -> scope.launch { profileStore.setTagline(line) } },
                        onSetAvatar = { spec -> scope.launch { profileStore.setAvatar(spec) } },
                        onToggleBalanceHidden = {
                            scope.launch { profileStore.setBalanceHidden(!profile.balanceHidden) }
                        },
                        onSignIn = { signingIn = true },
                        guestController = guestController,
                        membershipController = membershipController,
                        // Empty rather than the signed-in feed's, which is stopped while signed
                        // out. Nothing a guest reaches reads it: their home is the public one and
                        // their markets list reads the catalogue below.
                        marketState = MarketDataState(),
                        marketSearchController = guestSearch,
                        candleGateway = guestCandles,
                        portfolioController = portfolioControllers.getValue(activePlatform),
                        academyController = academyController,
                        terminalController = terminalController,
                        // No academy for a guest: its routes are behind the academy scope, which is
                        // minted from a mobile token nobody here holds.
                        hasAcademy = false,
                        adminController = adminController,
                        appLog = appLog,
                        hub = hub,
                        hubActions = hubActions,
                        briefing = HomeBriefing.Resting,
                        portfolio = null,
                        subscription = null,
                        watchlist = watchlist,
                        onToggleWatch = { symbol -> scope.launch { watchlistStore.toggle(symbol) } },
                        onRefreshAccount = {},
                        signalController = signalController,
                        notificationController = notificationController,
                        executionController = executionController,
                        copyTradeController = copyTradeController,
                        aiSignalController = aiSignalController,
                        aiVisionController = aiVisionController,
                        aiAssistantController = aiAssistantController,
                        marketIntelController = marketIntelController,
                        accountController = accountController,
                        launchSignalId = null,
                        launchActivity = false,
                        notificationPermissionState = deliverablePermissionState,
                        chartVisionAvailable = false,
                        pushAvailable = false,
                        assistantAvailable = false,
                        aiSignalsAvailable = false,
                        accountDeletionAvailable = false,
                        journalController = journalController,
                        paperTradeController = paperTradeController,
                        scriptController = scriptController,
                        chartLayouts = chartLayouts,
                        onSaveLayout = { layout -> scope.launch { chartLayoutStore.save(layout) } },
                        onDeleteLayout = { name -> scope.launch { chartLayoutStore.delete(name) } },
                        onSignalLaunchConsumed = onSignalLaunchConsumed,
                        onActivityLaunchConsumed = onActivityLaunchConsumed,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onSendFeedback = onSendFeedback,
                        onMarketRetry = { guestSearch.refresh() },
                        onSubscribeSymbols = {},
                        // One platform, and no switcher. The public routes are TradeYar's; a
                        // control offering CoinePro-FX would offer a feed with no public side.
                        platforms = listOf(MarketPlatform.TRADEYAR),
                        activePlatform = MarketPlatform.TRADEYAR,
                        onSelectPlatform = {},
                        onLogout = { signingIn = true },
                    )
                    return@CoineProTheme
                }

                EmailAuthScreen(
                    state = emailAuthState,
                    onSignIn = emailAuthController::signIn,
                    onRegister = emailAuthController::startRegistration,
                    onVerify = emailAuthController::verifyCode,
                    onStartOver = emailAuthController::startOver,
                    onRequestReset = emailAuthController::requestPasswordReset,
                    onResetPassword = { token, password ->
                        onResetTokenConsumed()
                        emailAuthController.resetPassword(token, password)
                    },
                    onGoTo = emailAuthController::goTo,
                    onRetryMethods = emailAuthController::loadMethods,
                    onGoogleSignIn = {
                        // The audience is the server's own client id, not one compiled in: the two
                        // deployments have separate Google configuration, and a token minted for
                        // one carries an `aud` the other refuses.
                        val audience = emailAuthState.methods.googleClientId
                        if (!audience.isNullOrBlank()) {
                            scope.launch {
                                when (val outcome = googleSignIn.requestIdToken(audience)) {
                                    is GoogleSignInOutcome.Token ->
                                        emailAuthController.signInWithGoogle(outcome.idToken)
                                    // Closing the sheet is a decision, not a failure. Saying
                                    // anything here would report a problem where there was none.
                                    GoogleSignInOutcome.Cancelled -> Unit
                                    is GoogleSignInOutcome.Failed ->
                                        emailAuthController.reportGoogleFailure(outcome.message)
                                }
                            }
                        }
                    },
                    initialResetToken = launchResetToken.orEmpty(),
                )
            }
            else -> AuthScreen(
                state = session,
                loginConfigState = loginConfigState,
                onRetryLoginConfig = { scope.launch { sessionController.prepareLogin() } },
                onRetry = { scope.launch { platformSessions.controller(activePlatform).restore() } },
                onLogout = { scope.launch { platformSessions.logoutAll() } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    /**
     * Whether nobody is signed in.
     *
     * This is the whole of the guest work in one parameter, and the point of putting it here rather
     * than in a second shell is that there is no second shell: a guest gets this Scaffold, this
     * bottom bar, this navigation graph and these screens. Five destinations change what they draw
     * — Home becomes the public one, Signals and the AI become an offer rather than a wall, the
     * toolkit hides the three cards that need a session, and the profile loses the account rows —
     * and everything else is byte-for-byte the app a member uses.
     *
     * A separate guest shell would drift within a release, and the one that drifted would be the
     * one nobody on the team looks at.
     */
    guest: Boolean,
    /** The reader's own name, face and line, whether or not they have an account. */
    profile: StoredProfile,
    notificationSettingsStore: NotificationSettingsStore,
    localAlertStore: LocalAlertStore,
    localAlertScheduler: LocalAlertScheduler,
    /** What the server calls this reader, and where to reach them. Null for a guest. */
    accountName: String?,
    accountEmail: String?,
    onSetDisplayName: (String?) -> Unit,
    onSetTagline: (String?) -> Unit,
    onSetAvatar: (AvatarSpec) -> Unit,
    /** Puts the balance behind dots, or takes it back out. */
    onToggleBalanceHidden: () -> Unit,
    /** Offered on the guest surfaces. Null when there is already a session. */
    onSignIn: (() -> Unit)?,
    /** The public feed's controller — Home and the two gates read it when [guest] is true. */
    guestController: GuestController,
    membershipController: MembershipController,
    marketState: MarketDataState,
    marketSearchController: MarketSearchController,
    /** The candle source for the platform on screen. See the chart route below. */
    candleGateway: CandleGateway,
    portfolioController: PortfolioController,
    academyController: AcademyController,
    terminalController: TerminalController,
    /**
     * Whether this platform has an academy at all.
     *
     * CoinePro-FX does; TradeYar has no `/academy` surface. Offering the entry on both and letting
     * the second fail would report an absent feature as an outage.
     */
    hasAcademy: Boolean,
    signalController: SignalController,
    notificationController: NotificationController,
    executionController: ExecutionController,
    copyTradeController: CopyTradeController,
    aiSignalController: AiSignalController,
    aiVisionController: AiVisionController,
    aiAssistantController: AiAssistantController,
    marketIntelController: MarketIntelController,
    accountController: AccountController,
    adminController: AdminController,
    appLog: AppLog,
    hub: ControlHub,
    hubActions: HubActions,
    briefing: HomeBriefing,
    portfolio: HomePortfolio?,
    subscription: HomeSubscription?,
    onRefreshAccount: () -> Unit,
    launchSignalId: Long?,
    launchActivity: Boolean,
    notificationPermissionState: NotificationPermissionUiState,
    /** What this deployment reports it can do. A feature it does not offer is not drawn. */
    chartVisionAvailable: Boolean,
    pushAvailable: Boolean,
    assistantAvailable: Boolean,
    aiSignalsAvailable: Boolean,
    accountDeletionAvailable: Boolean,
    journalController: JournalController,
    paperTradeController: PaperTradeController,
    scriptController: ScriptController,
    chartLayouts: List<ChartLayout>,
    onSaveLayout: (ChartLayout) -> Unit,
    onDeleteLayout: (String) -> Unit,
    watchlist: List<String>,
    onToggleWatch: (String) -> Unit,
    onSignalLaunchConsumed: () -> Unit,
    onActivityLaunchConsumed: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSendFeedback: () -> Unit,
    onMarketRetry: () -> Unit,
    /** Narrows the live price feed to what the markets list is showing. */
    onSubscribeSymbols: (Set<String>) -> Unit = {},
    onLogout: () -> Unit,
    platforms: List<MarketPlatform>,
    activePlatform: MarketPlatform,
    onSelectPlatform: (MarketPlatform) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val sparklineScope = rememberCoroutineScope()
    // Keyed on the gateway, so switching platform builds a new store rather than drawing a
    // forex line beside a crypto price. The scope is the composition's: leaving the app cancels
    // whatever is in flight.
    val sparklineStore = remember(candleGateway) { SparklineStore(candleGateway, sparklineScope) }
    // The charts, held here rather than inside their own destinations. See `ChartControllers`:
    // one controller per destination is what made every drawing tool in the app inert.
    val chartControllers = rememberChartControllers(candleGateway, sparklineScope)
    val currentRoute = backStackEntry?.destination?.route

    // Every screen the reader reaches, in sequence. It is two lines and it is the single most
    // useful thing in a bug report: "it crashed" becomes "it crashed on the chart, having come
    // from search, forty seconds after a socket drop".
    //
    // The route *pattern* is logged, never the filled path — `chart/{symbol}` and not
    // `chart/BTCUSDT`. A path carries arguments, and an argument on some other route is an id or
    // an email; the pattern says which screen without ever saying whose data.
    LaunchedEffect(currentRoute) {
        currentRoute?.let { appLog.info(LogTag.NAVIGATION, it) }
    }
    val isSubScreen = currentRoute in setOf(
        SIGNAL_DETAIL_PATTERN,
        EXECUTION_PATTERN,
        CONNECTIONS_ROUTE,
        MARKET_SEARCH_ROUTE,
        CHART_PATTERN,
        PROFILE_ROUTE,
        NOTIFICATIONS_ROUTE,
        MEMBERSHIP_ROUTE,
        PORTFOLIO_ROUTE,
        ACADEMY_ROUTE,
        LESSON_PATTERN,
        TERMINAL_ROUTE,
        AI_VISION_ROUTE,
        AI_ASSISTANT_ROUTE,
        KYC_ROUTE,
        DELETE_ACCOUNT_ROUTE,
        ALERTS_ROUTE,
        JOURNAL_ROUTE,
        PAPER_TRADE_ROUTE,
        SCRIPT_PATTERN,
        STUDIO_PATTERN,
        // Tools, Activity, News and the calendar are **not** sub-screens and used to be listed
        // here. A sub-screen loses the bottom bar, which is right for a chart or a lesson — a
        // place you are inside and leave by going back. These four are places a reader *goes*,
        // and stripping the bar made Tools in particular a dead end: it fans out to eight
        // destinations, so a reader who wanted Markets from there had to go back first. That is
        // most of why the toolkit felt buried.
        LAUNCH_READINESS_ROUTE,
        ADMIN_ROUTE,
    )
    val subTitleRes = when (currentRoute) {
        ADMIN_ROUTE -> R.string.screen_diagnostics
        SIGNAL_DETAIL_PATTERN -> R.string.screen_signal_detail
        EXECUTION_PATTERN -> R.string.screen_execution
        // Named for what the screen actually is on each platform. "Connections" over a
        // copy-trading screen is not wrong so much as unhelpful: it is the reader's word for the
        // wrong feature.
        CONNECTIONS_ROUTE -> when (activePlatform) {
            MarketPlatform.COINEPRO_FX -> R.string.screen_copy_trading
            MarketPlatform.TRADEYAR -> R.string.screen_connections
        }
        PROFILE_ROUTE -> R.string.screen_profile
        NOTIFICATIONS_ROUTE -> R.string.screen_notifications
        MEMBERSHIP_ROUTE -> R.string.screen_membership
        KYC_ROUTE -> R.string.screen_kyc
        DELETE_ACCOUNT_ROUTE -> R.string.screen_delete_account
        ALERTS_ROUTE -> R.string.screen_alerts
        JOURNAL_ROUTE -> R.string.screen_journal
        PAPER_TRADE_ROUTE -> R.string.screen_paper_trade
        SCRIPT_PATTERN -> R.string.screen_script
        STUDIO_PATTERN -> R.string.screen_chart_studio
        TOOLS_ROUTE -> R.string.screen_tools
        ACTIVITY_ROUTE -> R.string.screen_activity
        AI_VISION_ROUTE -> R.string.screen_ai_vision
        AI_ASSISTANT_ROUTE -> R.string.screen_ai_assistant
        MARKET_SEARCH_ROUTE -> R.string.screen_market_search
        // The chart names itself: its header is the symbol, which is more use than the word
        // "chart" over a screen that is obviously one.
        CHART_PATTERN -> R.string.screen_chart
        PORTFOLIO_ROUTE -> R.string.screen_portfolio
        // The lesson names itself in its own heading, so the bar carries the section instead.
        ACADEMY_ROUTE, LESSON_PATTERN -> R.string.screen_academy
        TERMINAL_ROUTE -> R.string.screen_terminal
        NEWS_ROUTE -> R.string.screen_news
        CALENDAR_ROUTE -> R.string.screen_calendar
        LAUNCH_READINESS_ROUTE -> R.string.screen_launch_readiness
        else -> R.string.app_name
    }
    // Home draws its own header — the greeting and the balance are the page's title — so a bar on
    // top of it would be a second one saying less.
    val showTopBar = isSubScreen || currentRoute != AppDestination.HOME.route

    LaunchedEffect(launchSignalId) {
        launchSignalId?.let { signalId ->
            navController.navigate(signalDetailRoute(signalId)) { launchSingleTop = true }
            onSignalLaunchConsumed()
        }
    }
    LaunchedEffect(launchActivity) {
        if (launchActivity) {
            navController.navigate(ACTIVITY_ROUTE) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            onActivityLaunchConsumed()
        }
    }

    Scaffold(
        containerColor = CoineProColors.Stage,
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    // A screen that draws its own heading gets a bar with nothing in it but the way
                    // back. Otherwise the reader is told what they are looking at twice — once by
                    // the bar and once by the page — and the duplicate costs the fifty-six points
                    // that the page's own heading needs.
                    title = {
                        if (currentRoute !in SELF_TITLED) Text(stringResource(subTitleRes))
                    },
                    navigationIcon = {
                        if (isSubScreen) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(
                                    painter = painterResource(CoineProIcons.Back),
                                    contentDescription = stringResource(R.string.action_back),
                                    tint = CoineProColors.TextPrimary,
                                )
                            }
                        }
                    },
                    actions = {
                        // One control where there were two text buttons.
                        //
                        // «ایمنی» and «خروج» were a pair of words in the corner of every screen,
                        // and neither is something a reader reaches for often. What they do want in
                        // that corner is themselves — so the corner is now the avatar, and safety,
                        // sign-out, verification and deletion are rows on the page it opens. A
                        // guest gets the same control with the same avatar; what differs is that
                        // their page offers an account instead of listing one.
                        if (!isSubScreen) {
                            IconButton(onClick = { navController.navigate(PROFILE_ROUTE) }) {
                                CoineProAvatar(
                                    spec = profile.avatar,
                                    initial = (profile.displayName ?: accountName)?.take(1) ?: "",
                                    size = 30.dp,
                                    contentDescription = stringResource(R.string.screen_profile),
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = CoineProColors.Stage,
                        titleContentColor = CoineProColors.TextPrimary,
                        actionIconContentColor = CoineProColors.TextSecondary,
                    ),
                )
            }
        },
        bottomBar = {
            if (!isSubScreen) {
                CoineProBottomBar(
                    currentRoute = currentRoute,
                    onSelect = { destination ->
                        navController.navigate(destination.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        // The accent for whatever is on screen, set once here rather than by every screen.
        //
        // Wrapping the NavHost rather than each destination means a screen cannot forget: the
        // accent is a property of the route, and the route is what changed. Anything not named
        // below is brand gold, which is the right default for the parts of the app that act on an
        // account rather than analyse a market.
        ProvidePageAccent(accentFor(currentRoute)) {
        NavHost(
            navController = navController,
            startDestination = AppDestination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestination.HOME.route) {
                // The same tab, two homes. A guest's is the public feed's — real prices, the real
                // track record, the real headlines — with their own avatar at the top of it, and
                // the market rows open the same chart a member's do.
                if (guest) {
                    GuestScreen(
                        controller = guestController,
                        onSignIn = onSignIn ?: {},
                        avatar = profile.avatar,
                        onOpenProfile = { navController.navigate(PROFILE_ROUTE) },
                        onOpenSymbol = { navController.navigate(chartRoute(it)) },
                        onOpenMarket = { navController.navigate(AppDestination.MARKETS.route) },
                        onOpenTools = { navController.navigate(TOOLS_ROUTE) },
                        onOpenNews = { navController.navigate(NEWS_ROUTE) },
                    )
                    return@composable
                }
                // The account's own equity, from the same closed trades the portfolio screen
                // charts. Started here because Home is where it is drawn and a controller nobody
                // started has no history to hand over; `start()` is a no-op after the first call.
                LaunchedEffect(portfolioController) { portfolioController.start() }
                val equityState by portfolioController.state.collectAsStateWithLifecycle()

                HomeScreen(
                    state = marketState,
                    // The reader's own name if they chose one, the server's otherwise. Without this
                    // the greeting row does not render at all — and with it goes the avatar, which
                    // is Home's only way into the account.
                    displayName = profile.displayName ?: accountName,
                    watchlist = watchlist,
                    onToggleWatch = onToggleWatch,
                    onVisibleSymbols = onSubscribeSymbols,
                    onOpenSymbol = { navController.navigate(chartRoute(it)) },
                    briefing = briefing,
                    portfolio = portfolio?.copy(
                        equity = equityState.stats.equity.map { it.equity },
                    ),
                    subscription = subscription,
                    onRetry = {
                        onMarketRetry()
                        onRefreshAccount()
                    },
                    // Both pills lead to the AI section, which is where the work actually happens.
                    onOpenTools = { navController.navigate(TOOLS_ROUTE) },
                    onOpenActivity = { navController.navigate(ACTIVITY_ROUTE) },
                    onOpenNews = { navController.navigate(NEWS_ROUTE) },
                    onGenerateSignal = { navController.navigate(AppDestination.AI.route) },
                    // Chart analysis is optional per deployment. Sending the reader to a screen the
                    // server has switched off is a wait that ends in an error every time, so the
                    // action falls back to the AI studio the server does serve.
                    onSendChart = {
                        navController.navigate(
                            if (chartVisionAvailable) AI_VISION_ROUTE else AppDestination.AI.route,
                        )
                    },
                    // The market card's own destination is the market list, not the signals
                    // feed. They were the same route while the app knew eight markets and there
                    // was no list worth opening.
                    onOpenMarket = { navController.navigate(MARKET_SEARCH_ROUTE) },
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
                    // Home carries no top bar, so the avatar is the way into the account — and it
                    // now opens the profile page rather than a four-item dropdown.
                    avatar = profile.avatar,
                    onOpenProfile = { navController.navigate(PROFILE_ROUTE) },
                    platforms = platforms,
                    activePlatform = activePlatform,
                    onSelectPlatform = onSelectPlatform,
                    balanceHidden = profile.balanceHidden,
                    onToggleBalanceHidden = onToggleBalanceHidden,
                    onOpenPortfolio = { navController.navigate(PORTFOLIO_ROUTE) },
                )
            }
            composable(ADMIN_ROUTE) {
                val adminState by adminController.state.collectAsStateWithLifecycle()
                AdminScreen(
                    state = adminState,
                    hub = hub,
                    actions = hubActions,
                )
            }
            composable(AppDestination.SIGNALS.route) {
                if (guest) {
                    GuestGateScreen(
                        gate = GuestGate.SIGNALS,
                        controller = guestController,
                        onSignIn = onSignIn ?: {},
                    )
                    return@composable
                }
                SignalsScreen(
                    controller = signalController,
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
                    platform = activePlatform,
                )
            }
            composable(
                route = SIGNAL_DETAIL_PATTERN,
                arguments = listOf(navArgument("signalId") { type = NavType.LongType }),
            ) { entry ->
                val signalId = entry.arguments?.getLong("signalId") ?: return@composable
                val chartScope = rememberCoroutineScope()
                val signalChartController = remember(candleGateway) {
                    SignalChartController(gateway = candleGateway, scope = chartScope)
                }
                SignalDetailScreen(
                    controller = signalController,
                    marketIntelController = marketIntelController,
                    signalId = signalId,
                    chartController = signalChartController,
                    // Null where the platform places no orders. CoinePro-FX is the case: its
                    // signals reach a reader's account through copy trading, so the button that
                    // used to sit here led to a screen that could only say the feature was absent.
                    onExecute = if (activePlatform == MarketPlatform.TRADEYAR) {
                        { id -> navController.navigate(executionRoute(id)) }
                    } else {
                        null
                    },
                    onOpenCopyTrading = if (activePlatform == MarketPlatform.COINEPRO_FX) {
                        { navController.navigate(CONNECTIONS_ROUTE) }
                    } else {
                        null
                    },
                )
            }
            composable(
                route = EXECUTION_PATTERN,
                arguments = listOf(navArgument("signalId") { type = NavType.LongType }),
            ) { entry ->
                val signalId = entry.arguments?.getLong("signalId") ?: return@composable
                ExecutionScreen(
                    signalId = signalId,
                    signalController = signalController,
                    executionController = executionController,
                    onOpenConnections = { navController.navigate(CONNECTIONS_ROUTE) },
                )
            }
            composable(PROFILE_ROUTE) {
                // The one page in the app that is about the reader rather than about a market.
                //
                // A guest reaches it from the same corner, sees the same hero, edits the same
                // avatar and keeps the same name — what they do not get is the account rows,
                // because there is no account to act on. They get the offer instead, once.
                var composing by rememberSaveable { mutableStateOf(false) }
                val initial = (profile.displayName ?: accountName)?.trim()?.take(1)
                    ?: stringResource(R.string.profile_initial_fallback)

                ProfileScreen(
                    profile = profile,
                    accountName = accountName,
                    email = accountEmail,
                    guest = guest,
                    planLabel = subscription?.planLabel,
                    platformLabel = stringResource(activePlatform.labelRes()),
                    // Three figures, and every one of them is about this reader: what they chose
                    // to watch, what they wrote down, what they practised. No market number
                    // belongs on this page.
                    readings = listOf(
                        CoineProReading(
                            label = stringResource(R.string.profile_reading_watchlist),
                            value = watchlist.size.toPersianDigits(),
                        ),
                    ),
                    onEditAvatar = { composing = true },
                    onSetDisplayName = onSetDisplayName,
                    onSetTagline = onSetTagline,
                    onSignIn = onSignIn.takeIf { guest },
                    actions = if (guest) {
                        // Safety is offered to a guest too. It is where the crash report and the
                        // version live, and a reader who has hit a bug should not have to make an
                        // account to tell us about it.
                        listOf(
                            // Offered to a guest too, and not as a courtesy: their price alerts
                            // run on this device and need no account, so the screen is as real
                            // for them as it is for a member.
                            ProfileAction(
                                label = stringResource(R.string.screen_notifications),
                                note = stringResource(R.string.profile_action_notifications_note),
                                icon = CoineProIcons.Bell,
                                onClick = { navController.navigate(NOTIFICATIONS_ROUTE) },
                            ),
                            ProfileAction(
                                label = stringResource(R.string.profile_action_safety),
                                note = stringResource(R.string.profile_action_safety_note),
                                icon = CoineProIcons.Secure,
                                onClick = { navController.navigate(LAUNCH_READINESS_ROUTE) },
                            ),
                        )
                    } else {
                        buildList {
                            add(
                                ProfileAction(
                                    label = stringResource(R.string.screen_membership),
                                    note = stringResource(R.string.profile_action_membership_note),
                                    icon = CoineProIcons.Wallet,
                                    onClick = { navController.navigate(MEMBERSHIP_ROUTE) },
                                ),
                            )
                            add(
                                ProfileAction(
                                    label = stringResource(R.string.profile_action_verification),
                                    icon = CoineProIcons.IdentityCard,
                                    onClick = { navController.navigate(KYC_ROUTE) },
                                ),
                            )
                            add(
                                ProfileAction(
                                    label = stringResource(R.string.screen_notifications),
                                    note = stringResource(R.string.profile_action_notifications_note),
                                    icon = CoineProIcons.Bell,
                                    onClick = { navController.navigate(NOTIFICATIONS_ROUTE) },
                                ),
                            )
                            add(
                                ProfileAction(
                                    label = stringResource(R.string.profile_action_alerts),
                                    icon = CoineProIcons.Alarm,
                                    onClick = { navController.navigate(ALERTS_ROUTE) },
                                ),
                            )
                            add(
                                ProfileAction(
                                    label = stringResource(R.string.profile_action_safety),
                                    note = stringResource(R.string.profile_action_safety_note),
                                    icon = CoineProIcons.Secure,
                                    onClick = { navController.navigate(LAUNCH_READINESS_ROUTE) },
                                ),
                            )
                            add(
                                ProfileAction(
                                    label = stringResource(R.string.action_logout),
                                    icon = CoineProIcons.SignOut,
                                    onClick = onLogout,
                                ),
                            )
                            // Last, and drawn in the refusal colour. It is the one row here that
                            // cannot be undone.
                            add(
                                ProfileAction(
                                    label = stringResource(R.string.profile_action_delete),
                                    destructive = true,
                                    icon = CoineProIcons.Delete,
                                    onClick = { navController.navigate(DELETE_ACCOUNT_ROUTE) },
                                ),
                            )
                        }
                    },
                )

                if (composing) {
                    AvatarComposerSheet(
                        current = profile.avatar,
                        initial = initial,
                        onSave = { spec ->
                            onSetAvatar(spec)
                            composing = false
                        },
                        onDismiss = { composing = false },
                    )
                }
            }
            composable(MEMBERSHIP_ROUTE) {
                // The screen that step three of the membership card has always pointed at and
                // that nothing in the app reached. A reader could register on an exchange, fund
                // it, and then find no way to tell CoinePro their UID — which is the one step the
                // whole arrangement turns on.
                //
                // Which exchanges accept a UID is the server's answer, not a constant here: it is
                // deliberately a superset of the copy-trading list, because Ourbit earns membership
                // and is never traded on. Compiling either list in would eventually offer somebody
                // an exchange the platform had stopped taking.
                val terms by guestController.membership.collectAsStateWithLifecycle()
                // Asked for here, not assumed. The terms come from a public route that only the
                // guest home polls, and a signed-in reader never renders that screen — so without
                // this the exchange picker would be empty for exactly the people who need it.
                LaunchedEffect(guestController) { guestController.refreshMembership() }
                MembershipScreen(
                    controller = membershipController,
                    uidExchanges = (terms as? GuestMembershipState.Ready)
                        ?.terms
                        ?.uidExchanges
                        .orEmpty(),
                )
            }
            composable(NOTIFICATIONS_ROUTE) {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val current by notificationSettingsStore.settings
                    .collectAsStateWithLifecycle(initialValue = NotificationSettings())
                val localAlerts by localAlertStore.alerts
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                var composing by rememberSaveable { mutableStateOf(false) }

                // Only the categories that can ever fire for this reader. A guest shown a switch
                // for "a copy trade opened" is being offered control over something that cannot
                // happen to them, which makes the whole screen read as decoration.
                val sections = notificationSections(guest = guest)

                NotificationSettingsScreen(
                    settings = current,
                    sections = sections,
                    alerts = localAlerts,
                    systemPermissionGranted =
                        notificationPermissionState != NotificationPermissionUiState.DENIED,
                    onOpenSystemSettings = onOpenNotificationSettings,
                    onSetEnabled = { on -> scope.launch { notificationSettingsStore.setEnabled(on) } },
                    onSetCategory = { category, on ->
                        scope.launch { notificationSettingsStore.setCategory(category, on) }
                    },
                    onSetQuietHours = { on, from, to ->
                        scope.launch { notificationSettingsStore.setQuietHours(on, from, to) }
                    },
                    onAddAlert = { composing = true },
                    onToggleAlert = { alert, active ->
                        scope.launch {
                            localAlertStore.setActive(alert.id, active)
                            localAlertScheduler.sync(hasActiveAlerts = active || localAlerts.any { it.active && it.id != alert.id })
                        }
                    },
                    onDeleteAlert = { alert ->
                        scope.launch {
                            localAlertStore.remove(alert.id)
                            localAlertScheduler.sync(hasActiveAlerts = localAlerts.any { it.active && it.id != alert.id })
                        }
                    },
                    labelFor = { category -> context.getString(category.channelNameRes()) },
                    noteFor = { category -> context.getString(category.channelDescriptionRes()) },
                )

                if (composing) {
                    // The reader's own first market, or the platform's — the same rule the chart
                    // tab and the script studio already follow, so "new alert" never opens on a
                    // ticker this backend does not carry.
                    val symbol = defaultScriptSymbol(activePlatform, watchlist)
                    AlertComposerSheet(
                        symbol = symbol,
                        currentPrice = marketState.quotes[symbol]?.price,
                        full = localAlerts.size >= LocalPriceAlert.MAX_ALERTS,
                        onCreate = { alert ->
                            scope.launch {
                                localAlertStore.add(alert)
                                localAlertScheduler.sync(hasActiveAlerts = true)
                            }
                            composing = false
                        },
                        onDismiss = { composing = false },
                    )
                }
            }
            composable(KYC_ROUTE) {
                KycScreen(controller = accountController)
            }
            composable(PAPER_TRADE_ROUTE) {
                PaperTradeScreen(
                    controller = paperTradeController,
                    // The same feed the market list is showing. A second source would let this
                    // screen and the row above it disagree about one instrument's price.
                    priceFor = { symbol -> marketState.quotes[symbol]?.price },
                )
            }
            composable(JOURNAL_ROUTE) {
                JournalScreen(controller = journalController)
            }
            composable(ALERTS_ROUTE) {
                AlertsScreen(controller = notificationController)
            }
            composable(DELETE_ACCOUNT_ROUTE) {
                DeleteAccountScreen(
                    controller = accountController,
                    supported = accountDeletionAvailable,
                    // Signing out on the way rather than after: the token the app is holding names
                    // an account that no longer exists, and the next request with it would be
                    // answered 401 and reported to the reader as an expired session.
                    onDeleted = onLogout,
                )
            }
            composable(CONNECTIONS_ROUTE) {
                // One route, two entirely different surfaces, because the two products are
                // different: TradeYar takes an exchange key and places orders per signal, while
                // CoinePro-FX links a broker account once and a service trades it. Sending an FX
                // reader to the venue form gave them a screen whose every field was answered by a
                // 404, worded as though the connection had failed.
                when (activePlatform) {
                    MarketPlatform.COINEPRO_FX -> CopyTradeScreen(controller = copyTradeController)
                    MarketPlatform.TRADEYAR ->
                        ConnectionsScreen(controller = executionController, platform = activePlatform)
                }
            }
            composable(AppDestination.AI.route) {
                if (guest) {
                    GuestGateScreen(
                        gate = GuestGate.AI,
                        controller = guestController,
                        onSignIn = onSignIn ?: {},
                    )
                    return@composable
                }
                // AiStudioScreen, not the older AiScreen: the two carried the same generator, and
                // only this one shows the evidence the server returns alongside the verdict.
                AiStudioScreen(
                    controller = aiSignalController,
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
                    chartVisionAvailable = chartVisionAvailable,
                    assistantAvailable = assistantAvailable,
                    aiSignalsAvailable = aiSignalsAvailable,
                    onOpenChartAnalysis = { navController.navigate(AI_VISION_ROUTE) },
                    onOpenAssistant = { navController.navigate(AI_ASSISTANT_ROUTE) },
                    platform = activePlatform,
                )
            }
            composable(AI_VISION_ROUTE) {
                AiVisionScreen(
                    controller = aiVisionController,
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
                )
            }
            composable(AI_ASSISTANT_ROUTE) {
                AiAssistantScreen(
                    controller = aiAssistantController,
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
                    available = assistantAvailable,
                )
            }
            composable(AppDestination.MARKETS.route) {
                val signals by signalController.state.collectAsStateWithLifecycle()
                MarketsScreen(
                    controller = marketSearchController,
                    sparklines = sparklineStore,
                    watchlist = watchlist,
                    onOpenSymbol = { symbol -> navController.navigate(chartRoute(symbol)) },
                    onOpenSearch = { navController.navigate(MARKET_SEARCH_ROUTE) },
                    // Only when there is something to say. A strip reading «۰ سیگنال باز» is a row
                    // of chrome reporting the absence of news.
                    openSignals = signals.items.takeIf { it.isNotEmpty() }?.let { open ->
                        MarketsSignalStrip(
                            count = open.size,
                            summary = open.take(2).joinToString(" · ") { signal ->
                                signal.symbol + " " + if (signal.direction == SignalDirection.BUY) "خرید" else "فروش"
                            },
                            onClick = { navController.navigate(AppDestination.SIGNALS.route) },
                        )
                    },
                )
            }
            composable(AppDestination.CHART.route) {
                // The tab opens the reader's own first market, or the platform's first quoted one.
                // A tab that asked which symbol before showing anything would be a picker with a
                // chart behind it, which is not what a chart tab is for.
                val symbol = defaultScriptSymbol(activePlatform, watchlist)
                LaunchedEffect(symbol) {
                    navController.navigate(chartRoute(symbol)) {
                        popUpTo(AppDestination.CHART.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
            composable(MARKET_SEARCH_ROUTE) {
                SearchScreen(
                    watchlist = watchlist,
                    onToggleWatch = onToggleWatch,
                    controller = marketSearchController,
                    onOpenSymbol = { navController.navigate(chartRoute(it)) },
                )
            }
            composable(
                route = CHART_PATTERN,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType }),
            ) { entry ->
                val symbol = entry.arguments?.getString("symbol").orEmpty()
                val chartController = chartControllers.controllerFor(symbol)
                ChartScreen(
                    layouts = chartLayouts,
                    onSaveLayout = onSaveLayout,
                    onDeleteLayout = onDeleteLayout,
                    watchlist = watchlist,
                    onPaperTrade = { symbol, buy, entry, size ->
                        paperTradeController.open(symbol, buy, entry, size)
                    },
                    onSelectSymbol = { symbol ->
                        // Replaces the chart rather than stacking one on top of another: flipping
                        // through six symbols must not build a six-deep back stack that takes six
                        // presses to leave.
                        navController.navigate(chartRoute(symbol)) {
                            popUpTo(CHART_PATTERN) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    controller = chartController,
                    onOpenStudio = { navController.navigate(studioRoute(symbol)) },
                    onOpenTerminal = if (terminalController.isConfigured) {
                        { navController.navigate(TERMINAL_ROUTE) }
                    } else {
                        null
                    },
                )
            }
            composable(
                route = SCRIPT_PATTERN,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType }),
            ) { entry ->
                val symbol = entry.arguments?.getString("symbol").orEmpty()
                val scope = rememberCoroutineScope()
                // A chart controller purely to fetch bars: the studio draws its own preview, and
                // reusing the chart's loader means the studio's candles and the chart's candles
                // come from one place. A second fetcher here would be a second thing to keep in
                // step with paging, timeframes and the academy-token failure modes.
                val previewController = remember(symbol, candleGateway) {
                    ChartController(symbol = symbol, gateway = candleGateway, scope = scope)
                }
                val previewState by previewController.state.collectAsStateWithLifecycle()
                LaunchedEffect(previewController) { previewController.start() }
                ScriptScreen(
                    controller = scriptController,
                    symbol = symbol,
                    series = previewState.series,
                    loading = previewState.loading,
                )
            }
            composable(
                route = STUDIO_PATTERN,
                arguments = listOf(navArgument("symbol") { type = NavType.StringType }),
            ) { entry ->
                val symbol = entry.arguments?.getString("symbol").orEmpty()
                // The chart's own controller, not a second one. Two instances is what made the
                // studio useless: arming a tool, toggling an indicator or choosing a chart type
                // wrote to an object the chart could not see, and the studio's copy was thrown
                // away on the way back. See `ChartControllers`.
                val studioController = chartControllers.controllerFor(symbol)
                LaunchedEffect(studioController) { studioController.start() }
                ChartStudioScreen(
                    controller = studioController,
                    layouts = chartLayouts,
                    onSaveLayout = onSaveLayout,
                    onDeleteLayout = onDeleteLayout,
                    onOpenScript = { navController.navigate(scriptRoute(symbol)) },
                    onBackToChart = { navController.popBackStack() },
                )
            }
            composable(NEWS_ROUTE) {
                // A guest reads the public headline route, which needs no account and which their
                // own home screen was already showing twelve of. Pointing them at the members'
                // screen would hand them a 401 worded as an outage, on content the server
                // publishes to anybody.
                if (guest) {
                    GuestNewsScreen(controller = guestController)
                    return@composable
                }
                NewsScreen(
                    platform = activePlatform,
                    controller = marketIntelController,
                    onOpenCalendar = { navController.navigate(CALENDAR_ROUTE) },
                )
            }
            composable(CALENDAR_ROUTE) {
                EconomicCalendarScreen(
                    controller = marketIntelController,
                    onOpenNews = { navController.navigate(NEWS_ROUTE) },
                )
            }
            composable(TOOLS_ROUTE) {
                ToolsScreen(
                    onOpenJournal = { navController.navigate(JOURNAL_ROUTE) },
                    onOpenPaperTrade = { navController.navigate(PAPER_TRADE_ROUTE) },
                    // The studio needs a symbol to run against, and from here there is no chart to
                    // take one from. The watchlist's first entry is the reader's own choice; the
                    // platform's default is the fallback for somebody who has not made one yet.
                    onOpenScript = {
                        navController.navigate(scriptRoute(defaultScriptSymbol(activePlatform, watchlist)))
                    },
                    platform = activePlatform,
                    // Null for a guest, on the three that read a signed-in route. Everything else
                    // on this screen is local to the phone and opens for anybody.
                    onOpenConnections = if (guest) null else ({ navController.navigate(CONNECTIONS_ROUTE) }),
                    onOpenNews = { navController.navigate(NEWS_ROUTE) },
                    onOpenCalendar = if (guest) null else ({ navController.navigate(CALENDAR_ROUTE) }),
                    onOpenPortfolio = if (guest) null else ({ navController.navigate(PORTFOLIO_ROUTE) }),
                    onOpenAcademy = if (hasAcademy && !guest) {
                        { navController.navigate(ACADEMY_ROUTE) }
                    } else {
                        null
                    },
                )
            }
            composable(TERMINAL_ROUTE) {
                TerminalScreen(
                    controller = terminalController,
                    onClose = { navController.popBackStack() },
                )
            }
            composable(ACADEMY_ROUTE) {
                AcademyScreen(
                    controller = academyController,
                    onOpenLesson = { navController.navigate(lessonRoute(it)) },
                    onOpenProfile = { navController.navigate(KYC_ROUTE) },
                )
            }
            composable(
                route = LESSON_PATTERN,
                arguments = listOf(navArgument("slug") { type = NavType.StringType }),
            ) { entry ->
                LessonScreen(
                    controller = academyController,
                    slug = entry.arguments?.getString("slug").orEmpty(),
                    onClose = { navController.popBackStack() },
                    onOpenProfile = { navController.navigate(KYC_ROUTE) },
                )
            }
            composable(PORTFOLIO_ROUTE) {
                PortfolioScreen(
                    controller = portfolioController,
                    onOpenConnections = { navController.navigate(CONNECTIONS_ROUTE) },
                )
            }
            composable(ACTIVITY_ROUTE) {
                ActivityScreen(
                    onOpenAlerts = { navController.navigate(ALERTS_ROUTE) },
                    controller = notificationController,
                    executionController = executionController,
                    signalController = signalController,
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
                    platform = activePlatform,
                )
            }
            composable(LAUNCH_READINESS_ROUTE) {
                val context = LocalContext.current
                val crashes = remember(context, appLog) { CrashReport(context, appLog) }
                // Read once: an install's certificate cannot change while it is running.
                val fingerprints = remember(context) {
                    listOf(
                        "SHA-1" to AppIntegrity.fingerprints(context, "SHA-1").firstOrNull(),
                        "SHA-256" to AppIntegrity.fingerprints(context, "SHA-256").firstOrNull(),
                    ).mapNotNull { (algorithm, value) -> value?.let { algorithm to it } }
                }
                // Read once per visit rather than watched: a crash file cannot change while the
                // app that would write it is the one on screen.
                var lastCrash by remember { mutableStateOf(crashes.last()) }
                LaunchReadinessScreen(
                    notificationPermissionState = notificationPermissionState,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onSendFeedback = onSendFeedback,
                    versionLabel = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    onOpenDiagnostics = { navController.navigate(ADMIN_ROUTE) },
                    lastCrash = lastCrash,
                    onCopyCrash = { trace ->
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(
                            ClipData.newPlainText(
                                "CoinePro crash",
                                "CoinePro ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n\n" + trace,
                            ),
                        )
                    },
                    onClearCrash = {
                        crashes.clear()
                        lastCrash = null
                    },
                    signingFingerprints = fingerprints,
                    onCopyFingerprint = { text ->
                        val clipboard = context.getSystemService(ClipboardManager::class.java)
                        clipboard?.setPrimaryClip(ClipData.newPlainText("CoinePro signature", text))
                    },
                )
            }
        }
        }
    }
}


/**
 * The categories a given reader can actually receive, grouped the way every app in this market
 * groups them.
 *
 * A guest is shown the market group and nothing else. The other three need an account to fire at
 * all, and a switch for an event that cannot happen turns the whole screen into decoration —
 * which is the fastest way to teach somebody that this app's settings do not mean anything.
 */
@Composable
private fun notificationSections(guest: Boolean): List<NotificationSection> {
    val market = NotificationSection(
        title = stringResource(R.string.channel_group_market),
        categories = listOfNotNull(
            NotificationCategory.PRICE_ALERT,
            NotificationCategory.WATCHLIST_MOVE,
            NotificationCategory.NEWS,
            NotificationCategory.CALENDAR,
            NotificationCategory.AI_SETUP.takeUnless { guest },
        ),
    )
    if (guest) {
        return listOf(
            market,
            NotificationSection(
                title = stringResource(R.string.channel_group_other),
                categories = listOf(NotificationCategory.MARKETING),
            ),
        )
    }
    return listOf(
        NotificationSection(
            title = stringResource(R.string.channel_group_trading),
            categories = listOf(
                NotificationCategory.NEW_SIGNAL,
                NotificationCategory.TARGET_HIT,
                NotificationCategory.STOP_HIT,
                NotificationCategory.SIGNAL_CLOSED,
                NotificationCategory.COPY_OPENED,
                NotificationCategory.COPY_CLOSED,
                NotificationCategory.COPY_FAILED,
            ),
        ),
        market,
        NotificationSection(
            title = stringResource(R.string.channel_group_account),
            categories = listOf(NotificationCategory.SECURITY, NotificationCategory.ACCOUNT),
        ),
        NotificationSection(
            title = stringResource(R.string.channel_group_other),
            categories = listOf(NotificationCategory.MARKETING),
        ),
    )
}

/* -------------------------------------------------------------- hub glue */

/**
 * The feed's own state, in the hub's four grades.
 *
 * Degraded is a warning rather than a failure on purpose: the socket is down but the HTTP snapshot
 * is carrying quotes, so the screen is still telling the truth — just less often.
 */
private fun MarketConnectionState.tone(): HubTone = when (this) {
    MarketConnectionState.LIVE -> HubTone.GOOD
    MarketConnectionState.CONNECTING, MarketConnectionState.DEGRADED -> HubTone.WARN
    MarketConnectionState.OFFLINE -> HubTone.BAD
    MarketConnectionState.IDLE -> HubTone.IDLE
}

/** The platform's own short name, for the badge on the profile hero. */
@StringRes
private fun MarketPlatform.labelRes(): Int = when (this) {
    MarketPlatform.TRADEYAR -> R.string.platform_crypto
    MarketPlatform.COINEPRO_FX -> R.string.platform_forex
}

@StringRes
private fun MarketConnectionState.labelRes(): Int = when (this) {
    MarketConnectionState.LIVE -> R.string.hub_feed_live
    MarketConnectionState.CONNECTING -> R.string.hub_feed_connecting
    MarketConnectionState.DEGRADED -> R.string.hub_feed_degraded
    MarketConnectionState.OFFLINE -> R.string.hub_feed_offline
    MarketConnectionState.IDLE -> R.string.hub_feed_idle
}

private fun NotificationPermissionUiState.toHubPermission(): PushPermission = when (this) {
    NotificationPermissionUiState.NOT_CONFIGURED -> PushPermission.NOT_CONFIGURED
    NotificationPermissionUiState.NOT_REQUIRED -> PushPermission.NOT_REQUIRED
    NotificationPermissionUiState.AVAILABLE_TO_REQUEST -> PushPermission.AVAILABLE
    NotificationPermissionUiState.DENIED -> PushPermission.DENIED
    NotificationPermissionUiState.GRANTED -> PushPermission.GRANTED
}

/**
 * The venue that executes for one platform — MetaTrader 5 for forex, LBank for crypto.
 *
 * Never both. Showing a reader the other platform's broker is the same mixing bug as showing its
 * symbols, and here it would invite someone to judge their execution readiness from an account
 * this session is not even signed in to.
 */
private fun ConnectionsState.forPlatform(platform: MarketPlatform): VenueStatus {
    val connection = when (platform) {
        MarketPlatform.COINEPRO_FX -> mt5
        MarketPlatform.TRADEYAR -> lbank
    }
    return VenueStatus(
        name = if (platform == MarketPlatform.COINEPRO_FX) "MetaTrader 5" else "LBank",
        configured = connection != null,
        connected = connection?.connected == true,
    )
}
