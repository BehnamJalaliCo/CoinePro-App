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

    // ── the magnet's channel, and the row shape that predates it ──────────────────────

    @Test
    fun `a row written by the shipped build decodes with no channel rather than being dropped`() = runTest {
        val backing = FakeDrawingPreferences()
        // Two halves per point — time, then price — which is exactly what every drawing already on
        // a reader's phone was written as. The whole point of the tolerance: an added field must
        // never be the reason somebody's chart comes back empty.
        backing.data.value = mutablePreferencesOf(
            stringPreferencesKey("chart_drawings_XAUUSD") to legacyRecord(),
        )
        val restored = ChartDrawingStore(backing).drawings("XAUUSD").first().single()

        assertEquals(listOf(1_700_000_000L to 2_643.18, 1_700_003_600L to 2_651.40), restored.points)
        assertEquals(emptyList<String?>(), restored.channels)
    }

    @Test
    fun `a drawing bound to the low and the high round-trips both channels`() = runTest {
        val store = ChartDrawingStore(FakeDrawingPreferences())
        val bound = line().copy(channels = listOf("LOW", "HIGH"))
        store.save("XAUUSD", listOf(bound))

        // The binding is the reason this field exists: what was chosen was "the low of that bar",
        // and a build that forgot it would restore a line frozen at a price the feed may have since
        // revised.
        assertEquals(bound, store.drawings("XAUUSD").first().single())
    }

    @Test
    fun `a drawing with only its second point bound keeps the first unbound`() = runTest {
        val store = ChartDrawingStore(FakeDrawingPreferences())
        // What a reader who turned the magnet on halfway through placing a line actually produces.
        val half = line().copy(channels = listOf(null, "CLOSE"))
        store.save("XAUUSD", listOf(half))

        assertEquals(listOf(null, "CLOSE"), store.drawings("XAUUSD").first().single().channels)
    }

    @Test
    fun `an unrecognised channel name decodes without throwing and is left for the mapper`() = runTest {
        val store = ChartDrawingStore(FakeDrawingPreferences())
        store.save("XAUUSD", listOf(line().copy(channels = listOf("MIDPOINT", null))))

        val restored = store.drawings("XAUUSD").first().single()
        // The store does not hold the vocabulary — `PriceChannel` lives in `core:chart`, which this
        // module deliberately does not depend on — so an unknown name survives the codec and becomes
        // null at `PriceChannel.decode`. What matters here is that the drawing and its points come
        // back whole rather than the row being discarded over one unreadable field.
        assertEquals(2, restored.points.size)
        assertEquals(listOf("MIDPOINT", null), restored.channels)
    }

    @Test
    fun `a channel carrying a separator is dropped rather than splitting the point in three`() = runTest {
        val store = ChartDrawingStore(FakeDrawingPreferences())
        val poisoned = "L" + UNIT_SEPARATOR + "OW"
        store.save("XAUUSD", listOf(line().copy(channels = listOf(poisoned, "HIGH"))))

        val restored = store.drawings("XAUUSD").first().single()
        // Written, that half would parse back as a fourth field and take the point with it. The
        // price survives and only the binding is lost, which is the smaller of the two failures.
        assertEquals(listOf(1_700_000_000L to 2_643.18, 1_700_003_600L to 2_651.40), restored.points)
        assertEquals(listOf(null, "HIGH"), restored.channels)
    }

    @Test
    fun `a blank channel is stored as no channel rather than as an empty third half`() = runTest {
        val store = ChartDrawingStore(FakeDrawingPreferences())
        store.save("XAUUSD", listOf(line().copy(channels = listOf("", ""))))

        assertTrue(store.drawings("XAUUSD").first().single().channels.isEmpty())
    }

    @Test
    fun `more drawings than the cap are truncated rather than refused`() = runTest {
        val store = ChartDrawingStore(FakeDrawingPreferences())
        store.save("XAUUSD", (1L..200L).map { line(id = it) })

        val restored = store.drawings("XAUUSD").first()
        assertEquals(120, restored.size)
        assertEquals(1L, restored.first().id)
    }

    @Test
    fun `the cap still holds when every point carries a channel`() = runTest {
        val store = ChartDrawingStore(FakeDrawingPreferences())
        // The third half makes every record longer, and the cap is a cap on drawings rather than on
        // characters — a reader who works with the magnet on must not silently get fewer of them.
        store.save("XAUUSD", (1L..200L).map { line(id = it).copy(channels = listOf("LOW", "HIGH")) })

        val restored = store.drawings("XAUUSD").first()
        assertEquals(120, restored.size)
        assertEquals(listOf("LOW", "HIGH"), restored.last().channels)
    }
}

/** The unit separator the codec joins one point's fields with. */
private const val UNIT_SEPARATOR = "\u001F"

/** The record separator the codec joins one drawing's fields with. */
private const val RECORD_SEPARATOR = "\u001E"

/**
 * One drawing in the record shape the currently shipped build writes.
 *
 * Eight fields, two halves per point, no channel anywhere — assembled by hand rather than by asking
 * the current encoder, because the whole question this answers is whether the current *decoder*
 * still reads what an older encoder produced.
 */
private fun legacyRecord(): String {
    val points = "1700000000${UNIT_SEPARATOR}2643.18,1700003600${UNIT_SEPARATOR}2651.4"
    return listOf("1", "trend", points, "4292388936", "1.6", "", "UP", "0")
        .joinToString(RECORD_SEPARATOR)
}

private class FakeDrawingPreferences : DataStore<Preferences> {
    override val data = MutableStateFlow<Preferences>(emptyPreferences())

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val next = transform(data.value)
        data.value = next
        return next
    }
}
