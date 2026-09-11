package com.coinepro.app

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import com.coinepro.core.datastore.Watchlist
import com.coinepro.core.datastore.WatchlistStore
import com.coinepro.core.designsystem.CoineProTeachingHost
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.designsystem.TeachingDismissals
import com.coinepro.core.designsystem.TeachingSurface
import com.coinepro.core.marketdata.MarketSearchController
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

/**
 * The explainer cards are gone from the pages, and what replaced them is a coach-mark (run F).
 *
 * The owner's reading of the shipped frames — Home, Watchlist and Chart each opening with a card
 * explaining what the screen is — and the rule behind it: «TradingView/Binance هرگز صفحه را برای
 * کاربر توضیح نمی‌دهند». The sentence itself is kept; where it is drawn is the whole change.
 *
 * So this asserts two things per screen, which together are the acceptance:
 *
 *  * **After the first run** — the surface dismissed, which is every run but one — neither the
 *    lead nor the pitfall is anywhere on the screen. Not smaller, not folded: absent.
 *  * **On the first run** the sentence is there and it is *floating*: its top edge is in the
 *    bottom third of the window, which is where the coach-mark sits and is nowhere a card in a
 *    page's layout flow could be.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
class ExplainerRemovedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    /** Everything dismissed, which is what every run after the first looks like. */
    private object AllDismissed : TeachingDismissals {
        override val dismissed: Set<String> get() = TeachingSurface.entries.map { it.key }.toSet()
        override fun dismiss(key: String) = Unit
        override fun restore(key: String) = Unit
    }

    /** Nothing dismissed: a freshly installed app. */
    private object NoneDismissed : TeachingDismissals {
        override val dismissed: Set<String> get() = emptySet()
        override fun dismiss(key: String) = Unit
        override fun restore(key: String) = Unit
    }

    @Test
    fun homeTeachesNothingAfterTheFirstRun() = assertSilent(TeachingSurface.HOME) { Home() }

    @Test
    fun watchlistTeachesNothingAfterTheFirstRun() = assertSilent(TeachingSurface.WATCHLIST) { WatchlistPage() }

    @Test
    fun chartTeachesNothingAfterTheFirstRun() = assertSilent(TeachingSurface.CHART) { Chart() }

    @Test
    fun theChartsCoachMarkFloatsAtTheFootOnTheFirstRun() {
        render(NoneDismissed) { Chart() }
        val lead = composeRule.activity.getString(TeachingSurface.CHART.lead)
        composeRule.onNodeWithText(lead, substring = true).assertIsDisplayed()
        val bounds = composeRule.onNodeWithText(lead, substring = true).fetchSemanticsNode().boundsInRoot
        val height = composeRule.activity.window.decorView.height.toFloat()
        assertTrue(
            "the coach-mark must float at the foot of the screen, not sit in the page: " +
                "top ${bounds.top} of $height",
            height > 0f && bounds.top > height * 2f / 3f,
        )
    }

    private fun assertSilent(surface: TeachingSurface, content: @Composable () -> Unit) {
        render(AllDismissed, content)
        val lead = composeRule.activity.getString(surface.lead)
        assertTrue(
            "«$lead» is still being drawn on this screen",
            composeRule.onAllNodesWithText(lead, substring = true).fetchSemanticsNodes().isEmpty(),
        )
        surface.pitfall?.let { pitfall ->
            val text = composeRule.activity.getString(pitfall)
            assertTrue(
                "«$text» is still being drawn on this screen",
                composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isEmpty(),
            )
        }
        // And the way back a dismissed strip used to leave behind is gone with it: the page is the
        // page. The «؟» in a screen's header is the way back where a screen has a header.
        val restore = composeRule.activity.getString(com.coinepro.core.designsystem.R.string.teaching_what_is_this)
        assertTrue(
            "the «$restore» link is still taking a row of the page",
            composeRule.onAllNodesWithText(restore, substring = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    private fun render(dismissals: TeachingDismissals, content: @Composable () -> Unit) {
        composeRule.setContent {
            CoineProTheme(darkTheme = true) {
                CompositionLocalProvider(LocalTeachingDismissals provides dismissals) {
                    CoineProTeachingHost {
                        Surface(modifier = Modifier.fillMaxSize()) { content() }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
    }

    @Composable
    private fun Home() {
        HomeScreen(
            state = ScreenshotFixtures.marketState(),
            onRetry = {},
            displayName = "بهنام",
            portfolio = ScreenshotFixtures.homePortfolio,
        )
    }

    @Composable
    private fun WatchlistPage() {
        val store = remember { WatchlistStore(FakeScreenshotPreferences()) }
        runBlocking { listOf("BTCUSDT", "XAUUSD").forEach { store.add(Watchlist.DEFAULT_LIST_ID, it) } }
        WatchlistScreen(
            controller = remember { MarketSearchController(ScreenshotFixtures.searchCatalog(), scope).also { it.start() } },
            store = store,
            sparklines = remember { ScreenshotFixtures.sparklineStore(scope) },
            onOpenSymbol = {},
            onOpenSearch = {},
        )
    }

    @Composable
    private fun Chart() {
        ChartScreen(controller = remember { ScreenshotFixtures.chartController(scope) })
    }
}
