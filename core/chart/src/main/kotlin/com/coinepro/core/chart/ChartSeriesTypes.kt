package com.coinepro.core.chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The seven chart types that were in the picker and drew as something else.
 *
 * ### What was wrong
 *
 * `BASELINE`, `HLC_AREA`, `STEP_LINE` and `LINE_MARKERS` all answered `ChartType.isLine`, so the
 * renderer sent them to the plain close polyline; `VOLUME_CANDLES`, `FOOTPRINT` and `TPO` fell
 * through to ordinary candles. Every one of them had an icon, a name and a help entry, and picking
 * «فوت‌پرینت» gave a reader candles. The arithmetic they needed was already written, unit-tested
 * and called by nothing outside those tests — `baselineSplit`, `stepLine`, `volumeWidths`,
 * `footprint`, `tpo`, `defaultBaseLevel`, `defaultRows`. This file is the half that was missing.
 *
 * ### Two of them refuse to draw, and that is the feature
 *
 * A footprint and a profile are tables of numbers inside a bar. Below a certain bar width they are
 * not a dense chart, they are grey mush with the numbers unreadable — and the reader cannot tell
 * mush from a rendering fault. So both fall back to candles below [FOOTPRINT_MIN_SLOT_DP], and both
 * fall back on a feed that reports no volume rather than drawing a grid of zeros, which is the same
 * rule the volume pane has always had.
 */

/**
 * The colour a single-line type reads as: the direction of the *visible window*.
 *
 * Shared by every line-shaped type so a baseline, a step line and an ordinary line all say up or
 * down the same way a candle chart does at a glance. Taken from the window rather than from the
 * series, for the same reason the price range is: the reader is asking about the bars in front of
 * them.
 */
internal fun directionColour(view: ChartViewport, palette: ChartPalette): Color {
    val first = view.series.close.getOrNull(view.firstVisible) ?: return palette.up
    val last = view.series.close.getOrNull(view.lastVisible) ?: return palette.up
    return if (last >= first) palette.up else palette.down
}

/**
 * The vertical ramp a filled area is painted with.
 *
 * TradingView's own fill is not flat: it is 0.28 at the line falling to 0.05 at the floor, which is
 * shallow enough that nobody reads it as a gradient and steep enough that the line reads as the
 * *edge* of a solid thing rather than as a stripe on a wash. This canvas used a flat `0.16` at both
 * ends, which is why its area charts looked printed rather than lit.
 *
 * `CoineProChart.kt` is on the motion-policy gate's gradient allow-list precisely for this — see
 * `scripts/quality/check-motion-policy.sh`, whose comment names "a chart's own area fill" as one of
 * the two kinds of gradient that earn their place. Nothing else in this file may grow one.
 */
internal fun areaBrush(colour: Color, topY: Float, bottomY: Float): Brush = Brush.verticalGradient(
    colors = listOf(colour.copy(alpha = AREA_ALPHA_TOP), colour.copy(alpha = AREA_ALPHA_BOTTOM)),
    startY = topY,
    endY = max(topY + 1f, bottomY),
)

/** The fill's alpha where it meets the line. See [areaBrush]. */
internal const val AREA_ALPHA_TOP = 0.28f

/** And where it meets the floor. */
internal const val AREA_ALPHA_BOTTOM = 0.05f

// ---------------------------------------------------------------------------- conflation

/**
 * Points merged when several of them land in the same sliver of a column.
 *
 * ### The problem it answers
 *
 * A line series emits one `lineTo` per visible bar. Zoomed out to two thousand bars on a phone that
 * is roughly six vertices per horizontal *pixel*, five of which the rasteriser resolves onto the
 * same column and throws away — after the path has allocated them, transformed them and clipped
 * them. The picture is identical and the work is not.
 *
 * ### Why the envelope and not the last point
 *
 * Keeping only one point per column would flatten a spike that happened inside it, and a spike is
 * exactly what a reader zoomed out is looking for. So a column emits, in the order they occurred,
 * its first point, its extremes and its last: the vertical stroke that comes out covers the same
 * pixels the five discarded vertices would have covered. That is what "conflation" means in every
 * terminal that ships it, and it is why it is safe to leave on.
 *
 * ### And why it is off by default anyway
 *
 * Because TradingView ships it off, and because at ordinary zooms it is pure overhead: a chart of a
 * hundred and twenty bars has no column with two points in it, so every comparison it makes is a
 * comparison that fails. It earns its keep only on a chart panned out past a screenful of pixels,
 * which is a deliberate act. See `CoineProChart`'s `conflate` parameter.
 *
 * Emission goes through a callback rather than into a `Path` so the rule can be asserted in a unit
 * test without a graphics context — the ordering is the part that can be wrong.
 */
internal class ColumnConflator(
    private val minGapPx: Float,
    private val emit: (Float, Float) -> Unit,
) {
    private var open = false
    private var columnX = 0f
    private var firstY = 0f
    private var lastY = 0f
    private var lowY = 0f
    private var highY = 0f
    private var lowAt = 0
    private var highAt = 0
    private var count = 0

    /** Offer a point. It either opens a new column or is folded into the one already open. */
    fun add(x: Float, y: Float) {
        if (!open) {
            begin(x, y)
            return
        }
        if (minGapPx <= 0f || abs(x - columnX) >= minGapPx) {
            flush()
            begin(x, y)
            return
        }
        val at = count
        count++
        lastY = y
        if (y < lowY) {
            lowY = y
            lowAt = at
        }
        if (y > highY) {
            highY = y
            highAt = at
        }
    }

    /** Emit whatever the open column holds. Call it at a gap in the data and at the end of a run. */
    fun flush() {
        if (!open) return
        // In the order the price actually reached them, so the stroke is drawn the way the market
        // moved rather than always bottom to top.
        val ordered = if (lowAt <= highAt) {
            floatArrayOf(firstY, lowY, highY, lastY)
        } else {
            floatArrayOf(firstY, highY, lowY, lastY)
        }
        var previous = Float.NaN
        for (y in ordered) {
            if (y == previous) continue
            emit(columnX, y)
            previous = y
        }
        open = false
    }

    private fun begin(x: Float, y: Float) {
        open = true
        columnX = x
        firstY = y
        lastY = y
        lowY = y
        highY = y
        lowAt = 0
        highAt = 0
        count = 1
    }
}

/**
 * How close two points have to be before conflation treats them as one column.
 *
 * Half a pixel. A whole pixel would merge two vertices that land either side of a boundary and can
 * genuinely be told apart on a high-density screen; a tenth would never merge anything.
 */
internal const val CONFLATION_GAP_PX = 0.5f

// ---------------------------------------------------------------------------- baseline

/**
 * The closes, filled in two colours against a level the reader chose.
 *
 * The fill runs from the line to the base rather than to the floor, which is the whole point of the
 * type: the area is "how far above the level, for how long", and a baseline chart filled to the
 * bottom of the plot is an area chart with a line drawn across it.
 *
 * The two halves are built from [ChartTransforms.baselineSplit] and each is clipped to its own side
 * of the base, so they meet exactly on it. Nothing here interpolates the crossing — a run begins at
 * the first bar that closed on that side, with a vertical edge down to the base — because inventing
 * the crossing price would put a price in the picture that no bar traded at, which is the one thing
 * every transform in this app refuses to do.
 *
 * The base itself is ruled dashed, because a solid rule at a price the market has not printed reads
 * as a level somebody drew.
 */
internal fun DrawScope.drawBaseline(
    view: ChartViewport,
    palette: ChartPalette,
    base: Double,
    above: DoubleArray,
    below: DoubleArray,
    conflateGap: Float,
) {
    val baseY = view.yOf(base)
    fillHalf(view, above, baseY, palette.up, topHalf = true)
    fillHalf(view, below, baseY, palette.down, topHalf = false)

    // The line itself is continuous and changes colour where it crosses, which the clip does for
    // free — two passes over one path, each showing only its own half.
    val path = closePath(view, conflateGap)
    val stroke = Stroke(width = LINE_WIDTH_DP.toPx())
    clipRect(0f, 0f, view.plotWidth, max(0f, baseY)) {
        drawPath(path, color = palette.up, style = stroke)
    }
    clipRect(0f, min(view.plotHeight, max(0f, baseY)), view.plotWidth, view.plotHeight) {
        drawPath(path, color = palette.down, style = stroke)
    }
    drawLine(
        color = palette.text,
        start = Offset(0f, baseY),
        end = Offset(view.plotWidth, baseY),
        strokeWidth = HAIRLINE_DP.toPx(),
        pathEffect = dashEffect(LineStyleKind.LARGE_DASHED, HAIRLINE_DP.toPx()),
    )
}

/** One side's fill: every run of finite values in [values], closed down to the base. */
private fun DrawScope.fillHalf(
    view: ChartViewport,
    values: DoubleArray,
    baseY: Float,
    colour: Color,
    topHalf: Boolean,
) {
    val path = Path()
    var runStart = -1
    fun close(endIndex: Int) {
        if (runStart < 0) return
        path.lineTo(view.xOf(endIndex), baseY)
        path.lineTo(view.xOf(runStart), baseY)
        path.close()
        runStart = -1
    }
    for (index in view.firstVisible..view.lastVisible) {
        val value = values.getOrNull(index) ?: Double.NaN
        if (!value.isFinite()) {
            close(index - 1)
            continue
        }
        val point = Offset(view.xOf(index), view.yOf(value))
        if (runStart < 0) {
            runStart = index
            path.moveTo(point.x, baseY)
            path.lineTo(point.x, point.y)
        } else {
            path.lineTo(point.x, point.y)
        }
    }
    close(view.lastVisible)
    val top = if (topHalf) 0f else baseY
    val bottom = if (topHalf) baseY else view.plotHeight
    clipRect(0f, max(0f, min(top, view.plotHeight)), view.plotWidth, max(0f, min(bottom, view.plotHeight))) {
        drawPath(path, brush = areaBrush(colour, top, bottom))
    }
}

// ---------------------------------------------------------------------------- HLC area

/**
 * The high-low band as an area, with the close drawn through it.
 *
 * What it is for: a line chart throws the range away, and on a thin instrument the range is most of
 * what happened. This keeps the close as the line a reader follows and puts the day's travel behind
 * it as a band, which is the same reading a candle gives without the picket fence.
 *
 * The band is one path — the highs left to right, then the lows right to left — rather than a
 * rectangle per bar, so at three pixels a bar it is a smooth envelope instead of a comb.
 */
internal fun DrawScope.drawHlcArea(view: ChartViewport, palette: ChartPalette, conflateGap: Float) {
    val colour = directionColour(view, palette)
    val band = Path()
    var started = false
    val highs = ColumnConflator(conflateGap) { x, y ->
        if (started) band.lineTo(x, y) else { band.moveTo(x, y); started = true }
    }
    for (index in view.firstVisible..view.lastVisible) {
        highs.add(view.xOf(index), view.yOf(view.series.high[index]))
    }
    highs.flush()
    val lows = ColumnConflator(conflateGap) { x, y -> band.lineTo(x, y) }
    for (index in view.lastVisible downTo view.firstVisible) {
        lows.add(view.xOf(index), view.yOf(view.series.low[index]))
    }
    lows.flush()
    band.close()
    drawPath(band, brush = areaBrush(colour, 0f, view.plotHeight))
    drawPath(
        path = closePath(view, conflateGap),
        color = colour,
        style = Stroke(width = LINE_WIDTH_DP.toPx()),
    )
}

// ---------------------------------------------------------------------------- step line

/**
 * The close held flat until the next bar prints.
 *
 * The difference from a line chart is not cosmetic. A line drawn between two closes slopes through
 * prices that were never quoted, and on a daily gold chart the slope is read as movement that
 * happened overnight. A step says what actually happened: the price was *that* until it was
 * something else, and the vertical is the moment it changed.
 *
 * The held level comes from [ChartTransforms.stepLine], which keeps it indexed by bar — so the
 * crosshair, the legend and the hit tests all still speak in bar indices rather than in a doubled
 * point list.
 */
internal fun DrawScope.drawStepLine(
    view: ChartViewport,
    palette: ChartPalette,
    held: DoubleArray,
    conflateGap: Float,
) {
    val path = Path()
    var started = false
    val half = view.barWidth / 2f
    val conflator = ColumnConflator(conflateGap) { x, y ->
        if (started) path.lineTo(x, y) else { path.moveTo(x, y); started = true }
    }
    for (index in view.firstVisible..view.lastVisible) {
        val level = held.getOrNull(index) ?: view.series.close[index]
        val right = view.xOf(index) + half
        // Along the bar at the level it arrived holding, then down or up to its own close at the
        // slot's right edge — where the next bar's held level takes over, so the two always meet.
        conflator.add(view.xOf(index) - half, view.yOf(level))
        conflator.add(right, view.yOf(level))
        conflator.add(right, view.yOf(view.series.close[index]))
    }
    conflator.flush()
    drawPath(path, color = directionColour(view, palette), style = Stroke(width = LINE_WIDTH_DP.toPx()))
}

// ---------------------------------------------------------------------------- line with markers

/**
 * The close line with a dot on every bar.
 *
 * The dots are what say *where the data is*. On a sparse series — an illiquid pair, a feed with
 * holes, a weekly chart — a plain line is a shape with no way to tell four bars from forty, and the
 * reader cannot see which parts of the curve are measurements and which are the joins between them.
 *
 * They are dropped, not shrunk, once the bars are closer together than [MARKER_MIN_SLOT_DP]. A dot
 * per bar at two pixels a bar is a thicker line, which claims something about the data that is the
 * opposite of what the dots are for.
 */
internal fun DrawScope.drawLineMarkers(
    view: ChartViewport,
    palette: ChartPalette,
    conflateGap: Float,
) {
    val colour = directionColour(view, palette)
    drawPath(closePath(view, conflateGap), color = colour, style = Stroke(width = LINE_WIDTH_DP.toPx()))
    val slot = view.barWidth
    if (slot < MARKER_MIN_SLOT_DP.toPx()) return
    val radius = min(MARKER_MAX_RADIUS_DP.toPx(), slot / MARKER_SLOT_SHARE)
    for (index in view.firstVisible..view.lastVisible) {
        drawCircle(colour, radius, Offset(view.xOf(index), view.yOf(view.series.close[index])))
    }
}

/** Below this bar spacing the dots would touch, and a line of touching dots is a thicker line. */
private val MARKER_MIN_SLOT_DP = 7.dp

/** A dot never grows past this, however far the reader zooms in. */
private val MARKER_MAX_RADIUS_DP = 2.5.dp

/** How much of a bar's slot a dot may take across. A third leaves daylight either side. */
private const val MARKER_SLOT_SHARE = 3f

// ---------------------------------------------------------------------------- volume candles

/**
 * Candles as wide as the volume that made them.
 *
 * The reading it buys, and the reason every terminal offers it: a break-out on a quarter of the
 * average volume and one on triple it are the same picture on an ordinary chart, and they are not
 * the same event. Here the bar that carried the move is the bar that is thick.
 *
 * The widths come from [ChartTransforms.volumeWidths], which clamps the thin end — a bar drawn at a
 * twentieth of the slot is a hairline, and a hairline is indistinguishable from a gap, so the reader
 * would conclude the market was closed rather than quiet. On a feed with no volume every width is
 * one and this is exactly the ordinary candle chart, which is the honest answer there.
 */
internal fun DrawScope.drawVolumeCandles(
    view: ChartViewport,
    palette: ChartPalette,
    metrics: CandleMetrics,
    widths: DoubleArray,
) {
    for (index in view.firstVisible..view.lastVisible) {
        val bar = view.series[index]
        val x = view.xOf(index)
        val colour = if (bar.up) palette.up else palette.down
        val body = max(1f, metrics.body * (widths.getOrNull(index) ?: 1.0).toFloat())
        drawLine(
            color = colour,
            start = Offset(x, view.yOf(bar.h)),
            end = Offset(x, view.yOf(bar.l)),
            strokeWidth = metrics.wick,
        )
        val top = min(view.yOf(bar.o), view.yOf(bar.c))
        val height = max(1f, abs(view.yOf(bar.c) - view.yOf(bar.o)))
        drawRect(color = colour, topLeft = Offset(x - body / 2, top), size = Size(body, height))
    }
}

// ---------------------------------------------------------------------------- footprint

/**
 * Each bar's volume, in price cells inside the bar.
 *
 * Read [ChartTransforms.footprint] before reading this: neither feed sends a tape, so the volume is
 * attributed to the side the bar closed on and spread evenly down its range. What the picture says
 * is therefore *shape and weight* — how far the bar travelled, and how much traded while it did —
 * and the cell alpha carries the weight against the heaviest bar on screen.
 *
 * The numbers are printed only where a cell is tall enough to hold one. A cell with a clipped digit
 * in it is worse than a cell with nothing in it, because the reader will try to read it.
 */
internal fun DrawScope.drawFootprint(
    view: ChartViewport,
    palette: ChartPalette,
    rows: Int,
    measurer: TextMeasurer,
    cache: TextWidthCache<TextLayoutResult>,
) {
    var peak = 0.0
    for (index in view.firstVisible..view.lastVisible) peak = max(peak, view.series.volume[index])
    if (peak <= 0.0) return
    val slot = view.barWidth
    val cellWidth = max(1f, slot - CELL_GAP_DP.toPx())
    for (index in view.firstVisible..view.lastVisible) {
        val cells = ChartTransforms.footprint(view.series, index, rows)
        if (cells.isEmpty()) continue
        val bar = view.series[index]
        val weight = (view.series.volume[index] / peak).toFloat().coerceIn(0f, 1f)
        val colour = if (bar.up) palette.up else palette.down
        val left = view.xOf(index) - cellWidth / 2
        for (cell in cells) {
            val top = view.yOf(cell.high)
            val bottom = view.yOf(cell.low)
            val height = max(1f, bottom - top)
            drawRect(
                color = colour.copy(alpha = CELL_ALPHA_FLOOR + CELL_ALPHA_RANGE * weight),
                topLeft = Offset(left, top),
                size = Size(cellWidth, height),
            )
            val style = axisStyle(palette.stage)
            val text = compactVolume(cell.total)
            val label = cache.measure(text to style) { measurer.measure(text, style) }
            if (label.size.height + CELL_GAP_DP.toPx() > height) continue
            if (label.size.width + CELL_GAP_DP.toPx() > cellWidth) continue
            drawText(
                textLayoutResult = label,
                topLeft = Offset(
                    left + (cellWidth - label.size.width) / 2,
                    top + (height - label.size.height) / 2,
                ),
            )
        }
        // A hairline lid on the bar, so two adjacent bars of the same weight do not read as one
        // block of colour.
        drawRect(
            color = colour,
            topLeft = Offset(left, view.yOf(bar.h)),
            size = Size(cellWidth, max(1f, view.yOf(bar.l) - view.yOf(bar.h))),
            style = Stroke(width = HAIRLINE_DP.toPx()),
        )
    }
}

/** Air between two price cells, so the grid reads as cells rather than as a column of colour. */
private val CELL_GAP_DP = 2.dp

/** The faintest a cell is drawn — present even on the quietest bar on screen. */
private const val CELL_ALPHA_FLOOR = 0.14f

/** How much more alpha the heaviest bar on screen gets over the lightest. */
private const val CELL_ALPHA_RANGE = 0.56f

/**
 * A volume as three or four characters.
 *
 * Latin digits and `Locale.US`, like every other market figure this canvas prints: the device
 * locale is Persian and `String.format` follows it, which would put «۱٫۲K» inside a cell in a grid
 * of Latin numbers. The suffixes are Latin too, because a footprint cell is four characters wide
 * and there is no room for a word.
 */
internal fun compactVolume(volume: Double): String {
    val magnitude = abs(volume)
    return when {
        !volume.isFinite() -> NO_VALUE
        magnitude >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", volume / 1_000_000_000)
        magnitude >= 1_000_000 -> String.format(Locale.US, "%.1fM", volume / 1_000_000)
        magnitude >= 1_000 -> String.format(Locale.US, "%.1fK", volume / 1_000)
        magnitude >= 1 -> String.format(Locale.US, "%.0f", volume)
        else -> String.format(Locale.US, "%.2f", volume)
    }
}

// ---------------------------------------------------------------------------- TPO

/**
 * The profile: one block per price row per time bracket, stacked from the left.
 *
 * The x axis of a TPO is a *count*, not a clock — how many brackets of the session visited that
 * price — and the width of the profile at a price is the whole content of the chart: it is where
 * the session agreed on value. That is why the blocks are stacked from the left edge rather than
 * drawn at the bar they came from, and why the widest row is picked out: the point of control is
 * the one number a reader takes off a profile.
 *
 * The cells come from [ChartTransforms.tpo], which emits visits and nothing else, so a row's block
 * count is its width with nothing to test.
 */
internal fun DrawScope.drawTpo(
    view: ChartViewport,
    palette: ChartPalette,
    rows: Int,
    bracketBars: Int,
) {
    val cells = ChartTransforms.tpo(view.series, view.firstVisible, view.lastVisible, rows, bracketBars)
    if (cells.isEmpty()) return
    var low = view.series.low[view.firstVisible]
    var high = view.series.high[view.firstVisible]
    for (index in view.firstVisible..view.lastVisible) {
        low = min(low, view.series.low[index])
        high = max(high, view.series.high[index])
    }
    val span = high - low
    if (span <= 0.0) return
    val rowHeight = span / rows
    val brackets = (cells.maxOf { it.bracket } + 1).coerceAtLeast(1)
    val cellWidth = view.plotWidth / brackets
    if (cellWidth <= 0f) return

    // The widest row, which is the point of control. Counted here rather than returned by the
    // transform because it is a property of what is on screen, not of the profile arithmetic.
    val widths = IntArray(rows)
    for (cell in cells) if (cell.rowIndex in 0 until rows) widths[cell.rowIndex]++
    var control = 0
    for (row in widths.indices) if (widths[row] > widths[control]) control = row

    for (cell in cells) {
        val bottom = view.yOf(low + cell.rowIndex * rowHeight)
        val top = view.yOf(low + (cell.rowIndex + 1) * rowHeight)
        val colour = if (cell.rowIndex == control) palette.up else palette.text
        drawRect(
            color = colour.copy(alpha = if (cell.rowIndex == control) CONTROL_ALPHA else BLOCK_ALPHA),
            topLeft = Offset(cell.bracket * cellWidth, min(top, bottom)),
            size = Size(
                max(1f, cellWidth - CELL_GAP_DP.toPx()),
                max(1f, abs(bottom - top) - BLOCK_GAP_PX),
            ),
        )
    }
}

/** How solid an ordinary profile block is. Monochrome, the way every profile is drawn. */
private const val BLOCK_ALPHA = 0.55f

/** And the row that was visited most, which is the one thing a reader takes off a profile. */
private const val CONTROL_ALPHA = 0.85f

/** A pixel of air between two rows, so the profile reads as rows. */
private const val BLOCK_GAP_PX = 1f

/**
 * How many bars one TPO bracket covers.
 *
 * A bracket is classically half an hour, and [ChartTypeConfig.tpoBracketMinutes] is where the
 * reader says so — but only the renderer knows how many minutes a bar is, which is why the
 * transform takes bars. With no answer from the reader the window is divided into
 * [TPO_TARGET_BRACKETS] instead, so a profile stays roughly the same width whatever the timeframe:
 * a fixed bar count would make an hourly chart one enormous block and a minute chart a smear.
 */
internal fun tpoBracketBars(visibleCount: Int, barSeconds: Long, bracketMinutes: Int?): Int {
    if (bracketMinutes != null && bracketMinutes > 0 && barSeconds > 0L) {
        return max(1, (bracketMinutes * 60.0 / barSeconds).roundToInt())
    }
    if (visibleCount <= TPO_TARGET_BRACKETS) return 1
    return max(1, (visibleCount + TPO_TARGET_BRACKETS - 1) / TPO_TARGET_BRACKETS)
}

/**
 * How many brackets a profile is aimed at.
 *
 * Thirteen, which is a session of half-hour brackets — the shape the reader already knows — and
 * also about as many blocks as fit across a phone before each one is thinner than the gap beside it.
 */
internal const val TPO_TARGET_BRACKETS = 13

/**
 * The bar interval in seconds, taken from the newest pair of timestamps.
 *
 * The same inference the countdown makes, and for the same reason: a timeframe passed in can be a
 * label on a feed that is sending something else, while the gap between the last two bars is what
 * the chart is actually drawing. Zero on a series too short to have an interval, which every caller
 * treats as "cannot tell".
 */
internal fun barIntervalSeconds(series: CandleSeries): Long {
    val times = series.time
    if (times.size < 2) return 0L
    return max(0L, times[times.size - 1] - times[times.size - 2])
}

/**
 * How many price rows a footprint or a profile is cut into, with a floor on how thin a row may be.
 *
 * [ChartTransforms.defaultRows] answers in *price* terms — a row is an eighth of an average bar's
 * range — and knows nothing about how tall the plot is. Sixty-four rows on a chart two hundred
 * pixels high is three pixels a row, which is a texture rather than a table. This is where the two
 * constraints meet, and it is the renderer's job because only the renderer has the pixels.
 */
internal fun legibleRows(requested: Int, plotHeight: Float, minRowPx: Float): Int {
    if (plotHeight <= 0f || minRowPx <= 0f) return max(1, requested)
    val fits = (plotHeight / minRowPx).toInt()
    return max(1, min(requested, fits))
}

/**
 * Below this much room per bar, a footprint and a profile stop being legible and fall back.
 *
 * Twenty-eight density-independent pixels is about four digits at the axis' own size. Below it the
 * numbers cannot be printed at all, and a footprint with no numbers in it is a candle chart drawn
 * badly — so the renderer draws the candle chart instead, and says so.
 */
internal val FOOTPRINT_MIN_SLOT_DP = 28.dp

/** The shortest a price cell may be before its row stops being worth drawing. */
internal val MIN_CELL_HEIGHT_DP = 9.dp

// ---------------------------------------------------------------------------- shared

/**
 * The close polyline over the visible window, conflated.
 *
 * Shared by every line-shaped type so they cannot drift apart in how they walk the series — the
 * baseline's line, the step's neighbours, the HLC's centre line and the markers' line are all the
 * same curve, and four copies of the same loop is four places for one of them to keep using the old
 * behaviour after a fix.
 */
internal fun closePath(view: ChartViewport, conflateGap: Float): Path {
    val path = Path()
    var started = false
    val conflator = ColumnConflator(conflateGap) { x, y ->
        if (started) path.lineTo(x, y) else { path.moveTo(x, y); started = true }
    }
    for (index in view.firstVisible..view.lastVisible) {
        conflator.add(view.xOf(index), view.yOf(view.series.close[index]))
    }
    conflator.flush()
    return path
}
