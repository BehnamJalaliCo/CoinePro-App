package com.coinepro.feature.chart

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.coinepro.core.chart.Arena
import com.coinepro.core.chart.ArenaChallenge
import com.coinepro.core.chart.ArenaScore

/**
 * **میدان**, as a mode of the chart rather than a screen of its own (run Ω4).
 *
 * ### Why there is no Arena screen
 *
 * Everything the Arena needs already exists on the chart page and is already tested there: the
 * replay engine and its bar, the paper ticket, the setup card that draws an entry, a stop and a
 * target, and the plot itself. A second screen would be a second copy of all of it, drifting from
 * the first the day either is touched — and the reader would be playing on a chart that is not the
 * chart they trade on, which is the one thing a rehearsal must never be.
 *
 * So the Arena is this state, one bar above the command band, and one sheet at the end. The chart
 * underneath is the chart.
 *
 * ### Why the clock is a count of seconds and not a `Duration`
 *
 * It is decremented by a `LaunchedEffect` on the composition's own clock and read by a bar that
 * draws it. There is nothing here that needs to survive the process — a five-minute challenge
 * interrupted by a phone call is over, and pretending otherwise would mean storing a start time and
 * arguing with a clock the reader can change.
 */
@Stable
class ArenaSession(
    val challenge: ArenaChallenge,
    /** The day this session belongs to, so the result is recorded against the right challenge. */
    val epochDay: Long,
) {

    /** Seconds left. Counts down while [running]; the bar reads it. */
    var remaining by mutableStateOf(Arena.TIMER_SECONDS)
        private set

    /** Whether the clock is going. False before the reader starts and after the session ends. */
    var running by mutableStateOf(false)
        private set

    /** The score, once there is one. Null while the session is still open. */
    var score by mutableStateOf<ArenaScore?>(null)
        private set

    /** Whether the five minutes are over, however they ended. */
    val finished: Boolean get() = score != null

    /** How far through the five minutes, 0..1, for the bar's own rule. */
    val progress: Float get() = 1f - remaining.toFloat() / Arena.TIMER_SECONDS

    fun start() {
        if (finished) return
        running = true
    }

    /** One second of the clock. Returns true when that tick ended the session. */
    fun tick(): Boolean {
        if (!running) return false
        remaining = (remaining - 1).coerceAtLeast(0)
        return remaining == 0
    }

    /**
     * Ends the session with a score.
     *
     * Called by the clock reaching zero and by the reader pressing «تمام», and the two are the same
     * thing on purpose: a reader who is finished at ninety seconds has finished, and a challenge
     * that made them sit out the remaining three and a half minutes would be teaching them to trade
     * to fill a timer.
     */
    fun finish(result: ArenaScore) {
        running = false
        remaining = 0
        score = result
    }
}
