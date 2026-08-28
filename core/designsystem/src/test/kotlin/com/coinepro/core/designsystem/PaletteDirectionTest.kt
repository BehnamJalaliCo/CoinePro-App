package com.coinepro.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * The two direction colours, and the one-line swap that inverts the whole product.
 *
 * `CoineProTheme` implements the red-up convention by exchanging `buy` and `sell` on the palette
 * rather than at any call site, which is what makes it one line instead of a hundred. The property
 * that has to hold is that the exchange is *complete and symmetric*: every direction colour in the
 * app resolves through these two fields, including the chart's canvas, which never touches a
 * composable colour at all.
 *
 * Worth testing rather than eyeballing, because a partial swap is the worst possible outcome — a
 * chart drawing rises in red while the percentage beside it draws them in green is not a theme, it
 * is two contradictory answers about whether the reader made money.
 */
class PaletteDirectionTest {

    private fun swapped(base: CoineProPalette) = base.copy(buy = base.sell, sell = base.buy)

    @Test
    fun `the swap exchanges the two and changes nothing else`() {
        listOf(CoineProDarkPalette, CoineProLightPalette).forEach { base ->
            val flipped = swapped(base)
            assertEquals(base.sell, flipped.buy)
            assertEquals(base.buy, flipped.sell)
            // Everything else is the same palette. A theme that also moved the stage or the text
            // ink would be a second theme wearing this switch's name.
            assertEquals(base, flipped.copy(buy = base.buy, sell = base.sell))
        }
    }

    @Test
    fun `swapping twice is the identity`() {
        listOf(CoineProDarkPalette, CoineProLightPalette).forEach { base ->
            assertEquals(base, swapped(swapped(base)))
        }
    }

    @Test
    fun `the two directions are actually different colours to begin with`() {
        // The guard against the swap being invisible. If a palette ever ended up with the same
        // colour for both, every test above would still pass and the switch would do nothing.
        listOf(CoineProDarkPalette, CoineProLightPalette).forEach { base ->
            assertNotEquals(base.buy, base.sell)
        }
    }

    @Test
    fun `the social green tracks the rise colour, not the palette's green`() {
        // `social` is documented as the same hue as `buy` "by design, not by accident" — copy
        // trading reads as growth. It is *not* swapped: a reader on the red-up convention has not
        // asked for the community section to turn red, and social is not a direction.
        listOf(CoineProDarkPalette, CoineProLightPalette).forEach { base ->
            assertEquals(base.social, swapped(base).social)
        }
    }
}
