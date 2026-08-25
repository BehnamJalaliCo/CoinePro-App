package com.coinepro.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Brand palette, sampled from the CoinePro mark.
 *
 * The logo is gold and silver on nothing else — there is no blue anywhere in it. The accent colour
 * is therefore [Gold], not the lapis that previously carried primary actions and selected states.
 * [GoldBright] and [GoldDeep] are the highlight and shadow stops of the mark's metallic gradient
 * and exist so gradients elsewhere in the product resolve to the same metal.
 *
 * The surface treatment is the "آرام" direction: a near-black stage, opaque cards a step above it,
 * and gold spent **once per screen** on the single primary action. Everything else is neutral, so
 * the one gold object is unambiguous. Putting gold on every selected chip and active tab, as the
 * palette previously did, left the reader no way to tell which one thing the screen wanted.
 *
 * Cards are opaque rather than translucent on purpose: at this level of whitespace a card is
 * defined by its own weight, and a low-alpha pane over a dark stage reads as a smudge rather than
 * as a surface.
 */
object CoineProColors {
    val Stage = Color(0xFF0E0F13)
    val Surface = Color(0xFF171921)
    val SurfaceElevated = Color(0xFF1D1F26)
    val Border = Color(0xFF21242D)

    val TextPrimary = Color(0xFFF4F5F7)
    val TextSecondary = Color(0xFF8B90A0)
    val TextMuted = Color(0xFF71778A)

    /** Mid stop of the mark's gold. Primary accent. */
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

    /**
     * Direction and outcome. Semantic, and deliberately not the accent: gold marks the action the
     * screen wants, green and red report what the market did. Both clear 4.5:1 on [Stage] and on
     * [Surface].
     */
    val Buy = Color(0xFF25C98D)
    val Sell = Color(0xFFFF5F70)
    val Warning = Color(0xFFF3B64A)

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
     * [symbol] is the wire symbol, quote currency included.
     */
    fun assetTint(symbol: String): Color = when (symbol.removeSuffix("USDT").removeSuffix("USD")) {
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
        else -> TextSecondary
    }
}
