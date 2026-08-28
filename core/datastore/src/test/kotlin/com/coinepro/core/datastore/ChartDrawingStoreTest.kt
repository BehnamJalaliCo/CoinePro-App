package com.coinepro.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChartDrawingStoreTest {

    private fun line(id: Long = 1L, text: String? = null) = StoredDrawing(
        id = id,
        toolId = "trend",
        points = listOf(1_700_000_000L to 2_643.18, 1_700_003_600L to 2_651.40),
        colour = 0xFFD8A848,
        widthDp = 1.6f,
        text = text,
        direction = "UP",
    )

    @Test
    fun `a drawing round-trips with its points intact`() = runTest {
        val store = ChartDrawingStore(FakeDrawingPreferences())
        store.save("XAUUSD", listOf(line()))

        assertEquals(line(), store.drawings("XAUUSD").first().single())
    }

    @Test
    fun `drawings are kept per symbol and never bleed across`() = runTest {
        val store = ChartDrawingStore(FakeDrawingPreferences())
        store.save("XAUUSD", listOf(line(id = 1)))
        store.save("BTCUSDT", listOf(line(id = 2), line(id = 3)))

        // The point of the per-symbol key: a trend line is anchored to one instrument's prices, and
        // the same line on another symbol is a line through unrelated numbers.
        assertEquals(listOf(1L), store.drawings("XAUUSD").first().map(StoredDrawing::id))
        assertEquals(listOf(2L, 3L), store.drawings("BTCUSDT").first().map(StoredDrawing::id))
    }

    @Test
    fun `the symbol key is case-insensitive`() = runTest {
        val store = ChartDrawingStore(FakeDrawingPreferences())
        store.save("btcusdt", listOf(line()))
        assertEquals(1, store.drawings("BTCUSDT").first().size)
    }

    @Test
    fun `saving nothing clears the symbol rather than storing an empty record`() = runTest {
        val backing = FakeDrawingPreferences()
        val store = ChartDrawingStore(backing)
        store.save("XAUUSD", listOf(line()))
        store.save("XAUUSD", emptyList())

        assertTrue(store.drawings("XAUUSD").first().isEmpty())
        assertTrue(backing.data.value.asMap().isEmpty())
    }

    @Test
    fun `a note carrying a separator is dropped rather than corrupting the record`() = runTest {
        val store = ChartDrawingStore(FakeDrawingPreferences())
        val poisoned = line(id = 2, text = "before\u001Eafter")
        store.save("XAUUSD", listOf(line(id = 1), poisoned))

        // Written, that record would parse back as different fields — the failure this scheme has
        // to be proof against, since the delimiters are the whole format.
        assertEquals(listOf(1L), store.drawings("XAUUSD").first().map(StoredDrawing::id))
    }

    @Test
    fun `a note with ordinary punctuation survives`() = runTest {
        val store = ChartDrawingStore(FakeDrawingPreferences())
        store.save("XAUUSD", listOf(line(text = "سقف قبلی — ۲٬۶۵۱")))
        assertEquals("سقف قبلی — ۲٬۶۵۱", store.drawings("XAUUSD").first().single().text)
    }

    @Test
    fun `garbage in the stored string is skipped rather than thrown`() = runTest {
        val backing = FakeDrawingPreferences()
        backing.data.value = mutablePreferencesOf(
            stringPreferencesKey("chart_drawings_XAUUSD") to "not a record at all",
        )
        val store = ChartDrawingStore(backing)

        // The whole reason decoding is total: a string written by an older version, or half-written
        // when the process died, must not be an app that cannot open a chart.
        assertTrue(store.drawings("XAUUSD").first().isEmpty())
    }

    @Test
    fun `a drawing with no points is not restored`() = runTest {
        val store = ChartDrawingStore(FakeDrawingPreferences())
        store.save("XAUUSD", listOf(line().copy(points = emptyList())))
        assertTrue(store.drawings("XAUUSD").first().isEmpty())
    }

    @Test
    fun `more drawings than the cap are truncated rather than refused`() = runTest {
        val store = ChartDrawingStore(FakeDrawingPreferences())
        store.save("XAUUSD", (1L..200L).map { line(id = it) })

        val restored = store.drawings("XAUUSD").first()
        assertEquals(120, restored.size)
        assertEquals(1L, restored.first().id)
    }
}

private class FakeDrawingPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow<Preferences>(emptyPreferences())

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(data.value)
        data.value = next
        return next
    }
}
