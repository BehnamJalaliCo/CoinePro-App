package com.coinepro.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The design tokens, under the names the visual audit uses.
 *
 * One source, not a second one: every value here is the palette role, the shape or the spacing
 * the rest of the design system already reads (`CoineProPalette`, `CoineProShapes`,
 * `CoineProSpacing`), exposed under the audit's vocabulary so a reviewer holding the spec and a
 * developer holding the code are looking at the same number. Change the role, and this changes.
 *
 * ### Surfaces
 *
 * `surface0` is the page and it is never `#000`: the dark theme sits on a deep navy (`#0B0E11`),
 * because pure black makes every card float and turns the chart's grid into a starfield. Each step
 * up is a plate on the one below it. In the light theme the page is `#F7F8FA` and the cards are
 * white — the reference app's canvas and its tiles.
 *
 * ### Accent
 *
 * The gold is one colour and it is used sparingly: the primary action, the selected state, the
 * brand mark. Not headings, not icons, not borders. A screen that is more than a tenth gold is a
 * screen that has lost the thing gold is for.
 *
 * ### Dynamic colour
 *
 * Off. A trading app's green and red are facts, not preferences, and a wallpaper must not be able
 * to turn a loss teal.
 */
object CoineProTokens {

    /* ------------------------------------------------------------------ surfaces */

    /** The page. Deep navy in the dark theme, off-white in the light. */
    val surface0: Color @Composable @ReadOnlyComposable get() = CoineProColors.Stage

    /** A card, a sheet. */
    val surface1: Color @Composable @ReadOnlyComposable get() = CoineProColors.Surface

    /** A tile on a card, a chip, a skeleton bar. */
    val surface2: Color @Composable @ReadOnlyComposable get() = CoineProColors.SurfaceElevated

    /** The topmost plate — a popover, a selected tile. */
    val surface3: Color @Composable @ReadOnlyComposable get() = CoineProColors.SurfaceRaised

    /** Hairlines and card edges: white at 10 % on dark, ink at 8 % on light. */
    val outline: Color @Composable @ReadOnlyComposable get() = CoineProColors.Border

    /* ------------------------------------------------------------------ market */

    /** A rising figure. Honours the reader's green-up / red-up choice through the palette. */
    val up: Color @Composable @ReadOnlyComposable get() = CoineProColors.MarketUp

    /** A falling figure. */
    val down: Color @Composable @ReadOnlyComposable get() = CoineProColors.MarketDown

    /* ------------------------------------------------------------------ ink */

    val textPrimary: Color @Composable @ReadOnlyComposable get() = CoineProColors.TextPrimary
    val textSecondary: Color @Composable @ReadOnlyComposable get() = CoineProColors.TextSecondary
    val textMuted: Color @Composable @ReadOnlyComposable get() = CoineProColors.TextMuted

    /** The one accent. See the class note on how little of a screen it may cover. */
    val accent: Color @Composable @ReadOnlyComposable get() = CoineProColors.AccentFill

    /** A figure that is older than it should be — amber, never red, because stale is not wrong. */
    val stale: Color @Composable @ReadOnlyComposable get() = CoineProColors.Warning

    /* ------------------------------------------------------------------ shape */

    /** Corner radii, in dp: a label on the chart, a chip that is not a pill, a button, a card, a sheet. */
    object Radius {
        val chartLabel: Dp = 4.dp
        val field: Dp = 8.dp
        val button: Dp = 12.dp
        val card: Dp = 16.dp
        val sheet: Dp = 28.dp
    }

    /* ------------------------------------------------------------------ space */

    /**
     * The grid is eight points; four is the half step. Every padding and every gap in the app is
     * a multiple of four — `check-cross-phase-consistency.py` (`check_grid`) refuses a literal
     * that is not. Sizes (an icon at 22 dp, a row at 58 dp) are not paddings and are not gated.
     */
    object Space {
        val x1: Dp = CoineProSpacing.Half
        val x2: Dp = CoineProSpacing.One
        val x3: Dp = CoineProSpacing.OneHalf
        val x4: Dp = CoineProSpacing.Two
        val x6: Dp = CoineProSpacing.Three
        val x8: Dp = CoineProSpacing.Four
        val gutter: Dp = CoineProSpacing.Gutter
        val section: Dp = CoineProSpacing.Stack
    }
}
