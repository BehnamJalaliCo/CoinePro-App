package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the bottom bar does to the back stack, and specifically what it does when the tab tapped is
 * the graph's own start destination.
 *
 * The owner reported that from the toolkit — which is not a tab, and is pushed on top of Home — the
 * Markets, Chart and every other tab switched, and **Home did nothing**. That is a back-stack
 * question rather than a rendering one, so it is tested here on a graph with the same shape as the
 * app's: a start destination, a second tab, and a non-tab route reached by a plain navigate.
 *
 * The options under test are the ones the bar actually uses, copied rather than paraphrased. If
 * they are ever changed in `CoineProApp`, this test is where the change should be argued.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BottomBarNavigationTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController

    private fun graph() {
        rule.setContent {
            nav = rememberNavController()
            NavHost(navController = nav, startDestination = HOME) {
                composable(HOME) { Text(HOME) }
                composable(MARKETS) { Text(MARKETS) }
                composable(TOOLS) { Text(TOOLS) }
            }
        }
    }

    /** Exactly the options `CoineProBottomBar`'s `onSelect` navigates with. */
    private fun tapTab(route: String) = rule.runOnUiThread {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    private fun route(): String? = nav.currentBackStackEntry?.destination?.route

    @Test
    fun `the home tab returns from a screen pushed above it`() {
        graph()
        rule.runOnUiThread { nav.navigate(TOOLS) }
        rule.waitForIdle()
        assertEquals(TOOLS, route())

        tapTab(HOME)
        rule.waitForIdle()

        assertEquals("the Home tab did not leave the toolkit", HOME, route())
    }

    @Test
    fun `another tab returns from the same screen`() {
        // The control. The owner reported this one working, and it has to keep working — a fix for
        // the case above that broke this would be a worse trade.
        graph()
        rule.runOnUiThread { nav.navigate(TOOLS) }
        rule.waitForIdle()

        tapTab(MARKETS)
        rule.waitForIdle()

        assertEquals(MARKETS, route())
    }

    @Test
    fun `tapping the tab already on screen is not a second copy of it`() {
        graph()
        tapTab(HOME)
        rule.waitForIdle()

        assertEquals(HOME, route())
        assertEquals("Home was pushed onto itself", 1, backStackCount(HOME))
    }

    private fun backStackCount(route: String): Int =
        nav.currentBackStack.value.count { it.destination.route == route }

    private companion object {
        const val HOME = "home"
        const val MARKETS = "markets"
        const val TOOLS = "tools"
    }
}
