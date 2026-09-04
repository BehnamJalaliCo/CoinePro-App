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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
                        NavigationBar(modifier = Modifier.testTag(BAR_TAG)) {
                            AppDestination.entries.forEach { destination ->
                                NavigationBarItem(
                                    selected = destination == AppDestination.entries.first(),
                                    onClick = {},
                                    icon = { Text(destination.mark) },
                                    label = { Text(stringResource(destination.labelRes)) },
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

        // A capture with no assertion passes on a blank screen, which is the one outcome it exists
        // to catch: this test's whole job is to prove the real navigation bar renders on a real
        // device, and a PNG nobody looks at proves nothing. Every destination's label is checked by
        // the string the app actually shows, so a destination added without a translation fails
        // here rather than shipping as an empty tab.
        //
        // **Inside the bar, and that is not tidiness.** The page underneath is a real screen with
        // real words on it, and one of them is «Chart» — the home screen's own chart shortcut. So
        // the unscoped lookup matched two nodes and the assertion died on the ambiguity rather than
        // on anything being wrong, which is a test failing at its own question. Anchored to the
        // bar, the label being looked for is the label the bar draws.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        AppDestination.entries.forEach { destination ->
            composeRule
                .onNode(
                    hasText(context.getString(destination.labelRes)) and
                        hasAnyAncestor(hasTestTag(BAR_TAG)),
                )
                .assertIsDisplayed()
        }

        Thread.sleep(500)
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/coinepro-menu-render.png")
            .close()
        Thread.sleep(300)
    }

    private companion object {
        /** What anchors the label lookups to the bar rather than to the page behind it. */
        const val BAR_TAG = "navigation-bar"
    }
}
