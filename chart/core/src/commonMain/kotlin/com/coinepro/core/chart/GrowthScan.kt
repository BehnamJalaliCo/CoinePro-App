package com.coinepro.core.chart

import kotlin.math.max
import kotlin.math.min

/**
 * The screener's «which market is starting to move up» reading (5.17.0).
 *
 * TradingView's screeners filter on a value — RSI under 30, price over the 50-day average. What a
 * reader actually asks a screener is a question about an *event*: which markets started a trend
 * this week, which broke out of their range on volume, which turned up out of a pullback inside an
 * uptrend. Each of those is a named setup with a mechanical definition, and this object is the
 * catalogue of them — every one computed from the same arithmetic the chart draws, so a market the
 * screener lists under «trend started» shows the SuperTrend turning green on the chart it opens.
 *
 * ### What a scan answers
 *
 * [Kind] is a setup; [of] returns, for each kind, how many bars ago it last fired on the series —
 * `0` for the newest bar — within [LOOKBACK], or nothing. «Bars ago» rather than a yes/no, because
 * «fired three bars ago» and «fired twenty bars ago» are different answers to «is it starting?», and
 * a filter picks its own horizon.
 *
 * [Scan.growth] is the composite: 0–100, how many of the conditions that precede a sustained rise
 * are true on the newest bar, weighted. It is a checklist score, not a forecast — the weights are
 * printed in [growthOf] and nothing else goes into it. [Scan.reliability] is the forecast's only
 * honest substitute: how often the trend-start signal has actually been followed by a rise on *this*
 * market's own history, measured by [ConfidenceEngine] with its sample count.
 *
 * ### The chart that goes with each
 *
 * [Kind.studies] is the set of chart studies that show the setup — the lines and structures a
 * trader would have drawn by hand to see it. The screener opens a market with them already on, so
 * the reason a market is on the list is on the chart the reader lands on.
 */
object GrowthScan {

    /** A setup the screener can look for. [bullish] is false only for the warning. */
    enum class Kind(
        val id: String,
        val label: String,
        val labelEn: String,
        val bullish: Boolean,
        /** The chart studies that draw this setup, by catalogue id. */
        val studies: List<String>,
    ) {
        /** SuperTrend (10, 3) turning up, or EMA 20 crossing EMA 50 with ADX over 18 and +DI leading. */
        TREND_START("scan_trend", "شروع روند صعودی", "Uptrend starting", true, listOf("supertrend", "ema", "adx")),
        /** The close through the prior twenty bars' high, on volume or on a range expansion. */
        BREAKOUT("scan_breakout", "شکست سقف با حجم", "Breakout on volume", true, listOf("donchian")),
        /** MACD's histogram crossing zero upwards with RSI between 45 and 70. */
        MOMENTUM("scan_momentum", "برگشت شتاب به بالا", "Momentum turning up", true, listOf("macd", "rsi")),
        /** SMA 50 crossing SMA 200 upwards. */
        GOLDEN_CROSS("scan_golden", "تقاطع طلایی", "Golden cross", true, listOf("sma")),
        /** Bollinger bands leaving the Keltner channel upwards after a squeeze. */
        SQUEEZE("scan_squeeze", "آزاد شدن فشردگی", "Squeeze release", true, listOf("bollinger", "keltner")),
        /** Volume over two and a half times its twenty-bar average on an up bar. */
        VOLUME_SURGE("scan_volume", "جهش حجم خرید", "Buying volume surge", true, listOf("volumeosc")),
        /** A regular bullish RSI divergence, confirmed. */
        DIVERGENCE("scan_divergence", "واگرایی مثبت", "Bullish divergence", true, listOf("rsi", "divergence")),
        /** RSI back over 40 while the close is over EMA 200 and EMA 50 is over EMA 200. */
        PULLBACK("scan_pullback", "پایان اصلاح در روند صعودی", "Pullback ending in an uptrend", true, listOf("ema", "rsi")),
        /** The close over the newest confirmed swing high. */
        STRUCTURE_BREAK("scan_structure", "شکست ساختار", "Break of structure", true, listOf("zigzag", "sr")),
        /** A bullish harmonic pattern completed. */
        HARMONIC("scan_harmonic", "الگوی هارمونیک صعودی", "Bullish harmonic", true, listOf("harmonics")),
        /** SuperTrend turning down: the warning that ends every other setup. */
        TREND_END("scan_trend_end", "پایان روند صعودی", "Uptrend ending", false, listOf("supertrend")),
        ;

        companion object {
            fun of(id: String): Kind? = entries.firstOrNull { it.id == id }

            /** The setups a reader looks for, without the warning. */
            val BULLISH: List<Kind> = entries.filter(Kind::bullish)
        }
    }

    /** One market's reading. */
    data class Scan(
        /** Bars since each kind last fired, within [LOOKBACK]. A kind that did not fire is absent. */
        val barsAgo: Map<Kind, Int>,
        /** 0–100. See [growthOf]. */
        val growth: Double,
        /**
         * The trend-start signal's historical hit rate on this market, 0–100, or null with fewer than
         * [MIN_SAMPLES] completed signals — a percentage of two is not a number to act on.
         */
        val reliability: Double?,
        /** How many completed trend-start signals [reliability] is measured over. */
        val samples: Int,
    ) {
        /** The setups that fired within [within] bars, freshest first. */
        fun fresh(within: Int = FRESH): List<Pair<Kind, Int>> =
            barsAgo.filter { (kind, ago) -> kind.bullish && ago <= within }.toList().sortedBy { it.second }
    }

    /** The key a scan value is stored under in the screener's reading map. */
    const val GROWTH_ID: String = "scan_growth"
    const val RELIABILITY_ID: String = "scan_reliability"

    /** Every id this scan answers, for the screener's key set. */
    val IDS: List<String> = Kind.entries.map(Kind::id) + GROWTH_ID + RELIABILITY_ID

    fun isScanId(id: String): Boolean = id == GROWTH_ID || id == RELIABILITY_ID || Kind.of(id) != null

    /** What a scan id is called. */
    fun labelOf(id: String, english: Boolean): String? = when (id) {
        GROWTH_ID -> if (english) "Growth score" else "امتیاز رشد"
        RELIABILITY_ID -> if (english) "Hit rate" else "نرخ موفقیت"
        else -> Kind.of(id)?.let { if (english) it.labelEn else it.label }
    }

    /** The reading for [id] out of [scan], in the unit the screener stores: a score, a rate, or bars. */
    fun valueOf(id: String, scan: Scan): Double? = when (id) {
        GROWTH_ID -> scan.growth
        RELIABILITY_ID -> scan.reliability
        else -> Kind.of(id)?.let { scan.barsAgo[it]?.toDouble() }
    }

    /** Scans [series], or null when it is too short for the two-hundred-bar average to exist. */
    fun of(series: CandleSeries): Scan? {
        if (series.size < MIN_BARS) return null
        val c = Context(series)
        val found = LinkedHashMap<Kind, Int>()
        val last = series.size - 1
        val from = max(1, series.size - LOOKBACK)
        for (i in last downTo from) {
            for (kind in Kind.entries) {
                if (kind in found) continue
                if (c.fires(kind, i)) found[kind] = last - i
            }
        }
        // Structure detectors answer for a whole series at once, so they are read once, not per bar.
        Detections.rsiDivergences(series).lastOrNull { it.bullish && !it.hidden }?.let { divergence ->
            // Confirmed `right` bars after the pivot; the event is the confirmation, not the pivot.
            val confirmed = min(last, divergence.to + DIVERGENCE_CONFIRM)
            if (last - confirmed <= LOOKBACK) found[Kind.DIVERGENCE] = last - confirmed
        }
        Detections.harmonics(series).lastOrNull { it.bullish }?.let { match ->
            val d = match.points.last().index
            if (last - d <= LOOKBACK) found[Kind.HARMONIC] = last - d
        }
        val reliability = runCatching {
            ConfidenceEngine.measure(SignalSpec.read("supertrend", series), series)
        }.getOrNull()
        val samples = reliability?.samples ?: 0
        return Scan(
            barsAgo = found,
            growth = growthOf(c, found),
            reliability = reliability?.takeIf { it.samples >= MIN_SAMPLES }?.let { it.winRate * 100.0 },
            samples = samples,
        )
    }

    /**
     * The composite, on the newest bar. The weights, all of them:
     *
     * * **Trend, 30** — close over EMA 20 (6), EMA 20 over EMA 50 (8), EMA 50 over EMA 200 (8), close
     *   over EMA 200 (8).
     * * **Momentum, 20** — RSI 50–70 (8; 45–50 gives 4), MACD histogram over zero (6) and rising (6).
     * * **Strength, 15** — ADX 20 or more with +DI over −DI (10), ADX rising (5).
     * * **Participation, 10** — the share of the last twenty bars' volume traded on up bars, scaled
     *   from 50 % (0) to 70 % (10); 5 where the feed has no volume, so a forex pair is not penalised
     *   for a column its broker never sends.
     * * **Fresh setups, 15** — 5 for each bullish setup within [FRESH] bars, at most three.
     * * **Rating, 10** — TradingView's technical rating, when positive, times ten.
     * * **Penalties** — the trend ending within [FRESH] bars (−15), RSI over 80 (−5).
     */
    internal fun growthOf(c: Context, found: Map<Kind, Int>): Double {
        val i = c.last
        var score = 0.0
        val close = c.close[i]
        if (c.ema20.at(i)?.let { close > it } == true) score += 6
        if (both(c.ema20, c.ema50, i) { a, b -> a > b }) score += 8
        if (both(c.ema50, c.ema200, i) { a, b -> a > b }) score += 8
        if (c.ema200.at(i)?.let { close > it } == true) score += 8

        val rsi = c.rsi.at(i)
        if (rsi != null) {
            if (rsi in 50.0..70.0) score += 8 else if (rsi >= 45.0 && rsi < 50.0) score += 4
            if (rsi > 80.0) score -= 5
        }
        val hist = c.macd.histogram.at(i)
        val histBefore = c.macd.histogram.at(i - 1)
        if (hist != null && hist > 0) score += 6
        if (hist != null && histBefore != null && hist > histBefore) score += 6

        val adx = c.adx.adx.at(i)
        val plus = c.adx.plusDi.at(i)
        val minus = c.adx.minusDi.at(i)
        if (adx != null && plus != null && minus != null && adx >= 20 && plus > minus) score += 10
        val adxBefore = c.adx.adx.at(i - 3)
        if (adx != null && adxBefore != null && adx > adxBefore) score += 5

        score += if (c.hasVolume) {
            var up = 0.0
            var total = 0.0
            for (k in max(1, i - 19)..i) {
                total += c.volume[k]
                if (c.close[k] > c.close[k - 1]) up += c.volume[k]
            }
            if (total <= 0.0) 5.0 else ((up / total - 0.5) / 0.2 * 10.0).coerceIn(0.0, 10.0)
        } else {
            5.0
        }

        score += 5.0 * min(3, found.count { (kind, ago) -> kind.bullish && ago <= FRESH })
        c.rating.overall.getOrNull(i)?.takeIf { it.isFinite() && it > 0 }?.let { score += it * 10.0 }
        if ((found[Kind.TREND_END] ?: Int.MAX_VALUE) <= FRESH) score -= 15
        return score.coerceIn(0.0, 100.0)
    }

    /** Every line the detectors read, computed once per series. */
    internal class Context(val series: CandleSeries) {
        val close = series.close
        val open = series.open
        val high = series.high
        val low = series.low
        val volume = series.volume
        val hasVolume = series.hasVolume
        val last = series.size - 1
        val ema20 = Indicators.ema(close, 20)
        val ema50 = Indicators.ema(close, 50)
        val ema200 = Indicators.ema(close, 200)
        val sma50 = Indicators.sma(close, 50)
        val sma200 = Indicators.sma(close, 200)
        val rsi = Indicators.rsi(close, 14)
        val macd = Indicators.macd(close)
        val adx = Indicators.adx(high, low, close, 14)
        val atr = Indicators.atr(high, low, close, 14)
        val superTrend = Indicators.supertrend(high, low, close, 10, 3.0)
        val bands = Indicators.bollinger(close, 20, 2.0)
        val keltner = Indicators.keltner(high, low, close, 20, 1.5)
        val volumeAverage = Indicators.sma(volume, 20)
        val rating by lazy { TechnicalRating.of(series) }
        private val swingHighs: List<Int> by lazy { pivotHighs() }

        fun fires(kind: Kind, i: Int): Boolean = when (kind) {
            Kind.TREND_START -> trendTurned(i, up = true) || emaCrossWithStrength(i)
            Kind.TREND_END -> trendTurned(i, up = false)
            Kind.BREAKOUT -> breakout(i)
            Kind.MOMENTUM -> momentum(i)
            Kind.GOLDEN_CROSS -> crossedAbove(sma50, sma200, i)
            Kind.SQUEEZE -> squeezeRelease(i)
            Kind.VOLUME_SURGE -> volumeSurge(i)
            Kind.PULLBACK -> pullback(i)
            Kind.STRUCTURE_BREAK -> structureBreak(i)
            // Read once for the whole series in [of].
            Kind.DIVERGENCE, Kind.HARMONIC -> false
        }

        private fun trendTurned(i: Int, up: Boolean): Boolean {
            val now = superTrend.trend.at(i) ?: return false
            val before = superTrend.trend.at(i - 1) ?: return false
            return if (up) now > 0 && before < 0 else now < 0 && before > 0
        }

        private fun emaCrossWithStrength(i: Int): Boolean {
            if (!crossedAbove(ema20, ema50, i)) return false
            val adxNow = adx.adx.at(i) ?: return false
            val plus = adx.plusDi.at(i) ?: return false
            val minus = adx.minusDi.at(i) ?: return false
            return adxNow >= 18 && plus > minus
        }

        private fun breakout(i: Int): Boolean {
            if (i < BREAKOUT_WINDOW + 1) return false
            val ceiling = highest(i - BREAKOUT_WINDOW, i - 1)
            val ceilingBefore = highest(i - BREAKOUT_WINDOW - 1, i - 2)
            if (close[i] <= ceiling || close[i - 1] > ceilingBefore) return false
            return if (hasVolume) {
                val average = volumeAverage.at(i - 1) ?: return false
                average > 0 && volume[i] >= average * 1.5
            } else {
                val range = atr.at(i - 1) ?: return false
                high[i] - low[i] >= range
            }
        }

        private fun momentum(i: Int): Boolean {
            val now = macd.histogram.at(i) ?: return false
            val before = macd.histogram.at(i - 1) ?: return false
            val strength = rsi.at(i) ?: return false
            return before <= 0 && now > 0 && strength in 45.0..70.0
        }

        private fun squeezeRelease(i: Int): Boolean {
            if (i < 7) return false
            var squeezed = 0
            for (k in i - 6 until i) if (inside(k)) squeezed += 1
            if (squeezed < 5 || inside(i)) return false
            val upper = bands.upper.at(i) ?: return false
            val keltnerUpper = keltner.upper.at(i) ?: return false
            val basis = bands.basis.at(i) ?: return false
            return upper > keltnerUpper && close[i] > basis
        }

        private fun inside(k: Int): Boolean {
            val bu = bands.upper.at(k) ?: return false
            val bl = bands.lower.at(k) ?: return false
            val ku = keltner.upper.at(k) ?: return false
            val kl = keltner.lower.at(k) ?: return false
            return bu < ku && bl > kl
        }

        private fun volumeSurge(i: Int): Boolean {
            if (!hasVolume) return false
            val average = volumeAverage.at(i - 1) ?: return false
            return average > 0 && volume[i] >= average * 2.5 && close[i] > open[i] && close[i] > close[i - 1]
        }

        private fun pullback(i: Int): Boolean {
            val fast = ema50.at(i) ?: return false
            val slow = ema200.at(i) ?: return false
            val now = rsi.at(i) ?: return false
            val before = rsi.at(i - 1) ?: return false
            return fast > slow && close[i] > slow && before < 40.0 && now >= 40.0
        }

        private fun structureBreak(i: Int): Boolean {
            // The newest swing high confirmed before bar i: five bars either side, all before i.
            val pivot = swingHighs.lastOrNull { it + PIVOT_SIDE < i } ?: return false
            val level = high[pivot]
            return close[i] > level && close[i - 1] <= level
        }

        private fun pivotHighs(): List<Int> {
            val out = ArrayList<Int>()
            for (k in PIVOT_SIDE until high.size - PIVOT_SIDE) {
                var peak = true
                for (j in k - PIVOT_SIDE..k + PIVOT_SIDE) {
                    if (j != k && high[j] >= high[k]) {
                        peak = false
                        break
                    }
                }
                if (peak) out += k
            }
            return out
        }

        private fun highest(from: Int, to: Int): Double {
            var top = Double.NEGATIVE_INFINITY
            for (k in max(0, from)..to) top = max(top, high[k])
            return top
        }

        private fun crossedAbove(fast: Line, slow: Line, i: Int): Boolean {
            val a = fast.at(i) ?: return false
            val b = slow.at(i) ?: return false
            val a0 = fast.at(i - 1) ?: return false
            val b0 = slow.at(i - 1) ?: return false
            return a > b && a0 <= b0
        }
    }

    private fun Line.at(index: Int): Double? =
        if (index >= 0 && isPresent(index)) raw(index).takeIf(Double::isFinite) else null

    private inline fun both(a: Line, b: Line, i: Int, test: (Double, Double) -> Boolean): Boolean {
        val x = a.at(i) ?: return false
        val y = b.at(i) ?: return false
        return test(x, y)
    }

    /** How far back a setup counts as having fired at all. */
    const val LOOKBACK: Int = 30

    /** «Fresh»: the horizon the growth score and the row's tags use. */
    const val FRESH: Int = 5

    /** Enough for EMA 200 to have warmed and a few bars besides. */
    const val MIN_BARS: Int = 60

    const val MIN_SAMPLES: Int = 5

    private const val BREAKOUT_WINDOW = 20
    private const val PIVOT_SIDE = 5
    private const val DIVERGENCE_CONFIRM = 5
}
