package com.coinepro.core.marketdata

import com.coinepro.core.model.MarketPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The separation between the two platforms, asserted rather than assumed.
 *
 * These are cheap tests for a failure that is expensive in the product: a metal appearing in a
 * crypto watchlist looks like working software right up until someone trades on it.
 */
class MarketDataSymbolsTest {

    @Test
    fun `crypto and forex share no symbol`() {
        val shared = MarketDataSymbols.crypto.intersect(MarketDataSymbols.forex.toSet())
        assertTrue("A symbol may belong to one platform only, found: $shared", shared.isEmpty())
    }

    @Test
    fun `every crypto symbol is quoted in a stablecoin`() {
        MarketDataSymbols.crypto.forEach { symbol ->
            assertTrue(
                "TradeYar quotes USDT pairs on LBank; $symbol is not one.",
                symbol.endsWith("USDT"),
            )
        }
    }

    @Test
    fun `no forex symbol is a stablecoin pair`() {
        MarketDataSymbols.forex.forEach { symbol ->
            assertTrue(
                "CoinePro-FX is quoted by Finnhub, not by a crypto venue; $symbol looks like one.",
                !symbol.endsWith("USDT"),
            )
        }
    }

    @Test
    fun `every platform has symbols`() {
        MarketPlatform.entries.forEach { platform ->
            assertTrue(
                "${platform.id} would open to an empty market.",
                MarketDataSymbols.forPlatform(platform).isNotEmpty(),
            )
        }
    }

    @Test
    fun `each platform resolves to its own list`() {
        assertEquals(MarketDataSymbols.crypto, MarketDataSymbols.forPlatform(MarketPlatform.TRADEYAR))
        assertEquals(MarketDataSymbols.forex, MarketDataSymbols.forPlatform(MarketPlatform.COINEPRO_FX))
    }
}
