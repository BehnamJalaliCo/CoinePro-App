package com.coinepro.core.backtest

import com.coinepro.core.common.JalaliDate
import com.coinepro.core.chart.Trade as EngineTrade
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The backtest report as a file the reader owns.
 *
 * TradingView puts its deep backtesting, its advanced metrics and both of its exports behind
 * Essential. All of it is given away here, on purpose: a run of the reader's own rule over public
 * candles is the reader's own work, and a report you cannot take out of the app is a report you
 * cannot check.
 *
 * ### Why this is not `feature:portfolio`'s `TradeExport`
 *
 * It should be. `TradeExport` already writes both formats and already gets the three details below
 * right. It cannot be reached from here and the reasons are structural rather than stylistic:
 * it lives in `feature:portfolio`, so using it would need a feature-to-feature dependency the
 * module graph does not have; its `MinimalWorkbook` — the whole XLSX writer — is `internal` to that
 * module; and its rows are `core:portfolio`'s `ClosedTrade`, a broker fill carrying a balance, a
 * swap, a liquidation flag and a close reason, none of which a backtest trade has or could
 * honestly invent. The fix is to move both files down into a module both sides can depend on;
 * until that happens this writes CSV and there is no XLSX here. See the report's WIRING NEEDED.
 *
 * ### The three rules copied deliberately, because they are the ones that break exports
 *
 * **A UTF-8 byte-order mark first.** Excel on a Persian Windows machine does not detect UTF-8 from
 * content; without the mark it decodes in the system code page and every Persian heading arrives as
 * mojibake. It costs three bytes and every other tool ignores it.
 *
 * **Every number Latin-digit, `Locale.US`, ungrouped.** A cell holding «۱۲٫۵» is text, and a
 * spreadsheet answers zero to every formula over the column while showing the reader something that
 * looks exactly right. The device locale here is Persian, so any unqualified format call produces
 * precisely that. Thousands separators fail the same way and are dropped for the same reason.
 *
 * **Two columns per timestamp: the ISO instant and the Jalali date.** A spreadsheet sorts and
 * subtracts the first and can do neither with the second; a Persian trader reconciling against
 * their own notes reads the second and cannot read the first. Printing one and calling it the date
 * makes the file useless to whichever of the two is doing the reading.
 */
object BacktestExport {

    /**
     * The trade table's columns, in order, in Persian.
     *
     * Persian because this file is opened by the reader, not by a program. Public because it is the
     * contract the tests check — a definition kept in the test file would be a copy free to drift
     * away from the one the export writes.
     */
    val TRADE_HEADERS: List<String> = listOf(
        "شماره",
        "جهت",
        "کندل ورود",
        "زمان ورود (ISO)",
        "تاریخ ورود (شمسی)",
        "قیمت ورود",
        "کندل خروج",
        "زمان خروج (ISO)",
        "تاریخ خروج (شمسی)",
        "قیمت خروج",
        "حجم",
        "کارمزد",
        "سود ناخالص",
        "سود خالص",
        "درصد سود",
        "کندل نگهداری",
        "بیشترین سود میان‌راه",
        "بیشترین زیان میان‌راه",
        "موجودی پس از معامله",
    )

    /**
     * Which trade columns hold numbers, by index into [TRADE_HEADERS].
     *
     * The rule the test enforces is "no Persian digit ever reaches a numeric column", and that rule
     * needs a definition of which columns those are.
     */
    val NUMERIC_TRADE_COLUMNS: List<Int> = listOf(0, 2, 5, 6, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)

    /**
     * The whole report: the window, the metrics, then the trades.
     *
     * One file with two sections rather than two files. A reader exporting a backtest wants the
     * evidence and the verdict together — a trade list with no Sharpe beside it invites the reader
     * to recompute one over a window they can no longer see, and the window is the number this
     * whole report exists to state plainly.
     *
     * The sections are separated by an empty line, which every spreadsheet reads as a blank row and
     * no parser mistakes for a header.
     */
    fun toCsv(
        report: BacktestReport,
        symbol: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val out = StringBuilder()
        out.append(BOM)
        summaryRows(report, symbol, zone).forEach { row ->
            out.append(row.joinToString(",") { quote(it) })
            out.append(LINE_BREAK)
        }
        out.append(LINE_BREAK)
        out.append(TRADE_HEADERS.joinToString(",") { quote(it) })
        var running = report.startingEquity
        report.trades.forEachIndexed { index, trade ->
            running += trade.pnl
            out.append(LINE_BREAK)
            out.append(tradeFields(index + 1, trade, running, zone).joinToString(",") { quote(it) })
        }
        out.append(LINE_BREAK)
        return out.toString()
    }

    /**
     * The header block: what was run, over what, and what it produced.
     *
     * The window comes first and before any metric, which is the same order the screen shows them
     * in and for the same reason. A Sharpe over three hundred bars is not a finding, and a file
     * that leads with the Sharpe invites it to be quoted without the bar count that qualifies it.
     */
    private fun summaryRows(
        report: BacktestReport,
        symbol: String,
        zone: ZoneId,
    ): List<List<String>> {
        val metrics = report.all
        return listOf(
            listOf("گزارش بک‌تست", symbol),
            listOf("راهبرد", strategyName(report.settings.strategy)),
            listOf("جهت", if (report.allowShorts) "خرید و فروش" else "فقط خرید"),
            listOf("هزینهٔ رفت و برگشت (درصد)", number(report.settings.costFraction * 100)),
            listOf("تعداد کندل", number(report.window.bars.toDouble())),
            listOf("بازهٔ زمانی", BacktestFormat.dateRange(report.window.firstTime, report.window.lastTime, zone)),
            listOf("تاریخچهٔ قدیمی‌تر بارگذاری نشده", if (report.window.moreHistoryAvailable) "بله" else "خیر"),
            listOf("کندل در سال", number(metrics.periodsPerYear)),
            listOf("سرمایهٔ اولیه", number(report.startingEquity)),
            listOf("سود خالص", number(metrics.netProfit)),
            listOf("سود خالص (درصد)", number(metrics.netProfitPercent)),
            listOf("سود ناخالص", number(metrics.grossProfit)),
            listOf("زیان ناخالص", number(metrics.grossLoss)),
            listOf("مجموع کارمزد", number(metrics.totalFees)),
            listOf("ضریب سود", number(metrics.profitFactor)),
            listOf("تعداد معامله", number(metrics.totalTrades.toDouble())),
            listOf("معاملات برنده", number(metrics.winningTrades.toDouble())),
            listOf("معاملات بازنده", number(metrics.losingTrades.toDouble())),
            listOf("درصد برد", number(metrics.percentProfitable)),
            listOf("میانگین سود هر معامله", number(metrics.averagePnl)),
            listOf("میانگین برد", number(metrics.averageWin)),
            listOf("میانگین باخت", number(metrics.averageLoss)),
            listOf("نسبت برد به باخت", number(metrics.winLossRatio)),
            listOf("بزرگ‌ترین برد", number(metrics.largestWin)),
            listOf("بزرگ‌ترین باخت", number(metrics.largestLoss)),
            listOf("میانگین کندل در معامله", number(metrics.averageBarsInTrade)),
            listOf("میانگین کندل در برنده‌ها", number(metrics.averageBarsInWinners)),
            listOf("میانگین کندل در بازنده‌ها", number(metrics.averageBarsInLosers)),
            listOf("بیشترین رشد سرمایه", number(metrics.maxEquityRunUp)),
            listOf("بیشترین رشد سرمایه (درصد)", number(metrics.maxEquityRunUpPercent)),
            listOf("بیشترین افت سرمایه", number(metrics.maxEquityDrawdown)),
            listOf("بیشترین افت سرمایه (درصد)", number(metrics.maxEquityDrawdownPercent)),
            listOf("طولانی‌ترین دورهٔ افت (کندل)", number(metrics.longestDrawdownBars.toDouble())),
            listOf("شارپ سالانه", number(metrics.sharpeRatio)),
            listOf("سورتینو سالانه", number(metrics.sortinoRatio)),
            listOf("امید ریاضی هر معامله", number(metrics.expectancy)),
            listOf("بازده خرید و نگهداری (درصد)", number(metrics.buyAndHoldReturn)),
        )
    }

    private fun tradeFields(
        ordinal: Int,
        trade: EngineTrade,
        equityAfter: Double,
        zone: ZoneId,
    ): List<String> = listOf(
        number(ordinal.toDouble()),
        if (trade.isLong) "خرید" else "فروش",
        number(trade.entryIndex.toDouble()),
        iso(trade.entryTime),
        jalali(trade.entryTime, zone),
        number(trade.entryPrice),
        number(trade.exitIndex.toDouble()),
        iso(trade.exitTime),
        jalali(trade.exitTime, zone),
        number(trade.exitPrice),
        number(trade.size),
        number(trade.fee),
        number(trade.grossPnl),
        number(trade.pnl),
        number(trade.pnlPercent),
        number(trade.barsHeld.toDouble()),
        number(trade.runUp),
        number(trade.drawdown),
        number(equityAfter),
    )

    /** The rule's Persian name, so the file says what was run rather than an enum's spelling. */
    private fun strategyName(strategy: Backtest.Strategy): String = when (strategy) {
        Backtest.Strategy.MA_CROSS -> "تقاطع میانگین"
        Backtest.Strategy.RSI_REVERSION -> "بازگشت RSI"
        Backtest.Strategy.BREAKOUT -> "شکست کانال"
    }

    /**
     * One numeric cell.
     *
     * Empty for a value that is not a number — an infinite profit factor is the case that actually
     * occurs — because a spreadsheet sums an empty cell as nothing and would sum the text "∞" as
     * zero while displaying it, which is the same wrong answer with a disguise. The screen shows a
     * dash for the same value; a file has no room for a typographic mark inside a numeric column.
     */
    private fun number(value: Double): String =
        if (!value.isFinite()) "" else DecimalFormat("0.########", DecimalFormatSymbols(Locale.US)).format(value)

    private fun iso(epochSeconds: Long): String =
        if (epochSeconds <= 0) "" else DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(epochSeconds))

    private fun jalali(epochSeconds: Long, zone: ZoneId): String =
        if (epochSeconds <= 0) "" else JalaliDate.fromInstant(Instant.ofEpochSecond(epochSeconds), zone).format()

    /**
     * Every field quoted, inner quotes doubled.
     *
     * A Persian heading contains no comma today. A symbol from a feed and a rule name typed by
     * somebody later both can, and an unquoted comma shifts every column after it by one without
     * any error being raised anywhere in the chain.
     */
    private fun quote(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    /** The byte-order mark Excel needs to read this as UTF-8 on a Persian Windows machine. */
    private const val BOM = "\uFEFF"

    /** CRLF, which is what the CSV specification says and what Excel expects. */
    private const val LINE_BREAK = "\r\n"
}
