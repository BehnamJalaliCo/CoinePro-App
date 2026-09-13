package com.coinepro.core.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sharing a script on the board, and reading one back (run Σ, S3 item D).
 *
 * The failure this file exists for is quiet: a post that looks right in the feed and turns out to
 * carry half a script, or nothing at all, when somebody taps «open in the studio». The board cuts a
 * post at two hundred characters in the list, so the only place anybody would find out is the one
 * screen where it matters.
 *
 * And one that is not quiet at all: the board refuses links, phone numbers and messenger handles,
 * server-side, with its own sentence. A share that composed an address would be refused at the door
 * every single time, which is why the post carries the code.
 */
class ScriptShareTest {

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
    ).copy(tags = listOf("روند", "میانگین"), defaults = mapOf("دوره" to 34.0))

    // ── the round trip ───────────────────────────────────────────────────────────────────────

    @Test
    fun `a shared script comes back whole`() {
        // Everything the reader chose. Not `updatedAt`: the file does not carry a timestamp, and a
        // script somebody else wrote has no business claiming a moment on this device — the copy is
        // new here, which is what a zero means. `ScriptDocumentTest` compares the same fields for
        // the same reason.
        val mine = document()
        val read = ScriptShare.scriptIn(ScriptShare.post(mine))
        assertEquals(mine, read?.copy(updatedAt = mine.updatedAt))
        assertEquals(0L, read?.updatedAt)
    }

    @Test
    fun `the post opens with a sentence rather than a header`() {
        // The board shows the first two hundred characters of a post in every list it appears in.
        // A post that began «// nama 1» would be, in the feed, a wall of header.
        val body = ScriptShare.post(document())
        assertTrue(body.startsWith("یک اسکریپت از من: میانگین من"))
        assertTrue("the name is off the end of the feed's cut", body.indexOf("میانگین من") < 200)
    }

    @Test
    fun `English shares the same document under an English sentence`() {
        val mine = document()
        val english = ScriptShare.post(mine, english = true)
        assertTrue(english.startsWith("A script of mine:"))
        assertEquals(mine, ScriptShare.scriptIn(english)?.copy(updatedAt = mine.updatedAt))
    }

    @Test
    fun `a reader may write as much as they like above the script`() {
        // The best posts on this board are the ones where somebody explains what they were
        // thinking. Refusing those because the document is not the first line would be a pity.
        val body = "سه ماه روی این کار کردم.\nزیرش می‌گذارمش.\n\n" + ScriptFile.write(document())
        assertEquals(document(), ScriptShare.scriptIn(body)?.copy(updatedAt = document().updatedAt))
        assertTrue(ScriptShare.carries(body))
    }

    // ── what is not a shared script ──────────────────────────────────────────────────────────

    @Test
    fun `an ordinary post carries nothing`() {
        assertNull(ScriptShare.scriptIn("طلا امروز خوب بود."))
        assertNull(ScriptShare.scriptIn(""))
        assertFalse(ScriptShare.carries("plot(ta.ema(close, 20))"))
    }

    @Test
    fun `a post that only quotes the first line is not half a script`() {
        // Somebody pasting the header alone, or writing about the format. Half a document read out
        // of it would be a button that opens an empty editor.
        assertNull(ScriptShare.scriptIn("اولین خط فایل «// nama 1» است."))
    }

    // ── what the board will refuse ───────────────────────────────────────────────────────────

    @Test
    fun `what this run shares is not refusable`() {
        // The whole reason the post carries the code: a `pro-chart.com/s/…` address would be a
        // link, and the board refuses links. This asserts that the thing the app composes by
        // itself trips none of the three rules.
        assertEquals(emptyList<String>(), ScriptShare.refusals(ScriptShare.post(document())))
        assertEquals(emptyList<String>(), ScriptShare.refusals(ScriptShare.post(document(), english = true)))
    }

    @Test
    fun `a link anywhere in the body is named before the round trip`() {
        assertEquals(listOf(ScriptShare.LINK), ScriptShare.refusals("ببین: https://pro-chart.com/s/abc"))
        assertEquals(listOf(ScriptShare.LINK), ScriptShare.refusals("www.example.com"))
        assertEquals(listOf(ScriptShare.LINK), ScriptShare.refusals("بیشتر در example.ir بخوانید"))
    }

    @Test
    fun `a phone number and a handle are named too`() {
        assertEquals(listOf(ScriptShare.NUMBER), ScriptShare.refusals("زنگ بزن ۰۹۱۲۳۴۵۶۷۸۹"))
        assertEquals(listOf(ScriptShare.HANDLE_KEY), ScriptShare.refusals("به @behnamtrader پیام بده"))
    }

    @Test
    fun `a version comment is not a messenger handle`() {
        // `//@version=5` rides in on nearly every script an assistant writes. Reporting it as a
        // handle would put a refusal warning on a post the board would have accepted, which
        // teaches the reader to ignore the warning.
        assertEquals(emptyList<String>(), ScriptShare.refusals("//@version=5\nplot(close)"))
    }

    @Test
    fun `a script whose own comment carries a link is reported rather than silently refused`() {
        // The reader's text, not ours. The point is that they are told which rule before the post
        // is sent, instead of after.
        val mine = ScriptDocument.of(source = "// see https://example.com\nplot(close)", name = "x", at = 1L)
        val body = ScriptShare.post(mine)
        assertEquals(listOf(ScriptShare.LINK), ScriptShare.refusals(body))
        // And it is still a readable share: the rule is the board's, not the format's.
        assertNotNull(ScriptShare.scriptIn(body))
    }
}
