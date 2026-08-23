package com.coinepro.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.coinepro.core.designsystem.CoineProTheme
import com.coinepro.core.marketdata.MarketConnectionState
import com.coinepro.core.marketdata.MarketDataOrigin
import com.coinepro.core.marketdata.MarketDataState
import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.QuoteSource
import com.coinepro.feature.home.HomeScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

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

        composeRule.onNodeWithText("Cached snapshot · offline").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Gold, XAUUSD, stale, price 2350.25, Finnhub, Metal")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Network refresh failed. The stored snapshot stays visible and remains marked stale.")
            .assertIsDisplayed()
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

        composeRule
            .onNodeWithText("\u20662350.25\u2069", useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Gold, XAUUSD, stale, price 2350.25, Finnhub, Metal")
            .assertIsDisplayed()
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

        composeRule.onNodeWithText("No market data available yet.").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertHasClickAction().performClick()
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

        composeRule.onNodeWithText("Realtime connected").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Bitcoin, BTCUSDT, live, price 64250.00, LBank, Crypto")
            .assertIsDisplayed()
    }

    private fun goldQuote() = MarketQuote(
        instrument = Instrument("XAUUSD", "Gold", MarketType.FOREX),
        price = 2350.25,
        timestampEpochMillis = 1_000L,
        source = QuoteSource.FINNHUB,
        isStale = true,
    )
}
