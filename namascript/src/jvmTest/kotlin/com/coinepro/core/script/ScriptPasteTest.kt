package com.coinepro.core.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What happens to whatever a reader pastes in (run Σ, S3 A).
 *
 * Two halves, and the second one matters more than the first.
 *
 * The first half is the obvious one: each of the twenty rules gets a case proving it fires on the
 * mistake it is named for.
 *
 * The second half is [no rule changes a correct script]. Every rule here is a textual substitution
 * run over a reader's own code without asking, and the admission test for the table is that
 * applying it to something already correct must be a no-op. A rule that is merely *usually* right
 * would silently rewrite somebody's working script, which is a worse failure than never having
 * offered the fix — so the whole conformance suite is run through [ScriptPaste.repair] and required
 * to come out byte-identical.
 */
class ScriptPasteTest {

    /**
     * The table applied to [source], with a `plot` appended first and taken off again.
     *
     * Because one of the twenty rules adds a missing `plot`, and without it every one-line case
     * below would come back with a plot stapled to the end of it — which is the rule working, and
     * noise in every test that is not about that rule. [rawFixIds] is the unwrapped form.
     */
    private fun repaired(source: String): String =
        ScriptPaste.repair("$source\n$DRAWS").first.removeSuffix("\n$DRAWS")

    private fun fixIds(source: String): List<String> = ScriptPaste.repair("$source\n$DRAWS").second.map { it.id }

    private fun rawFixIds(source: String): List<String> = ScriptPaste.repair(source).second.map { it.id }

    // ── which of the three things is it ───────────────────────────────────────────────────────

    @Test
    fun `a pine script is recognised by its header`() {
        assertEquals(
            ScriptPaste.Dialect.PINE,
            ScriptPaste.dialectOf(
                """
                //@version=5
                indicator("My MA", overlay=true)
                plot(ta.sma(close, 20))
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `a pine script is recognised without its header too`() {
        // An assistant asked for «a TradingView indicator» often omits the header entirely and the
        // give-away is a name that only Pine has.
        assertEquals(ScriptPaste.Dialect.PINE, ScriptPaste.dialectOf("x = ta.rma(close, 14)"))
        assertEquals(ScriptPaste.Dialect.PINE, ScriptPaste.dialectOf("plotshape(c, style=shape.triangleup)"))
        assertEquals(ScriptPaste.Dialect.PINE, ScriptPaste.dialectOf("var float top = na\ntop := high"))
        assertEquals(ScriptPaste.Dialect.PINE, ScriptPaste.dialectOf("x = input.int(20, minval=1)"))
    }

    @Test
    fun `what this language shares with pine is not evidence of pine`() {
        // The expensive mistake this file exists to stop. `var`, `:=`, `plotshape`,
        // `request.security` and `strategy.entry` all look like Pine and are all NamaScript's own:
        // sending one of these through the translator would be translating a script that ran.
        for (source in listOf(
            "var top = high\ntop := high\nplot(top)",
            "plotshape(close > open, title = \"up\")",
            "daily = request.security(close, \"1D\")\nplot(daily)",
            "strategy.entry(strategy.long, close > open)",
        )) {
            assertEquals(source, ScriptPaste.Dialect.NAMA, ScriptPaste.dialectOf(source))
            assertEquals("it was rewritten: $source", source, ScriptPaste.repair(source).first)
        }
    }

    @Test
    fun `namascript is recognised`() {
        assertEquals(ScriptPaste.Dialect.NAMA, ScriptPaste.dialectOf("average = ta.ema(close, 20)\nplot(average)"))
        assertEquals(ScriptPaste.Dialect.NAMA, ScriptPaste.dialectOf("plot(ta.rsi(close, 14), ownPane = true)"))
    }

    @Test
    fun `a sentence is a sentence`() {
        assertEquals(ScriptPaste.Dialect.PROSE, ScriptPaste.dialectOf("وقتی میانگین ۲۰ از ۵۰ رد شد بخر"))
        assertEquals(ScriptPaste.Dialect.PROSE, ScriptPaste.dialectOf("buy when rsi comes back over thirty"))
        assertEquals(ScriptPaste.Dialect.PROSE, ScriptPaste.dialectOf(""))
        assertEquals(ScriptPaste.Dialect.PROSE, ScriptPaste.dialectOf("   \n  \n"))
    }

    @Test
    fun `prose arrives with templates and code does not`() {
        val prose = ScriptPaste.read("وقتی میانگین ۲۰ از ۵۰ رد شد")
        assertTrue(prose.templates.isNotEmpty())
        assertEquals(prose.fixed, "وقتی میانگین ۲۰ از ۵۰ رد شد")
        assertTrue(ScriptPaste.read("plot(ta.ema(close, 20))").templates.isEmpty())
    }

    // ── the twenty rules, one case each ───────────────────────────────────────────────────────

    @Test
    fun `the pine header is commented out rather than deleted`() {
        // Commented and not deleted, because the header carries the reader's own title and a paste
        // box that silently ate it would have thrown away the one human thing in the file.
        val fixed = ScriptPaste.repair("indicator(\"My MA\", overlay=true)\nplot(ta.sma(close, 20))").first
        assertTrue(fixed, fixed.lines().first().startsWith("//"))
        assertTrue("the title was thrown away", "My MA" in fixed)
        assertTrue("drop-indicator-header" in rawFixIds("study(\"x\")\nplot(close)"))
        assertTrue("a strategy header is a header too", "drop-indicator-header" in rawFixIds("strategy(\"x\")\nplot(close)"))
    }

    @Test
    fun `pine's names for input's bounds are translated`() {
        assertEquals("""x = input(20, title = "L", min = 2, max = 90)""", repaired("""x = input(20, title = "L", minval = 2, maxval = 90)"""))
        assertTrue("input-bounds" in fixIds("x = input(20, minval = 2)"))
    }

    @Test
    fun `a version comment is left where it is`() {
        // `//@version=5` is a comment in this language and nothing more. Deleting it would be the
        // paste box editing a reader's file for cosmetics; Pine's own header is taken off by
        // PineTranslator on the way in, which is where that belongs.
        val source = "//@version=1\nplot(close)"
        assertEquals(source, ScriptPaste.repair(source).first)
    }

    @Test
    fun `pine's shape constants become the text this language reads`() {
        assertEquals("""marker(x, style = "triangleup")""", repaired("""marker(x, style = shape.triangleup)"""))
        assertTrue("pine-shape-constants" in fixIds("marker(x, style = shape.arrowdown)"))
    }

    @Test
    fun `pine's argument names become this language's`() {
        assertEquals("plot(close, width = 2)", repaired("plot(close, linewidth = 2)"))
        assertEquals("plot(close)", repaired("plot(close, overlay = true)"))
        assertTrue("linewidth-argument" in fixIds("plot(close, linewidth = 2)"))
        assertTrue("drop-overlay-argument" in fixIds("plot(close, overlay = false)"))
    }

    @Test
    fun `typographic quotes become plain ones`() {
        assertEquals("""plot(close, title = "MA")""", repaired("plot(close, title = \u201cMA\u201d)"))
        assertTrue("smart-quotes" in fixIds("plot(close, title = \u201cMA\u201d)"))
    }

    @Test
    fun `pine's names for this language's functions are translated`() {
        assertEquals("x = ta.smma(close, 14)", repaired("x = ta.rma(close, 14)"))
        assertEquals("x = ta.stoch_k(14)", repaired("x = ta.stoch(14)"))
        assertEquals("x = ta.crossover(a, b)", repaired("x = ta.cross(a, b)"))
        // …and the ones that already have the right name are left alone.
        assertEquals("x = ta.crossunder(a, b)", repaired("x = ta.crossunder(a, b)"))
        assertEquals("x = ta.stoch_d(14)", repaired("x = ta.stoch_d(14)"))
    }

    @Test
    fun `a bare function name gets its prefix`() {
        assertEquals("x = ta.sma(close, 20)", repaired("x = sma(close, 20)"))
        assertEquals("x = ta.rsi(close, 14)", repaired("x = rsi(close, 14)"))
        assertTrue("bare-ta-names" in fixIds("x = ema(close, 9)"))
        // A name that already carries the prefix is not given a second one.
        assertEquals("x = ta.sma(close, 20)", repaired("x = ta.sma(close, 20)"))
    }

    @Test
    fun `comparing with absence becomes the question it was meant to be`() {
        assertEquals("cond = na(top)", repaired("cond = top == na"))
        assertEquals("cond = not na(top)", repaired("cond = top != na"))
        assertTrue("na-comparison" in fixIds("cond = top == na"))
    }

    @Test
    fun `a single equals inside a condition becomes two`() {
        assertEquals("x = a and b == c", repaired("x = a and b = c"))
        assertEquals("x = a or b == c", repaired("x = a or b = c"))
        assertTrue("equality" in fixIds("x = a and b = c"))
    }

    @Test
    fun `the symbol operators become words`() {
        assertEquals("x = a and b", repaired("x = a && b"))
        assertEquals("x = a or b", repaired("x = a || b"))
        assertEquals("x = not a", repaired("x = !a"))
        // …and the comparison that merely contains one is untouched.
        assertEquals("x = a != b", repaired("x = a != b"))
    }

    @Test
    fun `plot's positional title is named`() {
        assertEquals("""plot(close, title = "MA")""", repaired("""plot(close, "MA")"""))
        assertEquals(
            """plot(ta.sma(close, 20), title = "MA", color = color.gold)""",
            repaired("""plot(ta.sma(close, 20), "MA", color = color.gold)"""),
        )
        // A plot whose title is already named is left exactly as it is.
        assertEquals("""plot(close, title = "MA")""", repaired("""plot(close, title = "MA")"""))
    }

    @Test
    fun `the argument keeps its american spelling`() {
        assertEquals("plot(close, color = color.gold)", repaired("plot(close, colour = color.gold)"))
        assertTrue("colour-spelling" in fixIds("plot(close, colour = color.gold)"))
    }

    @Test
    fun `a price called like a function becomes an index`() {
        assertEquals("x = close[1]", repaired("x = close(1)"))
        assertEquals("x = high[2] - low[2]", repaired("x = high(2) - low(2)"))
        // A real call with a real argument is untouched.
        assertEquals("x = ta.sma(close, 20)", repaired("x = ta.sma(close, 20)"))
    }

    @Test
    fun `a trailing semicolon goes`() {
        assertEquals("x = close", repaired("x = close;"))
        assertTrue("semicolons" in fixIds("x = close;"))
    }

    @Test
    fun `persian digits in the code become latin`() {
        assertEquals("x = ta.sma(close, 20)", repaired("x = ta.sma(close, ۲۰)"))
        assertTrue("persian-digits" in fixIds("x = ta.sma(close, ۲۰)"))
    }

    @Test
    fun `a script that draws nothing is given a plot`() {
        val fixed = ScriptPaste.repair("average = ta.ema(close, 20)").first
        assertTrue(fixed, "plot(average)" in fixed)
        assertTrue("add-plot" in rawFixIds("average = ta.ema(close, 20)"))
        // …and a script whose whole job is a signal is a legitimate shape, left alone.
        assertTrue("add-plot" !in rawFixIds("rising = close > open\nsignal(rising, text = \"سبز\")"))
        // …and a script that already draws keeps exactly one plot.
        assertTrue("add-plot" !in rawFixIds("average = ta.ema(close, 20)\nplot(average)"))
    }

    @Test
    fun `there are twenty rules and every id is its own`() {
        assertEquals(20, ScriptPaste.FIXES.size)
        assertEquals(ScriptPaste.FIXES.size, ScriptPaste.FIXES.map { it.id }.toSet().size)
    }

    @Test
    fun `every rule explains itself in both languages`() {
        for (rule in ScriptPaste.FIXES) {
            assertTrue("${rule.id} has no Persian line", rule.what.any { it in '؀'..'ۿ' })
            assertTrue("${rule.id}'s English line is Persian: ${rule.whatEn}", rule.whatEn.none { it in '؀'..'ۿ' })
        }
    }

    @Test
    fun `every code a rule names is a diagnostic the app can show`() {
        // The fix is offered *under* the error it repairs, so a code nobody defined is a fix the
        // reader never sees.
        val unknown = ScriptPaste.FIXES.map { it.code }.filter { it.isNotEmpty() && it !in ScriptDiagnostics.CODES }
        assertEquals(emptyList<String>(), unknown)
    }

    // ── the admission test ───────────────────────────────────────────────────────────────────

    @Test
    fun `no rule changes a correct script`() {
        // The whole table, over every script the conformance suite holds. A rule that rewrites one
        // of these has changed the meaning of something that already ran, which is the one thing a
        // one-tap fix must never do.
        val suite = java.io.File("src/jvmTest/resources/conformance").listFiles().orEmpty()
            .filter { it.extension == "nama" }
        assertTrue("the conformance suite was not found", suite.size >= 300)
        val changed = suite.filter { file ->
            val source = file.readText()
            // Only the scripts that actually run: a file whose expectation is an error is a file
            // full of deliberate mistakes, and repairing those is the point of the table.
            "expect: error" !in source && repaired(source) != source
        }.map { file -> "${file.name}: ${fixIds(file.readText())}" }
        assertEquals(emptyList<String>(), changed)
    }

    @Test
    fun `no rule changes a template or a preset or a library strategy`() {
        val changed = buildList {
            for (template in ScriptTemplates.ALL) {
                if (repaired(template.source()) != template.source()) add("template ${template.id}: ${fixIds(template.source())}")
            }
            for (preset in ScriptPresets.ALL) {
                if (repaired(preset.source) != preset.source) add("preset ${preset.id}: ${fixIds(preset.source)}")
            }
            for (strategy in ScriptStrategies.ALL) {
                if (repaired(strategy.source) != strategy.source) add("strategy ${strategy.id}: ${fixIds(strategy.source)}")
            }
        }
        assertEquals(emptyList<String>(), changed)
    }

    @Test
    fun `a reader's own text is never rewritten`() {
        // Every rule is a search-and-replace, and the reader's labels are full of the things they
        // search for. A Persian numeral in a label is prose; «&&» in a label is a label.
        val source = """
            note = "۳ کندل، a && b, colour, close(1), linewidth = 2"
            plot(close, title = note)
        """.trimIndent()
        assertEquals(source, ScriptPaste.repair(source).first)
    }

    @Test
    fun `a comment is not code`() {
        val source = "// buy when close = open and linewidth = 2\nplot(close)"
        assertEquals(source, ScriptPaste.repair(source).first)
    }

    @Test
    fun `applying the table twice is the same as applying it once`() {
        // The rules run in table order, once. Each has to be its own fixed point or a second paste
        // of the same text — which is exactly what a reader does when they undo and try again —
        // would drift.
        val messy = """
            //@version=5
            indicator("Mine", overlay=true)
            fast = sma(close, ۹)
            slow = ta.rma(close, 21);
            trend = close > slow && fast > slow
            plotshape(trend, style = shape.triangleup, linewidth = 2)
            hline(0)
        """.trimIndent()
        val once = ScriptPaste.repair(messy).first
        assertEquals(once, ScriptPaste.repair(once).first)
    }

    // ── end to end ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `a plausible assistant answer comes out running`() {
        // What an assistant that was told «NamaScript» but remembered Pine actually returns.
        val answer = """
            //@version=5
            indicator("EMA Cross", overlay=true)
            fastLen = input(9, "Fast")
            slowLen = input(21, "Slow")
            fast = ema(close, fastLen)
            slow = ema(close, slowLen)
            plot(fast, "Fast", color=color.gold)
            plot(slow, "Slow", color=color.blue)
            signal(ta.crossover(fast, slow), text="fast crossed up")
        """.trimIndent()
        val paste = ScriptPaste.read(answer)
        assertTrue("nothing was repaired", paste.fixes.isNotEmpty())
        assertNull("the repaired script still does not compile:\n${paste.fixed}", NamaScript.check(paste.fixed))
    }

    @Test
    fun `a pine script goes through the translator and then the table`() {
        val pine = """
            //@version=5
            indicator("RSI", overlay=false)
            r = ta.rsi(close, 14)
            plot(r, "RSI")
            hline(70)
            hline(30)
        """.trimIndent()
        val paste = ScriptPaste.read(pine)
        assertEquals(ScriptPaste.Dialect.PINE, paste.dialect)
        assertNull("the translated script does not compile:\n${paste.fixed}", NamaScript.check(paste.fixed))
    }

    @Test
    fun `what pine carried and this language has not is reported rather than dropped silently`() {
        val pine = """
            //@version=5
            strategy("S", overlay=true)
            for i = 0 to 10
                x = i
            plot(close)
        """.trimIndent()
        val paste = ScriptPaste.read(pine)
        assertTrue("the loop was swallowed without a word", paste.unsupported.isNotEmpty())
        assertTrue(paste.changed)
    }

    @Test
    fun `a fix names the lines it touched`() {
        val fixes = ScriptPaste.repair("plot(close)\nx = ta.rma(close, 14)\ny = ta.rma(close, 21)").second
        val fix = fixes.first { it.id == "rma-to-smma" }
        assertEquals(listOf(2, 3), fix.lines)
    }

    @Test
    fun `an empty paste is not a crash`() {
        assertEquals("", ScriptPaste.repair("").first)
        assertEquals(ScriptPaste.Dialect.PROSE, ScriptPaste.read("").dialect)
        assertNotNull(ScriptPaste.read("").templates)
    }

    @Test
    fun `a clean script reports no change at all`() {
        val clean = "average = ta.ema(close, 20)\nplot(average, title = \"میانگین\")\n"
        val paste = ScriptPaste.read(clean)
        assertEquals(clean, paste.fixed)
        assertTrue(paste.fixes.isEmpty())
        assertTrue("a clean paste claimed it had changed something", !paste.changed)
    }

    private companion object {
        /** A line that draws, so the rule that adds a missing plot has nothing to do. */
        const val DRAWS = "plot(close)"
    }
}
