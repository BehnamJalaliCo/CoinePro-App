package com.coinepro.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Radii for the "آرام" direction.
 *
 * Generous, because this direction separates content with whitespace and corner radius instead of
 * with rules and shadows. A card here has no border and no elevation, so the curve is the only
 * thing that tells the reader where the surface ends.
 *
 * [extraSmall] stays tight: it carries chips and status pills, where a large radius would make a
 * short Persian label look like a button.
 */
val CoineProShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    // Eighteen, up from sixteen. [large] is the card, and the card is what the owner named when he
    // put this app beside TradingView's: a corner is the one part of a flat surface that is pure
    // manner, and two points of it is the difference between a rectangle with rounded corners and a
    // shape that was drawn. Not more than two: past about twenty a card at this width starts to
    // read as a pill and the content inside it has to be inset to clear the curve.
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** Fully rounded, for the row of primary actions under the balance. */
val CoineProPillShape = RoundedCornerShape(percent = 50)
