package com.coinepro.app

import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ChartPoint
import com.coinepro.core.chart.DrawingActions
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.ToolGroup
import com.coinepro.core.chart.ToolRail
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSheetBody
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import com.coinepro.feature.chart.ChartController
import com.coinepro.feature.chart.ChartScreen
import com.coinepro.feature.chart.IndicatorArrangement
import com.coinepro.feature.chart.IndicatorSettingsBody
import com.coinepro.feature.chart.IndicatorSettingsTab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * The run E screenshot matrix, taken on whatever device is attached: six scenes × dark/light ×
 * Persian/English, off the real framebuffer. Run it once per device — Pixel 6a, Pixel Tablet,
 * Galaxy Tab S9 Ultra, Pixel Fold open and closed — and pull the frames:
 *
 *     ./gradlew :app:connectedDebugAndroidTest \
 *         -Pandroid.testInstrumentationRunnerArguments.class=com.coinepro.app.DeviceProofTest
 *     adb pull /sdcard/Android/data/com.coinepro.app/files/device-proof docs/qa/screenshots/device/
 *
 * The scenes are the ones `ToolsProofTest` renders under Robolectric, so a frame from the device
 * and a frame from the JVM show the same thing at the same size; each file is named
 * `<scene>-<model>-<fa|en>-<dark|light>.png` and `manifest.json` records the device's own
 * answers about itself. Nothing here asserts a look — a device that renders is a device that
 * reports.
 */
@RunWith(AndroidJUnit4::class)
class DeviceProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val slot = mutableStateOf<@Composable () -> Unit>({})
    private val dark = mutableStateOf(true)
    private val language = mutableStateOf(Locale("fa", "IR"))
    private var started = false

    private val output: File by lazy {
        val base = instrumentation.targetContext.getExternalFilesDir(null) ?: instrumentation.targetContext.filesDir
        File(base, "device-proof").apply { mkdirs() }
    }

    @Test
    fun matrix() {
        writeManifest()
        every("tools-1-pane-legend") { ChartScreen(controller = remember { arranged() }) }
        every("tools-2-selection-toolbar") { ChartScreen(controller = remember { selected() }) }
        every("tools-3-favourites-strip") { ChartScreen(controller = remember { favourites() }) }
        every("tools-3-rail-last-used") {
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
        val macd = ChartCatalog.INDICATORS.first { it.id == "macd" }
        every("tools-4-indicator-inputs") {
            CoineProSheetBody(title = macd.label, subtitle = ChartCatalog.categoryOf(macd.id).label) {
                IndicatorSettingsBody(
                    option = macd, period = null, colour = null, widthDp = null, hidden = false,
                    onSetPeriod = {}, onSetColour = {}, onSetWidth = {}, onToggleHidden = {}, onRemove = {},
                    params = mapOf("fast" to 8.0, "signal" to 7.0),
                )
            }
        }
        val rsi = ChartCatalog.INDICATORS.first { it.id == "rsi" }
        every("tools-4-indicator-pane") {
            CoineProSheetBody(title = rsi.label, subtitle = ChartCatalog.categoryOf(rsi.id).label) {
                IndicatorSettingsBody(
                    option = rsi, period = null, colour = null, widthDp = null, hidden = false,
                    onSetPeriod = {}, onSetColour = {}, onSetWidth = {}, onToggleHidden = {}, onRemove = {},
                    initialTab = IndicatorSettingsTab.VISIBILITY,
                    arrangement = IndicatorArrangement(false, false, false, canMoveUp = true, canMoveDown = true, canMergeUp = true),
                )
            }
        }
    }

    /* ------------------------------------------------------------------ the scenes */

    private fun controller(): ChartController {
        val series = walk(200)
        val gateway = object : CandleGateway {
            override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?) =
                CandlePage(symbol, timeframe, series.bars.map { OhlcBar(it.t, it.o, it.h, it.l, it.c, it.v ?: 0.0) }, hasMore = true)
        }
        return ChartController("XAUUSD", gateway, scope).also { it.start() }
    }

    private fun arranged() = controller().also {
        listOf("ema", "rsi", "macd", "atr", "bollinger").forEach(it::toggleIndicator)
        it.movePane("atr", up = true)
        it.mergePane("rsi", into = "macd")
        it.separateOverlay("bollinger", separate = true)
    }

    private fun selected() = controller().also { chart ->
        val bars = chart.state.value.series.bars
        if (bars.size < 60) return@also
        chart.arm(DrawingTools["trend"])
        var drawing = DrawingActions.tap(chart.state.value.drawing, ChartPoint(bars[bars.size - 60].t, bars[bars.size - 60].l))
        drawing = DrawingActions.tap(drawing, ChartPoint(bars.last().t, bars.last().h))
        chart.onDrawing(drawing)
        chart.arm(null)
        chart.selectDrawing(chart.state.value.drawing.drawings.last().id)
    }

    private fun favourites() = controller().also {
        listOf("trend", "hline", "fib", "rect").forEach(it::toggleToolFavourite)
    }

    private fun walk(bars: Int): CandleSeries {
        var seed = 20_260_826L
        fun random(): Double {
            seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
            return seed.toDouble() / 0x7FFFFFFF
        }
        var price = 2_600.0
        return CandleSeries(
            List(bars) { index ->
                val drift = when {
                    index < bars / 3 -> 0.55
                    index < bars * 2 / 3 -> 0.5
                    else -> 0.44
                }
                val open = price
                val close = open + (random() - drift) * 9.0 + if (index == bars * 3 / 4) -18.0 else 0.0
                price = close
                Candle(1_760_000_000L + index * 3600L, open, maxOf(open, close) + random() * 4.0, minOf(open, close) - random() * 4.0, close, 800.0 + random() * 5_000.0)
            },
        )
    }

    /* ------------------------------------------------------------------ the machinery */

    private fun every(scene: String, content: @Composable () -> Unit) {
        for (isDark in listOf(true, false)) {
            for (tag in listOf("fa-IR", "en-US")) {
                capture("$scene-$MODEL-${tag.take(2)}-${if (isDark) "dark" else "light"}", isDark, Locale.forLanguageTag(tag), content)
            }
        }
    }

    private fun capture(name: String, isDark: Boolean, at: Locale, content: @Composable () -> Unit) {
        dark.value = isDark
        language.value = at
        slot.value = content
        if (!started) {
            started = true
            composeRule.setContent {
                val base = LocalContext.current
                val configuration = Configuration(LocalConfiguration.current).apply { setLocale(language.value) }
                val context = remember(language.value) { base.createConfigurationContext(configuration) }
                CompositionLocalProvider(
                    LocalContext provides context,
                    LocalConfiguration provides configuration,
                    LocalLayoutDirection provides if (language.value.language == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    CoineProTheme(darkTheme = dark.value) {
                        Surface(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.fillMaxSize().background(CoineProColors.Stage)) { slot.value() }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        instrumentation.waitForIdleSync()
        val shot = instrumentation.uiAutomation.takeScreenshot()
        assertTrue("UiAutomation returned no screenshot for '$name'", shot != null)
        File(output, "$name.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun writeManifest() {
        val metrics = instrumentation.targetContext.resources.displayMetrics
        val configuration = instrumentation.targetContext.resources.configuration
        val manifest = JSONObject()
            .put("model", Build.MODEL)
            .put("api_level", Build.VERSION.SDK_INT)
            .put("width_px", metrics.widthPixels)
            .put("height_px", metrics.heightPixels)
            .put("density_dpi", metrics.densityDpi)
            .put("smallest_width_dp", configuration.smallestScreenWidthDp)
            .put("font_scale", configuration.fontScale.toDouble())
        File(output, "manifest.json").writeText(manifest.toString(2))
    }

    private companion object {
        val MODEL: String = Build.MODEL.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "-").trim('-')
    }
}
