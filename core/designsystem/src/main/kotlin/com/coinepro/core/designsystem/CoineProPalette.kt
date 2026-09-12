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
    /**
     * Which palette this is, for a test's failure message and nothing else.
     *
     * Added when [CoineProMidnightPalette] arrived (run Ω2) and `isDark=true` stopped identifying a
     * palette: two of the three are dark, so «a hairline is too faint (isDark=true)» named neither.
     * Never shown to a reader — the theme names readers see are string resources.
     */
    val name: String = "",
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
    // **The same ground as the stage since 4.70.0.** The chart used to sit four units darker, and
    // the argument for it was that a chart wants a ground of its own. Held against the app — Home
    // at `#0B0E11`, the chart at `#070A0F`, one tap apart — it read as two different apps, which
    // is the owner's own reading of the two screenshots side by side (run F). One `surface0`.
    terminal = Color(0xFF0B0E11),
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
    // One green and one red in the whole theme (run F).
    //
    // There were three greens on one screen: `#089981` on the candles, `#00B15C` on a sparkline
    // and the same again on a change pill. Every one of them meant "up", and a reader cannot be
    // asked to learn that the green of a rising candle and the green of a rising sparkline are the
    // same fact in two shades. The distinction this file used to draw — [buy] as an *order* and
    // [marketUp] as a *market* — survives in the field names and in where each is used; it no
    // longer survives as a difference in hue, because on screen there was no way to read it.
    social = Color(0xFF089981),
    premium = Color(0xFFD4AF37),
    buy = Color(0xFF089981),
    // **One green, and a red three points off the candle's.**
    //
    // The green is TradingView's own `#089981` and it reads 4.85:1 as ink on the elevated card,
    // so the candle colour and the figure colour are one value with nothing given up.
    //
    // The red cannot be. TradingView's `#F23645` measures **4.44:1** on that same card, and a
    // change pill sets 13sp — not large text, so 4.5 is the bar this file has held itself to
    // twice already. Three points lighter clears it at 4.89:1 and is indistinguishable beside it.
    // So the *candles* keep the reference's red — it is a fill, drawn from `TradingViewPalette`,
    // and no figure is set on it — and every red **figure** in the app is this one.
    sell = Color(0xFFF6465D), // 4.89:1 on the elevated card
    marketUp = Color(0xFF089981), // 4.85:1
    marketDown = Color(0xFFF6465D),
    warning = Color(0xFFF0B90B),
    assetInkShift = 0f,
    isDark = true,
    name = "dark",
)

/**
 * **Midnight** — the dark theme on true black (run Ω2).
 *
 * ### Why a third palette and not a switch inside the second
 *
 * On an OLED panel a `#000000` pixel is off. That is worth an option for two reasons a trader will
 * give you unprompted: the chart at night stops glowing around its own edges, and a phone left on a
 * chart all day spends measurably less battery on the two thirds of the screen that are background.
 * It is *not* the default, because true black is also where a one-pixel hairline disappears and a
 * near-black card has no shadow to sit in — which is why this is a ladder shifted down by a rung
 * rather than a flat fill of black.
 *
 * ### What it changes, which is only the ground
 *
 * Every ink, every hue and every rung above the page is the dark theme's, unchanged, by construction
 * — this is a `copy` and the compiler holds it to that. **Three** values move: the page, the chart's
 * pane, and the card that sits directly on them.
 *
 * The ladder is not shifted wholesale, and that was the first attempt. Moving every rung down put
 * `surfaceRaised` at `#171C24` over a `#0B0E11` card, and near black the *linear* luminance between
 * two such values is a few thousandths — `SurfaceLadderTest`'s «a raised surface is never the same
 * value as the container it is raised out of» caught it, which is what that test is for. A plate
 * lifted off a card has to still read as lifted, and on a black page the way to get that is not to
 * push the whole ladder down into the region where it stops separating.
 *
 * So a new rung is *inserted at the bottom* instead: `surface` becomes the dark theme's stage —
 * `#0B0E11`, the value every ink in this file was already measured against — and everything above it
 * stays exactly where it was. The result is one more step of structure than the dark theme has, all
 * of it above a page that is genuinely off.
 */
val CoineProMidnightPalette = CoineProDarkPalette.copy(
    stage = Color(0xFF000000),
    // Black, like the stage — the chart shares the page's ground in every theme since 4.70.0, and
    // on an OLED panel this is the whole point of the option: the pane behind the candles is off.
    terminal = Color(0xFF000000),
    // The dark theme's *stage* as this theme's card. A card here is a card there, so nothing above
    // it needed re-measuring and nothing below it is lighter than it was.
    surface = Color(0xFF0B0E11),
    // Raised from eight per cent, for the reason the dark theme raised them from five: an edge has to
    // survive the panel. On a black page a card's own fill is doing less of the work than it does on
    // `#0B0E11`, so the hairline is carrying more of the structure and has to be seen.
    borderSubtle = Color(0x1FFFFFFF),
    border = Color(0x26FFFFFF),
    borderStrong = Color(0x3AFFFFFF),
    name = "midnight",
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
    // The stage's own value since 4.70.0, for the reason the dark theme gives: one `surface0` for
    // the whole app. The chart still reads as a region — its plot is drawn inside a frame with its
    // own hairline — without the page changing colour under it.
    terminal = Color(0xFFF7F8FA),
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
    // One green and one red here too — see the dark theme's note. The pair kept is the *market*
    // pair below, which is the reference's hue darkened until it clears 4.5:1 on white: white on
    // `#057A66` measures 5.34:1 and on `#D01427` 5.54:1, so a filled buy button is as legible as
    // the darker green it replaces while a rising candle, a sparkline and a percent pill are
    // finally one colour.
    social = Color(0xFF057A66),
    premium = Color(0xFF8A6318),
    buy = Color(0xFF057A66), // 5.34:1 under white
    sell = Color(0xFFD01427), // 5.54:1 under white
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
    name = "light",
)

/**
 * Static rather than dynamic: the palette changes only when the whole theme changes, and a dynamic
 * local would invalidate every reader on any recomposition of the provider.
 */
val LocalCoineProPalette = staticCompositionLocalOf { CoineProDarkPalette }
