package com.coinepro.core.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Trading on the chart (5.17.0): a position's entry, stop and target and every working order, as
 * lines across the plot with a tag on the plot's far edge. The stop, the target and a working
 * order are dragged by their tag to a new price — TradingView's «drag to modify» — and the chart
 * reports `(id, price)`; what that means for the order is the host's business.
 *
 * Same geometry as the alert lines beside them ([PriceAxisAlertLines]), with the tag on the other
 * edge of the plot so the two never sit on one another.
 */
@Composable
internal fun PriceAxisOrderLines(
    frame: PlotFrame?,
    view: ChartViewport?,
    lines: List<ChartOrderLine>,
    palette: ChartPalette,
    accent: Color,
    onMove: ((id: String, price: Double) -> Unit)? = null,
    onGrab: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (frame == null || view == null || lines.isEmpty() || view.plotHeight <= 0f) return
    val density = LocalDensity.current
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragBy by remember { mutableStateOf(0f) }
    val dash = remember(density) { with(density) { floatArrayOf(DASH_DP.toPx(), DASH_DP.toPx()) } }

    fun colourOf(kind: OrderLineKind): Color = when (kind) {
        OrderLineKind.ENTRY -> accent
        OrderLineKind.STOP -> palette.down
        OrderLineKind.TARGET -> palette.up
        OrderLineKind.WORKING -> WORKING
    }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = crispStroke(with(density) { LINE_DP.toPx() })
            for (line in lines) {
                val base = view.yOf(line.price)
                val y = if (line.id == dragging) base + dragBy else base
                if (y < 0f || y > view.plotHeight) continue
                drawLine(
                    color = colourOf(line.kind).copy(alpha = if (line.id == dragging) 1f else LINE_ALPHA),
                    start = Offset(frame.left, strokeCentre(y, stroke)),
                    end = Offset(frame.right, strokeCentre(y, stroke)),
                    strokeWidth = stroke,
                    pathEffect = if (line.kind == OrderLineKind.ENTRY) null else PathEffect.dashPathEffect(dash, 0f),
                )
            }
        }
        for (line in lines) {
            val base = view.yOf(line.price)
            val y = if (line.id == dragging) base + dragBy else base
            if (y < 0f || y > view.plotHeight) continue
            val price = if (line.id == dragging) view.priceAt(y) else line.price
            val height = with(density) { TAG_HEIGHT_DP.toPx() }
            val top = (y - height / 2f).coerceAtLeast(0f)
            // The far edge from the price axis, so an order tag never covers an alert tag.
            val left = if (frame.tagsOnRight) frame.left + with(density) { TAG_INSET_DP.toPx() } else {
                frame.right - with(density) { (TAG_WIDTH_DP + TAG_INSET_DP).toPx() }
            }
            val state = rememberDraggableState { delta ->
                dragBy = (dragBy + delta).coerceIn(-base, view.plotHeight - base)
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
                    .height(TAG_HEIGHT_DP)
                    .clip(RoundedCornerShape(TAG_RADIUS_DP))
                    .background(colourOf(line.kind))
                    .then(
                        if (line.movable && onMove != null) {
                            Modifier.draggable(
                                state = state,
                                orientation = Orientation.Vertical,
                                onDragStarted = {
                                    dragging = line.id
                                    dragBy = 0f
                                    onGrab?.invoke()
                                },
                                onDragStopped = {
                                    val moved = view.priceAt(base + dragBy)
                                    dragging = null
                                    dragBy = 0f
                                    if (moved.isFinite() && moved > 0.0 && moved != line.price) {
                                        onGrab?.invoke()
                                        onMove(line.id, moved)
                                    }
                                },
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = TAG_PADDING_DP)
                    .semantics { contentDescription = "order-line-" + line.id },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = line.label + " " + view.axisText(price),
                    color = palette.stage,
                    fontSize = axisFontSizeSp(isPriceAxis = true).sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private val DASH_DP = 4.dp
private val LINE_DP = 1.dp
private const val LINE_ALPHA = 0.85f
private val TAG_HEIGHT_DP = 20.dp
private val TAG_WIDTH_DP = 120.dp
private val TAG_INSET_DP = 4.dp
private val TAG_RADIUS_DP = 4.dp
private val TAG_PADDING_DP = 4.dp

/** A working order's amber: neither side's colour, because it has not traded yet. */
private val WORKING = Color(0xFFF59E0B)
