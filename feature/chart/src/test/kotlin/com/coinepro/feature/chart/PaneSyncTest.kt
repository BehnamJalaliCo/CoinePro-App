package com.coinepro.feature.chart

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two-pane sync matrix: four switches, and each one has to be genuinely its own.
 *
 * The independence is the whole feature and it is exactly the property that rots first — one
 * `copy()` with the wrong field name and switching the crosshair on also ties the symbol, which
 * nobody notices until two panes are showing the same market and the reader cannot work out why.
 * So every field is set, cleared and toggled with the other three asserted unmoved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaneSyncTest {

    @Test
    fun `two panes start tied by nothing at all`() {
        val off = PaneSync.OFF
        assertFalse(off.symbol)
        assertFalse(off.interval)
        assertFalse(off.crosshair)
        assertFalse(off.timeRange)
        assertFalse(off.anyOn)
    }

    @Test
    fun `switching one field on leaves every other field exactly as it was`() {
        for (field in PaneSyncField.entries) {
            val next = PaneSync.OFF.with(field, true)
            assertTrue("$field should be on", next.isOn(field))
            for (other in PaneSyncField.entries) {
                if (other == field) continue
                assertFalse("$field must not switch $other on", next.isOn(other))
            }
        }
    }

    @Test
    fun `switching one field off leaves every other field on`() {
        val all = PaneSyncField.entries.fold(PaneSync.OFF) { sync, field -> sync.with(field, true) }
        for (field in PaneSyncField.entries) {
            val next = all.with(field, false)
            assertFalse("$field should be off", next.isOn(field))
            for (other in PaneSyncField.entries) {
                if (other == field) continue
                assertTrue("$field must not switch $other off", next.isOn(other))
            }
        }
    }

    @Test
    fun `toggling a field twice returns the set it started from`() {
        val start = PaneSync(symbol = true, timeRange = true)
        for (field in PaneSyncField.entries) {
            assertEquals(start, start.toggled(field).toggled(field))
        }
    }

    @Test
    fun `anything at all being tied is what the header reads`() {
        assertTrue(PaneSync(crosshair = true).anyOn)
        assertTrue(PaneSync(timeRange = true).anyOn)
        assertFalse(PaneSync().anyOn)
    }

    @Test
    fun `every one of the sixteen combinations survives a round trip through storage`() {
        for (mask in 0 until (1 shl PaneSyncField.entries.size)) {
            val sync = PaneSyncField.entries.foldIndexed(PaneSync.OFF) { index, acc, field ->
                acc.with(field, mask and (1 shl index) != 0)
            }
            assertEquals(sync, PaneSync.decode(sync.encode()))
        }
    }

    @Test
    fun `a record from a build with fewer fields reads its missing ones as off`() {
        // Two characters where there are now four: the first two are honoured and the rest are off,
        // because a tie the reader never asked for must never arrive by upgrade.
        val short = PaneSync.decode("11")
        assertTrue(short.symbol)
        assertTrue(short.interval)
        assertFalse(short.crosshair)
        assertFalse(short.timeRange)
    }

    @Test
    fun `an unreadable record is nothing tied rather than everything tied`() {
        assertEquals(PaneSync.OFF, PaneSync.decode(""))
        assertEquals(PaneSync.OFF, PaneSync.decode("xxxx"))
    }

    @Test
    fun `the switches a reader set are the switches they get back`() = runTest {
        val store = ChartWorkspaceStore(FakeWorkspacePreferences())
        assertEquals(PaneSync.OFF, store.paneSync.first())
        val chosen = PaneSync(interval = true, crosshair = true)
        store.setPaneSync(chosen)
        assertEquals(chosen, store.paneSync.first())
    }
}
