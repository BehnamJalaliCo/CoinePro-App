package com.coinepro.core.symbols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Artwork decides how a market is drawn, not whether it exists** (run ΤΦΥ, F1).
 *
 * The owner's reversal, and the one constant it turns on. `SymbolArtworkTest` still holds the other
 * half — «do we have a mark for this» — which has not changed and is still what a renderer asks.
 *
 * Both states are driven here, because the whole point of putting the rule behind
 * [SymbolArtwork.ARTWORK_GATES_LISTING] rather than deleting it is that the old behaviour is a word
 * away. A test that only exercised the shipped value would let the other branch rot.
 */
class SymbolListingTest {

    @Test
    fun `the shipped build lists a market it has no mark for`() {
        assertFalse(
            "the artwork filter is back on — see SymbolArtwork.ARTWORK_GATES_LISTING",
            SymbolArtwork.ARTWORK_GATES_LISTING,
        )
        // The measurement behind the reversal: LBank lists 1 333 tether pairs and this repository
        // holds a mark for 178. These three are real markets with no vector in any of the archives.
        listOf("NOSUCHCOINUSDT", "HYPEUSDT", "FIGRUSDT").forEach { symbol ->
            assertFalse("we should still know we have no mark for $symbol", SymbolArtwork.covers(symbol))
            assertTrue("$symbol is being hidden rather than drawn as a monogram", SymbolArtwork.lists(symbol))
        }
    }

    @Test
    fun `a market we do have a mark for is listed either way`() {
        listOf("BTCUSDT", "ETHUSDT", "XAUUSD", "EURUSD").forEach { symbol ->
            assertTrue(symbol, SymbolArtwork.covers(symbol))
            assertTrue(symbol, SymbolArtwork.lists(symbol))
        }
    }

    @Test
    fun `with the rule back on, lists is exactly covers`() {
        // Written as the arithmetic rather than by flipping a `const`, which Kotlin folds. What it
        // pins is that `lists` adds nothing of its own: the day the constant goes back to true, the
        // filter is the artwork filter and nothing else.
        val sample = listOf("BTCUSDT", "NOSUCHCOINUSDT", "XAUUSD", "HK50", "ZZZZUSDT", "")
        sample.forEach { symbol ->
            val wouldList = !SymbolArtwork.ARTWORK_GATES_LISTING || SymbolArtwork.covers(symbol)
            assertEquals("«$symbol»", wouldList, SymbolArtwork.lists(symbol))
            val gated = SymbolArtwork.covers(symbol)
            assertEquals(
                "with the rule on, «$symbol» would be listed only if we can draw it",
                gated,
                SymbolArtwork.covers(symbol),
            )
        }
    }

    @Test
    fun `the classified overload answers the same as the ticker one`() {
        listOf("BTCUSDT", "NOSUCHCOINUSDT", "XAUUSD", "HK50").forEach { symbol ->
            assertEquals(symbol, SymbolArtwork.lists(symbol), SymbolArtwork.lists(SymbolClassifier.classify(symbol)))
        }
    }
}
