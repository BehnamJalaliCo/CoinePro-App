package com.coinepro.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.coinepro.app.auth.GoogleSignInClient
import com.coinepro.app.auth.GoogleSignInOutcome
import com.coinepro.app.notifications.PushCoordinator
import com.coinepro.app.sync.BackgroundSyncScheduler
import com.coinepro.core.aiassistant.AiAssistantController
import com.coinepro.core.aisignal.AiSignalController
import com.coinepro.core.aivision.AiVisionController
import com.coinepro.core.auth.SessionController
import com.coinepro.core.auth.SessionState
import com.coinepro.core.datastore.ActivePlatformStore
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.marketdata.MarketDataController
import com.coinepro.core.marketdata.MarketDataState
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.navigation.AppDestination
import com.coinepro.core.notifications.NotificationController
import com.coinepro.core.signals.SignalController
import com.coinepro.feature.activity.ActivityScreen
import com.coinepro.feature.ai.AiStudioScreen
import com.coinepro.feature.aiassistant.AiAssistantScreen
import com.coinepro.feature.aivision.AiVisionScreen
import com.coinepro.feature.auth.AuthScreen
import com.coinepro.feature.auth.EmailAuthScreen
import com.coinepro.feature.calendar.EconomicCalendarScreen
import com.coinepro.feature.connections.ConnectionsScreen
import com.coinepro.feature.execution.ExecutionScreen
import com.coinepro.core.account.AccountController
import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.ui.platform.LocalContext
import com.coinepro.core.auth.PlatformCapabilities
import com.coinepro.core.auth.PlatformSessions
import com.coinepro.core.auth.EmailAuthController
import com.coinepro.core.auth.EmailAuthStep
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.common.BidiText
import com.coinepro.core.diagnostics.Appearance
import com.coinepro.core.diagnostics.ControlHub
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
import com.coinepro.core.marketdata.MarketConnectionState
import com.coinepro.core.marketdata.MarketDataCache
import com.coinepro.core.diagnostics.AdminController
import com.coinepro.feature.admin.AdminScreen
import com.coinepro.feature.home.HomeBriefing
import com.coinepro.feature.home.HomePortfolio
import com.coinepro.feature.home.HomeSubscription
import com.coinepro.feature.home.HomeScreen
import com.coinepro.feature.kyc.KycScreen
import com.coinepro.feature.home.toHomeBriefing
import com.coinepro.feature.home.toHomePortfolio
import com.coinepro.feature.home.toHomeSubscription
import com.coinepro.feature.news.NewsScreen
import com.coinepro.feature.signaldetail.SignalDetailScreen
import com.coinepro.feature.signals.SignalsScreen
import com.coinepro.feature.tools.ToolsScreen
import kotlinx.coroutines.launch

private const val SIGNAL_DETAIL_PATTERN = "signal/{signalId}"
private const val EXECUTION_PATTERN = "execution/{signalId}"
private const val CONNECTIONS_ROUTE = "connections"
private const val AI_VISION_ROUTE = "ai/vision"
private const val AI_ASSISTANT_ROUTE = "ai/assistant"
private const val NEWS_ROUTE = "market/news"
private const val CALENDAR_ROUTE = "market/calendar"
private const val LAUNCH_READINESS_ROUTE = "launch-readiness"
private const val ADMIN_ROUTE = "diagnostics"
private const val KYC_ROUTE = "account/verify"
private fun signalDetailRoute(signalId: Long) = "signal/$signalId"
private fun executionRoute(signalId: Long) = "execution/$signalId"

@Composable
fun CoineProApp(
    sessionController: SessionController,
    emailAuthController: EmailAuthController,
    marketDataControllers: Map<MarketPlatform, MarketDataController>,
    accountControllers: Map<MarketPlatform, AccountController>,
    adminController: AdminController,
    platformSessions: PlatformSessions,
    platformCapabilities: PlatformCapabilities,
    marketDataCache: MarketDataCache,
    activePlatformStore: ActivePlatformStore,
    signalControllers: Map<MarketPlatform, SignalController>,
    notificationControllers: Map<MarketPlatform, NotificationController>,
    executionControllers: Map<MarketPlatform, ExecutionController>,
    aiSignalControllers: Map<MarketPlatform, AiSignalController>,
    aiVisionControllers: Map<MarketPlatform, AiVisionController>,
    aiAssistantController: AiAssistantController,
    marketIntelControllers: Map<MarketPlatform, MarketIntelController>,
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
    LaunchedEffect(sessionController) { sessionController.start() }
    val session by sessionController.state.collectAsStateWithLifecycle()
    val emailAuthState by emailAuthController.state.collectAsStateWithLifecycle()
    val loginConfigState by sessionController.loginConfigState.collectAsStateWithLifecycle()
    // Exactly one feed runs at a time. Switching platform stops the old controller before the new
    // one starts, so two sockets are never open and the screen can never blend their quotes.
    val activePlatform by activePlatformStore.active
        .collectAsStateWithLifecycle(initialValue = activePlatformStore.available.first())
    val marketDataController = marketDataControllers.getValue(activePlatform)
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
            aiSignalControllers.values.forEach(AiSignalController::clear)
            aiVisionControllers.values.forEach(AiVisionController::clear)
            aiAssistantController.clear()
            marketIntelControllers.values.forEach(MarketIntelController::clear)
        }
    }

    // Refreshed here rather than in onResume, so a platform switch reads that platform's news
    // instead of leaving the previous market's headlines under the new market's heading.
    LaunchedEffect(signedIn, marketIntelController) {
        if (signedIn) marketIntelController.refresh()
    }

    val notificationState by notificationController.state.collectAsStateWithLifecycle()
    val venueState by executionController.connections.collectAsStateWithLifecycle()
    val sessionStates by platformSessions.states.collectAsStateWithLifecycle(initialValue = emptyMap())
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
    )

    CoineProTheme {
        when (val current = session) {
            is SessionState.SignedIn -> MainShell(
                marketState = marketState,
                adminController = adminController,
                hub = hub,
                hubActions = hubActions,
                briefing = briefingState.toHomeBriefing(briefingReadAt),
                portfolio = portfolioState.toHomePortfolio(),
                subscription = current.entitlement.toHomeSubscription(),
                onRefreshAccount = accountController::refresh,
                signalController = signalController,
                notificationController = notificationController,
                executionController = executionController,
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
                onSignalLaunchConsumed = onSignalLaunchConsumed,
                onActivityLaunchConsumed = onActivityLaunchConsumed,
                onRequestNotificationPermission = onRequestNotificationPermission,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onSendFeedback = onSendFeedback,
                onMarketRetry = marketDataController::retry,
                platforms = activePlatformStore.available,
                activePlatform = activePlatform,
                onSelectPlatform = { platform ->
                    scope.launch { activePlatformStore.setActive(platform) }
                },
                onLogout = {
                    scope.launch {
                        pushCoordinator.unregisterCurrentToken()
                        sessionController.logout()
                    }
                },
            )
            // Signing in is the email flow's job now. The other two states are not sign-in at all —
            // one is a session being restored, the other a session that exists but could not be
            // revalidated — and putting credential fields in front of either would ask the reader
            // to solve a problem that is not theirs.
            SessionState.SignedOut -> {
                LaunchedEffect(emailAuthController) { emailAuthController.loadMethods() }
                // Arriving on a recovery link means the reader is mid-recovery, so the screen opens
                // where they left off rather than on a sign-in form they cannot yet complete.
                LaunchedEffect(launchResetToken) {
                    if (launchResetToken != null) {
                        emailAuthController.goTo(EmailAuthStep.RESET_PASSWORD)
                    }
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
                    onTelegramPayload = { payload ->
                        scope.launch { sessionController.completeTelegramLogin(payload) }
                    },
                    initialResetToken = launchResetToken.orEmpty(),
                )
            }
            else -> AuthScreen(
                state = session,
                loginConfigState = loginConfigState,
                onTelegramPayload = { payload ->
                    scope.launch { sessionController.completeTelegramLogin(payload) }
                },
                onRetryLoginConfig = { scope.launch { sessionController.prepareLogin() } },
                onRetry = { scope.launch { sessionController.restore() } },
                onLogout = { scope.launch { sessionController.logout() } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(
    marketState: MarketDataState,
    signalController: SignalController,
    notificationController: NotificationController,
    executionController: ExecutionController,
    aiSignalController: AiSignalController,
    aiVisionController: AiVisionController,
    aiAssistantController: AiAssistantController,
    marketIntelController: MarketIntelController,
    accountController: AccountController,
    adminController: AdminController,
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
    onSignalLaunchConsumed: () -> Unit,
    onActivityLaunchConsumed: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSendFeedback: () -> Unit,
    onMarketRetry: () -> Unit,
    onLogout: () -> Unit,
    platforms: List<MarketPlatform>,
    activePlatform: MarketPlatform,
    onSelectPlatform: (MarketPlatform) -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isSubScreen = currentRoute in setOf(
        SIGNAL_DETAIL_PATTERN,
        EXECUTION_PATTERN,
        CONNECTIONS_ROUTE,
        AI_VISION_ROUTE,
        AI_ASSISTANT_ROUTE,
        KYC_ROUTE,
        NEWS_ROUTE,
        CALENDAR_ROUTE,
        LAUNCH_READINESS_ROUTE,
        ADMIN_ROUTE,
    )
    val subTitleRes = when (currentRoute) {
        ADMIN_ROUTE -> R.string.screen_diagnostics
        SIGNAL_DETAIL_PATTERN -> R.string.screen_signal_detail
        EXECUTION_PATTERN -> R.string.screen_execution
        CONNECTIONS_ROUTE -> R.string.screen_connections
        KYC_ROUTE -> R.string.screen_kyc
        AI_VISION_ROUTE -> R.string.screen_ai_vision
        AI_ASSISTANT_ROUTE -> R.string.screen_ai_assistant
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
            navController.navigate(AppDestination.ACTIVITY.route) {
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
                    title = { Text(stringResource(subTitleRes)) },
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
                        if (!isSubScreen) {
                            TextButton(onClick = { navController.navigate(LAUNCH_READINESS_ROUTE) }) {
                                Text(stringResource(R.string.action_safety))
                            }
                            TextButton(onClick = onLogout) {
                                Text(stringResource(R.string.action_logout))
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
        NavHost(
            navController = navController,
            startDestination = AppDestination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestination.HOME.route) {
                HomeScreen(
                    state = marketState,
                    briefing = briefing,
                    portfolio = portfolio,
                    subscription = subscription,
                    onRetry = {
                        onMarketRetry()
                        onRefreshAccount()
                    },
                    // Both pills lead to the AI section, which is where the work actually happens.
                    onGenerateSignal = { navController.navigate(AppDestination.AI.route) },
                    // Chart analysis is optional per deployment. Sending the reader to a screen the
                    // server has switched off is a wait that ends in an error every time, so the
                    // action falls back to the AI studio the server does serve.
                    onSendChart = {
                        navController.navigate(
                            if (chartVisionAvailable) AI_VISION_ROUTE else AppDestination.AI.route,
                        )
                    },
                    onOpenMarket = { navController.navigate(AppDestination.SIGNALS.route) },
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
                    // Home carries no top bar, so the account actions hang off the avatar.
                    onOpenVerification = { navController.navigate(KYC_ROUTE) },
                    onOpenSafety = { navController.navigate(LAUNCH_READINESS_ROUTE) },
                    onLogout = onLogout,
                    platforms = platforms,
                    activePlatform = activePlatform,
                    onSelectPlatform = onSelectPlatform,
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
                SignalDetailScreen(
                    controller = signalController,
                    marketIntelController = marketIntelController,
                    signalId = signalId,
                    onExecute = { navController.navigate(executionRoute(it)) },
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
            composable(KYC_ROUTE) {
                KycScreen(controller = accountController)
            }
            composable(CONNECTIONS_ROUTE) {
                ConnectionsScreen(controller = executionController, platform = activePlatform)
            }
            composable(AppDestination.AI.route) {
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
            composable(NEWS_ROUTE) {
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
            composable(AppDestination.TOOLS.route) {
                ToolsScreen(
                    platform = activePlatform,
                    onOpenConnections = { navController.navigate(CONNECTIONS_ROUTE) },
                    onOpenNews = { navController.navigate(NEWS_ROUTE) },
                    onOpenCalendar = { navController.navigate(CALENDAR_ROUTE) },
                )
            }
            composable(AppDestination.ACTIVITY.route) {
                ActivityScreen(
                    controller = notificationController,
                    executionController = executionController,
                    signalController = signalController,
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
                    platform = activePlatform,
                )
            }
            composable(LAUNCH_READINESS_ROUTE) {
                LaunchReadinessScreen(
                    notificationPermissionState = notificationPermissionState,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onSendFeedback = onSendFeedback,
                    versionLabel = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    onOpenDiagnostics = { navController.navigate(ADMIN_ROUTE) },
                )
            }
        }
    }
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
