package com.coinepro.feature.guest

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.designsystem.CoineProMarketRow
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
                    // Smaller than it was. A logo taking a fifth of the first screen is a brand
                    // being announced; the market underneath it is the thing that does the
                    // convincing, and it should be visible without a scroll.
                    markSize = 48.dp,
                    wordmarkWidth = 118.dp,
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
            // One card holding every row, not a card per row: the divider between two rows is a
            // hairline the eye crosses, where a gap between two cards is a boundary it stops at.
            is GuestPricesState.Ready -> item {
                CoineProCard(modifier = Modifier.fillMaxWidth()) {
                    current.prices.quotes.forEachIndexed { index, quote ->
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

/**
 * One market, in the app's own row.
 *
 * The list used to be one card per quote — a stack of large blocks a reader scrolled rather than
 * scanned. A market list is read by comparison and comparison needs the rows close enough to hold
 * in one glance, so this is a single card with dense rows inside it, which is what Home already
 * does and what every exchange's list looks like.
 */
@Composable
private fun QuoteRow(quote: GuestQuote) {
    CoineProMarketRow(
        symbol = quote.symbol,
        // The ticker is the name here. The public feed carries no display name, and inventing a
        // Persian one for several hundred symbols would be a table that is wrong somewhere.
        title = AnnotatedString(BidiText.isolateLtr(quote.base())),
        subtitle = AnnotatedString(BidiText.isolateLtr(quote.symbol)),
        price = MarketNumberFormatter.priceAuto(quote.price),
        changePercent = quote.changePercent24h,
        low24h = quote.low24h,
        high24h = quote.high24h,
        rawPrice = quote.price,
    )
}

/**
 * `BTCUSDT` without its quote currency.
 *
 * Every row on this list is quoted in USDT, so repeating it on every line spends the widest column
 * saying the one thing that never varies. The full symbol stays underneath, where it is the thing
 * a reader copies into an exchange.
 */
private fun GuestQuote.base(): String = symbol.removeSuffix("USDT").ifEmpty { symbol }

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
