package com.coinepro.core.chart

import kotlin.math.abs

/**
 * The nine playback speeds, as a ladder from slow study to fast scan.
 *
 * Nine is the web terminal's number and it is the right one. Fewer steps and the jump between two
 * of them is jarring — a reader who finds 1x too slow and 5x unwatchable has nowhere to go. More
 * steps and the picker stops being a row of chips and becomes a list, which is a menu to read
 * instead of a chart to watch. Nine fits across a phone in one row, and every neighbouring pair is
 * close enough that no reader ever wants the value between them.
 *
 * [millisPerBar] is how long one bar is held on screen, and it is the property the caller schedules
 * against; [multiplier] is only the label. The two slowest steps exist for studying one candle
 * forming — sixteen seconds a bar is long enough to read the structure around it — and the two
 * fastest for scanning a month of history for the next setup, where the reader is looking for a
 * shape rather than a price.
 */
enum class ReplaySpeed(
    /** The label a reader sees, as a multiple of the normal speed. */
    val multiplier: Double,
    /** How long one bar stays on screen before the next is revealed. */
    val millisPerBar: Long,
) {
    /** Sixteen seconds a bar. One candle at a time, for reading a single formation. */
    STUDY(0.1, 16_000),

    /** Six and a half seconds a bar. Slow enough to narrate while it runs. */
    SLOW(0.25, 6_400),

    /** Just over three seconds a bar — the pace of watching a live one-minute chart. */
    HALF(0.5, 3_200),

    /** The reference step: one bar every 1.6 seconds. Every other step is derived from it. */
    NORMAL(1.0, 1_600),

    /** A bar and a quarter a second. Fast enough to keep a practice session moving. */
    DOUBLE(2.0, 800),

    /** Roughly two bars a second. */
    TRIPLE(3.0, 533),

    /** Three bars a second. Structure is still readable; individual wicks are not. */
    QUINTUPLE(5.0, 320),

    /** Six bars a second. A trend reads as a slope rather than as candles. */
    TEN(10.0, 160),

    /** Twenty bars a second. Scanning for where something happened, not reading it. */
    THIRTY(30.0, 53),
    ;

    companion object {

        /**
         * The step nearest a raw multiplier.
         *
         * [ReplayState.speed] carries a `Double` rather than this enum, because that field predates
         * the ladder and is what saved chart layouts already contain. A layout written before a
         * ladder change must still reopen at a sensible speed rather than silently snapping back to
         * 1x, so the nearest step wins instead of requiring an exact match.
         */
        fun nearest(multiplier: Double): ReplaySpeed =
            entries.minByOrNull { abs(it.multiplier - multiplier) } ?: NORMAL
    }
}

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
     * `by lazy`, not a getter, and the difference is the reason a replay could not be zoomed.
     *
     * `CandleSeries` has identity equality, so a getter that allocated a fresh one on every read
     * made every recomposition look like a *new series* to the chart, whose viewport is remembered
     * against exactly that. The view was rebuilt from defaults on every frame: pinch did nothing,
     * pan did nothing, and the chart snapped back to 120 bars at the live edge several times a
     * second. Computed once per state — and this class is immutable, so a new cursor is a new
     * state and a new list — the identity is stable and the viewport survives.
     */
    val visible: CandleSeries by lazy(LazyThreadSafetyMode.NONE) {
        CandleSeries(bars.take(cursor + 1))
    }

    /**
     * The revealed bars, raw and untransformed — and the reason replay works on every chart type.
     *
     * The web terminal cannot replay Renko, Kagi, Point-and-Figure, Range, Line-break, footprint or
     * TPO, and says so in its own documentation. The reason is architectural: it replays the
     * *transformed* output, and every one of those types is path-dependent — a Renko brick is
     * printed only once price has travelled a box size, so a Renko series cannot be truncated at an
     * arbitrary bar and still be the series that history would have produced. Truncating the bricks
     * gives a chart that never existed.
     *
     * Our transforms in [ChartTransforms] are pure bars-to-bars functions with no state carried in
     * from outside the input, so we do the opposite: replay slices the *raw* series and the caller
     * re-runs the transform over the slice. The Renko a reader sees at cursor 400 is exactly the
     * Renko they would have seen live at bar 400, because it was built the same way — from the
     * first 401 candles and nothing else. That is why this returns candles rather than a drawn
     * series, and why a caller must never replay transformed output: transform the slice, do not
     * slice the transform.
     *
     * Shares [visible]'s backing list, so the identity is as stable as the viewport needs.
     */
    fun visibleRaw(): List<Candle> = visible.bars

    /** How far through, for a progress bar. */
    val progress: Float
        get() = if (bars.size <= 1) 0f else cursor.toFloat() / (bars.size - 1)

    /**
     * [speed] resolved onto the ladder, for a picker that has to mark one chip selected.
     *
     * Resolved rather than matched, because a persisted layout may hold a multiplier that is no
     * longer a step; leaving no chip highlighted would read as a broken control.
     */
    val speedStep: ReplaySpeed get() = ReplaySpeed.nearest(speed)
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
     * The speeds offered, as bare multipliers.
     *
     * Nine steps rather than a slider: a slider invites a reader to hunt for a speed instead of
     * watching the chart, and the two ends of this ladder — a tenth and thirty times — are far
     * enough apart that no intermediate value is missed.
     *
     * Derived from [ReplaySpeed] rather than written out again, because the two lists drifting
     * apart is exactly the bug that is invisible: a picker offering a multiplier the scheduler does
     * not recognise looks selected and changes nothing.
     */
    val SPEEDS: List<Double> = ReplaySpeed.entries.map { it.multiplier }

    /**
     * Fewer bars than this and there is nothing to replay.
     *
     * Thirty is the web terminal's number. Below it the chart starts at its own right edge and the
     * exercise is pointless rather than merely short.
     */
    const val MINIMUM_BARS = 30

    /**
     * How long one step lasts at a given speed, in milliseconds.
     *
     * Accepts any positive multiplier, not only a ladder step, so a persisted layout is never
     * rejected; a value at or below zero would divide to infinity and is read as 1x.
     *
     * The ceiling is sixteen seconds rather than the four it used to be. Four collapsed the two
     * slowest steps onto the same delay: 0.1x and 0.25x both clamped to 4,000 ms, so two of the
     * nine chips did exactly the same thing and the slowest one — the one a reader picks precisely
     * because they want to sit on a single candle — was silently two and a half times too fast.
     * With the ceiling raised, this function reproduces [ReplaySpeed.millisPerBar] exactly at every
     * step of the ladder, which is what makes the enum and this arithmetic safe to mix.
     */
    fun delayMillis(speed: Double): Long {
        val safe = if (speed > 0) speed else 1.0
        return (BASE_STEP_MS / safe).toLong().coerceIn(16, 16_000)
    }

    /** How long one step lasts at a ladder step. The enum already carries the answer. */
    fun delayMillis(speed: ReplaySpeed): Long = speed.millisPerBar

    /**
     * Begin, with the cursor part-way through.
     *
     * Fifty-five per cent by default, which leaves rather more history behind the cursor than
     * future ahead of it. That is the right asymmetry: the reader needs enough past to form a view
     * and only enough future to find out whether it was right.
     *
     * Every bad input is answered with a null or a clamp, never a throw. Entering before the series
     * has loaded — an empty list, or the handful of bars a first page returns — gives null, which
     * the caller reads as "not yet" and the button stays disabled. A [startIndex] outside the
     * series is pulled to the nearest end. Replay is reached by a tap on a chart that may still be
     * filling in behind it, and a crash there loses whatever the reader had drawn.
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

    /**
     * Reposition the cursor without leaving replay.
     *
     * The distinction from [seek] is intent, not arithmetic: [seek] is the scrub handle, dragged
     * continuously while the reader watches the chart follow; this is a jump to a bar the reader
     * named — a date typed in, a tap on a drawing, a search result. It stops playback, because
     * landing somewhere new and immediately being carried forward from it is disorienting and
     * costs the reader the very bar they went to look at.
     *
     * An index outside the series is clamped rather than refused. A caller computing an index from
     * a pixel, a date or a fraction will occasionally hand over -1 or `size`, and an exception
     * there would end a practice session over a rounding error.
     */
    fun goTo(state: ReplayState, index: Int): ReplayState {
        if (!state.isOn) return state
        val target = index.coerceIn(0, state.bars.size - 1)
        return if (target == state.cursor && !state.playing) {
            state
        } else {
            state.copy(cursor = target, playing = false)
        }
    }

    /**
     * Leave replay at the newest bar.
     *
     * Distinct from [exit], and the difference matters to the caller: [exit] forgets the snapshot
     * and hands the chart back to live data, while this walks the cursor to the last bar of the
     * snapshot and stops. The reader stays in replay, now seeing everything the snapshot holds, so
     * the practice run can be reviewed end to end before it is thrown away. A caller that wants
     * both calls this and then [exit].
     */
    fun jumpToLive(state: ReplayState): ReplayState =
        if (!state.isOn) state else state.copy(cursor = state.bars.size - 1, playing = false)

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

    /**
     * Change speed by ladder step, which is what a picker should call.
     *
     * Nothing to validate: the type is the validation, so unlike the `Double` overload this one
     * can never quietly do nothing.
     */
    fun setSpeed(state: ReplayState, speed: ReplaySpeed): ReplayState =
        state.copy(speed = speed.multiplier)

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
