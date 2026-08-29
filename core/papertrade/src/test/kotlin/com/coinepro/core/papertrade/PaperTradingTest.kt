package com.coinepro.core.papertrade

import com.coinepro.core.database.PaperTradeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The old model's arithmetic, which the migration reproduces rather than recomputes.
 *
 * These stay because the import depends on them: a reader's forty closed trades must come across
 * showing the numbers the old screen showed them, not the numbers the new rules would have charged.
 */
class PaperTradingTest {

    @Test
    fun `a sell profits when the price falls`() {
        val trade = trade(buy = false, entry = 100.0, size = 2.0)
        assertEquals(20.0, PaperTrading.profit(trade, price = 90.0)!!, 1e-9)
    }

    @Test
    fun `a closed trade marks at its exit, not at the live price`() {
        val trade = trade(entry = 100.0, size = 1.0).copy(exit = 110.0)
        // The market has moved on since. A closed position's result is fixed, and marking it
        // against today would make yesterday's win grow every time the screen is opened.
        assertEquals(10.0, PaperTrading.profit(trade, price = 500.0)!!, 1e-9)
    }

    @Test
    fun `an open trade with no price has no profit, rather than a zero one`() {
        assertNull(PaperTrading.profit(trade(), price = null))
    }

    @Test
    fun `the percentage divides by what was committed`() {
        val trade = trade(entry = 100.0, size = 2.0).copy(exit = 110.0)
        assertEquals(10.0, PaperTrading.profitPercent(trade, price = null)!!, 1e-9)
    }

    @Test
    fun `an entry of zero has no percentage, rather than an infinite one`() {
        assertNull(PaperTrading.profitPercent(trade(entry = 0.0).copy(exit = 10.0), price = null))
    }
}

private fun trade(buy: Boolean = true, entry: Double = 100.0, size: Double = 1.0) =
    PaperTradeEntity(
        id = 0,
        symbol = "BTCUSDT",
        buy = buy,
        entry = entry,
        size = size,
        openedAtEpochMillis = 1_700_000_000_000L,
    )
