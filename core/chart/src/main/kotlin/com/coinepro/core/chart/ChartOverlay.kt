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
    /**
     * Drawn as a dashed line.
     *
     * For the pivot levels, where it carries meaning rather than decoration: the pivot itself is
     * solid and the six levels around it are dashed, so a glance says which line is the reference
     * and which are derived from it.
     */
    val dashed: Boolean = false,
    /**
     * Join across the gaps instead of breaking at them.
     *
     * Off by default and it must stay off by default: for an indicator a gap is a warm-up or a
     * missing bar, and bridging it draws a straight line through data that does not exist. The
     * zigzag is the opposite case — every bar between two turns is deliberately absent and the join
     * across them *is* the study.
     */
    val connectNulls: Boolean = false,
)

/**
 * A horizontal line at one price, with a label.
 *
 * Not a [ChartLine] with the same value in every slot. A level has no time dimension at all — it is
 * true across the whole plot and past its right edge, into bars that have not printed — and one
 * value per bar is both the wrong shape and, on a thousand-bar series, a thousand times the memory
 * for one number.
 */
data class PriceLevel(
    val price: Double,
    val colour: Long,
    /** Drawn at the level's left end. Null draws the line and nothing else. */
    val label: String? = null,
    /** Whether the line continues past the last bar, into the space a trade would play out in. */
    val extendRight: Boolean = true,
)

/** What a marker looks like. */
enum class MarkerGlyph { ARROW_UP, ARROW_DOWN, CIRCLE }

/**
 * A mark on one bar — a swing point, a fractal, a zigzag turn.
 *
 * [above] places it clear of the bar's high rather than at [price] exactly, because a marker drawn
 * *on* the high is a marker that hides the high.
 */
data class ChartMarker(
    val time: Long,
    val price: Double,
    val above: Boolean,
    val colour: Long,
    val glyph: MarkerGlyph,
    val text: String? = null,
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
    /**
     * What to write beside each line — entry, stop, then one per take-profit in order.
     *
     * Supplied by the caller rather than built here, because `core:chart` has no string resources
     * and the words are Persian on every screen that draws this. Empty draws the lines bare, which
     * is what a thumbnail wants. Three green dashes at three different prices are three targets
     * only to a reader who already knows that; the labels are what make the picture readable
     * without the card underneath it.
     */
    val entryLabel: String? = null,
    val stopLabel: String? = null,
    val targetLabels: List<String> = emptyList(),
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
    /**
     * What the reader has drawn, in the order they drew it.
     *
     * Order is z-order: the last one is on top, and is the one a tap on an overlap selects. That is
     * the same rule the web terminal's object tree uses, so "bring to front" means the same thing
     * in both.
     */
    val drawings: List<Drawing> = emptyList(),
    /** Which drawing shows its handles. Only one at a time — see [drawDrawing]. */
    val selectedDrawingId: Long? = null,
    /** Horizontal levels: pivots, auto-Fibonacci, support and resistance, supply and demand. */
    val levels: List<PriceLevel> = emptyList(),
    /** Per-bar marks: swing points, fractals, zigzag turns. */
    val markers: List<ChartMarker> = emptyList(),
    /** Whether the volume pane is drawn. Hidden when the feed reports none — see [CandleSeries]. */
    val showVolume: Boolean = true,
    /** Whether the price grid and its labels are drawn. Off for a thumbnail. */
    val showAxes: Boolean = true,
    /**
     * Whether the dates along the bottom are drawn.
     *
     * Separate from [showAxes] for one honest reason: some feeds send bars with no timestamps at
     * all. CoinePro-FX's AI evidence is twelve candles of open/high/low/close and nothing else, and
     * a time axis under those would be printing dates the server never sent. The prices are real,
     * so the price axis stays; the dates go.
     */
    val showTimeAxis: Boolean = true,
)

/** Where the crosshair is, in chart space. Null when nobody is touching the chart. */
data class Crosshair(val index: Int, val price: Double)
