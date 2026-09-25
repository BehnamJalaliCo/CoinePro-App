package com.coinepro.core.designsystem

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * A row that a **mouse** can drag, as well as a finger.
 *
 * Compose's own scrollables refuse a drag that starts with a mouse button: on a desktop the wheel
 * is supposed to scroll, and a pressed button is supposed to select. That is right for a page and
 * wrong for a row of chips, because a vertical wheel does not move a horizontal row and nobody
 * knows to hold Shift. On pro-chart.com every chip row, timeframe strip and tab strip was therefore
 * a row whose end could not be reached (the owner's report on 5.18.1: «اسکرول به بالا و پایین داره
 * ولی به چپ و راست نداره»).
 *
 * This adds the one missing gesture: a press with the mouse that travels past the touch slop pans
 * the row, and the move is consumed so the chip under the pointer does not also take a click. A
 * finger still goes through the scrollable's own drag and fling; nothing here sees it.
 *
 * It is placed *outside* the scroll, on the viewport, so the pointer's movement is measured against
 * something that does not move with the content it is moving.
 */
fun Modifier.mouseDragScroll(state: ScrollableState): Modifier = this then MouseDragScrollElement(state)

/*
 * A modifier node rather than `composed {}` (5.18.2). The first version read the layout direction
 * and the touch slop through `composed`, which puts a group into the caller's composition; on the
 * web build that was enough to shift the chart screen's own remembered slots and crash it on its
 * first recomposition. A node reads both from the node tree instead and adds nothing to composition.
 */
private data class MouseDragScrollElement(val state: ScrollableState) : ModifierNodeElement<MouseDragScrollNode>() {
    override fun create() = MouseDragScrollNode(state)
    override fun update(node: MouseDragScrollNode) {
        node.update(state)
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "mouseDragScroll"
    }
}

private class MouseDragScrollNode(private var state: ScrollableState) :
    DelegatingNode(), PointerInputModifierNode, CompositionLocalConsumerModifierNode {

    private val input = delegate(
        SuspendingPointerInputModifierNode {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                if (down.type != PointerType.Mouse) return@awaitEachGesture
                val rtl = currentValueOf(LocalLayoutDirection) == LayoutDirection.Rtl
                val slop = viewConfiguration.touchSlop
                var travelled = 0f
                var panning = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val dx = change.positionChange().x
                    if (!panning) {
                        travelled += dx
                        if (abs(travelled) > slop) panning = true
                    }
                    if (panning) {
                        // The same sign the scrollable gives a finger: in a right-to-left row the
                        // start is on the right, so dragging right reveals what lies further along.
                        state.dispatchRawDelta(if (rtl) dx else -dx)
                        change.consume()
                    }
                }
            }
        },
    )

    fun update(newState: ScrollableState) {
        if (newState != state) {
            state = newState
            input.resetPointerInputHandler()
        }
    }

    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        input.onPointerEvent(pointerEvent, pass, bounds)
    }

    override fun onCancelPointerInput() {
        input.onCancelPointerInput()
    }
}

/** [horizontalScroll] that a mouse can drag too. See [mouseDragScroll]. */
fun Modifier.coineProHorizontalScroll(state: ScrollState): Modifier =
    mouseDragScroll(state).horizontalScroll(state)

/**
 * [LazyRow] that a mouse can drag too. Same parameters, same defaults; see [mouseDragScroll].
 */
@Composable
fun CoineProLazyRow(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    reverseLayout: Boolean = false,
    horizontalArrangement: Arrangement.Horizontal =
        if (!reverseLayout) Arrangement.Start else Arrangement.End,
    verticalAlignment: Alignment.Vertical = Alignment.Top,
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    userScrollEnabled: Boolean = true,
    content: LazyListScope.() -> Unit,
) {
    LazyRow(
        modifier = if (userScrollEnabled) modifier.mouseDragScroll(state) else modifier,
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = verticalAlignment,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        content = content,
    )
}
