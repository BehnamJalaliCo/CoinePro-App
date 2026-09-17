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
     * The same flick, reported **frame by frame**: how many bars the window moved on each frame.
     *
     * [flick] answers «how far» and «for how long», and a fling can be right on both and still be
     * wrong: the whole of the travel delivered on one frame is the same distance in the same time
     * and it is not an animation, it is a jump. That is what run Υ item 1 is about, so it is
     * measured rather than reasoned about.
     */
    private fun frames(velocity: Float): List<Int> {
        chart()
        val node = composeRule.onNodeWithTag(TAG)
        composeRule.mainClock.autoAdvance = false
        node.performTouchInput {
            val y = height * 0.4f
            swipeWithVelocity(
                start = Offset(width * 0.25f, y),
                end = Offset(width * 0.75f, y),
                endVelocity = velocity,
                durationMillis = 200L,
            )
        }
        composeRule.mainClock.advanceTimeBy(16L)
        composeRule.waitForIdle()
        var last = viewports.last().offset
        val steps = mutableListOf<Int>()
        var elapsed = 0L
        while (elapsed < 4_000L) {
            composeRule.mainClock.advanceTimeBy(FRAME_MS)
            composeRule.waitForIdle()
            elapsed += FRAME_MS
            val now = viewports.last().offset
            steps += now - last
            last = now
        }
        composeRule.mainClock.autoAdvance = true
        return steps
    }

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
    fun `the speed the finger left at is the speed that reaches the curve`() {
        // **Run Τ, item 1.** The one thing a distance test cannot separate: a chart that travels
        // too little because the curve is wrong, and one that travels too little because the speed
        // handed to the curve was divided by the display density on the way. Both look identical
        // from the outside and only the second is a bug in the gesture path.
        //
        // So the velocity is read back out of the travel. The curve is closed-form — a release at
        // `v` covers `(v − cut-off) / f` — which makes the distance an invertible measurement of
        // the speed that entered it. A 3 000 px/s flick divided by this phone's 2.625 would arrive
        // as 1 143 and read back as 1 143, not as 3 000.
        val travel = flick(3_000f)
        val bar = quantisation(viewports.first())
        val entered = kotlin.math.abs(travel.pixels) * FRICTION + CUT_OFF
        println("velocity: injected 3000 px/s, read back ${entered.toInt()} px/s from ${travel.pixels} px")
        assertTrue(
            "a 3 000 px/s finger reached the fling curve at ${entered.toInt()} px/s",
            kotlin.math.abs(entered - 3_000f) <= 300f + bar * FRICTION,
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `a slow drag places the chart and never throws it`() {
        // **Run Τ, item 4.** A finger that crosses the glass slowly and stops is positioning the
        // chart, not throwing it, and momentum on top of a placement is the picture walking away
        // from where the reader put it. The cut-off is what refuses it — see `KineticScroll`.
        chart()
        val node = composeRule.onNodeWithTag(TAG)
        composeRule.mainClock.autoAdvance = false
        node.performTouchInput {
            val y = height * 0.4f
            swipeWithVelocity(
                start = Offset(width * 0.3f, y),
                end = Offset(width * 0.7f, y),
                endVelocity = 120f,
                durationMillis = 700L,
            )
        }
        composeRule.mainClock.advanceTimeBy(16L)
        composeRule.waitForIdle()
        val afterLift = viewports.last().offset
        var elapsed = 0L
        while (elapsed < 1_500L) {
            composeRule.mainClock.advanceTimeBy(FRAME_MS)
            composeRule.waitForIdle()
            elapsed += FRAME_MS
        }
        composeRule.mainClock.autoAdvance = true
        val coasted = viewports.last().offset - afterLift
        // Not zero: lifting the finger springs the pan to the nearest whole bar, which is at most
        // one bar and is the settle, not momentum.
        assertTrue("a 120 px/s release coasted $coasted bars after the lift", kotlin.math.abs(coasted) <= 1)
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

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the flick is an animation and not a jump`() {
        // **Run Υ item 1.** «چارت کُپ میکنه زمانی که به چپ و راست سوائپ میکنم.»
        //
        // The fling loop reads the frame clock with `withFrameNanos` and hands what it reads to a
        // curve that measures its own elapsed time in **milliseconds**. One frame is 16 million of
        // the first unit and 16 of the second, so on the very first frame after the start the curve
        // is asked where it has got to after four and a half hours: the answer is «all the way»,
        // and the whole of a flick's travel — two or three screens since run Τ widened it — is
        // applied to the window between one frame and the next. The chart does not coast, it
        // teleports and lands hard, usually against the end of the loaded history. Nothing that
        // measured *distance* could see it, which is why both tests above passed through it.
        val steps = frames(3_000f)
        val moving = steps.count { it != 0 }
        val biggest = steps.maxOfOrNull { kotlin.math.abs(it) } ?: 0
        val total = steps.sumOf { kotlin.math.abs(it) }
        println("fling frames: $moving moving of ${steps.size}, biggest $biggest bars, total $total bars")
        assertTrue("the flick moved nothing at all", total > 0)
        // A two-second coast at 120 Hz is on the order of two hundred frames. Twenty is a floor far
        // below that and still far above the two a teleport takes, so it states the shape of the
        // defect rather than pinning a number this container happens to produce.
        assertTrue("the whole flick was delivered over $moving frames", moving >= 20)
        assertTrue(
            "one frame moved $biggest of the $total bars — that is a jump, not a fling",
            biggest <= total / 4 + 1,
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `a flick that lands on a chart still coasting is a flick like any other`() {
        // **Run Υ item 1.** «چارت کُپ میکنه زمانی که به چپ و راست سوائپ میکنم» — swiping *back and
        // forth*, which is a second gesture arriving while the first is still coasting. Run Τ made
        // that the ordinary case rather than the rare one: a flick used to be over in a third of a
        // second and now runs for two, so a reader working the chart left and right is almost always
        // interrupting one.
        chart()
        val node = composeRule.onNodeWithTag(TAG)
        composeRule.mainClock.autoAdvance = false

        fun flickRight() = node.performTouchInput {
            val y = height * 0.4f
            swipeWithVelocity(
                start = Offset(width * 0.25f, y),
                end = Offset(width * 0.75f, y),
                endVelocity = 3_000f,
                durationMillis = 200L,
            )
        }

        flickRight()
        composeRule.mainClock.advanceTimeBy(16L)
        composeRule.waitForIdle()
        // A few frames in — still coasting, nowhere near settled.
        repeat(8) {
            composeRule.mainClock.advanceTimeBy(FRAME_MS)
            composeRule.waitForIdle()
        }
        val interrupted = viewports.last().offset

        // The second gesture, on a chart that has not stopped.
        flickRight()
        composeRule.mainClock.advanceTimeBy(16L)
        composeRule.waitForIdle()
        val afterSecondLift = viewports.last().offset
        var elapsed = 0L
        while (elapsed < 3_000L) {
            composeRule.mainClock.advanceTimeBy(FRAME_MS)
            composeRule.waitForIdle()
            elapsed += FRAME_MS
        }
        val settled = viewports.last().offset
        composeRule.mainClock.autoAdvance = true

        // The same gesture on a chart that was standing still, for the comparison. On its own
        // «the drag moved nothing» says as much about this harness — which injects a whole swipe
        // between two frames — as about the chart.
        chart()
        val resting = viewports.last().offset
        composeRule.mainClock.autoAdvance = false
        flickRight()
        composeRule.mainClock.advanceTimeBy(16L)
        composeRule.waitForIdle()
        val restingLift = viewports.last().offset
        var rested = 0L
        while (rested < 3_000L) {
            composeRule.mainClock.advanceTimeBy(FRAME_MS)
            composeRule.waitForIdle()
            rested += FRAME_MS
        }
        val restingSettled = viewports.last().offset
        composeRule.mainClock.autoAdvance = true

        println(
            "interrupted: held at $interrupted, lift $afterSecondLift, settled $settled " +
                "(drag ${afterSecondLift - interrupted}, coast ${settled - afterSecondLift}) | " +
                "from rest: $resting, lift $restingLift, settled $restingSettled " +
                "(drag ${restingLift - resting}, coast ${restingSettled - restingLift})",
        )
        assertTrue(
            "a flick onto a coasting chart carried ${settled - afterSecondLift} bars against " +
                "${restingSettled - restingLift} from rest — the second gesture is not being taken",
            kotlin.math.abs(settled - afterSecondLift) >= kotlin.math.abs(restingSettled - restingLift) / 2,
        )
        assertTrue(
            "the drag moved ${afterSecondLift - interrupted} bars against ${restingLift - resting} at rest",
            kotlin.math.abs(afterSecondLift - interrupted) >= kotlin.math.abs(restingLift - resting) / 2,
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

        /** The curve's own two numbers, so the travel can be read back as a speed. */
        const val FRICTION = 1.25f
        const val CUT_OFF = 240f
    }
}
