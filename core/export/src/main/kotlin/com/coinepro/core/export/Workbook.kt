package com.coinepro.core.export

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A valid `.xlsx` file, written by hand, for whatever a caller can express as rows of text.
 *
 * ### The rule this file exists for
 *
 * **A column the caller declares numeric is written as a real number cell, never as an inline
 * string.** It is [Csv]'s rule two with a different mechanism and the same ending: a number written
 * as text sits in the cell looking exactly like a number, and `SUM` over the column returns zero
 * with nothing to warn the reader. In a CSV the trap is the digits; here the trap is the cell type,
 * and a spreadsheet asked to guess a type on a Persian machine guesses wrong. So the caller
 * declares which columns are numbers — see the `numericColumns` argument — and this writes those
 * cells as bare `<v>` literals and every other cell as a declared inline string.
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
 * that repeat one label thousands of times, and these exports repeat almost nothing except a
 * symbol; inline strings cost a few more bytes and remove an entire part, its relationship, and the
 * index bookkeeping that is the usual source of a corrupt-file dialog.
 *
 * Dates are written as text rather than as Excel date serials. A serial needs a number format, the
 * 1900 leap-year bug, and a decision about what timezone midnight means — and the reader would be
 * shown a date that had quietly moved. Callers write both an ISO instant and a Jalali date as text
 * instead, which sort correctly, mean exactly one thing, and can be read by a human.
 */
object Workbook {

    /**
     * Build the workbook.
     *
     * [header] is row one, bold and frozen in place, because a twenty-column export scrolled past
     * its own headings is a wall of unlabelled numbers. [numericColumns] are indices into [header];
     * a cell in one of them is written as a number when its text reads back as one, and as an
     * inline string when it does not — a caller that declares a column numeric and then writes
     * «خرید» into it gets a readable file rather than a workbook Excel refuses to open.
     *
     * An empty string is written as no cell at all rather than as a zero or as an empty string
     * cell, for the reason [Numbers.cell] gives: absent and nought are different claims.
     *
     * Rows shorter than the header are padded with blanks rather than rejected. A ragged row is a
     * bug in the caller, and an exception thrown at the moment a reader presses "export" is a worse
     * way to report it than a file with a gap in it.
     */
    fun build(
        sheetName: String,
        header: List<String>,
        rows: List<List<String>>,
        numericColumns: Set<Int>,
    ): ByteArray = build(
        sheetName = sheetName,
        preamble = emptyList(),
        header = header,
        rows = rows,
        numericColumns = numericColumns,
    )

    /**
     * The same workbook with a block of rows above the table: a caption, or a summary.
     *
     * [preamble] rows are written first, then one blank row, then the header — the same shape
     * [Csv.build] writes, so a reader who exports both formats of one report finds the same file
     * twice rather than two files.
     *
     * Preamble cells are always text, including the ones that look like numbers. A preamble here is
     * a key and its value on one line, not a column anything is summed over, and making it numeric
     * would mean deciding a cell's type by parsing it — the guess this whole module exists to
     * remove rather than relocate. Numbers that a formula will touch belong in [rows], where their
     * column is declared.
     */
    fun build(
        sheetName: String,
        preamble: List<List<String>>,
        header: List<String>,
        rows: List<List<String>>,
        numericColumns: Set<Int>,
    ): ByteArray {
        // The header sits below the preamble and the one blank row that separates them.
        val headerRow = if (preamble.isEmpty()) 1 else preamble.size + 2
        val body = StringBuilder()
        body.append(XML_DECLARATION)
        body.append("<worksheet xmlns=\"$MAIN_NAMESPACE\">")
        // Right-to-left, because every export in this app has Persian headings and a reader opening
        // one in Excel should find column A on the right, where the rest of their documents put it.
        body.append(
            "<sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\">" +
                "<pane ySplit=\"$headerRow\" topLeftCell=\"A${headerRow + 1}\" " +
                "activePane=\"bottomLeft\" state=\"frozen\"/>" +
                "</sheetView></sheetViews>",
        )
        body.append("<sheetData>")
        preamble.forEachIndexed { index, row ->
            val rowNumber = index + 1
            body.append("<row r=\"$rowNumber\">")
            row.forEachIndexed { column, value ->
                if (value.isNotEmpty()) {
                    body.append(textCell(columnName(column) + rowNumber, value, bold = column == 0))
                }
            }
            body.append("</row>")
        }
        body.append("<row r=\"$headerRow\">")
        header.forEachIndexed { column, label ->
            body.append(textCell(columnName(column) + headerRow, label, bold = true))
        }
        body.append("</row>")
        rows.forEachIndexed { index, row ->
            val rowNumber = headerRow + 1 + index
            body.append("<row r=\"$rowNumber\">")
            for (column in header.indices) {
                val value = row.getOrElse(column) { "" }
                val reference = columnName(column) + rowNumber
                val number = if (column in numericColumns) Numbers.parse(value) else null
                when {
                    value.isEmpty() -> Unit
                    number != null -> body.append("<c r=\"$reference\"><v>${Numbers.cell(number)}</v></c>")
                    else -> body.append(textCell(reference, value, bold = false))
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
     * Fixed so that exporting the same rows twice produces the same bytes. That is what makes the
     * export testable at all — a test can assert on the file rather than only on its parts — and it
     * means a reader who exports, re-exports and compares sees their data change rather than the
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
