package com.coinepro.core.chart

import kotlin.math.abs

/**
 * Stochastic RSI's two lines: the smoothed %K and the %D that follows it.
 *
 * Kept as a pair rather than returned as two calls because the caller that draws %K almost always
 * draws %D beside it, and computing the RSI twice to get them is the whole cost of the indicator.
 */
data class StochasticRsiSeries(val k: DoubleArray, val d: DoubleArray)

/**
 * The Alligator's three jaws, already displaced forward.
 *
 * The displacement is baked into the arrays rather than left to the renderer on purpose: a caller
 * that receives three undisplaced averages and is trusted to shift them by 8, 5 and 3 will one day
 * shift two of them, and the resulting picture still looks like an Alligator.
 */
data class AlligatorSeries(val jaw: DoubleArray, val teeth: DoubleArray, val lips: DoubleArray)

/**
 * The True Strength Index and its signal line.
 *
 * The signal is an EMA of the index itself, so it warms up later than the index does; both are
 * returned together so a reader cannot accidentally pair a line with a signal from another period.
 */
data class TrueStrengthSeries(val tsi: DoubleArray, val signal: DoubleArray)

/** Aroon's two lines: how recently the window's high was set, and how recently its low was. */
data class AroonSeries(val up: DoubleArray, val down: DoubleArray)

/**
 * The directional movement system read in full: both directional indicators and the ADX built on
 * them.
 *
 * ADX alone says a trend is strong and refuses to say which way, which is why it is never read
 * without the two DI lines beside it.
 */
data class DirectionalMovementSeries(
    val plusDi: DoubleArray,
    val minusDi: DoubleArray,
    val adx: DoubleArray,
)

/**
 * A percentage oscillator — PPO or PVO — with its signal and the histogram between them.
 *
 * The same three-field shape as MACD, and deliberately not [MacdResult]: these are percentages,
 * comparable between two symbols priced a thousand apart, and letting them share a type with the
 * absolute-difference MACD is how one ends up plotted on the other's scale.
 */
data class PercentageOscillatorSeries(
    val oscillator: DoubleArray,
    val signal: DoubleArray,
    val histogram: DoubleArray,
)

/**
 * The third pack: fourteen indicators that complete the momentum, volume and trend sets.
 *
 * ### Why plain arrays here and [Line] next door
 *
 * [Indicators] and [IndicatorsExt] hand back a [Line], which carries a presence mask beside its
 * values. This pack hands back a bare `DoubleArray` with `Double.NaN` in every warm-up slot, and
 * the difference is not an oversight. Half of what is below is fed the output of the other half —
 * the Stochastic RSI is a stochastic of an RSI, TEMA is an EMA of an EMA of an EMA, the ADX is a
 * Wilder average of a ratio of two other Wilder averages — and every hand-off through a masked
 * type is a place to lose a bar. NaN is the marker the arithmetic already propagates for free, and
 * the caller wraps the finished array once, at the edge, with `Line.of { it.takeIf(Double::isFinite) }`.
 *
 * ### The one rule that makes NaN safe to use as that marker
 *
 * A NaN in the *input* — a feed that dropped a bar, a symbol that did not trade — must never turn
 * into a NaN in a value that genuinely exists later on. So every window function here treats a gap
 * as a hole in that window only: the reading is absent while the gap is inside the lookback and
 * comes back the bar after it falls out the far end. Every running function — EMA, Wilder's
 * smoothing, the parabolic SAR's state machine — skips the bad bar without touching its state, so
 * one missing candle costs one reading rather than the rest of the series. The naive version, where
 * `previous = previous * k + NaN` poisons every remaining bar, is exactly the bug this rule exists
 * to prevent, and it is invisible on a chart that starts drawing after the gap.
 *
 * Nothing here throws. A period of zero, a period longer than the data, mismatched array lengths:
 * all of them return an all-NaN array of the input's length, because an indicator that cannot be
 * computed has no values, and a chart that crashes because a symbol has nine candles is worse than
 * one that draws nothing.
 */
object IndicatorsExtB {

    // ══════════════════════════════════════════════════════════ trend

    /**
     * [51] Wilder's Parabolic SAR: the stop that accelerates towards price until price crosses it.
     *
     * Three details separate a correct SAR from the many that merely look like one, and all three
     * are here.
     *
     * The first is the acceleration factor's reset. `af` grows by [step] only on a bar that sets a
     * *new* extreme, and on a reversal it drops all the way back to [step] rather than continuing
     * from where it was — a SAR that carries its acceleration across a turn is glued to price on
     * the new leg and stops out on the first pullback.
     *
     * The second is what the SAR becomes on a reversal: the extreme point of the leg that just
     * ended, not the raw parabola's next value. The stop jumps to the top of the rally it was
     * following, which is the whole reason the indicator is drawn as dots rather than a line.
     *
     * The third, and the one most implementations get wrong, is the clamp: the new SAR may never
     * be placed inside the range of the previous two bars. In an uptrend it is pulled down to the
     * lower of the last two lows, in a downtrend up to the higher of the last two highs. Without it
     * the accelerating parabola climbs into the recent trading range and reverses the trend on a
     * bar that did nothing — a false stop-out generated purely by arithmetic. Bar 0 has no SAR;
     * the series starts on bar 1 with the previous bar's extreme, and the initial direction comes
     * from whichever side of bar 1 expanded further.
     */
    fun parabolicSar(
        high: DoubleArray,
        low: DoubleArray,
        step: Double = 0.02,
        max: Double = 0.2,
    ): DoubleArray {
        val size = high.size
        val out = nanSeries(size)
        if (size < 2 || low.size < size || step <= 0.0 || max < step) return out

        var seed = -1
        for (index in 1 until size) {
            if (high[index - 1].isFinite() && low[index - 1].isFinite() &&
                high[index].isFinite() && low[index].isFinite()
            ) {
                seed = index
                break
            }
        }
        if (seed < 0) return out

        var rising = (high[seed] - high[seed - 1]) >= (low[seed - 1] - low[seed])
        var extreme = if (rising) high[seed] else low[seed]
        var stop = if (rising) low[seed - 1] else high[seed - 1]
        var acceleration = step
        out[seed] = stop

        for (index in seed + 1 until size) {
            if (!high[index].isFinite() || !low[index].isFinite()) continue
            var value = stop + acceleration * (extreme - stop)
            val previousHigh = high[index - 1]
            val previousLow = low[index - 1]
            val earlierHigh = if (index >= 2) high[index - 2] else Double.NaN
            val earlierLow = if (index >= 2) low[index - 2] else Double.NaN
            if (rising) {
                if (previousLow.isFinite() && value > previousLow) value = previousLow
                if (earlierLow.isFinite() && value > earlierLow) value = earlierLow
                if (low[index] < value) {
                    rising = false
                    val guard = if (previousHigh.isFinite()) previousHigh else high[index]
                    value = maxOf(extreme, high[index], guard)
                    extreme = low[index]
                    acceleration = step
                } else if (high[index] > extreme) {
                    extreme = high[index]
                    acceleration = minOf(acceleration + step, max)
                }
            } else {
                if (previousHigh.isFinite() && value < previousHigh) value = previousHigh
                if (earlierHigh.isFinite() && value < earlierHigh) value = earlierHigh
                if (high[index] > value) {
                    rising = true
                    val guard = if (previousLow.isFinite()) previousLow else low[index]
                    value = minOf(extreme, low[index], guard)
                    extreme = high[index]
                    acceleration = step
                } else if (low[index] < extreme) {
                    extreme = low[index]
                    acceleration = minOf(acceleration + step, max)
                }
            }
            out[index] = value
            stop = value
        }
        return out
    }

    /**
     * [57] Bill Williams' Alligator: three smoothed averages of the median price, pushed forward.
     *
     * The jaw is a 13-period SMMA displaced 8 bars, the teeth an 8 displaced 5, the lips a 5
     * displaced 3. The displacement is the indicator — three undisplaced averages of the same
     * series simply fan out in period order and never cross in the way the Alligator is read for.
     *
     * Forward means later: the average computed on bar `i` is published at bar `i + shift`, so the
     * jaw's first value lands at index 20 on a clean series rather than at 12, and the last eight
     * bars of the jaw are values the chart cannot show because the bars they belong to have not
     * happened yet. Those are dropped rather than crowded onto the final bar, which would put a
     * flat spur on the right-hand edge of every one of the three lines.
     */
    fun alligator(high: DoubleArray, low: DoubleArray): AlligatorSeries {
        val size = high.size
        val jaw = nanSeries(size)
        val teeth = nanSeries(size)
        val lips = nanSeries(size)
        if (low.size < size) return AlligatorSeries(jaw, teeth, lips)
        val median = DoubleArray(size) { medianPrice(high[it], low[it]) }
        val scratch = DoubleArray(size)
        displaceInto(median, 13, 8, scratch, jaw)
        displaceInto(median, 8, 5, scratch, teeth)
        displaceInto(median, 5, 3, scratch, lips)
        return AlligatorSeries(jaw, teeth, lips)
    }

    /**
     * [63] The directional movement system: +DI, −DI and the ADX computed from them.
     *
     * [Indicators.adx] already returns all three, and this is deliberately a second reading rather
     * than a call into it. The two are read as different indicators — ADX with its DI lines is a
     * trend-strength pane, DMI on its own is a crossover system — and a trader who puts both on the
     * chart with different lookbacks needs them to be genuinely separate series rather than the
     * same object drawn twice.
     *
     * A bar that expanded on both sides contributes to neither direction: only the larger of the
     * two moves counts, which is what stops an inside-out bar from registering as a trend in both
     * directions at once. The ADX is Wilder's average of the DX, so it starts a full period after
     * the DI lines do — at bar `2·period − 1`, not at `period`.
     */
    fun directionalMovement(
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        period: Int = 14,
    ): DirectionalMovementSeries {
        val size = close.size
        val plusDi = nanSeries(size)
        val minusDi = nanSeries(size)
        val adx = nanSeries(size)
        if (period < 1 || high.size < size || low.size < size || size <= period) {
            return DirectionalMovementSeries(plusDi, minusDi, adx)
        }
        val range = nanSeries(size)
        val plusMove = nanSeries(size)
        val minusMove = nanSeries(size)
        for (index in 1 until size) {
            if (!high[index].isFinite() || !low[index].isFinite() || !close[index - 1].isFinite() ||
                !high[index - 1].isFinite() || !low[index - 1].isFinite()
            ) {
                continue
            }
            range[index] = maxOf(
                high[index] - low[index],
                abs(high[index] - close[index - 1]),
                abs(low[index] - close[index - 1]),
            )
            val up = high[index] - high[index - 1]
            val down = low[index - 1] - low[index]
            plusMove[index] = if (up > down && up > 0) up else 0.0
            minusMove[index] = if (down > up && down > 0) down else 0.0
        }
        val smoothedRange = nanSeries(size)
        val smoothedPlus = nanSeries(size)
        val smoothedMinus = nanSeries(size)
        wilderInto(range, period, smoothedRange)
        wilderInto(plusMove, period, smoothedPlus)
        wilderInto(minusMove, period, smoothedMinus)

        val directionalIndex = nanSeries(size)
        for (index in 0 until size) {
            val total = smoothedRange[index]
            if (!total.isFinite() || total <= 0.0) continue
            val plus = 100 * smoothedPlus[index] / total
            val minus = 100 * smoothedMinus[index] / total
            if (!plus.isFinite() || !minus.isFinite()) continue
            plusDi[index] = plus
            minusDi[index] = minus
            val spread = plus + minus
            directionalIndex[index] = if (spread > 0) 100 * abs(plus - minus) / spread else 0.0
        }
        wilderInto(directionalIndex, period, adx)
        return DirectionalMovementSeries(plusDi, minusDi, adx)
    }

    // ══════════════════════════════════════════════════════════ moving averages

    /**
     * [58] Volume-weighted moving average: the window's average price with each bar counted as
     * many times as it traded.
     *
     * Not a VWAP. VWAP is cumulative from an anchor and answers "what did everyone pay"; this is a
     * rolling window and answers "where is the average now", so it can be compared against a plain
     * SMA of the same length. The difference between the two is the honest measure of whether a
     * move happened on real participation.
     *
     * A window whose volume sums to zero has no weighted average — no trades, no price anybody
     * paid — and is left absent rather than falling back to the unweighted mean, which would draw
     * a confident line through a period in which nothing happened.
     */
    fun vwma(close: DoubleArray, volume: DoubleArray, period: Int): DoubleArray {
        val size = close.size
        val out = nanSeries(size)
        if (period < 1 || volume.size < size || size < period) return out
        for (index in period - 1 until size) {
            var weighted = 0.0
            var traded = 0.0
            var complete = true
            for (step in 0 until period) {
                val price = close[index - step]
                val lot = volume[index - step]
                if (!price.isFinite() || !lot.isFinite()) {
                    complete = false
                    break
                }
                weighted += price * lot
                traded += lot
            }
            if (complete && traded > 0.0) out[index] = weighted / traded
        }
        return out
    }

    /**
     * [59] Triple exponential moving average: `3·EMA1 − 3·EMA2 + EMA3`.
     *
     * The three EMAs are chained, each one smoothing the previous one's output, and the
     * recombination subtracts most of the lag the chain introduced while keeping most of the
     * smoothing. It is not a triple-smoothed average — that would be `EMA3` alone, which lags
     * three times as much as one.
     *
     * The price is warm-up: each link in the chain can only start once the previous one has
     * published [period] values, so TEMA has nothing to say until bar `3·(period − 1)`. Reporting
     * anything earlier means reporting a value built on an EMA that is still mostly its own seed.
     */
    fun tema(values: DoubleArray, period: Int): DoubleArray {
        val size = values.size
        val out = nanSeries(size)
        if (period < 1 || size < period) return out
        val first = nanSeries(size)
        val second = nanSeries(size)
        val third = nanSeries(size)
        emaInto(values, period, first)
        emaInto(first, period, second)
        emaInto(second, period, third)
        for (index in 0 until size) {
            if (first[index].isFinite() && second[index].isFinite() && third[index].isFinite()) {
                out[index] = 3 * first[index] - 3 * second[index] + third[index]
            }
        }
        return out
    }

    /**
     * [60] Double exponential moving average: `2·EMA1 − EMA2`.
     *
     * The same lag-cancelling trick as [tema] with one fewer stage, so it turns earlier and
     * overshoots more. Which of the two a reader wants is a genuine choice rather than a question
     * with a right answer, and both are offered for that reason.
     */
    fun dema(values: DoubleArray, period: Int): DoubleArray {
        val size = values.size
        val out = nanSeries(size)
        if (period < 1 || size < period) return out
        val first = nanSeries(size)
        val second = nanSeries(size)
        emaInto(values, period, first)
        emaInto(first, period, second)
        for (index in 0 until size) {
            if (first[index].isFinite() && second[index].isFinite()) {
                out[index] = 2 * first[index] - second[index]
            }
        }
        return out
    }

    // ══════════════════════════════════════════════════════════ momentum

    /**
     * [53] Stochastic RSI: where the RSI sits inside its own recent range.
     *
     * A stochastic of the RSI rather than of price, which is the point — RSI spends long stretches
     * between 40 and 60 without touching either of the levels anybody watches, and this rescales
     * whatever range it actually used to the full 0..100. It is correspondingly noisy, which is why
     * the raw ratio is never plotted: %K is a [kSmooth]-bar average of it and %D a [dSmooth]-bar
     * average of %K.
     *
     * The warm-ups stack, and that surprises people: the first %D needs `rsiPeriod + stochPeriod +
     * kSmooth + dSmooth − 3` bars, which at the defaults is 41 bars before a single dot appears.
     * A window in which the RSI never moved is reported as 50 rather than divided by zero — dead
     * centre is the honest reading of a range with no width.
     */
    fun stochasticRsi(
        close: DoubleArray,
        rsiPeriod: Int = 14,
        stochPeriod: Int = 14,
        kSmooth: Int = 3,
        dSmooth: Int = 3,
    ): StochasticRsiSeries {
        val size = close.size
        val k = nanSeries(size)
        val d = nanSeries(size)
        if (rsiPeriod < 1 || stochPeriod < 1 || kSmooth < 1 || dSmooth < 1) {
            return StochasticRsiSeries(k, d)
        }
        if (size < rsiPeriod + stochPeriod) return StochasticRsiSeries(k, d)
        val strength = nanSeries(size)
        rsiInto(close, rsiPeriod, strength)
        val raw = nanSeries(size)
        for (index in stochPeriod - 1 until size) {
            var highest = -Double.MAX_VALUE
            var lowest = Double.MAX_VALUE
            var complete = true
            for (step in 0 until stochPeriod) {
                val value = strength[index - step]
                if (!value.isFinite()) {
                    complete = false
                    break
                }
                if (value > highest) highest = value
                if (value < lowest) lowest = value
            }
            if (!complete) continue
            val span = highest - lowest
            raw[index] = if (span > 0) 100 * (strength[index] - lowest) / span else 50.0
        }
        smaInto(raw, kSmooth, k)
        smaInto(k, dSmooth, d)
        return StochasticRsiSeries(k, d)
    }

    /**
     * [56] The Awesome Oscillator: a 5-bar average of the median price minus a 34-bar one.
     *
     * Median price rather than close, which is Williams' whole argument — the close is where the
     * bar happened to stop, the midpoint is where it was traded — and the two periods are fixed
     * rather than configurable because a reader comparing their AO against anyone else's is
     * comparing 5 and 34.
     */
    fun awesomeOscillator(high: DoubleArray, low: DoubleArray): DoubleArray {
        val size = high.size
        val out = nanSeries(size)
        if (low.size < size || size < 34) return out
        val median = DoubleArray(size) { medianPrice(high[it], low[it]) }
        val fast = nanSeries(size)
        val slow = nanSeries(size)
        smaInto(median, 5, fast)
        smaInto(median, 34, slow)
        for (index in 0 until size) {
            if (fast[index].isFinite() && slow[index].isFinite()) out[index] = fast[index] - slow[index]
        }
        return out
    }

    /**
     * [61] The True Strength Index: momentum smoothed twice, divided by its own magnitude smoothed
     * the same way.
     *
     * The double smoothing is what makes it readable — one pass over bar-to-bar change is noise —
     * and dividing by the identically smoothed absolute change is what bounds it to ±100 and makes
     * two symbols comparable. Because the numerator is signed and the denominator is not, a series
     * that rose and fell equally reads zero rather than reading "no movement", which is the
     * distinction the indicator exists to draw.
     *
     * A window with no movement at all divides zero by zero; that is reported as zero, since no
     * change in either direction is genuinely no strength in either direction.
     */
    fun trueStrengthIndex(
        close: DoubleArray,
        long: Int = 25,
        short: Int = 13,
        signal: Int = 13,
    ): TrueStrengthSeries {
        val size = close.size
        val line = nanSeries(size)
        val signalLine = nanSeries(size)
        if (long < 1 || short < 1 || signal < 1 || size < 2) return TrueStrengthSeries(line, signalLine)
        val change = nanSeries(size)
        val magnitude = nanSeries(size)
        for (index in 1 until size) {
            if (!close[index].isFinite() || !close[index - 1].isFinite()) continue
            val delta = close[index] - close[index - 1]
            change[index] = delta
            magnitude[index] = abs(delta)
        }
        val changeOnce = nanSeries(size)
        val changeTwice = nanSeries(size)
        val magnitudeOnce = nanSeries(size)
        val magnitudeTwice = nanSeries(size)
        emaInto(change, long, changeOnce)
        emaInto(changeOnce, short, changeTwice)
        emaInto(magnitude, long, magnitudeOnce)
        emaInto(magnitudeOnce, short, magnitudeTwice)
        for (index in 0 until size) {
            val numerator = changeTwice[index]
            val denominator = magnitudeTwice[index]
            if (!numerator.isFinite() || !denominator.isFinite()) continue
            line[index] = if (denominator != 0.0) 100 * numerator / denominator else 0.0
        }
        emaInto(line, signal, signalLine)
        return TrueStrengthSeries(line, signalLine)
    }

    /**
     * [62] Aroon: how long ago, as a percentage of the lookback, the window last made a new
     * extreme.
     *
     * Alone among the momentum indicators it measures time rather than price. A hundred on the up
     * line means the highest high of the window is this bar; zero means it is the oldest bar in the
     * window and nothing has exceeded it since.
     *
     * The window is `period + 1` bars — the current bar plus [period] before it — which is what
     * makes both endpoints of the scale reachable: a fresh high scores 100 and an extreme that has
     * survived the whole lookback scores 0. Implementations that scan only [period] bars can never
     * print a clean zero and quietly disagree with every published Aroon by one bar. On a tie the
     * more recent extreme wins, because "the high was set five bars ago and equalled today" is a
     * high set today.
     */
    fun aroon(high: DoubleArray, low: DoubleArray, period: Int = 14): AroonSeries {
        val size = high.size
        val up = nanSeries(size)
        val down = nanSeries(size)
        if (period < 1 || low.size < size || size <= period) return AroonSeries(up, down)
        for (index in period until size) {
            var highest = -Double.MAX_VALUE
            var lowest = Double.MAX_VALUE
            var sinceHigh = 0
            var sinceLow = 0
            var complete = true
            for (step in 0..period) {
                val top = high[index - step]
                val bottom = low[index - step]
                if (!top.isFinite() || !bottom.isFinite()) {
                    complete = false
                    break
                }
                if (top > highest) {
                    highest = top
                    sinceHigh = step
                }
                if (bottom < lowest) {
                    lowest = bottom
                    sinceLow = step
                }
            }
            if (!complete) continue
            up[index] = 100.0 * (period - sinceHigh) / period
            down[index] = 100.0 * (period - sinceLow) / period
        }
        return AroonSeries(up, down)
    }

    /**
     * [64] Percentage price oscillator: MACD expressed as a percentage of the slow average.
     *
     * The reason to prefer it over MACD is that MACD's units are the symbol's units. A MACD of 40
     * is enormous on a twenty-dollar coin and invisible on a sixty-thousand-dollar one, and no
     * MACD reading can be compared against another symbol's or against the same symbol two years
     * and one bull market ago. PPO divides that out and is directly comparable in both directions.
     */
    fun ppo(
        close: DoubleArray,
        fast: Int = 12,
        slow: Int = 26,
        signal: Int = 9,
    ): PercentageOscillatorSeries = percentageOscillator(close, fast, slow, signal)

    /**
     * [65] Percentage volume oscillator: the same construction as [ppo], run over volume.
     *
     * It answers a different question from any price oscillator — whether participation is
     * expanding or drying up — and it is read together with one, because volume expanding into a
     * price move and volume expanding against it mean opposite things.
     */
    fun pvo(
        volume: DoubleArray,
        fast: Int = 12,
        slow: Int = 26,
        signal: Int = 9,
    ): PercentageOscillatorSeries = percentageOscillator(volume, fast, slow, signal)

    // ══════════════════════════════════════════════════════════ volume

    /**
     * [54] The Money Flow Index: an RSI with every bar weighted by what it traded.
     *
     * The arithmetic is Wilder's RSI applied to typical price times volume instead of to price
     * alone, which is why it is sometimes called volume-weighted RSI and why the same 20/80 levels
     * are drawn on it. Two bars that moved identically count differently here, and that is the
     * entire reason to run it beside an RSI: a divergence between the two is a move the volume did
     * not support.
     *
     * A window in which no bar closed lower has no negative flow to divide by and reads 100, which
     * is correct. A window in which the typical price never moved at all has neither positive nor
     * negative flow and reads 50, because a market that did not move is not overbought.
     */
    fun moneyFlowIndex(
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        volume: DoubleArray,
        period: Int = 14,
    ): DoubleArray {
        val size = close.size
        val out = nanSeries(size)
        if (period < 1 || high.size < size || low.size < size || volume.size < size) return out
        if (size <= period) return out
        val typical = nanSeries(size)
        for (index in 0 until size) {
            if (high[index].isFinite() && low[index].isFinite() && close[index].isFinite()) {
                typical[index] = (high[index] + low[index] + close[index]) / 3
            }
        }
        for (index in period until size) {
            var positive = 0.0
            var negative = 0.0
            var complete = true
            for (step in 0 until period) {
                val at = index - step
                val now = typical[at]
                val before = typical[at - 1]
                val traded = volume[at]
                if (!now.isFinite() || !before.isFinite() || !traded.isFinite()) {
                    complete = false
                    break
                }
                val flow = now * traded
                if (now > before) positive += flow else if (now < before) negative += flow
            }
            if (!complete) continue
            out[index] = when {
                positive == 0.0 && negative == 0.0 -> 50.0
                negative == 0.0 -> 100.0
                else -> 100 - 100 / (1 + positive / negative)
            }
        }
        return out
    }

    /**
     * [55] Chaikin Money Flow: where in its own range each bar closed, weighted by volume and
     * summed over the window.
     *
     * The multiplier is +1 for a bar that closed on its high and −1 for one that closed on its low,
     * so the sign of the result is "did the volume of the last [period] bars arrive on strength or
     * on weakness". It ignores gaps entirely — a bar is measured only against itself — which is
     * both its weakness on a gapping market and the reason it is stable on a continuously traded
     * one.
     *
     * A window that traded nothing has no reading at all rather than a reading of zero. Zero here
     * means balanced buying and selling, which is a claim about a market; absence is the claim
     * about the data, and the two must not be drawn as the same line.
     */
    fun chaikinMoneyFlow(
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        volume: DoubleArray,
        period: Int = 20,
    ): DoubleArray {
        val size = close.size
        val out = nanSeries(size)
        if (period < 1 || high.size < size || low.size < size || volume.size < size) return out
        if (size < period) return out
        for (index in period - 1 until size) {
            var flow = 0.0
            var traded = 0.0
            var complete = true
            for (step in 0 until period) {
                val at = index - step
                val top = high[at]
                val bottom = low[at]
                val last = close[at]
                val lot = volume[at]
                if (!top.isFinite() || !bottom.isFinite() || !last.isFinite() || !lot.isFinite()) {
                    complete = false
                    break
                }
                val span = top - bottom
                val multiplier = if (span != 0.0) ((last - bottom) - (top - last)) / span else 0.0
                flow += multiplier * lot
                traded += lot
            }
            if (complete && traded > 0.0) out[index] = flow / traded
        }
        return out
    }

    // ══════════════════════════════════════════════════════════ shared arithmetic

    /** A fresh series of the right length with nothing in it yet. The starting point of every result. */
    private fun nanSeries(size: Int) = DoubleArray(size) { Double.NaN }

    private fun medianPrice(high: Double, low: Double): Double =
        if (high.isFinite() && low.isFinite()) (high + low) / 2 else Double.NaN

    /**
     * A simple moving average written into [out], with a gap in the window suppressing only the
     * readings whose window contains it.
     *
     * The running sum is rebuilt rather than adjusted whenever a gap is involved, which is what
     * stops one missing bar from leaving a residue in the total for the rest of the series.
     */
    private fun smaInto(source: DoubleArray, period: Int, out: DoubleArray) {
        var sum = 0.0
        var gaps = 0
        for (index in source.indices) {
            val entering = source[index]
            if (entering.isFinite()) sum += entering else gaps++
            if (index >= period) {
                val leaving = source[index - period]
                if (leaving.isFinite()) sum -= leaving else gaps--
            }
            if (index >= period - 1) out[index] = if (gaps == 0) sum / period else Double.NaN
        }
    }

    /**
     * An exponential moving average written into [out], seeded on the first real sample and
     * published once [period] real samples have been folded in.
     *
     * Counting *samples* rather than bars is what makes chaining safe: an EMA over another
     * indicator's output starts a full period after that output starts, rather than a period after
     * the series does, and a gap costs one reading instead of shifting every later one.
     */
    private fun emaInto(source: DoubleArray, period: Int, out: DoubleArray) {
        val weight = 2.0 / (period + 1)
        var previous = 0.0
        var seen = 0
        for (index in source.indices) {
            val value = source[index]
            if (!value.isFinite()) continue
            previous = if (seen == 0) value else value * weight + previous * (1 - weight)
            seen++
            if (seen >= period) out[index] = previous
        }
    }

    /**
     * Wilder's smoothing written into [out], seeded with the mean of the first [period] real
     * samples.
     *
     * Wilder's own seed, not an EMA seeded on one bar. The difference is small and permanent: an
     * ADX seeded the other way never converges back onto the published one, and a reader comparing
     * against any terminal would find this app's ADX a point or two out forever.
     */
    private fun wilderInto(source: DoubleArray, period: Int, out: DoubleArray) {
        var previous = 0.0
        var sum = 0.0
        var seen = 0
        var seeded = false
        for (index in source.indices) {
            val value = source[index]
            if (!value.isFinite()) continue
            if (!seeded) {
                sum += value
                seen++
                if (seen == period) {
                    previous = sum / period
                    seeded = true
                    out[index] = previous
                }
            } else {
                previous = (previous * (period - 1) + value) / period
                out[index] = previous
            }
        }
    }

    /**
     * A smoothed moving average of [source] pushed [shift] bars into the future, via [scratch] so
     * the three Alligator lines share one buffer instead of allocating three.
     */
    private fun displaceInto(
        source: DoubleArray,
        period: Int,
        shift: Int,
        scratch: DoubleArray,
        out: DoubleArray,
    ) {
        for (index in scratch.indices) scratch[index] = Double.NaN
        wilderInto(source, period, scratch)
        for (index in scratch.indices) {
            val target = index + shift
            if (target < out.size) out[target] = scratch[index]
        }
    }

    /**
     * Wilder's RSI written into [out].
     *
     * A private copy rather than a call into [Indicators.rsi] because that one returns a [Line] and
     * the Stochastic RSI needs to run a stochastic straight over the numbers; converting a masked
     * line back into an array to do it would be the one hand-off this pack exists to avoid.
     */
    private fun rsiInto(close: DoubleArray, period: Int, out: DoubleArray) {
        var averageGain = 0.0
        var averageLoss = 0.0
        var seen = 0
        var seeded = false
        for (index in 1 until close.size) {
            if (!close[index].isFinite() || !close[index - 1].isFinite()) continue
            val change = close[index] - close[index - 1]
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) -change else 0.0
            if (!seeded) {
                averageGain += gain
                averageLoss += loss
                seen++
                if (seen == period) {
                    averageGain /= period
                    averageLoss /= period
                    seeded = true
                    out[index] = relativeStrength(averageGain, averageLoss)
                }
            } else {
                averageGain = (averageGain * (period - 1) + gain) / period
                averageLoss = (averageLoss * (period - 1) + loss) / period
                out[index] = relativeStrength(averageGain, averageLoss)
            }
        }
    }

    // The same floored ratio [Indicators] uses, and floored for the same reason: fourteen bars
    // without a single down close is a real state of the market, and it reads 100 rather than
    // dividing by zero.
    private fun relativeStrength(gain: Double, loss: Double): Double =
        100 - 100 / (1 + gain / (if (loss == 0.0) 1e-9 else loss))

    /**
     * The shared body of [ppo] and [pvo]: a fast and a slow EMA, their difference as a percentage
     * of the slow one, and an EMA of that.
     *
     * The signal is an EMA of the oscillator rather than of the underlying difference, so it warms
     * up [signal] samples after the oscillator does and not [signal] bars after the series does.
     */
    private fun percentageOscillator(
        source: DoubleArray,
        fast: Int,
        slow: Int,
        signal: Int,
    ): PercentageOscillatorSeries {
        val size = source.size
        val oscillator = nanSeries(size)
        val signalLine = nanSeries(size)
        val histogram = nanSeries(size)
        if (fast < 1 || slow < 1 || signal < 1 || size < maxOf(fast, slow)) {
            return PercentageOscillatorSeries(oscillator, signalLine, histogram)
        }
        val quick = nanSeries(size)
        val slowly = nanSeries(size)
        emaInto(source, fast, quick)
        emaInto(source, slow, slowly)
        for (index in 0 until size) {
            val base = slowly[index]
            if (!quick[index].isFinite() || !base.isFinite() || base == 0.0) continue
            oscillator[index] = 100 * (quick[index] - base) / base
        }
        emaInto(oscillator, signal, signalLine)
        for (index in 0 until size) {
            if (oscillator[index].isFinite() && signalLine[index].isFinite()) {
                histogram[index] = oscillator[index] - signalLine[index]
            }
        }
        return PercentageOscillatorSeries(oscillator, signalLine, histogram)
    }
}
