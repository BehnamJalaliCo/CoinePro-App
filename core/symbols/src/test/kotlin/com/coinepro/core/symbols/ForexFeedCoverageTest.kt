package com.coinepro.core.symbols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every symbol the forex feed actually quotes, against the filter that decides what a reader sees.
 *
 * `§Ξ20` asked one question of the backend — «how many symbols does a bare `ws/snapshot` return
 * today» — and the answer, measured on 2026-09-22, is **nineteen**, listed below exactly as the
 * feed spells them. The interesting number is not that one, though. It is how many of the nineteen
 * survive `SymbolArtwork.covers`, because a symbol with no mark never reaches a list: «no blank
 * squares, no lettered discs» is a standing rule and the filter runs at the catalogue *and* at the
 * live feed.
 *
 * This test exists so the answer stops being something somebody has to re-derive. It is pinned
 * rather than merely asserted: a mark added for a category that has none will fail it, which is
 * the moment to move the number and say what changed.
 */
class ForexFeedCoverageTest {

    /** `coineprofx.com/api/ws/snapshot`, every key it returned, sorted. */
    private val feed = listOf(
        "AUDJPY", "AUDUSD", "DE40", "EURAUD", "EURGBP", "EURJPY", "EURUSD", "GBPJPY", "GBPUSD",
        "NAS100", "NZDUSD", "US30", "US500", "USDCAD", "USDCHF", "USDJPY", "XAGUSD", "XAUUSD",
        "XTIUSD",
    )

    @Test
    fun `the feed is nineteen symbols`() {
        assertEquals(19, feed.size)
    }

    @Test
    fun `every pair, metal and index the feed quotes has a mark`() {
        val unmarked = feed.filterNot { SymbolArtwork.covers(it) }
        // One, and it is the energy contract — see the test below, which says why.
        assertEquals(listOf("XTIUSD"), unmarked)
    }

    @Test
    fun `the four indices arrive under broker spellings and still resolve`() {
        // `DE40` and `NAS100` are not the canonical codes; the alias table folds them onto `GER40`
        // and `US100`, and the artwork tables are keyed on the canonical form. A broker renaming
        // its index must not empty a row.
        listOf("DE40", "NAS100", "US30", "US500").forEach { symbol ->
            assertTrue("$symbol lost its flag", SymbolArtwork.covers(symbol))
        }
    }

    @Test
    fun `the one symbol a reader cannot see is crude oil, and it is missing a mark rather than a name`() {
        // Everything else is in place for it: `SymbolAliases` folds XTIUSD onto USOIL,
        // `SymbolNames.ENERGY` calls it «نفت آمریکا (WTI)», `SymbolRanking` gives energy its own
        // rank, and `BundledUniverse` seeds it. `SymbolArtwork.covers` answers false because the
        // vendored archive has no oil mark and this app has authored none — energy is the one
        // category with «no mark of its own».
        //
        // So it is not a classification bug and not a feed gap. It is one drawing, and drawing it
        // is a decision about the product's look rather than one a build should take on its own —
        // `design/asset-logos/authored/README.md` is the path if the owner wants it.
        assertFalse(SymbolArtwork.covers("XTIUSD"))
        assertEquals(SymbolCategory.ENERGY, SymbolClassifier.classify("XTIUSD").category)
        assertEquals("USOIL", SymbolClassifier.classify("XTIUSD").canonical.uppercase())
        assertTrue(SymbolNames.ENERGY.containsKey("USOIL"))
    }
}
