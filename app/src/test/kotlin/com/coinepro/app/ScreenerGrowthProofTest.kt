package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.coinepro.core.chart.GrowthScan
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.marketdata.MarketCatalog
import com.coinepro.core.marketdata.MarketCatalogGateway
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.symbols.SymbolClassifier
import com.coinepro.feature.screener.ScreenerController
import com.coinepro.feature.screener.ScreenerMode
import com.coinepro.feature.screener.ScreenerScreen
import java.io.File
import kotlin.math.sin
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

/**
 * The screener's growth scan (5.17.0) as a frame: markets ranked by growth score, each carrying the
 * setups it fired and how many bars ago, on the phone and on a browser-wide window.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenerGrowthProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val universe = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT", "ADAUSDT", "DOGEUSDT")

    private fun barsOf(closes: List<Double>, volume: (Int) -> Double = { 1_000.0 }): List<OhlcBar> =
        closes.mapIndexed { index, close ->
            val open = (closes.getOrElse(index - 1) { close } + close) / 2
            OhlcBar(
                t = 1_700_000_000L + index * 86_400L,
                o = open,
                h = maxOf(open, close) * 1.004,
                l = minOf(open, close) * 0.996,
                c = close,
                v = volume(index),
            )
        }

    private val down = List(150) { 200.0 - it * 0.6 + sin(it / 3.0) }

    /** Six shapes: a trend just starting, a long rise, a breakout on volume, a flat range, a decline, a bounce. */
    private val series: Map<String, List<OhlcBar>> = mapOf(
        "BTCUSDT" to barsOf(down + List(25) { down.last() + it * 1.2 + sin(it / 3.0) * 0.5 }),
        "ETHUSDT" to barsOf(down + List(80) { down.last() + it * 1.2 + sin(it / 3.0) * 0.5 }),
        "SOLUSDT" to barsOf(List(160) { 100.0 + sin(it / 2.0) * 0.8 } + 106.0) { if (it == 160) 3_500.0 else 1_000.0 },
        "XRPUSDT" to barsOf(List(175) { 50.0 + sin(it / 5.0) * 0.4 }),
        "ADAUSDT" to barsOf(List(175) { 300.0 - it * 0.8 + sin(it / 4.0) * 0.3 }),
        "DOGEUSDT" to barsOf(down + List(12) { down.last() + it * 0.9 }),
    )

    private fun controller(): ScreenerController = ScreenerController(
        gateway = object : MarketCatalogGateway {
            override suspend fun load() = MarketCatalog(
                markets = SymbolClassifier.classifyAll(universe),
                quotes = emptyMap(),
                serverTimeEpochMillis = null,
            )
        },
        scope = CoroutineScope(Dispatchers.Unconfined),
        barSource = { symbol -> series.getValue(symbol) },
        computeDispatcher = Dispatchers.Unconfined,
    ).also {
        it.refresh()
        it.setMode(ScreenerMode.SIGNALS)
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h891dp-xhdpi")
    fun theGrowthScanOnThePhone() {
        val controller = controller()
        proof("tv-screener-growth-fa-dark", controller)
        val rows = controller.state.value.rows
        assertTrue("the scan listed the markets it read", rows.isNotEmpty())
        assertEquals("the rise that has run longest ranks first", "ETHUSDT", rows.first().symbol)
    }

    @Test
    @Config(sdk = [34], qualifiers = "en-rUS-ldltr-sw1080dp-w1920dp-h1080dp-mdpi")
    fun theGrowthScanOnTheWeb() {
        val controller = controller()
        controller.setScanIds(setOf(GrowthScan.Kind.TREND_START.id, GrowthScan.Kind.BREAKOUT.id))
        proof("tv-screener-growth-en-desktop", controller)
        val symbols = controller.state.value.rows.map { it.symbol }
        assertTrue("a start and a breakout are on the narrowed list: $symbols", "BTCUSDT" in symbols && "SOLUSDT" in symbols)
    }

    private fun proof(name: String, controller: ScreenerController) {
        composeRule.setContent {
            CoineProTheme(darkTheme = true) {
                CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ScreenerScreen(controller = controller, onOpenSymbol = {})
                    }
                }
            }
        }
        composeRule.waitForIdle()
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
    }
}
