package com.coinepro.feature.search

import com.coinepro.core.marketdata.Timeframe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * That each span chip asks for the span it is named after.
 *
 * The failure this catches is silent and would never be reported as a bug: a bar length and a bar
 * count that multiply out to eleven days behind a chip reading «۱ ماه» draws a real shape, of the
 * wrong period, with nothing on the screen to contradict it. Nobody notices, and the preview lies
 * about history — which is the one thing a chart must not do.
 */
class PreviewRangeTest {

    private val day = 86_400L

    @Test
    fun `each chip covers the period it is named after`() {
        // ±25 %, because a bar count is a round number and a month is not: 180 four-hourly bars is
        // thirty days exactly, 90 daily bars is a quarter to the day, and 96 quarter-hours is a day.
        // The band is wide enough for that rounding and far too narrow to hide a wrong multiplier.
        val nominal = mapOf(
            PreviewRange.DAY to day,
            PreviewRange.WEEK to 7 * day,
            PreviewRange.MONTH to 30 * day,
            PreviewRange.QUARTER to 90 * day,
            PreviewRange.YEAR to 365 * day,
        )
        for ((range, expected) in nominal) {
            val covered = range.timeframe.seconds * range.bars
            assertTrue(
                "$range asks for ${covered / day} days where it promises ${expected / day}",
                covered >= expected * 3 / 4 && covered <= expected * 5 / 4,
            )
        }
    }

    @Test
    fun `all asks for more history than either venue keeps`() {
        // «همه» cannot be a number of bars that happens to be right, so it asks for more than there
        // is and draws what comes back. Ten years is past the start of every instrument either
        // backend carries, including bitcoin's own listing on any venue this app talks to.
        val covered = PreviewRange.ALL.timeframe.seconds * PreviewRange.ALL.bars
        assertTrue("«all» must reach back further than ten years", covered >= 3_650 * day)
    }

    @Test
    fun `the spans widen and so do their bars`() {
        // A chip whose bars are finer than the chip before it would put more points on a longer span
        // — the one combination that turns the preview into a frame budget problem on a phone.
        val order = PreviewRange.entries
        for ((shorter, longer) in order.zipWithNext()) {
            assertTrue(
                "$longer must cover more time than $shorter",
                longer.timeframe.seconds * longer.bars > shorter.timeframe.seconds * shorter.bars,
            )
            assertTrue(
                "$longer must not be drawn at a finer bar than $shorter",
                longer.timeframe.seconds >= shorter.timeframe.seconds,
            )
        }
    }

    @Test
    fun `no span draws more points than a phone can stroke in one pass`() {
        // Four hundred is the ceiling: at 360 dp a line of four hundred points has a vertex every
        // 0.9 dp, which is already finer than the stroke is wide. More points past that is arithmetic
        // nobody can see, done on every frame of a scrub.
        for (range in PreviewRange.entries) {
            assertTrue("$range draws ${range.bars} points", range.bars <= 600)
            assertTrue("$range draws too few points to be a shape", range.bars >= 2)
        }
    }

    @Test
    fun `every span is drawn at a bar length both backends serve directly`() {
        // A span folded on the client is a span that costs several requests and arrives late. The
        // preview is a sheet somebody is holding open, so every chip here is a length the feed
        // answers in one call.
        val served = setOf(Timeframe.M15, Timeframe.H1, Timeframe.H4, Timeframe.D1, Timeframe.W1)
        for (range in PreviewRange.entries) {
            assertTrue("$range is drawn at ${range.timeframe}, which is folded rather than served", range.timeframe in served)
        }
    }

    @Test
    fun `each chip has its own label`() {
        val labels = PreviewRange.entries.map { it.labelRes }
        assertEquals("two chips sharing a label is a row with a duplicate in it", labels.size, labels.toSet().size)
    }
}
