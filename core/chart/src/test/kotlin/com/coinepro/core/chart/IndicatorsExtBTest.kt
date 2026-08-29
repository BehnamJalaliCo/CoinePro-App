package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fourteen indicators of [IndicatorsExtB], each checked against arithmetic done by hand.
 *
 * Every expected number below is written as the fraction it actually is — `4100.0 / 73.0` rather
 * than `56.164383` — because a rounded decimal in an assertion is a second implementation of the
 * indicator, and a wrong one. The fixtures are deliberately tiny and the periods deliberately short
 * so that every value can be followed on paper; the same code runs at period 14 on six hundred bars
 * in the app.
 *
 * Two of the tests below exist for specific bugs rather than for coverage. One pins the parabolic
 * SAR's clamp against the previous two bars, which is the detail most implementations drop and
 * whose absence shows up as a reversal on a bar that did nothing. The other pins which *index* each
 * Alligator line lands on, because a displacement that is off by a bar draws a picture that still
 * looks exactly like an Alligator.
 */
class IndicatorsExtBTest {

    // Six bars, chosen so that every window below works out to a short fraction: a three-bar rally,
    // an inside pullback, a wide up bar and a quiet down bar.
    private val high = doubleArrayOf(10.0, 11.0, 12.0, 11.5, 13.0, 12.5)
    private val low = doubleArrayOf(9.0, 9.5, 10.5, 10.0, 11.0, 11.5)
    private val close = doubleArrayOf(9.5, 10.5, 11.5, 10.5, 12.5, 12.0)
    private val volume = doubleArrayOf(100.0, 200.0, 300.0, 400.0, 500.0, 600.0)

    // Forty bars whose median price is exactly 10 + index, so a simple average over any window is
    // the midpoint of that window and can be read off without arithmetic. The last bar steps three
    // higher than the ramp so the final reading is not the same constant as every other one, which
    // is how a broken average passes a test on a straight line.
    private val rampMedian = DoubleArray(40) { if (it == 39) 52.0 else 10.0 + it }
    private val rampHigh = DoubleArray(40) { rampMedian[it] + 1 }
    private val rampLow = DoubleArray(40) { rampMedian[it] - 1 }

    private fun assertAbsent(what: String, value: Double) =
        assertTrue("$what should be warm-up, was $value", value.isNaN())

    // ── [51] parabolic SAR ─────────────────────────────────────────────────────────────────

    @Test
    fun `the parabolic SAR steps towards price and jumps to the extreme point on a reversal`() {
        // Five rising bars then one that collapses. Bar 1 seeds the stop at the previous bar's low
        // of 9 with the extreme point at 11 and af 0.02. Bar 2's raw step, 9 + 0.02·(11−9) = 9.04,
        // is pulled back to 9 by the clamp against bar 0's low; bar 3 steps to 9 + 0.04·(12−9) =
        // 9.12 and bar 4 to 9.12 + 0.06·(13−9.12) = 9.3528. Bar 5's low of 8 is below the stop, so
        // the SAR reverses and becomes the extreme point of the rally that just ended — 14, the
        // highest high — rather than the parabola's next value.
        val trendHigh = doubleArrayOf(10.0, 11.0, 12.0, 13.0, 14.0, 12.0)
        val trendLow = doubleArrayOf(9.0, 10.0, 11.0, 12.0, 13.0, 8.0)
        val sar = IndicatorsExtB.parabolicSar(trendHigh, trendLow)

        assertAbsent("bar 0", sar[0])
        assertEquals(9.0, sar[1], 1e-9)
        assertEquals(9.0, sar[2], 1e-9)
        assertEquals(9.12, sar[3], 1e-9)
        assertEquals(9.3528, sar[4], 1e-9)
        assertEquals(14.0, sar[5], 1e-9)
    }

    @Test
    fun `the parabolic SAR cannot be placed inside the previous two bars and so does not reverse`() {
        // The clamp, isolated. With step and max both 0.5 the acceleration is at its ceiling
        // immediately, so on bar 3 the stop is 15 with the extreme point at 22 and bar 4's raw step
        // is 15 + 0.5·(22−15) = 18.5. Bar 4's low is 18, which is *below* 18.5 — an unclamped
        // implementation reverses here and prints 22, a stop-out manufactured entirely by
        // arithmetic on a bar that made no new low.
        //
        // The clamp forbids placing the stop inside the previous two bars' range, so it is pulled
        // down to bar 3's low of 17, the trend survives, and bar 5 continues from there.
        val clampHigh = doubleArrayOf(10.0, 20.0, 21.0, 22.0, 19.0, 25.0)
        val clampLow = doubleArrayOf(9.0, 19.0, 20.0, 17.0, 18.0, 20.0)
        val sar = IndicatorsExtB.parabolicSar(clampHigh, clampLow, step = 0.5, max = 0.5)

        assertEquals(15.0, sar[3], 1e-9)
        assertEquals("the clamp must hold the stop at the prior low", 17.0, sar[4], 1e-9)
        assertTrue("a clamped stop stays below the bar it protects", sar[4] < clampLow[4])
        assertEquals(17.0, sar[5], 1e-9)
    }

    // ── [53] stochastic RSI ────────────────────────────────────────────────────────────────

    @Test
    fun `the stochastic RSI rescales the RSI to its own three-bar range`() {
        // Eight closes that alternate up 1 or 1.5 and down 0.5, which keeps the Wilder averages as
        // clean fractions: the RSI(3) readings are 100−100/6, 100−100/3, 100−100/5.7, 100−100/3
        // and 100−100·94/525. The stochastic then rescales each three-bar window of those, %K is a
        // two-bar average of the result and %D a two-bar average of %K.
        val closes = doubleArrayOf(10.0, 11.0, 10.5, 12.0, 11.5, 13.0, 12.5, 14.0)
        val result = IndicatorsExtB.stochasticRsi(closes, rsiPeriod = 3, stochPeriod = 3, kSmooth = 2, dSmooth = 2)

        assertAbsent("%K before the third stochastic reading", result.k[5])
        assertEquals(900.0 / 19.0, result.k[6], 1e-9)
        assertEquals(342.0 / 7.0, result.k[7], 1e-9)
        assertAbsent("%D one bar before %K has two values", result.d[6])
        assertEquals(6399.0 / 133.0, result.d[7], 1e-9)
    }

    // ── [54] money flow index ──────────────────────────────────────────────────────────────

    @Test
    fun `the money flow index weights each bar by what it traded`() {
        // Typical prices are 9.5, 31/3, 34/3, 32/3, 36.5/3 and 12. Over bars 1..3 the rises carry
        // 31/3·200 + 34/3·300 = 16400/3 and the single fall carries 32/3·400 = 12800/3, a ratio of
        // 41/32 and so 100 − 100·32/73. Over bars 3..5 only bar 4 rises, against two falls.
        val mfi = IndicatorsExtB.moneyFlowIndex(high, low, close, volume, period = 3)

        assertAbsent("before three money flows exist", mfi[2])
        assertEquals(4100.0 / 73.0, mfi[3], 1e-9)
        assertEquals(36500.0 / 1053.0, mfi[5], 1e-9)
    }

    // ── [55] Chaikin money flow ────────────────────────────────────────────────────────────

    @Test
    fun `Chaikin money flow sums where each bar closed in its range against its volume`() {
        // The multipliers are 0, 1/3, 1/3, −1/3, 1/2 and 0. Bars 0..2 therefore carry
        // 0 + 200/3 + 100 of flow against 600 of volume, and bars 3..5 carry −400/3 + 250 + 0
        // against 1500.
        val cmf = IndicatorsExtB.chaikinMoneyFlow(high, low, close, volume, period = 3)

        assertAbsent("before three bars exist", cmf[1])
        assertEquals(5.0 / 18.0, cmf[2], 1e-9)
        assertEquals(7.0 / 90.0, cmf[5], 1e-9)
    }

    // ── [56] awesome oscillator ────────────────────────────────────────────────────────────

    @Test
    fun `the awesome oscillator is the five-bar median average minus the thirty-four-bar one`() {
        // On the ramp the median price is 10 + index, so at bar 33 the fast average is the midpoint
        // of 39..43 — 41 — and the slow one the midpoint of 10..43 — 26.5. At bar 39 the stepped
        // last bar lifts the fast average to 238/5 and the slow one to 1108/34.
        val ao = IndicatorsExtB.awesomeOscillator(rampHigh, rampLow)

        assertAbsent("one bar short of thirty-four", ao[32])
        assertEquals(14.5, ao[33], 1e-9)
        assertEquals(1276.0 / 85.0, ao[39], 1e-9)
    }

    // ── [57] alligator ─────────────────────────────────────────────────────────────────────

    @Test
    fun `each alligator line is published on the bar its displacement moves it to`() {
        // This is the test the displacement exists for. The jaw's smoothed average first exists on
        // bar 12 — the mean of the medians 10..22, which is 16 — and an eight-bar forward shift
        // publishes it on bar 20, leaving bar 19 empty. Bar 13's average, (16·12 + 23)/13, lands on
        // bar 21. The teeth's first value, the mean of 10..17, moves five bars from 7 to 12, and
        // the lips' mean of 10..14 moves three bars from 4 to 7.
        val gator = IndicatorsExtB.alligator(rampHigh, rampLow)

        assertAbsent("the jaw one bar before its displaced start", gator.jaw[19])
        assertEquals(16.0, gator.jaw[20], 1e-9)
        assertEquals(215.0 / 13.0, gator.jaw[21], 1e-9)

        assertAbsent("the teeth one bar before their displaced start", gator.teeth[11])
        assertEquals(13.5, gator.teeth[12], 1e-9)

        assertAbsent("the lips one bar before their displaced start", gator.lips[6])
        assertEquals(12.0, gator.lips[7], 1e-9)
    }

    // ── [58] volume-weighted moving average ────────────────────────────────────────────────

    @Test
    fun `the volume weighted average counts each bar as many times as it traded`() {
        // Bars 0..2: (9.5·100 + 10.5·200 + 11.5·300) / 600 = 6500/600. Bars 3..5:
        // (10.5·400 + 12.5·500 + 12·600) / 1500 = 17650/1500.
        val vwma = IndicatorsExtB.vwma(close, volume, period = 3)

        assertAbsent("before three bars exist", vwma[1])
        assertEquals(65.0 / 6.0, vwma[2], 1e-9)
        assertEquals(353.0 / 30.0, vwma[5], 1e-9)
    }

    // ── [59] TEMA and [60] DEMA ────────────────────────────────────────────────────────────

    @Test
    fun `TEMA recombines three chained averages and waits for all three`() {
        // Period 3 makes the smoothing factor exactly one half, so each stage is the mean of the
        // new value and the previous stage. The chain runs 12.5, 12.75, 13.875, 14.9375, 16.46875;
        // the second stage starts at bar 4 with 13.25 and reaches 15.28125; the third starts at bar
        // 6 with 14.4765625. TEMA is 3·16.46875 − 3·15.28125 + 14.4765625.
        val values = doubleArrayOf(10.0, 12.0, 14.0, 13.0, 15.0, 16.0, 18.0)
        val tema = IndicatorsExtB.tema(values, 3)

        assertAbsent("one bar before the third average exists", tema[5])
        assertEquals(18.0390625, tema[6], 1e-9)
    }

    @Test
    fun `DEMA needs only two chained averages and so starts two bars earlier`() {
        // The same chain as above, stopping one stage short: 2·13.875 − 13.25 at bar 4 and
        // 2·16.46875 − 15.28125 at bar 6.
        val values = doubleArrayOf(10.0, 12.0, 14.0, 13.0, 15.0, 16.0, 18.0)
        val dema = IndicatorsExtB.dema(values, 3)

        assertAbsent("one bar before the second average exists", dema[3])
        assertEquals(14.5, dema[4], 1e-9)
        assertEquals(17.65625, dema[6], 1e-9)
    }

    // ── [61] true strength index ───────────────────────────────────────────────────────────

    @Test
    fun `the true strength index divides doubly smoothed momentum by its own magnitude`() {
        // Momentum is 2, −1, 3, −1, 2, 1 and its magnitude 2, 1, 3, 1, 2, 1. At period 3 both
        // smoothing stages halve, so the signed chain reaches 1.125 at bar 5 and 1.109375 at bar 6
        // while the magnitude chain reaches 1.875 and 1.640625. The signal is a two-bar average of
        // the line, which cannot exist until the line has two values.
        val closes = doubleArrayOf(10.0, 12.0, 11.0, 14.0, 13.0, 15.0, 16.0)
        val result = IndicatorsExtB.trueStrengthIndex(closes, long = 3, short = 3, signal = 2)

        assertAbsent("before both smoothing stages have published", result.tsi[4])
        assertEquals(60.0, result.tsi[5], 1e-9)
        assertEquals(1420.0 / 21.0, result.tsi[6], 1e-9)
        assertAbsent("the signal on the line's first bar", result.signal[5])
        assertEquals(4100.0 / 63.0, result.signal[6], 1e-9)
    }

    // ── [62] aroon ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `aroon measures how many bars ago the window set its extreme`() {
        // At period 4 the window is five bars. On bar 4 the highest high is bar 4's own 13, so up
        // is 100, and the lowest low is bar 0's 9, the oldest bar in the window, so down is 0. One
        // bar later the high is one bar old — 100·3/4 — and the low, now bar 1's 9.5, is again the
        // oldest in the window.
        val aroon = IndicatorsExtB.aroon(high, low, period = 4)

        assertAbsent("before the window is full", aroon.up[3])
        assertEquals(100.0, aroon.up[4], 1e-9)
        assertEquals(0.0, aroon.down[4], 1e-9)
        assertEquals(75.0, aroon.up[5], 1e-9)
        assertEquals(0.0, aroon.down[5], 1e-9)
    }

    // ── [63] directional movement ──────────────────────────────────────────────────────────

    @Test
    fun `the directional movement system publishes its ADX a full period after the DI lines`() {
        // True ranges are 1.5, 1.5, 1.5, 2.5, 1; the plus moves 1, 1, 0, 1.5, 0 and the minus
        // moves 0, 0, 0.5, 0, 0. Wilder's seed at bar 3 is the mean of the first three of each, and
        // two more bars of smoothing give +DI 100·17/42 and −DI 100·2/42 at bar 5. The DX readings
        // are 60, 1500/19 and 1500/19, whose mean — the first ADX — is 1380/19.
        val dmi = IndicatorsExtB.directionalMovement(high, low, close, period = 3)

        assertEquals(400.0 / 9.0, dmi.plusDi[3], 1e-9)
        assertEquals(100.0 / 9.0, dmi.minusDi[3], 1e-9)
        assertAbsent("the ADX while the DX is still filling", dmi.adx[4])
        assertEquals(850.0 / 21.0, dmi.plusDi[5], 1e-9)
        assertEquals(100.0 / 21.0, dmi.minusDi[5], 1e-9)
        assertEquals(1380.0 / 19.0, dmi.adx[5], 1e-9)
    }

    // ── [64] PPO and [65] PVO ──────────────────────────────────────────────────────────────

    @Test
    fun `the percentage price oscillator reports the gap between its averages as a percentage`() {
        // The two-period average, weight 2/3, reaches 100/9 at bar 2 and 352/27 at bar 3; the
        // three-period one, weight 1/2, reaches 11 and 12.5. So the oscillator is
        // 100·(100/9 − 11)/11 = 100/99 and then 100·(352/27 − 12.5)/12.5 = 116/27. Its signal is a
        // two-period average of those two readings and the histogram is the distance between them.
        val closes = doubleArrayOf(10.0, 12.0, 11.0, 14.0, 13.0, 15.0, 16.0)
        val ppo = IndicatorsExtB.ppo(closes, fast = 2, slow = 3, signal = 2)

        assertAbsent("before the slow average exists", ppo.oscillator[1])
        assertEquals(100.0 / 99.0, ppo.oscillator[2], 1e-9)
        assertEquals(116.0 / 27.0, ppo.oscillator[3], 1e-9)
        assertAbsent("the signal on the oscillator's first bar", ppo.signal[2])
        assertEquals(2852.0 / 891.0, ppo.signal[3], 1e-9)
        assertEquals(976.0 / 891.0, ppo.histogram[3], 1e-9)
    }

    @Test
    fun `the percentage volume oscillator is scale free and matches the price version`() {
        // The same arithmetic run over volume. Because every reading is a ratio, a volume series
        // that is exactly ten times a price series produces exactly the same oscillator — which is
        // the property that makes a percentage oscillator comparable between two symbols at all,
        // and is worth pinning because an absolute-difference version would fail it by a factor of
        // ten.
        val closes = doubleArrayOf(10.0, 12.0, 11.0, 14.0, 13.0, 15.0, 16.0)
        val volumes = DoubleArray(closes.size) { closes[it] * 10 }
        val pvo = IndicatorsExtB.pvo(volumes, fast = 2, slow = 3, signal = 2)
        val ppo = IndicatorsExtB.ppo(closes, fast = 2, slow = 3, signal = 2)

        assertEquals(100.0 / 99.0, pvo.oscillator[2], 1e-9)
        assertEquals(116.0 / 27.0, pvo.oscillator[3], 1e-9)
        assertEquals(ppo.oscillator[6], pvo.oscillator[6], 1e-9)
        assertEquals(ppo.histogram[6], pvo.histogram[6], 1e-9)
    }

    // ── the guards ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a gap in the feed costs only the readings whose window contains it`() {
        // The rule the whole pack is built on. One missing close must not poison the rest of the
        // series: bars 2 and 3 have the gap inside their two-bar window and are absent, bar 4 is
        // the first window clear of it and is a real average again.
        val gapped = doubleArrayOf(1.0, 2.0, Double.NaN, 4.0, 5.0, 6.0)
        val ones = DoubleArray(6) { 1.0 }
        val vwma = IndicatorsExtB.vwma(gapped, ones, period = 2)

        assertEquals(1.5, vwma[1], 1e-9)
        assertAbsent("the window containing the gap", vwma[2])
        assertAbsent("the second window containing the gap", vwma[3])
        assertEquals(4.5, vwma[4], 1e-9)
        assertEquals(5.5, vwma[5], 1e-9)

        // A running average must recover too, rather than carrying the NaN forward for ever.
        val dema = IndicatorsExtB.dema(gapped, 2)
        assertTrue("a chained average must resume after a gap", dema[5].isFinite())
    }

    @Test
    fun `an impossible period or a series too short for it yields nothing rather than an exception`() {
        val short = doubleArrayOf(1.0, 2.0, 3.0)
        val ones = DoubleArray(3) { 1.0 }

        assertTrue(IndicatorsExtB.vwma(short, ones, period = 0).all { it.isNaN() })
        assertTrue(IndicatorsExtB.vwma(short, ones, period = 9).all { it.isNaN() })
        assertTrue(IndicatorsExtB.tema(short, -4).all { it.isNaN() })
        assertTrue(IndicatorsExtB.dema(short, 9).all { it.isNaN() })
        assertTrue(IndicatorsExtB.awesomeOscillator(short, short).all { it.isNaN() })
        assertTrue(IndicatorsExtB.parabolicSar(short, short, step = 0.0).all { it.isNaN() })
        assertTrue(IndicatorsExtB.aroon(short, short, period = 0).up.all { it.isNaN() })
        assertTrue(IndicatorsExtB.chaikinMoneyFlow(short, short, short, ones, period = 9).all { it.isNaN() })
        assertTrue(IndicatorsExtB.moneyFlowIndex(short, short, short, ones, period = 3).all { it.isNaN() })
        assertTrue(IndicatorsExtB.stochasticRsi(short, rsiPeriod = 3, stochPeriod = 3).k.all { it.isNaN() })
        assertTrue(IndicatorsExtB.trueStrengthIndex(short, long = 0).tsi.all { it.isNaN() })
        assertTrue(IndicatorsExtB.directionalMovement(short, short, short, period = 3).adx.all { it.isNaN() })
        assertTrue(IndicatorsExtB.ppo(short, fast = 2, slow = 9).oscillator.all { it.isNaN() })
        assertTrue(IndicatorsExtB.pvo(short, signal = 0).oscillator.all { it.isNaN() })

        // An empty series is a length nothing can be shorter than, and it must come back empty.
        assertEquals(0, IndicatorsExtB.parabolicSar(DoubleArray(0), DoubleArray(0)).size)
        assertEquals(0, IndicatorsExtB.alligator(DoubleArray(0), DoubleArray(0)).jaw.size)
    }
}
