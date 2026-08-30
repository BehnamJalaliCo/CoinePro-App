package com.coinepro.core.chart

/**
 * How much history the chart may hold in memory at once, and which part of it.
 *
 * ### The problem a deep archive creates
 *
 * A chart that can page back to fifty thousand bars is not a chart that should hold fifty thousand
 * bars. Nothing about the drawing gets slower — [ChartViewport] already works between
 * `firstVisible` and `lastVisible`, so a frame costs what is on screen and not what is loaded — but
 * three other things do, and all three are per *series* rather than per visible bar:
 *
 *  * **Memory.** A [Candle] with its boxed volume is around eighty bytes, and [CandleSeries] builds
 *    six parallel columns beside it at another forty-eight. Call it a hundred and twenty-eight
 *    bytes a bar: fifty thousand is six and a half megabytes for one chart, and this app keeps up
 *    to eight chart controllers alive at once so a reader flipping between symbols does not lose
 *    their zoom. Eight of those is fifty megabytes of candles, which is not a budget a mid-range
 *    Android has spare.
 *  * **Indicators.** Several are written as a lookback loop per bar — a period-200 average is two
 *    hundred additions at every index — so they cost bars × period, and they are recomputed
 *    whenever the series is replaced. Ten million operations is a visible stall; two million is
 *    not.
 *  * **Chart types.** Heikin-Ashi, Renko and the rest rebuild every bar from the whole series each
 *    time it changes, which is linear in what is loaded rather than in what is shown.
 *
 * ### The answer
 *
 * The archive keeps the depth; the chart keeps a window of it. [MAX_RESIDENT_BARS] bars around
 * wherever the reader is looking, with [HEADROOM_BARS] spare on each side so that panning does not
 * re-slice on every frame, and the rest stays on disk until they pan far enough to want it. A
 * reader panning back past the edge of the resident window is a re-slice, not a fetch — the bars
 * are already held — which is why the headroom exists and why it is generous.
 *
 * Below the ceiling nothing happens at all: [resident] returns the same object it was given, so an
 * ordinary three-hundred-bar chart allocates nothing and compares equal frame to frame.
 */
object ChartHistory {

    /**
     * How many bars one chart may hold: twelve thousand.
     *
     * About a megabyte and a half with the columns, so eight live controllers come to twelve
     * megabytes — the number that has to be affordable, since that is what this app actually keeps.
     * It is also forty times a phone's widest zoom, so a reader who pans without re-zooming crosses
     * it in something like a hundred screenfuls.
     */
    const val MAX_RESIDENT_BARS = 12_000

    /**
     * How close the viewport may come to the edge of what is resident before it is worth re-slicing:
     * two thousand bars.
     *
     * The trigger, not the size — [residentRange] always spends the whole budget. It exists because
     * re-slicing when the reader has already reached the edge is too late: the frame that needs the
     * next bars is the frame that has to wait for them. Two thousand is a dozen screenfuls at
     * ordinary zoom, so the slice is rebuilt once, well before it is needed, rather than on every
     * frame of a drag.
     */
    const val HEADROOM_BARS = 2_000

    /** Roughly what [bars] loaded bars cost in memory, columns included. See the note above. */
    const val BYTES_PER_BAR = 128

    /** Roughly what a series of [bars] occupies. An order-of-magnitude answer, deliberately. */
    fun estimatedBytes(bars: Int): Long = bars.toLong() * BYTES_PER_BAR

    /**
     * Which slice of a [total]-bar history the chart should hold, given what is on screen.
     *
     * Index arithmetic and nothing else, so a caller can ask before it has built anything — which
     * is the point on the path that matters: bars come out of the archive as wire rows, and mapping
     * fifty thousand of them into [Candle] objects to then throw most away is the allocation this
     * is meant to prevent.
     *
     * Null when there is nothing to hold. The range is inclusive at both ends, is always at most
     * [maxBars] wide, and is anchored to the **newest** end when the window says nothing useful —
     * a chart with no viewport yet is a chart about to open at the live edge.
     */
    fun residentRange(
        total: Int,
        window: BarWindow = BarWindow.WHOLE_SERIES,
        maxBars: Int = MAX_RESIDENT_BARS,
    ): IntRange? {
        if (total <= 0) return null
        val cap = maxBars.coerceAtLeast(1)
        if (total <= cap) return 0 until total
        val visible = window.clampedTo(total)
        // No viewport, or one so wide that the whole budget is already on screen: hold the newest
        // `cap` bars, which is where a chart opens and where it sits until somebody pans.
        if (visible == null || visible.last - visible.first + 1 >= cap) {
            return (total - cap) until total
        }
        // The whole budget, centred on what is visible. Spending less would mean re-slicing sooner
        // for no saving — the memory is committed by the ceiling either way.
        val spare = cap - (visible.last - visible.first + 1)
        var first = visible.first - spare / 2
        var last = first + cap - 1
        // Slid rather than clipped at either end, so a chart at the live edge still carries a full
        // budget of history behind it instead of a window that ran out of room.
        if (first < 0) {
            first = 0
            last = cap - 1
        }
        if (last > total - 1) {
            last = total - 1
            first = total - cap
        }
        return first..last
    }

    /**
     * Whether the reader has panned close enough to the edge of [range] to be worth re-slicing.
     *
     * Asked rather than assumed, because the alternative is re-slicing on every frame of a drag —
     * an allocation and a full indicator recompute per frame, which is the stall this file exists
     * to prevent. False when the resident window already reaches the end it is being asked about:
     * there is nothing beyond it to slide towards.
     */
    fun needsReslice(
        range: IntRange?,
        window: BarWindow,
        total: Int,
        headroom: Int = HEADROOM_BARS,
    ): Boolean {
        if (total <= 0) return false
        if (range == null) return true
        val visible = window.clampedTo(total) ?: return false
        val nearOldEdge = range.first > 0 && visible.first - range.first < headroom
        val nearLiveEdge = range.last < total - 1 && range.last - visible.last < headroom
        return nearOldEdge || nearLiveEdge
    }

    /**
     * How many bars of the archive sit **before** [range] — history held but not resident.
     *
     * A separate function rather than arithmetic at the call site because the answer is easy to get
     * backwards, and a chart that tells a reader it has three thousand more bars than it does is
     * worse than one that says nothing.
     */
    fun withheldBefore(count: Int, range: IntRange?): Int =
        if (range == null) count.coerceAtLeast(0) else range.first.coerceIn(0, count.coerceAtLeast(0))
}

/**
 * This series bounded to what the chart may hold, around [window].
 *
 * Returns **the same object** when it already fits, which is the common case and the reason this is
 * safe to call unconditionally: an ordinary chart pays one comparison, and `ChartDerived`'s carried
 * value — which is keyed on series identity — is not invalidated by asking.
 *
 * The slice is copied rather than handed back as a `subList` view. A view keeps its whole backing
 * list alive, so slicing twelve thousand bars out of fifty thousand and holding the view would free
 * nothing at all, which is the one thing this must not do.
 */
fun CandleSeries.resident(
    window: BarWindow = BarWindow.WHOLE_SERIES,
    maxBars: Int = ChartHistory.MAX_RESIDENT_BARS,
): CandleSeries {
    if (size <= maxBars.coerceAtLeast(1)) return this
    val range = ChartHistory.residentRange(size, window, maxBars) ?: return CandleSeries.EMPTY
    return CandleSeries(ArrayList(bars.subList(range.first, range.last + 1)))
}
