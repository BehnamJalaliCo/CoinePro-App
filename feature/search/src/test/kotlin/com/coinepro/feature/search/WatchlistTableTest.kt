package com.coinepro.feature.search

import com.coinepro.core.datastore.WatchlistColumn
import com.coinepro.core.datastore.WatchlistFlag
import com.coinepro.core.datastore.WatchlistSort
import com.coinepro.core.marketdata.MarketSearchRow
import com.coinepro.core.model.Instrument
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.symbols.MatchField
import com.coinepro.core.symbols.SymbolCategory
import com.coinepro.core.symbols.SymbolMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic and the ordering behind the watchlist table — the parts that are wrong silently.
 *
 * A misplaced sort or a change figure recovered from the wrong side of the percentage does not
 * crash and does not look broken; it just quietly tells a trader something untrue.
 */
class WatchlistTableTest {

    @Test
    fun `the change in price is recovered exactly from the price and the percentage`() {
        // A market at 110 that is up 10 percent opened at 100, so the move is 10.
        val figures = figuresFor(row("BTCUSDT", price = 110.0, percent = 10.0), emptyList())

        assertEquals(10.0, figures.change!!, 1e-9)
        assertEquals(110.0, figures.price!!, 1e-9)
        assertEquals(10.0, figures.changePercent!!, 1e-9)
    }

    @Test
    fun `a fall gives a negative change`() {
        val figures = figuresFor(row("ETHUSDT", price = 90.0, percent = -10.0), emptyList())

        assertEquals(-10.0, figures.change!!, 1e-9)
    }

    @Test
    fun `a market with no quote has no figures rather than zeroes`() {
        val figures = figuresFor(row("SOLUSDT", price = null, percent = null), emptyList())

        assertNull(figures.price)
        assertNull(figures.change)
        assertNull(figures.changePercent)
    }

    @Test
    fun `the day high and low come from the same series the sparkline draws`() {
        val figures = figuresFor(
            row("BTCUSDT", price = 105.0, percent = 5.0),
            listOf(98.0, 103.0, 96.5, 105.0),
        )

        assertEquals(105.0, figures.dayHigh!!, 1e-9)
        assertEquals(96.5, figures.dayLow!!, 1e-9)
    }

    @Test
    fun `an amount is compacted to something a seventy-six point column can hold`() {
        assertEquals("1.28B", compactAmount(1_284_930_447.0))
        assertEquals("918.44M", compactAmount(918_442_000.0))
        assertEquals("12.70K", compactAmount(12_700.0))
        assertEquals("842.50", compactAmount(842.5))
        // Latin digits and a dot decimal, whatever the device locale is set to.
        assertEquals("-1.50M", compactAmount(-1_500_000.0))
    }

    @Test
    fun `tapping a heading goes largest first, then smallest first, then back to the reader's order`() {
        val first = nextSort(WatchlistSort.Manual, WatchlistColumn.CHANGE_PERCENT)
        assertEquals(WatchlistSort(WatchlistColumn.CHANGE_PERCENT, descending = true), first)

        val second = nextSort(first, WatchlistColumn.CHANGE_PERCENT)
        assertEquals(WatchlistSort(WatchlistColumn.CHANGE_PERCENT, descending = false), second)

        // The third state is the one that gives the reader their own order back.
        assertEquals(WatchlistSort.Manual, nextSort(second, WatchlistColumn.CHANGE_PERCENT))
    }

    @Test
    fun `switching to another column starts that column largest first`() {
        val sorted = WatchlistSort(WatchlistColumn.LAST_PRICE, descending = false)

        assertEquals(
            WatchlistSort(WatchlistColumn.CHANGE_PERCENT, descending = true),
            nextSort(sorted, WatchlistColumn.CHANGE_PERCENT),
        )
    }

    @Test
    fun `a manual sort leaves the rows exactly as the reader dragged them`() {
        val rows = listOf(
            row("SOLUSDT", price = 20.0, percent = 1.0),
            row("BTCUSDT", price = 90_000.0, percent = 5.0),
        )

        assertEquals(rows, sortRows(rows, WatchlistSort.Manual, emptyMap(), emptyMap()))
    }

    @Test
    fun `sorting by a column orders on that column in both directions`() {
        val rows = listOf(
            row("SOLUSDT", price = 20.0, percent = 1.0),
            row("BTCUSDT", price = 90_000.0, percent = 5.0),
            row("ETHUSDT", price = 3_000.0, percent = -2.0),
        )

        val descending = sortRows(
            rows,
            WatchlistSort(WatchlistColumn.LAST_PRICE, descending = true),
            emptyMap(),
            emptyMap(),
        )
        assertEquals(listOf("BTCUSDT", "ETHUSDT", "SOLUSDT"), descending.map { it.meta.symbol })

        val ascending = sortRows(
            rows,
            WatchlistSort(WatchlistColumn.LAST_PRICE, descending = false),
            emptyMap(),
            emptyMap(),
        )
        assertEquals(listOf("SOLUSDT", "ETHUSDT", "BTCUSDT"), ascending.map { it.meta.symbol })
    }

    @Test
    fun `a row the feed has not quoted sinks to the bottom in both directions`() {
        val rows = listOf(
            row("XRPUSDT", price = null, percent = null),
            row("BTCUSDT", price = 90_000.0, percent = 5.0),
            row("SOLUSDT", price = 20.0, percent = 1.0),
        )

        // Not treated as zero, which would float every unquoted market to the top of an ascending
        // sort and produce a list ordered by "what has not arrived yet".
        listOf(true, false).forEach { descending ->
            val sorted = sortRows(
                rows,
                WatchlistSort(WatchlistColumn.LAST_PRICE, descending),
                emptyMap(),
                emptyMap(),
            )
            assertEquals("XRPUSDT", sorted.last().meta.symbol)
        }
    }

    @Test
    fun `sorting by flag groups the colours and leaves the unflagged last`() {
        val rows = listOf(
            row("SOLUSDT", price = 20.0, percent = 1.0),
            row("BTCUSDT", price = 90_000.0, percent = 5.0),
            row("ETHUSDT", price = 3_000.0, percent = -2.0),
        )
        val flags = mapOf("ETHUSDT" to WatchlistFlag.RED, "SOLUSDT" to WatchlistFlag.GREEN)

        val sorted = sortRows(
            rows,
            WatchlistSort(WatchlistColumn.FLAG, descending = false),
            flags,
            emptyMap(),
        )

        // Enum order: red before green, and the unflagged row after both.
        assertEquals(listOf("ETHUSDT", "SOLUSDT", "BTCUSDT"), sorted.map { it.meta.symbol })
    }

    @Test
    fun `the default column set fits a 393dp phone without the move being cut`() {
        // The claim `WatchlistColumn.DEFAULT` makes, checked rather than trusted: the flag rail,
        // the logo, the ticker column and the gap between each of them, then the chosen figure
        // columns with eight between those.
        //
        // **393 and not 411.** The reference width this design system is measured against is wider
        // than the phone most readers hold, and the difference is exactly what shipped broken: at
        // 393 the old set ran fourteen points over, the figure block scrolls, the page is
        // right-to-left, and so the last column lost its leading characters — `+0.35%` reaching
        // the reader as `.35%`. A test that only ever asked about 411 could not see it.
        val leading = 3 + RowGap.value.toInt() + LogoSize.value.toInt() + RowGap.value.toInt() +
            SymbolColumn.value.toInt() + RowGap.value.toInt()
        val figures = WatchlistColumn.DEFAULT
            .filter { it != WatchlistColumn.FLAG }
            .sumOf { widthOf(it).value.toInt() } + 8 * 2

        assertEquals(359, leading + figures)
        assertTrue(
            "the default set is $leading + $figures wide and a 393dp phone has ${393 - 32}",
            leading + figures <= 393 - 32,
        )
    }

    @Test
    fun `with the reorder grip on the row the sparkline leaves and the rest still fits 393dp`() {
        // Reordering puts a 32-point grip and its gap in front of the logo; `WatchlistPanel`
        // drops the sparkline for the duration, so the price and the move keep their places.
        val leading = 3 + RowGap.value.toInt() + HandleWidth.value.toInt() + RowGap.value.toInt() +
            LogoSize.value.toInt() + RowGap.value.toInt() + SymbolColumn.value.toInt() +
            RowGap.value.toInt()
        val figures = WatchlistColumn.DEFAULT
            .filter { it != WatchlistColumn.FLAG && it != WatchlistColumn.SPARKLINE }
            .sumOf { widthOf(it).value.toInt() } + 8

        assertEquals(339, leading + figures)
        assertTrue(leading + figures <= 393 - 32)
    }
}

/** A catalogue row with just enough on it to sort and format. */
private fun row(symbol: String, price: Double?, percent: Double?) = MarketSearchRow(
    meta = SymbolMeta(
        symbol = symbol,
        canonical = symbol,
        category = SymbolCategory.CRYPTO,
        base = symbol.removeSuffix("USDT"),
        quote = "USDT",
        description = symbol,
        popular = false,
    ),
    quote = price?.let {
        MarketQuote(
            instrument = Instrument(symbol, symbol, MarketType.CRYPTO),
            price = it,
            changePercent = percent,
            timestampEpochMillis = 0L,
        )
    },
    field = MatchField.NONE,
    highlight = null,
)
