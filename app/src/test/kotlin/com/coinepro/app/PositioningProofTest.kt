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
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.coinepro.core.common.Entitlements
import com.coinepro.core.datastore.ReaderMode
import com.coinepro.core.designsystem.CoineProFreeBanner
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.designsystem.TeachingDismissals
import com.coinepro.core.symbols.SymbolArtwork
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier
import com.coinepro.feature.chart.ChartScreen
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.feature.chart.R as ChartR

/**
 * **Crypto first, and gold is still a first-class chart** (run ΤΦΥ, F4 and F9).
 *
 * The positioning that phase Φ ships is «خانه‌ی تحلیل کریپتو — با یک نگاه به طلا و دلار», and the
 * half of that sentence which is easy to get wrong is the second one. Taking the *account* away
 * from the forex side is a product decision; taking the *chart* away would be a different app, and
 * a reader who opens XAUUSD to find a page with fewer tools on it than BTCUSDT has been told which
 * markets this product takes seriously.
 *
 * So the frame is gold, charted, with an indicator on it and the drawing tools on the band — and
 * the assertions are the list F4 names, checked against the same screen a crypto market gets.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PositioningProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private var started = false

    /**
     * A dismissal set that starts empty and remembers, for the life of one test.
     *
     * `AllTeachingDismissed` cannot be used for the banner: a harness that begins with everything
     * dismissed has nothing to show, and a harness that never records a dismissal cannot show one
     * going away. Snapshot-backed so the composition sees the change.
     */
    private val dismissals = object : TeachingDismissals {
        private val closed = mutableStateSetOf<String>()
        override val dismissed: Set<String> get() = closed
        override fun dismiss(key: String) { closed += key }
        override fun restore(key: String) { closed -= key }
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `gold charts with an indicator and the drawing tools, exactly as a coin does`() {
        val controller = ScreenshotFixtures.chartController(scope, FX)
        controller.start()
        controller.toggleIndicator(INDICATOR)
        render {
            val ready = remember { controller }
            ChartScreen(controller = ready, onBack = {}, readerMode = ReaderMode.TRADER)
        }
        capture("phi-forex-chart-phone-fa")

        // The instrument is what it says it is, and it is not a coin.
        assertEquals(SymbolCategory.METAL, SymbolClassifier.classify(FX).category)
        // It is listed — the artwork rule never applied to it, and after F1 it does not apply to
        // anything, but a metal losing its row would be the loudest possible version of this bug.
        assertTrue(SymbolArtwork.lists(FX))

        // The indicator went on. `activeIndicators` is the controller's own answer rather than a
        // count of pixels: what F4 is about is whether the tool *works* on this market.
        assertTrue(
            "the indicator did not take on a metal: ${controller.state.value.activeIndicators}",
            INDICATOR in controller.state.value.activeIndicators,
        )

        // The band a reader reaches every other tool from, on this market's chart: the pencil (the
        // drawings), the indicators, the hub and the fullscreen. Run Τ item 6 pins the order; this
        // pins that the band is there at all on a market the product no longer lets you trade.
        listOf(
            ChartR.string.chart_band_draw,
            ChartR.string.chart_band_indicators,
            ChartR.string.chart_band_more,
            ChartR.string.chart_band_fullscreen,
        ).forEach { label ->
            val text = composeRule.activity.getString(label)
            assertEquals(
                "«$text» is missing from gold's own chart",
                1,
                composeRule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().size,
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `where a wall used to be, one sentence that closes for ever`() {
        assertTrue("the walls are back on; this frame would be a lie", Entitlements.all)
        render { CoineProFreeBanner(key = "academy") }
        capture("phi-free-banner-phone-fa")

        val sentence = composeRule.activity.getString(DesignR.string.brand_positioning_free)
        assertTrue(
            "the banner says nothing",
            composeRule.onAllNodesWithText(sentence).fetchSemanticsNodes().isNotEmpty(),
        )
        // And it goes away on a tap, for ever — the dismissal is the whole design. `AllTeachingDismissed`
        // is not used here, because a harness that starts with everything dismissed cannot show this.
        val close = composeRule.activity.getString(DesignR.string.free_banner_dismiss)
        composeRule.onAllNodesWithContentDescription(close)[0].performClick()
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
        assertTrue(
            "the banner came back after being closed",
            composeRule.onAllNodesWithText(sentence).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun render(content: @Composable () -> Unit) {
        check(!started) { "one composition per test: an activity's content is set once" }
        started = true
        composeRule.setContent {
            CompositionLocalProvider(LocalTeachingDismissals provides dismissals) {
                CoineProTheme(darkTheme = true) {
                    Surface(modifier = Modifier.fillMaxSize()) { content() }
                }
            }
        }
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
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
        File(OUTPUT, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        val OUTPUT = File("build/proof")
        const val PHONE = "fa-rIR-ldrtl-w411dp-h914dp-420dpi"

        /** Gold. The instrument this product was built around, and the one F4 is measured on. */
        const val FX = "XAUUSD"

        /** A plain moving average — the tool a reader reaches for first on any market. */
        const val INDICATOR = "ema"
    }
}
