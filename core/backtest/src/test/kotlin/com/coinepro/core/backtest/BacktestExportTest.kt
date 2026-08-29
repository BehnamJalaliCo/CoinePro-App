package com.coinepro.core.backtest

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

/**
 * The export, checked for the three things that make a Persian-language CSV unusable.
 *
 * None of them is visible in a diff and all three are discovered by a reader whose spreadsheet
 * shows mojibake, sums a column to zero, or cannot sort by date.
 */
class BacktestExportTest {

    private val zone: ZoneId = ZoneId.of("Asia/Tehran")

    private val report: BacktestReport = BacktestReports.build(
        series = CandleSeries(
            List(400) { index ->
                // A saw, so the run produces both winners and losers rather than one long hold.
                val close = 100.0 + index % 40
                Candle(
                    t = 1_700_000_000L + index * 3600L,
                    o = close,
                    h = close + 1,
                    l = close - 1,
                    c = close,
                )
            },
        ),
        settings = Backtest.Settings(fast = 5, slow = 20),
        moreHistoryAvailable = true,
    )!!

    @Test
    fun `the file starts with the byte-order mark Persian Excel needs to read it as UTF-8`() {
        val csv = BacktestExport.toCsv(report, "BTCUSDT", zone)
        assertEquals('\uFEFF', csv.first())
        assertTrue("rows end CRLF, which is what the specification says", csv.contains("\r\n"))
    }

    @Test
    fun `no Persian digit ever reaches a numeric column`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("fa-IR"))
        try {
            val rows = rows(BacktestExport.toCsv(report, "BTCUSDT", zone))
            val header = rows.indexOfFirst { it.firstOrNull() == BacktestExport.TRADE_HEADERS.first() }
            assertTrue("the trade table must be in the file", header >= 0)

            rows.drop(header + 1).filter { it.size == BacktestExport.TRADE_HEADERS.size }.forEach { row ->
                BacktestExport.NUMERIC_TRADE_COLUMNS.forEach { column ->
                    assertFalse(
                        "column $column holds ${row[column]}, which a spreadsheet reads as text",
                        row[column].any { it in '۰'..'۹' || it in '٠'..'٩' },
                    )
                }
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `every timestamp is written twice, as an instant and as a Jalali date`() {
        val rows = rows(BacktestExport.toCsv(report, "BTCUSDT", zone))
        val header = rows.first { it.firstOrNull() == BacktestExport.TRADE_HEADERS.first() }
        assertEquals(BacktestExport.TRADE_HEADERS, header)

        val first = rows.dropWhile { it != header }.drop(1).first()
        // A spreadsheet sorts and subtracts the ISO column; the reader reads the Jalali one.
        assertTrue(first[3].endsWith("Z"))
        assertTrue(first[4].any { it in '۰'..'۹' })
    }

    @Test
    fun `a metric that is not a number leaves the cell empty rather than writing an infinity`() {
        val winnersOnly = report.copy(
            trades = report.trades.filter { it.isWin },
            all = report.all.copy(profitFactor = Double.POSITIVE_INFINITY),
        )
        val rows = rows(BacktestExport.toCsv(winnersOnly, "BTCUSDT", zone))
        val row = rows.first { it.firstOrNull() == "ضریب سود" }
        assertEquals("", row[1])
    }

    @Test
    fun `the summary says how much history the run covered before it says anything else`() {
        val rows = rows(BacktestExport.toCsv(report, "BTCUSDT", zone))
        val bars = rows.indexOfFirst { it.firstOrNull() == "تعداد کندل" }
        val sharpe = rows.indexOfFirst { it.firstOrNull() == "شارپ سالانه" }
        assertTrue("the window must be stated before the ratio it qualifies", bars in 0 until sharpe)
        assertEquals("400", rows[bars][1])
    }

    /**
     * A parser for exactly the dialect written above: every field quoted, inner quotes doubled,
     * CRLF between rows. Deliberately not a general CSV reader — a lenient one would accept a file
     * the export should never produce and the assertions would stop meaning anything.
     */
    private fun rows(csv: String): List<List<String>> =
        csv.removePrefix("\uFEFF").split("\r\n").map { line ->
            if (line.isEmpty()) {
                emptyList()
            } else {
                line.split("\",\"").map { it.removePrefix("\"").removeSuffix("\"").replace("\"\"", "\"") }
            }
        }
}
