package com.coinepro.feature.activity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.designsystem.resolve
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.parseWireInstant
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.execution.ExecutionStatus
import com.coinepro.core.execution.SignalExecution
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.SignalDirection
import com.coinepro.core.notifications.NotificationController
import com.coinepro.core.notifications.PriceAlert
import com.coinepro.core.notifications.PriceAlertCondition
import com.coinepro.core.notifications.PriceAlertTrigger
import com.coinepro.core.notifications.PushPreferences
import com.coinepro.core.signals.PerformanceResultFilter
import com.coinepro.core.signals.SignalController
import com.coinepro.core.signals.SignalHistoryState
import com.coinepro.core.signals.TradingSignal
import com.coinepro.core.signals.filterHistory
import com.coinepro.core.signals.performanceResult
import com.coinepro.core.signals.summarizeSignalPerformance
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val activityTimeFormatter = DateTimeFormatter.ofPattern("MMM d · HH:mm")

@Composable
fun ActivityScreen(
    controller: NotificationController,
    executionController: ExecutionController,
    signalController: SignalController,
    onOpenSignal: (Long) -> Unit,
    /**
     * Opens the alert screen. Null leaves the entry off.
     *
     * Alerts live here because this is where a reader already comes to see what fired; the app used
     * to be able to *receive* a price alert while offering nowhere to create one.
     */
    onOpenAlerts: (() -> Unit)? = null,
    platform: MarketPlatform = MarketPlatform.COINEPRO_FX,
) {
    val notificationState by controller.state.collectAsStateWithLifecycle()
    val executionState by executionController.history.collectAsStateWithLifecycle()
    val historyState by signalController.historyState.collectAsStateWithLifecycle()
    // Fixed to the platform on screen rather than chosen. The two markets are separate accounts on
    // separate backends, so a "crypto" filter here would either return nothing or — worse — return
    // rows belonging to an account this session is not signed in to.
    val marketFilter: MarketType = platform.marketType
    var resultFilter by remember { mutableStateOf(PerformanceResultFilter.ALL) }
    var symbolFilter by remember { mutableStateOf("") }
    var alertSymbol by remember(platform) { mutableStateOf(platform.defaultAlertSymbol()) }
    var alertValue by remember { mutableStateOf("") }
    var alertCondition by remember { mutableStateOf(PriceAlertCondition.CROSS) }

    LaunchedEffect(controller, executionController, signalController) {
        controller.refresh()
        controller.markRead()
        executionController.refreshExecutions()
        signalController.refreshHistory()
    }

    val filteredHistory = historyState.items.filterHistory(
        market = marketFilter,
        symbol = symbolFilter,
        result = resultFilter,
    )
    val summary = summarizeSignalPerformance(
        signals = filteredHistory,
        expectedTotal = filteredHistory.size,
        coverageComplete = historyState.coverageComplete,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ActivityHeader(
                loadedSignals = historyState.items.size,
                expectedSignals = historyState.expectedTotal,
                executionCount = executionState.items.size,
                refreshing = historyState.loading || executionState.loading,
                onRefresh = {
                    signalController.refreshHistory()
                    executionController.refreshExecutions()
                    controller.refresh()
                },
            )
        }

        onOpenAlerts?.let { open ->
            item {
                CoineProCard(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = open),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.activity_alerts_title),
                                style = MaterialTheme.typography.titleSmall,
                                color = CoineProColors.TextPrimary,
                            )
                            Text(
                                text = stringResource(R.string.activity_alerts_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = CoineProColors.TextMuted,
                            )
                        }
                        Text(
                            text = notificationState.alerts.size.toPersianDigits(),
                            style = MaterialTheme.typography.titleMedium,
                            color = CoineProColors.Accent,
                        )
                    }
                }
            }
        }

        if (historyState.loading && historyState.items.isEmpty()) {
            item { LoadingPanel(stringResource(R.string.activity_history_loading)) }
        } else if (historyState.membershipRequired) {
            item {
                StatePanel(
                    title = stringResource(R.string.activity_history_locked_title),
                    body = stringResource(R.string.activity_history_locked_body),
                    action = stringResource(R.string.activity_check_again),
                    onAction = signalController::refreshHistory,
                )
            }
        } else if (historyState.error != null && historyState.items.isEmpty()) {
            item {
                StatePanel(
                    title = stringResource(R.string.activity_history_unavailable_title),
                    body = historyState.error?.resolve()
                        ?: stringResource(R.string.activity_history_unavailable_body),
                    action = stringResource(R.string.activity_retry),
                    onAction = signalController::refreshHistory,
                )
            }
        } else {
            if (!historyState.coverageComplete) {
                item { CoverageNotice(historyState) }
            }
            historyState.error?.let { error ->
                item {
                    NoticePanel(
                        title = stringResource(R.string.activity_refresh_failed),
                        body = stringResource(R.string.activity_refresh_failed_body, error),
                        accent = CoineProColors.Warning,
                    )
                }
            }
            item {
                PerformanceSection(
                    summary = summary,
                    hasRecords = filteredHistory.isNotEmpty(),
                    coverageComplete = historyState.coverageComplete,
                )
            }
            item {
                HistoryFilters(
                    result = resultFilter,
                    symbol = symbolFilter,
                    onResult = { resultFilter = it },
                    onSymbol = { symbolFilter = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(18) },
                    onReset = {
                        resultFilter = PerformanceResultFilter.ALL
                        symbolFilter = ""
                    },
                )
            }
            if (historyState.items.isEmpty()) {
                item {
                    StatePanel(
                        title = stringResource(R.string.activity_history_empty_title),
                        body = stringResource(R.string.activity_history_empty_body),
                    )
                }
            } else if (filteredHistory.isEmpty()) {
                item {
                    StatePanel(
                        title = stringResource(R.string.activity_filtered_empty_title),
                        body = stringResource(R.string.activity_filtered_empty_body),
                        action = stringResource(R.string.activity_clear_filters),
                        onAction = {
                            resultFilter = PerformanceResultFilter.ALL
                            symbolFilter = ""
                        },
                    )
                }
            } else {
                item { SectionHeader(stringResource(R.string.activity_history_title), stringResource(R.string.activity_history_subtitle)) }
                items(filteredHistory, key = { "signal-${it.id}" }) { signal ->
                    SignalHistoryCard(signal = signal, onClick = { onOpenSignal(signal.id) })
                }
            }
        }

        // The whole section is dropped where the platform places no orders at all. A ledger heading
        // above an explanation of why there is no ledger is two things saying nothing.
        if (!executionState.unsupported) {
        item { SectionHeader(stringResource(R.string.activity_ledger_title), stringResource(R.string.activity_ledger_subtitle)) }
        if (executionState.loading && executionState.items.isEmpty()) {
            item { LoadingPanel(stringResource(R.string.activity_executions_loading)) }
        } else if (executionState.error != null && executionState.items.isEmpty()) {
            item {
                StatePanel(
                    title = stringResource(R.string.activity_executions_unavailable_title),
                    body = executionState.error ?: stringResource(R.string.activity_executions_unavailable_body),
                    action = stringResource(R.string.activity_retry),
                    onAction = executionController::refreshExecutions,
                )
            }
        } else if (executionState.items.isEmpty()) {
            item {
                StatePanel(
                    title = stringResource(R.string.activity_executions_empty_title),
                    body = stringResource(R.string.activity_executions_empty_body),
                )
            }
        } else {
            executionState.error?.let { error ->
                item { NoticePanel(stringResource(R.string.activity_execution_refresh_failed), error, CoineProColors.Warning) }
            }
            items(executionState.items, key = { "execution-${it.id}" }) { execution ->
                ExecutionHistoryCard(execution, onOpenSignal)
            }
        }
        }

        item { SectionHeader(stringResource(R.string.activity_alerts_title), stringResource(R.string.activity_alerts_subtitle)) }
        item {
            PreferenceCard(
                value = notificationState.preferences,
                onChange = controller::updatePreferences,
            )
        }
        item {
            NewAlertCard(
                symbol = alertSymbol,
                value = alertValue,
                condition = alertCondition,
                onSymbol = { alertSymbol = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(18) },
                onValue = { alertValue = it.filter { ch -> ch.isDigit() || ch == '.' } },
                onCondition = { alertCondition = it },
                onCreate = {
                    val target = alertValue.toDoubleOrNull()
                    if (alertSymbol.isNotBlank() && target != null && target.isFinite() && target > 0.0) {
                        controller.createAlert(
                            symbol = alertSymbol,
                            condition = alertCondition,
                            value = target,
                            trigger = PriceAlertTrigger.ONCE,
                        )
                        alertValue = ""
                    }
                },
            )
        }
        if (notificationState.alerts.isNotEmpty()) {
            items(notificationState.alerts, key = { "alert-${it.id}" }) { alert ->
                AlertRow(
                    alert = alert,
                    onToggle = { controller.setAlertActive(alert, it) },
                    onDelete = { controller.deleteAlert(alert.id) },
                )
            }
        }

        item { SectionHeader(stringResource(R.string.activity_notifications_title), stringResource(R.string.activity_notifications_subtitle)) }
        if (notificationState.notifications.isEmpty() && !notificationState.loading) {
            item { StatePanel(stringResource(R.string.activity_notifications_empty_title), stringResource(R.string.activity_notifications_empty_body)) }
        } else {
            items(notificationState.notifications) { notification ->
                PremiumCard(
                    modifier = Modifier.clickable(enabled = notification.signalId != null) {
                        notification.signalId?.let(onOpenSignal)
                    },
                ) {
                    Text(notification.title, fontWeight = FontWeight.Bold)
                    if (notification.body.isNotBlank()) {
                        Text(notification.body, color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            // A truncated list that says nothing looks like the whole history, and a reader hunting
            // for something from last week concludes it was never recorded.
            if (notificationState.hasMoreNotifications) {
                item {
                    Text(
                        stringResource(R.string.activity_notifications_truncated),
                        modifier = Modifier.padding(horizontal = CoineProSpacing.Half),
                        color = CoineProColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        notificationState.lastMessage?.let { message ->
            item {
                // Server wording when the server gave any, resolved owned copy otherwise. Either
                // way it is one sentence a reader can act on, not an HTTP status.
                NoticePanel(
                    stringResource(R.string.activity_notification_error),
                    message.resolve(),
                    CoineProColors.Warning,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ActivityHeader(
    loadedSignals: Int,
    expectedSignals: Int,
    executionCount: Int,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.activity_eyebrow), color = CoineProColors.Gold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.activity_headline), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onRefresh, enabled = !refreshing) { Text(stringResource(if (refreshing) R.string.activity_refreshing else R.string.activity_refresh)) }
        }
        Text(
            stringResource(R.string.activity_note),
            color = CoineProColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderMetric(
                value = if (expectedSignals > loadedSignals) BidiText.isolateLtr("$loadedSignals / $expectedSignals") else loadedSignals.toString(),
                label = stringResource(R.string.activity_signals_loaded),
                accent = CoineProColors.Gold,
                modifier = Modifier.weight(1f),
            )
            HeaderMetric(executionCount.toPersianDigits(), stringResource(R.string.activity_metric_executions), CoineProColors.Silver, Modifier.weight(1f))
            HeaderMetric(stringResource(R.string.activity_server), stringResource(R.string.activity_truth_source), CoineProColors.Buy, Modifier.weight(1f))
        }
    }
}

@Composable
private fun HeaderMetric(value: String, label: String, accent: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = CoineProColors.SurfaceElevated,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CoineProColors.Border),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FinancialText(value, color = accent, style = MaterialTheme.typography.titleMedium)
            Text(label, color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun PerformanceSection(
    summary: com.coinepro.core.signals.SignalPerformanceSummary,
    hasRecords: Boolean,
    coverageComplete: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionHeaderContent(stringResource(R.string.activity_performance_title), if (coverageComplete) stringResource(R.string.activity_coverage_complete) else stringResource(R.string.activity_coverage_partial))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PerformanceMetric(
                label = stringResource(R.string.activity_metric_signals),
                value = if (hasRecords) summary.totalLoaded.toString() else "—",
                detail = if (hasRecords) stringResource(R.string.activity_metric_filtered) else stringResource(R.string.activity_metric_no_records),
                accent = CoineProColors.Gold,
                modifier = Modifier.weight(1f),
            )
            PerformanceMetric(
                label = stringResource(R.string.activity_metric_win_rate),
                value = percentOrMissing(summary.winRate.percent),
                detail = denominatorLabel(summary.winRate.denominator),
                accent = CoineProColors.Buy,
                modifier = Modifier.weight(1f),
            )
            PerformanceMetric(
                label = stringResource(R.string.activity_metric_losses),
                value = if (hasRecords) summary.losses.toString() else "—",
                detail = if (hasRecords) stringResource(R.string.activity_metric_explicit_pnl) else stringResource(R.string.activity_metric_no_records),
                accent = CoineProColors.Sell,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PerformanceMetric(
                label = stringResource(R.string.activity_metric_tp_hit),
                value = percentOrMissing(summary.tpHitRate.percent),
                detail = denominatorLabel(summary.tpHitRate.denominator),
                accent = CoineProColors.Buy,
                modifier = Modifier.weight(1f),
            )
            PerformanceMetric(
                label = stringResource(R.string.activity_metric_sl_rate),
                value = percentOrMissing(summary.stopLossRate.percent),
                detail = denominatorLabel(summary.stopLossRate.denominator),
                accent = CoineProColors.Sell,
                modifier = Modifier.weight(1f),
            )
            PerformanceMetric(
                label = stringResource(R.string.activity_metric_avg_rr),
                value = summary.averagePlannedRiskReward?.let { BidiText.isolateLtr("1:" + BidiText.strip(MarketNumberFormatter.price(it, 2))) } ?: "—",
                detail = denominatorLabel(summary.riskRewardDenominator),
                accent = CoineProColors.Gold,
                modifier = Modifier.weight(1f),
            )
        }
        PremiumCard {
            Text(stringResource(R.string.activity_evidence_coverage), style = MaterialTheme.typography.labelMedium, color = CoineProColors.TextMuted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                EvidenceCount(stringResource(R.string.activity_wins), summary.wins, CoineProColors.Buy)
                EvidenceCount(stringResource(R.string.activity_metric_losses), summary.losses, CoineProColors.Sell)
                EvidenceCount(stringResource(R.string.activity_breakeven), summary.breakeven, CoineProColors.Silver)
                EvidenceCount(stringResource(R.string.activity_pnl_missing), summary.unknownPnl, CoineProColors.Warning)
            }
            Text(
                stringResource(R.string.activity_zero_note),
                color = CoineProColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PerformanceMetric(label: String, value: String, detail: String, accent: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = CoineProColors.SurfaceElevated,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, CoineProColors.Border),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label.uppercase(), color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            FinancialText(value, color = accent, style = MaterialTheme.typography.titleLarge)
            Text(detail, color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun EvidenceCount(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FinancialText(value.toString(), color = color, style = MaterialTheme.typography.titleMedium)
        Text(label, color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CoverageNotice(state: SignalHistoryState) {
    NoticePanel(
        title = stringResource(R.string.activity_partial_title),
        body = stringResource(
            R.string.activity_partial_body,
            BidiText.isolateLtr("${state.items.size}"),
            BidiText.isolateLtr("${state.expectedTotal}"),
        ),
        accent = CoineProColors.Warning,
    )
}

@Composable
private fun HistoryFilters(
    result: PerformanceResultFilter,
    symbol: String,
    onResult: (PerformanceResultFilter) -> Unit,
    onSymbol: (String) -> Unit,
    onReset: () -> Unit,
) {
    PremiumCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(stringResource(R.string.activity_filters), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.activity_filters_subtitle), color = CoineProColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onReset) { Text(stringResource(R.string.activity_reset)) }
        }
        OutlinedTextField(
            value = symbol,
            onValueChange = onSymbol,
            label = { Text(stringResource(R.string.activity_instrument)) },
            placeholder = { Text(BidiText.isolateLtr("BTCUSDT")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PerformanceResultFilter.entries.forEach { item ->
                FilterPill(resultLabel(item), result == item) { onResult(item) }
            }
        }
    }
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) CoineProColors.Gold.copy(alpha = 0.18f) else CoineProColors.Surface,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, if (selected) CoineProColors.Gold.copy(alpha = 0.7f) else CoineProColors.Border),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            color = if (selected) CoineProColors.TextPrimary else CoineProColors.TextSecondary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SignalHistoryCard(signal: TradingSignal, onClick: () -> Unit) {
    val result = performanceResult(signal)
    val resultColor = when (result) {
        PerformanceResultFilter.WIN -> CoineProColors.Buy
        PerformanceResultFilter.LOSS -> CoineProColors.Sell
        PerformanceResultFilter.BREAKEVEN -> CoineProColors.Silver
        PerformanceResultFilter.UNKNOWN -> CoineProColors.Warning
        PerformanceResultFilter.ALL -> CoineProColors.TextMuted
    }
    val directionColor = when (signal.direction) {
        SignalDirection.BUY -> CoineProColors.Buy
        SignalDirection.SELL -> CoineProColors.Sell
        SignalDirection.NEUTRAL -> CoineProColors.TextSecondary
    }
    PremiumCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(signal.symbol, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(signal.market.name, signal.timeframe, formatTimestamp(signal.closedAt)).joinToString(" · "),
                    color = CoineProColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(resultLabel(result), color = resultColor, fontWeight = FontWeight.Bold)
                Text(signal.direction.name, color = directionColor, style = MaterialTheme.typography.labelMedium)
            }
        }
        HorizontalDivider(color = CoineProColors.Border)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HistoryValue(stringResource(R.string.activity_pnl), finitePnl(signal), resultColor)
            HistoryValue(stringResource(R.string.activity_entry), finitePrice(signal.entry, signal.symbol))
            HistoryValue("SL", finitePrice(signal.stopLoss, signal.symbol), CoineProColors.Sell)
            HistoryValue(stringResource(R.string.activity_rr), signal.riskRewardTp1?.takeIf { it.isFinite() && it > 0.0 }?.let { BidiText.isolateLtr("1:" + BidiText.strip(MarketNumberFormatter.price(it, 2))) } ?: "—")
        }
        val meta = listOfNotNull(
            signal.closeReason?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.activity_close, it) },
            signal.result?.source?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.activity_result_source, it) },
        )
        if (meta.isNotEmpty()) {
            Text(meta.joinToString(" · "), color = CoineProColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HistoryValue(label: String, value: String, color: Color = CoineProColors.TextPrimary) {
    Column {
        Text(label, color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelSmall)
        FinancialText(value, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExecutionHistoryCard(execution: SignalExecution, onOpenSignal: (Long) -> Unit) {
    val statusColor = when (execution.status) {
        ExecutionStatus.OPEN -> CoineProColors.Buy
        ExecutionStatus.FAILED, ExecutionStatus.CANCELLED -> CoineProColors.Sell
        ExecutionStatus.CLOSED -> CoineProColors.Silver
        ExecutionStatus.UNKNOWN -> CoineProColors.Warning
        else -> CoineProColors.Gold
    }
    PremiumCard(modifier = Modifier.clickable { onOpenSignal(execution.signalId) }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(execution.signal?.symbol ?: stringResource(R.string.activity_signal_number, BidiText.isolateLtr("${execution.signalId}")), fontWeight = FontWeight.Bold)
                // Venue and side are wire identifiers, not prose: shown as the server names them.
                Text(
                    text = BidiText.isolateLtr("${execution.venue.name} · ${execution.side.uppercase()}"),
                    color = CoineProColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(execution.status.name.replace('_', ' '), color = statusColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        HorizontalDivider(color = CoineProColors.Border)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HistoryValue(stringResource(R.string.activity_quantity), execution.quantity)
            HistoryValue(stringResource(R.string.activity_created), formatTimestamp(execution.createdAt))
            HistoryValue(stringResource(R.string.activity_closed), formatTimestamp(execution.closedAt))
        }
        execution.providerOrderId?.takeIf { it.isNotBlank() }?.let {
            Text(stringResource(R.string.activity_provider_order, BidiText.isolateLtr(it)), color = CoineProColors.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        execution.errorMessage?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = CoineProColors.Sell, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        SectionHeaderContent(title, subtitle)
    }
}

@Composable
private fun SectionHeaderContent(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(subtitle, color = CoineProColors.TextMuted, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PremiumCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    CoineProCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = CoineProSpacing.Gutter).then(modifier),
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            content()
        }
    }
}

@Composable
private fun LoadingPanel(message: String) {
    PremiumCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text(message, color = CoineProColors.TextSecondary)
        }
    }
}

@Composable
private fun StatePanel(title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null) {
    PremiumCard {
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Text(body, color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        if (action != null && onAction != null) {
            CoineProPrimaryButton(text = action, onClick = onAction)
        }
    }
}

@Composable
private fun NoticePanel(title: String, body: String, accent: Color) {
    // Outlined because it is a warning. Everything else on this screen is separated by the gap.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter)
            .background(accent.copy(alpha = 0.08f), MaterialTheme.shapes.large)
            .border(1.dp, accent.copy(alpha = 0.55f), MaterialTheme.shapes.large)
            .padding(CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = accent, style = MaterialTheme.typography.titleSmall)
        Text(body, color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PreferenceCard(value: PushPreferences, onChange: (PushPreferences) -> Unit) {
    PremiumCard {
        Text(stringResource(R.string.activity_push_prefs), fontWeight = FontWeight.Bold)
        PreferenceRow(stringResource(R.string.activity_new_signals), value.newSignals) { onChange(value.copy(newSignals = it)) }
        HorizontalDivider(color = CoineProColors.Border)
        PreferenceRow(stringResource(R.string.activity_entry_tp_sl), value.signalUpdates) { onChange(value.copy(signalUpdates = it)) }
        HorizontalDivider(color = CoineProColors.Border)
        PreferenceRow(stringResource(R.string.activity_price_alerts), value.priceAlerts) { onChange(value.copy(priceAlerts = it)) }
    }
}

@Composable
private fun PreferenceRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun NewAlertCard(
    symbol: String,
    value: String,
    condition: PriceAlertCondition,
    onSymbol: (String) -> Unit,
    onValue: (String) -> Unit,
    onCondition: (PriceAlertCondition) -> Unit,
    onCreate: () -> Unit,
) {
    PremiumCard {
        Text(stringResource(R.string.activity_new_alert), fontWeight = FontWeight.Bold)
        OutlinedTextField(value = symbol, onValueChange = onSymbol, label = { Text(stringResource(R.string.activity_symbol)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = value, onValueChange = onValue, label = { Text(stringResource(R.string.activity_price)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(PriceAlertCondition.ABOVE, PriceAlertCondition.BELOW, PriceAlertCondition.CROSS).forEach { item ->
                FilterPill(item.name.replace('_', ' '), condition == item) { onCondition(item) }
            }
        }
        Button(
            onClick = onCreate,
            enabled = symbol.isNotBlank() && value.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.activity_create_alert)) }
    }
}

@Composable
private fun AlertRow(alert: PriceAlert, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    PremiumCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(alert.symbol, fontWeight = FontWeight.Bold)
                FinancialText(
                    value = stringResource(
                        alert.condition.labelRes(),
                        MarketNumberFormatter.price(alert.value, 2),
                    ),
                    color = CoineProColors.TextSecondary,
                )
            }
            Switch(checked = alert.active, onCheckedChange = onToggle)
        }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.activity_delete)) }
    }
}

@Composable
private fun FinancialText(
    value: String,
    color: Color = CoineProColors.TextPrimary,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(value, color = color, style = style, fontWeight = FontWeight.Medium)
    }
}

// The percent sign belongs inside the isolate; outside it, bidi reordering renders "62%" as "%62".
private fun percentOrMissing(value: Double?): String =
    value?.let { BidiText.isolateLtr(BidiText.strip(MarketNumberFormatter.price(it, 1)) + "%") } ?: "—"

@Composable
private fun denominatorLabel(value: Int): String =
    if (value == 0) stringResource(R.string.activity_no_evidence) else stringResource(R.string.activity_sample_size, value.toPersianDigits())

@Composable
private fun resultLabel(value: PerformanceResultFilter): String = when (value) {
    PerformanceResultFilter.ALL -> stringResource(R.string.activity_all_results)
    PerformanceResultFilter.WIN -> stringResource(R.string.activity_win)
    PerformanceResultFilter.LOSS -> stringResource(R.string.activity_loss)
    PerformanceResultFilter.BREAKEVEN -> stringResource(R.string.activity_breakeven)
    PerformanceResultFilter.UNKNOWN -> stringResource(R.string.activity_result_missing)
}

private fun finitePnl(signal: TradingSignal): String {
    val pnl = signal.result?.pnlUsd?.takeIf(Double::isFinite) ?: return "—"
    val sign = if (pnl > 0.0) "+" else ""
    return "$sign${'$'}${MarketNumberFormatter.price(pnl, 2)}"
}

private fun finitePrice(value: Double?, symbol: String): String {
    val safe = value?.takeIf(Double::isFinite) ?: return "—"
    val decimals = when {
        symbol == "XAUUSD" -> 2
        symbol == "XAGUSD" -> 3
        safe >= 1_000 -> 2
        safe >= 1 -> 4
        else -> 6
    }
    return MarketNumberFormatter.price(safe, decimals)
}

private fun formatTimestamp(raw: String?): String {
    if (raw.isNullOrBlank()) return "—"
    val moment = parseWireInstant(raw) ?: return "—"
    return runCatching { moment.atZone(ZoneId.systemDefault()).format(activityTimeFormatter) }.getOrDefault("—")
}

@androidx.annotation.StringRes
private fun PriceAlertCondition.labelRes(): Int = when (this) {
    PriceAlertCondition.ABOVE -> R.string.activity_alert_above
    PriceAlertCondition.BELOW -> R.string.activity_alert_below
    PriceAlertCondition.CROSS_UP -> R.string.activity_alert_cross_up
    PriceAlertCondition.CROSS_DOWN -> R.string.activity_alert_cross_down
    PriceAlertCondition.CROSS -> R.string.activity_alert_cross
}

/** The instrument the alert form opens on, which must belong to the platform being shown. */
private fun MarketPlatform.defaultAlertSymbol(): String = when (this) {
    MarketPlatform.COINEPRO_FX -> "XAUUSD"
    MarketPlatform.TRADEYAR -> "BTCUSDT"
}
