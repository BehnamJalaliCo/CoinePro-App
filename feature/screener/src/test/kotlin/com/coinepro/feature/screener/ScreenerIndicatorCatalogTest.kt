package com.coinepro.feature.screener

import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.IndicatorPane
import com.coinepro.feature.screener.model.NumericOp
import com.coinepro.feature.screener.model.ScreenerField
import com.coinepro.feature.screener.model.ScreenerFilter
import com.coinepro.feature.screener.model.ScreenerRow
import com.coinepro.feature.screener.model.ScreenerSort
import com.coinepro.feature.screener.model.ScreenerUnit
import com.coinepro.core.symbols.SymbolClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which indicators the screener offers, and what it says about the ones it does not — [115].
 *
 * Nothing here pins a count. The previous wave learned that the hard way: a test asserting «۸۳
 * indicators» passed while seven chart types drew the wrong thing. What is asserted instead is the
 * *rule* — a structure study is never offered, a volume study is offered only where there is
 * volume, and everything that is offered can actually be computed.
 */
class ScreenerIndicatorCatalogTest {

    @Test
    fun `a study that draws levels rather than a value is never offered`() {
        val structure = ChartCatalog.INDICATORS.filter { it.pane == IndicatorPane.STRUCTURE }
        val offered = ScreenerIndicatorCatalog.offered(hasVolume = true).map { it.id }.toSet()
        structure.forEach { option ->
            assertFalse(option.id, option.id in offered)
            assertNull(option.id, ScreenerIndicatorCatalog.optionOf(option.id))
        }
    }

    @Test
    fun `the indicator that needs a second symbol is withheld, and says so`() {
        val withheld = ScreenerIndicatorCatalog.withheld(hasVolume = true)
        val correlation = withheld.firstOrNull { it.id == "correlation" }
        assertNotNull("a correlation of one market against nothing is not a reading", correlation)
        assertEquals(ScreenerIndicatorCatalog.Absence.NEEDS_COMPARISON, correlation!!.why)
    }

    @Test
    fun `volume studies are withheld on a feed with no volume and offered where there is`() {
        val without = ScreenerIndicatorCatalog.offered(hasVolume = false).map { it.id }.toSet()
        val with = ScreenerIndicatorCatalog.offered(hasVolume = true).map { it.id }.toSet()
        ChartCatalog.VOLUME_ONLY_INDICATORS.forEach { id ->
            assertFalse(id, id in without)
        }
        assertTrue("money flow is a real reading where the feed reports volume", "mfi" in with)

        val reasons = ScreenerIndicatorCatalog.withheld(hasVolume = false)
            .filter { it.why == ScreenerIndicatorCatalog.Absence.NEEDS_VOLUME }
            .map { it.id }
            .toSet()
        assertEquals(ChartCatalog.VOLUME_ONLY_INDICATORS, reasons)
    }

    @Test
    fun `every withheld indicator carries a sentence a reader can act on`() {
        ScreenerIndicatorCatalog.withheld(hasVolume = false).forEach { row ->
            assertTrue(row.id, row.why.reason.isNotBlank())
            assertTrue(row.id, row.label.isNotBlank())
        }
    }

    @Test
    fun `a price-scale indicator is offered as a distance and an oscillator as it stands`() {
        // The one judgement in the catalogue: a moving average is a price and cannot be compared
        // across markets, so what is offered is how far the price sits from it.
        val ema = ScreenerIndicatorCatalog.optionOf("ema")!!
        assertEquals(ScreenerIndicatorCatalog.Reading.LEVEL_DISTANCE, ema.reading)
        assertEquals(ScreenerUnit.PERCENT, ema.unit)

        val rsi = ScreenerIndicatorCatalog.optionOf("rsi")!!
        assertEquals(ScreenerIndicatorCatalog.Reading.RATIO, rsi.reading)
        assertEquals(ScreenerUnit.PLAIN, rsi.unit)

        // And a reading in the instrument's own currency is published as a share of price.
        val atr = ScreenerIndicatorCatalog.optionOf("atr")!!
        assertEquals(ScreenerIndicatorCatalog.Reading.PRICE_PERCENT, atr.reading)
    }

    @Test
    fun `an indicator with no single lookback is offered no period control`() {
        // MACD is 12/26/9. A stepper labelled «دوره» over that moves a third of the tool.
        assertFalse(ScreenerIndicatorCatalog.optionOf("macd")!!.takesPeriod)
        assertTrue(ScreenerIndicatorCatalog.optionOf("rsi")!!.takesPeriod)
        assertEquals(14, ScreenerIndicatorCatalog.optionOf("rsi")!!.defaultPeriod)
    }

    @Test
    fun `the search finds a row by its Persian name and by its id`() {
        val byId = ScreenerIndicatorCatalog.matching("rsi", hasVolume = true).map { it.id }
        assertTrue("rsi" in byId)
        val byName = ScreenerIndicatorCatalog.matching("قدرت", hasVolume = true).map { it.id }
        assertTrue("rsi" in byName)
        assertTrue(ScreenerIndicatorCatalog.matching("زززز", hasVolume = true).isEmpty())
    }

    @Test
    fun `an id from a later build is labelled as itself rather than as nothing`() {
        assertEquals("no_such_indicator", ScreenerIndicatorCatalog.labelOf("no_such_indicator"))
        assertEquals("شاخص قدرت نسبی", ScreenerIndicatorCatalog.labelOf("rsi"))
    }
}

/**
 * The columns an indicator condition puts on the table, and sorting by one of them — [115].
 *
 * A filter the table cannot show is a filter a reader cannot check. These assert the two halves of
 * making that impossible: the column appears, and the sort can name it.
 */
class ScreenerIndicatorColumnTest {

    private fun row(symbol: String, reading: Double?) = ScreenerRow(
        meta = SymbolClassifier.classify(symbol),
        price = 100.0,
        indicators = reading?.let { mapOf("tsi:25" to it) } ?: emptyMap(),
    )

    @Test
    fun `an indicator condition earns a column of its own`() {
        val filters = listOf(
            ScreenerFilter.IndicatorFilter("tsi", period = 25, op = NumericOp.GT, value = 20.0),
        )
        val columns = ScreenerIndicatorColumn.of(filters, ScreenerField.DEFAULT_COLUMNS)
        val column = columns.single()
        assertEquals("tsi:25", column.key)
        assertTrue("the heading names the indicator and its period", column.label.contains("25"))
        assertEquals(28.0, column.valueOf(row("BTCUSDT", 28.0))!!, 1e-9)
    }

    @Test
    fun `two conditions on one reading are one column`() {
        val filters = listOf(
            ScreenerFilter.IndicatorFilter("tsi", 25, NumericOp.GT, 20.0),
            ScreenerFilter.IndicatorFilter("tsi", 25, NumericOp.LT, 60.0),
        )
        assertEquals(1, ScreenerIndicatorColumn.of(filters, ScreenerField.DEFAULT_COLUMNS).size)
    }

    @Test
    fun `an indicator that is already a chosen column is not shown twice`() {
        val filters = listOf(
            ScreenerFilter.IndicatorFilter("rsi", 14, NumericOp.LT, 30.0),
        )
        val withRsiColumn = ScreenerField.DEFAULT_COLUMNS + ScreenerField.RSI
        assertTrue(ScreenerIndicatorColumn.of(filters, withRsiColumn).isEmpty())
        assertEquals(1, ScreenerIndicatorColumn.of(filters, ScreenerField.DEFAULT_COLUMNS).size)
    }

    @Test
    fun `sorting by an indicator column orders on the reading and pins the unread rows last`() {
        val rows = listOf(row("BTCUSDT", 10.0), row("ETHUSDT", null), row("SOLUSDT", 40.0))
        val descending = ScreenerSort(ScreenerField.LAST_PRICE, indicatorKey = "tsi:25")
        assertEquals(
            listOf("SOLUSDT", "BTCUSDT", "ETHUSDT"),
            descending.apply(rows).map { it.symbol },
        )
        // Ascending must not promote the market nobody has computed yet: «کمترین» is a question
        // about markets with a reading, not about the ones still being resolved.
        assertEquals(
            listOf("BTCUSDT", "SOLUSDT", "ETHUSDT"),
            descending.copy(descending = false).apply(rows).map { it.symbol },
        )
    }

    @Test
    fun `tapping the same indicator heading flips it and tapping a field moves off it`() {
        val onIndicator = ScreenerSort(ScreenerField.LAST_PRICE, indicatorKey = "tsi:25")
        assertFalse(onIndicator.toggledIndicator("tsi:25").descending)
        assertEquals("rsi:14", onIndicator.toggledIndicator("rsi:14").indicatorKey)
        val backToField = onIndicator.toggled(ScreenerField.VOLUME)
        assertNull(backToField.indicatorKey)
        assertEquals(ScreenerField.VOLUME, backToField.field)
        assertTrue(backToField.descending)
    }
}
