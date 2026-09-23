package com.coinepro.core.chart

import kotlin.math.abs

/** Which way the reader said it would go. There is no third answer, and that is the design. */
enum class DuelCall { UP, DOWN }

/** How a call turned out. */
enum class DuelVerdict {
    RIGHT,
    WRONG,

    /**
     * The market did not move enough for the call to have been either.
     *
     * **The most important of the three.** A market that closed 0.03 % away from where it started
     * did not prove the reader right, whichever way they called it, and scoring it as a win teaches
     * them to read noise as signal — which is the single habit this product exists to argue
     * against. It counts as played and as neither.
     */
    TOO_CLOSE,
}

/**
 * One moment out of the reader's own past, with what happened next hidden.
 *
 * [atBar] is the last bar the reader may see; [resolveBar] is the one the call is judged on. Both
 * are indices into the series the caller loaded, and the gap between them is [Duel.FORWARD_BARS] —
 * fixed, so the question is the same question every time and a reader cannot be asked to predict a
 * week on Monday and an hour on Tuesday.
 */
data class DuelRound(
    val symbol: String,
    val atBar: Int,
    val resolveBar: Int,
    /** The day this round was picked for, so re-opening the app does not reroll it. */
    val epochDay: Long,
)

/** What one answered round came to. */
data class DuelOutcome(
    val call: DuelCall,
    val verdict: DuelVerdict,
    /** How far the market actually moved, as a percentage, close to close. Signed. */
    val movePercent: Double,
)

/**
 * The reader's record. Counted, never averaged below the floor — see [Duel.MINIMUM_ROUNDS].
 */
data class DuelRecord(
    val played: Int = 0,
    val right: Int = 0,
    /** Rounds the market did not move enough to judge. Played, and neither right nor wrong. */
    val tooClose: Int = 0,
) {
    /** Rounds that actually resolved one way or the other. The denominator of any rate. */
    val judged: Int get() = played - tooClose

    /**
     * The hit rate, or **null below the floor**.
     *
     * Null for [MyWeek]'s reason and it is the same reason: a rate over three calls is three coin
     * flips, and printing it teaches somebody something about themselves that is not true.
     */
    val rightPercent: Double?
        get() = if (judged >= Duel.MINIMUM_ROUNDS) right * 100.0 / judged else null
}

/**
 * **دوئل با گذشته** — the chart as it was, with the next part hidden, and one question.
 *
 * ### Why this is not the Arena
 *
 * The Arena asks «could you have traded this» — five minutes, a paper account, and a score that is
 * sixty percent about *how* the trades were taken. This asks a smaller and much sharper question:
 * «which way did it go». One tap, no execution, no stop, no position size. The two are different
 * skills and conflating them is what makes a replay tool feel like homework.
 *
 * It is also **the reader's own markets**, not everybody's: the point is «I would have caught that»
 * about an instrument they actually watch, and a duel on a symbol they have never opened is a
 * quiz question.
 *
 * ### The rule that keeps the score honest
 *
 * A move under [FLAT_PERCENT] is [DuelVerdict.TOO_CLOSE] — neither right nor wrong. Without it
 * every round resolves, half of them on noise, and a reader converges on fifty percent while
 * learning that a coin flip is a read. With it the record counts only the rounds where the market
 * actually did something, and says how many it set aside.
 *
 * ### And the future is never in the series
 *
 * The caller hands the chart `bars.take(atBar + 1)` — which is exactly what `ReplayState.visible`
 * already produces — so there is no path by which the answer is on screen. [judge] is given the
 * whole series because it runs *after* the call, and it is the only thing here that ever sees past
 * [DuelRound.atBar].
 */
object Duel {

    /**
     * How far ahead the call is judged.
     *
     * Twenty bars. On the hourly chart this app defaults to that is most of a day — long enough
     * that a read about direction has room to be right, short enough that it is still a read about
     * the picture in front of the reader rather than about the next quarter.
     */
    const val FORWARD_BARS = 20

    /** Bars of history behind the moment, so the chart the reader is asked about is readable. */
    const val CONTEXT_BARS = 120

    /**
     * Below this, the market did not move enough for a call to have been right.
     *
     * Half a percent over twenty bars. Larger than the morning brief's tenth of a percent, because
     * this one has to clear twenty bars of drift rather than describe a single day, and a threshold
     * that a market crosses by standing still is not a threshold.
     */
    const val FLAT_PERCENT = 0.5

    /** How many judged rounds a hit rate needs. See [DuelRecord.rightPercent]. */
    const val MINIMUM_ROUNDS = 5

    /**
     * Today's round, or null where there is not enough history to ask about.
     *
     * Deterministic in [epochDay] and [symbols], so the question does not change when the reader
     * closes the app and opens it again — and so it does change tomorrow. The arithmetic is written
     * out rather than taken from a platform `Random` for [Arena.challengeFor]'s reason: the same
     * answer has to come out on a phone, on the JVM in a test, and in the web terminal.
     *
     * [symbols] must be in a stable order; the caller sorts it. Null where a shorter series cannot
     * carry both the context behind the moment and the bars in front of it — a duel whose answer is
     * off the end of the data is not a duel.
     */
    fun roundFor(epochDay: Long, symbols: List<String>, historyBars: Int): DuelRound? {
        if (symbols.isEmpty()) return null
        if (historyBars < CONTEXT_BARS + FORWARD_BARS + 1) return null
        // A different constant from the Arena's, so a reader does not get the same instrument for
        // both on the same day — which would read as the app having one idea rather than two.
        var seed = epochDay * 2_862_933_555_777_941_757L + 3_037_000_493L
        fun next(): Long {
            seed = seed * 2_862_933_555_777_941_757L + 3_037_000_493L
            return (seed ushr 17) and 0x7FFF_FFFFL
        }
        val symbol = symbols[(next() % symbols.size).toInt()]
        val span = historyBars - CONTEXT_BARS - FORWARD_BARS - 1
        val at = CONTEXT_BARS + (next() % (span + 1)).toInt()
        return DuelRound(
            symbol = symbol,
            atBar = at,
            resolveBar = at + FORWARD_BARS,
            epochDay = epochDay,
        )
    }

    /**
     * What the call came to, or null where the series cannot answer.
     *
     * Null rather than a guess for a round whose resolving bar is off the end, or whose closes are
     * not finite: an unanswerable round must not be recorded, because a record that counted it
     * would be a record of something that did not happen.
     */
    fun judge(series: CandleSeries, round: DuelRound, call: DuelCall): DuelOutcome? {
        if (round.atBar < 0 || round.resolveBar >= series.size) return null
        val from = series.close[round.atBar]
        val to = series.close[round.resolveBar]
        if (!from.isFinite() || !to.isFinite() || from == 0.0) return null
        val move = (to - from) / from * 100.0
        val verdict = when {
            abs(move) < FLAT_PERCENT -> DuelVerdict.TOO_CLOSE
            (move > 0) == (call == DuelCall.UP) -> DuelVerdict.RIGHT
            else -> DuelVerdict.WRONG
        }
        return DuelOutcome(call = call, verdict = verdict, movePercent = move)
    }

    /** The record with one more round in it. */
    fun record(record: DuelRecord, outcome: DuelOutcome): DuelRecord = DuelRecord(
        played = record.played + 1,
        right = record.right + if (outcome.verdict == DuelVerdict.RIGHT) 1 else 0,
        tooClose = record.tooClose + if (outcome.verdict == DuelVerdict.TOO_CLOSE) 1 else 0,
    )
}
