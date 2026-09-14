package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.coinepro.core.datastore.ReaderMode
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.feature.chart.ChartScreen
import java.io.File
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

/**
 * **The box beside the timeframe was empty** (the owner's screenshot, 4.88.0).
 *
 * The pill drew its frame and its two carets and no ticker at all — a control that says «something
 * belongs here» and shows nothing, which is worse than the control being absent. The wheel is how a
 * reader changes instrument without leaving the chart, so an empty pill is the feature missing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SymbolWheelBandTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun band(wheel: List<String>, frame: String, dark: Boolean = false) {
        val controller = ScreenshotFixtures.chartController(scope, SYMBOL)
        composeRule.setContent {
            CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                CoineProTheme(darkTheme = dark) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val ready = remember { controller }
                        ChartScreen(
                            controller = ready,
                            onBack = {},
                            readerMode = ReaderMode.TRADER,
                            wheelSymbols = wheel,
                            onSelectSymbol = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
        val view = composeRule.activity.window.decorView
        val metrics = composeRule.activity.resources.displayMetrics
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
        File(OUTPUT, "$frame.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        composeRule.waitForIdle()
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the wheel shows the instrument the chart is on`() {
        band(listOf("BTCUSDT", "ETHUSDT", SYMBOL, "XRPUSDT"), "tau2-wheel-ring-fa")
        // The ticker is drawn with a bidi isolate around it, so the match is on the bare letters
        // inside whatever the node reports.
        val nodes = composeRule.onAllNodesWithText(SYMBOL, substring = true).fetchSemanticsNodes()
        assertTrue("the wheel drew no ticker at all", nodes.isNotEmpty())
        // **The assertion that would have caught this.** The ticker was in the tree the whole time
        // — the old test passed on the broken build — and it was measured `210 x 1`: five rows of
        // eighteen points in a pill of thirty-six, so the first two took everything and the one
        // carrying the instrument got a single pixel. Presence was never the question; height was.
        val row = nodes.first().size.height
        assertTrue("the ticker is $row px tall, so it draws nothing", row >= MIN_ROW_PX)
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `a ring of one still shows its name`() {
        // The case the owner hit: one symbol to turn through — or none the app has artwork for —
        // must still print the instrument the chart is on. A wheel that cannot turn is a label,
        // and a label is what the reader needs from it anyway.
        band(listOf(SYMBOL), "tau2-wheel-one-fa")
        val drawn = composeRule.onAllNodesWithText(SYMBOL, substring = true).fetchSemanticsNodes().size
        assertTrue("a one-symbol ring drew nothing", drawn >= 1)
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the same wheel on the dark stage`() {
        // The same ring, the other palette. Two frames of one control is how a colour that resolves
        // to the wrong end of the palette is caught: it is invisible in one theme and obvious in
        // the other, and a single render can be looked at happily for months.
        band(listOf("BTCUSDT", "ETHUSDT", SYMBOL, "XRPUSDT"), "tau2-wheel-ring-dark-fa", dark = true)
        val drawn = composeRule.onAllNodesWithText(SYMBOL, substring = true).fetchSemanticsNodes().size
        assertTrue("the wheel drew no ticker at all", drawn >= 1)
    }

    private companion object {
        val OUTPUT = File("build/proof")
        const val PHONE = "fa-rIR-ldrtl-w411dp-h914dp-420dpi"
        const val SYMBOL = "DOGEUSDT"

        /** A row is 18 dp; at 420 dpi that is 47 px. Anything near zero is the bug this is about. */
        const val MIN_ROW_PX = 40
    }
}
