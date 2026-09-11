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
import androidx.compose.ui.test.onAllNodesWithText
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ChartPoint
import com.coinepro.core.chart.DrawingActions
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.ToolGroup
import com.coinepro.core.chart.ToolRail
import com.coinepro.core.designsystem.CoineProSheetBody
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.feature.chart.ChartController
import com.coinepro.feature.chart.ChartScreen
import com.coinepro.feature.chart.IndicatorArrangement
import com.coinepro.feature.chart.IndicatorSettingsBody
import com.coinepro.feature.chart.IndicatorSettingsTab
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
 * Run E, photographed on a phone and on a tablet: the pane legend over the reader's own
 * arrangement, the floating toolbar over a selected drawing, the rail with the last-used tool
 * leading its group and the favourites strip on the plot's edge, and an indicator's every knob
 * with the pane controls. Frames go to `build/proof/` for `docs/qa/screenshots/4.67/`, and each
 * asserts the thing it shows is in the tree, so a broken control cannot pose for its picture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ToolsProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    // ── 1. The pane legend over a moved, merged and separated arrangement ────────────────────

    private fun arranged(): ChartController =
        ScreenshotFixtures.chartController(scope).also {
            listOf("ema", "rsi", "macd", "atr", "bollinger").forEach(it::toggleIndicator)
            it.movePane("atr", up = true)
            it.mergePane("rsi", into = "macd")
            it.separateOverlay("bollinger", separate = true)
        }

    @Test
    @Config(sdk = [34], qualifiers = PHONE_FA)
    fun paneLegendPhone() = paneLegend("tools-1-pane-legend-phone-fa")

    @Test
    @Config(sdk = [34], qualifiers = TABLET_FA)
    fun paneLegendTablet() = paneLegend("tools-1-pane-legend-tablet-fa")

    private fun paneLegend(name: String) {
        lateinit var controller: ChartController
        proof(name) {
            controller = remember { arranged() }
            ChartScreen(controller = controller)
        }
        val state = controller.state.value
        assertEquals(listOf("atr", "macd", "bollinger"), state.paneOwnersShown)
        assertTrue(state.panes[1].title, state.panes[1].title.contains(" · RSI"))
        // The legend names the joint pane by both studies; its eye, gear and × open on a tap.
        assertTrue(composeRule.onAllNodesWithText("· RSI 14", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    // ── 2. The floating toolbar above the selected drawing ────────────────────────────────────

    @Test
    @Config(sdk = [34], qualifiers = PHONE_FA)
    fun selectionToolbarPhone() = selectionToolbar("tools-2-selection-toolbar-phone-fa")

    @Test
    @Config(sdk = [34], qualifiers = TABLET_FA)
    fun selectionToolbarTablet() = selectionToolbar("tools-2-selection-toolbar-tablet-fa")

    private fun selectionToolbar(name: String) {
        proof(name) {
            val controller = remember {
                ScreenshotFixtures.chartController(scope).also { chart ->
                    val bars = chart.state.value.series.bars
                    chart.arm(DrawingTools["trend"])
                    var drawing = DrawingActions.tap(chart.state.value.drawing, ChartPoint(bars[bars.size - 60].t, bars[bars.size - 60].l))
                    drawing = DrawingActions.tap(drawing, ChartPoint(bars.last().t, bars.last().h))
                    chart.onDrawing(drawing)
                    chart.arm(null)
                    chart.selectDrawing(chart.state.value.drawing.drawings.last().id)
                }
            }
            ChartScreen(controller = controller)
        }
        assertTrue(composeRule.onAllNodesWithContentDescription("تکثیر").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithContentDescription("حذف").fetchSemanticsNodes().isNotEmpty())
    }

    // ── 3. The rail with the last-used tool first, and the favourites strip on the plot ───────

    @Test
    @Config(sdk = [34], qualifiers = PHONE_FA)
    fun railLastUsedPhone() = railLastUsed("tools-3-rail-last-used-phone-fa")

    @Test
    @Config(sdk = [34], qualifiers = TABLET_FA)
    fun railLastUsedTablet() = railLastUsed("tools-3-rail-last-used-tablet-fa")

    private fun railLastUsed(name: String) {
        proof(name) {
            CoineProSheetBody(title = "ابزارها") {
                ToolRail(
                    selected = null,
                    onSelect = {},
                    hasVolume = true,
                    favourites = setOf("hline", "fib"),
                    lastUsed = mapOf(ToolGroup.LINES to "ray", ToolGroup.FIBONACCI to "fib"),
                    onToggleFavourite = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        assertTrue(composeRule.onAllNodesWithContentDescription("tool-last-used-ray").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE_FA)
    fun favouritesStripPhone() = favouritesStrip("tools-3-favourites-strip-phone-fa")

    @Test
    @Config(sdk = [34], qualifiers = TABLET_FA)
    fun favouritesStripTablet() = favouritesStrip("tools-3-favourites-strip-tablet-fa")

    private fun favouritesStrip(name: String) {
        proof(name) {
            val controller = remember {
                ScreenshotFixtures.chartController(scope).also {
                    listOf("trend", "hline", "fib", "rect").forEach(it::toggleToolFavourite)
                }
            }
            ChartScreen(controller = controller)
        }
        assertTrue(composeRule.onAllNodesWithContentDescription("favourite-tool-strip").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithContentDescription("favourite-tool-hline").fetchSemanticsNodes().isNotEmpty())
    }

    // ── 4. Every knob of a study, and the pane controls ───────────────────────────────────────

    @Test
    @Config(sdk = [34], qualifiers = PHONE_FA)
    fun indicatorInputsPhone() = indicatorInputs("tools-4-indicator-inputs-phone-fa")

    @Test
    @Config(sdk = [34], qualifiers = TABLET_FA)
    fun indicatorInputsTablet() = indicatorInputs("tools-4-indicator-inputs-tablet-fa")

    private fun indicatorInputs(name: String) {
        val macd = ChartCatalog.INDICATORS.first { it.id == "macd" }
        proof(name) {
            CoineProSheetBody(title = macd.label, subtitle = ChartCatalog.categoryOf(macd.id).label) {
                IndicatorSettingsBody(
                    option = macd,
                    period = null,
                    colour = null,
                    widthDp = null,
                    hidden = false,
                    onSetPeriod = {},
                    onSetColour = {},
                    onSetWidth = {},
                    onToggleHidden = {},
                    onRemove = {},
                    params = mapOf("fast" to 8.0, "signal" to 7.0),
                    onSetParam = { _, _ -> },
                )
            }
        }
        listOf("fast", "slow", "signal").forEach { key ->
            assertTrue(key, composeRule.onAllNodesWithContentDescription("indicator-param-$key").fetchSemanticsNodes().isNotEmpty())
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE_FA)
    fun indicatorPanePhone() = indicatorPane("tools-4-indicator-pane-phone-fa")

    @Test
    @Config(sdk = [34], qualifiers = TABLET_FA)
    fun indicatorPaneTablet() = indicatorPane("tools-4-indicator-pane-tablet-fa")

    private fun indicatorPane(name: String) {
        val rsi = ChartCatalog.INDICATORS.first { it.id == "rsi" }
        proof(name) {
            CoineProSheetBody(title = rsi.label, subtitle = ChartCatalog.categoryOf(rsi.id).label) {
                IndicatorSettingsBody(
                    option = rsi,
                    period = null,
                    colour = null,
                    widthDp = null,
                    hidden = false,
                    onSetPeriod = {},
                    onSetColour = {},
                    onSetWidth = {},
                    onToggleHidden = {},
                    onRemove = {},
                    initialTab = IndicatorSettingsTab.VISIBILITY,
                    arrangement = IndicatorArrangement(
                        overlayByDefault = false,
                        separated = false,
                        merged = false,
                        canMoveUp = true,
                        canMoveDown = true,
                        canMergeUp = true,
                    ),
                )
            }
        }
        listOf("indicator-move-up", "indicator-move-down", "indicator-merge-up").forEach { tag ->
            assertTrue(tag, composeRule.onAllNodesWithContentDescription(tag).fetchSemanticsNodes().isNotEmpty())
        }
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
        const val TABLET_FA = "fa-rIR-ldrtl-sw800dp-w1280dp-h800dp-xhdpi"
    }
}
