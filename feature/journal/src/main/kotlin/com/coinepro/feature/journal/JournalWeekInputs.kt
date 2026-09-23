package com.coinepro.feature.journal

/**
 * What «هفته‌ی من» needs that the journal cannot see — run Τ2, C4.
 *
 * The trades come from the journal's own entries and the week's boundaries from the clock, so those
 * are not here. These three are the app's, and each lives in a module this one does not depend on:
 * the alert store is in `core:datastore`, the practice sessions in `ArenaStore`, and the market
 * moves come off whichever feed the shell is running.
 *
 * ### Why plain lists rather than the stores themselves
 *
 * Because a journal that took a `LocalAlertStore` would be a journal that depends on alerts, and
 * the next thing that wanted a row on this card would add a fourth dependency to a screen about
 * writing down trades. The shell already has all three on its classpath and hands over four
 * numbers; the screen stays a screen.
 *
 * ### Empty is a working state, not a missing one
 *
 * Every field defaults to nothing, and the card still draws: the trades and the week are real, and
 * the rows these fill read as zero — which is what this device can actually see. That matters for
 * the preview host and for any build that wires the journal alone.
 */
data class JournalWeekInputs(
    /** When each of the reader's alerts last fired, in milliseconds. Filtered to the week here. */
    val alertFiredAt: List<Long> = emptyList(),
    /**
     * How many alerts are armed **now**.
     *
     * A standing state rather than something that happened this week, which is why it does not
     * make an otherwise empty week look busy — see `WeekSummary.empty`.
     */
    val alertsArmed: Int = 0,
    /** When each replay session finished, in milliseconds. */
    val practiceFinishedAt: List<Long> = emptyList(),
    /**
     * The reader's markets and how far each moved, as a percentage, or null where nothing measured.
     *
     * Null rather than zero, for the reason the morning brief gives: «the feed did not say» and
     * «it did not move» are different facts, and only one of them belongs in a summary of a week.
     */
    val marketChanges: List<Pair<String, Double?>> = emptyList(),
)
