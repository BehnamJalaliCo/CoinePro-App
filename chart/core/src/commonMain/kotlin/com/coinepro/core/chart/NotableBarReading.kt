package com.coinepro.core.chart

/**
 * What the sheet behind a notable-bar dot says — run Τ2, C1.
 *
 * ### Why this exists rather than the sheet assembling it
 *
 * Because the one thing this feature cannot do is contradict the dot. A mark on a bar whose sheet
 * says there was nothing unusual about it is worse than no mark at all, and the only way to be sure
 * is for both to read the same arithmetic: [NotableBars.of] places the dot and
 * [NotableBars.ratioAt] supplies the figure, and neither is re-derived here.
 *
 * ### What it does not claim
 *
 * **Not causation.** The name of the feature is «چرا این حرکت؟» and the honest answer is almost
 * never certain, so what the reading carries is *what was on the calendar and on the wire inside
 * that bar's own window* — and the sheet presents it as that rather than as a reason. Where the
 * window holds nothing, that is the answer: the bar was large and nothing this app knows about
 * explains it, which is a real and common state of a market and is said plainly instead of being
 * filled with the nearest headline.
 */
data class NotableBarReading(
    /** The bar this is about, as an index into the series the caller passed. */
    val index: Int,
    /** The bar's open time, in the series' own units. The window starts here. */
    val fromSeconds: Long,
    /**
     * The end of the bar's own window, exclusive.
     *
     * The next bar's open, or — for the last bar — one interval past its own, measured from the
     * gap before it. Half-open, the same rule `ChartEvents.barOf` places events with, so an event
     * landing exactly on the next bar's open belongs to that bar and not to this one.
     */
    val toSeconds: Long,
    /** The bar's range as a multiple of its own baseline, or null where there is none. */
    val ratio: Double?,
    /** Open to close, as a percentage. Null where the open is not a usable base. */
    val movePercent: Double?,
    /** Whether the bar closed at or above its open, the way every terminal colours a doji. */
    val up: Boolean,
    /**
     * What the app knew about, inside that window, in the order it happened.
     *
     * Unfiltered by the reader's own event switches on purpose: the switches decide what clutters
     * the axis, and this is a question the reader asked about one bar. Hiding a rate decision here
     * because they had turned the calendar's glyphs off would be answering «why did this move» with
     * a filtered truth.
     */
    val events: List<ChartEvent> = emptyList(),
) {
    /** Whether anything at all was on the calendar or the wire inside the bar. */
    val explained: Boolean get() = events.isNotEmpty()
}

/** Builds the reading. Pure, and the only place the bar's own window is worked out. */
object NotableBarReadings {

    /**
     * The reading for one bar, or null for an index the series does not hold.
     *
     * [events] is everything the chart is carrying; only the ones inside this bar's half-open
     * window survive. `ChartEvent.at` and `Candle.t` are **both unix seconds** — its own KDoc is
     * emphatic that a feed reporting milliseconds converts at its edge and not here — so there is
     * no conversion in this file, and the absence is deliberate rather than an omission.
     */
    fun of(series: CandleSeries, index: Int, events: List<ChartEvent> = emptyList()): NotableBarReading? {
        if (index < 0 || index >= series.size) return null
        val times = series.time
        val from = times[index]
        val to = if (index + 1 < series.size) {
            times[index + 1]
        } else {
            // The last bar is as wide as the gap before it, which is the timeframe the series is
            // on. One second for a single-bar series, so the window stays non-empty rather than
            // becoming a moment nothing can fall inside.
            from + if (series.size >= 2) (times[index] - times[index - 1]).coerceAtLeast(1L) else 1L
        }
        val open = series.open[index]
        val close = series.close[index]
        return NotableBarReading(
            index = index,
            fromSeconds = from,
            toSeconds = to,
            ratio = NotableBars.ratioAt(series, index),
            movePercent = if (open.isFinite() && close.isFinite() && open != 0.0) {
                (close - open) / open * 100.0
            } else {
                null
            },
            up = close >= open,
            events = events
                .filter { event -> event.at >= from && event.at < to }
                .sortedBy(ChartEvent::at),
        )
    }
}
