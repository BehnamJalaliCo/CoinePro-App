package com.coinepro.feature.screener

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.IndicatorPane
import com.coinepro.core.chart.Indicators
import com.coinepro.core.chart.IndicatorsExt
import com.coinepro.core.chart.Line
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.feature.screener.model.ScreenerIndicatorId

/**
 * One indicator, reduced to the single number a screener can put a threshold on — [109], [115].
 *
 * ### Why a screener needs its own reading of an indicator
 *
 * A chart wants a whole series: every bar's RSI, so it can be drawn. A screener wants the *last*
 * one, for a thousand markets, so it can be compared. Those are different jobs with different costs
 * — the chart's answer is one symbol and several hundred values, the screener's is one value and
 * several hundred symbols — and the difference is why the reduction happens here rather than in
 * `core:chart`. What does not differ, and must not, is the arithmetic: every reading below comes
 * out of `core:chart`'s own catalogue unchanged, so the RSI this screen filters on is the number the
 * chart would have drawn for the same market. A screener that disagreed with its own chart would be
 * worse than no screener.
 *
 * ### The catalogue is asked, not copied
 *
 * This used to hold a `when` over eight hard-coded ids while `ChartCatalog` carried eighty-three,
 * which meant seventy-five indicators the app can compute could not be filtered on and nobody could
 * see why. Now anything [ScreenerIndicatorCatalog] offers is answered here, through the very
 * builders the chart draws with — `ChartCatalog.overlayFor` for a price-scale indicator and
 * `ChartCatalog.paneFor` for an own-pane one — and the *primary* line of what comes back is the
 * reading. Primary means the first line the pane declares, falling back to its histogram for the
 * four indicators that are drawn only as columns; that is the line a reader watches, and the order
 * in the catalogue is already that order.
 *
 * ### Some readings are normalised and some are not
 *
 * [ScreenerIndicatorCatalog.Reading] decides, and it is the whole of the judgement — see its own
 * documentation. In one sentence: a bounded oscillator is comparable across a mixed catalogue as it
 * stands, a reading in the instrument's currency is not and is published as a percentage of price,
 * and a price-scale indicator is published as the signed distance from it. An ATR of twelve is
 * enormous on a currency pair and negligible on Bitcoin, and a single number typed into a filter
 * has to mean one thing on both.
 *
 * ### Warm-up is a null, never a substitute
 *
 * A fifty-bar average genuinely does not exist on a market with forty bars of history, and every
 * answer other than null is a lie a filter would act on. `core:chart`'s `Line` already carries that
 * distinction; this preserves it rather than flattening it to zero. The same rule covers a volume
 * study on a feed that reports no volume: `ChartCatalog` returns nothing at all there, and nothing
 * is what this hands back — never the zero that would rank four hundred markets as perfectly
 * balanced.
 */
object ScreenerIndicators {

    /**
     * The last reading of [indicatorId] over [bars], or null when it cannot be computed.
     *
     * Null covers four situations on purpose, because a caller can do nothing different about any
     * of them: an id this build does not know, a series too short to warm the indicator up, a
     * reading that came out non-finite because the market's own numbers divided badly, and an
     * indicator the feed cannot answer — a volume study on bars with no volume column.
     *
     * @param period the lookback, or null for the indicator's own default. Clamped into the
     *   indicator's own bounds rather than refused, because the bound comes from a saved screen
     *   that a later build may have written with a wider range, and dropping a reader's filter is a
     *   worse answer than answering it with the nearest period this build supports.
     */
    fun compute(indicatorId: String, period: Int?, bars: List<OhlcBar>): Double? {
        if (bars.size < MIN_BARS) return null
        return reading(indicatorId, period, seriesOf(bars))
    }

    /**
     * Every reading in [keys] that this build can produce for [bars].
     *
     * Keys are [ScreenerIndicatorId.normalisedKey] spellings, which is the form a filter asks in and
     * the form [com.coinepro.feature.screener.model.ScreenerRow.indicators] is keyed by. Parsing the
     * period back out of the key rather than taking it as a parameter keeps the round trip in one
     * place: a key that this function cannot take apart is a key nothing can answer, and it is
     * dropped rather than guessed at.
     *
     * The series is built once for the whole set. That is not a micro-optimisation: a
     * [CandleSeries] extracts six parallel arrays from the bar list, and building it per key would
     * repeat that work for every condition on the screen, for every market in the scan.
     */
    fun computeAll(keys: Set<String>, bars: List<OhlcBar>): Map<String, Double> {
        if (keys.isEmpty() || bars.size < MIN_BARS) return emptyMap()
        val series = seriesOf(bars)
        return buildMap {
            keys.forEach { key ->
                val separator = key.lastIndexOf(':')
                val id = if (separator < 0) key else key.substring(0, separator)
                val period = if (separator < 0) null else key.substring(separator + 1).toIntOrNull()
                reading(id, period, series)?.let { put(key, it) }
            }
        }
    }

    /**
     * The bars as the chart's own series type.
     *
     * The volume translation is the line that matters. `OhlcBar.v` is a non-null zero on the MT5
     * feed, which reports no volume at all, while `Candle.v` is nullable precisely so that "no
     * volume column" and "a bar in which nothing traded" stay different facts. Passing the zero
     * through would make [CandleSeries.hasVolume] true on the whole forex catalogue and every
     * volume study would answer with arithmetic on zeros — which is the exact failure
     * `ChartCatalog.VOLUME_ONLY_INDICATORS` exists to prevent.
     */
    private fun seriesOf(bars: List<OhlcBar>): CandleSeries = CandleSeries(
        bars.map { bar -> Candle(bar.t, bar.o, bar.h, bar.l, bar.c, bar.v.takeIf { it > 0.0 }) },
    )

    /**
     * One reading over a series that has already been built.
     *
     * The legacy ids are answered first and directly. They are the eight this feature shipped with
     * — they are written into saved screens, into [ScreenerIndicatorId] and into the presets — and
     * three of them (`stoch_k`, `macd_hist`, `bb_percent`) are not spellings the chart catalogue
     * knows at all. Routing them through the catalogue would change the number under a reader's
     * existing filter, which is the one thing a stored condition must never do.
     */
    private fun reading(indicatorId: String, period: Int?, series: CandleSeries): Double? {
        if (series.size < MIN_BARS) return null
        val close = series.close
        val high = series.high
        val low = series.low
        val last = close.last()
        val legacyLength = (period ?: ScreenerIndicatorId.defaultPeriodOf(indicatorId) ?: DEFAULT_PERIOD)
            .coerceIn(MIN_PERIOD, MAX_PERIOD)

        val value = when (indicatorId) {
            ScreenerIndicatorId.STOCHASTIC_K ->
                Indicators.stochastic(high, low, close, legacyLength).k.lastValue()
            ScreenerIndicatorId.MACD_HISTOGRAM -> Indicators.macd(close).histogram.lastValue()
            ScreenerIndicatorId.ATR_PERCENT ->
                Indicators.atr(high, low, close, legacyLength).lastValue()?.asPercentOf(last)
            ScreenerIndicatorId.SMA_DISTANCE ->
                Indicators.sma(close, legacyLength).lastValue()?.let { distance(last, it) }
            ScreenerIndicatorId.EMA_DISTANCE ->
                Indicators.ema(close, legacyLength).lastValue()?.let { distance(last, it) }
            ScreenerIndicatorId.BOLLINGER_PERCENT ->
                IndicatorsExt.bollingerPercent(close, legacyLength, BOLLINGER_MULTIPLIER).lastValue()
            else -> catalogueReading(indicatorId, period, series)
        }
        return value?.takeIf(Double::isFinite)
    }

    /**
     * Any of the eighty-three, through the builders the chart itself draws with.
     *
     * The period is clamped into this indicator's own bounds rather than the module's, because they
     * are not the same range for every indicator — the correlation coefficient starts at five, not
     * at two — and `ChartCatalog` clamps again on the way in, so the two agree by construction.
     */
    private fun catalogueReading(indicatorId: String, period: Int?, series: CandleSeries): Double? {
        val offered = ScreenerIndicatorCatalog.optionOf(indicatorId) ?: return null
        val option = ChartCatalog.INDICATORS.firstOrNull { it.id == indicatorId } ?: return null
        val length = period?.coerceIn(offered.minPeriod, offered.maxPeriod)
        val line = when (option.pane) {
            IndicatorPane.PRICE -> ChartCatalog.overlayFor(option, series, length).firstOrNull()
            // The pane's first line, or its histogram for the four that are drawn only as columns
            // — the Accelerator, Balance of Power, the Chaikin oscillator and the Awesome
            // oscillator. `paneFor` returns null on a volume study with no volume column, and that
            // null is carried through rather than turned into a zero.
            IndicatorPane.SEPARATE -> ChartCatalog.paneFor(option, series, length)
                ?.let { pane -> pane.lines.firstOrNull() ?: pane.histogram }
            // A structure study has no value per bar. It is not offered, and this is the second
            // place that says so rather than the first place that forgets.
            IndicatorPane.STRUCTURE -> null
        } ?: return null

        val value = line.values.lastValue() ?: return null
        val price = series.close.last()
        return when (offered.reading) {
            ScreenerIndicatorCatalog.Reading.RATIO -> value
            ScreenerIndicatorCatalog.Reading.TURNOVER -> value
            ScreenerIndicatorCatalog.Reading.PRICE_PERCENT -> value.asPercentOf(price)
            ScreenerIndicatorCatalog.Reading.LEVEL_DISTANCE -> distance(price, value)
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
