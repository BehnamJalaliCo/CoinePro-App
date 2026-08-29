package com.coinepro.feature.screener

import com.coinepro.core.marketdata.MarketCatalog
import com.coinepro.core.marketdata.MarketCatalogGateway
import com.coinepro.core.marketdata.MarketSnapshot
import com.coinepro.core.marketdata.MarketSnapshotGateway
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.symbols.SymbolClassifier
import com.coinepro.feature.screener.model.NumericOp
import com.coinepro.feature.screener.model.ScreenerField
import com.coinepro.feature.screener.model.ScreenerFilter
import com.coinepro.feature.screener.model.ScreenerRow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The controller's contract, with one test carrying most of the weight.
 *
 * A screener that polls every row in the catalogue is not a slower screener; it is an outage
 * against our own backend, and it would work perfectly on any fixture small enough to write by
 * hand. So the visible-window rule is pinned here explicitly rather than left to be noticed on a
 * real device with a thousand markets in it.
 */
class ScreenerControllerTest {

    private val universe = listOf(
        "BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT",
        "ADAUSDT", "DOGEUSDT", "TRXUSDT",
    )

    private fun quote(symbol: String, price: Double) = MarketQuote(
        instrument = Instrument(symbol, symbol, MarketType.CRYPTO),
        price = price,
        timestampEpochMillis = 0L,
    )

    /**
     * A catalogue of the eight coins, with whatever prices the test wants on them.
     *
     * An object expression rather than a lambda: `MarketCatalogGateway` is an ordinary interface,
     * not a `fun interface`, so it does not convert from one.
     */
    private fun catalogueOf(quotes: Map<String, MarketQuote>) = object : MarketCatalogGateway {
        override suspend fun load() = MarketCatalog(
            markets = SymbolClassifier.classifyAll(universe),
            quotes = quotes,
            serverTimeEpochMillis = null,
        )
    }

    /** Every market quoted at a hundred, which is what the live-price tests start from. */
    private val catalogue = catalogueOf(universe.associateWith { quote(it, 100.0) })

    /**
     * The catalogue with no prices at all.
     *
     * Used wherever the test is about figures derived from bars: a live quote deliberately wins over
     * a bar's close in [ScreenerMetrics], so a fixture that supplies both is testing the wrong one.
     */
    private val catalogueWithoutPrices = catalogueOf(emptyMap())

    /** Records every symbol list it is asked for, which is the whole point of the first test. */
    private class RecordingSnapshotGateway : MarketSnapshotGateway {
        val requests = mutableListOf<List<String>>()

        override suspend fun load(symbols: List<String>): MarketSnapshot {
            requests += symbols
            return MarketSnapshot(
                quotes = symbols.map {
                    MarketQuote(
                        instrument = Instrument(it, it, MarketType.CRYPTO),
                        price = 101.0,
                        timestampEpochMillis = 0L,
                    )
                },
                serverTimeEpochMillis = null,
            )
        }
    }

    /** A flat two-day series, so every market resolves with a known change. */
    private fun bars(previousClose: Double, close: Double): List<OhlcBar> = listOf(
        OhlcBar(t = 0, o = previousClose, h = previousClose, l = previousClose, c = previousClose, v = 10.0),
        OhlcBar(t = 86_400, o = previousClose, h = close, l = previousClose, c = close, v = 20.0),
    )

    @Test
    fun `quotes are requested for the visible rows and for nothing else`() = runTest {
        val snapshots = RecordingSnapshotGateway()
        val controller = ScreenerController(
            gateway = catalogue,
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            quotes = snapshots,
        )

        controller.refresh()
        advanceUntilIdle()
        val rows = controller.state.value.rows
        assertEquals(universe.size, rows.size)

        // Nothing is visible before the list has laid out, and nothing is polled either.
        controller.pollVisibleQuotes()
        assertTrue("an unlaid-out list must not poll at all", snapshots.requests.isEmpty())

        controller.setVisible(0, 2)
        controller.pollVisibleQuotes()

        val expected = rows.take(3).map(ScreenerRow::symbol)
        assertEquals(listOf(expected), snapshots.requests)

        // Scrolling moves the window; the rows that left it stop costing anything.
        controller.setVisible(4, 6)
        controller.pollVisibleQuotes()
        assertEquals(rows.subList(4, 7).map(ScreenerRow::symbol), snapshots.requests.last())
        assertEquals(2, snapshots.requests.size)
        assertTrue(
            "no request may ever carry the whole catalogue",
            snapshots.requests.none { it.size > 3 },
        )
    }

    @Test
    fun `a poll updates the price of the rows it covered`() = runTest {
        val controller = ScreenerController(
            gateway = catalogue,
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            quotes = RecordingSnapshotGateway(),
        )
        controller.refresh()
        advanceUntilIdle()
        controller.setVisible(0, 0)
        controller.pollVisibleQuotes()
        advanceUntilIdle()

        val first = controller.state.value.rows.first()
        assertEquals(101.0, first.price!!, 1e-9)
        // The row below the window kept the catalogue's price rather than being blanked.
        assertEquals(100.0, controller.state.value.rows.last().price!!, 1e-9)
    }

    @Test
    fun `bars fill in the day's figures and the filters then bite`() = runTest {
        val controller = ScreenerController(
            gateway = catalogueWithoutPrices,
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            barSource = { symbol -> if (symbol == "BTCUSDT") bars(100.0, 110.0) else bars(100.0, 95.0) },
        )
        controller.refresh()
        advanceUntilIdle()

        val bitcoin = controller.state.value.rows.first { it.symbol == "BTCUSDT" }
        assertNotNull("the bar has been read", bitcoin.high)
        assertEquals(10.0, bitcoin.changePercent!!, 1e-9)

        controller.setFilters(listOf(ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.GT, 0.0)))
        advanceUntilIdle()
        assertEquals(listOf("BTCUSDT"), controller.state.value.rows.map(ScreenerRow::symbol))
        assertEquals(1, controller.state.value.matchCount)
    }

    @Test
    fun `an indicator condition is resolved and applied without any membership check`() = runTest {
        // [109]. A rising series of forty bars puts RSI near the top of its range; the screen keeps
        // the market that is stretched and drops the one that is not.
        val rising = List(40) { index ->
            val close = 100.0 + index
            OhlcBar(t = index * 86_400L, o = close, h = close, l = close, c = close, v = 1.0)
        }
        val flat = List(40) { index ->
            OhlcBar(t = index * 86_400L, o = 100.0, h = 101.0, l = 99.0, c = 100.0, v = 1.0)
        }
        val controller = ScreenerController(
            gateway = catalogueWithoutPrices,
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            barSource = { symbol -> if (symbol == "BTCUSDT") rising else flat },
            // The readings are reduced off the caller's thread in production. Pointed at the
            // test scheduler here, so `advanceUntilIdle` actually waits for that work instead of
            // racing a background dispatcher it knows nothing about.
            computeDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        controller.refresh()
        advanceUntilIdle()

        controller.setFilters(
            listOf(ScreenerFilter.IndicatorFilter("rsi", period = 14, op = NumericOp.GT, value = 90.0)),
        )
        advanceUntilIdle()
        assertEquals(listOf("BTCUSDT"), controller.state.value.rows.map(ScreenerRow::symbol))
    }

    @Test
    fun `a failed catalogue is an error rather than an empty result`() = runTest {
        val failing = object : MarketCatalogGateway {
            override suspend fun load(): MarketCatalog = throw IllegalStateException("boom")
        }
        val controller = ScreenerController(gateway = failing, scope = TestScope(UnconfinedTestDispatcher(testScheduler)))
        controller.refresh()
        advanceUntilIdle()

        val state = controller.state.value
        assertNotNull("the reader is told the list failed, not that nothing matched", state.error)
        assertTrue(state.rows.isEmpty())
    }

    @Test
    fun `sorting moves without re-reading anything`() = runTest {
        val controller = ScreenerController(
            gateway = catalogueWithoutPrices,
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)),
            barSource = { symbol -> if (symbol == "BTCUSDT") bars(100.0, 90.0) else bars(100.0, 105.0) },
        )
        controller.refresh()
        advanceUntilIdle()

        controller.toggleSort(ScreenerField.CHANGE_PERCENT)
        assertEquals(false, controller.state.value.sort.descending)
        assertEquals("BTCUSDT", controller.state.value.rows.first().symbol)
    }
}
