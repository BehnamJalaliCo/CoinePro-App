package com.coinepro.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The product's own icon set.
 *
 * Drawn here rather than pulled from an icon library for two reasons. Every glyph in this direction
 * is a plain geometric silhouette on a 24-unit grid with the same optical weight, which no
 * general-purpose set gives you for free — mixing a house from one family with a sparkle from
 * another shows immediately in a five-item bar. And a path is a few hundred bytes with no
 * dependency and no raster asset at five densities.
 *
 * Each icon is a single filled path. Colour comes from the caller's tint, so the same vector serves
 * the selected and unselected states and both themes.
 */
object CoineProIcons {

    /** A house. The doorway is punched out by [PathFillType.EvenOdd], not drawn as a second shape. */
    val Home: ImageVector = icon(
        name = "Home",
        pathData = "M12 2.6 L21.6 10.4 V20.2 A0.8 0.8 0 0 1 20.8 21 H3.2 " +
            "A0.8 0.8 0 0 1 2.4 20.2 V10.4 Z " +
            "M9.9 21 V14.4 H14.1 V21 Z",
        evenOdd = true,
    )

    /**
     * A rhombus, outlined. This is the mark a trading setup carries throughout the product — entry,
     * stop and target all sit on one axis, and the diamond is that axis seen end-on.
     */
    val Signal: ImageVector = icon(
        name = "Signal",
        pathData = "M12 2.4 L21.6 12 L12 21.6 L2.4 12 Z " +
            "M12 7.2 L7.2 12 L12 16.8 L16.8 12 Z",
        evenOdd = true,
    )

    /** A four-point sparkle, for the assistant. */
    val Ai: ImageVector = icon(
        name = "Ai",
        pathData = "M12 2 C12.7 7.6 16.4 11.3 22 12 C16.4 12.7 12.7 16.4 12 22 " +
            "C11.3 16.4 7.6 12.7 2 12 C7.6 11.3 11.3 7.6 12 2 Z",
    )

    /** Two sliders, for the calculators and converters. */
    val Tools: ImageVector = icon(
        name = "Tools",
        pathData = "M2.6 6.2 H21.4 V7.8 H2.6 Z " +
            "M16 7 m -3 0 a 3 3 0 1 0 6 0 a 3 3 0 1 0 -6 0 " +
            "M2.6 16.2 H21.4 V17.8 H2.6 Z " +
            "M8 17 m -3 0 a 3 3 0 1 0 6 0 a 3 3 0 1 0 -6 0",
    )

    /** A bell, for notifications, fills and history. */
    val Activity: ImageVector = icon(
        name = "Activity",
        pathData = "M12 2.4 A5.4 5.4 0 0 0 6.6 7.8 V11 L4.7 14.6 V15.8 H19.3 V14.6 " +
            "L17.4 11 V7.8 A5.4 5.4 0 0 0 12 2.4 Z " +
            "M9.7 17.2 A2.3 2.3 0 0 0 14.3 17.2 Z",
    )

    private fun icon(name: String, pathData: String, evenOdd: Boolean = false): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(pathData),
            // White, so the caller's tint is what decides the colour in every state and theme.
            fill = SolidColor(Color.White),
            pathFillType = if (evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
        ).build()
}
