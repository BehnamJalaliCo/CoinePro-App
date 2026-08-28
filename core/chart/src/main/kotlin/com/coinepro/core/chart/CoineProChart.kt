package com.coinepro.core.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinepro.core.designsystem.CoineProColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The price chart.
 *
 * One Canvas and no view hierarchy, because the thing being drawn is a few hundred rectangles that
 * change on every frame of a drag. A composable per candle would allocate and recompose a hundred
 * nodes per frame; this allocates nothing inside the draw loop.
 *
 * The layout is the one every terminal converged on and is not a preference: price axis down the
 * right, time along the bottom, volume in a band under the price. Right rather than start-relative
 * even in Persian — a price axis is not text, it is the y axis of a graph, and mirroring it would
 * put the newest bar on the left, which no trader on earth reads.
 *
 * Everything about *where things are* comes from [ChartViewport] and nothing here computes a
 * coordinate of its own. That is what will let the drawing tools land later without touching this
 * file.
 */
@Composable
fun CoineProChart(
    series: CandleSeries,
    modifier: Modifier = Modifier,
    type: ChartType = ChartType.CANDLES,
    typeConfig: ChartTypeConfig = ChartTypeConfig(),
    decoration: ChartDecoration = ChartDecoration(),
    /** Interaction off makes this a static picture — for a list row or a card. */
    interactive: Boolean = true,
    /**
     * The drawing layer's state, or null on a chart that is not a drawing surface.
     *
     * Hoisted rather than owned here, because a screen has to save it, restore it and show it in a
     * list beside the chart. What is passed in decides what a tap does: with a tool armed a tap
     * places a point, and without one it selects whatever the tap landed on.
     */
    drawing: DrawingState? = null,
    onDrawing: ((DrawingState) -> Unit)? = null,
) {
    val display = remember(series, type, typeConfig) { ChartTransforms.apply(series, type, typeConfig) }
    var viewport by remember(display) { mutableStateOf(ChartViewport(display)) }
    var crosshair by remember { mutableStateOf<Crosshair?>(null) }

    // Follow the live edge as bars arrive, but never drag the view out from under a reader who has
    // panned back. ChartViewport.withSeries decides which of those applies.
    remember(display) { viewport = viewport.withSeries(display) }

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val axisWidth = with(density) { AXIS_WIDTH.toPx() }
    val timeHeight = with(density) { TIME_HEIGHT.toPx() }
    val tolerancePx = with(density) { DrawingHitTest.TOLERANCE_DP.dp.toPx() }

    /**
     * The last viewport the draw pass computed, for the gestures to read.
     *
     * A plain array rather than state: the gesture handlers need the plot size, which is only known
     * inside the draw lambda, and writing state from a draw pass invites a recomposition loop. The
     * gestures do not need to recompose when it changes — they need to read the current value at
     * the moment a finger lands.
     */
    val lastView = remember { arrayOfNulls<ChartViewport>(1) }
    val armed = drawing?.tool

    val palette = ChartPalette(
        up = CoineProColors.Buy,
        down = CoineProColors.Sell,
        grid = CoineProColors.Border,
        text = CoineProColors.TextMuted,
        crosshair = CoineProColors.TextSecondary,
        stage = CoineProColors.Stage,
    )

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!interactive) {
                        Modifier
                    } else {
                        Modifier
                            .pointerInput(display, armed) {
                                // Panning is off while a tool is armed. A one-finger drag on a
                                // chart in drawing mode is a placement, and a chart that scrolls
                                // under the finger at the same time places the point somewhere the
                                // reader was not pointing.
                                if (armed != null) return@pointerInput
                                detectTransformGestures { _, pan, zoom, _ ->
                                    if (abs(zoom - 1f) > ZOOM_DEADZONE) viewport = viewport.zoomedBy(zoom)
                                    if (abs(pan.x) > 0f) viewport = viewport.pannedBy(pan.x)
                                }
                            }
                            .pointerInput(display, armed, drawing, onDrawing) {
                                if (onDrawing == null || drawing == null) return@pointerInput
                                // Freehand is the one tool family that needs a drag: the stroke is
                                // the gesture. Everything else is N taps, which is the only thing
                                // that works for a five-point pattern on a phone — a five-point
                                // drag would have to be one continuous gesture with four pauses.
                                if (armed?.points != 0) return@pointerInput
                                val samples = mutableListOf<ChartPoint>()
                                detectDragGestures(
                                    onDragStart = { position ->
                                        samples.clear()
                                        lastView[0]?.let { samples += it.chartPointAt(position, display, drawing.magnet) }
                                    },
                                    onDrag = { change, _ ->
                                        lastView[0]?.let { samples += it.chartPointAt(change.position, display, drawing.magnet) }
                                    },
                                    onDragEnd = { onDrawing(DrawingActions.stroke(drawing, samples.toList())) },
                                    onDragCancel = { samples.clear() },
                                )
                            }
                            .pointerInput(display) {
                                // Long-press to summon the crosshair, drag to move it, lift to
                                // dismiss. A crosshair that follows every tap fights with panning;
                                // one that has to be asked for does not.
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { crosshair = viewport.crosshairAt(it) },
                                    onDrag = { change, _ -> crosshair = viewport.crosshairAt(change.position) },
                                    onDragEnd = { crosshair = null },
                                    onDragCancel = { crosshair = null },
                                )
                            }
                            .pointerInput(display, armed, drawing, onDrawing, tolerancePx) {
                                detectTapGestures(
                                    onDoubleTap = { viewport = viewport.atOffset(0) },
                                    onTap = { position ->
                                        val state = drawing ?: return@detectTapGestures
                                        val emit = onDrawing ?: return@detectTapGestures
                                        val view = lastView[0] ?: return@detectTapGestures
                                        val point = view.chartPointAt(position, display, state.magnet)
                                        // With nothing armed the tap is a selection, so the hit
                                        // test runs; with a tool armed it is a placement, and
                                        // running the hit test would let a tap that happens to
                                        // land on an old drawing silently select instead of place.
                                        val hit = if (state.tool == null) {
                                            DrawingHitTest.at(
                                                drawings = state.drawings,
                                                x = position.x,
                                                y = position.y,
                                                view = view,
                                                tolerancePx = tolerancePx,
                                            )?.id
                                        } else {
                                            null
                                        }
                                        emit(DrawingActions.tap(state, point, hit))
                                    },
                                )
                            }
                    },
                ),
        ) {
            val plotWidth = max(0f, size.width - if (decoration.showAxes) axisWidth else 0f)
            val timeAxis = if (decoration.showAxes && decoration.showTimeAxis) timeHeight else 0f
            // Panes eat into the price's height, never into each other or the axis. Clamped in
            // total, because four oscillators at 18% each would leave the candles a sliver — and
            // the candles are what the reader came for.
            val paneRatio = min(PANE_BUDGET, decoration.panes.sumOf { it.heightRatio.toDouble() }.toFloat())
            val available = max(0f, size.height - timeAxis)
            val paneHeight = if (decoration.panes.isEmpty()) 0f else available * paneRatio
            val plotHeight = max(0f, available - paneHeight)

            val view = viewport
                .sized(plotWidth, plotHeight)
                .copy(includedPrices = decoration.signal?.levels().orEmpty())
            lastView[0] = view
            if (view.visibleCount == 0) return@Canvas

            if (decoration.showAxes) drawGrid(view, plotWidth, palette, measurer, type)
            // The setup goes *under* the price. It is context for the bars, and drawn over them it
            // tints every candle it covers — which on a full-height risk band is most of them.
            decoration.signal?.let { drawSignal(view, it, plotWidth, palette, measurer) }
            // Volume sits in the foot of the price pane rather than in a band of its own — the way
            // every terminal draws it. A separate band cost a fifth of the canvas to say something
            // the reader glances at, and on a chart with three oscillators the volume bars ended up
            // taller than the candles above them.
            if (decoration.showVolume && series.hasVolume) {
                clipRect(0f, 0f, plotWidth, plotHeight) { drawVolume(view, plotHeight, palette) }
            }
            when {
                type.isLine -> drawLineSeries(view, palette, filled = type == ChartType.AREA)
                type == ChartType.BARS -> drawOhlcBars(view, palette)
                else -> drawCandles(view, palette, hollow = type == ChartType.HOLLOW)
            }
            // Clipped, like the drawings below and for the same reason. An overlay is a value per
            // bar and most of them stay near the price — but a pivot ladder, a SuperTrend after a
            // flip, or a Bollinger band on a spike all resolve to a y outside the plot, and an
            // unclipped Canvas paints them over the header and the axis. The first render of the
            // chart screen had a pivot line drawn across the symbol name.
            clipRect(0f, 0f, plotWidth, plotHeight) {
                decoration.overlays.forEach { drawOverlay(view, it, density.density) }
                decoration.levels.forEach { drawLevel(view, it, plotWidth, measurer) }
                decoration.markers.forEach { drawMarker(view, it, density.density) }
            }
            // The reader's own drawings go *over* the price — the opposite of the signal band. They
            // are annotations on the bars, and an annotation the bars cover is not one.
            //
            // Clipped to the plot, because a Compose Canvas does not clip itself and half these
            // tools are unbounded by definition: a ray runs four screen-diagonals past its second
            // point, and an unclipped one paints straight over the price axis, the volume pane and
            // whatever composable sits below the chart.
            //
            // A live drawing layer wins over the decoration's static list, and carries the
            // half-placed drawing with it — that is what lets a five-point pattern take shape as it
            // is tapped out rather than appearing whole on the fifth tap.
            val marks = drawing?.visible ?: decoration.drawings
            val highlighted = drawing?.selectedId ?: decoration.selectedDrawingId
            if (marks.isNotEmpty()) {
                clipRect(0f, 0f, plotWidth, plotHeight) {
                    marks.forEach { mark ->
                        drawDrawing(
                            drawing = mark,
                            view = view,
                            measurer = measurer,
                            selected = mark.id == highlighted,
                        )
                    }
                }
            }
            if (paneHeight > 0f) {
                var top = plotHeight
                val share = paneHeight / decoration.panes.sumOf { it.heightRatio.toDouble() }.toFloat()
                for (pane in decoration.panes) {
                    val height = pane.heightRatio * share
                    drawPane(view, pane, top, height, plotWidth, palette, measurer, density.density)
                    top += height
                }
            }
            // The live edge, and its price against the axis. This is the one number on the chart a
            // reader looks for without being asked, and before this it was only in the header —
            // where it says nothing about *where* on the scale the market currently is.
            val lastPriceY = if (decoration.showLastPrice) lastPriceTagY(view, measurer) else null
            if (decoration.showAxes) {
                drawPriceAxis(view, plotWidth, palette, measurer, lastPriceY)
                if (decoration.showTimeAxis) {
                    drawTimeAxis(view, plotHeight + paneHeight, plotWidth, type, palette, measurer)
                }
            }
            if (decoration.showLastPrice) {
                drawLastPrice(view, plotWidth, axisWidth, palette, measurer, decoration.showAxes)
            }
            if (decoration.showLegend) {
                drawLegend(view, decoration, crosshair, palette, measurer)
            }
            crosshair?.let {
                drawCrosshair(view, it, plotWidth, axisWidth, plotHeight + paneHeight, palette, measurer, decoration)
            }
        }
    }
}

/** The colours the chart draws with, resolved once per composition rather than per bar. */
private data class ChartPalette(
    val up: Color,
    val down: Color,
    val grid: Color,
    val text: Color,
    val crosshair: Color,
    val stage: Color,
)

// ---------------------------------------------------------------------------- series

private fun DrawScope.drawCandles(view: ChartViewport, palette: ChartPalette, hollow: Boolean) {
    val body = view.bodyWidth
    for (index in view.firstVisible..view.lastVisible) {
        val bar = view.series[index]
        val x = view.xOf(index)
        // A hollow chart colours by the *previous close*, not by the bar's own open — that is what
        // makes a run of gaps up read as one colour even when individual bars closed down.
        val rising = if (hollow && index > 0) bar.c >= view.series.close[index - 1] else bar.up
        val colour = if (rising) palette.up else palette.down

        drawLine(
            color = colour,
            start = Offset(x, view.yOf(bar.h)),
            end = Offset(x, view.yOf(bar.l)),
            strokeWidth = WICK_WIDTH,
        )
        val top = min(view.yOf(bar.o), view.yOf(bar.c))
        // A doji has no body height at all, and a zero-height rectangle draws nothing — so it is
        // given a hairline. Without it a flat bar vanishes and the chart appears to have a gap.
        val height = max(1f, abs(view.yOf(bar.c) - view.yOf(bar.o)))
        if (hollow && rising) {
            drawRect(
                color = colour,
                topLeft = Offset(x - body / 2, top),
                size = Size(body, height),
                style = Stroke(width = WICK_WIDTH),
            )
        } else {
            drawRect(color = colour, topLeft = Offset(x - body / 2, top), size = Size(body, height))
        }
    }
}

private fun DrawScope.drawOhlcBars(view: ChartViewport, palette: ChartPalette) {
    val tick = view.bodyWidth / 2
    for (index in view.firstVisible..view.lastVisible) {
        val bar = view.series[index]
        val x = view.xOf(index)
        val colour = if (bar.up) palette.up else palette.down
        drawLine(colour, Offset(x, view.yOf(bar.h)), Offset(x, view.yOf(bar.l)), WICK_WIDTH)
        drawLine(colour, Offset(x - tick, view.yOf(bar.o)), Offset(x, view.yOf(bar.o)), WICK_WIDTH)
        drawLine(colour, Offset(x, view.yOf(bar.c)), Offset(x + tick, view.yOf(bar.c)), WICK_WIDTH)
    }
}

private fun DrawScope.drawLineSeries(view: ChartViewport, palette: ChartPalette, filled: Boolean) {
    val path = Path()
    for (index in view.firstVisible..view.lastVisible) {
        val point = Offset(view.xOf(index), view.yOf(view.series.close[index]))
        if (index == view.firstVisible) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    // Colour by the direction of the visible window, so a line chart still says up or down at a
    // glance the way a candle chart does.
    val first = view.series.close[view.firstVisible]
    val last = view.series.close[view.lastVisible]
    val colour = if (last >= first) palette.up else palette.down

    if (filled) {
        val fill = Path().apply {
            addPath(path)
            lineTo(view.xOf(view.lastVisible), view.plotHeight)
            lineTo(view.xOf(view.firstVisible), view.plotHeight)
            close()
        }
        drawPath(fill, color = colour.copy(alpha = AREA_ALPHA))
    }
    drawPath(path, color = colour, style = Stroke(width = LINE_WIDTH))
}

private fun DrawScope.drawOverlay(view: ChartViewport, overlay: ChartLine, density: Float) {
    val path = Path()
    var started = false
    for (index in view.firstVisible..view.lastVisible) {
        val value = overlay.values[index]
        if (value == null) {
            // A gap in the middle of a line — a SuperTrend flip, a missing bar — lifts the pen
            // rather than drawing a straight line across it, which would read as a real move.
            // Unless the study is one whose gaps are the point: a zigzag names only its turns and
            // the join between them is the whole shape.
            if (!overlay.connectNulls) started = false
            continue
        }
        val point = Offset(view.xOf(index), view.yOf(value))
        if (!started) {
            path.moveTo(point.x, point.y)
            started = true
        } else {
            path.lineTo(point.x, point.y)
        }
    }
    drawPath(
        path = path,
        color = Color(overlay.colour),
        style = Stroke(
            width = overlay.widthDp * density,
            pathEffect = if (overlay.dashed) {
                PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF))
            } else {
                null
            },
        ),
    )
}

// ---------------------------------------------------------------------------- panes

/**
 * Volume in the foot of the price pane.
 *
 * Anchored to the bottom of the plot and scaled so the tallest visible bar reaches [VOLUME_INLINE]
 * of the plot's height. Drawn faint and *under* the candles, so it reads as ground rather than as a
 * second series competing with the price.
 *
 * The peak is taken from what is visible, not from the whole series. One record day three months
 * back would otherwise flatten every bar on screen to a millimetre — which is the same failure the
 * price axis avoids by scaling to the window.
 */
private fun DrawScope.drawVolume(view: ChartViewport, plotHeight: Float, palette: ChartPalette) {
    var peak = 0.0
    for (index in view.firstVisible..view.lastVisible) {
        if (view.series.volume[index] > peak) peak = view.series.volume[index]
    }
    if (peak <= 0.0) return
    val body = view.bodyWidth
    val band = plotHeight * VOLUME_INLINE
    for (index in view.firstVisible..view.lastVisible) {
        val bar = view.series[index]
        val height = (view.series.volume[index] / peak * band).toFloat()
        drawRect(
            color = (if (bar.up) palette.up else palette.down).copy(alpha = VOLUME_ALPHA),
            topLeft = Offset(view.xOf(index) - body / 2, plotHeight - height),
            size = Size(body, height),
        )
    }
}

/**
 * One indicator pane: a hairline lid, a title, and the lines on their own scale.
 *
 * The scale is taken from what is *visible*, not from the whole series. An RSI that spent last
 * March at 12 must not flatten today's range, and a reader who has zoomed in is asking about the
 * bars in front of them.
 *
 * Levels declared by the pane are folded into the extremes before the scale is fixed, so RSI's 30
 * and 70 are always on screen even on a stretch where the line never reached them — a pane whose
 * reference lines are outside its own scale is a pane that lies about where the line is sitting.
 */
private fun DrawScope.drawPane(
    view: ChartViewport,
    pane: ChartPane,
    top: Float,
    height: Float,
    plotWidth: Float,
    palette: ChartPalette,
    measurer: TextMeasurer,
    density: Float,
) {
    if (height <= 0f || plotWidth <= 0f) return
    drawLine(
        color = palette.grid.copy(alpha = GRID_ALPHA),
        start = Offset(0f, top),
        end = Offset(plotWidth, top),
        strokeWidth = HAIRLINE,
    )

    var low = Double.MAX_VALUE
    var high = -Double.MAX_VALUE
    val series = (pane.lines + listOfNotNull(pane.histogram))
    for (line in series) {
        for (index in view.firstVisible..view.lastVisible) {
            val value = line.values[index] ?: continue
            if (value < low) low = value
            if (value > high) high = value
        }
    }
    for (level in pane.levels) {
        if (level.price < low) low = level.price
        if (level.price > high) high = level.price
    }
    // A histogram is read against zero, so zero has to be inside the scale even when every bar in
    // view is on one side of it. Without this a run of positive MACD draws as bars hanging off the
    // bottom edge with no baseline to hang from.
    if (pane.histogram != null) {
        if (low > 0.0) low = 0.0
        if (high < 0.0) high = 0.0
    }
    if (high < low) return
    // A perfectly flat pane still has to draw, and dividing by a zero span would put it at NaN.
    val span = (high - low).takeIf { it > 0.0 } ?: 1.0
    val padded = span * PANE_PADDING
    val bottom = low - padded
    val ceiling = high + padded
    fun yOf(value: Double): Float =
        top + height * (1.0 - (value - bottom) / (ceiling - bottom)).toFloat()

    clipRect(0f, top, plotWidth, top + height) {
        pane.levels.forEach { level ->
            val y = yOf(level.price)
            drawLine(
                color = Color(level.colour.toInt()),
                start = Offset(0f, y),
                end = Offset(plotWidth, y),
                strokeWidth = HAIRLINE,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF)),
            )
        }
        pane.histogram?.let { histogram ->
            val zero = yOf(0.0)
            val body = view.bodyWidth
            for (index in view.firstVisible..view.lastVisible) {
                val value = histogram.values[index] ?: continue
                val y = yOf(value)
                drawRect(
                    color = if (value >= 0) palette.up else palette.down,
                    topLeft = Offset(view.xOf(index) - body / 2, min(y, zero)),
                    size = Size(body, max(1f, abs(y - zero))),
                )
            }
        }
        pane.lines.forEach { line ->
            val path = Path()
            var started = false
            for (index in view.firstVisible..view.lastVisible) {
                val value = line.values[index]
                if (value == null) {
                    if (!line.connectNulls) started = false
                    continue
                }
                val x = view.xOf(index)
                val y = yOf(value)
                if (!started) {
                    path.moveTo(x, y)
                    started = true
                } else {
                    path.lineTo(x, y)
                }
            }
            drawPath(
                path = path,
                color = Color(line.colour),
                style = Stroke(
                    width = line.widthDp * density,
                    pathEffect = if (line.dashed) {
                        PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF))
                    } else {
                        null
                    },
                ),
            )
        }
    }

    // The title on its own ground. A pane's own reference lines run the full width and one of them
    // is usually near the top — RSI's seventy, MACD's zero on a positive stretch — so without this
    // the dashes ran straight through the letters.
    val title = measurer.measure(pane.title, axisStyle(palette.text))
    drawRect(
        color = palette.stage,
        topLeft = Offset(0f, top + 1f),
        size = Size(title.size.width + LEGEND_INSET * 2, title.size.height + AXIS_PADDING * 2),
    )
    drawText(title, topLeft = Offset(LEGEND_INSET, top + AXIS_PADDING))
    // The two extremes at the right edge rather than a full axis: a pane is a shape to read, not a
    // scale to measure off, and five gridline labels in a 90px strip is unreadable noise.
    val decimals = paneDecimals(high - low)
    val topLabel = measurer.measure(formatPrice(ceiling, decimals), axisStyle(palette.text))
    drawText(topLabel, topLeft = Offset(plotWidth + AXIS_PADDING, top + AXIS_PADDING))
    val bottomLabel = measurer.measure(formatPrice(bottom, decimals), axisStyle(palette.text))
    drawText(
        bottomLabel,
        topLeft = Offset(plotWidth + AXIS_PADDING, top + height - bottomLabel.size.height - AXIS_PADDING),
    )
}

/**
 * How many decimals a pane's extremes need.
 *
 * Driven by the pane's own span, not by the price's: an RSI spanning 40 points needs none and a
 * MACD spanning 0.004 needs four. Reusing the price rule would print "0.00" for both edges of a
 * MACD pane, which says nothing at all.
 */
private fun paneDecimals(span: Double): Int = when {
    span >= 100 -> 0
    span >= 10 -> 1
    span >= 1 -> 2
    span >= 0.01 -> 4
    else -> 6
}

// ---------------------------------------------------------------------------- axes

private fun DrawScope.drawGrid(
    view: ChartViewport,
    plotWidth: Float,
    palette: ChartPalette,
    measurer: TextMeasurer,
    type: ChartType,
) {
    val grid = palette.grid.copy(alpha = GRID_ALPHA)
    for (step in 0..GRID_ROWS) {
        val y = view.plotHeight * step / GRID_ROWS
        drawLine(grid, Offset(0f, y), Offset(plotWidth, y), HAIRLINE)
    }
    // Verticals stand where the time labels stand, not at even fractions of the width. A grid whose
    // columns do not line up with the dates underneath them is a grid a reader cannot use to read
    // a date off a bar — which is the only thing vertical gridlines are for.
    for (index in timeLabelIndices(view)) {
        val x = view.xOf(index)
        if (x < 0f || x > plotWidth) continue
        drawLine(grid, Offset(x, 0f), Offset(x, view.plotHeight), HAIRLINE)
    }
}

/**
 * Which bars carry a label on the time axis.
 *
 * Shared by the axis and the grid so the two cannot disagree: the columns are placed by the same
 * arithmetic that places the dates, rather than by a second rule that happens to look similar.
 */
private fun timeLabelIndices(view: ChartViewport): List<Int> {
    if (view.visibleCount <= 0) return emptyList()
    return (0 until TIME_LABELS)
        .map { step -> view.firstVisible + (view.visibleCount - 1) * step / max(1, TIME_LABELS - 1) }
        .filter { it <= view.lastVisible }
        .distinct()
}

private fun DrawScope.drawPriceAxis(
    view: ChartViewport,
    plotWidth: Float,
    palette: ChartPalette,
    measurer: TextMeasurer,
    suppressNear: Float?,
) {
    val span = view.priceRange.endInclusive - view.priceRange.start
    val decimals = decimalsFor(view.priceRange.endInclusive)
    for (step in 0..GRID_ROWS) {
        val price = view.priceRange.start + span * (GRID_ROWS - step) / GRID_ROWS
        val y = view.plotHeight * step / GRID_ROWS
        val label = measurer.measure(formatPrice(price, decimals), axisStyle(palette.text))
        // Centred on the gridline, except at the two ends where centring would push half the label
        // off the canvas — the top one was clipped to a row of stumps before this.
        val top = (y - label.size.height / 2).coerceIn(0f, view.plotHeight - label.size.height)
        // A gridline label under the live-price tag is a number half-covered by another number.
        // The tag wins: it is the price the reader came for, and the gridline it hides is the one
        // they can infer from the two either side of it.
        if (suppressNear != null && abs(top - suppressNear) < label.size.height * 1.4f) continue
        drawText(textLayoutResult = label, topLeft = Offset(plotWidth + AXIS_PADDING, top))
    }
}

/**
 * Where the live-price tag will sit, so the axis can step around it.
 *
 * Computed separately rather than returned from [drawLastPrice], because the axis is drawn first —
 * the tag has to land on top of the gridline labels, not under them.
 */
private fun DrawScope.lastPriceTagY(view: ChartViewport, measurer: TextMeasurer): Float? {
    val bar = view.series.bars.getOrNull(view.lastVisible) ?: return null
    val y = view.yOf(bar.c)
    if (y < 0f || y > view.plotHeight) return null
    val height = measurer.measure("0", axisStyle(Color.White)).size.height + TAG_PADDING * 2
    return (y - height / 2).coerceIn(0f, max(0f, view.plotHeight - height))
}

/**
 * The price at the live edge, tagged against the axis.
 *
 * A dashed rule the width of the plot and a filled tag on the axis in the last bar's own direction.
 * The rule is what places the price on the visible scale; the tag is what makes the number legible
 * against the gridline labels it sits between.
 *
 * Skipped when the last close is outside the visible price range, which happens whenever the reader
 * has panned back — a tag pinned to the top or bottom edge would claim a price the chart is not
 * showing.
 */
private fun DrawScope.drawLastPrice(
    view: ChartViewport,
    plotWidth: Float,
    axisWidth: Float,
    palette: ChartPalette,
    measurer: TextMeasurer,
    withAxis: Boolean,
) {
    val bar = view.series.bars.getOrNull(view.lastVisible) ?: return
    val y = view.yOf(bar.c)
    if (y < 0f || y > view.plotHeight) return
    val colour = if (bar.up) palette.up else palette.down
    drawLine(
        color = colour.copy(alpha = LAST_PRICE_ALPHA),
        start = Offset(0f, y),
        end = Offset(plotWidth, y),
        strokeWidth = HAIRLINE,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF)),
    )
    if (!withAxis) return
    drawAxisTag(
        text = formatPrice(bar.c, decimalsFor(bar.c)),
        y = y,
        plotWidth = plotWidth,
        axisWidth = axisWidth,
        fill = colour,
        textColour = palette.stage,
        measurer = measurer,
        plotHeight = view.plotHeight,
    )
}

/**
 * A filled label in the price gutter.
 *
 * Shared by the last-price tag and the crosshair's, so the two are the same object in two colours
 * rather than two things that drifted apart. Clamped inside the plot so a tag near an edge stays
 * whole instead of being sliced by the canvas boundary.
 */
private fun DrawScope.drawAxisTag(
    text: String,
    y: Float,
    plotWidth: Float,
    axisWidth: Float,
    fill: Color,
    textColour: Color,
    measurer: TextMeasurer,
    plotHeight: Float,
) {
    val label = measurer.measure(text, axisStyle(textColour))
    val height = label.size.height + TAG_PADDING * 2
    val top = (y - height / 2).coerceIn(0f, max(0f, plotHeight - height))
    drawRoundRect(
        color = fill,
        topLeft = Offset(plotWidth + 1f, top),
        size = Size(max(0f, axisWidth - 2f), height),
        cornerRadius = CornerRadius(TAG_RADIUS, TAG_RADIUS),
    )
    drawText(
        textLayoutResult = label,
        topLeft = Offset(plotWidth + AXIS_PADDING, top + TAG_PADDING),
    )
}

/**
 * The corner readout.
 *
 * The bar's four prices, then one line per overlay in that overlay's own colour. Which bar it
 * reads is the crosshair's when there is one and the last otherwise — so putting a finger on the
 * chart turns the legend into a scrubber over history rather than a second copy of the header.
 *
 * Capped at [LEGEND_LINES] rows. A chart with nine overlays would otherwise print a paragraph over
 * its own candles, and the overflow is stated rather than silently dropped.
 */
private fun DrawScope.drawLegend(
    view: ChartViewport,
    decoration: ChartDecoration,
    crosshair: Crosshair?,
    palette: ChartPalette,
    measurer: TextMeasurer,
) {
    val index = (crosshair?.index ?: view.lastVisible).coerceIn(view.firstVisible, view.lastVisible)
    val bar = view.series.bars.getOrNull(index) ?: return
    val decimals = decimalsFor(bar.c)
    val available = view.plotWidth - LEGEND_INSET * 2 - LEGEND_PLATE_PADDING * 2

    // Measured before anything is drawn, because the plate behind the block has to be sized to the
    // block and a plate cannot be drawn after the text it is supposed to sit behind.
    val lines = mutableListOf<TextLayoutResult>()

    // The head line is fitted rather than truncated. Four prices at four decimals on a narrow
    // phone will not fit at any spacing, and «O 2571.2  H 2575.7  L 2570.1  C 2…» is the one
    // number a reader came for, cut off. So the separator tightens, then the labels go, and only
    // then does it fall back to the close alone — which is still true and still useful.
    val headColour = axisStyle(if (bar.up) palette.up else palette.down)
    val heads = listOf(
        "O ${formatPrice(bar.o, decimals)}   H ${formatPrice(bar.h, decimals)}   " +
            "L ${formatPrice(bar.l, decimals)}   C ${formatPrice(bar.c, decimals)}",
        "O ${formatPrice(bar.o, decimals)} H ${formatPrice(bar.h, decimals)} " +
            "L ${formatPrice(bar.l, decimals)} C ${formatPrice(bar.c, decimals)}",
        "${formatPrice(bar.o, decimals)} ${formatPrice(bar.h, decimals)} " +
            "${formatPrice(bar.l, decimals)} ${formatPrice(bar.c, decimals)}",
        "C ${formatPrice(bar.c, decimals)}",
    )
    lines += heads.map { measurer.measure(it, headColour) }
        .let { measured -> measured.firstOrNull { it.size.width <= available } ?: measured.last() }

    // The legend may take a quarter of the plot and no more. On a full-height chart that is every
    // row it wants; on a card two hundred pixels tall it is the OHLC line and one overlay, which is
    // the difference between a legend and a chart with writing over it.
    val budget = view.plotHeight * LEGEND_BUDGET
    val named = decoration.overlays.filter { !it.label.isNullOrBlank() }
    var height = lines[0].size.height.toFloat()
    var drawn = 0
    for (overlay in named.take(LEGEND_LINES)) {
        val value = overlay.values[index]
        val text = overlay.label + (value?.let { "  " + formatPrice(it, decimals) } ?: "  —")
        val line = measurer.measure(text, axisStyle(Color(overlay.colour)))
        if (height + LEGEND_GAP + line.size.height > budget) break
        lines += line
        height += LEGEND_GAP + line.size.height
        drawn++
    }
    if (named.size > drawn) {
        val more = measurer.measure("+${named.size - drawn}", axisStyle(palette.text))
        if (height + LEGEND_GAP + more.size.height <= budget) {
            lines += more
            height += LEGEND_GAP + more.size.height
        }
    }

    // The plate. Without it the legend is drawn *over* the candles, the moving averages and the
    // dashed levels, and every stroke that crosses a glyph takes a bite out of it — which is how a
    // five-line legend on a busy chart stops being readable at all. It is the stage colour rather
    // than black so it disappears into the chart on either theme, and it is not opaque, because the
    // reader is entitled to see roughly what is behind the writing.
    val width = lines.maxOf { it.size.width }.toFloat()
    drawRoundRect(
        color = palette.stage,
        topLeft = Offset(LEGEND_INSET, LEGEND_INSET),
        size = Size(
            width + LEGEND_PLATE_PADDING * 2,
            height + LEGEND_PLATE_PADDING * 2,
        ),
        cornerRadius = CornerRadius(LEGEND_PLATE_RADIUS, LEGEND_PLATE_RADIUS),
        alpha = LEGEND_PLATE_ALPHA,
    )

    var y = LEGEND_INSET + LEGEND_PLATE_PADDING
    for (line in lines) {
        drawText(line, topLeft = Offset(LEGEND_INSET + LEGEND_PLATE_PADDING, y))
        y += line.size.height + LEGEND_GAP
    }
}

private fun DrawScope.drawTimeAxis(
    view: ChartViewport,
    top: Float,
    plotWidth: Float,
    type: ChartType,
    palette: ChartPalette,
    measurer: TextMeasurer,
) {
    val labels = TIME_LABELS
    // Where the previous label ended, so a short series does not print "#1#5 #10#14#19" on top of
    // itself. Skipping a label is better than overlapping one: five collided labels say nothing,
    // three spaced ones say when.
    var occupiedUntil = -Float.MAX_VALUE
    for (step in 0 until labels) {
        val index = view.firstVisible + (view.visibleCount - 1) * step / max(1, labels - 1)
        if (index > view.lastVisible) continue
        // A price-driven type has no clock, so its axis is numbered by bar. Printing a date there
        // would be a fabricated one — Renko bars carry synthetic timestamps.
        val text = if (type.isTimeBased) formatTime(view.series.time[index]) else "#${index + 1}"
        val label = measurer.measure(text, axisStyle(palette.text))
        val x = (view.xOf(index) - label.size.width / 2).coerceIn(0f, plotWidth - label.size.width)
        if (x < occupiedUntil) continue
        drawText(label, topLeft = Offset(x, top + AXIS_PADDING))
        occupiedUntil = x + label.size.width + LABEL_GAP
    }
}

// ---------------------------------------------------------------------------- overlays

/**
 * One horizontal level, with its label at the left end.
 *
 * The label goes left because the right of the plot is where price is, and a row of labels stacked
 * against the live edge is a row of labels over the bars a reader is actually watching.
 */
private fun DrawScope.drawLevel(
    view: ChartViewport,
    level: PriceLevel,
    plotWidth: Float,
    measurer: TextMeasurer,
) {
    val y = view.yOf(level.price)
    if (y < 0f || y > view.plotHeight) return
    val colour = Color(level.colour.toInt())
    val right = if (level.extendRight) plotWidth else view.xOf(view.lastVisible)
    drawLine(
        color = colour,
        start = Offset(0f, y),
        end = Offset(right, y),
        strokeWidth = HAIRLINE,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF)),
    )
    val text = level.label ?: return
    val measured = measurer.measure(text, axisStyle(colour))
    drawText(measured, topLeft = Offset(AXIS_PADDING, y - measured.size.height - 1f))
}

/**
 * One marker, clear of the bar it belongs to.
 *
 * Offset above the high or below the low rather than drawn at the price, because a marker on the
 * high hides the high — and on a swing study the high is the thing being pointed at.
 */
private fun DrawScope.drawMarker(view: ChartViewport, marker: ChartMarker, density: Float) {
    val x = view.xOfTime(marker.time)
    if (x < 0f || x > view.plotWidth) return
    val clearance = MARKER_CLEARANCE * density
    val size = MARKER_SIZE * density
    val anchor = view.yOf(marker.price) + if (marker.above) -clearance else clearance
    val colour = Color(marker.colour.toInt())
    when (marker.glyph) {
        MarkerGlyph.CIRCLE -> drawCircle(colour, size / 2, Offset(x, anchor))
        MarkerGlyph.ARROW_UP, MarkerGlyph.ARROW_DOWN -> {
            val pointsDown = marker.glyph == MarkerGlyph.ARROW_DOWN
            val tip = if (pointsDown) anchor + size / 2 else anchor - size / 2
            val base = if (pointsDown) anchor - size / 2 else anchor + size / 2
            val triangle = Path().apply {
                moveTo(x, tip)
                lineTo(x - size / 2, base)
                lineTo(x + size / 2, base)
                close()
            }
            drawPath(triangle, colour)
        }
    }
}

private fun DrawScope.drawSignal(
    view: ChartViewport,
    signal: SignalOverlay,
    plotWidth: Float,
    palette: ChartPalette,
    measurer: TextMeasurer,
) {
    val entryY = view.yOf(signal.entry)
    // The band from entry to stop is the money at risk, and the band from entry to target is the
    // money on offer. Drawn as areas because their relative size is the whole judgement.
    signal.stopLoss?.let { stop ->
        val stopY = view.yOf(stop)
        drawRect(
            color = palette.down.copy(alpha = ZONE_ALPHA),
            topLeft = Offset(0f, min(entryY, stopY)),
            size = Size(plotWidth, abs(stopY - entryY)),
        )
        drawDashedLevel(stopY, plotWidth, palette.down)
        drawLevelLabel(signal.stopLabel, stopY, palette.down, measurer)
    }
    signal.takeProfits.firstOrNull()?.let { target ->
        val targetY = view.yOf(target)
        drawRect(
            color = palette.up.copy(alpha = ZONE_ALPHA),
            topLeft = Offset(0f, min(entryY, targetY)),
            size = Size(plotWidth, abs(targetY - entryY)),
        )
    }
    signal.takeProfits.forEachIndexed { index, price ->
        val y = view.yOf(price)
        drawDashedLevel(y, plotWidth, palette.up)
        drawLevelLabel(signal.targetLabels.getOrNull(index), y, palette.up, measurer)
    }
    drawLine(
        color = palette.crosshair,
        start = Offset(0f, entryY),
        end = Offset(plotWidth, entryY),
        strokeWidth = LINE_WIDTH,
    )
    drawLevelLabel(signal.entryLabel, entryY, palette.crosshair, measurer)
}

/**
 * A word above its line, at the left edge.
 *
 * Above rather than on: a label drawn across a price line is a label with a rule through it, which
 * is the mistake the drawing tools had to be fixed for. Skipped entirely when the line is off the
 * plot, so a target far above the visible range does not leave its name floating at the top.
 */
private fun DrawScope.drawLevelLabel(
    text: String?,
    y: Float,
    colour: Color,
    measurer: TextMeasurer,
) {
    if (text.isNullOrBlank()) return
    val measured = measurer.measure(text, axisStyle(colour))
    val top = y - measured.size.height - 1f
    if (top < 0f || y > size.height) return
    drawText(measured, topLeft = Offset(AXIS_PADDING, top))
}

private fun DrawScope.drawDashedLevel(y: Float, width: Float, colour: Color) {
    drawLine(
        color = colour,
        start = Offset(0f, y),
        end = Offset(width, y),
        strokeWidth = HAIRLINE,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF)),
    )
}

/**
 * The crosshair, and the two labels that make it readable.
 *
 * Snapped to a bar in x and free in y: the reader is asking "what happened at this bar", and a
 * crosshair between two bars answers a question about a bar that does not exist.
 *
 * The price is tagged in the axis gutter and the time under the plot, rather than being printed in
 * a corner. A crosshair whose value is written somewhere else makes the reader look away from the
 * place they are pointing at — and the OHLC readout, which used to live here, is now the legend's
 * job and follows the same bar.
 *
 * The vertical rule runs the full height, panes included, so a turn in the price and the reading in
 * the oscillator below it are measured against the same bar.
 */
private fun DrawScope.drawCrosshair(
    view: ChartViewport,
    crosshair: Crosshair,
    plotWidth: Float,
    axisWidth: Float,
    fullHeight: Float,
    palette: ChartPalette,
    measurer: TextMeasurer,
    decoration: ChartDecoration,
) {
    val index = crosshair.index.coerceIn(view.firstVisible, view.lastVisible)
    val bar = view.series[index]
    val x = view.xOf(index)
    val y = view.yOf(crosshair.price)
    val dash = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF))
    drawLine(palette.crosshair, Offset(x, 0f), Offset(x, fullHeight), HAIRLINE, pathEffect = dash)
    drawLine(palette.crosshair, Offset(0f, y), Offset(plotWidth, y), HAIRLINE, pathEffect = dash)

    if (!decoration.showAxes) return
    drawAxisTag(
        text = formatPrice(crosshair.price, decimalsFor(bar.c)),
        y = y,
        plotWidth = plotWidth,
        axisWidth = axisWidth,
        fill = palette.crosshair,
        textColour = palette.stage,
        measurer = measurer,
        plotHeight = view.plotHeight,
    )
    if (!decoration.showTimeAxis) return
    // The time under the finger, centred on the rule and held inside the canvas. Without the clamp
    // the label at either end of a scrolled chart hangs half off the edge.
    val stamp = measurer.measure(formatTime(bar.t), axisStyle(palette.stage))
    val width = stamp.size.width + TAG_PADDING * 4
    val left = (x - width / 2).coerceIn(0f, max(0f, plotWidth - width))
    drawRoundRect(
        color = palette.crosshair,
        topLeft = Offset(left, fullHeight + 1f),
        size = Size(width, stamp.size.height + TAG_PADDING * 2),
        cornerRadius = CornerRadius(TAG_RADIUS, TAG_RADIUS),
    )
    drawText(stamp, topLeft = Offset(left + TAG_PADDING * 2, fullHeight + 1f + TAG_PADDING))
}

// ---------------------------------------------------------------------------- helpers

/** Where a touch lands in chart space. */
private fun ChartViewport.crosshairAt(position: Offset): Crosshair =
    Crosshair(index = indexAt(position.x), price = priceAt(position.y))

/**
 * Where a touch lands as a drawing point, snapped to a bar's OHLC when the magnet is on.
 *
 * The snap is against the series being displayed, not the raw one. On a price-driven chart type
 * that means it snaps to a Renko brick or a Kagi turn, which is the right answer there and is what
 * every terminal does — but it is worth knowing that such a point is anchored to a synthetic
 * timestamp, and will not survive a switch back to candles unchanged.
 */
private fun ChartViewport.chartPointAt(
    position: Offset,
    series: CandleSeries,
    magnet: Boolean,
): ChartPoint {
    val point = ChartPoint(timeAt(position.x), priceAt(position.y))
    return if (magnet) DrawingActions.snap(point, series) else point
}

/**
 * The style every label on this canvas is measured in.
 *
 * Left-to-right explicitly. The measurer takes its direction from the composition, so on a Persian
 * device an axis label was laid out as a right-to-left paragraph — and a leading minus sign is a
 * neutral character, so a MACD pane's lower bound printed as `4.92-`. The digits are Latin market
 * figures and they read in one direction only.
 */
private fun axisStyle(colour: Color) = TextStyle(
    color = colour,
    fontSize = AXIS_TEXT_SIZE,
    textDirection = TextDirection.Ltr,
)

/**
 * How many decimals a price on this chart deserves.
 *
 * Taken from the magnitude rather than fixed, for the same reason the search rows do it: gold at
 * 2,643.18 and a memecoin at 0.000018 cannot share a format, and rounding either to the other's
 * precision makes the number wrong rather than merely ugly.
 */
fun decimalsFor(price: Double): Int {
    val magnitude = abs(price)
    return when {
        magnitude >= 1_000 -> 1
        magnitude >= 1 -> 2
        magnitude >= 0.01 -> 4
        else -> 6
    }
}

/**
 * Latin digits, always.
 *
 * `Locale.US` is not decoration. Without it `String.format` follows the device locale, and on a
 * Persian phone — which is this app's default — a price comes out as «۲٬۵۹۲٫۶»: Persian digits and
 * a Persian decimal separator, on the axis of a chart. Market figures stay Latin and comparable
 * down a column; only prose counts are written in Persian digits.
 */
fun formatPrice(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals}f", value)

/** `HH:mm` in UTC, which is what a bar's open time is in. Latin digits for the same reason. */
internal fun formatTime(epochSeconds: Long): String {
    val minutes = (epochSeconds / 60) % 60
    val hours = (epochSeconds / 3600) % 24
    return String.format(Locale.US, "%02d:%02d", hours, minutes)
}

/** Right-hand price axis width. Enough for six digits and a decimal point at 10sp. */
private val AXIS_WIDTH: Dp = 58.dp

/** Bottom time axis height. */
private val TIME_HEIGHT: Dp = 20.dp

/** The volume band, as a share of the canvas. */
/**
 * How much of the price pane the tallest visible volume bar reaches.
 *
 * A fifth. Enough to compare one bar against its neighbours, short enough that the candles above it
 * are never in doubt about which series the chart is about.
 */
private const val VOLUME_INLINE = 0.20f

/**
 * The most of the canvas indicator panes may take between them.
 *
 * Half. Past that the candles stop being the subject of the picture, and a reader with four
 * oscillators switched on has usually forgotten they switched on the fourth.
 */
private const val PANE_BUDGET = 0.5f


/** How far the last-price tag's rounded corner is cut. */
private const val TAG_RADIUS = 3f

/** How solid the last-price rule is. Present, and never competing with the candles. */
private const val LAST_PRICE_ALPHA = 0.75f

/** Rows the corner legend will print before it starts counting instead. */
private const val LEGEND_LINES = 4

/** Between two rows of the corner legend. */
private const val LEGEND_GAP = 2f

/**
 * How far the corner legend sits from the canvas edge.
 *
 * Wider than the axis padding: the legend's first glyph is a letter and the axis's is a digit, and
 * four pixels that read as tight beside a number read as clipped beside an «O».
 */
private const val LEGEND_INSET = 10f

/** The share of the plot's height the corner legend may occupy before it stops adding rows. */
private const val LEGEND_BUDGET = 0.25f

/** Breathing room between the legend's text and the edge of the plate behind it. */
private const val LEGEND_PLATE_PADDING = 5f

/** How much of the chart shows through the legend's plate. */
private const val LEGEND_PLATE_ALPHA = 0.82f

/** The plate's corner. Softer than a tag's, because it is a panel rather than a marker. */
private const val LEGEND_PLATE_RADIUS = 6f

/** Padding inside the last-price and crosshair tags. */
private const val TAG_PADDING = 3f

/** Breathing room above and below a pane's extremes, so the line never touches the lid. */
private const val PANE_PADDING = 0.06

private const val GRID_ROWS = 5
private const val GRID_COLUMNS = 5
private const val TIME_LABELS = 5

/** Minimum clear space between two time labels before the later one is dropped. */
private const val LABEL_GAP = 12f

private const val HAIRLINE = 1f
private const val WICK_WIDTH = 1.4f
private const val LINE_WIDTH = 2f
private const val AXIS_PADDING = 4f
private val AXIS_TEXT_SIZE = 9.sp

private const val GRID_ALPHA = 0.35f
private const val AREA_ALPHA = 0.16f
private const val VOLUME_ALPHA = 0.30f
private const val ZONE_ALPHA = 0.12f

private const val DASH_ON = 6f
private const val DASH_OFF = 6f

/** How far a marker sits from the bar's high or low, so it points rather than covers. */
private const val MARKER_CLEARANCE = 8f
private const val MARKER_SIZE = 7f

/** Below this a pinch is a drag with slightly uneven fingers, not an intent to zoom. */
private const val ZOOM_DEADZONE = 0.01f
