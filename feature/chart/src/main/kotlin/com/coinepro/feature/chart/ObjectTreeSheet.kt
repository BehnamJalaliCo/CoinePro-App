package com.coinepro.feature.chart

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.coinepro.core.chart.Drawing
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.ObjectGroup
import com.coinepro.core.chart.ObjectNode
import com.coinepro.core.common.countedLabel
import com.coinepro.core.common.toPersianDigits
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSheetEmpty
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.CoineProTint
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.rememberCoineProHaptics
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The object tree: every drawing on the chart, grouped, and reachable.
 *
 * ### The problem it solves
 *
 * Everything a reader could do to a drawing went through the canvas — to lock a line they had to
 * tap it, and to tap it they had to find it. Forty objects on one instrument is an ordinary week's
 * work, and at forty the hit boxes overlap so thoroughly that the drawing underneath cannot be
 * reached at all. This list reaches every one of them in one scroll, including the ones that are
 * off-screen or buried.
 *
 * ### Where it lives
 *
 * Behind the «ترسیم‌ها» button that already exists on the chart's bar, replacing the flat list that
 * was there. It is not a new affordance and the toolbar does not grow: the button that meant "show
 * me what I have drawn" now answers the question properly.
 *
 * ### What a row does
 *
 * Tap selects — which is the whole point, since selecting is what puts the handles on the canvas —
 * and closes the sheet, because a reader who has just found their line wants to be looking at it.
 * The eye and the padlock act in place and leave the sheet open, since hiding four things and
 * locking two is six taps that must not cost six reopenings. A long press picks the row up to
 * restack it, and a swipe takes it off the chart; both are the gestures a phone reader already
 * tries on a list of their own things.
 *
 * ### Order is the canvas's order
 *
 * Topmost first inside each group — see `ObjectTree.treeOf`. A tree whose first row is not the
 * object a tap on an overlap would select teaches a false model of the chart, and every restack
 * afterwards fights it.
 */
@Composable
internal fun ObjectTreeSheetBody(
    groups: List<ObjectGroup>,
    /** The chart's own list, oldest first — z-order. Restacking is expressed against this. */
    drawings: List<Drawing>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onToggleHidden: (Long) -> Unit,
    onToggleLocked: (ObjectNode) -> Unit,
    onDelete: (Long) -> Unit,
    /** Moves a drawing to a z index. See `ObjectTree.reorder` for what the index means. */
    onReorder: (id: Long, toIndex: Int) -> Unit,
    /** Opens one drawing's own settings — colour, width, templates, and where it sits. */
    onOpenStyle: (Long) -> Unit,
) {
    if (groups.isEmpty()) {
        CoineProSheetEmpty("هنوز چیزی روی چارت نکشیده‌ای.")
        return
    }
    val total = groups.sumOf { it.nodes.size }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = TREE_MAX_HEIGHT)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = total.toPersianDigits() + " ترسیم. برای انتخاب بزنید، برای جابه‌جایی نگه دارید، برای حذف بکشید.",
            style = MaterialTheme.typography.bodySmall,
            color = CoineProColors.TextMuted,
            modifier = Modifier.padding(bottom = CoineProSpacing.One),
        )
        groups.forEach { group ->
            ObjectGroupBlock(
                group = group,
                drawings = drawings,
                selectedId = selectedId,
                onSelect = onSelect,
                onToggleHidden = onToggleHidden,
                onToggleLocked = onToggleLocked,
                onDelete = onDelete,
                onReorder = onReorder,
                onOpenStyle = onOpenStyle,
            )
        }
    }
}

/**
 * One heading and the rows under it, with the drag that restacks them.
 *
 * The drag is confined to a group, and that is not a limitation — it is what makes the gesture
 * mean something. Groups are the rail's own taxonomy and a reader dragging a Fibonacci row past a
 * heading into the trend lines has expressed nothing about z-order that they could see the result
 * of. Within a group the rows are in z-order, so dragging one up puts it in front of its
 * neighbours, which is exactly what it looks like it does.
 */
@Composable
private fun ObjectGroupBlock(
    group: ObjectGroup,
    drawings: List<Drawing>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onToggleHidden: (Long) -> Unit,
    onToggleLocked: (ObjectNode) -> Unit,
    onDelete: (Long) -> Unit,
    onReorder: (id: Long, toIndex: Int) -> Unit,
    onOpenStyle: (Long) -> Unit,
) {
    val haptics = rememberCoineProHaptics()
    val rowPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val ids = group.nodes.map(ObjectNode::id)

    Text(
        text = countedLabel(group.group.label, group.nodes.size),
        style = MaterialTheme.typography.labelSmall,
        color = CoineProColors.TextMuted,
        fontWeight = FontWeight.Normal,
        modifier = Modifier.padding(top = CoineProSpacing.One, bottom = CoineProSpacing.Half),
    )

    group.nodes.forEachIndexed { row, node ->
        val dragging = draggingId == node.id
        ObjectRow(
            node = node,
            selected = node.id == selectedId,
            dragging = dragging,
            offsetY = if (dragging) dragOffset else 0f,
            onSelect = { onSelect(node.id) },
            onToggleHidden = { onToggleHidden(node.id) },
            onToggleLocked = { onToggleLocked(node) },
            onDelete = { onDelete(node.id) },
            onOpenStyle = { onOpenStyle(node.id) },
            dragModifier = Modifier.pointerInput(node.id, ids, drawings) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        haptics.select()
                        draggingId = node.id
                        dragOffset = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount.y
                    },
                    onDragEnd = {
                        val target = rowAfterDrag(row, dragOffset, rowPx, ids.size)
                        val index = restackIndex(ids, drawings, target)
                        if (index >= 0 && target != row) onReorder(node.id, index)
                        draggingId = null
                        dragOffset = 0f
                    },
                    onDragCancel = {
                        draggingId = null
                        dragOffset = 0f
                    },
                )
            },
        )
    }
}

/**
 * One drawing, as a row that can be selected, hidden, locked, restacked or thrown away.
 *
 * ### Why the swipe is written out rather than taken from the framework
 *
 * The row already carries a long-press vertical drag for restacking, and it has to keep working
 * beside a horizontal one. A hand-rolled swipe is twenty lines, shares the row's own state, and —
 * the part that matters — snaps back rather than settling into a dismissed position: the row does
 * not disappear because the box hid it, it disappears because the drawing is gone from the chart's
 * state a frame later. A box that also latched dismissed would leave a blank gap in the list until
 * recomposition caught up.
 *
 * The swipe is refused on a locked drawing rather than accepted and then ignored. `DrawingActions`
 * would refuse the delete anyway, and a row that slides off the screen and springs back with the
 * drawing still on the chart is the app appearing to lose a gesture.
 */
@Composable
private fun ObjectRow(
    node: ObjectNode,
    selected: Boolean,
    dragging: Boolean,
    offsetY: Float,
    onSelect: () -> Unit,
    onToggleHidden: () -> Unit,
    onToggleLocked: () -> Unit,
    onDelete: () -> Unit,
    onOpenStyle: () -> Unit,
    dragModifier: Modifier,
) {
    val haptics = rememberCoineProHaptics()
    val swipePx = with(LocalDensity.current) { SWIPE_TO_DELETE.toPx() }
    var swipeX by remember(node.id) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ROW_HEIGHT)
            .zIndex(if (dragging) 1f else 0f)
            .offset { IntOffset(0, offsetY.roundToInt()) },
    ) {
        // The bin behind the row, revealed only as far as the row has actually moved. A background
        // that is always there is a red band down the side of every list.
        if (swipeX != 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CoineProShapes.small)
                    .background(CoineProTint.fill(CoineProColors.Sell, CoineProColors.Surface))
                    .padding(horizontal = CoineProSpacing.OneHalf),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(DesignR.drawable.tv_trash2),
                    contentDescription = null,
                    tint = CoineProColors.Sell,
                    modifier = Modifier.size(GLYPH),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ROW_HEIGHT)
                .offset { IntOffset(swipeX.roundToInt(), 0) }
                .clip(CoineProShapes.small)
                .background(
                    when {
                        dragging -> CoineProColors.SurfaceElevated
                        selected -> CoineProTint.fill(CoineProColors.Gold, CoineProColors.Surface)
                        else -> CoineProColors.Surface
                    },
                )
                .pointerInput(node.id, node.locked) {
                    if (node.locked) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(swipeX) >= swipePx) {
                                haptics.select()
                                onDelete()
                            }
                            swipeX = 0f
                        },
                        onDragCancel = { swipeX = 0f },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            swipeX += amount
                        },
                    )
                }
                .then(dragModifier)
                .clickable(onClick = onSelect)
                .padding(horizontal = CoineProSpacing.One),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.Half),
        ) {
            // The colour dot is the drawing's own, so a reader who colour-codes their levels can
            // find the red one without reading a word.
            Box(
                modifier = Modifier
                    .size(DOT)
                    .clip(CircleShape)
                    .background(Color(node.colour.toULong() shl COLOUR_SHIFT)),
            )
            DrawingTools[node.toolId]?.let { tool ->
                Icon(
                    painter = painterResource(tool.icon),
                    contentDescription = null,
                    tint = if (node.hidden) CoineProColors.TextDisabled else CoineProColors.TextMuted,
                    modifier = Modifier.size(GLYPH),
                )
            }
            Text(
                text = node.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (node.hidden) CoineProColors.TextDisabled else CoineProColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            RowAction(
                icon = if (node.hidden) DesignR.drawable.icon_eye_slash else DesignR.drawable.icon_eye,
                label = if (node.hidden) "نمایش" else "پنهان کردن",
                tint = if (node.hidden) CoineProColors.Gold else CoineProColors.TextMuted,
                onClick = onToggleHidden,
            )
            RowAction(
                icon = if (node.locked) DesignR.drawable.tv_lock else DesignR.drawable.tv_unlock,
                label = if (node.locked) "باز کردن قفل" else "قفل کردن",
                tint = if (node.locked) CoineProColors.Gold else CoineProColors.TextMuted,
                onClick = onToggleLocked,
            )
            RowAction(
                icon = DesignR.drawable.tv_settings2,
                label = "تنظیمات این ترسیم",
                tint = CoineProColors.TextMuted,
                onClick = onOpenStyle,
            )
        }
    }
}

/**
 * One glyph on a row, with a hit rect a thumb can actually land on.
 *
 * Forty by forty rather than the icon's twenty. Three of these sit beside each other and a row of
 * small targets is what produces "I keep tapping the wrong one" in reviews of every app that has
 * a list like this.
 */
@Composable
private fun RowAction(
    @DrawableRes icon: Int,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val haptics = rememberCoineProHaptics()
    Box(
        modifier = Modifier
            .size(ACTION)
            .clip(CircleShape)
            .clickable {
                haptics.select()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(GLYPH),
        )
    }
}

/**
 * Which display row a drag of [dragPx] from [fromRow] lands on.
 *
 * Rounded rather than truncated, so a row dragged more than half a row's height has moved: with
 * truncation the reader has to overshoot by a whole row before anything happens, which reads as
 * the gesture being ignored. Clamped to the group, because a drag that leaves the group has no
 * meaning — see [ObjectGroupBlock].
 *
 * A zero or negative [rowPx] means the layout has not been measured and the drag is treated as no
 * movement rather than divided by.
 */
internal fun rowAfterDrag(fromRow: Int, dragPx: Float, rowPx: Float, rowCount: Int): Int {
    if (rowCount <= 0) return 0
    if (rowPx <= 0f || !dragPx.isFinite()) return fromRow.coerceIn(0, rowCount - 1)
    val moved = (dragPx / rowPx).roundToInt()
    return (fromRow + moved).coerceIn(0, rowCount - 1)
}

/**
 * The z index a row's new display position corresponds to.
 *
 * The tree shows each group topmost-first while the chart's list is oldest-first, so a display
 * position cannot be used as a z index directly — and the two run in opposite directions, which is
 * the sort of thing that is wrong by exactly one and looks almost right. The rule that survives
 * both reversals is simply: *take the place of whoever is standing there now*. The drawing already
 * occupying the target display slot has a z index, and `ObjectTree.reorder` moving the dragged one
 * to that index puts it where the reader dropped it, whichever direction they dragged.
 *
 * Returns -1 when the target slot or its drawing cannot be resolved, which the caller reads as
 * "nothing to do" rather than as index zero — restacking to the back of the chart because a lookup
 * failed would be a visible, wrong change.
 */
internal fun restackIndex(groupIds: List<Long>, drawings: List<Drawing>, toRow: Int): Int {
    val occupant = groupIds.getOrNull(toRow) ?: return -1
    return drawings.indexOfFirst { it.id == occupant }
}

/**
 * What turns a stored ARGB `Long` into a Compose colour: the value sits in the *high* half of a
 * 64-bit word, so `Color(0xFFD8A848L.toULong())` is transparent black. See the same constant in
 * `ChartScreen`, which crosses the same boundary for the same reason.
 */
private const val COLOUR_SHIFT = 32

/** Tall enough for a thumb and short enough that six rows fit a sheet. Also the drag's unit. */
private val ROW_HEIGHT = 44.dp

/** The hit rect of a row's eye, padlock and settings glyph. */
private val ACTION = 40.dp

/** The glyph inside one of those, and the tool icon beside the label. */
private val GLYPH = 18.dp

/** The drawing's own colour, as a dot. Small: it is a cue, not a swatch. */
private val DOT = 8.dp

/**
 * How far a row has to travel before letting go deletes the drawing.
 *
 * Seventy-two points. Far enough that a thumb sliding across the list while scrolling never reaches
 * it, short enough that it is one deliberate flick rather than a drag across the whole screen.
 */
private val SWIPE_TO_DELETE = 72.dp

/** A sheet's list is capped, so the sheet does not quietly become the whole screen. */
private val TREE_MAX_HEIGHT = 360.dp
