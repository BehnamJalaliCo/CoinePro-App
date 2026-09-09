package com.coinepro.core.chart

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Every built-in indicator against an outside reference, at 1e-6.
 *
 * [IndicatorParityTest] proves the Kotlin is a faithful port of the web terminal's JavaScript.
 * This test proves the pair of them is *right*: the fixture is the same 120-bar random walk run
 * through the `ta` Python library (bukosabino/ta) where its definition is the textbook one, and
 * through a formula written out in pandas where it is not — Wilder's RSI with its simple-mean
 * seed, Aroon over `length + 1` bars, TradingView's EOM scaling. Which reference each series
 * uses is in the fixture's header, beside `scripts/quality/gen_indicator_reference.py`, which
 * regenerates it.
 *
 * ### The conventions this pins
 *
 * - **EMA seeds with the first value**, not with an SMA of the first n. That is pandas', `ta`'s,
 *   this engine's and the web terminal's convention; TA-Lib's and TradingView's `ta.ema` seed with
 *   the SMA and agree to 1e-6 only after roughly five periods. A reader comparing an early EMA
 *   value with TradingView's will see the transient; the steady state is identical.
 * - **A signal line over a series with an undefined head** (MACD, PPO, PVO, TRIX, KST) starts at
 *   the first defined value. The engine used to run the recursion through the head as zeros; the
 *   reference caught it.
 * - **RSI, ATR, DMI** are Wilder's, seeded with a simple mean — TA-Lib's and TradingView's
 *   `ta.rma`. `ta` seeds RSI from the first bar and its ADX is not Wilder's; those series are
 *   written out in the generator rather than taken from `ta`.
 *
 * A series that the reference computes and the engine does not expose under the same parameters
 * is not compared; there are none of those at the moment, and the generator's series list and
 * this test's calls are the two places to look when one appears.
 */
class IndicatorReferenceTest {

    private val fixture = ReferenceFixture.load()
    private val bars = fixture.bars
    private val high = DoubleArray(bars.size) { bars[it].h }
    private val low = DoubleArray(bars.size) { bars[it].l }
    private val close = DoubleArray(bars.size) { bars[it].c }
    private val volume = DoubleArray(bars.size) { bars[it].v ?: 0.0 }

    @Test
    fun `the fixture is the parity file's walk`() {
        assertEquals(120, bars.size)
        assertEquals(ParityFixtureBars.load().first().c, bars.first().c, 0.0)
        assertTrue(fixture.series.size >= 55)
    }

    @Test
    fun `averages`() {
        fixture.check("sma20", Indicators.sma(close, 20))
        fixture.check("ema20", Indicators.ema(close, 20))
        fixture.check("wma20", Indicators.wma(close, 20))
        fixture.check("kama", IndicatorsExt.kama(close, 10, 2, 30))
        fixture.check("stdDev20", IndicatorsExt.stdDev(close, 20))
        fixture.verify()
    }

    @Test
    fun `momentum`() {
        fixture.check("rsi14", Indicators.rsi(close, 14))
        fixture.check("cci20", Indicators.cci(high, low, close, 20))
        fixture.check("wr14", Indicators.williamsR(high, low, close, 14))
        val stochastic = Indicators.stochastic(high, low, close, 14, 3)
        fixture.check("stochK", stochastic.k)
        fixture.check("stochD", stochastic.d)
        fixture.check("roc10", IndicatorsExt.rateOfChange(close, 10))
        fixture.check("momentum10", IndicatorsExt.momentum(close, 10))
        fixture.check("uo", IndicatorsExt.ultimateOscillator(high, low, close, 7, 14, 28))
        fixture.check("ao", IndicatorsExtB.awesomeOscillator(high, low))
        val tsi = IndicatorsExtB.trueStrengthIndex(close, 25, 13, 13)
        fixture.check("tsi", tsi.tsi)
        val smi = IndicatorsExt.smiErgodic(close, 20, 5, 5)
        fixture.check("smiErgodic", smi.line)
        fixture.check("smiErgodicSignal", smi.signal)
        val stochRsi = IndicatorsExtB.stochasticRsi(close, 14, 14, 3, 3)
        fixture.check("stochRsiK", stochRsi.k)
        fixture.check("stochRsiD", stochRsi.d)
        fixture.verify()
    }

    @Test
    fun `macd and the percentage oscillators`() {
        val macd = Indicators.macd(close, 12, 26, 9)
        fixture.check("macd", macd.macd)
        fixture.check("macdSignal", macd.signal)
        fixture.check("macdHist", macd.histogram)
        val ppo = IndicatorsExtB.ppo(close, 12, 26, 9)
        fixture.check("ppo", ppo.oscillator)
        fixture.check("ppoSignal", ppo.signal)
        val pvo = IndicatorsExtB.pvo(volume, 12, 26, 9)
        fixture.check("pvo", pvo.oscillator)
        fixture.check("pvoSignal", pvo.signal)
        fixture.verify()
    }

    @Test
    fun `volatility`() {
        fixture.check("tr", Line.of(close.size) { Indicators.trueRange(high, low, close)[it] })
        fixture.check("atr14", Indicators.atr(high, low, close, 14))
        val bb = Indicators.bollinger(close, 20, 2.0)
        fixture.check("bbBasis", bb.basis)
        fixture.check("bbUpper", bb.upper)
        fixture.check("bbLower", bb.lower)
        val kc = Indicators.keltner(high, low, close, 20, 2.0)
        fixture.check("kcBasis", kc.basis)
        fixture.check("kcUpper", kc.upper)
        fixture.check("kcLower", kc.lower)
        val dc = Indicators.donchian(high, low, 20)
        fixture.check("dcUpper", dc.upper)
        fixture.check("dcLower", dc.lower)
        fixture.check("dcBasis", dc.basis)
        fixture.verify()
    }

    @Test
    fun `trend`() {
        val adx = Indicators.adx(high, low, close, 14)
        fixture.check("plusDi", adx.plusDi)
        fixture.check("minusDi", adx.minusDi)
        fixture.check("adx14", adx.adx)
        val vortex = Indicators.vortex(high, low, close, 14)
        fixture.check("vortexPlus", vortex.plus)
        fixture.check("vortexMinus", vortex.minus)
        val ichimoku = Indicators.ichimoku(high, low, 9, 26, 52)
        fixture.check("tenkan", ichimoku.tenkan)
        fixture.check("kijun", ichimoku.kijun)
        fixture.check("spanA", ichimoku.spanA)
        fixture.check("spanB", ichimoku.spanB)
        val trix = IndicatorsExt.trix(close, 18, 9)
        fixture.check("trix", trix.line)
        fixture.check("trixSignal", trix.signal)
        fixture.check("dpo20", IndicatorsExtC.detrendedPriceOscillator(close, 20))
        val kst = IndicatorsExtC.knowSureThing(close)
        fixture.check("kst", kst.kst)
        fixture.check("kstSignal", kst.signal)
        fixture.check("massIndex", IndicatorsExtC.massIndex(high, low, 25, 9))
        val aroon = IndicatorsExtB.aroon(high, low, 14)
        fixture.check("aroonUp", aroon.up)
        fixture.check("aroonDown", aroon.down)
        fixture.check("psar", IndicatorsExtB.parabolicSar(high, low, 0.02, 0.2))
        fixture.verify()
    }

    @Test
    fun `volume`() {
        fixture.check("obv", Indicators.obv(close, volume))
        fixture.check("vwap", Indicators.vwap(high, low, close, volume))
        fixture.check("mfi14", IndicatorsExtB.moneyFlowIndex(high, low, close, volume, 14))
        fixture.check("cmf20", IndicatorsExtB.chaikinMoneyFlow(high, low, close, volume, 20))
        fixture.check("adl", IndicatorsExt.accumulationDistribution(high, low, close, volume))
        fixture.check("forceIndex13", IndicatorsExt.forceIndex(close, volume, 13))
        fixture.check("eom14", IndicatorsExt.easeOfMovement(high, low, volume, 14))
        fixture.check("pvt", IndicatorsExt.priceVolumeTrend(close, volume))
        fixture.verify()
    }
}

/** The parity file's bars, for the cross-check that both golden sets share one input. */
internal object ParityFixtureBars {
    fun load(): List<Candle> = ReferenceFixture.parse("/indicator-parity.txt").bars
}

internal class ReferenceFixture(val bars: List<Candle>, val series: Map<String, List<Double?>>) {

    fun check(name: String, actual: Line) = check(name, List(actual.size) { actual[it] })

    fun check(name: String, actual: DoubleArray, fromBar: Int = 0) =
        check(name, actual.map { if (it.isNaN()) null else it }, fromBar)

    /** Differences found so far; [verify] asserts on all of them at once, so one test names every series. */
    private val differences = mutableListOf<String>()

    fun verify() {
        val report = differences.joinToString("\n\n")
        differences.clear()
        assertTrue(report, report.isEmpty())
    }

    /** [fromBar] skips an opening the reference and the engine define by different conventions. */
    fun check(name: String, actual: List<Double?>, fromBar: Int = 0) {
        val expected = series[name] ?: error("reference has no series '$name'")
        assertEquals("$name: length", expected.size, actual.size)
        val problems = mutableListOf<String>()
        for (index in fromBar until expected.size) {
            val want = expected[index]
            val got = actual[index]
            when {
                want == null && got == null -> Unit
                want == null -> problems += "[$index] reference undefined, engine $got"
                got == null -> problems += "[$index] reference $want, engine undefined"
                abs(want - got) > TOLERANCE * maxOf(1.0, abs(want)) ->
                    problems += "[$index] reference $want, engine $got (Δ ${abs(want - got)})"
            }
        }
        if (problems.isNotEmpty()) {
            differences += "$name: ${problems.size} of ${expected.size} differ\n" + problems.take(4).joinToString("\n")
        }
    }

    companion object {
        /** Absolute below one, relative above: a cumulative series in the ten-thousands carries 1e-10 of float noise. */
        const val TOLERANCE = 1e-6

        fun load(): ReferenceFixture = parse("/indicator-reference.txt")

        fun parse(resource: String): ReferenceFixture {
            val text = ReferenceFixture::class.java.getResourceAsStream(resource)
                ?.bufferedReader()?.readText()
                ?: error("$resource is missing from the test resources")
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
                    mode == "series" -> series[pending] = line.split(",").map { it.takeIf(String::isNotEmpty)?.toDouble() }
                }
            }
            return ReferenceFixture(bars, series)
        }
    }
}
