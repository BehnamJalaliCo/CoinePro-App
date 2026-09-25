package com.coinepro.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Five radii, each with one job. See `docs/design/TOKENS.md`.
 *
 *  * `extraSmall` 4 — a label on the chart's axis, a badge.
 *  * `small` 8 — a chip that is not a pill, a text field.
 *  * `medium` 12 — a button.
 *  * `large` 16 — a card. Sixteen, down from eighteen: the reference this app is measured against
 *    draws its cards at sixteen, and a card that is rounder than the sheet it sits in reads as
 *    softer than the product around it.
 *  * `extraLarge` 16 — a sheet's top corners. Sixteen, down from Material's twenty-eight: the
 *    reference's phone sheets are twelve to sixteen, and a full-height sheet over a chart left a
 *    rounded sliver of candles in each corner.
 */
val CoineProShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(CoineProTokens.Radius.sheet),
)

val CoineProPillShape = RoundedCornerShape(percent = 50)
