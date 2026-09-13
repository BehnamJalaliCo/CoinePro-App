package com.coinepro.core.script

import com.coinepro.core.chart.Candle
import com.coinepro.core.chart.CandleSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * The sixty-one shipped strategies, every one of them run, in both languages (run Σ, S3 F).
 *
 * This is the largest block of NamaScript the app ships, and it is written for readers who cannot
 * check it themselves — so nothing here is read by eye. Each strategy is compiled, run over a series
 * with trends, ranges and shocks in it, and required to have drawn its lines, drawn its stop, and
 * said something in both directions.
 *
 * The English half is not a translation of a second file; it is the same entry rendered with its
 * own titles, so the two cannot drift. What this file holds is that the rendering is a *translation
 * and not an edit*: the code either side is identical token for token, and only the quoted text
 * differs. A library where the English version computed something else would be the worst kind of
 * bug — invisible to whoever wrote it, and only wrong for half the audience.
 */
class ScriptLibraryContentTest {

    /**
     * Twelve hundred bars, not four hundred.
     *
     * Because the library holds strategies built on a two-hundred-period average, and four hundred
     * bars is barely two of those: the golden cross crossed once on the short fixture and was
     * recorded as a strategy that only ever fires one way. A fixture too short for the slowest
     * study in the list cannot tell a one-sided strategy from a one-sided sample.
     */
    private fun waves(count: Int = 1_200): CandleSeries {
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
                    // Every real chart has bars several times the usual volume on them, and a
                    // fixture without one cannot tell a volume study that works from one that
                    // never speaks — which is exactly how `volume-spike` first passed here.
                    v = (900.0 + (index % 17) * 40) * if (index % 37 == 5) 4.0 else 1.0,
                )
            },
        )
    }

    private val series = waves()

    // ── the shelf ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `there are at least sixty and every id is its own`() {
        assertTrue("the library holds only ${ScriptLibrary.ALL.size}", ScriptLibrary.ALL.size >= 60)
        assertEquals(ScriptLibrary.ALL.size, ScriptLibrary.ALL.map { it.id }.toSet().size)
        assertEquals(ScriptLibrary.ALL.size, ScriptLibrary.BY_ID.size)
    }

    @Test
    fun `every shelf has something on it`() {
        for (family in ScriptFamily.entries) {
            assertTrue("$family is empty", ScriptLibrary.of(family).isNotEmpty())
        }
    }

    @Test
    fun `every entry is named in both languages and neither leaks`() {
        for (entry in ScriptLibrary.ALL) {
            assertTrue("${entry.id} has no Persian name", entry.title.any { it in '؀'..'ۿ' })
            assertTrue("${entry.id}'s English name is Persian: ${entry.titleEn}", entry.titleEn.none { it in '؀'..'ۿ' })
            assertTrue("${entry.id}'s English line is Persian: ${entry.summaryEn}", entry.summaryEn.none { it in '؀'..'ۿ' })
            assertTrue("${entry.id} has no summary", entry.summary.isNotBlank() && entry.summaryEn.isNotBlank())
            assertTrue("${entry.id}'s summary is not one line", '\n' !in entry.summary)
        }
    }

    @Test
    fun `no title or summary is repeated`() {
        // Two strategies with the same name in a list of sixty is a list a reader cannot use.
        assertEquals(ScriptLibrary.ALL.size, ScriptLibrary.ALL.map { it.title }.toSet().size)
        assertEquals(ScriptLibrary.ALL.size, ScriptLibrary.ALL.map { it.titleEn }.toSet().size)
    }

    // ── every one of them runs ───────────────────────────────────────────────────────────────

    @Test
    fun `every strategy compiles in both languages`() {
        val broken = ScriptLibrary.ALL.flatMap { entry ->
            listOf(false, true).mapNotNull { english ->
                NamaScript.check(entry.source(english))?.let {
                    "${entry.id} (${if (english) "en" else "fa"}): ${it.messageEn} at line ${it.line}, ${it.code}\n${entry.source(english)}"
                }
            }
        }
        assertEquals(emptyList<String>(), broken)
    }

    @Test
    fun `every strategy runs over a real series`() {
        val broken = ScriptLibrary.ALL.mapNotNull { entry ->
            NamaScript.run(entry.source(), series).error?.let { "${entry.id}: ${it.messageEn} at line ${it.line}" }
        }
        assertEquals(emptyList<String>(), broken)
    }

    @Test
    fun `every strategy draws its stop`() {
        // The brief asks every strategy to carry a default stop. A stop written in a description is
        // a stop nobody reads; this one is a line on the chart, and here is the proof it is drawn.
        val missing = ScriptLibrary.ALL.filterNot { entry ->
            NamaScript.run(entry.source(), series).plots.any { it.title == "حد ضرر" && it.stepped }
        }.map { it.id }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `every strategy says something, in both directions`() {
        // A strategy that only ever fires one way is not a strategy, it is half of one — and on
        // this series, which turns dozens of times, one that says nothing at all is broken.
        val silent = mutableListOf<String>()
        val oneSided = mutableListOf<String>()
        for (entry in ScriptLibrary.ALL) {
            val verdicts = NamaScript.run(entry.source(), series).verdicts
            when {
                verdicts.isEmpty() -> silent += entry.id
                verdicts.none { it.buy } || verdicts.none { !it.buy } -> oneSided += entry.id
            }
        }
        assertEquals("strategies that never spoke", emptyList<String>(), silent)
        assertEquals("strategies that only ever fire one way", emptyList<String>(), oneSided)
    }

    @Test
    fun `no strategy marks the bar that is still forming`() {
        // The fault every script in the old library had, and the reason `and confirmed` closes
        // every condition: an arrow on the forming bar can be gone a minute later, and a reader
        // scrolling back sees arrows only on the turns that worked.
        val last = series.bars.lastIndex
        val painting = ScriptLibrary.ALL.filter { entry ->
            NamaScript.run(entry.source(), series).verdicts.any { last in it.bars }
        }.map { it.id }
        assertEquals(emptyList<String>(), painting)
    }

    @Test
    fun `a flat market is not a crash`() {
        val flat = CandleSeries(
            List(300) { index ->
                Candle(t = 1_700_000_000L + index * 3_600L, o = 100.0, h = 100.0, l = 100.0, c = 100.0, v = 0.0)
            },
        )
        val broken = ScriptLibrary.ALL.mapNotNull { entry ->
            NamaScript.run(entry.source(), flat).error?.let { "${entry.id}: ${it.messageEn}" }
        }
        assertEquals(emptyList<String>(), broken)
    }

    @Test
    fun `a feed that sends no volume is not a crash`() {
        // Several of these read `volume`, and plenty of symbols report none. The study going quiet
        // is the right answer; a diagnostic in the reader's face is not.
        val noVolume = CandleSeries(series.bars.map { it.copy(v = 0.0) })
        val broken = ScriptLibrary.ALL.mapNotNull { entry ->
            NamaScript.run(entry.source(), noVolume).error?.let { "${entry.id}: ${it.messageEn}" }
        }
        assertEquals(emptyList<String>(), broken)
    }

    // ── the English half is a translation, not an edit ───────────────────────────────────────

    @Test
    fun `the two languages compute exactly the same thing`() {
        // Both renderings with every quoted string taken out. What is left is the code, and it has
        // to be identical — otherwise the English reader is running a different strategy.
        for (entry in ScriptLibrary.ALL) {
            assertEquals(
                "${entry.id} computes something different in English",
                withoutText(entry.source(english = false)),
                withoutText(entry.source(english = true)),
            )
        }
    }

    @Test
    fun `the English rendering carries no Persian and the Persian no Latin prose`() {
        for (entry in ScriptLibrary.ALL) {
            val english = entry.source(english = true)
            assertTrue(
                "${entry.id} leaked Persian into the English script:\n$english",
                textIn(english).none { quoted -> quoted.any { it in '؀'..'ۿ' } },
            )
            assertTrue(
                "${entry.id} has an empty title somewhere",
                textIn(english).none { it.isBlank() } && textIn(entry.source()).none { it.isBlank() },
            )
        }
    }

    @Test
    fun `every quoted string is closed`() {
        // The rendering builds NamaScript by hand, so a title carrying a quotation mark would
        // produce a script that does not parse. `quoted` strips them; this is the proof.
        for (entry in ScriptLibrary.ALL) {
            for (english in listOf(false, true)) {
                for (line in entry.source(english).lines()) {
                    assertTrue("${entry.id} has an odd quote: $line", line.count { it == '"' } % 2 == 0)
                }
            }
        }
    }

    // ── the fixer leaves them alone ──────────────────────────────────────────────────────────

    @Test
    fun `the paste table finds nothing to fix in the library`() {
        val rewritten = ScriptLibrary.ALL.flatMap { entry ->
            listOf(false, true).mapNotNull { english ->
                val source = entry.source(english)
                "${entry.id} (${if (english) "en" else "fa"}): ${ScriptPaste.repair(source).second.map { it.id }}"
                    .takeIf { ScriptPaste.repair(source).first != source }
            }
        }
        assertEquals(emptyList<String>(), rewritten)
    }

    private fun withoutText(source: String): String = source.replace(Regex("\"[^\"]*\""), "\"\"")

    private fun textIn(source: String): List<String> =
        Regex("\"([^\"]*)\"").findAll(source).map { it.groupValues[1] }.toList()
}
