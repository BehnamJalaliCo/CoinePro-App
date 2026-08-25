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
import com.coinepro.feature.calendar.EconomicCalendarScreen
import com.coinepro.feature.connections.ConnectionsScreen
import com.coinepro.feature.execution.ExecutionScreen
import com.coinepro.feature.home.HomeScreen
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
private fun signalDetailRoute(signalId: Long) = "signal/$signalId"
private fun executionRoute(signalId: Long) = "execution/$signalId"

@Composable
fun CoineProApp(
    sessionController: SessionController,
    marketDataControllers: Map<MarketPlatform, MarketDataController>,
    activePlatformStore: ActivePlatformStore,
    signalController: SignalController,
    notificationController: NotificationController,
    executionController: ExecutionController,
    aiSignalController: AiSignalController,
    aiVisionController: AiVisionController,
    aiAssistantController: AiAssistantController,
    marketIntelController: MarketIntelController,
    pushCoordinator: PushCoordinator,
    backgroundSyncScheduler: BackgroundSyncScheduler,
    launchSignalId: Long?,
    launchActivity: Boolean,
    notificationPermissionState: NotificationPermissionUiState,
    onSignalLaunchConsumed: () -> Unit,
    onActivityLaunchConsumed: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSendFeedback: () -> Unit,
) {
    LaunchedEffect(sessionController) { sessionController.start() }
    val session by sessionController.state.collectAsStateWithLifecycle()
    val loginConfigState by sessionController.loginConfigState.collectAsStateWithLifecycle()
    // Exactly one feed runs at a time. Switching platform stops the old controller before the new
    // one starts, so two sockets are never open and the screen can never blend their quotes.
    val activePlatform by activePlatformStore.active
        .collectAsStateWithLifecycle(initialValue = activePlatformStore.available.first())
    val marketDataController = marketDataControllers.getValue(activePlatform)
    val marketState by marketDataController.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val signedIn = session is SessionState.SignedIn

    LaunchedEffect(signedIn, activePlatform) {
        marketDataControllers.forEach { (platform, controller) ->
            if (platform != activePlatform) controller.stop()
        }
        if (signedIn) {
            marketDataController.start()
            pushCoordinator.registerCurrentToken()
            backgroundSyncScheduler.enableForAuthenticatedSession()
        } else {
            backgroundSyncScheduler.disable()
            marketDataController.stop()
            signalController.clear()
            notificationController.clear()
            executionController.clear()
            aiSignalController.clear()
            aiVisionController.clear()
            aiAssistantController.clear()
            marketIntelController.clear()
        }
    }

    CoineProTheme {
        when (session) {
            is SessionState.SignedIn -> MainShell(
                marketState = marketState,
                signalController = signalController,
                notificationController = notificationController,
                executionController = executionController,
                aiSignalController = aiSignalController,
                aiVisionController = aiVisionController,
                aiAssistantController = aiAssistantController,
                marketIntelController = marketIntelController,
                launchSignalId = launchSignalId,
                launchActivity = launchActivity,
                notificationPermissionState = notificationPermissionState,
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
    launchSignalId: Long?,
    launchActivity: Boolean,
    notificationPermissionState: NotificationPermissionUiState,
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
        NEWS_ROUTE,
        CALENDAR_ROUTE,
        LAUNCH_READINESS_ROUTE,
    )
    val subTitleRes = when (currentRoute) {
        SIGNAL_DETAIL_PATTERN -> R.string.screen_signal_detail
        EXECUTION_PATTERN -> R.string.screen_execution
        CONNECTIONS_ROUTE -> R.string.screen_connections
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
                    onRetry = onMarketRetry,
                    // The briefing stays in its resting state until a server produces one. Both
                    // pills lead to the AI section, which is where the work actually happens.
                    onGenerateSignal = { navController.navigate(AppDestination.AI.route) },
                    onSendChart = { navController.navigate(AI_VISION_ROUTE) },
                    onOpenMarket = { navController.navigate(AppDestination.SIGNALS.route) },
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
                    // Home carries no top bar, so the account actions hang off the avatar.
                    onOpenSafety = { navController.navigate(LAUNCH_READINESS_ROUTE) },
                    onLogout = onLogout,
                    platforms = platforms,
                    activePlatform = activePlatform,
                    onSelectPlatform = onSelectPlatform,
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
            composable(CONNECTIONS_ROUTE) {
                ConnectionsScreen(controller = executionController)
            }
            composable(AppDestination.AI.route) {
                // AiStudioScreen, not the older AiScreen: the two carried the same generator, and
                // only this one shows the evidence the server returns alongside the verdict.
                AiStudioScreen(
                    controller = aiSignalController,
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
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
                )
            }
            composable(NEWS_ROUTE) {
                NewsScreen(
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
                )
            }
            composable(LAUNCH_READINESS_ROUTE) {
                LaunchReadinessScreen(
                    notificationPermissionState = notificationPermissionState,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onSendFeedback = onSendFeedback,
                )
            }
        }
    }
}
