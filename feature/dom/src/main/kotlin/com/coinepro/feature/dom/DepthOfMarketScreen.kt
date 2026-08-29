package com.coinepro.feature.dom

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.common.PersianDateTime
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProEmptyState
import com.coinepro.core.designsystem.CoineProIcons
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.LtrDirection
import com.coinepro.core.orderbook.BookSide
import com.coinepro.core.orderbook.DepthOutageReason
import com.coinepro.core.orderbook.DepthUnavailableReason
import com.coinepro.core.orderbook.OrderBook
import com.coinepro.core.orderbook.OrderBookController
import com.coinepro.core.orderbook.OrderBookGateway
import com.coinepro.core.orderbook.OrderBookState
import java.time.Instant

/**
 * Depth of market — the resting book, as a ladder.
 *
 * ### What it is for
 *
 * A price list says where the market is. This says what it would cost to move it. The reader is
 * looking for two things and the layout exists to answer both at a glance: **where the walls are**,
 * which is the long bars, and **which side is heavier**, which is the meter in the header and the
 * asymmetry of the bars around the spread. Everything else on the screen is subordinate to those.
 *
 * ### Why the price column is a spine
 *
 * Sizes sit outside the prices — buys on one side, sells on the other — and the prices run down the
 * middle in one unbroken column. That is what makes the spread legible: a reader finds the row
 * where the two colours meet without reading a single number. It also means the ladder must not
 * mirror with the rest of the app, so the whole block is [LtrDirection]. The screen around it is
 * right-to-left Persian; the ladder is a table of market figures, and the *order of its columns*
 * carries meaning that reversing would destroy.
 *
 * ### It hands a price up, it does not place an order
 *
 * Tapping a row calls [onPickPrice] and nothing else happens. This screen has no idea what an order
 * is, deliberately: a ladder where a mis-tap sends a live order is the single most expensive
 * interaction in trading software, and the tap targets here are twenty-eight points tall and packed
 * sixteen to a screen. The caller decides what a picked price means.
 */
@Composable
fun DepthOfMarketScreen(
    controller: OrderBookController,
    symbol: String,
    /** Hands the tapped level's price to whoever owns the order form. Never places anything. */
    onPickPrice: (Double) -> Unit,
    modifier: Modifier = Modifier,
    /** Rows per side. Eight is what a phone fits without the figures shrinking out of legibility. */
    levels: Int = OrderBookGateway.VISIBLE_LEVELS,
) {
    LaunchedEffect(controller, symbol) { controller.start(symbol) }
    // Stopped when the screen leaves, so a ladder nobody is looking at is not polling a venue once
    // a second for the life of the process.
    DisposableEffect(controller) { onDispose { controller.stop() } }

    val state by controller.state.collectAsStateWithLifecycle()

    DepthOfMarketBody(
        state = state,
        onPickPrice = onPickPrice,
        onRetry = controller::refresh,
        modifier = modifier,
        levels = levels,
    )
}

/**
 * The screen without its controller, so every state it can be in is reachable from a render test.
 *
 * The four terminal states below are exhaustive and there is no fifth branch that keeps waiting.
 * That is the property this whole feature is built around: on both platforms today the honest
 * answer is one of the two refusals, and a screen that could still fall through to a spinner would
 * show that spinner to every reader on every symbol.
 */
@Composable
fun DepthOfMarketBody(
    state: OrderBookState,
    onPickPrice: (Double) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    levels: Int = OrderBookGateway.VISIBLE_LEVELS,
) {
    val book = state.book
    val unavailable = state.unavailable
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CoineProColors.Terminal)
            .verticalScroll(rememberScrollState()),
    ) {
        DepthHeader(state)
        when {
            unavailable != null -> DepthUnavailable(unavailable)
            state.failed -> CoineProEmptyState(
                message = stringResource(failureMessage(state.outage)),
                icon = CoineProIcons.Markets,
                hint = stringResource(failureHint(state.outage)),
                action = stringResource(R.string.dom_retry),
                onAction = onRetry,
            )
            book == null -> Text(
                text = stringResource(R.string.dom_loading),
                style = MaterialTheme.typography.bodySmall,
                color = CoineProColors.TextMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(CoineProSpacing.Gutter),
                textAlign = TextAlign.Center,
            )
            !state.hasDepth -> CoineProEmptyState(
                message = stringResource(R.string.dom_empty),
                icon = CoineProIcons.Markets,
                hint = stringResource(R.string.dom_empty_hint),
            )
            else -> {
                val ladder = remember(book, levels) { ladderRows(book, levels) }
                DepthLadderTable(ladder = ladder, onPickPrice = onPickPrice)
                DepthFootnotes(showOrdersNote = ladder.hasOrders)
            }
        }
    }
}

/**
 * The sentence a reader gets when there is no book and asking again cannot produce one.
 *
 * There is **no retry button** on any of these branches, and that is the whole design. A retry says
 * "persistence might help". None of these conditions is helped by persistence: a broker that does
 * not publish Level II, a server older than the route, a market this platform does not carry, a
 * contract the exchange has retired. Offering the button would be a more comfortable screen that
 * tells the reader something untrue.
 *
 * Each reason gets its own copy because each has a different future, and a reader deciding whether
 * this app will ever show them a book is entitled to know which one they are looking at. Two of
 * them also point at different people: the delisting is the exchange's doing and the out-of-scope
 * symbol is this app's, so the second says so rather than blaming the backend for a request it was
 * right to refuse.
 */
@Composable
private fun DepthUnavailable(reason: DepthUnavailableReason) {
    val message = when (reason) {
        DepthUnavailableReason.FEED_PUBLISHES_NO_DEPTH -> R.string.dom_unavailable_feed
        DepthUnavailableReason.ENDPOINT_NOT_SERVED -> R.string.dom_unavailable_endpoint
        DepthUnavailableReason.SYMBOL_NOT_SERVED -> R.string.dom_unavailable_symbol
        DepthUnavailableReason.SYMBOL_DELISTED -> R.string.dom_unavailable_delisted
    }
    val hint = when (reason) {
        DepthUnavailableReason.FEED_PUBLISHES_NO_DEPTH -> R.string.dom_unavailable_feed_hint
        DepthUnavailableReason.ENDPOINT_NOT_SERVED -> R.string.dom_unavailable_endpoint_hint
        DepthUnavailableReason.SYMBOL_NOT_SERVED -> R.string.dom_unavailable_symbol_hint
        DepthUnavailableReason.SYMBOL_DELISTED -> R.string.dom_unavailable_delisted_hint
    }
    CoineProEmptyState(
        message = stringResource(message),
        icon = CoineProIcons.Markets,
        hint = stringResource(hint),
    )
}

/**
 * The headline for a failure that a retry can outlive.
 *
 * Null — an ordinary dropped request — keeps the generic sentence, which is true for it. The two
 * named outages get their own, because the generic hint tells the reader to check their connection
 * and on both of these the reader's connection is the one part of the chain that is working.
 */
private fun failureMessage(outage: DepthOutageReason?): Int = when (outage) {
    null -> R.string.dom_failed
    DepthOutageReason.EXCHANGE_UNREACHABLE -> R.string.dom_outage_exchange
    DepthOutageReason.RELAY_NOT_CONFIGURED -> R.string.dom_outage_relay
}

/** The line under [failureMessage]. Same reasoning; see there. */
private fun failureHint(outage: DepthOutageReason?): Int = when (outage) {
    null -> R.string.dom_failed_hint
    DepthOutageReason.EXCHANGE_UNREACHABLE -> R.string.dom_outage_exchange_hint
    DepthOutageReason.RELAY_NOT_CONFIGURED -> R.string.dom_outage_relay_hint
}

/**
 * Symbol, venue, and the two figures that summarise the book.
 *
 * The imbalance meter is here rather than beside the ladder on purpose: it is measured over a band
 * near the touch that is wider than the eight rows drawn, and the ladder shows a window into it.
 * Placed among the rows it would read as a total of the rows, which it is not — hence the note
 * under it, which names the band.
 */
@Composable
private fun DepthHeader(state: OrderBookState) {
    val book = state.book
    val unavailable = state.unavailable
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.OneHalf),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The screen is self-titled, so the app bar above it is bare — which means this row is
            // the only place the ladder says which market it is a ladder of. A depth screen that
            // names no instrument is one screenshot away from being read against the wrong one.
            Row(
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dom_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = CoineProColors.TextPrimary,
                )
                if (state.symbol.isNotBlank()) {
                    Text(
                        // A ticker is an identifier, not prose: Latin, isolated so an RTL row
                        // cannot reorder it.
                        text = BidiText.isolateLtr(state.symbol),
                        style = MaterialTheme.typography.labelMedium,
                        color = CoineProColors.TextMuted,
                    )
                }
            }
            if (state.sourceName.isNotBlank()) {
                Text(
                    text = stringResource(R.string.dom_source, state.sourceName),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }

        if (book != null && state.hasDepth) {
            DepthSummary(book)
            // A crossed book is called out before anything else is read off it. The spread on such
            // a book is negative and the ladder's two colours overlap, and neither is a market
            // event — it is two halves of a snapshot that were assembled a moment apart.
            if (book.crossed) {
                Text(
                    text = stringResource(R.string.dom_crossed),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.Warning,
                )
            }
            if (book.truncated) {
                Text(
                    text = stringResource(R.string.dom_truncated),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
            }
            // Zero is "the venue published no timestamp", not the epoch. An unknown age is left
            // unstated rather than printed as 1970 or quietly replaced with the phone's own clock.
            //
            // On crypto it is always zero: LBank's futures book carries no time at all. What the
            // relay does declare is the TTL of the cache it answered from, which bounds the age
            // from above, so that is what is shown when there is no venue instant. It is the *only*
            // number on that response that means anything about age — `server_time_ms` is the
            // relay's own clock at serialisation and would present a half-second-old book as brand
            // new every time, which is why the model never carries it this far.
            val maxAge = book.maxAgeMillis
            when {
                book.at > 0L -> Text(
                    text = stringResource(
                        R.string.dom_updated,
                        PersianDateTime.clock(Instant.ofEpochMilli(book.at)),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
                maxAge != null -> Text(
                    text = stringResource(R.string.dom_max_age, maxAgeSecondsLabel(maxAge)),
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
    }
}

/** Spread, mid and the bid share, with the meter under them. */
@Composable
private fun DepthSummary(book: OrderBook) {
    val decimals = priceDecimalsFor(book.midPrice ?: 0.0)
    // Measured once for the figure and the meter, over the band near the touch rather than over
    // everything loaded — see `OrderBookGateway.IMBALANCE_LEVELS` for why the fetch got wider and
    // this did not.
    val share = book.imbalance(OrderBookGateway.IMBALANCE_LEVELS)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Two),
    ) {
        book.spread?.let { spread ->
            SummaryFigure(stringResource(R.string.dom_spread), MarketNumberFormatter.price(spread, decimals))
        }
        book.midPrice?.let { mid ->
            SummaryFigure(stringResource(R.string.dom_mid), MarketNumberFormatter.price(mid, decimals))
        }
        share?.let { SummaryFigure(stringResource(R.string.dom_imbalance), percentLabel(it)) }
    }
    share?.let {
        ImbalanceMeter(it)
        Text(
            // The band is printed with the meter and not left implied. A bid share over twenty
            // levels and one over the hundred loaded are different claims about the market wearing
            // the same percent sign, and neither the figure nor the bar can say which it is.
            text = stringResource(
                R.string.dom_imbalance_note,
                OrderBookGateway.IMBALANCE_LEVELS.toPersianDigits(),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
    }
}

@Composable
private fun SummaryFigure(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextPrimary,
        )
    }
}

/**
 * One bar split at the bid's share of the book.
 *
 * A flat fill of the buy colour over a flat fill of the sell colour, with no gradient between them:
 * the reader is comparing two lengths, and a blend at the join makes the boundary — the one thing
 * being measured — the least defined pixel on the meter.
 */
@Composable
private fun ImbalanceMeter(share: Double) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CoineProShapes.small)
            .background(CoineProColors.Sell),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(share.toFloat().coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(CoineProColors.Buy),
        )
    }
}

/**
 * The ladder itself: sells above, the spread across the middle, buys below.
 *
 * Wrapped in [LtrDirection] so the size / price / size columns hold their places in a right-to-left
 * app. Without it the whole table mirrors, the buy column lands where a reader has learned the sell
 * column is, and the bars grow from the wrong edges — a picture that is wrong in the one way a
 * ladder cannot afford, since its entire content is which side is which.
 */
@Composable
private fun DepthLadderTable(ladder: DepthLadder, onPickPrice: (Double) -> Unit) {
    LtrDirection {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            // The mark is secondary and it is the first thing to go. Decided once here for the
            // whole table rather than per cell, so a ladder cannot mark a wall at the top and drop
            // the mark from an identical one lower down — every row shares this layout exactly, so
            // one measurement answers for all of them. Below the threshold the sizes need every
            // point they have, and a size figure with a count sitting on top of it loses leading
            // digits, which is a far worse loss than a mark. Dropping it here costs a sighted
            // reader on a very narrow screen the mark and costs nobody the figure: the count stays
            // in every row's spoken description whatever this says. See `LadderRowView`.
            val showStackedMarks = maxWidth >= MinWidthForStackedMark
            Column(modifier = Modifier.fillMaxWidth()) {
                LadderHeaderRow()
                ladder.asks.forEach { row ->
                    LadderRowView(row, ladder.priceDecimals, ladder.quantityDecimals, showStackedMarks, onPickPrice)
                }
                SpreadRow(ladder)
                ladder.bids.forEach { row ->
                    LadderRowView(row, ladder.priceDecimals, ladder.quantityDecimals, showStackedMarks, onPickPrice)
                }
            }
        }
    }
}

/**
 * The column heads: two sizes and the price spine, and nothing else.
 *
 * There was a third label here naming an order-count column. Both are gone. The count is on about
 * one rung in eight now rather than on every one, so it has no column to head — what explains it is
 * the line under the ladder, which is prose and belongs outside this left-to-right block anyway.
 */
@Composable
private fun LadderHeaderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColumnLabel(stringResource(R.string.dom_column_bid), TextAlign.Left)
        Text(
            text = stringResource(R.string.dom_column_price),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextDisabled,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(PriceColumnWidth),
        )
        ColumnLabel(stringResource(R.string.dom_column_ask), TextAlign.Right)
    }
}

@Composable
private fun RowScope.ColumnLabel(text: String, align: TextAlign) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextDisabled,
        textAlign = align,
        modifier = Modifier.weight(1f),
    )
}

/**
 * One rung.
 *
 * The whole row is the tap target, not the price cell: a ladder is used at speed and a reader
 * aiming at a four-character number on a moving list will miss it. Twenty-eight points is under the
 * usual forty-eight-point minimum and is the density this instrument is for — the rows are
 * contiguous, so there is no dead space between targets to fall into, and the price picked is
 * handed to a form the reader confirms rather than acted on here.
 */
@Composable
private fun LadderRowView(
    row: LadderRow,
    priceDecimals: Int,
    quantityDecimals: Int,
    showStackedMarks: Boolean,
    onPickPrice: (Double) -> Unit,
) {
    val colour = when (row.side) {
        // Read from the palette, so the reader's own colour-direction preference is already
        // applied: `CoineProTheme` exchanges `buy` and `sell` for a reader on the red-up
        // convention, and every direction colour in the app resolves through those two fields.
        BookSide.BID -> CoineProColors.Buy
        BookSide.ASK -> CoineProColors.Sell
    }
    val price = MarketNumberFormatter.price(row.price, priceDecimals)
    // `clickable` merges this row's semantics, so the count has to be part of the row's own
    // description or a reader who cannot see it never hears it. It is spoken with the price rather
    // than on its own for the same reason the mark sits on the bar: a count without a price is a
    // number about nothing.
    //
    // Built from [spokenOrders] and never from [drawnOrders]. The sighted ladder now marks only
    // about one rung in eight, because a figure that reads `1` on nine rows out of ten is furniture
    // — but that is a decision about ink, not about facts, and a reader who cannot see the ladder
    // must not lose the other seven counts to it. Every rung that has a count says it.
    //
    // A rung whose count is unknown falls to the plain sentence and says nothing about orders at
    // all. Absent and one are different facts, and "1 order" spoken over a level nobody counted
    // would be the ladder inventing the very figure this contract exists to keep honest.
    val orders = spokenOrders(row)
    val pickDescription = if (orders == null) {
        stringResource(R.string.dom_pick_price, price)
    } else {
        stringResource(R.string.dom_pick_price_orders, price, ordersLabel(orders))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clickable { onPickPrice(row.price) }
            .semantics { contentDescription = pickDescription }
            .padding(horizontal = CoineProSpacing.Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuantityCell(
            row = row.takeIf { it.side == BookSide.BID },
            colour = colour,
            barEdge = Alignment.CenterStart,
            figureEdge = Alignment.CenterEnd,
            decimals = quantityDecimals,
            showStackedMarks = showStackedMarks,
        )
        Text(
            text = price,
            style = MaterialTheme.typography.labelMedium,
            color = colour,
            textAlign = TextAlign.Right,
            modifier = Modifier.width(PriceColumnWidth),
        )
        QuantityCell(
            row = row.takeIf { it.side == BookSide.ASK },
            colour = colour,
            barEdge = Alignment.CenterEnd,
            figureEdge = Alignment.CenterStart,
            decimals = quantityDecimals,
            showStackedMarks = showStackedMarks,
        )
    }
}

/**
 * One side's cell: the cumulative area, the level's own bar over it, and the figure on top.
 *
 * Both fills are flat and differ only in alpha. The faint one is the depth curve — everything
 * between this level and the touch — and because it only ever grows away from the spread it reads
 * as a shape running down the page rather than as a second bar per row. The solid one is this level
 * alone. Drawing the curve as an outline instead would put a second set of edges into a table that
 * is already sixteen horizontal rules tall.
 *
 * [row] is null on the side this rung does not belong to, and the cell then draws nothing at all —
 * an empty half is what makes the ladder read as two columns of liquidity meeting at the spread.
 *
 * ### Why the mark is an overlay and not a column
 *
 * The stacked mark is placed at the cell's **outboard** edge, over the far end of the bar, and the
 * size figure keeps the exact position it would have without it. That is the whole point of putting
 * it in the same [Box] rather than in a [Row] beside the size: a mark that appears on about one rung
 * in eight and pushes the size along when it does would make the size column jitter horizontally
 * from row to row, and a column that jitters cannot be scanned vertically — which is the only way
 * this table is ever read. Laid over the bar it also lands on the thing it is describing.
 */
@Composable
private fun RowScope.QuantityCell(
    row: LadderRow?,
    colour: Color,
    barEdge: Alignment,
    figureEdge: Alignment,
    decimals: Int,
    showStackedMarks: Boolean,
) {
    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
        if (row != null) {
            DepthFill(row.curveFraction, colour.copy(alpha = CurveAlpha), barEdge)
            DepthFill(row.barFraction, colour.copy(alpha = BarAlpha), barEdge)
            val mark = drawnOrders(row)
            if (showStackedMarks && mark != null) {
                StackedOrdersMark(
                    orders = mark,
                    colour = colour,
                    modifier = Modifier
                        .align(barEdge)
                        .padding(horizontal = CoineProSpacing.Half),
                )
            }
            Text(
                text = MarketNumberFormatter.price(row.quantity, decimals),
                style = MaterialTheme.typography.labelSmall,
                // A stacked rung is the exception this ladder now exists to surface, so its size
                // steps up one level of ink and weight with the mark. Two quiet signals on the same
                // row rather than one loud one: the reader finds the row from across the table by
                // its weight and reads what it is from the mark.
                color = if (row.stacked) CoineProColors.TextPrimary else CoineProColors.TextSecondary,
                fontWeight = if (row.stacked) FontWeight.SemiBold else null,
                textAlign = TextAlign.Right,
                modifier = Modifier
                    .align(figureEdge)
                    .padding(horizontal = CoineProSpacing.Half),
            )
        }
    }
}

/**
 * The mark on a level that more than one order is resting on — `27`.
 *
 * Drawn only where [STACKED_ORDERS_THRESHOLD] is met, which on this venue is about one rung in
 * eight. The number itself is the mark rather than a dot or an asterisk beside one: a reader who has
 * spotted the row wants to know whether it is two orders or twenty-seven, and those are very
 * different walls — twenty-seven is a crowd that has to be lifted one order at a time, two is very
 * nearly the single participant every other rung is.
 *
 * The ground is a flat wash of the terminal colour and there is no border: the row already carries
 * two flat fills in the side's colour, and a third weight of edge would read as a fourth element
 * rather than as a badge. That wash is only there to lift the digits off the bar beneath them. The
 * ink is the side's own colour, so the mark stays legibly part of its half of the ladder — but it is
 * set against the ground rather than filled with it, so it cannot be mistaken for a size figure.
 * Latin digits by [ordersLabel] — it is a market figure, and the device locale would otherwise
 * render it in Persian ones.
 */
@Composable
private fun StackedOrdersMark(orders: Int, colour: Color, modifier: Modifier = Modifier) {
    Text(
        text = ordersLabel(orders),
        style = MaterialTheme.typography.labelSmall,
        color = colour,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Right,
        modifier = modifier
            .clip(CoineProShapes.extraSmall)
            .background(CoineProColors.Terminal.copy(alpha = MarkGroundAlpha))
            .padding(horizontal = CoineProSpacing.Half),
    )
}

/**
 * The two lines under the ladder: what the marks mean, and what the screen costs to keep open.
 *
 * Persian prose, so it sits **outside** the ladder's [LtrDirection] block and reads right to left
 * with the rest of the app. Both lines are muted and neither moves: [showOrdersNote] follows the
 * venue rather than the current snapshot, so the caption cannot blink on and off as walls come and
 * go, and the data line is true of every book this screen draws.
 *
 * The data figure is here rather than buried in a settings screen because it is the reader's money.
 * At `depth=100`, gzipped, once a second, the ladder costs about four megabytes an hour — real
 * money on a metered Iranian connection, and more again through a VPN. Saying it plainly, once,
 * where the polling actually happens is the honest place for it; a switch would be a second thing to
 * get wrong and a settings row would be read by nobody. The screen already stops polling the moment
 * it is left, which is the part that matters.
 */
@Composable
private fun DepthFootnotes(showOrdersNote: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        if (showOrdersNote) {
            Text(
                text = stringResource(
                    R.string.dom_orders_note,
                    STACKED_ORDERS_THRESHOLD.toPersianDigits(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        }
        Text(
            text = stringResource(R.string.dom_data_note),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
    }
}

@Composable
private fun BoxScope.DepthFill(fraction: Float, colour: Color, edge: Alignment) {
    Box(
        modifier = Modifier
            .align(edge)
            .fillMaxHeight()
            .fillMaxWidth(fraction)
            .background(colour),
    )
}

/**
 * The seam, marked.
 *
 * A rule above and below and the spread across the middle, so the reader's eye lands on the one row
 * that separates the two sides without counting rows. The spread is suppressed on a crossed book:
 * the figure would be negative, and a negative spread printed in the place a reader expects the
 * cost of crossing is a number that invites a trade that does not exist.
 */
@Composable
private fun SpreadRow(ladder: DepthLadder) {
    val spread = ladder.book.spread?.takeIf { !ladder.book.crossed }
    HorizontalDivider(thickness = 1.dp, color = CoineProColors.BorderStrong)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .padding(horizontal = CoineProSpacing.Gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.dom_spread),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        Text(
            text = spread?.let { MarketNumberFormatter.price(it, ladder.priceDecimals) } ?: NoFigure,
            style = MaterialTheme.typography.labelMedium,
            color = CoineProColors.TextPrimary,
            textAlign = TextAlign.Right,
            modifier = Modifier.padding(start = CoineProSpacing.One),
        )
    }
    HorizontalDivider(thickness = 1.dp, color = CoineProColors.BorderStrong)
}

/** Wide enough for eight significant digits at `labelMedium`, which covers every market quoted. */
private val PriceColumnWidth = 96.dp

/** Dense on purpose. See [LadderRowView] for why this is under the usual minimum target. */
private val RowHeight = 28.dp

/**
 * The narrowest ladder that still has room for the stacked mark and the size in one cell.
 *
 * The price spine takes a fixed 96 points and the two gutters another 32, which leaves the two size
 * cells about 96 points each at this width — enough for a five-figure size at one end and a
 * two-figure mark at the other without the two meeting. Below it the mark is dropped rather than
 * allowed to overlap: a size figure with digits sitting on it loses its leading digits, and a
 * leading digit is the difference between a wall and a rounding error. Split-screen and the
 * smallest phones land under this; ordinary handsets do not. The count is still spoken on every
 * rung that has one at any width — see `LadderRowView`.
 */
private val MinWidthForStackedMark = 320.dp

/** The level's own bar: present enough to compare lengths, faint enough to read the figure over. */
private const val BarAlpha = 0.28f

/** The depth curve behind it. A third of the bar, so the two never read as one shape. */
private const val CurveAlpha = 0.09f

/**
 * The ground under a stacked mark's digits, over the bar they sit on.
 *
 * The terminal colour at a low alpha rather than an opaque chip: the bar has to stay visible through
 * it, because the bar is the figure the reader came for and the mark is an annotation on it.
 */
private const val MarkGroundAlpha = 0.55f

/** An em dash, for a figure that is genuinely absent rather than zero. */
private const val NoFigure = "—"
