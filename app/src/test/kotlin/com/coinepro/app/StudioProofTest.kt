package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText

import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.feature.script.ScriptScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The studio, photographed — the audit of 4.65.0 asked to see the editor rather than read about
 * it: the completion strip with signatures, a diagnostic with its line and column, the console
 * with the run's time, and the strategy card. Frames go to `build/proof/`, and every frame also
 * asserts the thing it shows is on the screen, so a broken studio cannot pose for its picture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StudioProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    @Config(sdk = [34], qualifiers = PHONE_FA)
    fun studioAutocomplete() {
        proof("studio-autocomplete-fa") {
            val series = ScreenshotFixtures.chartSeries()
            val controller = remember {
                ScreenshotFixtures.scriptController(scope).also {
                    it.setSeries(series)
                    it.edit("fast = ta.ema(close, 12)\nslow = ta.sm")
                }
            }
            ScriptScreen(controller = controller, symbol = "XAUUSD", series = series)
        }
        // The strip offers the reference's names for «ta.sm», each with its signature.
        composeRule.onNodeWithText("ta.sma(close, 20)").assertExists()
        composeRule.onNodeWithText("ta.smi(close, 20, 5, 5)").assertExists()
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE_FA)
    fun studioDiagnostic() {
        proof("studio-diagnostic-fa") {
            val series = ScreenshotFixtures.chartSeries()
            val controller = remember {
                ScreenshotFixtures.scriptController(scope).also {
                    it.setSeries(series)
                    it.edit("fast = ta.ema(close, 12)\nplot(fast\nplot(ta.magic(close))")
                }
            }
            ScriptScreen(controller = controller, symbol = "XAUUSD", series = series)
        }
        // The card names the line and the column — the «)» never closed is reported where the
        // parser noticed, at the start of line 3; Persian digits because they are prose counts.
        composeRule.onNodeWithText("خط ۳، ستون ۱", substring = true).assertExists()
    }

    @Test
    @Config(sdk = [34], qualifiers = TALL_PHONE_FA)
    fun studioConsoleAndStrategy() {
        proof("studio-console-strategy-fa") {
            val series = ScreenshotFixtures.chartSeries()
            val controller = remember {
                ScreenshotFixtures.scriptController(scope).also {
                    it.setSeries(series)
                    it.edit(
                        "fast = ta.ema(close, 9)\nslow = ta.ema(close, 21)\n" +
                            "strategy.entry(\"L\", strategy.long, when = ta.crossover(fast, slow))\n" +
                            "strategy.entry(\"S\", strategy.short, when = ta.crossunder(fast, slow))\n" +
                            "log(\"fast \" + str.tostring(fast))",
                    )
                    it.run()
                }
            }
            ScriptScreen(controller = controller, symbol = "XAUUSD", series = series)
        }
        assertTrue(composeRule.onAllNodesWithContentDescription("script-strategy-report").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithContentDescription("script-console-timing").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE_FA)
    fun studioInputs() {
        proof("studio-inputs-fa") {
            val series = ScreenshotFixtures.chartSeries()
            val controller = remember {
                ScreenshotFixtures.scriptController(scope).also {
                    it.setSeries(series)
                    it.edit(
                        "len = input.int(14, title = \"طول\", min = 2, max = 50)\n" +
                            "src = input.source(\"close\", title = \"منبع\")\n" +
                            "show = input.bool(true, title = \"نمایش\")\n" +
                            "tint = input.color(color.gold, title = \"رنگ\")\n" +
                            "plot(ta.rsi(src, len), title = \"RSI\", color = tint, pane = \"own\")",
                    )
                    it.run()
                }
            }
            ScriptScreen(controller = controller, symbol = "XAUUSD", series = series)
        }
        composeRule.onNodeWithText("ورودی‌ها").assertExists()
    }

    private fun proof(name: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            CoineProTheme(darkTheme = true) {
                CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                    Surface(modifier = Modifier.fillMaxSize()) { content() }
                }
            }
        }
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
        val view = composeRule.activity.window.decorView
        if (view.width == 0 || view.height == 0) {
            val metrics = composeRule.activity.resources.displayMetrics
            view.measure(
                View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
            )
            view.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
        }
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        OUTPUT_DIR.mkdirs()
        File(OUTPUT_DIR, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        val OUTPUT_DIR = File("build/proof")
        const val PHONE_FA = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"

        /** The same width, tall enough that the console and the strategy card sit inside one frame. */
        const val TALL_PHONE_FA = "fa-rIR-ldrtl-w411dp-h1500dp-xxhdpi"
    }
}
