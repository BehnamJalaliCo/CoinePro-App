package com.coinepro.feature.chart

import com.coinepro.core.chart.ChartPoint
import com.coinepro.core.chart.Drawing
import com.coinepro.core.chart.DrawingState
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.Timeframe
import com.coinepro.core.notifications.AlertTrigger
import com.coinepro.core.notifications.LocalAlertCondition
import com.coinepro.core.notifications.LocalPriceAlert
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deleting a drawing that carries alerts.
 *
 * The defect this is about is not visible on a screen: an alert whose drawing is gone stays in the
 * centre reading as armed, and can never fire. So every assertion here is about the state the
 * screen is given rather than about pixels — and the one that matters most is the negative one,
 * that a drawing nothing watches still goes on the tap with no question.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DrawingAlertDeleteTest {

    private class FakeGateway : CandleGateway {
        override suspend fun load(
            symbol: String,
            timeframe: Timeframe,
            limit: Int,
            before: Long?,
        ): CandlePage = CandlePage(symbol, timeframe, emptyList())
    }

    private class FakeAlerts(initial: List<LocalPriceAlert>) : DrawingAlerts {
        val held = MutableStateFlow(initial)
        override val alerts: Flow<List<LocalPriceAlert>> = held

        override suspend fun remove(alerts: List<LocalPriceAlert>) {
            val ids = alerts.mapTo(mutableSetOf(), LocalPriceAlert::id)
            held.value = held.value.filterNot { it.id in ids }
        }

        override suspend fun restore(alerts: List<LocalPriceAlert>) {
            held.value = held.value + alerts
        }
    }

    private fun alert(id: String, drawingId: String, symbol: String = "BTCUSDT") = LocalPriceAlert(
        id = id,
        symbol = symbol,
        condition = LocalAlertCondition.ABOVE,
        value = 1.0,
        trigger = AlertTrigger.DrawingTouch(drawingId),
    )

    private fun line(id: Long) = Drawing(
        id = id,
        toolId = "trend",
        points = listOf(ChartPoint(1_700_000_000L, 100.0), ChartPoint(1_700_003_600L, 110.0)),
    )

    private fun controller(
        scope: TestScope,
        alerts: FakeAlerts?,
    ) = ChartController(
        symbol = "BTCUSDT",
        gateway = FakeGateway(),
        scope = scope,
        drawingAlerts = alerts,
    )

    private fun ChartController.withDrawings(vararg ids: Long) {
        onDrawing(DrawingState(drawings = ids.map(::line)))
    }

    @Test
    fun `a drawing nothing watches goes on the tap`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val alerts = FakeAlerts(listOf(alert("a", drawingId = "99")))
        val controller = controller(scope, alerts)
        controller.start()
        advanceUntilIdle()
        controller.withDrawings(1L)

        controller.deleteDrawing(1L)

        assertNull(controller.state.value.pendingDrawingDelete)
        assertTrue(controller.state.value.drawing.drawings.isEmpty())
    }

    @Test
    fun `with no gateway at all the old behaviour is exactly preserved`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val controller = controller(scope, alerts = null)
        controller.start()
        advanceUntilIdle()
        controller.withDrawings(1L)

        controller.deleteDrawing(1L)

        assertNull(controller.state.value.pendingDrawingDelete)
        assertTrue(controller.state.value.drawing.drawings.isEmpty())
    }

    @Test
    fun `a drawing with an alert on it asks first, and nothing is deleted yet`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val alerts = FakeAlerts(listOf(alert("a", drawingId = "1")))
        val controller = controller(scope, alerts)
        controller.start()
        advanceUntilIdle()
        controller.withDrawings(1L)

        controller.deleteDrawing(1L)

        val pending = controller.state.value.pendingDrawingDelete
        assertNotNull(pending)
        assertEquals(listOf(1L), pending!!.drawingIds)
        assertEquals(listOf("a"), pending.alerts.map(LocalPriceAlert::id))
        // The line is still on the chart. A question is not a deletion.
        assertEquals(listOf(1L), controller.state.value.drawing.drawings.map(Drawing::id))
    }

    @Test
    fun `deleting a selection asks once, not once per drawing`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val alerts = FakeAlerts(listOf(alert("a", drawingId = "1"), alert("b", drawingId = "2")))
        val controller = controller(scope, alerts)
        controller.start()
        advanceUntilIdle()
        controller.withDrawings(1L, 2L)

        controller.deleteDrawing(1L)
        controller.deleteDrawing(2L)

        val pending = controller.state.value.pendingDrawingDelete
        assertEquals(listOf(1L, 2L), pending?.drawingIds)
        assertEquals(listOf("a", "b"), pending?.alerts?.map(LocalPriceAlert::id))
    }

    @Test
    fun `keeping the alerts deletes the drawing and writes nothing to the store`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val alerts = FakeAlerts(listOf(alert("a", drawingId = "1")))
        val controller = controller(scope, alerts)
        controller.start()
        advanceUntilIdle()
        controller.withDrawings(1L)

        controller.deleteDrawing(1L)
        controller.confirmDrawingDelete(alsoAlerts = false)
        advanceUntilIdle()

        assertTrue(controller.state.value.drawing.drawings.isEmpty())
        assertNull(controller.state.value.pendingDrawingDelete)
        assertEquals(listOf("a"), alerts.held.value.map(LocalPriceAlert::id))
    }

    @Test
    fun `taking both takes the alerts too`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val alerts = FakeAlerts(listOf(alert("a", drawingId = "1"), alert("b", drawingId = "9")))
        val controller = controller(scope, alerts)
        controller.start()
        advanceUntilIdle()
        controller.withDrawings(1L)

        controller.deleteDrawing(1L)
        controller.confirmDrawingDelete(alsoAlerts = true)
        advanceUntilIdle()

        assertTrue(controller.state.value.drawing.drawings.isEmpty())
        // The alert on a different drawing is untouched.
        assertEquals(listOf("b"), alerts.held.value.map(LocalPriceAlert::id))
    }

    @Test
    fun `cancelling leaves the drawing and the alerts alone`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val alerts = FakeAlerts(listOf(alert("a", drawingId = "1")))
        val controller = controller(scope, alerts)
        controller.start()
        advanceUntilIdle()
        controller.withDrawings(1L)

        controller.deleteDrawing(1L)
        controller.cancelDrawingDelete()
        advanceUntilIdle()

        assertNull(controller.state.value.pendingDrawingDelete)
        assertEquals(listOf(1L), controller.state.value.drawing.drawings.map(Drawing::id))
        assertEquals(listOf("a"), alerts.held.value.map(LocalPriceAlert::id))
    }

    @Test
    fun `the undo beside the toast puts the alerts back`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val alerts = FakeAlerts(listOf(alert("a", drawingId = "1")))
        val controller = controller(scope, alerts)
        controller.start()
        advanceUntilIdle()
        controller.withDrawings(1L)

        controller.deleteDrawing(1L)
        val removed = controller.state.value.pendingDrawingDelete!!.alerts
        controller.confirmDrawingDelete(alsoAlerts = true)
        advanceUntilIdle()
        assertTrue(alerts.held.value.isEmpty())

        controller.restoreAlerts(removed)
        advanceUntilIdle()

        assertEquals(listOf("a"), alerts.held.value.map(LocalPriceAlert::id))
    }

    @Test
    fun `an alert on another symbol's drawing of the same id does not stop the delete`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val alerts = FakeAlerts(listOf(alert("a", drawingId = "1", symbol = "XAUUSD")))
        val controller = controller(scope, alerts)
        controller.start()
        advanceUntilIdle()
        controller.withDrawings(1L)

        controller.deleteDrawing(1L)

        assertNull(controller.state.value.pendingDrawingDelete)
        assertTrue(controller.state.value.drawing.drawings.isEmpty())
    }

    @Test
    fun `the undo beside a deleted drawing brings it back, which it never did before`() = runTest {
        // The toast has offered «واگرد» since run Ω2 and it called an undo whose stack this
        // deletion never pushed to: `deleteDrawing` wrote the new layer straight into the state,
        // bypassing `onDrawing`, which is the one place a drawing step was recorded. So the button
        // either did nothing or took back an unrelated change. This is the assertion that catches
        // it, and it failed before `removeDrawings` began recording.
        //
        // The name is plain ASCII on purpose: Kotlin compiles a backticked test name into a
        // class *file* name, and an em dash in one makes the compiler throw
        // `InvalidPathException: Malformed input` on a JVM whose default charset is not UTF-8.
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val controller = controller(scope, alerts = null)
        controller.start()
        advanceUntilIdle()
        controller.withDrawings(1L)

        controller.deleteDrawing(1L)
        assertTrue(controller.state.value.drawing.drawings.isEmpty())
        controller.undo()

        assertEquals(listOf(1L), controller.state.value.drawing.drawings.map(Drawing::id))
    }

    @Test
    fun `deleting a selection is one step, so one undo brings all of it back`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val controller = controller(scope, alerts = null)
        controller.start()
        advanceUntilIdle()
        controller.withDrawings(1L, 2L, 3L)

        controller.deleteDrawings(listOf(1L, 2L, 3L))
        assertTrue(controller.state.value.drawing.drawings.isEmpty())
        controller.undo()

        assertEquals(listOf(1L, 2L, 3L), controller.state.value.drawing.drawings.map(Drawing::id))
    }

    @Test
    fun `and the undo after the dialog restores every drawing it took`() = runTest {
        val scope = TestScope(StandardTestDispatcher(testScheduler))
        val alerts = FakeAlerts(listOf(alert("a", drawingId = "2")))
        val controller = controller(scope, alerts)
        controller.start()
        advanceUntilIdle()
        controller.withDrawings(1L, 2L)

        controller.deleteDrawings(listOf(1L, 2L))
        controller.confirmDrawingDelete(alsoAlerts = true)
        advanceUntilIdle()
        assertTrue(controller.state.value.drawing.drawings.isEmpty())

        controller.undo()

        assertEquals(listOf(1L, 2L), controller.state.value.drawing.drawings.map(Drawing::id))
    }
}
