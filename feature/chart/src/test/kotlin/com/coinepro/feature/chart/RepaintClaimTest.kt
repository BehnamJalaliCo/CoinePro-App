package com.coinepro.feature.chart

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.ChartCatalog
import com.coinepro.core.chart.ReplayState
import com.coinepro.core.marketdata.ChartInterval
import com.coinepro.core.marketdata.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The «repaint نمی‌کند» mark, and the one rule that makes it worth anything.
 *
 * A false mark is worse than no mark: it converts an honest limitation into a claim the reader will
 * eventually catch, on the one subject this audience is least forgiving about. So the assertions
 * here are almost all negative — what must *not* carry it.
 */
class RepaintClaimTest {

    private fun series(bars: Int, volume: Double? = 5.0) = CandleSeries(
        (0 until bars).map { index ->
            val price = 100.0 + index * 0.5
            Candle(1_700_000_000L + index * 3600, price, price + 1, price - 1, price + 0.2, volume)
        },
    )

    private fun state(
        indicators: Set<String> = emptySet(),
        interval: ChartInterval = ChartInterval.Preset(Timeframe.H1),
        bars: CandleSeries = series(200),
        replay: ReplayState = ReplayState(),
    ) = ChartUiState(
        symbol = "XAUUSD",
        interval = interval,
        series = bars,
        activeIndicators = indicators,
        replay = replay,
    )

    @Test
    fun `the two studies that rewrite their own past are named as repainting`() {
        assertEquals(RepaintClaim.REPAINTS, RepaintClaims.of("zigzag"))
        assertEquals(RepaintClaim.REPAINTS, RepaintClaims.of("autofib"))
    }

    @Test
    fun `nothing that repaints is ever offered as trustworthy`() {
        for ((id, claim) in RepaintClaims.CLAIMS) {
            if (claim == RepaintClaim.REPAINTS) {
                assertFalse("$id is marked as trustworthy and repaints", claim.isTrustworthy)
            }
        }
        assertTrue(RepaintClaims.trustedAmong(RepaintClaims.CLAIMS.keys).none { it == "zigzag" })
    }

    @Test
    fun `every id this file claims about is a study the app actually offers`() {
        // A claim about an id nothing can switch on is a claim nobody will ever read, and worse, it
        // is a claim that will silently stop matching if a study is renamed.
        for (id in RepaintClaims.CLAIMS.keys) {
            assertNotNull(
                "$id has a repaint claim and is not in the catalogue",
                ChartCatalog.INDICATORS.firstOrNull { it.id == id },
            )
        }
    }

    @Test
    fun `a chart with a repainting study on it carries no mark at all`() {
        // Not even for the pivots beside it. A reader must never read «repaint نمی‌کند» on a chart
        // and apply it to the zigzag under their finger.
        assertNull(repaintMark(state(setOf("pivots", "zigzag"))))
    }

    @Test
    fun `pivots alone are settled`() {
        assertEquals(RepaintClaim.SETTLED, repaintMark(state(setOf("pivots"))))
        assertTrue(repaintSubjects(state(setOf("pivots"))).isNotEmpty())
    }

    @Test
    fun `a late-confirming study drags the whole mark down to late`() {
        // Swings need the bars to their right, one of which is the bar still printing. Reporting
        // the pair as settled would be claiming the newest arrow cannot be withdrawn.
        assertEquals(RepaintClaim.LATE, repaintMark(state(setOf("pivots", "swings"))))
    }

    @Test
    fun `a chart with nothing that qualifies says nothing rather than something reassuring`() {
        assertNull(repaintMark(state(setOf("ema", "rsi"))))
        assertTrue(repaintSubjects(state(setOf("ema", "rsi"))).isEmpty())
    }

    @Test
    fun `an issued setup settles the moment it is drawn`() {
        assertEquals(RepaintClaim.SETTLED, RepaintClaims.SIGNAL)
        val marked = repaintMark(state(), signalOnChart = true)
        assertEquals(RepaintClaim.SETTLED, marked)
        assertTrue(repaintSubjects(state(), signalOnChart = true).isNotEmpty())
    }

    @Test
    fun `a repainting study cancels the mark even for a setup drawn beside it`() {
        assertNull(repaintMark(state(setOf("zigzag")), signalOnChart = true))
    }

    @Test
    fun `every claim says something different in both the mark and the sentence under it`() {
        val labels = RepaintClaim.entries.map { it.label }
        val notes = RepaintClaim.entries.map { it.note }
        assertEquals(labels.size, labels.toSet().size)
        assertEquals(notes.size, notes.toSet().size)
    }
}
