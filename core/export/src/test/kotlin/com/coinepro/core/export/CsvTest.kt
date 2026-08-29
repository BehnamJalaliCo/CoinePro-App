package com.coinepro.core.export

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The CSV writer, checked with the device locale set to Persian.
 *
 * That setting is the point of the fixture rather than a detail of it. Every failure guarded
 * against here is invisible on an English machine and appears only on the machines this app's
 * readers use: the file looks perfectly correct and every formula over it returns zero.
 */
class CsvTest {

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

    private val header = listOf("نماد", "قیمت", "توضیح")

    private fun linesOf(csv: String): List<String> =
        csv.removePrefix(Csv.BOM).split(Csv.LINE_BREAK).dropLast(1)

    @Test
    fun `the first three bytes of the file are the byte-order mark`() {
        val bytes = Csv.build(header, listOf(listOf("XAUUSD", "1", "-"))).toByteArray(Charsets.UTF_8)
        // Without them Excel on a Persian Windows machine decodes the file in the system code page
        // and every Persian heading arrives as mojibake.
        assertArrayEquals(
            byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()),
            bytes.copyOfRange(0, 3),
        )
    }

    @Test
    fun `a Persian heading survives the round trip through UTF-8`() {
        val text = Csv.build(header, emptyList())
        val decoded = String(text.toByteArray(Charsets.UTF_8), Charsets.UTF_8)
        assertTrue(decoded.contains("\"نماد\""))
        assertEquals(header, fields(linesOf(decoded).first()))
    }

    @Test
    fun `every row ends CRLF and no lone newline is left anywhere`() {
        val text = Csv.build(header, listOf(listOf("a", "1", "b"), listOf("c", "2", "d")))
        assertTrue(text.endsWith(Csv.LINE_BREAK))
        // A lone newline is a row some Windows builds run together with the next one.
        assertFalse(text.replace("\r\n", "").contains("\n"))
        assertEquals(3, linesOf(text).size)
    }

    @Test
    fun `a value holding a comma stays one field`() {
        val text = Csv.build(header, listOf(listOf("BTC,USDT", "1", "-")))
        val row = fields(linesOf(text)[1])
        assertEquals(header.size, row.size)
        assertEquals("BTC,USDT", row[0])
    }

    @Test
    fun `a value holding a quote has it doubled and comes back single`() {
        val text = Csv.build(header, listOf(listOf("XAUUSD", "1", "stop, \"trailed\"")))
        assertTrue(text.contains("\"\"trailed\"\""))
        assertEquals("stop, \"trailed\"", fields(linesOf(text)[1])[2])
    }

    @Test
    fun `a numeric cell holds no Persian digit even when the device locale is Persian`() {
        assertEquals("fa", Locale.getDefault().language)
        val text = Csv.build(header, listOf(listOf("XAUUSD", Numbers.cell(2_412.85), "-")))
        val price = fields(linesOf(text)[1])[1]
        assertEquals("2412.85", price)
        assertFalse(price.any { it in '۰'..'۹' || it in '٠'..'٩' })
        // An Arabic decimal separator breaks a spreadsheet the same way a Persian digit does.
        assertFalse(price.contains('٫'))
    }

    @Test
    fun `a table with no rows still carries its headings`() {
        val lines = linesOf(Csv.build(header, emptyList()))
        assertEquals(1, lines.size)
        assertEquals(header, fields(lines.first()))
    }

    @Test
    fun `a preamble is written above the headings and an empty row in it becomes a blank line`() {
        val text = Csv.build(
            preamble = listOf(listOf("گزارش", "BTCUSDT"), emptyList()),
            header = header,
            rows = listOf(listOf("a", "1", "b")),
        )
        val lines = linesOf(text)
        assertEquals("گزارش", fields(lines[0])[0])
        // The gap every spreadsheet reads as a blank row and no parser mistakes for a heading.
        assertEquals("", lines[1])
        assertEquals(header, fields(lines[2]))
    }

    @Test
    fun `a preamble row need not be as wide as the table`() {
        val text = Csv.build(preamble = listOf(listOf("# XAUUSD")), header = header, rows = emptyList())
        assertEquals(1, fields(linesOf(text).first()).size)
    }

    /** A splitter for exactly the dialect written above: every field quoted, inner quotes doubled. */
    private fun fields(line: String): List<String> {
        val out = mutableListOf<String>()
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
                    out += field.toString()
                    field.setLength(0)
                }
                else -> field.append(character)
            }
            index++
        }
        out += field.toString()
        return out
    }
}
