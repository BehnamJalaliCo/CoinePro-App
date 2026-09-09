package com.coinepro.core.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The diagnostic codes are a contract with the reader and with `docs/namascript/SPEC.md` §7.
 * This test holds the three lists together: what the code can raise, what the table documents,
 * and what the spec prints.
 */
class ScriptDiagnosticsTest {

    @Test
    fun `every code the sources raise has a hint in both languages`() {
        val raised = Regex("""code = "(E\d{3})"""").findAll(sources()).map { it.groupValues[1] }.toSet() +
            Regex("""ScriptFailure\([^)]*"(E\d{3})"\)""").findAll(sources()).map { it.groupValues[1] }.toSet()
        assertTrue("no codes found in the sources", raised.size >= 20)
        val missing = raised.filterNot { it in ScriptDiagnostics.CODES }
        assertEquals("codes raised without a hint", emptyList<String>(), missing)
        ScriptDiagnostics.CODES.forEach { (code, hints) ->
            assertTrue(code, hints.first.isNotBlank() && hints.second.isNotBlank())
        }
    }

    @Test
    fun `the spec lists exactly the codes the table carries`() {
        val spec = File("../docs/namascript/SPEC.md").readText()
        val documented = Regex("""^\| (E\d{3}) \|""", RegexOption.MULTILINE).findAll(spec).map { it.groupValues[1] }.toSet()
        assertEquals(ScriptDiagnostics.CODES.keys, documented)
    }

    @Test
    fun `a failure carries its code and hint`() {
        val result = NamaScript.run("plot(closs)", ConformanceSuiteTest.fixture())
        val failure = result.error!!
        assertEquals("E301", failure.code)
        assertTrue(failure.hint(english = false).isNotBlank())
        assertTrue(failure.hint(english = true).contains("Define"))
        assertEquals(1, failure.line)
    }

    @Test
    fun `a message without a code still renders`() {
        val failure = ScriptFailure("x", "y", 0, 0)
        assertEquals("E000", failure.code)
        assertEquals("", failure.hint(english = true))
    }

    private fun sources(): String =
        File("src/commonMain/kotlin").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
}
