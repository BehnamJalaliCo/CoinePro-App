package com.coinepro.core.chart

import kotlin.math.abs

/**
 * Bar replay: the chart rewound to a point in the past and walked forward.
 *
 * Ported from `ReplayController.js`, and headless in the same way — it holds a snapshot and a
 * cursor and hands out a slice. Nothing here draws, and nothing here owns a timer: the caller
 * drives [step] from whatever clock it has, which is what makes the whole thing a unit test rather
 * than a stopwatch and a screen.
 *
 * The point of replay is practice. A reader who wants to know whether they would have taken the
 * trade has to not be able to see what happened next, and every part of this exists to enforce
 * that: the slice is the only view offered, and it never reaches past the cursor.
 */
data class ReplayState(
    /** Every bar, including the ones not yet revealed. Empty when replay is off. */
    val bars: List<Candle> = emptyList(),
    /** The last revealed bar. Everything after it is the future and must not be drawn. */
    val cursor: Int = 0,
    val playing: Boolean = false,
    val speed: Double = 1.0,
) {
    val isOn: Boolean get() = bars.isNotEmpty()

    val atEnd: Boolean get() = cursor >= bars.size - 1

    /**
     * What the chart may show.
     *
     * Rebuilt on demand rather than held, because holding it means two representations of the same
     * truth and one of them going stale — and the one that goes stale here shows the future.
     */
    val visible: CandleSeries get() = CandleSeries(bars.take(cursor + 1))

    /** How far through, for a progress bar. */
    val progress: Float
        get() = if (bars.size <= 1) 0f else cursor.toFloat() / (bars.size - 1)
}

/**
 * The replay transitions.
 *
 * Pure functions from state to state, like the drawing state machine and for the same reason: the
 * interesting bugs here are off-by-one at the ends, and those are much easier to be certain about
 * as assertions than as a screen somebody watches.
 */
object Replay {

    /**
     * The speeds offered, in the ladder the web terminal uses.
     *
     * Nine steps rather than a slider: a slider invites a reader to hunt for a speed instead of
     * watching the chart, and the two ends of this ladder — a tenth and thirty times — are far
     * enough apart that no intermediate value is missed.
     */
    val SPEEDS = listOf(0.1, 0.25, 0.5, 1.0, 2.0, 3.0, 5.0, 10.0, 30.0)

    /**
     * Fewer bars than this and there is nothing to replay.
     *
     * Thirty is the web terminal's number. Below it the chart starts at its own right edge and the
     * exercise is pointless rather than merely short.
     */
    const val MINIMUM_BARS = 30

    /** How long one step lasts at a given speed, in milliseconds. */
    fun delayMillis(speed: Double): Long {
        val safe = if (speed > 0) speed else 1.0
        return (BASE_STEP_MS / safe).toLong().coerceIn(16, 4_000)
    }

    /**
     * Begin, with the cursor part-way through.
     *
     * Fifty-five per cent by default, which leaves rather more history behind the cursor than
     * future ahead of it. That is the right asymmetry: the reader needs enough past to form a view
     * and only enough future to find out whether it was right.
     */
    fun enter(
        bars: List<Candle>,
        startIndex: Int? = null,
        startTime: Long? = null,
        startFraction: Double = 0.55,
    ): ReplayState? {
        if (bars.size < MINIMUM_BARS) return null
        val start = when {
            startIndex != null -> startIndex
            startTime != null -> indexOfTime(bars, startTime)
            else -> (bars.size * startFraction).toInt()
        }
        return ReplayState(bars = bars, cursor = start.coerceIn(0, bars.size - 1))
    }

    /** Reveal the next bar. At the end it pauses rather than wrapping or stalling silently. */
    fun step(state: ReplayState): ReplayState = when {
        !state.isOn -> state
        state.atEnd -> state.copy(playing = false)
        else -> state.copy(cursor = state.cursor + 1)
    }

    /** Hide the last revealed bar, so a move can be watched again. */
    fun stepBack(state: ReplayState): ReplayState =
        if (!state.isOn || state.cursor <= 0) state else state.copy(cursor = state.cursor - 1)

    fun seek(state: ReplayState, index: Int? = null, time: Long? = null): ReplayState {
        if (!state.isOn) return state
        val target = when {
            index != null -> index
            time != null -> indexOfTime(state.bars, time)
            else -> state.cursor
        }
        return state.copy(cursor = target.coerceIn(0, state.bars.size - 1))
    }

    /** Start playing. Refused at the end, where there is nothing left to reveal. */
    fun play(state: ReplayState): ReplayState =
        if (!state.isOn || state.playing || state.atEnd) state else state.copy(playing = true)

    fun pause(state: ReplayState): ReplayState =
        if (state.playing) state.copy(playing = false) else state

    fun toggle(state: ReplayState): ReplayState =
        if (state.playing) pause(state) else play(state)

    /** Change speed. An unknown value is ignored rather than accepted. */
    fun setSpeed(state: ReplayState, speed: Double): ReplayState =
        if (speed in SPEEDS) state.copy(speed = speed) else state

    /** Leave replay. The caller restores live data; this only forgets the snapshot. */
    fun exit(): ReplayState = ReplayState()

    /**
     * The bar nearest a moment, by binary search.
     *
     * Nearest rather than the first at-or-after: a reader who picks a date off a calendar means
     * "around here", and landing a bar early is as good an answer as landing one late.
     */
    fun indexOfTime(bars: List<Candle>, time: Long): Int {
        if (bars.isEmpty()) return 0
        if (time <= bars.first().t) return 0
        if (time >= bars.last().t) return bars.size - 1
        var low = 0
        var high = bars.size - 1
        while (low < high) {
            val middle = (low + high) / 2
            if (bars[middle].t < time) low = middle + 1 else high = middle
        }
        return if (low > 0 && abs(bars[low - 1].t - time) <= abs(bars[low].t - time)) low - 1 else low
    }

    /** One step at 1×. The ladder scales it. */
    private const val BASE_STEP_MS = 1_600.0
}
