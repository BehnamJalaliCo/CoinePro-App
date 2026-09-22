package com.coinepro.core.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The journal's round trip: write, read, compare — on the entries that are hard rather than the
 * ones that are easy.
 *
 * A journal is the one record in this app that exists nowhere else. There is no server route for
 * it and there should not be: «a trading diary is the one record whose value depends on nobody
 * else reading it». So a backup that loses part of an entry loses it for good, and the part it
 * would lose is the free text — the note and the lesson, which are the entry.
 */
class JournalArchiveCodecTest {

    private fun entry(
        symbol: String = "EURUSD",
        note: String = "took the long",
        lesson: String = "wait for the retest",
        entry: Double? = 1.0850,
        exit: Double? = 1.0902,
        pnl: Double? = 52.0,
        createdAt: Long = 1_700_000_000_000L,
    ) = JournalEntryEntity(
        id = 17,
        symbol = symbol,
        buy = true,
        entry = entry,
        exit = exit,
        size = 0.5,
        pnl = pnl,
        emotion = "بی‌حوصله",
        note = note,
        lesson = lesson,
        tags = "بریک‌اوت,لندن",
        createdAtEpochMillis = createdAt,
    )

    @Test
    fun `an entry comes back as itself, apart from the id`() {
        val original = entry()
        val restored = JournalArchiveCodec.decode(JournalArchiveCodec.encode(original))
        assertNotNull(restored)
        // The id was Room's, on the phone the archive came from. Carrying it would invite an
        // import to write over an unrelated row that happens to hold that number here.
        assertEquals(0L, restored!!.id)
        assertEquals(original.copy(id = 0), restored)
    }

    @Test
    fun `a note with newlines and separators comes back whole`() {
        val note = "1.0850 long\n\u001Ehesitated\u001D\nstopped: −12"
        val restored = JournalArchiveCodec.decode(JournalArchiveCodec.encode(entry(note = note)))
        assertEquals(note, restored?.note)
    }

    @Test
    fun `a price the reader did not record stays not recorded`() {
        // «took the EURUSD long, was impatient» is a complete journal entry. A zero restored in
        // place of a null would be a price the reader never gave, in a column they read as money.
        val restored = JournalArchiveCodec.decode(
            JournalArchiveCodec.encode(entry(entry = null, exit = null, pnl = null)),
        )
        assertNotNull(restored)
        assertNull(restored!!.entry)
        assertNull(restored.exit)
        assertNull(restored.pnl)
    }

    @Test
    fun `an entry with no market is refused rather than restored nameless`() {
        assertNull(JournalArchiveCodec.decode(JournalArchiveCodec.encode(entry(symbol = "   "))))
    }

    @Test
    fun `text that is not an entry is refused`() {
        assertNull(JournalArchiveCodec.decode(""))
        assertNull(JournalArchiveCodec.decode("EURUSD,long,1.0850"))
    }

    @Test
    fun `the archive name is the pair that identifies the entry`() {
        // The archive keys records by name, so two entries sharing one would silently become one.
        val morning = entry(createdAt = 1_700_000_000_000L)
        val evening = entry(createdAt = 1_700_000_060_000L)
        assertFalse(JournalArchiveCodec.nameOf(morning) == JournalArchiveCodec.nameOf(evening))
        assertTrue(JournalArchiveCodec.nameOf(morning).startsWith("EURUSD "))
    }

    @Test
    fun `the same entry imported twice is recognised as the same one`() {
        val mine = entry(note = "as I typed it")
        // The body is allowed to differ — an entry edited on one phone and backed up from another
        // is still that entry, and the import's job is not to add a second copy of it.
        val theirs = entry(note = "as it came back")
        assertTrue(JournalArchiveCodec.sameEntry(mine, theirs))
        assertFalse(JournalArchiveCodec.sameEntry(mine, entry(symbol = "XAUUSD")))
        assertFalse(JournalArchiveCodec.sameEntry(mine, entry(createdAt = 1L)))
    }
}
