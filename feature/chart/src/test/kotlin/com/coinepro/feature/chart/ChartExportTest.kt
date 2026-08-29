package com.coinepro.feature.chart

import com.coinepro.core.chart.Candle
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.Timeframe
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The candle export, and the three ways a Persian-locale export goes silently wrong.
 *
 * Every one of them produces a file that *looks* right and is unusable: a missing byte-order mark
 * turns every heading into mojibake on Persian Windows, a Persian digit in a price column makes a
 * spreadsheet answer zero to every formula over it, and a bare column count that does not match the
 * headings shifts a whole file by one without anything anywhere raising an error.
 *
 * The tests run under a Persian default locale on purpose. That is the state the app actually ships
 * in, and it is the state in which an unqualified `format` produces «۲۶۴۳٫۱۷».
 */
class ChartExportTest {

    private val tehran: ZoneId = ZoneId.of("Asia/Tehran")

    private val bars = (0 until 5).map { index ->
        val price = 2640.0 + index
        Candle(1_700_000_000L + index * 3600, price, price + 1.5, price - 1.25, price + 0.75, 12.5)
    }

    private fun <T> underPersianLocale(block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("fa-IR"))
        return try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    private fun csv() = underPersianLocale {
        ChartExport.toCsv(bars, "XAUUSD", ChartInterval.Preset(Timeframe.H1), "Binance", tehran)
    }

    @Test
    fun `the file opens on a Persian Windows machine because it starts with the mark`() {
        assertTrue(csv().startsWith("\uFEFF"))
    }

    @Test
    fun `the rows end the way the specification says and Excel expects`() {
        val text = csv()
        assertTrue(text.contains("\r\n"))
        // A lone newline anywhere would be a row some Windows builds run together with the next.
        assertFalse(text.replace("\r\n", "").contains("\n"))
    }

    @Test
    fun `the first row says what these numbers are`() {
        val first = csv().removePrefix("\uFEFF").substringBefore("\r\n")
        assertTrue(first.contains("XAUUSD"))
        assertTrue(first.contains("H1"))
        assertTrue(first.contains("Binance"))
        assertTrue(first.startsWith("\"#"))
    }

    @Test
    fun `every row has exactly as many fields as there are headings`() {
        val lines = csv().removePrefix("\uFEFF").split("\r\n").filter { it.isNotBlank() }
        // The provenance comment is one field; every row after it is the full width.
        for (line in lines.drop(1)) {
            assertEquals(ChartExport.HEADERS.size, line.count { it == ',' } + 1)
        }
    }

    @Test
    fun `no price ever reaches a numeric column as a Persian digit`() {
        underPersianLocale {
            val fields = ChartExport.fieldsOf(bars.first(), tehran)
            for (column in ChartExport.NUMERIC_COLUMNS) {
                val value = fields[column]
                assertTrue(
                    "column $column is text a spreadsheet will sum to zero: $value",
                    value.none { it in '۰'..'۹' },
                )
            }
        }
    }

    @Test
    fun `a bar with no volume writes a blank rather than a nought`() {
        val silent = Candle(1_700_000_000L, 10.0, 11.0, 9.0, 10.5, null)
        val fields = ChartExport.fieldsOf(silent, tehran)
        assertEquals("", fields.last())
    }

    @Test
    fun `a price keeps its precision without inventing any`() {
        assertEquals("2643.17", ChartExport.number(2643.17))
        assertEquals("0.00000042", ChartExport.number(0.00000042))
        assertEquals("", ChartExport.number(Double.NaN))
        assertEquals("", ChartExport.number(null))
    }

    @Test
    fun `the Jalali column sorts into calendar order rather than alphabetical`() {
        underPersianLocale {
            val early = ChartExport.jalaliDate(1_700_000_000L, tehran)
            val late = ChartExport.jalaliDate(1_800_000_000L, tehran)
            assertTrue("$early should sort before $late", early < late)
            assertTrue(early.none { it in '۰'..'۹' })
            // Zero-padded year/month/day, which is what makes the comparison above true.
            assertEquals(10, early.length)
        }
    }

    @Test
    fun `the clock column stays Latin under a Persian default locale`() {
        underPersianLocale {
            val clock = ChartExport.clock(1_700_000_000L, tehran)
            assertEquals(5, clock.length)
            assertTrue(clock.none { it in '۰'..'۹' })
        }
    }

    @Test
    fun `a symbol with a slash cannot become a path`() {
        val name = ChartExport.fileName("BTC/USDT", ChartInterval.Preset(Timeframe.D1), tehran)
        assertFalse(name.contains('/'))
        assertTrue(name.endsWith(".csv"))
        assertTrue(name.contains("D1"))
    }

    @Test
    fun `a quote inside a field cannot break the row apart`() {
        val odd = ChartExport.toCsv(
            bars.take(1),
            "A\"B",
            ChartInterval.Preset(Timeframe.H1),
            "ven,ue",
            tehran,
        )
        assertTrue(odd.contains("\"\""))
        val header = odd.removePrefix("\uFEFF").substringBefore("\r\n")
        // The comma inside the venue name is inside one quoted field, not a column boundary.
        assertTrue(header.startsWith("\"") && header.endsWith("\""))
    }

    @Test
    fun `the bars come out oldest first however they went in`() {
        val shuffled = bars.reversed()
        val text = ChartExport.toCsv(shuffled, "X", ChartInterval.Preset(Timeframe.H1), "v", tehran)
        val times = text.removePrefix("\uFEFF").split("\r\n")
            .drop(2)
            .filter { it.isNotBlank() }
            .map { it.substringAfter("\"").substringBefore("\"") }
        assertEquals(times.sorted(), times)
    }
}
