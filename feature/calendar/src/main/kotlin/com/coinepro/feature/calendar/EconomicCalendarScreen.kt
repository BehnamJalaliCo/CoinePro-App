package com.coinepro.feature.calendar

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
import com.coinepro.core.marketintel.EconomicEvent
import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.marketintel.MarketRelevance
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val eventTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val eventDateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

@Composable
fun EconomicCalendarScreen(
    controller: MarketIntelController,
    onOpenNews: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var impact by remember { mutableStateOf<MarketImpact?>(null) }
    var relevance by remember { mutableStateOf<MarketRelevance?>(null) }

    LaunchedEffect(controller) { controller.refresh() }

    val filtered = remember(state.calendar, impact, relevance) {
        state.calendar.filter { event ->
            (impact == null || event.impact == impact) &&
                (relevance == null || relevance in event.relevance)
        }
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
                Text("Economic Calendar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Normalized time · explicit impact", color = CoineProColors.TextSecondary)
            }
            Button(onClick = onOpenNews) { Text("News") }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = impact == null, onClick = { impact = null }, label = { Text("All impact") })
            FilterChip(selected = impact == MarketImpact.HIGH, onClick = { impact = MarketImpact.HIGH }, label = { Text("High") })
            FilterChip(selected = impact == MarketImpact.MEDIUM, onClick = { impact = MarketImpact.MEDIUM }, label = { Text("Medium") })
            FilterChip(selected = impact == MarketImpact.LOW, onClick = { impact = MarketImpact.LOW }, label = { Text("Low") })
            FilterChip(selected = impact == MarketImpact.UNKNOWN, onClick = { impact = MarketImpact.UNKNOWN }, label = { Text("Unknown") })
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = relevance == null, onClick = { relevance = null }, label = { Text("All markets") })
            FilterChip(selected = relevance == MarketRelevance.GOLD, onClick = { relevance = MarketRelevance.GOLD }, label = { Text("Gold") })
            FilterChip(selected = relevance == MarketRelevance.SILVER, onClick = { relevance = MarketRelevance.SILVER }, label = { Text("Silver") })
            FilterChip(selected = relevance == MarketRelevance.CRYPTO, onClick = { relevance = MarketRelevance.CRYPTO }, label = { Text("Crypto") })
        }

        AnimatedContent(
            targetState = when {
                state.loading -> "loading"
                state.error != null && state.calendar.isEmpty() -> "error"
                filtered.isEmpty() -> "empty"
                else -> "content"
            },
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 8 }) togetherWith
                    (fadeOut(tween(150)) + slideOutVertically(tween(150)) { -it / 12 })
            },
            label = "calendar-state",
        ) { mode ->
            when (mode) {
                "loading" -> CenterState("Loading economic events…", showProgress = true)
                "error" -> CenterState(state.error ?: "Calendar is unavailable.", "Retry", controller::refresh)
                "empty" -> CenterState("No events match these filters.", "Refresh", controller::refresh)
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        CalendarFreshnessStrip(state.refreshing, controller::refresh)
                    }
                    items(filtered, key = EconomicEvent::id) { event ->
                        TimelineEventCard(event, Modifier.animateItem())
                    }
                    item { androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CalendarFreshnessStrip(refreshing: Boolean, onRefresh: () -> Unit) {
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
                if (refreshing) "Refreshing server event times…" else "Times shown in device timezone from normalized server instants.",
                color = CoineProColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh, enabled = !refreshing) { Text(if (refreshing) "…" else "Refresh") }
        }
    }
}

@Composable
private fun TimelineEventCard(event: EconomicEvent, modifier: Modifier = Modifier) {
    val zoneTime = event.scheduledAt.atZone(ZoneId.systemDefault())
    val impactColor = when (event.impact) {
        MarketImpact.HIGH -> CoineProColors.Sell
        MarketImpact.MEDIUM -> CoineProColors.Warning
        MarketImpact.LOW -> CoineProColors.Buy
        MarketImpact.UNKNOWN -> CoineProColors.TextMuted
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(
            modifier = Modifier.padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(zoneTime.format(eventTimeFormatter), fontWeight = FontWeight.Bold, color = CoineProColors.TextPrimary)
            Text(zoneTime.format(eventDateFormatter), style = MaterialTheme.typography.labelSmall, color = CoineProColors.TextMuted)
        }
        Card(
            modifier = Modifier.weight(1f).animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = CoineProColors.SurfaceElevated),
            border = BorderStroke(1.dp, impactColor.copy(alpha = if (event.impact == MarketImpact.HIGH && !event.isStale) 0.65f else 0.28f)),
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
                        ImpactBadge(event.impact)
                        if (event.isStale) MetaBadge("Stale", CoineProColors.TextMuted)
                    }
                    Text(
                        listOfNotNull(event.country, event.currency).joinToString(" · ").ifBlank { "Global" },
                        color = CoineProColors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ValueCell("Actual", event.actual, Modifier.weight(1f))
                    ValueCell("Forecast", event.forecast, Modifier.weight(1f))
                    ValueCell("Previous", event.previous, Modifier.weight(1f))
                }
                Text(
                    event.relevance.joinToString(" · ") { it.name.lowercase().replaceFirstChar(Char::uppercase) }.ifBlank { "No instrument relevance supplied" },
                    color = CoineProColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (event.impact == MarketImpact.UNKNOWN) {
                    Text(
                        "Impact is unknown from the structured source and is not inferred by Android.",
                        color = CoineProColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ValueCell(label: String, value: String?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = CoineProColors.Stage.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, CoineProColors.Border),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelSmall)
            Text(value ?: "—", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ImpactBadge(impact: MarketImpact) {
    val color = when (impact) {
        MarketImpact.HIGH -> CoineProColors.Sell
        MarketImpact.MEDIUM -> CoineProColors.Warning
        MarketImpact.LOW -> CoineProColors.Buy
        MarketImpact.UNKNOWN -> CoineProColors.TextMuted
    }
    MetaBadge(if (impact == MarketImpact.UNKNOWN) "Impact unknown" else impact.name.lowercase().replaceFirstChar(Char::uppercase), color)
}

@Composable
private fun MetaBadge(text: String, color: androidx.compose.ui.graphics.Color) {
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
    action: String? = null,
    onAction: (() -> Unit)? = null,
    showProgress: Boolean = false,
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
