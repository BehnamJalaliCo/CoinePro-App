package com.coinepro.feature.portfolio

import com.coinepro.core.common.JalaliDate
import com.coinepro.core.export.Csv
import com.coinepro.core.export.Numbers
import com.coinepro.core.export.Workbook
import com.coinepro.core.portfolio.ClosedTrade
import com.coinepro.core.portfolio.TradeDirection
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The trade history as a file the reader owns.
 *
 * TradingView puts both of these behind Essential: CSV export and XLSX export are paid features
 * there, and the free tier can look at its report but not take it anywhere. Both are given away
 * here on purpose. A trader's own closed trades are their own record — the app is a viewer of a
 * broker's ledger, not its owner — and a viewer that will not let you take your own numbers out is
 * a viewer holding them hostage. Do not put either of these behind a tier.
 *
 * ### What this file is and is not
 *
 * It is a row shape: which columns exist, in what order, which of them hold numbers, and how a
 * `ClosedTrade` becomes text. It is **not** a file writer. The byte-order mark, the Latin-digit
 * rule, the CRLF and the quoting, and the typed cells in the workbook all live in `core:export` —
 * they were copied into three files before that module existed, which is how two of the three came
 * to be right. Do not reimplement any of them here.
 *
 * ### The one rule that is genuinely this file's own
 *
 * **Every timestamp gets two columns: the ISO instant and the Jalali date as text.** Neither alone
 * is enough. A spreadsheet sorts, filters and subtracts the ISO one and cannot do any of that with
 * «۱۱ دی ۱۴۰۴»; a Persian trader reconciling the export against their own notes reads the Jalali
 * one and cannot do that with `2026-01-01T09:30:00Z`. Printing one and calling it the date makes
 * the file useless to whichever of the two is doing the reading.
 */
object TradeExport {

    /**
     * The history as comma-separated text.
     *
     * Oldest close first, whatever order the screen was showing: an export is read top to bottom
     * and a running balance that goes backwards is a file the reader has to re-sort before they can
     * believe it.
     */
    fun toCsv(trades: List<ClosedTrade>, zone: ZoneId = ZoneId.systemDefault()): String =
        Csv.build(HEADERS, trades.sortedBy { it.closedAt }.map { fieldsOf(it, zone) })

    /**
     * The same history as a real spreadsheet.
     *
     * The difference from [toCsv] that matters to a reader is the typing: a CSV is text all the way
     * down and a spreadsheet has to guess which columns are numbers, which is exactly the guess
     * that goes wrong on a Persian machine. [NUMERIC_COLUMNS] declares them instead, so a sum over
     * the net-profit column is correct on the first attempt.
     */
    fun toXlsx(trades: List<ClosedTrade>, zone: ZoneId = ZoneId.systemDefault()): ByteArray =
        Workbook.build(
            sheetName = SHEET_NAME,
            header = HEADERS,
            rows = trades.sortedBy { it.closedAt }.map { fieldsOf(it, zone) },
            numericColumns = NUMERIC_COLUMNS,
        )

    /**
     * The columns, in order, in Persian.
     *
     * Persian because this file is opened by the reader rather than by a program: it is their trade
     * history, going into their own spreadsheet, and a Latin `net_profit` heading over a Persian
     * interface is a heading they have to translate every time. It is also the reason `core:export`
     * writes a byte-order mark.
     */
    val HEADERS: List<String> = listOf(
        "شناسه",
        "نماد",
        "جهت",
        "حجم",
        "قیمت ورود",
        "قیمت خروج",
        "زمان باز شدن (ISO)",
        "تاریخ باز شدن (شمسی)",
        "زمان بسته شدن (ISO)",
        "تاریخ بسته شدن (شمسی)",
        "مدت نگهداری (ثانیه)",
        "سود ناخالص",
        "کارمزد",
        "سواپ",
        "سود خالص",
        "پیپ",
        "دلیل بسته شدن",
        "موجودی پس از معامله",
        "لیکویید",
        "ارز",
    )

    /**
     * Which columns hold numbers, by index into [HEADERS].
     *
     * Public because it is the contract twice over: the workbook writes exactly these as number
     * cells, and the tests check that no Persian digit reaches one. A definition kept in the test
     * file would be a copy free to drift away from the one the export uses.
     */
    val NUMERIC_COLUMNS: Set<Int> = setOf(3, 4, 5, 10, 11, 12, 13, 14, 15, 17)

    /**
     * One trade as text fields, aligned with [HEADERS]. Absent values are the empty string.
     *
     * The seconds a position was held is a number rather than a duration string, because the one
     * thing a reader does with that column is average it, and «۲ ساعت و ۱۴ دقیقه» averages to
     * nothing at all. The screen renders it as prose; the export renders it as arithmetic.
     */
    internal fun fieldsOf(trade: ClosedTrade, zone: ZoneId): List<String> = listOf(
        trade.id,
        trade.symbol,
        if (trade.direction == TradeDirection.BUY) "خرید" else "فروش",
        number(trade.volume),
        number(trade.entry),
        number(trade.exit),
        isoInstant(trade.openedAt),
        jalaliDate(trade.openedAt, zone),
        isoInstant(trade.closedAt),
        jalaliDate(trade.closedAt, zone),
        trade.durationSeconds?.toString().orEmpty(),
        number(trade.grossProfit),
        number(trade.commission),
        number(trade.swap),
        number(trade.netProfit),
        number(trade.pips),
        trade.closeReason.orEmpty(),
        number(trade.balanceAfter),
        if (trade.liquidated) "بله" else "خیر",
        trade.currency.orEmpty(),
    )

    /**
     * A Jalali date as `1404/10/11`, in Latin digits and zero-padded.
     *
     * Deliberately not [JalaliDate.format], which renders «۱۱ دی ۱۴۰۴» — correct for prose on a
     * screen and wrong in a spreadsheet twice over: it is Persian digits in a column a reader will
     * sort, and a month *name* does not sort into calendar order in any locale. Zero-padded
     * year/month/day is the one Jalali form that sorts lexicographically into chronological order,
     * which makes the column useful next to the ISO one rather than merely decorative.
     */
    internal fun jalaliDate(epochSeconds: Long?, zone: ZoneId): String {
        val seconds = epochSeconds ?: return ""
        val date = JalaliDate.fromInstant(Instant.ofEpochSecond(seconds), zone)
        return "%04d/%02d/%02d".format(Locale.US, date.year, date.month, date.day)
    }

    /** `2026-01-01T09:30:00Z`. UTC always: both servers timestamp in it and so should the file. */
    internal fun isoInstant(epochSeconds: Long?): String {
        val seconds = epochSeconds ?: return ""
        return DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(seconds))
    }

    /**
     * A number as the export writes it, or the empty string for an absent one.
     *
     * Empty rather than `0`. Nought is a value a trade can genuinely have — a scratch closes at
     * exactly zero — and writing it where the server said nothing turns "the opening leg fell
     * outside the window" into "this trade made no money", which is a different and false claim.
     * The formatting itself is [Numbers.cell]'s, so this column and the backtest's obey one rule.
     */
    internal fun number(value: Double?): String = Numbers.cell(value)

    /** Thirty-one characters is the sheet-name limit Excel enforces; this is well inside it. */
    private const val SHEET_NAME = "معاملات"
}
