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
     */
    fun thin(markers: List<ChartMarker>, series: CandleSeries, window: Int = THIN_WINDOW): List<ChartMarker> {
        if (markers.size < 2 || series.isEmpty || window < 2) return markers
        val indexOf = HashMap<Long, Int>(series.size)
        for (index in 0 until series.size) indexOf[series.bars[index].t] = index
        val strongest = LinkedHashMap<Int, ChartMarker>()
        val orphans = mutableListOf<ChartMarker>()
        for (marker in markers) {
            val index = indexOf[marker.time]
            if (index == null) {
                orphans += marker
                continue
            }
            // Keyed by the window *and* by which way the mark points: a buy and a sell inside the
            // same ten bars is a study changing its mind, which is exactly the thing a reader
            // zoomed out is looking for, and collapsing them to one would hide it.
            val key = (index / window) * 2 + if (marker.above) 1 else 0
            val held = strongest[key]
            if (held == null || marker.strength >= held.strength) strongest[key] = marker
        }
        return strongest.values.toList() + orphans
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
