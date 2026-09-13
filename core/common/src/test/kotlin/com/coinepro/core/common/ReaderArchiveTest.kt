package com.coinepro.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Doctrine D8 — **the reader's own investment** — as the gate the doctrine names: «round-trip tests
 * per store: write, export, wipe, import, compare».
 *
 * A watchlist, a layout, a script and a journal entry are the only things in this app that cannot be
 * refetched. Everything else is a cache of something a server will send again on request; these came
 * out of a person. So the failure this file exists for is not a crash — it is an export that looked
 * like it worked and came back missing a line, which nobody notices until the phone it was on is
 * gone.
 *
 * The awkward cases are the point. A script is free text with quotes, newlines and Persian in it; a
 * journal entry is a paragraph somebody wrote while annoyed. Any format that gets those wrong gets
 * them wrong quietly.
 */
class ReaderArchiveTest {

    private val script = """
        // میانگین من
        length = input(20, title = "دوره", min = 2, max = 200)
        average = ta.ema(close, length)
        plot(average, title = "میانگین", color = color.gold)
    """.trimIndent()

    private val entry = """
        امروز زود وارد شدم.
        دلیلش: "حس کردم" دارد برمی‌گردد — که دلیل نیست.
        درس: منتظر بسته شدن کندل بمانم.
    """.trimIndent()

    private fun archive(): ReaderArchive = ReaderArchive(
        writtenAtEpochMillis = 1_700_000_000_000L,
        watchlist = listOf("BTCUSDT", "XAUUSD", "EURUSD"),
        layouts = listOf(ArchivedRecord("چیدمان روزانه", "ema:20,rsi:14|H1|candles")),
        scripts = listOf(ArchivedRecord("میانگین من", script)),
        journal = listOf(ArchivedRecord("۱۴۰۳/۰۶/۲۲", entry)),
        streak = ArchivedStreak(current = 3, best = 11, lastDayEpochDay = 19_700),
    )

    // ── the round trip ───────────────────────────────────────────────────────────────────────

    @Test
    fun `write, export, wipe, import, compare`() {
        // D8's own sentence, in one test.
        val mine = archive()
        val exported = ReaderArchiveFile.write(mine)
        val wiped = ReaderArchive()
        assertTrue("the wipe left something behind", wiped.isEmpty)
        val imported = ReaderArchiveFile.read(exported)
        assertEquals(mine, imported)
    }

    @Test
    fun `a second trip changes nothing`() {
        val once = ReaderArchiveFile.write(archive())
        assertEquals(once, ReaderArchiveFile.write(ReaderArchiveFile.read(once)!!))
    }

    @Test
    fun `every store survives on its own`() {
        // Per store, as the doctrine says — because a format that loses one section while the
        // others pass is exactly what a single whole-archive test hides.
        val cases = listOf(
            "watchlist" to ReaderArchive(watchlist = listOf("BTCUSDT")),
            "layouts" to ReaderArchive(layouts = listOf(ArchivedRecord("a", "b"))),
            "scripts" to ReaderArchive(scripts = listOf(ArchivedRecord("میانگین من", script))),
            "journal" to ReaderArchive(journal = listOf(ArchivedRecord("today", entry))),
            "streak" to ReaderArchive(streak = ArchivedStreak(1, 2, 3)),
        )
        for ((name, one) in cases) {
            assertEquals(name, one, ReaderArchiveFile.read(ReaderArchiveFile.write(one)))
        }
    }

    @Test
    fun `a script keeps every character it had`() {
        // The one that matters most: a script that comes back with its last line missing compiles
        // about half the time, and a script that runs and draws something *slightly* different is
        // worse than one that refuses.
        val imported = ReaderArchiveFile.read(ReaderArchiveFile.write(archive()))
        assertEquals(script, imported?.scripts?.single()?.body)
    }

    @Test
    fun `a journal entry keeps its newlines and its quotation marks`() {
        val imported = ReaderArchiveFile.read(ReaderArchiveFile.write(archive()))
        assertEquals(entry, imported?.journal?.single()?.body)
    }

    @Test
    fun `a record carrying the format's own punctuation survives`() {
        // Colons and brackets are the format's structure, and they are also things people write.
        val awkward = ReaderArchive(
            journal = listOf(
                ArchivedRecord(
                    name = "12:34 — [watchlist]",
                    body = "3:4:5\n[scripts]\n# prochart-archive 1\n",
                ),
            ),
        )
        assertEquals(awkward, ReaderArchiveFile.read(ReaderArchiveFile.write(awkward)))
    }

    @Test
    fun `an empty archive round-trips as empty`() {
        val nothing = ReaderArchive(writtenAtEpochMillis = 5L)
        assertEquals(nothing, ReaderArchiveFile.read(ReaderArchiveFile.write(nothing)))
        assertTrue(nothing.isEmpty)
        assertEquals(0, nothing.count)
    }

    // ── what is not an archive ───────────────────────────────────────────────────────────────

    @Test
    fun `something that is not an archive is refused rather than half-read`() {
        // Importing half of somebody's watchlist over the one they have is worse than importing
        // none of it, because they cannot tell which half.
        assertNull(ReaderArchiveFile.read(""))
        assertNull(ReaderArchiveFile.read("BTCUSDT\nXAUUSD"))
        assertNull(ReaderArchiveFile.read("# prochart-archive\n"))
        assertNull(ReaderArchiveFile.read("# prochart-archive x\n"))
        assertNull(ReaderArchiveFile.read("{\"watchlist\": []}"))
    }

    @Test
    fun `a truncated record is dropped rather than guessed at`() {
        val whole = ReaderArchiveFile.write(archive())
        val cut = whole.dropLast(40)
        val read = ReaderArchiveFile.read(cut)
        assertNotNull("a truncated file was refused entirely", read)
        // Whatever survived is whole. Nothing is half a script.
        for (record in read!!.scripts + read.journal + read.layouts) {
            assertTrue("a record came back empty", record.body.isNotEmpty())
        }
    }

    @Test
    fun `a file from a later version is still read`() {
        // Forwards compatibility, cheaply: an archive written by a newer build has sections this
        // one has never heard of, and refusing it would strand a reader's own backup.
        val text = ReaderArchiveFile.write(archive())
            .replace("[streak]", "[predictions]\n7:mood:4:calm\n[streak]")
            .replace("# prochart-archive 1", "# prochart-archive 9")
        val read = ReaderArchiveFile.read(text)
        assertEquals(9, read?.version)
        assertEquals(archive().watchlist, read?.watchlist)
        assertEquals(archive().streak, read?.streak)
    }

    // ── how an import meets what is already there ────────────────────────────────────────────

    @Test
    fun `merging adds what is missing and keeps the order`() {
        val mine = listOf("BTCUSDT", "XAUUSD")
        val theirs = listOf("XAUUSD", "EURUSD")
        assertEquals(
            listOf("BTCUSDT", "XAUUSD", "EURUSD"),
            ArchiveMerge.symbols(mine, theirs, ImportRule.MERGE),
        )
    }

    @Test
    fun `importing the same backup twice changes nothing the second time`() {
        // The failure a reader actually hits: they tap import, nothing visible happens, they tap it
        // again, and now they have two of everything.
        val mine = listOf("BTCUSDT", "XAUUSD")
        val once = ArchiveMerge.symbols(mine, mine, ImportRule.MERGE)
        assertEquals(once, ArchiveMerge.symbols(once, mine, ImportRule.MERGE))
        val records = listOf(ArchivedRecord("a", "1"), ArchivedRecord("b", "2"))
        val recordsOnce = ArchiveMerge.records(records, records, ImportRule.MERGE)
        assertEquals(recordsOnce, ArchiveMerge.records(recordsOnce, records, ImportRule.MERGE))
        assertEquals(2, recordsOnce.size)
    }

    @Test
    fun `replace means the archive wins, which is what a new phone wants`() {
        assertEquals(
            listOf("EURUSD"),
            ArchiveMerge.symbols(listOf("BTCUSDT"), listOf("EURUSD"), ImportRule.REPLACE),
        )
    }

    @Test
    fun `keeping mine takes only what has no counterpart`() {
        // Importing somebody else's archive: their scripts, not their opinion of what my layout
        // called «روزانه» should contain.
        val mine = listOf(ArchivedRecord("روزانه", "mine"))
        val theirs = listOf(ArchivedRecord("روزانه", "theirs"), ArchivedRecord("هفتگی", "new"))
        val merged = ArchiveMerge.records(mine, theirs, ImportRule.MINE)
        assertEquals(2, merged.size)
        assertEquals("mine", merged.first { it.name == "روزانه" }.body)
        assertEquals("new", merged.first { it.name == "هفتگی" }.body)
    }

    @Test
    fun `merging lets the incoming copy win a name clash`() {
        val mine = listOf(ArchivedRecord("روزانه", "mine"))
        val theirs = listOf(ArchivedRecord("روزانه", "theirs"))
        assertEquals("theirs", ArchiveMerge.records(mine, theirs, ImportRule.MERGE).single().body)
    }

    @Test
    fun `a streak is never shortened by an import`() {
        // A streak is a record of something the reader did. An import that cut it would be the app
        // telling them it did not happen.
        val mine = ArchivedStreak(current = 9, best = 30, lastDayEpochDay = 19_800)
        val theirs = ArchivedStreak(current = 2, best = 40, lastDayEpochDay = 19_500)
        val merged = ArchiveMerge.streak(mine, theirs, ImportRule.MERGE)
        assertEquals(9, merged?.current)
        assertEquals(40, merged?.best)
        assertEquals(19_800L, merged?.lastDayEpochDay)
    }

    @Test
    fun `a missing streak on either side is not a loss`() {
        val mine = ArchivedStreak(3, 7, 19_700)
        assertEquals(mine, ArchiveMerge.streak(mine, null, ImportRule.MERGE))
        assertEquals(mine, ArchiveMerge.streak(null, mine, ImportRule.MERGE))
        assertNull(ArchiveMerge.streak(null, null, ImportRule.MERGE))
    }

    @Test
    fun `the count is what a reader would count`() {
        val mine = archive()
        assertEquals(3 + 1 + 1 + 1, mine.count)
    }
}
