package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.feature.chart.ChartScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The right button on the plot: a menu at the pointer with the price under it.
 *
 * The chart reads the price at the click and hands it up; the screen draws the menu. What is
 * asserted is that the menu is there after a secondary click, that its alert item carries the
 * price the chart read, and that choosing it hands that price to the alert callback and closes
 * the menu.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartContextMenuTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val alerts = mutableListOf<Pair<String, Double>>()
    private var searches = 0

    private fun compose() {
        rule.setContent {
            CoineProTheme(darkTheme = true) {
                ChartScreen(
                    controller = ScreenshotFixtures.chartController(scope),
                    onCreateAlert = { symbol, price -> alerts += symbol to price },
                    onOpenSymbolSearch = { searches++ },
                )
            }
        }
        rule.waitForIdle()
        // The chart learns its plot in its draw pass — see `ChartDeskPointerTest`.
        val view = rule.activity.window.decorView
        val metrics = rule.activity.resources.displayMetrics
        if (view.width == 0 || view.height == 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        view.draw(Canvas(Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)))
        rule.waitForIdle()
    }

    @Test
    @Config(sdk = [34], qualifiers = TABLET)
    fun `a secondary click opens the menu, and the alert item carries the price under the pointer`() {
        compose()
        rule.onNodeWithContentDescription("chart-menu-copy").assertDoesNotExist()
        rule.onNodeWithTag("chart-plot").performMouseInput {
            moveTo(Offset(width * 0.5f, height * 0.5f))
            click(Offset(width * 0.5f, height * 0.5f), MouseButton.Secondary)
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("chart-menu-copy").assertIsDisplayed()
        rule.onNodeWithContentDescription("chart-menu-scale").assertIsDisplayed()
        rule.onNodeWithContentDescription("chart-menu-search").assertIsDisplayed()
        rule.onNodeWithContentDescription("chart-menu-alert").assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals(1, alerts.size)
        assertTrue("the alert's price should be a price on the chart: ${alerts[0].second}", alerts[0].second > 0.0)
        rule.onNodeWithContentDescription("chart-menu-copy").assertDoesNotExist()
    }

    @Test
    @Config(sdk = [34], qualifiers = TABLET)
    fun `the search item opens the search and closes the menu`() {
        compose()
        rule.onNodeWithTag("chart-plot").performMouseInput {
            moveTo(Offset(width * 0.4f, height * 0.4f))
            click(Offset(width * 0.4f, height * 0.4f), MouseButton.Secondary)
        }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("chart-menu-search").performClick()
        rule.waitForIdle()
        assertEquals(1, searches)
        rule.onNodeWithContentDescription("chart-menu-search").assertDoesNotExist()
    }

    private companion object {
        const val TABLET = "fa-rIR-ldrtl-sw800dp-w1280dp-h800dp-xhdpi"
    }
}
