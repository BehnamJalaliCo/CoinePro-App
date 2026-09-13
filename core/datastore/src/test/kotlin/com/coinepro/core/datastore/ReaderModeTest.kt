package com.coinepro.core.datastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the one question the app asks is allowed to mean.
 *
 * The claim this file holds is the one the whole feature rests on: **a mode decides what is drawn
 * and never what exists**. Nothing here can be read as a permission, a tier or an entitlement, and
 * the two properties that do the hiding are the only two places the hiding is decided — so a mode
 * that hides four controls and leaves the fifth is a test failure rather than a bug report.
 */
class ReaderModeTest {

    @Test
    fun `a stored id reads back as the mode that wrote it`() {
        for (mode in ReaderMode.entries) {
            assertEquals(
                "«${mode.id}» must read back as $mode, or a reader's answer is lost on restart",
                mode,
                ReaderMode.fromId(mode.id),
            )
        }
    }

    @Test
    fun `an unknown answer is the middle and not the narrowest`() {
        // An install that predates the question, a key written by a newer build, a corrupt file:
        // all three land here, and all three must keep the whole chrome the reader already had.
        // Falling forward to SIMPLE would cut somebody's chart down on an upgrade they did not ask
        // for, which is the one outcome this enum exists to prevent.
        assertEquals(ReaderMode.TRADER, ReaderMode.fromId(null))
        assertEquals(ReaderMode.TRADER, ReaderMode.fromId(""))
        assertEquals(ReaderMode.TRADER, ReaderMode.fromId("beginner"))
        assertEquals(ReaderMode.TRADER, ReaderMode.fromId("SIMPLE"))
    }

    @Test
    fun `only the simple page drops the advanced chrome`() {
        assertFalse(ReaderMode.SIMPLE.showsAdvancedChrome)
        assertTrue(ReaderMode.TRADER.showsAdvancedChrome)
        assertTrue(ReaderMode.PRO.showsAdvancedChrome)
    }

    @Test
    fun `only the whole surface carries the workbench`() {
        assertFalse(ReaderMode.SIMPLE.showsWorkbench)
        assertFalse(ReaderMode.TRADER.showsWorkbench)
        assertTrue(ReaderMode.PRO.showsWorkbench)
    }

    @Test
    fun `the modes widen in one direction and never cross`() {
        // Each mode shows everything the one before it does. A pair where the narrower mode carries
        // something the wider one does not would mean a reader can lose a control by asking for
        // more, which is the shape of an entitlement rather than of a preference.
        val widening = listOf(ReaderMode.SIMPLE, ReaderMode.TRADER, ReaderMode.PRO)
        for ((narrower, wider) in widening.zipWithNext()) {
            assertTrue(
                "$wider must show the chrome $narrower shows",
                !narrower.showsAdvancedChrome || wider.showsAdvancedChrome,
            )
            assertTrue(
                "$wider must show the workbench $narrower shows",
                !narrower.showsWorkbench || wider.showsWorkbench,
            )
        }
    }

    @Test
    fun `every id is distinct and none of them is localised`() {
        val ids = ReaderMode.entries.map { it.id }
        assertEquals("two modes sharing an id would overwrite each other in storage", ids.size, ids.toSet().size)
        for (id in ids) {
            assertTrue("«$id» must be a stable ASCII key, never a word on a screen", id.all { it in 'a'..'z' })
        }
    }
}
