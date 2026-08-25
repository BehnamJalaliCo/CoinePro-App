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
    val Gutter = 20.dp

    /** Between cards in a scrolling stack. */
    val Stack = 20.dp

    /** Inside a card, edge to content. */
    val CardHorizontal = 20.dp
    val CardVertical = 20.dp

    /** Above and below one row inside a card. */
    val Row = 14.dp
}
