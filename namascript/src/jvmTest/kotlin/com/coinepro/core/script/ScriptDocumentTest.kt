package com.coinepro.core.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A script the reader owns: saved, edited, undone, exported, imported, linked (run Σ, S3 C).
 *
 * The round trip is the centre of this file. A reader who exports a script and imports it back has
 * to get the same thing — not «a script that also runs», the same one, with the name they typed, the
 * colour they picked and the numbers they set. Anything less and the export button is a way to lose
 * work that looks like a way to keep it.
 */
class ScriptDocumentTest {

    private val source = """
        length = input(20, title = "دوره", min = 2, max = 200)
        average = ta.ema(close, length)
        plot(average, title = "میانگین من", color = color.gold)
        signal(ta.crossover(close, average) and confirmed, text = "قیمت رد شد")
    """.trimIndent()

    private fun document(): ScriptDocument = ScriptDocument.of(
        source = source,
        name = "میانگین من",
        at = 1_700_000_000_000L,
        description = "میانگینی که خودم ساختم",
        origin = "ema-cross",
    ).copy(
        colour = 0xFF2962FF,
        tags = listOf("روند", "میانگین"),
        ownPane = false,
        defaults = mapOf("دوره" to 34.0),
    )

    // ── saving ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a saved script keeps what the reader chose`() {
        val saved = document()
        assertEquals("میانگین من", saved.name)
        assertEquals("میانگینی که خودم ساختم", saved.description)
        assertEquals("ema-cross", saved.origin)
        assertTrue(saved.id.isNotEmpty())
    }

    @Test
    fun `saving the same script twice in the same moment is one script`() {
        // The double tap. Two entries a reader then has to tell apart is the failure here.
        val once = ScriptDocument.of(source, "x", at = 1L)
        val twice = ScriptDocument.of(source, "x", at = 1L)
        assertEquals(once.id, twice.id)
    }

    @Test
    fun `two different scripts are two different ids`() {
        val one = ScriptDocument.of(source, "x", at = 1L)
        val other = ScriptDocument.of(source + "\nplot(close)", "x", at = 1L)
        assertNotEquals(one.id, other.id)
        assertNotEquals(one.id, ScriptDocument.of(source, "x", at = 2L).id)
    }

    @Test
    fun `an id is something a link can carry and a person can read out`() {
        for (at in 0L..500L) {
            val id = ScriptDocument.idFor(source, at)
            assertTrue("$id is not URL-safe", Regex("^[a-z2-9]+$").matches(id))
            assertTrue("$id has a character that is read wrong aloud", id.none { it in "l1o0" })
        }
    }

    @Test
    fun `a name too long for a list is cut, and an empty one has a fallback`() {
        val long = ScriptDocument.of(source, "ی".repeat(200), at = 1L)
        assertEquals(ScriptDocument.NAME_LIMIT, long.name.length)
        assertEquals("اسکریپت بی‌نام", ScriptDocument.of(source, "   ", at = 1L).displayName(english = false))
        assertEquals("Untitled script", ScriptDocument.of(source, "", at = 1L).displayName(english = true))
    }

    @Test
    fun `a description that arrived with newlines in it becomes one line`() {
        val saved = ScriptDocument.of(source, "x", at = 1L, description = "یک\nدو")
        assertEquals("یک دو", saved.description)
    }

    // ── the history ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `an edit keeps the version before it`() {
        val first = document()
        val second = first.edited("plot(close)", at = 2L)
        assertEquals("plot(close)", second.source)
        assertEquals(1, second.history.size)
        assertEquals(source, second.history.first().source)
    }

    @Test
    fun `only the last five are kept`() {
        var script = document()
        for (revision in 1..12) script = script.edited("plot(close + $revision)", at = revision.toLong())
        assertEquals(ScriptDocument.REVISIONS, script.history.size)
        // Newest first, and the newest is the one immediately before the current source.
        assertEquals("plot(close + 11)", script.history.first().source)
        assertEquals("plot(close + 7)", script.history.last().source)
    }

    @Test
    fun `opening a script and closing it does not use up a revision`() {
        val first = document()
        val again = first.edited(source, at = 99L)
        assertTrue("looking at a script spent a revision", again.history.isEmpty())
        assertEquals(99L, again.updatedAt)
    }

    @Test
    fun `an undo is itself undoable`() {
        // A reader who rolls back and then wants the pasted version again must be able to get it:
        // restoring is an edit like any other, so what it replaced goes onto the history.
        val first = document()
        val pasted = first.edited("plot(ta.rsi(close, 14), pane = \"own\")", at = 2L)
        val rolledBack = pasted.restored(pasted.history.first(), at = 3L)
        assertEquals(source, rolledBack.source)
        assertEquals("the version rolled back from was lost", "plot(ta.rsi(close, 14), pane = \"own\")", rolledBack.history.first().source)
    }

    // ── export and import ────────────────────────────────────────────────────────────────────

    @Test
    fun `a script survives the round trip whole`() {
        val saved = document()
        val read = ScriptFile.read(ScriptFile.write(saved))
        assertEquals(saved.id, read?.id)
        assertEquals(saved.name, read?.name)
        assertEquals(saved.description, read?.description)
        assertEquals(saved.source, read?.source)
        assertEquals(saved.colour, read?.colour)
        assertEquals(saved.tags, read?.tags)
        assertEquals(saved.ownPane, read?.ownPane)
        assertEquals(saved.defaults, read?.defaults)
        assertEquals(saved.origin, read?.origin)
    }

    @Test
    fun `the round trip is stable a second time`() {
        val once = ScriptFile.write(document())
        assertEquals(once, ScriptFile.write(ScriptFile.read(once)!!))
    }

    @Test
    fun `an exported file is itself a runnable script`() {
        // The whole reason the header is comment lines. A reader who pastes the file into the studio
        // — header and all, which is what actually happens — gets their script, not an error.
        val exported = ScriptFile.write(document())
        assertNull("the exported file does not compile:\n$exported", NamaScript.check(exported))
    }

    @Test
    fun `the fixer finds nothing to do to an exported file`() {
        val exported = ScriptFile.write(document())
        assertEquals(exported, ScriptPaste.repair(exported).first)
    }

    @Test
    fun `a bare script is not mistaken for a file`() {
        // The likeliest thing on the other end of «import» is a plain script somebody saved with
        // the wrong extension. Reading it as a malformed document would be worse than refusing.
        assertNull(ScriptFile.read(source))
        assertNull(ScriptFile.read(""))
        assertNull(ScriptFile.read("// nama 1\n// id: abc\nplot(close)"))
        assertNull(ScriptFile.read("// nama 1\n// ---\n"))
    }

    @Test
    fun `a name carrying a quote or a newline cannot break the file`() {
        val awkward = document().copy(name = "a\nb: c \"d\"", description = "x\r\ny")
        val read = ScriptFile.read(ScriptFile.write(awkward))
        assertEquals(source, read?.source)
        assertTrue("the name broke the header: ${read?.name}", read?.name?.contains('\n') == false)
    }

    @Test
    fun `a file with an unknown key is still read`() {
        // Forwards compatibility, cheaply: a file written by a later version has keys this one has
        // never heard of, and refusing it would strand a reader's own script on their own device.
        val text = ScriptFile.write(document()).replace("// pane:", "// mood: cheerful\n// pane:")
        assertEquals(source, ScriptFile.read(text)?.source)
    }

    // ── the link ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a link round-trips its id`() {
        val id = ScriptDocument.idFor(source, 1L)
        assertEquals(id, ScriptLink.idOf(ScriptLink.of(id)))
        assertEquals("https://pro-chart.com/s/$id", ScriptLink.of(id))
    }

    @Test
    fun `a link is read however it was pasted`() {
        val id = ScriptDocument.idFor(source, 1L)
        for (form in listOf(
            "https://pro-chart.com/s/$id",
            "http://pro-chart.com/s/$id",
            "https://www.pro-chart.com/s/$id",
            "pro-chart.com/s/$id",
            "https://pro-chart.com/s/$id/",
            "https://pro-chart.com/s/$id?from=telegram",
            "https://pro-chart.com/s/$id#top",
            "  https://pro-chart.com/s/$id  ",
        )) {
            assertEquals(form, id, ScriptLink.idOf(form))
        }
    }

    @Test
    fun `a link from anywhere else is refused`() {
        // The security half. A script id is about to become a request, and then code a reader is
        // offered — so anything that is not exactly one of ours is nothing.
        for (hostile in listOf(
            "https://pro-chart.com.evil.example/s/abcdefgh",
            "https://evil.example/s/abcdefgh",
            "https://evil.example/pro-chart.com/s/abcdefgh",
            "https://pro-chart.com/reset/abcdefgh",
            "https://pro-chart.com/s/",
            "https://pro-chart.com/s/../../etc/passwd",
            "https://pro-chart.com/s/abc defgh",
            "https://pro-chart.com/s/ABCDEFGH",
            "https://pro-chart.com/s/" + "a".repeat(64),
            "javascript:alert(1)",
            "",
        )) {
            assertNull(hostile, ScriptLink.idOf(hostile))
        }
    }
}
