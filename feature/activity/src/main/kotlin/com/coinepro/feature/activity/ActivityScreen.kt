package com.coinepro.feature.activity

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.designsystem.resolve
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.execution.ExecutionController
import com.coinepro.core.execution.ExecutionStatus
import com.coinepro.core.execution.SignalExecution
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
) {
    val notificationState by controller.state.collectAsStateWithLifecycle()
    val executionState by executionController.history.collectAsStateWithLifecycle()
    val historyState by signalController.historyState.collectAsStateWithLifecycle()
    var marketFilter by remember { mutableStateOf<MarketType?>(null) }
    var resultFilter by remember { mutableStateOf(PerformanceResultFilter.ALL) }
    var symbolFilter by remember { mutableStateOf("") }
    var alertSymbol by remember { mutableStateOf("XAUUSD") }
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

        if (historyState.loading && historyState.items.isEmpty()) {
            item { LoadingPanel("Loading closed signal history from the server…") }
        } else if (historyState.membershipRequired) {
            item {
                StatePanel(
                    title = "Signal history requires access",
                    body = "The server did not grant access to closed signals. Performance metrics are not estimated locally.",
                    action = "Check again",
                    onAction = signalController::refreshHistory,
                )
            }
        } else if (historyState.error != null && historyState.items.isEmpty()) {
            item {
                StatePanel(
                    title = "Signal history unavailable",
                    body = historyState.error?.resolve()
                        ?: "The server did not return signal history.",
                    action = "Retry",
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
                        title = "Refresh failed",
                        body = "$error Last loaded records remain visible; they are not presented as refreshed data.",
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
                    market = marketFilter,
                    result = resultFilter,
                    symbol = symbolFilter,
                    onMarket = { marketFilter = it },
                    onResult = { resultFilter = it },
                    onSymbol = { symbolFilter = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(18) },
                    onReset = {
                        marketFilter = null
                        resultFilter = PerformanceResultFilter.ALL
                        symbolFilter = ""
                    },
                )
            }
            if (historyState.items.isEmpty()) {
                item {
                    StatePanel(
                        title = "No closed signals yet",
                        body = "The server returned no closed signal records. Rates remain missing, not zero.",
                    )
                }
            } else if (filteredHistory.isEmpty()) {
                item {
                    StatePanel(
                        title = "No records match these filters",
                        body = "Clear or change the filters. Existing performance totals are not reused for an empty filtered view.",
                        action = "Clear filters",
                        onAction = {
                            marketFilter = null
                            resultFilter = PerformanceResultFilter.ALL
                            symbolFilter = ""
                        },
                    )
                }
            } else {
                item { SectionHeader("Signal history", "Closed signals with explicit result evidence. Tap a record to inspect the persisted signal.") }
                items(filteredHistory, key = { "signal-${it.id}" }) { signal ->
                    SignalHistoryCard(signal = signal, onClick = { onOpenSignal(signal.id) })
                }
            }
        }

        item { SectionHeader("Execution ledger", "Server-reported execution lifecycle. No P&L or broker state is inferred.") }
        if (executionState.loading && executionState.items.isEmpty()) {
            item { LoadingPanel("Loading executed signals…") }
        } else if (executionState.error != null && executionState.items.isEmpty()) {
            item {
                StatePanel(
                    title = "Execution history unavailable",
                    body = executionState.error ?: "Execution history is unavailable.",
                    action = "Retry",
                    onAction = executionController::refreshExecutions,
                )
            }
        } else if (executionState.items.isEmpty()) {
            item {
                StatePanel(
                    title = "No executed signals",
                    body = "No execution records were returned by the server.",
                )
            }
        } else {
            executionState.error?.let { error ->
                item { NoticePanel("Execution refresh failed", error, CoineProColors.Warning) }
            }
            items(executionState.items, key = { "execution-${it.id}" }) { execution ->
                ExecutionHistoryCard(execution, onOpenSignal)
            }
        }

        item { SectionHeader("Alerts", "Price alerts and push preferences remain source-backed operational controls.") }
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

        item { SectionHeader("Notifications", "Signal and account events received from the notification service.") }
        if (notificationState.notifications.isEmpty() && !notificationState.loading) {
            item { StatePanel("No notifications yet", "No notification records are available.") }
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
        }
        notificationState.lastError?.let { error ->
            item { NoticePanel("Notification error", error, CoineProColors.Warning) }
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
                Text("ACTIVITY / PERFORMANCE", color = CoineProColors.Gold, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text("Your trading record, from server evidence.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onRefresh, enabled = !refreshing) { Text(if (refreshing) "Refreshing" else "Refresh") }
        }
        Text(
            "Closed signals, execution lifecycle and performance evidence stay separate. Missing data stays missing.",
            color = CoineProColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderMetric(
                value = if (expectedSignals > loadedSignals) "$loadedSignals / $expectedSignals" else loadedSignals.toString(),
                label = "signals loaded",
                accent = CoineProColors.Gold,
                modifier = Modifier.weight(1f),
            )
            HeaderMetric(executionCount.toString(), "executions", CoineProColors.Silver, Modifier.weight(1f))
            HeaderMetric("SERVER", "truth source", CoineProColors.Buy, Modifier.weight(1f))
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
        SectionHeaderContent("Performance evidence", if (coverageComplete) "Complete loaded history." else "Metrics use loaded evidence only; coverage is incomplete.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PerformanceMetric(
                label = "Signals",
                value = if (hasRecords) summary.totalLoaded.toString() else "—",
                detail = if (hasRecords) "filtered records" else "no records",
                accent = CoineProColors.Gold,
                modifier = Modifier.weight(1f),
            )
            PerformanceMetric(
                label = "Win rate",
                value = percentOrMissing(summary.winRate.percent),
                detail = denominatorLabel(summary.winRate.denominator),
                accent = CoineProColors.Buy,
                modifier = Modifier.weight(1f),
            )
            PerformanceMetric(
                label = "Losses",
                value = if (hasRecords) summary.losses.toString() else "—",
                detail = if (hasRecords) "explicit P&L" else "no records",
                accent = CoineProColors.Sell,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PerformanceMetric(
                label = "TP hit",
                value = percentOrMissing(summary.tpHitRate.percent),
                detail = denominatorLabel(summary.tpHitRate.denominator),
                accent = CoineProColors.Buy,
                modifier = Modifier.weight(1f),
            )
            PerformanceMetric(
                label = "SL rate",
                value = percentOrMissing(summary.stopLossRate.percent),
                detail = denominatorLabel(summary.stopLossRate.denominator),
                accent = CoineProColors.Sell,
                modifier = Modifier.weight(1f),
            )
            PerformanceMetric(
                label = "Avg R:R",
                value = summary.averagePlannedRiskReward?.let { "1:${MarketNumberFormatter.price(it, 2)}" } ?: "—",
                detail = denominatorLabel(summary.riskRewardDenominator),
                accent = CoineProColors.Gold,
                modifier = Modifier.weight(1f),
            )
        }
        PremiumCard {
            Text("Evidence coverage", style = MaterialTheme.typography.labelMedium, color = CoineProColors.TextMuted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                EvidenceCount("Wins", summary.wins, CoineProColors.Buy)
                EvidenceCount("Losses", summary.losses, CoineProColors.Sell)
                EvidenceCount("Breakeven", summary.breakeven, CoineProColors.Silver)
                EvidenceCount("PnL missing", summary.unknownPnl, CoineProColors.Warning)
            }
            Text(
                "Zero is shown only when explicit evidence produces zero. A missing denominator is shown as —.",
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
        title = "Partial signal history",
        body = "Loaded ${state.items.size} of ${state.expectedTotal} server-reported closed signals. Performance metrics use only loaded evidence and are not presented as full-history statistics.",
        accent = CoineProColors.Warning,
    )
}

@Composable
private fun HistoryFilters(
    market: MarketType?,
    result: PerformanceResultFilter,
    symbol: String,
    onMarket: (MarketType?) -> Unit,
    onResult: (PerformanceResultFilter) -> Unit,
    onSymbol: (String) -> Unit,
    onReset: () -> Unit,
) {
    PremiumCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Filters", fontWeight = FontWeight.Bold)
                Text("Market, instrument and explicit result", color = CoineProColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onReset) { Text("Reset") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterPill("All markets", market == null) { onMarket(null) }
            FilterPill("Forex", market == MarketType.FOREX) { onMarket(MarketType.FOREX) }
            FilterPill("Crypto", market == MarketType.CRYPTO) { onMarket(MarketType.CRYPTO) }
        }
        OutlinedTextField(
            value = symbol,
            onValueChange = onSymbol,
            label = { Text("Instrument") },
            placeholder = { Text("XAUUSD / BTCUSDT") },
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
            HistoryValue("P&L", finitePnl(signal), resultColor)
            HistoryValue("Entry", finitePrice(signal.entry, signal.symbol))
            HistoryValue("SL", finitePrice(signal.stopLoss, signal.symbol), CoineProColors.Sell)
            HistoryValue("R:R", signal.riskRewardTp1?.takeIf { it.isFinite() && it > 0.0 }?.let { "1:${MarketNumberFormatter.price(it, 2)}" } ?: "—")
        }
        val meta = listOfNotNull(
            signal.closeReason?.takeIf { it.isNotBlank() }?.let { "Close: $it" },
            signal.result?.source?.takeIf { it.isNotBlank() }?.let { "Result source: $it" },
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
                Text(execution.signal?.symbol ?: "Signal #${execution.signalId}", fontWeight = FontWeight.Bold)
                Text("${execution.venue.name} · ${execution.side.uppercase()}", color = CoineProColors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Text(execution.status.name.replace('_', ' '), color = statusColor, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        HorizontalDivider(color = CoineProColors.Border)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            HistoryValue("Quantity", execution.quantity)
            HistoryValue("Created", formatTimestamp(execution.createdAt))
            HistoryValue("Closed", formatTimestamp(execution.closedAt))
        }
        execution.providerOrderId?.takeIf { it.isNotBlank() }?.let {
            Text("Provider order: $it", color = CoineProColors.TextMuted, style = MaterialTheme.typography.bodySmall)
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).then(modifier),
        colors = CardDefaults.cardColors(containerColor = CoineProColors.SurfaceElevated),
        border = BorderStroke(1.dp, CoineProColors.Border),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun NoticePanel(title: String, body: String, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = accent, fontWeight = FontWeight.Bold)
            Text(body, color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PreferenceCard(value: PushPreferences, onChange: (PushPreferences) -> Unit) {
    PremiumCard {
        Text("Push preferences", fontWeight = FontWeight.Bold)
        PreferenceRow("New signals", value.newSignals) { onChange(value.copy(newSignals = it)) }
        HorizontalDivider(color = CoineProColors.Border)
        PreferenceRow("Entry / TP / SL", value.signalUpdates) { onChange(value.copy(signalUpdates = it)) }
        HorizontalDivider(color = CoineProColors.Border)
        PreferenceRow("Price alerts", value.priceAlerts) { onChange(value.copy(priceAlerts = it)) }
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
        Text("New price alert", fontWeight = FontWeight.Bold)
        OutlinedTextField(value = symbol, onValueChange = onSymbol, label = { Text("Symbol") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = value, onValueChange = onValue, label = { Text("Price") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
        ) { Text("Create alert") }
    }
}

@Composable
private fun AlertRow(alert: PriceAlert, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    PremiumCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(alert.symbol, fontWeight = FontWeight.Bold)
                FinancialText("${alert.condition.name.replace('_', ' ')} ${MarketNumberFormatter.price(alert.value, 2)}", color = CoineProColors.TextSecondary)
            }
            Switch(checked = alert.active, onCheckedChange = onToggle)
        }
        TextButton(onClick = onDelete) { Text("Delete") }
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

private fun percentOrMissing(value: Double?): String = value?.let { "${MarketNumberFormatter.price(it, 1)}%" } ?: "—"
private fun denominatorLabel(value: Int): String = if (value == 0) "no evidence" else "n=$value"
private fun resultLabel(value: PerformanceResultFilter): String = when (value) {
    PerformanceResultFilter.ALL -> "All results"
    PerformanceResultFilter.WIN -> "Win"
    PerformanceResultFilter.LOSS -> "Loss"
    PerformanceResultFilter.BREAKEVEN -> "Breakeven"
    PerformanceResultFilter.UNKNOWN -> "Result missing"
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
    return runCatching { Instant.parse(raw).atZone(ZoneId.systemDefault()).format(activityTimeFormatter) }.getOrDefault("—")
}
