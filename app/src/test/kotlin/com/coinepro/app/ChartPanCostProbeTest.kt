package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.coinepro.core.chart.ChartViewport
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.designsystem.CoineProTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **What a frame of a drag actually costs** (run Τ2, item 3).
 *
 * «سوایپ به چپ و راست توی چارت خیلی خیلی خیلی کنده.» The fling was arithmetic and could be fixed by
 * reading the arithmetic; a drag that feels heavy is a *cost*, and a cost has to be measured before
 * anything is changed — the last run is what happens when you fix the suspect instead of the cause.
 *
 * A drag has two kinds of frame and they do very different amounts of work:
 *
 *  * a **sub-bar** frame moves the picture by less than one bar. Nothing changes in the window;
 *    the shift is a float the draw pass reads.
 *  * a **bar-step** frame crosses a bar boundary. The window moves, which is snapshot state the
 *    chart reads while it composes — so the whole composable recomposes, and the renderer
 *    invalidates every cached layer.
 *
 * This measures the two against each other on the same chart. The number is not milliseconds on a
 * phone — this container has no GPU and Robolectric's Skia is not the device's — it is the **ratio**,
 * which is a property of the code rather than of the machine, and it is what says whether a drag is
 * dominated by drawing or by recomposing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartPanCostProbeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val viewports = mutableListOf<ChartViewport>()

    private fun chart() {
        composeRule.setContent {
            CoineProTheme(darkTheme = true) {
                CoineProChart(
                    series = ScreenshotFixtures.chartSeries(bars = 4_000),
                    modifier = Modifier.fillMaxSize().testTag(TAG),
                    onViewportChange = { viewports += it },
                )
            }
        }
        composeRule.waitForIdle()
        draw()
    }

    private fun draw() {
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
    }

    /** Nanoseconds for [frames] drag frames, each moving the finger by [step] pixels. */
    private fun drag(step: Float, frames: Int): Long {
        val node = composeRule.onNodeWithTag(TAG)
        var spent = 0L
        node.performTouchInput { down(Offset(width * 0.5f, height * 0.4f)) }
        composeRule.waitForIdle()
        repeat(frames) { i ->
            val started = System.nanoTime()
            node.performTouchInput { moveTo(Offset(width * 0.5f + step * (i + 1), height * 0.4f)) }
            composeRule.waitForIdle()
            draw()
            spent += System.nanoTime() - started
        }
        node.performTouchInput { up() }
        composeRule.waitForIdle()
        return spent
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `a frame that crosses a bar costs more than one that does not, and by how much`() {
        chart()
        val restingBars = viewports.first().barsPerView
        // Sub-bar: a fifth of a bar a frame, so the window never moves.
        val subBar = drag(step = (PLOT_PX / restingBars) * 0.2f, frames = FRAMES)
        val before = viewports.last().offset
        // Bar-crossing: two bars a frame, which is an ordinary drag speed on a phone.
        val barSteps = drag(step = (PLOT_PX / restingBars) * 2f, frames = FRAMES)
        val moved = kotlin.math.abs(viewports.last().offset - before)

        val ratio = barSteps.toDouble() / subBar.toDouble()
        println(
            "pan cost: ${subBar / FRAMES / 1_000}µs a sub-bar frame, " +
                "${barSteps / FRAMES / 1_000}µs a bar-step frame, ratio ${"%.2f".format(ratio)}, " +
                "$moved bars crossed",
        )
        assertTrue("the bar-crossing drag never moved the window", moved > 0)
        // Not an assertion about the ratio — this is a probe, and a threshold here would be a
        // number about this container. It prints, and the number goes in the report.
        assertTrue(subBar > 0 && barSteps > 0)
    }

    private companion object {
        const val TAG = "chart-pan-probe"
        const val PHONE = "fa-rIR-ldrtl-w411dp-h914dp-420dpi"
        const val FRAMES = 40
        const val PLOT_PX = (411f - 64f) * 420f / 160f
    }
}
