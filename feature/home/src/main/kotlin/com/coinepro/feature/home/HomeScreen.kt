package com.coinepro.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProAgentOrb
import com.coinepro.core.designsystem.CoineProAssetToken
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProStreamingBar
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.marketdata.MarketConnectionState
import com.coinepro.core.marketdata.MarketDataOrigin
import com.coinepro.core.marketdata.MarketDataState
import com.coinepro.core.model.MarketQuote
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val cacheTimeFormatter = DateTimeFormatter.ofPattern("MMM d · HH:mm")

/**
 * The home screen, in the "آرام" direction.
 *
 * The balance is the hero and everything else is quiet around it. There is exactly one gold object
 * on the page — the primary action under the balance — and the rest of the screen is a short stack
 * of neutral cards separated by gap rather than by rules.
 *
 * Nothing here is invented. The balance appears only when [portfolio] carries one, the signal card
 * only when [openSignals] is non-empty, and the assistant card shows its resting state rather than
 * composing a market summary the server did not send.
 */
@Composable
fun HomeScreen(
    state: MarketDataState,
    onRetry: () -> Unit,
    briefing: HomeBriefing = HomeBriefing.Resting,
    portfolio: HomePortfolio? = null,
    openSignals: List<HomeSignal> = emptyList(),
    onGenerateSignal: () -> Unit = {},
    onSendChart: () -> Unit = {},
    onOpenSignal: (Long) -> Unit = {},
) {
    val quotes = state.quotes.values.sortedWith(
        compareBy<MarketQuote>({ marketRank(it) }, { it.instrument.symbol }),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { BalanceBlock(portfolio = portfolio, state = state) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CoineProPrimaryButton(
                    text = stringResource(R.string.home_action_signal),
                    onClick = onGenerateSignal,
                    modifier = Modifier.weight(1f),
                )
                CoineProSecondaryButton(
                    text = stringResource(R.string.home_action_chart),
                    onClick = onSendChart,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (quotes.isEmpty()) {
            item { EmptyMarket(state = state, onRetry = onRetry) }
        } else {
            item { MarketCard(quotes) }
        }

        if (openSignals.isNotEmpty()) {
            item { SignalsCard(signals = openSignals, onOpenSignal = onOpenSignal) }
        }

        item { AssistantCard(briefing) }

        state.recoveryNote()?.let { note ->
            item {
                Text(
                    text = stringResource(note),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ balance */

@Composable
private fun BalanceBlock(portfolio: HomePortfolio?, state: MarketDataState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = stringResource(R.string.home_portfolio_total),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
        if (portfolio != null) {
            Text(
                text = portfolio.totalLabel,
                style = CoineProTextStyles.Balance,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = portfolio.changeLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = if (portfolio.isUp) CoineProColors.Buy else CoineProColors.Sell,
            )
        } else {
            // An account with no balance yet gets an em dash at hero size rather than a zero. A
            // rendered 0.00 is a claim about the account; the dash is a claim about the data.
            Text(
                text = stringResource(R.string.home_value_missing),
                style = CoineProTextStyles.Balance,
                color = CoineProColors.TextMuted,
            )
        }
        Spacer(Modifier.height(6.dp))
        ConnectionRow(state)
    }
}

/* ------------------------------------------------------------------ market */

@Composable
private fun MarketCard(quotes: List<MarketQuote>) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_market_title),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        quotes.forEachIndexed { index, quote ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(CoineProColors.Border),
                )
            }
            QuoteRow(quote)
        }
    }
}

@Composable
private fun QuoteRow(quote: MarketQuote) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp)
            .clearAndSetSemantics { contentDescription = quote.instrument.symbol },
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoineProAssetToken(
            label = quote.instrument.displayName.take(1).uppercase(),
            tint = CoineProColors.assetTint(quote.instrument.symbol),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = quote.instrument.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = BidiText.isolateLtr(quote.instrument.symbol),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = MarketNumberFormatter.price(quote.price, quote.decimals()),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            ChangeText(quote)
        }
    }
}

/** The 24-hour move, or the staleness marker when the feed has stopped moving. */
@Composable
private fun ChangeText(quote: MarketQuote) {
    val change = quote.changePercent
    when {
        change != null -> Text(
            text = MarketNumberFormatter.signedPercent(change),
            style = MaterialTheme.typography.labelSmall,
            color = if (change >= 0) CoineProColors.Buy else CoineProColors.Sell,
            fontWeight = FontWeight.Normal,
        )
        // No dash standing in for zero: a missing change is reported as missing, and a stale price
        // says so, because a stale quote drawn like a live one is the failure that costs money.
        quote.isStale -> Text(
            text = stringResource(R.string.home_quote_stale),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.Warning,
            fontWeight = FontWeight.Normal,
        )
        else -> Text(
            text = stringResource(R.string.home_value_missing),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            fontWeight = FontWeight.Normal,
        )
    }
}

@Composable
private fun EmptyMarket(state: MarketDataState, onRetry: () -> Unit) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(
                if (state.connection == MarketConnectionState.CONNECTING) {
                    R.string.home_market_connecting
                } else {
                    R.string.home_market_empty
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextSecondary,
        )
        if (state.connection == MarketConnectionState.OFFLINE ||
            state.connection == MarketConnectionState.DEGRADED
        ) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.home_retry), color = CoineProColors.Gold)
            }
        }
    }
}

/* ------------------------------------------------------------------ signals */

@Composable
private fun SignalsCard(signals: List<HomeSignal>, onOpenSignal: (Long) -> Unit) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.home_signals_title),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
        )
        Spacer(Modifier.height(6.dp))
        signals.forEachIndexed { index, signal ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(CoineProColors.Border),
                )
            }
            Row(
                // The whole row is the target rather than a chevron, so the touch area matches what
                // the reader sees.
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSignal(signal.id) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = signal.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = CoineProColors.TextPrimary,
                    )
                    Text(
                        text = stringResource(
                            R.string.home_signal_levels,
                            signal.entryLabel,
                            signal.stopLabel,
                            signal.targetLabel,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.TextMuted,
                        fontWeight = FontWeight.Normal,
                    )
                }
                signal.progressLabel?.let { progress ->
                    Text(
                        text = progress,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (signal.isUp) CoineProColors.Buy else CoineProColors.Sell,
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ assistant */

@Composable
private fun AssistantCard(briefing: HomeBriefing) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoineProAgentOrb(size = 22.dp)
            Text(
                text = stringResource(R.string.home_agent_name),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = CoineProColors.TextPrimary,
            )
            val stamp = when (briefing) {
                is HomeBriefing.Ready -> briefing.ageLabel
                HomeBriefing.Working -> stringResource(R.string.home_agent_working)
                else -> null
            }
            if (stamp != null) {
                Text(
                    text = stamp,
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    fontWeight = FontWeight.Normal,
                )
            }
        }

        if (briefing is HomeBriefing.Working) {
            Spacer(Modifier.height(12.dp))
            CoineProStreamingBar(Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(10.dp))
        when (briefing) {
            // Server text, rendered as written. The client does not rewrite, summarise or
            // translate a market claim it did not make.
            is HomeBriefing.Ready -> Text(
                text = briefing.body,
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )

            HomeBriefing.Working -> Text(
                text = stringResource(R.string.home_agent_working_body),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )

            HomeBriefing.Resting -> Text(
                text = stringResource(R.string.home_agent_resting_body),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )

            is HomeBriefing.Unavailable -> Text(
                text = briefing.reason ?: stringResource(R.string.home_agent_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.Warning,
            )
        }
    }
}

/* ------------------------------------------------------------------ chrome */

@Composable
private fun ConnectionRow(state: MarketDataState) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(state.connectionColour(), MaterialTheme.shapes.extraSmall),
            )
            Text(
                text = stringResource(state.connectionLabel()),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
            )
        }
        if (state.origin == MarketDataOrigin.CACHE && state.quotes.isNotEmpty()) {
            Text(
                text = cacheLabel(state.cacheStoredAtEpochMillis),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun cacheLabel(epochMillis: Long?): String {
    val value = epochMillis ?: return stringResource(R.string.home_cache_unknown)
    val formatted = Instant.ofEpochMilli(value)
        .atZone(ZoneId.systemDefault())
        .format(cacheTimeFormatter)
    return stringResource(R.string.home_cache_stored, BidiText.isolateLtr(formatted))
}

/* ------------------------------------------------------------------ helpers */

private fun MarketQuote.decimals(): Int = when (instrument.symbol) {
    "XAUUSD", "XAGUSD" -> 2
    else -> if (price >= 1_000) 2 else if (price >= 1) 4 else 6
}

private fun MarketDataState.connectionLabel(): Int = when {
    origin == MarketDataOrigin.CACHE && connection == MarketConnectionState.CONNECTING ->
        R.string.home_status_cached_refreshing
    origin == MarketDataOrigin.CACHE -> R.string.home_status_cached
    connection == MarketConnectionState.IDLE -> R.string.home_status_idle
    connection == MarketConnectionState.CONNECTING -> R.string.home_status_connecting
    connection == MarketConnectionState.LIVE -> R.string.home_status_live
    connection == MarketConnectionState.DEGRADED -> R.string.home_status_degraded
    else -> R.string.home_status_offline
}

private fun MarketDataState.connectionColour() = when {
    origin == MarketDataOrigin.CACHE -> CoineProColors.Warning
    connection == MarketConnectionState.LIVE -> CoineProColors.Buy
    connection == MarketConnectionState.DEGRADED -> CoineProColors.Warning
    connection == MarketConnectionState.OFFLINE -> CoineProColors.Sell
    else -> CoineProColors.TextMuted
}

private fun MarketDataState.recoveryNote(): Int? = when {
    lastError.isNullOrBlank() -> null
    connection == MarketConnectionState.LIVE -> null
    origin == MarketDataOrigin.CACHE -> R.string.home_note_refresh_failed
    else -> R.string.home_note_stream_recovering
}

private fun marketRank(quote: MarketQuote): Int = when (quote.instrument.symbol) {
    "BTCUSDT" -> 0
    "ETHUSDT" -> 1
    "SOLUSDT" -> 2
    "XAUUSD" -> 3
    "XAGUSD" -> 4
    else -> 10
}
