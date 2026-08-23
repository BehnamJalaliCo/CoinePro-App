package com.coinepro.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
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
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.marketdata.MarketDataController
import com.coinepro.core.marketdata.MarketDataState
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.navigation.AppDestination
import com.coinepro.core.notifications.NotificationController
import com.coinepro.core.signals.SignalController
import com.coinepro.feature.activity.ActivityScreen
import com.coinepro.feature.ai.AiScreen
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
    marketDataController: MarketDataController,
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
    val marketState by marketDataController.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val signedIn = session is SessionState.SignedIn

    LaunchedEffect(signedIn) {
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
    val subTitle = when (currentRoute) {
        SIGNAL_DETAIL_PATTERN -> "Signal"
        EXECUTION_PATTERN -> "Execute signal"
        CONNECTIONS_ROUTE -> "Connections"
        AI_VISION_ROUTE -> "AI Vision"
        AI_ASSISTANT_ROUTE -> "AI Assistant"
        NEWS_ROUTE -> "Market Intelligence"
        CALENDAR_ROUTE -> "Economic Calendar"
        LAUNCH_READINESS_ROUTE -> "Launch & safety"
        else -> "CoinePro"
    }

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
        topBar = {
            if (isSubScreen) {
                TopAppBar(
                    title = { Text(subTitle) },
                    navigationIcon = {
                        TextButton(onClick = { navController.popBackStack() }) { Text("Back") }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("CoinePro") },
                    actions = {
                        TextButton(onClick = { navController.navigate(LAUNCH_READINESS_ROUTE) }) { Text("Safety") }
                        TextButton(onClick = onLogout) { Text("Logout") }
                    },
                )
            }
        },
        bottomBar = {
            if (!isSubScreen) {
                NavigationBar {
                    AppDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(destination.mark) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(AppDestination.HOME.route) {
                HomeScreen(state = marketState, onRetry = onMarketRetry)
            }
            composable(AppDestination.SIGNALS.route) {
                SignalsScreen(
                    controller = signalController,
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
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
                AiScreen(
                    controller = aiSignalController,
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
                    onOpenVision = { navController.navigate(AI_VISION_ROUTE) },
                    onOpenAssistant = { navController.navigate(AI_ASSISTANT_ROUTE) },
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
