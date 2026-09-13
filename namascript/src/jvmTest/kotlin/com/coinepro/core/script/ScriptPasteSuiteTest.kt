package com.coinepro.core.script

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.sin

/**
 * Thirty pastes of the kind an assistant actually returns (run Σ, S3 G).
 *
 * ### Why these are files and not strings in a test
 *
 * Because they are *evidence*, and evidence should be readable without a Kotlin compiler. Each file
 * under `src/jvmTest/resources/pastes` is exactly what somebody would copy out of a chat window —
 * Pine with its header on, NamaScript with one of the twenty mistakes in it, or a sentence in
 * Persian — with a one-line header saying what should become of it:
 *
 * ```
 * // expect: runs      -- repaired, then compiles and runs
 * // expect: clean     -- already correct; the table must find nothing
 * // expect: template  -- prose; the picker must offer something that compiles
 * ```
 *
 * ### What passing means
 *
 * That a reader who pastes this gets a working chart without being asked to understand anything.
 * Not that the repair was clever — that the outcome was the one the file names. A paste that comes
 * out compiling but drawing nothing has failed just as surely as one that does not compile, so the
 * suite runs every repaired script over a real series and requires it to have drawn.
 *
 * Adding a case is adding a file. When an assistant returns something new that this table cannot
 * handle, the file goes in first and the rule follows.
 */
class ScriptPasteSuiteTest {

    private val series: CandleSeries = CandleSeries(
        List(400) { index ->
            val base = 100.0 + sin(index / 9.0) * 6 + sin(index / 31.0) * 14 + index * 0.03
            Candle(
                t = 1_700_000_000L + index * 3_600L,
                o = base - 0.4,
                h = base + 1.1,
                l = base - 1.2,
                c = base,
                v = (900.0 + (index % 17) * 40) * if (index % 37 == 5) 4.0 else 1.0,
            )
        },
    )

    private fun pastes(): List<File> =
        File("src/jvmTest/resources/pastes").listFiles().orEmpty().filter { it.extension == "txt" }.sortedBy { it.name }

    @Test
    fun `there are thirty of them and every one names an outcome`() {
        val files = pastes()
        assertTrue("expected thirty pastes, found ${files.size}", files.size >= 30)
        val unheaded = files.filterNot { it.readText().lineSequence().first().trim().startsWith(HEADER) }.map { it.name }
        assertEquals(emptyList<String>(), unheaded)
    }

    @Test
    fun `every paste reaches the outcome its header names`() {
        val failures = mutableListOf<String>()
        for (file in pastes()) {
            val text = file.readText()
            val expectation = text.lineSequence().first().trim().removePrefix(HEADER).trim()
            val body = text.substringAfter('\n')
            val paste = ScriptPaste.read(body)
            when (expectation) {
                "clean" -> {
                    if (paste.fixes.isNotEmpty()) {
                        failures += "${file.name}: a correct script was rewritten by ${paste.fixes.map { it.id }}"
                    }
                    ranOrNull(file.name, paste.fixed)?.let { failures += it }
                }
                "runs" -> ranOrNull(file.name, paste.fixed)?.let { failures += it }
                "template" -> {
                    if (paste.dialect != ScriptPaste.Dialect.PROSE) {
                        failures += "${file.name}: a sentence was read as ${paste.dialect}"
                    } else if (paste.templates.isEmpty()) {
                        failures += "${file.name}: the picker offered nothing"
                    } else {
                        val filled = paste.templates.first().source(ScriptTemplates.numbersIn(body))
                        ranOrNull(file.name, filled)?.let { failures += it }
                    }
                }
                else -> failures += "${file.name}: unknown expectation “$expectation”"
            }
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun `no paste needs a second pass`() {
        // A reader presses «اصلاح» once. If a second run of the table found more to do, the first
        // answer they saw was not the finished one.
        for (file in pastes()) {
            val body = file.readText().substringAfter('\n')
            val paste = ScriptPaste.read(body)
            // Prose is not code, and the table never touches it — running `repair` over a sentence
            // here would be the test doing something the app does not.
            if (paste.dialect == ScriptPaste.Dialect.PROSE) continue
            assertEquals("${file.name} was not finished after one pass", paste.fixed, ScriptPaste.repair(paste.fixed).first)
        }
    }

    @Test
    fun `every repaired paste keeps what the reader wrote`() {
        // The repairs are textual, and a textual repair that eats a line is a repair that loses the
        // reader's work silently. Nothing here may come out shorter in statements than it went in.
        for (file in pastes()) {
            val body = file.readText().substringAfter('\n')
            val paste = ScriptPaste.read(body)
            if (paste.dialect == ScriptPaste.Dialect.PROSE) continue
            val before = body.lines().count { it.isStatement() }
            val after = paste.fixed.lines().count { it.isStatement() }
            assertTrue("${file.name} lost ${before - after} statements", after >= before - HEADERS_ALLOWED)
        }
    }

    /**
     * The failure message for [source], or null when it ran and drew.
     *
     * Drawing is part of it. A script that compiles and puts nothing on the chart is, to the reader
     * who pasted it, indistinguishable from one that failed — and a paste box that reports success
     * over a blank chart is the worst of the outcomes, because they will go looking for the bug in
     * their own idea rather than in the paste.
     */
    private fun ranOrNull(name: String, source: String): String? {
        val result = NamaScript.run(source, series)
        result.error?.let { return "$name: ${it.messageEn} at line ${it.line} (${it.code})\n$source" }
        val drew = result.plots.isNotEmpty() || result.markers.isNotEmpty() ||
            result.levels.isNotEmpty() || result.verdicts.isNotEmpty()
        return if (drew) null else "$name: it ran and drew nothing\n$source"
    }

    private fun String.isStatement(): Boolean = trim().isNotEmpty() && !trim().startsWith("//")

    private companion object {
        const val HEADER = "// expect:"

        /** A Pine header becomes a comment, so one statement may legitimately go. */
        const val HEADERS_ALLOWED = 1
    }
}
