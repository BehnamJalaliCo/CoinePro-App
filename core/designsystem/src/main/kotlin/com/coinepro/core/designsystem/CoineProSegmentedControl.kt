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
            .background(CoineProColors.Surface, CoineProPillShape)
            // The tray gets an edge too. A control has to look like a container before the block
            // inside it can look raised out of one.
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
 * ### The inversion this fixes
 *
 * The selected segment took `SurfaceElevated` and the tray took `Surface`. In the dark theme that
 * is correct — elevated is lighter, so the segment rises. In the light theme the ladder climbs
 * *down* into grey, so elevated is **darker than its own tray**: the selected segment was pressed
 * into the control and the two unselected ones were flush with it. That is why the light theme's
 * one-of-three controls all read as three flat blocks with a smudge on one of them.
 *
 * `SurfaceRaised` is the token that says "lifted out of its container" in both themes, which is a
 * different statement from "one rung further along the ladder", and this is the control that shows
 * why the two cannot be the same token.
 */
@Composable
private fun RowScope.Segment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (selected) CoineProColors.SurfaceRaised else Color.Transparent,
        animationSpec = CoineProMotionSpecs.standard(),
        label = "segmentFill",
    )
    val ink by animateColorAsState(
        targetValue = if (selected) CoineProColors.TextPrimary else CoineProColors.TextMuted,
        animationSpec = CoineProMotionSpecs.standard(),
        label = "segmentInk",
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
            .background(color = fill, shape = CoineProPillShape)
            .then(
                if (selected) {
                    Modifier.border(1.dp, CoineProColors.BorderSubtle, CoineProPillShape)
                } else {
                    Modifier
                },
            )
            // A filter above the balance should not weigh the same as the screen's primary
            // action. Nine points and the medium label put it at 40dp instead of 56.
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = ink,
            textAlign = TextAlign.Center,
        )
    }
}
