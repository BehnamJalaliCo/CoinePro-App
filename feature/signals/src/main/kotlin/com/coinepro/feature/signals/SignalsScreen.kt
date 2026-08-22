package com.coinepro.feature.signals

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.model.SignalDirection
import com.coinepro.core.signals.SignalController
import com.coinepro.core.signals.SignalMarketFilter
import com.coinepro.core.signals.SignalStatusFilter
import com.coinepro.core.signals.TradingSignal

@Composable
fun SignalsScreen(
    controller: SignalController,
    onOpenSignal: (Long) -> Unit,
) {
    LaunchedEffect(controller) { controller.start() }
    val state by controller.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = CoineProSpacing.Two),
    ) {
        Spacer(Modifier.height(CoineProSpacing.Two))
        Text("Signals", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Actionable setups with server-truth entry, risk and targets.",
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
        Spacer(Modifier.height(CoineProSpacing.Two))

        Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            FilterChip(
                selected = state.market == SignalMarketFilter.FOREX,
                onClick = { controller.selectMarket(SignalMarketFilter.FOREX) },
                label = { Text("Forex") },
            )
            FilterChip(
                selected = state.market == SignalMarketFilter.CRYPTO,
                onClick = { controller.selectMarket(SignalMarketFilter.CRYPTO) },
                label = { Text("Crypto") },
            )
        }
        Spacer(Modifier.height(CoineProSpacing.One))
        Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            SignalStatusFilter.entries.forEach { filter ->
                FilterChip(
                    selected = state.status == filter,
                    onClick = { controller.selectStatus(filter) },
                    label = { Text(filter.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
        Spacer(Modifier.height(CoineProSpacing.Two))

        when {
            state.loading && state.items.isEmpty() -> CenterMessage { CircularProgressIndicator() }
            state.membershipRequired -> MembershipRequired(onRetry = controller::refresh)
            state.error != null && state.items.isEmpty() -> CenterMessage {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error ?: "Signals are unavailable", color = CoineProColors.TextSecondary)
                    Spacer(Modifier.height(CoineProSpacing.One))
                    Button(onClick = controller::refresh) { Text("Retry") }
                }
            }
            state.items.isEmpty() -> CenterMessage {
                Text("No signals in this view.", color = CoineProColors.TextSecondary)
            }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
            ) {
                if (state.loading) {
                    item { Text("Refreshing…", color = CoineProColors.TextMuted) }
                }
                items(state.items, key = { it.id }) { signal ->
                    SignalCard(signal = signal, onClick = { onOpenSignal(signal.id) })
                }
                item { Spacer(Modifier.height(CoineProSpacing.Two)) }
            }
        }
    }
}

@Composable
private fun CenterMessage(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}

@Composable
private fun MembershipRequired(onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CoineProColors.SurfaceElevated),
        border = BorderStroke(1.dp, CoineProColors.Border),
    ) {
        Column(Modifier.padding(CoineProSpacing.Two)) {
            Text("VIP Signals", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(CoineProSpacing.One))
            Text(
                "Active trade levels are available with an active subscription.",
                color = CoineProColors.TextSecondary,
            )
            Spacer(Modifier.height(CoineProSpacing.OneHalf))
            Button(onClick = onRetry) { Text("Check access again") }
        }
    }
}

@Composable
private fun SignalCard(signal: TradingSignal, onClick: () -> Unit) {
    val directionColor = when (signal.direction) {
        SignalDirection.BUY -> CoineProColors.Buy
        SignalDirection.SELL -> CoineProColors.Sell
        SignalDirection.NEUTRAL -> CoineProColors.TextSecondary
    }
    val symbolColor = when (signal.symbol) {
        "XAUUSD" -> CoineProColors.Gold
        "XAGUSD" -> CoineProColors.Silver
        else -> CoineProColors.TextPrimary
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = CoineProColors.SurfaceElevated),
        border = BorderStroke(1.dp, CoineProColors.Border),
    ) {
        Column(Modifier.padding(CoineProSpacing.Two)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(signal.symbol, color = symbolColor, fontWeight = FontWeight.Bold)
                    Text(
                        listOfNotNull(signal.timeframe, signal.strategy).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = CoineProColors.TextMuted,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(signal.direction.name, color = directionColor, fontWeight = FontWeight.Bold)
                    signal.confidence?.let {
                        Text("$it% confidence", style = MaterialTheme.typography.bodySmall, color = CoineProColors.TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(CoineProSpacing.OneHalf))
            HorizontalDivider(color = CoineProColors.Border)
            Spacer(Modifier.height(CoineProSpacing.OneHalf))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PriceMetric("Entry", signal.entry, signal.symbol)
                PriceMetric("SL", signal.stopLoss, signal.symbol)
                PriceMetric("TP1", signal.targets.firstOrNull { it.level == 1 }?.price, signal.symbol)
            }

            signal.currentQuote?.let { quote ->
                Spacer(Modifier.height(CoineProSpacing.OneHalf))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (quote.isStale) "Last price" else "Current", color = CoineProColors.TextMuted)
                    FinancialText(formatPrice(signal.symbol, quote.price))
                }
            }
        }
    }
}

@Composable
private fun PriceMetric(label: String, value: Double?, symbol: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = CoineProColors.TextMuted)
        FinancialText(value?.let { formatPrice(symbol, it) } ?: "—")
    }
}

@Composable
private fun FinancialText(value: String) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(value, color = CoineProColors.TextPrimary, fontWeight = FontWeight.Medium)
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
