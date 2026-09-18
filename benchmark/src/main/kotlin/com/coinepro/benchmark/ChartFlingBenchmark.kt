package com.coinepro.benchmark

import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.coinepro.app"

/** The chart the benchmark opens: the deepest history the app ships, on the busiest market. */
private const val CHART_LINK = "coinepro://market/BTCUSDT"

/** How many samples one pinch is driven through. Forty, the same as the UiAutomator pinch above. */
private const val PINCH_STEPS = 40

/** How far from the centre each finger starts and ends, as a share of the screen's short side. */
private const val PINCH_NEAR = 0.05f
private const val PINCH_FAR = 0.22f

/**
 * Frame times while the chart is flung, pinched and panned — the numbers Phase 4 is judged on.
 *
 * `FrameTimingMetric` reports `frameDurationCpuMs` and `frameOverrunMs` at P50/P90/P95/P99; the
 * budget is **P95 ≤ 8 ms and no overrun during the fling** on a mid-range phone. The gestures are
 * driven by UiAutomator against the middle of the screen rather than a node, because the chart is a
 * `Canvas` and has no children to find — the page opened by [CHART_LINK] puts it there.
 *
 * Runs on a device or an emulator with a GPU; a JVM has no frames to time. From the repo root:
 *
 *     ./gradlew :benchmark:connectedBenchmarkAndroidTest \
 *         -Pandroid.testInstrumentationRunnerArguments.class=com.coinepro.benchmark.ChartFlingBenchmark
 *
 * and read the JSON under `benchmark/build/outputs/connected_android_test_additional_output/`.
 *
 * (Written without a glob on purpose. Kotlin block comments **nest**, unlike Java's: a slash
 * followed by a star inside this KDoc opens a second comment, and the delimiter that ends this
 * one then closes only the inner. It did — the rest of the file became comment, the module
 * stopped compiling, and the error read «Unclosed comment» against the last line, which is
 * about as far from the cause as a diagnostic can get.)
 */
@RunWith(AndroidJUnit4::class)
class ChartFlingBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun flingAcrossHistory() = measure { fling(seconds = 3) }

    /**
     * **Three flicks of increasing speed** (run Τ, item 7).
     *
     * The same finger travel — a quarter of the screen to three quarters — delivered over forty,
     * ten and three samples. UiAutomator hands each sample to the digitiser about five
     * milliseconds apart, so the release velocities are roughly 900, 3 500 and 12 000 px/s on a
     * 1 080-wide panel: a placement, an ordinary flick, and as hard as a thumb can throw.
     *
     * They are separate scenarios rather than one loop because the fling's cost is not flat in its
     * speed. A hard release coasts for two seconds across thousands of pixels, which is thousands
     * of bars of layout and every study on them re-windowed; a slow one settles inside a few
     * frames. Averaging the three reports a number that describes no gesture a reader makes.
     *
     * What they cannot answer is the owner's question — whether the chart *feels* right — which
     * needs a phone. `docs/qa/DEVICE_PROOFS.md` carries the command and what to read out of it.
     */
    @Test
    fun flickVelocitySlow() = measure { flicks(steps = 40) }

    @Test
    fun flickVelocityMedium() = measure { flicks(steps = 10) }

    @Test
    fun flickVelocityHard() = measure { flicks(steps = 3) }

    @Test
    fun pinchZoom() = measure {
        repeat(3) {
            pinch(open = false)
            pinch(open = true)
        }
    }

    /**
     * **The gesture the owner reported as broken** (run Σ, S1).
     *
     * Two fingers separating and closing along a horizontal line through the plot, which is what
     * «بیشتر کندل ببینم» is with a phone in one hand. It is a scenario of its own rather than a mode
     * of [pinchZoom] because the two are different work for the renderer: a time zoom rebuilds the
     * bar layout and every study on it, a price zoom only re-maps y, and averaging them into one
     * number would hide a regression in either.
     *
     * `UiObject2.pinchOpen` cannot stand in for this. It pinches along the object's own widest axis
     * and against its bounds, so on a full-screen object it spends part of its travel on the price
     * gutter — which, correctly, does not zoom time at all.
     */
    @Test
    fun horizontalPinch() = measure {
        repeat(3) {
            plotPinch(vertical = false, open = true)
            plotPinch(vertical = false, open = false)
        }
    }

    /** The same, upright. On the plot this is also a time zoom — see `PinchZone`. */
    @Test
    fun verticalPinch() = measure {
        repeat(3) {
            plotPinch(vertical = true, open = true)
            plotPinch(vertical = true, open = false)
        }
    }

    @Test
    fun panAndHold() = measure {
        repeat(4) { drag() }
        longPress()
    }

    private fun measure(gestures: MacrobenchmarkScope.() -> Unit) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = {
            pressHome()
            startActivityAndWait(
                Intent(Intent.ACTION_VIEW, Uri.parse(CHART_LINK)).setPackage(TARGET_PACKAGE),
            )
            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5_000)
            device.waitForIdle()
        },
        measureBlock = { gestures() },
    )

    /** Repeated flicks from the middle of the plot towards the live edge and back, [seconds] long. */
    private fun MacrobenchmarkScope.fling(seconds: Int) {
        val width = device.displayWidth
        val height = device.displayHeight
        val y = height / 2
        val flicks = seconds * 2
        repeat(flicks) { index ->
            val leftToRight = index % 2 == 0
            val from = if (leftToRight) width / 4 else width * 3 / 4
            val to = if (leftToRight) width * 3 / 4 else width / 4
            // Five steps is a fast flick — about 25 ms of finger travel — which is what produces
            // momentum rather than a drag.
            device.swipe(from, y, to, y, 5)
            device.waitForIdle()
        }
    }

    /**
     * Six flicks into history and back, each one [steps] samples of finger travel.
     *
     * Rightwards first, into loaded history, for the same reason [fling] goes that way: a leftward
     * flick meets the live edge in a few bars and what stops it is the rubber band, not the curve.
     */
    private fun MacrobenchmarkScope.flicks(steps: Int) {
        val width = device.displayWidth
        val y = device.displayHeight / 2
        repeat(6) { index ->
            val rightwards = index % 2 == 0
            val from = if (rightwards) width / 4 else width * 3 / 4
            val to = if (rightwards) width * 3 / 4 else width / 4
            device.swipe(from, y, to, y, steps)
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.pinch(open: Boolean) {
        val root = device.findObject(By.pkg(TARGET_PACKAGE).depth(0)) ?: return
        if (open) root.pinchOpen(0.4f, 40) else root.pinchClose(0.4f, 40)
        device.waitForIdle()
    }

    /**
     * A two-finger pinch on the **plot**, along one axis, with an explicit pointer path.
     *
     * Both pointers are kept clear of the price gutter and of the date strip, so the gesture is
     * routed to the time zoom by where it started — the rule `PinchZone` states. The travel is a
     * fifth of the screen each way over [PINCH_STEPS] samples, which at the default sampling is
     * about a third of a second: fast enough to be a real pinch, slow enough that every frame in
     * between is one the metric sees.
     */
    private fun MacrobenchmarkScope.plotPinch(vertical: Boolean, open: Boolean) {
        val width = device.displayWidth
        val height = device.displayHeight
        // The plot is the top seventy per cent of the page and the gutter is the right eighth; the
        // centre of what is left is a candle on every reader mode.
        val centreX = (width * 0.42f).toInt()
        val centreY = (height * 0.30f).toInt()
        val near = (if (vertical) height else width) * PINCH_NEAR
        val far = (if (vertical) height else width) * PINCH_FAR
        val from = if (open) near else far
        val to = if (open) far else near
        val first = Array(PINCH_STEPS) { step ->
            val reach = from + (to - from) * step / (PINCH_STEPS - 1f)
            pointerAt(
                x = if (vertical) centreX else centreX - reach.toInt(),
                y = if (vertical) centreY - reach.toInt() else centreY,
            )
        }
        val second = Array(PINCH_STEPS) { step ->
            val reach = from + (to - from) * step / (PINCH_STEPS - 1f)
            pointerAt(
                x = if (vertical) centreX else centreX + reach.toInt(),
                y = if (vertical) centreY + reach.toInt() else centreY,
            )
        }
        // `performMultiPointerGesture` lives on `UiObject` — the `UiSelector` API — and on nothing
        // else: not on `UiDevice`, which has only the single-pointer `swipe`, and not on the
        // `UiObject2` that [pinch] above gets back from `findObject(By…)`. Two fingers on an
        // explicit path is the whole point of this gesture, so the selector API is the one to use.
        val surface = device.findObject(UiSelector().packageName(TARGET_PACKAGE))
        surface.performMultiPointerGesture(first, second)
        device.waitForIdle()
    }

    private fun pointerAt(x: Int, y: Int): MotionEvent.PointerCoords = MotionEvent.PointerCoords().also {
        it.x = x.toFloat()
        it.y = y.toFloat()
        it.pressure = 1f
        it.size = 1f
    }

    private fun MacrobenchmarkScope.drag() {
        val width = device.displayWidth
        val y = device.displayHeight / 2
        // Forty steps is a slow drag: the finger stays on the glass, the chart follows it bar by bar.
        device.swipe(width * 3 / 4, y, width / 4, y, 40)
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.longPress() {
        val x = device.displayWidth / 2
        val y = device.displayHeight / 2
        // A swipe of zero distance over many steps is a press held for ~half a second: the
        // crosshair engages, and the frames after it are the crosshair layer redrawing alone.
        device.swipe(x, y, x, y, 100)
        device.waitForIdle()
    }
}
