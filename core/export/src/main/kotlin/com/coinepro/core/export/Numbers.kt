package com.coinepro.core.export

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * How a number is written into a file, in the one place that decides it.
 *
 * This is the second of the three rules the whole module exists for, and it is the one that costs
 * a reader money rather than patience. The device locale here is Persian by default, so an
 * unqualified `String.format`, `toString` on a `NumberFormat`, or a `DecimalFormat` built without
 * symbols renders `2412.85` as «۲۴۱۲٫۸۵». That cell is not a number. It is text that looks exactly
 * like a number, a spreadsheet answers zero to every formula over the column, and nothing anywhere
 * raises an error — the reader discovers it when their own arithmetic disagrees with the app's.
 *
 * So: `Locale.US` symbols, Latin digits, and no grouping separator. Grouping fails the same way for
 * the same reason — `64,182.40` inside a cell is a string, and in a CSV it is also a column
 * boundary hiding inside a field.
 */
object Numbers {

    /**
     * One numeric cell, or the empty string when there is no number to write.
     *
     * Empty rather than zero, and empty rather than `∞` or `NaN`. Nought is a value a row can
     * genuinely hold — a scratch trade closes at exactly zero — so writing it where the source said
     * nothing turns "we do not know" into a false claim of "nothing". A non-finite value is the
     * profit factor of a run with no losing trade, and «∞» in a numeric column is text a spreadsheet
     * sums as zero while displaying it; an empty cell sums as nothing, which is the honest answer.
     *
     * At most eight decimal places, which covers a token quoted at `0.00000042` without printing
     * gold's two as `2643.17000000`. `DecimalFormat` trims what it does not need.
     */
    fun cell(value: Double?): String {
        if (value == null || !value.isFinite()) return ""
        return format().format(value)
    }

    /**
     * The text of a cell read back as a number, or null when it is not one.
     *
     * [Workbook] uses it to decide whether a column a caller declared numeric really can be written
     * as a number. It is deliberately strict — `toDoubleOrNull` on text this module wrote, nothing
     * cleverer — because a lenient parse is type-guessing, and type-guessing is the failure this
     * module was built to remove rather than relocate.
     */
    internal fun parse(text: String): Double? = text.toDoubleOrNull()?.takeIf { it.isFinite() }

    /**
     * A fresh formatter each call.
     *
     * `DecimalFormat` is not thread-safe and an export runs off the main thread while the screen
     * that started it is still alive. One allocation per cell is nothing next to the zip and the
     * XML this feeds.
     */
    private fun format(): DecimalFormat = DecimalFormat("0.########", DecimalFormatSymbols(Locale.US))
}
