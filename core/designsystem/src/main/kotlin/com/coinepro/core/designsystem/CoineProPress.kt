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
