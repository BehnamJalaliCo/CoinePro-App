package com.coinepro.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The properties that make a flat interface read as built rather than printed.
 *
 * None of these pin a value. Every one of them pins a *relationship* that was broken in a way no
 * amount of looking at the dark theme would have caught, because each failure only shows up in one
 * theme and each looked plausible in the file:
 *
 *  * a surface one rung along the ladder from its own container, in a theme where the ladder runs
 *    the other way, so the selected segment was a dent;
 *  * a hairline at an alpha below what a panel resolves, so cards had no edge;
 *  * a *fill* taking the ink variant of the accent, so a selected chip's label sat at 2.6:1.
 *
 * The right guard against all three is the same: state the relationship the design depends on and
 * let the build hold it, in both themes at once.
 */
class SurfaceLadderTest {

    private val palettes = listOf(CoineProDarkPalette, CoineProLightPalette)

    /** WCAG relative contrast, for two opaque colours. */
    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return (maxOf(la, lb) / minOf(la, lb)).toDouble()
    }

    @Test
    fun `a raised surface is never the same value as the container it is raised out of`() {
        // The bug this replaces: `surfaceElevated` is *darker* than `surface` in the light theme,
        // so a selected segment drawn with it was pressed into its tray rather than lifted out.
        palettes.forEach { palette ->
            val delta = abs(palette.surfaceRaised.luminance() - palette.surface.luminance())
            assertTrue(
                "raised is indistinguishable from its container (isDark=${palette.isDark})",
                delta > 0.01f,
            )
        }
    }

    @Test
    fun `raised means lighter, in both themes`() {
        // The whole reason it is its own token rather than a rung: "further along the ladder" runs
        // in opposite directions in the two themes, and "lifted" does not.
        palettes.forEach { palette ->
            assertTrue(
                "raised is not lighter than its container (isDark=${palette.isDark})",
                palette.surfaceRaised.luminance() > palette.surface.luminance(),
            )
        }
    }

    @Test
    fun `every rung of the ladder is separated from its neighbour`() {
        palettes.forEach { palette ->
            val ladder = listOf(
                palette.stage,
                palette.surface,
                palette.surfaceElevated,
                palette.surfaceOverlay,
            )
            ladder.zipWithNext { lower, upper ->
                assertTrue(
                    "two adjacent surfaces are the same value (isDark=${palette.isDark})",
                    abs(lower.luminance() - upper.luminance()) > 0.002f,
                )
            }
        }
    }

    @Test
    fun `the chart ground is not the card`() {
        // Documented intent that the light theme quietly broke by giving both `#F7F8FA`: a chart
        // is meant to recede further than the page around it, and it cannot recede to exactly the
        // surface a card is drawn on.
        palettes.forEach { palette ->
            assertTrue(
                "the terminal ground and the card surface are the same (isDark=${palette.isDark})",
                palette.terminal != palette.surface,
            )
        }
    }

    @Test
    fun `a hairline is opaque enough to be drawn`() {
        // Below about 6% the edge of a card is a line that exists in the file and not on the
        // panel, which is exactly how a card ends up looking like a printed region.
        palettes.forEach { palette ->
            assertTrue(
                "the subtle border is too faint to read (isDark=${palette.isDark})",
                palette.borderSubtle.alpha >= 0.06f,
            )
        }
    }

    @Test
    fun `the border weights are ordered`() {
        palettes.forEach { palette ->
            assertTrue(
                "border weights are not ordered (isDark=${palette.isDark})",
                palette.borderSubtle.alpha < palette.border.alpha &&
                    palette.border.alpha < palette.borderStrong.alpha,
            )
        }
    }

    @Test
    fun `the accent used as a fill can carry its own label`() {
        // What a selected chip, a primary button and a filled pill all depend on.
        palettes.forEach { palette ->
            assertTrue(
                "onAccent is unreadable on accentFill (isDark=${palette.isDark})",
                contrast(palette.accentFill, palette.onAccent) >= 4.5,
            )
        }
    }

    @Test
    fun `the accent used as ink is readable on the page it is written on`() {
        palettes.forEach { palette ->
            listOf(palette.stage, palette.surface, palette.surfaceElevated).forEach { ground ->
                assertTrue(
                    "the ink accent is unreadable on a surface (isDark=${palette.isDark})",
                    contrast(palette.accent, ground) >= 4.5,
                )
            }
        }
    }

    @Test
    fun `the text ramp is readable on every ground it is written on`() {
        // `textMuted` is the caption under every card in the app, and it read 4.06:1 in the light
        // theme. `textDisabled` read 2.24:1 — and it is not reserved for disabled: the column
        // headings on four screens, a signal's setup name and the chevron on an *interactive* row
        // all take it, so it carries real content and has to clear the large-text bar at least.
        palettes.forEach { palette ->
            listOf(palette.stage, palette.surface, palette.surfaceElevated).forEach { ground ->
                assertTrue(
                    "textPrimary is unreadable on a surface (isDark=${palette.isDark})",
                    contrast(palette.textPrimary, ground) >= 7.0,
                )
                assertTrue(
                    "textSecondary is unreadable on a surface (isDark=${palette.isDark})",
                    contrast(palette.textSecondary, ground) >= 4.5,
                )
                assertTrue(
                    "textMuted is unreadable on a surface (isDark=${palette.isDark})",
                    contrast(palette.textMuted, ground) >= 4.5,
                )
                assertTrue(
                    "textDisabled is unreadable on a surface (isDark=${palette.isDark})",
                    contrast(palette.textDisabled, ground) >= 3.0,
                )
            }
        }
    }

    @Test
    fun `the text ramp stays a ramp`() {
        // Four steps that have to keep their order, or "muted" stops meaning quieter than
        // "secondary" and the hierarchy the whole app is laid out on inverts.
        palettes.forEach { palette ->
            val ramp = listOf(
                palette.textPrimary,
                palette.textSecondary,
                palette.textMuted,
                palette.textDisabled,
            ).map { contrast(it, palette.surface) }
            ramp.zipWithNext { louder, quieter ->
                assertTrue(
                    "the text ramp is not monotonic (isDark=${palette.isDark})",
                    louder > quieter,
                )
            }
        }
    }

    @Test
    fun `the semantic inks carry a figure at the size they are actually set`() {
        // A percent pill sets 13sp, which is not large text, so 4.5 is the bar and the light
        // theme's green measured 4.12. Green, red and the warning amber are all read as ink on a
        // card — never as a fill with their own label — so this is the only bar that applies.
        palettes.forEach { palette ->
            listOf(palette.stage, palette.surface, palette.surfaceElevated).forEach { ground ->
                listOf("buy" to palette.buy, "sell" to palette.sell, "warning" to palette.warning)
                    .forEach { (name, ink) ->
                        assertTrue(
                            "$name is unreadable on a surface (isDark=${palette.isDark})",
                            contrast(ink, ground) >= 4.5,
                        )
                    }
            }
        }
    }

    @Test
    fun `the ink accent is not usable as a fill, which is why the two are separate fields`() {
        // The failure the chip shipped with: `Accent` as the fill and `OnAccent` as the label. In
        // the light theme that is near-black on dark brown. This test exists so that swapping them
        // back fails the build rather than a reader's eyes.
        val light = CoineProLightPalette
        assertTrue(
            "the ink accent has become usable as a fill — the fill/ink split may be collapsing",
            contrast(light.accent, light.onAccent) < 4.5,
        )
    }
}
