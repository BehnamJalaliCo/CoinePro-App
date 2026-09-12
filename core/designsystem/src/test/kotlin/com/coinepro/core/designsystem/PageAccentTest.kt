package com.coinepro.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * **One accent** (run Ω2), held by a test rather than by a review.
 *
 * The rule: green and red belong to the market, and exactly one warm gold belongs to action. A blue
 * «add indicator» chip beside a green candle and a red candle gives a reader four colours to scan
 * when the screen only has two facts and one thing to press. This is the test that notices when a
 * domain colour creeps back onto a control, which is how it left in the first place — `ANALYSIS`
 * resolved to blue for twenty-five versions and every screen it appeared on looked deliberate.
 *
 * [PageAccent.DESTRUCTIVE] is excluded and that is the point of stating it here: it is not a domain,
 * it means «this cannot be undone», and it is the one press in the app that must not wear the colour
 * that means «press me».
 */
class PageAccentTest {

    private val palettes = listOf(CoineProDarkPalette, CoineProLightPalette)

    /** Everything that reads a domain rather than a consequence. */
    private val domains = PageAccent.entries.filter { it != PageAccent.DESTRUCTIVE }

    @Test
    fun `every domain resolves to the same gold, as a fill and as ink`() {
        palettes.forEach { palette ->
            listOf(true, false).forEach { fill ->
                val resolved = domains.map { palette.accentFor(it, fill) }.distinct()
                assertEquals("more than one accent paints a control: $resolved", 1, resolved.size)
                assertEquals(palette.accentFor(PageAccent.BRAND, fill), resolved.single())
            }
        }
    }

    @Test
    fun `every domain takes the same ink on its fill`() {
        palettes.forEach { palette ->
            assertEquals(1, domains.map { palette.inkOn(it) }.distinct().size)
            assertEquals(palette.onAccent, palette.inkOn(PageAccent.ANALYSIS))
        }
    }

    @Test
    fun `no control wears a direction colour`() {
        // The market's two colours mean «up» and «down» on every surface in this app. A button in
        // either of them is a button claiming a direction.
        palettes.forEach { palette ->
            domains.forEach { accent ->
                listOf(true, false).forEach { fill ->
                    val colour = palette.accentFor(accent, fill)
                    assertNotEquals("$accent paints a control in the rise colour", palette.buy, colour)
                    assertNotEquals("$accent paints a control in the fall colour", palette.sell, colour)
                }
            }
        }
    }

    @Test
    fun `the fill and the ink are allowed to differ, and only for gold`() {
        // Gold is a mid-tone: the fill is the brand colour and the ink is darkened so it can be read
        // on white. That split is the reason this is a function of two arguments rather than a value,
        // so it is worth asserting it is still doing something in the light theme.
        assertNotEquals(
            CoineProLightPalette.accentFor(PageAccent.BRAND, fill = true),
            CoineProLightPalette.accentFor(PageAccent.BRAND, fill = false),
        )
    }

    @Test
    fun `destruction keeps its own colour and its own ink`() {
        palettes.forEach { palette ->
            assertEquals(palette.sell, palette.accentFor(PageAccent.DESTRUCTIVE, fill = true))
            assertNotEquals(palette.onAccent, palette.inkOn(PageAccent.DESTRUCTIVE))
        }
    }
}
