package com.coinepro.core.chart

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chart's pixel arithmetic, pinned to exact values.
 *
 * These are the numbers a reader sees rather than numbers a reader could derive, so the assertions
 * here are exact rather than approximate: "a candle body is five pixels at this zoom" is a claim
 * that either holds or does not, and a tolerance around it would let the whole curve drift a pixel
 * at a time without anything failing. The three properties worth stating in prose — that the gap
 * grows as the reader zooms in, that a wick is never wider than the body it rises out of, and that
 * borders disappear before they can swallow a candle — are each tested as properties as well as at
 * their exact values, because those are the reasons the constants are what they are.
 */
class ChartPixelsTest {

    // ------------------------------------------------------------------ optimalBarWidth

    @Test
    fun `a bar between two and a half and four pixels wide is pinned to three`() {
        // The band where the general curve flickers between two pixels and three as the reader
        // zooms slowly through it. Pinned, so it does not.
        assertEquals(3f, optimalBarWidth(2.5f, 1f), 0f)
        assertEquals(3f, optimalBarWidth(3f, 1f), 0f)
        assertEquals(3f, optimalBarWidth(4f, 1f), 0f)
        // And it is pinned in *device* pixels, so a dense screen gets a proportionally wider body
        // rather than the same three physical dots.
        assertEquals(6f, optimalBarWidth(3f, 2f), 0f)
        assertEquals(9f, optimalBarWidth(3f, 3f), 0f)
    }

    @Test
    fun `the body width at six twelve and twenty pixels of spacing`() {
        assertEquals(5f, optimalBarWidth(6f, 1f), 0f)
        assertEquals(9f, optimalBarWidth(12f, 1f), 0f)
        assertEquals(16f, optimalBarWidth(20f, 1f), 0f)
    }

    @Test
    fun `the body's share of the slot falls toward eighty percent as the bars get wider`() {
        // The asymptote is the whole point of the atan curve: the coefficient runs from one down to
        // 0.8 and never past it, so a zoomed-in chart settles at 80 body to 20 gap rather than
        // continuing to open up until the candles are mostly air.
        val share = { spacing: Float -> optimalBarWidth(spacing, 1f) / spacing }
        assertEquals(0.8333f, share(6f), 0.001f)
        assertEquals(0.8f, share(100f), 0.001f)
        assertEquals(0.8f, share(400f), 0.001f)
        // Approaching from above, and closer at every step.
        assertTrue(share(6f) > share(100f))
        assertTrue(share(100f) >= share(400f))
        assertTrue(share(400f) >= 0.8f)
    }

    @Test
    fun `the gap between candles grows as the reader zooms in`() {
        // A fixed body ratio gives a gap that grows linearly and looks like a picket fence at the
        // wide end. This one grows and then stabilises as a share, which is why candles never
        // touch once there is room for them not to.
        val gap = { spacing: Float -> spacing - optimalBarWidth(spacing, 1f) }
        val gaps = listOf(6f, 12f, 20f, 40f, 100f).map(gap)
        gaps.zipWithNext().forEach { (narrower, wider) ->
            assertTrue("gap must not shrink as bars widen: $gaps", wider >= narrower)
        }
        assertEquals(1f, gap(6f), 0f)
        assertEquals(3f, gap(12f), 0f)
        assertEquals(4f, gap(20f), 0f)
    }

    @Test
    fun `a body never rounds away to nothing`() {
        // Six hundred bars on a phone is under half a pixel each. A floor of zero there is a chart
        // that draws no candles at all.
        assertEquals(1f, optimalBarWidth(0.4f, 1f), 0f)
        assertEquals(2f, optimalBarWidth(0.4f, 2f), 0f)
    }

    // ------------------------------------------------------------------ wickWidth

    @Test
    fun `a wick is one device pixel while there is room for it`() {
        assertEquals(1f, wickWidth(barWidth = 9f, barSpacing = 12f, pixelRatio = 1f), 0f)
        assertEquals(3f, wickWidth(barWidth = 12f, barSpacing = 6f, pixelRatio = 3f), 0f)
    }

    @Test
    fun `a wick is capped at the body it rises out of`() {
        // The failure this cap exists for: a 3x screen zoomed all the way out, where the body has
        // floored to two device pixels and the naive one-device-pixel wick is three. A wick wider
        // than its candle reads as a bar with shoulders.
        assertEquals(2f, wickWidth(barWidth = 2f, barSpacing = 4f, pixelRatio = 3f), 0f)
        assertEquals(1f, wickWidth(barWidth = 1f, barSpacing = 4f, pixelRatio = 3f), 0f)
        listOf(1f, 2f, 3f).forEach { ratio ->
            listOf(0.5f, 1f, 2f, 3f, 8f, 30f).forEach { body ->
                val wick = wickWidth(body, barSpacing = 10f, pixelRatio = ratio)
                assertTrue("wick $wick wider than body $body", wick <= maxOf(body, 1f))
            }
        }
    }

    @Test
    fun `a wick is never thinner than a pixel`() {
        // Below one pixel the platform draws a grey suggestion rather than a line, which is what
        // the wicks on this chart looked like when they were measured in raw pixels.
        assertEquals(1f, wickWidth(barWidth = 0.4f, barSpacing = 0.5f, pixelRatio = 1f), 0f)
    }

    // ------------------------------------------------------------------ borderWidth / drawBorder

    @Test
    fun `a candle keeps its outline while there is fill left between the two sides`() {
        assertEquals(1f, borderWidth(barWidth = 12f, pixelRatio = 1f), 0f)
        assertTrue(drawBorder(barWidth = 12f, borderWidth = borderWidth(12f, 1f)))
        assertEquals(2f, borderWidth(barWidth = 12f, pixelRatio = 2f), 0f)
        assertTrue(drawBorder(barWidth = 12f, borderWidth = borderWidth(12f, 2f)))
        assertEquals(3f, borderWidth(barWidth = 12f, pixelRatio = 3f), 0f)
        assertTrue(drawBorder(barWidth = 12f, borderWidth = borderWidth(12f, 3f)))
    }

    @Test
    fun `the outline vanishes once it would swallow the candle`() {
        // This is why zoomed-out candles do not turn to mush: below the threshold the body is
        // filled solid, which still says up or down, instead of being two overlapping strokes.
        assertFalse(drawBorder(barWidth = 2f, borderWidth = borderWidth(2f, 1f)))
        assertFalse(drawBorder(barWidth = 1f, borderWidth = borderWidth(1f, 1f)))
        assertTrue(drawBorder(barWidth = 3f, borderWidth = borderWidth(3f, 1f)))

        assertFalse(drawBorder(barWidth = 4f, borderWidth = borderWidth(4f, 2f)))
        assertTrue(drawBorder(barWidth = 5f, borderWidth = borderWidth(5f, 2f)))

        assertFalse(drawBorder(barWidth = 6f, borderWidth = borderWidth(6f, 3f)))
        assertTrue(drawBorder(barWidth = 7f, borderWidth = borderWidth(7f, 3f)))
    }

    @Test
    fun `every body wide enough to be outlined is wider than two borders`() {
        listOf(1f, 2f, 3f).forEach { ratio ->
            var sawBoth = false
            var previous = false
            (1..40).forEach { width ->
                val body = width.toFloat()
                val border = borderWidth(body, ratio)
                val outlined = drawBorder(body, border)
                if (outlined) assertTrue(body > border * 2)
                if (outlined != previous) sawBoth = true
                previous = outlined
            }
            // The rule has to actually switch over somewhere in that range, or the assertion above
            // is being satisfied vacuously.
            assertTrue("border rule never changes at pixelRatio $ratio", sawBoth)
        }
    }

    // ------------------------------------------------------------------ axis geometry

    @Test
    fun `the price axis is always an even number of pixels wide`() {
        // The 1px border between plot and axis has to land on a device-pixel boundary at 2x, or it
        // is painted as two half-intensity rows and the edge of the chart looks fuzzy.
        listOf(9f, 10f, 11f, 12f, 13.5f, 16f, 24f, 36f).forEach { fontSize ->
            (0..60).forEach { step ->
                val width = priceAxisWidth(maxLabelWidth = step * 3.7f, fontSize = fontSize)
                assertEquals("odd axis width $width", 0, width.toInt() % 2)
                assertEquals("axis width is not whole: $width", width.toInt().toFloat(), width, 0f)
            }
        }
    }

    @Test
    fun `the price axis leaves room for the label plus its margins`() {
        // 1 + 5 + 5 + 5 + 5 + 40, rounded up to even.
        assertEquals(62f, priceAxisWidth(maxLabelWidth = 40f, fontSize = 12f), 0f)
        // Never narrower than the thing it has to hold.
        listOf(20f, 55.5f, 190f).forEach { label ->
            assertTrue(priceAxisWidth(label, 12f) > label)
        }
    }

    @Test
    fun `the time axis is twenty-eight pixels tall at the reference type size`() {
        assertEquals(28f, timeAxisHeight(12f), 0f)
        // And it scales with the reader's font setting rather than clipping the date.
        assertEquals(50f, timeAxisHeight(24f), 0f)
        assertTrue(timeAxisHeight(18f) > timeAxisHeight(12f))
    }

    @Test
    fun `a separator's grab band is nine times the line and straddles it`() {
        val band = separatorHitRect(separatorY = 100f, density = 1f)
        assertEquals(96f, band.start, 0f)
        assertEquals(105f, band.endInclusive, 0f)
        assertTrue(100f in band)

        val dense = separatorHitRect(separatorY = 300f, density = 3f)
        assertEquals(27f, dense.endInclusive - dense.start, 0.0001f)
        assertTrue(300f in dense)
    }

    @Test
    fun `the price axis carries the larger type and the time axis the smaller`() {
        assertEquals(12f, axisFontSizeSp(isPriceAxis = true), 0f)
        assertEquals(11f, axisFontSizeSp(isPriceAxis = false), 0f)
    }

    // ------------------------------------------------------------------ line styles

    @Test
    fun `every dash pattern scales with the line's own width`() {
        assertArrayEquals(floatArrayOf(), dashIntervals(LineStyleKind.SOLID, 2f), 0f)
        assertArrayEquals(floatArrayOf(2f, 2f), dashIntervals(LineStyleKind.DOTTED, 2f), 0f)
        assertArrayEquals(floatArrayOf(4f, 4f), dashIntervals(LineStyleKind.DASHED, 2f), 0f)
        assertArrayEquals(floatArrayOf(12f, 12f), dashIntervals(LineStyleKind.LARGE_DASHED, 2f), 0f)
        assertArrayEquals(floatArrayOf(2f, 8f), dashIntervals(LineStyleKind.SPARSE_DOTTED, 2f), 0f)

        // Thicken the line and the pattern keeps its proportions rather than closing up.
        assertArrayEquals(floatArrayOf(1f, 1f), dashIntervals(LineStyleKind.DOTTED, 1f), 0f)
        assertArrayEquals(floatArrayOf(24f, 24f), dashIntervals(LineStyleKind.LARGE_DASHED, 4f), 0f)
    }

    @Test
    fun `a solid line has no pattern at all`() {
        // Callers must read this as "no dash effect". An empty interval list is not a valid path
        // effect on Android and throws when handed to one.
        assertEquals(0, dashIntervals(LineStyleKind.SOLID, 3f).size)
    }

    @Test
    fun `a label chip is rounded on one side only`() {
        assertArrayEquals(
            floatArrayOf(2f, 2f, 0f, 0f, 0f, 0f, 2f, 2f),
            labelChipRadii(rightAligned = true, radius = 2f),
            0f,
        )
        assertArrayEquals(
            floatArrayOf(0f, 0f, 2f, 2f, 2f, 2f, 0f, 0f),
            labelChipRadii(rightAligned = false, radius = 2f),
            0f,
        )
    }

    // ------------------------------------------------------------------ separateLabels

    @Test
    fun `overlapping labels are nudged apart and never left touching`() {
        val centres = floatArrayOf(100f, 104f, 106f, 140f)
        val placed = separateLabels(centres, height = 14f, top = 0f, bottom = 400f)
        placed.toList().zipWithNext().forEach { (above, below) ->
            assertTrue("labels still overlap: ${placed.toList()}", below - above >= 14f - 1e-3f)
        }
    }

    @Test
    fun `separation preserves the order the labels came in`() {
        val centres = floatArrayOf(300f, 100f, 102f, 250f, 101f)
        val placed = separateLabels(centres, height = 20f, top = 0f, bottom = 500f)
        for (i in centres.indices) {
            for (j in centres.indices) {
                if (centres[i] < centres[j]) {
                    assertTrue(
                        "label $i crossed label $j: ${placed.toList()}",
                        placed[i] <= placed[j],
                    )
                }
            }
        }
    }

    @Test
    fun `a label that is already clear of its neighbours does not move`() {
        // The nudge is a repair, not a layout. Rewriting positions that were already fine would
        // slide the whole axis every time one pair happened to collide.
        val centres = floatArrayOf(50f, 150f, 250f, 350f)
        val placed = separateLabels(centres, height = 14f, top = 0f, bottom = 400f)
        assertArrayEquals(centres, placed, 1e-4f)
    }

    @Test
    fun `labels are kept inside the column they belong to`() {
        val centres = floatArrayOf(-30f, 2f, 398f, 430f)
        val placed = separateLabels(centres, height = 12f, top = 0f, bottom = 400f)
        placed.forEach { y ->
            assertTrue("label at $y escaped the column", y >= 0f - 1e-3f && y <= 400f + 1e-3f)
        }
    }

    @Test
    fun `a single label is left exactly where it was asked for`() {
        assertArrayEquals(
            floatArrayOf(200f),
            separateLabels(floatArrayOf(200f), height = 14f, top = 0f, bottom = 400f),
            1e-4f,
        )
        assertEquals(0, separateLabels(FloatArray(0), 14f, 0f, 400f).size)
    }

    // ------------------------------------------------------------------ KineticScroll

    @Test
    fun `a fling decays to nothing and then stops on its own`() {
        val scroll = KineticScroll()
        scroll.start(1_200f)
        assertTrue(scroll.isRunning)

        var now = 1_000L
        var travelled = 0f
        var previousStep = Float.MAX_VALUE
        var frames = 0
        // The first tick establishes the clock and moves nothing.
        assertEquals(0f, scroll.tick(now), 0f)
        while (scroll.isRunning && frames < 1_000) {
            now += 16
            val step = scroll.tick(now)
            assertTrue("a fling must never speed up: $step after $previousStep", step <= previousStep + 1e-3f)
            previousStep = step
            travelled += step
            frames++
        }
        assertFalse("the fling never stopped", scroll.isRunning)
        // Roughly v / (1000 * (1 - decay)) pixels, less the tail below the cut-off.
        assertEquals(393f, travelled, 20f)
        // And it is over inside a second and a half of wall clock, not asymptotically approaching
        // stillness for ever.
        assertTrue("a fling ran for ${frames * 16}ms", frames * 16 < 1_600)
        // Ticking a finished animation is harmless.
        assertEquals(0f, scroll.tick(now + 16), 0f)
    }

    @Test
    fun `a fling carries the direction it was thrown in`() {
        val scroll = KineticScroll()
        scroll.start(-1_200f)
        scroll.tick(0L)
        assertTrue(scroll.tick(16L) < 0f)
    }

    @Test
    fun `a release slower than the cut-off starts nothing`() {
        // Twenty pixels a second is a third of a pixel per frame. Below it the chart would not move,
        // it would shimmer — and every frame of it costs a price-scale recomputation.
        val scroll = KineticScroll()
        scroll.start(19f)
        assertFalse(scroll.isRunning)
        assertEquals(0f, scroll.tick(0L), 0f)
    }

    @Test
    fun `stopping a fling ends it immediately`() {
        // What a finger landing on the chart does: the touch always beats the momentum.
        val scroll = KineticScroll()
        scroll.start(2_000f)
        scroll.tick(0L)
        scroll.stop()
        assertFalse(scroll.isRunning)
        assertEquals(0f, scroll.tick(16L), 0f)
    }

    @Test
    fun `a fling starting at frame zero still moves`() {
        // `withFrameMillis` on a fresh process hands out small numbers, and zero is an ordinary
        // one. Treating it as "no clock yet" would make the first fling of a session inert.
        val scroll = KineticScroll()
        scroll.start(1_000f)
        assertEquals(0f, scroll.tick(0L), 0f)
        assertTrue(scroll.tick(16L) > 0f)
    }
}
