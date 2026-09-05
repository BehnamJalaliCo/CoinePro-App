package com.coinepro.benchmark

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TARGET_PACKAGE = "com.coinepro.app"

/** The chart the benchmark opens: the deepest history the app ships, on the busiest market. */
private const val CHART_LINK = "coinepro://market/BTCUSDT"

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
 * and read `benchmark/build/outputs/connected_android_test_additional_output/…/*.json`.
 */
@RunWith(AndroidJUnit4::class)
class ChartFlingBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun flingAcrossHistory() = measure { fling(seconds = 3) }

    @Test
    fun pinchZoom() = measure {
        repeat(3) {
            pinch(open = false)
            pinch(open = true)
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

    private fun MacrobenchmarkScope.pinch(open: Boolean) {
        val root = device.findObject(By.pkg(TARGET_PACKAGE).depth(0)) ?: return
        if (open) root.pinchOpen(0.4f, 40) else root.pinchClose(0.4f, 40)
        device.waitForIdle()
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
