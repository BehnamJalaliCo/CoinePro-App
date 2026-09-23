package com.coinepro.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * One trade the reader closed, as the week counts it.
 *
 * Deliberately three fields and not a journal entry: this summariser is in `core:common` and must
 * not learn what a journal row is, or what an alert is, or what a replay session is. Each caller
 * maps its own rows at its own edge, which is also what keeps the week's arithmetic testable
 * without a database.
 */
data class WeekTrade(
    /** When it closed, in milliseconds. The week is decided by this and nothing else. */
    val closedAtEpochMillis: Long,
    /** Whether it made money. A break-even trade is not a win — see [MyWeek]. */
    val won: Boolean,
)

/** What the reader did with the week, and what the app declines to say about it. */
data class WeekSummary(
    /** The week's own boundaries, in milliseconds, half-open. */
    val fromEpochMillis: Long,
    val toEpochMillis: Long,
    /** Trades closed inside the week. */
    val trades: Int = 0,
    val won: Int = 0,
    /**
     * The win rate as a percentage, or **null where there were too few trades to have one**.
     *
     * Null is the feature. See [MyWeek.MINIMUM_TRADES].
     */
    val winPercent: Double? = null,
    /** Alerts that fired inside the week, and how many are still waiting. */
    val alertsFired: Int = 0,
    val alertsArmed: Int = 0,
    /** Replay sessions finished inside the week. */
    val practiceSessions: Int = 0,
    /** The market on the reader's list that moved most, and by how much. Null on a quiet week. */
    val moverSymbol: String? = null,
    val moverPercent: Double? = null,
) {
    /**
     * Whether anything at all happened.
     *
     * A week with nothing in it is shown as a sentence rather than as four zeros: a screen of
     * zeros reads as a broken feature, and «you did not trade this week» is both true and the more
     * useful thing to say to somebody who is building a habit.
     */
    val empty: Boolean
        get() = trades == 0 && alertsFired == 0 && practiceSessions == 0 && moverSymbol == null
}

/**
 * **هفته‌ی من** — what the reader actually did, over their own week.
 *
 * ### The rule this screen exists to keep
 *
 * **A win rate over three trades is not a win rate.** Every app in this market prints one anyway,
 * and the reader who went two-for-three reads «۶۷٪» and believes something about themselves that
 * three coin flips would produce one time in three. So below [MINIMUM_TRADES] the percentage is
 * **withheld** — not rounded, not hedged, not shown in grey — and the count is shown instead. The
 * screen then says what it knows: «۲ از ۳». That is not a smaller claim, it is the claim that is
 * true.
 *
 * Above the floor the figure is printed plainly, because at that point it is worth something and
 * dressing it in caveats would be the opposite failure.
 *
 * ### The week is the reader's, not the calendar's
 *
 * It opens on [WeekStart.of]'s day in the reader's own zone — Saturday in Iran and across the Gulf
 * — and runs to the same instant seven days later. A summary that quietly used an ISO Monday would
 * put Thursday and Friday, the two days an Iranian reader is most likely to be reviewing *from*, in
 * the middle of the week being reviewed.
 *
 * ### Everything here is counted, nothing is inferred
 *
 * There is no score, no grade and no streak. Each number is a thing the reader did that the app
 * already recorded, and a week is too short a sample for anything else to be honest at.
 */
object MyWeek {

    /**
     * How many closed trades a win rate needs before it is printed.
     *
     * Five. Small enough that an active reader has one most weeks; large enough that a single
     * outcome cannot move the figure by fifty points, which is what makes a three-trade percentage
     * a lie told in a confident font.
     */
    const val MINIMUM_TRADES = 5

    /** Below this, a market did not move enough to be the week's mover. Same floor as the brief. */
    const val QUIET_PERCENT = 0.1

    /** The instant the reader's current week opened, in milliseconds. */
    fun weekStart(nowEpochMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowEpochMillis)
            .atZone(zone)
            .toLocalDate()
            .with(TemporalAdjusters.previousOrSame(WeekStart.of(zone)))
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()

    /** The same, as the reader's own date — for a caption that names the week. */
    fun weekStartDate(nowEpochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(nowEpochMillis)
            .atZone(zone)
            .toLocalDate()
            .with(TemporalAdjusters.previousOrSame(WeekStart.of(zone)))

    /**
     * The week, summarised.
     *
     * Every list is filtered here rather than by the caller, so «inside the week» means one thing
     * and is tested in one place. [alertsArmed] and [marketChanges] are states rather than events
     * and are taken as they stand — how many alerts are waiting *now*, and how the reader's markets
     * moved over the period the caller measured.
     */
    fun of(
        nowEpochMillis: Long,
        zone: ZoneId,
        trades: List<WeekTrade> = emptyList(),
        alertFiredAt: List<Long> = emptyList(),
        alertsArmed: Int = 0,
        practiceFinishedAt: List<Long> = emptyList(),
        marketChanges: List<Pair<String, Double?>> = emptyList(),
    ): WeekSummary {
        val from = weekStart(nowEpochMillis, zone)
        val to = from + WEEK_MILLIS
        val inside = trades.filter { it.closedAtEpochMillis in from until to }
        val won = inside.count(WeekTrade::won)
        val mover = marketChanges
            .filter { (_, change) -> change != null && change.isFinite() }
            .maxByOrNull { (_, change) -> kotlin.math.abs(change!!) }
            ?.takeIf { (_, change) -> kotlin.math.abs(change!!) >= QUIET_PERCENT }
        return WeekSummary(
            fromEpochMillis = from,
            toEpochMillis = to,
            trades = inside.size,
            won = won,
            // The whole of the rule, in one expression: below the floor there is no percentage,
            // and the screen has the count to show instead.
            winPercent = if (inside.size >= MINIMUM_TRADES) won * 100.0 / inside.size else null,
            alertsFired = alertFiredAt.count { it in from until to },
            alertsArmed = alertsArmed,
            practiceSessions = practiceFinishedAt.count { it in from until to },
            moverSymbol = mover?.first,
            moverPercent = mover?.second,
        )
    }

    private const val WEEK_MILLIS = 7L * 24L * 60L * 60L * 1000L
}
