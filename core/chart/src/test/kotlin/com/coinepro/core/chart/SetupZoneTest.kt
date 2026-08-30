package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a setup's shading is allowed to be, which is the complaint this file was written for.
 *
 * «در زمانی که پوزیشن باز می‌شود باید حد سود و ضرر از کندلی که پوزیشن باز شده ستاپ چیده بشه و رنگ
 * سبز و قرمز بذاری، نه کل چارت رو» — the zones ran the full width of every chart that drew one, so a
 * reader saw the whole visible history painted green above the entry and red below it and read that
 * as a position that had been open across all of it.
 *
 * The geometry is asserted here rather than in the draw pass because the draw pass cannot be: a
 * `DrawScope` needs a device. Everything the renderer decides about *where* now lives in
 * [setupSpan] and [setupBands], and those two are what these tests hold.
 */
class SetupZoneTest {

    private val series = CandleSeries(
        (0 until BARS).map { index ->
            val base = 100.0 + index * 0.1
            Candle(START + index * HOUR, base, base + 1, base - 1, base + 0.5)
        },
    )

    /**
     * Eighty bars of a two-hundred-bar series, glued to the right edge, eight hundred pixels wide.
     *
     * Chosen so every number in these tests is exact: eighty slots into eight hundred pixels is ten
     * pixels a bar, and the first visible bar is index 120.
     */
    private val view = ChartViewport(series).sized(width = PLOT, height = 300f)

    private fun long(issuedAt: Long?, closedAt: Long? = null) = SignalOverlay(
        entry = 110.0,
        stopLoss = 108.0,
        takeProfits = listOf(114.0, 118.0),
        isLong = true,
        issuedAt = issuedAt,
        closedAt = closedAt,
    )

    // ── the anchor ────────────────────────────────────────────────────────────────────

    @Test
    fun `the zone starts at the entry bar and nothing is painted left of it`() {
        val span = setupSpan(view, long(issuedAt = series.time[150]))

        // The entry candle is inside its own zone: the left edge is the leading edge of that bar's
        // slot, not its centre.
        assertEquals(view.xOf(150) - view.barWidth / 2f, span.left, TOLERANCE)
        assertTrue("the zone must not reach the left edge of the plot", span.left > 0f)
        assertTrue(span.anchored)
    }

    @Test
    fun `an open position runs to the right edge, blank slots included`() {
        val span = setupSpan(view, long(issuedAt = series.time[150]))

        assertEquals(PLOT, span.right, TOLERANCE)
    }

    @Test
    fun `the entry bar is marked, at the bar itself`() {
        val span = setupSpan(view, long(issuedAt = series.time[150]))

        assertEquals(view.xOf(150), span.entryX!!, TOLERANCE)
    }

    // ── a position that has closed ────────────────────────────────────────────────────

    @Test
    fun `a closed position stops at its closing bar rather than at the live edge`() {
        val span = setupSpan(
            view,
            long(issuedAt = series.time[150], closedAt = series.time[170]),
        )

        // The closing candle is inside the zone the same way the entry candle is.
        assertEquals(view.xOf(170) + view.barWidth / 2f, span.right, TOLERANCE)
        assertTrue("a closed setup must not reach the live edge", span.right < PLOT)
    }

    @Test
    fun `a close before its own entry paints nothing at all`() {
        val span = setupSpan(
            view,
            long(issuedAt = series.time[170], closedAt = series.time[150]),
        )

        assertTrue(span.isEmpty)
    }

    // ── a short ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a long puts the risk below the entry and the reward above it`() {
        val bands = setupBands(
            SignalOverlay(entry = 100.0, stopLoss = 96.0, takeProfits = listOf(108.0), isLong = true),
        )

        val risk = bands.single { it.role == SetupBandRole.RISK }
        val reward = bands.single { it.role == SetupBandRole.REWARD }
        assertTrue(risk.to < risk.from)
        assertTrue(reward.to > reward.from)
    }

    @Test
    fun `a short inverts the two sides — the sell colour goes above the entry`() {
        val bands = setupBands(
            SignalOverlay(entry = 100.0, stopLoss = 104.0, takeProfits = listOf(92.0), isLong = false),
        )

        val risk = bands.single { it.role == SetupBandRole.RISK }
        val reward = bands.single { it.role == SetupBandRole.REWARD }
        assertTrue("a short's stop is above its entry, so the risk band is too", risk.to > risk.from)
        assertTrue("and its target is below, so the reward band is", reward.to < reward.from)
    }

    @Test
    fun `only the first target is filled, whatever a signal names`() {
        val bands = setupBands(
            SignalOverlay(
                entry = 100.0,
                stopLoss = 96.0,
                takeProfits = listOf(108.0, 116.0, 124.0),
                isLong = true,
            ),
        )

        assertEquals(2, bands.size)
        assertEquals(108.0, bands.single { it.role == SetupBandRole.REWARD }.to, 0.0)
    }

    @Test
    fun `a stop sitting on the entry is no band at all`() {
        val bands = setupBands(
            SignalOverlay(entry = 100.0, stopLoss = 100.0, takeProfits = emptyList(), isLong = true),
        )

        assertTrue(bands.isEmpty())
    }

    @Test
    fun `the position tool asks the same question and gets the same answer`() {
        // «موقعیت خرید/فروش» draws its own band from three loose prices. It is a separate renderer
        // — its span is the two points the reader tapped — but it must not have a second opinion
        // about which side of the entry the red goes on.
        val short = setupBands(entry = 100.0, stopLoss = 104.0, target = 92.0)

        assertEquals(SetupBandRole.RISK, short.first().role)
        assertTrue(short.first().to > short.first().from)
        assertEquals(
            setupBands(
                SignalOverlay(entry = 100.0, stopLoss = 104.0, takeProfits = listOf(92.0), isLong = false),
            ),
            short,
        )
    }

    @Test
    fun `a position tool drawing with no target yet is still one band`() {
        assertEquals(
            listOf(SetupBand(SetupBandRole.RISK, 100.0, 96.0)),
            setupBands(entry = 100.0, stopLoss = 96.0, target = null),
        )
    }

    // ── the awkward viewports ─────────────────────────────────────────────────────────

    @Test
    fun `an entry scrolled off the left still draws, from the left edge, with no mark`() {
        // Bar 10 is well before the first visible bar, which is 120.
        val span = setupSpan(view, long(issuedAt = series.time[10]))

        assertEquals(0f, span.left, TOLERANCE)
        assertEquals(PLOT, span.right, TOLERANCE)
        assertTrue(span.anchored)
        assertNull("there is no bar on screen to mark", span.entryX)
    }

    @Test
    fun `a reader panned back before the setup existed sees none of it`() {
        // Far enough back that bar 150 is off the right-hand edge entirely.
        val panned = view.atOffset(150)
        val span = setupSpan(panned, long(issuedAt = series.time[150]))

        assertTrue(span.isEmpty)
    }

    @Test
    fun `a setup issued after the newest bar lands in the air at the live edge`() {
        val resting = view.atRest()
        val span = setupSpan(resting, long(issuedAt = series.time.last() + HOUR / 2))

        assertTrue(
            "it begins right of where the newest bar's own slot begins",
            span.left > resting.xOf(resting.lastVisible) - resting.barWidth / 2f,
        )
        assertTrue("and still has somewhere to be drawn", span.right > span.left)
    }

    // ── the honest fallback ───────────────────────────────────────────────────────────

    @Test
    fun `a setup with no entry bar known is not anchored, so nothing is shaded`() {
        val span = setupSpan(view, long(issuedAt = null))

        assertFalse("no fill may be drawn for a setup whose start nobody recorded", span.anchored)
        // The lines still span the plot: a price level is true whenever it is read. It is the
        // shading that makes a claim about time, and that is what is withheld.
        assertEquals(0f, span.left, TOLERANCE)
        assertEquals(PLOT, span.right, TOLERANCE)
        assertNull(span.entryX)
    }

    @Test
    fun `an unmeasured chart asks for nothing`() {
        val span = setupSpan(ChartViewport(series), long(issuedAt = series.time[150]))

        assertTrue(span.isEmpty)
    }

    @Test
    fun `an empty series is not anchored`() {
        val empty = ChartViewport(CandleSeries(emptyList())).sized(PLOT, 300f)

        assertFalse(setupSpan(empty, long(issuedAt = START)).anchored)
    }

    private companion object {
        const val BARS = 200
        const val PLOT = 800f
        const val START = 1_700_000_000L
        const val HOUR = 3_600L
        const val TOLERANCE = 0.01f
    }
}
