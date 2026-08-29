package com.coinepro.core.chart

/**
 * What sort of thing happened, which is also what shape the mark on the axis takes.
 *
 * Five kinds rather than one, because a reader scanning a chart wants to tell "the central bank
 * spoke" from "the stock went ex-dividend" without opening anything, and the two have entirely
 * different consequences for the candle beside them. The Persian [label] is what a mark's sheet
 * heads itself with; it lives here so the model and the screen cannot drift into two vocabularies.
 */
enum class EventKind(val label: String) {
    NEWS("خبر"),
    ECONOMIC("اقتصادی"),
    EARNINGS("گزارش درآمد"),
    DIVIDEND("سود نقدی"),
    SPLIT("تجزیهٔ سهم"),
}

/**
 * How much an event is expected to move the price.
 *
 * Three levels, matching what every economic calendar publishes, because the axis cannot show
 * every event at full weight: a chart with a mark under every bar is a chart with a second time
 * axis made of noise. The renderer draws high importance loudest and low importance quietly, and a
 * reader who only wants the loud ones filters on this.
 */
enum class Importance(val label: String) {
    LOW("کم"),
    MEDIUM("متوسط"),
    HIGH("زیاد"),
}

/**
 * One thing that happened at one moment.
 *
 * [at] is a unix time in **seconds**, the same convention [Candle.t] uses and the same one both
 * feeds send. That is not a detail to be flexible about: a value in milliseconds lands a thousand
 * times further into the future than any bar in the series and is silently dropped as out of
 * range, so a feed that reports milliseconds converts once, at its own edge, rather than here.
 *
 * [detail] is the sentence behind the headline — the previous figure, the forecast, the actual —
 * and is null when the source gave only a title. [title] is never null, because a mark a reader
 * opens to find nothing written is worse than a mark that was never drawn.
 */
data class ChartEvent(
    val at: Long,
    val kind: EventKind,
    val title: String,
    val detail: String?,
    val importance: Importance,
)

/**
 * One glyph on the time axis, and everything it stands for.
 *
 * [events] is deliberately a list rather than a single event: see [ChartEvents.place] for why
 * several events in one bar collapse into one mark. [kind] and [importance] are the mark's own —
 * the strongest of what it carries — because they are what the renderer draws before anybody has
 * tapped it.
 */
data class EventMark(
    val barIndex: Int,
    /** Everything in this bar, earliest first. Never empty. */
    val events: List<ChartEvent>,
    val kind: EventKind,
    val importance: Importance,
) {
    /** Whether this mark stands for more than one event, and so opens a list rather than a card. */
    val isCluster: Boolean get() = events.size > 1
}

/**
 * Where events sit on the time axis.
 *
 * ### One mark per bar, never one per event
 *
 * Events do not arrive on bar boundaries; they arrive at whatever second they happened. Ten
 * releases inside one four-hour bar is an ordinary Friday morning, and drawing ten glyphs at ten
 * sub-bar positions produces a smear a few pixels wide that no reader can tap, read or count. So
 * every event is bucketed into the bar whose interval contains it and everything in one bar
 * collapses into a **single** [EventMark] carrying all of them, taking the highest importance
 * present and the kind that goes with it. That mark opens a list of ten, which is a feature; ten
 * overlapping glyphs is a defect.
 *
 * ### A bar's interval is half open
 *
 * Bar *i* covers from its own open time up to, but not including, the next bar's — which is what
 * makes bucketing total and unambiguous, with no event landing on two bars or between them. The
 * last bar has no successor, so it is given the same width as the gap before it; an event past the
 * end of that is in the future the series has not reached and is dropped.
 *
 * ### Out of range is dropped, not clamped
 *
 * An event before the first visible bar or after the last is left out entirely. Clamping it to the
 * edge would draw a marker claiming something happened at a time it did not, and a reader who
 * scrolls to that edge to read the news finds a bar that has nothing to do with it. The visible
 * window itself is clamped to the series — a chart panned past its own data asks for indices that
 * do not exist — but that clamps the *window*, never an event's position.
 *
 * Pure Kotlin: no Compose, no networking. Another layer draws these and another fetches them.
 */
object ChartEvents {

    /**
     * The marks for one visible range, ordered by bar.
     *
     * [fromIndex] and [toIndex] are the first and last visible bars, inclusive, in either order —
     * a caller that hands them over reversed gets the same window rather than nothing.
     */
    fun place(
        events: List<ChartEvent>,
        series: CandleSeries,
        fromIndex: Int,
        toIndex: Int,
    ): List<EventMark> {
        if (events.isEmpty() || series.isEmpty) return emptyList()
        val last = series.size - 1
        val from = minOf(fromIndex, toIndex).coerceIn(0, last)
        val to = maxOf(fromIndex, toIndex).coerceIn(0, last)
        val buckets = LinkedHashMap<Int, MutableList<ChartEvent>>()
        for (event in events) {
            val index = barOf(series, event.at) ?: continue
            if (index < from || index > to) continue
            buckets.getOrPut(index) { mutableListOf() }.add(event)
        }
        return buckets.entries
            .sortedBy { it.key }
            .map { (index, inBar) -> markOf(index, inBar) }
    }

    /**
     * Which bar contains a moment, or null when no bar does.
     *
     * Null for anything before the series opens and for anything past the end of the last bar's
     * own interval. Public because the same question is asked outside placement — a reader tapping
     * a mark's sheet wants the bar it belongs to — and because a second implementation of the
     * half-open rule would be a second chance to get it wrong.
     */
    fun barOf(series: CandleSeries, at: Long): Int? {
        if (series.isEmpty) return null
        val times = series.time
        if (at < times[0]) return null
        var low = 0
        var high = times.size - 1
        var found = 0
        // The last bar whose open time is at or before the moment. Binary search rather than a
        // walk, because this runs once per event against a series that is thousands of bars long.
        while (low <= high) {
            val mid = (low + high) / 2
            if (times[mid] <= at) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (found == times.size - 1 && at >= times[found] + lastWidth(times)) return null
        return found
    }

    /**
     * How wide to treat the final bar as being.
     *
     * The gap before it, because that is the timeframe the series is on and the bar is a bar of the
     * same length. One second for a series with a single bar or a degenerate gap, which keeps the
     * interval non-empty so an event landing exactly on that bar's open still belongs to it rather
     * than to nothing.
     */
    private fun lastWidth(times: LongArray): Long {
        if (times.size < 2) return 1L
        return (times[times.size - 1] - times[times.size - 2]).coerceAtLeast(1L)
    }

    /**
     * One bar's events as a single mark.
     *
     * The mark takes the highest importance in the bar and the kind of the event that carries it,
     * earliest first among ties: a bar holding one rate decision and four minor releases must draw
     * as a rate decision, or the loudest thing on the chart is hidden behind the quietest.
     */
    private fun markOf(barIndex: Int, inBar: List<ChartEvent>): EventMark {
        val ordered = inBar.sortedBy(ChartEvent::at)
        val strongest = ordered.maxByOrNull { it.importance.ordinal } ?: ordered.first()
        return EventMark(
            barIndex = barIndex,
            events = ordered,
            kind = strongest.kind,
            importance = strongest.importance,
        )
    }
}
