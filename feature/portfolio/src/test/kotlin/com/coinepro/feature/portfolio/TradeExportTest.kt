package com.coinepro.feature.portfolio

import com.coinepro.core.portfolio.ClosedTrade
import com.coinepro.core.portfolio.TradeDirection
import java.io.ByteArrayInputStream
import java.time.ZoneOffset
import java.util.Locale
import java.util.zip.ZipInputStream
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The export, checked with the device locale set to Persian.
 *
 * That setting is the whole point of the fixture rather than a detail of it. Every failure this
 * file guards against is invisible on an English machine and appears only on the machines this
 * app's readers actually use: a formatter that follows the default locale emits Persian digits and
 * an Arabic decimal separator, and the resulting file looks perfectly correct while every formula
 * over it returns zero.
 */
class TradeExportTest {

    private lateinit var previous: Locale

    @Before
    fun useAPersianDevice() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("fa-IR"))
    }

    @After
    fun restoreTheDevice() {
        Locale.setDefault(previous)
    }

    private val base = 1_767_225_600L // 2026-01-01T00:00:00Z

    private val trades = listOf(
        ClosedTrade(
            id = "t-1",
            symbol = "XAUUSD",
            direction = TradeDirection.BUY,
            volume = 0.15,
            entry = 2_412.85,
            exit = 2_431.4,
            openedAt = base - 3_600,
            closedAt = base,
            grossProfit = 285.75,
            commission = -4.5,
            swap = -1.25,
            netProfit = 280.0,
            pips = 18.55,
            closeReason = "tp",
            balanceAfter = 10_280.0,
            currency = "USD",
        ),
        ClosedTrade(
            id = "t-2",
            symbol = "BTCUSDT",
            direction = TradeDirection.SELL,
            volume = 0.002,
            entry = null,
            exit = 64_182.4,
            openedAt = null,
            closedAt = base + 86_400,
            netProfit = -124.5,
            liquidated = true,
            currency = "USDT",
        ),
    )

    /** A quoted-field CSV splitter, good enough for a file this test wrote. */
    private fun row(line: String): List<String> {
        val fields = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val character = line[index]
            when {
                character == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    field.append('"')
                    index++
                }
                character == '"' -> inQuotes = !inQuotes
                character == ',' && !inQuotes -> {
                    fields += field.toString()
                    field.setLength(0)
                }
                else -> field.append(character)
            }
            index++
        }
        fields += field.toString()
        return fields
    }

    private fun rows(csv: String): List<List<String>> =
        csv.removePrefix("﻿").split("\r\n").filter { it.isNotBlank() }.map(::row)

    @Test
    fun `the CSV begins with the UTF-8 byte order mark`() {
        val bytes = TradeExport.toCsv(trades, ZoneOffset.UTC).toByteArray(Charsets.UTF_8)
        // Without these three bytes Excel on a Persian Windows machine decodes the file in the
        // system code page and every Persian heading arrives as mojibake.
        assertArrayEquals(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            bytes.copyOfRange(0, 3),
        )
    }

    @Test
    fun `no numeric column holds a Persian digit even when the device locale is Persian`() {
        assertEquals("fa", Locale.getDefault().language)
        val parsed = rows(TradeExport.toCsv(trades, ZoneOffset.UTC))
        assertEquals(TradeExport.HEADERS, parsed.first())

        for (line in parsed.drop(1)) {
            for (column in TradeExport.NUMERIC_COLUMNS) {
                val value = line[column]
                assertFalse(
                    "column $column held a non-Latin digit: $value",
                    value.any { it in '۰'..'۹' || it in '٠'..'٩' },
                )
                // And an Arabic decimal separator would break a spreadsheet the same way a
                // Persian digit does, silently and while looking right.
                assertFalse(value.contains('٫'))
            }
        }
    }

    @Test
    fun `numbers are written with a dot and without a thousands separator`() {
        val parsed = rows(TradeExport.toCsv(trades, ZoneOffset.UTC))
        val first = parsed[1]
        assertEquals("0.15", first[3])
        assertEquals("2412.85", first[4])
        // A grouped `2,412.85` inside a spreadsheet cell is text, and every sum over the column
        // would silently drop it.
        assertFalse(first[4].contains(","))
        assertEquals("280", first[14])
        assertEquals("-124.5", parsed[2][14])
    }

    @Test
    fun `every timestamp is written twice, as an ISO instant and as a Jalali date`() {
        val first = rows(TradeExport.toCsv(trades, ZoneOffset.UTC))[1]
        assertEquals("2025-12-31T23:00:00Z", first[6])
        assertEquals("1404/10/10", first[7])
        assertEquals("2026-01-01T00:00:00Z", first[8])
        // Zero-padded and Latin, so the column sorts into chronological order. «۱۱ دی ۱۴۰۴» would
        // sort by the digit ۱ and put Dey after Aban.
        assertEquals("1404/10/11", first[9])
    }

    @Test
    fun `an absent value is left empty rather than written as a zero`() {
        val second = rows(TradeExport.toCsv(trades, ZoneOffset.UTC))[2]
        // No entry price and no open time: the server said it did not know, and a zero here would
        // be this app inventing a price of nought.
        assertEquals("", second[4])
        assertEquals("", second[6])
        assertEquals("", second[7])
        assertEquals("", second[10])
        assertEquals("", second[11])
    }

    @Test
    fun `a field holding a comma or a quote survives the round trip`() {
        val awkward = trades.first().copy(closeReason = "stop, \"trailed\"")
        val parsed = rows(TradeExport.toCsv(listOf(awkward), ZoneOffset.UTC))
        assertEquals("stop, \"trailed\"", parsed[1][16])
        assertEquals(TradeExport.HEADERS.size, parsed[1].size)
    }

    @Test
    fun `an empty history still exports its headings`() {
        val parsed = rows(TradeExport.toCsv(emptyList(), ZoneOffset.UTC))
        assertEquals(1, parsed.size)
        assertEquals(TradeExport.HEADERS, parsed.first())
    }

    private fun partsOf(bytes: ByteArray): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                names += entry.name
                entry = zip.nextEntry
            }
        }
        return names
    }

    private fun partText(bytes: ByteArray, name: String): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == name) return zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        throw AssertionError("part $name is not in the workbook")
    }

    @Test
    fun `the XLSX bytes open as a zip holding the parts a workbook needs`() {
        val parts = partsOf(TradeExport.toXlsx(trades, ZoneOffset.UTC))
        assertEquals(
            listOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/styles.xml",
                "xl/worksheets/sheet1.xml",
            ),
            parts,
        )
    }

    @Test
    fun `the workbook writes numbers as numbers and text as text`() {
        val sheet = partText(TradeExport.toXlsx(trades, ZoneOffset.UTC), "xl/worksheets/sheet1.xml")
        // A bare `<v>` is a numeric cell; a spreadsheet will sum this column.
        assertTrue(sheet.contains("<v>280</v>"))
        assertTrue(sheet.contains("<v>-124.5</v>"))
        assertTrue(sheet.contains("<v>2412.85</v>"))
        // The symbol is declared as an inline string, so nothing tries to parse it as a number.
        assertTrue(sheet.contains("XAUUSD"))
        assertFalse(sheet.any { it in '۰'..'۹' })
        // Right-to-left, because the headings are Persian.
        assertTrue(sheet.contains("rightToLeft=\"1\""))
    }

    @Test
    fun `the same trades export to the same bytes twice`() {
        // Fixed entry timestamps rather than the clock, so a reader who exports twice sees their
        // trades change rather than the minute they pressed the button.
        assertArrayEquals(
            TradeExport.toXlsx(trades, ZoneOffset.UTC),
            TradeExport.toXlsx(trades, ZoneOffset.UTC),
        )
    }

    @Test
    fun `an awkward close reason cannot produce XML a spreadsheet refuses to open`() {
        val awkward = trades.first().copy(closeReason = "stop & <run>")
        val sheet = partText(TradeExport.toXlsx(listOf(awkward), ZoneOffset.UTC), "xl/worksheets/sheet1.xml")
        assertTrue(sheet.contains("stop &amp; &lt;run&gt;"))
    }
}
