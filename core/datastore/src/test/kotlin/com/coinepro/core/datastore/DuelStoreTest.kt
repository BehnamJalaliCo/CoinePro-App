package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The duel's record.
 *
 * The assertion this file exists for is the once-a-day guard: a record that could be padded by
 * answering the same chart five times is not a record, and the refusal has to be **reported** so a
 * screen can say so rather than looking like a counter that does not move.
 */
class DuelStoreTest {

    private fun store() = DuelStore(FakeDuelPreferences())

    @Test
    fun `a fresh reader has an empty tally and no day`() = runTest {
        val store = store()
        assertEquals(DuelTally(), store.tally.first())
        assertNull(store.answeredDay.first())
    }

    @Test
    fun `a right answer counts as right and as played`() = runTest {
        val store = store()
        assertTrue(store.answer(right = true, tooClose = false, localDay = 20_000L))
        assertEquals(DuelTally(played = 1, right = 1, tooClose = 0), store.tally.first())
        assertEquals(20_000L, store.answeredDay.first())
    }

    @Test
    fun `a wrong answer counts as played and as neither`() = runTest {
        val store = store()
        store.answer(right = false, tooClose = false, localDay = 20_000L)
        assertEquals(DuelTally(played = 1, right = 0, tooClose = 0), store.tally.first())
    }

    @Test
    fun `a set-aside round is played and set aside`() = runTest {
        val store = store()
        store.answer(right = false, tooClose = true, localDay = 20_000L)
        assertEquals(DuelTally(played = 1, right = 0, tooClose = 1), store.tally.first())
    }

    @Test
    fun `a second answer on the same day is refused, and says so`() = runTest {
        val store = store()
        assertTrue(store.answer(right = true, tooClose = false, localDay = 20_000L))
        // False, not a silent no-op: the screen has to be able to tell the reader why nothing
        // changed, or a refusal is indistinguishable from a broken counter.
        assertFalse(store.answer(right = true, tooClose = false, localDay = 20_000L))
        assertEquals(1, store.tally.first().played)
    }

    @Test
    fun `tomorrow is a new round`() = runTest {
        val store = store()
        store.answer(right = true, tooClose = false, localDay = 20_000L)
        assertTrue(store.answer(right = false, tooClose = false, localDay = 20_001L))
        assertEquals(DuelTally(played = 2, right = 1, tooClose = 0), store.tally.first())
    }

    @Test
    fun `a clock that went backwards does not lock the reader out for ever`() = runTest {
        // A day *before* the stored one is not the stored one, so it is allowed. The alternative —
        // refusing anything not strictly later — would leave a reader whose phone corrected its
        // clock unable to play again until the calendar caught up.
        val store = store()
        store.answer(right = true, tooClose = false, localDay = 20_000L)
        assertTrue(store.answer(right = true, tooClose = false, localDay = 19_999L))
        assertEquals(2, store.tally.first().played)
    }

    @Test
    fun `clearing forgets everything, including the day`() = runTest {
        val store = store()
        store.answer(right = true, tooClose = false, localDay = 20_000L)
        store.clear()
        assertEquals(DuelTally(), store.tally.first())
        assertNull(store.answeredDay.first())
        // And the reader can play again immediately, which is what «forget my record» means.
        assertTrue(store.answer(right = true, tooClose = false, localDay = 20_000L))
    }
}

private class FakeDuelPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow<Preferences>(emptyPreferences())

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(data.value)
        data.value = next
        return next
    }
}
