package com.coinepro.feature.dom

import com.coinepro.core.common.BidiText
import com.coinepro.core.common.MarketNumberFormatter
import com.coinepro.core.orderbook.BookSide
import com.coinepro.core.orderbook.OrderBook
import com.coinepro.core.orderbook.OrderBookGateway
import com.coinepro.core.orderbook.aggregationDecimals
import java.util.Locale

/**
 * Which figure the size columns print, and which length their bars carry.
 *
 * Binance's own two-way switch, and the two are genuinely different readings of one book rather
 * than a formatting preference. [AMOUNT] answers "how big is the wall at *this* price", which is
 * what a scalper looking for a level to lean on is reading. [CUMULATIVE] answers "what would it
 * cost me to get from the touch to here", which is what somebody sizing an order is reading, and
 * on a raw ladder that is a sum the reader would otherwise be doing in their head down eight rows.
 *
 * The same rung means both things at once and can only print one of them, which is why this is a
 * switch and not two columns: a ladder wide enough for two size figures a side has no room left for
 * the price spine that makes it a ladder.
 */
enum class LadderFigure {
    AMOUNT,
    CUMULATIVE,
}

/**
 * One rung, with everything the renderer needs and nothing it has to work out for itself.
 *
 * [barFraction], [cumulativeBarFraction] and [curveFraction] are in `0f..1f` and are the only
 * numbers the drawing code reads. Keeping the arithmetic here rather than inside the composable is
 * what makes it testable without a device — and the arithmetic is where a ladder lies, because the
 * same rows scaled against a different denominator draw a different market.
 */
data class LadderRow(
    val price: Double,
    val quantity: Double,
    /**
     * Everything resting between this level and the touch, this level included.
     *
     * Taken straight from [com.coinepro.core.orderbook.CumulativeDepth.total] rather than summed
     * again here. The book already walks each side outward from the touch and already carries the
     * reasoning for doing so; a second running total in this module would be a second answer to a
     * question that has one, and the two would part company the first time a level was dropped in
     * one place and not the other.
     */
    val total: Double,
    /** Which colour it takes and which edge it grows from. */
    val side: BookSide,
    /** This level against the largest **visible** level on either side. */
    val barFraction: Float,
    /**
     * [total] against the deepest **visible** total on either side — the bar [LadderFigure.CUMULATIVE]
     * draws.
     *
     * Scaled to the window and not to the loaded book, which makes it a different number from
     * [curveFraction] even though both are built from the same running total. The reason is that
     * this one is a bar in a row and has to fill its cell to be compared against the row above it;
     * [curveFraction] is a wash behind the bars whose whole point is that eight rows are a small
     * slice of a hundred. Collapsing the two onto one denominator loses whichever answer it keeps.
     */
    val cumulativeBarFraction: Float,
    /**
     * Everything between this level and the touch, against the heavier side of the **loaded** book.
     *
     * The one fraction on this row that is not scaled to the window, and the difference is what the
     * curve is for. See [ladderRows].
     */
    val curveFraction: Float,
    /**
     * How many separate orders make up [quantity], where the venue counts them.
     *
     * Carried straight through from [com.coinepro.core.orderbook.DepthLevel.orders] with no
     * arithmetic on it, because there is none to do: it is a count, not a share, and scaling it
     * against anything would answer a question nobody asked. Null on a venue that does not publish
     * it — MT5 never will — and null again on a level whose count this venue did not know, since
     * TradeYar omit the figure rather than sending a zero. The renderer draws nothing at all for a
     * null, and the row's spoken description says nothing about orders either: a level whose count
     * is unknown must never read as a level with one order on it.
     */
    val orders: Int? = null,
) {
    /**
     * Whether this rung is worth marking as more than one participant's order.
     *
     * The row-level answer to [STACKED_ORDERS_THRESHOLD]; see there for where the number comes
     * from. Null is false, and deliberately so — an unknown count is not evidence of a single
     * order, so it earns no mark, exactly as it earns no figure.
     */
    val stacked: Boolean get() = orders != null && orders >= STACKED_ORDERS_THRESHOLD
}

/**
 * The count at which a level stops being ordinary and gets marked — **2** on this exchange.
 *
 * ### Where the number comes from
 *
 * TradeYar measured the distribution over 400 levels per symbol (`depth=200`, both sides) on
 * 2026-08-29:
 *
 * | symbol | `orders = 1` | 2–4 | highest seen |
 * |---|---|---|---|
 * | BTCUSDT | 351 | 39 | 27 |
 * | ETHUSDT | 348 | 47 | 22 |
 * | DOGEUSDT | 346 | 51 | 9 |
 *
 * About **88% of levels are a single order**. That killed the first design, which printed the count
 * on every row that had width for it: a column reading `1` nine times out of ten is not information,
 * it is furniture, and it buries the rare stacked level it was added to reveal. So the column is
 * gone and only the exceptions are marked.
 *
 * Two is the first count that is not the dominant case, and it is a boundary their own buckets
 * draw rather than one invented here. It marks roughly one row in eight — about two rungs of a
 * sixteen-row ladder — which is often enough that a reader learns what the mark means and rare
 * enough that it still reads as an exception. Five was the alternative, taking only the tail above
 * their `2–4` bucket, and it was rejected for the opposite failure: at about one row in forty most
 * ladders would carry no mark at all, and a mark nobody ever sees is a mark nobody ever learns.
 *
 * ### It is an observation about LBank's futures book, not a law
 *
 * This is the shape of *this* venue at *this* time. A venue whose levels are usually stacked — a
 * retail-heavy book, or an exchange that does not aggregate by participant — would invert the
 * distribution, and then two would mark nearly every row and the column this replaced would have
 * been the better design after all. Anyone porting this ladder to another feed should re-measure
 * before trusting the number, and anyone who notices the marks have become the majority on this one
 * should treat that as the venue having changed rather than as the ladder being right.
 */
const val STACKED_ORDERS_THRESHOLD = 2

/**
 * The count this rung **draws**, or null where it draws none.
 *
 * Null on nine rungs in ten, and that is the design rather than a gap: see
 * [STACKED_ORDERS_THRESHOLD]. Paired deliberately with [spokenOrders], which answers the same
 * question for a reader who cannot see the ladder and answers it differently. The two exist as a
 * pair so that the difference between them is a decision with a name on it, rather than a `takeIf`
 * buried in a composable that the next quiet layout change silently applies to both.
 */
fun drawnOrders(row: LadderRow): Int? = row.orders.takeIf { row.stacked }

/**
 * The count this rung **says**, which is every count it has.
 *
 * A screen reader user must not lose information because a sighted layout got quieter. Dropping the
 * order column was a decision about ink — a figure reading `1` on nine rows out of ten crowds the
 * ladder and hides the tenth — and none of that reasoning applies to a description read out one row
 * at a time, where there is no column to crowd and no scan to interrupt.
 *
 * Null stays null and must never become `1`. A level whose count the venue did not publish and a
 * level with a single order on it are different facts about a market, and the second is the more
 * dangerous to invent: it says one participant is holding that whole wall and could pull it in a
 * single message.
 */
fun spokenOrders(row: LadderRow): Int? = row.orders

/**
 * The ladder as it is drawn: sells stacked above the spread, buys below it.
 *
 * [asks] is in *display* order — highest price first — which is the reverse of the order the book
 * holds it in. The book's order walks outward from the touch; the ladder reads top to bottom with
 * the spread in the middle, so the ask side has to be turned over exactly once. Doing it here, in a
 * value a test can inspect, is the alternative to doing it inside a `LazyColumn` where nothing
 * would ever check which way up it went.
 *
 * [book] is the narrowed book these rows came from, not the loaded one. The header reads its
 * [OrderBook.spread], [OrderBook.midPrice] and [OrderBook.truncated] so that what is printed above
 * the ladder describes the ladder that is actually on screen.
 */
data class DepthLadder(
    val asks: List<LadderRow>,
    val bids: List<LadderRow>,
    val book: OrderBook,
    /**
     * Decimal places for every price in the column, chosen **once** for the whole ladder.
     *
     * `MarketNumberFormatter.priceAuto` picks per value, which is right in a list of different
     * markets and wrong here: within one book the levels straddle a magnitude step often enough
     * that a per-row choice prints `0.5241` above `0.52`, the decimal points stop lining up, and a
     * column whose whole job is to be scanned vertically becomes ragged. See [priceDecimalsFor].
     *
     * On an aggregated ladder this comes from the step instead of from the mid, and that is the
     * visible half of the aggregation feature: every price is an exact multiple of the step, so a
     * digit past the step's last one is a zero printed to no purpose. It is what Binance's own help
     * text promises — choosing `0.01` displays prices to two decimal places — and without it the
     * column would fold eight rows onto one bucket and still print the four digits that made them
     * look different.
     */
    val priceDecimals: Int,
    /** The same decision for the two size columns in [LadderFigure.AMOUNT]. See [quantityDecimalsFor]. */
    val quantityDecimals: Int,
    /**
     * And in [LadderFigure.CUMULATIVE], where the figures are sums and so an order of magnitude
     * larger.
     *
     * A separate choice rather than reusing [quantityDecimals], because a book of hundredths whose
     * levels need five decimals has totals in the tens that need one, and printing those totals to
     * five decimals is a column of digits nobody reads that pushes the leading digits — the ones
     * that matter — out of the cell.
     */
    val cumulativeDecimals: Int,
    /**
     * Whether any visible rung carries an order count at all, decided once for the whole table.
     *
     * It no longer gates a column — there is no column — it gates the one line of copy that tells a
     * reader what the marks on the stacked rows are. That line is shown whenever this venue counts,
     * not whenever a mark happens to be on screen: with roughly one rung in eight marked, a legend
     * keyed to the marks themselves would appear and vanish between polls, and a caption that blinks
     * is worse than one that sits still. Where the venue counts nothing there is nothing to explain
     * and the line is not drawn.
     */
    val hasOrders: Boolean,
)

/**
 * Builds the rungs for the [levels] prices nearest the spread on each side.
 *
 * ### The denominator is the visible book, deliberately
 *
 * Bars are scaled against the largest level **among the rows on screen**, not among the twenty
 * loaded. Scaling against the loaded book would make a wall sitting six levels below the visible
 * window shrink every bar the reader can see, so the ladder would flatten for a reason nothing on
 * the screen explains. The imbalance figure in the header is the opposite choice — it is measured
 * over the whole loaded book, because that is a claim about the market rather than about the
 * picture — and the two are printed with their depths so they cannot be read as the same number.
 *
 * ### The curve's denominator is the loaded book, equally deliberately
 *
 * The two fractions on a row are scaled against different things because they answer different
 * questions, and collapsing them onto one denominator would cost one of the two answers. The bar
 * asks "which of the rows I can see is the big one" and is scaled to the window. The curve asks
 * "how much of this market is between here and the touch", and a hundred levels are fetched
 * precisely so it can answer that — scaled to the window instead, the deepest visible rung is
 * always full width whatever lies below it, which is the one shape a depth curve must never draw.
 * Read against the loaded book the visible rungs are a short, shallow wash, and that is the true
 * picture: eight rows really are a small slice of a hundred, and the size really is further out.
 *
 * ### Small levels stay small
 *
 * A level a hundredth the size of the wall gets a bar a hundredth as long, which on a phone is
 * under a pixel and draws as nothing. That is left alone rather than given a minimum width: the
 * quantity is printed beside every bar, and stretching a tiny level to a visible one is precisely
 * the lie the bar exists to avoid — it is how a ladder invents support that is not there.
 *
 * ### [book] arrives aggregated; [step] is read only for the decimals
 *
 * Nothing here folds prices into buckets. The screen aggregates once, above this call, and hands
 * the result both to this function and to the depth curve beside it — because the two have to be
 * looking at exactly the same book, and a step applied independently in two places is two places
 * that can disagree about which step is in force. What [step] does here is name the aggregation
 * that already happened so [priceDecimals] can be taken from it; null means the raw book, and the
 * decimals then come from the mid as they always did.
 */
fun ladderRows(
    book: OrderBook,
    levels: Int = OrderBookGateway.VISIBLE_LEVELS,
    step: Double? = null,
): DepthLadder {
    val visible = book.top(levels)
    val largestQuantity = visible.largestQuantity
    // The loaded book, not the window. See the note above on the two denominators.
    val largestCumulative = book.largestCumulative
    // The window again, and a third denominator rather than either of the two above: the cumulative
    // bars have to fill their cells to be compared with one another. The running total is monotonic
    // outward from the touch, so the deepest visible rung on each side is that side's largest and
    // there is nothing to scan for.
    val largestVisibleCumulative = maxOf(visible.bids.sumOf { it.quantity }, visible.asks.sumOf { it.quantity })

    // Zipped against the book's own levels rather than carried on `CumulativeDepth`: the curve is a
    // running total and an order count is not summable along it, so putting the count in that type
    // would invite exactly the addition that must never happen.
    fun rows(side: BookSide): List<LadderRow> {
        val sideLevels = when (side) {
            BookSide.BID -> visible.bids
            BookSide.ASK -> visible.asks
        }
        // `cumulative` walks the same list in the same order, so the index is the same rung. It is
        // read by index rather than zipped so that a future change to either side's ordering breaks
        // here instead of silently pairing a count with the wrong price.
        return visible.cumulative(side).mapIndexed { index, level ->
            LadderRow(
                price = level.price,
                quantity = level.quantity,
                total = level.total,
                side = side,
                barFraction = fraction(level.quantity, largestQuantity),
                cumulativeBarFraction = fraction(level.total, largestVisibleCumulative),
                curveFraction = fraction(level.total, largestCumulative),
                orders = sideLevels[index].orders,
            )
        }
    }

    val asks = rows(BookSide.ASK)
    val bids = rows(BookSide.BID)

    return DepthLadder(
        // Reversed exactly once, here. See the note on `DepthLadder.asks`.
        asks = asks.reversed(),
        bids = bids,
        book = visible,
        // The step where there is one, because every aggregated price is an exact multiple of it and
        // any further digit is a printed zero. Otherwise from the mid rather than from any one
        // level, so the choice does not change as the top of the book moves across a magnitude step
        // and reformats the entire column mid-session.
        priceDecimals = step?.let { aggregationDecimals(it) }
            ?: priceDecimalsFor(visible.midPrice ?: visible.bestBid ?: visible.bestAsk ?: 0.0),
        quantityDecimals = quantityDecimalsFor(largestQuantity),
        cumulativeDecimals = quantityDecimalsFor(largestVisibleCumulative),
        hasOrders = asks.any { it.orders != null } || bids.any { it.orders != null },
    )
}

/**
 * How many decimals a price column needs, from the magnitude of the market it is quoting.
 *
 * The steps are the exchanges' own — the same ones `MarketNumberFormatter.priceAuto` uses — so a
 * reader holding this ladder against LBank's sees the same digits. They are restated here rather
 * than called because that function chooses per value and this column needs one choice; its own
 * step table is private, and a public one would invite exactly the per-row use this exists to
 * avoid.
 */
fun priceDecimalsFor(reference: Double): Int {
    val magnitude = kotlin.math.abs(reference)
    return when {
        // Not a very small price: an absent one. Two decimals says "no price" the way the rest of
        // the app does, rather than claiming eight digits of precision about nothing.
        magnitude == 0.0 -> 2
        magnitude >= 1.0 -> 2
        magnitude >= 0.01 -> 4
        magnitude >= 0.0001 -> 6
        else -> 8
    }
}

/**
 * How many decimals the size columns need, from the largest level on the ladder.
 *
 * Sizes span further than prices do — fractions of a coin on one market, thousands of lots on
 * another — and the risk runs in both directions. Too few and every level in a book of hundredths
 * prints as `0`, which reads as an empty rung beside a bar that is plainly there. Too many and a
 * five-figure size needs more width than the column has and truncates, which loses the leading
 * digits that matter.
 */
fun quantityDecimalsFor(largest: Double): Int = when {
    largest >= 1_000.0 -> 0
    largest >= 100.0 -> 1
    largest >= 1.0 -> 3
    else -> 5
}

/**
 * The size figure this rung prints under [mode].
 *
 * A function beside [ladderBarFraction] rather than a `when` inside the cell, and the pair is the
 * point: the figure and the bar have to describe the same quantity or the row lies twice over — a
 * bar drawn from the level's own size beside a printed running total says the deepest rung is the
 * smallest, which is the exact inverse of what a cumulative ladder means. Kept together and named,
 * so a change to one is a change a reader of this file sees next to the other.
 */
fun ladderFigure(row: LadderRow, mode: LadderFigure): Double = when (mode) {
    LadderFigure.AMOUNT -> row.quantity
    LadderFigure.CUMULATIVE -> row.total
}

/** The bar length that goes with [ladderFigure]. See there for why they are a pair. */
fun ladderBarFraction(row: LadderRow, mode: LadderFigure): Float = when (mode) {
    LadderFigure.AMOUNT -> row.barFraction
    LadderFigure.CUMULATIVE -> row.cumulativeBarFraction
}

/** The decimals that go with [ladderFigure]. Same pairing, same reason. */
fun ladderFigureDecimals(ladder: DepthLadder, mode: LadderFigure): Int = when (mode) {
    LadderFigure.AMOUNT -> ladder.quantityDecimals
    LadderFigure.CUMULATIVE -> ladder.cumulativeDecimals
}

/**
 * An aggregation step as it appears on its chip — `0.1`, `10`.
 *
 * Printed to the step's own decimals and no more, by
 * [com.coinepro.core.orderbook.aggregationDecimals], so the chip reads as the number the column
 * will show rather than as a padded version of it — `10` and not `10.00`.
 *
 * Through `MarketNumberFormatter` rather than `String.format`, which is what isolates it: the chips
 * sit in a right-to-left row, and a bare `0.1` there is at the mercy of the paragraph around it.
 * The formatter also fixes [Locale.US], without which the device's Persian locale would render this
 * market figure in Persian digits — the one number convention this app does not use.
 */
fun stepLabel(step: Double): String =
    MarketNumberFormatter.price(step, aggregationDecimals(step))

/**
 * A share of `0.0..1.0` as a whole-number percentage — `62%`.
 *
 * [Locale.US] explicitly, because the device locale is Persian and `String.format` would otherwise
 * emit `۶۲٪` silently: a market figure in Persian digits, which is the one number convention this
 * app does not use. The sign is inside the isolate so a right-to-left line cannot move it.
 */
fun percentLabel(share: Double): String =
    BidiText.isolateLtr(String.format(Locale.US, "%.0f%%", share * 100))

/**
 * A resting-order count for the ladder — `12`.
 *
 * Latin digits, and [Locale.US] is the reason: the device locale is Persian, and `%d` through the
 * default locale emits `۱۲` silently. A count of orders at a price is a market figure and takes the
 * app's market-figure convention, not the Persian digits prose counts use. Isolated so it cannot be
 * reordered against the size beside it when the row is laid out.
 *
 * Used in two places that must not drift apart: the mark on a stacked rung, and the spoken
 * description of **every** rung that has a count, marked or not. A screen reader user is not to lose
 * a figure because the sighted layout got quieter.
 */
fun ordersLabel(orders: Int): String =
    BidiText.isolateLtr(String.format(Locale.US, "%d", orders))

/**
 * A cache bound in milliseconds as a figure in seconds — `0.5`.
 *
 * This is the only staleness figure the crypto ladder can honestly print. LBank's futures book
 * carries no timestamp, so there is no book age to show; what the relay does declare is the TTL of
 * the cache it served from, which bounds the age from above. The sentence around this label says
 * "at most", because that is what the number is — anything phrased as "updated N ago" would turn a
 * bound into a measurement.
 *
 * One decimal, because the bound in production is 500 ms and `0` would read as "no age at all".
 * [Locale.US] for the same reason as everywhere else in this file.
 */
fun maxAgeSecondsLabel(maxAgeMillis: Long): String =
    BidiText.isolateLtr(String.format(Locale.US, "%.1f", maxAgeMillis / 1_000.0))

/**
 * A share of [largest], clamped, with a zero denominator answering zero rather than infinity.
 *
 * A book of nothing but zero-sized levels cannot happen — `OrderBook.of` drops them — but a book
 * with no levels at all can, and dividing by its zero largest would put `NaN` into a layout
 * modifier, which throws at measure time and takes the whole screen down.
 */
private fun fraction(value: Double, largest: Double): Float =
    if (largest <= 0.0) 0f else (value / largest).toFloat().coerceIn(0f, 1f)
