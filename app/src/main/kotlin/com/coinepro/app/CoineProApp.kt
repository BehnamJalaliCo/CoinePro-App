package com.coinepro.app

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.navigation.AppDestination
import com.coinepro.feature.activity.ActivityScreen
import com.coinepro.feature.ai.AiScreen
import com.coinepro.feature.home.HomeScreen
import com.coinepro.feature.signals.SignalsScreen
import com.coinepro.feature.tools.ToolsScreen

@Composable
fun CoineProApp() {
    CoineProTheme {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        Scaffold(
            bottomBar = {
                NavigationBar {
                    AppDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(destination.mark) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppDestination.HOME.route,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(AppDestination.HOME.route) { HomeScreen() }
                composable(AppDestination.SIGNALS.route) { SignalsScreen() }
                composable(AppDestination.AI.route) { AiScreen() }
                composable(AppDestination.TOOLS.route) { ToolsScreen() }
                composable(AppDestination.ACTIVITY.route) { ActivityScreen() }
            }
        }
    }
}
