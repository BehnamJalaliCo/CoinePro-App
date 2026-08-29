package com.coinepro.feature.chart

import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.Timeframe

/**
 * How much history the reader wants in front of them, in the words they use for it.
 *
 * ### What this is, and what it deliberately is not
 *
 * On a desktop terminal a range button leaves the bar length alone and moves the *viewport*: five
 * years of hourly candles is a legitimate thing to be zoomed out over. That is not available here
 * and asking for it would be the wrong answer anyway. `CoineProChart` owns its own viewport — it
 * has to, because the viewport is measured against a canvas nobody outside the composable has —
 * and five years of hourly bars is forty-three thousand candles, which is neither fetchable in one
 * page nor legible on a phone if it were.
 *
 * So a range here chooses **the bar length that puts that much history on one screen**. That is
 * what somebody tapping «۱ سال» on a phone means: not "keep my five-minute bars and zoom out
 * until they are a smear", but "show me the year". It is also what every mobile broker app that
 * ships this control actually does.
 *
 * ### Why the timeframes are what they are
 *
 * One page is [CandleGateway.DEFAULT_LIMIT] bars. Each range picks the coarsest length that still
 * gives the chart a few hundred bars to draw, because the failure at both ends is visible: a
 * length too fine truncates the range to the first page and the reader is shown a month when they
 * asked for a year, and one too coarse draws fifty-two candles across a phone and calls it a
 * chart. [barsAcross] is that arithmetic, and `ChartRangeTest` holds every entry inside the band
 * rather than pinning the table, so a range added later has to earn its length the same way.
 *
 * [ALL] is the exception and is honest about it: nobody knows how long an instrument's history is
 * before asking for it, so it takes the longest bar the feed serves and lets the page cap decide.
 */
enum class ChartRange(
    /** What the pill says. Persian digits, because this is a prose duration and not a price. */
    val label: String,
    /** Roughly how long the range is, in seconds. Nominal — see [Timeframe.MN1]. */
    val seconds: Long,
    /** The bar length this range is drawn at. */
    val timeframe: Timeframe,
) {
    ALL("همه", 0L, Timeframe.MN1),
    Y5("۵ سال", 5 * YEAR_SECONDS, Timeframe.W1),
    Y1("۱ سال", YEAR_SECONDS, Timeframe.D1),
    M6("۶ ماه", 182 * DAY_SECONDS, Timeframe.D1),
    M3("۳ ماه", 91 * DAY_SECONDS, Timeframe.D1),
    M1("۱ ماه", 31 * DAY_SECONDS, Timeframe.H4),
    D5("۵ روز", 5 * DAY_SECONDS, Timeframe.M30),
    D1("۱ روز", DAY_SECONDS, Timeframe.M5),
    ;

    /** The interval a tap on this pill puts the chart on. */
    val interval: ChartInterval get() = ChartInterval.Preset(timeframe)

    /**
     * How many bars of [timeframe] this range spans.
     *
     * Zero for [ALL], which has no span to divide. The number is what the choice of length is
     * argued from, so it is a property rather than a comment: a range that would draw eleven bars
     * or eleven thousand is a range whose length is wrong, and the test says so in those terms.
     */
    val barsAcross: Int
        get() = if (seconds <= 0L) 0 else (seconds / timeframe.seconds).toInt()

    companion object {
        /**
         * The pills, longest first.
         *
         * Longest first because the row is read right-to-left in a Persian layout and «همه» is the
         * outermost, widest claim — the same order the web terminal uses and the order the label
         * text itself implies. Reversing it would put «۱ روز» under the thumb and «همه» off the
         * far edge, which is backwards: the short ranges are the ones somebody taps repeatedly.
         */
        val OFFERED: List<ChartRange> = listOf(ALL, Y5, Y1, M6, M3, M1, D5, D1)

        /**
         * The most bars a range may ask a single page for before it is lying about its length.
         *
         * The page cap is [CandleGateway.DEFAULT_LIMIT]; a range that wants meaningfully more than
         * that gets truncated to it, and the reader who asked for a year is shown ten months with
         * nothing saying so. A small overshoot is fine — the chart pages back on a pan — so the
         * bound is generous rather than exact.
         */
        const val MAX_BARS = 400

        /**
         * The fewest bars a range may draw before it stops looking like a chart.
         *
         * Sixty is about two hundred pixels' worth of candle at phone width. Below it a reader
         * cannot see structure, which is the entire reason they pressed a range button.
         */
        const val MIN_BARS = 60
    }
}

/** A year, nominally. Three hundred and sixty-five days; leap years cost this a day and nothing else. */
private const val YEAR_SECONDS = 365L * 86_400L

private const val DAY_SECONDS = 86_400L
