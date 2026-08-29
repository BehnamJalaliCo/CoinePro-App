package com.coinepro.feature.search

import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.marketdata.MarketTicker
import com.coinepro.core.marketdata.MarketTickerStore
import com.coinepro.core.marketdata.MarketTickerTable
import com.coinepro.core.marketdata.UnsupportedMarketTickerGateway
import com.coinepro.core.symbols.MatchField
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ordering behind the market tabs, which is the part of them that can be wrong without looking
 * wrong.
 *
 * A gainers list with a faller in it, a "most traded" column ranked on the base asset, or a market
 * with no figure sitting in the middle of the list as though it were flat — none of these crash,
 * none look broken, and every one of them tells a trader something untrue about the board.
 */
class MarketArrangementTest {

    @Test
    fun `gainers holds only markets that rose, largest first`() {
        val arranged = arrangeMarkets(
            rows = rows("BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT"),
            tickers = table(
                ticker("BTCUSDT", change = 1.2),
                ticker("ETHUSDT", change = -4.0),
                ticker("SOLUSDT", change = 9.5),
                ticker("XRPUSDT", change = 0.0),
            ),
            lens = MarketLens.GAINERS,
            sort = null,
        )

        // The flat market is not a gainer either. Zero is a real reading the server meant, and it
        // is a market that did nothing rather than one that rose.
        assertEquals(listOf("SOLUSDT", "BTCUSDT"), arranged.symbols())
    }

    @Test
    fun `losers holds only markets that fell, deepest first`() {
        val arranged = arrangeMarkets(
            rows = rows("BTCUSDT", "ETHUSDT", "SOLUSDT"),
            tickers = table(
                ticker("BTCUSDT", change = 1.2),
                ticker("ETHUSDT", change = -4.0),
                ticker("SOLUSDT", change = -11.75),
            ),
            lens = MarketLens.LOSERS,
            sort = null,
        )

        assertEquals(listOf("SOLUSDT", "ETHUSDT"), arranged.symbols())
    }

    @Test
    fun `a market with no change is not a gainer and not a loser`() {
        // The one that matters most. `changePercent24h` is absent rather than zero when the server
        // does not know it, and a row read as zero percent would sit in the middle of the gainers
        // list looking like a market that traded flat all day.
        val tickers = table(
            ticker("BTCUSDT", change = 3.0),
            ticker("ETHUSDT", change = -3.0),
            // Present in the table, priced, and with no day's move at all.
            MarketTicker(symbol = "SOLUSDT", last = 140.0, turnover24h = 9_000_000.0),
        )
        val rows = rows("BTCUSDT", "ETHUSDT", "SOLUSDT")

        assertFalse("SOLUSDT" in arrangeMarkets(rows, tickers, MarketLens.GAINERS, null).symbols())
        assertFalse("SOLUSDT" in arrangeMarkets(rows, tickers, MarketLens.LOSERS, null).symbols())
    }

    @Test
    fun `a market the table does not carry at all is dropped by a lens`() {
        val arranged = arrangeMarkets(
            rows = rows("BTCUSDT", "NOTINTABLE"),
            tickers = table(ticker("BTCUSDT", change = 3.0)),
            lens = MarketLens.GAINERS,
            sort = null,
        )

        assertEquals(listOf("BTCUSDT"), arranged.symbols())
    }

    @Test
    fun `sorting by change sinks the unknowns to the bottom in both directions`() {
        val rows = rows("BTCUSDT", "NOCHANGE", "ETHUSDT")
        val tickers = table(
            ticker("BTCUSDT", change = 5.0),
            ticker("ETHUSDT", change = -2.0),
            MarketTicker(symbol = "NOCHANGE", last = 1.0),
        )

        val down = arrangeMarkets(rows, tickers, MarketLens.NONE, MarketSort(MarketSortKey.CHANGE, true))
        val up = arrangeMarkets(rows, tickers, MarketLens.NONE, MarketSort(MarketSortKey.CHANGE, false))

        assertEquals(listOf("BTCUSDT", "ETHUSDT", "NOCHANGE"), down.symbols())
        // Not first. An unknown read as zero would open the ascending sort with every market the
        // feed has said nothing about, which is a list ordered by what has not arrived.
        assertEquals(listOf("ETHUSDT", "BTCUSDT", "NOCHANGE"), up.symbols())
    }

    @Test
    fun `most traded is turnover, not volume`() {
        // The bug this test exists for: volume is counted in the base asset, so a list ordered by
        // it ranks a count of the cheap token above a count of bitcoin. The two fields disagree
        // here on purpose — DOGE has the larger volume and the smaller turnover.
        val tickers = table(
            MarketTicker(symbol = "BTCUSDT", last = 90_000.0, volume24h = 12.0, turnover24h = 1_080_000.0),
            MarketTicker(symbol = "DOGEUSDT", last = 0.12, volume24h = 4_000_000.0, turnover24h = 480_000.0),
        )

        val arranged = arrangeMarkets(
            rows = rows("DOGEUSDT", "BTCUSDT"),
            tickers = tickers,
            lens = MarketLens.NONE,
            sort = MarketSort(MarketSortKey.TURNOVER, descending = true),
        )

        assertEquals(listOf("BTCUSDT", "DOGEUSDT"), arranged.symbols())
    }

    @Test
    fun `ties keep the order they arrived in`() {
        // Two markets both at exactly the same move. A sort that reversed its ties would swap them
        // on every poll of a table that has not changed, which is a list that looks alive when
        // nothing has happened.
        val tickers = table(
            ticker("AAAUSDT", change = 2.0),
            ticker("BBBUSDT", change = 2.0),
            ticker("CCCUSDT", change = 2.0),
        )
        val rows = rows("AAAUSDT", "BBBUSDT", "CCCUSDT")

        assertEquals(
            listOf("AAAUSDT", "BBBUSDT", "CCCUSDT"),
            arrangeMarkets(rows, tickers, MarketLens.GAINERS, null).symbols(),
        )
        assertEquals(
            listOf("AAAUSDT", "BBBUSDT", "CCCUSDT"),
            arrangeMarkets(rows, tickers, MarketLens.NONE, MarketSort(MarketSortKey.CHANGE, true)).symbols(),
        )
    }

    @Test
    fun `hot keeps the half that moved and orders it by turnover`() {
        // Five markets. The absolute moves are 0.5, 1, 2, 8 and 20, so the middle is 2 and the
        // three at or above it survive — then they are ordered by the money that went through them,
        // which is not the order their moves are in.
        val tickers = table(
            MarketTicker(symbol = "AAAUSDT", last = 1.0, changePercent24h = 0.5, turnover24h = 900_000_000.0),
            MarketTicker(symbol = "BBBUSDT", last = 1.0, changePercent24h = -1.0, turnover24h = 800_000_000.0),
            MarketTicker(symbol = "CCCUSDT", last = 1.0, changePercent24h = 2.0, turnover24h = 10_000_000.0),
            MarketTicker(symbol = "DDDUSDT", last = 1.0, changePercent24h = -8.0, turnover24h = 70_000_000.0),
            MarketTicker(symbol = "EEEUSDT", last = 1.0, changePercent24h = 20.0, turnover24h = 30_000_000.0),
        )

        val arranged = arrangeMarkets(
            rows = rows("AAAUSDT", "BBBUSDT", "CCCUSDT", "DDDUSDT", "EEEUSDT"),
            tickers = tickers,
            lens = MarketLens.HOT,
            sort = null,
        )

        // The two busiest markets on the board are absent because neither one moved: that is the
        // whole point of the gate, and it is what stops «داغ» from being a second turnover list.
        assertEquals(listOf("DDDUSDT", "EEEUSDT", "CCCUSDT"), arranged.symbols())
    }

    @Test
    fun `a fall is as hot as a rise of the same size`() {
        val tickers = table(
            MarketTicker(symbol = "UPUSDT", last = 1.0, changePercent24h = 6.0, turnover24h = 10.0),
            MarketTicker(symbol = "DOWNUSDT", last = 1.0, changePercent24h = -6.0, turnover24h = 20.0),
            MarketTicker(symbol = "FLATUSDT", last = 1.0, changePercent24h = 0.0, turnover24h = 30.0),
        )

        val arranged = arrangeMarkets(rows("UPUSDT", "DOWNUSDT", "FLATUSDT"), tickers, MarketLens.HOT, null)

        // Median of |0|, |6|, |6| is 6, so the flat market is out and both movers are in, ordered
        // by turnover. A crash is news in exactly the way a rally is.
        assertEquals(listOf("DOWNUSDT", "UPUSDT"), arranged.symbols())
    }

    @Test
    fun `hot needs both figures and leaves out a market missing either`() {
        val tickers = table(
            MarketTicker(symbol = "BOTHUSDT", last = 1.0, changePercent24h = 5.0, turnover24h = 100.0),
            // Busy, and the server did not say how it moved.
            MarketTicker(symbol = "NOMOVEUSDT", last = 1.0, turnover24h = 5_000.0),
            // Moved a long way, and the server did not say how much traded.
            MarketTicker(symbol = "NOTURNUSDT", last = 1.0, changePercent24h = 40.0),
        )

        val arranged = arrangeMarkets(
            rows = rows("BOTHUSDT", "NOMOVEUSDT", "NOTURNUSDT"),
            tickers = tickers,
            lens = MarketLens.HOT,
            sort = null,
        )

        assertEquals(listOf("BOTHUSDT"), arranged.symbols())
    }

    @Test
    fun `the lens narrows and the sort then reorders what is left`() {
        val tickers = table(
            MarketTicker(symbol = "AAAUSDT", last = 1.0, changePercent24h = 9.0, turnover24h = 10.0),
            MarketTicker(symbol = "BBBUSDT", last = 1.0, changePercent24h = 1.0, turnover24h = 900.0),
            MarketTicker(symbol = "CCCUSDT", last = 1.0, changePercent24h = -5.0, turnover24h = 5_000.0),
        )

        val arranged = arrangeMarkets(
            rows = rows("AAAUSDT", "BBBUSDT", "CCCUSDT"),
            tickers = tickers,
            lens = MarketLens.GAINERS,
            sort = MarketSort(MarketSortKey.TURNOVER, descending = true),
        )

        // The faller stays out even though it is by far the most traded: the lens picked the rows
        // and the sort only rearranged them. The other order round would drop rows out of the
        // middle of a list the reader had already sorted.
        assertEquals(listOf("BBBUSDT", "AAAUSDT"), arranged.symbols())
    }

    @Test
    fun `an empty table leaves the catalogue order alone and empties every lens`() {
        val rows = rows("BTCUSDT", "ETHUSDT", "SOLUSDT")
        val empty = MarketTickerStore.MarketTickerState(table = MarketTickerTable.Empty)

        assertEquals(rows.symbols(), arrangeMarkets(rows, empty, MarketLens.NONE, null).symbols())
        assertTrue(arrangeMarkets(rows, empty, MarketLens.HOT, null).isEmpty())
        assertTrue(arrangeMarkets(rows, empty, MarketLens.GAINERS, null).isEmpty())
        assertTrue(arrangeMarkets(rows, empty, MarketLens.LOSERS, null).isEmpty())
    }

    @Test
    fun `a table that has not loaded yet keeps every row and sorts none of them`() {
        // The first frame, before the poll lands. A sort over it must not reorder anything or drop
        // anything — every row is an unknown, and every unknown sinks, which leaves the list as it
        // was rather than blank.
        val rows = rows("BTCUSDT", "ETHUSDT", "SOLUSDT")
        val waiting = MarketTickerStore.MarketTickerState()

        assertTrue(waiting.table.tickers.isEmpty())
        assertEquals(
            rows.symbols(),
            arrangeMarkets(rows, waiting, MarketLens.NONE, MarketSort(MarketSortKey.CHANGE, true)).symbols(),
        )
    }

    @Test
    fun `a platform with no such route reports it, and the screen keeps the plain list`() {
        // CoinePro-FX. The store exists, answers, and answers with nothing forever — so `supported`
        // is what the screen gates the whole second axis on, not the store being non-null. With the
        // lens held at NONE the arrangement is the catalogue's, which is the screen as it was.
        val store = MarketTickerStore(UnsupportedMarketTickerGateway(), CoroutineScope(Dispatchers.Unconfined))
        store.start()

        assertFalse(store.supported)
        assertTrue(store.state.value.table.tickers.isEmpty())
        assertFalse(store.state.value.loading)

        val rows = rows("XAUUSD", "EURUSD")
        assertEquals(
            rows.symbols(),
            arrangeMarkets(rows, store.state.value, MarketLens.NONE, null).symbols(),
        )
    }

    @Test
    fun `a heading cycles largest first, smallest first, then off`() {
        val first = nextMarketSort(null, MarketSortKey.CHANGE)
        assertEquals(MarketSort(MarketSortKey.CHANGE, descending = true), first)

        val second = nextMarketSort(first, MarketSortKey.CHANGE)
        assertEquals(MarketSort(MarketSortKey.CHANGE, descending = false), second)

        // Off, not back to the top. The lens under it has an order of its own, and a sort with no
        // way out of it would take «داغ» away from a reader who only wanted one glance down a
        // column.
        assertNull(nextMarketSort(second, MarketSortKey.CHANGE))
    }

    @Test
    fun `moving to the other column starts that one over rather than inheriting a direction`() {
        val ascending = MarketSort(MarketSortKey.CHANGE, descending = false)

        assertEquals(
            MarketSort(MarketSortKey.TURNOVER, descending = true),
            nextMarketSort(ascending, MarketSortKey.TURNOVER),
        )
    }
}

private fun List<MarketSearchRow>.symbols(): List<String> = map { it.meta.symbol }

private fun rows(vararg symbols: String): List<MarketSearchRow> = symbols.map { symbol ->
    MarketSearchRow(
        meta = SymbolMeta(
            symbol = symbol,
            canonical = symbol,
            category = SymbolCategory.CRYPTO,
            base = symbol.removeSuffix("USDT"),
            quote = "USDT",
            description = symbol,
            popular = false,
        ),
        // Deliberately null throughout. `MarketQuote.changePercent` has been null on every quote
        // either backend has ever returned, so a test that fed one would be testing a source the
        // arrangement is not allowed to depend on.
        quote = null,
        field = MatchField.NONE,
        highlight = null,
    )
}

private fun ticker(symbol: String, change: Double) =
    MarketTicker(symbol = symbol, last = 1.0, changePercent24h = change)

private fun table(vararg tickers: MarketTicker) = MarketTickerStore.MarketTickerState(
    table = MarketTickerTable(
        tickers = tickers.associateBy { it.symbol },
        serverTimeEpochMillis = null,
        cacheTtlMillis = null,
        fetchedAtEpochMillis = null,
        source = null,
    ),
)
