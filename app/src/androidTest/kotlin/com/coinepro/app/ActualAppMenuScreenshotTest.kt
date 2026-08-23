package com.coinepro.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.marketdata.MarketConnectionState
import com.coinepro.core.marketdata.MarketDataState
import com.coinepro.core.navigation.AppDestination
import com.coinepro.feature.home.HomeScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActualAppMenuScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun captureActualRenderedHomeMenu() {
        composeRule.setContent {
            CoineProTheme {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("CoinePro") }) },
                    bottomBar = {
                        NavigationBar {
                            AppDestination.entries.forEach { destination ->
                                NavigationBarItem(
                                    selected = destination == AppDestination.HOME,
                                    onClick = {},
                                    icon = { Text(destination.mark) },
                                    label = { Text(destination.label) },
                                )
                            }
                        }
                    },
                ) { paddingValues ->
                    Box(modifier = Modifier.padding(paddingValues)) {
                        HomeScreen(
                            state = MarketDataState(connection = MarketConnectionState.OFFLINE),
                            onRetry = {},
                        )
                    }
                }
            }
        }

        composeRule.waitForIdle()
        Thread.sleep(500)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/coinepro-menu-render.png")
            .close()
        Thread.sleep(300)
    }
}
