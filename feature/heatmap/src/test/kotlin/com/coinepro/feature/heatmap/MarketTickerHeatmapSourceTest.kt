package com.coinepro.feature.heatmap

import com.coinepro.core.marketdata.MarketCatalog
import com.coinepro.core.marketdata.MarketCatalogGateway
import com.coinepro.core.marketdata.MarketSearchController
import com.coinepro.core.marketdata.MarketTicker
import com.coinepro.core.marketdata.MarketTickerGateway
import com.coinepro.core.marketdata.MarketTickerStore
import com.coinepro.core.marketdata.MarketTickerTable
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.symbols.SymbolClassifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The map on the day's table: one request for the whole catalogue instead of one per tile.
 *
 * What is pinned here is the seam between `core:marketdata`'s shared store and the map's own ticker
 * type, and every case is one the two platforms genuinely differ on. TradeYar serves the table;
 * CoinePro-FX has no such route and must keep drawing exactly the map it drew before, from candles.
 * The third case — a row that carries a price and nothing else — is the contract the route was
 * written to: an absent field is a field nobody knows, and it has to stay absent all the way to the
 * hatching on the tile.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MarketTickerHeatmapSourceTest {

    private val symbols = listOf("BTCUSDT", "ETHUSDT")

    private class FakeTickerGateway(
        override val supported: Boolean,
        private val answer: () -> MarketTickerTable,
    ) : MarketTickerGateway {
        var calls = 0

        override suspend fun load(symbols: List<String>?): MarketTickerTable {
            calls += 1
            return answer()
        }
    }

    private fun table(vararg rows: MarketTicker) = MarketTickerTable(
        tickers = rows.associateBy(MarketTicker::symbol),
        serverTimeEpochMillis = 1L,
        cacheTtlMillis = 5_000L,
        fetchedAtEpochMillis = 1L,
        source = "lbank",
    )

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
    fun `the venue's day wins over the one derived from two daily closes`() = runTest {
        // Both sources can answer a change here and they do not agree — the bars say the market is
        // up eleven percent from yesterday's close, the venue says minus three and a half over its
        // own rolling window. The venue is what the exchange's own site prints and what a reader
        // holds this map against, so it is the one that reaches the tile.
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val gateway = FakeTickerGateway(supported = true) {
            table(
                *symbols.map { symbol ->
                    MarketTicker(
                        symbol = symbol,
                        last = 100.0,
                        changePercent24h = -3.5,
                        high24h = 104.0,
                        low24h = 97.0,
                        volume24h = 12.0,
                        turnover24h = 1_200.0,
                        fundingRate = 0.00009263,
                    )
                }.toTypedArray(),
            )
        }
        val store = MarketTickerStore(gateway, scope)
        val controller = HeatmapController(
            search = MarketSearchController(catalogue(), scope),
            scope = scope,
            bars = HeatmapBarSource { bars() },
            tickers = MarketTickerHeatmapSource(store),
        )
        controller.start()
        advanceUntilIdle()

        assertEquals("the whole catalogue is one request, not one per market", 1, gateway.calls)
        controller.state.value.assets.forEach { asset ->
            assertEquals(-3.5, asset.changePercent!!, 0.0)
            assertEquals(104.0, asset.dayHigh!!, 0.0)
            assertEquals(12.0, asset.volume!!, 0.0)
            // Turnover is the venue's own quote-currency figure and never the base volume: across a
            // mixed list, sorting by volume compares a count of bitcoin against a count of dogecoin.
            assertEquals(1_200.0, asset.turnover!!, 0.0)
            // A fraction on the wire, a percentage in the field that is named for one.
            assertEquals(0.009263, asset.fundingRatePercent!!, 1e-9)
            // The period return is the figure a rolling twenty-four-hour window cannot carry, so
            // the candles are still what answers it — and with two bars, it cannot be answered.
            assertNull(HeatmapMetrics.valueOf(asset, HeatmapColour.PERFORMANCE))
        }
    }

    @Test
    fun `a row carrying only a price leaves every other figure unknown`() = runTest {
        // The route omits rather than zeroes, deliberately: a market with no change percent is a
        // market nobody knows about, not a flat one. With no bars behind it either, the tile has to
        // draw hatched rather than take the neutral middle of the ramp.
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val gateway = FakeTickerGateway(supported = true) {
            table(*symbols.map { MarketTicker(symbol = it, last = 100.0) }.toTypedArray())
        }
        val controller = HeatmapController(
            search = MarketSearchController(catalogue(), scope),
            scope = scope,
            bars = null,
            tickers = MarketTickerHeatmapSource(MarketTickerStore(gateway, scope)),
        )
        controller.start()
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(symbols.size, state.assets.size)
        // Read, and still without an answer. Those are two different things and the coverage line
        // above the map says the first while every tile draws the second.
        assertEquals(symbols.size, state.resolved)
        state.assets.forEach { asset ->
            assertNull(asset.volume)
            assertNull(asset.turnover)
            assertNull(asset.fundingRatePercent)
            HeatmapColour.entries.forEach { colour ->
                assertNull("$colour must not invent an answer", HeatmapMetrics.valueOf(asset, colour))
            }
        }
    }

    @Test
    fun `a platform with no such route falls straight back to its candles`() = runTest {
        // CoinePro-FX. The source answers empty without waiting for a table that is never coming,
        // and the map is exactly the map that shipped: every figure derived from daily bars.
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val gateway = FakeTickerGateway(supported = false) { MarketTickerTable.Empty }
        var candleRequests = 0
        val controller = HeatmapController(
            search = MarketSearchController(catalogue(), scope),
            scope = scope,
            bars = HeatmapBarSource {
                candleRequests += 1
                bars()
            },
            tickers = MarketTickerHeatmapSource(MarketTickerStore(gateway, scope)),
        )
        controller.start()
        advanceUntilIdle()

        assertEquals("a platform without the route must never be polled for it", 0, gateway.calls)
        assertEquals(symbols.size, candleRequests)
        controller.state.value.assets.forEach { asset ->
            // Eleven percent, from the previous daily close against the live price — the derivation
            // this platform has always used and still has to.
            assertNotNull(asset.changePercent)
            assertTrue(asset.changePercent!! > 0.0)
        }
    }

    @Test
    fun `the shared store is let go of once the answer is in`() = runTest {
        // The store is reference counted and polls for as long as anybody is holding it. This
        // source is asked once per map open rather than subscribed to, so leaving the count raised
        // would keep a five-second poll running against the whole catalogue for the rest of the
        // process, for a map that has been closed.
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val gateway = FakeTickerGateway(supported = true) {
            table(MarketTicker(symbol = "BTCUSDT", last = 100.0, changePercent24h = 1.0))
        }
        val store = MarketTickerStore(gateway, scope)
        val loaded = MarketTickerHeatmapSource(store).tickers()
        advanceUntilIdle()

        assertEquals(1, loaded.size)
        val afterAnswer = gateway.calls
        // Six times the interval the store polls at. `advanceTimeBy` rather than `advanceUntilIdle`
        // on purpose: a poll that was never stopped has no idle to advance to, so the second would
        // hang here instead of failing and nobody would know which of the two had happened.
        testScheduler.advanceTimeBy(30_000)
        testScheduler.runCurrent()
        assertEquals("the poll outlived the reader", afterAnswer, gateway.calls)
    }
}
