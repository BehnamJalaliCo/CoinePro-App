package com.coinepro.core.chart

/**
 * How much of a signal marker is drawn at a given zoom, and how big it is (run Σ, S2).
 *
 * ### Why the density rule is arithmetic and not a feeling
 *
 * A buy triangle with «خرید» under it is the right object at the zoom a reader studies a signal
 * from. At the zoom they *scan* from — a year of daily bars on a phone, two pixels a bar — the same
 * object is a rash: forty labels overlapping each other and the candles they are about, which is
 * worse than no labels because it also hides the price. Every terminal solves this with a threshold
 * and the thresholds are the whole of the design, so they are here, named, with the reasoning
 * attached, rather than as three magic numbers inside a draw pass.
 *
 * The unit is **density-independent pixels per bar**, not a bar count and not a zoom factor. A phone
 * and a tablet showing the same hundred bars are showing them at very different sizes, and the
 * question a label has to answer — «is there room for four characters between this candle and the
 * next» — is about points, not about bars.
 *
 * Pure, so the rule is a unit test rather than a screenshot, and so the web terminal inherits it
 * with the engine.
 */
object SignalMarkers {

    /**
     * At or above this many points a bar, the marker carries its word.
     *
     * Twelve. «خرید» at 11 sp is about twenty-six points wide, so at twelve points a bar a label
     * spans a little over two bars — which is the density at which consecutive signals are rare
     * enough that two labels in a row is the exception rather than the rule, and the overlap rule
     * below catches the exception.
     */
    const val LABEL_SPACING_DP: Float = 12f

    /**
     * And below this many points a bar, the markers thin out.
     *
     * Six points is where a triangle is wider than the candle it sits over. Past that the honest
     * thing is fewer marks rather than smaller ones: a mark that is one pixel is not a mark, it is
     * noise on the wick.
     */
    const val TRIANGLE_SPACING_DP: Float = 6f

    /**
     * How many bars a thinned marker speaks for.
     *
     * Ten. At the zoom this applies to — under six points a bar — ten bars is under sixty points,
     * about a fingertip, so one mark per window is one mark per place a reader could point at.
     */
    const val THIN_WINDOW: Int = 10

    /** The three sizes, in points, for a weak, an ordinary and a strong reading. */
    const val SIZE_WEAK_DP: Float = 6f
    const val SIZE_MEDIUM_DP: Float = 8f
    const val SIZE_STRONG_DP: Float = 10f

    /** Below this the event barely happened; at or above [STRONG] it went a long way past the line. */
    const val WEAK: Float = 0.34f
    const val STRONG: Float = 0.67f

    /**
     * What to draw for a study set to [style] at [barSpacingDp] points a bar.
     *
     * [MarkerStyle.OFF] is the reader's own answer and beats the zoom in both directions: a study
     * they have silenced is silent at every zoom, and one they have asked for triangles on never
     * grows a label however far they pinch in.
     */
    fun detailFor(barSpacingDp: Float, style: MarkerStyle = MarkerStyle.LABELS): MarkerDetail = when {
        style == MarkerStyle.OFF -> MarkerDetail.HIDDEN
        barSpacingDp >= LABEL_SPACING_DP && style == MarkerStyle.LABELS -> MarkerDetail.LABEL
        barSpacingDp >= TRIANGLE_SPACING_DP -> MarkerDetail.TRIANGLE
        else -> MarkerDetail.THINNED
    }

    /** The glyph's size in points for a reading of [strength]. See [SIZE_WEAK_DP]. */
    fun sizeDpFor(strength: Float): Float = when {
        !strength.isFinite() -> SIZE_MEDIUM_DP
        strength < WEAK -> SIZE_WEAK_DP
        strength < STRONG -> SIZE_MEDIUM_DP
        else -> SIZE_STRONG_DP
    }

    /**
     * The markers [view] can actually draw — run Υ, item 1.
     *
     * ### Why this is a step of its own, and why it comes first
     *
     * The renderer has always dropped a marker whose glyph would land off the plot; it did it one
     * marker at a time, at the end, after [thin] had already decided among the whole set. That order
     * is the wrong way round the moment the set stops being a handful of signals — a structure study
     * draws a mark a bar, and a reader flicking back through history pages the series towards
     * [ChartHistory.MAX_RESIDENT_BARS], so the set becomes every bar the chart holds and the frame
     * pays to thin fifty thousand marks in order to draw forty.
     *
     * Windowing first also gives the better answer at the edges: a ten-bar bucket straddling the
     * boundary used to be spoken for by a mark outside the plot, which was then dropped, so the
     * bucket drew nothing. Now the strongest mark a reader can *see* wins it.
     */
    fun onPlot(markers: List<ChartMarker>, view: ChartViewport): List<ChartMarker> {
        if (markers.isEmpty() || view.plotWidth <= 0f) return markers
        return markers.filter { mark ->
            val x = view.xOfTime(mark.time)
            x >= 0f && x <= view.plotWidth
        }
    }

    /**
     * One marker per [window] bars — the strongest of each — for a chart zoomed out past the point
     * where every mark can be its own object.
     *
     * ### Why the strongest and not the newest
     *
     * Because at this zoom the reader is looking for *where something happened*, and the answer they
     * want in a ten-bar smear is the loudest one. The newest is an arbitrary tiebreak dressed up as
     * a rule; on a study that fired four times in a week it would keep the one that happened to be
     * on Friday.
     *
     * Ties go to the newer mark, which is the only tiebreak that cannot put a stale mark over a
     * fresh one of equal weight.
     *
     * Markers whose time is not in [series] are kept as they are: the window is measured in bar
     * indices, and a mark this series has never heard of has no window to belong to.
     *
     * ### Why the bar index is searched for and not looked up — run Υ, item 1
     *
     * «چارت کُپ میکنه زمانی که به چپ و راست سوائپ میکنم.»
     *
     * This runs **inside the draw pass**, once a frame, for as long as the reader stays zoomed out
     * past [TRIANGLE_SPACING_DP] — which is the zoom somebody looking back through history is at.
     * It used to build a `HashMap` of every bar in the series to find each mark's index, and both of
     * its inputs grow with use: a structure study draws a mark a bar, and flicking back pages the
     * series towards [ChartHistory.MAX_RESIDENT_BARS]. Measured, that map cost five milliseconds and
     * near half a megabyte of garbage **per frame** on a fifty-thousand-bar chart carrying two
     * hundred marks — on the drawing thread, at a hundred and twenty frames a second. See
     * `SignalMarkerThinningCostTest`.
     *
     * [CandleSeries.time] is sorted, because `CandleSeries` refuses to be built out of order, so the
     * index is a binary search: no map, no boxing, nothing allocated per bar, and the answer is the
     * same one. The cost of a frame is now a function of the marks being thinned rather than of the
     * history the reader has paged in, which is the property it always should have had.
     */
    fun thin(markers: List<ChartMarker>, series: CandleSeries, window: Int = THIN_WINDOW): List<ChartMarker> {
        if (markers.size < 2 || series.isEmpty || window < 2) return markers
        val times = series.time
        val strongest = LinkedHashMap<Int, ChartMarker>()
        var orphans: MutableList<ChartMarker>? = null
        for (marker in markers) {
            val index = barAt(times, marker.time)
            if (index < 0) {
                // Allocated only when there is one. A chart whose marks all belong to its own bars
                // — every chart, in practice — pays nothing for the case that they might not.
                (orphans ?: mutableListOf<ChartMarker>().also { orphans = it }) += marker
                continue
            }
            // Keyed by the window *and* by which way the mark points: a buy and a sell inside the
            // same ten bars is a study changing its mind, which is exactly the thing a reader
            // zoomed out is looking for, and collapsing them to one would hide it.
            val key = (index / window) * 2 + if (marker.above) 1 else 0
            val held = strongest[key]
            if (held == null || marker.strength >= held.strength) strongest[key] = marker
        }
        val kept = orphans ?: return strongest.values.toList()
        return strongest.values.toList() + kept
    }

    /**
     * The index of the bar at [time] in the sorted [times], or −1 where this series has no such bar.
     *
     * Written out rather than taken from the standard library because `LongArray.binarySearch` is a
     * JVM extension and this file is common code: the web terminal compiles it too.
     */
    private fun barAt(times: LongArray, time: Long): Int {
        var low = 0
        var high = times.size - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val at = times[middle]
            when {
                at < time -> low = middle + 1
                at > time -> high = middle - 1
                else -> return middle
            }
        }
        return -1
    }
}

/** How much of a marker survives the zoom. See [SignalMarkers.detailFor]. */
enum class MarkerDetail {
    /** The glyph and its word. */
    LABEL,

    /** The glyph alone — there is no room for four characters between two candles. */
    TRIANGLE,

    /** The glyph alone, and one per [SignalMarkers.THIN_WINDOW] bars. */
    THINNED,

    /** Nothing, because the reader turned this study's marks off. */
    HIDDEN,
}

/**
 * What a reader has asked for on one study's marks (run Σ, S2).
 *
 * Three, and not a boolean, because the middle one is the setting people actually want: a reader who
 * knows their own chart has learned which way a triangle points and wants the candles back, and
 * telling them to choose between «a word on every signal» and «no signals at all» is a false choice.
 *
 * Per study rather than per chart, because the reason to silence one is that *that study* is noisy.
 */
enum class MarkerStyle {
    /** The default: a triangle with «خرید» or «فروش» beside it, where the zoom has room. */
    LABELS,

    /** Triangles only, at every zoom. */
    TRIANGLES,

    /** Nothing on the candles. The study still reads, still scores and still explains itself. */
    OFF,
}
