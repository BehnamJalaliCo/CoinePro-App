package com.coinepro.feature.chart

import com.coinepro.core.chart.Candle
import com.coinepro.core.common.JalaliDate
import com.coinepro.core.marketdata.ChartInterval
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The bars on the chart, as a file the reader owns.
 *
 * TradingView gates chart-data export at Plus. It is free here, and that is a decision rather than
 * an oversight: these are public prices from a named venue, the app is a viewer of a feed and not
 * its owner, and a chart that will not let a reader take the numbers out is a chart asking to be
 * believed on its own word. It is also the cheapest possible answer to «کندل‌سازی» — the
 * accusation that the broker draws its own prices. Somebody who can export the bars and hold them
 * against the exchange's own can settle the question themselves in a minute.
 *
 * ### The three rules, taken from `feature:portfolio`'s trade export
 *
 * They are the same rules and they are here in full rather than by reference, because that module
 * is a sibling and a feature reaching into another feature is how two screens end up owning one
 * file. `TradeExport` states the reasoning at length; the short of it:
 *
 * **The file starts with a UTF-8 byte-order mark.** Excel on a Persian Windows machine does not
 * sniff UTF-8 from content, and without the mark every Persian heading arrives as mojibake. Three
 * bytes, and every other tool ignores them.
 *
 * **Every number is Latin-digit, `Locale.US`, ungrouped.** The device locale here is Persian, so an
 * unqualified format produces «۲۶۴۳٫۱۷» — which a spreadsheet reads as text, sums to zero, and
 * displays looking exactly correct. This is the failure that is invisible until somebody's
 * arithmetic is wrong.
 *
 * **Every timestamp gets two columns.** The ISO instant is what a spreadsheet sorts and subtracts;
 * the Jalali date is what a Persian trader reconciles against their own notes. Printing one and
 * calling it the date makes the file useless to whichever of the two is reading it.
 *
 * ### What is exported is what is on the chart
 *
 * The visible series, so a replay exports the bars the reader can see rather than the future they
 * have deliberately hidden from themselves — exporting past the replay cursor would hand somebody
 * practising their decisions the answer sheet. Volume is exported as an empty cell rather than a
 * zero on a feed that carries none, for the same reason `TradeExport` writes blanks: nought is a
 * value a bar can genuinely have, and writing it where the feed said nothing turns "this venue
 * does not report volume" into "nobody traded".
 */
object ChartExport {

    /**
     * The columns, in order, in Persian.
     *
     * Persian because the file is opened by the reader and not by a program. The two English words
     * inside the parentheses are the format names — `ISO`, and the OHLC letters a chart already
     * prints in its own legend — which are read as symbols rather than as words and are what a
     * spreadsheet's own documentation calls them.
     */
    val HEADERS: List<String> = listOf(
        "زمان (ISO)",
        "تاریخ (شمسی)",
        "ساعت",
        "باز",
        "بیشترین",
        "کمترین",
        "بسته",
        "حجم",
    )

    /**
     * Which columns hold numbers, by index into [HEADERS].
     *
     * Public because it is the contract the test checks: "no Persian digit ever reaches a numeric
     * column" needs a definition of which columns those are, and one kept in the test file would be
     * a copy that could drift away from the one the export uses.
     */
    val NUMERIC_COLUMNS: Set<Int> = setOf(3, 4, 5, 6, 7)

    /**
     * The bars as comma-separated text, byte-order mark first.
     *
     * CRLF line endings, which is what the CSV specification says and what Excel expects. Every
     * field is quoted and inner quotes are doubled — a symbol or a venue name is not free text in
     * theory and repeatedly is in practice, and one unquoted comma shifts every column after it
     * without any error being raised anywhere.
     *
     * The first row is [provenance] — the instrument, the bar length and the venue — rather than
     * the column names. It is prefixed with `#`, which every spreadsheet imports as an ordinary
     * first row and every script skips, and it is what stops an exported file being an anonymous
     * grid of numbers three weeks later.
     */
    fun toCsv(
        bars: List<Candle>,
        symbol: String,
        interval: ChartInterval,
        source: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val rows = StringBuilder()
        rows.append(BOM)
        rows.append(quote(provenance(symbol, interval, source)))
        rows.append(LINE_BREAK)
        rows.append(HEADERS.joinToString(",") { quote(it) })
        for (bar in bars.sortedBy { it.t }) {
            rows.append(LINE_BREAK)
            rows.append(fieldsOf(bar, zone).joinToString(",") { quote(it) })
        }
        rows.append(LINE_BREAK)
        return rows.toString()
    }

    /**
     * The one line that says what these numbers are.
     *
     * Symbol, bar length and venue, in that order, because that is the order somebody would say it
     * out loud. The wire spelling of the interval rather than its Persian label: `H4` is what the
     * reader will type into whatever they are comparing against.
     */
    internal fun provenance(symbol: String, interval: ChartInterval, source: String): String =
        "# " + symbol + " · " + interval.wire + " · " + (source.ifEmpty { "بدون منبع" })

    /** One bar as text fields, aligned with [HEADERS]. Absent values are the empty string. */
    internal fun fieldsOf(bar: Candle, zone: ZoneId): List<String> = listOf(
        isoInstant(bar.t),
        jalaliDate(bar.t, zone),
        clock(bar.t, zone),
        number(bar.o),
        number(bar.h),
        number(bar.l),
        number(bar.c),
        number(bar.v),
    )

    /**
     * A Jalali date as `1404/10/11`, zero-padded and in Latin digits.
     *
     * Deliberately not `JalaliDate.format`, which renders «۱۱ دی ۱۴۰۴» — right for prose on a
     * screen and wrong in a spreadsheet twice over: Persian digits in a column somebody will sort,
     * and a month *name* that sorts into alphabetical rather than calendar order. Zero-padded
     * year/month/day is the one Jalali form whose lexicographic order is its chronological order.
     */
    internal fun jalaliDate(epochSeconds: Long, zone: ZoneId): String {
        val date = JalaliDate.fromInstant(Instant.ofEpochSecond(epochSeconds), zone)
        return "%04d/%02d/%02d".format(Locale.US, date.year, date.month, date.day)
    }

    /**
     * The bar's opening clock time in the reader's own zone, `HH:mm`.
     *
     * Its own column rather than folded into the Jalali date, because the two are used separately:
     * a daily export wants the date and a five-minute export wants the time, and a reader
     * filtering «only the bars that opened at 09:30» can do it on this column and cannot do it on
     * a concatenated string. `Locale.US` on the pattern for the usual reason — the default locale
     * is Persian and would print «۰۹:۳۰» into a numeric-looking column.
     */
    internal fun clock(epochSeconds: Long, zone: ZoneId): String = runCatching {
        Instant.ofEpochSecond(epochSeconds)
            .atZone(zone)
            .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
    }.getOrDefault("")

    /** `2026-01-01T09:30:00Z`. UTC always: both feeds timestamp in it and so should the file. */
    internal fun isoInstant(epochSeconds: Long): String =
        DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(epochSeconds))

    /**
     * A price as the export writes it, or the empty string for an absent or non-finite one.
     *
     * Eight decimal places at most, which covers a token quoted at 0.00000042 without printing
     * gold's two as `2643.17000000`. `DecimalFormat` trims what it does not need.
     */
    internal fun number(value: Double?): String {
        if (value == null || !value.isFinite()) return ""
        return DecimalFormat("0.########", DecimalFormatSymbols(Locale.US)).format(value)
    }

    /**
     * A suggested filename, in Latin digits and hyphens.
     *
     * It will be typed into a search box and sorted in a file list, and Persian digits sort into
     * neither. The symbol and the wire interval are in it so a folder of exports is readable
     * without opening any of them. The reader can rename it.
     */
    fun fileName(symbol: String, interval: ChartInterval, zone: ZoneId = ZoneId.systemDefault()): String {
        val today = JalaliDate.fromInstant(Instant.now(), zone)
        val stamp = "%04d-%02d-%02d".format(Locale.US, today.year, today.month, today.day)
        return sanitise(symbol) + "-" + sanitise(interval.wire) + "-" + stamp + ".csv"
    }

    /**
     * A filename fragment with everything a file system objects to taken out.
     *
     * A symbol is `XAUUSD` almost always and `BTC/USDT` occasionally, and a slash in a filename is
     * a path separator on every system this app runs on. Anything outside letters, digits, a dot
     * and a hyphen becomes a hyphen.
     */
    private fun sanitise(text: String): String =
        text.map { if (it.isLetterOrDigit() || it == '.' || it == '-') it else '-' }.joinToString("")

    private fun quote(field: String): String = "\"" + field.replace("\"", "\"\"") + "\""

    /** U+FEFF. Three bytes in UTF-8, and the reason the file opens correctly on a Persian machine. */
    private const val BOM = "\uFEFF"

    private const val LINE_BREAK = "\r\n"
}
