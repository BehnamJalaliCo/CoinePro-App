package com.coinepro.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.core.marketdata.MarketConnectionState
import com.coinepro.core.marketdata.MarketDataState
import com.coinepro.core.model.MarketQuote
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.QuoteSource
import java.util.Locale

@Composable
fun HomeScreen(
    state: MarketDataState,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Market Pulse",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = connectionLabel(state.connection),
            style = MaterialTheme.typography.labelMedium,
        )

        if (state.quotes.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (state.connection == MarketConnectionState.CONNECTING) {
                    "Connecting to live market data…"
                } else {
                    "No market data available yet."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.connection == MarketConnectionState.OFFLINE ||
                state.connection == MarketConnectionState.DEGRADED
            ) {
                Button(onClick = onRetry) { Text("Retry") }
            }
        } else {
            state.quotes.values
                .sortedWith(compareBy<MarketQuote>({ marketRank(it) }, { it.instrument.symbol }))
                .forEach { quote -> QuoteCard(quote) }

            if (!state.lastError.isNullOrBlank() &&
                state.connection != MarketConnectionState.LIVE
            ) {
                Text(
                    text = "Realtime stream is recovering. Cached snapshot remains visible.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun QuoteCard(quote: MarketQuote) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = quote.instrument.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = quote.instrument.symbol,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Text(
                    text = if (quote.isStale) "STALE" else "LIVE",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = "\u2066${formatPrice(quote)}\u2069",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(sourceLabel(quote.source), style = MaterialTheme.typography.bodySmall)
                Text(
                    text = if (quote.instrument.marketType == MarketType.FOREX) "Metal" else "Crypto",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private fun formatPrice(quote: MarketQuote): String {
    val decimals = when (quote.instrument.symbol) {
        "XAUUSD", "XAGUSD" -> 2
        else -> if (quote.price >= 1_000) 2 else if (quote.price >= 1) 4 else 6
    }
    return String.format(Locale.US, "%.${decimals}f", quote.price)
}

private fun sourceLabel(source: QuoteSource): String = when (source) {
    QuoteSource.FINNHUB -> "Finnhub"
    QuoteSource.LBANK -> "LBank"
    QuoteSource.UNKNOWN -> "Unknown source"
}

private fun connectionLabel(state: MarketConnectionState): String = when (state) {
    MarketConnectionState.IDLE -> "Market stream idle"
    MarketConnectionState.CONNECTING -> "Connecting…"
    MarketConnectionState.LIVE -> "Realtime connected"
    MarketConnectionState.DEGRADED -> "Realtime reconnecting · HTTP fallback active"
    MarketConnectionState.OFFLINE -> "Market data offline"
}

private fun marketRank(quote: MarketQuote): Int = when (quote.instrument.symbol) {
    "XAUUSD" -> 0
    "XAGUSD" -> 1
    "BTCUSDT" -> 2
    "ETHUSDT" -> 3
    else -> 10
}
