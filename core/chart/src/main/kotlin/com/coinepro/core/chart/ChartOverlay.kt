package com.coinepro.core.chart

/**
 * A line drawn over the price, with its own colour.
 *
 * Overlays share the price axis — a moving average, a Bollinger edge, a SuperTrend stop. Anything
 * on a different scale (RSI, MACD, volume) belongs in its own pane and is not one of these.
 */
data class ChartLine(
    val values: Line,
    /** ARGB, so `core:chart` does not have to know about Compose's Color to describe itself. */
    val colour: Long,
    val widthDp: Float = 1.2f,
    /** A label for the legend, e.g. "EMA 20". */
    val label: String? = null,
)

/**
 * A trade setup drawn on the chart: where to get in, where to be wrong, where to take profit.
 *
 * This is the one overlay that is not an indicator, and it is the reason the chart exists in this
 * product rather than being a nice extra. Both the AI signals and — once the terminal lands —
 * namascript's `riskreward()` produce exactly this shape, so they render through one path.
 *
 * The zones are drawn as filled bands rather than lines because the distance between entry and stop
 * *is* the information: a reader has to see the risk as an area against the reward, not read two
 * numbers and do the subtraction.
 */
data class SignalOverlay(
    val entry: Double,
    val stopLoss: Double?,
    /** In order. Several targets are ordinary and each gets its own line. */
    val takeProfits: List<Double> = emptyList(),
    /** Long or short. Decides which side of entry is the loss. */
    val isLong: Boolean,
    /** When the setup was issued, so the drawing can start at that bar rather than at the edge. */
    val issuedAt: Long? = null,
) {
    /**
     * Reward over risk, or null when there is no stop or no target to measure between.
     *
     * Null rather than a default: a setup without a stop has *unbounded* risk, and printing any
     * ratio for it would be the most dangerous number on the screen.
     */
    /**
     * Every price the setup names, so the chart can keep them all on screen.
     *
     * A target is by definition somewhere price has not been, so a range taken from the bars alone
     * puts it off the canvas — and a band that runs off the top is the least useful possible
     * drawing of "take profit here".
     */
    fun levels(): List<Double> = buildList {
        add(entry)
        stopLoss?.let(::add)
        addAll(takeProfits)
    }

    val riskReward: Double?
        get() {
            val stop = stopLoss ?: return null
            val target = takeProfits.firstOrNull() ?: return null
            val risk = if (isLong) entry - stop else stop - entry
            val reward = if (isLong) target - entry else entry - target
            return if (risk > 0 && reward > 0) reward / risk else null
        }
}

/** Everything drawn on one chart, beyond the bars themselves. */
data class ChartDecoration(
    val overlays: List<ChartLine> = emptyList(),
    val signal: SignalOverlay? = null,
    /** Whether the volume pane is drawn. Hidden when the feed reports none — see [CandleSeries]. */
    val showVolume: Boolean = true,
    /** Whether the price grid and its labels are drawn. Off for a thumbnail. */
    val showAxes: Boolean = true,
)

/** Where the crosshair is, in chart space. Null when nobody is touching the chart. */
data class Crosshair(val index: Int, val price: Double)
