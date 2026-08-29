package com.coinepro.feature.heatmap

import com.coinepro.core.marketdata.MarketCatalog
import com.coinepro.core.marketdata.MarketCatalogGateway
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.symbols.SymbolClassifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
 * The resolution path: how a map with no figures on it becomes a map with figures on it.
 *
 * Three properties matter here and none of them is visible in the arithmetic. The map must draw
 * before any candle has landed, or the screen is a spinner over a catalogue the app already has.
 * It must not ask for the same market twice, or a scroll becomes a denial of service against our
 * own server. And it must say, in its own state, whether the tiles it is showing as unknown are
 * still filling in or never will be — those two look identical on the canvas.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HeatmapControllerTest {

    private val symbols = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT")

    private fun catalogue() = object : MarketCatalogGateway {
        override suspend fun load() = MarketCatalog(
            markets = symbols.map(SymbolClassifier::classify),
            quotes = symbols.associateWith { symbol ->
                MarketQuote(
                    instrument = Instrument(symbol, symbol, MarketType.CRYPTO),
                    price = 100.0,
                    timestampEpochMillis = 0L,
                )
            },
            serverTimeEpochMillis = null,
        )
    }

    /** Two closed days and one open one, which is the least that can answer a day's move. */
    private fun bars() = listOf(
        OhlcBar(t = 0L, o = 90.0, h = 95.0, l = 88.0, c = 90.0, v = 5.0),
        OhlcBar(t = 1L, o = 90.0, h = 101.0, l = 90.0, c = 96.0, v = 7.0),
    )

    @Test
    fun `the map draws from the catalogue before a single candle has landed`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val search = MarketSearchController(catalogue(), scope)
        val controller = HeatmapController(search, scope, bars = null)
        controller.start()
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(symbols.size, state.assets.size)
        assertFalse("no source was given, so nothing can ever resolve", state.canResolve)
        assertEquals(0, state.resolved)
        // Every tile hatched, and none of them claiming to be flat.
        state.assets.forEach { assertNull(HeatmapMetrics.valueOf(it, HeatmapColour.CHANGE)) }
    }

    @Test
    fun `bars turn into figures, and every market is asked for exactly once`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val asked = mutableListOf<String>()
        val source = HeatmapBarSource { symbol ->
            asked += symbol
            bars()
        }
        val search = MarketSearchController(catalogue(), scope)
        val controller = HeatmapController(search, scope, source)
        controller.start()
        advanceUntilIdle()

        val state = controller.state.value
        assertTrue(state.canResolve)
        assertEquals(symbols.size, state.resolved)
        assertFalse(state.resolving)
        assertEquals(symbols.sorted(), asked.sorted())
        assertEquals("a market was asked for twice", asked.size, asked.toSet().size)
        state.assets.forEach {
            assertNotNull(HeatmapMetrics.valueOf(it, HeatmapColour.CHANGE))
        }
    }

    @Test
    fun `a market whose bars come back empty is still counted as read, not as pending`() = runTest {
        // Otherwise the coverage line above the map counts it as arriving forever, and a reader
        // waits for a figure that is never coming.
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val search = MarketSearchController(catalogue(), scope)
        val controller = HeatmapController(search, scope, HeatmapBarSource { emptyList() })
        controller.start()
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(symbols.size, state.resolved)
        state.assets.forEach { assertNull(HeatmapMetrics.valueOf(it, HeatmapColour.CHANGE)) }
    }

    @Test
    fun `a ticker answers the whole catalogue in one call and outranks the bars`() = runTest {
        // The route this expects does not exist yet — see the module's `## SERVER ASKS`. The path
        // is asserted now so that wiring it is one argument rather than a rewrite, and so that the
        // precedence is fixed before there is a second implementation to argue with it.
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        var calls = 0
        val tickers = HeatmapTickerSource {
            calls++
            symbols.associateWith { symbol ->
                HeatmapTicker(symbol = symbol, changePercent = -3.5, volume = 12.0)
            }
        }
        val search = MarketSearchController(catalogue(), scope)
        val controller = HeatmapController(search, scope, HeatmapBarSource { bars() }, tickers)
        controller.start()
        advanceUntilIdle()

        assertEquals("the ticker route is a batch, not a per-symbol lookup", 1, calls)
        controller.state.value.assets.forEach {
            // The venue's own window, not the one derived from two daily closes.
            assertEquals(-3.5, it.changePercent!!, 0.0)
            assertEquals(12.0, it.volume!!, 0.0)
        }
    }

    @Test
    fun `the performance window is recomputed from bars already held, with no new request`() =
        runTest {
            val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
            var calls = 0
            val long = List(40) { OhlcBar(t = it.toLong(), o = 100.0, h = 100.0, l = 100.0, c = 100.0, v = 1.0) }
            val search = MarketSearchController(catalogue(), scope)
            val controller = HeatmapController(
                search,
                scope,
                HeatmapBarSource {
                    calls++
                    long
                },
            )
            controller.start()
            advanceUntilIdle()
            val afterLoad = calls

            controller.setPeriod(HeatmapPeriod.QUARTER)
            advanceUntilIdle()
            assertEquals("changing the window must not refetch", afterLoad, calls)
            // Forty bars cannot answer a ninety-bar question, and the honest answer is nothing.
            controller.state.value.assets.forEach {
                assertNull(HeatmapMetrics.valueOf(it, HeatmapColour.PERFORMANCE))
            }

            controller.setPeriod(HeatmapPeriod.MONTH)
            advanceUntilIdle()
            controller.state.value.assets.forEach {
                assertNotNull(HeatmapMetrics.valueOf(it, HeatmapColour.PERFORMANCE))
            }
        }
}
