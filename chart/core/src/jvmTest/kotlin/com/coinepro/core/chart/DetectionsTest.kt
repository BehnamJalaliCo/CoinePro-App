package com.coinepro.core.chart

import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The automatic detections and the technical rating (5.13.0). */
class DetectionsTest {

    private fun series(closes: List<Double>, gapAt: Int? = null): CandleSeries = CandleSeries(
        closes.mapIndexed { i, c ->
            // The open halfway from the last close, so neighbouring bars do not share a low and a
            // strict pivot can form; a gap bar opens a full percent away instead.
            val open = if (i == gapAt) c * 1.01 else (closes.getOrElse(i - 1) { c } + c) / 2
            Candle(1_700_000_000L + i * 3_600L, open, maxOf(open, c) + 0.2, minOf(open, c) - 0.2, c, 100.0 + i % 7)
        },
    )

    @Test
    fun `a gap is a bar that opens away from the last close, and only that`() {
        val flat = series(List(50) { 100.0 + sin(it / 30.0) })
        assertTrue(Detections.gaps(flat).isEmpty())
        val gapped = series(List(50) { 100.0 + sin(it / 30.0) }, gapAt = 20)
        val found = Detections.gaps(gapped)
        assertEquals(listOf(20), found.map { it.index })
        assertTrue(found.single().up)
    }

    @Test
    fun `a lower low with a higher RSI low is a regular bullish divergence`() {
        // A fall, a sharp bounce, then a slower drift to a slightly lower low: price makes the
        // lower low, momentum does not.
        val closes = ArrayList<Double>()
        repeat(30) { closes += 120.0 - it * 1.0 }        // steep fall to 91
        repeat(12) { closes += 92.2 + it * 1.2 }         // bounce
        repeat(25) { closes += 104.2 - it * 0.57 }       // slow drift down, below 91
        repeat(12) { closes += 90.5 + it * 1.0 }         // recovery to confirm the pivot
        val found = Detections.rsiDivergences(series(closes))
        assertTrue("no divergence found: $found", found.any { it.bullish && !it.hidden })
        for (d in found) assertTrue(d.from < d.to)
    }

    @Test
    fun `the swings alternate high and low`() {
        val s = series(List(200) { 100.0 + 8 * sin(it / 6.0) + (it % 5) * 0.3 })
        val swings = Detections.alternatingSwings(s, 3, 3)
        assertTrue("only ${swings.size} swings", swings.size >= 6)
        for (k in 1 until swings.size) assertFalse(swings[k].high == swings[k - 1].high)
    }

    @Test
    fun `a textbook gartley is found and named`() {
        // X=100, A=110 (up 10), B=103.82 (0.618 of XA), C=108 (0.676 of AB), D=102.14 (0.786 of XA).
        val points = listOf(100.0, 110.0, 103.82, 108.0, 102.14, 106.0)
        val closes = ArrayList<Double>()
        repeat(20) { closes += 105.0 + (if (it % 2 == 0) 0.1 else -0.1) }
        for (k in 1 until points.size) {
            val from = points[k - 1]
            val to = points[k]
            for (step in 0 until 8) closes += from + (to - from) * step / 8
        }
        repeat(6) { closes += 106.0 }
        val s = CandleSeries(
            closes.mapIndexed { i, c -> Candle(1_700_000_000L + i * 3_600L, c, c + 0.01, c - 0.01, c) },
        )
        val found = Detections.harmonics(s)
        assertTrue("no pattern: $found", found.any { it.pattern == Detections.Harmonic.GARTLEY && it.bullish })
    }

    @Test
    fun `the technical rating stays between minus one and one and warms up at sixty bars`() {
        val s = series(List(300) { 100.0 + 10 * sin(it / 15.0) + it * 0.05 })
        val rating = TechnicalRating.of(s)
        for (i in 0 until TechnicalRating.MIN_BARS - 1) assertTrue(rating.overall[i].isNaN())
        for (i in TechnicalRating.MIN_BARS - 1 until s.size) {
            assertTrue(rating.overall[i] in -1.0..1.0)
            assertTrue(rating.averages[i] in -1.0..1.0)
            assertTrue(rating.oscillators[i] in -1.0..1.0)
        }
        // A steady climb: price above every average but the Hull, which fits a straight line
        // exactly and so votes neither way.
        val up = series(List(300) { 100.0 + it * 0.5 })
        val last = TechnicalRating.of(up).averages.last()
        assertTrue("averages at $last", last > 0.9)
        assertEquals(TechnicalRating.Verdict.STRONG_BUY, TechnicalRating.verdictOf(0.6))
        assertEquals(TechnicalRating.Verdict.NEUTRAL, TechnicalRating.verdictOf(0.05))
        assertEquals(TechnicalRating.Verdict.SELL, TechnicalRating.verdictOf(-0.3))
    }

    @Test
    fun `every detection draws through the catalogue`() {
        val s = series(List(300) { 100.0 + 10 * sin(it / 9.0) + (it % 11) * 0.4 }, gapAt = 150)
        for (id in listOf("harmonics", "divergence", "gaps")) {
            val option = ChartCatalog.INDICATORS.first { it.id == id }
            val overlay = ChartCatalog.structureFor(option, s)
            assertTrue("$id is a structure study", option.pane == IndicatorPane.STRUCTURE)
            if (id == "gaps") assertFalse("gaps found nothing", overlay.isEmpty)
        }
        val pane = ChartCatalog.paneFor(ChartCatalog.INDICATORS.first { it.id == "techrating" }, s)
        assertTrue(pane!!.title.startsWith("Technical Rating"))
        assertEquals(3, pane.lines.size)
    }
}
