package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChartLayoutStoreTest {

    @Test
    fun `saving under an existing name replaces rather than duplicating`() = runTest {
        val store = ChartLayoutStore(FakePreferences())
        store.save(ChartLayout("روند", "candles", "H1", listOf("ema20")))
        store.save(ChartLayout("روند", "heikin", "H4", listOf("ema50", "rsi14")))

        val layouts = store.layouts.first()
        assertEquals(1, layouts.size)
        assertEquals("heikin", layouts.single().chartTypeId)
        assertEquals(listOf("ema50", "rsi14"), layouts.single().indicatorIds)
    }

    @Test
    fun `a layout with no indicators round-trips as an empty list, not one holding a blank`() = runTest {
        val store = ChartLayoutStore(FakePreferences())
        store.save(ChartLayout("ساده", "line", "D1", emptyList()))

        assertEquals(emptyList<String>(), store.layouts.first().single().indicatorIds)
    }

    @Test
    fun `a name carrying a separator is refused rather than written back wrong`() = runTest {
        val store = ChartLayoutStore(FakePreferences())

        store.save(ChartLayout("a\u001Eb", "candles", "H1", emptyList()))

        // Refused, not sanitised. Silently renaming somebody's layout is worse than not saving it,
        // and writing it would produce a record that parses back as different fields.
        assertEquals(emptyList<ChartLayout>(), store.layouts.first())
    }
}

private class FakePreferences : DataStore<Preferences> {
    private val state = MutableStateFlow<Preferences>(emptyPreferences())
    override val data = state
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(state.value)
        state.value = next
        return next
    }
}
