package com.coinepro.feature.search

import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.symbols.MatchField
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolClassifier
import com.coinepro.core.symbols.SymbolUniverse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **What the markets filter sheet does to a list** (run ΤΦΥ, F2).
 *
 * Four questions that compose, and one rule that is easy to get wrong in a way nobody notices until
 * a screen is empty: a figure the app does not have must not remove a row. The non-crypto side of
 * this product reports no turnover at all and often no change either, so a filter that treated
 * «unknown» as «no» would answer «بیش از ۵٪+» with an empty list on the day gold moved eight.
 */
class MarketFilterTest {

    private fun row(
        symbol: String,
        venue: String? = null,
        turnover: Double? = null,
    ): MarketSearchRow = MarketSearchRow(
        meta = SymbolClassifier.classify(symbol),
        quote = null,
        field = MatchField.NONE,
        highlight = null,
        listing = venue?.let {
            SymbolUniverse.of(SymbolClassifier.classify(symbol), venue = it, turnover24h = turnover)
        },
    )

    private val rows = listOf(
        row("BTCUSDT", venue = "LBank", turnover = 50_000_000.0),
        row("ETHUSDT", venue = "LBank", turnover = 900_000.0),
        row("XAUUSD", venue = "CoinePro FX"),
        row("EURUSD", venue = "CoinePro FX"),
        row("DOGEUSDT"),
    )

    @Test
    fun `an untouched filter is not a filter`() {
        val filter = MarketFilter()
        assertTrue(filter.isEmpty)
        assertEquals(0, filter.count)
        assertEquals(rows.size, applyFilter(rows, filter) { null }.size)
    }

    @Test
    fun `the badge counts the questions that have been answered`() {
        val filter = MarketFilter(
            types = setOf(SymbolCategory.CRYPTO, SymbolCategory.METAL),
            change = ChangeBand.UP_5,
        )
        assertEquals(3, filter.count)
        assertFalse(filter.isEmpty)
    }

    @Test
    fun `type and venue narrow, and they compose`() {
        assertEquals(
            listOf("BTCUSDT", "ETHUSDT", "DOGEUSDT"),
            applyFilter(rows, MarketFilter(types = setOf(SymbolCategory.CRYPTO))) { null }.map { it.meta.symbol },
        )
        assertEquals(
            listOf("BTCUSDT", "ETHUSDT"),
            applyFilter(
                rows,
                MarketFilter(types = setOf(SymbolCategory.CRYPTO), venues = setOf("LBank")),
            ) { null }.map { it.meta.symbol },
        )
    }

    @Test
    fun `a row nothing named a venue for is dropped by a venue filter`() {
        // Correct, and worth pinning: «traded on LBank» is a claim, and a row whose listing nobody
        // sent has not made it. This is the one place a null does remove a row, because the
        // question is about the listing rather than about a figure on it.
        val kept = applyFilter(rows, MarketFilter(venues = setOf("LBank"))) { null }
        assertTrue(kept.none { it.meta.symbol == "DOGEUSDT" })
    }

    @Test
    fun `a turnover floor keeps only what said enough, and drops what said nothing`() {
        assertEquals(
            listOf("BTCUSDT"),
            applyFilter(rows, MarketFilter(turnover = TurnoverFloor.OVER_10M)) { null }.map { it.meta.symbol },
        )
    }

    @Test
    fun `a change band never removes a row whose change nobody knows`() {
        val changes = mapOf("BTCUSDT" to 7.0, "ETHUSDT" to -6.0)
        val up = applyFilter(rows, MarketFilter(change = ChangeBand.UP_5)) { changes[it.meta.symbol] }
        // Bitcoin qualifies; Ether does not; the three with no figure are still on screen.
        assertTrue(up.any { it.meta.symbol == "BTCUSDT" })
        assertTrue(up.none { it.meta.symbol == "ETHUSDT" })
        assertTrue(up.any { it.meta.symbol == "XAUUSD" })
        assertTrue(up.any { it.meta.symbol == "DOGEUSDT" })
    }

    @Test
    fun `the bands mean what they say`() {
        assertTrue(ChangeBand.UP_5.holds(5.0))
        assertFalse(ChangeBand.UP_5.holds(4.99))
        assertTrue(ChangeBand.DOWN_5.holds(-5.0))
        assertFalse(ChangeBand.DOWN_5.holds(-4.99))
        assertTrue(ChangeBand.UP_ANY.holds(0.0))
        assertTrue(ChangeBand.DOWN_ANY.holds(0.0))
    }

    @Test
    fun `the sheet is offered only the types and venues the list actually has`() {
        assertEquals(
            listOf(SymbolCategory.CRYPTO, SymbolCategory.METAL, SymbolCategory.FOREX),
            typesOf(rows),
        )
        assertEquals(listOf("LBank", "CoinePro FX"), venuesOf(rows))
        // A list from one venue offers no venue block at all — the sheet's own rule. Stated here so
        // the screen and the test agree about what «nothing to choose between» means.
        assertEquals(1, venuesOf(rows.filter { it.venue == "LBank" }).size)
    }
}
