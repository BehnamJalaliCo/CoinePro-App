package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The stored side of the one question, and the one promise the chart's toggle makes.
 *
 * The promise is «this is reversible». A toggle that cannot put a reader back exactly where they
 * were is not reversible, whatever the tile says — so the round trip is the interesting case here,
 * and the [ReaderMode.PRO] one is the case that would have quietly demoted somebody.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderModeStoreTest {

    @Test
    fun `nobody has answered until somebody answers`() = runTest {
        val store = UserPreferencesStore(FakeReaderPreferences())

        assertFalse("a fresh install must be asked the question", store.readerModeChosen.first())
        // And it still resolves, so nothing downstream has to handle an absence.
        assertEquals(ReaderMode.TRADER, store.readerMode.first())

        store.setReaderMode(ReaderMode.TRADER)

        assertTrue("answering «trader» is an answer, not an absence", store.readerModeChosen.first())
        assertEquals(ReaderMode.TRADER, store.readerMode.first())
    }

    @Test
    fun `each answer round-trips`() = runTest {
        for (mode in ReaderMode.entries) {
            val store = UserPreferencesStore(FakeReaderPreferences())
            store.setReaderMode(mode)
            assertEquals(mode, store.readerMode.first())
        }
    }

    @Test
    fun `the toggle takes a pro reader to simple and back to pro`() = runTest {
        val store = UserPreferencesStore(FakeReaderPreferences())
        store.setReaderMode(ReaderMode.PRO)

        store.toggleSimpleReaderMode()
        assertEquals(ReaderMode.SIMPLE, store.readerMode.first())

        store.toggleSimpleReaderMode()
        // The whole point: a reader who simplified the chart for one look does not come back as a
        // Trader with their workbench gone.
        assertEquals(ReaderMode.PRO, store.readerMode.first())
    }

    @Test
    fun `the toggle takes a trader to simple and back to trader`() = runTest {
        val store = UserPreferencesStore(FakeReaderPreferences())
        store.setReaderMode(ReaderMode.TRADER)

        store.toggleSimpleReaderMode()
        assertEquals(ReaderMode.SIMPLE, store.readerMode.first())

        store.toggleSimpleReaderMode()
        assertEquals(ReaderMode.TRADER, store.readerMode.first())
    }

    @Test
    fun `a reader who has never answered can still use the toggle`() = runTest {
        val store = UserPreferencesStore(FakeReaderPreferences())

        store.toggleSimpleReaderMode()
        assertEquals(ReaderMode.SIMPLE, store.readerMode.first())
        assertTrue("using the toggle is answering the question", store.readerModeChosen.first())

        store.toggleSimpleReaderMode()
        assertEquals(ReaderMode.TRADER, store.readerMode.first())
    }

    @Test
    fun `the toggle never comes back to simple`() = runTest {
        val store = UserPreferencesStore(FakeReaderPreferences())
        store.setReaderMode(ReaderMode.SIMPLE)

        // Simple is not remembered as the mode to return to — a toggle that came back to the mode it
        // left would be a control that does nothing every other tap.
        store.toggleSimpleReaderMode()
        assertEquals(ReaderMode.TRADER, store.readerMode.first())
    }

    @Test
    fun `choosing simple from the appearance page leaves the way back intact`() = runTest {
        val store = UserPreferencesStore(FakeReaderPreferences())
        store.setReaderMode(ReaderMode.PRO)
        // The two controls must agree: picking Simple on the settings page and then tapping the
        // chart's toggle has to return the reader to Pro, exactly as the toggle's own round trip
        // does. This is why `setReaderMode` records the full mode on its way past.
        store.setReaderMode(ReaderMode.SIMPLE)

        store.toggleSimpleReaderMode()

        assertEquals(ReaderMode.PRO, store.readerMode.first())
    }
}

private class FakeReaderPreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
