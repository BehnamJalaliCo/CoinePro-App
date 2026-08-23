package com.coinepro.feature.news

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.marketintel.MarketNewsItem
import com.coinepro.core.marketintel.MarketRelevance
import com.coinepro.core.marketintel.NewsSentiment
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timestampFormatter = DateTimeFormatter.ofPattern("MMM d · HH:mm")

@Composable
fun NewsScreen(
    controller: MarketIntelController,
    onOpenCalendar: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var relevance by remember { mutableStateOf<MarketRelevance?>(null) }

    LaunchedEffect(controller) { controller.refresh() }

    val filtered = remember(state.news, relevance) {
        if (relevance == null) state.news else state.news.filter { relevance in it.relevance }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Market Intelligence", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Structured news · source truth", color = CoineProColors.TextSecondary)
            }
            Button(onClick = onOpenCalendar) { Text("Calendar") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = relevance == null, onClick = { relevance = null }, label = { Text("All") })
            FilterChip(selected = relevance == MarketRelevance.GOLD, onClick = { relevance = MarketRelevance.GOLD }, label = { Text("Gold") })
            FilterChip(selected = relevance == MarketRelevance.SILVER, onClick = { relevance = MarketRelevance.SILVER }, label = { Text("Silver") })
            FilterChip(selected = relevance == MarketRelevance.CRYPTO, onClick = { relevance = MarketRelevance.CRYPTO }, label = { Text("Crypto") })
        }

        AnimatedContent(
            targetState = when {
                state.loading -> "loading"
                state.error != null && state.news.isEmpty() -> "error"
                filtered.isEmpty() -> "empty"
                else -> "content"
            },
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 8 }) togetherWith
                    (fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 12 })
            },
            label = "news-state",
        ) { mode ->
            when (mode) {
                "loading" -> CenterState("Loading verified market news…", showProgress = true)
                "error" -> CenterState(
                    state.error ?: "Market news is unavailable.",
                    action = "Retry",
                    onAction = controller::refresh,
                )
                "empty" -> CenterState(
                    "No news matches this market filter.",
                    action = "Refresh",
                    onAction = controller::refresh,
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        FreshnessStrip(
                            refreshing = state.refreshing,
                            onRefresh = controller::refresh,
                        )
                    }
                    items(filtered, key = MarketNewsItem::id) { item ->
                        NewsCard(item, Modifier.animateItem())
                    }
                    item { androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FreshnessStrip(refreshing: Boolean, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = CoineProColors.SurfaceElevated),
        border = BorderStroke(1.dp, CoineProColors.Border),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (refreshing) "Refreshing source timestamps…" else "Publication times are normalized from server ISO timestamps.",
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh, enabled = !refreshing) { Text(if (refreshing) "…" else "Refresh") }
        }
    }
}

@Composable
private fun NewsCard(item: MarketNewsItem, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = CoineProColors.SurfaceElevated),
        border = BorderStroke(1.dp, if (item.impact == MarketImpact.HIGH && !item.isStale) CoineProColors.Warning.copy(alpha = 0.55f) else CoineProColors.Border),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ImpactPill(item.impact)
                    SentimentPill(item.sentiment)
                    if (item.isStale) MetaPill("Stale", CoineProColors.TextMuted)
                }
                Text(
                    item.publishedAt.atZone(ZoneId.systemDefault()).format(timestampFormatter),
                    color = CoineProColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            item.summary?.let {
                Text(it, color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.source, color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelMedium)
                Text(
                    item.relevance.joinToString(" · ") { relevance -> relevance.name.lowercase().replaceFirstChar(Char::uppercase) }.ifBlank { "General market" },
                    color = CoineProColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ImpactPill(impact: MarketImpact) {
    val color = when (impact) {
        MarketImpact.HIGH -> CoineProColors.Sell
        MarketImpact.MEDIUM -> CoineProColors.Warning
        MarketImpact.LOW -> CoineProColors.Buy
        MarketImpact.UNKNOWN -> CoineProColors.TextMuted
    }
    MetaPill(if (impact == MarketImpact.UNKNOWN) "Impact unknown" else "${impact.name.lowercase().replaceFirstChar(Char::uppercase)} impact", color)
}

@Composable
private fun SentimentPill(sentiment: NewsSentiment) {
    val color = when (sentiment) {
        NewsSentiment.BULLISH -> CoineProColors.Buy
        NewsSentiment.BEARISH -> CoineProColors.Sell
        NewsSentiment.NEUTRAL -> CoineProColors.Silver
        NewsSentiment.UNKNOWN -> CoineProColors.TextMuted
    }
    MetaPill(if (sentiment == NewsSentiment.UNKNOWN) "Sentiment unknown" else sentiment.name.lowercase().replaceFirstChar(Char::uppercase), color)
}

@Composable
private fun MetaPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.32f)),
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun CenterState(
    message: String,
    showProgress: Boolean = false,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showProgress) CircularProgressIndicator()
        Text(message, color = CoineProColors.TextSecondary, modifier = Modifier.padding(16.dp))
        if (action != null && onAction != null) Button(onClick = onAction) { Text(action) }
    }
}
