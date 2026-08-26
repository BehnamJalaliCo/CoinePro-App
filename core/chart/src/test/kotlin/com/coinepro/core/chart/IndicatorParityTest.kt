package com.coinepro.core.chart

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every indicator, checked against the web app that already ships them.
 *
 * This is the test that matters for the port and it is the reason the port is a port. Two traders
 * comparing this app's RSI with the Bazaarnama terminal's must see the same number — and an
 * indicator that is *nearly* right is the worst possible outcome, because nothing about it looks
 * wrong. A warm-up that starts a bar early, a deviation divided by `n-1` instead of `n`, a Wilder
 * average seeded from the wrong mean: all of them draw a plausible line.
 *
 * So the fixture is not hand-written expectations. It is the actual output of `indicators.js`,
 * run over a deterministic random walk and recorded — `core/chart/src/test/resources/
 * indicator-parity.txt`. Regenerating it means running the JavaScript again, not editing numbers
 * until this passes.
 *
 * The tolerance is 1e-6 rather than exact equality because the two languages accumulate
 * floating-point error in a different order, not because either is approximate.
 */
class IndicatorParityTest {

    private val fixture = ParityFixture.load()
    private val bars = fixture.bars
    private val high = DoubleArray(bars.size) { bars[it].h }
    private val low = DoubleArray(bars.size) { bars[it].l }
    private val open = DoubleArray(bars.size) { bars[it].o }
    private val close = DoubleArray(bars.size) { bars[it].c }
    private val volume = DoubleArray(bars.size) { bars[it].v ?: 0.0 }

    @Test
    fun `the fixture itself is what it claims to be`() {
        assertEquals(120, bars.size)
        assertTrue("expected series were not loaded", fixture.series.size >= 60)
    }

    @Test
    fun `moving averages match`() {
        fixture.assertMatches("sma20", Indicators.sma(close, 20))
        fixture.assertMatches("ema20", Indicators.ema(close, 20))
        fixture.assertMatches("wma20", Indicators.wma(close, 20))
        fixture.assertMatches("hma20", Indicators.hma(close, 20))
    }

    @Test
    fun `momentum matches`() {
        fixture.assertMatches("rsi14", Indicators.rsi(close, 14))
        fixture.assertMatches("cci20", Indicators.cci(high, low, close, 20))
        fixture.assertMatches("wr14", Indicators.williamsR(high, low, close, 14))

        val macd = Indicators.macd(close)
        fixture.assertMatches("macd", macd.macd)
        fixture.assertMatches("macdSignal", macd.signal)
        fixture.assertMatches("macdHist", macd.histogram)

        val stochastic = Indicators.stochastic(high, low, close, 14, 3)
        fixture.assertMatches("stochK", stochastic.k)
        fixture.assertMatches("stochD", stochastic.d)
    }

    @Test
    fun `volatility matches`() {
        fixture.assertMatches("atr14", Indicators.atr(high, low, close, 14))
        fixture.assertMatches("chop14", Indicators.choppiness(high, low, close, 14))

        val bollinger = Indicators.bollinger(close, 20, 2.0)
        fixture.assertMatches("bbUpper", bollinger.upper)
        fixture.assertMatches("bbLower", bollinger.lower)

        val keltner = Indicators.keltner(high, low, close, 20, 2.0)
        fixture.assertMatches("kcUpper", keltner.upper)
        fixture.assertMatches("kcLower", keltner.lower)

        val donchian = Indicators.donchian(high, low, 20)
        fixture.assertMatches("dcUpper", donchian.upper)
        fixture.assertMatches("dcLower", donchian.lower)
    }

    @Test
    fun `trend matches`() {
        val ichimoku = Indicators.ichimoku(high, low)
        fixture.assertMatches("tenkan", ichimoku.tenkan)
        fixture.assertMatches("kijun", ichimoku.kijun)
        fixture.assertMatches("spanA", ichimoku.spanA)
        fixture.assertMatches("spanB", ichimoku.spanB)

        val supertrend = Indicators.supertrend(high, low, close, 10, 3.0)
        fixture.assertMatches("stLine", supertrend.line)
        fixture.assertMatches("stTrend", supertrend.trend)

        val vortex = Indicators.vortex(high, low, close, 14)
        fixture.assertMatches("vortexPlus", vortex.plus)
        fixture.assertMatches("vortexMinus", vortex.minus)
    }

    @Test
    fun `volume matches`() {
        fixture.assertMatches("obv", Indicators.obv(close, volume))
        fixture.assertMatches("vwap", Indicators.vwap(high, low, close, volume))
    }

    // ── the second thirty, from indicators_ext_a.js and indicators_ext_b.js ────────────────────

    @Test
    fun `the extended moving averages match`() {
        fixture.assertMatches("smma14", IndicatorsExt.smma(close, 14))
        fixture.assertMatches("zlema21", IndicatorsExt.zlema(close, 21))
        fixture.assertMatches("kama10", IndicatorsExt.kama(close, 10, 2, 30))
        fixture.assertMatches("t3_10", IndicatorsExt.t3(close, 10, 0.7))
        fixture.assertMatches("mcginley14", IndicatorsExt.mcginley(close, 14))
        fixture.assertMatches("linreg100", IndicatorsExt.linearRegression(close, 100, 0))
        fixture.assertMatches("lsma25", IndicatorsExt.linearRegression(close, 25, 0))
    }

    @Test
    fun `the extended volatility measures match`() {
        fixture.assertMatches("stddev20", IndicatorsExt.stdDev(close, 20))
        fixture.assertMatches("hv10", IndicatorsExt.historicalVolatility(close, 10, 365))
        fixture.assertMatches("chaikinVol10", IndicatorsExt.chaikinVolatility(high, low, 10, 10))

        val envelopes = IndicatorsExt.envelopes(close, 20, 1.0)
        fixture.assertMatches("envelopesUpper", envelopes.upper)
        fixture.assertMatches("envelopesBasis", envelopes.basis)
        fixture.assertMatches("envelopesLower", envelopes.lower)

        fixture.assertMatches("bbPercent20", IndicatorsExt.bollingerPercent(close, 20, 2.0))
        fixture.assertMatches("bbWidth20", IndicatorsExt.bollingerWidth(close, 20, 2.0))
    }

    @Test
    fun `the extended oscillators match`() {
        fixture.assertMatches("mom10", IndicatorsExt.momentum(close, 10))
        fixture.assertMatches("roc9", IndicatorsExt.rateOfChange(close, 9))
        fixture.assertMatches("ac", IndicatorsExt.accelerator(high, low))
        fixture.assertMatches("uo", IndicatorsExt.ultimateOscillator(high, low, close, 7, 14, 28))
        fixture.assertMatches("crsi", IndicatorsExt.connorsRsi(close, 3, 2, 100))
        fixture.assertMatches("smi10", IndicatorsExt.stochasticMomentum(high, low, close, 10, 3, 3))
        fixture.assertMatches("bop", IndicatorsExt.balanceOfPower(open, high, low, close, 1))

        val trix = IndicatorsExt.trix(close, 18, 9)
        fixture.assertMatches("trix18", trix.line)
        fixture.assertMatches("trix18Signal", trix.signal)

        val fisher = IndicatorsExt.fisherTransform(high, low, 9)
        fixture.assertMatches("fisher9", fisher.line)
        fixture.assertMatches("fisher9Signal", fisher.signal)

        val ergodic = IndicatorsExt.smiErgodic(close, 20, 5, 5)
        fixture.assertMatches("smiErgodic", ergodic.line)
        fixture.assertMatches("smiErgodicSignal", ergodic.signal)
    }

    @Test
    fun `the extended volume measures match`() {
        fixture.assertMatches("adLine", IndicatorsExt.accumulationDistribution(high, low, close, volume))
        fixture.assertMatches("chaikinOsc", IndicatorsExt.chaikinOscillator(high, low, close, volume, 3, 10))
        fixture.assertMatches("eom14", IndicatorsExt.easeOfMovement(high, low, volume, 14, 1e8))
        fixture.assertMatches("forceIndex13", IndicatorsExt.forceIndex(close, volume, 13))
        fixture.assertMatches("pvt", IndicatorsExt.priceVolumeTrend(close, volume))

        val klinger = IndicatorsExt.klinger(high, low, close, volume, 34, 55, 13)
        fixture.assertMatches("klinger", klinger.line)
        fixture.assertMatches("klingerSignal", klinger.signal)
    }

    @Test
    fun `the pivot ladders match`() {
        // PivotSession.BAR, because that is what the JavaScript computes. The app draws DAILY —
        // see PivotSession for why the per-bar version is unusable on an intraday chart — so what
        // this checks is the formula, which is the part that was ported.
        val classic = Structure.pivots(CandleSeries(bars), Structure.PivotType.CLASSIC, Structure.PivotSession.BAR)
        fixture.assertMatches("pivotClassicP", classic[3].values)
        fixture.assertMatches("pivotClassicR1", classic[2].values)
        fixture.assertMatches("pivotClassicS1", classic[4].values)
        fixture.assertMatches(
            "pivotFibR2",
            Structure.pivots(CandleSeries(bars), Structure.PivotType.FIBONACCI, Structure.PivotSession.BAR)[1].values,
        )
        fixture.assertMatches(
            "pivotCamarillaR3",
            Structure.pivots(CandleSeries(bars), Structure.PivotType.CAMARILLA, Structure.PivotSession.BAR)[0].values,
        )
        fixture.assertMatches(
            "pivotWoodieS2",
            Structure.pivots(CandleSeries(bars), Structure.PivotType.WOODIE, Structure.PivotSession.BAR)[5].values,
        )
        fixture.assertMatches(
            "pivotDemarkP",
            Structure.pivots(CandleSeries(bars), Structure.PivotType.DEMARK, Structure.PivotSession.BAR)[3].values,
        )
    }

    @Test
    fun `the zigzag picks the same turns`() {
        // The one study whose output is a shape rather than a number, checked as a series anyway:
        // a turn one bar out is a different level, and the two products would disagree about it.
        fixture.assertMatches("zigzag5", Structure.zigzag(CandleSeries(bars), 5.0).first.values)
    }

    @Test
    fun `a volume indicator on a feed with no volume is empty, not flat zero`() {
        // A flat line at zero is a claim — "no accumulation". Nothing is the truth: no data. The
        // crypto feed reports volume and the forex one does not, so both cases are live in this app.
        val none = DoubleArray(close.size)
        val line = IndicatorsExt.accumulationDistribution(high, low, close, none)
        for (index in close.indices) assertEquals("bar $index", null, line[index])
    }

    @Test
    fun `warm-up is null, not zero and not the first real value`() {
        // The single most consequential convention in the file. A zero here draws a moving average
        // along the floor for the first nineteen bars and rescales the whole price axis with it.
        val sma = Indicators.sma(close, 20)
        for (index in 0 until 19) assertEquals(null, sma[index])
        assertNotNull(sma[19])
    }
}

/** The recorded JavaScript output, and the comparison against it. */
private class ParityFixture(val bars: List<Candle>, val series: Map<String, List<Double?>>) {

    fun assertMatches(name: String, actual: Line) {
        val expected = series[name] ?: error("fixture has no series '$name'")
        assertEquals("$name: length", expected.size, actual.size)
        for (index in expected.indices) {
            val want = expected[index]
            val got = actual[index]
            when {
                want == null && got == null -> Unit
                want == null ->
                    error("$name[$index]: JavaScript has no value here, Kotlin produced $got")
                got == null ->
                    error("$name[$index]: JavaScript produced $want, Kotlin has no value here")
                abs(want - got) > TOLERANCE ->
                    error("$name[$index]: JavaScript $want, Kotlin $got")
            }
        }
    }

    companion object {
        const val TOLERANCE = 1e-6

        fun load(): ParityFixture {
            val text = ParityFixture::class.java.getResourceAsStream("/indicator-parity.txt")
                ?.bufferedReader()?.readText()
                ?: error("indicator-parity.txt is missing from the test resources")

            val bars = mutableListOf<Candle>()
            val series = linkedMapOf<String, List<Double?>>()
            var mode = ""
            var pending = ""
            for (raw in text.lineSequence()) {
                val line = raw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue
                when {
                    line == "BARS" -> mode = "bars"
                    line.startsWith("SERIES ") -> {
                        mode = "series"
                        pending = line.removePrefix("SERIES ").trim()
                    }
                    mode == "bars" -> {
                        val parts = line.split(",")
                        bars += Candle(
                            t = parts[0].toLong(),
                            o = parts[1].toDouble(),
                            h = parts[2].toDouble(),
                            l = parts[3].toDouble(),
                            c = parts[4].toDouble(),
                            v = parts[5].toDouble(),
                        )
                    }
                    mode == "series" -> {
                        series[pending] = line.split(",").map { it.takeIf(String::isNotEmpty)?.toDouble() }
                    }
                }
            }
            return ParityFixture(bars, series)
        }
    }
}
