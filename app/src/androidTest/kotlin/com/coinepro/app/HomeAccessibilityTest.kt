package com.coinepro.app

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.marketdata.MarketConnectionState
import com.coinepro.core.marketdata.MarketDataOrigin
import com.coinepro.core.marketdata.MarketDataState
import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.QuoteSource
import com.coinepro.feature.home.HomeScreen
import com.coinepro.feature.home.R as HomeR
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home, on a real device: what a market row says out loud, and that a stale price says so.
 *
 * ### What these used to assert, and why none of it held
 *
 * A hand-written content description per row — `"Gold, XAUUSD, stale, price 2350.25, Finnhub,
 * Metal"` — composed in the screen and checked here character for character. It does not exist any
 * more, and its removal was the improvement: `CoineProMarketRow` now merges its own descendants, so
 * a reader hears the row's **real** children in the order they are drawn, and there is no second
 * description to be kept in step with the first. A screen reader on the old row could be told a
 * price the row had stopped showing.
 *
 * So these assert the merged node instead: one node carrying the instrument's name, its ticker and
 * its price, which is the row as it is actually announced. The prose around it — the connection
 * line, the recovery note, the empty state — is read from `strings.xml` rather than typed out, for
 * the reason the safety screen's tests give: a copy edit is not a regression, and a suite that
 * fails on wording teaches its reader to ignore it.
 *
 * ### Scrolling is explicit
 *
 * Home is a `LazyColumn`, so the market card is not composed until it is scrolled to and
 * `performScrollTo` on an uncomposed node cannot find it. `performScrollToNode` is the form that
 * works on a lazy list, and every assertion below the fold goes through [reach].
 */
@RunWith(AndroidJUnit4::class)
class HomeAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun cachedQuoteIsAnnouncedAsStaleWithFinancialValueInRtl() {
        val quote = goldQuote()
        val state = MarketDataState(
            connection = MarketConnectionState.OFFLINE,
            quotes = mapOf(quote.instrument.symbol to quote),
            lastError = "offline",
            origin = MarketDataOrigin.CACHE,
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                CoineProTheme {
                    HomeScreen(state = state, onRetry = {})
                }
            }
        }

        composeRule.reach(hasText(context.getString(HomeR.string.home_status_cached)))
            .assertIsDisplayed()
        composeRule.reach(hasText(context.getString(HomeR.string.home_note_refresh_failed)))
            .assertIsDisplayed()
        // The row as one announcement: the instrument, its ticker, its price — and the word that
        // says the price is old. A stale quote drawn like a live one is the failure that costs
        // money, so it is the assertion this test exists for.
        composeRule.reach(
            hasText("Gold") and
                hasText(BidiText.isolateLtr("XAUUSD")) and
                hasText(MarketNumberFormatter.price(2350.25, 2)) and
                hasText(context.getString(HomeR.string.home_quote_stale)),
        ).assertIsDisplayed()
    }

    @Test
    fun largeFontRtlKeepsCoreFinancialQuoteVisibleAndReadable() {
        val quote = goldQuote()
        val state = MarketDataState(
            connection = MarketConnectionState.OFFLINE,
            quotes = mapOf(quote.instrument.symbol to quote),
            origin = MarketDataOrigin.CACHE,
        )

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                CoineProTheme {
                    HomeScreen(state = state, onRetry = {})
                }
            }
        }

        // At twice the type size, in a right-to-left page: the figure is still on screen and still
        // whole. This is the case a fixed-height row loses first.
        composeRule.reach(
            hasText("Gold") and hasText(MarketNumberFormatter.price(2350.25, 2)),
        ).assertIsDisplayed()
    }

    @Test
    fun offlineEmptyStateExposesRetryAction() {
        var retries = 0
        val state = MarketDataState(connection = MarketConnectionState.OFFLINE)

        composeRule.setContent {
            CoineProTheme {
                HomeScreen(state = state, onRetry = { retries += 1 })
            }
        }

        composeRule.reach(hasText(context.getString(HomeR.string.home_market_empty)))
            .assertIsDisplayed()
        composeRule.reach(hasText(context.getString(HomeR.string.home_retry)))
            .assertHasClickAction()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun networkQuoteIsAnnouncedAsLiveOnlyWhenStateSaysLive() {
        val quote = MarketQuote(
            instrument = Instrument("BTCUSDT", "Bitcoin", MarketType.CRYPTO),
            price = 64250.0,
            timestampEpochMillis = 2_000L,
            source = QuoteSource.LBANK,
            isStale = false,
        )
        val state = MarketDataState(
            connection = MarketConnectionState.LIVE,
            quotes = mapOf(quote.instrument.symbol to quote),
            origin = MarketDataOrigin.NETWORK,
        )

        composeRule.setContent {
            CoineProTheme {
                HomeScreen(state = state, onRetry = {})
            }
        }

        composeRule.reach(hasText(context.getString(HomeR.string.home_status_live)))
            .assertIsDisplayed()
        composeRule.reach(
            hasText("Bitcoin") and
                hasText(BidiText.isolateLtr("BTCUSDT")) and
                hasText(MarketNumberFormatter.price(64250.0, 2)),
        ).assertIsDisplayed()
        // Live, so no stale marker anywhere on the page.
        composeRule
            .onAllNodes(hasText(context.getString(HomeR.string.home_quote_stale)))
            .assertCountEquals(0)
    }

    private fun goldQuote() = MarketQuote(
        instrument = Instrument("XAUUSD", "Gold", MarketType.FOREX),
        price = 2350.25,
        timestampEpochMillis = 1_000L,
        source = QuoteSource.FINNHUB,
        isStale = true,
    )
}

/**
 * Scroll the page until [matcher] is composed, then hand back the node.
 *
 * Home is a lazy list, so a node below the fold does not exist to be asserted on and
 * `performScrollTo` — which needs an already-composed node — fails with «Action performScrollTo()
 * failed» rather than with anything about the screen. That failure is what the previous version of
 * these tests reported, and it said nothing.
 */
internal fun ComposeContentTestRule.reach(matcher: SemanticsMatcher) =
    onAllNodes(hasScrollAction()).onFirst().performScrollToNode(matcher).let { onNode(matcher) }
