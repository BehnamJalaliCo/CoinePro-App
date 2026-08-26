package com.coinepro.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

    /** A card. Barely moves. */
    const val CARD = 0.995f

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
