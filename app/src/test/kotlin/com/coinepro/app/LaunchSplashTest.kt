package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **The launch stopped stuttering** (4.87.1, the owner's second item).
 *
 * «انیمیشنش گیر داره.» It did, and the cause was not in the curve: the whole app was composing
 * underneath while the sheet was moving. The first composition of that tree is the most expensive
 * thing this app ever does, it runs on the main thread, and the sheet's clock is a wall clock — so a
 * blocked thread does not slow the wipe down, it makes it *skip*. That is what «گیر» looks like.
 *
 * The order is now: draw the lockup, tell the caller it is done, hold still while the app composes,
 * then fade. Two of the three steps in that sentence are things a test can hold, and they are the
 * two that would be silently undone by a later edit:
 *
 *  * the sheet says when it has finished drawing, **before** it finishes,
 *  * and it waits for the app rather than fading over a half-composed screen — but not for ever,
 *    because a launch that waits for ever is a white screen.
 *
 * The smoothness itself is a thing for eyes on a device: `ScreenshotRenderTest.launchSplash` holds
 * the finished frame, and the owner's recording is what closes the rest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LaunchSplashTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun `the sheet says the lockup is drawn before it says the launch is over`() {
        // The order is the whole point. `onDrawn` is what lets the app compose under a still sheet;
        // if it arrived with `onFinished` it would be telling the caller something it already knew.
        val order = mutableListOf<String>()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            LaunchSplash(
                onFinished = { order += "finished" },
                onDrawn = { order += "drawn" },
                appReady = true,
            )
        }
        rule.mainClock.advanceTimeBy(4_000L)
        rule.waitForIdle()

        assertEquals(listOf("drawn", "finished"), order)
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun `the sheet holds while the app is still composing, and gives up rather than waiting for ever`() {
        // `appReady` never becomes true here — the worst case, a phone whose first composition is
        // slower than the whole launch. The sheet must still go: a cap that did not exist would be
        // a white screen with a logo on it, which is the one outcome worse than a rough animation.
        var finished = false
        rule.mainClock.autoAdvance = false
        rule.setContent {
            LaunchSplash(onFinished = { finished = true }, appReady = false)
        }

        // Past the draw-in, into the hold. Nothing has been handed over yet.
        rule.mainClock.advanceTimeBy(1_300L)
        rule.waitForIdle()
        assertFalse("the sheet went while the app was still composing", finished)

        // And past the cap.
        rule.mainClock.advanceTimeBy(3_000L)
        rule.waitForIdle()
        assertTrue("the sheet waited for ever on an app that never arrived", finished)
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun `three frames of the draw-in, because smooth is a thing you look at`() {
        // A curve cannot be asserted and can be looked at. Three frames off one clock: the mark
        // part-drawn, the hand-over where the mark is nearly complete and the name has already
        // started — the overlap that replaced the dead stretch — and the landed lockup.
        rule.mainClock.autoAdvance = false
        rule.setContent { LaunchSplash(onFinished = {}, appReady = true) }
        listOf(320L to "launch-draw-320ms", 300L to "launch-draw-620ms", 480L to "launch-landed").forEach {
            (step, name) ->
            rule.mainClock.advanceTimeBy(step)
            rule.waitForIdle()
            capture(name)
        }
    }

    private fun capture(name: String) {
        val view = rule.activity.window.decorView
        val metrics = rule.activity.resources.displayMetrics
        if (view.width == 0 || view.height == 0) {
            view.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        OUTPUT.mkdirs()
        File(OUTPUT, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        val OUTPUT = File("build/proof")
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun `with animations off the reader is shown the finished lockup and nothing draws itself`() {
        // Unchanged behaviour, kept under the new clock: somebody who turned animations off asked
        // not to watch shapes draw themselves, and the hold must not turn that into a longer wait
        // than the still it replaces.
        var finished = false
        rule.mainClock.autoAdvance = false
        rule.setContent {
            LaunchSplash(onFinished = { finished = true }, moving = false, appReady = true)
        }

        rule.mainClock.advanceTimeBy(4_000L)
        rule.waitForIdle()
        assertTrue("the still never handed over", finished)
    }
}
