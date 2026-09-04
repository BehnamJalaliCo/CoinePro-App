package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.navigation.AppDestination
import kotlin.math.abs
import com.coinepro.feature.search.WatchlistScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The two numbers the owner set, asserted rather than eyeballed — and asserted twice.
 *
 * ### Why a test and not a screenshot
 *
 * Both of these are about how much of the glass the app spends before it shows the reader anything.
 * A golden catches a change to them, but only by failing on a picture — and a picture does not say
 * which number moved or by how much. These say it in points, and they fail the day somebody adds
 * one more row of chrome for a good reason, which is exactly the day it needs saying.
 *
 * ### Two layers, because a budget is not a target
 *
 * The first version of this file asserted only `chrome <= 125dp`. That is a *ceiling*, and a
 * ceiling is silent about the eight points between the number the layout was designed to hit and
 * the number it is allowed to reach. A layout can drift from 117 to 124 without one test going red,
 * and then the design has quietly changed and nobody decided to change it.
 *
 * So each metric is held two ways:
 *
 * * **Layer 1 — the exact target.** What the layout is supposed to measure, with only enough slack
 *   for rounding: ±1 point on the watchlist's chrome, ±0.5 on the bar. A failure here is a *design
 *   change*, and the right response is to look at what moved and either revert it or move the
 *   target on purpose.
 * * **Layer 2 — the hard budget.** The ceiling, kept as it was. A failure here is not a design
 *   change, it is a regression, and there is nothing to discuss.
 *
 * ### Three configurations, and the third one is the point
 *
 * Persian at both widths, because 411 is the design reference and 393 is the phone most readers
 * hold. And **English at 411**, because the whole layout mirrors and a number that holds in one
 * direction is not evidence about the other: a longer word in a control row, a differently-metricked
 * font, a chip that wraps — any of them moves the fold, and none of them is visible from a Persian
 * measurement. If English ever needs a *different* target, the layout gets fixed first; a
 * locale-specific number is a last resort and would be written here with its reason.
 *
 * ### The chrome is measured with the teaching banner already read
 *
 * That banner is a one-visit object: shown once, closed, never seen again. Measuring the fold with
 * it up would be measuring a screen that almost nobody has — and the point of the budget is what a
 * reader sees on the four hundredth visit, not the first.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FoldMetricsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    /* ------------------------------------------------------------------ the watchlist's fold */

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun watchlistChromeAt411() = assertWatchlistChrome("fa 411")

    /** And on the phone most readers hold, which is narrower and where the row is tightest. */
    @Test
    @Config(sdk = [34], qualifiers = FA_393)
    fun watchlistChromeAt393() = assertWatchlistChrome("fa 393")

    /** The mirrored layout, which is a different layout and gets the same target. */
    @Test
    @Config(sdk = [34], qualifiers = EN_411)
    fun watchlistChromeInEnglish() = assertWatchlistChrome("en 411")

    /* ------------------------------------------------------------------ the bar */

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun bottomBarAt411() = assertBarHeight("fa 411")

    @Test
    @Config(sdk = [34], qualifiers = FA_393)
    fun bottomBarAt393() = assertBarHeight("fa 393")

    @Test
    @Config(sdk = [34], qualifiers = EN_411)
    fun bottomBarInEnglish() = assertBarHeight("en 411")

    /* ------------------------------------------------------------------ the measurements */

    private fun assertWatchlistChrome(label: String) {
        val store = WatchlistStore(FakeScreenshotPreferences())
        runBlocking {
            listOf("BTCUSDT", "ETHUSDT", "SOLUSDT").forEach {
                store.add(Watchlist.DEFAULT_LIST_ID, it)
            }
        }
        composeRule.setContent {
            CoineProTheme(darkTheme = true) {
                CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                    WatchlistScreen(
                        controller = MarketSearchController(ScreenshotFixtures.searchCatalog(), scope)
                            .also { it.start() },
                        store = store,
                        sparklines = ScreenshotFixtures.sparklineStore(scope),
                        onOpenSymbol = {},
                        onOpenSearch = {},
                        modifier = Modifier.testTag(PAGE),
                    )
                }
            }
        }
        composeRule.waitForIdle()

        val pageTop = composeRule.onNodeWithTag(PAGE).fetchSemanticsNode().positionInRoot.y
        // The **row**, not the words in it: the merged node that carries the click, which is the
        // row's own box. Measuring to the ticker's baseline would count the row's top padding as
        // chrome, and the budget is about where the list starts.
        val rowTop = composeRule
            .onAllNodes(hasClickAction() and hasText("BTCUSDT", substring = true))
            .onFirst()
            .fetchSemanticsNode()
            .positionInRoot
            .y
        val chrome = with(composeRule.density) { (rowTop - pageTop).toDp() }
        println("watchlist[$label] pre-row chrome: $chrome (target $CHROME_TARGET, budget $CHROME_BUDGET)")

        assertExact(label, "watchlist pre-row chrome", chrome, CHROME_TARGET, CHROME_DRIFT_DP)
        assertBudget(label, "watchlist pre-row chrome", chrome, CHROME_BUDGET)
    }

    /**
     * The bar, measured as three numbers rather than one.
     *
     * The budget is written against **app-owned chrome** — the hairline that closes the page, plus
     * the row of tabs. What a gesture-navigation phone adds beneath is the system's: it is 0 here,
     * about 24 points on a three-button phone and about 16 on a gesture one, it is not this app's
     * to spend, and folding it into the total would make one number that means a different thing on
     * every device. So the inset is read separately, printed, and asserted *not* to be inside the
     * app's own figure.
     */
    private fun assertBarHeight(label: String) {
        var inset = 0.dp
        composeRule.setContent {
            CoineProTheme(darkTheme = true) {
                val density = LocalDensity.current
                inset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
                CoineProBottomBar(
                    currentRoute = AppDestination.WATCHLIST.route,
                    onSelect = {},
                    modifier = Modifier.fillMaxWidth().testTag(AppChromeTestTags.BOTTOM_BAR),
                )
            }
        }
        composeRule.waitForIdle()

        val total = height(AppChromeTestTags.BOTTOM_BAR)
        val divider = height(AppChromeTestTags.BOTTOM_BAR_DIVIDER)
        // The tab row pads itself with the navigation inset before it takes its own height, so what
        // it reports is content plus inset. The app owns the content.
        val content = height(AppChromeTestTags.BOTTOM_BAR_CONTENT) - inset
        val appChrome = divider + content

        println(
            "bottom bar[$label] app chrome=$appChrome (divider=$divider + content=$content) | " +
                "system navigation inset=$inset | total=$total " +
                "(target $BAR_TARGET, budget $BAR_BUDGET)",
        )

        assertExact(label, "bottom bar divider", divider, DIVIDER, DIVIDER_DRIFT_DP)
        assertExact(label, "bottom bar content", content, BAR_CONTENT, BAR_DRIFT_DP)
        assertExact(label, "bottom bar app chrome", appChrome, BAR_TARGET, BAR_DRIFT_DP)
        assertBudget(label, "bottom bar app chrome", appChrome, BAR_BUDGET)
        assertTrue(
            "[$label] the bar reports $total in total but its app-owned chrome is $appChrome and " +
                "the system asked for $inset — those should add up, and if they do not then " +
                "something else is padding the bar.",
            abs((total - appChrome - inset).value) <= DIVIDER_DRIFT_DP,
        )
    }

    /** Layer 1: what the layout is supposed to measure. A failure here is a design change. */
    private fun assertExact(label: String, what: String, actual: Dp, target: Dp, drift: Float) {
        val delta = abs((actual - target).value)
        assertTrue(
            "[$label] $what is $actual and the design target is $target — a ${delta}dp " +
                "difference, and only ${drift}dp of measurement drift is allowed. If this moved " +
                "on purpose, move the target on purpose too and say why here.",
            delta <= drift,
        )
    }

    /** Layer 2: the ceiling. A failure here is not a design change, it is a regression. */
    private fun assertBudget(label: String, what: String, actual: Dp, budget: Dp) {
        assertTrue("[$label] $what is $actual; the budget is $budget.", actual <= budget)
    }

    private fun height(tag: String): Dp = with(composeRule.density) {
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().size.height.toDp()
    }

    private companion object {
        const val PAGE = "watchlist-page"

        const val FA_411 = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"
        const val FA_393 = "fa-rIR-ldrtl-w393dp-h914dp-xxhdpi"
        const val EN_411 = "en-rUS-ldltr-w411dp-h914dp-xxhdpi"

        /** What the watchlist's chrome measures today, and is meant to keep measuring. */
        val CHROME_TARGET = 117.dp
        const val CHROME_DRIFT_DP = 1.0f

        /** «Watchlist pre-row chrome <= 125dp», from the owner's own list. */
        val CHROME_BUDGET = 125.dp

        /** The hairline above the bar, and the row of tabs under it. */
        val DIVIDER = 1.dp
        val BAR_CONTENT = 64.dp

        /** The two of them: the app's whole share of the foot of the screen. */
        val BAR_TARGET = DIVIDER + BAR_CONTENT
        const val BAR_DRIFT_DP = 0.5f

        /** A hairline is a hairline. Anything else is a mistake, not a drift. */
        const val DIVIDER_DRIFT_DP = 0.1f

        /** «Bottom Bar <= 70dp», likewise — app-owned chrome, insets excluded. */
        val BAR_BUDGET = 70.dp
    }
}
