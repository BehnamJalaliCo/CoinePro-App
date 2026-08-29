package com.coinepro.feature.alerts

import com.coinepro.core.notifications.AlertFrequency
import com.coinepro.core.notifications.AlertRepeat
import com.coinepro.core.notifications.LocalPriceAlert

/**
 * Which of the three parts of the list an alert belongs in.
 *
 * Three and not four. «متوقف» is not a section: a paused alert is one the reader switched off for
 * an afternoon and will switch back on, it is still armed in every sense that matters to them, and
 * a fourth heading for it would push the two sections they came to read below the fold. It stays
 * under [ARMED] with a mark on its row.
 */
enum class AlertSectionKind {
    /** Waiting. Everything that can still fire, including the ones the reader has paused. */
    ARMED,

    /** It fired inside the recent window. The section somebody opens this screen to look at. */
    FIRED,

    /** It cannot fire again — its own expiry passed, or it was a one-shot and has been shot. */
    EXPIRED,
}

/** One heading and the alerts under it. Never empty; [AlertGrouping.group] drops empty sections. */
data class AlertSection(val kind: AlertSectionKind, val alerts: List<LocalPriceAlert>)

/**
 * How the alert list is cut into three.
 *
 * ### Why grouping rather than sorting
 *
 * A flat list sorted by time mixes three different questions. "What am I still waiting for" is the
 * reader's standing state, "what went off" is what they open the app to check after a move, and
 * "what is dead" is a housekeeping list. Sorted together, the second question — the urgent one —
 * is answered by scanning for a badge. Cut apart, it is answered by looking at one heading.
 *
 * ### Pure, with the clock as a parameter
 *
 * Every boundary here is a time comparison, so the clock arrives as an argument rather than being
 * read. "Does a one-shot that fired eight seconds ago show as fired or as expired" is then a unit
 * test rather than a thing somebody checks by making an alert and waiting.
 */
object AlertGrouping {

    /**
     * How long after firing an alert still counts as «تازه».
     *
     * A day, because the reader this section exists for is the one who slept through a move and is
     * looking at their phone over breakfast. An hour would have already hidden the thing they came
     * for; a week would fill the section with last Tuesday.
     */
    const val RECENT_WINDOW_MILLIS: Long = 24L * 60L * 60L * 1000L

    /**
     * Where one alert belongs.
     *
     * The order of the tests is the design, and each one is answering an objection to the one
     * before it:
     *
     * 1. An expiry the reader typed themselves wins outright. They said "after this, stop", and an
     *    alert shown as armed past its own expiry is the app disagreeing with them.
     * 2. A recent firing beats being spent. A one-shot that went off ten minutes ago is *both* — it
     *    can never fire again, and it is the single row the reader is looking for. Filing it under
     *    «منقضی» because it is technically finished is correct and useless.
     * 3. Only then is a spent one-shot expired. Both spellings of "one shot" count: the bar-aware
     *    [AlertFrequency.ONCE] and the older [AlertRepeat.ONCE] that governs an alert with no
     *    frequency.
     *
     * A [LocalPriceAlert.lastFiredAtEpochMillis] in the future is treated as having just fired
     * rather than as never having fired. Device clocks move backwards — a manual change, a network
     * sync after a flat battery — and an alert that vanished from «تازه» into «فعال» because of it
     * would look exactly like an alert that lost its history.
     */
    fun kindOf(
        alert: LocalPriceAlert,
        nowEpochMillis: Long,
        recentWindowMillis: Long = RECENT_WINDOW_MILLIS,
    ): AlertSectionKind {
        if (alert.hasExpired(nowEpochMillis)) return AlertSectionKind.EXPIRED
        val fired = alert.lastFiredAtEpochMillis
        if (fired != null && (fired >= nowEpochMillis || nowEpochMillis - fired <= recentWindowMillis)) {
            return AlertSectionKind.FIRED
        }
        if (fired != null && isOneShot(alert)) return AlertSectionKind.EXPIRED
        return AlertSectionKind.ARMED
    }

    /**
     * The whole list, cut and ordered.
     *
     * Sections come back in a fixed order — waiting, fired, expired — rather than in the order the
     * data happens to produce, because a screen whose headings move as alerts change state is a
     * screen the reader has to re-read every time they open it. Empty sections are dropped rather
     * than shown with nothing under them; a heading over a blank space reads as a fault.
     */
    fun group(
        alerts: List<LocalPriceAlert>,
        nowEpochMillis: Long,
        recentWindowMillis: Long = RECENT_WINDOW_MILLIS,
    ): List<AlertSection> {
        val byKind = alerts.groupBy { kindOf(it, nowEpochMillis, recentWindowMillis) }
        return AlertSectionKind.entries.mapNotNull { kind ->
            val members = byKind[kind].orEmpty()
            if (members.isEmpty()) null else AlertSection(kind, order(kind, members))
        }
    }

    /**
     * Whether this alert is spent once it has fired.
     *
     * [LocalPriceAlert.frequency] wins where it is present, because an alert that has one is
     * governed by it and the older [LocalPriceAlert.repeat] on the same row is then vestigial. Only
     * an alert with no frequency falls back to the repeat.
     */
    private fun isOneShot(alert: LocalPriceAlert): Boolean {
        val frequency = alert.frequency ?: return alert.repeat == AlertRepeat.ONCE
        return frequency == AlertFrequency.ONCE
    }

    /**
     * The order inside one section, which is a different question in each of them.
     *
     * Waiting is ordered by when it was made, newest first, and paused ones sink — the reader's
     * live alerts are what the section is for. Fired is ordered by when it fired, because that is
     * the column the reader is scanning. Expired is ordered by whichever timestamp ended it.
     */
    private fun order(kind: AlertSectionKind, alerts: List<LocalPriceAlert>): List<LocalPriceAlert> =
        when (kind) {
            AlertSectionKind.ARMED -> alerts.sortedWith(
                compareByDescending<LocalPriceAlert> { it.active }
                    .thenByDescending { it.createdAtEpochMillis },
            )
            AlertSectionKind.FIRED -> alerts.sortedByDescending { it.lastFiredAtEpochMillis ?: 0L }
            AlertSectionKind.EXPIRED -> alerts.sortedByDescending {
                it.expiresAt ?: it.lastFiredAtEpochMillis ?: it.createdAtEpochMillis
            }
        }
}
