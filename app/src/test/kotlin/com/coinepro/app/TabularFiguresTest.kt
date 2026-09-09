package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.coinepro.core.designsystem.CoineProTextStyles
import com.coinepro.core.designsystem.CoineProTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A ticking price does not move its neighbours — measured, rather than recorded.
 *
 * The plan asks for a five-second 60 fps recording with no glyph shift; a recording needs a
 * device. What a recording would show is that every frame's figure is the same width whatever
 * digits it holds, and that is a measurement: the ten strings a price can tick through, laid out
 * with the real fonts under every style a figure is set in, must all measure the same width to
 * the pixel. Native graphics, so the measurement is Skia's and the fonts are the shipped ones.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
class TabularFiguresTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `every digit is the same width in every style a figure is set in`() {
        val widths = mutableMapOf<String, List<Int>>()
        rule.setContent {
            CoineProTheme {
                val measurer = rememberTextMeasurer()
                val styles: List<Pair<String, TextStyle>> = listOf(
                    "bodyMedium" to MaterialTheme.typography.bodyMedium,
                    "labelSmall" to MaterialTheme.typography.labelSmall,
                    "titleMedium" to MaterialTheme.typography.titleMedium,
                    "Numeric" to CoineProTextStyles.Numeric,
                    "RowFigure" to CoineProTextStyles.RowFigure,
                    "Balance" to CoineProTextStyles.Balance,
                )
                for ((name, style) in styles) {
                    widths[name] = (0..9).map { digit ->
                        val text = "$digit$digit,$digit$digit$digit.$digit$digit"
                        measurer.measure(text, style).size.width
                    }
                }
            }
        }
        rule.waitForIdle()
        for ((name, tick) in widths) {
            assertEquals("$name reflows as the digits tick: $tick", 1, tick.toSet().size)
        }
    }
}
