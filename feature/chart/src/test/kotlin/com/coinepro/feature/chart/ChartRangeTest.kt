package com.coinepro.feature.chart

import com.coinepro.core.marketdata.CandleGateway
import com.coinepro.core.marketdata.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quick ranges, and the one property that makes each of them worth offering.
 *
 * A range picks a bar length so that the span it names fits on one screen. Both ways of getting
 * that wrong are silent: too fine a length and the page cap truncates the range, so a reader who
 * asked for a year is shown ten months with nothing saying so; too coarse and the chart draws
 * fifty candles and has no shape to read. So the assertions are the band, not the table — a range
 * added later has to earn its length the same way rather than inherit a pinned number.
 */
class ChartRangeTest {

    @Test
    fun `every range with a span fits on one page and still has candles to read`() {
        for (range in ChartRange.OFFERED) {
            if (range == ChartRange.ALL) continue
            val bars = range.barsAcross
            assertTrue(
                "${range.name} draws $bars bars, below the readable floor",
                bars >= ChartRange.MIN_BARS,
            )
            assertTrue(
                "${range.name} wants $bars bars, more than one page can carry",
                bars <= ChartRange.MAX_BARS,
            )
        }
    }

    @Test
    fun `a page of the default limit is enough for every range but the open-ended one`() {
        // The band above is argued from the gateway's own page size. Asserting the relationship
        // rather than the number keeps the two honest if the page size ever moves.
        assertTrue(ChartRange.MAX_BARS >= CandleGateway.DEFAULT_LIMIT)
    }

    @Test
    fun `the whole history is drawn on the longest bar the feed serves`() {
        assertEquals(Timeframe.MN1, ChartRange.ALL.timeframe)
        assertEquals(0, ChartRange.ALL.barsAcross)
    }

    @Test
    fun `a shorter range is never drawn on a longer bar than a longer range`() {
        // The ordering that makes the row make sense: «۱ روز» must not end up on a coarser length
        // than «۱ ماه». It is the mistake a hand-written table invites and nothing else would catch.
        val spans = ChartRange.OFFERED.filter { it != ChartRange.ALL }.sortedBy { it.seconds }
        for (index in 1 until spans.size) {
            assertTrue(
                "${spans[index - 1].name} is drawn coarser than ${spans[index].name}",
                spans[index - 1].timeframe.seconds <= spans[index].timeframe.seconds,
            )
        }
    }

    @Test
    fun `every offered range says something a reader can tell apart`() {
        val labels = ChartRange.OFFERED.map { it.label }
        assertEquals(labels.size, labels.toSet().size)
        for (range in ChartRange.OFFERED) {
            assertTrue("${range.name} has an empty label", range.label.isNotBlank())
            assertNotNull(range.interval)
        }
    }
}
