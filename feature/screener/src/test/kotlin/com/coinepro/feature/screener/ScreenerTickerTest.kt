package com.coinepro.feature.screener

import com.coinepro.core.marketdata.MarketCatalog
import com.coinepro.core.marketdata.MarketCatalogGateway
import com.coinepro.core.marketdata.MarketTicker
import com.coinepro.core.marketdata.MarketTickerGateway
import com.coinepro.core.marketdata.MarketTickerStore
import com.coinepro.core.marketdata.MarketTickerTable
import com.coinepro.core.marketdata.OhlcBar
import com.coinepro.core.symbols.SymbolClassifier
import com.coinepro.feature.screener.model.NumericOp
import com.coinepro.feature.screener.model.ScreenerField
import com.coinepro.feature.screener.model.ScreenerFilter
import com.coinepro.feature.screener.model.ScreenerRow
import com.coinepro.feature.screener.model.ScreenerUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The screener on the day's table.
 *
 * The screener used to issue one candle request per market for the sole purpose of deriving today's
 * change, so filling the default table — price, the day's move, volume — cost a hundred and twenty
 * round trips before a single figure appeared. TradeYar now serves all of it for the whole catalogue
 * in one request, and the first test here is the one that matters: with the table in hand the
 * screener asks for no candles at all.
 *
 * Everything else is about the two ways that can go wrong. The saving must not reach CoinePro-FX,
 * which has no such route and has to go on reading candles exactly as it did. And it must not be
 * bought by quietly treating an absent figure as a present one — the route omits rather than zeroes,
 * so a market with no change percent is a market nobody knows about, and it has to stay that way all
 * the way to the cell and the count above the table.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ScreenerTickerTest {

    private val universe = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT")

    private fun catalogue() = object : MarketCatalogGateway {
        override suspend fun load() = MarketCatalog(
            markets = SymbolClassifier.classifyAll(universe),
            quotes = emptyMap(),
            serverTimeEpochMillis = null,
        )
    }

    /**
     * A flat two-day series that answers a day's move of ten percent.
     *
     * Deliberately disagreeing with every ticker below, so that "the venue wins" is a fact a test
     * can see rather than a coincidence of two sources that happen to agree.
     */
    private fun bars(): List<OhlcBar> = listOf(
        OhlcBar(t = 0, o = 100.0, h = 100.0, l = 100.0, c = 100.0, v = 10.0),
        OhlcBar(t = 86_400, o = 100.0, h = 110.0, l = 100.0, c = 110.0, v = 20.0),
    )

    private fun tickerSource(rows: Map<String, MarketTicker>) = ScreenerTickerSource { flowOf(rows) }

    private fun venueTickers(
        change: Double? = -4.0,
        volume: Double? = 500.0,
        turnover: Double? = 1_000_000.0,
    ) = universe.associateWith { symbol ->
        MarketTicker(
            symbol = symbol,
            last = 96.0,
            open24h = 100.0,
            high24h = 101.0,
            low24h = 95.0,
            changePercent24h = change,
            volume24h = volume,
            turnover24h = turnover,
        )
    }

    private fun controller(
        scope: TestScope,
        tickers: ScreenerTickerSource?,
        onBars: (String) -> List<OhlcBar> = { bars() },
    ) = ScreenerController(
        gateway = catalogue(),
        scope = scope,
        barSource = { symbol -> onBars(symbol) },
        tickers = tickers,
        computeDispatcher = UnconfinedTestDispatcher(scope.testScheduler),
    )

    @Test
    fun `the day's table answers the whole catalogue, and no candle is read for it`() = runTest {
        // This is the whole point. Every figure the default columns show — the price, the day's
        // move, the volume — used to be derived from a series fetched per market; all of them now
        // arrive in the one request the store already made for every other screen.
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val candleRequests = mutableListOf<String>()
        val controller = controller(scope, tickerSource(venueTickers())) { symbol ->
            candleRequests += symbol
            bars()
        }
        controller.start()
        advanceUntilIdle()

        assertTrue("the table answered, so nothing needed candles", candleRequests.isEmpty())
        val state = controller.state.value
        assertEquals(universe.size, state.rows.size)
        assertEquals("every market has been read", universe.size, state.resolvedCount)
        state.rows.forEach { row ->
            assertEquals(-4.0, row.changePercent!!, 1e-9)
            assertEquals(500.0, row.volume!!, 1e-9)
            assertEquals(1_000_000.0, row.quoteVolume!!, 1e-9)
        }
    }

    @Test
    fun `the venue's day beats the one derived from two daily closes`() = runTest {
        // Both sources can answer here and they do not agree: the bars say up ten percent from
        // yesterday's close, the venue says down four over its own rolling window. The venue is what
        // its site prints and what a reader holds this column against.
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val controller = controller(scope, tickerSource(venueTickers()))
        controller.start()
        advanceUntilIdle()

        // An indicator condition is what makes the candles arrive as well, so both sources are
        // genuinely in hand when the row is built and the precedence is doing real work.
        controller.setFilters(
            listOf(ScreenerFilter.IndicatorFilter("rsi", period = 14, op = NumericOp.GT, value = 0.0)),
        )
        advanceUntilIdle()

        controller.state.value.rows.forEach { row ->
            assertEquals(-4.0, row.changePercent!!, 1e-9)
            // Measured from the venue's own reference and not from the bar's, or the pair would not
            // agree with itself: 96 against an open of 100.
            assertEquals(-4.0, row.changeAbsolute!!, 1e-9)
            // The high is the venue's, widened by nothing — the live price is below it.
            assertEquals(101.0, row.high!!, 1e-9)
        }
    }

    @Test
    fun `a row that arrives with only a price stays unknown rather than becoming flat`() = runTest {
        // The server omits rather than zeroes, deliberately. A market with no change percent is a
        // market nobody knows about, and «—» is the only honest thing to print in that cell.
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val bare = universe.associateWith { MarketTicker(symbol = it, last = 96.0) }
        val controller = controller(scope, tickerSource(bare)) { emptyList() }
        controller.start()
        advanceUntilIdle()

        val state = controller.state.value
        // Read, and still without an answer: two different facts, and the progress line above the
        // table reports the first while every cell prints the second.
        assertEquals(universe.size, state.resolvedCount)
        state.rows.forEach { row ->
            assertTrue(row.resolved)
            assertNull(row.changePercent)
            assertNull(row.volume)
            assertNull(row.quoteVolume)
            assertNull(row.high)
            assertEquals(96.0, row.price!!, 1e-9)
            assertEquals(
                ScreenerFormat.ABSENT,
                ScreenerFormat.cell(row.changePercent, ScreenerUnit.PERCENT),
            )
        }
    }

    @Test
    fun `a threshold on a figure the table did not carry excludes the market and says so`() =
        runTest {
            // A table with holes in it. Two markets report a volume and two do not, and the two that
            // do not are not markets that traded less than the reader asked for — they are markets
            // nothing is known about. They come out of the list, as they must, and the screen is
            // told how many so that it can say so instead of losing them silently.
            val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val holes = universe.mapIndexed { index, symbol ->
                symbol to MarketTicker(
                    symbol = symbol,
                    last = 96.0,
                    changePercent24h = -4.0,
                    volume24h = if (index < 2) 500.0 else null,
                )
            }.toMap()
            val controller = controller(scope, tickerSource(holes)) { emptyList() }
            controller.start()
            advanceUntilIdle()

            controller.setFilters(
                listOf(ScreenerFilter.Numeric(ScreenerField.VOLUME, NumericOp.GT, 100.0)),
            )
            advanceUntilIdle()

            val state = controller.state.value
            assertEquals(universe.take(2), state.rows.map(ScreenerRow::symbol))
            assertEquals("the unknowns are counted, not discarded", 2, state.unknownCount)

            // A condition every market can answer leaves nothing unaccounted for, so the line the
            // screen draws from this disappears rather than reporting a zero.
            controller.setFilters(
                listOf(ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.LT, 0.0)),
            )
            advanceUntilIdle()
            assertEquals(universe.size, controller.state.value.rows.size)
            assertEquals(0, controller.state.value.unknownCount)
        }

    @Test
    fun `an indicator condition still costs the candles, because nothing else can answer it`() =
        runTest {
            // The one thing a twenty-four-hour rollup structurally cannot carry. The saving is on
            // the figures the venue computes, and it must not turn into a screener that quietly
            // stops offering the filters this feature exists for.
            val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val candleRequests = mutableListOf<String>()
            val rising = List(40) { index ->
                val close = 100.0 + index
                OhlcBar(t = index * 86_400L, o = close, h = close, l = close, c = close, v = 1.0)
            }
            val controller = controller(scope, tickerSource(venueTickers())) { symbol ->
                candleRequests += symbol
                rising
            }
            controller.start()
            advanceUntilIdle()
            assertTrue(candleRequests.isEmpty())

            controller.setFilters(
                listOf(ScreenerFilter.IndicatorFilter("rsi", period = 14, op = NumericOp.GT, value = 90.0)),
            )
            advanceUntilIdle()

            assertEquals(universe.sorted(), candleRequests.sorted())
            assertEquals(universe.size, controller.state.value.rows.size)
        }

    @Test
    fun `a platform with no such route reads candles exactly as it did before`() = runTest {
        // CoinePro-FX, through the real source over the real store. The gateway is never called
        // because there is nothing to call, the source says so at once, and every figure on the
        // screen comes from the market's own bars — a ten percent move, derived.
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val gateway = FakeTickerGateway(supported = false) { MarketTickerTable.Empty }
        val candleRequests = mutableListOf<String>()
        val controller = controller(
            scope = scope,
            tickers = MarketTickerScreenerSource(MarketTickerStore(gateway, scope)),
        ) { symbol ->
            candleRequests += symbol
            bars()
        }
        controller.start()
        advanceUntilIdle()

        assertEquals("a platform without the route must never be polled for it", 0, gateway.calls)
        assertEquals(universe.sorted(), candleRequests.sorted())
        controller.state.value.rows.forEach { row ->
            assertEquals(10.0, row.changePercent!!, 1e-9)
            assertEquals(20.0, row.volume!!, 1e-9)
        }
    }

    @Test
    fun `the shared store is followed while the screen is open and let go when it closes`() =
        runTest {
            // The store is reference counted and polls only while somebody is reading it. A screener
            // that raised the count and never lowered it would leave a five-second request against
            // the whole catalogue running behind a reader who has moved on.
            val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
            val gateway = FakeTickerGateway(supported = true) {
                MarketTickerTable(
                    tickers = venueTickers(),
                    serverTimeEpochMillis = 1L,
                    cacheTtlMillis = 5_000L,
                    fetchedAtEpochMillis = 1L,
                    source = "lbank",
                )
            }
            val controller = controller(
                scope = scope,
                tickers = MarketTickerScreenerSource(MarketTickerStore(gateway, scope)),
            ) { emptyList() }
            controller.start()
            runCurrent()

            assertEquals("the whole catalogue is one request", 1, gateway.calls)
            controller.state.value.rows.forEach { assertEquals(-4.0, it.changePercent!!, 1e-9) }

            // Still open, so the figures keep moving: the store re-reads at the interval the server
            // named and the day's column follows it rather than freezing at whatever it opened on.
            testScheduler.advanceTimeBy(12_000)
            runCurrent()
            assertTrue("a screen left open must keep its day's figures current", gateway.calls > 1)

            controller.stop()
            val afterClose = gateway.calls
            // `advanceTimeBy` rather than `advanceUntilIdle`: a poll nobody stopped has no idle to
            // advance to, so the second would hang here instead of failing.
            testScheduler.advanceTimeBy(60_000)
            runCurrent()
            assertEquals("the poll outlived the screen", afterClose, gateway.calls)
        }

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
}
