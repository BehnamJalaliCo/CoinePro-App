package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure half of the second wave of drawing tools: the label budget, the magnet's binding, the
 * eraser's split, the keep-drawing latch, and the refusal to draw a volume tool on a feed that
 * reports no volume.
 *
 * Everything asserted here is arithmetic or state, and none of it needs a canvas. What a Gann square
 * *looks like* is a screenshot's job; whether it prints seventeen ratios on top of each other is
 * this file's, and that is the one of the two that can go wrong silently.
 */
class DrawingWaveTwoTest {

    private val polylineTool = DrawingTools["polyline"]!!
    private val pathTool = DrawingTools["path"]!!
    private val trend = DrawingTools["trend"]!!

    /** Ten bars an hour apart. Bar *n* is open 103+n, high 108+n, low 95+n, close 104+n. */
    private val series = CandleSeries(
        (0 until 10).map { index ->
            Candle(
                t = 1_700_000_000L + index * 3600,
                o = 103.0 + index,
                h = 108.0 + index,
                l = 95.0 + index,
                c = 104.0 + index,
                v = 1_000.0 + index * 10,
            )
        },
    )

    /** The same bars with no volume column at all, which is what the MT5 forex feed sends. */
    private val silent = CandleSeries(series.bars.map { it.copy(v = null) })

    private fun point(time: Long, price: Double) = ChartPoint(time, price)

    // ── label collisions ──────────────────────────────────────────────────────────────

    @Test
    fun `a label yields to one already sitting within the gap in both axes`() {
        val lane = LabelLane(gapX = 30f, gapY = 10f)
        assertTrue("the first label always fits", lane.claim(100f, 200f))
        assertFalse("four pixels below the last one is a smear", lane.claim(100f, 204f))
        assertTrue("far enough down to read", lane.claim(100f, 215f))
        assertEquals(2, lane.placed)
    }

    @Test
    fun `labels at the same height but far apart along time all survive`() {
        // The time-cycles tool puts a dozen numbers along the top of the plot at one height. A rule
        // that only looked at the vertical gap would suppress every one of them but the first.
        val lane = LabelLane(gapX = 30f, gapY = 10f)
        assertTrue(lane.claim(0f, 0f))
        assertTrue(lane.claim(40f, 0f))
        assertTrue(lane.claim(80f, 0f))
        assertEquals(3, lane.placed)
    }

    @Test
    fun `a suppressed label does not itself suppress the next one`() {
        // A refused claim was never drawn and occupies nothing. Recording it would let one dropped
        // label cast a shadow that drops a second that would have fitted.
        val lane = LabelLane(gapX = 10f, gapY = 10f)
        assertTrue(lane.claim(0f, 0f))
        assertFalse(lane.claim(5f, 0f))
        assertTrue(lane.claim(12f, 0f))
        assertEquals(2, lane.placed)
    }

    @Test
    fun `a Gann square's seventeen ratios are cut down rather than drawn on top of each other`() {
        val lane = LabelLane(gapX = 40f, gapY = 12f)
        val drawn = (0 until 17).count { row -> lane.claim(200f, row * 4f) }
        assertTrue("unmanaged, all seventeen would draw", drawn < 17)
        assertEquals(6, drawn)
    }

    // ── the magnet ────────────────────────────────────────────────────────────────────

    @Test
    fun `a strong magnet binds to the channel it chose, not to the price it found`() {
        // Bar 3 is open 106, high 111, low 98, close 107. A tap at 110.4 is nearest the high.
        val snap = DrawingActions.snap(
            point(1_700_000_000L + 3 * 3600 + 200, 110.4),
            series,
            MagnetMode.STRONG,
        )
        assertEquals(PriceChannel.HIGH, snap.channel)
        assertEquals(111.0, snap.point.price, 1e-9)
        assertEquals(1_700_000_000L + 3 * 3600, snap.point.time)
    }

    @Test
    fun `a strong magnet snaps however far away the finger was`() {
        val snap = DrawingActions.snap(point(1_700_000_000L, 4_000.0), series, MagnetMode.STRONG)
        assertEquals(PriceChannel.HIGH, snap.channel)
        assertEquals(108.0, snap.point.price, 1e-9)
    }

    @Test
    fun `a weak magnet leaves a tap in open space exactly where it landed`() {
        // Bar 0 is 103/108/95/104, so a quarter of its 13-point range is 3.25. A tap at 99 is four
        // away from the nearest of the four prices and is left alone — and left with no channel,
        // which is what stops a later resnap moving it.
        val tapped = point(1_700_000_000L, 99.0)
        val snap = DrawingActions.snap(tapped, series, MagnetMode.WEAK)
        assertNull(snap.channel)
        assertEquals(tapped, snap.point)
    }

    @Test
    fun `a weak magnet does snap once the finger is close enough`() {
        // Half a point off bar 0's close of 104, which is well inside the quarter-range reach.
        val snap = DrawingActions.snap(point(1_700_000_000L, 104.5), series, MagnetMode.WEAK)
        assertEquals(PriceChannel.CLOSE, snap.channel)
        assertEquals(104.0, snap.point.price, 1e-9)
    }

    @Test
    fun `the magnet off is not a magnet`() {
        val tapped = point(1_700_000_000L + 100, 101.234)
        assertEquals(tapped, DrawingActions.snap(tapped, series, MagnetMode.OFF).point)
    }

    @Test
    fun `a revised bar drags its bound point with it and leaves an unbound one alone`() {
        var state = DrawingActions.arm(DrawingState(magnetMode = MagnetMode.STRONG), trend)
        state = DrawingActions.tapSnapped(state, point(1_700_000_000L, 107.6), series)
        state = DrawingActions.tapSnapped(state, point(1_700_000_000L + 3600, 96.0), series)
        assertEquals(listOf(108.0, 96.0), state.drawings[0].points.map { it.price })

        // The feed resends the session with a higher high on bar 0 and an unchanged low on bar 1.
        val revised = CandleSeries(
            series.bars.mapIndexed { index, bar -> if (index == 0) bar.copy(h = 120.0) else bar },
        )
        val moved = DrawingActions.resnap(state, revised)
        assertEquals(listOf(120.0, 96.0), moved.drawings[0].points.map { it.price })
    }

    @Test
    fun `a point placed with the magnet off is never moved by a later revision`() {
        var state = DrawingActions.arm(DrawingState(), trend)
        state = DrawingActions.tap(state, point(1_700_000_000L, 101.5))
        state = DrawingActions.tap(state, point(1_700_000_000L + 3600, 99.5))
        val revised = CandleSeries(series.bars.map { it.copy(h = 400.0, l = 1.0) })
        assertEquals(state, DrawingActions.resnap(state, revised))
    }

    @Test
    fun `an old row with no channel decodes to no channel`() {
        assertNull(PriceChannel.decode(null))
        assertNull(PriceChannel.decode(""))
        assertNull(PriceChannel.decode("MIDPOINT"))
        assertEquals(PriceChannel.LOW, PriceChannel.decode("LOW"))
    }

    // ── variable-point tools ──────────────────────────────────────────────────────────

    @Test
    fun `a polyline keeps taking taps and commits when the reader says so`() {
        var state = DrawingActions.arm(DrawingState(), polylineTool)
        repeat(5) { step -> state = DrawingActions.tap(state, point(step.toLong(), 100.0 + step)) }
        assertTrue("a variable-point tool must not commit on a count", state.drawings.isEmpty())
        assertEquals(5, state.pending.size)

        state = DrawingActions.finish(state)
        assertEquals(1, state.drawings.size)
        assertEquals(5, state.drawings[0].points.size)
        assertNotEquals(state.drawings[0].points.first(), state.drawings[0].points.last())
    }

    @Test
    fun `closing a polyline repeats its first anchor at the end`() {
        var state = DrawingActions.arm(DrawingState(), polylineTool)
        repeat(4) { step -> state = DrawingActions.tap(state, point(step.toLong(), 100.0 + step)) }
        state = DrawingActions.closeShape(state)
        val points = state.drawings[0].points
        assertEquals(5, points.size)
        assertEquals(points.first(), points.last())
    }

    @Test
    fun `a path finished with one anchor disarms instead of placing a dot`() {
        var state = DrawingActions.arm(DrawingState(), pathTool)
        state = DrawingActions.tap(state, point(1, 100.0))
        state = DrawingActions.finish(state)
        assertTrue(state.drawings.isEmpty())
        assertNull(state.tool)
    }

    // ── the eraser ────────────────────────────────────────────────────────────────────

    @Test
    fun `a partial erase splits a polyline into the two pieces either side of the leg`() {
        val state = DrawingState(
            drawings = listOf(
                Drawing(
                    id = 7,
                    toolId = "polyline",
                    points = (0 until 4).map { point(it.toLong(), 100.0 + it) },
                ),
            ),
        )
        val erased = DrawingActions.erasePartial(state, id = 7, segmentIndex = 1)
        assertEquals(2, erased.drawings.size)
        assertEquals(listOf(0L, 1L), erased.drawings[0].points.map { it.time })
        assertEquals(listOf(2L, 3L), erased.drawings[1].points.map { it.time })
        assertTrue("the original must be gone", erased.drawings.none { it.id == 7L })
    }

    @Test
    fun `erasing the first leg of a three-point path leaves one line and no orphan`() {
        val state = DrawingState(
            drawings = listOf(
                Drawing(id = 3, toolId = "path", points = (0 until 3).map { point(it.toLong(), 100.0) }),
            ),
        )
        val erased = DrawingActions.erasePartial(state, id = 3, segmentIndex = 0)
        assertEquals(1, erased.drawings.size)
        assertEquals(listOf(1L, 2L), erased.drawings[0].points.map { it.time })
    }

    @Test
    fun `a partial erase on a tool that is not a chain removes the whole drawing`() {
        val state = DrawingState(
            drawings = listOf(Drawing(id = 2, toolId = "trend", points = listOf(point(0, 1.0), point(1, 2.0)))),
        )
        assertTrue(DrawingActions.erasePartial(state, id = 2, segmentIndex = 0).drawings.isEmpty())
    }

    @Test
    fun `a partial erase refuses a locked drawing, the same as every other edit`() {
        val state = DrawingState(
            drawings = listOf(
                Drawing(
                    id = 9,
                    toolId = "polyline",
                    points = (0 until 4).map { point(it.toLong(), 100.0) },
                    locked = true,
                ),
            ),
        )
        assertEquals(state, DrawingActions.erasePartial(state, id = 9, segmentIndex = 1))
    }

    // ── keep drawing ──────────────────────────────────────────────────────────────────

    @Test
    fun `keep-drawing leaves the same tool armed after a commit`() {
        var state = DrawingActions.setKeepDrawing(DrawingState(), true)
        state = DrawingActions.arm(state, trend)
        state = DrawingActions.tap(state, point(0, 100.0))
        state = DrawingActions.tap(state, point(1, 110.0))
        assertEquals(1, state.drawings.size)
        assertEquals(trend, state.tool)
        assertTrue("the next drawing starts from nothing", state.pending.isEmpty())

        state = DrawingActions.tap(state, point(2, 120.0))
        state = DrawingActions.tap(state, point(3, 130.0))
        assertEquals(2, state.drawings.size)
    }

    @Test
    fun `without keep-drawing a commit falls back to the cursor`() {
        var state = DrawingActions.arm(DrawingState(), trend)
        state = DrawingActions.tap(state, point(0, 100.0))
        state = DrawingActions.tap(state, point(1, 110.0))
        assertNull(state.tool)
    }

    // ── lock, layers, selection and the clipboard ─────────────────────────────────────

    @Test
    fun `lock all locks what is there and what arrives afterwards`() {
        var state = DrawingState(
            drawings = listOf(Drawing(id = 1, toolId = "trend", points = listOf(point(0, 1.0), point(1, 2.0)))),
        )
        state = DrawingActions.setLockAll(state, true)
        assertTrue(state.drawings.all { it.locked })

        state = DrawingActions.arm(state, trend)
        state = DrawingActions.tap(state, point(2, 3.0))
        state = DrawingActions.tap(state, point(3, 4.0))
        assertTrue("a drawing placed under the switch arrives locked", state.drawings.last().locked)
    }

    @Test
    fun `hiding the drawings layer leaves a trade setup on the chart`() {
        val state = DrawingState(
            drawings = listOf(
                Drawing(id = 1, toolId = "trend", points = listOf(point(0, 1.0), point(1, 2.0))),
                Drawing(id = 2, toolId = "longshort", points = listOf(point(0, 1.0), point(1, 2.0))),
            ),
        )
        val hidden = DrawingActions.setHidden(state, DrawingLayer.DRAWINGS, true)
        assertEquals(listOf(2L), hidden.visible.map { it.id })

        val nothing = DrawingActions.setAllHidden(state, true)
        assertTrue(nothing.visible.isEmpty())
        assertTrue(nothing.isHidden(DrawingLayer.INDICATORS))
    }

    @Test
    fun `a colour applies to the whole selection at once and skips what is locked`() {
        var state = DrawingState(
            drawings = listOf(
                Drawing(id = 1, toolId = "trend", points = listOf(point(0, 1.0), point(1, 2.0))),
                Drawing(id = 2, toolId = "trend", points = listOf(point(0, 1.0), point(1, 2.0))),
                Drawing(id = 3, toolId = "trend", points = listOf(point(0, 1.0), point(1, 2.0)), locked = true),
            ),
        )
        state = DrawingActions.select(state, 1L)
        state = DrawingActions.select(state, 2L, additive = true)
        state = DrawingActions.select(state, 3L, additive = true)
        state = DrawingActions.recolourSelection(state, 0xFFFF0000)
        assertEquals(0xFFFF0000, state.drawings[0].colour)
        assertEquals(0xFFFF0000, state.drawings[1].colour)
        assertEquals(Drawing.DEFAULT_DRAWING_COLOUR, state.drawings[2].colour)
        assertEquals(3L, state.selectedId)
    }

    @Test
    fun `copy and paste produce fresh ids offset from the originals`() {
        var state = DrawingState(
            drawings = listOf(
                Drawing(id = 4, toolId = "trend", points = listOf(point(0, 1.0), point(3600, 2.0))),
            ),
        )
        state = DrawingActions.select(state, 4L)
        state = DrawingActions.copySelection(state)
        state = DrawingActions.paste(state, deltaTime = 7200, deltaPrice = 5.0)
        assertEquals(2, state.drawings.size)
        assertEquals(5L, state.drawings[1].id)
        assertEquals(listOf(7200L, 10800L), state.drawings[1].points.map { it.time })
        assertEquals(listOf(6.0, 7.0), state.drawings[1].points.map { it.price })
        assertEquals(setOf(5L), state.selection)
    }

    @Test
    fun `clone duplicates one drawing without disturbing the clipboard`() {
        val state = DrawingState(
            drawings = listOf(Drawing(id = 1, toolId = "trend", points = listOf(point(0, 1.0), point(1, 2.0)))),
            clipboard = emptyList(),
        )
        val cloned = DrawingActions.clone(state, 1L, deltaTime = 60)
        assertEquals(2, cloned.drawings.size)
        assertEquals(listOf(60L, 61L), cloned.drawings[1].points.map { it.time })
        assertTrue("cloning is not copying", cloned.clipboard.isEmpty())
    }

    @Test
    fun `the magnet cycles off, weak, strong and back`() {
        var state = DrawingState()
        state = DrawingActions.cycleMagnet(state)
        assertEquals(MagnetMode.WEAK, state.magnetMode)
        assertTrue(state.magnet)
        state = DrawingActions.cycleMagnet(state)
        assertEquals(MagnetMode.STRONG, state.magnetMode)
        state = DrawingActions.cycleMagnet(state)
        assertEquals(MagnetMode.OFF, state.magnetMode)
        assertFalse(state.magnet)
    }

    @Test
    fun `a favourite goes in and comes back out`() {
        var state = DrawingActions.toggleFavourite(DrawingState(), "fib")
        assertEquals(setOf("fib"), state.favourites)
        state = DrawingActions.toggleFavourite(state, "fib")
        assertTrue(state.favourites.isEmpty())
    }

    // ── the volume tools ──────────────────────────────────────────────────────────────

    @Test
    fun `the three volume tools refuse a feed that reports no volume`() {
        for (tool in listOf("avwap", "volumeprofile", "avolumeprofile")) {
            assertFalse("$tool must not draw over an absent volume column", volumeToolDrawable(tool, silent))
            assertTrue("$tool draws where there is volume", volumeToolDrawable(tool, series))
        }
    }

    @Test
    fun `every other tool is drawable whatever the feed reports`() {
        assertTrue(volumeToolDrawable("trend", silent))
        assertTrue(volumeToolDrawable("gannsquare", CandleSeries(emptyList())))
    }

    @Test
    fun `an anchored VWAP is the volume-weighted mean from the anchor forward`() {
        // Bar 0: typical (108 + 95 + 104) / 3 = 102.333…, volume 1,000.
        // Bar 1: typical (109 + 96 + 105) / 3 = 103.333…, volume 1,010.
        val values = anchoredVwap(series.high, series.low, series.close, series.volume, 0, 1)
        assertEquals(2, values.size)
        assertEquals(307.0 / 3, values[0], 1e-9)
        val expected = (307.0 / 3 * 1_000 + 310.0 / 3 * 1_010) / 2_010
        assertEquals(expected, values[1], 1e-9)
    }

    @Test
    fun `an anchored VWAP over a volume-less window falls back to the typical price`() {
        val values = anchoredVwap(silent.high, silent.low, silent.close, silent.volume, 2, 3)
        assertEquals((110.0 + 97.0 + 106.0) / 3, values[0], 1e-9)
        assertEquals((111.0 + 98.0 + 107.0) / 3, values[1], 1e-9)
    }
}
