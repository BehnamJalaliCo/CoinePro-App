package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.coinepro.app.GoldenScreenshot.assertMatchesGolden
import com.coinepro.app.ideas.IdeasFace
import com.coinepro.app.ideas.IdeasScreen
import com.coinepro.core.community.CommunityController
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.designsystem.TeachingDismissals
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.navigation.AppDestination
import com.coinepro.core.signals.SignalController
import com.coinepro.feature.chart.ChartScreen
import com.coinepro.feature.community.CommunityScreen
import com.coinepro.feature.explore.ExploreScreen
import com.coinepro.feature.menu.MenuAccess
import com.coinepro.feature.menu.MenuScreen
import com.coinepro.feature.search.WatchlistScreen
import com.coinepro.feature.signals.SignalsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The surfaces whose pixels are committed, and the widths they are committed at.
 *
 * ### Why this matrix and not another
 *
 * Every case here is a screen where **a point of chrome is the product**. The watchlist's fold
 * decides how many markets a reader sees; the bar's height is a ninth of the phone; Explore's fold
 * decides whether the page opens on market content or on the way out of itself; Ideas is the one
 * place two screens are composed inside a third, which is exactly where a duplicated header hides.
 * A regression on any of them is invisible in a unit test and obvious in a diff.
 *
 * ### Two widths, and 393 is the important one
 *
 * 411 is this design system's reference width. It is **wider than the phone most readers hold**, and
 * that gap has already shipped one visible fault — the watchlist's move column ran off the row at
 * 393 while every measurement said it fitted. So the surfaces where width is tightest are pinned at
 * both, and the narrow one is not optional.
 *
 * ### Both themes, and both directions
 *
 * Dark and light for the three list surfaces, because this palette is not an inversion and several
 * of its tokens genuinely swap roles. And one English case, because the whole layout mirrors: a
 * screen that is right in Persian can be wrong in English in ways only a picture shows.
 *
 * See [GoldenScreenshot] for what a failure prints and how to re-record.
 */
@RunWith(RobolectricTestRunner::class)
// Real pixels. Robolectric's legacy graphics mode records draw calls and returns a bitmap of the
// fill colour, which for a golden comparison is a test that compares two blank frames and always
// passes. Native runs the same Skia the device does.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoldenScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    /* ------------------------------------------------------------------ watchlist */

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun watchlistDark() = composeRule.assertMatchesGolden("watchlist-fa-411-dark") { Watchlist() }

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun watchlistLight() =
        composeRule.assertMatchesGolden("watchlist-fa-411-light", darkTheme = false) { Watchlist() }

    /** The narrow phone, which is the one the column arithmetic is actually measured against. */
    @Test
    @Config(sdk = [34], qualifiers = FA_393)
    fun watchlistNarrow() = composeRule.assertMatchesGolden("watchlist-fa-393") { Watchlist() }

    /** The same screen with the layout mirrored, which is a different layout. */
    @Test
    @Config(sdk = [34], qualifiers = EN_411)
    fun watchlistEnglish() = composeRule.assertMatchesGolden("watchlist-en-411") { Watchlist() }

    /* ------------------------------------------------------------------ explore */

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun exploreDark() = composeRule.assertMatchesGolden("explore-fa-411-dark") { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun exploreLight() =
        composeRule.assertMatchesGolden("explore-fa-411-light", darkTheme = false) { Explore() }

    @Test
    @Config(sdk = [34], qualifiers = FA_393)
    fun exploreNarrow() = composeRule.assertMatchesGolden("explore-fa-393") { Explore() }

    /* ------------------------------------------------------------------ ideas */

    /**
     * Both faces, with the content the screens actually draw.
     *
     * A stub would render the switch and prove nothing: the fault this case exists for is a *child*
     * screen drawing its own heading under the switch that already named the page, and a stub has
     * no heading to draw.
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun ideasSignals() = composeRule.assertMatchesGolden("ideas-signals-fa-411") {
        Ideas(IdeasFace.SIGNALS)
    }

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun ideasCommunity() = composeRule.assertMatchesGolden("ideas-community-fa-411") {
        Ideas(IdeasFace.COMMUNITY)
    }

    /* ------------------------------------------------------------------ menu */

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun menuDark() = composeRule.assertMatchesGolden("menu-fa-411-dark") { Menu() }

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun menuLight() =
        composeRule.assertMatchesGolden("menu-fa-411-light", darkTheme = false) { Menu() }

    /* ------------------------------------------------------------------ the bar */

    /**
     * Every state of the bar at once, which is the only way to see the selection treatment.
     *
     * One capture per tab would be five nearly identical frames; stacked, the difference between
     * the plate under «دیده‌بان» and the plate under «منو» is on one screen and a diff points
     * straight at it.
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun bottomBarStates() = composeRule.assertMatchesGolden("bottom-bar-fa-411") { BarStates() }

    @Test
    @Config(sdk = [34], qualifiers = FA_393)
    fun bottomBarNarrow() = composeRule.assertMatchesGolden("bottom-bar-fa-393") { BarStates() }

    /* ------------------------------------------------------------------ the chart */

    /**
     * A sanity frame, and deliberately only one.
     *
     * The chart's geometry and palette are settled and out of scope for a chrome pass; what this
     * guards is that a change to a shared token — a colour, a spacing step, the row that the
     * watchlist strip shares — did not reach into the plot.
     */
    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun chartSanity() = composeRule.assertMatchesGolden("chart-fa-411") {
        ChartScreen(controller = ScreenshotFixtures.chartController(scope))
    }

    /* ------------------------------------------------------------------ the fixtures */

    /**
     * The watchlist in its **steady state**, which is the one worth committing pixels for.
     *
     * The teaching banner is marked dismissed. It is a one-visit object — read once, closed, never
     * seen again — and a golden that carries it would be guarding ninety points of chrome that no
     * reader past their first day has on screen, while guarding nothing about the fold that
     * everybody actually sees.
     */
    @Composable
    private fun Watchlist() {
        val store = WatchlistStore(FakeScreenshotPreferences())
        runBlocking {
            listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XAUUSD").forEach {
                store.add(Watchlist.DEFAULT_LIST_ID, it)
            }
        }
        Taught {
            WatchlistScreen(
                controller = MarketSearchController(ScreenshotFixtures.searchCatalog(), scope)
                    .also { it.start() },
                store = store,
                sparklines = ScreenshotFixtures.sparklineStore(scope),
                onOpenSymbol = {},
                onOpenSearch = {},
            )
        }
    }

    @Composable
    private fun Explore() {

        val intel = MarketIntelController(FakeMarketIntelGateway(), scope).also { it.refresh() }
        ExploreScreen(
            controller = MarketSearchController(ScreenshotFixtures.searchCatalog(), scope)
                .also { it.start() },
            intel = intel,
            sparklines = ScreenshotFixtures.sparklineStore(scope),
            onOpenSymbol = {},
            onOpenNews = {},
            onOpenCalendar = {},
            onOpenHeatmap = {},
            onOpenSearch = {},
            onOpenMarkets = {},
        )
    }

    @Composable
    private fun Ideas(initial: IdeasFace) {
        val signals = SignalController(FakeSignalGateway(), scope)
        val community = CommunityController(FakeCommunityGateway(), FakeCommunityIdentity(), scope)
        Taught {
        IdeasScreen(
            initial = initial,
            signals = {
                SignalsScreen(
                    controller = signals,
                    onOpenSignal = {},
                    platform = MarketPlatform.TRADEYAR,
                    embedded = true,
                )
            },
            community = {
                CommunityScreen(controller = community, onOpenThread = {}, embedded = true)
            },
        )
        }
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

    /** Every teaching banner already read, which is where a reader spends their life. */
    @Composable
    private fun Taught(content: @Composable () -> Unit) {
        CompositionLocalProvider(
            LocalTeachingDismissals provides AllTeachingDismissed,
            content = content,
        )
    }

    @Composable
    private fun BarStates() {
        Column(
            modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
        ) {
            AppDestination.entries.forEach { destination ->
                Text(
                    text = destination.route,
                    color = CoineProColors.TextMuted,
                    modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
                )
                CoineProBottomBar(
                    currentRoute = destination.route,
                    onSelect = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    private companion object {
        /** The design system's reference width, in the app's own language and direction. */
        const val FA_411 = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"

        /** A Pixel, which is narrower than the reference and is what most readers hold. */
        const val FA_393 = "fa-rIR-ldrtl-w393dp-h914dp-xxhdpi"

        /** The mirrored layout. */
        const val EN_411 = "en-rUS-ldltr-w411dp-h914dp-xxhdpi"
    }
}


/** Every banner read and closed — the state a screen spends almost all of its life in. */
internal object AllTeachingDismissed : TeachingDismissals {
    override val dismissed: Set<String> = ALL_TEACHING_KEYS
    override fun dismiss(key: String) = Unit
    override fun restore(key: String) = Unit
}

private val ALL_TEACHING_KEYS: Set<String> =
    com.coinepro.core.designsystem.TeachingSurface.entries.map { it.key }.toSet()
