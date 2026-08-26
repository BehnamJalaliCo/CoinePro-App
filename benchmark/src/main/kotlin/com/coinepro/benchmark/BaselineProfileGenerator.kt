package com.coinepro.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records what ART should compile ahead of the first frame.
 *
 * Run this on a device or emulator and copy the result over `app/src/main/baseline-prof.txt`; the
 * file checked in there is hand-written and says so. The difference is not cosmetic — a recorded
 * profile carries method entries collected from a real run, and only those let ART AOT-compile the
 * hot path rather than merely preload the classes.
 *
 * The journey below is deliberately more than a cold launch. A profile recorded from
 * `startActivityAndWait()` alone covers the Application, the activity and the first composition,
 * and misses the two things that dominate the *felt* start of this app: the market list settling
 * as the feed connects, and the first scroll. Both are on the path a reader actually takes, and
 * both are worth compiling.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = PACKAGE,
        // Several iterations rather than one: a profile from a single run records whatever the
        // JIT happened to reach that time, and the first run of a cold app is the least
        // representative of them.
        maxIterations = 5,
        stableIterations = 2,
    ) {
        pressHome()
        startActivityAndWait()

        // Wait for content rather than for a fixed delay. A sleep long enough to be safe on a slow
        // emulator is dead time on every other run, and one short enough to be quick records a
        // profile of a half-drawn screen.
        device.wait(Until.hasObject(By.scrollable(true)), CONTENT_TIMEOUT_MS)

        device.findObject(By.scrollable(true))?.let { list ->
            list.setGestureMargin(device.displayWidth / MARGIN_FRACTION)
            repeat(2) {
                list.fling(Direction.DOWN)
                device.waitForIdle()
            }
            list.fling(Direction.UP)
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE = "com.coinepro.app"
        const val CONTENT_TIMEOUT_MS = 10_000L

        /**
         * Keeps a fling from starting inside the system gesture area at the screen edge, where it
         * is swallowed as a back gesture and the scroll never happens — which records a profile
         * with no scrolling in it and no error to say so.
         */
        const val MARGIN_FRACTION = 5
    }
}
