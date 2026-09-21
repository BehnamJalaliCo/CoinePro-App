package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a new series is allowed to do to a reader who is panning.
 *
 * «چارت اصلا روش اسکرول میکنم به عقب برمیگرده دیگه به جلو برنمیگرده» — panning back was sticky and
 * panning forward made no progress at all. The saved pan position is written from an effect that
 * runs *after* composition and was read from a block that runs *during* it, so a series replacement
 * landing in the same frame as a drag restored the reader to where they had been a frame earlier.
 * Backwards that is behind them and they still crept along; forwards it is also behind them, which
 * is exactly the direction they were trying to leave, so every frame undid the drag.
 *
 * These are the two halves of the rule [seedViewport] now enforces: the saved trio seeds a *fresh*
 * composition and nothing else, and every series after that goes through [ChartViewport.withSeries]
 * alone.
 */
class ChartPanRestoreTest {

    private fun bars(from: Int, count: Int) = (from until from + count).map { index ->
        val base = 100.0 + index * 0.1
        Candle(START + index * HOUR, base, base + 1, base - 1, base + 0.5)
    }

    private val series = CandleSeries(bars(from = 0, count = 200))

    private fun seed(
        current: ChartViewport,
        series: CandleSeries,
        seeded: Boolean,
        savedOffset: Int,
        savedZoom: Int = ChartViewport.DEFAULT_BARS_PER_VIEW,
        savedPriceZoom: Float = 1f,
        restAtEdge: Boolean = true,
    ) = seedViewport(
        current = current,
        series = series,
        seeded = seeded,
        savedZoom = savedZoom,
        savedOffset = savedOffset,
        savedPriceZoom = savedPriceZoom,
        restAtEdge = restAtEdge,
    )

    /** A chart that has already been composed once, sitting where the reader left it. */
    private fun composed() = seed(
        current = ChartViewport(series).sized(width = 800f, height = 300f),
        series = series,
        seeded = false,
        savedOffset = UNSET_OFFSET,
    )

    // ── the pan itself ────────────────────────────────────────────────────────────────

    @Test
    fun `panning forward survives a page of history landing in the same frame`() {
        var view = composed().atOffset(60)
        // What the effect will eventually write — one frame behind the reader's finger.
        val stale = view.offset
        view = view.atOffset(40)

        // The archive answers from disk before the effect has run, so the series is replaced with a
        // prepended one on the very frame the reader is dragging forward.
        val prepended = CandleSeries(bars(from = -100, count = 100) + series.bars)
        view = seed(view, prepended, seeded = true, savedOffset = stale)

        assertTrue("the reader's forward pan must survive the prepend", view.offset < stale)
        assertEquals(40, view.offset)
    }

    @Test
    fun `the stale saved offset is exactly what used to undo the drag`() {
        // The same frame under the old rule, kept as an assertion so the guard cannot be dropped
        // without something failing: re-seeding on every series change restores the reader to a
        // position they had already left.
        var view = composed().atOffset(60)
        val stale = view.offset
        view = view.atOffset(40)

        val prepended = CandleSeries(bars(from = -100, count = 100) + series.bars)
        val regressed = seed(view, prepended, seeded = false, savedOffset = stale)

        assertEquals(stale, regressed.offset)
    }

    @Test
    fun `a reader panning forward through a run of prepends makes progress on every frame`() {
        var view = composed().atOffset(120)
        var oldest = 0
        var all = series.bars
        var stale = view.offset

        repeat(5) {
            view = view.atOffset(view.offset - 10)
            oldest -= 20
            all = bars(from = oldest, count = 20) + all
            view = seed(view, CandleSeries(all), seeded = true, savedOffset = stale)
            stale = view.offset
        }

        assertEquals(70, view.offset)
    }

    @Test
    fun `a prepend on its own moves nobody`() {
        val view = composed().atOffset(90)
        val prepended = CandleSeries(bars(from = -100, count = 100) + series.bars)

        assertEquals(90, seed(view, prepended, seeded = true, savedOffset = 90).offset)
    }

    @Test
    fun `a reader at the live edge still follows new bars`() {
        val view = composed()
        val appended = CandleSeries(series.bars + bars(from = 200, count = 3))

        val followed = seed(view, appended, seeded = true, savedOffset = view.offset)
        assertEquals(view.offset, followed.offset)
        assertTrue("still at the live edge", followed.isAtLiveEdge)
    }

    // ── and the restore the saved trio is actually for ────────────────────────────────

    @Test
    fun `a fresh composition restores the saved offset, zoom and price zoom`() {
        val restored = seed(
            current = ChartViewport(series).sized(width = 800f, height = 300f),
            series = series,
            seeded = false,
            savedOffset = 25,
            savedZoom = 40,
            savedPriceZoom = 1.5f,
        )

        assertEquals(25, restored.offset)
        assertEquals(40, restored.barsPerView)
        assertEquals(1.5f, restored.priceZoom, 0.0001f)
    }

    @Test
    fun `a fresh composition nobody has panned opens at rest, with air at the live edge`() {
        val opened = seed(
            current = ChartViewport(series).sized(width = 800f, height = 300f),
            series = series,
            seeded = false,
            savedOffset = UNSET_OFFSET,
        )

        assertEquals(opened.restingOffset, opened.offset)
        assertTrue("the resting position keeps the newest bar off the axis", opened.offset < 0)
    }

    @Test
    fun `a thumbnail has no gutter to breathe into and opens glued to the edge`() {
        val opened = seed(
            current = ChartViewport(series).sized(width = 200f, height = 60f),
            series = series,
            seeded = false,
            savedOffset = UNSET_OFFSET,
            restAtEdge = false,
        )

        assertEquals(0, opened.offset)
    }

    private companion object {
        const val START = 1_700_000_000L
        const val HOUR = 3_600L
    }
}
