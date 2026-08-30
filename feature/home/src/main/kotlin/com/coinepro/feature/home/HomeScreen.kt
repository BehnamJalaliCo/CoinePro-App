package com.coinepro.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProAgentOrb
import com.coinepro.core.designsystem.CoineProAssetLogo
import com.coinepro.core.designsystem.CoineProAssetToken
import com.coinepro.core.designsystem.CoineProAvatar
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProMarketRow
import com.coinepro.core.designsystem.CoineProPrimaryButton
import com.coinepro.core.designsystem.CoineProPrivacy
import com.coinepro.core.designsystem.CoineProPullToRefresh
import com.coinepro.core.designsystem.CoineProSecondaryButton
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProSparkline
import com.coinepro.core.designsystem.CoineProStreamingBar
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.rememberCoineProHaptics
import com.coinepro.core.designsystem.CoineProTeachingStrip
import com.coinepro.core.designsystem.TeachingSurface
import com.coinepro.core.marketdata.MarketConnectionState
import com.coinepro.core.marketdata.MarketDataOrigin
import com.coinepro.core.marketdata.MarketDataState
import com.coinepro.core.model.AvatarSpec
import com.coinepro.core.model.MarketPlatform
import com.coinepro.core.model.MarketQuote
import java.time.Instant
import java.time.ZoneId



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
    displayName: String? = null,
    briefing: HomeBriefing = HomeBriefing.Resting,
    portfolio: HomePortfolio? = null,
    subscription: HomeSubscription? = null,
    openSignals: List<HomeSignal> = emptyList(),
    onGenerateSignal: () -> Unit = {},
    onSendChart: () -> Unit = {},
    onOpenMarket: () -> Unit = {},
    onOpenSignal: (Long) -> Unit = {},
    /**
     * The reader's chosen avatar, and where tapping it goes.
     *
     * The greeting row used to open a dropdown of account actions from a lettered disc. Those
     * actions moved to the profile page, which is a better home for them — verification, alerts,
     * safety, sign-out and deletion are a list, and a list belongs on a page rather than in a menu
     * that shows four of them at a time. What is left here is the avatar itself, which is now the
     * reader's own picture rather than their initial, and it is the way in.
     */
    avatar: AvatarSpec = AvatarSpec.Default,
    onOpenProfile: (() -> Unit)? = null,
    /**
     * The toolkit and the activity log, which used to be bottom-navigation tabs.
     *
     * They moved here when Markets and Chart took their places in the bar. Both are places a
     * reader goes deliberately rather than surfaces they live in, and Home is where somebody looks
     * when they are deciding what to do next.
     */
    onOpenTools: (() -> Unit)? = null,
    onOpenActivity: (() -> Unit)? = null,
    /** The headlines. See `ShortcutRow` for why this earned a slot. */
    onOpenNews: (() -> Unit)? = null,
    /** The reader's own list, oldest first. Empty hides the card rather than showing a placeholder. */
    watchlist: List<String> = emptyList(),
    onToggleWatch: ((String) -> Unit)? = null,
    platforms: List<MarketPlatform> = emptyList(),
    activePlatform: MarketPlatform? = null,
    onSelectPlatform: (MarketPlatform) -> Unit = {},
    /**
     * The symbols this screen is actually showing.
     *
     * TradeYar's crypto scope is 441 markets and a bare price subscription takes every one of
     * them — several hundred updates a second into a phone rendering a dozen rows. Their team
     * declined to cap it server-side, correctly, so the cap is here: the screen knows what it is
     * drawing and nothing else does.
     */
    onVisibleSymbols: (Set<String>) -> Unit = {},
    /** Opens the chart for a market row. Null leaves the card inert — see `MarketRow` in search. */
    onOpenSymbol: ((String) -> Unit)? = null,
    /**
     * Whether the reader has asked for their money not to be drawn.
     *
     * Read from the profile store rather than held here, so the choice survives leaving the screen
     * and closing the app — somebody who hides their balance in an office wants it hidden the next
     * time they open the app in that office.
     */
    balanceHidden: Boolean = false,
    onToggleBalanceHidden: (() -> Unit)? = null,
    /**
     * The portfolio, which the balance is a summary of.
     *
     * Home used to print every holding under the balance — a card that grew with the account and
     * was, on a real one, the second-largest thing on the page. The portfolio screen already draws
     * them with an equity curve, per-symbol attribution and a monthly breakdown; home now says how
     * many there are and goes there.
     */
    onOpenPortfolio: (() -> Unit)? = null,
) {
    val quotes = state.quotes.values.sortedWith(
        compareBy<MarketQuote>({ marketRank(it) }, { it.instrument.symbol }),
    )

    // The rows this screen actually draws, and only those.
    //
    // It used to be every symbol in `state.quotes`, which on TradeYar is over four hundred markets
    // — precisely the flood this parameter's own note says it exists to prevent. The card renders
    // six. Taken in the same order the card takes them, so the set matches what is on screen rather
    // than a different six.
    //
    // Empty is left alone rather than sent. `MarketDataController.webSocketUrl` reads an empty
    // subscription as "the whole universe", so narrowing to nothing is the widest possible request
    // — and nothing is exactly what this list holds on the first frame after a platform switch,
    // which is the one moment the narrowing matters most.
    val watched = quotes.filter { it.instrument.symbol in watchlist }
        .sortedBy { watchlist.indexOf(it.instrument.symbol) }
    val visible = (watched + quotes.filterNot { it.instrument.symbol in watchlist })
        .take(HOME_MARKET_ROWS)
        .map { it.instrument.symbol }
        .toSet()
    // Keyed on the set, so this fires when the list of markets changes and not on every price tick.
    // Subscribing is a socket reconnect; doing it per tick would leave the feed permanently down.
    androidx.compose.runtime.LaunchedEffect(visible) {
        if (visible.isNotEmpty()) onVisibleSymbols(visible)
    }

    // The gesture calls the same function the error card's retry button does. Home is the screen a
    // reader opens and stares at, so it is the one where the reflex to tug is strongest — and it
    // was the one with no answer to it at all, because the only retry lived inside an error state
    // the reader only sees when something is already wrong.
    CoineProPullToRefresh(
        refreshing = state.connection == MarketConnectionState.CONNECTING,
        onRefresh = onRetry,
        modifier = Modifier.fillMaxSize().background(CoineProColors.Stage),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = CoineProSpacing.Gutter,
                vertical = CoineProSpacing.Gutter,
            ),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Stack),
        ) {
            // What this screen is, once, for a reader who has just installed the app. See
            // `CoineProTeachingStrip`: it puts itself away for good and leaves a way back.
            item { CoineProTeachingStrip(TeachingSurface.HOME, gutter = false) }
            if (displayName != null) {
                item { GreetingRow(displayName, avatar, onOpenProfile) }
            }

            // Only when the build actually serves both. A one-option switch is a label pretending to be
            // a control, and it would take the top of the screen to say nothing.
            if (platforms.size > 1 && activePlatform != null) {
                item {
                    CoineProSegmentedControl(
                        options = platforms.map { it to stringResource(it.labelRes()) },
                        selected = activePlatform,
                        onSelect = onSelectPlatform,
                    )
                }
            }

            item {
                BalanceBlock(
                    portfolio = portfolio,
                    state = state,
                    hidden = balanceHidden,
                    onToggleHidden = onToggleBalanceHidden,
                    onOpenPortfolio = onOpenPortfolio,
                )
            }

            if (onOpenTools != null || onOpenActivity != null || onOpenNews != null) {
                item {
                    ShortcutRow(
                        onOpenTools = onOpenTools,
                        onOpenActivity = onOpenActivity,
                        onOpenNews = onOpenNews,
                    )
                }
            }

            item {
                // Three pills whose only difference used to be a word. A reader choosing among
                // them had to read all three every time; with the glyphs the choice is a
                // recognition, and the one-word labels stop carrying the whole burden.
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CoineProPrimaryButton(
                        text = stringResource(R.string.home_action_signal),
                        onClick = onGenerateSignal,
                        modifier = Modifier.weight(1f),
                        // The sparkle, not the candlesticks. This pill opens the AI studio — that
                        // is what generating a signal means here — and the signals glyph is a pair
                        // of candles, which is what the pill beside it already shows. Two of three
                        // buttons carrying the same picture is worse than none of them carrying
                        // one: it tells the reader they are the same action.
                        icon = CoineProIcons.Ai,
                    )
                    CoineProSecondaryButton(
                        text = stringResource(R.string.home_action_chart),
                        onClick = onSendChart,
                        modifier = Modifier.weight(1f),
                        icon = CoineProIcons.Chart,
                    )
                    CoineProSecondaryButton(
                        text = stringResource(R.string.home_action_market),
                        onClick = onOpenMarket,
                        modifier = Modifier.weight(1f),
                        icon = CoineProIcons.Markets,
                    )
                }
            }

            // Only in the week it matters. A subscription that is healthy is not news, and a card
            // on the home screen restating it every day for eleven months is 128dp spent saying
            // nothing. The state this card exists for is the one where the plan is about to end.
            subscription?.takeIf { it.endingSoon }?.let { item { SubscriptionCard(it) } }

            if (quotes.isEmpty()) {
                item { EmptyMarket(state = state, onRetry = onRetry) }
            } else {
                // One card, not two. The watchlist used to be a second card above this one, so a
                // reader with three starred symbols scrolled two headings and two card edges
                // through the same rows twice. Starred symbols lead, in the order they were
                // starred, and the rest follow — which is the same information in one object.
                //
                // Capped, and the cap is the point: eight rows at eighty points each was the
                // largest thing on the page, and the markets screen holds all of them, denser,
                // with a filter and a search. The footer says so and goes there.
                val rest = quotes.filterNot { it.instrument.symbol in watchlist }
                item {
                    MarketCard(
                        quotes = (watched + rest).take(HOME_MARKET_ROWS),
                        onOpenSymbol = onOpenSymbol,
                        watchlist = watchlist,
                        onToggleWatch = onToggleWatch,
                        more = (watched.size + rest.size - HOME_MARKET_ROWS).takeIf { it > 0 },
                        onOpenMarket = onOpenMarket,
                    )
                }
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
}

/* ------------------------------------------------------------------ greeting */

@Composable
private fun GreetingRow(
    displayName: String,
    avatar: AvatarSpec,
    onOpenProfile: (() -> Unit)?,
) {
    // Resolved out here: the semantics block is not a composable scope.
    val profileLabel = stringResource(R.string.home_menu_account)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.home_greeting, displayName),
            style = MaterialTheme.typography.bodyLarge,
            color = CoineProColors.TextSecondary,
        )
        // The one place on the screen that says whose account this is, and — since Home carries no
        // top bar — the way into everything the account can do.
        CoineProAvatar(
            spec = avatar,
            initial = displayName.trim().take(1),
            size = 38.dp,
            modifier = if (onOpenProfile == null) {
                Modifier
            } else {
                Modifier
                    .clickable(onClick = onOpenProfile)
                    .semantics { contentDescription = profileLabel }
            },
        )
    }
}

/* ------------------------------------------------------------------ balance */

@Composable
private fun BalanceBlock(
    portfolio: HomePortfolio?,
    state: MarketDataState,
    hidden: Boolean,
    onToggleHidden: (() -> Unit)?,
    onOpenPortfolio: (() -> Unit)? = null,
) {
    Column(
        // No horizontal inset. Four points here put the balance block on a third left edge,
        // between the screen gutter and the cards' own content — three different edges down one
        // scroll, which is the kind of thing that reads as "unfinished" without being nameable.
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            Text(
                text = stringResource(R.string.home_portfolio_total),
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextSecondary,
            )
            // Beside the label rather than beside the figure. The eye is a control and the balance
            // is the largest thing on the screen; putting a 20dp button next to 40sp type makes the
            // button look like a mistake, and puts a tap target where a reader's eye has to rest.
            if (onToggleHidden != null) {
                val haptics = rememberCoineProHaptics()
                val label = stringResource(
                    if (hidden) R.string.home_balance_show else R.string.home_balance_hide,
                )
                Icon(
                    painter = painterResource(
                        if (hidden) CoineProIcons.Hidden else CoineProIcons.Visible,
                    ),
                    contentDescription = label,
                    modifier = Modifier
                        // Glyph 18, target 48. See the star in `CoineProMarketRow`.
                        .minimumInteractiveComponentSize()
                        .clip(CoineProShapes.small)
                        .clickable {
                            haptics.select()
                            onToggleHidden()
                        }
                        .padding(4.dp)
                        .size(18.dp),
                    tint = CoineProColors.TextMuted,
                )
            }
        }
        if (portfolio != null) {
            Text(
                text = CoineProPrivacy.mask(portfolio.totalLabel, hidden),
                style = CoineProTextStyles.Balance,
                color = CoineProColors.TextPrimary,
            )
            // Under the figure, not beside it: the balance is the largest thing on the screen and
            // a line at its shoulder would compete with it. Two points are not a curve — a segment
            // between two closed trades says "it went up" with the authority of a chart — so the
            // line waits until there is a shape to draw.
            if (!hidden && portfolio.equity.size >= MIN_EQUITY_POINTS) {
                CoineProSparkline(
                    values = portfolio.equity,
                    modifier = Modifier
                        .padding(top = 2.dp, bottom = 2.dp)
                        .fillMaxWidth(0.62f)
                        .height(30.dp),
                    colour = if (portfolio.isUp) CoineProColors.Buy else CoineProColors.Sell,
                )
            }
            Text(
                text = CoineProPrivacy.mask(portfolio.changeLabel, hidden),
                style = MaterialTheme.typography.bodyMedium,
                // Masked, the line is no longer a claim about direction, so it loses the direction's
                // colour too. A green row of dots is still telling somebody over the shoulder that
                // the day went well.
                color = when {
                    hidden -> CoineProColors.TextMuted
                    portfolio.isUp -> CoineProColors.Buy
                    else -> CoineProColors.Sell
                },
            )
        } else {
            // An account with no balance yet gets a dash rather than a zero, because a rendered
            // `0.00` is a claim about the account and the dash is a claim about the data. What it
            // does **not** get is the hero style.
            //
            // At `Balance` the em dash sat on the baseline of a 46-point line box, so what a reader
            // actually saw was a heading, a gap the height of a line, and a stray dash floating
            // below it with nothing between the two — a rendering fault, not an answer. It is now
            // set at the size of the figure it stands in for, with a sentence saying why it is
            // empty. A missing number is worth one line of explanation; it is not worth the largest
            // type on the screen.
            Text(
                text = stringResource(R.string.home_value_missing),
                style = MaterialTheme.typography.titleLarge,
                color = CoineProColors.TextMuted,
            )
            Text(
                text = stringResource(R.string.home_value_missing_note),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
            )
        }
        // One line where a card used to be. It says how many positions the figure above is the
        // sum of, and goes to the screen that draws them — which is a better home for them than
        // this one, and was already built.
        if (portfolio != null && portfolio.holdings.isNotEmpty() && onOpenPortfolio != null) {
            val haptics = rememberCoineProHaptics()
            Row(
                modifier = Modifier
                    .clip(CoineProShapes.small)
                    .clickable {
                        haptics.select()
                        onOpenPortfolio()
                    }
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(CoineProIcons.ChevronForward),
                    contentDescription = null,
                    tint = CoineProColors.TextMuted,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = stringResource(
                        R.string.home_holdings_count,
                        portfolio.holdings.size.toPersianDigits(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextSecondary,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        ConnectionRow(state)
    }
}

/* ------------------------------------------------------------ subscription */

@Composable
private fun SubscriptionCard(subscription: HomeSubscription) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardLabel(stringResource(R.string.home_subscription_title))
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.Row),
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // The server's own name for the plan, as written. Where it named none, the
                    // membership itself is the only true thing left to say.
                    text = subscription.planLabel
                        ?: stringResource(R.string.home_subscription_active),
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                )
                if (subscription.isVip) {
                    Text(
                        text = stringResource(R.string.home_subscription_vip),
                        style = MaterialTheme.typography.labelSmall,
                        color = CoineProColors.Accent,
                        fontWeight = FontWeight.Normal,
                    )
                }
            }
            subscription.expiresLabel?.let { expires ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = expires,
                        style = CoineProTextStyles.RowFigure,
                        color = CoineProColors.TextPrimary,
                    )
                    subscription.daysRemaining?.let { days ->
                        Text(
                            text = pluralStringResource(R.plurals.home_subscription_days, days, days),
                            style = MaterialTheme.typography.labelSmall,
                            // Warning rather than muted only near the end: colouring every renewal
                            // date as a problem would make the one that is a problem invisible.
                            color = if (subscription.endingSoon) {
                                CoineProColors.Warning
                            } else {
                                CoineProColors.TextMuted
                            },
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

/* ------------------------------------------------------------------ market */

@Composable
private fun MarketCard(
    quotes: List<MarketQuote>,
    onOpenSymbol: ((String) -> Unit)?,
    titleRes: Int = R.string.home_market_title,
    watchlist: List<String> = emptyList(),
    onToggleWatch: ((String) -> Unit)? = null,
    /** How many markets are not on this card. Null when the card is showing all of them. */
    more: Int? = null,
    onOpenMarket: (() -> Unit)? = null,
) {
    CoineProCard(modifier = Modifier.fillMaxWidth()) {
        CardLabel(stringResource(titleRes))
        quotes.forEachIndexed { index, quote ->
            if (index > 0) RowDivider()
            QuoteRow(quote, onOpenSymbol, watchlist, onToggleWatch)
        }
        // The way out, and it states the number rather than saying «بیشتر». A card that is showing
        // six of two hundred markets and does not say so reads as a card showing the market.
        if (more != null && onOpenMarket != null) {
            RowDivider()
            val haptics = rememberCoineProHaptics()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptics.select()
                        onOpenMarket()
                    }
                    .padding(vertical = CoineProSpacing.Row),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_market_more, more.toPersianDigits()),
                    style = MaterialTheme.typography.labelMedium,
                    color = CoineProColors.TextSecondary,
                )
                Icon(
                    painter = painterResource(CoineProIcons.ChevronForward),
                    contentDescription = null,
                    tint = CoineProColors.TextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * How many instruments the home card carries.
 *
 * Six is what a reader takes in without scrolling past it, and the markets screen — one tap away
 * through the footer — holds every one of them with a filter, a search and a sparkline per row.
 * Eight rows at eighty points was the single largest object on the home screen.
 */
private const val HOME_MARKET_ROWS = 6

@Composable
private fun QuoteRow(
    quote: MarketQuote,
    onOpenSymbol: ((String) -> Unit)?,
    watchlist: List<String>,
    onToggleWatch: ((String) -> Unit)?,
) {
    val stale = stringResource(R.string.home_quote_stale)
    CoineProMarketRow(
        symbol = quote.instrument.symbol,
        starred = onToggleWatch?.let { quote.instrument.symbol in watchlist },
        onToggleStar = onToggleWatch?.let { toggle -> { toggle(quote.instrument.symbol) } },
        title = AnnotatedString(quote.instrument.displayName),
        subtitle = AnnotatedString(BidiText.isolateLtr(quote.instrument.symbol)),
        price = MarketNumberFormatter.price(quote.price, quote.decimals()),
        changePercent = quote.changePercent,
        // No dash standing in for zero: a missing change is reported as missing, and a stale price
        // says so, because a stale quote drawn like a live one is the failure that costs money.
        trailingNote = stale.takeIf { quote.changePercent == null && quote.isStale },
        trailingNoteColor = CoineProColors.Warning,
        onClick = onOpenSymbol?.let { open -> { open(quote.instrument.symbol) } },
    )
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
        CardLabel(stringResource(R.string.home_signals_title))
        signals.forEachIndexed { index, signal ->
            if (index > 0) RowDivider()
            Row(
                // The whole row is the target rather than a chevron, so the touch area matches what
                // the reader sees.
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenSignal(signal.id) }
                    .padding(vertical = CoineProSpacing.Row),
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
    val formatted = PersianDateTime.moment(Instant.ofEpochMilli(value))
    return stringResource(R.string.home_cache_stored, formatted)
}

/** The quiet label at the top of a card. */
@Composable
private fun CardLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = CoineProColors.TextSecondary,
    )
    Spacer(Modifier.height(CoineProSpacing.One))
}

/** A hairline between rows *inside* one card. Cards themselves are never divided by rules. */
@Composable
private fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(CoineProColors.Border),
    )
}

/* ------------------------------------------------------------------ helpers */

@StringRes
private fun MarketPlatform.labelRes(): Int = when (this) {
    MarketPlatform.TRADEYAR -> R.string.home_platform_crypto
    MarketPlatform.COINEPRO_FX -> R.string.home_platform_forex
}

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

@Composable
@ReadOnlyComposable
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

/**
 * The two destinations that lost their tab.
 *
 * A pair of wide, plainly-labelled rows rather than icons: they are visited rarely enough that a
 * glyph alone would not be recognised, and often enough that burying them in the overflow menu
 * would be hiding them.
 */
@Composable
private fun ShortcutRow(
    onOpenTools: (() -> Unit)?,
    onOpenActivity: (() -> Unit)?,
    onOpenNews: (() -> Unit)?,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        onOpenTools?.let {
            Shortcut(
                label = stringResource(R.string.home_shortcut_tools),
                icon = DesignR.drawable.nav_tools,
                onClick = it,
                modifier = Modifier.weight(1f),
            )
        }
        onOpenActivity?.let {
            Shortcut(
                label = stringResource(R.string.home_shortcut_activity),
                icon = DesignR.drawable.nav_activity,
                onClick = it,
                modifier = Modifier.weight(1f),
            )
        }
        // The third slot, and the reason it exists: news had exactly one entry point in the whole
        // app — the fourth card down a toolkit screen that is itself three thousand points long —
        // while the guest home printed twelve headlines in full at the bottom of a page nobody
        // scrolled to. One is now a place you go, from here.
        onOpenNews?.let {
            Shortcut(
                label = stringResource(R.string.home_shortcut_news),
                icon = CoineProIcons.News,
                onClick = it,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Shortcut(label: String, icon: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            // Twelve of vertical padding around an 18dp glyph draws a 42dp row, and this is a
            // control on the first screen the app opens on.
            .minimumInteractiveComponentSize()
            .clip(CoineProShapes.small)
            // Elevated with a hairline, not `Surface` with nothing.
            //
            // These three sit directly on the page rather than inside a card, and `Surface` on the
            // stage measures 1.07:1 in the dark theme and 1.04:1 in the light — a fill that is
            // there in the file and not on the panel. With no border either, the row read as three
            // labels floating on the page rather than three things to press.
            .background(CoineProColors.SurfaceElevated)
            .border(1.dp, CoineProColors.BorderSubtle, CoineProShapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.OneHalf),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = CoineProColors.TextSecondary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextPrimary,
        )
    }
}

/**
 * The shortest history worth drawing as a line.
 *
 * Two points make a straight segment, which reads as a trend with none of a trend's evidence. Five
 * is where the shape starts carrying information the change figure beside it does not already have.
 */
private const val MIN_EQUITY_POINTS = 5
