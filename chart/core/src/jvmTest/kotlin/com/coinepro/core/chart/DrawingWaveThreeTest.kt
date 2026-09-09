package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The third wave of drawing work: the modes, the momentary magnet, the angle constraint, sync,
 * timeframes, per-drawing deviations and the marks that expire.
 *
 * Every one of these covers something that was built and then could not be reached — a field
 * nothing set, a parameter nothing passed, a rail entry that did nothing when tapped. The tests are
 * written against behaviour rather than against inventories for the same reason: a count assertion
 * passed the whole of the last wave while seven chart types drew the wrong thing.
 */
class DrawingWaveThreeTest {

    /**
     * A hundred and twenty bars that drift up with a deterministic wobble on the closes.
     *
     * The wobble is not decoration. A perfectly straight series has zero residuals, so a regression
     * channel's rails collapse onto its centre line at every deviation count and the test that
     * proves the count reaches the renderer would divide nothing by nothing and pass.
     */
    private val series = CandleSeries(
        (0 until 120).map { index ->
            val drift = 100.0 + index * 0.5
            val close = drift + ((index % 7) - 3) * 0.8
            Candle(
                t = 1_700_000_000L + index * 3600,
                o = drift,
                h = maxOf(drift, close) + 2,
                l = minOf(drift, close) - 2,
                c = close,
            )
        },
    )

    private val view = ChartViewport(series).sized(width = 360f, height = 240f)

    private fun tool(id: String) = DrawingTools[id]!!

    private fun point(time: Long, price: Double) = ChartPoint(time, price)

    // ── modes ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `arming a mode entry sets the mode instead of dropping it on the floor`() {
        // `dot` and `arrowcursor` were rail buttons that did nothing at all: `arm` refused every
        // MODES entry and stored nothing in its place.
        for (mode in listOf(DrawingMode.DOT, DrawingMode.ARROW_CURSOR, DrawingMode.SELECT)) {
            val state = DrawingActions.arm(DrawingState(), tool(mode.toolId))
            assertEquals(mode, state.mode)
            assertNull("a mode arms no tool", state.tool)
        }
    }

    @Test
    fun `the eraser is a mode like any other and the state answers for it`() {
        val state = DrawingActions.arm(DrawingState(), tool("eraser"))
        assertEquals(DrawingMode.ERASER, state.mode)
        assertTrue(state.eraser)

        // Arming a real tool takes the eraser off: a tap cannot both draw and delete.
        val armed = DrawingActions.arm(state, tool("trend"))
        assertFalse(armed.eraser)
        assertEquals(DrawingMode.CURSOR, armed.mode)
        assertEquals("trend", armed.tool?.id)
    }

    @Test
    fun `demonstration mode survives arming a tool because it is about the mark, not the tap`() {
        var state = DrawingActions.arm(DrawingState(), tool(DrawingTools.DEMONSTRATION_TOOL))
        assertTrue(state.demonstrating)

        state = DrawingActions.arm(state, tool("highlighter"))
        assertTrue("a demonstration needs a tool to demonstrate with", state.demonstrating)
        assertEquals("highlighter", state.tool?.id)

        // And the cursor turns it off, which is the way out.
        assertFalse(DrawingActions.arm(state, tool("cursor")).demonstrating)
    }

    @Test
    fun `the rail's magnet entry advances the magnet rather than becoming a mode`() {
        val magnet = tool("magnet")
        var state = DrawingActions.arm(DrawingState(), magnet)
        assertEquals(MagnetMode.WEAK, state.magnetMode)
        state = DrawingActions.arm(state, magnet)
        assertEquals(MagnetMode.STRONG, state.magnetMode)
        state = DrawingActions.arm(state, magnet)
        assertEquals(MagnetMode.OFF, state.magnetMode)
        assertEquals("the magnet is not a mode", DrawingMode.CURSOR, state.mode)
    }

    @Test
    fun `setting a tap-changing mode abandons a half-placed drawing`() {
        var state = DrawingActions.arm(DrawingState(), tool("xabcd"))
        state = DrawingActions.tap(state, point(1, 100.0))
        state = DrawingActions.setMode(state, DrawingMode.ERASER)
        assertTrue("orphaned anchors would be read by the new mode", state.pending.isEmpty())
        assertNull(state.tool)
    }

    // ── the momentary magnet, item 38 ─────────────────────────────────────────────────

    @Test
    fun `holding the magnet turns it on from off and leaves a chosen strength alone`() {
        val off = DrawingActions.holdMagnet(DrawingState())
        assertEquals(MagnetMode.STRONG, off.effectiveMagnetMode)
        assertTrue(off.magnet)

        val weak = DrawingActions.holdMagnet(DrawingState(magnetMode = MagnetMode.WEAK))
        assertEquals(
            "a hold is «snap this one», not «snap it harder»",
            MagnetMode.WEAK,
            weak.effectiveMagnetMode,
        )

        assertEquals(MagnetMode.OFF, DrawingActions.releaseMagnet(off).effectiveMagnetMode)
    }

    @Test
    fun `a held magnet snaps the tap and is released by the placement it was held for`() {
        var state = DrawingActions.arm(DrawingState(), tool("hline"))
        state = DrawingActions.holdMagnet(state)
        // A price a couple of dollars under bar 40's low, which a strong magnet pulls onto it.
        val low = series.low[40]
        state = DrawingActions.tapSnapped(state, point(series.time[40], low - 3.0), series)

        assertEquals(low, state.drawings.single().points[0].price, 1e-9)
        assertFalse("the hold has to end with the placement", state.momentaryMagnet)
        assertEquals(MagnetMode.OFF, state.effectiveMagnetMode)
    }

    @Test
    fun `with no hold and the magnet off a tap lands exactly where the finger did`() {
        var state = DrawingActions.arm(DrawingState(), tool("hline"))
        val wanted = series.low[40] - 3.0
        state = DrawingActions.tapSnapped(state, point(series.time[40], wanted), series)
        assertEquals(wanted, state.drawings.single().points[0].price, 1e-9)
    }

    // ── the angle constraint, item 48 ─────────────────────────────────────────────────

    @Test
    fun `a constrained trend line snaps to the nearest eighth of a turn`() {
        val from = point(series.time[70], series.close[70])
        // A drag that is almost horizontal: a few pixels of rise over a long run.
        val nearlyFlat = ChartPoint(series.time[100], view.priceAt(view.yOf(from.price) - 3f))
        val flat = DrawingActions.constrain("trend", from, nearlyFlat, view)
        assertEquals(
            "a near-horizontal drag should be horizontal",
            view.yOf(from.price),
            view.yOf(flat.price),
            0.6f,
        )

        // And one at about forty degrees lands on exactly forty-five: equal legs on screen.
        val run = 80f
        val diagonal = ChartPoint(
            view.timeAt(view.xOfTime(from.time) + run),
            view.priceAt(view.yOf(from.price) - run * 0.85f),
        )
        val snapped = DrawingActions.constrain("trend", from, diagonal, view)
        val dx = view.xOfTime(snapped.time) - view.xOfTime(from.time)
        val dy = view.yOf(from.price) - view.yOf(snapped.price)
        // Within a bar: a chart point is a moment, and a moment lands on the bar it falls in, so
        // the x that comes back is quantised even though the maths that produced it was not.
        assertEquals("a diagonal is equal legs on screen", dx, dy, view.barWidth + 1f)
    }

    @Test
    fun `a constrained rectangle is square on the glass and an ellipse is a circle`() {
        val from = point(series.time[70], series.close[70])
        val wide = ChartPoint(
            view.timeAt(view.xOfTime(from.time) + 120f),
            view.priceAt(view.yOf(from.price) + 40f),
        )
        for (id in listOf("rect", "ellipse", "circle")) {
            val squared = DrawingActions.constrain(id, from, wide, view)
            val dx = kotlin.math.abs(view.xOfTime(squared.time) - view.xOfTime(from.time))
            val dy = kotlin.math.abs(view.yOf(squared.price) - view.yOf(from.price))
            assertEquals("$id should be square on screen", dx, dy, view.barWidth + 1f)
            // The shorter side wins, so the shape stays inside the drag rather than overshooting.
            assertTrue("$id overshot the finger", dx <= 41f + view.barWidth)
        }
    }

    @Test
    fun `a tool with nothing to constrain is returned untouched`() {
        val from = point(series.time[70], 100.0)
        val to = point(series.time[100], 140.0)
        assertEquals(to, DrawingActions.constrain("xabcd", from, to, view))
        assertEquals(to, DrawingActions.constrain("fib", from, to, view))
    }

    @Test
    fun `a constrained drag that went nowhere is left alone rather than divided by zero`() {
        val at = point(series.time[70], 100.0)
        assertEquals(at, DrawingActions.constrain("trend", at, at, view))
        assertEquals(at, DrawingActions.constrain("rect", at, at, view))
    }

    // ── the directed arrow, item 2 ────────────────────────────────────────────────────

    @Test
    fun `the second tap on a directed arrow is its direction, not a second anchor`() {
        var state = DrawingActions.arm(DrawingState(), tool("arrowdir"))
        val head = point(series.time[40], 120.0)
        state = DrawingActions.tap(state, head)
        assertTrue("it should still be waiting for the direction", state.drawings.isEmpty())

        state = DrawingActions.tap(state, point(series.time[40], 110.0), view = view)
        val placed = state.drawings.single()
        assertEquals("a marker is one point", 1, placed.points.size)
        assertEquals(head, placed.points[0])
        assertEquals(ArrowDirection.DOWN, placed.direction)
    }

    @Test
    fun `an arrow points the way the finger went, on all four sides`() {
        val at = point(series.time[80], 120.0)
        val above = ChartPoint(at.time, view.priceAt(view.yOf(at.price) - 60f))
        val below = ChartPoint(at.time, view.priceAt(view.yOf(at.price) + 60f))
        val right = ChartPoint(view.timeAt(view.xOfTime(at.time) + 60f), at.price)
        val left = ChartPoint(view.timeAt(view.xOfTime(at.time) - 60f), at.price)

        assertEquals(ArrowDirection.UP, DrawingActions.directionOf(at, above, view))
        assertEquals(ArrowDirection.DOWN, DrawingActions.directionOf(at, below, view))
        assertEquals(ArrowDirection.RIGHT, DrawingActions.directionOf(at, right, view))
        assertEquals(ArrowDirection.LEFT, DrawingActions.directionOf(at, left, view))
    }

    @Test
    fun `with no viewport the price axis decides, because seconds and dollars do not compare`() {
        val at = point(series.time[40], 120.0)
        assertEquals(ArrowDirection.UP, DrawingActions.directionOf(at, point(at.time, 125.0), null))
        assertEquals(ArrowDirection.DOWN, DrawingActions.directionOf(at, point(at.time, 115.0), null))
    }

    // ── arrow marks, item 9 ───────────────────────────────────────────────────────────

    @Test
    fun `arrow marks take as many taps as the reader wants and commit when they say so`() {
        // The registry said zero points, `tap` refuses a zero-point tool, and the geometry needs
        // two anchors — so arming this tool and tapping placed nothing at all.
        var state = DrawingActions.arm(DrawingState(), tool("arrowmarks"))
        state = DrawingActions.tap(state, point(series.time[10], 105.0))
        state = DrawingActions.tap(state, point(series.time[20], 115.0))
        state = DrawingActions.tap(state, point(series.time[30], 108.0))
        assertEquals(3, state.pending.size)

        state = DrawingActions.finish(state)
        assertEquals(3, state.drawings.single().points.size)
        assertEquals("arrowmarks", state.drawings.single().toolId)
    }

    @Test
    fun `tapping back on the first arrow mark ends the row rather than closing a ring`() {
        var state = DrawingActions.arm(DrawingState(), tool("arrowmarks"))
        val first = point(series.time[10], 105.0)
        state = DrawingActions.tap(state, first)
        state = DrawingActions.tap(state, point(series.time[20], 115.0))
        state = DrawingActions.tap(state, point(series.time[30], 108.0))
        state = DrawingActions.closeShape(state)

        val placed = state.drawings.single()
        assertEquals("a row of marks is not a ring", 3, placed.points.size)
        assertFalse("the first mark was duplicated", placed.points.last() == first)
    }

    @Test
    fun `a polyline still closes into a ring`() {
        var state = DrawingActions.arm(DrawingState(), tool("polyline"))
        val first = point(series.time[10], 105.0)
        state = DrawingActions.tap(state, first)
        state = DrawingActions.tap(state, point(series.time[20], 115.0))
        state = DrawingActions.tap(state, point(series.time[30], 108.0))
        state = DrawingActions.closeShape(state)
        assertEquals(first, state.drawings.single().points.last())
    }

    // ── drawing sync, items 51 and 188 ────────────────────────────────────────────────

    @Test
    fun `a globally synced drawing appears under every layout and the others do not`() {
        val here = Drawing(id = 1, toolId = "hline", points = listOf(point(1, 100.0)), layoutId = "a")
        val everywhere = here.copy(id = 2, sync = DrawingSync.GLOBAL)
        val scratch = here.copy(id = 3, sync = DrawingSync.NONE)
        val all = listOf(here, everywhere, scratch)

        assertEquals(
            listOf(1L, 2L, 3L),
            DrawingActions.syncedInto(all, "a").map { it.id },
        )
        assertEquals(
            "only the global one travels to another layout",
            listOf(2L),
            DrawingActions.syncedInto(all, "b").map { it.id },
        )
        assertEquals(
            listOf(2L),
            DrawingActions.syncedInto(all, null).map { it.id },
        )
    }

    @Test
    fun `an unsynced drawing is visible where it was drawn but is not filed with the layout`() {
        val kept = Drawing(id = 1, toolId = "hline", points = listOf(point(1, 100.0)), layoutId = "a")
        val scratch = kept.copy(id = 2, sync = DrawingSync.NONE)
        val saved = DrawingActions.savedWithLayout(listOf(kept, scratch), "a").map { it.id }
        assertEquals(listOf(1L), saved)
        assertTrue(DrawingSync.LAYOUT.travels)
        assertTrue(DrawingSync.GLOBAL.travels)
        assertFalse(DrawingSync.NONE.travels)
    }

    @Test
    fun `a placed drawing takes the layout and the sync setting in force`() {
        val state = DrawingState(layoutId = "swing", sync = DrawingSync.GLOBAL)
        val placed = DrawingActions.tap(
            DrawingActions.arm(state, tool("hline")),
            point(series.time[10], 110.0),
        )
        assertEquals("swing", placed.drawings.single().layoutId)
        assertEquals(DrawingSync.GLOBAL, placed.drawings.single().sync)

        val moved = DrawingActions.setSync(placed, placed.drawings.single().id, DrawingSync.NONE)
        assertEquals(DrawingSync.NONE, moved.drawings.single().sync)
    }

    // ── the timeframe a drawing carries, items 52 and 187 ─────────────────────────────

    @Test
    fun `a drawing records the interval it was drawn on`() {
        val state = DrawingActions.setTimeframe(DrawingState(), "H1")
        val placed = DrawingActions.tap(
            DrawingActions.arm(state, tool("trend")),
            point(series.time[10], 110.0),
        ).let { DrawingActions.tap(it, point(series.time[40], 130.0)) }
        assertEquals("H1", placed.drawings.single().timeframe)
    }

    @Test
    fun `a chart that never said which interval it is on labels nothing`() {
        // "Nothing said" is the truth about every drawing saved before the field existed, and a
        // confident wrong label on somebody's old work is worse than no label.
        val placed = DrawingActions.tap(
            DrawingActions.arm(DrawingState(), tool("hline")),
            point(series.time[10], 110.0),
        )
        assertNull(placed.drawings.single().timeframe)
        assertNull(DrawingActions.setTimeframe(DrawingState(), "   ").timeframe)
    }

    // ── the regression channel's deviations, item 8 ───────────────────────────────────

    @Test
    fun `a regression channel carries its own deviation count and it is clamped`() {
        var state = DrawingActions.arm(DrawingState(), tool("regression"))
        state = DrawingActions.tap(state, point(series.time[10], 105.0))
        state = DrawingActions.tap(state, point(series.time[60], 130.0))
        val id = state.drawings.single().id
        assertEquals(Drawing.DEFAULT_DEVIATIONS, state.drawings.single().deviations, 1e-9)

        assertEquals(
            1.0,
            DrawingActions.setDeviations(state, id, 1.0).drawings.single().deviations,
            1e-9,
        )
        assertEquals(
            DrawingActions.MAX_DEVIATIONS,
            DrawingActions.setDeviations(state, id, 99.0).drawings.single().deviations,
            1e-9,
        )
        assertEquals(
            DrawingActions.MIN_DEVIATIONS,
            DrawingActions.setDeviations(state, id, -4.0).drawings.single().deviations,
            1e-9,
        )
    }

    @Test
    fun `the rails actually move when the deviation count does`() {
        // The renderer passed no deviations at all, so this argument was documented, tested in the
        // geometry and unreachable from the chart. Two counts must not produce the same channel.
        val points = listOf(GeoPoint(0.0, 100.0), GeoPoint(50.0, 130.0))
        val narrow = DrawingGeometryA.regressionChannel(points, series.close, 0, 50, deviations = 1.0)
        val wide = DrawingGeometryA.regressionChannel(points, series.close, 0, 50, deviations = 3.0)
        val narrowSpread = narrow[1].a.p - narrow[2].a.p
        val wideSpread = wide[1].a.p - wide[2].a.p
        assertTrue("a wider channel must be wider", wideSpread > narrowSpread + 1e-6)
        assertEquals(3.0 / 1.0, wideSpread / narrowSpread, 1e-6)
    }

    // ── demonstration mode, item 41 ───────────────────────────────────────────────────

    @Test
    fun `a mark placed in demonstration mode is given a deadline and an ordinary one is not`() {
        var state = DrawingActions.arm(DrawingState(), tool(DrawingTools.DEMONSTRATION_TOOL))
        state = DrawingActions.arm(state, tool("hline"))
        val before = System.currentTimeMillis()
        state = DrawingActions.tap(state, point(series.time[10], 110.0))
        val fadesAt = state.drawings.single().fadesAtMillis
        assertNotNull("a demonstration mark has to expire", fadesAt)
        assertTrue(fadesAt!! >= before + DrawingActions.DEMONSTRATION_LIFETIME_MS)

        val ordinary = DrawingActions.tap(
            DrawingActions.arm(DrawingState(), tool("hline")),
            point(series.time[10], 110.0),
        )
        assertNull(ordinary.drawings.single().fadesAtMillis)
    }

    @Test
    fun `a demonstration mark is solid, then ramps, then is gone`() {
        val deadline = 10_000L
        val mark = Drawing(
            id = 1,
            toolId = "hline",
            points = listOf(point(1, 100.0)),
            fadesAtMillis = deadline,
        )
        val fadeStart = deadline - DrawingActions.DEMONSTRATION_FADE_MS

        assertEquals(1f, DrawingActions.fadeAlpha(mark, 0L), 1e-6f)
        assertEquals(1f, DrawingActions.fadeAlpha(mark, fadeStart), 1e-6f)
        assertEquals(0.5f, DrawingActions.fadeAlpha(mark, deadline - DrawingActions.DEMONSTRATION_FADE_MS / 2), 1e-6f)
        assertEquals(0f, DrawingActions.fadeAlpha(mark, deadline), 1e-6f)
        assertEquals(0f, DrawingActions.fadeAlpha(mark, deadline + 5_000), 1e-6f)

        assertFalse(mark.hasFaded(fadeStart))
        assertTrue(mark.hasFaded(deadline))
    }

    @Test
    fun `a permanent drawing never asks the clock`() {
        val mark = Drawing(id = 1, toolId = "hline", points = listOf(point(1, 100.0)))
        assertEquals(1f, DrawingActions.fadeAlpha(mark, Long.MAX_VALUE), 1e-6f)
        assertFalse(mark.hasFaded(Long.MAX_VALUE))
    }

    @Test
    fun `expiring a mark takes its selection and its bindings with it`() {
        val temporary = Drawing(
            id = 1,
            toolId = "hline",
            points = listOf(point(1, 100.0)),
            fadesAtMillis = 5_000L,
        )
        val permanent = Drawing(id = 2, toolId = "hline", points = listOf(point(2, 101.0)))
        val state = DrawingState(
            drawings = listOf(temporary, permanent),
            selectedId = 1L,
            selection = setOf(1L, 2L),
            bindings = mapOf(PointRef(1L, 0) to PriceChannel.LOW),
            hiddenIds = setOf(1L),
        )

        val early = DrawingActions.expire(state, 4_000L)
        assertTrue("nothing expired yet, so nothing should have been rebuilt", early === state)

        val reaped = DrawingActions.expire(state, 6_000L)
        assertEquals(listOf(2L), reaped.drawings.map { it.id })
        assertNull(reaped.selectedId)
        assertEquals(setOf(2L), reaped.selection)
        assertTrue(reaped.bindings.isEmpty())
        assertTrue(reaped.hiddenIds.isEmpty())
    }

    @Test
    fun `a faded mark leaves the visible list even before anything reaps it`() {
        val gone = Drawing(
            id = 1,
            toolId = "hline",
            points = listOf(point(1, 100.0)),
            fadesAtMillis = 1L,
        )
        val here = Drawing(id = 2, toolId = "hline", points = listOf(point(2, 101.0)))
        assertEquals(listOf(2L), DrawingState(drawings = listOf(gone, here)).visible.map { it.id })
    }

    // ── the icon set and the image tool's honesty, item 1 ─────────────────────────────

    @Test
    fun `the icon tool offers single-character marks and defaults to one of them`() {
        assertTrue(DrawingActions.ICON_GLYPHS.isNotEmpty())
        for (glyph in DrawingActions.ICON_GLYPHS) {
            assertEquals("«$glyph» is not one mark", 1, glyph.length)
        }
        assertTrue(DrawingActions.DEFAULT_ICON_GLYPH in DrawingActions.ICON_GLYPHS)
        assertTrue(DrawingActions.holdsIcon("icon"))
        assertFalse(DrawingActions.holdsIcon("text"))
    }

    @Test
    fun `the image tool says out loud that it cannot load a file`() {
        assertNotNull(DrawingActions.toolNote("image"))
        assertNull("ninety tools need no note", DrawingActions.toolNote("trend"))
    }
}
