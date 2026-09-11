package com.coinepro.core.chart

import kotlin.math.abs

/**
 * Three plain-language readings of a series.
 *
 * The point of this file is that a reader should not have to switch on ADX, ATR and two moving
 * averages to learn three things the app can already compute. Every value here is the app's own
 * arithmetic — the same `Indicators` the chart draws — reduced to one word.
 *
 * The words are deliberately coarse. A trend strength of «۶۲ از ۱۰۰» is a false precision: ADX is a
 * noisy measure and nobody trades the difference between 61 and 63. Three bands, named for what
 * they mean, is as much as the number can honestly carry.
 */
data class ChartReading(
    /** Wilder's ADX on the last bar. */
    val strength: Double,
    /**
     * Today's ATR as a percentile of its own recent range, 0..1.
     *
     * A ratio rather than a price, because "volatile" is a comparison an instrument makes with
     * itself: two dollars of daily range is nothing on bitcoin and a storm on EURUSD.
     */
    val volatility: Double,
    /** Fast average minus slow, as a fraction of price. Positive is up. */
    val bias: Double,
) {

    /**
     * The three readings, in words, in the language the screen is in (4.72.0).
     *
     * They were Persian only, from this table, and this module has no resources — so the English
     * tablet read «Trend strength: متوسط · Volatility: کم · Bias: خنثی». Both languages are here for
     * the reason the tool names are in `DrawingTools`: the *threshold* and the word belong together,
     * and a second table keyed by the same thresholds in `strings.xml` is a second place to get the
     * boundary wrong.
     */
    fun strengthLabel(english: Boolean = false): String = when {
        strength >= STRONG -> if (english) "Strong" else "قوی"
        strength >= TRENDING -> if (english) "Moderate" else "متوسط"
        else -> if (english) "No trend" else "بدون روند"
    }

    fun volatilityLabel(english: Boolean = false): String = when {
        volatility >= HIGH_VOL -> if (english) "High" else "زیاد"
        volatility >= MID_VOL -> if (english) "Medium" else "متوسط"
        else -> if (english) "Low" else "کم"
    }

    fun biasLabel(english: Boolean = false): String = when {
        bias > BIAS_EDGE -> if (english) "Bullish" else "صعودی"
        bias < -BIAS_EDGE -> if (english) "Bearish" else "نزولی"
        else -> if (english) "Neutral" else "خنثی"
    }

    val isUp: Boolean get() = bias > BIAS_EDGE
    val isDown: Boolean get() = bias < -BIAS_EDGE

    companion object {
        /** Wilder's own threshold for "there is a trend here at all". */
        private const val TRENDING = 20.0

        /**
         * The same threshold, published, so a caller can colour by the *number* rather than by the
         * word. Before 4.72.0 the chart's tint compared `strengthLabel` against «بدون روند», which
         * stopped meaning anything the moment the label could also be `No trend`.
         */
        const val TRENDING_FLOOR = TRENDING
        private const val STRONG = 30.0

        private const val MID_VOL = 0.35
        private const val HIGH_VOL = 0.70

        /**
         * How far the averages must separate before the market is called.
         *
         * A tenth of a percent of price. Below it the two lines are touching, and naming that a
         * direction would put a word on noise.
         */
        private const val BIAS_EDGE = 0.001

        /** Bars needed before any of this means anything. Below it there is no reading, not a zero. */
        private const val MINIMUM_BARS = 60

        /**
         * Reads [series], or null when there is not enough of it.
         *
         * Null rather than a neutral default: a card saying «خنثی» over thirty bars is a claim, and
         * the honest answer to "what is this market doing" on thirty bars is that we cannot tell.
         */
        fun of(series: CandleSeries): ChartReading? {
            if (series.bars.size < MINIMUM_BARS) return null
            val close = series.close
            val last = close.lastIndex

            val adx = Indicators.adx(series.high, series.low, close).adx[last] ?: return null

            val atr = Indicators.atr(series.high, series.low, close)
            val recent = (last - ATR_WINDOW + 1).coerceAtLeast(0)..last
            val values = recent.mapNotNull { atr[it] }
            if (values.isEmpty()) return null
            val now = values.last()
            val low = values.min()
            val high = values.max()
            val span = high - low
            val percentile = if (span <= 0.0) 0.5 else (now - low) / span

            val fast = Indicators.ema(close, FAST)[last] ?: return null
            val slow = Indicators.ema(close, SLOW)[last] ?: return null
            val price = close[last]
            val bias = if (price == 0.0) 0.0 else (fast - slow) / abs(price)

            return ChartReading(strength = adx, volatility = percentile, bias = bias)
        }

        /** How far back the volatility comparison looks — a fortnight of daily bars, or a day of hourly. */
        private const val ATR_WINDOW = 60
        private const val FAST = 20
        private const val SLOW = 50
    }
}
