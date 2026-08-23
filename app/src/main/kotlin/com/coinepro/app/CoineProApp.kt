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
import com.coinepro.core.auth.SessionController
import com.coinepro.core.auth.SessionState
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.marketdata.MarketDataController
import com.coinepro.core.marketdata.MarketDataState
import com.coinepro.core.navigation.AppDestination
import com.coinepro.core.notifications.NotificationController
import com.coinepro.core.signals.SignalController
import com.coinepro.feature.activity.ActivityScreen
import com.coinepro.feature.ai.AiScreen
import com.coinepro.feature.auth.AuthScreen
import com.coinepro.feature.home.HomeScreen
import com.coinepro.feature.signaldetail.SignalDetailScreen
import com.coinepro.feature.signals.SignalsScreen
import com.coinepro.feature.tools.ToolsScreen
import kotlinx.coroutines.launch

private const val SIGNAL_DETAIL_PATTERN = "signal/{signalId}"
private fun signalDetailRoute(signalId: Long) = "signal/$signalId"

@Composable
fun CoineProApp(
    sessionController: SessionController,
    marketDataController: MarketDataController,
    signalController: SignalController,
    notificationController: NotificationController,
    pushCoordinator: PushCoordinator,
    launchSignalId: Long?,
    launchActivity: Boolean,
    onSignalLaunchConsumed: () -> Unit,
    onActivityLaunchConsumed: () -> Unit,
) {
    LaunchedEffect(sessionController) { sessionController.start() }
    val session by sessionController.state.collectAsStateWithLifecycle()
    val botUsername by sessionController.botUsername.collectAsStateWithLifecycle()
    val marketState by marketDataController.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val signedIn = session is SessionState.SignedIn

    LaunchedEffect(signedIn) {
        if (signedIn) {
            marketDataController.start()
            pushCoordinator.registerCurrentToken()
        } else {
            marketDataController.stop()
            signalController.clear()
            notificationController.clear()
        }
    }

    CoineProTheme {
        when (session) {
            is SessionState.SignedIn -> MainShell(
                marketState = marketState,
                signalController = signalController,
                notificationController = notificationController,
                launchSignalId = launchSignalId,
                launchActivity = launchActivity,
                onSignalLaunchConsumed = onSignalLaunchConsumed,
                onActivityLaunchConsumed = onActivityLaunchConsumed,
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
                botUsername = botUsername,
                onTelegramPayload = { payload ->
                    scope.launch { sessionController.completeTelegramLogin(payload) }
                },
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
    launchSignalId: Long?,
    launchActivity: Boolean,
    onSignalLaunchConsumed: () -> Unit,
    onActivityLaunchConsumed: () -> Unit,
    onMarketRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isSignalDetail = currentRoute == SIGNAL_DETAIL_PATTERN

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
            if (isSignalDetail) {
                TopAppBar(
                    title = { Text("Signal") },
                    navigationIcon = {
                        TextButton(onClick = { navController.popBackStack() }) { Text("Back") }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("CoinePro") },
                    actions = { TextButton(onClick = onLogout) { Text("Logout") } },
                )
            }
        },
        bottomBar = {
            if (!isSignalDetail) {
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
                SignalDetailScreen(controller = signalController, signalId = signalId)
            }
            composable(AppDestination.AI.route) { AiScreen() }
            composable(AppDestination.TOOLS.route) { ToolsScreen() }
            composable(AppDestination.ACTIVITY.route) {
                ActivityScreen(
                    controller = notificationController,
                    onOpenSignal = { navController.navigate(signalDetailRoute(it)) },
                )
            }
        }
    }
}
