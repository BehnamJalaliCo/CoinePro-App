package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbers TradingView publishes about itself, held against the numbers this app draws with.
 *
 * ### A third class of evidence
 *
 * `docs/design/TRADINGVIEW_VISUAL_PARITY.md` is strict about what may be a *pixel* golden: a PNG
 * off the real Android app on the canonical device, and nothing else. That rule stands and this
 * file does not weaken it.
 *
 * But it is not the only kind of evidence there is. Some of TradingView's values are not read off
 * an image at all — they are **published constants**: the Charting Library's own
 * `ChartPropertiesOverrides` defaults, and the arithmetic in the open-source Lightweight Charts
 * renderer that the paid library shares. A number taken from a vendor's own source is stronger
 * than a number sampled from a screenshot, not weaker, because there is no encoder, no scale
 * factor and no display profile between it and the truth.
 *
 * What it cannot do is answer a question about *layout on a phone*, because a source default is
 * not a rendered frame. So the division of labour is:
 *
 * * **published constants → this file.** Colour, dash tables, and the renderer's own arithmetic.
 * * **device captures → `scripts/visual/compare_tradingview_reference.py`.** Position, size, gap,
 *   baseline, and everything else that only exists once something has been drawn.
 *
 * ### Why the arithmetic is asserted through the functions rather than the constants
 *
 * The private constants in `ChartPixels` are an implementation. What TradingView actually publishes
 * is the *behaviour* — that a candle at six units of spacing is five pixels of body and one of gap,
 * that a time axis at twelve-point type is twenty-eight pixels tall. Asserting the behaviour means
 * this file keeps passing through a refactor and keeps failing on a change of meaning, which is the
 * only distinction worth having.
 */
class TradingViewSourceConstantsTest {

    /* ------------------------------------------------------------------ colour */

    /**
     * The candle pair, and the correction that matters most in this whole area.
     *
     * `#26a69a` / `#ef5350` is the pair every clone copies, and it is **Google's Material palette**
     * — the default of the open-source Lightweight Charts library, not TradingView's own colour.
     * The product's colour is `#089981` / `#F23645`, from the Advanced Charts defaults. Anything
     * that drifts back toward the Material pair is a chart that has stopped being this reference's.
     */
    @Test
    fun candleColoursAreTheProductsAndNotMaterials() {
        assertEquals(0xFF089981, TradingViewPalette.UP)
        assertEquals(0xFFF23645, TradingViewPalette.DOWN)
        assertFalse("Material's #26a69a is not TradingView's up", TradingViewPalette.UP == 0xFF26A69A)
        assertFalse("Material's #ef5350 is not TradingView's down", TradingViewPalette.DOWN == 0xFFEF5350)
    }

    /* ------------------------------------------------------------------ dash tables */

    /**
     * The five line styles, exactly as the renderer defines them.
     *
     * Every interval is a multiple of the line's own width — which is the part worth guarding.
     * A fixed pixel pattern looks right at one stroke width and wrong at every other, so a reader
     * who thickens a drawing gets a solid rule with pinholes in it instead of a heavier dash.
     */
    @Test
    fun dashPatternsScaleWithTheStroke() {
        assertArray(floatArrayOf(), dashIntervals(LineStyleKind.SOLID, 1f))
        assertArray(floatArrayOf(1f, 1f), dashIntervals(LineStyleKind.DOTTED, 1f))
        assertArray(floatArrayOf(2f, 2f), dashIntervals(LineStyleKind.DASHED, 1f))
        assertArray(floatArrayOf(6f, 6f), dashIntervals(LineStyleKind.LARGE_DASHED, 1f))
        assertArray(floatArrayOf(1f, 4f), dashIntervals(LineStyleKind.SPARSE_DOTTED, 1f))

        // And at four times the width, four times the pattern.
        assertArray(floatArrayOf(8f, 8f), dashIntervals(LineStyleKind.DASHED, 4f))
        assertArray(floatArrayOf(4f, 16f), dashIntervals(LineStyleKind.SPARSE_DOTTED, 4f))
    }

    /* ------------------------------------------------------------------ candle geometry */

    /**
     * The published body/gap table, at the three spacings the renderer's own notes give.
     *
     * Six is the default spacing, and the ratio drifts toward roughly eighty/twenty as a reader
     * zooms in rather than staying fixed. That drift is the point: candles get wider *and* keep a
     * gap, so a dense chart never fuses into a solid band. Most clones pin a ratio and look wrong
     * at both ends of the zoom.
     */
    @Test
    fun candleBodyAndGapFollowThePublishedTable() {
        val cases = listOf(
            Triple(6f, 5f, 1f),
            Triple(12f, 9f, 3f),
            Triple(20f, 16f, 4f),
        )
        cases.forEach { (spacing, body, gap) ->
            val width = optimalBarWidth(spacing, pixelRatio = 1f)
            assertEquals("body at barSpacing $spacing", body, width, 0f)
            assertEquals("gap at barSpacing $spacing", gap, spacing - width, 0f)
        }
    }

    /**
     * The special case, which exists so a very tight chart does not collapse.
     *
     * Between 2.5 and 4 units of spacing the width is pinned at three device pixels rather than
     * computed — the curve would hand back one or two there, and a chart of one-pixel candles is a
     * histogram of noise.
     */
    @Test
    fun theTightSpacingSpecialCaseIsPinned() {
        listOf(2.5f, 3f, 3.7f, 4f).forEach { spacing ->
            assertEquals("at barSpacing $spacing", 3f, optimalBarWidth(spacing, pixelRatio = 1f), 0f)
        }
        assertEquals("at 2x density", 6f, optimalBarWidth(3f, pixelRatio = 2f), 0f)
    }

    /** A candle never rounds away to nothing, however far out the reader zooms. */
    @Test
    fun aCandleIsNeverThinnerThanADevicePixel() {
        listOf(0.5f, 1f, 2f).forEach { spacing ->
            assertTrue(
                "barSpacing $spacing produced a candle of no width",
                optimalBarWidth(spacing, pixelRatio = 1f) >= 1f,
            )
        }
    }

    /**
     * The border collapses politely rather than swallowing the candle.
     *
     * `barWidth > borderWidth × 2` is the published threshold: below it there is no room for two
     * outlines and something between them, so the body is filled solid. It is why a zoomed-out
     * chart of outlined candles stays readable instead of turning to mush.
     */
    @Test
    fun theOutlineGivesWayToASolidFill() {
        assertTrue(drawBorder(barWidth = 9f, borderWidth = 1f))
        assertFalse(drawBorder(barWidth = 2f, borderWidth = 1f))
        assertFalse(drawBorder(barWidth = 1f, borderWidth = 1f))
    }

    /**
     * A wick is a position, not a quantity: one device pixel, capped by the body it rises from.
     */
    @Test
    fun theWickIsAHairlineCappedByTheBody() {
        assertEquals(1f, wickWidth(barWidth = 9f, barSpacing = 12f, pixelRatio = 1f), 0f)
        assertEquals(
            "a wick may not be wider than the candle it rises out of",
            1f,
            wickWidth(barWidth = 1f, barSpacing = 12f, pixelRatio = 3f),
            0f,
        )
    }

    /* ------------------------------------------------------------------ the axes */

    /**
     * Twenty-eight pixels at twelve-point type — the published sum, term by term.
     *
     * `border 1 + tick 5 + font 12 + padTop 3 + padBottom 3 + labelOffset 4`. Worth pinning as a
     * total rather than as six constants because it is the total that has to be reproduced.
     *
     * The three paddings scale with the type size and the border and tick do not — so the axis
     * grows with a reader's font setting rather than clipping the date, without the tick mark
     * inflating along with the text. Doubling the type to 24 gives 50, not 56: `1 + 5 + 24 + 6 + 6
     * + 8`. Affine, not proportional, and that is the correct shape — a tick is a mark on a ruler
     * and has no business getting bigger because the label did.
     */
    @Test
    fun theTimeAxisIsTwentyEightAtTheReferenceTypeSize() {
        assertEquals(28f, timeAxisHeight(12f), 0f)
        // The paddings scale, the border and the tick do not.
        assertEquals(50f, timeAxisHeight(24f), 0f)
    }

    /**
     * Twenty-one pixels of fixed chrome plus the widest label, rounded **up to an even number**.
     *
     * The rounding is not tidiness. The one-pixel border between plot and axis has to land on a
     * device-pixel boundary; at 2× an odd width puts it on a half pixel, the compositor paints two
     * rows at half intensity, and the edge of the chart goes soft. Nobody names it in a review —
     * they say the chart looks cheap.
     *
     * At a thirteen-pixel label this comes out at the published default of 34.
     */
    @Test
    fun thePriceAxisIsFixedChromePlusTheLabelRoundedEven() {
        assertEquals(34f, priceAxisWidth(maxLabelWidth = 13f, fontSize = 12f), 0f)
        listOf(0f, 7f, 13f, 40f, 41f).forEach { label ->
            val width = priceAxisWidth(label, fontSize = 12f)
            assertEquals(
                "priceAxisWidth($label) = $width is odd; the axis border would land on a half pixel",
                0f,
                width % 2f,
                0f,
            )
        }
    }

    /**
     * The separator's invisible nine-pixel target, centred on its one visible pixel.
     *
     * A one-pixel divider that can only be grabbed on its one pixel is a control that appears
     * broken. The band never shows up in a screenshot, and it is most of what separates an
     * interface that feels expensive from one that does not — so it is asserted rather than trusted.
     */
    @Test
    fun theSeparatorHasANinePixelTarget() {
        val band = separatorHitRect(separatorY = 100f, density = 1f)
        assertEquals(96f, band.start, 0f)
        assertEquals(105f, band.endInclusive, 0f)
        assertEquals(9f, band.endInclusive - band.start, 0f)

        val dense = separatorHitRect(separatorY = 100f, density = 3f)
        assertEquals("the band scales with the screen", 27f, dense.endInclusive - dense.start, 0f)
    }

    /**
     * The axis type sizes — pinned at what this app currently draws, with the disagreement named.
     *
     * **This is an open question, and it is written down rather than settled.** Two sources say
     * different things:
     *
     * * TradingView's published Charting Library values are **12 px on the price axis and 11 on the
     *   time axis** — one apart, deliberately, so the number a reader repeats out loud is a step
     *   larger than the context they glance at.
     * * This app's own measurement, taken off a phone render, read **12 on both**, and that is what
     *   the code sets.
     *
     * A source constant is stronger than a pixel sampled from a screenshot; a *phone app* is also
     * entitled to override a library default, and the Charting Library is not the Android client.
     * Neither argument settles it, so the number is not being changed on the strength of a
     * document. What settles it is a capture of the real Android app on the canonical device —
     * which is exactly the `REFERENCE_MISSING` state this repository is honest about.
     *
     * Until then this test pins what is actually drawn, so the value cannot drift while the
     * question is open, and the question cannot be quietly forgotten while the value is pinned.
     */
    @Test
    fun theAxisTypeSizesAreWhatThisAppDrawsAndTheDisagreementIsRecorded() {
        assertEquals(12f, axisFontSizeSp(isPriceAxis = true), 0f)
        assertEquals(
            "The time axis is 12sp here. TradingView's published Charting Library value is 11. " +
                "See this test's documentation and docs/design/TRADINGVIEW_VISUAL_PARITY.md — the " +
                "question is open until a real Android capture answers it, and changing this " +
                "number on the strength of a library default would be guessing.",
            12f,
            axisFontSizeSp(isPriceAxis = false),
            0f,
        )
    }

    private fun assertArray(expected: FloatArray, actual: FloatArray) {
        assertEquals("length", expected.size, actual.size)
        expected.indices.forEach { assertEquals("[$it]", expected[it], actual[it], 0f) }
    }
}
