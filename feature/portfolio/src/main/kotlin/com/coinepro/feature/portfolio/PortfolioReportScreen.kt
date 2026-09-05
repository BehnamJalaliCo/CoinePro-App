package com.coinepro.feature.portfolio

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.JalaliDate
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProPageHeading
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.portfolio.ClosedTrade
import com.coinepro.core.portfolio.MonthlyPerformance
import com.coinepro.core.portfolio.PortfolioController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

/**
 * The full performance report, and the file the reader takes away.
 *
 * ### What this screen is, and what it costs elsewhere
 *
 * TradingView's free tier gives a strategy report its "basic metrics" and stops there: the
 * risk-adjusted ratios, the streaks, the drawdown anatomy, per-symbol attribution, and any export at
 * all begin at Essential. Every one of them is on this screen, free, and that is a decision rather
 * than an omission — the numbers are arithmetic over a ledger the reader already owns, and the app
 * is a viewer of their broker's books rather than the owner of them. `PortfolioMetrics` and
 * `TradeExport` both say the same thing in their own headers, because this is the kind of decision
 * that gets quietly reversed by somebody who never knew it was one.
 *
 * ### Why it is a second screen
 *
 * `PortfolioScreen` answers "how am I doing" in one figure and four lines, which is what somebody
 * opening a portfolio tab wants. This answers "why", and the honest form of that answer is fifteen
 * metrics, a monthly matrix and a per-symbol table. Putting them on the same screen would bury the
 * first question under the second every single time the tab is opened.
 */
@Composable
fun PortfolioReportScreen(
    controller: PortfolioController,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    LaunchedEffect(controller) { controller.start() }
    val state by controller.state.collectAsStateWithLifecycle()
    val metrics = remember(state.trades) { PortfolioMetrics.of(state.trades) }
    val attribution = remember(state.trades) { PortfolioMetrics.attribution(state.trades) }

    Column(modifier = Modifier.fillMaxSize().background(CoineProColors.Stage)) {
        CoineProPageHeading(
            title = stringResource(R.string.portfolio_report_title),
            eyebrow = stringResource(R.string.portfolio_eyebrow),
            subtitle = stringResource(R.string.portfolio_report_subtitle),
        )
        when {
            state.loading && state.trades.isEmpty() -> Centre { CoineProThinkingDots() }
            state.trades.isEmpty() -> Centre {
                CoineProEmptyState(
                    message = stringResource(R.string.portfolio_report_empty),
                    hint = stringResource(R.string.portfolio_report_empty_hint),
                )
            }
            else -> Report(state.trades, metrics, attribution, state.byMonth, zone)
        }
    }
}

@Composable
private fun Report(
    trades: List<ClosedTrade>,
    metrics: TradeMetrics,
    attribution: List<SymbolAttribution>,
    months: List<MonthlyPerformance>,
    zone: ZoneId,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = CoineProSpacing.Gutter,
            end = CoineProSpacing.Gutter,
            bottom = CoineProSpacing.Six,
        ),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Stack),
    ) {
        item { CurveCard(metrics, zone) }
        item { MetricsCard(metrics) }
        if (attribution.isNotEmpty()) {
            item { AttributionCard(attribution) }
        }
        if (months.isNotEmpty()) {
            item { MonthlyGridCard(months) }
        }
        item { ExportCard(trades, zone) }
    }
}

/**
 * The curve, with the deepest fall marked and then written out underneath.
 *
 * The legend under the chart is not decoration. A shaded band says *where*, and a reader still has
 * to be told *how much* and *over what* — and the amount is the figure they will quote at
 * themselves later, so it is printed as a figure rather than left to be estimated off an axis this
 * chart deliberately does not have.
 */
@Composable
private fun CurveCard(metrics: TradeMetrics, zone: ZoneId) {
    if (metrics.equity.size < 2) return
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardLabel(
            stringResource(
                if (metrics.equityIsBalance) R.string.portfolio_curve_balance
                else R.string.portfolio_curve_profit,
            ),
        )
        Spacer(Modifier.height(CoineProSpacing.One))
        EquityReportCurve(
            points = metrics.equity,
            drawdown = metrics.drawdown,
            runUp = metrics.runUp,
            isBalance = metrics.equityIsBalance,
        )
        val climb = metrics.runUp
        if (climb != null) {
            Spacer(Modifier.height(CoineProSpacing.One))
            // Above the fall, because it happens first in the reading: how far the account got,
            // and then how much of it was given back. The two lines share a shape on purpose.
            SpanLegend(
                swatch = CoineProColors.Buy,
                title = stringResource(R.string.portfolio_report_runup_span),
                span = stringResource(
                    R.string.portfolio_report_drawdown_between,
                    jalaliShort(climb.troughAt, zone),
                    jalaliShort(climb.peakAt, zone),
                ),
                figure = runUpFigure(climb),
                tint = CoineProColors.Buy,
            )
        }
        val fall = metrics.drawdown
        if (fall != null) {
            Spacer(Modifier.height(CoineProSpacing.One))
            SpanLegend(
                swatch = CoineProColors.Sell,
                title = stringResource(R.string.portfolio_report_drawdown_span),
                span = stringResource(
                    R.string.portfolio_report_drawdown_between,
                    jalaliShort(fall.peakAt, zone),
                    jalaliShort(fall.troughAt, zone),
                ),
                figure = drawdownFigure(fall),
                tint = CoineProColors.Sell,
            )
        }
        Spacer(Modifier.height(CoineProSpacing.One))
        Text(
            text = stringResource(R.string.portfolio_report_peak_note),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            fontWeight = FontWeight.Normal,
        )
    }
}

/** The whole metric set, in the order a report is read: result, then edge, then risk, then habit. */
@Composable
private fun MetricsCard(metrics: TradeMetrics) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardLabel(stringResource(R.string.portfolio_report_metrics))
        Spacer(Modifier.height(CoineProSpacing.One))

        MetricRow(stringResource(R.string.portfolio_win_rate), metrics.winRate?.let {
            BidiText.isolateLtr(BidiText.strip(MarketNumberFormatter.price(it, 1)) + "%")
        }, tint = metrics.winRate?.let { it - 50.0 })
        MetricRow(stringResource(R.string.portfolio_profit_factor), metrics.profitFactor?.let {
            MarketNumberFormatter.price(it, 2)
        }, tint = metrics.profitFactor?.let { it - 1.0 })
        MetricRow(stringResource(R.string.portfolio_expectancy), metrics.expectancy?.let {
            MarketNumberFormatter.money(it, signed = true)
        }, tint = metrics.expectancy)

        Divider()
        MetricRow(stringResource(R.string.portfolio_metric_average_win), metrics.averageWin?.let {
            MarketNumberFormatter.money(it)
        }, tint = 1.0)
        MetricRow(stringResource(R.string.portfolio_metric_average_loss), metrics.averageLoss?.let {
            MarketNumberFormatter.money(it)
        }, tint = -1.0)
        MetricRow(stringResource(R.string.portfolio_metric_win_loss), metrics.winLossRatio?.let {
            MarketNumberFormatter.price(it, 2)
        }, tint = metrics.winLossRatio?.let { it - 1.0 })
        MetricRow(stringResource(R.string.portfolio_metric_largest_win), metrics.largestWin?.let {
            MarketNumberFormatter.money(it, signed = true)
        }, tint = 1.0)
        MetricRow(stringResource(R.string.portfolio_metric_largest_loss), metrics.largestLoss?.let {
            MarketNumberFormatter.money(it, signed = true)
        }, tint = -1.0)

        Divider()
        MetricRow(
            label = stringResource(R.string.portfolio_report_runup_span),
            value = metrics.runUp?.let { runUpFigure(it) },
            tint = 1.0,
        )
        // Beside the fall and before it, because a report that prints only the fall makes a
        // steady account and a wild one look identical. `core/chart`'s backtest engine has walked
        // both in one pass since it was written; this is the same pair over a real history.
        MetricNote(stringResource(R.string.portfolio_report_runup_note))
        MetricRow(
            label = stringResource(R.string.portfolio_max_drawdown),
            value = metrics.drawdown?.let { drawdownFigure(it) },
            tint = -1.0,
        )
        // Said on the screen and not only in the KDoc. This is the one figure on the page a reader
        // is likely to arrive already holding a wrong definition of, and a report that prints it
        // without the correction is a report that confirms the wrong definition.
        MetricNote(stringResource(R.string.portfolio_report_drawdown_note))
        MetricRow(
            label = stringResource(R.string.portfolio_metric_longest_drawdown),
            value = metrics.longestDrawdown?.let { run ->
                val parts = durationParts(run.seconds)
                durationLabel(parts)
            },
            tint = -1.0,
        )
        metrics.longestDrawdown?.let { run ->
            MetricNote(
                stringResource(
                    if (run.recovered) R.string.portfolio_report_drawdown_recovered
                    else R.string.portfolio_report_drawdown_open,
                    run.trades.toPersianDigits(),
                ),
            )
        }
        MetricRow(stringResource(R.string.portfolio_metric_sharpe), metrics.sharpe?.let {
            MarketNumberFormatter.price(it, 2)
        }, tint = metrics.sharpe)
        MetricRow(stringResource(R.string.portfolio_metric_sortino), metrics.sortino?.let {
            MarketNumberFormatter.price(it, 2)
        }, tint = metrics.sortino)
        MetricNote(stringResource(R.string.portfolio_report_ratio_note))

        Divider()
        MetricRow(
            label = stringResource(R.string.portfolio_metric_holding),
            value = metrics.averageHoldingSeconds?.let { durationLabel(durationParts(it)) },
        )
        if (metrics.holdingSample in 1 until metrics.trades) {
            // The sample is smaller than the history whenever a trade arrived without an open
            // time, which TradeYar does routinely. Printing the average without saying so would be
            // reporting a scalper's holding time to somebody who swings.
            MetricNote(
                stringResource(
                    R.string.portfolio_report_holding_sample,
                    metrics.holdingSample.toPersianDigits(),
                    metrics.trades.toPersianDigits(),
                ),
            )
        }
        MetricRow(
            label = stringResource(R.string.portfolio_metric_streak_wins),
            value = BidiText.isolateLtr("${metrics.longestWinStreak}"),
            tint = 1.0,
        )
        MetricRow(
            label = stringResource(R.string.portfolio_metric_streak_losses),
            value = BidiText.isolateLtr("${metrics.longestLossStreak}"),
            tint = -1.0,
        )
        if (metrics.scratches > 0) {
            MetricRow(
                label = stringResource(R.string.portfolio_metric_scratches),
                value = BidiText.isolateLtr("${metrics.scratches}"),
            )
        }
    }
}

/**
 * Per-symbol attribution, largest mover first.
 *
 * The bar under each row is the symbol's share of the pool it drew from, drawn as a flat block
 * rather than printed twice. Two numbers on one row is a table; one number and a length is a
 * ranking a reader can read at a glance, which is the only thing this list is for.
 */
@Composable
private fun AttributionCard(rows: List<SymbolAttribution>) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardLabel(stringResource(R.string.portfolio_report_attribution))
        Spacer(Modifier.height(CoineProSpacing.One))
        rows.forEachIndexed { index, row ->
            if (index > 0) Divider()
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.One)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = BidiText.isolateLtr(row.symbol),
                            style = MaterialTheme.typography.titleSmall,
                            color = CoineProColors.TextPrimary,
                        )
                        Text(
                            text = BidiText.isolateLtr("${row.trades}") + " " +
                                stringResource(R.string.portfolio_trades) +
                                (row.winRate?.let {
                                    " · " + BidiText.isolateLtr(
                                        BidiText.strip(MarketNumberFormatter.price(it, 0)) + "%",
                                    )
                                } ?: ""),
                            style = MaterialTheme.typography.labelSmall,
                            color = CoineProColors.TextMuted,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                    Text(
                        text = MarketNumberFormatter.money(row.net, signed = true),
                        style = CoineProTextStyles.RowFigure,
                        color = resultTint(row.net),
                        textAlign = TextAlign.Right,
                    )
                }
                row.share?.let { share ->
                    Spacer(Modifier.height(CoineProSpacing.Half))
                    ShareBar(share, resultTint(row.net))
                }
            }
        }
    }
}

/** A flat block whose length is [percent] of the row. No gradient, no rounding beyond the token. */
@Composable
private fun ShareBar(percent: Double, tint: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(CoineProColors.Border, CoineProShapes.extraSmall),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth((percent / 100.0).toFloat().coerceIn(0.02f, 1f))
                .height(4.dp)
                .background(tint.copy(alpha = 0.7f), CoineProShapes.extraSmall),
        )
    }
}

/**
 * The month-by-month matrix: one row per Solar Hijri year, twelve columns, a total at the end.
 *
 * The months are Jalali because `PortfolioMath.byMonth` buckets them that way, and it does so for a
 * reason worth repeating here: a Jalali month spans two Gregorian ones, so a Gregorian bucket under
 * a Persian month name would file three weeks of trades under the wrong heading.
 *
 * The row scrolls sideways rather than shrinking to fit. Twelve months squeezed into a phone width
 * is twelve columns too narrow to hold a currency figure, and the result is either a truncated
 * number — which is a wrong number — or a font nobody can read.
 */
@Composable
private fun MonthlyGridCard(months: List<MonthlyPerformance>) {
    val byYear = remember(months) { months.groupBy { it.year } }
    val years = remember(byYear) { byYear.keys.sorted() }
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardLabel(stringResource(R.string.portfolio_by_month))
        Spacer(Modifier.height(CoineProSpacing.One))
        Column(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GridCell(text = "", width = YEAR_COLUMN, muted = true)
                for (month in 1..12) {
                    GridCell(
                        text = JalaliDate(years.first(), month, 1).monthName,
                        width = MONTH_COLUMN,
                        muted = true,
                    )
                }
                GridCell(text = stringResource(R.string.portfolio_report_year_total), width = MONTH_COLUMN, muted = true)
            }
            years.forEach { year ->
                val rows = byYear[year].orEmpty().associateBy { it.month }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GridCell(text = BidiText.isolateLtr("$year"), width = YEAR_COLUMN, muted = true)
                    for (month in 1..12) {
                        val cell = rows[month]
                        GridCell(
                            // A month with no trades is blank, not a zero. Zero is a month that
                            // traded and broke even, which is a different and much rarer thing.
                            text = if (cell == null || cell.trades == 0) "" else compactMoney(cell.net),
                            width = MONTH_COLUMN,
                            tint = cell?.takeIf { it.trades > 0 }?.let { resultTint(it.net) },
                        )
                    }
                    val total = byYear[year].orEmpty().sumOf { it.net }
                    GridCell(
                        text = compactMoney(total),
                        width = MONTH_COLUMN,
                        tint = resultTint(total),
                        emphasised = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun GridCell(
    text: String,
    width: Dp,
    muted: Boolean = false,
    tint: Color? = null,
    emphasised: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier.width(width).padding(vertical = CoineProSpacing.Half, horizontal = 4.dp),
        style = if (emphasised) CoineProTextStyles.RowFigure else MaterialTheme.typography.labelSmall,
        color = tint ?: if (muted) CoineProColors.TextMuted else CoineProColors.TextSecondary,
        fontWeight = if (emphasised) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        // Right, never End: a currency figure is a left-to-right run and mirroring its alignment
        // with the paragraph is what puts a column of numbers out of alignment with itself.
        textAlign = TextAlign.Right,
    )
}

/**
 * The two exports.
 *
 * Written through the system document picker rather than into the app's own storage. The reader
 * chooses where the file lands — their drive, their downloads, a mail draft — and the app keeps no
 * copy of a document it has no business keeping. It also means no `FileProvider`, no shared cache
 * directory, and no permission to ask for.
 *
 * The bytes are built off the main thread. A CSV of two hundred trades is instant; a workbook of
 * several thousand is a zip and a few hundred kilobytes of XML, and building that in the click
 * handler is a dropped frame at exactly the moment the reader is watching.
 */
@Composable
private fun ExportCard(trades: List<ClosedTrade>, zone: ZoneId) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(trades)
    var outcome by remember { mutableStateOf<Int?>(null) }

    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CSV_MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.Default) {
                TradeExport.toCsv(current, zone).toByteArray(Charsets.UTF_8)
            }
            outcome = write(context, uri, bytes)
        }
    }
    val xlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.Default) { TradeExport.toXlsx(current, zone) }
            outcome = write(context, uri, bytes)
        }
    }

    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardLabel(stringResource(R.string.portfolio_report_export))
        Spacer(Modifier.height(CoineProSpacing.One))
        Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            CoineProSecondaryButton(
                text = stringResource(R.string.portfolio_report_export_csv),
                onClick = { csv.launch(fileName(zone, "csv")) },
                modifier = Modifier.weight(1f),
            )
            CoineProSecondaryButton(
                text = stringResource(R.string.portfolio_report_export_xlsx),
                onClick = { xlsx.launch(fileName(zone, "xlsx")) },
                modifier = Modifier.weight(1f),
            )
        }
        outcome?.let {
            Spacer(Modifier.height(CoineProSpacing.One))
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.labelSmall,
                color = if (it == R.string.portfolio_report_export_done) {
                    CoineProColors.TextSecondary
                } else {
                    CoineProColors.Warning
                },
                fontWeight = FontWeight.Normal,
            )
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
private suspend fun write(context: Context, uri: Uri, bytes: ByteArray): Int = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openOutputStream(uri)?.use { stream -> stream.write(bytes) }
            ?: error("no stream")
    }.fold(
        onSuccess = { R.string.portfolio_report_export_done },
        onFailure = { R.string.portfolio_report_export_failed },
    )
}

/**
 * A suggested name carrying the Jalali date the reader would look for it under.
 *
 * Latin digits and hyphens, because this is a filename that will be typed into a search box and
 * sorted in a file list, and Persian digits sort into neither. The reader can rename it.
 */
private fun fileName(zone: ZoneId, extension: String): String {
    val today = JalaliDate.fromInstant(Instant.now(), zone)
    return "coinepro-trades-%04d-%02d-%02d.%s".format(
        Locale.US,
        today.year,
        today.month,
        today.day,
        extension,
    )
}

@Composable
private fun MetricRow(label: String, value: String?, tint: Double? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = CoineProSpacing.Half),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextMuted)
        Text(
            text = value ?: stringResource(R.string.portfolio_value_missing),
            style = CoineProTextStyles.RowFigure,
            color = when {
                value == null -> CoineProColors.TextMuted
                tint == null -> CoineProColors.TextPrimary
                else -> resultTint(tint)
            },
            textAlign = TextAlign.Right,
        )
    }
}

/** A sentence attached to the metric above it, in the muted voice so it never reads as a figure. */
@Composable
private fun MetricNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(bottom = CoineProSpacing.Half),
    )
}

@Composable
private fun Divider() {
    HorizontalDivider(
        color = CoineProColors.Border,
        thickness = 1.dp,
        modifier = Modifier.padding(vertical = CoineProSpacing.Half),
    )
}

@Composable
private fun CardLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextSecondary)
}

@Composable
private fun Centre(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(CoineProSpacing.Gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/**
 * The run-up as one isolated run — the amount, and the percentage where there is one.
 *
 * The same shape as [drawdownFigure], down to the separator, because the two sit one above the
 * other and a difference in punctuation between them would read as a difference in kind.
 */
private fun runUpFigure(span: RunUpSpan): String {
    val amount = compactMoney(span.height)
    val percent = span.heightPercent ?: return amount
    return BidiText.isolateLtr(
        BidiText.strip(amount) + " · " + BidiText.strip(MarketNumberFormatter.price(percent, 1)) + "%",
    )
}

/**
 * One marked stretch of the curve, written out under it: a swatch, what it is, when, and how much.
 *
 * Shared by the rise and the fall so the two read as one grammar. Two copies would drift — a
 * different weight on the date line, a different alignment on the figure — and the pair would stop
 * looking like two answers to the same question.
 */
@Composable
private fun SpanLegend(
    swatch: Color,
    title: String,
    span: String,
    figure: String,
    tint: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(swatch.copy(alpha = 0.5f), CoineProShapes.extraSmall),
        )
        Spacer(Modifier.width(CoineProSpacing.One))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
            )
            Text(
                text = span,
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
            )
        }
        Text(
            text = figure,
            style = CoineProTextStyles.RowFigure,
            color = tint,
            textAlign = TextAlign.Right,
        )
    }
}

/**
 * The drawdown as one isolated run — the amount, and the percentage where there is one.
 *
 * One isolate around the whole string rather than two side by side, for the reason
 * `PortfolioScreen` spells out: two adjacent left-to-right runs in a right-to-left paragraph are
 * ordered right to left as runs, so the bracket lands before the amount and the parentheses mirror.
 */
private fun drawdownFigure(span: DrawdownSpan): String {
    val money = MarketNumberFormatter.money(-span.depth, signed = true)
    val percent = span.depthPercent ?: return money
    return BidiText.isolateLtr(
        BidiText.strip(money) + " (" + BidiText.strip(MarketNumberFormatter.price(percent, 1)) + "%)",
    )
}

/** «۱۱ دی» — a date in prose, so Persian digits, and short because the year is on both ends. */
private fun jalaliShort(epochSeconds: Long, zone: ZoneId): String =
    JalaliDate.fromInstant(Instant.ofEpochSecond(epochSeconds), zone).formatShort()

/**
 * A duration as prose, in Persian digits.
 *
 * Two units at most. A holding time is read to answer "roughly how long", and the third unit adds
 * precision that an average over a whole history does not have.
 */
@Composable
private fun durationLabel(parts: DurationParts): String = when {
    parts.days > 0 -> stringResource(
        R.string.portfolio_duration_day_hour,
        parts.days.toInt().toPersianDigits(),
        parts.hours.toInt().toPersianDigits(),
    )
    parts.hours > 0 -> stringResource(
        R.string.portfolio_duration_hour_minute,
        parts.hours.toInt().toPersianDigits(),
        parts.minutes.toInt().toPersianDigits(),
    )
    parts.minutes > 0 -> stringResource(
        R.string.portfolio_duration_minute,
        parts.minutes.toInt().toPersianDigits(),
    )
    else -> stringResource(R.string.portfolio_duration_second, parts.seconds.toInt().toPersianDigits())
}

/**
 * A figure narrow enough for a month cell.
 *
 * Thousands are folded to `k` because twelve columns of `+1,240.00` is a table nobody can read on a
 * phone, and the cell is a shape rather than a statement of record — the exact figure is in the
 * export and in the trade list. Latin digits and `Locale.US` throughout, like every market number.
 */
private fun compactMoney(value: Double): String {
    val magnitude = abs(value)
    val sign = if (value < 0) "-" else "+"
    val body = when {
        magnitude >= 1_000 -> BidiText.strip(MarketNumberFormatter.price(magnitude / 1_000, 1)) + "k"
        else -> BidiText.strip(MarketNumberFormatter.price(magnitude, 0))
    }
    return BidiText.isolateLtr(sign + body)
}

@Composable
private fun resultTint(value: Double): Color = when {
    value > 0 -> CoineProColors.Buy
    value < 0 -> CoineProColors.Sell
    else -> CoineProColors.TextPrimary
}

private val YEAR_COLUMN = 44.dp
private val MONTH_COLUMN = 62.dp

private const val CSV_MIME = "text/csv"
private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
