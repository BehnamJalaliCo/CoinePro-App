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
import com.coinepro.core.chart.ChartViewport
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.chart.Crosshair
import com.coinepro.core.designsystem.CoineProTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Does the chart follow the finger while the finger is still down?** (run Ξ, items 1 and 5.)
 *
 * ### Why this test did not exist, and why the run before it measured the wrong thing
 *
 * Every gesture test in this repository until now drove `swipeWithVelocity`, which injects a whole
 * swipe — down, moves and up — **between two frames**. That measures the *fling*: the momentum
 * after the lift. It cannot see whether the chart moved while the finger was on the glass, because
 * in that harness the finger is never on the glass for a frame.
 *
 * `ChartFlingRegressionTest` even printed the answer and excused it. Its interruption case reports
 * `drag 1` and `drag 0` — the drag moved a bar or nothing — and the comment beside it calls that an
 * artefact of «a harness which injects a whole swipe between two frames». It was not an artefact.
 * It was the defect the owner filmed: bursts of a frame or two, then seconds of nothing while the
 * finger dragged.
 *
 * ### What this drives instead
 *
 * A **stepwise** gesture: `down`, then ten separate `moveTo` calls of twelve pixels each, each one
 * its own `performTouchInput` invocation and therefore its own pointer event, and the window is
 * read **before** the `up`. That is the shape of a real drag, and it is the only shape that can
 * fail the way the recordings fail.
 *
 * A hundred and twenty pixels of travel, less the platform's touch slop, is about a hundred pixels
 * — five or six bars at the resting zoom on this density. The assertion is deliberately weak on the
 * number and absolute on the fact: **the window moved before the finger lifted**.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChartDragTraceTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val viewports = mutableListOf<ChartViewport>()
    private val crosshairs = mutableListOf<Crosshair?>()
    private val generation = mutableIntStateOf(0)
    private var started = false

    private fun chart() {
        viewports.clear()
        crosshairs.clear()
        if (!started) {
            started = true
            composeRule.setContent {
                CoineProTheme(darkTheme = true) {
                    key(generation.intValue) {
                        CoineProChart(
                            series = ScreenshotFixtures.chartSeries(bars = 4_000),
                            modifier = Modifier.fillMaxSize().testTag(TAG),
                            onViewportChange = { viewports += it },
                            onCrosshairMove = { crosshairs += it },
                        )
                    }
                }
            }
        } else {
            composeRule.runOnUiThread { generation.intValue++ }
        }
        composeRule.waitForIdle()
        // The chart learns its frame in the draw pass and the handlers refuse a gesture that did
        // not start on the plot, so without one draw every gesture here would be ignored.
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
     * Ten moves of [step] pixels, read after each one. The last entry is the state before the lift.
     *
     * Rightwards, into history, for the same reason every gesture test here goes that way: a
     * leftward drag meets the live edge within a few bars and what stops it is the rubber band
     * rather than the gesture.
     */
    private fun stepwiseDrag(step: Float): List<Int> {
        chart()
        val resting = viewports.last().offset
        val node = composeRule.onNodeWithTag(TAG)
        val moves = mutableListOf<Int>()
        var start = Offset.Zero
        node.performTouchInput {
            start = Offset(width * 0.3f, height * 0.4f)
            down(0, start)
        }
        composeRule.waitForIdle()
        repeat(MOVES) { index ->
            node.performTouchInput { moveTo(0, start + Offset(step * (index + 1), 0f)) }
            composeRule.waitForIdle()
            moves += viewports.last().offset - resting
        }
        node.performTouchInput { up(0) }
        composeRule.waitForIdle()
        return moves
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the window moves while the finger is still down`() {
        val moves = stepwiseDrag(STEP)
        println("drag frames (bars from rest, one per move): $moves")

        // The whole of item 5, in one line: something happened before the up event.
        assertTrue(
            "ten moves of ${STEP.toInt()} px moved the window $moves — the chart did not follow " +
                "the finger at all while it was down",
            moves.last() != 0,
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `it keeps moving, move after move, rather than once and then never again`() {
        // The shape the owner filmed: a burst of one or two frames, then seconds of exactly zero
        // with the finger still on the glass. A chart that answers the first move and then stops is
        // a chart whose pan was cancelled — which a test asserting only «it moved» would pass.
        val moves = stepwiseDrag(STEP)
        val advancing = moves.zipWithNext().count { (before, after) -> after != before }
        println("moves that changed the window: $advancing of ${moves.size - 1}")
        assertTrue(
            "the window changed on only $advancing of the ${moves.size - 1} moves after the first: " +
                "$moves",
            advancing >= MOVES / 2,
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the travel is the finger's, less one slop`() {
        // Item 6: the slop is spent once, at the start, and what is beyond it is not thrown away.
        // Twelve pixels a move for ten moves is 120 px; the platform slop on this density is about
        // twenty-one. The bound is generous — this is a statement about *not discarding*, not a
        // pixel measurement — and the bar is about nineteen pixels at the resting zoom.
        val moves = stepwiseDrag(STEP)
        val travelled = kotlin.math.abs(moves.last())
        println("travelled $travelled bars over ${MOVES * STEP.toInt()} px of finger")
        assertTrue(
            "a ${MOVES * STEP.toInt()} px drag moved the window $travelled bars — the slop was " +
                "taken more than once, or the remainder was dropped",
            travelled >= MIN_BARS,
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `a fast horizontal drag never lands on the crosshair`() {
        // **Run Ξ, item 7.** Scrubbing is a long press or the dedicated tool, and nothing else. A
        // plain drag that engaged the crosshair would be the chart reading out one bar while the
        // reader was trying to move the window — and the pan is suspended while tracking, so it
        // would look exactly like the defect this run is about.
        stepwiseDrag(STEP)
        val engaged = crosshairs.filterNotNull()
        println("crosshair emissions during a plain drag: ${engaged.size}")
        assertTrue(
            "a ${MOVES * STEP.toInt()} px drag engaged the crosshair ${engaged.size} times",
            engaged.isEmpty(),
        )
    }

    private companion object {
        const val TAG = "chart-drag-trace"

        /** The owner's own phone. See `ChartFlingRegressionTest` for why the density is pinned. */
        const val PHONE = "fa-rIR-ldrtl-w411dp-h914dp-420dpi"

        const val MOVES = 10
        const val STEP = 12f

        /**
         * The floor on travel, in bars.
         *
         * 120 px of finger, less about 21 px of platform slop, is ~99 px; a bar is about nineteen
         * at the resting zoom, so five is the honest arithmetic. Four is stated instead, because a
         * bar's width depends on the plot's width and this is a test about the gesture arriving,
         * not about the zoom.
         */
        const val MIN_BARS = 4
    }
}
