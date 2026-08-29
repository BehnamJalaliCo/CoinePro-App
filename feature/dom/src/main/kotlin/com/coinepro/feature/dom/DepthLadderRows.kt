package com.coinepro.feature.dom

import com.coinepro.core.common.BidiText
import com.coinepro.core.orderbook.BookSide
import com.coinepro.core.orderbook.OrderBook
import com.coinepro.core.orderbook.OrderBookGateway
import java.util.Locale

/**
 * One rung, with everything the renderer needs and nothing it has to work out for itself.
 *
 * [barFraction] and [curveFraction] are in `0f..1f` and are the only two numbers the drawing code
 * reads. Keeping the arithmetic here rather than inside the composable is what makes it testable
 * without a device — and the arithmetic is where a ladder lies, because the same rows scaled
 * against a different denominator draw a different market.
 */
data class LadderRow(
    val price: Double,
    val quantity: Double,
    /** Which colour it takes and which edge it grows from. */
    val side: BookSide,
    /** This level against the largest **visible** level on either side. */
    val barFraction: Float,
    /** Everything between this level and the touch, against the heavier visible side's total. */
    val curveFraction: Float,
)

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
     */
    val priceDecimals: Int,
    /** The same decision for the two quantity columns. See [quantityDecimalsFor]. */
    val quantityDecimals: Int,
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
 * ### Small levels stay small
 *
 * A level a hundredth the size of the wall gets a bar a hundredth as long, which on a phone is
 * under a pixel and draws as nothing. That is left alone rather than given a minimum width: the
 * quantity is printed beside every bar, and stretching a tiny level to a visible one is precisely
 * the lie the bar exists to avoid — it is how a ladder invents support that is not there.
 */
fun ladderRows(book: OrderBook, levels: Int = OrderBookGateway.VISIBLE_LEVELS): DepthLadder {
    val visible = book.top(levels)
    val largestQuantity = visible.largestQuantity
    val largestCumulative = visible.largestCumulative

    fun rows(side: BookSide): List<LadderRow> = visible.cumulative(side).map { level ->
        LadderRow(
            price = level.price,
            quantity = level.quantity,
            side = side,
            barFraction = fraction(level.quantity, largestQuantity),
            curveFraction = fraction(level.total, largestCumulative),
        )
    }

    return DepthLadder(
        // Reversed exactly once, here. See the note on `DepthLadder.asks`.
        asks = rows(BookSide.ASK).reversed(),
        bids = rows(BookSide.BID),
        book = visible,
        // From the mid rather than from any one level, so the choice does not change as the top of
        // the book moves across a magnitude step and reformats the entire column mid-session.
        priceDecimals = priceDecimalsFor(visible.midPrice ?: visible.bestBid ?: visible.bestAsk ?: 0.0),
        quantityDecimals = quantityDecimalsFor(largestQuantity),
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
 * A share of `0.0..1.0` as a whole-number percentage — `62%`.
 *
 * [Locale.US] explicitly, because the device locale is Persian and `String.format` would otherwise
 * emit `۶۲٪` silently: a market figure in Persian digits, which is the one number convention this
 * app does not use. The sign is inside the isolate so a right-to-left line cannot move it.
 */
fun percentLabel(share: Double): String =
    BidiText.isolateLtr(String.format(Locale.US, "%.0f%%", share * 100))

/**
 * A share of [largest], clamped, with a zero denominator answering zero rather than infinity.
 *
 * A book of nothing but zero-sized levels cannot happen — `OrderBook.of` drops them — but a book
 * with no levels at all can, and dividing by its zero largest would put `NaN` into a layout
 * modifier, which throws at measure time and takes the whole screen down.
 */
private fun fraction(value: Double, largest: Double): Float =
    if (largest <= 0.0) 0f else (value / largest).toFloat().coerceIn(0f, 1f)
