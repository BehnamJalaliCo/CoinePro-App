package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.marketdata.MarketCatalog
import com.coinepro.core.marketdata.MarketCatalogGateway
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.feature.explore.ExploreScreen
import com.coinepro.feature.explore.ExploreTestTags
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The one thing on Explore that a screenshot of the finished screen cannot catch: the page moving
 * under the reader when the data arrives.
 *
 * ### The fault
 *
 * The loading placeholder was pinned at 116 points and the tile that replaces it had no height at
 * all — it measured its own content, which after the tile was made compact came to about ninety.
 * So the catalogue landing shortened the strip by roughly twenty-five points and pulled the whole
 * page up with it: the chips, the stories, everything. The placeholder exists *precisely* to stop
 * that, and it was the thing causing it.
 *
 * A steady-state golden cannot see this. Both frames it might capture are individually correct; the
 * fault is the difference between them. So this renders **one** composition and walks it through the
 * real transition — a catalogue gateway that is held open, then released — and measures before and
 * after.
 *
 * The assertion a reader actually feels is on the **story row's** position, not on the strip: what
 * somebody experiences is the sentence they are reading jumping, and that is what is held to one
 * physical pixel.
 *
 * ### Font scale
 *
 * Pinned at 1.0, which is where the geometry contract holds. Above it the tile's content grows past
 * a fixed height and clips: that is the trade a fixed-height card makes, it is why the strip is a
 * taste of the catalogue with the full list one tap away, and it is not what this test is about.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExploreGeometryTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun loadingAndLoadedAgreeDark411() = assertNoJump("fa 411 dark", darkTheme = true)

    @Test
    @Config(sdk = [34], qualifiers = FA_411)
    fun loadingAndLoadedAgreeLight411() = assertNoJump("fa 411 light", darkTheme = false)

    @Test
    @Config(sdk = [34], qualifiers = FA_393)
    fun loadingAndLoadedAgreeDark393() = assertNoJump("fa 393 dark", darkTheme = true)

    @Test
    @Config(sdk = [34], qualifiers = FA_393)
    fun loadingAndLoadedAgreeLight393() = assertNoJump("fa 393 light", darkTheme = false)

    private fun assertNoJump(label: String, darkTheme: Boolean) {
        val gate = CompletableDeferred<Unit>()
        val markets = MarketSearchController(HeldCatalog(gate), scope)
        val intel = MarketIntelController(FakeMarketIntelGateway(), scope).also { it.refresh() }

        composeRule.setContent {
            CoineProTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
                    ExploreScreen(
                        controller = markets,
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
            }
        }
        composeRule.waitForIdle()

        val loadingStrip = height(ExploreTestTags.LOADING_STRIP)
        val loadingChips = height(ExploreTestTags.CATEGORY_ROW)
        val loadingStory = top(ExploreTestTags.FIRST_STORY)

        // The catalogue arrives, in the same composition the reader is looking at.
        gate.complete(Unit)
        composeRule.waitForIdle()

        val loadedCard = height(ExploreTestTags.FIRST_MARKET_CARD)
        val loadedChips = height(ExploreTestTags.CATEGORY_ROW)
        val loadedStory = top(ExploreTestTags.FIRST_STORY)

        val heightDelta = abs((loadingStrip - loadedCard).value)
        val storyDelta = abs((loadingStory - loadedStory).value)
        val storyDeltaPx = storyDelta * composeRule.density.density

        println(
            "explore[$label] chips loading=$loadingChips loaded=$loadedChips | " +
                "strip loading=$loadingStrip loaded=$loadedCard Δ=${heightDelta}dp | " +
                "first story loading=$loadingStory loaded=$loadedStory " +
                "Δ=${storyDelta}dp (${storyDeltaPx}px)",
        )
        assertTrue(
            "[$label] the loading strip is $loadingStrip and the tile that replaces it is " +
                "$loadedCard — a ${heightDelta}dp step, and the placeholder exists to have none.",
            heightDelta <= HEIGHT_SLACK_DP,
        )
        assertTrue(
            "[$label] the first story moves ${storyDeltaPx}px when the catalogue lands; the " +
                "budget is $STORY_SLACK_PX physical pixel.",
            storyDeltaPx <= STORY_SLACK_PX,
        )
    }

    private fun height(tag: String): Dp = with(composeRule.density) {
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().size.height.toDp()
    }

    private fun top(tag: String): Dp = with(composeRule.density) {
        composeRule.onNodeWithTag(tag).fetchSemanticsNode().positionInRoot.y.toDp()
    }

    /**
     * The catalogue, held until the test lets it through.
     *
     * `ExploreScreen` starts its own controller, so a loading state cannot be arranged by simply
     * not starting one — the screen would start it a frame later. A gateway that suspends until
     * released is the honest way to hold the screen in the state a reader sees on a slow
     * connection, and releasing it is the transition itself rather than a second render.
     */
    private class HeldCatalog(private val gate: CompletableDeferred<Unit>) : MarketCatalogGateway {
        private val real = ScreenshotFixtures.searchCatalog()

        override suspend fun load(): MarketCatalog {
            gate.await()
            return real.load()
        }
    }

    private companion object {
        const val FA_411 = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi"
        const val FA_393 = "fa-rIR-ldrtl-w393dp-h914dp-xxhdpi"

        /** «abs(loadedCardHeight - loadingCardHeight) <= 0.5dp», from the owner's own list. */
        const val HEIGHT_SLACK_DP = 0.5f

        /** «first story Y drift <= 1 physical pixel», likewise. */
        const val STORY_SLACK_PX = 1.0f
    }
}
