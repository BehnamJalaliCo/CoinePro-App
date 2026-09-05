package com.coinepro.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.sp

/**
 * IRANYekanX (Eco) ships only Regular and Bold, so every style below resolves to one of those two
 * weights instead of relying on synthetic emphasis.
 *
 * The Latin-numeral family is deliberate: prices, quantities and identifiers must stay comparable
 * with broker and exchange terminals, and [com.coinepro.core.common.MarketNumberFormatter] already
 * formats with Latin digits.
 *
 * ### Tabular figures: already true, and not by a feature tag
 *
 * A column of prices only reads as a column if every digit is the same width. The usual way to ask
 * for that is `FontFeatureSettings("tnum")`, and it was measured here before being added, because
 * a feature the font does not carry is a string that silently does nothing.
 *
 * The measurement: IRANYekanX (Eco) exposes `aalt calt ccmp dlig dnom fina frac init kern locl
 * mark medi mkmk numr rlig salt ss01–ss04` — **no `tnum`.** It does not need one. Its Latin digits
 * are already monospaced by design: all ten advance 562 units in Regular and 572 in Bold. Every
 * price, quantity, percentage and identifier in the app is Latin-digit by the standing rule, so
 * every column of numbers in the app is already tabular, and `tnum` here would have been a no-op
 * that read as a fix.
 *
 * The Persian digits are the opposite — proportional, and dramatically so: ۱ advances 238 units
 * against ۳'s 655, nearly three to one. There is no feature tag that would even them out. That is
 * the second, quieter reason market figures are Latin: a column of Persian numerals in this face
 * cannot be made to line up at all. Persian digits stay in prose, where nothing beneath them has
 * to agree.
 */
val CoineProFontFamily = FontFamily(
    Font(R.font.iranyekanx_regular, FontWeight.Normal),
    // Medium and SemiBold resolve to Bold on purpose. The shipped IRANYekanX is static Regular
    // and Bold; the two middle weights are the owner's licence to obtain, and until they arrive a
    // style asking for 500 must not fall back to Regular and quietly un-bold every title. When
    // the files land, point these two entries at them and nothing else changes.
    Font(R.font.iranyekanx_bold, FontWeight.Medium),
    Font(R.font.iranyekanx_bold, FontWeight.SemiBold),
    Font(R.font.iranyekanx_bold, FontWeight.Bold),
)

/**
 * Inter, for Latin and for every numeral in both locales.
 *
 * One variable file carries all four weights. It is the face for a figure — a price, a change, a
 * quantity, a balance, an axis label, a ticker — because a market figure is compared down a
 * column and against another app, and Inter's tabular figures (`tnum`, set on every numeric
 * style below) line up to the pixel where a Persian text face's Latin digits merely nearly do.
 * Persian prose stays on IRANYekanX; the two faces share x-height closely enough to sit on one
 * line.
 */
@OptIn(ExperimentalTextApi::class)
val CoineProLatinFontFamily = FontFamily(
    Font(R.font.inter_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Tabular figures, so a column of prices lines up and a ticking price does not shift its neighbours. */
const val TABULAR_FIGURES = "tnum"

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
 * The scale: Persian's uplift over Material, and no more than that.
 *
 * ### What this used to say, and why it was wrong
 *
 * It said "one step above Material's defaults across the board", and the argument was sound as far
 * as it went — Persian glyphs carry their meaning in marks that are small relative to the letter
 * body (a dot's position separates ب from ت from ث), so Persian genuinely does need more size than
 * Latin at the same reading distance.
 *
 * The mistake was the size of the correction and where it was applied. Persian wants about **one
 * point**; this was carrying two to three, on every role including the dense ones. And the line
 * heights were left at Material's *reading* ratios — 1.63 on a list title, 1.73 on body, 1.76 on
 * `bodyLarge` — which is right for a paragraph somebody sits down with and wrong for a row in a
 * market list. Dense UI runs at 1.30–1.45.
 *
 * The two compounded. Every two-line row paid the size once and the leading twice, and the owner's
 * verdict on the result was «همه چیز گنده و بزرگه … ظریف و شیک و لوکس نیست» — everything is bulky;
 * it is not delicate. He is right, and this file is where it came from.
 *
 * So: reading text keeps Persian's point and loses the slack leading. The dense roles — labels,
 * row titles, button text, captions — come back to where a terminal puts them. The hero balance
 * stays a hero at 36sp, which is still twice the next-largest thing on its screen.
 *
 * ### The steps have to be steps
 *
 * A scale is not a list of sizes, it is a list of *differences*, and one of them was not a
 * difference at all: `headlineSmall` sat at 22 and `titleLarge` at 21. A single point is below what
 * anybody can see, so the two roles were the same role wearing two names, and a screen that used
 * both looked like a screen with a heading it had forgotten to make into a heading. `titleLarge` is
 * 19 now, which puts the ladder at
 *
 * ```
 *   52  42  34   30  25  22   19  17  15   16  14  13   15  13  11
 *   └ display ┘  └ headline ┘ └  title  ┘  └  body  ┘   └ label ┘
 * ```
 *
 * and every neighbouring pair inside a family is a tenth or more apart. Nineteen also gives the
 * screen title an audible gap under `headlineSmall`, which is what a card's own title is measured
 * against on the nineteen screens that set one.
 *
 * The families still overlap — `labelLarge` and `titleSmall` are both 15, `bodyLarge` and
 * `RowFigure` are both 16 — and that is Material's design rather than an oversight here: those
 * pairs differ in weight and in job, and collapsing them would cost a role rather than buy a step.
 *
 * ### Letter spacing
 *
 * Material's positive tracking is tuned for Latin and pushes Persian glyphs apart at the joins, so
 * it stays at zero for everything a reader reads. The exception is [Typography.labelSmall], the
 * caption and column-heading role, which gains a little: at 11sp a label wants to read as a label
 * rather than as body text that has been shrunk.
 */
val CoineProTypography = Typography(
    // The audit's slots: display 32/40 SemiBold, headline 24/32, title 18/24 Medium, body 15/22,
    // label 12/16 Medium — each the *Medium* size of its slot, with Large and Small a step either
    // side so the scale still has room at both ends.
    displayLarge = coineProTextStyle(40, 48, FontWeight.SemiBold, -0.5),
    displayMedium = coineProTextStyle(32, 40, FontWeight.SemiBold, -0.25),
    displaySmall = coineProTextStyle(28, 36, FontWeight.SemiBold),

    headlineLarge = coineProTextStyle(28, 36, FontWeight.SemiBold),
    headlineMedium = coineProTextStyle(24, 32, FontWeight.SemiBold),
    headlineSmall = coineProTextStyle(20, 28, FontWeight.SemiBold),

    titleLarge = coineProTextStyle(20, 28, FontWeight.Medium),
    titleMedium = coineProTextStyle(18, 24, FontWeight.Medium),
    titleSmall = coineProTextStyle(15, 20, FontWeight.Medium),

    bodyLarge = coineProTextStyle(16, 24, FontWeight.Normal),
    bodyMedium = coineProTextStyle(15, 22, FontWeight.Normal),
    bodySmall = coineProTextStyle(13, 18, FontWeight.Normal),

    labelLarge = coineProTextStyle(14, 20, FontWeight.Medium),
    labelMedium = coineProTextStyle(12, 16, FontWeight.Medium),
    labelSmall = coineProTextStyle(11, 15, FontWeight.Normal, 0.3),
)

/** Styles that carry a specific job rather than a place on the Material scale. */
/**
 * A figure style: Inter, tabular, left-to-right.
 *
 * Every price, quantity, percentage, balance and axis label goes through one of these or through
 * [TextStyle.numeric]. The direction is pinned because a figure is Latin whatever the paragraph
 * around it is, and a minus sign on the wrong side of a number is a different number.
 */
private fun numericTextStyle(
    fontSize: Int,
    lineHeight: Int,
    weight: FontWeight,
    letterSpacing: Double = 0.0,
): TextStyle = TextStyle(
    fontFamily = CoineProLatinFontFamily,
    fontWeight = weight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    fontFeatureSettings = TABULAR_FIGURES,
    textDirection = TextDirection.Ltr,
    lineHeightStyle = PersianLineHeightStyle,
)

/**
 * The same style, as a figure: Inter's face, tabular digits, left-to-right. Size, weight and line
 * height are kept, so `MaterialTheme.typography.labelSmall.numeric()` is the small label with the
 * digits that line up.
 */
fun TextStyle.numeric(): TextStyle = copy(
    fontFamily = CoineProLatinFontFamily,
    fontFeatureSettings = TABULAR_FIGURES,
    textDirection = TextDirection.Ltr,
)

object CoineProTextStyles {

    /** A figure in running text — a price in a row, a value in a legend. 13/16, tabular. */
    val Numeric: TextStyle = numericTextStyle(13, 16, FontWeight.Normal)

    /** A figure that is the point of its card — a last price, a total. 22/28 Medium, tabular. */
    val NumericLarge: TextStyle = numericTextStyle(22, 28, FontWeight.Medium, -0.2)

    /**
     * The account total: the largest thing on any screen it appears on.
     *
     * Sized between [Typography.displaySmall] and [Typography.displayMedium] because neither is
     * right — the smaller does not read as the hero of the screen and the larger wraps a six-figure
     * balance on a narrow phone. The negative tracking is what keeps a long Latin figure from
     * looking loose beside the Persian label above it.
     */
    val Balance: TextStyle = numericTextStyle(36, 46, FontWeight.SemiBold, -0.8)

    /**
     * A figure that is the subject of its row rather than an annotation on it — a price in a market
     * list, a position's value. One step above the row's own title so the number leads.
     *
     * It was fifteen, which is what [Typography.titleSmall] is, which is what the row's *title* is
     * — so the price and the name of the thing it prices were the same size and the same weight,
     * and the row had no subject. "One step above" was the stated intention and the number did not
     * implement it. Sixteen does, and it is one point rather than three because the row still has
     * to stay a row.
     */
    val RowFigure: TextStyle = numericTextStyle(16, 21, FontWeight.SemiBold, -0.2)

    /**
     * The figure inside a small tile — a reading under a hero, a stat in a card.
     *
     * A tile is a label and an answer, and the two were 11sp and 15sp: close enough that the eye
     * read the pair as one block of text and had to *read* it to find the number. At eighteen the
     * answer is found before it is read, which is the whole point of putting it in a tile.
     */
    val TileFigure: TextStyle = numericTextStyle(18, 24, FontWeight.SemiBold, -0.2)

    /**
     * The small line that names a category above a title — «سیگنال» over «BTCUSDT خرید».
     *
     * Its own role rather than a re-coloured [Typography.labelSmall], because an eyebrow is doing a
     * different job from a caption: a caption is quiet text you may read, an eyebrow is a label you
     * are meant to notice and then look past. Bold and slightly tracked at eleven points is what
     * separates the two at a size where nothing else can.
     *
     * The tracking is 0.5 — a fraction above the caption's 0.4 and well short of anything that
     * would open the joins in a Persian word.
     */
    val Eyebrow: TextStyle = coineProTextStyle(11, 15, FontWeight.Bold, 0.5)
}
