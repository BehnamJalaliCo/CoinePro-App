package com.coinepro.feature.papertrade

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.designsystem.CoineProCard
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProHeaderAction
import com.coinepro.core.designsystem.CoineProHeroFigure
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProListHeader
import com.coinepro.core.designsystem.CoineProReading
import com.coinepro.core.designsystem.CoineProReadingRow
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTeachingStrip
import com.coinepro.core.designsystem.TeachingSurface
import com.coinepro.core.papertrade.PaperEngine
import com.coinepro.core.papertrade.PaperQuote
import com.coinepro.core.papertrade.PaperTradeController
import com.coinepro.core.papertrade.PaperTradeUiState
import com.coinepro.core.symbols.SymbolMeta
import java.time.ZoneId

/** The five things a paper account is, in the order a session uses them. */
enum class PaperTab { TICKET, POSITIONS, ORDERS, HISTORY, RECORD }

/**
 * A paper-trading account, as its own product.
 *
 * ### What this replaced, and why the replacement is this large
 *
 * The screen before it was a symbol field, a size field, a buy/sell pair and a list. It opened at
 * the last price, closed at the last price, charged nothing, held no balance, and stated in one
 * line that it modelled no costs. That is a profit-and-loss calculator with a history, and the
 * owner's word for it — decoration — was exact. Worse than incomplete, it was *teaching*: a reader
 * who practises here learns that entering and leaving is free, and takes that habit to a market
 * that charges them the spread twice and the fee twice on every round trip.
 *
 * So the arithmetic came first and the screen follows it. There is an account with a balance the
 * reader sets, equity that moves with what is open, realised and unrealised kept apart, margin and
 * a stop-out where there is leverage. There are four order types, stops and targets on a position,
 * partial closes and reversals. There is a record built by the portfolio's own arithmetic. And
 * there is a fill model with rules a reader can read on this screen — see [PaperRulesCard], which
 * is not a disclaimer but the specification.
 *
 * ### Two things it must never do
 *
 * **Be mistakable for a real order.** The account panel, every list row and every ticket carry the
 * word in Persian, permanently, not behind a tooltip and not only on the first visit. This app has
 * real execution and real copy trading a tab away; a reader who confuses the two loses money.
 *
 * **Invent a price.** [quoteFor] and [priceFor] both read the app's one market feed. Nothing here
 * fetches. A second source would let this screen and the chart above it disagree about one
 * instrument, and the reader would have no way to tell which had lied.
 *
 * @param priceFor the last price for a symbol, from the same feed the market list reads.
 * @param quoteFor the fuller observation where the host can supply one — bid, ask and the feed's
 *   own staleness. Null falls back to [priceFor], which costs the fill rules their real spread and
 *   makes them use the assumed one — and the screen says so, rather than presenting an assumption
 *   as a quote. The app passes it: `marketState.quotes[symbol]?.asPaperQuote()`, so a reader
 *   practising here crosses the spread the venue actually quoted. Null remains supported for a
 *   host whose feed carries only a last price, and for the render tests.
 * @param onOpenSymbol opens the chart for a symbol. Null simply omits the affordance rather than
 *   showing a dead one.
 */
@Composable
fun PaperTradeScreen(
    controller: PaperTradeController,
    priceFor: (String) -> Double?,
    quoteFor: ((String) -> PaperQuote?)? = null,
    onOpenSymbol: ((String) -> Unit)? = null,
    zone: ZoneId = ZoneId.systemDefault(),
    /**
     * The platform's catalogue, for the ticket's symbol field — item 9 of the owner's list.
     *
     * The ticket took a symbol as free text, so a reader who typed «bit» had no quote and a
     * market order refused for want of a price. With the catalogue the field offers `BTCUSDT`
     * as they type, the quote line fills the moment it is picked, and the order fills at it.
     */
    markets: List<SymbolMeta> = emptyList(),
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(PaperTab.TICKET) }
    var symbol by rememberSaveable { mutableStateOf("") }
    var assumptionsOpen by rememberSaveable { mutableStateOf(false) }

    // Every symbol the book needs marking, plus whatever the ticket is looking at. Built on each
    // composition rather than remembered: the enclosing screen passes a new lambda whenever the
    // feed ticks, and a remembered map keyed on the lambda would go stale the moment a host held
    // its own reference.
    val observed = observations(state, symbol, priceFor, quoteFor)
    LaunchedEffect(observed) { controller.onQuotes(observed) }

    Column(modifier = Modifier.fillMaxSize().background(CoineProColors.Stage)) {
        CoineProListHeader(
            title = stringResource(R.string.paper_title),
            subtitle = stringResource(R.string.paper_generation, PaperFormat.count(state.book.account.generation)),
            actions = {
                CoineProHeaderAction(
                    icon = CoineProIcons.Settings,
                    label = stringResource(R.string.paper_settings),
                    onClick = { assumptionsOpen = true },
                )
            },
        )
        CoineProTeachingStrip(TeachingSurface.PAPER_TRADE)
        AccountPanel(state)
        TabChips(tab) { tab = it }

        when (tab) {
            PaperTab.TICKET -> PaperTicket(
                state = state,
                controller = controller,
                symbol = symbol,
                onSymbol = { symbol = it },
                markets = markets,
                modifier = Modifier.fillMaxSize(),
            )
            PaperTab.POSITIONS -> PaperPositions(
                state = state,
                controller = controller,
                onOpenSymbol = onOpenSymbol,
                modifier = Modifier.fillMaxSize(),
            )
            PaperTab.ORDERS -> PaperOrders(
                state = state,
                controller = controller,
                zone = zone,
                modifier = Modifier.fillMaxSize(),
            )
            PaperTab.HISTORY -> PaperHistory(
                state = state,
                zone = zone,
                modifier = Modifier.fillMaxSize(),
            )
            PaperTab.RECORD -> PaperRecordTab(
                state = state,
                zone = zone,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    if (assumptionsOpen) {
        PaperAssumptionsSheet(
            state = state,
            controller = controller,
            onDismiss = { assumptionsOpen = false },
        )
    }
}

/**
 * The account, at the top of every tab.
 *
 * Pinned rather than scrolled away with the content, because it is the thing the whole screen is
 * about: a reader placing an order has to be able to see what it is being placed against without
 * scrolling back. It is also where the permanent simulation line lives — the one sentence that has
 * to be true on every tab, and the reason this panel is not collapsible.
 */
@Composable
private fun AccountPanel(state: PaperTradeUiState) {
    val account = state.book.account
    val realised = account.balance - account.startingBalance
    CoineProCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = CoineProSpacing.OneHalf,
            vertical = CoineProSpacing.OneHalf,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.paper_equity_caption),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
            PaperBadge()
        }
        CoineProHeroFigure(
            figure = if (state.loaded) PaperFormat.money(state.equity) else PaperFormat.ABSENT,
            modifier = Modifier.padding(top = CoineProSpacing.Half),
            caption = PaperFormat.money(realised, signed = true),
        )
        CoineProReadingRow(
            readings = listOf(
                CoineProReading(stringResource(R.string.paper_balance), PaperFormat.money(account.balance)),
                CoineProReading(
                    stringResource(R.string.paper_unrealised),
                    PaperFormat.money(state.unrealised, signed = true),
                    PaperFormat.tone(state.unrealised),
                ),
                CoineProReading(
                    // The margin level where there is leverage; the free margin where there is not,
                    // because at one times the level is a ratio nobody reads and the money left is.
                    if (state.book.marginUsed > 0.0 && state.book.rules.leverage > 1.0) {
                        stringResource(R.string.paper_margin_level)
                    } else {
                        stringResource(R.string.paper_free_margin)
                    },
                    if (state.book.marginUsed > 0.0 && state.book.rules.leverage > 1.0) {
                        PaperFormat.ratio(state.marginLevelPercent)
                    } else {
                        PaperFormat.money(state.freeMargin)
                    },
                ),
            ),
            modifier = Modifier.padding(horizontal = 0.dp),
        )
        Text(
            text = stringResource(R.string.paper_banner),
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextSecondary,
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.Half),
        )
        if (!state.loaded) {
            Notice(stringResource(R.string.paper_loading), CoineProColors.TextMuted)
        }
        if (state.stale) {
            Notice(stringResource(R.string.paper_stale), CoineProColors.Warning)
        }
        if (state.markIncomplete) {
            Notice(stringResource(R.string.paper_marks_missing), CoineProColors.Warning)
        }
    }
}

@Composable
private fun Notice(text: String, tone: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = tone,
        textAlign = TextAlign.Right,
        modifier = Modifier.fillMaxWidth().padding(top = CoineProSpacing.Half),
    )
}

/**
 * The word, wherever a surface could be mistaken for a real one.
 *
 * Small and permanent rather than large and dismissible. A banner a reader can close is a banner
 * that is absent on the day it matters, and this app has a real order ticket two taps away.
 */
@Composable
fun PaperBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.paper_badge),
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.Warning,
        modifier = modifier
            .background(CoineProColors.SurfaceElevated, CoineProShapes.small)
            .padding(horizontal = CoineProSpacing.One, vertical = 4.dp),
    )
}

@Composable
private fun TabChips(selected: PaperTab, onSelect: (PaperTab) -> Unit) {
    val options = listOf(
        CoineProChip(PaperTab.TICKET.name, stringResource(R.string.paper_tab_ticket)),
        CoineProChip(PaperTab.POSITIONS.name, stringResource(R.string.paper_tab_positions)),
        CoineProChip(PaperTab.ORDERS.name, stringResource(R.string.paper_tab_orders)),
        CoineProChip(PaperTab.HISTORY.name, stringResource(R.string.paper_tab_history)),
        CoineProChip(PaperTab.RECORD.name, stringResource(R.string.paper_tab_record)),
    )
    CoineProChipRow(
        options = options,
        selectedId = selected.name,
        // Null is the reader tapping the section they are already in. There is no "no section" to
        // fall back to, so it means nothing and does nothing.
        onSelect = { id -> id?.let { onSelect(PaperTab.valueOf(it)) } },
        modifier = Modifier.padding(bottom = CoineProSpacing.One),
    )
}

/**
 * What the screen is allowed to tell the simulator about the market.
 *
 * The ticket's symbol is included even when nothing is open in it, because the fill preview has to
 * be able to price an order before it exists. Everything else comes from the book itself, so a
 * screen never subscribes to more than the account needs.
 */
private fun observations(
    state: PaperTradeUiState,
    ticket: String,
    priceFor: (String) -> Double?,
    quoteFor: ((String) -> PaperQuote?)?,
): Map<String, PaperQuote> {
    val wanted = state.book.tracked + PaperEngine.normalise(ticket)
    return buildMap {
        wanted.filter { it.isNotEmpty() }.forEach { symbol ->
            val quote = quoteFor?.invoke(symbol)
                ?: priceFor(symbol)
                    ?.takeIf { it.isFinite() && it > 0.0 }
                    ?.let { PaperQuote(symbol = symbol, last = it) }
            if (quote != null) put(symbol, quote)
        }
    }
}
