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
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import com.coinepro.core.common.AppLanguage
import com.coinepro.core.datastore.MarketColorScheme
import com.coinepro.core.datastore.QuoteCurrency
import com.coinepro.core.datastore.ThemeMode
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.marketdata.MarketPulse
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.marketdata.MarketTicker
import com.coinepro.core.marketdata.MarketTickerGateway
import com.coinepro.core.marketdata.MarketTickerStore
import com.coinepro.core.marketdata.MarketTickerTable
import com.coinepro.feature.search.MarketHeadline
import com.coinepro.feature.search.MarketNewsTicker
import com.coinepro.feature.search.MarketPulseRow
import com.coinepro.feature.search.MarketsPage
import com.coinepro.feature.search.MarketsScreen
import com.coinepro.feature.search.MarketsTabRow
import com.coinepro.feature.search.WatchlistScreen
import com.coinepro.feature.search.offeredPages
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.coinepro.feature.search.R as SearchR

/**
 * **The five things phase Υ put on the glass** (run ΤΦΥ, U1–U5).
 *
 * Every one of these is a surface whose whole content is its arrangement, so a test that only
 * asserted the semantics tree would pass on a screen nobody could read. What each case does is take
 * the frame *and* pin the one claim a still cannot make on its own — that a dash is a dash rather
 * than a missing figure, that seven tabs are seven, that a slide advances.
 *
 * The qualifiers are the owner's phone: 411dp at 420dpi, Persian, right to left. The frames land in
 * `app/build/proof/` and are named in `docs/runs/RUN_TFY/CHECKLIST.md`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpsilonProofTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    // ---------------------------------------------------------------- U1, the welcome

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the welcome opens on the first slide, with both buttons fixed under it`() {
        render { WelcomeSlides(onStart = {}, onSignIn = {}) }
        capture("upsilon-welcome-phone-fa")

        assertTrue(
            "the welcome did not draw",
            composeRule.onAllNodesWithTag(WELCOME_TAG).fetchSemanticsNodes().isNotEmpty(),
        )
        // The two buttons are the fixed part: they do not move between slides, so a reader who has
        // decided on slide one does not have to wait for the carousel to stop to act on it.
        listOf(R.string.welcome_start, R.string.welcome_sign_in).forEach { label ->
            val text = composeRule.activity.getString(label)
            assertTrue(
                "«$text» is not under the slides",
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @Test
    fun `there are five slides and each one says something different`() {
        // The count and the copy, without a frame: five is the owner's number, and five slides
        // carrying four distinct headings would be a carousel that repeats itself.
        assertEquals(5, WelcomeSlide.entries.size)
        val headings = WelcomeSlide.entries.map { it.headline }.toSet()
        assertEquals("two slides share a heading", 5, headings.size)
    }

    // ---------------------------------------------------------------- U2, the preferences

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the starter asks four questions and every one of them is already answered`() {
        render {
            StarterPreferences(
                theme = ThemeMode.SYSTEM,
                onTheme = {},
                language = AppLanguage.Default,
                onLanguage = {},
                quote = QuoteCurrency.USDT,
                onQuote = {},
                colours = MarketColorScheme.GREEN_UP,
                onColours = {},
                onDone = {},
            )
        }
        capture("upsilon-starter-phone-fa")

        assertTrue(
            "the starter did not draw",
            composeRule.onAllNodesWithTag(STARTER_TAG).fetchSemanticsNodes().isNotEmpty(),
        )
        listOf(
            R.string.starter_theme,
            R.string.starter_language,
            R.string.starter_quote,
            R.string.starter_colours,
            // Live from the first frame: nothing here is required, so the button is never dead.
            R.string.starter_done,
        ).forEach { label ->
            val text = composeRule.activity.getString(label)
            assertTrue(
                "«$text» is missing from the starter",
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
            )
        }
        // The language row is written in itself, so it works for a reader who cannot read the rest.
        AppLanguage.entries.forEach { language ->
            assertTrue(
                "«${language.displayName}» is not written in its own language",
                composeRule.onAllNodesWithText(language.displayName).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    // ---------------------------------------------------------------- U3, the pulse

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the pulse draws a dash for every figure this app cannot compute`() {
        // The state that actually ships: one real figure and three explained dashes. The frame is
        // the evidence that a dash reads as an answer rather than as a cell that failed to load.
        render {
            MarketPulseRow(
                pulse = MarketPulse(turnover24h = 1_420_000_000.0, breadth = 61, markets = 1_333),
                onOpen = {},
            )
        }
        capture("upsilon-pulse-missing-phone-fa")

        val dashes = composeRule.onAllNodesWithText(EM_DASH).fetchSemanticsNodes()
        assertEquals("three of the four cells have no figure — see MarketPulse", 3, dashes.size)
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the pulse draws four figures the day every backend serves them`() {
        // The other half, and the reason the row is worth building before the data exists: nothing
        // about its shape changes when the figures arrive. A reader who learned where «سهم
        // بیت‌کوین» sits finds it in the same place.
        render {
            MarketPulseRow(
                pulse = MarketPulse(
                    marketCap = 2_380_000_000_000.0,
                    turnover24h = 94_600_000_000.0,
                    bitcoinDominance = 54.0,
                    fearGreed = 72,
                    breadth = 61,
                    markets = 1_333,
                ),
                onOpen = {},
            )
        }
        capture("upsilon-pulse-full-phone-fa")

        assertTrue(
            "a cell with a figure must not also carry a dash",
            composeRule.onAllNodesWithText(EM_DASH).fetchSemanticsNodes().isEmpty(),
        )
    }

    // ---------------------------------------------------------------- U4, the ticker

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the ticker carries the headlines and marks the important one`() {
        render {
            MarketNewsTicker(
                headlines = listOf(
                    MarketHeadline("a", "نرخ بهره آمریکا بدون تغییر ماند", important = true),
                    MarketHeadline("b", "حجم معاملات بیت‌کوین به بالاترین حد ماه رسید"),
                    MarketHeadline("c", "طلا در کانال تازه‌ای تثبیت شد"),
                ),
                onOpen = {},
            )
        }
        capture("upsilon-ticker-phone-fa")

        val important = composeRule.activity.getString(SearchR.string.markets_news_important)
        assertEquals(
            "exactly the story that carries it gets the chip",
            1,
            composeRule.onAllNodesWithText(important).fetchSemanticsNodes().size,
        )
    }

    // ---------------------------------------------------------------- U5, the tabs

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the seven tabs are one strip, with the live one underlined`() {
        val pages = MarketsPage.entries.toList()
        render { MarketsTabRow(pages = pages, selected = MarketsPage.MOVERS, onSelect = {}) }
        capture("upsilon-tabs-phone-fa")

        pages.forEach { page ->
            val text = composeRule.activity.getString(page.labelRes)
            assertTrue(
                "«$text» is not in the strip",
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `a platform with no figures draws four tabs rather than seven empty ones`() {
        // CoinePro-FX, and the frame is the point: what a reader sees there is a shorter strip, not
        // three tabs that open onto «موردی یافت نشد».
        val pages = offeredPages(
            families = setOf(
                com.coinepro.core.symbols.SymbolCategory.FOREX,
                com.coinepro.core.symbols.SymbolCategory.METAL,
            ),
            hasFigures = false,
        )
        render { MarketsTabRow(pages = pages, selected = MarketsPage.TOP, onSelect = {}) }
        capture("upsilon-tabs-nofigures-phone-fa")

        assertEquals(4, pages.size)
        listOf(MarketsPage.POPULAR, MarketsPage.MOVERS, MarketsPage.VOLUME).forEach { absent ->
            val text = composeRule.activity.getString(absent.labelRes)
            assertTrue(
                "«$text» is drawn on a platform that can never fill it",
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    // ---------------------------------------------------------------- U6, the lists

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the watchlist names the live list and offers a plus, an overflow and a comparison`() {
        val store = WatchlistStore(FakeScreenshotPreferences())
        runBlocking {
            store.create("گاوها")
            listOf("BTCUSDT", "ETHUSDT", "SOLUSDT").forEach {
                store.add(Watchlist.DEFAULT_LIST_ID, it)
            }
        }
        render {
            WatchlistScreen(
                controller = remember {
                    MarketSearchController(ScreenshotFixtures.searchCatalog(), scope).also { it.start() }
                },
                store = store,
                sparklines = remember { ScreenshotFixtures.sparklineStore(scope) },
                onOpenSymbol = {},
                onOpenSearch = {},
                onCompare = {},
            )
        }
        capture("upsilon-watchlist-phone-fa")

        // The four controls the picker row carries. Read by their labels, which is what a screen
        // reader announces and what the frame shows — the glyphs themselves carry no text.
        listOf(
            SearchR.string.watchlist_pick_list,
            SearchR.string.watchlist_create,
            SearchR.string.watchlist_compare,
            SearchR.string.watchlist_more,
        ).forEach { label ->
            val text = composeRule.activity.getString(label)
            assertTrue(
                "«$text» is not on the watchlist's control row",
                composeRule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty(),
            )
        }
        // And the live list's own name, which is the thing the chip row could not say.
        assertTrue(
            "the control row does not name the list being shown",
            composeRule.onAllNodesWithText(Watchlist.DEFAULT_LIST_NAME).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `a list of one market is not offered a comparison`() {
        // Two is the floor: one market against itself is the split view, and the reader asked for
        // something else. Absent rather than inert — see `WatchlistPanel.onCompare`.
        val store = WatchlistStore(FakeScreenshotPreferences())
        runBlocking { store.add(Watchlist.DEFAULT_LIST_ID, "BTCUSDT") }
        render {
            WatchlistScreen(
                controller = remember {
                    MarketSearchController(ScreenshotFixtures.searchCatalog(), scope).also { it.start() }
                },
                store = store,
                sparklines = remember { ScreenshotFixtures.sparklineStore(scope) },
                onOpenSymbol = {},
                onCompare = {},
            )
        }
        val compare = composeRule.activity.getString(SearchR.string.watchlist_compare)
        assertTrue(
            "«$compare» is offered on a list with nothing to compare against",
            composeRule.onAllNodesWithContentDescription(compare).fetchSemanticsNodes().isEmpty(),
        )
    }

    // ---------------------------------------------------------------- U3–U5, on the screen

    @Test
    @Config(sdk = [34], qualifiers = PHONE)
    fun `the markets surface carries the pulse, the ticker, the doors and the tabs`() {
        val controller = MarketSearchController(
            gateway = ScreenshotFixtures.emptyCatalog(),
            scope = scope,
            universe = com.coinepro.core.marketdata.BundledSymbolUniverseGateway,
        )
        render {
            val ready = remember { controller.also { it.start() } }
            MarketsScreen(
                controller = ready,
                sparklines = ScreenshotFixtures.sparklineStore(scope),
                onOpenSymbol = {},
                onOpenSearch = {},
                headlines = listOf(
                    MarketHeadline("a", "نرخ بهره آمریکا بدون تغییر ماند", important = true),
                    MarketHeadline("b", "حجم معاملات بیت‌کوین به بالاترین حد ماه رسید"),
                ),
                onOpenHeadline = {},
                onOpenNews = {},
                onOpenCalendar = {},
                onOpenHeatmap = {},
                tickers = remember { tickers() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        capture("upsilon-markets-phone-fa")

        // The three rooms Explore used to be the only door to (U7).
        listOf(
            SearchR.string.markets_door_news,
            SearchR.string.markets_door_calendar,
            SearchR.string.markets_door_heatmap,
            // And the tab the screen opens on.
            SearchR.string.markets_page_top,
            // The pulse, which is drawn because this fixture has a ticker route — the same
            // «absent, not empty» rule the ordering tabs are drawn by. See `tickers()`.
            SearchR.string.markets_pulse_volume,
            // And one of the three tabs that route makes possible.
            SearchR.string.markets_page_volume,
        ).forEach { label ->
            val text = composeRule.activity.getString(label)
            assertTrue(
                "«$text» is not on the markets surface",
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    // ---------------------------------------------------------------- the other half of the matrix

    @Test
    @Config(sdk = [34], qualifiers = ENGLISH)
    fun `the markets surface reads in English on the light theme`() {
        // The other three quarters of «phone fa/en × dark/light» are the same compositions under a
        // different locale and a different palette, and the two that can go wrong are on this
        // frame: a Persian string left in an English build, and an underline or a dash that
        // vanishes against a white stage.
        val controller = MarketSearchController(
            gateway = ScreenshotFixtures.emptyCatalog(),
            scope = scope,
            universe = com.coinepro.core.marketdata.BundledSymbolUniverseGateway,
        )
        render(dark = false) {
            val ready = remember { controller.also { it.start() } }
            MarketsScreen(
                controller = ready,
                sparklines = ScreenshotFixtures.sparklineStore(scope),
                onOpenSymbol = {},
                onOpenSearch = {},
                headlines = listOf(
                    MarketHeadline("a", "Rates held steady", important = true),
                    MarketHeadline("b", "Bitcoin turnover at a monthly high"),
                ),
                onOpenHeadline = {},
                onOpenNews = {},
                onOpenCalendar = {},
                onOpenHeatmap = {},
                tickers = remember { tickers() },
                modifier = Modifier.fillMaxSize(),
            )
        }
        capture("upsilon-markets-phone-en-light")

        // English, from `values/` — the locale inversion means this is the *default* file, so a
        // string that was only ever written in Persian shows up here and nowhere else.
        listOf("Top", "News", "Calendar", "Heat map", "24h volume").forEach { word ->
            assertTrue(
                "«$word» is missing from the English markets surface",
                composeRule.onAllNodesWithText(word).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = ENGLISH)
    fun `the welcome reads in English on the light theme`() {
        render(dark = false) { WelcomeSlides(onStart = {}, onSignIn = {}) }
        capture("upsilon-welcome-phone-en-light")

        listOf(R.string.welcome_start, R.string.welcome_sign_in).forEach { label ->
            val text = composeRule.activity.getString(label)
            assertTrue(
                "«$text» is not under the slides",
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty(),
            )
        }
    }

    /**
     * A ticker table, so the markets frame carries the pulse and all seven tabs.
     *
     * The markets surface draws the pulse and the three ordering tabs **only where there is a route
     * to fill them** — the same «absent, not empty» rule the tabs have always been drawn by. Without
     * a store the frame would be CoinePro-FX's screen, which is a real screen and is not the one
     * these cases are about.
     */
    private fun tickers(): MarketTickerStore = MarketTickerStore(
        gateway = object : MarketTickerGateway {
            override val supported: Boolean = true
            override suspend fun load(symbols: List<String>?): MarketTickerTable = MarketTickerTable(
                tickers = listOf(
                    MarketTicker("BTCUSDT", last = 91_248.30, changePercent24h = 2.4, turnover24h = 918_442_310.0),
                    MarketTicker("ETHUSDT", last = 3_142.10, changePercent24h = -1.1, turnover24h = 412_004_900.0),
                    MarketTicker("SOLUSDT", last = 186.42, changePercent24h = 5.8, turnover24h = 96_330_110.0),
                ).associateBy { it.symbol },
                serverTimeEpochMillis = null,
                cacheTtlMillis = null,
                fetchedAtEpochMillis = null,
                source = null,
            )
        },
        scope = scope,
    )

    private fun render(dark: Boolean = true, content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                CoineProTheme(darkTheme = dark) {
                    Surface(modifier = Modifier.fillMaxSize()) { content() }
                }
            }
        }
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
    }

    private fun capture(name: String) {
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
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
        const val PHONE = "fa-rIR-ldrtl-w411dp-h914dp-420dpi"

        /** The same phone, in the language `values/` holds. See the locale-inversion note. */
        const val ENGLISH = "en-rUS-ldltr-w411dp-h914dp-420dpi"

        /** What a pulse cell with no figure draws. Spelled out, so the assertion reads as the pixels do. */
        const val EM_DASH = "—"
    }
}
