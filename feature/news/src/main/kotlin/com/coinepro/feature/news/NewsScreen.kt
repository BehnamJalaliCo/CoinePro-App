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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProHeaderAction
import com.coinepro.core.designsystem.CoineProListHeader
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProPullToRefresh
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.chartevents.ChartEventSymbols
import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.marketintel.MarketNewsItem
import com.coinepro.core.marketintel.MarketRelevance
import com.coinepro.core.marketintel.NewsSentiment
import com.coinepro.core.model.MarketPlatform
import java.time.ZoneId



@Composable
fun NewsScreen(
    controller: MarketIntelController,
    onOpenCalendar: () -> Unit,
    platform: MarketPlatform = MarketPlatform.TRADEYAR,
    /**
     * Open the chart at the instrument this story is about, scrolled to the second it broke.
     *
     * The other half of the marks on the chart's time axis, and the reason it is worth having: a
     * reader who found the headline here should be able to see what the candle did about it, and a
     * reader who tapped a mark on the axis is already able to read the headline. One direction
     * without the other is a feature that only works if you happened to start on the right screen.
     *
     * Null where the host has no chart to send them to — the guest shell — and then no card offers
     * the entry at all, rather than offering a button that does nothing.
     */
    onOpenChart: ((symbol: String, atSeconds: Long) -> Unit)? = null,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var relevance by remember { mutableStateOf<MarketRelevance?>(null) }

    LaunchedEffect(controller) { controller.refresh() }

    val filtered = remember(state.news, relevance) {
        if (relevance == null) state.news else state.news.filter { relevance in it.relevance }
    }

    // Only the markets this platform serves. A crypto session filtering by "Gold" would be asking
    // the feed for a market its own signals never mention.
    val relevances = remember(platform) {
        when (platform) {
            MarketPlatform.TRADEYAR -> listOf(MarketRelevance.CRYPTO)
            MarketPlatform.COINEPRO_FX -> listOf(MarketRelevance.GOLD, MarketRelevance.SILVER)
        }
    }
    LaunchedEffect(platform) { relevance = null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .padding(horizontal = CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        // The list voice: a compact header with its one action as an icon, so the first headline
        // is above the fold rather than under a two-line heading and a button.
        CoineProListHeader(
            title = stringResource(R.string.news_title),
            subtitle = stringResource(R.string.news_subtitle),
            modifier = Modifier.padding(horizontal = 0.dp),
            actions = {
                CoineProHeaderAction(
                    icon = DesignR.drawable.icon_calendar_dots,
                    label = stringResource(R.string.news_calendar),
                    onClick = onOpenCalendar,
                )
            },
        )

        // A one-market platform gets no filter at all: a control with a single alternative to "all"
        // is a switch that says nothing.
        if (relevances.size > 1) {
            CoineProSegmentedControl(
                options = listOf<MarketRelevance?>(null).plus(relevances)
                    .map { it to (it?.let { r -> stringResource(r.labelRes()) } ?: stringResource(R.string.news_filter_all)) },
                selected = relevance,
                onSelect = { relevance = it },
            )
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
                "loading" -> CenterState(stringResource(R.string.news_loading), showProgress = true)
                // Server wording when there is any: the client did not diagnose this.
                "error" -> CenterState(
                    message = state.error ?: stringResource(R.string.news_unavailable),
                    action = stringResource(R.string.news_retry),
                    onAction = controller::refresh,
                )
                "empty" -> CoineProEmptyState(
                    icon = CoineProIcons.News,
                    message = stringResource(R.string.news_empty),
                    action = stringResource(R.string.news_refresh),
                    onAction = controller::refresh,
                )
                // The strip stays: it says how old the headlines are, which the gesture cannot.
                // What the gesture adds is the answer to a tug, which this list had none of.
                else -> CoineProPullToRefresh(
                    refreshing = state.refreshing,
                    onRefresh = controller::refresh,
                ) {
                    LazyColumn(
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
                            NewsCard(item, onOpenChart, Modifier.animateItem())
                        }
                        item { androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FreshnessStrip(refreshing: Boolean, onRefresh: () -> Unit) {
    CoineProCard(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = CoineProSpacing.Two, vertical = CoineProSpacing.OneHalf),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (refreshing) R.string.news_refreshing else R.string.news_timestamp_note,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (!refreshing) {
                CoineProSecondaryButton(
                    text = stringResource(R.string.news_refresh),
                    onClick = onRefresh,
                )
            }
        }
    }
}

@Composable
private fun NewsCard(
    item: MarketNewsItem,
    onOpenChart: ((symbol: String, atSeconds: Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // A live high-impact story is the one card that gets an edge. Everything else is separated by
    // the gap, so the edge means "read this one" rather than "this is a card".
    val urgent = item.impact == MarketImpact.HIGH && !item.isStale
    CoineProCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .then(
                if (urgent) {
                    Modifier.border(1.dp, CoineProColors.Warning.copy(alpha = 0.55f), MaterialTheme.shapes.large)
                } else {
                    Modifier
                },
            ),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ImpactPill(item.impact)
                    SentimentPill(item.sentiment)
                    // Staleness is said, not implied by a dimmer grey nobody reads as a claim.
                    if (item.isStale) MetaPill(stringResource(R.string.news_stale), CoineProColors.Warning)
                }
                Text(
                    PersianDateTime.moment(item.publishedAt),
                    color = CoineProColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(item.title, style = MaterialTheme.typography.titleSmall, color = CoineProColors.TextPrimary)
            item.summary?.let {
                Text(it, color = CoineProColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.source, color = CoineProColors.TextMuted, style = MaterialTheme.typography.labelMedium)
                // Resolved before the join: stringResource is composable and cannot be called from
                // inside joinToString's non-composable transform.
                val relevanceLabels = item.relevance.map { stringResource(it.labelRes()) }
                Text(
                    text = relevanceLabels.joinToString(" · ")
                        .ifBlank { stringResource(R.string.news_relevance_general) },
                    color = CoineProColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            // Only where the story names a market. A general-market headline has no instrument to
            // open, and sending the reader to an arbitrary chart would imply the story was about it.
            val symbol = ChartEventSymbols.symbolFor(item.relevance)
            if (onOpenChart != null && symbol != null) {
                CoineProSecondaryButton(
                    text = stringResource(R.string.news_open_chart),
                    icon = DesignR.drawable.nav_chart,
                    onClick = { onOpenChart(symbol, item.publishedAt.epochSecond) },
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
    MetaPill(stringResource(impact.labelRes()), color)
}

@Composable
private fun SentimentPill(sentiment: NewsSentiment) {
    val color = when (sentiment) {
        NewsSentiment.BULLISH -> CoineProColors.Buy
        NewsSentiment.BEARISH -> CoineProColors.Sell
        NewsSentiment.NEUTRAL -> CoineProColors.Silver
        NewsSentiment.UNKNOWN -> CoineProColors.TextMuted
    }
    MetaPill(stringResource(sentiment.labelRes()), color)
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
        if (showProgress) CoineProThinkingDots()
        Text(message, color = CoineProColors.TextSecondary, modifier = Modifier.padding(16.dp))
        if (action != null && onAction != null) {
            CoineProPrimaryButton(text = action, onClick = onAction)
        }
    }
}

@androidx.annotation.StringRes
private fun MarketRelevance.labelRes(): Int = when (this) {
    MarketRelevance.GOLD -> R.string.news_relevance_gold
    MarketRelevance.SILVER -> R.string.news_relevance_silver
    MarketRelevance.CRYPTO -> R.string.news_relevance_crypto
}

@androidx.annotation.StringRes
private fun MarketImpact.labelRes(): Int = when (this) {
    MarketImpact.HIGH -> R.string.news_impact_high
    MarketImpact.MEDIUM -> R.string.news_impact_medium
    MarketImpact.LOW -> R.string.news_impact_low
    MarketImpact.UNKNOWN -> R.string.news_impact_unknown
}

@androidx.annotation.StringRes
private fun NewsSentiment.labelRes(): Int = when (this) {
    NewsSentiment.BULLISH -> R.string.news_sentiment_bullish
    NewsSentiment.BEARISH -> R.string.news_sentiment_bearish
    NewsSentiment.NEUTRAL -> R.string.news_sentiment_neutral
    NewsSentiment.UNKNOWN -> R.string.news_sentiment_unknown
}
