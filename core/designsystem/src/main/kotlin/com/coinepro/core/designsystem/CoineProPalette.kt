package com.coinepro.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every colour that changes between the light and the dark theme.
 *
 * The surface ladder is `foundation-v2.css`, the token layer the owner's web terminal already
 * ships. Adopting it rather than reinventing one buys three things the previous ad-hoc values did
 * not have: a *five*-step ladder instead of three, so a sheet over a card over the page is legible
 * without borders; separate hover and pressed steps, so a pressed row does not have to be
 * simulated with alpha; and three border weights, so a hairline that closes a shape and a rule
 * that divides a list are not the same colour.
 *
 * A sixth step, [surfaceRaised], is this app's own and not the web terminal's — see its own note
 * for the light-theme inversion it exists to fix. And every border weight moved up: the ladder was
 * being asked to carry the whole structure on fill alone, which is what a reader means when they
 * say a flat interface looks printed rather than built. Fill says which rung; the hairline says
 * there is an edge at all.
 *
 * **The brand gold is not adopted.** `foundation-v2` uses Binance yellow `#F0B90B` for brand and
 * execution; this app's gold is `#D8A848`, sampled from the CoinePro mark. Taking the web
 * terminal's yellow would change the brand to another company's, which is the one thing in that
 * file that is theirs rather than structural. The structure is adopted; the identity is not.
 *
 * Brand colours are deliberately not in here. The gold is the gold in both themes — that is what
 * makes it the brand — so it lives on [CoineProColors] as a fixed value and only the colours it is
 * read *against* move. What does change is the gold used as ink: on a white card the brand
 * mid-tone measures 2.1:1 and is unreadable, so [accent] carries a darkened gold in the light
 * theme while `Gold` keeps filling the primary button in both.
 *
 * [onAccent] is the label on a gold fill. It is near-black in both themes for the same reason: the
 * brand gold is a mid-tone, and white on it fails contrast whichever theme the reader is in.
 */
@Immutable
data class CoineProPalette(
    /** The page behind everything. */
    val stage: Color,
    /**
     * The ground a chart draws on — one step *darker* than the stage in the dark theme.
     *
     * Deliberately not the stage. A chart is a dense field of thin strokes and it reads better on
     * a ground that recedes further than the page around it; the web terminal makes the same
     * distinction and calls it `--pc-bg-terminal`.
     */
    val terminal: Color,
    val surface: Color,
    val surfaceElevated: Color,
    /**
     * Something lifted *out of* the container it sits in, rather than one rung further down the
     * page's own ladder.
     *
     * The distinction sounds pedantic and it is the reason the light theme's segmented control was
     * drawn upside down. A selected segment is supposed to read as raised out of its tray; the tray
     * is [surface] and the segment took [surfaceElevated], which in the light theme is *darker*
     * than the tray. So the selected tab was a hole, and the two unselected ones were the surface —
     * which is exactly backwards, and it is why nothing in those screenshots looked like it sat on
     * anything.
     *
     * In the dark theme "raised" means lighter and in the light theme it means white. That cannot
     * be expressed by a rung on a single monotonic ladder, which is why it is its own token.
     */
    val surfaceRaised: Color,
    /** One step above elevated — for a sheet over a card, or a popover over a sheet. */
    val surfaceOverlay: Color,
    /** Under a pointer, where there is one. */
    val surfaceHover: Color,
    /** Under a finger. */
    val surfacePressed: Color,
    /** The faintest rule: closes a shape, never divides anything. */
    val borderSubtle: Color,
    val border: Color,
    /** For a selected edge, or a divider that has to be seen rather than felt. */
    val borderStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    /** Text on a control that cannot be used. Distinct from muted, which is text that can. */
    val textDisabled: Color,
    /**
     * Gold **as ink** — a label, an icon, a value.
     *
     * Darkened well past the brand gold in the light theme, where the mid-tone measures 2.1:1 on
     * white and is unreadable.
     */
    val accent: Color,
    /**
     * Gold **as a fill** — the primary button, a selected chip.
     *
     * The brand gold in both themes, which is what makes it the brand. Separate from [accent] and
     * the separation matters: filling a button with the light theme's ink gold gives near-black
     * text on dark brown, which is the exact failure this pair exists to prevent.
     */
    val accentFill: Color,
    val onAccent: Color,
    /**
     * The analysis blue.
     *
     * A domain colour rather than a decorative one — see [PageAccent]. It is the one hue in this
     * app that is not gold, and it exists because a chart screen full of gold selection states
     * competes with the gold that means "this is the primary action".
     */
    val analysis: Color,
    /** The social green — copy trading, community. Same hue as [buy] by design, not by accident. */
    val social: Color,
    /** Classic gold, for subscription and premium only. Never a generic active state. */
    val premium: Color,
    val buy: Color,
    val sell: Color,
    val warning: Color,
    /** How far an asset's brand colour is pulled toward black before it is used as ink. */
    val assetInkShift: Float,
    val isDark: Boolean,
)

/**
 * The dark theme, on `foundation-v2`'s neutral ladder.
 *
 * A near-black stage with a five-step ladder above it. Cards are still separated from their
 * neighbours by gap rather than by rules; the borders here are for closing shapes and marking
 * selection, not for dividing lists.
 */
val CoineProDarkPalette = CoineProPalette(
    stage = Color(0xFF0B0E11),
    terminal = Color(0xFF070A0F),
    surface = Color(0xFF10141B),
    surfaceElevated = Color(0xFF171C24),
    surfaceRaised = Color(0xFF222831),
    surfaceOverlay = Color(0xFF1E2329),
    surfaceHover = Color(0xFF252A31),
    surfacePressed = Color(0xFF2B3139),
    // Raised from 5% to 8% white. At 5% a hairline on a #10141B card over a #0B0E11 stage is below
    // the threshold an OLED panel resolves at all, so the card had no edge and the whole ladder
    // depended on a two-level fill difference nobody could see.
    borderSubtle = Color(0x14FFFFFF),
    border = Color(0x1EFFFFFF),
    borderStrong = Color(0x2EFFFFFF),
    textPrimary = Color(0xFFF0F1F2),
    textSecondary = Color(0xFFB7BDC6),
    textMuted = Color(0xFF848E9C),
    textDisabled = Color(0xFF5E6673),
    accent = Color(0xFFD8A848),
    accentFill = Color(0xFFD8A848),
    onAccent = Color(0xFF0B0E11),
    analysis = Color(0xFF2962FF),
    social = Color(0xFF00B15C),
    premium = Color(0xFFD4AF37),
    buy = Color(0xFF00B15C),
    sell = Color(0xFFF6465D),
    warning = Color(0xFFF0B90B),
    assetInkShift = 0f,
    isDark = true,
)

/**
 * The light theme.
 *
 * Not an inversion. The stage is white and the ladder climbs *down* into grey, so a card still
 * reads as sitting above the page — inverting instead would put white cards on a white page and
 * lose the whole structure.
 *
 * Green and red are darkened well past their dark-theme values: the greens that carry a gain on
 * near-black measure under 2:1 on white. The analysis blue is darkened for the same reason.
 */
val CoineProLightPalette = CoineProPalette(
    stage = Color(0xFFFFFFFF),
    // Pulled off the surface it used to share a value with. A chart ground identical to a card is
    // a chart with no ground.
    terminal = Color(0xFFF1F3F7),
    surface = Color(0xFFF6F7FA),
    surfaceElevated = Color(0xFFEBEEF3),
    // White, because in a light theme the thing that is lifted is the thing that is brightest.
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceOverlay = Color(0xFFE8EBEF),
    surfaceHover = Color(0xFFE9EDF2),
    surfacePressed = Color(0xFFE1E6EC),
    // Seven percent black on white is #EEEFF1, which is a line you can measure and not one you can
    // see. Ten is where a card edge reads as drawn; the strong step goes to twenty percent so a
    // selected border is unmistakably a choice.
    borderSubtle = Color(0x1A0D121C),
    border = Color(0x240D121C),
    borderStrong = Color(0x330D121C),
    textPrimary = Color(0xFF111318),
    textSecondary = Color(0xFF4E5661),
    textMuted = Color(0xFF707A88),
    textDisabled = Color(0xFFA1A8B2),
    accent = Color(0xFF8A6318),
    accentFill = Color(0xFFD8A848),
    onAccent = Color(0xFF111318),
    analysis = Color(0xFF1B4ACC),
    social = Color(0xFF0E8A4C),
    premium = Color(0xFF8A6318),
    buy = Color(0xFF0E8A4C),
    sell = Color(0xFFC9203A),
    warning = Color(0xFFB4700C),
    assetInkShift = 0.35f,
    isDark = false,
)

/**
 * Static rather than dynamic: the palette changes only when the whole theme changes, and a dynamic
 * local would invalidate every reader on any recomposition of the provider.
 */
val LocalCoineProPalette = staticCompositionLocalOf { CoineProDarkPalette }
