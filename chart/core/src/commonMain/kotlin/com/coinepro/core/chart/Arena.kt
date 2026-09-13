package com.coinepro.core.chart

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * **میدان** — the daily replay challenge (run Ω4).
 *
 * ### What it is for
 *
 * The replay engine has existed since run E and almost nobody opens it, for the reason almost nobody
 * opens a backtester: it is a tool with no question attached. The Arena attaches one. Everybody gets
 * *the same* instrument and *the same* window on the same day, five minutes on the clock, a paper
 * account, and a score at the end — which turns an engine into something a person comes back to
 * tomorrow, and turns «I would have caught that» into a number.
 *
 * ### Why the challenge is computed and not served
 *
 * Neither backend has a route for «today's symbol and window», and `docs/runs/RUN_OMEGA/BLOCKED.md`
 * says so. The client picks deterministically from the date, so every reader on a given day gets the
 * same challenge and a shared score is comparable — which is the whole property a leaderboard needs.
 * When the endpoint exists it replaces [challengeFor] and nothing else moves: the seam is one
 * function returning one value.
 *
 * ### Why the score is discipline first
 *
 * A five-minute replay scored on profit alone teaches the one lesson this product exists to argue
 * against: that a good trade is a profitable one. It is not. A trade taken with a stop that lost is a
 * good trade; a trade taken without one that won is a bad trade that happened to pay. So sixty of the
 * hundred points are for how the trades were *taken* and forty for what they made, and the result
 * screen says both numbers separately so neither can hide inside the other.
 */
object Arena {

    /** Five minutes, which is the brief's and is the right length: long enough to be wrong twice. */
    const val TIMER_SECONDS = 300

    /** Bars the replay must have ahead of it, or there is no challenge to play. */
    const val MINIMUM_FORWARD_BARS = 60

    /** Bars of history behind the start, so the chart the reader arrives at is readable. */
    const val MINIMUM_CONTEXT_BARS = 120

    /**
     * Today's challenge, picked from the date.
     *
     * The same [epochDay] and the same [symbols] always produce the same challenge, on every phone,
     * in every process — which is the property that makes two readers' scores about the same thing.
     * [symbols] must therefore be in a stable order; the caller sorts it.
     *
     * Null where there is not enough history to play on: a challenge whose window has forty bars in
     * front of it is five minutes of pressing «next» into an empty chart.
     */
    fun challengeFor(epochDay: Long, symbols: List<String>, historyBars: Int): ArenaChallenge? {
        if (symbols.isEmpty()) return null
        if (historyBars < MINIMUM_CONTEXT_BARS + MINIMUM_FORWARD_BARS) return null
        // A plain multiplicative hash of the date. Deliberately not a platform `Random`: this has to
        // give the same answer on a phone, on the JVM in a test and in the web terminal, and only
        // arithmetic written out here does that.
        var seed = epochDay * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
        fun next(): Long {
            seed = seed * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
            return (seed ushr 17) and 0x7FFF_FFFFL
        }
        val symbol = symbols[(next() % symbols.size).toInt()]
        // The window's start, somewhere in the middle of what is loaded: far enough in that the
        // chart has a past, far enough from the end that it has a future.
        val span = historyBars - MINIMUM_CONTEXT_BARS - MINIMUM_FORWARD_BARS
        val offset = MINIMUM_CONTEXT_BARS + (next() % (span + 1)).toInt()
        return ArenaChallenge(symbol = symbol, startBar = offset, epochDay = epochDay)
    }

    /**
     * Score a finished session.
     *
     * Pure arithmetic over what the paper account recorded, so the result screen and any future
     * leaderboard cannot disagree, and so «why did I get 48» is answerable from the same three
     * numbers the screen prints.
     */
    fun score(trades: List<ArenaTrade>): ArenaScore {
        if (trades.isEmpty()) return ArenaScore.UNPLAYED
        val withStop = trades.count { it.hadStop }
        val revenge = trades.count { it.revenge }
        // Sixty points for taking trades the way a plan says to, minus a flat penalty per revenge
        // trade. The penalty is flat rather than proportional because one revenge trade in three is
        // the same mistake as one in ten — it is a thing that happened, not a rate.
        val discipline = (DISCIPLINE_POINTS * withStop / trades.size - REVENGE_PENALTY * revenge)
            .coerceIn(0, DISCIPLINE_POINTS)
        val earned = trades.sumOf { it.rMultiple ?: 0.0 }
        // Forty points for the money, and capped: three units of risk is a very good five minutes,
        // and a reader who found a ten-R move should not out-score a disciplined session by six
        // times. The cap is what stops the Arena rewarding the one behaviour it is arguing against.
        val profit = (PROFIT_POINTS * (earned / TARGET_R).coerceIn(0.0, 1.0)).roundToInt()
        return ArenaScore(
            discipline = discipline,
            profit = profit,
            trades = trades.size,
            withStop = withStop,
            revenge = revenge,
            earnedR = earned,
        )
    }

    /** Sixty of the hundred. See the class note for why this is the larger half. */
    const val DISCIPLINE_POINTS = 60

    /** And forty for the result. */
    const val PROFIT_POINTS = 40

    /** What a full-marks five minutes makes, in units of risk. */
    const val TARGET_R = 3.0

    /** What one revenge trade costs. */
    const val REVENGE_PENALTY = 15

    /**
     * How soon after a loss a new trade counts as revenge.
     *
     * Ninety seconds of replay time. It is not a claim about anybody's state of mind — it is the
     * observable thing: a position opened a moment after one was stopped out, before the bar that
     * stopped it had even closed. The window is in the *replay's* clock rather than the reader's, so
     * it means the same thing at every playback speed.
     */
    const val REVENGE_WINDOW_SECONDS = 90L
}

/**
 * One day's challenge: which market, and where the replay starts.
 *
 * [startBar] is an index into the loaded history rather than a timestamp, because the two backends
 * disagree about how far back they will serve and a timestamp one of them cannot reach is a
 * challenge one of them cannot play.
 */
data class ArenaChallenge(
    val symbol: String,
    val startBar: Int,
    val epochDay: Long,
)

/**
 * One trade, as the Arena reads it.
 *
 * A flat value rather than the paper account's own row, for the reason [TradeFacts] is one: this
 * module does not know what a paper account is, and a score is about a stop, a result and a moment.
 */
data class ArenaTrade(
    /** Whether it went on with a stop attached. The single biggest term in the score. */
    val hadStop: Boolean,
    /** The result in units of risk. Null where there was no stop to measure it against. */
    val rMultiple: Double? = null,
    /** Whether it was opened inside [Arena.REVENGE_WINDOW_SECONDS] of a losing close. */
    val revenge: Boolean = false,
)

/**
 * What five minutes came to.
 *
 * The two halves are carried separately and printed separately. A single number would let a
 * profitable, undisciplined session hide behind a good total — which is exactly the reading the
 * Arena exists to make impossible.
 */
data class ArenaScore(
    val discipline: Int,
    val profit: Int,
    val trades: Int,
    val withStop: Int,
    val revenge: Int,
    val earnedR: Double,
) {
    val total: Int get() = discipline + profit

    /** A session with no trades in it. Not a zero — a zero is a score, and this is an absence. */
    val played: Boolean get() = trades > 0

    companion object {
        val UNPLAYED = ArenaScore(
            discipline = 0,
            profit = 0,
            trades = 0,
            withStop = 0,
            revenge = 0,
            earnedR = 0.0,
        )
    }
}

/**
 * Marks the trades that followed a loss too closely.
 *
 * Separated from [Arena.score] because it is the one part of the scoring that needs the *order* and
 * the clock, and because it is the term a reader will argue with — so it is one function with one
 * rule, testable on its own.
 *
 * @param opened the moment each trade was opened, in the replay's own seconds.
 * @param closed the moment each was closed, in the same clock.
 */
fun markRevengeTrades(
    trades: List<ArenaTrade>,
    opened: List<Long>,
    closed: List<Long>,
    results: List<Double>,
): List<ArenaTrade> {
    if (trades.size != opened.size || trades.size != closed.size || trades.size != results.size) return trades
    return trades.mapIndexed { index, trade ->
        val revenge = (0 until index).any { earlier ->
            results[earlier] < 0.0 &&
                opened[index] >= closed[earlier] &&
                opened[index] - closed[earlier] <= Arena.REVENGE_WINDOW_SECONDS
        }
        trade.copy(revenge = revenge)
    }
}

/** The distance between two prices, for a caller sizing a stop. Here so nobody writes it twice. */
internal fun riskBetween(entry: Double, stop: Double): Double = abs(entry - stop)
