package com.coinepro.core.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The app's switch colours, for a Material `Switch` (DIALOGS-12).
 *
 * Material's own switch letters its thumb in `onPrimary`, which in this theme is the near-black that
 * reads on a gold button — so every switch was a black disc on a gold slab, the heaviest object in
 * any settings dialog, and in the light theme a hole punched in the page. TradingView's toggles are
 * the opposite: a white thumb on the accent when on, a pale thumb on a quiet grey when off.
 *
 * `Switch(checked, onCheckedChange, colors = CoineProSwitchDefaults.colors())` — or use
 * [CoineProSwitch], which is the same palette at TradingView's compact size.
 */
object CoineProSwitchDefaults {
    @Composable
    fun colors(): SwitchColors {
        val look = switchLook()
        return SwitchDefaults.colors(
            checkedThumbColor = look.thumbOn,
            checkedTrackColor = look.trackOn,
            checkedBorderColor = Color.Transparent,
            checkedIconColor = look.trackOn,
            uncheckedThumbColor = look.thumbOff,
            uncheckedTrackColor = look.trackOff,
            uncheckedBorderColor = look.edgeOff,
            uncheckedIconColor = look.trackOff,
            disabledCheckedThumbColor = look.thumbOn.copy(alpha = DISABLED_ALPHA),
            disabledCheckedTrackColor = look.trackOn.copy(alpha = DISABLED_ALPHA),
            disabledCheckedBorderColor = Color.Transparent,
            disabledUncheckedThumbColor = look.thumbOff.copy(alpha = DISABLED_ALPHA),
            disabledUncheckedTrackColor = look.trackOff.copy(alpha = DISABLED_ALPHA),
            disabledUncheckedBorderColor = look.edgeOff.copy(alpha = DISABLED_ALPHA),
        )
    }
}

/**
 * A compact switch: a 36 × 20 track and a 16 dp thumb, TradingView's size, in [CoineProSwitchDefaults]'
 * colours. The target is still 48 dp; only the drawing is small.
 *
 * Same shape as Material's `Switch(checked, onCheckedChange, modifier, enabled)`, so a call site
 * swaps the name and drops its `colors`.
 */
@Composable
fun CoineProSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val look = switchLook()
    val haptics = rememberCoineProHaptics()
    val interaction = remember { MutableInteractionSource() }
    val dim = if (enabled) 1f else DISABLED_ALPHA
    val track by animateColorAsState(
        targetValue = (if (checked) look.trackOn else look.trackOff).let { it.copy(alpha = it.alpha * dim) },
        animationSpec = CoineProMotionSpecs.standard(),
        label = "switchTrack",
    )
    val edge by animateColorAsState(
        targetValue = if (checked) Color.Transparent else look.edgeOff.copy(alpha = look.edgeOff.alpha * dim),
        animationSpec = CoineProMotionSpecs.standard(),
        label = "switchEdge",
    )
    val thumb by animateColorAsState(
        targetValue = (if (checked) look.thumbOn else look.thumbOff).let { it.copy(alpha = it.alpha * dim) },
        animationSpec = CoineProMotionSpecs.standard(),
        label = "switchThumb",
    )
    // The thumb travels, so it springs (D6). `offset` mirrors itself in Persian, which puts «on» at
    // the reading end in both directions, as Material's switch does.
    val travel by animateDpAsState(
        targetValue = if (checked) SWITCH_TRAVEL else 0.dp,
        animationSpec = CoineProMotionSpecs.defaultSpatialFor(),
        label = "switchThumbTravel",
    )
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Switch,
        ) {
            haptics.select()
            onCheckedChange(it)
        }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(toggle)
            .pressScale(interaction, CoineProPress.CHIP),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = SWITCH_WIDTH, height = SWITCH_HEIGHT)
                .clip(CircleShape)
                .background(track)
                .border(1.dp, edge, CircleShape)
                .padding(SWITCH_INSET),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = travel)
                    .size(SWITCH_THUMB)
                    .shadow(1.dp, CircleShape)
                    .background(thumb, CircleShape),
            )
        }
    }
}

@Immutable
private data class SwitchLook(
    val trackOn: Color,
    val thumbOn: Color,
    val trackOff: Color,
    val thumbOff: Color,
    val edgeOff: Color,
)

@Composable
@ReadOnlyComposable
private fun switchLook(): SwitchLook {
    val palette = LocalCoineProPalette.current
    return SwitchLook(
        trackOn = CoineProColors.pageAccent,
        // White in both themes: the thumb is an object on the track, not text, and a white disc on
        // the accent is the light, calm toggle the reference draws.
        thumbOn = Color.White,
        trackOff = if (palette.isDark) palette.surfaceRaised else palette.borderStrong.compositeOver(palette.surface),
        thumbOff = if (palette.isDark) palette.textSecondary else Color.White,
        edgeOff = if (palette.isDark) palette.borderStrong else Color.Transparent,
    )
}

private const val DISABLED_ALPHA = 0.38f
private val SWITCH_WIDTH = 36.dp
private val SWITCH_HEIGHT = 20.dp
private val SWITCH_INSET = 2.dp
private val SWITCH_THUMB = 16.dp
private val SWITCH_TRAVEL = SWITCH_WIDTH - SWITCH_THUMB - SWITCH_INSET * 2
