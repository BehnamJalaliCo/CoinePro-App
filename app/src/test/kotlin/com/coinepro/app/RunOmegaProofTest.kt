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
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.feature.chart.ChartController
import com.coinepro.feature.chart.ChartScreen
import com.coinepro.feature.chart.ExplainSheetBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
 * **The chart that talks**, photographed (run Ω1).
 *
 * Three frames and the assertions behind them: the Signal Layer on a phone chart with two built-ins
 * and a reader's own script on it, the same thing in English, and the Explain sheet's body — the
 * sentence, the base rate with its sample size, the horizon chips and the last five outcomes.
 *
 * The Explain frame is the **body** rather than the sheet, for the reason run K's indicator frame
 * was: a Material bottom sheet renders into a window of its own and this capture is of the
 * activity's decor view. The body is the same composable the sheet wraps and the tablet will dock.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RunOmegaProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    /** A reader's own study that says what it means — the short form of `signal(...)`. */
    private val myScript = """
        fast = ta.ema(close, 9)
        slow = ta.ema(close, 21)
        plot(fast, title = "Fast")
        signal(ta.crossover(fast, slow), text = "Fast crossed the slow average")
    """.trimIndent()

    private fun charted(symbol: String = "BTCUSDT"): ChartController =
        ScreenshotFixtures.chartController(scope, symbol).also {
            it.toggleIndicator("ema")
            it.toggleIndicator("rsi")
            it.addScript(name = "My crossing", source = myScript)
        }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun theChartSaysWhatItIsSaying() {
        val controller = charted()
        proof("omega-signal-layer-fa-dark") {
            val ready = remember { controller }
            ChartScreen(controller = ready)
        }
        val layer = controller.state.value.signals
        assertTrue("nothing on the chart had an opinion", layer.reads.isNotEmpty())
        // Every study on the chart is in the strip — two built-ins and the reader's own.
        assertTrue("EMA is missing from the reading", layer.reads.any { it.id == "ema" })
        assertTrue("RSI is missing from the reading", layer.reads.any { it.id == "rsi" })
        assertTrue("the reader's own script is missing", layer.reads.any { it.id.startsWith("nama:") })
        // And each of them has a base rate measured on this symbol and this bar length.
        assertEquals(layer.reads.size, layer.confidence.size)
        // The header's number is over the studies that voted.
        assertTrue("the setup score counted nobody", layer.setup.studies > 0)
    }

    @Test
    @Config(sdk = [34], qualifiers = EN_PHONE)
    fun theSameChartInEnglishSaysItInEnglish() {
        val controller = charted("XAUUSD")
        proof("omega-signal-layer-en-dark") {
            val ready = remember { controller }
            ChartScreen(controller = ready)
        }
        val sentence = controller.state.value.signals.sentence("ema", english = true)
        assertNotNull("the EMA had nothing to say", sentence)
        assertTrue(
            "an English chart printed a Persian sentence: $sentence",
            sentence!!.none { it in '؀'..'ۿ' },
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun theExplainSheetShowsTheBaseRateAndItsSampleSize() {
        val controller = charted()
        proof("omega-explain-sheet-fa-dark") {
            ExplainSheetBody(
                id = "rsi",
                layer = controller.state.value.signals,
                onSetHorizon = controller::setConfidenceHorizon,
                onAddAlert = {},
                onPractise = {},
                onSelect = {},
            )
        }
        val report = controller.state.value.signals.confidenceOf("rsi")
        assertNotNull("the RSI has no measured history at all", report)
        // The rule the sheet is built around: a percentage never appears without the number of
        // signals behind it, and under the thin line it does not appear at all.
        assertTrue(
            "a percentage was offered on ${report!!.samples} samples",
            report.trustworthy || report.percent == 0,
        )
    }

    private fun proof(name: String, darkTheme: Boolean = true, content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                CoineProTheme(darkTheme = darkTheme) {
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
        OUTPUT.mkdirs()
        File(OUTPUT, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        val OUTPUT = File("build/proof")
        const val FA_PHONE = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"
        const val EN_PHONE = "en-rUS-w411dp-h914dp-xxhdpi"
    }
}
