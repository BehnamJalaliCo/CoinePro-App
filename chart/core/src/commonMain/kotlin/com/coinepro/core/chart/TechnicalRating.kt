package com.coinepro.core.chart

/**
 * The technical rating, TradingView's way: fifteen moving averages and eleven oscillators each vote
 * buy, sell or neutral, and the two halves are averaged into a score from −1 (strong sell) to +1
 * (strong buy). A port of `techRating.js` in Pro-Chart's terminal (5.13.0), with its rules:
 *
 * * **Averages** — SMA and EMA at 10, 20, 30, 50, 100 and 200, Hull 9, the Ichimoku base line and,
 *   where the feed has volume, VWMA 20. Price above the average is a buy, below is a sell.
 * * **Oscillators** — RSI 14 (below 30 buy, above 70 sell), Stochastic %K (20/80), CCI 20 (±100),
 *   MACD against its signal, Williams %R (−80/−20), Awesome Oscillator against zero, Stochastic RSI
 *   %K (20/80), Momentum 10 rising or falling, Ultimate Oscillator (70/30 the other way round, as
 *   there), an ADX(14) +DI/−DI cross with ADX above 20, and Elder's bull/bear power.
 *
 * Computed for every bar, not only the last, so it draws as a line and a reader can see when the
 * verdict changed. Each input is computed once over the whole series; a bar's vote is a lookup.
 */
object TechnicalRating {

    /** The five verdicts, with their thresholds on the score. */
    enum class Verdict(val short: String) { STRONG_SELL("Strong Sell"), SELL("Sell"), NEUTRAL("Neutral"), BUY("Buy"), STRONG_BUY("Strong Buy") }

    fun verdictOf(score: Double): Verdict = when {
        score > 0.5 -> Verdict.STRONG_BUY
        score > 0.1 -> Verdict.BUY
        score < -0.5 -> Verdict.STRONG_SELL
        score < -0.1 -> Verdict.SELL
        else -> Verdict.NEUTRAL
    }

    /** The overall score and its two halves, bar by bar; `NaN` before sixty bars. */
    data class Ratings(val overall: DoubleArray, val averages: DoubleArray, val oscillators: DoubleArray)

    fun of(series: CandleSeries): Ratings {
        val n = series.size
        val close = series.close
        val high = series.high
        val low = series.low
        val overall = DoubleArray(n) { Double.NaN }
        val maScore = DoubleArray(n) { Double.NaN }
        val oscScore = DoubleArray(n) { Double.NaN }
        if (n < MIN_BARS) return Ratings(overall, maScore, oscScore)

        val averages = ArrayList<Line>()
        for (p in listOf(10, 20, 30, 50, 100, 200)) {
            averages += Indicators.sma(close, p)
            averages += Indicators.ema(close, p)
        }
        averages += Indicators.hma(close, 9)
        val vwma = if (series.hasVolume) IndicatorsExtB.vwma(close, series.volume, 20) else null

        val rsi = Indicators.rsi(close, 14)
        val stochK = Indicators.stochastic(high, low, close, 14, 3).k
        val cci = Indicators.cci(high, low, close, 20)
        val macd = Indicators.macd(close, 12, 26, 9)
        val williams = Indicators.williamsR(high, low, close, 14)
        val ao = IndicatorsExtB.awesomeOscillator(high, low)
        val stochRsiK = IndicatorsExtB.stochasticRsi(close, 14, 14, 3, 3).k
        val uo = IndicatorsExt.ultimateOscillator(high, low, close, 7, 14, 28)
        val dmi = IndicatorsExtB.directionalMovement(high, low, close, 14)
        val elder = IndicatorsExtD.elderRay(high, low, close, 13)

        for (i in MIN_BARS - 1 until n) {
            val price = close[i]
            var maBuy = 0
            var maSell = 0
            var maTotal = 0
            fun rate(value: Double?) {
                if (value == null || !value.isFinite()) return
                maTotal++
                if (price > value) maBuy++ else if (price < value) maSell++
            }
            // An average is only counted once it has had its own length plus two bars to settle,
            // which is where the original starts counting it.
            val periods = listOf(10, 10, 20, 20, 30, 30, 50, 50, 100, 100, 200, 200)
            for ((k, line) in averages.withIndex()) {
                if (k < periods.size && i + 1 <= periods[k] + 2) continue
                rate(line[i])
            }
            if (i >= 25) {
                var hh = Double.NEGATIVE_INFINITY
                var ll = Double.POSITIVE_INFINITY
                for (j in i - 25..i) {
                    if (high[j] > hh) hh = high[j]
                    if (low[j] < ll) ll = low[j]
                }
                rate((hh + ll) / 2)
            }
            if (vwma != null && i + 1 > 22) rate(vwma[i])
            val ma = if (maTotal > 0) (maBuy - maSell).toDouble() / maTotal else 0.0

            var oBuy = 0
            var oSell = 0
            var oTotal = 0
            fun vote(signal: Int) {
                oTotal++
                if (signal > 0) oBuy++ else if (signal < 0) oSell++
            }
            rsi[i]?.let { vote(if (it < 30) 1 else if (it > 70) -1 else 0) }
            stochK[i]?.let { vote(if (it < 20) 1 else if (it > 80) -1 else 0) }
            cci[i]?.let { vote(if (it < -100) 1 else if (it > 100) -1 else 0) }
            val m = macd.macd[i]
            val s = macd.signal[i]
            if (m != null && s != null) vote(if (m > s) 1 else if (m < s) -1 else 0)
            williams[i]?.let { vote(if (it < -80) 1 else if (it > -20) -1 else 0) }
            ao[i].takeIf(Double::isFinite)?.let { vote(if (it > 0) 1 else if (it < 0) -1 else 0) }
            stochRsiK[i].takeIf(Double::isFinite)?.let { vote(if (it < 20) 1 else if (it > 80) -1 else 0) }
            if (i >= 11) {
                val now = close[i] - close[i - 10]
                val before = close[i - 1] - close[i - 11]
                vote(if (now > before) 1 else if (now < before) -1 else 0)
            }
            uo[i]?.let { vote(if (it > 70) 1 else if (it < 30) -1 else 0) }
            if (i >= 1) vote(adxCross(dmi, i))
            if (i >= 1) vote(bullBear(elder, i))
            val osc = if (oTotal > 0) (oBuy - oSell).toDouble() / oTotal else 0.0

            maScore[i] = ma
            oscScore[i] = osc
            overall[i] = (ma + osc) / 2
        }
        return Ratings(overall, maScore, oscScore)
    }

    private fun adxCross(dmi: DirectionalMovementSeries, i: Int): Int {
        val adx = dmi.adx[i]
        val plus = dmi.plusDi[i]
        val minus = dmi.minusDi[i]
        val plusBefore = dmi.plusDi[i - 1]
        val minusBefore = dmi.minusDi[i - 1]
        if (!listOf(adx, plus, minus, plusBefore, minusBefore).all(Double::isFinite)) return 0
        if (adx > 20 && plusBefore < minusBefore && plus > minus) return 1
        if (adx > 20 && plusBefore > minusBefore && plus < minus) return -1
        return 0
    }

    private fun bullBear(elder: ElderRaySeries, i: Int): Int {
        val bull = elder.bull[i]
        val bullBefore = elder.bull[i - 1]
        val bear = elder.bear[i]
        val bearBefore = elder.bear[i - 1]
        if (!listOf(bull, bullBefore, bear, bearBefore).all(Double::isFinite)) return 0
        if (bear < 0 && bear > bearBefore && bull > bullBefore) return 1
        if (bull > 0 && bull < bullBefore && bear < bearBefore) return -1
        return 0
    }

    /** The original returns nothing under sixty bars; so does this, bar by bar. */
    const val MIN_BARS = 60
}
