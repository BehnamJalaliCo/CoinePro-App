package com.coinepro.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Brand palette, sampled from the CoinePro mark, plus the theme-dependent colours read against it.
 *
 * The logo is gold and silver on nothing else — there is no blue anywhere in it. The accent colour
 * is therefore [Gold], not the lapis that primary actions and selected states once carried.
 * [GoldBright] and [GoldDeep] are the highlight and shadow stops of the mark's metallic gradient
 * and exist so gradients elsewhere in the product resolve to the same metal. All three are fixed:
 * a brand colour that changes with the theme is not a brand colour.
 *
 * Everything else here reads from [LocalCoineProPalette] and therefore needs a composable context.
 * That is deliberate — it is what makes a light theme possible without every screen knowing which
 * theme it is in.
 *
 * The surface treatment is the "آرام" direction: the balance is the hero, gold appears exactly
 * once per screen on the primary action, and cards are plain blocks separated by gap rather than
 * by rules. A second gold object on a screen is a design bug, not a variation.
 */
object CoineProColors {

    /* --------------------------------------------------- brand, fixed in both themes */

    /** Mid stop of the mark's gold. Fills the primary action in both themes. */
    val Gold = Color(0xFFD8A848)

    /** Highlight stop, for gradient tops and pressed states. */
    val GoldBright = Color(0xFFF0CC60)

    /** Shadow stop, for gradient bottoms and borders on gold surfaces. */
    val GoldDeep = Color(0xFF966A1F)

    /**
     * The mark's silver is neutral, not the cool blue-grey this palette used to carry. Sampling it
     * as blue-tinted put a hue in the product that the brand does not contain.
     */
    val Silver = Color(0xFFE4E4E4)
    val SilverMuted = Color(0xFFCCCCCC)

    /* --------------------------------------------------- theme-dependent */

    val Stage: Color @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.stage
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.surface
    val SurfaceElevated: Color
        @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.surfaceElevated
    val Border: Color @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.border

    /**
     * The ground a chart draws on — one step below the stage in the dark theme.
     *
     * A chart is a dense field of thin strokes and it reads better on a ground that recedes further
     * than the page around it.
     */
    val Terminal: Color @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.terminal

    /** One step above [SurfaceElevated] — for a sheet over a card, or a popover over a sheet. */
    val SurfaceOverlay: Color
        @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.surfaceOverlay

    /** Under a pointer, where there is one. */
    val SurfaceHover: Color
        @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.surfaceHover

    /** Under a finger. */
    val SurfacePressed: Color
        @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.surfacePressed

    /** The faintest rule: closes a shape, never divides anything. */
    val BorderSubtle: Color
        @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.borderSubtle

    /** For a selected edge, or a divider that has to be seen rather than felt. */
    val BorderStrong: Color
        @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.borderStrong

    /** Text on a control that cannot be used. Distinct from [TextMuted], which is text that can. */
    val TextDisabled: Color
        @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.textDisabled

    /** The analysis blue — chart, markets, AI. Never decorative; see [PageAccent]. */
    val Analysis: Color @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.analysis

    /** The social green — copy trading, community. */
    val Social: Color @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.social

    /** Classic gold, for subscription and premium only. Never a generic active state. */
    val Premium: Color @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.premium

    /**
     * The hairline around an instrument logo.
     *
     * A separate token from [Border] because it is solving a different problem: many coin marks are
     * a near-black disc, and against this app's near-black stage they have no edge at all. This ring
     * gives them one. It is deliberately faint — enough to close the shape, not enough to read as a
     * border the designer chose — and it flips with the theme so it never becomes the brightest
     * thing in a row of logos.
     */
    val assetRing: Color
        @Composable @ReadOnlyComposable get() =
            if (LocalCoineProPalette.current.isDark) {
                Color(0x29FFFFFF)
            } else {
                Color(0x1F101828)
            }

    val TextPrimary: Color
        @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.textPrimary
    val TextSecondary: Color
        @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.textSecondary
    val TextMuted: Color
        @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.textMuted

    /** Gold as ink. Darkened in the light theme, where the brand mid-tone is unreadable. */
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.accent

    /** The label on a gold fill — near-black in both themes. */
    val OnAccent: Color
        @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.onAccent

    val Buy: Color @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.buy
    val Sell: Color @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.sell
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalCoineProPalette.current.warning

    /**
     * Retained because instrument colouring keys off it: Gold is the metal, Silver is the metal,
     * and both are now brand colours too. Kept as distinct names so a future palette change to the
     * brand does not silently recolour XAUUSD and XAGUSD rows.
     */
    val InstrumentGold = Gold
    val InstrumentSilver = SilverMuted

    /**
     * The asset's own colour, for the round token beside its name.
     *
     * These are the assets' published brand colours, so a reader who knows them from any other
     * exchange finds the same row here. Anything unrecognised falls back to neutral rather than
     * being assigned a colour, because a colour nobody chose still looks like it means something.
     *
     * In the light theme each one is pulled toward black before use: several of them — Solana's
     * green above all — are chosen to glow on a dark ground and vanish on white.
     *
     * [symbol] is the wire symbol, quote currency included.
     */
    @Composable
    @ReadOnlyComposable
    fun assetTint(symbol: String): Color {
        val brand = when (symbol.removeSuffix("USDT").removeSuffix("USD")) {
            "XAU" -> InstrumentGold
            "XAG" -> InstrumentSilver
            "BTC" -> Color(0xFFF7931A)
            "ETH" -> Color(0xFF8098EE)
            "SOL" -> Color(0xFF14F195)
            "BNB" -> Color(0xFFF3BA2F)
            "XRP" -> Color(0xFF8E9BA8)
            "ADA" -> Color(0xFF4C7BEF)
            "TON" -> Color(0xFF35A9EA)
            "DOGE" -> Color(0xFFC2A633)
            else -> return LocalCoineProPalette.current.textSecondary
        }
        val shift = LocalCoineProPalette.current.assetInkShift
        return if (shift == 0f) brand else lerp(brand, Color.Black, shift)
    }
}
