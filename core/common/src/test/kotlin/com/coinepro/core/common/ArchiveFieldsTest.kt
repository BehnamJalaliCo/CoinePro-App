package com.coinepro.core.common

import com.coinepro.core.common.ArchiveFields.field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The record that has no separator, tested on the values a separator would have eaten.
 *
 * Every case here is something a reader has actually typed or pasted into a journal entry. The
 * point of the format is that none of them is special, so the test is mostly a list of characters
 * with an assertion that nothing happened to them.
 */
class ArchiveFieldsTest {

    @Test
    fun `a value carrying the separators every other codec uses survives`() {
        // \u001D, \u001E and \u001F are the group, record and unit separators the layout store
        // joins with — and the layout store's answer to one inside a value is to blank the field.
        // Here they are just characters.
        val note = "long \u001E short\u001D\u001F — both stopped out"
        val values = ArchiveFields.read(ArchiveFields.write(listOf(note)))
        assertEquals(note, values.field(0))
    }

    @Test
    fun `newlines, colons and quotes are values like any other`() {
        val note = "entry: 1.0850\n\"impatient\"\nlesson: wait for the retest"
        assertEquals(note, ArchiveFields.read(ArchiveFields.write(listOf(note))).field(0))
    }

    @Test
    fun `an empty field is kept rather than collapsed`() {
        // The positions matter: a dropped empty field would shift every field after it, so a
        // journal entry with no exit price would restore with its size read as its exit.
        val values = ArchiveFields.read(ArchiveFields.write(listOf("EURUSD", "", "", "1.0850")))
        assertEquals(4, values.size)
        assertEquals("EURUSD", values.field(0))
        assertEquals("", values.field(1))
        assertEquals("1.0850", values.field(3))
    }

    @Test
    fun `a field a later version added is read up to what this build knows`() {
        val written = ArchiveFields.write(listOf("EURUSD", "1", "from the future"))
        val values = ArchiveFields.read(written)
        assertEquals("EURUSD", values.field(0))
        // And an index past the end is empty rather than a crash.
        assertEquals("", values.field(9))
    }

    @Test
    fun `text that is not a field block reads as nothing rather than as half a record`() {
        // Half a journal entry is a note with its ending missing, which the reader will believe
        // because they do not remember exactly what they wrote — which is why they wrote it down.
        assertTrue(ArchiveFields.read("not a record").isEmpty())
        assertTrue(ArchiveFields.read("5:abc").isEmpty())
        assertTrue(ArchiveFields.read("-1:x").isEmpty())
        assertTrue(ArchiveFields.read(":x").isEmpty())
    }

    @Test
    fun `nothing at all round-trips to nothing at all`() {
        assertEquals("", ArchiveFields.write(emptyList()))
        assertTrue(ArchiveFields.read("").isEmpty())
    }
}
