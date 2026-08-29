package com.coinepro.core.export

import java.io.ByteArrayInputStream
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
 * The hand-written workbook, checked as a file rather than as a string.
 *
 * The assertions that matter are the ones a reader would otherwise discover by opening the export
 * in Excel: whether it opens at all, and whether the column they sum adds up.
 */
class WorkbookTest {

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

    private val header = listOf("نماد", "سود خالص", "دلیل")
    private val rows = listOf(
        listOf("XAUUSD", Numbers.cell(280.0), "tp"),
        listOf("BTCUSDT", Numbers.cell(-124.5), ""),
    )
    private val numeric = setOf(1)

    private fun bytes(): ByteArray = Workbook.build("معاملات", header, rows, numeric)

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

    private fun sheet(bytes: ByteArray): String = partText(bytes, "xl/worksheets/sheet1.xml")

    @Test
    fun `the bytes open as a zip holding the parts a workbook needs`() {
        assertEquals(
            listOf(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/styles.xml",
                "xl/worksheets/sheet1.xml",
            ),
            partsOf(bytes()),
        )
    }

    @Test
    fun `a declared numeric column is a number cell rather than an inline string`() {
        val xml = sheet(bytes())
        // A bare `<v>` with no `t` attribute is a number; a spreadsheet will sum this column.
        assertTrue(xml.contains("<c r=\"B2\"><v>280</v></c>"))
        assertTrue(xml.contains("<c r=\"B3\"><v>-124.5</v></c>"))
        // And the text beside it is declared a string, so nothing tries to parse the symbol.
        assertTrue(xml.contains("<c r=\"A2\" t=\"inlineStr\">"))
        assertFalse(xml.any { it in '۰'..'۹' })
    }

    @Test
    fun `a numeric column holding text a formula cannot use is written as text rather than refused`() {
        // A caller that declares a column numeric and then writes a word into it gets a readable
        // file. A workbook Excel will not open is a worse answer than a cell somebody can see.
        val xml = sheet(Workbook.build("s", header, listOf(listOf("X", "خرید", "-")), numeric))
        assertTrue(xml.contains("خرید"))
        assertFalse(xml.contains("<v>"))
    }

    @Test
    fun `an empty value is written as no cell at all`() {
        // Absent and nought are different claims, and a zero here would be this app inventing one.
        val xml = sheet(bytes())
        assertFalse(xml.contains("C3"))
        assertTrue(xml.contains("<row r=\"3\">"))
    }

    @Test
    fun `the headings are frozen and the sheet reads right to left`() {
        val xml = sheet(bytes())
        assertTrue(xml.contains("rightToLeft=\"1\""))
        assertTrue(xml.contains("ySplit=\"1\""))
        assertTrue(xml.contains("topLeftCell=\"A2\""))
    }

    @Test
    fun `a preamble pushes the table down and keeps a blank row above it`() {
        val xml = sheet(
            Workbook.build(
                sheetName = "s",
                preamble = listOf(listOf("گزارش", "BTCUSDT"), listOf("تعداد کندل", "400")),
                header = header,
                rows = rows,
                numericColumns = numeric,
            ),
        )
        // Two preamble rows, row three left out entirely as the gap, headings on row four.
        assertFalse(xml.contains("<row r=\"3\">"))
        assertTrue(xml.contains("<c r=\"A4\" s=\"1\" t=\"inlineStr\">"))
        assertTrue(xml.contains("<c r=\"B5\"><v>280</v></c>"))
        // The freeze follows the headings rather than staying on row one.
        assertTrue(xml.contains("ySplit=\"4\""))
    }

    @Test
    fun `a preamble cell that looks like a number is still written as text`() {
        // The type is declared, never guessed: a preamble is a key and its value, not a column.
        val xml = sheet(
            Workbook.build("s", listOf(listOf("تعداد کندل", "400")), header, emptyList(), numeric),
        )
        assertTrue(xml.contains("<c r=\"B1\" t=\"inlineStr\"><is><t xml:space=\"preserve\">400</t></is></c>"))
    }

    @Test
    fun `a value carrying XML syntax cannot produce a file a spreadsheet refuses to open`() {
        val xml = sheet(Workbook.build("s", header, listOf(listOf("X", "1", "stop & <run>")), numeric))
        assertTrue(xml.contains("stop &amp; &lt;run&gt;"))
    }

    @Test
    fun `a control character in a value is dropped rather than written into the XML`() {
        // XML 1.0 cannot hold one, and a file containing one opens as "unreadable content" with a
        // message naming the wrong problem. A broker's close reason has arrived with one before.
        val xml = sheet(Workbook.build("s", header, listOf(listOf("X\u0007Y", "1", "-")), numeric))
        assertTrue(xml.contains("XY"))
    }

    @Test
    fun `a row shorter than the headings is padded rather than rejected`() {
        val xml = sheet(Workbook.build("s", header, listOf(listOf("X")), numeric))
        assertTrue(xml.contains("<row r=\"2\">"))
        assertFalse(xml.contains("C2"))
    }

    @Test
    fun `the same rows export to the same bytes twice`() {
        // Fixed zip timestamps rather than the clock, so a reader who exports twice and compares
        // sees their data change rather than the minute they pressed the button.
        assertArrayEquals(bytes(), bytes())
    }

    @Test
    fun `a sheet name longer than Excel allows is cut rather than rejected`() {
        val name = "ب".repeat(40)
        val xml = partText(Workbook.build(name, header, rows, numeric), "xl/workbook.xml")
        assertTrue(xml.contains("name=\"${"ب".repeat(31)}\""))
    }
}
