package com.coinepro.core.chart

/**
 * The data window: every figure on the chart at one bar, in a table.
 *
 * Pro-Chart's terminal had it as a floating panel (`DataWindow` in `ChartOverlays.jsx`, TradingView's
 * «Data Window»): the bar under the crosshair — or the newest bar when there is no crosshair — with
 * its open, high, low and close, the change from the previous close in price and in percent, the
 * volume, and then one row per study on the chart with its value there. The legend already carries
 * the price line; what it cannot carry is *every* study's value at once without covering the plot,
 * which is what this is for (5.14.0).
 *
 * Pure data, so the panel is a table and nothing more, and the rules are tested once here.
 */
object DataWindow {

    /** One study's row: its label, its colour, and its value on each of its lines at the bar. */
    data class Row(val label: String, val colour: Long, val values: List<Double?>)

    data class Reading(
        val index: Int,
        val time: Long,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        /** Null where the feed carries no volume, so the panel leaves the row out. */
        val volume: Double?,
        /**
         * Close against the previous bar's close, the way TradingView's window and status line read
         * it; against this bar's open for the first bar, which has no previous close.
         */
        val change: Double,
        /** [change] as a percentage of what it was measured from; null when that was zero. */
        val changePercent: Double?,
        val rows: List<Row>,
    ) {
        val up: Boolean get() = close >= open
    }

    /**
     * The reading at [index] (the crosshair's bar), or at the newest bar when [index] is null or off
     * the series — the terminal shows the last candle rather than an empty panel. Null only for an
     * empty series.
     *
     * Overlay lines are one row each, by their legend label; a line with no label is a band edge or
     * a fill the legend does not name either, and is left out. A pane is one row with all of its
     * lines, under its title — the MACD's three values on one line, as the terminal joined them, the
     * histogram last.
     */
    fun at(
        series: CandleSeries,
        index: Int?,
        overlays: List<ChartLine> = emptyList(),
        panes: List<ChartPane> = emptyList(),
    ): Reading? {
        if (series.isEmpty) return null
        val i = if (index != null && index in 0 until series.size) index else series.size - 1
        val base = if (i > 0) series.close[i - 1] else series.open[i]
        val change = series.close[i] - base
        val rows = ArrayList<Row>()
        for (line in overlays) {
            val label = line.label ?: continue
            rows += Row(label, line.colour, listOf(line.values.valueAt(i)))
        }
        for (pane in panes) {
            // The histogram last, where MACD's legend puts it.
            val lines = pane.lines.filter { it.widthDp > 0f } + listOfNotNull(pane.histogram)
            if (lines.isEmpty()) continue
            rows += Row(pane.title, lines.first().colour, lines.map { it.values.valueAt(i) })
        }
        return Reading(
            index = i,
            time = series.time[i],
            open = series.open[i],
            high = series.high[i],
            low = series.low[i],
            close = series.close[i],
            volume = if (series.hasVolume) series.volume[i] else null,
            change = change,
            changePercent = if (base != 0.0) change / base * 100 else null,
            rows = rows,
        )
    }

    /** A line computed on a different length (a comparison, a higher timeframe) reads as empty. */
    private fun Line.valueAt(index: Int): Double? = if (index < size) this[index]?.takeIf { it.isFinite() } else null
}
