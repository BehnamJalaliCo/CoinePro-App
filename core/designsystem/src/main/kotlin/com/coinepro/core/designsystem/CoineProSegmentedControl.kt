package com.coinepro.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A switch between a small number of exclusive contexts.
 *
 * Built for the one place the product genuinely needs it: choosing which platform the whole app is
 * showing. That is not a filter — it changes which account is signed in and which feed every quote
 * comes from — so it is deliberately heavier than a chip row and sits at the top of the screen
 * rather than beside the content it affects.
 *
 * The selected segment is a raised neutral block, not gold. The screen's gold belongs to its
 * primary action, and a gold segment here would read as the thing to press rather than as where
 * you already are.
 */
@Composable
fun <T> CoineProSegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(CoineProColors.Surface, CoineProPillShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            Segment(
                label = label,
                selected = value == selected,
                onClick = { onSelect(value) },
            )
        }
    }
}

@Composable
private fun RowScope.Segment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .background(
                color = if (selected) CoineProColors.SurfaceElevated else CoineProColors.Surface,
                shape = CoineProPillShape,
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) CoineProColors.TextPrimary else CoineProColors.TextMuted,
            textAlign = TextAlign.Center,
        )
    }
}
