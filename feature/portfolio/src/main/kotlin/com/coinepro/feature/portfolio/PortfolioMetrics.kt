package com.coinepro.feature.portfolio

import com.coinepro.core.portfolio.ClosedTrade
import com.coinepro.core.portfolio.EquityPoint
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The full report a trader is normally charged for.
 *
 * TradingView's free tier gives a strategy report "basic metrics" and puts the rest — the
 * risk-adjusted ratios, the streaks, the drawdown anatomy, and any export at all — behind Essential.
 * Everything here is given away instead, and that is a decision rather than an oversight: these are
 * fifteen lines of arithmetic over a list a reader already owns, and charging for arithmetic on
 * somebody else's trading history is not a business this app is in. Keep it that way.
 *
 * This sits beside `PortfolioMath` rather than inside it. `PortfolioMath` computes what the summary
 * card shows and is deliberately small; this computes what the report page shows, which is a
 * different and much longer list, and folding the two together would put a Sortino ratio on the
 * critical path of the screen that only wants a net total.
 *
 * Every function is pure and every figure is hand-checkable. That is the whole reason for the
 * shape: a fixture of six trades in `PortfolioMetricsTest` can be added up on paper, so a failure
 * names the wrong number instead of announcing that something changed.
 */
object PortfolioMetrics {

    /**
     * Roll a window of closed trades into the whole report.
     *
     * [target] is the return a trade has to beat before it counts as an excess return. It is the
     * risk-free rate for [TradeMetrics.sharpe] and the minimum acceptable return for
     * [TradeMetrics.sortino], and it defaults to zero because these are per-trade currency results
     * rather than periodic percentage returns — there is no interval to accrue a risk-free rate
     * over. Pass a non-zero value only if you also intend to explain it on screen.
     *
     * Trades arrive in whatever order the server sent them — TradeYar newest first, CoinePro-FX in
     * pages — and every sequential figure below (the curve, the drawdown, the streaks) is wrong if
     * they are read in that order, so they are sorted by close time here rather than trusted.
     */
    fun of(trades: List<ClosedTrade>, target: Double = 0.0): TradeMetrics {
        if (trades.isEmpty()) return TradeMetrics()
        val ordered = trades.sortedBy { it.closedAt }

        var wins = 0
        var losses = 0
        var scratches = 0
        var grossWin = 0.0
        var grossLoss = 0.0
        var largestWin: Double? = null
        var largestLoss: Double? = null
        var heldSeconds = 0L
        var heldSample = 0
        var currentWins = 0
        var currentLosses = 0
        var longestWinStreak = 0
        var longestLossStreak = 0

        for (trade in ordered) {
            val profit = trade.netProfit ?: 0.0
            when {
                profit > 0.0 -> {
                    wins++
                    grossWin += profit
                    if (largestWin == null || profit > largestWin) largestWin = profit
                    currentWins++
                    currentLosses = 0
                }
                profit < 0.0 -> {
                    losses++
                    grossLoss += -profit
                    if (largestLoss == null || profit < largestLoss) largestLoss = profit
                    currentLosses++
                    currentWins = 0
                }
                // A scratch breaks both runs. "Four wins in a row" is a claim about four adjacent
                // trades, and a break-even trade sitting between two winners is a trade that did
                // not win — skipping over it would let a streak be reported that never happened.
                else -> {
                    scratches++
                    currentWins = 0
                    currentLosses = 0
                }
            }
            if (currentWins > longestWinStreak) longestWinStreak = currentWins
            if (currentLosses > longestLossStreak) longestLossStreak = currentLosses
            trade.durationSeconds?.let { seconds ->
                if (seconds >= 0) {
                    heldSeconds += seconds
                    heldSample++
                }
            }
        }

        val returns = ordered.map { it.netProfit ?: 0.0 }
        val net = returns.sum()
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

        return TradeMetrics(
            trades = ordered.size,
            wins = wins,
            losses = losses,
            scratches = scratches,
            net = net,
            grossWin = grossWin,
            grossLoss = grossLoss,
            largestWin = largestWin,
            largestLoss = largestLoss,
            averageHoldingSeconds = if (heldSample == 0) null else heldSeconds / heldSample,
            holdingSample = heldSample,
            longestWinStreak = longestWinStreak,
            longestLossStreak = longestLossStreak,
            sharpe = sharpe(returns, target),
            sortino = sortino(returns, target),
            equity = curve,
            equityIsBalance = balanced,
            drawdown = deepestDrawdown(curve, balanced),
            longestDrawdown = longestDrawdown(curve),
        )
    }

    /**
     * Which instruments moved the result, largest mover first.
     *
     * Ordered by the **magnitude** of the contribution rather than by its sign, which is what makes
     * this an attribution table rather than a leaderboard: the question it answers is "what moved
     * the number", and the single symbol that lost four thousand belongs at the top next to the one
     * that made four thousand, not buried under nine symbols that made forty each. `PortfolioMath`
     * orders its own summary list worst-first because that list answers a different question —
     * "what is bleeding" — and the two are deliberately not the same order.
     *
     * Alphabetical would be the one order that carries no information at all, and it is the order a
     * `groupBy` gives you for free, which is why it has to be ruled out explicitly.
     */
    fun attribution(trades: List<ClosedTrade>): List<SymbolAttribution> {
        if (trades.isEmpty()) return emptyList()
        val grossWin = trades.sumOf { (it.netProfit ?: 0.0).coerceAtLeast(0.0) }
        val grossLoss = trades.sumOf { -(it.netProfit ?: 0.0).coerceAtMost(0.0) }
        return trades.groupBy { it.symbol }
            .map { (symbol, rows) ->
                val net = rows.sumOf { it.netProfit ?: 0.0 }
                SymbolAttribution(
                    symbol = symbol,
                    trades = rows.size,
                    wins = rows.count { it.isWin },
                    losses = rows.count { it.isLoss },
                    net = net,
                    // Share of the pool this symbol drew from — winners against the gross win,
                    // losers against the gross loss. Dividing both by the net total instead is the
                    // usual mistake: a net near zero makes every share enormous, and a negative net
                    // flips the sign of every winner's contribution.
                    share = when {
                        net > 0.0 && grossWin > 0.0 -> net / grossWin * 100.0
                        net < 0.0 && grossLoss > 0.0 -> -net / grossLoss * 100.0
                        else -> null
                    },
                )
            }
            .sortedWith(compareByDescending<SymbolAttribution> { abs(it.net) }.thenBy { it.symbol })
    }

    /**
     * Mean excess return over the standard deviation of returns.
     *
     * This is a **per-trade** Sharpe on currency results, not the annualised return-on-equity
     * figure a fund publishes, and the two are not comparable — an annualised Sharpe multiplies by
     * the square root of the number of periods in a year, and there is no such number for a trader
     * who took forty trades one week and none the next. Printed beside the app's own history it
     * says what it should: whether the wins were large relative to how much the results scattered.
     *
     * The deviation divides by the count of all returns rather than by one less. That matches
     * [sortino]'s divisor exactly, which is the only way the two ratios can be read against each
     * other; a sample deviation here and a population one there would make Sortino look better than
     * Sharpe on every fixture by construction.
     *
     * Null below two returns, and null when every return is identical. A ratio with a zero
     * denominator is not infinity, it is a set of trades that has not yet produced a spread.
     */
    fun sharpe(returns: List<Double>, target: Double = 0.0): Double? {
        if (returns.size < 2) return null
        val mean = returns.average()
        val variance = returns.sumOf { (it - mean) * (it - mean) } / returns.size
        val deviation = sqrt(variance)
        return if (deviation <= 0.0) null else (mean - target) / deviation
    }

    /**
     * Mean excess return over the downside deviation.
     *
     * **The divisor is the trap, and it is the reason this function exists rather than a one-liner
     * at the call site.** The downside deviation is the root mean square of the shortfalls, and the
     * mean is taken over *every* return, not over the losing ones:
     *
     *     downside = sqrt( sum over all returns of min(r - target, 0)^2  /  count of ALL returns )
     *
     * Dividing by the count of the negative returns instead is the standard way a published Sortino
     * ends up flattering. It is not a rounding difference: a strategy that loses twice in twenty
     * trades gets a divisor ten times too large under the correct rule and would report a Sortino
     * roughly three times higher under the wrong one, which is exactly the regime — rare, shallow
     * losses — that a Sortino ratio is quoted to advertise.
     *
     * Null with no shortfall at all, for the same reason [sharpe] is null with no spread: a run
     * without a single losing trade has not achieved an infinite downside-adjusted return, it has
     * not yet produced a denominator.
     */
    fun sortino(returns: List<Double>, target: Double = 0.0): Double? {
        if (returns.size < 2) return null
        val mean = returns.average()
        val shortfall = returns.sumOf {
            val below = (it - target).coerceAtMost(0.0)
            below * below
        }
        // The divisor: every return, including the winners, which contribute zero to the numerator
        // of this fraction but still count in it.
        val downside = sqrt(shortfall / returns.size)
        return if (downside <= 0.0) null else (mean - target) / downside
    }

    /**
     * The deepest fall from a running peak, with the span that produced it.
     *
     * **Peak to trough on the equity curve, and not the largest losing trade.** They are different
     * numbers and confusing them understates the risk badly: five losses of two hundred each, taken
     * in a row, is a thousand-dollar drawdown and a two-hundred-dollar worst trade. A reader sizing
     * their account off the second figure is sizing it off a fifth of what actually happened. The
     * largest losing trade is reported separately as [TradeMetrics.largestLoss] precisely so the two
     * can never be read as the same statistic.
     *
     * The percentage is returned only when the curve is real account balance. On a profit-from-zero
     * curve the peak can be a few dollars, and dividing by it produces the sort of figure
     * CoinePro-FX's own report prints — 312% — which is arithmetically correct and completely
     * meaningless.
     */
    fun deepestDrawdown(curve: List<EquityPoint>, curveIsBalance: Boolean): DrawdownSpan? {
        if (curve.size < 2) return null
        var peakIndex = 0
        var peak = curve.first().equity
        var deepest: DrawdownSpan? = null
        curve.forEachIndexed { index, point ->
            if (point.equity > peak) {
                peak = point.equity
                peakIndex = index
            }
            val fall = peak - point.equity
            if (fall > 0.0 && fall > (deepest?.depth ?: 0.0)) {
                deepest = DrawdownSpan(
                    peakIndex = peakIndex,
                    troughIndex = index,
                    peakAt = curve[peakIndex].time,
                    troughAt = point.time,
                    peakEquity = peak,
                    troughEquity = point.equity,
                    depth = fall,
                    depthPercent = if (curveIsBalance && abs(peak) > 0.0) fall / abs(peak) * 100.0 else null,
                )
            }
        }
        return deepest
    }

    /**
     * The longest stretch spent below a previous high, in trades and in seconds.
     *
     * A different question from [deepestDrawdown] and often a more useful one. The deepest fall
     * says how much was given back; this says how long the account sat under water before it made
     * a new high — which is the number that decides whether a strategy is survivable, because it is
     * the length of time a reader has to keep taking its signals while the curve tells them they
     * were wrong.
     *
     * A run still under water when the history ends is counted to the last trade rather than
     * discarded. It is the run the reader is currently in, and it is the one they care about most.
     */
    fun longestDrawdown(curve: List<EquityPoint>): DrawdownRun? {
        if (curve.size < 2) return null
        var peakIndex = 0
        var peak = curve.first().equity
        var underwater = false
        var longest: DrawdownRun? = null

        fun close(endIndex: Int, recovered: Boolean) {
            val run = DrawdownRun(
                startIndex = peakIndex,
                endIndex = endIndex,
                startAt = curve[peakIndex].time,
                endAt = curve[endIndex].time,
                trades = endIndex - peakIndex,
                seconds = curve[endIndex].time - curve[peakIndex].time,
                recovered = recovered,
            )
            if (run.trades > (longest?.trades ?: 0)) longest = run
        }

        for (index in 1 until curve.size) {
            if (curve[index].equity >= peak) {
                if (underwater) close(index, recovered = true)
                peak = curve[index].equity
                peakIndex = index
                underwater = false
            } else {
                underwater = true
            }
        }
        if (underwater) close(curve.lastIndex, recovered = false)
        return longest
    }
}

/**
 * One symbol's share of the result.
 *
 * Separate from `core:portfolio`'s `SymbolPerformance` because it answers a different question and
 * carries a field that one cannot: [share], the proportion of the winning or the losing pool this
 * instrument accounts for. Merging the two would mean either putting a report-only figure on the
 * summary card's model or computing it twice.
 */
data class SymbolAttribution(
    val symbol: String,
    val trades: Int,
    val wins: Int,
    val losses: Int,
    /** Signed. Positive symbols made money over the window, negative ones lost it. */
    val net: Double,
    /**
     * Percent of the gross win this symbol supplied, or of the gross loss it caused.
     *
     * Always positive, and read together with the sign of [net] rather than instead of it. Null on
     * a symbol that netted exactly zero, and on a window where the relevant pool is empty.
     */
    val share: Double?,
) {
    /** Wins over decided trades here. Null where every trade on this symbol was a scratch. */
    val winRate: Double? get() = (wins + losses).takeIf { it > 0 }?.let { wins.toDouble() / it * 100.0 }
}

/**
 * A single peak-to-trough fall on the equity curve.
 *
 * Held as a span rather than as one number because a curve hides it. A reader looking at a line
 * that ends higher than it started does not see the eleven trades in the middle where it gave back
 * a third of the account, and the depth alone does not tell them where to look. The indices are
 * what lets the chart shade the exact stretch.
 */
data class DrawdownSpan(
    /** Index into the equity curve of the high the fall started from. */
    val peakIndex: Int,
    /** Index of the low it reached. Always at or after [peakIndex]. */
    val troughIndex: Int,
    val peakAt: Long,
    val troughAt: Long,
    val peakEquity: Double,
    val troughEquity: Double,
    /** Positive magnitude, in account currency. */
    val depth: Double,
    /** Only on a real balance curve — see `PortfolioMetrics.deepestDrawdown`. */
    val depthPercent: Double?,
) {
    /** How long the fall itself took, in seconds. Not how long recovery took. */
    val seconds: Long get() = troughAt - peakAt

    /** How many trades it took to fall. */
    val trades: Int get() = troughIndex - peakIndex
}

/**
 * One stretch spent below a previous high.
 *
 * [recovered] is the field that changes how the run should be read: a closed run is history, and an
 * open one is where the account is standing right now.
 */
data class DrawdownRun(
    val startIndex: Int,
    val endIndex: Int,
    val startAt: Long,
    val endAt: Long,
    val trades: Int,
    val seconds: Long,
    /** False when the history ends with the account still below the high it started from. */
    val recovered: Boolean,
)

/**
 * Every figure the report page prints.
 *
 * Nullable throughout wherever the figure genuinely has no value on the given history, and never
 * defaulted to zero to make the type simpler. Zero is a claim — a win rate of nought per cent, a
 * profit factor of nothing — and on an empty or one-sided window every one of those claims is
 * false. The screen prints an em dash for a null, which is the only honest rendering of "there is
 * not enough history to say".
 */
data class TradeMetrics(
    val trades: Int = 0,
    val wins: Int = 0,
    val losses: Int = 0,
    /** Trades that closed at exactly zero. Neither a win nor a loss, and in neither denominator. */
    val scratches: Int = 0,
    val net: Double = 0.0,
    /** The winners added up, and the losers added up as a positive magnitude. */
    val grossWin: Double = 0.0,
    val grossLoss: Double = 0.0,
    val largestWin: Double? = null,
    /** Signed, so it reads as the loss it was. Not to be confused with the drawdown. */
    val largestLoss: Double? = null,
    /**
     * Mean seconds a position stayed open.
     *
     * Over [holdingSample], not over [trades]. A TradeYar trade whose opening leg fell outside the
     * requested window has no open time at all, and averaging those in as zero would report a
     * scalping habit to somebody who holds for days.
     */
    val averageHoldingSeconds: Long? = null,
    /** How many trades the holding-time average could actually be taken over. */
    val holdingSample: Int = 0,
    val longestWinStreak: Int = 0,
    val longestLossStreak: Int = 0,
    val sharpe: Double? = null,
    val sortino: Double? = null,
    val equity: List<EquityPoint> = emptyList(),
    /** True only when every trade carried a broker balance. See `PortfolioMath.summarise`. */
    val equityIsBalance: Boolean = false,
    val drawdown: DrawdownSpan? = null,
    val longestDrawdown: DrawdownRun? = null,
) {
    /** Wins over decided trades. A scratch is in neither column and in neither total. */
    val winRate: Double? get() = (wins + losses).takeIf { it > 0 }?.let { wins.toDouble() / it * 100.0 }

    /** Gross win over gross loss. Null rather than infinity where nothing has been lost yet. */
    val profitFactor: Double? get() = grossLoss.takeIf { it > 0.0 }?.let { grossWin / it }

    /** Average net per trade, scratches included — they are trades that were taken. */
    val expectancy: Double? get() = trades.takeIf { it > 0 }?.let { net / it }

    val averageWin: Double? get() = wins.takeIf { it > 0 }?.let { grossWin / it }

    /** Positive magnitude, so it reads beside [averageWin] rather than against it. */
    val averageLoss: Double? get() = losses.takeIf { it > 0 }?.let { grossLoss / it }

    /**
     * Average win over average loss — the payoff ratio.
     *
     * Deliberately not the win rate's companion figure by accident: a system can be right a third
     * of the time and profitable, and this is the number that says by how much. Null where either
     * side is missing, because a ratio against nothing is not a large ratio.
     */
    val winLossRatio: Double?
        get() {
            val up = averageWin ?: return null
            val down = averageLoss ?: return null
            return if (down > 0.0) up / down else null
        }

    /** Positive magnitude of the deepest peak-to-trough fall, or zero on a curve that never fell. */
    val maxDrawdown: Double get() = drawdown?.depth ?: 0.0

    /** Only on a real balance curve. Null everywhere else, on purpose. */
    val maxDrawdownPercent: Double? get() = drawdown?.depthPercent
}
