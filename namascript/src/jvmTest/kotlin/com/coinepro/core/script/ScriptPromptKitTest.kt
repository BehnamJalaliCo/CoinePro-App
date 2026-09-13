package com.coinepro.core.script

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prompt handed to an assistant (run Σ, S3 B).
 *
 * The prompt is a promise about the language, made to a machine that will take it literally. So the
 * two things this file holds are that every name it teaches is a name the interpreter answers to,
 * and that every example it carries compiles and runs — because an example that does not run does
 * not merely fail to help, it teaches the mistake and every script that comes back has it.
 */
class ScriptPromptKitTest {

    private val series = CandleSeries(
        List(200) { index ->
            val base = 100.0 + index % 17 - (index % 5) * 2.0
            Candle(t = 1_700_000_000L + index * 3_600L, o = base, h = base + 2, l = base - 2, c = base + 1, v = 500.0 + index)
        },
    )

    @Test
    fun `every example compiles and runs`() {
        for (example in ScriptPromptKit.EXAMPLES) {
            val source = example.source.trimIndent()
            assertNull("${example.titleEn} does not compile: ${NamaScript.check(source)?.messageEn}", NamaScript.check(source))
            val result = NamaScript.run(source, series)
            assertNull("${example.titleEn} does not run: ${result.error?.messageEn}", result.error)
            assertTrue(
                "${example.titleEn} draws nothing",
                result.plots.isNotEmpty() || result.levels.isNotEmpty() || result.markers.isNotEmpty(),
            )
        }
    }

    @Test
    fun `there are three examples and the last one signals`() {
        assertEquals(3, ScriptPromptKit.EXAMPLES.size)
        assertTrue(
            "no example shows signal(...), which would leave it looking optional",
            ScriptPromptKit.EXAMPLES.any { "signal(" in it.source },
        )
    }

    @Test
    fun `every name the prompt teaches is a name the interpreter answers to`() {
        // The prompt is generated from the reference, so this holds transitively — but it is the
        // claim that matters, and it is cheap to check directly against what the prompt says rather
        // than against what it was built from.
        val prompt = ScriptPromptKit.prompt("XAUUSD", "۱ ساعته")
        val known = (ScriptReference.SERIES + ScriptReference.ALL_GROUPS.flatMap { it.functions })
            .map { ScriptReferenceEn.nameOf(it.signature) }
            .toSet() + DRAWING + ScriptReference.COLOUR_NAMES + NAMED_TO_FORBID
        val taught = Regex("""\b(ta|math|str|input|color|request|strategy)\.[a-z_]+""").findAll(prompt)
            .map { it.value }
            .toSet()
        assertEquals(emptyList<String>(), (taught - known).sorted())
    }

    @Test
    fun `the prompt names the reader's own chart`() {
        val prompt = ScriptPromptKit.prompt("XAUUSD", "۱ ساعته")
        assertTrue("the symbol is missing", "XAUUSD" in prompt)
        assertTrue("the timeframe is missing", "۱ ساعته" in prompt)
    }

    @Test
    fun `the prompt carries its own version`() {
        // A script that came back wrong has to be traceable to the prompt that asked for it.
        assertTrue(ScriptPromptKit.VERSION in ScriptPromptKit.prompt("BTCUSDT", "4h"))
        assertTrue(ScriptPromptKit.VERSION in ScriptPromptKit.prompt("BTCUSDT", "4h", english = true))
    }

    @Test
    fun `the prompt says the four things that separate this language from pine`() {
        // The differences an assistant gets wrong by default. Each of these is one of the twenty
        // repairs in ScriptPaste, which is the same list read from the other end.
        val prompt = ScriptPromptKit.prompt("BTCUSDT", "4h", english = true)
        assertTrue("no header", "//@version" in prompt && "indicator(" in prompt)
        assertTrue("the walrus", ":=" in prompt)
        assertTrue("the operators", "&&" in prompt)
        assertTrue("the history index", "close[1]" in prompt)
    }

    @Test
    fun `the prompt teaches signal because that is what makes a script a signal`() {
        for (english in listOf(false, true)) {
            assertTrue("signal(" in ScriptPromptKit.prompt("BTCUSDT", "4h", english = english))
        }
    }

    @Test
    fun `the English prompt is English`() {
        val prompt = ScriptPromptKit.prompt("BTCUSDT", "4h", english = true)
        // The examples' own titles and the reference's headings both have to have been translated.
        val persian = prompt.lines().filter { line -> line.any { it in '؀'..'ۿ' } }
            // The example sources are written with Persian titles inside them, and they stay: a
            // reader on the English app whose script says title = "میانگین" still runs.
            .filterNot { line -> ScriptPromptKit.EXAMPLES.any { example -> line.trim() in example.source.trimIndent() } }
        assertEquals(emptyList<String>(), persian)
    }

    @Test
    fun `the Persian prompt is Persian`() {
        val prompt = ScriptPromptKit.prompt("BTCUSDT", "۴ ساعته")
        assertTrue("the prompt lost its Persian framing", prompt.count { it in '؀'..'ۿ' } > 200)
    }

    @Test
    fun `the prompt is short enough to paste`() {
        // An assistant given six thousand characters of preamble answers the preamble. The whole
        // point of listing only the first few functions of each group is to stay under that.
        for (english in listOf(false, true)) {
            val length = ScriptPromptKit.prompt("BTCUSDT", "4h", english = english).length
            assertTrue("the prompt grew to $length characters", length < 6_000)
        }
    }

    @Test
    fun `what comes back from the prompt's own examples needs no repair`() {
        // The examples are what an assistant will imitate most closely, so ScriptPaste must find
        // nothing to fix in them: a prompt that teaches something the fixer then rewrites is a
        // prompt teaching the wrong language.
        for (example in ScriptPromptKit.EXAMPLES) {
            val source = example.source.trimIndent()
            assertEquals("${example.titleEn} was repaired", source, ScriptPaste.repair(source).first)
        }
    }

    // ── the Persian prompt carries an English specification (run Σ-FIX 2) ────────────────────

    @Test
    fun `the Persian prompt asks in Persian and specifies in English`() {
        // The owner's review of 4.85.0: «مدل‌ها با مشخصات انگلیسی کد دقیق‌تری می‌دهند». The reader
        // and the model are two audiences, and only one of them is Persian — so the ask is Persian
        // and the specification the model has to follow is English.
        val prompt = ScriptPromptKit.prompt("XAUUSD", "H1")
        assertTrue("the ask is not Persian", prompt.startsWith("یک اندیکاتور"))
        assertTrue("no English specification block", prompt.contains("--- NamaScript specification"))
        assertTrue("the block does not close", prompt.contains("--- end of specification ---"))
        for (line in listOf(
            "The logical operators are and, or, not",
            "The previous bar is close[1]",
            "There is no ta.rma; it is called ta.smma",
            "signal(condition, text =",
            "What draws:",
            "Three examples:",
        )) {
            assertTrue("the English spec is missing «$line»", prompt.contains(line))
        }
    }

    @Test
    fun `both prompts specify the same language, word for word`() {
        // One specification, not two: a Persian copy and an English copy would be two descriptions
        // free to disagree, and the one nobody reads is the one that drifts.
        val fa = ScriptPromptKit.prompt("XAUUSD", "H1")
        val en = ScriptPromptKit.prompt("XAUUSD", "H1", english = true)
        val spec = { text: String ->
            text.substringAfter("--- NamaScript specification").substringBefore("--- end of specification ---")
        }
        assertEquals(spec(en), spec(fa))
    }

    @Test
    fun `the reader's own chart is named to the model, in English`() {
        // A model that knows it is writing for gold on the hourly picks different lengths. Inside
        // the English block, because that is the half the model is reading.
        val prompt = ScriptPromptKit.prompt("XAUUSD", "H1")
        assertTrue(prompt.contains("The reader's chart: XAUUSD on the H1 timeframe"))
        assertTrue(
            "the chart line is outside the specification block",
            prompt.indexOf("The reader's chart") < prompt.indexOf("--- end of specification ---"),
        )
    }

    @Test
    fun `the prompt says the reader's own words may stay Persian`() {
        // Without this the English specification quietly asks for English titles, and a Persian
        // reader gets a chart legend in a language they did not choose — the exact leak run G spent
        // itself on, arriving through a prompt.
        assertTrue(
            ScriptPromptKit.prompt("XAUUSD", "H1")
                .contains("Titles and signal text may be written in Persian"),
        )
    }

    private companion object {
        /** Names the prompt teaches that are not `ta.`-style calls but plain built-ins. */
        val DRAWING = setOf("plot", "hline", "marker", "fill", "input", "signal", "color.new")

        /**
         * Names the prompt mentions in order to say they do *not* exist.
         *
         * `ta.rma` is the single commonest thing an assistant reaches for, and naming it is worth
         * more than leaving it out — so the prompt says «there is no ta.rma; it is ta.smma».
         */
        val NAMED_TO_FORBID = setOf("ta.rma")
    }
}
