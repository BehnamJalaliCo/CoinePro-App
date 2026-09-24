package com.coinepro.core.chart

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Three lines that are read as one band: a centre and the two edges drawn around it. */
data class BandSeries(val upper: DoubleArray, val basis: DoubleArray, val lower: DoubleArray)

/** Elder-ray: how far the high reached above the average (bulls) and the low fell below it (bears). */
data class ElderRaySeries(val bull: DoubleArray, val bear: DoubleArray)

/** Guppy's two ribbons, six averages each: the traders' (fast) and the investors' (slow). */
data class GuppySeries(val short: List<DoubleArray>, val long: List<DoubleArray>)

/** The Price Momentum Oscillator and the average it is read against. */
data class MomentumSignalSeries(val line: DoubleArray, val signal: DoubleArray)

/** A stop that is on one side of the price or the other: [long] below it, [short] above it. */
data class SidedStopSeries(val long: DoubleArray, val short: DoubleArray)

/** A turning bar: the index and whether it is a high ([high] true) or a low. */
data class PivotPoint(val index: Int, val high: Boolean)

/**
 * The fourth indicator pack: the studies Pro-Chart's web terminal offered that this app did not.
 *
 * Every function here is a port of the one in `BehnamJalaliCo/Pro-Chart`
 * (`frontend/prochart/src/bazaarnama/indicators.js` and `indicators_ext_b.js`), line for line and
 * warm-up for warm-up, and `IndicatorParityTest` holds each one to the JavaScript's own output at
 * 1e-6 (`scripts/design/generate-indicator-parity.mjs`). Where the original has a quirk — an EMA
 * that carries its last value over a gap, a seed on the first real value rather than on a simple
 * mean — the quirk is kept, because a reader switching from the site to the app should see the
 * same line, and each one is named where it happens.
 *
 * The same `DoubleArray`-with-`NaN` convention as [IndicatorsExtB] and [IndicatorsExtC]: `NaN` is
 * the JavaScript's `null`, and `ChartCatalog` is the one place it is turned into a [Line].
 */
object IndicatorsExtD {

    // ── Averages ──────────────────────────────────────────────────────────────────────────

    /**
     * Arnaud Legoux moving average: a Gaussian-weighted window whose peak sits at [offset] of the
     * way through it and whose width is the window over [sigma].
     */
    fun alma(source: DoubleArray, period: Int = 9, offset: Double = 0.85, sigma: Double = 6.0): DoubleArray {
        val n = source.size
        val out = nan(n)
        if (period < 1) return out
        val m = offset * (period - 1)
        val s = (period / sigma).takeIf { it != 0.0 } ?: 1e-9
        val weights = DoubleArray(period) { j -> exp(-((j - m) * (j - m)) / (2 * s * s)) }
        val total = weights.sum()
        for (i in period - 1 until n) {
            var acc = 0.0
            var ok = true
            for (j in 0 until period) {
                val v = source[i - (period - 1) + j]
                if (v.isNaN()) {
                    ok = false
                    break
                }
                acc += v * weights[j]
            }
            if (ok && total != 0.0) out[i] = acc / total
        }
        return out
    }

    /** [count] simple averages, [base] bars long and each [step] longer than the last. */
    fun movingAverageRibbon(close: DoubleArray, base: Int = 20, step: Int = 10, count: Int = 6): List<DoubleArray> =
        (0 until count).map { k -> sma(close, max(1, base + k * step)) }

    /** Guppy multiple moving averages: EMAs 3/5/8/10/12/15 and 30/35/40/45/50/60. */
    fun guppy(close: DoubleArray): GuppySeries = GuppySeries(
        short = GUPPY_SHORT.map { emaSkipping(close, it) },
        long = GUPPY_LONG.map { emaSkipping(close, it) },
    )

    /**
     * An EMA of a higher timeframe, [factor] bars to one, drawn on this one.
     *
     * With no lookahead: the higher bar that ends on bar `i·f + f − 1` is only known from the bar
     * after it, so each value is carried forward from there and the first [factor] bars are empty.
     * The site's first version stamped every bar of a bucket with the bucket's close, which let a
     * backtest see the future; this is the corrected one.
     */
    fun higherTimeframeEma(close: DoubleArray, period: Int = 50, factor: Int = 4): DoubleArray =
        expand(emaSkipping(resampleClose(close, factor), period), factor, close.size)

    /** The RSI of a higher timeframe, drawn on this one. See [higherTimeframeEma] for the carry. */
    fun higherTimeframeRsi(close: DoubleArray, period: Int = 14, factor: Int = 4): DoubleArray =
        expand(rsi(resampleClose(close, factor), period), factor, close.size)

    /**
     * VWAP anchored [anchorBars] bars back from the last bar, with bands one volume-weighted standard
     * deviation times [multiplier] either side.
     */
    fun anchoredVwap(
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        volume: DoubleArray,
        anchorBars: Int = 100,
        multiplier: Double = 1.0,
    ): BandSeries {
        val n = close.size
        val basis = nan(n)
        val upper = nan(n)
        val lower = nan(n)
        if (volume.none { it > 0 }) return BandSeries(upper, basis, lower)
        val start = max(0, n - (if (anchorBars > 0) anchorBars else n))
        val k = if (multiplier != 0.0) multiplier else 1.0
        var pv = 0.0
        var vv = 0.0
        var pv2 = 0.0
        for (i in start until n) {
            val typical = (high[i] + low[i] + close[i]) / 3
            val v = volume[i].takeIf { it.isFinite() } ?: 0.0
            pv += typical * v
            vv += v
            pv2 += typical * typical * v
            val vw = if (vv != 0.0) pv / vv else close[i]
            basis[i] = vw
            val variance = if (vv != 0.0) max(0.0, pv2 / vv - vw * vw) else 0.0
            val sd = sqrt(variance)
            upper[i] = vw + k * sd
            lower[i] = vw - k * sd
        }
        return BandSeries(upper, basis, lower)
    }

    /**
     * Standard error bands: the end of a least-squares line over [period] bars, and that end plus
     * and minus [multiplier] standard errors of the fit.
     */
    fun standardErrorBands(close: DoubleArray, period: Int = 21, multiplier: Double = 2.0): BandSeries {
        val n = close.size
        val mid = nan(n)
        val up = nan(n)
        val dn = nan(n)
        if (period < 1) return BandSeries(up, mid, dn)
        for (i in period - 1 until n) {
            var sx = 0.0
            var sy = 0.0
            var sxx = 0.0
            var sxy = 0.0
            for (j in 0 until period) {
                val x = j.toDouble()
                val y = close[i - period + 1 + j]
                sx += x
                sy += y
                sxx += x * x
                sxy += x * y
            }
            val denominator = (period * sxx - sx * sx).takeIf { it != 0.0 } ?: 1.0
            val b = (period * sxy - sx * sy) / denominator
            val a = (sy - b * sx) / period
            val fitted = a + b * (period - 1)
            var squared = 0.0
            for (j in 0 until period) {
                val e = close[i - period + 1 + j] - (a + b * j)
                squared += e * e
            }
            val error = sqrt(squared / max(1, period - 2))
            mid[i] = fitted
            up[i] = fitted + multiplier * error
            dn[i] = fitted - multiplier * error
        }
        return BandSeries(up, mid, dn)
    }

    /** The rolling median of [source] over [period] bars; an even window averages the middle two. */
    fun rollingMedian(source: DoubleArray, period: Int = 3): DoubleArray {
        val n = source.size
        val out = nan(n)
        if (period < 1) return out
        val window = DoubleArray(period)
        for (k in period - 1 until n) {
            var ok = true
            for (j in 0 until period) {
                val v = source[k - j]
                if (v.isNaN()) {
                    ok = false
                    break
                }
                window[j] = v
            }
            if (!ok) continue
            val sorted = window.sortedArray()
            out[k] = if (period % 2 == 1) sorted[(period - 1) / 2] else (sorted[period / 2 - 1] + sorted[period / 2]) / 2
        }
        return out
    }

    /** (high + low + close) / 3, bar by bar. */
    fun typicalPrice(high: DoubleArray, low: DoubleArray, close: DoubleArray): DoubleArray =
        DoubleArray(close.size) { (high[it] + low[it] + close[it]) / 3 }

    /** (high + low + 2·close) / 4, bar by bar. */
    fun weightedClose(high: DoubleArray, low: DoubleArray, close: DoubleArray): DoubleArray =
        DoubleArray(close.size) { (high[it] + low[it] + 2 * close[it]) / 4 }

    /**
     * Linear regression channel: the end of the least-squares line over [length] bars, and the
     * population standard deviation of the residuals times [multiplier] either side.
     */
    fun linearRegressionChannel(source: DoubleArray, length: Int = 100, multiplier: Double = 2.0): BandSeries {
        val n = source.size
        val len = max(2, length)
        val basis = nan(n)
        val upper = nan(n)
        val lower = nan(n)
        val sx = (len - 1) * len / 2.0
        val sxx = (len - 1) * len * (2.0 * len - 1) / 6.0
        val denominator = (len * sxx - sx * sx).takeIf { it != 0.0 } ?: 1e-9
        for (k in len - 1 until n) {
            var sy = 0.0
            var sxy = 0.0
            for (j in 0 until len) {
                val y = source[k - len + 1 + j]
                sy += y
                sxy += j * y
            }
            val slope = (len * sxy - sx * sy) / denominator
            val intercept = (sy - slope * sx) / len
            val end = intercept + slope * (len - 1)
            var squared = 0.0
            for (j in 0 until len) {
                val e = source[k - len + 1 + j] - (intercept + slope * j)
                squared += e * e
            }
            val sd = sqrt(squared / len)
            basis[k] = end
            upper[k] = end + multiplier * sd
            lower[k] = end - multiplier * sd
        }
        return BandSeries(upper, basis, lower)
    }

    /**
     * Chandelier exit, on closes: the highest close less [multiplier] ATRs while long, the lowest
     * plus [multiplier] ATRs while short, each ratcheting only in its own direction and the side
     * flipping when the close crosses the stop. Only the active side has a value on any bar.
     */
    fun chandelierExit(
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        period: Int = 22,
        multiplier: Double = 3.0,
    ): SidedStopSeries {
        val n = close.size
        val atr = wilder(trueRange(high, low, close), period)
        val highest = highestTolerant(close, period)
        val lowest = lowestTolerant(close, period)
        val green = nan(n)
        val red = nan(n)
        var direction = 1
        var previousLong = Double.NaN
        var previousShort = Double.NaN
        for (k in 0 until n) {
            if (atr[k].isNaN() || highest[k].isNaN() || lowest[k].isNaN()) continue
            var longStop = highest[k] - multiplier * atr[k]
            var shortStop = lowest[k] + multiplier * atr[k]
            if (!previousLong.isNaN() && k > 0 && close[k - 1] > previousLong) longStop = max(longStop, previousLong)
            if (!previousShort.isNaN() && k > 0 && close[k - 1] < previousShort) shortStop = min(shortStop, previousShort)
            if (direction == -1 && !previousShort.isNaN() && close[k] > previousShort) {
                direction = 1
            } else if (direction == 1 && !previousLong.isNaN() && close[k] < previousLong) {
                direction = -1
            }
            if (direction == 1) green[k] = longStop else red[k] = shortStop
            previousLong = longStop
            previousShort = shortStop
        }
        return SidedStopSeries(long = green, short = red)
    }

    // ── Oscillators ───────────────────────────────────────────────────────────────────────

    /**
     * Schaff trend cycle: a MACD of [fast]/[slow] EMAs put through a [cycle]-bar stochastic twice,
     * each pass smoothed by a three-bar EMA. Reads 0 to 100; 25 and 75 are its thresholds.
     */
    fun schaffTrendCycle(close: DoubleArray, fast: Int = 23, slow: Int = 50, cycle: Int = 10): DoubleArray {
        val emaFast = emaCarrying(close, fast)
        val emaSlow = emaCarrying(close, slow)
        val macd = DoubleArray(close.size) { i ->
            if (emaFast[i].isNaN() || emaSlow[i].isNaN()) Double.NaN else emaFast[i] - emaSlow[i]
        }
        val first = emaCarrying(stochasticOf(macd, cycle), 3)
        return emaCarrying(stochasticOf(first, cycle), 3)
    }

    /** Elder-ray bull and bear power against a [period]-bar EMA of the close. */
    fun elderRay(high: DoubleArray, low: DoubleArray, close: DoubleArray, period: Int = 13): ElderRaySeries {
        val average = emaCarrying(close, period)
        return ElderRaySeries(
            bull = DoubleArray(close.size) { if (average[it].isNaN()) Double.NaN else high[it] - average[it] },
            bear = DoubleArray(close.size) { if (average[it].isNaN()) Double.NaN else low[it] - average[it] },
        )
    }

    /** Aroon up less Aroon down: −100 to +100, and the zero crossing is the turn. */
    fun aroonOscillator(high: DoubleArray, low: DoubleArray, period: Int = 14): DoubleArray {
        val n = high.size
        val out = nan(n)
        if (period < 1) return out
        for (i in period until n) {
            var hi = Double.NEGATIVE_INFINITY
            var lo = Double.POSITIVE_INFINITY
            var highBar = 0
            var lowBar = 0
            for (j in 0..period) {
                if (high[i - j] > hi) {
                    hi = high[i - j]
                    highBar = j
                }
                if (low[i - j] < lo) {
                    lo = low[i - j]
                    lowBar = j
                }
            }
            val up = 100.0 * (period - highBar) / period
            val down = 100.0 * (period - lowBar) / period
            out[i] = up - down
        }
        return out
    }

    /** Average daily range: the simple average of high less low over [period] bars. */
    fun averageDailyRange(high: DoubleArray, low: DoubleArray, period: Int = 14): DoubleArray =
        smaZeroing(DoubleArray(high.size) { high[it] - low[it] }, period)

    /**
     * DecisionPoint's Price Momentum Oscillator: the one-bar rate of change times ten, smoothed
     * twice with a 2/length EMA, and a [signal]-bar EMA of that.
     */
    fun priceMomentumOscillator(close: DoubleArray, first: Int = 35, second: Int = 20, signal: Int = 10): MomentumSignalSeries {
        val n = close.size
        val roc = nan(n)
        for (k in 1 until n) if (close[k - 1] != 0.0) roc[k] = (close[k] / close[k - 1] - 1) * 100 * 10
        val line = emaAlpha(emaAlpha(roc, first), second)
        return MomentumSignalSeries(line = line, signal = emaCarryingWarm(line, signal))
    }

    /**
     * Positive (or negative) volume index: a running index from 1000 that moves with the close only
     * on bars whose volume rose (or fell). Empty on a feed that reports no volume.
     */
    fun volumeIndex(close: DoubleArray, volume: DoubleArray, positive: Boolean): DoubleArray {
        val n = close.size
        val out = nan(n)
        if (volume.none { it > 0 }) return out
        var index = 1000.0
        if (n > 0) out[0] = 1000.0
        for (k in 1 until n) {
            val up = volume[k] > volume[k - 1]
            val down = volume[k] < volume[k - 1]
            if (((positive && up) || (!positive && down)) && close[k - 1] != 0.0) {
                index += (close[k] - close[k - 1]) / close[k - 1] * index
            }
            out[k] = index
        }
        return out
    }

    /**
     * Dorsey's relative volatility index: an RSI whose gains and losses are the [deviation]-bar
     * standard deviation, credited to the side the bar closed on and averaged over [length] bars.
     */
    fun relativeVolatilityIndex(source: DoubleArray, length: Int = 14, deviation: Int = 10): DoubleArray {
        val n = source.size
        val sd = populationStdDev(source, deviation)
        val up = nan(n)
        val down = nan(n)
        for (k in 1 until n) {
            if (sd[k].isNaN()) continue
            when {
                source[k] > source[k - 1] -> {
                    up[k] = sd[k]
                    down[k] = 0.0
                }
                source[k] < source[k - 1] -> {
                    up[k] = 0.0
                    down[k] = sd[k]
                }
                else -> {
                    up[k] = 0.0
                    down[k] = 0.0
                }
            }
        }
        val upAverage = emaCarryingWarm(up, length)
        val downAverage = emaCarryingWarm(down, length)
        val out = nan(n)
        for (k in 0 until n) {
            if (upAverage[k].isNaN() || downAverage[k].isNaN()) continue
            val sum = upAverage[k] + downAverage[k]
            out[k] = if (sum != 0.0) 100 * upAverage[k] / sum else 0.0
        }
        return out
    }

    /**
     * Ulcer index: the root mean square, over [period] bars, of the percentage drawdown from the
     * highest close of the last [period] bars. Never negative; higher is deeper pain.
     */
    fun ulcerIndex(close: DoubleArray, period: Int = 14): DoubleArray {
        val n = close.size
        val highest = highestTolerant(close, period)
        val squared = nan(n)
        for (k in 0 until n) {
            if (highest[k].isNaN() || highest[k] == 0.0) continue
            val pct = 100 * (close[k] - highest[k]) / highest[k]
            squared[k] = pct * pct
        }
        val out = nan(n)
        if (period < 1) return out
        for (k in period - 1 until n) {
            var sum = 0.0
            var complete = true
            for (j in 0 until period) {
                val v = squared[k - j]
                if (v.isNaN()) {
                    complete = false
                    break
                }
                sum += v
            }
            if (complete) out[k] = sqrt(sum / period)
        }
        return out
    }

    /** The percentage gap between a [short]- and a [long]-bar EMA of volume. Empty with no volume. */
    fun volumeOscillator(volume: DoubleArray, short: Int = 5, long: Int = 10): DoubleArray {
        val n = volume.size
        if (volume.none { it > 0 }) return nan(n)
        val clean = DoubleArray(n) { volume[it].takeIf(Double::isFinite) ?: 0.0 }
        val fast = emaCarryingWarm(clean, max(1, short))
        val slow = emaCarryingWarm(clean, max(2, long))
        return DoubleArray(n) { k ->
            if (fast[k].isNaN() || slow[k].isNaN() || slow[k] == 0.0) Double.NaN else (fast[k] - slow[k]) / slow[k] * 100
        }
    }

    // ── Structure ─────────────────────────────────────────────────────────────────────────

    /**
     * Pivot highs and lows: a bar whose high is strictly above the [left] bars before it and the
     * [right] bars after it (or whose low is strictly below them). A bar can be both.
     */
    fun pivotHighLow(high: DoubleArray, low: DoubleArray, left: Int = 5, right: Int = 5): List<PivotPoint> = buildList {
        for (k in left until high.size - right) {
            var isHigh = true
            var isLow = true
            for (j in 1..left) {
                if (high[k - j] >= high[k]) isHigh = false
                if (low[k - j] <= low[k]) isLow = false
            }
            for (j in 1..right) {
                if (high[k + j] >= high[k]) isHigh = false
                if (low[k + j] <= low[k]) isLow = false
            }
            if (isHigh) add(PivotPoint(k, high = true))
            if (isLow) add(PivotPoint(k, high = false))
        }
    }

    // ── The original's helpers, each with the original's edge behaviour ──────────────────

    private val GUPPY_SHORT = listOf(3, 5, 8, 10, 12, 15)
    private val GUPPY_LONG = listOf(30, 35, 40, 45, 50, 60)

    private fun nan(size: Int): DoubleArray = DoubleArray(size) { Double.NaN }

    /** `indicators.js` `sma`: a plain running sum, no gap handling (its inputs have none). */
    private fun sma(source: DoubleArray, period: Int): DoubleArray {
        val out = nan(source.size)
        var sum = 0.0
        for (i in source.indices) {
            sum += source[i]
            if (i >= period) sum -= source[i - period]
            if (i >= period - 1) out[i] = sum / period
        }
        return out
    }

    /** `indicators_ext_b.js` `_sma`: a gap counts as zero. */
    private fun smaZeroing(source: DoubleArray, period: Int): DoubleArray {
        val out = nan(source.size)
        var sum = 0.0
        for (i in source.indices) {
            sum += source[i].takeUnless(Double::isNaN) ?: 0.0
            if (i >= period) sum -= source[i - period].takeUnless(Double::isNaN) ?: 0.0
            if (i >= period - 1) out[i] = sum / period
        }
        return out
    }

    /** `indicators.js` `ema`: seeded on the first real value, gaps skipped, output from bar p−1. */
    private fun emaSkipping(source: DoubleArray, period: Int): DoubleArray {
        val out = nan(source.size)
        val k = 2.0 / (period + 1)
        var previous = Double.NaN
        for (i in source.indices) {
            val v = source[i]
            if (v.isNaN()) continue
            previous = if (previous.isNaN()) v else v * k + previous * (1 - k)
            if (i >= period - 1) out[i] = previous
        }
        return out
    }

    /** `indicators.js` `_ema2`: seeded on the first real value, a gap carries the last one, no warm-up. */
    private fun emaCarrying(source: DoubleArray, period: Int): DoubleArray {
        val out = nan(source.size)
        val k = 2.0 / (period + 1)
        var previous = Double.NaN
        for (i in source.indices) {
            val v = source[i]
            if (v.isNaN()) {
                out[i] = previous
                continue
            }
            previous = if (previous.isNaN()) v else v * k + previous * (1 - k)
            out[i] = previous
        }
        return out
    }

    /** `indicators_ext_b.js` `_ema`: as [emaCarrying], but a real value is only written from bar p−1. */
    private fun emaCarryingWarm(source: DoubleArray, period: Int): DoubleArray {
        val out = nan(source.size)
        val k = 2.0 / (period + 1)
        var previous = Double.NaN
        for (i in source.indices) {
            val v = source[i]
            if (v.isNaN()) {
                out[i] = previous
                continue
            }
            previous = if (previous.isNaN()) v else v * k + previous * (1 - k)
            if (i >= period - 1) out[i] = previous
        }
        return out
    }

    /** `indicators_ext_b.js` `_emaAlpha`: alpha 2/period, seeded on the first real value, gaps carry. */
    private fun emaAlpha(source: DoubleArray, period: Int): DoubleArray {
        val out = nan(source.size)
        val alpha = 2.0 / period
        var previous = Double.NaN
        for (k in source.indices) {
            val v = source[k]
            if (v.isNaN()) {
                out[k] = previous
                continue
            }
            previous = if (previous.isNaN()) v else previous + alpha * (v - previous)
            out[k] = previous
        }
        return out
    }

    /** `indicators.js` `rsi`: Wilder's, seeded with the mean of the first [period] changes. */
    private fun rsi(close: DoubleArray, period: Int): DoubleArray {
        val out = nan(close.size)
        var gain = 0.0
        var loss = 0.0
        for (i in 1 until close.size) {
            val change = close[i] - close[i - 1]
            val g = max(change, 0.0)
            val l = max(-change, 0.0)
            if (i <= period) {
                gain += g
                loss += l
                if (i == period) {
                    gain /= period
                    loss /= period
                    out[i] = 100 - 100 / (1 + gain / (if (loss != 0.0) loss else 1e-9))
                }
            } else {
                gain = (gain * (period - 1) + g) / period
                loss = (loss * (period - 1) + l) / period
                out[i] = 100 - 100 / (1 + gain / (if (loss != 0.0) loss else 1e-9))
            }
        }
        return out
    }

    /** `indicators_ext_b.js` `_rma`: Wilder's average seeded with the simple mean; a gap counts as zero. */
    private fun wilder(source: DoubleArray, period: Int): DoubleArray {
        val out = nan(source.size)
        var previous = 0.0
        var sum = 0.0
        for (i in source.indices) {
            val v = source[i].takeUnless(Double::isNaN) ?: 0.0
            if (i < period) {
                sum += v
                if (i == period - 1) {
                    previous = sum / period
                    out[i] = previous
                }
                continue
            }
            previous = (previous * (period - 1) + v) / period
            out[i] = previous
        }
        return out
    }

    private fun trueRange(high: DoubleArray, low: DoubleArray, close: DoubleArray): DoubleArray =
        DoubleArray(close.size) { i ->
            if (i == 0) {
                high[i] - low[i]
            } else {
                max(high[i] - low[i], max(abs(high[i] - close[i - 1]), abs(low[i] - close[i - 1])))
            }
        }

    /** `indicators_ext_b.js` `_highest`: gaps ignored, a window of nothing but gaps is a gap. */
    private fun highestTolerant(source: DoubleArray, period: Int): DoubleArray {
        val out = nan(source.size)
        if (period < 1) return out
        for (i in period - 1 until source.size) {
            var best = Double.NEGATIVE_INFINITY
            var any = false
            for (j in 0 until period) {
                val v = source[i - j]
                if (v.isNaN()) continue
                any = true
                if (v > best) best = v
            }
            if (any) out[i] = best
        }
        return out
    }

    private fun lowestTolerant(source: DoubleArray, period: Int): DoubleArray {
        val out = nan(source.size)
        if (period < 1) return out
        for (i in period - 1 until source.size) {
            var best = Double.POSITIVE_INFINITY
            var any = false
            for (j in 0 until period) {
                val v = source[i - j]
                if (v.isNaN()) continue
                any = true
                if (v < best) best = v
            }
            if (any) out[i] = best
        }
        return out
    }

    /** `indicators_ext_b.js` `_stdev`: population standard deviation over a full window. */
    private fun populationStdDev(source: DoubleArray, period: Int): DoubleArray {
        val out = nan(source.size)
        if (period < 1) return out
        for (i in period - 1 until source.size) {
            var mean = 0.0
            for (j in 0 until period) mean += source[i - j]
            mean /= period
            var squared = 0.0
            for (j in 0 until period) {
                val d = source[i - j] - mean
                squared += d * d
            }
            out[i] = sqrt(squared / period)
        }
        return out
    }

    /** The STC's inner stochastic: a window with a gap is a gap, a flat window reads zero. */
    private fun stochasticOf(source: DoubleArray, period: Int): DoubleArray {
        val out = nan(source.size)
        if (period < 1) return out
        for (i in period - 1 until source.size) {
            var hh = Double.NEGATIVE_INFINITY
            var ll = Double.POSITIVE_INFINITY
            var ok = true
            for (j in 0 until period) {
                val v = source[i - j]
                if (v.isNaN()) {
                    ok = false
                    break
                }
                hh = max(hh, v)
                ll = min(ll, v)
            }
            if (ok) out[i] = if (hh > ll) (source[i] - ll) / (hh - ll) * 100 else 0.0
        }
        return out
    }

    /** `_resampleC`'s closes: every [factor] bars from the first, the last close of each bucket. */
    private fun resampleClose(close: DoubleArray, factor: Int): DoubleArray {
        val f = max(1, factor)
        val buckets = (close.size + f - 1) / f
        return DoubleArray(buckets) { b -> close[min((b + 1) * f, close.size) - 1] }
    }

    /** `_expandA`: bucket `i` is known from bar `(i + 1)·f` and holds for [factor] bars. */
    private fun expand(values: DoubleArray, factor: Int, size: Int): DoubleArray {
        val f = max(1, factor)
        val out = nan(size)
        for (i in values.indices) {
            val from = (i + 1) * f
            val to = min(from + f, size)
            for (index in from until to) out[index] = values[i]
        }
        return out
    }
}
