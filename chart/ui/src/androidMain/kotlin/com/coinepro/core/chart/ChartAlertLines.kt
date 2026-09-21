package com.coinepro.core.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.coinepro.core.designsystem.R as DesignR

/**
 * A price alert as the chart draws it: one line, and a handle you can drag.
 *
 * ### Why the chart has to draw these at all
 *
 * An alert set from this chart was, until run Ω2, invisible on it. The reader picked a price, agreed
 * to it, and the chart went back to looking exactly as it had — so the one thing they had told the
 * app to watch was the one thing the app would not show them. They could not see whether the level
 * they had chosen was the level they meant, and the only way to move it was to delete it and set
 * another one from a list on a different screen.
 *
 * ### Why the handle is in the gutter and not on the line
 *
 * Because a one-pixel dashed line across a chart whose every other gesture is a pan is not a drag
 * target — a finger that misses it by three points scrolls the chart instead, which is the worst
 * possible outcome for a control that moves a level a reader is relying on. The handle is the tag on
 * the price axis, which is twenty-two points tall, sits where the price is already written, and is
 * exactly where every phone terminal puts it. The line follows the handle live.
 *
 * Nothing is saved until the finger lifts, and a drag that ends where it started changes nothing.
 */
@Composable
internal fun PriceAxisAlertLines(
    /** Where the plot and its gutters are. Null until the first draw has published a frame. */
    frame: PlotFrame?,
    /** The viewport as the last draw resolved it, for the price↔pixel mapping. */
    view: ChartViewport?,
    /** The alerts on this symbol. Empty draws nothing at all. */
    alerts: List<ChartAlertLine>,
    palette: ChartPalette,
    /** The one accent this app acts in — an alert is the reader's own mark, like a drawing. */
    accent: Color,
    /**
     * A dragged alert's new price, once the finger lifts.
     *
     * Null makes every handle read-only, which is what a thumbnail and the share renderer want: the
     * lines still say where the alerts are and nothing can be moved by a stray touch.
     */
    onMoveAlert: ((id: String, price: Double) -> Unit)? = null,
    /** A tick as the handle takes hold, and another as it lands. */
    onGrab: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (frame == null || view == null || alerts.isEmpty() || view.plotHeight <= 0f) return
    val density = LocalDensity.current
    // Which handle is under a finger and how far it has travelled, in canvas pixels. One at a time:
    // two alerts being dragged at once is not a gesture anybody makes and supporting it would mean
    // a map where a pair does.
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragBy by remember { mutableStateOf(0f) }
    val dash = remember(density) { with(density) { floatArrayOf(DASH_ON_DP.toPx(), DASH_OFF_DP.toPx()) } }

    Box(modifier = modifier.fillMaxSize()) {
        // The lines, on a canvas of their own. It reads `dragging`/`dragBy`, which is why it is not
        // in the chart's main draw lambda: a handle moving must not re-walk the price range or
        // re-issue three hundred candles. Same two-layer split the crosshair uses.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = crispStroke(with(density) { LINE_DP.toPx() })
            val effect = PathEffect.dashPathEffect(dash, 0f)
            for (alert in alerts) {
                val base = view.yOf(alert.price)
                val y = if (alert.id == dragging) base + dragBy else base
                if (y < 0f || y > view.plotHeight) continue
                drawLine(
                    // Full strength under a finger, the resting alpha while armed, and fainter still
                    // for a one-shot that has already fired and is watching nothing.
                    color = accent.copy(
                        alpha = when {
                            alert.id == dragging -> 1f
                            alert.armed -> LINE_ALPHA
                            else -> SPENT_ALPHA
                        },
                    ),
                    start = Offset(frame.left, strokeCentre(y, stroke)),
                    end = Offset(frame.right, strokeCentre(y, stroke)),
                    strokeWidth = stroke,
                    pathEffect = effect,
                )
            }
        }
        for (alert in alerts) {
            val base = view.yOf(alert.price)
            val y = if (alert.id == dragging) base + dragBy else base
            if (y < 0f || y > view.plotHeight) continue
            val price = if (alert.id == dragging) view.priceAt(y) else alert.price
            AlertHandle(
                frame = frame,
                y = y,
                label = view.axisText(price),
                accent = accent,
                ink = palette.stage,
                draggable = onMoveAlert != null,
                onDragStart = {
                    dragging = alert.id
                    dragBy = 0f
                    onGrab?.invoke()
                },
                onDragBy = { delta ->
                    // Clamped to the plot, because an alert dragged off the top of the chart would be
                    // set at a price the axis cannot even print.
                    dragBy = (dragBy + delta).coerceIn(-base, view.plotHeight - base)
                },
                onDragEnd = {
                    val moved = view.priceAt(base + dragBy)
                    dragging = null
                    dragBy = 0f
                    if (moved.isFinite() && moved != alert.price) {
                        onGrab?.invoke()
                        onMoveAlert?.invoke(alert.id, moved)
                    }
                },
            )
        }
    }
}

/** One alert's tag on the price axis: a bell, the price, and the whole of the drag target. */
@Composable
private fun AlertHandle(
    frame: PlotFrame,
    y: Float,
    label: String,
    accent: Color,
    ink: Color,
    draggable: Boolean,
    onDragStart: () -> Unit,
    onDragBy: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val density = LocalDensity.current
    val height = with(density) { HANDLE_HEIGHT_DP.toPx() }
    val top = (y - height / 2f).coerceAtLeast(0f)
    val left = if (frame.tagsOnRight) {
        frame.right - with(density) { HANDLE_WIDTH_DP.toPx() } + with(density) { HANDLE_BLEED_DP.toPx() }
    } else {
        frame.left - with(density) { HANDLE_BLEED_DP.toPx() }
    }
    val state = rememberDraggableState { delta -> onDragBy(delta) }
    Row(
        modifier = Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .height(HANDLE_HEIGHT_DP)
            .clip(RoundedCornerShape(HANDLE_RADIUS_DP))
            .background(accent)
            .then(
                if (draggable) {
                    Modifier.draggable(
                        state = state,
                        orientation = Orientation.Vertical,
                        onDragStarted = { onDragStart() },
                        onDragStopped = { onDragEnd() },
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = HANDLE_PADDING_DP)
            .semantics { contentDescription = HANDLE_LABEL },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HANDLE_PADDING_DP),
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.tv_bell),
            contentDescription = null,
            tint = ink,
            modifier = Modifier.size(GLYPH_DP),
        )
        Text(text = label, color = ink, fontSize = axisFontSizeSp(isPriceAxis = true).sp)
    }
}

/** A dash short enough to read as «watching», not as a level the chart itself drew. */
private val DASH_ON_DP = 3.dp
private val DASH_OFF_DP = 3.dp
private val LINE_DP = 1.dp

/** How present a line is when nothing is being dragged: findable, never competing with a candle. */
private const val LINE_ALPHA = 0.7f

/** A fired one-shot: still where the reader put it, visibly no longer watching. */
private const val SPENT_ALPHA = 0.32f

/** Tall enough to drag with a thumb, short enough that two alerts ten points apart both show. */
private val HANDLE_HEIGHT_DP = 22.dp
private val HANDLE_WIDTH_DP = 84.dp
private val HANDLE_BLEED_DP = 2.dp
private val HANDLE_RADIUS_DP = 4.dp
private val HANDLE_PADDING_DP = 4.dp
private val GLYPH_DP = 12.dp

/** What TalkBack says. It names what dragging does, which is the only thing worth saying. */
private const val HANDLE_LABEL = "کشیدن برای جابه‌جا کردن هشدار"
