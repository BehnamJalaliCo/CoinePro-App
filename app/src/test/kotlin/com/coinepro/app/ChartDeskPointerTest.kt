package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import com.coinepro.core.chart.ChartViewport
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.chart.Crosshair
import com.coinepro.core.designsystem.CoineProTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The chart under a mouse: the wheel zooms, a hover reads, the right button opens the reading.
 *
 * Injected through the Compose test rule's mouse, which is the same path a real pointer takes
 * into `pointerInput` — a unit test of the handler's arithmetic would prove the arithmetic and
 * not that the handler sees the event. What is asserted is what the chart *reports*: the viewport
 * it publishes after a wheel notch, and the crosshair it publishes under a resting pointer.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartDeskPointerTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val viewports = mutableListOf<ChartViewport>()
    private val crosshairs = mutableListOf<Crosshair?>()

    private fun chart() {
        composeRule.setContent {
            CoineProTheme(darkTheme = true) {
                CoineProChart(
                    series = ScreenshotFixtures.chartSeries(),
                    modifier = Modifier.fillMaxSize().testTag(TAG),
                    onViewportChange = { viewports += it },
                    onCrosshairMove = { crosshairs += it },
                )
            }
        }
        composeRule.waitForIdle()
        // Robolectric composes but does not run the render pipeline, and the chart learns its
        // frame — the plot the crosshair is measured against — in its draw pass. One draw into an
        // off-screen bitmap, the way the golden rig captures a screen, gives it that.
        val view = composeRule.activity.window.decorView
        val metrics = composeRule.activity.resources.displayMetrics
        if (view.width == 0 || view.height == 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        view.draw(Canvas(Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)))
        composeRule.waitForIdle()
    }

    @Test
    @Config(sdk = [34], qualifiers = TABLET)
    fun `a wheel notch towards the reader zooms in at the cursor`() {
        chart()
        val before = viewports.last().visibleCount
        composeRule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(width * 0.5f, height * 0.5f))
            scroll(-1f)
        }
        composeRule.waitForIdle()
        val after = viewports.last().visibleCount
        assertTrue("wheel up should show fewer bars: $before -> $after", after < before)
    }

    @Test
    @Config(sdk = [34], qualifiers = TABLET)
    fun `a wheel notch away from the reader zooms out`() {
        chart()
        val before = viewports.last().visibleCount
        composeRule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(width * 0.5f, height * 0.5f))
            scroll(1f)
        }
        composeRule.waitForIdle()
        assertTrue(viewports.last().visibleCount > before)
    }

    @Test
    @Config(sdk = [34], qualifiers = TABLET)
    fun `a resting pointer reads the chart and leaving clears it`() {
        chart()
        composeRule.onNodeWithTag(TAG).performMouseInput {
            enter(Offset(width * 0.4f, height * 0.4f))
            moveTo(Offset(width * 0.5f, height * 0.5f))
        }
        composeRule.waitForIdle()
        assertNotNull("hover should place a crosshair", crosshairs.lastOrNull())
        composeRule.onNodeWithTag(TAG).performMouseInput { exit() }
        composeRule.waitForIdle()
        assertNull("leaving should clear it", crosshairs.last())
    }

    @Test
    @Config(sdk = [34], qualifiers = TABLET)
    fun `the secondary button reads the chart and keeps the reading`() {
        chart()
        composeRule.onNodeWithTag(TAG).performMouseInput {
            moveTo(Offset(width * 0.5f, height * 0.5f))
            click(Offset(width * 0.5f, height * 0.5f), MouseButton.Secondary)
            exit()
        }
        composeRule.waitForIdle()
        // Tracking mode: the crosshair the right button placed survives the pointer leaving, the
        // way a long-press reading survives the finger lifting.
        assertNotNull(crosshairs.last())
        assertEquals(crosshairs.last(), crosshairs.filterNotNull().last())
    }

    private companion object {
        const val TAG = "chart"
        const val TABLET = "fa-rIR-ldrtl-sw800dp-w1280dp-h800dp-xhdpi"
    }
}
