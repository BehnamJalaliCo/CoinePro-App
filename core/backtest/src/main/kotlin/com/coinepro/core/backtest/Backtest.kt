package com.coinepro.core.backtest

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.Indicators
import kotlin.math.abs
import kotlin.math.max

/**
 * Running a rule over history and counting what it would have done.
 *
 * The web terminal backtests `namescript`, an arbitrary language, on the server. This does not try
 * to be that. It runs three named rules on the bars the chart already has, on the device, with no
 * request — and the reason to build the small version rather than wait for the large one is that
 * the small version answers the question a reader actually has, which is "does this idea survive
 * contact with the last thousand bars".
 *
 * Every choice below is the pessimistic one, because a backtest that flatters is worse than none:
 *
 * * A signal on bar *n* is filled at the **open of bar n+1**, never at the close that produced it.
 *   Filling at the signal bar's own close is the single most common way a backtest invents money
 *   that could not have been made — the close is not known until the bar is over.
 * * The last position is closed at the final bar's close rather than left open, so the equity curve
 *   ends at a number that was actually realisable.
 * * Costs are a parameter with a real default rather than zero. A strategy that flips every few
 *   bars is profitable at zero cost and ruinous at five basis points, and the difference is the
 *   entire finding.
 */
object Backtest {

    enum class Strategy(val id: String) {
        /** Fast SMA over slow SMA. The oldest rule there is, and the honest baseline. */
        MA_CROSS("ma_cross"),

        /** Long under the lower band, flat over the upper. Mean reversion. */
        RSI_REVERSION("rsi_reversion"),

        /** Long above the upper Donchian, out below the lower. Trend following. */
        BREAKOUT("breakout"),
    }

    data class Settings(
        val strategy: Strategy = Strategy.MA_CROSS,
        val fast: Int = 20,
        val slow: Int = 50,
        val rsiPeriod: Int = 14,
        val rsiBuy: Double = 30.0,
        val rsiSell: Double = 70.0,
        val channel: Int = 20,
        /**
         * Round-trip cost as a fraction — 0.0005 is five basis points, roughly a taker fee pair.
         *
         * Not zero, deliberately. Zero is the setting under which every high-frequency rule looks
         * like a fortune, and a reader who never changes a default should not be handed that one.
         */
        val costFraction: Double = 0.0005,
    )

    data class Trade(
        val buy: Boolean,
        val entryIndex: Int,
        val exitIndex: Int,
        val entry: Double,
        val exit: Double,
        /** After costs, as a fraction of the entry. */
        val returnFraction: Double,
    )

    data class Result(
        val trades: List<Trade>,
        /** Equity as a multiple of the starting stake, one point per bar. Starts at 1.0. */
        val equity: List<Double>,
        val settings: Settings,
    ) {
        val closed: Int get() = trades.size
        val wins: Int get() = trades.count { it.returnFraction > 0 }
        val winRate: Double? get() = if (closed == 0) null else wins * 100.0 / closed

        /** Total return as a percentage. Null on an empty run rather than a flat zero. */
        val totalReturnPercent: Double?
            get() = equity.lastOrNull()?.takeIf { closed > 0 }?.let { (it - 1) * 100 }

        /** The worst peak-to-trough fall, as a positive percentage. The number that decides sizing. */
        val maxDrawdownPercent: Double
            get() {
                var peak = 1.0
                var worst = 0.0
                equity.forEach { point ->
                    peak = max(peak, point)
                    if (peak > 0) worst = max(worst, (peak - point) / peak)
                }
                return worst * 100
            }

        val profitFactor: Double?
            get() {
                val gains = trades.filter { it.returnFraction > 0 }.sumOf { it.returnFraction }
                val losses = abs(trades.filter { it.returnFraction < 0 }.sumOf { it.returnFraction })
                return if (losses == 0.0) null else gains / losses
            }
    }

    /**
     * The minimum history worth running over.
     *
     * Below this the slow average has barely warmed up and the answer is noise wearing a percentage
     * sign — which reads exactly like a finding.
     */
    const val MINIMUM_BARS = 120

    fun run(bars: List<Candle>, settings: Settings = Settings()): Result? {
        if (bars.size < MINIMUM_BARS) return null
        val wanted = signals(bars, settings) ?: return null

        val trades = mutableListOf<Trade>()
        val equity = MutableList(bars.size) { 1.0 }
        var capital = 1.0
        var entryIndex: Int? = null
        var entryPrice = 0.0

        // From 1, because a fill happens on the bar *after* the signal and bar 0 has no bar before.
        for (index in 1 until bars.size) {
            val signal = wanted[index - 1]
            val open = bars[index].o
            if (entryIndex == null && signal) {
                entryIndex = index
                entryPrice = open
            } else if (entryIndex != null && !signal) {
                capital = closeTrade(trades, entryIndex, index, entryPrice, open, settings, capital)
                entryIndex = null
            }
            equity[index] = capital
        }

        // Anything still open closes at the last close. Leaving it open would end the curve on an
        // unrealised number and let a losing run finish looking like a pause.
        entryIndex?.let { open ->
            capital = closeTrade(trades, open, bars.lastIndex, entryPrice, bars.last().c, settings, capital)
            equity[bars.lastIndex] = capital
        }

        return Result(trades = trades, equity = equity, settings = settings)
    }

    private fun closeTrade(
        into: MutableList<Trade>,
        entryIndex: Int,
        exitIndex: Int,
        entry: Double,
        exit: Double,
        settings: Settings,
        capital: Double,
    ): Double {
        if (entry <= 0 || !entry.isFinite() || !exit.isFinite()) return capital
        val gross = (exit - entry) / entry
        val net = gross - settings.costFraction
        into += Trade(true, entryIndex, exitIndex, entry, exit, net)
        return capital * (1 + net)
    }

    /**
     * Whether the rule wants to be long at the close of each bar.
     *
     * Long-only, and worth saying why: a short on an instrument this app quotes needs a borrow, a
     * funding rate and a liquidation price, none of which a bar series knows. A backtest that
     * silently shorts is answering a question about a position the reader could not have held.
     */
    private fun signals(bars: List<Candle>, settings: Settings): BooleanArray? {
        val close = DoubleArray(bars.size) { bars[it].c }
        val high = DoubleArray(bars.size) { bars[it].h }
        val low = DoubleArray(bars.size) { bars[it].l }

        return when (settings.strategy) {
            Strategy.MA_CROSS -> {
                if (settings.fast >= settings.slow) return null
                val fast = Indicators.sma(close, settings.fast)
                val slow = Indicators.sma(close, settings.slow)
                BooleanArray(bars.size) { index ->
                    val a = fast[index]
                    val b = slow[index]
                    a != null && b != null && a > b
                }
            }

            Strategy.RSI_REVERSION -> {
                val rsi = Indicators.rsi(close, settings.rsiPeriod)
                val wanted = BooleanArray(bars.size)
                var holding = false
                for (index in bars.indices) {
                    val value = rsi[index]
                    if (value != null) {
                        if (!holding && value < settings.rsiBuy) holding = true
                        else if (holding && value > settings.rsiSell) holding = false
                    }
                    wanted[index] = holding
                }
                wanted
            }

            Strategy.BREAKOUT -> {
                val channel = Indicators.donchian(high, low, settings.channel)
                val wanted = BooleanArray(bars.size)
                var holding = false
                for (index in bars.indices) {
                    val upper = channel.upper[index]
                    val lower = channel.lower[index]
                    if (upper != null && lower != null) {
                        if (!holding && close[index] >= upper) holding = true
                        else if (holding && close[index] <= lower) holding = false
                    }
                    wanted[index] = holding
                }
                wanted
            }
        }
    }
}
