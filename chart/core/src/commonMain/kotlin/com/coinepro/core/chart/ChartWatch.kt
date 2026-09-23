package com.coinepro.core.chart

/**
 * What a chart looks like in a window the size of a stamp — run Τ2, B8.
 *
 * ### Why it is a snapshot and not the chart
 *
 * A picture-in-picture window is about a hundred and fifty points across. Putting the real plot in
 * it would mean axes nobody can read, a legend that does not fit, and the whole indicator stack
 * recomputing for a surface with no room to show it. What a reader actually wants out of the corner
 * of their eye is four facts — which market, what it costs, which way it is going, and how long
 * this candle has left — plus enough of a line to see the shape.
 *
 * So the chart publishes *this*, and the window draws *this*. The two are decoupled on purpose: the
 * activity is what enters the mode and it lives nowhere near the chart's own state, and a window
 * that reached into a controller across that gap would keep a whole screen composed behind it.
 */
data class WatchSnapshot(
    val symbol: String,
    /** The bar length, as the wire names it — «H1». Drawn as-is; there is no room for a sentence. */
    val intervalWire: String,
    val price: Double,
    /** Change across the visible window, as a percentage. Null where it cannot be read. */
    val changePercent: Double?,
    /**
     * The tail of the series, oldest first, for the line.
     *
     * Capped at [ChartWatch.LINE_POINTS] by the publisher. More than that is invisible at this size
     * and is a list copied every second for nothing.
     */
    val closes: List<Double> = emptyList(),
    /** When the forming bar closes, in unix seconds, or null on a series with no open bar. */
    val barClosesAtEpochSeconds: Long? = null,
)

/**
 * The rules a stamp-sized window needs, none of which belong in an activity.
 */
object ChartWatch {

    /**
     * How often the window may be redrawn.
     *
     * One second. Android throttles a picture-in-picture surface anyway, and a price that ticks
     * forty times a second in a window the reader is not looking at is a composition running at
     * forty hertz behind whatever they *are* looking at — which is the one cost this feature could
     * plausibly impose on somebody and the reason to bound it here rather than hope.
     */
    const val MIN_PUBLISH_MILLIS = 1_000L

    /** How much of the tail the line draws. Beyond this the points are narrower than a pixel. */
    const val LINE_POINTS = 60

    /**
     * Android's own bounds on a picture-in-picture aspect ratio, as a decimal.
     *
     * Outside `1:2.39` … `2.39:1` the system refuses the request — and it refuses by throwing, so a
     * ratio computed from a window that happened to be very tall would crash the app on the way
     * into the mode rather than degrade.
     */
    const val MIN_ASPECT = 1.0 / 2.39
    const val MAX_ASPECT = 2.39

    /** The aspect the window asks for, clamped into what the system will accept. */
    fun aspectOf(width: Int, height: Int): Double {
        if (width <= 0 || height <= 0) return DEFAULT_ASPECT
        val raw = width.toDouble() / height.toDouble()
        if (!raw.isFinite()) return DEFAULT_ASPECT
        return raw.coerceIn(MIN_ASPECT, MAX_ASPECT)
    }

    /**
     * Sixteen by nine, which is what a chart wants and what the system opens at by default.
     *
     * Used where there is no measured window to ask about — the mode can be entered from a state
     * where nothing has been laid out yet, and a zero there would otherwise become a crash.
     */
    const val DEFAULT_ASPECT = 16.0 / 9.0

    /**
     * Whether a new snapshot is worth publishing.
     *
     * Two rules and they are both about not working for nothing:
     *
     *  * Not more often than [MIN_PUBLISH_MILLIS].
     *  * Not at all when nothing a reader could see has changed. An equal snapshot republished is a
     *    recomposition with no new information in it, and on this surface that is the whole budget.
     *
     * [lastAtMillis] null means nothing has been published yet, and the first snapshot always goes
     * through: a window that waited a second before drawing anything would open empty.
     */
    fun shouldPublish(
        previous: WatchSnapshot?,
        next: WatchSnapshot,
        lastAtMillis: Long?,
        nowMillis: Long,
    ): Boolean {
        if (previous == null || lastAtMillis == null) return true
        if (previous == next) return false
        return nowMillis - lastAtMillis >= MIN_PUBLISH_MILLIS
    }

    /**
     * Seconds until the forming bar closes, or null where there is nothing to count down to.
     *
     * Null rather than zero past the close, and that is the bug this function exists to not repeat:
     * a countdown is `close − now`, and the live tag once printed nothing at all when that went
     * negative because a bar minutes old has a negative one. Here a bar whose close has passed has
     * no countdown — the next snapshot carries the next bar — and the window draws no line rather
     * than a stuck «0:00».
     */
    fun countdownSeconds(barClosesAtEpochSeconds: Long?, nowEpochSeconds: Long): Int? {
        val closesAt = barClosesAtEpochSeconds ?: return null
        val left = closesAt - nowEpochSeconds
        return if (left in 1..MAX_COUNTDOWN_SECONDS) left.toInt() else null
    }

    /**
     * A week. Past this the number is not a countdown, it is a date — and a monthly bar's would be.
     */
    private const val MAX_COUNTDOWN_SECONDS = 7L * 24 * 60 * 60
}
