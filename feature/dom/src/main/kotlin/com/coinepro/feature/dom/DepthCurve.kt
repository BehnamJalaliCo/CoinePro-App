package com.coinepro.feature.dom

import com.coinepro.core.orderbook.BookSide
import com.coinepro.core.orderbook.OrderBook

/**
 * One vertex of the depth curve: a level, and where it lands in the plot.
 *
 * [price] and [total] are the figures the curve is *about* and are carried so the panel can label
 * its ends without going back to the book and risking a different window; [x] and [y] are the only
 * two numbers the drawing code reads, both in `0f..1f`, with `y` measured **upwards** from the
 * baseline so the renderer inverts it once against the canvas height rather than every call site
 * inverting it separately.
 */
data class DepthCurvePoint(val price: Double, val total: Double, val x: Float, val y: Float)

/**
 * The cumulative depth on both sides, laid out for one plot.
 *
 * [bids] and [asks] are in the **book's own order** — outward from the touch — which is the order
 * [OrderBook.cumulative] returns and the order the running total is monotonic in. So a bid list
 * walks leftwards from the middle of the plot and an ask list walks rightwards, and the renderer
 * draws each one from the spread outward. Turning either over here would put the reversal in a
 * place no test looks at, and a depth curve drawn backwards falls away from the touch, which is the
 * shape of a book that does not exist.
 *
 * [lowPrice] and [highPrice] are the ends of the price axis and are placed **symmetrically about
 * [mid]**: see [depthCurve] for why the two sides must share one half-width.
 */
data class DepthCurve(
    val bids: List<DepthCurvePoint>,
    val asks: List<DepthCurvePoint>,
    val mid: Double,
    val lowPrice: Double,
    val highPrice: Double,
    /** The denominator both sides are scaled by — the heavier side's whole loaded volume. */
    val peakTotal: Double,
)

/**
 * The depth curve for one book, or null when this book has no curve to draw.
 *
 * ### It renders [OrderBook.cumulative] and computes nothing of its own
 *
 * The running totals come from the book, which already walks each side outward from the touch and
 * already carries the reasoning for doing so. All that happens here is the mapping into plot space.
 * A second running total computed in the feature module would be a second answer to a question the
 * book has already answered, and the two would drift the first time a level was dropped in one
 * place and not the other.
 *
 * ### One half-width, shared, for the same reason `largestQuantity` is shared
 *
 * The axis runs from `mid − half` to `mid + half` where `half` is the *further* of the two sides'
 * reach. Giving each side its own half-width would stretch the shallower side across the same
 * horizontal distance as the deeper one, and the single thing the curve exists to show — which side
 * has its size closer in — would be scaled out of the picture. The consequence is that the
 * shallower side's curve stops short of its edge of the plot, and that is the true statement: the
 * book really does not reach that far on that side.
 *
 * Both fractions are clamped, which costs nothing on a well-formed book and stops a level that
 * arrived outside the window from being drawn off-canvas.
 *
 * ### Null, and what each null means
 *
 * * **Either side empty.** There is no mid to centre on, and a one-sided depth chart drawn centred
 *   would put a wall of buyers against an empty half that reads as an absence of sellers rather
 *   than as an absence of data.
 * * **A crossed book.** Asked outright rather than left to fall out of the arithmetic, because it
 *   does not: a book whose best bid is above its best ask still has a mid and still has a positive
 *   half-width, and the curve it produces is a perfectly clean picture in which part of the buy
 *   side sits to the *right* of the middle, over the sell side, with nothing on it to say so. The
 *   ladder already says in words that the book is momentarily inconsistent — two halves of a
 *   snapshot assembled a few milliseconds apart — and the curve adds nothing to that by drawing it.
 * * **No volume at all.** Nothing to scale against, and a flat line along the baseline would read
 *   as a market with no resting size in it.
 *
 * In every one of those the panel is simply not drawn. A depth curve is the one element on this
 * screen with no honest empty state — an empty plot with axes on it is a claim about liquidity.
 */
fun depthCurve(book: OrderBook): DepthCurve? {
    if (book.crossed) return null
    val mid = book.midPrice ?: return null
    val deepestBid = book.bids.lastOrNull()?.price ?: return null
    val deepestAsk = book.asks.lastOrNull()?.price ?: return null
    val half = maxOf(mid - deepestBid, deepestAsk - mid)
    if (!half.isFinite() || half <= 0.0) return null
    val peak = book.largestCumulative
    if (!peak.isFinite() || peak <= 0.0) return null

    val low = mid - half
    val span = half * 2.0

    fun points(side: BookSide): List<DepthCurvePoint> = book.cumulative(side).map { level ->
        DepthCurvePoint(
            price = level.price,
            total = level.total,
            x = ((level.price - low) / span).toFloat().coerceIn(0f, 1f),
            y = (level.total / peak).toFloat().coerceIn(0f, 1f),
        )
    }

    return DepthCurve(
        bids = points(BookSide.BID),
        asks = points(BookSide.ASK),
        mid = mid,
        lowPrice = low,
        highPrice = mid + half,
        peakTotal = peak,
    )
}
