package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.coinepro.core.common.ReturnLoop
import com.coinepro.core.common.SymbolMove
import com.coinepro.core.designsystem.CONTENT_MAX_WIDTH
import com.coinepro.core.designsystem.CONTENT_MAX_WIDTH_WIDE
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.designsystem.LocalTeachingDismissals
import com.coinepro.core.model.MarketPlatform
import com.coinepro.feature.home.HOME_LIST
import com.coinepro.feature.home.HomeScreen
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A column of cards stops growing before the glass does (run Σ, S8).
 *
 * Home is a list of cards, and on a 1973 dp panel every one of them was the full width of the
 * device: a row with its symbol at one edge and its percentage at the other, and a «do it» button
 * the width of a twelve-inch screen. It had been that way since Home existed; the «since your last
 * visit» card, with three symbols and three figures in a row, is simply what made it impossible to
 * keep looking past.
 *
 * The cap is [CONTENT_MAX_WIDTH] and this is the gate on it, because a picture of a wide screen is
 * exactly the kind of evidence somebody glances at and accepts. Two assertions and they are
 * opposites: the card is no wider than the cap on the big panel, and the phone is untouched — a
 * «fix» that narrowed the phone would be a worse bug than the one being fixed.
 */
@RunWith(RobolectricTestRunner::class)
class ContentWidthTest {

    private companion object {
        const val SINCE = "وقتی نبودید"
        /** The challenge card's own sentence: «امروز» alone also matches the balance's day chip. */
        val TODAY = ReturnLoop.challengeFor(19_980L).title
    }

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun compose() {
        rule.setContent {
            CoineProTheme {
                CompositionLocalProvider(LocalTeachingDismissals provides AllTeachingDismissed) {
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
                        watchlist = listOf("BTCUSDT", "XAUUSD"),
                        challenge = ReturnLoop.challengeFor(19_980L),
                        since = ReturnLoop.sinceLastVisit(
                            lastVisitEpochMillis = 1_726_000_000_000L - 14 * 3_600_000L,
                            nowEpochMillis = 1_726_000_000_000L,
                            movers = listOf(SymbolMove("BTCUSDT", 0.058), SymbolMove("XAUUSD", -0.031)),
                        ),
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-sw1232dp-w1973dp-h1232dp-hdpi")
    fun `on the widest panel the content stops at the wide cap`() {
        // Two columns, capped as a pair — not one column with two empty thirds beside it, which is
        // what 4.84.0's single cap produced and what the owner's review called out (run Σ-FIX 7).
        compose()
        val width = rule.onNodeWithTag(HOME_LIST).getUnclippedBoundsInRoot().width
        assertTrue("the content is $width wide on a 1973dp panel", width <= CONTENT_MAX_WIDTH_WIDE)
        assertTrue("the content is only $width wide — one column again", width > CONTENT_MAX_WIDTH)
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-sw800dp-w1280dp-h800dp-xhdpi")
    fun `on a tablet the two cards share a row`() {
        // «since you were away» and «today» are two short cards answering one question. Stacked on
        // a twelve-inch panel they spend a third of the fold saying it twice as tall.
        compose()
        val since = rule.onNodeWithText(SINCE, substring = true).getUnclippedBoundsInRoot()
        val today = rule.onNodeWithText(TODAY, substring = true).getUnclippedBoundsInRoot()
        assertTrue(
            "the cards are stacked: since at ${since.top}, today at ${today.top}",
            kotlin.math.abs((since.top - today.top).value) < 24f,
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun `on a phone the two cards stay stacked`() {
        // The other half, and the one that would look fine in a diff: two 180 dp columns at 411 dp
        // is two cards nobody can read.
        compose()
        val since = rule.onNodeWithText(SINCE, substring = true).getUnclippedBoundsInRoot()
        val today = rule.onNodeWithText(TODAY, substring = true).getUnclippedBoundsInRoot()
        assertTrue("the cards share a row on a phone", today.top > since.top)
    }

    @Test
    @Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
    fun `on a phone nothing is given away`() {
        compose()
        val width = rule.onNodeWithTag(HOME_LIST).getUnclippedBoundsInRoot().width
        assertTrue("the column is only $width wide on a phone", width >= 400.dp)
    }
}
