package com.coinepro.feature.heatmap

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ramp, which is the part of a heatmap that is wrong most often and noticed least.
 *
 * A colour that is a little off looks like a design choice, so none of this can be left to a
 * glance. What is asserted here is the behaviour a reader depends on without knowing it: that zero
 * is neutral, that an outlier is clamped rather than allowed to flatten everything else, that the
 * direction follows the reader's own buy/sell convention, and that the colour-blind palette is
 * genuinely readable without the red-green axis rather than merely named after it.
 */
class HeatmapColourTest {

    private fun red(argb: Long) = ((argb shr 16) and 0xFF).toInt()
    private fun green(argb: Long) = ((argb shr 8) and 0xFF).toInt()
    private fun blue(argb: Long) = (argb and 0xFF).toInt()

    @Test
    fun `exactly zero is the palette's neutral, in every palette`() {
        HeatmapPalette.entries.forEach { palette ->
            assertEquals(
                "$palette should be neutral at zero",
                HeatmapColours.neutralOf(palette),
                HeatmapColours.colourFor(0.0, 5.0, palette),
            )
        }
    }

    @Test
    fun `a value past the scale is clamped instead of running off the ramp`() {
        val atScale = HeatmapColours.colourFor(5.0, 5.0, HeatmapPalette.CLASSIC)
        // Two, ten and two hundred times the scale are all simply "off the end". Without the clamp
        // the arithmetic would keep going and a single runaway coin would decide the whole map.
        assertEquals(atScale, HeatmapColours.colourFor(10.0, 5.0, HeatmapPalette.CLASSIC))
        assertEquals(atScale, HeatmapColours.colourFor(1_000.0, 5.0, HeatmapPalette.CLASSIC))
        val belowScale = HeatmapColours.colourFor(-5.0, 5.0, HeatmapPalette.CLASSIC)
        assertEquals(belowScale, HeatmapColours.colourFor(-99.0, 5.0, HeatmapPalette.CLASSIC))
        assertNotEquals(atScale, belowScale)
    }

    @Test
    fun `a value inside the scale is not clamped, or the map would have two colours`() {
        val half = HeatmapColours.colourFor(2.5, 5.0, HeatmapPalette.CLASSIC)
        assertNotEquals(half, HeatmapColours.colourFor(5.0, 5.0, HeatmapPalette.CLASSIC))
        assertNotEquals(half, HeatmapColours.neutralOf(HeatmapPalette.CLASSIC))
    }

    @Test
    fun `a scale of zero or a value that is not a number falls back to neutral`() {
        val neutral = HeatmapColours.neutralOf(HeatmapPalette.CLASSIC)
        assertEquals(neutral, HeatmapColours.colourFor(3.0, 0.0, HeatmapPalette.CLASSIC))
        assertEquals(neutral, HeatmapColours.colourFor(Double.NaN, 5.0, HeatmapPalette.CLASSIC))
        assertEquals(neutral, HeatmapColours.colourFor(3.0, Double.NaN, HeatmapPalette.CLASSIC))
    }

    @Test
    fun `classic draws a rise green and a fall red, and swaps when the reader asked for red up`() {
        val rise = HeatmapColours.colourFor(4.0, 5.0, HeatmapPalette.CLASSIC, risingIsGreen = true)
        val fall = HeatmapColours.colourFor(-4.0, 5.0, HeatmapPalette.CLASSIC, risingIsGreen = true)
        assertTrue("a rise should be greener than it is red", green(rise) > red(rise))
        assertTrue("a fall should be redder than it is green", red(fall) > green(fall))

        val flippedRise = HeatmapColours.colourFor(4.0, 5.0, HeatmapPalette.CLASSIC, risingIsGreen = false)
        val flippedFall = HeatmapColours.colourFor(-4.0, 5.0, HeatmapPalette.CLASSIC, risingIsGreen = false)
        // The exchange has to be complete: a partial swap is the worst outcome, a map drawing rises
        // in red beside a percentage drawn in green.
        assertEquals(fall, flippedRise)
        assertEquals(rise, flippedFall)
    }

    @Test
    fun `monochrome ignores the direction preference, because lightness is not a convention`() {
        HeatmapPalette.MONOCHROME.let { palette ->
            listOf(-4.0, -1.0, 1.0, 4.0).forEach { value ->
                assertEquals(
                    HeatmapColours.colourFor(value, 5.0, palette, risingIsGreen = true),
                    HeatmapColours.colourFor(value, 5.0, palette, risingIsGreen = false),
                )
            }
        }
        assertTrue(
            "a rise should be the lighter end",
            HeatmapColours.luminanceOf(HeatmapColours.colourFor(5.0, 5.0, HeatmapPalette.MONOCHROME)) >
                HeatmapColours.luminanceOf(HeatmapColours.colourFor(-5.0, 5.0, HeatmapPalette.MONOCHROME)),
        )
    }

    @Test
    fun `the colour-blind ramp carries its meaning in the blue channel`() {
        val samples = (-4..4).map { step ->
            HeatmapColours.colourFor(step / 4.0, 1.0, HeatmapPalette.COLOUR_BLIND)
        }
        val blues = samples.map(::blue)

        // Monotone from one end of the ramp to the other. This is the property that makes the
        // scheme work for a reader who sees no red-green difference at all: whatever happens to the
        // other two channels, blue alone already orders the ramp.
        blues.zipWithNext { low, high ->
            assertTrue("blue channel is not monotone across the ramp: $blues", high > low)
        }
        // And it travels far enough to be seen, rather than technically varying.
        assertTrue("blue channel barely moves: $blues", blues.last() - blues.first() > 120)

        // No two stops of the ramp are told apart by red and green alone.
        for (a in samples.indices) {
            for (b in a + 1 until samples.size) {
                assertNotEquals(
                    "stops $a and $b differ only in red and green",
                    blue(samples[a]),
                    blue(samples[b]),
                )
            }
        }
    }

    @Test
    fun `the classic ramp is the one that does depend on red and green, which is why the other exists`() {
        // The control for the test above. If this ever started varying its blue channel widely, the
        // colour-blind assertions would stop proving anything about the difference between them.
        val blues = (-4..4).map { step ->
            blue(HeatmapColours.colourFor(step / 4.0, 1.0, HeatmapPalette.CLASSIC))
        }
        assertTrue("classic should not be carrying the ramp in blue: $blues", blues.max() - blues.min() < 80)
    }

    @Test
    fun `the ink flips so a label is never near-white on a near-white tile`() {
        HeatmapPalette.entries.forEach { palette ->
            listOf(-1.0, -0.5, 0.0, 0.5, 1.0).forEach { t ->
                val tile = HeatmapColours.colourFor(t, 1.0, palette)
                val ink = HeatmapColours.labelInkFor(tile)
                val contrast = abs(HeatmapColours.luminanceOf(tile) - HeatmapColours.luminanceOf(ink))
                assertTrue("$palette at $t leaves the label at $contrast", contrast > 0.1)
            }
        }
    }

    @Test
    fun `the scale ignores one runaway market rather than normalising the map against it`() {
        val ordinary = List(20) { 1.5 }
        val withOutlier = ordinary + 340.0
        // The maximum would be 340 and would map every real tile into the first half a percent of
        // the ramp — two hundred squares of grey around one screaming one.
        assertEquals(1.5, HeatmapColours.scaleFor(withOutlier), 0.001)
    }

    @Test
    fun `the scale is held inside its band at both ends`() {
        assertEquals("a dead flat session must not be amplified", 0.5, HeatmapColours.scaleFor(List(10) { 0.01 }), 0.001)
        assertEquals("a violent one must not wash the map out", 25.0, HeatmapColours.scaleFor(List(10) { 90.0 }), 0.001)
        assertEquals("no data is not a scale of zero", 0.5, HeatmapColours.scaleFor(emptyList()), 0.001)
        assertEquals(0.5, HeatmapColours.scaleFor(listOf(Double.NaN, Double.POSITIVE_INFINITY)), 0.001)
    }

    @Test
    fun `the scale reads the ninetieth percentile of the magnitudes`() {
        // Ten values, so the ninetieth percentile is the ninth of them once sorted. The sign is
        // irrelevant — a fall of four is as far from neutral as a rise of four — and the tenth,
        // the largest, is deliberately not the answer.
        val values = listOf(-1.0, 2.0, -3.0, 4.0, 5.0, -6.0, 7.0, 8.0, -9.0, 10.0)
        assertEquals(9.0, HeatmapColours.scaleFor(values, floor = 0.5, ceiling = 100.0), 0.001)
    }
}
