package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every pattern on a fixture built by hand, and on a near miss of the same shape.
 *
 * The near misses are the half that matters. A detector that fires on everything vaguely
 * hammer-shaped is worse than none: it puts arrows all over the chart, the reader stops looking at
 * them, and the one that meant something goes past unread. So each pattern is checked twice — once
 * on a bar that is textbook, and once on one that misses a single rule.
 *
 * Prices here are around 100 with ranges of a few points, but nothing in the detector reads them as
 * prices: every fixture would behave identically at 2,400 or at 0.08, which is what
 * `a pattern reads the same on gold and on a coin` proves.
 */
class CandlePatternsTest {

    private fun bar(o: Double, h: Double, l: Double, c: Double) = Candle(0L, o, h, l, c, 1_000.0)

    /** Eight plain bearish bars, each falling two points, ending with a close of [endClose]. */
    private fun falling(endClose: Double, count: Int = 8): List<Candle> =
        (0 until count).map { index ->
            val close = endClose + (count - 1 - index) * 2.0
            bar(o = close + 1.6, h = close + 1.8, l = close - 0.2, c = close)
        }

    /** The same, rising. */
    private fun rising(endClose: Double, count: Int = 8): List<Candle> =
        (0 until count).map { index ->
            val close = endClose - (count - 1 - index) * 2.0
            bar(o = close - 1.6, h = close - 1.8 + 2.0, l = close - 1.8, c = close)
        }

    /** Eight bars that go nowhere: the context in which no reversal pattern should be called. */
    private fun flat(level: Double, count: Int = 8): List<Candle> =
        (0 until count).map { index ->
            val close = level + if (index % 2 == 0) 0.1 else -0.1
            bar(o = level, h = level + 1.0, l = level - 1.0, c = close)
        }

    private fun seriesOf(context: List<Candle>, tail: List<Candle>): CandleSeries =
        CandleSeries((context + tail).mapIndexed { index, candle -> candle.copy(t = 1_700_000_000L + index * 3600L) })

    /** The hit on the last bar, or null. */
    private fun hit(pattern: CandlePattern, context: List<Candle>, tail: List<Candle>): PatternHit? {
        val series = seriesOf(context, tail)
        return CandlePatterns.detect(series, setOf(pattern)).firstOrNull { it.index == series.size - 1 }
    }

    private fun assertFires(pattern: CandlePattern, context: List<Candle>, tail: List<Candle>): PatternHit {
        val found = hit(pattern, context, tail)
        assertNotNull("$pattern should have been found on the last bar", found)
        return found!!
    }

    private fun assertQuiet(pattern: CandlePattern, context: List<Candle>, tail: List<Candle>) {
        assertNull("$pattern fired on a bar that misses one of its rules", hit(pattern, context, tail))
    }

    // ── One bar ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a doji is a bar with almost no body, and a small body is not one`() {
        val doji = listOf(bar(o = 100.0, h = 101.0, l = 99.0, c = 100.02))
        val found = assertFires(CandlePattern.DOJI, falling(100.0), doji)
        assertTrue("a doji after a decline is read as a pause in it", found.bullish)
        // A fifth of the bar is a body. It is a small bar, not an indecisive one.
        assertQuiet(CandlePattern.DOJI, falling(100.0), listOf(bar(o = 100.0, h = 101.0, l = 99.0, c = 100.2)))
    }

    @Test
    fun `a hammer needs the long shadow under a small body`() {
        val hammer = listOf(bar(o = 100.0, h = 100.7, l = 95.0, c = 100.5))
        val found = assertFires(CandlePattern.HAMMER, falling(100.0), hammer)
        assertTrue(found.bullish)
        // The same long shadow under a body four tenths of the bar: that is a bar that fell and
        // came back most of the way, not a rejection of the low.
        assertQuiet(CandlePattern.HAMMER, falling(100.0), listOf(bar(o = 100.0, h = 103.2, l = 97.0, c = 103.0)))
        // And a spinning top: the lower shadow is only half the bar and there is another on top.
        assertQuiet(CandlePattern.HAMMER, falling(100.0), listOf(bar(o = 100.0, h = 105.0, l = 95.0, c = 103.0)))
    }

    @Test
    fun `the same shape is a hammer after a fall and a hanging man after a rise`() {
        val shape = listOf(bar(o = 100.0, h = 100.7, l = 95.0, c = 100.5))
        assertTrue(assertFires(CandlePattern.HAMMER, falling(100.0), shape).bullish)
        assertFalse(assertFires(CandlePattern.HANGING_MAN, rising(100.0), shape).bullish)
        assertQuiet(CandlePattern.HANGING_MAN, falling(100.0), shape)
        assertQuiet(CandlePattern.HAMMER, rising(100.0), shape)
    }

    @Test
    fun `neither is called in a market that was going sideways`() {
        // The shape alone is not the pattern. Without a trend to reverse there is nothing to say.
        val shape = listOf(bar(o = 100.0, h = 100.7, l = 95.0, c = 100.5))
        assertQuiet(CandlePattern.HAMMER, flat(100.0), shape)
        assertQuiet(CandlePattern.HANGING_MAN, flat(100.0), shape)
    }

    @Test
    fun `an inverted hammer after a fall is a shooting star after a rise`() {
        val shape = listOf(bar(o = 100.0, h = 105.0, l = 99.8, c = 100.2))
        assertTrue(assertFires(CandlePattern.INVERTED_HAMMER, falling(100.0), shape).bullish)
        assertFalse(assertFires(CandlePattern.SHOOTING_STAR, rising(100.0), shape).bullish)
        // Shadows on both sides and neither dominant: a spinning top.
        val neither = listOf(bar(o = 100.0, h = 100.5, l = 99.8, c = 100.2))
        assertQuiet(CandlePattern.INVERTED_HAMMER, falling(100.0), neither)
        assertQuiet(CandlePattern.SHOOTING_STAR, rising(100.0), neither)
    }

    @Test
    fun `a marubozu is body and nothing else`() {
        val found = assertFires(
            CandlePattern.MARUBOZU,
            rising(100.0),
            listOf(bar(o = 100.0, h = 110.0, l = 100.0, c = 110.0)),
        )
        assertTrue("the direction of a marubozu is the bar's own", found.bullish)
        // Shadows of five percent each side: close, and not a marubozu.
        assertQuiet(
            CandlePattern.MARUBOZU,
            rising(100.0),
            listOf(bar(o = 100.0, h = 110.6, l = 99.4, c = 110.0)),
        )
    }

    // ── Two bars ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `an engulfing bar must swallow the whole body before it`() {
        val previous = bar(o = 102.0, h = 102.2, l = 99.8, c = 100.0)
        val found = assertFires(
            CandlePattern.ENGULFING,
            falling(102.0),
            listOf(previous, bar(o = 99.5, h = 102.7, l = 99.3, c = 102.5)),
        )
        assertTrue(found.bullish)
        // Opens above the previous close: it covers the top of the body and not the bottom.
        assertQuiet(
            CandlePattern.ENGULFING,
            falling(102.0),
            listOf(previous, bar(o = 100.5, h = 102.7, l = 100.3, c = 102.5)),
        )
    }

    @Test
    fun `a harami is a small body inside a long one, and its cross is a doji`() {
        val previous = bar(o = 104.0, h = 104.2, l = 99.8, c = 100.0)
        val inside = bar(o = 101.0, h = 102.3, l = 100.7, c = 102.0)
        val found = assertFires(CandlePattern.HARAMI, falling(104.0), listOf(previous, inside))
        assertTrue("a small rise inside a long fall reads bullish", found.bullish)
        // Inside, but more than half the body it sits in.
        assertQuiet(
            CandlePattern.HARAMI,
            falling(104.0),
            listOf(previous, bar(o = 101.0, h = 103.7, l = 100.9, c = 103.5)),
        )
        // The cross is the same pattern with a doji in it, and the two must not both fire.
        val cross = bar(o = 101.5, h = 102.0, l = 101.0, c = 101.52)
        assertTrue(assertFires(CandlePattern.HARAMI_CROSS, falling(104.0), listOf(previous, cross)).bullish)
        assertQuiet(CandlePattern.HARAMI, falling(104.0), listOf(previous, cross))
        assertQuiet(CandlePattern.HARAMI_CROSS, falling(104.0), listOf(previous, inside))
    }

    @Test
    fun `a piercing line has to close past the middle of the fall it answers`() {
        val previous = bar(o = 104.0, h = 104.2, l = 99.8, c = 100.0)
        assertTrue(
            assertFires(
                CandlePattern.PIERCING_LINE,
                falling(104.0),
                listOf(previous, bar(o = 99.5, h = 102.7, l = 99.3, c = 102.5)),
            ).bullish,
        )
        // Closes at 101.5, below the midpoint of 102: a bounce, not a piercing line.
        assertQuiet(
            CandlePattern.PIERCING_LINE,
            falling(104.0),
            listOf(previous, bar(o = 99.5, h = 101.7, l = 99.3, c = 101.5)),
        )
    }

    @Test
    fun `a dark cloud cover is the piercing line upside down`() {
        val previous = bar(o = 100.0, h = 104.2, l = 99.8, c = 104.0)
        assertFalse(
            assertFires(
                CandlePattern.DARK_CLOUD_COVER,
                rising(100.0),
                listOf(previous, bar(o = 104.5, h = 104.7, l = 101.3, c = 101.5)),
            ).bullish,
        )
        assertQuiet(
            CandlePattern.DARK_CLOUD_COVER,
            rising(100.0),
            listOf(previous, bar(o = 104.5, h = 104.7, l = 102.3, c = 102.5)),
        )
    }

    @Test
    fun `tweezers are two bars turned back from the same price`() {
        val top = listOf(
            bar(o = 98.0, h = 100.50, l = 97.8, c = 100.0),
            bar(o = 100.0, h = 100.55, l = 97.5, c = 98.0),
        )
        assertFalse(assertFires(CandlePattern.TWEEZER_TOP, rising(98.0), top).bullish)
        // A point and a half apart is not the same level on a three-point bar.
        assertQuiet(
            CandlePattern.TWEEZER_TOP,
            rising(98.0),
            listOf(top.first(), bar(o = 100.0, h = 102.0, l = 97.5, c = 98.0)),
        )

        val bottom = listOf(
            bar(o = 102.0, h = 102.2, l = 99.50, c = 100.0),
            bar(o = 100.0, h = 102.5, l = 99.55, c = 102.0),
        )
        assertTrue(assertFires(CandlePattern.TWEEZER_BOTTOM, falling(102.0), bottom).bullish)
        assertQuiet(
            CandlePattern.TWEEZER_BOTTOM,
            falling(102.0),
            listOf(bottom.first(), bar(o = 100.0, h = 102.5, l = 98.0, c = 102.0)),
        )
    }

    // ── Three bars ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a morning star has to close back inside the fall that started it`() {
        val first = bar(o = 110.0, h = 110.2, l = 103.8, c = 104.0)
        val pause = bar(o = 103.5, h = 103.8, l = 102.9, c = 103.2)
        val found = assertFires(
            CandlePattern.MORNING_STAR,
            falling(112.0),
            listOf(first, pause, bar(o = 103.6, h = 108.2, l = 103.4, c = 108.0)),
        )
        assertTrue(found.bullish)
        assertEquals("the hit is reported on the bar that completes it", 10, found.index)
        // Closes at 106, short of the 107 midpoint: the fall has not been answered.
        assertQuiet(
            CandlePattern.MORNING_STAR,
            falling(112.0),
            listOf(first, pause, bar(o = 103.6, h = 106.2, l = 103.4, c = 106.0)),
        )
    }

    @Test
    fun `an evening star is the morning star upside down`() {
        val first = bar(o = 104.0, h = 110.2, l = 103.8, c = 110.0)
        val pause = bar(o = 110.5, h = 111.1, l = 110.2, c = 110.8)
        assertFalse(
            assertFires(
                CandlePattern.EVENING_STAR,
                rising(102.0),
                listOf(first, pause, bar(o = 110.4, h = 110.6, l = 105.8, c = 106.0)),
            ).bullish,
        )
        assertQuiet(
            CandlePattern.EVENING_STAR,
            rising(102.0),
            listOf(first, pause, bar(o = 110.4, h = 110.6, l = 107.8, c = 108.0)),
        )
    }

    @Test
    fun `three soldiers each open inside the body before them`() {
        val soldiers = listOf(
            bar(o = 100.0, h = 104.3, l = 99.8, c = 104.0),
            bar(o = 102.0, h = 106.3, l = 101.8, c = 106.0),
            bar(o = 104.0, h = 108.3, l = 103.8, c = 108.0),
        )
        assertTrue(assertFires(CandlePattern.THREE_WHITE_SOLDIERS, rising(100.0), soldiers).bullish)
        // The third gaps above the second's body: an acceleration, not a march.
        assertQuiet(
            CandlePattern.THREE_WHITE_SOLDIERS,
            rising(100.0),
            listOf(soldiers[0], soldiers[1], bar(o = 106.5, h = 110.3, l = 106.3, c = 110.0)),
        )
    }

    @Test
    fun `three crows are three soldiers falling`() {
        val crows = listOf(
            bar(o = 108.0, h = 108.2, l = 103.7, c = 104.0),
            bar(o = 106.0, h = 106.2, l = 101.7, c = 102.0),
            bar(o = 104.0, h = 104.2, l = 99.7, c = 100.0),
        )
        assertFalse(assertFires(CandlePattern.THREE_BLACK_CROWS, falling(108.0), crows).bullish)
        assertQuiet(
            CandlePattern.THREE_BLACK_CROWS,
            falling(108.0),
            listOf(crows[0], crows[1], bar(o = 101.5, h = 101.7, l = 97.2, c = 97.5)),
        )
    }

    // ── The properties that hold across all of them ──────────────────────────────────────

    @Test
    fun `a pattern reads the same on gold and on a coin`() {
        // The reason every rule is a ratio. The same hammer, scaled by a factor of thirty thousand,
        // must still be a hammer — an implementation with an absolute threshold in it fails here.
        val hammer = listOf(bar(o = 100.0, h = 100.7, l = 95.0, c = 100.5))
        val cheap = seriesOf(falling(100.0), hammer)
        val expensive = CandleSeries(
            cheap.bars.map { it.copy(o = it.o * 30_000, h = it.h * 30_000, l = it.l * 30_000, c = it.c * 30_000) },
        )
        val one = CandlePatterns.detect(cheap, setOf(CandlePattern.HAMMER))
        val other = CandlePatterns.detect(expensive, setOf(CandlePattern.HAMMER))
        assertEquals(one.map { it.index }, other.map { it.index })
        assertEquals(one.first().strength, other.first().strength, 1e-9)
    }

    @Test
    fun `strength stays inside its range and rewards the cleaner shape`() {
        val textbook = assertFires(
            CandlePattern.HAMMER,
            falling(100.0),
            listOf(bar(o = 100.0, h = 100.2, l = 92.0, c = 100.1)),
        )
        val scruffy = assertFires(
            CandlePattern.HAMMER,
            falling(100.0),
            listOf(bar(o = 100.0, h = 100.35, l = 99.0, c = 100.25)),
        )
        assertTrue(textbook.strength in 0.0..1.0 && scruffy.strength in 0.0..1.0)
        assertTrue(
            "a long clean shadow on a full-sized bar must rank above a tiny one",
            textbook.strength > scruffy.strength,
        )
    }

    @Test
    fun `switching a pattern on produces marks on the right side of the bar`() {
        val series = seriesOf(falling(100.0), listOf(bar(o = 100.0, h = 100.7, l = 95.0, c = 100.5)))
        val markers = CandlePatterns.markersFor(series, setOf("pattern_hammer"))
        assertTrue("a switched-on pattern must draw something", markers.isNotEmpty())
        val mark = markers.last()
        assertFalse("a bullish mark hangs under the bar it describes", mark.above)
        assertEquals(MarkerGlyph.ARROW_UP, mark.glyph)
        assertEquals("چکش", mark.text)
        assertTrue("nothing is drawn for a pattern nobody switched on", CandlePatterns.markersFor(series, emptySet()).isEmpty())
    }

    @Test
    fun `every registered pattern has a Persian name in both directions`() {
        for (option in CandlePatterns.OPTIONS) {
            assertTrue("${option.id} has no label", option.label.isNotBlank())
            assertTrue(option.pattern.persianName(true).isNotBlank())
            assertTrue(option.pattern.persianName(false).isNotBlank())
            assertEquals("ids must be prefixed so nothing confuses one with an indicator", true, option.id.startsWith("pattern_"))
            assertNull(
                "a pattern id must never collide with an indicator id",
                ChartCatalog.INDICATORS.firstOrNull { it.id == option.id },
            )
        }
        for (pattern in CandlePattern.entries) {
            assertNotNull(
                "$pattern is detectable but cannot be switched on by anything",
                CandlePatterns.OPTIONS.firstOrNull { it.pattern == pattern },
            )
        }
    }

    @Test
    fun `an empty series and a flat line produce no hits rather than an exception`() {
        assertTrue(CandlePatterns.detect(CandleSeries.EMPTY).isEmpty())
        val motionless = CandleSeries(
            (0 until 30).map { Candle(1_700_000_000L + it * 3600L, 100.0, 100.0, 100.0, 100.0, 10.0) },
        )
        assertTrue("a bar with no range is not a doji, it is not a bar", CandlePatterns.detect(motionless).isEmpty())
    }
}
