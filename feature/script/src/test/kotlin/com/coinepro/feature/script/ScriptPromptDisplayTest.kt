package com.coinepro.feature.script

import com.coinepro.core.common.BidiText
import com.coinepro.core.script.ScriptPromptKit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The prompt is isolated for the screen and raw for the clipboard (run Σ, S3 B).
 *
 * Two claims, and the second is the one with teeth. An isolate is an invisible control character:
 * a prompt that carried them into an assistant would be asking it to read `ta.ema(close, 20)` with
 * control codes inside it, and the reader would get back a script that does not compile for a
 * reason nothing on the screen could explain. So the transform has to be **exactly** reversible,
 * and here is the proof for every line of both languages' prompts.
 */
class ScriptPromptDisplayTest {

    private val persian = ScriptPromptKit.prompt(symbol = "XAUUSD", timeframe = "۱ ساعته")
    private val english = ScriptPromptKit.prompt(symbol = "XAUUSD", timeframe = "1 hour", english = true)

    private fun stripped(value: String): String = value.filterNot { it == BidiText.LRI || it == BidiText.PDI }

    @Test
    fun `stripping the isolates gives the prompt back exactly`() {
        for (prompt in listOf(persian, english)) {
            assertEquals(prompt, stripped(isolatedForDisplay(prompt)))
        }
    }

    @Test
    fun `the prompt itself carries no isolate`() {
        // If it ever did, the clipboard would be handing them out and this whole transform would be
        // papering over a bug one layer down.
        for (prompt in listOf(persian, english)) {
            assertTrue("the prompt already contains bidi controls", prompt.none { it == BidiText.LRI || it == BidiText.PDI })
        }
    }

    @Test
    fun `the code a persian line carries is isolated`() {
        val display = isolatedForDisplay(persian)
        for (token in listOf("//@version", "indicator(...)", "close[1]", "ta.smma")) {
            assertTrue(
                "$token was left to the paragraph's direction",
                "${BidiText.LRI}$token" in display || display.contains(Regex("${BidiText.LRI}[^${BidiText.PDI}]*${Regex.escape(token)}")),
            )
        }
    }

    @Test
    fun `a persian word between two latin ones breaks the run`() {
        // What makes it safe to apply to the whole prompt rather than to a hand-picked list: the
        // isolate never swallows Persian, so no Persian sentence is ever laid out left to right.
        val display = isolatedForDisplay("- عملگرهای منطقی and و or و not هستند.")
        assertEquals("${BidiText.LRI}and${BidiText.PDI}", display.substringAfter("منطقی ").substringBefore(" و"))
        assertTrue("Persian was swallowed into an isolate", "${BidiText.LRI}منطقی" !in display)
    }

    @Test
    fun `a comma-separated list is one run, so it reads in the order it was written`() {
        // The bug the first version of this had: isolating each name on its own left the *list*
        // laid out right to left, and a reader saw the series in reverse.
        val display = isolatedForDisplay("- سری‌ها: close, open, high")
        assertEquals("${BidiText.LRI}close, open, high${BidiText.PDI}", display.substringAfter("سری‌ها: "))
    }

    @Test
    fun `text with no code in it is untouched`() {
        val plain = "این را کپی کنید و به هر دستیاری بدهید."
        assertEquals(plain, isolatedForDisplay(plain))
        assertEquals("", isolatedForDisplay(""))
    }

    @Test
    fun `punctuation on its own is not treated as code`() {
        // A full stop or a bracket belongs to the Persian sentence around it, and isolating it
        // would pin it to the wrong end of the line — which is the fault being fixed, inverted.
        assertEquals("تمام. (بله)", isolatedForDisplay("تمام. (بله)"))
    }
}
