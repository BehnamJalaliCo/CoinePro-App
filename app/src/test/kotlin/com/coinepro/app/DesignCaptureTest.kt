package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.IndicatorPicker
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.CoineProSheetBody
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.orderbook.DepthLevel
import com.coinepro.core.orderbook.OrderBook
import com.coinepro.core.orderbook.OrderBookState
import com.coinepro.feature.chart.ChartDesignPreviews
import com.coinepro.feature.chart.ChartScreen
import com.coinepro.feature.dom.DepthOfMarketBody
import com.coinepro.feature.home.HomeScreen
import com.coinepro.feature.menu.MenuAccess
import com.coinepro.feature.menu.MenuScreen
import com.coinepro.feature.search.WatchlistScreen
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The design review's evidence: ten screens, dark and light, Persian and English, as pixels.
 *
 * Sprint A of the visual audit asks for a BEFORE/AFTER pair of every screen it touches. This is
 * the camera. It renders each screen on the phone profile the audit names (a Pixel 6a: 411×914 dp
 * at 2.625×, `xxhdpi`) through the same native-Skia rig the goldens use, and writes the PNGs to
 * `docs/design/<stage>/`, where they are committed and looked at.
 *
 * Off unless asked for: forty renders is a minute of CI for pictures CI does not read. Run it as
 *
 *     ./gradlew :app:testDebugUnitTest --tests com.coinepro.app.DesignCaptureTest \
 *         -Dcoinepro.design.capture=true -Dcoinepro.design.stage=before
 *
 * and again with `stage=after` once the work is in. The fixtures are the ones the render suite
 * already uses, so the picture is of the screen the app draws, not of a demo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignCaptureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun onlyWhenAsked() {
        assumeTrue(System.getProperty("coinepro.design.capture") == "true")
    }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun homeFaDark() = capture("home-fa-dark", darkTheme = true) { Home() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun homeFaLight() = capture("home-fa-light", darkTheme = false) { Home() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun homeEnDark() = capture("home-en-dark", darkTheme = true) { Home() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun homeEnLight() = capture("home-en-light", darkTheme = false) { Home() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun watchlistFaDark() = capture("watchlist-fa-dark", darkTheme = true) { WatchlistFixture() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun watchlistFaLight() = capture("watchlist-fa-light", darkTheme = false) { WatchlistFixture() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun watchlistEnDark() = capture("watchlist-en-dark", darkTheme = true) { WatchlistFixture() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun watchlistEnLight() = capture("watchlist-en-light", darkTheme = false) { WatchlistFixture() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun chartFaDark() = capture("chart-fa-dark", darkTheme = true) { Chart() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun chartFaLight() = capture("chart-fa-light", darkTheme = false) { Chart() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun chartEnDark() = capture("chart-en-dark", darkTheme = true) { Chart() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun chartEnLight() = capture("chart-en-light", darkTheme = false) { Chart() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun drawingsSheetFaDark() = capture("drawings-sheet-fa-dark", darkTheme = true) { DrawingsSheet() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun drawingsSheetFaLight() = capture("drawings-sheet-fa-light", darkTheme = false) { DrawingsSheet() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun drawingsSheetEnDark() = capture("drawings-sheet-en-dark", darkTheme = true) { DrawingsSheet() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun drawingsSheetEnLight() = capture("drawings-sheet-en-light", darkTheme = false) { DrawingsSheet() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun indicatorsSheetFaDark() = capture("indicators-sheet-fa-dark", darkTheme = true) { IndicatorsSheet() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun indicatorsSheetFaLight() = capture("indicators-sheet-fa-light", darkTheme = false) { IndicatorsSheet() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun indicatorsSheetEnDark() = capture("indicators-sheet-en-dark", darkTheme = true) { IndicatorsSheet() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun indicatorsSheetEnLight() = capture("indicators-sheet-en-light", darkTheme = false) { IndicatorsSheet() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun drawingSettingsFaDark() = capture("drawing-settings-fa-dark", darkTheme = true) { DrawingSettings() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun drawingSettingsFaLight() = capture("drawing-settings-fa-light", darkTheme = false) { DrawingSettings() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun drawingSettingsEnDark() = capture("drawing-settings-en-dark", darkTheme = true) { DrawingSettings() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun drawingSettingsEnLight() = capture("drawing-settings-en-light", darkTheme = false) { DrawingSettings() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun timeframeSheetFaDark() = capture("timeframe-sheet-fa-dark", darkTheme = true) { TimeframeSheet() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun timeframeSheetFaLight() = capture("timeframe-sheet-fa-light", darkTheme = false) { TimeframeSheet() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun timeframeSheetEnDark() = capture("timeframe-sheet-en-dark", darkTheme = true) { TimeframeSheet() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun timeframeSheetEnLight() = capture("timeframe-sheet-en-light", darkTheme = false) { TimeframeSheet() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun analysisHubFaDark() = capture("analysis-hub-fa-dark", darkTheme = true) { AnalysisHub() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun analysisHubFaLight() = capture("analysis-hub-fa-light", darkTheme = false) { AnalysisHub() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun analysisHubEnDark() = capture("analysis-hub-en-dark", darkTheme = true) { AnalysisHub() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun analysisHubEnLight() = capture("analysis-hub-en-light", darkTheme = false) { AnalysisHub() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun domFaDark() = capture("dom-fa-dark", darkTheme = true) { Dom() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun domFaLight() = capture("dom-fa-light", darkTheme = false) { Dom() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun domEnDark() = capture("dom-en-dark", darkTheme = true) { Dom() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun domEnLight() = capture("dom-en-light", darkTheme = false) { Dom() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun menuFaDark() = capture("menu-fa-dark", darkTheme = true) { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = FA)
    fun menuFaLight() = capture("menu-fa-light", darkTheme = false) { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun menuEnDark() = capture("menu-en-dark", darkTheme = true) { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = EN)
    fun menuEnLight() = capture("menu-en-light", darkTheme = false) { Menu() }

    /* ------------------------------------------------------------------ fixtures */

    @Composable
    private fun Home() {
        HomeScreen(
            state = ScreenshotFixtures.marketState(),
            onRetry = {},
            displayName = "بهنام",
            briefing = ScreenshotFixtures.homeBriefing,
            portfolio = ScreenshotFixtures.homePortfolio,
            platforms = MarketPlatform.entries,
            activePlatform = MarketPlatform.TRADEYAR,
            onToggleBalanceHidden = {},
            onOpenPortfolio = {},
            onOpenTools = {},
            onOpenActivity = {},
            onOpenNews = {},
            watchlist = listOf("ETHUSDT", "SOLUSDT"),
        )
    }

    @Composable
    private fun WatchlistFixture() {
        val store = WatchlistStore(FakeScreenshotPreferences())
        runBlocking {
            listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XAUUSD").forEach { store.add(Watchlist.DEFAULT_LIST_ID, it) }
        }
        WatchlistScreen(
            controller = MarketSearchController(ScreenshotFixtures.searchCatalog(), scope).also { it.start() },
            store = store,
            sparklines = ScreenshotFixtures.sparklineStore(scope),
            onOpenSymbol = {},
            onOpenSearch = {},
        )
    }

    @Composable
    private fun Chart() {
        val controller = ScreenshotFixtures.chartController(scope)
        listOf("ema", "bollinger").forEach(controller::toggleIndicator)
        ChartScreen(
            controller = controller,
            wheelSymbols = listOf("XAUUSD", "BTCUSDT", "ETHUSDT", "XAGUSD"),
            onSelectSymbol = {},
            onOpenStudio = {},
            onOpenTerminal = {},
        )
    }

    @Composable
    private fun DrawingsSheet() {
        val controller = ScreenshotFixtures.chartController(scope)
        controller.arm(DrawingTools.ALL.first { it.id == "trend" })
        CoineProSheetBody(title = "ابزار رسم", subtitle = DrawingTools.ALL.size.toPersianDigits() + " ابزار") {
            ChartDesignPreviews.DrawingsSheet(controller = controller)
        }
    }

    @Composable
    private fun IndicatorsSheet() {
        CoineProSheetBody(
            title = "اندیکاتورها",
            subtitle = ChartCatalog.INDICATORS.size.toPersianDigits() + " اندیکاتور",
        ) {
            IndicatorPicker(
                active = setOf("ema", "bollinger", "rsi"),
                onToggle = {},
                onHelp = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    @Composable
    private fun DrawingSettings() {
        CoineProSheetBody(title = "خط روند", subtitle = "تنظیمات ترسیم") {
            ChartDesignPreviews.DrawingSettingsSheet()
        }
    }

    @Composable
    private fun TimeframeSheet() {
        CoineProSheetBody(title = "تایم‌فریم", subtitle = null) {
            ChartDesignPreviews.TimeframeSheet()
        }
    }

    @Composable
    private fun AnalysisHub() {
        val controller = ScreenshotFixtures.chartController(scope)
        CoineProSheetBody(title = "تحلیل", subtitle = null) {
            ChartDesignPreviews.AnalysisHub(controller = controller)
        }
    }

    @Composable
    private fun Dom() {
        val book = OrderBook.of(
            symbol = "BTCUSDT",
            bids = listOf(68_420.0 to 3.4, 68_410.0 to 11.2, 68_400.0 to 6.1, 68_390.0 to 1.8, 68_380.0 to 9.6)
                .map { (p, q) -> DepthLevel(p, q) },
            asks = listOf(68_450.0 to 2.2, 68_460.0 to 8.7, 68_470.0 to 4.0, 68_480.0 to 12.9, 68_490.0 to 5.3)
                .map { (p, q) -> DepthLevel(p, q) },
            at = 1_772_000_000_000L,
        )
        DepthOfMarketBody(
            state = OrderBookState(symbol = "BTCUSDT", book = book, sourceName = "LBank Futures"),
            onPickPrice = {},
            onRetry = {},
        )
    }

    @Composable
    private fun Menu() {
        MenuScreen(
            access = MenuAccess(platform = MarketPlatform.TRADEYAR, signedIn = true),
            onOpen = {},
            name = "بهنام",
            email = "trader@example.com",
            planLabel = "حرفه‌ای",
            platformLabel = "کریپتو",
            watchlistCount = 12,
        )
    }

    /* ------------------------------------------------------------------ camera */

    private fun capture(name: String, darkTheme: Boolean, content: @Composable () -> Unit) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        composeRule.setContent {
            CoineProTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) { content() }
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
        val stage = System.getProperty("coinepro.design.stage") ?: "before"
        val dir = File("../docs/design/$stage").also { it.mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        /** A Pixel 6a, in the app's default language and direction. */
        const val FA = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"
        const val EN = "en-rUS-ldltr-w411dp-h914dp-xxhdpi"
    }
}
