package com.coinepro.core.designsystem

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hierarchy the scale claims to have.
 *
 * Every style in [CoineProTextStyles] carries a KDoc saying what job it does relative to its
 * neighbours — "one step above the row's own title so the number leads" — and [CoineProTextStyles]
 * shipped with that sentence written above a size that was exactly equal to the row's own title.
 * The prose was right and the number was wrong for as long as nothing checked.
 *
 * So these pin the *ordering*, never a size. A future pass is free to move the whole scale; it is
 * not free to let a figure sink back to the weight of its own label, which is the specific way this
 * app came to look like one voice reading everything at the same volume.
 */
class TypeScaleTest {

    @Test
    fun `a row's figure leads its own title`() {
        assertTrue(
            "the price is no larger than the name of the thing it prices",
            CoineProTextStyles.RowFigure.fontSize.value >
                CoineProTypography.titleSmall.fontSize.value,
        )
    }

    @Test
    fun `a tile's answer is unmistakably larger than its caption`() {
        // Not "larger" — a point of difference is a rounding error to the eye. A tile is read at a
        // glance and the ratio is what makes that possible.
        assertTrue(
            "a tile's figure does not lead its label by enough to be found without reading",
            CoineProTextStyles.TileFigure.fontSize.value >=
                CoineProTypography.labelSmall.fontSize.value * 1.5f,
        )
    }

    @Test
    fun `the hero is the largest thing the system offers a screen`() {
        listOf(
            CoineProTextStyles.TileFigure,
            CoineProTextStyles.RowFigure,
            CoineProTextStyles.Eyebrow,
        ).forEach { lesser ->
            assertTrue(
                "something outgrew the balance",
                CoineProTextStyles.Balance.fontSize.value > lesser.fontSize.value,
            )
        }
    }

    @Test
    fun `an eyebrow is a label rather than shrunken body text`() {
        // Its whole job is to be noticed and then looked past, and at eleven points the only two
        // things that can carry that are weight and tracking.
        assertTrue(
            "the eyebrow is not bold",
            CoineProTextStyles.Eyebrow.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold,
        )
        assertTrue(
            "the eyebrow has no tracking to separate it from body copy",
            CoineProTextStyles.Eyebrow.letterSpacing.value > 0f,
        )
    }

    @Test
    fun `tracking never opens the joins in a Persian word`() {
        // Persian is a joined script and positive tracking is tuned for Latin. A little is what
        // makes a small label read as a label; much is what makes «سیگنال» read as six letters.
        listOf(
            CoineProTextStyles.Eyebrow,
            CoineProTypography.labelSmall,
            CoineProTypography.bodyMedium,
            CoineProTypography.titleMedium,
        ).forEach { style ->
            assertTrue(
                "a reading style carries Latin tracking",
                style.letterSpacing.value <= 0.6f,
            )
        }
    }

    @Test
    fun `dense roles stay dense`() {
        // The owner's «همه چیز گنده و بزرگه» verdict came from reading ratios applied to rows. The
        // guard is on leading, not size: a row that gains a size gains it once, and a row that
        // gains a paragraph's leading gains it on every line forever.
        listOf(
            CoineProTypography.labelSmall,
            CoineProTypography.labelMedium,
            CoineProTypography.titleSmall,
            CoineProTextStyles.RowFigure,
            CoineProTextStyles.TileFigure,
        ).forEach { style ->
            val ratio = style.lineHeight.value / style.fontSize.value
            assertTrue("a dense role is carrying reading leading", ratio <= 1.45f)
        }
    }
}
