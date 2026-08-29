package com.coinepro.feature.chart

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
// ── The name collision, and which side of it this file is on ────────────────────────────────────
//
// There are two `Backtest` objects. `com.coinepro.core.backtest.Backtest` — imported plainly below
// — is the three named rules and the `Settings` these chips set. `com.coinepro.core.chart.Backtest`
// is the real engine that runs them: twenty-five metrics, annualised Sharpe and Sortino, run-up and
// drawdown on the marked equity curve. `Trade`, `Strategy` and `Signal` collide the same way.
//
// This sheet used to use the first one's own five-metric summariser, and the engine — seven hundred
// and seventy-six lines of it — had no caller but its own test. It is on the engine now. The
// convention across both modules: the engine's types carry an `Engine` prefix and this module's do
// not, so a reader can tell at the call site which of the two they are looking at.
import com.coinepro.core.backtest.Backtest
import com.coinepro.core.backtest.BacktestExport
import com.coinepro.core.backtest.BacktestFormat
import com.coinepro.core.backtest.BacktestReport
import com.coinepro.core.backtest.BacktestReports
import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.Trade as EngineTrade
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.marketdata.CHART_TIME_ZONE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The strategy report: five tabs over the bars the chart has.
 *
 * The tabs are TradingView's own — Overview, Performance, Trades analysis, Risk ratios, List of
 * trades — because after twenty years of people reading backtests they are the right five, and a
 * trader arriving from that terminal already knows which one holds the number they came for.
 * Everything it charges its Essential tier for is here for nothing: the advanced metrics, the deep
 * run, the export.
 *
 * ### The window, first and in plain sight
 *
 * The single most dangerous thing a report like this can do is state a Sharpe ratio to two decimal
 * places over three hundred candles. Nothing about the figure looks provisional; it reads as a
 * verdict on the idea. So the bar count and the date range are the first card on the sheet, above
 * every metric, and when the feed still has older bars that the run did not cover the sheet says so
 * and offers to fetch them. The honest number matters more than the big one.
 *
 * ### Nothing divides by zero on screen
 *
 * A run with no losing trade has an infinite profit factor, and every way of printing an infinity
 * is a lie — see `BacktestFormat`. Every figure below goes through it, so the impossible metrics
 * render as a dash and send the reader to the trade count beside them, which is the number that
 * settles it.
 */
@Composable
internal fun BacktestSheetBody(
    bars: List<Candle>,
    symbol: String,
    /**
     * The root column's modifier, and the reason it is a parameter rather than a `verticalScroll`
     * fixed inside this file.
     *
     * This body has two hosts with opposite needs. `ChartStudioScreen` renders it inside a
     * `LazyColumn` item, where a vertically scrollable child is measured with an infinite maximum
     * height and throws — the crash reads "Vertically scrollable component was measured with an
     * infinity maximum height constraints" and does not appear until somebody opens the section. A
     * bottom sheet is the mirror image: nothing above it scrolls, so a report this tall is clipped
     * at the bottom of the screen with no way to reach the rest.
     *
     * So the decision belongs to whoever is placing it. A list host passes nothing; a sheet host
     * passes `Modifier.verticalScroll(rememberScrollState())`.
     */
    modifier: Modifier = Modifier,
    /**
     * Whether the feed still holds older bars than the chart has paged in.
     *
     * The chart pages history in as the reader pans, so `bars` is usually a window rather than the
     * instrument's past. Passing this in is what lets the report say which of the two it ran over.
     */
    hasMoreHistory: Boolean = false,
    /** Whether a page of older history is in flight, so the button can say so. */
    loadingHistory: Boolean = false,
    /**
     * Fetch another page of older bars, or null on a screen that cannot.
     *
     * Null is not a failure — a static chart has nothing to page — and the sheet then states the
     * window plainly instead of offering to widen it.
     */
    onLoadMoreHistory: (() -> Unit)? = null,
) {
    var strategy by rememberSaveable { mutableStateOf(Backtest.Strategy.MA_CROSS) }
    var costBasisPoints by rememberSaveable { mutableStateOf(5) }
    var allowShorts by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(ReportTab.OVERVIEW) }

    val settings = Backtest.Settings(
        strategy = strategy,
        costFraction = costBasisPoints / 10_000.0,
    )
    val report = remember(bars, settings, allowShorts, hasMoreHistory) {
        // One pass over the bars and a handful of indicator arrays. It is cheap enough to sit in a
        // `remember` rather than a coroutine, and keeping it here means the report can never be one
        // recomposition behind the chips that produced it.
        BacktestReports.build(
            series = CandleSeries(bars),
            settings = settings,
            allowShorts = allowShorts,
            moreHistoryAvailable = hasMoreHistory,
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Backtest.Strategy.entries.forEach { option ->
                Chip(option.label(), option == strategy) { strategy = option }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            listOf(0, 2, 5, 10).forEach { points ->
                Chip(
                    // Latin, like every market figure: a reader compares this against an exchange's
                    // published fee schedule.
                    label = BidiText.isolateLtr("$points bp"),
                    selected = points == costBasisPoints,
                ) { costBasisPoints = points }
            }
            Chip("با فروش", allowShorts) { allowShorts = !allowShorts }
        }

        if (allowShorts) {
            Text(
                text = "فروش استقراضی تقریبی است: کارمزد دو طرف حساب می‌شود ولی بهرهٔ قرض و نرخ فاندینگ در سری کندل وجود ندارد.",
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.Warning,
                fontWeight = FontWeight.Normal,
            )
        }

        WindowCard(
            report = report,
            barsLoaded = bars.size,
            hasMoreHistory = hasMoreHistory,
            loadingHistory = loadingHistory,
            onLoadMoreHistory = onLoadMoreHistory,
        )

        if (report == null) {
            Text(
                text = "برای بک‌تست دست‌کم ${Backtest.MINIMUM_BARS.toPersianDigits()} کندل لازم است. کمی به عقب اسکرول کنید تا بارگذاری شود.",
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextMuted,
            )
            return@Column
        }

        CoineProSegmentedControl(
            options = ReportTab.entries.map { it to it.label },
            selected = tab,
            onSelect = { tab = it },
        )

        when (tab) {
            ReportTab.OVERVIEW -> OverviewTab(report)
            ReportTab.PERFORMANCE -> PerformanceTab(report)
            ReportTab.TRADES -> TradesTab(report)
            ReportTab.RISK -> RiskTab(report)
            ReportTab.LIST -> TradeListTab(report)
        }

        ExportCard(report, symbol)

        Text(
            text = "ورود در باز شدنِ کندلِ بعد از سیگنال حساب می‌شود، نه در بستهٔ همان کندل. موقعیتِ باز در آخرین کندل بسته می‌شود تا منحنی سرمایه روی عددی تمام شود که واقعاً قابل برداشت بود. نتیجهٔ گذشته تضمین آینده نیست.",
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
        )
    }
}

/**
 * The five tabs, in the order a report is read.
 *
 * Short labels, because five segments across a phone is already tight and a truncated tab name is
 * worse than a terse one. The full sense of each is carried by what is inside it.
 */
private enum class ReportTab(val label: String) {
    OVERVIEW("کلی"),
    PERFORMANCE("عملکرد"),
    TRADES("معاملات"),
    RISK("ریسک"),
    LIST("فهرست"),
}

/**
 * How much history this run covered, above everything it concluded.
 *
 * The bar count is a market figure and stays Latin — it is compared against another terminal's run
 * and against the reader's own memory of how long the instrument has traded. The date range is
 * Jalali prose, because that is the calendar the reader remembers events in.
 */
@Composable
private fun WindowCard(
    report: BacktestReport?,
    barsLoaded: Int,
    hasMoreHistory: Boolean,
    loadingHistory: Boolean,
    onLoadMoreHistory: (() -> Unit)?,
) {
    val counted = report?.window?.bars ?: barsLoaded
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            MetricRow("کندل‌های این اجرا", BacktestFormat.count(counted))
            val range = report?.let {
                BacktestFormat.dateRange(it.window.firstTime, it.window.lastTime, CHART_TIME_ZONE)
            }
            if (!range.isNullOrEmpty()) {
                MetricRow("بازهٔ زمانی", range)
            }
            if (hasMoreHistory) {
                Text(
                    text = "این اجرا فقط روی کندل‌های بارگذاری‌شده است، نه کل تاریخچه. تا وقتی بازه کوتاه است، شارپ و ضریب سود بیشتر شانس‌اند تا نتیجه.",
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.Warning,
                    fontWeight = FontWeight.Normal,
                )
                if (onLoadMoreHistory != null) {
                    CoineProSecondaryButton(
                        text = if (loadingHistory) "در حال بارگذاری تاریخچه" else "بارگذاری تاریخچهٔ بیشتر",
                        onClick = { if (!loadingHistory) onLoadMoreHistory() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else if (report != null) {
                Text(
                    text = "کل تاریخچهٔ بارگذاری‌شدهٔ این نماد در این اجرا هست.",
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}

/** Overview: the curve, the baseline it is measured against, and the six figures that qualify it. */
@Composable
private fun OverviewTab(report: BacktestReport) {
    val metrics = report.all
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
        EquityChart(report)
        CoineProCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                MetricRow(
                    "سود خالص",
                    BacktestFormat.money(metrics.netProfit, signed = true),
                    resultTint(metrics.netProfit),
                )
                MetricRow(
                    "سود خالص (درصد)",
                    BacktestFormat.signedPercent(metrics.netProfitPercent),
                    resultTint(metrics.netProfit),
                )
                MetricRow(
                    // The number that decides position size, and the one a flattering backtest
                    // leaves out: peak to trough on the account, not start to end.
                    "بیشترین افت سرمایه",
                    BacktestFormat.percent(metrics.maxEquityDrawdownPercent, 1),
                    CoineProColors.Sell,
                )
                MetricRow(
                    "بیشترین رشد سرمایه",
                    BacktestFormat.percent(metrics.maxEquityRunUpPercent, 1),
                    CoineProColors.Buy,
                )
                MetricRow("تعداد معامله", BacktestFormat.count(metrics.totalTrades))
                MetricRow("درصد برد", BacktestFormat.percent(metrics.percentProfitable, 1))
                MetricRow("ضریب سود", BacktestFormat.ratio(metrics.profitFactor))
                MetricRow(
                    "خرید و نگهداری",
                    BacktestFormat.signedPercent(metrics.buyAndHoldReturn),
                    resultTint(metrics.buyAndHoldReturn),
                )
            }
        }
        SampleWarning(metrics.totalTrades)
    }
}

/**
 * Performance: the same metrics for everything, for the longs alone and for the shorts alone.
 *
 * Not a filter over one summary — a subset of trades has its own equity curve, so its drawdown and
 * its ratios are computed from that curve. The longs of a rule that only works in one direction are
 * the whole rule, and the shorts are the evidence.
 */
@Composable
private fun PerformanceTab(report: BacktestReport) {
    var side by rememberSaveable { mutableStateOf(Side.ALL) }
    val metrics = when (side) {
        Side.ALL -> report.all
        Side.LONG -> report.longs
        Side.SHORT -> report.shorts
    }

    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
        CoineProSegmentedControl(
            options = Side.entries.map { it to it.label },
            selected = side,
            onSelect = { side = it },
        )
        if (metrics.totalTrades == 0) {
            Text(
                text = if (side == Side.SHORT && !report.allowShorts) {
                    "این اجرا فقط خرید بود. برای دیدن فروش، «با فروش» را روشن کنید."
                } else {
                    "در این جهت معامله‌ای بسته نشد."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextMuted,
            )
            return@Column
        }
        CoineProCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                MetricRow(
                    "سود خالص",
                    BacktestFormat.money(metrics.netProfit, signed = true),
                    resultTint(metrics.netProfit),
                )
                MetricRow("سود ناخالص", BacktestFormat.money(metrics.grossProfit))
                MetricRow("زیان ناخالص", BacktestFormat.money(metrics.grossLoss))
                MetricRow("مجموع کارمزد", BacktestFormat.money(metrics.totalFees))
                MetricRow("تعداد معامله", BacktestFormat.count(metrics.totalTrades))
                MetricRow("برنده", BacktestFormat.count(metrics.winningTrades))
                MetricRow("بازنده", BacktestFormat.count(metrics.losingTrades))
                MetricRow("درصد برد", BacktestFormat.percent(metrics.percentProfitable, 1))
                MetricRow("ضریب سود", BacktestFormat.ratio(metrics.profitFactor))
                MetricRow(
                    "بیشترین افت سرمایه",
                    BacktestFormat.percent(metrics.maxEquityDrawdownPercent, 1),
                    CoineProColors.Sell,
                )
            }
        }
        MutedNote(
            "کارمزد را کنار سود خالص بخوانید. وقتی کارمزد از سود بزرگ‌تر است، این قاعده هرچقدر هم درصد بردش بالا باشد، فقط برای صرافی سود می‌سازد.",
        )
    }
}

/**
 * Trades analysis: what one trade was worth, and how evenly.
 *
 * The run-up rows are the reason this tab is not just an average. A rule whose losers were up
 * three hundred at some point and closed at minus eighty does not have a rule problem, it has a
 * stop problem — and there is no way to see that from P&L, which is why run-up was measured here
 * and shown nowhere for so long.
 */
@Composable
private fun TradesTab(report: BacktestReport) {
    val metrics = report.all
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
        CoineProCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                MetricRow(
                    "میانگین هر معامله",
                    BacktestFormat.money(metrics.averagePnl, signed = true),
                    resultTint(metrics.averagePnl),
                )
                MetricRow("میانگین برد", BacktestFormat.money(metrics.averageWin), CoineProColors.Buy)
                MetricRow("میانگین باخت", BacktestFormat.money(metrics.averageLoss), CoineProColors.Sell)
                MetricRow("نسبت برد به باخت", BacktestFormat.ratio(metrics.winLossRatio))
                MetricRow("بزرگ‌ترین برد", BacktestFormat.money(metrics.largestWin), CoineProColors.Buy)
                MetricRow("بزرگ‌ترین باخت", BacktestFormat.money(metrics.largestLoss), CoineProColors.Sell)
                MetricRow(
                    "سهم بزرگ‌ترین برد از سود",
                    BacktestFormat.percent(BacktestReports.bestTradeShare(metrics) * 100, 1),
                )
                MetricRow("پراکندگی سود", BacktestFormat.money(BacktestReports.pnlDispersion(report.trades)))
                MetricRow("میانگین کندل در معامله", BacktestFormat.bars(metrics.averageBarsInTrade))
                MetricRow("میانگین کندل در برنده‌ها", BacktestFormat.bars(metrics.averageBarsInWinners))
                MetricRow("میانگین کندل در بازنده‌ها", BacktestFormat.bars(metrics.averageBarsInLosers))
            }
        }
        CoineProCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                MetricRow(
                    "بیشترین سود میان‌راهِ یک معامله",
                    BacktestFormat.money(report.bestTradeRunUp),
                    CoineProColors.Buy,
                )
                MetricRow(
                    "میانگین سود میان‌راهِ بازنده‌ها",
                    BacktestFormat.money(report.averageLoserRunUp),
                    CoineProColors.Buy,
                )
                MutedNote(
                    "سود میان‌راه یعنی معامله تا کجا به نفع شما رفت پیش از آنکه بسته شود. اگر بازنده‌ها سود میان‌راه بزرگی داشته‌اند، ایراد از قاعدهٔ ورود نیست، از حد ضرر یا حد سود است.",
                )
            }
        }
        SampleWarning(metrics.totalTrades)
    }
}

/**
 * Risk ratios, and the two sentences that stop them being quoted alone.
 *
 * Sharpe and Sortino are annualised here — see `core:chart`'s `Backtest.sharpe`. A ratio taken from
 * raw per-bar returns is roughly twenty times too small on a daily series and is quoted anyway,
 * because it still looks like a plausible number. The bars-per-year figure is on the card so a
 * reader can check the run was scaled by the number they expected.
 */
@Composable
private fun RiskTab(report: BacktestReport) {
    val metrics = report.all
    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
        CoineProCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                MetricRow("شارپ (سالانه)", BacktestFormat.ratio(metrics.sharpeRatio))
                MetricRow("سورتینو (سالانه)", BacktestFormat.ratio(metrics.sortinoRatio))
                MetricRow("ضریب سود", BacktestFormat.ratio(metrics.profitFactor))
                MetricRow(
                    "امید ریاضی هر معامله",
                    BacktestFormat.money(metrics.expectancy, signed = true),
                    resultTint(metrics.expectancy),
                )
                MetricRow(
                    "بیشترین افت سرمایه",
                    BacktestFormat.money(metrics.maxEquityDrawdown),
                    CoineProColors.Sell,
                )
                MetricRow(
                    "بیشترین افت سرمایه (درصد)",
                    BacktestFormat.percent(metrics.maxEquityDrawdownPercent, 1),
                    CoineProColors.Sell,
                )
                MetricRow("طولانی‌ترین دورهٔ افت", "${BacktestFormat.count(metrics.longestDrawdownBars)} کندل")
                MetricRow("کندل در سال", BacktestFormat.count(metrics.periodsPerYear.toInt()))
            }
        }
        MutedNote(
            "شارپ نوسان مثبت را هم مثل نوسان منفی جریمه می‌کند، پس قاعده‌ای که چند برد بزرگ دارد بدتر از یک قاعدهٔ کم‌جان امتیاز می‌گیرد. سورتینو فقط سمت زیان را می‌سنجد و وقتی کندل‌های زیان‌ده کم باشند بزرگ و بی‌معنا می‌شود — دقیقاً همان‌جا که بیشتر نقل می‌شود.",
        )
        SampleWarning(metrics.totalTrades)
    }
}

/**
 * The list of trades, run-up included.
 *
 * Newest first, because the reader's question is nearly always "what did it just do". The rows are
 * capped: a thousand-trade run inside a bottom sheet is a scroll nobody finishes, and the export
 * beneath it carries every one of them. The cap is stated in prose with the count, so it is never a
 * silent truncation.
 */
@Composable
private fun TradeListTab(report: BacktestReport) {
    var side by rememberSaveable { mutableStateOf(Side.ALL) }
    val listed = when (side) {
        Side.ALL -> report.trades
        Side.LONG -> report.longTrades
        Side.SHORT -> report.shortTrades
    }
    val shown = listed.asReversed().take(MAX_LISTED_TRADES)

    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
        CoineProSegmentedControl(
            options = Side.entries.map { it to it.label },
            selected = side,
            onSelect = { side = it },
        )
        if (listed.isEmpty()) {
            Text(
                text = if (side == Side.ALL) {
                    "این قاعده در این بازه هیچ معامله‌ای نبست."
                } else {
                    "در این جهت معامله‌ای بسته نشد."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextMuted,
            )
            return@Column
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Column {
                TradeHeaderRow()
                shown.forEach { trade -> TradeRow(trade) }
            }
        }
        if (listed.size > shown.size) {
            Text(
                text = "${shown.size.toPersianDigits()} معاملهٔ آخر از ${listed.size.toPersianDigits()} معامله. برای همه، خروجی CSV بگیرید.",
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun TradeHeaderRow() {
    Row(modifier = Modifier.padding(vertical = CoineProSpacing.Half)) {
        Cell("جهت", DIRECTION_WIDTH, header = true)
        Cell("ورود", PRICE_WIDTH, header = true)
        Cell("خروج", PRICE_WIDTH, header = true)
        Cell("سود", PNL_WIDTH, header = true)
        Cell("درصد", PERCENT_WIDTH, header = true)
        Cell("میان‌راه", PNL_WIDTH, header = true)
        Cell("کندل", BARS_WIDTH, header = true)
    }
}

/**
 * One trade.
 *
 * Run-up sits beside P&L rather than in a detail view, because the two are only meaningful
 * together: a loser that ran two hundred in your favour first and a loser that never did are the
 * same row without it, and they are completely different mistakes.
 */
@Composable
private fun TradeRow(trade: EngineTrade) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Cell(
            text = if (trade.isLong) "خرید" else "فروش",
            width = DIRECTION_WIDTH,
            tint = if (trade.isLong) CoineProColors.Buy else CoineProColors.Sell,
        )
        Cell(BacktestFormat.money(trade.entryPrice), PRICE_WIDTH)
        Cell(BacktestFormat.money(trade.exitPrice), PRICE_WIDTH)
        Cell(
            text = BacktestFormat.money(trade.pnl, signed = true),
            width = PNL_WIDTH,
            tint = resultTint(trade.pnl),
        )
        Cell(
            text = BacktestFormat.signedPercent(trade.pnlPercent),
            width = PERCENT_WIDTH,
            tint = resultTint(trade.pnl),
        )
        Cell(BacktestFormat.money(trade.runUp), PNL_WIDTH, tint = CoineProColors.Buy)
        Cell(BacktestFormat.count(trade.barsHeld), BARS_WIDTH)
    }
}

@Composable
private fun Cell(text: String, width: Dp, header: Boolean = false, tint: Color? = null) {
    Text(
        text = text,
        modifier = Modifier.width(width).padding(horizontal = 2.dp),
        style = if (header) MaterialTheme.typography.labelSmall else CoineProTextStyles.RowFigure,
        color = tint ?: if (header) CoineProColors.TextMuted else CoineProColors.TextSecondary,
        fontWeight = if (header) FontWeight.Normal else FontWeight.Bold,
        maxLines = 1,
        // Right, never End: a market figure is a left-to-right run, and mirroring its alignment
        // with the Persian paragraph is what puts a column of numbers out of line with itself.
        textAlign = TextAlign.Right,
    )
}

/**
 * The equity curve, its buy-and-hold baseline, and the two excursions that explain it.
 *
 * Four things on one canvas and no axis, because the question it answers at a glance is not "what
 * number" — every number is in the card below — it is "what shape". The two highlighted spans are
 * the point of the drawing: the green one is the best run the account had and the red one is the
 * worst fall it sat through, and a reader who can see the second is a reader who can size for it.
 *
 * The buy-and-hold line is the same money in the same instrument held throughout, so a strategy
 * that finishes under it made less than doing nothing — which is a result that no arrangement of
 * percentages makes as obvious as two lines crossing.
 */
@Composable
private fun EquityChart(report: BacktestReport) {
    val curve = report.equityCurve
    val hold = report.buyAndHoldCurve
    if (curve.size < 2) return

    // Resolved out here: a DrawScope is not a composable scope and the palette is a CompositionLocal.
    val rising = curve.last() >= curve.first()
    val ink = if (rising) CoineProColors.Buy else CoineProColors.Sell
    val holdInk = CoineProColors.TextMuted
    val baselineInk = CoineProColors.Border
    val runUpInk = CoineProColors.Buy
    val drawdownInk = CoineProColors.Sell
    val excursion = report.excursion

    var low = report.startingEquity
    var high = report.startingEquity
    curve.forEach { point ->
        if (point < low) low = point
        if (point > high) high = point
    }
    hold.forEach { point ->
        if (point < low) low = point
        if (point > high) high = point
    }
    val span = (high - low).takeIf { it > 0 } ?: 1.0

    Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
            fun x(index: Int): Float = size.width * index / (curve.size - 1)
            fun y(value: Double): Float = size.height - (size.height * ((value - low) / span)).toFloat()

            // The starting stake, so a curve ending above the line is visibly a profit rather than
            // just a shape that goes up.
            val baseline = y(report.startingEquity)
            drawLine(
                baselineInk,
                Offset(0f, baseline),
                Offset(size.width, baseline),
                strokeWidth = 1.dp.toPx(),
            )

            if (hold.size == curve.size) {
                val holdPath = Path()
                hold.forEachIndexed { index, value ->
                    val point = Offset(x(index), y(value))
                    if (index == 0) holdPath.moveTo(point.x, point.y) else holdPath.lineTo(point.x, point.y)
                }
                drawPath(holdPath, holdInk, style = Stroke(width = 1.dp.toPx()))
            }

            val path = Path()
            curve.forEachIndexed { index, value ->
                val point = Offset(x(index), y(value))
                if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
            }
            drawPath(path, ink, style = Stroke(width = 2.dp.toPx()))

            // Over the curve, not under it: these two spans are the answer, and a highlight the
            // curve draws on top of is a highlight nobody sees.
            fun segment(from: Int, to: Int, colour: Color) {
                if (to <= from) return
                val piece = Path()
                for (index in from..to) {
                    val point = Offset(x(index), y(curve[index]))
                    if (index == from) piece.moveTo(point.x, point.y) else piece.lineTo(point.x, point.y)
                }
                drawPath(piece, colour, style = Stroke(width = 3.dp.toPx()))
            }
            segment(excursion.runUpFrom, excursion.runUpTo, runUpInk)
            segment(excursion.drawdownFrom, excursion.drawdownTo, drawdownInk)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            LegendDot("سرمایه", ink)
            LegendDot("خرید و نگهداری", holdInk)
            LegendDot("بیشترین رشد", runUpInk)
            LegendDot("بیشترین افت", drawdownInk)
        }
    }
}

@Composable
private fun LegendDot(label: String, colour: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Spacer(Modifier.size(8.dp).clip(CoineProShapes.small).background(colour))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            fontWeight = FontWeight.Normal,
        )
    }
}

/**
 * The export, through the system document picker.
 *
 * The reader chooses where the file lands — their drive, their downloads, a mail draft — so the app
 * keeps no copy of a document it has no business keeping, and needs neither a `FileProvider` nor a
 * permission. The bytes are built off the main thread: a report of two thousand trades is a few
 * hundred kilobytes of text, and building it in the click handler is a dropped frame at exactly the
 * moment the reader is watching.
 */
@Composable
private fun ExportCard(report: BacktestReport, symbol: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(report)
    var outcome by remember { mutableStateOf<String?>(null) }

    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.Default) {
                BacktestExport.toCsv(current, symbol, CHART_TIME_ZONE).toByteArray(Charsets.UTF_8)
            }
            outcome = write(context, uri, bytes)
        }
    }
    // The second writer, and not a duplicate of the first. `BacktestExport.toXlsx` declares which
    // columns are numbers; a CSV cannot, and the spreadsheet that opens one has to guess — the
    // guess that goes wrong on a Persian machine and returns zero for every sum over the net-profit
    // column. It had no call site until now, which meant the whole workbook writer was unreachable.
    val xlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.Default) {
                BacktestExport.toXlsx(current, symbol, CHART_TIME_ZONE)
            }
            outcome = write(context, uri, bytes)
        }
    }

    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
                CoineProSecondaryButton(
                    text = "خروجی CSV",
                    onClick = { csv.launch(exportFileName(symbol, "csv")) },
                    modifier = Modifier.weight(1f),
                )
                CoineProSecondaryButton(
                    text = "خروجی Excel",
                    onClick = { xlsx.launch(exportFileName(symbol, "xlsx")) },
                    modifier = Modifier.weight(1f),
                )
            }
            MutedNote(
                "همهٔ معاملات، همهٔ سنجه‌ها و بازهٔ اجرا در یک فایل. رایگان — تریدینگ‌ویو برای همین خروجی اشتراک می‌گیرد.",
            )
            outcome?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextSecondary,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}

/**
 * Write the bytes, and report which sentence to show.
 *
 * Every failure here is the same failure from the reader's side — the file did not get written —
 * and there is nothing useful they can do with the distinction between a revoked grant and a full
 * disc, so one sentence covers both. The exception is swallowed rather than rethrown because a
 * crash at the end of an export loses the export as well as the session.
 */
private suspend fun write(context: Context, uri: Uri, bytes: ByteArray): String =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream -> stream.write(bytes) }
                ?: error("no stream")
        }.fold(
            onSuccess = { "فایل ذخیره شد." },
            onFailure = { "ذخیرهٔ فایل انجام نشد. جای دیگری را انتخاب کنید." },
        )
    }

/**
 * A suggested filename carrying the symbol.
 *
 * Latin and hyphenated, because this is a name that will be typed into a search box and sorted in a
 * file list, and Persian digits sort into neither. The reader can rename it.
 */
private fun exportFileName(symbol: String, extension: String): String =
    "coinepro-backtest-" + symbol.lowercase().filter { it.isLetterOrDigit() } + "." + extension

/**
 * The sample-size sentence, under every tab that states a ratio.
 *
 * Thirty is where the metrics stop being anecdotes. The dangerous property of a report like this is
 * that nothing about it looks less confident as the sample shrinks — the decimals do not go away —
 * so the caution has to be written out rather than implied by a small trade count.
 */
@Composable
private fun SampleWarning(trades: Int) {
    if (trades >= CONFIDENT_TRADES) return
    Text(
        text = "فقط ${trades.toPersianDigits()} معامله بسته شد. زیر ${CONFIDENT_TRADES.toPersianDigits()} معامله، این عددها حکایت‌اند نه نتیجه.",
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.Warning,
        fontWeight = FontWeight.Normal,
    )
}

@Composable
private fun MutedNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        fontWeight = FontWeight.Normal,
    )
}

@Composable
private fun MetricRow(label: String, value: String, colour: Color = CoineProColors.TextPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = CoineProColors.TextSecondary)
        Text(
            text = value,
            style = CoineProTextStyles.RowFigure,
            color = colour,
            textAlign = TextAlign.Right,
        )
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = if (selected) CoineProColors.OnAccent else CoineProColors.TextSecondary,
        modifier = Modifier
            .clip(CoineProShapes.small)
            .background(if (selected) CoineProColors.Accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
    )
}

/** Green above zero, red below, and the ordinary ink at exactly zero — a scratch is not a result. */
@Composable
private fun resultTint(value: Double): Color = when {
    !value.isFinite() -> CoineProColors.TextMuted
    value > 0 -> CoineProColors.Buy
    value < 0 -> CoineProColors.Sell
    else -> CoineProColors.TextPrimary
}

private fun Backtest.Strategy.label(): String = when (this) {
    Backtest.Strategy.MA_CROSS -> "تقاطع میانگین"
    Backtest.Strategy.RSI_REVERSION -> "بازگشت RSI"
    Backtest.Strategy.BREAKOUT -> "شکست کانال"
}

/** The three ways the Performance tab can be sliced. */
private enum class Side(val label: String) {
    ALL("همه"),
    LONG("فقط خرید"),
    SHORT("فقط فروش"),
}

/**
 * How many trades the list tab renders before it stops and says so.
 *
 * Two hundred rows is already a long scroll inside a bottom sheet, and every row beyond it is in
 * the export. A silent truncation would be the worse half of this trade-off, so the sentence under
 * the table names both numbers.
 */
private const val MAX_LISTED_TRADES = 200

/** Where a report stops being an anecdote. See [SampleWarning]. */
private const val CONFIDENT_TRADES = 30

private const val CSV_MIME = "text/csv"

/** The full OOXML type. A picker handed `application/vnd.ms-excel` offers to save a 1997 file. */
private const val XLSX_MIME =
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

private val DIRECTION_WIDTH = 44.dp
private val PRICE_WIDTH = 84.dp
private val PNL_WIDTH = 80.dp
private val PERCENT_WIDTH = 68.dp
private val BARS_WIDTH = 44.dp
