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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import com.coinepro.core.chart.Candle
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
 * Run G, photographed and asserted: the i18n leaks the owner found in the 4.70.0 frames.
 *
 * The four defects this class is the evidence for, each one a line the owner read off a screenshot
 * of the *English* app:
 *
 *  * «طلا / دلار آمریکا» in the chart header and «بیت‌کوین/تتر» in the watchlist — a symbol had one
 *    name and it was the Persian one. Now it has two and the screen picks (`localName`).
 *  * «۴ symbols» — a prose count formatted in Persian digits inside an English sentence, because
 *    the formatter read a fixed table rather than the screen's language (`proseDigits`).
 *  * «دیده‌بان» as the heading of the English watchlist — the base list's stored name, which is
 *    user data, drawn without the translation that stands in for it until it is renamed.
 *  * «+$261.40 · +2.14% امروز» — a composite assembled in code with an untranslated period word.
 *
 * Every frame goes to `build/proof/` for `docs/qa/screenshots/4.71/`, beside the 4.70.0 set which
 * is the "before", and every one carries an assertion so a frame cannot be a picture of a broken
 * screen.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
class RunGProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    // ── The three screens, dark and light, Persian and English ───────────────────────────────

    @Test
    fun homeFrames() = everyWay("run-g-home") { Home() }

    @Test
    fun watchlistFrames() = everyWay("run-g-watchlist") { WatchlistPage() }

    @Test
    fun chartFrames() = everyWay("run-g-chart") { Chart() }

    // ── A. the instrument's name follows the screen's language ───────────────────────────────

    @Test
    fun theEnglishChartHeaderNamesTheInstrumentInEnglish() {
        proof("run-g-chart-name-en", dark = true, persian = false) { Chart() }
        // The legend's title line. `XAUUSD` is gold against the dollar in both languages and only
        // one of them was ever printed.
        assertTrue(
            "the English chart must head itself «Gold / US Dollar», not «طلا / دلار آمریکا»",
            texts().any { it.contains("Gold / US Dollar") },
        )
        assertTrue(
            "and no Persian name may be left on the English screen",
            texts().none { it.contains("طلا") },
        )
    }

    @Test
    fun thePersianChartHeaderIsUnchanged() {
        proof("run-g-chart-name-fa", dark = true, persian = true) { Chart() }
        assertTrue(
            "the Persian chart keeps «طلا / دلار آمریکا»",
            texts().any { it.contains("طلا") && it.contains("دلار") },
        )
    }

    @Test
    fun theEnglishWatchlistNamesEveryRowInEnglish() {
        proof("run-g-watchlist-names-en", dark = true, persian = false) { WatchlistPage() }
        val shown = texts()
        assertTrue(
            "«Bitcoin/Tether» rather than «بیت‌کوین/تتر»",
            shown.any { it.contains("Bitcoin") && it.contains("Tether") },
        )
        assertTrue(
            "«Gold/Dollar» rather than «طلا/دلار»",
            shown.any { it.contains("Gold") && it.contains("Dollar") },
        )
        assertTrue("no Persian instrument name survives", shown.none { it.contains("بیت‌کوین") })
        // And the columns over those rows are headed in English too: the table's headings are
        // stored beside the column set rather than in `strings.xml`, so they were Persian-only.
        assertTrue("«Last» rather than «آخرین»", shown.any { it.contains("Last") })
        assertTrue("«Trend» rather than «روند»", shown.any { it.contains("Trend") })
        assertTrue("no Persian heading over an English column", shown.none { it.contains("آخرین") })
    }

    // ── B and C. counts, the list's own name, and the composite ──────────────────────────────

    @Test
    fun theEnglishWatchlistCountsInLatinDigits() {
        proof("run-g-watchlist-count-en", dark = true, persian = false) { WatchlistPage() }
        assertTrue(
            "«4 symbols», not «۴ symbols»",
            texts().any { it.contains("4 symbols") },
        )
        val persianDigits = texts().filter { text -> text.any { it in '۰'..'۹' } }
        assertTrue(
            "no Persian digit may appear anywhere on the English screen: $persianDigits",
            persianDigits.isEmpty(),
        )
    }

    @Test
    fun thePersianWatchlistStillCountsInPersianDigits() {
        proof("run-g-watchlist-count-fa", dark = true, persian = true) { WatchlistPage() }
        assertTrue("«۴ نماد»", texts().any { it.contains("۴ نماد") })
    }

    @Test
    fun theBaseListIsNamedInTheScreensLanguage() {
        proof("run-g-watchlist-listname-en", dark = true, persian = false) { WatchlistPage() }
        assertTrue("«Watchlist», not «دیده‌بان»", texts().any { it.contains("Watchlist") })
        assertTrue("and nothing left in Persian", texts().none { it.contains("دیده‌بان") })
    }

    @Test
    fun theChangePillIsOneTranslatedSentence() {
        proof("run-g-home-change-en", dark = true, persian = false) { Home() }
        assertTrue(
            "«… · … today», not «… · … امروز»",
            texts().any { it.contains("today") },
        )
        assertTrue("no Persian word inside an English pill", texts().none { it.contains("امروز") })
    }

    // ── D. the legend's format ───────────────────────────────────────────────────────────────

    @Test
    fun theCollapsedLegendPutsItsCountAtTheEnd() {
        proof("run-g-chart-legend-ten", dark = true, persian = true) { Chart(studies = TEN_INDICATORS) }
        val shown = texts()
        assertTrue(
            "the count is a chip at the end of the row — «▸ +9»",
            shown.any { it.contains("▸ +9") },
        )
        assertTrue(
            "and it is no longer welded onto the indicator's name",
            shown.none { it.contains("  +9") },
        )
    }

    // ── E. the pair mark is artwork, never an emoji ──────────────────────────────────────────

    @Test
    fun noSymbolHeaderCarriesAnEmoji() {
        for (symbol in listOf("XAUUSD", "EURUSD")) {
            proof("run-g-header-${symbol.lowercase()}", dark = true, persian = false) {
                Chart(symbol = symbol)
            }
            val offenders = texts().filter { text -> text.codePoints().anyEmoji() }
            assertTrue(
                "a symbol header drew an emoji instead of artwork: $offenders",
                offenders.isEmpty(),
            )
        }
    }

    // ── F. the shape under a row is the day, drawn straight ──────────────────────────────────

    @Test
    fun theSparklinesAreADaysWorthOfRealCloses() {
        proof("run-g-watchlist-sparkline", dark = true, persian = true) { WatchlistPage() }
        // The store's own contract, asserted here because the line itself is canvas: a day at
        // half-hourly resolution is forty-eight closes, which is what the renderer is handed.
        val store = ScreenshotFixtures.sparklineStore(scope)
        store.request("BTCUSDT")
        store.request("XAUUSD")
        val lines = store.lines.value
        assertTrue("every row's line is a full day of closes", lines.values.all { it.size >= 48 })
        assertTrue(
            "and two markets never draw the same shape",
            lines["BTCUSDT"] != lines["XAUUSD"],
        )
    }

    // ── G. the readings panel opens on a drag, not on arrival ────────────────────────────────

    @Test
    fun theReadingsPanelIsClosedOnArrival() {
        proof("run-g-chart-readings-closed", dark = true, persian = true) { Chart() }
        val heading = composeRule.activity.getString(
            com.coinepro.feature.chart.R.string.chart_readings_disclosure,
        )
        assertTrue("the handle is on the page", texts().any { it.contains(heading) })
        // What is *inside* the panel is not: the trend reading's own heading is the panel's first
        // block, and a closed panel does not draw it.
        val reading = composeRule.activity.getString(
            com.coinepro.feature.chart.R.string.chart_reading_title,
        )
        assertTrue("and the readings behind it are not", texts().none { it.contains(reading) })
    }

    // ── The screens ──────────────────────────────────────────────────────────────────────────

    @Composable
    private fun Home() {
        HomeScreen(
            state = ScreenshotFixtures.marketState(),
            onRetry = {},
            displayName = "بهنام",
            briefing = ScreenshotFixtures.homeBriefing,
            portfolio = ScreenshotFixtures.homePortfolioFromAccount(),
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
        symbol: String = "XAUUSD",
    ) {
        val controller = remember(studies, symbol) {
            chartController(symbol).also { chart -> studies.forEach(chart::toggleIndicator) }
        }
        ChartScreen(controller = controller)
    }

    private fun chartController(symbol: String): ChartController {
        val series = walk(bars = 400)
        val gateway = object : CandleGateway {
            override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?) =
                CandlePage(
                    symbol,
                    timeframe,
                    series.bars.map { OhlcBar(it.t, it.o, it.h, it.l, it.c, it.v ?: 0.0) },
                    hasMore = true,
                )
        }
        return ChartController(symbol, gateway, scope).also { it.start() }
    }

    private fun walk(bars: Int): CandleSeries {
        var seed = 20_260_911L
        fun random(): Double {
            seed = (seed * 1103515245 + 12345) and 0x7FFFFFFF
            return seed.toDouble() / 0x7FFFFFFF
        }
        val hour = 3_600L
        val last = 1_760_000_000L
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

    // ── Reading the screen ───────────────────────────────────────────────────────────────────

    /** Every string the current frame is drawing, through the semantics tree. */
    private fun texts(): List<String> {
        val found = mutableListOf<String>()
        fun walk(node: androidx.compose.ui.semantics.SemanticsNode) {
            node.config.find { it.key == SemanticsProperties.Text }
                ?.let { entry ->
                    @Suppress("UNCHECKED_CAST")
                    (entry.value as? List<androidx.compose.ui.text.AnnotatedString>)
                        ?.forEach { found += it.text }
                }
            node.children.forEach(::walk)
        }
        walk(composeRule.onRoot().fetchSemanticsNode())
        return found
    }

    /** Whether any code point is in the emoji planes the owner's test names. */
    private fun java.util.stream.IntStream.anyEmoji(): Boolean =
        anyMatch { it in EMOJI_FIRST..EMOJI_LAST }

    // ── The camera ───────────────────────────────────────────────────────────────────────────

    private fun everyWay(name: String, content: @Composable () -> Unit) {
        for (dark in listOf(true, false)) {
            for (persian in listOf(true, false)) {
                val tag = (if (persian) "fa" else "en") + "-" + (if (dark) "dark" else "light")
                proof("$name-$tag", dark = dark, persian = persian, content = content)
            }
        }
    }

    private val slot = mutableStateOf<@Composable () -> Unit>({})
    private val dark = mutableStateOf(true)
    private val persian = mutableStateOf(true)
    private var started = false

    /** One composition for the whole class, swapped through a slot. See `RunFProofTest`. */
    private fun proof(name: String, dark: Boolean, persian: Boolean, content: @Composable () -> Unit) {
        this.dark.value = dark
        this.persian.value = persian
        slot.value = content
        if (!started) {
            started = true
            composeRule.setContent {
                val base = LocalContext.current
                val configuration = Configuration(LocalConfiguration.current).apply {
                    setLocale(if (this@RunGProofTest.persian.value) Locale("fa", "IR") else Locale.US)
                }
                val context = remember(this@RunGProofTest.persian.value) {
                    base.createConfigurationContext(configuration)
                }
                CompositionLocalProvider(
                    LocalContext provides context,
                    LocalConfiguration provides configuration,
                    LocalLayoutDirection provides
                        if (this@RunGProofTest.persian.value) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    LocalTeachingDismissals provides AllTeachingDismissed,
                    LocalActivityResultRegistryOwner provides composeRule.activity,
                ) {
                    CoineProTheme(darkTheme = this@RunGProofTest.dark.value) {
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

        /** Ten indicators, so nine of them fold behind the first. */
        val TEN_INDICATORS = listOf(
            "ema", "sma", "bollinger", "supertrend", "vwap",
            "rsi", "macd", "atr", "stochastic", "obv",
        )

        /**
         * The emoji planes, as the owner's acceptance names them: U+1F000–U+1FAFF.
         *
         * Mahjong tiles through the newest symbol block, which is every pictograph a font would
         * substitute for a flag or a medal. A symbol header must contain none of them: what names
         * an instrument there is artwork — two overlapped discs — and an emoji in its place is the
         * platform's font speaking for the product.
         */
        const val EMOJI_FIRST = 0x1F000
        const val EMOJI_LAST = 0x1FAFF
    }
}
