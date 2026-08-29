package com.coinepro.feature.portfolio

import java.io.ByteArrayOutputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * One value in a spreadsheet cell, with its type decided before it is written.
 *
 * The distinction is the whole reason this type exists. A number written as text sits in the cell
 * looking exactly like a number and silently answers zero to every `SUM` over the column, and there
 * is no visual difference to warn the reader — which is the same failure the Persian-digit rule
 * guards against from the other direction.
 */
internal sealed interface SheetCell {
    /** Anything a formula will never be run over: a symbol, a date, a reason, a yes or a no. */
    data class Text(val value: String) : SheetCell

    /** A real number, written as a bare numeric literal so a spreadsheet can add it up. */
    data class Number(val value: Double) : SheetCell

    /** A genuinely absent value. Written as no cell at all, rather than as a zero or an empty string. */
    data object Blank : SheetCell
}

/**
 * A valid `.xlsx` file, written by hand.
 *
 * ### Why there is no library here
 *
 * The obvious answer is Apache POI, and it is the wrong one for this app. POI is roughly twelve
 * megabytes of jars before shrinking and carries `javax.xml` plus reflection-heavy OOXML bindings
 * that an Android build then has to be taught to keep; the released APK would grow by more than the
 * whole chart engine, for one button most readers press once. The project holds no spreadsheet
 * dependency today — deliberately — and adding one to a mobile app for a single export is a trade
 * that only looks cheap until the next R8 rule file.
 *
 * A `.xlsx` is a zip of a handful of XML parts. Written for a fixed shape — one sheet, one header
 * row, text and numbers only, no formulas, no merged cells, no images — the whole format is the two
 * hundred lines below, and every one of them is readable. The moment this needs a second sheet with
 * a chart in it the trade changes, and so should this decision.
 *
 * ### What is deliberately not used
 *
 * There is no `sharedStrings.xml`. The shared-string table is a size optimisation for spreadsheets
 * that repeat the same label thousands of times, and a trade history repeats almost nothing except
 * the symbol; inline strings cost a few more bytes and remove an entire part, its relationship, and
 * the index bookkeeping that is the usual source of a corrupt-file dialog.
 *
 * Dates are written as text rather than as Excel date serials. A serial needs a number format, the
 * 1900 leap-year bug, and a decision about which timezone midnight means — and the reader would be
 * shown a date that had quietly moved. [TradeExport] writes both an ISO instant and a Jalali date as
 * text instead, which sort correctly, mean exactly one thing, and can be read by a human.
 */
internal object MinimalWorkbook {

    /**
     * Build the workbook.
     *
     * [header] is written as row one in bold and frozen in place, because a twenty-column export
     * scrolled past its own headings is a wall of unlabelled numbers. Rows shorter than the header
     * are padded with blanks rather than rejected: a ragged row is a bug in the caller, and an
     * exception thrown at the moment a reader presses "export" is a worse way to report it than a
     * file with a gap in it.
     */
    fun build(sheetName: String, header: List<String>, rows: List<List<SheetCell>>): ByteArray {
        val body = StringBuilder()
        body.append(XML_DECLARATION)
        body.append("<worksheet xmlns=\"$MAIN_NAMESPACE\">")
        // Right-to-left, because this workbook's headings are Persian and a reader opening it in
        // Excel should find column A on the right, where the rest of their documents put it.
        body.append(
            "<sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\">" +
                "<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>" +
                "</sheetView></sheetViews>",
        )
        body.append("<sheetData>")
        body.append("<row r=\"1\">")
        header.forEachIndexed { column, label ->
            body.append(textCell(columnName(column) + "1", label, bold = true))
        }
        body.append("</row>")
        rows.forEachIndexed { index, row ->
            val rowNumber = index + 2
            body.append("<row r=\"$rowNumber\">")
            for (column in header.indices) {
                when (val cell = row.getOrElse(column) { SheetCell.Blank }) {
                    is SheetCell.Blank -> Unit
                    is SheetCell.Text ->
                        body.append(textCell(columnName(column) + rowNumber, cell.value, bold = false))
                    is SheetCell.Number -> body.append(
                        "<c r=\"${columnName(column)}$rowNumber\"><v>${number(cell.value)}</v></c>",
                    )
                }
            }
            body.append("</row>")
        }
        body.append("</sheetData></worksheet>")

        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.part("[Content_Types].xml", CONTENT_TYPES)
            zip.part("_rels/.rels", ROOT_RELATIONSHIPS)
            zip.part("xl/workbook.xml", workbook(sheetName))
            zip.part("xl/_rels/workbook.xml.rels", WORKBOOK_RELATIONSHIPS)
            zip.part("xl/styles.xml", STYLES)
            zip.part("xl/worksheets/sheet1.xml", body.toString())
        }
        return output.toByteArray()
    }

    /**
     * A zip entry with a fixed timestamp.
     *
     * Fixed so that exporting the same trades twice produces the same bytes. That is what makes the
     * export testable at all — a test can assert on the file rather than only on its parts — and it
     * means a reader who exports, re-exports and compares sees the trades change rather than the
     * clock. The constant is midday on the first of January 2000: the zip format cannot store a date
     * before 1980 and midday keeps it clear of that floor in every timezone on earth.
     */
    private fun ZipOutputStream.part(name: String, content: String) {
        val entry = ZipEntry(name)
        entry.time = FIXED_TIMESTAMP
        putNextEntry(entry)
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun textCell(reference: String, value: String, bold: Boolean): String {
        val style = if (bold) " s=\"1\"" else ""
        // `xml:space="preserve"` so a label that legitimately ends in a space survives the round
        // trip. Excel trims it otherwise, and a trimmed heading no longer matches the column a
        // formula names.
        return "<c r=\"$reference\"$style t=\"inlineStr\"><is><t xml:space=\"preserve\">" +
            escape(value) + "</t></is></c>"
    }

    /**
     * A number as a bare literal.
     *
     * `Locale.US` symbols, no grouping, at most eight decimals. Grouping separators are the trap
     * here rather than a nicety: `64,182.40` inside a spreadsheet cell is a string, and every sum
     * over the column silently drops it. A device locale left to itself would supply both a Persian
     * digit set and an Arabic decimal separator, which fails the same way while looking correct.
     */
    private fun number(value: Double): String =
        if (!value.isFinite()) "0" else DecimalFormat("0.########", DecimalFormatSymbols(Locale.US)).format(value)

    /** Spreadsheet column letters: A, B, … Z, AA, AB. */
    private fun columnName(index: Int): String {
        var remaining = index
        val name = StringBuilder()
        while (true) {
            name.insert(0, 'A' + remaining % 26)
            remaining = remaining / 26 - 1
            if (remaining < 0) break
        }
        return name.toString()
    }

    /**
     * XML escaping, including the control characters XML 1.0 simply cannot hold.
     *
     * A broker's close reason has arrived with a stray control byte in it before. Left in, it
     * produces a file every spreadsheet refuses to open with a message naming the wrong problem, so
     * it is dropped here rather than passed through.
     */
    private fun escape(value: String): String {
        val out = StringBuilder(value.length)
        for (character in value) {
            when {
                character == '&' -> out.append("&amp;")
                character == '<' -> out.append("&lt;")
                character == '>' -> out.append("&gt;")
                character == '"' -> out.append("&quot;")
                character == '\'' -> out.append("&apos;")
                character == '\n' || character == '\r' || character == '\t' -> out.append(character)
                character.code < 0x20 -> Unit
                else -> out.append(character)
            }
        }
        return out.toString()
    }

    private fun workbook(sheetName: String): String =
        XML_DECLARATION +
            "<workbook xmlns=\"$MAIN_NAMESPACE\" xmlns:r=\"$RELATIONSHIP_NAMESPACE\">" +
            "<sheets><sheet name=\"${escape(sheetName.take(31))}\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
            "</workbook>"

    private const val XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
    private const val MAIN_NAMESPACE = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val RELATIONSHIP_NAMESPACE = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

    /** Midday, 1 January 2000. See [part]. */
    private const val FIXED_TIMESTAMP = 946_728_000_000L

    private const val CONTENT_TYPES = XML_DECLARATION +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/xl/workbook.xml\" " +
        "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
        "<Override PartName=\"/xl/worksheets/sheet1.xml\" " +
        "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
        "<Override PartName=\"/xl/styles.xml\" " +
        "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
        "</Types>"

    private const val ROOT_RELATIONSHIPS = XML_DECLARATION +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"$RELATIONSHIP_NAMESPACE/officeDocument\" Target=\"xl/workbook.xml\"/>" +
        "</Relationships>"

    private const val WORKBOOK_RELATIONSHIPS = XML_DECLARATION +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"$RELATIONSHIP_NAMESPACE/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"$RELATIONSHIP_NAMESPACE/styles\" Target=\"styles.xml\"/>" +
        "</Relationships>"

    /**
     * Two fonts and nothing else.
     *
     * The two fills are not optional decoration: Excel requires the first fill to be `none` and the
     * second to be `gray125`, in that order, and a styles part without both is one of the ways a
     * hand-written workbook opens as "unreadable content".
     */
    private const val STYLES = XML_DECLARATION +
        "<styleSheet xmlns=\"$MAIN_NAMESPACE\">" +
        "<fonts count=\"2\">" +
        "<font><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "<font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font>" +
        "</fonts>" +
        "<fills count=\"2\">" +
        "<fill><patternFill patternType=\"none\"/></fill>" +
        "<fill><patternFill patternType=\"gray125\"/></fill>" +
        "</fills>" +
        "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
        "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
        "<cellXfs count=\"2\">" +
        "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>" +
        "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>" +
        "</cellXfs>" +
        "</styleSheet>"
}
