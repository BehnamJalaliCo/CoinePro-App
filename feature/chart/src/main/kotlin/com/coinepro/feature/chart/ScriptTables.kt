package com.coinepro.feature.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.coinepro.core.designsystem.CoineProColors
import com.coinepro.core.designsystem.CoineProShapes
import com.coinepro.core.script.ScriptTable

/**
 * A script's `table.new` grids over the plot (5.15.0), each in the corner it named.
 *
 * The corner is Pine's and absolute — `top_right` is the right-hand corner in either language, the
 * side the price axis is on — and so is the column order: column zero is on the left, the way the
 * author laid the table out. A cell's own text keeps its own direction inside it.
 */
@Composable
internal fun ScriptTables(tables: List<ScriptTable>, modifier: Modifier = Modifier) {
    if (tables.isEmpty()) return
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier) {
            for (table in tables) {
                if (table.cells.isEmpty()) continue
                ScriptTableGrid(table, Modifier.align(cornerOf(table.position)))
            }
        }
    }
}

@Composable
private fun ScriptTableGrid(table: ScriptTable, modifier: Modifier) {
    Row(
        modifier = modifier
            .clip(CoineProShapes.small)
            .background(table.background?.let { Color(it.toInt()) } ?: CoineProColors.SurfaceElevated)
            .border(1.dp, CoineProColors.Border, CoineProShapes.small)
            .semantics { contentDescription = "script-table" },
    ) {
        // Column by column, so a column is as wide as its widest cell and the rows line up.
        for (column in 0 until table.columns) {
            val cells = table.cells.filter { it.column == column }
            if (cells.isEmpty()) continue
            Column {
                for (row in 0 until table.rows) {
                    if (table.cells.none { it.row == row }) continue
                    val cell = cells.firstOrNull { it.row == row }
                    Text(
                        text = cell?.text.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = cell?.textColour?.let { Color(it.toInt()) } ?: CoineProColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(cell?.background?.let { Color(it.toInt()) } ?: Color.Transparent)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/** Pine's nine positions; anything else is the bottom-right, Pine's own default. */
internal fun cornerOf(position: String): Alignment = when (position) {
    "top_left" -> AbsoluteAlignment.TopLeft
    "top_center" -> Alignment.TopCenter
    "top_right" -> AbsoluteAlignment.TopRight
    "middle_left" -> AbsoluteAlignment.CenterLeft
    "middle_center" -> Alignment.Center
    "middle_right" -> AbsoluteAlignment.CenterRight
    "bottom_left" -> AbsoluteAlignment.BottomLeft
    "bottom_center" -> Alignment.BottomCenter
    else -> AbsoluteAlignment.BottomRight
}

