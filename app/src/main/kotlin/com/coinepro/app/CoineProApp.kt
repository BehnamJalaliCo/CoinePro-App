package com.coinepro.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.coinepro.app.notifications.PushCoordinator
import com.coinepro.core.aisignal.AiSignalController
import com.coinepro.core.aisignal.AiVisionController
import com.coinepro.core.auth.SessionController
import com.coinepro.core.auth.SessionState
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.marketdata.MarketDataController
import com.coinepro.core.marketdata.MarketDataState
import com.coinepro.core.navigation.AppDestination
import com.coinepro.core.notifications.NotificationController
import com.coinepro.core.signals.SignalController
import com.coinepro.feature.activity.ActivityScreen
import com.coinepro.feature.ai.AiScreen
import com.coinepro.feature.ai.AiVisionScreen
import com.coinepro.feature.auth.AuthScreen
import com.coinepro.feature.connections.ConnectionsScreen
import com.coinepro.feature.execution.ExecutionScreen
import com.coinepro.feature.home.HomeScreen
import com.coinepro.feature.signaldetail.SignalDetailScreen
import com.coinepro.feature.signals.SignalsScreen
import com.coinepro.feature.tools.ToolsScreen
import kotlinx.coroutines.launch

private const val SIGNAL_DETAIL_PATTERN = "signal/{signalId}"
private const val EXECUTION_PATTERN = "execution/{signalId}"
private const val CONNECTIONS_ROUTE = "connections"
private const val AI_VISION_ROUTE = "ai-vision"
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
    pushCoordinator: PushCoordinator,
    launchSignalId: Long?, launchActivity: Boolean,
    onSignalLaunchConsumed: () -> Unit, onActivityLaunchConsumed: () -> Unit,
) {
    LaunchedEffect(sessionController) { sessionController.start() }
    val session by sessionController.state.collectAsStateWithLifecycle()
    val botUsername by sessionController.botUsername.collectAsStateWithLifecycle()
    val marketState by marketDataController.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val signedIn = session is SessionState.SignedIn
    LaunchedEffect(signedIn) {
        if (signedIn) { marketDataController.start(); pushCoordinator.registerCurrentToken() }
        else {
            marketDataController.stop(); signalController.clear(); notificationController.clear(); executionController.clear()
            aiSignalController.clear(); aiVisionController.onSignedOut()
        }
    }
    CoineProTheme {
        if (session is SessionState.SignedIn) MainShell(
            marketState, signalController, notificationController, executionController, aiSignalController, aiVisionController,
            launchSignalId, launchActivity, onSignalLaunchConsumed, onActivityLaunchConsumed, marketDataController::retry,
        ) { scope.launch { pushCoordinator.unregisterCurrentToken(); sessionController.logout() } }
        else AuthScreen(session, botUsername, { payload -> scope.launch { sessionController.completeTelegramLogin(payload) } }, { scope.launch { sessionController.restore() } }, { scope.launch { sessionController.logout() } })
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
    launchSignalId: Long?, launchActivity: Boolean,
    onSignalLaunchConsumed: () -> Unit, onActivityLaunchConsumed: () -> Unit,
    onMarketRetry: () -> Unit, onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val isSub = currentRoute in setOf(SIGNAL_DETAIL_PATTERN, EXECUTION_PATTERN, CONNECTIONS_ROUTE, AI_VISION_ROUTE)
    val title = when (currentRoute) { SIGNAL_DETAIL_PATTERN -> "Signal"; EXECUTION_PATTERN -> "Execute signal"; CONNECTIONS_ROUTE -> "Connections"; AI_VISION_ROUTE -> "AI Vision"; else -> "CoinePro" }
    LaunchedEffect(launchSignalId) { launchSignalId?.let { navController.navigate(signalDetailRoute(it)) { launchSingleTop = true }; onSignalLaunchConsumed() } }
    LaunchedEffect(launchActivity) { if (launchActivity) { navController.navigate(AppDestination.ACTIVITY.route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }; onActivityLaunchConsumed() } }
    Scaffold(
        topBar = { if (isSub) TopAppBar({ Text(title) }, navigationIcon = { TextButton({ navController.popBackStack() }) { Text("Back") } }) else TopAppBar({ Text("CoinePro") }, actions = { TextButton(onLogout) { Text("Logout") } }) },
        bottomBar = { if (!isSub) NavigationBar { AppDestination.entries.forEach { destination -> NavigationBarItem(currentRoute == destination.route, { navController.navigate(destination.route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }, { Text(destination.mark) }, label = { Text(destination.label) }) } } },
    ) { padding ->
        NavHost(navController, AppDestination.HOME.route, Modifier.padding(padding)) {
            composable(AppDestination.HOME.route) { HomeScreen(marketState, onMarketRetry) }
            composable(AppDestination.SIGNALS.route) { SignalsScreen(signalController) { navController.navigate(signalDetailRoute(it)) } }
            composable(SIGNAL_DETAIL_PATTERN, arguments = listOf(navArgument("signalId") { type = NavType.LongType })) { back -> back.arguments?.getLong("signalId")?.let { id -> SignalDetailScreen(signalController, id) { navController.navigate(executionRoute(it)) } } }
            composable(EXECUTION_PATTERN, arguments = listOf(navArgument("signalId") { type = NavType.LongType })) { back -> back.arguments?.getLong("signalId")?.let { id -> ExecutionScreen(id, signalController, executionController) { navController.navigate(CONNECTIONS_ROUTE) } } }
            composable(CONNECTIONS_ROUTE) { ConnectionsScreen(executionController) }
            composable(AppDestination.AI.route) { Column { AiScreen(aiSignalController) { navController.navigate(signalDetailRoute(it)) }; TextButton(onClick = { navController.navigate(AI_VISION_ROUTE) }) { Text("Analyze chart image with AI Vision") } } }
            composable(AI_VISION_ROUTE) { AiVisionScreen(aiVisionController) { navController.navigate(signalDetailRoute(it)) } }
            composable(AppDestination.TOOLS.route) { ToolsScreen { navController.navigate(CONNECTIONS_ROUTE) } }
            composable(AppDestination.ACTIVITY.route) { ActivityScreen(notificationController, executionController) { navController.navigate(signalDetailRoute(it)) } }
        }
    }
}
