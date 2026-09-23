package com.coinepro.feature.chart

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.coinepro.core.chart.DuelOutcome
import com.coinepro.core.chart.DuelRound

/**
 * **دوئل با گذشته**, as a mode of the chart — run Τ2, C3.
 *
 * ### Why there is no duel screen either
 *
 * `ArenaSession`'s argument, unchanged: the replay engine, its bar and the plot are all here and
 * all tested here, and a second screen would be a second copy of them drifting from the first. More
 * to the point, the reader has to be looking at *the chart they trade on* — a rehearsal on a
 * different-looking chart rehearses the wrong thing.
 *
 * ### What it holds, and what it deliberately does not
 *
 * The round, and the outcome once there is one. **No timer**: the Arena's five minutes exist
 * because trading under time pressure is part of what it is rehearsing, and reading a chart is not.
 * A reader who wants to stare at this one for ten minutes is doing the thing the feature is for.
 *
 * **No second call.** Once [outcome] is set the buttons are gone, because the answer is on screen:
 * a reader who could call again after seeing the next twenty bars would be recording a prediction
 * they did not make.
 */
@Stable
class DuelSession(
    val round: DuelRound,
    /** The reader's own local day, so the record's once-a-day guard is keyed on their calendar. */
    val localDay: Long,
) {

    /** What the call came to, or null while the reader has not called. */
    var outcome by mutableStateOf<DuelOutcome?>(null)
        private set

    /**
     * Whether the record refused this round because the day was already answered.
     *
     * Shown rather than swallowed: a refusal that looked like a counter failing to move is the
     * failure `DuelStore.answer` returns a boolean to prevent.
     */
    var alreadyAnswered by mutableStateOf(false)
        private set

    /** Whether the reader has called and the next bars are revealed. */
    val answered: Boolean get() = outcome != null

    /** Records the call. Ignored once one has been made — see the note on a second call. */
    fun answer(result: DuelOutcome, recorded: Boolean) {
        if (answered) return
        outcome = result
        alreadyAnswered = !recorded
    }
}
