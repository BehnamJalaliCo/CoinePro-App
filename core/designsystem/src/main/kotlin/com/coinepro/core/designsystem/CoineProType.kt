package com.coinepro.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * IRANYekanX (Eco) ships only Regular and Bold, so every style below resolves to one of those two
 * weights instead of relying on synthetic emphasis.
 *
 * The Latin-numeral family is deliberate: prices, quantities and identifiers must stay comparable
 * with broker and exchange terminals, and [com.coinepro.core.common.MarketNumberFormatter] already
 * formats with Latin digits.
 */
val CoineProFontFamily = FontFamily(
    Font(R.font.iranyekanx_regular, FontWeight.Normal),
    Font(R.font.iranyekanx_bold, FontWeight.Bold),
)

/**
 * Persian glyphs sit taller than Latin ones, so line heights are trimmed to the first and last line
 * to keep dense financial rows from gaining uneven vertical padding.
 */
private val PersianLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun coineProTextStyle(
    fontSize: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Double = 0.0,
): TextStyle = TextStyle(
    fontFamily = CoineProFontFamily,
    fontWeight = weight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    lineHeightStyle = PersianLineHeightStyle,
)

val CoineProTypography = Typography(
    displayLarge = coineProTextStyle(57, 72, FontWeight.Bold, -0.25),
    displayMedium = coineProTextStyle(45, 58, FontWeight.Bold),
    displaySmall = coineProTextStyle(36, 48, FontWeight.Bold),

    headlineLarge = coineProTextStyle(32, 44, FontWeight.Bold),
    headlineMedium = coineProTextStyle(28, 40, FontWeight.Bold),
    headlineSmall = coineProTextStyle(24, 36, FontWeight.Bold),

    titleLarge = coineProTextStyle(22, 34, FontWeight.Bold),
    titleMedium = coineProTextStyle(16, 26, FontWeight.Bold, 0.15),
    titleSmall = coineProTextStyle(14, 22, FontWeight.Bold, 0.1),

    bodyLarge = coineProTextStyle(16, 28, FontWeight.Normal, 0.5),
    bodyMedium = coineProTextStyle(14, 24, FontWeight.Normal, 0.25),
    bodySmall = coineProTextStyle(12, 20, FontWeight.Normal, 0.4),

    labelLarge = coineProTextStyle(14, 22, FontWeight.Bold, 0.1),
    labelMedium = coineProTextStyle(12, 18, FontWeight.Bold, 0.5),
    labelSmall = coineProTextStyle(11, 16, FontWeight.Bold, 0.5),
)

/** Styles that carry a specific job rather than a place on the Material scale. */
object CoineProTextStyles {

    /**
     * The account total: the largest thing on any screen it appears on.
     *
     * Sized between [Typography.displaySmall] and [Typography.displayMedium] because neither is
     * right — 36sp does not read as the hero of the screen and 45sp wraps a six-figure balance on a
     * narrow phone. The negative tracking is what keeps a long Latin figure from looking loose
     * beside the Persian label above it.
     */
    val Balance: TextStyle = coineProTextStyle(42, 54, FontWeight.Bold, -1.0)
}
