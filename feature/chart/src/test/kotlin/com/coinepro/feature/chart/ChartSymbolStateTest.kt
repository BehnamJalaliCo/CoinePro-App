package com.coinepro.feature.chart

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.coinepro.core.chart.PriceScaleMode
import com.coinepro.core.datastore.ChartLayout
import com.coinepro.core.datastore.ChartLayoutStore
import com.coinepro.core.datastore.SymbolChartState
import com.coinepro.core.datastore.SymbolChartStateStore
import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.CandlePage
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.CustomInterval
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The per-symbol chart state, end to end through a real store on a fake preferences file.
 *
 * A real [SymbolChartStateStore] rather than a stub of it, because half the value of this feature
 * is in the encoding — an interval that round-trips as a `String` and comes back as a different
 * interval would pass every test written against a stub and fail on a phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChartSymbolStateTest {

    /** A preferences file that lives in memory. `edit` goes through `updateData`, so it works. */
    private class FakePreferences : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    private class FakeGateway : CandleGateway {
        val calls = mutableListOf<Pair<String, Timeframe>>()

        override suspend fun load(
            symbol: String,
            timeframe: Timeframe,
            limit: Int,
            before: Long?,
        ): CandlePage {
            calls += symbol to timeframe
            return CandlePage(symbol, timeframe, bars(count = 40, step = timeframe.seconds))
        }
    }


    private fun controller(
        scope: TestScope,
        gateway: CandleGateway = FakeGateway(),
        states: SymbolChartStateStore? = null,
        layouts: ChartLayoutStore? = null,
        symbol: String = "BTCUSDT",
    ) = ChartController(
        symbol = symbol,
        gateway = gateway,
        scope = scope,
        symbolStates = states,
        layoutStore = layouts,
    )

    @Test
    fun `a saved timeframe is applied before the first fetch, not after it`() = runTest {
        // The whole point of the feature. A restore that landed after the load would still end in
        // the right place, having first requested and drawn an hour of bars nobody asked for.
        val states = SymbolChartStateStore(FakePreferences())
        states.put(SymbolChartState(symbol = "BTCUSDT", timeframe = "M15", updatedAt = 1L))
        val gateway = FakeGateway()
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)), gateway, states)

        controller.start()
        advanceUntilIdle()

        assertEquals(1, gateway.calls.size)
        assertEquals(Timeframe.M15, gateway.calls.single().second)
        assertEquals("M15", controller.state.value.interval.wire)
    }

    @Test
    fun `a saved chart type, indicator set, periods and scale mode all come back`() = runTest {
        val states = SymbolChartStateStore(FakePreferences())
        states.put(
            SymbolChartState(
                symbol = "BTCUSDT",
                timeframe = "H4",
                chartType = "LINE",
                indicators = listOf("ema", "not_an_indicator"),
                indicatorPeriods = mapOf("ema" to 21),
                scaleMode = "PERCENT",
                updatedAt = 1L,
            ),
        )
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)), states = states)

        controller.start()
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals("H4", state.interval.wire)
        assertEquals("LINE", state.chartType.name)
        // The unknown id is dropped and the known one kept: losing one line is better than losing
        // the whole restore.
        assertEquals(setOf("ema"), state.activeIndicators)
        assertEquals(mapOf("ema" to 21), state.indicatorPeriods)
        assertEquals(PriceScaleMode.PERCENT, state.scaleMode)
    }

    @Test
    fun `changing the interval writes it back under this symbol and no other`() = runTest {
        val states = SymbolChartStateStore(FakePreferences())
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)), states = states)

        controller.start()
        advanceUntilIdle()
        controller.setTimeframe(Timeframe.D1)
        advanceUntilIdle()

        assertEquals("D1", states.state("BTCUSDT").first()?.timeframe)
        assertNull(states.state("ETHUSDT").first())
    }

    @Test
    fun `a custom minute interval survives the round trip as its own bare minute count`() = runTest {
        val states = SymbolChartStateStore(FakePreferences())
        val first = controller(TestScope(StandardTestDispatcher(testScheduler)), states = states)

        first.start()
        advanceUntilIdle()
        first.setInterval(ChartInterval.Custom(CustomInterval(205)))
        advanceUntilIdle()

        assertEquals("205", states.state("BTCUSDT").first()?.timeframe)

        val second = controller(TestScope(StandardTestDispatcher(testScheduler)), states = states)
        second.start()
        advanceUntilIdle()

        assertEquals(ChartInterval.Custom(CustomInterval(205)), second.state.value.interval)
    }

    @Test
    fun `the scale mode is stored by name, and the log flag stays true to it`() = runTest {
        val states = SymbolChartStateStore(FakePreferences())
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)), states = states)

        controller.start()
        advanceUntilIdle()
        controller.setScaleMode(PriceScaleMode.PERCENT)
        advanceUntilIdle()

        val stored = states.state("BTCUSDT").first()
        assertEquals("PERCENT", stored?.scaleMode)
        // Percent is not logarithmic, and a widget reading only the boolean must not be told it is.
        assertFalse(stored?.logScale ?: true)

        controller.setScaleMode(PriceScaleMode.LOGARITHMIC)
        advanceUntilIdle()
        assertTrue(states.state("BTCUSDT").first()?.logScale ?: false)
    }

    @Test
    fun `a row written before the axis had four modes restores from its boolean alone`() = runTest {
        val states = SymbolChartStateStore(FakePreferences())
        states.put(SymbolChartState(symbol = "BTCUSDT", logScale = true, updatedAt = 1L))
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)), states = states)

        controller.start()
        advanceUntilIdle()

        assertEquals(PriceScaleMode.LOGARITHMIC, controller.state.value.scaleMode)
    }

    @Test
    fun `a layout restores a custom interval from its wire spelling`() = runTest {
        // `205` is not the name of any enum entry, which is precisely why matching on enum names
        // used to lose it silently and leave the reader on whatever they were looking at.
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        controller.applyLayout(layout(timeframe = "205"))
        advanceUntilIdle()

        assertEquals(ChartInterval.Custom(CustomInterval(205)), controller.state.value.interval)
    }

    @Test
    fun `a layout restores the monthly preset, which the old eight never had`() = runTest {
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        controller.applyLayout(layout(timeframe = "MN1"))
        advanceUntilIdle()

        assertEquals(ChartInterval.Preset(Timeframe.MN1), controller.state.value.interval)
    }

    @Test
    fun `a layout carries the periods and the scale mode, not only the indicator ids`() = runTest {
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        controller.applyLayout(
            layout(
                timeframe = "H4",
                indicators = listOf("ema", "rsi"),
                periods = mapOf("ema" to 21),
                scaleMode = "LOGARITHMIC",
            ),
        )
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(setOf("ema", "rsi"), state.activeIndicators)
        assertEquals(21, state.indicatorPeriods["ema"])
        assertEquals(PriceScaleMode.LOGARITHMIC, state.scaleMode)
    }

    @Test
    fun `applying a layout records it as the one to come back to`() = runTest {
        val layouts = ChartLayoutStore(FakePreferences())
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)), layouts = layouts)
        controller.start()
        advanceUntilIdle()

        controller.applyLayout(layout(id = "scalping"))
        advanceUntilIdle()

        assertEquals("scalping", layouts.lastOpened().first())
    }

    @Test
    fun `with nothing saved for this symbol, the last layout the reader used opens the chart`() = runTest {
        val layouts = ChartLayoutStore(FakePreferences())
        layouts.save(layout(id = "swing", timeframe = "D1", indicators = listOf("ema")))
        layouts.setLastOpened("swing")
        val gateway = FakeGateway()
        val controller = controller(
            TestScope(StandardTestDispatcher(testScheduler)),
            gateway = gateway,
            states = SymbolChartStateStore(FakePreferences()),
            layouts = layouts,
        )

        controller.start()
        advanceUntilIdle()

        assertEquals("D1", controller.state.value.interval.wire)
        assertEquals(setOf("ema"), controller.state.value.activeIndicators)
        // And exactly one fetch: the layout is put on before the load rather than triggering a
        // second one over the top of the first.
        assertEquals(1, gateway.calls.size)
    }

    @Test
    fun `this symbol's own saved state beats the last layout used`() = runTest {
        val layouts = ChartLayoutStore(FakePreferences())
        layouts.save(layout(id = "swing", timeframe = "D1"))
        layouts.setLastOpened("swing")
        val states = SymbolChartStateStore(FakePreferences())
        states.put(SymbolChartState(symbol = "BTCUSDT", timeframe = "M5", updatedAt = 1L))
        val controller = controller(
            TestScope(StandardTestDispatcher(testScheduler)),
            states = states,
            layouts = layouts,
        )

        controller.start()
        advanceUntilIdle()

        assertEquals("M5", controller.state.value.interval.wire)
    }

    @Test
    fun `the state describes itself as a layout, periods and axis included`() = runTest {
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()
        controller.setTimeframe(Timeframe.H4)
        controller.toggleIndicator("ema")
        controller.setIndicatorPeriod("ema", 21)
        controller.setScaleMode(PriceScaleMode.LOGARITHMIC)
        advanceUntilIdle()

        val saved = controller.state.value.toLayout(
            id = "one",
            name = "چیدمان من",
            createdAt = 10L,
            updatedAt = 20L,
        )

        assertEquals("H4", saved.timeframe)
        assertEquals(listOf("ema"), saved.indicators)
        assertEquals(mapOf("ema" to 21), saved.indicatorPeriods)
        assertEquals("LOGARITHMIC", saved.scaleMode)
        assertEquals(10L, saved.createdAt)
        assertEquals(20L, saved.updatedAt)
    }

    @Test
    fun `a folded interval reports that its pages are short, and a native one does not`() = runTest {
        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        assertFalse(controller.state.value.historyTruncated)

        controller.setInterval(ChartInterval.Custom(CustomInterval(205)))
        advanceUntilIdle()

        // 205 minutes is forty-one five-minute bars, so a thousand-bar page is twenty-four of them.
        assertTrue(controller.state.value.historyTruncated)
    }

    @Test
    fun `chart vision is offered on the six it reads and refused with a reason on the rest`() = runTest {
        // Mirrors `AiVisionTimeframes.supported`. If that set moves, this fails rather than the
        // app forwarding an interval the endpoint answers with an error.
        assertEquals(setOf("M1", "M5", "M15", "H1", "H4", "D1"), AI_VISION_INTERVALS)

        val controller = controller(TestScope(StandardTestDispatcher(testScheduler)))
        controller.start()
        advanceUntilIdle()

        assertEquals("H1", controller.state.value.aiVisionWire)
        assertNull(controller.state.value.aiVisionRefusal)

        controller.setTimeframe(Timeframe.H3)
        advanceUntilIdle()
        assertNull(controller.state.value.aiVisionWire)
        assertNotNull(controller.state.value.aiVisionRefusal)

        controller.setInterval(ChartInterval.Custom(CustomInterval(205)))
        advanceUntilIdle()
        assertNull(controller.state.value.aiVisionWire)
    }

    private fun layout(
        id: String = "layout",
        timeframe: String = "H1",
        indicators: List<String> = emptyList(),
        periods: Map<String, Int> = emptyMap(),
        scaleMode: String = "",
    ) = ChartLayout(
        id = id,
        name = "چیدمان",
        symbol = "BTCUSDT",
        timeframe = timeframe,
        chartType = "CANDLES",
        indicators = indicators,
        indicatorPeriods = periods,
        scaleMode = scaleMode,
        createdAt = 1L,
        updatedAt = 1L,
    )
}

/**
 * A flat series, at whatever spacing the timeframe asks for.
 *
 * File scope rather than a member: `FakeGateway` is a nested non-inner class and cannot reach the
 * enclosing test's methods, and making it `inner` to fix that would tie the fake to an instance it
 * has no use for.
 */
private fun bars(count: Int, step: Long): List<OhlcBar> = (0 until count).map { index ->
    OhlcBar(t = 1_700_000_000L + index * step, o = 100.0, h = 101.0, l = 99.0, c = 100.5, v = 7.0)
}
