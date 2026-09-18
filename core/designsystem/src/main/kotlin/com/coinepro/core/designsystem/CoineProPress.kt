package com.coinepro.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/**
 * How far a surface compresses under a finger.
 *
 * Bigger surface, smaller compression — a card that shrank as much as a button would look like it
 * was falling into the page. The numbers are the web terminal's, and they are small on purpose:
 * the point is a sense of contact, not an animation somebody notices.
 */
object CoineProPress {
    /** A pill control. */
    const val CONTROL = 0.965f

    /** A full-width primary action — the largest control, so the deepest press. */
    const val CTA = 0.955f

    /**
     * A card. Barely moves.
     *
     * Nine hundred and ninety, up from nine hundred and ninety-five. At 0.995 a 360dp-wide card
     * travels 0.9dp on each edge, which is under a physical pixel on most of the phones this ships
     * to — a press state that is real in the file and absent under the thumb. A full point of scale
     * is still a tenth of what the primary action does, so the hierarchy the numbers encode is
     * intact: the card acknowledges, the button commits.
     *
     * The scale is not the whole press state and never was the important half of it. See
     * [CoineProCard], which also moves its fill to `surfacePressed` — a change in value is what the
     * eye actually reads as contact, and it works at any card size.
     */
    const val CARD = 0.99f

    /** A chip. */
    const val CHIP = 0.98f

    /** A list row. */
    const val ROW = 0.997f
}

/**
 * Scale this composable while it is pressed.
 *
 * Finite and state-driven rather than a loop, so it is outside what the reduced-motion gate is
 * about: it lasts exactly as long as a finger is down. It still respects the system animation
 * scale, because `animateFloatAsState` does — with animations off the value snaps rather than
 * eases, which is the correct behaviour and not a special case worth writing.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressed: Float = CoineProPress.CONTROL,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressed else 1f,
        animationSpec = CoineProMotionSpecs.press(),
        label = "pressScale",
    )
    return scale(scale)
}

/**
 * A control that compresses under a thumb and answers it — the two halves of D6 in one modifier.
 *
 * ### Why this exists rather than three lines at each call site
 *
 * Because the three lines were the problem. A control in this app is «an interaction source, a
 * `pressScale` on it, a `haptics.select()` in the lambda, and `indication = null` so the ripple
 * does not fight the scale», and every one of those is a line somebody can forget without anything
 * failing. The chart is where that showed: run Ξ's audit found fifty clickable controls on and
 * around the plot and **thirty-one of them were silent** — the drawing pencil, every replay
 * transport, the selection toolbar, the legend's rows, the scale minis. Each was a perfectly
 * ordinary `Modifier.clickable(onClick = …)`, which is exactly why nobody noticed: the missing
 * feedback is invisible in a diff and inaudible in a screenshot.
 *
 * The buttons had it because the *component* carried it — that is the argument
 * `scripts/quality/check-haptic-policy.sh` already makes for its five primitives. This extends the
 * same argument to everything that is a control without being a button.
 *
 * ### What it does not do
 *
 * It does not invent a sixth haptic weight: [CoineProHaptics.select] is the vocabulary's word for
 * «you touched a control and it took», and a control that commits something destructive should
 * still say so itself. It does not take a ripple, because a ripple and a scale together read as two
 * responses to one touch, and the scale is the one this product uses.
 */
@Composable
fun Modifier.coineProControl(
    enabled: Boolean = true,
    pressed: Float = CoineProPress.CONTROL,
    /** Read out by a screen reader in place of «button». Null keeps the platform's own wording. */
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val haptics = rememberCoineProHaptics()
    return this
        .pressScale(interaction, pressed)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
        ) {
            haptics.select()
            onClick()
        }
}
