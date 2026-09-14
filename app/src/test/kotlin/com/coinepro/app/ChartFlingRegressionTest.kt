package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeWithVelocity
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
 * **The flick that stopped travelling** (run Τ, item 1).
 *
 * The owner measured both apps on one phone, frame by frame: with the same finger TradingView left
 * the glass at about 4 300 px/s and coasted for two seconds; this app left at about 800 px/s, ran
 * for a third of a second, and then crept at one pixel a frame for another two hundred milliseconds
 * before stopping. Rendering was never the problem — 120 frames a second against TradingView's
 * forty — but a smooth animation of the wrong physics is still the wrong physics.
 *
 * These tests drive the **real event stream** through the chart and read the window it lands on,
 * because that is the only place the fault could have been seen. Every piece of the fling had a unit
 * test that passed: the decay curve, the routing, the viewport arithmetic. What nothing measured was
 * a finger's speed arriving at that curve, which is where it was being lost.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartFlingRegressionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val viewports = mutableListOf<ChartViewport>()
    private val generation = mutableIntStateOf(0)
    private var started = false

    private fun chart() {
        viewports.clear()
        if (!started) {
            started = true
            composeRule.setContent {
                CoineProTheme(darkTheme = true) {
                    key(generation.intValue) {
                        CoineProChart(
                            series = ScreenshotFixtures.chartSeries(bars = 4_000),
                            modifier = Modifier.fillMaxSize().testTag(TAG),
                            onViewportChange = { viewports += it },
                        )
                    }
                }
            }
        } else {
            composeRule.runOnUiThread { generation.intValue++ }
        }
        composeRule.waitForIdle()
        // The chart learns its frame in the draw pass, and the fling handler refuses a flick that
        // did not start on the plot — so without one draw every gesture here would be ignored.
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

    /**
     * One flick at [velocity] pixels a second, then the frames it coasts through.
     *
     * Returns how far the chart travelled **in pixels**, and how long the momentum lasted. The
     * distance is bars × the width of a bar, which is what the reader sees move.
     */
    private fun flick(velocity: Float): Travel {
        chart()
        val resting = viewports.first()
        val node = composeRule.onNodeWithTag(TAG)
        composeRule.mainClock.autoAdvance = false
        node.performTouchInput {
            val y = height * 0.4f
            // Rightwards, into history. A leftward flick runs into the live edge within a few
            // bars — the band, not the physics, would be what stopped it, and the measurement
            // would be of a wall.
            swipeWithVelocity(
                start = Offset(width * 0.25f, y),
                end = Offset(width * 0.75f, y),
                endVelocity = velocity,
                durationMillis = 200L,
            )
        }
        composeRule.mainClock.advanceTimeBy(16L)
        composeRule.waitForIdle()
        val afterLift = viewports.last().offset

        var elapsed = 0L
        var settled = 0L
        var last = afterLift
        while (elapsed < 4_000L) {
            composeRule.mainClock.advanceTimeBy(FRAME_MS)
            composeRule.waitForIdle()
            elapsed += FRAME_MS
            val now = viewports.last().offset
            if (now != last) {
                settled = elapsed
                last = now
            }
        }
        composeRule.mainClock.autoAdvance = true
        val barWidth = PLOT_WIDTH_PX / resting.barsPerView.toFloat()
        return Travel(pixels = (last - afterLift) * barWidth, millis = settled)
    }

    private data class Travel(val pixels: Float, val millis: Long)

    /**
     * What this measurement cannot see: the travel still sitting inside the current bar.
     *
     * The chart moves in two parts — whole bars, which change the published window, and a float
     * remainder that shifts the picture without changing it. Only the first is visible from out
     * here, so every figure below is short by up to one bar. The bounds carry that rather than
     * pretending the number is exact, and the bar is about nineteen pixels at the resting zoom.
     */
    private fun quantisation(resting: ChartViewport): Float = PLOT_WIDTH_PX / resting.barsPerView.toFloat()

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `a hard flick carries the chart more than a screen and a half`() {
        // The number the owner measured on TradingView, put back. Before run Τ this travelled about
        // a fifth of it, which is «کند و با استوپ» in one figure.
        val travel = flick(3_000f)
        assertTrue(
            "a 3000 px/s flick moved ${travel.pixels} px — less than 1.5 screens ($MIN_HARD px)",
            kotlin.math.abs(travel.pixels) >= MIN_HARD - quantisation(viewports.first()),
        )
        assertTrue("it was still moving after ${travel.millis} ms", travel.millis <= 2_400L)
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `a gentle flick still coasts rather than stopping under the finger`() {
        val travel = flick(800f)
        // `(800 − 240) / 1.25` is 448 px, which is 0.415 of this screen; the bound is the brief's
        // four tenths less the bar this measurement cannot see. Deliberately not rounded up into
        // the physics: a curve tuned until a test goes green is a curve fitted to a test.
        val floor = SCREEN_PX * 0.4f - quantisation(viewports.first())
        assertTrue(
            "an 800 px/s flick moved ${travel.pixels} px — under 0.4 of a screen",
            kotlin.math.abs(travel.pixels) >= floor,
        )
    }

    private companion object {
        const val TAG = "chart-fling"
        /**
         * The owner's own phone, and the density matters.
         *
         * Every figure in the report is in **pixels**, measured on a 420 dpi screen. A finger
         * crossing the same glass at the same speed produces more pixels a second on a denser
         * panel, so «a 3 000 px/s flick covers a screen and a half» is a statement about that
         * phone. Run at xxhdpi the same physical flick would have to cover fourteen per cent more
         * pixels to earn the same sentence, and the test would be measuring the test device.
         */
        const val PHONE = "fa-rIR-ldrtl-w411dp-h914dp-420dpi"
        const val FRAME_MS = 8L

        /** 411 dp at 420 dpi, less the price ladder the plot does not use. */
        const val DENSITY = 420f / 160f
        const val SCREEN_PX = 411f * DENSITY
        const val PLOT_WIDTH_PX = SCREEN_PX - 64f * DENSITY
        const val MIN_HARD = SCREEN_PX * 1.5f
    }
}
