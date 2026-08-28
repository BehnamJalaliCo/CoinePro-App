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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
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
import com.coinepro.core.marketintel.EconomicEvent
import com.coinepro.core.marketintel.MarketImpact
import com.coinepro.core.marketintel.MarketIntelController
import com.coinepro.core.marketintel.MarketRelevance
import java.time.ZoneId


@Composable
fun EconomicCalendarScreen(
    controller: MarketIntelController,
    onOpenNews: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var impact by remember { mutableStateOf<MarketImpact?>(null) }

    LaunchedEffect(controller) { controller.refresh() }

    val filtered = remember(state.calendar, impact) {
        state.calendar.filter { event -> impact == null || event.impact == impact }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CoineProColors.Stage)
            .padding(horizontal = CoineProSpacing.Gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoineProListHeader(
            title = stringResource(R.string.calendar_title),
            subtitle = stringResource(R.string.calendar_subtitle),
            modifier = Modifier.padding(horizontal = 0.dp),
            actions = {
                CoineProHeaderAction(
                    icon = DesignR.drawable.icon_newspaper,
                    label = stringResource(R.string.calendar_news),
                    onClick = onOpenNews,
                )
            },
        )

        // Impact is the only filter that survives. The market filter went with it: the calendar is
        // macro data that moves both platforms, so filtering it by instrument hid the releases a
        // reader most needed to see.
        CoineProSegmentedControl(
            options = listOf<MarketImpact?>(null, MarketImpact.HIGH, MarketImpact.MEDIUM, MarketImpact.LOW)
                .map { it to (it?.let { i -> stringResource(i.shortLabelRes()) } ?: stringResource(R.string.calendar_impact_all)) },
            selected = impact,
            onSelect = { impact = it },
        )

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
                "loading" -> CenterState(stringResource(R.string.calendar_loading), showProgress = true)
                // Server wording when there is any.
                "error" -> CenterState(
                    state.error ?: stringResource(R.string.calendar_unavailable),
                    stringResource(R.string.calendar_retry),
                    controller::refresh,
                )
                "empty" -> CoineProEmptyState(
                    icon = CoineProIcons.Calendar,
                    message = stringResource(R.string.calendar_empty),
                    action = stringResource(R.string.calendar_refresh),
                    onAction = controller::refresh,
                )
                else -> CoineProPullToRefresh(
                    refreshing = state.refreshing,
                    onRefresh = controller::refresh,
                ) {
                    LazyColumn(
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
}

@Composable
private fun CalendarFreshnessStrip(refreshing: Boolean, onRefresh: () -> Unit) {
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
                    if (refreshing) R.string.calendar_refreshing else R.string.calendar_timezone_note,
                ),
                color = CoineProColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (!refreshing) {
                CoineProSecondaryButton(
                    text = stringResource(R.string.calendar_refresh),
                    onClick = onRefresh,
                )
            }
        }
    }
}

@Composable
private fun TimelineEventCard(event: EconomicEvent, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
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
            // The clock stays Latin and the date does not, and the difference is deliberate: a
            // release time is checked against MetaTrader's session clock, where a date is prose.
            // See PersianDateTime, which is where that line is drawn for the whole app.
            Text(
                PersianDateTime.clock(event.scheduledAt, zone),
                fontWeight = FontWeight.Bold,
                color = CoineProColors.TextPrimary,
            )
            Text(
                PersianDateTime.weekdayAndDay(event.scheduledAt, zone),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        }
        // Only a live high-impact release is outlined. Outlining every card would make the edge mean
        // "card" rather than "this is the one that moves the market you are in".
        val urgent = event.impact == MarketImpact.HIGH && !event.isStale
        CoineProCard(
            modifier = Modifier
                .weight(1f)
                .animateContentSize()
                .then(
                    if (urgent) {
                        Modifier.border(1.dp, impactColor.copy(alpha = 0.65f), MaterialTheme.shapes.large)
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
                        ImpactBadge(event.impact)
                        if (event.isStale) MetaBadge(stringResource(R.string.calendar_stale), CoineProColors.Warning)
                    }
                    Text(
                        listOfNotNull(event.country, event.currency).joinToString(" · ")
                            .ifBlank { stringResource(R.string.calendar_global) },
                        color = CoineProColors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Text(event.title, style = MaterialTheme.typography.titleSmall, color = CoineProColors.TextPrimary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ValueCell(stringResource(R.string.calendar_actual), event.actual, Modifier.weight(1f))
                    ValueCell(stringResource(R.string.calendar_forecast), event.forecast, Modifier.weight(1f))
                    ValueCell(stringResource(R.string.calendar_previous), event.previous, Modifier.weight(1f))
                }
                // Resolved before the join: stringResource cannot be called inside joinToString.
                val relevanceLabels = event.relevance.map { stringResource(it.labelRes()) }
                Text(
                    text = relevanceLabels.joinToString(" · ")
                        .ifBlank { stringResource(R.string.calendar_no_relevance) },
                    color = CoineProColors.TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                if (event.impact == MarketImpact.UNKNOWN) {
                    Text(
                        text = stringResource(R.string.calendar_impact_unknown_note),
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
        Column(
            // Padding tightened from OneHalf. Three of these share the row's width, and at the
            // wider inset the label had barely thirty pixels to print in — «پیش‌بینی» broke across
            // two lines as «پیش‌بین / ی» and «Forecast» as «Foreca / st».
            modifier = Modifier.padding(horizontal = CoineProSpacing.One, vertical = CoineProSpacing.OneHalf),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = CoineProColors.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                // One line, shrunk to fit rather than wrapped. A word split down the middle is
                // unreadable in a way a slightly smaller word is not — and these three labels are
                // the only thing telling the reader which figure is which.
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
            Text(
                // A release with no number yet gets an em dash. Server values arrive as text and
                // are shown as text — reparsing one would risk printing a figure nobody published.
                text = value?.let(BidiText::isolateLtr) ?: stringResource(R.string.calendar_value_missing),
                style = MaterialTheme.typography.bodyMedium,
                color = if (value == null) CoineProColors.TextMuted else CoineProColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    MetaBadge(stringResource(impact.labelRes()), color)
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
        if (showProgress) CoineProThinkingDots()
        Text(message, color = CoineProColors.TextSecondary, modifier = Modifier.padding(16.dp))
        if (action != null && onAction != null) {
            CoineProPrimaryButton(text = action, onClick = onAction)
        }
    }
}

@androidx.annotation.StringRes
private fun MarketRelevance.labelRes(): Int = when (this) {
    MarketRelevance.GOLD -> R.string.calendar_relevance_gold
    MarketRelevance.SILVER -> R.string.calendar_relevance_silver
    MarketRelevance.CRYPTO -> R.string.calendar_relevance_crypto
}

@androidx.annotation.StringRes
private fun MarketImpact.labelRes(): Int = when (this) {
    MarketImpact.HIGH -> R.string.calendar_impact_high
    MarketImpact.MEDIUM -> R.string.calendar_impact_medium
    MarketImpact.LOW -> R.string.calendar_impact_low
    MarketImpact.UNKNOWN -> R.string.calendar_impact_unknown
}

/** The short form, for the filter, where the word "impact" is already implied by the control. */
@androidx.annotation.StringRes
private fun MarketImpact.shortLabelRes(): Int = when (this) {
    MarketImpact.HIGH -> R.string.calendar_impact_short_high
    MarketImpact.MEDIUM -> R.string.calendar_impact_short_medium
    MarketImpact.LOW -> R.string.calendar_impact_short_low
    MarketImpact.UNKNOWN -> R.string.calendar_impact_short_unknown
}
