package com.coinepro.core.signals

import com.coinepro.core.model.MarketType
import com.coinepro.core.model.SignalDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The forex side of this product is the gold call** (run Ψ).
 *
 * The rule is small and the reason it is a rule rather than a `filter` at one call site is that the
 * app makes a promise with it. Copy trading is gone; what remains on that side is a chart to study,
 * alerts to set, and one published signal. A list that showed a EURUSD call from some earlier
 * configuration of the desk would be showing something the product cannot stand behind.
 *
 * What is pinned here: gold survives whatever the feed's punctuation, silver does not, crypto is
 * untouched, and what is held back is counted rather than dropped.
 */
class ForexSignalScopeTest {

    @Test
    fun `gold is recognised however the feed spells it`() {
        listOf("XAUUSD", "xauusd", "XAU/USD", "XAUUSD.m", "XAU_USD").forEach { symbol ->
            assertTrue("«$symbol» is gold and was not recognised", ForexSignalScope.isGold(symbol))
        }
    }

    @Test
    fun `silver is not gold, and neither is anything else on the forex feed`() {
        // Silver is the one worth naming. It is in the bundled symbol list because the **chart**
        // carries it, and a chart is not a call — the two lists are different things and this is
        // where they stop being confused for each other.
        listOf("XAGUSD", "EURUSD", "GBPJPY", "USDIRR", "DXY").forEach { symbol ->
            assertFalse("«$symbol» was taken for gold", ForexSignalScope.isGold(symbol))
        }
    }

    @Test
    fun `a forex list keeps its gold and nothing else`() {
        val page = listOf(signal(1, "XAUUSD"), signal(2, "EURUSD"), signal(3, "XAU/USD"), signal(4, "XAGUSD"))
        val shown = ForexSignalScope.scoped(SignalMarketFilter.FOREX, page)
        assertEquals(listOf(1L, 3L), shown.map(TradingSignal::id))
        assertEquals(2, ForexSignalScope.withheld(SignalMarketFilter.FOREX, page))
    }

    @Test
    fun `crypto is untouched, and is the same list object`() {
        // Identity rather than equality: the crypto path must not copy a list of a hundred signals
        // on every refresh to answer a question that has nothing to do with it.
        val page = listOf(signal(1, "BTCUSDT"), signal(2, "ETHUSDT"))
        assertTrue(ForexSignalScope.scoped(SignalMarketFilter.CRYPTO, page) === page)
        assertEquals(0, ForexSignalScope.withheld(SignalMarketFilter.CRYPTO, page))
    }

    @Test
    fun `a gold-only response withholds nothing, which is the expected case`() {
        val page = listOf(signal(1, "XAUUSD"), signal(2, "XAUUSD"))
        assertEquals(0, ForexSignalScope.withheld(SignalMarketFilter.FOREX, page))
        assertEquals(page, ForexSignalScope.scoped(SignalMarketFilter.FOREX, page))
    }

    private fun signal(id: Long, symbol: String) = TradingSignal(
        id = id,
        market = MarketType.FOREX,
        symbol = symbol,
        direction = SignalDirection.BUY,
        status = "active",
        timeframe = "H1",
        strategy = null,
        confidence = null,
        entry = null,
        entryZone = null,
        stopLoss = null,
        targets = emptyList(),
        riskRewardTp1 = null,
        currentQuote = null,
        livePnlPercent = null,
        hitTarget = null,
        createdAt = null,
        closedAt = null,
    )
}
