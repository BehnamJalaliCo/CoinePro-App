package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IndicatorFavouritesStoreTest {

    @Test
    fun `a star round-trips and a second tap takes it off`() = runTest {
        val store = IndicatorFavouritesStore(FakePreferences())

        store.toggleFavourite("ema")
        store.toggleFavourite("rsi")
        assertEquals(listOf("ema", "rsi"), store.favourites().first())

        store.toggleFavourite("ema")
        assertEquals(listOf("rsi"), store.favourites().first())
    }

    @Test
    fun `recent is newest first, has no duplicates and stops at eight`() = runTest {
        val store = IndicatorFavouritesStore(FakePreferences())

        listOf("sma", "ema", "rsi", "macd", "atr", "adx", "obv", "cci", "mfi").forEach { store.recordRecent(it) }
        store.recordRecent("ema")

        val recent = store.recent().first()
        assertEquals("ema", recent.first())
        assertEquals(IndicatorFavouritesStore.MAX_RECENT, recent.size)
        assertEquals(recent.size, recent.toSet().size)
    }

    @Test
    fun `an id that is not letters and digits is refused rather than stored`() {
        assertNull(IndicatorFavouritesStore.usable("a\u001Db"))
        assertNull(IndicatorFavouritesStore.usable(""))
        assertEquals("volumeprofile_ind", IndicatorFavouritesStore.usable(" volumeprofile_ind "))
    }
}

private class FakePreferences(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
