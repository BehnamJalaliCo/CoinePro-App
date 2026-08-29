package com.coinepro.feature.portfolio

import com.coinepro.core.portfolio.ClosedTrade
import com.coinepro.core.portfolio.TradeDirection
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report's arithmetic, on fixtures small enough to add up on paper.
 *
 * Two of these tests exist because the figure they check is routinely computed wrongly rather than
 * because the code is likely to rot: the max-drawdown test proves the number is peak-to-trough on
 * equity and not the largest losing trade, and the Sortino test proves the divisor is the count of
 * every return rather than the count of the losing ones. Both are the kind of error that produces a
 * plausible number, which is why neither can be caught by looking at the screen.
 */
class PortfolioMetricsTest {

    private val day = 86_400L
    private val base = 1_767_225_600L // 2026-01-01T00:00:00Z

    private fun trade(
        id: String,
        net: Double?,
        closedAt: Long,
        symbol: String = "XAUUSD",
        openedAt: Long? = closedAt - 3_600,
        balanceAfter: Double? = null,
    ) = ClosedTrade(
        id = id,
        symbol = symbol,
        direction = TradeDirection.BUY,
        volume = 0.1,
        entry = 100.0,
        exit = 101.0,
        openedAt = openedAt,
        closedAt = closedAt,
        netProfit = net,
        balanceAfter = balanceAfter,
    )

    /** +100, −40, −30, +10, −50: the equity walks 100, 60, 30, 40, −10. */
    private val walk = listOf(
        trade("1", 100.0, base + day),
        trade("2", -40.0, base + 2 * day),
        trade("3", -30.0, base + 3 * day),
        trade("4", 10.0, base + 4 * day),
        trade("5", -50.0, base + 5 * day),
    )

    @Test
    fun `an empty history reports nothing rather than a set of zeros`() {
        val metrics = PortfolioMetrics.of(emptyList())
        assertEquals(0, metrics.trades)
        assertNull(metrics.winRate)
        assertNull(metrics.profitFactor)
        assertNull(metrics.expectancy)
        assertNull(metrics.sharpe)
        assertNull(metrics.sortino)
        assertNull(metrics.drawdown)
        assertNull(metrics.longestDrawdown)
        assertEquals(0.0, metrics.maxDrawdown, 1e-9)
        assertTrue(metrics.equity.isEmpty())
    }

    @Test
    fun `the counting figures are what you get by adding the fixture up by hand`() {
        val metrics = PortfolioMetrics.of(walk)
        assertEquals(5, metrics.trades)
        assertEquals(2, metrics.wins)
        assertEquals(3, metrics.losses)
        assertEquals(0, metrics.scratches)
        assertEquals(-10.0, metrics.net, 1e-9)
        assertEquals(110.0, metrics.grossWin, 1e-9)
        assertEquals(120.0, metrics.grossLoss, 1e-9)
        assertEquals(40.0, metrics.winRate!!, 1e-9)
        assertEquals(110.0 / 120.0, metrics.profitFactor!!, 1e-9)
        assertEquals(-2.0, metrics.expectancy!!, 1e-9)
    }

    @Test
    fun `average win average loss and their ratio are the payoff figures`() {
        val metrics = PortfolioMetrics.of(walk)
        assertEquals(55.0, metrics.averageWin!!, 1e-9)
        // Positive magnitude, so it reads beside the average win rather than against it.
        assertEquals(40.0, metrics.averageLoss!!, 1e-9)
        assertEquals(1.375, metrics.winLossRatio!!, 1e-9)
        assertEquals(100.0, metrics.largestWin!!, 1e-9)
        // Signed, because it is a loss.
        assertEquals(-50.0, metrics.largestLoss!!, 1e-9)
    }

    @Test
    fun `max drawdown is peak to trough on equity and not the largest losing trade`() {
        val metrics = PortfolioMetrics.of(walk)
        // The curve peaks at +100 after the first trade and troughs at −10 after the last, so the
        // account gave back 110 — more than twice the worst single trade, which lost 50. A report
        // that printed 50 here would be understating the risk by a factor of two.
        assertEquals(110.0, metrics.maxDrawdown, 1e-9)
        assertEquals(-50.0, metrics.largestLoss!!, 1e-9)
        assertNotEquals(metrics.maxDrawdown, -metrics.largestLoss!!, 1e-9)

        val span = metrics.drawdown!!
        assertEquals(0, span.peakIndex)
        assertEquals(4, span.troughIndex)
        assertEquals(100.0, span.peakEquity, 1e-9)
        assertEquals(-10.0, span.troughEquity, 1e-9)
        assertEquals(4, span.trades)
        assertEquals(4 * day, span.seconds)
        // No percentage: this curve is cumulative profit from zero, and dividing by a peak that
        // can sit near zero is how a report ends up printing 312%.
        assertNull(span.depthPercent)
    }

    @Test
    fun `a drawdown percentage is only offered on a real balance curve`() {
        val balances = listOf(
            trade("1", 0.0, base + day, balanceAfter = 1_000.0),
            trade("2", -100.0, base + 2 * day, balanceAfter = 900.0),
            trade("3", 200.0, base + 3 * day, balanceAfter = 1_100.0),
        )
        val metrics = PortfolioMetrics.of(balances)
        assertTrue(metrics.equityIsBalance)
        assertEquals(100.0, metrics.maxDrawdown, 1e-9)
        assertEquals(10.0, metrics.maxDrawdownPercent!!, 1e-9)
    }

    @Test
    fun `the longest drawdown counts to the last trade while the account is still under water`() {
        val metrics = PortfolioMetrics.of(walk)
        val run = metrics.longestDrawdown!!
        assertEquals(0, run.startIndex)
        assertEquals(4, run.endIndex)
        assertEquals(4, run.trades)
        assertEquals(4 * day, run.seconds)
        assertFalse(run.recovered)
    }

    @Test
    fun `a drawdown that makes a new high is recorded as recovered`() {
        val balances = listOf(
            trade("1", 0.0, base + day, balanceAfter = 1_000.0),
            trade("2", -100.0, base + 2 * day, balanceAfter = 900.0),
            trade("3", 50.0, base + 3 * day, balanceAfter = 950.0),
            trade("4", 50.0, base + 4 * day, balanceAfter = 1_000.0),
        )
        val run = PortfolioMetrics.of(balances).longestDrawdown!!
        assertEquals(3, run.trades)
        assertTrue(run.recovered)
    }

    @Test
    fun `Sortino divides by the count of all returns and not by the count of the losing ones`() {
        val returns = listOf(30.0, 10.0, -10.0, -20.0)
        // Mean 2.5. Shortfall squares 100 + 400 = 500, over four returns, so the downside deviation
        // is sqrt(125) = 11.1803 and Sortino is 0.2236.
        val correct = 2.5 / sqrt(500.0 / 4)
        // Over the two losing returns instead it would be sqrt(250) = 15.8114 and Sortino would
        // read 0.1581 — the flattering version, roughly forty per cent higher on this fixture and
        // several times higher on a history with rare losses.
        val flattering = 2.5 / sqrt(500.0 / 2)

        val sortino = PortfolioMetrics.sortino(returns)!!
        assertEquals(0.2236068, correct, 1e-6)
        assertEquals(correct, sortino, 1e-9)
        assertNotEquals(flattering, sortino, 1e-6)
    }

    @Test
    fun `Sharpe uses the same divisor as Sortino so the two can be read against each other`() {
        val returns = listOf(30.0, 10.0, -10.0, -20.0)
        // Population deviation: 1475 / 4 = 368.75, root 19.2029, so Sharpe is 0.1302.
        val expected = 2.5 / sqrt(368.75)
        assertEquals(0.1301891, expected, 1e-6)
        assertEquals(expected, PortfolioMetrics.sharpe(returns)!!, 1e-9)
    }

    @Test
    fun `a ratio over a single trade or over an unvarying set has no denominator`() {
        assertNull(PortfolioMetrics.sharpe(listOf(10.0)))
        assertNull(PortfolioMetrics.sortino(listOf(10.0)))
        assertNull(PortfolioMetrics.sharpe(listOf(10.0, 10.0, 10.0)))
        // Every return above the target, so there is no shortfall to divide by.
        assertNull(PortfolioMetrics.sortino(listOf(10.0, 20.0, 30.0)))
    }

    @Test
    fun `a break even trade breaks a winning streak rather than being skipped over`() {
        val streak = listOf(
            trade("1", 1.0, base + day),
            trade("2", 1.0, base + 2 * day),
            trade("3", 0.0, base + 3 * day),
            trade("4", 1.0, base + 4 * day),
            trade("5", 1.0, base + 5 * day),
        )
        val metrics = PortfolioMetrics.of(streak)
        assertEquals(2, metrics.longestWinStreak)
        assertEquals(0, metrics.longestLossStreak)
        assertEquals(1, metrics.scratches)
        assertEquals(4, metrics.wins)
        // The scratch is a trade that was taken, so it stays in the count and in the expectancy.
        assertEquals(5, metrics.trades)
        assertEquals(100.0, metrics.winRate!!, 1e-9)
    }

    @Test
    fun `consecutive losses are counted over adjacent trades in close order`() {
        val metrics = PortfolioMetrics.of(walk)
        assertEquals(2, metrics.longestLossStreak)
        assertEquals(1, metrics.longestWinStreak)
    }

    @Test
    fun `the holding time is averaged only over the trades that have an open time`() {
        val held = listOf(
            trade("1", 10.0, base + day, openedAt = base + day - 3_600),
            trade("2", 10.0, base + 2 * day, openedAt = base + 2 * day - 7_200),
            // TradeYar routinely sends this: the opening leg fell before the window it could read.
            trade("3", 10.0, base + 3 * day, openedAt = null),
        )
        val metrics = PortfolioMetrics.of(held)
        assertEquals(5_400L, metrics.averageHoldingSeconds!!)
        assertEquals(2, metrics.holdingSample)
        assertEquals(3, metrics.trades)
    }

    @Test
    fun `the curve is built in close order however the server sent the trades`() {
        val metrics = PortfolioMetrics.of(walk.reversed())
        assertEquals(listOf(100.0, 60.0, 30.0, 40.0, -10.0), metrics.equity.map { it.equity })
        assertEquals(110.0, metrics.maxDrawdown, 1e-9)
    }

    @Test
    fun `attribution ranks symbols by how much they moved the result, not alphabetically`() {
        val mixed = listOf(
            trade("1", 10.0, base + day, symbol = "AAA"),
            trade("2", -500.0, base + 2 * day, symbol = "BBB"),
            trade("3", 200.0, base + 3 * day, symbol = "CCC"),
        )
        val rows = PortfolioMetrics.attribution(mixed)
        assertEquals(listOf("BBB", "CCC", "AAA"), rows.map { it.symbol })
        assertEquals(-500.0, rows[0].net, 1e-9)
        // The loser accounts for the whole of the loss pool; the two winners split the win pool.
        assertEquals(100.0, rows[0].share!!, 1e-9)
        assertEquals(200.0 / 210.0 * 100.0, rows[1].share!!, 1e-9)
        assertEquals(10.0 / 210.0 * 100.0, rows[2].share!!, 1e-9)
    }

    @Test
    fun `a symbol that netted exactly nothing has no share of either pool`() {
        val scratched = listOf(
            trade("1", 50.0, base + day, symbol = "AAA"),
            trade("2", -50.0, base + 2 * day, symbol = "AAA"),
        )
        val row = PortfolioMetrics.attribution(scratched).single()
        assertEquals(0.0, row.net, 1e-9)
        assertNull(row.share)
        assertEquals(2, row.trades)
        assertEquals(1, row.wins)
        assertEquals(1, row.losses)
        assertEquals(50.0, row.winRate!!, 1e-9)
    }

    @Test
    fun `the running peak never falls and marks every new high`() {
        assertEquals(
            listOf(100.0, 100.0, 100.0, 100.0, 140.0),
            runningPeaks(listOf(100.0, 60.0, 30.0, 40.0, 140.0)),
        )
        assertTrue(runningPeaks(emptyList()).isEmpty())
    }

    @Test
    fun `a duration is split into whole days hours and minutes`() {
        val parts = durationParts(2 * 86_400 + 3 * 3_600 + 14 * 60 + 9)
        assertEquals(2L, parts.days)
        assertEquals(3L, parts.hours)
        assertEquals(14L, parts.minutes)
        assertEquals(9L, parts.seconds)
    }

    @Test
    fun `a negative duration is clamped rather than rendered with a minus sign inside it`() {
        val parts = durationParts(-500)
        assertEquals(0L, parts.days)
        assertEquals(0L, parts.hours)
        assertEquals(0L, parts.minutes)
        assertEquals(0L, parts.seconds)
    }
}
