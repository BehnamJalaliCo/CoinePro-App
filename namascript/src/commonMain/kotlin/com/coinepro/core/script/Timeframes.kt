package com.coinepro.core.script

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries

/**
 * Other timeframes, built from the chart's own bars — what `request.security` runs on.
 *
 * A NamaScript run has one series: the chart's. A coarser timeframe is that series bucketed —
 * an H4 bar is the four H1 bars whose times fall in the same four-hour slot: the first's open,
 * the highest high, the lowest low, the last's close, the summed volume. A finer timeframe
 * cannot be made from coarser bars and is refused (E210), as is a timeframe that is not a whole
 * multiple of the chart's.
 *
 * The mapping back is **confirmed**: a base bar takes the value of the last *completed* higher
 * bar, and only the base bar that closes a higher bar sees that bar's own value. A script cannot
 * read the future of the higher timeframe from inside its bars, so what it draws on history is
 * what it would have drawn live — no repainting.
 */
internal object Timeframes {

    /** Seconds for a timeframe written Pine's way (`"240"`, `"D"`, `"W"`) or the app's (`"H4"`, `"D1"`, `"M15"`). */
    fun seconds(text: String): Long? {
        val t = text.trim().uppercase()
        if (t.isEmpty()) return null
        t.toLongOrNull()?.let { return it * 60 }
        // Pine's "4H", "1D", "1W" and the app's "H4", "D1", "M15": a unit letter with a count on
        // either side. A bare "M" is a month, Pine's way; "M5" is five minutes, the app's.
        val pine = PINE_FORM.matchEntire(t)
        val app = APP_FORM.matchEntire(t)
        val (unit, count) = when {
            pine != null -> pine.groupValues[2][0] to pine.groupValues[1].toLong()
            app != null -> app.groupValues[1][0] to (app.groupValues[2].toLongOrNull() ?: 1L)
            else -> return null
        }
        return when (unit) {
            'M' -> if (app != null && app.groupValues[2].isEmpty()) 30L * 86_400 else count * 60
            'H' -> count * 3_600
            'D' -> count * 86_400
            'W' -> count * 7 * 86_400
            else -> null
        }
    }

    private val PINE_FORM = Regex("""(\d+)([MHDW])""")
    private val APP_FORM = Regex("""([MHDW])(\d*)""")

    /** The chart's own bar length, from the most common spacing between consecutive bars. */
    fun baseSeconds(series: CandleSeries): Long {
        if (series.size < 2) return 0L
        val counts = HashMap<Long, Int>()
        val limit = minOf(series.size - 1, 500)
        for (index in 1..limit) {
            val delta = series.bars[index].t - series.bars[index - 1].t
            if (delta > 0) counts[delta] = (counts[delta] ?: 0) + 1
        }
        return counts.maxByOrNull { it.value }?.key ?: 0L
    }

    /** The coarser series and, for every base bar, the index of the higher bar it belongs to. */
    class Aggregation(val series: CandleSeries, val bucketOf: IntArray, val closesBucket: BooleanArray)

    fun aggregate(series: CandleSeries, seconds: Long): Aggregation {
        val bucketOf = IntArray(series.size)
        val closes = BooleanArray(series.size)
        val bars = ArrayList<Candle>()
        var currentSlot = Long.MIN_VALUE
        var open = 0.0; var high = 0.0; var low = 0.0; var close = 0.0; var volume = 0.0
        var hasVolume = false
        fun flush() {
            if (currentSlot != Long.MIN_VALUE) bars += Candle(currentSlot * seconds, open, high, low, close, if (hasVolume) volume else null)
        }
        for (index in 0 until series.size) {
            val bar = series.bars[index]
            val slot = bar.t.floorDiv(seconds)
            if (slot != currentSlot) {
                flush()
                currentSlot = slot
                open = bar.o; high = bar.h; low = bar.l; close = bar.c; volume = 0.0; hasVolume = false
            }
            high = maxOf(high, bar.h)
            low = minOf(low, bar.l)
            close = bar.c
            bar.v?.let { volume += it; hasVolume = true }
            bucketOf[index] = bars.size
        }
        flush()
        for (index in 0 until series.size) {
            closes[index] = index == series.size - 1 || bucketOf[index + 1] != bucketOf[index]
        }
        return Aggregation(CandleSeries(bars), bucketOf, closes)
    }
}
