package com.coinepro.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The colour a market with no logo is recognised by** (run ΤΦΥ, F1).
 *
 * The catalogue used to hide every market it had no artwork for. It lists them now, which puts
 * hundreds of monograms on screen, and a monogram is only useful if its colour is a property of the
 * ticker rather than of the run. Three things have to hold and each has a way of quietly failing:
 *
 * * **the same ticker is always the same colour** — broken the moment somebody reaches for
 *   `String.hashCode`, whose value is a platform's business and not a contract;
 * * **two tickers are usually different colours** — broken by a hash with a short period;
 * * **no monogram is signal green or signal red** — broken by nobody, until a coin lands on the
 *   colour that means «up» beside a price that is down.
 */
class MonogramTintTest {

    private fun hueOf(colour: Color): Float {
        val srgb = colour.convert(ColorSpaces.Srgb)
        val r = srgb.red
        val g = srgb.green
        val b = srgb.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val span = max - min
        if (span < 1e-4f) return 0f
        val hue = when (max) {
            r -> 60f * (((g - b) / span) % 6f)
            g -> 60f * ((b - r) / span + 2f)
            else -> 60f * ((r - g) / span + 4f)
        }
        return (hue + 360f) % 360f
    }

    @Test
    fun `the same ticker is the same colour every time`() {
        assertEquals(
            CoineProColors.monogramHue("PEPE").value,
            CoineProColors.monogramHue("PEPE").value,
        )
        // Written out rather than taken from `hashCode`, so the answer is this file's and not the
        // JVM's. If this ever changes, a reader who learned their coin is the teal one is wrong.
        assertEquals(CoineProColors.monogramHue("PEPE"), CoineProColors.monogramHue("pepe"))
        assertEquals(CoineProColors.monogramHue("PEPE"), CoineProColors.monogramHue("PE-PE"))
    }

    @Test
    fun `neighbouring tickers do not collide`() {
        // The four that share a first letter, which is what made a one-letter monogram unreadable
        // and is exactly the case the hue has to answer.
        val tints = listOf("PEPE", "PENDLE", "PYTH", "PAXG").map(CoineProColors::monogramHue)
        assertEquals("two of the four P tickers share a colour", 4, tints.distinct().size)
    }

    @Test
    fun `the wheel spreads, rather than landing everything in one band`() {
        val hues = BUSY.map { hueOf(CoineProColors.monogramHue(it)) }
        val warm = hues.count { it < 120f }
        val cool = hues.count { it >= 120f }
        assertTrue("all ${hues.size} tickers landed on one side: warm=$warm cool=$cool", warm > 0 && cool > 0)
        assertTrue("only ${hues.distinct().size} distinct hues over ${hues.size} tickers", hues.distinct().size >= 12)
    }

    @Test
    fun `no monogram is ever signal green or signal red`() {
        // The one rule that is not about legibility. Green means «up» and red means «down» on every
        // other surface in this app, and a coin whose disc landed on either says two things at once.
        BUSY.forEach { ticker ->
            val hue = hueOf(CoineProColors.monogramHue(ticker))
            assertTrue("$ticker is at ${hue.toInt()}°, inside the signal green band", hue !in 100f..160f)
            assertTrue("$ticker is at ${hue.toInt()}°, inside the signal red band", hue < 340f && hue > 20f)
        }
    }

    @Test
    fun `an empty ticker gets a colour rather than an exception`() {
        // A market with a blank base is a bug somewhere upstream and it must not be a crash here.
        assertNotEquals(Color.Unspecified, CoineProColors.monogramHue(""))
        assertEquals(CoineProColors.monogramHue(""), CoineProColors.monogramHue("   "))
    }

    private companion object {
        /** A spread of real tickers off the bundled table, long enough to see the wheel. */
        val BUSY = listOf(
            "BTC", "ETH", "SOL", "XRP", "ADA", "DOGE", "TRX", "LINK", "AVAX", "DOT",
            "MATIC", "SHIB", "LTC", "BCH", "UNI", "ATOM", "XLM", "NEAR", "APT", "FIL",
            "HBAR", "ARB", "OP", "INJ", "SUI", "PEPE", "RNDR", "AAVE", "SAND", "GRT",
        )
    }
}
