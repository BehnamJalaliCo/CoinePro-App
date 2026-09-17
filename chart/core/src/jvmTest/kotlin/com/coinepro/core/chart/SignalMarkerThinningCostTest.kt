package com.coinepro.core.chart

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **What thinning costs on a chart that has been used** (run Υ, item 1).
 *
 * «چارت کُپ میکنه زمانی که به چپ و راست سوائپ میکنم.»
 *
 * [SignalMarkers.thin] runs **inside the draw pass**, once a frame, whenever the bars are closer
 * together than [SignalMarkers.TRIANGLE_SPACING_DP] — which is to say whenever the reader has zoomed
 * out, which is what a reader looking through history has done. It took the whole marker list and
 * the whole series and built a `HashMap` of every bar in that series to look up each mark's index.
 *
 * Both of its inputs grow with use and neither grows with what is on screen. A structure study draws
 * a mark per bar; a reader flicking back through history pages the series towards the resident
 * ceiling of fifty thousand bars. So the map rebuilt every frame grows from a few hundred entries to
 * fifty thousand, on the drawing thread, at a hundred and twenty frames a second — and the chart
 * stops.
 *
 * This measures it. The figures are microseconds on this machine and the machine is not a phone; what
 * is about the code is the **ratio** between a fresh chart and a paged-in one, and the bound below is
 * on that ratio.
 */
class SignalMarkerThinningCostTest {

    private fun series(bars: Int): CandleSeries = CandleSeries(
        (0 until bars).map { index ->
            val base = 100.0 + (index % 97)
            Candle(t = 1_700_000_000L + index * 3_600L, o = base, h = base + 1, l = base - 1, c = base, v = 1.0)
        },
    )

    /** One mark per bar — the chop band, and what a structure study puts on a long chart. */
    private fun marksFor(series: CandleSeries): List<ChartMarker> = series.bars.mapIndexed { index, bar ->
        ChartMarker(
            time = bar.t,
            price = bar.l,
            above = index % 2 == 0,
            colour = 0xFF089981,
            glyph = MarkerGlyph.CIRCLE,
            strength = (index % 7) / 7f,
        )
    }

    /**
     * Microseconds for one frame's worth of thinning on a chart holding [bars] bars.
     *
     * [SCREENFUL] marks, whatever the history: that is what the draw pass now hands over, because
     * `drawMarkers` windows the list to the plot before thinning it. The series is still the whole
     * resident one — thinning reads it to find each mark's bar — so this is exactly the question
     * the report asks: does a frame get more expensive as the reader pages history in?
     */
    private fun cost(bars: Int, rounds: Int = 40): Long {
        val candles = series(bars)
        // Taken from the middle, so the binary search is not answering from the first probe.
        val marks = marksFor(candles).drop(bars / 2).take(SCREENFUL)
        // Warm first: the first call through this path pays for class loading and a cold JIT, and
        // neither is a fact about a chart.
        repeat(20) { SignalMarkers.thin(marks, candles) }
        var best = Long.MAX_VALUE
        repeat(rounds) {
            val started = System.nanoTime()
            SignalMarkers.thin(marks, candles)
            best = minOf(best, System.nanoTime() - started)
        }
        return best / 1_000
    }

    @Test
    fun `a frame of thinning does not get more expensive as the reader pages in history`() {
        val fresh = cost(FRESH)
        val paged = cost(PAGED)
        val ratio = paged.toDouble() / fresh.toDouble()
        println(
            "thin: ${fresh}µs on a fresh chart ($FRESH bars), ${paged}µs after the page-backs " +
                "($PAGED bars), both with $SCREENFUL marks on the plot, " +
                "ratio ${String.format(Locale.ROOT, "%.2f", ratio)}",
        )
        assertTrue("nothing was measured", fresh >= 0 && paged >= 0)
        // Twenty times the bars held, the same marks on screen. This runs once a frame on the
        // drawing thread, so its cost has to be a function of what is on the plot and not of what
        // the reader has walked back through. The old whole-series index made it the second, and a
        // chart that gets heavier the longer it is used is «کُپ میکنه».
        assertTrue(
            "one frame of thinning costs ${String.format(Locale.ROOT, "%.2f", ratio)}× as much after " +
                "${PAGED / FRESH}× the page-backs",
            paged <= maxOf(fresh * 2, fresh + 20),
        )
    }

    @Test
    fun `thinning allocates nothing per bar of a series it is only reading`() {
        // The map was `HashMap<Long, Int>(series.size)` — one boxed entry per bar in the chart,
        // built and thrown away every frame. Measured rather than asserted about the source: what
        // matters is the garbage, and the garbage is what the collector sees.
        val candles = series(PAGED)
        val marks = marksFor(candles).take(200)
        val runtime = Runtime.getRuntime()
        repeat(5) { SignalMarkers.thin(marks, candles) }
        System.gc()
        val before = runtime.totalMemory() - runtime.freeMemory()
        repeat(ROUNDS) { SignalMarkers.thin(marks, candles) }
        val after = runtime.totalMemory() - runtime.freeMemory()
        val perCall = (after - before) / ROUNDS
        println("thin: ${perCall / 1_024}KiB a call over a $PAGED-bar series with ${marks.size} marks")
        // Two hundred marks over fifty thousand bars. A frame's allocation belongs to the marks it
        // is thinning, not to the bars it is reading times: a kilobyte a mark is generous, and the
        // old whole-series index was four hundred times that.
        assertTrue(
            "one call allocated ${perCall / 1_024}KiB for ${marks.size} marks",
            perCall < marks.size * 1_024L,
        )
    }

    @Test
    fun `the window is taken before the thinning, and it is the plot's own`() {
        val candles = series(1_000)
        val marks = marksFor(candles)
        val view = ChartViewport(series = candles, barsPerView = 120, offset = 300, plotWidth = 900f)
        val visible = SignalMarkers.onPlot(marks, view)
        assertTrue("a thousand marks were handed over and ${visible.size} survived", visible.size < marks.size)
        assertTrue("and the ones on the plot did survive", visible.isNotEmpty())
        assertTrue(
            "every mark kept lands inside the plot",
            visible.all { view.xOfTime(it.time) in 0f..view.plotWidth },
        )
        assertTrue(
            "and every mark dropped lands outside it",
            marks.filterNot { it in visible }.none { view.xOfTime(it.time) in 0f..view.plotWidth },
        )
    }

    @Test
    fun `a viewport that has not been measured yet keeps every mark rather than none`() {
        // `plotWidth` is zero until the first draw pass. Filtering against it would answer «nothing
        // is on the plot» for the first frame of every chart, which is a blank chart on open.
        val candles = series(50)
        val marks = marksFor(candles)
        assertEquals(marks, SignalMarkers.onPlot(marks, ChartViewport(series = candles, barsPerView = 50)))
    }

    @Test
    fun `and it still answers exactly what it used to`() {
        // The cost is not allowed to be bought with a different answer. One mark per window per
        // direction, the strongest of each, orphans kept — the contract `SignalMarkersTest` pins.
        val candles = series(120)
        val marks = listOf(
            ChartMarker(candles.bars[0].t, 1.0, false, 0, MarkerGlyph.CIRCLE, strength = 0.2f),
            ChartMarker(candles.bars[3].t, 1.0, false, 0, MarkerGlyph.CIRCLE, strength = 0.9f),
            ChartMarker(candles.bars[9].t, 1.0, false, 0, MarkerGlyph.CIRCLE, strength = 0.5f),
            ChartMarker(candles.bars[4].t, 1.0, true, 0, MarkerGlyph.CIRCLE, strength = 0.1f),
            ChartMarker(candles.bars[15].t, 1.0, false, 0, MarkerGlyph.CIRCLE, strength = 0.3f),
            ChartMarker(99L, 1.0, false, 0, MarkerGlyph.CIRCLE, strength = 0.4f),
        )
        val thinned = SignalMarkers.thin(marks, candles)
        // Window 0 below: the 0.9 at bar 3 wins. Window 0 above: the only one. Window 1 below: the
        // 0.3 at bar 15. And the orphan, whose time this series has never heard of, is kept.
        assertEquals(4, thinned.size)
        assertTrue(thinned.any { it.strength == 0.9f })
        assertTrue(thinned.any { it.strength == 0.1f && it.above })
        assertTrue(thinned.any { it.strength == 0.3f })
        assertTrue("the orphan is kept as it is", thinned.any { it.time == 99L })
    }

    private companion object {
        const val FRESH = 2_500
        const val PAGED = 50_000
        const val ROUNDS = 200

        /** Marks a phone can have on the plot at the zoom that thins them — a screenful. */
        const val SCREENFUL = 200
    }
}
