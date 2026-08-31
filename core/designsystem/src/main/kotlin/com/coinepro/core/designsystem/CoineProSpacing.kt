package com.coinepro.core.designsystem

import androidx.compose.ui.unit.dp

/**
 * The spacing scale.
 *
 * A 4dp base with the steps the "آرام" direction actually uses. [Gutter] and [Stack] are named for
 * their job rather than their size because they are the two decisions that carry the direction: how
 * far content sits from the edge of the phone, and how far one card sits from the next. Changing
 * either changes the feel of every screen at once, which is exactly why they should not be typed as
 * loose numbers at each call site.
 */
object CoineProSpacing {
    val Half = 4.dp
    val One = 8.dp
    val OneHalf = 12.dp
    val Two = 16.dp
    val Three = 24.dp
    val Four = 32.dp
    val Six = 48.dp

    /** Screen edge to content. */
    /**
     * Screen edge to content.
     *
     * Sixteen, which is what Markets, Signals and Tools already used while Home, Profile and
     * Notifications used twenty — so a reader moving between them watched the content edge jump
     * four points. Cards also gain eight points of usable width, which lets a row breathe sideways
     * instead of getting taller.
     */
    val Gutter = 16.dp

    /** Between cards in a scrolling stack. */
    /**
     * Between two cards.
     *
     * It has to be **larger than the padding inside a card** or the cards read as slabs rather than
     * as a rhythm. Profile and Notifications had it inverted — 8dp between, 20dp within — which is
     * exactly what made those two screens the heaviest in the app.
     *
     * Twenty-four, up from twenty, and it is the cheapest half of what the owner was asking for
     * when he said TradingView's screens are calm enough to work in for hours. Calm is not a
     * colour; it is the ratio between the space *around* a group and the space *inside* it. At
     * 20-against-16 that ratio was 1.25 and the eye has to work to find the seam between two cards;
     * at 24-against-18 it is 1.33, which is where a stack reads as separate objects at a glance.
     */
    val Stack = 24.dp

    /**
     * Inside a card, edge to content.
     *
     * Eighteen on both axes, up from sixteen. One number for both, and the same number in every
     * card in the app, because the thing that reads as cheap is not a padding that is too small —
     * it is a padding that is 16 here and 20 there and 12 in the card underneath. The pair is kept
     * as two named values rather than one so that a future direction can set them apart
     * deliberately; nothing should set them apart by accident.
     */
    val CardHorizontal = 18.dp
    val CardVertical = 18.dp

    /** Above and below one row inside a card. */
    val Row = 10.dp
}
