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

/**
 * The scale, one step above Material's defaults across the board.
 *
 * Two reasons it is not the stock scale. Persian glyphs carry their meaning in marks and curves
 * that are small relative to the letter body — a dot's position separates ب from ت from ث — so
 * Persian needs more size than Latin to stay comfortable at the same reading distance. And this
 * direction spends whitespace generously, which only reads as calm if the type is confident enough
 * to hold the space; at Material's sizes the same layout reads as sparse instead.
 *
 * Letter spacing goes the other way. Material's positive tracking is tuned for Latin and pushes
 * Persian glyphs apart at the joins, so it is dropped to zero everywhere except where a Latin
 * all-caps run needs it.
 */
val CoineProTypography = Typography(
    displayLarge = coineProTextStyle(60, 76, FontWeight.Bold, -0.5),
    displayMedium = coineProTextStyle(48, 62, FontWeight.Bold, -0.25),
    displaySmall = coineProTextStyle(38, 50, FontWeight.Bold),

    headlineLarge = coineProTextStyle(34, 46, FontWeight.Bold),
    headlineMedium = coineProTextStyle(30, 42, FontWeight.Bold),
    headlineSmall = coineProTextStyle(26, 38, FontWeight.Bold),

    titleLarge = coineProTextStyle(24, 36, FontWeight.Bold),
    titleMedium = coineProTextStyle(18, 28, FontWeight.Bold),
    titleSmall = coineProTextStyle(16, 26, FontWeight.Bold),

    bodyLarge = coineProTextStyle(17, 30, FontWeight.Normal),
    bodyMedium = coineProTextStyle(15, 26, FontWeight.Normal),
    bodySmall = coineProTextStyle(13, 22, FontWeight.Normal),

    labelLarge = coineProTextStyle(16, 24, FontWeight.Bold),
    labelMedium = coineProTextStyle(14, 20, FontWeight.Bold),
    labelSmall = coineProTextStyle(13, 18, FontWeight.Bold),
)

/** Styles that carry a specific job rather than a place on the Material scale. */
object CoineProTextStyles {

    /**
     * The account total: the largest thing on any screen it appears on.
     *
     * Sized between [Typography.displaySmall] and [Typography.displayMedium] because neither is
     * right — the smaller does not read as the hero of the screen and the larger wraps a six-figure
     * balance on a narrow phone. The negative tracking is what keeps a long Latin figure from
     * looking loose beside the Persian label above it.
     */
    val Balance: TextStyle = coineProTextStyle(44, 58, FontWeight.Bold, -1.0)

    /**
     * A figure that is the subject of its row rather than an annotation on it — a price in a market
     * list, a position's value. One step above the row's own title so the number leads.
     */
    val RowFigure: TextStyle = coineProTextStyle(17, 26, FontWeight.Bold, -0.2)
}
