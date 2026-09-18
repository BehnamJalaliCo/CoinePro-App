package com.coinepro.core.chart

/**
 * **Which bars are worth asking «چرا؟» about** (run Τ2, C1).
 *
 * ### The test, and why it is a ratio rather than a number
 *
 * A bar is notable when its range — high minus low — is more than [MULTIPLE] times the average
 * range of the [WINDOW] bars before it. A ratio, because «a big candle» has no absolute meaning
 * across the markets this app carries: two hundred dollars is a quiet hour on Bitcoin and an
 * impossible one on EURUSD, and a threshold in price would mark every bar on one and none on the
 * other. What a reader notices is a bar that is big *for this chart, lately*, and that is exactly
 * what a trailing average is.
 *
 * The window is the **preceding** bars and never includes the bar being judged. A bar that is part
 * of its own baseline raises the bar it has to clear, so the biggest candles — the ones the feature
 * exists for — are the ones most likely to hide themselves.
 *
 * ### What it refuses to mark
 *
 * * The first [WINDOW] bars of a series, which have no baseline behind them. Marking them on a
 *   short average would put a dot on the oldest bars of every chart, which reads as a bug.
 * * A window whose average range is zero — a market that did not move at all. Every bar is then
 *   infinitely more than the average, and the honest answer is that there is nothing to compare.
 * * Anything with a non-finite high or low. A feed gap is not an event.
 *
 * ### Pure, and cheap enough to run on every window change
 *
 * One pass with a rolling sum: the whole series costs `O(n)` and no allocation beyond the result.
 * The caller re-runs it when the bars change, not when the viewport moves — the answer is a
 * property of the series, not of what is on screen.
 */
object NotableBars {

    /** How many bars behind a candle its baseline is taken over. */
    const val WINDOW = 20

    /** How many times the baseline a range must exceed. The brief's number. */
    const val MULTIPLE = 2.0

    /**
     * The indices of [series] whose range clears the test, in ascending order.
     *
     * Empty for a series shorter than [WINDOW] + 1, which is the honest answer rather than a
     * shorter average: a baseline of three bars is not a baseline.
     */
    fun of(series: CandleSeries): List<Int> {
        val size = series.size
        if (size <= WINDOW) return emptyList()
        val high = series.high
        val low = series.low

        // The ranges, once. `high[i] - low[i]` is read twice — for the bar's own test and for the
        // window it later rolls out of — and a series of several thousand bars is re-tested on
        // every load, so the subtraction is done once and kept.
        val ranges = DoubleArray(size) { index ->
            val h = high[index]
            val l = low[index]
            if (h.isFinite() && l.isFinite() && h >= l) h - l else Double.NaN
        }

        val notable = mutableListOf<Int>()
        var sum = 0.0
        var counted = 0
        for (index in 0 until WINDOW) {
            val range = ranges[index]
            if (range.isFinite()) {
                sum += range
                counted++
            }
        }
        for (index in WINDOW until size) {
            val range = ranges[index]
            // `counted` rather than WINDOW: a window with gaps in it averages over the bars that
            // actually traded. Dividing by twenty when four of them were blank would understate the
            // baseline and mark ordinary bars.
            if (range.isFinite() && counted > 0) {
                val average = sum / counted
                if (average > 0.0 && range > MULTIPLE * average) notable += index
            }
            // Roll the window forward: the bar just judged comes in, the one WINDOW back goes out.
            if (range.isFinite()) {
                sum += range
                counted++
            }
            val leaving = ranges[index - WINDOW]
            if (leaving.isFinite()) {
                sum -= leaving
                counted--
            }
        }
        return notable
    }

    /**
     * Whether one bar clears the test, for a caller that has an index and no list.
     *
     * Written in terms of [of] rather than beside it, because two implementations of one rule drift
     * and the drift would be invisible: the dot would appear on a bar whose sheet said there was
     * nothing unusual about it.
     */
    fun covers(series: CandleSeries, index: Int): Boolean = index in of(series)
}
