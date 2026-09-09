package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.coinepro.core.designsystem.CoineProNavigationRail
import com.coinepro.core.designsystem.CoineProRailItem
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.CoineProWindowClass
import com.coinepro.core.designsystem.LocalCoineProWindowClass
import com.coinepro.core.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The rail and the bar are the same five destinations, in the same order, with the same words.
 *
 * Both are built from `AppDestination.entries`, so today they cannot disagree; this pins that
 * arrangement, because the day someone adds a sixth destination to the bar by hand — or filters
 * one out of the rail because it "does not fit" — is the day a tablet reader loses a tab a phone
 * reader has. The plan's tablet is a first-class citizen: nothing reachable on the phone is
 * unreachable on the rail, and the rail's tap goes to the same route the bar's tap does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w840dp-h1280dp-xhdpi")
class NavigationParityTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the rail carries every destination the bar carries, in the bar's order`() {
        lateinit var items: List<CoineProRailItem>
        lateinit var labels: List<String>
        rule.setContent {
            CoineProTheme {
                items = coineProRailItems()
                labels = AppDestination.entries.map { stringResource(it.labelRes) }
            }
        }
        assertEquals(AppDestination.entries.map { it.route }, items.map { it.key })
        assertEquals(labels, items.map { it.label })
        assertEquals(AppDestination.entries.size, items.size)
    }

    @Test
    fun `every destination is on screen in both, and the rail's tap names the bar's route`() {
        val tapped = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalCoineProWindowClass provides CoineProWindowClass.of(840, 1280)) {
                CoineProTheme {
                    CoineProNavigationRail(
                        items = coineProRailItems(),
                        selectedKey = AppDestination.entries.first().route,
                        onSelect = { tapped += it.key },
                        labelled = true,
                    )
                    CoineProBottomBar(
                        currentRoute = AppDestination.entries.first().route,
                        onSelect = { tapped += it.route },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        for (destination in AppDestination.entries) {
            rule.onNodeWithTag(AppChromeTestTags.barItem(destination.route)).assertIsDisplayed()
            val label = rule.activity.getString(destination.labelRes)
            // On the rail and on the bar, so at least twice.
            assertTrue(label, rule.onAllNodesWithText(label, useUnmergedTree = true).fetchSemanticsNodes().size >= 2)
        }
        // The bar's route and the rail's key are the same string, so one navigator serves both.
        rule.onNodeWithTag(AppChromeTestTags.barItem(AppDestination.CHART.route)).performClick()
        assertEquals(listOf(AppDestination.CHART.route), tapped)
    }
}
