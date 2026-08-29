package com.coinepro.feature.screener

import com.coinepro.core.chart.Indicators
import com.coinepro.core.chart.IndicatorsExt
import com.coinepro.core.chart.Line
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.feature.screener.model.ScreenerIndicatorId

/**
 * One indicator, reduced to the single number a screener can put a threshold on — [109].
 *
 * ### Why a screener needs its own reading of an indicator
 *
 * A chart wants a whole series: every bar's RSI, so it can be drawn. A screener wants the *last*
 * one, for a thousand markets, so it can be compared. Those are different jobs with different costs
 * — the chart's answer is one symbol and several hundred values, the screener's is one value and
 * several hundred symbols — and the difference is why the reduction happens here rather than in
 * `core:chart`. What does not differ, and must not, is the arithmetic: every reading below comes
 * out of `core:chart`'s library unchanged, so the RSI this screen filters on is the number the
 * chart would have drawn for the same market. A screener that disagreed with its own chart would be
 * worse than no screener.
 *
 * ### Some readings are normalised and some are not
 *
 * RSI, ADX, the stochastic and Bollinger %B are already comparable across markets — they are bounded
 * or scale-free by construction, so a threshold of thirty means the same thing on gold as on a
 * satoshi-priced coin. ATR and the moving-average distances are **not**: an ATR of 12 is enormous on
 * a currency pair and negligible on Bitcoin. Those three are therefore published as a percentage of
 * price rather than in the instrument's own units, which is the only form in which a single number
 * typed into a filter can mean anything across a mixed catalogue. The MACD histogram is left in the
 * instrument's units on purpose: it is a sign-and-direction reading, and a reader filtering it is
 * almost always asking for "above zero", which normalising would not help.
 *
 * ### Warm-up is a null, never a substitute
 *
 * A fifty-bar average genuinely does not exist on a market with forty bars of history, and every
 * answer other than null is a lie a filter would act on. `core:chart`'s `Line` already carries that
 * distinction; this preserves it rather than flattening it to zero.
 */
object ScreenerIndicators {

    /**
     * The last reading of [indicatorId] over [bars], or null when it cannot be computed.
     *
     * Null covers three different situations on purpose, because a caller can do nothing different
     * about any of them: an id this build does not know, a series too short to warm the indicator
     * up, and a reading that came out non-finite because the market's own numbers divided badly.
     *
     * @param period the lookback, or null for the indicator's own default. Clamped into
     *   [MIN_PERIOD]..[MAX_PERIOD] rather than refused, because the bound comes from a saved screen
     *   that a later build may have written with a wider range, and dropping a reader's filter is a
     *   worse answer than answering it with the nearest period this build supports.
     */
    fun compute(indicatorId: String, period: Int?, bars: List<OhlcBar>): Double? {
        if (bars.size < MIN_BARS) return null
        val length = (period ?: ScreenerIndicatorId.defaultPeriodOf(indicatorId) ?: DEFAULT_PERIOD)
            .coerceIn(MIN_PERIOD, MAX_PERIOD)
        val close = DoubleArray(bars.size) { bars[it].c }
        val high = DoubleArray(bars.size) { bars[it].h }
        val low = DoubleArray(bars.size) { bars[it].l }
        val last = close.last()

        val reading = when (indicatorId) {
            ScreenerIndicatorId.RSI -> Indicators.rsi(close, length).lastValue()
            ScreenerIndicatorId.ADX -> Indicators.adx(high, low, close, length).adx.lastValue()
            ScreenerIndicatorId.STOCHASTIC_K -> Indicators.stochastic(high, low, close, length).k.lastValue()
            ScreenerIndicatorId.MACD_HISTOGRAM -> Indicators.macd(close).histogram.lastValue()
            ScreenerIndicatorId.ATR_PERCENT ->
                Indicators.atr(high, low, close, length).lastValue()?.asPercentOf(last)
            ScreenerIndicatorId.SMA_DISTANCE ->
                Indicators.sma(close, length).lastValue()?.let { distance(last, it) }
            ScreenerIndicatorId.EMA_DISTANCE ->
                Indicators.ema(close, length).lastValue()?.let { distance(last, it) }
            ScreenerIndicatorId.BOLLINGER_PERCENT ->
                IndicatorsExt.bollingerPercent(close, length, BOLLINGER_MULTIPLIER).lastValue()
            else -> null
        }
        return reading?.takeIf(Double::isFinite)
    }

    /**
     * Every reading in [keys] that this build can produce for [bars].
     *
     * Keys are [ScreenerIndicatorId.normalisedKey] spellings, which is the form a filter asks in and
     * the form [com.coinepro.feature.screener.model.ScreenerRow.indicators] is keyed by. Parsing the
     * period back out of the key rather than taking it as a parameter keeps the round trip in one
     * place: a key that this function cannot take apart is a key nothing can answer, and it is
     * dropped rather than guessed at.
     */
    fun computeAll(keys: Set<String>, bars: List<OhlcBar>): Map<String, Double> = buildMap {
        keys.forEach { key ->
            val separator = key.lastIndexOf(':')
            val id = if (separator < 0) key else key.substring(0, separator)
            val period = if (separator < 0) null else key.substring(separator + 1).toIntOrNull()
            compute(id, period, bars)?.let { put(key, it) }
        }
    }

    /** The last bar at which the line has a value, or null when it is warm-up all the way through. */
    private fun Line.lastValue(): Double? {
        for (index in size - 1 downTo 0) {
            if (isPresent(index)) return raw(index)
        }
        return null
    }

    /** A value in the instrument's own units, as a percentage of its price. */
    private fun Double.asPercentOf(price: Double): Double? =
        if (price > 0.0) this / price * 100.0 else null

    /** How far the price sits above or below a level, signed, as a percentage of the level. */
    private fun distance(price: Double, level: Double): Double? =
        if (level > 0.0) (price - level) / level * 100.0 else null

    /**
     * The shortest series worth computing anything from.
     *
     * Below thirty bars every indicator here is either entirely warm-up or is answering from so few
     * samples that the number is noise. Refusing outright is better than publishing a reading a
     * filter would then rank a market by.
     */
    const val MIN_BARS: Int = 30

    /** The bounds a saved period is clamped into. The same range `core:chart`'s picker offers. */
    const val MIN_PERIOD: Int = 2
    const val MAX_PERIOD: Int = 400

    /** Used only for an indicator that declares no default of its own. */
    private const val DEFAULT_PERIOD: Int = 14

    /** Two standard deviations, which is what every terminal means by "the Bollinger band". */
    private const val BOLLINGER_MULTIPLIER: Double = 2.0
}
