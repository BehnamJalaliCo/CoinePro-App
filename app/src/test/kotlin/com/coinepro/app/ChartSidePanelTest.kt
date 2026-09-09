package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.feature.chart.ChartScreen
import com.coinepro.feature.chart.ChartSidePanel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The docked panels: a rail on a wide window, nothing on a tablet held upright.
 *
 * The chart is handed a panel the shell would own — here a stub with a sentence in it — and
 * the rail is expected to carry the chart's own object tree first and the shell's panel after.
 * Tapping the panel's glyph opens it beside the plot; on the 840 dp window there is no room for a
 * panel beside the tools and the plot's floor, and the rail is not drawn at all — the phone's
 * sheets and routes still serve.
 */
@RunWith(RobolectricTestRunner::class)
class ChartSidePanelTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun compose() {
        rule.setContent {
            CoineProTheme {
                ChartScreen(
                    controller = ScreenshotFixtures.chartController(scope),
                    sidePanels = listOf(
                        ChartSidePanel("stub", com.coinepro.feature.chart.R.string.chart_panel_watchlist, com.coinepro.core.designsystem.R.drawable.icon_star) {
                            Text(STUB)
                        },
                    ),
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-sw800dp-w1280dp-h800dp-xhdpi")
    fun `a landscape tablet draws the rail, the object tree first, and opens a panel on tap`() {
        compose()
        rule.onNodeWithContentDescription("side-panel-objects").assertIsDisplayed()
        rule.onNodeWithContentDescription("side-panel-stub").assertIsDisplayed()
        rule.onNodeWithText(STUB).assertDoesNotExist()
        rule.onNodeWithContentDescription("side-panel-stub").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(STUB).assertIsDisplayed()
        // Tapping the open panel's glyph closes it again.
        rule.onNodeWithContentDescription("side-panel-stub").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(STUB).assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w840dp-h1280dp-xhdpi")
    fun `a tablet held upright has no room for a panel and draws no rail`() {
        compose()
        rule.onNodeWithContentDescription("side-panel-objects").assertDoesNotExist()
        rule.onNodeWithContentDescription("side-panel-stub").assertDoesNotExist()
    }

    private companion object {
        const val STUB = "panel-stub-sentence"
    }
}
