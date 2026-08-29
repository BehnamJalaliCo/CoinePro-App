package com.coinepro.feature.alerts

import com.coinepro.core.datastore.StoredDrawing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drawing picker's menu: what it offers, what it leaves out, and what number it shows.
 *
 * The number is the part worth asserting. A picker of drawings is unusable without one — the store
 * holds numeric ids and nothing else a reader recognises — and a *wrong* one is worse than none,
 * because somebody would set an alert on the line they think reads 68,500.
 */
class AlertDrawingsTest {

    private val delta = 1e-9

    private fun drawing(
        id: Long,
        toolId: String,
        points: List<Pair<Long, Double>>,
    ) = StoredDrawing(
        id = id,
        toolId = toolId,
        points = points,
        colour = 0xFFFFFFFF,
        widthDp = 1f,
        text = null,
        direction = "up",
    )

    @Test
    fun `a horizontal line is offered at the price it was placed at`() {
        val option = AlertDrawings.optionsOf(
            listOf(drawing(1L, "hline", listOf(1_700_000_000L to 68_500.0))),
        ).single()

        assertEquals("1", option.id)
        assertEquals("خط افقی", option.label)
        assertEquals(68_500.0, option.level, delta)
    }

    @Test
    fun `a trend line is offered at its last anchor, which is where the reader put it`() {
        val option = AlertDrawings.optionsOf(
            listOf(
                drawing(
                    id = 2L,
                    toolId = "trend",
                    points = listOf(1_000L to 100.0, 2_000L to 200.0),
                ),
            ),
        ).single()

        assertEquals("خط روند", option.label)
        assertEquals(200.0, option.level, delta)
    }

    @Test
    fun `a three-point channel is read along the line through its first two anchors`() {
        // The same straight line `AlertDrawingLevel` extends when it resolves the level at each
        // sample. If these two ever disagree, the picker names a level the alert never fires at.
        val option = AlertDrawings.optionsOf(
            listOf(
                drawing(
                    id = 3L,
                    toolId = "channel",
                    points = listOf(0L to 100.0, 100L to 200.0, 300L to 150.0),
                ),
            ),
        ).single()

        assertEquals(400.0, option.level, delta)
    }

    @Test
    fun `two anchors on one instant have no slope and do not divide by zero`() {
        val option = AlertDrawings.optionsOf(
            listOf(drawing(4L, "trend", listOf(500L to 42.0, 500L to 99.0))),
        ).single()

        assertEquals(42.0, option.level, delta)
    }

    @Test
    fun `a time marker is left out rather than offered at whatever height it was tapped`() {
        // A vertical line has a price in its anchor — wherever the finger landed — and offering it
        // would make a silent horizontal alert at a level nobody chose.
        val drawings = AlertDrawings.TIME_ONLY_TOOLS.mapIndexed { index, tool ->
            drawing(index.toLong(), tool, listOf(1_000L to 55.0, 2_000L to 66.0))
        }

        assertTrue(AlertDrawings.optionsOf(drawings).isEmpty())
        assertNull(AlertDrawings.levelOf(drawings.first()))
    }

    @Test
    fun `a drawing with no points at all is left out rather than shown as zero`() {
        assertTrue(AlertDrawings.optionsOf(listOf(drawing(5L, "trend", emptyList()))).isEmpty())
    }

    @Test
    fun `the newest drawing is offered first, because it is the one about to be alerted on`() {
        val options = AlertDrawings.optionsOf(
            listOf(
                drawing(10L, "hline", listOf(1L to 1.0)),
                drawing(30L, "hline", listOf(1L to 3.0)),
                drawing(20L, "hline", listOf(1L to 2.0)),
            ),
        )

        assertEquals(listOf("30", "20", "10"), options.map(AlertDrawingOption::id))
    }

    @Test
    fun `a tool this build has no name for still appears, under its id`() {
        // Hiding it would be the app deciding the reader's own drawing does not exist. The same
        // fallback `AlertSentence.indicatorName` makes for an indicator id from a later release.
        val option = AlertDrawings.optionsOf(
            listOf(drawing(6L, "somethingnew", listOf(1L to 7.0))),
        ).single()

        assertEquals("SOMETHINGNEW", option.label)
    }

    @Test
    fun `the id is the store's own id as a string, because that is what the evaluator keys on`() {
        // `GuestAlertMarketSource` builds its level map with `drawing.id.toString()`. A different
        // spelling here would resolve to no level, and the alert would never fire — silently.
        val option = AlertDrawings.optionsOf(
            listOf(drawing(1_731_059_442L, "hline", listOf(1L to 9.0))),
        ).single()

        assertEquals("1731059442", option.id)
    }
}
