package com.coinepro.core.chart

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Know Sure Thing: the four smoothed rates of change added together, and the average of that.
 *
 * The two are always read as a pair — the line alone says nothing a plain momentum reading does
 * not, and the crossing is the whole signal — so they are returned together rather than as two
 * calls a caller could accidentally run at different periods.
 */
data class KnowSureThingResult(val kst: DoubleArray, val signal: DoubleArray)

/**
 * The two Chande–Kroll stops: the trailing exit for a long position and the one for a short.
 *
 * Both exist on every bar, and that is deliberate rather than wasteful. Which one a reader is
 * looking at depends on a position this module knows nothing about, so it draws both and lets the
 * chart decide.
 */
data class ChandeKrollStop(val longStop: DoubleArray, val shortStop: DoubleArray)

/** The Relative Vigor Index and its four-bar signal, which is the line the crossing is read against. */
data class RelativeVigorResult(val rvi: DoubleArray, val signal: DoubleArray)

/**
 * Woodie's CCI: the slow line the trader reads for trend and the fast "turbo" line read for entries.
 *
 * The same calculation at two lookbacks, which is the entire method — a turbo computed with a
 * different formula from the main line would make the two disagree about the same bar.
 */
data class WoodiesCciResult(val cci: DoubleArray, val turbo: DoubleArray)

/**
 * The volatility stop, and the side it is currently protecting.
 *
 * [isLong] is not decoration: the stop price is below price in an uptrend and above it in a
 * downtrend, so a chart that draws the value without the side draws a stop on the wrong side of the
 * candle every time the trend flips. It is `false` wherever [stop] has not warmed up.
 */
data class VolatilityStopResult(val stop: DoubleArray, val isLong: BooleanArray)

/**
 * A volume profile over a window of bars: how much volume traded at each price, not at each time.
 *
 * Row `i` covers the half-open price band `[rowLow[i], rowHigh[i])`, lowest row first, and the
 * topmost row includes its own upper edge so the window's high is not silently dropped. [volume] is
 * the total in the row and [buy] plus [sell] is that same total split by the sign of the bar,
 * so a caller can draw either one bar per row or the split pair without recomputing anything.
 *
 * [pocIndex] is the point of control, the row that traded the most, and it is `-1` — with
 * [valueAreaLow] and [valueAreaHigh] also `-1` — when the window carries no volume at all. That is
 * the case a caller must handle: a feed that reports zero volume is not a market that traded evenly
 * at every price, and a profile drawn as flat bars would claim exactly that.
 *
 * The arrays are the profile's identity, so `equals` on this class compares references rather than
 * contents. It is a carrier for a drawing pass, never a map key.
 */
data class VolumeProfile(
    val rowLow: DoubleArray,
    val rowHigh: DoubleArray,
    val volume: DoubleArray,
    val buy: DoubleArray,
    val sell: DoubleArray,
    val pocIndex: Int,
    val valueAreaLow: Int,
    val valueAreaHigh: Int,
)

/**
 * The third indicator pack, plus the volume profile engine three drawing tools and one indicator
 * share.
 *
 * ### Why this pack speaks `DoubleArray` and not [Line]
 *
 * [Indicators] and [IndicatorsExt] hand back a [Line], which is right for something the chart draws
 * once per redraw and reads through a presence mask. The consumers here are different: the fixed
 * range, session and visible-range volume tools all call [volumeProfile] and then bucket, sort and
 * rescale its output on every pan, and the oscillators in this pack are read by the alert engine as
 * much as by the renderer. Both want the plain array. So warm-up is `Double.NaN` rather than a
 * parallel mask, which is the same claim — "no value here" — carried by the number itself.
 *
 * `NaN` is used, and never zero, for the reason spelled out on [Line]: zero is a reading, and a
 * chande momentum oscillator sitting at zero for its first nine bars is a lie about a market that
 * was merely not measured yet. Every function here also *accepts* `NaN` in its input and produces
 * `NaN` out of any window that touches one, so an indicator fed another's warm-up cannot invent a
 * value from a hole.
 *
 * ### The guard contract
 *
 * A nonsensical period, arrays of different lengths, or a series shorter than the warm-up needs all
 * return an all-`NaN` array of the input's length rather than throwing. The caller here is a
 * renderer running inside a gesture: a chart that draws nothing for a symbol with nine bars of
 * history is a small disappointment, and one that throws out of a scroll is a crash report.
 */
object IndicatorsExtC {

    // ══════════════════════════════════════════════════════════ oscillators

    /**
     * [66] Detrended price oscillator: price as it stood half a cycle ago, minus the average.
     *
     * The displacement is the point and is the part most implementations get wrong. Comparing
     * *today's* close against a centred average would leave the trend in, which is the one thing the
     * indicator is named for removing; shifting the price back by `period / 2 + 1` bars lines it up
     * with the middle of the window the average covers, so what is left is the cycle alone.
     *
     * It therefore does not extend to the last bar in the way a moving average does, and it is not
     * a signal line: a DPO crossing zero says where price sat relative to its own recent middle, not
     * where it is going.
     */
    fun detrendedPriceOscillator(close: DoubleArray, period: Int = 20): DoubleArray {
        val out = nan(close.size)
        if (period <= 0 || close.size < period) return out
        val shift = period / 2 + 1
        val average = sma(close, period)
        for (index in close.indices) {
            val mean = average[index]
            val past = index - shift
            if (past < 0 || !mean.isFinite()) continue
            val price = close[past]
            if (price.isFinite()) out[index] = price - mean
        }
        return out
    }

    /**
     * [67] Pring's Know Sure Thing: four rates of change, smoothed, weighted one to four, and summed.
     *
     * The weights are the design. A ten-bar rate of change turns first and a thirty-bar one turns
     * last, so weighting the slow one four times heavier means the sum only turns when the long
     * cycle agrees — which is what makes KST slower to fire and much harder to whipsaw than the
     * momentum readings it is built from.
     *
     * The periods are fixed rather than parameters because these four in this combination *are* the
     * indicator; a KST at other lengths is a different oscillator wearing its name, and a reader
     * comparing against the web terminal would see a line that never matches.
     */
    fun knowSureThing(close: DoubleArray): KnowSureThingResult {
        val kst = nan(close.size)
        if (close.isEmpty()) return KnowSureThingResult(kst, nan(0))
        val first = sma(rateOfChange(close, 10), 10)
        val second = sma(rateOfChange(close, 15), 10)
        val third = sma(rateOfChange(close, 20), 10)
        val fourth = sma(rateOfChange(close, 30), 15)
        for (index in close.indices) {
            val a = first[index]
            val b = second[index]
            val c = third[index]
            val d = fourth[index]
            if (a.isFinite() && b.isFinite() && c.isFinite() && d.isFinite()) {
                kst[index] = a + 2 * b + 3 * c + 4 * d
            }
        }
        return KnowSureThingResult(kst, sma(kst, 9))
    }

    /**
     * [68] Mass index: the sum of the ratio between a smoothed range and its own smoothing.
     *
     * It measures the range widening, not the direction, which is why it has no sign and why the
     * only signal traders take from it — the "reversal bulge", a rise above 27 followed by a drop
     * back under 26.5 — says a move is coming without saying which way.
     *
     * [ema] is the smoothing length used twice, once on the high-low range and once on that result.
     * Both smoothings must be the same length; the ratio of an EMA to a *differently* smoothed EMA
     * of itself drifts with the trend and stops measuring range at all.
     */
    fun massIndex(high: DoubleArray, low: DoubleArray, period: Int = 25, ema: Int = 9): DoubleArray {
        val out = nan(high.size)
        val smoothing = ema
        if (period <= 0 || smoothing <= 0 || low.size != high.size) return out
        if (high.size < period + 2 * smoothing - 2) return out
        val range = DoubleArray(high.size) { index ->
            val top = high[index]
            val bottom = low[index]
            if (top.isFinite() && bottom.isFinite()) top - bottom else Double.NaN
        }
        val single = ema(range, smoothing)
        val doubled = ema(single, smoothing)
        val ratio = nan(high.size)
        for (index in high.indices) {
            val fast = single[index]
            val slow = doubled[index]
            if (fast.isFinite() && slow.isFinite() && slow != 0.0) ratio[index] = fast / slow
        }
        return rollingSum(ratio, period)
    }

    /**
     * [69] Chande momentum oscillator: up moves minus down moves over their total, as a percentage.
     *
     * Unlike RSI it uses the raw sums rather than Wilder's smoothing of them, so it reaches ±100 and
     * moves far more sharply. That is not a defect to be smoothed away — the whole reason to reach
     * for a CMO over an RSI is that it does not hide a one-sided run behind an average.
     *
     * A window in which price never moved has no up and no down, so it returns zero: genuinely
     * neither side had the bar, which is a different statement from the `NaN` of a warm-up.
     */
    fun chandeMomentumOscillator(close: DoubleArray, period: Int = 9): DoubleArray {
        val out = nan(close.size)
        if (period <= 0 || close.size <= period) return out
        for (index in period until close.size) {
            var up = 0.0
            var down = 0.0
            var complete = true
            for (step in 0 until period) {
                val now = close[index - step]
                val before = close[index - step - 1]
                if (!now.isFinite() || !before.isFinite()) {
                    complete = false
                    break
                }
                val change = now - before
                if (change > 0) up += change else down -= change
            }
            if (!complete) continue
            val total = up + down
            out[index] = if (total > 0) 100 * (up - down) / total else 0.0
        }
        return out
    }

    /**
     * [70] Chande–Kroll stop: an ATR-wide stop, then the extreme of that stop over a second window.
     *
     * The second pass is what makes it usable. A raw "highest high minus [x] ATRs" jumps around with
     * every new extreme; taking the highest of those stops over [q] bars ratchets it, so the short
     * stop only ever moves down towards price and the long stop only ever moves up towards it while
     * the swing lasts.
     *
     * [p] is the lookback for both the extremes and the ATR, [x] the ATR multiple, and [q] the
     * length of the ratchet.
     */
    fun chandeKrollStop(
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        p: Int = 10,
        x: Double = 1.0,
        q: Int = 9,
    ): ChandeKrollStop {
        val size = high.size
        if (p <= 0 || q <= 0 || low.size != size || close.size != size || size < p + q - 1) {
            return ChandeKrollStop(nan(size), nan(size))
        }
        val range = averageTrueRange(high, low, close, p)
        val firstHigh = nan(size)
        val firstLow = nan(size)
        for (index in p - 1 until size) {
            val atr = range[index]
            if (!atr.isFinite()) continue
            var highest = -Double.MAX_VALUE
            var lowest = Double.MAX_VALUE
            var complete = true
            for (step in 0 until p) {
                val top = high[index - step]
                val bottom = low[index - step]
                if (!top.isFinite() || !bottom.isFinite()) {
                    complete = false
                    break
                }
                if (top > highest) highest = top
                if (bottom < lowest) lowest = bottom
            }
            if (!complete) continue
            firstHigh[index] = highest - x * atr
            firstLow[index] = lowest + x * atr
        }
        return ChandeKrollStop(longStop = lowest(firstLow, q), shortStop = highest(firstHigh, q))
    }

    /**
     * [71] Chop zone: the slope of a 34-bar EMA, normalised by the range, as a palette index.
     *
     * It is drawn as a coloured ribbon rather than a line, and the colour is the whole reading: a
     * flat EMA means chop, a steep one means trend, and the eight buckets are the granularity the
     * indicator was designed around. What makes the angle comparable across symbols is the
     * normalisation — the slope is divided by the bar's own midpoint and scaled by
     * `25 / (highest − lowest) × lowest` over [period] bars, so a five-degree rise means the same
     * thing on a five-thousand-dollar symbol as on a five-cent one. Without it the "angle" would be
     * a function of the price axis, which is a property of the screen and not of the market.
     *
     * The returned index runs from most bullish to most bearish and is `-1` where the indicator has
     * not warmed up, since an `IntArray` has no `NaN` to say so with:
     *
     * * `0` — the EMA is rising at 5° or more. Strong uptrend.
     * * `1` — rising between 2.14° and 5°. Uptrend.
     * * `2` — rising between 0.71° and 2.14°. Weak uptrend.
     * * `3` — rising by less than 0.71°. Flat, tilted up: chop.
     * * `4` — falling by less than 0.71°. Flat, tilted down: chop.
     * * `5` — falling between 0.71° and 2.14°. Weak downtrend.
     * * `6` — falling between 2.14° and 5°. Downtrend.
     * * `7` — falling at 5° or more. Strong downtrend.
     *
     * The angle is kept as a real number rather than rounded to whole degrees on the way in. The
     * original rounds first and only then compares against 0.71 and 2.14, so those thresholds are
     * not the boundaries they appear to be: once the angle is an integer the only cut points that
     * can ever be reached are the half-degrees, and editing 0.71 to 0.9 would change nothing at all.
     * Comparing the whole angle makes each boundary mean the number written beside it.
     */
    fun chopZone(high: DoubleArray, low: DoubleArray, close: DoubleArray, period: Int = 30): IntArray {
        val size = close.size
        val out = IntArray(size) { -1 }
        if (period <= 0 || high.size != size || low.size != size) return out
        if (size < max(period, CHOP_ZONE_EMA + 1)) return out
        val average = ema(close, CHOP_ZONE_EMA)
        for (index in max(period - 1, 1) until size) {
            val now = average[index]
            val before = average[index - 1]
            if (!now.isFinite() || !before.isFinite()) continue
            var highest = -Double.MAX_VALUE
            var lowest = Double.MAX_VALUE
            var complete = true
            for (step in 0 until period) {
                val top = high[index - step]
                val bottom = low[index - step]
                if (!top.isFinite() || !bottom.isFinite()) {
                    complete = false
                    break
                }
                if (top > highest) highest = top
                if (bottom < lowest) lowest = bottom
            }
            if (!complete) continue
            val midpoint = (high[index] + low[index]) / 2
            if (midpoint == 0.0) continue
            val span = if (highest > lowest) 25.0 / (highest - lowest) * lowest else 0.0
            val slope = (before - now) / midpoint * span
            val degrees = 180 * acos(1 / sqrt(1 + slope * slope)) / PI
            // The slope is measured as previous minus current, so a *rising* EMA gives a negative
            // number. The sign is put back here rather than left inverted, because an "angle" whose
            // sign disagrees with the direction of the line is the kind of thing that survives
            // review and then quietly colours every trend backwards.
            val angle = if (slope > 0) -degrees else degrees
            out[index] = when {
                angle >= 5.0 -> 0
                angle >= 2.14 -> 1
                angle >= 0.71 -> 2
                angle >= 0.0 -> 3
                angle > -0.71 -> 4
                angle > -2.14 -> 5
                angle > -5.0 -> 6
                else -> 7
            }
        }
        return out
    }

    /**
     * [72] Coppock curve: a weighted average of two rates of change added together.
     *
     * Built for monthly index data and for one purpose — a rise from below zero marks a generational
     * bottom — which is worth remembering before reading it on an hourly crypto chart, where it
     * fires constantly and means very little. It is offered because traders ask for it by name.
     *
     * The weighted average, not a simple one: the front-loaded weights are what let a fourteen-bar
     * smoothing still turn within a couple of bars of the underlying momentum.
     */
    fun coppockCurve(close: DoubleArray, roc1: Int = 14, roc2: Int = 11, wma: Int = 10): DoubleArray {
        val out = nan(close.size)
        val smoothing = wma
        if (roc1 <= 0 || roc2 <= 0 || smoothing <= 0) return out
        if (close.size < max(roc1, roc2) + smoothing) return out
        val slow = rateOfChange(close, roc1)
        val fast = rateOfChange(close, roc2)
        val total = nan(close.size)
        for (index in close.indices) {
            val a = slow[index]
            val b = fast[index]
            if (a.isFinite() && b.isFinite()) total[index] = a + b
        }
        return wma(total, smoothing)
    }

    /**
     * [73] Net volume: volume signed by the bar's direction, accumulated from the first bar.
     *
     * The same idea as on-balance volume and deliberately kept separate from it, because the two are
     * read differently: OBV is a line traders draw trendlines on, net volume is a histogram column
     * per bar read against its own recent level. Sharing one function would mean one of the two
     * screens eventually getting the other's warm-up.
     *
     * An unchanged close contributes nothing. It is not a rounding decision — a bar that closed
     * exactly where the last one did gave neither side the day, and assigning its volume to either
     * would put a step in the line that the market did not put there.
     */
    fun netVolume(close: DoubleArray, volume: DoubleArray): DoubleArray {
        val out = nan(close.size)
        if (close.isEmpty() || volume.size != close.size) return out
        var total = 0.0
        out[0] = 0.0
        for (index in 1 until close.size) {
            val now = close[index]
            val before = close[index - 1]
            val traded = volume[index]
            if (now.isFinite() && before.isFinite() && traded.isFinite()) {
                total += when {
                    now > before -> traded
                    now < before -> -traded
                    else -> 0.0
                }
            }
            out[index] = total
        }
        return out
    }

    /**
     * [74] Relative Vigor Index: where the bar closed within its range, smoothed and averaged.
     *
     * The premise is that in an uptrend price closes above where it opened, so the ratio of the body
     * to the range is a measure of conviction rather than of level. Both halves get the same
     * symmetric 1-2-2-1 four-bar smoothing before the division, which is what removes the intraday
     * noise without shifting the two relative to each other — smoothing only the numerator would
     * make the ratio drift on any bar where the range changed sharply.
     *
     * The signal is that same 1-2-2-1 weighting applied to the line itself, so it lags by about two
     * bars and the crossing is the trigger.
     */
    fun relativeVigorIndex(
        open: DoubleArray,
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        period: Int = 10,
    ): RelativeVigorResult {
        val size = close.size
        if (period <= 0 || open.size != size || high.size != size || low.size != size) {
            return RelativeVigorResult(nan(size), nan(size))
        }
        if (size < period + 3) return RelativeVigorResult(nan(size), nan(size))
        val body = nan(size)
        val span = nan(size)
        for (index in 3 until size) {
            var numerator = 0.0
            var denominator = 0.0
            var complete = true
            for (step in 0 until 4) {
                val at = index - step
                val weight = if (step == 1 || step == 2) 2.0 else 1.0
                val o = open[at]
                val h = high[at]
                val l = low[at]
                val c = close[at]
                if (!o.isFinite() || !h.isFinite() || !l.isFinite() || !c.isFinite()) {
                    complete = false
                    break
                }
                numerator += weight * (c - o)
                denominator += weight * (h - l)
            }
            if (!complete) continue
            body[index] = numerator / 6
            span[index] = denominator / 6
        }
        val smoothedBody = sma(body, period)
        val smoothedSpan = sma(span, period)
        val rvi = nan(size)
        for (index in 0 until size) {
            val top = smoothedBody[index]
            val bottom = smoothedSpan[index]
            if (top.isFinite() && bottom.isFinite() && bottom != 0.0) rvi[index] = top / bottom
        }
        val signal = nan(size)
        for (index in 3 until size) {
            val a = rvi[index]
            val b = rvi[index - 1]
            val c = rvi[index - 2]
            val d = rvi[index - 3]
            if (a.isFinite() && b.isFinite() && c.isFinite() && d.isFinite()) {
                signal[index] = (a + 2 * b + 2 * c + d) / 6
            }
        }
        return RelativeVigorResult(rvi, signal)
    }

    /**
     * [75] Woodie's CCI: the standard CCI at two lookbacks, read together.
     *
     * Woodie's method is a set of patterns — the zero-line reject, the trend line break — and every
     * one of them is stated in terms of the fast line behaving differently from the slow one. So the
     * pair is computed here in one call from one typical-price series, which is the only way to
     * guarantee both lines describe the same bar with the same arithmetic.
     */
    fun woodiesCci(
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        period: Int = 14,
        turboPeriod: Int = 6,
    ): WoodiesCciResult {
        val size = close.size
        if (high.size != size || low.size != size) return WoodiesCciResult(nan(size), nan(size))
        val typical = DoubleArray(size) { index ->
            val top = high[index]
            val bottom = low[index]
            val last = close[index]
            if (top.isFinite() && bottom.isFinite() && last.isFinite()) {
                (top + bottom + last) / 3
            } else {
                Double.NaN
            }
        }
        return WoodiesCciResult(
            cci = commodityChannel(typical, period),
            turbo = commodityChannel(typical, turboPeriod),
        )
    }

    /**
     * [76] Volatility stop: a trailing ATR stop that flips side when price closes through it.
     *
     * The stop ratchets. While the trend holds it takes the best extreme price has reached and sits
     * [multiplier] ATRs away from it, and it never gives that ground back — which is exactly what a
     * stop is for and exactly what a plain "close minus two ATR" fails to do, since that walks back
     * down with every quiet bar and stops out on noise.
     *
     * On the flip both the running extreme and the stop are reset to the bar that broke it, rather
     * than carried across. Carrying them would leave the new stop anchored to a high the market has
     * already rejected, and the first bar of every new trend would show a stop nowhere near price.
     */
    fun volatilityStop(
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        period: Int = 20,
        multiplier: Double = 2.0,
    ): VolatilityStopResult {
        val size = close.size
        val stop = nan(size)
        val isLong = BooleanArray(size)
        if (period <= 0 || high.size != size || low.size != size || size < period) {
            return VolatilityStopResult(stop, isLong)
        }
        val range = averageTrueRange(high, low, close, period)
        var highest = 0.0
        var lowest = 0.0
        var current = 0.0
        var uptrend = true
        var started = false
        for (index in 0 until size) {
            val atr = range[index]
            val price = close[index]
            if (!atr.isFinite() || !price.isFinite()) continue
            val width = multiplier * atr
            if (!started) {
                highest = price
                lowest = price
                uptrend = true
                current = price - width
                started = true
                stop[index] = current
                isLong[index] = true
                continue
            }
            if (price > highest) highest = price
            if (price < lowest) lowest = price
            current = if (uptrend) max(current, highest - width) else min(current, lowest + width)
            val nowLong = price - current >= 0.0
            if (nowLong != uptrend) {
                uptrend = nowLong
                highest = price
                lowest = price
                current = if (uptrend) price - width else price + width
            }
            stop[index] = current
            isLong[index] = uptrend
        }
        return VolatilityStopResult(stop, isLong)
    }

    /**
     * [77] Rolling Pearson correlation between two series, from −1 to 1.
     *
     * Read for the pairs it makes obvious: a coin that has stopped following bitcoin, or two
     * holdings that turn out to be the same bet. The window matters more than with most indicators —
     * a twenty-bar correlation is a claim about twenty bars, and reading it as "these assets are
     * correlated" is how a portfolio ends up concentrated.
     *
     * A window in which either series never moved has no correlation to report, not a correlation of
     * zero, so it returns `NaN`: division by a zero deviation is undefined and the honest output is
     * a gap in the line. The result is clamped to ±1 because the sum-of-products form can overshoot
     * by a few ulps on a perfectly correlated window, and a chart with a −1..1 scale would clip a
     * point drawn at 1.0000000000000002 to nothing.
     */
    fun correlationCoefficient(a: DoubleArray, b: DoubleArray, period: Int = 20): DoubleArray {
        val out = nan(a.size)
        if (period <= 1 || b.size != a.size || a.size < period) return out
        val length = period.toDouble()
        for (index in period - 1 until a.size) {
            var sumA = 0.0
            var sumB = 0.0
            var sumAA = 0.0
            var sumBB = 0.0
            var sumAB = 0.0
            var complete = true
            for (step in 0 until period) {
                val left = a[index - step]
                val right = b[index - step]
                if (!left.isFinite() || !right.isFinite()) {
                    complete = false
                    break
                }
                sumA += left
                sumB += right
                sumAA += left * left
                sumBB += right * right
                sumAB += left * right
            }
            if (!complete) continue
            val varianceA = length * sumAA - sumA * sumA
            val varianceB = length * sumBB - sumB * sumB
            if (varianceA <= 0.0 || varianceB <= 0.0) continue
            val value = (length * sumAB - sumA * sumB) / sqrt(varianceA * varianceB)
            out[index] = value.coerceIn(-1.0, 1.0)
        }
        return out
    }

    // ══════════════════════════════════════════════════════════ volume profile

    /**
     * [52/2/3] The volume profile of `[fromIndex, toIndex]`, in [rows] price bands.
     *
     * This is the engine under the fixed-range, session and visible-range volume tools and under the
     * volume-profile indicator, which is why it takes a window rather than reading the whole series:
     * every one of those four is the same calculation over a different pair of indices, and four
     * copies of it would be four chances to disagree about what the point of control is.
     *
     * ### Each bar is spread across the rows it touched
     *
     * A bar's volume is divided between rows in proportion to how much of the bar's high-low range
     * each row covers. The common shortcut — dropping the whole bar into the row containing its
     * close — is wrong in a way that looks right: on any timeframe where bars are tall relative to
     * the row height it produces a profile of narrow spikes at closing prices, and the point of
     * control it reports is an artefact of where bars happened to end rather than where trade
     * happened. Spreading it is an assumption too (volume was not really uniform inside the bar),
     * but it is the assumption every terminal makes and the only one available without tick data.
     *
     * A bar with no range at all — high equal to low, which is a real thing on an illiquid symbol —
     * has nothing to spread across, so its whole volume goes into the single row containing that
     * price. Dividing by its zero range instead is how this calculation usually first crashes.
     *
     * ### Buy and sell
     *
     * Split by the bar's own body: a bar that closed above its open counts as buying, anything else
     * as selling. There is no intrabar data behind this and it is not an order-flow measurement; a
     * doji falls on the sell side because the split must be total and an unchanged bar is not a
     * demonstration of demand. The caller draws it as the up/down split it is, never as delta.
     *
     * ### The value area
     *
     * Starting at the point of control, the area grows one row at a time, each step taking whichever
     * neighbour — the row above or the row below — holds more volume, until the rows taken account
     * for [valueAreaPercent] of the window's total. Taking the heavier neighbour rather than
     * expanding symmetrically is what makes the value area sit off-centre on a market that spent its
     * time on one side of the control price, which is the entire information in it. Where the two
     * neighbours tie the lower row is taken first, so the result does not depend on iteration order.
     */
    fun volumeProfile(
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        open: DoubleArray,
        volume: DoubleArray,
        fromIndex: Int,
        toIndex: Int,
        rows: Int = 24,
        valueAreaPercent: Double = 70.0,
    ): VolumeProfile {
        val size = minOf(high.size, low.size, close.size, open.size, volume.size)
        val start = max(0, fromIndex)
        val end = min(size - 1, toIndex)
        if (rows <= 0 || start > end) return emptyProfile(max(0, rows))

        var bottom = Double.MAX_VALUE
        var top = -Double.MAX_VALUE
        var bars = 0
        for (index in start..end) {
            if (!tradable(high[index], low[index], close[index], open[index], volume[index])) continue
            if (low[index] < bottom) bottom = low[index]
            if (high[index] > top) top = high[index]
            bars++
        }
        if (bars == 0) return emptyProfile(rows)

        val rowLow = DoubleArray(rows)
        val rowHigh = DoubleArray(rows)
        val rowVolume = DoubleArray(rows)
        val buy = DoubleArray(rows)
        val sell = DoubleArray(rows)
        // A window in which every bar printed at one price. The rows all collapse onto it and the
        // volume has exactly one place to go; the alternative, a zero height fed into the row
        // arithmetic below, is a division by zero on the quietest symbol in the list.
        val height = (top - bottom) / rows
        val flat = !(height > 0.0)
        for (row in 0 until rows) {
            rowLow[row] = if (flat) bottom else bottom + row * height
            rowHigh[row] = if (flat) top else bottom + (row + 1) * height
        }

        for (index in start..end) {
            if (!tradable(high[index], low[index], close[index], open[index], volume[index])) continue
            val traded = volume[index]
            if (traded <= 0.0) continue
            val bullish = close[index] > open[index]
            val barLow = low[index]
            val barHigh = high[index]
            if (flat || barHigh <= barLow) {
                val row = if (flat) 0 else rowOf(barLow, bottom, height, rows)
                rowVolume[row] += traded
                if (bullish) buy[row] += traded else sell[row] += traded
                continue
            }
            val reach = barHigh - barLow
            val firstRow = rowOf(barLow, bottom, height, rows)
            val lastRow = rowOf(barHigh, bottom, height, rows)
            for (row in firstRow..lastRow) {
                val overlapLow = if (barLow > rowLow[row]) barLow else rowLow[row]
                val overlapHigh = if (barHigh < rowHigh[row]) barHigh else rowHigh[row]
                val overlap = overlapHigh - overlapLow
                if (overlap <= 0.0) continue
                val share = traded * overlap / reach
                rowVolume[row] += share
                if (bullish) buy[row] += share else sell[row] += share
            }
        }

        var pocIndex = -1
        var best = 0.0
        var total = 0.0
        for (row in 0 until rows) {
            total += rowVolume[row]
            if (rowVolume[row] > best) {
                best = rowVolume[row]
                pocIndex = row
            }
        }
        if (pocIndex < 0) {
            return VolumeProfile(rowLow, rowHigh, rowVolume, buy, sell, -1, -1, -1)
        }

        val target = total * valueAreaPercent.coerceIn(0.0, 100.0) / 100.0
        var valueAreaLow = pocIndex
        var valueAreaHigh = pocIndex
        var covered = rowVolume[pocIndex]
        while (covered < target && (valueAreaLow > 0 || valueAreaHigh < rows - 1)) {
            val below = if (valueAreaLow > 0) rowVolume[valueAreaLow - 1] else Double.NEGATIVE_INFINITY
            val above = if (valueAreaHigh < rows - 1) rowVolume[valueAreaHigh + 1] else Double.NEGATIVE_INFINITY
            if (above > below) {
                valueAreaHigh++
                covered += rowVolume[valueAreaHigh]
            } else {
                valueAreaLow--
                covered += rowVolume[valueAreaLow]
            }
        }
        return VolumeProfile(rowLow, rowHigh, rowVolume, buy, sell, pocIndex, valueAreaLow, valueAreaHigh)
    }

    // ══════════════════════════════════════════════════════════ shared arithmetic

    /** The 34-bar EMA the chop zone measures. Fixed by the indicator, not a preference. */
    private const val CHOP_ZONE_EMA = 34

    /** An array of the right length that says "nothing here" at every bar. */
    private fun nan(size: Int): DoubleArray = DoubleArray(size) { Double.NaN }

    /**
     * A simple moving average that refuses a window containing a gap.
     *
     * The running sum is kept alongside a count of the `NaN`s inside the window rather than being
     * poisoned by them: one `NaN` added into a running total makes every later value `NaN` forever,
     * which would turn a single missing candle into a permanently blank indicator.
     */
    private fun sma(source: DoubleArray, period: Int): DoubleArray {
        val out = nan(source.size)
        if (period <= 0 || source.size < period) return out
        var sum = 0.0
        var missing = 0
        for (index in source.indices) {
            val value = source[index]
            if (value.isFinite()) sum += value else missing++
            if (index >= period) {
                val leaving = source[index - period]
                if (leaving.isFinite()) sum -= leaving else missing--
            }
            if (index >= period - 1 && missing == 0) out[index] = sum / period
        }
        return out
    }

    /** [sma] without the division — the rolling total the mass index sums. */
    private fun rollingSum(source: DoubleArray, period: Int): DoubleArray {
        val out = sma(source, period)
        for (index in out.indices) {
            if (out[index].isFinite()) out[index] = out[index] * period
        }
        return out
    }

    /**
     * An EMA that skips gaps rather than carrying across them, and publishes from its `period`-th
     * real sample.
     *
     * This is pack A's rule from [IndicatorsExt], chosen for the same reason it is right there: the
     * inputs here are frequently another indicator's output, and an average that started counting
     * during the warm-up would publish a number seeded almost entirely by its first value.
     */
    private fun ema(source: DoubleArray, period: Int): DoubleArray {
        val out = nan(source.size)
        if (period <= 0) return out
        val k = 2.0 / (period + 1)
        var previous = 0.0
        var seen = 0
        for (index in source.indices) {
            val value = source[index]
            if (!value.isFinite()) continue
            previous = if (seen == 0) value else value * k + previous * (1 - k)
            seen++
            if (seen >= period) out[index] = previous
        }
        return out
    }

    /** A weighted moving average, heaviest on the most recent bar. */
    private fun wma(source: DoubleArray, period: Int): DoubleArray {
        val out = nan(source.size)
        if (period <= 0 || source.size < period) return out
        val denominator = period * (period + 1) / 2.0
        for (index in period - 1 until source.size) {
            var sum = 0.0
            var complete = true
            for (step in 0 until period) {
                val value = source[index - step]
                if (!value.isFinite()) {
                    complete = false
                    break
                }
                sum += value * (period - step)
            }
            if (complete) out[index] = sum / denominator
        }
        return out
    }

    /** Percentage change over [period] bars. A zero base has no percentage, so it stays `NaN`. */
    private fun rateOfChange(source: DoubleArray, period: Int): DoubleArray {
        val out = nan(source.size)
        if (period <= 0) return out
        for (index in period until source.size) {
            val base = source[index - period]
            val value = source[index]
            if (base.isFinite() && value.isFinite() && base != 0.0) {
                out[index] = 100 * (value - base) / base
            }
        }
        return out
    }

    /** The highest value of the [period] bars ending at each index. */
    private fun highest(source: DoubleArray, period: Int): DoubleArray {
        val out = nan(source.size)
        if (period <= 0 || source.size < period) return out
        for (index in period - 1 until source.size) {
            var best = -Double.MAX_VALUE
            var complete = true
            for (step in 0 until period) {
                val value = source[index - step]
                if (!value.isFinite()) {
                    complete = false
                    break
                }
                if (value > best) best = value
            }
            if (complete) out[index] = best
        }
        return out
    }

    /** The lowest value of the [period] bars ending at each index. */
    private fun lowest(source: DoubleArray, period: Int): DoubleArray {
        val out = nan(source.size)
        if (period <= 0 || source.size < period) return out
        for (index in period - 1 until source.size) {
            var best = Double.MAX_VALUE
            var complete = true
            for (step in 0 until period) {
                val value = source[index - step]
                if (!value.isFinite()) {
                    complete = false
                    break
                }
                if (value < best) best = value
            }
            if (complete) out[index] = best
        }
        return out
    }

    /**
     * Wilder's ATR, seeded with the simple mean of the first [period] true ranges.
     *
     * Recomputed here rather than borrowed from [Indicators] because that one returns a [Line] and
     * this pack works in raw arrays; the arithmetic is the same, deliberately, so a Chande–Kroll
     * stop and an ATR band drawn on the same chart agree about the same bar.
     */
    private fun averageTrueRange(
        high: DoubleArray,
        low: DoubleArray,
        close: DoubleArray,
        period: Int,
    ): DoubleArray {
        val size = close.size
        val out = nan(size)
        if (period <= 0 || size < period) return out
        val range = DoubleArray(size)
        for (index in 0 until size) {
            range[index] = if (index == 0) {
                high[index] - low[index]
            } else {
                maxOf(
                    high[index] - low[index],
                    abs(high[index] - close[index - 1]),
                    abs(low[index] - close[index - 1]),
                )
            }
        }
        var previous = 0.0
        var seeded = false
        for (index in 0 until size) {
            if (!seeded) {
                if (index < period - 1) continue
                var sum = 0.0
                var complete = true
                for (step in 0 until period) {
                    if (!range[index - step].isFinite()) {
                        complete = false
                        break
                    }
                    sum += range[index - step]
                }
                if (!complete) continue
                previous = sum / period
                seeded = true
            } else {
                val value = range[index]
                if (!value.isFinite()) continue
                previous = (previous * (period - 1) + value) / period
            }
            out[index] = previous
        }
        return out
    }

    /** The CCI proper, shared by both of Woodie's lines. Lambert's 0.015 is in here once. */
    private fun commodityChannel(typical: DoubleArray, period: Int): DoubleArray {
        val out = nan(typical.size)
        if (period <= 0 || typical.size < period) return out
        val mean = sma(typical, period)
        for (index in period - 1 until typical.size) {
            val average = mean[index]
            if (!average.isFinite()) continue
            var deviation = 0.0
            for (step in 0 until period) deviation += abs(typical[index - step] - average)
            deviation /= period
            out[index] = if (deviation == 0.0) 0.0 else (typical[index] - average) / (0.015 * deviation)
        }
        return out
    }

    /** A bar this profile can use: five real numbers and a range that is not upside down. */
    private fun tradable(
        high: Double,
        low: Double,
        close: Double,
        open: Double,
        volume: Double,
    ): Boolean =
        high.isFinite() && low.isFinite() && close.isFinite() && open.isFinite() &&
            volume.isFinite() && volume >= 0.0 && high >= low

    /** Which row a price falls in, clamped so the window's own high does not fall off the top. */
    private fun rowOf(price: Double, bottom: Double, height: Double, rows: Int): Int =
        floor((price - bottom) / height).toInt().coerceIn(0, rows - 1)

    /** The profile of a window with nothing in it: real geometry is unknowable, so none is claimed. */
    private fun emptyProfile(rows: Int): VolumeProfile = VolumeProfile(
        rowLow = DoubleArray(rows),
        rowHigh = DoubleArray(rows),
        volume = DoubleArray(rows),
        buy = DoubleArray(rows),
        sell = DoubleArray(rows),
        pocIndex = -1,
        valueAreaLow = -1,
        valueAreaHigh = -1,
    )
}
