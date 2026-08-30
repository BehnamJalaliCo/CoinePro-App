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
    /**
     * The price-row histogram this line is the summary of — item 54.
     *
     * Only «پروفایل حجم» sets it, and it is the way that study gets drawn at all. A profile is
     * measured *across* the price axis — one bar per price row, growing sideways — and this type is
     * one value per *time* bar, so for a long while the indicator resolved to three flat lines and
     * the histogram [ChartCatalog.volumeProfileFor] had already computed was thrown away at the
     * call site. Three lines is not a profile; it is the caption under one.
     *
     * Carried on the line rather than on [ChartDecoration] because that is the shape the feature
     * layer already passes through untouched: `overlayFor` builds it, `decoration.overlays` moves
     * it, the canvas draws it. A field on the decoration would have needed a second hand-off in a
     * file that has no idea this study exists, which is how the rows came to be discarded the first
     * time.
     *
     * It hangs off the point-of-control line specifically — the one line that *is* the profile's
     * headline — so hiding that legend row hides the bars with it.
     */
    val profile: VolumeProfile? = null,
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
 *
 * ### The bands are anchored in time
 *
 * They start at the bar named by [issuedAt] and run to [closedAt] or to the live edge — never
 * further left than the entry bar. They used to run the full width of the plot, and a reader looking
 * at a chart shaded green above the entry and red below it from the first visible bar reads it as
 * "this whole chart is a long position", which was false on every chart that drew one. See
 * [setupSpan] for the rule and `SetupZoneTest` for what it guarantees.
 */
data class SignalOverlay(
    val entry: Double,
    val stopLoss: Double?,
    /** In order. Several targets are ordinary and each gets its own line. */
    val takeProfits: List<Double> = emptyList(),
    /** Long or short. Decides which side of entry is the loss. */
    val isLong: Boolean,
    /**
     * When the setup was issued, so the drawing starts at that bar rather than at the plot's edge.
     *
     * This is the anchor the whole zone hangs off — see [setupSpan]. Without it the renderer draws
     * the levels and no shading at all, which is the honest picture of a setup whose start nobody
     * recorded: the prices are true, the claim that they were true across the visible history is
     * not.
     *
     * A moment rather than a bar index, because the caller has a signal's `createdAt` and does not
     * have this chart's bar grid — and because the two must not drift when the reader switches the
     * timeframe under a setup that is already drawn.
     */
    val issuedAt: Long? = null,
    /**
     * When it closed, or null while it is still open.
     *
     * An open position's zone runs to the right-hand edge of the plot, blank slots included: it is
     * still open, and the air at the live edge is the near future it is open into. A closed one
     * stops at the bar it closed on. Painting a closed setup all the way to the edge is the same
     * false claim as painting an open one all the way to the left, in the other direction.
     */
    val closedAt: Long? = null,
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

    /**
     * Reward over risk, or null when there is no stop or no target to measure between.
     *
     * Null rather than a default: a setup without a stop has *unbounded* risk, and printing any
     * ratio for it would be the most dangerous number on the screen.
     */
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
/**
 * A chart's own palette, overriding the theme's.
 *
 * ARGB longs rather than Compose `Color` because these come out of storage, and the storage layer
 * deliberately knows nothing about Compose. Convert at the edge with `Color(value.toULong() shl 32)`.
 *
 * Only the seven colours a reader can actually distinguish on a chart are here. A template with
 * thirty knobs is a template nobody finishes filling in, and every extra one is another way for two
 * saved templates to differ invisibly.
 */
data class ChartColours(
    /** The rising candle body, border and wick. */
    val up: Long,
    /** The falling candle body, border and wick. */
    val down: Long,
    /** Both grid directions. */
    val grid: Long,
    /** The pane behind everything. */
    val background: Long,
    /** Axis labels and the legend. */
    val text: Long,
    /** The crosshair line, drawn dashed at partial alpha over this. */
    val crosshair: Long,
)

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
    /**
     * Other instruments laid over this one, already aligned to this chart's bar grid.
     *
     * Aligned, not merged: [Comparison.align] carries the compared feed's last known value forward
     * across a bar this market has and that one does not, and leaves NaN before it starts. The base
     * series is never trimmed to match, because a comparison must not change the geometry of the
     * chart it was added to.
     *
     * At most [MAX_COMPARISONS] of them, and that cap is a legibility limit rather than a technical
     * one: past four the colours stop being distinguishable at phone width, and a chart nobody can
     * read is not a feature.
     */
    val comparisons: List<ComparisonSeries> = emptyList(),
    /**
     * How the comparisons are made comparable.
     *
     * Percent by default, because two instruments at different price magnitudes cannot share an
     * axis any other way — gold at 2,400 and a coin at 0.08 on one linear scale is one flat line
     * and one invisible one. Ratio answers a different question entirely and gets its own scale.
     */
    val comparisonBasis: ComparisonBasis = ComparisonBasis.PERCENT,
    /**
     * The colours the canvas paints with, or null for the theme's own.
     *
     * A stored colour template is worthless until it reaches the renderer, and until this existed
     * the app could save one, list it and restore it while the chart went on painting in the theme
     * palette — a setting that appears to work and does nothing, which is worse than one that is
     * absent. Null keeps the existing behaviour exactly, so every call site that does not care is
     * unaffected.
     */
    val colours: ChartColours? = null,
    /** Horizontal levels: pivots, auto-Fibonacci, support and resistance, supply and demand. */
    val levels: List<PriceLevel> = emptyList(),
    /** Per-bar marks: swing points, fractals, zigzag turns. */
    val markers: List<ChartMarker> = emptyList(),
    /**
     * What happened, on the time axis: releases, earnings, headlines.
     *
     * Already placed by [ChartEvents.place] rather than raw events, and that is the boundary: bar
     * bucketing needs the series and the reader's own kind filter, both of which the caller has and
     * the renderer does not. The renderer draws one glyph per mark in the time-axis strip — never
     * over the candles — and drops any mark outside the visible window rather than pinning it to
     * the edge, because a glyph at the edge claims something happened at a time it did not.
     */
    val events: List<EventMark> = emptyList(),
    /**
     * Strips below the price, each on its own scale — oscillators, and a script's own-pane plots.
     *
     * Order is top to bottom. Empty is the common case and costs nothing.
     */
    val panes: List<ChartPane> = emptyList(),
    /** Whether the volume pane is drawn. Hidden when the feed reports none — see [CandleSeries]. */
    val showVolume: Boolean = true,
    /** Whether the price grid and its labels are drawn. Off for a thumbnail. */
    val showAxes: Boolean = true,
    /**
     * The dashed line at the last close, with its price tagged against the axis.
     *
     * On by default and off for a thumbnail. It is the one number a reader looks for without being
     * asked, and a header showing it says nothing about *where* on the visible scale the market is.
     */
    val showLastPrice: Boolean = true,
    /**
     * The corner readout: the bar's open, high, low and close, then each overlay's own name in its
     * own colour.
     *
     * Without it a chart with four lines on it is four anonymous curves. With the crosshair down it
     * follows the crosshair's bar; without one it reads the last bar.
     */
    val showLegend: Boolean = true,
    /**
     * Whether the dates along the bottom are drawn.
     *
     * Separate from [showAxes] for one honest reason: some feeds send bars with no timestamps at
     * all. CoinePro-FX's AI evidence is twelve candles of open/high/low/close and nothing else, and
     * a time axis under those would be printing dates the server never sent. The prices are real,
     * so the price axis stays; the dates go.
     */
    val showTimeAxis: Boolean = true,
    /**
     * How long until the bar being drawn closes, tagged under the live price.
     *
     * Off by default and on wherever the chart is looking at a live feed. It is the smallest
     * feature in this file and one of the most asked for: a trader reading a 15-minute chart is
     * waiting for a *close*, not for a price, and without it the only way to know whether the
     * candle in front of you has ten seconds or ten minutes left is to do the arithmetic yourself
     * from the clock.
     *
     * Ignored while the reader has panned back — a countdown over history is a countdown to
     * nothing — and ignored on a series whose bars carry no timestamps.
     */
    val showCountdown: Boolean = false,
    /**
     * How much taller or shorter the reader has made the indicator panes, as a factor.
     *
     * One is what the panes ask for themselves. Above one they take more of the canvas and the
     * candles take less; below one the reverse.
     *
     * It exists because a pane's own [ChartPane.heightRatio] is a designer's guess at how much of
     * the picture an oscillator deserves, and the right answer depends entirely on what the reader
     * is doing: somebody reading divergence wants the RSI half the screen, and somebody who left
     * it switched on wants it out of the way. The complaint — "the indicator window can't be
     * resized, it used to be draggable" — appears in reviews of every app in this category,
     * Persian ones included.
     */
    val paneScale: Float = 1f,
    /**
     * The line at the previous session's close.
     *
     * ### Why a chart needs it
     *
     * Because "how much is it up today" is a question about a *distance on the chart*, and without
     * this line there is nothing on the plot to measure that distance from. The header carries the
     * session change as a number, and a number is the one form of that answer a chart is bad at:
     * what a trader reads off the picture is whether the candles are above or below where the day
     * started, by how much, and whether the current bar is the one that crossed it. Every terminal
     * draws it, and until now this one drew nothing at all — the price plot had no reference for
     * the day whatsoever, which is a large part of why it reads as a picture of prices rather than
     * as a trading chart.
     *
     * ### Intraday only, and that is not a limitation
     *
     * On a daily chart or coarser the previous close is the bar immediately to the left, so a line
     * at it is a line through the candle next door: it says nothing the picture does not already
     * say, and it adds a rule across a chart that has no session to divide. The renderer decides
     * this from the bars' own spacing rather than from a timeframe it was told, for the same reason
     * the countdown does — see `drawCountdown`.
     *
     * On by default, and drawn only where the live price is: a thumbnail switches both off
     * together, which is the one bit that already separates a chart from a picture of one.
     */
    val showPreviousClose: Boolean = true,
)

/** Where the crosshair is, in chart space. Null when nobody is touching the chart. */
data class Crosshair(val index: Int, val price: Double)

/**
 * A strip below the price with its own vertical scale.
 *
 * The reason this type exists rather than the lines being folded into [ChartDecoration.overlays]:
 * an RSI reads 0–100 and gold reads 2,600, and drawing them on one axis collapses the price to a
 * flat line. A pane is the promise that a line inside it is measured against *its own* extremes and
 * nothing else on the chart.
 *
 * [heightRatio] is a share of the whole canvas rather than a height in dp, so a phone and a tablet
 * give an oscillator the same proportion of the picture. The renderer clamps the total: panes never
 * take so much that the candles stop being the subject.
 */
data class ChartPane(
    /** Written in the pane's top-left corner, e.g. "RSI 14". */
    val title: String,
    val lines: List<ChartLine> = emptyList(),
    /**
     * Horizontal references inside the pane — RSI's 30 and 70, MACD's zero.
     *
     * [PriceLevel.price] is read on the pane's own scale here, not the price's. It is the same
     * shape because it is drawn the same way, and a second near-identical type would be worse.
     */
    val levels: List<PriceLevel> = emptyList(),
    /**
     * Drawn as columns from zero rather than as a line — MACD's histogram, OBV's bars.
     *
     * Separate from [lines] because a histogram is not a line with a different stroke: it is
     * anchored to zero, and its colour changes with its sign.
     */
    val histogram: ChartLine? = null,
    val heightRatio: Float = 0.18f,
)
