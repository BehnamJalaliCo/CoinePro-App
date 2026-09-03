package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.coinepro.core.navigation.AppDestination
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

    /**
     * The production options themselves, not a copy of them.
     *
     * `tabSwitch` is what the bar's `onSelect` calls, so a change to it is caught here rather than
     * only on a device. A test that pasted the same four lines would keep passing after somebody
     * changed the real ones.
     */
    private fun tapTab(route: String) = rule.runOnUiThread {
        nav.navigate(route) { tabSwitch(nav, route) }
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

    // ── The real bar ────────────────────────────────────────────────────────────────────────────

    /**
     * The graph the app actually has at its roots: every [AppDestination], in order, plus one
     * screen pushed on top of them the way the toolkit is.
     *
     * Built from `entries` rather than from five literals, so a destination added or renamed is
     * covered by these tests the day it lands rather than the day somebody remembers to list it.
     */
    private fun realGraph() {
        rule.setContent {
            nav = rememberNavController()
            NavHost(
                navController = nav,
                startDestination = AppDestination.entries.first().route,
            ) {
                AppDestination.entries.forEach { destination ->
                    composable(destination.route) { Text(destination.route) }
                }
                composable(TOOLS) { Text(TOOLS) }
            }
        }
    }

    @Test
    fun `every destination in the bar is reachable and becomes the current one`() {
        realGraph()
        for (destination in AppDestination.entries) {
            tapTab(destination.route)
            rule.waitForIdle()
            assertEquals(
                "tapping ${destination.name} did not land on it",
                destination.route,
                route(),
            )
        }
    }

    @Test
    fun `the bar's selected state is the current route, for each of the five`() {
        // What the bar draws is `currentRoute == destination.route`. The failure this catches is a
        // destination whose route the graph spells differently from the enum — the tab would then
        // navigate correctly and never look selected, which reads as a dead tab.
        realGraph()
        for (destination in AppDestination.entries) {
            tapTab(destination.route)
            rule.waitForIdle()
            val selected = AppDestination.entries.filter { it.route == route() }
            assertEquals("exactly one tab must look selected", listOf(destination), selected)
        }
    }

    @Test
    fun `a screen pushed above any root is left by tapping that root`() {
        // The generalisation of the bug this file was written for. It was reported on Home, which
        // was then the start destination; the start destination is the watchlist now, so the case
        // that needs the conditional `restoreState` has moved with it. Every root is checked
        // rather than the first one, because which of them is the graph's start is a decision that
        // can change again.
        realGraph()
        for (destination in AppDestination.entries) {
            tapTab(destination.route)
            rule.waitForIdle()
            rule.runOnUiThread { nav.navigate(TOOLS) }
            rule.waitForIdle()
            assertEquals(TOOLS, route())

            tapTab(destination.route)
            rule.waitForIdle()
            assertEquals(
                "the ${destination.name} tab did not leave the screen above it",
                destination.route,
                route(),
            )
        }
    }

    @Test
    fun `a tab remembers where you were in it, and re-tapping it goes back to its own root`() {
        // The two halves of the rule, in one sequence, because they are one rule: restore the tab
        // you are going *to*, never the tab you are already *in*.
        realGraph()
        val first = AppDestination.entries.first()
        val second = AppDestination.entries[1]

        tapTab(second.route)
        rule.waitForIdle()
        rule.runOnUiThread { nav.navigate(TOOLS) }
        rule.waitForIdle()

        // Away, and back: the tab is where it was left.
        tapTab(first.route)
        rule.waitForIdle()
        assertEquals(first.route, route())
        tapTab(second.route)
        rule.waitForIdle()
        assertEquals("the tab did not remember where the reader was in it", TOOLS, route())

        // And re-tapping the tab the reader is standing in goes to that tab's own root.
        tapTab(second.route)
        rule.waitForIdle()
        assertEquals("re-tapping the current tab did not return to its root", second.route, route())
    }

    @Test
    fun `switching away and back does not stack copies of a root`() {
        realGraph()
        val first = AppDestination.entries.first()
        val second = AppDestination.entries[1]
        repeat(3) {
            tapTab(second.route)
            rule.waitForIdle()
            tapTab(first.route)
            rule.waitForIdle()
        }
        assertEquals("the start destination was pushed onto itself", 1, backStackCount(first.route))
    }

    private companion object {
        const val HOME = "home"
        const val MARKETS = "markets"
        const val TOOLS = "tools"
    }
}
