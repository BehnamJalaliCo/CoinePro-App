package com.coinepro.core.aisignal

import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.symbols.SymbolClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which list the picker offers, and why.
 *
 * The precedence is the whole point and it is the thing most likely to be broken by a later
 * well-meaning change: a server that states its own scope must not be overruled by a catalogue that
 * is merely bigger. Getting that backwards reintroduces the 422 with the app having been told.
 */
class AiSymbolUniverseTest {

    private val catalogue = SymbolClassifier.classifyAll(
        listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XAUUSD", "EURUSD", "GBPUSD"),
    )

    @Test
    fun `a list the server stated wins over a larger catalogue`() {
        val universe = AiSymbolUniverse.resolve(
            platform = MarketPlatform.COINEPRO_FX,
            stated = listOf("XAUUSD", "EURUSD"),
            catalogue = catalogue,
        )

        assertEquals(AiSymbolOrigin.SERVER, universe.origin)
        assertTrue(universe.allows("XAUUSD"))
        assertFalse(universe.allows("BTCUSDT"))
    }

    @Test
    fun `the catalogue still supplies the metadata for a symbol the server allowed`() {
        // The server sends tickers and nothing else. Classifying them from scratch would work, and
        // would also throw away the Persian name and the base/quote split the catalogue carries.
        val universe = AiSymbolUniverse.resolve(
            platform = MarketPlatform.TRADEYAR,
            stated = listOf("btc/usdt"),
            catalogue = catalogue,
        )

        val market = universe.markets.single()
        assertEquals("BTCUSDT", market.symbol)
        assertEquals(catalogue.first { it.symbol == "BTCUSDT" }.description, market.description)
    }

    @Test
    fun `a name the server allows that the catalogue has never heard of is still offered`() {
        val universe = AiSymbolUniverse.resolve(
            platform = MarketPlatform.COINEPRO_FX,
            stated = listOf("XAUUSD", "USDJPY"),
            catalogue = catalogue,
        )

        assertTrue(universe.allows("USDJPY"))
    }

    @Test
    fun `silence from the server is not a refusal of everything`() {
        // TradeYar sends no list at all. Reading its empty list as "accepts nothing" would leave
        // the crypto picker permanently empty.
        val universe = AiSymbolUniverse.resolve(
            platform = MarketPlatform.TRADEYAR,
            stated = emptyList(),
            catalogue = catalogue,
        )

        assertEquals(AiSymbolOrigin.CATALOGUE, universe.origin)
        assertTrue(universe.allows("SOLUSDT"))
    }

    @Test
    fun `with neither list the fallback keeps the screen usable`() {
        val universe = AiSymbolUniverse.resolve(
            platform = MarketPlatform.TRADEYAR,
            stated = emptyList(),
            catalogue = emptyList(),
        )

        assertEquals(AiSymbolOrigin.FALLBACK, universe.origin)
        assertTrue(universe.allows("BTCUSDT"))
    }

    @Test
    fun `search finds a market by ticker, by base and by its Persian name`() {
        val universe = AiSymbolUniverse.resolve(MarketPlatform.TRADEYAR, emptyList(), catalogue)

        assertEquals("BTCUSDT", universe.search("btc").first().meta.symbol)
        assertEquals("EURUSD", universe.search("eur").first().meta.symbol)
        // The old screen searched nothing at all; the old markets list searched the ticker only,
        // which found nothing for a Persian speaker in an app that is Persian by default.
        val persian = universe.search(catalogue.first { it.symbol == "BTCUSDT" }.description)
        assertEquals("BTCUSDT", persian.first().meta.symbol)
    }

    @Test
    fun `an empty query browses rather than returning nothing`() {
        val universe = AiSymbolUniverse.resolve(MarketPlatform.TRADEYAR, emptyList(), catalogue)

        assertEquals(universe.markets.size, universe.search("").size)
    }
}
