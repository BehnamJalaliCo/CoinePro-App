package com.coinepro.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The one formula every tinted surface in this app uses.
 *
 * A card that means something — a warning, a selected row, a premium block — is the ordinary
 * surface pulled 8% toward the meaning's colour, with a border pulled 34%. Not a translucent
 * overlay: alpha over an unknown ground gives a different colour on a card than on the page, and
 * the same "selected" state then looks like two different states depending where it sits.
 *
 * The two numbers are the web terminal's and they are worth keeping exactly. Eight percent is the
 * point where a fill reads as *tinted* rather than as coloured; thirty-four is where a border reads
 * as chosen rather than as a default that happens to be near the accent.
 */
object CoineProTint {

    /** The fill: the base surface, 8% of the way to [accent]. */
    @Composable
    @ReadOnlyComposable
    fun fill(accent: Color, base: Color = CoineProColors.Surface): Color =
        lerp(base, accent, FILL_FRACTION)

    /** The edge: the base border, 34% of the way to [accent]. */
    @Composable
    @ReadOnlyComposable
    fun edge(accent: Color, base: Color = CoineProColors.Border): Color =
        lerp(base, accent, EDGE_FRACTION)

    const val FILL_FRACTION = 0.08f
    const val EDGE_FRACTION = 0.34f
}
