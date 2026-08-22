package com.coinepro.feature.signaldetail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.model.SignalDirection
import com.coinepro.core.signals.SignalController
import com.coinepro.core.signals.TradingSignal

@Composable
fun SignalDetailScreen(controller: SignalController, signalId: Long) {
    LaunchedEffect(signalId) { controller.loadDetail(signalId) }
    DisposableEffect(signalId) { onDispose(controller::clearDetail) }
    val state by controller.detailState.collectAsStateWithLifecycle()

    when {
        state.loading -> Center { CircularProgressIndicator() }
        state.membershipRequired -> Center { Text("An active subscription is required for this signal.") }
        state.error != null -> Center {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.error ?: "Signal details are unavailable", color = CoineProColors.TextSecondary)
                Spacer(Modifier.height(CoineProSpacing.One))
                Button(onClick = { controller.loadDetail(signalId) }) { Text("Retry") }
            }
        }
        state.signal != null -> SignalContent(state.signal!!)
        else -> Center { Text("Signal not found.", color = CoineProColors.TextSecondary) }
    }
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun SignalContent(signal: TradingSignal) {
    val directionColor = when (signal.direction) {
        SignalDirection.BUY -> CoineProColors.Buy
        SignalDirection.SELL -> CoineProColors.Sell
        SignalDirection.NEUTRAL -> CoineProColors.TextSecondary
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(signal.symbol, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(signal.timeframe, signal.strategy).joinToString(" · "),
                    color = CoineProColors.TextSecondary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(signal.direction.name, color = directionColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                signal.confidence?.let { Text("$it% confidence", color = CoineProColors.TextSecondary) }
            }
        }

        signal.currentQuote?.let { quote ->
            InfoCard(title = if (quote.isStale) "Last known price" else "Current price") {
                FinancialText(formatPrice(signal.symbol, quote.price), MaterialTheme.typography.headlineSmall)
                if (quote.isStale) {
                    Text("Realtime quote is stale; no live status is implied.", color = CoineProColors.Warning, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        InfoCard("Trade setup") {
            LevelRow("Entry", signal.entry, signal.symbol)
            signal.entryZone?.let { zone ->
                if (zone.low != null || zone.high != null) {
                    LevelRow("Entry zone low", zone.low, signal.symbol)
                    LevelRow("Entry zone high", zone.high, signal.symbol)
                }
            }
            LevelRow("Stop loss", signal.stopLoss, signal.symbol, accent = CoineProColors.Sell)
            signal.targets.sortedBy { it.level }.forEach { target ->
                LevelRow("TP${target.level}${if (target.hit) " · hit" else ""}", target.price, signal.symbol, accent = CoineProColors.Buy)
            }
            signal.riskRewardTp1?.let {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("R:R to TP1", color = CoineProColors.TextMuted)
                    FinancialText("1:${MarketNumberFormatter.price(it, 2)}")
                }
            }
        }

        signal.rationale?.takeIf { it.isNotBlank() }?.let { rationale ->
            InfoCard("Why this setup") {
                Text(rationale, color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodyLarge)
            }
        }

        signal.scoreBreakdown?.let { scores ->
            if (scores.technical != null || scores.pattern != null || scores.ml != null) {
                InfoCard("Signal evidence") {
                    ScoreRow("Technical", scores.technical)
                    ScoreRow("Pattern", scores.pattern)
                    ScoreRow("ML", scores.ml)
                }
            }
        }

        signal.result?.let { result ->
            InfoCard("Result") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("P&L", color = CoineProColors.TextMuted)
                    FinancialText(result.pnlUsd?.let { "${'$'}${MarketNumberFormatter.price(it, 2)}" } ?: "—")
                }
                result.source?.let { Text("Source: $it", color = CoineProColors.TextMuted, style = MaterialTheme.typography.bodySmall) }
            }
        }

        Spacer(Modifier.height(CoineProSpacing.Two))
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CoineProColors.SurfaceElevated),
        border = BorderStroke(1.dp, CoineProColors.Border),
    ) {
        Column(
            modifier = Modifier.padding(CoineProSpacing.Two),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(color = CoineProColors.Border)
            content()
        }
    }
}

@Composable
private fun LevelRow(label: String, value: Double?, symbol: String, accent: Color = CoineProColors.TextPrimary) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = CoineProColors.TextMuted)
        FinancialText(value?.let { formatPrice(symbol, it) } ?: "—", color = accent)
    }
}

@Composable
private fun ScoreRow(label: String, value: Double?) {
    if (value == null) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = CoineProColors.TextMuted)
        FinancialText(MarketNumberFormatter.price(value, 1))
    }
}

@Composable
private fun FinancialText(
    value: String,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = CoineProColors.TextPrimary,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(value, color = color, style = style, fontWeight = FontWeight.Medium)
    }
}

private fun formatPrice(symbol: String, value: Double): String {
    val decimals = when {
        symbol == "XAUUSD" -> 2
        symbol == "XAGUSD" -> 3
        value >= 1_000 -> 2
        value >= 1 -> 4
        else -> 6
    }
    return MarketNumberFormatter.price(value, decimals)
}
