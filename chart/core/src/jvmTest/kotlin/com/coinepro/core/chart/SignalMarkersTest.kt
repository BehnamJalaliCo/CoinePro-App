package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a signal marker becomes as the chart is zoomed (run Σ, S2).
 *
 * The label is the feature — «برچسب خرید/فروش روی فلش‌ها» — and the density rule is what stops it
 * from being a defect at the other end of the zoom range. A word under every triangle on a year of
 * daily bars is forty words overlapping each other and the candles they are about, which hides more
 * than it says.
 *
 * The thresholds are in points a bar and not in bars, because a phone and a tablet showing the same
 * hundred bars are showing them at very different sizes and the question a label has to answer is
 * «is there room for four characters here».
 */
class SignalMarkersTest {

    private fun series(bars: Int): CandleSeries = CandleSeries(
        (0 until bars).map { index ->
            val base = 100.0 + index
            Candle(t = 1_700_000_000L + index * 3_600L, o = base, h = base + 1, l = base - 1, c = base, v = 1.0)
        },
    )

    private fun mark(bar: Int, strength: Float = 1f, above: Boolean = false, series: CandleSeries) = ChartMarker(
        time = series.bars[bar].t,
        price = series.bars[bar].l,
        above = above,
        colour = 0xFF089981,
        glyph = if (above) MarkerGlyph.ARROW_DOWN else MarkerGlyph.ARROW_UP,
        label = if (above) "فروش" else "خرید",
        strength = strength,
    )

    // ── the density rule ─────────────────────────────────────────────────────────────────────

    @Test
    fun `a chart with room shows the word`() {
        assertEquals(MarkerDetail.LABEL, SignalMarkers.detailFor(barSpacingDp = 16f))
        assertEquals(MarkerDetail.LABEL, SignalMarkers.detailFor(barSpacingDp = 12f))
    }

    @Test
    fun `a chart with less room shows the triangle alone`() {
        assertEquals(MarkerDetail.TRIANGLE, SignalMarkers.detailFor(barSpacingDp = 11.9f))
        assertEquals(MarkerDetail.TRIANGLE, SignalMarkers.detailFor(barSpacingDp = 8f))
        assertEquals(MarkerDetail.TRIANGLE, SignalMarkers.detailFor(barSpacingDp = 6f))
    }

    @Test
    fun `a chart with no room at all thins the marks out`() {
        assertEquals(MarkerDetail.THINNED, SignalMarkers.detailFor(barSpacingDp = 5.9f))
        assertEquals(MarkerDetail.THINNED, SignalMarkers.detailFor(barSpacingDp = 4f))
        assertEquals(MarkerDetail.THINNED, SignalMarkers.detailFor(barSpacingDp = 0.5f))
    }

    @Test
    fun `the reader's own answer beats the zoom in both directions`() {
        // Off is off at every zoom…
        for (spacing in listOf(0.5f, 4f, 8f, 16f, 50f)) {
            assertEquals(MarkerDetail.HIDDEN, SignalMarkers.detailFor(spacing, MarkerStyle.OFF))
        }
        // …and a reader who asked for triangles never grows a label however far they pinch in.
        assertEquals(MarkerDetail.TRIANGLE, SignalMarkers.detailFor(50f, MarkerStyle.TRIANGLES))
        assertEquals(MarkerDetail.TRIANGLE, SignalMarkers.detailFor(12f, MarkerStyle.TRIANGLES))
        // The thinning still applies to them, because that rule is about the glass and not about
        // the word.
        assertEquals(MarkerDetail.THINNED, SignalMarkers.detailFor(3f, MarkerStyle.TRIANGLES))
    }

    @Test
    fun `the rule is monotonic — zooming in never shows less`() {
        // The property, over the whole range, rather than the five points somebody wrote down. A
        // threshold entered the wrong way round would pass every case above and fail here.
        val order = listOf(MarkerDetail.THINNED, MarkerDetail.TRIANGLE, MarkerDetail.LABEL)
        var lowest = 0
        var spacing = 0.5f
        while (spacing <= 50f) {
            val rank = order.indexOf(SignalMarkers.detailFor(spacing))
            assertTrue("detail went backwards at $spacing dp", rank >= lowest)
            lowest = rank
            spacing += 0.25f
        }
    }

    // ── the size ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a louder reading is a bigger mark`() {
        assertEquals(SignalMarkers.SIZE_WEAK_DP, SignalMarkers.sizeDpFor(0.1f), 0f)
        assertEquals(SignalMarkers.SIZE_MEDIUM_DP, SignalMarkers.sizeDpFor(0.5f), 0f)
        assertEquals(SignalMarkers.SIZE_STRONG_DP, SignalMarkers.sizeDpFor(0.9f), 0f)
        assertEquals(SignalMarkers.SIZE_STRONG_DP, SignalMarkers.sizeDpFor(1f), 0f)
    }

    @Test
    fun `a strength nobody set is the middle size`() {
        // Every marker that is not a signal — a swing, a pattern, a chop zone — carries the default
        // of one and must keep the size it has always had.
        assertEquals(SignalMarkers.SIZE_STRONG_DP, SignalMarkers.sizeDpFor(1f), 0f)
        assertEquals(SignalMarkers.SIZE_MEDIUM_DP, SignalMarkers.sizeDpFor(Float.NaN), 0f)
    }

    // ── the thinning ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `one mark per window, and it is the strongest`() {
        val bars = series(60)
        val markers = listOf(
            mark(1, strength = 0.2f, series = bars),
            mark(4, strength = 0.9f, series = bars),
            mark(7, strength = 0.5f, series = bars),
            mark(21, strength = 0.3f, series = bars),
        )
        val thinned = SignalMarkers.thin(markers, bars, window = 10)
        assertEquals("two windows had marks in them", 2, thinned.size)
        assertTrue("the loudest mark of the first window was dropped", thinned.any { it.strength == 0.9f })
        assertTrue("the second window's mark was dropped", thinned.any { it.strength == 0.3f })
    }

    @Test
    fun `a study changing its mind inside one window keeps both marks`() {
        // A buy and a sell ten bars apart is the thing a reader zoomed out is looking for, and
        // collapsing them to whichever was louder would hide it.
        val bars = series(60)
        val markers = listOf(
            mark(2, strength = 0.9f, above = false, series = bars),
            mark(6, strength = 0.4f, above = true, series = bars),
        )
        val thinned = SignalMarkers.thin(markers, bars, window = 10)
        assertEquals(2, thinned.size)
        assertTrue(thinned.any { !it.above })
        assertTrue(thinned.any { it.above })
    }

    @Test
    fun `ties go to the newer mark`() {
        val bars = series(60)
        val markers = listOf(
            mark(1, strength = 0.5f, series = bars),
            mark(8, strength = 0.5f, series = bars),
        )
        val thinned = SignalMarkers.thin(markers, bars, window = 10)
        assertEquals(1, thinned.size)
        assertEquals(bars.bars[8].t, thinned.first().time)
    }

    @Test
    fun `a mark this series has never heard of is kept as it is`() {
        // The window is measured in bar indices, so a marker on a timestamp that is not in the
        // series has no window to belong to — and dropping it would be the thinning quietly
        // deleting data rather than summarising it.
        val bars = series(60)
        val orphan = mark(0, series = bars).copy(time = 1L)
        val thinned = SignalMarkers.thin(listOf(mark(3, series = bars), orphan), bars, window = 10)
        assertEquals(2, thinned.size)
        assertTrue(thinned.any { it.time == 1L })
    }

    @Test
    fun `a single mark, an empty list and a tiny window are all left alone`() {
        val bars = series(60)
        val one = listOf(mark(3, series = bars))
        assertEquals(one, SignalMarkers.thin(one, bars))
        assertEquals(emptyList<ChartMarker>(), SignalMarkers.thin(emptyList(), bars))
        val two = listOf(mark(3, series = bars), mark(4, series = bars))
        assertEquals(two, SignalMarkers.thin(two, bars, window = 1))
    }

    // ── the word ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the word is a verdict, in both languages`() {
        assertEquals("خرید", TradeSide.BUY.action(english = false))
        assertEquals("فروش", TradeSide.SELL.action(english = false))
        assertEquals("Buy", TradeSide.BUY.action(english = true))
        assertEquals("Sell", TradeSide.SELL.action(english = true))
    }

    @Test
    fun `neither language leaks into the other`() {
        for (side in TradeSide.entries) {
            assertTrue(
                "a Persian word reached the English label: ${side.action(english = true)}",
                side.action(english = true).none { it in '؀'..'ۿ' },
            )
            assertTrue(
                "an English word reached the Persian label: ${side.action(english = false)}",
                side.action(english = false).none { it in 'A'..'z' },
            )
        }
    }
}
