package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the one promise this store makes: a dismissed banner stays dismissed, and nothing else in
 * this file is ever allowed to invent a dismissal the reader did not make.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TeachingStoreTest {

    @Test
    fun `a reader who has dismissed nothing sees every banner`() = runTest {
        val store = TeachingStore(FakeTeachingPreferences())

        assertEquals(emptySet<String>(), store.dismissed().first())
    }

    @Test
    fun `a dismissal round-trips`() = runTest {
        val store = TeachingStore(FakeTeachingPreferences())

        store.dismiss("markets")

        assertEquals(setOf("markets"), store.dismissed().first())
    }

    @Test
    fun `dismissing one screen leaves every other banner alone`() = runTest {
        val store = TeachingStore(FakeTeachingPreferences())
        store.dismiss("markets")

        store.dismiss("dom")

        assertEquals(setOf("markets", "dom"), store.dismissed().first())
    }

    @Test
    fun `restoring brings one banner back and keeps the rest away`() = runTest {
        val store = TeachingStore(FakeTeachingPreferences())
        store.dismiss("markets")
        store.dismiss("dom")

        store.restore("dom")

        assertEquals(setOf("markets"), store.dismissed().first())
    }

    @Test
    fun `restoring the last dismissal removes the entry rather than leaving an empty string`() = runTest {
        val backing = FakeTeachingPreferences()
        val store = TeachingStore(backing)
        store.dismiss("chart")

        store.restore("chart")

        assertNull(backing.data.first()[TeachingStore.DISMISSED])
    }

    @Test
    fun `restoreAll forgets every dismissal`() = runTest {
        val backing = FakeTeachingPreferences()
        val store = TeachingStore(backing)
        store.dismiss("chart")
        store.dismiss("signals")

        store.restoreAll()

        assertEquals(emptySet<String>(), store.dismissed().first())
        assertNull(backing.data.first()[TeachingStore.DISMISSED])
    }

    @Test
    fun `dismissing twice is one dismissal and does not rewrite the row`() = runTest {
        val backing = FakeTeachingPreferences()
        val store = TeachingStore(backing)
        store.dismiss("alerts")
        val afterFirst = backing.data.first()[TeachingStore.DISMISSED]

        store.dismiss("alerts")

        assertEquals(setOf("alerts"), store.dismissed().first())
        assertEquals(afterFirst, backing.data.first()[TeachingStore.DISMISSED])
    }

    @Test
    fun `restoring something that was never dismissed does nothing`() = runTest {
        val backing = FakeTeachingPreferences()
        val store = TeachingStore(backing)

        store.restore("academy")

        assertNull(backing.data.first()[TeachingStore.DISMISSED])
    }

    @Test
    fun `a key is case-insensitive, so one screen is never two dismissals`() = runTest {
        val store = TeachingStore(FakeTeachingPreferences())
        store.dismiss("Markets")

        store.restore("markets")

        assertEquals(emptySet<String>(), store.dismissed().first())
    }

    @Test
    fun `whitespace around a key is trimmed rather than making a second entry`() = runTest {
        val store = TeachingStore(FakeTeachingPreferences())

        store.dismiss("  paper_trade  ")

        assertEquals(setOf("paper_trade"), store.dismissed().first())
    }

    @Test
    fun `a blank row left by a truncated write reads as nothing dismissed`() = runTest {
        val backing = FakeTeachingPreferences(
            mutablePreferencesOf(TeachingStore.DISMISSED to ""),
        )

        // The bias only goes one way: guessing "dismissed" would delete the teaching for somebody
        // who never dismissed anything, and they would never learn there had been an explanation.
        assertEquals(emptySet<String>(), TeachingStore(backing).dismissed().first())
    }

    @Test
    fun `a token that cannot be a key is dropped and its neighbours are kept`() {
        val decoded = TeachingStore.decode("markets\u001Dnot a key\u001Ddom")

        assertEquals(setOf("markets", "dom"), decoded)
    }

    @Test
    fun `a token carrying the separator cannot survive as a key`() {
        // The separator is outside the character set a key may use, which is what makes decoding
        // total: nothing can come back out that was never written.
        assertNull(TeachingStore.usable("mark\u001Dets"))
    }

    @Test
    fun `a revised key is a different dismissal, which is how a rewritten banner comes back`() = runTest {
        val store = TeachingStore(FakeTeachingPreferences())
        store.dismiss("markets")

        // The copy was rewritten and the catalogue moved to `markets.2`. Nobody has dismissed that.
        assertTrue("markets.2" !in store.dismissed().first())
        assertEquals("markets.2", TeachingStore.usable("markets.2"))
    }

    @Test
    fun `an absurdly long key is refused before it reaches the row`() = runTest {
        val backing = FakeTeachingPreferences()

        TeachingStore(backing).dismiss("x".repeat(TeachingStore.MAX_KEY_LENGTH + 1))

        assertNull(backing.data.first()[TeachingStore.DISMISSED])
    }

    @Test
    fun `the row stops growing at the cap instead of one preferences string without bound`() = runTest {
        val store = TeachingStore(FakeTeachingPreferences())

        (1..TeachingStore.MAX_SURFACES * 2).forEach { index -> store.dismiss("surface$index") }

        val dismissed = store.dismissed().first()
        assertEquals(TeachingStore.MAX_SURFACES, dismissed.size)
        // The cap drops the oldest, not the newest: the newest dismissal is the one the reader just
        // made, and losing it would make the banner they just closed come straight back.
        assertTrue("surface${TeachingStore.MAX_SURFACES * 2}" in dismissed)
        assertTrue("surface1" !in dismissed)
    }
}

private class FakeTeachingPreferences(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
