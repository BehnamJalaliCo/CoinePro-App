package com.coinepro.feature.guest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProAvatar
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProMarketRow
import com.coinepro.core.designsystem.ProChartWordmark
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProThinkingDots
import com.coinepro.core.guest.GuestController
import com.coinepro.core.guest.GuestHeadline
import com.coinepro.core.guest.GuestMembershipState
import com.coinepro.core.guest.GuestNewsState
import com.coinepro.core.guest.GuestPricesState
import com.coinepro.core.guest.GuestQuote
import com.coinepro.core.guest.GuestTrackRecord
import com.coinepro.core.guest.GuestTrackRecordState
import com.coinepro.core.model.AvatarSpec

/**
 * Home, for somebody who has not signed in.
 *
 * It is Home — not a landing page in front of the app. The bottom bar under it is the same bar, the
 * markets tab beside it lists the same several hundred instruments, and the chart it opens is the
 * same chart. What this screen is, is the *first* of those surfaces: the reader's own corner with
 * their avatar in it, the market moving underneath, what the published signals actually did, and
 * the account offered once.
 *
 * Two things it deliberately does **not** do.
 *
 * It does not demand an account. The membership card states what an account adds and then stops;
 * there is no second, more insistent version of it further down and no interstitial anywhere in the
 * guest experience. That is the owner's rule for this product — «به زور کسی رو ما ثبت نام نمی‌کنیم»
 * — and it is also the only rule that survives contact with a reader who arrived from a link and is
 * deciding, in about eight seconds, whether this is a serious app.
 *
 * And it no longer carries the community section. A member count and a list of Telegram channels is
 * a crowd shown to somebody who has not yet been told what the crowd is for, and it was taking the
 * place of the market on the one screen where the market is the argument.
 */
@Composable
fun GuestScreen(
    controller: GuestController,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    /** The reader's own chosen avatar. A guest has one too — that is the point of the profile. */
    avatar: AvatarSpec = AvatarSpec.Default,
    onOpenProfile: (() -> Unit)? = null,
    /** Opens the chart on a market row, exactly as the signed-in home does. */
    onOpenSymbol: ((String) -> Unit)? = null,
    /** The full market list — the several hundred this screen is showing twenty of. */
    onOpenMarket: (() -> Unit)? = null,
    /** The local toolkit: journal, paper trading, NamaScript. None of it needs an account. */
    onOpenTools: (() -> Unit)? = null,
    /**
     * The news screen, which a guest could not reach at all.
     *
     * This page used to print twelve headline cards in full — 1,700dp, about forty per cent of the
     * whole page, at the very bottom, so a guest scrolled four screens to reach them and had
     * nowhere to go afterwards. The headlines come from a public route; there was never a reason
     * for them to be trapped here.
     */
    onOpenNews: (() -> Unit)? = null,
) {
    val prices by controller.prices.collectAsStateWithLifecycle()
    val news by controller.news.collectAsStateWithLifecycle()
    val trackRecord by controller.trackRecord.collectAsStateWithLifecycle()
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
        item { ProfileHeader(avatar = avatar, onOpenProfile = onOpenProfile) }

        // The brand, on the one screen where a person may not yet know whose app this is.
        //
        // `guest_wordmark_description` has existed since this screen was written and nothing drew
        // anything for it to describe. It does now: the front door for somebody with no account is
        // exactly where a name belongs, and the mark is small enough that the prices below it are
        // still the first thing on screen.
        item {
            ProChartWordmark(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .width(GUEST_WORDMARK_WIDTH),
                contentDescription = stringResource(R.string.guest_wordmark_description),
            )
        }

        if (onOpenMarket != null || onOpenTools != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One)) {
                    onOpenMarket?.let {
                        CoineProSecondaryButton(
                            text = stringResource(R.string.guest_action_markets),
                            onClick = it,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    onOpenTools?.let {
                        CoineProSecondaryButton(
                            text = stringResource(R.string.guest_action_tools),
                            onClick = it,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        item { PricesHeader(prices) }

        when (val current = prices) {
            GuestPricesState.Loading -> item { CoineProThinkingDots() }
            // One card holding every row, not a card per row: the divider between two rows is a
            // hairline the eye crosses, where a gap between two cards is a boundary it stops at.
            // Six, not twenty. The full list is one tap away on the markets screen and is
            // denser there — sparklines, a category filter, a search. Twenty rows here was
            // 1,600dp of one page that also has to hold a track record, a membership card and
            // the news.
            is GuestPricesState.Ready -> item {
                CoineProCard(modifier = Modifier.fillMaxWidth()) {
                    current.prices.quotes.take(HOME_PRICE_ROWS).forEachIndexed { index, quote ->
                        if (index > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(CoineProColors.Border),
                            )
                        }
                        QuoteRow(quote = quote, onOpenSymbol = onOpenSymbol)
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

        item {
            Text(
                text = stringResource(R.string.guest_news_title),
                style = MaterialTheme.typography.titleMedium,
                color = CoineProColors.TextPrimary,
            )
        }
        when (val current = news) {
            GuestNewsState.Loading -> item { CoineProThinkingDots() }
            // The newest headline and a count, not twelve cards. See `onOpenNews`.
            is GuestNewsState.Ready -> current.headlines.firstOrNull()?.let { newest ->
                item { NewsTeaser(newest, current.headlines.size, onOpenNews) }
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
 * The reader's own row, at the top, where the member's greeting sits on the signed-in home.
 *
 * A guest has a profile in this app. Not a placeholder for one — a real page with their own avatar,
 * their own name if they typed one, and their own watchlist counted. That is what makes the guest
 * experience the app rather than a preview of it, and it is why this row is the first thing on the
 * screen rather than a sign-up banner.
 */
@Composable
private fun ProfileHeader(avatar: AvatarSpec, onOpenProfile: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.guest_greeting),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = CoineProColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.guest_greeting_note),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        CoineProAvatar(
            spec = avatar,
            initial = stringResource(R.string.guest_initial),
            size = 40.dp,
            contentDescription = stringResource(R.string.guest_profile_description),
            modifier = if (onOpenProfile == null) {
                Modifier
            } else {
                Modifier.clickable(onClick = onOpenProfile)
            },
        )
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
 * Tappable, and that is the substance of the guest work rather than a detail of it: the row opens
 * the same chart a member opens, on the same candles, because the public candle route runs the same
 * code path as the signed-in one. A market list a guest could look at but not open would be a
 * catalogue, not an app.
 */
@Composable
private fun QuoteRow(quote: GuestQuote, onOpenSymbol: ((String) -> Unit)?) {
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
        onClick = onOpenSymbol?.let { open -> { open(quote.symbol) } },
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
fun TrackRecordSummary(record: GuestTrackRecord, modifier: Modifier = Modifier) {
    CoineProCard(modifier = modifier.fillMaxWidth()) {
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
 * The news, as one card.
 *
 * Twelve headline cards at the foot of the guest home was the single largest thing on the page and
 * the last thing on it — so a reader scrolled past everything the app is for to reach a list they
 * then could not open. This says what the newest one is and how many there are, and goes to the
 * screen built for reading them.
 *
 * Where there is nowhere to go — a build with no news route — the card still renders the headline
 * and simply is not clickable. A teaser that led nowhere would be worse than the twelve cards.
 */
@Composable
private fun NewsTeaser(newest: GuestHeadline, total: Int, onOpenNews: (() -> Unit)?) {
    CoineProCard(
        modifier = Modifier
            .fillMaxWidth()
            .let { base -> onOpenNews?.let { base.clickable(onClick = it) } ?: base },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            ) {
                Text(
                    text = newest.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // A prose count, so Persian digits — the rule the whole app follows.
                    text = stringResource(R.string.guest_news_count, total.toPersianDigits()),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                    fontWeight = FontWeight.Normal,
                )
            }
            if (onOpenNews != null) {
                Icon(
                    painter = painterResource(CoineProIcons.ChevronForward),
                    contentDescription = null,
                    tint = CoineProColors.TextDisabled,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * How many market rows the guest home carries.
 *
 * Six is what fits above a fold without pushing everything else off the page, and the markets
 * screen holds the rest — denser, filtered and searchable.
 */
private const val HOME_PRICE_ROWS = 6

/**
 * How wide the wordmark sits at the head of the guest screen.
 *
 * Half a phone, roughly. Wide enough that the name is legible at arm's length and narrow enough
 * that it is a header rather than a splash: the reason somebody opens this screen is the prices
 * under it, and a brand that fills the first fold delays them for nothing.
 */
private val GUEST_WORDMARK_WIDTH = 176.dp
