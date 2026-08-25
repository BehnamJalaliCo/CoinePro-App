package com.coinepro.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every colour that changes between the light and the dark theme.
 *
 * Brand colours are deliberately **not** here. The gold is the gold in both themes — that is what
 * makes it the brand — so it lives on [CoineProColors] as a fixed value and only the colours it is
 * read *against* move. What does change is the gold used as ink: on a white card the brand mid-tone
 * measures 2.1:1 and is unreadable, so [accent] carries a darkened gold in the light theme while
 * [Gold] keeps filling the primary button in both.
 *
 * [onAccent] is the label on a gold fill. It is near-black in both themes for the same reason: the
 * brand gold is a mid-tone, and white on it fails contrast whichever theme the reader is in.
 */
@Immutable
data class CoineProPalette(
    val stage: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val buy: Color,
    val sell: Color,
    val warning: Color,
    /** How far an asset's brand colour is pulled toward black before it is used as ink. */
    val assetInkShift: Float,
    val isDark: Boolean,
)

/**
 * The dark theme.
 *
 * A near-black stage with cards one step above it, and no borders anywhere — a card is separated
 * from its neighbour by the gap between them.
 */
val CoineProDarkPalette = CoineProPalette(
    stage = Color(0xFF0E0F13),
    surface = Color(0xFF171921),
    surfaceElevated = Color(0xFF1D1F26),
    border = Color(0xFF21242D),
    textPrimary = Color(0xFFF4F5F7),
    textSecondary = Color(0xFF8B90A0),
    textMuted = Color(0xFF71778A),
    accent = Color(0xFFD8A848),
    onAccent = Color(0xFF0E0F13),
    buy = Color(0xFF25C98D),
    sell = Color(0xFFFF5F70),
    warning = Color(0xFFF3B64A),
    assetInkShift = 0f,
    isDark = true,
)

/**
 * The light theme.
 *
 * Not an inversion. The stage is a soft neutral and the cards are pure white, so the depth order
 * stays the same as in dark — cards read as sitting *above* the page in both. Inverting instead
 * would put white behind white cards and lose the whole structure.
 *
 * Green and red are darkened well past their dark-theme values: the greens that carry a gain on
 * near-black measure under 2:1 on white.
 */
val CoineProLightPalette = CoineProPalette(
    stage = Color(0xFFF4F5F7),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFEBEDF1),
    border = Color(0xFFE2E5EA),
    textPrimary = Color(0xFF14161B),
    textSecondary = Color(0xFF5C6172),
    textMuted = Color(0xFF868C9C),
    accent = Color(0xFF8A6318),
    onAccent = Color(0xFF14161B),
    buy = Color(0xFF0E9F6E),
    sell = Color(0xFFD93A4A),
    warning = Color(0xFFB4700C),
    assetInkShift = 0.35f,
    isDark = false,
)

/**
 * Static rather than dynamic: the palette changes only when the whole theme changes, and a dynamic
 * local would invalidate every reader on any recomposition of the provider.
 */
val LocalCoineProPalette = staticCompositionLocalOf { CoineProDarkPalette }
