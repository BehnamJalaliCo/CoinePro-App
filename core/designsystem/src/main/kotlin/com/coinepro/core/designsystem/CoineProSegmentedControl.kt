package com.coinepro.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
    val haptics = rememberCoineProHaptics()
    Row(
        modifier = modifier
            .fillMaxWidth()
            // The chip family's quiet plate: one rung off the card, so the tray reads as a control
            // even inside a sheet whose own ground is the card colour.
            .background(CoineProColors.SurfaceElevated, CoineProPillShape)
            // The tray gets an edge too. A control has to look like a container before the block
            // inside it can look chosen out of one.
            .border(1.dp, CoineProColors.BorderSubtle, CoineProPillShape)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            Segment(
                label = label,
                selected = value == selected,
                // Only a change is worth a tick. Pressing the segment you are already on has
                // changed nothing, and a buzz that says otherwise teaches the reader to distrust
                // the ones that do mean something.
                onClick = {
                    if (value != selected) haptics.select()
                    onSelect(value)
                },
            )
        }
    }
}

/**
 * One segment.
 *
 * ### The inversion this fixed, and the family it joined
 *
 * The selected segment took `SurfaceElevated` over a `Surface` tray, which in the light theme is
 * *darker* than its own tray — the selected segment was pressed into the control. It then took
 * `SurfaceRaised`, which fixed the direction and left the control a grey-on-grey smudge.
 *
 * It now selects the way every chip does ([chipLook]): a soft wash of the page accent, lettered in
 * the accent's ink and closed with its hairline. That is a *tint*, not the gold slab the note above
 * warns about — the one solid gold on the screen is still its primary action.
 */
@Composable
private fun RowScope.Segment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val look = chipLook(selected = true)
    val fill by animateColorAsState(
        targetValue = if (selected) look.plate else Color.Transparent,
        animationSpec = CoineProMotionSpecs.standard(),
        label = "segmentFill",
    )
    val ink by animateColorAsState(
        targetValue = if (selected) look.ink else CoineProColors.TextSecondary,
        animationSpec = CoineProMotionSpecs.standard(),
        label = "segmentInk",
    )
    val edge by animateColorAsState(
        targetValue = if (selected) look.edge else Color.Transparent,
        animationSpec = CoineProMotionSpecs.standard(),
        label = "segmentEdge",
    )
    Box(
        modifier = Modifier
            .weight(1f)
            .pressScale(interaction, CoineProPress.CHIP)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            // Thirty-two inside the tray's four: a forty-point control, the chip family's regular
            // size, rather than the screen's primary action's.
            .heightIn(min = CoineProChipDefaults.CompactHeight)
            .background(color = fill, shape = CoineProPillShape)
            .border(1.dp, edge, CoineProPillShape)
            .padding(horizontal = CoineProSpacing.One),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
