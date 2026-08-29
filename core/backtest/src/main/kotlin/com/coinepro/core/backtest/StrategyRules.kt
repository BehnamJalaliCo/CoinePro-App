package com.coinepro.core.backtest

import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.Indicators
import com.coinepro.core.chart.Signal
// ── The one import in this module that has to be aliased ────────────────────────────────────────
//
// There are two `Backtest` objects in this app and they are different things. The unaliased one is
// this module's `com.coinepro.core.backtest.Backtest`: three named rules, a `Settings` a reader can
// change from a sheet, and five headline numbers. The aliased one is
// `com.coinepro.core.chart.Backtest`, the real engine — an arbitrary strategy over a bar series,
// twenty-five metrics, annualised Sharpe and Sortino, run-up and drawdown on the marked equity
// curve. `Strategy` and `Trade` collide the same way: this module's are nested inside `Backtest`,
// the engine's are top-level in `core:chart`.
//
// The rule everywhere below, and in `feature:chart`'s backtest sheet, is that the engine's types
// carry the `Engine` prefix and this module's do not. Anyone reading `Engine.run` is looking at the
// full engine; anyone reading `Backtest.Settings` is looking at what the sheet's chips set.
import com.coinepro.core.chart.Backtest as Engine
import com.coinepro.core.chart.Strategy as EngineStrategy
import com.coinepro.core.chart.BacktestResult as EngineResult

/**
 * The three named rules, expressed as strategies the real engine can run.
 *
 * This module already knew how to decide "long or flat" for a moving-average cross, an RSI
 * reversion and a channel breakout, and it ran them through a five-metric summariser of its own.
 * That summariser is gone from the reader's path: the rules are the part worth keeping, the
 * arithmetic belongs to [Engine], and this file is the adapter between them.
 *
 * ### Two conventions that are not cosmetic
 *
 * **Cost.** [Backtest.Settings.costFraction] is a *round trip* expressed as a fraction — `0.0005`
 * is five basis points in and out together. [Engine] charges a *percentage per side*. Handing the
 * fraction straight over would charge five basis points on the way in and five more on the way
 * out, doubling every cost in the report, and nothing would look wrong: the strategy would simply
 * be reported as slightly worse than it is, which is the failure nobody investigates. See
 * [feePercentPerSide].
 *
 * **Size.** Every rule trades the same quantity for the whole run — enough units that the position
 * is worth the starting equity at the first bar, which is exactly the quantity buy-and-hold holds.
 * That is what makes the buy-and-hold line on the equity chart a comparison rather than a
 * decoration: the two curves are the same money in the same instrument, differing only in when it
 * was exposed. A size that compounded with equity would flatter every rule that happened to win
 * early, and a fixed one unit would be a notional of six figures on BTC and of a few cents on a
 * micro-cap, so the percentages would mean something different on every symbol.
 */
object StrategyRules {

    /** Out of the market. */
    const val FLAT = 0

    /** Long. */
    const val LONG = 1

    /** Short — only ever produced when the caller asked for shorts. See [directions]. */
    const val SHORT = -1

    /**
     * The per-side fee percentage that matches a round-trip cost fraction.
     *
     * Halved and multiplied by a hundred, in that order and for the reason in this file's header:
     * the two engines express the same cost in two different units, and the conversion is the kind
     * of thing that is wrong for a year because the result still looks like a plausible number.
     */
    fun feePercentPerSide(costFraction: Double): Double = costFraction / 2.0 * 100.0

    /**
     * How many units of the instrument every trade is for.
     *
     * The quantity buy-and-hold would hold, so the two curves on the Overview tab are comparable.
     * Falls back to one unit on a series whose first close is not a usable price, which is a series
     * that will produce no meaningful report anyway.
     */
    fun positionSize(series: CandleSeries, startingEquity: Double): Double {
        val first = series.close.firstOrNull() ?: return 1.0
        if (first <= 0 || !first.isFinite()) return 1.0
        return startingEquity / first
    }

    /**
     * What the rule wants to be holding at the close of each bar: [LONG], [SHORT] or [FLAT].
     *
     * Precomputed over the whole series rather than decided inside the per-bar callback, because
     * every one of these rules is a read of an indicator array and computing an SMA once per bar
     * would turn a linear run into a quadratic one.
     *
     * Null means the settings do not describe a rule — a fast average at or above the slow one is
     * the only case, and it is a refusal rather than an empty result because a cross of a line with
     * itself is not a strategy that produced no trades, it is not a strategy.
     *
     * ### Shorts are opt-in, and the reason is not squeamishness
     *
     * A short on anything this app quotes needs a borrow, a funding rate and a liquidation price,
     * and a bar series knows none of them. A run with [allowShorts] on is therefore an
     * approximation: it charges the same fee both ways and no carry at all, so a short held for
     * weeks is reported as cheaper than it could ever be. It exists because a report with a
     * "shorts only" tab that is permanently empty tells the reader nothing, and because the
     * symmetric version of a rule is the honest way to see whether its edge is direction or drift.
     */
    fun directions(
        series: CandleSeries,
        settings: Backtest.Settings,
        allowShorts: Boolean = false,
    ): IntArray? {
        val size = series.size
        if (size == 0) return null
        val close = series.close
        val wanted = IntArray(size)

        when (settings.strategy) {
            Backtest.Strategy.MA_CROSS -> {
                if (settings.fast >= settings.slow) return null
                val fast = Indicators.sma(close, settings.fast)
                val slow = Indicators.sma(close, settings.slow)
                for (index in 0 until size) {
                    val a = fast[index]
                    val b = slow[index]
                    wanted[index] = when {
                        a == null || b == null -> FLAT
                        a > b -> LONG
                        allowShorts && a < b -> SHORT
                        else -> FLAT
                    }
                }
            }

            Backtest.Strategy.RSI_REVERSION -> {
                val rsi = Indicators.rsi(close, settings.rsiPeriod)
                // Stateful, because reversion is a rule about crossings rather than about levels:
                // "below thirty" is true for a hundred bars in a row and the position is opened on
                // the first of them, not on each.
                var held = FLAT
                for (index in 0 until size) {
                    val value = rsi[index]
                    if (value != null) {
                        held = when {
                            held == LONG -> if (value > settings.rsiSell) FLAT else LONG
                            held == SHORT -> if (value < settings.rsiBuy) FLAT else SHORT
                            value < settings.rsiBuy -> LONG
                            allowShorts && value > settings.rsiSell -> SHORT
                            else -> FLAT
                        }
                    }
                    wanted[index] = held
                }
            }

            Backtest.Strategy.BREAKOUT -> {
                val channel = Indicators.donchian(series.high, series.low, settings.channel)
                var held = FLAT
                for (index in 0 until size) {
                    val upper = channel.upper[index]
                    val lower = channel.lower[index]
                    if (upper != null && lower != null) {
                        held = when {
                            held == LONG -> if (close[index] <= lower) FLAT else LONG
                            held == SHORT -> if (close[index] >= upper) FLAT else SHORT
                            close[index] >= upper -> LONG
                            allowShorts && close[index] <= lower -> SHORT
                            else -> FLAT
                        }
                    }
                    wanted[index] = held
                }
            }
        }
        return wanted
    }

    /**
     * A precomputed direction series, as a strategy the engine can be asked bar by bar.
     *
     * A reversal is deliberately two signals and not one: the rule emits [Signal.Exit] on the bar
     * it changes its mind and [Signal.Enter] on the next bar it is asked. That costs a bar and two
     * fees, which is what a reversal actually costs — a single flipping signal would report every
     * reversing rule as cheaper than any broker could fill it.
     */
    fun strategy(directions: IntArray, size: Double): EngineStrategy =
        EngineStrategy { index, _, position ->
            val wanted = directions.getOrElse(index) { FLAT }
            val held = when {
                position == null -> FLAT
                position.isLong -> LONG
                else -> SHORT
            }
            when {
                held == wanted -> Signal.Hold
                held != FLAT -> Signal.Exit
                wanted == LONG -> Signal.Enter(isLong = true, size = size)
                wanted == SHORT -> Signal.Enter(isLong = false, size = size)
                else -> Signal.Hold
            }
        }

    /**
     * Run one of the named rules through the full engine.
     *
     * Null below [Backtest.MINIMUM_BARS] and on settings that do not describe a rule, for the
     * reasons those two carry themselves: under a hundred and twenty bars the slow average has
     * barely warmed up and every metric downstream is noise wearing a percentage sign.
     */
    fun run(
        series: CandleSeries,
        settings: Backtest.Settings = Backtest.Settings(),
        allowShorts: Boolean = false,
        startingEquity: Double = Engine.DEFAULT_STARTING_EQUITY,
    ): EngineResult? {
        if (series.size < Backtest.MINIMUM_BARS) return null
        val wanted = directions(series, settings, allowShorts) ?: return null
        val size = positionSize(series, startingEquity)
        return Engine.run(
            series = series,
            strategy = strategy(wanted, size),
            startingEquity = startingEquity,
            feePercent = feePercentPerSide(settings.costFraction),
        )
    }
}
