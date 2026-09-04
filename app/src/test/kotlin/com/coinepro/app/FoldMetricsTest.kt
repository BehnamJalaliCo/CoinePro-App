package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.navigation.AppDestination
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
 * The two numbers the owner set, asserted rather than eyeballed.
 *
 * ### Why a test and not a screenshot
 *
 * Both of these are *budgets*: how much of the glass the app spends before it shows the reader
 * anything. A golden catches a change to them, but only by failing on a picture — and a picture
 * does not say which number moved or by how much. These say it in points, and they fail the day
 * somebody adds one more row of chrome for a good reason, which is exactly the day it needs saying.
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

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun watchlistChromeFitsTheBudgetAt411() = assertWatchlistChrome()

    /** And on the phone most readers hold, which is narrower and where the row is tightest. */
    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w393dp-h914dp-xxhdpi")
    fun watchlistChromeFitsTheBudgetAt393() = assertWatchlistChrome()

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun bottomBarFitsTheBudgetAt411() = assertBarHeight()

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w393dp-h914dp-xxhdpi")
    fun bottomBarFitsTheBudgetAt393() = assertBarHeight()

    private fun assertWatchlistChrome() {
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
        println("watchlist pre-row chrome: $chrome")
        assertTrue(
            "The watchlist spends $chrome above its first row; the budget is $CHROME_BUDGET.",
            chrome <= CHROME_BUDGET,
        )
    }

    private fun assertBarHeight() {
        composeRule.setContent {
            CoineProTheme(darkTheme = true) {
                CoineProBottomBar(
                    currentRoute = AppDestination.WATCHLIST.route,
                    onSelect = {},
                    modifier = Modifier.fillMaxWidth().testTag(BAR),
                )
            }
        }
        composeRule.waitForIdle()
        val node = composeRule.onNodeWithTag(BAR).fetchSemanticsNode()
        val height = with(composeRule.density) { node.size.height.toDp() }
        println("bottom bar height: $height")
        assertTrue(
            "The bottom bar is $height tall; the budget is $BAR_BUDGET.",
            height <= BAR_BUDGET,
        )
    }

    private companion object {
        const val PAGE = "watchlist-page"
        const val BAR = "bottom-bar"

        /** «Watchlist pre-row chrome <= 125dp», from the owner's own list. */
        val CHROME_BUDGET = 125.dp

        /** «Bottom Bar <= 70dp», likewise. The insets are zero in this configuration. */
        val BAR_BUDGET = 70.dp
    }
}
