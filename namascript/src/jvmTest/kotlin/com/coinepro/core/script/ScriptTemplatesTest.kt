package com.coinepro.core.script

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * The twelve templates, every one of them run (run Σ, S3 A).
 *
 * A template is offered to a reader who could not write the script themselves, which is exactly
 * the reader who cannot tell a broken one from a working one. So there is no reading-by-eye here:
 * each template is compiled, run over a series with shapes in it, and required to have drawn
 * something and to have produced at least one `signal(...)` — the last of which is what separates
 * an indicator in this app from a picture.
 *
 * The template sources are also the only place in the app where NamaScript is written as *content*
 * rather than as a test fixture, so a change to the language that breaks them breaks here first.
 */
class ScriptTemplatesTest {

    /**
     * A series with trends, pullbacks, ranges and a few shocks.
     *
     * The same construction `ScriptStrategiesTest` uses and for the same reason: a smooth wave lets
     * a broken study hide, and a study that is never shown a bar several times the size of its
     * neighbours is a study nobody has tested.
     */
    private fun waves(count: Int = 300): CandleSeries {
        var seed = 20260913L
        fun noise(): Double {
            seed = seed * 6364136223846793005L + 1442695040888963407L
            return ((seed ushr 11).toDouble() / (1L shl 53).toDouble()) - 0.5
        }
        var shock = 0.0
        var previous = 100.0
        return CandleSeries(
            List(count) { index ->
                if (index % 83 == 11) shock += if ((index / 83) % 2 == 0) 40.0 else -40.0
                val base = 100.0 + sin(index / 7.0) * 20 + sin(index / 23.0) * 40 +
                    sin(index / 71.0) * 34 + index * 0.02 + noise() * 5 + shock
                val open = previous
                previous = base
                val wick = (0.4 + abs(noise())) * 3
                Candle(
                    t = 1_700_000_000L + index * 3_600L,
                    o = open,
                    h = maxOf(open, base) + wick,
                    l = minOf(open, base) - wick,
                    c = base,
                    v = 900.0 + (index % 17) * 40,
                )
            },
        )
    }

    private val series = waves()

    // ── every template runs ──────────────────────────────────────────────────────────────────

    @Test
    fun `every template compiles`() {
        val broken = ScriptTemplates.ALL.mapNotNull { template ->
            NamaScript.check(template.source())?.let { "${template.id}: ${it.messageEn} (line ${it.line}, ${it.code})" }
        }
        assertEquals(emptyList<String>(), broken)
    }

    @Test
    fun `every template runs over a real series`() {
        val broken = ScriptTemplates.ALL.mapNotNull { template ->
            val result = NamaScript.run(template.source(), series)
            result.error?.let { "${template.id}: ${it.messageEn} (line ${it.line}, ${it.code})" }
        }
        assertEquals(emptyList<String>(), broken)
    }

    @Test
    fun `every template draws something`() {
        val silent = ScriptTemplates.ALL.filter { template ->
            val result = NamaScript.run(template.source(), series)
            result.plots.isEmpty() && result.markers.isEmpty() && result.levels.isEmpty()
        }.map { it.id }
        assertEquals("a template that draws nothing is invisible on the chart", emptyList<String>(), silent)
    }

    @Test
    fun `every template calls signal`() {
        // The whole reason a template exists rather than a paragraph of help: a script with a
        // signal gets a state, a base rate and an Explain sheet the moment it lands on the chart.
        val unsignalled = ScriptTemplates.ALL.filterNot { "signal(" in it.source() }.map { it.id }
        assertEquals(emptyList<String>(), unsignalled)
    }

    @Test
    fun `every template survives a series that never moved`() {
        // A brand-new symbol, a halted market, a feed that sent one price all session. None of
        // these is exotic and none of them may be a crash in front of a reader.
        val flat = CandleSeries(
            List(120) { index ->
                Candle(t = 1_700_000_000L + index * 3_600L, o = 100.0, h = 100.0, l = 100.0, c = 100.0, v = 0.0)
            },
        )
        val broken = ScriptTemplates.ALL.mapNotNull { template ->
            NamaScript.run(template.source(), flat).error?.let { "${template.id}: ${it.messageEn}" }
        }
        assertEquals(emptyList<String>(), broken)
    }

    @Test
    fun `a series shorter than the warm-up is a sentence, not a crash`() {
        // A three-bar chart cannot carry a twenty-period average and the engine says so. What is
        // being held here is that it *says* so — a diagnostic with a code the studio can render —
        // rather than throwing, and that nothing comes back half-drawn.
        val short = CandleSeries(series.bars.take(3))
        for (template in ScriptTemplates.ALL) {
            val result = NamaScript.run(template.source(), short)
            val error = result.error
            if (error == null) continue
            assertTrue("${template.id} failed without a code: ${error.messageEn}", error.code.isNotEmpty())
            assertTrue("${template.id} drew something and then failed", result.plots.isEmpty())
        }
    }

    // ── the table itself ─────────────────────────────────────────────────────────────────────

    @Test
    fun `there are twelve of them and every id is its own`() {
        assertEquals(12, ScriptTemplates.ALL.size)
        assertEquals(ScriptTemplates.ALL.size, ScriptTemplates.ALL.map { it.id }.toSet().size)
    }

    @Test
    fun `every template has both languages and neither leaks`() {
        for (template in ScriptTemplates.ALL) {
            assertTrue("${template.id} has no Persian title", template.title.any { it in '؀'..'ۿ' })
            assertTrue(
                "${template.id}'s English title is Persian: ${template.titleEn}",
                template.titleEn.none { it in '؀'..'ۿ' },
            )
            assertTrue(
                "${template.id}'s English summary is Persian: ${template.summaryEn}",
                template.summaryEn.none { it in '؀'..'ۿ' },
            )
            assertTrue("${template.id}'s summary is empty", template.summary.isNotBlank())
        }
    }

    @Test
    fun `no hole is left unfilled`() {
        // The placeholders are %1, %2… and a template whose defaults list is shorter than its holes
        // would ship a script with a literal %3 in it.
        for (template in ScriptTemplates.ALL) {
            assertTrue("${template.id} left a placeholder: ${template.source()}", "%" !in template.source())
        }
    }

    // ── the matcher ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a sentence about crossing averages finds the crossing template`() {
        val matches = ScriptTemplates.matching("وقتی میانگین ۲۰ از میانگین ۵۰ رد شد بخر")
        assertEquals("ma-cross", matches.first().id)
    }

    @Test
    fun `an English sentence finds it too`() {
        assertEquals("ma-cross", ScriptTemplates.matching("buy when the 20 ema crosses the 50 ema").first().id)
        assertEquals("rsi-zones", ScriptTemplates.matching("rsi oversold bounce").first().id)
        assertEquals("volume-spike", ScriptTemplates.matching("a volume spike").first().id)
    }

    @Test
    fun `a sentence the matcher does not understand still gets three answers`() {
        // An empty picker tells a reader to go away. Three shapes tells them what the app can do.
        val matches = ScriptTemplates.matching("سلام")
        assertEquals(3, matches.size)
    }

    @Test
    fun `the numbers in the sentence reach the script`() {
        val text = "وقتی میانگین ۹ از ۲۱ رد شد"
        val numbers = ScriptTemplates.numbersIn(text)
        assertEquals(listOf(9, 21), numbers)
        val source = ScriptTemplates.ALL.first { it.id == "ma-cross" }.source(numbers)
        assertTrue("the reader's 9 did not reach the script:\n$source", "input(9," in source)
        assertTrue("the reader's 21 did not reach the script:\n$source", "input(21," in source)
        assertNull("the reader's own numbers broke the script", NamaScript.check(source))
    }

    @Test
    fun `latin numbers are read as well as persian ones`() {
        assertEquals(listOf(20, 50), ScriptTemplates.numbersIn("ema 20 crossing ema 50"))
    }

    @Test
    fun `a sentence naming one number keeps the default for the other`() {
        val source = ScriptTemplates.ALL.first { it.id == "ma-cross" }.source(listOf(9))
        assertTrue("the named number was dropped", "input(9," in source)
        assertTrue("the second hole lost its default", "input(50," in source)
        assertNull(NamaScript.check(source))
    }

    @Test
    fun `a nonsense number does not produce a script that refuses to run`() {
        // Somebody writes «میانگین ۰» or «rsi 1». The input's own min clamps it; what must not
        // happen is a template that compiles for its defaults and fails for the reader's numbers.
        for (template in ScriptTemplates.ALL) {
            for (numbers in listOf(listOf(0, 0, 0), listOf(1, 1, 1), listOf(9999, 9999, 9999))) {
                val source = template.source(numbers)
                val result = NamaScript.run(source, series)
                assertNull("${template.id} with $numbers: ${result.error?.messageEn}", result.error)
            }
        }
    }

    @Test
    fun `the matcher never returns more than it was asked for`() {
        assertTrue(ScriptTemplates.matching("cross میانگین rsi حجم شکست", limit = 2).size <= 2)
    }

    @Test
    fun `a template reached through a paste is the same script`() {
        // The paste box hands prose to the same matcher; this is the seam between the two files.
        val paste = ScriptPaste.read("میانگین ۲۰ از میانگین ۵۰ رد شد")
        assertEquals(ScriptPaste.Dialect.PROSE, paste.dialect)
        assertTrue("the paste offered no template", paste.templates.isNotEmpty())
        assertEquals("ma-cross", paste.templates.first().id)
        assertNotNull(paste.templates.first().source(ScriptTemplates.numbersIn("میانگین ۲۰ از میانگین ۵۰ رد شد")))
    }
}
