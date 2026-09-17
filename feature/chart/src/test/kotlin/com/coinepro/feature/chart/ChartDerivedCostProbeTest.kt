package com.coinepro.feature.chart

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartHistory
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **What one page of history costs the studies** (run Υ, item 1).
 *
 * «چارت کُپ میکنه زمانی که به چپ و راست سوائپ میکنم.»
 *
 * Swiping right walks into history. Near the left edge the chart asks for another page, and a page
 * from the archive is [ChartController.ARCHIVE_PAGE_BARS] bars — five thousand — up to a resident
 * ceiling of [ChartHistory.MAX_RESIDENT_BARS], fifty thousand. Every page that arrives replaces the
 * series, and a new series invalidates `ChartDerived`: **every switched-on study is recomputed over
 * every bar the chart holds**, not over the bars on screen.
 *
 * Run Τ made that reachable. A flick used to cover about a screen; at friction 1.25 it covers two or
 * three, so a reader flicking back through history crosses the load margin several times in a few
 * seconds and the series climbs towards the ceiling. What each of those pages costs is the number
 * this probe prints.
 *
 * It prints rather than asserts a millisecond figure: this container has no GPU, its JIT is cold and
 * the absolute number is about the machine. The **shape** — how the cost grows with the number of
 * bars held — is about the code, and that is what goes in the report.
 */
class ChartDerivedCostProbeTest {

    private fun series(bars: Int): CandleSeries = CandleSeries(
        List(bars) { index ->
            val c = 100.0 + (index % 97) * 0.4 - (index % 31) * 0.3
            Candle(
                t = 1_000L + index * 3_600L,
                o = c - 0.2,
                h = c + 0.6,
                l = c - 0.7,
                c = c,
                v = 10.0 + index % 7,
            )
        },
    )

    /** Nanoseconds to derive [active] over [bars] bars, best of three. */
    private fun cost(bars: Int, active: Set<String>): Long {
        val candles = series(bars)
        var best = Long.MAX_VALUE
        repeat(3) {
            val started = System.nanoTime()
            ChartDerived.of(candles, active, emptyMap())
            best = minOf(best, System.nanoTime() - started)
        }
        return best
    }

    @Test
    fun `a reader's ordinary chart, derived at every series length a page-back can reach`() {
        // What somebody actually has switched on: a line on the price, a band, a strip, and one of
        // the structure studies that draws the levels — the study in the report.
        val active = setOf("ema", "bollinger", "rsi", "sr")
        val lengths = listOf(500, 2_000, 5_000, 10_000, 20_000, ChartHistory.MAX_RESIDENT_BARS)
        val costs = lengths.map { it to cost(it, active) }
        costs.forEach { (bars, nanos) ->
            println("derive $active over $bars bars: ${nanos / 1_000_000}ms (${nanos / 1_000}µs)")
        }
        // The ceiling is reachable by flicking, so what it costs is a fact about the product.
        val ceiling = costs.last().second
        val page = costs.first().second
        println("the ceiling costs ${"%.1f".format(ceiling.toDouble() / page.toDouble())}× a screenful")
        assertTrue("nothing was measured", ceiling > 0 && page > 0)
    }

    @Test
    fun `and the same chart with nothing switched on, which is the floor`() {
        val lengths = listOf(500, 5_000, 20_000, ChartHistory.MAX_RESIDENT_BARS)
        lengths.forEach { bars ->
            val nanos = cost(bars, emptySet())
            println("derive nothing over $bars bars: ${nanos / 1_000}µs")
        }
        assertTrue(true)
    }
}
