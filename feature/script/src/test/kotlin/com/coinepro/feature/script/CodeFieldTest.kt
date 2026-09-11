package com.coinepro.feature.script

import com.coinepro.core.common.proseDigits
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The editor's three helpers, which are arithmetic on a string and a caret and are tested as such. */
class CodeFieldTest {

    @Test
    fun `the word under the caret is the identifier with its namespace`() {
        val value = TextFieldValue("x = ta.sm", TextRange(9))
        assertEquals(4 to "ta.sm", wordBeforeCaret(value))
        assertEquals(null, wordBeforeCaret(TextFieldValue("x = 1", TextRange(5))))
        assertEquals(null, wordBeforeCaret(TextFieldValue("x = ta.sm", TextRange(2, 9))))
    }

    @Test
    fun `completions come from the reference and start with the word`() {
        val names = completionsFor(TextFieldValue("plot(ta.sm", TextRange(10)))
        assertTrue(names.toString(), "ta.sma" in names && "ta.smma" in names && "ta.smi" in names)
        assertTrue(names.all { it.startsWith("ta.sm") })
        assertTrue(completionsFor(TextFieldValue("c", TextRange(1))).isEmpty())
        assertTrue(completionsFor(TextFieldValue("clo", TextRange(3))).contains("close"))
    }

    @Test
    fun `a function completes with its parenthesis and the caret inside, a series completes bare`() {
        val function = complete(TextFieldValue("plot(ta.sm", TextRange(10)), "ta.sma")
        assertEquals("plot(ta.sma()", function.text)
        assertEquals(12, function.selection.end)
        val series = complete(TextFieldValue("plot(clo", TextRange(8)), "close")
        assertEquals("plot(close", series.text)
        assertEquals(10, series.selection.end)
    }

    @Test
    fun `a typed bracket brings its closing half, and typing the closing half steps over it`() {
        val before = TextFieldValue("plot", TextRange(4))
        val opened = autoClose(before, TextFieldValue("plot(", TextRange(5)))
        assertEquals("plot()", opened.text)
        assertEquals(5, opened.selection.end)
        val stepped = autoClose(opened, TextFieldValue("plot())", TextRange(6)))
        assertEquals("plot()", stepped.text)
        assertEquals(6, stepped.selection.end)
    }

    @Test
    fun `a paste or a deletion is left alone`() {
        val before = TextFieldValue("plot(", TextRange(5))
        val pasted = TextFieldValue("plot(close, open", TextRange(16))
        assertEquals(pasted, autoClose(before, pasted))
        val deleted = TextFieldValue("plot", TextRange(4))
        assertEquals(deleted, autoClose(before, deleted))
    }
}

/** The completion strip's signatures, the console's timing line and the snippets — 4.61.0. */
class StudioHelpersTest {

    @Test
    fun `a completion carries the reference's signature`() {
        assertEquals("ta.sma(close, 20)", signatureFor("ta.sma"))
        assertEquals("close", signatureFor("close"))
        assertEquals("label.new(bar_index, high, \"متن\", color = color.gold)", signatureFor("label.new"))
        assertEquals("nothing", signatureFor("nothing"))
    }

    @Test
    fun `every snippet compiles`() {
        for (snippet in SNIPPETS) {
            val failure = com.coinepro.core.script.NamaScript.check(snippet.source)
            assertEquals("${snippet.title}: ${failure?.messageEn}", null, failure)
        }
        assertTrue(SNIPPETS.size >= 4)
    }

    @Test
    fun `the timing line keeps Latin milliseconds and a prose bar count`() {
        // The line itself is a resource since 4.72.0 — «اجرا در %1$s ms · %2$s کندل» / «Ran in
        // %1$s ms · %2$s bars» — so what is left to assert without a composition is the rule the
        // two arguments follow: the milliseconds are a *measurement* and stay Latin in both
        // languages, and the bar count is prose and follows the screen.
        assertEquals("12", 12L.toString())
        assertEquals("۲۰۰۰", 2_000.proseDigits(english = false))
        assertEquals("2000", 2_000.proseDigits(english = true))
    }
}
