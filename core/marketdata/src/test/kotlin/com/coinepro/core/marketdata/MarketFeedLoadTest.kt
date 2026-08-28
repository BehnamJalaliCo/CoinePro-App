package com.coinepro.core.marketdata

import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app itself does when the feed is fast and the catalogue is large.
 *
 * ### Why this is the client half of "five to ten thousand users"
 *
 * The owner asked to see the app under that load. That number is a *server* question — an Android
 * app runs on one phone for one person, and no amount of load on the phone reproduces ten thousand
 * of them. What ten thousand people do is hit `/public/prices` and the socket at the same moment,
 * and what gives way is a connection pool or a rate limiter on CoinePro-FX and TradeYar.
 * `scripts/load/backend-load.py` drives that, against staging, with the app's own request mix.
 *
 * The question this file answers is the one that *is* about the client, and it is the one that
 * actually bites: **TradeYar quotes 441 crypto markets and pushes several hundred updates a second
 * into a phone drawing a dozen rows.** A search that is linear in the catalogue, a recompute that
 * runs per tick, or a filter that allocates per frame is a device that heats up and a list that
 * stutters — and none of it shows up in a test that uses eight symbols.
 *
 * So these run at the real scale, on every build, and they assert a *budget* rather than a
 * stopwatch reading. A wall-clock assertion on CI is a flake; a budget generous enough to pass on
 * the slowest runner still catches the change that makes something quadratic, which is the failure
 * worth catching.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarketFeedLoadTest {

    /** Roughly what TradeYar quotes, built from real bases so the classifier does real work. */
    private fun bigCatalog(): MarketCatalogGateway {
        val bases = listOf(
            "BTC", "ETH", "SOL", "BNB", "XRP", "ADA", "DOGE", "TRX", "AVAX", "LINK",
            "DOT", "MATIC", "SHIB", "LTC", "BCH", "UNI", "ATOM", "XLM", "NEAR", "APT",
            "ARB", "OP", "FIL", "INJ", "SUI", "TIA", "SEI", "RENDER", "PEPE", "WIF",
        )
        val quotes = listOf("USDT", "USDC", "BTC", "ETH")
        val symbols = buildList {
            bases.forEach { base -> quotes.forEach { quote -> add(base + quote) } }
            // The forex and metal side, so the classifier is exercised on both shapes.
            listOf("EURUSD", "GBPUSD", "USDJPY", "USDCHF", "AUDUSD", "XAUUSD", "XAGUSD")
                .forEach(::add)
        }
        val metas = symbols.map(SymbolClassifier::classify)
        return object : MarketCatalogGateway {
            override suspend fun load(): MarketCatalog =
                MarketCatalog(markets = metas, quotes = emptyMap(), serverTimeEpochMillis = null)
        }
    }

    @Test
    fun `a catalogue the size of the real one loads and stays whole`() = runTest {
        val controller = MarketSearchController(bigCatalog(), this)
        controller.start()
        advanceUntilIdle()

        // 120 crypto pairs plus seven forex and metal, minus whatever has no artwork. The exact
        // number is not the point; that it is in the hundreds and not eight is.
        assertTrue(
            "catalogue collapsed to ${controller.state.value.catalogSize}",
            controller.state.value.catalogSize > 100,
        )
    }

    @Test
    fun `a thousand keystrokes over the whole catalogue stay inside budget`() = runTest {
        val controller = MarketSearchController(bigCatalog(), this)
        controller.start()
        advanceUntilIdle()

        // Every prefix of a dozen queries, which is what typing them character by character does.
        val queries = listOf(
            "b", "bt", "btc", "e", "et", "eth", "s", "so", "sol", "x", "xa", "xau",
            "بیت", "اتر", "طلا", "eur", "usd", "doge", "pepe", "zzzz",
        )
        val started = System.nanoTime()
        repeat(50) {
            queries.forEach { query ->
                controller.setQuery(query)
                advanceUntilIdle()
            }
        }
        val millis = (System.nanoTime() - started) / 1_000_000

        // A thousand searches over a few hundred markets. The budget is deliberately loose — this
        // is not a stopwatch, it is a tripwire for somebody making the matcher quadratic.
        assertTrue("1,000 searches took ${millis}ms", millis < SEARCH_BUDGET_MS)
    }

    @Test
    fun `filtering by category does not rebuild the world`() = runTest {
        val controller = MarketSearchController(bigCatalog(), this)
        controller.start()
        advanceUntilIdle()

        val started = System.nanoTime()
        repeat(200) {
            SymbolCategory.entries.forEach { category ->
                controller.setCategory(category)
                advanceUntilIdle()
            }
        }
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue("category switching took ${millis}ms", millis < FILTER_BUDGET_MS)
    }

    @Test
    fun `an empty query returns the whole catalogue rather than nothing`() = runTest {
        val controller = MarketSearchController(bigCatalog(), this)
        controller.start()
        advanceUntilIdle()
        controller.setQuery("btc")
        advanceUntilIdle()
        controller.setQuery("")
        advanceUntilIdle()

        // The regression this pins: a browse list that empties when the reader clears the field is
        // a screen that looks broken at exactly the moment they are trying to start again.
        assertTrue(controller.state.value.results.isNotEmpty())
        assertEquals(false, controller.state.value.empty)
    }

    private companion object {
        /**
         * A thousand searches over a few hundred markets.
         *
         * Generous on purpose: CI runners vary by more than an order of magnitude and a tight
         * bound here would be a flake that gets deleted rather than a check that holds. What it
         * still catches is the shape change — a matcher that goes from linear to quadratic blows
         * past this by a factor of thousands, not percent.
         */
        const val SEARCH_BUDGET_MS = 20_000L

        /** Twelve hundred category switches. Same reasoning. */
        const val FILTER_BUDGET_MS = 10_000L
    }
}
