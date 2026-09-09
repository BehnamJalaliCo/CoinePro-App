package com.coinepro.core.script

import com.coinepro.core.chart.Line

/**
 * Handing a series with gaps to the indicator library, safely.
 *
 * The library takes a `DoubleArray` and several of its members — `sma`, `ema` — carry a running sum
 * or a recursion forward across the whole array. That is the right implementation for a price
 * column, which has a value at every bar. It is the wrong thing to feed a *derived* series, which
 * does have gaps: an RSI has a warm-up, `close[5]` has five empty bars at the front, and a division
 * that hit a zero leaves a hole in the middle.
 *
 * Filling a gap with zero and letting a running sum absorb it produces a number rather than an
 * error, and that number is wrong for as long as the recursion remembers it — which for an EMA is
 * forever.
 *
 * So a gapped series is not filled. It is **started after its last gap**: everything up to and
 * including the final absent bar is treated as warm-up, the indicator runs over the contiguous
 * tail, and the result is placed back at the right offset with the front left absent.
 *
 * The cost is some leading bars on a series that has an interior hole. The alternative is a chart
 * that is confidently wrong, and a reader has no way to tell those apart by looking.
 */
internal class Source private constructor(
    val values: DoubleArray,
    /** Where [values] begins within the full series. */
    val offset: Int,
    private val size: Int,
) {

    /** Puts a result computed over [values] back where it belongs in the full series. */
    fun realign(result: Line): Line = if (offset == 0) {
        result
    } else {
        Line.of(size) { index -> if (index < offset) null else result[index - offset] }
    }

    companion object {
        fun of(line: Line): Source {
            var start = 0
            for (index in 0 until line.size) {
                if (!line.isPresent(index)) start = index + 1
            }
            if (start >= line.size) {
                // Nothing present at all. An empty array rather than a special case at every call
                // site: every indicator over it returns nothing, which is the truthful answer.
                return Source(DoubleArray(0), line.size, line.size)
            }
            val values = DoubleArray(line.size - start) { line.raw(start + it) }
            return Source(values, start, line.size)
        }
    }
}

/** Runs an indicator over a series that may have gaps, and re-aligns the result. */
internal inline fun Line.through(indicator: (DoubleArray) -> Line): Line {
    val source = Source.of(this)
    if (source.values.isEmpty()) return Line.empty(size)
    return source.realign(indicator(source.values))
}

/**
 * The same, for an indicator that reads three columns at once — ATR, CCI, the stochastic.
 *
 * All three are trimmed to the **latest** start among them, so the arrays stay the same length and
 * aligned with each other. Trimming each to its own start would silently pair bar 40 of one with
 * bar 37 of another.
 */
internal inline fun Line.throughTriple(
    second: Line,
    third: Line,
    indicator: (DoubleArray, DoubleArray, DoubleArray) -> Line,
): Line {
    val a = Source.of(this)
    val b = Source.of(second)
    val c = Source.of(third)
    val start = maxOf(a.offset, b.offset, c.offset)
    if (start >= size) return Line.empty(size)
    val length = size - start
    val first = DoubleArray(length) { this.raw(start + it) }
    val middle = DoubleArray(length) { second.raw(start + it) }
    val last = DoubleArray(length) { third.raw(start + it) }
    val result = indicator(first, middle, last)
    return if (start == 0) result else Line.of(size) { index ->
        if (index < start) null else result[index - start]
    }
}
