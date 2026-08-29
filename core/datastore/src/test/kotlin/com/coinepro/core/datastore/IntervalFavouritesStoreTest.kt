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

@OptIn(ExperimentalCoroutinesApi::class)
class IntervalFavouritesStoreTest {

    @Test
    fun `a starred interval round-trips onto the end of the bar`() = runTest {
        val store = IntervalFavouritesStore(FakeIntervalPreferences())

        store.star("W1")

        assertEquals(
            IntervalFavouritesStore.DEFAULT_FAVOURITES + "W1",
            store.favourites().first(),
        )
    }

    @Test
    fun `an interval the reader typed is stored by its wire spelling like any preset`() = runTest {
        val store = IntervalFavouritesStore(FakeIntervalPreferences())

        store.star("205")

        assertTrue("205" in store.favourites().first())
    }

    @Test
    fun `nobody who has never touched this setting gets an empty interval bar`() = runTest {
        val store = IntervalFavouritesStore(FakeIntervalPreferences())

        assertEquals(IntervalFavouritesStore.DEFAULT_FAVOURITES, store.favourites().first())
    }

    @Test
    fun `a blank row left by a truncated write reads as the default rather than as nothing`() = runTest {
        val backing = FakeIntervalPreferences(
            mutablePreferencesOf(IntervalFavouritesStore.FAVOURITES to ""),
        )

        assertEquals(
            IntervalFavouritesStore.DEFAULT_FAVOURITES,
            IntervalFavouritesStore(backing).favourites().first(),
        )
    }

    @Test
    fun `a row of nothing but separators reads as the default, because it is an accident`() {
        // Not an opinion: it is what a half-written string looks like, and an interval bar with
        // nothing on it is a chart whose timeframe cannot be changed.
        assertEquals(
            IntervalFavouritesStore.DEFAULT_FAVOURITES,
            IntervalFavouritesStore.readFavourites("\u001D\u001D\u001D"),
        )
    }

    @Test
    fun `unstarring every interval is an opinion and survives, unlike an empty row`() = runTest {
        val store = IntervalFavouritesStore(FakeIntervalPreferences())

        IntervalFavouritesStore.DEFAULT_FAVOURITES.forEach { store.unstar(it) }

        // The distinction this whole file turns on: explicitly empty is not stored-empty.
        assertEquals(emptyList<String>(), store.favourites().first())
    }

    @Test
    fun `the explicit empty selection is not written as an empty string`() = runTest {
        val backing = FakeIntervalPreferences()
        val store = IntervalFavouritesStore(backing)

        IntervalFavouritesStore.DEFAULT_FAVOURITES.forEach { store.unstar(it) }

        assertEquals(
            IntervalFavouritesStore.EMPTY_SELECTION,
            backing.data.first()[IntervalFavouritesStore.FAVOURITES],
        )
    }

    @Test
    fun `unstarring one of the six materialises the other five rather than emptying the bar`() = runTest {
        val store = IntervalFavouritesStore(FakeIntervalPreferences())

        store.unstar("M1")

        assertEquals(
            IntervalFavouritesStore.DEFAULT_FAVOURITES - "M1",
            store.favourites().first(),
        )
    }

    @Test
    fun `starring is case-insensitive so one interval is never two chips`() = runTest {
        val store = IntervalFavouritesStore(FakeIntervalPreferences())

        store.star("h4")

        assertEquals(IntervalFavouritesStore.DEFAULT_FAVOURITES, store.favourites().first())
    }

    @Test
    fun `reset goes back to the app's default rather than to a snapshot of it`() = runTest {
        val backing = FakeIntervalPreferences()
        val store = IntervalFavouritesStore(backing)
        store.star("MN1")
        store.hide("M1")

        store.reset()

        assertEquals(IntervalFavouritesStore.DEFAULT_FAVOURITES, store.favourites().first())
        assertEquals(emptySet<String>(), store.hidden().first())
        // Removed, not rewritten: a later change to the app's six has to reach this reader.
        assertNull(backing.data.first()[IntervalFavouritesStore.FAVOURITES])
    }

    @Test
    fun `hiding an interval also takes it off the bar, so the two settings cannot contradict`() = runTest {
        val store = IntervalFavouritesStore(FakeIntervalPreferences())

        store.hide("M5")

        assertTrue("M5" in store.hidden().first())
        assertTrue("M5" !in store.favourites().first())
    }

    @Test
    fun `starring a hidden interval un-hides it`() = runTest {
        val store = IntervalFavouritesStore(FakeIntervalPreferences())
        store.hide("W1")

        store.star("W1")

        assertEquals(emptySet<String>(), store.hidden().first())
        assertTrue("W1" in store.favourites().first())
    }

    @Test
    fun `un-hiding does not put the interval back on the bar`() = runTest {
        val store = IntervalFavouritesStore(FakeIntervalPreferences())
        store.hide("M15")

        store.unhide("M15")

        assertEquals(emptySet<String>(), store.hidden().first())
        // Nothing on disk remembers whether it was starred, so nothing guesses.
        assertTrue("M15" !in store.favourites().first())
    }

    @Test
    fun `un-hiding the last interval removes the entry rather than leaving an empty string`() = runTest {
        val backing = FakeIntervalPreferences()
        val store = IntervalFavouritesStore(backing)
        store.hide("M15")

        store.unhide("M15")

        assertNull(backing.data.first()[IntervalFavouritesStore.HIDDEN])
    }

    @Test
    fun `a wire carrying a separator is dropped without taking the row around it`() {
        // A separator inside the token, not around it: a leading or trailing one is whitespace to
        // the JVM and trims away harmlessly, but one in the middle would parse back as a wire this
        // build never wrote.
        val decoded = IntervalFavouritesStore.readFavourites("M15\u001DH\u001E4")

        assertEquals(listOf("M15"), decoded)
    }

    @Test
    fun `a wire that cannot be an interval is dropped and its neighbours are kept`() {
        val decoded = IntervalFavouritesStore.readFavourites("M15\u001Dnot-a-wire\u001DH4")

        assertEquals(listOf("M15", "H4"), decoded)
    }

    @Test
    fun `the bar stops growing at the cap instead of one preferences string without bound`() = runTest {
        val store = IntervalFavouritesStore(FakeIntervalPreferences())

        (1..IntervalFavouritesStore.MAX_FAVOURITES * 2).forEach { minutes ->
            store.star(minutes.toString())
        }

        val favourites = store.favourites().first()
        assertEquals(IntervalFavouritesStore.MAX_FAVOURITES, favourites.size)
        // The cap trims the tail, so what the reader had first is what survives.
        assertEquals(IntervalFavouritesStore.DEFAULT_FAVOURITES, favourites.take(6))
    }

    @Test
    fun `hiding stops at its own cap`() = runTest {
        val store = IntervalFavouritesStore(FakeIntervalPreferences())

        (1..IntervalFavouritesStore.MAX_HIDDEN * 2).forEach { minutes -> store.hide(minutes.toString()) }

        assertEquals(IntervalFavouritesStore.MAX_HIDDEN, store.hidden().first().size)
    }
}

private class FakeIntervalPreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
