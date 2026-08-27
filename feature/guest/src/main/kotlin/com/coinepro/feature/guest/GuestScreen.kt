package com.coinepro.feature.guest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.common.toPersianGroupedDigits
import com.coinepro.core.designsystem.CoineProMarketRow
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProLockup
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.guest.CommunityChannel
import com.coinepro.core.guest.GuestCommunity
import com.coinepro.core.guest.GuestCommunityState
import com.coinepro.core.guest.GuestController
import com.coinepro.core.guest.GuestHeadline
import com.coinepro.core.guest.GuestMembershipState
import com.coinepro.core.guest.GuestNewsState
import com.coinepro.core.guest.GuestPricesState
import com.coinepro.core.guest.GuestTrackRecord
import com.coinepro.core.guest.GuestTrackRecordState
import com.coinepro.core.guest.GuestQuote
import com.coinepro.core.guest.MemberCount

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
    val trackRecord by controller.trackRecord.collectAsStateWithLifecycle()
    val community by controller.community.collectAsStateWithLifecycle()
    val membership by controller.membership.collectAsStateWithLifecycle()

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

        // The track record goes *above* the membership card, not below it. It is the reason to
        // read the card; a card asking for a sign-up before showing what the signals did is asking
        // for trust it has not earned yet.
        when (val record = trackRecord) {
            is GuestTrackRecordState.Ready -> {
                item {
                    Text(
                        text = stringResource(R.string.guest_record_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = CoineProColors.TextPrimary,
                    )
                }
                item { TrackRecordSummary(record.record) }
            }
            // Nothing at all when the server has nothing gradeable. An empty section headed
            // "results" is worse than no section: it reads as a bot that has never traded.
            GuestTrackRecordState.Loading, GuestTrackRecordState.Unavailable -> Unit
        }

        item {
            MembershipGate(
                onSignIn = onSignIn,
                terms = (membership as? GuestMembershipState.Ready)?.terms,
            )
        }

        // The community sits under the membership card rather than over it. It is the answer to
        // "is anyone else here", which is a question a reader asks *after* they know what the
        // thing is — putting a member count first is a crowd shown to somebody who has not yet
        // been told what the crowd is for.
        when (val current = community) {
            is GuestCommunityState.Ready -> {
                item {
                    Text(
                        text = stringResource(R.string.guest_community_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = CoineProColors.TextPrimary,
                    )
                }
                item { CommunitySummary(current.community) }
            }
            // Nothing while loading, and nothing when it failed. A heading over «داده در دسترس
            // نیست» is a section that exists only to report its own absence.
            GuestCommunityState.Loading, GuestCommunityState.Unavailable -> Unit
        }

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
        when {
            state !is GuestPricesState.Ready -> Unit
            state.prices.stale -> Text(
                text = stringResource(R.string.guest_prices_stale),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.Warning,
            )
            // How many there really are. Twenty rows with nothing saying otherwise is a much
            // smaller product than the one the feed actually carries.
            else -> state.prices.universeSize?.let { total ->
                Text(
                    text = stringResource(R.string.guest_market_count, total.toPersianDigits()),
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
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

/**
 * What the published signals did, as a record rather than a promise.
 *
 * Every figure here is the server's own: the win rate is counted from rows the server marked won,
 * by its own ladder definition, and the percentages are the ones it banked. The app does not
 * recompute any of it — the route says in as many words that a client must not, and two different
 * win rates in front of one reader is worse than none.
 */
@Composable
private fun TrackRecordSummary(record: GuestTrackRecord) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            record.winRate?.let { rate ->
                Text(
                    text = stringResource(
                        R.string.guest_record_summary,
                        record.wins.toPersianDigits(),
                        record.entries.size.toPersianDigits(),
                        MarketNumberFormatter.price(rate, 1),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }
            record.entries.take(6).forEach { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = BidiText.isolateLtr(entry.symbol),
                        style = MaterialTheme.typography.bodyMedium,
                        color = CoineProColors.TextPrimary,
                    )
                    Text(
                        text = MarketNumberFormatter.signedPercent(entry.percentGain),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (entry.win) CoineProColors.Buy else CoineProColors.Sell,
                    )
                }
            }
            Text(
                text = stringResource(R.string.guest_record_note),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
    }
}

/**
 * The public channels, and how many people are in them.
 *
 * One rule governs this whole composable and it comes from the route itself: a count the server
 * could not fetch renders as «داده در دسترس نیست», never as a zero and never as a remembered
 * number. Telegram refuses often enough that this is the ordinary case rather than the edge one,
 * and a channel of fifty thousand drawn as «۰ عضو» is not a cautious understatement, it is a lie
 * about the size of the thing a reader is deciding whether to join.
 *
 * That is why [MemberCount] is a sealed type rather than a `Long?`: there is no `?: 0` to write.
 *
 * The counts are Persian digits because they are prose — people, not a market figure. The rule
 * everywhere else in this app is the opposite, and the difference is exactly that nobody converts
 * a member count into a trade.
 */
@Composable
private fun CommunitySummary(community: GuestCommunity) {
    val unknown = stringResource(R.string.guest_community_unknown)
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
            (community.total as? MemberCount.Known)?.let { total ->
                Text(
                    text = stringResource(
                        R.string.guest_community_total,
                        total.value.toPersianGroupedDigits(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }
            (community.botUsers as? MemberCount.Known)?.let { bot ->
                Text(
                    text = stringResource(
                        R.string.guest_community_bot,
                        bot.value.toPersianGroupedDigits(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                )
            }

            community.channels.forEach { channel -> ChannelRow(channel, unknown) }

            community.note?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
    }
}

/**
 * One channel. Tappable only where the server gave a link — a row that looks tappable and does
 * nothing is worse than one that plainly does not.
 */
@Composable
private fun ChannelRow(channel: CommunityChannel, unknown: String) {
    val context = LocalContext.current
    val url = channel.url
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (url == null) Modifier else Modifier.clickable { context.open(url) }),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = channel.label,
            style = MaterialTheme.typography.bodyMedium,
            color = CoineProColors.TextPrimary,
        )
        Text(
            text = when (val members = channel.members) {
                is MemberCount.Known ->
                    stringResource(
                        R.string.guest_community_members,
                        members.value.toPersianGroupedDigits(),
                    )
                MemberCount.Unavailable -> unknown
            },
            style = MaterialTheme.typography.bodySmall,
            // Muted for the unknown case rather than the same weight as a real figure: it is an
            // absence being reported, and it should not read as a number.
            color = if (channel.members is MemberCount.Known) {
                CoineProColors.TextSecondary
            } else {
                CoineProColors.TextMuted
            },
        )
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
