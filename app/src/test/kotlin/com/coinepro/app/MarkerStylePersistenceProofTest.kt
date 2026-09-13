package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartDecoration
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.chart.MarkerStyle
import com.coinepro.core.datastore.SymbolChartStateStore
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import com.coinepro.feature.chart.ChartController
import com.coinepro.feature.chart.ChartSignalEngine
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **What a study draws on the candles, after the app has been closed and opened** (4.86.1).
 *
 * 4.86.1 shipped without a frame, and the checklist said so in those words: a fact about *two runs*
 * of the app is not something a still can carry, so the evidence was four tests. The owner asked
 * whether there was a picture. There is one worth taking, and this is it — but only if the picture
 * is taken from the right place.
 *
 * A frame of a chart with triangles on it proves nothing: the renderer will draw whatever map it is
 * handed, and handing it `mapOf("ema" to TRIANGLES)` would be a photograph of the test's own
 * argument. So the map here is not written by this file. It is:
 *
 *  1. set on a controller, through `setMarkerStyle`, the way a reader sets it in the Explain sheet;
 *  2. written to a real [SymbolChartStateStore] over a preferences file;
 *  3. read back by a **second controller** that has never seen the first — which is what a cold
 *     start is;
 *  4. and only then drawn.
 *
 * If the restore breaks, this frame comes back with «خرید» and «فروش» on it, because that is what
 * an empty map means. That is the failure the picture is for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MarkerStylePersistenceProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val series: CandleSeries = ScreenshotFixtures.chartSeries()

    /** A preferences file in memory, exactly as `ChartSymbolStateTest` uses one. */
    private class FakePreferences : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    /**
     * Enough of a feed for the controller to reach its loaded state.
     *
     * The fixture's own candles, carried across into the feed's shape — the same numbers the frame
     * is drawn from, so nothing about the picture depends on which of the two the controller read.
     */
    private class FixtureGateway(private val series: CandleSeries) : CandleGateway {
        override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?): CandlePage =
            CandlePage(
                symbol,
                timeframe,
                series.bars.map { OhlcBar(t = it.t, o = it.o, h = it.h, l = it.l, c = it.c, v = it.v ?: 0.0) },
            )
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `the style a reader chose is what a second run draws`() {
        val store = SymbolChartStateStore(FakePreferences())
        val scopes = mutableListOf<CoroutineScope>()
        var restored: Map<String, MarkerStyle> = emptyMap()
        var firstRun: Map<String, MarkerStyle> = emptyMap()

        runTest {
            fun controller(): ChartController {
                val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
                scopes += scope
                return ChartController(
                    symbol = SYMBOL,
                    gateway = FixtureGateway(series),
                    scope = scope,
                    symbolStates = store,
                )
            }

            // The first run: a reader who has learned which way a triangle points turns the words
            // off for one study, in the Explain sheet, through this exact call.
            val first = controller()
            first.start()
            advanceUntilIdle()
            first.setMarkerStyle("ema", MarkerStyle.TRIANGLES)
            advanceUntilIdle()
            firstRun = first.state.value.markerStyles

            // The second run. A different controller, a different scope, the same phone.
            val second = controller()
            second.start()
            advanceUntilIdle()
            restored = second.state.value.markerStyles

            scopes.forEach { it.cancel() }
        }

        assertEquals("the setting never took in the first place", mapOf("ema" to MarkerStyle.TRIANGLES), firstRun)
        assertEquals("the setting did not survive the cold start", firstRun, restored)

        // And the frame is drawn from `restored` — the map that came back out of the store.
        proof("sigma-marker-style-restored-phone-fa") {
            val layer = ChartSignalEngine.evaluate(series = series, indicatorIds = listOf("ema", "rsi"))
            CoineProChart(
                series = series,
                modifier = Modifier.fillMaxSize(),
                savedBarsPerView = BARS,
                decoration = ChartDecoration(
                    markers = ChartSignalEngine.markersFor(layer, series, styles = restored),
                ),
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun `a phone that has never been configured draws the words`() {
        // The other half of the pair, and the half that makes the first one mean something: an
        // untouched store restores an empty map, and an empty map is «every study labelled». Put
        // the two frames side by side and the difference is the setting surviving a cold start.
        val store = SymbolChartStateStore(FakePreferences())
        val scopes = mutableListOf<CoroutineScope>()
        var fresh: Map<String, MarkerStyle> = emptyMap()

        runTest {
            val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
            scopes += scope
            val only = ChartController(
                symbol = SYMBOL,
                gateway = FixtureGateway(series),
                scope = scope,
                symbolStates = store,
            )
            only.start()
            advanceUntilIdle()
            fresh = only.state.value.markerStyles
            scopes.forEach { it.cancel() }
        }

        assertEquals("an untouched phone came back configured", emptyMap<String, MarkerStyle>(), fresh)

        proof("sigma-marker-style-default-phone-fa") {
            val layer = ChartSignalEngine.evaluate(series = series, indicatorIds = listOf("ema", "rsi"))
            CoineProChart(
                series = series,
                modifier = Modifier.fillMaxSize(),
                savedBarsPerView = BARS,
                decoration = ChartDecoration(
                    markers = ChartSignalEngine.markersFor(layer, series, styles = fresh),
                ),
            )
        }
    }

    private fun proof(name: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            CoineProTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize()) { content() }
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
        File(OUTPUT, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        val OUTPUT = File("build/proof")
        const val FA_PHONE = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"
        const val SYMBOL = "BTCUSDT"

        /** Sixteen points a bar — the zoom at which a labelled study *would* show its words. */
        const val BARS = 21
    }
}
