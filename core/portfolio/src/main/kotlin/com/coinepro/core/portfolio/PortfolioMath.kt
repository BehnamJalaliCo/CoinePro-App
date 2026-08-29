package com.coinepro.core.portfolio

import com.coinepro.core.common.JalaliDate
import java.time.Instant
import java.time.ZoneId

/**
 * The arithmetic behind the portfolio screen.
 *
 * Pure functions over a list of closed trades, deliberately: this is the part that has to be right,
 * and every one of these numbers is one a reader will make a decision on. A unit test can hold a
 * fixture of twelve trades and check the win rate by hand; a screen cannot.
 *
 * Break-even is the case that gets these wrong, and it appears three times. A trade with exactly
 * zero net profit is neither a win nor a loss, does not enter the profit factor, and still counts
 * as a trade. CoinePro-FX's own `/stats` agrees on the first — it filters on `> 0` and `< 0` — and
 * this matches it rather than inventing a third convention.
 */
object PortfolioMath {

    /**
     * Roll up a window of trades.
     *
     * The trades may arrive in any order; the curve needs them oldest first, so they are sorted
     * here rather than trusting either server's ordering. TradeYar sends newest first and
     * CoinePro-FX sends pages, and a curve drawn backwards is a curve that says the account fell.
     */
    fun summarise(trades: List<ClosedTrade>): PortfolioStats {
        if (trades.isEmpty()) return PortfolioStats()
        val ordered = trades.sortedBy { it.closedAt }

        var wins = 0
        var losses = 0
        var net = 0.0
        var grossWin = 0.0
        var grossLoss = 0.0
        var gross: Double? = null
        var costs: Double? = null
        var best: Double? = null
        var worst: Double? = null

        for (trade in ordered) {
            val profit = trade.netProfit ?: 0.0
            net += profit
            when {
                profit > 0.0 -> { wins++; grossWin += profit }
                profit < 0.0 -> { losses++; grossLoss += -profit }
            }
            if (best == null || profit > best) best = profit
            if (worst == null || profit < worst) worst = profit
            trade.grossProfit?.let { gross = (gross ?: 0.0) + it }
            trade.costs?.let { costs = (costs ?: 0.0) + it }
        }

        // A balance curve only when *every* trade carries one. A line stitched from some real
        // balances and some running totals has a step in it that means nothing at all.
        val balanced = ordered.all { it.balanceAfter != null }
        val curve = if (balanced) {
            ordered.map { EquityPoint(it.closedAt, it.balanceAfter!!) }
        } else {
            var running = 0.0
            ordered.map { trade ->
                running += trade.netProfit ?: 0.0
                EquityPoint(trade.closedAt, running)
            }
        }

        return PortfolioStats(
            trades = ordered.size,
            wins = wins,
            losses = losses,
            net = net,
            gross = gross,
            costs = costs,
            best = best,
            worst = worst,
            grossWin = grossWin,
            grossLoss = grossLoss,
            equity = curve,
            equityIsBalance = balanced,
        )
    }

    /**
     * Per-symbol attribution, worst first.
     *
     * Worst first because the list is read to find what is losing money. Sorted by net rather than
     * by trade count: twenty small winners are less interesting than one large loser, and a list
     * ordered by activity buries exactly the row somebody opened this screen to find.
     */
    fun bySymbol(trades: List<ClosedTrade>): List<SymbolPerformance> =
        trades.groupBy { it.symbol }
            .map { (symbol, rows) ->
                SymbolPerformance(
                    symbol = symbol,
                    trades = rows.size,
                    wins = rows.count { it.isWin },
                    net = rows.sumOf { it.netProfit ?: 0.0 },
                )
            }
            .sortedBy { it.net }

    /**
     * Calendar months, oldest first, with the gaps filled in.
     *
     * The months are **Solar Hijri**, not Gregorian, and that is a correctness decision rather than
     * a cosmetic one. A reader asking how last month went means Mordad, which starts on the 23rd of
     * July and ends on the 22nd of August; bucketing their trades into August and then labelling
     * the bucket «مرداد» would put three weeks of the wrong month under that bar. The two calendars
     * do not line up anywhere, so either the grouping moves or the label lies.
     *
     * A month with no trades is a bar of zero rather than a missing column, because a bar chart
     * that silently omits quiet months compresses time and makes a two-month gap look like a
     * consecutive pair.
     */
    fun byMonth(trades: List<ClosedTrade>, zone: ZoneId = ZoneId.systemDefault()): List<MonthlyPerformance> {
        if (trades.isEmpty()) return emptyList()
        // A trade whose close date this calendar cannot represent is dropped from the breakdown
        // rather than throwing. `closedAt` is a server value, and the throwing conversion here ran
        // inside `PortfolioController`'s `onSuccess` — outside its `runCatching`, on the app's one
        // shared scope, which has no exception handler — so a single hostile timestamp in a page
        // of trades killed the process rather than losing one bar. See `JalaliDate.fromInstantOrNull`.
        val keyed = trades
            .mapNotNull { trade ->
                JalaliDate.fromInstantOrNull(Instant.ofEpochSecond(trade.closedAt), zone)
                    ?.let { date -> trade to (date.year to date.month) }
            }
            .groupBy({ it.second }, { it.first })
        if (keyed.isEmpty()) return emptyList()
        val first = keyed.keys.minWith(compareBy({ it.first }, { it.second }))
        val last = keyed.keys.maxWith(compareBy({ it.first }, { it.second }))

        val out = ArrayList<MonthlyPerformance>()
        var year = first.first
        var month = first.second
        while (year < last.first || (year == last.first && month <= last.second)) {
            val rows = keyed[year to month].orEmpty()
            out += MonthlyPerformance(
                year = year,
                month = month,
                trades = rows.size,
                net = rows.sumOf { it.netProfit ?: 0.0 },
            )
            month++
            if (month > 12) {
                month = 1
                year++
            }
        }
        return out
    }
}
