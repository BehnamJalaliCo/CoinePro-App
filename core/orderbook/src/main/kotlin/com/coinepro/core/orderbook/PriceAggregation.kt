package com.coinepro.core.orderbook

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Price aggregation — folding a raw-tick book into buckets a phone can actually be read on.
 *
 * ### Why this is a condition of use rather than a convenience
 *
 * At LBank's futures tick a hundred levels of BTCUSDT span about ten USDT on a price near eighty
 * thousand. Drawn one tick to a row, the price column is eight rows that differ in their last digit
 * and agree in every other one, and the reader's eye — which finds the walls by *shape* and the
 * spread by where the colours meet — is handed a column of the same number eight times. Aggregation
 * is what turns that back into a ladder. On a screen this narrow it is not a refinement of the
 * feature, it is the feature working at all.
 *
 * ### The rounding direction is not symmetric, and that is the whole safety property
 *
 * Bids floor onto their bucket and asks ceil onto theirs. That is deliberate and it is the one rule
 * in this file that must never be relaxed for tidiness: it guarantees
 *
 * ```
 * aggregatedBestBid <= bestBid < bestAsk <= aggregatedBestAsk
 * ```
 *
 * so aggregating can only ever widen the spread, never narrow it and never cross a book that was
 * not already crossed. Round both sides the same way — to nearest, say, which is what "bucketing"
 * sounds like it should mean — and a bid a hair under a boundary is rounded *up* onto it, the
 * printed spread comes out tighter than the market's, and on a screen whose entire subject is the
 * cost of crossing that is a number inviting a trade that does not exist.
 *
 * ### Arithmetic in [BigDecimal], not in doubles
 *
 * The steps are decimal by construction — a tenth, a half, a thousandth — and none of the
 * interesting ones is representable in binary. `floor(price / 0.1) * 0.1` puts a level at
 * `77588.10000000001` about as often as it puts one at `77588.1`, and two levels that should have
 * merged into one bucket then draw as two rungs a hair apart, which is precisely the ladder this
 * exists to stop. [BigDecimal.valueOf] takes the double's shortest decimal representation, so
 * `0.1` really is one tenth here, and the quotient is exact. A hundred levels of it once a second
 * is nothing next to the request that fetched them.
 *
 * ### What it does with the book it is given
 *
 * Returns the book unchanged when [step] is null, which is how "no aggregation" travels: the raw
 * book is a real choice a reader can make and is the one this screen opens on, so it is a value
 * rather than an absent selection.
 *
 * Quantities are summed within a bucket and so are order counts, both by [OrderBook.of] — which
 * also re-sorts, so nothing here has to reason about ordering. The count keeps its null: a bucket
 * built only from levels this venue did not count stays uncounted rather than acquiring a zero.
 *
 * [OrderBook.truncated] and [OrderBook.maxAgeMillis] are carried through untouched. Aggregation
 * changes how the levels are grouped and says nothing about how deep the request went or how old
 * the answer is, and a folded book that forgot it was truncated would let the deepest bucket read
 * as the end of the market.
 */
fun OrderBook.aggregated(step: Double?): OrderBook {
    if (step == null || !step.isFinite() || step <= 0.0) return this
    // A step coarser than the cheapest resting bid floors that bid onto zero, and `OrderBook.of`
    // drops a level at zero — so an over-coarse step would delete the buy side one rung at a time
    // and draw a market with no buyers in it. It cannot arrive from `aggregationSteps`, which is
    // bounded well below this; it can arrive from a preference stored against a different
    // instrument. Refused outright rather than applied partially.
    if (bids.isNotEmpty() && step > bids.last().price) return this
    val bucket = BigDecimal.valueOf(step)
    return OrderBook.of(
        symbol = symbol,
        bids = bids.map { it.copy(price = it.price.snappedTo(bucket, RoundingMode.FLOOR)) },
        asks = asks.map { it.copy(price = it.price.snappedTo(bucket, RoundingMode.CEILING)) },
        at = at,
        truncated = truncated,
        maxAgeMillis = maxAgeMillis,
    )
}

/**
 * The finest price difference this book actually distinguishes, or null when it shows none.
 *
 * The smallest positive gap between two adjacent levels, taken across both sides. It is an
 * **upper bound on the venue's tick and never a lower one**: a book whose levels happen to be two
 * ticks apart everywhere reads as having a tick of two, and no book can ever make this smaller than
 * the real tick. That direction is the safe one for what it is used for — [aggregationSteps] offers
 * nothing finer than this, and offering a step finer than the venue's tick would be offering a
 * choice that changes nothing.
 *
 * Null on a book with fewer than two levels on both sides, where there is no gap to measure. The
 * caller then has no ladder to offer and says so by offering none, rather than by inventing a tick
 * from the price magnitude — a guessed tick is wrong on exactly the instruments a guess is needed
 * for.
 */
fun OrderBook.inferredTick(): Double? {
    val gaps = bids.zipWithNext { above, below -> above.price - below.price } +
        asks.zipWithNext { below, above -> above.price - below.price }
    return gaps.filter { it.isFinite() && it > 0.0 }.minOrNull()
}

/**
 * The steps this instrument is worth offering, coarsest-first arithmetic and finest-first order.
 *
 * ### Where the ladder starts
 *
 * At the instrument's own tick, snapped **down** to the nearest round `1` or `5` of its decade by
 * [ladderBase]. Snapping down matters twice over: it keeps the offered figures round — `0.5`, not
 * `0.30000000000000004` — and it makes the list stable between polls. A measured tick wobbles when
 * a thin book loses the two adjacent levels that were a single tick apart; snapped to a decade it
 * takes a tenfold change in the book's own granularity to move, and a control whose options change
 * under the reader's finger once a second is a control nobody can use.
 *
 * ### Where it stops
 *
 * At [MAX_STEP_IN_TICKS] times that base, which is [OrderBookGateway.DEFAULT_DEPTH] — the number of
 * levels the app actually fetches. That is the bound with a reason behind it rather than a round
 * number: a step that spans the whole loaded book folds every level into one rung and draws a
 * ladder of one row, and the rows past it are not coarser views of the market, they are absent.
 * Binance offers steps well beyond this on the same instrument because their book view is far
 * deeper than a hundred levels; ours is a hundred, and offering their range over our depth would be
 * offering four options that draw nothing. **Widening [OrderBookGateway.DEFAULT_DEPTH] widens this
 * ladder, which is correct and is the intended coupling.**
 *
 * The rungs themselves alternate ×5 and ×2 — `1, 5, 10, 50, 100` — rather than the full `1, 2, 5`
 * ladder. Both are round; the shorter one reaches the useful coarse end of the range in four rungs
 * instead of nine, and four chips fit in the screen's chrome where nine do not.
 *
 * [keep] folds a step the caller already has selected back into the list even when this book would
 * not have offered it — a preference stored when the book was denser, or one restored for a symbol
 * whose granularity has since changed. Dropping it would leave the reader's own choice applied to
 * the ladder with no chip showing it, which reads as a broken control rather than as a stale
 * preference.
 */
fun aggregationSteps(book: OrderBook, keep: Double? = null): List<Double> {
    val kept = keep?.takeIf { it.isFinite() && it > 0.0 }
    val tick = book.inferredTick() ?: return listOfNotNull(kept)
    // Both the ladder and the bound it stops at are built in `BigDecimal` for the reason the file
    // note gives: a ladder grown by repeated double multiplication drifts, and the drift arrives on
    // screen as a chip labelled `0.30000000000000004`.
    var rung = BigDecimal.valueOf(ladderBase(tick))
    val cap = rung.multiply(BigDecimal.valueOf(MAX_STEP_IN_TICKS.toLong()))
    val steps = mutableListOf<Double>()
    // ×5 then ×2, which walks 1 → 5 → 10 → 50 → 100. The base itself is never offered: it is at or
    // below the measured tick, so a chip for it would be the raw book wearing a number.
    var byFive = true
    while (steps.size < MAX_STEPS) {
        rung = rung.multiply(if (byFive) FIVE else TWO)
        byFive = !byFive
        if (rung > cap) break
        val step = rung.toDouble()
        // Strictly coarser than the measured tick, so every rung on the control genuinely changes
        // the ladder rather than redrawing it identically.
        if (step > tick) steps += step
    }
    return (steps + listOfNotNull(kept)).distinct().sorted()
}

/**
 * How many decimals a price column aggregated at [step] needs — and never more.
 *
 * This is the payoff Binance names in its own help text: choosing `0.01` displays prices to two
 * decimal places. Every aggregated price is an exact multiple of the step, so any digit past the
 * step's own last one is a zero the column is printing to no purpose — and on a ladder those zeroes
 * are the difference between a price spine that can be scanned and one that cannot.
 *
 * Clamped to `0..8`, which is what `MarketNumberFormatter.price` accepts; a step of `10` wants a
 * negative scale and takes zero.
 */
fun aggregationDecimals(step: Double): Int =
    BigDecimal.valueOf(step).stripTrailingZeros().scale().coerceIn(0, 8)

/**
 * The largest round `1` or `5` of a decade that is not coarser than [value].
 *
 * `0.1` stays `0.1`, `0.3` becomes `0.1`, `0.7` becomes `0.5`. Down and never up, so the base can
 * never claim a coarser granularity than the book showed.
 *
 * ### It is rounded to [TICK_SIGNIFICANT_DIGITS] first, and that is not tidying
 *
 * The value handed here is a *subtraction of two prices*, so a book quoting tenths hands over
 * `0.09999999999417923` rather than `0.1` — the two prices differ by a tenth in decimal and by
 * slightly less than a tenth in binary. Fed to a raw `log10` that lands one decade too low, the
 * base comes out `0.05`, and every chip on the control is then half the figure it should be for a
 * reason invisible anywhere on screen. Six significant digits is far more than any venue's tick
 * carries and far fewer than the noise, so it separates the two cleanly.
 *
 * The decade is then read off the [BigDecimal] rather than through `Math.log10`: for a value that
 * has already been normalised, `precision - scale - 1` **is** the exponent, exactly, with no
 * floating-point step left in the path that decides which decade the whole control sits on.
 */
internal fun ladderBase(value: Double): Double {
    val rounded = BigDecimal.valueOf(value).round(MathContext(TICK_SIGNIFICANT_DIGITS)).stripTrailingZeros()
    val exponent = rounded.precision() - rounded.scale() - 1
    val decade = BigDecimal.ONE.scaleByPowerOfTen(exponent)
    return if (rounded.divide(decade) >= FIVE) decade.multiply(FIVE).toDouble() else decade.toDouble()
}

/**
 * How much of a measured gap between two prices is signal.
 *
 * Six, because no venue quotes a tick with more significant digits than that and the binary noise
 * on a subtraction of two prices starts well past it. See [ladderBase] for the bug this number
 * exists to prevent.
 */
private const val TICK_SIGNIFICANT_DIGITS = 6

/**
 * The coarsest step offered, as a multiple of the instrument's tick.
 *
 * [OrderBookGateway.DEFAULT_DEPTH] and not a number of its own: it is the count of levels the app
 * fetches, and a step that spans all of them folds the book into a single rung. See
 * [aggregationSteps] for why that is the honest place to stop.
 */
private const val MAX_STEP_IN_TICKS = OrderBookGateway.DEFAULT_DEPTH

/**
 * How many aggregation chips the screen's chrome will carry.
 *
 * Four, plus the raw book the screen offers alongside them, which is five tap targets on one row —
 * about what fits beside the ladder on the narrowest phone this app supports before the row has to
 * scroll. It is a ceiling rather than a target: an instrument whose tick is coarse relative to the
 * depth fetched offers fewer, and fewer real choices is better than padding the row with steps that
 * draw one rung.
 */
private const val MAX_STEPS = 4

private val TWO = BigDecimal.valueOf(2L)
private val FIVE = BigDecimal.valueOf(5L)

private fun Double.snappedTo(bucket: BigDecimal, direction: RoundingMode): Double =
    BigDecimal.valueOf(this).divide(bucket, 0, direction).multiply(bucket).toDouble()
