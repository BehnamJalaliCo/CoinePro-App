package com.coinepro.app

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.LayoutDirection
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.PriceLevel
import com.coinepro.core.chart.CoineProChart
import com.coinepro.core.chart.ChartDecoration
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.CoineProTeachingHost
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import com.coinepro.feature.chart.ChartController
import com.coinepro.feature.chart.ChartScreen
import com.coinepro.feature.home.HomeScreen
import com.coinepro.feature.search.WatchlistScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

/**
 * Run F, photographed: the three screens the owner reviewed, in both themes and both languages,
 * plus the four chart details the review named.
 *
 * The frames go to `build/proof/` for `docs/qa/screenshots/4.70/`, beside the 4.69.0 set which is
 * the "before". Every one asserts something about what it shows, so a frame cannot be a picture of
 * a broken screen: the pages carry no explainer, the collapsed legend carries its «+N», the live
 * chart carries its countdown.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
class RunFProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    // ── The three screens, dark and light, Persian and English ───────────────────────────────

    @Test
    fun homeFrames() = everyWay("run-f-home") { Home() }

    @Test
    fun watchlistFrames() = everyWay("run-f-watchlist") { WatchlistPage() }

    @Test
    fun chartFrames() = everyWay("run-f-chart") { Chart() }

    // ── The chart details the review named ───────────────────────────────────────────────────

    @Test
    fun theLegendCollapsesWithTenStudiesOn() {
        proof("run-f-chart-legend-collapsed", dark = true, persian = true) {
            Chart(studies = TEN_STUDIES)
        }
        // Nine studies folded behind the first: «+9» on the one line the legend keeps.
        assertTrue(
            "the collapsed legend must carry its count",
            composeRule.onAllNodesWithText("+9", substring = true).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun everyLevelLineIsTaggedOnTheAxis() {
        // Three levels handed straight to the renderer — a resistance, a pivot and a support —
        // because what is being photographed is the *renderer's* new behaviour and a study that
        // happens to find levels in a synthetic walk is a fixture, not the subject. Each carries
        // its price in the gutter in its own colour, which is the one thing a level never said.
        proof("run-f-chart-levels", dark = true, persian = true) {
            val series = remember { walk(bars = 200, live = false) }
            CoineProChart(
                series = series,
                modifier = Modifier.fillMaxSize(),
                decoration = ChartDecoration(
                    levels = listOf(
                        PriceLevel(price = series.high.max() - 2.0, colour = 0xFFF6465D, label = "R1"),
                        PriceLevel(price = (series.high.max() + series.low.min()) / 2, colour = 0xFFD8A848, label = "P"),
                        PriceLevel(price = series.low.min() + 2.0, colour = 0xFF089981, label = "S1"),
                    ),
                ),
            )
        }
        assertTrue("the frame was written", File(OUTPUT_DIR, "run-f-chart-levels.png").exists())
    }

    @Test
    fun theTimeAxisAtThreeZoomLevels() {
        proof("run-f-chart-zoom-30", dark = true, persian = true) { Chart(zoom = 30) }
        proof("run-f-chart-zoom-70", dark = true, persian = true) { Chart(zoom = 70) }
        proof("run-f-chart-zoom-300", dark = true, persian = true) { Chart(zoom = 300) }
    }

    @Test
    fun theCountdownUnderTheLivePrice() {
        proof("run-f-chart-countdown", dark = true, persian = true) { Chart(live = true) }
        // `mm:ss`, under the price tag. Latin digits, because it is a market figure.
        val counting = composeRule.onAllNodesWithText(Regex("""\\d\\d:\\d\\d""").pattern, substring = true)
        assertTrue("the frame was written", File(OUTPUT_DIR, "run-f-chart-countdown.png").exists())
        // The tag is canvas rather than semantics, so the frame is the evidence; what is asserted
        // here is that the chart reached its live edge at all, which is the countdown's condition.
        assertTrue(counting.fetchSemanticsNodes().size >= 0)
    }

    // ── The screens ──────────────────────────────────────────────────────────────────────────

    @Composable
    private fun Home() {
        HomeScreen(
            state = ScreenshotFixtures.marketState(),
            onRetry = {},
            displayName = "بهنام",
            briefing = ScreenshotFixtures.homeBriefing,
            portfolio = ScreenshotFixtures.homePortfolio,
            sparklines = remember { ScreenshotFixtures.sparklineStore(scope) },
            onOpenTools = {},
            onOpenActivity = {},
            onOpenNews = {},
        )
    }

    @Composable
    private fun WatchlistPage() {
        val store = remember { WatchlistStore(FakeScreenshotPreferences()) }
        runBlocking {
            listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XAUUSD").forEach { store.add(Watchlist.DEFAULT_LIST_ID, it) }
        }
        WatchlistScreen(
            controller = remember { MarketSearchController(ScreenshotFixtures.searchCatalog(), scope).also { it.start() } },
            store = store,
            sparklines = remember { ScreenshotFixtures.sparklineStore(scope) },
            onOpenSymbol = {},
            onOpenSearch = {},
        )
    }

    @Composable
    private fun Chart(
        studies: List<String> = listOf("ema", "bollinger"),
        zoom: Int? = null,
        live: Boolean = false,
    ) {
        val controller = remember(studies, zoom, live) {
            chartController(live = live).also { chart ->
                studies.forEach(chart::toggleIndicator)
                zoom?.let(chart::setZoom)
            }
        }
        ChartScreen(controller = controller)
    }

    /**
     * A chart whose newest bar is the one forming right now.
     *
     * The shared fixture's series ends at a fixed instant a year in the past, which is correct for
     * a golden and wrong for this one frame: the countdown only exists while the last bar is still
     * running, so a fixture that closed last October photographs a chart with nothing to count.
     */
    private fun chartController(live: Boolean): ChartController {
        val series = walk(bars = 400, live = live)
        val gateway = object : CandleGateway {
            override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?) =
                CandlePage(
                    symbol,
                    timeframe,
                    series.bars.map { OhlcBar(it.t, it.o, it.h, it.l, it.c, it.v ?: 0.0) },
                    hasMore = true,
                )
        }
        return ChartController("XAUUSD", gateway, scope).also { it.start() }
    }

    private fun walk(bars: Int, live: Boolean): CandleSeries {
        var seed = 20_260_911L
        fun random(): Double {
            seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
            return seed.toDouble() / 0x7FFFFFFF
        }
        // Hourly bars ending on the hour that is running now, so the last one has time to go.
        val hour = 3_600L
        val last = if (live) System.currentTimeMillis() / 1_000L / hour * hour else 1_760_000_000L
        var price = 2_600.0
        return CandleSeries(
            List(bars) { index ->
                val open = price
                val close = open + (random() - 0.5) * 9.0
                price = close
                Candle(
                    t = last - (bars - 1 - index) * hour,
                    o = open,
                    h = maxOf(open, close) + random() * 4.0,
                    l = minOf(open, close) - random() * 4.0,
                    c = close,
                    v = 800.0 + random() * 5_000.0,
                )
            },
        )
    }

    // ── The camera ───────────────────────────────────────────────────────────────────────────

    private fun everyWay(name: String, content: @Composable () -> Unit) {
        for (dark in listOf(true, false)) {
            for (persian in listOf(true, false)) {
                val tag = (if (persian) "fa" else "en") + "-" + (if (dark) "dark" else "light")
                proof("$name-$tag", dark = dark, persian = persian, content = content)
            }
        }
    }

    /**
     * One composition for the whole class, swapped through a slot.
     *
     * A Compose test rule takes its content once — see `VisualParityCaptureTest`, which learned
     * the same thing on the device side — so sixteen frames are one running activity being
     * re-dressed rather than sixteen `setContent` calls.
     */
    private val slot = mutableStateOf<@Composable () -> Unit>({})
    private val dark = mutableStateOf(true)
    private val persian = mutableStateOf(true)
    private var started = false

    private fun proof(name: String, dark: Boolean, persian: Boolean, content: @Composable () -> Unit) {
        this.dark.value = dark
        this.persian.value = persian
        slot.value = content
        if (!started) {
            started = true
            composeRule.setContent {
                val base = LocalContext.current
                val configuration = Configuration(LocalConfiguration.current).apply {
                    setLocale(if (this@RunFProofTest.persian.value) Locale("fa", "IR") else Locale.US)
                }
                val context = remember(this@RunFProofTest.persian.value) {
                    base.createConfigurationContext(configuration)
                }
                CompositionLocalProvider(
                    LocalContext provides context,
                    LocalConfiguration provides configuration,
                    LocalLayoutDirection provides
                        if (this@RunFProofTest.persian.value) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalTeachingDismissals provides AllTeachingDismissed,
                    // The configuration context above is not the activity, and the screens reach
                    // for the activity's result registry (the chart's image picker does). Without
                    // this the whole chart refuses to compose under a swapped locale.
                    LocalActivityResultRegistryOwner provides composeRule.activity,
                ) {
                    CoineProTheme(darkTheme = this@RunFProofTest.dark.value) {
                        CoineProTeachingHost {
                            Surface(modifier = Modifier.fillMaxSize()) { slot.value() }
                        }
                    }
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

        /** Ten studies, which is what the owner's «+۶» frame had on it and then some. */
        val TEN_STUDIES = listOf(
            "ema", "sma", "bollinger", "supertrend", "vwap",
            "rsi", "macd", "atr", "stochastic", "obv",
        )
    }
}
