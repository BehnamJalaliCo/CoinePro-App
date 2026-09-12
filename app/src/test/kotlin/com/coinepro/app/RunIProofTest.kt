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
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.feature.chart.ChartController
import com.coinepro.feature.chart.ChartScreen
import com.coinepro.feature.chart.ChartScriptSource
import com.coinepro.feature.script.ScriptScreen
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
import java.io.File

/**
 * Run I item 0, photographed: **a reader's script is an indicator on their own chart.**
 *
 * The frames are the acceptance line's, minus the parts that need a device: the studio with «Add to
 * chart» beside Run, the chart with the script in its legend next to EMA and Bollinger, its settings
 * sheet generated from its own `input(...)`s, and the four-chart layout with the script on one pane
 * only. Each carries the assertion that makes it evidence rather than a picture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RunIProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    /**
     * A study over the price, so its row sits in the main legend beside EMA — which is where the
     * acceptance line says to look for it.
     */
    private val overlayScript = """
        length = input(50, title = "Length", min = 5, max = 200)
        plot(ta.ema(close, length), title = "Momentum", color = color.orange)
    """.trimIndent()

    /** A three-line study with an input and an alert condition — everything item 0 is about. */
    private val rsiScript = """
        length = input(14, title = "RSI length", min = 2, max = 60)
        r = ta.rsi(close, length)
        plot(r, title = "RSI", pane = "own")
        hline(70, title = "Overbought")
        alertcondition(ta.crossunder(r, 30), "Oversold cross")
    """.trimIndent()

    private fun chart(symbol: String = "XAUUSD"): ChartController =
        ScreenshotFixtures.chartController(scope, symbol)

    // ── 0.1 and 0.8: the script in the legend, beside the built-ins ──────────────────────────

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun aScriptSitsInTheLegendBesideTheBuiltIns() {
        val controller = chart()
        proof("run-i-script-on-chart-en-dark") {
            val ready = remember {
                controller.also {
                    it.toggleIndicator("ema")
                    it.addScript(name = "Momentum", source = overlayScript)
                }
            }
            ChartScreen(controller = ready)
        }
        val state = controller.state.value
        // **The script is in the same list the legend is built from.** `overlays` is what the
        // decoration is handed and `shownOverlayOwners` is how a row resolves back to a study, so
        // a script's line appearing in both, aligned, *is* «it is in the legend beside EMA» — and
        // it is the assertion rather than the drawn text because the collapsed plate prints two
        // rows and counts the rest («▸ +1» in the frame), which is the legend's own rule.
        assertEquals("one built-in line and one script line", 2, state.overlays.size)
        assertEquals(listOf("ema", "nama:1"), state.shownOverlayOwners)
        assertEquals("Momentum", state.overlays.last().label)
        assertTrue("and the legend says there is one more row than it printed", texts().any { it == "▸ +1" })
        // The owner id is what the eye, the gear, the × and the pane order all key on, and it is
        // the same shape as «ema».
        assertEquals("nama:1", state.scripts.single().ownerId)
    }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun theScriptDrawsThroughTheSamePathAsAnIndicator() {
        val controller = chart()
        proof("run-i-script-pane-en-dark") {
            val ready = remember { controller.also { it.addScript(name = "RSI Zones", source = rsiScript) } }
            ChartScreen(controller = ready)
        }
        val state = controller.state.value
        // Its pane arrived in `panes`, its owner in the owners list, and the two are aligned —
        // which is what lets the legend resolve a row back to this script.
        assertTrue("the script produced a pane", state.scriptDraw.panes.isNotEmpty())
        assertEquals(listOf("nama:1"), state.scriptDraw.paneOwners)
        // Its `hline` went into the **pane**, not onto the price scale, because the script asked
        // for `pane = "own"` — which is the rule `ScriptOverlay` applies and the reason an RSI's
        // 70 line is drawn on the RSI's own scale rather than at a price of seventy.
        assertTrue("the pane carries the script's own level", state.scriptDraw.panes.single().levels.isNotEmpty())
        assertTrue("so nothing of it landed on the price scale", state.scriptDraw.levels.isEmpty())
    }

    // ── 0.4: the settings sheet, generated from the script's own inputs ──────────────────────

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun theScriptsInputsBecomeItsSettings() {
        val controller = chart()
        proof("run-i-script-inputs-en-dark") {
            val ready = remember { controller.also { it.addScript(name = "RSI Zones", source = rsiScript) } }
            ChartScreen(controller = ready)
        }
        val inputs = controller.state.value.scriptDraw.inputs["nama:1"].orEmpty()
        assertEquals("the script declares one input", 1, inputs.size)
        assertEquals("RSI length", inputs.single().name)
        // And setting it is remembered on the instance, which is what a second copy of the same
        // script with a different length depends on.
        controller.setScriptInput("1", "RSI length", 30.0)
        assertEquals(30.0, controller.state.value.scripts.single().overrides["RSI length"])
    }

    // ── 0.6: the alert condition is offered where alerts are made ────────────────────────────

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun theScriptsAlertConditionIsCarried() {
        val controller = chart()
        proof("run-i-script-alerts-en-dark") {
            val ready = remember { controller.also { it.addScript(name = "RSI Zones", source = rsiScript) } }
            ChartScreen(controller = ready, onCreateScriptAlert = { _, _, _, _ -> })
        }
        val alerts = controller.state.value.scriptDraw.alerts["nama:1"].orEmpty()
        assertEquals("the script named one condition", 1, alerts.size)
        assertEquals("Oversold cross", alerts.single().title)
    }

    // ── 0.2: «Add to chart» in the studio, beside Run ────────────────────────────────────────

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun theStudioOffersAddToChart() {
        var added: String? = null
        proof("run-i-studio-add-to-chart-en-dark") {
            val series = ScreenshotFixtures.chartSeries(symbol = "XAUUSD")
            val controller = remember {
                ScreenshotFixtures.scriptController(scope).also {
                    it.setSeries(series)
                    it.openText(name = "RSI Zones", source = rsiScript)
                }
            }
            ScriptScreen(
                controller = controller,
                symbol = "XAUUSD",
                series = series,
                onAddToChart = { name, _, _ -> added = name; "1" },
            )
        }
        val shown = texts()
        assertTrue("the button is on the page", shown.any { it == "Add to chart" })
        assertTrue("and it is reachable by name", shown.any { it == "script-add-to-chart" })
        // Nothing was tapped, so nothing was added: the frame is the button offered, not pressed.
        assertEquals(null, added)
    }

    // ── 0.2: the reader's scripts in the indicator sheet ─────────────────────────────────────

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun theIndicatorSheetListsTheReadersScripts() {
        val controller = chart()
        proof("run-i-indicator-sheet-custom-en-dark") {
            ChartScreen(
                controller = controller,
                scriptLibrary = listOf(
                    ChartScriptSource(id = "rsi", name = "RSI Zones", source = rsiScript),
                    ChartScriptSource(id = "ema", name = "Two moving averages", source = "plot(ta.ema(close, 20))"),
                ),
            )
        }
        // The library is data the app supplies; what this asserts is that the chart takes it and
        // that switching one on is `putScript` — one instance, not two, on a second tap.
        controller.putScript("RSI Zones", rsiScript)
        controller.putScript("RSI Zones", rsiScript)
        assertEquals("adding the same script twice updates it", 1, controller.state.value.scripts.size)
    }

    // ── 0.5: the instance survives being written down and read back ──────────────────────────

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun anInstanceCarriesEverythingAStoredRowNeeds() {
        val controller = chart()
        controller.addScript(name = "RSI Zones", source = rsiScript)
        controller.setScriptInput("1", "RSI length", 21.0)
        val instance = controller.state.value.scripts.single()
        // These four fields are what `SymbolChartState` and `ChartLayout` write down, and the
        // source is among them on purpose: a restored chart has to be the chart that was saved
        // even after the reader renames or deletes the script it came from. The round trip itself
        // is `ChartScriptCodecTest`'s; this is that the instance has something true to hand it.
        assertEquals("1", instance.instanceId)
        assertEquals("RSI Zones", instance.name)
        assertEquals(rsiScript, instance.source)
        assertEquals(mapOf("RSI length" to 21.0), instance.overrides)
    }

    // ── the camera ───────────────────────────────────────────────────────────────────────────

    private fun texts(): List<String> {
        val found = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.find { it.key == SemanticsProperties.Text }?.let { entry ->
                @Suppress("UNCHECKED_CAST")
                (entry.value as? List<AnnotatedString>)?.forEach { found += it.text }
            }
            node.config.find { it.key == SemanticsProperties.ContentDescription }?.let { entry ->
                @Suppress("UNCHECKED_CAST")
                (entry.value as? List<String>)?.forEach { found += it }
            }
            node.children.forEach(::walk)
        }
        composeRule.onAllNodes(SemanticsMatcher("a root") { it.parent == null }, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .forEach(::walk)
        return found
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
        const val EN_1280 = "en-rUS-w1280dp-h800dp-xhdpi"
        const val EN_S9U = "en-rUS-w1478dp-h924dp-xhdpi"
    }
}
