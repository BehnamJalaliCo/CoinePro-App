package com.coinepro.core.symbols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchlistStarterTest {

    @Test
    fun `at least seven markets on each platform, all with artwork`() {
        assertTrue(WatchlistStarter.CRYPTO.size >= 7)
        assertTrue(WatchlistStarter.FOREX.size >= 7)
        for (symbol in WatchlistStarter.ALL) {
            assertTrue("$symbol has no artwork and would be filtered off the list", SymbolArtwork.covers(symbol))
        }
    }

    @Test
    fun `each half classifies as its own platform's market`() {
        for (symbol in WatchlistStarter.CRYPTO) {
            assertEquals(symbol, SymbolCategory.CRYPTO, SymbolClassifier.classify(symbol).category)
        }
        for (symbol in WatchlistStarter.FOREX) {
            assertTrue(symbol, SymbolClassifier.classify(symbol).category != SymbolCategory.CRYPTO)
        }
    }

    @Test
    fun `no duplicates`() {
        assertEquals(WatchlistStarter.ALL.size, WatchlistStarter.ALL.distinct().size)
    }
}
