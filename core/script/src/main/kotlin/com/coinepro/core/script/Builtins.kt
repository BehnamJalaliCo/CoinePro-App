package com.coinepro.core.script

import com.coinepro.core.chart.CandleSeries
import com.coinepro.core.chart.Indicators
import com.coinepro.core.chart.IndicatorsExt
import com.coinepro.core.chart.Line
import com.coinepro.core.common.toPersianDigits
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Every function a script may call.
 *
 * The `ta.` family delegates to `core:chart`'s indicator library rather than reimplementing it.
 * That is the point of the whole design: the moving average a script draws is bit-for-bit the one
 * the chart's own EMA toggle draws, so a reader who checks one against the other finds them equal.
 * A second implementation would eventually disagree with the first, and the reader would be right
 * to trust neither.
 */
internal object Builtins {

    /**
     * How far a series may typically sit from the close, as a fraction of the price's own range,
     * and still be drawn over the candles.
     */
    private const val OVERLAY_DISTANCE = 0.25

    fun call(interpreter: Interpreter, node: Call): Value {
        val arguments = Arguments(interpreter, node)
        return when (node.qualified) {

            /* ---------------------------------------------------------- moving averages */
            "ta.sma" -> series(interpreter, arguments.source(0).through { Indicators.sma(it, arguments.length(1)) })
            "ta.ema" -> series(interpreter, arguments.source(0).through { Indicators.ema(it, arguments.length(1)) })
            "ta.wma" -> series(interpreter, arguments.source(0).through { Indicators.wma(it, arguments.length(1)) })
            "ta.hma" -> series(interpreter, arguments.source(0).through { Indicators.hma(it, arguments.length(1)) })

            /* ---------------------------------------------------------- oscillators */
            "ta.rsi" -> series(interpreter, arguments.source(0).through { Indicators.rsi(it, arguments.length(1, default = 14)) })
            "ta.cci" -> series(
                interpreter,
                Indicators.cci(
                    interpreter.candles.high,
                    interpreter.candles.low,
                    interpreter.candles.close,
                    arguments.length(0, default = 20),
                ),
            )
            "ta.atr" -> series(
                interpreter,
                Indicators.atr(
                    interpreter.candles.high,
                    interpreter.candles.low,
                    interpreter.candles.close,
                    arguments.length(0, default = 14),
                ),
            )
            "ta.macd" -> series(
                interpreter,
                arguments.source(0).through {
                    Indicators.macd(
                        it,
                        arguments.length(1, default = 12),
                        arguments.length(2, default = 26),
                        arguments.length(3, default = 9),
                    ).macd
                },
            )
            "ta.macd_signal" -> series(
                interpreter,
                arguments.source(0).through {
                    Indicators.macd(
                        it,
                        arguments.length(1, default = 12),
                        arguments.length(2, default = 26),
                        arguments.length(3, default = 9),
                    ).signal
                },
            )
            "ta.macd_hist" -> series(
                interpreter,
                arguments.source(0).through {
                    Indicators.macd(
                        it,
                        arguments.length(1, default = 12),
                        arguments.length(2, default = 26),
                        arguments.length(3, default = 9),
                    ).histogram
                },
            )

            /* ---------------------------------------------------------- bands */
            "ta.bb_upper" -> series(interpreter, band(arguments).upper)
            "ta.bb_lower" -> series(interpreter, band(arguments).lower)
            "ta.bb_basis" -> series(interpreter, band(arguments).basis)
            "ta.donchian_upper" -> series(interpreter, donchian(interpreter, arguments).upper)
            "ta.donchian_lower" -> series(interpreter, donchian(interpreter, arguments).lower)

            /* ---------------------------------------------------------- trend engines */
            "ta.supertrend" -> series(interpreter, superTrend(interpreter, arguments).line)
            "ta.supertrend_trend" -> series(interpreter, superTrend(interpreter, arguments).trend)
            "ta.adx" -> series(interpreter, directional(interpreter, arguments).adx)
            "ta.di_plus" -> series(interpreter, directional(interpreter, arguments).plusDi)
            "ta.di_minus" -> series(interpreter, directional(interpreter, arguments).minusDi)
            "ta.stoch_k" -> series(interpreter, stochastic(interpreter, arguments).k)
            "ta.stoch_d" -> series(interpreter, stochastic(interpreter, arguments).d)

            /* ---------------------------------------------------------- ichimoku */
            // Four separate accessors rather than one call with a field, because the language has
            // no record type. The spans come back **undisplaced**: what `ta.ichimoku_span_a` gives
            // at bar i is the value computed from bars up to i, and a script that wants the cloud
            // a chart draws *at* bar i asks for `ta.ichimoku_span_a(9, 26)[26]`. Shifting it here
            // instead would hand every caller a series whose bar i was computed from bar i+26.
            "ta.ichimoku_conversion" -> series(interpreter, ichimoku(interpreter, arguments).tenkan)
            "ta.ichimoku_base" -> series(interpreter, ichimoku(interpreter, arguments).kijun)
            "ta.ichimoku_span_a" -> series(interpreter, ichimoku(interpreter, arguments).spanA)
            "ta.ichimoku_span_b" -> series(interpreter, ichimoku(interpreter, arguments).spanB)

            /* ---------------------------------------------------------- volume */
            "ta.vwap" -> series(interpreter, vwap(interpreter))

            /* ---------------------------------------------------------- statistics */
            "ta.stdev" -> series(interpreter, rolling(interpreter, arguments.source(0), arguments.length(1)) { window ->
                val mean = window.average()
                sqrt(window.sumOf { (it - mean) * (it - mean) } / window.size)
            })
            "ta.highest" -> series(interpreter, rolling(interpreter, arguments.source(0), arguments.length(1)) { it.max() })
            "ta.lowest" -> series(interpreter, rolling(interpreter, arguments.source(0), arguments.length(1)) { it.min() })
            "ta.sum" -> series(interpreter, rolling(interpreter, arguments.source(0), arguments.length(1)) { it.sum() })

            /* ---------------------------------------------------------- change */
            "ta.change" -> {
                val source = arguments.source(0)
                val back = arguments.length(1, default = 1)
                series(interpreter, Line.of(interpreter.barCount) { index ->
                    if (index < back) return@of null
                    val now = source[index] ?: return@of null
                    val then = source[index - back] ?: return@of null
                    now - then
                })
            }
            "ta.roc" -> {
                val source = arguments.source(0)
                val back = arguments.length(1, default = 1)
                series(interpreter, Line.of(interpreter.barCount) { index ->
                    if (index < back) return@of null
                    val now = source[index] ?: return@of null
                    val previous = source[index - back] ?: return@of null
                    if (previous == 0.0) null else (now - previous) / previous * 100.0
                })
            }

            /* ---------------------------------------------------------- more averages */
            "ta.smma" -> series(interpreter, arguments.source(0).through { IndicatorsExt.smma(it, arguments.length(1)) })
            "ta.zlema" -> series(interpreter, arguments.source(0).through { IndicatorsExt.zlema(it, arguments.length(1)) })
            "ta.kama" -> series(
                interpreter,
                arguments.source(0).through {
                    IndicatorsExt.kama(
                        it,
                        arguments.length(1, default = 10),
                        arguments.length(2, default = 2),
                        arguments.length(3, default = 30),
                    )
                },
            )
            "ta.mcginley" -> series(interpreter, arguments.source(0).through { IndicatorsExt.mcginley(it, arguments.length(1, default = 14)) })
            "ta.linreg" -> series(interpreter, arguments.source(0).through { IndicatorsExt.linearRegression(it, arguments.length(1)) })
            /* ---------------------------------------------------------- more oscillators */
            "ta.momentum" -> series(interpreter, arguments.source(0).through { IndicatorsExt.momentum(it, arguments.length(1, default = 10)) })
            "ta.williams_r" -> series(interpreter, withHlc(interpreter) { h, l, c -> Indicators.williamsR(h, l, c, arguments.length(0, default = 14)) })
            "ta.ultimate" -> series(
                interpreter,
                withHlc(interpreter) { h, l, c ->
                    IndicatorsExt.ultimateOscillator(
                        h, l, c,
                        arguments.length(0, default = 7),
                        arguments.length(1, default = 14),
                        arguments.length(2, default = 28),
                    )
                },
            )
            "ta.trix" -> series(interpreter, arguments.source(0).through { IndicatorsExt.trix(it, arguments.length(1, default = 18), arguments.length(2, default = 9)).line })
            "ta.trix_signal" -> series(interpreter, arguments.source(0).through { IndicatorsExt.trix(it, arguments.length(1, default = 18), arguments.length(2, default = 9)).signal })
            "ta.fisher" -> series(interpreter, withHl(interpreter) { h, l -> IndicatorsExt.fisherTransform(h, l, arguments.length(0, default = 9)).line })
            "ta.fisher_signal" -> series(interpreter, withHl(interpreter) { h, l -> IndicatorsExt.fisherTransform(h, l, arguments.length(0, default = 9)).signal })
            "ta.crsi" -> series(
                interpreter,
                arguments.source(0).through {
                    IndicatorsExt.connorsRsi(
                        it,
                        arguments.length(1, default = 3),
                        arguments.length(2, default = 2),
                        arguments.length(3, default = 100),
                    )
                },
            )
            "ta.smi" -> series(interpreter, arguments.source(0).through { IndicatorsExt.smiErgodic(it, arguments.length(1, default = 20), arguments.length(2, default = 5), arguments.length(3, default = 5)).line })
            "ta.smi_signal" -> series(interpreter, arguments.source(0).through { IndicatorsExt.smiErgodic(it, arguments.length(1, default = 20), arguments.length(2, default = 5), arguments.length(3, default = 5)).signal })
            "ta.chop" -> series(interpreter, withHlc(interpreter) { h, l, c -> Indicators.choppiness(h, l, c, arguments.length(0, default = 14)) })
            "ta.bop" -> series(
                interpreter,
                IndicatorsExt.balanceOfPower(
                    interpreter.candles.open,
                    interpreter.candles.high,
                    interpreter.candles.low,
                    interpreter.candles.close,
                    arguments.length(0, default = 1),
                ),
            )
            "ta.vortex_plus" -> series(interpreter, withHlc(interpreter) { h, l, c -> Indicators.vortex(h, l, c, arguments.length(0, default = 14)).plus })
            "ta.vortex_minus" -> series(interpreter, withHlc(interpreter) { h, l, c -> Indicators.vortex(h, l, c, arguments.length(0, default = 14)).minus })
            /* ---------------------------------------------------------- volatility */
            "ta.tr" -> series(interpreter, withHlc(interpreter) { h, l, c -> Line.from(Indicators.trueRange(h, l, c).map { v -> v.takeIf(Double::isFinite) }) })
            "ta.hv" -> series(interpreter, arguments.source(0).through { IndicatorsExt.historicalVolatility(it, arguments.length(1, default = 10)) })
            "ta.chaikin_vol" -> series(interpreter, withHl(interpreter) { h, l -> IndicatorsExt.chaikinVolatility(h, l, arguments.length(0, default = 10), arguments.length(1, default = 10)) })
            "ta.bb_percent" -> series(interpreter, arguments.source(0).through { IndicatorsExt.bollingerPercent(it, arguments.length(1, default = 20), multiplierOf(arguments, 2)) })
            "ta.bb_width" -> series(interpreter, arguments.source(0).through { IndicatorsExt.bollingerWidth(it, arguments.length(1, default = 20), multiplierOf(arguments, 2)) })
            "ta.keltner_upper" -> series(interpreter, keltner(interpreter, arguments).upper)
            "ta.keltner_lower" -> series(interpreter, keltner(interpreter, arguments).lower)
            "ta.keltner_basis" -> series(interpreter, keltner(interpreter, arguments).basis)
            "ta.env_upper" -> series(interpreter, envelope(arguments).upper)
            "ta.env_lower" -> series(interpreter, envelope(arguments).lower)
            "ta.env_basis" -> series(interpreter, envelope(arguments).basis)
            /* ---------------------------------------------------------- more volume */
            "ta.obv" -> series(interpreter, withVolume(interpreter) { c -> Indicators.obv(c.close, c.volume) })
            "ta.ad" -> series(interpreter, withVolume(interpreter) { c -> IndicatorsExt.accumulationDistribution(c.high, c.low, c.close, c.volume) })
            "ta.pvt" -> series(interpreter, withVolume(interpreter) { c -> IndicatorsExt.priceVolumeTrend(c.close, c.volume) })
            "ta.force" -> series(interpreter, withVolume(interpreter) { c -> IndicatorsExt.forceIndex(c.close, c.volume, arguments.length(0, default = 13)) })
            "ta.chaikin_osc" -> series(interpreter, withVolume(interpreter) { c -> IndicatorsExt.chaikinOscillator(c.high, c.low, c.close, c.volume, arguments.length(0, default = 3), arguments.length(1, default = 10)) })
            "ta.eom" -> series(interpreter, withVolume(interpreter) { c -> IndicatorsExt.easeOfMovement(c.high, c.low, c.volume, arguments.length(0, default = 14)) })
            "ta.klinger" -> series(interpreter, withVolume(interpreter) { c -> IndicatorsExt.klinger(c.high, c.low, c.close, c.volume, arguments.length(0, default = 34), arguments.length(1, default = 55), arguments.length(2, default = 13)).line })
            "ta.klinger_signal" -> series(interpreter, withVolume(interpreter) { c -> IndicatorsExt.klinger(c.high, c.low, c.close, c.volume, arguments.length(0, default = 34), arguments.length(1, default = 55), arguments.length(2, default = 13)).signal })
            /* ---------------------------------------------------------- bar logic */
            "ta.rising" -> monotone(interpreter, arguments, rising = true)
            "ta.falling" -> monotone(interpreter, arguments, rising = false)
            "ta.barssince" -> {
                val condition = interpreter.flagLine(arguments.value(0), node)
                var since: Int? = null
                series(interpreter, Line.of(interpreter.barCount) { index ->
                    val fired = condition[index]?.let { it != 0.0 } ?: false
                    since = if (fired) 0 else since?.plus(1)
                    since?.toDouble()
                })
            }
            "ta.valuewhen" -> {
                val condition = interpreter.flagLine(arguments.value(0), node)
                val source = arguments.source(1)
                // Zero is the latest occurrence, so this is a count and not a period.
                val occurrence = if (arguments.size > 2) arguments.constant(2, "شمارهٔ رخداد").toInt().coerceAtLeast(0) else 0
                val held = ArrayDeque<Double?>()
                series(interpreter, Line.of(interpreter.barCount) { index ->
                    val fired = condition[index]?.let { it != 0.0 } ?: false
                    if (fired) {
                        held.addFirst(source[index])
                        while (held.size > occurrence + 1) held.removeLast()
                    }
                    held.getOrNull(occurrence)
                })
            }
            "ta.cum" -> {
                val source = arguments.source(0)
                var total = 0.0
                series(interpreter, Line.of(interpreter.barCount) { index ->
                    source[index]?.let { total += it }
                    total
                })
            }
            "ta.pivothigh" -> pivot(interpreter, arguments, high = true)
            "ta.pivotlow" -> pivot(interpreter, arguments, high = false)
            /* ---------------------------------------------------------- crosses */
            "ta.crossover" -> cross(interpreter, node, arguments, upward = true)
            "ta.crossunder" -> cross(interpreter, node, arguments, upward = false)

            /* ---------------------------------------------------------- maths */
            "math.abs" -> unary(interpreter, node, arguments) { abs(it) }
            "math.floor" -> unary(interpreter, node, arguments) { floor(it) }
            "math.ceil" -> unary(interpreter, node, arguments) { ceil(it) }
            "math.round" -> unary(interpreter, node, arguments) { kotlin.math.round(it) }
            "math.sqrt" -> unary(interpreter, node, arguments) { if (it < 0) Double.NaN else sqrt(it) }
            "math.log" -> unary(interpreter, node, arguments) { if (it <= 0) Double.NaN else ln(it) }
            "math.sign" -> unary(interpreter, node, arguments) { sign(it) }
            "math.max" -> binary(interpreter, node, arguments) { a, b -> max(a, b) }
            "math.min" -> binary(interpreter, node, arguments) { a, b -> min(a, b) }
            "math.pow" -> binary(interpreter, node, arguments) { a, b -> a.pow(b) }

            /* ---------------------------------------------------------- control */
            "iff" -> {
                val condition = interpreter.flagLine(arguments.value(0), node)
                val whenTrue = arguments.source(1)
                val whenFalse = arguments.source(2)
                series(interpreter, Line.of(interpreter.barCount) { index ->
                    val decided = condition[index] ?: return@of null
                    if (decided != 0.0) whenTrue[index] else whenFalse[index]
                })
            }
            "nz" -> {
                val source = arguments.source(0)
                val replacement = if (arguments.size > 1) arguments.constant(1, "مقدار جایگزین") else 0.0
                series(interpreter, Line.of(interpreter.barCount) { source[it] ?: replacement })
            }

            /* ---------------------------------------------------------- inputs */
            "input" -> input(interpreter, node, arguments)

            /* ---------------------------------------------------------- output */
            "plot" -> plot(interpreter, node, arguments)
            "hline" -> hline(interpreter, node, arguments)
            "marker" -> marker(interpreter, node, arguments)
            "signal" -> signal(interpreter, node, arguments)
            "log" -> {
                interpreter.addLog(arguments.text(0))
                Value.Flag(true)
            }

            else -> throw ScriptError("تابع «${node.qualified}» وجود ندارد", node.line, node.column)
        }
    }

    /* ------------------------------------------------------------------ groups */

    private fun band(arguments: Arguments): com.coinepro.core.chart.Band {
        val line = arguments.source(0)
        val period = arguments.length(1, default = 20)
        val multiplier = if (arguments.size > 2) arguments.constant(2, "ضریب") else 2.0
        val source = Source.of(line)
        if (source.values.isEmpty()) {
            val empty = Line.empty(line.size)
            return com.coinepro.core.chart.Band(empty, empty, empty)
        }
        val band = Indicators.bollinger(source.values, period, multiplier)
        // Re-aligned as a group so the three edges stay on the same bars as each other.
        return com.coinepro.core.chart.Band(
            source.realign(band.basis),
            source.realign(band.upper),
            source.realign(band.lower),
        )
    }

    private fun donchian(interpreter: Interpreter, arguments: Arguments): com.coinepro.core.chart.Band =
        Indicators.donchian(
            interpreter.candles.high,
            interpreter.candles.low,
            arguments.length(0, default = 20),
        )

    private fun superTrend(
        interpreter: Interpreter,
        arguments: Arguments,
    ): com.coinepro.core.chart.SuperTrendResult = Indicators.supertrend(
        interpreter.candles.high,
        interpreter.candles.low,
        interpreter.candles.close,
        arguments.length(0, default = 10),
        if (arguments.size > 1) arguments.constant(1, "ضریب") else 3.0,
    )

    private fun directional(
        interpreter: Interpreter,
        arguments: Arguments,
    ): com.coinepro.core.chart.AdxResult = Indicators.adx(
        interpreter.candles.high,
        interpreter.candles.low,
        interpreter.candles.close,
        arguments.length(0, default = 14),
    )

    private fun stochastic(
        interpreter: Interpreter,
        arguments: Arguments,
    ): com.coinepro.core.chart.StochasticResult = Indicators.stochastic(
        interpreter.candles.high,
        interpreter.candles.low,
        interpreter.candles.close,
        arguments.length(0, default = 14),
        arguments.length(1, default = 3),
    )

    private fun ichimoku(
        interpreter: Interpreter,
        arguments: Arguments,
    ): com.coinepro.core.chart.IchimokuResult = Indicators.ichimoku(
        interpreter.candles.high,
        interpreter.candles.low,
        arguments.length(0, default = 9),
        arguments.length(1, default = 26),
        arguments.length(2, default = 52),
    )

    /**
     * Volume-weighted average price, or nothing at all where the feed reports no volume.
     *
     * The library's own VWAP falls back to the close on a bar with no volume, which is the right
     * answer for a *drawn* line — it keeps the curve continuous through a quiet bar. It is the
     * wrong answer for a *condition*: on the MT5 side no bar carries volume, so the fallback makes
     * `close > ta.vwap()` compare the close against itself and return false on every bar of every
     * chart. A filter that is silently and permanently false is worse than one that is absent,
     * because `and` propagates absence and a reader sees a strategy that draws nothing rather than
     * a strategy that quietly lost a third of its evidence.
     */
    /* ------------------------------------------------------------------ second-wave groups */

    /**
     * The chart's own high/low/close, handed to an indicator that reads them whole.
     *
     * The three columns are the chart's, not a script source, so there is no `Source` offset to
     * realign: every one of these indicators is aligned to the bar it was computed on.
     */
    private inline fun withHlc(interpreter: Interpreter, compute: (DoubleArray, DoubleArray, DoubleArray) -> Line): Line {
        val candles = interpreter.candles
        return compute(candles.high, candles.low, candles.close)
    }

    private inline fun withHl(interpreter: Interpreter, compute: (DoubleArray, DoubleArray) -> Line): Line {
        val candles = interpreter.candles
        return compute(candles.high, candles.low)
    }

    /**
     * A volume indicator, or an empty line on a feed with no volume.
     *
     * Empty rather than zero, the same rule `ta.vwap` follows: a feed that sends no volume has
     * not said the volume was nought, and an OBV drawn flat at zero would say exactly that.
     */
    private inline fun withVolume(interpreter: Interpreter, compute: (CandleSeries) -> Line): Line {
        val candles = interpreter.candles
        if (!candles.hasVolume) return Line.empty(interpreter.barCount)
        return compute(candles)
    }

    private fun multiplierOf(arguments: Arguments, index: Int): Double =
        if (arguments.size > index) arguments.constant(index, "ضریب") else 2.0

    private fun keltner(interpreter: Interpreter, arguments: Arguments): com.coinepro.core.chart.Band =
        Indicators.keltner(
            interpreter.candles.high,
            interpreter.candles.low,
            interpreter.candles.close,
            arguments.length(0, default = 20),
            multiplierOf(arguments, 1),
        )

    private fun envelope(arguments: Arguments): com.coinepro.core.chart.Band {
        val line = arguments.source(0)
        val period = arguments.length(1, default = 20)
        val percent = if (arguments.size > 2) arguments.constant(2, "درصد") else 1.0
        val source = Source.of(line)
        if (source.values.isEmpty()) {
            val empty = Line.empty(line.size)
            return com.coinepro.core.chart.Band(empty, empty, empty)
        }
        val band = IndicatorsExt.envelopes(source.values, period, percent)
        return com.coinepro.core.chart.Band(
            source.realign(band.basis),
            source.realign(band.upper),
            source.realign(band.lower),
        )
    }

    /** True on a bar where the source has risen (or fallen) on each of the last `length` bars. */
    private fun monotone(interpreter: Interpreter, arguments: Arguments, rising: Boolean): Value {
        val source = arguments.source(0)
        val length = arguments.length(1, default = 1)
        return Value.FlagSeries(
            Line.of(interpreter.barCount) { index ->
                if (index < length) return@of null
                for (back in 0 until length) {
                    val now = source[index - back] ?: return@of null
                    val then = source[index - back - 1] ?: return@of null
                    val ok = if (rising) now > then else now < then
                    if (!ok) return@of 0.0
                }
                1.0
            },
        )
    }

    /**
     * A pivot, reported on the bar that confirms it.
     *
     * `ta.pivothigh(left, right)` is the high of a bar that is higher than the `left` bars before
     * it and the `right` bars after it; the value is placed on the bar `right` bars later, which
     * is the first bar on which the pivot is known — and nowhere else, so nothing here repaints.
     */
    private fun pivot(interpreter: Interpreter, arguments: Arguments, high: Boolean): Value {
        val left = arguments.length(0, default = 5)
        val right = if (arguments.size > 1) arguments.length(1, default = left) else left
        val column = if (high) interpreter.candles.high else interpreter.candles.low
        return series(interpreter, Line.of(interpreter.barCount) { index ->
            val centre = index - right
            if (centre - left < 0) return@of null
            val candidate = column[centre]
            for (offset in 1..left) {
                val other = column[centre - offset]
                if (if (high) other >= candidate else other <= candidate) return@of null
            }
            for (offset in 1..right) {
                val other = column[centre + offset]
                if (if (high) other >= candidate else other <= candidate) return@of null
            }
            candidate
        })
    }

    private fun vwap(interpreter: Interpreter): Line {
        val candles = interpreter.candles
        if (!candles.hasVolume) return Line.empty(interpreter.barCount)
        return Indicators.vwap(candles.high, candles.low, candles.close, candles.volume)
    }

    private fun cross(interpreter: Interpreter, node: Call, arguments: Arguments, upward: Boolean): Value {
        val a = arguments.source(0)
        val b = arguments.source(1)
        return Value.FlagSeries(
            Line.of(interpreter.barCount) { index ->
                if (index == 0) return@of null
                val nowA = a[index] ?: return@of null
                val nowB = b[index] ?: return@of null
                val wasA = a[index - 1] ?: return@of null
                val wasB = b[index - 1] ?: return@of null
                // Strictly crossing: equal on both bars is not a cross, and equal on the previous
                // bar counts only if it moved clear on this one. Without that, a flat pair fires
                // on every bar it touches.
                val crossed = if (upward) wasA <= wasB && nowA > nowB else wasA >= wasB && nowA < nowB
                if (crossed) 1.0 else 0.0
            },
        )
    }

    private inline fun rolling(
        interpreter: Interpreter,
        source: Line,
        window: Int,
        crossinline operation: (DoubleArray) -> Double,
    ): Line = Line.of(interpreter.barCount) { index ->
        if (index + 1 < window) return@of null
        val values = DoubleArray(window)
        for (offset in 0 until window) {
            values[offset] = source[index - window + 1 + offset] ?: return@of null
        }
        operation(values)
    }

    private inline fun unary(
        interpreter: Interpreter,
        node: Call,
        arguments: Arguments,
        crossinline operation: (Double) -> Double,
    ): Value {
        val value = arguments.value(0)
        if (value is Value.Num) return Value.Num(operation(value.value))
        val line = interpreter.numberLine(value, node)
        return series(interpreter, Line.of(interpreter.barCount) { line[it]?.let(operation) })
    }

    private inline fun binary(
        interpreter: Interpreter,
        node: Call,
        arguments: Arguments,
        crossinline operation: (Double, Double) -> Double,
    ): Value {
        val first = arguments.value(0)
        val second = arguments.value(1)
        if (first is Value.Num && second is Value.Num) return Value.Num(operation(first.value, second.value))
        val a = interpreter.numberLine(first, node)
        val b = interpreter.numberLine(second, node)
        return series(interpreter, Line.of(interpreter.barCount) { index ->
            val left = a[index] ?: return@of null
            val right = b[index] ?: return@of null
            operation(left, right)
        })
    }

    private fun series(interpreter: Interpreter, line: Line): Value {
        require(line.size == interpreter.barCount)
        return Value.NumberSeries(line)
    }

    /* ------------------------------------------------------------------ inputs and output */

    private fun input(interpreter: Interpreter, node: Call, arguments: Arguments): Value {
        val default = arguments.constant(0, "مقدار پیش‌فرض")
        val title = if (arguments.has("title")) arguments.named("title").let { arguments.textOf(it) } else "ورودی"
        val minimum = if (arguments.has("min")) arguments.constantOf(arguments.named("min"), "کمینه") else null
        val maximum = if (arguments.has("max")) arguments.constantOf(arguments.named("max"), "بیشینه") else null

        // A value the reader set in the panel wins over the default written in the script — that is
        // the whole point of declaring an input. Clamped to the declared range so a stored value
        // from an earlier version of the script cannot take it outside its own bounds.
        val supplied = interpreter.override(title)
        val effective = (supplied ?: default)
            .let { if (minimum != null) max(it, minimum) else it }
            .let { if (maximum != null) min(it, maximum) else it }

        interpreter.addInput(ScriptInput(title, effective, minimum, maximum))
        return Value.Num(effective)
    }

    private fun plot(interpreter: Interpreter, node: Call, arguments: Arguments): Value {
        val value = arguments.value(0)
        if (value is Value.FlagSeries || value is Value.Flag) {
            throw ScriptError(
                "plot یک سری عددی می‌خواهد. برای شرط از marker استفاده کنید",
                node.line,
                node.column,
            )
        }
        val line = interpreter.numberLine(value, node)
        // Numbered from the plots already added, so an untitled script still gets a legend that
        // tells its lines apart. Persian digits: this is a count in prose, not a market figure.
        val title = if (arguments.has("title")) {
            arguments.textOf(arguments.named("title"))
        } else {
            "خط " + (interpreter.plotCount + 1).toPersianDigits()
        }
        val colour = if (arguments.has("color")) arguments.colourOf(arguments.named("color")) else 0xFFD8A848
        val width = if (arguments.has("width")) arguments.constantOf(arguments.named("width"), "ضخامت") else 1.4
        val dashed = arguments.has("dashed") && arguments.flagOf(arguments.named("dashed"))
        val pane = if (arguments.has("pane")) {
            arguments.textOf(arguments.named("pane")) != "price"
        } else {
            !overlaysPrice(interpreter, line)
        }
        interpreter.addPlot(
            ScriptPlot(
                title = title,
                values = line,
                colour = colour,
                widthDp = width.toFloat().coerceIn(0.5f, 6f),
                ownPane = pane,
                dashed = dashed,
            ),
            node,
        )
        return Value.NumberSeries(line)
    }

    /**
     * Whether a plotted series belongs over the price rather than in its own pane.
     *
     * Decided by **measurement**, not by the title: a series whose values sit inside the price's own
     * range is a moving average or a band edge, and one that does not is an oscillator. Guessing
     * from the name would put anything a reader called `myRSI` in the wrong place, and an RSI drawn
     * over the candles flattens the price axis to a line and makes the whole chart unreadable.
     *
     * The measure is the *typical distance from the close on the same bar*, not an overlap of the
     * two ranges. Ranges overlap by accident — an RSI reading 0–100 sits neatly inside a price that
     * happens to trade between 100 and 160 — while a series that genuinely rides the price stays
     * near it bar by bar. A quarter of the price's own range is the line: a moving average or a
     * Bollinger edge is far inside it, an oscillator far outside.
     */
    private fun overlaysPrice(interpreter: Interpreter, line: Line): Boolean {
        val priceExtent = Line.of(interpreter.barCount) { interpreter.candles.close[it] }.extent() ?: return true
        val (priceLow, priceHigh) = priceExtent
        // A flat chart has no range to measure against, so fall back to the price's own magnitude.
        val scale = (priceHigh - priceLow).takeIf { it > 0.0 } ?: abs(priceHigh).takeIf { it > 0.0 } ?: return true
        val distances = ArrayList<Double>(interpreter.barCount)
        for (index in 0 until interpreter.barCount) {
            val value = line[index] ?: continue
            distances += abs(value - interpreter.candles.close[index])
        }
        if (distances.isEmpty()) return true
        distances.sort()
        val typical = distances[distances.size / 2]
        return typical <= scale * OVERLAY_DISTANCE
    }

    private fun hline(interpreter: Interpreter, node: Call, arguments: Arguments): Value {
        val price = arguments.constant(0, "قیمت خط")
        val title = if (arguments.has("title")) arguments.textOf(arguments.named("title")) else null
        val colour = if (arguments.has("color")) arguments.colourOf(arguments.named("color")) else 0xFF848E9C
        val pane = if (arguments.has("pane")) arguments.textOf(arguments.named("pane")) != "price" else null
        val ownPane = pane ?: run {
            val extent = Line.of(interpreter.barCount) { interpreter.candles.close[it] }.extent()
            extent == null || price < extent.first * 0.5 || price > extent.second * 1.5
        }
        interpreter.addLevel(ScriptLevel(price, title, colour, ownPane))
        return Value.Num(price)
    }

    private fun marker(interpreter: Interpreter, node: Call, arguments: Arguments): Value {
        val flags = interpreter.flagLine(arguments.value(0), node)
        val title = if (arguments.has("title")) arguments.textOf(arguments.named("title")) else "نشانه"
        val style = when (if (arguments.has("style")) arguments.textOf(arguments.named("style")) else "circle") {
            "up" -> ScriptMarkerStyle.ARROW_UP
            "down" -> ScriptMarkerStyle.ARROW_DOWN
            else -> ScriptMarkerStyle.CIRCLE
        }
        val colour = if (arguments.has("color")) {
            arguments.colourOf(arguments.named("color"))
        } else {
            when (style) {
                ScriptMarkerStyle.ARROW_UP -> 0xFF00B15C
                ScriptMarkerStyle.ARROW_DOWN -> 0xFFF6465D
                ScriptMarkerStyle.CIRCLE -> 0xFF848E9C
            }
        }
        val bars = (0 until interpreter.barCount).filter { flags.flagAt(it) }
        interpreter.addMarker(ScriptMarker(title, bars, style, colour))
        return Value.Num(bars.size.toDouble())
    }

    /**
     * The trade idea, taken from the **last** bar the condition held on.
     *
     * Not every historical firing: this is what a reader might act on now, and offering a setup
     * that fired three weeks ago is offering something that has already played out. The markers
     * carry the history.
     */
    private fun signal(interpreter: Interpreter, node: Call, arguments: Arguments): Value {
        val flags = interpreter.flagLine(arguments.value(0), node)
        val buy = if (arguments.has("buy")) arguments.flagOf(arguments.named("buy")) else true
        val entry = interpreter.numberLine(arguments.namedOrPositional("entry", 1), node)
        val stop = interpreter.numberLine(arguments.namedOrPositional("stop", 2), node)
        val target = if (arguments.has("target")) {
            interpreter.numberLine(arguments.named("target"), node)
        } else {
            null
        }

        val bar = (interpreter.barCount - 1 downTo 0).firstOrNull { flags.flagAt(it) }
            ?: return Value.Flag(false)
        val entryPrice = entry[bar] ?: return Value.Flag(false)
        val stopPrice = stop[bar] ?: return Value.Flag(false)
        // A stop on the wrong side of entry is not a setup, it is a mistake worth naming: a long
        // whose stop is above its entry would render as a negative risk and a nonsense R:R.
        if (buy && stopPrice >= entryPrice) {
            throw ScriptError("در خرید، حد ضرر باید پایین‌تر از ورود باشد", node.line, node.column)
        }
        if (!buy && stopPrice <= entryPrice) {
            throw ScriptError("در فروش، حد ضرر باید بالاتر از ورود باشد", node.line, node.column)
        }
        interpreter.setSetup(ScriptSetup(buy, entryPrice, stopPrice, target?.get(bar), bar))
        return Value.Flag(true)
    }
}

/**
 * One call's arguments, positional and named together.
 *
 * Named arguments may appear in any order and positional ones fill from the left, which is the rule
 * every reader already expects. Reading an argument that was not supplied and has no default is an
 * error naming the function, not a null somewhere later.
 */
internal class Arguments(private val interpreter: Interpreter, private val node: Call) {

    private val positional = node.arguments.filter { it.name == null }.map { it.value }
    private val byName = node.arguments.filter { it.name != null }.associate { it.name!! to it.value }

    val size: Int get() = positional.size

    fun has(name: String): Boolean = name in byName

    fun named(name: String): Value = interpreter.let { evaluated(byName.getValue(name)) }

    fun namedOrPositional(name: String, index: Int): Value =
        if (has(name)) named(name) else value(index)

    fun value(index: Int): Value {
        if (index >= positional.size) {
            throw ScriptError(
                "«${node.qualified}» به ورودی ${index + 1} نیاز دارد",
                node.line,
                node.column,
            )
        }
        return evaluated(positional[index])
    }

    fun source(index: Int): Line = interpreter.numberLine(value(index), node)

    fun length(index: Int, default: Int? = null): Int {
        if (index >= positional.size) {
            default?.let { return it }
            throw ScriptError("«${node.qualified}» به طول دوره نیاز دارد", node.line, node.column)
        }
        return interpreter.period(value(index), node, "طول دوره")
    }

    fun constant(index: Int, what: String): Double = interpreter.scalar(value(index), node, what)

    fun constantOf(value: Value, what: String): Double = interpreter.scalar(value, node, what)

    fun text(index: Int): String = textOf(value(index))

    fun textOf(value: Value): String = when (value) {
        is Value.Text -> value.value
        is Value.Num -> com.coinepro.core.common.MarketNumberFormatter.priceAuto(value.value)
        is Value.Flag -> if (value.value) "درست" else "نادرست"
        else -> throw ScriptError("اینجا متن لازم است، نه ${value.typeName}", node.line, node.column)
    }

    fun colourOf(value: Value): Long = when (value) {
        is Value.Colour -> value.argb
        else -> throw ScriptError("اینجا رنگ لازم است — مثلاً color.gold", node.line, node.column)
    }

    fun flagOf(value: Value): Boolean = when (value) {
        is Value.Flag -> value.value
        is Value.Num -> value.value != 0.0
        else -> throw ScriptError("اینجا درست/نادرست لازم است", node.line, node.column)
    }

    private fun evaluated(expression: Expr): Value = evaluator(expression)

    /** Set by the interpreter before any call is dispatched. */
    private val evaluator: (Expr) -> Value = { expression -> interpreter.evaluateArgument(expression) }
}
