package com.coinepro.feature.dom

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.coinepro.core.designsystem.CoineProPillShape
import com.coinepro.core.designsystem.CoineProSegmentedControl
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.LtrDirection
import com.coinepro.core.designsystem.CoineProTeachingStrip
import com.coinepro.core.designsystem.TeachingSurface
import com.coinepro.core.orderbook.BookSide
import com.coinepro.core.orderbook.DepthOutageReason
import com.coinepro.core.orderbook.DepthUnavailableReason
import com.coinepro.core.orderbook.OrderBook
import com.coinepro.core.orderbook.OrderBookController
import com.coinepro.core.orderbook.OrderBookGateway
import com.coinepro.core.orderbook.OrderBookState
import com.coinepro.core.orderbook.aggregated
import com.coinepro.core.orderbook.aggregationSteps
import kotlinx.coroutines.launch
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
    /**
     * Where this symbol's aggregation and figure mode are kept between visits, or null for a screen
     * that forgets them when it is left. See [DepthLadderPreferences] for why it is optional.
     */
    preferences: DepthLadderPreferences? = null,
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
        preferences = preferences,
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
    preferences: DepthLadderPreferences? = null,
) {
    val book = state.book
    val unavailable = state.unavailable

    // Held here rather than in the controller, and keyed on the symbol so switching markets starts
    // from that market's own answer instead of carrying a step that was chosen for a different
    // price magnitude. `rememberSaveable` is the floor: with nothing wired to [preferences] the
    // choice still survives a rotation, which is the failure a reader meets soonest.
    var step by rememberSaveable(state.symbol) { mutableStateOf<Double?>(null) }
    var figure by rememberSaveable(state.symbol) { mutableStateOf(LadderFigure.AMOUNT) }
    val scope = rememberCoroutineScope()

    // One read per symbol. It cannot fight the reader's own taps because every tap writes back
    // immediately below, so what this restores is always the last thing that was chosen here.
    LaunchedEffect(preferences, state.symbol) {
        if (preferences == null || state.symbol.isBlank()) return@LaunchedEffect
        preferences.load(state.symbol)?.let { stored ->
            step = stored.step
            figure = stored.figure
        }
    }

    // Named for what it does rather than `remember`, which would shadow the composable of that name
    // in a file that leans on it three lines further down.
    fun choose(newStep: Double?, newFigure: LadderFigure) {
        step = newStep
        figure = newFigure
        val store = preferences ?: return
        if (state.symbol.isBlank()) return
        scope.launch { store.save(state.symbol, DepthLadderPreference(newStep, newFigure)) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CoineProColors.Terminal)
            .verticalScroll(rememberScrollState()),
    ) {
        DepthHeader(state)
        CoineProTeachingStrip(TeachingSurface.DOM)
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
                // Folded once, here, and handed to both the ladder and the curve. They have to be
                // looking at the same book: a curve drawn from the raw levels under a ladder drawn
                // from folded ones would put its walls at prices the rows above it do not have.
                val shown = remember(book, step) { book.aggregated(step) }
                val ladder = remember(shown, levels, step) { ladderRows(shown, levels, step) }
                // From the raw book, because the tick the ladder is derived from is the venue's and
                // not the reader's: measured on the folded book the offered steps would climb every
                // time one was chosen, and the control would walk away under the finger.
                val steps = remember(book, step) { aggregationSteps(book, keep = step) }
                val curve = remember(shown) { depthCurve(shown) }
                DepthLadderControls(
                    steps = steps,
                    step = step,
                    figure = figure,
                    onStep = { choose(it, figure) },
                    onFigure = { choose(step, it) },
                )
                DepthLadderTable(ladder = ladder, figure = figure, onPickPrice = onPickPrice)
                curve?.let { DepthCurvePanel(curve = it, ladder = ladder) }
                DepthFootnotes(showOrdersNote = ladder.hasOrders, showStepNote = steps.isNotEmpty())
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
 * The two controls over the ladder: how coarsely prices are bucketed, and which figure the sizes
 * are.
 *
 * ### Why they are in the chrome and not behind a sheet
 *
 * Both change what every row on the ladder *means*, and both are things a reader changes while
 * reading rather than once when setting the screen up — the step is turned coarser to find the
 * walls and back to raw to work the touch, several times in a minute. A sheet would put two taps
 * and a dismissal between the reader and a change they make constantly, and it would hide the
 * current setting behind a button while the ladder underneath silently obeyed it.
 *
 * The step chips scroll horizontally rather than sharing the width equally. A step's label is its
 * own number and those are not the same length — `0.0005` beside `10` — so an equal split sizes
 * every chip to the longest and wastes most of the row on the shortest. Scrolling also means an
 * instrument that earns five options is not squeezed into the space an instrument with two needed.
 *
 * The whole block is Persian-side prose and stays outside the ladder's [LtrDirection]: the chips
 * carry Latin figures, which [stepLabel] isolates individually, and a left-to-right block here
 * would put the label «تجمیع قیمت» on the wrong end of its own row.
 */
@Composable
private fun DepthLadderControls(
    steps: List<Double>,
    step: Double?,
    figure: LadderFigure,
    onStep: (Double?) -> Unit,
    onFigure: (LadderFigure) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Half),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        // A book too thin to show a tick offers no steps, and the label for an empty row would be a
        // heading over nothing. The figure switch below stays either way: it needs no tick.
        if (steps.isNotEmpty()) {
            Text(
                text = stringResource(R.string.dom_step_label),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
            ) {
                // The raw book first and always. It is the ladder's opening state and the one a
                // reader returns to in order to see the venue's own levels, so it is a chip in the
                // same row rather than an absence of selection somewhere else.
                StepChip(
                    label = stringResource(R.string.dom_step_raw),
                    selected = step == null,
                    onClick = { onStep(null) },
                )
                steps.forEach { offered ->
                    StepChip(
                        label = stepLabel(offered),
                        selected = step == offered,
                        onClick = { onStep(offered) },
                    )
                }
            }
        }
        CoineProSegmentedControl(
            options = listOf(
                LadderFigure.AMOUNT to stringResource(R.string.dom_figure_amount),
                LadderFigure.CUMULATIVE to stringResource(R.string.dom_figure_cumulative),
            ),
            selected = figure,
            onSelect = onFigure,
        )
    }
}

/**
 * One aggregation step, as a chip.
 *
 * `selectable` with [Role.RadioButton] rather than `clickable`: the row is one exclusive choice out
 * of several, and a screen reader that announces each chip as a button leaves the reader with no
 * way to hear which one is currently in force. The label is the whole content, so no separate
 * description is set — a second one would be read instead of the figure rather than beside it.
 */
@Composable
private fun StepChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) CoineProColors.TextPrimary else CoineProColors.TextSecondary,
        fontWeight = if (selected) FontWeight.SemiBold else null,
        modifier = Modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .clip(CoineProPillShape)
            .background(if (selected) CoineProColors.SurfaceRaised else CoineProColors.Surface)
            .border(
                1.dp,
                if (selected) CoineProColors.BorderStrong else CoineProColors.BorderSubtle,
                CoineProPillShape,
            )
            .padding(horizontal = CoineProSpacing.OneHalf, vertical = CoineProSpacing.One),
    )
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
private fun DepthLadderTable(
    ladder: DepthLadder,
    figure: LadderFigure,
    onPickPrice: (Double) -> Unit,
) {
    val decimals = ladderFigureDecimals(ladder, figure)
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
                LadderHeaderRow(figure)
                ladder.asks.forEach { row ->
                    LadderRowView(row, figure, ladder.priceDecimals, decimals, showStackedMarks, onPickPrice)
                }
                SpreadRow(ladder)
                ladder.bids.forEach { row ->
                    LadderRowView(row, figure, ladder.priceDecimals, decimals, showStackedMarks, onPickPrice)
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
 *
 * The two size labels name the [LadderFigure] in force. Without that the switch above the ladder
 * would be the only thing on screen saying which quantity the columns hold, and a reader who has
 * scrolled the switch off the top is left comparing sums against sizes with nothing to tell them
 * apart — the two look identical and differ by an order of magnitude.
 */
@Composable
private fun LadderHeaderRow(figure: LadderFigure) {
    val bid = when (figure) {
        LadderFigure.AMOUNT -> R.string.dom_column_bid
        LadderFigure.CUMULATIVE -> R.string.dom_column_bid_cumulative
    }
    val ask = when (figure) {
        LadderFigure.AMOUNT -> R.string.dom_column_ask
        LadderFigure.CUMULATIVE -> R.string.dom_column_ask_cumulative
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.Half),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColumnLabel(stringResource(bid), TextAlign.Left)
        Text(
            text = stringResource(R.string.dom_column_price),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(PriceColumnWidth),
        )
        ColumnLabel(stringResource(ask), TextAlign.Right)
    }
}

@Composable
private fun RowScope.ColumnLabel(text: String, align: TextAlign) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
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
    figure: LadderFigure,
    priceDecimals: Int,
    figureDecimals: Int,
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
            figure = figure,
            colour = colour,
            barEdge = Alignment.CenterStart,
            figureEdge = Alignment.CenterEnd,
            decimals = figureDecimals,
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
            figure = figure,
            colour = colour,
            barEdge = Alignment.CenterEnd,
            figureEdge = Alignment.CenterStart,
            decimals = figureDecimals,
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
    figure: LadderFigure,
    colour: Color,
    barEdge: Alignment,
    figureEdge: Alignment,
    decimals: Int,
    showStackedMarks: Boolean,
) {
    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
        if (row != null) {
            // The wash is drawn only under a per-level bar. In [LadderFigure.CUMULATIVE] the bar is
            // already the running total, so the wash would be the same quantity a second time at a
            // second scale — two nested bars measuring one thing, which reads as a rendering fault
            // rather than as two facts.
            if (figure == LadderFigure.AMOUNT) {
                DepthFill(row.curveFraction, colour.copy(alpha = CurveAlpha), barEdge)
            }
            DepthFill(ladderBarFraction(row, figure), colour.copy(alpha = BarAlpha), barEdge)
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
                text = MarketNumberFormatter.price(ladderFigure(row, figure), decimals),
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
 * The depth curve: the same cumulative totals the ladder washes behind its bars, drawn as a shape.
 *
 * ### Why a picture of a number the ladder already carries
 *
 * The ladder's wash answers "how much is between this rung and the touch" one row at a time, over
 * the eight rows on screen. The curve answers it over all hundred levels at once, and the answer it
 * gives — *where the size actually sits* — is not readable from eight rows however carefully they
 * are scaled. A wall four hundred ticks out is the reason a trader opens this screen and is off the
 * bottom of the ladder in every snapshot; here it is a step in the outline.
 *
 * It is [OrderBook.cumulative] rendered and nothing else. See [depthCurve].
 *
 * ### A path, filled flat and stroked, and no gradient anywhere in it
 *
 * The fill is one flat colour at low alpha and the outline is the same colour at full strength. A
 * vertical ramp — which is what a depth chart looks like everywhere else — would put the strongest
 * ink at the baseline, where there is nothing to read, and fade out exactly at the outline, which
 * is the only part of the shape carrying the data. The two sides are the ladder's own buy and sell
 * colours, so a reader who has learned which half is which on the rows above does not have to learn
 * it again twenty points lower.
 *
 * ### The reader who cannot see it gets the figures instead
 *
 * The canvas is one node with a description on it. A curve is a shape and there is no honest way to
 * speak a shape, so what is spoken is the pair of totals it is drawn from and the price band it
 * covers — which is the substance of it, and is otherwise nowhere on the screen.
 */
@Composable
private fun DepthCurvePanel(curve: DepthCurve, ladder: DepthLadder) {
    val buy = CoineProColors.Buy
    val sell = CoineProColors.Sell
    val axis = CoineProColors.BorderStrong
    // From the curve's own peak and not from `ladder.cumulativeDecimals`. The ladder's totals are
    // eight rows deep and this one is a hundred, so they are two magnitudes apart, and the ladder's
    // choice would print the curve's figure with three decimal places of nothing on the end.
    val totals = MarketNumberFormatter.price(curve.peakTotal, quantityDecimalsFor(curve.peakTotal))
    val description = stringResource(
        R.string.dom_curve_description,
        totals,
        MarketNumberFormatter.price(curve.lowPrice, ladder.priceDecimals),
        MarketNumberFormatter.price(curve.highPrice, ladder.priceDecimals),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        Text(
            text = stringResource(R.string.dom_curve_title),
            style = MaterialTheme.typography.labelSmall,
            color = CoineProColors.TextMuted,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CurveHeight)
                .semantics { contentDescription = description },
        ) {
            // The mid, so the two halves can be told apart without reading the axis under them. It
            // is drawn first and under both fills: a rule over the outline would break the one line
            // in the picture the eye follows.
            drawLine(
                color = axis,
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 1.dp.toPx(),
            )
            drawSide(curve.bids, buy)
            drawSide(curve.asks, sell)
        }
        // The axis, left to right under a left-to-right plot, so it stays outside the Persian flow
        // of everything else in this column.
        LtrDirection {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CurveAxisLabel(MarketNumberFormatter.price(curve.lowPrice, ladder.priceDecimals))
                CurveAxisLabel(MarketNumberFormatter.price(curve.mid, ladder.priceDecimals))
                CurveAxisLabel(MarketNumberFormatter.price(curve.highPrice, ladder.priceDecimals))
            }
        }
    }
}

@Composable
private fun CurveAxisLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        textAlign = TextAlign.Right,
    )
}

/**
 * One side of the curve: the area under it, then the outline over that.
 *
 * The area is closed down to the baseline at both ends — at the touch and at the far end — rather
 * than back along the outline, so a side whose book stops short of the plot's edge ends in a
 * vertical drop rather than in a diagonal running back to the spread. The diagonal would be a line
 * the data does not contain, and it slopes the wrong way: it reads as liquidity thinning out where
 * in truth the request simply stopped.
 *
 * Drawn as a polyline through the levels rather than as a staircase. The book really is a step
 * function and at a hundred levels across a phone's width each tread is about three points wide,
 * where the two are indistinguishable; the staircase would double the vertex count of a path
 * rebuilt on every poll to draw a difference nobody can see.
 */
private fun DrawScope.drawSide(points: List<DepthCurvePoint>, colour: Color) {
    if (points.size < 2) return
    fun px(point: DepthCurvePoint) = Offset(point.x * size.width, (1f - point.y) * size.height)

    val outline = Path().apply {
        moveTo(px(points.first()).x, px(points.first()).y)
        points.drop(1).forEach { lineTo(px(it).x, px(it).y) }
    }
    val area = Path().apply {
        moveTo(px(points.first()).x, size.height)
        points.forEach { lineTo(px(it).x, px(it).y) }
        lineTo(px(points.last()).x, size.height)
        close()
    }
    drawPath(area, colour.copy(alpha = CurveFillAlpha))
    drawPath(outline, colour, style = Stroke(width = 1.5.dp.toPx()))
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
private fun DepthFootnotes(showOrdersNote: Boolean, showStepNote: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
        verticalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
    ) {
        // Follows the control rather than the reader's current choice, for the reason
        // [showOrdersNote] does: a line that appears only while a step is selected explains the
        // feature exactly to the readers who have already found it.
        if (showStepNote) {
            Text(
                text = stringResource(R.string.dom_step_note),
                style = MaterialTheme.typography.labelSmall,
                color = CoineProColors.TextMuted,
            )
        }
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
 * How tall the depth-curve plot is.
 *
 * Eighty points, which is about three ladder rows. It has to be short: the ladder is what this
 * screen is, the curve is the context around it, and a plot tall enough to be a chart in its own
 * right would push the rungs under the fold on the phones this app is mostly read on. Eighty is
 * enough for the outline's steps to be separable and not enough to compete with the rows above it.
 */
private val CurveHeight = 80.dp

/**
 * The ground under the curve's outline.
 *
 * Heavier than [CurveAlpha], because this fill has no bar over it and nothing printed on top — it
 * is the whole of one side of the picture rather than a wash behind a figure. Still well short of
 * solid, so where the two sides' bands sit either side of the mid neither one reads as a block.
 */
private const val CurveFillAlpha = 0.16f

/**
 * The ground under a stacked mark's digits, over the bar they sit on.
 *
 * The terminal colour at a low alpha rather than an opaque chip: the bar has to stay visible through
 * it, because the bar is the figure the reader came for and the mark is an annotation on it.
 */
private const val MarkGroundAlpha = 0.55f

/** An em dash, for a figure that is genuinely absent rather than zero. */
private const val NoFigure = "—"
