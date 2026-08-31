package com.coinepro.core.backtest

import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.BacktestMetrics as EngineMetrics
// See `StrategyRules` for why `Backtest` is the aliased import in this module and what the alias
// means: `Engine` is `core:chart`'s full runner, plain `Backtest` is this module's three rules.
import com.coinepro.core.chart.Backtest as Engine
import com.coinepro.core.chart.Trade as EngineTrade
import kotlin.math.abs
import kotlin.math.max

/**
 * How much history the run actually covered.
 *
 * The reason this is part of the report rather than a caption somebody might add later: every
 * number beside it is stated to two decimal places and reads like a verdict, and a Sharpe computed
 * over three hundred bars is not a verdict, it is an anecdote with a decimal point. The chart holds
 * whatever has been paged in — often a few hundred candles — so the window is the difference
 * between a finding and a coincidence, and it is shown on the report rather than left to be
 * inferred.
 *
 * [moreHistoryAvailable] is the honest half of that: it says the feed has older bars that this run
 * did not see. A report that covers everything the market has and a report that covers the last
 * fortnight are different documents and must not look identical.
 */
data class BacktestWindow(
    /** How many candles the run walked. The first figure on the report, and Latin on screen. */
    val bars: Int,
    /** Open time of the first bar in the run, unix seconds. Zero on an empty series. */
    val firstTime: Long,
    /** Open time of the last bar in the run, unix seconds. Zero on an empty series. */
    val lastTime: Long,
    /** The inferred bar length in seconds — what Sharpe and Sortino were annualised by. */
    val barSeconds: Long,
    /** Whether the feed still has older bars that this run did not cover. */
    val moreHistoryAvailable: Boolean,
)

/**
 * Where the equity curve made its best run and took its worst fall, with the bars it happened over.
 *
 * The values duplicate what [EngineMetrics] already reports and are kept for tests; what nothing
 * else in the app has is the *indices*, and they are the point. Run-up is measured everywhere and
 * shown nowhere, which makes it useless: a trader whose stop was too tight learns it from seeing
 * how far the account ran before it gave the move back, and that is a span on a chart rather than
 * a number in a table.
 *
 * Both spans are `from ≤ to`. On a flat or empty curve every field is zero, which draws nothing.
 */
data class EquityExcursion(
    /** The best trough-to-peak rise, in quote currency. */
    val runUp: Double = 0.0,
    /** [runUp] as a percentage of the trough it started from. */
    val runUpPercent: Double = 0.0,
    /** The bar the best rise started at. */
    val runUpFrom: Int = 0,
    /** The bar it peaked at. Equal to [runUpFrom] when the curve never rose. */
    val runUpTo: Int = 0,
    /** The worst peak-to-trough fall, in quote currency. */
    val drawdown: Double = 0.0,
    /** [drawdown] as a percentage of the peak it fell from. */
    val drawdownPercent: Double = 0.0,
    /** The bar the worst fall started at. */
    val drawdownFrom: Int = 0,
    /** The bar it bottomed at. Equal to [drawdownFrom] when the curve never fell. */
    val drawdownTo: Int = 0,
)

/**
 * What the five-tab report needs, whoever traded.
 *
 * ### Why the report is an interface rather than one class
 *
 * Because there are two traders now and only one right answer. A strategy run and a reader's own
 * replay session are the same kind of document — a list of round trips, an equity curve marked to
 * every bar, the same buy-and-hold baseline, the same twenty-five metrics — about different hands.
 * Giving the reader's session its own report type with its own screen would mean a second set of
 * statistics, and a second set of statistics is a second definition of a win: the day the two
 * disagreed by a fee, nobody would be able to say which of them was right, and the reader would
 * have learned a win rate that does not mean what the strategy tab's win rate means.
 *
 * So both implement this, `BacktestSheet` renders whichever it is handed, and every figure in the
 * app comes out of `core:chart`'s one summariser.
 *
 * The derived properties below have bodies here rather than in each implementation, for the same
 * reason: "which trades are the longs" is not a question two report types should be allowed to
 * answer differently.
 */
interface TradeReport {
    /** How much history this report covered. Read before anything below it. */
    val window: BacktestWindow

    /** Whether the short side was available. Decides what an empty short column means. */
    val allowShorts: Boolean

    /** The stake every percentage is a percentage of. */
    val startingEquity: Double

    /** Every round trip that closed, in the order it closed. */
    val trades: List<EngineTrade>

    /** One point per bar, marked to that bar's close, open position included. */
    val equityCurve: DoubleArray

    /** The same money in the same instrument, held from the first bar to the last. */
    val buyAndHoldCurve: DoubleArray

    /** Where the curve made its best run and its worst fall, with the bars. Drawn, not tabulated. */
    val excursion: EquityExcursion

    /** Every trade. The Overview, Trades analysis and Risk tabs all read this one. */
    val all: EngineMetrics

    /** The long trades alone, summarised over their own equity curve. */
    val longs: EngineMetrics

    /** The short trades alone. All zeros on a long-only run, which is a fact rather than a gap. */
    val shorts: EngineMetrics

    /** Trades taken long. The Performance tab's first column, and the list tab's filter. */
    val longTrades: List<EngineTrade> get() = trades.filter { it.isLong }

    /** Trades taken short. Empty on every run made with [allowShorts] off. */
    val shortTrades: List<EngineTrade> get() = trades.filter { !it.isLong }

    /**
     * The best run-up any single trade went through, in quote currency.
     *
     * The trade-level mirror of [EngineMetrics.maxEquityRunUp], and the number a reader compares
     * against their stop: when the largest run-up across the losers is far above zero, the rule was
     * right and the exit was wrong, which is a different problem from the rule being wrong.
     */
    val bestTradeRunUp: Double get() = trades.maxOfOrNull { it.runUp } ?: 0.0

    /** The mean run-up of the trades that still closed at a loss. Zero when there were none. */
    val averageLoserRunUp: Double
        get() {
            val losers = trades.filter { it.isLoss }
            return if (losers.isEmpty()) 0.0 else losers.sumOf { it.runUp } / losers.size
        }
}

/**
 * Everything the five-tab report shows, computed once.
 *
 * TradingView's own tabs, because they are the right ones and a trader arriving from it already
 * knows where to look: Overview, Performance, Trades analysis, Risk ratios, List of trades.
 * Everything that terminal charges its Essential tier for — the advanced metrics, the CSV, the
 * XLSX — is free here, deliberately. A backtest of a reader's own idea over public candles is not
 * a premium good.
 *
 * [longs] and [shorts] are the Performance tab. They are not slices of [all]: a subset of trades
 * has its own equity curve, so its drawdown and its ratios have to be computed from that curve
 * rather than pulled out of the whole run's. See [BacktestReports.markedCurve] for the one place
 * the subset curve is weaker than the engine's own.
 *
 * The two `DoubleArray` fields make this class compare by identity, exactly as
 * `com.coinepro.core.chart.BacktestResult` does and for the same reason: the generated `equals` on
 * an array field compares references, which is correct for a result object nobody diffs and would
 * be quietly wrong for a value somebody expected to compare by content.
 */
data class BacktestReport(
    /** How much history this run covered. Read before anything below it. */
    override val window: BacktestWindow,
    /** The rule and the cost the run was made with, so a report can say what produced it. */
    val settings: Backtest.Settings,
    /** Whether the run was allowed to take the short side. See [StrategyRules.directions]. */
    override val allowShorts: Boolean,
    /** The stake every percentage below is a percentage of. */
    override val startingEquity: Double,
    /** Every round trip that closed, in the order it closed. */
    override val trades: List<EngineTrade>,
    /** One point per bar, marked to that bar's close, open position included. */
    override val equityCurve: DoubleArray,
    /** The same money in the same instrument, held from the first bar to the last. */
    override val buyAndHoldCurve: DoubleArray,
    /** Where the curve made its best run and its worst fall, with the bars. Drawn, not tabulated. */
    override val excursion: EquityExcursion,
    /** Every trade. The Overview, Trades analysis and Risk tabs all read this one. */
    override val all: EngineMetrics,
    /** The long trades alone, summarised over their own equity curve. */
    override val longs: EngineMetrics,
    /** The short trades alone. All zeros on a long-only run, which is a fact rather than a gap. */
    override val shorts: EngineMetrics,
) : TradeReport

/**
 * Building a report, in pure arithmetic over bars.
 *
 * No Compose, no coroutines, no clock — the same discipline the engine keeps, for the same reason:
 * every interesting failure of a backtest report is an arithmetic one, and arithmetic is far easier
 * to be certain about as an assertion than as a screen somebody looks at.
 */
object BacktestReports {

    /**
     * Run a named rule and summarise it into the five tabs.
     *
     * Null exactly where [StrategyRules.run] is null — too little history, or settings that do not
     * describe a rule. The caller shows the reason; a report of zeros would be a document claiming
     * the strategy did nothing, which is not what happened.
     */
    fun build(
        series: CandleSeries,
        settings: Backtest.Settings = Backtest.Settings(),
        allowShorts: Boolean = false,
        startingEquity: Double = Engine.DEFAULT_STARTING_EQUITY,
        moreHistoryAvailable: Boolean = false,
    ): BacktestReport? {
        val result = StrategyRules.run(series, settings, allowShorts, startingEquity) ?: return null
        val barSeconds = Engine.inferBarSeconds(series)
        val longs = result.trades.filter { it.isLong }
        val shorts = result.trades.filter { !it.isLong }

        return BacktestReport(
            window = BacktestWindow(
                bars = series.size,
                firstTime = series.time.firstOrNull() ?: 0L,
                lastTime = series.time.lastOrNull() ?: 0L,
                barSeconds = barSeconds,
                moreHistoryAvailable = moreHistoryAvailable,
            ),
            settings = settings,
            allowShorts = allowShorts,
            startingEquity = startingEquity,
            trades = result.trades,
            equityCurve = result.equityCurve,
            buyAndHoldCurve = buyAndHold(series, startingEquity),
            excursion = excursion(result.equityCurve),
            all = result.metrics,
            longs = Engine.summarise(
                trades = longs,
                equityCurve = markedCurve(longs, series, startingEquity),
                series = series,
                startingEquity = startingEquity,
                barSeconds = barSeconds,
            ),
            shorts = Engine.summarise(
                trades = shorts,
                equityCurve = markedCurve(shorts, series, startingEquity),
                series = series,
                startingEquity = startingEquity,
                barSeconds = barSeconds,
            ),
        )
    }

    /**
     * Buying the first bar and holding to the last, on the same scale as the equity curve.
     *
     * The only honest baseline, and the reason it is a *curve* rather than the single percentage
     * the engine already reports: a strategy that ends level with buy-and-hold after sitting out
     * the whole drawdown is a completely different result from one that tracked it the whole way,
     * and the two end at the same point.
     *
     * A non-positive first close — which no real feed produces and a synthetic series can — gives a
     * flat line at the starting equity rather than a division by zero.
     */
    fun buyAndHold(series: CandleSeries, startingEquity: Double): DoubleArray {
        val size = series.size
        if (size == 0) return DoubleArray(0)
        val first = series.close[0]
        if (first <= 0 || !first.isFinite()) return DoubleArray(size) { startingEquity }
        return DoubleArray(size) { index -> startingEquity * series.close[index] / first }
    }

    /**
     * An equity curve for an arbitrary subset of trades, marked to every bar's close.
     *
     * Needed because the Performance tab asks what the longs alone did, and a drawdown is a
     * property of a curve rather than of a list: adding up the losing long trades gives their
     * total, not the depth the account would have reached holding only them.
     *
     * ### Where this is weaker than the engine's own curve, and it matters
     *
     * The engine knows the fee it charged on entry. A closed [EngineTrade] carries only the round
     * trip, so the open position here is marked with half of it. The two agree exactly whenever the
     * two sides cost the same, which is every fee model this app uses; on an asymmetric fee
     * schedule the unrealised line would be out by the difference — cents on a ten thousand — and
     * the realised endpoints, which is where every metric is actually taken, stay exact either way.
     */
    fun markedCurve(
        trades: List<EngineTrade>,
        series: CandleSeries,
        startingEquity: Double,
    ): DoubleArray {
        val size = series.size
        val curve = DoubleArray(size) { startingEquity }
        if (size == 0 || trades.isEmpty()) return curve

        val byEntry = trades.sortedBy { it.entryIndex }
        val byExit = trades.sortedBy { it.exitIndex }
        val open = ArrayList<EngineTrade>(4)
        var nextEntry = 0
        var nextExit = 0
        var realised = 0.0

        for (index in 0 until size) {
            while (nextEntry < byEntry.size && byEntry[nextEntry].entryIndex <= index) {
                open += byEntry[nextEntry]
                nextEntry++
            }
            while (nextExit < byExit.size && byExit[nextExit].exitIndex <= index) {
                realised += byExit[nextExit].pnl
                nextExit++
            }
            open.removeAll { it.exitIndex <= index }

            var unrealised = 0.0
            val close = series.close[index]
            for (trade in open) {
                unrealised += (close - trade.entryPrice) * trade.direction * trade.size - trade.fee / 2
            }
            curve[index] = startingEquity + realised + unrealised
        }
        return curve
    }

    /**
     * The best trough-to-peak rise and the worst peak-to-trough fall, with the bars they ran over.
     *
     * One pass, because the two share a walk. The peak and the trough are tracked independently:
     * a drawdown is measured from the highest point *so far* and a run-up from the lowest point so
     * far, and folding them into one extreme would report a run-up that started after the fall it
     * is supposed to have recovered.
     *
     * Percentages are of the point the excursion started from, which is the form that compares
     * between a run on ten thousand and a run on a million. A non-positive base contributes no
     * percentage rather than an infinity.
     */
    fun excursion(curve: DoubleArray): EquityExcursion {
        if (curve.isEmpty()) return EquityExcursion()

        var peak = curve[0]
        var peakIndex = 0
        var trough = curve[0]
        var troughIndex = 0
        var worstFall = 0.0
        var worstFallPercent = 0.0
        var worstFrom = 0
        var worstTo = 0
        var bestRise = 0.0
        var bestRisePercent = 0.0
        var bestFrom = 0
        var bestTo = 0

        for (index in curve.indices) {
            val point = curve[index]

            if (point >= peak) {
                peak = point
                peakIndex = index
            }
            val fall = peak - point
            if (fall > worstFall) {
                worstFall = fall
                worstFrom = peakIndex
                worstTo = index
            }
            if (peak > 0) worstFallPercent = max(worstFallPercent, fall / peak * 100)

            if (point <= trough) {
                trough = point
                troughIndex = index
            }
            val rise = point - trough
            if (rise > bestRise) {
                bestRise = rise
                bestFrom = troughIndex
                bestTo = index
            }
            if (trough > 0) bestRisePercent = max(bestRisePercent, rise / trough * 100)
        }

        return EquityExcursion(
            runUp = bestRise,
            runUpPercent = bestRisePercent,
            runUpFrom = bestFrom,
            runUpTo = bestTo,
            drawdown = worstFall,
            drawdownPercent = worstFallPercent,
            drawdownFrom = worstFrom,
            drawdownTo = worstTo,
        )
    }

    /**
     * How much of the net profit came from the single best trade, as a fraction.
     *
     * The one number that most often turns a report round. A rule with a fine profit factor and
     * eighty per cent of its profit in one trade has not been shown to work — that trade has. Zero
     * when nothing was made, so a losing run does not report a share of a negative.
     */
    fun bestTradeShare(metrics: EngineMetrics): Double {
        if (metrics.netProfit <= 0 || metrics.largestWin <= 0) return 0.0
        return metrics.largestWin / metrics.netProfit
    }

    /**
     * The mean absolute deviation of trade P&L, in quote currency.
     *
     * On the Trades analysis tab beside the average, because an average alone cannot tell a rule
     * that makes forty pounds every time from one that makes four hundred once and loses forty
     * nine times. Zero on an empty list rather than a `NaN` from dividing by no trades.
     */
    fun pnlDispersion(trades: List<EngineTrade>): Double {
        if (trades.isEmpty()) return 0.0
        val mean = trades.sumOf { it.pnl } / trades.size
        return trades.sumOf { abs(it.pnl - mean) } / trades.size
    }
}
