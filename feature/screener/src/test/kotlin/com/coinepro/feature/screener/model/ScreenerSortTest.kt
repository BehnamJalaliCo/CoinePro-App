package com.coinepro.feature.screener.model

import com.coinepro.core.symbols.SymbolClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The sort's two promises: it is stable, and a market with no value sorts last whichever way it
 * runs.
 *
 * Stability is the one that has to be tested rather than assumed. On a quiet day dozens of markets
 * tie at exactly `0.00`, and an unstable sort would reorder them on every recomposition — a table
 * where nothing moved but the rows swapped places, which is the most unsettling thing a market
 * screen can do and the least likely to be noticed in review.
 */
class ScreenerSortTest {

    private fun row(symbol: String, change: Double? = null, volume: Double? = null) = ScreenerRow(
        meta = SymbolClassifier.classify(symbol),
        price = 1.0,
        changePercent = change,
        volume = volume,
    )

    @Test
    fun `sorting is stable, so markets that tie keep the order they arrived in`() {
        // Six markets, all flat. Every one of them compares equal, so the answer must be the input.
        val input = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT", "ADAUSDT", "DOGEUSDT")
            .map { row(it, change = 0.0) }
        val sorted = ScreenerSort(ScreenerField.CHANGE_PERCENT, descending = true).apply(input)
        assertEquals(input.map(ScreenerRow::symbol), sorted.map(ScreenerRow::symbol))
    }

    @Test
    fun `a tie inside a real ordering keeps its incoming order too`() {
        val input = listOf(
            row("BTCUSDT", change = 5.0),
            row("ETHUSDT", change = 2.0),
            row("SOLUSDT", change = 2.0),
            row("XRPUSDT", change = 2.0),
            row("ADAUSDT", change = 9.0),
        )
        val sorted = ScreenerSort(ScreenerField.CHANGE_PERCENT, descending = true).apply(input)
        assertEquals(
            listOf("ADAUSDT", "BTCUSDT", "ETHUSDT", "SOLUSDT", "XRPUSDT"),
            sorted.map(ScreenerRow::symbol),
        )
    }

    @Test
    fun `descending puts the biggest first`() {
        val sorted = ScreenerSort(ScreenerField.CHANGE_PERCENT, descending = true).apply(
            listOf(row("BTCUSDT", 1.0), row("ETHUSDT", 7.0), row("SOLUSDT", -3.0)),
        )
        assertEquals(listOf("ETHUSDT", "BTCUSDT", "SOLUSDT"), sorted.map(ScreenerRow::symbol))
    }

    @Test
    fun `ascending puts the smallest first`() {
        val sorted = ScreenerSort(ScreenerField.CHANGE_PERCENT, descending = false).apply(
            listOf(row("BTCUSDT", 1.0), row("ETHUSDT", 7.0), row("SOLUSDT", -3.0)),
        )
        assertEquals(listOf("SOLUSDT", "BTCUSDT", "ETHUSDT"), sorted.map(ScreenerRow::symbol))
    }

    @Test
    fun `a market with no value sorts last in both directions`() {
        // Not zero, and not promoted when the order flips. «کمترین حجم» must not become a list of
        // everything the app has not read yet.
        val input = listOf(
            row("BTCUSDT", volume = 4.0),
            row("ETHUSDT", volume = null),
            row("SOLUSDT", volume = 1.0),
        )
        assertEquals(
            listOf("BTCUSDT", "SOLUSDT", "ETHUSDT"),
            ScreenerSort(ScreenerField.VOLUME, descending = true).apply(input).map(ScreenerRow::symbol),
        )
        assertEquals(
            listOf("SOLUSDT", "BTCUSDT", "ETHUSDT"),
            ScreenerSort(ScreenerField.VOLUME, descending = false).apply(input).map(ScreenerRow::symbol),
        )
    }

    @Test
    fun `a categorical column sorts by its text`() {
        val input = listOf(row("BTCUSDT"), row("EURUSD"), row("XAUUSD"))
        assertEquals(
            listOf("BTCUSDT", "EURUSD", "XAUUSD"),
            ScreenerSort(ScreenerField.ASSET_CLASS, descending = false)
                .apply(input)
                .map(ScreenerRow::symbol),
        )
    }

    @Test
    fun `tapping the sorted column flips it and tapping another moves it, descending`() {
        val start = ScreenerSort(ScreenerField.CHANGE_PERCENT, descending = true)
        assertEquals(
            ScreenerSort(ScreenerField.CHANGE_PERCENT, descending = false),
            start.toggled(ScreenerField.CHANGE_PERCENT),
        )
        assertEquals(
            ScreenerSort(ScreenerField.VOLUME, descending = true),
            start.toggled(ScreenerField.VOLUME),
        )
        // Even from an ascending sort, a new column starts descending: somebody who just chose
        // «حجم» does not mean "show me the markets that barely traded".
        assertEquals(
            ScreenerSort(ScreenerField.VOLUME, descending = true),
            ScreenerSort(ScreenerField.CHANGE_PERCENT, descending = false).toggled(ScreenerField.VOLUME),
        )
    }

    @Test
    fun `a saved screen filters and sorts in one step`() {
        val screen = ScreenerScreen(
            id = "s1",
            name = "رشد",
            filters = listOf(ScreenerFilter.Numeric(ScreenerField.CHANGE_PERCENT, NumericOp.GT, 0.0)),
            sort = ScreenerSort(ScreenerField.CHANGE_PERCENT, descending = true),
        )
        val result = screen.apply(
            listOf(row("BTCUSDT", 1.0), row("ETHUSDT", -2.0), row("SOLUSDT", 6.0), row("XRPUSDT", null)),
        )
        assertEquals(listOf("SOLUSDT", "BTCUSDT"), result.map(ScreenerRow::symbol))
    }
}
