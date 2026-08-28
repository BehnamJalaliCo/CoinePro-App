package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The position tool's third point — the one that makes "drag your target" true.
 *
 * The renderer used to compute the target as `entry + 2 × risk` with a comment beside it saying a
 * reader who wants a different multiple drags the line afterwards. There was no third handle, so
 * there was nothing to drag: the comment described behaviour that did not exist and the tool drew
 * exactly one reward, for ever.
 */
class PositionToolTest {

    private val position = DrawingTools.ALL.first { it.id == "longshort" }

    private fun placed(entry: Double, stop: Double): DrawingState {
        var state = DrawingActions.arm(DrawingState(), position)
        state = DrawingActions.tap(state, ChartPoint(1_700_000_000L, entry))
        state = DrawingActions.tap(state, ChartPoint(1_700_003_600L, stop))
        return state
    }

    @Test
    fun `two taps place three points`() {
        val drawing = placed(entry = 100.0, stop = 90.0).drawings.single()
        assertEquals(3, drawing.points.size)
        // Two-to-one on the risk: risk is 10, so the target is 20 above the entry.
        assertEquals(120.0, drawing.points[2].price, 1e-9)
        // At the stop's moment, so the three handles form a column a thumb can tell apart rather
        // than three points stacked on one x.
        assertEquals(drawing.points[1].time, drawing.points[2].time)
    }

    @Test
    fun `a short is the same arithmetic in the other direction`() {
        val drawing = placed(entry = 100.0, stop = 110.0).drawings.single()
        assertEquals(80.0, drawing.points[2].price, 1e-9)
    }

    @Test
    fun `the target is draggable, and the setup follows it`() {
        val state = placed(entry = 100.0, stop = 90.0)
        val id = state.drawings.single().id
        // What a reader dragging the top line does. `movePoint` is the same call the chart's drag
        // gesture makes.
        val dragged = DrawingActions.movePoint(state, id, index = 2, to = ChartPoint(1_700_003_600L, 130.0))
        assertEquals(130.0, dragged.drawings.single().points[2].price, 1e-9)

        val order = ChartOrder(TradeSide.BUY, entry = 100.0, stopLoss = 90.0, takeProfit = 130.0)
        assertEquals(3.0, TradeFromChart.riskReward(order)!!, 1e-9)
        assertTrue(TradeFromChart.isValid(order))
    }

    @Test
    fun `a degenerate setup gets no target rather than a divide by zero`() {
        // Entry on the stop is not a trade. Two points are left as two points, which the renderer
        // and the controller both handle by falling back.
        val flat = placed(entry = 100.0, stop = 100.0).drawings.single()
        assertEquals(2, flat.points.size)
    }

    @Test
    fun `every other tool keeps exactly the points it was tapped`() {
        // The guard against `withTarget` leaking into tools it has nothing to do with. A trend
        // line that grew a third point would draw a triangle.
        DrawingTools.ALL
            .filter { it.points == 2 && it.id != "longshort" }
            .forEach { tool ->
                var state = DrawingActions.arm(DrawingState(), tool)
                state = DrawingActions.tap(state, ChartPoint(1_700_000_000L, 100.0))
                state = DrawingActions.tap(state, ChartPoint(1_700_003_600L, 90.0))
                assertEquals("${tool.id} changed shape", 2, state.drawings.single().points.size)
            }
    }
}
