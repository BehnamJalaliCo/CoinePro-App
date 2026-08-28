package com.coinepro.feature.signals

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProColumnHeadings
import com.coinepro.core.designsystem.CoineProHeaderAction
import com.coinepro.core.designsystem.CoineProListHeader
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProPullToRefresh
import com.coinepro.core.designsystem.CoineProRowDivider
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProPercentPill
import com.coinepro.core.designsystem.CoineProSegmentTabs
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProSetupProgress
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.resolve
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.MarketType
import com.coinepro.core.model.SignalDirection
import com.coinepro.core.signals.SignalController
import com.coinepro.core.signals.SignalMarketFilter
import com.coinepro.core.signals.SignalStatusFilter
import com.coinepro.core.signals.TradingSignal

/**
 * The signals list, in the "آرام" direction.
 *
 * There is no market filter on this screen any more. Which market is being shown is decided once,
 * by the platform the whole app is scoped to, and offering it again here would let a reader put the
 * screen into a state the rest of the app is not in — a crypto session listing forex setups.
 *
 * [platform] therefore drives the controller rather than the reader driving it.
 */
@Composable
fun SignalsScreen(
    controller: SignalController,
    onOpenSignal: (Long) -> Unit,
    platform: MarketPlatform = MarketPlatform.TRADEYAR,
) {
    LaunchedEffect(controller) { controller.start() }
    LaunchedEffect(controller, platform) { controller.selectMarket(platform.toFilter()) }
    val state by controller.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().background(CoineProColors.Stage)) {
        CoineProListHeader(
            title = stringResource(R.string.signals_title),
            subtitle = if (state.items.isEmpty()) {
                stringResource(R.string.signals_subtitle)
            } else {
                pluralCount(state.items.size)
            },
            actions = {
                CoineProHeaderAction(
                    icon = DesignR.drawable.icon_arrows_clockwise,
                    label = stringResource(R.string.signals_retry),
                    onClick = controller::refresh,
                )
            },
        )
        CoineProSegmentTabs(
            options = SignalStatusFilter.entries.map { it to stringResource(it.labelRes()) },
            selected = state.status,
            onSelect = controller::selectStatus,
        )

        when {
            state.loading && state.items.isEmpty() -> Placeholder {
                CircularProgressIndicator(color = CoineProColors.Gold, strokeWidth = 2.dp)
            }

            state.membershipRequired -> Placeholder {
                MembershipRequired(state.membershipMessage, onRetry = controller::refresh)
            }

            state.error != null && state.items.isEmpty() -> Placeholder {
                CoineProCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        // Server wording when there is any: the client does not restate a failure
                        // it did not diagnose.
                        text = state.error?.resolve() ?: stringResource(R.string.signals_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.TextSecondary,
                    )
                    Spacer(Modifier.height(CoineProSpacing.OneHalf))
                    CoineProPrimaryButton(
                        text = stringResource(R.string.signals_retry),
                        onClick = controller::refresh,
                    )
                }
            }

            state.items.isEmpty() -> Placeholder {
                // Empty is a real state here and it is common: the «فعال» tab is empty whenever
                // nothing is running, which is most of a quiet week. One grey sentence on a black
                // screen cannot be told apart from a screen that failed to load, and a reader who
                // reads it as a failure stops opening the tab.
                CoineProEmptyState(
                    icon = CoineProIcons.Signals,
                    message = stringResource(R.string.signals_empty),
                    hint = stringResource(state.status.emptyHintRes()),
                    action = stringResource(R.string.signals_retry),
                    onAction = controller::refresh,
                )
            }

            else -> {
                CoineProColumnHeadings(
                    start = stringResource(R.string.signals_column_symbol),
                    middle = stringResource(R.string.signals_column_direction),
                    end = stringResource(R.string.signals_column_levels),
                )
                // The pull is the gesture a reader watching a signal actually makes; the arrow in
                // the header stays for the reader who wants a target to aim at. Both call refresh.
                CoineProPullToRefresh(
                    refreshing = state.loading,
                    onRefresh = controller::refresh,
                    modifier = Modifier.weight(1f),
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.items, key = { it.id }) { signal ->
                            SignalRow(signal = signal, onClick = { onOpenSignal(signal.id) })
                            CoineProRowDivider()
                        }
                    }
                }
            }
        }
    }
}

/**
 * One signal as two lines.
 *
 * The list voice wants one row per thing, and a signal is four numbers — so the row carries the
 * identity and the call on the first line and the three levels, labelled, on the second. Dropping
 * the levels would have made the list scannable and useless: "BTCUSDT خرید" without a stop is not
 * something anybody can act on, and making the reader open every row to find out is worse than a
 * second line.
 */
@Composable
private fun SignalRow(signal: TradingSignal, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CoineProSpacing.Two, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
        ) {
            CoineProAssetLogo(symbol = signal.symbol, size = 30.dp)
            Column(modifier = Modifier.width(96.dp)) {
                Text(
                    text = BidiText.isolateLtr(signal.symbol),
                    style = MaterialTheme.typography.labelMedium,
                    color = CoineProColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val context = listOfNotNull(signal.timeframe, signal.strategy).joinToString(" · ")
                if (context.isNotBlank()) {
                    Text(
                        text = context,
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextDisabled,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                DirectionPill(signal.direction)
            }
            signal.currentQuote?.let { quote ->
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = formatPrice(signal.symbol, quote.price),
                        style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr),
                        color = CoineProColors.TextPrimary,
                    )
                    // Only a stale quote says anything. The column is already named «قیمت
                    // لحظه‌ای» at the top of the list, so repeating it on every row is a word the
                    // reader has to skip past to reach the one row where it means something.
                    if (quote.isStale) {
                        Text(
                            text = stringResource(R.string.signals_quote_last),
                            style = MaterialTheme.typography.labelSmall,
                            color = CoineProColors.Warning,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                    // What the trade is worth right now, as the same pill the market list uses for
                    // a move. The server has been sending this on every signal since the feed was
                    // first read and no screen has ever drawn it — so a reader wanting to know
                    // whether a call was working had four prices and their own arithmetic.
                    livePnl(signal)?.let { percent ->
                        CoineProPercentPill(percent = percent, background = CoineProColors.Stage)
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = CoineProSpacing.Two, end = CoineProSpacing.Two, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
        ) {
            LevelFigure(R.string.signals_metric_entry, signal.entry, signal.symbol, CoineProColors.TextSecondary)
            LevelFigure(R.string.signals_metric_stop_loss, signal.stopLoss, signal.symbol, CoineProColors.Sell)
            LevelFigure(
                R.string.signals_metric_target_one,
                signal.targets.firstOrNull { it.level == 1 }?.price,
                signal.symbol,
                CoineProColors.Buy,
            )
        }
        // The three numbers above, drawn. It is the same information and it is not redundant: the
        // figures say where the levels are and the bar says where the trade *is*, which is the
        // thing a reader scanning the list actually came for and the one thing four prices in a
        // row do not tell them.
        CoineProSetupProgress(
            entry = signal.entry,
            stop = signal.stopLoss,
            target = signal.targets.firstOrNull { it.level == 1 }?.price,
            price = signal.currentQuote?.price,
            modifier = Modifier.padding(
                start = CoineProSpacing.Two,
                end = CoineProSpacing.Two,
                bottom = 9.dp,
            ),
        )
    }
}

/** One labelled level on a signal row's second line. */
@Composable
private fun LevelFigure(labelRes: Int, value: Double?, symbol: String, colour: androidx.compose.ui.graphics.Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextDisabled,
            fontWeight = FontWeight.Normal,
        )
        Text(
            text = value?.let { formatPrice(symbol, it) } ?: stringResource(R.string.signals_value_missing),
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Ltr),
            color = if (value == null) CoineProColors.TextDisabled else colour,
        )
    }
}

/** «۴ سیگنال» — a count in prose, so Persian digits. */
@Composable
private fun pluralCount(count: Int): String =
    stringResource(R.string.signals_count, count.toPersianDigits())

@Composable
private fun ColumnScope.Placeholder(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(CoineProSpacing.Gutter),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun MembershipRequired(serverMessage: String?, onRetry: () -> Unit) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.signals_membership_title),
            style = MaterialTheme.typography.titleMedium,
            color = CoineProColors.TextPrimary,
        )
        Spacer(Modifier.height(CoineProSpacing.One))
        Text(
            // The server's own words where it gave them. How someone subscribes differs per
            // platform and changes without the app being rebuilt, so the app's copy is a fallback
            // for a server that said nothing rather than the usual case.
            text = serverMessage?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.signals_membership_body),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
        Spacer(Modifier.height(CoineProSpacing.OneHalf))
        CoineProPrimaryButton(
            text = stringResource(R.string.signals_membership_action),
            onClick = onRetry,
        )
    }
}

@Composable
private fun SignalCard(signal: TradingSignal, onClick: () -> Unit) {
    CoineProCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoineProAssetLogo(symbol = signal.symbol)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = BidiText.isolateLtr(signal.symbol),
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                )
                val context = listOfNotNull(signal.timeframe, signal.strategy).joinToString(" · ")
                if (context.isNotBlank()) {
                    Text(
                        text = context,
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                    )
                }
            }
            DirectionPill(signal.direction)
        }

        Spacer(Modifier.height(CoineProSpacing.OneHalf))
        HorizontalDivider(color = CoineProColors.Border)
        Spacer(Modifier.height(CoineProSpacing.OneHalf))

        // Deliberately *not* held left-to-right. Each cell carries its own label, so the order is
        // read rather than inferred from position, and a Persian reader scanning right-to-left must
        // meet the entry first. Only an unlabelled positional ladder — a price column, an order
        // book — needs its direction pinned; the figures inside these cells are isolated already.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PriceMetric(R.string.signals_metric_entry, signal.entry, signal.symbol, CoineProColors.TextPrimary)
            PriceMetric(R.string.signals_metric_stop_loss, signal.stopLoss, signal.symbol, CoineProColors.Sell)
            PriceMetric(
                R.string.signals_metric_target_one,
                signal.targets.firstOrNull { it.level == 1 }?.price,
                signal.symbol,
                CoineProColors.Buy,
            )
        }

        signal.currentQuote?.let { quote ->
            Spacer(Modifier.height(CoineProSpacing.OneHalf))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    // A stale quote says so rather than being drawn like a live one.
                    text = stringResource(
                        if (quote.isStale) R.string.signals_quote_last else R.string.signals_quote_current,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (quote.isStale) CoineProColors.Warning else CoineProColors.TextMuted,
                )
                Text(
                    text = formatPrice(signal.symbol, quote.price),
                    style = CoineProTextStyles.RowFigure,
                    color = CoineProColors.TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun DirectionPill(direction: SignalDirection) {
    val colour = when (direction) {
        SignalDirection.BUY -> CoineProColors.Buy
        SignalDirection.SELL -> CoineProColors.Sell
        SignalDirection.NEUTRAL -> CoineProColors.TextSecondary
    }
    Text(
        text = stringResource(direction.labelRes()),
        modifier = Modifier
            .background(colour.copy(alpha = 0.14f), CoineProPillShape)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = colour,
    )
}

@Composable
private fun PriceMetric(labelRes: Int, value: Double?, symbol: String, colour: androidx.compose.ui.graphics.Color) {
    Column {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        Text(
            text = value?.let { formatPrice(symbol, it) } ?: stringResource(R.string.signals_value_missing),
            style = CoineProTextStyles.RowFigure,
            color = if (value == null) CoineProColors.TextMuted else colour,
        )
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

private fun MarketPlatform.toFilter(): SignalMarketFilter = when (marketType) {
    MarketType.CRYPTO -> SignalMarketFilter.CRYPTO
    MarketType.FOREX -> SignalMarketFilter.FOREX
}

/**
 * Filter and direction names are shown to the reader, so they resolve through resources rather than
 * through the enum's Kotlin name. The wire values these enums carry stay untouched.
 */
private fun SignalStatusFilter.labelRes(): Int = when (this) {
    SignalStatusFilter.ACTIVE -> R.string.signals_status_active
    SignalStatusFilter.RECENT -> R.string.signals_status_recent
    SignalStatusFilter.CLOSED -> R.string.signals_status_closed
}

private fun SignalDirection.labelRes(): Int = when (this) {
    SignalDirection.BUY -> R.string.signals_direction_buy
    SignalDirection.SELL -> R.string.signals_direction_sell
    SignalDirection.NEUTRAL -> R.string.signals_direction_neutral
}

/**
 * How far the trade is in front, as a percentage of the entry.
 *
 * The server's own figure where it sent one — it is the number the rest of the platform reports and
 * the app has no business publishing a second, slightly different one. Computed here only when it
 * did not, which is the case on a feed that quotes live prices without scoring them.
 *
 * Signed by *direction*, not by price: a sell that has fallen is winning, and a bare
 * `(price − entry) / entry` would paint it red. Null where there is nothing honest to say —
 * no entry, no quote, or an entry of zero.
 */
internal fun livePnl(signal: TradingSignal): Double? {
    signal.livePnlPercent?.let { return it }
    val entry = signal.entry?.takeIf { it != 0.0 } ?: return null
    val price = signal.currentQuote?.price ?: return null
    val move = (price - entry) / entry * 100.0
    return when (signal.direction) {
        SignalDirection.BUY -> move
        SignalDirection.SELL -> -move
        // A neutral call has no side, so it has no profit and loss — only a price that moved.
        SignalDirection.NEUTRAL -> null
    }
}

/**
 * What would fill this tab, said per tab.
 *
 * The three views are empty for three different reasons and one sentence for all of them would be
 * wrong twice: «فعال» is empty because nothing is running, «اخیر» because nothing has been
 * published lately, «بسته‌شده» because nothing has finished yet. A reader who knows which comes
 * back; one who does not concludes the feed is broken.
 */
@androidx.annotation.StringRes
private fun SignalStatusFilter.emptyHintRes(): Int = when (this) {
    SignalStatusFilter.ACTIVE -> R.string.signals_empty_active_hint
    SignalStatusFilter.RECENT -> R.string.signals_empty_recent_hint
    SignalStatusFilter.CLOSED -> R.string.signals_empty_closed_hint
}
