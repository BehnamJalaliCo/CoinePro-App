package com.coinepro.core.chart

import kotlin.math.abs

/**
 * The automatic detections Pro-Chart's terminal drew and this app did not until 5.13.0: harmonic
 * patterns, RSI divergences, price gaps, and the TradingView-style technical rating.
 *
 * Ports of `harmonic.js`, `divergence.js`, `gaps.js` and `techRating.js` in that repository, with
 * the same thresholds and the same pivot rules, so a reader moving from the site sees the same
 * marks on the same bars. Each returns plain data; `ChartCatalog` turns it into marks and lines.
 */
object Detections {

    // ── harmonic patterns ─────────────────────────────────────────────────────────────────

    /** One of the four classic harmonic patterns, with its Fibonacci windows. */
    enum class Harmonic(
        val short: String,
        val ab: ClosedFloatingPointRange<Double>,
        val bc: ClosedFloatingPointRange<Double>,
        val cd: ClosedFloatingPointRange<Double>,
        val ad: ClosedFloatingPointRange<Double>,
    ) {
        GARTLEY("Gartley", 0.55..0.68, 0.38..0.90, 1.10..1.70, 0.72..0.84),
        BAT("Bat", 0.35..0.55, 0.38..0.90, 1.55..2.70, 0.84..0.92),
        BUTTERFLY("Butterfly", 0.74..0.82, 0.38..0.90, 1.55..2.30, 1.24..1.65),
        CRAB("Crab", 0.35..0.65, 0.38..0.90, 2.10..3.70, 1.55..1.70),
    }

    /** A turning point: bar [index], its price, and whether it is a high. */
    data class Swing(val index: Int, val price: Double, val high: Boolean)

    /** A completed pattern: its kind, the five points X, A, B, C, D, and whether D is a low. */
    data class HarmonicMatch(val pattern: Harmonic, val points: List<Swing>, val bullish: Boolean)

    /**
     * The harmonic patterns completed among the last few alternating swings (three bars either
     * side), matched against [Harmonic]'s windows — the first pattern that fits wins, as there.
     * Needs forty bars; one match per D point.
     */
    fun harmonics(series: CandleSeries): List<HarmonicMatch> {
        if (series.size < 40) return emptyList()
        val swings = alternatingSwings(series, 3, 3)
        if (swings.size < 5) return emptyList()
        val found = LinkedHashMap<Int, HarmonicMatch>()
        for (start in maxOf(0, swings.size - 8)..swings.size - 5) {
            val (x, a, b, c, d) = swings.subList(start, start + 5)
            if (x.high == a.high || a.high == b.high || b.high == c.high || c.high == d.high) continue
            val xa = abs(a.price - x.price)
            val ab = abs(b.price - a.price)
            val bc = abs(c.price - b.price)
            val cd = abs(d.price - c.price)
            val ad = abs(d.price - a.price)
            if (xa == 0.0 || ab == 0.0 || bc == 0.0) continue
            val rAb = ab / xa
            val rBc = bc / ab
            val rCd = cd / bc
            val rAd = ad / xa
            val match = Harmonic.entries.firstOrNull { p -> rAb in p.ab && rBc in p.bc && rCd in p.cd && rAd in p.ad }
            if (match != null && d.index !in found) {
                found[d.index] = HarmonicMatch(match, listOf(x, a, b, c, d), bullish = !d.high)
            }
        }
        return found.values.sortedBy { it.points.last().index }
    }

    /** Confirmed pivots, made to alternate high-low by keeping the more extreme of two in a row. */
    fun alternatingSwings(series: CandleSeries, left: Int, right: Int): List<Swing> {
        val raw = ArrayList<Swing>()
        for (i in left until series.size - right) {
            var isHigh = true
            var isLow = true
            for (j in i - left..i + right) {
                if (j == i) continue
                if (series.high[j] >= series.high[i]) isHigh = false
                if (series.low[j] <= series.low[i]) isLow = false
            }
            if (isHigh) raw += Swing(i, series.high[i], high = true)
            if (isLow) raw += Swing(i, series.low[i], high = false)
        }
        val out = ArrayList<Swing>()
        for (p in raw) {
            val last = out.lastOrNull()
            if (last == null || last.high != p.high) {
                out += p
            } else if ((p.high && p.price > last.price) || (!p.high && p.price < last.price)) {
                out[out.size - 1] = p
            }
        }
        return out
    }

    // ── RSI divergence ────────────────────────────────────────────────────────────────────

    /** Regular divergence warns of a turn; hidden divergence says the trend should continue. */
    data class Divergence(val from: Int, val to: Int, val bullish: Boolean, val hidden: Boolean)

    /**
     * RSI divergences between consecutive confirmed pivots ([left] and [right] bars): a lower low
     * in price with a higher low in RSI is regular bullish, a higher low in price with a lower one
     * in RSI is hidden bullish, and the mirror images on the highs are bearish.
     */
    fun rsiDivergences(series: CandleSeries, period: Int = 14, left: Int = 5, right: Int = 5): List<Divergence> {
        if (series.size < period + left + right + 2) return emptyList()
        val rsi = wilderRsi(series.close, period)
        val lows = ArrayList<Int>()
        val highs = ArrayList<Int>()
        for (i in left until series.size - right) {
            if (rsi[i].isNaN()) continue
            var isLow = true
            var isHigh = true
            for (j in i - left..i + right) {
                if (j == i) continue
                if (series.low[j] <= series.low[i]) isLow = false
                if (series.high[j] >= series.high[i]) isHigh = false
            }
            if (isLow) lows += i
            if (isHigh) highs += i
        }
        val out = ArrayList<Divergence>()
        for (k in 1 until lows.size) {
            val a = lows[k - 1]
            val b = lows[k]
            when {
                series.low[b] < series.low[a] && rsi[b] > rsi[a] -> out += Divergence(a, b, bullish = true, hidden = false)
                series.low[b] > series.low[a] && rsi[b] < rsi[a] -> out += Divergence(a, b, bullish = true, hidden = true)
            }
        }
        for (k in 1 until highs.size) {
            val a = highs[k - 1]
            val b = highs[k]
            when {
                series.high[b] > series.high[a] && rsi[b] < rsi[a] -> out += Divergence(a, b, bullish = false, hidden = false)
                series.high[b] < series.high[a] && rsi[b] > rsi[a] -> out += Divergence(a, b, bullish = false, hidden = true)
            }
        }
        return out.sortedBy { it.to }
    }

    /** `divergence.js`'s RSI: seeded on the first [period] changes, 100 where there is no loss. */
    fun wilderRsi(close: DoubleArray, period: Int): DoubleArray {
        val out = DoubleArray(close.size) { Double.NaN }
        if (close.size <= period) return out
        var gain = 0.0
        var loss = 0.0
        for (i in 1..period) {
            val d = close[i] - close[i - 1]
            if (d >= 0) gain += d else loss -= d
        }
        gain /= period
        loss /= period
        out[period] = if (loss == 0.0) 100.0 else 100 - 100 / (1 + gain / loss)
        for (i in period + 1 until close.size) {
            val d = close[i] - close[i - 1]
            gain = (gain * (period - 1) + (if (d > 0) d else 0.0)) / period
            loss = (loss * (period - 1) + (if (d < 0) -d else 0.0)) / period
            out[i] = if (loss == 0.0) 100.0 else 100 - 100 / (1 + gain / loss)
        }
        return out
    }

    // ── gaps ──────────────────────────────────────────────────────────────────────────────

    /** A bar that opened away from the previous close by at least the threshold, in percent. */
    data class Gap(val index: Int, val up: Boolean, val percent: Double)

    /** Every bar whose open is at least [minPercent] percent from the previous close. */
    fun gaps(series: CandleSeries, minPercent: Double = 0.1): List<Gap> {
        val out = ArrayList<Gap>()
        for (i in 1 until series.size) {
            val previous = series.close[i - 1]
            if (previous == 0.0 || !previous.isFinite() || !series.open[i].isFinite()) continue
            val pct = (series.open[i] - previous) / previous * 100
            if (abs(pct) >= minPercent) out += Gap(i, up = pct > 0, percent = pct)
        }
        return out
    }
}
