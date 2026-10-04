package com.coinepro.feature.chart

import com.coinepro.core.designsystem.coachTarget
import com.coinepro.core.designsystem.CoachTip
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.coinepro.core.designsystem.CoineProColors
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.coinepro.core.chart.ChartIcon
import com.coinepro.core.chart.ChartIcons
import com.coinepro.core.chart.DrawingTool
import com.coinepro.core.chart.DrawingTools
import com.coinepro.core.chart.ToolGroup
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.R as DesignR
import com.coinepro.core.designsystem.inEnglish
import kotlin.math.roundToInt

/**
 * TradingView's phone drawing toolbar, from the owner's recording (5.20.0).
 *
 * A white pill floating over the left of the plot, one glyph per group of tools, the armed one in
 * TradingView's blue. Three things make it TradingView's rather than a list:
 *
 * * **It moves.** The grip at the head drags the whole toolbar anywhere over the plot, so it is
 *   never parked over the candles the reader is drawing on.
 * * **It folds.** A tap on the grip springs it down to the grip and the armed tool, and back —
 *   a spring rather than a tween, so it overshoots a little and settles the way the recording's
 *   does, and a second tap mid-fold carries the motion it had.
 * * **A tap draws, a hold chooses.** A tap arms the group's tool — the one the reader last used in
 *   that group, else the group's first; a long press opens the group's tools beside it.
 *
 * The phone's alternative used to be the pencil opening a sheet of ninety-one tools that covered
 * the chart being drawn on.
 */
@Composable
internal fun FloatingDrawingToolbar(
    state: ChartUiState,
    controller: ChartController,
    onAllTools: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val english = inEnglish()
    val hasVolume = state.series.hasVolume
    val groups = remember(hasVolume) {
        DrawingTools.GROUPS.filter { it != ToolGroup.MODES && (it != ToolGroup.VOLUME || hasVolume) }
    }
    val armed = state.drawing.tool
    var collapsed by rememberSaveable { mutableStateOf(false) }
    var dragX by rememberSaveable { mutableStateOf(0f) }
    val density = LocalDensity.current
    // Below the legend — the back key, the symbol and the price — rather than over it.
    var dragY by rememberSaveable { mutableStateOf(with(density) { TOOLBAR_TOP.toPx() }) }
    var flyoutGroup by remember { mutableStateOf<ToolGroup?>(null) }

    // The rail is laid out left to right in every language, as the tablet's is: TradingView's
    // Arabic chart keeps its drawing toolbar on the left too, clear of the price axis on the right.
    // Its labels take the reader's direction back inside the flyout.
    val reader = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    BoxWithConstraints(modifier = modifier) {
        val maxX = with(density) { (maxWidth - TOOLBAR_WIDTH - EDGE * 2).toPx() }.coerceAtLeast(0f)
        val maxY = with(density) { (maxHeight - GRIP_HEIGHT - EDGE * 2).toPx() }.coerceAtLeast(0f)
            Surface(
                modifier = Modifier
                    .offset { IntOffset(dragX.coerceIn(0f, maxX).roundToInt(), dragY.coerceIn(0f, maxY).roundToInt()) }
                    .padding(EDGE)
                    .width(TOOLBAR_WIDTH)
                    .heightIn(max = maxHeight - EDGE * 2)
                    .shadow(TOOLBAR_SHADOW, RoundedCornerShape(TOOLBAR_RADIUS))
                    .coachTarget(CoachTip.DRAW_TOOLBAR)
                    .semantics { contentDescription = "chart-floating-tools" },
                shape = RoundedCornerShape(TOOLBAR_RADIUS),
                color = PILL,
            ) {
                Column(
                    modifier = Modifier
                        .animateContentSize(
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                        .padding(bottom = if (collapsed) 0.dp else CoineProSpacing.Half),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Grip(
                        onTap = { collapsed = !collapsed },
                        onDrag = { dx, dy ->
                            dragX = (dragX + dx).coerceIn(0f, maxX)
                            dragY = (dragY + dy).coerceIn(0f, maxY)
                        },
                    )
                    if (collapsed) {
                        // Folded: the armed tool alone, so the reader still sees what a tap on the
                        // plot will do.
                        armed?.let { tool ->
                            ToolbarGlyph(
                                tool = tool,
                                active = true,
                                description = tool.label(english),
                                onTap = { controller.arm(null) },
                                onHold = {},
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            for (group in groups) {
                                val tools = DrawingTools.ALL.filter { it.group == group }
                                val shown = state.drawing.lastUsed[group]
                                    ?.let { id -> tools.firstOrNull { it.id == id } }
                                    ?: tools.firstOrNull()
                                    ?: continue
                                val active = armed?.group == group
                                Box {
                                    ToolbarGlyph(
                                        tool = if (active) armed ?: shown else shown,
                                        active = active,
                                        description = group.label(english),
                                        many = tools.size > 1,
                                        onTap = { controller.arm(if (active) null else shown) },
                                        onHold = { flyoutGroup = group },
                                    )
                                    if (flyoutGroup == group) {
                                        GroupFlyout(
                                            tools = tools,
                                            armedId = armed?.id,
                                            title = group.label(english),
                                            english = english,
                                            reader = reader,
                                            onArm = { tool ->
                                                flyoutGroup = null
                                                controller.arm(tool)
                                            },
                                            onDismiss = { flyoutGroup = null },
                                        )
                                    }
                                }
                            }
                            // Every tool, in the full palette, for the ones no group glyph shows.
                            Box(
                                modifier = Modifier
                                    .size(CELL)
                                    .pointerInput(Unit) { detectTapGestures(onTap = { onAllTools() }) }
                                    .semantics { contentDescription = "chart-floating-tools-all" },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(DesignR.drawable.tv_layout_grid),
                                    contentDescription = null,
                                    tint = INK,
                                    modifier = Modifier.size(GLYPH),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The six dots at the head: drag to move, tap to fold. */
@Composable
private fun Grip(onTap: () -> Unit, onDrag: (Float, Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(GRIP_HEIGHT)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
            .pointerInput(Unit) {
                detectDragGestures { change, amount ->
                    change.consume()
                    onDrag(amount.x, amount.y)
                }
            }
            .semantics { contentDescription = "chart-floating-tools-grip" },
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) {
                        Box(Modifier.size(3.dp).clip(CircleShape).background(GRIP_DOT))
                    }
                }
            }
        }
    }
}

/**
 * One glyph: TradingView's 28-unit drawing, in a 44 dp cell, on a pale blue plate when armed. A
 * group of more than one tool carries the small corner mark that says a hold opens more.
 */
@Composable
private fun ToolbarGlyph(
    tool: DrawingTool,
    active: Boolean,
    description: String,
    onTap: () -> Unit,
    onHold: () -> Unit,
    many: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(CELL)
            .pointerInput(tool.id, active) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onHold() })
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(PLATE)
                .clip(RoundedCornerShape(8.dp))
                .background(if (active) ACTIVE_PLATE else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(ChartIcons.drawable(ChartIcon(tool.icon.name))),
                contentDescription = null,
                tint = if (active) ACTIVE else INK,
                modifier = Modifier.size(GLYPH),
            )
        }
        if (many) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp)
                    .size(4.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(GRIP_DOT),
            )
        }
    }
}

/** A group's tools beside the toolbar, TradingView's 40 dp rows. */
@Composable
private fun GroupFlyout(
    tools: List<DrawingTool>,
    armedId: String?,
    title: String,
    english: Boolean,
    reader: LayoutDirection,
    onArm: (DrawingTool) -> Unit,
    onDismiss: () -> Unit,
) {
    val gap = with(LocalDensity.current) { (CELL + 6.dp).roundToPx() }
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(gap, 0),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides reader) {
            Surface(
                modifier = Modifier.width(FLYOUT_WIDTH).heightIn(max = FLYOUT_MAX_HEIGHT),
                shape = RoundedCornerShape(10.dp),
                color = PILL,
                shadowElevation = TOOLBAR_SHADOW,
            ) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MUTED,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    for (tool in tools) {
                        val on = tool.id == armedId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .background(if (on) ACTIVE_PLATE else Color.Transparent)
                                .pointerInput(tool.id) { detectTapGestures(onTap = { onArm(tool) }) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                painter = painterResource(ChartIcons.drawable(ChartIcon(tool.icon.name))),
                                contentDescription = null,
                                tint = if (on) ACTIVE else INK,
                                modifier = Modifier.size(GLYPH),
                            )
                            Text(
                                text = tool.label(english),
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (on) ACTIVE else INK,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** TradingView's toolbar in the recording: a white pill on a light chart, charcoal on a dark one, blue armed. */
private val PILL: Color @Composable get() = if (darkStage()) Color(0xFF1E222D) else Color(0xFFFFFFFF)
private val INK: Color @Composable get() = if (darkStage()) Color(0xFFD1D4DC) else Color(0xFF131722)
private val MUTED: Color @Composable get() = Color(0xFF787B86)
private val GRIP_DOT: Color @Composable get() = if (darkStage()) Color(0xFF50535E) else Color(0xFFB2B5BE)

/** TradingView's dark chart carries the same toolbar in its own charcoal rather than white. */
@Composable
private fun darkStage(): Boolean = CoineProColors.Stage.luminance() < 0.5f
private val ACTIVE = Color(0xFF2962FF)
private val ACTIVE_PLATE = Color(0x1A2962FF)

private val TOOLBAR_WIDTH = 48.dp
private val TOOLBAR_RADIUS = 10.dp
private val TOOLBAR_SHADOW = 6.dp
private val TOOLBAR_TOP = 72.dp
private val EDGE = 8.dp
private val GRIP_HEIGHT = 24.dp
private val CELL = 44.dp
private val PLATE = 38.dp
private val GLYPH = 26.dp
private val FLYOUT_WIDTH = 240.dp
private val FLYOUT_MAX_HEIGHT = 440.dp
