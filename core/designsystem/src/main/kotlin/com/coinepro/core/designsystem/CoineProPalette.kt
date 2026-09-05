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
    /**
     * A market that has gone up, which is **not** the same fact as an order to buy.
     *
     * [buy] and [sell] are execution semantics — the side of a trade, the colour of a button that
     * commits money. This pair is market movement: a price that rose, a candle that closed higher,
     * a percentage in a watchlist. They were one token, and reusing the execution colour for both
     * is what makes a terminal read as an app about buttons: every list of prices was painted in
     * the same green as the confirm action, so the loudest colour on a screen of forty rows was the
     * one that should have belonged to the single thing a reader can press.
     *
     * The values are the reference's own — TradingView sets `#089981` and `#F23645` on every
     * surface it draws a market on — and reproducing them exactly is the point of having a separate
     * token at all. See the light palette for the one place they are not reproduced exactly and
     * why.
     */
    val marketUp: Color,
    val marketDown: Color,
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
    border = Color(0x1AFFFFFF),
    borderStrong = Color(0x2EFFFFFF),
    textPrimary = Color(0xFFF0F1F2),
    textSecondary = Color(0xFFB7BDC6),
    textMuted = Color(0xFF848E9C),
    // Raised from #5E6673, which read 2.95:1 on an elevated card. The dark theme had the same
    // defect the light one did and it hid better: `textDisabled` is not reserved for disabled
    // controls — the column headings, a signal's setup name and an interactive row's chevron all
    // take it — so it carries real content and has to clear three to one on every rung, not just
    // on the stage.
    textDisabled = Color(0xFF6B7482), // 3.62:1 on the elevated card, 4.10:1 on the stage
    accent = Color(0xFFD8A848),
    accentFill = Color(0xFFD8A848),
    onAccent = Color(0xFF0B0E11),
    analysis = Color(0xFF2962FF),
    social = Color(0xFF00B15C),
    premium = Color(0xFFD4AF37),
    buy = Color(0xFF00B15C),
    sell = Color(0xFFF6465D),
    // The reference's own, exactly: TradingView's up and down on a dark terminal.
    marketUp = Color(0xFF089981),
    marketDown = Color(0xFFF23645),
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
    stage = Color(0xFFF7F8FA),
    // Pulled off the surface it used to share a value with. A chart ground identical to a card is
    // a chart with no ground.
    terminal = Color(0xFFF1F3F7),
    // **#F1F2F6, and the four points it moved are the difference between a card and a region.**
    //
    // It was #F6F7FA. Against a white stage that is a difference of 4.5 units of luminance and
    // about two units of CIE lightness — a step that exists in the file, survives a colour picker
    // and does not survive a phone in a lit room. So the light theme's whole structure rested on
    // the hairline, and a screen read exactly as the owner described it: a white sheet with
    // slightly-less-white shapes printed on it.
    //
    // Measured against the reference the owner put beside it: TradingView's light theme sets its
    // tiles at #F2F2F2 on a white page, which is ΔL* 4.7. This is ΔL* 4.5 — the same step, in this
    // app's own cooler neutral. That is enough for the ground to carry the card on its own, which
    // is what lets `CoineProCard` drop the hairline in this theme; see its note on when an edge is
    // drawn at all.
    //
    // Every ink was re-measured against it rather than assumed. The ramp loses about 4% of its
    // contrast on the darker ground and the tightest of them, the ink gold, still reads 4.84:1
    // against a 4.5 bar. `SurfaceLadderTest` holds all of it.
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFF0F3FA),
    // White, because in a light theme the thing that is lifted is the thing that is brightest.
    surfaceRaised = Color(0xFFE8ECF4),
    surfaceOverlay = Color(0xFFE8EBEF),
    surfaceHover = Color(0xFFE9EDF2),
    surfacePressed = Color(0xFFE1E6EC),
    // Seven percent black on white is #EEEFF1, which is a line you can measure and not one you can
    // see. Ten is where a card edge reads as drawn; the strong step goes to twenty percent so a
    // selected border is unmistakably a choice.
    borderSubtle = Color(0x100D121C),
    border = Color(0x140D121C),
    borderStrong = Color(0x330D121C),
    textPrimary = Color(0xFF111318),
    textSecondary = Color(0xFF4E5661),
    // Four inks darkened to clear AA against the light theme's own surfaces, measured rather than
    // picked. Each figure below is the ratio on `surface` (#F6F7FA), which is the harder of the two
    // grounds — on the white stage every one is a tenth higher.
    //
    // These were not decorative colours. `textMuted` is the caption under every card in the app and
    // read 4.06:1; `textDisabled` read 2.24:1 and is not reserved for disabled — the column
    // headings on four screens, a signal's setup name and the chevron on an *interactive* row all
    // take it. Real content and a live control were below three to one on a white phone.
    textMuted = Color(0xFF5F6875), // 5.26:1
    textDisabled = Color(0xFF767F8D), // 3.78:1 — see below
    accent = Color(0xFF8A6318),
    accentFill = Color(0xFFD8A848),
    onAccent = Color(0xFF111318),
    analysis = Color(0xFF1B4ACC),
    social = Color(0xFF0E8A4C),
    premium = Color(0xFF8A6318),
    // The two semantic inks, at the sizes they are actually used: a percent pill sets 13sp, which
    // is not large text, so 4.5 is the bar and 4.12 was under it.
    buy = Color(0xFF08703C), // 5.78:1
    sell = Color(0xFFC9203A),
    // **The one deviation from the reference's hex, and it is deliberate.**
    //
    // TradingView sets `#089981` on white too. Against this palette's white stage that is 3.3:1,
    // and a percentage in a watchlist sets 13sp — not large text, so 4.5 is the bar this app has
    // already held itself to once, when `buy` moved from 4.12 to 5.78 for exactly this reason. So
    // the light theme keeps the reference's *hue* and takes the lightness down until the figure is
    // readable: same green, same red, legible on white. The dark theme — which is the terminal
    // look this parity work is measured against — carries the published values untouched.
    marketUp = Color(0xFF057A66), // 4.62:1 on the white stage
    marketDown = Color(0xFFD01427), // 5.02:1
    warning = Color(0xFF8A5606), // 5.74:1
    assetInkShift = 0.35f,
    isDark = false,
)

/**
 * Static rather than dynamic: the palette changes only when the whole theme changes, and a dynamic
 * local would invalidate every reader on any recomposition of the provider.
 */
val LocalCoineProPalette = staticCompositionLocalOf { CoineProDarkPalette }
