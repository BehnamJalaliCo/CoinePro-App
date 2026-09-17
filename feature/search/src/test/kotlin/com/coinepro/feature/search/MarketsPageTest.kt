package com.coinepro.feature.search

import com.coinepro.core.symbols.SymbolCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seven tabs, and which of them a platform is allowed to draw (run ΤΦΥ, U5).
 *
 * The rule the whole strip rests on is **absent, not empty**: a tab that can only ever show nothing
 * teaches the reader that the app is broken rather than that the data is elsewhere. That was the
 * reported fault the old five-tab tray caused — «فارکس» on TradeYar opened a screen saying
 * «بازاری با این نام پیدا نشد» about a search nobody had made — so it is worth a test rather than a
 * comment.
 */
class MarketsPageTest {

    private val crypto = setOf(SymbolCategory.CRYPTO)
    private val both = setOf(SymbolCategory.CRYPTO, SymbolCategory.FOREX, SymbolCategory.METAL)

    @Test
    fun `the owner's seven, in the owner's order`() {
        assertEquals(
            listOf("TOP", "POPULAR", "WATCHLIST", "MOVERS", "VOLUME", "FOREX", "METALS"),
            MarketsPage.entries.map { it.name },
        )
    }

    @Test
    fun `a crypto-only catalogue is not offered forex or metals`() {
        val offered = offeredPages(families = crypto, hasFigures = true)
        assertFalse(MarketsPage.FOREX in offered)
        assertFalse(MarketsPage.METALS in offered)
        assertTrue(MarketsPage.TOP in offered)
    }

    @Test
    fun `a platform with no day's figures is offered neither an ordering tab nor three of them`() {
        // CoinePro-FX. There is no ticker route at all there, so «پرطرفدار», «برنده/بازنده» and
        // «حجم» have nothing to order by — and each would be a door onto an empty screen.
        val offered = offeredPages(families = both, hasFigures = false)
        assertEquals(
            listOf(MarketsPage.TOP, MarketsPage.WATCHLIST, MarketsPage.FOREX, MarketsPage.METALS),
            offered,
        )
    }

    @Test
    fun `the list itself and the reader's own are always offered`() {
        // Even against a catalogue that has not arrived. «برتر» is the list and «دیده‌بان» is the
        // reader's, which is allowed to be empty and has copy of its own that says so.
        val offered = offeredPages(families = emptySet(), hasFigures = false)
        assertEquals(listOf(MarketsPage.TOP, MarketsPage.WATCHLIST), offered)
    }

    @Test
    fun `only the ordering tabs are marked as needing the day's figures`() {
        // The property that gates the three above. A family tab is filtered by the catalogue and a
        // catalogue exists on both platforms, so marking one of those as needing figures would take
        // «فارکس» off CoinePro-FX — the one platform whose whole catalogue is forex.
        assertEquals(
            listOf(MarketsPage.POPULAR, MarketsPage.MOVERS, MarketsPage.VOLUME),
            MarketsPage.entries.filter { it.needsFigures },
        )
    }

    @Test
    fun `the watchlist is the only panel`() {
        // `panel` is what swaps the catalogue list for `WatchlistPanel`, and two of them would be
        // two screens claiming the same body.
        assertEquals(listOf(MarketsPage.WATCHLIST), MarketsPage.entries.filter { it.panel })
    }
}
