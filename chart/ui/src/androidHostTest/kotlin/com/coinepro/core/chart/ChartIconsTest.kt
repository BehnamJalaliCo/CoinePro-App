package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.io.File

/**
 * The engine names icons; `ChartIcons` turns names into drawables. The two lists drift the day
 * somebody adds a tool without a branch here, and the symptom is a candles glyph on a tool that
 * is not candles — quiet, and on every phone. So the test reads the engine's sources and checks
 * every name it uses has a branch, and every branch has a drawable file behind it.
 */
class ChartIconsTest {

    @Test
    fun `every icon the engine names has a drawable`() {
        val used = File("../core/src/commonMain/kotlin")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { ICON.findAll(it.readText()).map { match -> match.groupValues[1] } }
            .toSortedSet()
        assertNotEquals("the engine's icon names were not found", 0, used.size)
        assertEquals(used, ChartIcons.names.toSortedSet())
        val drawables = File("../../core/designsystem/src/main/res/drawable")
        val missing = used.filterNot { File(drawables, "$it.xml").isFile }
        assertEquals("names with no drawable file", emptyList<String>(), missing)
    }

    @Test
    fun `an unknown name falls back to the candles glyph rather than crashing`() {
        assertEquals(ChartIcons.drawable(ChartIcon("tv_chart_candles")), ChartIcons.drawable(ChartIcon("no_such_icon")))
    }

    @Test
    fun `a known name resolves to its own drawable`() {
        assertNotEquals(ChartIcons.drawable(ChartIcon("tv_chart_candles")), ChartIcons.drawable(ChartIcon("tv_tool_sine")))
    }

    private companion object {
        val ICON = Regex("""ChartIcon\("([a-z0-9_]+)"\)""")
    }
}
