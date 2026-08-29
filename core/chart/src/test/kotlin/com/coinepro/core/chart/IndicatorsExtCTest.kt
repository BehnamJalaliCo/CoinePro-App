package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The third indicator pack, checked against arithmetic done by hand rather than against itself.
 *
 * Every expected number here was worked out from the indicator's definition — on paper for the short
 * fixtures, and on a separate reference implementation of the published formula for the four that
 * need thirty bars of warm-up before they say anything. None of them was read off this code, which
 * is the only way a test of a formula is worth running: a fixture recorded from the implementation
 * proves the implementation has not changed, not that it was ever right.
 *
 * The delta is 1e-9 throughout. These are sums and divisions of small doubles, so the last bit or
 * two is genuinely free to move between machines; anything looser would let a real error through.
 */
class IndicatorsExtCTest {

    private val delta = 1e-9

    private fun assertGap(message: String, value: Double) = assertTrue(message, value.isNaN())

    // ══════════════════════════════════════════════════════════ oscillators

    /**
     * On a straight ramp of one unit per bar, the four-bar average sits exactly one and a half units
     * below the price three bars back, at every bar. Which makes the displacement visible: an
     * implementation that compared today's close against the same average would report `+1.5`, the
     * same magnitude with the wrong sign, and look plausible on a chart.
     */
    @Test
    fun `the detrended price oscillator compares price against the average of its own window`() {
        val close = doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0)
        val out = IndicatorsExtC.detrendedPriceOscillator(close, period = 4)

        // SMA(4) does not exist before bar 3, and the shift is 4 / 2 + 1 = 3 bars.
        assertGap("bar 0 has no four-bar average behind it", out[0])
        assertGap("bar 2 still has no four-bar average", out[2])
        // Bar 3: close[0] = 1 against the mean of 1..4, which is 2.5.
        assertEquals(-1.5, out[3], delta)
        // Bar 7: close[4] = 5 against the mean of 5..8, which is 6.5.
        assertEquals(-1.5, out[7], delta)
    }

    /**
     * Sixty bars of a one-unit ramp, so every rate of change is a known fraction of a known base.
     *
     * The value at bar 44 is the first one that exists at all: the thirty-bar rate of change starts
     * at bar 30 and its fifteen-bar average fifteen bars later. A KST that published before that
     * would be reporting a sum in which the heaviest term was missing.
     */
    @Test
    fun `know sure thing waits for its slowest term and weights that term heaviest`() {
        val close = DoubleArray(60) { 100.0 + it }
        val result = IndicatorsExtC.knowSureThing(close)

        assertGap("bar 43 is one bar short of the thirty-bar term's average", result.kst[43])
        assertEquals(194.40619323111486, result.kst[44], delta)
        assertEquals(192.6937567143305, result.kst[45], delta)
        assertEquals(171.55196934680345, result.kst[59], delta)

        assertGap("the signal is a nine-bar average of a line that starts at 44", result.signal[51])
        assertEquals(187.8264073640325, result.signal[52], delta)
        assertEquals(177.1791393815709, result.signal[59], delta)

        // The ramp's momentum decays as the base price grows, so the line falls throughout.
        assertTrue("a decaying ramp must give a falling KST", result.kst[59] < result.kst[44])
    }

    /**
     * A series whose bars all have the same height is the one case with an answer that needs no
     * arithmetic: both smoothings settle on that height, their ratio is exactly one, and the sum of
     * `period` ones is `period`. Anything else means the two EMAs were not the same length.
     */
    @Test
    fun `the mass index of a constant range is the period itself`() {
        val high = DoubleArray(20) { 10.0 }
        val low = DoubleArray(20) { 8.0 }

        val flat = IndicatorsExtC.massIndex(high, low, period = 5, ema = 3)
        assertEquals(5.0, flat[19], delta)

        val varying = IndicatorsExtC.massIndex(
            high = doubleArrayOf(10.0, 11.0, 13.0, 12.0, 15.0, 16.0, 14.0, 13.0, 17.0, 18.0),
            low = doubleArrayOf(9.0, 9.5, 11.0, 11.0, 12.0, 13.5, 12.0, 12.5, 14.0, 16.0),
            period = 3,
            ema = 3,
        )
        assertGap("three ratios need two three-bar EMAs behind them first", varying[5])
        assertEquals(3.3363236957027995, varying[6], delta)
        assertEquals(2.919035454206334, varying[7], delta)
        assertEquals(2.92000256436019, varying[9], delta)
    }

    /**
     * Three bars of change over a three-bar window, added up by hand.
     *
     * The reading at bar 3 is `100 × (3 − 1) / 4 = 50` and at bar 4 `100 × (5 − 1) / 6`. A run with
     * no down bar in it must reach exactly 100, which is the property that separates a CMO from an
     * RSI and the one an accidental Wilder smoothing would quietly destroy.
     */
    @Test
    fun `the chande momentum oscillator uses raw sums and so reaches the ends of its scale`() {
        val close = doubleArrayOf(10.0, 11.0, 13.0, 12.0, 15.0)
        val out = IndicatorsExtC.chandeMomentumOscillator(close, period = 3)

        assertGap("three changes need four bars", out[2])
        assertEquals(50.0, out[3], delta)
        assertEquals(100.0 * 4 / 6, out[4], delta)

        val rising = IndicatorsExtC.chandeMomentumOscillator(
            doubleArrayOf(1.0, 2.0, 3.0, 4.0, 5.0),
            period = 3,
        )
        assertEquals(100.0, rising[4], delta)

        val still = IndicatorsExtC.chandeMomentumOscillator(DoubleArray(6) { 7.0 }, period = 3)
        assertEquals("a market that did not move gave neither side the bar", 0.0, still[5], delta)
    }

    /**
     * Two-bar extremes, a two-bar ATR and a two-bar ratchet, so the first stop can be checked in one
     * line: the lowest low of bars 0 and 1 is 9.0, the ATR there is `(1.0 + 1.5) / 2 = 1.25`, and
     * the long stop is their sum. The ratchet is then visible in the series: both stops rise with
     * price and neither falls back on the quiet bars.
     */
    @Test
    fun `the chande kroll stop places both stops an atr from the window's extremes`() {
        val high = doubleArrayOf(10.0, 11.0, 13.0, 12.0, 15.0, 16.0, 14.0, 13.0, 17.0, 18.0)
        val low = doubleArrayOf(9.0, 9.5, 11.0, 11.0, 12.0, 13.5, 12.0, 12.5, 14.0, 16.0)
        val close = doubleArrayOf(9.5, 10.5, 12.0, 11.5, 14.0, 15.0, 13.0, 12.5, 16.5, 17.5)
        val stop = IndicatorsExtC.chandeKrollStop(high, low, close, p = 2, x = 1.0, q = 2)

        assertGap("the ratchet needs two of the first-pass stops", stop.longStop[1])
        assertEquals(10.25, stop.longStop[2], delta)
        assertEquals(11.375, stop.longStop[3], delta)
        assertEquals(15.560546875, stop.longStop[9], delta)

        assertEquals(11.125, stop.shortStop[2], delta)
        assertEquals(15.4697265625, stop.shortStop[9], delta)

        assertTrue(
            "the short stop sits above the long one while the two windows agree",
            stop.shortStop[2] > stop.longStop[2],
        )
    }

    /**
     * Ramps of five different slopes, each run twice — once up, once down.
     *
     * Two things are being checked and neither is a single number. The index must fall as the slope
     * steepens, which is what makes the ribbon readable at all, and the down ramp must produce
     * exactly the mirror index, `7 − i`. A sign dropped anywhere in the angle would pass the first
     * check and fail the second, and on a chart it would colour every downtrend as a rally.
     */
    @Test
    fun `the chop zone grades the ema's slope and mirrors it for a falling market`() {
        fun bucketAt(step: Double): Int {
            val close = DoubleArray(40) { 100.0 + step * it }
            val high = DoubleArray(40) { close[it] + 1.0 }
            val low = DoubleArray(40) { close[it] - 1.0 }
            return IndicatorsExtC.chopZone(high, low, close, period = 30)[39]
        }

        assertEquals("a market going nowhere is chop, tilted up by a hair", 3, bucketAt(0.0))
        assertEquals(3, bucketAt(0.001))
        assertEquals(2, bucketAt(0.002))
        assertEquals(1, bucketAt(0.004))
        assertEquals(0, bucketAt(0.016))

        assertEquals(4, bucketAt(-0.001))
        assertEquals(5, bucketAt(-0.002))
        assertEquals(6, bucketAt(-0.004))
        assertEquals(7, bucketAt(-0.016))

        val close = DoubleArray(40) { 100.0 + it }
        val high = DoubleArray(40) { close[it] + 1.0 }
        val low = DoubleArray(40) { close[it] - 1.0 }
        val zone = IndicatorsExtC.chopZone(high, low, close, period = 30)
        assertEquals("a slope needs the EMA on this bar and on the one before it", -1, zone[33])
        assertEquals(0, zone[34])
    }

    /**
     * A series that doubles every bar, so both rates of change are constants: 700% over three bars
     * and 300% over two. Their sum is 1000 at every bar that has both, and a weighted average of a
     * constant is that constant — which makes the whole chain checkable without a single rounding.
     */
    @Test
    fun `the coppock curve is the weighted average of its two rates of change added together`() {
        val close = doubleArrayOf(1.0, 2.0, 4.0, 8.0, 16.0, 32.0, 64.0)
        val out = IndicatorsExtC.coppockCurve(close, roc1 = 3, roc2 = 2, wma = 2)

        assertGap("bar 3 has both rates but not yet two of them to average", out[3])
        assertEquals(1000.0, out[4], delta)
        assertEquals(1000.0, out[6], delta)
    }

    /** Five bars, one of them unchanged, added up by hand: 0, +3, −4, +0, +2 gives 0, 3, −1, −1, 1. */
    @Test
    fun `net volume signs each bar by its direction and skips a bar that closed flat`() {
        val close = doubleArrayOf(10.0, 11.0, 10.0, 10.0, 12.0)
        val volume = doubleArrayOf(5.0, 3.0, 4.0, 7.0, 2.0)
        val out = IndicatorsExtC.netVolume(close, volume)

        assertEquals(0.0, out[0], delta)
        assertEquals(3.0, out[1], delta)
        assertEquals(-1.0, out[2], delta)
        assertEquals("the unchanged bar moved the line by nothing", -1.0, out[3], delta)
        assertEquals(1.0, out[4], delta)
    }

    /**
     * Identical bars first, because they give the answer away: a body of 1 inside a range of 3
     * survives both smoothings and both averages, so the line must be exactly one third and the
     * signal must equal it. Then a fixture of real bars, worked out from the same definition.
     */
    @Test
    fun `the relative vigor index divides the smoothed body by the smoothed range`() {
        val open = DoubleArray(10) { 1.0 }
        val close = DoubleArray(10) { 2.0 }
        val high = DoubleArray(10) { 3.0 }
        val low = DoubleArray(10) { 0.0 }
        val flat = IndicatorsExtC.relativeVigorIndex(open, high, low, close, period = 2)
        assertEquals(1.0 / 3, flat.rvi[9], delta)
        assertEquals(1.0 / 3, flat.signal[9], delta)

        val result = IndicatorsExtC.relativeVigorIndex(
            open = doubleArrayOf(10.0, 11.0, 12.0, 11.0, 13.0, 12.0, 14.0, 15.0),
            high = doubleArrayOf(11.0, 12.5, 13.0, 12.0, 14.0, 13.5, 15.0, 16.0),
            low = doubleArrayOf(9.5, 10.5, 11.0, 10.0, 12.5, 11.5, 13.5, 14.5),
            close = doubleArrayOf(10.5, 12.0, 11.5, 11.5, 13.5, 13.0, 14.5, 15.5),
            period = 2,
        )
        assertGap("the four-bar smoothing plus a two-bar average starts at bar 4", result.rvi[3])
        assertEquals(0.15217391304347824, result.rvi[4], delta)
        assertEquals(0.3023255813953488, result.rvi[6], delta)
        assertEquals(0.39024390243902435, result.rvi[7], delta)

        assertGap("the signal needs four values of the line", result.signal[6])
        assertEquals(0.25043742230479266, result.signal[7], delta)
    }

    /**
     * A ramp with no wicks, so the typical price is the close and the mean deviation is a fraction
     * that cancels: three bars of 1, 2, 3 give a mean of 2 and a deviation of 2/3, so the CCI is
     * `1 / (0.015 × 2/3)`, exactly 100. The turbo line at two bars gives `0.5 / 0.0075`.
     */
    @Test
    fun `woodies cci runs one calculation at two lookbacks over one typical price`() {
        val price = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        val result = IndicatorsExtC.woodiesCci(price, price, price, period = 3, turboPeriod = 2)

        assertGap("three bars are needed for the slow line", result.cci[1])
        assertEquals(100.0, result.cci[2], delta)
        assertEquals(100.0, result.cci[3], delta)

        assertEquals(0.5 / 0.0075, result.turbo[1], delta)
        assertTrue("the turbo line exists two bars before the slow one", result.turbo[1].isFinite())
    }

    /**
     * A rally into a crash, at a three-bar ATR.
     *
     * The stop holds still through bars 3 to 5 — price pulled back but the running high did not, and
     * a stop that follows price down is not a stop. Then bar 6 closes through it and everything
     * resets to the other side: the value jumps above price and the side flips.
     */
    @Test
    fun `the volatility stop ratchets under the trend and flips to the other side of price`() {
        val high = doubleArrayOf(10.0, 11.0, 12.0, 13.0, 12.5, 12.0, 9.0, 8.5, 8.0, 9.0)
        val low = doubleArrayOf(9.0, 9.5, 11.0, 12.0, 11.0, 10.0, 7.5, 7.0, 7.0, 8.0)
        val close = doubleArrayOf(9.5, 10.5, 11.5, 12.5, 11.5, 10.5, 8.0, 7.5, 7.5, 8.5)
        val result = IndicatorsExtC.volatilityStop(high, low, close, period = 3, multiplier = 2.0)

        assertGap("a three-bar ATR has nothing to say on bar 1", result.stop[1])
        assertEquals(8.833333333333334, result.stop[2], delta)
        assertEquals(9.722222222222223, result.stop[3], delta)
        assertEquals("the stop must not give ground on a pullback", 9.722222222222223, result.stop[5], delta)

        assertTrue("bars 2 to 5 are an uptrend", result.isLong[2] && result.isLong[5])
        assertEquals(12.156378600823047, result.stop[6], delta)
        assertTrue("bar 6 closed through the stop", !result.isLong[6])
        assertTrue("the flipped stop sits above price", result.stop[6] > close[6])
        assertEquals(10.620408474317939, result.stop[9], delta)
    }

    /**
     * Two series that move together, the same pair inverted, and a pair whose answer is 0.8 by hand:
     * the products of the deviations sum to 4 and each series' squared deviations sum to 5.
     *
     * The constant series matters as much as the other three. Its deviation is zero, the division is
     * undefined, and the honest answer is a gap — a zero there would read as "these two are
     * unrelated", which is a claim about the market rather than about the missing denominator.
     */
    @Test
    fun `the correlation coefficient reads plus one, minus one, and a gap where there is no variance`() {
        val a = doubleArrayOf(1.0, 2.0, 3.0, 4.0)
        assertEquals(1.0, IndicatorsExtC.correlationCoefficient(a, doubleArrayOf(2.0, 4.0, 6.0, 8.0), 4)[3], delta)
        assertEquals(-1.0, IndicatorsExtC.correlationCoefficient(a, doubleArrayOf(8.0, 6.0, 4.0, 2.0), 4)[3], delta)
        assertEquals(0.8, IndicatorsExtC.correlationCoefficient(a, doubleArrayOf(1.0, 3.0, 2.0, 4.0), 4)[3], delta)

        val flat = IndicatorsExtC.correlationCoefficient(a, doubleArrayOf(5.0, 5.0, 5.0, 5.0), 4)
        assertGap("a series that never moved has no correlation to report", flat[3])
    }

    // ══════════════════════════════════════════════════════════ volume profile

    /**
     * One bar from 5 to 30 across a window that runs 0 to 30 in three ten-wide rows.
     *
     * It covers half of the bottom row and all of the other two, so its twenty-five units of volume
     * must land as 5, 10 and 10. The implementation this test exists to rule out puts all twenty-five
     * in the row containing the close, which would report 0, 0, 25 — a profile with a point of
     * control at the top of the range on a bar that traded through all of it.
     */
    @Test
    fun `a bar spanning three rows splits its volume in proportion to the overlap`() {
        val profile = IndicatorsExtC.volumeProfile(
            high = doubleArrayOf(10.0, 30.0),
            low = doubleArrayOf(0.0, 5.0),
            close = doubleArrayOf(5.0, 20.0),
            open = doubleArrayOf(4.0, 10.0),
            volume = doubleArrayOf(0.0, 25.0),
            fromIndex = 0,
            toIndex = 1,
            rows = 3,
        )

        assertEquals(0.0, profile.rowLow[0], delta)
        assertEquals(10.0, profile.rowHigh[0], delta)
        assertEquals(30.0, profile.rowHigh[2], delta)

        assertEquals(5.0, profile.volume[0], delta)
        assertEquals(10.0, profile.volume[1], delta)
        assertEquals(10.0, profile.volume[2], delta)
        assertEquals(
            "the split must not create or destroy volume",
            25.0,
            profile.volume.sum(),
            delta,
        )

        // The bar closed above its open, so all of it is buying — and it is the same split again.
        assertEquals(5.0, profile.buy[0], delta)
        assertEquals(0.0, profile.sell.sum(), delta)

        assertEquals("the two heavy rows tie, and the lower one takes it", 1, profile.pocIndex)
    }

    /**
     * Five prices, one bar each, with 100 units at the middle price, 50 above it and 30 below.
     *
     * Seventy per cent of two hundred is a hundred and forty, and the point of control alone holds a
     * hundred. The next row taken must be the heavier neighbour — the fifty above, not the thirty
     * below — which puts the value area at rows 2 to 3. The mirrored fixture proves it is the
     * volume being compared and not the direction: the same numbers reversed take the row below.
     */
    @Test
    fun `the value area grows towards the heavier neighbour`() {
        val price = doubleArrayOf(0.0, 10.0, 20.0, 30.0, 40.0)

        val heavyAbove = IndicatorsExtC.volumeProfile(
            high = price,
            low = price,
            close = price,
            open = price,
            volume = doubleArrayOf(10.0, 30.0, 100.0, 50.0, 10.0),
            fromIndex = 0,
            toIndex = 4,
            rows = 5,
            valueAreaPercent = 70.0,
        )
        assertEquals(2, heavyAbove.pocIndex)
        assertEquals(2, heavyAbove.valueAreaLow)
        assertEquals(3, heavyAbove.valueAreaHigh)

        val heavyBelow = IndicatorsExtC.volumeProfile(
            high = price,
            low = price,
            close = price,
            open = price,
            volume = doubleArrayOf(10.0, 50.0, 100.0, 30.0, 10.0),
            fromIndex = 0,
            toIndex = 4,
            rows = 5,
            valueAreaPercent = 70.0,
        )
        assertEquals(2, heavyBelow.pocIndex)
        assertEquals(1, heavyBelow.valueAreaLow)
        assertEquals(2, heavyBelow.valueAreaHigh)
    }

    /**
     * A bar whose high equals its low has no range to spread across, and the arithmetic that spreads
     * the others divides by exactly that. All seven of its units belong to the one row holding its
     * price, and every other row must stay empty.
     */
    @Test
    fun `a bar with no range at all lands in exactly one row`() {
        val profile = IndicatorsExtC.volumeProfile(
            high = doubleArrayOf(20.0, 10.0),
            low = doubleArrayOf(0.0, 10.0),
            close = doubleArrayOf(10.0, 10.0),
            open = doubleArrayOf(9.0, 10.0),
            volume = doubleArrayOf(0.0, 7.0),
            fromIndex = 0,
            toIndex = 1,
            rows = 4,
        )

        assertEquals(0.0, profile.volume[0], delta)
        assertEquals(0.0, profile.volume[1], delta)
        assertEquals(7.0, profile.volume[2], delta)
        assertEquals(0.0, profile.volume[3], delta)
        assertEquals(2, profile.pocIndex)
        // Open equals close, so the bar is not a demonstration of demand and counts as selling.
        assertEquals(7.0, profile.sell[2], delta)
        assertEquals(0.0, profile.buy[2], delta)
    }

    /**
     * Two windows with nothing to profile: one that runs backwards, and one over bars whose feed
     * reported no volume at all.
     *
     * Both must say so with `pocIndex = -1` rather than pointing at row zero. A caller that draws a
     * point-of-control line at whatever `pocIndex` says would otherwise put a horizontal line at the
     * bottom of the range of a symbol nobody traded, and it would look exactly like a real level.
     */
    @Test
    fun `a window with no bars and a window with no volume both report no point of control`() {
        val empty = IndicatorsExtC.volumeProfile(
            high = doubleArrayOf(10.0, 11.0, 12.0),
            low = doubleArrayOf(9.0, 10.0, 11.0),
            close = doubleArrayOf(9.5, 10.5, 11.5),
            open = doubleArrayOf(9.2, 10.2, 11.2),
            volume = doubleArrayOf(1.0, 1.0, 1.0),
            fromIndex = 3,
            toIndex = 0,
            rows = 4,
        )
        assertEquals(-1, empty.pocIndex)
        assertEquals(-1, empty.valueAreaLow)
        assertEquals(-1, empty.valueAreaHigh)
        assertEquals("the row arrays still have the shape that was asked for", 4, empty.volume.size)
        assertEquals(0.0, empty.volume.sum(), delta)

        val silent = IndicatorsExtC.volumeProfile(
            high = doubleArrayOf(10.0, 11.0, 12.0),
            low = doubleArrayOf(9.0, 10.0, 11.0),
            close = doubleArrayOf(9.5, 10.5, 11.5),
            open = doubleArrayOf(9.2, 10.2, 11.2),
            volume = doubleArrayOf(0.0, 0.0, 0.0),
            fromIndex = 0,
            toIndex = 2,
            rows = 4,
        )
        assertEquals(-1, silent.pocIndex)
        assertEquals(0.0, silent.volume.sum(), delta)
        // The price geometry is still known, and is still worth handing back for the axis.
        assertEquals(9.0, silent.rowLow[0], delta)
        assertEquals(12.0, silent.rowHigh[3], delta)
    }

    /**
     * The guard contract, on the four functions most likely to be handed a bad argument by a period
     * stepper or by two series of different lengths.
     *
     * None of them may throw. The caller is a renderer inside a gesture, and a chart that draws
     * nothing for a thinly listed symbol is a shrug where an exception would be a crash report.
     */
    @Test
    fun `a nonsensical period or a mismatched pair gives back gaps instead of throwing`() {
        val short = doubleArrayOf(1.0, 2.0, 3.0)

        assertTrue(
            "a zero period has no average behind it",
            IndicatorsExtC.detrendedPriceOscillator(short, period = 0).all { it.isNaN() },
        )
        assertTrue(
            "nine changes cannot be read from three bars",
            IndicatorsExtC.chandeMomentumOscillator(short, period = 9).all { it.isNaN() },
        )
        assertTrue(
            "two series of different lengths are not a pair",
            IndicatorsExtC.massIndex(short, doubleArrayOf(1.0, 2.0)).all { it.isNaN() },
        )
        assertTrue(
            "a window longer than the series has no window",
            IndicatorsExtC.correlationCoefficient(short, short, period = 20).all { it.isNaN() },
        )
        assertEquals(
            "the chop zone says nothing with an int array's own gap value",
            listOf(-1, -1, -1),
            IndicatorsExtC.chopZone(short, short, short, period = 30).toList(),
        )
    }
}
