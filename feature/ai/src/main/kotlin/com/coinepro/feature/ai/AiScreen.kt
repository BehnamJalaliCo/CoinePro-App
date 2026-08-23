package com.coinepro.feature.ai

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.aisignal.AiGeneratedSignal
import com.coinepro.core.aisignal.AiSignalController
import com.coinepro.core.aisignal.AiSignalJob
import com.coinepro.core.aisignal.AiSignalJobStatus
import com.coinepro.core.aisignal.AiSignalProductScope
import com.coinepro.core.aisignal.AiSignalRequest
import com.coinepro.core.aisignal.AiSignalRisk
import com.coinepro.core.aisignal.AiSignalState
import com.coinepro.core.aisignal.AiSignalTimeframe
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.model.SignalDirection

@Composable
fun AiScreen(
    controller: AiSignalController,
    onOpenSignal: (Long) -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var symbol by remember { mutableStateOf(AiSignalProductScope.defaultSymbols.first()) }
    var timeframe by remember { mutableStateOf(AiSignalTimeframe.H1) }
    var risk by remember { mutableStateOf(AiSignalRisk.MEDIUM) }

    LaunchedEffect(controller) { controller.refreshQuota() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "AI Market Signal",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Request a server-generated market signal. Android never invents AI progress and never executes raw model text.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )

        QuotaCard(state)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("New AI Signal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Symbol", style = MaterialTheme.typography.labelLarge)
                ChipRow {
                    AiSignalProductScope.defaultSymbols.forEach { option ->
                        FilterChip(
                            selected = symbol == option,
                            onClick = { symbol = option },
                            label = { Text(option) },
                        )
                    }
                }

                Text("Timeframe", style = MaterialTheme.typography.labelLarge)
                ChipRow {
                    AiSignalTimeframe.entries.forEach { option ->
                        FilterChip(
                            selected = timeframe == option,
                            onClick = { timeframe = option },
                            label = { Text(option.label) },
                        )
                    }
                }

                Text("Risk", style = MaterialTheme.typography.labelLarge)
                ChipRow {
                    AiSignalRisk.entries.forEach { option ->
                        FilterChip(
                            selected = risk == option,
                            onClick = { risk = option },
                            label = { Text(option.label) },
                        )
                    }
                }

                Button(
                    onClick = {
                        controller.submit(
                            AiSignalRequest(
                                symbol = symbol,
                                timeframe = timeframe,
                                risk = risk,
                            ),
                        )
                    },
                    enabled =
                        !state.submitting &&
                        state.job?.isPending != true &&
                        !state.entitlementRequired &&
                        !state.quotaExhausted,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.submitting) "Submitting…" else "Generate AI Signal")
                }
            }
        }

        state.job?.let { job ->
            JobCard(
                job = job,
                onRefresh = controller::refreshCurrent,
                onRetry = controller::retryCurrent,
                onDismiss = controller::dismissJob,
                onOpenSignal = onOpenSignal,
            )
        }

        state.error?.let { error ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("AI Signal status", fontWeight = FontWeight.SemiBold)
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun QuotaCard(state: AiSignalState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Access & quota", fontWeight = FontWeight.SemiBold)
            when {
                state.refreshingQuota -> {
                    CircularProgressIndicator()
                    Text("Checking server quota…")
                }
                state.entitlementRequired -> Text(
                    "An active server entitlement is required for AI Signals.",
                    color = MaterialTheme.colorScheme.error,
                )
                state.quotaExhausted -> {
                    Text("AI Signal quota is exhausted.", color = MaterialTheme.colorScheme.error)
                    state.quota?.resetAt?.let { Text("Reset: $it", style = MaterialTheme.typography.bodySmall) }
                }
                state.quota != null -> {
                    Text("${state.quota.remaining} of ${state.quota.limit} requests remaining")
                    state.quota.resetAt?.let { Text("Reset: $it", style = MaterialTheme.typography.bodySmall) }
                }
                else -> Text("Quota will be confirmed by the server before generation.")
            }
        }
    }
}

@Composable
private fun JobCard(
    job: AiSignalJob,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSignal: (Long) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Generation job", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${job.request.symbol} · ${job.request.timeframe.label} · ${job.request.risk.label} risk")
            Text("Status: ${job.status.name.replace('_', ' ')}", fontWeight = FontWeight.SemiBold)

            when (job.status) {
                AiSignalJobStatus.QUEUED,
                AiSignalJobStatus.RUNNING,
                -> {
                    CircularProgressIndicator()
                    Text(
                        "Waiting for server status. No local percentage or fake completion is shown.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onRefresh) { Text("Refresh status") }
                }

                AiSignalJobStatus.DONE -> {
                    val result = job.result
                    if (result != null) {
                        ValidatedSignalCard(result, onOpenSignal)
                    } else {
                        Text(
                            "The server did not provide a validated structured Signal, so this result is blocked.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onDismiss) { Text("New request") }
                    }
                }

                AiSignalJobStatus.FAILED,
                AiSignalJobStatus.EXPIRED,
                -> {
                    Text(
                        job.errorMessage ?: if (job.status == AiSignalJobStatus.EXPIRED) {
                            "This AI Signal job expired."
                        } else {
                            "AI Signal generation failed."
                        },
                        color = MaterialTheme.colorScheme.error,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onRetry) { Text("Try again") }
                        TextButton(onClick = onDismiss) { Text("Change request") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ValidatedSignalCard(
    signal: AiGeneratedSignal,
    onOpenSignal: (Long) -> Unit,
) {
    val directionColor = when (signal.direction) {
        SignalDirection.BUY -> CoineProColors.Buy
        SignalDirection.SELL -> CoineProColors.Sell
        SignalDirection.NEUTRAL -> CoineProColors.TextSecondary
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Validated Signal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${signal.symbol} · ${signal.timeframe}", fontWeight = FontWeight.SemiBold)
                Text(signal.direction.name, color = directionColor, fontWeight = FontWeight.Bold)
            }
            Text("${signal.confidence}% confidence", color = MaterialTheme.colorScheme.onSurfaceVariant)
            LevelRow("Entry", signal.entry)
            signal.entryZone?.let {
                LevelRow("Entry zone low", it.low)
                LevelRow("Entry zone high", it.high)
            }
            LevelRow("Stop loss", signal.stopLoss, CoineProColors.Sell)
            signal.targets.forEach { target ->
                LevelRow("TP${target.level}", target.price, CoineProColors.Buy)
            }
            signal.riskRewardTp1?.let { FinancialRow("R:R to TP1", "1:${MarketNumberFormatter.price(it, 2)}") }
            signal.rationale?.let {
                Text("Why this setup", fontWeight = FontWeight.SemiBold)
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick = { onOpenSignal(signal.signalId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open validated Signal")
            }
            Text(
                "Execution is available only from the persisted server Signal flow. Raw AI output cannot be executed here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun LevelRow(label: String, value: Double, color: androidx.compose.ui.graphics.Color = CoineProColors.TextPrimary) {
    FinancialRow(label, MarketNumberFormatter.price(value, 6).trimEnd('0').trimEnd('.'), color)
}

@Composable
private fun FinancialRow(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color = CoineProColors.TextPrimary,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(value, color = color, fontWeight = FontWeight.Medium)
        }
    }
}
