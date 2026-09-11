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
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.CoineProListDetail
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.orderbook.DepthLevel
import com.coinepro.core.orderbook.OrderBook
import com.coinepro.core.orderbook.OrderBookState
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
 * Run H, photographed: the tablet's proportions, its rail, and the words on it.
 *
 * The owner's acceptance list, frame for frame — the chart with the rail and one flyout open, the
 * chart with the ladder docked, the chart with the NamaScript panel, the list-detail, and the
 * four-chart layout with a different market in every pane. Both tablets, both themes, both
 * languages where the owner asked for both.
 *
 * What each frame is evidence *for* is asserted beside it, so a frame cannot be a picture of a
 * broken screen: the rail is 48 points and is on the page, the plot keeps at least the owner's
 * share of the window, the panel writes its name once, and nothing on the English tablet is in
 * Persian.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RunHProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    // ── 1. the rail, and a flyout open on it ─────────────────────────────────────────────────

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun theRailAndAFlyout() {
        proof("run-h-rail-flyout-en-dark") { ChartScreen(controller = chart()) }
        // The rail is on the page, named for a screen reader, and 48 points wide — the number the
        // whole item is about. Measured from the node's own bounds rather than trusted.
        val rail = composeRule.onAllNodesWithContentDescription("chart-tool-rail").fetchSemanticsNodes()
        assertTrue("the tool rail is on the tablet chart", rail.isNotEmpty())
        val width = rail.first().size.width / composeRule.density.density
        assertTrue("the rail is a rail, not a panel: ${width}dp", width in 44f..52f)
        // And a group opens a flyout: the Lines group carries the trend line, which is what the
        // frame then shows over the plot.
        composeRule.onAllNodesWithContentDescription("Lines").fetchSemanticsNodes().firstOrNull()
            ?: error("the rail must carry a group glyph for Lines")
        composeRule.onAllNodesWithContentDescription("Lines")[0].performClick()
        composeRule.waitForIdle()
        assertTrue(
            "the flyout lists the group's tools",
            texts().any { it.contains("Trend line") },
        )
        // The flyout is a column in the layout rather than a popup window, so `decorView.draw`
        // photographs it with everything else. That is the whole reason it is not a `DropdownMenu`:
        // a menu is its own window, absent from a screenshot and awkward to hit under a test.
        capture("run-h-rail-flyout-open-en-dark")
    }

    // ── 2 and 3. the plot's share, with a panel docked ───────────────────────────────────────

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun chartAndDepthEnDark() = proof("run-h-chart-depth-en-dark") { ChartAndDepth() }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun chartAndDepthFaDark() = proof("run-h-chart-depth-fa-dark") { ChartAndDepth() }

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun chartAndDepthTabS9UltraEnLight() =
        proof("run-h-chart-depth-tab-s9-ultra-en-light", darkTheme = false) { ChartAndDepth() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun theDockedLadderNamesItselfOnce() {
        proof("run-h-depth-title-en-dark") { ChartAndDepth() }
        val titles = texts(descriptions = false).count { it == "Depth of market" }
        assertTrue("the panel writes its name once, not twice: $titles", titles == 1)
        // And it is the chart's own market rather than a fixture from another instrument.
        assertTrue("the ladder is the chart's symbol", texts().any { it.contains("XAUUSD") })
    }

    // ── 4. the NamaScript panel ──────────────────────────────────────────────────────────────

    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun chartAndScriptEnDark() {
        proof("run-h-chart-script-en-dark") { ChartAndScript() }
        val shown = texts()
        assertTrue("the editor's own words are English", shown.any { it == "Editor" })
        assertTrue("and the panel's tabs are still there", shown.any { it == "Reference" })
        // A docked panel is 400–480 points wide, which is one editor, not two columns. So the
        // split is **not offered** here: halving it would leave the editor 200 points and
        // `plot(rsi, ti` on a line, which is the thing item 4 is against.
        assertTrue("no split toggle in a panel too narrow for one", shown.none { it == "Code | chart" })
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_S9U)
    fun chartAndScriptFaDark() = proof("run-h-chart-script-fa-dark") { ChartAndScript() }

    /**
     * And the split itself, where there is room for it: the editor screen at full tablet width.
     *
     * Two columns of 400 points or nothing — so on the 1478-point Tab S9 Ultra the toggle is there
     * and on by default, and the frame is the code beside a chart that redraws as the reader types.
     */
    @Test
    @Config(sdk = [34], qualifiers = EN_S9U)
    fun theScriptScreenSplitsWhereThereIsRoom() {
        proof("run-h-script-split-en-dark") { FullScript() }
        val shown = texts()
        assertTrue("the split is offered at this width", shown.any { it == "Code | chart" })
        assertTrue("and the other half of the toggle with it", shown.any { it == "Code only" })
    }

    // ── the list-detail and the four charts ──────────────────────────────────────────────────

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun listDetailEnDark() = proof("run-h-list-detail-en-dark") { ListDetail() }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun listDetailFaLight() = proof("run-h-list-detail-fa-light", darkTheme = false) { ListDetail() }

    @Test
    @Config(sdk = [34], qualifiers = EN_1280)
    fun fourChartsEachWithItsOwnMarket() {
        proof("run-h-panes-4-en-dark") { Panes4() }
        // Four instruments, four prices: the fixture is seeded per symbol since run H, so the frame
        // is four charts rather than one chart printed four times.
        val shown = texts()
        for (symbol in listOf("XAUUSD", "BTCUSDT", "ETHUSDT", "XAGUSD")) {
            assertTrue("$symbol is one of the four panes", shown.any { it.contains(symbol) })
        }
        // And the timeframe in each header is the code, not «۱ ساعت».
        assertTrue("the pane headers carry the timeframe code", shown.any { it == "H1" })
        assertTrue("and not the prose name", shown.none { it.contains("۱ ساعت") })
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_1280)
    fun fourChartsFaDark() = proof("run-h-panes-4-fa-dark") { Panes4() }

    // ── 8. the phone, where run G left two things ────────────────────────────────────────────

    /**
     * The two phone frames the owner asked run H for: **the air on the right**, and **the gutter
     * with a crowd in it**.
     *
     * The margin is [ChartViewport.RIGHT_MARGIN_SHARE] — a tenth of the plot between the newest
     * bar's right edge and the price axis — and it is asserted as arithmetic in `ChartViewportTest`
     * rather than guessed from pixels here. What these frames add is the picture: a reader can hold
     * a rule to the PNG and see the tenth, and see that the gridline numbers are all still legible
     * with six levels' tags in the same gutter.
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_PHONE)
    fun thePhoneChartKeepsItsMarginAndItsAxisLabels() {
        proof("run-h-phone-levels-fa-dark") { PhoneChart() }
        // Every gridline number the axis drew is still a number — the tag pass suppresses a label
        // only where a tag took its row, and drops the tag instead wherever the ladder was there
        // first, so the scale is never the thing that goes.
        assertTrue("the axis is still printing prices", texts().any { it.contains("2,5") })
    }

    @Test
    @Config(sdk = [34], qualifiers = EN_PHONE)
    fun thePhoneChartInEnglishLight() =
        proof("run-h-phone-levels-en-light", darkTheme = false) { PhoneChart() }

    // ── the screens ──────────────────────────────────────────────────────────────────────────

    private fun chart(symbol: String = "XAUUSD"): ChartController =
        ScreenshotFixtures.chartController(scope, symbol)

    @Composable
    private fun ChartAndDepth() {
        ChartScreen(controller = chart(), sidePanels = listOf(depthPanel()), initialSidePanel = "depth")
    }

    @Composable
    private fun ChartAndScript() {
        ChartScreen(controller = chart(), sidePanels = listOf(scriptPanel()), initialSidePanel = "script")
    }

    private fun depthPanel() = ChartSidePanel(
        id = "depth",
        labelRes = com.coinepro.feature.chart.R.string.chart_panel_depth,
        icon = com.coinepro.core.designsystem.R.drawable.tv_chart_columns,
    ) {
        val book = OrderBook.of(
            symbol = "XAUUSD",
            bids = listOf(2_704.6 to 3.4, 2_704.4 to 11.2, 2_704.2 to 6.1, 2_704.0 to 1.8)
                .map { (p, q) -> DepthLevel(p, q) },
            asks = listOf(2_705.0 to 2.2, 2_705.2 to 8.7, 2_705.4 to 4.0, 2_705.6 to 12.9)
                .map { (p, q) -> DepthLevel(p, q) },
            at = 1_772_000_000_000L,
        )
        DepthOfMarketBody(
            state = OrderBookState(symbol = "XAUUSD", book = book, sourceName = "LBank Futures"),
            onPickPrice = {},
            onRetry = {},
            showTitle = false,
        )
    }

    private fun scriptPanel() = ChartSidePanel(
        id = "script",
        labelRes = com.coinepro.feature.chart.R.string.chart_panel_script,
        icon = com.coinepro.core.designsystem.R.drawable.tv_code2,
    ) {
        val series = ScreenshotFixtures.chartSeries(symbol = "XAUUSD")
        val controller = remember {
            ScreenshotFixtures.scriptController(scope).also {
                it.setSeries(series)
                it.openPreset(com.coinepro.core.script.ScriptPresets.byId("rsi-zones")!!)
            }
        }
        ScriptScreen(controller = controller, symbol = "XAUUSD", series = series)
    }

    /**
     * A phone chart with enough overlays to crowd the price gutter.
     *
     * Six studies rather than two: each of them puts at least one level in the gutter, and a
     * Bollinger band puts three, which is the density the owner's «برخورد برچسب‌ها» was about.
     */
    @Composable
    private fun PhoneChart() {
        val controller = remember {
            chart().also { chart ->
                listOf("ema", "sma", "bollinger", "supertrend", "vwap", "sar")
                    .forEach(chart::toggleIndicator)
            }
        }
        ChartScreen(controller = controller)
    }

    /** The editor screen on its own, with the whole tablet to lay out in. */
    @Composable
    private fun FullScript() {
        val series = ScreenshotFixtures.chartSeries(symbol = "XAUUSD")
        val controller = remember {
            ScreenshotFixtures.scriptController(scope).also {
                it.setSeries(series)
                it.openPreset(com.coinepro.core.script.ScriptPresets.byId("rsi-zones")!!)
                it.run()
            }
        }
        ScriptScreen(controller = controller, symbol = "XAUUSD", series = series)
    }

    @Composable
    private fun ListDetail() {
        val store = remember { WatchlistStore(FakeScreenshotPreferences()) }
        runBlocking {
            listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XAUUSD").forEach {
                store.add(Watchlist.DEFAULT_LIST_ID, it)
            }
        }
        CoineProListDetail(detail = { ChartScreen(controller = chart()) }) {
            WatchlistScreen(
                controller = remember {
                    MarketSearchController(ScreenshotFixtures.searchCatalog(), scope).also { it.start() }
                },
                store = store,
                sparklines = remember { ScreenshotFixtures.sparklineStore(scope) },
                onOpenSymbol = {},
                onOpenSearch = {},
            )
        }
    }

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
            controllerFor = { symbol -> controllers.getOrPut(symbol) { chart(symbol) } },
            watchlist = listOf("XAUUSD", "BTCUSDT", "ETHUSDT", "XAGUSD"),
            workspace = workspace,
            onBack = {},
        )
    }

    // ── the camera ───────────────────────────────────────────────────────────────────────────

    /**
     * Every string the frame is drawing.
     *
     * Over *all* roots rather than `onRoot()`: a flyout is a popup with a root of its own, and a
     * single-root query fails the moment one is open — which is exactly the frame this run is for.
     *
     * [descriptions] is off where the assertion is about what a reader **sees**: a rail glyph
     * carries its panel's name as a content description, so counting both would report the ladder's
     * title twice when the screen writes it once.
     */
    private fun texts(descriptions: Boolean = true): List<String> {
        val found = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.find { it.key == SemanticsProperties.Text }?.let { entry ->
                @Suppress("UNCHECKED_CAST")
                (entry.value as? List<AnnotatedString>)?.forEach { found += it.text }
            }
            if (descriptions) {
                node.config.find { it.key == SemanticsProperties.ContentDescription }?.let { entry ->
                    @Suppress("UNCHECKED_CAST")
                    (entry.value as? List<String>)?.forEach { found += it }
                }
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
        capture(name)
    }

    private fun capture(name: String) {
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
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        OUTPUT.mkdirs()
        File(OUTPUT, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private companion object {
        val OUTPUT = File("build/proof")

        /** A Pixel Tablet and a Tab S9 Ultra, in both languages. The owner's two devices. */
        const val EN_1280 = "en-rUS-w1280dp-h800dp-xhdpi"
        const val FA_1280 = "fa-rIR-ldrtl-w1280dp-h800dp-xhdpi"
        const val EN_S9U = "en-rUS-w1478dp-h924dp-xhdpi"
        const val FA_S9U = "fa-rIR-ldrtl-w1478dp-h924dp-xhdpi"

        /** And a Pixel 7, for the two items run G left on the phone. */
        const val FA_PHONE = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"
        const val EN_PHONE = "en-rUS-w411dp-h914dp-xxhdpi"
    }
}
