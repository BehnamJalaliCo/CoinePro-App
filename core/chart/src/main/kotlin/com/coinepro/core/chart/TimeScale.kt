package com.coinepro.core.chart

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * The calendar boundary a time-axis label stands on.
 *
 * Declared coarsest last, and the ordinal is load-bearing: [TimeScale.ticks] fills the axis in
 * descending order of this enum, so a label that opens a year is placed before one that opens a
 * month, which is placed before one that merely opens an hour. That single rule is what makes the
 * axis stable under panning — the boundaries do not move, so the labels slide across the plot
 * instead of being renumbered.
 */
enum class TimeTickUnit {
    /** A new minute, and nothing coarser. Only ever reached on second-resolution feeds. */
    MINUTE,

    /** A new hour. What an intraday chart is labelled by once it is zoomed in past a session. */
    HOUR,

    /** A new day in the reader's own zone — the session boundary on any intraday chart. */
    DAY,

    /** A new ISO week, which is the boundary a daily chart is read by. */
    WEEK,

    /** A new month. Drawn bold, because it is what the eye navigates by. */
    MONTH,

    /** A new year. Drawn bold, and never dropped for want of room. */
    YEAR,
}

/**
 * One label on the time axis: which bar it sits on and what kind of boundary that bar opens.
 *
 * [unit] is null for the axis of a price-driven chart type — Renko, Kagi, point-and-figure — whose
 * bars carry synthetic timestamps and are numbered rather than dated. Those ticks are spread evenly,
 * because there is no calendar underneath them to anchor to and pretending otherwise would print
 * fabricated dates.
 */
data class TimeTick(val index: Int, val time: Long, val unit: TimeTickUnit?)

/**
 * Where the dates go.
 *
 * ### The defect this replaces
 *
 * The price axis was fixed a wave ago and the note on `priceTicks` says exactly why it mattered:
 * before it, the axis divided the visible range into five and printed whatever fell out —
 * `2571.34`, `2578.85`, `2586.36` — and "nothing about that is wrong, and everything about it reads
 * as a debug render". The *time* axis was never given the same treatment. It still divided the
 * visible **bar count** into five and printed whatever timestamp fell out, so:
 *
 *  * Every label was an arbitrary moment. On an hourly chart the axis read `03:00 14:00 01:00
 *    12:00 23:00` — five true times, none of them a time anybody would say out loud.
 *  * Panning renumbered the whole axis on every frame, because the fractions are measured from the
 *    first visible bar and the first visible bar moves. A reader dragging through history watched
 *    five dates churn rather than five dates slide, which is the single clearest way an axis can
 *    say "this was computed, not laid out".
 *  * The vertical gridlines stand on those same indices, so the columns did not line up with
 *    midnight, with a week, or with the start of a month — the only things a vertical gridline on a
 *    price chart is for.
 *
 * ### What replaces it
 *
 * The same idea the price ladder uses, in the units time actually has. Every visible bar is asked
 * what calendar boundary it opens against the bar before it, and the axis is then filled from the
 * coarsest of those downward, keeping a minimum gap so nothing collides. Years first, then months,
 * then weeks, days, hours, minutes. A label therefore always lands on a boundary a reader
 * recognises, and because a boundary belongs to the *bar* rather than to the view, panning slides
 * the labels instead of recomputing them.
 *
 * This is what every terminal does and it is why their axes read as calendars rather than as
 * samples.
 */
object TimeScale {

    /**
     * The coarsest calendar unit that changes between [previous] and [time], or null for neither.
     *
     * Coarsest wins, and only one is reported: a bar that opens a year also opens a month, a week,
     * a day and an hour, and reporting all five would let one moment take five slots on an axis
     * that has room for five.
     */
    fun boundaryOf(time: Long, previous: Long, zone: ZoneId): TimeTickUnit? {
        val offsetNow = zone.rules.getOffset(Instant.ofEpochSecond(time)).totalSeconds.toLong()
        val offsetBefore = zone.rules.getOffset(Instant.ofEpochSecond(previous)).totalSeconds.toLong()
        return boundaryOfLocal(time + offsetNow, previous + offsetBefore)
    }

    /**
     * The axis' labels for the bars [first]..[last] of [times].
     *
     * [minGapBars] is the collision rule, in bars, and comes from the caller because only the caller
     * knows how wide a label is and how many pixels a bar is worth. [maxTicks] caps the count for a
     * narrow phone.
     *
     * The bar *before* [first] is consulted where there is one, so the leftmost visible bar can
     * itself be a boundary — a reader who has panned so that a month starts at the left edge should
     * see that month named, and the old code, which measured from the first visible bar, could
     * never say so.
     *
     * Falls back to an even spread when the window carries no boundary at all: a chart with
     * synthetic timestamps, one bar, or a window inside a single minute. An axis with no labels is
     * worse than an axis with arbitrary ones — the reader loses the ability to place anything.
     */
    fun ticks(
        times: LongArray,
        first: Int,
        last: Int,
        zone: ZoneId,
        minGapBars: Int,
        maxTicks: Int,
        dated: Boolean = true,
    ): List<TimeTick> {
        if (times.isEmpty() || last < first || first < 0 || last >= times.size) return emptyList()
        if (maxTicks <= 0) return emptyList()
        if (!dated) return evenly(times, first, last, maxTicks)

        // One zone offset for the whole window wherever the window does not straddle a transition,
        // which is every window but two an instrument sees in a year. `ZoneRules.getOffset` walks a
        // sorted table, and doing it twice per bar inside a draw pass at six hundred bars a screen
        // is the one thing on this axis that could cost a frame. When the ends *do* disagree the
        // fast path is abandoned rather than fudged: an axis an hour out at a DST boundary is the
        // class of quiet wrongness this whole file exists to avoid.
        val startOffset = zone.rules.getOffset(Instant.ofEpochSecond(times[first])).totalSeconds.toLong()
        val endOffset = zone.rules.getOffset(Instant.ofEpochSecond(times[last])).totalSeconds.toLong()
        val fixed = if (startOffset == endOffset) startOffset else null

        val candidates = ArrayList<Candidate>(last - first + 1)
        for (index in maxOf(first, 1)..last) {
            val time = times[index]
            val previous = times[index - 1]
            val offset = fixed ?: zone.rules.getOffset(Instant.ofEpochSecond(time)).totalSeconds.toLong()
            val before = fixed ?: zone.rules.getOffset(Instant.ofEpochSecond(previous)).totalSeconds.toLong()
            val unit = boundaryOfLocal(time + offset, previous + before) ?: continue
            candidates += Candidate(TimeTick(index, time, unit), time + offset)
        }
        if (candidates.isEmpty()) return evenly(times, first, last, maxTicks)

        val usable = roundMinutesOnly(candidates, maxTicks)

        // Coarsest first, and within one unit oldest first so that a chart spanning three years
        // labels all three rather than whichever two the sort happened to reach.
        val ordered = usable
            .sortedWith(compareByDescending<Candidate> { it.tick.unit?.ordinal ?: -1 }.thenBy { it.tick.index })
            .map { it.tick }
        val gap = maxOf(1, minGapBars)
        val accepted = ArrayList<TimeTick>(maxTicks)
        for (tick in ordered) {
            if (accepted.size >= maxTicks) break
            if (accepted.any { abs(it.index - tick.index) < gap }) continue
            accepted += tick
        }
        return accepted.sortedBy { it.index }
    }

    /** A boundary and the local moment it stands on, which is what the minute rule below needs. */
    private data class Candidate(val tick: TimeTick, val local: Long)

    /**
     * Thin the minute boundaries down to round ones.
     *
     * Minutes are the one unit whose members are not all equally worth naming. Every other boundary
     * is inherently round — an hour, a day, a month — but on a one-minute chart *every bar* opens a
     * minute, so the ladder would fill up with `09:37`, `09:41`, `09:46`: true times that no trader
     * would ever say out loud, and exactly the arbitrary-label failure this whole file replaced on
     * the price axis.
     *
     * So the coarsest step that still leaves enough labels wins — half-hours if the window holds
     * enough of them, then quarters, then tens, then fives — and if none does, the minutes are left
     * alone rather than the axis being left empty. It is the 1-2-5 ladder idea in the base time
     * happens to be counted in.
     *
     * Untouched wherever the window's labelling is not going to come down to minutes anyway: a chart
     * whose coarsest boundary is an hour or better already has round labels and this would only ever
     * throw candidates away.
     */
    private fun roundMinutesOnly(candidates: List<Candidate>, maxTicks: Int): List<Candidate> {
        val minutes = candidates.filter { it.tick.unit == TimeTickUnit.MINUTE }
        if (minutes.size <= 1) return candidates
        val wanted = minOf(maxTicks, MIN_ROUND_MINUTES)
        val step = MINUTE_STEPS.firstOrNull { step ->
            minutes.count { minuteOf(it.local) % step == 0L } >= wanted
        } ?: return candidates
        return candidates.filter {
            it.tick.unit != TimeTickUnit.MINUTE || minuteOf(it.local) % step == 0L
        }
    }

    /** The minute past the hour a local moment falls on. */
    private fun minuteOf(local: Long): Long = Math.floorDiv(local, SECONDS_PER_MINUTE) % 60

    /** The round minutes, coarsest first. Anything finer than five is not a round time. */
    private val MINUTE_STEPS = longArrayOf(30, 15, 10, 5).toList()

    /** How many round minutes a step has to offer before it is worth thinning down to it. */
    private const val MIN_ROUND_MINUTES = 3

    /**
     * The old behaviour, kept for the two cases that genuinely have no calendar: a price-driven
     * chart type, and a window with no boundary in it.
     */
    private fun evenly(times: LongArray, first: Int, last: Int, maxTicks: Int): List<TimeTick> {
        val count = last - first + 1
        if (count <= 0) return emptyList()
        val steps = minOf(maxTicks, count)
        return (0 until steps)
            .map { step -> first + (count - 1) * step / maxOf(1, steps - 1) }
            .distinct()
            .map { TimeTick(it, times[it], null) }
    }

    /**
     * The boundary test itself, on local seconds — the epoch shifted into the reader's own zone.
     *
     * Integer division rather than a calendar object per bar, because that is what makes this
     * affordable inside a draw pass. Only a day boundary is expensive, and only then: a month and a
     * year are read off [LocalDate.ofEpochDay], which is arithmetic on the day number the division
     * already produced.
     */
    private fun boundaryOfLocal(local: Long, localBefore: Long): TimeTickUnit? {
        val day = Math.floorDiv(local, SECONDS_PER_DAY)
        val dayBefore = Math.floorDiv(localBefore, SECONDS_PER_DAY)
        if (day != dayBefore) {
            val date = LocalDate.ofEpochDay(day)
            val before = LocalDate.ofEpochDay(dayBefore)
            return when {
                date.year != before.year -> TimeTickUnit.YEAR
                date.monthValue != before.monthValue -> TimeTickUnit.MONTH
                // Epoch day zero is a Thursday, so shifting by three puts the week boundary on a
                // Monday, which is where ISO puts it and where every market calendar puts it.
                Math.floorDiv(day + WEEK_ANCHOR, DAYS_PER_WEEK) !=
                    Math.floorDiv(dayBefore + WEEK_ANCHOR, DAYS_PER_WEEK) -> TimeTickUnit.WEEK
                else -> TimeTickUnit.DAY
            }
        }
        if (Math.floorDiv(local, SECONDS_PER_HOUR) != Math.floorDiv(localBefore, SECONDS_PER_HOUR)) {
            return TimeTickUnit.HOUR
        }
        if (Math.floorDiv(local, SECONDS_PER_MINUTE) != Math.floorDiv(localBefore, SECONDS_PER_MINUTE)) {
            return TimeTickUnit.MINUTE
        }
        return null
    }

    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
    private const val SECONDS_PER_DAY = 86_400L
    private const val DAYS_PER_WEEK = 7L

    /** Epoch day 0 was a Thursday; three days on is the Monday the ISO week opens with. */
    private const val WEEK_ANCHOR = 3L
}

/**
 * Whether a tick is one the axis sets in bold.
 *
 * A month and a year, and nothing finer. It is one font weight and it does most of the wayfinding
 * on this axis: five labels reading «3 Mar  10 Mar  17 Mar  24 Mar  31 Mar» are five equal-looking
 * dates a reader has to actually read, and bolding the one that opens April lets the eye find the
 * boundary without reading anything at all.
 */
internal fun TimeTick.isBoundary(): Boolean =
    unit == TimeTickUnit.MONTH || unit == TimeTickUnit.YEAR
