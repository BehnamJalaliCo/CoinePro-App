package com.coinepro.core.export

/**
 * Comma-separated text, written the one way this app writes it.
 *
 * ### Why this is a module and not a helper in whichever screen needed it first
 *
 * It was a helper in whichever screen needed it first. The trade history wrote a CSV, and then the
 * backtest report and the candle export each copied the same three rules out of it — verbatim, with
 * a comment explaining that they could not reach the original because it lived in another feature.
 * Three copies of a rule are three chances for two of them to be right. The rules below are the
 * entire reason this file exists, and they are stated here once.
 *
 * **1. The file starts with a UTF-8 byte-order mark.** Excel on a Persian Windows machine does not
 * detect UTF-8 from content: without the mark it decodes the file in the system code page and every
 * Persian column heading arrives as mojibake — «نماد» becomes `Ù†Ù…Ø§Ø¯`. Persian Windows is the
 * machine this app's readers are on, so the mark is the difference between a usable export and one
 * nobody tries a second time. It costs three bytes and every other tool ignores it.
 *
 * **2. Every number is Latin-digit, `Locale.US`, ungrouped.** See [Numbers]: the device locale is
 * Persian, a cell holding «۱۲۴٫۵» is text rather than a number, and every formula over the column
 * silently returns zero while the column looks exactly right.
 *
 * **3. Rows end CRLF, and every field is quoted with its inner quotes doubled.** CRLF is what the
 * CSV specification says and what Excel expects; a lone newline is read correctly by most things
 * and produces one very long row in some Windows builds. The quoting is not conditional here — a
 * value carrying a comma, a quote or a newline *must* be quoted, and quoting all of them costs two
 * bytes a field and removes the question of whether a broker's close reason, a venue name or a
 * symbol from a feed is free text this week. An unquoted comma shifts every column after it by one
 * without any error being raised anywhere in the chain.
 *
 * What this file does not know is what it is writing. Rows arrive as text that a caller has already
 * decided the shape of; trades, candles and backtests are the callers' business and never this
 * module's.
 */
object Csv {

    /** U+FEFF. Three bytes in UTF-8, and rule one above. */
    const val BOM: String = "\uFEFF"

    /** CRLF. Rule three above, and the reason a reader on Windows sees rows rather than one row. */
    const val LINE_BREAK: String = "\r\n"

    /**
     * A table: one header row, then the rows, each field quoted and the whole thing mark-first.
     *
     * The file ends with a line break, so the last row is terminated like every other one. A parser
     * that stops at the last separator and a parser that reads to the end then both see the same
     * number of rows.
     */
    fun build(header: List<String>, rows: List<List<String>>): String =
        build(preamble = emptyList(), header = header, rows = rows)

    /**
     * The same table with a block of rows in front of it: a provenance line, or a summary.
     *
     * [preamble] is written before the header, in the order given, and an **empty row in it becomes
     * an empty line** — which every spreadsheet reads as a blank row and no parser mistakes for a
     * heading. That is how a two-section file (a metrics block, a gap, then a table) is written
     * without this module needing to know what a metric is.
     *
     * A preamble row need not be as wide as the header. It is a caption or a key and its value, not
     * a row of the table, and padding it to the table's width would only fill the file with commas.
     */
    fun build(
        preamble: List<List<String>>,
        header: List<String>,
        rows: List<List<String>>,
    ): String {
        val out = StringBuilder()
        out.append(BOM)
        for (row in preamble) {
            out.append(line(row))
            out.append(LINE_BREAK)
        }
        out.append(line(header))
        for (row in rows) {
            out.append(LINE_BREAK)
            out.append(line(row))
        }
        out.append(LINE_BREAK)
        return out.toString()
    }

    /** One row, without its terminator. An empty row is an empty line, which is rule three's gap. */
    private fun line(values: List<String>): String = values.joinToString(",") { field(it) }

    /**
     * One field: always quoted, inner quotes doubled.
     *
     * Unconditional on purpose. The alternative is a test of whether *this* value happens to hold a
     * comma today, and the values that acquire one are exactly the ones nobody is watching — a
     * close reason typed by a dealer, a venue name, a strategy somebody names later.
     */
    private fun field(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
