package com.coinepro.core.papertrade

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The record, and specifically that it is `PortfolioMath`'s and not a second copy of it.
 *
 * The one thing worth pinning beyond the translation is the curve: a paper account knows its
 * balance after every close, so `PortfolioStats` draws a *balance* curve rather than cumulative
 * profit from zero — and that is what makes the drawdown percentage a real number rather than one
 * divided by something near zero.
 */
class PaperRecordTest {

    @Test
    fun `an empty book has no statistics rather than zeroed ones`() {
        val record = PaperRecordMath.of(emptyList())

        assertNull(record.stats.winRate)
        assertNull(record.stats.profitFactor)
        assertNull(record.averageWin)
        assertNull(record.payoff)
    }

    @Test
    fun `the curve is the account balance, so the drawdown has a percentage`() {
        val record = PaperRecordMath.of(
            listOf(
                closed(id = 1, gross = 200.0, balanceAfter = 10_200.0),
                closed(id = 2, gross = -400.0, balanceAfter = 9_800.0),
                closed(id = 3, gross = 100.0, balanceAfter = 9_900.0),
            ),
        )

        assertTrue(record.stats.equityIsBalance)
        assertEquals(10_200.0, record.stats.equity.first().equity, 1e-9)
        // The peak is 10,200 and the trough 9,800.
        assertEquals(400.0, record.stats.maxDrawdown, 1e-9)
        assertNotNull("a balance curve can divide, so the percentage exists", record.stats.maxDrawdownPercent)
    }

    @Test
    fun `costs are reported as costs and not folded into the profit`() {
        // Ten of gross and one of fees is nine of net. A record that showed ten would be telling a
        // reader their strategy makes more than their account does.
        val record = PaperRecordMath.of(listOf(closed(id = 1, gross = 10.0, fees = 1.0, balanceAfter = 10_009.0)))

        assertEquals(9.0, record.stats.net, 1e-9)
        assertEquals(1.0, record.costs, 1e-9)
    }

    @Test
    fun `a scratch trade is neither a win nor a loss`() {
        // The same rule `PortfolioMath` keeps everywhere else, which is the whole reason this
        // module does not compute its own win rate.
        val record = PaperRecordMath.of(
            listOf(
                closed(id = 1, gross = 10.0, balanceAfter = 10_010.0),
                closed(id = 2, gross = 0.0, balanceAfter = 10_010.0),
            ),
        )

        assertEquals(1, record.stats.wins)
        assertEquals(0, record.stats.losses)
        assertEquals(100.0, record.stats.winRate!!, 1e-9)
        assertEquals(2, record.stats.trades)
    }

    @Test
    fun `the per-symbol breakdown puts the worst first`() {
        val record = PaperRecordMath.of(
            listOf(
                closed(id = 1, symbol = "BTCUSDT", gross = 50.0, balanceAfter = 10_050.0),
                closed(id = 2, symbol = "ETHUSDT", gross = -90.0, balanceAfter = 9_960.0),
            ),
        )

        assertEquals("ETHUSDT", record.bySymbol.first().symbol)
    }

    private fun closed(
        id: Long,
        symbol: String = "BTCUSDT",
        gross: Double,
        fees: Double = 0.0,
        balanceAfter: Double,
    ) = PaperClosedTrade(
        id = id,
        symbol = symbol,
        side = PaperSide.BUY,
        size = 1.0,
        entry = 100.0,
        exit = 100.0 + gross,
        openedAtEpochMillis = AT,
        closedAtEpochMillis = AT + id * 60_000L,
        gross = gross,
        fees = fees,
        reason = PaperCloseReason.MANUAL,
        balanceAfter = balanceAfter,
    )

    private companion object {
        const val AT = 1_756_000_000_000L
    }
}
