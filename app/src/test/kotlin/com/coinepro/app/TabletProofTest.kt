package com.coinepro.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.coinepro.core.chart.ChartPoint
import com.coinepro.core.chart.Drawing
import com.coinepro.core.datastore.AlertAuditStore
import com.coinepro.core.datastore.LocalAlertStore
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.CoineProListDetail
import com.coinepro.core.designsystem.CoineProTheme
import androidx.compose.runtime.CompositionLocalProvider
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.orderbook.DepthLevel
import com.coinepro.core.orderbook.OrderBook
import com.coinepro.core.orderbook.OrderBookState
import com.coinepro.feature.alerts.AlertCenterScreen
import com.coinepro.feature.alerts.AlertsController
import com.coinepro.feature.chart.ChartController
import com.coinepro.feature.chart.ChartLayoutPreset
import com.coinepro.feature.chart.ChartPanesScreen
import com.coinepro.feature.chart.ChartScreen
import com.coinepro.feature.chart.ChartSidePanel
import com.coinepro.feature.chart.ChartWorkspaceStore
import com.coinepro.feature.dom.DepthOfMarketBody
import com.coinepro.feature.script.ScriptScreen
import com.coinepro.feature.search.WatchlistScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The tablet proofs the owner asked to see — item FIX of the 4.58 run.
 *
 * Seven scenes on two devices, dark and light, Persian and English: the watchlist ⇄ chart
 * list-detail, the chart with each of its five docked panels open, and the four-chart layout.
 * Not goldens: nothing is compared, the frames are written to `build/proof/` for
 * `docs/qa/screenshots/` and the report. Every fixture is the one the goldens and the design
 * captures already use, so what these show is what the tests assert elsewhere.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Composable
    private fun ListDetail() {
        val store = WatchlistStore(FakeScreenshotPreferences())
        runBlocking { listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XAUUSD").forEach { store.add(Watchlist.DEFAULT_LIST_ID, it) } }
        CoineProListDetail(detail = { ChartScreen(controller = ScreenshotFixtures.chartController(scope)) }) {
            WatchlistScreen(
                controller = MarketSearchController(ScreenshotFixtures.searchCatalog(), scope).also { it.start() },
                store = store,
                sparklines = ScreenshotFixtures.sparklineStore(scope),
                onOpenSymbol = {},
                onOpenSearch = {},
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun listDetailPixelTabletFaDark() = proof("list-detail-pixel-tablet-fa-dark") { ListDetail() }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun listDetailPixelTabletFaLight() = proof("list-detail-pixel-tablet-fa-light", darkTheme = false) { ListDetail() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun listDetailPixelTabletEnDark() = proof("list-detail-pixel-tablet-en-dark") { ListDetail() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun listDetailPixelTabletEnLight() = proof("list-detail-pixel-tablet-en-light", darkTheme = false) { ListDetail() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun listDetailTabS9UltraFaDark() = proof("list-detail-tab-s9-ultra-fa-dark") { ListDetail() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun listDetailTabS9UltraFaLight() = proof("list-detail-tab-s9-ultra-fa-light", darkTheme = false) { ListDetail() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun listDetailTabS9UltraEnDark() = proof("list-detail-tab-s9-ultra-en-dark") { ListDetail() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun listDetailTabS9UltraEnLight() = proof("list-detail-tab-s9-ultra-en-light", darkTheme = false) { ListDetail() }

    @Composable
    private fun PanelObjects() {
        ChartScreen(controller = drawnController(), initialSidePanel = "objects")
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun panelObjectsPixelTabletFaDark() = proof("panel-objects-pixel-tablet-fa-dark") { PanelObjects() }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun panelObjectsPixelTabletFaLight() = proof("panel-objects-pixel-tablet-fa-light", darkTheme = false) { PanelObjects() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun panelObjectsPixelTabletEnDark() = proof("panel-objects-pixel-tablet-en-dark") { PanelObjects() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun panelObjectsPixelTabletEnLight() = proof("panel-objects-pixel-tablet-en-light", darkTheme = false) { PanelObjects() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun panelObjectsTabS9UltraFaDark() = proof("panel-objects-tab-s9-ultra-fa-dark") { PanelObjects() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun panelObjectsTabS9UltraFaLight() = proof("panel-objects-tab-s9-ultra-fa-light", darkTheme = false) { PanelObjects() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun panelObjectsTabS9UltraEnDark() = proof("panel-objects-tab-s9-ultra-en-dark") { PanelObjects() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun panelObjectsTabS9UltraEnLight() = proof("panel-objects-tab-s9-ultra-en-light", darkTheme = false) { PanelObjects() }

    @Composable
    private fun PanelWatchlist() {
        ChartScreen(controller = ScreenshotFixtures.chartController(scope), sidePanels = shellPanels(), initialSidePanel = "watchlist")
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun panelWatchlistPixelTabletFaDark() = proof("panel-watchlist-pixel-tablet-fa-dark") { PanelWatchlist() }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun panelWatchlistPixelTabletFaLight() = proof("panel-watchlist-pixel-tablet-fa-light", darkTheme = false) { PanelWatchlist() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun panelWatchlistPixelTabletEnDark() = proof("panel-watchlist-pixel-tablet-en-dark") { PanelWatchlist() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun panelWatchlistPixelTabletEnLight() = proof("panel-watchlist-pixel-tablet-en-light", darkTheme = false) { PanelWatchlist() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun panelWatchlistTabS9UltraFaDark() = proof("panel-watchlist-tab-s9-ultra-fa-dark") { PanelWatchlist() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun panelWatchlistTabS9UltraFaLight() = proof("panel-watchlist-tab-s9-ultra-fa-light", darkTheme = false) { PanelWatchlist() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun panelWatchlistTabS9UltraEnDark() = proof("panel-watchlist-tab-s9-ultra-en-dark") { PanelWatchlist() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun panelWatchlistTabS9UltraEnLight() = proof("panel-watchlist-tab-s9-ultra-en-light", darkTheme = false) { PanelWatchlist() }

    @Composable
    private fun PanelDepth() {
        ChartScreen(controller = ScreenshotFixtures.chartController(scope), sidePanels = shellPanels(), initialSidePanel = "depth")
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun panelDepthPixelTabletFaDark() = proof("panel-depth-pixel-tablet-fa-dark") { PanelDepth() }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun panelDepthPixelTabletFaLight() = proof("panel-depth-pixel-tablet-fa-light", darkTheme = false) { PanelDepth() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun panelDepthPixelTabletEnDark() = proof("panel-depth-pixel-tablet-en-dark") { PanelDepth() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun panelDepthPixelTabletEnLight() = proof("panel-depth-pixel-tablet-en-light", darkTheme = false) { PanelDepth() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun panelDepthTabS9UltraFaDark() = proof("panel-depth-tab-s9-ultra-fa-dark") { PanelDepth() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun panelDepthTabS9UltraFaLight() = proof("panel-depth-tab-s9-ultra-fa-light", darkTheme = false) { PanelDepth() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun panelDepthTabS9UltraEnDark() = proof("panel-depth-tab-s9-ultra-en-dark") { PanelDepth() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun panelDepthTabS9UltraEnLight() = proof("panel-depth-tab-s9-ultra-en-light", darkTheme = false) { PanelDepth() }

    @Composable
    private fun PanelAlerts() {
        ChartScreen(controller = ScreenshotFixtures.chartController(scope), sidePanels = shellPanels(), initialSidePanel = "alerts")
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun panelAlertsPixelTabletFaDark() = proof("panel-alerts-pixel-tablet-fa-dark") { PanelAlerts() }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun panelAlertsPixelTabletFaLight() = proof("panel-alerts-pixel-tablet-fa-light", darkTheme = false) { PanelAlerts() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun panelAlertsPixelTabletEnDark() = proof("panel-alerts-pixel-tablet-en-dark") { PanelAlerts() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun panelAlertsPixelTabletEnLight() = proof("panel-alerts-pixel-tablet-en-light", darkTheme = false) { PanelAlerts() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun panelAlertsTabS9UltraFaDark() = proof("panel-alerts-tab-s9-ultra-fa-dark") { PanelAlerts() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun panelAlertsTabS9UltraFaLight() = proof("panel-alerts-tab-s9-ultra-fa-light", darkTheme = false) { PanelAlerts() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun panelAlertsTabS9UltraEnDark() = proof("panel-alerts-tab-s9-ultra-en-dark") { PanelAlerts() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun panelAlertsTabS9UltraEnLight() = proof("panel-alerts-tab-s9-ultra-en-light", darkTheme = false) { PanelAlerts() }

    @Composable
    private fun PanelScript() {
        ChartScreen(controller = ScreenshotFixtures.chartController(scope), sidePanels = shellPanels(), initialSidePanel = "script")
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun panelScriptPixelTabletFaDark() = proof("panel-script-pixel-tablet-fa-dark") { PanelScript() }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun panelScriptPixelTabletFaLight() = proof("panel-script-pixel-tablet-fa-light", darkTheme = false) { PanelScript() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun panelScriptPixelTabletEnDark() = proof("panel-script-pixel-tablet-en-dark") { PanelScript() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun panelScriptPixelTabletEnLight() = proof("panel-script-pixel-tablet-en-light", darkTheme = false) { PanelScript() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun panelScriptTabS9UltraFaDark() = proof("panel-script-tab-s9-ultra-fa-dark") { PanelScript() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun panelScriptTabS9UltraFaLight() = proof("panel-script-tab-s9-ultra-fa-light", darkTheme = false) { PanelScript() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun panelScriptTabS9UltraEnDark() = proof("panel-script-tab-s9-ultra-en-dark") { PanelScript() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun panelScriptTabS9UltraEnLight() = proof("panel-script-tab-s9-ultra-en-light", darkTheme = false) { PanelScript() }

    @Composable
    private fun Panes4() {
        val workspace = remember {
            ChartWorkspaceStore(FakeScreenshotPreferences()).also { store ->
                scope.launch {
                    store.setPaneCount(4)
                    store.setPaneLayout(ChartLayoutPreset.FOUR)
                    store.setExtraPaneSymbols(listOf("BTCUSDT", "ETHUSDT", "XAGUSD"))
                }
            }
        }
        val controllers = remember { mutableMapOf<String, ChartController>() }
        ChartPanesScreen(
            firstSymbol = "XAUUSD",
            controllerFor = { symbol -> controllers.getOrPut(symbol) { ScreenshotFixtures.chartController(scope, symbol) } },
            watchlist = listOf("XAUUSD", "BTCUSDT", "ETHUSDT", "XAGUSD"),
            workspace = workspace,
            onBack = {},
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun panes4PixelTabletFaDark() = proof("panes-4-pixel-tablet-fa-dark") { Panes4() }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun panes4PixelTabletFaLight() = proof("panes-4-pixel-tablet-fa-light", darkTheme = false) { Panes4() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun panes4PixelTabletEnDark() = proof("panes-4-pixel-tablet-en-dark") { Panes4() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun panes4PixelTabletEnLight() = proof("panes-4-pixel-tablet-en-light", darkTheme = false) { Panes4() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun panes4TabS9UltraFaDark() = proof("panes-4-tab-s9-ultra-fa-dark") { Panes4() }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun panes4TabS9UltraFaLight() = proof("panes-4-tab-s9-ultra-fa-light", darkTheme = false) { Panes4() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun panes4TabS9UltraEnDark() = proof("panes-4-tab-s9-ultra-en-dark") { Panes4() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun panes4TabS9UltraEnLight() = proof("panes-4-tab-s9-ultra-en-light", darkTheme = false) { Panes4() }

    /** A chart with three drawings on it, so the object tree has rows. */
    private fun drawnController(): ChartController {
        val controller = ScreenshotFixtures.chartController(scope)
        val series = ScreenshotFixtures.chartSeries()
        val at = { fraction: Double -> series.time[(series.size * fraction).toInt().coerceIn(0, series.size - 1)] }
        val price = { fraction: Double -> series.close[(series.size * fraction).toInt().coerceIn(0, series.size - 1)] }
        val drawings = listOf(
            Drawing(id = 1, toolId = "trend", points = listOf(ChartPoint(at(0.55), price(0.55) * 0.995), ChartPoint(at(0.95), price(0.95) * 1.005))),
            Drawing(id = 2, toolId = "horizontal_line", points = listOf(ChartPoint(at(0.5), price(0.5)))),
            Drawing(id = 3, toolId = "rectangle", points = listOf(ChartPoint(at(0.2), price(0.2) * 1.01), ChartPoint(at(0.4), price(0.4) * 0.99))),
        )
        controller.onDrawing(controller.state.value.drawing.copy(drawings = drawings))
        return controller
    }

    /** The four panels the shell docks, with the fixtures their own captures use. */
    @Composable
    private fun shellPanels(): List<ChartSidePanel> {
        val series = ScreenshotFixtures.chartSeries()
        return listOf(
            ChartSidePanel("watchlist", com.coinepro.feature.chart.R.string.chart_panel_watchlist, com.coinepro.core.designsystem.R.drawable.icon_star) {
                val store = remember { WatchlistStore(FakeScreenshotPreferences()) }
                runBlocking { listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XAUUSD").forEach { store.add(Watchlist.DEFAULT_LIST_ID, it) } }
                WatchlistScreen(
                    controller = remember { MarketSearchController(ScreenshotFixtures.searchCatalog(), scope).also { it.start() } },
                    store = store,
                    sparklines = remember { ScreenshotFixtures.sparklineStore(scope) },
                    onOpenSymbol = {},
                    onOpenSearch = {},
                )
            },
            ChartSidePanel("depth", com.coinepro.feature.chart.R.string.chart_panel_depth, com.coinepro.core.designsystem.R.drawable.tv_chart_columns) {
                // **The ladder is the chart's own market** (run H item 6). It was a bitcoin book
                // beside a gold chart — a fixture, but a fixture that photographs as a bug, and
                // the owner read it as one. The app already binds the panel to `activeChartSymbol`;
                // this is the proof catching up with the app.
                val book = OrderBook.of(
                    symbol = "XAUUSD",
                    bids = listOf(2_704.6 to 3.4, 2_704.4 to 11.2, 2_704.2 to 6.1, 2_704.0 to 1.8).map { (p, q) -> DepthLevel(p, q) },
                    asks = listOf(2_705.0 to 2.2, 2_705.2 to 8.7, 2_705.4 to 4.0, 2_705.6 to 12.9).map { (p, q) -> DepthLevel(p, q) },
                    at = 1_772_000_000_000L,
                )
                DepthOfMarketBody(
                    state = OrderBookState(symbol = "XAUUSD", book = book, sourceName = "LBank Futures"),
                    onPickPrice = {},
                    onRetry = {},
                    // The panel writes «Depth of market» above the content already.
                    showTitle = false,
                )
            },
            ChartSidePanel("alerts", com.coinepro.feature.chart.R.string.chart_panel_alerts, com.coinepro.core.designsystem.R.drawable.icon_bell) {
                AlertCenterScreen(
                    controller = remember {
                        AlertsController(
                            store = LocalAlertStore(FakeScreenshotPreferences()),
                            audit = AlertAuditStore(FakeScreenshotPreferences()),
                            catalogOf = { ScreenshotFixtures.alertSymbols() },
                            scope = scope,
                        )
                    },
                )
            },
            ChartSidePanel("script", com.coinepro.feature.chart.R.string.chart_panel_script, com.coinepro.core.designsystem.R.drawable.tv_code2) {
                val controller = remember {
                    ScreenshotFixtures.scriptController(scope).also {
                        it.setSeries(series)
                        it.openPreset(com.coinepro.core.script.ScriptPresets.byId("rsi-zones")!!)
                    }
                }
                ScriptScreen(controller = controller, symbol = "XAUUSD", series = series)
            },
        )
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
        const val FA_1280 = "fa-rIR-ldrtl-sw800dp-w1280dp-h800dp-xhdpi"
        const val EN_1280 = "en-rUS-ldltr-sw800dp-w1280dp-h800dp-xhdpi"
        const val FA_S9U = "fa-rIR-ldrtl-sw1232dp-w1973dp-h1232dp-hdpi"
        const val EN_S9U = "en-rUS-ldltr-sw1232dp-w1973dp-h1232dp-hdpi"
    }
}
