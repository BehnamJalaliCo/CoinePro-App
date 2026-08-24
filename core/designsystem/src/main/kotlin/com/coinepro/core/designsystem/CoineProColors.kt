package com.coinepro.core.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Brand palette, sampled from the CoinePro mark.
 *
 * The logo is gold and silver on nothing else — there is no blue anywhere in it. The accent colour
 * is therefore [Gold], not the lapis that previously carried primary actions and selected states.
 * [GoldBright] and [GoldDeep] are the highlight and shadow stops of the mark's metallic gradient
 * and exist so gradients elsewhere in the product resolve to the same metal.
 */
object CoineProColors {
    val Stage = Color(0xFF090B10)
    val Surface = Color(0xFF10131A)
    val SurfaceElevated = Color(0xFF171B24)
    val Border = Color(0xFF282E3A)

    val TextPrimary = Color(0xFFF3F5F8)
    val TextSecondary = Color(0xFF9CA4B4)
    val TextMuted = Color(0xFF687184)

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

    val Buy = Color(0xFF2CCB8E)
    val Sell = Color(0xFFF15B69)
    val Warning = Color(0xFFF3B64A)

    /**
     * Retained because instrument colouring keys off it: Gold is the metal, Silver is the metal,
     * and both are now brand colours too. Kept as distinct names so a future palette change to the
     * brand does not silently recolour XAUUSD and XAGUSD rows.
     */
    val InstrumentGold = Gold
    val InstrumentSilver = SilverMuted
}
