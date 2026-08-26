package com.coinepro.core.chart

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.coinepro.core.designsystem.CoineProColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import androidx.compose.foundation.Canvas

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
                            .pointerInput(display) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    if (abs(zoom - 1f) > ZOOM_DEADZONE) viewport = viewport.zoomedBy(zoom)
                                    if (abs(pan.x) > 0f) viewport = viewport.pannedBy(pan.x)
                                }
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
                            .pointerInput(display) {
                                detectTapGestures(onDoubleTap = { viewport = viewport.atOffset(0) })
                            }
                    },
                ),
        ) {
            val plotWidth = max(0f, size.width - if (decoration.showAxes) axisWidth else 0f)
            val volumeHeight = if (decoration.showVolume && series.hasVolume) {
                size.height * VOLUME_RATIO
            } else {
                0f
            }
            val timeAxis = if (decoration.showAxes) timeHeight else 0f
            val plotHeight = max(0f, size.height - volumeHeight - timeAxis)

            val view = viewport
                .sized(plotWidth, plotHeight)
                .copy(includedPrices = decoration.signal?.levels().orEmpty())
            if (view.visibleCount == 0) return@Canvas

            if (decoration.showAxes) drawGrid(view, plotWidth, palette, measurer)
            // The setup goes *under* the price. It is context for the bars, and drawn over them it
            // tints every candle it covers — which on a full-height risk band is most of them.
            decoration.signal?.let { drawSignal(view, it, plotWidth, palette) }
            when {
                type.isLine -> drawLineSeries(view, palette, filled = type == ChartType.AREA)
                type == ChartType.BARS -> drawOhlcBars(view, palette)
                else -> drawCandles(view, palette, hollow = type == ChartType.HOLLOW)
            }
            decoration.overlays.forEach { drawOverlay(view, it, density.density) }
            if (volumeHeight > 0) drawVolume(view, plotHeight, volumeHeight, palette)
            if (decoration.showAxes) {
                drawPriceAxis(view, plotWidth, palette, measurer)
                drawTimeAxis(view, plotHeight + volumeHeight, plotWidth, type, palette, measurer)
            }
            crosshair?.let { drawCrosshair(view, it, plotWidth, palette, measurer) }
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
            started = false
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
    drawPath(path, color = Color(overlay.colour), style = Stroke(width = overlay.widthDp * density))
}

// ---------------------------------------------------------------------------- panes

private fun DrawScope.drawVolume(
    view: ChartViewport,
    top: Float,
    height: Float,
    palette: ChartPalette,
) {
    var peak = 0.0
    for (index in view.firstVisible..view.lastVisible) {
        if (view.series.volume[index] > peak) peak = view.series.volume[index]
    }
    if (peak <= 0.0) return
    val body = view.bodyWidth
    translate(top = top) {
        for (index in view.firstVisible..view.lastVisible) {
            val bar = view.series[index]
            val barHeight = (view.series.volume[index] / peak * height).toFloat()
            drawRect(
                color = (if (bar.up) palette.up else palette.down).copy(alpha = VOLUME_ALPHA),
                topLeft = Offset(view.xOf(index) - body / 2, height - barHeight),
                size = Size(body, barHeight),
            )
        }
    }
}

// ---------------------------------------------------------------------------- axes

private fun DrawScope.drawGrid(
    view: ChartViewport,
    plotWidth: Float,
    palette: ChartPalette,
    measurer: TextMeasurer,
) {
    val grid = palette.grid.copy(alpha = GRID_ALPHA)
    for (step in 0..GRID_ROWS) {
        val y = view.plotHeight * step / GRID_ROWS
        drawLine(grid, Offset(0f, y), Offset(plotWidth, y), HAIRLINE)
    }
    for (step in 0..GRID_COLUMNS) {
        val x = plotWidth * step / GRID_COLUMNS
        drawLine(grid, Offset(x, 0f), Offset(x, view.plotHeight), HAIRLINE)
    }
}

private fun DrawScope.drawPriceAxis(
    view: ChartViewport,
    plotWidth: Float,
    palette: ChartPalette,
    measurer: TextMeasurer,
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
        drawText(textLayoutResult = label, topLeft = Offset(plotWidth + AXIS_PADDING, top))
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

private fun DrawScope.drawSignal(
    view: ChartViewport,
    signal: SignalOverlay,
    plotWidth: Float,
    palette: ChartPalette,
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
    }
    signal.takeProfits.firstOrNull()?.let { target ->
        val targetY = view.yOf(target)
        drawRect(
            color = palette.up.copy(alpha = ZONE_ALPHA),
            topLeft = Offset(0f, min(entryY, targetY)),
            size = Size(plotWidth, abs(targetY - entryY)),
        )
    }
    signal.takeProfits.forEach { drawDashedLevel(view.yOf(it), plotWidth, palette.up) }
    drawLine(
        color = palette.crosshair,
        start = Offset(0f, entryY),
        end = Offset(plotWidth, entryY),
        strokeWidth = LINE_WIDTH,
    )
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

private fun DrawScope.drawCrosshair(
    view: ChartViewport,
    crosshair: Crosshair,
    plotWidth: Float,
    palette: ChartPalette,
    measurer: TextMeasurer,
) {
    val index = crosshair.index.coerceIn(view.firstVisible, view.lastVisible)
    val bar = view.series[index]
    val x = view.xOf(index)
    // Snapped to the bar in x, free in y. The reader is asking "what happened at this bar", and a
    // crosshair between two bars answers a question about a bar that does not exist.
    val y = view.yOf(crosshair.price)
    val dash = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF))
    drawLine(palette.crosshair, Offset(x, 0f), Offset(x, view.plotHeight), HAIRLINE, pathEffect = dash)
    drawLine(palette.crosshair, Offset(0f, y), Offset(plotWidth, y), HAIRLINE, pathEffect = dash)

    val decimals = decimalsFor(bar.c)
    val readout = "O ${formatPrice(bar.o, decimals)}  H ${formatPrice(bar.h, decimals)}  " +
        "L ${formatPrice(bar.l, decimals)}  C ${formatPrice(bar.c, decimals)}"
    val label = measurer.measure(readout, axisStyle(palette.text))
    drawText(label, topLeft = Offset(AXIS_PADDING, AXIS_PADDING))
}

// ---------------------------------------------------------------------------- helpers

/** Where a touch lands in chart space. */
private fun ChartViewport.crosshairAt(position: Offset): Crosshair =
    Crosshair(index = indexAt(position.x), price = priceAt(position.y))

private fun axisStyle(colour: Color) = TextStyle(color = colour, fontSize = AXIS_TEXT_SIZE)

/**
 * How many decimals a price on this chart deserves.
 *
 * Taken from the magnitude rather than fixed, for the same reason the search rows do it: gold at
 * 2,643.18 and a memecoin at 0.000018 cannot share a format, and rounding either to the other's
 * precision makes the number wrong rather than merely ugly.
 */
internal fun decimalsFor(price: Double): Int {
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
internal fun formatPrice(value: Double, decimals: Int): String =
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
private const val VOLUME_RATIO = 0.20f

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
private const val VOLUME_ALPHA = 0.45f
private const val ZONE_ALPHA = 0.12f

private const val DASH_ON = 6f
private const val DASH_OFF = 6f

/** Below this a pinch is a drag with slightly uneven fingers, not an intent to zoom. */
private const val ZOOM_DEADZONE = 0.01f
