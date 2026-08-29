package com.coinepro.feature.portfolio

import com.coinepro.core.common.JalaliDate
import com.coinepro.core.portfolio.ClosedTrade
import com.coinepro.core.portfolio.TradeDirection
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
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
 * ### Three rules that look cosmetic and are not
 *
 * **The CSV starts with a UTF-8 byte-order mark.** Excel on a Persian Windows machine does not
 * detect UTF-8 from content; absent the mark it decodes the file in the system code page and every
 * Persian column heading arrives as mojibake — «نماد» becomes `Ù†Ù…Ø§Ø¯`. This app's readers are on
 * Persian Windows, so the mark is not a nicety, it is the difference between a usable export and
 * one nobody tries twice. It costs three bytes and every other tool ignores it.
 *
 * **Every number is Latin-digit, `Locale.US`, ungrouped.** A cell holding `۱۲۴٫۵` is not a number,
 * it is text, and a spreadsheet answers zero to every formula over the column while showing the
 * reader something that looks exactly right. The device locale here is Persian by default, so any
 * unqualified `String.format` or `toString` on a formatter would produce precisely that. Grouping
 * separators fail the same way and are dropped for the same reason.
 *
 * **Every timestamp gets two columns: the ISO instant and the Jalali date as text.** Neither alone
 * is enough. A spreadsheet sorts, filters and subtracts the ISO one and cannot do any of that with
 * «۱۱ دی ۱۴۰۴»; a Persian trader reconciling the export against their own notes reads the Jalali
 * one and cannot do that with `2026-01-01T09:30:00Z`. Printing one and calling it the date makes
 * the file useless to whichever of the two is doing the reading.
 */
object TradeExport {

    /**
     * The history as comma-separated text, byte-order mark first.
     *
     * Line endings are CRLF, which is what the CSV specification says and what Excel expects; a
     * lone newline is read correctly by everything else and produces one very long row in some
     * older Windows builds.
     *
     * Every field is quoted and inner quotes are doubled. A broker's close reason and a symbol are
     * not free text in theory and repeatedly are in practice, and an unquoted comma inside one
     * shifts every column after it by one without any error being raised anywhere.
     */
    fun toCsv(trades: List<ClosedTrade>, zone: ZoneId = ZoneId.systemDefault()): String {
        val rows = StringBuilder()
        rows.append(BOM)
        rows.append(HEADERS.joinToString(",") { quote(it) })
        for (trade in trades.sortedBy { it.closedAt }) {
            rows.append(LINE_BREAK)
            rows.append(fieldsOf(trade, zone).joinToString(",") { quote(it) })
        }
        rows.append(LINE_BREAK)
        return rows.toString()
    }

    /**
     * The same history as a real spreadsheet, written without a spreadsheet library.
     *
     * See [MinimalWorkbook] for why there is no dependency here and what the hand-written format
     * does and does not cover. The difference from [toCsv] that matters to a reader is the typing:
     * a CSV is text all the way down and a spreadsheet has to guess which columns are numbers,
     * which is exactly the guess that goes wrong on a Persian machine. Here the numeric cells are
     * declared numeric, so a sum over the net-profit column is correct on the first attempt.
     */
    fun toXlsx(trades: List<ClosedTrade>, zone: ZoneId = ZoneId.systemDefault()): ByteArray =
        MinimalWorkbook.build(
            sheetName = SHEET_NAME,
            header = HEADERS,
            rows = trades.sortedBy { it.closedAt }.map { trade -> cellsOf(trade, zone) },
        )

    /**
     * The columns, in order, in Persian.
     *
     * Persian because this file is opened by the reader rather than by a program: it is their trade
     * history, going into their own spreadsheet, and a Latin `net_profit` heading over a Persian
     * interface is a heading they have to translate every time. It is also the reason the
     * byte-order mark above is not optional.
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
     * Public because it is the contract the tests check: the rule "no Persian digit ever reaches a
     * numeric column" needs a definition of which columns those are, and a definition kept in the
     * test file would be a copy that could drift away from the one the export uses.
     */
    val NUMERIC_COLUMNS: Set<Int> = setOf(3, 4, 5, 10, 11, 12, 13, 14, 15, 17)

    /** One trade as text fields, aligned with [HEADERS]. Absent values are the empty string. */
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
     * The same trade as typed spreadsheet cells.
     *
     * The seconds a position was held is a number rather than a duration string, because the one
     * thing a reader does with that column is average it, and «۲ ساعت و ۱۴ دقیقه» averages to
     * nothing at all. The screen renders it as prose; the export renders it as arithmetic.
     */
    internal fun cellsOf(trade: ClosedTrade, zone: ZoneId): List<SheetCell> = listOf(
        SheetCell.Text(trade.id),
        SheetCell.Text(trade.symbol),
        SheetCell.Text(if (trade.direction == TradeDirection.BUY) "خرید" else "فروش"),
        cell(trade.volume),
        cell(trade.entry),
        cell(trade.exit),
        text(isoInstant(trade.openedAt)),
        text(jalaliDate(trade.openedAt, zone)),
        text(isoInstant(trade.closedAt)),
        text(jalaliDate(trade.closedAt, zone)),
        trade.durationSeconds?.let { SheetCell.Number(it.toDouble()) } ?: SheetCell.Blank,
        cell(trade.grossProfit),
        cell(trade.commission),
        cell(trade.swap),
        cell(trade.netProfit),
        cell(trade.pips),
        text(trade.closeReason.orEmpty()),
        cell(trade.balanceAfter),
        SheetCell.Text(if (trade.liquidated) "بله" else "خیر"),
        text(trade.currency.orEmpty()),
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
     */
    internal fun number(value: Double?): String {
        if (value == null || !value.isFinite()) return ""
        return DecimalFormat("0.########", DecimalFormatSymbols(Locale.US)).format(value)
    }

    private fun cell(value: Double?): SheetCell =
        if (value == null || !value.isFinite()) SheetCell.Blank else SheetCell.Number(value)

    private fun text(value: String): SheetCell =
        if (value.isEmpty()) SheetCell.Blank else SheetCell.Text(value)

    private fun quote(field: String): String = "\"" + field.replace("\"", "\"\"") + "\""

    /** U+FEFF. Three bytes in UTF-8, and the reason the file opens correctly at all. */
    private const val BOM = "\uFEFF"

    private const val LINE_BREAK = "\r\n"

    /** Thirty-one characters is the sheet-name limit Excel enforces; this is well inside it. */
    private const val SHEET_NAME = "معاملات"
}
