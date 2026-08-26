package com.coinepro.core.symbols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolSearchTest {

    private val universe = SymbolClassifier.classifyAll(
        listOf(
            "AAVEUSDT", "BTCUSDT", "WBTCUSDT", "ETHUSDT", "SOLUSDT", "PEPEUSDT",
            "EURUSD", "EURNZD", "XAUEUR", "GBPUSD", "XAUUSD", "US500", "NATGAS",
        ),
    )

    private fun symbols(query: String, category: SymbolCategory? = null) =
        SymbolSearch.search(universe, query, category).map { it.meta.symbol }

    @Test
    fun `an exact ticker wins outright`() {
        assertEquals("EURUSD", symbols("eurusd").first())
    }

    @Test
    fun `a prefix beats a substring beats a scattered match`() {
        val order = symbols("eur")
        assertEquals(listOf("EURUSD", "EURNZD", "XAUEUR"), order.take(3))
    }

    @Test
    fun `typing a coin's base finds the coin, not the wrapped one`() {
        // A `contains` filter put these two in list order, which meant WBTCUSDT could come first.
        val order = symbols("btc")
        assertEquals("BTCUSDT", order.first())
        assertTrue("WBTCUSDT" in order)
    }

    @Test
    fun `a Persian name finds the market`() {
        // The whole reason the description is searched. Somebody typing this in a Persian-default
        // app got nothing at all from the old ticker-only filter.
        assertEquals("SOLUSDT", symbols("سولانا").single())
        assertEquals("BTCUSDT", symbols("بیت‌کوین").first())
        assertEquals("NATGAS", symbols("گاز").single())
    }

    @Test
    fun `the tie-break is liquidity, not the alphabet`() {
        // Every one of these carries "usd" in the middle, so they all match the same way and only
        // the tie-break separates them. Alphabetically AAVEUSDT leads; by liquidity the majors do,
        // which is what the old list got wrong on every query.
        val order = symbols("usd")
        assertEquals("EURUSD", order.first())
        assertTrue(order.indexOf("BTCUSDT") < order.indexOf("AAVEUSDT"))
    }

    @Test
    fun `popularity orders equals without overtaking a better match`() {
        // EURUSD and EURNZD are both prefix matches of the same length; popularity separates them.
        val prefixes = symbols("eur").take(2)
        assertEquals(listOf("EURUSD", "EURNZD"), prefixes)

        // XAUEUR is only a substring match. No boost can lift it over either prefix.
        assertTrue(symbols("eur").indexOf("XAUEUR") > 1)
    }

    @Test
    fun `an empty query is a ranked browse list, not an unranked dump`() {
        val browse = symbols("")
        assertEquals(universe.size, browse.size)
        assertTrue(browse.indexOf("EURUSD") < browse.indexOf("AAVEUSDT"))
        assertTrue(browse.indexOf("BTCUSDT") < browse.indexOf("PEPEUSDT"))
    }

    @Test
    fun `a category chip narrows without reordering`() {
        val crypto = symbols("", SymbolCategory.CRYPTO)
        assertTrue(crypto.all { SymbolClassifier.classify(it).category == SymbolCategory.CRYPTO })
        assertEquals("BTCUSDT", crypto.first())
    }

    @Test
    fun `no match is no result rather than everything`() {
        assertEquals(emptyList<String>(), symbols("zzzzzz"))
    }

    @Test
    fun `the match carries the span to underline`() {
        val hit = SymbolSearch.match(SymbolClassifier.classify("XAUEUR"), "eur")!!
        assertEquals(MatchField.SYMBOL, hit.field)
        assertEquals(3..5, hit.range)
    }

    @Test
    fun `a scattered match has no span, because there is nothing contiguous to underline`() {
        val hit = SymbolSearch.match(SymbolClassifier.classify("EURNZD"), "end")
        assertNotNull(hit)
        assertNull(hit!!.range)
    }

    @Test
    fun `case folding does not depend on the device locale`() {
        val turkish = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale("tr", "TR"))
            // A Turkish default lower-cases I to a dotless ı, which would stop this matching.
            assertEquals("BTCUSDT", symbols("BTC").first())
        } finally {
            java.util.Locale.setDefault(turkish)
        }
    }
}
