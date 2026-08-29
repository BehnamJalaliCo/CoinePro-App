package com.coinepro.core.papertrade

import com.coinepro.core.portfolio.ClosedTrade
import com.coinepro.core.portfolio.MonthlyPerformance
import com.coinepro.core.portfolio.PortfolioMath
import com.coinepro.core.portfolio.PortfolioStats
import com.coinepro.core.portfolio.SymbolPerformance
import com.coinepro.core.portfolio.TradeDirection
import java.time.ZoneId

/**
 * What the paper account has actually done, computed by the portfolio's own arithmetic.
 *
 * Not one line of win rate, profit factor, expectancy or drawdown is written here. `PortfolioMath`
 * already has every one of them, it is already the thing that decides what a break-even trade
 * counts as, and it is already tested against a fixture somebody worked out by hand. A second
 * implementation in this module would disagree with the portfolio screen by a rounding rule, and
 * nobody would be able to say which of the two numbers under the word «نرخ برد» was the real one.
 *
 * The one thing this file does is translate. A [PaperClosedTrade] becomes a [ClosedTrade] with its
 * costs in the fields that mean costs, and — this is the part worth the trouble — with
 * [ClosedTrade.balanceAfter] filled in on every row. `PortfolioMath` draws a real balance curve
 * only when every trade in the window carries one, and a paper account always knows its own
 * balance. So the paper screen gets the better of the two curves and a drawdown *percentage*,
 * which the live crypto side cannot have because LBank keeps no balance history at all.
 */
data class PaperRecord(
    val stats: PortfolioStats = PortfolioStats(),
    val bySymbol: List<SymbolPerformance> = emptyList(),
    val byMonth: List<MonthlyPerformance> = emptyList(),
    /** Everything the simulation charged, across the window. Fees, spread and slippage together. */
    val costs: Double = 0.0,
) {
    /** Average winner. Null with no winners, which is not a zero average. */
    val averageWin: Double? get() = stats.wins.takeIf { it > 0 }?.let { stats.grossWin / it }

    /** Average loser, as a positive magnitude. Null with no losers. */
    val averageLoss: Double? get() = stats.losses.takeIf { it > 0 }?.let { stats.grossLoss / it }

    /**
     * Average win over average loss.
     *
     * Read beside the win rate and never instead of it: a system that wins a third of the time at
     * four to one is a good system, and either number alone says the opposite of the truth.
     */
    val payoff: Double? get() {
        val win = averageWin ?: return null
        val loss = averageLoss ?: return null
        return if (loss > 0.0) win / loss else null
    }
}

object PaperRecordMath {

    fun of(
        closed: List<PaperClosedTrade>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): PaperRecord {
        if (closed.isEmpty()) return PaperRecord()
        val trades = closed.map(::asClosedTrade)
        return PaperRecord(
            stats = PortfolioMath.summarise(trades),
            bySymbol = PortfolioMath.bySymbol(trades),
            byMonth = PortfolioMath.byMonth(trades, zone),
            costs = closed.sumOf { it.fees },
        )
    }

    /**
     * One paper round trip in the shape the portfolio arithmetic reads.
     *
     * The commission is written negative because that is the convention `ClosedTrade` documents —
     * CoinePro-FX stores both costs signed, and `costs` there is a plain sum. Writing the paper
     * fee positive would make the one screen that shows gross and costs together report a cost that
     * had been added to the profit.
     */
    fun asClosedTrade(trade: PaperClosedTrade): ClosedTrade = ClosedTrade(
        id = trade.id.toString(),
        symbol = trade.symbol,
        direction = if (trade.side == PaperSide.BUY) TradeDirection.BUY else TradeDirection.SELL,
        volume = trade.size,
        entry = trade.entry,
        exit = trade.exit,
        openedAt = trade.openedAtEpochMillis / 1000L,
        closedAt = trade.closedAtEpochMillis / 1000L,
        grossProfit = trade.gross,
        commission = -trade.fees,
        swap = null,
        netProfit = trade.net,
        closeReason = trade.reason.id,
        balanceAfter = trade.balanceAfter,
    )
}
