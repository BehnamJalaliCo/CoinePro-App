package com.coinepro.core.chart

import com.coinepro.core.common.toPersianDigits
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProChip
import com.coinepro.core.designsystem.CoineProChipRow
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProSheetEmpty
import com.coinepro.core.designsystem.CoineProSheetSearch
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.designsystem.CoineProSpacing
import com.coinepro.core.designsystem.R as DesignR

/**
 * The drawing tools.
 *
 * Fifty-two of them, which is the number that decides the whole layout. Three arrangements were
 * available and only one survives contact with a phone:
 *
 * * The web terminal's **vertical rail** becomes a two-screen scroll of 24dp targets here. It works
 *   on a desktop because a mouse is precise and the rail sits permanently beside the chart; neither
 *   is true on a phone.
 * * An **accordion** of eleven groups hides ten group names behind a tap and turns finding a tool
 *   into a two-step search — open the right drawer, then look inside it — while leaving the reader
 *   wondering what is collapsed.
 * * A **chip row over a grid** keeps every group name visible, costs one tap, and carries a search
 *   field for the reader who knows the name. That is this.
 *
 * The search earns its place: with fifty-two tools, typing «فیب» beats any amount of scanning, and
 * it is the only path that works for somebody who knows a tool by name but not by glyph.
 */
@Composable
fun ToolRail(
    selected: String?,
    onSelect: (DrawingTool) -> Unit,
    modifier: Modifier = Modifier,
    onHelp: ((String) -> Unit)? = null,
) {
    var group by remember { mutableStateOf<ToolGroup?>(null) }
    var query by remember { mutableStateOf("") }

    // Typing overrides the chips rather than intersecting with them. Somebody who types «کمان»
    // wants the arc tool — not "the arc tool if it happens to be in the group I last tapped". An
    // empty result the reader cannot explain is the worst outcome of combining two filters quietly.
    val searching = query.isNotBlank()
    val tools = when {
        searching -> DrawingTools.matching(query)
        group != null -> DrawingTools.inGroup(group!!)
        else -> DrawingTools.ALL
    }

    Column(modifier = modifier.fillMaxWidth().background(CoineProColors.Surface)) {
        CoineProSheetSearch(
            value = query,
            onValueChange = { query = it },
            placeholder = "جست‌وجوی ابزار",
            modifier = Modifier.padding(horizontal = CoineProSpacing.Gutter),
        )
        Spacer(Modifier.height(CoineProSpacing.OneHalf))
        if (!searching) {
            CoineProChipRow(
                options = DrawingTools.GROUPS.map { candidate ->
                    CoineProChip(
                        id = candidate.name,
                        label = candidate.label,
                        count = DrawingTools.inGroup(candidate).size,
                    )
                },
                selectedId = group?.name,
                onSelect = { id -> group = id?.let(ToolGroup::valueOf) },
                allLabel = "همه",
            )
            Spacer(Modifier.height(CoineProSpacing.One))
        }

        if (tools.isEmpty()) {
            CoineProSheetEmpty("ابزاری با این نام پیدا نشد.")
            return@Column
        }

        val grouped = !searching && group == null
        LazyVerticalGrid(
            columns = GridCells.Fixed(TOOLS_ACROSS),
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                horizontal = CoineProSpacing.OneHalf,
                vertical = CoineProSpacing.One,
            ),
            horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
            verticalArrangement = Arrangement.spacedBy(CoineProSpacing.One),
        ) {
            // A group heading only when the list actually spans groups. Printing "فیبوناچی" over a
            // grid the reader just filtered *to* فیبوناچی is a line of noise.
            var lastGroup: ToolGroup? = null
            for (tool in tools) {
                if (grouped && tool.group != lastGroup) {
                    lastGroup = tool.group
                    val heading = tool.group
                    item(key = "h-${heading.name}", span = { GridItemSpan(TOOLS_ACROSS) }) {
                        Text(
                            text = heading.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = CoineProColors.TextMuted,
                            modifier = Modifier.padding(
                                top = CoineProSpacing.One,
                                bottom = CoineProSpacing.Half,
                            ),
                        )
                    }
                }
                item(key = tool.id) {
                    ToolCell(
                        tool = tool,
                        selected = tool.id == selected,
                        onClick = { onSelect(tool) },
                        onHelp = tool.helpId?.let { id -> onHelp?.let { { it(id) } } },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolCell(
    tool: DrawingTool,
    selected: Boolean,
    onClick: () -> Unit,
    onHelp: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            // A fixed height, so a two-line name does not make its cell taller than the three
            // beside it. Rows in a grid size to their tallest cell, and the result was a ragged
            // wall with holes in it.
            .height(CELL_HEIGHT)
            .clip(CoineProShapes.small)
            .background(if (selected) CoineProColors.SurfaceElevated else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) CoineProColors.Accent else CoineProColors.Border,
                shape = CoineProShapes.small,
            )
            .combinedClickable(onClick = onClick, onLongClick = onHelp)
            .padding(horizontal = CoineProSpacing.Half),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(tool.icon),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = if (selected) CoineProColors.Accent else CoineProColors.TextSecondary,
        )
        Spacer(Modifier.height(CoineProSpacing.Half))
        Text(
            text = tool.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) CoineProColors.TextPrimary else CoineProColors.TextMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            // Two lines and then an ellipsis. "گسترش زمانی فیبوناچی" does not fit a quarter of a
            // phone at any size worth reading, and a cell that grows to fit it breaks the grid.
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The strip that says which tool is armed, and offers the ways out of it.
 *
 * A drawing tool is a *mode*, and a mode with no visible state is how people end up drawing trend
 * lines they did not mean to. This says what is armed, how many taps are left, and gives one tap
 * back to the cursor.
 */
@Composable
fun ActiveToolBar(
    tool: DrawingTool?,
    /** How many points are already placed on the drawing in progress. */
    placed: Int,
    onCancel: () -> Unit,
    onUndo: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onHelp: ((String) -> Unit)? = null,
) {
    if (tool == null) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CoineProColors.SurfaceElevated)
            .padding(horizontal = CoineProSpacing.Gutter, vertical = CoineProSpacing.One),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
    ) {
        Icon(
            painter = painterResource(tool.icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = CoineProColors.Accent,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tool.label,
                style = MaterialTheme.typography.bodyMedium,
                color = CoineProColors.TextPrimary,
            )
            if (tool.points > 0) {
                Text(
                    // A prose count, so Persian digits — unlike a price, which stays Latin.
                    text = "نقطهٔ ${(placed + 1).toPersianDigits()} از ${(tool.points).toPersianDigits()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = CoineProColors.TextMuted,
                )
            }
        }
        if (onUndo != null && placed > 0) {
            RailAction(DesignR.drawable.icon_arrows_clockwise, "واگرد", onUndo)
        }
        tool.helpId?.let { id ->
            onHelp?.let { RailAction(DesignR.drawable.tv_help_circle, "راهنما") { it(id) } }
        }
        RailAction(DesignR.drawable.icon_x, "بستن", onCancel)
    }
}

@Composable
private fun RailAction(icon: Int, label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = CoineProColors.TextMuted,
        )
    }
}

/**
 * The placed drawings, listed so they can be found and removed.
 *
 * A chart with twenty drawings on it needs a way to delete the one behind the others, and hunting
 * for it by tapping is not one.
 */
@Composable
fun DrawingList(
    drawings: List<Drawing>,
    onSelect: (Drawing) -> Unit,
    onDelete: (Drawing) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (drawings.isEmpty()) {
        CoineProSheetEmpty("هنوز چیزی روی چارت نکشیده‌ای.", modifier)
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(CoineProColors.Surface)
            .heightIn(max = LIST_MAX_HEIGHT),
    ) {
        items(drawings.size, key = { drawings[it].id }) { index ->
            val drawing = drawings[index]
            val tool = DrawingTools[drawing.toolId]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(drawing) }
                    .padding(
                        horizontal = CoineProSpacing.Gutter,
                        vertical = CoineProSpacing.OneHalf,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CoineProSpacing.OneHalf),
            ) {
                if (tool != null) {
                    Icon(
                        painter = painterResource(tool.icon),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color(drawing.colour),
                    )
                }
                Text(
                    text = tool?.label ?: drawing.toolId,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoineProColors.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                RailAction(DesignR.drawable.tv_trash2, "حذف") { onDelete(drawing) }
            }
        }
    }
}

/**
 * Four across.
 *
 * Three wastes a phone's width; five puts the labels below a size anyone reads. At four, a 411dp
 * screen gives each cell about 92dp, which fits a 24dp glyph and two lines of Persian under it.
 */
private const val TOOLS_ACROSS = 4

/** Tall enough for a 24dp glyph and two lines of Persian, and the same for every cell. */
private val CELL_HEIGHT = 84.dp

/** A sheet's list is capped, so the sheet does not quietly become the whole screen. */
private val LIST_MAX_HEIGHT = 320.dp
