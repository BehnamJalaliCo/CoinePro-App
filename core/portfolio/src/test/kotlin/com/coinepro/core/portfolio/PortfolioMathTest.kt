package com.coinepro.core.portfolio

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic a reader will act on.
 *
 * Every number here is checkable by hand, and that is the point of the fixtures: they are small
 * enough to add up in your head, so a test that fails says which figure is wrong rather than that
 * something somewhere changed.
 */
class PortfolioMathTest {

    private fun trade(
        id: String,
        symbol: String = "XAUUSD",
        net: Double?,
        closedAt: Long,
        balanceAfter: Double? = null,
        gross: Double? = null,
        commission: Double? = null,
        swap: Double? = null,
    ) = ClosedTrade(
        id = id,
        symbol = symbol,
        direction = TradeDirection.BUY,
        volume = 0.1,
        entry = 100.0,
        exit = 101.0,
        openedAt = closedAt - 3_600,
        closedAt = closedAt,
        grossProfit = gross,
        commission = commission,
        swap = swap,
        netProfit = net,
        balanceAfter = balanceAfter,
    )

    private val day = 86_400L
    private val base = 1_767_225_600L // 2026-01-01T00:00:00Z

    @Test
    fun `an empty history is zero rather than a divide by nothing`() {
        val stats = PortfolioMath.summarise(emptyList())
        assertEquals(0, stats.trades)
        assertEquals(0.0, stats.net, 1e-9)
        assertNull(stats.winRate)
        assertNull(stats.profitFactor)
        assertNull(stats.expectancy)
        assertEquals(0.0, stats.maxDrawdown, 1e-9)
        assertTrue(stats.equity.isEmpty())
    }

    @Test
    fun `wins losses and the totals are what you get by adding them up`() {
        val stats = PortfolioMath.summarise(
            listOf(
                trade("1", net = 100.0, closedAt = base),
                trade("2", net = -40.0, closedAt = base + day),
                trade("3", net = 60.0, closedAt = base + 2 * day),
                trade("4", net = -20.0, closedAt = base + 3 * day),
            ),
        )
        assertEquals(4, stats.trades)
        assertEquals(2, stats.wins)
        assertEquals(2, stats.losses)
        assertEquals(100.0, stats.net, 1e-9)
        assertEquals(50.0, stats.winRate!!, 1e-9)
        // 160 won over 60 lost
        assertEquals(160.0 / 60.0, stats.profitFactor!!, 1e-9)
        assertEquals(25.0, stats.expectancy!!, 1e-9)
        assertEquals(100.0, stats.best!!, 1e-9)
        assertEquals(-40.0, stats.worst!!, 1e-9)
    }

    @Test
    fun `a break even trade counts as a trade and as neither a win nor a loss`() {
        // CoinePro-FX's own /stats filters on net > 0 and net < 0, so a zero lands in neither. This
        // matches that rather than inventing a third convention — and the expectancy still divides
        // by every trade, because a flat trade did happen.
        val stats = PortfolioMath.summarise(
            listOf(
                trade("1", net = 50.0, closedAt = base),
                trade("2", net = 0.0, closedAt = base + day),
                trade("3", net = -50.0, closedAt = base + 2 * day),
            ),
        )
        assertEquals(3, stats.trades)
        assertEquals(1, stats.wins)
        assertEquals(1, stats.losses)
        assertEquals(50.0, stats.winRate!!, 1e-9)
        assertEquals(0.0, stats.expectancy!!, 1e-9)
    }

    @Test
    fun `no losses gives no profit factor rather than infinity`() {
        // Four winners is not evidence of an infinite edge, and "∞" beside a win rate reads as a
        // bug rather than as a result.
        val stats = PortfolioMath.summarise(
            listOf(
                trade("1", net = 10.0, closedAt = base),
                trade("2", net = 20.0, closedAt = base + day),
            ),
        )
        assertEquals(100.0, stats.winRate!!, 1e-9)
        assertNull(stats.profitFactor)
    }

    @Test
    fun `the curve is cumulative profit when no balance is reported`() {
        val stats = PortfolioMath.summarise(
            listOf(
                trade("1", net = 100.0, closedAt = base),
                trade("2", net = -40.0, closedAt = base + day),
                trade("3", net = 60.0, closedAt = base + 2 * day),
            ),
        )
        assertFalse(stats.equityIsBalance)
        assertEquals(listOf(100.0, 60.0, 120.0), stats.equity.map { it.equity })
        assertNull("no denominator without a balance", stats.maxDrawdownPercent)
        assertEquals(40.0, stats.maxDrawdown, 1e-9)
    }

    @Test
    fun `the curve is real balance when every trade carries one`() {
        val stats = PortfolioMath.summarise(
            listOf(
                trade("1", net = 100.0, closedAt = base, balanceAfter = 10_100.0),
                trade("2", net = -600.0, closedAt = base + day, balanceAfter = 9_500.0),
                trade("3", net = 200.0, closedAt = base + 2 * day, balanceAfter = 9_700.0),
            ),
        )
        assertTrue(stats.equityIsBalance)
        assertEquals(600.0, stats.maxDrawdown, 1e-9)
        // 600 off a 10,100 peak. This is the figure that is meaningless on a profit-from-zero
        // curve, which is why it is only offered when the curve is a real balance.
        assertEquals(600.0 / 10_100.0 * 100.0, stats.maxDrawdownPercent!!, 1e-9)
    }

    @Test
    fun `one missing balance is enough to fall back to cumulative profit`() {
        // A line stitched from two real balances and one running total has a step in it that means
        // nothing at all, and it would be drawn as though it meant something.
        val stats = PortfolioMath.summarise(
            listOf(
                trade("1", net = 100.0, closedAt = base, balanceAfter = 10_100.0),
                trade("2", net = -40.0, closedAt = base + day, balanceAfter = null),
            ),
        )
        assertFalse(stats.equityIsBalance)
        assertEquals(listOf(100.0, 60.0), stats.equity.map { it.equity })
    }

    @Test
    fun `trades arriving newest first still draw the curve forwards`() {
        // TradeYar sorts newest first and CoinePro-FX pages oldest first. A curve drawn in the
        // order received would say a profitable account had fallen.
        val newestFirst = listOf(
            trade("3", net = 60.0, closedAt = base + 2 * day),
            trade("2", net = -40.0, closedAt = base + day),
            trade("1", net = 100.0, closedAt = base),
        )
        val stats = PortfolioMath.summarise(newestFirst)
        assertEquals(listOf(base, base + day, base + 2 * day), stats.equity.map { it.time })
        assertEquals(listOf(100.0, 60.0, 120.0), stats.equity.map { it.equity })
    }

    @Test
    fun `gross and costs are summed only where a server reports them`() {
        val withCosts = PortfolioMath.summarise(
            listOf(
                trade("1", net = -257.36, closedAt = base, gross = -256.8, commission = -0.56, swap = 0.0),
            ),
        )
        assertEquals(-256.8, withCosts.gross!!, 1e-9)
        assertEquals(-0.56, withCosts.costs!!, 1e-9)

        val withoutCosts = PortfolioMath.summarise(listOf(trade("1", net = 5.0, closedAt = base)))
        assertNull("no gross on the exchange side", withoutCosts.gross)
        assertNull(withoutCosts.costs)
    }

    @Test
    fun `attribution is worst symbol first`() {
        // Ordered by money, not by activity: twenty small winners are less interesting than one
        // large loser, and a list sorted by trade count buries the row somebody came here to find.
        val rows = PortfolioMath.bySymbol(
            listOf(
                trade("1", symbol = "XAUUSD", net = 40.0, closedAt = base),
                trade("2", symbol = "XAUUSD", net = 30.0, closedAt = base + 1),
                trade("3", symbol = "XAGUSD", net = -500.0, closedAt = base + 2),
                trade("4", symbol = "EURUSD", net = 10.0, closedAt = base + 3),
            ),
        )
        assertEquals(listOf("XAGUSD", "EURUSD", "XAUUSD"), rows.map { it.symbol })
        assertEquals(2, rows.last().trades)
        assertEquals(100.0, rows.last().winRate!!, 1e-9)
    }

    @Test
    fun `a quiet month is a zero rather than a missing column`() {
        // January and March, no February. A chart that omits the empty month draws them adjacent
        // and compresses two months of time into one gap.
        val rows = PortfolioMath.byMonth(
            listOf(
                trade("1", net = 100.0, closedAt = 1_767_225_600L), // 2026-01-01
                trade("2", net = -20.0, closedAt = 1_772_323_200L), // 2026-03-01
            ),
            ZoneOffset.UTC,
        )
        assertEquals(listOf(1 to 1, 2 to 0, 3 to 1), rows.map { it.month to it.trades })
        assertEquals(listOf(2026, 2026, 2026), rows.map { it.year })
        assertEquals(0.0, rows[1].net, 1e-9)
    }

    @Test
    fun `months run across a year boundary`() {
        val rows = PortfolioMath.byMonth(
            listOf(
                trade("1", net = 1.0, closedAt = 1_764_547_200L), // 2025-12-01
                trade("2", net = 2.0, closedAt = 1_769_904_000L), // 2026-02-01
            ),
            ZoneOffset.UTC,
        )
        assertEquals(listOf(2025 to 12, 2026 to 1, 2026 to 2), rows.map { it.year to it.month })
    }

    @Test
    fun `a trade with no profit figure does not poison the totals`() {
        // Neither server should send this, but a null net is representable and reading it as a
        // loss would invent one.
        val stats = PortfolioMath.summarise(
            listOf(
                trade("1", net = 50.0, closedAt = base),
                trade("2", net = null, closedAt = base + day),
            ),
        )
        assertEquals(2, stats.trades)
        assertEquals(1, stats.wins)
        assertEquals(0, stats.losses)
        assertEquals(50.0, stats.net, 1e-9)
    }
}
