package com.coinepro.core.chart

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * One completed round trip, and the excursions it went through on the way.
 *
 * A trade is stored with both its endpoints resolved — index, time and price for entry and for
 * exit — rather than as an open position with a nullable exit, because everything below reads a
 * trade only after it has closed and a nullable exit would make every one of those reads defensive.
 * The open position the runner carries while it walks the bars is expressed as a trade too, marked
 * to the current close, so a [Strategy] can read `pnl` or `drawdown` on it and decide to get out
 * without the strategy having to know about a second type.
 *
 * [highestHigh] and [lowestLow] are the extremes of the *bars* held, not of their closes, and that
 * is the whole reason they are stored rather than recomputed from the two prices. They default to
 * the entry and exit prices only so that a trade built by hand in a test is still coherent; the
 * runner always fills them from the bar highs and lows it walked.
 *
 * [size] is in units of the instrument and [fee] is the whole round trip in quote currency, both
 * sides already added together. Charging one number once is what keeps [pnl] a subtraction rather
 * than a small argument about which side a fee belonged to.
 */
data class Trade(
    val entryIndex: Int,
    val entryTime: Long,
    val entryPrice: Double,
    val exitIndex: Int,
    val exitTime: Long,
    val exitPrice: Double,
    val isLong: Boolean,
    val size: Double,
    val fee: Double,
    /** Highest high of every bar held, inclusive of the entry and exit bars. */
    val highestHigh: Double = max(entryPrice, exitPrice),
    /** Lowest low of every bar held, inclusive of the entry and exit bars. */
    val lowestLow: Double = min(entryPrice, exitPrice),
) {

    /** `+1` long, `-1` short. Every price difference below is multiplied by it. */
    val direction: Int get() = if (isLong) 1 else -1

    /** What the position was worth at entry, which is the base every percentage here divides by. */
    val notional: Double get() = entryPrice * size

    /** Profit before costs. Shown nowhere on its own; it exists so [pnl] reads as one subtraction. */
    val grossPnl: Double get() = (exitPrice - entryPrice) * direction * size

    /**
     * What the trade actually made, after the round-trip fee.
     *
     * The fee is subtracted here rather than reported beside the profit because a strategy that
     * flips often is profitable gross and ruinous net, and a reader comparing two strategies must
     * not have to remember which of the two numbers they were looking at.
     */
    val pnl: Double get() = grossPnl - fee

    /**
     * [pnl] as a percentage of the entry notional.
     *
     * Not a percentage of account equity, and the difference matters: on a small position this
     * number can be large while the account barely moved. Read it next to [pnl], never instead
     * of it. Zero rather than a division by zero when the notional is zero.
     */
    val pnlPercent: Double get() = if (notional > 0) pnl / notional * 100 else 0.0

    /**
     * How many bars the position was held.
     *
     * Bars, not time, because that is what the strategy experienced; converting to hours needs the
     * timeframe and hides that a weekend passed inside a single daily bar.
     */
    val barsHeld: Int get() = max(0, exitIndex - entryIndex)

    /** A scratch trade — exactly zero after fees — counts as neither a win nor a loss. */
    val isWin: Boolean get() = pnl > 0
    val isLoss: Boolean get() = pnl < 0

    /**
     * Maximum favourable excursion: the best this trade ever looked, in quote currency.
     *
     * Computed from [highestHigh] for a long and [lowestLow] for a short — from the bar extremes,
     * because a trade that ran fifty points in your favour intrabar and closed flat *did* run fifty
     * points, and a run-up computed from closes would report nothing happened. This is the number
     * that says a target was left on the table.
     *
     * Where it misleads: it is an upper bound nobody could have captured reliably, since it assumes
     * the exit landed on the exact extreme. Treat a large run-up beside a small [pnl] as a question
     * about the exit rule, not as money that was lost.
     */
    val runUp: Double
        get() = max(0.0, (if (isLong) highestHigh - entryPrice else entryPrice - lowestLow) * size)

    /**
     * Maximum adverse excursion: the worst this trade ever looked, in quote currency.
     *
     * The mirror of [runUp], and the more useful of the two: a winning trade whose drawdown was
     * three times the stop distance is a trade that only survived because the stop was not where
     * the reader thought it was. This is what tells a trader their stop was too tight — or that it
     * was never tested.
     *
     * Where it misleads: it is bar-resolution. Inside one bar the sequence of high and low is
     * unknown, so on a wide bar this can overstate what a live position would have shown between
     * two ticks.
     */
    val drawdown: Double
        get() = max(0.0, (if (isLong) entryPrice - lowestLow else highestHigh - entryPrice) * size)
}

/**
 * What a strategy wants to do at the close of one bar.
 *
 * Three cases and no more. A rule that wants to reverse emits [Exit] and then [Enter] on the next
 * bar it is asked, which costs one bar and is honest: a reversal is two fills and two fees, and a
 * signal type that hid that would make every reversing strategy look cheaper than it is.
 */
sealed interface Signal {

    /** Do nothing. The overwhelmingly common answer, and the reason it carries no data. */
    data object Hold : Signal

    /**
     * Open a position, filled at the *next* bar's open.
     *
     * [size] is in units of the instrument. It is a plain number rather than a fraction of equity
     * because position sizing is a separate decision from entry timing, and mixing them makes a
     * backtest that compounds silently — which flatters every strategy that happens to win early.
     */
    data class Enter(val isLong: Boolean, val size: Double = 1.0) : Signal

    /** Close the open position, filled at the next bar's open. Ignored when flat. */
    data object Exit : Signal
}

/**
 * A rule, asked once per bar.
 *
 * A `fun interface` so a rule can be a lambda: most of them are three lines over an indicator array
 * and giving each one a named class would bury the rule in ceremony.
 *
 * The whole [CandleSeries] is passed rather than the bars up to [Trade.entryIndex], because
 * indicators are computed over arrays and slicing per bar would turn an O(n) run into O(n²). The
 * trap that follows is the one every backtest dies of: nothing stops a rule reading
 * `series.close[index + 1]`, and a rule that does will be perfect and worthless. Read at or before
 * `index`, never after it. The runner cannot enforce this — only reviewing the rule can.
 *
 * `position` is the open trade marked to the current bar's close, or null when flat. It is there so
 * a rule can implement a stop or a target from [Trade.pnl] and [Trade.drawdown] rather than
 * recomputing the entry price it already gave away.
 */
fun interface Strategy {
    fun onBar(index: Int, series: CandleSeries, position: Trade?): Signal
}

/**
 * Everything a run produced.
 *
 * [equityCurve] has exactly one point per bar of the input, marked to that bar's close and
 * including the open position, so it can be plotted directly under the price chart with the same x
 * axis and no interpolation. It is a `DoubleArray` for the same reason the indicator library uses
 * them: it is read in loops of arithmetic, and this class is therefore compared by identity rather
 * than by content — the generated `equals` on an array field compares references, which is correct
 * for a result object nobody diffs and would be wrong for a value nobody has asked for.
 */
data class BacktestResult(
    val trades: List<Trade>,
    val equityCurve: DoubleArray,
    val metrics: BacktestMetrics,
)

/**
 * The performance summary, with every number defined once so two screens cannot disagree.
 *
 * All currency figures are in quote currency and on the same scale as the starting equity. All
 * percentages are already multiplied by a hundred, so a caller formats with `%.2f` and appends the
 * sign itself — and does it with `Locale.US`, or the device's Persian locale will render market
 * figures in Persian digits.
 *
 * Nothing here is ever `NaN` or the result of a division by zero. A run with no trades returns
 * zeros throughout, with the single deliberate exception of [buyAndHoldReturn], which is a property
 * of the bars rather than of the strategy and stays real — a strategy that never traded through a
 * doubling is a finding, and zeroing the comparison would hide it.
 */
data class BacktestMetrics(
    /** Sum of every trade's P&L after fees. The headline, and on its own almost meaningless: it
     * says nothing about how much was risked to get it. Read it beside [maxEquityDrawdown]. */
    val netProfit: Double = 0.0,

    /** [netProfit] as a percentage of the starting equity, which is the form that compares across
     * two runs with different stakes. It ignores when the profit arrived, so a run that made
     * everything in one bar and a steady one look identical. */
    val netProfitPercent: Double = 0.0,

    /** The sum of the winning trades only. Useful as the numerator of [profitFactor]; on its own it
     * is the half of the story a losing strategy would like to show you. */
    val grossProfit: Double = 0.0,

    /** The sum of the losing trades, as a positive number so it can be read as a magnitude rather
     * than parsed for a minus sign. */
    val grossLoss: Double = 0.0,

    /** Every fee charged, both sides of every trade. Worth reading against [netProfit]: when fees
     * are the larger number the strategy is a rebate scheme for the exchange, whatever the
     * percentage profitable says. */
    val totalFees: Double = 0.0,

    /** [grossProfit] divided by [grossLoss]. Above 1 is profitable, and roughly 1.5 is where a rule
     * starts to survive live costs. It misleads in two directions: a handful of trades can produce
     * a spectacular factor by luck, and one enormous winner can carry a rule that loses on almost
     * every trade. Infinite when there were profits and no losses at all — which is a sample-size
     * artefact, not a perfect strategy, and should be shown as a dash rather than a number. Zero
     * when there were no trades or no profits. */
    val profitFactor: Double = 0.0,

    /** How many round trips closed. Under about thirty, every other number on this list is an
     * anecdote; the metrics do not get less confident-looking as the sample shrinks, which is the
     * single most dangerous thing about a report like this. */
    val totalTrades: Int = 0,

    /** Trades that closed above zero after fees. */
    val winningTrades: Int = 0,

    /** Trades that closed below zero after fees. A scratch trade, exactly zero, is in neither
     * count, so these two need not add up to [totalTrades]. */
    val losingTrades: Int = 0,

    /** Winners as a percentage of all trades. The number most likely to be quoted and least likely
     * to matter: a rule can be right eighty per cent of the time and lose money, and trend
     * following is usually right under forty and makes it. Read it only with [winLossRatio]. */
    val percentProfitable: Double = 0.0,

    /** [netProfit] divided by [totalTrades] — what one trade was worth on average. This is the
     * number that decides whether a rule survives a spread, because the spread is paid per trade
     * and does not care about the average being made of a few large winners. */
    val averagePnl: Double = 0.0,

    /** The mean of the winners. */
    val averageWin: Double = 0.0,

    /** The mean of the losers, as a positive magnitude. */
    val averageLoss: Double = 0.0,

    /** [averageWin] over [averageLoss]. Together with [percentProfitable] it is the whole edge:
     * multiply the win rate by this and compare against one. It hides the shape of the
     * distribution, so a rule with one outlier winner reports a fine ratio it will never repeat.
     * Zero when there were no losers to divide by. */
    val winLossRatio: Double = 0.0,

    /** The best single trade. When it is a large share of [netProfit], the rule has not been shown
     * to work — one trade has. */
    val largestWin: Double = 0.0,

    /** The worst single trade, as a positive magnitude. If it is much larger than [averageLoss] the
     * rule has no working stop, whatever the stop parameter says. */
    val largestLoss: Double = 0.0,

    /** Mean bars held across all trades. Its use is practical: it tells a reader whether this is a
     * rule they can actually run given how often they look at a chart. */
    val averageBarsInTrade: Double = 0.0,

    /** Mean bars held in the winners. */
    val averageBarsInWinners: Double = 0.0,

    /** Mean bars held in the losers. When losers are held far longer than winners, the rule cuts
     * winners early and lets losers run — visible here and nowhere else on this list. */
    val averageBarsInLosers: Double = 0.0,

    /** The largest trough-to-peak rise of the equity curve, in quote currency. The mirror of
     * [maxEquityDrawdown], and much less useful: it is the best stretch, and every strategy has
     * one. */
    val maxEquityRunUp: Double = 0.0,

    /** [maxEquityRunUp] as a percentage of the equity at the trough it started from. */
    val maxEquityRunUpPercent: Double = 0.0,

    /** The largest peak-to-trough fall of the **equity curve**, in quote currency.
     *
     * Peak to trough on the account, which is not the largest losing trade and is usually much
     * bigger than it: four ordinary losses in a row draw the account down further than one bad
     * trade does. This is the number that decides position size, because it is what a reader would
     * have had to sit through without turning the strategy off. It is computed on the marked
     * curve, so it includes open-position loss the reader would have watched, not only realised
     * loss. It still understates: bar closes miss the intrabar low of the worst day. */
    val maxEquityDrawdown: Double = 0.0,

    /** [maxEquityDrawdown] as a percentage of the peak it fell from — the form that is comparable
     * between a run on ten thousand and a run on a million. */
    val maxEquityDrawdownPercent: Double = 0.0,

    /** The most bars spent below a previous equity peak before exceeding it again, counting to the
     * end of the run when the peak was never recovered. Drawdown depth is what a reader can size
     * for; this is what they have to endure, and it is why strategies are abandoned one bar before
     * they recover. */
    val longestDrawdownBars: Int = 0,

    /** Annualised Sharpe ratio: mean per-bar return over its standard deviation, multiplied by the
     * square root of the number of bars in a year.
     *
     * Annualised deliberately — see [Backtest.sharpe]. A Sharpe quoted from raw per-bar returns is
     * not a Sharpe and is smaller than the real one by a factor of about twenty on a daily series.
     * Where it misleads: the denominator punishes upside volatility exactly as hard as downside, so
     * a strategy with occasional large wins scores worse than a flat one that bleeds. Zero, not
     * `NaN`, when the equity curve never moved. */
    val sharpeRatio: Double = 0.0,

    /** Annualised Sortino ratio: the same numerator as [sharpeRatio] over the downside deviation
     * only, so upside volatility is no longer punished — see [Backtest.sortino] for the two ways
     * the denominator is usually got wrong. Where it misleads: with few losing bars the downside is
     * estimated from almost no data and the ratio becomes enormous and meaningless, which is
     * exactly when it is most likely to be quoted. Zero when there were no negative returns. */
    val sortinoRatio: Double = 0.0,

    /** What one trade is worth on average, decomposed as `winRate × averageWin − lossRate ×
     * averageLoss`. It is arithmetically the same number as [averagePnl], and that is the point:
     * expectancy is average P&L, written so a reader can see which of the two levers — being right
     * more often, or being right by more — the edge is actually coming from. It misleads exactly as
     * [averagePnl] does, by averaging over a distribution with fat tails. */
    val expectancy: Double = 0.0,

    /** The return, as a percentage, of simply buying the first bar and holding to the last. The
     * only honest baseline: a strategy that returns forty per cent through a market that returned
     * ninety has destroyed value while looking profitable. Where it misleads in the other
     * direction: it was fully exposed the whole time, so compare it against [maxEquityDrawdown]
     * before concluding the strategy lost. */
    val buyAndHoldReturn: Double = 0.0,

    /** How many bars make a year at this timeframe — the annualisation factor's source, kept so a
     * reader can check that a Sharpe was scaled by the number they expected. Zero when the bar
     * length was unknown, in which case the ratios above were not annualised. */
    val periodsPerYear: Double = 0.0,
)

/**
 * A strategy runner over a bar series: pure Kotlin, no Compose, no coroutines, no clock.
 *
 * Headless in the same way [Replay] is, and for the same reason — the interesting failures of a
 * backtest are arithmetic, and arithmetic is far easier to be certain about as an assertion than as
 * a chart somebody looks at. The whole run is one pass over the bars; nothing here allocates per
 * frame and nothing here suspends, so a full history runs inside a click.
 *
 * Every convention below is the pessimistic one, because a backtest that flatters is worse than no
 * backtest at all:
 *
 * * A signal produced at the close of bar *n* fills at the **open of bar n+1**. Filling at the
 *   close that produced the signal is the single most common way a backtest invents money: that
 *   close is not known until the bar is over, so nobody could have traded on it.
 * * A position still open at the end is closed at the final bar's close, so the curve ends on a
 *   number that was realisable rather than on an unrealised hope.
 * * The equity curve is marked to market at every bar, open position included. A curve of realised
 *   P&L only is flat through the whole of a losing hold and then drops in one step, which hides the
 *   drawdown the reader would actually have sat through — and drawdown is the number that decides
 *   whether the strategy is runnable.
 * * Fees are charged on both sides at a real default. At zero fees every fast rule is a fortune.
 */
object Backtest {

    /** A round number to start from, so percentages and currency figures read at the same scale. */
    const val DEFAULT_STARTING_EQUITY = 10_000.0

    /** Five basis points a side — roughly a taker fee. Not zero, which is nobody's fee. */
    const val DEFAULT_FEE_PERCENT = 0.05

    /** Seconds in a 365-day year, the denominator every annualisation here divides. */
    const val SECONDS_PER_YEAR = 365.0 * 24 * 60 * 60

    /**
     * Run [strategy] over [series] and report what it would have done.
     *
     * [feePercent] is charged per side as a percentage of the filled notional, so `0.05` is five
     * basis points on the way in and five on the way out.
     *
     * [barSeconds] is the timeframe, and it exists because [sharpe] and [sortino] are meaningless
     * without it: the same return series is a fine Sharpe on daily bars and an absurd one on
     * minutes. It defaults to the median gap in the series, which is right for every feed this app
     * reads and wrong only for a series with gaps larger than half its bars.
     *
     * An empty series, a series of one bar, or a strategy that never trades all return a result of
     * zeros rather than throwing or dividing by zero.
     */
    fun run(
        series: CandleSeries,
        strategy: Strategy,
        startingEquity: Double = DEFAULT_STARTING_EQUITY,
        feePercent: Double = DEFAULT_FEE_PERCENT,
        barSeconds: Long = inferBarSeconds(series),
    ): BacktestResult {
        val count = series.size
        if (count == 0) {
            return BacktestResult(emptyList(), DoubleArray(0), BacktestMetrics())
        }

        val trades = ArrayList<Trade>()
        val equity = DoubleArray(count)
        var realised = 0.0

        var open = false
        var openIsLong = false
        var openIndex = 0
        var openTime = 0L
        var openPrice = 0.0
        var openSize = 0.0
        var openFee = 0.0
        var openHigh = 0.0
        var openLow = 0.0

        var pendingEntry: Signal.Enter? = null
        var pendingExit = false

        fun feeOn(price: Double, size: Double) = abs(price * size) * feePercent / 100.0

        for (index in 0 until count) {
            val bar = series[index]

            // Fills first, at this bar's open, from the signal the previous bar's close produced.
            if (pendingExit && open) {
                val exitFee = feeOn(bar.o, openSize)
                trades += Trade(
                    entryIndex = openIndex,
                    entryTime = openTime,
                    entryPrice = openPrice,
                    exitIndex = index,
                    exitTime = bar.t,
                    exitPrice = bar.o,
                    isLong = openIsLong,
                    size = openSize,
                    fee = openFee + exitFee,
                    // The fill price is a price the position really passed through, so it belongs
                    // inside the excursion envelope; the rest of this bar happened after the exit
                    // and deliberately does not.
                    highestHigh = max(openHigh, bar.o),
                    lowestLow = min(openLow, bar.o),
                )
                realised += trades.last().pnl
                open = false
            }
            pendingExit = false

            val wanted = pendingEntry
            pendingEntry = null
            if (wanted != null && !open && wanted.size > 0) {
                open = true
                openIsLong = wanted.isLong
                openIndex = index
                openTime = bar.t
                openPrice = bar.o
                openSize = wanted.size
                openFee = feeOn(bar.o, wanted.size)
                openHigh = bar.o
                openLow = bar.o
            }

            // The excursion of every bar held, entry bar included. Highs and lows, never closes:
            // a trade that ran in your favour intrabar did run, and the closes do not know it.
            if (open) {
                openHigh = max(openHigh, bar.h)
                openLow = min(openLow, bar.l)
            }

            val unrealised = if (open) {
                (bar.c - openPrice) * (if (openIsLong) 1 else -1) * openSize - openFee
            } else {
                0.0
            }
            equity[index] = startingEquity + realised + unrealised

            val position = if (open) {
                Trade(
                    entryIndex = openIndex,
                    entryTime = openTime,
                    entryPrice = openPrice,
                    exitIndex = index,
                    exitTime = bar.t,
                    exitPrice = bar.c,
                    isLong = openIsLong,
                    size = openSize,
                    fee = openFee + feeOn(bar.c, openSize),
                    highestHigh = openHigh,
                    lowestLow = openLow,
                )
            } else {
                null
            }

            when (val signal = strategy.onBar(index, series, position)) {
                Signal.Hold -> Unit
                is Signal.Enter -> if (!open) pendingEntry = signal
                Signal.Exit -> if (open) pendingExit = true
            }
        }

        // Anything still open closes at the last close. Leaving it open would end the curve on an
        // unrealised number and let a losing run finish looking like a pause. A pending entry on
        // the final bar never fills, because there is no next open to fill it at.
        if (open) {
            val last = series[count - 1]
            val exitFee = feeOn(last.c, openSize)
            trades += Trade(
                entryIndex = openIndex,
                entryTime = openTime,
                entryPrice = openPrice,
                exitIndex = count - 1,
                exitTime = last.t,
                exitPrice = last.c,
                isLong = openIsLong,
                size = openSize,
                fee = openFee + exitFee,
                highestHigh = openHigh,
                lowestLow = openLow,
            )
            realised += trades.last().pnl
            equity[count - 1] = startingEquity + realised
        }

        return BacktestResult(
            trades = trades,
            equityCurve = equity,
            metrics = summarise(trades, equity, series, startingEquity, barSeconds),
        )
    }

    /**
     * The median gap between bars, in seconds, or zero when the series is too short to tell.
     *
     * Median rather than mean, and rather than the first gap: a series that spans a weekend, a
     * halt or a feed outage has a handful of enormous gaps, and either of the other two choices
     * would report a timeframe the chart has never drawn.
     */
    fun inferBarSeconds(series: CandleSeries): Long {
        if (series.size < 2) return 0L
        val gaps = ArrayList<Long>(series.size - 1)
        for (index in 1 until series.size) {
            val gap = series.time[index] - series.time[index - 1]
            if (gap > 0) gaps += gap
        }
        if (gaps.isEmpty()) return 0L
        gaps.sort()
        return gaps[gaps.size / 2]
    }

    /**
     * How many bars of this length fit in a year.
     *
     * Returns 1.0 for an unknown or non-positive bar length, which leaves the ratios below
     * unannualised rather than infinite — a small number a reader can question, instead of a
     * crash or a `NaN` they cannot.
     */
    fun periodsPerYear(barSeconds: Long): Double =
        if (barSeconds > 0) SECONDS_PER_YEAR / barSeconds else 1.0

    /**
     * Per-bar returns of an equity curve, as fractions.
     *
     * Fractions of the previous point, not differences, because the ratios below assume a return
     * series and a difference series would make them depend on the size of the account. A
     * non-positive previous point contributes a zero return rather than an infinity: a blown
     * account has no meaningful percentage move left in it.
     */
    fun returns(equityCurve: DoubleArray): DoubleArray {
        if (equityCurve.size < 2) return DoubleArray(0)
        return DoubleArray(equityCurve.size - 1) { index ->
            val previous = equityCurve[index]
            if (previous > 0) (equityCurve[index + 1] - previous) / previous else 0.0
        }
    }

    /**
     * Annualised Sharpe ratio of a per-period return series.
     *
     * Two things have to be right and are usually not. The first is that a Sharpe is an *annual*
     * number: the mean-over-deviation of a per-bar return series is a per-bar quantity, and turning
     * it into a Sharpe means multiplying by the square root of [periodsPerYear] — which is why this
     * takes the periods rather than guessing them. A figure quoted from raw per-bar returns is
     * roughly twenty times too small on daily bars and four hundred times too small on hourly, and
     * it is quoted anyway because it still looks like a plausible number.
     *
     * The second is the deviation itself: this uses the population deviation, dividing by the count
     * of returns, because the return series is the entire run rather than a sample drawn from it.
     *
     * The risk-free rate is taken as zero, which is the usual convention for a strategy report and
     * flatters every result by whatever cash was paying.
     *
     * Returns 0.0, never `NaN`, when there are no returns or when the curve never moved — a flat
     * account has no risk-adjusted return, and zero is the honest way to say so.
     */
    fun sharpe(returns: DoubleArray, periodsPerYear: Double): Double {
        if (returns.isEmpty()) return 0.0
        val mean = returns.average()
        var sumSquares = 0.0
        for (value in returns) {
            val deviation = value - mean
            sumSquares += deviation * deviation
        }
        val deviation = sqrt(sumSquares / returns.size)
        if (deviation <= 0.0) return 0.0
        return mean / deviation * sqrt(max(periodsPerYear, 0.0))
    }

    /**
     * Annualised Sortino ratio of a per-period return series.
     *
     * Same numerator as [sharpe]; the denominator is the downside deviation, and it is where nearly
     * every published Sortino goes wrong, in one of two ways.
     *
     * The definition is the root mean square of the negative returns divided by the count of
     * **all** returns, not by the count of the negative ones. Dividing by the negative count is the
     * first mistake: it enlarges the denominator and quietly *deflates* the ratio, so a strategy is
     * reported as worse than it was — wrong, but at least wrong in the direction that costs nobody
     * money.
     *
     * The second mistake is the flattering one and the reason so many published Sortinos are too
     * good: taking the *standard deviation* of the negative returns, measured about their own mean,
     * instead of their root mean square about zero. Losses cluster, so their spread about their own
     * mean is far smaller than their distance from zero, and the ratio comes out several times too
     * large. The tell is that the number improves the more uniform the losses are, which is
     * backwards — uniform losses are the safe kind.
     *
     * This implementation squares each negative return about zero and divides by every return.
     *
     * Returns 0.0 when there are no returns and when there are no negative returns at all. The
     * second case is a real limitation rather than a perfect strategy: with no downside there is
     * nothing to divide by, and an infinity on screen would read as a result.
     */
    fun sortino(returns: DoubleArray, periodsPerYear: Double): Double {
        if (returns.isEmpty()) return 0.0
        val mean = returns.average()
        var sumSquares = 0.0
        for (value in returns) {
            if (value < 0) sumSquares += value * value
        }
        // Divided by every return, not only the negative ones. This is the whole point.
        val downside = sqrt(sumSquares / returns.size)
        if (downside <= 0.0) return 0.0
        return mean / downside * sqrt(max(periodsPerYear, 0.0))
    }

    /**
     * Every metric, from the closed trades and the marked equity curve.
     *
     * Public so a caller that has assembled trades another way — an imported journal, a paper
     * trading session — can summarise them with exactly the same arithmetic the backtest uses,
     * rather than a second implementation that will disagree by a fee.
     */
    fun summarise(
        trades: List<Trade>,
        equityCurve: DoubleArray,
        series: CandleSeries,
        startingEquity: Double = DEFAULT_STARTING_EQUITY,
        barSeconds: Long = inferBarSeconds(series),
    ): BacktestMetrics {
        val winners = trades.filter { it.isWin }
        val losers = trades.filter { it.isLoss }

        val grossProfit = winners.sumOf { it.pnl }
        val grossLoss = abs(losers.sumOf { it.pnl })
        val netProfit = trades.sumOf { it.pnl }

        val averageWin = if (winners.isNotEmpty()) grossProfit / winners.size else 0.0
        val averageLoss = if (losers.isNotEmpty()) grossLoss / losers.size else 0.0
        val winRate = if (trades.isNotEmpty()) winners.size.toDouble() / trades.size else 0.0
        val lossRate = if (trades.isNotEmpty()) losers.size.toDouble() / trades.size else 0.0

        val excursion = equityExcursion(equityCurve)
        val periods = periodsPerYear(barSeconds)
        val returnSeries = returns(equityCurve)

        val firstClose = if (series.size >= 2) series.close.first() else 0.0
        val buyAndHold = if (series.size >= 2 && firstClose > 0) {
            (series.close.last() - firstClose) / firstClose * 100
        } else {
            0.0
        }

        return BacktestMetrics(
            netProfit = netProfit,
            netProfitPercent = if (startingEquity > 0) netProfit / startingEquity * 100 else 0.0,
            grossProfit = grossProfit,
            grossLoss = grossLoss,
            totalFees = trades.sumOf { it.fee },
            profitFactor = when {
                grossLoss > 0 -> grossProfit / grossLoss
                grossProfit > 0 -> Double.POSITIVE_INFINITY
                else -> 0.0
            },
            totalTrades = trades.size,
            winningTrades = winners.size,
            losingTrades = losers.size,
            percentProfitable = winRate * 100,
            averagePnl = if (trades.isNotEmpty()) netProfit / trades.size else 0.0,
            averageWin = averageWin,
            averageLoss = averageLoss,
            winLossRatio = if (averageLoss > 0) averageWin / averageLoss else 0.0,
            largestWin = winners.maxOfOrNull { it.pnl } ?: 0.0,
            largestLoss = losers.minOfOrNull { it.pnl }?.let { abs(it) } ?: 0.0,
            averageBarsInTrade = trades.averageBars(),
            averageBarsInWinners = winners.averageBars(),
            averageBarsInLosers = losers.averageBars(),
            maxEquityRunUp = excursion.runUp,
            maxEquityRunUpPercent = excursion.runUpPercent,
            maxEquityDrawdown = excursion.drawdown,
            maxEquityDrawdownPercent = excursion.drawdownPercent,
            longestDrawdownBars = excursion.longestDrawdownBars,
            sharpeRatio = sharpe(returnSeries, periods),
            sortinoRatio = sortino(returnSeries, periods),
            expectancy = winRate * averageWin - lossRate * averageLoss,
            buyAndHoldReturn = buyAndHold,
            periodsPerYear = if (barSeconds > 0) periods else 0.0,
        )
    }

    /** The four equity-curve excursion figures, walked in one pass because they share a peak. */
    private data class Excursion(
        val runUp: Double,
        val runUpPercent: Double,
        val drawdown: Double,
        val drawdownPercent: Double,
        val longestDrawdownBars: Int,
    )

    /**
     * Peak-to-trough and trough-to-peak on the equity curve.
     *
     * Deliberately not the largest losing trade, which is the number this is most often confused
     * with and is always smaller: a run of four ordinary losses takes the account further down than
     * any one of them did, and it is the run the reader has to sit through.
     */
    private fun equityExcursion(equityCurve: DoubleArray): Excursion {
        if (equityCurve.isEmpty()) return Excursion(0.0, 0.0, 0.0, 0.0, 0)

        var peak = equityCurve[0]
        var trough = equityCurve[0]
        var peakIndex = 0
        var worstFall = 0.0
        var worstFallPercent = 0.0
        var bestRise = 0.0
        var bestRisePercent = 0.0
        var longestBars = 0
        var underwater = false

        for (index in equityCurve.indices) {
            val point = equityCurve[index]

            if (point >= peak) {
                // The peak is matched or exceeded, so whatever drawdown was running ends here —
                // but only if one was running. Without the flag a flat curve reports a drawdown a
                // bar long at every bar, which is the account never having lost anything being
                // described as permanently underwater.
                if (underwater) longestBars = max(longestBars, index - peakIndex)
                peak = point
                peakIndex = index
                underwater = false
            } else {
                underwater = true
            }
            val fall = peak - point
            if (fall > worstFall) worstFall = fall
            if (peak > 0) worstFallPercent = max(worstFallPercent, fall / peak * 100)

            if (point <= trough) trough = point
            val rise = point - trough
            if (rise > bestRise) bestRise = rise
            if (trough > 0) bestRisePercent = max(bestRisePercent, rise / trough * 100)
        }
        // A drawdown still running at the last bar is counted to the end: it is the one the reader
        // is sitting in, and dropping it would report the most comfortable version of the run.
        if (underwater) longestBars = max(longestBars, equityCurve.size - 1 - peakIndex)

        return Excursion(bestRise, bestRisePercent, worstFall, worstFallPercent, longestBars)
    }

    /** Mean bars held, or zero for an empty list rather than a `NaN` from dividing by no trades. */
    private fun List<Trade>.averageBars(): Double =
        if (isEmpty()) 0.0 else sumOf { it.barsHeld }.toDouble() / size
}
