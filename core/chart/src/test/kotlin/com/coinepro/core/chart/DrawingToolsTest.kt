package com.coinepro.core.chart

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingToolsTest {

    private val helpIds: Set<String> = HelpIds.load()

    private val series = CandleSeries(
        (0 until 200).map { index ->
            val base = 100.0 + index * 0.1
            Candle(1_700_000_000L + index * 3600, base, base + 1, base - 1, base + 0.5)
        },
    )

    private val view = ChartViewport(series).sized(width = 360f, height = 240f)

    @Test
    fun `every tool points at a help entry that exists`() {
        // The first attempt built this list from the icon file names and produced twenty-seven
        // tools whose «؟» pointed at nothing. It is now taken from the web terminal's own rail,
        // which is where the help ids come from too — so they agree by construction, and this
        // test is what keeps them agreeing.
        for (tool in DrawingTools.ALL) {
            val helpId = tool.helpId ?: continue
            assertTrue(
                "tool ${tool.id} points at '$helpId', which has no help entry",
                helpId in helpIds,
            )
        }
    }

    @Test
    fun `only the two non-drawing modes lack help`() {
        val withoutHelp = DrawingTools.ALL.filter { it.helpId == null }.map { it.id }
        assertEquals(listOf("cursor", "select"), withoutHelp)
    }

    @Test
    fun `the rail offers what the web terminal offers`() {
        assertEquals(52, DrawingTools.ALL.size)
    }

    @Test
    fun `no tool is listed twice and every group has tools`() {
        assertEquals(DrawingTools.ALL.size, DrawingTools.ALL.map { it.id }.toSet().size)
        for (group in DrawingTools.GROUPS) {
            assertTrue("$group is empty", DrawingTools.inGroup(group).isNotEmpty())
        }
        assertEquals(ToolGroup.entries.toSet(), DrawingTools.GROUPS.toSet())
    }

    @Test
    fun `point counts are sane`() {
        for (tool in DrawingTools.ALL) {
            // Zero means freehand — the reader drags and the stroke ends when they lift. Anything
            // else is a fixed number of taps, and more than seven would be a tool nobody finishes.
            assertTrue("${tool.id} asks for ${tool.points} points", tool.points in 0..7)
        }
    }

    @Test
    fun `an unknown tool id is null`() {
        assertNull(DrawingTools["no-such-tool"])
        assertEquals("خط روند", DrawingTools["trend"]?.label)
    }

    @Test
    fun `a tap near a trend line finds it and a tap away from it does not`() {
        val drawing = Drawing(
            id = 1,
            toolId = "trend",
            points = listOf(
                ChartPoint(series.time[100], series.close[100]),
                ChartPoint(series.time[150], series.close[150]),
            ),
        )
        val midX = (view.xOfTime(drawing.points[0].time) + view.xOfTime(drawing.points[1].time)) / 2
        val midY = (view.yOf(drawing.points[0].price) + view.yOf(drawing.points[1].price)) / 2

        assertTrue(DrawingHitTest.distanceTo(drawing, midX, midY, view) < 1f)
        assertTrue(DrawingHitTest.distanceTo(drawing, midX, midY + 60f, view) > 30f)
    }

    @Test
    fun `a tap past the end of a trend line measures to its end, not to its extension`() {
        // Otherwise every trend line is grabbable along an invisible infinite line through it, and
        // a tap in empty space selects a drawing that is nowhere near.
        val drawing = Drawing(
            id = 1,
            toolId = "trend",
            points = listOf(ChartPoint(series.time[10], 100.0), ChartPoint(series.time[20], 101.0)),
        )
        val beyond = view.xOfTime(series.time[60])
        val onTheExtension = view.yOf(105.0)
        assertTrue(DrawingHitTest.distanceTo(drawing, beyond, onTheExtension, view) > 20f)
    }

    @Test
    fun `a horizontal line is grabbable all the way across`() {
        // A price level is a level. Requiring a tap near where it was first placed would make it
        // ungrabbable everywhere else on the plot, which is most of it.
        val level = Drawing(id = 1, toolId = "hline", points = listOf(ChartPoint(series.time[5], 110.0)))
        val y = view.yOf(110.0)
        for (x in listOf(5f, 120f, 350f)) {
            assertTrue("not grabbable at x=$x", DrawingHitTest.distanceTo(level, x, y, view) < 1f)
        }
        assertTrue(DrawingHitTest.distanceTo(level, 120f, y + 80f, view) > 20f)
    }

    @Test
    fun `a vertical line is grabbable all the way down`() {
        val moment = Drawing(id = 1, toolId = "vline", points = listOf(ChartPoint(series.time[150], 100.0)))
        val x = view.xOfTime(series.time[150])
        for (y in listOf(2f, 120f, 235f)) {
            assertTrue("not grabbable at y=$y", DrawingHitTest.distanceTo(moment, x, y, view) < 1f)
        }
    }

    @Test
    fun `the topmost drawing wins when two overlap`() {
        val lower = Drawing(id = 1, toolId = "hline", points = listOf(ChartPoint(series.time[5], 110.0)))
        val upper = Drawing(id = 2, toolId = "hline", points = listOf(ChartPoint(series.time[5], 110.0)))
        val hit = DrawingHitTest.at(listOf(lower, upper), 100f, view.yOf(110.0), view, tolerancePx = 12f)
        assertEquals(2L, hit?.id)
    }

    @Test
    fun `a tap on empty space selects nothing`() {
        val drawing = Drawing(id = 1, toolId = "hline", points = listOf(ChartPoint(series.time[5], 110.0)))
        assertNull(DrawingHitTest.at(listOf(drawing), 100f, view.yOf(110.0) + 90f, view, 12f))
    }

    @Test
    fun `a drawing survives a pan and a zoom because it is stored in chart space`() {
        // The whole reason points are (time, price). If this ever fails, every tool in the app has
        // silently become a pixel drawing that slides off its bars.
        val drawing = Drawing(
            id = 1,
            toolId = "hline",
            points = listOf(ChartPoint(series.time[100], 110.0)),
        )
        val moved = view.pannedBy(view.barWidth * 25).zoomedBy(1.8f)
        assertTrue(DrawingHitTest.distanceTo(drawing, 100f, moved.yOf(110.0), moved) < 1f)
    }

    @Test
    fun `a degenerate segment does not divide by zero`() {
        assertEquals(5f, DrawingHitTest.distanceToSegment(0f, 5f, 0f, 0f, 0f, 0f), 1e-4f)
    }

    @Test
    fun `a note can hold a note`() {
        // `Drawing.text` existed from the first version of the drawing engine and nothing could
        // write to it, so `text`, `callout` and `pricelabel` rendered the literal «یادداشت»
        // forever and `note` drew a bare circle. This is the setter that closes that.
        val placed = DrawingActions.tap(
            DrawingActions.arm(DrawingState(), DrawingTools.ALL.first { it.id == "note" }),
            ChartPoint(1_700_000_000L, 100.0),
        )
        val id = placed.drawings.single().id
        val labelled = DrawingActions.setText(placed, id, "  مقاومت هفتگی  ")
        assertEquals("مقاومت هفتگی", labelled.drawings.single().text)

        // Emptying it clears rather than storing "", so the drawing falls back to its placeholder
        // instead of becoming an invisible thing the reader cannot find to delete.
        assertNull(DrawingActions.setText(labelled, id, "   ").drawings.single().text)
        assertNull(DrawingActions.setText(labelled, id, null).drawings.single().text)
    }

    @Test
    fun `only the four annotation tools are offered a keyboard`() {
        val holds = DrawingTools.ALL.filter { DrawingActions.holdsText(it.id) }.map { it.id }.toSet()
        assertEquals(setOf("text", "callout", "pricelabel", "note"), holds)
    }

    @Test
    fun `a label longer than the chart can carry is cut, not refused`() {
        val placed = DrawingActions.tap(
            DrawingActions.arm(DrawingState(), DrawingTools.ALL.first { it.id == "text" }),
            ChartPoint(1_700_000_000L, 100.0),
        )
        val id = placed.drawings.single().id
        val long = "ب".repeat(DrawingActions.MAX_TEXT_LENGTH * 2)
        val text = DrawingActions.setText(placed, id, long).drawings.single().text
        assertEquals(DrawingActions.MAX_TEXT_LENGTH, text!!.length)
    }
}

/** The shipped help ids, read from `core:help`'s asset without depending on that module. */
internal object HelpIds {
    fun load(): Set<String> {
        val file = File("../help/src/main/assets/help/content.json")
        check(file.exists()) { "The help catalogue is missing: ${file.absolutePath}" }
        val text = file.readText()
        return Regex("^  \"([^\"]+)\":", RegexOption.MULTILINE)
            .findAll(text)
            .map { it.groupValues[1] }
            .toSet()
            .ifEmpty {
                Regex("\"([A-Za-z0-9_+-]+)\"\\s*:\\s*\\{\\s*\"title\"")
                    .findAll(text)
                    .map { it.groupValues[1] }
                    .toSet()
            }
    }
}
