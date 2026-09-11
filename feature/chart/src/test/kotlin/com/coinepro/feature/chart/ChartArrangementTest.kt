package com.coinepro.feature.chart

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.coinepro.core.chart.ChartLegendTarget
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.ToolGroup
import com.coinepro.core.datastore.SymbolChartState
import com.coinepro.core.datastore.SymbolChartStateStore
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.marketdata.Timeframe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Run E's state: every parameter of a study, the panes as the reader arranged them, and the
 * rail's memory of the last tool per group — each one held, clamped, and written back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartArrangementTest {

    private class FakePreferences : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    private class FakeGateway : CandleGateway {
        override suspend fun load(symbol: String, timeframe: Timeframe, limit: Int, before: Long?): CandlePage =
            CandlePage(
                symbol,
                timeframe,
                (0 until 300).map { index ->
                    val t = 1_000L + index * timeframe.seconds
                    val c = 100.0 + (index % 17) * 0.4 - (index % 5) * 0.3
                    OhlcBar(t = t, o = c - 0.2, h = c + 0.6, l = c - 0.7, c = c, v = 10.0 + index % 7)
                },
            )
    }

    private fun controller(scope: TestScope, states: SymbolChartStateStore? = null) =
        ChartController("BTCUSDT", FakeGateway(), scope, symbolStates = states)

    @Test
    fun `a parameter is clamped, the default is not stored, and the length goes through the period`() = runTest {
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()
        controller.toggleIndicator("macd")

        controller.setIndicatorParam("macd", "fast", 8.0)
        assertEquals(mapOf("fast" to 8.0), controller.state.value.indicatorParams["macd"])
        assertTrue(controller.state.value.panes.any { it.title == "MACD 8/26/9" })

        controller.setIndicatorParam("macd", "slow", 9_999.0)
        assertEquals(400.0, controller.state.value.indicatorParams.getValue("macd").getValue("slow"), 0.0)

        controller.setIndicatorParam("macd", "fast", 12.0)
        controller.setIndicatorParam("macd", "slow", null)
        assertNull("the defaults leave nothing behind", controller.state.value.indicatorParams["macd"])
        assertTrue(controller.state.value.panes.any { it.title == "MACD 12/26/9" })

        controller.toggleIndicator("bollinger")
        controller.setIndicatorParam("bollinger", "length", 50.0)
        assertEquals(50, controller.state.value.indicatorPeriods["bollinger"])
        assertNull(controller.state.value.indicatorParams["bollinger"])

        controller.setIndicatorParam("macd", "no-such-knob", 3.0)
        assertNull(controller.state.value.indicatorParams["macd"])
    }

    @Test
    fun `panes move, merge under a joint title, and an overlay can be set apart`() = runTest {
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()
        listOf("rsi", "macd", "atr", "ema").forEach(controller::toggleIndicator)
        assertEquals(listOf("rsi", "macd", "atr"), controller.state.value.paneOwnersShown)

        controller.movePane("atr", up = true)
        assertEquals(listOf("rsi", "atr", "macd"), controller.state.value.paneOwnersShown)
        controller.movePane("rsi", up = true)
        assertEquals("the top pane stays where it is", listOf("rsi", "atr", "macd"), controller.state.value.paneOwnersShown)

        controller.mergePane("macd", into = "atr")
        val state = controller.state.value
        assertEquals(listOf("rsi", "atr"), state.paneOwnersShown)
        assertEquals(2, state.panes.size)
        val joint = state.panes[1]
        assertTrue(joint.title, joint.title.startsWith("ATR") && joint.title.contains(" · MACD"))
        assertTrue("the guest's lines ride inside the host", joint.lines.size > 1)
        assertEquals("the legend's row resolves to the host", "atr", state.indicatorFor(ChartLegendTarget.Pane(1)))

        controller.mergePane("macd", into = null)
        assertEquals(3, controller.state.value.panes.size)

        // A merge into a pane that is itself a guest is refused: chains would have no host.
        controller.mergePane("macd", into = "atr")
        controller.mergePane("rsi", into = "macd")
        assertFalse("rsi" in controller.state.value.paneMerges)

        controller.separateOverlay("ema", separate = true)
        val apart = controller.state.value
        assertTrue("ema" in apart.separated)
        assertTrue("the overlay left the price pane", apart.overlays.none { it.label.orEmpty().startsWith("EMA") })
        assertTrue("and became a pane", apart.panes.any { it.title.startsWith("EMA") })
        assertTrue("ema" in apart.paneOwnersShown)

        controller.separateOverlay("ema", separate = false)
        assertTrue(controller.state.value.overlays.any { it.label.orEmpty().startsWith("EMA") })
    }

    @Test
    fun `the zoom is remembered per timeframe and restored onto the same one`() = runTest {
        val states = SymbolChartStateStore(FakePreferences())
        val first = controller(TestScope(StandardTestDispatcher(testScheduler)), states)
        first.start()
        advanceUntilIdle()

        first.setZoom(40)
        first.setTimeframe(Timeframe.D1)
        advanceUntilIdle()
        first.setZoom(200)
        advanceUntilIdle()

        val saved = states.state("BTCUSDT").first()!!
        assertEquals(mapOf("H1" to 40, "D1" to 200), saved.zoom)

        // Out of the bounds this build draws at: dropped rather than clamped, so a row written by
        // a build with a wider range cannot move this reader's chart on their behalf.
        first.setZoom(99_999)
        advanceUntilIdle()
        assertEquals(200, states.state("BTCUSDT").first()!!.zoom["D1"])

        val second = controller(TestScope(StandardTestDispatcher(testScheduler)), states)
        second.start()
        advanceUntilIdle()
        // Opens on D1 — the timeframe the reader left it on — and therefore on D1's own zoom.
        assertEquals(200, second.state.value.barsPerView)
        second.setTimeframe(Timeframe.H1)
        advanceUntilIdle()
        assertEquals(40, second.state.value.barsPerView)
        second.setTimeframe(Timeframe.H4)
        advanceUntilIdle()
        assertNull("a timeframe never zoomed opens on the default", second.state.value.barsPerView)
    }

    @Test
    fun `the arrangement, the parameters and the rail's memory are written back and restored`() = runTest {
        val states = SymbolChartStateStore(FakePreferences())
        val first = controller(TestScope(StandardTestDispatcher(testScheduler)), states)
        first.start()
        advanceUntilIdle()
        listOf("rsi", "macd", "ema").forEach(first::toggleIndicator)
        first.setIndicatorParam("macd", "signal", 7.0)
        first.movePane("macd", up = true)
        first.mergePane("rsi", into = "macd")
        first.separateOverlay("ema", separate = true)
        first.arm(DrawingTools["hline"])
        first.arm(DrawingTools["ray"])
        first.arm(null)
        advanceUntilIdle()

        val saved: SymbolChartState = states.state("BTCUSDT").first()!!
        assertEquals(mapOf("macd" to mapOf("signal" to 7.0)), saved.indicatorParams)
        assertEquals(listOf("macd", "rsi"), saved.paneOrder)
        assertEquals(mapOf("rsi" to "macd"), saved.paneMerges)
        assertEquals(listOf("ema"), saved.separatedIndicators)
        assertEquals(mapOf("LINES" to "ray"), saved.toolLastUsed)

        val second = controller(TestScope(StandardTestDispatcher(testScheduler)), states)
        second.start()
        advanceUntilIdle()
        val restored = second.state.value
        assertEquals(mapOf("macd" to mapOf("signal" to 7.0)), restored.indicatorParams)
        assertTrue(restored.panes.any { it.title.startsWith("MACD 12/26/7") })
        assertEquals(listOf("macd", "ema"), restored.paneOwnersShown)
        assertEquals(mapOf("rsi" to "macd"), restored.paneMerges)
        assertEquals(setOf("ema"), restored.separated)
        assertEquals(mapOf(ToolGroup.LINES to "ray"), restored.drawing.lastUsed)
    }
}
