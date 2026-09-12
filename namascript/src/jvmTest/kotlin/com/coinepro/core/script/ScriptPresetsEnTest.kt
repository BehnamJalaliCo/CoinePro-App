package com.coinepro.core.script

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every shipped preset exists in both languages, and both halves compile.
 *
 * The failure this catches is the one a translation table always has: a preset added to the Persian
 * list and forgotten in the English one, which an English reader meets as a Persian card. The
 * second assertion is the one that matters more — the English *source* is code, not prose, and a
 * translated comment that swallowed a bracket would hand somebody a script that does not run.
 */
class ScriptPresetsEnTest {

    private val persian = Regex("[\\u0600-\\u06FF]")

    @Test
    fun `every preset has an English twin`() {
        val missing = ScriptPresets.ALL.filter { ScriptPresetsEn.BY_ID[it.id] == null }
        assertTrue("these presets have no English text: ${missing.map { it.id }}", missing.isEmpty())
        // And nothing in the English table names a preset that no longer ships.
        val orphans = ScriptPresetsEn.BY_ID.keys - ScriptPresets.ALL.map { it.id }.toSet()
        assertTrue("these English entries name no preset: $orphans", orphans.isEmpty())
    }

    @Test
    fun `no English preset carries Persian anywhere`() {
        for (preset in ScriptPresets.all(english = true)) {
            for ((what, text) in listOf(
                "title" to preset.title,
                "summary" to preset.summary,
                "teaches" to preset.teaches,
                "source" to preset.source,
            )) {
                assertTrue(
                    "${preset.id}'s $what is still Persian: $text",
                    !persian.containsMatchIn(text),
                )
            }
        }
        assertTrue("and the blank script too", !persian.containsMatchIn(ScriptPresets.blank(english = true)))
    }

    @Test
    fun `both languages of every preset compile and run`() {
        val series = CandleSeries(
            (0 until 300).map { index ->
                val base = 100.0 + index * 0.3
                Candle(t = 1_700_000_000L + index * 3_600L, o = base, h = base + 1.0, l = base - 1.0, c = base + 0.2, v = 100.0 + index)
            },
        )
        for (preset in ScriptPresets.ALL) {
            for (language in listOf(false, true)) {
                val text = preset.localised(language).source
                val compiled = NamaScript.compile(text)
                assertEquals(
                    "${preset.id} (${if (language) "en" else "fa"}) did not compile: ${compiled.failure?.messageEn}",
                    null,
                    compiled.failure,
                )
                val result = compiled.script!!.run(series)
                assertEquals(
                    "${preset.id} (${if (language) "en" else "fa"}) failed to run: ${result.error?.messageEn}",
                    null,
                    result.error,
                )
            }
        }
    }

    @Test
    fun `the two languages are the same code`() {
        // Line for line, once the prose inside the quotes and after the slashes is taken out. This
        // is what keeps a translation from quietly becoming a different study.
        for (preset in ScriptPresets.ALL) {
            val en = ScriptPresetsEn.BY_ID.getValue(preset.id)
            assertEquals(
                "${preset.id} has a different shape in the two languages",
                skeleton(preset.source),
                skeleton(en.source),
            )
        }
    }

    /** A script with its comments dropped and its string contents blanked. */
    private fun skeleton(source: String): List<String> = source
        .lines()
        .map { it.substringBefore("//").trim() }
        .map { it.replace(Regex("\"[^\"]*\""), "\"\"") }
        .filter { it.isNotBlank() }
}
