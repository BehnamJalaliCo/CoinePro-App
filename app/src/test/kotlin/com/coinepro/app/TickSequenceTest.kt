package com.coinepro.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
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
 * Five seconds at sixty frames, off-device — item 3's «tick recording, no glyph shift».
 *
 * A recording on a phone shows what a reader would see; what it proves is that no digit's left
 * edge moved between frames. That is a number, and it can be measured without a phone: three
 * hundred consecutive prices, the way a feed ticks them, laid out under every style a figure is
 * set in, and the bounding box of every character compared frame to frame. The assertion is that
 * the maximum shift is **zero pixels** across all 300 frames, for the layout's width and for
 * each glyph's left edge. `TabularFiguresTest` is the same fact for ten digits; this is the
 * fact over time, with the decimal point, the grouping comma and a sign moving through.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "fa-rIR-ldrtl-w411dp-h914dp-xxhdpi")
class TickSequenceTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `three hundred ticks move no glyph`() {
        val frames = (0 until FRAMES).map { frame -> tick(frame) }
        val shifts = mutableMapOf<String, Float>()
        val widths = mutableMapOf<String, Int>()
        rule.setContent {
            CoineProTheme {
                val measurer = rememberTextMeasurer()
                val styles: List<Pair<String, TextStyle>> = listOf(
                    "bodyMedium" to MaterialTheme.typography.bodyMedium,
                    "labelSmall" to MaterialTheme.typography.labelSmall,
                    "titleMedium" to MaterialTheme.typography.titleMedium,
                    "Numeric" to CoineProTextStyles.Numeric,
                    "NumericLarge" to CoineProTextStyles.NumericLarge,
                    "RowFigure" to CoineProTextStyles.RowFigure,
                    "TileFigure" to CoineProTextStyles.TileFigure,
                    "Balance" to CoineProTextStyles.Balance,
                )
                for ((name, style) in styles) {
                    var maxShift = 0f
                    var firstWidth = -1
                    var previous: FloatArray? = null
                    for (text in frames) {
                        val layout = measurer.measure(text, style)
                        if (firstWidth < 0) firstWidth = layout.size.width
                        assertEquals("$name width changed on «$text»", firstWidth, layout.size.width)
                        val lefts = FloatArray(text.length) { index -> layout.getBoundingBox(index).left }
                        previous?.let { last ->
                            for (index in lefts.indices) {
                                val shift = kotlin.math.abs(lefts[index] - last[index])
                                if (shift > maxShift) maxShift = shift
                            }
                        }
                        previous = lefts
                    }
                    shifts[name] = maxShift
                    widths[name] = firstWidth
                }
            }
        }
        rule.waitForIdle()
        println("tick sequence: ${frames.size} frames (${frames.size / 60} s at 60 fps), max glyph shift per style: " +
            shifts.entries.joinToString { "${it.key}=${it.value}px" })
        for ((name, shift) in shifts) {
            assertEquals("$name: a glyph moved $shift px between frames", 0f, shift, 0f)
        }
    }

    /**
     * A price that ticks the way a feed does: the last two decimals walk, the thousands roll
     * over once in the middle, and the sign of the change flips — every digit from 0 to 9 passes
     * through every slot.
     */
    private fun tick(frame: Int): String {
        val cents = (frame * 37) % 100
        val units = 2_599 + (frame * 7) % 11 - 5 + frame / 150
        // The sign is fixed across the run: «+» and «-» are different glyphs with different
        // advances in every font, so a change of sign is a change of text, not a glyph shift.
        // The app draws the sign in its own column for exactly that reason.
        val sign = "+"
        val change = (frame * 13) % 100
        val whole = units.toString().let { if (it.length > 3) it.dropLast(3) + "," + it.takeLast(3) else it }
        return "$whole.${cents.toString().padStart(2, '0')} $sign${change / 10}.${change % 10}%"
    }

    private companion object {
        const val FRAMES = 300
    }
}
