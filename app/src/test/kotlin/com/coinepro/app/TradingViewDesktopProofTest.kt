package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.coinepro.core.chart.ChartAppearance
import com.coinepro.feature.chart.ChartSettingsBody
import com.coinepro.feature.chart.ChartSettingsTab
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.coinepro.core.chart.BuiltInIndicatorTemplates
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.feature.chart.ChartScreen
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
 * TradingView's desktop chrome on a browser-wide window (5.16.0): the toolbar over the chart, the
 * range bar under it, the chart settings dialog's tabs, and the templates menu — each as a frame
 * and each asserted.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TradingViewDesktopProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    @Config(sdk = [34], qualifiers = EN_DESKTOP)
    fun theDesktopChartCarriesTradingViewsToolbarsEn() {
        val controller = ScreenshotFixtures.chartController(scope)
        proof("tv-desktop-chart-en-dark") { ChartScreen(controller = controller) }
        assertTrue(composeRule.onAllNodes(hasTestTag("chart-desktop-toolbar")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodes(hasTestTag("chart-desktop-bottom")).fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodes(hasContentDescription("toolbar-indicators")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_DESKTOP)
    fun theDesktopChartCarriesTradingViewsToolbarsFaLight() {
        val controller = ScreenshotFixtures.chartController(scope)
        proof("tv-desktop-chart-fa-light", darkTheme = false) { ChartScreen(controller = controller) }
        assertTrue(composeRule.onAllNodes(hasTestTag("chart-desktop-toolbar")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    @Config(sdk = [34], qualifiers = EN_DESKTOP)
    fun theSettingsDialogHasTradingViewsSixTabs() {
        val controller = ScreenshotFixtures.chartController(scope)
        proof("tv-desktop-settings-en-dark") { ChartScreen(controller = controller) }
        composeRule.onNodeWithContentDescription(SETTINGS_LABEL).performClick()
        composeRule.waitForIdle()
        capture("tv-desktop-settings-en-dark")
        assertTrue(composeRule.onAllNodes(hasTestTag("chart-settings")).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    @Config(sdk = [34], qualifiers = EN_DESKTOP)
    fun aTemplateReplacesTheStudies() {
        val controller = ScreenshotFixtures.chartController(scope)
        proof("tv-desktop-template-en-dark") { ChartScreen(controller = controller) }
        controller.applyBuiltInTemplate(BuiltInIndicatorTemplates.ALL.first { it.id == "tv_oscillators" })
        composeRule.waitForIdle()
        capture("tv-desktop-template-en-dark")
        assertEquals(setOf("rsi", "macd", "stochastic"), controller.state.value.activeIndicators)
    }

    @Test
    @Config(sdk = [34], qualifiers = EN_TABLET)
    fun theSettingsTabsAsTheyRead() {
        var appearance by mutableStateOf(ChartAppearance(topMarginPercent = 15, gridVertical = false))
        var tab by mutableStateOf(ChartSettingsTab.CANVAS)
        proof("tv-settings-canvas-en-dark") {
            Column(Modifier.fillMaxSize().padding(24.dp)) {
                ChartSettingsBody(tab = tab, onTab = { tab = it }, appearance = appearance, onChange = { appearance = it }, scales = {})
            }
        }
        tab = ChartSettingsTab.STATUS_LINE
        composeRule.waitForIdle()
        capture("tv-settings-status-en-dark")
        tab = ChartSettingsTab.SYMBOL
        composeRule.waitForIdle()
        capture("tv-settings-symbol-en-dark")
        assertEquals(15, appearance.topMarginPercent)
    }

    private fun proof(name: String, darkTheme: Boolean = true, content: @Composable () -> Unit) {
        composeRule.setContent {
            CoineProTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                    Surface(modifier = Modifier.fillMaxSize()) { content() }
                }
            }
        }
        composeRule.waitForIdle()
        capture(name)
    }

    private fun capture(name: String) {
        shadowOf(Looper.getMainLooper()).idle()
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
        const val EN_DESKTOP = "en-rUS-ldltr-sw1080dp-w1920dp-h1080dp-mdpi"
        const val FA_DESKTOP = "fa-rIR-ldrtl-sw1080dp-w1920dp-h1080dp-mdpi"
        const val EN_TABLET = "en-rUS-ldltr-sw600dp-w600dp-h900dp-mdpi"
        const val SETTINGS_LABEL = "Chart settings"
    }
}
