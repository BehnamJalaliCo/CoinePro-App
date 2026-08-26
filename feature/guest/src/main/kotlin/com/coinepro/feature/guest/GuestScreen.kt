package com.coinepro.feature.guest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProLockup
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.guest.GuestController
import com.coinepro.core.guest.GuestHeadline
import com.coinepro.core.guest.GuestNewsState
import com.coinepro.core.guest.GuestPricesState
import com.coinepro.core.guest.GuestQuote

/**
 * What the app looks like before anyone has signed in.
 *
 * The app used to open on a sign-in form. That is the wrong first screen for this product: a reader
 * arriving from a link has no idea yet whether the thing is worth an account, and a password field
 * is a question asked before any reason to answer it. So the first screen is the market — real
 * prices, moving, from TradeYar's public feed — with the account explained underneath rather than
 * demanded on top.
 *
 * It is a real screen and not a teaser. The prices are the same numbers the web site shows, the
 * headlines are the published ones, and nothing here is blurred or truncated to make a point.
 * Something dressed up to look withheld is an advertisement; this is the product, as much of it as
 * can honestly be given away.
 */
@Composable
fun GuestScreen(
    controller: GuestController,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prices by controller.prices.collectAsStateWithLifecycle()
    val news by controller.news.collectAsStateWithLifecycle()

    // Started and stopped with the screen rather than the process. A poll that outlives the screen
    // is a request nobody is looking at, on a connection somebody is paying for.
    DisposableEffect(controller) {
        controller.start()
        onDispose(controller::stop)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(CoineProColors.Stage),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(CoineProSpacing.Two),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            ) {
                CoineProLockup(
                    markSize = 72.dp,
                    wordmarkWidth = 150.dp,
                    contentDescription = stringResource(R.string.guest_wordmark_description),
                )
                Text(
                    text = stringResource(R.string.guest_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        item { PricesHeader(prices) }

        when (val current = prices) {
            GuestPricesState.Loading -> item { CoineProThinkingDots() }
            is GuestPricesState.Ready -> items(current.prices.quotes, key = GuestQuote::symbol) { quote ->
                QuoteRow(quote)
            }
            is GuestPricesState.Unavailable -> item {
                Text(
                    text = current.reason ?: stringResource(R.string.guest_prices_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextMuted,
                )
            }
        }

        item { MembershipGate(onSignIn = onSignIn) }

        item {
            Text(
                text = stringResource(R.string.guest_news_title),
                style = MaterialTheme.typography.titleMedium,
                color = CoineProColors.TextPrimary,
            )
        }
        when (val current = news) {
            GuestNewsState.Loading -> item { CoineProThinkingDots() }
            is GuestNewsState.Ready -> items(current.headlines, key = GuestHeadline::slug) { headline ->
                HeadlineRow(headline)
            }
            is GuestNewsState.Unavailable -> item {
                Text(
                    text = current.reason ?: stringResource(R.string.guest_news_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextMuted,
                )
            }
        }
    }
}

/**
 * The market heading, carrying the feed's own freshness verdict.
 *
 * The staleness flag is the server's and is stated plainly rather than being softened into a
 * timestamp. A guest looking at a price list has no other way to know the feed has stopped, and a
 * number that is quietly four minutes old is the one thing on this screen that could cost them
 * money.
 */
@Composable
private fun PricesHeader(state: GuestPricesState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.guest_market_title),
            style = MaterialTheme.typography.titleMedium,
            color = CoineProColors.TextPrimary,
        )
        if (state is GuestPricesState.Ready && state.prices.stale) {
            Text(
                text = stringResource(R.string.guest_prices_stale),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.Warning,
            )
        }
    }
}

@Composable
private fun QuoteRow(quote: GuestQuote) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoineProAssetLogo(symbol = quote.symbol, size = 32.dp)
                Text(
                    // Isolated: a Latin ticker inside a right-to-left paragraph reorders without it,
                    // and BTCUSDT becomes USDTBTC on the reader's screen.
                    text = BidiText.isolateLtr(quote.symbol),
                    style = MaterialTheme.typography.bodyLarge,
                    color = CoineProColors.TextPrimary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    // `price` isolates its own output, so there is no second isolate here. Nesting
                    // two would be harmless but would say the author was unsure which one worked.
                    text = MarketNumberFormatter.priceAuto(quote.price),
                    style = MaterialTheme.typography.bodyLarge,
                    color = CoineProColors.TextPrimary,
                )
                // Null is drawn as nothing rather than as zero. The server omits the key when it
                // does not know, and a zero would draw a flat day it never claimed.
                quote.changePercent24h?.let { change ->
                    Text(
                        text = MarketNumberFormatter.signedPercent(change),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (change >= 0) CoineProColors.Buy else CoineProColors.Sell,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeadlineRow(headline: GuestHeadline) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half)) {
            Text(
                text = headline.title,
                style = MaterialTheme.typography.bodyLarge,
                color = CoineProColors.TextPrimary,
            )
            headline.summary?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }
            headline.source?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
    }
}
