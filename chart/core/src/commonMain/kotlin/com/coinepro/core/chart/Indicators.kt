package com.coinepro.core.chart

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A series aligned to the bars, with a null wherever the indicator has not warmed up.
 *
 * The nulls are the whole point and are not an implementation detail to be filled in. A 200-period
 * moving average genuinely does not exist for the first 199 bars, and every alternative to saying
 * so is a lie the chart draws: zero puts a line along the floor, the first real value flat-lines
 * the start, and dropping the entries silently shifts the series left so it no longer lines up with
 * the price it is supposed to describe.
 *
 * `DoubleArray` with a parallel presence mask rather than `Array<Double?>`, because these are one
 * per bar per indicator per redraw and boxing a thousand doubles per frame is a real cost.
 */
class Line(private val values: DoubleArray, private val present: BooleanArray) {

    init {
        require(values.size == present.size) { "value and presence arrays must match" }
    }

    val size: Int get() = values.size

    operator fun get(index: Int): Double? =
        if (index in values.indices && present[index]) values[index] else null

    fun isPresent(index: Int): Boolean = index in values.indices && present[index]

    /** The raw value, valid only where [isPresent]. For tight drawing loops. */
    fun raw(index: Int): Double = values[index]

    /** The range across present values, or null when the line is entirely warm-up. */
    fun extent(from: Int = 0, until: Int = size): Pair<Double, Double>? {
        var low = Double.MAX_VALUE
        var high = -Double.MAX_VALUE
        for (index in max(0, from) until min(size, until)) {
            if (!present[index]) continue
            if (values[index] < low) low = values[index]
            if (values[index] > high) high = values[index]
        }
        return if (high < low) null else low to high
    }

    fun toList(): List<Double?> = List(size) { get(it) }

    companion object {
        fun of(size: Int, compute: (Int) -> Double?): Line {
            val values = DoubleArray(size)
            val present = BooleanArray(size)
            for (index in 0 until size) {
                val value = compute(index)
                if (value != null && value.isFinite()) {
                    values[index] = value
                    present[index] = true
                }
            }
            return Line(values, present)
        }

        fun from(source: List<Double?>): Line = of(source.size) { source[it] }

        fun empty(size: Int): Line = Line(DoubleArray(size), BooleanArray(size))

        /**
         * One value repeated, without the boxing.
         *
         * [of] takes a `(Int) -> Double?`, so it boxes a `Double` per bar. That is the right shape
         * for a computed line and the wrong one for a constant: the volume profile draws three
         * horizontal levels through this, and at fifty thousand resident bars a pan step was boxing
         * a hundred and fifty thousand doubles to say three numbers.
         */
        fun constant(size: Int, value: Double): Line =
            if (!value.isFinite()) {
                empty(size)
            } else {
                Line(DoubleArray(size) { value }, BooleanArray(size) { true })
            }
    }
}

/** Two lines that are read together — a value and the average of it. */
data class LinePair(val line: Line, val signal: Line)

/** A band: a middle and two edges. */
data class Band(val basis: Line, val upper: Line, val lower: Line)

data class MacdResult(val macd: Line, val signal: Line, val histogram: Line)

data class StochasticResult(val k: Line, val d: Line)

data class IchimokuResult(val tenkan: Line, val kijun: Line, val spanA: Line, val spanB: Line)

data class SuperTrendResult(val line: Line, val trend: Line)

data class VortexResult(val plus: Line, val minus: Line)

data class AdxResult(val adx: Line, val plusDi: Line, val minusDi: Line)

/**
 * The indicator library, ported from the Bazaarnama web app's `indicators.js`.
 *
 * Ported rather than rewritten, and the arithmetic is deliberately kept identical to the original —
 * including the parts that look like they could be tidied. Two traders comparing this app's RSI
 * against the web terminal's must see the same number, and "I improved the warm-up" is exactly the
 * kind of improvement that makes two products disagree about whether an asset is overbought.
 *
 * Twenty indicators, which is the set the first chart ships. The web app has around ninety-five;
 * the rest are a mechanical continuation of these and are not blocked by anything.
 *
 * A note on the warm-up conventions, since they differ per indicator and are not arbitrary: an SMA
 * has no value until it has `period` samples; an EMA seeds from the first sample but is only
 * *published* from bar `period - 1`, because before that it is dominated by its seed; Wilder's
 * smoothing (ATR, ADX, RSI) seeds from a simple mean of the first `period` values, which is what
 * Wilder specified and what every terminal implements.
 */
object Indicators {

    // ---------------------------------------------------------------- moving averages

    fun sma(source: DoubleArray, period: Int): Line {
        require(period > 0) { "period must be positive" }
        val out = DoubleArray(source.size)
        val present = BooleanArray(source.size)
        var sum = 0.0
        for (index in source.indices) {
            sum += source[index]
            if (index >= period) sum -= source[index - period]
            if (index >= period - 1) {
                out[index] = sum / period
                present[index] = true
            }
        }
        return Line(out, present)
    }

    fun ema(source: DoubleArray, period: Int): Line {
        require(period > 0) { "period must be positive" }
        val out = DoubleArray(source.size)
        val present = BooleanArray(source.size)
        val k = 2.0 / (period + 1)
        var previous: Double? = null
        for (index in source.indices) {
            val value = source[index]
            previous = if (previous == null) value else value * k + previous * (1 - k)
            if (index >= period - 1) {
                out[index] = previous
                present[index] = true
            }
        }
        return Line(out, present)
    }

    fun wma(source: DoubleArray, period: Int): Line {
        require(period > 0) { "period must be positive" }
        val denominator = period * (period + 1) / 2.0
        return Line.of(source.size) { index ->
            if (index < period - 1) {
                null
            } else {
                var sum = 0.0
                for (step in 0 until period) sum += source[index - step] * (period - step)
                sum / denominator
            }
        }
    }

    /**
     * Hull moving average: `WMA(2·WMA(n/2) − WMA(n), √n)`.
     *
     * The inner difference has nulls in its warm-up, and the outer WMA cannot take them — so they
     * are passed through as zero and then masked back out afterwards. That is the original's
     * behaviour and it matters: filling with the first real value instead would bend the first
     * √n bars of the curve.
     */
    fun hma(source: DoubleArray, period: Int): Line {
        val half = max(1, period / 2)
        val root = max(1, sqrt(period.toDouble()).roundToInt())
        val fast = wma(source, half)
        val slow = wma(source, period)
        val difference = DoubleArray(source.size)
        val defined = BooleanArray(source.size)
        for (index in source.indices) {
            if (fast.isPresent(index) && slow.isPresent(index)) {
                difference[index] = 2 * fast.raw(index) - slow.raw(index)
                defined[index] = true
            }
        }
        val hull = wma(difference, root)
        return Line.of(source.size) { index ->
            if (defined[index] && hull.isPresent(index)) hull.raw(index) else null
        }
    }

    /** Wilder's smoothing — the running mean ATR, ADX and RSI are all built on. */
    private fun wilder(source: DoubleArray, period: Int, from: Int = 0): Line {
        val out = DoubleArray(source.size)
        val present = BooleanArray(source.size)
        var previous = 0.0
        var seeded = false
        for (index in from until source.size) {
            if (!seeded) {
                if (index - from >= period - 1) {
                    var sum = 0.0
                    for (step in 0 until period) sum += source[index - step]
                    previous = sum / period
                    seeded = true
                    out[index] = previous
                    present[index] = true
                }
                continue
            }
            previous = (previous * (period - 1) + source[index]) / period
            out[index] = previous
            present[index] = true
        }
        return Line(out, present)
    }

    // ---------------------------------------------------------------- momentum

    fun rsi(close: DoubleArray, period: Int = 14): Line {
        val out = DoubleArray(close.size)
        val present = BooleanArray(close.size)
        var averageGain = 0.0
        var averageLoss = 0.0
        for (index in 1 until close.size) {
            val change = close[index] - close[index - 1]
            val gain = max(change, 0.0)
            val loss = max(-change, 0.0)
            if (index <= period) {
                averageGain += gain
                averageLoss += loss
                if (index == period) {
                    averageGain /= period
                    averageLoss /= period
                    out[index] = relativeStrength(averageGain, averageLoss)
                    present[index] = true
                }
            } else {
                averageGain = (averageGain * (period - 1) + gain) / period
                averageLoss = (averageLoss * (period - 1) + loss) / period
                out[index] = relativeStrength(averageGain, averageLoss)
                present[index] = true
            }
        }
        return Line(out, present)
    }

    // The floor stops a run with no losses dividing by zero. It resolves to 100, which is correct:
    // an asset that has only risen for fourteen bars *is* at the top of the scale.
    private fun relativeStrength(gain: Double, loss: Double): Double =
        100 - 100 / (1 + gain / (if (loss == 0.0) 1e-9 else loss))

    fun macd(
        close: DoubleArray,
        fast: Int = 12,
        slow: Int = 26,
        signalPeriod: Int = 9,
    ): MacdResult {
        val fastLine = ema(close, fast)
        val slowLine = ema(close, slow)
        val defined = BooleanArray(close.size)
        val difference = DoubleArray(close.size)
        for (index in close.indices) {
            if (fastLine.isPresent(index) && slowLine.isPresent(index)) {
                difference[index] = fastLine.raw(index) - slowLine.raw(index)
                defined[index] = true
            }
        }
        val rawSignal = ema(difference, signalPeriod)
        val macdLine = Line.of(close.size) { if (defined[it]) difference[it] else null }
        val signal = Line.of(close.size) {
            if (defined[it] && rawSignal.isPresent(it)) rawSignal.raw(it) else null
        }
        val histogram = Line.of(close.size) { index ->
            val line = macdLine[index]
            val average = signal[index]
            if (line != null && average != null) line - average else null
        }
        return MacdResult(macdLine, signal, histogram)
    }

    fun cci(high: DoubleArray, low: DoubleArray, close: DoubleArray, period: Int = 20): Line {
        val typical = DoubleArray(close.size) { (high[it] + low[it] + close[it]) / 3 }
        val mean = sma(typical, period)
        return Line.of(close.size) { index ->
            if (!mean.isPresent(index)) {
                null
            } else {
                var deviation = 0.0
                for (step in 0 until period) deviation += abs(typical[index - step] - mean.raw(index))
                deviation /= period
                // Lambert's constant. It scales the result so roughly 70-80% of readings fall
                // inside ±100, which is the only reason those two numbers are the levels everybody
                // draws.
                if (deviation == 0.0) 0.0 else (typical[index] - mean.raw(index)) / (0.015 * deviation)
            }
        }
    }

    fun williamsR(high: DoubleArray, low: DoubleArray, close: DoubleArray, period: Int = 14): Line =
        Line.of(close.size) { index ->
            if (index < period - 1) {
                null
            } else {
                val (highest, lowest) = extremes(high, low, index, period)
                if (highest == lowest) -50.0 else (highest - close[index]) / (highest - lowest) * -100
            }
        }

    fun stochastic(
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        period: Int = 14,
        smoothing: Int = 3,
    ): StochasticResult {
        val raw = DoubleArray(close.size)
        val defined = BooleanArray(close.size)
        for (index in period - 1 until close.size) {
            val (highest, lowest) = extremes(high, low, index, period)
            raw[index] = if (highest == lowest) 50.0 else (close[index] - lowest) / (highest - lowest) * 100
            defined[index] = true
        }
        val smoothed = sma(raw, smoothing)
        // %D is a mean of %K, so it is not a reading until its whole window is one. The head of
        // `raw` is zero because that is what an empty `DoubleArray` holds, not because price sat at
        // the bottom of its range, and averaging that in drags the first `smoothing - 1` values of
        // %D down towards zero — where they cross %K, on a chart that is only warming up, and every
        // strategy reading that cross takes a trade on it.
        //
        // Settled at `period - 1` — where %K itself becomes real — plus the smoothing's own
        // `smoothing - 1`. Before that it is null, which is a value the whole `Line` machinery
        // already knows how to not draw.
        val settled = period - 1 + smoothing - 1
        return StochasticResult(
            k = Line.of(close.size) { if (defined[it]) raw[it] else null },
            d = Line.of(close.size) {
                if (it >= settled && defined[it] && smoothed.isPresent(it)) smoothed.raw(it) else null
            },
        )
    }

    // ---------------------------------------------------------------- volatility

    /** True range: the widest of the bar, the gap up, and the gap down. */
    fun trueRange(high: DoubleArray, low: DoubleArray, close: DoubleArray): DoubleArray =
        DoubleArray(close.size) { index ->
            if (index == 0) {
                high[index] - low[index]
            } else {
                maxOf(
                    high[index] - low[index],
                    abs(high[index] - close[index - 1]),
                    abs(low[index] - close[index - 1]),
                )
            }
        }

    fun atr(high: DoubleArray, low: DoubleArray, close: DoubleArray, period: Int = 14): Line =
        wilder(trueRange(high, low, close), period)

    fun bollinger(close: DoubleArray, period: Int = 20, multiplier: Double = 2.0): Band {
        val basis = sma(close, period)
        val upper = DoubleArray(close.size)
        val lower = DoubleArray(close.size)
        val present = BooleanArray(close.size)
        for (index in period - 1 until close.size) {
            var sum = 0.0
            for (step in 0 until period) {
                val delta = close[index - step] - basis.raw(index)
                sum += delta * delta
            }
            // Population deviation, dividing by n rather than n-1. That is what the original does
            // and what TradingView does; the sample form would make the bands very slightly wider
            // and the two products would disagree at every touch.
            val deviation = sqrt(sum / period)
            upper[index] = basis.raw(index) + multiplier * deviation
            lower[index] = basis.raw(index) - multiplier * deviation
            present[index] = true
        }
        return Band(basis, Line(upper, present), Line(lower, present))
    }

    fun keltner(
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        period: Int = 20,
        multiplier: Double = 2.0,
    ): Band {
        val basis = ema(close, period)
        val range = atr(high, low, close, 10)
        return Band(
            basis = basis,
            upper = Line.of(close.size) { index ->
                if (basis.isPresent(index) && range.isPresent(index)) {
                    basis.raw(index) + multiplier * range.raw(index)
                } else {
                    null
                }
            },
            lower = Line.of(close.size) { index ->
                if (basis.isPresent(index) && range.isPresent(index)) {
                    basis.raw(index) - multiplier * range.raw(index)
                } else {
                    null
                }
            },
        )
    }

    fun donchian(high: DoubleArray, low: DoubleArray, period: Int = 20): Band {
        val upper = DoubleArray(high.size)
        val lower = DoubleArray(high.size)
        val basis = DoubleArray(high.size)
        val present = BooleanArray(high.size)
        for (index in period - 1 until high.size) {
            val (highest, lowest) = extremes(high, low, index, period)
            upper[index] = highest
            lower[index] = lowest
            basis[index] = (highest + lowest) / 2
            present[index] = true
        }
        return Band(Line(basis, present), Line(upper, present), Line(lower, present))
    }

    /**
     * Choppiness index, 0 to 100. Above ~61.8 the market is ranging, below ~38.2 it is trending.
     *
     * It compares the distance price actually travelled against the distance it covered, so a
     * market that moved a lot and ended where it started scores high.
     */
    fun choppiness(high: DoubleArray, low: DoubleArray, close: DoubleArray, period: Int = 14): Line {
        val range = trueRange(high, low, close)
        return Line.of(close.size) { index ->
            if (index < period - 1) {
                null
            } else {
                var sum = 0.0
                for (step in 0 until period) sum += range[index - step]
                val (highest, lowest) = extremes(high, low, index, period)
                val span = highest - lowest
                if (span > 0) 100 * log10(sum / span) / log10(period.toDouble()) else null
            }
        }
    }

    // ---------------------------------------------------------------- trend

    fun supertrend(
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        period: Int = 10,
        multiplier: Double = 3.0,
    ): SuperTrendResult {
        val range = atr(high, low, close, period)
        val line = DoubleArray(close.size)
        val trend = DoubleArray(close.size)
        val present = BooleanArray(close.size)
        var upperBand: Double? = null
        var lowerBand: Double? = null
        var direction = 1
        for (index in close.indices) {
            if (!range.isPresent(index)) continue
            val middle = (high[index] + low[index]) / 2
            var upper = middle + multiplier * range.raw(index)
            var lower = middle - multiplier * range.raw(index)
            // Wilder's rule, and it runs the other way from the one that reads naturally here. A
            // band keeps its previous reading unless the last close went through it, and while a
            // trend holds the surviving band is the one *nearer* price — the higher of the two
            // lower bands, the lower of the two upper ones. That is the ratchet: it tightens as the
            // trend runs, so an ordinary pullback into it ends the trend, which is the whole
            // signal.
            //
            // Taken the other way round, as it was, the band walks away from price instead: it
            // settles a fixed multiple of ATR from the current mid and stays there, and the trend
            // can then only flip on a single bar that travels several average ranges. Measured
            // against a recorded fixture, it flipped on synthetic shock bars and on nothing else —
            // an indicator that was on every chart and had stopped saying anything.
            if (upperBand != null && close[index - 1] <= upperBand) upper = min(upper, upperBand)
            if (lowerBand != null && close[index - 1] >= lowerBand) lower = max(lower, lowerBand)
            if (direction == 1 && close[index] < (lowerBand ?: lower)) {
                direction = -1
            } else if (direction == -1 && close[index] > (upperBand ?: upper)) {
                direction = 1
            }
            upperBand = upper
            lowerBand = lower
            trend[index] = direction.toDouble()
            line[index] = if (direction == 1) lower else upper
            present[index] = true
        }
        return SuperTrendResult(Line(line, present), Line(trend, present))
    }

    fun adx(high: DoubleArray, low: DoubleArray, close: DoubleArray, period: Int = 14): AdxResult {
        val size = close.size
        val range = DoubleArray(size)
        val plusMove = DoubleArray(size)
        val minusMove = DoubleArray(size)
        for (index in 1 until size) {
            range[index] = maxOf(
                high[index] - low[index],
                abs(high[index] - close[index - 1]),
                abs(low[index] - close[index - 1]),
            )
            val up = high[index] - high[index - 1]
            val down = low[index - 1] - low[index]
            // A bar that expanded in both directions counts for neither: only the *dominant* side
            // of the move is directional.
            plusMove[index] = if (up > down && up > 0) up else 0.0
            minusMove[index] = if (down > up && down > 0) down else 0.0
        }
        val smoothedRange = wilder(range, period, from = 1)
        val smoothedPlus = wilder(plusMove, period, from = 1)
        val smoothedMinus = wilder(minusMove, period, from = 1)

        val plusDi = Line.of(size) { index ->
            if (smoothedRange.isPresent(index) && smoothedRange.raw(index) > 0) {
                100 * smoothedPlus.raw(index) / smoothedRange.raw(index)
            } else {
                null
            }
        }
        val minusDi = Line.of(size) { index ->
            if (smoothedRange.isPresent(index) && smoothedRange.raw(index) > 0) {
                100 * smoothedMinus.raw(index) / smoothedRange.raw(index)
            } else {
                null
            }
        }
        val directionalIndex = DoubleArray(size)
        val defined = BooleanArray(size)
        for (index in 0 until size) {
            val plus = plusDi[index] ?: continue
            val minus = minusDi[index] ?: continue
            val total = plus + minus
            directionalIndex[index] = if (total > 0) 100 * abs(plus - minus) / total else 0.0
            defined[index] = true
        }
        val first = defined.indexOfFirst { it }
        val adx = if (first < 0) Line.empty(size) else wilder(directionalIndex, period, from = first)
        return AdxResult(adx, plusDi, minusDi)
    }

    fun vortex(high: DoubleArray, low: DoubleArray, close: DoubleArray, period: Int = 14): VortexResult {
        val size = close.size
        val range = trueRange(high, low, close)
        val plusMovement = DoubleArray(size)
        val minusMovement = DoubleArray(size)
        for (index in 1 until size) {
            plusMovement[index] = abs(high[index] - low[index - 1])
            minusMovement[index] = abs(low[index] - high[index - 1])
        }
        val plus = DoubleArray(size)
        val minus = DoubleArray(size)
        val present = BooleanArray(size)
        for (index in period until size) {
            var rangeSum = 0.0
            var plusSum = 0.0
            var minusSum = 0.0
            for (step in 0 until period) {
                rangeSum += range[index - step]
                plusSum += plusMovement[index - step]
                minusSum += minusMovement[index - step]
            }
            if (rangeSum > 0) {
                plus[index] = plusSum / rangeSum
                minus[index] = minusSum / rangeSum
                present[index] = true
            }
        }
        return VortexResult(Line(plus, present), Line(minus, present))
    }

    fun ichimoku(
        high: DoubleArray,
        low: DoubleArray,
        conversion: Int = 9,
        base: Int = 26,
        span: Int = 52,
    ): IchimokuResult {
        fun midpoint(period: Int) = Line.of(high.size) { index ->
            if (index < period - 1) {
                null
            } else {
                val (highest, lowest) = extremes(high, low, index, period)
                (highest + lowest) / 2
            }
        }
        val tenkan = midpoint(conversion)
        val kijun = midpoint(base)
        return IchimokuResult(
            tenkan = tenkan,
            kijun = kijun,
            spanA = Line.of(high.size) { index ->
                val fast = tenkan[index]
                val slow = kijun[index]
                if (fast != null && slow != null) (fast + slow) / 2 else null
            },
            spanB = midpoint(span),
        )
    }

    // ---------------------------------------------------------------- volume

    /**
     * On-balance volume: a running total that adds the bar's volume on an up close and subtracts
     * it on a down close.
     *
     * Its absolute value means nothing — it depends entirely on where the series happens to start.
     * Only its slope is read, which is why nothing here tries to normalise it.
     */
    fun obv(close: DoubleArray, volume: DoubleArray): Line {
        val out = DoubleArray(close.size)
        val present = BooleanArray(close.size)
        if (close.isEmpty()) return Line(out, present)
        present[0] = true
        var total = 0.0
        for (index in 1 until close.size) {
            total += when {
                close[index] > close[index - 1] -> volume[index]
                close[index] < close[index - 1] -> -volume[index]
                else -> 0.0
            }
            out[index] = total
            present[index] = true
        }
        return Line(out, present)
    }

    /**
     * Volume-weighted average price, cumulative from the first bar of the series.
     *
     * Deliberately *not* anchored to the session. A phone chart shows whatever window was loaded,
     * which is rarely a session boundary, and a VWAP that silently resets at a midnight the reader
     * cannot see is worse than one that plainly covers the visible history.
     */
    fun vwap(
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        volume: DoubleArray,
    ): Line {
        var priceVolume = 0.0
        var totalVolume = 0.0
        return Line.of(close.size) { index ->
            val typical = (high[index] + low[index] + close[index]) / 3
            priceVolume += typical * volume[index]
            totalVolume += volume[index]
            if (totalVolume > 0) priceVolume / totalVolume else close[index]
        }
    }

    // ---------------------------------------------------------------- shared

    /** The highest high and lowest low of the [period] bars ending at [index]. */
    private fun extremes(
        high: DoubleArray,
        low: DoubleArray,
        index: Int,
        period: Int,
    ): Pair<Double, Double> {
        var highest = -Double.MAX_VALUE
        var lowest = Double.MAX_VALUE
        for (step in 0 until period) {
            if (high[index - step] > highest) highest = high[index - step]
            if (low[index - step] < lowest) lowest = low[index - step]
        }
        return highest to lowest
    }
}
