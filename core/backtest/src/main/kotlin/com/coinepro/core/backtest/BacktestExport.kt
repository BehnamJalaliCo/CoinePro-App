package com.coinepro.core.backtest

import com.coinepro.core.chart.Trade as EngineTrade
import com.coinepro.core.common.JalaliDate
import com.coinepro.core.export.Csv
import com.coinepro.core.export.Numbers
import com.coinepro.core.export.Workbook
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The backtest report as a file the reader owns.
 *
 * TradingView puts its deep backtesting, its advanced metrics and both of its exports behind
 * Essential. All of it is given away here, on purpose: a run of the reader's own rule over public
 * candles is the reader's own work, and a report you cannot take out of the app is a report you
 * cannot check.
 *
 * ### What this file is and is not
 *
 * It is a row shape — the summary block, the trade columns, which of them hold numbers, and how an
 * engine trade becomes text. The writing is `core:export`'s: the byte-order mark Persian Excel
 * needs, the Latin-digit rule, the CRLF and the quoting, and the typed cells that make a workbook
 * column summable. This file used to carry its own copy of all four, with a comment explaining that
 * `feature:portfolio`'s writer could not be reached from here. It can now, and the copy is gone.
 *
 * The XLSX below is the whole reason that mattered: it is the same call the trade history makes,
 * over a different row shape.
 *
 * ### The rule that is this file's own
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
     * Two contracts in one list: the workbook writes exactly these as number cells rather than as
     * text, and the test enforces "no Persian digit ever reaches a numeric column" over them.
     */
    val NUMERIC_TRADE_COLUMNS: List<Int> = listOf(0, 2, 5, 6, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18)

    /**
     * The whole report as comma-separated text: the window, the metrics, then the trades.
     *
     * One file with two sections rather than two files. A reader exporting a backtest wants the
     * evidence and the verdict together — a trade list with no Sharpe beside it invites the reader
     * to recompute one over a window they can no longer see, and the window is the number this
     * whole report exists to state plainly.
     *
     * The sections are separated by an empty row, which every spreadsheet reads as a blank line and
     * no parser mistakes for a header.
     */
    fun toCsv(
        report: BacktestReport,
        symbol: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = Csv.build(
        preamble = summaryRows(report, symbol, zone) + listOf(emptyList()),
        header = TRADE_HEADERS,
        rows = tradeRows(report, zone),
    )

    /**
     * The same report as a real spreadsheet.
     *
     * The difference that matters to a reader is the typing. A CSV is text all the way down and the
     * spreadsheet that opens it has to guess which columns are numbers — the guess that goes wrong
     * on a Persian machine, quietly, with every sum over the column returning zero. Here
     * [NUMERIC_TRADE_COLUMNS] declares them, so the net-profit column adds up on the first attempt.
     *
     * The summary sits above the table exactly as it does in the CSV, so a reader who exports both
     * finds the same file twice rather than two files that have to be reconciled.
     */
    fun toXlsx(
        report: BacktestReport,
        symbol: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ): ByteArray = Workbook.build(
        sheetName = SHEET_NAME,
        preamble = summaryRows(report, symbol, zone),
        header = TRADE_HEADERS,
        rows = tradeRows(report, zone),
        numericColumns = NUMERIC_TRADE_COLUMNS.toSet(),
    )

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
            listOf("هزینه‌ی رفت و برگشت (درصد)", number(report.settings.costFraction * 100)),
            listOf("تعداد کندل", number(report.window.bars.toDouble())),
            listOf("بازه‌ی زمانی", BacktestFormat.dateRange(report.window.firstTime, report.window.lastTime, zone)),
            listOf("تاریخچه‌ی قدیمی‌تر بارگذاری نشده", if (report.window.moreHistoryAvailable) "بله" else "خیر"),
            listOf("کندل در سال", number(metrics.periodsPerYear)),
            listOf("سرمایه‌ی اولیه", number(report.startingEquity)),
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
            listOf("طولانی‌ترین دوره‌ی افت (کندل)", number(metrics.longestDrawdownBars.toDouble())),
            listOf("شارپ سالانه", number(metrics.sharpeRatio)),
            listOf("سورتینو سالانه", number(metrics.sortinoRatio)),
            listOf("امید ریاضی هر معامله", number(metrics.expectancy)),
            listOf("بازده خرید و نگهداری (درصد)", number(metrics.buyAndHoldReturn)),
        )
    }

    /**
     * The trades, with the balance carried down the column.
     *
     * The running equity is computed here rather than read off each trade because the engine's
     * trade knows its own profit and not the account it was one of; a reader checking the last row
     * against the summary's net profit is checking exactly that sum.
     */
    private fun tradeRows(report: BacktestReport, zone: ZoneId): List<List<String>> {
        var running = report.startingEquity
        return report.trades.mapIndexed { index, trade ->
            running += trade.pnl
            tradeFields(index + 1, trade, running, zone)
        }
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
    private fun number(value: Double): String = Numbers.cell(value)

    private fun iso(epochSeconds: Long): String =
        if (epochSeconds <= 0) "" else DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(epochSeconds))

    private fun jalali(epochSeconds: Long, zone: ZoneId): String =
        if (epochSeconds <= 0) "" else JalaliDate.fromInstant(Instant.ofEpochSecond(epochSeconds), zone).format()

    /** Thirty-one characters is the sheet-name limit Excel enforces; this is well inside it. */
    private const val SHEET_NAME = "بک‌تست"
}
