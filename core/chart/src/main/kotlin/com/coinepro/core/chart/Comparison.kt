package com.coinepro.core.chart

import kotlin.math.abs

/*
 * A second instrument drawn over the first.
 *
 * Comparison is the one thing on a chart that answers a question about the *world* rather than
 * about one symbol: gold against the dollar, a coin against Bitcoin, an index against the stock a
 * reader holds. Every terminal gives it away, including the free tiers, because a trader who has to
 * open two charts and hold one of them in their head is doing arithmetic instead of reading.
 *
 * Everything in this file is arithmetic on arrays and knows nothing about Compose. The chart layer
 * turns a [ComparisonSeries] into a line; what happens here is the part that is easy to get subtly
 * and invisibly wrong — lining two feeds up that do not share a calendar, and putting two prices of
 * wildly different magnitude on one axis without lying about either.
 */

/**
 * One compared instrument, already lined up with the base chart's bars.
 *
 * [values] is one entry per **base** bar and is the same length as the base series, always. A bar
 * the compared instrument did not print carries the last price it did print, and a bar older than
 * anything this instrument has carries `NaN`. Neither is a hole to be filled in later: they are the
 * two honest answers, and see [align] for why nothing is interpolated between them.
 *
 * [times] is the base series' timestamps, copied, so a renderer never has to reach back for them
 * and can never accidentally plot this series against its own original grid.
 *
 * [colour] is ARGB in a `Long` for the same reason [ChartLine.colour] is: this module describes what
 * it wants drawn without depending on Compose's `Color`. Take it from [comparisonColour] rather than
 * inventing one, because the constraint the palette satisfies is not aesthetic.
 *
 * Equality is by array *content* rather than by array identity, which is what the generated `equals`
 * of a data class holding a `DoubleArray` would give. Reference equality here would mean two
 * alignments of the same two feeds compare unequal, and the chart would redraw every frame for no
 * reason; content equality of a few hundred doubles is far cheaper than the recomposition it avoids.
 */
data class ComparisonSeries(
    val symbol: String,
    val label: String,
    val colour: Long,
    val values: DoubleArray,
    val times: LongArray,
) {

    /** Bars in the series, which is the base chart's bar count by construction. */
    val size: Int get() = values.size

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ComparisonSeries) return false
        return symbol == other.symbol &&
            label == other.label &&
            colour == other.colour &&
            values.contentEquals(other.values) &&
            times.contentEquals(other.times)
    }

    override fun hashCode(): Int {
        var result = symbol.hashCode()
        result = 31 * result + label.hashCode()
        result = 31 * result + colour.hashCode()
        result = 31 * result + values.contentHashCode()
        result = 31 * result + times.contentHashCode()
        return result
    }
}

/**
 * How a compared series is expressed against the base.
 *
 * The choice is not cosmetic and it is not a preference: each of these answers a different question,
 * and a reader who picks the wrong one gets a confident answer to a question they did not ask.
 */
enum class ComparisonBasis {

    /**
     * Percentage moved since the first visible bar, so `0` is where the reader started looking.
     *
     * This is the only honest way to put an instrument priced at 2,300 beside one priced at 0.42 on
     * one axis: absolute prices differ by four orders of magnitude and the cheaper one would be a
     * flat line along the floor. Rebasing to the left edge of the screen — not to the first bar
     * loaded — also means the comparison re-answers itself as the reader pans, which is the whole
     * point: "which has done better *over what I am looking at*".
     */
    PERCENT,

    /**
     * The same rebasing as [PERCENT], with the anchor at `100` instead of `0`.
     *
     * Identical information, and it exists because it is what the index world reads in: an
     * instrument at 118 has gained eighteen percent, and a ratio between two of these lines can be
     * taken by eye. Every value is exactly one hundred above the matching [PERCENT] value, which is
     * a property the tests pin so the two can never drift apart into two separate calculations.
     */
    INDEXED_100,

    /**
     * The compared series divided by the base, bar for bar.
     *
     * A different question entirely, and the reason this basis is worth having: [PERCENT] says which
     * of the two rose more since the left edge, while a ratio line says whether gold is gaining on
     * the dollar *right now*, with no dependence on where the reader happened to start. A rising
     * ratio is the compared instrument outperforming, whatever both are doing in absolute terms.
     * Bars where the base is zero produce `NaN`, because a ratio to nothing is not a number.
     */
    RATIO,

    /**
     * Raw prices, on the compared instrument's own scale.
     *
     * Usually unreadable, and kept anyway because sometimes a reader genuinely means it: two
     * instruments that trade in the same magnitude — two stablecoins, a pair and its inverse, the
     * spot and the future of one asset — are more informative as two real price lines than as two
     * percentages. It requires the renderer to give this series a second axis of its own; drawn
     * against the base's axis it will simply leave the plot, which is why the chart layer must not
     * offer this basis without a second scale to put it on.
     */
    ABSOLUTE,
}

/**
 * How many instruments may be compared at once, base chart excluded.
 *
 * Four, because past four the palette stops working. [comparisonColour] has to keep every overlay
 * distinct from every other overlay, from the up and down candles behind them, and from itself under
 * a red-green deficiency, all at the width of a phone; a fifth line either repeats a hue or takes
 * one close enough to another that a reader has to consult the legend for every line, and a chart
 * that must be decoded line by line is not a faster read than opening the second chart was.
 */
const val MAX_COMPARISONS = 4

/**
 * The buy and sell colours of both themes, which no comparison line may collide with.
 *
 * Copied as literals rather than read from the design system, because this module describes colours
 * in ARGB longs and the palette is only reachable from inside a composable — a pure function that
 * has to *avoid* two colours cannot go and look them up. Kept public so the test that guards the
 * constraint asserts against the same list the palette choice was made from: if the theme's buy or
 * sell ever changes, this list changes with it and the test says immediately whether the comparison
 * palette still clears it.
 */
val MARKET_COLOURS: List<Long> = listOf(
    0xFF00B15C, // buy, dark theme
    0xFFF6465D, // sell, dark theme
    0xFF0E8A4C, // buy, light theme
    0xFFC9203A, // sell, light theme
)

/**
 * The colour for the compared instrument in slot [index].
 *
 * Blue, amber, teal and mauve. None of them is the market green or the market red, which is not a
 * style rule: a comparison line in the buy colour reads as a bullish study of the *base* symbol, and
 * a reader will act on that misreading before they think to check the legend. They are also spread
 * across hue *and* lightness rather than hue alone, so a reader with a red-green deficiency — the
 * common one, and common enough that a trading chart cannot treat it as an edge case — still tells
 * them apart: after the red and green channels collapse, blue, yellow, pale cyan and muted rose are
 * four different lightnesses as well as four different tints.
 *
 * Out-of-range indices wrap rather than throw. A renderer asking for slot five is a bug upstream of
 * here — [MAX_COMPARISONS] is the cap — but a chart that crashes mid-draw is a worse answer to that
 * bug than a chart that repeats a colour.
 */
fun comparisonColour(index: Int): Long {
    val slot = ((index % MAX_COMPARISONS) + MAX_COMPARISONS) % MAX_COMPARISONS
    return COMPARISON_PALETTE[slot]
}

private val COMPARISON_PALETTE = longArrayOf(
    0xFF4C9AFF, // blue
    0xFFE69F00, // amber
    0xFF00C2D1, // teal
    0xFFB07AA1, // mauve
)

/**
 * Put [other] onto [base]'s bar grid, without moving a single base bar.
 *
 * This is the whole difficulty of comparison and the reason this function exists rather than a loop
 * at the call site. Two feeds do not share a calendar: gold stops for the weekend and Bitcoin does
 * not, one venue has a maintenance gap where the other has bars, a session opens an hour later after
 * a clock change, and the timestamps of a bar that "is" the same bar can be offset outright. Zipping
 * the two arrays by position lines Friday against Sunday and draws a comparison that is wrong by
 * whole days without looking wrong anywhere.
 *
 * So each base bar takes the **last value [other] actually printed at or before that bar's time**,
 * carried forward across every missing bar. Carried, never interpolated: an interpolated price is a
 * price nobody traded at, and once it is drawn it is read as one — a reader takes a level off it,
 * and the level was never there. A flat segment across a weekend is the truth, and it looks like
 * what it is.
 *
 * Base bars that precede [other]'s first bar get `NaN`, because the instrument did not exist yet as
 * far as this feed is concerned, and back-filling them with the first known price would invent a
 * history in which the newer instrument was flat for a year.
 *
 * No base bar is ever dropped to make the two match. The base chart's geometry — its bar count, its
 * spacing, every drawing anchored to a bar index — must not change because a reader added an
 * overlay, and a comparison that quietly trims the chart it was added to is a comparison that
 * breaks the thing it was meant to inform. The returned series is therefore always exactly
 * `base.size` long, including when [other] is empty, in which case it is all `NaN`.
 *
 * [symbol], [label] and [colour] are carried through untouched; the defaults exist so a caller that
 * only wants the alignment can call `align(base, other)` and fill in the presentation afterwards.
 */
fun align(
    base: CandleSeries,
    other: CandleSeries,
    symbol: String = "",
    label: String = symbol,
    colour: Long = comparisonColour(0),
): ComparisonSeries {
    val times = base.time.copyOf()
    val values = DoubleArray(times.size) { Double.NaN }
    if (times.isEmpty() || other.isEmpty) {
        return ComparisonSeries(symbol, label, colour, values, times)
    }

    val otherTime = other.time
    val otherClose = other.close
    // One pass over each array: both are ascending, so the cursor into `other` only ever moves
    // forward. A binary search per bar would be the same answer at several times the cost, on a
    // path that runs on every feed tick.
    var cursor = -1
    for (index in times.indices) {
        val at = times[index]
        while (cursor + 1 < otherTime.size && otherTime[cursor + 1] <= at) cursor++
        values[index] = if (cursor >= 0) otherClose[cursor] else Double.NaN
    }
    return ComparisonSeries(symbol, label, colour, values, times)
}

/**
 * Express [series] in [basis], ready to be handed to the renderer as a plain array.
 *
 * [baseIndexFirstVisible] is the base chart's leftmost visible bar, and it is what the rebasing
 * bases are anchored to — not bar zero. A percentage comparison means "since where I am looking",
 * so it has to be recomputed as the reader pans, and passing a viewport index in is what makes that
 * possible without this file knowing what a viewport is.
 *
 * **Leading `NaN` is skipped when choosing the anchor.** A compared instrument that is younger than
 * the base chart has no value at the first visible bar, and anchoring on that `NaN` would make every
 * later value `NaN` too: the overlay would silently vanish, and it would look like a feed failure
 * rather than like a young instrument. Instead the anchor is the series' first real value at or
 * after the first visible bar, so the line starts where the instrument starts and reads zero there.
 * If the series has no real value in view at all there is nothing to anchor on and the result is all
 * `NaN`, which is the correct empty answer.
 *
 * An anchor of exactly zero also yields all `NaN`: a percentage move away from zero is not a number,
 * and the alternative is an axis blown out by an infinity.
 *
 * [baseSeries] is the base instrument's own values, one per bar, and is read only by
 * [ComparisonBasis.RATIO]. [ComparisonBasis.ABSOLUTE] returns a copy of the raw values, so a caller
 * may never assume the result aliases [series]`.values` — it does not, in any basis.
 */
fun rebase(
    series: ComparisonSeries,
    basis: ComparisonBasis,
    baseIndexFirstVisible: Int,
    baseSeries: DoubleArray,
): DoubleArray {
    val values = series.values
    if (basis == ComparisonBasis.ABSOLUTE) return values.copyOf()

    if (basis == ComparisonBasis.RATIO) {
        return DoubleArray(values.size) { index ->
            val over = values[index]
            val under = if (index < baseSeries.size) baseSeries[index] else Double.NaN
            if (over.isFinite() && under.isFinite() && under != 0.0) over / under else Double.NaN
        }
    }

    val anchor = anchorValue(values, baseIndexFirstVisible)
        ?: return DoubleArray(values.size) { Double.NaN }

    // abs on the denominator is the "negated when the anchor is negative" rule: an instrument that
    // was at −40 and is now at −30 has risen, and dividing by the signed anchor would report a fall.
    val denominator = abs(anchor)
    if (denominator == 0.0) return DoubleArray(values.size) { Double.NaN }

    val offset = if (basis == ComparisonBasis.INDEXED_100) 100.0 else 0.0
    return DoubleArray(values.size) { index ->
        val value = values[index]
        if (value.isFinite()) 100.0 * (value - anchor) / denominator + offset else Double.NaN
    }
}

/** The first finite value at or after [from], or null when the series is blank from there on. */
private fun anchorValue(values: DoubleArray, from: Int): Double? {
    var index = from.coerceAtLeast(0)
    while (index < values.size) {
        val value = values[index]
        if (value.isFinite()) return value
        index++
    }
    return null
}

/**
 * The range that holds every finite value across all of [series], or null when none is finite.
 *
 * The price axis has to cover the base and every comparison at once, and computing that per series
 * and taking the union at the call site is where the `NaN`s get in: `min` and `max` on a `Double`
 * propagate `NaN` rather than ignoring it, so one missing bar in one overlay poisons the whole scale
 * and the chart draws nothing at all. Filtering happens here, once.
 *
 * Null rather than an arbitrary `0.0..1.0` when there is nothing finite: an empty axis is a decision
 * the renderer has to make — usually by keeping the base's own range — and a fabricated range would
 * make an absent overlay look like an overlay pinned to zero.
 */
fun combinedRange(series: List<DoubleArray>): ClosedFloatingPointRange<Double>? {
    var low = Double.MAX_VALUE
    var high = -Double.MAX_VALUE
    var seen = false
    for (values in series) {
        for (value in values) {
            if (!value.isFinite()) continue
            if (value < low) low = value
            if (value > high) high = value
            seen = true
        }
    }
    return if (seen) low..high else null
}
