package com.coinepro.feature.screener

import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.IndicatorOption
import com.coinepro.core.chart.IndicatorPane
import com.coinepro.feature.screener.model.ScreenerField
import com.coinepro.feature.screener.model.ScreenerUnit
import java.util.Locale

/**
 * Which of the chart's indicators a screener can honestly put a threshold on — [115].
 *
 * ### Why this file exists
 *
 * The screener used to offer eight indicators, written out by hand, while `ChartCatalog` carried
 * eighty-three. That is the wrong shape twice over: the eight were a copy of a list that already
 * existed, so a ninth added to the chart never reached the screener; and the whole claim of [109]
 * — that filtering a market list on *any* indicator is free here and sold as Premium elsewhere —
 * was quietly untrue for seventy-five of them.
 *
 * So the list is derived rather than written. Every row below comes from [ChartCatalog.INDICATORS]
 * and nothing here names an indicator that file does not.
 *
 * ### What is deliberately not offered, and why that matters more than what is
 *
 * A screener reduces an indicator to **one comparable number per market**. Three families of
 * indicator cannot be reduced that way, and the important thing is that each is *withheld with a
 * reason* rather than offered and silently answered with zero:
 *
 * * **Structure studies** — pivots, swings, fractals, zigzag, auto-Fibonacci, support and
 *   resistance, supply and demand, the chop zone. They draw levels and per-bar marks. There is no
 *   "the value of support" to compare against a number a reader types.
 * * **The correlation coefficient**, which is not a function of one market at all. Without a second
 *   series there is nothing to correlate against, and `ChartCatalog.paneFor` says so by returning an
 *   empty pane rather than a line of zeros.
 * * **The volume studies, on a feed that reports no volume.** This follows the `hasVolume`
 *   convention the chart already uses — see [ChartCatalog.VOLUME_ONLY_INDICATORS] — and the reason
 *   is written there at length: a money-flow index computed from a column of zeros is not "no money
 *   flowing", it is a reading of data nobody sent. On a screener the damage is worse than on a
 *   chart, because the fabricated number would not merely be drawn, it would *rank four hundred
 *   markets*.
 *
 * [withheld] is what the filter sheet prints under the picker. An absent indicator with a stated
 * reason is a product being honest; an absent indicator with no explanation is a product that looks
 * incomplete.
 *
 * ### The readings are not all in the same units, and that is the one trap here
 *
 * An RSI of thirty means the same thing on gold and on a satoshi-priced coin. An ATR of twelve does
 * not — it is enormous on a currency pair and negligible on Bitcoin — and a single threshold typed
 * into a filter has to mean one thing across a mixed catalogue or it means nothing. So [Reading]
 * carries how each indicator is turned into a comparable number, and the two families that are in
 * the instrument's own units are published as a percentage instead. See [ScreenerIndicators.compute],
 * which is the only place that arithmetic happens.
 */
object ScreenerIndicatorCatalog {

    /**
     * How one indicator becomes a single number a threshold can bite on.
     *
     * This is the judgement in this file. Everything else is a lookup.
     */
    enum class Reading(val unit: ScreenerUnit) {
        /**
         * The last value of the indicator's own line, unchanged.
         *
         * For everything already bounded or scale-free by construction — RSI, ADX, the stochastic
         * family, %B, the percentage oscillators. A threshold on one of these is portable across
         * the whole catalogue with no scaling at all, which is why they are the easy case.
         */
        RATIO(ScreenerUnit.PLAIN),

        /**
         * The last value as a percentage of the market's price.
         *
         * For the readings that come out in the instrument's own currency — the ATR, the standard
         * deviation, momentum, the Awesome and Accelerator oscillators, MACD, the detrended price
         * oscillator. Raw, an ATR filter would return every expensive instrument and no cheap one,
         * whatever number the reader typed, and it would look like it worked.
         */
        PRICE_PERCENT(ScreenerUnit.PERCENT),

        /**
         * How far the price sits above or below the indicator's line, signed, as a percentage.
         *
         * Every price-scale indicator reduces this way and only this way. «EMA ۲۰۰» is not a number
         * a reader compares markets on — it is a price, so on Bitcoin it is ninety thousand — but
         * *the distance from it* is exactly what a trend filter is asking about, and it is
         * comparable everywhere. For a band the line taken is the one the chart draws first, which
         * is the upper edge, so the reading answers "how far under the top of the band is it".
         */
        LEVEL_DISTANCE(ScreenerUnit.PERCENT),

        /**
         * The last value, in units of traded volume.
         *
         * On-balance volume, the accumulation/distribution line, the price-volume trend, Klinger,
         * the force index and net volume are cumulative or volume-weighted totals. They are honest
         * per market and are **not** comparable between markets — a filter on them is worth having
         * ("is this accumulating at all") and is deliberately formatted with the volume column's
         * abbreviations, so a reader sees `1.24B` and knows they are looking at a size rather than
         * at a score.
         */
        TURNOVER(ScreenerUnit.VOLUME),
    }

    /** Why an indicator the chart offers is not on the screener's list. */
    enum class Absence(val reason: String) {
        /** A study that draws levels or marks. There is no single number per market to compare. */
        NO_SINGLE_VALUE("این ابزار سطح و نشانه می‌کشد و برای هر بازار یک عدد یکتا نمی‌دهد"),

        /** Correlation. It measures two markets against each other; a screener has only one. */
        NEEDS_COMPARISON("برای محاسبه به نماد دوم نیاز دارد"),

        /** A volume study on a feed with no volume column. See the object's note. */
        NEEDS_VOLUME("به ستون حجم نیاز دارد و این خوراک حجمی گزارش نمی‌کند"),
    }

    /**
     * One indicator, as the screener offers it.
     *
     * [minPeriod] and [maxPeriod] are the chart's own bounds for this indicator, so a stepper here
     * cannot ask for a lookback the engine would refuse. [defaultPeriod] is null for the indicators
     * whose shape is a fixed set of periods rather than one lookback — MACD's 12/26/9, the Awesome
     * Oscillator's 5/34 — and the sheet shows those no period box at all rather than a box that
     * changes nothing. That is the same rule `ChartCatalog.PERIODS` states for the chart's picker.
     */
    data class Option(
        val id: String,
        val label: String,
        val reading: Reading,
        val defaultPeriod: Int?,
        val minPeriod: Int,
        val maxPeriod: Int,
    ) {
        /** What the cell under this indicator is written as. Follows [Reading]. */
        val unit: ScreenerUnit get() = reading.unit

        /** True where the sheet should show a lookback control for this indicator. */
        val takesPeriod: Boolean get() = defaultPeriod != null
    }

    /** One indicator the chart has and the screener will not offer, with the reason to print. */
    data class Withheld(val id: String, val label: String, val why: Absence)

    /**
     * Every indicator that can be reduced to one comparable number on this feed.
     *
     * @param hasVolume whether the feed reports a volume column at all. False on the MT5 forex
     *   side, where fourteen of the eighty-three are arithmetic on a column that was never sent.
     */
    fun offered(hasVolume: Boolean): List<Option> =
        ChartCatalog.INDICATORS.mapNotNull { option ->
            if (absenceOf(option, hasVolume) != null) null else optionOf(option)
        }

    /** Everything [offered] left out, in the catalogue's order, each with its reason. */
    fun withheld(hasVolume: Boolean): List<Withheld> =
        ChartCatalog.INDICATORS.mapNotNull { option ->
            absenceOf(option, hasVolume)?.let { Withheld(option.id, option.label, it) }
        }

    /**
     * The offered indicators whose Persian name or id contains [query].
     *
     * A plain substring, for the reason `ChartCatalog.matchingIndicators` gives: the reader is
     * filtering a list they can already see, and a fuzzy matcher that returns rows they did not ask
     * for reads as broken. Both halves match, so «rsi» and «قدرت» find the same row.
     */
    fun matching(query: String, hasVolume: Boolean): List<Option> {
        val needle = query.trim().lowercase(Locale.ROOT)
        val all = offered(hasVolume)
        if (needle.isEmpty()) return all
        return all.filter {
            needle in it.label.lowercase(Locale.ROOT) || needle in it.id.lowercase(Locale.ROOT)
        }
    }

    /**
     * One indicator by id, or null where this build cannot reduce it.
     *
     * Null for an id from a later build and null for a structure study alike, because the caller
     * can do nothing different about either: both mean "no reading, do not offer it". Volume is not
     * consulted here — a symbol whose own bars carry volume can answer a volume study even on a
     * mixed catalogue, and the feed-wide question is [offered]'s.
     */
    fun optionOf(id: String): Option? {
        val option = ChartCatalog.INDICATORS.firstOrNull { it.id == id } ?: return null
        if (option.pane == IndicatorPane.STRUCTURE || option.id == COMPARISON_ONLY) return null
        return optionOf(option)
    }

    /**
     * The Persian name of an indicator id, whatever its source.
     *
     * Falls back to the legacy [ScreenerField] labels and then to the raw id, because a saved
     * screen written by a later build can name an indicator this one has never heard of, and a
     * condition row that prints nothing is worse than one that prints a ticker the reader
     * half-recognises.
     */
    fun labelOf(id: String): String =
        ChartCatalog.INDICATORS.firstOrNull { it.id == id }?.label
            ?: ScreenerField.entries.firstOrNull { it.indicatorId == id }?.label
            ?: id

    private fun optionOf(option: IndicatorOption): Option {
        val period = ChartCatalog.periodOf(option.id)
        return Option(
            id = option.id,
            label = option.label,
            reading = readingFor(option),
            defaultPeriod = period?.default,
            minPeriod = period?.min ?: DEFAULT_MIN_PERIOD,
            maxPeriod = period?.max ?: DEFAULT_MAX_PERIOD,
        )
    }

    private fun readingFor(option: IndicatorOption): Reading = when {
        option.pane == IndicatorPane.PRICE -> Reading.LEVEL_DISTANCE
        option.id in PRICE_UNIT_OSCILLATORS -> Reading.PRICE_PERCENT
        option.id in VOLUME_UNIT_OSCILLATORS -> Reading.TURNOVER
        else -> Reading.RATIO
    }

    private fun absenceOf(option: IndicatorOption, hasVolume: Boolean): Absence? = when {
        option.pane == IndicatorPane.STRUCTURE -> Absence.NO_SINGLE_VALUE
        option.id == COMPARISON_ONLY -> Absence.NEEDS_COMPARISON
        !hasVolume && option.id in ChartCatalog.VOLUME_ONLY_INDICATORS -> Absence.NEEDS_VOLUME
        else -> null
    }

    /** The one indicator that measures two markets against each other. See [Absence.NEEDS_COMPARISON]. */
    private const val COMPARISON_ONLY = "correlation"

    /**
     * The own-pane readings that come out in the instrument's currency.
     *
     * Each is a difference of prices somewhere in its arithmetic — a true range, a deviation, a
     * gap between two averages — so each scales with the price and none of them can be compared
     * across a catalogue until it is divided by one. Everything not listed here is already a
     * ratio, a percentage or a bounded oscillator.
     */
    private val PRICE_UNIT_OSCILLATORS: Set<String> =
        setOf("atr", "stddev", "mom", "ao", "ac", "macd", "dpo")

    /** The own-pane readings measured in traded volume. See [Reading.TURNOVER]. */
    private val VOLUME_UNIT_OSCILLATORS: Set<String> =
        setOf("obv", "adline", "pvt", "klinger", "forceIndex", "netvolume")

    /** Used only for an indicator with no entry in `ChartCatalog.PERIODS`. The chart's own bounds. */
    private const val DEFAULT_MIN_PERIOD = 2
    private const val DEFAULT_MAX_PERIOD = 400
}
